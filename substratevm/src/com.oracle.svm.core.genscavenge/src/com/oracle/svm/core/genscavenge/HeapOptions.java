/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;

import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;

public final class HeapOptions {
    @Option(help = "Print the shape of the heap before and after each collection, if +VerboseGC.")//
    public static final RuntimeOptionKey<Boolean> PrintHeapShape = new RuntimeOptionKey<>(false);

    @Option(help = "Print summary GC information after application main method returns.")//
    public static final RuntimeOptionKey<Boolean> PrintGCSummary = new RuntimeOptionKey<>(false);

    @Option(help = "Print a time stamp at each collection, if +PrintGC or +VerboseGC.")//
    public static final RuntimeOptionKey<Boolean> PrintGCTimeStamps = new RuntimeOptionKey<>(false);

    @Option(help = "Print the time for each of the phases of each collection, if +VerboseGC.")//
    public static final RuntimeOptionKey<Boolean> PrintGCTimes = new RuntimeOptionKey<>(false);

    @Option(help = "Trace each object promotion (generates significant amounts of output).")//
    public static final HostedOptionKey<Boolean> TraceObjectPromotion = new HostedOptionKey<>(false);

    @Option(help = "Failed verification of the heap (if enabled) causes termination.")//
    public static final RuntimeOptionKey<Boolean> HeapVerificationFailureIsFatal = new RuntimeOptionKey<>(true);

    @Option(help = "Verify the heap before each collection.")//
    public static final HostedOptionKey<Boolean> VerifyHeapBeforeCollection = new HostedOptionKey<>(false);

    @Option(help = "Verify the heap after each collection.")//
    public static final HostedOptionKey<Boolean> VerifyHeapAfterCollection = new HostedOptionKey<>(false);

    @Option(help = "Trace heap verification.")//
    public static final HostedOptionKey<Boolean> TraceHeapVerification = new HostedOptionKey<>(false);

    @Option(help = "Verify stacks before each collection.")//
    public static final HostedOptionKey<Boolean> VerifyStackBeforeCollection = new HostedOptionKey<>(false);

    @Option(help = "Verify stacks after each collection.")//
    public static final HostedOptionKey<Boolean> VerifyStackAfterCollection = new HostedOptionKey<>(false);

    @Option(help = "Trace stack verification.")//
    public static final HostedOptionKey<Boolean> TraceStackVerification = new HostedOptionKey<>(false);

    @Option(help = "Verify dirty cards before each collection.") //
    public static final HostedOptionKey<Boolean> VerifyDirtyCardsBeforeCollection = new HostedOptionKey<>(false);

    @Option(help = "Verify dirty cards after each collection.") //
    public static final HostedOptionKey<Boolean> VerifyDirtyCardsAfterCollection = new HostedOptionKey<>(false);

    @Option(help = "Soft references: this number of milliseconds multiplied by the free heap memory in MByte is the time span " +
                    "for which a soft reference will keep its referent alive after its last access.", type = OptionType.Expert) //
    public static final HostedOptionKey<Integer> SoftRefLRUPolicyMSPerMB = new HostedOptionKey<>(1000);

    @Option(help = "Enables card marking for image heap objects, which arranges them in chunks. Automatically enabled when supported.", type = OptionType.Expert) //
    public static final HostedOptionKey<Boolean> ImageHeapCardMarking = new HostedOptionKey<>(null);

    private HeapOptions() {
    }
}
