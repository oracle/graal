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
package com.oracle.truffle.tools.agentscript;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.tools.agentscript.impl.AccessorFrameLibrary;
import com.oracle.truffle.tools.agentscript.impl.DefaultFrameLibrary;
import java.util.Set;

@GenerateLibrary(defaultExportLookupEnabled = true)
@GenerateLibrary.DefaultExport(DefaultFrameLibrary.class)
public abstract class FrameLibrary extends Library {
    public static FrameLibrary getUncached() {
        return UncachedDefault.DEFAULT;
    }

    public abstract Object readMember(
                    Query env,
                    String member) throws UnknownIdentifierException;

    public abstract void collectNames(
                    Query env,
                    Set<String> names) throws InteropException;

    public static final class Query {
        private final Node where;
        private final Frame frame;
        private final TruffleInstrument.Env env;

        Query(Node where, Frame frame, TruffleInstrument.Env env) {
            this.where = where;
            this.frame = frame;
            this.env = env;
        }

        public Iterable<Scope> findLocalScopes() {
            return env.findLocalScopes(where, frame);
        }

        public Frame frame() {
            return frame;
        }
    }

    static {
        AccessorFrameLibrary accessor = new AccessorFrameLibrary() {
            @Override
            protected Query create(Node where, Frame frame, TruffleInstrument.Env env) {
                return new Query(where, frame, env);
            }
        };
        assert AccessorFrameLibrary.DEFAULT == accessor;
    }

    private static final class UncachedDefault extends FrameLibrary {
        static final FrameLibrary DEFAULT = new UncachedDefault();

        private UncachedDefault() {
        }

        @CompilerDirectives.TruffleBoundary
        @Override
        public Object readMember(Query env, String member) throws UnknownIdentifierException {
            InteropLibrary iop = InteropLibrary.getFactory().getUncached();
            for (Scope scope : env.findLocalScopes()) {
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

        @CompilerDirectives.TruffleBoundary
        @Override
        public void collectNames(Query env, Set<String> names) throws InteropException {
            InteropLibrary iop = InteropLibrary.getFactory().getUncached();
            for (Scope scope : env.findLocalScopes()) {
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

        @Override
        public boolean accepts(Object receiver) {
            return receiver instanceof Query;
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

        static void readMemberNames(Set<String> names, Object map, InteropLibrary iop) throws InteropException {
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
}
