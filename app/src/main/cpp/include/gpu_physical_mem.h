#ifndef GPU_PHYSICAL_MEM_H
#define GPU_PHYSICAL_MEM_H

#include <stdint.h>
#include <stddef.h>

int gpu_phys_init(int kgsl_fd, int ioctl_type, int drawctxt_create, int map_user_mem,
                  int gpu_command, int gpumem_alloc, int gpumem_free, int context_id);

void gpu_phys_setup_window(void *pt_va, uint64_t pt_gpuaddr,
                           uint64_t window_gpuaddr, void *window_user_ptr);

uint64_t gpu_phys_read64(uint64_t phys_addr);
int gpu_phys_write64(uint64_t phys_addr, uint64_t value);

int gpu_phys_read(uint64_t phys_addr, void *buf, size_t len);
int gpu_phys_write(uint64_t phys_addr, const void *buf, size_t len);

uint64_t gpu_phys_virt_to_phys(void *virt_addr);

int gpu_phys_escalate_privileges(void);

void gpu_phys_cleanup(void);

#endif