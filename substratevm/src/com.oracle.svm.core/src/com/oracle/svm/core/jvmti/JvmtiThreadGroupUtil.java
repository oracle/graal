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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jvmti.headers.JThread;
import com.oracle.svm.core.jvmti.headers.JThreadGroup;
import com.oracle.svm.core.jvmti.headers.JThreadGroupPointer;
import com.oracle.svm.core.jvmti.headers.JThreadGroupPointerPointer;
import com.oracle.svm.core.jvmti.headers.JThreadPointer;
import com.oracle.svm.core.jvmti.headers.JThreadPointerPointer;
import com.oracle.svm.core.jvmti.headers.JvmtiError;
import com.oracle.svm.core.jvmti.headers.JvmtiThreadGroupInfo;
import com.oracle.svm.core.jvmti.utils.JvmtiUtils;
import com.oracle.svm.core.thread.JavaLangThreadGroupSubstitutions;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.NativeVMOperation;
import com.oracle.svm.core.thread.NativeVMOperationData;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.VMThreads;

import jdk.graal.compiler.api.replacements.Fold;

public class JvmtiThreadGroupUtil {

    private final JvmtiGetThreadGroupChildrenOperation getThreadGroupChildrenOperation;
    private static final int INITIAL_THREAD_BUFFER_CAPACITY = 50;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JvmtiThreadGroupUtil() {
        getThreadGroupChildrenOperation = new JvmtiGetThreadGroupChildrenOperation();
    }

    @Fold
    public static JvmtiThreadGroupUtil singleton() {
        return ImageSingletons.lookup(JvmtiThreadGroupUtil.class);
    }

