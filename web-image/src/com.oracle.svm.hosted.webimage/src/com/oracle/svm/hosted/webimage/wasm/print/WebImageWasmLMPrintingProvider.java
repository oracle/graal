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

package com.oracle.svm.hosted.webimage.wasm.print;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CShortPointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.webimage.wasm.nodes.WasmPrintNode;
import com.oracle.svm.webimage.functionintrinsics.JSCallNode;
import com.oracle.svm.webimage.platform.WebImageWasmLMPlatform;
import com.oracle.svm.webimage.print.WebImagePrintingProvider;

import jdk.graal.compiler.word.Word;

/**
 * Printing functionality for the Wasm backend.
 * <p>
 * Printing is done by passing a raw pointer to the start of the char array to a dedicated
 * {@link WasmPrintNode}
 */
@AutomaticallyRegisteredImageSingleton(WebImagePrintingProvider.class)
@Platforms(WebImageWasmLMPlatform.class)
public class WebImageWasmLMPrintingProvider extends WebImagePrintingProvider {
    @Override
    @Uninterruptible(reason = "Handles untracked pointers.")
    public void print(Descriptor fd, char[] chars) {
        DynamicHub hub = KnownIntrinsics.readHub(chars);
        UnsignedWord baseOffset = LayoutEncoding.getArrayBaseOffset(hub.getLayoutEncoding());
        CShortPointer dataPtr = (CShortPointer) Word.objectToUntrackedPointer(chars).add(baseOffset);
        WasmPrintNode.print(fd.num, 2, dataPtr, Word.unsigned(chars.length));
    }

    @Override
    public void flush(Descriptor fd) {
        switch (fd) {
            case OUT -> JSCallNode.call(JSCallNode.PRINT_FLUSH_OUT);
            case ERR -> JSCallNode.call(JSCallNode.PRINT_FLUSH_ERR);
            default -> throw VMError.shouldNotReachHereAtRuntime();
        }
    }

    @Override
    public void close(Descriptor fd) {
        switch (fd) {
            case OUT -> JSCallNode.call(JSCallNode.PRINT_CLOSE_OUT);
            case ERR -> JSCallNode.call(JSCallNode.PRINT_CLOSE_ERR);
            default -> throw VMError.shouldNotReachHereAtRuntime();
        }
    }
}
