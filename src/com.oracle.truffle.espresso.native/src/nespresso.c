/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
#include <jni.h>
#include <trufflenfi.h>
#include <stdlib.h>
#include <string.h>

// Sulong intrinsics.
extern void *truffle_deref_handle_for_managed(void *managed);
extern void *truffle_release_handle(void *handle);

#define EXPAND(...) __VA_ARGS__

#define JNI_FUNCTION_LIST(V) \
V(GetVersion) \
V(DefineClass) \
V(FindClass) \
V(FromReflectedMethod) \
V(FromReflectedField) \
V(ToReflectedMethod) \
V(GetSuperclass) \
V(IsAssignableFrom) \
V(ToReflectedField) \
V(Throw) \
V(ThrowNew) \
V(ExceptionOccurred) \
V(ExceptionDescribe) \
V(ExceptionClear) \
V(FatalError) \
V(PushLocalFrame) \
V(PopLocalFrame) \
V(DeleteLocalRef) \
V(NewLocalRef) \
V(EnsureLocalCapacity) \
V(AllocObject) \
V(GetObjectClass) \
V(IsInstanceOf) \
V(GetMethodID) \
V(GetFieldID) \
V(GetObjectField) \
V(GetBooleanField) \
V(GetByteField) \
V(GetCharField) \
V(GetShortField) \
V(GetIntField) \
V(GetLongField) \
V(GetFloatField) \
V(GetDoubleField) \
V(SetObjectField) \
V(SetBooleanField) \
V(SetByteField) \
V(SetCharField) \
V(SetShortField) \
V(SetIntField) \
V(SetLongField) \
V(SetFloatField) \
V(SetDoubleField) \
V(GetStaticMethodID) \
V(GetStaticFieldID) \
V(GetStaticObjectField) \
V(GetStaticBooleanField) \
V(GetStaticByteField) \
V(GetStaticCharField) \
V(GetStaticShortField) \
V(GetStaticIntField) \
V(GetStaticLongField) \
V(GetStaticFloatField) \
V(GetStaticDoubleField) \
V(SetStaticObjectField) \
V(SetStaticBooleanField) \
V(SetStaticByteField) \
V(SetStaticCharField) \
V(SetStaticShortField) \
V(SetStaticIntField) \
V(SetStaticLongField) \
V(SetStaticFloatField) \
V(SetStaticDoubleField) \
V(NewString) \
V(GetStringLength) \
V(GetStringChars) \
V(ReleaseStringChars) \
V(NewStringUTF) \
V(GetStringUTFLength) \
V(GetStringUTFChars) \
V(ReleaseStringUTFChars) \
V(GetArrayLength) \
V(NewObjectArray) \
V(GetObjectArrayElement) \
V(SetObjectArrayElement) \
V(NewBooleanArray) \
V(NewByteArray) \
V(NewCharArray) \
V(NewShortArray) \
V(NewIntArray) \
V(NewLongArray) \
V(NewFloatArray) \
V(NewDoubleArray) \
V(GetBooleanArrayElements) \
V(GetByteArrayElements) \
V(GetCharArrayElements) \
V(GetShortArrayElements) \
V(GetIntArrayElements) \
V(GetLongArrayElements) \
V(GetFloatArrayElements) \
V(GetDoubleArrayElements) \
V(ReleaseBooleanArrayElements) \
V(ReleaseByteArrayElements) \
V(ReleaseCharArrayElements) \
V(ReleaseShortArrayElements) \
V(ReleaseIntArrayElements) \
V(ReleaseLongArrayElements) \
V(ReleaseFloatArrayElements) \
V(ReleaseDoubleArrayElements) \
V(GetBooleanArrayRegion) \
V(GetByteArrayRegion) \
V(GetCharArrayRegion) \
V(GetShortArrayRegion) \
V(GetIntArrayRegion) \
V(GetLongArrayRegion) \
V(GetFloatArrayRegion) \
V(GetDoubleArrayRegion) \
V(SetBooleanArrayRegion) \
V(SetByteArrayRegion) \
V(SetCharArrayRegion) \
V(SetShortArrayRegion) \
V(SetIntArrayRegion) \
V(SetLongArrayRegion) \
V(SetFloatArrayRegion) \
V(SetDoubleArrayRegion) \
V(UnregisterNatives) \
V(MonitorEnter) \
V(MonitorExit) \
V(GetJavaVM) \
V(GetStringRegion) \
V(GetStringUTFRegion) \
V(GetPrimitiveArrayCritical) \
V(ReleasePrimitiveArrayCritical) \
V(GetStringCritical) \
V(ReleaseStringCritical) \
V(ExceptionCheck) \
V(GetDirectBufferAddress) \
V(GetDirectBufferCapacity) \
V(GetObjectRefType) \
V(IsSameObject) \
V(NewGlobalRef) \
V(DeleteGlobalRef) \
V(NewWeakGlobalRef) \
V(DeleteWeakGlobalRef) \
V(NewDirectByteBuffer)


