/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
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
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.JavaMemoryUtil;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.StaticFieldsSupport;
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
import com.oracle.svm.core.hub.PredefinedClassesSupport;
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
import com.oracle.svm.core.jni.headers.JNIVersionJDK19OrLater;
import com.oracle.svm.core.jni.headers.JNIVersionJDK20OrLater;
import com.oracle.svm.core.jni.headers.JNIVersionJDK21OrLater;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.monitor.MonitorInflationCause;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.VMThreads.SafepointBehavior;
import com.oracle.svm.core.thread.VirtualThreads;
import com.oracle.svm.core.util.ArrayUtil;
import com.oracle.svm.core.util.Utf8;
import com.oracle.svm.core.util.VMError;

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
public final class JNIFunctions {
    private static final Unsafe U = Unsafe.getUnsafe();

    // Checkstyle: stop

    /*
     * jint GetVersion(JNIEnv *env);
     */

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = CEntryPointOptions.NoPrologue.class, epilogue = CEntryPointOptions.NoEpilogue.class)
    @Uninterruptible(reason = "No need to enter the isolate and also no way to report errors if unable to.")
    static int GetVersion(JNIEnvironment env) {
        return JavaVersionUtil.JAVA_SPEC >= 21 ? JNIVersionJDK21OrLater.JNI_VERSION_21()
                        : JavaVersionUtil.JAVA_SPEC >= 20 ? JNIVersionJDK20OrLater.JNI_VERSION_20()
                                        : JavaVersionUtil.JAVA_SPEC >= 19 ? JNIVersionJDK19OrLater.JNI_VERSION_19()
                                                        : JNIVersion.JNI_VERSION_10();
    }

    /*
     * jobject NewLocalRef(JNIEnv *env, jobject ref);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewLocalRef(JNIEnvironment env, JNIObjectHandle ref) {
        return JNIObjectHandles.newLocalRef(ref);
    }

    /*
     * void DeleteLocalRef(JNIEnv *env, jobject localRef);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void DeleteLocalRef(JNIEnvironment env, JNIObjectHandle localRef) {
        JNIObjectHandles.deleteLocalRef(localRef);
    }

    /*
     * jint EnsureLocalCapacity(JNIEnv *env, jint capacity);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnJniErr.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnEDetached.class)
    static int EnsureLocalCapacity(JNIEnvironment env, int capacity) {
        if (capacity < 0) {
            return JNIErrors.JNI_ERR();
        }
        JNIObjectHandles.ensureLocalCapacity(capacity);
        return 0;
    }

    /*
     * jint PushLocalFrame(JNIEnv *env, jint capacity);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnJniErr.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnEDetached.class)
    static int PushLocalFrame(JNIEnvironment env, int capacity) {
        if (capacity < 0) {
            return JNIErrors.JNI_ERR();
        }
        JNIObjectHandles.pushLocalFrame(capacity);
        return 0;
    }

    /*
     * jobject PopLocalFrame(JNIEnv *env, jobject result);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle PopLocalFrame(JNIEnvironment env, JNIObjectHandle handle) {
        Object obj = JNIObjectHandles.getObject(handle);
        JNIObjectHandles.popLocalFrame();
        return JNIObjectHandles.createLocal(obj);
    }

    /*
     * jboolean IsSameObject(JNIEnv *env, jobject ref1, jobject ref2);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnFalse.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static boolean IsSameObject(JNIEnvironment env, JNIObjectHandle ref1, JNIObjectHandle ref2) {
        Object obj1 = JNIObjectHandles.getObject(ref1);
        Object obj2 = JNIObjectHandles.getObject(ref2);
        return obj1 == obj2;
    }

    /*
     * jboolean IsInstanceOf(JNIEnv *env, jobject obj, jclass clazz);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnFalse.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static boolean IsInstanceOf(JNIEnvironment env, JNIObjectHandle obj, JNIObjectHandle clazz) {
        Object o = JNIObjectHandles.getObject(obj);
        if (o == null) {
            // JNI specifies: "A NULL object can be cast to any class"
            return true;
        }
        Class<?> c = JNIObjectHandles.getObject(clazz);
        return c.isInstance(o);
    }

    /*
     * jclass GetObjectClass(JNIEnv *env, jobject obj);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle GetObjectClass(JNIEnvironment env, JNIObjectHandle handle) {
        Object obj = JNIObjectHandles.getObject(handle);
        Class<?> clazz = obj.getClass();
        return JNIObjectHandles.createLocal(clazz);
    }

    /*
     * jclass GetSuperclass(JNIEnv *env, jclass clazz);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle GetSuperclass(JNIEnvironment env, JNIObjectHandle handle) {
        Class<?> clazz = JNIObjectHandles.getObject(handle);
        return JNIObjectHandles.createLocal(clazz.getSuperclass());
    }

    /*
     * jboolean IsAssignableFrom(JNIEnv *env, jclass clazz1, jclass clazz2);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnFalse.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static boolean IsAssignableFrom(JNIEnvironment env, JNIObjectHandle handle1, JNIObjectHandle handle2) {
        Class<?> clazz1 = JNIObjectHandles.getObject(handle1);
        Class<?> clazz2 = JNIObjectHandles.getObject(handle2);
        return clazz2.isAssignableFrom(clazz1);
    }

    /*
     * jobject NewGlobalRef(JNIEnv *env, jobject obj);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static JNIObjectHandle NewGlobalRef(JNIEnvironment env, JNIObjectHandle handle) {
        return JNIObjectHandles.newGlobalRef(handle);
    }

    /*
     * void DeleteGlobalRef(JNIEnv *env, jobject globalRef);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void DeleteGlobalRef(JNIEnvironment env, JNIObjectHandle globalRef) {
        JNIObjectHandles.deleteGlobalRef(globalRef);
    }

    /*
     * jweak NewWeakGlobalRef(JNIEnv *env, jobject obj);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewWeakGlobalRef(JNIEnvironment env, JNIObjectHandle handle) {
        return JNIObjectHandles.newWeakGlobalRef(handle);
    }

    /*
     * void DeleteWeakGlobalRef(JNIEnv *env, jweak obj);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void DeleteWeakGlobalRef(JNIEnvironment env, JNIObjectHandle weak) {
        JNIObjectHandles.deleteWeakGlobalRef(weak);
    }

    /*
     * jobjectRefType GetObjectRefType(JNIEnv* env, jobject obj);
     */

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static JNIObjectRefType GetObjectRefType(JNIEnvironment env, JNIObjectHandle handle) {
        try {
            return JNIObjectHandles.getHandleType(handle);
        } catch (Throwable t) {
            return JNIObjectRefType.Invalid;
        }
    }

    /*
     * jclass FindClass(JNIEnv *env, const char *name);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle FindClass(JNIEnvironment env, CCharPointer cname) {
        CharSequence name = Utf8.wrapUtf8CString(cname);
        if (name == null) {
            throw new NoClassDefFoundError("Class name is either null or invalid UTF-8 string");
        }

        Class<?> clazz = JNIReflectionDictionary.singleton().getClassObjectByName(name);
        if (clazz == null) {
            throw new NoClassDefFoundError(name.toString());
        }
        /* Ensure that native code can't access the uninitialized native state, if any. */
        DynamicHub.fromClass(clazz).ensureInitialized();
        return JNIObjectHandles.createLocal(clazz);
    }

    /*
     * jint RegisterNatives(JNIEnv *env, jclass clazz, const JNINativeMethod *methods, jint
     * nMethods);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnJniErr.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnEDetached.class)
    static int RegisterNatives(JNIEnvironment env, JNIObjectHandle hclazz, JNINativeMethod methods, int nmethods) {
        Class<?> clazz = JNIObjectHandles.getObject(hclazz);
        Pointer p = (Pointer) methods;
        for (int i = 0; i < nmethods; i++) {
            JNINativeMethod entry = (JNINativeMethod) p;
            CharSequence name = Utf8.wrapUtf8CString(entry.name());
            if (name == null) {
                throw new NoSuchMethodError("Method name at index " + i + " is either null or invalid UTF-8 string");
            }

            CharSequence signature = Utf8.wrapUtf8CString(entry.signature());
            if (signature == null) {
                throw new NoSuchMethodError("Method signature at index " + i + " is either null or invalid UTF-8 string");
            }

            CFunctionPointer fnPtr = entry.fnPtr();

            String declaringClass = MetaUtil.toInternalName(clazz.getName());
            JNINativeLinkage linkage = JNIReflectionDictionary.singleton().getLinkage(declaringClass, name, signature);
            if (linkage != null) {
                linkage.setEntryPoint(fnPtr);
            } else {
                /*
                 * It happens that libraries register arbitrary Java native methods from their
                 * native code. If during analysis, we didn't reach some of those JNI methods (see
                 * com.oracle.svm.jni.hosted.JNINativeCallWrapperSubstitutionProcessor.lookup and
                 * com.oracle.svm.jni.access.JNIAccessFeature.duringAnalysis) we shouldn't fail:
                 * those native methods can never be invoked.
                 */
            }

            p = p.add(SizeOf.get(JNINativeMethod.class));
        }
        return JNIErrors.JNI_OK();
    }

    /*
     * jint UnregisterNatives(JNIEnv *env, jclass clazz);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnJniErr.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnEDetached.class)
    static int UnregisterNatives(JNIEnvironment env, JNIObjectHandle hclazz) {
        Class<?> clazz = JNIObjectHandles.getObject(hclazz);
        String internalName = MetaUtil.toInternalName(clazz.getName());
        JNIReflectionDictionary.singleton().unsetEntryPoints(internalName);
        return JNIErrors.JNI_OK();
    }

    /*
     * jmethodID GetMethodID(JNIEnv *env, jclass clazz, const char *name, const char *sig);
     *
     * jmethodID GetStaticMethodID(JNIEnv *env, jclass clazz, const char *name, const char *sig);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullPointer.class)
    static JNIMethodId GetMethodID(JNIEnvironment env, JNIObjectHandle hclazz, CCharPointer cname, CCharPointer csig) {
        return Support.getMethodID(hclazz, cname, csig, false);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullPointer.class)
    static JNIMethodId GetStaticMethodID(JNIEnvironment env, JNIObjectHandle hclazz, CCharPointer cname, CCharPointer csig) {
        return Support.getMethodID(hclazz, cname, csig, true);
    }

    /*
     * jfieldID GetFieldID(JNIEnv *env, jclass clazz, const char *name, const char *sig);
     *
     * jfieldID GetStaticFieldID(JNIEnv *env, jclass clazz, const char *name, const char *sig);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullPointer.class)
    static JNIFieldId GetFieldID(JNIEnvironment env, JNIObjectHandle hclazz, CCharPointer cname, CCharPointer csig) {
        return Support.getFieldID(hclazz, cname, csig, false);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullPointer.class)
    static JNIFieldId GetStaticFieldID(JNIEnvironment env, JNIObjectHandle hclazz, CCharPointer cname, CCharPointer csig) {
        return Support.getFieldID(hclazz, cname, csig, true);
    }

    /*
     * jobject AllocObject(JNIEnv *env, jclass clazz);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle AllocObject(JNIEnvironment env, JNIObjectHandle classHandle) throws InstantiationException {
        Class<?> clazz = JNIObjectHandles.getObject(classHandle);
        Object instance = U.allocateInstance(clazz);
        return JNIObjectHandles.createLocal(instance);
    }

    /*
     * jstring NewString(JNIEnv *env, const jchar *unicodeChars, jsize len);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewString(JNIEnvironment env, CShortPointer unicode, int len) {
        String str;
        char[] chars = new char[len];
        for (int i = 0; i < chars.length; i++) {
            int value = Short.toUnsignedInt(unicode.read(i));
            chars[i] = (char) value;
        }
        str = new String(chars);
        return JNIObjectHandles.createLocal(str);
    }

    /*
     * jstring NewStringUTF(JNIEnv *env, const char *bytes);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewStringUTF(JNIEnvironment env, CCharPointer bytes) {
        return JNIObjectHandles.createLocal(Utf8.utf8ToString(bytes));
    }

    /*
     * jsize GetStringLength(JNIEnv *env, jstring string);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnMinusOne.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnMinusOne.class)
    static int GetStringLength(JNIEnvironment env, JNIObjectHandle hstr) {
        String str = JNIObjectHandles.getObject(hstr);
        return (str != null) ? str.length() : 0;
    }

    /*
     * jsize GetStringUTFLength(JNIEnv *env, jstring string);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnMinusOne.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnMinusOne.class)
    static int GetStringUTFLength(JNIEnvironment env, JNIObjectHandle hstr) {
        String str = JNIObjectHandles.getObject(hstr);
        return Utf8.utf8Length(str);
    }

    /*
     * const jchar * GetStringChars(JNIEnv *env, jstring string, jboolean *isCopy);
     *
     * void ReleaseStringChars(JNIEnv *env, jstring string, const jchar *chars);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullPointer.class)
    static CShortPointer GetStringChars(JNIEnvironment env, JNIObjectHandle hstr, CCharPointer isCopy) {
        return Support.getNulTerminatedStringCharsAndPin(hstr, isCopy);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleaseStringChars(JNIEnvironment env, JNIObjectHandle hstr, CShortPointer chars) {
        Support.releaseString(chars);
    }

    /*
     * const char * GetStringUTFChars(JNIEnv *env, jstring string, jboolean *isCopy);
     *
     * void ReleaseStringUTFChars(JNIEnv *env, jstring string, const char *utf);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullPointer.class)
    static CCharPointer GetStringUTFChars(JNIEnvironment env, JNIObjectHandle hstr, CCharPointer isCopy) {
        String str = JNIObjectHandles.getObject(hstr);
        if (str == null) {
            return WordFactory.nullPointer();
        }
        if (isCopy.isNonNull()) {
            isCopy.write((byte) 1);
        }
        byte[] utf = Utf8.stringToUtf8(str, true);
        return JNIThreadLocalPrimitiveArrayViews.createArrayViewAndGetAddress(utf);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleaseStringUTFChars(JNIEnvironment env, JNIObjectHandle hstr, CCharPointer chars) {
        JNIThreadLocalPrimitiveArrayViews.destroyNewestArrayViewByAddress(chars, JNIMode.JNI_ABORT());
    }

    /*
     * const jchar * GetStringCritical(JNIEnv *env, jstring string, jboolean *isCopy);
     *
     * void ReleaseStringCritical(JNIEnv *env, jstring string, const jchar *carray);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullPointer.class)
    static CShortPointer GetStringCritical(JNIEnvironment env, JNIObjectHandle hstr, CCharPointer isCopy) {
        return Support.getNulTerminatedStringCharsAndPin(hstr, isCopy);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleaseStringCritical(JNIEnvironment env, JNIObjectHandle hstr, CShortPointer carray) {
        Support.releaseString(carray);
    }

    /*
     * void GetStringRegion(JNIEnv *env, jstring str, jsize start, jsize len, jchar *buf);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void GetStringRegion(JNIEnvironment env, JNIObjectHandle hstr, int start, int len, CShortPointer buf) {
        String str = JNIObjectHandles.getObject(hstr);
        if (start < 0) {
            throw new StringIndexOutOfBoundsException(start);
        }
        if (start + len > str.length()) {
            throw new StringIndexOutOfBoundsException(start + len);
        }
        if (len < 0) {
            throw new StringIndexOutOfBoundsException(len);
        }
        for (int i = 0; i < len; i++) {
            char c = str.charAt(start + i);
            buf.write(i, (short) c);
        }
    }

    /*
     * void GetStringUTFRegion(JNIEnv *env, jstring str, jsize start, jsize len, char *buf);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void GetStringUTFRegion(JNIEnvironment env, JNIObjectHandle hstr, int start, int len, CCharPointer buf) {
        String str = JNIObjectHandles.getObject(hstr);
        if (start < 0) {
            throw new StringIndexOutOfBoundsException(start);
        }
        if (start + len > str.length()) {
            throw new StringIndexOutOfBoundsException(start + len);
        }
        if (len < 0) {
            throw new StringIndexOutOfBoundsException(len);
        }
        int capacity = Utf8.maxUtf8ByteLength(len, true); // estimate: caller must pre-allocate
                                                          // enough
        ByteBuffer buffer = CTypeConversion.asByteBuffer(buf, capacity);
        Utf8.substringToUtf8(buffer, str, start, start + len, true);
    }

    /*
     * jobject NewDirectByteBuffer(JNIEnv* env, void* address, jlong capacity);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewDirectByteBuffer(JNIEnvironment env, WordPointer address, long capacity) {
        ByteBuffer bb = DirectByteBufferUtil.allocate(address.rawValue(), capacity);
        return JNIObjectHandles.createLocal(bb);
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
        Target_java_nio_Buffer buf = Support.directBufferFromJNIHandle(handle);
        return (buf == null) ? WordFactory.nullPointer() : WordFactory.pointer(buf.address);
    }

    /*
     * jlong GetDirectBufferCapacity(JNIEnv* env, jobject buf);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnMinusOne.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnMinusOneLong.class)
    static long GetDirectBufferCapacity(JNIEnvironment env, JNIObjectHandle handle) {
        Target_java_nio_Buffer buf = Support.directBufferFromJNIHandle(handle);
        return (buf == null) ? -1 : buf.capacity;
    }

    /*
     * ArrayType New<PrimitiveType>Array(JNIEnv *env, jsize length);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewBooleanArray(JNIEnvironment env, int length) {
        if (length < 0) {
            return JNIObjectHandles.nullHandle();
        }
        return JNIObjectHandles.createLocal(new boolean[length]);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewByteArray(JNIEnvironment env, int length) {
        if (length < 0) {
            return JNIObjectHandles.nullHandle();
        }
        return JNIObjectHandles.createLocal(new byte[length]);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewCharArray(JNIEnvironment env, int length) {
        if (length < 0) {
            return JNIObjectHandles.nullHandle();
        }
        return JNIObjectHandles.createLocal(new char[length]);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewDoubleArray(JNIEnvironment env, int length) {
        if (length < 0) {
            return JNIObjectHandles.nullHandle();
        }
        return JNIObjectHandles.createLocal(new double[length]);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewFloatArray(JNIEnvironment env, int length) {
        if (length < 0) {
            return JNIObjectHandles.nullHandle();
        }
        return JNIObjectHandles.createLocal(new float[length]);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewIntArray(JNIEnvironment env, int length) {
        if (length < 0) {
            return JNIObjectHandles.nullHandle();
        }
        return JNIObjectHandles.createLocal(new int[length]);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewLongArray(JNIEnvironment env, int length) {
        if (length < 0) {
            return JNIObjectHandles.nullHandle();
        }
        return JNIObjectHandles.createLocal(new long[length]);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewShortArray(JNIEnvironment env, int length) {
        if (length < 0) {
            return JNIObjectHandles.nullHandle();
        }
        return JNIObjectHandles.createLocal(new short[length]);
    }

    /*
     * jobjectArray NewObjectArray(JNIEnv *env, jsize length, jclass elementClass, jobject
     * initialElement);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle NewObjectArray(JNIEnvironment env, int length, JNIObjectHandle hElementClass, JNIObjectHandle hInitialElement) {
        if (length < 0) {
            return JNIObjectHandles.nullHandle();
        }
        Class<?> elementClass = JNIObjectHandles.getObject(hElementClass);
        Object[] array = null;
        if (elementClass != null) {
            Object initialElement = JNIObjectHandles.getObject(hInitialElement);
            array = (Object[]) Array.newInstance(elementClass, length);
            Arrays.fill(array, initialElement);
        }
        return JNIObjectHandles.createLocal(array);
    }

    /*
     * jobject GetObjectArrayElement(JNIEnv *env, jobjectArray array, jsize index);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle GetObjectArrayElement(JNIEnvironment env, JNIObjectHandle harray, int index) {
        Object[] array = JNIObjectHandles.getObject(harray);
        Object value = array[index];
        return JNIObjectHandles.createLocal(value);
    }

    /*
     * void SetObjectArrayElement(JNIEnv *env, jobjectArray array, jsize index, jobject value);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetObjectArrayElement(JNIEnvironment env, JNIObjectHandle harray, int index, JNIObjectHandle hvalue) {
        Object[] array = JNIObjectHandles.getObject(harray);
        Object value = JNIObjectHandles.getObject(hvalue);
        array[index] = value;
    }

    /*
     * jvoid * GetPrimitiveArrayCritical(JNIEnv *env, jarray array, jboolean *isCopy);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullPointer.class)
    static WordPointer GetPrimitiveArrayCritical(JNIEnvironment env, JNIObjectHandle harray, CCharPointer isCopy) {
        Object array = JNIObjectHandles.getObject(harray);
        if (array == null) {
            return WordFactory.nullPointer();
        }
        PrimitiveArrayView ref = JNIThreadLocalPrimitiveArrayViews.createArrayView(array);
        if (isCopy.isNonNull()) {
            isCopy.write(ref.isCopy() ? (byte) 1 : (byte) 0);
        }
        return ref.addressOfArrayElement(0);
    }

    /*
     * void ReleasePrimitiveArrayCritical(JNIEnv *env, jarray array, void *carray, jint mode);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleasePrimitiveArrayCritical(JNIEnvironment env, JNIObjectHandle harray, WordPointer carray, int mode) {
        JNIThreadLocalPrimitiveArrayViews.destroyNewestArrayViewByAddress(carray, mode);
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
        return ArrayLengthNode.arrayLength(JNIObjectHandles.getObject(harray));
    }

    /*
     * jboolean ExceptionCheck(JNIEnv *env);
     *
     * jthrowable ExceptionOccurred(JNIEnv *env);
     */

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static boolean ExceptionCheck(JNIEnvironment env) {
        return JNIThreadLocalPendingException.get() != null;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle ExceptionOccurred(JNIEnvironment env) {
        return JNIObjectHandles.createLocal(JNIThreadLocalPendingException.get());
    }

    /*
     * void ExceptionClear(JNIEnv *env);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ExceptionClear(JNIEnvironment env) {
        JNIThreadLocalPendingException.clear();
    }

    /*
     * void ExceptionDescribe(JNIEnv *env);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ExceptionDescribe(JNIEnvironment env) {
        Throwable t = JNIThreadLocalPendingException.get();
        JNIThreadLocalPendingException.clear();
        if (t != null) {
            if (t instanceof ThreadDeath) {
                // this thread is being killed, do not print anything
            } else {
                System.err.println("Exception in thread \"" + Thread.currentThread().getName() + "\": " + t.getClass().getCanonicalName());
                try {
                    t.printStackTrace();
                } catch (Throwable ignored) {
                    // ignore
                }
                System.err.flush();
            }
        }
    }

    /*
     * jint Throw(JNIEnv *env, jthrowable obj);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnZero.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnEDetached.class)
    static int Throw(JNIEnvironment env, JNIObjectHandle handle) throws Throwable {
        throw (Throwable) JNIObjectHandles.getObject(handle);
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
        Class<?> clazz = JNIObjectHandles.getObject(clazzHandle);
        JNIMethodId ctor = Support.getMethodID(clazz, "<init>", "(Ljava/lang/String;)V", false);
        JNIObjectHandle messageHandle = NewStringUTF(env, message);
        /*
         * The iOS calling convention mandates that variadic functions parameters are all passed on
         * the stack. As a consequence, calling the newObject method does not work on iOS as the
         * code generator has no way of telling that the call is variadic, so we use newObjectA
         * instead.
         */
        NewObjectWithObjectArrayArgFunctionPointer newObjectA = (NewObjectWithObjectArrayArgFunctionPointer) env.getFunctions().getNewObjectA();
        JNIValue array = UnsafeStackValue.get(JNIValue.class);
        array.setObject(messageHandle);
        JNIObjectHandle exception = newObjectA.invoke(env, clazzHandle, ctor, array);
        throw (Throwable) JNIObjectHandles.getObject(exception);
    }

    /*
     * void FatalError(JNIEnv *env, const char *msg);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    @NeverInline("Access of caller frame.")
    static void FatalError(JNIEnvironment env, CCharPointer message) {
        CodePointer callerIP = KnownIntrinsics.readReturnAddress();
        Pointer callerSP = KnownIntrinsics.readCallerStackPointer();
        Support.fatalError(callerIP, callerSP, CTypeConversion.toJavaString(message));
    }

    /*
     * jint GetJavaVM(JNIEnv *env, JavaVM **vm);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnJniErr.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnEDetached.class)
    static int GetJavaVM(JNIEnvironment env, JNIJavaVMPointer vm) {
        vm.write(JNIFunctionTables.singleton().getGlobalJavaVM());
        return JNIErrors.JNI_OK();
    }

    /*
     * jfieldID FromReflectedField(JNIEnv *env, jobject field);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullPointer.class)
    static JNIFieldId FromReflectedField(JNIEnvironment env, JNIObjectHandle fieldHandle) {
        JNIFieldId fieldId = WordFactory.zero();
        Field obj = JNIObjectHandles.getObject(fieldHandle);
        if (obj != null) {
            boolean isStatic = Modifier.isStatic(obj.getModifiers());
            fieldId = JNIReflectionDictionary.singleton().getDeclaredFieldID(obj.getDeclaringClass(), obj.getName(), isStatic);
        }
        return fieldId;
    }

    /*
     * jobject ToReflectedField(JNIEnv *env, jclass cls, jfieldID fieldID, jboolean isStatic);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle ToReflectedField(JNIEnvironment env, JNIObjectHandle classHandle, JNIFieldId fieldId) {
        Field field = null;
        Class<?> clazz = JNIObjectHandles.getObject(classHandle);
        if (clazz != null) {
            String name = JNIReflectionDictionary.singleton().getFieldNameByID(clazz, fieldId);
            if (name != null) {
                try {
                    field = clazz.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    // proceed and return null
                }
            }
        }
        return JNIObjectHandles.createLocal(field);
    }

    /*
     * jmethodID FromReflectedMethod(JNIEnv *env, jobject method);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullPointer.class)
    static JNIMethodId FromReflectedMethod(JNIEnvironment env, JNIObjectHandle methodHandle) {
        JNIMethodId methodId = WordFactory.nullPointer();
        Executable method = JNIObjectHandles.getObject(methodHandle);
        if (method != null) {
            boolean isStatic = Modifier.isStatic(method.getModifiers());
            JNIAccessibleMethodDescriptor descriptor = JNIAccessibleMethodDescriptor.of(method);
            methodId = JNIReflectionDictionary.singleton().getDeclaredMethodID(method.getDeclaringClass(), descriptor, isStatic);
        }
        return methodId;
    }

    /*
     * jobject ToReflectedMethod(JNIEnv *env, jclass cls, jmethodID methodID, jboolean isStatic);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullPointer.class)
    static JNIObjectHandle ToReflectedMethod(JNIEnvironment env, JNIObjectHandle classHandle, JNIMethodId methodId, boolean isStatic) {
        Executable result = null;
        JNIAccessibleMethod jniMethod = JNIReflectionDictionary.getMethodByID(methodId);
        JNIAccessibleMethodDescriptor descriptor = JNIReflectionDictionary.getMethodDescriptor(jniMethod);
        if (descriptor != null) {
            Class<?> clazz = jniMethod.getDeclaringClass().getClassObject();
            if (descriptor.isConstructor()) {
                for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                    if (descriptor.equals(JNIAccessibleMethodDescriptor.of(ctor))) {
                        result = ctor;
                        break;
                    }
                }
            } else {
                for (Method method : clazz.getDeclaredMethods()) {
                    if (descriptor.getName().equals(method.getName())) {
                        if (descriptor.equals(JNIAccessibleMethodDescriptor.of(method))) {
                            result = method;
                            break;
                        }
                    }
                }
            }
        }
        return JNIObjectHandles.createLocal(result);
    }

    /*
     * jint MonitorEnter(JNIEnv *env, jobject obj);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnJniErr.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnEDetached.class)
    static int MonitorEnter(JNIEnvironment env, JNIObjectHandle handle) {
        Object obj = JNIObjectHandles.getObject(handle);
        if (obj == null) {
            throw new NullPointerException();
        }
        boolean pinned = false;
        if (VirtualThreads.isSupported() && JavaThreads.isCurrentThreadVirtual()) {
            // Acquiring monitors via JNI associates them with the carrier thread via
            // JNIThreadOwnedMonitors, so we must pin the virtual thread
            try {
                VirtualThreads.singleton().pinCurrent();
            } catch (IllegalStateException e) { // too many pins
                throw new IllegalMonitorStateException();
            }
            pinned = true;
        }
        boolean acquired = false;
        try {
            MonitorSupport.singleton().monitorEnter(obj, MonitorInflationCause.JNI_ENTER);
            assert Thread.holdsLock(obj);
            acquired = true;

            JNIThreadOwnedMonitors.entered(obj);
            return JNIErrors.JNI_OK();
        } catch (Throwable t) {
            try {
                if (acquired) {
                    /* The thread acquired the monitor, so monitor inflation can't happen here. */
                    MonitorSupport.singleton().monitorExit(obj, MonitorInflationCause.VM_INTERNAL);
                }
                if (pinned) {
                    VirtualThreads.singleton().unpinCurrent();
                }
            } catch (Throwable u) {
                throw VMError.shouldNotReachHere(u);
            }
            throw t;
        }
    }

    /*
     * jint MonitorExit(JNIEnv *env, jobject obj);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnJniErr.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnEDetached.class)
    static int MonitorExit(JNIEnvironment env, JNIObjectHandle handle) {
        Object obj = JNIObjectHandles.getObject(handle);
        if (obj == null) {
            throw new NullPointerException();
        }
        if (!Thread.holdsLock(obj)) {
            throw new IllegalMonitorStateException();
        }
        MonitorSupport.singleton().monitorExit(obj, MonitorInflationCause.JNI_EXIT);
        JNIThreadOwnedMonitors.exited(obj);
        if (VirtualThreads.isSupported() && JavaThreads.isCurrentThreadVirtual()) {
            try {
                VirtualThreads.singleton().unpinCurrent();
            } catch (IllegalStateException e) { // not pinned?
                throw new IllegalMonitorStateException();
            }
        }
        return JNIErrors.JNI_OK();
    }

    /*
     * jobject (JNICALL *GetModule) (JNIEnv* env, jclass clazz);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle GetModule(JNIEnvironment env, JNIObjectHandle handle) {
        Object obj = JNIObjectHandles.getObject(handle);
        if (obj == null) {
            throw new NullPointerException();
        }
        if (!(obj instanceof Class<?>)) {
            throw new IllegalArgumentException();
        }
        Module module = ((Class<?>) obj).getModule();
        return JNIObjectHandles.createLocal(module);
    }

    /*
     * jclass DefineClass(JNIEnv *env, const char *name, jobject loader, const jbyte *buf, jsize
     * bufLen);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterPrologue.class, prologueBailout = ReturnNullHandle.class)
    static JNIObjectHandle DefineClass(JNIEnvironment env, CCharPointer cname, JNIObjectHandle loader, CCharPointer buf, int bufLen) {
        if (buf.isNull() || bufLen < 0) {
            throw new ClassFormatError();
        }
        String name = Utf8.utf8ToString(cname);
        if (name != null) { // inverse to HotSpot fixClassname():
            name = name.replace('/', '.');
        }
        ClassLoader classLoader = JNIObjectHandles.getObject(loader);
        byte[] data = new byte[bufLen];
        CTypeConversion.asByteBuffer(buf, bufLen).get(data);
        Class<?> clazz = PredefinedClassesSupport.loadClass(classLoader, name, data, 0, data.length, null);
        return JNIObjectHandles.createLocal(clazz);
    }
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static PointerBase GetBooleanArrayElements(JNIEnvironment env, JNIObjectHandle array, CCharPointer isCopy) {
        return Support.createArrayViewAndGetAddress(array, isCopy);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static PointerBase GetByteArrayElements(JNIEnvironment env, JNIObjectHandle array, CCharPointer isCopy) {
        return Support.createArrayViewAndGetAddress(array, isCopy);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static PointerBase GetShortArrayElements(JNIEnvironment env, JNIObjectHandle array, CCharPointer isCopy) {
        return Support.createArrayViewAndGetAddress(array, isCopy);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static PointerBase GetCharArrayElements(JNIEnvironment env, JNIObjectHandle array, CCharPointer isCopy) {
        return Support.createArrayViewAndGetAddress(array, isCopy);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static PointerBase GetIntArrayElements(JNIEnvironment env, JNIObjectHandle array, CCharPointer isCopy) {
        return Support.createArrayViewAndGetAddress(array, isCopy);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static PointerBase GetLongArrayElements(JNIEnvironment env, JNIObjectHandle array, CCharPointer isCopy) {
        return Support.createArrayViewAndGetAddress(array, isCopy);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static PointerBase GetFloatArrayElements(JNIEnvironment env, JNIObjectHandle array, CCharPointer isCopy) {
        return Support.createArrayViewAndGetAddress(array, isCopy);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static PointerBase GetDoubleArrayElements(JNIEnvironment env, JNIObjectHandle array, CCharPointer isCopy) {
        return Support.createArrayViewAndGetAddress(array, isCopy);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleaseBooleanArrayElements(JNIEnvironment env, JNIObjectHandle array, PointerBase elements, int mode) {
        Support.destroyNewestArrayViewByAddress(elements, mode);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleaseByteArrayElements(JNIEnvironment env, JNIObjectHandle array, PointerBase elements, int mode) {
        Support.destroyNewestArrayViewByAddress(elements, mode);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleaseShortArrayElements(JNIEnvironment env, JNIObjectHandle array, PointerBase elements, int mode) {
        Support.destroyNewestArrayViewByAddress(elements, mode);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleaseCharArrayElements(JNIEnvironment env, JNIObjectHandle array, PointerBase elements, int mode) {
        Support.destroyNewestArrayViewByAddress(elements, mode);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleaseIntArrayElements(JNIEnvironment env, JNIObjectHandle array, PointerBase elements, int mode) {
        Support.destroyNewestArrayViewByAddress(elements, mode);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleaseLongArrayElements(JNIEnvironment env, JNIObjectHandle array, PointerBase elements, int mode) {
        Support.destroyNewestArrayViewByAddress(elements, mode);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleaseFloatArrayElements(JNIEnvironment env, JNIObjectHandle array, PointerBase elements, int mode) {
        Support.destroyNewestArrayViewByAddress(elements, mode);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void ReleaseDoubleArrayElements(JNIEnvironment env, JNIObjectHandle array, PointerBase elements, int mode) {
        Support.destroyNewestArrayViewByAddress(elements, mode);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void GetBooleanArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, PointerBase buffer) {
        Support.getPrimitiveArrayRegion(JavaKind.Boolean, array, start, count, buffer);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void GetByteArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, PointerBase buffer) {
        Support.getPrimitiveArrayRegion(JavaKind.Byte, array, start, count, buffer);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void GetShortArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, PointerBase buffer) {
        Support.getPrimitiveArrayRegion(JavaKind.Short, array, start, count, buffer);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void GetCharArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, PointerBase buffer) {
        Support.getPrimitiveArrayRegion(JavaKind.Char, array, start, count, buffer);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void GetIntArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, PointerBase buffer) {
        Support.getPrimitiveArrayRegion(JavaKind.Int, array, start, count, buffer);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void GetLongArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, PointerBase buffer) {
        Support.getPrimitiveArrayRegion(JavaKind.Long, array, start, count, buffer);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void GetFloatArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, PointerBase buffer) {
        Support.getPrimitiveArrayRegion(JavaKind.Float, array, start, count, buffer);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void GetDoubleArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, PointerBase buffer) {
        Support.getPrimitiveArrayRegion(JavaKind.Double, array, start, count, buffer);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetBooleanArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, @CConst PointerBase buffer) {
        Support.setPrimitiveArrayRegion(JavaKind.Boolean, array, start, count, buffer);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetByteArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, @CConst PointerBase buffer) {
        Support.setPrimitiveArrayRegion(JavaKind.Byte, array, start, count, buffer);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetShortArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, @CConst PointerBase buffer) {
        Support.setPrimitiveArrayRegion(JavaKind.Short, array, start, count, buffer);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetCharArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, @CConst PointerBase buffer) {
        Support.setPrimitiveArrayRegion(JavaKind.Char, array, start, count, buffer);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetIntArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, @CConst PointerBase buffer) {
        Support.setPrimitiveArrayRegion(JavaKind.Int, array, start, count, buffer);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetLongArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, @CConst PointerBase buffer) {
        Support.setPrimitiveArrayRegion(JavaKind.Long, array, start, count, buffer);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetFloatArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, @CConst PointerBase buffer) {
        Support.setPrimitiveArrayRegion(JavaKind.Float, array, start, count, buffer);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetDoubleArrayRegion(JNIEnvironment env, JNIObjectHandle array, int start, int count, @CConst PointerBase buffer) {
        Support.setPrimitiveArrayRegion(JavaKind.Double, array, start, count, buffer);
    }

    /* It is not correct to return null when an exception is thrown, see GR-54276. */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static JNIObjectHandle GetObjectField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId) {
        return JNIObjectFieldAccess.singleton().getObjectField(obj, fieldId);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static boolean GetBooleanField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId) {
        Object o = JNIObjectHandles.getObject(obj);
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        return U.getBoolean(o, offset);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static byte GetByteField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId) {
        Object o = JNIObjectHandles.getObject(obj);
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        return U.getByte(o, offset);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static short GetShortField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId) {
        Object o = JNIObjectHandles.getObject(obj);
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        return U.getShort(o, offset);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static char GetCharField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId) {
        Object o = JNIObjectHandles.getObject(obj);
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        return U.getChar(o, offset);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static int GetIntField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId) {
        Object o = JNIObjectHandles.getObject(obj);
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        return U.getInt(o, offset);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static long GetLongField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId) {
        Object o = JNIObjectHandles.getObject(obj);
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        return U.getLong(o, offset);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static float GetFloatField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId) {
        Object o = JNIObjectHandles.getObject(obj);
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        return U.getFloat(o, offset);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static double GetDoubleField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId) {
        Object o = JNIObjectHandles.getObject(obj);
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        return U.getDouble(o, offset);
    }

    /* It is not correct to return null when an exception is thrown, see GR-54276. */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static JNIObjectHandle GetStaticObjectField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId) {
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        Object result = U.getReference(StaticFieldsSupport.getStaticObjectFields(), offset);
        return JNIObjectHandles.createLocal(result);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static boolean GetStaticBooleanField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId) {
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        return U.getBoolean(StaticFieldsSupport.getStaticPrimitiveFields(), offset);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static byte GetStaticByteField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId) {
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        return U.getByte(StaticFieldsSupport.getStaticPrimitiveFields(), offset);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static short GetStaticShortField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId) {
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        return U.getShort(StaticFieldsSupport.getStaticPrimitiveFields(), offset);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static char GetStaticCharField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId) {
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        return U.getChar(StaticFieldsSupport.getStaticPrimitiveFields(), offset);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static int GetStaticIntField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId) {
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        return U.getInt(StaticFieldsSupport.getStaticPrimitiveFields(), offset);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static long GetStaticLongField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId) {
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        return U.getLong(StaticFieldsSupport.getStaticPrimitiveFields(), offset);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static float GetStaticFloatField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId) {
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        return U.getFloat(StaticFieldsSupport.getStaticPrimitiveFields(), offset);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static double GetStaticDoubleField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId) {
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        return U.getDouble(StaticFieldsSupport.getStaticPrimitiveFields(), offset);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetObjectField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId, JNIObjectHandle value) {
        Object o = JNIObjectHandles.getObject(obj);
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        U.putReference(o, offset, JNIObjectHandles.getObject(value));
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetBooleanField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId, boolean value) {
        Object o = JNIObjectHandles.getObject(obj);
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        U.putBoolean(o, offset, value);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetByteField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId, byte value) {
        Object o = JNIObjectHandles.getObject(obj);
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        U.putByte(o, offset, value);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetShortField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId, short value) {
        Object o = JNIObjectHandles.getObject(obj);
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        U.putShort(o, offset, value);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetCharField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId, char value) {
        Object o = JNIObjectHandles.getObject(obj);
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        U.putChar(o, offset, value);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetIntField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId, int value) {
        Object o = JNIObjectHandles.getObject(obj);
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        U.putInt(o, offset, value);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetLongField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId, long value) {
        Object o = JNIObjectHandles.getObject(obj);
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        U.putLong(o, offset, value);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetFloatField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId, float value) {
        Object o = JNIObjectHandles.getObject(obj);
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        U.putFloat(o, offset, value);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetDoubleField(JNIEnvironment env, JNIObjectHandle obj, JNIFieldId fieldId, double value) {
        Object o = JNIObjectHandles.getObject(obj);
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        U.putDouble(o, offset, value);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetStaticObjectField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId, JNIObjectHandle value) {
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        U.putReference(StaticFieldsSupport.getStaticObjectFields(), offset, JNIObjectHandles.getObject(value));
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetStaticBooleanField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId, boolean value) {
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        U.putBoolean(StaticFieldsSupport.getStaticPrimitiveFields(), offset, value);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetStaticByteField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId, byte value) {
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        U.putByte(StaticFieldsSupport.getStaticPrimitiveFields(), offset, value);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetStaticShortField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId, short value) {
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        U.putShort(StaticFieldsSupport.getStaticPrimitiveFields(), offset, value);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetStaticCharField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId, char value) {
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        U.putChar(StaticFieldsSupport.getStaticPrimitiveFields(), offset, value);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetStaticIntField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId, int value) {
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        U.putInt(StaticFieldsSupport.getStaticPrimitiveFields(), offset, value);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetStaticLongField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId, long value) {
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        U.putLong(StaticFieldsSupport.getStaticPrimitiveFields(), offset, value);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetStaticFloatField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId, float value) {
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        U.putFloat(StaticFieldsSupport.getStaticPrimitiveFields(), offset, value);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    @CEntryPoint(exceptionHandler = FatalExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = JNIEnvEnterFatalOnFailurePrologue.class)
    static void SetStaticDoubleField(JNIEnvironment env, JNIObjectHandle clazz, JNIFieldId fieldId, double value) {
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        U.putDouble(StaticFieldsSupport.getStaticPrimitiveFields(), offset, value);

    // Checkstyle: resume

    /**
     * Helper code for JNI functions. This is an inner class because the outer methods must match
     * JNI functions.
     */
    public static class Support {
        static class JNIEnvEnterPrologue implements CEntryPointOptions.Prologue {
            @Uninterruptible(reason = "prologue")
            public static int enter(JNIEnvironment env) {
                return CEntryPointActions.enter((IsolateThread) env);
            }
        }

        static class ReturnNullHandle implements CEntryPointOptions.PrologueBailout {
            @Uninterruptible(reason = "prologue")
            public static JNIObjectHandle bailout(int prologueResult) {
                return JNIObjectHandles.nullHandle();
            }
        }

        static class ReturnEDetached implements CEntryPointOptions.PrologueBailout {
            @Uninterruptible(reason = "prologue")
            public static int bailout(int prologueResult) {
                return JNIErrors.JNI_EDETACHED();
            }
        }

        static class ReturnMinusOne implements CEntryPointOptions.PrologueBailout {
            @Uninterruptible(reason = "prologue")
            public static int bailout(int prologueResult) {
                return -1;
            }
        }

        static class ReturnMinusOneLong implements CEntryPointOptions.PrologueBailout {
            @Uninterruptible(reason = "prologue")
            public static long bailout(int prologueResult) {
                return -1L;
            }
        }

        static final CGlobalData<CCharPointer> JNIENV_ENTER_FAIL_FATALLY_MESSAGE = CGlobalDataFactory.createCString(
                        "A JNI call failed to enter the isolate via its JNI environment argument. The environment might be invalid or no longer exists.");

        public static class JNIEnvEnterFatalOnFailurePrologue implements CEntryPointOptions.Prologue {
            @Uninterruptible(reason = "prologue")
            public static void enter(JNIEnvironment env) {
                int error = CEntryPointActions.enter((IsolateThread) env);
                if (error != CEntryPointErrors.NO_ERROR) {
                    CEntryPointActions.failFatally(error, JNIENV_ENTER_FAIL_FATALLY_MESSAGE.get());
                }
            }
        }

        static class JNIJavaVMEnterAttachThreadEnsureJavaThreadPrologue implements CEntryPointOptions.Prologue {
            @Uninterruptible(reason = "prologue")
            static int enter(JNIJavaVM vm) {
                /*
                 * DetachCurrentThread and DestroyJavaVM never return a more specific error than
                 * JNI_ERR on HotSpot. So, we need to do the same.
                 */
                int code = CEntryPointActions.enterAttachThread(vm.getFunctions().getIsolate(), false, true);
                return convertCEntryPointErrorToJNIError(code, false);
            }
        }

        static class JNIJavaVMEnterAttachThreadManualJavaThreadPrologue implements CEntryPointOptions.Prologue {
            @Uninterruptible(reason = "prologue")
            static int enter(JNIJavaVM vm) {
                /*
                 * AttachCurrentThread and AttachCurrentThreadAsDaemon never return a more specific
                 * error than JNI_ERR on HotSpot. So, we need to do the same.
                 */
                int code = CEntryPointActions.enterAttachThread(vm.getFunctions().getIsolate(), false, false);
                return convertCEntryPointErrorToJNIError(code, false);
            }
        }

        public static class JNIExceptionHandlerVoid implements CEntryPoint.ExceptionHandler {
            @Uninterruptible(reason = "exception handler")
            static void handle(Throwable t) {
                Support.handleException(t);
            }
        }

        static class JNIExceptionHandlerReturnNullHandle implements CEntryPoint.ExceptionHandler {
            @Uninterruptible(reason = "exception handler")
            static JNIObjectHandle handle(Throwable t) {
                Support.handleException(t);
                return JNIObjectHandles.nullHandle();
            }
        }

        static class JNIExceptionHandlerReturnNullWord implements CEntryPoint.ExceptionHandler {
            @Uninterruptible(reason = "exception handler")
            static WordBase handle(Throwable t) {
                Support.handleException(t);
                return WordFactory.nullPointer();
            }
        }

        static class JNIExceptionHandlerReturnFalse implements CEntryPoint.ExceptionHandler {
            @Uninterruptible(reason = "exception handler")
            static boolean handle(Throwable t) {
                Support.handleException(t);
                return false;
            }
        }

        static class JNIExceptionHandlerReturnMinusOne implements CEntryPoint.ExceptionHandler {
            @Uninterruptible(reason = "exception handler")
            static int handle(Throwable t) {
                Support.handleException(t);
                return -1;
            }
        }

        static class JNIExceptionHandlerReturnZero implements CEntryPoint.ExceptionHandler {
            @Uninterruptible(reason = "exception handler")
            static int handle(Throwable t) {
                Support.handleException(t);
                return 0;
            }
        }

        static class JNIExceptionHandlerReturnJniErr implements CEntryPoint.ExceptionHandler {
            @Uninterruptible(reason = "exception handler")
            static int handle(Throwable t) {
                Support.handleException(t);
                return JNIErrors.JNI_ERR();
            }
        }

        static class JNIExceptionHandlerDetachAndReturnJniErr implements CEntryPoint.ExceptionHandler {
            @Uninterruptible(reason = "exception handler")
            static int handle(Throwable t) {
                int error = (t instanceof OutOfMemoryError) ? JNIErrors.JNI_ENOMEM() : JNIErrors.JNI_ERR();
                CEntryPointActions.leaveDetachThread();
                return error;
            }
        }

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
                }
            }

            return JNIErrors.JNI_ERR();
        }

        static JNIMethodId getMethodID(JNIObjectHandle hclazz, CCharPointer cname, CCharPointer csig, boolean isStatic) {
            Class<?> clazz = JNIObjectHandles.getObject(hclazz);
            DynamicHub.fromClass(clazz).ensureInitialized();

            CharSequence name = Utf8.wrapUtf8CString(cname);
            if (name == null) {
                throw new NoSuchMethodError("Method name is either null or invalid UTF-8 string");
            }

            CharSequence signature = Utf8.wrapUtf8CString(csig);
            if (signature == null) {
                throw new NoSuchMethodError("Method signature is either null or invalid UTF-8 string");
            }

            return getMethodID(clazz, name, signature, isStatic);
        }

        private static JNIMethodId getMethodID(Class<?> clazz, CharSequence name, CharSequence signature, boolean isStatic) {
            JNIMethodId methodID = JNIReflectionDictionary.singleton().getMethodID(clazz, name, signature, isStatic);
            if (methodID.isNull()) {
                String message = clazz.getName() + "." + name + signature;
                JNIMethodId candidate = JNIReflectionDictionary.singleton().getMethodID(clazz, name, signature, !isStatic);
                if (candidate.isNonNull()) {
                    if (isStatic) {
                        message += " (found matching non-static method that would be returned by GetMethodID)";
                    } else {
                        message += " (found matching static method that would be returned by GetStaticMethodID)";
                    }
                }
                throw new NoSuchMethodError(message);
            }
            return methodID;
        }

        static JNIFieldId getFieldID(JNIObjectHandle hclazz, CCharPointer cname, CCharPointer csig, boolean isStatic) {
            Class<?> clazz = JNIObjectHandles.getObject(hclazz);
            DynamicHub.fromClass(clazz).ensureInitialized();

            CharSequence name = Utf8.wrapUtf8CString(cname);
            if (name == null) {
                throw new NoSuchFieldError("Field name is either null or invalid UTF-8 string");
            }

            JNIFieldId fieldID = JNIReflectionDictionary.singleton().getFieldID(clazz, name, isStatic);
            if (fieldID.isNull()) {
                throw new NoSuchFieldError(clazz.getName() + '.' + name);
            }
            // TODO: check field signature
            return fieldID;
        }

        static CShortPointer getNulTerminatedStringCharsAndPin(JNIObjectHandle hstr, CCharPointer isCopy) {
            String str = JNIObjectHandles.getObject(hstr);
            if (str == null) {
                return WordFactory.nullPointer();
            }
            if (isCopy.isNonNull()) {
                isCopy.write((byte) 1);
            }
            /*
             * With compressed strings (introduced in JDK 9), a Java String can have different
             * encodings. So we always request a copy as a char[] array. For a JDK 8 String, or for
             * a JDK 9 UTF16 encoded String, we could avoid the copying. But it would require us to
             * know internals of the String implementation, so we do not do it for now.
             */
            char[] chars = new char[str.length() + 1];
            str.getChars(0, str.length(), chars, 0);
            return JNIThreadLocalPrimitiveArrayViews.createArrayViewAndGetAddress(chars);
        }

        static void releaseString(CShortPointer cstr) {
            JNIThreadLocalPrimitiveArrayViews.destroyNewestArrayViewByAddress(cstr, JNIMode.JNI_ABORT());
        }

        @Uninterruptible(reason = "exception handler")
        static void handleException(Throwable t) {
            /*
             * The JNI specification requires that native code may call only certain functions while
             * an exception is pending. However, the JNI implementation in OpenJDK generally does
             * not enforce this and when another exception occurs in a JNI function, it replaces the
             * exception that was already pending. So does ours.
             */
            JNIThreadLocalPendingException.set(t);
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

        static PointerBase createArrayViewAndGetAddress(JNIObjectHandle handle, CCharPointer isCopy) {
            Object obj = JNIObjectHandles.getObject(handle);
            if (!obj.getClass().isArray()) {
                throw new IllegalArgumentException("Argument is not an array");
            }

            /* Create a view for the non-null array object. */
            PrimitiveArrayView ref = JNIThreadLocalPrimitiveArrayViews.createArrayView(obj);
            if (isCopy.isNonNull()) {
                isCopy.write(ref.isCopy() ? (byte) 1 : (byte) 0);
            }
            return ref.addressOfArrayElement(0);
        }

        static void destroyNewestArrayViewByAddress(PointerBase address, int mode) {
            JNIThreadLocalPrimitiveArrayViews.destroyNewestArrayViewByAddress(address, mode);
        }

        static void getPrimitiveArrayRegion(JavaKind elementKind, JNIObjectHandle handle, int start, int count, PointerBase buffer) {
            Object obj = JNIObjectHandles.getObject(handle);
            /* Check if we have a non-null array object and if start/count are valid. */
            if (ArrayUtil.isOutOfBounds(obj, start, count)) {
                throw new ArrayIndexOutOfBoundsException();
            }
            if (count > 0) {
                long offset = ConfigurationValues.getObjectLayout().getArrayElementOffset(elementKind, start);
                int elementSize = ConfigurationValues.getObjectLayout().sizeInBytes(elementKind);
                UnsignedWord bytes = WordFactory.unsigned(count).multiply(elementSize);
                JavaMemoryUtil.copyOnHeap(obj, WordFactory.unsigned(offset), null, WordFactory.unsigned(buffer.rawValue()), bytes);
            }
        }

        static void setPrimitiveArrayRegion(JavaKind elementKind, JNIObjectHandle handle, int start, int count, PointerBase buffer) {
            Object obj = JNIObjectHandles.getObject(handle);
            if (ArrayUtil.isOutOfBounds(obj, start, count)) {
                throw new ArrayIndexOutOfBoundsException();
            }
            if (count > 0) {
                long offset = ConfigurationValues.getObjectLayout().getArrayElementOffset(elementKind, start);
                int elementSize = ConfigurationValues.getObjectLayout().sizeInBytes(elementKind);
                UnsignedWord bytes = WordFactory.unsigned(count).multiply(elementSize);
                JavaMemoryUtil.copyOnHeap(null, WordFactory.unsigned(buffer.rawValue()), obj, WordFactory.unsigned(offset), bytes);
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
            throw VMError.shouldNotReachHere("An unimplemented JNI function was called. Please refer to the stack trace.");
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
            throw VMError.shouldNotReachHere("An unimplemented JNI function was called. Please refer to the stack trace.");
        }
    }
}
