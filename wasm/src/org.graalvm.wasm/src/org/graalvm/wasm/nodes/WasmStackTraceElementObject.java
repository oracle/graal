/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.wasm.nodes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.SourceSection;

@ExportLibrary(InteropLibrary.class)
final class WasmStackTraceElementObject implements TruffleObject {

    private final WasmFunctionRootNode rootNode;
    private final SourceSection sourceSection;
    private final int bytecodeIndex;

    WasmStackTraceElementObject(WasmFunctionRootNode rootNode, SourceSection sourceSection, int bytecodeIndex) {
        this.rootNode = rootNode;
        this.sourceSection = sourceSection;
        this.bytecodeIndex = bytecodeIndex;
    }

    @ExportMessage
    @TruffleBoundary
    boolean hasExecutableName() {
        return rootNode.getName() != null;
    }

    @ExportMessage
    @TruffleBoundary
    Object getExecutableName() throws UnsupportedMessageException {
        String name = rootNode.getName();
        if (name == null) {
            throw UnsupportedMessageException.create();
        }
        return name;
    }

    @ExportMessage
    boolean hasSourceLocation() {
        return sourceSection != null;
    }

    @ExportMessage
    SourceSection getSourceLocation() throws UnsupportedMessageException {
        if (sourceSection == null) {
            throw UnsupportedMessageException.create();
        }
        return sourceSection;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasLanguageId() {
        return true;
    }

    @ExportMessage
    String getLanguageId() {
        return rootNode.getLanguageInfo().getId();
    }

    @ExportMessage
    @TruffleBoundary
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        StringBuilder builder = new StringBuilder();
        builder.append('<').append(getLanguageId()).append("> ");
        String name = rootNode.getName();
        builder.append(name != null ? name : "Unknown");
        if (sourceSection != null) {
            builder.append('(');
            builder.append(sourceSection.getSource().getName());
            builder.append(':');
            builder.append(sourceSection.getStartLine());
            builder.append(')');
        }
        return builder.toString();
    }

    @ExportMessage
    boolean hasBytecodeIndex() {
        return bytecodeIndex >= 0;
    }

    @ExportMessage
    int getBytecodeIndex() throws UnsupportedMessageException {
        if (hasBytecodeIndex()) {
            return bytecodeIndex;
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isInternal() {
        return rootNode.isInternal();
    }

    @Override
    public String toString() {
        return (String) toDisplayString(true);
    }
}
