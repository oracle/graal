#include <jni.h>
#include <stdlib.h>
#include <unistd.h>

#include "jvm.h"
#include "jvm_md.h"

JNIEnv *jniEnv = NULL;

void Mokapot_SetJNIEnv(JNIEnv *env) {
  jniEnv = env;
}

jint JVM_GetInterfaceVersion(void) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetInterfaceVersion.\n");
  return 666;
}

jint JVM_IHashCode(JNIEnv *env, jobject obj) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_IHashCode.\n");

  return 0;
}

void JVM_MonitorWait(JNIEnv *env, jobject obj, jlong ms) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_MonitorWait.\n");

}

void JVM_MonitorNotify(JNIEnv *env, jobject obj) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_MonitorNotify.\n");

}

void JVM_MonitorNotifyAll(JNIEnv *env, jobject obj) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_MonitorNotifyAll.\n");

}

jobject JVM_Clone(JNIEnv *env, jobject obj) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_Clone.\n");
  return NULL;
}

jstring JVM_InternString(JNIEnv *env, jstring str) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_InternString.\n");
  return NULL;
}

jlong JVM_CurrentTimeMillis(JNIEnv *env, jclass ignored) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_CurrentTimeMillis.\n");
  return 0;
}

jlong JVM_NanoTime(JNIEnv *env, jclass ignored) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_NanoTime.\n");
  return 0;
}

void JVM_ArrayCopy(JNIEnv *env, jclass ignored, jobject src, jint src_pos, jobject dst, jint dst_pos, jint length) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_ArrayCopy.\n");

}

jobject JVM_InitProperties(JNIEnv *env, jobject p) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_InitProperties.\n");
  return NULL;
}

void JVM_OnExit(void (*func)(void)) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_OnExit.\n");

}

void JVM_Exit(jint code) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_Exit.\n");

}

void JVM_Halt(jint code) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_Halt.\n");

}

void JVM_GC(void) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GC.\n");

}

jlong JVM_MaxObjectInspectionAge(void) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_MaxObjectInspectionAge.\n");
  return 0;
}

void JVM_TraceInstructions(jboolean on) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_TraceInstructions.\n");

}

void JVM_TraceMethodCalls(jboolean on) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_TraceMethodCalls.\n");

}

jlong JVM_TotalMemory(void) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_TotalMemory.\n");
  return 0;
}

jlong JVM_FreeMemory(void) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_FreeMemory.\n");
  return 0;
}

jlong JVM_MaxMemory(void) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_MaxMemory.\n");
  return 0;
}

jint JVM_ActiveProcessorCount(void) {
  fprintf(stderr, "Calling mokapot JVM_ActiveProcessorCount.\n");
  return sysconf(_SC_NPROCESSORS_ONLN);
}

void *JVM_LoadLibrary(const char *name) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_LoadLibrary.\n");
  return NULL;
}

void JVM_UnloadLibrary(void *handle) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_UnloadLibrary.\n");

}

void *JVM_FindLibraryEntry(void *handle, const char *name) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_FindLibraryEntry.\n");
  return NULL;
}

jboolean JVM_IsSupportedJNIVersion(jint version) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_IsSupportedJNIVersion.\n");
  return 0;
}

jboolean JVM_IsNaN(jdouble d) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_IsNaN.\n");
  return 0;
}

void JVM_FillInStackTrace(JNIEnv *env, jobject throwable) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_FillInStackTrace.\n");

}

jint JVM_GetStackTraceDepth(JNIEnv *env, jobject throwable) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetStackTraceDepth.\n");
  return 0;
}

jobject JVM_GetStackTraceElement(JNIEnv *env, jobject throwable, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetStackTraceElement.\n");
  return NULL;
}

void JVM_InitializeCompiler(JNIEnv *env, jclass compCls) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_InitializeCompiler.\n");

}

jboolean JVM_IsSilentCompiler(JNIEnv *env, jclass compCls) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_IsSilentCompiler.\n");
  return 0;
}

