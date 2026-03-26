#ifndef ROOT_SHELL_H
#define ROOT_SHELL_H

#ifdef __cplusplus
extern "C" {
#endif

int establish_root_shell(void);
int write_root_shell(const char *cmd);
char* read_root_shell(void);
void close_root_shell(void);
int is_root_shell_available(void);

#ifdef __cplusplus
}
#endif

#endif