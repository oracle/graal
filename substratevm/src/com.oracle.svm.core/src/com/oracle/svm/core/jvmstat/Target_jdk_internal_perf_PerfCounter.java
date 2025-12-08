/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmstat;

import java.nio.LongBuffer;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;

/**
 * {@code PerfCounter} objects created at build time and written in the image heap reference an
 * invalid buffer via the {@code PerfCounter.lb} field. Accessing this buffer would result in a
 * segfault, so we substitute all methods that may access this buffer. Moreover, we delete the
 * buffer field since it should never be reachable and make the factory methods that would
 * initialize it throw an {@link VMError#unsupportedFeature(String)} at run time.
 */
@TargetClass(className = "jdk.internal.perf.PerfCounter")
final class Target_jdk_internal_perf_PerfCounter {
    @Delete //
    private LongBuffer lb;

    @Substitute  //
    public static Target_jdk_internal_perf_PerfCounter newPerfCounter(@SuppressWarnings("unused") String name) {
        throw VMError.unsupportedFeature("Creating a new jdk.internal.perf.PerfCounter at run time is currently not supported.");
    }

    @Substitute  //
    public static Target_jdk_internal_perf_PerfCounter newConstantPerfCounter(@SuppressWarnings("unused") String name) {
        throw VMError.unsupportedFeature("Creating a new jdk.internal.perf.PerfCounter at run time is currently not supported.");
    }

    @Substitute
    @SuppressWarnings("static-method")
    public long get() {
        return 0;
    }

    @Substitute
    public void set(@SuppressWarnings("unused") long var1) {
    }

    @Substitute
    public void add(@SuppressWarnings("unused") long var1) {
    }
}
