/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;

import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractContextImpl;
import org.graalvm.polyglot.proxy.Proxy;
import org.graalvm.polyglot.io.FileSystem;

/**
 * A polyglot context for Graal guest languages that allows to {@link #eval(Source) evaluate} code.
 * A polyglot context represents the global runtime state of all {@link Engine#getLanguages()
 * installed} and {@link #newBuilder(String...) permitted} languages. Permitted languages are
 * {@link #initialize(String) initialized} lazily, when they are used for the first time. For many
 * operations of the context, a <i>language identifier</i> needs to be specified. A language
 * identifier is unique for each language.
 *
 * <h3>Evaluation</h3>
 *
 * A context allows to evaluate Guest language source code using {@link #eval(Source)}. This is
 * possible by evaluating {@link Source} objects or given a language identifier and code
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
 * <li>We first first create a new context with all installed languages as permitted languages.
 * <li>Next, we evaluate the expression "42" with language "js", which is the language identifier
 * for JavaScript. Since this is the first time we access JavaScript, it automatically gets
 * {@link #initialize(String) initialized} first.
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
 * {@link Engine#getOptions() compiler}. For {@link Language#getOptions() language options} the
 * option key consists of the {@link Language#getId() language id} plus a dot followed by the option
 * name (e.g. "js.Strict"). For most languages the option names start with an upper-case letter by
 * convention. A list of available options may be received using {@link Language#getOptions()}.
 * {@link Instrument#getOptions() Instrument options} are structured in the same way as language
 * options but start with the {@link Instrument#getId() instrument id} instead.
 * <p>
 * If system properties are {@link Engine.Builder#useSystemProperties(boolean) enabled}, which they
 * are by default, then all polyglot options maybe specified with the prefix "polyglot." (e.g.
 * "-Dpolyglot.js.Strict=true"). The system properties are read only once when the context or engine
 * instance is created. After that changes to the system properties have no affect.
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
 * <li>We first first create a new context with all installed languages as permitted languages.
 * <li>Next, we evaluate the expression "42" with language "js", which is the language identifier
 * for JavaScript. Since this is the first time we access JavaScript, it first gets
 * {@link #initialize(String) initialized} as well.
 * <li>Then, we assert the result value by converting the result value as primitive <code>int</code>
 * .
 * <li>Finally, if the context is no longer needed, it is necessary to close it to ensure that all
 * resources are freed. Contexts are also {@link AutoCloseable} for use with the Java
 * {@code try-with-resources} statement.
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
 * It is often necessary to interact with values of the host runtime within Graal guest languages.
 * Such objects are referred to as <i>host objects</i>. Every Java value that is passed to a Graal
 * language is interpreted according to the specification described in {@link #asValue(Object)}.
 * Also see {@link Value#as(Class)} for further details.
 *
 * <p>
 * <b>Example</b> using a Java object from JavaScript:
 *
 * <pre>
 * public class JavaRecord {
 *     public int x;
 *
 *     public String name() {
 *         return "foo";
 *     }
 * }
 * Context context = Context.create();
 *
 * JavaRecord record = new JavaRecord();
 * context.getBindings("js").putMember("javaRecord", record);
 *
 * context.eval("js", "record.x = 42");
 * assert record.x == 42;
 *
 * context.eval("js", "record.name()").asString().equals("foo");
 * </pre>
 *
 * <h3>Error Handling</h3>
 *
 * Guest languages code may fail when executing guest language code or when accessing guest language
 * object. So almost all methods in the {@link Context} and {@link Value} API throw a
 * {@link PolyglotException} in case an error occurs. See {@link PolyglotException} for further
 * details on error handling.
 *
 * <h3>Isolation</h3>
 *
 * Each context is by default isolated from all other instances with respect to both language
 * evaluation semantics and resource consumption. By default a new context instance has no access to
 * host resources, like threads, files or loading new host classes. To allow access to such
 * resources either the individual access right must be granted or
 * {@link Builder#allowAllAccess(boolean) all access} must be set to <code>true</code>.
 *
 * <p>
 * Contexts can be {@linkplain Builder#engine(Engine) configured} to share certain system resources
 * like ASTs, optimized code by specifying a single underlying engine; see {@link Engine} for more
 * details about code sharing.
 *
 * <h3>Proxies</h3>
 *
 * The {@link Proxy proxy interfaces} allow to mimic guest language objects, arrays, executables,
 * primitives and native objects in Graal languages. Every Graal language will treat instances of
 * proxies like an object of that particular language. Multiple proxy interfaces can be implemented
 * at the same time. For example, it is useful to provide proxy values that are objects with members
 * and arrays at the same time.
 *
 * <h3>Thread-Safety</h3>
 *
 * It is safe to use a context instance from a single thread. It is also safe to use it with
 * multiple threads if they do not access the context at the same time. Whether a single context
 * instance may be used from multiple threads at the same time depends on if all initialized
 * languages support it. If only languages are initialized that support multi-threading then the
 * context instance may be used from multiple threads at the same time. If a context is used from
 * multiple threads and the language does not fit then an {@link IllegalStateException} is thrown by
 * the accessing method.
 * <p>
 * Meta-data from the context's underlying {@link #getEngine() engine} can be retrieved safely by
 * any thread at any time.
 * <p>
 * A context may be {@linkplain #close() closed} from any thread, but only if the context is not
 * currently executing code. If a context is currently executing code, a different thread may kill
 * the currently runnign execution and close the context using {@link #close(boolean)}.
 *
 * <h3>Pre-Initialization</h3>
 *
 * The Context pre-initialization can be used to perform expensive builtin creation in the time of
 * native compilation.
 * <p>
 * The context pre-initialization is enabled by setting the system property
 * {@code polyglot.engine.PreinitializeContexts} to a comma separated list of language ids which
 * should be pre-initialized, for example: {@code -Dpolyglot.engine.PreinitializeContexts=js,python}
 * <p>
 * See
 * {@code com.oracle.truffle.api.TruffleLanguage.patchContext(java.lang.Object, com.oracle.truffle.api.TruffleLanguage.Env)}
 * for details about pre-initialization for language implementers.
 *
 * @since 1.0
 */
