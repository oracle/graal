/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.debug;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;

/**
 * Interface of an object describing a source-level variable. Debuggers can use this to display the
 * original source-level state of an executed LLVM IR file.
 */
public interface LLVMDebugObject extends TruffleObject {

    static boolean isInstance(TruffleObject object) {
        return object instanceof LLVMDebugObject;
    }

    /**
     * Get an object describing the referenced variable's type for the debugger to show.
     *
     * @return the type of the referenced object
     */
    Object getType();

    /**
     * If this is a complex object return the identifiers for its members.
     *
     * @return the keys or null
     */
    Object[] getKeys();

    /**
     * If this is a complex object return the member that is identified by the given key.
     *
     * @param identifier the object identifying the member
     *
     * @return the member or {@code null} if the key does not identify a member
     */
    Object getMember(Object identifier);

    /**
     * A representation of the current value of the referenced variable for the debugger to show.
     *
     * @return a string describing the referenced value
     */
    @Override
    String toString();

    @Override
    default ForeignAccess getForeignAccess() {
        return LLVMDebugObjectMessageResolutionForeign.ACCESS;
    }
}
