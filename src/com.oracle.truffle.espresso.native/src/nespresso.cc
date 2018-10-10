#include <jni.h>
#include <ffi.h>
#include <trufflenfi.h>
#include <cstdlib>

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

#define TYPE_LIST(V) \
  V(Object)          \
  PRIMITIVE_LIST(V)

#define MAKE_GETTER(type) (Get##type##Field)
#define MAKE_SETTER(type) (Set##type##Field)

#define MAKE_STATIC_METHOD(type) (CallStatic##type##MethodV)

#define FIELD_GETTER_LIST(V) MAP(TYPE_LIST, MAKE_GETTER, V)
#define FIELD_SETTER_LIST(V) MAP(TYPE_LIST, MAKE_SETTER, V)

#define CALL_STATIC_METHOD_LIST(V) MAP(TYPE_LIST, MAKE_STATIC_METHOD, V)


#define FIELDS(V) CONCAT(FIELD_GETTER_LIST, FIELD_SETTER_LIST, V)

#define JNI_FUNCTION_LIST(V) \
  V(GetVersion) \
  V(GetObjectClass) \
  V(GetMethodID)
  
/*
// V(GetVersion) \
// V(DefineClass) \
// V(FindClass) \
// V(FromReflectedMethod) \
// V(FromReflectedField) \
// V(ToReflectedMethod) \
// V(GetSuperclass) \
// V(IsAssignableFrom) \
// V(ToReflectedField) \
// V(Throw) \
// V(ThrowNew) \
// V(ExceptionOccurred) \
// V(ExceptionDescribe) \
// V(ExceptionClear) \
// V(FatalError) \
// V(PushLocalFrame) \
// V(PopLocalFrame) \
// V(NewGlobalRef) \
// V(DeleteGlobalRef) \
// V(DeleteLocalRef) \
// V(IsSameObject) \
// V(NewLocalRef) \
// V(EnsureLocalCapacity) \
// V(AllocObject) \
// V(NewObject) \
// V(NewObjectV) \
// V(NewObjectA) \
// V(GetObjectClass) \
// V(IsInstanceOf) \
// V(GetMethodID) \
// \
// V(CallObjectMethod) \
// V(CallObjectMethodV) \
// V(CallObjectMethodA) \
// V(CallBooleanMethod) \
// V(CallBooleanMethodV) \
// V(CallBooleanMethodA) \
// V(CallByteMethod) \
// V(CallByteMethodV) \
// V(CallByteMethodA) \
// V(CallCharMethod) \
// V(CallCharMethodV) \
// V(CallCharMethodA) \
// V(CallShortMethod) \
// V(CallShortMethodV) \
// V(CallShortMethodA) \
// V(CallIntMethod) \
// V(CallIntMethodV) \
// V(CallIntMethodA) \
// V(CallLongMethod) \
// V(CallLongMethodV) \
// V(CallLongMethodA) \
// V(CallFloatMethod) \
// V(CallFloatMethodV) \
// V(CallFloatMethodA) \
// V(CallDoubleMethod) \
// V(CallDoubleMethodV) \
// V(CallDoubleMethodA) \
// V(CallVoidMethod) \
// V(CallVoidMethodV) \
// V(CallVoidMethodA) \
// \
// V(CallNonvirtualObjectMethod) \
// V(CallNonvirtualObjectMethodV) \
// V(CallNonvirtualObjectMethodA) \
// V(CallNonvirtualBooleanMethod) \
// V(CallNonvirtualBooleanMethodV) \
// V(CallNonvirtualBooleanMethodA) \
// V(CallNonvirtualByteMethod) \
// V(CallNonvirtualByteMethodV) \
// V(CallNonvirtualByteMethodA) \
// V(CallNonvirtualCharMethod) \
// V(CallNonvirtualCharMethodV) \
// V(CallNonvirtualCharMethodA) \
// V(CallNonvirtualShortMethod) \
// V(CallNonvirtualShortMethodV) \
// V(CallNonvirtualShortMethodA) \
// V(CallNonvirtualIntMethod) \
// V(CallNonvirtualIntMethodV) \
// V(CallNonvirtualIntMethodA) \
// V(CallNonvirtualLongMethod) \
// V(CallNonvirtualLongMethodV) \
// V(CallNonvirtualLongMethodA) \
// V(CallNonvirtualFloatMethod) \
// V(CallNonvirtualFloatMethodV) \
// V(CallNonvirtualFloatMethodA) \
// V(CallNonvirtualDoubleMethod) \
// V(CallNonvirtualDoubleMethodV) \
// V(CallNonvirtualDoubleMethodA) \
// V(CallNonvirtualVoidMethod) \
// V(CallNonvirtualVoidMethodV) \
// V(CallNonvirtualVoidMethodA) \
// \
// V(GetFieldID) \
// \
// V(GetObjectField) \
// V(GetBooleanField) \
// V(GetByteField) \
// V(GetCharField) \
// V(GetShortField) \
// V(GetIntField) \
// V(GetLongField) \
// V(GetFloatField) \
// V(GetDoubleField) \
// \
// V(SetObjectField) \
// V(SetBooleanField) \
// V(SetByteField) \
// V(SetCharField) \
// V(SetShortField) \
// V(SetIntField) \
// V(SetLongField) \
// V(SetFloatField) \
// V(SetDoubleField) \
// \
// V(GetStaticMethodID) \
// \
// V(CallStaticObjectMethod) \
// V(CallStaticObjectMethodV) \
// V(CallStaticObjectMethodA) \
// V(CallStaticBooleanMethod) \
// V(CallStaticBooleanMethodV) \
// V(CallStaticBooleanMethodA) \
// V(CallStaticByteMethod) \
// V(CallStaticByteMethodV) \
// V(CallStaticByteMethodA) \
// V(CallStaticCharMethod) \
// V(CallStaticCharMethodV) \
// V(CallStaticCharMethodA) \
// V(CallStaticShortMethod) \
// V(CallStaticShortMethodV) \
// V(CallStaticShortMethodA) \
// V(CallStaticIntMethod) \
// V(CallStaticIntMethodV) \
// V(CallStaticIntMethodA) \
// V(CallStaticLongMethod) \
// V(CallStaticLongMethodV) \
// V(CallStaticLongMethodA) \
// V(CallStaticFloatMethod) \
// V(CallStaticFloatMethodV) \
// V(CallStaticFloatMethodA) \
// V(CallStaticDoubleMethod) \
// V(CallStaticDoubleMethodV) \
// V(CallStaticDoubleMethodA) \
// V(CallStaticVoidMethod) \
// V(CallStaticVoidMethodV) \
// V(CallStaticVoidMethodA) \
// \
// V(GetStaticFieldID) \
// \
// V(GetStaticObjectField) \
// V(GetStaticBooleanField) \
// V(GetStaticByteField) \
// V(GetStaticCharField) \
// V(GetStaticShortField) \
// V(GetStaticIntField) \
// V(GetStaticLongField) \
// V(GetStaticFloatField) \
// V(GetStaticDoubleField) \
// \
// V(SetStaticObjectField) \
// V(SetStaticBooleanField) \
// V(SetStaticByteField) \
// V(SetStaticCharField) \
// V(SetStaticShortField) \
// V(SetStaticIntField) \
// V(SetStaticLongField) \
// V(SetStaticFloatField) \
// V(SetStaticDoubleField) \
// \
// V(NewString) \
// V(GetStringLength) \
// V(GetStringChars) \
// V(ReleaseStringChars) \
// V(NewStringUTF) \
// V(GetStringUTFLength) \
// V(GetStringUTFChars) \
// V(ReleaseStringUTFChars) \
// V(GetArrayLength) \
// V(NewObjectArray) \
// V(GetObjectArrayElement) \
// V(SetObjectArrayElement) \
// \
// V(NewBooleanArray) \
// V(NewByteArray) \
// V(NewCharArray) \
// V(NewShortArray) \
// V(NewIntArray) \
// V(NewLongArray) \
// V(NewFloatArray) \
// V(NewDoubleArray) \
// \
// V(GetBooleanArrayElements) \
// V(GetByteArrayElements) \
// V(GetCharArrayElements) \
// V(GetShortArrayElements) \
// V(GetIntArrayElements) \
// V(GetLongArrayElements) \
// V(GetFloatArrayElements) \
// V(GetDoubleArrayElements) \
// \
// V(ReleaseBooleanArrayElements) \
// V(ReleaseByteArrayElements) \
// V(ReleaseCharArrayElements) \
// V(ReleaseShortArrayElements) \
// V(ReleaseIntArrayElements) \
// V(ReleaseLongArrayElements) \
// V(ReleaseFloatArrayElements) \
// V(ReleaseDoubleArrayElements) \
// \
// V(GetBooleanArrayRegion) \
// V(GetByteArrayRegion) \
// V(GetCharArrayRegion) \
// V(GetShortArrayRegion) \
// V(GetIntArrayRegion) \
// V(GetLongArrayRegion) \
// V(GetFloatArrayRegion) \
// V(GetDoubleArrayRegion) \
// \
// V(SetBooleanArrayRegion) \
// V(SetByteArrayRegion) \
// V(SetCharArrayRegion) \
// V(SetShortArrayRegion) \
// V(SetIntArrayRegion) \
// V(SetLongArrayRegion) \
// V(SetFloatArrayRegion) \
// V(SetDoubleArrayRegion) \
// \
// V(RegisterNatives) \
// V(UnregisterNatives) \
// V(MonitorEnter) \
// V(MonitorExit) \
// V(GetJavaVM) \
// V(GetStringRegion) \
// V(GetStringUTFRegion) \
// V(GetPrimitiveArrayCritical) \
// V(ReleasePrimitiveArrayCritical) \
// V(GetStringCritical) \
// V(ReleaseStringCritical) \
// V(NewWeakGlobalRef) \
// V(DeleteWeakGlobalRef) \
// V(ExceptionCheck) \
// V(NewDirectByteBuffer) \
// V(GetDirectBufferAddress) \
// V(GetDirectBufferCapacity) \
// V(GetObjectRefType)
  */

