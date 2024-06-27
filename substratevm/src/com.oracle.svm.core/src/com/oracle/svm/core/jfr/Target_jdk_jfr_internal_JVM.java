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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.svm.core.Containers;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.heap.PhysicalMemory.PhysicalMemorySupport;
import com.oracle.svm.core.jdk.JDK22OrLater;
import com.oracle.svm.core.jdk.JDK23OrLater;
import com.oracle.svm.core.jfr.traceid.JfrTraceId;
import com.oracle.svm.core.util.PlatformTimeUtils;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;

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
    @TargetElement(onlyWith = JDK22OrLater.class) //
    private static volatile boolean nativeOK;

    /** See {@link JVM#registerNatives}. */
    @Substitute
    private static void registerNatives() {
    }

    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void markChunkFinal() {
        SubstrateJVM.get().markChunkFinal();
    }

    /** See {@link JVM#beginRecording}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void beginRecording() {
        SubstrateJVM.get().beginRecording();
    }

    /** See {@link JVM#isRecording}. */
    @Substitute
    @Uninterruptible(reason = "Needed for calling SubstrateJVM.isRecording().")
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static boolean isRecording() {
        return SubstrateJVM.get().isRecording();
    }

    /** See {@link JVM#endRecording}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
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
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static boolean emitEvent(long eventTypeId, long timestamp, long when) {
        return false;
    }

    /** See {@link JVM#getAllEventClasses}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static List<Class<? extends jdk.internal.event.Event>> getAllEventClasses() {
        return JfrJavaEvents.getAllEventClasses();
    }

    /** See {@link JVM#getUnloadedEventClassCount}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
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
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static String getPid() {
        long id = ProcessProperties.getProcessID();
        return String.valueOf(id);
    }

    /** See {@link JVM#getStackTraceId}. */
    @Substitute
    @Uninterruptible(reason = "Needed for SubstrateJVM.getStackTraceId().")
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static long getStackTraceId(int skipCount, long stackFilterId) {
        /*
         * The result is only valid until the epoch changes but this is fine because EventWriter
         * instances are invalidated when the epoch changes.
         */
        return SubstrateJVM.get().getStackTraceId(skipCount);
    }

    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static long registerStackFilter(String[] classes, String[] methods) {
        throw VMError.unimplemented("JFR StackFilters are not yet supported.");
    }

    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void unregisterStackFilter(long stackFilterId) {
        throw VMError.unimplemented("JFR StackFilters are not yet supported.");
    }

    /**
     * As of 22+27, This method is both used to set cutoff tick values for leak profiling and
     * for @Deprecated events.
     */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void setMiscellaneous(long eventTypeId, long value) {
        Logger.log(LogTag.JFR_SETTING, LogLevel.WARN, "@Deprecated JFR events, and leak profiling are not yet supported.");
        /* Explicitly don't throw an exception (would result in an unspecific warning). */
    }

    /** See {@link JVM#getThreadId}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static long getThreadId(Thread t) {
        return SubstrateJVM.getThreadId(t);
    }

    /** See {@link JVM#getTicksFrequency}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static long getTicksFrequency() {
        return JfrTicks.getTicksFrequency();
    }

    /** See {@code JVM#nanosNow}. */
    @Substitute
    @TargetElement(onlyWith = JDK23OrLater.class)
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
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static synchronized void retransformClasses(Class<?>[] classes) {
        // Not supported but this method is called during JFR startup, so we can't throw an error.
    }

    /** See {@link JVM#setEnabled}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void setEnabled(long eventTypeId, boolean enabled) {
        SubstrateJVM.get().setEnabled(eventTypeId, enabled);
    }

    /** See {@link JVM#setFileNotification}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void setFileNotification(long delta) {
        SubstrateJVM.get().setFileNotification(delta);
    }

    /** See {@link JVM#setGlobalBufferCount}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void setGlobalBufferCount(long count) throws IllegalArgumentException, IllegalStateException {
        SubstrateJVM.get().setGlobalBufferCount(count);
    }

    /** See {@link JVM#setGlobalBufferSize}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void setGlobalBufferSize(long size) throws IllegalArgumentException {
        SubstrateJVM.get().setGlobalBufferSize(size);
    }

    /** See {@link JVM#setMemorySize}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void setMemorySize(long size) throws IllegalArgumentException {
        SubstrateJVM.get().setMemorySize(size);
    }

    /** See {@code JVM#setMethodSamplingPeriod}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void setMethodSamplingPeriod(long type, long intervalMillis) {
        SubstrateJVM.get().setMethodSamplingInterval(type, intervalMillis);
    }

    /** See {@link JVM#setOutput}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void setOutput(String file) {
        SubstrateJVM.get().setOutput(file);
    }

    /** See {@link JVM#setForceInstrumentation}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void setForceInstrumentation(boolean force) {
    }

    /** See {@link JVM#setCompressedIntegers}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void setCompressedIntegers(boolean compressed) throws IllegalStateException {
        SubstrateJVM.get().setCompressedIntegers(compressed);
    }

    /** See {@link JVM#setStackDepth}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void setStackDepth(int depth) throws IllegalArgumentException, IllegalStateException {
        SubstrateJVM.get().setStackDepth(depth);
    }

    /** See {@link JVM#setStackTraceEnabled}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void setStackTraceEnabled(long eventTypeId, boolean enabled) {
        SubstrateJVM.get().setStackTraceEnabled(eventTypeId, enabled);
    }

    /** See {@link JVM#setThreadBufferSize}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void setThreadBufferSize(long size) throws IllegalArgumentException, IllegalStateException {
        SubstrateJVM.get().setThreadBufferSize(size);
    }

    /** See {@link JVM#setThreshold}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static boolean setThreshold(long eventTypeId, long ticks) {
        return SubstrateJVM.get().setThreshold(eventTypeId, ticks);
    }

    /** See {@link JVM#storeMetadataDescriptor}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void storeMetadataDescriptor(byte[] bytes) {
        SubstrateJVM.get().storeMetadataDescriptor(bytes);
    }

    /** See {@link JVM#getAllowedToDoEventRetransforms}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static boolean getAllowedToDoEventRetransforms() {
        return false;
    }

    /** See {@link JVM#createJFR}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    private static boolean createJFR(boolean simulateFailure) throws IllegalStateException {
        return SubstrateJVM.get().createJFR(simulateFailure);
    }

    /** See {@link JVM#destroyJFR}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    private static boolean destroyJFR() {
        return SubstrateJVM.get().destroyJFR();
    }

    /** See {@link JVM#isAvailable}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static boolean isAvailable() {
        return true;
    }

    /** See {@link JVM#getTimeConversionFactor}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static double getTimeConversionFactor() {
        return 1;
    }

    /** See {@link JVM#getTypeId(Class)}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
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
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void flush() {
        SubstrateJVM.get().flush();
    }

    @Substitute
    public static long commit(long nextPosition) {
        return SubstrateJVM.get().commit(nextPosition);
    }

    /** See {@link JVM#setRepositoryLocation}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void setRepositoryLocation(String dirText) {
        SubstrateJVM.get().setRepositoryLocation(dirText);
    }

    /** See {@code JVM#setDumpPath(String)}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void setDumpPath(String dumpPathText) {
        SubstrateJVM.get().setDumpPath(dumpPathText);
    }

    /** See {@code JVM#getDumpPath()}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static String getDumpPath() {
        return SubstrateJVM.get().getDumpPath();
    }

    /** See {@link JVM#abort}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
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
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void uncaughtException(Thread thread, Throwable t) {
        // Would be used to determine the emergency dump filename if an exception happens during
        // shutdown.
    }

    /** See {@link JVM#setCutoff}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static boolean setCutoff(long eventTypeId, long cutoffTicks) {
        return SubstrateJVM.get().setCutoff(eventTypeId, cutoffTicks);
    }

    /** See {@link JVM#setThrottle}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static boolean setThrottle(long eventTypeId, long eventSampleSize, long periodMs) {
        return SubstrateJVM.get().setThrottle(eventTypeId, eventSampleSize, periodMs);
    }

    /** See {@link JVM#emitOldObjectSamples}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void emitOldObjectSamples(long cutoff, boolean emitAll, boolean skipBFS) {
        SubstrateJVM.get().emitOldObjectSamples(cutoff, emitAll, skipBFS);
    }

    /** See {@link JVM#shouldRotateDisk}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static boolean shouldRotateDisk() {
        return SubstrateJVM.get().shouldRotateDisk();
    }

    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void include(Thread thread) {
        JfrThreadLocal.setExcluded(thread, false);
    }

    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static void exclude(Thread thread) {
        JfrThreadLocal.setExcluded(thread, true);
    }

    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static boolean isExcluded(Thread thread) {
        return JfrThreadLocal.isThreadExcluded(thread);
    }

    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class) //
    public static boolean isExcluded(Class<? extends jdk.internal.event.Event> eventClass) {
        // Temporarily always include.
        return false;
    }

    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class) //
    public static boolean isInstrumented(Class<? extends jdk.internal.event.Event> eventClass) {
        // This should check for blessed commit methods in the event class [GR-41200]
        return true;
    }

    /** See {@link SubstrateJVM#getChunkStartNanos}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static long getChunkStartNanos() {
        return SubstrateJVM.get().getChunkStartNanos();
    }

    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class) //
    public static boolean setConfiguration(Class<? extends jdk.internal.event.Event> eventClass, Target_jdk_jfr_internal_event_EventConfiguration configuration) {
        return SubstrateJVM.get().setConfiguration(eventClass, configuration);
    }

    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class) //
    public static Object getConfiguration(Class<? extends jdk.internal.event.Event> eventClass) {
        return SubstrateJVM.get().getConfiguration(eventClass);
    }

    /** See {@link JVM#getTypeId(String)}. */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    public static long getTypeId(String name) {
        /* Not implemented at the moment. */
        return -1;
    }

    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class) //
    public static boolean isContainerized() {
        return Containers.isContainerized();
    }

    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class) //
    public static long hostTotalMemory() {
        // This is intentionally using PhysicalMemorySupport since we are
        // interested in the host values (and not the containerized values).
        return ImageSingletons.lookup(PhysicalMemorySupport.class).size().rawValue();
    }

    @Substitute
    @TargetElement(onlyWith = JDK23OrLater.class) //
    public static long hostTotalSwapMemory() {
        /* Not implemented at the moment. */
        return -1;
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
