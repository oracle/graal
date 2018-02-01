/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
