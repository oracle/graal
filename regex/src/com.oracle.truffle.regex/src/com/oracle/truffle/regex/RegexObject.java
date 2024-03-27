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
package com.oracle.truffle.regex;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
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
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.literal.LiteralRegexExecNode;
import com.oracle.truffle.regex.result.RegexResult;
import com.oracle.truffle.regex.tregex.TRegexCompilationRequest;
import com.oracle.truffle.regex.tregex.parser.flavors.PythonFlags;
import com.oracle.truffle.regex.tregex.parser.flavors.RubyFlags;
import com.oracle.truffle.regex.util.TruffleReadOnlyKeysArray;

/**
 * {@link RegexObject} represents a compiled regular expression that can be used to match against
 * input strings. It is the result of a call to
 * {@link RegexLanguage#parse(TruffleLanguage.ParsingRequest)}. It exposes the following three
 * properties:
 * <ol>
 * <li>{@link String} {@code pattern}: the source of the compiled regular expression</li>
 * <li>{@link TruffleObject} {@code flags}: the set of flags passed to the regular expression
 * compiler. The type differs based on the flavor of regular expressions used:
 * <ul>
 * <li>{@link RegexFlags} if the flavor was {@code ECMAScript}</li>
 * <li>{@link PythonFlags} if the flavor was {@code Python}</li>
 * <li>{@link RubyFlags} if the flavor was {@code Ruby}</li>
 * </ul>
 * </li>
 * <li>{@code int groupCount}: number of capture groups present in the regular expression, including
 * group 0.</li>
 * <li>{@link RegexObjectExecMethod} {@code exec}: an executable method that matches the compiled
 * regular expression against a string. The method accepts two parameters:
 * <ol>
 * <li>{@link Object} {@code input}: the character sequence to search in. This may either be a
 * {@link String} or a {@link TruffleObject} that responds to
 * {@link InteropLibrary#hasArrayElements(Object)} and returns {@link Character}s on indexed
 * {@link InteropLibrary#readArrayElement(Object, long)} requests.</li>
 * <li>{@link Number} {@code fromIndex}: the position to start searching from. This argument will be
 * cast to {@code int}, since a {@link String} can not be longer than {@link Integer#MAX_VALUE}. If
 * {@code fromIndex} is greater than {@link Integer#MAX_VALUE}, this method will immediately return
 * NO_MATCH.</li>
 * </ol>
 * The return value is a {@link RegexResult}. The contents of the {@code exec} can be compiled
 * lazily and so its first invocation might involve a longer delay as the regular expression is
 * compiled on the fly.
 * <li>{@link TruffleObject} {@code groups}: a Truffle object that has a member for every named
 * capture group. The value of the member depends on the flavor of regular expressions. In flavors
 * where all capture groups must have a unique name, the value of the member is a single integer,
 * the index of the group that bears the member's name. In flavors where it is possible to have
 * multiple groups of the same name (as in Ruby), the value of the member has array elements that
 * give the indices of the groups with the member's name.</li>
 * <li>{@code boolean isBacktracking}: whether or not matching with this regular expression will use
 * backtracking when matching, which could result in an exponential runtime in the worst case
 * scenario</li>
 * </ol>
 * <p>
 */
@ExportLibrary(InteropLibrary.class)
public final class RegexObject extends AbstractConstantKeysObject {

    static final String PROP_EXEC = "exec";
    static final String PROP_EXEC_BOOLEAN = "execBoolean";
    private static final String PROP_PATTERN = "pattern";
    private static final String PROP_FLAGS = "flags";
    private static final String PROP_GROUP_COUNT = "groupCount";
    private static final String PROP_GROUPS = "groups";
    private static final String PROP_IS_BACKTRACKING = "isBacktracking";
    private static final TruffleReadOnlyKeysArray KEYS = new TruffleReadOnlyKeysArray(PROP_EXEC, PROP_EXEC_BOOLEAN, PROP_PATTERN, PROP_FLAGS, PROP_GROUP_COUNT, PROP_GROUPS, PROP_IS_BACKTRACKING);

    private final RegexLanguage language;
    private final RegexSource source;
    private final AbstractRegexObject flags;
    private final int numberOfCaptureGroups;
    private final AbstractRegexObject namedCaptureGroups;
    @CompilationFinal private RegexRootNode execRootNode;
    @CompilationFinal private RegexRootNode execBooleanRootNode;
    private final boolean backtracking;

    public RegexObject(RegexExecNode execNode, RegexSource source, AbstractRegexObject flags, int numberOfCaptureGroups, AbstractRegexObject namedCaptureGroups) {
        this.language = execNode.getRegexLanguage();
        this.source = source;
        this.flags = flags;
        this.numberOfCaptureGroups = numberOfCaptureGroups;
        this.namedCaptureGroups = namedCaptureGroups;
        RegexRootNode rootNode = new RegexRootNode(execNode.getRegexLanguage(), execNode);
        if (execNode.isBooleanMatch()) {
            this.execBooleanRootNode = rootNode;
        } else {
            this.execRootNode = rootNode;
        }
        this.backtracking = execNode.isBacktracking();
    }

