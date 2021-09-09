/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
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
#ifndef _JAVASOFT_JVM_H_
#define _JAVASOFT_JVM_H_

#include <jni.h>

#if defined(_WIN32)
# include "jvm_windows.h"
#elif defined(__linux__) || defined(__APPLE__)
# include "jvm_posix.h"
#else
#error unknown platform
#endif

#ifdef __cplusplus
extern "C" {
#endif

/*
 * This file contains additional functions exported from the VM.
 * These functions are complementary to the standard JNI support.
 * There are three parts to this file:
 *
 * First, this file contains the VM-related functions needed by native
 * libraries in the standard Java API. For example, the java.lang.Object
 * class needs VM-level functions that wait for and notify monitors.
 *
 * Second, this file contains the functions and constant definitions
 * needed by the byte code verifier and class file format checker.
 * These functions allow the verifier and format checker to be written
 * in a VM-independent way.
 *
 * Third, this file contains various I/O and nerwork operations needed
 * by the standard Java I/O and network APIs.
 */

/*
 * Bump the version number when either of the following happens:
 *
 * 1. There is a change in JVM_* functions.
 *
 * 2. There is a change in the contract between VM and Java classes.
 *    For example, if the VM relies on a new private field in Thread
 *    class.
 */

#define JVM_INTERFACE_VERSION 6

JNIEXPORT jint JNICALL
JVM_GetInterfaceVersion(void);

/*************************************************************************
 PART 1: Functions for Native Libraries
 ************************************************************************/
/*
 * java.lang.Object
 */
JNIEXPORT jint JNICALL
JVM_IHashCode(JNIEnv *env, jobject obj);

JNIEXPORT void JNICALL
JVM_MonitorWait(JNIEnv *env, jobject obj, jlong ms);

JNIEXPORT void JNICALL
JVM_MonitorNotify(JNIEnv *env, jobject obj);

JNIEXPORT void JNICALL
JVM_MonitorNotifyAll(JNIEnv *env, jobject obj);

JNIEXPORT jobject JNICALL
JVM_Clone(JNIEnv *env, jobject obj);

/*
 * java.lang.String
 */
JNIEXPORT jstring JNICALL
JVM_InternString(JNIEnv *env, jstring str);

/*
 * java.lang.System
 */
JNIEXPORT jlong JNICALL
JVM_CurrentTimeMillis(JNIEnv *env, jclass ignored);

JNIEXPORT jlong JNICALL
JVM_NanoTime(JNIEnv *env, jclass ignored);

JNIEXPORT jlong JNICALL
JVM_GetNanoTimeAdjustment(JNIEnv *env, jclass ignored, jlong offset_secs);

JNIEXPORT void JNICALL
JVM_ArrayCopy(JNIEnv *env, jclass ignored, jobject src, jint src_pos,
              jobject dst, jint dst_pos, jint length);

JNIEXPORT jobject JNICALL
JVM_InitProperties(JNIEnv *env, jobject p);

/*
 * Return an array of all properties as alternating name and value pairs.
 */
JNIEXPORT jobjectArray JNICALL
JVM_GetProperties(JNIEnv *env);

/*
 * java.io.File
 */
JNIEXPORT void JNICALL
JVM_OnExit(void (*func)(void));

/*
 * java.nio.Bits
 */
JNIEXPORT void JNICALL
JVM_CopySwapMemory(JNIEnv *env, jobject srcObj, jlong srcOffset,
                   jobject dstObj, jlong dstOffset, jlong size,
                   jlong elemSize);

/*
 * java.lang.Runtime
 */
JNIEXPORT void JNICALL
JVM_Exit(jint code);

JNIEXPORT void JNICALL
JVM_BeforeHalt();

JNIEXPORT void JNICALL
JVM_Halt(jint code);

JNIEXPORT void JNICALL
JVM_GC(void);

/* Returns the number of real-time milliseconds that have elapsed since the
 * least-recently-inspected heap object was last inspected by the garbage
 * collector.
 *
 * For simple stop-the-world collectors this value is just the time
 * since the most recent collection.  For generational collectors it is the
 * time since the oldest generation was most recently collected.  Other
 * collectors are free to return a pessimistic estimate of the elapsed time, or
 * simply the time since the last full collection was performed.
 *
 * Note that in the presence of reference objects, a given object that is no
 * longer strongly reachable may have to be inspected multiple times before it
 * can be reclaimed.
 */
JNIEXPORT jlong JNICALL
JVM_MaxObjectInspectionAge(void);

JNIEXPORT void JNICALL
JVM_TraceInstructions(jboolean on);

JNIEXPORT void JNICALL
JVM_TraceMethodCalls(jboolean on);

JNIEXPORT jlong JNICALL
JVM_TotalMemory(void);

JNIEXPORT jlong JNICALL
JVM_FreeMemory(void);

JNIEXPORT jlong JNICALL
JVM_MaxMemory(void);

JNIEXPORT jint JNICALL
JVM_ActiveProcessorCount(void);

JNIEXPORT jboolean JNICALL
JVM_IsUseContainerSupport(void);

JNIEXPORT void * JNICALL
JVM_LoadLibrary(const char *name);

JNIEXPORT void JNICALL
JVM_UnloadLibrary(void * handle);

JNIEXPORT void * JNICALL
JVM_FindLibraryEntry(void *handle, const char *name);

JNIEXPORT jboolean JNICALL
JVM_IsSupportedJNIVersion(jint version);

JNIEXPORT jobjectArray JNICALL
JVM_GetVmArguments(JNIEnv *env);

JNIEXPORT void JNICALL
JVM_InitializeFromArchive(JNIEnv* env, jclass cls);

/* jdk.internal.misc.CDS */

JNIEXPORT void JNICALL
JVM_RegisterLambdaProxyClassForArchiving(JNIEnv* env, jclass caller,
                                         jstring invokedName,
                                         jobject invokedType,
                                         jobject methodType,
                                         jobject implMethodMember,
                                         jobject instantiatedMethodType,
                                         jclass lambdaProxyClass);

JNIEXPORT jclass JNICALL
JVM_LookupLambdaProxyClassFromArchive(JNIEnv* env, jclass caller,
                                      jstring invokedName,
                                      jobject invokedType,
                                      jobject methodType,
                                      jobject implMethodMember,
                                      jobject instantiatedMethodType);

JNIEXPORT jboolean JNICALL
JVM_IsCDSDumpingEnabled(JNIEnv* env);

JNIEXPORT jboolean JNICALL
JVM_IsSharingEnabled(JNIEnv* env);

JNIEXPORT jboolean JNICALL
JVM_IsDumpingClassList(JNIEnv* env);

JNIEXPORT jlong JNICALL
JVM_GetRandomSeedForDumping();

JNIEXPORT void JNICALL
JVM_LogLambdaFormInvoker(JNIEnv* env, jstring line);

/*
 * java.lang.Float and java.lang.Double
 */
JNIEXPORT jboolean JNICALL
JVM_IsNaN(jdouble d);

/*
 * java.lang.Throwable
 */
JNIEXPORT void JNICALL
JVM_FillInStackTrace(JNIEnv *env, jobject throwable);

JNIEXPORT jint JNICALL
JVM_GetStackTraceDepth(JNIEnv *env, jobject throwable);

JNIEXPORT jobject JNICALL
JVM_GetStackTraceElement(JNIEnv *env, jobject throwable, jint index);

/*
 * java.lang.StackTraceElement
 */
JNIEXPORT void JNICALL
JVM_InitStackTraceElementArray(JNIEnv *env, jobjectArray elements, jobject throwable);

JNIEXPORT void JNICALL
JVM_InitStackTraceElement(JNIEnv* env, jobject element, jobject stackFrameInfo);

/*
 * java.lang.NullPointerException
 */

JNIEXPORT jstring JNICALL
JVM_GetExtendedNPEMessage(JNIEnv *env, jthrowable throwable);

/*
 * java.lang.Compiler
 */
JNIEXPORT void JNICALL
JVM_InitializeCompiler (JNIEnv *env, jclass compCls);

JNIEXPORT jboolean JNICALL
JVM_IsSilentCompiler(JNIEnv *env, jclass compCls);

JNIEXPORT jboolean JNICALL
JVM_CompileClass(JNIEnv *env, jclass compCls, jclass cls);

JNIEXPORT jboolean JNICALL
JVM_CompileClasses(JNIEnv *env, jclass cls, jstring jname);

JNIEXPORT jobject JNICALL
JVM_CompilerCommand(JNIEnv *env, jclass compCls, jobject arg);

JNIEXPORT void JNICALL
JVM_EnableCompiler(JNIEnv *env, jclass compCls);

JNIEXPORT void JNICALL
JVM_DisableCompiler(JNIEnv *env, jclass compCls);

/*
 * java.lang.StackWalker
 */
enum {
  JVM_STACKWALK_FILL_CLASS_REFS_ONLY       = 0x2,
  JVM_STACKWALK_GET_CALLER_CLASS           = 0x04,
  JVM_STACKWALK_SHOW_HIDDEN_FRAMES         = 0x20,
  JVM_STACKWALK_FILL_LIVE_STACK_FRAMES     = 0x100
};

JNIEXPORT jobject JNICALL
JVM_CallStackWalk(JNIEnv *env, jobject stackStream, jlong mode,
                  jint skip_frames, jint frame_count, jint start_index,
                  jobjectArray frames);

JNIEXPORT jint JNICALL
JVM_MoreStackWalk(JNIEnv *env, jobject stackStream, jlong mode, jlong anchor,
                  jint frame_count, jint start_index,
                  jobjectArray frames);

/*
 * java.lang.Thread
 */
JNIEXPORT void JNICALL
JVM_StartThread(JNIEnv *env, jobject thread);

JNIEXPORT void JNICALL
JVM_StopThread(JNIEnv *env, jobject thread, jobject exception);

JNIEXPORT jboolean JNICALL
JVM_IsThreadAlive(JNIEnv *env, jobject thread);

JNIEXPORT void JNICALL
JVM_SuspendThread(JNIEnv *env, jobject thread);

JNIEXPORT void JNICALL
JVM_ResumeThread(JNIEnv *env, jobject thread);

JNIEXPORT void JNICALL
JVM_SetThreadPriority(JNIEnv *env, jobject thread, jint prio);

JNIEXPORT void JNICALL
JVM_Yield(JNIEnv *env, jclass threadClass);

JNIEXPORT void JNICALL
JVM_Sleep(JNIEnv *env, jclass threadClass, jlong millis);

JNIEXPORT jobject JNICALL
JVM_CurrentThread(JNIEnv *env, jclass threadClass);

JNIEXPORT jint JNICALL
JVM_CountStackFrames(JNIEnv *env, jobject thread);

JNIEXPORT void JNICALL
JVM_Interrupt(JNIEnv *env, jobject thread);

JNIEXPORT jboolean JNICALL
JVM_IsInterrupted(JNIEnv *env, jobject thread, jboolean clearInterrupted);

JNIEXPORT jboolean JNICALL
JVM_HoldsLock(JNIEnv *env, jclass threadClass, jobject obj);

JNIEXPORT void JNICALL
JVM_DumpAllStacks(JNIEnv *env, jclass unused);

JNIEXPORT jobjectArray JNICALL
JVM_GetAllThreads(JNIEnv *env, jclass dummy);

JNIEXPORT void JNICALL
JVM_SetNativeThreadName(JNIEnv *env, jobject jthread, jstring name);

/* getStackTrace() and getAllStackTraces() method */
JNIEXPORT jobjectArray JNICALL
JVM_DumpThreads(JNIEnv *env, jclass threadClass, jobjectArray threads);

/*
 * java.lang.SecurityManager
 */
JNIEXPORT jclass JNICALL
JVM_CurrentLoadedClass(JNIEnv *env);

JNIEXPORT jobject JNICALL
JVM_CurrentClassLoader(JNIEnv *env);

JNIEXPORT jobjectArray JNICALL
JVM_GetClassContext(JNIEnv *env);

JNIEXPORT jint JNICALL
JVM_ClassDepth(JNIEnv *env, jstring name);

JNIEXPORT jint JNICALL
JVM_ClassLoaderDepth(JNIEnv *env);

/*
 * java.lang.Package
 */
JNIEXPORT jstring JNICALL
JVM_GetSystemPackage(JNIEnv *env, jstring name);

JNIEXPORT jobjectArray JNICALL
JVM_GetSystemPackages(JNIEnv *env);

/*
 * java.lang.ref.Reference
 */
JNIEXPORT jobject JNICALL
JVM_GetAndClearReferencePendingList(JNIEnv *env);

JNIEXPORT jboolean JNICALL
JVM_HasReferencePendingList(JNIEnv *env);

JNIEXPORT void JNICALL
JVM_WaitForReferencePendingList(JNIEnv *env);

JNIEXPORT jboolean JNICALL
JVM_ReferenceRefersTo(JNIEnv *env, jobject ref, jobject o);

JNIEXPORT void JNICALL
JVM_ReferenceClear(JNIEnv *env, jobject ref);

/*
 * java.lang.ref.PhantomReference
 */
JNIEXPORT jboolean JNICALL
JVM_PhantomReferenceRefersTo(JNIEnv *env, jobject ref, jobject o);

/*
 * java.io.ObjectInputStream
 */
JNIEXPORT jobject JNICALL
JVM_AllocateNewObject(JNIEnv *env, jobject obj, jclass currClass,
                      jclass initClass);

JNIEXPORT jobject JNICALL
JVM_AllocateNewArray(JNIEnv *env, jobject obj, jclass currClass,
                     jint length);

JNIEXPORT jobject JNICALL
JVM_LatestUserDefinedLoader(JNIEnv *env);

/*
 * This function has been deprecated and should not be considered
 * part of the specified JVM interface.
 */
JNIEXPORT jclass JNICALL
JVM_LoadClass0(JNIEnv *env, jobject obj, jclass currClass,
               jstring currClassName);

/*
 * java.lang.reflect.Array
 */
JNIEXPORT jint JNICALL
JVM_GetArrayLength(JNIEnv *env, jobject arr);

JNIEXPORT jobject JNICALL
JVM_GetArrayElement(JNIEnv *env, jobject arr, jint index);

JNIEXPORT jvalue JNICALL
JVM_GetPrimitiveArrayElement(JNIEnv *env, jobject arr, jint index, jint wCode);

JNIEXPORT void JNICALL
JVM_SetArrayElement(JNIEnv *env, jobject arr, jint index, jobject val);

JNIEXPORT void JNICALL
JVM_SetPrimitiveArrayElement(JNIEnv *env, jobject arr, jint index, jvalue v,
                             unsigned char vCode);

JNIEXPORT jobject JNICALL
JVM_NewArray(JNIEnv *env, jclass eltClass, jint length);

JNIEXPORT jobject JNICALL
JVM_NewMultiArray(JNIEnv *env, jclass eltClass, jintArray dim);

/*
 * java.lang.Class and java.lang.ClassLoader
 */

#define JVM_CALLER_DEPTH -1

/*
 * Returns the class in which the code invoking the native method
 * belongs.
 *
 * Note that in JDK 1.1, native methods did not create a frame.
 * In 1.2, they do. Therefore native methods like Class.forName
 * can no longer look at the current frame for the caller class.
 */
JNIEXPORT jclass JNICALL
JVM_GetCallerClass(JNIEnv *env, int n);

/*
 * Find primitive classes
 * utf: class name
 */
JNIEXPORT jclass JNICALL
JVM_FindPrimitiveClass(JNIEnv *env, const char *utf);

/*
 * Link the class
 */
JNIEXPORT void JNICALL
JVM_ResolveClass(JNIEnv *env, jclass cls);

/*
 * Find a class from a given class loader. Throw ClassNotFoundException
 * or NoClassDefFoundError depending on the value of the last
 * argument.
 */
JNIEXPORT jclass JNICALL
JVM_FindClassFromClassLoader(JNIEnv *env, const char *name, jboolean init,
                             jobject loader, jboolean throwError);

/*
 * Find a class from a boot class loader. Returns NULL if class not found.
 */
JNIEXPORT jclass JNICALL
JVM_FindClassFromBootLoader(JNIEnv *env, const char *name);

/*
 * Find a class from a given class loader.  Throws ClassNotFoundException.
 *  name:   name of class
 *  init:   whether initialization is done
 *  loader: class loader to look up the class. This may not be the same as the caller's
 *          class loader.
 *  caller: initiating class. The initiating class may be null when a security
 *          manager is not installed.
 */
JNIEXPORT jclass JNICALL
JVM_FindClassFromCaller(JNIEnv *env, const char *name, jboolean init,
                        jobject loader, jclass caller);

/*
 * Find a class from a given class.
 */
JNIEXPORT jclass JNICALL
JVM_FindClassFromClass(JNIEnv *env, const char *name, jboolean init,
                             jclass from);

/* Find a loaded class cached by the VM */
JNIEXPORT jclass JNICALL
JVM_FindLoadedClass(JNIEnv *env, jobject loader, jstring name);

/* Define a class */
JNIEXPORT jclass JNICALL
JVM_DefineClass(JNIEnv *env, const char *name, jobject loader, const jbyte *buf,
                jsize len, jobject pd);

/* Define a class with a source (added in JDK1.5) */
JNIEXPORT jclass JNICALL
JVM_DefineClassWithSource(JNIEnv *env, const char *name, jobject loader,
                          const jbyte *buf, jsize len, jobject pd,
                          const char *source);

/*
 * Define a class with the specified lookup class.
 *  lookup:  Lookup class
 *  name:    the name of the class
 *  buf:     class bytes
 *  len:     length of class bytes
 *  pd:      protection domain
 *  init:    initialize the class
 *  flags:   properties of the class
 *  classData: private static pre-initialized field; may be null
 */
JNIEXPORT jclass JNICALL
JVM_LookupDefineClass(JNIEnv *env, jclass lookup, const char *name, const jbyte *buf,
                      jsize len, jobject pd, jboolean init, int flags, jobject classData);

/* Define a class with a source with conditional verification (added HSX 14)
 * -Xverify:all will verify anyway, -Xverify:none will not verify,
 * -Xverify:remote (default) will obey this conditional
 * i.e. true = should_verify_class
 */
JNIEXPORT jclass JNICALL
JVM_DefineClassWithSourceCond(JNIEnv *env, const char *name,
                              jobject loader, const jbyte *buf,
                              jsize len, jobject pd, const char *source,
                              jboolean verify);

/*
 * Module support funcions
 */

/*
 * Define a module with the specified packages and bind the module to the
 * given class loader.
 *  module:       module to define
 *  is_open:      specifies if module is open (currently ignored)
 *  version:      the module version
 *  location:     the module location
 *  packages:     list of packages in the module
 *  num_packages: number of packages in the module
 */
JNIEXPORT void JNICALL
JVM_DefineModule(JNIEnv *env, jobject module, jboolean is_open, jstring version,
                 jstring location, const char* const* packages, jsize num_packages);

/*
 * Set the boot loader's unnamed module.
 *  module: boot loader's unnamed module
 */
JNIEXPORT void JNICALL
JVM_SetBootLoaderUnnamedModule(JNIEnv *env, jobject module);

/*
 * Do a qualified export of a package.
 *  from_module: module containing the package to export
 *  package:     name of the package to export
 *  to_module:   module to export the package to
 */
JNIEXPORT void JNICALL
JVM_AddModuleExports(JNIEnv *env, jobject from_module, const char* package, jobject to_module);

/*
 * Do an export of a package to all unnamed modules.
 *  from_module: module containing the package to export
 *  package:     name of the package to export to all unnamed modules
 */
JNIEXPORT void JNICALL
JVM_AddModuleExportsToAllUnnamed(JNIEnv *env, jobject from_module, const char* package);

/*
 * Do an unqualified export of a package.
 *  from_module: module containing the package to export
 *  package:     name of the package to export
 */
JNIEXPORT void JNICALL
JVM_AddModuleExportsToAll(JNIEnv *env, jobject from_module, const char* package);

/*
 * Add a module to the list of modules that a given module can read.
 *  from_module:   module requesting read access
 *  source_module: module that from_module wants to read
 */
JNIEXPORT void JNICALL
JVM_AddReadsModule(JNIEnv *env, jobject from_module, jobject source_module);

/*
 * Define all modules that have been stored in the CDS archived heap.
 *  platform_loader: the built-in platform class loader
 *  system_loader:   the built-in system class loader
 */
JNIEXPORT void JNICALL
JVM_DefineArchivedModules(JNIEnv *env, jobject platform_loader, jobject system_loader);

/* Nestmates - since JDK 11 */

JNIEXPORT jboolean JNICALL
JVM_AreNestMates(JNIEnv *env, jclass current, jclass member);

JNIEXPORT jclass JNICALL
JVM_GetNestHost(JNIEnv *env, jclass current);

JNIEXPORT jobjectArray JNICALL
JVM_GetNestMembers(JNIEnv *env, jclass current);

/* Records - since JDK 14 */

JNIEXPORT jboolean JNICALL
JVM_IsRecord(JNIEnv *env, jclass cls);

JNIEXPORT jobjectArray JNICALL
JVM_GetRecordComponents(JNIEnv *env, jclass ofClass);

/* Sealed types - since JDK 15 */

JNIEXPORT jobjectArray JNICALL
JVM_GetPermittedSubclasses(JNIEnv *env, jclass current);

/*
 * Reflection support functions
 */
 
JNIEXPORT jstring JNICALL
JVM_InitClassName(JNIEnv *env, jclass cls);

JNIEXPORT jstring JNICALL
JVM_GetClassName(JNIEnv *env, jclass cls);

JNIEXPORT jobjectArray JNICALL
JVM_GetClassInterfaces(JNIEnv *env, jclass cls);

JNIEXPORT jobject JNICALL
JVM_GetClassLoader(JNIEnv *env, jclass cls);

JNIEXPORT jboolean JNICALL
JVM_IsInterface(JNIEnv *env, jclass cls);

JNIEXPORT jobjectArray JNICALL
JVM_GetClassSigners(JNIEnv *env, jclass cls);

JNIEXPORT void JNICALL
JVM_SetClassSigners(JNIEnv *env, jclass cls, jobjectArray signers);

JNIEXPORT jobject JNICALL
JVM_GetProtectionDomain(JNIEnv *env, jclass cls);

JNIEXPORT jboolean JNICALL
JVM_IsArrayClass(JNIEnv *env, jclass cls);

JNIEXPORT jboolean JNICALL
JVM_IsPrimitiveClass(JNIEnv *env, jclass cls);

JNIEXPORT jboolean JNICALL
JVM_IsHiddenClass(JNIEnv *env, jclass cls);

JNIEXPORT jclass JNICALL
JVM_GetComponentType(JNIEnv *env, jclass cls);

JNIEXPORT jint JNICALL
JVM_GetClassModifiers(JNIEnv *env, jclass cls);

JNIEXPORT jobjectArray JNICALL
JVM_GetDeclaredClasses(JNIEnv *env, jclass ofClass);

JNIEXPORT jclass JNICALL
JVM_GetDeclaringClass(JNIEnv *env, jclass ofClass);

JNIEXPORT jstring JNICALL
JVM_GetSimpleBinaryName(JNIEnv *env, jclass ofClass);

/* Generics support (JDK 1.5) */
JNIEXPORT jstring JNICALL
JVM_GetClassSignature(JNIEnv *env, jclass cls);

/* Annotations support (JDK 1.5) */
JNIEXPORT jbyteArray JNICALL
JVM_GetClassAnnotations(JNIEnv *env, jclass cls);

/* Annotations support (JDK 1.6) */

// field is a handle to a java.lang.reflect.Field object
JNIEXPORT jbyteArray JNICALL
JVM_GetFieldAnnotations(JNIEnv *env, jobject field);

// method is a handle to a java.lang.reflect.Method object
JNIEXPORT jbyteArray JNICALL
JVM_GetMethodAnnotations(JNIEnv *env, jobject method);

// method is a handle to a java.lang.reflect.Method object
JNIEXPORT jbyteArray JNICALL
JVM_GetMethodDefaultAnnotationValue(JNIEnv *env, jobject method);

// method is a handle to a java.lang.reflect.Method object
JNIEXPORT jbyteArray JNICALL
JVM_GetMethodParameterAnnotations(JNIEnv *env, jobject method);

/* Type use annotations support (JDK 1.8) */

JNIEXPORT jbyteArray JNICALL
JVM_GetClassTypeAnnotations(JNIEnv *env, jclass cls);

// field is a handle to a java.lang.reflect.Field object
JNIEXPORT jbyteArray JNICALL
JVM_GetFieldTypeAnnotations(JNIEnv *env, jobject field);

// method is a handle to a java.lang.reflect.Method object
JNIEXPORT jbyteArray JNICALL
JVM_GetMethodTypeAnnotations(JNIEnv *env, jobject method);

/*
 * New (JDK 1.4) reflection implementation
 */

JNIEXPORT jobjectArray JNICALL
JVM_GetClassDeclaredMethods(JNIEnv *env, jclass ofClass, jboolean publicOnly);

JNIEXPORT jobjectArray JNICALL
JVM_GetClassDeclaredFields(JNIEnv *env, jclass ofClass, jboolean publicOnly);

JNIEXPORT jobjectArray JNICALL
JVM_GetClassDeclaredConstructors(JNIEnv *env, jclass ofClass, jboolean publicOnly);

/* Differs from JVM_GetClassModifiers in treatment of inner classes.
   This returns the access flags for the class as specified in the
   class file rather than searching the InnerClasses attribute (if
   present) to find the source-level access flags. Only the values of
   the low 13 bits (i.e., a mask of 0x1FFF) are guaranteed to be
   valid. */
JNIEXPORT jint JNICALL
JVM_GetClassAccessFlags(JNIEnv *env, jclass cls);

/*
 * Constant pool access; currently used to implement reflective access to annotations (JDK 1.5)
 */

JNIEXPORT jobject JNICALL
JVM_GetClassConstantPool(JNIEnv *env, jclass cls);

JNIEXPORT jint JNICALL JVM_ConstantPoolGetSize
(JNIEnv *env, jobject obj, jobject unused);

JNIEXPORT jclass JNICALL JVM_ConstantPoolGetClassAt
(JNIEnv *env, jobject obj, jobject unused, jint index);

JNIEXPORT jclass JNICALL JVM_ConstantPoolGetClassAtIfLoaded
(JNIEnv *env, jobject obj, jobject unused, jint index);

JNIEXPORT jint JNICALL JVM_ConstantPoolGetClassRefIndexAt
(JNIEnv *env, jobject obj, jobject unused, jint index);

JNIEXPORT jobject JNICALL JVM_ConstantPoolGetMethodAt
(JNIEnv *env, jobject obj, jobject unused, jint index);

JNIEXPORT jobject JNICALL JVM_ConstantPoolGetMethodAtIfLoaded
(JNIEnv *env, jobject obj, jobject unused, jint index);

JNIEXPORT jobject JNICALL JVM_ConstantPoolGetFieldAt
(JNIEnv *env, jobject obj, jobject unused, jint index);

JNIEXPORT jobject JNICALL JVM_ConstantPoolGetFieldAtIfLoaded
(JNIEnv *env, jobject obj, jobject unused, jint index);

JNIEXPORT jobjectArray JNICALL JVM_ConstantPoolGetMemberRefInfoAt
(JNIEnv *env, jobject obj, jobject unused, jint index);

JNIEXPORT jint JNICALL JVM_ConstantPoolGetNameAndTypeRefIndexAt
(JNIEnv *env, jobject obj, jobject unused, jint index);

JNIEXPORT jobjectArray JNICALL JVM_ConstantPoolGetNameAndTypeRefInfoAt
(JNIEnv *env, jobject obj, jobject unused, jint index);

JNIEXPORT jint JNICALL JVM_ConstantPoolGetIntAt
(JNIEnv *env, jobject obj, jobject unused, jint index);

JNIEXPORT jlong JNICALL JVM_ConstantPoolGetLongAt
(JNIEnv *env, jobject obj, jobject unused, jint index);

JNIEXPORT jfloat JNICALL JVM_ConstantPoolGetFloatAt
(JNIEnv *env, jobject obj, jobject unused, jint index);

JNIEXPORT jdouble JNICALL JVM_ConstantPoolGetDoubleAt
(JNIEnv *env, jobject obj, jobject unused, jint index);

JNIEXPORT jstring JNICALL JVM_ConstantPoolGetStringAt
(JNIEnv *env, jobject obj, jobject unused, jint index);

JNIEXPORT jstring JNICALL JVM_ConstantPoolGetUTF8At
(JNIEnv *env, jobject obj, jobject unused, jint index);

JNIEXPORT jbyte JNICALL JVM_ConstantPoolGetTagAt
(JNIEnv *env, jobject unused, jobject jcpool, jint index);

/*
 * Parameter reflection
 */

JNIEXPORT jobjectArray JNICALL
JVM_GetMethodParameters(JNIEnv *env, jobject method);

/*
 * java.security.*
 */

JNIEXPORT jobject JNICALL
JVM_DoPrivileged(JNIEnv *env, jclass cls,
                 jobject action, jobject context, jboolean wrapException);

JNIEXPORT jobject JNICALL
JVM_GetInheritedAccessControlContext(JNIEnv *env, jclass cls);

JNIEXPORT jobject JNICALL
JVM_GetStackAccessControlContext(JNIEnv *env, jclass cls);

/*
 * Signal support, used to implement the shutdown sequence.  Every VM must
 * support JVM_SIGINT and JVM_SIGTERM, raising the former for user interrupts
 * (^C) and the latter for external termination (kill, system shutdown, etc.).
 * Other platform-dependent signal values may also be supported.
 */

JNIEXPORT void * JNICALL
JVM_RegisterSignal(jint sig, void *handler);

JNIEXPORT jboolean JNICALL
JVM_RaiseSignal(jint sig);

JNIEXPORT jint JNICALL
JVM_FindSignal(const char *name);

/*
 * Retrieve the assertion directives for the specified class.
 */
JNIEXPORT jboolean JNICALL
JVM_DesiredAssertionStatus(JNIEnv *env, jclass unused, jclass cls);

/*
 * Retrieve the assertion directives from the VM.
 */
JNIEXPORT jobject JNICALL
JVM_AssertionStatusDirectives(JNIEnv *env, jclass unused);

/*
 * java.util.concurrent.atomic.AtomicLong
 */
JNIEXPORT jboolean JNICALL
JVM_SupportsCX8(void);

JNIEXPORT jboolean JNICALL
JVM_CX8Field(JNIEnv *env, jobject obj, jfieldID fldID, jlong oldVal, jlong newVal);

/*
 * com.sun.dtrace.jsdt support
 */

#define JVM_TRACING_DTRACE_VERSION 1

/*
 * Structure to pass one probe description to JVM.
 *
 * The VM will overwrite the definition of the referenced method with
 * code that will fire the probe.
 */
typedef struct {
    jmethodID method;
    jstring   function;
    jstring   name;
    void*     reserved[4];     // for future use
} JVM_DTraceProbe;

/**
 * Encapsulates the stability ratings for a DTrace provider field
 */
typedef struct {
    jint nameStability;
    jint dataStability;
    jint dependencyClass;
} JVM_DTraceInterfaceAttributes;

/*
 * Structure to pass one provider description to JVM
 */
typedef struct {
    jstring                       name;
    JVM_DTraceProbe*              probes;
    jint                          probe_count;
    JVM_DTraceInterfaceAttributes providerAttributes;
    JVM_DTraceInterfaceAttributes moduleAttributes;
    JVM_DTraceInterfaceAttributes functionAttributes;
    JVM_DTraceInterfaceAttributes nameAttributes;
    JVM_DTraceInterfaceAttributes argsAttributes;
    void*                         reserved[4]; // for future use
} JVM_DTraceProvider;

/*
 * Get the version number the JVM was built with
 */
JNIEXPORT jint JNICALL
JVM_DTraceGetVersion(JNIEnv* env);

/*
 * Register new probe with given signature, return global handle
 *
 * The version passed in is the version that the library code was
 * built with.
 */
JNIEXPORT jlong JNICALL
JVM_DTraceActivate(JNIEnv* env, jint version, jstring module_name,
  jint providers_count, JVM_DTraceProvider* providers);

/*
 * Check JSDT probe
 */
JNIEXPORT jboolean JNICALL
JVM_DTraceIsProbeEnabled(JNIEnv* env, jmethodID method);

/*
 * Destroy custom DOF
 */
JNIEXPORT void JNICALL
JVM_DTraceDispose(JNIEnv* env, jlong handle);

/*
 * Check to see if DTrace is supported by OS
 */
JNIEXPORT jboolean JNICALL
JVM_DTraceIsSupported(JNIEnv* env);

/*************************************************************************
 PART 2: Support for the Verifier and Class File Format Checker
 ************************************************************************/
/*
 * Return the class name in UTF format. The result is valid
 * until JVM_ReleaseUTf is called.
 *
 * The caller must treat the string as a constant and not modify it
 * in any way.
 */
JNIEXPORT const char * JNICALL
JVM_GetClassNameUTF(JNIEnv *env, jclass cb);

/*
 * Returns the constant pool types in the buffer provided by "types."
 */
JNIEXPORT void JNICALL
JVM_GetClassCPTypes(JNIEnv *env, jclass cb, unsigned char *types);

/*
 * Returns the number of Constant Pool entries.
 */
JNIEXPORT jint JNICALL
JVM_GetClassCPEntriesCount(JNIEnv *env, jclass cb);

/*
 * Returns the number of *declared* fields or methods.
 */
JNIEXPORT jint JNICALL
JVM_GetClassFieldsCount(JNIEnv *env, jclass cb);

JNIEXPORT jint JNICALL
JVM_GetClassMethodsCount(JNIEnv *env, jclass cb);

/*
 * Returns the CP indexes of exceptions raised by a given method.
 * Places the result in the given buffer.
 *
 * The method is identified by method_index.
 */
JNIEXPORT void JNICALL
JVM_GetMethodIxExceptionIndexes(JNIEnv *env, jclass cb, jint method_index,
                                unsigned short *exceptions);
/*
 * Returns the number of exceptions raised by a given method.
 * The method is identified by method_index.
 */
JNIEXPORT jint JNICALL
JVM_GetMethodIxExceptionsCount(JNIEnv *env, jclass cb, jint method_index);

/*
 * Returns the byte code sequence of a given method.
 * Places the result in the given buffer.
 *
 * The method is identified by method_index.
 */
JNIEXPORT void JNICALL
JVM_GetMethodIxByteCode(JNIEnv *env, jclass cb, jint method_index,
                        unsigned char *code);

/*
 * Returns the length of the byte code sequence of a given method.
 * The method is identified by method_index.
 */
JNIEXPORT jint JNICALL
JVM_GetMethodIxByteCodeLength(JNIEnv *env, jclass cb, jint method_index);

/*
 * A structure used to a capture exception table entry in a Java method.
 */
typedef struct {
    jint start_pc;
    jint end_pc;
    jint handler_pc;
    jint catchType;
} JVM_ExceptionTableEntryType;

/*
 * Returns the exception table entry at entry_index of a given method.
 * Places the result in the given buffer.
 *
 * The method is identified by method_index.
 */
JNIEXPORT void JNICALL
JVM_GetMethodIxExceptionTableEntry(JNIEnv *env, jclass cb, jint method_index,
                                   jint entry_index,
                                   JVM_ExceptionTableEntryType *entry);

/*
 * Returns the length of the exception table of a given method.
 * The method is identified by method_index.
 */
JNIEXPORT jint JNICALL
JVM_GetMethodIxExceptionTableLength(JNIEnv *env, jclass cb, int index);

/*
 * Returns the modifiers of a given field.
 * The field is identified by field_index.
 */
JNIEXPORT jint JNICALL
JVM_GetFieldIxModifiers(JNIEnv *env, jclass cb, int index);

/*
 * Returns the modifiers of a given method.
 * The method is identified by method_index.
 */
JNIEXPORT jint JNICALL
JVM_GetMethodIxModifiers(JNIEnv *env, jclass cb, int index);

/*
 * Returns the number of local variables of a given method.
 * The method is identified by method_index.
 */
JNIEXPORT jint JNICALL
JVM_GetMethodIxLocalsCount(JNIEnv *env, jclass cb, int index);

/*
 * Returns the number of arguments (including this pointer) of a given method.
 * The method is identified by method_index.
 */
JNIEXPORT jint JNICALL
JVM_GetMethodIxArgsSize(JNIEnv *env, jclass cb, int index);

/*
 * Returns the maximum amount of stack (in words) used by a given method.
 * The method is identified by method_index.
 */
JNIEXPORT jint JNICALL
JVM_GetMethodIxMaxStack(JNIEnv *env, jclass cb, int index);

/*
 * Is a given method a constructor.
 * The method is identified by method_index.
 */
JNIEXPORT jboolean JNICALL
JVM_IsConstructorIx(JNIEnv *env, jclass cb, int index);

/*
 * Is the given method generated by the VM.
 * The method is identified by method_index.
 */
JNIEXPORT jboolean JNICALL
JVM_IsVMGeneratedMethodIx(JNIEnv *env, jclass cb, int index);

/*
 * Returns the name of a given method in UTF format.
 * The result remains valid until JVM_ReleaseUTF is called.
 *
 * The caller must treat the string as a constant and not modify it
 * in any way.
 */
JNIEXPORT const char * JNICALL
JVM_GetMethodIxNameUTF(JNIEnv *env, jclass cb, jint index);

/*
 * Returns the signature of a given method in UTF format.
 * The result remains valid until JVM_ReleaseUTF is called.
 *
 * The caller must treat the string as a constant and not modify it
 * in any way.
 */
JNIEXPORT const char * JNICALL
JVM_GetMethodIxSignatureUTF(JNIEnv *env, jclass cb, jint index);

/*
 * Returns the name of the field refered to at a given constant pool
 * index.
 *
 * The result is in UTF format and remains valid until JVM_ReleaseUTF
 * is called.
 *
 * The caller must treat the string as a constant and not modify it
 * in any way.
 */
JNIEXPORT const char * JNICALL
JVM_GetCPFieldNameUTF(JNIEnv *env, jclass cb, jint index);

/*
 * Returns the name of the method refered to at a given constant pool
 * index.
 *
 * The result is in UTF format and remains valid until JVM_ReleaseUTF
 * is called.
 *
 * The caller must treat the string as a constant and not modify it
 * in any way.
 */
JNIEXPORT const char * JNICALL
JVM_GetCPMethodNameUTF(JNIEnv *env, jclass cb, jint index);

/*
 * Returns the signature of the method refered to at a given constant pool
 * index.
 *
 * The result is in UTF format and remains valid until JVM_ReleaseUTF
 * is called.
 *
 * The caller must treat the string as a constant and not modify it
 * in any way.
 */
JNIEXPORT const char * JNICALL
JVM_GetCPMethodSignatureUTF(JNIEnv *env, jclass cb, jint index);

/*
 * Returns the signature of the field refered to at a given constant pool
 * index.
 *
 * The result is in UTF format and remains valid until JVM_ReleaseUTF
 * is called.
 *
 * The caller must treat the string as a constant and not modify it
 * in any way.
 */
JNIEXPORT const char * JNICALL
JVM_GetCPFieldSignatureUTF(JNIEnv *env, jclass cb, jint index);

/*
 * Returns the class name refered to at a given constant pool index.
 *
 * The result is in UTF format and remains valid until JVM_ReleaseUTF
 * is called.
 *
 * The caller must treat the string as a constant and not modify it
 * in any way.
 */
JNIEXPORT const char * JNICALL
JVM_GetCPClassNameUTF(JNIEnv *env, jclass cb, jint index);

/*
 * Returns the class name refered to at a given constant pool index.
 *
 * The constant pool entry must refer to a CONSTANT_Fieldref.
 *
 * The result is in UTF format and remains valid until JVM_ReleaseUTF
 * is called.
 *
 * The caller must treat the string as a constant and not modify it
 * in any way.
 */
JNIEXPORT const char * JNICALL
JVM_GetCPFieldClassNameUTF(JNIEnv *env, jclass cb, jint index);

/*
 * Returns the class name refered to at a given constant pool index.
 *
 * The constant pool entry must refer to CONSTANT_Methodref or
 * CONSTANT_InterfaceMethodref.
 *
 * The result is in UTF format and remains valid until JVM_ReleaseUTF
 * is called.
 *
 * The caller must treat the string as a constant and not modify it
 * in any way.
 */
JNIEXPORT const char * JNICALL
JVM_GetCPMethodClassNameUTF(JNIEnv *env, jclass cb, jint index);

/*
 * Returns the modifiers of a field in calledClass. The field is
 * referred to in class cb at constant pool entry index.
 *
 * The caller must treat the string as a constant and not modify it
 * in any way.
 *
 * Returns -1 if the field does not exist in calledClass.
 */
JNIEXPORT jint JNICALL
JVM_GetCPFieldModifiers(JNIEnv *env, jclass cb, int index, jclass calledClass);

/*
 * Returns the modifiers of a method in calledClass. The method is
 * referred to in class cb at constant pool entry index.
 *
 * Returns -1 if the method does not exist in calledClass.
 */
JNIEXPORT jint JNICALL
JVM_GetCPMethodModifiers(JNIEnv *env, jclass cb, int index, jclass calledClass);

/*
 * Releases the UTF string obtained from the VM.
 */
JNIEXPORT void JNICALL
JVM_ReleaseUTF(const char *utf);

/*
 * Compare if two classes are in the same package.
 */
JNIEXPORT jboolean JNICALL
JVM_IsSameClassPackage(JNIEnv *env, jclass class1, jclass class2);

/* Constants in class files */

#define JVM_ACC_PUBLIC        0x0001  /* visible to everyone */
#define JVM_ACC_PRIVATE       0x0002  /* visible only to the defining class */
#define JVM_ACC_PROTECTED     0x0004  /* visible to subclasses */
#define JVM_ACC_STATIC        0x0008  /* instance variable is static */
#define JVM_ACC_FINAL         0x0010  /* no further subclassing, overriding */
#define JVM_ACC_SYNCHRONIZED  0x0020  /* wrap method call in monitor lock */
#define JVM_ACC_SUPER         0x0020  /* funky handling of invokespecial */
#define JVM_ACC_VOLATILE      0x0040  /* can not cache in registers */
#define JVM_ACC_BRIDGE        0x0040  /* bridge method generated by compiler */
#define JVM_ACC_TRANSIENT     0x0080  /* not persistent */
#define JVM_ACC_VARARGS       0x0080  /* method declared with variable number of args */
#define JVM_ACC_NATIVE        0x0100  /* implemented in C */
#define JVM_ACC_INTERFACE     0x0200  /* class is an interface */
#define JVM_ACC_ABSTRACT      0x0400  /* no definition provided */
#define JVM_ACC_STRICT        0x0800  /* strict floating point */
#define JVM_ACC_SYNTHETIC     0x1000  /* compiler-generated class, method or field */
#define JVM_ACC_ANNOTATION    0x2000  /* annotation type */
#define JVM_ACC_ENUM          0x4000  /* field is declared as element of enum */

#define JVM_ACC_PUBLIC_BIT        0
#define JVM_ACC_PRIVATE_BIT       1
#define JVM_ACC_PROTECTED_BIT     2
#define JVM_ACC_STATIC_BIT        3
#define JVM_ACC_FINAL_BIT         4
#define JVM_ACC_SYNCHRONIZED_BIT  5
#define JVM_ACC_SUPER_BIT         5
#define JVM_ACC_VOLATILE_BIT      6
#define JVM_ACC_BRIDGE_BIT        6
#define JVM_ACC_TRANSIENT_BIT     7
#define JVM_ACC_VARARGS_BIT       7
#define JVM_ACC_NATIVE_BIT        8
#define JVM_ACC_INTERFACE_BIT     9
#define JVM_ACC_ABSTRACT_BIT      10
#define JVM_ACC_STRICT_BIT        11
#define JVM_ACC_SYNTHETIC_BIT     12
#define JVM_ACC_ANNOTATION_BIT    13
#define JVM_ACC_ENUM_BIT          14

// NOTE: replicated in SA in vm/agent/sun/jvm/hotspot/utilities/ConstantTag.java
enum {
    JVM_CONSTANT_Utf8 = 1,
    JVM_CONSTANT_Unicode,               /* unused */
    JVM_CONSTANT_Integer,
    JVM_CONSTANT_Float,
    JVM_CONSTANT_Long,
    JVM_CONSTANT_Double,
    JVM_CONSTANT_Class,
    JVM_CONSTANT_String,
    JVM_CONSTANT_Fieldref,
    JVM_CONSTANT_Methodref,
    JVM_CONSTANT_InterfaceMethodref,
    JVM_CONSTANT_NameAndType,
    JVM_CONSTANT_MethodHandle           = 15,  // JSR 292
    JVM_CONSTANT_MethodType             = 16,  // JSR 292
    //JVM_CONSTANT_(unused)             = 17,  // JSR 292 early drafts only
    JVM_CONSTANT_InvokeDynamic          = 18,  // JSR 292
    JVM_CONSTANT_ExternalMax            = 18   // Last tag found in classfiles
};

/* JVM_CONSTANT_MethodHandle subtypes */
enum {
    JVM_REF_getField                = 1,
    JVM_REF_getStatic               = 2,
    JVM_REF_putField                = 3,
    JVM_REF_putStatic               = 4,
    JVM_REF_invokeVirtual           = 5,
    JVM_REF_invokeStatic            = 6,
    JVM_REF_invokeSpecial           = 7,
    JVM_REF_newInvokeSpecial        = 8,
    JVM_REF_invokeInterface         = 9
};

/* Used in the newarray instruction. */

#define JVM_T_BOOLEAN 4
#define JVM_T_CHAR    5
#define JVM_T_FLOAT   6
#define JVM_T_DOUBLE  7
#define JVM_T_BYTE    8
#define JVM_T_SHORT   9
#define JVM_T_INT    10
#define JVM_T_LONG   11

/* JVM method signatures */

#define JVM_SIGNATURE_ARRAY             '['
#define JVM_SIGNATURE_BYTE              'B'
#define JVM_SIGNATURE_CHAR              'C'
#define JVM_SIGNATURE_CLASS             'L'
#define JVM_SIGNATURE_ENDCLASS          ';'
#define JVM_SIGNATURE_ENUM              'E'
#define JVM_SIGNATURE_FLOAT             'F'
#define JVM_SIGNATURE_DOUBLE            'D'
#define JVM_SIGNATURE_FUNC              '('
#define JVM_SIGNATURE_ENDFUNC           ')'
#define JVM_SIGNATURE_INT               'I'
#define JVM_SIGNATURE_LONG              'J'
#define JVM_SIGNATURE_SHORT             'S'
#define JVM_SIGNATURE_VOID              'V'
#define JVM_SIGNATURE_BOOLEAN           'Z'

/*
 * A function defined by the byte-code verifier and called by the VM.
 * This is not a function implemented in the VM.
 *
 * Returns JNI_FALSE if verification fails. A detailed error message
 * will be places in msg_buf, whose length is specified by buf_len.
 */
typedef jboolean (*verifier_fn_t)(JNIEnv *env,
                                  jclass cb,
                                  char * msg_buf,
                                  jint buf_len);


/*
 * Support for a VM-independent class format checker.
 */
typedef struct {
    unsigned long code;    /* byte code */
    unsigned long excs;    /* exceptions */
    unsigned long etab;    /* catch table */
    unsigned long lnum;    /* line number */
    unsigned long lvar;    /* local vars */
} method_size_info;

typedef struct {
    unsigned int constants;    /* constant pool */
    unsigned int fields;
    unsigned int methods;
    unsigned int interfaces;
    unsigned int fields2;      /* number of static 2-word fields */
    unsigned int innerclasses; /* # of records in InnerClasses attr */

    method_size_info clinit;   /* memory used in clinit */
    method_size_info main;     /* used everywhere else */
} class_size_info;

/*
 * Functions defined in libjava.so to perform string conversions.
 *
 */

typedef jstring (*to_java_string_fn_t)(JNIEnv *env, char *str);

typedef char *(*to_c_string_fn_t)(JNIEnv *env, jstring s, jboolean *b);

/* This is the function defined in libjava.so that performs class
 * format checks. This functions fills in size information about
 * the class file and returns:
 *
 *   0: good
 *  -1: out of memory
 *  -2: bad format
 *  -3: unsupported version
 *  -4: bad class name
 */

typedef jint (*check_format_fn_t)(char *class_name,
                                  unsigned char *data,
                                  unsigned int data_size,
                                  class_size_info *class_size,
                                  char *message_buffer,
                                  jint buffer_length,
                                  jboolean measure_only,
                                  jboolean check_relaxed);

#define JVM_RECOGNIZED_CLASS_MODIFIERS (JVM_ACC_PUBLIC | \
                                        JVM_ACC_FINAL | \
                                        JVM_ACC_SUPER | \
                                        JVM_ACC_INTERFACE | \
                                        JVM_ACC_ABSTRACT | \
                                        JVM_ACC_ANNOTATION | \
                                        JVM_ACC_ENUM | \
                                        JVM_ACC_SYNTHETIC)

