/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jdk.jfr;

import java.lang.management.ManagementFactory;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Supplier;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.jfr.logging.JfrLogger;
import com.oracle.svm.core.jdk.jfr.recorder.JfrRecorder;
import com.oracle.svm.core.jdk.jfr.recorder.checkpoint.JfrMetadataEvent;
import com.oracle.svm.core.jdk.jfr.recorder.checkpoint.types.traceid.JfrTraceId;
import com.oracle.svm.core.jdk.jfr.recorder.checkpoint.types.traceid.JfrTraceIdEpoch;
import com.oracle.svm.core.jdk.jfr.recorder.repository.JfrChunkRotation;
import com.oracle.svm.core.jdk.jfr.recorder.repository.JfrRepository;
import com.oracle.svm.core.jdk.jfr.recorder.storage.JfrBuffer;
import com.oracle.svm.core.jdk.jfr.recorder.storage.JfrStorage;
import com.oracle.svm.core.jdk.jfr.recorder.stringpool.JfrStringPool;
import com.oracle.svm.core.jdk.jfr.support.JfrThreadLocal;
import com.oracle.svm.core.thread.JavaThreads;

import jdk.internal.event.Event;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;

@TargetClass(className = "jdk.jfr.internal.Type", onlyWith = JfrAvailability.WithJfr.class)
final class Target_jdk_jfr_internal_Type {
    @Alias
    public long getId() {
        return 0;
    }
}

@TargetClass(className = "jdk.jfr.internal.PlatformEventType", onlyWith = JfrAvailability.WithJfr.class)
final class Target_jdk_jfr_internal_PlatformEventType {
    @Alias
    public boolean getStackTraceEnabled() {
        return false;
    }

    @Alias
    public int getStackTraceOffset() {
        return 0;
    }
}

@TargetClass(className = "jdk.jfr.internal.StringPool", onlyWith = JfrAvailability.WithJfr.class)
final class Target_jdk_jfr_internal_StringPool {
    @Substitute
    private static boolean getCurrentEpoch() {
        return JfrTraceIdEpoch.getEpoch();
    }

    @Alias
    public static long addString(String s) {
        return 0;
    }

}

@Substitute
@TargetClass(className = "jdk.jfr.internal.Logger", onlyWith = JfrAvailability.WithJfr.class)
final class Target_jdk_jfr_internal_Logger {

    @Substitute
    private static final int MAX_SIZE = 10000;

    @Substitute
    public static boolean shouldLog(LogTag tag, LogLevel level) {
        return true;
    }

    @KeepOriginal
    public static void log(LogTag logTag, LogLevel logLevel, String message) {
    }

    @KeepOriginal
    public static void log(LogTag logTag, LogLevel logLevel, Supplier<String> messageSupplier) {
    }

    @KeepOriginal
    private static void logInternal(LogTag logTag, LogLevel logLevel, String message) {
    }
}

@TargetClass(className = "jdk.jfr.internal.MetadataRepository", onlyWith = JfrAvailability.WithJfr.class)
final class Target_jdk_jfr_internal_MetadataRepository {
    @Substitute
    private void unregisterUnloaded() {
        // JFR.TODO: once traceid related work is fixed, this
        // substitution should be removed in favor of the original
        // implementation
    }
}

@Substitute
@TargetClass(className = "jdk.jfr.internal.EventWriter", onlyWith = JfrAvailability.WithJfr.class)
final class Target_jdk_jfr_internal_EventWriter {
    private static final Target_jdk_jfr_internal_JVM jvm = Target_jdk_jfr_internal_JVM.getJVM();

    private final long threadID;
    private int maxEventSize;

    private boolean started;
    private boolean valid;
    private boolean flushOnEnd;

    private Target_jdk_jfr_internal_PlatformEventType eventType;

    private int startPosition;
    private JfrBuffer buffer;

    private final JfrThreadLocal jtl;

