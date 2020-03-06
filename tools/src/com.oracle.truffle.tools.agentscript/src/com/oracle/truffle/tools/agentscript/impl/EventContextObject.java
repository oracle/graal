/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.tools.agentscript.impl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.SourceSection;

@SuppressWarnings("unused")
@ExportLibrary(InteropLibrary.class)
final class EventContextObject implements TruffleObject {
    private static final ArrayObject MEMBERS = ArrayObject.array(
                    "name", "source", "characters",
                    "line", "startLine", "endLine",
                    "column", "startColumn", "endColumn");
    private final EventContext context;
    @CompilerDirectives.CompilationFinal private String name;
    @CompilerDirectives.CompilationFinal(dimensions = 1) private int[] values;

    EventContextObject(EventContext context) {
        this.context = context;
    }

    @CompilerDirectives.TruffleBoundary
    RuntimeException wrap(Object target, int arity, InteropException ex) {
        IllegalStateException ill = new IllegalStateException("Cannot invoke " + target + " with " + arity + " arguments: " + ex.getMessage());
        ill.initCause(ex);
        return context.createError(ill);
    }

    RuntimeException rethrow(RuntimeException ex) {
        if (ex instanceof TruffleException) {
            if (!((TruffleException) ex).isInternalError()) {
                return context.createError(ex);
            }
        }
        throw ex;
    }

    @ExportMessage
    static boolean hasMembers(EventContextObject obj) {
        return true;
    }

    @ExportMessage
    static Object getMembers(EventContextObject obj, boolean includeInternal) {
        return MEMBERS;
    }

    @ExportMessage
    Object readMember(String member) throws UnknownIdentifierException {
        int index;
        switch (member) {
            case "name":
                if (name == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    name = context.getInstrumentedNode().getRootNode().getName();
                }
                return name;
            case "characters":
                CompilerDirectives.transferToInterpreter();
                return context.getInstrumentedSourceSection().getCharacters().toString();
            case "source":
                return new SourceEventObject(context.getInstrumentedSourceSection().getSource());
            case "line":
            case "startLine":
                index = 0;
                break;
            case "endLine":
                index = 1;
                break;
            case "column":
            case "startColumn":
                index = 2;
                break;
            case "endColumn":
                index = 3;
                break;
            default:
                throw UnknownIdentifierException.create(member);
        }
        if (values == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            values = valuesForContext();
        }
        return values[index];
    }

    @CompilerDirectives.TruffleBoundary
    private int[] valuesForContext() {
        final SourceSection section = context.getInstrumentedSourceSection();
        return new int[]{
                        section.getStartLine(),
                        section.getEndLine(),
                        section.getStartColumn(),
                        section.getEndColumn()
        };
    }

    @ExportMessage
    static boolean isMemberReadable(EventContextObject obj, String member) {
        return MEMBERS.contains(member);
    }

}
