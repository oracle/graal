/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

abstract class AbstractContextObject implements TruffleObject {
    static final ArrayObject MEMBERS = ArrayObject.array(
                    "name", "source", "characters",
                    "line", "startLine", "endLine",
                    "column", "startColumn", "endColumn", "charIndex", "charLength", "charEndIndex");

    @CompilerDirectives.CompilationFinal private String name;
    @CompilerDirectives.CompilationFinal(dimensions = 1) private int[] values;

    abstract Node getInstrumentedNode();

    abstract SourceSection getInstrumentedSourceSection();

    Object readMember(String member) throws UnknownIdentifierException {
        int index;
        switch (member) {
            case "name":
                if (name == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    final RootNode node = getInstrumentedNode().getRootNode();
                    name = node.getQualifiedName();
                }
                return NullObject.nullCheck(name);
            case "characters": {
                CompilerDirectives.transferToInterpreter();
                final SourceSection ss = getInstrumentedSourceSection();
                if (ss == null) {
                    return NullObject.nullCheck(null);
                }
                return ss.getCharacters().toString();
            }
            case "source": {
                final SourceSection ss = getInstrumentedSourceSection();
                if (ss == null) {
                    return NullObject.nullCheck(null);
                }
                return new SourceEventObject(ss.getSource());
            }
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
            case "charIndex":
                index = 4;
                break;
            case "charLength":
                index = 5;
                break;
            case "charEndIndex":
                index = 6;
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
        final SourceSection section = getInstrumentedSourceSection();
        if (section == null) {
            return new int[7];
        }
        return new int[]{
                        section.getStartLine(),
                        section.getEndLine(),
                        section.getStartColumn(),
                        section.getEndColumn(),
                        section.getCharIndex(),
                        section.getCharLength(),
                        section.getCharEndIndex(),
        };
    }
}
