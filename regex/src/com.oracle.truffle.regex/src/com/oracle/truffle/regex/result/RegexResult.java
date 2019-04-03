/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.result;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.regex.AbstractConstantKeysObject;
import com.oracle.truffle.regex.RegexLanguageObject;
import com.oracle.truffle.regex.RegexObject;
import com.oracle.truffle.regex.runtime.nodes.ToIntNode;
import com.oracle.truffle.regex.util.TruffleReadOnlyKeysArray;

/**
 * {@link RegexResult} is a {@link TruffleObject} that represents the result of matching a regular
 * expression against a string. It can be obtained as the result of a {@link RegexObject}'s
 * {@code exec} method and has the following properties:
 * <ol>
 * <li>{@code boolean isMatch}: {@code true} if a match was found, {@code false} otherwise.</li>
 * <li>{@code int groupCount}: number of capture groups present in the regular expression, including
 * group 0. If the result is no match, this property is undefined.</li>
 * <li>{@link TruffleObject} {@code getStart(int groupNumber)}: returns the position where the
 * beginning of the capture group with the given number was found. If the result is no match, the
 * returned value is undefined. Capture group number {@code 0} denotes the boundaries of the entire
 * expression. If no match was found for a particular capture group, the returned value is
 * {@code -1}.</li>
 * <li>{@link TruffleObject} {@code end}: returns the position where the end of the capture group
 * with the given number was found. If the result is no match, the returned value is undefined.
 * Capture group number {@code 0} denotes the boundaries of the entire expression. If no match was
 * found for a particular capture group, the returned value is {@code -1}.</li>
 * </ol>
 * </li>
 */
@ExportLibrary(InteropLibrary.class)
public abstract class RegexResult extends AbstractConstantKeysObject {

    static final String PROP_IS_MATCH = "isMatch";
    static final String PROP_GET_START = "getStart";
    static final String PROP_GET_END = "getEnd";

    private static final TruffleReadOnlyKeysArray KEYS = new TruffleReadOnlyKeysArray(PROP_IS_MATCH, PROP_GET_START, PROP_GET_END);

    @Override
    public TruffleReadOnlyKeysArray getKeys() {
        return KEYS;
    }

