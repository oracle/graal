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
package com.oracle.truffle.regex;

import org.graalvm.polyglot.SandboxPolicy;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.regex.analysis.InputStringGenerator;
import com.oracle.truffle.regex.tregex.TRegexCompiler;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.RegexParser;
import com.oracle.truffle.regex.tregex.parser.RegexParserGlobals;
import com.oracle.truffle.regex.tregex.parser.RegexValidator;
import com.oracle.truffle.regex.tregex.parser.ast.GroupBoundaries;
import com.oracle.truffle.regex.tregex.parser.flavors.ECMAScriptFlavor;
import com.oracle.truffle.regex.tregex.parser.flavors.RegexFlavor;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.util.TruffleNull;

/**
 * Truffle Regular Expression Language
 * <p>
 * This language represents classic regular expressions. It accepts regular expressions in the
 * following format: {@code options/regex/flags}, where {@code options} is a comma-separated list of
 * key-value pairs which affect how the regex is interpreted (see {@link RegexOptions}), and
 * {@code /regex/flags} is equivalent to the popular regular expression literal format found in e.g.
 * JavaScript or Ruby.
 * <p>
 * When parsing a regular expression, TRegex will return a {@link CallTarget}, which, when called,
 * will yield one of the following results:
 * <ul>
 * <li>a {@link TruffleNull} object, indicating that TRegex cannot handle the given regex</li>
 * <li>a {@link RegexObject}, which can be used to match the given regex</li>
 * <li>a {@link RegexSyntaxException} may be thrown to indicate a syntax error. This exception is an
 * {@link AbstractTruffleException} with exception type {@link ExceptionType#PARSE_ERROR}.</li>
 * </ul>
 *
 * An example of how to parse a regular expression:
 *
 * <pre>
 * Object regex;
 * try {
 *     regex = getContext().getEnv().parseInternal(Source.newBuilder("regex", "Flavor=ECMAScript/(a|(b))c/i", "myRegex").mimeType("application/tregex").internal(true).build()).call();
 * } catch (AbstractTruffleException e) {
 *     if (InteropLibrary.getUncached().getExceptionType(e) == ExceptionType.PARSE_ERROR) {
 *         // handle parser error
 *     } else {
 *         // fatal error, this should never happen
 *     }
 * }
 * if (InteropLibrary.getUncached().isNull(regex)) {
 *     // regex is not supported by TRegex, fall back to a different regex engine
 * }
 * </pre>
 *
 * Regex matcher usage example in pseudocode:
 *
 * <pre>
 * {@code
 * regex = <matcher from previous example>
 * assert(regex.pattern == "(a|(b))c")
 * assert(regex.flags.ignoreCase == true)
 * assert(regex.groupCount == 3)
 *
 * result = regex.exec("xacy", 0)
 * assert(result.isMatch == true)
 * assertEquals([result.getStart(0), result.getEnd(0)], [ 1,  3])
 * assertEquals([result.getStart(1), result.getEnd(1)], [ 1,  2])
 * assertEquals([result.getStart(2), result.getEnd(2)], [-1, -1])
 *
 * result2 = regex.exec("xxx", 0)
 * assert(result2.isMatch == false)
 * // result2.getStart(...) and result2.getEnd(...) are undefined
 * }
 * </pre>
 *
 * Debug loggers: {@link com.oracle.truffle.regex.tregex.util.Loggers}.
 *
 * @see RegexOptions
 * @see RegexObject
 * @see com.oracle.truffle.regex.tregex.util.Loggers
 */
@TruffleLanguage.Registration(name = RegexLanguage.NAME, //
                id = RegexLanguage.ID, //
                characterMimeTypes = RegexLanguage.MIME_TYPE, //
                version = "0.1", //
                contextPolicy = TruffleLanguage.ContextPolicy.SHARED, //
                internal = true, //
                interactive = false, //
                sandbox = SandboxPolicy.UNTRUSTED, //
                website = "https://github.com/oracle/graal/tree/master/regex")
@ProvidedTags(StandardTags.RootTag.class)
public final class RegexLanguage extends TruffleLanguage<RegexLanguage.RegexContext> {

    public static final String NAME = "REGEX";
    public static final String ID = "regex";
    public static final String MIME_TYPE = "application/tregex";

