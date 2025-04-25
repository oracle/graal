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

package com.oracle.svm.webimage.substitute.system;

import java.nio.ByteBuffer;

import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.webimage.platform.WebImageWasmGCPlatform;

@TargetClass(ByteBuffer.class)
public final class Target_java_nio_ByteBuffer_Web {

    /**
     * {@link ByteBuffer#allocateDirect(int)} usually creates a bytebuffer that operates on off-heap
     * memory, this is not supported in the WasmGC backend yet, and we simply create a regular
     * array-backed instance.
     * <p>
     * The API for {@link ByteBuffer#allocateDirect(int)} allows for this. It's method contract is
     * the same as {@link ByteBuffer#allocate(int)}, except that it is not defined whether there is
     * a backing array.
     * <p>
     * TODO GR-60261 Once off heap memory is supported in WasmGC, allow for the use of direct byte
     * buffers.
     */
    @Substitute
    @Platforms(WebImageWasmGCPlatform.class)
    public static ByteBuffer allocateDirect(int capacity) {
        return ByteBuffer.allocate(capacity);
    }
}
