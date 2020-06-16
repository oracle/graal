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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.LLVMAsForeignNode;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class LLVMPolyglotWrite {

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPolyglotPutMember extends LLVMIntrinsic {

        @Child LLVMDataEscapeNode prepareValueForEscape;

        public LLVMPolyglotPutMember(Type[] argTypes) {
            if (argTypes.length != 4) { // first argument is the stack pointer
                throw new LLVMPolyglotException(this, "polyglot_put_member must be called with exactly 3 arguments.");
            }
            prepareValueForEscape = LLVMDataEscapeNode.create(argTypes[3]);
        }

        @Specialization
        protected void doIntrinsic(LLVMManagedPointer target, Object id, Object value,
                        @Cached LLVMAsForeignNode asForeign,
                        @CachedLibrary(limit = "3") InteropLibrary foreignWrite,
                        @Cached("createReadString()") LLVMReadStringNode readStr,
                        @Cached BranchProfile exception) {
            Object foreign = asForeign.execute(target);
            String name = readStr.executeWithTarget(id);
            Object escapedValue = prepareValueForEscape.executeWithTarget(value);
            try {
                foreignWrite.writeMember(foreign, name, escapedValue);
            } catch (UnsupportedMessageException e) {
                exception.enter();
                throw new LLVMPolyglotException(foreignWrite, "Can not write member '%s' to polyglot value.", name);
            } catch (UnknownIdentifierException e) {
                exception.enter();
                throw new LLVMPolyglotException(foreignWrite, "Member '%s' does not exist.", e.getUnknownIdentifier());
            } catch (UnsupportedTypeException e) {
                exception.enter();
                throw new LLVMPolyglotException(foreignWrite, "Unsupported type writing member '%s'.", name);
            }
        }

        @Fallback
        @TruffleBoundary
        @SuppressWarnings("unused")
        public Object fallback(Object value, Object id, Object v) {
            throw new LLVMPolyglotException(this, "Invalid argument to polyglot builtin.");
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPolyglotSetArrayElement extends LLVMIntrinsic {

        @Child LLVMDataEscapeNode prepareValueForEscape;

        public LLVMPolyglotSetArrayElement(Type[] argTypes) {
            if (argTypes.length != 4) { // first argument is the stack pointer
                throw new LLVMPolyglotException(this, "polyglot_set_array_element must be called with exactly 3 arguments.");
            }
            prepareValueForEscape = LLVMDataEscapeNode.create(argTypes[3]);
        }

        @Specialization
        protected Object doIntrinsic(LLVMManagedPointer target, int id, Object value,
                        @Cached LLVMAsForeignNode asForeign,
                        @CachedLibrary(limit = "3") InteropLibrary foreignWrite) {
            Object foreign = asForeign.execute(target);
            Object escapedValue = prepareValueForEscape.executeWithTarget(value);
            try {
                foreignWrite.writeArrayElement(foreign, id, escapedValue);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new LLVMPolyglotException(foreignWrite, "Can not write to index %d of polyglot value.", id);
            } catch (InvalidArrayIndexException e) {
                CompilerDirectives.transferToInterpreter();
                throw new LLVMPolyglotException(foreignWrite, "Index %d does not exist.", id);
            } catch (UnsupportedTypeException e) {
                CompilerDirectives.transferToInterpreter();
                throw new LLVMPolyglotException(foreignWrite, "Unsupported type writing index %d.", id);
            }
            return null;
        }

        @Fallback
        @TruffleBoundary
        @SuppressWarnings("unused")
        public Object fallback(Object value, Object id, Object v) {
            throw new LLVMPolyglotException(this, "Invalid argument to polyglot builtin.");
        }
    }
}
