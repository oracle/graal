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

import java.util.Collection;
import java.util.Collections;
import java.util.ServiceLoader;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

/**
 * The {@link LanguageProvider} provides factory methods for language data types, expressions,
 * statements and scripts used for testing language inter-operability. The {@link LanguageProvider}
 * implementations are loaded by the {@link ServiceLoader} and should be registered in the
 * 'META-INF/services/org.graalvm.polyglot.tck.LanguageProvider'. See
 * <a href="https://github.com/oracle/graal/blob/master/truffle/docs/TCK.md" target="_top">Test
 * Compatibility Kit</a> for details how to add a new language provider and execute tests.
 *
 * @since 0.30
 */
public interface LanguageProvider {

    /**
     * Returns an identification of a provider. The common pattern is to use the identification of
     * the tested language.
     *
     * @return the language identification
     * @since 0.30
     */
    String getId();

    /**
     * Creates an identity function. The identity function just returns its argument.
     *
     * <p>
     * The JavaScript sample implementation:
     * {@codesnippet LanguageProviderSnippets#JsSnippets#createIdentityFunction}
     * <p>
     * The R sample implementation:
     * {@codesnippet LanguageProviderSnippets#RSnippets#createIdentityFunction}
     *
     * @param context the context for a guest language code literal evaluation
     * @return the {@link Value} representing the identity function
     * @since 0.30
     */
    Value createIdentityFunction(Context context);

    /**
     * Creates a collection of functions creating language data types. For each language data type
     * create a function returning a value of given type and assign a correct {@link TypeDescriptor}
     * to it. The {@link TypeDescriptor} can be one of the predefined {@link TypeDescriptor}s, an
     * array with component type, an executable with required parameter types or an intersection
     * type.
     *
     * <p>
     * The JavaScript sample implementation creating a boolean type:
     * {@codesnippet LanguageProviderSnippets#JsSnippets#createValueConstructors}
     * <p>
     * The R sample implementation creating a boolean type:
     * {@codesnippet LanguageProviderSnippets#RSnippets#createValueConstructors}
     *
     * @param context the context for a guest language code literal evaluation
     * @return factories creating the language data types
     * @since 0.30
     */
    Collection<? extends Snippet> createValueConstructors(Context context);

    /**
     * Creates a collection of functions representing language expressions to test. For each
     * language operator create a function performing given operator and assign a correct
     * {@link TypeDescriptor}s to its parameters and return type. The parameter types and return
     * type can be one of the predefined {@link TypeDescriptor}s, an array with component type, an
     * executable with required parameter types or an union type.
     *
     * <p>
     * The JavaScript sample implementation creating a plus operator:
     * {@codesnippet LanguageProviderSnippets#JsSnippets#createExpressions}
     * <p>
     * The R sample implementation creating a plus operator:
     * {@codesnippet LanguageProviderSnippets#RSnippets#createExpressions}
     *
     * @param context the context for a guest language code literal evaluation
     * @return factories creating the language expressions
     * @since 0.30
     */
    Collection<? extends Snippet> createExpressions(Context context);

    /**
     * Creates a collection of functions representing language statements to test. For each control
     * flow statement create a function performing given statement and assign a correct
     * {@link TypeDescriptor}s to its parameters and return type. The parameter types and return
     * type can be one of the predefined {@link TypeDescriptor}s, an array with component type, an
     * executable with required parameter types or an union type.
     *
     * <p>
     * The JavaScript sample implementation creating the {@code if} statement:
     * {@codesnippet LanguageProviderSnippets#JsSnippets#createStatements}
     * <p>
     * The R sample implementation creating the {@code if} statement:
     * {@codesnippet LanguageProviderSnippets#RSnippets#createStatements}
     *
     * @param context the context for a guest language code literal evaluation
     * @return factories creating the language statements
     * @since 0.30
     */
    Collection<? extends Snippet> createStatements(Context context);

    /**
     * Creates a collection of simple scripts used for instrumentation testing. Each script is
     * represented as a function performing the script. The function must have no formal parameters
     * but may return a result which can be asserted by {@link ResultVerifier}.
     * <p>
     * The JavaScript sample implementation:
     * {@codesnippet LanguageProviderSnippets#JsSnippets#createScripts}
     *
     * @param context the context for a guest language code literal evaluation
     * @return the language scripts
     * @since 0.30
     */
    Collection<? extends Snippet> createScripts(Context context);

    /**
     * Creates a collection of scripts containing a syntax error.
     * <p>
     * The JavaScript sample implementation:
     * {@codesnippet LanguageProviderSnippets#JsSnippets#createInvalidSyntaxScripts}
     *
     * @param context the context for a guest language code literal evaluation
     * @return the scripts
     * @since 0.30
     */
    Collection<? extends Source> createInvalidSyntaxScripts(Context context);

    /**
     * Creates a collection of inline code snippets.
     * <p>
     * This method is optional, it should be implemented if
     * <code>TruffleLanguage.parse(InlineParsingRequest)</code> is implemented. It returns an empty
     * list by default.
     * <p>
     * The JavaScript sample implementation creating inline code snippets:
     * {@codesnippet LanguageProviderSnippets#JsSnippets#createInlineScripts}
     *
     * @param context the context for a guest language code literal evaluation
     * @return A collection of inline code snippets.
     * @since 0.32
     */
    default Collection<? extends InlineSnippet> createInlineScripts(Context context) {
        return Collections.emptyList();
    }
}