#define JVM_RECOGNIZED_FIELD_MODIFIERS (JVM_ACC_PUBLIC | \
                                        JVM_ACC_PRIVATE | \
                                        JVM_ACC_PROTECTED | \
                                        JVM_ACC_STATIC | \
                                        JVM_ACC_FINAL | \
                                        JVM_ACC_VOLATILE | \
                                        JVM_ACC_TRANSIENT | \
                                        JVM_ACC_ENUM | \
                                        JVM_ACC_SYNTHETIC)

#define JVM_RECOGNIZED_METHOD_MODIFIERS (JVM_ACC_PUBLIC | \
                                         JVM_ACC_PRIVATE | \
                                         JVM_ACC_PROTECTED | \
                                         JVM_ACC_STATIC | \
                                         JVM_ACC_FINAL | \
                                         JVM_ACC_SYNCHRONIZED | \
                                         JVM_ACC_BRIDGE | \
                                         JVM_ACC_VARARGS | \
                                         JVM_ACC_NATIVE | \
                                         JVM_ACC_ABSTRACT | \
                                         JVM_ACC_STRICT | \
                                         JVM_ACC_SYNTHETIC)

/*
 * This is the function defined in libjava.so to perform path
 * canonicalization. VM call this function before opening jar files
 * to load system classes.
 *
 */

