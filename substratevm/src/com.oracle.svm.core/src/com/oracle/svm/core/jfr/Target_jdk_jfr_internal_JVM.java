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

import java.util.List;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.container.Container;
import com.oracle.svm.core.container.OperatingSystem;
import com.oracle.svm.core.jfr.traceid.JfrTraceId;
import com.oracle.svm.core.util.PlatformTimeUtils;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.LogTag;

@SuppressWarnings({"static-method", "unused"})
@TargetClass(value = jdk.jfr.internal.JVM.class, onlyWith = HasJfrSupport.class)
public final class Target_jdk_jfr_internal_JVM {
    // Checkstyle: stop
    @Alias //
    @TargetElement(onlyWith = HasChunkRotationMonitorField.class) //
    static Object CHUNK_ROTATION_MONITOR;

    @Alias //
    @TargetElement(onlyWith = HasFileDeltaChangeField.class) //
    static Object FILE_DELTA_CHANGE;
    // Checkstyle: resume

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private static volatile boolean nativeOK;

    /** See {@link JVM#registerNatives}. */
    @Substitute
    private static void registerNatives() {
    }

    @Substitute
    public static void markChunkFinal() {
        SubstrateJVM.get().markChunkFinal();
    }

    /** See {@link JVM#beginRecording}. */
    @Substitute
    public static void beginRecording() {
        SubstrateJVM.get().beginRecording();
    }

    /** See {@link JVM#isRecording}. */
    @Substitute
    @Uninterruptible(reason = "Needed for calling SubstrateJVM.isRecording().")
    public static boolean isRecording() {
        return SubstrateJVM.get().isRecording();
    }

    /** See {@link JVM#endRecording}. */
    @Substitute
    public static void endRecording() {
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
        /*
         * The result is only valid until the epoch changes but this is fine because EventWriter
         * instances are invalidated when the epoch changes.
         */
        return SubstrateJVM.get().getStackTraceId(skipCount);
    }

    @Substitute
    public static long registerStackFilter(String[] classes, String[] methods) {
        throw VMError.unimplemented("JFR StackFilters are not yet supported.");
    }

    @Substitute
    public static void unregisterStackFilter(long stackFilterId) {
        throw VMError.unimplemented("JFR StackFilters are not yet supported.");
    }

    /**
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
        SubstrateJVM.get().log(tagSetId, level, message);
    }

    /** See {@link JVM#logEvent}. */
    @Substitute
    public static void logEvent(int level, String[] lines, boolean system) {
        SubstrateJVM.get().logEvent(level, lines, system);
    }

    /** See {@link JVM#subscribeLogLevel}. */
    @Substitute
    public static void subscribeLogLevel(LogTag lt, int tagSetId) {
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
        SubstrateJVM.get().setEnabled(eventTypeId, enabled);
    }

    /** See {@link JVM#setFileNotification}. */
    @Substitute
    public static void setFileNotification(long delta) {
        SubstrateJVM.get().setFileNotification(delta);
    }

    /** See {@link JVM#setGlobalBufferCount}. */
    @Substitute
    public static void setGlobalBufferCount(long count) throws IllegalArgumentException, IllegalStateException {
        SubstrateJVM.get().setGlobalBufferCount(count);
    }

    /** See {@link JVM#setGlobalBufferSize}. */
    @Substitute
    public static void setGlobalBufferSize(long size) throws IllegalArgumentException {
        SubstrateJVM.get().setGlobalBufferSize(size);
    }

    /** See {@link JVM#setMemorySize}. */
    @Substitute
    public static void setMemorySize(long size) throws IllegalArgumentException {
        SubstrateJVM.get().setMemorySize(size);
    }

    /** See {@code JVM#setMethodSamplingPeriod}. */
    @Substitute
    public static void setMethodSamplingPeriod(long type, long intervalMillis) {
        SubstrateJVM.get().setMethodSamplingInterval(type, intervalMillis);
    }

