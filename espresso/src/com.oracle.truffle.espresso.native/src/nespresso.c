/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "nespresso.h"

#include <mokapot.h>
#include <jni.h>
#include <trufflenfi.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <stdint.h>

#if defined(_WIN32)
#define va_copy(dest, src) (dest = src)
// Sulong is not yet supported on Windows.
void *truffle_deref_handle_for_managed(void *managed) {
  return NULL;
}
void *truffle_release_handle(void *handle) {
  return NULL;
}
#else
// Sulong intrinsics.
extern void *truffle_deref_handle_for_managed(void *managed);
extern void *truffle_release_handle(void *handle);
#endif

#define EXPAND(...) __VA_ARGS__
#define EXPAND2(...) EXPAND(EXPAND(__VA_ARGS__))

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


#define VAR_jobject(x) x =
#define VAR_jboolean(x) x =
#define VAR_jchar(x) x =
#define VAR_jbyte(x) x =
#define VAR_jshort(x) x =
#define VAR_jint(x) x =
#define VAR_jfloat(x) x =
#define VAR_jdouble(x) x =
#define VAR_jlong(x) x =
#define VAR_void(x)

#define DECL_jobject(x) jobject x;
#define DECL_jboolean(x) jboolean x;
#define DECL_jchar(x) jchar x;
#define DECL_jbyte(x) jbyte x;
#define DECL_jshort(x) jshort x;
#define DECL_jint(x) jint x;
#define DECL_jfloat(x) jfloat x;
#define DECL_jdouble(x) jdouble x;
#define DECL_jlong(x) jlong x;
#define DECL_void(x)

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
#define DECL(returnType) CONCAT_(DECL_, returnType)

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
JNIEXPORT jboolean JNICALL pop_boolean(struct Varargs* varargs) {
  return varargs->functions->pop_boolean(varargs);
}

JNIEXPORT jbyte JNICALL pop_byte(struct Varargs* varargs) {
  return varargs->functions->pop_byte(varargs);
}

JNIEXPORT jchar JNICALL pop_char(struct Varargs* varargs) {
  return varargs->functions->pop_char(varargs);
}

JNIEXPORT jshort JNICALL pop_short(struct Varargs* varargs) {
  return varargs->functions->pop_short(varargs);
}

JNIEXPORT jint JNICALL pop_int(struct Varargs* varargs) {
  return varargs->functions->pop_int(varargs);
}

JNIEXPORT jfloat JNICALL pop_float(struct Varargs* varargs) {
  return varargs->functions->pop_float(varargs);
}

JNIEXPORT jdouble JNICALL pop_double(struct Varargs* varargs) {
  return varargs->functions->pop_double(varargs);
}

JNIEXPORT jlong JNICALL pop_long(struct Varargs* varargs) {
  return varargs->functions->pop_long(varargs);
}

JNIEXPORT jobject JNICALL pop_object(struct Varargs* varargs) {
  return varargs->functions->pop_object(varargs);
}

JNIEXPORT void* JNICALL pop_word(struct Varargs* varargs) {
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
  DECL(returnType)(result) \
  struct VarargsV varargs = { /* .base = */ { /* .functions = */ &valist_functions } }; \
  struct NespressoEnv *nespresso_env = (struct NespressoEnv*) (*env)->reserved0; \
  va_copy(varargs.args, args); \
  VAR(returnType)(result) nespresso_env->Call##Type##MethodVarargs(env, obj, methodID, &varargs); \
  va_end(varargs.args); \
  RETURN(returnType)(result); \
} \
returnType Call##Type##MethodA(JNIEnv *env, jobject obj, jmethodID methodID, const jvalue *args) { \
  DECL(returnType)(result) \
  struct VarargsA varargs = { /* .base = */ { /* .functions = */ &jvalues_functions }, /* .args = */ (void*) args}; \
  struct NespressoEnv *nespresso_env = (struct NespressoEnv*) (*env)->reserved0; \
  VAR(returnType)(result) nespresso_env->Call##Type##MethodVarargs(env, obj, methodID, &varargs); \
  RETURN(returnType)(result); \
} \
returnType Call##Type##Method(JNIEnv *env, jobject obj, jmethodID methodID, ...) { \
  DECL(returnType)(result) \
  va_list args; \
  va_start(args, methodID); \
  VAR(returnType)(result) Call##Type##MethodV(env, obj, methodID, args); \
  va_end(args); \
  RETURN(returnType)(result); \
}

