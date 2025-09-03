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

package com.oracle.svm.hosted.webimage.wasm;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.hosted.webimage.wasm.gc.WasmAllocation;
import com.oracle.svm.webimage.platform.WebImageWasmLMPlatform;

@AutomaticallyRegisteredImageSingleton(UnmanagedMemorySupport.class)
@Platforms(WebImageWasmLMPlatform.class)
public class WasmLMUnmanagedMemorySupport implements UnmanagedMemorySupport {
    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @SuppressWarnings("unchecked")
    public <T extends PointerBase> T malloc(UnsignedWord size) {
        return (T) WasmAllocation.doMalloc(size);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public <T extends PointerBase> T calloc(UnsignedWord size) {
        T ptr = malloc(size);
        if (ptr.isNonNull()) {
            UnmanagedMemoryUtil.fill((Pointer) ptr, size, (byte) 0);
        }
        return ptr;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @SuppressWarnings("unchecked")
    public <T extends PointerBase> T realloc(T ptr, UnsignedWord size) {
        return (T) WasmAllocation.doRealloc((Pointer) ptr, size);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void free(PointerBase ptr) {
        WasmAllocation.doFree((Pointer) ptr);
    }
}
