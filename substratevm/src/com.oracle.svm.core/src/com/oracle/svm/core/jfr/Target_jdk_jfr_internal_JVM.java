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
package com.oracle.svm.core.jfr;

import java.util.List;

import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.svm.core.Containers;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK11OrEarlier;
import com.oracle.svm.core.jdk.JDK17OrEarlier;
import com.oracle.svm.core.jdk.JDK17OrLater;
import com.oracle.svm.core.jdk.JDK19OrLater;
import com.oracle.svm.core.jfr.traceid.JfrTraceId;
import com.oracle.svm.core.util.VMError;

import jdk.jfr.Event;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.LogTag;

@SuppressWarnings({"static-method", "unused"})
@TargetClass(value = jdk.jfr.internal.JVM.class, onlyWith = HasJfrSupport.class)
public final class Target_jdk_jfr_internal_JVM {
    // Checkstyle: stop
    @Alias static Object FILE_DELTA_CHANGE;
    // Checkstyle: resume

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private volatile boolean nativeOK;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    @TargetElement(onlyWith = JDK11OrEarlier.class) //
    private volatile boolean recording;

    /** See {@link JVM#registerNatives}. */
    @Substitute
    private static void registerNatives() {
    }

    /** See {@link JVM#beginRecording}. */
    @Substitute
    public void beginRecording() {
        SubstrateJVM.get().beginRecording();
    }

    /** See {@link JVM#counterTime}. */
    @Substitute
    public static long counterTime() {
        return JfrTicks.elapsedTicks();
    }

    /** See {@link JVM#emitEvent}. */
    @Substitute
    public boolean emitEvent(long eventTypeId, long timestamp, long when) {
        return false;
    }

    /** See {@link JVM#endRecording}. */
    @Substitute
    public void endRecording() {
        SubstrateJVM.get().endRecording();
    }

    /** See {@link JVM#isRecording}. */
    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    public boolean isRecording() {
        return SubstrateJVM.get().unsafeIsRecording();
    }

    /** See {@link JVM#getAllEventClasses}. */
    @Substitute
    public List<Class<? extends Event>> getAllEventClasses() {
        return JfrJavaEvents.getAllEventClasses();
    }

    /** See {@link JVM#getUnloadedEventClassCount}. */
    @Substitute
    public long getUnloadedEventClassCount() {
        return 0;
    }

    /** See {@link JVM#getClassId}. Intrinsified on HotSpot. */
    @Substitute
    public static long getClassId(Class<?> clazz) {
        return SubstrateJVM.get().getClassId(clazz);
    }

    /** See JVM.getClassIdNonIntrinsic(Class). */
    @Substitute
    @TargetElement(onlyWith = JDK11OrEarlier.class)
    public static long getClassIdNonIntrinsic(Class<?> clazz) {
        return getClassId(clazz);
    }

    /** See {@link JVM#getPid}. */
    @Substitute
    public String getPid() {
        long id = ProcessProperties.getProcessID();
        return String.valueOf(id);
    }

    /** See {@link JVM#getStackTraceId}. */
    @Substitute
    public long getStackTraceId(int skipCount) {
        return SubstrateJVM.get().getStackTraceId(skipCount);
    }

    /** See {@link JVM#getThreadId}. */
    @Substitute
    public long getThreadId(Thread t) {
        return SubstrateJVM.getThreadId(t);
    }

    /** See {@link JVM#getTicksFrequency}. */
    @Substitute
    public long getTicksFrequency() {
        return JfrTicks.getTicksFrequency();
    }

    /** See {@link JVM#log}. */
    @Substitute
    public static void log(int tagSetId, int level, String message) {
        SubstrateJVM.get().log(tagSetId, level, message);
    }

    /** See {@link JVM#subscribeLogLevel}. */
    @Substitute
    public static void subscribeLogLevel(LogTag lt, int tagSetId) {
        SubstrateJVM.get().subscribeLogLevel(lt, tagSetId);
    }

    /** See {@link JVM#retransformClasses}. */
    @Substitute
    public synchronized void retransformClasses(Class<?>[] classes) {
        // Not supported but this method is called during JFR startup, so we can't throw an error.
    }

    /** See {@link JVM#setEnabled}. */
    @Substitute
    public void setEnabled(long eventTypeId, boolean enabled) {
        SubstrateJVM.get().setEnabled(eventTypeId, enabled);
    }

    /** See {@link JVM#setFileNotification}. */
    @Substitute
    public void setFileNotification(long delta) {
        SubstrateJVM.get().setFileNotification(delta);
    }

