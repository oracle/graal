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

package com.oracle.svm.hosted.webimage.wasm.gc;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.hosted.webimage.wasm.gc.WasmHeapVerifier.ObjectReferenceVerifier;
import com.oracle.svm.hosted.webimage.wasm.stack.WebImageWasmStackFrameVisitor;
import com.oracle.svm.hosted.webimage.wasm.stack.WebImageWasmStackWalker;
import com.oracle.svm.webimage.wasm.code.WasmSimpleCodeInfoQueryResult;

/**
 * Verifies that all references on the stack are valid.
 */
public class WasmStackVerifier {

    private static final StackFrameVerificationVisitor STACK_FRAME_VISITOR = new StackFrameVerificationVisitor();

    @NeverInline("Starts stack walk in caller frame")
    public static boolean verify() {
        STACK_FRAME_VISITOR.initialize();
        WebImageWasmStackWalker.walkCurrentThread(KnownIntrinsics.readCallerStackPointer(), STACK_FRAME_VISITOR);
        return STACK_FRAME_VISITOR.getResult();
    }

    private static class StackFrameVerificationVisitor extends WebImageWasmStackFrameVisitor {
        private final ObjectReferenceVerifier verifyFrameReferencesVisitor;

        @Platforms(Platform.HOSTED_ONLY.class)
        StackFrameVerificationVisitor() {
            verifyFrameReferencesVisitor = new ObjectReferenceVerifier();
        }

        public void initialize() {
            verifyFrameReferencesVisitor.initialize();
        }

        public boolean getResult() {
            return verifyFrameReferencesVisitor.result;
        }

        /**
         * Pass all references on the stack frame to {@link ObjectReferenceVerifier}.
         */
        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while verifying the stack.")
        public boolean visitFrame(Pointer currentSP, CodePointer currentIP) {
            WasmSimpleCodeInfoQueryResult queryResult = StackValue.get(WasmSimpleCodeInfoQueryResult.class);
            WebImageWasmStackWalker.getCodeInfo(currentIP, queryResult);

            for (int offset : queryResult.getOffsets()) {
                verifyFrameReferencesVisitor.visitObjectReferences(currentSP.add(offset), false, FrameAccess.uncompressedReferenceSize(), null, 1);
            }

            return true;
        }
    }
}