#define TYPE_LIST2(V)  \
  V(jobject, Object)   \
  V(jboolean, Boolean) \
  V(jchar, Char)       \
  V(jbyte, Byte)       \
  V(jshort, Short)     \
  V(jint, Int)         \
  V(jfloat, Float)     \
  V(jdouble, Double)   \
  V(jlong, Long)       \
  V(void, Void)


#define VAR_jobject(x) jobject x =
#define VAR_jboolean(x) jboolean x =
#define VAR_jchar(x) jchar x =
#define VAR_jbyte(x) jbyte x =
#define VAR_jshort(x) jshort x =
#define VAR_jint(x) jint x =
#define VAR_jfloat(x) jfloat x =
#define VAR_jdouble(x) jdouble x =
#define VAR_jlong(x) jlong x =
#define VAR_void(x)

#define RETURN_jobject(x) return x
#define RETURN_jboolean(x) return x
#define RETURN_jchar(x) return x
#define RETURN_jbyte(x) return x
#define RETURN_jshort(x) return x
#define RETURN_jint(x) return x
#define RETURN_jfloat(x) return x
#define RETURN_jdouble(x) return x
#define RETURN_jlong(x) return x
#define RETURN_void(x) return

#define CONCAT_(X, Y) X ## Y
#define RETURN(returnType) CONCAT_(RETURN_, returnType)
#define VAR(returnType) CONCAT_(VAR_, returnType)

struct Varargs {
    const struct VarargsInterface* functions;
};

struct VarargsInterface {
  jboolean (*pop_boolean)(struct Varargs*);
  jbyte (*pop_byte)(struct Varargs*);
  jchar (*pop_char)(struct Varargs*);
  jshort (*pop_short)(struct Varargs*);
  jint (*pop_int)(struct Varargs*);
  jfloat (*pop_float)(struct Varargs*);
  jdouble (*pop_double)(struct Varargs*);
  jlong (*pop_long)(struct Varargs*);
  jobject (*pop_object)(struct Varargs*);
  void* (*pop_word)(struct Varargs*);
};

struct VarargsV {
    struct Varargs base;
    va_list args;
};

struct VarargsA {
    struct Varargs base;
    jvalue* args;
};

// valist varargs impl

static jboolean valist_pop_boolean(struct Varargs* varargs) {
    struct VarargsV* v = (struct VarargsV*) varargs;
    return (jboolean) (va_arg(v->args, jint) == 0 ? JNI_FALSE : JNI_TRUE);
}

static jbyte valist_pop_byte(struct Varargs* varargs) {
    struct VarargsV* v = (struct VarargsV*) varargs;
    return (jbyte) va_arg(v->args, jint);
}

static jchar valist_pop_char(struct Varargs* varargs) {
    struct VarargsV* v = (struct VarargsV*) varargs;
    return (jchar) va_arg(v->args, jint);
}

static jshort valist_pop_short(struct Varargs* varargs) {
    struct VarargsV* v = (struct VarargsV*) varargs;
    return (jshort) va_arg(v->args, jint);
}

static jint valist_pop_int(struct Varargs* varargs) {
    struct VarargsV* v = (struct VarargsV*) varargs;
    return (jint) va_arg(v->args, jint);
}

static jfloat valist_pop_float(struct Varargs* varargs) {
    struct VarargsV* v = (struct VarargsV*) varargs;
    return (jfloat) va_arg(v->args, jdouble);
}

static jdouble valist_pop_double(struct Varargs* varargs) {
    struct VarargsV* v = (struct VarargsV*) varargs;
    return (jdouble) va_arg(v->args, jdouble);
}

