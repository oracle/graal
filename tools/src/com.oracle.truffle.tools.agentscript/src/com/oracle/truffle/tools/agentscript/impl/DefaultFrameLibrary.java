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
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.tools.agentscript.FrameLibrary;
import java.util.Set;

@ExportLibrary(value = FrameLibrary.class, receiverType = Frame.class)
public final class DefaultFrameLibrary {
    @CompilerDirectives.TruffleBoundary
    @ExportMessage
    static Object readMember(
                    Frame frame, Node where,
                    TruffleInstrument.Env env,
                    String member) throws UnknownIdentifierException {
        InteropLibrary iop = InteropLibrary.getFactory().getUncached();
        for (Scope scope : env.findLocalScopes(where, frame)) {
            if (scope == null) {
                continue;
            }
            if (member.equals(scope.getReceiverName())) {
                return scope.getReceiver();
            }
            Object variable = readMemberImpl(member, scope.getVariables(), iop);
            if (variable != null) {
                return variable;
            }
            Object argument = readMemberImpl(member, scope.getArguments(), iop);
            if (argument != null) {
                return argument;
            }
        }
        throw UnknownIdentifierException.create(member);
    }

    static Object readMemberImpl(String name, Object map, InteropLibrary iop) {
        if (map != null && iop.hasMembers(map)) {
            try {
                return iop.readMember(map, name);
            } catch (InteropException e) {
                return null;
            }
        }
        return null;
    }

    @ExportMessage
    static void collectNames(Frame frame, Node where,
                    TruffleInstrument.Env env,
                    Set<String> names) throws InteropException {
        InteropLibrary iop = InteropLibrary.getFactory().getUncached();
        for (Scope scope : env.findLocalScopes(where, frame)) {
            if (scope == null) {
                continue;
            }
            final String receiverName = scope.getReceiverName();
            if (receiverName != null) {
                names.add(receiverName);
            }
            readMemberNames(names, scope.getVariables(), iop);
            readMemberNames(names, scope.getArguments(), iop);
        }
    }

    private static void readMemberNames(Set<String> names, Object map, InteropLibrary iop) throws InteropException {
        if (map != null && iop.hasMembers(map)) {
            Object members = iop.getMembers(map);
            long size = iop.getArraySize(members);
            for (long i = 0; i < size; i++) {
                Object at = iop.readArrayElement(members, i);
                if (at instanceof String) {
                    names.add((String) at);
                }
            }
        }
    }
}
