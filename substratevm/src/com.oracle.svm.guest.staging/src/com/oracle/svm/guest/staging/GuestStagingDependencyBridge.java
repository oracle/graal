/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.guest.staging;

import java.io.PrintStream;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.RuntimeStateTrimConfig;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.guest.staging.option.NotifyGCRuntimeOptionKey;
import com.oracle.svm.shared.Uninterruptible;

/**
 * Temporary bridge for cutting builder-to-guest migration dependencies.
 * <p>
 * Add methods here only when moving code to guest/staging would otherwise pull in a larger
 * builder-side dependency cluster. Each method must have a concrete deletion condition: remove the
 * method when the delegated implementation moves to guest/staging.
 */
public interface GuestStagingDependencyBridge {

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static GuestStagingDependencyBridge singleton() {
        return ImageSingletons.lookup(GuestStagingDependencyBridge.class);
    }

    /**
     * Delegates to {@code com.oracle.svm.core.SubstrateOptions.useEpsilonGC()}.
     * <p>
     * Remove this method when GC selection becomes guest-owned.
     */
    boolean useEpsilonGC();

    /**
     * Delegates to {@code com.oracle.svm.core.SubstrateOptions.useSerialGC()}.
     * <p>
     * Remove this method when GC selection becomes guest-owned.
     */
    boolean useSerialGC();

    /**
     * Delegates to {@code com.oracle.svm.core.SubstrateOptions.useG1GC()}.
     * <p>
     * Remove this method when GC selection becomes guest-owned.
     */
    boolean useG1GC();

    /**
     * Delegates to
     * {@code com.oracle.svm.core.heap.ReferenceAccess.singleton().getMaxAddressSpaceSize()}.
     * <p>
     * Remove this method when reference layout information becomes guest-owned.
     */
    UnsignedWord getMaxHeapAddressSpaceSize();

    /**
     * Delegates to
     * {@code com.oracle.svm.core.heap.ReferenceAccess.singleton().getCompressionShift()}.
     * <p>
     * Remove this method when reference layout information becomes guest-owned.
     */
    int getHeapCompressionShift();

    /**
     * Delegates to {@code com.oracle.svm.core.heap.Heap.getHeap().optionValueChanged(key)}.
     * <p>
     * Remove this method when GC option change notification moves to guest/staging.
     */
    void heapOptionValueChanged(NotifyGCRuntimeOptionKey<?> key);

    /**
     * Delegates to {@code com.oracle.svm.core.Isolates.isCurrentFirst()}.
     * <p>
     * Remove this method when {@code com.oracle.svm.core.Isolates} moves to guest/staging or when
     * guest/staging gets its own guest-owned first-isolate query.
     */
    boolean isCurrentFirstIsolate();

    /**
     * Runs Java-level shutdown hooks through
     * {@code com.oracle.svm.core.jdk.Target_java_lang_Shutdown.shutdown()}.
     * <p>
     * Remove this method when substitution processing supports guest-owned substitution classes
     * (GR-71844).
     */
    void runJavaShutdownHooks();

    /**
     * Runs the delayed LogManager shutdown hook through
     * {@code com.oracle.svm.core.jdk.Util_java_lang_Shutdown.runLogManagerShutdownHook()}.
     * <p>
     * Remove this method when the shutdown substitution and its helper can move to guest/staging
     * (GR-71844).
     */
    void runLogManagerShutdownHook();

    /**
     * Returns the active low-level log.
     * <p>
     * Remove this method when the low-level logging implementation moves to guest/staging
     * (GR-77530).
     */
    Log log();

    /**
     * Returns the active low-level log as a {@link PrintStream}.
     * <p>
     * Remove this method when the low-level logging implementation moves to guest/staging
     * (GR-77530).
     */
    PrintStream logStream();

    /**
     * Returns the disabled low-level log.
     * <p>
     * Remove this method when the low-level logging implementation moves to guest/staging
     * (GR-77530).
     */
    Log noopLog();

    /**
     * Configures the low-level log file and registers its teardown hook.
     * <p>
     * Remove this method when the low-level logging implementation moves to guest/staging
     * (GR-77530).
     */
    void configureLogFile(String optionPrefix, String logFile);

