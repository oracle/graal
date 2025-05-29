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

import com.oracle.svm.core.BuildPhaseProvider.AfterCompilation;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.webimage.wasm.stack.WebImageWasmFrameMap;

/**
 * Stores stack frame metadata for a method.
 */
public class FrameData {

    /**
     * Total size of the stack frame in bytes.
     *
     * @see WebImageWasmFrameMap#totalFrameSize()
     */
    @UnknownPrimitiveField(availability = AfterCompilation.class) //
    private final int frameSize;

    /**
     * Name of the method this stack frame is for.
     * <p>
     * When this is not accessed, the String data is optimized away and does not appear in the final
     * image.
     */
    @UnknownObjectField(availability = AfterCompilation.class) private String methodName;

    public FrameData(int frameSize, String methodName) {
        this.frameSize = frameSize;
        this.methodName = methodName;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getFrameSize() {
        return frameSize;
    }

    /**
     * Only call this for debugging, can drastically increase the image size.
     */
    public String getMethodName() {
        return methodName;
    }
}
