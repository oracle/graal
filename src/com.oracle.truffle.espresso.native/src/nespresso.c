#include <jni.h>
#include <ffi.h>
#include <trufflenfi.h>
#include <stdlib.h>
#include <string.h>

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
V(NewGlobalRef) \
V(DeleteGlobalRef) \
V(DeleteLocalRef) \
V(IsSameObject) \
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
V(RegisterNatives) \
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
V(NewWeakGlobalRef) \
V(DeleteWeakGlobalRef) \
V(ExceptionCheck) \
V(NewDirectByteBuffer) \
V(GetDirectBufferAddress) \
V(GetDirectBufferCapacity) \
V(GetObjectRefType)

// Varargs
#define NON_JAVA_IMPL(V) \
V(NewObject) \
V(NewObjectV) \
V(NewObjectA) \
V(CallObjectMethod) \
V(CallObjectMethodV) \
V(CallObjectMethodA) \
V(CallBooleanMethod) \
V(CallBooleanMethodV) \
V(CallBooleanMethodA) \
V(CallByteMethod) \
V(CallByteMethodV) \
V(CallByteMethodA) \
V(CallCharMethod) \
V(CallCharMethodV) \
V(CallCharMethodA) \
V(CallShortMethod) \
V(CallShortMethodV) \
V(CallShortMethodA) \
V(CallIntMethod) \
V(CallIntMethodV) \
V(CallIntMethodA) \
V(CallLongMethod) \
V(CallLongMethodV) \
V(CallLongMethodA) \
V(CallFloatMethod) \
V(CallFloatMethodV) \
V(CallFloatMethodA) \
V(CallDoubleMethod) \
V(CallDoubleMethodV) \
V(CallDoubleMethodA) \
V(CallVoidMethod) \
V(CallVoidMethodV) \
V(CallVoidMethodA) \
V(CallStaticObjectMethod) \
V(CallStaticObjectMethodV) \
V(CallStaticObjectMethodA) \
V(CallStaticBooleanMethod) \
V(CallStaticBooleanMethodV) \
V(CallStaticBooleanMethodA) \
V(CallStaticByteMethod) \
V(CallStaticByteMethodV) \
V(CallStaticByteMethodA) \
V(CallStaticCharMethod) \
V(CallStaticCharMethodV) \
V(CallStaticCharMethodA) \
V(CallStaticShortMethod) \
V(CallStaticShortMethodV) \
V(CallStaticShortMethodA) \
V(CallStaticIntMethod) \
V(CallStaticIntMethodV) \
V(CallStaticIntMethodA) \
V(CallStaticLongMethod) \
V(CallStaticLongMethodV) \
V(CallStaticLongMethodA) \
V(CallStaticFloatMethod) \
V(CallStaticFloatMethodV) \
V(CallStaticFloatMethodA) \
V(CallStaticDoubleMethod) \
V(CallStaticDoubleMethodV) \
V(CallStaticDoubleMethodA) \
V(CallStaticVoidMethod) \
V(CallStaticVoidMethodV) \
V(CallStaticVoidMethodA) \
V(CallNonvirtualObjectMethod) \
V(CallNonvirtualObjectMethodV) \
V(CallNonvirtualObjectMethodA) \
V(CallNonvirtualBooleanMethod) \
V(CallNonvirtualBooleanMethodV) \
V(CallNonvirtualBooleanMethodA) \
V(CallNonvirtualByteMethod) \
V(CallNonvirtualByteMethodV) \
V(CallNonvirtualByteMethodA) \
V(CallNonvirtualCharMethod) \
V(CallNonvirtualCharMethodV) \
V(CallNonvirtualCharMethodA) \
V(CallNonvirtualShortMethod) \
V(CallNonvirtualShortMethodV) \
V(CallNonvirtualShortMethodA) \
V(CallNonvirtualIntMethod) \
V(CallNonvirtualIntMethodV) \
V(CallNonvirtualIntMethodA) \
V(CallNonvirtualLongMethod) \
V(CallNonvirtualLongMethodV) \
V(CallNonvirtualLongMethodA) \
V(CallNonvirtualFloatMethod) \
V(CallNonvirtualFloatMethodV) \
V(CallNonvirtualFloatMethodA) \
V(CallNonvirtualDoubleMethod) \
V(CallNonvirtualDoubleMethodV) \
V(CallNonvirtualDoubleMethodA) \
V(CallNonvirtualVoidMethod) \
V(CallNonvirtualVoidMethodV) \
V(CallNonvirtualVoidMethodA)

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
};

