/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core.jni.functions;

import static com.oracle.svm.core.heap.RestrictHeapAccess.Access.NO_ALLOCATION;

import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import com.oracle.svm.core.thread.VMThreads;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.FatalExceptionHandler;
import org.graalvm.nativeimage.c.function.CEntryPoint.Publish;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CConst;
import org.graalvm.nativeimage.c.type.CShortPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.JavaMemoryUtil;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateDiagnostics;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.ReturnNullPointer;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.handles.PrimitiveArrayView;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.hub.RuntimeClassLoading.ClassDefinitionInfo;
import com.oracle.svm.core.jdk.DirectByteBufferUtil;
import com.oracle.svm.core.jni.JNIObjectFieldAccess;
import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jni.JNIThreadLocalPendingException;
import com.oracle.svm.core.jni.JNIThreadLocalPrimitiveArrayViews;
import com.oracle.svm.core.jni.JNIThreadOwnedMonitors;
import com.oracle.svm.core.jni.access.JNIAccessibleField;
import com.oracle.svm.core.jni.access.JNIAccessibleMethod;
import com.oracle.svm.core.jni.access.JNIAccessibleMethodDescriptor;
import com.oracle.svm.core.jni.access.JNINativeLinkage;
import com.oracle.svm.core.jni.access.JNIReflectionDictionary;
import com.oracle.svm.core.jni.functions.JNIFunctions.Support.JNIEnvEnterFatalOnFailurePrologue;
import com.oracle.svm.core.jni.functions.JNIFunctions.Support.JNIEnvEnterPrologue;
import com.oracle.svm.core.jni.functions.JNIFunctions.Support.JNIExceptionHandlerReturnFalse;
import com.oracle.svm.core.jni.functions.JNIFunctions.Support.JNIExceptionHandlerReturnJniErr;
import com.oracle.svm.core.jni.functions.JNIFunctions.Support.JNIExceptionHandlerReturnMinusOne;
import com.oracle.svm.core.jni.functions.JNIFunctions.Support.JNIExceptionHandlerReturnNullHandle;
import com.oracle.svm.core.jni.functions.JNIFunctions.Support.JNIExceptionHandlerReturnNullWord;
import com.oracle.svm.core.jni.functions.JNIFunctions.Support.JNIExceptionHandlerReturnZero;
import com.oracle.svm.core.jni.functions.JNIFunctions.Support.JNIExceptionHandlerVoid;
import com.oracle.svm.core.jni.functions.JNIFunctions.Support.ReturnEDetached;
import com.oracle.svm.core.jni.functions.JNIFunctions.Support.ReturnMinusOne;
import com.oracle.svm.core.jni.functions.JNIFunctions.Support.ReturnMinusOneLong;
import com.oracle.svm.core.jni.functions.JNIFunctions.Support.ReturnNullHandle;
import com.oracle.svm.core.jni.functions.JNIFunctions;
import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.jni.headers.JNIErrors;
import com.oracle.svm.core.jni.headers.JNIFieldId;
import com.oracle.svm.core.jni.headers.JNIJavaVM;
import com.oracle.svm.core.jni.headers.JNIJavaVMPointer;
import com.oracle.svm.core.jni.headers.JNIMethodId;
import com.oracle.svm.core.jni.headers.JNIMode;
import com.oracle.svm.core.jni.headers.JNINativeMethod;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.core.jni.headers.JNIObjectRefType;
import com.oracle.svm.core.jni.headers.JNIValue;
import com.oracle.svm.core.jni.headers.JNIVersion;
import com.oracle.svm.core.libjvm.LibJVMMainMethodWrappers;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.monitor.MonitorInflationCause;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.ContinuationSupport;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.Target_java_lang_BaseVirtualThread;
import com.oracle.svm.core.thread.Target_jdk_internal_vm_Continuation;
import com.oracle.svm.core.thread.VMThreads.SafepointBehavior;
import com.oracle.svm.core.util.ArrayUtil;
import com.oracle.svm.core.util.Utf8;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.word.Word;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaUtil;

/**
 * Implementations of the functions defined by the Java Native Interface.
 *
 * Not all functions are currently implemented. Some functions are generated, and therefore not
 * defined in this class:
 *
 * <ul>
 * <li>Field getters and setters ({@code Get<Type>Field}, {@code Set<Type>Field},
 * {@code GetStatic<Type>Field}, and {@code SetStatic<Type>Field}) are generated in
 * {@code JNIFieldAccessorMethod}.</li>
 *
 * <li>Operations on primitive arrays {@code Get<PrimitiveType>ArrayElements},
 * {@code Release<PrimitiveType>ArrayElements}, {@code Get<PrimitiveType>ArrayRegion} and
 * {@code Set<PrimitiveType>ArrayRegion}) are generated in
 * {@code JNIPrimitiveArrayOperationMethod}</li>
 *
 * <li>Wrappers for the methods callable by JNI are generated in
 * {@code JNIJavaCallWrapperMethod}.</li>
 * </ul>
 *
 * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/functions.html">Java
 *      Native Interface Specification: JNI Functions</a>
 */
@SuppressWarnings("unused")
public final class ValidatedJNIFunctions {
    private static final Unsafe U = Unsafe.getUnsafe();

    // Checkstyle: stop

