/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmti;

import static com.oracle.svm.core.heap.RestrictHeapAccess.Access.NO_ALLOCATION;
import static com.oracle.svm.core.jvmti.headers.JvmtiError.JVMTI_ERROR_ACCESS_DENIED;
import static com.oracle.svm.core.jvmti.headers.JvmtiError.JVMTI_ERROR_ILLEGAL_ARGUMENT;
import static com.oracle.svm.core.jvmti.headers.JvmtiError.JVMTI_ERROR_INTERNAL;
import static com.oracle.svm.core.jvmti.headers.JvmtiError.JVMTI_ERROR_INVALID_ENVIRONMENT;
import static com.oracle.svm.core.jvmti.headers.JvmtiError.JVMTI_ERROR_INVALID_EVENT_TYPE;
import static com.oracle.svm.core.jvmti.headers.JvmtiError.JVMTI_ERROR_MUST_POSSESS_CAPABILITY;
import static com.oracle.svm.core.jvmti.headers.JvmtiError.JVMTI_ERROR_NONE;
import static com.oracle.svm.core.jvmti.headers.JvmtiError.JVMTI_ERROR_NULL_POINTER;
import static com.oracle.svm.core.jvmti.headers.JvmtiError.JVMTI_ERROR_OUT_OF_MEMORY;
import static com.oracle.svm.core.jvmti.headers.JvmtiError.JVMTI_ERROR_UNATTACHED_THREAD;

import java.nio.charset.StandardCharsets;

import com.oracle.svm.core.jvmti.headers.BooleanPointer;
import com.oracle.svm.core.jvmti.headers.JThreadGroupPointerPointer;
import com.oracle.svm.core.jvmti.headers.VoidPointerPointer;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CConst;
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.nativeimage.c.type.CFloatPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jni.headers.JNIFieldId;
import com.oracle.svm.core.jni.headers.JNIFieldIdPointerPointer;
import com.oracle.svm.core.jni.headers.JNIMethodId;
import com.oracle.svm.core.jni.headers.JNIMethodIdPointer;
import com.oracle.svm.core.jni.headers.JNIMethodIdPointerPointer;
import com.oracle.svm.core.jni.headers.JNINativeInterface;
import com.oracle.svm.core.jni.headers.JNINativeInterfacePointer;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.core.jvmti.headers.JClass;
import com.oracle.svm.core.jvmti.headers.JClassPointer;
import com.oracle.svm.core.jvmti.headers.JClassPointerPointer;
import com.oracle.svm.core.jvmti.headers.JNIObjectHandlePointer;
import com.oracle.svm.core.jvmti.headers.JNIObjectHandlePointerPointer;
import com.oracle.svm.core.jvmti.headers.JRawMonitorId;
import com.oracle.svm.core.jvmti.headers.JRawMonitorIdPointer;
import com.oracle.svm.core.jvmti.headers.JThread;
import com.oracle.svm.core.jvmti.headers.JThreadGroup;
import com.oracle.svm.core.jvmti.headers.JThreadGroupPointer;
import com.oracle.svm.core.jvmti.headers.JThreadPointer;
import com.oracle.svm.core.jvmti.headers.JThreadPointerPointer;
import com.oracle.svm.core.jvmti.headers.JvmtiCapabilities;
import com.oracle.svm.core.jvmti.headers.JvmtiClassDefinition;
import com.oracle.svm.core.jvmti.headers.JvmtiError;
import com.oracle.svm.core.jvmti.headers.JvmtiErrorPointer;
import com.oracle.svm.core.jvmti.headers.JvmtiEvent;
import com.oracle.svm.core.jvmti.headers.JvmtiEventCallbacks;
import com.oracle.svm.core.jvmti.headers.JvmtiEventMode;
import com.oracle.svm.core.jvmti.headers.JvmtiExtensionFunctionInfoPointer;
import com.oracle.svm.core.jvmti.headers.JvmtiExternalEnv;
import com.oracle.svm.core.jvmti.headers.JvmtiFrameInfoPointer;
import com.oracle.svm.core.jvmti.headers.JvmtiHeapCallbacks;
import com.oracle.svm.core.jvmti.headers.JvmtiHeapObjectCallback;
import com.oracle.svm.core.jvmti.headers.JvmtiHeapRootCallback;
import com.oracle.svm.core.jvmti.headers.JvmtiLineNumberEntryPointer;
import com.oracle.svm.core.jvmti.headers.JvmtiLocalVariableEntryPointer;
import com.oracle.svm.core.jvmti.headers.JvmtiMonitorStackDepthInfoPointer;
import com.oracle.svm.core.jvmti.headers.JvmtiMonitorUsage;
import com.oracle.svm.core.jvmti.headers.JvmtiObjectReferenceCallback;
import com.oracle.svm.core.jvmti.headers.JvmtiPhase;
import com.oracle.svm.core.jvmti.headers.JvmtiStackInfoPointerPointer;
import com.oracle.svm.core.jvmti.headers.JvmtiStackReferenceCallback;
import com.oracle.svm.core.jvmti.headers.JvmtiStartFunctionPointer;
import com.oracle.svm.core.jvmti.headers.JvmtiThreadGroupInfo;
import com.oracle.svm.core.jvmti.headers.JvmtiThreadInfo;
import com.oracle.svm.core.jvmti.headers.JvmtiTimerInfo;
import com.oracle.svm.core.jvmti.headers.JvmtiVersion;