#define CALL_STATIC_METHOD_BRIDGE(returnType, Type) \
returnType CallStatic##Type##MethodV(JNIEnv *env, jclass clazz, jmethodID methodID, va_list args) { \
  DECL(returnType)(result) \
  struct VarargsV varargs = { /* .base = */ { /* .functions = */ &valist_functions } }; \
  struct NespressoEnv *nespresso_env = (struct NespressoEnv*) (*env)->reserved0; \
  va_copy(varargs.args, args); \
  VAR(returnType)(result) nespresso_env->CallStatic##Type##MethodVarargs(env, clazz, methodID, &varargs); \
  va_end(varargs.args); \
  RETURN(returnType)(result); \
} \
returnType CallStatic##Type##MethodA(JNIEnv *env, jclass clazz, jmethodID methodID, const jvalue *args) { \
  DECL(returnType)(result) \
  struct VarargsA varargs = { /* .base = */ { /* .functions = */ &jvalues_functions } , /* .args = */ (void*) args}; \
  struct NespressoEnv *nespresso_env = (struct NespressoEnv*) (*env)->reserved0; \
  VAR(returnType)(result) nespresso_env->CallStatic##Type##MethodVarargs(env, clazz, methodID, &varargs); \
  RETURN(returnType)(result); \
} \
returnType CallStatic##Type##Method(JNIEnv *env, jclass clazz, jmethodID methodID, ...) { \
  DECL(returnType)(result) \
  va_list args; \
  va_start(args, methodID); \
  VAR(returnType)(result) CallStatic##Type##MethodV(env, clazz, methodID, args); \
  va_end(args); \
  RETURN(returnType)(result); \
}

#define CALL_NON_VIRTUAL_METHOD_BRIDGE(returnType, Type) \
returnType CallNonvirtual##Type##MethodV(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, va_list args) { \
  DECL(returnType)(result) \
  struct VarargsV varargs = { /* .base = */ { /* .functions = */ &valist_functions } }; \
  struct NespressoEnv *nespresso_env = (struct NespressoEnv*) (*env)->reserved0; \
  va_copy(varargs.args, args); \
  VAR(returnType)(result) nespresso_env->CallNonvirtual##Type##MethodVarargs(env, obj, clazz, methodID, &varargs); \
  RETURN(returnType)(result); \
} \
returnType CallNonvirtual##Type##MethodA(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, const jvalue *args) { \
  DECL(returnType)(result) \
  struct VarargsA varargs = { /* .base = */ { /* .functions = */ &jvalues_functions }, /* .args = */ (void*) args}; \
  struct NespressoEnv *nespresso_env = (struct NespressoEnv*) (*env)->reserved0; \
  VAR(returnType)(result) nespresso_env->CallNonvirtual##Type##MethodVarargs(env, obj, clazz, methodID, &varargs); \
  RETURN(returnType)(result); \
} \
returnType CallNonvirtual##Type##Method(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...) { \
  DECL(returnType)(result) \
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
  EXPAND2(TYPE_LIST2(V MAKE_METHOD_VARARGS)) \
  EXPAND2(TYPE_LIST2(V MAKE_STATIC_METHOD_VARARGS)) \
  EXPAND2(TYPE_LIST2(V MAKE_NON_VIRTUAL_METHOD_VARARGS)) \
  V(NewObjectVarargs) \
  V(RegisterNative)


#define BRIDGE_METHOD_LIST(V) \
  EXPAND2(TYPE_LIST2(V MAKE_METHOD)) \
  EXPAND2(TYPE_LIST2(V MAKE_METHOD_A)) \
  EXPAND2(TYPE_LIST2(V MAKE_METHOD_V)) \
  EXPAND2(TYPE_LIST2(V MAKE_STATIC_METHOD)) \
  EXPAND2(TYPE_LIST2(V MAKE_STATIC_METHOD_A)) \
  EXPAND2(TYPE_LIST2(V MAKE_STATIC_METHOD_V)) \
  EXPAND2(TYPE_LIST2(V MAKE_NON_VIRTUAL_METHOD)) \
  EXPAND2(TYPE_LIST2(V MAKE_NON_VIRTUAL_METHOD_A)) \
  EXPAND2(TYPE_LIST2(V MAKE_NON_VIRTUAL_METHOD_V)) \
  V(NewObject) \
  V(NewObjectA) \
  V(NewObjectV) \
  V(RegisterNatives) \
  V(GetJavaVM)


jobject NewObjectV(JNIEnv *env, jclass clazz, jmethodID methodID, va_list args) {
  jobject result;
  struct VarargsV varargs = { /* .base = */ { /* .functions = */ &valist_functions } };
  struct NespressoEnv *nespresso_env = (struct NespressoEnv*) (*env)->reserved0;
  va_copy(varargs.args, args);
  result = nespresso_env->NewObjectVarargs(env, clazz, methodID, &varargs);
  va_end(varargs.args);
  return result;
}

