/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr;

import static com.oracle.svm.core.jfr.Target_jdk_jfr_internal_JVM_Util.jfrNotSupportedException;

import java.util.List;

import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.container.Container;
import com.oracle.svm.core.container.OperatingSystem;
import com.oracle.svm.core.jfr.traceid.JfrTraceId;
import com.oracle.svm.core.util.PlatformTimeUtils;

import jdk.jfr.internal.JVM;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.event.EventWriter;

/**
 * The substitutions below are always active, even if the JFR support is disabled. Otherwise, we
 * would see an {@link UnsatisfiedLinkError} if a JFR native method is called at run-time.
 */
@SuppressWarnings({"static-method", "unused"})
@TargetClass(value = jdk.jfr.internal.JVM.class)
public final class Target_jdk_jfr_internal_JVM {
    // Checkstyle: stop
    @Alias //
    static Object CHUNK_ROTATION_MONITOR;
    // Checkstyle: resume

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private static volatile boolean nativeOK;

    /** See {@link JVM#registerNatives}. */
    @Substitute
    private static void registerNatives() {
    }

    /** See {@link JVM#markChunkFinal}. */
    @Substitute
    public static void markChunkFinal() {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().markChunkFinal();
    }

    /** See {@link JVM#beginRecording}. */
    @Substitute
    public static void beginRecording() {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().beginRecording();
    }

    /** See {@link JVM#isRecording}. */
    @Substitute
    @Uninterruptible(reason = "Needed for calling SubstrateJVM.isRecording().")
    public static boolean isRecording() {
        if (!HasJfrSupport.get()) {
            return false;
        }
        return SubstrateJVM.get().isRecording();
    }

    /** See {@link JVM#endRecording}. */
    @Substitute
    public static void endRecording() {
        if (!HasJfrSupport.get()) {
            /* Nothing to do. */
            return;
        }
        SubstrateJVM.get().endRecording();
    }

    /** See {@link JVM#counterTime}. */
    @Substitute
    public static long counterTime() {
        return JfrTicks.elapsedTicks();
    }

    /** See {@link JVM#emitEvent}. */
    @Substitute
    public static boolean emitEvent(long eventTypeId, long timestamp, long when) {
        return false;
    }

    /** See {@link JVM#getAllEventClasses}. */
    @Substitute
    public static List<Class<? extends jdk.internal.event.Event>> getAllEventClasses() {
        return JfrJavaEvents.getAllEventClasses();
    }

    /** See {@link JVM#getUnloadedEventClassCount}. */
    @Substitute
    public static long getUnloadedEventClassCount() {
        return 0;
    }