public final class Context implements AutoCloseable {

    final AbstractContextImpl impl;

    Context(AbstractContextImpl impl) {
        this.impl = impl;
    }

    /**
     * Provides access to meta-data about the underlying Graal {@linkplain Engine engine}.
     *
     * @return the Graal {@link Engine} being used by this context
     * @since 1.0
     */
    public Engine getEngine() {
        return impl.getEngineImpl(this);
    }

    /**
     * Evaluates a source by using the {@linkplain Source#getLanguage() language} specified in the
     * source. The result is accessible as {@link Value value} and never <code>null</code>. The
     * first time a source is evaluated it will be parsed, consecutive invocations of eval with the
     * same source will only execute the already parsed code.
     * <p>
     * <b>Basic Example:</b>
     *
     * <pre>
     * Context context = Context.create();
     * Source source = Source.newBuilder("js", "42").name("mysource.js").build();
     * Value result = context.eval(source);
     * assert result.asInt() == 42;
     * context.close();
     * </pre>
     *
     * @param source a source object to evaluate
     * @throws PolyglotException in case parsing or evaluation of the guest language code failed.
     * @throws IllegalStateException if the context is already closed, the current thread is not
     *             allowed to access this context
     * @throws IllegalArgumentException if the language of the given source is not installed or the
     *             {@link Source#getMimeType() MIME type} is not supported with the language.
     * @return result of the evaluation. The returned instance is is never <code>null</code>, but
     *         the result might represent a {@link Value#isNull() null} value.
     * @since 1.0
     */
    public Value eval(Source source) {
        return impl.eval(source.getLanguage(), source.impl);
    }

