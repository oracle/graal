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

package com.oracle.svm.hosted.webimage.wasm.stack;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.hosted.webimage.wasm.gc.MemoryLayout;
import com.oracle.svm.webimage.wasm.code.FrameData;
import com.oracle.svm.webimage.wasm.code.WasmCodeInfoHolder;
import com.oracle.svm.webimage.wasm.code.WasmCodeInfoQueryResult;
import com.oracle.svm.webimage.wasm.code.WasmSimpleCodeInfoQueryResult;
import com.oracle.svm.webimage.wasm.stack.WebImageWasmFrameMap;

import jdk.graal.compiler.word.Word;

/**
 * Stack walker for the WebImage Wasm backend.
 *
 * @see WebImageWasmFrameMap
 * @see WasmCodeInfoQueryResult
 * @see FrameData
 */
public class WebImageWasmStackWalker {

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.", callerMustBe = true)
    private static void initialize(WebImageWasmStackWalk walk, Pointer startSP, CodePointer startIP) {
        assert startIP.isNonNull();

        walk.setSP(startSP);
        walk.setIP(startIP);
        /*
         * Make sure we don't exceed the stack base. This is needed because the stack frames
         * currently can't tell if they are an entry point.
         */
        walk.setEndSP(MemoryLayout.getStackBase());
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
    public static boolean walkCurrentThread(Pointer startSP, WebImageWasmStackFrameVisitor visitor) {
        CodePointer startIP = FrameAccess.singleton().readReturnAddress(CurrentIsolate.getCurrentThread(), startSP);
        WebImageWasmStackWalk walk = StackValue.get(WebImageWasmStackWalk.class);
        initialize(walk, startSP, startIP);
        return doWalk(walk, CurrentIsolate.getCurrentThread(), visitor);
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.", callerMustBe = true)
    private static boolean doWalk(WebImageWasmStackWalk walk, IsolateThread thread, WebImageWasmStackFrameVisitor visitor) {
        while (true) {
            if (!callVisitor(walk, visitor)) {
                return false;
            }
            if (!continueWalk(walk, thread)) {
                return true;
            }
        }
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
    public static boolean continueWalk(WebImageWasmStackWalk walk, IsolateThread thread) {
        if (walk.getSP().isNull() || walk.getIP().isNull()) {
            return false;
        }

        WasmSimpleCodeInfoQueryResult queryResult = StackValue.get(WasmSimpleCodeInfoQueryResult.class);
        getCodeInfo(walk.getIP(), queryResult);

        return continueWalk(walk, thread, queryResult);
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.", callerMustBe = true)
    public static boolean continueWalk(WebImageWasmStackWalk walk, IsolateThread thread, WasmSimpleCodeInfoQueryResult queryResult) {
        Pointer sp = walk.getSP();

        FrameData frameData = queryResult.getFrameData();
        long totalFrameSize = frameData.getFrameSize();

        /* Bump sp *up* over my frame. */
        sp = sp.add(Word.unsigned(totalFrameSize));
        /* Read the return address to my caller. */
        CodePointer ip = FrameAccess.singleton().readReturnAddress(thread, sp);

        walk.setSP(sp);
        walk.setIP(ip);

        return !walk.getEndSP().isNonNull() || !walk.getSP().aboveOrEqual(walk.getEndSP());
    }

    @Uninterruptible(reason = "Wraps the now safe call to the possibly interruptible visitor.", callerMustBe = true, calleeMustBe = false)
    @RestrictHeapAccess(reason = "Whitelisted because some StackFrameVisitor implementations can allocate.", access = RestrictHeapAccess.Access.UNRESTRICTED)
    private static boolean callVisitor(WebImageWasmStackWalk walk, WebImageWasmStackFrameVisitor visitor) {
        return visitor.visitFrame(walk.getSP(), walk.getIP());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void getCodeInfo(CodePointer ip, WasmSimpleCodeInfoQueryResult queryResult) {
        WasmCodeInfoQueryResult result = WasmCodeInfoHolder.getCodeInfo(ip);
        assert result != null;
        queryResult.setFrameData(result.getFrameData());
        queryResult.setOffsets(result.getReferenceOffsets());
    }
}