static jlong valist_pop_long(struct Varargs* varargs) {
    struct VarargsV* v = (struct VarargsV*) varargs;
    return (jlong) va_arg(v->args, jlong);
}

static jobject valist_pop_object(struct Varargs* varargs) {
    struct VarargsV* v = (struct VarargsV*) varargs;
    return (jobject) va_arg(v->args, jobject);
}

static void* valist_pop_word(struct Varargs* varargs) {
    struct VarargsV* v = (struct VarargsV*) varargs;
    return (void*) va_arg(v->args, void*);
}

// jvalue* varargs impl

static jboolean jvalues_pop_boolean(struct Varargs* varargs) {
    struct VarargsA* a = (struct VarargsA*) varargs;
    return a->args++->z;
}

static jbyte jvalues_pop_byte(struct Varargs* varargs) {
    struct VarargsA* a = (struct VarargsA*) varargs;
    return a->args++->b;
}

static jchar jvalues_pop_char(struct Varargs* varargs) {
    struct VarargsA* a = (struct VarargsA*) varargs;
    return a->args++->c;
}

static jshort jvalues_pop_short(struct Varargs* varargs) {
    struct VarargsA* a = (struct VarargsA*) varargs;
    return a->args++->s;
}

static jint jvalues_pop_int(struct Varargs* varargs) {
    struct VarargsA* a = (struct VarargsA*) varargs;
    return a->args++->i;
}

static jfloat jvalues_pop_float(struct Varargs* varargs) {
    struct VarargsA* a = (struct VarargsA*) varargs;
    return a->args++->f;
}

static jdouble jvalues_pop_double(struct Varargs* varargs) {
    struct VarargsA* a = (struct VarargsA*) varargs;
    return a->args++->d;
}

static jlong jvalues_pop_long(struct Varargs* varargs) {
    struct VarargsA* a = (struct VarargsA*) varargs;
    return a->args++->j;
}

static jobject jvalues_pop_object(struct Varargs* varargs) {
    struct VarargsA* a = (struct VarargsA*) varargs;
    return a->args++->l;
}

static void* jvalues_pop_word(struct Varargs* varargs) {
    struct VarargsA* a = (struct VarargsA*) varargs;
    return (void*) a->args++->l;
}

static const struct VarargsInterface valist_functions = {
  valist_pop_boolean, valist_pop_byte, valist_pop_char, valist_pop_short, valist_pop_int, valist_pop_float, valist_pop_double, valist_pop_long, valist_pop_object, valist_pop_word
};

static const struct VarargsInterface jvalues_functions = {
  jvalues_pop_boolean, jvalues_pop_byte, jvalues_pop_char, jvalues_pop_short, jvalues_pop_int, jvalues_pop_float, jvalues_pop_double, jvalues_pop_long, jvalues_pop_object, jvalues_pop_word
};

// Exported
jboolean pop_boolean(struct Varargs* varargs) {
  return varargs->functions->pop_boolean(varargs);
}

jbyte pop_byte(struct Varargs* varargs) {
  return varargs->functions->pop_byte(varargs);
}

jchar pop_char(struct Varargs* varargs) {
  return varargs->functions->pop_char(varargs);
}

jshort pop_short(struct Varargs* varargs) {
  return varargs->functions->pop_short(varargs);
}

jint pop_int(struct Varargs* varargs) {
  return varargs->functions->pop_int(varargs);
}

jfloat pop_float(struct Varargs* varargs) {
  return varargs->functions->pop_float(varargs);
}

jdouble pop_double(struct Varargs* varargs) {
  return varargs->functions->pop_double(varargs);
}

jlong pop_long(struct Varargs* varargs) {
  return varargs->functions->pop_long(varargs);
}

jobject pop_object(struct Varargs* varargs) {
  return varargs->functions->pop_object(varargs);
}

void* pop_word(struct Varargs* varargs) {
  return varargs->functions->pop_word(varargs);
}

