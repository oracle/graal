/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.interop;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@GenerateUncached
@NodeChild(type = LLVMExpressionNode.class)
@NodeField(name = "allowNonForeign", type = boolean.class)
public abstract class LLVMAsForeignNode extends LLVMNode {
    public abstract Object execute(VirtualFrame frame);

    public abstract Object execute(LLVMManagedPointer pointer);

    public static LLVMAsForeignNode create() {
        return LLVMAsForeignNodeGen.create(null, false);
    }

    public static LLVMAsForeignNode create(LLVMExpressionNode arg) {
        return LLVMAsForeignNodeGen.create(arg, false);
    }

    public static LLVMAsForeignNode createOptional() {
        return LLVMAsForeignNodeGen.create(null, true);
    }

    protected abstract boolean isAllowNonForeign();

    @Specialization
    Object doForeign(Object pointer,
                    @Cached("createClassProfile()") ValueProfile objectProfile,
                    @Cached ConditionProfile foreignProfile,
                    @Cached BranchProfile nonForeignProfile) {
        if (LLVMManagedPointer.isInstance(pointer)) {
            LLVMManagedPointer managed = LLVMManagedPointer.cast(pointer);
            if (managed.getOffset() == 0) {
                Object object = objectProfile.profile(managed.getObject());
                // XYZ
                if (foreignProfile.profile(object instanceof LLVMTypedForeignObject)) {
                    return ((LLVMTypedForeignObject) object).getForeign();
                } else if (!(object instanceof LLVMInternalTruffleObject) && !LLVMPointer.isInstance(object)) {
                    return object;
                }
            }
        }
        nonForeignProfile.enter();
        if (isAllowNonForeign()) {
            return null;
        } else {
            throw new LLVMPolyglotException(this, "Pointer does not point to a polyglot value");
        }
    }
}
