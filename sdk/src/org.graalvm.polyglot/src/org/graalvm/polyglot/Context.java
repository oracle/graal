/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;

import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractContextImpl;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.MessageTransport;
import org.graalvm.polyglot.io.ProcessHandler;
import org.graalvm.polyglot.proxy.Proxy;

/**
 * A polyglot context for Graal guest languages that allows to {@link #eval(Source) evaluate} code.
 * A polyglot context represents the global runtime state of all {@link Engine#getLanguages()
 * installed} and {@link #newBuilder(String...) permitted} languages. Permitted languages are
 * {@link #initialize(String) initialized} lazily, when they are used for the first time. For many
 * context operations, a <i>language identifier</i> needs to be specified. A language identifier is
 * unique for each language.
 *
 * <h3>Evaluation</h3>
 *
 * A context allows to evaluate a guest language source code using {@link #eval(Source)}. This is
 * possible by evaluating {@link Source} objects or a given language identifier and code
 * {@link String}. The {@link #eval(Source) evaluation} returns either the result value or throws a
 * {@link PolyglotException} if a guest language error occurs.
 * <p>
 * <b>Example</b> for evaluation of a fragment of JavaScript code with a new context:
 *
 * <pre>
 * Context context = Context.create();
 * Value result = context.eval("js", "42");
 * assert result.asInt() == 42;
 * context.close();
 * </pre>
 *
 * In this example:
 * <ul>
 * <li>First we create a new context with all permitted languages.
 * <li>Next, we evaluate the expression "42" with language "js", which is the language identifier
 * for JavaScript. Since this is the first time we access JavaScript, it automatically gets
 * {@link #initialize(String) initialized}.
 * <li>Then, we assert the result value by converting the result value as primitive <code>int</code>
 * .
 * <li>Finally, if the context is no longer needed, it is necessary to close it to ensure that all
 * resources are freed. Contexts are also {@link AutoCloseable} for use with the Java
 * {@code try-with-resources} statement.
 * </ul>
 *
 * <h3>Configuration</h3>
 * <p>
 * Contexts may be created either with default configuration using the {@link #create(String...)
 * create method} or with custom configuration using the {@link #newBuilder(String...) builder}.
 * Both methods allow to specify a subset of the installed languages as permitted languages. If no
 * language is specified then all installed languages are permitted. Using the builder method
 * {@link Builder#in(InputStream) input}, {@link Builder#err(OutputStream) error} and
 * {@link Builder#out(OutputStream) output} streams, {@link Builder#option(String, String) options},
 * and {@link Builder#arguments(String, String[]) application arguments} may be configured.
 * <p>
 * Options may be specified for {@link Engine#getLanguages() languages},
 * {@link Engine#getInstruments() instruments}, the {@link Engine#getOptions() engine} and the
 * {@link Engine#getOptions() compiler}. For {@link Language#getOptions() language options}, the
 * option key consists of the {@link Language#getId() language id} plus a dot followed by the option
 * name (e.g. "js.Strict"). For most languages the option names start with an upper-case letter by
 * convention. A list of available options may be received using {@link Language#getOptions()}.
 * {@link Instrument#getOptions() Instrument options} are structured in the same way as language
 * options but start with the {@link Instrument#getId() instrument id} instead.
 * <p>
 * If system properties are {@link Engine.Builder#useSystemProperties(boolean) enabled}, which they
 * are by default, then all polyglot options maybe specified with the prefix "polyglot." (e.g.
 * "-Dpolyglot.js.Strict=true"). The system properties are read only once when the context or engine
 * instance is created. After that, changes to the system properties have no affect.
 * <p>
 * Each Graal language performs an initialization step before it can be used to execute code, after
 * which it remains initialized for the lifetime of the context. Initialization is by default lazy
 * and automatic, but initialization can be forced {@link Context#initialize(String) manually} if
 * needed.
 * <p>
 * <b>Example</b> for custom configuration using the context builder:
 *
 * <pre>
 * OutputStream myOut = new BufferedOutputStream()
 * Context context = Context.newBuilder("js", "R")
 *                          .out(myOut)
 *                          .option("js.Strict", "true")
 *                          .allowAllAccess(true)
 *                          .build();
 * context.eval("js", "42");
 * context.eval("R", "42");
 * context.close();
 * </pre>
 *
 * In this example:
 * <ul>
 * <li>At first, we create a new context and specify permitted languages as parameters.
 * <li>Secondly, we set the standard output stream to be used for the context.
 * <li>Then, we specify an option for JavaScript language only, by structuring the option key with
 * the language id followed by the option name;
 * <li>With {@link Builder#allowAllAccess(boolean)} we grant a new context instance with the same
 * access privileges as the host virtual machine.
 * <li>Next, we evaluate the expression "42" with language "js", which is the language identifier
 * for JavaScript. Since this is the first time we access JavaScript, it first gets
 * {@link #initialize(String) initialized} as well.
 * <li>Similarly to the previous line, the R language expression gets evaluated.
 * <li>Finally, we close the context, since it is no longer needed, to free all allocated resources.
 * Contexts are also {@link AutoCloseable} for use with the Java {@code try-with-resources}
 * statement.
 * </ul>
 *
 * <h3>Bindings</h3>
 *
 * The symbols of the top-most scope of a language can be accessed using the
 * {@link #getBindings(String) language bindings}. Each language provides its own bindings object
 * for a context. The bindings object may be used to read, modify, insert and delete members in the
 * top-most scope of the language. Certain languages may not allow write access to the bindings
 * object. See {@link #getBindings(String)} for details.
 * <p>
 * A context instance also provides access to the {@link #getPolyglotBindings() polyglot} bindings.
 * The polyglot bindings are shared between languages and may be used to exchange values. See
 * {@link #getPolyglotBindings()} for details.
 * <p>
 * <b>Examples</b> using language bindings from JavaScript:
 *
 * <pre>
 * Context context = Context.create("js");
 * Value jsBindings = context.getBindings("js")
 *
 * jsBindings.putMember("foo", 42);
 * assert context.eval("js", "foo").asInt() == 42;
 *
 * context.eval("js", "var bar = 42");
 * assert jsBindings.getMember("bar").asInt() == 42;
 *
 * assert jsBindings.getMember("Math")
 *                  .getMember("abs")
 *                  .execute(-42)
 *                  .asInt() == 42;
 * context.close();
 * </pre>
 *
 * In this example:
 * <ul>
 * <li>We create a new context with JavaScript as the only permitted language.
 * <li>Next, we load the JavaScript bindings object and assign it to a local variable
 * <code>jsBindings</code>.
 * <li>Then, we insert a new member <code>foo</code> into to the bindings object and verify that the
 * object is accessible within the language by reading from a global symbol with the same name.
 * <li>After that, we declare a new global variable in JavaScript and verify that it is accessible
 * as member of the language bindings object.
 * <li>Next, we access we access a JavaScript builtin named <code>Math.abs</code> symbol and execute
 * it with -42. This result is asserted to be 42.
 * <li>Finally, we close the context to free all allocated resources.
 * </ul>
 *
 * <h3>Host Interoperability</h3>
 *
 * It is often necessary to interact with values of the host runtime and Graal guest languages. Such
 * objects are referred to as <i>host objects</i>. Every Java value that is passed to a Graal
 * language is interpreted according to the specification described in {@link #asValue(Object)}.
 * Also see {@link Value#as(Class)} for further details.
 * <p>
 * By default only public classes, methods, and fields that are annotated with
 * {@link HostAccess.Export @HostAccess.Export} are accessible to the guest language. This policy
 * can be customized using {@link Builder#allowHostAccess(HostAccess)} when constructing the
 * context.
 *
 * <p>
 * <b>Example</b> using a Java object from JavaScript:
 *
 * <pre>
 * public class JavaRecord {
 *     &#64;HostAccess.Export public int x;
 *
 *     &#64;HostAccess.Export
 *     public String name() {
 *         return "foo";
 *     }
 * }
 * Context context = Context.create();
 *
 * JavaRecord record = new JavaRecord();
 * context.getBindings("js").putMember("javaRecord", record);
 *
 * context.eval("js", "javaRecord.x = 42");
 * assert record.x == 42;
 *
 * context.eval("js", "javaRecord.name()").asString().equals("foo");
 * </pre>
 *
 * <h3>Error Handling</h3>
 *
 * Program execution may fail when executing a guest language code or when accessing guest language
 * object. Almost all methods in the {@link Context} and {@link Value} API throw a
 * {@link PolyglotException} in case an error occurs. See {@link PolyglotException} for further
 * details on error handling.
 *
 * <h3>Isolation</h3>
 *
 * Each context is by default isolated from all other instances with respect to both language
 * evaluation semantics and resource consumption. By default, a new context instance has no access
 * to host resources, like threads, files or loading new host classes. To allow access to such
 * resources either the individual access right must be granted or
 * {@link Builder#allowAllAccess(boolean) all access} must be set to <code>true</code>.
 *
 * <p>
 * Contexts can be {@linkplain Builder#engine(Engine) configured} to share certain system resources
 * like ASTs or optimized code by specifying a single underlying engine. See {@link Engine} for more
 * details about code sharing.
 *
 * <h3>Proxies</h3>
 *
 * The {@link Proxy proxy interfaces} allow to mimic guest language objects, arrays, executables,
 * primitives and native objects in Graal languages. Every Graal language will treat proxy instances
 * like objects of that particular language. Multiple proxy interfaces can be implemented at the
 * same time. For example, it is useful to provide proxy values that are objects with members and
 * arrays at the same time.
 *
 * <h3>Thread-Safety</h3>
 *
 * It is safe to use a context instance from a single thread. It is also safe to use it with
 * multiple threads if they do not access the context at the same time. Whether a single context
 * instance may be used from multiple threads at the same time depends on if all initialized
 * languages support it. If initialized languages support multi-threading, then the context instance
 * may be used from multiple threads at the same time. If a context is used from multiple threads
 * and the language does not fit, then an {@link IllegalStateException} is thrown by the accessing
 * method.
 * <p>
 * Meta-data from the context's underlying {@link #getEngine() engine} can be retrieved safely by
 * any thread at any time.
 * <p>
 * A context may be {@linkplain #close() closed} from any thread, but only if the context is not
 * currently executing code. If the context is currently executing some code, a different thread may
 * kill the running execution and close the context using {@link #close(boolean)}.
 *
 * <h3>Pre-Initialization</h3>
 *
 * The context pre-initialization can be used to perform expensive builtin creation in the time of
 * native compilation.
 * <p>
 * The context pre-initialization is enabled by setting the system property
 * {@code polyglot.image-build-time.PreinitializeContexts} to a comma separated list of language ids
 * which should be pre-initialized, for example:
 * {@code -Dpolyglot.image-build-time.PreinitializeContexts=js,python}
 * <p>
 * See
 * {@code com.oracle.truffle.api.TruffleLanguage.patchContext(java.lang.Object, com.oracle.truffle.api.TruffleLanguage.Env)}
 * for details about pre-initialization for language implementers.
 *
 * @since 19.0
 */
