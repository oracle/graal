/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.resident;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

import org.graalvm.nativeimage.IsolateThread;

import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.FrameSourceInfo;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.interpreter.InterpreterFrameSourceInfo;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.ThreadSuspendSupport;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.interpreter.DebuggerSupport;
import com.oracle.svm.interpreter.debug.DebuggerEvents;
import com.oracle.svm.interpreter.debug.EventKind;
import com.oracle.svm.interpreter.debug.Location;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterUniverse;
import com.oracle.svm.jdwp.bridge.ErrorCode;
import com.oracle.svm.jdwp.bridge.JDWP;
import com.oracle.svm.jdwp.bridge.JDWPBridge;
import com.oracle.svm.jdwp.bridge.JDWPException;
import com.oracle.svm.jdwp.bridge.Packet;
import com.oracle.svm.jdwp.bridge.StackFrame;
import com.oracle.svm.jdwp.bridge.TypeTag;
import com.oracle.svm.jdwp.resident.impl.AllJavaFramesVisitor;
import com.oracle.svm.jdwp.resident.impl.ResidentJDWP;
import com.oracle.svm.jdwp.resident.impl.SafeStackWalker;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class JDWPBridgeImpl implements JDWPBridge {

    JDWP impl = new ResidentJDWP();

    private static final ObjectIdMap IDS = new ObjectIdMap();

    public static ObjectIdMap getIds() {
        return IDS;
    }

    @Override
    public void setEventEnabled(long threadId, int eventKind, boolean enable) {
        DebuggerEvents.singleton().setEventEnabled(getIds().toObject(threadId, Thread.class), EventKind.fromOrdinal(eventKind), enable);
    }

    @Override
    public boolean isEventEnabled(long threadId, int eventKind) {
        return DebuggerEvents.singleton().isEventEnabled(getIds().toObject(threadId, Thread.class), EventKind.fromOrdinal(eventKind));
    }

    @Override
    public void toggleBreakpoint(long methodId, int bci, boolean enable) {
        DebuggerEvents.singleton().toggleBreakpoint(getIds().toObject(methodId, ResolvedJavaMethod.class), bci, enable);
    }

    @Override
    public void toggleMethodEnterEvent(long clazzId, boolean enable) {
        DebuggerEvents.singleton().toggleMethodEnterEvent(getIds().toObject(clazzId, ResolvedJavaType.class), enable);
    }

    @Override
    public void toggleMethodExitEvent(long clazzId, boolean enable) {
        DebuggerEvents.singleton().toggleMethodExitEvent(getIds().toObject(clazzId, ResolvedJavaType.class), enable);
    }

    @Override
    public void setSteppingFromLocation(long threadId, int depth, int size, long methodId, int bci, int lineNumber) {
        DebuggerEvents.singleton().setSteppingFromLocation(getIds().toObject(threadId, Thread.class), depth, size,
                        Location.create(getIds().toObject(methodId, ResolvedJavaMethod.class), bci, lineNumber));
    }

    @Override
    public void clearStepping(long threadId) {
        DebuggerEvents.singleton().clearStepping(getIds().toObject(threadId, Thread.class));
    }

    @Override
    public Packet dispatch(Packet packet) throws JDWPException {
        return impl.dispatch(packet);
    }

    @Override
    public int getThreadStatus(long threadId) {
        Thread thread = ResidentJDWP.getThread(threadId);
        return JDWPThreadStatus.getThreadStatus(thread);
    }

    @Override
    public long threadSuspend(long threadId) {
        return threadSuspendOrResume(true, threadId);
    }

    @Override
    public long threadResume(long threadId) {
        return threadSuspendOrResume(false, threadId);
    }

    private static long threadSuspendOrResume(boolean suspend, long threadId) {
        Thread thread = ResidentJDWP.getThread(threadId);
        if (!thread.isAlive()) {
            // An invalid thread (zombie)
            return -1;
        }
        if (suspend) {
            ThreadSuspendSupport.suspend(thread);
        } else {
            ThreadSuspendSupport.resume(thread);
        }
        return 1;
    }

    @Override
    public void setThreadRequest(boolean start, boolean enable) {
        ThreadStartDeathSupport.get().setListeningOn(start, enable);
    }

    @Override
    public long[] vmSuspend(long[] ignoredThreadIds) {
        SuspendAllOperation operation = new SuspendAllOperation(ignoredThreadIds);
        operation.enqueue();
        return operation.getSuspendedThreadIds();
    }

    @Override
    public void vmResume(long[] resumeThreadIds) {
        if (!DebuggingOnDemandHandler.suspendDoneOnShellSide()) {
            ResumeAllOperation operation = new ResumeAllOperation(resumeThreadIds);
            operation.enqueue();
        }
    }

    private static class SuspendAllOperation extends JavaVMOperation {

        private final Thread[] ignoredThreads;
        private Thread[] suspendedThreads;
        private int suspendedThreadsCount;

        SuspendAllOperation(long[] ignoredThreadIds) {
            super(VMOperationInfos.get(SuspendAllOperation.class, "All Threads Suspend", VMOperation.SystemEffect.SAFEPOINT));
            ignoredThreads = new Thread[ignoredThreadIds.length];
            for (int i = 0; i < ignoredThreadIds.length; i++) {
                ignoredThreads[i] = JDWPBridgeImpl.getIds().toObject(ignoredThreadIds[i], Thread.class);
            }
        }

        @Override
        protected void operate() {
            Thread[] threads = new Thread[10];
            int i = 0;
            threadsLoop: for (IsolateThread thread = VMThreads.firstThreadUnsafe(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
                Thread t = ThreadStartDeathSupport.get().filterAppThread(thread);
                if (t == null || PlatformThreads.getThreadStatus(t) == com.oracle.svm.core.thread.ThreadStatus.TERMINATED) {
                    continue;
                }
                for (Thread ignoredThread : ignoredThreads) {
                    if (ignoredThread == t) {
                        continue threadsLoop;
                    }
                }
                ThreadSuspendSupport.suspend(t);
                if (i >= threads.length) {
                    threads = Arrays.copyOf(threads, i + i / 2);
                }
                threads[i++] = t;
            }
            suspendedThreads = threads;
            suspendedThreadsCount = i;
        }

        long[] getSuspendedThreadIds() {
            long[] ids = new long[suspendedThreadsCount];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = JDWPBridgeImpl.getIds().getIdOrCreateWeak(suspendedThreads[i]);
            }
            return ids;
        }
    }

    private static class ResumeAllOperation extends JavaVMOperation {

        private final Thread[] resumeThreads;

        ResumeAllOperation(long[] resumeThreadIds) {
            super(VMOperationInfos.get(ResumeAllOperation.class, "All Threads Resume", VMOperation.SystemEffect.SAFEPOINT));
            resumeThreads = new Thread[resumeThreadIds.length];
            for (int i = 0; i < resumeThreadIds.length; i++) {
                resumeThreads[i] = JDWPBridgeImpl.getIds().toObject(resumeThreadIds[i], Thread.class);
            }
        }

        @Override
        protected void operate() {
            for (Thread thread : resumeThreads) {
                if (thread == null) {
                    continue;
                }
                ThreadSuspendSupport.resume(thread);
            }
        }
    }

    @Override
    public String getSystemProperty(String key) {
        return System.getProperty(key);
    }

    @Override
    public long typeRefIndexToId(int typeRefIndex) {
        ResolvedJavaType typeAtIndex = DebuggerSupport.singleton().getUniverse().getTypeAtIndex(typeRefIndex);
        return getIds().getIdOrCreateWeak(typeAtIndex);
    }

    @Override
    public long fieldRefIndexToId(int fieldRefIndex) {
        ResolvedJavaField fieldAtIndex = DebuggerSupport.singleton().getUniverse().getFieldAtIndex(fieldRefIndex);
        return getIds().getIdOrCreateWeak(fieldAtIndex);
    }

    @Override
    public long methodRefIndexToId(int methodRefIndex) {
        ResolvedJavaMethod methodAtIndex = DebuggerSupport.singleton().getUniverse().getMethodAtIndex(methodRefIndex);
        return getIds().getIdOrCreateWeak(methodAtIndex);
    }

    @Override
    public int typeRefIdToIndex(long typeRefId) {
        ResolvedJavaType resolvedJavaType = null;
        try {
            resolvedJavaType = getIds().toObject(typeRefId, ResolvedJavaType.class);
        } catch (ClassCastException e) {
            throw JDWPException.raise(ErrorCode.INVALID_CLASS);
        }
        if (resolvedJavaType == null) {
            throw JDWPException.raise(ErrorCode.INVALID_OBJECT);
        }
        OptionalInt methodRefIndex = DebuggerSupport.singleton().getUniverse().getTypeIndexFor(resolvedJavaType);
        return methodRefIndex.orElseThrow(() -> JDWPException.raise(ErrorCode.INVALID_CLASS));
    }

    @Override
    public int fieldRefIdToIndex(long fieldRefId) {
        ResolvedJavaField resolvedJavaField = null;
        try {
            resolvedJavaField = getIds().toObject(fieldRefId, ResolvedJavaField.class);
        } catch (ClassCastException e) {
            throw JDWPException.raise(ErrorCode.INVALID_FIELDID);
        }
        if (resolvedJavaField == null) {
            throw JDWPException.raise(ErrorCode.INVALID_OBJECT);
        }
        OptionalInt typeRefIndex = DebuggerSupport.singleton().getUniverse().getFieldIndexFor(resolvedJavaField);
        return typeRefIndex.orElseThrow(() -> JDWPException.raise(ErrorCode.INVALID_FIELDID));
    }

    @Override
    public int methodRefIdToIndex(long methodRefId) {
        ResolvedJavaMethod resolvedJavaMethod = null;
        try {
            resolvedJavaMethod = getIds().toObject(methodRefId, ResolvedJavaMethod.class);
        } catch (ClassCastException e) {
            throw JDWPException.raise(ErrorCode.INVALID_METHODID);
        }
        if (resolvedJavaMethod == null) {
            throw JDWPException.raise(ErrorCode.INVALID_OBJECT);
        }
        OptionalInt methodRefIndex = DebuggerSupport.singleton().getUniverse().getMethodIndexFor(resolvedJavaMethod);
        return methodRefIndex.orElseThrow(() -> JDWPException.raise(ErrorCode.INVALID_METHODID));
    }

    @Override
    public String currentWorkingDirectory() {
        return Path.of("").toAbsolutePath().toString();
    }

    @Override
    public int[] typeStatus(long... typeIds) {
        int[] result = new int[typeIds.length];
        for (int i = 0; i < typeIds.length; i++) {
            long typeId = typeIds[i];
            Object type = getIds().getObject(typeId);
            if (type instanceof ResolvedJavaType resolvedJavaType) {
                // Check that the type is part of the interpreter universe, e.g. arbitrary
                // ResolvedJavaType instances are not valid.
                OptionalInt typeIndex = DebuggerSupport.singleton().getUniverse().getTypeIndexFor(resolvedJavaType);
                if (typeIndex.isEmpty()) {
                    throw JDWPException.raise(ErrorCode.INVALID_CLASS);
                }
                result[i] = ClassUtils.getStatus(resolvedJavaType);
            } else {
                throw JDWPException.raise(ErrorCode.INVALID_CLASS);
            }
        }
        return result;
    }

    @Override
    public StackFrame[] getThreadFrames(long threadId) {
        Thread targetThread = JDWPBridgeImpl.getIds().toObject(threadId, Thread.class);
        AllJavaFramesVisitor getAllFrames = new AllJavaFramesVisitor(true);
        SafeStackWalker.safeStackWalk(targetThread, getAllFrames);

        List<FrameSourceInfo> frames = getAllFrames.getFrames();

        StackFrame[] stackFrames = new StackFrame[frames.size()];
        for (int i = 0; i < stackFrames.length; i++) {
            FrameSourceInfo frameInfo = frames.get(i);

            Class<?> sourceClass = frameInfo.getSourceClass();
            ResolvedJavaType sourceType = DebuggerSupport.singleton().getUniverse().lookupType(sourceClass);
            ResolvedJavaMethod sourceMethod = findSourceMethod(sourceType, frameInfo);

            byte typeTag = TypeTag.getKind(sourceType);
            long classId = JDWPBridgeImpl.getIds().getIdOrCreateWeak(sourceType);
            long methodId = JDWPBridgeImpl.getIds().getIdOrCreateWeak(sourceMethod);
            int bci = frameInfo.getBci();

            // Workaround for JDI crash, causes by illegal BCI on stack traces.
            // SVM native C API may produce non-native methods that have no bytecodes, but report
            // BCI=1 on stack traces.
            // Ensure that methods without bytecodes always report unknown BCI in stack traces.
            if (!sourceMethod.hasBytecodes()) {
                bci = -1;
            }

            int frameDepth = i;
            stackFrames[i] = new StackFrame(typeTag, classId, methodId, bci, frameDepth);
        }

        return stackFrames;
    }

    @Override
    public long getCurrentThis() {
        Object thisObject = ResidentJDWP.getThis(Thread.currentThread(), 0);
        return JDWPBridgeImpl.getIds().getIdOrCreateWeak(thisObject);
    }

    @Override
    public boolean isCurrentThreadVirtual() {
        return Thread.currentThread().isVirtual();
    }

    /**
     * Returns the {@link InterpreterResolvedJavaMethod} associated with the given stack frame.
     *
     * <p>
     * Interpreter frames contains a direct reference to the associated
     * {@link InterpreterResolvedJavaMethod}. Compiled frames contains a
     * {@link FrameInfoQueryResult#getSourceMethodId()} that can be used to obtain the interpreter
     * method via {@link InterpreterUniverse#getMethodFromMethodId(int)}.
     */
    public static ResolvedJavaMethod findSourceMethod(ResolvedJavaType sourceType, FrameSourceInfo frameInfo) {
        if (frameInfo instanceof InterpreterFrameSourceInfo interpreterFrameSourceInfo) {
            // Interpreter frames contain the interpreted method.
            assert sourceType.equals(interpreterFrameSourceInfo.getInterpretedMethod().getDeclaringClass());
            return interpreterFrameSourceInfo.getInterpretedMethod();
        } else if (frameInfo instanceof FrameInfoQueryResult compiledFrameInfo) {
            // Compiled frames have a methodId.
            int sourceMethodId = compiledFrameInfo.getSourceMethodId();
            if (sourceMethodId != 0) {
                InterpreterUniverse universe = DebuggerSupport.singleton().getUniverse();
                ResolvedJavaMethod interpreterMethod = universe.getMethodFromMethodId(sourceMethodId);
                if (interpreterMethod != null) {
                    assert sourceType.equals(interpreterMethod.getDeclaringClass());
                    assert interpreterMethod.getName().equals(frameInfo.getSourceMethodName());
                    return interpreterMethod;
                }
            }
        }

        throw VMError.shouldNotReachHere("Cannot find method " + frameInfo.getSourceMethodName() + " in class " + frameInfo.getSourceClassName() + " at line " + frameInfo.getSourceLineNumber());
    }
}
