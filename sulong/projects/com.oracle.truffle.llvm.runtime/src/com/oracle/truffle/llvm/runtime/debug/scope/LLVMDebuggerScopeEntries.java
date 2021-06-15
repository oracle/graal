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
package com.oracle.truffle.llvm.runtime.debug.scope;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebuggerValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@ExportLibrary(InteropLibrary.class)
public final class LLVMDebuggerScopeEntries extends LLVMDebuggerValue {

    static final LLVMDebuggerScopeEntries EMPTY_SCOPE = new LLVMDebuggerScopeEntries();

    private LLVMDebuggerScopeEntries parentScope;
    private final ArrayList<String> flattenedScopeEntries;
    private final Map<String, Object> flattenedScopeMap;

    private final Map<String, Object> entries;
    private String scopeName;
    private boolean isScopeFlattened;

    LLVMDebuggerScopeEntries() {
        this.entries = new HashMap<>();
        this.flattenedScopeEntries = new ArrayList<>();
        this.flattenedScopeMap = new HashMap<>();
        isScopeFlattened = false;
    }

    @TruffleBoundary
    void add(String name, Object value) {
        entries.put(name, value);
    }

    void setScopeName(String scopeName) {
        CompilerAsserts.neverPartOfCompilation();
        this.scopeName = scopeName;
    }

    @TruffleBoundary
    boolean contains(String name) {
        return entries.containsKey(name);
    }

    @Override
    @TruffleBoundary
    protected int getElementCountForDebugger() {
        if (!isScopeFlattened) {
            flattenScopeEntries();
        }
        return flattenedScopeEntries.size();
    }

    @Override
    @TruffleBoundary
    protected String[] getKeysForDebugger() {
        if (!isScopeFlattened) {
            flattenScopeEntries();
        }
        final int count = getElementCountForDebugger();
        if (count == 0) {
            return NO_KEYS;
        }
        return flattenedScopeEntries.toArray(NO_KEYS);
    }

    @Override
    @TruffleBoundary
    protected Object getElementForDebugger(String key) {
        if (!isScopeFlattened) {
            flattenScopeEntries();
        }
        return flattenedScopeMap.get(key);
    }

    @TruffleBoundary
    void removeElement(String key) {
        entries.remove(key);
    }

    protected void setParentScope(LLVMDebuggerScopeEntries parentScope) {
        CompilerAsserts.neverPartOfCompilation();
        this.parentScope = parentScope;
    }

    private void flattenScopeEntries() {
        CompilerAsserts.neverPartOfCompilation();
        LLVMDebuggerScopeEntries tempScope = this;
        while (tempScope != null) {
            flattenedScopeMap.putAll(tempScope.entries);
            flattenedScopeEntries.addAll(tempScope.entries.keySet());
            tempScope = tempScope.parentScope;
        }
        isScopeFlattened = true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean isScope() {
        return true;
    }

    @ExportMessage
    public boolean hasScopeParent() {
        return parentScope != null;
    }

    @ExportMessage
    public Object getScopeParent() throws UnsupportedMessageException {
        if (!hasScopeParent()) {
            throw UnsupportedMessageException.create();
        }
        return parentScope;
    }

    @ExportMessage
    @Override
    public String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return scopeName;
    }
}