jboolean JVM_CompileClass(JNIEnv *env, jclass compCls, jclass cls) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_CompileClass.\n");
  return 0;
}

jboolean JVM_CompileClasses(JNIEnv *env, jclass cls, jstring jname) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_CompileClasses.\n");
  return 0;
}

jobject JVM_CompilerCommand(JNIEnv *env, jclass compCls, jobject arg) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_CompilerCommand.\n");
  return NULL;
}

void JVM_EnableCompiler(JNIEnv *env, jclass compCls) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_EnableCompiler.\n");

}

void JVM_DisableCompiler(JNIEnv *env, jclass compCls) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_DisableCompiler.\n");

}

void JVM_StartThread(JNIEnv *env, jobject thread) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_StartThread.\n");

}

void JVM_StopThread(JNIEnv *env, jobject thread, jobject exception) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_StopThread.\n");

}

jboolean JVM_IsThreadAlive(JNIEnv *env, jobject thread) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_IsThreadAlive.\n");
  return 0;
}

void JVM_SuspendThread(JNIEnv *env, jobject thread) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_SuspendThread.\n");

}

void JVM_ResumeThread(JNIEnv *env, jobject thread) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_ResumeThread.\n");

}

void JVM_SetThreadPriority(JNIEnv *env, jobject thread, jint prio) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_SetThreadPriority.\n");

}

void JVM_Yield(JNIEnv *env, jclass threadClass) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_Yield.\n");

}

void JVM_Sleep(JNIEnv *env, jclass threadClass, jlong millis) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_Sleep.\n");

}

jobject JVM_CurrentThread(JNIEnv *env, jclass threadClass) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_CurrentThread.\n");
  return NULL;
}

jint JVM_CountStackFrames(JNIEnv *env, jobject thread) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_CountStackFrames.\n");
  return 0;
}

void JVM_Interrupt(JNIEnv *env, jobject thread) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_Interrupt.\n");

}

jboolean JVM_IsInterrupted(JNIEnv *env, jobject thread, jboolean clearInterrupted) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_IsInterrupted.\n");
  return 0;
}

jboolean JVM_HoldsLock(JNIEnv *env, jclass threadClass, jobject obj) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_HoldsLock.\n");
  return 0;
}

void JVM_DumpAllStacks(JNIEnv *env, jclass unused) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_DumpAllStacks.\n");

}

jobjectArray JVM_GetAllThreads(JNIEnv *env, jclass dummy) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetAllThreads.\n");
  return NULL;
}

void JVM_SetNativeThreadName(JNIEnv *env, jobject jthread, jstring name) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_SetNativeThreadName.\n");

}

jobjectArray JVM_DumpThreads(JNIEnv *env, jclass threadClass, jobjectArray threads) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_DumpThreads.\n");
  return NULL;
}

jclass JVM_CurrentLoadedClass(JNIEnv *env) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_CurrentLoadedClass.\n");
  return NULL;
}

jobject JVM_CurrentClassLoader(JNIEnv *env) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_CurrentClassLoader.\n");
  return NULL;
}

jobjectArray JVM_GetClassContext(JNIEnv *env) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetClassContext.\n");
  return NULL;
}

jint JVM_ClassDepth(JNIEnv *env, jstring name) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_ClassDepth.\n");
  return 0;
}

jint JVM_ClassLoaderDepth(JNIEnv *env) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_ClassLoaderDepth.\n");
  return 0;
}

jstring JVM_GetSystemPackage(JNIEnv *env, jstring name) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetSystemPackage.\n");
  return NULL;
}

jobjectArray JVM_GetSystemPackages(JNIEnv *env) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetSystemPackages.\n");
  return NULL;
}

jobject JVM_AllocateNewObject(JNIEnv *env, jobject obj, jclass currClass, jclass initClass) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_AllocateNewObject.\n");
  return NULL;
}

jobject JVM_AllocateNewArray(JNIEnv *env, jobject obj, jclass currClass, jint length) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_AllocateNewArray.\n");
  return NULL;
}

