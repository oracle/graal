/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
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
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.regex.AbstractConstantKeysObject;
import com.oracle.truffle.regex.AbstractRegexObject;
import com.oracle.truffle.regex.RegexObject;
import com.oracle.truffle.regex.runtime.nodes.DispatchNode;
import com.oracle.truffle.regex.runtime.nodes.ToIntNode;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.util.EmptyArrays;
import com.oracle.truffle.regex.util.TruffleReadOnlyKeysArray;

/**
 * {@link RegexResult} is a {@link TruffleObject} that represents the result of matching a regular
 * expression against a string. It can be obtained as the result of a {@link RegexObject}'s
 * {@code exec} method and has the following properties:
 * <ol>
 * <li>{@code boolean isMatch}: {@code true} if a match was found, {@code false} otherwise.</li>
 * <li>{@link TruffleObject} {@code getStart(int groupNumber)}: returns the position where the
 * beginning of the capture group with the given number was found. If the result is no match, the
 * returned value is undefined. Capture group number {@code 0} denotes the boundaries of the entire
 * expression. If no match was found for a particular capture group, the returned value is
 * {@code -1}.</li>
 * <li>{@link TruffleObject} {@code end}: returns the position where the end of the capture group
 * with the given number was found. If the result is no match, the returned value is undefined.
 * Capture group number {@code 0} denotes the boundaries of the entire expression. If no match was
 * found for a particular capture group, the returned value is {@code -1}.</li>
 * <li>{@code int lastGroup}: The index of the last capture group that was matched. -1 if no capture
 * group was matched. This property is only tracked for Python regular expressions. For other
 * flavors of regular expressions, this always has the value -1.</li>
 * </ol>
 * </li>
 */
@ExportLibrary(InteropLibrary.class)
public final class RegexResult extends AbstractConstantKeysObject {

    static final String PROP_IS_MATCH = "isMatch";
    static final String PROP_GET_START = "getStart";
    static final String PROP_GET_END = "getEnd";
    static final String PROP_LAST_GROUP = "lastGroup";

    private static final TruffleReadOnlyKeysArray KEYS = new TruffleReadOnlyKeysArray(PROP_IS_MATCH, PROP_GET_START, PROP_GET_END, PROP_LAST_GROUP);

    private final TruffleString input;
    private final int fromIndex;
    private final int regionFrom;
    private final int regionTo;
    private final int start;
    private final int end;
    private int[] result;

    private final CallTarget lazyCallTarget;

    protected RegexResult(TruffleString input, int fromIndex, int regionFrom, int regionTo, int start, int end, int[] result, CallTarget lazyCallTarget) {
        this.input = input;
        this.fromIndex = fromIndex;
        this.regionFrom = regionFrom;
        this.regionTo = regionTo;
        this.start = start;
        this.end = end;
        this.result = result;
        this.lazyCallTarget = lazyCallTarget;
    }

    private static final RegexResult NO_MATCH_RESULT = new RegexResult(null, -1, -1, -1, -1, -1, EmptyArrays.INT, null);
    private static final RegexResult BOOLEAN_MATCH_RESULT = new RegexResult(null, -1, -1, -1, -1, -1, EmptyArrays.INT, null);

    public static RegexResult getNoMatchInstance() {
        return NO_MATCH_RESULT;
    }

    public static RegexResult getBooleanMatchInstance() {
        return BOOLEAN_MATCH_RESULT;
    }

    public static RegexResult create(int start, int end) {
        return new RegexResult(null, -1, 0, 0, 0, 0, new int[]{start, end}, null);
    }

    public static RegexResult create(int[] result) {
        assert result != null && result.length >= 2;
        return new RegexResult(null, -1, 0, 0, 0, 0, result, null);
    }

    public static RegexResult createFromExecutorResult(Object executorResult) {
        if (executorResult == null) {
            return RegexResult.getNoMatchInstance();
        }
        return RegexResult.create((int[]) executorResult);
    }

    public static RegexResult createLazy(TruffleString input, int fromIndex, int regionFrom, int regionTo, int start, int end, CallTarget lazyCallTarget) {
        return new RegexResult(input, fromIndex, regionFrom, regionTo, start, end, null, lazyCallTarget);
    }

    public TruffleString getInput() {
        return input;
    }