    /** See {@link JVM#setOutput}. */
    @Substitute
    public static void setOutput(String file) {
        SubstrateJVM.get().setOutput(file);
    }

    /** See {@link JVM#setForceInstrumentation}. */
    @Substitute
    public static void setForceInstrumentation(boolean force) {
    }

    /** See {@link JVM#setCompressedIntegers}. */
    @Substitute
    public static void setCompressedIntegers(boolean compressed) throws IllegalStateException {
        SubstrateJVM.get().setCompressedIntegers(compressed);
    }

    /** See {@link JVM#setStackDepth}. */
    @Substitute
    public static void setStackDepth(int depth) throws IllegalArgumentException, IllegalStateException {
        SubstrateJVM.get().setStackDepth(depth);
    }

    /** See {@link JVM#setStackTraceEnabled}. */
    @Substitute
    public static void setStackTraceEnabled(long eventTypeId, boolean enabled) {
        SubstrateJVM.get().setStackTraceEnabled(eventTypeId, enabled);
    }

    /** See {@link JVM#setThreadBufferSize}. */
    @Substitute
    public static void setThreadBufferSize(long size) throws IllegalArgumentException, IllegalStateException {
        SubstrateJVM.get().setThreadBufferSize(size);
    }

    /** See {@link JVM#setThreshold}. */
    @Substitute
    public static boolean setThreshold(long eventTypeId, long ticks) {
        return SubstrateJVM.get().setThreshold(eventTypeId, ticks);
    }

    /** See {@link JVM#storeMetadataDescriptor}. */
    @Substitute
    public static void storeMetadataDescriptor(byte[] bytes) {
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
        return SubstrateJVM.get().createJFR(simulateFailure);
    }

    /** See {@link JVM#destroyJFR}. */
    @Substitute
    private static boolean destroyJFR() {
        return SubstrateJVM.get().destroyJFR();
    }

    /** See {@link JVM#isAvailable}. */
    @Substitute
    public static boolean isAvailable() {
        return true;
    }

    /** See {@link JVM#getTimeConversionFactor}. */
    @Substitute
    public static double getTimeConversionFactor() {
        return 1;
    }

    /** See {@link JVM#getTypeId(Class)}. */
    @Substitute
    public static long getTypeId(Class<?> clazz) {
        return JfrTraceId.getTraceId(clazz);
    }

    /** See {@link JVM#getEventWriter}. */
    @Substitute
    public static Target_jdk_jfr_internal_event_EventWriter getEventWriter() {
        return SubstrateJVM.get().getEventWriter();
    }

    /** See {@link JVM#newEventWriter}. */
    @Substitute
    public static Target_jdk_jfr_internal_event_EventWriter newEventWriter() {
        return SubstrateJVM.get().newEventWriter();
    }

    /** See {@link JVM#flush}. */
    @Substitute
    public static void flush(Target_jdk_jfr_internal_event_EventWriter writer, int uncommittedSize, int requestedSize) {
        SubstrateJVM.get().flush(writer, uncommittedSize, requestedSize);
    }

    @Substitute
    public static void flush() {
        SubstrateJVM.get().flush();
    }

    @Substitute
    public static long commit(long nextPosition) {
        return SubstrateJVM.get().commit(nextPosition);
    }

    /** See {@link JVM#setRepositoryLocation}. */
    @Substitute
    public static void setRepositoryLocation(String dirText) {
        SubstrateJVM.get().setRepositoryLocation(dirText);
    }

    /** See {@code JVM#setDumpPath(String)}. */
    @Substitute
    public static void setDumpPath(String dumpPathText) {
        SubstrateJVM.get().setDumpPath(dumpPathText);
    }

    /** See {@code JVM#getDumpPath()}. */
    @Substitute
    public static String getDumpPath() {
        return SubstrateJVM.get().getDumpPath();
    }

    /** See {@link JVM#abort}. */
    @Substitute
    public static void abort(String errorMsg) {
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
        // Would be used to determine the emergency dump filename if an exception happens during
        // shutdown.
    }