    public RegexSource getSource() {
        return source;
    }

    public TruffleObject getFlags() {
        return flags;
    }

    public int getNumberOfCaptureGroups() {
        return numberOfCaptureGroups;
    }

    public TruffleObject getNamedCaptureGroups() {
        return namedCaptureGroups;
    }

    public String getLabel() {
        return execRootNode == null ? getLabel(execBooleanRootNode) : getLabel(execRootNode);
    }

    private static String getLabel(RegexRootNode rootNode) {
        RegexExecNode execNode = (RegexExecNode) rootNode.getBodyUnwrapped();
        if (execNode instanceof LiteralRegexExecNode) {
            return "literal";
        } else if (execNode.isBacktracking()) {
            return "backtracker";
        } else if (execNode.isNFA()) {
            return "nfa";
        } else {
            return "dfa";
        }
    }

    public CallTarget getExecCallTarget() {
        if (execRootNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            execRootNode = new RegexRootNode(language, new TRegexCompilationRequest(language, execBooleanRootNode.getSource().withoutBooleanMatch()).compile());
        }
        return execRootNode.getCallTarget();
    }

    public CallTarget getExecBooleanCallTarget() {
        if (execBooleanRootNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            execBooleanRootNode = new RegexRootNode(language, new TRegexCompilationRequest(language, execRootNode.getSource().withBooleanMatch()).compile());
        }
        return execBooleanRootNode.getCallTarget();
    }

    public boolean isBacktracking() {
        return backtracking;
    }

    public RegexObjectExecMethod getExecMethod() {
        // this allocation should get virtualized and optimized away by graal
        return new RegexObjectExecMethod(this);
    }

    public RegexObjectExecBooleanMethod getExecBooleanMethod() {
        // this allocation should get virtualized and optimized away by graal
        return new RegexObjectExecBooleanMethod(this);
    }

    @Override
    public TruffleReadOnlyKeysArray getKeys() {
        return KEYS;
    }

    @Override
    public boolean isMemberReadableImpl(String symbol) {
        switch (symbol) {
            case PROP_EXEC:
            case PROP_EXEC_BOOLEAN:
            case PROP_PATTERN:
            case PROP_FLAGS:
            case PROP_GROUP_COUNT:
            case PROP_GROUPS:
            case PROP_IS_BACKTRACKING:
                return true;
            default:
                return false;
        }
    }

    @Override
    public Object readMemberImpl(String symbol) throws UnknownIdentifierException {
        switch (symbol) {
            case PROP_EXEC:
                return getExecMethod();
            case PROP_EXEC_BOOLEAN:
                return getExecBooleanMethod();
            case PROP_PATTERN:
                return getSource().getPattern();
            case PROP_FLAGS:
                return getFlags();
            case PROP_GROUP_COUNT:
                return getNumberOfCaptureGroups();
            case PROP_GROUPS:
                return getNamedCaptureGroups();
            case PROP_IS_BACKTRACKING:
                return isBacktracking();
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnknownIdentifierException.create(symbol);
        }
    }

    private static final String N_METHODS = "2";

    @ExportMessage
    abstract static class IsMemberInvocable {

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol == cachedSymbol", "result"}, limit = N_METHODS)
        static boolean cacheIdentity(RegexObject receiver, String symbol,
                        @Cached("symbol") String cachedSymbol,
                        @Cached("isInvocable(receiver, cachedSymbol)") boolean result) {
            return result;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol.equals(cachedSymbol)", "result"}, limit = N_METHODS, replaces = "cacheIdentity")
        static boolean cacheEquals(RegexObject receiver, String symbol,
                        @Cached("symbol") String cachedSymbol,
                        @Cached("isInvocable(receiver, cachedSymbol)") boolean result) {
            return result;
        }

        @SuppressWarnings("unused")
        @Specialization(replaces = "cacheEquals")
        static boolean isInvocable(RegexObject receiver, String symbol) {
            return PROP_EXEC.equals(symbol) || PROP_EXEC_BOOLEAN.equals(symbol);
        }
    }

    @ExportMessage
    Object invokeMember(String member, Object[] args,
                    @Cached InvokeCacheNode invokeCache)
                    throws UnknownIdentifierException, ArityException, UnsupportedTypeException, UnsupportedMessageException {
        checkArity(args);
        return invokeCache.execute(member, this, args);
    }

    @ImportStatic(RegexObject.class)
    @GenerateInline(false)
    @GenerateUncached
    abstract static class InvokeCacheNode extends Node {

