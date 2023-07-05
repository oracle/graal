/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm;

import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public abstract class LLVMBuiltin extends LLVMExpressionNode {

    public interface ScalarBuiltinFactory {

        LLVMExpressionNode create(LLVMExpressionNode[] args);
    }

    public interface ScalarBuiltinFactory1 extends ScalarBuiltinFactory {

        @Override
        default LLVMExpressionNode create(LLVMExpressionNode[] args) {
            return create(args[1]);
        }

        LLVMExpressionNode create(LLVMExpressionNode arg);
    }

    public interface ScalarBuiltinFactory2 extends ScalarBuiltinFactory {

        @Override
        default LLVMExpressionNode create(LLVMExpressionNode[] args) {
            return create(args[1], args[2]);
        }

        LLVMExpressionNode create(LLVMExpressionNode arg1, LLVMExpressionNode arg2);
    }

    public interface ScalarBuiltinFactory3 extends ScalarBuiltinFactory {

        @Override
        default LLVMExpressionNode create(LLVMExpressionNode[] args) {
            return create(args[1], args[2], args[3]);
        }

        LLVMExpressionNode create(LLVMExpressionNode arg1, LLVMExpressionNode arg2, LLVMExpressionNode arg3);
    }

    public interface VectorBuiltinFactory {

        LLVMExpressionNode create(int vectorSize, LLVMExpressionNode[] args);
    }

    public interface VectorBuiltinFactory1 extends VectorBuiltinFactory {

        @Override
        default LLVMExpressionNode create(int vectorSize, LLVMExpressionNode[] args) {
            return create(vectorSize, args[1]);
        }

        LLVMExpressionNode create(int vectorSize, LLVMExpressionNode arg);
    }

    public interface VectorBuiltinFactory2 extends VectorBuiltinFactory {

        @Override
        default LLVMExpressionNode create(int vectorSize, LLVMExpressionNode[] args) {
            return create(vectorSize, args[1], args[2]);
        }

        LLVMExpressionNode create(int vectorSize, LLVMExpressionNode arg1, LLVMExpressionNode arg2);
    }

    public interface VectorBuiltinFactory3 extends VectorBuiltinFactory {

        @Override
        default LLVMExpressionNode create(int vectorSize, LLVMExpressionNode[] args) {
            return create(vectorSize, args[1], args[2], args[3]);
        }

        LLVMExpressionNode create(int vectorSize, LLVMExpressionNode arg1, LLVMExpressionNode arg2, LLVMExpressionNode arg3);
    }

    public interface TypedBuiltinFactory {

        ScalarBuiltinFactory getScalar();

        VectorBuiltinFactory getVector();

        static TypedBuiltinFactory vector(ScalarBuiltinFactory f, VectorBuiltinFactory v) {
            return new TypedBuiltinFactory() {

                @Override
                public ScalarBuiltinFactory getScalar() {
                    return f;
                }

                @Override
                public VectorBuiltinFactory getVector() {
                    return v;
                }
            };
        }

        static TypedBuiltinFactory vector1(ScalarBuiltinFactory1 f, VectorBuiltinFactory1 v) {
            return vector(f, v);
        }

        static TypedBuiltinFactory vector2(ScalarBuiltinFactory2 f, VectorBuiltinFactory2 v) {
            return vector(f, v);
        }

        static TypedBuiltinFactory vector3(ScalarBuiltinFactory3 f, VectorBuiltinFactory3 v) {
            return vector(f, v);
        }

        static TypedBuiltinFactory simple(ScalarBuiltinFactory f) {
            return vector(f, null);
        }

        static TypedBuiltinFactory simple1(ScalarBuiltinFactory1 f) {
            return vector(f, null);
        }

        static TypedBuiltinFactory simple2(ScalarBuiltinFactory2 f) {
            return vector(f, null);
        }

        static TypedBuiltinFactory simple3(ScalarBuiltinFactory3 f) {
            return vector(f, null);
        }
    }
}
