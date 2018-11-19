/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.tck;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;

final class LanguageProviderSnippets {

    static final class JsSnippets implements LanguageProvider {

        @Override
        public String getId() {
            return "js";
        }

        // @formatter:off
        // BEGIN: LanguageProviderSnippets#JsSnippets#createIdentityFunction
        @Override
        public Value createIdentityFunction(Context context) {
            return context.eval("js", "(function (a){ return a; })");
        }
        // END: LanguageProviderSnippets#JsSnippets#createIdentityFunction
        // @formatter:on

        // @formatter:off
        // BEGIN: LanguageProviderSnippets#JsSnippets#createValueConstructors
        @Override
        public Collection<? extends Snippet> createValueConstructors(Context context) {
            final Collection<Snippet> valueConstructors = new ArrayList<>();
            Snippet.Builder builder = Snippet.newBuilder(
                    "boolean",
                    context.eval("js", "(function (){ return false;})"),
                    TypeDescriptor.BOOLEAN);
            valueConstructors.add(builder.build());
            return valueConstructors;
        }
        // END: LanguageProviderSnippets#JsSnippets#createValueConstructors
        // @formatter:on

        // @formatter:off
        // BEGIN: LanguageProviderSnippets#JsSnippets#createExpressions
        @Override
        public Collection<? extends Snippet> createExpressions(Context context) {
            final Collection<Snippet> expressions = new ArrayList<>();
            final TypeDescriptor numeric = TypeDescriptor.union(
                    TypeDescriptor.NUMBER,
                    TypeDescriptor.BOOLEAN);
            final TypeDescriptor nonNumeric = TypeDescriptor.union(
                    TypeDescriptor.STRING,
                    TypeDescriptor.OBJECT,
                    TypeDescriptor.ARRAY,
                    TypeDescriptor.EXECUTABLE_ANY);
            Snippet.Builder builder = Snippet.newBuilder(
                    "+",
                    context.eval(
                            "js",
                            "(function (a, b){ return a + b;})"),
                    TypeDescriptor.NUMBER).
                parameterTypes(numeric, numeric);
            expressions.add(builder.build());
            builder = Snippet.newBuilder(
                    "+",
                    context.eval(
                            "js",
                            "(function (a, b){ return a + b;})"),
                    TypeDescriptor.STRING).
                parameterTypes(nonNumeric, TypeDescriptor.ANY);
            expressions.add(builder.build());
            builder = Snippet.newBuilder(
                    "+",
                    context.eval(
                            "js",
                            "(function (a, b){ return a + b;})"),
                    TypeDescriptor.STRING).
                parameterTypes(TypeDescriptor.ANY, nonNumeric);
            expressions.add(builder.build());
            return expressions;
        }
        // END: LanguageProviderSnippets#JsSnippets#createExpressions
        // @formatter:on

        // @formatter:off
        // BEGIN: LanguageProviderSnippets#JsSnippets#createStatements
        @Override
        public Collection<? extends Snippet> createStatements(Context context) {
            final Collection<Snippet> statements = new ArrayList<>();
            Snippet.Builder builder = Snippet.newBuilder(
                    "if",
                    context.eval(
                            "js",
                            "(function (p){\n" +
                            "  if (p) return true ; else  return false;\n" +
                            "})"),
                    TypeDescriptor.BOOLEAN).
                parameterTypes(TypeDescriptor.union(
                        TypeDescriptor.STRING,
                        TypeDescriptor.OBJECT,
                        TypeDescriptor.NUMBER,
                        TypeDescriptor.BOOLEAN));
            statements.add(builder.build());
            return statements;
        }
        // END: LanguageProviderSnippets#JsSnippets#createStatements
        // @formatter:on

