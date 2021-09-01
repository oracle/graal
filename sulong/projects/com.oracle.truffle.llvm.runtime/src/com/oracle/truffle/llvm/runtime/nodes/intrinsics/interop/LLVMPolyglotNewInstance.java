/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.LLVMAsForeignNode;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNode;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.types.Type;

@NodeChild(value = "object", type = LLVMExpressionNode.class)
public abstract class LLVMPolyglotNewInstance extends LLVMIntrinsic {

    @Children private final LLVMExpressionNode[] args;
    @Children private final LLVMDataEscapeNode[] prepareValuesForEscape;

    @Child private InteropLibrary foreignNewInstance;
    @Child private LLVMAsForeignNode asForeign = LLVMAsForeignNode.create();

    public LLVMPolyglotNewInstance(LLVMExpressionNode[] args, Type[] argTypes) {
        this.args = args;
        this.prepareValuesForEscape = new LLVMDataEscapeNode[args.length];
        for (int i = 0; i < prepareValuesForEscape.length; i++) {
            prepareValuesForEscape[i] = LLVMDataEscapeNode.create(argTypes[i]);
        }
        this.foreignNewInstance = InteropLibrary.getFactory().createDispatched(5);
    }

    @Specialization
    @ExplodeLoop
    protected Object doNew(VirtualFrame frame, LLVMManagedPointer value,
                    @Cached("createForeignToLLVM()") ForeignToLLVM toLLVM,
                    @Cached BranchProfile exception) {
        Object foreign = asForeign.execute(value);

        Object[] evaluatedArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            evaluatedArgs[i] = prepareValuesForEscape[i].executeWithTarget(args[i].executeGeneric(frame));
        }

        try {
            Object rawValue;
            rawValue = foreignNewInstance.instantiate(foreign, evaluatedArgs);
            return toLLVM.executeWithTarget(rawValue);
        } catch (UnsupportedMessageException e) {
            exception.enter();
            throw new LLVMPolyglotException(this, "Polyglot value cannot be instantiated.");
        } catch (UnsupportedTypeException e) {
            exception.enter();
            throw new LLVMPolyglotException(this, "Wrong argument type passed to polyglot_new_instance.");
        } catch (ArityException e) {
            exception.enter();
            throw new LLVMPolyglotException(this, "Wrong number of arguments passed to polyglot_new_instance, expected %d but got %d.", e.getExpectedMinArity(), e.getActualArity());
        }
    }

    /**
     * @param value @NodeChild
     * @see LLVMPolyglotNewInstance
     */
    @Fallback
    public Object fallback(Object value) {
        throw new LLVMPolyglotException(this, "Non-polyglot value passed to polyglot_new_instance.");
    }

    protected ForeignToLLVM createForeignToLLVM() {
        return CommonNodeFactory.createForeignToLLVM(ForeignToLLVMType.POINTER);
    }
}
