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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNode.LLVMPointerDataEscapeNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@ExportLibrary(InteropLibrary.class)
public class LLVMVaListStorage implements TruffleObject {

    private static final String GET_MEMBER = "get";

    protected Object[] realArguments;
    protected int numberOfExplicitArguments;

    protected LLVMNativePointer nativized;

    // InteropLibrary implementation

    /*
     * The managed va_list can be accessed as an array, where the array elements correspond to the
     * varargs, i.e. the explicit arguments are excluded.
     *
     * Further, the managed va_list exposes one invokable member 'get(index, type)'. The index
     * argument identifies the argument in the va_list, while the type specifies the required type
     * of the returned argument. In the case of a pointer argument, the pointer is just exported
     * with the given type. For other argument types the appropriate conversion should be done
     * (TODO).
     */

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean hasMembers() {
        return true;
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class VAListMembers implements TruffleObject {

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        long getArraySize() {
            return 1;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isArrayElementReadable(long index) {
            return index == 0;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (index == 0) {
                return "get";
            } else {
                throw InvalidArrayIndexException.create(index);
            }
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new VAListMembers();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean isMemberInvocable(String member) {
        return GET_MEMBER.equals(member);
    }

    @ExportMessage
    public Object invokeMember(String member, Object[] arguments,
                    @Cached LLVMPointerDataEscapeNode pointerEscapeNode) throws ArityException, UnknownIdentifierException, UnsupportedTypeException {
        if (GET_MEMBER.equals(member)) {
            if (arguments.length == 2) {
                if (!(arguments[0] instanceof Integer)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw UnsupportedTypeException.create(new Object[]{arguments[0]}, "Index argument must be an integer");
                }
                int i = (Integer) arguments[0];
                if (i >= realArguments.length - numberOfExplicitArguments) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new ArrayIndexOutOfBoundsException(i);
                }

                Object arg = realArguments[numberOfExplicitArguments + i];

                if (!(arguments[1] instanceof LLVMInteropType.Structured)) {
                    return arg;
                }
                LLVMInteropType.Structured type = (LLVMInteropType.Structured) arguments[1];

                if (!LLVMPointer.isInstance(arg)) {
                    // TODO: Do some conversion if the type in the 2nd argument does not match the
                    // arg's types
                    return arg;
                }
                LLVMPointer ptrArg = LLVMPointer.cast(arg);

                return pointerEscapeNode.executeWithType(ptrArg, type);

            } else {
                throw ArityException.create(2, arguments.length);
            }
        }
        throw UnknownIdentifierException.create(member);
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    public long getArraySize() {
        return realArguments.length - numberOfExplicitArguments;
    }

    @ExportMessage
    public boolean isArrayElementReadable(long index) {
        return index < realArguments.length - numberOfExplicitArguments;
    }

    @ExportMessage
    public Object readArrayElement(long index) {
        return realArguments[(int) index + numberOfExplicitArguments];
    }

    @ExportMessage
    public boolean isPointer() {
        return nativized != null && LLVMNativePointer.isInstance(nativized);
    }

    @ExportMessage
    public long asPointer() {
        return nativized == null ? 0L : nativized.asNative();
    }

}