        // @formatter:off
        // BEGIN: LanguageProviderSnippets#JsSnippets#createScripts
        @Override
        public Collection<? extends Snippet> createScripts(Context context) {
            try {
                final Collection<Snippet> scripts = new ArrayList<>();
                Reader reader = new InputStreamReader(
                        getClass().getResourceAsStream("sample.js"),
                        "UTF-8");
                Source source = Source.newBuilder(
                        "js",
                        reader,
                        "sample.js").build();
                Snippet.Builder builder = Snippet.newBuilder(
                        source.getName(),
                        context.eval(source),
                        TypeDescriptor.NULL);
                scripts.add(builder.build());
                return scripts;
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
        // END: LanguageProviderSnippets#JsSnippets#createScripts
        // @formatter:on

        // @formatter:off
        // BEGIN: LanguageProviderSnippets#JsSnippets#createInlineScripts
        @Override
        public Collection<? extends InlineSnippet>
                    createInlineScripts(Context context) {
            final Collection<InlineSnippet> inlineScripts = new ArrayList<>();
            Snippet.Builder scriptBuilder = Snippet.newBuilder(
                    "factorial",
                    context.eval(
                            "js",
                            "(function (){\n" +
                            "  let factorial = function(n) {\n" +
                            "    let f = 1;\n" +
                            "    for (let i = 2; i <= n; i++) {\n" +
                            "      f *= i;\n" +
                            "    }\n" +
                            "  };\n" +
                            "  return factorial(10);\n" +
                            "})"),
                    TypeDescriptor.NUMBER);
            InlineSnippet.Builder builder = InlineSnippet.newBuilder(
                    scriptBuilder.build(),
                    "n * n").
                locationPredicate((SourceSection section) -> {
                    int line = section.getStartLine();
                    return 3 <= line && line <= 6;
                });
            inlineScripts.add(builder.build());
            builder = InlineSnippet.newBuilder(
                    scriptBuilder.build(),
                    "Math.sin(Math.PI)");
            inlineScripts.add(builder.build());
            return inlineScripts;
        }
        // END: LanguageProviderSnippets#JsSnippets#createInlineScripts
        // @formatter:on

        // @formatter:off
        // BEGIN: LanguageProviderSnippets#JsSnippets#createInvalidSyntaxScripts
        @Override
        public Collection<? extends Source> createInvalidSyntaxScripts(Context ctx) {
            try {
                final Collection<Source> scripts = new ArrayList<>();
                Reader reader = new InputStreamReader(
                        getClass().getResourceAsStream("invalidSyntax.js"),
                        "UTF-8");
                scripts.add(Source.newBuilder(
                        "js",
                        reader,
                        "invalidSyntax.js").build());
                return scripts;
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
        // END: LanguageProviderSnippets#JsSnippets#createInvalidSyntaxScripts
        // @formatter:on
    }

    static final class RSnippets implements LanguageProvider {

        @Override
        public String getId() {
            return "R";
        }

        // @formatter:off
        // BEGIN: LanguageProviderSnippets#RSnippets#createIdentityFunction
        @Override
        public Value createIdentityFunction(Context context) {
            return context.eval("R", "function (a){ a }");
        }
        // END: LanguageProviderSnippets#RSnippets#createIdentityFunction
        // @formatter:on

        // @formatter:off
        // BEGIN: LanguageProviderSnippets#RSnippets#createValueConstructors
        @Override
        public Collection<? extends Snippet> createValueConstructors(Context context) {
            final Collection<Snippet> valueConstructors = new ArrayList<>();
            Snippet.Builder builder = Snippet.newBuilder(
                    "boolean",
                    context.eval("R", "function (){ FALSE }"),
                    TypeDescriptor.BOOLEAN);
            valueConstructors.add(builder.build());
            return valueConstructors;
        }
        // END: LanguageProviderSnippets#RSnippets#createValueConstructors
        // @formatter:on

        // @formatter:off
        // BEGIN: LanguageProviderSnippets#RSnippets#createExpressions
        @Override
        public Collection<? extends Snippet> createExpressions(Context context) {
            final Collection<Snippet> expressions = new ArrayList<>();
            final TypeDescriptor numOrBool = TypeDescriptor.union(
                    TypeDescriptor.NUMBER,
                    TypeDescriptor.BOOLEAN);
            final TypeDescriptor arrNumOrBool = TypeDescriptor.array(
                    numOrBool);
            final TypeDescriptor numeric = TypeDescriptor.union(
                    numOrBool,
                    arrNumOrBool);
            Snippet.Builder builder = Snippet.newBuilder(
                    "+",
                    context.eval(
                            "R",
                            "function (a, b){ a + b}"),
                    numeric).
                parameterTypes(numeric, numeric);
            expressions.add(builder.build());
            return expressions;
        }
        // END: LanguageProviderSnippets#RSnippets#createExpressions
        // @formatter:on

        // @formatter:off
        // BEGIN: LanguageProviderSnippets#RSnippets#createStatements
        @Override
        public Collection<? extends Snippet> createStatements(Context context) {
            final Collection<Snippet> statements = new ArrayList<>();
            final TypeDescriptor numberOrBoolean = TypeDescriptor.union(
                    TypeDescriptor.NUMBER,
                    TypeDescriptor.BOOLEAN);
            final TypeDescriptor arrayNumberOrBoolean = TypeDescriptor.array(
                    numberOrBoolean);
            Snippet.Builder builder = Snippet.newBuilder(
                    "if",
                    context.eval(
                            "R",
                            "function(p){\n" +
                            "  if (p) { return (TRUE) } else { return (FALSE) }\n" +
                            "}"),
                    TypeDescriptor.BOOLEAN).
                parameterTypes(TypeDescriptor.union(
                        numberOrBoolean,
                        arrayNumberOrBoolean));
            statements.add(builder.build());
            return statements;
        }
        // END: LanguageProviderSnippets#RSnippets#createStatements
        // @formatter:on

        @Override
        public Collection<? extends Snippet> createScripts(Context context) {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public Collection<? extends Source> createInvalidSyntaxScripts(Context context) {
            throw new UnsupportedOperationException("Not supported.");
        }
    }

    abstract static class TypeDescriptorSnippets implements LanguageProvider {
        // @formatter:off
        // BEGIN: LanguageProviderSnippets#TypeDescriptorSnippets#createValueConstructors
        @Override
        public Collection<? extends Snippet> createValueConstructors(Context context) {
            return Collections.singleton(Snippet.newBuilder(
                    "function",
                    context.eval("js", "(function(){ return function(){}})"),
                    TypeDescriptor.EXECUTABLE).build());
        }
        // END: LanguageProviderSnippets#TypeDescriptorSnippets#createValueConstructors
        // @formatter:on
    }
}
