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
#ifndef _MOKAPOT_H
#define _MOKAPOT_H

#include "jvm.h"
#include "jni.h"
#include "os.h"

#include "libjavavm_dynamic.h"

#include <trufflenfi.h>
#include <stddef.h>
#include <stdint.h>
#include <errno.h>

struct MokapotNativeInterface_;
struct MokapotEnv_;

#ifdef __cplusplus
typedef MokapotEnv_ MokapotEnv;
#else
typedef const struct MokapotNativeInterface_ *MokapotEnv;
#endif

#define UNIMPLEMENTED(name) \
  fprintf(stderr, "Calling unimplemented mokapot %s\n", #name);

#define IMPLEMENTED(name) do {} while (0);

// Methods implemented in C (not Java call)
#define NATIVE(name) do {} while (0);

// Additional Java basic types

typedef uint8_t  jubyte;
typedef uint16_t jushort;
typedef uint32_t juint;
typedef uint64_t julong;

// A VM created from espresso host Java code through initializeMokapotContext
#define MOKA_RISTRETTO ((void *)11)
// A VM created from JNI_CreateJavaVM
#define MOKA_LATTE ((void *)22)
// A MOKA_RISTRETTO VM that is used by a MOKA_LATTE VM
#define MOKA_AMERICANO ((void *)33)


/* Usage of the JavaVM reserved fields:
 * vm type   | MOKA_RISTRETTO | MOKA_LATTE          | MOKA_AMERICANO |
 * ----------+----------------+---------------------+----------------+
 * reserved0 | NULL           | LibJavaVMIsolate* | context handle |
 * reserved1 | MOKA_RISTRETTO | MOKA_LATTE          | MOKA_AMERICANO |
 * reserved2 | NULL           | JavaVM* (americano) | JavaVM* (latte)|
 */

#define VM_METHOD_LIST(V) \
    V(JVM_Accept) \
    V(JVM_ActiveProcessorCount) \
    V(JVM_AllocateNewArray) \
    V(JVM_AllocateNewObject) \
    V(JVM_ArrayCopy) \
    V(JVM_AssertionStatusDirectives) \
    V(JVM_Available) \
    V(JVM_Bind) \
    V(JVM_ClassDepth) \
    V(JVM_ClassLoaderDepth) \
    V(JVM_Clone) \
    V(JVM_Close) \
    V(JVM_CX8Field) \
    V(JVM_CompileClass) \
    V(JVM_CompileClasses) \
    V(JVM_CompilerCommand) \
    V(JVM_Connect) \
    V(JVM_ConstantPoolGetClassAt) \
    V(JVM_ConstantPoolGetClassAtIfLoaded) \
    V(JVM_ConstantPoolGetDoubleAt) \
    V(JVM_ConstantPoolGetFieldAt) \
    V(JVM_ConstantPoolGetFieldAtIfLoaded) \
    V(JVM_ConstantPoolGetFloatAt) \
    V(JVM_ConstantPoolGetIntAt) \
    V(JVM_ConstantPoolGetLongAt) \
    V(JVM_ConstantPoolGetMethodAt) \
    V(JVM_ConstantPoolGetMethodAtIfLoaded) \
    V(JVM_ConstantPoolGetMemberRefInfoAt) \
    V(JVM_ConstantPoolGetSize) \
    V(JVM_ConstantPoolGetStringAt) \
    V(JVM_ConstantPoolGetUTF8At) \
    V(JVM_CopySwapMemory) \
    V(JVM_CountStackFrames) \
    V(JVM_CurrentClassLoader) \
    V(JVM_CurrentLoadedClass) \
    V(JVM_CurrentThread) \
    V(JVM_CurrentTimeMillis) \
    V(JVM_DefineClass) \
    V(JVM_DefineClassWithSource) \
    V(JVM_DefineClassWithSourceCond) \
    V(JVM_DesiredAssertionStatus) \
    V(JVM_DisableCompiler) \
    V(JVM_DoPrivileged) \
    V(JVM_DTraceGetVersion) \
    V(JVM_DTraceActivate) \
    V(JVM_DTraceIsProbeEnabled) \
    V(JVM_DTraceIsSupported) \
    V(JVM_DTraceDispose) \
    V(JVM_DumpAllStacks) \
    V(JVM_DumpThreads) \
    V(JVM_EnableCompiler) \
    V(JVM_Exit) \
    V(JVM_FillInStackTrace) \
    V(JVM_FindClassFromCaller) \
    V(JVM_FindClassFromClass) \
    V(JVM_FindClassFromClassLoader) \
    V(JVM_FindClassFromBootLoader) \
    V(JVM_FindLibraryEntry) \
    V(JVM_FindLoadedClass) \
    V(JVM_FindPrimitiveClass) \
    V(JVM_FindSignal) \
    V(JVM_FreeMemory) \
    V(JVM_GC) \
    V(JVM_GetAllThreads) \
    V(JVM_GetArrayElement) \
    V(JVM_GetArrayLength) \
    V(JVM_GetCPClassNameUTF) \
    V(JVM_GetCPFieldClassNameUTF) \
    V(JVM_GetCPFieldModifiers) \
    V(JVM_GetCPFieldNameUTF) \
    V(JVM_GetCPFieldSignatureUTF) \
    V(JVM_GetCPMethodClassNameUTF) \
    V(JVM_GetCPMethodModifiers) \
    V(JVM_GetCPMethodNameUTF) \
    V(JVM_GetCPMethodSignatureUTF) \
    V(JVM_GetCallerClass) \
    V(JVM_GetClassAccessFlags) \
    V(JVM_GetClassAnnotations) \
    V(JVM_GetClassCPEntriesCount) \
    V(JVM_GetClassCPTypes) \
    V(JVM_GetClassConstantPool) \
    V(JVM_GetClassContext) \
    V(JVM_GetClassDeclaredConstructors) \
    V(JVM_GetClassDeclaredFields) \
    V(JVM_GetClassDeclaredMethods) \
    V(JVM_GetClassFieldsCount) \
    V(JVM_GetClassInterfaces) \
    V(JVM_GetClassLoader) \
    V(JVM_GetClassMethodsCount) \
    V(JVM_GetClassModifiers) \
    V(JVM_GetClassName) \
    V(JVM_GetClassNameUTF) \
    V(JVM_GetClassSignature) \
    V(JVM_GetClassSigners) \
    V(JVM_GetClassTypeAnnotations) \
    V(JVM_GetComponentType) \
    V(JVM_GetDeclaredClasses) \
    V(JVM_GetDeclaringClass) \
    V(JVM_GetEnclosingMethodInfo) \
    V(JVM_GetFieldAnnotations) \
    V(JVM_GetFieldIxModifiers) \
    V(JVM_GetFieldTypeAnnotations) \
    V(JVM_GetHostName) \
    V(JVM_GetInheritedAccessControlContext) \
    V(JVM_GetInterfaceVersion) \
    V(JVM_GetLastErrorString) \
    V(JVM_GetManagement) \
    V(JVM_GetMethodAnnotations) \
    V(JVM_GetMethodDefaultAnnotationValue) \
    V(JVM_GetMethodIxArgsSize) \
    V(JVM_GetMethodIxByteCode) \
    V(JVM_GetMethodIxByteCodeLength) \
    V(JVM_GetMethodIxExceptionIndexes) \
    V(JVM_GetMethodIxExceptionTableEntry) \
    V(JVM_GetMethodIxExceptionTableLength) \
    V(JVM_GetMethodIxExceptionsCount) \
    V(JVM_GetMethodIxLocalsCount) \
    V(JVM_GetMethodIxMaxStack) \
    V(JVM_GetMethodIxModifiers) \
    V(JVM_GetMethodIxNameUTF) \
    V(JVM_GetMethodIxSignatureUTF) \
    V(JVM_GetMethodParameterAnnotations) \
    V(JVM_GetMethodParameters) \
    V(JVM_GetMethodTypeAnnotations) \
    V(JVM_GetPrimitiveArrayElement) \
    V(JVM_GetProtectionDomain) \
    V(JVM_GetSockName) \
    V(JVM_GetSockOpt) \
    V(JVM_GetStackAccessControlContext) \
    V(JVM_GetStackTraceDepth) \
    V(JVM_GetStackTraceElement) \
    V(JVM_GetSystemPackage) \
    V(JVM_GetSystemPackages) \
    V(JVM_GetTemporaryDirectory) \
    V(JVM_GetThreadStateNames) \
    V(JVM_GetThreadStateValues) \
    V(JVM_GetVersionInfo) \
    V(JVM_Halt) \
    V(JVM_HoldsLock) \
    V(JVM_IHashCode) \
    V(JVM_InitAgentProperties) \
    V(JVM_InitProperties) \
    V(JVM_InitializeCompiler) \
    V(JVM_InitializeSocketLibrary) \
    V(JVM_InternString) \
    V(JVM_Interrupt) \
    V(JVM_InvokeMethod) \
    V(JVM_IsArrayClass) \
    V(JVM_IsConstructorIx) \
    V(JVM_IsInterface) \
    V(JVM_IsInterrupted) \
    V(JVM_IsNaN) \
    V(JVM_IsPrimitiveClass) \
    V(JVM_IsSameClassPackage) \
    V(JVM_IsSilentCompiler) \
    V(JVM_IsSupportedJNIVersion) \
    V(JVM_IsThreadAlive) \
    V(JVM_IsUseContainerSupport) \
    V(JVM_IsVMGeneratedMethodIx) \
    V(JVM_LatestUserDefinedLoader) \
    V(JVM_Listen) \
    V(JVM_LoadClass0) \
    V(JVM_LoadZipLibrary) \
    V(JVM_LoadLibrary) \
    V(JVM_Lseek) \
    V(JVM_MaxObjectInspectionAge) \
    V(JVM_MaxMemory) \
    V(JVM_MonitorNotify) \
    V(JVM_MonitorNotifyAll) \
    V(JVM_MonitorWait) \
    V(JVM_NanoTime) \
    V(JVM_NativePath) \
    V(JVM_NewArray) \
    V(JVM_NewInstanceFromConstructor) \
    V(JVM_NewMultiArray) \
    V(JVM_OnExit) \
    V(JVM_Open) \
    V(JVM_RaiseSignal) \
    V(JVM_RawMonitorCreate) \
    V(JVM_RawMonitorDestroy) \
    V(JVM_RawMonitorEnter) \
    V(JVM_RawMonitorExit) \
    V(JVM_Read) \
    V(JVM_Recv) \
    V(JVM_RecvFrom) \
    V(JVM_RegisterSignal) \
    V(JVM_ReleaseUTF) \
    V(JVM_ResolveClass) \
    V(JVM_KnownToNotExist) \
    V(JVM_GetResourceLookupCacheURLs) \
    V(JVM_GetResourceLookupCache) \
    V(JVM_ResumeThread) \
    V(JVM_Send) \
    V(JVM_SendTo) \
    V(JVM_SetArrayElement) \
    V(JVM_SetClassSigners) \
    V(JVM_SetLength) \
    V(JVM_SetNativeThreadName) \
    V(JVM_SetPrimitiveArrayElement) \
    V(JVM_SetSockOpt) \
    V(JVM_SetThreadPriority) \
    V(JVM_Sleep) \
    V(JVM_Socket) \
    V(JVM_SocketAvailable) \
    V(JVM_SocketClose) \
    V(JVM_SocketShutdown) \
    V(JVM_StartThread) \
    V(JVM_StopThread) \
    V(JVM_SuspendThread) \
    V(JVM_SupportsCX8) \
    V(JVM_Sync) \
    V(JVM_Timeout) \
    V(JVM_TotalMemory) \
    V(JVM_TraceInstructions) \
    V(JVM_TraceMethodCalls) \
    V(JVM_UnloadLibrary) \
    V(JVM_Write) \
    V(JVM_Yield) \
    /* V(JVM_handle_linux_signal) */ \
    /* Invocation API */ \
    V(JNI_GetCreatedJavaVMs) \
    /* Java 11 VM methods */ \
    V(JVM_AddModuleExports) \
    V(JVM_AddModuleExportsToAll) \
    V(JVM_AddModuleExportsToAllUnnamed) \
    V(JVM_AddReadsModule) \
    V(JVM_AreNestMates) \
    V(JVM_BeforeHalt) \
    V(JVM_CallStackWalk) \
    V(JVM_ConstantPoolGetClassRefIndexAt) \
    V(JVM_ConstantPoolGetNameAndTypeRefIndexAt) \
    V(JVM_ConstantPoolGetNameAndTypeRefInfoAt) \
    V(JVM_ConstantPoolGetTagAt) \
    V(JVM_DefineModule) \
    V(JVM_GetAndClearReferencePendingList) \
    V(JVM_GetNanoTimeAdjustment) \
    V(JVM_GetNestHost) \
    V(JVM_GetNestMembers) \
    V(JVM_GetSimpleBinaryName) \
    V(JVM_GetVmArguments) \
    V(JVM_HasReferencePendingList) \
    V(JVM_InitClassName) \
    V(JVM_InitializeFromArchive) \
    V(JVM_InitStackTraceElement) \
    V(JVM_InitStackTraceElementArray) \
    V(JVM_MoreStackWalk) \
    /* V(JVM_RegisterJDKInternalMiscUnsafeMethods) */ \
    V(JVM_SetBootLoaderUnnamedModule) \
    V(JVM_WaitForReferencePendingList) \
    /* Java 16 VM methods */ \
    V(JVM_DefineArchivedModules) \
    V(JVM_GetExtendedNPEMessage) \
    V(JVM_GetPermittedSubclasses) \
    V(JVM_GetProperties) \
    V(JVM_GetRandomSeedForDumping) \
    V(JVM_GetRecordComponents) \
    V(JVM_IsCDSDumpingEnabled) \
    V(JVM_IsDumpingClassList) \
    V(JVM_IsHiddenClass) \
    V(JVM_IsRecord) \
    V(JVM_IsSharingEnabled) \
    V(JVM_IsUseContainerSupport) \
    V(JVM_LogLambdaFormInvoker) \
    V(JVM_LookupDefineClass) \
    V(JVM_LookupLambdaProxyClassFromArchive) \
    V(JVM_PhantomReferenceRefersTo) \
    V(JVM_ReferenceClear) \
    V(JVM_ReferenceRefersTo) \
    V(JVM_RegisterLambdaProxyClassForArchiving)

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT MokapotEnv* JNICALL initializeMokapotContext(JNIEnv* env, void* (*fetch_by_name)(const char *, void*));

JNIEXPORT void JNICALL disposeMokapotContext(MokapotEnv* moka_env, void (*release_closure)(void *));

JNIEXPORT JavaVM* JNICALL getJavaVM(MokapotEnv* moka_env);

JNIEXPORT void JNICALL mokapotAttachThread(MokapotEnv* moka_env);

JNIEXPORT OS_DL_HANDLE JNICALL mokapotGetRTLD_DEFAULT();

JNIEXPORT OS_DL_HANDLE JNICALL mokapotGetProcessHandle();

JNIEXPORT const char* JNICALL getPackageAt(const char* const* packages, int at);

#ifdef __cplusplus
} // extern "C"
#endif