struct NespressoEnv {
  #define CALL_METHOD(returnType, Type) \
    returnType (*Call##Type##MethodVarargs)(JNIEnv *env, jobject obj, jmethodID methodID, void* varargs); \
    returnType (*CallStatic##Type##MethodVarargs)(JNIEnv *env, jobject clazz, jmethodID methodID, void* varargs); \
    returnType (*CallNonvirtual##Type##MethodVarargs)(JNIEnv *env, jobject obj, jobject clazz, jmethodID methodID, void* varargs);

  TYPE_LIST2(CALL_METHOD)
  #undef CALL_METHOD

  // NewObject varargs
  jobject (*NewObjectVarargs)(JNIEnv *env, jclass clazz, jmethodID methodID, void* varargs);

  // RegisterNative (single method)
  jint (*RegisterNative)(JNIEnv *env, jclass clazz, const char* name, const char* signature, void* closure);
};

#define CALL_METHOD_BRIDGE(returnType, Type) \
returnType Call##Type##MethodV(JNIEnv *env, jobject obj, jmethodID methodID, va_list args) { \
  struct VarargsV varargs = { .base = { .functions = &valist_functions } }; \
  struct NespressoEnv *nespresso_env = (struct NespressoEnv*) (*env)->reserved0; \
  va_copy(varargs.args, args); \
  VAR(returnType)(result) nespresso_env->Call##Type##MethodVarargs(env, obj, methodID, &varargs); \
  va_end(varargs.args); \
  RETURN(returnType)(result); \
} \
returnType Call##Type##MethodA(JNIEnv *env, jobject obj, jmethodID methodID, const jvalue *args) { \
  struct VarargsA varargs = { .base = { .functions = &jvalues_functions }, .args = (void*) args}; \
  struct NespressoEnv *nespresso_env = (struct NespressoEnv*) (*env)->reserved0; \
  VAR(returnType)(result) nespresso_env->Call##Type##MethodVarargs(env, obj, methodID, &varargs); \
  RETURN(returnType)(result); \
} \
returnType Call##Type##Method(JNIEnv *env, jobject obj, jmethodID methodID, ...) { \
  va_list args; \
  va_start(args, methodID); \
  VAR(returnType)(result) Call##Type##MethodV(env, obj, methodID, args); \
  va_end(args); \
  RETURN(returnType)(result); \
}

#define CALL_STATIC_METHOD_BRIDGE(returnType, Type) \
returnType CallStatic##Type##MethodV(JNIEnv *env, jclass clazz, jmethodID methodID, va_list args) { \
  struct VarargsV varargs = { .base = { .functions = &valist_functions } }; \
  struct NespressoEnv *nespresso_env = (struct NespressoEnv*) (*env)->reserved0; \
  va_copy(varargs.args, args); \
  VAR(returnType)(result) nespresso_env->CallStatic##Type##MethodVarargs(env, clazz, methodID, &varargs); \
  va_end(varargs.args); \
  RETURN(returnType)(result); \
} \
returnType CallStatic##Type##MethodA(JNIEnv *env, jclass clazz, jmethodID methodID, const jvalue *args) { \
  struct VarargsA varargs = { .base = { .functions = &jvalues_functions } , .args = (void*) args}; \
  struct NespressoEnv *nespresso_env = (struct NespressoEnv*) (*env)->reserved0; \
  VAR(returnType)(result) nespresso_env->CallStatic##Type##MethodVarargs(env, clazz, methodID, &varargs); \
  RETURN(returnType)(result); \
} \
returnType CallStatic##Type##Method(JNIEnv *env, jclass clazz, jmethodID methodID, ...) { \
  va_list args; \
  va_start(args, methodID); \
  VAR(returnType)(result) CallStatic##Type##MethodV(env, clazz, methodID, args); \
  va_end(args); \
  RETURN(returnType)(result); \
}

#define CALL_NON_VIRTUAL_METHOD_BRIDGE(returnType, Type) \
returnType CallNonvirtual##Type##MethodV(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, va_list args) { \
  struct VarargsV varargs = { .base = { .functions = &valist_functions } }; \
  va_copy(varargs.args, args); \
  struct NespressoEnv *nespresso_env = (struct NespressoEnv*) (*env)->reserved0; \
  VAR(returnType)(result) nespresso_env->CallNonvirtual##Type##MethodVarargs(env, obj, clazz, methodID, &varargs); \
  RETURN(returnType)(result); \
} \
returnType CallNonvirtual##Type##MethodA(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, const jvalue *args) { \
  struct VarargsA varargs = { .base = { .functions = &jvalues_functions }, .args = (void*) args}; \
  struct NespressoEnv *nespresso_env = (struct NespressoEnv*) (*env)->reserved0; \
  VAR(returnType)(result) nespresso_env->CallNonvirtual##Type##MethodVarargs(env, obj, clazz, methodID, &varargs); \
  RETURN(returnType)(result); \
} \
returnType CallNonvirtual##Type##Method(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...) { \
  va_list args; \
  va_start(args, methodID); \
  VAR(returnType)(result) CallNonvirtual##Type##MethodV(env, obj, clazz, methodID, args); \
  va_end(args); \
  RETURN(returnType)(result); \
}