jobject JVM_LatestUserDefinedLoader(JNIEnv *env) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_LatestUserDefinedLoader.\n");
  return NULL;
}

jclass JVM_LoadClass0(JNIEnv *env, jobject obj, jclass currClass, jstring currClassName) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_LoadClass0.\n");
  return NULL;
}

jint JVM_GetArrayLength(JNIEnv *env, jobject arr) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetArrayLength.\n");
  return 0;
}

jobject JVM_GetArrayElement(JNIEnv *env, jobject arr, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetArrayElement.\n");
  return NULL;
}

jvalue JVM_GetPrimitiveArrayElement(JNIEnv *env, jobject arr, jint index, jint wCode) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetPrimitiveArrayElement.\n");
  jvalue result = {(jlong) 0};
  return result;
}

void JVM_SetArrayElement(JNIEnv *env, jobject arr, jint index, jobject val) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_SetArrayElement.\n");

}

void JVM_SetPrimitiveArrayElement(JNIEnv *env, jobject arr, jint index, jvalue v, unsigned char vCode) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_SetPrimitiveArrayElement.\n");

}

jobject JVM_NewArray(JNIEnv *env, jclass eltClass, jint length) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_NewArray.\n");
  return NULL;
}

jobject JVM_NewMultiArray(JNIEnv *env, jclass eltClass, jintArray dim) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_NewMultiArray.\n");
  return NULL;
}

jclass JVM_GetCallerClass(JNIEnv *env, int depth) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetCallerClass.\n");
  return NULL;
}

jclass JVM_FindPrimitiveClass(JNIEnv *env, const char *utf) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_FindPrimitiveClass.\n");
  return NULL;
}

void JVM_ResolveClass(JNIEnv *env, jclass cls) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_ResolveClass.\n");

}

jclass JVM_FindClassFromBootLoader(JNIEnv *env, const char *name) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_FindClassFromBootLoader.\n");
  return NULL;
}

jclass JVM_FindClassFromCaller(JNIEnv *env, const char *name, jboolean init, jobject loader, jclass caller) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_FindClassFromCaller.\n");
  return NULL;
}

jclass JVM_FindClassFromClassLoader(JNIEnv *env, const char *name, jboolean init, jobject loader, jboolean throwError) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_FindClassFromClassLoader.\n");
  return NULL;
}

jclass JVM_FindClassFromClass(JNIEnv *env, const char *name, jboolean init, jclass from) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_FindClassFromClass.\n");
  return NULL;
}

jclass JVM_FindLoadedClass(JNIEnv *env, jobject loader, jstring name) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_FindLoadedClass.\n");
  return NULL;
}

jclass JVM_DefineClass(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize len, jobject pd) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_DefineClass.\n");
  return NULL;
}

jclass JVM_DefineClassWithSource(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize len, jobject pd,
                                 const char *source) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_DefineClassWithSource.\n");
  return NULL;
}

jstring JVM_GetClassName(JNIEnv *env, jclass cls) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetClassName.\n");
  return NULL;
}

jobjectArray JVM_GetClassInterfaces(JNIEnv *env, jclass cls) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetClassInterfaces.\n");
  return NULL;
}

jboolean JVM_IsInterface(JNIEnv *env, jclass cls) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_IsInterface.\n");
  return 0;
}

jobjectArray JVM_GetClassSigners(JNIEnv *env, jclass cls) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetClassSigners.\n");
  return NULL;
}

void JVM_SetClassSigners(JNIEnv *env, jclass cls, jobjectArray signers) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_SetClassSigners.\n");

}

jobject JVM_GetProtectionDomain(JNIEnv *env, jclass cls) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetProtectionDomain.\n");
  return NULL;
}

jboolean JVM_IsArrayClass(JNIEnv *env, jclass cls) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_IsArrayClass.\n");
  return 0;
}

jboolean JVM_IsPrimitiveClass(JNIEnv *env, jclass cls) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_IsPrimitiveClass.\n");
  return 0;
}

