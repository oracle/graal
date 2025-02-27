/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
//Checkstyle: stop
// @formatter:off
package com.oracle.svm.jdwp.bridge;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.IsolateThreadContext;
import org.graalvm.nativeimage.c.type.CCharPointer;

import com.oracle.svm.jdwp.bridge.jniutils.JNI.JByteArray;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JClass;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JIntArray;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JLongArray;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JNIEnv;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JString;
import com.oracle.svm.jdwp.bridge.jniutils.JNIMethodScope;
import com.oracle.svm.jdwp.bridge.jniutils.JNIUtil;
import com.oracle.svm.jdwp.bridge.nativebridge.BinaryInput;
import com.oracle.svm.jdwp.bridge.nativebridge.BinaryMarshaller;
import com.oracle.svm.jdwp.bridge.nativebridge.BinaryOutput;
import com.oracle.svm.jdwp.bridge.nativebridge.BinaryOutput.CCharPointerBinaryOutput;
import com.oracle.svm.jdwp.bridge.nativebridge.ForeignException;
import com.oracle.svm.jdwp.bridge.nativebridge.JNIConfig;
import com.oracle.svm.jdwp.bridge.nativebridge.NativeIsolate;
import com.oracle.svm.jdwp.bridge.nativebridge.NativeIsolateThread;
import com.oracle.svm.jdwp.bridge.nativebridge.NativeObjectHandles;

import jdk.graal.compiler.word.Word;

/* Checkout README.md before modifying */
final class HSToNativeJDWPBridgeGen {

    static HSToNativeJDWPBridge createHSToNative(NativeIsolate isolate, long objectHandle) {
        return new HSToNativeStartPoint(isolate, objectHandle);
    }

    private static final class HSToNativeStartPoint extends HSToNativeJDWPBridge {

        private static final BinaryMarshaller<Packet> packetMarshaller;
        private static final BinaryMarshaller<StackFrame> stackFrameMarshaller;
        private static final BinaryMarshaller<Throwable> throwableMarshaller;
        
        static  {
            JNIConfig config = JDWPJNIConfig.getInstance();
            packetMarshaller = config.lookupMarshaller(Packet.class);
            stackFrameMarshaller = config.lookupMarshaller(StackFrame.class);
            throwableMarshaller = config.lookupMarshaller(Throwable.class);
        }

        HSToNativeStartPoint(NativeIsolate isolate, long objectHandle) {
            super(isolate, objectHandle);
        }
        