/**
 * Defines all JVMTI functions. This class may only contain methods that are annotated with
 * {@link CEntryPoint}.
 * <p>
 * JVMTI functions are annotated with {@link RestrictHeapAccess} because they must not execute any
 * code that could trigger JVMTI events (could result in endless recursion).
 * <ul>
 * <li>they must not trigger (potentially recursive) JVMTI events</li>
 * <li>they may be called from certain JVMTI event callbacks where we can't execute normal Java code
 * (e.g., out of Java heap memory)</li>
 * </ul>
 */

// TEMP (chaeubl):
// - Do we need to be uninterruptible? No, because some operations may cause a safepoint.
// - Can we allocate Java heap memory? No, because some operations are called from places where
// allocations are disallowed (e.g., JVMTI_EVENT_RESOURCE_EXHAUSTED).
// - Is Java synchronization allow? No.

// TEMP (chaeubl): this still doesn't work...
// - JVMTI_EVENT_EXCEPTION and JVMTI_EVENT_EXCEPTION_CATCH - eventually, this code would have to be
// uninterruptible so that exceptions don't break uninterruptible code. But we can't trigger events
// from uninterruptible code... -> we would need to know if uninterruptible code is currently
// running and only trigger the JVMTI event in certain cases? Or we would need to exclude
// SVM-internal code?
// - JVMTI_EVENT_RESOURCE_EXHAUSTED: a lot of JVMTI/JNI code can be triggered, which is problematic
// because we must not allocate any Java heap memory.
public final class JvmtiFunctions {
    @Platforms(Platform.HOSTED_ONLY.class)
    private JvmtiFunctions() {
    }