jclass JVM_GetComponentType(JNIEnv *env, jclass cls) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetComponentType.\n");
  return NULL;
}

jint JVM_GetClassModifiers(JNIEnv *env, jclass cls) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetClassModifiers.\n");
  return 0;
}

jobjectArray JVM_GetDeclaredClasses(JNIEnv *env, jclass ofClass) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetDeclaredClasses.\n");
  return NULL;
}

jclass JVM_GetDeclaringClass(JNIEnv *env, jclass ofClass) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetDeclaringClass.\n");
  return NULL;
}

jstring JVM_GetClassSignature(JNIEnv *env, jclass cls) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetClassSignature.\n");
  return NULL;
}

jbyteArray JVM_GetClassAnnotations(JNIEnv *env, jclass cls) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetClassAnnotations.\n");
  return NULL;
}

jbyteArray JVM_GetClassTypeAnnotations(JNIEnv *env, jclass cls) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetClassTypeAnnotations.\n");
  return NULL;
}

jbyteArray JVM_GetFieldTypeAnnotations(JNIEnv *env, jobject field) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetFieldTypeAnnotations.\n");
  return NULL;
}

jbyteArray JVM_GetMethodTypeAnnotations(JNIEnv *env, jobject method) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetMethodTypeAnnotations.\n");
  return NULL;
}

jobjectArray JVM_GetClassDeclaredMethods(JNIEnv *env, jclass ofClass, jboolean publicOnly) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetClassDeclaredMethods.\n");
  return NULL;
}

jobjectArray JVM_GetClassDeclaredFields(JNIEnv *env, jclass ofClass, jboolean publicOnly) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetClassDeclaredFields.\n");
  return NULL;
}

jobjectArray JVM_GetClassDeclaredConstructors(JNIEnv *env, jclass ofClass, jboolean publicOnly) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetClassDeclaredConstructors.\n");
  return NULL;
}

jint JVM_GetClassAccessFlags(JNIEnv *env, jclass cls) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetClassAccessFlags.\n");
  return 0;
}

jobject JVM_InvokeMethod(JNIEnv *env, jobject method, jobject obj, jobjectArray args0) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_InvokeMethod.\n");
  return NULL;
}

jobject JVM_NewInstanceFromConstructor(JNIEnv *env, jobject c, jobjectArray args0) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_NewInstanceFromConstructor.\n");
  return NULL;
}

jobject JVM_GetClassConstantPool(JNIEnv *env, jclass cls) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetClassConstantPool.\n");
  return NULL;
}

jint JVM_ConstantPoolGetSize(JNIEnv *env, jobject unused, jobject jcpool) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_ConstantPoolGetSize.\n");
  return 0;
}

jclass JVM_ConstantPoolGetClassAt(JNIEnv *env, jobject unused, jobject jcpool, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_ConstantPoolGetClassAt.\n");
  return NULL;
}

jclass JVM_ConstantPoolGetClassAtIfLoaded(JNIEnv *env, jobject unused, jobject jcpool, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_ConstantPoolGetClassAtIfLoaded.\n");
  return NULL;
}

jobject JVM_ConstantPoolGetMethodAt(JNIEnv *env, jobject unused, jobject jcpool, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_ConstantPoolGetMethodAt.\n");
  return NULL;
}

jobject JVM_ConstantPoolGetMethodAtIfLoaded(JNIEnv *env, jobject unused, jobject jcpool, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_ConstantPoolGetMethodAtIfLoaded.\n");
  return NULL;
}

jobject JVM_ConstantPoolGetFieldAt(JNIEnv *env, jobject unused, jobject jcpool, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_ConstantPoolGetFieldAt.\n");
  return NULL;
}

jobject JVM_ConstantPoolGetFieldAtIfLoaded(JNIEnv *env, jobject unused, jobject jcpool, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_ConstantPoolGetFieldAtIfLoaded.\n");
  return NULL;
}