jobject NewObjectA(JNIEnv *env, jclass clazz, jmethodID methodID, const jvalue *args) {
  struct VarargsA varargs = { /* .base = */ { /* .functions = */ &jvalues_functions }, /* .args = */ (jvalue*) args };
  struct NespressoEnv *nespresso_env = (struct NespressoEnv*) (*env)->reserved0;
  return nespresso_env->NewObjectVarargs(env, clazz, methodID, &varargs);
}

jobject NewObject(JNIEnv *env, jclass clazz, jmethodID methodID, ...) {
  jobject result;
  va_list args;
  va_start(args, methodID);
  result = (*env)->NewObjectV(env, clazz, methodID, args);
  va_end(args);
  return result;
}

jint RegisterNatives(JNIEnv *env, jclass clazz, const JNINativeMethod *methods, jint nMethods) {
  struct NespressoEnv *nespresso_env = (struct NespressoEnv*) (*env)->reserved0;
  jint ret = JNI_OK;
  jint i;
  for (i = 0; i < nMethods; ++i) {
    ret = nespresso_env->RegisterNative(env, clazz, methods[i].name, methods[i].signature, methods[i].fnPtr);
    if (ret != JNI_OK) {
        break;
    }
  }
  return ret;
}

jint GetJavaVM(JNIEnv *env, JavaVM **vmPtr) {
  MokapotEnv* moka_env = NULL;
  JavaVM* vm = NULL;
  if (vmPtr == NULL) {
    return JNI_ERR;
  }

  moka_env = (MokapotEnv*) (*env)->reserved1;
  if (moka_env == NULL) {
    fprintf(stderr, "GetJavaVM: Passed JNIEnv* has no MokapotEnv* associated" OS_NEWLINE_STR);
    return JNI_ERR;
  }

  vm = (*moka_env)->vm;

  // If there's an isolate-aware JavaVM, find it.
  if ((*vm)->reserved1 == MOKA_AMERICANO) {
    vm = (JavaVM*) (*vm)->reserved2;
    // MOKA_AMERICANO JavaVM can only point to a MOKA_LATTE.
    if ((*vm)->reserved1 != MOKA_LATTE) {
      fprintf(stderr, "GetJavaVM: not a MOKA_LATTE" OS_NEWLINE_STR);
      return JNI_ERR;
    }
  } else if ((*vm)->reserved1 != MOKA_RISTRETTO) {
    fprintf(stderr, "GetJavaVM: not a MOKA_RISTRETTO" OS_NEWLINE_STR);
    return JNI_ERR;
  }

  *vmPtr = vm;
  return JNI_OK;
}

static void unset_function_error() {
  fprintf(stderr, "Call to uninitialized JNI function slot" OS_NEWLINE_STR);
  exit(-1);
}

JNIEXPORT JNIEnv* JNICALL initializeNativeContext(void* (*fetch_by_name)(const char *)) {
  JNIEnv* env = (JNIEnv*) malloc(sizeof(*env));
  struct JNINativeInterface_* jni_impl = malloc(sizeof(*jni_impl));
  struct NespressoEnv* nespresso_env = (struct NespressoEnv*) malloc(sizeof(*nespresso_env));

  int fnCount = sizeof(*jni_impl) / sizeof(void*);
  int i;
  for (i = 0; i < fnCount; ++i) {
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

JNIEXPORT void JNICALL disposeNativeContext(JNIEnv* env, void (*release_closure)(void *)) {
  struct JNINativeInterface_* jni_impl = (struct JNINativeInterface_*) *env;
  struct NespressoEnv *nespresso_env = (struct NespressoEnv *) (*env)->reserved0;

  // Dispose methods implemented in Java.
  #define DISPOSE__(fn_name) \
    if (release_closure != NULL) { \
      release_closure(jni_impl->fn_name); \
    } \
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
    if (release_closure != NULL) { \
      release_closure(nespresso_env->fn_name); \
    } \
    *(void**)(&nespresso_env->fn_name) = NULL;

  VARARGS_METHOD_LIST(DISPOSE_VARARGS_METHOD__)
  #undef DISPOSE_VARARG_METHOD__

  free(nespresso_env);
  jni_impl->reserved0 = NULL;

  free(jni_impl);
  *env = NULL;

  free(env);
}

JNIEXPORT void * JNICALL allocateMemory(size_t size) {
  return malloc(size);
}

JNIEXPORT void JNICALL freeMemory(void *ptr) {
  free(ptr);
}

JNIEXPORT void * JNICALL reallocateMemory(void *ptr, size_t new_size) {
  return realloc(ptr, new_size);
}

JNIEXPORT jlong JNICALL get_SIZE_MAX() {
  return (jlong) SIZE_MAX;
}