typedef int (*canonicalize_fn_t)(JNIEnv *env, char *orig, char *out, int len);

/*************************************************************************
 PART 3: I/O and Network Support
 ************************************************************************/

/* Note that the JVM IO functions are expected to return JVM_IO_ERR
 * when there is any kind of error. The caller can then use the
 * platform specific support (e.g., errno) to get the detailed
 * error info.  The JVM_GetLastErrorString procedure may also be used
 * to obtain a descriptive error string.
 */
#define JVM_IO_ERR  (-1)

/* For interruptible IO. Returning JVM_IO_INTR indicates that an IO
 * operation has been disrupted by Thread.interrupt. There are a
 * number of technical difficulties related to interruptible IO that
 * need to be solved. For example, most existing programs do not handle
 * InterruptedIOExceptions specially, they simply treat those as any
 * IOExceptions, which typically indicate fatal errors.
 *
 * There are also two modes of operation for interruptible IO. In the
 * resumption mode, an interrupted IO operation is guaranteed not to
 * have any side-effects, and can be restarted. In the termination mode,
 * an interrupted IO operation corrupts the underlying IO stream, so
 * that the only reasonable operation on an interrupted stream is to
 * close that stream. The resumption mode seems to be impossible to
 * implement on Win32 and Solaris. Implementing the termination mode is
 * easier, but it's not clear that's the right semantics.
 *
 * Interruptible IO is not supported on Win32.It can be enabled/disabled
 * using a compile-time flag on Solaris. Third-party JVM ports do not
 * need to implement interruptible IO.
 */
