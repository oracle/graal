/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.nodes.base;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;

public final class LLVMFrameNullerUtil {
    private LLVMFrameNullerUtil() {
        // no instances
    }

    public static void nullFrameSlot(VirtualFrame frame, FrameSlot frameSlot) {
        CompilerAsserts.partialEvaluationConstant(frameSlot);
        if (CompilerDirectives.inInterpreter()) {
            // Nulling frame slots is only necessary in compiled code (otherwise, we would compute
            // values that are only used in framestates). This code must NOT be moved to a separate
            // method as it would cause endless deopts (the method or classes that are used within
            // the method might be unresolved because they were never executed). For the same
            // reason, we also must NOT use a switch statement.
            return;
        }
        FrameSlotKind kind = frame.getFrameDescriptor().getFrameSlotKind(frameSlot);
        if (kind == FrameSlotKind.Object) {
            frame.setObject(frameSlot, null);
        } else if (kind == FrameSlotKind.Boolean) {
            frame.setBoolean(frameSlot, false);
        } else if (kind == FrameSlotKind.Byte) {
            frame.setByte(frameSlot, (byte) 0);
        } else if (kind == FrameSlotKind.Int) {
            frame.setInt(frameSlot, 0);
        } else if (kind == FrameSlotKind.Long) {
            frame.setLong(frameSlot, 0L);
        } else if (kind == FrameSlotKind.Float) {
            frame.setFloat(frameSlot, 0f);
        } else if (kind == FrameSlotKind.Double) {
            frame.setDouble(frameSlot, 0d);
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new UnsupportedOperationException("unexpected frameslot kind");
        }
    }
}