    /**
     * Evaluates a guest language code literal, using a provided {@link Language#getId() language
     * id}. The result is accessible as {@link Value value} and never <code>null</code>. The
     * provided {@link CharSequence} must represent an immutable String.
     * <p>
     * <b>Basic Example:</b>
     *
     * <pre>
     * Context context = Context.create();
     * Value result = context.eval("js", "42");
     * assert result.asInt() == 42;
     * context.close();
     * </pre>
     *
     * @throws PolyglotException in case parsing or evaluation of the guest language code failed.
     * @throws IllegalArgumentException if the language does not exist or is not accessible.
     * @throws IllegalStateException if the context is already closed, the current thread is not
     *             allowed to access this context or if the given language is not installed.
     * @return result of the evaluation. The returned instance is is never <code>null</code>, but
     *         the result might represent a {@link Value#isNull() null} value.
     * @since 1.0
     */
    public Value eval(String languageId, CharSequence source) {
        return eval(Source.create(languageId, source));
    }

    /**
     * Returns polyglot bindings that may be used to exchange symbols between the host and guest
     * languages. All languages have unrestricted access to the polyglot bindings. The returned
     * bindings object always has {@link Value#hasMembers() members} and its members are
     * {@link Value#getMember(String) readable}, {@link Value#putMember(String, Object) writable}
     * and {@link Value#removeMember(String) removable}.
     * <p>
     * Guest languages may put and get members through language specific APIs. For example, in
     * JavaScript symbols of the polyglot bindings can be accessed using
     * <code>Polyglot.import("name")</code> and set using
     * <code>Polyglot.export("name", value)</code>. Please see the individual language reference on
     * how to access these symbols.
     *
     * @throws IllegalStateException if context is already closed.
     * @since 1.0
     */
    public Value getPolyglotBindings() {
        return impl.getPolyglotBindings();
    }

