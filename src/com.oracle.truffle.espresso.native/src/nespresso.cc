#include <jni.h>
#include <ffi.h>
#include <trufflenfi.h>
#include <cstdlib>
#include <cstring>

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
V(NewObject) \
V(NewObjectV) \
V(NewObjectA) \
V(GetObjectClass) \
V(IsInstanceOf) \
V(GetMethodID) \
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
V(CallNonvirtualVoidMethodA) \
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


class VarArgs {
public:
  virtual jboolean popBoolean() = 0;
  virtual jbyte popByte()       = 0;
  virtual jchar popChar()       = 0;
  virtual jshort popShort()     = 0;
  virtual jint popInt()         = 0;
  virtual jfloat popFloat()     = 0;
  virtual jdouble popDouble()   = 0;
  virtual jlong popLong()       = 0;
  virtual jobject popObject()   = 0;
  virtual ~VarArgs() { /* nop */ }
};

class VarArgsVaList : public VarArgs {
private:
  va_list _args;
public:
  VarArgsVaList(va_list args) {
    va_copy(_args, args);
  }
  ~VarArgsVaList() {
    va_end(_args);
  }
  // All sub-int values are promoted to int.
  inline jboolean popBoolean() {
    return (va_arg(_args, jint) == 0 ? JNI_FALSE : JNI_TRUE);
  }
  inline jbyte popByte() {
    return (jbyte) va_arg(_args, jint);
  }
  inline jchar popChar() {
    return (jchar) va_arg(_args, jint);
  }
  inline jshort popShort() {
    return (jshort) va_arg(_args, jint);
  }
  inline jint popInt() {
    return (jint) va_arg(_args, jint);
  }
  inline jfloat popFloat() {
    // float is promoted to double.
    return (jfloat) va_arg(_args, jdouble);
  }
  inline jdouble popDouble() {
    return (jdouble) va_arg(_args, jdouble);
  }
  inline jlong popLong() {
    return (jlong) va_arg(_args, jlong);
  }
  inline jobject popObject() {
    return (jobject) va_arg(_args, jobject);
  }
};

class VarArgsJValues : public VarArgs {
private:
  jvalue *_args;
public:
  VarArgsJValues(const jvalue *args) : _args((jvalue*) args) {}
  inline jboolean popBoolean() {
    return _args++->z;
  }
  inline jbyte popByte() {
    return _args++->b;
  }
  inline jchar popChar() {
    return _args++->c;
  }
  inline jshort popShort() {
    return _args++->s;
  } 
  inline jint popInt() {
    return _args++->i;
  }
  inline jfloat popFloat() {
    return _args++->f;
  }
  inline jdouble popDouble() {
    return _args++->d;
  }
  inline jlong popLong() {
    return _args++->j;
  }
  inline jobject popObject() {
    return _args++->l;
  }
};

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


struct NespressoEnv {
  // TODO(peterssen): Add C++ env-less methods.
  #define CALL_METHOD(returnType, Type) \
    returnType (*Call##Type##Method)(JNIEnv *env, jobject obj, jmethodID methodID, jlong varargs); \
    returnType (*CallStatic##Type##Method)(JNIEnv *env, jobject clazz, jmethodID methodID, jlong varargs); \
    returnType (*CallNonvirtual##Type##Method)(JNIEnv *env, jobject obj, jobject clazz, jmethodID methodID, jlong varargs);

  TYPE_LIST2(CALL_METHOD)
  #undef CALL_METHOD

  // NewObject varargs
  jobject (*NewObject)(JNIEnv *env, jclass clazz, jmethodID methodID, jlong varargs);

};

#define CALL_METHOD_BRIDGE(returnType, Type) \
returnType Call##Type##MethodV(JNIEnv *env, jobject obj, jmethodID methodID, va_list args) { \
  VarArgsVaList varargs(args); \
  NespressoEnv *nespresso_env = (NespressoEnv*) env->functions->reserved0; \
  return nespresso_env->Call##Type##Method(env, obj, methodID, (jlong) &varargs); \
} \
returnType Call##Type##MethodA(JNIEnv *env, jobject obj, jmethodID methodID, const jvalue *args) { \
  VarArgsJValues varargs(args); \
  NespressoEnv *nespresso_env = (NespressoEnv*) env->functions->reserved0; \
  return nespresso_env->Call##Type##Method(env, obj, methodID, (jlong) &varargs); \
}

#define CALL_STATIC_METHOD_BRIDGE(returnType, Type) \
returnType CallStatic##Type##MethodV(JNIEnv *env, jclass clazz, jmethodID methodID, va_list args) { \
  VarArgsVaList varargs(args); \
  NespressoEnv *nespresso_env = (NespressoEnv*) env->functions->reserved0; \
  return nespresso_env->CallStatic##Type##Method(env, clazz, methodID, (jlong) &varargs); \
} \
returnType CallStatic##Type##MethodA(JNIEnv *env, jclass clazz, jmethodID methodID, const jvalue *args) { \
  VarArgsJValues varargs(args); \
  NespressoEnv *nespresso_env = (NespressoEnv*) env->functions->reserved0; \
  return nespresso_env->CallStatic##Type##Method(env, clazz, methodID, (jlong) &varargs); \
}