public final class Context implements AutoCloseable {

    final AbstractContextImpl impl;

    Context(AbstractContextImpl impl) {
        this.impl = impl;
    }

    /**
     * Provides access to meta-data about the underlying Graal {@linkplain Engine engine}.
     *
     * @return Graal {@link Engine} being used by this context
     * @since 19.0
     */
    public Engine getEngine() {
        return impl.getEngineImpl(this);
    }

    /**
     * Evaluates a source object by using the {@linkplain Source#getLanguage() language} specified
     * in the source. The result is accessible as {@link Value value} and never returns
     * <code>null</code>. The first time a source is evaluated, it will be parsed. Consecutive
     * invocations of eval with the same source will only execute the already parsed code.
     * <p>
     * <b>Basic Example:</b>
     *
     * <pre>
     * try (Context context = Context.create()) {
     *     Source source = Source.newBuilder("js", "42", "mysource.js").build();
     *     Value result = context.eval(source);
     *     assert result.asInt() == 42;
     * }
     * </pre>
     *
     * @param source a source object to evaluate
     * @throws PolyglotException in case the guest language code parsing or evaluation failed.
     * @throws IllegalStateException if the context is already closed and the current thread is not
     *             allowed to access it.
     * @throws IllegalArgumentException if the language of the given source is not installed or the
     *             {@link Source#getMimeType() MIME type} is not supported with the language.
     * @return the evaluation result. The returned instance is never <code>null</code>, but the
     *         result might represent a {@link Value#isNull() null} value.
     * @since 19.0
     */
    public Value eval(Source source) {
        return impl.eval(source.getLanguage(), source.impl);
    }

    /**
     * Evaluates a guest language code literal, using a provided {@link Language#getId() language
     * id}. The result is accessible as {@link Value value} and never returns <code>null</code>. The
     * provided {@link CharSequence} must represent an immutable String.
     * <p>
     * <b>Basic Example:</b>
     *
     * <pre>
     * try (Context context = Context.create()) {
     *     Value result = context.eval("js", "42");
     *     assert result.asInt() == 42;
     * }
     * </pre>
     *
     * @throws PolyglotException in case the guest language code parsing or evaluation failed.
     * @throws IllegalArgumentException if the language does not exist or is not accessible.
     * @throws IllegalStateException if the context is already closed and the current thread is not
     *             allowed to access it, or if the given language is not installed.
     * @return the evaluation result. The returned instance is never <code>null</code>, but the
     *         result might represent a {@link Value#isNull() null} value.
     * @since 19.0
     */
    public Value eval(String languageId, CharSequence source) {
        return eval(Source.create(languageId, source));
    }

    /**
     * Parses but does not evaluate a given source by using the {@linkplain Source#getLanguage()
     * language} specified in the source and returns a {@link Value value} that can be
     * {@link Value#execute(Object...) executed}. If a parsing fails, e.g. due to a syntax error in
     * the source, then a {@link PolyglotException} will be thrown. In case of a syntax error the
     * {@link PolyglotException#isSyntaxError()} will return <code>true</code>. There is no
     * guarantee that only syntax errors will be thrown by this method. Any other guest language
     * exception might be thrown. If the validation succeeds then the method completes without
     * throwing an exception.
     * <p>
     * The result value only supports an empty set of arguments to {@link Value#execute(Object...)
     * execute}. If executed repeatedly then the source is evaluated multiple times.
     * {@link Source.Builder#interactive(boolean) Interactive} sources will print their result for
     * each execution of the parsing result to the {@link Builder#out(OutputStream) output} stream.
     * <p>
     * If the parsing succeeds and the source is {@link Source.Builder#cached(boolean) cached} then
     * the result will automatically be reused for consecutive calls to {@link #parse(Source)} or
     * {@link #eval(Source)}. If the validation should be performed for each invocation or the
     * result should not be remembered then {@link Source.Builder#cached(boolean) cached} can be set
     * to <code>false</code>. By default sources are cached.
     * <p>
     * <b>Basic Example:</b>
     *
     * <pre>
     * try (Context context = Context.create()) {
     *     Source source = Source.create("js", "42");
     *     Value value;
     *     try {
     *         value = context.parse(source);
     *         // parsing succeeded
     *     } catch (PolyglotException e) {
     *         if (e.isSyntaxError()) {
     *             SourceSection location = e.getSourceLocation();
     *             // syntax error detected at location
     *         } else {
     *             // other guest error detected
     *         }
     *         throw e;
     *     }
     *     // evaluate the parsed script
     *     value.execute();
     * }
     * </pre>
     *
     * @param source a source object to parse
     * @throws PolyglotException in case the guest language code parsing or validation failed.
     * @throws IllegalArgumentException if the language does not exist or is not accessible.
     * @throws IllegalStateException if the context is already closed and the current thread is not
     *             allowed to access it, or if the given language is not installed.
     * @since 20.2
     */
    public Value parse(Source source) throws PolyglotException {
        return impl.parse(source.getLanguage(), source.impl);
    }

