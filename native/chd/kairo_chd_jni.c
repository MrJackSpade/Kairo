/* ChdImage's native side (frontend ChdImage.kt): a CHD's disc image read from a file descriptor. */
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

#include "kairo_chd.h"

static void throw_io(JNIEnv *env, const char *message) {
    jclass io = (*env)->FindClass(env, "java/io/IOException");
    if (io) (*env)->ThrowNew(env, io, message);
}

JNIEXPORT jlong JNICALL
Java_com_mrjackspade_kairo_frontend_ChdImage_nativeOpenFd(JNIEnv *env, jclass type, jint fd) {
    char error[256] = "";
    kairo_chd *chd = kairo_chd_open_fd(fd, error, sizeof(error));
    (void)type;
    if (!chd) {
        throw_io(env, error);
        return 0;
    }
    return (jlong)(intptr_t)chd;
}

JNIEXPORT jlong JNICALL
Java_com_mrjackspade_kairo_frontend_ChdImage_nativeSize(JNIEnv *env, jclass type, jlong handle) {
    (void)env;
    (void)type;
    return (jlong)kairo_chd_size((const kairo_chd *)(intptr_t)handle);
}

JNIEXPORT jint JNICALL
Java_com_mrjackspade_kairo_frontend_ChdImage_nativeRead(JNIEnv *env, jclass type, jlong handle, jlong offset,
                                                          jbyteArray buffer, jint at, jint length) {
    kairo_chd *chd = (kairo_chd *)(intptr_t)handle;
    jbyte *bytes = (*env)->GetByteArrayElements(env, buffer, NULL);
    size_t read;
    (void)type;
    if (!bytes) return -1;
    read = kairo_chd_read(chd, (uint64_t)offset, bytes + at, (size_t)length);
    (*env)->ReleaseByteArrayElements(env, buffer, bytes, read == (size_t)-1 ? JNI_ABORT : 0);
    if (read == (size_t)-1) {
        throw_io(env, kairo_chd_error(chd));
        return -1;
    }
    return (jint)read;
}

JNIEXPORT jstring JNICALL
Java_com_mrjackspade_kairo_frontend_ChdImage_nativeCue(JNIEnv *env, jclass type, jlong handle, jstring binName) {
    const kairo_chd *chd = (const kairo_chd *)(intptr_t)handle;
    const char *name = (*env)->GetStringUTFChars(env, binName, NULL);
    size_t length;
    char *text;
    jstring result;
    (void)type;
    if (!name) return NULL;
    length = kairo_chd_cue(chd, name, NULL, 0);
    text = (char *)malloc(length + 1);
    if (!text) {
        (*env)->ReleaseStringUTFChars(env, binName, name);
        throw_io(env, "Out of memory");
        return NULL;
    }
    kairo_chd_cue(chd, name, text, length + 1);
    (*env)->ReleaseStringUTFChars(env, binName, name);
    result = (*env)->NewStringUTF(env, text);
    free(text);
    return result;
}

JNIEXPORT void JNICALL
Java_com_mrjackspade_kairo_frontend_ChdImage_nativeClose(JNIEnv *env, jclass type, jlong handle) {
    (void)env;
    (void)type;
    kairo_chd_close((kairo_chd *)(intptr_t)handle);
}