    public static JvmtiError getTopThreadGroups(CIntPointer groupCountPtr, JThreadGroupPointerPointer groupsPtr) {
        ThreadGroup top = PlatformThreads.singleton().systemGroup;
        JThreadGroup topHandle = (JThreadGroup) JNIObjectHandles.createLocal(top);

        JThreadGroupPointer topArray = JvmtiUtils.allocateWordBuffer(1);
        if (topArray.isNull()) {
            return JvmtiError.JVMTI_ERROR_OUT_OF_MEMORY;
        }

        JvmtiUtils.writeWordAtIdxInBuffer(topArray, 0, topHandle);

        ((Pointer) groupsPtr).writeWord(0, topArray);
        groupCountPtr.write(1);
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    public static JvmtiError getThreadGroupInfo(JThreadGroup group, JvmtiThreadGroupInfo infoPtr) {
        CIntPointer errorPtr = StackValue.get(CIntPointer.class);
        ThreadGroup threadGroup = getThreadGroupFromHandle(group, errorPtr);
        if (JvmtiError.fromValue(errorPtr.read()) != JvmtiError.JVMTI_ERROR_NONE) {
            return JvmtiError.fromValue(errorPtr.read());
        }
        ThreadGroup parentGroup = threadGroup.getParent();
        String groupName = threadGroup.getName();
        int groupMaxPriority = threadGroup.getMaxPriority();
        // No longer valid
        boolean isDeamonGroup = false;

        fillThreadGroupInfo(infoPtr, parentGroup, groupName, groupMaxPriority, isDeamonGroup);
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    public static JvmtiError getThreadGroupChildren(JThreadGroup group, CIntPointer threadCountPtr, JThreadPointerPointer threadsPtr,
                    CIntPointer groupCountPtr, JThreadGroupPointer groupsPtr) {
        return singleton().getThreadGroupChildrenInternal(group, threadCountPtr, threadsPtr, groupCountPtr, groupsPtr);
    }

    private JvmtiError getThreadGroupChildrenInternal(JThreadGroup group, CIntPointer threadCountPtr, JThreadPointerPointer threadsPtr,
                    CIntPointer groupCountPtr, JThreadGroupPointer groupsPtr) {
        CIntPointer errorPtr = StackValue.get(CIntPointer.class);

        ThreadGroup threadGroup = getThreadGroupFromHandle(group, errorPtr);
        if (JvmtiError.fromValue(errorPtr.read()) != JvmtiError.JVMTI_ERROR_NONE) {
            return JvmtiError.fromValue(errorPtr.read());
        }

        int size = SizeOf.get(JvmtiThreadGroupVMOperationData.class);
        JvmtiThreadGroupVMOperationData data = StackValue.get(size);
        UnmanagedMemoryUtil.fill((Pointer) data, WordFactory.unsigned(size), (byte) 0);

        int activeCountEstimate = threadGroup.activeCount();
        int activeGroupCountEstimate = threadGroup.activeCount();
        data.setJThreadBufferSize(activeCountEstimate > 0 ? activeCountEstimate : INITIAL_THREAD_BUFFER_CAPACITY);
        data.setJThreadGroupBufferSize(activeGroupCountEstimate > 0 ? activeGroupCountEstimate : INITIAL_THREAD_BUFFER_CAPACITY);

        data.setJThreadGroup(group);
        data.setJvmtiError(JvmtiError.JVMTI_ERROR_NONE.getCValue());

        getThreadGroupChildrenOperation.enqueue(data);
        if (JvmtiError.fromValue(data.getJvmtiError()) != JvmtiError.JVMTI_ERROR_NONE) {
            return JvmtiError.fromValue(data.getJvmtiError());
        }

        threadCountPtr.write(data.getJThreadBufferSize());
        ((Pointer) threadsPtr).writeWord(0, data.getJThreadBuffer());

        groupCountPtr.write(data.getJThreadGroupBufferSize());
        ((Pointer) groupsPtr).writeWord(0, data.getJThreadGroupBuffer());
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    // Could be refactored
    @Uninterruptible(reason = "jvmti getThreadGroupChildren")
    private static void getThreadGroupChildren(JvmtiThreadGroupVMOperationData data) {
        int threadGroupsBufferSize = data.getJThreadGroupBufferSize();
        int threadsBufferSize = data.getJThreadBufferSize();
        JThreadGroupPointer threadGroupsBuffer = JvmtiUtils.allocateWordBuffer(threadGroupsBufferSize);
        JThreadPointer threadsBuffer = JvmtiUtils.allocateWordBuffer(threadsBufferSize);

        ThreadGroup targetThreadGroup = JNIObjectHandles.getObject(data.getJThreadGroup());

        int nbThreadsWritten = 0;
        int nbThreadGroupsWritten = 0;

        for (IsolateThread isolateThread = VMThreads.firstThread(); isolateThread.isNonNull(); isolateThread = VMThreads.nextThread(isolateThread)) {
            Thread thread = PlatformThreads.fromVMThread(isolateThread);
            // "Get all live platform threads that are attached to the VM"
            if (thread == null || thread.isVirtual() || !JavaThreads.isAlive(thread)) {
                continue;
            }
            ThreadGroup currentThreadGroup = JavaThreads.getRawThreadGroup(thread);
            // Collect subgroups
            if (JavaLangThreadGroupSubstitutions.getParentThreadGroupUnsafe(currentThreadGroup) == targetThreadGroup) {
                if (nbThreadGroupsWritten == threadGroupsBufferSize) {
                    threadGroupsBuffer = JvmtiUtils.growWordBuffer(threadGroupsBuffer, 2 * threadGroupsBufferSize);
                    threadGroupsBufferSize = 2 * threadGroupsBufferSize;
                    if (threadGroupsBuffer.isNull()) {
                        data.setJvmtiError(JvmtiError.JVMTI_ERROR_OUT_OF_MEMORY.getCValue());
                        JvmtiUtils.freeWordBuffer(threadsBuffer);
                        return;
                    }
                }
                JThreadGroup threadGroupHandle = (JThreadGroup) JNIObjectHandles.createLocal(currentThreadGroup);
                JvmtiUtils.writeWordAtIdxInBuffer(threadGroupsBuffer, nbThreadGroupsWritten, threadGroupHandle);
                nbThreadGroupsWritten++;
            }
            // Collect threads belonging to group
            if (currentThreadGroup == targetThreadGroup) {
                if (nbThreadsWritten == threadsBufferSize) {
                    threadsBuffer = JvmtiUtils.growWordBuffer(threadsBuffer, 2 * threadsBufferSize);
                    threadsBufferSize = 2 * threadsBufferSize;
                    if (threadsBuffer.isNull()) {
                        data.setJvmtiError(JvmtiError.JVMTI_ERROR_OUT_OF_MEMORY.getCValue());
                        JvmtiUtils.freeWordBuffer(threadGroupsBuffer);
                        return;
                    }
                }
                JThread threadHandle = (JThread) JNIObjectHandles.createLocal(thread);
                JvmtiUtils.writeWordAtIdxInBuffer(threadGroupsBuffer, nbThreadGroupsWritten, threadHandle);
                nbThreadsWritten++;
            }
        }
        data.setJThreadGroupBuffer(threadGroupsBuffer);
        data.setJThreadGroupBufferSize(nbThreadGroupsWritten);
        data.setJThreadBuffer(threadsBuffer);
        data.setJThreadBufferSize(nbThreadsWritten);
    }

    @RawStructure
    private interface JvmtiThreadGroupVMOperationData extends NativeVMOperationData {
        @RawField
        JThreadGroup getJThreadGroup();

        @RawField
        void setJThreadGroup(JThreadGroup ptr);

        @RawField
        void setJvmtiError(int err);

        @RawField
        int getJvmtiError();

        @RawField
        void setJThreadBuffer(JThreadPointer ptr);

        @RawField
        JThreadPointer getJThreadBuffer();

        @RawField
        void setJThreadBufferSize(int size);

        @RawField
        int getJThreadBufferSize();

        @RawField
        void setJThreadGroupBuffer(JThreadGroupPointer ptr);

        @RawField
        JThreadGroupPointer getJThreadGroupBuffer();

        @RawField
        void setJThreadGroupBufferSize(int size);

        @RawField
        int getJThreadGroupBufferSize();
    }

    private static class JvmtiGetThreadGroupChildrenOperation extends NativeVMOperation {
        JvmtiGetThreadGroupChildrenOperation() {
            super(VMOperationInfos.get(JvmtiGetThreadGroupChildrenOperation.class, "Get stack trace jvmti", SystemEffect.SAFEPOINT));
        }

        @Override
        protected void operate(NativeVMOperationData data) {
            getThreadGroupChildren((JvmtiThreadGroupVMOperationData) data);
        }
    }

    private static void fillThreadGroupInfo(JvmtiThreadGroupInfo infoPtr, ThreadGroup parent, String name, int maxPriority, boolean isDaemon) {
        JThreadGroup parentHandle = (JThreadGroup) JNIObjectHandles.createLocal(parent);
        int nameSize = UninterruptibleUtils.String.modifiedUTF8Length(name, true, null);
        CCharPointer nameBuffer = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(WordFactory.unsigned(nameSize));
        UninterruptibleUtils.String.toModifiedUTF8(name, (Pointer) nameBuffer, ((Pointer) nameBuffer).add(nameSize), true);

        infoPtr.setParent(parentHandle);
        infoPtr.setName(nameBuffer);
        infoPtr.setMaxPriority(maxPriority);
        infoPtr.setIsDaemon(isDaemon);
    }

    private static ThreadGroup getThreadGroupFromHandle(JThreadGroup handle, CIntPointer error) {
        ThreadGroup threadGroup;
        try {
            threadGroup = JNIObjectHandles.getObject(handle);
        } catch (ClassCastException | IllegalArgumentException e) {
            error.write(JvmtiError.JVMTI_ERROR_INVALID_THREAD_GROUP.getCValue());
            return null;
        }
        if (threadGroup == null) {
            error.write(JvmtiError.JVMTI_ERROR_INVALID_THREAD_GROUP.getCValue());
            return null;
        }
        error.write(JvmtiError.JVMTI_ERROR_NONE.getCValue());
        return threadGroup;
    }

    private static <T extends PointerBase> T allocateReturnArray(int nbElement) {
        return ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(WordFactory.unsigned(nbElement * ConfigurationValues.getTarget().wordSize));
    }

}
