/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.result;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
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
import com.oracle.truffle.regex.AbstractRegexObject;
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

    public abstract int getStart(int groupNumber);

    public abstract int getEnd(int groupNumber);

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
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnknownIdentifierException.create(symbol);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public final class RegexResultGetStartMethod extends AbstractRegexObject {

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
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(1, args.length);
            }
            return getStartNode.execute(result, toIntNode.execute(args[0]));
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public final class RegexResultGetEndMethod extends AbstractRegexObject {

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
                CompilerDirectives.transferToInterpreterAndInvalidate();
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
            CompilerDirectives.transferToInterpreterAndInvalidate();
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
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw UnknownIdentifierException.create(symbol);
            }
        }
    }
}