    // Checkstyle: stop: MethodName

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int Allocate(JvmtiExternalEnv externalEnv, long size, CCharPointerPointer memPtr) {
        if (memPtr.isNull()) {
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        if (size < 0) {
            memPtr.write(WordFactory.nullPointer());
            return JVMTI_ERROR_ILLEGAL_ARGUMENT.getCValue();
        }
        if (size == 0) {
            memPtr.write(WordFactory.nullPointer());
        } else {
            CCharPointer mem = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(WordFactory.unsigned(size));
            memPtr.write(mem);
            if (mem.isNull()) {
                return JVMTI_ERROR_OUT_OF_MEMORY.getCValue();
            }
        }
        return JVMTI_ERROR_NONE.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int Deallocate(JvmtiExternalEnv externalEnv, CCharPointer mem) {
        if (mem.isNonNull()) {
            ImageSingletons.lookup(UnmanagedMemorySupport.class).free(mem);
        }
        return JVMTI_ERROR_NONE.getCValue();
    }

    // @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetThreadState(JvmtiExternalEnv externalEnv, JThread thread, CIntPointer threadStatePtr) {
        if (threadStatePtr.isNull()) {
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiThreadStateUtil.getThreadState(thread, threadStatePtr);
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetCurrentThread(JvmtiExternalEnv externalEnv, JThreadPointer threadPtr) {
        if (threadPtr.equal(WordFactory.nullPointer())) {
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiGetThreadsUtil.getCurrentThread(threadPtr);
    }

    // @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetAllThreads(JvmtiExternalEnv externalEnv, CIntPointer threadsCountPtr, JThreadPointerPointer threadsPtr) {
        if (threadsCountPtr.isNull() || threadsPtr.isNull()) {
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiGetThreadsUtil.getAllThreads(threadsCountPtr, threadsPtr);
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int SuspendThread(JvmtiExternalEnv externalEnv, JThreadPointer thread) {
       return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int SuspendThreadList(JvmtiExternalEnv externalEnv, int requestCount, @CConst JThreadPointer requestList, JvmtiErrorPointer results) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int SuspendAllVirtualThreads(JvmtiExternalEnv externalEnv, int exceptCount, @CConst JThreadPointer exceptList) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int ResumeThread(JvmtiExternalEnv externalEnv, JThread thread) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int ResumeThreadList(JvmtiExternalEnv externalEnv, int requestCount, @CConst JThreadPointer requestList, JvmtiErrorPointer results) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int ResumeAllVirtualThreads(JvmtiExternalEnv externalEnv, int exceptCount, @CConst JThreadPointer exceptList) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int StopThread(JvmtiExternalEnv externalEnv, JThread thread, JNIObjectHandle exception) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int InterruptThread(JvmtiExternalEnv externalEnv, JThread thread) {
        if (!JvmtiEnvUtil.hasCapability(externalEnv, JvmtiCapabilitiesEnum.CAN_SIGNAL_THREAD)) {
            return JVMTI_ERROR_MUST_POSSESS_CAPABILITY.getCValue();
        }
        return JvmtiThreadActionsUtil.interruptThread(thread);
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetThreadInfo(JvmtiExternalEnv externalEnv, JThread thread, JvmtiThreadInfo infoPtr) {
        if (infoPtr.isNull()) {
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiThreadActionsUtil.getThreadInfo(thread, infoPtr).getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetOwnedMonitorInfo(JvmtiExternalEnv externalEnv, JThread thread, CIntPointer ownedMonitorCountPtr, JNIObjectHandlePointerPointer ownedMonitorsPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetOwnedMonitorStackDepthInfo(JvmtiExternalEnv externalEnv, JThread thread, CIntPointer monitorInfoCountPtr, JvmtiMonitorStackDepthInfoPointer monitorInfoPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetCurrentContendedMonitor(JvmtiExternalEnv externalEnv, JThread thread, JNIObjectHandlePointer monitorPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int RunAgentThread(JvmtiExternalEnv externalEnv, JThread thread, JvmtiStartFunctionPointer proc, @CConst VoidPointer arg, int priority) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int SetThreadLocalStorage(JvmtiExternalEnv externalEnv, JThread thread, @CConst VoidPointer data) {
        return JvmtiThreadLocalStorage.setThreadLocalStorage(thread, data).getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetThreadLocalStorage(JvmtiExternalEnv externalEnv, JThread thread, VoidPointerPointer dataPtr) {
        if(dataPtr.isNull()){
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiThreadLocalStorage.getThreadLocalStorage(thread, dataPtr).getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetTopThreadGroups(JvmtiExternalEnv externalEnv, CIntPointer groupCountPtr, JThreadGroupPointerPointer groupsPtr) {
        if(groupsPtr.isNull() || groupCountPtr.isNull()){
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiThreadGroupUtil.getTopThreadGroups(groupCountPtr, groupsPtr).getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetThreadGroupInfo(JvmtiExternalEnv externalEnv, JThreadGroup group, JvmtiThreadGroupInfo infoPtr) {
        if(infoPtr.isNull()){
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiThreadGroupUtil.getThreadGroupInfo(group, infoPtr).getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetThreadGroupChildren(JvmtiExternalEnv externalEnv, JThreadGroup group, CIntPointer threadCountPtr, JThreadPointerPointer threadsPtr, CIntPointer groupCountPtr,
                    JThreadGroupPointer groupsPtr) {
        if (threadCountPtr.isNull() || threadsPtr.isNull() ||groupCountPtr.isNull() || groupsPtr.isNull()){
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiThreadGroupUtil.getThreadGroupChildren(group, threadCountPtr, threadsPtr, groupCountPtr, groupsPtr).getCValue();
    }

    // @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetStackTrace(JvmtiExternalEnv externalEnv, JThread thread, int startDepth, int maxFrameCount, JvmtiFrameInfoPointer frameBuffer, CIntPointer countPtr) {
        if (maxFrameCount < 0) {
            return JvmtiError.JVMTI_ERROR_ILLEGAL_ARGUMENT.getCValue();
        }
        if (frameBuffer.isNull() || countPtr.isNull()) {
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }

        if (JvmtiStackTraceUtil.startDepthIsOutOfBound(startDepth, SubstrateOptions.maxJavaStackTraceDepth())) {
            return JvmtiError.JVMTI_ERROR_ILLEGAL_ARGUMENT.getCValue();
        }
        return JvmtiStackTraceUtil.getStackTrace(thread, startDepth, maxFrameCount, frameBuffer, countPtr).getCValue();
    }

    // @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetAllStackTraces(JvmtiExternalEnv externalEnv, int maxFrameCount, JvmtiStackInfoPointerPointer stackInfoPtr, CIntPointer threadCountPtr) {
        if (stackInfoPtr.isNull() || threadCountPtr.isNull()) {
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        if (maxFrameCount < 0) {
            return JVMTI_ERROR_ILLEGAL_ARGUMENT.getCValue();
        }

        return JvmtiMultiStackTracesUtil.getAllStackTraces(maxFrameCount, stackInfoPtr, threadCountPtr);
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetThreadListStackTraces(JvmtiExternalEnv externalEnv, int threadCount, @CConst JThreadPointer threadList, int maxFrameCount, JvmtiStackInfoPointerPointer stackInfoPtr) {
        if (stackInfoPtr.isNull() || threadList.isNull()) {
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        if (maxFrameCount < 0 || threadCount < 0) {
            return JVMTI_ERROR_ILLEGAL_ARGUMENT.getCValue();
        }

        return JvmtiMultiStackTracesUtil.getListStackTraces(threadCount, threadList, maxFrameCount, stackInfoPtr);
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetFrameCount(JvmtiExternalEnv externalEnv, JThread thread, CIntPointer countPtr) {
        if(countPtr.isNull()){
            return JvmtiError.JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiStackTraceUtil.getFrameCount(thread, countPtr).getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int PopFrame(JvmtiExternalEnv externalEnv, JThread thread) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetFrameLocation(JvmtiExternalEnv externalEnv, JThread thread, int depth, JNIMethodIdPointer methodPtr, CLongPointer locationPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int NotifyFramePop(JvmtiExternalEnv externalEnv, JThread thread, int depth) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int ForceEarlyReturnObject(JvmtiExternalEnv externalEnv, JThread thread, JNIObjectHandle value) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int ForceEarlyReturnInt(JvmtiExternalEnv externalEnv, JThread thread, int value) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int ForceEarlyReturnLong(JvmtiExternalEnv externalEnv, JThread thread, long value) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int ForceEarlyReturnFloat(JvmtiExternalEnv externalEnv, JThread thread, float value) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int ForceEarlyReturnDouble(JvmtiExternalEnv externalEnv, JThread thread, double value) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int ForceEarlyReturnVoid(JvmtiExternalEnv externalEnv, JThread thread) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int FollowReferences(JvmtiExternalEnv externalEnv, int heapFilter, JClass klass, JNIObjectHandle initialObject, @CConst JvmtiHeapCallbacks callbacks, @CConst VoidPointer userData) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int IterateThroughHeap(JvmtiExternalEnv externalEnv, int heapFilter, JClass klass, @CConst JvmtiHeapCallbacks callbacks, @CConst VoidPointer userData) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetTag(JvmtiExternalEnv externalEnv, JNIObjectHandle object, CLongPointer tagPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int SetTag(JvmtiExternalEnv externalEnv, JNIObjectHandle object, long tag) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetObjectsWithTags(JvmtiExternalEnv externalEnv, int tagCount, @CConst CLongPointer tags, CIntPointer countPtr, JNIObjectHandlePointerPointer objectResultPtr,
                    CLongPointerPointer tagResultPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int ForceGarbageCollection(JvmtiExternalEnv externalEnv) {
        Heap.getHeap().getGC().collectCompletely(GCCause.JvmtiForceGC);
        return JVMTI_ERROR_NONE.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int IterateOverObjectsReachableFromObject(JvmtiExternalEnv externalEnv, JNIObjectHandle object, JvmtiObjectReferenceCallback objectReferenceCallback, @CConst VoidPointer userData) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int IterateOverReachableObjects(JvmtiExternalEnv externalEnv, JvmtiHeapRootCallback heapRootCallback, JvmtiStackReferenceCallback stackRefCallback,
                    JvmtiObjectReferenceCallback objectRefCallback,
                    @CConst VoidPointer userData) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int IterateOverHeap(JvmtiExternalEnv externalEnv, int heapObjectFilter, JvmtiHeapObjectCallback heapObjectCallback, @CConst VoidPointer userData) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int IterateOverInstancesOfClass(JvmtiExternalEnv externalEnv, JClass klass, int heapObjectFilter, JvmtiHeapObjectCallback heapObjectCallback,
                    @CConst VoidPointer userData) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetLocalObject(JvmtiExternalEnv externalEnv, JThread thread, int depth, int slot, JNIObjectHandlePointer valuePtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetLocalInstance(JvmtiExternalEnv externalEnv, JThread thread, int depth, JNIObjectHandlePointer valuePtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetLocalInt(JvmtiExternalEnv externalEnv, JThread thread, int depth, int slot, CIntPointer valuePtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetLocalLong(JvmtiExternalEnv externalEnv, JThread thread, int depth, int slot, CLongPointer valuePtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetLocalFloat(JvmtiExternalEnv externalEnv, JThread thread, int depth, int slot, CFloatPointer valuePtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetLocalDouble(JvmtiExternalEnv externalEnv, JThread thread, int depth, int slot, CDoublePointer valuePtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int SetLocalObject(JvmtiExternalEnv externalEnv, JThread thread, int depth, int slot, JNIObjectHandle value) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int SetLocalInt(JvmtiExternalEnv externalEnv, JThread thread, int depth, int slot, int value) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int SetLocalLong(JvmtiExternalEnv externalEnv, JThread thread, int depth, int slot, long value) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int SetLocalFloat(JvmtiExternalEnv externalEnv, JThread thread, int depth, int slot, float value) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int SetLocalDouble(JvmtiExternalEnv externalEnv, JThread thread, int depth, int slot, double value) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int SetBreakpoint(JvmtiExternalEnv externalEnv, JNIMethodId method, long location) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int ClearBreakpoint(JvmtiExternalEnv externalEnv, JNIMethodId method, long location) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int SetFieldAccessWatch(JvmtiExternalEnv externalEnv, JClass klass, JNIFieldId field) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int ClearFieldAccessWatch(JvmtiExternalEnv externalEnv, JClass klass, JNIFieldId field) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int SetFieldModificationWatch(JvmtiExternalEnv externalEnv, JClass klass, JNIFieldId field) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int ClearFieldModificationWatch(JvmtiExternalEnv externalEnv, JClass klass, JNIFieldId field) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetAllModules(JvmtiExternalEnv externalEnv, CIntPointer moduleCountPtr, JNIObjectHandlePointerPointer modulesPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetNamedModule(JvmtiExternalEnv externalEnv, JNIObjectHandle classLoader, @CConst CCharPointer packageName, JNIObjectHandlePointer modulePtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int AddModuleReads(JvmtiExternalEnv externalEnv, JNIObjectHandle module, JNIObjectHandle toModule) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int AddModuleExports(JvmtiExternalEnv externalEnv, JNIObjectHandle module, @CConst CCharPointer pkgName, JNIObjectHandle toModule) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int AddModuleOpens(JvmtiExternalEnv externalEnv, JNIObjectHandle module, @CConst CCharPointer pkgName, JNIObjectHandle toModule) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int AddModuleUses(JvmtiExternalEnv externalEnv, JNIObjectHandle module, JClass service) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int AddModuleProvides(JvmtiExternalEnv externalEnv, JNIObjectHandle module, JClass service, JClass impl_class) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int IsModifiableModule(JvmtiExternalEnv externalEnv, JNIObjectHandle module, BooleanPointer isModifiableModulePtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetLoadedClasses(JvmtiExternalEnv externalEnv, CIntPointer classCountPtr, JClassPointerPointer classesPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetClassLoaderClasses(JvmtiExternalEnv externalEnv, JNIObjectHandle initiatingLoader, CIntPointer classCountPtr, JClassPointerPointer classesPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    // @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetClassSignature(JvmtiExternalEnv externalEnv, JClass klass, CCharPointerPointer signaturePtr, CCharPointerPointer genericPtr) {
        return JvmtiClassInfoUtil.getClassSignature(klass, signaturePtr, genericPtr).getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetClassStatus(JvmtiExternalEnv externalEnv, JClass klass, CIntPointer statusPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetSourceFileName(JvmtiExternalEnv externalEnv, JClass klass, CCharPointerPointer sourceNamePtr) {
        if(sourceNamePtr.isNull()){
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiClassInfoUtil.getSourceFileName(klass, sourceNamePtr).getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetClassModifiers(JvmtiExternalEnv externalEnv, JClass klass, CIntPointer modifiersPtr) {
        if(modifiersPtr.isNull()){
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiClassInfoUtil.getClassModifiers(klass, modifiersPtr).getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetClassMethods(JvmtiExternalEnv externalEnv, JClass klass, CIntPointer methodCountPtr, JNIMethodIdPointerPointer methodsPtr) {
        if(methodCountPtr.isNull() || methodsPtr.isNull()){
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiClassInfoUtil.getClassMethods(klass, methodCountPtr, methodsPtr).getCValue();

    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetClassFields(JvmtiExternalEnv externalEnv, JClass klass, CIntPointer fieldCountPtr, JNIFieldIdPointerPointer fieldsPtr) {
        if(fieldCountPtr.isNull() || fieldsPtr.isNull()){
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiClassInfoUtil.getClassFields(klass, fieldCountPtr, fieldsPtr).getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetImplementedInterfaces(JvmtiExternalEnv externalEnv, JClass klass, CIntPointer interfaceCountPtr, JClassPointerPointer interfacesPtr) {
        if(interfacesPtr.isNull() || interfaceCountPtr.isNull()){
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiClassInfoUtil.getImplementedInterfaces(klass, interfaceCountPtr, interfacesPtr).getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetClassVersionNumbers(JvmtiExternalEnv externalEnv, JClass klass, CIntPointer minorVersionPtr, CIntPointer majorVersionPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetConstantPool(JvmtiExternalEnv externalEnv, JClass klass, CIntPointer constantPoolCountPtr, CIntPointer constantPoolByteCountPtr, CCharPointerPointer constantPoolBytesPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int IsInterface(JvmtiExternalEnv externalEnv, JClass klass, BooleanPointer isInterfacePtr) {
        if(isInterfacePtr.isNull()){
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiClassInfoUtil.isInterface(klass, isInterfacePtr).getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int IsArrayClass(JvmtiExternalEnv externalEnv, JClass klass, BooleanPointer isArrayClassPtr) {
        if(isArrayClassPtr.isNull()){
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiClassInfoUtil.isArrayClass(klass, isArrayClassPtr).getCValue();
    }

   // @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int IsModifiableClass(JvmtiExternalEnv externalEnv, JClass klass, BooleanPointer isModifiableClassPtr) {
        if(isModifiableClassPtr.isNull()){
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiClassInfoUtil.isModifiableClass(klass, isModifiableClassPtr).getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetClassLoader(JvmtiExternalEnv externalEnv, JClass klass, JNIObjectHandlePointer classloaderPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetSourceDebugExtension(JvmtiExternalEnv externalEnv, JClass klass, CCharPointerPointer sourceDebugExtensionPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int RetransformClasses(JvmtiExternalEnv externalEnv, int classCount, @CConst JClassPointer classes) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int RedefineClasses(JvmtiExternalEnv externalEnv, int classCount, @CConst JvmtiClassDefinition classDefinitions) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetObjectSize(JvmtiExternalEnv externalEnv, JNIObjectHandle object, CLongPointer sizePtr) {
        if (sizePtr.isNull()) {
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiObjectInfoUttil.getObjectSize(object, sizePtr).getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetObjectHashCode(JvmtiExternalEnv externalEnv, JNIObjectHandle object, CIntPointer hashCodePtr) {
        if (hashCodePtr.isNull()) {
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiObjectInfoUttil.getHashCode(object, hashCodePtr).getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetObjectMonitorUsage(JvmtiExternalEnv externalEnv, JNIObjectHandle object, JvmtiMonitorUsage infoPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetFieldName(JvmtiExternalEnv externalEnv, JClass klass, JNIFieldId field, CCharPointerPointer namePtr, CCharPointerPointer signaturePtr, CCharPointerPointer genericPtr) {
        return JvmtiClassInfoUtil.getFieldName(klass, field, namePtr, signaturePtr, genericPtr).getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetFieldDeclaringClass(JvmtiExternalEnv externalEnv, JClass klass, JNIFieldId field, JClassPointer declaringClassPtr) {
        if (declaringClassPtr.isNull()){
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiClassInfoUtil.getFieldDeclaringClass(klass, field, declaringClassPtr).getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetFieldModifiers(JvmtiExternalEnv externalEnv, JClass klass, JNIFieldId field, CIntPointer modifiersPtr) {
        if (modifiersPtr.isNull()){
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiClassInfoUtil.getFieldModifiers(klass, field, modifiersPtr).getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int IsFieldSynthetic(JvmtiExternalEnv externalEnv, JClass klass, JNIFieldId field, BooleanPointer isSyntheticPtr) {
        if (isSyntheticPtr.isNull()){
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        if(!JvmtiEnvUtil.hasCapability(externalEnv, JvmtiCapabilitiesEnum.CAN_GET_SYNTHETIC_ATTRIBUTE)){
            return JVMTI_ERROR_MUST_POSSESS_CAPABILITY.getCValue();
        }
        return JvmtiClassInfoUtil.isFieldSynthetic(klass, field, isSyntheticPtr).getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetMethodName(JvmtiExternalEnv externalEnv, JNIMethodId method, CCharPointerPointer namePtr, CCharPointerPointer signaturePtr, CCharPointerPointer genericPtr) {
        return JvmtiClassInfoUtil.getMethodName(method, namePtr, signaturePtr, genericPtr).getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetMethodDeclaringClass(JvmtiExternalEnv externalEnv, JNIMethodId method, JClassPointer declaringClassPtr) {
        if (declaringClassPtr.isNull()) {
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiClassInfoUtil.getMethodDeclaringClass(method, declaringClassPtr).getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetMethodModifiers(JvmtiExternalEnv externalEnv, JNIMethodId method, CIntPointer modifiersPtr) {
        if(modifiersPtr.isNull()){
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiClassInfoUtil.getMethodModifiers(method, modifiersPtr).getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetMaxLocals(JvmtiExternalEnv externalEnv, JNIMethodId method, CIntPointer maxPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetArgumentsSize(JvmtiExternalEnv externalEnv, JNIMethodId method, CIntPointer sizePtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetLineNumberTable(JvmtiExternalEnv externalEnv, JNIMethodId method, CIntPointer entryCountPtr, JvmtiLineNumberEntryPointer tablePtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetMethodLocation(JvmtiExternalEnv externalEnv, JNIMethodId method, CLongPointer startLocationPtr, CLongPointer endLocationPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetLocalVariableTable(JvmtiExternalEnv externalEnv, JNIMethodId method, CIntPointer entryCountPtr, JvmtiLocalVariableEntryPointer tablePtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetBytecodes(JvmtiExternalEnv externalEnv, JNIMethodId method, CIntPointer bytecodeCountPtr, CCharPointerPointer bytecodesPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int IsMethodNative(JvmtiExternalEnv externalEnv, JNIMethodId method, BooleanPointer isNativePtr) {
        if(isNativePtr.isNull()){
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiClassInfoUtil.isMethodNative(method, isNativePtr).getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int IsMethodSynthetic(JvmtiExternalEnv externalEnv, JNIMethodId method, BooleanPointer isSyntheticPtr) {
        if(isSyntheticPtr.isNull()){
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        if(!JvmtiEnvUtil.hasCapability(externalEnv, JvmtiCapabilitiesEnum.CAN_GET_SYNTHETIC_ATTRIBUTE)){
            return JVMTI_ERROR_MUST_POSSESS_CAPABILITY.getCValue();
        }
        return JvmtiClassInfoUtil.isMethodSynthetic(method, isSyntheticPtr).getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int IsMethodObsolete(JvmtiExternalEnv externalEnv, JNIMethodId method, BooleanPointer isObsoletePtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int SetNativeMethodPrefix(JvmtiExternalEnv externalEnv, @CConst CCharPointer prefix) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int SetNativeMethodPrefixes(JvmtiExternalEnv externalEnv, int prefixCount, CCharPointerPointer prefixes) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int CreateRawMonitor(JvmtiExternalEnv externalEnv, @CConst CCharPointer name, JRawMonitorIdPointer monitorPtr) {
        return JvmtiRawMonitorUtil.createRawMonitor(name, monitorPtr).getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int DestroyRawMonitor(JvmtiExternalEnv externalEnv, JRawMonitorId monitor) {
        return JvmtiRawMonitorUtil.destroyRawMonitor(monitor).getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int RawMonitorEnter(JvmtiExternalEnv externalEnv, JRawMonitorId monitor) {
        return JvmtiRawMonitorUtil.rawMonitorEnter(monitor).getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int RawMonitorExit(JvmtiExternalEnv externalEnv, JRawMonitorId monitor) {
        return JvmtiRawMonitorUtil.rawMonitorExit(monitor).getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int RawMonitorWait(JvmtiExternalEnv externalEnv, JRawMonitorId monitor, long millis) {
        return JvmtiRawMonitorUtil.rawMonitorWait(monitor, millis).getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int RawMonitorNotify(JvmtiExternalEnv externalEnv, JRawMonitorId monitor) {
        return JvmtiRawMonitorUtil.rawMonitorNotify(monitor).getCValue();
    }

    //@RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int RawMonitorNotifyAll(JvmtiExternalEnv externalEnv, JRawMonitorId monitor) {
        return JvmtiRawMonitorUtil.rawMonitorNotifyAll(monitor).getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int SetJNIFunctionTable(JvmtiExternalEnv externalEnv, @CConst JNINativeInterface functionTable) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetJNIFunctionTable(JvmtiExternalEnv externalEnv, JNINativeInterfacePointer functionTable) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int SetEventCallbacks(JvmtiExternalEnv externalEnv, @CConst JvmtiEventCallbacks callbacks, int sizeOfCallbacks) {
        if (sizeOfCallbacks <= 0) {
            return JVMTI_ERROR_ILLEGAL_ARGUMENT.getCValue();
        }

        JvmtiEnv env = JvmtiEnvUtil.toInternal(externalEnv);
        JvmtiEnvUtil.setEventCallbacks(env, callbacks, sizeOfCallbacks);

        return JVMTI_ERROR_NONE.getCValue();
    }

    // TODO dprcci disabled temporarily
    // @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int SetEventNotificationMode(JvmtiExternalEnv externalEnv, int eventMode, JvmtiEvent eventType, JThread eventThread) {
        // TEMP (chaeubl): this method uses varargs... do we need a native wrapper to deal with
        // that? seems that this is only reserved for future expansion but may still be tricky.

        if (eventType == null || !JvmtiEnvEventEnabledUtils.isInValidEventRange(eventType)) {
            return JVMTI_ERROR_INVALID_EVENT_TYPE.getCValue();
        } else if (!eventType.isSupported()) {
            return JVMTI_ERROR_ACCESS_DENIED.getCValue();
        }

        if (eventMode != JvmtiEventMode.JVMTI_ENABLE() && eventMode != JvmtiEventMode.JVMTI_DISABLE()) {
            return JVMTI_ERROR_ILLEGAL_ARGUMENT.getCValue();
        }

        /* Check that the needed capabilities are present. */
        boolean enable = (eventMode == JvmtiEventMode.JVMTI_ENABLE());
        JvmtiEnv env = JvmtiEnvUtil.toInternal(externalEnv);
        if (enable && !JvmtiEnvUtil.hasEventCapability(env, eventType)) {
            return JVMTI_ERROR_MUST_POSSESS_CAPABILITY.getCValue();
        }

        Thread javaEventThread = JNIObjectHandles.getObject(eventThread);
        if (javaEventThread == null) {
            /* Change global event status. */
            JvmtiEnvUtil.setEventUserEnabled(env, null, eventType, enable);
        } else {
            /* Change thread-local event status. */
            if (eventType.isGlobal()) {
                /* Global events cannot be controlled at thread level. */
                return JVMTI_ERROR_ILLEGAL_ARGUMENT.getCValue();
            }
            /* At the moment, we don't support enabling events for specific threads. */
            return JVMTI_ERROR_ACCESS_DENIED.getCValue();
        }

        return JVMTI_ERROR_NONE.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GenerateEvents(JvmtiExternalEnv externalEnv, int eventType) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetExtensionFunctions(JvmtiExternalEnv externalEnv, CIntPointer extensionCountPtr, JvmtiExtensionFunctionInfoPointer extensions) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetExtensionEvents(JvmtiExternalEnv externalEnv, CIntPointer extensionCountPtr, JvmtiExtensionFunctionInfoPointer extensions) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int SetExtensionEventCallback(JvmtiExternalEnv externalEnv, int extensionEventIndex, CFunctionPointer callback) {
        /* The callback is a vararg callback that we can't model easily. */
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetPotentialCapabilities(JvmtiExternalEnv externalEnv, JvmtiCapabilities result) {
        if (result.isNull()) {
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }

        JvmtiEnv env = JvmtiEnvUtil.toInternal(externalEnv);
        JvmtiEnvUtil.getPotentialCapabilities(env, result);
        /* We don't support any capabilities at the moment. *//*
                                                               * JvmtiCapabilitiesUtil.clear(result)
                                                               * ;
                                                               */
        return JVMTI_ERROR_NONE.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetCapabilities(JvmtiExternalEnv externalEnv, JvmtiCapabilities result) {
        if (result.isNull()) {
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }

        JvmtiEnv env = JvmtiEnvUtil.toInternal(externalEnv);
        JvmtiCapabilitiesUtil.copy(JvmtiEnvUtil.getCapabilities(env), result);
        return JVMTI_ERROR_NONE.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int AddCapabilities(JvmtiExternalEnv externalEnv, @CConst JvmtiCapabilities capabilities) {
        if (capabilities.isNull()) {
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        JvmtiEnv env = JvmtiEnvUtil.toInternal(externalEnv);
        return JvmtiEnvUtil.addCapabilities(env, capabilities).getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int RelinquishCapabilities(JvmtiExternalEnv externalEnv, @CConst JvmtiCapabilities capabilities) {
        if (capabilities.isNull()) {
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }

        JvmtiEnv env = JvmtiEnvUtil.toInternal(externalEnv);
        return JvmtiEnvUtil.relinquishCapabilities(env, capabilities).getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetCurrentThreadCpuTimerInfo(JvmtiExternalEnv externalEnv, JvmtiTimerInfo infoPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetCurrentThreadCpuTime(JvmtiExternalEnv externalEnv, CLongPointer nanosPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetThreadCpuTimerInfo(JvmtiExternalEnv externalEnv, JvmtiTimerInfo infoPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetThreadCpuTime(JvmtiExternalEnv externalEnv, JThread thread, CLongPointer nanosPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetTimerInfo(JvmtiExternalEnv externalEnv, JvmtiTimerInfo infoPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetTime(JvmtiExternalEnv externalEnv, CLongPointer nanosPtr) {
        if (nanosPtr.isNull()) {
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        nanosPtr.write(System.nanoTime());
        return JVMTI_ERROR_NONE.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetAvailableProcessors(JvmtiExternalEnv externalEnv, CIntPointer processorCountPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int AddToBootstrapClassLoaderSearch(JvmtiExternalEnv externalEnv, @CConst CCharPointer segment) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int AddToSystemClassLoaderSearch(JvmtiExternalEnv externalEnv, @CConst CCharPointer segment) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetSystemProperties(JvmtiExternalEnv externalEnv, CIntPointer countPtr, CCharPointerPointerPointer propertyPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetSystemProperty(JvmtiExternalEnv externalEnv, @CConst CCharPointer property, CCharPointerPointer valuePtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int SetSystemProperty(JvmtiExternalEnv externalEnv, @CConst CCharPointer property, @CConst CCharPointer valuePtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetPhase(JvmtiExternalEnv externalEnv, CIntPointer phasePtr) {
        if (phasePtr.isNull()) {
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        phasePtr.write(JvmtiPhase.JVMTI_PHASE_LIVE());
        return JVMTI_ERROR_NONE.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int DisposeEnvironment(JvmtiExternalEnv externalEnv) {
        JvmtiEnv env = JvmtiEnvUtil.toInternal(externalEnv);
        JvmtiEnvManager manager = JvmtiEnvManager.singleton();
        manager.destroyJvmtiEnv(env);
        return JVMTI_ERROR_NONE.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int SetEnvironmentLocalStorage(JvmtiExternalEnv externalEnv, @CConst VoidPointer data) {
        if(JvmtiEnvUtil.isValid(JvmtiEnvUtil.toInternal(externalEnv))){
            return JVMTI_ERROR_INVALID_ENVIRONMENT.getCValue();
        }
        return JvmtiEnvStorage.setEnvironmentStorage(externalEnv, data).getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetEnvironmentLocalStorage(JvmtiExternalEnv externalEnv, VoidPointerPointer dataPtr) {
        if(JvmtiEnvUtil.isValid(JvmtiEnvUtil.toInternal(externalEnv))){
            return JVMTI_ERROR_INVALID_ENVIRONMENT.getCValue();
        }
        if(dataPtr.isNull()){
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        return JvmtiEnvStorage.getEnvironmentStorage(externalEnv, dataPtr).getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetVersionNumber(JvmtiExternalEnv externalEnv, CIntPointer versionPtr) {
        if (versionPtr.isNull()) {
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }
        versionPtr.write(JvmtiVersion.CURRENT_VERSION);
        return JVMTI_ERROR_NONE.getCValue();
    }

    // TODO dprcci disabled temporarily
    // @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetErrorName(JvmtiExternalEnv externalEnv, JvmtiError jvmtiError, CCharPointerPointer namePtr) {
        if (namePtr.isNull()) {
            return JVMTI_ERROR_NULL_POINTER.getCValue();
        }

        if (jvmtiError == null) {
            return JVMTI_ERROR_ILLEGAL_ARGUMENT.getCValue();
        }

        /* Convert name to ASCII. */
        String name = jvmtiError.name();
        UnsignedWord bufferSize = WordFactory.unsigned(name.length() + 1);
        CCharPointer mem = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(bufferSize);
        namePtr.write(mem);
        if (mem.isNull()) {
            return JVMTI_ERROR_OUT_OF_MEMORY.getCValue();
        }

        CTypeConversion.toCString(name, StandardCharsets.US_ASCII, mem, bufferSize);
        return JVMTI_ERROR_NONE.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int SetVerboseFlag(JvmtiExternalEnv externalEnv, int verboseFlag, boolean value) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int GetJLocationFormat(JvmtiExternalEnv externalEnv, CIntPointer formatPtr) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "JVMTI function.")
    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = JvmtiEnvEnterPrologue.class)
    static int SetHeapSamplingInterval(JvmtiExternalEnv externalEnv, int samplingInterval) {
        return JVMTI_ERROR_ACCESS_DENIED.getCValue();
    }

    // Checkstyle: resume

    private static class JvmtiEnvEnterPrologue implements CEntryPointOptions.Prologue {
        @Uninterruptible(reason = "prologue")
        public static int enter(JvmtiExternalEnv externalEnv) {
            // TEMP (chaeubl): we might need special logic again to avoid objects in the
            // reference map before the heap base is set. Or, we make sure that the values are
            // constant folded (via @Fold).
            if (externalEnv.isNull()) {
                return JVMTI_ERROR_INVALID_ENVIRONMENT.getCValue();
            }

            JvmtiEnv env = JvmtiEnvUtil.toInternal(externalEnv);
            if (!JvmtiEnvUtil.isValid(env)) {
                return JVMTI_ERROR_INVALID_ENVIRONMENT.getCValue();
            }

            int error = CEntryPointActions.enterByIsolate(JvmtiEnvUtil.getIsolate(env));
            if (error == CEntryPointErrors.UNATTACHED_THREAD) {
                return JVMTI_ERROR_UNATTACHED_THREAD.getCValue();
            } else if (error != CEntryPointErrors.NO_ERROR) {
                return JVMTI_ERROR_INTERNAL.getCValue();
            }

            return JVMTI_ERROR_NONE.getCValue();
        }
    }

    @CPointerTo(CCharPointerPointer.class)
    private interface CCharPointerPointerPointer extends PointerBase {
    }

    @CPointerTo(CLongPointer.class)
    private interface CLongPointerPointer extends PointerBase {
    }

}
