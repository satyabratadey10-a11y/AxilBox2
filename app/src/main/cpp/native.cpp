#include <jni.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <fcntl.h>
#include <signal.h>
#include <errno.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <string>
#include <vector>
#include <android/log.h>

#define LOG_TAG "AxilBoxNative"

extern "C" {

/**
 * Checks if the file at the given absolute path exists and has executable permissions (S_IXUSR).
 */
JNIEXPORT jboolean JNICALL
Java_com_axilbox_app_engine_NativeEngineBridge_hasExecutable(
    JNIEnv* env,
    jobject /* this */,
    jstring path
) {
    if (path == nullptr) return JNI_FALSE;

    const char* cpath = env->GetStringUTFChars(path, nullptr);
    if (cpath == nullptr) return JNI_FALSE;

    struct stat st;
    jboolean result = JNI_FALSE;

    if (stat(cpath, &st) == 0) {
        if ((st.st_mode & (S_IXUSR | S_IXGRP | S_IXOTH)) && access(cpath, X_OK) == 0) {
            result = JNI_TRUE;
        }
    }

    env->ReleaseStringUTFChars(path, cpath);
    return result;
}

/**
 * Sets executable permissions (chmod 0755) on the file at the given path.
 */
JNIEXPORT jboolean JNICALL
Java_com_axilbox_app_engine_NativeEngineBridge_chmodExecutable(
    JNIEnv* env,
    jobject /* this */,
    jstring path
) {
    if (path == nullptr) return JNI_FALSE;

    const char* cpath = env->GetStringUTFChars(path, nullptr);
    if (cpath == nullptr) return JNI_FALSE;

    int res = chmod(cpath, 0755);
    env->ReleaseStringUTFChars(path, cpath);
    return (res == 0) ? JNI_TRUE : JNI_FALSE;
}

/**
 * Formats the QEMU -L argument for firmware/option-ROM resolution against the extracted pc-bios directory.
 */
JNIEXPORT jstring JNICALL
Java_com_axilbox_app_engine_NativeEngineBridge_formatBiosArg(
    JNIEnv* env,
    jobject /* this */,
    jstring biosPath
) {
    if (biosPath == nullptr) return env->NewStringUTF("-L");

    const char* cpath = env->GetStringUTFChars(biosPath, nullptr);
    if (cpath == nullptr) return env->NewStringUTF("-L");

    std::string arg = std::string("-L ") + cpath;
    env->ReleaseStringUTFChars(biosPath, cpath);
    return env->NewStringUTF(arg.c_str());
}

/**
 * Native fork()+execve() launcher for QEMU.
 *
 * Explicitly manages open file descriptors across fork/exec, guaranteeing that SAF ParcelFileDescriptors
 * survive into the child process. Defensively verifies and clears FD_CLOEXEC on each required fd,
 * logs readlink("/proc/self/fd/<N>") diagnostics on startup, and captures child stdout/stderr into a pipe.
 *
 * Returns jintArray of [childPid, stdoutPipeReadFd], or nullptr on failure.
 */
JNIEXPORT jintArray JNICALL
Java_com_axilbox_app_engine_NativeEngineBridge_nativeForkAndExecQemu(
    JNIEnv* env,
    jobject /* this */,
    jstring qemuPath,
    jobjectArray argvArray,
    jobjectArray envpArray,
    jstring workingDir,
    jintArray preservedFds
) {
    if (qemuPath == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "qemuPath is null");
        return nullptr;
    }

    // 1. Extract binary path
    const char* rawQemuPath = env->GetStringUTFChars(qemuPath, nullptr);
    if (rawQemuPath == nullptr) return nullptr;
    std::string binPath = rawQemuPath;
    env->ReleaseStringUTFChars(qemuPath, rawQemuPath);

    // 2. Extract argv array
    std::vector<std::string> argvStrings;
    if (argvArray != nullptr) {
        jsize argc = env->GetArrayLength(argvArray);
        argvStrings.reserve(argc);
        for (jsize i = 0; i < argc; ++i) {
            auto argStr = static_cast<jstring>(env->GetObjectArrayElement(argvArray, i));
            if (argStr != nullptr) {
                const char* cStr = env->GetStringUTFChars(argStr, nullptr);
                argvStrings.emplace_back(cStr ? cStr : "");
                if (cStr) env->ReleaseStringUTFChars(argStr, cStr);
                env->DeleteLocalRef(argStr);
            }
        }
    }

    // If argv is empty, provide binary name as argv[0]
    if (argvStrings.empty()) {
        argvStrings.push_back(binPath);
    }

    // 3. Extract envp array
    std::vector<std::string> envpStrings;
    if (envpArray != nullptr) {
        jsize envc = env->GetArrayLength(envpArray);
        envpStrings.reserve(envc);
        for (jsize i = 0; i < envc; ++i) {
            auto eStr = static_cast<jstring>(env->GetObjectArrayElement(envpArray, i));
            if (eStr != nullptr) {
                const char* cStr = env->GetStringUTFChars(eStr, nullptr);
                envpStrings.emplace_back(cStr ? cStr : "");
                if (cStr) env->ReleaseStringUTFChars(eStr, cStr);
                env->DeleteLocalRef(eStr);
            }
        }
    }

    // 4. Extract working directory
    std::string workDirStr = "";
    if (workingDir != nullptr) {
        const char* rawWorkDir = env->GetStringUTFChars(workingDir, nullptr);
        if (rawWorkDir != nullptr) {
            workDirStr = rawWorkDir;
            env->ReleaseStringUTFChars(workingDir, rawWorkDir);
        }
    }

    // 5. Extract preserved file descriptors
    std::vector<int> targetFds;
    if (preservedFds != nullptr) {
        jsize fdCount = env->GetArrayLength(preservedFds);
        jint* rawFds = env->GetIntArrayElements(preservedFds, nullptr);
        if (rawFds != nullptr) {
            for (jsize i = 0; i < fdCount; ++i) {
                targetFds.push_back(rawFds[i]);
            }
            env->ReleaseIntArrayElements(preservedFds, rawFds, JNI_ABORT);
        }
    }

    // 6. Construct NULL-terminated C pointer arrays before fork()
    std::vector<char*> c_argv;
    c_argv.reserve(argvStrings.size() + 1);
    for (auto& s : argvStrings) {
        c_argv.push_back(const_cast<char*>(s.c_str()));
    }
    c_argv.push_back(nullptr);

    std::vector<char*> c_envp;
    c_envp.reserve(envpStrings.size() + 1);
    for (auto& s : envpStrings) {
        c_envp.push_back(const_cast<char*>(s.c_str()));
    }
    c_envp.push_back(nullptr);

    // 7. Create stdout/stderr capture pipe with O_CLOEXEC
    int out_pipe[2];
    if (pipe2(out_pipe, O_CLOEXEC) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "pipe2() failed: %s (errno=%d)", strerror(errno), errno);
        return nullptr;
    }

    // 8. Fork child process
    pid_t pid = fork();
    if (pid < 0) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "fork() failed: %s (errno=%d)", strerror(errno), errno);
        close(out_pipe[0]);
        close(out_pipe[1]);
        return nullptr;
    }

    if (pid > 0) {
        // Parent process: close write end, return child PID and read fd
        close(out_pipe[1]);
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Forked QEMU child PID %d (pipe read fd=%d)", pid, out_pipe[0]);

        jintArray result = env->NewIntArray(2);
        if (result != nullptr) {
            jint vals[2] = { static_cast<jint>(pid), static_cast<jint>(out_pipe[0]) };
            env->SetIntArrayRegion(result, 0, 2, vals);
        }
        return result;
    }

    // =========================================================================
    // Child process (pid == 0) — strictly POSIX async-signal-safe calls only
    // =========================================================================

    // Close parent's read end of pipe
    close(out_pipe[0]);

    // Redirect stdout and stderr to out_pipe[1]
    dup2(out_pipe[1], STDOUT_FILENO);
    dup2(out_pipe[1], STDERR_FILENO);
    if (out_pipe[1] != STDOUT_FILENO && out_pipe[1] != STDERR_FILENO) {
        close(out_pipe[1]);
    }

    // Redirect stdin to /dev/null
    int devnull = open("/dev/null", O_RDONLY);
    if (devnull >= 0) {
        dup2(devnull, STDIN_FILENO);
        if (devnull != STDIN_FILENO) {
            close(devnull);
        }
    }

    // Diagnostic & Defensive verification of each preserved fd
    for (int fd : targetFds) {
        if (fd < 0) continue;

        int flags = fcntl(fd, F_GETFD);
        if (flags == -1) {
            fprintf(stderr, "[AxilBox Native Child] ERROR: Preserved fd %d is INVALID or CLOSED: %s (errno=%d)\n",
                    fd, strerror(errno), errno);
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                                "Child fd %d is invalid or closed: %s", fd, strerror(errno));
        } else {
            if (flags & FD_CLOEXEC) {
                fprintf(stderr, "[AxilBox Native Child] WARN: fd %d had FD_CLOEXEC set; clearing now...\n", fd);
                if (fcntl(fd, F_SETFD, flags & ~FD_CLOEXEC) == -1) {
                    fprintf(stderr, "[AxilBox Native Child] ERROR: Failed to clear FD_CLOEXEC on fd %d: %s\n",
                            fd, strerror(errno));
                }
            } else {
                fprintf(stderr, "[AxilBox Native Child] OK: fd %d confirmed open with FD_CLOEXEC=0\n", fd);
            }

            // Diagnostic: readlink("/proc/self/fd/<N>")
            char proc_link[64];
            snprintf(proc_link, sizeof(proc_link), "/proc/self/fd/%d", fd);
            char target_buf[1024];
            ssize_t len = readlink(proc_link, target_buf, sizeof(target_buf) - 1);
            if (len != -1) {
                target_buf[len] = '\0';
                fprintf(stderr, "[AxilBox Native Child] DIAGNOSTIC: /proc/self/fd/%d -> %s\n", fd, target_buf);
                __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                                    "DIAGNOSTIC: /proc/self/fd/%d -> %s", fd, target_buf);
            } else {
                fprintf(stderr, "[AxilBox Native Child] DIAGNOSTIC: readlink(/proc/self/fd/%d) failed: %s (errno=%d)\n",
                        fd, strerror(errno), errno);
                __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
                                    "DIAGNOSTIC: readlink(/proc/self/fd/%d) failed: %s", fd, strerror(errno));
            }
        }
    }

    // Change working directory if specified
    if (!workDirStr.empty()) {
        if (chdir(workDirStr.c_str()) != 0) {
            fprintf(stderr, "[AxilBox Native Child] WARN: chdir('%s') failed: %s\n",
                    workDirStr.c_str(), strerror(errno));
        }
    }

    fflush(stdout);
    fflush(stderr);

    // Execute QEMU binary directly
    execve(binPath.c_str(), c_argv.data(), c_envp.data());

    // If execve returns, execution failed!
    int err = errno;
    fprintf(stderr, "[AxilBox Native Child] FATAL: execve('%s') failed: %s (errno=%d)\n",
            binPath.c_str(), strerror(err), err);
    __android_log_print(ANDROID_LOG_FATAL, LOG_TAG,
                        "execve('%s') failed: %s (errno=%d)", binPath.c_str(), strerror(err), err);
    fflush(stderr);
    _exit(127);
}

/**
 * Waits for a child process to terminate and returns its exit status.
 */
JNIEXPORT jint JNICALL
Java_com_axilbox_app_engine_NativeEngineBridge_nativeWaitForProcess(
    JNIEnv* /* env */,
    jobject /* this */,
    jint pid
) {
    if (pid <= 0) return -1;
    int status = 0;
    pid_t result = waitpid(static_cast<pid_t>(pid), &status, 0);
    if (result < 0) {
        return -1;
    }
    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    } else if (WIFSIGNALED(status)) {
        return 128 + WTERMSIG(status);
    }
    return status;
}

/**
 * Sends a signal (e.g. SIGTERM=15 or SIGKILL=9) to a child process.
 */
JNIEXPORT jboolean JNICALL
Java_com_axilbox_app_engine_NativeEngineBridge_nativeKillProcess(
    JNIEnv* /* env */,
    jobject /* this */,
    jint pid,
    jint sig
) {
    if (pid <= 0) return JNI_FALSE;
    int res = kill(static_cast<pid_t>(pid), sig);
    return (res == 0) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"