static jobject (JNICALL *CallObjectMethod__)(JNIEnv *env, jobject obj, jmethodID methodID, jlong varargs);
static void (JNICALL *CallVoidMethod__)(JNIEnv *env, jobject obj, jmethodID methodID, jlong varargs);

class VarArgs {
  public:
    virtual jboolean popBoolean() = 0;        
    virtual jbyte popByte() = 0;
    virtual jchar popChar() = 0;
    virtual jshort popShort() = 0;
    virtual jint popInt() = 0;    
    virtual jfloat popFloat() = 0;
    virtual jdouble popDouble() = 0;
    virtual jlong popLong() = 0;
    virtual jobject popObject() = 0;
};

class VarArgsVaList : public VarArgs {
private:  
  va_list args;
public:
  VarArgsVaList(va_list in_args) {
    va_copy(args, in_args);
  }    
  jboolean popBoolean() {
    return (va_arg(args, jint) == 0 ? JNI_FALSE : JNI_TRUE);
  }  
  jbyte popByte() {
    return (jbyte) va_arg(args, jint);
  }
  jchar popChar() {
    return (jchar) va_arg(args, jint);
  }
  jshort popShort() {
    return (jshort) va_arg(args, jint);
  }
  jint popInt() {
    return (jbyte) va_arg(args, jint);
  }
  jfloat popFloat() {
    // float is promoted to double
    return (jfloat) va_arg(args, jdouble);
  }
  jdouble popDouble() {
    return (jdouble) va_arg(args, jdouble);
  }
  jlong popLong() {
    return (jlong) va_arg(args, jlong);
  }
  jobject popObject() {
    return (jobject) va_arg(args, jobject);
  }
};

