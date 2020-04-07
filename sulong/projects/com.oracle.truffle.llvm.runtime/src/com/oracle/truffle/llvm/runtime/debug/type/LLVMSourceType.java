/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.debug.type;

import java.util.function.Supplier;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebuggerValue;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObject;

@ExportLibrary(InteropLibrary.class)
public abstract class LLVMSourceType extends LLVMDebuggerValue {

    public static final LLVMSourceType UNSUPPORTED = new LLVMSourceType(() -> "<unsupported>", 0, 0, 0, null) {

        @Override
        public LLVMSourceType getOffset(long newOffset) {
            return this;
        }
    };

    public static final LLVMSourceType UNKNOWN = new LLVMSourceType(() -> "<unknown>", 0, 0, 0, null) {

        @Override
        public LLVMSourceType getOffset(long newOffset) {
            return this;
        }
    };

    public static final LLVMSourceType VOID = new LLVMSourceType(() -> "void", 0, 0, 0, null) {
        @Override
        public LLVMSourceType getOffset(long newOffset) {
            return this;
        }
    };

    private final LLVMSourceLocation location;
    private final long size;
    private final long align;
    private final long offset;
    @CompilationFinal private Supplier<String> nameSupplier;

    public LLVMSourceType(Supplier<String> nameSupplier, long size, long align, long offset, LLVMSourceLocation location) {
        this.nameSupplier = nameSupplier;
        this.size = size;
        this.align = align;
        this.offset = offset;
        this.location = location;
    }

    LLVMSourceType(long size, long align, long offset, LLVMSourceLocation location) {
        this(UNKNOWN::getName, size, align, offset, location);
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    final boolean isMetaObject() {
        return true;
    }

    @ExportMessage(name = "getMetaSimpleName")
    @ExportMessage(name = "getMetaQualifiedName")
    final Object getMetaSimpleName() {
        return getName();
    }

    @ExportMessage
    final boolean isMetaInstance(Object instance) {
        if (instance instanceof LLVMDebugObject) {
            return ((LLVMDebugObject) instance).getType() == this;
        }
        return false;
    }

    @ExportMessage
    final boolean hasSourceLocation() {
        return location != null;
    }

    @ExportMessage
    @TruffleBoundary
    final SourceSection getSourceLocation() {
        return location.getSourceSection();
    }

    @TruffleBoundary
    public String getName() {
        return nameSupplier.get();
    }

    public void setName(Supplier<String> nameSupplier) {
        CompilerAsserts.neverPartOfCompilation();
        this.nameSupplier = nameSupplier;
    }

    public long getSize() {
        return size;
    }

    public long getAlign() {
        return align;
    }

    public long getOffset() {
        return offset;
    }

    public abstract LLVMSourceType getOffset(long newOffset);

    public LLVMSourceType getActualType() {
        return this;
    }

    @Ignore
    public boolean isPointer() {
        return false;
    }

    public boolean isReference() {
        return false;
    }

    public boolean isAggregate() {
        return false;
    }

    public boolean isEnum() {
        return false;
    }

    public int getElementCount() {
        return 0;
    }

    public String getElementName(@SuppressWarnings("unused") long i) {
        return null;
    }

    public LLVMSourceType getElementType(@SuppressWarnings("unused") long i) {
        return null;
    }

    public LLVMSourceType getElementType(@SuppressWarnings("unused") String name) {
        return null;
    }

    public LLVMSourceLocation getElementDeclaration(@SuppressWarnings("unused") long i) {
        return null;
    }

    public LLVMSourceLocation getElementDeclaration(@SuppressWarnings("unused") String name) {
        return null;
    }

    public LLVMSourceLocation getLocation() {
        return location;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    protected int getElementCountForDebugger() {
        return getElementCount();
    }

    @Override
    protected String[] getKeysForDebugger() {
        if (getElementCount() == 0) {
            return NO_KEYS;
        }

        final String[] keys = new String[getElementCount()];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = getElementName(i);
        }
        return keys;
    }

    @Override
    protected Object getElementForDebugger(String key) {
        return getElementType(key);
    }
}