#define JVM_IO_INTR (-2)

/* Write a string into the given buffer, in the platform's local encoding,
 * that describes the most recent system-level error to occur in this thread.
 * Return the length of the string or zero if no error occurred.
 */
JNIEXPORT jint JNICALL
JVM_GetLastErrorString(char *buf, int len);

/*
 * Convert a pathname into native format.  This function does syntactic
 * cleanup, such as removing redundant separator characters.  It modifies
 * the given pathname string in place.
 */
JNIEXPORT char * JNICALL
JVM_NativePath(char *);

/*
 * JVM I/O error codes
 */
#define JVM_EEXIST       -100

/*
 * Open a file descriptor. This function returns a negative error code
 * on error, and a non-negative integer that is the file descriptor on
 * success.
 */
JNIEXPORT jint JNICALL
JVM_Open(const char *fname, jint flags, jint mode);

/*
 * Close a file descriptor. This function returns -1 on error, and 0
 * on success.
 *
 * fd        the file descriptor to close.
 */
JNIEXPORT jint JNICALL
JVM_Close(jint fd);

/*
 * Read data from a file decriptor into a char array.
 *
 * fd        the file descriptor to read from.
 * buf       the buffer where to put the read data.
 * nbytes    the number of bytes to read.
 *
 * This function returns -1 on error, and 0 on success.
 */