class VarArgsJValues : public VarArgs {
private:  
  jvalue* args;
public:
  VarArgsJValues(const jvalue *args) : args((jvalue*) args) {}
  jboolean popBoolean() {
    return args++->z;
  }
  jbyte popByte() {
    return args++->b;
  }
  jchar popChar() {
    return args++->c;
  }
  jshort popShort() {
    return args++->s;
  }
  jint popInt() {
    return args++->i;
  }
  jfloat popFloat() {    
    return args++->f;
  }
  jdouble popDouble() {
    return args++->d;
  }
  jlong popLong() {
    return args++->j;
  }
  jobject popObject() {
    return args++->l;
  }
};

jobject JNICALL CallObjectMethodV(JNIEnv *env, jobject obj, jmethodID methodID, va_list args) {
  printf("native CallObjectMethodV");
  VarArgsVaList varargs(args);    
  //VarArgsDefault varargs;
  return CallObjectMethod__(env, obj, methodID, (jlong) &varargs);
}

jobject JNICALL CallObjectMethodA(JNIEnv *env, jobject obj, jmethodID methodID, const jvalue * args) {
  printf("native CallObjectMethodA");
  VarArgsJValues varargs(args);
  // VarArgsDefault varargs;
  return CallObjectMethod__(env, obj, methodID, (jlong) &varargs);
}

