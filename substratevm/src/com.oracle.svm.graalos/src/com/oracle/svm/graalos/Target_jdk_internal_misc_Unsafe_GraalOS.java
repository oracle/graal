/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graalos;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.shared.util.VMError;

@SuppressWarnings("all")
@TargetClass(className = "jdk.internal.misc.Unsafe", onlyWith = GraalOSEnabled.class)
final class Target_jdk_internal_misc_Unsafe_GraalOS {
    // Report cache flushing as unsupported by the platform.
    // The following substitutions are equivalent to forcing DATA_CACHE_LINE_FLUSH_SIZE = 0.
    // The three target methods are the only usages of DATA_CACHE_LINE_FLUSH_SIZE.

    @Substitute
    public int dataCacheLineFlushSize() {
        return 0;
    }

    @Substitute
    public long dataCacheLineAlignDown(long address) {
        return 0;
    }

    @Substitute
    public static boolean isWritebackEnabled() {
        return false;
    }

    // Thanks to the above substitutions, the following method should never be called.
    // However, the analysis still considers it reachable.
    // Let it fail at runtime (even if ReportUnsupportedElementsAtRuntime is false).

    @Substitute
    public void writebackMemory(long address, long length) {
        throw VMError.unsupportedFeature("Unsupported method jdk.internal.misc.Unsafe.writebackMemory(long, long) is reachable");
    }

    @Delete("Unsupported method jdk.internal.misc.Unsafe.writeback0(long) is reachable")
    private native void writeback0(long address);

    @Delete("Unsupported method jdk.internal.misc.Unsafe.writebackPreSync0() is reachable")
    private native void writebackPreSync0();

    @Delete("Unsupported method jdk.internal.misc.Unsafe.writebackPostSync0() is reachable")
    private native void writebackPostSync0();
}