JNIEXPORT jint JNICALL
JVM_Read(jint fd, char *buf, jint nbytes);

/*
 * Write data from a char array to a file decriptor.
 *
 * fd        the file descriptor to read from.
 * buf       the buffer from which to fetch the data.
 * nbytes    the number of bytes to write.
 *
 * This function returns -1 on error, and 0 on success.
 */
JNIEXPORT jint JNICALL
JVM_Write(jint fd, char *buf, jint nbytes);

/*
 * Returns the number of bytes available for reading from a given file
 * descriptor
 */
JNIEXPORT jint JNICALL
JVM_Available(jint fd, jlong *pbytes);

/*
 * Move the file descriptor pointer from whence by offset.
 *
 * fd        the file descriptor to move.
 * offset    the number of bytes to move it by.
 * whence    the start from where to move it.
 *
 * This function returns the resulting pointer location.
 */
JNIEXPORT jlong JNICALL
JVM_Lseek(jint fd, jlong offset, jint whence);

/*
 * Set the length of the file associated with the given descriptor to the given
 * length.  If the new length is longer than the current length then the file
 * is extended; the contents of the extended portion are not defined.  The
 * value of the file pointer is undefined after this procedure returns.
 */
JNIEXPORT jint JNICALL
JVM_SetLength(jint fd, jlong length);

