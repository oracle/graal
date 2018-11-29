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

import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;

public class HeapOptions {
    @Option(help = "Use a card remembered set heap for GC")//
    public static final HostedOptionKey<Boolean> UseCardRememberedSetHeap = new HostedOptionKey<>(true);

    @Option(help = "Print the shape of the heap before and after each collection, if +VerboseGC.")//
    public static final RuntimeOptionKey<Boolean> PrintHeapShape = new RuntimeOptionKey<>(false);

    @Option(help = "Print the time for each of the phases of each collection, if +VerboseGC.")//
    public static final RuntimeOptionKey<Boolean> PrintGCTimes = new RuntimeOptionKey<>(false);

    /** This produces a lot of output: be prepared to stream the output to a post-processor. */
    @Option(help = "Trace each object promotion.")//
    public static final HostedOptionKey<Boolean> TraceObjectPromotion = new HostedOptionKey<>(false);

    @Option(help = "Verify the heap before and after each collection.")//
    public static final HostedOptionKey<Boolean> VerifyHeap = new HostedOptionKey<>(false);

    @Option(help = "Verify the heap before and after each collection.")//
    public static final RuntimeOptionKey<Boolean> HeapVerificationFailureIsFatal = new RuntimeOptionKey<>(true);

    @Option(help = "Verify the heap before each collection.")//
    public static final HostedOptionKey<Boolean> VerifyHeapBeforeCollection = new HostedOptionKey<>(false);

    @Option(help = "Verify the heap after each collection.")//
    public static final HostedOptionKey<Boolean> VerifyHeapAfterCollection = new HostedOptionKey<>(false);

    @Option(help = "Trace heap verification.")//
    public static final HostedOptionKey<Boolean> TraceHeapVerification = new HostedOptionKey<>(false);

    @Option(help = "Verify the stack before each collection.")//
    public static final HostedOptionKey<Boolean> VerifyStackBeforeCollection = new HostedOptionKey<>(false);

    @Option(help = "Verify the stack after each collection.")//
    public static final HostedOptionKey<Boolean> VerifyStackAfterCollection = new HostedOptionKey<>(false);

    @Option(help = "Trace stack verification.")//
    public static final HostedOptionKey<Boolean> TraceStackVerification = new HostedOptionKey<>(false);
}