    @Substitute
    public static Target_jdk_jfr_internal_EventWriter getEventWriter() {
        JfrThreadLocal jtl = JavaThreads.getThreadLocal(Thread.currentThread());
        assert (jtl != null);
        Target_jdk_jfr_internal_EventWriter writer = (Target_jdk_jfr_internal_EventWriter) jtl.getEventWriter();
        if (writer != null) {
            return writer;
        }

        assert (!jtl.hasBuffer());
        JfrBuffer b = jtl.buffer();
        if (b == null) {
            throw new OutOfMemoryError("OOME for thread local buffer");
        }

        jtl.setEventWriter(new Target_jdk_jfr_internal_EventWriter(b, true, jtl));
        assert (jtl.getEventWriter() != null);

        return ((Target_jdk_jfr_internal_EventWriter) jtl.getEventWriter());
    }

    Target_jdk_jfr_internal_EventWriter(JfrBuffer buffer, boolean valid, JfrThreadLocal parent) {
        this.threadID = buffer.identity();
        this.valid = valid;
        this.started = false;

        this.buffer = buffer;
        this.startPosition = this.buffer.getCommittedPosition();

        // event may not exceed size for a padded integer
        this.maxEventSize = (1 << 28) - 1;

        this.jtl = parent;
    }

    @Substitute
    public void putBoolean(boolean i) {
        if (isValidForSize(Byte.BYTES)) {
            buffer.getBuffer().put(i ? (byte) 1 : (byte) 0);
        }
    }

    @Substitute
    public void putByte(byte i) {
        if (isValidForSize(Byte.BYTES)) {
            buffer.getBuffer().put(i);
        }
    }

    @Substitute
    public void putChar(char v) {
        if (isValidForSize(Character.BYTES + 1)) {
            putUncheckedLong(v);
        }
    }

    @Substitute
    private void putUncheckedChar(char v) {
        putUncheckedLong(v);
    }

    @Substitute
    public void putShort(short v) {
        if (isValidForSize(Short.BYTES + 1)) {
            putUncheckedLong(v & 0xFFFF);
        }
    }

    @Substitute
    public void putInt(int v) {
        if (isValidForSize(Integer.BYTES + 1)) {
            putUncheckedLong(v & 0x00000000ffffffffL);
        }
    }

    @Substitute
    private void putUncheckedInt(int v) {
        putUncheckedLong(v & 0x00000000ffffffffL);
    }

    @Substitute
    public void putFloat(float i) {
        if (isValidForSize(Float.BYTES)) {
            buffer.getBuffer().putFloat(i);
        }
    }

    @Substitute
    public void putLong(long v) {
        if (isValidForSize(Long.BYTES + 1)) {
            putUncheckedLong(v);
        }
    }

    @Substitute
    public void putDouble(double i) {
        if (isValidForSize(Double.BYTES)) {
            buffer.getBuffer().putDouble(i);
        }
    }

    @Substitute
    public void putString(String s, Target_jdk_jfr_internal_StringPool pool) {
        if (s == null) {
            byte b = 0;
            putByte(b);
            return;
        }
        int length = s.length();
        if (length == 0) {
            byte b = 1;
            putByte(b);
            return;
        }
        if (length > 16 && length < 128) {
            long l = Target_jdk_jfr_internal_StringPool.addString(s);
            if (l > -1) {
                byte b = 2;
                putByte(b);
                putLong(l);
                return;
            }
        }
        putStringValue(s);
    }

    @KeepOriginal
    private void putStringValue(String s) {
    }

    @Substitute
    public void putEventThread() {
        putLong(threadID);
    }

    @Substitute
    public void putThread(Thread aThread) {
        if (aThread == null) {
            putLong(0L);
        } else {
            putLong(jvm.getThreadId(aThread));
        }
    }

    @Substitute
    public void putClass(Class<?> aClass) {
        if (aClass == null) {
            putLong(0L);
        } else {
            putLong(Target_jdk_jfr_internal_JVM.getClassIdNonIntrinsic(aClass));
        }
    }

    @Substitute
    public void putStackTrace() {
        if (this.eventType.getStackTraceEnabled()) {
            putLong(jvm.getStackTraceId(this.eventType.getStackTraceOffset()));
        } else {
            putLong(0L);
        }
    }