jobjectArray JVM_ConstantPoolGetMemberRefInfoAt(JNIEnv *env, jobject unused, jobject jcpool, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_ConstantPoolGetMemberRefInfoAt.\n");
  return NULL;
}

jint JVM_ConstantPoolGetIntAt(JNIEnv *env, jobject unused, jobject jcpool, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_ConstantPoolGetIntAt.\n");
  return 0;
}

jlong JVM_ConstantPoolGetLongAt(JNIEnv *env, jobject unused, jobject jcpool, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_ConstantPoolGetLongAt.\n");
  return 0;
}

jfloat JVM_ConstantPoolGetFloatAt(JNIEnv *env, jobject unused, jobject jcpool, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_ConstantPoolGetFloatAt.\n");
  return 0;
}

jdouble JVM_ConstantPoolGetDoubleAt(JNIEnv *env, jobject unused, jobject jcpool, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_ConstantPoolGetDoubleAt.\n");
  return 0;
}

jstring JVM_ConstantPoolGetStringAt(JNIEnv *env, jobject unused, jobject jcpool, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_ConstantPoolGetStringAt.\n");
  return NULL;
}

jstring JVM_ConstantPoolGetUTF8At(JNIEnv *env, jobject unused, jobject jcpool, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_ConstantPoolGetUTF8At.\n");
  return NULL;
}

jobjectArray JVM_GetMethodParameters(JNIEnv *env, jobject method) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetMethodParameters.\n");
  return NULL;
}

jobject JVM_DoPrivileged(JNIEnv *env, jclass cls, jobject action, jobject context, jboolean wrapException) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_DoPrivileged.\n");
  return NULL;
}

jobject JVM_GetInheritedAccessControlContext(JNIEnv *env, jclass cls) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetInheritedAccessControlContext.\n");
  return NULL;
}

jobject JVM_GetStackAccessControlContext(JNIEnv *env, jclass cls) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetStackAccessControlContext.\n");
  return NULL;
}

void *JVM_RegisterSignal(jint sig, void *handler) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_RegisterSignal.\n");
  return NULL;
}

jboolean JVM_RaiseSignal(jint sig) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_RaiseSignal.\n");
  return 0;
}

jint JVM_FindSignal(const char *name) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_FindSignal.\n");
  return 0;
}

jboolean JVM_DesiredAssertionStatus(JNIEnv *env, jclass unused, jclass cls) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_DesiredAssertionStatus.\n");
  return 0;
}

jobject JVM_AssertionStatusDirectives(JNIEnv *env, jclass unused) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_AssertionStatusDirectives.\n");
  return NULL;
}

jboolean JVM_SupportsCX8(void) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_SupportsCX8.\n");
  return 0;
}

jint JVM_DTraceGetVersion(JNIEnv *env) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_DTraceGetVersion.\n");
  return 0;
}

jlong JVM_DTraceActivate(JNIEnv *env, jint version, jstring module_name, jint providers_count,
                         JVM_DTraceProvider *providers) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_DTraceActivate.\n");
  return 0;
}

jboolean JVM_DTraceIsProbeEnabled(JNIEnv *env, jmethodID method) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_DTraceIsProbeEnabled.\n");
  return 0;
}

void JVM_DTraceDispose(JNIEnv *env, jlong activation_handle) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_DTraceDispose.\n");

}

jboolean JVM_DTraceIsSupported(JNIEnv *env) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_DTraceIsSupported.\n");
  return 0;
}

const char *JVM_GetClassNameUTF(JNIEnv *env, jclass cb) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetClassNameUTF.\n");
  return NULL;
}

void JVM_GetClassCPTypes(JNIEnv *env, jclass cb, unsigned char *types) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetClassCPTypes.\n");

}

jint JVM_GetClassCPEntriesCount(JNIEnv *env, jclass cb) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetClassCPEntriesCount.\n");
  return 0;
}

jint JVM_GetClassFieldsCount(JNIEnv *env, jclass cb) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetClassFieldsCount.\n");
  return 0;
}