    private final GroupBoundaries[] cachedGroupBoundaries;
    public final RegexParserGlobals parserGlobals;

    public RegexLanguage() {
        this.cachedGroupBoundaries = GroupBoundaries.createCachedGroupBoundaries();
        this.parserGlobals = new RegexParserGlobals(this);
    }

    public GroupBoundaries[] getCachedGroupBoundaries() {
        return cachedGroupBoundaries;
    }

    @Override
    protected CallTarget parse(ParsingRequest parsingRequest) {
        RegexSource source = createRegexSource(parsingRequest.getSource());
        if (source.getOptions().isGenerateInput()) {
            RegexFlavor flavor = source.getOptions().getFlavor();
            RegexParser parser = flavor.createParser(this, source, new CompilationBuffer(source.getEncoding()));
            return InputStringGenerator.generateRootNode(this, parser.parse()).getCallTarget();
        }
        return RootNode.createConstantNode(createRegexObject(source)).getCallTarget();
    }

    public static RegexSource createRegexSource(Source source) {
        String srcStr = source.getCharacters().toString();
        if (srcStr.length() < 2) {
            throw CompilerDirectives.shouldNotReachHere("malformed regex");
        }
        RegexOptions.Builder optBuilder = RegexOptions.builder(source, srcStr);
        int firstSlash = optBuilder.parseOptions();
        int lastSlash = srcStr.lastIndexOf('/');
        assert firstSlash >= 0 && firstSlash <= srcStr.length();
        if (lastSlash <= firstSlash) {
            throw CompilerDirectives.shouldNotReachHere("malformed regex");
        }
        String pattern = srcStr.substring(firstSlash + 1, lastSlash);
        String flags = srcStr.substring(lastSlash + 1);
        // ECMAScript-specific: the 'u' and 'v' flags change the encoding
        if (optBuilder.getFlavor() == ECMAScriptFlavor.INSTANCE) {
            if (flags.indexOf('u') >= 0 || flags.indexOf('v') >= 0) {
                if (!optBuilder.isUtf16ExplodeAstralSymbols() && optBuilder.getEncoding() == Encodings.UTF_16_RAW) {
                    optBuilder.encoding(Encodings.UTF_16);
                }
            } else {
                if (optBuilder.getEncoding() == Encodings.UTF_16) {
                    optBuilder.encoding(Encodings.UTF_16_RAW);
                }
            }
        }
        return new RegexSource(pattern, flags, optBuilder.build(), source);
    }

    private Object createRegexObject(RegexSource source) {
        if (source.getOptions().isValidate()) {
            RegexFlavor flavor = source.getOptions().getFlavor();
            RegexValidator validator = flavor.createValidator(this, source, new CompilationBuffer(source.getEncoding()));
            validator.validate();
            return TruffleNull.INSTANCE;
        }
        try {
            return TRegexCompiler.compile(this, source);
        } catch (UnsupportedRegexException e) {
            return TruffleNull.INSTANCE;
        }
    }

    @Override
    protected RegexContext createContext(Env env) {
        return new RegexContext(env);
    }

    @Override
    protected boolean patchContext(RegexContext context, Env newEnv) {
        context.patchContext(newEnv);
        return true;
    }

    @Override
    protected Object getScope(RegexContext context) {
        return null;
    }

    /**
     * {@link RegexLanguage} is thread-safe - it supports parallel parsing requests as well as
     * parallel access to all {@link AbstractRegexObject}s. Parallel access to
     * {@link com.oracle.truffle.regex.result.RegexResult}s objects may lead to duplicate execution
     * of code, but no wrong results.
     *
     * @param thread the thread that accesses the context for the first time.
     * @param singleThreaded {@code true} if the access is considered single-threaded, {@code false}
     *            if more than one thread is active at the same time.
     * @return always {@code true}
     */
    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        return true;
    }

    public static final class RegexContext {

        @CompilationFinal private Env env;

        RegexContext(Env env) {
            this.env = env;
        }

        void patchContext(Env patchedEnv) {
            this.env = patchedEnv;
        }

        public Env getEnv() {
            return env;
        }

        private static final ContextReference<RegexContext> REFERENCE = ContextReference.create(RegexLanguage.class);

        public static RegexContext get(Node node) {
            return REFERENCE.get(node);
        }
    }
}
