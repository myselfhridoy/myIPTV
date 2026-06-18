#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_streambd_iptv_utils_CryptoUtils_getNativeAesKey(
        JNIEnv* env,
        jobject /* this */) {
    std::string aesKey = "X3#yS@6k~u&hr$0z";
    return env->NewStringUTF(aesKey.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_streambd_iptv_utils_CryptoUtils_getNativeAesIv(
        JNIEnv* env,
        jobject /* this */) {
    std::string aesIv = "n!0kfd&re%3#pw*s";
    return env->NewStringUTF(aesIv.c_str());
}