struct VarargsV {
    struct Varargs base;
    va_list args;
};

struct VarargsA {
    struct Varargs base;
    jvalue* args;
};

static jboolean valist_pop_boolean(struct Varargs* varargs) {
    struct VarargsV* s = (struct VarargsV*) varargs;
    return (jboolean) (va_arg(s->args, jint) == 0 ? JNI_FALSE : JNI_TRUE);
}

static jbyte valist_pop_byte(struct Varargs* varargs) {
    struct VarargsV* s = (struct VarargsV*) varargs;
    return (jbyte) va_arg(s->args, jint);  
}

static jchar valist_pop_char(struct Varargs* varargs) {
    struct VarargsV* s = (struct VarargsV*) varargs;
    return (jchar) va_arg(s->args, jint);  
}

static jshort valist_pop_short(struct Varargs* varargs) {
    struct VarargsV* s = (struct VarargsV*) varargs;
    return (jshort) va_arg(s->args, jint);  
}

static jint valist_pop_int(struct Varargs* varargs) {
    struct VarargsV* s = (struct VarargsV*) varargs;
    return (jint) va_arg(s->args, jint);  
}

static jfloat valist_pop_float(struct Varargs* varargs) {
    struct VarargsV* s = (struct VarargsV*) varargs;
    return (jfloat) va_arg(s->args, jdouble);  
}

static jdouble valist_pop_double(struct Varargs* varargs) {
    struct VarargsV* s = (struct VarargsV*) varargs;
    return (jdouble) va_arg(s->args, jdouble);  
}

static jlong valist_pop_long(struct Varargs* varargs) {
    struct VarargsV* s = (struct VarargsV*) varargs;
    return (jlong) va_arg(s->args, jlong);  
}

static jobject valist_pop_object(struct Varargs* varargs) {
    struct VarargsV* s = (struct VarargsV*) varargs;
    return (jobject) va_arg(s->args, jobject);  
}

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
    return a->args++->f;
  
}
static jlong jvalues_pop_long(struct Varargs* varargs) {
    struct VarargsA* a = (struct VarargsA*) varargs;
    return a->args++->j;
  
}
static jobject jvalues_pop_object(struct Varargs* varargs) {
    struct VarargsA* a = (struct VarargsA*) varargs;
    return a->args++->l;
  
}

static const struct VarargsInterface valist_functions = {
  valist_pop_boolean, valist_pop_byte, valist_pop_char, valist_pop_short, valist_pop_int, valist_pop_float, valist_pop_double, valist_pop_long, valist_pop_object
};

static const struct VarargsInterface jvalues_functions = {
  jvalues_pop_boolean, jvalues_pop_byte, jvalues_pop_char, jvalues_pop_short, jvalues_pop_int, jvalues_pop_float, jvalues_pop_double, jvalues_pop_long, jvalues_pop_object
};

// Exported
jboolean pop_boolean(jlong ptr) {
  struct Varargs *varargs = (struct Varargs*) ptr;
  return varargs->functions->pop_boolean(varargs);
}

jbyte pop_byte(jlong ptr) {
  struct Varargs *varargs = (struct Varargs*) ptr;
  return varargs->functions->pop_byte(varargs);
}

jchar pop_char(jlong ptr) {
  struct Varargs *varargs = (struct Varargs*) ptr;
  return varargs->functions->pop_char(varargs);
}

jshort pop_short(jlong ptr) {
  struct Varargs *varargs = (struct Varargs*) ptr;
  return varargs->functions->pop_short(varargs);
}

jint pop_int(jlong ptr) {
  struct Varargs *varargs = (struct Varargs*) ptr;
  return varargs->functions->pop_int(varargs);
}

jfloat pop_float(jlong ptr) {
  struct Varargs *varargs = (struct Varargs*) ptr;
  return varargs->functions->pop_float(varargs);
}

jdouble pop_double(jlong ptr) {
  struct Varargs *varargs = (struct Varargs*) ptr;
  return varargs->functions->pop_double(varargs);
}

jlong pop_long(jlong ptr) {
  struct Varargs *varargs = (struct Varargs*) ptr;
  return varargs->functions->pop_long(varargs);
}

jobject pop_object(jlong ptr) {
  struct Varargs *varargs = (struct Varargs*) ptr;
  return varargs->functions->pop_object(varargs);
}