    /** See {@link JVM#setCutoff}. */
    @Substitute
    public static boolean setCutoff(long eventTypeId, long cutoffTicks) {
        return SubstrateJVM.get().setCutoff(eventTypeId, cutoffTicks);
    }

    /** See {@link JVM#setThrottle}. */
    @Substitute
    public static boolean setThrottle(long eventTypeId, long eventSampleSize, long periodMs) {
        return SubstrateJVM.get().setThrottle(eventTypeId, eventSampleSize, periodMs);
    }

    /** See {@link JVM#emitOldObjectSamples}. */
    @Substitute
    public static void emitOldObjectSamples(long cutoff, boolean emitAll, boolean skipBFS) {
        SubstrateJVM.get().emitOldObjectSamples(cutoff, emitAll, skipBFS);
    }

    /** See {@link JVM#shouldRotateDisk}. */
    @Substitute
    public static boolean shouldRotateDisk() {
        return SubstrateJVM.get().shouldRotateDisk();
    }

    @Substitute
    public static void include(Thread thread) {
        JfrThreadLocal.setExcluded(thread, false);
    }

    @Substitute
    public static void exclude(Thread thread) {
        JfrThreadLocal.setExcluded(thread, true);
    }

    @Substitute
    public static boolean isExcluded(Thread thread) {
        return JfrThreadLocal.isThreadExcluded(thread);
    }

    @Substitute
    public static boolean isExcluded(Class<? extends jdk.internal.event.Event> eventClass) {
        // Temporarily always include.
        return false;
    }

    @Substitute
    public static boolean isInstrumented(Class<? extends jdk.internal.event.Event> eventClass) {
        // This should check for blessed commit methods in the event class [GR-41200]
        return true;
    }

    /** See {@link SubstrateJVM#getChunkStartNanos}. */
    @Substitute
    public static long getChunkStartNanos() {
        return SubstrateJVM.get().getChunkStartNanos();
    }

    @Substitute
    public static boolean setConfiguration(Class<? extends jdk.internal.event.Event> eventClass, Target_jdk_jfr_internal_event_EventConfiguration configuration) {
        return SubstrateJVM.get().setConfiguration(eventClass, configuration);
    }

    @Substitute
    public static Object getConfiguration(Class<? extends jdk.internal.event.Event> eventClass) {
        return SubstrateJVM.get().getConfiguration(eventClass);
    }

    /** See {@link JVM#getTypeId(String)}. */
    @Substitute
    public static long getTypeId(String name) {
        /* Not implemented at the moment. */
        return -1;
    }

    @Substitute
    public static boolean isContainerized() {
        return Container.singleton().isContainerized();
    }

    /**
     * Return the total memory available on the host.
     *
     * This is unconditionally using {@link OperatingSystem#getPhysicalMemorySize} since we are
     * interested in the host values (and not the containerized values).
     */
    @Substitute
    public static long hostTotalMemory() {
        return OperatingSystem.singleton().getPhysicalMemorySize().rawValue();
    }

    @Substitute
    public static long hostTotalSwapMemory() {
        /* Not implemented at the moment. */
        return -1;
    }

    @Substitute
    public static boolean isProduct() {
        /*
         * Currently only used for jdk.jfr.internal.tool.Command, which is not relevant for us. We
         * implement it nevertheless and return true to disable non-product features.
         */
        return true;
    }
}

class HasChunkRotationMonitorField implements BooleanSupplier {
    private static final boolean HAS_FIELD = ReflectionUtil.lookupField(true, JVM.class, "CHUNK_ROTATION_MONITOR") != null;

    @Override
    public boolean getAsBoolean() {
        return HAS_FIELD;
    }

    @Fold
    public static boolean get() {
        return HAS_FIELD;
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class HasFileDeltaChangeField implements BooleanSupplier {
    private static final boolean HAS_FIELD = ReflectionUtil.lookupField(true, JVM.class, "FILE_DELTA_CHANGE") != null;

    @Override
    public boolean getAsBoolean() {
        return HAS_FIELD;
    }
}
