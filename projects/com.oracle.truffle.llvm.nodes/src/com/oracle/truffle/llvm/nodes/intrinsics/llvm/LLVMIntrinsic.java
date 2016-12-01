/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.intrinsics.llvm;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.llvm.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.nodes.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI8Node;

public interface LLVMIntrinsic {

    @GenerateNodeFactory
    abstract class LLVMVoidIntrinsic extends LLVMNode implements LLVMIntrinsic {

    }

    @GenerateNodeFactory
    abstract class LLVMAddressIntrinsic extends LLVMAddressNode implements LLVMIntrinsic {

    }

    @GenerateNodeFactory
    abstract class LLVMFloatIntrinsic extends LLVMFloatNode implements LLVMIntrinsic {

    }

    @GenerateNodeFactory
    abstract class LLVMDoubleIntrinsic extends LLVMDoubleNode implements LLVMIntrinsic {

    }

    @GenerateNodeFactory
    abstract class LLVMBooleanIntrinsic extends LLVMI1Node implements LLVMIntrinsic {

    }

    @GenerateNodeFactory
    abstract class LLVMI8Intrinsic extends LLVMI8Node implements LLVMIntrinsic {

    }

    @GenerateNodeFactory
    abstract class LLVMI32Intrinsic extends LLVMI32Node implements LLVMIntrinsic {

    }

    @GenerateNodeFactory
    abstract class LLVMI64Intrinsic extends LLVMI64Node implements LLVMIntrinsic {

    }

}