jint JVM_GetClassMethodsCount(JNIEnv *env, jclass cb) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetClassMethodsCount.\n");
  return 0;
}

void JVM_GetMethodIxExceptionIndexes(JNIEnv *env, jclass cb, jint method_index, unsigned short *exceptions) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetMethodIxExceptionIndexes.\n");

}

jint JVM_GetMethodIxExceptionsCount(JNIEnv *env, jclass cb, jint method_index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetMethodIxExceptionsCount.\n");
  return 0;
}

void JVM_GetMethodIxByteCode(JNIEnv *env, jclass cb, jint method_index, unsigned char *code) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetMethodIxByteCode.\n");

}

jint JVM_GetMethodIxByteCodeLength(JNIEnv *env, jclass cb, jint method_index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetMethodIxByteCodeLength.\n");
  return 0;
}

void JVM_GetMethodIxExceptionTableEntry(JNIEnv *env, jclass cb, jint method_index, jint entry_index,
                                        JVM_ExceptionTableEntryType *entry) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetMethodIxExceptionTableEntry.\n");

}

jint JVM_GetMethodIxExceptionTableLength(JNIEnv *env, jclass cb, int index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetMethodIxExceptionTableLength.\n");
  return 0;
}

jint JVM_GetFieldIxModifiers(JNIEnv *env, jclass cb, int index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetFieldIxModifiers.\n");
  return 0;
}

jint JVM_GetMethodIxModifiers(JNIEnv *env, jclass cb, int index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetMethodIxModifiers.\n");
  return 0;
}

jint JVM_GetMethodIxLocalsCount(JNIEnv *env, jclass cb, int index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetMethodIxLocalsCount.\n");
  return 0;
}

jint JVM_GetMethodIxArgsSize(JNIEnv *env, jclass cb, int index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetMethodIxArgsSize.\n");
  return 0;
}

jint JVM_GetMethodIxMaxStack(JNIEnv *env, jclass cb, int index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetMethodIxMaxStack.\n");
  return 0;
}

jboolean JVM_IsConstructorIx(JNIEnv *env, jclass cb, int index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_IsConstructorIx.\n");
  return 0;
}

jboolean JVM_IsVMGeneratedMethodIx(JNIEnv *env, jclass cb, int index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_IsVMGeneratedMethodIx.\n");
  return 0;
}

const char *JVM_GetMethodIxNameUTF(JNIEnv *env, jclass cb, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetMethodIxNameUTF.\n");
  return NULL;
}

const char *JVM_GetMethodIxSignatureUTF(JNIEnv *env, jclass cb, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetMethodIxSignatureUTF.\n");
  return NULL;
}

const char *JVM_GetCPFieldNameUTF(JNIEnv *env, jclass cb, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetCPFieldNameUTF.\n");
  return NULL;
}

const char *JVM_GetCPMethodNameUTF(JNIEnv *env, jclass cb, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetCPMethodNameUTF.\n");
  return NULL;
}

const char *JVM_GetCPMethodSignatureUTF(JNIEnv *env, jclass cb, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetCPMethodSignatureUTF.\n");
  return NULL;
}

const char *JVM_GetCPFieldSignatureUTF(JNIEnv *env, jclass cb, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetCPFieldSignatureUTF.\n");
  return NULL;
}

const char *JVM_GetCPClassNameUTF(JNIEnv *env, jclass cb, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetCPClassNameUTF.\n");
  return NULL;
}

const char *JVM_GetCPFieldClassNameUTF(JNIEnv *env, jclass cb, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetCPFieldClassNameUTF.\n");
  return NULL;
}

const char *JVM_GetCPMethodClassNameUTF(JNIEnv *env, jclass cb, jint index) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetCPMethodClassNameUTF.\n");
  return NULL;
}

jint JVM_GetCPFieldModifiers(JNIEnv *env, jclass cb, int index, jclass calledClass) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetCPFieldModifiers.\n");
  return 0;
}