/*
 * Synchronize the file descriptor's in memory state with that of the
 * physical device.  Return of -1 is an error, 0 is OK.
 */
JNIEXPORT jint JNICALL
JVM_Sync(jint fd);

/*
 * Networking library support
 */

JNIEXPORT jint JNICALL
JVM_InitializeSocketLibrary(void);

struct sockaddr;

JNIEXPORT jint JNICALL
JVM_Socket(jint domain, jint type, jint protocol);

JNIEXPORT jint JNICALL
JVM_SocketClose(jint fd);

JNIEXPORT jint JNICALL
JVM_SocketShutdown(jint fd, jint howto);

JNIEXPORT jint JNICALL
JVM_Recv(jint fd, char *buf, jint nBytes, jint flags);

JNIEXPORT jint JNICALL
JVM_Send(jint fd, char *buf, jint nBytes, jint flags);

JNIEXPORT jint JNICALL
JVM_Timeout(int fd, long timeout);

JNIEXPORT jint JNICALL
JVM_Listen(jint fd, jint count);

JNIEXPORT jint JNICALL
JVM_Connect(jint fd, struct sockaddr *him, jint len);

JNIEXPORT jint JNICALL
JVM_Bind(jint fd, struct sockaddr *him, jint len);

JNIEXPORT jint JNICALL
JVM_Accept(jint fd, struct sockaddr *him, jint *len);

