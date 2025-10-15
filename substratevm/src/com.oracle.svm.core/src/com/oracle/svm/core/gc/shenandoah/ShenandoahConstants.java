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

import static com.oracle.svm.core.gc.shenandoah.ShenandoahOptions.ShenandoahRegionSize;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.gc.shared.NativeGCOptions;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.vm.ci.code.CodeUtil;

/**
 * Defines Shenandoah-specific constants that are used during code generation. If the value of a
 * constant depends on the debug-level of the linked Shenandoah library, the constant is defined as
 * an array of values (i.e., one value per debug-level).
 */
public class ShenandoahConstants {
    private static final int TLAB_TOP_OFFSET = 104;
    private static final int TLAB_END_OFFSET = 120;
    private static final byte DIRTY_CARD_VALUE = 0;
    private static final int[] JAVA_THREAD_SIZE = {280, 312, 312};

    @Fold
    public static int tlabTopOffset() {
        return TLAB_TOP_OFFSET;
    }

    @Fold
    public static int tlabEndOffset() {
        return TLAB_END_OFFSET;
    }

    @Fold
    public static byte dirtyCardValue() {
        return DIRTY_CARD_VALUE;
    }

    @Fold
    public static int cardTableShift() {
        return CodeUtil.log2(NativeGCOptions.GCCardSizeInBytes.getValue());
    }

    @Fold
    public static int cardSize() {
        return NativeGCOptions.GCCardSizeInBytes.getValue();
    }

    @Fold
    public static int javaThreadSize() {
        return JAVA_THREAD_SIZE[debugLevelIndex()];
    }

    @Fold
    public static int logOfHeapRegionGrainBytes() {
        return CodeUtil.log2(ShenandoahRegionSize.getValue());
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static int debugLevelIndex() {
        return ShenandoahOptions.getDebugLevel().getIndex();
    }
}