    @Override
    public final Object readMemberImpl(String symbol) throws UnknownIdentifierException {
        switch (symbol) {
            case PROP_IS_MATCH:
                return this != NoMatchResult.getInstance();
            case PROP_GET_START:
                return new RegexResultGetStartMethod(this);
            case PROP_GET_END:
                return new RegexResultGetEndMethod(this);
            default:
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.create(symbol);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public final class RegexResultGetStartMethod implements RegexLanguageObject {

        private final RegexResult result;

        public RegexResultGetStartMethod(RegexResult result) {
            this.result = result;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        int execute(Object[] args,
                        @Cached ToIntNode toIntNode,
                        @Cached RegexResultGetStartNode getStartNode) throws ArityException, UnsupportedTypeException {
            if (args.length != 1) {
                CompilerDirectives.transferToInterpreter();
                throw ArityException.create(1, args.length);
            }
            return getStartNode.execute(result, toIntNode.execute(args[0]));
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public final class RegexResultGetEndMethod implements RegexLanguageObject {

        private final RegexResult result;

        public RegexResultGetEndMethod(RegexResult result) {
            this.result = result;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        int execute(Object[] args,
                        @Cached ToIntNode toIntNode,
                        @Cached RegexResultGetEndNode getEndNode) throws ArityException, UnsupportedTypeException {
            if (args.length != 1) {
                CompilerDirectives.transferToInterpreter();
                throw ArityException.create(1, args.length);
            }
            return getEndNode.execute(result, toIntNode.execute(args[1]));
        }
    }

    @ExportMessage
    boolean isMemberInvocable(String member,
                    @Cached IsInvocableCacheNode cache,
                    @Shared("receiverProfile") @Cached("createIdentityProfile()") ValueProfile receiverProfile) {
        return cache.execute(receiverProfile.profile(this), member);
    }

    @ExportMessage
    Object invokeMember(String member, Object[] args,
                    @Cached ToIntNode toIntNode,
                    @Cached InvokeCacheNode invokeCache,
                    @Shared("receiverProfile") @Cached("createIdentityProfile()") ValueProfile receiverProfile) throws UnknownIdentifierException, ArityException, UnsupportedTypeException {
        if (args.length != 1) {
            CompilerDirectives.transferToInterpreter();
            throw ArityException.create(1, args.length);
        }
        return invokeCache.execute(receiverProfile.profile(this), member, toIntNode.execute(args[0]));
    }

    @GenerateUncached
    abstract static class IsInvocableCacheNode extends Node {

        abstract boolean execute(RegexResult receiver, String symbol);

        @SuppressWarnings("unused")
        @Specialization(guards = "symbol == cachedSymbol", limit = "2")
        static boolean cacheIdentity(RegexResult receiver, String symbol,
                        @Cached("symbol") String cachedSymbol,
                        @Cached("isInvocable(receiver, cachedSymbol)") boolean result) {
            return result;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "symbol.equals(cachedSymbol)", limit = "2", replaces = "cacheIdentity")
        static boolean cacheEquals(RegexResult receiver, String symbol,
                        @Cached("symbol") String cachedSymbol,
                        @Cached("isInvocable(receiver, cachedSymbol)") boolean result) {
            return result;
        }

        @SuppressWarnings("unused")
        @Specialization(replaces = "cacheEquals")
        static boolean isInvocable(RegexResult receiver, String symbol) {
            return PROP_GET_START.equals(symbol) || PROP_GET_END.equals(symbol);
        }
    }

    @ReportPolymorphism
    @ImportStatic(RegexResult.class)
    @GenerateUncached
    abstract static class InvokeCacheNode extends Node {

        abstract Object execute(RegexResult receiver, String symbol, int groupNumber) throws UnknownIdentifierException;

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol == cachedSymbol", "cachedSymbol.equals(PROP_GET_START)"}, limit = "2")
        Object getStartIdentity(RegexResult receiver, String symbol, int groupNumber,
                        @Cached("symbol") String cachedSymbol,
                        @Cached RegexResultGetStartNode getStartNode) {
            return getStartNode.execute(receiver, groupNumber);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol.equals(cachedSymbol)", "cachedSymbol.equals(PROP_GET_START)"}, limit = "2", replaces = "getStartIdentity")
        Object getStartEquals(RegexResult receiver, String symbol, int groupNumber,
                        @Cached("symbol") String cachedSymbol,
                        @Cached RegexResultGetStartNode getStartNode) {
            return getStartNode.execute(receiver, groupNumber);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol == cachedSymbol", "cachedSymbol.equals(PROP_GET_END)"}, limit = "2")
        Object getEndIdentity(RegexResult receiver, String symbol, int groupNumber,
                        @Cached("symbol") String cachedSymbol,
                        @Cached RegexResultGetEndNode getEndNode) {
            return getEndNode.execute(receiver, groupNumber);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol.equals(cachedSymbol)", "cachedSymbol.equals(PROP_GET_END)"}, limit = "2", replaces = "getEndIdentity")
        Object getEndEquals(RegexResult receiver, String symbol, int groupNumber,
                        @Cached("symbol") String cachedSymbol,
                        @Cached RegexResultGetEndNode getEndNode) {
            return getEndNode.execute(receiver, groupNumber);
        }

        @Specialization(replaces = {"getStartEquals", "getEndEquals"})
        static Object invokeGeneric(RegexResult receiver, String symbol, int groupNumber,
                        @Cached RegexResultGetStartNode getStartNode,
                        @Cached RegexResultGetEndNode getEndNode) throws UnknownIdentifierException {
            switch (symbol) {
                case PROP_GET_START:
                    return getStartNode.execute(receiver, groupNumber);
                case PROP_GET_END:
                    return getEndNode.execute(receiver, groupNumber);
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw UnknownIdentifierException.create(symbol);
            }
        }
    }
}
