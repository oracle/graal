/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.debug.value;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebuggerValue;

public final class LLVMDebugInternalValue extends LLVMDebuggerValue {

    private static final String VALUE_KEY = "<value>";
    private static final String[] INTEROP_KEYS = new String[]{VALUE_KEY};

    private static final String NO_TYPE = "";

    private final Object value;
    private final Object type;

    public LLVMDebugInternalValue(Object value, Object type) {
        this.value = value;
        this.type = type;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return String.valueOf(value);
    }

    @TruffleBoundary
    public Object getMetaObject() {
        return type == null ? NO_TYPE : String.valueOf(type);
    }

    @Override
    protected int getElementCountForDebugger() {
        return value instanceof TruffleObject ? 1 : 0;
    }

    @Override
    protected String[] getKeysForDebugger() {
        return value instanceof TruffleObject ? INTEROP_KEYS : NO_KEYS;
    }

    @Override
    protected Object getElementForDebugger(String key) {
        return VALUE_KEY.equals(key) ? value : null;
    }
}
