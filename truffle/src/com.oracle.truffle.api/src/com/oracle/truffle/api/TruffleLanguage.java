/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.impl.ReadOnlyArrayList;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * A Truffle language implementation for executing guest language code in a
 * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine PolyglotEngine}. Subclasses of
 * {@link TruffleLanguage} must provide a public default constructor.
 *
 * <h4>Lifecycle</h4>
 *
 * A language implementation becomes available for use by an engine when metadata is added using the
 * {@link Registration} annotation and the implementation's JAR file placed on the host Java Virtual
 * Machine's class path.
 * <p>
 * A newly created engine locates all available language implementations and creates a
 * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine.Language descriptor} for each. The
 * descriptor holds the language's registered metadata, but its execution environment is not
 * initialized until the language is needed for code execution. That execution environment remains
 * initialized for the lifetime of the engine and is isolated from the environment in any other
 * engine instance.
 * <p>
 * A new {@link TruffleLanguage language implementation} instance is instantiated for each runtime
 * that is created using the {@linkplain com.oracle.truffle.api.vm.PolyglotRuntime.Builder#build()
 * runtime builder}. If an engine is created without a runtime then the language implementation
 * instance is created for each engine.
 * <p>
 * Global state can be shared between multiple context instances by saving them as in a field of the
 * {@link TruffleLanguage} subclass. The implementation needs to ensure data isolation between the
 * contexts. However ASTs or assumptions can be shared across multiple contexts if modifying them
 * does not affect language semantics. Languages are strongly discouraged from using static mutable
 * state in their languages. Instead language implementation instances should be used instead.
 * <p>
 * The methods in {@link TruffleLanguage} might be invoked from multiple threads. However, one
 * context instance is guaranteed to be invoked only from one single thread. Therefore context
 * objects don't need to be thread-safe. Code that is shared using the {@link TruffleLanguage}
 * instance needs to be prepared to be accessed from multiple threads.
 * <p>
 * Whenever an engine is disposed then each initialized context will be disposed
 * {@link #disposeContext(Object) disposed}.
 *
 * <h4>Cardinalities</h4>
 *
 * <i>One</i> host virtual machine depends on other system instances using the following
 * cardinalities:
 *
 * <pre>
 * K = number of installed languages
 * I = number of installed instruments
 * N = unbounded
 *
 * - 1:Host VM Processs
 *   - N:PolyglotRuntime
 *     - K:TruffleLanguage
 *     - I:PolyglotRuntime.Instrument
 *       - 1:TruffleInstrument
 *   - N:PolyglotEngine
 *     - 1:Thread
 *     - K:PolyglotEngine.Language
 *       - 1:Language Context
 * </pre>
 *
 * <h4>Language Configuration</h4>
 *
 * Each engine instance can, during its creation, register
 * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine.Builder#config(String, String, Object)
 * language-specific configuration data} (for example originating from command line arguments) in
 * the form of {@code MIME-type/key/object} triples. A Language implementation retrieves the data
 * via {@link Env#getConfig()} for configuring {@link #createContext(Env) initial execution state}.
 *
 * <h4>Global Symbols</h4>
 *
 * Language implementations communicate with one another (and with instrumentation-based tools such
 * as debuggers) by exporting/importing named values known as <em>global symbols</em>. These
 * typically implement guest language export/import statements used for <em>language
 * interoperation</em>.
 * <p>
 * A language manages its namespace of exported global symbols dynamically, by its response to the
 * query {@link #findExportedSymbol(Object, String, boolean)}. No attempt is made to avoid
 * cross-language name conflicts.
 * <p>
 * A language implementation can also {@linkplain Env#importSymbol(String) import} a global symbol
 * by name, according to the following rules:
 * <ul>
 * <li>A global symbol
 * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine.Builder#globalSymbol(String, Object)
 * registered by the engine} will be returned, if any, ignoring global symbols exported by other
 * languages.</li>
 * <li>Otherwise all languages are queried in unspecified order, and the first global symbol found,
 * if any, is returned.</li>
 * </ul>
 *
 * <h4>Configuration vs. Initialization</h4>
 *
 * To ensure that a Truffle language can be used in a language-agnostic way, the implementation
 * should be designed to decouple its configuration and initialization from language specifics as
 * much as possible. One aspect of this is the initialization and start of execution via the
 * {@link com.oracle.truffle.api.vm.PolyglotEngine}, which should be designed in a generic way.
 * Language-specific entry points, for instance to emulate the command-line interface of an existing
 * implementation, should be handled externally.
 *
 * @param <C> internal state of the language associated with every thread that is executing program
 *            {@link #parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest) parsed} by the
 *            language
 * @since 0.8 or earlier
 */
@SuppressWarnings({"javadoc"})
public abstract class TruffleLanguage<C> {

    // get and isFinal are frequent operations -> cache the engine access call
    @CompilationFinal private LanguageInfo languageInfo;
    @CompilationFinal private ContextReference<C> reference;
    @CompilationFinal private boolean singletonLanguage;

    /**
     * Constructor to be called by subclasses.
     *
     * @since 0.8 or earlier
     */
    protected TruffleLanguage() {
    }

    /**
     * The annotation to use to register your language to the
     * {@link com.oracle.truffle.api.vm.PolyglotEngine Truffle} system. By annotating your
     * implementation of {@link TruffleLanguage} by this annotation you are just a <em>one JAR drop
     * to the class path</em> away from your users. Once they include your JAR in their application,
     * your language will be available to the {@link com.oracle.truffle.api.vm.PolyglotEngine
     * Truffle virtual machine}.
     *
     * @since 0.8 or earlier
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    public @interface Registration {

        /**
         * Unique id of your language. This id will be exposed to users via the getter. It is used
         * as group identifier for options of the language.
         *
         * @return identifier of your language
         */
        String id() default "";

        /**
         * Unique name of your language. This name will be exposed to users via the
         * {@link com.oracle.truffle.api.vm.PolyglotEngine.Language#getName()} getter.
         *
         * @return identifier of your language
         */
        String name();

        /**
         * Unique name of your language implementation.
         *
         * @return the implementation name of your language
         */
        String implementationName() default "";

        /**
         * Unique string identifying the language version. This name will be exposed to users via
         * the {@link com.oracle.truffle.api.vm.PolyglotEngine.Language#getVersion()} getter.
         *
         * @return version of your language
         */
        String version();

        /**
         * List of MIME types associated with your language. Users will use them (directly or
         * indirectly) when
         * {@link com.oracle.truffle.api.vm.PolyglotEngine#eval(com.oracle.truffle.api.source.Source)
         * executing} their code snippets or their {@link Source files}.
         *
         * @return array of MIME types assigned to your language files
         */
        String[] mimeType();

        /**
         * Specifies if the language is suitable for interactive evaluation of {@link Source
         * sources}. {@link #interactive() Interactive} languages should be displayed in interactive
         * environments and presented to the user. The default value of this attribute is
         * <code>true</code> assuming majority of the languages is interactive. Change the value to
         * <code>false</code> to opt-out and turn your language into non-interactive one.
         *
         * @return <code>true</code> if the language should be presented to end-user in an
         *         interactive environment
         * @since 0.22
         */
        boolean interactive() default true;

        /**
         * Returns <code>true</code> if this language is intended to be used internally only.
         * Internal languages cannot be used directly, only by using it from another non-internal
         * language.
         *
         * @since 0.27
         */
        boolean internal() default false;
    }

    /**
     * Creates internal representation of the executing context suitable for given environment. Each
     * time the {@link TruffleLanguage language} is used by a new
     * {@link com.oracle.truffle.api.vm.PolyglotEngine} or in a new thread, the system calls this
     * method to let the {@link TruffleLanguage language} prepare for <em>execution</em>. The
     * returned execution context is completely language specific; it is however expected it will
     * contain reference to here-in provided <code>env</code> and adjust itself according to
     * parameters provided by the <code>env</code> object.
     * <p>
     * The context created by this method is accessible using {@link #getContextReference()}. An
     * {@link IllegalStateException} is thrown if the context is tried to be accessed while the
     * createContext method is executed.
     * <p>
     * This method shouldn't perform any complex operations. The runtime system is just being
     * initialized and for example making
     * {@link Env#parse(com.oracle.truffle.api.source.Source, java.lang.String...) calls into other
     * languages} and assuming your language is already initialized and others can see it would be
     * wrong - until you return from this method, the initialization isn't over. Should there be a
     * need to perform complex initializaton, do it by overriding the
     * {@link #initializeContext(java.lang.Object)} method.
     *
     * @param env the environment the language is supposed to operate in
     * @return internal data of the language in given environment
     * @since 0.8 or earlier
     */
    protected abstract C createContext(Env env);

    /**
     * Perform any complex initialization. The
     * {@link #createContext(com.oracle.truffle.api.TruffleLanguage.Env) } factory method shouldn't
     * do any complex operations. Just create the instance of the context, let the runtime system
     * register it properly. Should there be a need to perform complex initializaton, override this
     * method and let the runtime call it <em>later</em> to finish any <em>post initialization</em>
     * actions. Example:
     *
     * {@link TruffleLanguageSnippets.PostInitLanguage#createContext}
     *
     * @param context the context created by
     *            {@link #createContext(com.oracle.truffle.api.TruffleLanguage.Env)}
     * @throws java.lang.Exception if something goes wrong
     * @since 0.17
     */
    protected void initializeContext(C context) throws Exception {
    }

    /**
     * Disposes the context created by
     * {@link #createContext(com.oracle.truffle.api.TruffleLanguage.Env)}. A language can be asked
     * by its user to <em>clean-up</em>. In such case the language is supposed to dispose any
     * resources acquired before and <em>dispose</em> the <code>context</code> - e.g. render it
     * useless for any future calls.
     *
     * @param context the context {@link #createContext(com.oracle.truffle.api.TruffleLanguage.Env)
     *            created by the language}
     * @since 0.8 or earlier
     */
    protected void disposeContext(C context) {
    }

    /**
     * Parses the provided source and generates appropriate AST. The parsing should execute no user
     * code, it should only create the {@link Node} tree to represent the source. If the provided
     * source does not correspond naturally to a call target, the returned call target should create
     * and if necessary initialize the corresponding language entity and return it. The parsing may
     * be performed in a context (specified as another {@link Node}) or without context. The
     * {@code argumentNames} may contain symbolic names for actual parameters of the call to the
     * returned value. The result should be a call target with method
     * {@link CallTarget#call(java.lang.Object...)} that accepts as many arguments as were provided
     * via the {@code argumentNames} array.
     *
     * @param code source code to parse
     * @param context a {@link Node} defining context for the parsing
     * @param argumentNames symbolic names for parameters of
     *            {@link CallTarget#call(java.lang.Object...)}
     * @return a call target to invoke which also keeps in memory the {@link Node} tree representing
     *         just parsed <code>code</code>
     * @throws Exception if parsing goes wrong. Here-in thrown exception is propagated to the user
     *             who called one of <code>eval</code> methods of
     *             {@link com.oracle.truffle.api.vm.PolyglotEngine}
     * @since 0.8 or earlier
     * @deprecated override {@link #parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest)}
     */
    @Deprecated
    protected CallTarget parse(Source code, Node context, String... argumentNames) throws Exception {
        throw new UnsupportedOperationException("Call parse with ParsingRequest parameter!");
    }

    /**
     * Parses the {@link ParsingRequest#getSource() provided source} and generates its appropriate
     * AST representation. The parsing should execute no user code, it should only create the
     * {@link Node} tree to represent the source. If the {@link ParsingRequest#getSource() provided
     * source} does not correspond naturally to a {@link CallTarget call target}, the returned call
     * target should create and if necessary initialize the corresponding language entity and return
     * it.
     *
     * The parsing may be performed in a context (specified by {@link ParsingRequest#getLocation()})
     * or without context. The {@code argumentNames} may contain symbolic names for actual
     * parameters of the call to the returned value. The result should be a call target with method
     * {@link CallTarget#call(java.lang.Object...)} that accepts as many arguments as were provided
     * via the {@link ParsingRequest#getArgumentNames()} method.
     *
     * @param request request for parsing
     * @return a call target to invoke which also keeps in memory the {@link Node} tree representing
     *         just parsed <code>code</code>
     * @throws Exception exception can be thrown parsing goes wrong. Here-in thrown exception is
     *             propagated to the user who called one of <code>eval</code> methods of
     *             {@link com.oracle.truffle.api.vm.PolyglotEngine}
     * @since 0.22
     */
    protected CallTarget parse(ParsingRequest request) throws Exception {
        throw new UnsupportedOperationException(
                        String.format("Override parse method of %s, it will be made abstract in future version of Truffle API!", getClass().getName()));
    }

    /**
     * @since 0.27
     * @deprecated in 0.27 implement {@link #getOptionDescriptors()} instead.
     */
    @Deprecated
    protected List<OptionDescriptor> describeOptions() {
        return null;
    }

    /**
     * Returns a set of option descriptors that are supported by this language. Option values are
     * accessible using the {@link Env#getOptions() environment} when the context is
     * {@link #createContext(Env) created}. To construct option descriptors from a list then
     * {@link OptionDescriptors#create(List)} can be used.
     *
     * @see Option For an example of declaring the option descriptor using an annotation.
     * @since 0.27
     */
    protected OptionDescriptors getOptionDescriptors() {
        return OptionDescriptors.create(describeOptions());
    }

    /**
     * Request for parsing. Contains information of what to parse and in which context.
     *
     * @since 0.22
     */
    public static final class ParsingRequest {
        private final Node node;
        private final MaterializedFrame frame;
        private final Source source;
        private final String[] argumentNames;
        private boolean disposed;

        ParsingRequest(Source source, Node node, MaterializedFrame frame, String... argumentNames) {
            Objects.nonNull(source);
            this.node = node;
            this.frame = frame;
            this.source = source;
            this.argumentNames = argumentNames;
        }

        /**
         * The source code to parse.
         *
         * @return the source code, never <code>null</code>
         * @since 0.22
         */
        public Source getSource() {
            if (disposed) {
                throw new IllegalStateException();
            }
            return source;
        }

        /**
         * Specifies the code location for parsing. The location is specified as an instance of a
         * {@link Node} in the AST. There doesn't have to be any specific location and in such case
         * this method returns <code>null</code>. If the node is provided, it can be for example
         * {@link com.oracle.truffle.api.instrumentation.EventContext#getInstrumentedNode()} when
         * {@link com.oracle.truffle.api.instrumentation.EventContext#parseInContext} is called.
         *
         *
         * @return a {@link Node} defining AST context for the parsing or <code>null</code>
         * @since 0.22
         */
        public Node getLocation() {
            if (disposed) {
                throw new IllegalStateException();
            }
            return node;
        }

        /**
         * Specifies the execution context for parsing. If the parsing request is used for
         * evaluation during halted execution, for example as in
         * {@link com.oracle.truffle.api.debug.DebugStackFrame#eval(String)} method, this method
         * provides access to current {@link MaterializedFrame frame} with local variables, etc.
         *
         * @return a {@link MaterializedFrame} exposing the current execution state or
         *         <code>null</code> if there is none
         * @since 0.22
         */
        public MaterializedFrame getFrame() {
            if (disposed) {
                throw new IllegalStateException();
            }
            return frame;
        }

        /**
         * Argument names. The result of
         * {@link #parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest) parsing} is an
         * instance of {@link CallTarget} that {@link CallTarget#call(java.lang.Object...) can be
         * invoked} without or with some parameters. If the invocation requires some arguments, and
         * the {@link #getSource()} references them, it is essential to name them. Example that uses
         * the argument names:
         *
         * {@link TruffleLanguageSnippets#parseWithParams}
         *
         * @return symbolic names for parameters of {@link CallTarget#call(java.lang.Object...)}
         * @since 0.22
         */
        public List<String> getArgumentNames() {
            if (disposed) {
                throw new IllegalStateException();
            }
            return argumentNames == null ? Collections.<String> emptyList() : ReadOnlyArrayList.asList(argumentNames, 0, argumentNames.length);
        }

        void dispose() {
            disposed = true;
        }

        CallTarget parse(TruffleLanguage<?> truffleLanguage) throws Exception {
            try {
                return truffleLanguage.parse(this);
            } catch (UnsupportedOperationException ex) {
                return truffleLanguage.parse(source, node, argumentNames);
            }
        }
    }

    /**
     * Called when some other language is seeking for a global symbol. This method is supposed to do
     * lazy binding, e.g. there is no need to export symbols in advance, it is fine to wait until
     * somebody asks for it (by calling this method).
     * <p>
     * The exported object can either be <code>TruffleObject</code> (e.g. a native object from the
     * other language) to support interoperability between languages, {@link String} or one of the
     * Java primitive wrappers ( {@link Integer}, {@link Double}, {@link Short}, {@link Boolean},
     * etc.).
     * <p>
     * The way a symbol becomes <em>exported</em> is language dependent. In general it is preferred
     * to make the export explicit - e.g. call some function or method to register an object under
     * specific name. Some languages may however decide to support implicit export of symbols (for
     * example from global scope, if they have one). However explicit exports should always be
     * preferred. Implicitly exported object of some name should only be used when there is no
     * explicit export under such <code>globalName</code>. To ensure so the infrastructure first
     * asks all known languages for <code>onlyExplicit</code> symbols and only when none is found,
     * it does one more round with <code>onlyExplicit</code> set to <code>false</code>.
     *
     * @param context context to locate the global symbol in
     * @param globalName the name of the global symbol to find
     * @param onlyExplicit should the language seek for implicitly exported object or only consider
     *            the explicitly exported ones?
     * @return an exported object or <code>null</code>, if the symbol does not represent anything
     *         meaningful in this language
     * @since 0.8 or earlier
     */
    protected Object findExportedSymbol(C context, String globalName, boolean onlyExplicit) {
        return null;
    }

    /**
     * Looks up symbol in the top-most scope of the language. Returns <code>null</code> if no symbol
     * was found.
     * <p>
     * The returned object can either be <code>TruffleObject</code> (e.g. a native object from the
     * other language) to support interoperability between languages, {@link String} or one of the
     * Java primitive wrappers ( {@link Integer}, {@link Double}, {@link Byte}, {@link Boolean},
     * etc.).
     * <p>
     *
     * @param context the current context of the language
     * @param symbolName the name of the symbol to look up.
     *
     * @since 0.27
     */
    protected Object lookupSymbol(C context, String symbolName) {
        return null;
    }

    /**
     * Returns global object for the language.
     * <p>
     * The object is expected to be <code>TruffleObject</code> (e.g. a native object from the other
     * language) but technically it can be one of the Java primitive wrappers ({@link Integer},
     * {@link Double}, {@link Short}, etc.).
     *
     * @param context context to find the language global in
     * @return the global object or <code>null</code> if the language does not support such concept
     * @since 0.8 or earlier
     */
    protected abstract Object getLanguageGlobal(C context);

    /**
     * Checks whether the object is provided by this language.
     *
     * @param object the object to check
     * @return <code>true</code> if this language can deal with such object in native way
     * @since 0.8 or earlier
     */
    protected abstract boolean isObjectOfLanguage(Object object);

    /**
     * Runs source code in a halted execution context, or at top level.
     *
     * @param source the code to run
     * @param node node where execution halted, {@code null} if no execution context
     * @param mFrame frame where execution halted, {@code null} if no execution context
     * @return result of running the code in the context, or at top level if no execution context.
     * @throws Exception if the evaluation cannot be performed
     * @since 0.8 or earlier
     * @deprecated override {@link #parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest)}
     *             and use {@link ParsingRequest#getFrame()} to obtain the current frame information
     */
    @Deprecated
    protected Object evalInContext(Source source, Node node, MaterializedFrame mFrame) throws IOException {
        ParsingRequest request = new ParsingRequest(source, node, mFrame);
        CallTarget target;
        try {
            target = parse(request);
        } catch (Exception ex) {
            if (ex instanceof IOException) {
                throw (IOException) ex;
            }
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new RuntimeException(ex);
        }
        return target.call();
    }

    /**
     * Generates language specific textual representation of a value. Each language may have special
     * formating conventions - even primitive values may not follow the traditional Java formating
     * rules. As such when {@link com.oracle.truffle.api.vm.PolyglotEngine.Value#as(java.lang.Class)
     * value.as(String.class)} is requested, it consults the language that produced the value by
     * calling this method. By default this method calls {@link Objects#toString(java.lang.Object)}.
     *
     * @param context the execution context for doing the conversion
     * @param value the value to convert. Either primitive type or
     *            {@link com.oracle.truffle.api.interop.TruffleObject}
     * @return textual representation of the value in this language
     * @since 0.8 or earlier
     */
    protected String toString(C context, Object value) {
        return Objects.toString(value);
    }

    /**
     * Decides whether the result of evaluating an interactive source should be printed to stdout.
     * By default this methods returns <code>true</code> claiming all values are visible.
     * <p>
     * This method affects behavior of {@link com.oracle.truffle.api.vm.PolyglotEngine#eval} - when
     * evaluating an {@link Source#isInteractive() interactive source} the result of the
     * {@link com.oracle.truffle.api.vm.PolyglotEngine#eval evaluation} is tested for
     * {@link #isVisible(java.lang.Object, java.lang.Object) visibility} and if the value is found
     * visible, it gets {@link TruffleLanguage#toString(java.lang.Object, java.lang.Object)
     * converted to string} and printed to
     * {@link com.oracle.truffle.api.vm.PolyglotEngine.Builder#setOut standard output}.
     * <p>
     * A language can control whether a value is or isn't printed by overriding this method and
     * returning <code>false</code> for some or all values. In such case it is up to the language
     * itself to use {@link com.oracle.truffle.api.vm.PolyglotEngine polyglot engine}
     * {@link com.oracle.truffle.api.vm.PolyglotEngine.Builder#setOut output},
     * {@link com.oracle.truffle.api.vm.PolyglotEngine.Builder#setErr error} and
     * {@link com.oracle.truffle.api.vm.PolyglotEngine.Builder#setIn input} streams. When
     * {@link com.oracle.truffle.api.vm.PolyglotEngine#eval} is called over an
     * {@link Source#isInteractive() interactive source} of a language that controls its interactive
     * behavior, it is the reponsibility of the language itself to print the result to
     * {@link com.oracle.truffle.api.vm.PolyglotEngine.Builder#setOut(OutputStream) standard output}
     * or {@link com.oracle.truffle.api.vm.PolyglotEngine.Builder#setErr(OutputStream) error output}
     * and/or access {@link com.oracle.truffle.api.vm.PolyglotEngine.Builder#setIn(InputStream)
     * standard input} in an appropriate way.
     *
     * @param context the execution context for doing the conversion
     * @param value the value to check. Either primitive type or
     *            {@link com.oracle.truffle.api.interop.TruffleObject}
     * @return <code>true</code> if the language implements an interactive response to evaluation of
     *         interactive sources.
     * @since 0.22
     */
    protected boolean isVisible(C context, Object value) {
        return true;
    }

    /**
     * Looks an additional language service up. By default it checks if the language itself is
     * implementing the requested class and if so, it returns <code>this</code>.
     * <p>
     * In future this method can be made protected and overridable by language implementors to
     * create more dynamic service system.
     *
     * @param <T> the type to request
     * @param clazz
     * @return
     */
    final /* protected */ <T> T lookup(Class<T> clazz) {
        if (clazz.isInterface()) {
            if (clazz.isInstance(this)) {
                return clazz.cast(this);
            }
        }
        return null;
    }

    /**
     * Find a meta-object of a value, if any. The meta-object represents a description of the
     * object, reveals it's kind and it's features. Some information that a meta-object might define
     * includes the base object's type, interface, class, methods, attributes, etc.
     * <p>
     * A programmatic {@link #toString(java.lang.Object, java.lang.Object) textual representation}
     * should be provided for meta-objects, when possible. The meta-object may have properties
     * describing their structure.
     * <p>
     * NOTE: Allocating the meta object must not be treated as or cause any
     * {@link com.oracle.truffle.api.instrumentation.AllocationListener reported guest language
     * value allocations}
     * <p>
     * When no meta-object is known, return <code>null</code>. The default implementation returns
     * <code>null</code>.
     *
     * @param context the execution context
     * @param value a value to find the meta-object of
     * @return the meta-object, or <code>null</code>
     * @since 0.22
     */
    protected Object findMetaObject(C context, Object value) {
        return null;
    }

    /**
     * Find a source location where a value is declared, if any. This is often useful especially for
     * retrieval of source locations of {@link #findMetaObject meta-objects}. The default
     * implementation returns <code>null</code>.
     *
     * @param context the execution context
     * @param value a value to get the source location for
     * @return a source location of the object, or <code>null</code>
     * @since 0.22
     */
    protected SourceSection findSourceLocation(C context, Object value) {
        return null;
    }

    /**
     * @since 0.8 or earlier
     * @deprecated in 0.25 use {@link #getContextReference()} instead
     */
    @Deprecated
    protected final Node createFindContextNode() {
        return AccessAPI.engineAccess().createFindContextNode(this);
    }

    /**
     * @since 0.8 or earlier
     * @deprecated in 0.25 use {@linkplain #getContextReference()}.
     *             {@linkplain ContextReference#get() get()} instead
     */
    @SuppressWarnings({"rawtypes", "unchecked", "deprecation"})
    @Deprecated
    protected final C findContext(Node n) {
        com.oracle.truffle.api.impl.FindContextNode fcn = (com.oracle.truffle.api.impl.FindContextNode) n;
        if (fcn.getTruffleLanguage() != this) {
            throw new ClassCastException();
        }
        return (C) fcn.executeFindContext();
    }

    /**
     * Creates a reference to the current context to be stored in an AST. The current context can be
     * accessed using the {@link ContextReference#get()} method of the returned reference. If a
     * context reference is created in the language class constructor an
     * {@link IllegalStateException} is thrown. The exception is also thrown if the reference is
     * tried to be created or accessed outside of the execution of an engine.
     * <p>
     * The returned reference identity is undefined. It might either return always the same instance
     * or a new reference for each invocation of the method. Please note that the current context
     * might vary between {@link RootNode#execute(VirtualFrame) executions} if resources or code is
     * shared between multiple contexts.
     *
     * @since 0.25
     */
    public final ContextReference<C> getContextReference() {
        if (reference == null) {
            throw new IllegalStateException("TruffleLanguage instance is not initialized. Cannot get the current context reference.");
        }
        return reference;
    }

    void initialize(LanguageInfo language, boolean singleton) {
        this.singletonLanguage = singleton;
        if (!singleton) {
            this.languageInfo = language;
            this.reference = new ContextReference<>(API.nodes().getEngineObject(languageInfo));
        }
    }

    CallTarget parse(Source source, Node context, MaterializedFrame frame, String... argumentNames) {
        ParsingRequest request = new ParsingRequest(source, context, frame, argumentNames);
        CallTarget target;
        try {
            target = request.parse(this);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            request.dispose();
        }
        return target;
    }

    /**
     * Returns the current language instance for the current {@link Thread thread}. If a root node
     * is accessible then {@link RootNode#getLanguage(Class)} should be used instead. Throws an
     * {@link IllegalStateException} if the language is not yet initialized or not executing on this
     * thread. If invoked on the fast-path then <code>languageClass</code> must be a compilation
     * final value.
     *
     * @param <T> the language type
     * @param languageClass the exact language class needs to be provided for the lookup.
     * @since 0.27
     */
    protected static <T extends TruffleLanguage<?>> T getCurrentLanguage(Class<T> languageClass) {
        return AccessAPI.engineAccess().getCurrentLanguage(languageClass);
    }

    /**
     * Returns the current language context for the current {@link Thread thread}. If a root node is
     * accessible then {@link RootNode#getCurrentContext(Class)} should be used instead. An
     * {@link IllegalStateException} is thrown if the language is not yet initialized or not
     * executing on this thread. This is a short-cut for {@link #getCurrentLanguage(Class)
     * getCurrent(languageClass)}.{@link #getContextReference() getContextReference()}.
     * {@link ContextReference#get() get()}. If invoked on the fast-path then
     * <code>languageClass</code> must be a compilation final value.
     *
     * @param <C> the context type
     * @param <T> the language type
     * @param languageClass the exact language class needs to be provided for the lookup.
     * @since 0.27
     */
    protected static <C, T extends TruffleLanguage<C>> C getCurrentContext(Class<T> languageClass) {
        return AccessAPI.engineAccess().getCurrentContext(languageClass);
    }

    /**
     * Represents execution environment of the {@link TruffleLanguage}. Each active
     * {@link TruffleLanguage} receives instance of the environment before any code is executed upon
     * it. The environment has knowledge of all active languages and can exchange symbols between
     * them.
     *
     * @since 0.8 or earlier
     */
    public static final class Env {

        private final Object vmObject; // PolyglotEngine.Language
        private final LanguageInfo language;
        private final TruffleLanguage<Object> spi;
        private final InputStream in;
        private final OutputStream err;
        private final OutputStream out;
        private final Map<String, Object> config;
        private final OptionValues options;
        private final String[] applicationArguments;
        private List<Object> services;
        @CompilationFinal private Object context;
        @CompilationFinal private volatile boolean initialized = false;
        @CompilationFinal private volatile Assumption initializedUnchangedAssumption = Truffle.getRuntime().createAssumption("Language context initialized unchanged");

        @SuppressWarnings("unchecked")
        private Env(Object vmObject, LanguageInfo language, OutputStream out, OutputStream err, InputStream in, Map<String, Object> config, OptionValues options, String[] applicationArguments) {
            this.vmObject = vmObject;
            this.language = language;
            this.spi = (TruffleLanguage<Object>) API.nodes().getLanguageSpi(language);
            this.in = in;
            this.err = err;
            this.out = out;
            this.config = config;
            this.options = options;
            this.applicationArguments = applicationArguments == null ? new String[0] : applicationArguments;
        }

        TruffleLanguage<Object> getSpi() {
            return spi;
        }

        void checkDisposed() {
            if (AccessAPI.engineAccess().isDisposed(vmObject)) {
                throw new IllegalStateException("Language environment is already disposed.");
            }
        }

        /**
         * Returns option values for the options described in
         * {@link TruffleLanguage#getOptionDescriptors()}. The returned options are never
         * <code>null</code>.
         *
         * @since 0.27
         */
        public OptionValues getOptions() {
            return options;
        }

        /**
         * Returns the application arguments that were provided for this context. The arguments
         * array and its elements are never <code>null</code>. It is up to the language
         * implementation whether and how they are accessible within the guest language scripts.
         *
         * @since 0.27
         */
        public String[] getApplicationArguments() {
            return applicationArguments;
        }

        /**
         * Explicitely imports a symbol from the polyglot scope. The polyglot scope consists of a
         * set of symbols that have been exported explicitely by the languages or the engine. This
         * set of symbols allows for data exchange between polyglot languages.
         *
         * <p>
         * The returned symbol value can either be a <code>TruffleObject</code> (e.g. a native
         * object from the other language) to support interoperability between languages,
         * {@link String} or one of the Java primitive wrappers ( {@link Integer}, {@link Double},
         * {@link Byte}, {@link Boolean}, etc.).
         * <p>
         *
         * @param symbolName the name of the symbol to search for
         * @return object representing the symbol or <code>null</code> if it does not exist
         * @since 0.8 or earlier
         */
        public Object importSymbol(String symbolName) {
            return AccessAPI.engineAccess().importSymbol(vmObject, this, symbolName);
        }

        /**
         * Returns an iterable collection of global symbols that are exported for a given name.
         * Multiple languages may export a symbol with a particular name. This method is intended to
         * be used to disambiguate exported symbols. The objects returned from the iterable conform
         * to {@link com.oracle.truffle.api.interop.java.JavaInterop#asTruffleValue interop
         * semantics} e.g. the expected returned type is either
         * {@link com.oracle.truffle.api.interop.TruffleObject}, or one of the wrappers of Java
         * primitive types (like {@link Integer}, {@link Double}).
         *
         * @param globalName the name of the symbol to search for
         * @return iterable returning objects representing the symbol
         * @since 0.22
         * @deprecated in 0.27 use {@link #importSymbol(String)} instead. There is now always
         *             exactly one value per exported symbol that is returned in the order that they
         *             are exported.
         */
        @Deprecated
        public Iterable<? extends Object> importSymbols(String globalName) {
            return AccessAPI.engineAccess().importSymbols(vmObject, this, globalName);
        }

        /**
         * Explicitely exports a symbol to the polyglot scope. The polyglot scope consists of a set
         * of symbols that have been exported explicitely by the languages or the engine. This set
         * of symbols allows for data exchange between polyglot languages. If a symbol is already
         * exported then it is overwritten. An exported symbol can be cleared by calling the method
         * with <code>null</code>.
         * <p>
         * The exported symbol value can either be a <code>TruffleObject</code> (e.g. a native
         * object from the other language) to support interoperability between languages,
         * {@link String} or one of the Java primitive wrappers ( {@link Integer}, {@link Double},
         * {@link Byte}, {@link Boolean}, etc.).
         * <p>
         *
         * @param symbolName the name with which the symbol should be exported into the polyglot
         *            scope
         * @param value the value to export for
         * @since 0.27
         */
        public void exportSymbol(String symbolName, Object value) {
            AccessAPI.engineAccess().exportSymbol(vmObject, symbolName, value);
        }

        /**
         * Allows it to be determined if this {@link com.oracle.truffle.api.vm.PolyglotEngine} can
         * execute code written in a language with a given MIME type.
         *
         * @see Source#getMimeType()
         * @see #parse(Source, String...)
         *
         * @return a boolean that indicates if the MIME type is supported
         * @since 0.11
         */
        public boolean isMimeTypeSupported(String mimeType) {
            checkDisposed();
            return AccessAPI.engineAccess().isMimeTypeSupported(vmObject, mimeType);
        }

        /**
         * Evaluates source of (potentially different) language. The {@link Source#getMimeType()
         * MIME type} is used to identify the {@link TruffleLanguage} to use to perform the
         * {@link #parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest)} . The names of
         * arguments are parameters for the resulting {#link CallTarget} that allow the
         * <code>source</code> to reference the actual parameters passed to
         * {@link CallTarget#call(java.lang.Object...)}.
         *
         * @param source the source to evaluate
         * @param argumentNames the names of {@link CallTarget#call(java.lang.Object...)} arguments
         *            that can be referenced from the source
         * @return the call target representing the parsed result
         * @since 0.8 or earlier
         */
        public CallTarget parse(Source source, String... argumentNames) {
            CompilerAsserts.neverPartOfCompilation();
            checkDisposed();
            return AccessAPI.engineAccess().getEnvForLanguage(vmObject, source.getMimeType()).spi.parse(source, null, null, argumentNames);
        }

        /**
         * Input associated with {@link com.oracle.truffle.api.vm.PolyglotEngine} this language is
         * being executed in.
         *
         * @return reader, never <code>null</code>
         * @since 0.8 or earlier
         */
        public InputStream in() {
            checkDisposed();
            return in;
        }

        /**
         * Standard output writer for {@link com.oracle.truffle.api.vm.PolyglotEngine} this language
         * is being executed in.
         *
         * @return writer, never <code>null</code>
         * @since 0.8 or earlier
         */
        public OutputStream out() {
            checkDisposed();
            return out;
        }

        /**
         * Standard error writer for {@link com.oracle.truffle.api.vm.PolyglotEngine} this language
         * is being executed in.
         *
         * @return writer, never <code>null</code>
         * @since 0.8 or earlier
         */
        public OutputStream err() {
            checkDisposed();
            return err;
        }

        /**
         * Looks additional service up. An environment for a particular {@link TruffleLanguage
         * language} and a {@link com.oracle.truffle.api.vm.PolyglotEngine} may also be associated
         * with additional services. One can request implementations of such services by calling
         * this method with the type identifying the requested service and its API.
         *
         * Services that can be obtained via this method include
         * {@link com.oracle.truffle.api.instrumentation.Instrumenter} and others.
         *
         * @param <T> type of requested service
         * @param type class of requested service
         * @return instance of T or <code>null</code> if there is no such service available
         * @since 0.12
         */
        public <T> T lookup(Class<T> type) {
            checkDisposed();
            for (Object obj : services) {
                if (type.isInstance(obj)) {
                    return type.cast(obj);
                }
            }
            return null;
        }

        /**
         * Returns an additional service provided by this instrument, specified by type. If an
         * instrument is not enabled, it will be enabled automatically by requesting a supported
         * service. If the instrument does not provide a service for a given type it will not be
         * enabled automatically.
         *
         * @param <S> the requested type
         * @param instrument identification of the instrument to query
         * @param type the class of the requested type
         * @return the registered service or <code>null</code> if none is found
         * @since 0.26
         */
        @SuppressWarnings("static-method")
        public <S> S lookup(InstrumentInfo instrument, Class<S> type) {
            return AccessAPI.engineAccess().lookup(instrument, type);
        }

        /**
         * Returns an additional service provided by the given language, specified by type. If an
         * language is not loaded, it will not be automatically loaded by requesting a service. In
         * order to ensure a language to be loaded at least one {@link Source} must be
         * {@link #parse(Source, String...) parsed} first.
         *
         * @param <S> the requested type
         * @param language the language to query
         * @param type the class of the requested type
         * @return the registered service or <code>null</code> if none is found
         * @since 0.26
         */
        public <S> S lookup(@SuppressWarnings("hiding") LanguageInfo language, Class<S> type) {
            if (this.language == language) {
                throw new IllegalArgumentException("Cannot request services from the current language.");
            }
            TruffleLanguage<?> otherSpi = AccessAPI.nodesAccess().getLanguageSpi(language);
            return otherSpi.lookup(type);
        }

        /**
         * Returns a map mime-type to language instance of all languages that are installed in the
         * environment. Using the language instance additional services can be
         * {@link #lookup(LanguageInfo, Class) looked up} .
         *
         * @since 0.26
         */
        public Map<String, LanguageInfo> getLanguages() {
            return AccessAPI.engineAccess().getLanguages(vmObject);
        }

        /**
         * Returns a map instrument-id to instrument instance of all instruments that are installed
         * in the environment. Using the instrument instance additional services can be
         * {@link #lookup(InstrumentInfo, Class) looked up} .
         *
         * @since 0.26
         */
        public Map<String, InstrumentInfo> getInstruments() {
            return AccessAPI.engineAccess().getInstruments(vmObject);
        }

        /**
         * Configuration arguments for this language. Arguments set
         * {@link com.oracle.truffle.api.vm.PolyglotEngine.Builder#config when constructing the
         * engine} are accessible via this map.
         *
         * This method (in combination with
         * {@link com.oracle.truffle.api.vm.PolyglotEngine.Builder#config}) provides a
         * straight-forward way to pass implementation-level arguments, as typically specified on a
         * command line, to the languages.
         *
         * {@link com.oracle.truffle.api.vm.PolyglotEngineSnippets#initializeWithParameters}
         *
         * In contrast to {@link com.oracle.truffle.api.vm.PolyglotEngine.Builder#globalSymbol
         * global symbols} the provided values are passed in exactly as specified, because these
         * configuration arguments are strictly at the implementation level and not language-level
         * objects.
         *
         * These configuration arguments are available when
         * {@link #createContext(com.oracle.truffle.api.TruffleLanguage.Env) creating the language
         * context} to make it possible to take them into account before the language gets ready for
         * execution. This is the most common way to access them:
         *
         * {@link TruffleLanguageSnippets.MyLanguage#createContext}
         *
         * @return read-only view of configuration options for this language
         * @since 0.11
         */
        public Map<String, Object> getConfig() {
            checkDisposed();
            return config;
        }

        @SuppressWarnings("rawtypes")
        @TruffleBoundary
        <E extends TruffleLanguage> E getLanguage(Class<E> languageClass) {
            checkDisposed();
            if (languageClass != spi.getClass()) {
                throw new IllegalArgumentException("Invalid access to language " + languageClass + ".");
            }
            return languageClass.cast(spi);
        }

        Object findExportedSymbol(String globalName, boolean onlyExplicit) {
            return spi.findExportedSymbol(context, globalName, onlyExplicit);
        }

        Object getLanguageGlobal() {
            return spi.getLanguageGlobal(context);
        }

        Object findMetaObject(Object obj) {
            final Object rawValue = AccessAPI.engineAccess().findOriginalObject(obj);
            return spi.findMetaObject(context, rawValue);
        }

        SourceSection findSourceLocation(Object obj) {
            final Object rawValue = AccessAPI.engineAccess().findOriginalObject(obj);
            return spi.findSourceLocation(context, rawValue);
        }

        boolean isObjectOfLanguage(Object obj) {
            final Object rawValue = AccessAPI.engineAccess().findOriginalObject(obj);
            return spi.isObjectOfLanguage(rawValue);
        }

        void dispose() {
            spi.disposeContext(context);
        }

        void postInit() {
            try {
                spi.initializeContext(context);
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            } finally {
                initialized = true;
                Assumption old = initializedUnchangedAssumption;
                initializedUnchangedAssumption = Truffle.getRuntime().createAssumption("Language context initialized unchanged");
                old.invalidate();
            }
        }

        private boolean isInitialized() {
            if (initializedUnchangedAssumption.isValid()) {
                return initialized;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return initialized;
            }
        }

        String toStringIfVisible(Object value, boolean checkVisibility) {
            if (checkVisibility) {
                if (!spi.isVisible(context, value)) {
                    return null;
                }
            }
            return spi.toString(context, value);
        }

    }

    /**
     * Represents a reference to the current context to be stored in an AST. A reference can be
     * created using {@link TruffleLanguage#getContextReference()} and the current context can be
     * accessed using the {@link ContextReference#get()} method of the returned reference.
     * <p>
     * Please note that the current context might vary between {@link RootNode#execute(VirtualFrame)
     * executions} if resources or code is shared between multiple contexts.
     *
     * @since 0.25
     */
    public static final class ContextReference<C> {

        private final Object languageShared;

        private ContextReference(Object languageShared) {
            this.languageShared = languageShared;
        }

        /**
         * Returns the current context associated with the language this reference was created with.
         * If a context is accessed during {@link TruffleLanguage#createContext(Env) context
         * creation} or in the language class constructor an {@link IllegalStateException} is
         * thrown. This methods is designed to be called safely from compiled code paths.
         * <p>
         * Please note that the current context might vary between
         * {@link RootNode#execute(VirtualFrame) executions} if resources or code is shared between
         * multiple contexts.
         *
         * @since 0.25
         */
        @SuppressWarnings("unchecked")
        public C get() {
            return (C) AccessAPI.engineAccess().contextReferenceGet(languageShared);
        }

    }

    static final AccessAPI API = new AccessAPI();

    static final class AccessAPI extends Accessor {

        static EngineSupport engineAccess() {
            return API.engineSupport();
        }

        static InstrumentSupport instrumentAccess() {
            return API.instrumentSupport();
        }

        static Nodes nodesAccess() {
            return API.nodes();
        }

        @Override
        protected LanguageSupport languageSupport() {
            return new LanguageImpl();
        }

        @Override
        protected Nodes nodes() {
            return super.nodes();
        }
    }

    static final class LanguageImpl extends Accessor.LanguageSupport {

        @Override
        public InstrumentInfo createInstrument(Object vmObject, String id, String name, String version) {
            return new InstrumentInfo(vmObject, id, name, version);
        }

        @Override
        public Object getVMObject(InstrumentInfo info) {
            return info.getVmObject();
        }

        @Override
        public void initializeLanguage(LanguageInfo language, TruffleLanguage<?> impl, boolean legacyLanguage) {
            AccessAPI.nodesAccess().setLanguageSpi(language, impl);
            impl.initialize(language, legacyLanguage);
        }

        @Override
        public Object lookupSymbol(Env env, String globalName) {
            return env.spi.lookupSymbol(env.context, globalName);
        }

        @Override
        public Object getContext(Env env) {
            return env.context;
        }

        @Override
        public TruffleLanguage<?> getSPI(Env env) {
            return env.spi;
        }

        @Override
        public Env createEnv(Object vmObject, LanguageInfo language, OutputStream stdOut, OutputStream stdErr, InputStream stdIn, Map<String, Object> config, OptionValues options,
                        String[] applicationArguments) {
            Env env = new Env(vmObject, language, stdOut, stdErr, stdIn, config, options, applicationArguments);
            LinkedHashSet<Object> collectedServices = new LinkedHashSet<>();
            AccessAPI.instrumentAccess().collectEnvServices(collectedServices, API.nodes().getEngineObject(language), language);
            env.services = new ArrayList<>(collectedServices);
            env.context = env.getSpi().createContext(env);
            return env;
        }

        @Override
        public void postInitEnv(Env env) {
            env.postInit();
        }

        @Override
        public boolean isContextInitialized(Env env) {
            return env.isInitialized();
        }

        @Override
        public CallTarget parse(Env env, Source code, Node context, String... argumentNames) {
            return env.getSpi().parse(code, context, null, argumentNames);
        }

        @Override
        public LanguageInfo getLanguageInfo(Env env) {
            return env.language;
        }

        @Override
        public void onThrowable(RootNode root, Throwable e) {
            TruffleStackTrace.fillIn(e);
        }

        @Override
        public Object evalInContext(String code, Node node, final MaterializedFrame mFrame) {
            RootNode rootNode = node.getRootNode();
            if (rootNode == null) {
                throw new IllegalArgumentException("Cannot evaluate in context using a node that is not yet adopated using a RootNode.");
            }

            LanguageInfo info = rootNode.getLanguageInfo();
            if (info == null) {
                throw new IllegalArgumentException("Cannot evaluate in context using a without an associated TruffleLanguage.");
            }

            final Source source = Source.newBuilder(code).name("eval in context").mimeType("content/unknown").build();
            CallTarget target = API.nodes().getLanguageSpi(info).parse(source, node, mFrame);

            RootNode exec;
            if (target instanceof RootCallTarget) {
                exec = ((RootCallTarget) target).getRootNode();
            } else {
                throw new IllegalStateException("" + target);
            }

            try {
                // TODO find a better way to convert from materialized frame to virtual frame.
                // maybe we should always use Frame everywhere?
                return exec.execute(new VirtualFrame() {

                    public void setObject(FrameSlot slot, Object value) {
                        mFrame.setObject(slot, value);
                    }

                    public void setLong(FrameSlot slot, long value) {
                        mFrame.setLong(slot, value);
                    }

                    public void setInt(FrameSlot slot, int value) {
                        mFrame.setInt(slot, value);
                    }

                    public void setFloat(FrameSlot slot, float value) {
                        mFrame.setFloat(slot, value);
                    }

                    public void setDouble(FrameSlot slot, double value) {
                        mFrame.setDouble(slot, value);
                    }

                    public void setByte(FrameSlot slot, byte value) {
                        mFrame.setByte(slot, value);
                    }

                    public void setBoolean(FrameSlot slot, boolean value) {
                        mFrame.setBoolean(slot, value);
                    }

                    public MaterializedFrame materialize() {
                        return mFrame;
                    }

                    public boolean isObject(FrameSlot slot) {
                        return mFrame.isObject(slot);
                    }

                    public boolean isLong(FrameSlot slot) {
                        return mFrame.isLong(slot);
                    }

                    public boolean isInt(FrameSlot slot) {
                        return mFrame.isInt(slot);
                    }

                    public boolean isFloat(FrameSlot slot) {
                        return mFrame.isFloat(slot);
                    }

                    public boolean isDouble(FrameSlot slot) {
                        return mFrame.isDouble(slot);
                    }

                    public boolean isByte(FrameSlot slot) {
                        return mFrame.isByte(slot);
                    }

                    public boolean isBoolean(FrameSlot slot) {
                        return mFrame.isBoolean(slot);
                    }

                    public Object getValue(FrameSlot slot) {
                        return mFrame.getValue(slot);
                    }

                    public Object getObject(FrameSlot slot) throws FrameSlotTypeException {
                        return mFrame.getObject(slot);
                    }

                    public long getLong(FrameSlot slot) throws FrameSlotTypeException {
                        return mFrame.getLong(slot);
                    }

                    public int getInt(FrameSlot slot) throws FrameSlotTypeException {
                        return mFrame.getInt(slot);
                    }

                    public FrameDescriptor getFrameDescriptor() {
                        return mFrame.getFrameDescriptor();
                    }

                    public float getFloat(FrameSlot slot) throws FrameSlotTypeException {
                        return mFrame.getFloat(slot);
                    }

                    public double getDouble(FrameSlot slot) throws FrameSlotTypeException {
                        return mFrame.getDouble(slot);
                    }

                    public byte getByte(FrameSlot slot) throws FrameSlotTypeException {
                        return mFrame.getByte(slot);
                    }

                    public boolean getBoolean(FrameSlot slot) throws FrameSlotTypeException {
                        return mFrame.getBoolean(slot);
                    }

                    public Object[] getArguments() {
                        return mFrame.getArguments();
                    }
                });
            } catch (Exception ex) {
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                }
                throw new RuntimeException(ex);
            }
        }

        @Override
        public Object findExportedSymbol(TruffleLanguage.Env env, String globalName, boolean onlyExplicit) {
            return env.findExportedSymbol(globalName, onlyExplicit);
        }

        @Override
        public LanguageInfo getLanguageInfo(TruffleLanguage<?> language) {
            return language.languageInfo;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public LanguageInfo getLegacyLanguageInfo(Object vm, Class<? extends TruffleLanguage> languageClass) {
            if (vm == null) {
                return null;
            }
            Env env = AccessAPI.engineAccess().findEnv(vm, languageClass, false);
            if (env != null) {
                return env.language;
            } else {
                return null;
            }
        }

        @Override
        public Object languageGlobal(TruffleLanguage.Env env) {
            return env.getLanguageGlobal();
        }

        @Override
        public void dispose(Env env) {
            env.dispose();
        }

        @Override
        public String toStringIfVisible(Env env, Object value, boolean checkVisibility) {
            return env.toStringIfVisible(value, checkVisibility);
        }

        @Override
        public Object findMetaObject(Env env, Object obj) {
            return env.findMetaObject(obj);
        }

        @Override
        public SourceSection findSourceLocation(Env env, Object obj) {
            return env.findSourceLocation(obj);
        }

        @Override
        public boolean isObjectOfLanguage(Env env, Object value) {
            return env.isObjectOfLanguage(value);
        }

        @Override
        public <S> S lookup(LanguageInfo language, Class<S> type) {
            return TruffleLanguage.AccessAPI.nodesAccess().getLanguageSpi(language).lookup(type);
        }

        @Override
        public OptionDescriptors describeOptions(TruffleLanguage<?> language, String requiredGroup) {
            OptionDescriptors descriptors = language.getOptionDescriptors();
            if (descriptors == null) {
                return OptionDescriptors.EMPTY;
            }
            String groupPlusDot = requiredGroup + ".";
            for (OptionDescriptor descriptor : descriptors) {
                if (!descriptor.getName().equals(requiredGroup) && !descriptor.getName().startsWith(groupPlusDot)) {
                    throw new IllegalArgumentException(String.format("Illegal option prefix in name '%s' specified for option described by language '%s'. " +
                                    "The option prefix must match the id of the language '%s'.",
                                    descriptor.getName(), language.getClass().getName(), requiredGroup));
                }
            }
            return descriptors;
        }

    }
}

class TruffleLanguageSnippets {
    class Context {
        final String[] args;
        final Env env;
        CallTarget mul;

        Context(String[] args) {
            this.args = args;
            this.env = null;
        }

        Context(Env env) {
            this.env = env;
            this.args = null;
        }

        Context fork() {
            return null;
        }
    }

    // @formatter:off
    abstract
    // BEGIN: TruffleLanguageSnippets.MyLanguage#createContext
    class MyLanguage extends TruffleLanguage<Context> {
        @Override
        protected Context createContext(Env env) {
            String[] args = (String[]) env.getConfig().get("CMD_ARGS");
            return new Context(args);
        }
    }
    // END: TruffleLanguageSnippets.MyLanguage#createContext

    abstract
    // BEGIN: TruffleLanguageSnippets.PostInitLanguage#createContext
    class PostInitLanguage extends TruffleLanguage<Context> {
        @Override
        protected Context createContext(Env env) {
            // "quickly" create the context
            return new Context(env);
        }

        @Override
        protected void initializeContext(Context context) throws IOException {
            // called "later" to finish the initialization
            // for example call into another language
            Source source =
                Source.newBuilder("function mul(x, y) { return x * y }").
                name("mul.js").
                mimeType("text/javascript").
                build();
            context.mul = context.env.parse(source);
        }
    }
    // END: TruffleLanguageSnippets.PostInitLanguage#createContext

    // BEGIN: TruffleLanguageSnippets#parseWithParams
    public void parseWithParams(Env env) {
        Source multiply = Source.newBuilder("a * b").
            mimeType("text/javascript").
            name("mul.js").
            build();
        CallTarget method = env.parse(multiply, "a", "b");
        Number fortyTwo = (Number) method.call(6, 7);
        assert 42 == fortyTwo.intValue();
        Number ten = (Number) method.call(2, 5);
        assert 10 == ten.intValue();
    }
    // END: TruffleLanguageSnippets#parseWithParams


}
