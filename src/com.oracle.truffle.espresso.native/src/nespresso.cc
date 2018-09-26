#include <jni.h>
#include <trufflenfi.h>

extern "C" {

JNIEnv* createJniEnv(TruffleEnv* truffle_env,                     
                     void* (*fetch_by_name)(const char *)
//                      ,
// 
//                      jint (JNICALL* GetVersion)(JNIEnv*),
//                      jint (JNICALL* GetArrayLength)(JNIEnv*, jarray),
//                     
//                      jfieldID (JNICALL *GetFieldID)(JNIEnv *, jclass, const char *, const char *),
//                      jobject (JNICALL *GetObjectField)(JNIEnv *, jobject, jfieldID),
//                      jclass (JNICALL *GetObjectClass)(JNIEnv *, jobject),
//                      
//                      jboolean (JNICALL *GetBooleanField)(JNIEnv *env, jobject obj, jfieldID fieldID),
//                      jbyte (JNICALL *GetByteField)(JNIEnv *env, jobject obj, jfieldID fieldID),
//                      jchar (JNICALL *GetCharField)(JNIEnv *env, jobject obj, jfieldID fieldID),
//                      jshort (JNICALL *GetShortField)(JNIEnv *env, jobject obj, jfieldID fieldID),
//                      jint (JNICALL *GetIntField)(JNIEnv *env, jobject obj, jfieldID fieldID),
//                      jlong (JNICALL *GetLongField)(JNIEnv *env, jobject obj, jfieldID fieldID),
//                      jfloat (JNICALL *GetFloatField)(JNIEnv *env, jobject obj, jfieldID fieldID),
//                      jdouble (JNICALL *GetDoubleField)(JNIEnv *env, jobject obj, jfieldID fieldID)
                     
                     
) {
  JNINativeInterface_* functions = new JNINativeInterface_();

  JNIEnv* env = new JNIEnv();
  env->functions = functions;
  
  functions->GetVersion = (typeof(functions->GetVersion)) fetch_by_name("GetVersion");
  // (*(void**)functions->GetVersion) = (void*)(truffle_env->dupClosureRef(fetch_by_name("GetVersion")));
  functions->GetArrayLength = (typeof(functions->GetArrayLength)) fetch_by_name("GetArrayLength");

//   functions->GetVersion = truffle_env->dupClosureRef(GetVersion);
//   functions->GetArrayLength = truffle_env->dupClosureRef(GetArrayLength);
  /*
  functions->GetFieldID = truffle_env->dupClosureRef(GetFieldID);
  functions->GetObjectField = truffle_env->dupClosureRef(GetObjectField);
  functions->GetObjectClass = truffle_env->dupClosureRef(GetObjectClass);
  
  functions->GetBooleanField = truffle_env->dupClosureRef(GetBooleanField);
  functions->GetByteField = truffle_env->dupClosureRef(GetByteField);
  functions->GetCharField = truffle_env->dupClosureRef(GetCharField);
  functions->GetShortField = truffle_env->dupClosureRef(GetShortField);
  functions->GetIntField = truffle_env->dupClosureRef(GetIntField);
  functions->GetLongField = truffle_env->dupClosureRef(GetLongField);
  functions->GetFloatField = truffle_env->dupClosureRef(GetFloatField);
  functions->GetDoubleField = truffle_env->dupClosureRef(GetDoubleField);*/

  return env;
}

void disposeJniEnv(TruffleEnv* truffle_env, JNIEnv* env) {
  truffle_env->releaseClosureRef(env->functions->GetVersion);
  truffle_env->releaseClosureRef(env->functions->GetArrayLength);

//   truffle_env->releaseClosureRef(env->functions->GetFieldID);
//   truffle_env->releaseClosureRef(env->functions->GetObjectField);
//   truffle_env->releaseClosureRef(env->functions->GetObjectClass);
//   
//   truffle_env->releaseClosureRef(env->functions->GetBooleanField);
//   truffle_env->releaseClosureRef(env->functions->GetByteField);
//   truffle_env->releaseClosureRef(env->functions->GetCharField);
//   truffle_env->releaseClosureRef(env->functions->GetShortField);
//   truffle_env->releaseClosureRef(env->functions->GetIntField);
//   truffle_env->releaseClosureRef(env->functions->GetLongField);
//   truffle_env->releaseClosureRef(env->functions->GetFloatField);
//   truffle_env->releaseClosureRef(env->functions->GetDoubleField);  

  delete env->functions;
  delete env;
}

void* dupClosureRef(TruffleEnv *truffle_env, void* closure) {
    return truffle_env->dupClosureRef(closure);
}

}