void JNICALL CallVoidMethodV(JNIEnv *env, jobject obj, jmethodID methodID, va_list args) {
  printf("native CallVoidMethodV\n");
  VarArgsVaList varargs(args);
  CallVoidMethod__(env, obj, methodID, (jlong) &varargs);
  printf("Done\n");
}

void JNICALL CallVoidMethodA(JNIEnv *env, jobject obj, jmethodID methodID, const jvalue * args) {
  printf("native CallVoidMethodA\n");
  VarArgsJValues varargs(args);
  return CallVoidMethod__(env, obj, methodID, (jlong) &varargs);
}

// jobject CallObjectMethodV(JNIEnv *guest_env, jobject obj, jmethodID methodID, va_list args) {
//   NespressoEnv* nenv = (NespressoEnv*) guest_env->reserved0;
//   JNIEnv* host_env = (JNIEnv*) guest_env->reserved1;

//   jint arg_count = 16;
//   jobject varargs = env->CallStaticVoidMethod(nenv->VarArgs, nenv->VarArgs_create, (jint) arg_count);
//   for (int i = 0; i < arg_count; ++i) {
//     switch (arg_type[i]) {
//       case BOOLEAN: host_env->CallVoidMethod(nenv->VarArgs, varargs, nenv->VarArgs_pushByte,   (jboolean) ((va_arg(args, jint) == 0) ? JNI_FALSE : JNI_TRUE)); break;
//       case BYTE   : host_env->CallVoidMethod(nenv->VarArgs, varargs, nenv->VarArgs_pushByte,   (jbyte) va_arg(args, jint));      break;
//       case CHAR   : host_env->CallVoidMethod(nenv->VarArgs, varargs, nenv->VarArgs_pushChar,   (jchar) va_arg(args, jint));      break;
//       case SHORT  : host_env->CallVoidMethod(nenv->VarArgs, varargs, nenv->VarArgs_pushShort,  (jshort) va_arg(args, jint));     break;
//       case INT    : host_env->CallVoidMethod(nenv->VarArgs, varargs, nenv->VarArgs_pushInt,    (jint) va_arg(args, jint));       break;
//       case FLOAT  : host_env->CallVoidMethod(nenv->VarArgs, varargs, nenv->VarArgs_pushFloat,  (jfloat) va_arg(args, jdouble));  break;
//       case DOUBLE : host_env->CallVoidMethod(nenv->VarArgs, varargs, nenv->VarArgs_pushDouble, (jdouble) va_arg(args, jdouble)); break;
//       case LONG   : host_env->CallVoidMethod(nenv->VarArgs, varargs, nenv->VarArgs_pushLong,   (jlong) va_arg(args, jlong));     break;
//       case OBJECT : host_env->CallVoidMethod(nenv->VarArgs, varargs, nenv->VarArgs_pushObject, (jobject) va_arg(args, jobject)); break;
//     }
//   }
//   jobjectArray tail = host_env->CallObjectMethod(ret->VarArgs, ret->VarArgs_getVarArgs);
//   // Call host Object... args method
//   return nenv->CallObjectMethod__(obj, methodID, tail);
// }

// jobject CallObjectMethodA(JNIEnv *guest_env, jobject obj, jmethodID methodID, const jvalue * args) {
//   NespressoEnv* nenv = (NespressoEnv*) guest_env->reserved0;
//   JNIEnv* host_env = (JNIEnv*) guest_env->reserved1;

