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
package com.oracle.truffle.llvm.nodes.impl.op.compare;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI64Node;

@NodeChildren({@NodeChild(type = LLVMI64Node.class), @NodeChild(type = LLVMI64Node.class)})
public abstract class LLVMI64CompareNode extends LLVMI1Node {

    public abstract static class LLVMI64SltNode extends LLVMI64CompareNode {
        @Specialization
        public boolean executeI1(long val1, long val2) {
            return val1 < val2;
        }
    }

    public abstract static class LLVMI64SleNode extends LLVMI64CompareNode {
        @Specialization
        public boolean executeI1(long val1, long val2) {
            return val1 <= val2;
        }
    }

    public abstract static class LLVMI64SgtNode extends LLVMI64CompareNode {
        @Specialization
        public boolean executeI1(long val1, long val2) {
            return val1 > val2;
        }
    }

    public abstract static class LLVMI64SgeNode extends LLVMI64CompareNode {
        @Specialization
        public boolean executeI1(long val1, long val2) {
            return val1 >= val2;
        }
    }

    public abstract static class LLVMI64UgtNode extends LLVMI64CompareNode {
        @Specialization
        public boolean executeI1(long val1, long val2) {
            return Long.compareUnsigned(val1, val2) > 0;

        }
    }

    public abstract static class LLVMI64UgeNode extends LLVMI64CompareNode {
        @Specialization
        public boolean executeI1(long val1, long val2) {
            return Long.compareUnsigned(val1, val2) >= 0;
        }
    }

    public abstract static class LLVMI64UltNode extends LLVMI64CompareNode {
        @Specialization
        public boolean executeI1(long val1, long val2) {
            return Long.compareUnsigned(val1, val2) < 0;
        }
    }

    public abstract static class LLVMI64UleNode extends LLVMI64CompareNode {
        @Specialization
        public boolean executeI1(long val1, long val2) {
            return Long.compareUnsigned(val1, val2) <= 0;
        }
    }

}