    /** See {@link JVM#setGlobalBufferCount}. */
    @Substitute
    public void setGlobalBufferCount(long count) throws IllegalArgumentException, IllegalStateException {
        SubstrateJVM.get().setGlobalBufferCount(count);
    }

    /** See {@link JVM#setGlobalBufferSize}. */
    @Substitute
    public void setGlobalBufferSize(long size) throws IllegalArgumentException {
        SubstrateJVM.get().setGlobalBufferSize(size);
    }

    /** See {@link JVM#setMemorySize}. */
    @Substitute
    public void setMemorySize(long size) throws IllegalArgumentException {
        SubstrateJVM.get().setMemorySize(size);
    }

    /** See {@code JVM#setMethodSamplingInterval}. */
    @Substitute
    @TargetElement(onlyWith = JDK17OrEarlier.class)
    public void setMethodSamplingInterval(long type, long intervalMillis) {
        SubstrateJVM.get().setMethodSamplingInterval(type, intervalMillis);
    }

    /** See {@code JVM#setMethodSamplingPeriod}. */
    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class)
    public void setMethodSamplingPeriod(long type, long intervalMillis) {
        SubstrateJVM.get().setMethodSamplingInterval(type, intervalMillis);
    }

    /** See {@link JVM#setOutput}. */
    @Substitute
    public void setOutput(String file) {
        SubstrateJVM.get().setOutput(file);
    }

    /** See {@link JVM#setForceInstrumentation}. */
    @Substitute
    public void setForceInstrumentation(boolean force) {
    }

    @Substitute
    @TargetElement(onlyWith = JDK17OrEarlier.class)
    public void setSampleThreads(boolean sampleThreads) throws IllegalStateException {
        SubstrateJVM.get().setSampleThreads(sampleThreads);
    }

    /** See {@link JVM#setCompressedIntegers}. */
    @Substitute
    public void setCompressedIntegers(boolean compressed) throws IllegalStateException {
        SubstrateJVM.get().setCompressedIntegers(compressed);
    }

    /** See {@link JVM#setStackDepth}. */
    @Substitute
    public void setStackDepth(int depth) throws IllegalArgumentException, IllegalStateException {
        SubstrateJVM.get().setStackDepth(depth);
    }

    /** See {@link JVM#setStackTraceEnabled}. */
    @Substitute
    public void setStackTraceEnabled(long eventTypeId, boolean enabled) {
        SubstrateJVM.get().setStackTraceEnabled(eventTypeId, enabled);
    }

    /** See {@link JVM#setThreadBufferSize}. */
    @Substitute
    public void setThreadBufferSize(long size) throws IllegalArgumentException, IllegalStateException {
        SubstrateJVM.get().setThreadBufferSize(size);
    }

    /** See {@link JVM#setThreshold}. */
    @Substitute
    public boolean setThreshold(long eventTypeId, long ticks) {
        return SubstrateJVM.get().setThreshold(eventTypeId, ticks);
    }

    /** See {@link JVM#storeMetadataDescriptor}. */
    @Substitute
    public void storeMetadataDescriptor(byte[] bytes) {
        SubstrateJVM.get().storeMetadataDescriptor(bytes);
    }

    /** See {@link JVM#getAllowedToDoEventRetransforms}. */
    @Substitute
    public boolean getAllowedToDoEventRetransforms() {
        return false;
    }

    /** See {@link JVM#createJFR}. */
    @Substitute
    private boolean createJFR(boolean simulateFailure) throws IllegalStateException {
        return SubstrateJVM.get().createJFR(simulateFailure);
    }

    /** See {@link JVM#destroyJFR}. */
    @Substitute
    private boolean destroyJFR() {
        return SubstrateJVM.get().destroyJFR();
    }

    /** See {@link JVM#isAvailable}. */
    @Substitute
    public boolean isAvailable() {
        return true;
    }

    /** See {@link JVM#getTimeConversionFactor}. */
    @Substitute
    public double getTimeConversionFactor() {
        return 1;
    }

    /** See {@link SubstrateJVM#getChunkStartNanos}. */
    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    public long getChunkStartNanos() {
        return SubstrateJVM.get().getChunkStartNanos();
    }

    @Substitute
    @TargetElement(onlyWith = {JDK17OrLater.class, JDK17OrEarlier.class})
    public boolean setHandler(Class<? extends jdk.internal.event.Event> eventClass, Target_jdk_jfr_internal_handlers_EventHandler handler) {
        // eventHandler fields should all be set at compile time so this method
        // should never be reached at runtime
        throw VMError.shouldNotReachHere("eventHandler does not exist for: " + eventClass);
    }

    /** See {@link SubstrateJVM#getHandler}. */
    @Substitute
    @TargetElement(onlyWith = {JDK17OrLater.class, JDK17OrEarlier.class})
    public Object getHandler(Class<? extends jdk.internal.event.Event> eventClass) {
        return SubstrateJVM.getHandler(eventClass);
    }

    /** See {@link JVM#getTypeId}. */
    @Substitute
    public long getTypeId(Class<?> clazz) {
        return JfrTraceId.getTraceId(clazz);
    }

    /** See {@link JVM#getEventWriter}. */
    @Substitute
    public static Object getEventWriter() {
        return SubstrateJVM.get().getEventWriter();
    }

    /** See {@link JVM#newEventWriter}. */
    @Substitute
    public static Target_jdk_jfr_internal_EventWriter newEventWriter() {
        return SubstrateJVM.get().newEventWriter();
    }

    /** See {@link JVM#flush}. */
    @Substitute
    public static boolean flush(Target_jdk_jfr_internal_EventWriter writer, int uncommittedSize, int requestedSize) {
        return SubstrateJVM.get().flush(writer, uncommittedSize, requestedSize);
    }

    /** See {@link JVM#setRepositoryLocation}. */
    @Substitute
    public void setRepositoryLocation(String dirText) {
        SubstrateJVM.get().setRepositoryLocation(dirText);
    }

    /** See {@code JVM#setDumpPath(String)}. */
    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class)
    public void setDumpPath(String dumpPathText) {
        SubstrateJVM.get().setDumpPath(dumpPathText);
    }

    /** See {@code JVM#getDumpPath()}. */
    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class)
    public String getDumpPath() {
        return SubstrateJVM.get().getDumpPath();
    }

    /** See {@link JVM#abort}. */
    @Substitute
    public void abort(String errorMsg) {
        SubstrateJVM.get().abort(errorMsg);
    }

    /** See {@link JVM#uncaughtException}. */
    @Substitute
    public void uncaughtException(Thread thread, Throwable t) {
        // Would be used to determine the emergency dump filename if an exception happens during
        // shutdown.
    }

    /** See {@link JVM#setCutoff}. */
    @Substitute
    public boolean setCutoff(long eventTypeId, long cutoffTicks) {
        return SubstrateJVM.get().setCutoff(eventTypeId, cutoffTicks);
    }

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    public boolean setThrottle(long eventTypeId, long eventSampleSize, long periodMs) {
        // Not supported but this method is called during JFR startup, so we can't throw an error.
        return true;
    }

    /** See {@link JVM#emitOldObjectSamples}. */
    @Substitute
    @TargetElement(onlyWith = JDK11OrEarlier.class) //
    public void emitOldObjectSamples(long cutoff, boolean emitAll) {
        // Not supported but this method is called during JFR shutdown, so we can't throw an error.
    }

    /** See {@link JVM#emitOldObjectSamples}. */
    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class) //
    public void emitOldObjectSamples(long cutoff, boolean emitAll, boolean skipBFS) {
        // Not supported but this method is called during JFR shutdown, so we can't throw an error.
    }

    /** See {@link JVM#shouldRotateDisk}. */
    @Substitute
    public boolean shouldRotateDisk() {
        return SubstrateJVM.get().shouldRotateDisk();
    }

    /** See {@link JVM#flush}. */
    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class) //
    public void flush() {
        // Temporarily do nothing. This is used for JFR streaming.
    }

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class) //
    public void include(Thread thread) {
        // Temporarily do nothing. This is used for JFR streaming.
    }

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class) //
    public void exclude(Thread thread) {
        // Temporarily do nothing. This is used for JFR streaming.
    }

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class) //
    public boolean isExcluded(Thread thread) {
        // Temporarily do nothing. This is used for JFR streaming.
        return false;
    }

    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class) //
    public boolean isExcluded(Class<? extends jdk.internal.event.Event> eventClass) {
        // Temporarily always include.
        return false;
    }

    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class) //
    public boolean setConfiguration(Class<? extends jdk.internal.event.Event> eventClass, Target_jdk_jfr_internal_event_EventConfiguration configuration) {
        return SubstrateJVM.get().setConfiguration(eventClass, configuration);
    }

    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class) //
    public Object getConfiguration(Class<? extends jdk.internal.event.Event> eventClass) {
        return SubstrateJVM.get().getConfiguration(eventClass);
    }

    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class) //
    public boolean isInstrumented(Class<? extends jdk.internal.event.Event> eventClass) {
        // This should check for blessed commit methods in the event class [GR-41200]
        return true;
    }

    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class) //
    public boolean isContainerized() {
        return Containers.isContainerized();
    }

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class) //
    public void markChunkFinal() {
        // Temporarily do nothing. This is used for JFR streaming.
    }
}