struct MokapotNativeInterface_ {

    JavaVM *vm;

jint (*JVM_GetInterfaceVersion)(void);

jint (*JVM_IHashCode)(JNIEnv *env, jobject obj);

void (*JVM_MonitorWait)(JNIEnv *env, jobject obj, jlong ms);

void (*JVM_MonitorNotify)(JNIEnv *env, jobject obj);

void (*JVM_MonitorNotifyAll)(JNIEnv *env, jobject obj);

jobject (*JVM_Clone)(JNIEnv *env, jobject obj);

jstring (*JVM_InternString)(JNIEnv *env, jstring str);

jlong (*JVM_CurrentTimeMillis)(JNIEnv *env, jclass ignored);

jlong (*JVM_NanoTime)(JNIEnv *env, jclass ignored);

void (*JVM_ArrayCopy)(JNIEnv *env, jclass ignored, jobject src, jint src_pos,
            jobject dst, jint dst_pos, jint length);
jobject (*JVM_InitProperties)(JNIEnv *env, jobject p);

void (*JVM_OnExit)(void (*func)(void));

void (*JVM_Exit)(jint code);

void (*JVM_Halt)(jint code);

void (*JVM_GC)(void);

jlong (*JVM_MaxObjectInspectionAge)(void);

void (*JVM_TraceInstructions)(jboolean on);

void (*JVM_TraceMethodCalls)(jboolean on);

jlong (*JVM_TotalMemory)(void);

jlong (*JVM_FreeMemory)(void);

jlong (*JVM_MaxMemory)(void);

jint (*JVM_ActiveProcessorCount)(void);

void * (*JVM_LoadZipLibrary)(void);

void * (*JVM_LoadLibrary)(const char *name /*, jboolean throwException*/);

void (*JVM_UnloadLibrary)(void * handle);

void * (*JVM_FindLibraryEntry)(void *handle, const char *name);

jboolean (*JVM_IsSupportedJNIVersion)(jint version);

jboolean (*JVM_IsNaN)(jdouble d);

void (*JVM_FillInStackTrace)(JNIEnv *env, jobject throwable);

jint (*JVM_GetStackTraceDepth)(JNIEnv *env, jobject throwable);

jobject (*JVM_GetStackTraceElement)(JNIEnv *env, jobject throwable, jint index);

void (*JVM_InitializeCompiler) (JNIEnv *env, jclass compCls);

jboolean (*JVM_IsSilentCompiler)(JNIEnv *env, jclass compCls);

jboolean (*JVM_CompileClass)(JNIEnv *env, jclass compCls, jclass cls);

jboolean (*JVM_CompileClasses)(JNIEnv *env, jclass cls, jstring jname);

jobject (*JVM_CompilerCommand)(JNIEnv *env, jclass compCls, jobject arg);

void (*JVM_EnableCompiler)(JNIEnv *env, jclass compCls);

void (*JVM_DisableCompiler)(JNIEnv *env, jclass compCls);

void (*JVM_StartThread)(JNIEnv *env, jobject thread);

void (*JVM_StopThread)(JNIEnv *env, jobject thread, jobject exception);

jboolean (*JVM_IsThreadAlive)(JNIEnv *env, jobject thread);

void (*JVM_SuspendThread)(JNIEnv *env, jobject thread);

void (*JVM_ResumeThread)(JNIEnv *env, jobject thread);

void (*JVM_SetThreadPriority)(JNIEnv *env, jobject thread, jint prio);

void (*JVM_Yield)(JNIEnv *env, jclass threadClass);

void (*JVM_Sleep)(JNIEnv *env, jclass threadClass, jlong millis);

jobject (*JVM_CurrentThread)(JNIEnv *env, jclass threadClass);

jint (*JVM_CountStackFrames)(JNIEnv *env, jobject thread);

void (*JVM_Interrupt)(JNIEnv *env, jobject thread);

jboolean (*JVM_IsInterrupted)(JNIEnv *env, jobject thread, jboolean clearInterrupted);

jboolean (*JVM_HoldsLock)(JNIEnv *env, jclass threadClass, jobject obj);

void (*JVM_DumpAllStacks)(JNIEnv *env, jclass unused);

jobjectArray (*JVM_GetAllThreads)(JNIEnv *env, jclass dummy);

void (*JVM_SetNativeThreadName)(JNIEnv *env, jobject jthread, jstring name);

jobjectArray (*JVM_DumpThreads)(JNIEnv *env, jclass threadClass, jobjectArray threads);

jclass (*JVM_CurrentLoadedClass)(JNIEnv *env);

jobject (*JVM_CurrentClassLoader)(JNIEnv *env);

jobjectArray (*JVM_GetClassContext)(JNIEnv *env);

jint (*JVM_ClassDepth)(JNIEnv *env, jstring name);

jint (*JVM_ClassLoaderDepth)(JNIEnv *env);

jstring (*JVM_GetSystemPackage)(JNIEnv *env, jstring name);

jobjectArray (*JVM_GetSystemPackages)(JNIEnv *env);

jobject (*JVM_AllocateNewObject)(JNIEnv *env, jobject obj, jclass currClass,
                    jclass initClass);
jobject (*JVM_AllocateNewArray)(JNIEnv *env, jobject obj, jclass currClass,
                    jint length);
jobject (*JVM_LatestUserDefinedLoader)(JNIEnv *env);

jclass (*JVM_LoadClass0)(JNIEnv *env, jobject obj, jclass currClass,
            jstring currClassName);
jint (*JVM_GetArrayLength)(JNIEnv *env, jobject arr);

jobject (*JVM_GetArrayElement)(JNIEnv *env, jobject arr, jint index);

jvalue (*JVM_GetPrimitiveArrayElement)(JNIEnv *env, jobject arr, jint index, jint wCode);

void (*JVM_SetArrayElement)(JNIEnv *env, jobject arr, jint index, jobject val);

void (*JVM_SetPrimitiveArrayElement)(JNIEnv *env, jobject arr, jint index, jvalue v,
                            unsigned char vCode);
jobject (*JVM_NewArray)(JNIEnv *env, jclass eltClass, jint length);

jobject (*JVM_NewMultiArray)(JNIEnv *env, jclass eltClass, jintArray dim);

jclass (*JVM_GetCallerClass)(JNIEnv *env, int depth);

jclass (*JVM_FindPrimitiveClass)(JNIEnv *env, const char *utf);

void (*JVM_ResolveClass)(JNIEnv *env, jclass cls);

jclass (*JVM_FindClassFromBootLoader)(JNIEnv *env, const char *name);

jclass (*JVM_FindClassFromCaller)(JNIEnv *env, const char *name, jboolean init,
                        jobject loader, jclass caller);
jclass (*JVM_FindClassFromClassLoader)(JNIEnv *env, const char *name, jboolean init,
                            jobject loader, jboolean throwError);
jclass (*JVM_FindClassFromClass)(JNIEnv *env, const char *name, jboolean init,
                            jclass from);
jclass (*JVM_FindLoadedClass)(JNIEnv *env, jobject loader, jstring name);

jclass (*JVM_DefineClass)(JNIEnv *env, const char *name, jobject loader, const jbyte *buf,
                jsize len, jobject pd);

jclass (*JVM_DefineClassWithSource)(JNIEnv *env, const char *name, jobject loader,
                        const jbyte *buf, jsize len, jobject pd,
                        const char *source);

jstring (*JVM_GetClassName)(JNIEnv *env, jclass cls);

jobjectArray (*JVM_GetClassInterfaces)(JNIEnv *env, jclass cls);

jboolean (*JVM_IsInterface)(JNIEnv *env, jclass cls);

jobjectArray (*JVM_GetClassSigners)(JNIEnv *env, jclass cls);

void (*JVM_SetClassSigners)(JNIEnv *env, jclass cls, jobjectArray signers);

jobject (*JVM_GetProtectionDomain)(JNIEnv *env, jclass cls);

jboolean (*JVM_IsArrayClass)(JNIEnv *env, jclass cls);

jboolean (*JVM_IsPrimitiveClass)(JNIEnv *env, jclass cls);

jclass (*JVM_GetComponentType)(JNIEnv *env, jclass cls);

jint (*JVM_GetClassModifiers)(JNIEnv *env, jclass cls);

jobjectArray (*JVM_GetDeclaredClasses)(JNIEnv *env, jclass ofClass);

jclass (*JVM_GetDeclaringClass)(JNIEnv *env, jclass ofClass);

jstring (*JVM_GetClassSignature)(JNIEnv *env, jclass cls);

jbyteArray (*JVM_GetClassAnnotations)(JNIEnv *env, jclass cls);

jbyteArray (*JVM_GetClassTypeAnnotations)(JNIEnv *env, jclass cls);

jbyteArray (*JVM_GetFieldTypeAnnotations)(JNIEnv *env, jobject field);

jbyteArray (*JVM_GetMethodTypeAnnotations)(JNIEnv *env, jobject method);

jobjectArray (*JVM_GetClassDeclaredMethods)(JNIEnv *env, jclass ofClass, jboolean publicOnly);

jobjectArray (*JVM_GetClassDeclaredFields)(JNIEnv *env, jclass ofClass, jboolean publicOnly);

jobjectArray (*JVM_GetClassDeclaredConstructors)(JNIEnv *env, jclass ofClass, jboolean publicOnly);

jint (*JVM_GetClassAccessFlags)(JNIEnv *env, jclass cls);

jobject (*JVM_InvokeMethod)(JNIEnv *env, jobject method, jobject obj, jobjectArray args0);

jobject (*JVM_NewInstanceFromConstructor)(JNIEnv *env, jobject c, jobjectArray args0);

jobject (*JVM_GetClassConstantPool)(JNIEnv *env, jclass cls);

jint (*JVM_ConstantPoolGetSize)(JNIEnv *env, jobject unused, jobject jcpool);

jclass (*JVM_ConstantPoolGetClassAt)(JNIEnv *env, jobject unused, jobject jcpool, jint index);

jclass (*JVM_ConstantPoolGetClassAtIfLoaded)(JNIEnv *env, jobject unused, jobject jcpool, jint index);

jobject (*JVM_ConstantPoolGetMethodAt)(JNIEnv *env, jobject unused, jobject jcpool, jint index);

jobject (*JVM_ConstantPoolGetMethodAtIfLoaded)(JNIEnv *env, jobject unused, jobject jcpool, jint index);

jobject (*JVM_ConstantPoolGetFieldAt)(JNIEnv *env, jobject unused, jobject jcpool, jint index);

jobject (*JVM_ConstantPoolGetFieldAtIfLoaded)(JNIEnv *env, jobject unused, jobject jcpool, jint index);

jobjectArray (*JVM_ConstantPoolGetMemberRefInfoAt)(JNIEnv *env, jobject unused, jobject jcpool, jint index);

jint (*JVM_ConstantPoolGetIntAt)(JNIEnv *env, jobject unused, jobject jcpool, jint index);

jlong (*JVM_ConstantPoolGetLongAt)(JNIEnv *env, jobject unused, jobject jcpool, jint index);

jfloat (*JVM_ConstantPoolGetFloatAt)(JNIEnv *env, jobject unused, jobject jcpool, jint index);

jdouble (*JVM_ConstantPoolGetDoubleAt)(JNIEnv *env, jobject unused, jobject jcpool, jint index);

jstring (*JVM_ConstantPoolGetStringAt)(JNIEnv *env, jobject unused, jobject jcpool, jint index);

jstring (*JVM_ConstantPoolGetUTF8At)(JNIEnv *env, jobject unused, jobject jcpool, jint index);

jobjectArray (*JVM_GetMethodParameters)(JNIEnv *env, jobject method);

jobject (*JVM_DoPrivileged)(JNIEnv *env, jclass cls,
                jobject action, jobject context, jboolean wrapException);
jobject (*JVM_GetInheritedAccessControlContext)(JNIEnv *env, jclass cls);

jobject (*JVM_GetStackAccessControlContext)(JNIEnv *env, jclass cls);

void * (*JVM_RegisterSignal)(jint sig, void *handler);

jboolean (*JVM_RaiseSignal)(jint sig);

jint (*JVM_FindSignal)(const char *name);

jboolean (*JVM_DesiredAssertionStatus)(JNIEnv *env, jclass unused, jclass cls);

jobject (*JVM_AssertionStatusDirectives)(JNIEnv *env, jclass unused);

jboolean (*JVM_SupportsCX8)(void);

jint (*JVM_DTraceGetVersion)(JNIEnv* env);

jlong (*JVM_DTraceActivate)(JNIEnv* env, jint version, jstring module_name,
jint providers_count, JVM_DTraceProvider* providers);
jboolean (*JVM_DTraceIsProbeEnabled)(JNIEnv* env, jmethodID method);

void (*JVM_DTraceDispose)(JNIEnv* env, jlong activation_handle);

jboolean (*JVM_DTraceIsSupported)(JNIEnv* env);

const char * (*JVM_GetClassNameUTF)(JNIEnv *env, jclass cb);

void (*JVM_GetClassCPTypes)(JNIEnv *env, jclass cb, unsigned char *types);

jint (*JVM_GetClassCPEntriesCount)(JNIEnv *env, jclass cb);

jint (*JVM_GetClassFieldsCount)(JNIEnv *env, jclass cb);

jint (*JVM_GetClassMethodsCount)(JNIEnv *env, jclass cb);

void (*JVM_GetMethodIxExceptionIndexes)(JNIEnv *env, jclass cb, jint method_index,
                                unsigned short *exceptions);
jint (*JVM_GetMethodIxExceptionsCount)(JNIEnv *env, jclass cb, jint method_index);

void (*JVM_GetMethodIxByteCode)(JNIEnv *env, jclass cb, jint method_index,
                        unsigned char *code);
jint (*JVM_GetMethodIxByteCodeLength)(JNIEnv *env, jclass cb, jint method_index);

void (*JVM_GetMethodIxExceptionTableEntry)(JNIEnv *env, jclass cb, jint method_index,
                                jint entry_index,
                                JVM_ExceptionTableEntryType *entry);

jint (*JVM_GetMethodIxExceptionTableLength)(JNIEnv *env, jclass cb, int index);

jint (*JVM_GetFieldIxModifiers)(JNIEnv *env, jclass cb, int index);

jint (*JVM_GetMethodIxModifiers)(JNIEnv *env, jclass cb, int index);

jint (*JVM_GetMethodIxLocalsCount)(JNIEnv *env, jclass cb, int index);

jint (*JVM_GetMethodIxArgsSize)(JNIEnv *env, jclass cb, int index);

jint (*JVM_GetMethodIxMaxStack)(JNIEnv *env, jclass cb, int index);

jboolean (*JVM_IsConstructorIx)(JNIEnv *env, jclass cb, int index);

jboolean (*JVM_IsVMGeneratedMethodIx)(JNIEnv *env, jclass cb, int index);

const char * (*JVM_GetMethodIxNameUTF)(JNIEnv *env, jclass cb, jint index);

const char * (*JVM_GetMethodIxSignatureUTF)(JNIEnv *env, jclass cb, jint index);

const char * (*JVM_GetCPFieldNameUTF)(JNIEnv *env, jclass cb, jint index);

const char * (*JVM_GetCPMethodNameUTF)(JNIEnv *env, jclass cb, jint index);

const char * (*JVM_GetCPMethodSignatureUTF)(JNIEnv *env, jclass cb, jint index);

const char * (*JVM_GetCPFieldSignatureUTF)(JNIEnv *env, jclass cb, jint index);

const char * (*JVM_GetCPClassNameUTF)(JNIEnv *env, jclass cb, jint index);

const char * (*JVM_GetCPFieldClassNameUTF)(JNIEnv *env, jclass cb, jint index);

const char * (*JVM_GetCPMethodClassNameUTF)(JNIEnv *env, jclass cb, jint index);

jint (*JVM_GetCPFieldModifiers)(JNIEnv *env, jclass cb, int index, jclass calledClass);

jint (*JVM_GetCPMethodModifiers)(JNIEnv *env, jclass cb, int index, jclass calledClass);

void (*JVM_ReleaseUTF)(const char *utf);

jboolean (*JVM_IsSameClassPackage)(JNIEnv *env, jclass class1, jclass class2);

jint (*JVM_GetLastErrorString)(char *buf, int len);

char * (*JVM_NativePath)(char *);

jint (*JVM_Open)(const char *fname, jint flags, jint mode);

jint (*JVM_Close)(jint fd);

jint (*JVM_Read)(jint fd, char *buf, jint nbytes);

jint (*JVM_Write)(jint fd, char *buf, jint nbytes);

jint (*JVM_Available)(jint fd, jlong *pbytes);

jlong (*JVM_Lseek)(jint fd, jlong offset, jint whence);

jint (*JVM_SetLength)(jint fd, jlong length);

jint (*JVM_Sync)(jint fd);

jint (*JVM_InitializeSocketLibrary)(void);

jint (*JVM_Socket)(jint domain, jint type, jint protocol);

jint (*JVM_SocketClose)(jint fd);

jint (*JVM_SocketShutdown)(jint fd, jint howto);

jint (*JVM_Recv)(jint fd, char *buf, jint nBytes, jint flags);

jint (*JVM_Send)(jint fd, char *buf, jint nBytes, jint flags);

jint (*JVM_Timeout)(int fd, long timeout);

jint (*JVM_Listen)(jint fd, jint count);

jint (*JVM_Connect)(jint fd, struct sockaddr *him, jint len);

jint (*JVM_Bind)(jint fd, struct sockaddr *him, jint len);

jint (*JVM_Accept)(jint fd, struct sockaddr *him, jint *len);

jint (*JVM_RecvFrom)(jint fd, char *buf, int nBytes,
                int flags, struct sockaddr *from, int *fromlen);
jint (*JVM_SendTo)(jint fd, char *buf, int len,
                int flags, struct sockaddr *to, int tolen);
jint (*JVM_SocketAvailable)(jint fd, jint *result);

jint (*JVM_GetSockName)(jint fd, struct sockaddr *him, int *len);

jint (*JVM_GetSockOpt)(jint fd, int level, int optname, char *optval, int *optlen);

jint (*JVM_SetSockOpt)(jint fd, int level, int optname, const char *optval, int optlen);

int (*JVM_GetHostName)(char* name, int namelen);

void * (*JVM_RawMonitorCreate)(void);

void (*JVM_RawMonitorDestroy)(void *mon);

jint (*JVM_RawMonitorEnter)(void *mon);

void (*JVM_RawMonitorExit)(void *mon);

void* (*JVM_GetManagement)(jint version);

jobject (*JVM_InitAgentProperties)(JNIEnv *env, jobject agent_props);

jstring (*JVM_GetTemporaryDirectory)(JNIEnv *env);

jobjectArray (*JVM_GetEnclosingMethodInfo)(JNIEnv* env, jclass ofClass);

jintArray (*JVM_GetThreadStateValues)(JNIEnv* env, jint javaThreadState);

jobjectArray (*JVM_GetThreadStateNames)(JNIEnv* env, jint javaThreadState, jintArray values);

jboolean (*JVM_KnownToNotExist)(JNIEnv *env, jobject loader, const char *classname);

jobjectArray (*JVM_GetResourceLookupCacheURLs)(JNIEnv *env, jobject loader);

jintArray (*JVM_GetResourceLookupCache)(JNIEnv *env, jobject loader, const char *resource_name);

void (*JVM_GetVersionInfo)(JNIEnv* env, jvm_version_info* info, size_t info_size);

void (*JVM_CopySwapMemory)(JNIEnv *env, jobject srcObj, jlong srcOffset,
									 jobject dstObj, jlong dstOffset, 
									 jlong size,     jlong elemSize);

void (*Mokapot_SetJNIEnv)(JNIEnv* env);

jboolean (*JVM_CX8Field)(JNIEnv *env, jobject obj, jfieldID fid, jlong oldVal, jlong newVal);

jclass (*JVM_DefineClassWithSourceCond)(JNIEnv *env, const char *name,
                                                  jobject loader, const jbyte *buf,
                                                  jsize len, jobject pd,
                                                  const char *source, jboolean verify);

jobject (*JVM_GetClassLoader)(JNIEnv *env, jclass cls);

jbyteArray (*JVM_GetFieldAnnotations)(JNIEnv *env, jobject field);

jbyteArray (*JVM_GetMethodAnnotations)(JNIEnv *env, jobject method);

jbyteArray (*JVM_GetMethodDefaultAnnotationValue)(JNIEnv *env, jobject method);

jbyteArray (*JVM_GetMethodParameterAnnotations)(JNIEnv *env, jobject method);

// Invocation API
jint (*JNI_GetCreatedJavaVMs)(JavaVM **vm_buf, jsize buf_len, jsize *numVMs);

// Java 11 VM methods
void (*JVM_AddModuleExports)(JNIEnv *env, jobject from_module, const char* package, jobject to_module);

void (*JVM_AddModuleExportsToAllUnnamed)(JNIEnv *env, jobject from_module, const char* package);

void (*JVM_AddModuleExportsToAll)(JNIEnv *env, jobject from_module, const char* package);

void (*JVM_AddReadsModule)(JNIEnv *env, jobject from_module, jobject source_module);

jboolean (*JVM_AreNestMates)(JNIEnv *env, jclass current, jclass member);

void (*JVM_BeforeHalt)();

jobject (*JVM_CallStackWalk)(JNIEnv *env, jobject stackStream, jlong mode,
                      jint skip_frames, jint frame_count, jint start_index,
                      jobjectArray frames);

jint (*JVM_ConstantPoolGetClassRefIndexAt)
            (JNIEnv *env, jobject obj, jobject unused, jint index);

jint (*JVM_ConstantPoolGetNameAndTypeRefIndexAt)
            (JNIEnv *env, jobject obj, jobject unused, jint index);

jobjectArray (*JVM_ConstantPoolGetNameAndTypeRefInfoAt)
            (JNIEnv *env, jobject obj, jobject unused, jint index);

jbyte (*JVM_ConstantPoolGetTagAt)
            (JNIEnv *env, jobject unused, jobject jcpool, jint index);

void (*JVM_DefineModule)(JNIEnv *env, jobject module, jboolean is_open, jstring version,
                     jstring location, const char* const* packages, jsize num_packages);

jobject (*JVM_GetAndClearReferencePendingList)(JNIEnv *env);

jlong (*JVM_GetNanoTimeAdjustment)(JNIEnv *env, jclass ignored, jlong offset_secs);

jclass (*JVM_GetNestHost)(JNIEnv *env, jclass current);

jobjectArray (*JVM_GetNestMembers)(JNIEnv *env, jclass current);

jstring (*JVM_GetSimpleBinaryName)(JNIEnv *env, jclass ofClass);

jobjectArray (*JVM_GetVmArguments)(JNIEnv *env);

jboolean (*JVM_HasReferencePendingList)(JNIEnv *env);

jstring (*JVM_InitClassName)(JNIEnv *env, jclass cls);

void (*JVM_InitializeFromArchive)(JNIEnv* env, jclass cls);

void (*JVM_InitStackTraceElement)(JNIEnv* env, jobject element, jobject stackFrameInfo);

void (*JVM_InitStackTraceElementArray)(JNIEnv *env, jobjectArray elements, jobject throwable);

jint (*JVM_MoreStackWalk)(JNIEnv *env, jobject stackStream, jlong mode, jlong anchor,
                      jint frame_count, jint start_index,
                      jobjectArray frames);

void (*JVM_SetBootLoaderUnnamedModule)(JNIEnv *env, jobject module);

void (*JVM_WaitForReferencePendingList)(JNIEnv *env);

jboolean (*JVM_IsUseContainerSupport)(void);

void (*JVM_DefineArchivedModules)(JNIEnv *env, jobject platform_loader, jobject system_loader);

jstring (*JVM_GetExtendedNPEMessage)(JNIEnv *env, jthrowable throwable);

jobjectArray (*JVM_GetPermittedSubclasses)(JNIEnv *env, jclass current);

jobjectArray (*JVM_GetProperties)(JNIEnv *env);

jobjectArray (*JVM_GetRecordComponents)(JNIEnv *env, jclass ofClass);

void (*JVM_RegisterLambdaProxyClassForArchiving)(JNIEnv* env, jclass caller,
                                         jstring invokedName,
                                         jobject invokedType,
                                         jobject methodType,
                                         jobject implMethodMember,
                                         jobject instantiatedMethodType,
                                         jclass lambdaProxyClass);

jclass (*JVM_LookupLambdaProxyClassFromArchive)(JNIEnv* env, jclass caller,
                                      jstring invokedName,
                                      jobject invokedType,
                                      jobject methodType,
                                      jobject implMethodMember,
                                      jobject instantiatedMethodType);

jboolean (*JVM_IsCDSDumpingEnabled)(JNIEnv* env);

jboolean (*JVM_IsSharingEnabled)(JNIEnv* env);

jboolean (*JVM_IsDumpingClassList)(JNIEnv* env);

jlong (*JVM_GetRandomSeedForDumping)();

void (*JVM_LogLambdaFormInvoker)(JNIEnv* env, jstring line);

jboolean (*JVM_IsHiddenClass)(JNIEnv *env, jclass cls);

jboolean (*JVM_IsRecord)(JNIEnv *env, jclass cls);

jclass (*JVM_LookupDefineClass)(JNIEnv *env, jclass lookup, const char *name, const jbyte *buf,
                      jsize len, jobject pd, jboolean init, int flags, jobject classData);

jboolean (*JVM_PhantomReferenceRefersTo)(JNIEnv *env, jobject ref, jobject o);

jboolean (*JVM_ReferenceRefersTo)(JNIEnv *env, jobject ref, jobject o);

void (*JVM_ReferenceClear)(JNIEnv *env, jobject ref);


};