    /**
     * Parses but does not evaluate a guest language code literal using a provided
     * {@link Language#getId() language id} and character sequence and returns a {@link Value value}
     * that can be {@link Value#execute(Object...) executed}. The provided {@link CharSequence} must
     * represent an immutable String. This method represents a short-hand for {@link #parse(Source)}
     * .
     * <p>
     * The result value only supports an empty set of arguments to {@link Value#execute(Object...)
     * execute}. If executed repeatedly then the source is evaluated multiple times.
     * {@link Source.Builder#interactive(boolean) Interactive} sources will print their result for
     * each execution of the parsing result to the {@link Builder#out(OutputStream) output} stream.
     * <p>
     *
     * <pre>
     * try (Context context = Context.create()) {
     *     Value value;
     *     try {
     *         value = context.parse("js", "42");
     *         // parsing succeeded
     *     } catch (PolyglotException e) {
     *         if (e.isSyntaxError()) {
     *             SourceSection location = e.getSourceLocation();
     *             // syntax error detected at location
     *         } else {
     *             // other guest error detected
     *         }
     *         throw e;
     *     }
     *     // evaluate the parsed script
     *     value.execute();
     * }
     * </pre>
     *
     * @throws PolyglotException in case the guest language code parsing or evaluation failed.
     * @throws IllegalArgumentException if the language does not exist or is not accessible.
     * @throws IllegalStateException if the context is already closed and the current thread is not
     *             allowed to access it, or if the given language is not installed.
     * @since 20.2
     */
    public Value parse(String languageId, CharSequence source) {
        return parse(Source.create(languageId, source));
    }

    /**
     * Returns polyglot bindings that may be used to exchange symbols between the host and guest
     * languages. All languages have unrestricted access to the polyglot bindings. The returned
     * bindings object always has {@link Value#hasMembers() members} and its members are
     * {@link Value#getMember(String) readable}, {@link Value#putMember(String, Object) writable}
     * and {@link Value#removeMember(String) removable}.
     * <p>
     * Guest languages may put and get members through language specific APIs. For example, in
     * JavaScript, symbols of the polyglot bindings can be accessed using
     * <code>Polyglot.import("name")</code> and set using
     * <code>Polyglot.export("name", value)</code>. Please see the individual language reference on
     * how to access these symbols.
     *
     * @throws IllegalStateException if context is already closed.
     * @since 19.0
     */
    public Value getPolyglotBindings() {
        return impl.getPolyglotBindings();
    }

    /**
     * Returns a value that represents the top-most bindings of a language. The top most bindings of
     * the language returns a {@link Value#getMember(String) member} for a symbol in the scope.
     * Languages may allow modifications of members of the returned bindings object at the
     * language's discretion. If the language has not been {@link #initialize(String) initialized}
     * yet, it will be initialized when the bindings are requested.
     *
     * @throws IllegalArgumentException if the language does not exist or is not accessible.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException in case the lazy initialization failed due to a guest language
     *             error.
     * @since 19.0
     */
    public Value getBindings(String languageId) {
        return impl.getBindings(languageId);
    }

    /**
     * Forces the initialization of a language. It is not necessary to explicitly initialize a
     * language, it will be initialized the first time it is used.
     *
     * @param languageId the identifier of the language to initialize.
     * @return <code>true</code> if the language was initialized. Returns <code>false</code> if it
     *         was already initialized.
     * @throws PolyglotException in case the initialization failed due to a guest language error.
     * @throws IllegalArgumentException if the language does not exist or is not accessible.
     * @throws IllegalStateException if the context is already closed.
     * @since 19.0
     */
    public boolean initialize(String languageId) {
        return impl.initializeLanguage(languageId);
    }

    /**
     * Resets all accumulators of resource limits for the associated context to zero.
     *
     * @since 19.3
     */
    public void resetLimits() {
        impl.resetLimits();
    }

    /**
     * Converts a host value to a polyglot {@link Value value} representation. This conversion is
     * applied implicitly whenever {@link Value#execute(Object...) execution} or
     * {@link Value#newInstance(Object...) instantiation} arguments are provided,
     * {@link Value#putMember(String, Object) members} and
     * {@link Value#setArrayElement(long, Object) array elements} are set or when a value is
     * returned by a {@link Proxy polyglot proxy}. It is not required nor efficient to explicitly
     * convert to polyglot values before performing these operations. This method is useful to
     * convert a {@link Value#as(Class) mapped} host value back to a polyglot value while preserving
     * the identity.
     * <p>
     * When a host value is converted to a polyglot value the following rules apply:
     * <ol>
     * <li>If the <code>hostValue</code> is <code>null</code>, then it will be interpreted as
     * polyglot {@link Value#isNull() null}.
     * <li>If the <code>hostValue</code> is already a {@link Value polyglot value}, then it will be
     * cast to {@link Value}.
     * <li>If the <code>hostValue</code> is an instance of {@link Byte}, {@link Short},
     * {@link Integer}, {@link Long}, {@link Float} or {@link Double}, then it will be interpreted
     * as polyglot {@link Value#isNumber() number}. Other subclasses of {@link Number} will be
     * interpreted as {@link Value#isHostObject() host object} (see later).
     * <li>If the <code>hostValue</code> is an instance of {@link Character} or {@link String}, then
     * it will be interpreted as polyglot {@link Value#isString() string}.
     * <li>If the <code>hostValue</code> is an instance of {@link Boolean}, then it will be
     * interpreted as polyglot {@link Value#isBoolean() boolean}.
     * <li>If the <code>hostValue</code> is a {@link Proxy polyglot proxy}, then it will be
     * interpreted according to the behavior specified by the proxy. See the javadoc of the proxy
     * subclass for further details.
     * <li>If the <code>hostValue</code> is a non-primitive {@link Value#as(Class) mapped Java
     * value}, then the original value will be restored. For example, if a guest language object was
     * mapped to {@link Map}, then the original object identity will be preserved when converting
     * back to a polyglot value.
     * <li>Any other <code>hostValue</code> will be interpreted as {@link Value#isHostObject() host
     * object}. Host objects expose all their public java fields and methods as
     * {@link Value#getMember(String) members}. In addition, Java arrays and subtypes of
     * {@link List} will be interpreted as a value with {@link Value#hasArrayElements() array
     * elements} and single method interfaces annotated with {@link FunctionalInterface} are
     * {@link Value#execute(Object...) executable} directly. Java {@link Class} instances are
     * interpreted as {@link Value#canInstantiate() instantiable}, but they do not expose Class
     * methods as members.
     * </ol>
     * <p>
     * <b>Basic Examples:</b>
     *
     * The following assertion statements always hold:
     *
     * <pre>
     * Context context = Context.create();
     * assert context.asValue(null).isNull();
     * assert context.asValue(42).isNumber();
     * assert context.asValue("42").isString();
     * assert context.asValue('c').isString();
     * assert context.asValue(new String[0]).hasArrayElements();
     * assert context.asValue(new ArrayList<>()).isHostObject();
     * assert context.asValue(new ArrayList<>()).hasArrayElements();
     * assert context.asValue((Supplier<Integer>) () -> 42).execute().asInt() == 42;
     * </pre>
     *
     * <h1>Mapping to Java methods and fields</h1>
     *
     * When Java host objects are passed to guest languages, their public methods and fields are
     * provided as {@link Value#getMember(String) members}. Methods and fields are grouped by name,
     * so only one member is exposed for each name.
     * <p>
     * {@link Class} objects have a member named {@code static} referring to the class's companion
     * object containing the static methods of the class. Likewise, the companion object has a
     * member named {@code class} that points back to the class object.
     * <p>
     * When an argument value needs to be mapped to match a required Java method parameter type,
     * then the semantics of {@link Value#as(Class) host value mapping} is used. The result of the
     * mapping is equivalent of calling {@link Value#as(Class)} with the parameter type. Therefore,
     * a {@link ClassCastException} or {@link NullPointerException} is thrown if a parameter value
     * cannot be cast to the required parameter type.
     * <p>
     * Overloaded java methods are selected based on the provided arguments. In case multiple mapped
     * Java methods with the same name are applicable for {@link Value#execute(Object...)
     * executions} or {@link Value#newInstance(Object...) instantiations}, then the method with the
     * most concrete method with applicable arguments will be called.
     * <p>
     * The following parameter type hierarchy is used for a method resolution. Left-most parameter
     * types are prioritized over types to their right.
     * <ul>
     * <li>{@link Value#isBoolean() Boolean} values: boolean, Boolean, Object
     * <li>String values: char, Character, String, CharSequence, Object
     * <li>Number values: byte, Byte, short, Short, int, Integer, long, Long, float, Float, double,
     * Double, Number, Object
     * <li>Other values are resolved based on their Java type hierarchy.
     * </ul>
     * If there are multiple concrete methods or too many arguments provided, then an illegal
     * argument type error will be raised.
     * <p>
     * <b>Advanced Example:</b>
     *
     * This example first creates a new instance of the Java class <code>Record</code> and inspects
     * it using the polyglot value API. Later, a host value is converted to a polyglot value using
     * JavaScript guest language.
     * <p>
     * In the following examples all assertions hold.
     *
     * <pre>
     * <b>class</b> JavaRecord {
     *   <b>public int</b> x = 42;
     *   <b>public double</b> y = 42.0;
     *   <b>public</b> String name() {
     *     <b>return</b> "foo";
     *   }
     * }
     * Context context = Context.create();
     * Value record = context.asValue(new JavaRecord());
     * assert record.getMember("x").asInt() == 42;
     * assert record.getMember("y").asDouble() == 42.0d;
     * assert record.getMember("name").execute().asString().equals("foo");
     *
     * assert context.eval("js", "(function(record) record.x)")
     *               .execute(record).asInt() == 42;
     * assert context.eval("js", "(function(record) record.y)")
     *               .execute(record).asDouble() == 42.0d;
     * assert context.eval("js", "(function(record) record.name())")
     *               .execute(record).asString().equals("foo");
     * </pre>
     *
     * @see Value#as(Class)
     * @throws IllegalStateException if the context is already closed
     * @param hostValue the host value to convert to a polyglot value.
     * @return the polyglot value.
     * @since 19.0
     */
    public Value asValue(Object hostValue) {
        return impl.asValue(hostValue);
    }

