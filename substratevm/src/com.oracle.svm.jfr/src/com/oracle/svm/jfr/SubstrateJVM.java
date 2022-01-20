/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jfr;

import java.lang.reflect.Field;
import java.util.List;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.ThreadListener;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.jfr.events.ExecutionSampleEvent;
import com.oracle.svm.jfr.logging.JfrLogging;

import jdk.jfr.Configuration;
import jdk.jfr.internal.EventWriter;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.LogTag;

/**
 * Manager class that handles most JFR Java API, see {@link Target_jdk_jfr_internal_JVM}.
 */
public class SubstrateJVM {
    private final List<Configuration> knownConfigurations;
    private final JfrOptionSet options;
    private final JfrNativeEventSetting[] eventSettings;
    private final JfrSymbolRepository symbolRepo;
    private final JfrTypeRepository typeRepo;
    private final JfrThreadRepository threadRepo;
    private final JfrStackTraceRepository stackTraceRepo;
    private final JfrMethodRepository methodRepo;
    private final JfrConstantPool[] repositories;

    private final JfrThreadLocal threadLocal;
    private final JfrGlobalMemory globalMemory;
    private final JfrUnlockedChunkWriter unlockedChunkWriter;
    private final JfrRecorderThread recorderThread;

    private final JfrLogging jfrLogging;

    private boolean initialized;
    // We need this separate field for all JDK versions, i.e., even for versions where the field
    // JVM.recording is present (JVM.recording is not set for all the cases that we are interested
    // in).
    private volatile boolean recording;
    private byte[] metadataDescriptor;

    @Platforms(Platform.HOSTED_ONLY.class)
    SubstrateJVM(List<Configuration> configurations) {
        this.knownConfigurations = configurations;

        options = new JfrOptionSet();

        int eventCount = JfrEvents.getEventCount();
        eventSettings = new JfrNativeEventSetting[eventCount];
        for (int i = 0; i < eventSettings.length; i++) {
            eventSettings[i] = new JfrNativeEventSetting();
        }

        symbolRepo = new JfrSymbolRepository();
        typeRepo = new JfrTypeRepository();
        threadRepo = new JfrThreadRepository();
        stackTraceRepo = new JfrStackTraceRepository();
        methodRepo = new JfrMethodRepository();
        /*
         * The ordering in the array dictates the writing order of constant pools in the recording.
         * Current rules: 1. methodRepo should be after stackTraceRepo; 2. typeRepo should be after
         * methodRepo and stackTraceRepo; 3. symbolRepo should be on end.
         */
        repositories = new JfrConstantPool[]{stackTraceRepo, methodRepo, typeRepo, threadRepo, symbolRepo};

        threadLocal = new JfrThreadLocal();
        globalMemory = new JfrGlobalMemory();
        unlockedChunkWriter = new JfrChunkWriter(globalMemory);
        recorderThread = new JfrRecorderThread(globalMemory, unlockedChunkWriter);

        jfrLogging = new JfrLogging();

        initialized = false;
        recording = false;
        metadataDescriptor = null;
    }

    @Fold
    public static SubstrateJVM get() {
        return ImageSingletons.lookup(SubstrateJVM.class);
    }

    @Fold
    public static List<Configuration> getKnownConfigurations() {
        return get().knownConfigurations;
    }

    @Fold
    public static JfrGlobalMemory getGlobalMemory() {
        return get().globalMemory;
    }

    @Fold
    public static JfrRecorderThread getRecorderThread() {
        return get().recorderThread;
    }

