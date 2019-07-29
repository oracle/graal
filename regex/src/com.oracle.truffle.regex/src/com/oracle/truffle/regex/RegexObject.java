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
package com.oracle.truffle.regex;

import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.result.NoMatchResult;
import com.oracle.truffle.regex.result.RegexResult;
import com.oracle.truffle.regex.runtime.nodes.ExpectStringOrTruffleObjectNode;
import com.oracle.truffle.regex.runtime.nodes.StringEqualsNode;
import com.oracle.truffle.regex.runtime.nodes.ToLongNode;
import com.oracle.truffle.regex.tregex.parser.flavors.PythonFlags;
import com.oracle.truffle.regex.util.TruffleNull;
import com.oracle.truffle.regex.util.TruffleReadOnlyKeysArray;
import com.oracle.truffle.regex.util.TruffleReadOnlyMap;

/**
 * {@link RegexObject} represents a compiled regular expression that can be used to match against
 * input strings. It is the result of executing a {@link RegexEngine}. It exposes the following
 * three properties:
 * <ol>
 * <li>{@link String} {@code pattern}: the source of the compiled regular expression</li>
 * <li>{@link TruffleObject} {@code flags}: the set of flags passed to the regular expression
 * compiler. The type differs based on the flavor of regular expressions used:
 * <ul>
 * <li>{@link RegexFlags} if the flavor was {@code ECMAScript}</li>
 * <li>{@link PythonFlags} if the flavor was {@code PythonStr} or {@code PythonBytes}</li>
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
 * </ol>
 * <p>
 */
@ExportLibrary(InteropLibrary.class)
public final class RegexObject extends AbstractConstantKeysObject {

    static final String PROP_EXEC = "exec";
    private static final String PROP_PATTERN = "pattern";
    private static final String PROP_FLAGS = "flags";
    private static final String PROP_GROUP_COUNT = "groupCount";
    private static final String PROP_GROUPS = "groups";
    private static final TruffleReadOnlyKeysArray KEYS = new TruffleReadOnlyKeysArray(PROP_EXEC, PROP_PATTERN, PROP_FLAGS, PROP_GROUP_COUNT, PROP_GROUPS);

    private final RegexCompiler compiler;
    private final RegexSource source;
    private final TruffleObject flags;
    private final int numberOfCaptureGroups;
    private final TruffleObject namedCaptureGroups;
    private Object compiledRegexObject;

