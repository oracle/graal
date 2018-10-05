#include <jni.h>
#include <trufflenfi.h>

extern "C" {

#define EXPAND(...) __VA_ARGS__
#define MAP(LIST, FN, V) EXPAND(LIST(V FN))
#define CONCAT(LEFT, RIGHT, V) LEFT(V) RIGHT(V)

#define PRIMITIVE_LIST(V) \
  V(Object)  \
  V(Boolean) \
  V(Byte)    \
  V(Char)    \
  V(Short)   \
  V(Int)     \
  V(Long)    \
  V(Float)   \
  V(Double)

    



#define MAKE_GETTER(type) (Get##type##Field)
#define MAKE_SETTER(type) (Set##type##Field)

#define MAKE_STATIC_METHOD(type) (CallStatic##type##MethodV)

#define FIELD_GETTER_LIST(V) MAP(TYPE_LIST, MAKE_GETTER, V)
#define FIELD_SETTER_LIST(V) MAP(TYPE_LIST, MAKE_SETTER, V)


#define CALL_STATIC_METHOD_LIST(V) MAP(TYPE_LIST, MAKE_STATIC_METHOD, V)


#define FIELDS(V) CONCAT(FIELD_GETTER_LIST, FIELD_SETTER_LIST, V)

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
\
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
\
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
\
V(GetFieldID) \
\
V(GetObjectField) \
V(GetBooleanField) \
V(GetByteField) \
V(GetCharField) \
V(GetShortField) \
V(GetIntField) \
V(GetLongField) \
V(GetFloatField) \
V(GetDoubleField) \
\
V(SetObjectField) \
V(SetBooleanField) \
V(SetByteField) \
V(SetCharField) \
V(SetShortField) \
V(SetIntField) \
V(SetLongField) \
V(SetFloatField) \
V(SetDoubleField) \
\
V(GetStaticMethodID) \
\
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
\
V(GetStaticFieldID) \
\
V(GetStaticObjectField) \
V(GetStaticBooleanField) \
V(GetStaticByteField) \
V(GetStaticCharField) \
V(GetStaticShortField) \
V(GetStaticIntField) \
V(GetStaticLongField) \
V(GetStaticFloatField) \
V(GetStaticDoubleField) \
\
V(SetStaticObjectField) \
V(SetStaticBooleanField) \
V(SetStaticByteField) \
V(SetStaticCharField) \
V(SetStaticShortField) \
V(SetStaticIntField) \
V(SetStaticLongField) \
V(SetStaticFloatField) \
V(SetStaticDoubleField) \
\
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
\
V(NewBooleanArray) \
V(NewByteArray) \
V(NewCharArray) \
V(NewShortArray) \
V(NewIntArray) \
V(NewLongArray) \
V(NewFloatArray) \
V(NewDoubleArray) \
\
V(GetBooleanArrayElements) \
V(GetByteArrayElements) \
V(GetCharArrayElements) \
V(GetShortArrayElements) \
V(GetIntArrayElements) \
V(GetLongArrayElements) \
V(GetFloatArrayElements) \
V(GetDoubleArrayElements) \
\
V(ReleaseBooleanArrayElements) \
V(ReleaseByteArrayElements) \
V(ReleaseCharArrayElements) \
V(ReleaseShortArrayElements) \
V(ReleaseIntArrayElements) \
V(ReleaseLongArrayElements) \
V(ReleaseFloatArrayElements) \
V(ReleaseDoubleArrayElements) \
\
V(GetBooleanArrayRegion) \
V(GetByteArrayRegion) \
V(GetCharArrayRegion) \
V(GetShortArrayRegion) \
V(GetIntArrayRegion) \
V(GetLongArrayRegion) \
V(GetFloatArrayRegion) \
V(GetDoubleArrayRegion) \
\
V(SetBooleanArrayRegion) \
V(SetByteArrayRegion) \
V(SetCharArrayRegion) \
V(SetShortArrayRegion) \
V(SetIntArrayRegion) \
V(SetLongArrayRegion) \
V(SetFloatArrayRegion) \
V(SetDoubleArrayRegion) \
\
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

JNIEnv* createJniEnv(TruffleEnv* truffle_env, void* (*fetch_by_name)(const char *)) {
  JNINativeInterface_* functions = new JNINativeInterface_();
  JNIEnv* env = new JNIEnv();
  env->functions = functions;

  #define INIT__(name) \
    functions->name = (typeof(functions->name)) fetch_by_name(#name);

  JNI_FUNCTION_LIST(INIT__)
  #undef INIT__

  return env;
}

void disposeJniEnv(TruffleEnv* truffle_env, JNIEnv* env) {
  #define DISPOSE__(name) \
     truffle_env->releaseClosureRef(env->functions->name);

  JNI_FUNCTION_LIST(DISPOSE__)
  #undef DISPOSE__

  delete env->functions;
  delete env;
}

void* dupClosureRef(TruffleEnv *truffle_env, void* closure) {
    return truffle_env->dupClosureRef(closure);
}

} // extern
