/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
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
     * Allows language providers to provide language options during the creation of the test
     * context. {@link LanguageProvider LanguageProviders} are only allowed to set options of the
     * language they represent (Options starting with {@link #getId()}). Attempts to set options
     * other than their own language will throw {@link IllegalArgumentException} on test context
     * creation.
     *
     * @return The {@code (key, value)} pairs of language option to add to the context.
     * @since 22.3
     */
    default Map<String, String> additionalOptions() {
        return Collections.emptyMap();
    }

    /**
     * Creates an identity function. The identity function just returns its argument.
     *
     * <p>
     * The JavaScript sample implementation:
     * 
     * <pre>
     * &#64;Override
     * public Value createIdentityFunction(Context context) {
     *     return context.eval("js", "(function (a){ return a; })");
     * }
     * </pre>
     * 
     * <p>
     * The R sample implementation:
     * 
     * <pre>
     * &#64;Override
     * public Value createIdentityFunction(Context context) {
     *     return context.eval("R", "function (a){ a }");
     * }
     * </pre>
     *
     * @param context the context for a guest language code literal evaluation
     * @return the {@link Value} representing the identity function
     * @since 0.30
     */
    Value createIdentityFunction(Context context);

    /**
     * Creates a {@link Snippet} for an identity function. The identity function just returns its
     * argument. This method allows an implementor to override the default identity function
     * verification. In most cases it's not needed to implement this method and it's enough to
     * implement {@link #createIdentityFunction}.
     * <p>
     * The implementor can delegate to the default identity function verifier obtained by
     * {@link ResultVerifier#getIdentityFunctionDefaultResultVerifier()}.
     *
     * @param context the context for a guest language code literal evaluation
     * @return the {@link Snippet} representing the identity function
     * @since 19.1.0
     */
    default Snippet createIdentityFunctionSnippet(Context context) {
        Value value = createIdentityFunction(context);
        if (!value.canExecute()) {
            throw new AssertionError(String.format("Result of createIdentityFunction for tck provider %s did not return an executable value. Returned value '%s'.", getId(), value));
        }
        return (Snippet.newBuilder("identity", value, TypeDescriptor.ANY).parameterTypes(TypeDescriptor.ANY).resultVerifier(ResultVerifier.getIdentityFunctionDefaultResultVerifier()).build());
    }

    /**
     * Creates a collection of functions creating language data types. For each language data type
     * create a function returning a value of given type and assign a correct {@link TypeDescriptor}
     * to it. The {@link TypeDescriptor} can be one of the predefined {@link TypeDescriptor}s, an
     * array with component type, an executable with required parameter types or an intersection
     * type.
     *
     * <p>
     * The JavaScript sample implementation creating a boolean type:
     * 
     * <pre>
     * @Override
     * public Collection<? extends Snippet> createValueConstructors(Context context) {
     *     final Collection<Snippet> valueConstructors = new ArrayList<>();
     *     Snippet.Builder builder = Snippet.newBuilder(
     *                     "boolean",
     *                     context.eval("js", "(function (){ return false;})"),
     *                     TypeDescriptor.BOOLEAN);
     *     valueConstructors.add(builder.build());
     *     return valueConstructors;
     * }
     * </pre>
     * 
     * <p>
     * The R sample implementation creating a boolean type:
     * 
     * <pre>
     * &#64;Override
     * public Collection&lt;? extends Snippet&gt; createValueConstructors(Context context) {
     *     final Collection&lt;Snippet&gt; valueConstructors = new ArrayList<>();
     *     Snippet.Builder builder = Snippet.newBuilder(
     *                     "boolean",
     *                     context.eval("R", "function (){ FALSE }"),
     *                     TypeDescriptor.BOOLEAN);
     *     valueConstructors.add(builder.build());
     *     return valueConstructors;
     * }
     * </pre>
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
     * 
     * <pre>
     * &#64;Override
     * public Collection&lt;? extends Snippet&gt; createExpressions(Context context) {
     *     final Collection&lt;Snippet&gt; expressions = new ArrayList&lt;&gt;();
     *     final TypeDescriptor numeric = TypeDescriptor.union(
     *                     TypeDescriptor.NUMBER,
     *                     TypeDescriptor.BOOLEAN);
     *     final TypeDescriptor nonNumeric = TypeDescriptor.union(
     *                     TypeDescriptor.STRING,
     *                     TypeDescriptor.OBJECT,
     *                     TypeDescriptor.ARRAY,
     *                     TypeDescriptor.EXECUTABLE_ANY);
     *     Snippet.Builder builder = Snippet.newBuilder(
     *                     "+",
     *                     context.eval("js",
     *                                     "(function (a, b){ return a + b;})"),
     *                     TypeDescriptor.NUMBER).parameterTypes(numeric, numeric);
     *     expressions.add(builder.build());
     *     builder = Snippet.newBuilder(
     *                     "+",
     *                     context.eval("js",
     *                                     "(function (a, b){ return a + b;})"),
     *                     TypeDescriptor.STRING).parameterTypes(nonNumeric, TypeDescriptor.ANY);
     *     expressions.add(builder.build());
     *     builder = Snippet.newBuilder(
     *                     "+",
     *                     context.eval("js",
     *                                     "(function (a, b){ return a + b;})"),
     *                     TypeDescriptor.STRING).parameterTypes(TypeDescriptor.ANY, nonNumeric);
     *     expressions.add(builder.build());
     *     return expressions;
     * }
     * </pre>
     * 
     * The R sample implementation creating a plus operator:
     * 
     * <pre>
     * &#64;Override
     * public Collection&lt;? extends Snippet&gt; createExpressions(Context context) {
     *     final Collection&lt;Snippet&gt; expressions = new ArrayList&lt;&gt;();
     *     final TypeDescriptor numOrBool = TypeDescriptor.union(
     *                     TypeDescriptor.NUMBER,
     *                     TypeDescriptor.BOOLEAN);
     *     final TypeDescriptor arrNumOrBool = TypeDescriptor.array(
     *                     numOrBool);
     *     final TypeDescriptor numeric = TypeDescriptor.union(
     *                     numOrBool,
     *                     arrNumOrBool);
     *     Snippet.Builder builder = Snippet.newBuilder(
     *                     "+",
     *                     context.eval("R",
     *                                     "function (a, b){ a + b}"),
     *                     numeric).parameterTypes(numeric, numeric);
     *     expressions.add(builder.build());
     *     return expressions;
     * }
     * </pre>
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
     * 
     * <pre>
     * &#64;Override
     * public Collection&lt;? extends Snippet&gt; createStatements(Context context) {
     *     final Collection&lt;Snippet&gt; statements = new ArrayList&lt;&gt;();
     *     Snippet.Builder builder = Snippet.newBuilder(
     *                     "if",
     *                     context.eval("js",
     *                                     "(function (p){\n" +
     *                                                     "  if (p) return true ; else  return false;\n" +
     *                                                     "})"),
     *                     TypeDescriptor.BOOLEAN).parameterTypes(
     *                                     TypeDescriptor.union(
     *                                                     TypeDescriptor.STRING,
     *                                                     TypeDescriptor.OBJECT,
     *                                                     TypeDescriptor.NUMBER,
     *                                                     TypeDescriptor.BOOLEAN));
     *     statements.add(builder.build());
     *     return statements;
     * }
     * </pre>
     * <p>
     * The R sample implementation creating the {@code if} statement:
     *
     * <pre>
     * &#64;Override
     * public Collection&lt;? extends Snippet&gt; createStatements(Context context) {
     *     final Collection&lt;Snippet&gt; statements = new ArrayList&lt;&gt;();
     *     final TypeDescriptor numberOrBoolean = TypeDescriptor.union(
     *                     TypeDescriptor.NUMBER,
     *                     TypeDescriptor.BOOLEAN);
     *     final TypeDescriptor arrayNumberOrBoolean = TypeDescriptor.array(
     *                     numberOrBoolean);
     *     Snippet.Builder builder = Snippet.newBuilder(
     *                     "if",
     *                     context.eval("R",
     *                                     "function(p){\n" +
     *                                                     "  if (p) { return (TRUE) } else { return (FALSE) }\n" +
     *                                                     "}"),
     *                     TypeDescriptor.BOOLEAN).parameterTypes(
     *                                     TypeDescriptor.union(
     *                                                     numberOrBoolean,
     *                                                     arrayNumberOrBoolean));
     *     statements.add(builder.build());
     *     return statements;
     * }
     * </pre>
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
     * 
     * <pre>
     * &#64;Override
     * public Collection&lt;? extends Snippet&gt; createScripts(Context context) {
     *     try {
     *         final Collection&lt;Snippet&gt; scripts = new ArrayList&lt;&gt;();
     *         Reader reader = new InputStreamReader(
     *                         getClass().getResourceAsStream("sample.js"),
     *                         "UTF-8");
     *         Source source = Source.newBuilder(
     *                         "js",
     *                         reader,
     *                         "sample.js").build();
     *         Snippet.Builder builder = Snippet.newBuilder(
     *                         source.getName(),
     *                         context.eval(source),
     *                         TypeDescriptor.NULL);
     *         scripts.add(builder.build());
     *         return scripts;
     *     } catch (IOException ioe) {
     *         throw new RuntimeException(ioe);
     *     }
     * }
     * </pre>
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
     * 
     * <pre>
     * &#64;Override
     * public Collection&lt;? extends Source&gt; createInvalidSyntaxScripts(Context ctx) {
     *     try {
     *         final Collection<Source> scripts = new ArrayList&lt;&gt;();
     *         Reader reader = new InputStreamReader(
     *                         getClass().getResourceAsStream("invalidSyntax.js"),
     *                         "UTF-8");
     *         scripts.add(Source.newBuilder(
     *                         "js",
     *                         reader,
     *                         "invalidSyntax.js").build());
     *         return scripts;
     *     } catch (IOException ioe) {
     *         throw new RuntimeException(ioe);
     *     }
     * }
     * </pre>
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
     * 
     * <pre>
     * &#64;Override
     * public Collection&lt;? extends InlineSnippet&gt; createInlineScripts(Context context) {
     *     final Collection&lt;InlineSnippet&gt; inlineScripts = new ArrayList&lt;&gt;();
     *     Snippet.Builder scriptBuilder = Snippet.newBuilder(
     *                     "factorial",
     *                     context.eval("js",
     *                                     "(function (){\n" +
     *                                                     "  let factorial = function(n) {\n" +
     *                                                     "    let f = 1;\n" +
     *                                                     "    for (let i = 2; i &lt;= n; i++) {\n" +
     *                                                     "      f *= i;\n" +
     *                                                     "    }\n" +
     *                                                     "  };\n" +
     *                                                     "  return factorial(10);\n" +
     *                                                     "})"),
     *                     TypeDescriptor.NUMBER);
     *     InlineSnippet.Builder builder = InlineSnippet.newBuilder(
     *                     scriptBuilder.build(),
     *                     "n * n").locationPredicate((SourceSection section) -> {
     *                         int line = section.getStartLine();
     *                         return 3 &lt;= line &amp;&amp; line &lt;= 6;
     *                     });
     *     inlineScripts.add(builder.build());
     *     builder = InlineSnippet.newBuilder(
     *                     scriptBuilder.build(),
     *                     "Math.sin(Math.PI)");
     *     inlineScripts.add(builder.build());
     *     return inlineScripts;
     * }
     * </pre>
     *
     * @param context the context for a guest language code literal evaluation
     * @return A collection of inline code snippets.
     * @since 0.32
     */
    default Collection<? extends InlineSnippet> createInlineScripts(Context context) {
        return Collections.emptyList();
    }
}