    public RegexObject(RegexCompiler compiler, RegexSource source, TruffleObject flags, int numberOfCaptureGroups, Map<String, Integer> namedCaptureGroups) {
        this.compiler = compiler;
        this.source = source;
        this.flags = flags;
        this.numberOfCaptureGroups = numberOfCaptureGroups;
        this.namedCaptureGroups = namedCaptureGroups != null ? new TruffleReadOnlyMap(namedCaptureGroups) : TruffleNull.INSTANCE;
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

    public Object getCompiledRegexObject() {
        if (compiledRegexObject == null) {
            compiledRegexObject = compileRegex();
        }
        return compiledRegexObject;
    }

    @TruffleBoundary
    private Object compileRegex() {
        return compiler.compile(source);
    }

    public void setCompiledRegexObject(TruffleObject compiledRegexObject) {
        this.compiledRegexObject = compiledRegexObject;
    }

    public RegexObjectExecMethod getExecMethod() {
        // this allocation should get virtualized and optimized away by graal
        return new RegexObjectExecMethod(this);
    }

    @Override
    public TruffleReadOnlyKeysArray getKeys() {
        return KEYS;
    }

    @Override
    public Object readMemberImpl(String symbol) throws UnknownIdentifierException {
        switch (symbol) {
            case PROP_EXEC:
                return getExecMethod();
            case PROP_PATTERN:
                return getSource().getPattern();
            case PROP_FLAGS:
                return getFlags();
            case PROP_GROUP_COUNT:
                return getNumberOfCaptureGroups();
            case PROP_GROUPS:
                return getNamedCaptureGroups();
            default:
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.create(symbol);
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isMemberInvocable(String member,
                    @Shared("isExecPropNode") @Cached StringEqualsNode isExecPropNode) {
        return isExecPropNode.execute(member, PROP_EXEC);
    }

    @ExportMessage
    Object invokeMember(String member, Object[] args,
                    @Shared("isExecPropNode") @Cached StringEqualsNode isExecPropNode,
                    @Cached GetCompiledRegexNode getCompiledRegexNode,
                    @Cached ExpectStringOrTruffleObjectNode expectStringOrTruffleObjectNode,
                    @Cached ToLongNode toLongNode,
                    @Cached ExecCompiledRegexNode execNode) throws UnknownIdentifierException, ArityException, UnsupportedTypeException, UnsupportedMessageException {
        if (!isExecPropNode.execute(member, PROP_EXEC)) {
            CompilerDirectives.transferToInterpreter();
            throw UnknownIdentifierException.create(member);
        }
        if (args.length != 2) {
            CompilerDirectives.transferToInterpreter();
            throw ArityException.create(2, args.length);
        }
        Object input = expectStringOrTruffleObjectNode.execute(args[0]);
        long fromIndex = toLongNode.execute(args[1]);
        if (fromIndex > Integer.MAX_VALUE) {
            return NoMatchResult.getInstance();
        }
        return execNode.execute(getCompiledRegexNode.execute(this), input, (int) fromIndex);
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class RegexObjectExecMethod implements RegexLanguageObject {

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
                        @Cached GetCompiledRegexNode getCompiledRegexNode,
                        @Cached ExpectStringOrTruffleObjectNode expectStringOrTruffleObjectNode,
                        @Cached ToLongNode toLongNode,
                        @Cached ExecCompiledRegexNode execNode) throws ArityException, UnsupportedTypeException, UnsupportedMessageException {
            if (args.length != 2) {
                CompilerDirectives.transferToInterpreter();
                throw ArityException.create(2, args.length);
            }
            Object input = expectStringOrTruffleObjectNode.execute(args[0]);
            long fromIndex = toLongNode.execute(args[1]);
            if (fromIndex > Integer.MAX_VALUE) {
                return NoMatchResult.getInstance();
            }
            try {
                return execNode.execute(getCompiledRegexNode.execute(getRegexObject()), input, (int) fromIndex);
            } catch (UnknownIdentifierException e) {
                CompilerDirectives.transferToInterpreter();
                throw new RuntimeException(e);
            }
        }
    }

    @ReportPolymorphism
    @GenerateUncached
    abstract static class GetCompiledRegexNode extends Node {

        abstract Object execute(RegexObject receiver);

        @SuppressWarnings("unused")
        @Specialization(guards = "receiver == cachedReceiver", limit = "4")
        static Object executeFixed(RegexObject receiver,
                        @Cached("receiver") RegexObject cachedReceiver,
                        @Cached("receiver.getCompiledRegexObject()") Object cachedCompiledRegex) {
            return cachedCompiledRegex;
        }

        @Specialization(replaces = "executeFixed")
        static Object executeVarying(RegexObject receiver) {
            return receiver.getCompiledRegexObject();
        }
    }

    @ReportPolymorphism
    @ImportStatic(RegexObject.class)
    @GenerateUncached
    abstract static class ExecCompiledRegexNode extends Node {

        abstract Object execute(Object receiver, Object input, int fromIndex)
                        throws UnsupportedMessageException, ArityException, UnsupportedTypeException, UnknownIdentifierException;

        @SuppressWarnings("unused")
        @Specialization(guards = "receiver == cachedReceiver", limit = "4")
        static Object executeTRegexFixed(CompiledRegexObject receiver, Object input, int fromIndex,
                        @Cached("receiver") CompiledRegexObject cachedReceiver,
                        @Cached("create(cachedReceiver.getCallTarget())") DirectCallNode directCallNode) {
            return directCallNode.call(input, fromIndex);
        }

        @Specialization(replaces = "executeTRegexFixed")
        static Object executeTRegexVarying(CompiledRegexObject receiver, Object input, int fromIndex,
                        @Cached IndirectCallNode indirectCallNode) {
            return indirectCallNode.call(receiver.getCallTarget(), input, fromIndex);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "receivers.isMemberInvocable(receiver, PROP_EXEC)", limit = "4")
        static Object executeForeign(TruffleObject receiver, Object input, int fromIndex,
                        @CachedLibrary("receiver") InteropLibrary receivers) throws UnsupportedMessageException, ArityException, UnsupportedTypeException, UnknownIdentifierException {
            return receivers.invokeMember(receiver, "exec", input, fromIndex);
        }
    }
}