    /// Parses and applies one unified logging option.
    ///
    /// @param arg a value that starts with `-Xlog`
    boolean parseXLogOption(String arg);

    /// Initializes unified logging before command-line properties and runtime options are parsed.
    void initializeLogging();

    /// Releases logging resources when isolate startup does not complete.
    void abortLoggingInitialization();

    /**
     * Returns whether runtime arguments must be parsed in the current isolate.
     * <p>
     * Remove this method when isolate startup policy moves to guest/staging.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean shouldParseRuntimeOptions(boolean isCompilationIsolate);

    /**
     * Returns whether libc support is available in the image.
     * <p>
     * Remove this method when libc support moves to guest/staging (GR-79128).
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean isLibCSupported();

    /**
     * Returns the current libc errno value.
     * <p>
     * Remove this method when libc support moves to guest/staging (GR-79128).
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    int libcErrno();

    /**
     * Sets the libc errno value.
     * <p>
     * Remove this method when libc support moves to guest/staging (GR-79128).
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void libcSetErrno(int value);

    /**
     * Checks whether a character is a decimal digit according to libc.
     * <p>
     * Remove this method when libc support moves to guest/staging (GR-79128).
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    int libcIsDigit(int value);

    /**
     * Returns the length of a libc string.
     * <p>
     * Remove this method when libc support moves to guest/staging (GR-79128).
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    UnsignedWord libcStrlen(CCharPointer string);

    /**
     * Parses an unsigned long integer using libc.
     * <p>
     * Remove this method when libc support moves to guest/staging (GR-79128).
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    UnsignedWord libcStrtoull(CCharPointer string, CCharPointerPointer endPtr, int base);

    /// Returns whether strict runtime Java option handling is enabled.
    ///
    /// Remove this method when `StrictRuntimeJavaOptions` moves to guest/staging.
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean strictRuntimeJavaOptions();

    /**
     * Initializes a system property parsed from a runtime Java option.
     * <p>
     * Remove this method when system-property initialization moves to guest/staging.
     */
    void initializeSystemProperty(String key, String value);

    /**
     * Enables the JDK runtime state selected by {@code --enable-preview}.
     * <p>
     * Remove this method when substitutions can move to guest/staging (GR-71844).
     */
    void enablePreviewFeatures();

    /**
     * Returns whether runtime class loading is supported.
     * <p>
     * Remove this method when runtime class loading (aka Crema) options move to guest/staging.
     */
    boolean isRuntimeClassLoadingSupported();

    /**
     * Delegates to {@code com.oracle.svm.core.SubstrateOptions.useRistretto()}.
     * <p>
     * Remove this method when Ristretto options move to guest/staging.
     */
    boolean useRistretto();

    /**
     * Enables tracing of class loading. Enabled through {@code --verbose} or {@code --verbose:class}.
     * <p>
     * Remove this method when runtime class loading (aka Crema) options move to guest/staging.
     */
    void enableTraceClassLoading();

    /// Applies a runtime assertion directive to a class, package, or the application default.
    ///
    /// Remove this method when runtime assertion support moves to guest/staging.
    /// @param classOrPackage target class or package to which directive applies.
    /// A value of `""` applies the directive to all classes.
    void updateRuntimeAssertionStatus(String classOrPackage, boolean enable);

    /// Updates the runtime assertion default for bootstrap-loaded classes.
    ///
    /// Remove this method when runtime assertion support moves to guest/staging.
    void updateRuntimeSystemAssertionStatus(boolean enable);

    /// Updates the bytecode verification mode selected by a Java VM option.
    ///
    /// The mode is represented as a string here because guest/staging must not depend on the
    /// runtime class-loading implementation.
    void setVerifyMode(String mode);

    /**
     * This method is called at the end of runtime options parsing.
     * <p>
     * Remove this method when runtime option parsing fully moves to guest/staging.
     */
    void endOfParsing();

    /**
     * Delegates to
     * {@code com.oracle.svm.core.RuntimeStateTrimSupport.trimRuntimeState(config)}.
     * <p>
     * Remove this method when {@code com.oracle.svm.core.RuntimeStateTrimSupport} moves
     * to guest/staging.
     */
    void trimRuntimeState(RuntimeStateTrimConfig config);
}