jint JVM_GetCPMethodModifiers(JNIEnv *env, jclass cb, int index, jclass calledClass) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetCPMethodModifiers.\n");
  return 0;
}

void JVM_ReleaseUTF(const char *utf) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_ReleaseUTF.\n");

}

jboolean JVM_IsSameClassPackage(JNIEnv *env, jclass class1, jclass class2) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_IsSameClassPackage.\n");
  return 0;
}

jint JVM_GetLastErrorString(char *buf, int len) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetLastErrorString.\n");
  return 0;
}

char *JVM_NativePath(char *pathname) {
  fprintf(stderr, "Calling mokapot JVM_NativePath.\n");
  // TODO(peterssen): This mimics the HotSpot implementation... yet another useless method.
  return pathname;
}

jint JVM_Open(const char *path, jint oflag, jint mode) {
  fprintf(stderr, "Calling mokapot JVM_Open.\n");

  FD fd;
  RESTARTABLE(open(path, oflag, mode), fd);
  if (fd != -1) {
    struct stat buf64;
    int result;
    RESTARTABLE(fstat(fd, &buf64), result);
    if (result != -1) {
      if (S_ISDIR(buf64.st_mode)) {
        close(fd);
        errno = EISDIR;
        fd = -1;
        }
      } else {
      close(fd);
      fd = -1;
    }
  }
  return fd;
}

jint JVM_Close(jint fd) {
  fprintf(stderr, "Calling mokapot JVM_Close.\n");
  return close(fd);
}

jint JVM_Read(jint fd, char *buf, jint nbytes) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_Read.\n");
  return 0;
}

jint JVM_Write(jint fd, char *buf, jint nbytes) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_Write.\n");
  return 0;
}

jint JVM_Available(jint fd, jlong *pbytes) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_Available.\n");
  return 0;
}

jlong JVM_Lseek(jint fd, jlong offset, jint whence) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_Lseek.\n");
  return 0;
}

jint JVM_SetLength(jint fd, jlong length) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_SetLength.\n");
  return 0;
}

jint JVM_Sync(jint fd) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_Sync.\n");
  return 0;
}

jint JVM_InitializeSocketLibrary(void) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_InitializeSocketLibrary.\n");
  return 0;
}

jint JVM_Socket(jint domain, jint type, jint protocol) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_Socket.\n");
  return 0;
}

jint JVM_SocketClose(jint fd) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_SocketClose.\n");
  return 0;
}

jint JVM_SocketShutdown(jint fd, jint howto) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_SocketShutdown.\n");
  return 0;
}

jint JVM_Recv(jint fd, char *buf, jint nBytes, jint flags) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_Recv.\n");
  return 0;
}

jint JVM_Send(jint fd, char *buf, jint nBytes, jint flags) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_Send.\n");
  return 0;
}

jint JVM_Timeout(int fd, long timeout) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_Timeout.\n");
  return 0;
}

jint JVM_Listen(jint fd, jint count) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_Listen.\n");
  return 0;
}

jint JVM_Connect(jint fd, struct sockaddr *him, jint len) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_Connect.\n");
  return 0;
}

jint JVM_Bind(jint fd, struct sockaddr *him, jint len) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_Bind.\n");
  return 0;
}

jint JVM_Accept(jint fd, struct sockaddr *him, jint *len) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_Accept.\n");
  return 0;
}

jint JVM_RecvFrom(jint fd, char *buf, int nBytes, int flags, struct sockaddr *from, int *fromlen) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_RecvFrom.\n");
  return 0;
}

jint JVM_SendTo(jint fd, char *buf, int len, int flags, struct sockaddr *to, int tolen) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_SendTo.\n");
  return 0;
}

jint JVM_SocketAvailable(jint fd, jint *result) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_SocketAvailable.\n");
  return 0;
}

jint JVM_GetSockName(jint fd, struct sockaddr *him, int *len) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetSockName.\n");
  return 0;
}

jint JVM_GetSockOpt(jint fd, int level, int optname, char *optval, int *optlen) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetSockOpt.\n");
  return 0;
}