    /**
     * Explicitly enters the context on the current thread. A context needs to be entered and left
     * for any operation to be performed. For example, before and after invoking the
     * {@link Value#execute(Object...) execute} method. This can be inefficient if a very high
     * number of simple operations needs to be performed. By {@link #enter() entering} and
     * {@link #leave() leaving} once explicitly, the overhead for entering/leaving the context for
     * each operation can be eliminated. Contexts can be entered multiple times on the same thread.
     *
     * @throws IllegalStateException if the context is already {@link #close() closed}.
     * @throws PolyglotException if a language has denied execution on the current thread.
     * @see #leave() leave a context.
     * @since 19.0
     */
    public void enter() {
        impl.explicitEnter(this);
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Context) {
            Context other = ((Context) obj);
            return impl.equals(other.impl);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public int hashCode() {
        return impl.hashCode();
    }

    /**
     * Explicitly leaves the context on the current thread. The context must be {@link #enter()
     * entered} before calling this method.
     *
     * @throws IllegalStateException if the context is already closed or if the context was not
     *             {@link #enter() entered} on the current thread.
     * @see #enter() enter a context.
     * @since 19.0
     */
    public void leave() {
        impl.explicitLeave(this);
    }

    /**
     * Closes the context and frees up potentially allocated native resources. A context cannot free
     * all native resources allocated automatically. For this reason it is necessary to close
     * contexts after use. If a context is cancelled, then the executing thread will throw a
     * {@link PolyglotException}. The exception indicates that it was
     * {@link PolyglotException#isCancelled() cancelled}. Please note, canceling a single context
     * can negatively affect the performance of other executing contexts constructed with the same
     * engine.
     * <p>
     * If internal errors occur during context closing, then they are printed to the configured
     * {@link Builder#err(OutputStream) error output stream}. If a context was closed, then its
     * methods will throw an {@link IllegalStateException} when invoked. If an attempt to close a
     * context was successful, then consecutive calls to close have no effect.
     *
     * @param cancelIfExecuting if <code>true</code> then currently executing contexts will be
     *            {@link PolyglotException#isCancelled() cancelled}, else an
     *            {@link IllegalStateException} is thrown.
     * @see Engine#close() close an engine.
     * @throws PolyglotException in case the close failed due to a guest language error.
     * @throws IllegalStateException if the context is still running and cancelIfExecuting is
     *             <code>false</code>
     * @since 19.0
     */
    public void close(boolean cancelIfExecuting) {
        impl.close(this, cancelIfExecuting);
    }

    /**
     * Closes this context and frees up potentially allocated native resources. A context may not
     * free all native resources allocated automatically. For this reason it is recommended to close
     * contexts after use. If the context is currently being executed on another thread, then an
     * {@link IllegalStateException} is thrown. To close concurrently executing contexts see
     * {@link #close(boolean)}.
     * <p>
     * If internal errors occur during the context closure, then they are printed to the configured
     * {@link Builder#err(OutputStream) error output stream}. If a context was closed, then its
     * methods will throw an {@link IllegalStateException}, when invoked. If an attempt to close a
     * context was successful, then consecutive calls to close have no effect.
     *
     * @throws PolyglotException in case the close failed due to a guest language error.
     * @throws IllegalStateException if the context is currently executing on another thread.
     * @see Engine#close() close an engine.
     * @since 19.0
     */
    public void close() {
        close(false);
    }

    /**
     * Returns the currently entered polyglot context. A context will be entered if the current
     * executing Java method is called by a Graal guest language or if a context is entered
     * explicitly using {@link Context#enter()} on the current thread. The returned context may be
     * used to:
     * <ul>
     * <li>Evaluate guest language code from {@link #eval(String, CharSequence) string literals} or
     * {@link #eval(Source) file} sources.
     * <li>{@link #asValue(Object) Convert} Java values to {@link Value polyglot values}.
     * <li>Access top-level {@link #getBindings(String) bindings} of other languages.
     * <li>Access {@link #getPolyglotBindings() polyglot bindings}.
     * <li>Access meta-data like available {@link Engine#getLanguages() languages} or
     * {@link Engine#getOptions() options} of the {@link #getEngine() engine}.
     * </ul>
     * <p>
     * The returned context can <b>not</b> be used to {@link #enter() enter} , {@link #leave()
     * leave} or {@link #close() close} the context or {@link #getEngine() engine}. Invoking such
     * methods will cause an {@link IllegalStateException} to be thrown. This ensures that only the
     * {@link #create(String...) creator} of a context is allowed to enter, leave or close a
     * context.
     * <p>
     * The current entered context may change. It is therefore required to call {@link #getCurrent()
     * getCurrent} every time a context is needed. The current entered context should not be cached
     * in static fields.
     *
     * @throws IllegalStateException if no context is currently entered.
     * @since 19.0
     */
    public static Context getCurrent() {
        return Engine.getImpl().getCurrentContext();
    }

    /**
     * Creates a context with default configuration.
     *
     * @param permittedLanguages names of languages permitted in this context. If no languages are
     *            provided, then all installed languages will be permitted.
     * @return a new context
     * @since 19.0
     */
    public static Context create(String... permittedLanguages) {
        return newBuilder(permittedLanguages).build();
    }

    /**
     * Creates a builder for constructing a context with custom configuration.
     *
     * @param permittedLanguages names of languages permitted in the context. If no languages are
     *            provided, then all installed languages will be permitted.
     * @return a builder that can create a context
     * @since 19.0
     */
    public static Builder newBuilder(String... permittedLanguages) {
        return EMPTY.new Builder(permittedLanguages);
    }

    private static final Context EMPTY = new Context(null);

    static final Predicate<String> UNSET_HOST_LOOKUP = new Predicate<String>() {
        public boolean test(String t) {
            return false;
        }
    };

    static final Predicate<String> NO_HOST_CLASSES = new Predicate<String>() {
        public boolean test(String t) {
            return false;
        }
    };

    static final Predicate<String> ALL_HOST_CLASSES = new Predicate<String>() {
        public boolean test(String t) {
            return true;
        }
    };

    /**
     * Builder class to construct {@link Context} instances. A builder instance is not thread-safe
     * and must not be used from multiple threads at the same time.
     *
     * @see Context
     * @since 19.0
     */
    @SuppressWarnings("hiding")
    public final class Builder {

        private Engine sharedEngine;
        private String[] onlyLanguages;

        private OutputStream out;
        private OutputStream err;
        private InputStream in;
        private Map<String, String> options;
        private Map<String, String[]> arguments;
        private Predicate<String> hostClassFilter = UNSET_HOST_LOOKUP;
        private Boolean allowNativeAccess;
        private Boolean allowCreateThread;
        private boolean allowAllAccess;
        private Boolean allowIO;
        private Boolean allowHostClassLoading;
        private Boolean allowExperimentalOptions;
        private Boolean allowHostAccess;
        private PolyglotAccess polyglotAccess;
        private HostAccess hostAccess;
        private FileSystem customFileSystem;
        private MessageTransport messageTransport;
        private Object customLogHandler;
        private Boolean allowCreateProcess;
        private ProcessHandler processHandler;
        private EnvironmentAccess environmentAccess;
        private ResourceLimits resourceLimits;
        private Map<String, String> environment;
        private ZoneId zone;
        private Path currentWorkingDirectory;
        private ClassLoader hostClassLoader;

        Builder(String... onlyLanguages) {
            Objects.requireNonNull(onlyLanguages);
            for (String onlyLanguage : onlyLanguages) {
                Objects.requireNonNull(onlyLanguage);
            }
            this.onlyLanguages = onlyLanguages;
        }

        /**
         * Explicitly sets the underlying engine to use. By default, every context has its own
         * isolated engine. If multiple contexts are created from one engine, then they may
         * share/cache certain system resources like ASTs or optimized code by specifying a single
         * underlying engine. See {@link Engine} for more details about system resource sharing.
         *
         * @since 19.0
         */
        public Builder engine(Engine engine) {
            Objects.requireNonNull(engine);
            this.sharedEngine = engine;
            return this;
        }

        /**
         * Sets the standard output stream to be used for the context. If not set, then the standard
         * output stream configured for the {@link #engine(Engine) engine} or standard error stream
         * is used.
         *
         * @since 19.0
         */
        public Builder out(OutputStream out) {
            Objects.requireNonNull(out);
            this.out = out;
            return this;
        }

        /**
         * Sets the error output stream to be used for the context. If not set, then either the
         * error stream configured for the {@link #engine(Engine) engine} or standard error stream
         * is used.
         *
         * @since 19.0
         */
        public Builder err(OutputStream err) {
            Objects.requireNonNull(err);
            this.err = err;
            return this;
        }

        /**
         * Sets the input stream to be used for the context. If not set, then either the input
         * stream configured for the {@link #engine(Engine) engine} or standard in stream is used.
         *
         * @since 19.0
         */
        public Builder in(InputStream in) {
            Objects.requireNonNull(in);
            this.in = in;
            return this;
        }

        /**
         * Allows guest languages to access the host language by loading new classes. Default is
         * <code>false</code>. If {@link #allowAllAccess(boolean) all access} is set to
         * <code>true</code>, then host access is enabled if not disallowed explicitly.
         *
         * @since 19.0
         * @deprecated use {@link #allowHostAccess(HostAccess)} or
         *             {@link #allowHostClassLookup(Predicate)} instead.
         */
        @Deprecated
        public Builder allowHostAccess(boolean enabled) {
            this.allowHostAccess = enabled;
            return this;
        }

        /**
         * Configures which public constructors, methods or fields of public classes are accessible
         * by guest applications. By default if {@link #allowAllAccess(boolean)} is
         * <code>false</code> the {@link HostAccess#EXPLICIT} policy will be used, otherwise
         * {@link HostAccess#ALL}.
         *
         * @see HostAccess#EXPLICIT EXPLICIT - to allow explicitly annotated constructors, methods
         *      or fields.
         * @see HostAccess#ALL ALL - to allow unrestricted access (use only for trusted guest
         *      applications)
         * @see HostAccess#NONE NONE - to not allow any access
         * @see HostAccess#newBuilder() newBuilder() - to create a custom configuration.
         *
         * @since 19.0
         */
        public Builder allowHostAccess(HostAccess config) {
            this.hostAccess = config;
            return this;
        }

        /**
         * Allows guest languages to access the native interface.
         *
         * @since 19.0
         */
        public Builder allowNativeAccess(boolean enabled) {
            this.allowNativeAccess = enabled;
            return this;
        }

        /**
         * If <code>true</code>, allows guest languages to create new threads. Default is
         * <code>false</code>. If {@link #allowAllAccess(boolean) all access} is set to
         * <code>true</code>, then the creation of threads is enabled if not allowed explicitly.
         * Threads created by guest languages are closed, when the context is {@link Context#close()
         * closed}.
         *
         * @since 19.0
         */
        public Builder allowCreateThread(boolean enabled) {
            this.allowCreateThread = enabled;
            return this;
        }

        /**
         * Sets the default value for all privileges. If not explicitly specified, then all access
         * is <code>false</code>. If all access is enabled then certain privileges may still be
         * disabled by configuring it explicitly using the builder (either before or after the call
         * to {@link #allowAllAccess(boolean) allowAllAccess()}). Allowing all access should only be
         * set if the guest application is fully trusted.
         * <p>
         * If <code>true</code>, grants the context the same access privileges as the host virtual
         * machine. If the host VM runs without a {@link SecurityManager security manager} enabled,
         * then enabling all access gives the guest languages full control over the host process.
         * Otherwise, Java {@link SecurityManager security manager} is in control of restricting the
         * privileges of the polyglot context. If new privilege restrictions are added to the
         * polyglot API, then they will default to full access.
         * <p>
         * Grants full access to the following privileges by default:
         * <ul>
         * <li>The {@link #allowCreateThread(boolean) creation} and use of new threads.
         * <li>The access to public {@link #allowHostAccess(HostAccess) host classes}.
         * <li>The loading of new {@link #allowHostClassLoading(boolean) host classes} by adding
         * entries to the class path.
         * <li>Exporting new members into the polyglot {@link Context#getPolyglotBindings()
         * bindings}.
         * <li>Unrestricted {@link #allowIO(boolean) IO operations} on host system.
         * <li>Passing {@link #allowExperimentalOptions(boolean) experimental options}.
         * <li>The {@link #allowCreateProcess(boolean) creation} and use of new sub-processes.
         * <li>The {@link #allowEnvironmentAccess(org.graalvm.polyglot.EnvironmentAccess) access} to
         * process environment variables.
         * </ul>
         *
         * @param enabled <code>true</code> for all access by default.
         * @since 19.0
         */
        public Builder allowAllAccess(boolean enabled) {
            this.allowAllAccess = enabled;
            return this;
        }

        /**
         * If host class loading is enabled, then the guest language is allowed to load new host
         * classes via jar or class files. If {@link #allowAllAccess(boolean) all access} is set to
         * <code>true</code>, then the host class loading is enabled if it is not disallowed
         * explicitly. For host class loading to be useful, {@link #allowIO(boolean) IO} operations
         * {@link #allowHostClassLookup(Predicate) host class lookup}, and the
         * {@link #allowHostAccess(org.graalvm.polyglot.HostAccess) host access policy} needs to be
         * configured as well.
         *
         * @see #allowHostAccess(HostAccess)
         * @see #allowHostClassLookup(Predicate)
         * @since 19.0
         */
        public Builder allowHostClassLoading(boolean enabled) {
            this.allowHostClassLoading = enabled;
            return this;
        }

        /**
         * Sets a filter that specifies the Java host classes that can be looked up by the guest
         * application. If set to <code>null</code> then no class lookup is allowed and relevant
         * language builtins are not available (e.g. <code>Java.type</code> in JavaScript). If the
         * <code>classFilter</code> parameter is set to a filter predicate, then language builtins
         * are available and classes can be looked up if the filter predicate returns
         * <code>true</code> for the fully qualified class name. If the filter returns
         * <code>false</code>, then the class cannot be looked up and as a result throws a guest
         * language error when accessed. By default and if {@link #allowAllAccess(boolean) all
         * access} is <code>false</code>, host class lookup is disabled. By default and if
         * {@link #allowAllAccess(boolean) all access} is <code>true</code>, then all classes may be
         * looked up by the guest application.
         * <p>
         * In order to access class members looked up by the guest application a
         * {@link #allowHostAccess(org.graalvm.polyglot.HostAccess) host access policy} needs to be
         * set or {@link #allowAllAccess(boolean) all access} needs to be set to <code>true</code>.
         * <p>
         * To load new classes the context uses the
         * {@link Context.Builder#hostClassLoader(java.lang.ClassLoader) hostClassLoader} if
         * specified or the {@link Thread#getContextClassLoader() context class loader} that will be
         * captured when the context is {@link #build() built}. If an explicit
         * {@link #engine(Engine) engine} was specified, then the context class loader at engine
         * {@link Engine.Builder#build() build-time} will be used instead. When the Java module
         * system is available (>= JDK 9) then only classes are accessible that are exported to the
         * unnamed module of the captured class loader.
         * <p>
         * <h3>Example usage with JavaScript:</h3>
         *
         * <pre>
         * public class MyClass {
         *     &#64;HostAccess.Export
         *     public int accessibleMethod() {
         *         return 42;
         *     }
         *
         *     public static void main(String[] args) {
         *         try (Context context = Context.newBuilder() //
         *                         .allowHostClassLookup(c -> c.equals("myPackage.MyClass")) //
         *                         .build()) {
         *             int result = context.eval("js", "" +
         *                             "var MyClass = Java.type('myPackage.MyClass');" +
         *                             "new MyClass().accessibleMethod()").asInt();
         *             assert result == 42;
         *         }
         *     }
         * }
         * </pre>
         *
         * <h4>In this example:</h4>
         * <ul>
         * <li>We create a new context with the {@link Builder#allowHostClassLookup(Predicate)
         * permission} to look up the class <code>myPackage.MyClass</code> in the guest language
         * application.
         * <li>We evaluate a JavaScript code snippet that accesses the Java class
         * <code>myPackage.MyClass</code> using the <code>Java.type</code> builtin provided by the
         * JavaScript language implementation. Other classes can only be looked up if the provided
         * class filter returns <code>true</code> for their name.
         * <li>We create a new instance of the Java class <code>MyClass</code> by using the
         * JavaScript <code>new</code> keyword.
         * <li>We call the method <code>accessibleMethod</code> which returns <code>42</code>. The
         * method is accessible to the guest language because because the enclosing class and the
         * declared method are public, as well as annotated with the
         * {@link HostAccess.Export @HostAccess.Export} annotation. Which Java members of classes
         * are accessible can be configured using the {@link #allowHostAccess(HostAccess) host
         * access policy}.
         * </ul>
         *
         * @param classFilter a predicate that returns <code>true</code> or <code>false</code> for a
         *            qualified Java class name or <code>null</code> to disable host class lookup.
         * @see #allowHostClassLoading(boolean) allowHostClassLoading - to allow loading of classes.
         * @see #allowHostAccess(HostAccess) allowHostAccess - to configure the access policy of
         *      host values for guest languages.
         * @since 19.0
         */
        public Builder allowHostClassLookup(Predicate<String> classFilter) {
            this.hostClassFilter = classFilter;
            return this;
        }

        /**
         * Allow experimental options to be used for language options. Do not use experimental
         * options in production environments. If set to {@code false} (the default), then passing
         * an experimental option results in an {@link IllegalArgumentException} when the context is
         * built.
         *
         * @since 19.0
         */
        public Builder allowExperimentalOptions(boolean enabled) {
            this.allowExperimentalOptions = enabled;
            return this;
        }

        /**
         * Allow polyglot access using the provided policy. If {@link #allowAllAccess(boolean) all
         * access} is <code>true</code> then the default polyglot access policy is
         * {@link PolyglotAccess#ALL}, otherwise {@link PolyglotAccess#NONE}. The provided access
         * policy must not be <code>null</code>.
         *
         * @since 19.0
         */
        public Builder allowPolyglotAccess(PolyglotAccess accessPolicy) {
            Objects.requireNonNull(accessPolicy);
            this.polyglotAccess = accessPolicy;
            return this;
        }

        /**
         * Sets a class filter that allows to limit the classes that are allowed to be loaded by
         * guest languages. If the filter returns <code>true</code>, then the class is accessible,
         * otherwise it is not accessible and throws a guest language error when accessed. In order
         * to have an effect, {@link #allowHostAccess(org.graalvm.polyglot.HostAccess)} or
         * {@link #allowAllAccess(boolean)} needs to be set to <code>true</code>.
         *
         * @param classFilter a predicate that returns <code>true</code> or <code>false</code> for a
         *            java qualified class name.
         * @since 19.0
         * @deprecated use {@link #allowHostClassLookup(Predicate)} instead.
         */
        @Deprecated
        public Builder hostClassFilter(Predicate<String> classFilter) {
            Objects.requireNonNull(classFilter);
            this.hostClassFilter = classFilter;
            return this;
        }

        /**
         * Sets an option for this {@link Context context}. By default, any options for the
         * {@link Engine#getOptions() engine}, {@link Language#getOptions() language} or
         * {@link Instrument#getOptions() instrument} can be set for a context. If an
         * {@link #engine(Engine) explicit engine} is set for the context, then only language
         * options can be set. Instrument and engine options can be set exclusively on the explicit
         * engine instance. If a language option is set for the context and the engine, then the
         * option of the context is going to take precedence.
         * <p>
         * If one of the set option keys or values is invalid, then an
         * {@link IllegalArgumentException} is thrown when the context is {@link #build() built}.
         * The given key and value must not be <code>null</code>.
         *
         * @see Engine.Builder#option(String, String) To specify an option for the engine.
         * @since 19.0
         */
        public Builder option(String key, String value) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            if (this.options == null) {
                this.options = new HashMap<>();
            }
            this.options.put(key, value);
            return this;
        }

        /**
         * Shortcut for setting multiple {@link #option(String, String) options} using a map. All
         * values of the provided map must be non-null.
         *
         * @param options a map options.
         * @see #option(String, String) To set a single option.
         * @since 19.0
         */
        public Builder options(Map<String, String> options) {
            for (String key : options.keySet()) {
                option(key, options.get(key));
            }
            return this;
        }

        /**
         * Sets the guest language application arguments for a language {@link Context context}.
         * Application arguments are typically made available to guest language implementations. It
         * depends on the language if and how they are accessible within the
         * {@link Context#eval(Source) evaluated} guest language scripts. Passing no arguments to a
         * language is equivalent to providing an empty arguments array.
         *
         * @param language the language id of the primary language.
         * @param args an array of arguments passed to the guest language program.
         * @throws IllegalArgumentException if an invalid language id was specified.
         * @since 19.0
         */
        public Builder arguments(String language, String[] args) {
            Objects.requireNonNull(language);
            Objects.requireNonNull(args);
            String[] newArgs = args;
            if (args.length > 0) {
                newArgs = new String[args.length];
                for (int i = 0; i < args.length; i++) { // defensive copy
                    newArgs[i] = Objects.requireNonNull(args[i]);
                }
            }
            if (arguments == null) {
                arguments = new HashMap<>();
            }
            arguments.put(language, newArgs);
            return this;
        }

        /**
         * If <code>true</code>, allows guest language to perform unrestricted IO operations on host
         * system. Default is <code>false</code>. If {@link #allowAllAccess(boolean) all access} is
         * set to <code>true</code>, then IO is enabled if not allowed explicitly.
         *
         * @param enabled {@code true} to enable Input/Output
         * @return the {@link Builder}
         * @since 19.0
         */
        public Builder allowIO(final boolean enabled) {
            allowIO = enabled;
            return this;
        }

        /**
         * Installs a new {@link FileSystem}.
         *
         * @param fileSystem the file system to be installed
         * @return the {@link Builder}
         * @since 19.0
         */
        public Builder fileSystem(final FileSystem fileSystem) {
            Objects.requireNonNull(fileSystem, "FileSystem must be non null.");
            this.customFileSystem = fileSystem;
            return this;
        }

        /**
         * Take over the transport of messages communication with a server peer. Provide an
         * implementation of {@link MessageTransport} to virtualize a transport of messages to a
         * server endpoint.
         * {@link MessageTransport#open(java.net.URI, org.graalvm.polyglot.io.MessageEndpoint)}
         * corresponds to accept of a server socket.
         *
         * @param serverTransport an implementation of message transport interceptor
         * @see MessageTransport
         * @since 19.0
         */
        public Builder serverTransport(final MessageTransport serverTransport) {
            Objects.requireNonNull(serverTransport, "MessageTransport must be non null.");
            this.messageTransport = serverTransport;
            return this;
        }

        /**
         * Installs a new logging {@link Handler}. The logger's {@link Level} configuration is done
         * using the {@link #options(java.util.Map) Context's options}. The level option key has the
         * following format: {@code log.languageId.loggerName.level} or
         * {@code log.instrumentId.loggerName.level}. The value is either the name of a pre-defined
         * {@link Level} constant or a numeric {@link Level} value. If not explicitly set in
         * options, the level is inherited from the parent logger.
         * <p>
         * <b>Examples</b> of setting log level options:<br>
         * {@code builder.option("log.level","FINE");} sets the {@link Level#FINE FINE level} to all
         * {@code TruffleLogger}s.<br>
         * {@code builder.option("log.js.level","FINE");} sets the {@link Level#FINE FINE level} to
         * JavaScript {@code TruffleLogger}s.<br>
         * {@code builder.option("log.js.com.oracle.truffle.js.parser.JavaScriptLanguage.level","FINE");}
         * sets the {@link Level#FINE FINE level} to {@code TruffleLogger} for the
         * {@code JavaScriptLanguage} class.<br>
         * <p>
         * If the {@code logHandler} is not set on {@link Engine} nor on {@link Context}, the log
         * messages are printed to {@link #err(java.io.OutputStream) Context's error output stream}.
         *
         * @param logHandler the {@link Handler} to use for logging in built {@link Context}. The
         *            passed {@code logHandler} is closed when the context is {@link Context#close()
         *            closed}.
         * @return the {@link Builder}
         * @since 19.0
         */
        public Builder logHandler(final Handler logHandler) {
            Objects.requireNonNull(logHandler, "Handler must be non null.");
            this.customLogHandler = logHandler;
            return this;
        }

        /**
         * Sets the default time zone to be used for this context. If not set, or explicitly set to
         * <code>null</code> then the {@link ZoneId#systemDefault() system default} zone will be
         * used.
         *
         * @return the {@link Builder}
         * @see ZoneId#systemDefault()
         * @since 19.2.0
         */
        public Builder timeZone(final ZoneId zone) {
            this.zone = zone;
            return this;
        }

        /**
         * Installs a new logging {@link Handler} using given {@link OutputStream}. The logger's
         * {@link Level} configuration is done using the {@link #options(java.util.Map) Context's
         * options}. The level option key has the following format:
         * {@code log.languageId.loggerName.level} or {@code log.instrumentId.loggerName.level}. The
         * value is either the name of pre-defined {@link Level} constant or a numeric {@link Level}
         * value. If not explicitly set in options the level is inherited from the parent logger.
         * <p>
         * <b>Examples</b> of setting log level options:<br>
         * {@code builder.option("log.level","FINE");} sets the {@link Level#FINE FINE level} to all
         * {@code TruffleLogger}s.<br>
         * {@code builder.option("log.js.level","FINE");} sets the {@link Level#FINE FINE level} to
         * JavaScript {@code TruffleLogger}s.<br>
         * {@code builder.option("log.js.com.oracle.truffle.js.parser.JavaScriptLanguage.level","FINE");}
         * sets the {@link Level#FINE FINE level} to {@code TruffleLogger} for the
         * {@code JavaScriptLanguage} class.<br>
         * <p>
         * If the {@code logHandler} is not set on {@link Engine} nor on {@link Context} the log
         * messages are printed to {@link #out(java.io.OutputStream) Context's standard output
         * stream}.
         *
         * @param logOut the {@link OutputStream} to use for logging in built {@link Context}. The
         *            passed {@code logOut} stream is closed when the context is
         *            {@link Context#close() closed}.
         * @return the {@link Builder}
         * @since 19.0
         */
        public Builder logHandler(final OutputStream logOut) {
            Objects.requireNonNull(logOut, "LogOut must be non null.");
            this.customLogHandler = logOut;
            return this;
        }

        /**
         * If <code>true</code>, allows guest language to execute external processes. Default is
         * <code>false</code>. If {@link #allowAllAccess(boolean) all access} is set to
         * <code>true</code>, then process creation is enabled if not denied explicitly.
         *
         * @param enabled {@code true} to enable external process creation
         * @since 19.1.0
         */
        public Builder allowCreateProcess(boolean enabled) {
            this.allowCreateProcess = enabled;
            return this;
        }

        /**
         * Installs a {@link ProcessHandler} responsible for external process creation.
         *
         * @param handler the handler to be installed
         * @since 19.1.0
         */
        public Builder processHandler(ProcessHandler handler) {
            Objects.requireNonNull(handler, "Handler must be non null.");
            this.processHandler = handler;
            return this;
        }

        /**
         * Assigns resource limit configuration to a context. By default no resource limits are
         * assigned. The limits will be enabled for all contexts created using this builder.
         * Assigning a limit may have performance impact of all contexts that run with the same
         * engine.
         *
         * @see ResourceLimits for usage examples
         * @since 19.3.0
         */
        public Builder resourceLimits(ResourceLimits limits) {
            this.resourceLimits = limits;
            return this;
        }

        /**
         * Allow environment access using the provided policy. If {@link #allowAllAccess(boolean)
         * all access} is {@code true} then the default environment access policy is
         * {@link EnvironmentAccess#INHERIT}, otherwise {@link EnvironmentAccess#NONE}. The provided
         * access policy must not be {@code null}.
         *
         * @param accessPolicy the {@link EnvironmentAccess environment access policy}
         * @since 19.1.0
         */
        public Builder allowEnvironmentAccess(EnvironmentAccess accessPolicy) {
            Objects.requireNonNull(accessPolicy, "AccessPolicy must be non null.");
            this.environmentAccess = accessPolicy;
            return this;
        }

        /**
         * Sets an environment variable.
         *
         * @param name the environment variable name
         * @param value the environment variable value
         * @since 19.1.0
         */
        public Builder environment(String name, String value) {
            Objects.requireNonNull(name, "Name must be non null.");
            Objects.requireNonNull(value, "Value must be non null.");
            if (this.environment == null) {
                this.environment = new HashMap<>();
            }
            this.environment.put(name, value);
            return this;
        }

        /**
         * Shortcut for setting multiple {@link #environment(String, String) environment variables}
         * using a map. All values of the provided map must be non-null.
         *
         * @param env environment variables
         * @see #environment(String, String) To set a single environment variable.
         * @since 19.1.0
         */
        public Builder environment(Map<String, String> env) {
            Objects.requireNonNull(env, "Env must be non null.");
            for (Map.Entry<String, String> e : env.entrySet()) {
                environment(e.getKey(), e.getValue());
            }
            return this;
        }

        /**
         * Sets the current working directory used by the guest application to resolve relative
         * paths. When the Context is built, the given directory is set as the current working
         * directory on the Context's file system using the
         * {@link FileSystem#setCurrentWorkingDirectory(java.nio.file.Path)
         * FileSystem.setCurrentWorkingDirectory} method.
         *
         * @param workingDirectory the new current working directory
         * @throws NullPointerException when {@code workingDirectory} is {@code null}
         * @throws IllegalArgumentException when {@code workingDirectory} is a relative path
         * @since 20.0.0
         */
        public Builder currentWorkingDirectory(Path workingDirectory) {
            Objects.requireNonNull(workingDirectory, "WorkingDirectory must be non null.");
            if (!workingDirectory.isAbsolute()) {
                throw new IllegalArgumentException("WorkingDirectory must be an absolute path.");
            }
            this.currentWorkingDirectory = workingDirectory;
            return this;
        }

        /**
         * Sets a host class loader. If set the given {@code classLoader} is used to load host
         * classes and it's also set as a {@link Thread#setContextClassLoader(java.lang.ClassLoader)
         * context ClassLoader} during code execution. Otherwise the ClassLoader that was captured
         * when the context was {@link #build() built} is used to to load host classes and the
         * {@link Thread#setContextClassLoader(java.lang.ClassLoader) context ClassLoader} is not
         * set during code execution. Setting the hostClassLoader has a negative effect on enter and
         * leave performance.
         *
         * @param classLoader the host class loader
         * @since 20.1.0
         */
        public Builder hostClassLoader(ClassLoader classLoader) {
            Objects.requireNonNull(classLoader, "ClassLoader must be non null.");
            this.hostClassLoader = classLoader;
            return this;
        }

        /**
         * Creates a new context instance from the configuration provided in the builder. The same
         * context builder can be used to create multiple context instances.
         *
         * @since 19.0
         */
        public Context build() {
            boolean nativeAccess = orAllAccess(allowNativeAccess);
            boolean createThread = orAllAccess(allowCreateThread);
            boolean io = orAllAccess(allowIO);
            boolean hostClassLoading = orAllAccess(allowHostClassLoading);
            boolean experimentalOptions = orAllAccess(allowExperimentalOptions);

            if (this.allowHostAccess != null && this.hostAccess != null) {
                throw new IllegalArgumentException("The method allowHostAccess with boolean and with HostAccess are mutually exclusive.");
            }

            Predicate<String> localHostLookupFilter = this.hostClassFilter;
            HostAccess hostAccess = this.hostAccess;

            if (this.allowHostAccess != null && this.allowHostAccess) {
                if (localHostLookupFilter == UNSET_HOST_LOOKUP) {
                    // legacy behavior support
                    localHostLookupFilter = ALL_HOST_CLASSES;
                }
                // legacy behavior support
                hostAccess = HostAccess.ALL;
            }
            if (hostAccess == null) {
                hostAccess = this.allowAllAccess ? HostAccess.ALL : HostAccess.EXPLICIT;
            }

            PolyglotAccess polyglotAccess = this.polyglotAccess;
            if (polyglotAccess == null) {
                polyglotAccess = this.allowAllAccess ? PolyglotAccess.ALL : PolyglotAccess.NONE;
            }

            if (localHostLookupFilter == UNSET_HOST_LOOKUP) {
                if (allowAllAccess) {
                    localHostLookupFilter = ALL_HOST_CLASSES;
                } else {
                    localHostLookupFilter = null;
                }
            }
            boolean hostClassLookupEnabled = localHostLookupFilter != null;
            if (localHostLookupFilter == null) {
                localHostLookupFilter = NO_HOST_CLASSES;
            }

            boolean createProcess = orAllAccess(allowCreateProcess);
            if (environmentAccess == null) {
                environmentAccess = this.allowAllAccess ? EnvironmentAccess.INHERIT : EnvironmentAccess.NONE;
            }
            Object limits;
            if (resourceLimits != null) {
                limits = resourceLimits.impl;
            } else {
                limits = null;
            }

            if (!io && customFileSystem != null) {
                throw new IllegalStateException("Cannot install custom FileSystem when IO is disabled.");
            }
            String localCurrentWorkingDirectory = currentWorkingDirectory == null ? null : currentWorkingDirectory.toString();
            Engine engine = this.sharedEngine;
            if (engine == null) {
                org.graalvm.polyglot.Engine.Builder engineBuilder = Engine.newBuilder().options(options == null ? Collections.emptyMap() : options);
                if (out != null) {
                    engineBuilder.out(out);
                }
                if (err != null) {
                    engineBuilder.err(err);
                }
                if (in != null) {
                    engineBuilder.in(in);
                }
                if (messageTransport != null) {
                    engineBuilder.serverTransport(messageTransport);
                }
                if (customLogHandler instanceof Handler) {
                    engineBuilder.logHandler((Handler) customLogHandler);
                } else if (customLogHandler instanceof OutputStream) {
                    engineBuilder.logHandler((OutputStream) customLogHandler);
                }
                engineBuilder.allowExperimentalOptions(experimentalOptions);
                engineBuilder.setBoundEngine(true);
                engine = engineBuilder.build();
                Context ctx = engine.impl.createContext(null, null, null, hostClassLookupEnabled, hostAccess, polyglotAccess, nativeAccess, createThread,
                                io, hostClassLoading, experimentalOptions,
                                localHostLookupFilter, Collections.emptyMap(), arguments == null ? Collections.emptyMap() : arguments,
                                onlyLanguages, customFileSystem, customLogHandler, createProcess, processHandler, environmentAccess, environment, zone, limits,
                                localCurrentWorkingDirectory, hostClassLoader);
                return ctx;
            } else {
                if (messageTransport != null) {
                    throw new IllegalStateException("Cannot use MessageTransport in a context that shares an Engine.");
                }
                return engine.impl.createContext(out, err, in, hostClassLookupEnabled, hostAccess, polyglotAccess, nativeAccess, createThread,
                                io, hostClassLoading, experimentalOptions,
                                localHostLookupFilter, options == null ? Collections.emptyMap() : options, arguments == null ? Collections.emptyMap() : arguments,
                                onlyLanguages, customFileSystem, customLogHandler, createProcess, processHandler, environmentAccess, environment, zone, limits,
                                localCurrentWorkingDirectory, hostClassLoader);
            }
        }

        private boolean orAllAccess(Boolean optionalBoolean) {
            return optionalBoolean != null ? optionalBoolean : allowAllAccess;
        }

    }
}