        @Override
        public void clearStepping(long threadId) {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                clearStepping0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), threadId);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public String currentWorkingDirectory() {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                return currentWorkingDirectory0(nativeIsolateThread.getIsolateThreadId(), this.getHandle());
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public Packet dispatch(Packet packet) throws JDWPException {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                int marshalledParametersSizeEstimate = packetMarshaller.inferSize(packet);
                BinaryOutput.ByteArrayBinaryOutput marshalledParametersOutput = BinaryOutput.ByteArrayBinaryOutput.create(marshalledParametersSizeEstimate);
                packetMarshaller.write(marshalledParametersOutput, packet);
                byte[] endPointResult = dispatch0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), marshalledParametersOutput.getArray());
                BinaryInput marshalledResultInput = BinaryInput.create(endPointResult);
                return packetMarshaller.read(marshalledResultInput);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public int fieldRefIdToIndex(long fieldRefId) {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                return fieldRefIdToIndex0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), fieldRefId);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public long fieldRefIndexToId(int fieldRefIndex) {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                return fieldRefIndexToId0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), fieldRefIndex);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public long getCurrentThis() {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                return getCurrentThis0(nativeIsolateThread.getIsolateThreadId(), this.getHandle());
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public String getSystemProperty(String key) {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                return getSystemProperty0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), key);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public StackFrame[] getThreadFrames(long threadId) {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                byte[] endPointResult = getThreadFrames0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), threadId);
                BinaryInput marshalledResultInput = BinaryInput.create(endPointResult);
                int hsResultLength = marshalledResultInput.readInt();
                StackFrame[] hsResult;
                if (hsResultLength != -1) {
                    hsResult = new StackFrame[hsResultLength];
                    for(int hsResultIndex = 0; hsResultIndex < hsResultLength; hsResultIndex++) {
                        StackFrame hsResultElement = stackFrameMarshaller.read(marshalledResultInput);
                        hsResult[hsResultIndex] = hsResultElement;
                    }
                } else {
                    hsResult = null;
                }
                return hsResult;
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public int getThreadStatus(long threadId) throws JDWPException {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                return getThreadStatus0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), threadId);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public boolean isCurrentThreadVirtual() {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                return isCurrentThreadVirtual0(nativeIsolateThread.getIsolateThreadId(), this.getHandle());
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public boolean isEventEnabled(long threadId, int eventKind) {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                return isEventEnabled0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), threadId, eventKind);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public int methodRefIdToIndex(long methodRefId) {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                return methodRefIdToIndex0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), methodRefId);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public long methodRefIndexToId(int methodRefIndex) {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                return methodRefIndexToId0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), methodRefIndex);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public void setEventEnabled(long threadId, int eventKind, boolean enable) {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                setEventEnabled0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), threadId, eventKind, enable);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public void setStepping(long threadId, int depth, int size) {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                setStepping0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), threadId, depth, size);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public void setSteppingFromLocation(long threadId, int depth, int size, long methodId, int bci, int lineNumber) {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                setSteppingFromLocation0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), threadId, depth, size, methodId, bci, lineNumber);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public void setThreadRequest(boolean start, boolean enable) {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                setThreadRequest0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), start, enable);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public long threadResume(long suspendId) {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                return threadResume0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), suspendId);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public long threadSuspend(long threadId) {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                return threadSuspend0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), threadId);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public void toggleBreakpoint(long methodId, int bci, boolean enable) {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                toggleBreakpoint0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), methodId, bci, enable);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public void toggleMethodEnterEvent(long clazzId, boolean enable) {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                toggleMethodEnterEvent0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), clazzId, enable);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public void toggleMethodExitEvent(long clazzId, boolean enable) {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                toggleMethodExitEvent0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), clazzId, enable);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public int typeRefIdToIndex(long typeRefId) {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                return typeRefIdToIndex0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), typeRefId);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public long typeRefIndexToId(int typeRefIndex) {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                return typeRefIndexToId0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), typeRefIndex);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public int[] typeStatus(long... typeIds) {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                return typeStatus0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), typeIds);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public void vmResume(long[] ignoredThreadIds) {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                vmResume0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), ignoredThreadIds);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        @Override
        public long[] vmSuspend(long[] ignoredThreadIds) {
            NativeIsolateThread nativeIsolateThread = this.getIsolate().enter();
            try {
                return vmSuspend0(nativeIsolateThread.getIsolateThreadId(), this.getHandle(), ignoredThreadIds);
            } catch (ForeignException foreignException) {
                throw foreignException.throwOriginalException(throwableMarshaller);
            } finally {
                nativeIsolateThread.leave();
            }
        }
        
        private static native void clearStepping0(long isolateThread, long objectId, long threadId);
        
        private static native String currentWorkingDirectory0(long isolateThread, long objectId);
        
        private static native byte[] dispatch0(long isolateThread, long objectId, byte[] marshalledData) throws JDWPException;
        
        private static native int fieldRefIdToIndex0(long isolateThread, long objectId, long fieldRefId);
        
        private static native long fieldRefIndexToId0(long isolateThread, long objectId, int fieldRefIndex);
        
        private static native long getCurrentThis0(long isolateThread, long objectId);
        
        private static native String getSystemProperty0(long isolateThread, long objectId, String key);
        
        private static native byte[] getThreadFrames0(long isolateThread, long objectId, long threadId);
        
        private static native int getThreadStatus0(long isolateThread, long objectId, long threadId) throws JDWPException;
        
        private static native boolean isCurrentThreadVirtual0(long isolateThread, long objectId);
        
        private static native boolean isEventEnabled0(long isolateThread, long objectId, long threadId, int eventKind);
        
        private static native int methodRefIdToIndex0(long isolateThread, long objectId, long methodRefId);
        
        private static native long methodRefIndexToId0(long isolateThread, long objectId, int methodRefIndex);
        
        private static native void setEventEnabled0(long isolateThread, long objectId, long threadId, int eventKind, boolean enable);
        
        private static native void setStepping0(long isolateThread, long objectId, long threadId, int depth, int size);
        
        private static native void setSteppingFromLocation0(long isolateThread, long objectId, long threadId, int depth, int size, long methodId, int bci, int lineNumber);
        
        private static native void setThreadRequest0(long isolateThread, long objectId, boolean start, boolean enable);
        
        private static native long threadResume0(long isolateThread, long objectId, long suspendId);
        
        private static native long threadSuspend0(long isolateThread, long objectId, long threadId);
        
        private static native void toggleBreakpoint0(long isolateThread, long objectId, long methodId, int bci, boolean enable);
        
        private static native void toggleMethodEnterEvent0(long isolateThread, long objectId, long clazzId, boolean enable);
        
        private static native void toggleMethodExitEvent0(long isolateThread, long objectId, long clazzId, boolean enable);
        
        private static native int typeRefIdToIndex0(long isolateThread, long objectId, long typeRefId);
        
        private static native long typeRefIndexToId0(long isolateThread, long objectId, int typeRefIndex);
        
        private static native int[] typeStatus0(long isolateThread, long objectId, long[] typeIds);
        
        private static native void vmResume0(long isolateThread, long objectId, long[] ignoredThreadIds);
        
        private static native long[] vmSuspend0(long isolateThread, long objectId, long[] ignoredThreadIds);
    }

    @SuppressWarnings("unused")
    private static final class HSToNativeEndPoint {

        private static final BinaryMarshaller<Packet> packetMarshaller;
        private static final BinaryMarshaller<StackFrame> stackFrameMarshaller;
        private static final BinaryMarshaller<Throwable> throwableMarshaller;
        static  {
            JNIConfig config = JDWPJNIConfig.getInstance();
            packetMarshaller = config.lookupMarshaller(Packet.class);
            stackFrameMarshaller = config.lookupMarshaller(StackFrame.class);
            throwableMarshaller = config.lookupMarshaller(Throwable.class);
        }
        

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_clearStepping0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static void clearStepping(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, long threadId) {
            try (JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::clearStepping", jniEnv)) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                receiverObject.clearStepping(threadId);
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
            }
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_currentWorkingDirectory0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static JString currentWorkingDirectory(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId) {
            JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::currentWorkingDirectory", jniEnv);
            try (JNIMethodScope sc = scope) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                String endPointResult = receiverObject.currentWorkingDirectory();
                scope.setObjectResult(JNIUtil.createHSString(jniEnv, endPointResult));
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
                scope.setObjectResult(Word.nullPointer());
            }
            return scope.getObjectResult();
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_dispatch0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static JByteArray dispatch(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, JByteArray marshalledData) {
            JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::dispatch", jniEnv);
            try (JNIMethodScope sc = scope) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                CCharPointer staticMarshallBuffer = StackValue.get(256);
                int marshalledDataLength = JNIUtil.GetArrayLength(jniEnv, marshalledData);
                CCharPointer marshallBuffer = marshalledDataLength <= 256 ? staticMarshallBuffer : UnmanagedMemory.malloc(marshalledDataLength);
                try {
                    JNIUtil.GetByteArrayRegion(jniEnv, marshalledData, 0, marshalledDataLength, marshallBuffer);
                    BinaryInput marshalledParametersInput = BinaryInput.create(marshallBuffer, marshalledDataLength);
                    Packet packet = packetMarshaller.read(marshalledParametersInput);
                    Packet endPointResult = receiverObject.dispatch(packet);
                    int marshalledResultSizeEstimate = packetMarshaller.inferSize(endPointResult);
                    int marshallBufferLength = Math.max(256, marshalledDataLength);
                    try (CCharPointerBinaryOutput marshalledResultOutput = marshalledResultSizeEstimate > marshallBufferLength ? CCharPointerBinaryOutput.create(marshalledResultSizeEstimate) : BinaryOutput.create(marshallBuffer, marshallBufferLength, false)) {
                        packetMarshaller.write(marshalledResultOutput, endPointResult);
                        int marshalledResultPosition = marshalledResultOutput.getPosition();
                        JByteArray marshalledResult = marshalledResultPosition <= marshalledDataLength ? marshalledData : JNIUtil.NewByteArray(jniEnv, marshalledResultPosition);
                        JNIUtil.SetByteArrayRegion(jniEnv, marshalledResult, 0, marshalledResultPosition, marshalledResultOutput.getAddress());
                        scope.setObjectResult(marshalledResult);
                    }
                } finally {
                    if (marshallBuffer != staticMarshallBuffer) {
                        UnmanagedMemory.free(marshallBuffer);
                    }
                }
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
                scope.setObjectResult(Word.nullPointer());
            }
            return scope.getObjectResult();
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_fieldRefIdToIndex0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static int fieldRefIdToIndex(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, long fieldRefId) {
            try (JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::fieldRefIdToIndex", jniEnv)) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                int endPointResult = receiverObject.fieldRefIdToIndex(fieldRefId);
                return endPointResult;
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
                return 0;
            }
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_fieldRefIndexToId0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static long fieldRefIndexToId(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, int fieldRefIndex) {
            try (JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::fieldRefIndexToId", jniEnv)) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                long endPointResult = receiverObject.fieldRefIndexToId(fieldRefIndex);
                return endPointResult;
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
                return 0;
            }
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_getCurrentThis0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static long getCurrentThis(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId) {
            try (JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::getCurrentThis", jniEnv)) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                long endPointResult = receiverObject.getCurrentThis();
                return endPointResult;
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
                return 0;
            }
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_getSystemProperty0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static JString getSystemProperty(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, JString key) {
            JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::getSystemProperty", jniEnv);
            try (JNIMethodScope sc = scope) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                String endPointResult = receiverObject.getSystemProperty(JNIUtil.createString(jniEnv, key));
                scope.setObjectResult(JNIUtil.createHSString(jniEnv, endPointResult));
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
                scope.setObjectResult(Word.nullPointer());
            }
            return scope.getObjectResult();
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_getThreadFrames0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static JByteArray getThreadFrames(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, long threadId) {
            JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::getThreadFrames", jniEnv);
            try (JNIMethodScope sc = scope) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                CCharPointer staticMarshallBuffer = StackValue.get(256);
                StackFrame[] endPointResult = receiverObject.getThreadFrames(threadId);
                int marshalledResultSizeEstimate = Integer.BYTES + (endPointResult != null && endPointResult.length > 0 ? endPointResult.length * stackFrameMarshaller.inferSize(endPointResult[0]) : 0);
                try (CCharPointerBinaryOutput marshalledResultOutput = marshalledResultSizeEstimate > 256 ? CCharPointerBinaryOutput.create(marshalledResultSizeEstimate) : BinaryOutput.create(staticMarshallBuffer, 256, false)) {
                    if (endPointResult != null) {
                        marshalledResultOutput.writeInt(endPointResult.length);
                        for (StackFrame endPointResultElement : endPointResult) {
                            stackFrameMarshaller.write(marshalledResultOutput, endPointResultElement);
                        }
                    } else {
                        marshalledResultOutput.writeInt(-1);
                    }
                    int marshalledResultPosition = marshalledResultOutput.getPosition();
                    JByteArray marshalledResult = JNIUtil.NewByteArray(jniEnv, marshalledResultPosition);
                    JNIUtil.SetByteArrayRegion(jniEnv, marshalledResult, 0, marshalledResultPosition, marshalledResultOutput.getAddress());
                    scope.setObjectResult(marshalledResult);
                }
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
                scope.setObjectResult(Word.nullPointer());
            }
            return scope.getObjectResult();
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_getThreadStatus0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static int getThreadStatus(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, long threadId) {
            try (JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::getThreadStatus", jniEnv)) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                int endPointResult = receiverObject.getThreadStatus(threadId);
                return endPointResult;
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
                return 0;
            }
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_isCurrentThreadVirtual0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static boolean isCurrentThreadVirtual(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId) {
            try (JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::isCurrentThreadVirtual", jniEnv)) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                boolean endPointResult = receiverObject.isCurrentThreadVirtual();
                return endPointResult;
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
                return false;
            }
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_isEventEnabled0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static boolean isEventEnabled(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, long threadId, int eventKind) {
            try (JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::isEventEnabled", jniEnv)) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                boolean endPointResult = receiverObject.isEventEnabled(threadId, eventKind);
                return endPointResult;
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
                return false;
            }
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_methodRefIdToIndex0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static int methodRefIdToIndex(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, long methodRefId) {
            try (JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::methodRefIdToIndex", jniEnv)) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                int endPointResult = receiverObject.methodRefIdToIndex(methodRefId);
                return endPointResult;
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
                return 0;
            }
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_methodRefIndexToId0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static long methodRefIndexToId(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, int methodRefIndex) {
            try (JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::methodRefIndexToId", jniEnv)) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                long endPointResult = receiverObject.methodRefIndexToId(methodRefIndex);
                return endPointResult;
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
                return 0;
            }
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_setEventEnabled0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static void setEventEnabled(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, long threadId, int eventKind, boolean enable) {
            try (JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::setEventEnabled", jniEnv)) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                receiverObject.setEventEnabled(threadId, eventKind, enable);
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
            }
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_setStepping0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static void setStepping(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, long threadId, int depth, int size) {
            try (JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::setStepping", jniEnv)) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                receiverObject.setStepping(threadId, depth, size);
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
            }
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_setSteppingFromLocation0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static void setSteppingFromLocation(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, long threadId, int depth, int size, long methodId, int bci, int lineNumber) {
            try (JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::setSteppingFromLocation", jniEnv)) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                receiverObject.setSteppingFromLocation(threadId, depth, size, methodId, bci, lineNumber);
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
            }
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_setThreadRequest0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static void setThreadRequest(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, boolean start, boolean enable) {
            try (JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::setThreadRequest", jniEnv)) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                receiverObject.setThreadRequest(start, enable);
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
            }
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_threadResume0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static long threadResume(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, long suspendId) {
            try (JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::threadResume", jniEnv)) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                long endPointResult = receiverObject.threadResume(suspendId);
                return endPointResult;
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
                return 0;
            }
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_threadSuspend0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static long threadSuspend(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, long threadId) {
            try (JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::threadSuspend", jniEnv)) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                long endPointResult = receiverObject.threadSuspend(threadId);
                return endPointResult;
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
                return 0;
            }
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_toggleBreakpoint0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static void toggleBreakpoint(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, long methodId, int bci, boolean enable) {
            try (JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::toggleBreakpoint", jniEnv)) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                receiverObject.toggleBreakpoint(methodId, bci, enable);
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
            }
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_toggleMethodEnterEvent0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static void toggleMethodEnterEvent(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, long clazzId, boolean enable) {
            try (JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::toggleMethodEnterEvent", jniEnv)) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                receiverObject.toggleMethodEnterEvent(clazzId, enable);
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
            }
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_toggleMethodExitEvent0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static void toggleMethodExitEvent(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, long clazzId, boolean enable) {
            try (JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::toggleMethodExitEvent", jniEnv)) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                receiverObject.toggleMethodExitEvent(clazzId, enable);
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
            }
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_typeRefIdToIndex0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static int typeRefIdToIndex(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, long typeRefId) {
            try (JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::typeRefIdToIndex", jniEnv)) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                int endPointResult = receiverObject.typeRefIdToIndex(typeRefId);
                return endPointResult;
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
                return 0;
            }
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_typeRefIndexToId0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static long typeRefIndexToId(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, int typeRefIndex) {
            try (JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::typeRefIndexToId", jniEnv)) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                long endPointResult = receiverObject.typeRefIndexToId(typeRefIndex);
                return endPointResult;
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
                return 0;
            }
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_typeStatus0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static JIntArray typeStatus(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, JLongArray typeIds) {
            JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::typeStatus", jniEnv);
            try (JNIMethodScope sc = scope) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                int[] endPointResult = receiverObject.typeStatus(JNIUtil.createArray(jniEnv, typeIds));
                scope.setObjectResult(JNIUtil.createHSArray(jniEnv, endPointResult));
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
                scope.setObjectResult(Word.nullPointer());
            }
            return scope.getObjectResult();
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_vmResume0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static void vmResume(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, JLongArray ignoredThreadIds) {
            try (JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::vmResume", jniEnv)) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                receiverObject.vmResume(JNIUtil.createArray(jniEnv, ignoredThreadIds));
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
            }
        }

        @CEntryPoint(name = "Java_com_oracle_svm_jdwp_bridge_HSToNativeJDWPBridgeGen_00024HSToNativeStartPoint_vmSuspend0", include = ResidentJDWPFeatureEnabled.class)
        @SuppressWarnings({"try", "unused"})
        static JLongArray vmSuspend(JNIEnv jniEnv, JClass jniClazz, @IsolateThreadContext long isolateThread, long objectId, JLongArray ignoredThreadIds) {
            JNIMethodScope scope = new JNIMethodScope("HSToNativeJDWPBridgeGen::vmSuspend", jniEnv);
            try (JNIMethodScope sc = scope) {
                JDWPBridge receiverObject = NativeObjectHandles.resolve(objectId, JDWPBridge.class);
                long[] endPointResult = receiverObject.vmSuspend(JNIUtil.createArray(jniEnv, ignoredThreadIds));
                scope.setObjectResult(JNIUtil.createHSArray(jniEnv, endPointResult));
            } catch (Throwable e) {
                ForeignException.forThrowable(e, throwableMarshaller).throwUsingJNI(jniEnv);
                scope.setObjectResult(Word.nullPointer());
            }
            return scope.getObjectResult();
        }
    }
}