TYPE_LIST2(CALL_METHOD_BRIDGE)

TYPE_LIST2(CALL_STATIC_METHOD_BRIDGE)

TYPE_LIST2(CALL_NON_VIRTUAL_METHOD_BRIDGE)

#define MAKE_METHOD(unused, Type) (Call##Type##Method)
#define MAKE_STATIC_METHOD(unused, Type) (CallStatic##Type##Method)
#define MAKE_NON_VIRTUAL_METHOD(unused, Type) (CallNonvirtual##Type##Method)
#define MAKE_METHOD_A(unused, Type) (Call##Type##MethodA)
#define MAKE_METHOD_V(unused, Type) (Call##Type##MethodV)
#define MAKE_STATIC_METHOD_A(unused, Type) (CallStatic##Type##MethodA)
#define MAKE_STATIC_METHOD_V(unused, Type) (CallStatic##Type##MethodV)
#define MAKE_NON_VIRTUAL_METHOD_A(unused, Type) (CallNonvirtual##Type##MethodA)
#define MAKE_NON_VIRTUAL_METHOD_V(unused, Type) (CallNonvirtual##Type##MethodV)


#define MAKE_METHOD_VARARGS(unused, Type) (Call##Type##MethodVarargs)
#define MAKE_STATIC_METHOD_VARARGS(unused, Type) (CallStatic##Type##MethodVarargs)
#define MAKE_NON_VIRTUAL_METHOD_VARARGS(unused, Type) (CallNonvirtual##Type##MethodVarargs)

// TODO(peterssen): RegisterNative is not a varargs.
#define VARARGS_METHOD_LIST(V) \
  EXPAND(TYPE_LIST2(V MAKE_METHOD_VARARGS)) \
  EXPAND(TYPE_LIST2(V MAKE_STATIC_METHOD_VARARGS)) \
  EXPAND(TYPE_LIST2(V MAKE_NON_VIRTUAL_METHOD_VARARGS)) \
  V(NewObjectVarargs) \
  V(RegisterNative)


#define BRIDGE_METHOD_LIST(V) \
  EXPAND(TYPE_LIST2(V MAKE_METHOD)) \
  EXPAND(TYPE_LIST2(V MAKE_METHOD_A)) \
  EXPAND(TYPE_LIST2(V MAKE_METHOD_V)) \
  EXPAND(TYPE_LIST2(V MAKE_STATIC_METHOD)) \
  EXPAND(TYPE_LIST2(V MAKE_STATIC_METHOD_A)) \
  EXPAND(TYPE_LIST2(V MAKE_STATIC_METHOD_V)) \
  EXPAND(TYPE_LIST2(V MAKE_NON_VIRTUAL_METHOD)) \
  EXPAND(TYPE_LIST2(V MAKE_NON_VIRTUAL_METHOD_A)) \
  EXPAND(TYPE_LIST2(V MAKE_NON_VIRTUAL_METHOD_V)) \
  V(NewObject) \
  V(NewObjectA) \
  V(NewObjectV) \
  V(RegisterNatives)


jobject NewObjectV(JNIEnv *env, jclass clazz, jmethodID methodID, va_list args) {
  struct VarargsV varargs = { .base = { .functions = &valist_functions } };
  struct NespressoEnv *nespresso_env = (struct NespressoEnv*) (*env)->reserved0;
  va_copy(varargs.args, args);
  jobject result = nespresso_env->NewObjectVarargs(env, clazz, methodID, &varargs);
  va_end(varargs.args);
  return result;
}

jobject NewObjectA(JNIEnv *env, jclass clazz, jmethodID methodID, const jvalue *args) {
  struct VarargsA varargs = { .base = { .functions = &jvalues_functions }, .args = (jvalue*) args };
  struct NespressoEnv *nespresso_env = (struct NespressoEnv*) (*env)->reserved0;
  return nespresso_env->NewObjectVarargs(env, clazz, methodID, &varargs);
}

