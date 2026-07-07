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
package com.oracle.svm.core.jdk;

import java.io.PrintStream;

import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.IsolateArgumentParser;
import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.log.CoreLogSupport;
import com.oracle.svm.guest.staging.GuestStagingDependencyBridge;
import com.oracle.svm.guest.staging.HeapSizeVerifier;
import com.oracle.svm.guest.staging.SubstrateGCOptions;
import com.oracle.svm.guest.staging.option.NotifyGCRuntimeOptionKey;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.Duplicable;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

/**
 * Provides core implementations for runtime services that guest-staging code cannot yet access
 * directly.
 */
@AutomaticallyRegisteredImageSingleton(GuestStagingDependencyBridge.class)
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Duplicable.class)
final class GuestStagingDependencyBridgeImpl implements GuestStagingDependencyBridge {

    @Override
    public void verifyIsolateArgumentOptionValues() {
        IsolateArgumentParser.singleton().verifyOptionValues();
    }

    @Override
    public boolean useEpsilonGC() {
        return SubstrateOptions.useEpsilonGC();
    }

    @Override
    public boolean useSerialGC() {
        return SubstrateOptions.useSerialGC();
    }

    @Override
    public UnsignedWord getMaxHeapAddressSpaceSize() {
        return ReferenceAccess.singleton().getMaxAddressSpaceSize();
    }

    @Override
    public int getHeapCompressionShift() {
        return ReferenceAccess.singleton().getCompressionShift();
    }

    @Override
    public void minHeapSizeOptionValueChanged(long newValue) {
        HeapSizeVerifier.verifyMinHeapSizeAgainstMaxAddressSpaceSize(Word.unsigned(newValue));
        int optionIndex = IsolateArgumentParser.getOptionIndex(SubstrateGCOptions.MinHeapSize);
        IsolateArgumentParser.singleton().setLongOptionValue(optionIndex, newValue);
    }

    @Override
    public void maxHeapSizeOptionValueChanged(long newValue) {
        HeapSizeVerifier.verifyMaxHeapSizeAgainstMaxAddressSpaceSize(Word.unsigned(newValue));
        int optionIndex = IsolateArgumentParser.getOptionIndex(SubstrateGCOptions.MaxHeapSize);
        IsolateArgumentParser.singleton().setLongOptionValue(optionIndex, newValue);
    }

    @Override
    public void maxNewSizeOptionValueChanged(long newValue) {
        HeapSizeVerifier.verifyMaxNewSizeAgainstMaxAddressSpaceSize(Word.unsigned(newValue));
        int optionIndex = IsolateArgumentParser.getOptionIndex(SubstrateGCOptions.MaxNewSize);
        IsolateArgumentParser.singleton().setLongOptionValue(optionIndex, newValue);
    }

    @Override
    public void heapOptionValueChanged(NotifyGCRuntimeOptionKey<?> key) {
        Heap.getHeap().optionValueChanged(key);
    }

    @Override
    public boolean isCurrentFirstIsolate() {
        return Isolates.isCurrentFirst();
    }

    @Override
    public void runJavaShutdownHooks() {
        Target_java_lang_Shutdown.shutdown();
    }

    @Override
    public void runLogManagerShutdownHook() {
        Util_java_lang_Shutdown.runLogManagerShutdownHook();
    }

    @Override
    public Log log() {
        return CoreLogSupport.log();
    }

    @Override
    public PrintStream logStream() {
        return CoreLogSupport.logStream();
    }

    @Override
    public Log noopLog() {
        return CoreLogSupport.noopLog();
    }
}