struct MokapotEnv_ {
    const struct MokapotNativeInterface_ *functions;
    // Add C++ methods
    #ifdef __cplusplus

    #endif
};

// An always-growing, lock-free list of JavaVM*
typedef struct VMList {
    struct VMList* OS_ATOMIC next;
    uint32_t capacity;
    JavaVM* OS_ATOMIC vms[];
} VMList;

extern VMList* OS_ATOMIC vm_list_head;

void add_java_vm(JavaVM* vm);
jint remove_java_vm(JavaVM* vm);
void gather_java_vms(JavaVM** buf, jsize buf_size, jsize* numVms);

#define LIB_JAVAVM_PLAIN 0
#define LIB_JAVAVM_POLYGLOT 1

typedef struct LibJavaVM {
    graal_create_isolate_fn_t create_isolate;
    graal_attach_thread_fn_t attach_thread;
    graal_detach_thread_fn_t detach_thread;
    graal_get_current_thread_fn_t get_current_thread;
    graal_tear_down_isolate_fn_t tear_down_isolate;
    graal_detach_all_threads_and_tear_down_isolate_fn_t detach_all_threads_and_tear_down_isolate;
    Espresso_CreateJavaVM_fn_t Espresso_CreateJavaVM;       // create + enter
    Espresso_EnterContext_fn_t Espresso_EnterContext;       // enter
    Espresso_LeaveContext_fn_t Espresso_LeaveContext;       // leave
    Espresso_ReleaseContext_fn_t Espresso_ReleaseContext;   // release
    Espresso_CloseContext_fn_t Espresso_CloseContext;       // release + leave + close
    Espresso_Shutdown_fn_t Espresso_Shutdown;               // shutdown
} LibJavaVM;

typedef struct LibJavaVMIsolate {
    LibJavaVM *lib;
    graal_isolate_t *isolate;
    jboolean is_sun_standard_launcher; // -Dsun.java.launcher=SUN_STANDARD
} LibJavaVMIsolate;

#endif // _MOKAPOT_H