struct NespressoEnv {
  // TODO(peterssen): Add C++ env-less methods.
  #define CALL_METHOD(returnType, Type) \
    returnType (*Call##Type##MethodVarargs)(JNIEnv *env, jobject obj, jmethodID methodID, jlong varargs); \
    returnType (*CallStatic##Type##MethodVarargs)(JNIEnv *env, jobject clazz, jmethodID methodID, jlong varargs); \
    returnType (*CallNonvirtual##Type##MethodVarargs)(JNIEnv *env, jobject obj, jobject clazz, jmethodID methodID, jlong varargs);

  TYPE_LIST2(CALL_METHOD)
  #undef CALL_METHOD

  // NewObject varargs
  jobject (*NewObjectVarargs)(JNIEnv *env, jclass clazz, jmethodID methodID, jlong varargs);

  // RegisterNative (single method)
  jint (*RegisterNative)(JNIEnv *env, jclass clazz, const char* name, const char* signature, void* closure);
};

#define CALL_METHOD_BRIDGE(returnType, Type) \
returnType Call##Type##MethodV(JNIEnv *env, jobject obj, jmethodID methodID, va_list args) { \
  struct VarargsV varargs = { .base = { .functions = &valist_functions } }; \
  va_copy(varargs.args, args); \
  struct NespressoEnv *nespresso_env = (struct NespressoEnv*) env->functions->reserved0; \
  VAR(returnType)(result) nespresso_env->Call##Type##MethodVarargs(env, obj, methodID, (jlong) &varargs); \
  va_end(varargs.args); \
  RETURN(returnType)(result); \
} \
returnType Call##Type##MethodA(JNIEnv *env, jobject obj, jmethodID methodID, const jvalue *args) { \
  struct VarargsA varargs = {&jvalues_functions, args}; \
  NespressoEnv *nespresso_env = (NespressoEnv*) (*env)->functions->reserved0; \
  return nespresso_env->Call##Type##MethodVarargs(env, obj, methodID, (jlong) &varargs); \
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
  VarargsV varargs = { .functions = &valist_functions }; \
  va_copy(varargs.args, args); \
  NespressoEnv *nespresso_env = (NespressoEnv*) (*env)->functions->reserved0; \
  VAR(returnType)(result) nespresso_env->CallStatic##Type##MethodVarargs(env, clazz, methodID, (jlong) &varargs); \
  va_end(varargs.args); \
  RETURN(returnType)(result); \
} \
returnType CallStatic##Type##MethodA(JNIEnv *env, jclass clazz, jmethodID methodID, const jvalue *args) { \
  VarargsA varargs = {&jvalues_functions, args}; \
  NespressoEnv *nespresso_env = (NespressoEnv*) (*env)->functions->reserved0; \
  return nespresso_env->CallStatic##Type##MethodVarargs(env, clazz, methodID, (jlong) &varargs); \
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
  VarargsV varargs = { .functions = &valist_functions }; \
  va_copy(varargs.args, args); \
  NespressoEnv *nespresso_env = (NespressoEnv*) (*env)->functions->reserved0; \
  return nespresso_env->CallNonvirtual##Type##MethodVarargs(env, obj, clazz, methodID, (jlong) &varargs); \
} \
returnType CallNonvirtual##Type##MethodA(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, const jvalue *args) { \
  VarargsA varargs = {&jvalues_functions, args}; \
  NespressoEnv *nespresso_env = (NespressoEnv*) (*env)->functions->reserved0; \
  VAR(returnType)(result) nespresso_env->CallNonvirtual##Type##MethodVarargs(env, obj, clazz, methodID, (jlong) &varargs); \
  va_end(varargs.args); \
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

// Spawn a "guest" direct byte buffer.
// This ByteBuffer is set to BIG_ENDIAN by default.
jobject NewDirectByteBuffer(JNIEnv* env, void* address, jlong capacity) {
  jclass java_nio_DirectByteBuffer = (*env)->FindClass("java.nio.DirectByteBuffer");
  // TODO(peterssen): Cache class and constructor.
  jmethodID constructor = (*env)->GetMethodID(java_nio_DirectByteBuffer, "<init>", "(JI)V");
  // TODO(peterssen): Check narrowing conversion.
  return (*env)->NewObject(java_nio_DirectByteBuffer, constructor, (jlong) address, (jint) capacity);
}

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
  V(NewDirectByteBuffer) \
  V(RegisterNatives) \
  V(NewGlobalRef) \
  V(DeleteGlobalRef)


