/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import static com.oracle.svm.core.option.RuntimeOptionKey.RuntimeOptionKeyFlag.Immutable;
import static com.oracle.svm.core.option.RuntimeOptionKey.RuntimeOptionKeyFlag.IsolateCreationOnly;
import static com.oracle.svm.core.option.RuntimeOptionKey.RuntimeOptionKeyFlag.RegisterForIsolateArgumentParser;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.core.heap.HeapSizeVerifier;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.NotifyGCRuntimeOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.util.DuplicatedInNativeCode;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.word.Word;

/**
 * Garbage collection-specific options that are supported by all garbage collectors. Some of these
 * options don't have any effect on the epsilon GC because it does not collect any garbage.
 */
@DuplicatedInNativeCode
public class SubstrateGCOptions {
    private static final int K = 1024;

    @Option(help = "The minimum heap size at run-time, in bytes.", type = OptionType.User)//
    public static final RuntimeOptionKey<Long> MinHeapSize = new NotifyGCRuntimeOptionKey<>(0L, RegisterForIsolateArgumentParser) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Long oldValue, Long newValue) {
            if (!SubstrateUtil.HOSTED) {
                HeapSizeVerifier.verifyMinHeapSizeAgainstMaxAddressSpaceSize(Word.unsigned(newValue));
            }
            super.onValueUpdate(values, oldValue, newValue);
        }
    };

    @Option(help = "The maximum heap size at run-time, in bytes.", type = OptionType.User)//
    public static final RuntimeOptionKey<Long> MaxHeapSize = new NotifyGCRuntimeOptionKey<>(0L, RegisterForIsolateArgumentParser) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Long oldValue, Long newValue) {
            if (!SubstrateUtil.HOSTED) {
                HeapSizeVerifier.verifyMaxHeapSizeAgainstMaxAddressSpaceSize(Word.unsigned(newValue));
            }
            super.onValueUpdate(values, oldValue, newValue);
        }
    };

    @Option(help = "The maximum size of the young generation at run-time, in bytes", type = OptionType.User)//
    public static final RuntimeOptionKey<Long> MaxNewSize = new NotifyGCRuntimeOptionKey<>(0L, RegisterForIsolateArgumentParser) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Long oldValue, Long newValue) {
            if (!SubstrateUtil.HOSTED) {
                HeapSizeVerifier.verifyMaxNewSizeAgainstMaxAddressSpaceSize(Word.unsigned(newValue));
            }
            super.onValueUpdate(values, oldValue, newValue);
        }
    };

    @Option(help = "The number of bytes that should be reserved for the heap address space.", type = OptionType.Expert)//
    public static final RuntimeOptionKey<Long> ReservedAddressSpaceSize = new RuntimeOptionKey<>(0L, RegisterForIsolateArgumentParser);

    @Option(help = "Exit on the first occurrence of an out-of-memory error that is thrown because the Java heap is out of memory.", type = OptionType.Expert)//
    public static final RuntimeOptionKey<Boolean> ExitOnOutOfMemoryError = new RuntimeOptionKey<>(false, Immutable);

    @Option(help = "Report a fatal error on the first occurrence of an out-of-memory error that is thrown because the Java heap is out of memory.", type = OptionType.Expert)//
    public static final RuntimeOptionKey<Boolean> ReportFatalErrorOnOutOfMemoryError = new RuntimeOptionKey<>(false);

    @Option(help = "Ignore calls to System.gc().", type = OptionType.Expert)//
    public static final RuntimeOptionKey<Boolean> DisableExplicitGC = new NotifyGCRuntimeOptionKey<>(false, Immutable);

    @Option(help = "Print summary GC information after each collection.", type = OptionType.Expert)//
    public static final RuntimeOptionKey<Boolean> PrintGC = new NotifyGCRuntimeOptionKey<>(false);

    @Option(help = "Print more information about the heap before and after each collection.", type = OptionType.Expert)//
    public static final RuntimeOptionKey<Boolean> VerboseGC = new NotifyGCRuntimeOptionKey<>(false);

    @Option(help = "Verify the heap before and after each collection.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> VerifyHeap = new HostedOptionKey<>(false);

    @Option(help = "Determines if references from runtime-compiled code to Java heap objects should be treated as strong or weak.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> TreatRuntimeCodeInfoReferencesAsWeak = new HostedOptionKey<>(true);

    @DuplicatedInNativeCode
    public static class TlabOptions {
        @Option(help = "Use thread-local object allocation.", type = OptionType.Expert)//
        public static final HostedOptionKey<Boolean> UseTLAB = new HostedOptionKey<>(true);

        @Option(help = "Dynamically resize TLAB size for threads.", type = OptionType.Expert)//
        public static final RuntimeOptionKey<Boolean> ResizeTLAB = new RuntimeOptionKey<>(true, IsolateCreationOnly);

        @Option(help = "Minimum allowed TLAB size (in bytes).", type = OptionType.Expert)//
        public static final RuntimeOptionKey<Long> MinTLABSize = new RuntimeOptionKey<>(2L * K, RegisterForIsolateArgumentParser);

        @Option(help = "Starting TLAB size (in bytes); zero means set ergonomically.", type = OptionType.Expert)//
        public static final RuntimeOptionKey<Long> TLABSize = new RuntimeOptionKey<>(0L, RegisterForIsolateArgumentParser);

    }

}
