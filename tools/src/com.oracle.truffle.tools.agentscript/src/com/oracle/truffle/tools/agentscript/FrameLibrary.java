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
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.tools.agentscript.impl.AccessorFrameLibrary;
import com.oracle.truffle.tools.agentscript.impl.DefaultFrameLibrary;
import java.util.Collections;
import java.util.Set;

/**
 * Encapsulating access to execution frames.
 * <a href="https://github.com/oracle/graal/blob/master/tools/docs/Insight.md">GraalVM Insight</a>
 * scripts can access local variables of the dynamically <a href=
 * "https://github.com/oracle/graal/blob/master/tools/docs/Insight-Manual.md#inspecting-values">
 * instrumented source code</a>. This library handles such accesses. {@linkplain ExportLibrary
 * Implement your own} to respond to various messages (like
 * {@link #readMember(com.oracle.truffle.tools.agentscript.FrameLibrary.Query, java.lang.String) })
 * in a different way - for example by exposing more than it is in their {@linkplain Frame frames}
 * for some specific languages.
 * <p>
 * It is expected more messages appear in this library during its evolution. Such messages will be
 * provided with their appropriate {@link #getUncached() default} implementation.
 * 
 * @since 20.1
 */
@GenerateLibrary(defaultExportLookupEnabled = true)
@GenerateLibrary.DefaultExport(DefaultFrameLibrary.class)
public abstract class FrameLibrary extends Library {
    /**
     * Default constructor.
     * 
     * @since 20.1
     */
    protected FrameLibrary() {
    }

    /**
     * Default implementation of the {@code FrameLibrary}. Provides the same view of local variables
     * as available to debugger.
     * 
     * @return a shared instance of the library
     * @since 20.1
     */
    public static FrameLibrary getUncached() {
        return UncachedDefault.DEFAULT;
    }

    /**
     * Reads a value of a local variable.
     * 
     * @param env location, environment, etc. to read values from
     * @param member the name of the variable to read
     * @return value of the variable
     * @throws UnknownIdentifierException thrown when the member is unknown
     * @since 20.1
     */
    public Object readMember(
                    Query env,
                    String member) throws UnknownIdentifierException {
        return getUncached().readMember(env, member);
    }

    /**
     * Assigns new value to an existing local variable.
     * 
     * @param env location, environment, etc. to read values from
     * @param member the name of the variable to modify
     * @param value new value for the variable
     * @throws com.oracle.truffle.api.interop.UnknownIdentifierException if the variable doesn't
     *             exist
     * @throws com.oracle.truffle.api.interop.UnsupportedTypeException if the type isn't appropriate
     * @since 20.1
     */
    public void writeMember(Query env, String member, Object value) throws UnknownIdentifierException, UnsupportedTypeException {
        getUncached().writeMember(env, member, value);
    }

    /**
     * Collect names of local variables.
     * 
     * @param env location, environment, etc. to read values from
     * @param names collection to add the names to
     * @throws InteropException thrown when something goes wrong
     * @since 20.1
     */
    public void collectNames(
                    Query env,
                    Set<String> names) throws InteropException {
        getUncached().collectNames(env, names);
    }

    /**
     * Holds location, environment, etc. Used to provide necessary information for implementation of
     * individual {@link FrameLibrary} messages like
     * {@link FrameLibrary#collectNames(com.oracle.truffle.tools.agentscript.FrameLibrary.Query, java.util.Set)}
     * and co.
     * 
     * @since 20.1
     */
    public final class Query {
        private final Node where;
        private final Frame frame;
        private final TruffleInstrument.Env env;

        Query(Node where, Frame frame, TruffleInstrument.Env env) {
            this.where = where;
            this.frame = frame;
            this.env = env;
        }

        /**
         * Access to current and enclosing {@link Scope}.
         * 
         * @return iterable providing access to the frames.
         * @since 20.1
         */
        public Iterable<Scope> findLocalScopes() {
            return env != null ? env.findLocalScopes(where, frame) : Collections.emptySet();
        }

        /**
         * Currently active frame to read values from.
         * 
         * @return the currently active frame
         * @since 20.1
         */
        public Frame frame() {
            return frame;
        }
    }

    static {
        AccessorFrameLibrary accessor = new AccessorFrameLibrary() {
            @Override
            protected Query create(Node where, Frame frame, TruffleInstrument.Env env) {
                return UncachedDefault.DEFAULT.new Query(where, frame, env);
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
        public void writeMember(Query env, String member, Object value) throws UnknownIdentifierException, UnsupportedTypeException {
            InteropLibrary iop = InteropLibrary.getFactory().getUncached();
            for (Scope scope : env.findLocalScopes()) {
                if (scope == null) {
                    continue;
                }
                if (writeMemberImpl(member, value, scope.getVariables(), iop)) {
                    return;
                }
                if (writeMemberImpl(member, value, scope.getArguments(), iop)) {
                    return;
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

        static boolean writeMemberImpl(String name, Object value, Object map, InteropLibrary iop) throws UnknownIdentifierException, UnsupportedTypeException {
            if (map != null && iop.hasMembers(map)) {
                try {
                    iop.writeMember(map, name, value);
                    return true;
                } catch (UnsupportedMessageException ex) {
                    return false;
                }
            }
            return false;
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
