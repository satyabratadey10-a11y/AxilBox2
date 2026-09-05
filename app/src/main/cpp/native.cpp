#include <jni.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <fcntl.h>
#include <string>

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

} // extern "C"
