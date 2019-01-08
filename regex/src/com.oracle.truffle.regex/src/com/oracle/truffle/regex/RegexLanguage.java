/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.regex.tregex.parser.RegexParser;

import java.util.Collections;

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
 *
 * result = regex.exec("xacy", 0)
 * assert(result.isMatch == true)
 * assert(result.input == "xacy")
 * assert(result.groupCount == 3)
 * assert([result.start[0], result.end[0]], [ 1,  3])
 * assert([result.start[1], result.end[1]], [ 1,  2])
 * assert([result.start[2], result.end[2]], [-1, -1])
 *
 * result2 = regex.exec("xxx", 0)
 * assert(result2.isMatch == false)
 * assert(result2.input == null)
 * assert(result2.groupCount == 0)
 * assertThrows([result2.start[0], result2.end[0]], IndexOutOfBoundsException)
 * }
 * </pre>
 */

@TruffleLanguage.Registration(name = RegexLanguage.NAME, id = RegexLanguage.ID, characterMimeTypes = RegexLanguage.MIME_TYPE, version = "0.1", contextPolicy = TruffleLanguage.ContextPolicy.SHARED, internal = true, interactive = false)
@ProvidedTags(StandardTags.RootTag.class)
public final class RegexLanguage extends TruffleLanguage<Void> {

    public static final String NAME = "REGEX";
    public static final String ID = "regex";
    public static final String MIME_TYPE = "application/tregex";

    public final RegexEngineBuilder engineBuilder = new RegexEngineBuilder(this);

    private final CallTarget getEngineBuilderCT = Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(engineBuilder));

    public static void validateRegex(String pattern, String flags) throws RegexSyntaxException {
        RegexParser.validate(new RegexSource(pattern, flags));
    }

    @Override
    protected CallTarget parse(ParsingRequest parsingRequest) {
        return getEngineBuilderCT;
    }

    @Override
    protected Void createContext(Env env) {
        return null;
    }

    @Override
    protected boolean patchContext(Void context, Env newEnv) {
        return true;
    }

    @Override
    protected Iterable<Scope> findTopScopes(Void context) {
        return Collections.emptySet();
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return object instanceof RegexLanguageObject;
    }

    /**
     * {@link RegexLanguage} is thread-safe - it supports parallel parsing requests as well as
     * parallel access to all {@link RegexLanguageObject}s. Parallel access to
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
}
