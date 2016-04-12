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
package com.oracle.truffle.llvm.nodes.impl.others;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVM80BitFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.floating.LLVM80BitFloat;

public class LLVMProfilingSelectNode {

    @NodeChildren({@NodeChild(type = LLVMI1Node.class), @NodeChild(type = LLVMI1Node.class), @NodeChild(type = LLVMI1Node.class)})
    public abstract static class LLVMI1ProfilingSelectNode extends LLVMI1Node {

        private final ConditionProfile conditionProfile = ConditionProfile.createCountingProfile();

        @Specialization
        public boolean execute(boolean cond, boolean trueBranch, boolean elseBranch) {
            if (conditionProfile.profile(cond)) {
                return trueBranch;
            } else {
                return elseBranch;
            }
        }

    }

    @NodeChildren({@NodeChild(type = LLVMI1Node.class), @NodeChild(type = LLVMI8Node.class), @NodeChild(type = LLVMI8Node.class)})
    public abstract static class LLVMI8ProfilingSelectNode extends LLVMI8Node {

        private final ConditionProfile conditionProfile = ConditionProfile.createCountingProfile();

        @Specialization
        public byte execute(boolean cond, byte trueBranch, byte elseBranch) {
            if (conditionProfile.profile(cond)) {
                return trueBranch;
            } else {
                return elseBranch;
            }
        }

    }

    @NodeChildren({@NodeChild(type = LLVMI1Node.class), @NodeChild(type = LLVMI16Node.class), @NodeChild(type = LLVMI16Node.class)})
    public abstract static class LLVMI16ProfilingSelectNode extends LLVMI16Node {

        private final ConditionProfile conditionProfile = ConditionProfile.createCountingProfile();

        @Specialization
        public short execute(boolean cond, short trueBranch, short elseBranch) {
            if (conditionProfile.profile(cond)) {
                return trueBranch;
            } else {
                return elseBranch;
            }
        }

    }

    @NodeChildren({@NodeChild(type = LLVMI1Node.class), @NodeChild(type = LLVMI32Node.class), @NodeChild(type = LLVMI32Node.class)})
    public abstract static class LLVMI32ProfilingSelectNode extends LLVMI32Node {

        private final ConditionProfile conditionProfile = ConditionProfile.createCountingProfile();

        @Specialization
        public int execute(boolean cond, int trueBranch, int elseBranch) {
            if (conditionProfile.profile(cond)) {
                return trueBranch;
            } else {
                return elseBranch;
            }
        }

    }

    @NodeChildren({@NodeChild(type = LLVMI1Node.class), @NodeChild(type = LLVMI64Node.class), @NodeChild(type = LLVMI64Node.class)})
    public abstract static class LLVMI64ProfilingSelectNode extends LLVMI64Node {

        private final ConditionProfile conditionProfile = ConditionProfile.createCountingProfile();

        @Specialization
        public long execute(boolean cond, long trueBranch, long elseBranch) {
            if (conditionProfile.profile(cond)) {
                return trueBranch;
            } else {
                return elseBranch;
            }
        }

    }

    @NodeChildren({@NodeChild(type = LLVMI1Node.class), @NodeChild(type = LLVMFloatNode.class), @NodeChild(type = LLVMFloatNode.class)})
    public abstract static class LLVMFloatProfilingSelectNode extends LLVMFloatNode {

        private final ConditionProfile conditionProfile = ConditionProfile.createCountingProfile();

        @Specialization
        public float execute(boolean cond, float trueBranch, float elseBranch) {
            if (conditionProfile.profile(cond)) {
                return trueBranch;
            } else {
                return elseBranch;
            }
        }

    }

    @NodeChildren({@NodeChild(type = LLVMI1Node.class), @NodeChild(type = LLVMDoubleNode.class), @NodeChild(type = LLVMDoubleNode.class)})
    public abstract static class LLVMDoubleProfilingSelectNode extends LLVMDoubleNode {

        private final ConditionProfile conditionProfile = ConditionProfile.createCountingProfile();

        @Specialization
        public double execute(boolean cond, double trueBranch, double elseBranch) {
            if (conditionProfile.profile(cond)) {
                return trueBranch;
            } else {
                return elseBranch;
            }
        }

    }

    @NodeChildren({@NodeChild(type = LLVMI1Node.class), @NodeChild(type = LLVM80BitFloatNode.class), @NodeChild(type = LLVM80BitFloatNode.class)})
    public abstract static class LLVM80BitFloatProfilingSelectNode extends LLVM80BitFloatNode {

        private final ConditionProfile conditionProfile = ConditionProfile.createCountingProfile();

        @Specialization
        public LLVM80BitFloat execute(boolean cond, LLVM80BitFloat trueBranch, LLVM80BitFloat elseBranch) {
            if (conditionProfile.profile(cond)) {
                return trueBranch;
            } else {
                return elseBranch;
            }
        }

    }

    @NodeChildren({@NodeChild(type = LLVMI1Node.class), @NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMAddressNode.class)})
    public abstract static class LLVMAddressProfilingSelectNode extends LLVMAddressNode {

        private final ConditionProfile conditionProfile = ConditionProfile.createCountingProfile();

        @Specialization
        public LLVMAddress execute(boolean cond, LLVMAddress trueBranch, LLVMAddress elseBranch) {
            if (conditionProfile.profile(cond)) {
                return trueBranch;
            } else {
                return elseBranch;
            }
        }

    }

    @NodeChildren({@NodeChild(type = LLVMI1Node.class), @NodeChild(type = LLVMFunctionNode.class), @NodeChild(type = LLVMFunctionNode.class)})
    public abstract static class LLVMFunctionProfilingSelectNode extends LLVMFunctionNode {

        private final ConditionProfile conditionProfile = ConditionProfile.createCountingProfile();

        @Specialization
        public LLVMFunctionDescriptor execute(boolean cond, LLVMFunctionDescriptor trueBranch, LLVMFunctionDescriptor elseBranch) {
            if (conditionProfile.profile(cond)) {
                return trueBranch;
            } else {
                return elseBranch;
            }
        }

    }

}