#define CALL_NON_VIRTUAL_METHOD_BRIDGE(returnType, Type) \
returnType CallNonvirtual##Type##MethodV(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, va_list args) { \
  VarArgsVaList varargs(args); \
  NespressoEnv *nespresso_env = (NespressoEnv*) env->functions->reserved0; \
  return nespresso_env->CallNonvirtual##Type##Method(env, obj, clazz, methodID, (jlong) &varargs); \
} \
returnType CallNonvirtual##Type##MethodA(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, const jvalue *args) { \
  VarArgsJValues varargs(args); \
  NespressoEnv *nespresso_env = (NespressoEnv*) env->functions->reserved0; \
  return nespresso_env->CallNonvirtual##Type##Method(env, obj, clazz, methodID, (jlong) &varargs); \
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

#define VARARGS_METHOD_LIST(V) \
  EXPAND(TYPE_LIST2(V MAKE_METHOD)) \
  EXPAND(TYPE_LIST2(V MAKE_STATIC_METHOD)) \
  EXPAND(TYPE_LIST2(V MAKE_NON_VIRTUAL_METHOD)) \
  V(NewObject)

// Spawn a "guest" direct byte buffer.
jobject NewDirectByteBuffer(JNIEnv* env, void* address, jlong capacity) {
  jclass java_nio_DirectByteBuffer = env->FindClass("java.nio.DirectByteBuffer");
  // TODO(peterssen): Cache class and constructor.
  jmethodID constructor = env->GetMethodID(java_nio_DirectByteBuffer, "<init>", "(JI)V");
  // TODO(peterssen): Check narrowing conversion.
  return env->NewObject(java_nio_DirectByteBuffer, constructor, (jlong) address, (jint) capacity);
}  

#define BRIDGE_METHOD_LIST(V) \
  EXPAND(TYPE_LIST2(V MAKE_METHOD_A)) \
  EXPAND(TYPE_LIST2(V MAKE_METHOD_V)) \
  EXPAND(TYPE_LIST2(V MAKE_STATIC_METHOD_A)) \
  EXPAND(TYPE_LIST2(V MAKE_STATIC_METHOD_V)) \
  EXPAND(TYPE_LIST2(V MAKE_NON_VIRTUAL_METHOD_A)) \
  EXPAND(TYPE_LIST2(V MAKE_NON_VIRTUAL_METHOD_V)) \
  V(NewObjectA) \
  V(NewObjectV) \
  V(NewDirectByteBuffer)


extern "C" {

jobject NewObjectV(JNIEnv *env, jclass clazz, jmethodID methodID, va_list args) { 
  VarArgsVaList varargs(args);
  NespressoEnv *nespresso_env = (NespressoEnv*) env->functions->reserved0;
  return nespresso_env->NewObject(env, clazz, methodID, (jlong) &varargs);
}

jobject NewObjectA(JNIEnv *env, jclass clazz, jmethodID methodID, const jvalue *args) {
  VarArgsJValues varargs(args);
  NespressoEnv *nespresso_env = (NespressoEnv*) env->functions->reserved0;
  return nespresso_env->NewObject(env, clazz, methodID, (jlong) &varargs);
}

JNIEnv* initializeNativeContext(TruffleEnv* truffle_env, void* (*fetch_by_name)(const char *)) {
  JNIEnv* env = new JNIEnv();
  JNINativeInterface_ *functions = new JNINativeInterface_();
  NespressoEnv *nespresso_env = new NespressoEnv();
  functions->reserved0 = nespresso_env;

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

  env->functions = functions;

  return env;
}

void disposeNativeContext(TruffleEnv* truffle_env, jlong envPtr) {
  // FIXME(peterssen): This method leaks, a lot, please fix.

  JNIEnv *env = (JNIEnv*) envPtr;
  NespressoEnv *nespresso_env = (NespressoEnv *) env->functions->reserved0;

/*   #define DISPOSE__(name) \
     truffle_env->releaseClosureRef(env->functions->name);

  JNI_FUNCTION_LIST(DISPOSE__)
  #undef DISPOSE__ */

  #define DISPOSE_VARARGS_METHOD__(name) \
    truffle_env->releaseClosureRef(nespresso_env->name);

  VARARGS_METHOD_LIST(DISPOSE_VARARGS_METHOD__)
  #undef DISPOSE_VARARG_METHOD__

  delete nespresso_env;
  delete env->functions;
  delete env;
}

void* dupClosureRef(TruffleEnv *truffle_env, void* closure) {
    return truffle_env->dupClosureRef(closure);
}

jboolean popBoolean(jlong ptr) {
    VarArgs *varargs = (VarArgs*) ptr;
    return varargs->popBoolean();
}

jbyte popByte(jlong ptr) {
    VarArgs *varargs = (VarArgs*) ptr;
    return varargs->popByte();
}

jchar popChar(jlong ptr) {
    VarArgs *varargs = (VarArgs*) ptr;
    return varargs->popChar();
}

jshort popShort(jlong ptr) {
    VarArgs *varargs = (VarArgs*) ptr;
    return varargs->popShort();
}

jint popInt(jlong ptr) {
    VarArgs *varargs = (VarArgs*) ptr;
    return varargs->popInt();
}

jfloat popFloat(jlong ptr) {
    VarArgs *varargs = (VarArgs*) ptr;
    return varargs->popFloat();
}

jdouble popDouble(jlong ptr) {
    VarArgs *varargs = (VarArgs*) ptr;
    return varargs->popDouble();
}

jlong popLong(jlong ptr) {
    VarArgs *varargs = (VarArgs*) ptr;
    return varargs->popLong();
}

jobject popObject(jlong ptr) {
    VarArgs *varargs = (VarArgs*) ptr;
    return varargs->popObject();
}

} // extern "C"