JNIEXPORT jint JNICALL
JVM_RecvFrom(jint fd, char *buf, int nBytes,
                  int flags, struct sockaddr *from, int *fromlen);

JNIEXPORT jint JNICALL
JVM_SendTo(jint fd, char *buf, int len,
                int flags, struct sockaddr *to, int tolen);

JNIEXPORT jint JNICALL
JVM_SocketAvailable(jint fd, jint *result);


JNIEXPORT jint JNICALL
JVM_GetSockName(jint fd, struct sockaddr *him, int *len);

JNIEXPORT jint JNICALL
JVM_GetSockOpt(jint fd, int level, int optname, char *optval, int *optlen);

JNIEXPORT jint JNICALL
JVM_SetSockOpt(jint fd, int level, int optname, const char *optval, int optlen);

JNIEXPORT int JNICALL
JVM_GetHostName(char* name, int namelen);

/*
 * The standard printing functions supported by the Java VM. (Should they
 * be renamed to JVM_* in the future?
 */

/*
 * BE CAREFUL! The following functions do not implement the
 * full feature set of standard C printf formats.
 */
JNIEXPORT int
jio_vsnprintf(char *str, size_t count, const char *fmt, va_list args);

JNIEXPORT int
jio_snprintf(char *str, size_t count, const char *fmt, ...);

JNIEXPORT int
jio_fprintf(FILE *, const char *fmt, ...);

JNIEXPORT int
jio_vfprintf(FILE *, const char *fmt, va_list args);


JNIEXPORT void * JNICALL
JVM_RawMonitorCreate(void);

JNIEXPORT void JNICALL
JVM_RawMonitorDestroy(void *mon);

JNIEXPORT jint JNICALL
JVM_RawMonitorEnter(void *mon);

JNIEXPORT void JNICALL
JVM_RawMonitorExit(void *mon);

/*
 * java.lang.reflect.Method
 */
JNIEXPORT jobject JNICALL
JVM_InvokeMethod(JNIEnv *env, jobject method, jobject obj, jobjectArray args0);

