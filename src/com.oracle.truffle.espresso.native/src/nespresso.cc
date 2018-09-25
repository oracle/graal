#include <cstdio>
#include <cstdlib>
#include <cstring>

#include <jni.h>
#include "com_oracle_truffle_espresso_jni_JniEnv.h"
#include <trufflenfi.h>

/* double increment_DOUBLE(double arg) {
    return arg + 1;
}
   
double callback_DOUBLE(double (*fn)(double), double arg) {
    return fn(arg + 1) * 2;
}

typedef double (*fnptr_DOUBLE)(double);

fnptr_DOUBLE callback_ret_DOUBLE() {
    return increment_DOUBLE;
}

double pingpong_DOUBLE(TruffleEnv *env, fnptr_DOUBLE (*wrapFn)(TruffleEnv *env, fnptr_DOUBLE), double arg) {
    fnptr_DOUBLE wrapped = wrapFn(env, increment_DOUBLE);
    int ret = wrapped(arg + 1) * 2;
    (*env)->releaseClosureRef(env, wrapped);
    return ret;
}
 */

JNIEnv* createJniEnv(TruffleEnv *truffle_env, void* (*fetch_function)(const char *)) {
    JNIEnv *env = (JNIEnv*) malloc(sizeof(JNIEnv));
    memset(env, 0, sizeof(JNIEnv));

    void *fn = fetch_function("GetArrayLength");    
    truffle_env->newClosureRef(fn);    
    ((void*)((*env).*GetArrayLength)) = fn;

    return env;
}

void disposeJniEnv(TruffleEnv *truffle_env, JNIEnv *env) {
    void *fn = env->GetArrayLength;
    truffle_env->releaseClosureRef(fn);
    env->GetArrayLength = NULL;

    free(env);     
}

JNIEXPORT jlong JNICALL Java_com_oracle_truffle_espresso_jni_JniEnv_createEnv
  (JNIEnv *env, jclass unused) {
    return 0;      
}

JNIEXPORT void JNICALL Java_com_oracle_truffle_espresso_jni_JniEnv_disposeEnv
  (JNIEnv *env, jclass unused, jlong jni_env_ptr) {
      // nop
}
