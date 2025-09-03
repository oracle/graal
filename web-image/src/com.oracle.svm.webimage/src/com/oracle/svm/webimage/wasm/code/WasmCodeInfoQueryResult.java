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

package com.oracle.svm.webimage.wasm.code;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.BuildPhaseProvider.AfterCompilation;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.UnknownObjectField;

/**
 * Holds information about an instruction pointer (IP).
 */
public class WasmCodeInfoQueryResult {
    /**
     * The {@link FrameData} for the method this IP is in.
     */
    @UnknownObjectField(availability = AfterCompilation.class) private FrameData frameData;

    /**
     * A set of offsets relative to the stack pointer that contain possibly live objects at this IP.
     */
    @UnknownObjectField(availability = AfterCompilation.class) private int[] referenceOffsets;

    @Platforms(Platform.HOSTED_ONLY.class)
    public WasmCodeInfoQueryResult(FrameData frameData, int[] referenceOffsets) {
        this.frameData = frameData;
        this.referenceOffsets = referenceOffsets;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public FrameData getFrameData() {
        return frameData;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int[] getReferenceOffsets() {
        return referenceOffsets;
    }
}
