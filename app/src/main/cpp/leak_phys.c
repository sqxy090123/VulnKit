#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdint.h>

#define PAGE_SIZE 4096

int main(int argc, char *argv[]) {
    if (argc != 2) {
        fprintf(stderr, "Usage: %s <virtual_address_in_hex>\n", argv[0]);
        return 1;
    }

    uint64_t user_ptr = strtoull(argv[1], NULL, 16);
    uint64_t page = user_ptr / PAGE_SIZE;
    int pid = getpid();

    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/pagemap", pid);
    int fd = open(path, O_RDONLY);
    if (fd < 0) {
        perror("open pagemap");
        return 1;
    }

    off_t offset = page * sizeof(uint64_t);
    uint64_t entry = 0;
    if (pread(fd, &entry, sizeof(entry), offset) != sizeof(entry)) {
        perror("pread");
        close(fd);
        return 1;
    }
    close(fd);

    if (!(entry & (1ULL << 63))) {
        fprintf(stderr, "Page not present\n");
        return 1;
    }

    uint64_t pfn = entry & ((1ULL << 55) - 1);
    uint64_t phys = (pfn * PAGE_SIZE) | (user_ptr & (PAGE_SIZE - 1));
    printf("0x%llx\n", (unsigned long long)phys);
    return 0;
}