    @Substitute
    private void reserveEventSizeField() {
        try {
            // move currentPosition Integer.Bytes offset from start position
            if (isValidForSize(Integer.BYTES)) {
                buffer.getBuffer().position(buffer.getBuffer().position() + Integer.BYTES);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Substitute
    private void reset() {
        buffer.getBuffer().position(buffer.getCommittedPosition());
        if (flushOnEnd) {
            flushOnEnd = flush();
        }
        valid = true;
        started = false;
    }

    @Substitute
    private boolean isValidForSize(int requestedSize) {
        if (!valid) {
            return false;
        }
        if (buffer.getBuffer().position() + requestedSize > buffer.getBuffer().limit()) {
            flushOnEnd = flush(usedSize(), requestedSize);
            if (buffer.getBuffer().position() + requestedSize > buffer.getBuffer().limit()) {
                valid = false;
                return false;
            }
        }
        return true;
    }

    @Substitute
    private boolean isNotified() {
        return this.jtl.isNotified();
    }

    @Substitute
    private void resetNotified() {
        this.jtl.setNotified(false);
    }

    @Substitute
    private int usedSize() {
        return buffer.getBuffer().position() - buffer.getCommittedPosition();
    }

    @KeepOriginal
    private boolean flush() {
        return false;
    }

    // usedSize: buffer position - committed position
    // requestedSize: size to write into buffer
    @Substitute
    private boolean flush(int usedSize, int requestedSize) {
        this.buffer = JfrStorage.instance().flush(this.buffer, usedSize, requestedSize, Thread.currentThread());

        boolean isValid = this.buffer.getFreeSize() >= usedSize + requestedSize;
        int newPos = isValid ? this.buffer.getCommittedPosition() + usedSize : this.buffer.getCommittedPosition();

        // ByteBuffer specific
        if (this.buffer.getBuffer().position() != newPos) {
            JfrLogger.logWarning("Unexpected flush occurred. Data may be lost");
            this.buffer.getBuffer().position(newPos);
        }
        this.startPosition = this.buffer.getCommittedPosition();

        if (!isValid) {
            this.valid = false;
            return false;
        }

        return this.buffer.isLeased();
    }

    @Substitute
    public boolean beginEvent(Target_jdk_jfr_internal_PlatformEventType eventType) {
        try {
            if (this.started) {
                return false;
            }
            this.started = true;

            this.eventType = eventType;
            reserveEventSizeField();
            Target_jdk_jfr_internal_Type type = Target_jdk_jfr_internal_Type.class.cast(eventType);
            putLong(type.getId());
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
        return true;
    }

    @Substitute
    public boolean endEvent() {
        if (!valid) {
            this.reset();
            return true;
        }
        final int eventSize = usedSize();
        if (eventSize > maxEventSize) {
            this.reset();
            return true;
        }
        buffer.getBuffer().putInt(startPosition, makePaddedInt(eventSize));
        if (isNotified()) {
            resetNotified();
            this.reset();
            // returning false will trigger restart of the event write attempt
            return false;
        }
        startPosition = buffer.getBuffer().position();
        buffer.setCommittedPosition(startPosition);
        // the event is now committed
        if (flushOnEnd) {
            flushOnEnd = flush();
        }
        started = false;

        return true;
    }

    @Substitute
    private static int makePaddedInt(int v) {
        // bit 0-6 + pad => bit 24 - 31
        long b1 = (((v >>> 0) & 0x7F) | 0x80) << 24;

        // bit 7-13 + pad => bit 16 - 23
        long b2 = (((v >>> 7) & 0x7F) | 0x80) << 16;

        // bit 14-20 + pad => bit 8 - 15
        long b3 = (((v >>> 14) & 0x7F) | 0x80) << 8;

        // bit 21-28 => bit 0 - 7
        long b4 = (((v >>> 21) & 0x7F)) << 0;

        return (int) (b1 + b2 + b3 + b4);
    }

    @Substitute
    private void putUncheckedLong(long v) {
        if ((v & ~0x7FL) == 0L) {
            putUncheckedByte((byte) v); // 0-6
            return;
        }
        putUncheckedByte((byte) (v | 0x80L)); // 0-6
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            putUncheckedByte((byte) v); // 7-13
            return;
        }
        putUncheckedByte((byte) (v | 0x80L)); // 7-13
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            putUncheckedByte((byte) v); // 14-20
            return;
        }
        putUncheckedByte((byte) (v | 0x80L)); // 14-20
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            putUncheckedByte((byte) v); // 21-27
            return;
        }
        putUncheckedByte((byte) (v | 0x80L)); // 21-27
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            putUncheckedByte((byte) v); // 28-34
            return;
        }
        putUncheckedByte((byte) (v | 0x80L)); // 28-34
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            putUncheckedByte((byte) v); // 35-41
            return;
        }
        putUncheckedByte((byte) (v | 0x80L)); // 35-41
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            putUncheckedByte((byte) v); // 42-48
            return;
        }
        putUncheckedByte((byte) (v | 0x80L)); // 42-48
        v >>>= 7;

        if ((v & ~0x7FL) == 0L) {
            putUncheckedByte((byte) v); // 49-55
            return;
        }
        putUncheckedByte((byte) (v | 0x80L)); // 49-55
        putUncheckedByte((byte) (v >>> 7)); // 56-63, last byte as is.
    }

    @Substitute
    private void putUncheckedByte(byte i) {
        buffer.getBuffer().put(i);
    }
}

