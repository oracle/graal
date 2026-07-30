/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.g1;

import static com.oracle.svm.core.g1.G1Options.G1HeapRegionSize;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.gc.shared.NativeGCOptions;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.vm.ci.code.CodeUtil;

/**
 * Defines G1-specific constants that are used during code generation. If the value of a constant
 * depends on the debug-level of the linked G1 GC library, the constant is defined as an array of
 * values (i.e., one value per debug-level).
 */
public class G1Constants {
    private static final int TLAB_TOP_OFFSET = 104;
    private static final int TLAB_END_OFFSET = 120;
    private static final int SATB_QUEUE_MARKING_OFFSET = 24;
    private static final int SATB_QUEUE_BUFFER_OFFSET = 16;
    private static final int SATB_QUEUE_INDEX_OFFSET = 8;
    private static final int CARD_QUEUE_BUFFER_OFFSET = 40;
    private static final int CARD_QUEUE_INDEX_OFFSET = 32;
    private static final byte DIRTY_CARD_VALUE = 0;
    private static final byte YOUNG_CARD_VALUE = 2;
    private static final int[] JAVA_THREAD_SIZE = {280, 312, 312};
    private static final int AGE_BIT_COUNT = 4;

    @Fold
    public static int tlabTopOffset() {
        return TLAB_TOP_OFFSET;
    }

    @Fold
    public static int tlabEndOffset() {
        return TLAB_END_OFFSET;
    }

    @Fold
    public static int satbQueueMarkingActiveOffset() {
        return SATB_QUEUE_MARKING_OFFSET;
    }

    @Fold
    public static int satbQueueBufferOffset() {
        return SATB_QUEUE_BUFFER_OFFSET;
    }

    @Fold
    public static int satbQueueIndexOffset() {
        return SATB_QUEUE_INDEX_OFFSET;
    }

    @Fold
    public static int cardQueueBufferOffset() {
        return CARD_QUEUE_BUFFER_OFFSET;
    }

    @Fold
    public static int cardQueueIndexOffset() {
        return CARD_QUEUE_INDEX_OFFSET;
    }

    @Fold
    public static byte dirtyCardValue() {
        return DIRTY_CARD_VALUE;
    }

    @Fold
    public static byte youngCardValue() {
        return YOUNG_CARD_VALUE;
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
        return CodeUtil.log2(G1HeapRegionSize.getValue());
    }

    @Fold
    public static int ageBitCount() {
        return AGE_BIT_COUNT;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static int debugLevelIndex() {
        return G1Options.getDebugLevel().getIndex();
    }
}
