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
package com.oracle.truffle.regex;

import java.util.Collections;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Truffle Regular Expression Language
 * <p>
 * This language represents classic regular expressions. By evaluating any source, you get access to
 * the {@link RegexEngineBuilder}. By calling this builder, you can build your custom
 * {@link RegexEngine} which implements your flavor of regular expressions and uses your fallback
 * compiler for expressions not covered. The {@link RegexEngine} accepts regular expression patterns
 * and flags and compiles them to {@link RegexObject}s, which you can use to match the regular
 * expressions against strings.
 * <p>
 *
 * <pre>
 * Usage example in pseudocode:
 * {@code
 * engineBuilder = <eval any source in the "regex" language>
 * engine = engineBuilder("Flavor=ECMAScript", optionalFallbackCompiler)
 *
 * regex = engine("(a|(b))c", "i")
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
 */

@TruffleLanguage.Registration(name = RegexLanguage.NAME, id = RegexLanguage.ID, characterMimeTypes = RegexLanguage.MIME_TYPE, version = "0.1", contextPolicy = TruffleLanguage.ContextPolicy.SHARED, internal = true, interactive = false)
@ProvidedTags(StandardTags.RootTag.class)
public final class RegexLanguage extends TruffleLanguage<RegexLanguage.RegexContext> {

    public static final String NAME = "REGEX";
    public static final String ID = "regex";
    public static final String MIME_TYPE = "application/tregex";

    public final RegexEngineBuilder engineBuilder = new RegexEngineBuilder(this);

    @Override
    protected CallTarget parse(ParsingRequest parsingRequest) {
        return getCurrentContext().getEngineBuilderCT;
    }

    @Override
    protected RegexContext createContext(Env env) {
        return new RegexContext(env, engineBuilder);
    }

    @Override
    protected boolean patchContext(RegexContext context, Env newEnv) {
        context.patchContext(newEnv);
        return true;
    }

    @Override
    protected Iterable<Scope> findTopScopes(RegexContext context) {
        return Collections.emptySet();
    }

    /**
     * {@link RegexLanguage} is thread-safe - it supports parallel parsing requests as well as
     * parallel access to all {@link AbstractRegexObject}s. Parallel access to
     * {@link com.oracle.truffle.regex.result.LazyCaptureGroupsResult} objects may lead to duplicate
     * execution of code, but no wrong results.
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

    public static RegexContext getCurrentContext() {
        return getCurrentContext(RegexLanguage.class);
    }

    public static final class RegexContext {
        @CompilerDirectives.CompilationFinal private Env env;
        private final CallTarget getEngineBuilderCT;

        RegexContext(Env env, RegexEngineBuilder builder) {
            this.env = env;
            getEngineBuilderCT = Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(builder));
        }

        void patchContext(Env patchedEnv) {
            this.env = patchedEnv;
        }

        public Env getEnv() {
            return env;
        }
    }
}
