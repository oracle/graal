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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.guest.staging.option.NotifyGCRuntimeOptionKey;

/**
 * Temporary bridge for cutting builder-to-guest migration dependencies.
 * <p>
 * Add methods here only when moving code to guest/staging would otherwise pull in a larger
 * builder-side dependency cluster. Each method must have a concrete deletion condition: remove the
 * method when the delegated implementation moves to guest/staging.
 */
public interface GuestStagingDependencyBridge {

    static GuestStagingDependencyBridge singleton() {
        return ImageSingletons.lookup(GuestStagingDependencyBridge.class);
    }

    /**
     * Delegates to
     * {@code com.oracle.svm.core.IsolateArgumentParser.singleton().verifyOptionValues()}.
     * <p>
     * Remove this method when {@code com.oracle.svm.core.IsolateArgumentParser} moves to
     * guest/staging.
     */
    void verifyIsolateArgumentOptionValues();

    /**
     * Delegates to {@code com.oracle.svm.core.heap.HeapSizeVerifier.verifyHeapOptions()}.
     * <p>
     * Remove this method when {@code com.oracle.svm.core.heap.HeapSizeVerifier} moves to
     * guest/staging.
     */
    void verifyHeapOptions();

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
     * Verifies and records an updated minimum heap size.
     * <p>
     * Remove this method when heap-size verification and isolate-argument storage move to
     * guest/staging.
     */
    void minHeapSizeOptionValueChanged(long newValue);

    /**
     * Verifies and records an updated maximum heap size.
     * <p>
     * Remove this method when heap-size verification and isolate-argument storage move to
     * guest/staging.
     */
    void maxHeapSizeOptionValueChanged(long newValue);

    /**
     * Verifies and records an updated maximum young-generation size.
     * <p>
     * Remove this method when heap-size verification and isolate-argument storage move to
     * guest/staging.
     */
    void maxNewSizeOptionValueChanged(long newValue);

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
}