        abstract Object execute(String symbol, RegexObject receiver, Object[] args)
                        throws UnsupportedMessageException, ArityException, UnsupportedTypeException, UnknownIdentifierException;

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol == cachedSymbol", "cachedSymbol.equals(PROP_EXEC)"}, limit = N_METHODS)
        Object execIdentity(String symbol, RegexObject receiver, Object[] args,
                        @Cached("symbol") String cachedSymbol,
                        @Cached @Shared ExecCompiledRegexNode execNode) throws UnsupportedMessageException, ArityException, UnsupportedTypeException {
            return execNode.execute(receiver.getExecCallTarget(), args);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol.equals(cachedSymbol)", "cachedSymbol.equals(PROP_EXEC)"}, limit = N_METHODS, replaces = "execIdentity")
        Object execEquals(String symbol, RegexObject receiver, Object[] args,
                        @Cached("symbol") String cachedSymbol,
                        @Cached @Shared ExecCompiledRegexNode execNode) throws UnsupportedMessageException, ArityException, UnsupportedTypeException {
            return execNode.execute(receiver.getExecCallTarget(), args);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol == cachedSymbol", "cachedSymbol.equals(PROP_EXEC_BOOLEAN)"}, limit = N_METHODS)
        boolean execBooleanIdentity(String symbol, RegexObject receiver, Object[] args,
                        @Cached("symbol") String cachedSymbol,
                        @Cached @Shared ExecCompiledRegexNode execBoolNode) throws UnsupportedMessageException, ArityException, UnsupportedTypeException {
            return execBoolNode.execute(receiver.getExecBooleanCallTarget(), args) != RegexResult.getNoMatchInstance();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"symbol.equals(cachedSymbol)", "cachedSymbol.equals(PROP_EXEC_BOOLEAN)"}, limit = N_METHODS, replaces = "execBooleanIdentity")
        boolean execBooleanEquals(String symbol, RegexObject receiver, Object[] args,
                        @Cached("symbol") String cachedSymbol,
                        @Cached @Shared ExecCompiledRegexNode execBoolNode) throws UnsupportedMessageException, ArityException, UnsupportedTypeException {
            return execBoolNode.execute(receiver.getExecBooleanCallTarget(), args) != RegexResult.getNoMatchInstance();
        }

        @ReportPolymorphism.Megamorphic
        @Specialization(replaces = {"execEquals", "execBooleanEquals"})
        static Object invokeGeneric(String symbol, RegexObject receiver, Object[] args,
                        @Cached @Shared ExecCompiledRegexNode execNode) throws UnsupportedMessageException, ArityException, UnsupportedTypeException, UnknownIdentifierException {
            switch (symbol) {
                case PROP_EXEC:
                    return execNode.execute(receiver.getExecCallTarget(), args);
                case PROP_EXEC_BOOLEAN:
                    return execNode.execute(receiver.getExecBooleanCallTarget(), args) != RegexResult.getNoMatchInstance();
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw UnknownIdentifierException.create(symbol);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class RegexObjectExecMethod extends AbstractRegexObject {

        private final RegexObject regex;

        public RegexObjectExecMethod(RegexObject regex) {
            this.regex = regex;
        }

        public RegexObject getRegexObject() {
            return regex;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] args,
                        @Cached ExecCompiledRegexNode execNode) throws ArityException, UnsupportedTypeException, UnsupportedMessageException {
            checkArity(args);
            return execNode.execute(getRegexObject().getExecCallTarget(), args);
        }

        @TruffleBoundary
        @Override
        public String toString() {
            return "TRegexObjectExecMethod{" + "regex=" + regex + '}';
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class RegexObjectExecBooleanMethod extends AbstractRegexObject {

        private final RegexObject regex;

        public RegexObjectExecBooleanMethod(RegexObject regex) {
            this.regex = regex;
        }

        public RegexObject getRegexObject() {
            return regex;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        boolean execute(Object[] args,
                        @Cached ExecCompiledRegexNode execNode) throws ArityException, UnsupportedTypeException, UnsupportedMessageException {
            checkArity(args);
            return execNode.execute(getRegexObject().getExecBooleanCallTarget(), args) != RegexResult.getNoMatchInstance();
        }

        @TruffleBoundary
        @Override
        public String toString() {
            return "TRegexObjectExecMethod{" + "regex=" + regex + '}';
        }
    }

    @ImportStatic(RegexObject.class)
    @GenerateInline(false)
    @GenerateUncached
    abstract static class ExecCompiledRegexNode extends Node {

        abstract Object execute(CallTarget receiver, Object[] args) throws UnsupportedMessageException, ArityException, UnsupportedTypeException;

        @SuppressWarnings("unused")
        @Specialization(guards = "receiver == cachedCallTarget", limit = "4")
        static Object doDirectCall(CallTarget receiver, Object[] args,
                        @Cached("receiver") CallTarget cachedCallTarget,
                        @Cached("create(cachedCallTarget)") DirectCallNode directCallNode) {
            return directCallNode.call(args);
        }

        @ReportPolymorphism.Megamorphic
        @Specialization(replaces = "doDirectCall")
        static Object doIndirectCall(CallTarget receiver, Object[] args,
                        @Cached IndirectCallNode indirectCallNode) {
            return indirectCallNode.call(receiver, args);
        }
    }

    private static void checkArity(Object[] args) throws ArityException {
        if (args.length != 2 && args.length != 5) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw ArityException.create(2, 5, args.length);
        }
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return "TRegexObject{source=" + source + '}';
    }
}