    @Fold
    public static ThreadListener getThreadLocal() {
        return get().threadLocal;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long getParentThreadId(IsolateThread isolateThread) {
        return get().threadLocal.getParentThreadId(isolateThread);
    }

    @Fold
    public static JfrTypeRepository getTypeRepository() {
        return get().typeRepo;
    }

    @Fold
    public static JfrSymbolRepository getSymbolRepository() {
        return get().symbolRepo;
    }

    @Fold
    public static JfrThreadRepository getThreadRepo() {
        return get().threadRepo;
    }

    @Fold
    public static JfrMethodRepository getMethodRepo() {
        return get().methodRepo;
    }

    @Fold
    public static JfrLogging getJfrLogging() {
        return get().jfrLogging;
    }

    public static Object getHandler(Class<? extends jdk.internal.event.Event> eventClass) {
        try {
            Field f = eventClass.getDeclaredField("eventHandler");
            f.setAccessible(true);
            return f.get(null);
        } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
            throw new InternalError("Could not access event handler");
        }
    }

    public static boolean isInitialized() {
        return get().initialized;
    }

    @Uninterruptible(reason = "Prevent races with threads that start/stop recording.", callerMustBe = true)
    public static boolean isRecording() {
        return get().recording;
    }

    /**
     * See {@link JVM#createJFR}. Until {@link #beginRecording} is executed, no JFR events can be
     * triggered yet. So, we don't need to take any precautions here.
     */
    public boolean createJFR(boolean simulateFailure) {
        if (simulateFailure) {
            throw new IllegalStateException("Unable to start JFR");
        } else if (initialized) {
            throw new IllegalStateException("JFR was already started before");
        }

        options.validateAndAdjustMemoryOptions();

        JfrTicks.initialize();
        threadLocal.initialize(options.threadBufferSize.getValue());
        globalMemory.initialize(options.globalBufferSize.getValue(), options.globalBufferCount.getValue());
        unlockedChunkWriter.initialize(options.maxChunkSize.getValue());

        recorderThread.start();
        initialized = true;
        return true;
    }

