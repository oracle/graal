/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.sulong;

import java.util.List;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.api.Toolchain;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMReadStringNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

public abstract class LLVMToolchainNode extends LLVMIntrinsic {
    @NodeChild(value = "name", type = LLVMExpressionNode.class)
    public abstract static class LLVMToolchainToolNode extends LLVMToolchainNode {

        @Specialization
        protected Object doOp(Object name,
                        @CachedContext(LLVMLanguage.class) LLVMContext ctx,
                        @Cached LLVMReadStringNode readString) {
            TruffleFile path = getToolPath(ctx.getToolchain(), readString.executeWithTarget(name));
            if (path == null) {
                return LLVMNativePointer.createNull();
            }
            return LLVMManagedPointer.create(path.toString());
        }

        @TruffleBoundary
        private static TruffleFile getToolPath(Toolchain toolchain, String tool) {
            return toolchain.getToolPath(tool);
        }
    }

    @NodeChild(value = "path", type = LLVMExpressionNode.class)
    public abstract static class LLVMToolchainPathNode extends LLVMToolchainNode {

        @Specialization
        protected Object doOp(Object path,
                        @CachedContext(LLVMLanguage.class) LLVMContext ctx,
                        @Cached LLVMReadStringNode readString) {
            List<TruffleFile> paths = getPaths(ctx.getToolchain(), readString.executeWithTarget(path));
            if (paths == null) {
                return LLVMNativePointer.createNull();
            }
            return LLVMContext.toTruffleObjects(toArray(paths));
        }

        @TruffleBoundary
        private static String[] toArray(List<TruffleFile> paths) {
            return paths.stream().map(Objects::toString).toArray(String[]::new);
        }

        @TruffleBoundary
        private static List<TruffleFile> getPaths(Toolchain toolchain, String pathName) {
            return toolchain.getPaths(pathName);
        }

    }

    public abstract static class LLVMToolchainIdentifierNode extends LLVMToolchainNode {

        @Specialization
        protected Object doOp(@CachedContext(LLVMLanguage.class) LLVMContext ctx) {
            return LLVMManagedPointer.create(ctx.getToolchain().getIdentifier());
        }

    }
}