    /**
     * Returns a value that represents the top-most bindings of a language. The top most bindings of
     * the language returns a {@link Value#getMember(String) member} for symbol in the scope.
     * Languages may allow modifications of members of the returned bindings object at the
     * language's discretion. If the language was not yet {@link #initialize(String) initialized} it
     * will be initialized when the bindings are requested.
     *
     * @throws IllegalArgumentException if the language does not exist or is not accessible.
     * @throws IllegalStateException if the context is already closed.
     * @throws PolyglotException in case the lazy initialization failed due to a guest language
     *             error.
     * @since 1.0
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
     * @since 1.0
     */
    public boolean initialize(String languageId) {
        return impl.initializeLanguage(languageId);
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
     * <li>If the <code>hostValue</code> is <code>null</code> then it will be interpreted as
     * polyglot {@link Value#isNull() null}.
     * <li>If the <code>hostValue</code> is already a {@link Value polyglot value} then it will be
     * cast to {@link Value}.
     * <li>If the <code>hostValue</code> is an instance of {@link Byte}, {@link Short},
     * {@link Integer}, {@link Long}, {@link Float} or {@link Double} then it will be interpreted as
     * polyglot {@link Value#isNumber() number}. Other subclasses of {@link Number} will be
     * interpreted as {@link Value#isHostObject() host object} (see later).
     * <li>If the <code>hostValue</code> is an instance of {@link Character} or {@link String} then
     * it will be interpreted as polyglot {@link Value#isString() string}.
     * <li>If the <code>hostValue</code> is an instance of {@link Boolean} then it will be
     * interpreted as polyglot {@link Value#isBoolean() boolean}.
     * <li>If the <code>hostValue</code> is a {@link Proxy polyglot proxy} then it will be
     * interpreted according to the behavior specified by the proxy. See the javadoc of the proxy
     * subclass for further details.
     * <li>If the <code>hostValue</code> is a non-primitive {@link Value#as(Class) mapped Java
     * value} then the original value will be restored. For example if a guest language object was
     * mapped to {@link Map} then the original object identity will be preserved when converting
     * back to a polyglot value.
     * <li>Any other <code>hostValue</code> will be interpreted as {@link Value#isHostObject() host
     * object}. Host objects expose all their public java fields and methods as
     * {@link Value#getMember(String) members}. In addition, Java arrays and subtypes of
     * {@link List} will be interpreted as value with {@link Value#hasArrayElements() array
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
     * When an argument value needs to be mapped to match a required Java method parameter type then
     * the semantics of {@link Value#as(Class) host value mapping} is used. The result of the
     * mapping is equivalent of calling {@link Value#as(Class)} with the parameter type. Therefore a
     * {@link ClassCastException} or {@link NullPointerException} is thrown if a parameter value
     * cannot be cast to the required parameter type.
     * <p>
     * Overloaded java methods are selected based on the arguments that are provided. In case
     * multiple mapped Java methods with the same name are applicable for
     * {@link Value#execute(Object...) executions} or {@link Value#newInstance(Object...)
     * instantiations} then the method with the most concrete method with applicable arguments will
     * be used used.
     * <p>
     * The following parameter type hierarchy is used for method resolution. Left-most parameter
     * types are prioritized over types to their right.
     * <ul>
     * <li>{@link Value#isBoolean() Boolean} values: boolean, Boolean, Object
     * <li>String values: char, Character, String, CharSequence, Object
     * <li>Number values: byte, Byte, short, Short, int, Integer, long, Long, float, Float, double,
     * Double, Number, Object
     * <li>Other values are resolved based on their Java type hierarchy.
     * </ul>
     * If there are multiple most concrete methods or too many arguments were provided then an
     * illegal argument type error will be raised.
     * <p>
     * <b>Advanced Example:</b>
     *
     * This example first creates a new instance of the Java class <code>Record</code> and inspects
     * it first using the polyglot value API and later using the JavaScript guest language.
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
     * @since 1.0
     */
    public Value asValue(Object hostValue) {
        return impl.asValue(hostValue);
    }

    /**
     * Explicitly enters this context on the current thread. A context needs to be entered for any
     * operation to be performed. The context implicitly enters and leaves the context for every
     * operation. For example, before and after invoking the {@link Value#execute(Object...)
     * execute} method. This can be inefficient if a very high number of simple operations needs to
     * be performed. By {@link #enter() entering} and {@link #leave() leaving} once explicitly, the
     * overhead for entering/leaving contexts for each operation can be eliminated. Contexts can be
     * entered multiple times on the same thread.
     *
     * @throws IllegalStateException if the context is already {@link #close() closed}.
     * @throws PolyglotException if a language has denied execution on the current thread.
     * @see #leave() to leave a context.
     * @since 1.0
     */
    public void enter() {
        impl.explicitEnter(this);
    }

    /**
     * Explicitly leaves this context on the current thread. The context must be {@link #enter()
     * entered} before calling this method.
     *
     * @throws IllegalStateException if the context is already closed or if the context was not
     *             {@link #enter() entered} on the current thread.
     * @see #enter() to enter a context.
     * @since 1.0
     */
    public void leave() {
        impl.explicitLeave(this);
    }

    /**
     * Closes this context and frees up potentially allocated native resources. A context cannot
     * free all native resources allocated automatically. For this reason it is necessary to close
     * contexts after use. If a context is cancelled then the currently executing thread will throw
     * a {@link PolyglotException}. The exception indicates that it was
     * {@link PolyglotException#isCancelled() cancelled}. Please note that canceling a single
     * context can negatively affect the performance of other executing contexts constructed with
     * the same engine.
     * <p>
     * If internal errors occur during closing of the language then they are printed to the
     * configured {@link Builder#err(OutputStream) error output stream}. If a context was closed
     * then all its methods will throw an {@link IllegalStateException} when invoked. If an attempt
     * to close a context was successful then consecutive calls to close have no effect.
     *
     * @param cancelIfExecuting if <code>true</code> then currently executing contexts will be
     *            {@link PolyglotException#isCancelled() cancelled}, else an
     *            {@link IllegalStateException} is thrown.
     * @see Engine#close() To close an engine.
     * @throws PolyglotException in case the close failed due to a guest language error.
     * @throws IllegalStateException if the context is still running and cancelIfExecuting is
     *             <code>false</code>
     * @since 1.0
     */
    public void close(boolean cancelIfExecuting) {
        impl.close(this, cancelIfExecuting);
    }

    /**
     * Closes this context and frees up potentially allocated native resources. Languages might not
     * be able to free all native resources allocated by a context automatically. For this reason it
     * is recommended to close contexts after use. If the context is currently being executed on
     * another thread then an {@link IllegalStateException} is thrown. To close concurrently
     * executing contexts see {@link #close(boolean)}.
     * <p>
     * If internal errors occur during closing of the language then they are printed to the
     * configured {@link Builder#err(OutputStream) error output stream}. If a context was closed
     * then all its methods will throw an {@link IllegalStateException} when invoked. If an attempt
     * to close a context was successful then consecutive calls to close have no effect.
     *
     * @throws PolyglotException in case the close failed due to a guest language error.
     * @throws IllegalStateException if the context is currently executing on another thread.
     * @see Engine#close() To close an engine.
     * @since 1.0
     */
    public void close() {
        close(false);
    }

    /**
     * Returns the currently entered polyglot context. A context is entered if the currently
     * executing Java method was called by a Graal guest language or if a context was entered
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
     * The returned context may <b>not</b> be used to {@link #enter() enter} , {@link #leave()
     * leave} or {@link #close() close} the context or {@link #getEngine() engine}. Invoking such
     * methods will cause an {@link IllegalStateException} to be thrown. This ensures that only the
     * {@link #create(String...) creator} of a context is allowed to enter, leave or close a
     * context.
     * <p>
     * The currently entered context may change. It is therefore required to call
     * {@link #getCurrent() getCurrent} every time a context is needed. The currently entered
     * context should not be cached in static fields.
     *
     * @throws IllegalStateException if no context is currently entered.
     * @since 1.0
     */
    public static Context getCurrent() {
        return Engine.getImpl().getCurrentContext();
    }

    /**
     * Creates a context with default configuration.
     *
     * @param permittedLanguages names of languages permitted in this context, if no languages are
     *            provided then all the use of languages will be permitted.
     * @return a new context
     * @since 1.0
     */
    public static Context create(String... permittedLanguages) {
        return newBuilder(permittedLanguages).build();
    }

    /**
     * Creates a builder for constructing a context with custom configuration.
     *
     * @param permittedLanguages names of languages permitted in this context, if no languages are
     *            provided then the use of all languages will be permitted.
     * @return a builder that can create a context
     * @since 1.0
     */
    public static Builder newBuilder(String... permittedLanguages) {
        return EMPTY.new Builder(permittedLanguages);
    }

    private static final Context EMPTY = new Context(null);

    /**
     * Builder class to construct {@link Context} instances. A builder instance is not thread-safe
     * and must not be used from multiple threads at the same time.
     *
     * @see Context
     * @since 1.0
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
        private Predicate<String> hostClassFilter;
        private Boolean allowHostAccess;
        private Boolean allowNativeAccess;
        private Boolean allowCreateThread;
        private boolean allowAllAccess;
        private Boolean allowIO;
        private Boolean allowHostClassLoading;
        private FileSystem customFileSystem;
        private Handler customLogHandler;

        Builder(String... onlyLanguages) {
            Objects.requireNonNull(onlyLanguages);
            for (String onlyLanguage : onlyLanguages) {
                Objects.requireNonNull(onlyLanguage);
            }
            this.onlyLanguages = onlyLanguages;
        }

        /**
         * Explicitly sets the underlying engine to use. By default every context has its own
         * isolated engine. If multiple contexts are created from one engine, then they may
         * share/cache certain system resources like ASTs, optimized code by specifying a single
         * underlying engine; see {@link Engine} for more details about system resource sharing.
         *
         * @since 1.0
         */
        public Builder engine(Engine engine) {
            Objects.requireNonNull(engine);
            this.sharedEngine = engine;
            return this;
        }

        /**
         * Sets the standard output stream to be used for this context. If not set then the standard
         * output stream configured for the {@link #engine(Engine) engine} is used or standard error
         * stream.
         *
         * @since 1.0
         */
        public Builder out(OutputStream out) {
            Objects.requireNonNull(out);
            this.out = out;
            return this;
        }

        /**
         * Sets the error output stream to be used for this context. If not set then either the
         * error stream configured for the {@link #engine(Engine) engine} is used or standard error
         * stream.
         *
         * @since 1.0
         */
        public Builder err(OutputStream err) {
            Objects.requireNonNull(err);
            this.err = err;
            return this;
        }

        /**
         * Sets the input stream to be used for this context. If not set then either the input
         * stream configured for the {@link #engine(Engine) engine} is used or standard in stream.
         *
         * @since 1.0
         */
        public Builder in(InputStream in) {
            Objects.requireNonNull(in);
            this.in = in;
            return this;
        }

        /**
         * Allows guest languages to access the host language by loading new classes. Default is
         * <code>false</code>. If {@link #allowAllAccess(boolean) all access} is set to
         * <code>true</code> then then host access is enabled if not allowed explicitly.
         *
         * @since 1.0
         */
        public Builder allowHostAccess(boolean enabled) {
            this.allowHostAccess = enabled;
            return this;
        }

        /**
         * Allows guest languages to access the native interface.
         *
         * @since 1.0
         */
        public Builder allowNativeAccess(boolean enabled) {
            this.allowNativeAccess = enabled;
            return this;
        }

        /**
         * If <code>true</code> allows guest languages to create new threads. Default is
         * <code>false</code>. If {@link #allowAllAccess(boolean) all access} is set to
         * <code>true</code> then the creation of threads is enabled if not allowed explicitly.
         * Threads created by guest languages are closed when the context is {@link Context#close()
         * closed}.
         *
         * @since 1.0
         */
        public Builder allowCreateThread(boolean enabled) {
            this.allowCreateThread = enabled;
            return this;
        }

        /**
         * If <code>true</code> grants the context the same access privileges as the host virtual
         * machine. If not explicitly specified then all access is <code>false</code>. If the host
         * VM runs without a {@link SecurityManager security manager} enabled, then enabling all
         * access gives the guest languages full control over the host process. Otherwise, the Java
         * {@link SecurityManager security manager} is in control of restricting the privileges of
         * the polyglot context. If new privilege restrictions are added to the polyglot API then
         * they will default to full access if all access is set to <code>true</code>. If all access
         * is enabled then certain privileges may still be disabled by configuring it explicitly
         * using this builder.
         * <p>
         * Grants full access to the following privileges by default:
         * <ul>
         * <li>The {@link #allowCreateThread(boolean) creation} and use of new threads.
         * <li>The access to public {@link #allowHostAccess(boolean) host classes}.
         * <li>The loading of new {@link #allowHostClassLoading(boolean) host classes} by adding
         * entries to the class path.
         * <li>Exporting new members into the polyglot {@link Context#getPolyglotBindings()
         * bindings}.
         * <li>Unrestricted {@link #allowIO(boolean) IO operations} on host system.
         * </ul>
         *
         * @param enabled <code>true</code> for all access by default.
         * @since 1.0
         */
        public Builder allowAllAccess(boolean enabled) {
            this.allowAllAccess = enabled;
            return this;
        }

        /**
         * If host class loading is enabled then the guest language is allowed to load new host
         * classes via jar or class files. If {@link #allowAllAccess(boolean) all access} is set to
         * <code>true</code> then the host class loading is enabled if it is not disallowed
         * explicitly. For host class loading to be useful {@link #allowIO(boolean) IO} operations
         * and {@link #allowHostAccess(boolean) host access} need to be allowed as well.
         *
         * @since 1.0
         */
        public Builder allowHostClassLoading(boolean enabled) {
            this.allowHostClassLoading = enabled;
            return this;
        }

        /**
         * Sets a class filter that allows to limit the classes that are allowed to be loaded by
         * guest languages. If the filter returns <code>true</code> then the class is accessible,
         * else it is not accessible and throws an guest language error when accessed. In order to
         * have an effect {@link #allowHostAccess(boolean)} or {@link #allowAllAccess(boolean)}
         * needs to be set to <code>true</code>.
         *
         * @param classFilter a predicate that returns <code>true</code> or <code>false</code> for a
         *            java qualified class name.
         * @since 1.0
         */
        public Builder hostClassFilter(Predicate<String> classFilter) {
            Objects.requireNonNull(classFilter);
            this.hostClassFilter = classFilter;
            return this;
        }

        /**
         * Set an option for this {@link Context context}. By default any options for the
         * {@link Engine#getOptions() engine}, {@link Language#getOptions() language} or
         * {@link Instrument#getOptions() instrument} can be set for a context. If an
         * {@link #engine(Engine) explicit engine} is set for this context then only language
         * options can be set. Instrument and engine options can be set exclusively on the explicit
         * engine instance. If a language option was set for the context and the engine then the
         * option of the context is going to take precedence.
         * <p>
         * If one of the set option keys or values is invalid then an
         * {@link IllegalArgumentException} is thrown when the context is {@link #build() built}.
         * The given key and value must not be <code>null</code>.
         *
         * @see Engine.Builder#option(String, String) To specify an option for the engine.
         * @since 1.0
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
         * @since 1.0
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
         * @since 1.0
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
         * If <code>true</code> allows guest language to perform unrestricted IO operations on host
         * system. Default is <code>false</code>. If {@link #allowAllAccess(boolean) all access} is
         * set to <code>true</code> then IO is enabled if not allowed explicitly.
         *
         * @param enabled {@code true} to enable Input/Output
         * @return the {@link Builder}
         * @since 1.0
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
         * @since 1.0
         */
        public Builder fileSystem(final FileSystem fileSystem) {
            Objects.requireNonNull(fileSystem, "FileSystem must be non null.");
            this.customFileSystem = fileSystem;
            return this;
        }

        /**
         * Installs a new logging {@link Handler}. The logger's {@link Level} configuration is done
         * using the {@link #options(java.util.Map) Context's options}. The level option key has the
         * following format: {@code log.languageId.loggerName.level} or
         * {@code log.instrumentId.loggerName.level}. The value is either the name of pre-defined
         * {@link Level} constant or a numeric {@link Level} value. If not explicitly set in options
         * the level is inherited from the parent logger.
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
         * @param logHandler the {@link Handler} to use for logging in built {@link Context}.
         * @return the {@link Builder}
         * @since 1.0
         */
        public Builder logHandler(final Handler logHandler) {
            Objects.requireNonNull(logHandler, "Hanlder must be non null.");
            this.customLogHandler = logHandler;
            return this;
        }

        /**
         * Creates a new context instance from the configuration provided in the builder. The same
         * context builder can be used to create multiple context instances.
         *
         * @since 1.0
         */
        public Context build() {
            if (allowHostAccess == null) {
                allowHostAccess = allowAllAccess;
            }
            if (allowNativeAccess == null) {
                allowNativeAccess = allowAllAccess;
            }
            if (allowCreateThread == null) {
                allowCreateThread = allowAllAccess;
            }
            if (allowIO == null) {
                allowIO = allowAllAccess;
            }
            if (allowHostClassLoading == null) {
                allowHostClassLoading = allowAllAccess;
            }
            if (!allowIO && customFileSystem != null) {
                throw new IllegalStateException("Cannot install custom FileSystem when IO is disabled.");
            }
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
                if (customLogHandler != null) {
                    engineBuilder.logHandler(customLogHandler);
                }
                engineBuilder.setBoundEngine(true);
                engine = engineBuilder.build();
                return engine.impl.createContext(null, null, null, allowHostAccess, allowNativeAccess, allowCreateThread, allowIO,
                                allowHostClassLoading,
                                hostClassFilter, Collections.emptyMap(), arguments == null ? Collections.emptyMap() : arguments, onlyLanguages, customFileSystem, customLogHandler);
            } else {
                return engine.impl.createContext(out, err, in, allowHostAccess, allowNativeAccess, allowCreateThread, allowIO,
                                allowHostClassLoading,
                                hostClassFilter, options == null ? Collections.emptyMap() : options, arguments == null ? Collections.emptyMap() : arguments, onlyLanguages, customFileSystem,
                                customLogHandler);
            }
        }

    }

}
