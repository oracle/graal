/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.c;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMCTypeIntrinsics extends LLVMIntrinsic {

    public static boolean isLowercaseLetter(int value) {
        return value >= 'a' && value <= 'z';
    }

    public static boolean isUppercaseLetter(int value) {
        return value >= 'A' && value <= 'Z';
    }

    public static boolean isAsciiCharacter(int value) {
        return value >= 0 && value < 128;
    }

    public abstract static class LLVMToUpper extends LLVMCTypeIntrinsics {

        @Specialization(guards = "isLowercaseLetter(value)")
        protected int fromLower(int value) {
            return value - 0x20;
        }

        @Specialization(guards = "isUppercaseLetter(value)")
        protected int fromUpper(int value) {
            return value;
        }

        @Specialization(guards = "isAsciiCharacter(value)")
        protected int fromCharacter(int value) {
            if (isLowercaseLetter(value)) {
                return fromLower(value);
            }
            return value;
        }

        @Specialization(replaces = {"fromLower", "fromUpper", "fromCharacter"})
        @TruffleBoundary
        protected int doIntrinsic(int value) {
            return Character.toUpperCase(value);
        }
    }

    public abstract static class LLVMTolower extends LLVMCTypeIntrinsics {

        @Specialization(guards = "isLowercaseLetter(value)")
        protected int fromLower(int value) {
            return value;
        }

        @Specialization(guards = "isUppercaseLetter(value)")
        protected int fromUpper(int value) {
            return value + 0x20;
        }

        @Specialization(guards = "isAsciiCharacter(value)")
        protected int fromCharacter(int value) {
            if (isUppercaseLetter(value)) {
                return fromUpper(value);
            }
            return value;
        }

        @Specialization(replaces = {"fromLower", "fromUpper", "fromCharacter"})
        @TruffleBoundary
        protected int doIntrinsic(int value) {
            return Character.toLowerCase(value);
        }
    }

    public abstract static class LLVMIsalpha extends LLVMCTypeIntrinsics {

        @Specialization
        @TruffleBoundary
        protected int doIntrinsic(int value) {
            return Character.isAlphabetic(value) ? 1 : 0;
        }
    }

    public abstract static class LLVMIsspace extends LLVMCTypeIntrinsics {

        @Specialization
        @TruffleBoundary
        protected int doIntrinsic(int value) {
            return Character.isWhitespace(value) ? 1 : 0;
        }
    }

    public abstract static class LLVMIsupper extends LLVMCTypeIntrinsics {

        @Specialization(guards = "isLowercaseLetter(value)")
        protected int fromLower(@SuppressWarnings("unused") int value) {
            return 0;
        }

        @Specialization(guards = "isUppercaseLetter(value)")
        protected int fromUpper(@SuppressWarnings("unused") int value) {
            return 1;
        }

        @Specialization(guards = "isAsciiCharacter(value)")
        protected int fromCharacter(int value) {
            if (isUppercaseLetter(value)) {
                return 1;
            }
            return 0;
        }

        @Specialization(replaces = {"fromLower", "fromUpper", "fromCharacter"})
        @TruffleBoundary
        protected int doIntrinsic(int value) {
            return Character.isUpperCase(value) ? 1 : 0;
        }
    }
}
