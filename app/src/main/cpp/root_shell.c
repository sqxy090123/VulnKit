#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/wait.h>
#include <pthread.h>
#include <android/log.h>

#define TAG "RootShell"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

static int root_pid = -1;
static int root_stdin = -1;
static int root_stdout = -1;
static int root_stderr = -1;

int establish_root_shell(void) {
    // 如果已经有 shell 则直接返回成功
    if (root_pid > 0) return 0;

    int pipe_stdin[2], pipe_stdout[2], pipe_stderr[2];
    if (pipe(pipe_stdin) < 0 || pipe(pipe_stdout) < 0 || pipe(pipe_stderr) < 0) {
        LOGI("pipe failed");
        return -1;
    }

    pid_t pid = fork();
    if (pid == 0) {
        // 子进程：重定向标准输入输出
        dup2(pipe_stdin[0], STDIN_FILENO);
        dup2(pipe_stdout[1], STDOUT_FILENO);
        dup2(pipe_stderr[1], STDERR_FILENO);
        close(pipe_stdin[0]); close(pipe_stdin[1]);
        close(pipe_stdout[0]); close(pipe_stdout[1]);
        close(pipe_stderr[0]); close(pipe_stderr[1]);

        // 尝试提升权限（如果当前进程已经是 root 则直接 setuid(0)）
        setuid(0);
        setgid(0);

        // 执行 shell
        char *argv[] = {"/system/bin/sh", NULL};
        execve(argv[0], argv, environ);
        perror("execve");
        exit(1);
    } else if (pid > 0) {
        close(pipe_stdin[0]);
        close(pipe_stdout[1]);
        close(pipe_stderr[1]);

        root_pid = pid;
        root_stdin = pipe_stdin[1];
        root_stdout = pipe_stdout[0];
        root_stderr = pipe_stderr[0];
        LOGI("Root shell established, pid=%d", root_pid);
        return 0;
    } else {
        LOGI("fork failed");
        return -1;
    }
}

int write_root_shell(const char *cmd) {
    if (root_stdin < 0) return -1;
    int len = strlen(cmd);
    int written = write(root_stdin, cmd, len);
    return written;
}

char* read_root_shell(void) {
    if (root_stdout < 0) return NULL;
    static char buffer[4096];
    int n = read(root_stdout, buffer, sizeof(buffer)-1);
    if (n > 0) {
        buffer[n] = '\0';
        return buffer;
    }
    return NULL;
}

void close_root_shell(void) {
    if (root_pid > 0) {
        kill(root_pid, SIGTERM);
        waitpid(root_pid, NULL, 0);
        root_pid = -1;
    }
    if (root_stdin >= 0) close(root_stdin);
    if (root_stdout >= 0) close(root_stdout);
    if (root_stderr >= 0) close(root_stderr);
    root_stdin = root_stdout = root_stderr = -1;
}

int is_root_shell_available(void) {
    return (root_pid > 0) ? 1 : 0;
}