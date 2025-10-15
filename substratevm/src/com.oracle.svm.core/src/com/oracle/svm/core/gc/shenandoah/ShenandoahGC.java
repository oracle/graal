/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.gc.shenandoah;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.gc.shared.NativeGCOptions;
import com.oracle.svm.core.gc.shenandoah.nativelib.ShenandoahLibrary;
import com.oracle.svm.core.heap.GC;
import com.oracle.svm.core.heap.GCCause;

public class ShenandoahGC implements GC {
    @Platforms(Platform.HOSTED_ONLY.class)
    public ShenandoahGC() {
    }

    @Override
    public void collect(GCCause cause) {
        ShenandoahLibrary.collect(cause.getId());
    }

    @Override
    public void collectCompletely(GCCause cause) {
        ShenandoahLibrary.collect(cause.getId());
    }

    @Override
    public void collectionHint(boolean fullGC) {
        /* Ignore collection hints. */
    }

    @Override
    public String getName() {
        return "Shenandoah GC";
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public String getDefaultMaxHeapSize() {
        return String.format("%s%% of RAM", NativeGCOptions.MaxRAMPercentage.getValue());
    }
}
