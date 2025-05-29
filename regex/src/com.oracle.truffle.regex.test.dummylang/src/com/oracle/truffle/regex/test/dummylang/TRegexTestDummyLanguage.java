/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.test.dummylang;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexObject;
import com.oracle.truffle.regex.RegexSyntaxException;

@TruffleLanguage.Registration(name = TRegexTestDummyLanguage.NAME, id = TRegexTestDummyLanguage.ID, characterMimeTypes = TRegexTestDummyLanguage.MIME_TYPE, version = "0.1", dependentLanguages = RegexLanguage.ID)
public class TRegexTestDummyLanguage extends TruffleLanguage<TRegexTestDummyLanguage.DummyLanguageContext> {

    public static final String NAME = "REGEXDUMMYLANG";
    public static final String ID = "regexDummyLang";
    public static final String MIME_TYPE = "application/tregexdummy";
    public static final String BENCH_PREFIX = "__BENCH__";
    public static final String BENCH_CG_PREFIX = "__BENCH_CG__";

    @Override
    protected CallTarget parse(ParsingRequest parsingRequest) {
        String src = parsingRequest.getSource().getCharacters().toString();
        if (src.startsWith(BENCH_PREFIX)) {
            final Object regex = DummyLanguageContext.get(null).getEnv().parseInternal(
                            Source.newBuilder(RegexLanguage.ID, "BooleanMatch=true," + src.substring(BENCH_PREFIX.length()), parsingRequest.getSource().getName()).internal(true).build()).call();

            return new RootNode(this) {

                private final Object compiledRegex = regex;
                private final String name = parsingRequest.getSource().getName();

                @Child RegexBenchNode benchNode = TRegexTestDummyLanguageFactory.RegexBenchNodeGen.create();

                @Override
                public Object execute(VirtualFrame frame) {
                    Object[] args = frame.getArguments();
                    return benchNode.execute(this, compiledRegex, args[0], (int) args[1]);
                }

                @Override
                public String toString() {
                    return name + ' ' + ((RegexObject) compiledRegex).getLabel();
                }
            }.getCallTarget();
        }
        if (src.startsWith(BENCH_CG_PREFIX)) {
            final Object regex = DummyLanguageContext.get(null).getEnv().parseInternal(
                            Source.newBuilder(RegexLanguage.ID, src.substring(BENCH_CG_PREFIX.length()), parsingRequest.getSource().getName()).internal(true).build()).call();
            return new RootNode(this) {

                private final Object compiledRegex = regex;

                @Child RegexBenchCGNode benchNode = TRegexTestDummyLanguageFactory.RegexBenchCGNodeGen.create();

                @Override
                public Object execute(VirtualFrame frame) {
                    Object[] args = frame.getArguments();
                    return benchNode.execute(this, compiledRegex, args[0], (int) args[1]);
                }
            }.getCallTarget();
        }
        try {
            return DummyLanguageContext.get(null).getEnv().parseInternal(
                            Source.newBuilder(RegexLanguage.ID, src, parsingRequest.getSource().getName()).internal(true).build());
        } catch (RegexSyntaxException e) {
            throw e.withErrorCodeInMessage();
        }
    }

    @GenerateInline
    abstract static class RegexBenchNode extends Node {

        protected static final String EXEC = "execBoolean";

        abstract boolean execute(Node node, Object compiledRegex, Object input, int fromIndex);

        @Specialization(guards = "objs.isMemberInvocable(compiledRegex, EXEC)", limit = "3")
        static boolean run(Object compiledRegex, String input, int fromIndex,
                        @CachedLibrary("compiledRegex") InteropLibrary objs) {
            try {
                return (boolean) objs.invokeMember(compiledRegex, EXEC, input, fromIndex);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Specialization(guards = "objs.isMemberInvocable(compiledRegex, EXEC)", limit = "3")
        static boolean run(Object compiledRegex, TruffleString input, int fromIndex,
                        @CachedLibrary("compiledRegex") InteropLibrary objs) {
            try {
                return (boolean) objs.invokeMember(compiledRegex, EXEC, input, fromIndex);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

    }

    @GenerateInline
    abstract static class RegexBenchCGNode extends Node {

        protected static final String EXEC = "exec";

        abstract int execute(Node node, Object compiledRegex, Object input, int fromIndex);

        @Specialization(guards = "objs.isMemberInvocable(compiledRegex, EXEC)", limit = "3")
        static int run(Node node, Object compiledRegex, String input, int fromIndex,
                        @CachedLibrary("compiledRegex") InteropLibrary objs,
                        @Cached RegexBenchCGGetStartNode getStart0,
                        @Cached RegexBenchCGGetStartNode getStart1,
                        @Cached RegexBenchCGGetEndNode getEnd0,
                        @Cached RegexBenchCGGetEndNode getEnd1) {
            try {
                Object result = objs.invokeMember(compiledRegex, EXEC, input, fromIndex);
                int start0 = getStart0.execute(node, result, 0);
                int end0 = getEnd0.execute(node, result, 0);
                int start1 = getStart1.execute(node, result, 1);
                int end1 = getEnd1.execute(node, result, 1);
                return start0 + start1 + end0 + end1;
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @GenerateInline
    abstract static class RegexBenchCGGetStartNode extends Node {

        protected static final String GET_START = "getStart";

        abstract int execute(Node node, Object regexResult, int i);

        @Specialization(guards = "objs.isMemberInvocable(regexResult, GET_START)", limit = "3")
        static int exec(Object regexResult, int i,
                        @CachedLibrary("regexResult") InteropLibrary objs) {
            try {
                return (int) objs.invokeMember(regexResult, GET_START, i);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @GenerateInline
    abstract static class RegexBenchCGGetEndNode extends Node {

        protected static final String GET_END = "getEnd";

        abstract int execute(Node node, Object regexResult, int i);

        @Specialization(guards = "objs.isMemberInvocable(regexResult, GET_END)", limit = "3")
        static int exec(Object regexResult, int i,
                        @CachedLibrary("regexResult") InteropLibrary objs) {
            try {
                return (int) objs.invokeMember(regexResult, GET_END, i);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @Override
    protected DummyLanguageContext createContext(Env env) {
        return new DummyLanguageContext(env);
    }

    @Override
    protected boolean patchContext(DummyLanguageContext context, Env newEnv) {
        context.patchContext(newEnv);
        return true;
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        return true;
    }

    public static final class DummyLanguageContext {

        @CompilerDirectives.CompilationFinal private Env env;

        DummyLanguageContext(Env env) {
            this.env = env;
        }

        void patchContext(Env patchedEnv) {
            this.env = patchedEnv;
        }

        public Env getEnv() {
            return env;
        }

        private static final ContextReference<DummyLanguageContext> REFERENCE = ContextReference.create(TRegexTestDummyLanguage.class);

        public static DummyLanguageContext get(Node node) {
            return REFERENCE.get(node);
        }
    }
}
