/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.guest.staging.core.heap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.guest.staging.util.AbstractImageHeapList;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.DuplicatedInNativeCode;

/**
 * This class holds garbage collection causes that are common and therefore shared between different
 * garbage collector implementations.
 */
public class GCCause {

    @DuplicatedInNativeCode public static final GCCause JavaLangSystemGC = new GCCause("java.lang.System.gc()", 0, true);
    @DuplicatedInNativeCode public static final GCCause TestGCInDeoptimizer = new GCCause("Test GC in deoptimizer", 2, false);
    @DuplicatedInNativeCode public static final GCCause HintedGC = new GCCause("Hinted GC", 3, false);
    @DuplicatedInNativeCode public static final GCCause JvmtiForceGC = new GCCause("JvmtiEnv ForceGarbageCollection", 4, true);
    @DuplicatedInNativeCode public static final GCCause HeapDump = new GCCause("Heap Dump Initiated GC", 5, true);
    @DuplicatedInNativeCode public static final GCCause DiagnosticCommand = new GCCause("Diagnostic Command", 6, true);
    @DuplicatedInNativeCode public static final GCCause WhiteBoxTestYoungGC = new GCCause("WhiteBox Initiated Young GC", 7, false);
    @DuplicatedInNativeCode public static final GCCause WhiteBoxTestFullGC = new GCCause("WhiteBox Initiated Full GC", 8, true);
    @DuplicatedInNativeCode public static final GCCause RuntimeStateTrimYoungGC = new GCCause("Runtime-State Trim Young GC", 9, false);
    @DuplicatedInNativeCode public static final GCCause RuntimeStateTrimFullGC = new GCCause("Runtime-State Trim Full GC", 10, true);

    private final int id;
    private final String name;
    private final boolean completeCollection;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected GCCause(String name, int id, boolean completeCollection) {
        this.id = id;
        this.name = name;
        this.completeCollection = completeCollection;
    }

    public String getName() {
        return name;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getId() {
        return id;
    }

    /** Returns whether this cause triggers a complete collection. */
    public boolean collectsCompletely() {
        return completeCollection;
    }

    public static GCCause fromId(int causeId) {
        return getGCCauses().get(causeId);
    }

    public static AbstractImageHeapList<GCCause> getGCCauses() {
        return ImageSingletons.lookup(GCCauseSupport.class).gcCauses;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerGCCause(GCCause cause) {
        ImageSingletons.lookup(GCCauseSupport.class).installGCCause(cause);
    }
}