jobject NewObject(JNIEnv *env, jclass clazz, jmethodID methodID, ...) {
  va_list args;
  va_start(args, methodID);
  jobject result = (*env)->NewObjectV(env, clazz, methodID, args);
  va_end(args);
  return result;
}

jint RegisterNatives(JNIEnv *env, jclass clazz, const JNINativeMethod *methods, jint nMethods) {

  struct NespressoEnv *nespresso_env = (struct NespressoEnv*) (*env)->reserved0;
  jint ret = JNI_OK;
  for (jint i = 0; i < nMethods; ++i) {
    ret = nespresso_env->RegisterNative(env, clazz, methods[i].name, methods[i].signature, methods[i].fnPtr);
    if (ret != JNI_OK) {
        break;
    }
  }
  return ret;
}

static void unset_function_error() {
  fprintf(stderr, "Call to uninitialized JNI function slot\n");
  exit(-1);
}

void* dupClosureRef(TruffleEnv *truffle_env, void* closure) {
    if (truffle_env != NULL) {
        (*truffle_env)->newClosureRef(truffle_env, closure);
    } else {
        closure = truffle_deref_handle_for_managed(closure);
    }
    return closure;
}

JNIEnv* initializeNativeContext(TruffleEnv *truffle_env, void* (*fetch_by_name)(const char *)) {
  JNIEnv* env = (JNIEnv*) malloc(sizeof(*env));
  struct JNINativeInterface_* jni_impl = malloc(sizeof(*jni_impl));
  struct NespressoEnv* nespresso_env = (struct NespressoEnv*) malloc(sizeof(*nespresso_env));

  int fnCount = sizeof(*jni_impl) / sizeof(void*);
  for (int i = 0; i < fnCount; ++i) {
    ((void**)jni_impl)[i] = &unset_function_error;
  }

  *env = jni_impl;

  jni_impl->reserved0 = nespresso_env;

  // Fetch Java ... varargs methods.
  #define INIT_VARARGS_METHOD__(fn_name) \
    *(void**)(&nespresso_env->fn_name) = fetch_by_name(#fn_name);

    VARARGS_METHOD_LIST(INIT_VARARGS_METHOD__)
  #undef INIT_VARARGS_METHOD__

  #define INIT__(fn_name) \
    jni_impl->fn_name = fetch_by_name(#fn_name);

  JNI_FUNCTION_LIST(INIT__)
  #undef INIT__

  // Put ... bridges in the env.
  #define INIT_NATIVE_METHOD__(fn_name) \
    jni_impl->fn_name = &fn_name;

    BRIDGE_METHOD_LIST(INIT_NATIVE_METHOD__)
  #undef INIT_NATIVE_METHOD__

  return env;
}

static void releaseClosure(TruffleEnv *truffle_env, void* closure) {
    if (truffle_env != NULL) {
        (*truffle_env)->releaseClosureRef(truffle_env, closure);
    } else {
        truffle_release_handle(closure);
    }
}

void disposeNativeContext(TruffleEnv* truffle_env, JNIEnv* env) {  
  struct JNINativeInterface_* jni_impl = (struct JNINativeInterface_*) *env;
  struct NespressoEnv *nespresso_env = (struct NespressoEnv *) (*env)->reserved0;

  // Dispose methods implemented in Java.
  #define DISPOSE__(fn_name) \
     releaseClosure(truffle_env, jni_impl->fn_name); \
     jni_impl->fn_name = NULL;

  JNI_FUNCTION_LIST(DISPOSE__)
  #undef DISPOSE__

  // Dispose (nullify) native methods (bridges).
  #define DISPOSE_BRIDGE__(fn_name) \
     jni_impl->fn_name = NULL;

    BRIDGE_METHOD_LIST(DISPOSE_BRIDGE__)
  #undef DISPOSE_BRIDGE__

  // Dispose Nespresso-specific methods implemented in Java (e.g. Java varargs).
  #define DISPOSE_VARARGS_METHOD__(fn_name) \
    releaseClosure(truffle_env, nespresso_env->fn_name); \
    *(void**)(&nespresso_env->fn_name) = NULL;

  VARARGS_METHOD_LIST(DISPOSE_VARARGS_METHOD__)
  #undef DISPOSE_VARARG_METHOD__

  free(nespresso_env);
  jni_impl->reserved0 = NULL;

  free(jni_impl);
  *env = NULL;

  free(env);
}
