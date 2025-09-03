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

package com.oracle.svm.hosted.webimage.wasmgc;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.webimage.platform.WebImageWasmGCPlatform;

// GR-60261 Support unmanaged memory
@AutomaticallyRegisteredImageSingleton(UnmanagedMemorySupport.class)
@Platforms(WebImageWasmGCPlatform.class)
public class WasmGCUnmanagedMemorySupport implements UnmanagedMemorySupport {
    @Override
    public <T extends PointerBase> T malloc(UnsignedWord size) {
        throw VMError.unimplemented("UnmanagedMemorySupport.malloc not supported");
    }

    @Override
    public <T extends PointerBase> T calloc(UnsignedWord size) {
        throw VMError.unimplemented("UnmanagedMemorySupport.calloc not supported");
    }

    @Override
    public <T extends PointerBase> T realloc(T ptr, UnsignedWord size) {
        throw VMError.shouldNotReachHere("UnmanagedMemorySupport.realloc not supported");
    }

    @Override
    public void free(PointerBase ptr) {
        throw VMError.shouldNotReachHere("UnmanagedMemorySupport.free not supported");
    }
}
