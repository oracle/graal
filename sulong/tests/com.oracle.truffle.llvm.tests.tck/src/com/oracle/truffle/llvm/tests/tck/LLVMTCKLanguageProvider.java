/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.tests.tck;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.tck.InlineSnippet;
import org.graalvm.polyglot.tck.LanguageProvider;
import org.graalvm.polyglot.tck.ResultVerifier;
import org.graalvm.polyglot.tck.Snippet;
import org.graalvm.polyglot.tck.TypeDescriptor;
import org.junit.Assert;

public final class LLVMTCKLanguageProvider implements LanguageProvider {

    private static final String ID = "llvm";
    private final String nativeSourcePath = System.getProperty("test.sulongtck.path");

    public LLVMTCKLanguageProvider() {
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public Value createIdentityFunction(Context context) {
        try {
            Source source = createSource(nativeSourcePath + "/identity.bc");
            return context.eval(source).getMember("identity");
        } catch (IOException ioe) {
            throw new AssertionError("IOException while creating a test script for llvm_tck in identity function.", ioe);
        }
    }

    @Override
    public Snippet createIdentityFunctionSnippet(Context context) {
        Value value = createIdentityFunction(context);
        return (Snippet.newBuilder("identity", value, TypeDescriptor.ANY).parameterTypes(TypeDescriptor.ANY).resultVerifier(new IdentityFunctionResultVerifier()).build());
    }

    @Override
    public Collection<? extends Snippet> createValueConstructors(Context context) {
        try {
            List<Snippet> vals = new ArrayList<>();
            Source intSource = createSource(nativeSourcePath + "/value_int.bc");
            vals.add(createSnippet(context, intSource, "val", "1", TypeDescriptor.NUMBER));

            Source doubleSource = createSource(nativeSourcePath + "/value_double.bc");
            vals.add(createSnippet(context, doubleSource, "val", "1.42", TypeDescriptor.NUMBER));

            Source boolSource = createSource(nativeSourcePath + "/value_bool.bc");
            vals.add(createSnippet(context, boolSource, "val", "true", TypeDescriptor.BOOLEAN));

            Source charSource = createSource(nativeSourcePath + "/value_char.bc");
            vals.add(createSnippet(context, charSource, "val", "'a'", TypeDescriptor.NUMBER));

            Source nullSource = createSource(nativeSourcePath + "/value_null.bc");
            vals.add(createSnippet(context, nullSource, "val", "NULL", TypeDescriptor.NULL));

            return Collections.unmodifiableList(vals);
        } catch (IOException ioe) {
            throw new AssertionError("IOException while creating a test script for llvm_tck in value constructors.", ioe);
        }
    }

    private static Snippet createSnippet(
                    Context context,
                    Source source,
                    String member,
                    String value,
                    TypeDescriptor type) {
        return Snippet.newBuilder(value, context.eval(source).getMember(member), type).build();
    }

    @Override
    public Collection<? extends Snippet> createExpressions(Context context) {
        try {
            final Collection<Snippet> expressions = new ArrayList<>();

            Source intAddSource = createSource(nativeSourcePath + "/add_int.bc");
            Snippet.Builder intAddBuilder = Snippet.newBuilder("add_int", context.eval(intAddSource).getMember("plus"),
                            TypeDescriptor.intersection(TypeDescriptor.NUMBER, TypeDescriptor.STRING, TypeDescriptor.BOOLEAN)).parameterTypes(
                                            TypeDescriptor.intersection(TypeDescriptor.NUMBER, TypeDescriptor.STRING, TypeDescriptor.BOOLEAN),
                                            TypeDescriptor.intersection(TypeDescriptor.NUMBER, TypeDescriptor.STRING, TypeDescriptor.BOOLEAN)).resultVerifier(new AddExpressionResultVerifier());
            expressions.add(intAddBuilder.build());

            Source doubleAddSource = createSource(nativeSourcePath + "/add_double.bc");
            Snippet.Builder doubleAddBuilder = Snippet.newBuilder("add_double", context.eval(doubleAddSource).getMember("plus"),
                            TypeDescriptor.intersection(TypeDescriptor.NUMBER, TypeDescriptor.STRING, TypeDescriptor.BOOLEAN)).parameterTypes(
                                            TypeDescriptor.intersection(TypeDescriptor.NUMBER, TypeDescriptor.STRING, TypeDescriptor.BOOLEAN),
                                            TypeDescriptor.intersection(TypeDescriptor.NUMBER, TypeDescriptor.STRING, TypeDescriptor.BOOLEAN)).resultVerifier(new AddExpressionResultVerifier());
            expressions.add(doubleAddBuilder.build());

            return expressions;
        } catch (IOException ioe) {
            throw new AssertionError("IOException while creating a test script for llvm_tck in expression creation.", ioe);
        }
    }

    @Override
    public Collection<? extends Snippet> createStatements(Context context) {
        // List<Snippet> res = new ArrayList<>();
        return Collections.emptyList();
    }

    @Override
    public Collection<? extends Snippet> createScripts(Context context) {
        // List<Snippet> res = new ArrayList<>();
        return Collections.emptyList();
    }

    @Override
    public Collection<? extends Source> createInvalidSyntaxScripts(Context ctx) {
        // List<Source> res = new ArrayList<>();
        return Collections.emptyList();
    }

    @Override
    public Collection<? extends InlineSnippet> createInlineScripts(Context context) {
        return Collections.emptyList();
    }

    private static Source createSource(String resourceName) throws IOException {
        File file = new File(resourceName);
        return Source.newBuilder(ID, file).build();
    }

    final class IdentityFunctionResultVerifier implements ResultVerifier {
        ResultVerifier delegate = ResultVerifier.getIdentityFunctionDefaultResultVerifier();

        private IdentityFunctionResultVerifier() {
        }

        @Override
        public void accept(SnippetRun snippetRun) throws PolyglotException {
            for (Value p : snippetRun.getParameters()) {
                if (p.isNull()) {
                    return;
                }
            }
            delegate.accept(snippetRun);
        }
    }

    final class StatementResultVerifier implements ResultVerifier {
        ResultVerifier delegate = ResultVerifier.getDefaultResultVerifier();

        private StatementResultVerifier() {
        }

        @Override
        public void accept(SnippetRun snippetRun) throws PolyglotException {

            for (Value p : snippetRun.getParameters()) {
                if (p.isString()) {
                    String val = p.asString();
                    Assert.assertFalse(val.isEmpty());
                }
                if (p.isNull()) {
                    return;
                }
            }
            delegate.accept(snippetRun);
        }
    }

    final class AddExpressionResultVerifier implements ResultVerifier {
        ResultVerifier delegate = ResultVerifier.getDefaultResultVerifier();

        private AddExpressionResultVerifier() {
        }

        @Override
        public void accept(SnippetRun snippetRun) throws PolyglotException {
            final Value firstParam = snippetRun.getParameters().get(0);
            final Value secondParam = snippetRun.getParameters().get(1);
            final PolyglotException exception = snippetRun.getException();
            if (exception != null) {
                throw exception;
            }
            Assert.assertTrue(firstParam.isNumber());
            Assert.assertTrue(secondParam.isNumber());
            delegate.accept(snippetRun);
        }
    }
}