    public int getFromIndex() {
        return fromIndex;
    }

    public int getRegionFrom() {
        return regionFrom;
    }

    public int getRegionTo() {
        return regionTo;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public void setResult(int[] result) {
        this.result = result;
    }

    public int getStart(int groupNumber) {
        int index = Group.groupNumberToBoundaryIndexStart(groupNumber);
        return groupNumber >= result.length >> 1 ? -1 : result[index];
    }

    public int getEnd(int groupNumber) {
        int index = Group.groupNumberToBoundaryIndexEnd(groupNumber);
        return groupNumber >= result.length >> 1 ? -1 : result[index];
    }

    public int getLastGroup() {
        return (result.length & 1) == 0 ? -1 : result[result.length - 1];
    }

    @ExportMessage
    @Override
    public Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return KEYS;
    }

    @ExportMessage
    abstract static class ReadMember {

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol == cachedSymbol", "cachedSymbol.equals(PROP_IS_MATCH)"}, limit = "2")
        static boolean isMatchIdentity(RegexResult receiver, String symbol,
                        @Cached("symbol") String cachedSymbol) {
            return receiver != getNoMatchInstance();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol.equals(cachedSymbol)", "cachedSymbol.equals(PROP_IS_MATCH)"}, limit = "2", replaces = "isMatchIdentity")
        static boolean isMatchEquals(RegexResult receiver, String symbol,
                        @Cached("symbol") String cachedSymbol) {
            return receiver != getNoMatchInstance();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol == cachedSymbol", "cachedSymbol.equals(PROP_GET_START)"}, limit = "2")
        static RegexResultGetStartMethod getStartIdentity(RegexResult receiver, String symbol,
                        @Cached("symbol") String cachedSymbol) {
            return new RegexResultGetStartMethod(receiver);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol.equals(cachedSymbol)", "cachedSymbol.equals(PROP_GET_START)"}, limit = "2", replaces = "getStartIdentity")
        static RegexResultGetStartMethod getStartEquals(RegexResult receiver, String symbol,
                        @Cached("symbol") String cachedSymbol) {
            return new RegexResultGetStartMethod(receiver);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol == cachedSymbol", "cachedSymbol.equals(PROP_GET_END)"}, limit = "2")
        static RegexResultGetEndMethod getEndIdentity(RegexResult receiver, String symbol,
                        @Cached("symbol") String cachedSymbol) {
            return new RegexResultGetEndMethod(receiver);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol.equals(cachedSymbol)", "cachedSymbol.equals(PROP_GET_END)"}, limit = "2", replaces = "getEndIdentity")
        static RegexResultGetEndMethod getEndEquals(RegexResult receiver, String symbol,
                        @Cached("symbol") String cachedSymbol) {
            return new RegexResultGetEndMethod(receiver);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol == cachedSymbol", "cachedSymbol.equals(PROP_LAST_GROUP)"}, limit = "2")
        static int lastGroupIdentity(RegexResult receiver, String symbol,
                        @Cached("symbol") String cachedSymbol,
                        @Cached @Shared RegexResultGetLastGroupNode getLastGroupNode) {
            return getLastGroupNode.execute(receiver);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol.equals(cachedSymbol)", "cachedSymbol.equals(PROP_LAST_GROUP)"}, limit = "2", replaces = "lastGroupIdentity")
        static int lastGroupEquals(RegexResult receiver, String symbol,
                        @Cached("symbol") String cachedSymbol,
                        @Cached @Shared RegexResultGetLastGroupNode getLastGroupNode) {
            return getLastGroupNode.execute(receiver);
        }

        @ReportPolymorphism.Megamorphic
        @Specialization(replaces = {"isMatchEquals", "getStartEquals", "getEndEquals"})
        static Object readGeneric(RegexResult receiver, String symbol,
                        @Cached @Shared RegexResultGetLastGroupNode getLastGroupNode) throws UnknownIdentifierException {
            switch (symbol) {
                case PROP_IS_MATCH:
                    return receiver != getNoMatchInstance();
                case PROP_GET_START:
                    return new RegexResultGetStartMethod(receiver);
                case PROP_GET_END:
                    return new RegexResultGetEndMethod(receiver);
                case PROP_LAST_GROUP:
                    return getLastGroupNode.execute(receiver);
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw UnknownIdentifierException.create(symbol);
            }
        }
    }

    @ExportMessage
    abstract static class IsMemberReadable {

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol == cachedSymbol", "result"}, limit = "4")
        static boolean cacheIdentity(RegexResult receiver, String symbol,
                        @Cached("symbol") String cachedSymbol,
                        @Cached("isReadable(receiver, cachedSymbol)") boolean result) {
            return result;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol.equals(cachedSymbol)", "result"}, limit = "4", replaces = "cacheIdentity")
        static boolean cacheEquals(RegexResult receiver, String symbol,
                        @Cached("symbol") String cachedSymbol,
                        @Cached("isReadable(receiver, cachedSymbol)") boolean result) {
            return result;
        }

        @SuppressWarnings("unused")
        @Specialization(replaces = "cacheEquals")
        static boolean isReadable(RegexResult receiver, String symbol) {
            return KEYS.contains(symbol);
        }
    }

    @Override
    public TruffleReadOnlyKeysArray getKeys() {
        return KEYS;
    }

    @Override
    public boolean isMemberReadableImpl(String symbol) {
        switch (symbol) {
            case PROP_IS_MATCH:
            case PROP_GET_START:
            case PROP_GET_END:
            case PROP_LAST_GROUP:
                return true;
            default:
                return false;
        }
    }

    @Override
    public Object readMemberImpl(String symbol) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw CompilerDirectives.shouldNotReachHere();
    }

    @ExportLibrary(InteropLibrary.class)
    static final class RegexResultGetStartMethod extends AbstractRegexObject {

        private final RegexResult result;

        RegexResultGetStartMethod(RegexResult result) {
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
                throw ArityException.create(1, 1, args.length);
            }
            return getStartNode.execute(result, toIntNode.execute(args[0]));
        }

        @TruffleBoundary
        @Override
        public String toString() {
            return "TRegexResultGetStartMethod{" + "result=" + result + '}';
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class RegexResultGetEndMethod extends AbstractRegexObject {

        private final RegexResult result;

        RegexResultGetEndMethod(RegexResult result) {
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
                throw ArityException.create(1, 1, args.length);
            }
            return getEndNode.execute(result, toIntNode.execute(args[0]));
        }

        @TruffleBoundary
        @Override
        public String toString() {
            return "TRegexResultGetEndMethod{" + "result=" + result + '}';
        }
    }

    @ExportMessage
    abstract static class IsMemberInvocable {

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol == cachedSymbol", "result"}, limit = "2")
        static boolean cacheIdentity(RegexResult receiver, String symbol,
                        @Cached("symbol") String cachedSymbol,
                        @Cached("isInvocable(receiver, cachedSymbol)") boolean result) {
            return result;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol.equals(cachedSymbol)", "result"}, limit = "2", replaces = "cacheIdentity")
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

    @ExportMessage
    Object invokeMember(String member, Object[] args,
                    @Cached ToIntNode toIntNode,
                    @Cached InvokeCacheNode invokeCache) throws UnknownIdentifierException, ArityException, UnsupportedTypeException {
        if (args.length != 1) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw ArityException.create(1, 1, args.length);
        }
        return invokeCache.execute(this, member, toIntNode.execute(args[0]));
    }

    @ImportStatic(RegexResult.class)
    @GenerateUncached
    @GenerateInline(false)
    abstract static class InvokeCacheNode extends Node {

        abstract Object execute(RegexResult receiver, String symbol, int groupNumber) throws UnknownIdentifierException;

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol == cachedSymbol", "cachedSymbol.equals(PROP_GET_START)"}, limit = "2")
        static Object getStartIdentity(RegexResult receiver, String symbol, int groupNumber,
                        @Cached("symbol") String cachedSymbol,
                        @Cached @Shared RegexResultGetStartNode getStartNode) {
            return getStartNode.execute(receiver, groupNumber);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol.equals(cachedSymbol)", "cachedSymbol.equals(PROP_GET_START)"}, limit = "2", replaces = "getStartIdentity")
        static Object getStartEquals(RegexResult receiver, String symbol, int groupNumber,
                        @Cached("symbol") String cachedSymbol,
                        @Cached @Shared RegexResultGetStartNode getStartNode) {
            return getStartNode.execute(receiver, groupNumber);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol == cachedSymbol", "cachedSymbol.equals(PROP_GET_END)"}, limit = "2")
        static Object getEndIdentity(RegexResult receiver, String symbol, int groupNumber,
                        @Cached("symbol") String cachedSymbol,
                        @Cached @Shared RegexResultGetEndNode getEndNode) {
            return getEndNode.execute(receiver, groupNumber);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol.equals(cachedSymbol)", "cachedSymbol.equals(PROP_GET_END)"}, limit = "2", replaces = "getEndIdentity")
        static Object getEndEquals(RegexResult receiver, String symbol, int groupNumber,
                        @Cached("symbol") String cachedSymbol,
                        @Cached @Shared RegexResultGetEndNode getEndNode) {
            return getEndNode.execute(receiver, groupNumber);
        }

        @ReportPolymorphism.Megamorphic
        @Specialization(replaces = {"getStartEquals", "getEndEquals"})
        static Object invokeGeneric(RegexResult receiver, String symbol, int groupNumber,
                        @Cached @Shared RegexResultGetStartNode getStartNode,
                        @Cached @Shared RegexResultGetEndNode getEndNode) throws UnknownIdentifierException {
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

    @TruffleBoundary
    public void debugForceEvaluation() {
        CompilerAsserts.neverPartOfCompilation();
        assert this != getNoMatchInstance();
        if (result == null) {
            lazyCallTarget.call(this);
        }
        assert result != null;
    }

    private static final int INVALID_RESULT_INDEX = -1;

    @GenerateInline(false)
    @GenerateUncached
    abstract static class RegexResultGetEndNode extends Node {

        abstract int execute(Object receiver, int groupNumber);

        @Specialization
        int doResult(RegexResult receiver, int groupNumber,
                        @Cached InlinedBranchProfile lazyProfile,
                        @Cached DispatchNode getIndicesCall) {
            if (receiver.result == null) {
                assert receiver.lazyCallTarget != null;
                lazyProfile.enter(this);
                getIndicesCall.execute(this, receiver.lazyCallTarget, receiver);
            }
            int i = Group.groupNumberToBoundaryIndexEnd(groupNumber);
            return i < 0 || i >= receiver.result.length ? INVALID_RESULT_INDEX : receiver.result[i];
        }
    }

    @GenerateInline(false)
    @GenerateUncached
    public abstract static class RegexResultGetStartNode extends Node {

        public abstract int execute(Object receiver, int groupNumber);

        @Specialization
        int doResult(RegexResult receiver, int groupNumber,
                        @Cached InlinedBranchProfile lazyProfile,
                        @Cached DispatchNode getIndicesCall) {
            if (receiver.result == null) {
                assert receiver.lazyCallTarget != null;
                lazyProfile.enter(this);
                getIndicesCall.execute(this, receiver.lazyCallTarget, receiver);
            }
            int i = Group.groupNumberToBoundaryIndexStart(groupNumber);
            return i < 0 || i >= receiver.result.length ? INVALID_RESULT_INDEX : receiver.result[i];
        }

        public static RegexResultGetStartNode getUncached() {
            return RegexResultFactory.RegexResultGetStartNodeGen.getUncached();
        }
    }

    @GenerateInline(false)
    @GenerateUncached
    public abstract static class RegexResultGetLastGroupNode extends Node {

        public abstract int execute(Object receiver);

        @Specialization
        int doResult(RegexResult receiver,
                        @Cached InlinedBranchProfile lazyProfile,
                        @Cached DispatchNode getIndicesCall) {
            if (receiver.result == null) {
                assert receiver.lazyCallTarget != null;
                lazyProfile.enter(this);
                getIndicesCall.execute(this, receiver.lazyCallTarget, receiver);
            }
            return receiver.getLastGroup();
        }
    }

    @TruffleBoundary
    @Override
    public String toString() {
        if (this == getNoMatchInstance()) {
            return "NO_MATCH";
        }
        if (result == null) {
            return "[ _lazy_ ]";
        }
        return Arrays.toString(result);
    }

    @TruffleBoundary
    @ExportMessage
    @Override
    public Object toDisplayString(boolean allowSideEffects) {
        if (allowSideEffects) {
            debugForceEvaluation();
            return "TRegexResult" + this;
        }
        return "TRegexResult";
    }
}