@Substitute
@TargetClass(className = "jdk.jfr.internal.JVM", onlyWith = JfrAvailability.WithJfr.class)
final class Target_jdk_jfr_internal_JVM {

    private static final Target_jdk_jfr_internal_JVM jvm = new Target_jdk_jfr_internal_JVM();

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)
    private volatile boolean nativeOK;

    static final Object FILE_DELTA_CHANGE = new Object();

    @TargetElement(name = TargetElement.CONSTRUCTOR_NAME)
    private Target_jdk_jfr_internal_JVM() {
    }

    @Substitute
    @TargetElement(name = "getJVM")
    public static Target_jdk_jfr_internal_JVM getJVM() {
        return jvm;
    }

    @Substitute
    @TargetElement(name = "beginRecording")
    public void beginRecording() {
        if (!JfrRecorder.isRecording()) {
            JfrRecorder.startRecording();
        }
    }

    @Substitute
    @TargetElement(name = "counterTime")
    public static long counterTime() {
        return ManagementFactory.getRuntimeMXBean().getStartTime();
    }

    @Substitute
    @TargetElement(name = "emitEvent")
    public boolean emitEvent(long eventTypeId, long timestamp, long when) {
        return false;
    }

    @Substitute
    @TargetElement(name = "endRecording")
    public void endRecording() {
        if (!JfrRecorder.isRecording()) {
            return;
        }
        JfrRecorder.stopRecording();
    }

    @Substitute
    @TargetElement(name = "getAllEventClasses")
    public List<Class<? extends Event>> getAllEventClasses() {
        JfrRuntimeAccess jfrRuntime = ImageSingletons.lookup(JfrRuntimeAccess.class);
        return jfrRuntime.getEventClasses();
    }

    @Substitute
    @TargetElement(name = "getUnloadedEventClassCount")
    public long getUnloadedEventClassCount() {
        return 0;
    }

    @Substitute
    @TargetElement(name = "getClassId")
    public static long getClassId(Class<?> clazz) {
        return getClassIdNonIntrinsic(clazz);
    }

    @Substitute
    @TargetElement(name = "getClassIdNonIntrinsic")
    public static long getClassIdNonIntrinsic(Class<?> clazz) {
        return JfrTraceId.load(clazz);
    }

    @Substitute
    @TargetElement(name = "getPid")
    public String getPid() {
        return String.valueOf(ProcessHandle.current().pid());
    }

    @Substitute
    @TargetElement(name = "getStackTraceId")
    public long getStackTraceId(int skipCount) {
        return 0;
    }

    @Substitute
    @TargetElement(name = "getThreadId")
    public long getThreadId(Thread t) {
        return t.getId();
    }

    @Substitute
    @TargetElement(name = "getTicksFrequency")
    public long getTicksFrequency() {
        return 0;
    }

    @Substitute
    @TargetElement(name = "log")
    public static void log(int tagSetId, int level, String message) {
        JfrLogger.log(tagSetId, level, message);
    }

    @Substitute
    @TargetElement(name = "subscribeLogLevel")
    public static void subscribeLogLevel(LogTag lt, int tagSetId) {
        // Substitutions for jdk.jfr.internal.JVM mean this code path should not be
        // reached
        throw new RuntimeException("Should not reach here");
    }

    @Substitute
    @TargetElement(name = "retransformClasses")
    public synchronized void retransformClasses(Class<?>[] classes) {
    }

    @Substitute
    @TargetElement(name = "setEnabled")
    public void setEnabled(long eventTypeId, boolean enabled) {
    }

    @Substitute
    @TargetElement(name = "setFileNotification")
    public void setFileNotification(long delta) {
        JfrChunkRotation.setThreshold(delta, FILE_DELTA_CHANGE);
    }

    @Substitute
    @TargetElement(name = "setGlobalBufferCount")
    public void setGlobalBufferCount(long count) throws IllegalArgumentException, IllegalStateException {
        // Changes to JfrOptions mean this code path should not be reached
        throw new RuntimeException("Should not reach here");
    }

    @Substitute
    @TargetElement(name = "setGlobalBufferSize")
    public void setGlobalBufferSize(long size) throws IllegalArgumentException {
        // Changes to JfrOptions mean this code path should not be reached
        throw new RuntimeException("Should not reach here");
    }

    @Substitute
    @TargetElement(name = "setMemorySize")
    public void setMemorySize(long size) throws IllegalArgumentException {
        // Changes to JfrOptions mean this code path should not be reached
        throw new RuntimeException("Should not reach here");
    }

    @Substitute
    @TargetElement(name = "setMethodSamplingInterval")
    public void setMethodSamplingInterval(long type, long intervalMillis) throws IllegalArgumentException {
    }

    @Substitute
    @TargetElement(name = "setOutput")
    public void setOutput(String file) {
        if (file != null) {
            JfrRepository.setInstanceChunkPath(Paths.get(file));
        } else {
            JfrRepository.setInstanceChunkPath(null);
        }
    }

    @Substitute
    @TargetElement(name = "setForceInstrumentation")
    public void setForceInstrumentation(boolean force) {
        // This code path should not be reached
        throw new RuntimeException("Should not reach here");
    }

    @Substitute
    @TargetElement(name = "setSampleThreads")
    public void setSampleThreads(boolean sampleThreads) throws IllegalStateException {
        // Changes to JfrOptions mean this code path should not be reached
        throw new RuntimeException("Should not reach here");
    }

    @Substitute
    @TargetElement(name = "setCompressedIntegers")
    public void setCompressedIntegers(boolean compressed) throws IllegalStateException {
        // Seems unused in jdk/jdk
        throw new RuntimeException("Should not reach here");
    }

    @Substitute
    @TargetElement(name = "setStackDepth")
    public void setStackDepth(int depth) throws IllegalArgumentException, IllegalStateException {
        // Changes to JfrOptions mean this code path should not be reached
        throw new RuntimeException("Should not reach here");
    }

    @Substitute
    @TargetElement(name = "setStackTraceEnabled")
    public void setStackTraceEnabled(long eventTypeId, boolean enabled) {
        // JFR.TODO
    }

    @Substitute
    @TargetElement(name = "setThreadBufferSize")
    public void setThreadBufferSize(long size) throws IllegalArgumentException, IllegalStateException {
        // Changes to JfrOptions mean this code path should not be reached
        throw new RuntimeException("Should not reach here");
    }

    @Substitute
    @TargetElement(name = "setThreshold")
    public boolean setThreshold(long eventTypeId, long ticks) {
        return false;
    }

    @Substitute
    @TargetElement(name = "storeMetadataDescriptor")
    public void storeMetadataDescriptor(byte[] bytes) {
        JfrMetadataEvent.update(bytes);
    }

    @Substitute
    @TargetElement(name = "endRecording_")
    public void endRecording_() {
        endRecording();
    }

    @Substitute
    @TargetElement(name = "beginRecording_")
    public void beginRecording_() {
        beginRecording();
    }

    @Substitute
    @TargetElement(name = "isRecording")
    public boolean isRecording() {
        // JFR.TODO
        // This currently returns true unconditionally
        // It should instead return value of this.recording, but substitutions in
        // jfr java code need to be added to recompute field values so that
        // the singleton jvm instance is always the same instance
        return JfrRecorder.isRecording();
    }

    @Substitute
    @TargetElement(name = "getAllowedToDoEventRetransforms")
    public boolean getAllowedToDoEventRetransforms() {
        return false;
    }

    @Substitute
    @TargetElement(name = "createJFR")
    private boolean createJFR(boolean simulateFailure) throws IllegalStateException {
        if (JfrRecorder.isCreated()) {
            return true;
        }
        if (!JfrRecorder.create(simulateFailure)) {
            // if (!thread->has_pending_exception()) {
            // JfrJavaSupport::throw_illegal_state_exception("Unable to start Jfr", thread);
            // }

            return false;
        }
        return true;
    }

    @Substitute
    @TargetElement(name = "destroyJFR")
    private boolean destroyJFR() {
        JfrRecorder.destroy();
        return true;
    }

    @Substitute
    @TargetElement(name = "createFailedNativeJFR")
    public boolean createFailedNativeJFR() throws IllegalStateException {
        return createJFR(true);
    }

    @Substitute
    @TargetElement(name = "createNativeJFR")
    public void createNativeJFR() {
        nativeOK = createJFR(false);
    }

    @Substitute
    @TargetElement(name = "destroyNativeJFR")
    public boolean destroyNativeJFR() {
        boolean result = destroyJFR();
        nativeOK = !result;
        return result;
    }

    @Substitute
    @TargetElement(name = "hasNativeJFR")
    public boolean hasNativeJFR() {
        return nativeOK;
    }

    @Substitute
    @TargetElement(name = "isAvailable")
    public boolean isAvailable() {
        return !Jfr.isDisabled();
    }

    @Substitute
    @TargetElement(name = "getTimeConversionFactor")
    public double getTimeConversionFactor() {
        return 0;
    }

    @Substitute
    @TargetElement(name = "getTypeId")
    public long getTypeId(Class<?> clazz) {
        return JfrTraceId.getTraceId(clazz);
    }

    @Substitute
    @TargetElement(name = "getEventWriter")
    public static Object getEventWriter() {
        // Substitutions for jdk.jfr.internal.EventWriter mean this code path should not
        // be reached
        throw new RuntimeException("Should not reach here");
    }

    @Substitute
    @TargetElement(name = "newEventWriter")
    public static Target_jdk_jfr_internal_EventWriter newEventWriter() {
        // Substitutions for jdk.jfr.internal.EventWriter mean this code path should not
        // be reached
        throw new RuntimeException("Should not reach here");
    }

    @Substitute
    @TargetElement(name = "flush")
    public static boolean flush(Target_jdk_jfr_internal_EventWriter writer, int uncommittedSize, int requestedSize) {
        // Substitutions for jdk.jfr.internal.EventWriter mean this code path should not
        // be reached
        throw new RuntimeException("Should not reach here");
    }

    @Substitute
    @TargetElement(name = "setRepositoryLocation")
    public void setRepositoryLocation(String dirText) {
        if (dirText != null) {
            JfrRepository.setInstancePath(Paths.get(dirText));
        }
    }

    @Substitute
    @TargetElement(name = "abort")
    public void abort(String errorMsg) {
    }

    @Substitute
    @TargetElement(name = "addStringConstant")
    public static boolean addStringConstant(boolean epoch, long id, String s) {
        boolean r = JfrStringPool.instance().addStringConstant(epoch, id, s);
        return r;
    }

    @Substitute
    @TargetElement(name = "getEpochAddress")
    public long getEpochAddress() {
        // Substitutions for jdk.jfr.internal.StringPool mean this code path should not
        // be reached
        throw new RuntimeException("Should not reach here");
    }

    @Substitute
    @TargetElement(name = "uncaughtException")
    public void uncaughtException(Thread thread, Throwable t) {
    }

    @Substitute
    @TargetElement(name = "setCutoff")
    public boolean setCutoff(long eventTypeId, long cutoffTicks) {
        return false;
    }

    @Substitute
    @TargetElement(name = "emitOldObjectSamples")
    public void emitOldObjectSamples(long cutoff, boolean emitAll) {
    }

    @Substitute
    @TargetElement(name = "shouldRotateDisk")
    public boolean shouldRotateDisk() {
        boolean b = JfrChunkRotation.shouldRotate();
        return b;
    }
}

public final class JfrSubstitutions {
}