/*
 * java.lang.reflect.Constructor
 */
JNIEXPORT jobject JNICALL
JVM_NewInstanceFromConstructor(JNIEnv *env, jobject c, jobjectArray args0);

/*
 * java.lang.management support
 */
JNIEXPORT void* JNICALL
JVM_GetManagement(jint version);

/*
 * com.sun.tools.attach.VirtualMachine support
 *
 * Initialize the agent properties with the properties maintained in the VM.
 */
JNIEXPORT jobject JNICALL
JVM_InitAgentProperties(JNIEnv *env, jobject agent_props);

JNIEXPORT jstring JNICALL
JVM_GetTemporaryDirectory(JNIEnv *env);

/* Generics reflection support.
 *
 * Returns information about the given class's EnclosingMethod
 * attribute, if present, or null if the class had no enclosing
 * method.
 *
 * If non-null, the returned array contains three elements. Element 0
 * is the java.lang.Class of which the enclosing method is a member,
 * and elements 1 and 2 are the java.lang.Strings for the enclosing
 * method's name and descriptor, respectively.
 */
JNIEXPORT jobjectArray JNICALL
JVM_GetEnclosingMethodInfo(JNIEnv* env, jclass ofClass);

/*
 * Java thread state support
 */
enum {
    JAVA_THREAD_STATE_NEW           = 0,
    JAVA_THREAD_STATE_RUNNABLE      = 1,
    JAVA_THREAD_STATE_BLOCKED       = 2,
    JAVA_THREAD_STATE_WAITING       = 3,
    JAVA_THREAD_STATE_TIMED_WAITING = 4,
    JAVA_THREAD_STATE_TERMINATED    = 5,
    JAVA_THREAD_STATE_COUNT         = 6
};

/*
 * Returns an array of the threadStatus values representing the
 * given Java thread state.  Returns NULL if the VM version is
 * incompatible with the JDK or doesn't support the given
 * Java thread state.
 */
JNIEXPORT jintArray JNICALL
JVM_GetThreadStateValues(JNIEnv* env, jint javaThreadState);

/*
 * Returns an array of the substate names representing the
 * given Java thread state.  Returns NULL if the VM version is
 * incompatible with the JDK or the VM doesn't support
 * the given Java thread state.
 * values must be the jintArray returned from JVM_GetThreadStateValues
 * and javaThreadState.
 */
JNIEXPORT jobjectArray JNICALL
JVM_GetThreadStateNames(JNIEnv* env, jint javaThreadState, jintArray values);

/*
 * Returns true if the JVM's lookup cache indicates that this class is
 * known to NOT exist for the given loader.
 */
JNIEXPORT jboolean JNICALL
JVM_KnownToNotExist(JNIEnv *env, jobject loader, const char *classname);

/*
 * Returns an array of all URLs that are stored in the JVM's lookup cache
 * for the given loader. NULL if the lookup cache is unavailable.
 */
JNIEXPORT jobjectArray JNICALL
JVM_GetResourceLookupCacheURLs(JNIEnv *env, jobject loader);

/*
 * Returns an array of all URLs that *may* contain the resource_name for the
 * given loader. This function returns an integer array, each element
 * of which can be used to index into the array returned by
 * JVM_GetResourceLookupCacheURLs of the same loader to determine the
 * URLs.
 */
JNIEXPORT jintArray JNICALL
JVM_GetResourceLookupCache(JNIEnv *env, jobject loader, const char *resource_name);


/* =========================================================================
 * The following defines a private JVM interface that the JDK can query
 * for the JVM version and capabilities.  sun.misc.Version defines
 * the methods for getting the VM version and its capabilities.
 *
 * When a new bit is added, the following should be updated to provide
 * access to the new capability:
 *    HS:   JVM_GetVersionInfo and Abstract_VM_Version class
 *    SDK:  Version class
 *
 * Similary, a private JDK interface JDK_GetVersionInfo0 is defined for
 * JVM to query for the JDK version and capabilities.
 *
 * When a new bit is added, the following should be updated to provide
 * access to the new capability:
 *    HS:   JDK_Version class
 *    SDK:  JDK_GetVersionInfo0
 *
 * ==========================================================================
 */
typedef struct {
    /* HotSpot Express VM version string:
     * <major>.<minor>-bxx[-<identifier>][-<debug_flavor>]
     */
    unsigned int jvm_version; /* Consists of major.minor.0.build */
    unsigned int update_version : 8;         /* 0 in HotSpot Express VM */
    unsigned int special_update_version : 8; /* 0 in HotSpot Express VM */
    unsigned int reserved1 : 16;
    unsigned int reserved2;

    /* The following bits represents JVM supports that JDK has dependency on.
     * JDK can use these bits to determine which JVM version
     * and support it has to maintain runtime compatibility.
     *
     * When a new bit is added in a minor or update release, make sure
     * the new bit is also added in the main/baseline.
     */
    unsigned int is_attachable : 1;
    unsigned int : 31;
    unsigned int : 32;
    unsigned int : 32;
} jvm_version_info;

#define JVM_VERSION_MAJOR(version) ((version & 0xFF000000) >> 24)
#define JVM_VERSION_MINOR(version) ((version & 0x00FF0000) >> 16)
// Micro version is 0 in HotSpot Express VM (set in jvm.cpp).
#define JVM_VERSION_MICRO(version) ((version & 0x0000FF00) >> 8)
/* Build number is available in all HotSpot Express VM builds.
 * It is defined in make/hotspot_version file.
 */
#define JVM_VERSION_BUILD(version) ((version & 0x000000FF))

JNIEXPORT void JNICALL
JVM_GetVersionInfo(JNIEnv* env, jvm_version_info* info, size_t info_size);

typedef struct jdk_version_info {
    // Naming convention of RE build version string: n.n.n[_uu[c]][-<identifier>]-bxx
    unsigned int jdk_version;   /* Consists of major, minor, micro (n.n.n) */
                                /* and build number (xx) */
    unsigned int update_version : 8;         /* Update release version (uu) */
    unsigned int special_update_version : 8; /* Special update release version (c)*/
    unsigned int reserved1 : 16;
    unsigned int reserved2;

    /* The following bits represents new JDK supports that VM has dependency on.
     * VM implementation can use these bits to determine which JDK version
     * and support it has to maintain runtime compatibility.
     *
     * When a new bit is added in a minor or update release, make sure
     * the new bit is also added in the main/baseline.
     */
    unsigned int thread_park_blocker : 1;
    unsigned int post_vm_init_hook_enabled : 1;
    unsigned int pending_list_uses_discovered_field : 1;
    unsigned int : 29;
    unsigned int : 32;
    unsigned int : 32;
} jdk_version_info;

#define JDK_VERSION_MAJOR(version) ((version & 0xFF000000) >> 24)
#define JDK_VERSION_MINOR(version) ((version & 0x00FF0000) >> 16)
#define JDK_VERSION_MICRO(version) ((version & 0x0000FF00) >> 8)

/* Build number is available only for RE build (i.e. JDK_BUILD_NUMBER is set to bNN)
 * It will be zero for internal builds.
 */
#define JDK_VERSION_BUILD(version) ((version & 0x000000FF))

/*
 * This is the function JDK_GetVersionInfo0 defined in libjava.so
 * that is dynamically looked up by JVM.
 */
typedef void (*jdk_version_info_fn_t)(jdk_version_info* info, size_t info_size);

/*
 * This structure is used by the launcher to get the default thread
 * stack size from the VM using JNI_GetDefaultJavaVMInitArgs() with a
 * version of 1.1.  As it is not supported otherwise, it has been removed
 * from jni.h
 */
typedef struct JDK1_1InitArgs {
    jint version;

    char **properties;
    jint checkSource;
    jint nativeStackSize;
    jint javaStackSize;
    jint minHeapSize;
    jint maxHeapSize;
    jint verifyMode;
    char *classpath;

    jint (JNICALL *vfprintf)(FILE *fp, const char *format, va_list args);
    void (JNICALL *exit)(jint code);
    void (JNICALL *abort)(void);

    jint enableClassGC;
    jint enableVerboseGC;
    jint disableAsyncGC;
    jint verbose;
    jboolean debugging;
    jint debugPort;
} JDK1_1InitArgs;

#ifdef __cplusplus
} /* extern "C" */
#endif /* __cplusplus */

#endif /* !_JAVASOFT_JVM_H_ */