    /** See {@link JVM#getClassId}. Intrinsified on HotSpot. */
    @Substitute
    @Uninterruptible(reason = "Needed for SubstrateJVM.getClassId().")
    public static long getClassId(Class<?> clazz) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }

        /*
         * The result is only valid until the epoch changes but this is fine because EventWriter
         * instances are invalidated when the epoch changes.
         */
        return SubstrateJVM.get().getClassId(clazz);
    }

    /** See {@link JVM#getPid}. */
    @Substitute
    public static String getPid() {
        long id = ProcessProperties.getProcessID();
        return String.valueOf(id);
    }

    /** See {@link JVM#getStackTraceId}. */
    @Substitute
    @Uninterruptible(reason = "Needed for SubstrateJVM.getStackTraceId().")
    public static long getStackTraceId(int skipCount, long stackFilterId) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }

        /*
         * The result is only valid until the epoch changes but this is fine because EventWriter
         * instances are invalidated when the epoch changes.
         */
        return SubstrateJVM.get().getStackTraceId(skipCount);
    }

    /** See {@link JVM#registerStackFilter}. */
    @Substitute
    public static long registerStackFilter(String[] classes, String[] methods) {
        throw new UnsupportedOperationException("JFR stack filters are not supported at the moment.");
    }

    /** See {@link JVM#unregisterStackFilter}. */
    @Substitute
    public static void unregisterStackFilter(long stackFilterId) {
        /* Ignore the call for now (registerStackFilter() is not implemented). */
    }

    /**
     * See {@link JVM#setMiscellaneous}.
     * <p>
     * As of 22+27, This method is both used to set cutoff tick values for leak profiling and
     * for @Deprecated events. Note that this method is called during JFR startup.
     */
    @Substitute
    public static void setMiscellaneous(long eventTypeId, long value) {
        /* Ignore the call and don't throw an exception (would result in an unspecific warning). */
    }

    /** See {@link JVM#getThreadId}. */
    @Substitute
    public static long getThreadId(Thread t) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.getThreadId(t);
    }

    /** See {@link JVM#getTicksFrequency}. */
    @Substitute
    public static long getTicksFrequency() {
        return JfrTicks.getTicksFrequency();
    }

    /** See {@code JVM#nanosNow}. */
    @Substitute
    public static long nanosNow() {
        return PlatformTimeUtils.singleton().nanosNow();
    }

    /** See {@link JVM#log}. */
    @Substitute
    public static void log(int tagSetId, int level, String message) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().log(tagSetId, level, message);
    }

    /** See {@link JVM#logEvent}. */
    @Substitute
    public static void logEvent(int level, String[] lines, boolean system) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().logEvent(level, lines, system);
    }

    /** See {@link JVM#subscribeLogLevel}. */
    @Substitute
    public static void subscribeLogLevel(LogTag lt, int tagSetId) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().subscribeLogLevel(lt, tagSetId);
    }

    /** See {@link JVM#retransformClasses}. */
    @Substitute
    public static synchronized void retransformClasses(Class<?>[] classes) {
        // Not supported but this method is called during JFR startup, so we can't throw an error.
    }

    /** See {@link JVM#setEnabled}. */
    @Substitute
    public static void setEnabled(long eventTypeId, boolean enabled) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setEnabled(eventTypeId, enabled);
    }

    /** See {@link JVM#setFileNotification}. */
    @Substitute
    public static void setFileNotification(long delta) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setFileNotification(delta);
    }

    /** See {@link JVM#setGlobalBufferCount}. */
    @Substitute
    public static void setGlobalBufferCount(long count) throws IllegalArgumentException, IllegalStateException {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setGlobalBufferCount(count);
    }

    /** See {@link JVM#setGlobalBufferSize}. */
    @Substitute
    public static void setGlobalBufferSize(long size) throws IllegalArgumentException {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setGlobalBufferSize(size);
    }

    /** See {@link JVM#setMemorySize}. */
    @Substitute
    public static void setMemorySize(long size) throws IllegalArgumentException {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setMemorySize(size);
    }

    /** See {@code JVM#setMethodSamplingPeriod}. */
    @Substitute
    public static void setMethodSamplingPeriod(long type, long intervalMillis) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setMethodSamplingInterval(type, intervalMillis);
    }

    /** See {@code JVM#setCPURate}. */
    @Substitute
    public static void setCPURate(double rate) {
        // JFR CPUTimeSample is not supported.
    }

    /** See {@code JVM#setCPUPeriod}. */
    @Substitute
    public static void setCPUPeriod(long periodNanos) {
        // JFR CPUTimeSample is not supported.
    }

    /** See {@link JVM#setOutput}. */
    @Substitute
    public static void setOutput(String file) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setOutput(file);
    }

    /** See {@link JVM#setForceInstrumentation}. */
    @Substitute
    public static void setForceInstrumentation(boolean force) {
    }

    /** See {@link JVM#setCompressedIntegers}. */
    @Substitute
    public static void setCompressedIntegers(boolean compressed) throws IllegalStateException {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setCompressedIntegers(compressed);
    }

    /** See {@link JVM#setStackDepth}. */
    @Substitute
    public static void setStackDepth(int depth) throws IllegalArgumentException, IllegalStateException {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setStackDepth(depth);
    }

    /** See {@link JVM#setStackTraceEnabled}. */
    @Substitute
    public static void setStackTraceEnabled(long eventTypeId, boolean enabled) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setStackTraceEnabled(eventTypeId, enabled);
    }

    /** See {@link JVM#setThreadBufferSize}. */
    @Substitute
    public static void setThreadBufferSize(long size) throws IllegalArgumentException, IllegalStateException {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setThreadBufferSize(size);
    }

    /** See {@link JVM#setThreshold}. */
    @Substitute
    public static boolean setThreshold(long eventTypeId, long ticks) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().setThreshold(eventTypeId, ticks);
    }

    /** See {@link JVM#storeMetadataDescriptor}. */
    @Substitute
    public static void storeMetadataDescriptor(byte[] bytes) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().storeMetadataDescriptor(bytes);
    }

    /** See {@link JVM#getAllowedToDoEventRetransforms}. */
    @Substitute
    public static boolean getAllowedToDoEventRetransforms() {
        return false;
    }

    /** See {@link JVM#createJFR}. */
    @Substitute
    private static boolean createJFR(boolean simulateFailure) throws IllegalStateException {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().createJFR(simulateFailure);
    }

    /** See {@link JVM#destroyJFR}. */
    @Substitute
    private static boolean destroyJFR() {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().destroyJFR();
    }

    /** See {@link JVM#isAvailable}. */
    @Substitute
    public static boolean isAvailable() {
        return HasJfrSupport.get();
    }

    /** See {@link JVM#getTimeConversionFactor}. */
    @Substitute
    public static double getTimeConversionFactor() {
        return 1;
    }

    /** See {@link JVM#getTypeId(Class)}. */
    @Substitute
    public static long getTypeId(Class<?> clazz) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return JfrTraceId.getTraceId(clazz);
    }

    /** See {@link JVM#getEventWriter}. */
    @Substitute
    public static Target_jdk_jfr_internal_event_EventWriter getEventWriter() {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().getEventWriter();
    }

    /** See {@link JVM#newEventWriter}. */
    @Substitute
    public static Target_jdk_jfr_internal_event_EventWriter newEventWriter() {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().newEventWriter();
    }

    /** See {@link JVM#flush(EventWriter, int, int)}. */
    @Substitute
    public static void flush(Target_jdk_jfr_internal_event_EventWriter writer, int uncommittedSize, int requestedSize) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().flush(writer, uncommittedSize, requestedSize);
    }

    /** See {@link JVM#flush()}. */
    @Substitute
    public static void flush() {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().flush();
    }

    /** See {@link JVM#commit}. */
    @Substitute
    public static long commit(long nextPosition) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().commit(nextPosition);
    }

    /** See {@link JVM#setRepositoryLocation}. */
    @Substitute
    public static void setRepositoryLocation(String dirText) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setRepositoryLocation(dirText);
    }

    /** See {@code JVM#setDumpPath(String)}. */
    @Substitute
    public static void setDumpPath(String dumpPathText) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setDumpPath(dumpPathText);
    }

    /** See {@code JVM#getDumpPath()}. */
    @Substitute
    public static String getDumpPath() {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().getDumpPath();
    }

    /** See {@link JVM#abort}. */
    @Substitute
    public static void abort(String errorMsg) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().abort(errorMsg);
    }

    /** See {@link JVM#addStringConstant}. */
    @Substitute
    public static boolean addStringConstant(long id, String s) {
        return false;
    }

    /** See {@link JVM#uncaughtException}. */
    @Substitute
    public static void uncaughtException(Thread thread, Throwable t) {
        /*
         * Would be used to determine the emergency dump filename if an exception happens during
         * shutdown.
         */
    }

    /** See {@link JVM#setCutoff}. */
    @Substitute
    public static boolean setCutoff(long eventTypeId, long cutoffTicks) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().setCutoff(eventTypeId, cutoffTicks);
    }

    /** See {@link JVM#setThrottle}. */
    @Substitute
    public static boolean setThrottle(long eventTypeId, long eventSampleSize, long periodMs) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().setThrottle(eventTypeId, eventSampleSize, periodMs);
    }

    /** See {@link JVM#emitOldObjectSamples}. */
    @Substitute
    public static void emitOldObjectSamples(long cutoff, boolean emitAll, boolean skipBFS) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().emitOldObjectSamples(cutoff, emitAll, skipBFS);
    }

    /** See {@link JVM#shouldRotateDisk}. */
    @Substitute
    public static boolean shouldRotateDisk() {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().shouldRotateDisk();
    }

    /** See {@link JVM#include}. */
    @Substitute
    public static void include(Thread thread) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        JfrThreadLocal.setExcluded(thread, false);
    }

    /** See {@link JVM#exclude}. */
    @Substitute
    public static void exclude(Thread thread) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        JfrThreadLocal.setExcluded(thread, true);
    }

    /** See {@link JVM#isExcluded(Thread)}. */
    @Substitute
    public static boolean isExcluded(Thread thread) {
        if (!HasJfrSupport.get()) {
            return true;
        }
        return JfrThreadLocal.isThreadExcluded(thread);
    }

    /** See {@link JVM#isExcluded(Class)}. */
    @Substitute
    public static boolean isExcluded(Class<? extends jdk.internal.event.Event> eventClass) {
        /* For now, assume that event classes are only excluded if JFR support is disabled. */
        return !HasJfrSupport.get();
    }

    /** See {@link JVM#isInstrumented}. */
    @Substitute
    public static boolean isInstrumented(Class<? extends jdk.internal.event.Event> eventClass) {
        /*
         * Assume that event classes are instrumented if JFR support is present. This method should
         * ideally check for blessed commit methods in the event class, see GR-41200.
         */
        return HasJfrSupport.get();
    }

    /** See {@link JVM#getChunkStartNanos}. */
    @Substitute
    public static long getChunkStartNanos() {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().getChunkStartNanos();
    }

    /** See {@link JVM#setConfiguration}. */
    @Substitute
    public static boolean setConfiguration(Class<? extends jdk.internal.event.Event> eventClass, Target_jdk_jfr_internal_event_EventConfiguration configuration) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().setConfiguration(eventClass, configuration);
    }

    /** See {@link JVM#getConfiguration}. */
    @Substitute
    public static Object getConfiguration(Class<? extends jdk.internal.event.Event> eventClass) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().getConfiguration(eventClass);
    }

    /** See {@link JVM#getTypeId(String)}. */
    @Substitute
    public static long getTypeId(String name) {
        /* Not implemented at the moment. */
        return -1;
    }

    /** See {@link JVM#isContainerized}. */
    @Substitute
    public static boolean isContainerized() {
        return Container.singleton().isContainerized();
    }

    /**
     * See {@link JVM#hostTotalMemory()}.
     * <p>
     * This calls {@link OperatingSystem#getPhysicalMemorySize} since we are interested in the host
     * values (and not the containerized values).
     */
    @Substitute
    public static long hostTotalMemory() {
        return OperatingSystem.singleton().getPhysicalMemorySize().rawValue();
    }

    /** See {@link JVM#hostTotalSwapMemory}. */
    @Substitute
    public static long hostTotalSwapMemory() {
        /* Not implemented at the moment. */
        return -1;
    }

    /** See {@link JVM#isProduct}. */
    @Substitute
    public static boolean isProduct() {
        /*
         * Currently only used for jdk.jfr.internal.tool.Command, which is not relevant for us. We
         * implement it nevertheless and return true to disable non-product features.
         */
        return true;
    }

    /** See {@link JVM#setMethodTraceFilters}. */
    @Substitute
    public static long[] setMethodTraceFilters(String[] classes, String[] methods, String[] annotations, int[] modification) {
        // JFR method tracing is not supported. No filters can be used so return null.
        return null;
    }

    /** See {@link JVM#drainStaleMethodTracerIds}. */
    @Substitute
    public static long[] drainStaleMethodTracerIds() {
        // JFR method tracing is not supported. Return no stale IDs.
        return null;
    }
}

class Target_jdk_jfr_internal_JVM_Util {
    static UnsupportedOperationException jfrNotSupportedException() {
        throw new UnsupportedOperationException(VMInspectionOptions.getJfrNotSupportedMessage());
    }
}
