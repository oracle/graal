/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.others;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;

public abstract class LLVMCheckGlobalVariableStorageNode extends LLVMNode {

    private final LLVMGlobal descriptor;

    public LLVMCheckGlobalVariableStorageNode(LLVMGlobal descriptor) {
        this.descriptor = descriptor;
    }

    public abstract boolean execute();


    @Specialization
    boolean doCheck(
                    @CachedContext(LLVMLanguage.class) LLVMContext context,
                    @Cached CheckDynamicObjectHelper checkHelper) {
        return checkHelper.execute(context.getGlobalStorage(), descriptor);
    }

    abstract static class CheckDynamicObjectHelper extends LLVMNode {

        public abstract boolean execute(DynamicObject object, LLVMGlobal descriptor);

        @SuppressWarnings("unused")
        @Specialization(limit = "3", //
                        guards = {
                                        "dynamicObject.getShape() == cachedShape",
                        }, //
                        assumptions = {
                                        "layoutAssumption"
                        })
        protected boolean checkDirect(DynamicObject dynamicObject, LLVMGlobal descriptor,
                        @Cached("dynamicObject.getShape()") Shape cachedShape,
                        @Cached("cachedShape.getValidAssumption()") Assumption layoutAssumption,
                        @Cached("cachedShape.getProperty(descriptor)") Property property) {
            CompilerAsserts.partialEvaluationConstant(descriptor);
            return property == null ? false : true;
        }

        @TruffleBoundary
        @Specialization(replaces = {"checkDirect"})
        protected boolean checkIndirect(DynamicObject dynamicObject, LLVMGlobal descriptor) {
            return dynamicObject.containsKey(descriptor);
        }

    }
}