jint JVM_SetSockOpt(jint fd, int level, int optname, const char *optval, int optlen) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_SetSockOpt.\n");
  return 0;
}

int JVM_GetHostName(char *name, int namelen) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetHostName.\n");
  return 0;
}

void *JVM_RawMonitorCreate(void) {
  // fprintf(stderr, "Calling mokapot JVM_RawMonitorCreate.\n");
  // TODO(peterssen): Cache class and method.
  jclass java_lang_Object = (*jniEnv)->FindClass(jniEnv, "java.lang.Object");
  jmethodID constructor = (*jniEnv)->GetMethodID(jniEnv, java_lang_Object, "<init>", "()V");
  jobject lock = (*jniEnv)->NewObject(jniEnv, java_lang_Object, constructor);
  return (void*) (*jniEnv)->NewGlobalRef(jniEnv, lock);
}

void JVM_RawMonitorDestroy(void *mon) {
  // fprintf(stderr, "Calling unimplemented mokapot JVM_RawMonitorDestroy.\n");
  // TODO(peterssen): Cache class and method.
  jobject lock = (jobject) mon;
  (*jniEnv)->DeleteGlobalRef(jniEnv, lock);
}

jint JVM_RawMonitorEnter(void *mon) {
  // fprintf(stderr, "Calling mokapot JVM_RawMonitorEnter.\n");
  return (*jniEnv)->MonitorEnter(jniEnv, (jobject) mon);
}

void JVM_RawMonitorExit(void *mon) {
  // fprintf(stderr, "Calling mokapot JVM_RawMonitorExit.\n");
  (*jniEnv)->MonitorExit(jniEnv, (jobject) mon);
}

void *JVM_GetManagement(jint version) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetManagement.\n");
  return NULL;
}

jobject JVM_InitAgentProperties(JNIEnv *env, jobject agent_props) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_InitAgentProperties.\n");
  return NULL;
}

jstring JVM_GetTemporaryDirectory(JNIEnv *env) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetTemporaryDirectory.\n");
  return NULL;
}

jobjectArray JVM_GetEnclosingMethodInfo(JNIEnv *env, jclass ofClass) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetEnclosingMethodInfo.\n");
  return NULL;
}

jintArray JVM_GetThreadStateValues(JNIEnv *env, jint javaThreadState) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetThreadStateValues.\n");
  return NULL;
}

jobjectArray JVM_GetThreadStateNames(JNIEnv *env, jint javaThreadState, jintArray values) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetThreadStateNames.\n");
  return NULL;
}

jboolean JVM_KnownToNotExist(JNIEnv *env, jobject loader, const char *classname) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_KnownToNotExist.\n");
  return 0;
}

jobjectArray JVM_GetResourceLookupCacheURLs(JNIEnv *env, jobject loader) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetResourceLookupCacheURLs.\n");
  return NULL;
}

jintArray JVM_GetResourceLookupCache(JNIEnv *env, jobject loader, const char *resource_name) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetResourceLookupCache.\n");
  return NULL;
}

void JVM_GetVersionInfo(JNIEnv *env, jvm_version_info *info, size_t info_size) {
  fprintf(stderr, "Calling unimplemented mokapot JVM_GetVersionInfo.\n");

}

int jio_vsnprintf(char *str, size_t count, const char *fmt, va_list args) {
    fprintf(stderr, "Calling unimplemented mokapot jio_vsnprintf.\n");
    return 0;
}

int jio_snprintf(char *str, size_t count, const char *fmt, ...) {
    fprintf(stderr, "Calling unimplemented mokapot jio_snprintf.\n");
    return 0;
}

int jio_fprintf(FILE *file, const char *fmt, ...) {
    fprintf(stderr, "Calling unimplemented mokapot jio_fprintf.\n");
    return 0;
}

int jio_vfprintf(FILE *file, const char *fmt, va_list args) {
    fprintf(stderr, "Calling unimplemented mokapot jio_vfprintf.\n");
    return 0;
}