//   jint arg_count = 16;
//   jobject varargs = env->CallStaticVoidMethod(nenv->VarArgs, nenv->VarArgs_create, (jint) arg_count);
//   for (int i = 0; i < arg_count; ++i) {
//     switch (arg_type[i]) {
//       case BOOLEAN: henv->CallVoidMethod(nenv->VarArgs, varargs, nenv->VarArgs_pushByte,   args[i].z); break;
//       case BYTE   : henv->CallVoidMethod(nenv->VarArgs, varargs, nenv->VarArgs_pushByte,   args[i].b); break;
//       case CHAR   : henv->CallVoidMethod(nenv->VarArgs, varargs, nenv->VarArgs_pushChar,   args[i].c); break;
//       case SHORT  : henv->CallVoidMethod(nenv->VarArgs, varargs, nenv->VarArgs_pushShort,  args[i].s); break;
//       case INT    : henv->CallVoidMethod(nenv->VarArgs, varargs, nenv->VarArgs_pushInt,    args[i].i); break;
//       case FLOAT  : henv->CallVoidMethod(nenv->VarArgs, varargs, nenv->VarArgs_pushFloat,  args[i].f); break;
//       case DOUBLE : henv->CallVoidMethod(nenv->VarArgs, varargs, nenv->VarArgs_pushDouble, args[i].d); break;
//       case LONG   : henv->CallVoidMethod(nenv->VarArgs, varargs, nenv->VarArgs_pushLong,   args[i].j); break;
//       case OBJECT : henv->CallVoidMethod(nenv->VarArgs, varargs, nenv->VarArgs_pushObject, args[i].l); break;
//     }
//   }
//   jobjectArray tail = (jobjectArray) host_env->CallObjectMethod(nenv->VarArgs, varargs, nenv->VarArgs_getVarArgs);
//   // Call host Object... args method
//   return nenv->CallObjectMethod__(obj, methodID, tail);
// }

JNIEnv* createJniEnv(TruffleEnv* truffle_env, void* (*fetch_by_name)(const char *)) {
  JNINativeInterface_* functions = new JNINativeInterface_();
  JNIEnv* guest_env = new JNIEnv();
  guest_env->functions = functions;

  #define INIT__(name) \
    functions->name = truffle_env->dupClosureRef((typeof(functions->name)) fetch_by_name(#name));

  JNI_FUNCTION_LIST(INIT__)
  #undef INIT__

  //functions->CallObjectMethod = &CallObjectMethod;
  functions->CallObjectMethodA = &CallObjectMethodA;
  functions->CallObjectMethodV = &CallObjectMethodV;
  
  //functions->CallVoidMethod = &CallVoidMethod;
  functions->CallVoidMethodA = &CallVoidMethodA;
  functions->CallVoidMethodV = &CallVoidMethodV;
  
  CallObjectMethod__ = truffle_env->dupClosureRef((typeof(CallObjectMethod__)) fetch_by_name("CallObjectMethod__"));
  CallVoidMethod__ = truffle_env->dupClosureRef((typeof(CallVoidMethod__)) fetch_by_name("CallVoidMethod__"));

  // nenv->VarArgs = henv->NewGlobalRef(henv->FindClass("com/oracle/truffle/espresso/jni/VarArgs"));
  // nenv->VarArgs_pushBoolean = henv->GetMethodID(nenv->VarArgs, "pushBoolean", "(Z)V");
  // nenv->VarArgs_pushByte = henv->GetMethodID(nenv->VarArgs, "pushByte", "(B)V");
  // nenv->VarArgs_pushChar = henv->GetMethodID(nenv->VarArgs, "pushChar", "(C)V");
  // nenv->VarArgs_pushShort = henv->GetMethodID(nenv->VarArgs, "pushShort", "(S)V");
  // nenv->VarArgs_pushInt = henv->GetMethodID(nenv->VarArgs, "pushInt", "(I)V");
  // nenv->VarArgs_pushFloat = henv->GetMethodID(nenv->VarArgs, "pushFloat", "(F)V");
  // nenv->VarArgs_pushDouble = henv->GetMethodID(nenv->VarArgs, "pushDouble", "(D)V");
  // nenv->VarArgs_pushLong = henv->GetMethodID(nenv->VarArgs, "pushLong", "(J)V");
  // nenv->VarArgs_pushObject = henv->GetMethodID(nenv->VarArgs, "pushObject", "(Ljava/lang/Object;)V");
  // nenv->VarArgs_getVarArgs = henv->GetMethodID(nenv->VarArgs, "getVarArgs", "()[Ljava/lang/Object;");
  // nenv->VarArgs_create = henv->GetStaticMethodID(nenv->VarArgs, "create", "(I)Lcom/oracle/truffle/espresso/jni/VarArgs;");

  return guest_env;
}

void disposeJniEnv(TruffleEnv* truffle_env, JNIEnv* env) {
  #define DISPOSE__(name) \
     truffle_env->releaseClosureRef(env->functions->name);

  JNI_FUNCTION_LIST(DISPOSE__)
  #undef DISPOSE__

  truffle_env->releaseClosureRef(CallObjectMethod__);
  truffle_env->releaseClosureRef(CallVoidMethod__);

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


} // extern