extern "C" {

// Global state.
static TruffleContext *truffle_ctx;

jobject NewObjectV(JNIEnv *env, jclass clazz, jmethodID methodID, va_list args) {
  VarargsV varargs = { .functions = &valist_functions };
  NespressoEnv *nespresso_env = (NespressoEnv*) (*env)->functions->reserved0;
  return nespresso_env->NewObjectVarargs(env, clazz, methodID, (jlong) &varargs);
}

jobject NewObjectA(JNIEnv *env, jclass clazz, jmethodID methodID, const jvalue *args) {
  VarargsJValues varargs(args);
  NespressoEnv *nespresso_env = (NespressoEnv*) (*env)->functions->reserved0;
  return nespresso_env->NewObjectVarargs(env, clazz, methodID, (jlong) &varargs);
}

jobject NewObject(JNIEnv *env, jclass clazz, jmethodID methodID, ...) {
  va_list args;
  va_start(args, methodID);
  jobject result = (*env)->NewObjectV(clazz, methodID, args);
  va_end(args);
  return result;
}

jint RegisterNatives(JNIEnv *env, jclass clazz, const JNINativeMethod *methods, jint nMethods) {  
  NespressoEnv *nespresso_env = (NespressoEnv*) (*env)->functions->reserved0;
  
  for (jint i = 0; i < nMethods; ++i) {
    fprintf(stderr, "RegisterNative %s %s %p\n", methods[i].name, methods[i].signature, methods[i].fnPtr);
    nespresso_env->RegisterNative(env, clazz, methods[i].name, methods[i].signature, methods[i].fnPtr);
  }
  // TODO(peterssen): Always OK?.
  return JNI_OK;
}

jobject NewGlobalRef(JNIEnv *env, jobject obj) {
  TruffleEnv *truffle_env = truffle_ctx->getTruffleEnv();
  return (jobject) truffle_env->newObjectRef((TruffleObject) obj);
}

void DeleteGlobalRef(JNIEnv *env, jobject globalRef) {
  TruffleEnv *truffle_env = truffle_ctx->getTruffleEnv();
  truffle_env->releaseObjectRef((TruffleObject) globalRef);
}

jlong initializeNativeContext(TruffleEnv* truffle_env, void* (*fetch_by_name)(const char *)) {
  JNIEnv* env = new JNIEnv();
  JNINativeInterface_ *functions = new JNINativeInterface_();
  NespressoEnv *nespresso_env = new NespressoEnv();
  functions->reserved0 = nespresso_env;

  // Global state.
  truffle_ctx = truffle_env->getTruffleContext();

  // Fetch Java ... varargs methods.
  #define INIT_VARARGS_METHOD__(name) \
    nespresso_env->name = truffle_env->dupClosureRef((typeof(nespresso_env->name)) fetch_by_name(#name));

    VARARGS_METHOD_LIST(INIT_VARARGS_METHOD__)
  #undef INIT_VARARGS_METHOD__

  #define INIT__(name) \
    functions->name = truffle_env->dupClosureRef((typeof(functions->name)) fetch_by_name(#name));
  JNI_FUNCTION_LIST(INIT__)
  #undef INIT__

  // Put ... bridges in the env.
  #define INIT_NATIVE_METHOD__(name) \
    functions->name = &name;

    BRIDGE_METHOD_LIST(INIT_NATIVE_METHOD__)
  #undef INIT_NATIVE_METHOD__

  (*env)->functions = functions;

  return (jlong) env;
}

void disposeNativeContext(TruffleEnv* truffle_env, jlong envPtr) {
  JNIEnv *env = (JNIEnv*) envPtr;
  NespressoEnv *nespresso_env = new NespressoEnv();

  #define DISPOSE__(name) \
     truffle_env->releaseClosureRef(env->functions->name);

  JNI_FUNCTION_LIST(DISPOSE__)
  #undef DISPOSE__

  #define DISPOSE_VARARGS_METHOD__(name) \
    truffle_env->releaseClosureRef(nespresso_env->name);

  VARARGS_METHOD_LIST(DISPOSE_VARARGS_METHOD__)
  #undef DISPOSE_VARARG_METHOD__

  delete (NespressoEnv*) (*env)->functions->reserved0;
  delete (*env)->functions;
  delete env;

  truffle_ctx = NULL;
}

void* dupClosureRef(TruffleEnv *truffle_env, void* closure) {
    return truffle_env->dupClosureRef(closure);
}

} // extern "C"
