/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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
package com.oracle.svm.core.nmt;

import com.oracle.svm.core.Uninterruptible;

/** Categories for native memory tracking. */
public enum NmtCategory {
    /** Auxiliary images. */
    AuxiliaryImage("Auxiliary Image"),
    /** JIT compiler. */
    Compiler("Compiler"),
    /** JIT compiled code. */
    Code("Code"),
    /** Garbage collector. */
    GC("GC"),
    /** Heap dumping infrastructure. */
    HeapDump("Heap Dump"),
    /** Image heap (may include GC-specific data). */
    ImageHeap("Image Heap"),
    /** Collected Java heap (may include GC-specific data). */
    JavaHeap("Java Heap"),
    /** Java Flight Recorder. */
    JFR("JFR"),
    /** Java Native Interface. */
    JNI("JNI"),
    /** JVM stat / perf data. */
    JvmStat("jvmstat"),
    /** Java Virtual Machine Tool Interface. */
    JVMTI("JVMTI"),
    /** Metaspace objects. */
    Metaspace("Metaspace"),
    /** NMT itself. */
    NMT("Native Memory Tracking"),
    /** Profile-guided optimizations. */
    PGO("PGO"),
    /* Serviceability, e.g., attach API. */
    Serviceability("Serviceability)"),
    /** Threading. */
    Threading("Threading"),
    /** Memory allocated via Unsafe. */
    Unsafe("Unsafe"),

    /** Some other, VM internal reason - avoid if possible, better to add a new category. */
    Internal("Internal");

    private final String name;

    NmtCategory(String name) {
        this.name = name;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public String getName() {
        return name;
    }
}