    /**
     * See {@link JVM#destroyJFR}. This method is only called after the recording was already
     * stopped. So, no JFR events can be triggered by this or any other thread and we don't need to
     * take any precautions here.
     */
    public boolean destroyJFR() {
        assert !recording : "must already have been stopped";
        if (!initialized) {
            return false;
        }

        recorderThread.setStopped(true);
        recorderThread.signal();
        try {
            recorderThread.join();
        } catch (InterruptedException e) {
            throw VMError.shouldNotReachHere(e);
        }

        globalMemory.teardown();
        symbolRepo.teardown();
        threadRepo.teardown();
        stackTraceRepo.teardown();
        methodRepo.teardown();

        initialized = false;
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getStackTraceId(long eventTypeId, int skipCount) {
        if (isStackTraceEnabled(eventTypeId)) {
            return getStackTraceId(skipCount);
        } else {
            return 0L;
        }
    }

    /** See {@link JVM#getStackTraceId}. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getStackTraceId(int skipCount) {
        return stackTraceRepo.getStackTraceId(skipCount, false);
    }

    /** See {@link JVM#getThreadId}. */
    public long getThreadId(Thread thread) {
        return thread.getId();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getThreadId(IsolateThread isolateThread) {
        return threadLocal.getTraceId(isolateThread);
    }

    /** See {@link JVM#storeMetadataDescriptor}. */
    public void storeMetadataDescriptor(byte[] bytes) {
        metadataDescriptor = bytes;
    }

    /** See {@link JVM#beginRecording}. */
    public void beginRecording() {
        assert !recording;

        JfrChunkWriter chunkWriter = unlockedChunkWriter.lock();
        try {
            // It is possible that setOutput was called with a filename earlier. In that case, we
            // need to start recording to the specified file right away.
            chunkWriter.maybeOpenFile();
        } finally {
            chunkWriter.unlock();
        }

        JavaVMOperation.enqueueBlockingSafepoint("Jfr begin recording", () -> {
            recording = true;
            SubstrateJVM.getThreadRepo().registerRunningThreads();
            // After changing the value of recording to true, JFR events can be triggered at any
            // time.
        });
    }

    /** See {@link JVM#endRecording}. */
    public void endRecording() {
        assert recording;
        JavaVMOperation.enqueueBlockingSafepoint("JFR end recording", () -> recording = false);
        // After the safepoint, it is guaranteed that all JfrNativeEventWriters finished their job
        // and that no further JFR events will be triggered.
    }

    /** See {@link JVM#isRecording}. This is not thread safe */
    public boolean unsafeIsRecording() {
        return recording;
    }

    /** See {@link JVM#getClassId}. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getClassId(Class<?> clazz) {
        return typeRepo.getClassId(clazz);
    }

    /**
     * See {@link JVM#setOutput}. The JFR infrastructure also calls this method when it is time to
     * rotate the file.
     */
    public void setOutput(String file) {
        JfrChunkWriter chunkWriter = unlockedChunkWriter.lock();
        try {
            if (recording) {
                boolean existingFile = chunkWriter.hasOpenFile();
                if (existingFile) {
                    chunkWriter.closeFile(metadataDescriptor, repositories);
                }

                if (file != null) {
                    chunkWriter.openFile(file);
                    // If in-memory recording was active so far, we should notify the recorder
                    // thread because the global memory buffers could be rather full.
                    if (!existingFile) {
                        recorderThread.signal();
                    }
                }
            } else {
                chunkWriter.setFilename(file);
            }
        } finally {
            chunkWriter.unlock();
        }
    }

    /** See {@link JVM#setFileNotification}. */
    public void setFileNotification(long delta) {
        options.maxChunkSize.setUserValue(delta);
    }

    /** See {@link JVM#setGlobalBufferCount}. */
    public void setGlobalBufferCount(long count) {
        options.globalBufferCount.setUserValue(count);
    }

    /** See {@link JVM#setGlobalBufferSize}. */
    public void setGlobalBufferSize(long size) {
        options.globalBufferSize.setUserValue(size);
    }

    /** See {@link JVM#setMemorySize}. */
    public void setMemorySize(long size) {
        options.memorySize.setUserValue(size);
    }

    /** See {@link JVM#setMethodSamplingInterval}. */
    public void setMethodSamplingInterval(long type, long intervalMillis) {
        long millis = intervalMillis;
        if (type != JfrEvents.ExecutionSample.getId()) {
            // JFR is currently only supporting ExecutionSample event, but this method is called
            // during JFR startup, so we can't throw an error.
            return;
        }

        if (millis > 0) {
            SubstrateJVM.get().setEnabled(type, true);
        } else {
            millis = 0;
        }
        ExecutionSampleEvent.setSamplingInterval(millis);
    }

    /** See {@link JVM#setSampleThreads}. */
    public void setSampleThreads(boolean sampleThreads) {
        setEnabled(JfrEvents.ExecutionSample.getId(), sampleThreads);
        setEnabled(JfrEvents.NativeMethodSample.getId(), sampleThreads);
    }

    /** See {@link JVM#setCompressedIntegers}. */
    public void setCompressedIntegers(boolean compressed) {
        if (!compressed) {
            throw new IllegalStateException("JFR currently only supports compressed integers.");
        }
    }

    /** See {@link JVM#setStackDepth}. */
    public void setStackDepth(int depth) {
        stackTraceRepo.setStackTraceDepth(depth);
    }

    /** See {@link JVM#setStackTraceEnabled}. */
    public void setStackTraceEnabled(long eventTypeId, boolean enabled) {
        eventSettings[NumUtil.safeToInt(eventTypeId)].setStackTrace(enabled);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isStackTraceEnabled(long eventTypeId) {
        assert (int) eventTypeId == eventTypeId;
        return eventSettings[(int) eventTypeId].hasStackTrace();
    }

    /** See {@link JVM#setThreadBufferSize}. */
    public void setThreadBufferSize(long size) {
        options.threadBufferSize.setUserValue(size);
    }

    /** See {@link JVM#flush}. */
    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public boolean flush(EventWriter writer, int uncommittedSize, int requestedSize) {
        assert writer != null;
        assert uncommittedSize >= 0;

        JfrBuffer oldBuffer = threadLocal.getJavaBuffer();
        assert oldBuffer.isNonNull();

        JfrBuffer newBuffer = JfrThreadLocal.flush(oldBuffer, WordFactory.unsigned(uncommittedSize), requestedSize);
        if (newBuffer.isNull()) {
            // The flush failed for some reason, so mark the EventWriter as invalid for this write
            // attempt.
            JfrEventWriterAccess.setStartPosition(writer, oldBuffer.getPos().rawValue());
            JfrEventWriterAccess.setCurrentPosition(writer, oldBuffer.getPos().rawValue());
            JfrEventWriterAccess.setValid(writer, false);
        } else {
            // Update the EventWriter so that it uses the correct buffer and positions.
            Pointer newCurrentPos = newBuffer.getPos().add(uncommittedSize);
            JfrEventWriterAccess.setStartPosition(writer, newBuffer.getPos().rawValue());
            JfrEventWriterAccess.setCurrentPosition(writer, newCurrentPos.rawValue());
            if (newBuffer.notEqual(oldBuffer)) {
                JfrEventWriterAccess.setStartPositionAddress(writer, JfrBufferAccess.getAddressOfPos(newBuffer).rawValue());
                JfrEventWriterAccess.setMaxPosition(writer, JfrBufferAccess.getDataEnd(newBuffer).rawValue());
            }
        }

        // Return false to signal that there is no need to do another flush at the end of the
        // current event.
        return false;
    }

    /** See {@link JVM#setRepositoryLocation}. */
    public void setRepositoryLocation(@SuppressWarnings("unused") String dirText) {
        // Would only be used in case of an emergency dump, which is not supported at the moment.
    }

    /** See {@link JVM#abort}. */
    public void abort(String errorMsg) {
        throw VMError.shouldNotReachHere(errorMsg);
    }

    /** See {@link JVM#shouldRotateDisk}. */
    public boolean shouldRotateDisk() {
        JfrChunkWriter chunkWriter = unlockedChunkWriter.lock();
        try {
            return chunkWriter.shouldRotateDisk();
        } finally {
            chunkWriter.unlock();
        }
    }

    public long getChunkStartNanos() {
        JfrChunkWriter chunkWriter = unlockedChunkWriter.lock();
        try {
            return chunkWriter.getChunkStartNanos();
        } finally {
            chunkWriter.unlock();
        }
    }

    /** See {@link JVM#log}. */
    public void log(int tagSetId, int level, String message) {
        jfrLogging.log(tagSetId, level, message);
    }

    /** See {@link JVM#subscribeLogLevel}. */
    public void subscribeLogLevel(@SuppressWarnings("unused") LogTag lt, @SuppressWarnings("unused") int tagSetId) {
        // Currently unused because logging support is minimal.
    }

    /** See {@link JVM#getEventWriter}. */
    public Target_jdk_jfr_internal_EventWriter getEventWriter() {
        return threadLocal.getEventWriter();
    }

    /** See {@link JVM#newEventWriter}. */
    public Target_jdk_jfr_internal_EventWriter newEventWriter() {
        return threadLocal.newEventWriter();
    }

    /** See {@link JVM#setEnabled}. */
    public void setEnabled(long eventTypeId, boolean enabled) {
        eventSettings[NumUtil.safeToInt(eventTypeId)].setEnabled(enabled);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isEnabled(JfrEvents event) {
        return eventSettings[(int) event.getId()].isEnabled();
    }

    /** See {@link JVM#setThreshold}. */
    public boolean setThreshold(long eventTypeId, long ticks) {
        eventSettings[NumUtil.safeToInt(eventTypeId)].setThresholdTicks(ticks);
        return true;
    }

    /** See {@link JVM#setCutoff}. */
    public boolean setCutoff(long eventTypeId, long cutoffTicks) {
        eventSettings[NumUtil.safeToInt(eventTypeId)].setCutoffTicks(cutoffTicks);
        return true;
    }
}