    /*
     * jint GetVersion(JNIEnv *env);
     */

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class)
    static int GetVersion(JNIEnvironment env) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        int result = JNIFunctions.GetVersion(env);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jobject NewLocalRef(JNIEnv *env, jobject ref);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewLocalRef(JNIEnvironment env, JNIObjectHandle ref) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        if (!ref.equal(Word.nullPointer())) {
            JNIValidation.validateHandle(ref);
        }
        JNIObjectHandle result = JNIFunctions.NewLocalRef(env, ref);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * void DeleteLocalRef(JNIEnv *env, jobject localRef);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void DeleteLocalRef(JNIEnvironment env, JNIObjectHandle localRef) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterExceptionAllowed();
        JNIValidation.validateObject(localRef);
        JNIObjectRefType refType = JNIObjectHandles.getHandleType(localRef);
        if (!(refType.equals(JNIObjectRefType.Local))) JNIValidation.failFatally("Invalid local JNI handle passed to DeleteLocalRef");
        JNIFunctions.DeleteLocalRef(env, localRef);
        JNIValidation.functionExit();
    }

    /*
     * jint EnsureLocalCapacity(JNIEnv *env, jint capacity);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnJniErr.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnEDetached.class)
    static int EnsureLocalCapacity(JNIEnvironment env, int capacity) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        if (capacity < 0) {
            JNIValidation.failFatally("negative capacity");
        }
        int result = JNIFunctions.EnsureLocalCapacity(env, capacity);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jint PushLocalFrame(JNIEnv *env, jint capacity);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnJniErr.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnEDetached.class)
    static int PushLocalFrame(JNIEnvironment env, int capacity) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterExceptionAllowed();
        if (capacity < 0) {
            JNIValidation.failFatally("negative capacity");
        }
        int result = JNIFunctions.PushLocalFrame(env, capacity);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jobject PopLocalFrame(JNIEnv *env, jobject result);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle PopLocalFrame(JNIEnvironment env, JNIObjectHandle handle) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterExceptionAllowed();
        JNIObjectHandle result = JNIFunctions.PopLocalFrame(env, handle);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jboolean IsSameObject(JNIEnv *env, jobject ref1, jobject ref2);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnFalse.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static boolean IsSameObject(JNIEnvironment env, JNIObjectHandle ref1, JNIObjectHandle ref2) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterExceptionAllowed();
        if(!ref1.equal(Word.nullPointer())) {
            if(JNIValidation.validateHandle(ref1)) {
                JNIValidation.validateObject(ref1);
            }
        }
        if(!ref2.equal(Word.nullPointer())) {
            if(JNIValidation.validateHandle(ref2)) {
                JNIValidation.validateObject(ref2);
            }
        }
        boolean result = JNIFunctions.IsSameObject(env, ref1, ref2);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jboolean IsInstanceOf(JNIEnv *env, jobject obj, jclass clazz);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnFalse.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static boolean IsInstanceOf(JNIEnvironment env, JNIObjectHandle obj, JNIObjectHandle clazz) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateObject(obj);
        JNIValidation.validateClass(clazz, true);
        boolean result = JNIFunctions.IsInstanceOf(env, obj, clazz);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jclass GetObjectClass(JNIEnv *env, jobject obj);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle GetObjectClass(JNIEnvironment env, JNIObjectHandle handle) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateObject(handle);
        JNIObjectHandle result = JNIFunctions.GetObjectClass(env, handle);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jclass GetSuperclass(JNIEnv *env, jclass clazz);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle GetSuperclass(JNIEnvironment env, JNIObjectHandle handle) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(handle, true);
        JNIObjectHandle result = JNIFunctions.GetSuperclass(env, handle);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jboolean IsAssignableFrom(JNIEnv *env, jclass clazz1, jclass clazz2);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnFalse.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static boolean IsAssignableFrom(JNIEnvironment env, JNIObjectHandle handle1, JNIObjectHandle handle2) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(handle1, true);
        JNIValidation.validateClass(handle2, true);
        boolean result = JNIFunctions.IsAssignableFrom(env, handle1, handle2);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jobject NewGlobalRef(JNIEnv *env, jobject obj);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static JNIObjectHandle NewGlobalRef(JNIEnvironment env, JNIObjectHandle handle) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        if (!handle.equal(Word.nullPointer())) {
            JNIValidation.validateHandle(handle);
        }
        JNIObjectHandle result = JNIFunctions.NewGlobalRef(env, handle);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * void DeleteGlobalRef(JNIEnv *env, jobject globalRef);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void DeleteGlobalRef(JNIEnvironment env, JNIObjectHandle globalRef) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterExceptionAllowed();
        JNIValidation.validateObject(globalRef);
        JNIObjectRefType refType = JNIObjectHandles.getHandleType(globalRef);
        if (!refType.equals(JNIObjectRefType.Global)) JNIValidation.failFatally("Invalid global JNI handle passed to DeleteGlobalRef");
        JNIFunctions.DeleteGlobalRef(env, globalRef);
        JNIValidation.functionExit();
    }

    /*
     * jweak NewWeakGlobalRef(JNIEnv *env, jobject obj);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewWeakGlobalRef(JNIEnvironment env, JNIObjectHandle handle) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        if (!handle.equal(Word.nullPointer())) {
            JNIValidation.validateHandle(handle);
        }
        JNIObjectHandle result = JNIFunctions.NewWeakGlobalRef(env, handle);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * void DeleteWeakGlobalRef(JNIEnv *env, jweak obj);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void DeleteWeakGlobalRef(JNIEnvironment env, JNIObjectHandle weak) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterExceptionAllowed();
        JNIObjectRefType refType = JNIObjectHandles.getHandleType(weak);
        if (!refType.equals(JNIObjectRefType.WeakGlobal)) JNIValidation.failFatally("Invalid weak global JNI handle passed to DeleteWeakGlobalRef");
        JNIFunctions.DeleteWeakGlobalRef(env, weak);
        JNIValidation.functionExit();
    }

    /*
     * jobjectRefType GetObjectRefType(JNIEnv* env, jobject obj);
     */

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static JNIObjectRefType GetObjectRefType(JNIEnvironment env, JNIObjectHandle handle) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateObject(handle);
        JNIObjectRefType result = JNIFunctions.GetObjectRefType(env, handle);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jclass FindClass(JNIEnv *env, const char *name);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle FindClass(JNIEnvironment env, CCharPointer cname) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClassDescriptor(cname);
        JNIObjectHandle result = JNIFunctions.FindClass(env, cname);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jint RegisterNatives(JNIEnv *env, jclass clazz, const JNINativeMethod *methods, jint
     * nMethods);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnJniErr.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnEDetached.class)
    static int RegisterNatives(JNIEnvironment env, JNIObjectHandle hclazz, JNINativeMethod methods, int nmethods) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        int result = JNIFunctions.RegisterNatives(env, hclazz, methods, nmethods);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jint UnregisterNatives(JNIEnv *env, jclass clazz);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnJniErr.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnEDetached.class)
    static int UnregisterNatives(JNIEnvironment env, JNIObjectHandle hclazz) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        int result = JNIFunctions.UnregisterNatives(env, hclazz);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jmethodID GetMethodID(JNIEnv *env, jclass clazz, const char *name, const char *sig);
     *
     * jmethodID GetStaticMethodID(JNIEnv *env, jclass clazz, const char *name, const char *sig);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullPointer.class)
    static JNIMethodId GetMethodID(JNIEnvironment env, JNIObjectHandle hclazz, CCharPointer cname, CCharPointer csig) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(hclazz, false);
        JNIMethodId result = JNIFunctions.GetMethodID(env, hclazz, cname, csig);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullPointer.class)
    static JNIMethodId GetStaticMethodID(JNIEnvironment env, JNIObjectHandle hclazz, CCharPointer cname, CCharPointer csig) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(hclazz, false);
        JNIMethodId result = JNIFunctions.GetStaticMethodID(env, hclazz, cname, csig);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jfieldID GetFieldID(JNIEnv *env, jclass clazz, const char *name, const char *sig);
     *
     * jfieldID GetStaticFieldID(JNIEnv *env, jclass clazz, const char *name, const char *sig);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullPointer.class)
    static JNIFieldId GetFieldID(JNIEnvironment env, JNIObjectHandle hclazz, CCharPointer cname, CCharPointer csig) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(hclazz, false);
        JNIFieldId result = JNIFunctions.GetFieldID(env, hclazz, cname, csig);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullPointer.class)
    static JNIFieldId GetStaticFieldID(JNIEnvironment env, JNIObjectHandle hclazz, CCharPointer cname, CCharPointer csig) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(hclazz, false);
        JNIFieldId result = JNIFunctions.GetStaticFieldID(env, hclazz, cname, csig);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jobject AllocObject(JNIEnv *env, jclass clazz);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle AllocObject(JNIEnvironment env, JNIObjectHandle classHandle) throws InstantiationException {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(classHandle, false);
        JNIObjectHandle result = JNIFunctions.AllocObject(env, classHandle);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jstring NewString(JNIEnv *env, const jchar *unicodeChars, jsize len);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewString(JNIEnvironment env, CShortPointer unicode, int len) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIObjectHandle result = JNIFunctions.NewString(env, unicode, len);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jstring NewStringUTF(JNIEnv *env, const char *bytes);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewStringUTF(JNIEnvironment env, CCharPointer bytes) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIObjectHandle result = JNIFunctions.NewStringUTF(env, bytes);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jsize GetStringLength(JNIEnv *env, jstring string);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnMinusOne.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnMinusOne.class)
    static int GetStringLength(JNIEnvironment env, JNIObjectHandle hstr) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateString(hstr);
        int result = JNIFunctions.GetStringLength(env, hstr);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jsize GetStringUTFLength(JNIEnv *env, jstring string);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnMinusOne.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnMinusOne.class)
    static int GetStringUTFLength(JNIEnvironment env, JNIObjectHandle hstr) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateString(hstr);
        int result = JNIFunctions.GetStringUTFLength(env, hstr);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * const jchar * GetStringChars(JNIEnv *env, jstring string, jboolean *isCopy);
     *
     * void ReleaseStringChars(JNIEnv *env, jstring string, const jchar *chars);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullPointer.class)
    static CShortPointer GetStringChars(JNIEnvironment env, JNIObjectHandle hstr, CCharPointer isCopy) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateString(hstr);
        CShortPointer result = JNIFunctions.GetStringChars(env, hstr, isCopy);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleaseStringChars(JNIEnvironment env, JNIObjectHandle hstr, CShortPointer chars) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterExceptionAllowed();
        JNIValidation.validateString(hstr);
        JNIFunctions.ReleaseStringChars(env, hstr, chars);
        JNIValidation.functionExit();
    }

    /*
     * const char * GetStringUTFChars(JNIEnv *env, jstring string, jboolean *isCopy);
     *
     * void ReleaseStringUTFChars(JNIEnv *env, jstring string, const char *utf);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullPointer.class)
    static CCharPointer GetStringUTFChars(JNIEnvironment env, JNIObjectHandle hstr, CCharPointer isCopy) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateString(hstr);
        CCharPointer result = JNIFunctions.GetStringUTFChars(env, hstr, isCopy);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleaseStringUTFChars(JNIEnvironment env, JNIObjectHandle hstr, CCharPointer chars) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterExceptionAllowed();
        JNIValidation.validateString(hstr);
        JNIFunctions.ReleaseStringUTFChars(env, hstr, chars);
        JNIValidation.functionExit();
    }

    /*
     * const jchar * GetStringCritical(JNIEnv *env, jstring string, jboolean *isCopy);
     *
     * void ReleaseStringCritical(JNIEnv *env, jstring string, const jchar *carray);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullPointer.class)
    static CShortPointer GetStringCritical(JNIEnvironment env, JNIObjectHandle hstr, CCharPointer isCopy) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterCritical();
        JNIValidation.validateString(hstr);
        CShortPointer result = JNIFunctions.GetStringCritical(env, hstr, isCopy);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleaseStringCritical(JNIEnvironment env, JNIObjectHandle hstr, CShortPointer carray) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterCriticalExceptionAllowed();
        JNIValidation.validateString(hstr);
        JNIFunctions.ReleaseStringCritical(env, hstr, carray);
        JNIValidation.functionExit();
    }

    /*
     * void GetStringRegion(JNIEnv *env, jstring str, jsize start, jsize len, jchar *buf);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void GetStringRegion(JNIEnvironment env, JNIObjectHandle hstr, int start, int len, CShortPointer buf) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateString(hstr);
        JNIFunctions.GetStringRegion(env, hstr, start, len, buf);
        JNIValidation.functionExit();
    }

    /*
     * void GetStringUTFRegion(JNIEnv *env, jstring str, jsize start, jsize len, char *buf);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void GetStringUTFRegion(JNIEnvironment env, JNIObjectHandle hstr, int start, int len, CCharPointer buf) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateString(hstr);
        JNIFunctions.GetStringUTFRegion(env, hstr, start, len, buf);
        JNIValidation.functionExit();
    }

    /*
     * jobject NewDirectByteBuffer(JNIEnv* env, void* address, jlong capacity);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewDirectByteBuffer(JNIEnvironment env, WordPointer address, long capacity) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIObjectHandle result = JNIFunctions.NewDirectByteBuffer(env, address, capacity);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * void* GetDirectBufferAddress(JNIEnv* env, jobject buf);
     */

    @TargetClass(java.nio.Buffer.class)
    static final class Target_java_nio_Buffer {
        @Alias int capacity;
        @Alias long address;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullPointer.class)
    static WordPointer GetDirectBufferAddress(JNIEnvironment env, JNIObjectHandle handle) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        WordPointer result = JNIFunctions.GetDirectBufferAddress(env, handle);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jlong GetDirectBufferCapacity(JNIEnv* env, jobject buf);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnMinusOne.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnMinusOneLong.class)
    static long GetDirectBufferCapacity(JNIEnvironment env, JNIObjectHandle handle) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        long result = JNIFunctions.GetDirectBufferCapacity(env, handle);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * ArrayType New<PrimitiveType>Array(JNIEnv *env, jsize length);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewBooleanArray(JNIEnvironment env, int length) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIObjectHandle result = JNIFunctions.NewBooleanArray(env, length);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewByteArray(JNIEnvironment env, int length) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIObjectHandle result = JNIFunctions.NewByteArray(env, length);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewCharArray(JNIEnvironment env, int length) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIObjectHandle result = JNIFunctions.NewCharArray(env, length);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewDoubleArray(JNIEnvironment env, int length) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIObjectHandle result = JNIFunctions.NewDoubleArray(env, length);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewFloatArray(JNIEnvironment env, int length) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIObjectHandle result = JNIFunctions.NewFloatArray(env, length);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewIntArray(JNIEnvironment env, int length) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIObjectHandle result = JNIFunctions.NewIntArray(env, length);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewLongArray(JNIEnvironment env, int length) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIObjectHandle result = JNIFunctions.NewLongArray(env, length);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewShortArray(JNIEnvironment env, int length) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIObjectHandle result = JNIFunctions.NewShortArray(env, length);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jobjectArray NewObjectArray(JNIEnv *env, jsize length, jclass elementClass, jobject
     * initialElement);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewObjectArray(JNIEnvironment env, int length, JNIObjectHandle hElementClass, JNIObjectHandle hInitialElement) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIObjectHandle result = JNIFunctions.NewObjectArray(env, length, hElementClass, hInitialElement);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jobject GetObjectArrayElement(JNIEnv *env, jobjectArray array, jsize index);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle GetObjectArrayElement(JNIEnvironment env, JNIObjectHandle harray, int index) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateObjectArray(harray);
        JNIObjectHandle result = JNIFunctions.GetObjectArrayElement(env, harray, index);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * void SetObjectArrayElement(JNIEnv *env, jobjectArray array, jsize index, jobject value);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetObjectArrayElement(JNIEnvironment env, JNIObjectHandle harray, int index, JNIObjectHandle hvalue) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateObjectArray(harray);
        JNIFunctions.SetObjectArrayElement(env, harray, index, hvalue);
        JNIValidation.functionExit();
    }

    /*
     * jvoid * GetPrimitiveArrayCritical(JNIEnv *env, jarray array, jboolean *isCopy);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullPointer.class)
    static WordPointer GetPrimitiveArrayCritical(JNIEnvironment env, JNIObjectHandle harray, CCharPointer isCopy) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterCritical();
        JNIValidation.validatePrimitiveArray(harray);
        WordPointer result = JNIFunctions.GetPrimitiveArrayCritical(env, harray, isCopy);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * void ReleasePrimitiveArrayCritical(JNIEnv *env, jarray array, void *carray, jint mode);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleasePrimitiveArrayCritical(JNIEnvironment env, JNIObjectHandle harray, WordPointer carray, int mode) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterCriticalExceptionAllowed();
        JNIValidation.validatePrimitiveArray(harray);
        JNIValidation.validateWrappedArrayRelease(harray, carray, mode, true);
        JNIFunctions.ReleasePrimitiveArrayCritical(env, harray, carray, mode);
    }

    /*
     * jsize GetArrayLength(JNIEnv *env, jarray array);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnMinusOne.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnMinusOne.class)
    static int GetArrayLength(JNIEnvironment env, JNIObjectHandle harray) {
        /*
         * JNI does not specify the behavior for illegal arguments (e.g. null or non-array objects);
         * it is the JNI caller's responsibility to ensure that arguments are correct. We therefore
         * use an unchecked access to the length field. Note that the lack of check is also
         * necessary to support hybrid object layouts.
         */
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateArray(harray);
        int result = JNIFunctions.GetArrayLength(env, harray);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jboolean ExceptionCheck(JNIEnv *env);
     *
     * jthrowable ExceptionOccurred(JNIEnv *env);
     */

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static boolean ExceptionCheck(JNIEnvironment env) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterExceptionAllowed();
        boolean result = JNIFunctions.ExceptionCheck(env);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle ExceptionOccurred(JNIEnvironment env) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterExceptionAllowed();
        JNIObjectHandle result = JNIFunctions.ExceptionOccurred(env);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * void ExceptionClear(JNIEnv *env);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ExceptionClear(JNIEnvironment env) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterExceptionAllowed();
        JNIFunctions.ExceptionClear(env);
        JNIValidation.functionExit();
    }

    /*
     * void ExceptionDescribe(JNIEnv *env);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ExceptionDescribe(JNIEnvironment env) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterExceptionAllowed();
        JNIFunctions.ExceptionDescribe(env);
        JNIValidation.functionExit();
    }

    /*
     * jint Throw(JNIEnv *env, jthrowable obj);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnZero.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnEDetached.class)
    static int Throw(JNIEnvironment env, JNIObjectHandle handle) throws Throwable {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateObject(handle);
        JNIValidation.validateThrowableClass(handle);
        int result = JNIFunctions.Throw(env, handle);
        JNIValidation.functionExit();
        return result;
    }

    interface NewObjectWithObjectArrayArgFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        JNIObjectHandle invoke(JNIEnvironment env, JNIObjectHandle clazz, JNIMethodId ctor, JNIValue array);
    }

    /*
     * jint ThrowNew(JNIEnv *env, jclass clazz, const char *message);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnZero.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnEDetached.class)
    static int ThrowNew(JNIEnvironment env, JNIObjectHandle clazzHandle, CCharPointer message) throws Throwable {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(clazzHandle, false);
        JNIValidation.validateThrowableClass(clazzHandle);
        int result = JNIFunctions.ThrowNew(env, clazzHandle, message);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * void FatalError(JNIEnv *env, const char *msg);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    @NeverInline("Access of caller frame.")
    static void FatalError(JNIEnvironment env, CCharPointer message) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIFunctions.FatalError(env, message);
        JNIValidation.functionExit();
    }

    /*
     * jint GetJavaVM(JNIEnv *env, JavaVM **vm);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnJniErr.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnEDetached.class)
    static int GetJavaVM(JNIEnvironment env, JNIJavaVMPointer vm) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        int result = GetJavaVM(env, vm);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jfieldID FromReflectedField(JNIEnv *env, jobject field);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullPointer.class)
    static JNIFieldId FromReflectedField(JNIEnvironment env, JNIObjectHandle fieldHandle) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateObject(fieldHandle);
        JNIFieldId result = JNIFunctions.FromReflectedField(env, fieldHandle);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jobject ToReflectedField(JNIEnv *env, jclass cls, jfieldID fieldID, jboolean isStatic);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle ToReflectedField(JNIEnvironment env, JNIObjectHandle classHandle, JNIFieldId fieldId) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(classHandle, false);
        JNIObjectHandle result = JNIFunctions.ToReflectedField(env, classHandle, fieldId);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jmethodID FromReflectedMethod(JNIEnv *env, jobject method);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullPointer.class)
    static JNIMethodId FromReflectedMethod(JNIEnvironment env, JNIObjectHandle methodHandle) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateObject(methodHandle);
        JNIMethodId result = JNIFunctions.FromReflectedMethod(env, methodHandle);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jobject ToReflectedMethod(JNIEnv *env, jclass cls, jmethodID methodID, jboolean isStatic);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullPointer.class)
    static JNIObjectHandle ToReflectedMethod(JNIEnvironment env, JNIObjectHandle classHandle, JNIMethodId methodId, boolean isStatic) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateCall(classHandle, methodId);
        JNIObjectHandle result = JNIFunctions.ToReflectedMethod(env, classHandle, methodId, isStatic);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jint MonitorEnter(JNIEnv *env, jobject obj);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnJniErr.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnEDetached.class)
    static int MonitorEnter(JNIEnvironment env, JNIObjectHandle handle) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateObject(handle);
        int result = JNIFunctions.MonitorEnter(env, handle);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jint MonitorExit(JNIEnv *env, jobject obj);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnJniErr.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnEDetached.class)
    static int MonitorExit(JNIEnvironment env, JNIObjectHandle handle) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterExceptionAllowed();
        JNIValidation.validateObject(handle);
        int result = JNIFunctions.MonitorExit(env, handle);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jobject (JNICALL *GetModule) (JNIEnv* env, jclass clazz);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle GetModule(JNIEnvironment env, JNIObjectHandle handle) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIObjectHandle result = JNIFunctions.GetModule(env, handle);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jclass DefineClass(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize
     * bufLen);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle DefineClass(JNIEnvironment env, CCharPointer cname, JNIObjectHandle loader, CCharPointer buf, int bufLen) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateObject(loader);
        JNIObjectHandle result = JNIFunctions.DefineClass(env, cname, loader, buf, bufLen);
        JNIValidation.functionExit();
        return result;
    }

    /*
     * jboolean (JNICALL *IsVirtualThread) (JNIEnv *env, jobject obj);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnFalse.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static boolean IsVirtualThread(JNIEnvironment env, JNIObjectHandle handle) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        boolean result = JNIFunctions.IsVirtualThread(env, handle);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static PointerBase GetBooleanArrayElements(JNIEnvironment env, JNIObjectHandle array, CCharPointer isCopy) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.BOOLEAN);
        PointerBase result = JNIFunctions.GetBooleanArrayElements(env, array, isCopy);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static PointerBase GetByteArrayElements(JNIEnvironment env, JNIObjectHandle array, CCharPointer isCopy) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.BYTE);
        PointerBase result = JNIFunctions.GetByteArrayElements(env, array, isCopy);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static PointerBase GetShortArrayElements(JNIEnvironment env, JNIObjectHandle array, CCharPointer isCopy) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.SHORT);
        PointerBase result = JNIFunctions.GetShortArrayElements(env, array, isCopy);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static PointerBase GetCharArrayElements(JNIEnvironment env, JNIObjectHandle array, CCharPointer isCopy) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.CHAR);
        PointerBase result = JNIFunctions.GetCharArrayElements(env, array, isCopy);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static PointerBase GetIntArrayElements(JNIEnvironment env, JNIObjectHandle array, CCharPointer isCopy) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.INT);
        PointerBase result = JNIFunctions.GetIntArrayElements(env, array, isCopy);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static PointerBase GetLongArrayElements(JNIEnvironment env, JNIObjectHandle array, CCharPointer isCopy) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.LONG);
        PointerBase result = JNIFunctions.GetLongArrayElements(env, array, isCopy);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static PointerBase GetFloatArrayElements(JNIEnvironment env, JNIObjectHandle array, CCharPointer isCopy) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.FLOAT);
        PointerBase result = JNIFunctions.GetFloatArrayElements(env, array, isCopy);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static PointerBase GetDoubleArrayElements(JNIEnvironment env, JNIObjectHandle array, CCharPointer isCopy) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.DOUBLE);
        PointerBase result = JNIFunctions.GetDoubleArrayElements(env, array, isCopy);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleaseBooleanArrayElements(JNIEnvironment env, JNIObjectHandle array, PointerBase elements, int mode) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterExceptionAllowed();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.BOOLEAN);
        JNIValidation.validateWrappedArrayRelease(array, (WordPointer) elements, mode, false);
        JNIFunctions.ReleaseBooleanArrayElements(env, array, elements, mode);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleaseByteArrayElements(JNIEnvironment env, JNIObjectHandle array, PointerBase elements, int mode) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterExceptionAllowed();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.BYTE);
        JNIValidation.validateWrappedArrayRelease(array, (WordPointer) elements, mode, false);
        JNIFunctions.ReleaseByteArrayElements(env, array, elements, mode);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleaseShortArrayElements(JNIEnvironment env, JNIObjectHandle array, PointerBase elements, int mode) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterExceptionAllowed();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.SHORT);
        JNIValidation.validateWrappedArrayRelease(array, (WordPointer) elements, mode, false);
        JNIFunctions.ReleaseShortArrayElements(env, array, elements, mode);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleaseCharArrayElements(JNIEnvironment env, JNIObjectHandle array, PointerBase elements, int mode) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterExceptionAllowed();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.CHAR);
        JNIValidation.validateWrappedArrayRelease(array, (WordPointer) elements, mode, false);
        JNIFunctions.ReleaseCharArrayElements(env, array, elements, mode);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleaseIntArrayElements(JNIEnvironment env, JNIObjectHandle array, PointerBase elements, int mode) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterExceptionAllowed();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.INT);
        JNIValidation.validateWrappedArrayRelease(array, (WordPointer) elements, mode, false);
        JNIFunctions.ReleaseIntArrayElements(env, array, elements, mode);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleaseLongArrayElements(JNIEnvironment env, JNIObjectHandle array, PointerBase elements, int mode) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterExceptionAllowed();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.LONG);
        JNIValidation.validateWrappedArrayRelease(array, (WordPointer) elements, mode, false);
        JNIFunctions.ReleaseLongArrayElements(env, array, elements, mode);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleaseFloatArrayElements(JNIEnvironment env, JNIObjectHandle array, PointerBase elements, int mode) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterExceptionAllowed();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.FLOAT);
        JNIValidation.validateWrappedArrayRelease(array, (WordPointer) elements, mode, false);
        JNIFunctions.ReleaseFloatArrayElements(env, array, elements, mode);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleaseDoubleArrayElements(JNIEnvironment env, JNIObjectHandle array, PointerBase elements, int mode) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnterExceptionAllowed();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.DOUBLE);
        JNIValidation.validateWrappedArrayRelease(array, (WordPointer) elements, mode, false);
        JNIFunctions.ReleaseDoubleArrayElements(env, array, elements, mode);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void GetBooleanArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, PointerBase buffer) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.BOOLEAN);
        JNIFunctions.GetBooleanArrayRegion(env, array, start, count, buffer);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void GetByteArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, PointerBase buffer) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.BYTE);
        JNIFunctions.GetByteArrayRegion(env, array, start, count, buffer);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void GetShortArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, PointerBase buffer) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.SHORT);
        JNIFunctions.GetShortArrayRegion(env, array, start, count, buffer);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void GetCharArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, PointerBase buffer) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.CHAR);
        JNIFunctions.GetCharArrayRegion(env, array, start, count, buffer);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void GetIntArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, PointerBase buffer) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.INT);
        JNIFunctions.GetIntArrayRegion(env, array, start, count, buffer);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void GetLongArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, PointerBase buffer) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.LONG);
        JNIFunctions.GetLongArrayRegion(env, array, start, count, buffer);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void GetFloatArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, PointerBase buffer) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.FLOAT);
        JNIFunctions.GetFloatArrayRegion(env, array, start, count, buffer);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void GetDoubleArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, PointerBase buffer) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.DOUBLE);
        JNIFunctions.GetDoubleArrayRegion(env, array, start, count, buffer);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetBooleanArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, @CConst PointerBase buffer) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.BOOLEAN);
        JNIFunctions.SetBooleanArrayRegion(env, array, start, count, buffer);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetByteArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, @CConst PointerBase buffer) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.BYTE);
        JNIFunctions.SetByteArrayRegion(env, array, start, count, buffer);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetShortArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, @CConst PointerBase buffer) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.SHORT);
        JNIFunctions.SetShortArrayRegion(env, array, start, count, buffer);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetCharArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, @CConst PointerBase buffer) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.CHAR);
        JNIFunctions.SetCharArrayRegion(env, array, start, count, buffer);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetIntArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, @CConst PointerBase buffer) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.INT);
        JNIFunctions.SetIntArrayRegion(env, array, start, count, buffer);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetLongArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, @CConst PointerBase buffer) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.LONG);
        JNIFunctions.SetLongArrayRegion(env, array, start, count, buffer);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetFloatArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, @CConst PointerBase buffer) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.FLOAT);
        JNIFunctions.SetFloatArrayRegion(env, array, start, count, buffer);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetDoubleArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, @CConst PointerBase buffer) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validatePrimitiveArrayType(array, JNIPrimitiveType.DOUBLE);
        JNIFunctions.SetDoubleArrayRegion(env, array, start, count, buffer);
        JNIValidation.functionExit();
    }

    /* It is not correct to return null when an exception is thrown, see GR-54276. */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static JNIObjectHandle GetObjectField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateInstanceField(obj,
                fieldId,
                JNIPrimitiveType.OBJECT, false);
        JNIObjectHandle result = JNIFunctions.GetObjectField(env, obj, fieldId);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static boolean GetBooleanField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateInstanceField(obj,
                fieldId,
                JNIPrimitiveType.BOOLEAN, false);
        boolean result = JNIFunctions.GetBooleanField(env, obj, fieldId);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static byte GetByteField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateInstanceField(obj,
                fieldId,
                JNIPrimitiveType.BYTE, false);
        byte result = JNIFunctions.GetByteField(env, obj, fieldId);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static short GetShortField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateInstanceField(obj,
                fieldId,
                JNIPrimitiveType.SHORT, false);
        short result = JNIFunctions.GetShortField(env, obj, fieldId);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static char GetCharField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateInstanceField(obj,
                fieldId,
                JNIPrimitiveType.SHORT, false);
        char result = JNIFunctions.GetCharField(env, obj, fieldId);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static int GetIntField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateInstanceField(obj,
                fieldId,
                JNIPrimitiveType.SHORT, false);
        int result = JNIFunctions.GetIntField(env, obj, fieldId);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static long GetLongField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateInstanceField(obj,
                fieldId,
                JNIPrimitiveType.LONG, false);
        long result = JNIFunctions.GetLongField(env, obj, fieldId);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static float GetFloatField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateInstanceField(obj,
                fieldId,
                JNIPrimitiveType.FLOAT, false);
        float result = JNIFunctions.GetFloatField(env, obj, fieldId);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static double GetDoubleField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateInstanceField(obj,
                fieldId,
                JNIPrimitiveType.DOUBLE, false);
        double result = JNIFunctions.GetDoubleField(env, obj, fieldId);
        JNIValidation.functionExit();
        return result;
    }

    /* It is not correct to return null when an exception is thrown, see GR-54276. */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static JNIObjectHandle GetStaticObjectField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(clazz, false);
        JNIValidation.validateStaticField(clazz, fieldId, JNIPrimitiveType.OBJECT, false);
        JNIObjectHandle result = JNIFunctions.GetStaticObjectField(env, clazz, fieldId);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static boolean GetStaticBooleanField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(clazz, false);
        JNIValidation.validateStaticField(clazz, fieldId, JNIPrimitiveType.OBJECT, false);
        boolean result = JNIFunctions.GetStaticBooleanField(env, clazz, fieldId);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static byte GetStaticByteField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(clazz, false);
        JNIValidation.validateStaticField(clazz, fieldId, JNIPrimitiveType.BYTE, false);
        byte result = JNIFunctions.GetStaticByteField(env, clazz, fieldId);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static short GetStaticShortField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(clazz, false);
        JNIValidation.validateStaticField(clazz, fieldId, JNIPrimitiveType.SHORT, false);
        short result = JNIFunctions.GetStaticShortField(env, clazz, fieldId);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static char GetStaticCharField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(clazz, false);
        JNIValidation.validateStaticField(clazz, fieldId, JNIPrimitiveType.CHAR, false);
        char result = JNIFunctions.GetStaticCharField(env, clazz, fieldId);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static int GetStaticIntField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(clazz, false);
        JNIValidation.validateStaticField(clazz, fieldId, JNIPrimitiveType.INT, false);
        int result = JNIFunctions.GetStaticIntField(env, clazz, fieldId);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static long GetStaticLongField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(clazz, false);
        JNIValidation.validateStaticField(clazz, fieldId, JNIPrimitiveType.LONG, false);
        long result = JNIFunctions.GetStaticLongField(env, clazz, fieldId);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static float GetStaticFloatField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(clazz, false);
        JNIValidation.validateStaticField(clazz, fieldId, JNIPrimitiveType.FLOAT, false);
        float result = JNIFunctions.GetStaticFloatField(env, clazz, fieldId);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static double GetStaticDoubleField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(clazz, false);
        JNIValidation.validateStaticField(clazz, fieldId, JNIPrimitiveType.DOUBLE, false);
        double result = JNIFunctions.GetStaticDoubleField(env, clazz, fieldId);
        JNIValidation.functionExit();
        return result;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetObjectField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId, JNIObjectHandle value) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateInstanceField(obj, fieldId, JNIPrimitiveType.OBJECT, true);
        JNIFunctions.SetObjectField(env, obj, fieldId, value);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetBooleanField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId, boolean value) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateInstanceField(obj, fieldId, JNIPrimitiveType.BOOLEAN, true);
        JNIFunctions.SetBooleanField(env, obj, fieldId, value);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetByteField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId, byte value) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateInstanceField(obj, fieldId, JNIPrimitiveType.BYTE, true);
        JNIFunctions.SetByteField(env, obj, fieldId, value);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetShortField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId, short value) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateInstanceField(obj, fieldId, JNIPrimitiveType.SHORT, true);
        JNIFunctions.SetShortField(env, obj, fieldId, value);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetCharField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId, char value) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateInstanceField(obj, fieldId, JNIPrimitiveType.CHAR, true);
        JNIFunctions.SetCharField(env, obj, fieldId, value);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetIntField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId, int value) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateInstanceField(obj, fieldId, JNIPrimitiveType.INT, true);
        JNIFunctions.SetIntField(env, obj, fieldId, value);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetLongField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId, long value) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateInstanceField(obj, fieldId, JNIPrimitiveType.LONG, true);
        JNIFunctions.SetLongField(env, obj, fieldId, value);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetFloatField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId, float value) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateInstanceField(obj, fieldId, JNIPrimitiveType.FLOAT, true);
        JNIFunctions.SetFloatField(env, obj, fieldId, value);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetDoubleField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId, double value) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateInstanceField(obj, fieldId, JNIPrimitiveType.DOUBLE, true);
        JNIFunctions.SetDoubleField(env, obj, fieldId, value);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetStaticObjectField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId, JNIObjectHandle value) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(clazz, false);
        JNIValidation.validateStaticField(clazz, fieldId, JNIPrimitiveType.OBJECT, true);
        JNIFunctions.SetStaticObjectField(env, clazz, fieldId, value);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetStaticBooleanField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId, boolean value) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(clazz, false);
        JNIValidation.validateStaticField(clazz, fieldId, JNIPrimitiveType.BOOLEAN, true);
        JNIFunctions.SetStaticBooleanField(env, clazz, fieldId, value);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetStaticByteField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId, byte value) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(clazz, false);
        JNIValidation.validateStaticField(clazz, fieldId, JNIPrimitiveType.BYTE, true);
        JNIFunctions.SetStaticByteField(env, clazz, fieldId, value);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetStaticShortField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId, short value) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(clazz, false);
        JNIValidation.validateStaticField(clazz, fieldId, JNIPrimitiveType.SHORT, true);
        JNIFunctions.SetStaticShortField(env, clazz, fieldId, value);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetStaticCharField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId, char value) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(clazz, false);
        JNIValidation.validateStaticField(clazz, fieldId, JNIPrimitiveType.CHAR, true);
        JNIFunctions.SetStaticCharField(env, clazz, fieldId, value);
        JNIValidation.functionExit();
    }


    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetStaticIntField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId, int value) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(clazz, false);
        JNIValidation.validateStaticField(clazz, fieldId, JNIPrimitiveType.INT, true);
        JNIFunctions.SetStaticIntField(env, clazz, fieldId, value);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetStaticLongField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId, long value) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(clazz, false);
        JNIValidation.validateStaticField(clazz, fieldId, JNIPrimitiveType.LONG, true);
        JNIFunctions.SetStaticLongField(env, clazz, fieldId, value);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetStaticFloatField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId, float value) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(clazz, false);
        JNIValidation.validateStaticField(clazz, fieldId, JNIPrimitiveType.FLOAT, true);
        JNIFunctions.SetStaticFloatField(env, clazz, fieldId, value);
        JNIValidation.functionExit();
    }

    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetStaticDoubleField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId, double value) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateClass(clazz, false);
        JNIValidation.validateStaticField(clazz, fieldId, JNIPrimitiveType.DOUBLE, true);
        JNIFunctions.SetStaticDoubleField(env, clazz, fieldId, value);
        JNIValidation.functionExit();
    }

    /*
     * jlong GetStringUTFLengthAsLong(JNIEnv *env, jstring string);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnMinusOne.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnMinusOneLong.class)
    static long GetStringUTFLengthAsLong(JNIEnvironment env, JNIObjectHandle hstr) {
        JNIValidation.validateJNIEnv(env);
        JNIValidation.validateThread();
        JNIValidation.functionEnter();
        JNIValidation.validateString(hstr);
        long result = JNIFunctions.GetStringUTFLengthAsLong(env, hstr);
        JNIValidation.functionExit();
        return result;
    }

    // Checkstyle: resume

    /**
     * Helper code for JNI functions. This is an inner class because the outer methods must match
     * JNI functions.
     */
    public static class Support {
        static final CGlobalData<CCharPointer> JNIENV_ENTER_FAIL_FATALLY_MESSAGE = CGlobalDataFactory.createCString(
                "A JNI call failed to enter the isolate via its JNI environment argument. The environment might be invalid or no longer exists.");

        /**
         * We use one of the approaches below when mapping our internal {@link CEntryPointErrors} to
         * {@link JNIErrors}. Which approach is used depends on the method arguments and the value
         * of {@link SubstrateOptions#JNIEnhancedErrorCodes}.
         * <ul>
         * <li>Map all internal errors to {@link JNIErrors#JNI_ERR()}. This is needed for some
         * methods to maximize compatibility with HotSpot.</li>
         * <li>Map internal errors to roughly matching standard JNI errors. Internal errors that
         * don't have a counterpart will be mapped to {@link JNIErrors#JNI_ERR()}.</li>
         * <li>Map all internal errors to non-standard JNI errors.</li>
         * </ul>
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        static int convertCEntryPointErrorToJNIError(int code, boolean mapToMatchingStandardJNIError) {
            if (code == CEntryPointErrors.NO_ERROR) {
                return JNIErrors.JNI_OK();
            }

            if (SubstrateOptions.JNIEnhancedErrorCodes.getValue()) {
                /*
                 * Return a non-standard JNI error so that callers such as libgraal can figure out
                 * what went wrong.
                 */
                int result = -1000000000 - code;
                if (result == JNIErrors.JNI_OK() || result >= -100) {
                    return JNIErrors.JNI_ERR(); // non-negative or potential actual JNI error
                }
                return result;
            }

            if (mapToMatchingStandardJNIError) {
                /* Map some internal errors to standard JNI errors. */
                switch (code) {
                    case CEntryPointErrors.ALLOCATION_FAILED:
                    case CEntryPointErrors.MAP_HEAP_FAILED:
                    case CEntryPointErrors.RESERVE_ADDRESS_SPACE_FAILED:
                    case CEntryPointErrors.INSUFFICIENT_ADDRESS_SPACE:
                        return JNIErrors.JNI_ENOMEM();
                    case CEntryPointErrors.SINGLE_ISOLATE_ALREADY_CREATED:
                        return JNIErrors.JNI_EEXIST();
                }
            }

            return JNIErrors.JNI_ERR();
        }

        @Uninterruptible(reason = "Prevent safepoints until everything is set up for the fatal error printing.", calleeMustBe = false)
        @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Must not allocate in fatal error handling.")
        static void fatalError(CodePointer callerIP, Pointer callerSP, String message) {
            SafepointBehavior.preventSafepoints();
            StackOverflowCheck.singleton().disableStackOverflowChecksForFatalError();

            LogHandler logHandler = ImageSingletons.lookup(LogHandler.class);
            Log log = Log.enterFatalContext(logHandler, callerIP, message, null);
            if (log != null) {
                try {
                    log.string("Fatal error reported via JNI: ").string(message).newline();
                    SubstrateDiagnostics.printFatalError(log, callerSP, callerIP);
                } catch (Throwable ignored) {
                    /*
                     * Ignore exceptions reported during error reporting, we are going to exit
                     * anyway.
                     */
                }
            }
            logHandler.fatalError();
        }

        /*
         * Make sure the given handle identifies a direct buffer.
         *
         * The object is considered "a buffer" if it implements java.nio.Buffer. The buffer is
         * considered "direct" if it implements sun.nio.ch.DirectBuffer.
         */
        @SuppressFBWarnings(value = "BC_IMPOSSIBLE_INSTANCEOF", justification = "FindBugs does not understand substitution classes")
        static Target_java_nio_Buffer directBufferFromJNIHandle(JNIObjectHandle handle) {
            Object obj = JNIObjectHandles.getObject(handle);
            if (obj instanceof Target_java_nio_Buffer && obj instanceof sun.nio.ch.DirectBuffer) {
                return (Target_java_nio_Buffer) obj;
            } else {
                return null;
            }
        }
    }

    static final CGlobalData<CCharPointer> UNIMPLEMENTED_UNATTACHED_ERROR_MESSAGE = CGlobalDataFactory.createCString(
            "An unimplemented JNI function was called in a way or at a time when no error reporting could be performed.");

    static class JNIEnvUnimplementedPrologue implements CEntryPointOptions.Prologue {
        @Uninterruptible(reason = "prologue")
        static void enter(JNIEnvironment env) {
            int error = CEntryPointActions.enter((IsolateThread) env);
            if (error != CEntryPointErrors.NO_ERROR) {
                CEntryPointActions.failFatally(error, UNIMPLEMENTED_UNATTACHED_ERROR_MESSAGE.get());
            }
        }
    }

    public static class UnimplementedWithJNIEnvArgument {
        /**
         * Stub for unimplemented JNI functionality with a JNIEnv argument.
         */
        @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
        @CEntryPointOptions(prologue = JNIEnvUnimplementedPrologue.class)
        static int unimplemented(JNIEnvironment env) {
            /*
             * We do not catch and preserve this exception like we normally would with JNI because
             * it is unlikely that native code checks for a pending exception after each JNI
             * function call. As a result, the caller would likely crash at a different location or
             * encounter the pending exception in an unrelated context, making this unnecessarily
             * difficult to debug.
             */
            return JNIFunctions.UnimplementedWithJNIEnvArgument.unimplemented(env);
        }
    }

    static class JNIJavaVMUnimplementedPrologue implements CEntryPointOptions.Prologue {
        @Uninterruptible(reason = "prologue")
        static void enter(JNIJavaVM vm) {
            int error = CEntryPointActions.enterAttachThread(vm.getFunctions().getIsolate(), false, true);
            if (error != CEntryPointErrors.NO_ERROR) {
                CEntryPointActions.failFatally(error, UNIMPLEMENTED_UNATTACHED_ERROR_MESSAGE.get());
            }
        }
    }

    public static class UnimplementedWithJavaVMArgument {
        /**
         * Stub for unimplemented JNI functionality with a JavaVM argument.
         */
        @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
        @CEntryPointOptions(prologue = JNIJavaVMUnimplementedPrologue.class)
        static int unimplemented(JNIJavaVM vm) {
            return JNIFunctions.UnimplementedWithJavaVMArgument.unimplemented(vm);
        }
    }

    enum JNIPrimitiveType {
        OBJECT,
        BOOLEAN,
        BYTE,
        CHAR,
        SHORT,
        INT,
        LONG,
        FLOAT,
        DOUBLE;
    }

    public static class JNIValidation {
        private static final String JVM_SIGNATURE_CLASS = "L";
        private static final String JVM_SIGNATURE_ENDCLASS = ";";
        private static final int JNI_COMMIT = 1;
        private static final int JNI_ABORT = 2;
        static void validateJNIEnv(JNIEnvironment env) {
            if (env.isNull()) {
                failFatally("JNIEnv is null");
            }
        }
        static void validateThread() {
            IsolateThread thread = CurrentIsolate.getCurrentThread();
            if (!VMThreads.singleton().verifyIsCurrentThread(thread) || !VMThreads.isAttached(thread)) {
                failFatally("A call from native code to Java code provided the wrong JNI environment or the wrong IsolateThread. " +
                        "The JNI environment / IsolateThread is a thread-local data structure and must not be shared between threads.");
            }
        }
        static void functionEnter() {}
        static void functionEnterExceptionAllowed() {}
        static void functionEnterCritical() {}
        static void functionEnterCriticalExceptionAllowed() {}
        static void functionExit() {}

        static void validateObject(JNIObjectHandle obj) {
            if (obj.equal(Word.nullPointer())) {
                failFatally("Object handle is null");
            }
            if (!validateHandle(obj)) failFatally("Bad ref to jni");
        }

        static boolean validateHandle(JNIObjectHandle handle) {
            JNIObjectRefType handleType = JNIObjectHandles.getHandleType(handle);
            return !handle.equal(Word.nullPointer()) && !handleType.equals(JNIObjectRefType.Invalid);
        }

        static void validateClassDescriptor(CCharPointer name) {
            if (name.isNull()) return;
            String desc = CTypeConversion.toJavaString(name);
            int len = desc.length();
            if (len >= 2 && desc.startsWith(JVM_SIGNATURE_CLASS) && desc.endsWith(JVM_SIGNATURE_ENDCLASS)) reportWarning("Bad class descriptor");

            byte[] bytes = new byte[len];
            for (int i = 0; i < len; i++) {
                bytes[i] = name.read(i);
            }
            if(!isValidUtf8(bytes)) failFatally("Non-UTF8 class name");
        }

        static void validateClass(JNIObjectHandle clazz, boolean allowPrimitive) {
            if (!validateHandle(clazz)) failFatally("Received null Class");
            Object o = JNIObjectHandles.getObject(clazz);
            if (!(o instanceof Class<?>)) failFatally("JNI class handle does not refer to a java.lang.Class");
            Class<?> cls = (Class<?>) o;
            if (!allowPrimitive && Objects.requireNonNull(cls).isPrimitive()) failFatally("Primitive class not allowed here");
        }

        static void validateThrowableClass(JNIObjectHandle clazz) {
            if (clazz.equal(Word.nullPointer())) failFatally("Class is not allowed to be Null");
            Object o = JNIObjectHandles.getObject(clazz);
            if (!(o instanceof Class<?>) || !Throwable.class.isAssignableFrom((Class<?>) o)) failFatally("Class is not a Throwable Class");
        }

        static void validateCall(JNIObjectHandle clazz, JNIMethodId method) {
            validateJNIObjectHandle(clazz);
            if (method.isNull()) failFatally("Method does not exist");

            if (!clazz.equal(Word.nullPointer())) {
                validateClass(clazz, false);
            }
        }

        static void validateStaticField(JNIObjectHandle clazz, JNIFieldId field, JNIPrimitiveType expectedType, boolean isSetter) {
            if (clazz.equal(Word.nullPointer())) {
                failFatally("Static field access on null class");
            }

            if (field.isNull()) {
                failFatally("Static field ID is null");
            }

            validateClass(clazz, false);

            if (expectedType == null) {
                failFatally("Expected field type is null");
            }
        }

        static void validateInstanceField(JNIObjectHandle obj, JNIFieldId field, JNIPrimitiveType expectedType, boolean isSetter) {
            if (obj.equal(Word.nullPointer())) {
                failFatally("Instance field access on null object");
            }

            if (field.isNull()) {
                failFatally("Instance field ID is null");
            }

            validateJNIObjectHandle(obj);

            if (expectedType == null) {
                failFatally("Expected field type is null");
            }
        }
        static void validateString(JNIObjectHandle str) {
            if (str.equal(Word.nullPointer())) {
                failFatally("String handle is null");
            }
            Object o = JNIObjectHandles.getObject(str);
            if (!(o instanceof String)) {
                failFatally("Not a String");
            }
            validateJNIObjectHandle(str);
        }
        static void validateArray(JNIObjectHandle array) {
            if (array.equal(Word.nullPointer())) {
                failFatally("String handle is null");
            }
            Object o = JNIObjectHandles.getObject(array);
            if (o != null && !o.getClass().isArray()) {
                failFatally("Not an array");
            }
        }
        static void validatePrimitiveArray(JNIObjectHandle array) {
            validateArray(array);
            Object o = JNIObjectHandles.getObject(array);
            if (o != null && !o.getClass().getComponentType().isPrimitive()) {
                failFatally("Not a primitive array");
            }
        }

        static void validatePrimitiveArrayType(JNIObjectHandle array, JNIPrimitiveType type) {
            validatePrimitiveArray(array);
            if (type == null) failFatally("Expected primitive type is null");
            Class<?> arrType = Objects.requireNonNull(JNIObjectHandles.getObject(array)).getClass().getComponentType();

            if (arrType != toJavaPrimitiveClass(type)) {
                failFatally("Element type mismatch");
            }
        }

        static void validateObjectArray(JNIObjectHandle array) {
            validateArray(array);
            Class<?> arrType = Objects.requireNonNull(JNIObjectHandles.getObject(array)).getClass().getComponentType();

            if (arrType.isPrimitive()) {
                failFatally("Expected an object array");
            }
        }

        static void validateWrappedArrayRelease(JNIObjectHandle harray, WordPointer carray, int mode, boolean isCritical) {
            validateJNIObjectHandle(harray);

            if (carray.isNull()) {
                failFatally("Elements pointer is null");
            }

            switch (mode) {
                case 0: // JNI_COPY_BACK_AND_FREE (default)
                case JNI_COMMIT:
                case JNI_ABORT:
                    break;
                default:
                    failFatally("Unrecognized array release mode: " + mode);
            }
            if (isCritical && mode == JNI_COMMIT) {
                reportWarning("JNI_COMMIT used with ReleasePrimitiveArrayCritical");
            }
        }

        static void failFatally(String msg) {
            throw VMError.shouldNotReachHere("JNI validation failed: " + msg);
        }

        static void reportWarning(String msg) {
            Log.log().string("JNI WARNING: ").string(msg).newline();
        }

        static void validateJNIObjectHandle(JNIObjectHandle handle) {
            if (handle.equal(Word.nullPointer())) {
                return; // null is allowed
            }
            JNIObjectRefType type = JNIObjectHandles.getHandleType(handle);

            if (type == JNIObjectRefType.Invalid) {
                failFatally("Invalid JNI object handle");
            }

            Object obj = null;
            try {
                obj = JNIObjectHandles.getObject(handle);
            } catch (IllegalArgumentException e) {
                failFatally("JNI handle does not resolve to a valid object");
            }

            if (obj == null) {
                // Non-null handle resolving to null is illegal
                failFatally("JNI handle resolves to null object");
            }
        }

        private static Class<?> toJavaPrimitiveClass(JNIPrimitiveType type) {
            return switch (type) {
                case BOOLEAN -> boolean.class;
                case BYTE -> byte.class;
                case CHAR -> char.class;
                case SHORT -> short.class;
                case INT -> int.class;
                case LONG -> long.class;
                case FLOAT -> float.class;
                case DOUBLE -> double.class;
                default -> {
                    throw new RuntimeException("Unsupported JNI primitive type: " + type);
                }
            };
        }

        private static boolean isValidUtf8(byte[] bytes) {
            CharsetDecoder decoder = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            try {
                decoder.decode(ByteBuffer.wrap(bytes));
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}