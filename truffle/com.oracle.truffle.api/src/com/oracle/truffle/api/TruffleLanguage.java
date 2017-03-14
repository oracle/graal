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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
 * A new {@link TruffleLanguage language implementation} instance is instantiated for each engine
 * that is created using the {@linkplain com.oracle.truffle.api.vm.PolyglotEngine.Builder#build()
 * engine builder}. The same language implementation instance is shared between multiple
 * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine#fork() forked} engine instances. When a fork
 * is requested and a context instance was already {@link #createContext(Env) created}, then the
 * language implementation will be asked to create a fork from an existing context by calling
 * {@link #forkContext(Object)}. Else, if the language is used for the first time, then a new
 * context instance is {@link #createContext(Env) created} instead.
 * <p>
 * State can be shared between multiple forked context instances by saving them as in a field of the
 * {@link TruffleLanguage} subclass. The implementation needs to ensure data isolation between the
 * contexts. However ASTs or assumptions can be shared across multiple contexts if modifying them
 * does not affect language semantics.
 * <p>
 * Whenever an engine is disposed then each initialized context will be disposed
 * {@link #disposeContext(Object) disposed}.
 *
 * <h4>Cardinalities</h4>
 *
 * <i>One</i> language implementation instance refers to other classes using the following
 * cardinalities:
 * <ul>
 * <li><i>many</i> {@linkplain #createContext(Env) created} language contexts
 * <li><i>many</i> {@linkplain #forkContext(Object) forked} language contexts
 * <li><i>many</i> {@linkplain TruffleRuntime#createCallTarget(RootNode) created} {@link CallTarget
 * call targets} potentially shared between contexts.
 * </ul>
 *
 * <h4>Context Mutability</h4>
 *
 * The {@link #getContextReference() current} context can vary between
 * {@link RootNode#execute(VirtualFrame) executions}. Therefore the current context should not be
 * stored in a field of the AST unless every context reference is known to be always
 * {@link ContextReference#isFinal() final}.
 *
 * The context reference is always final if the following conditions apply:
 * <ul>
 * <li>{@link TruffleLanguage#forkContext(Object) Forking} the context always throws
 * {@link UnsupportedOperationException} (default behavior) or ensures that all ASTs stored in the
 * context are copied without references to the original context.
 * <li>No AST is shared across context instances. In other words the {@link TruffleLanguage}
 * instance is not used to share ASTs between context instances.
 * </ul>
 * These conditions are not verified by the framework, therefore it is recommended to insert an
 * assertion if the context is assumed always {@link ContextReference#isFinal() final}.
 * <p>
 * If one of the conditions is not satisfied then the language implementation needs to be prepared
 * for varying context instances i.e. {@link ContextReference#isFinal()} needs to be checked to
 * remain <code>true</code> while the context reference is stored in the AST. If a reference becomes
 * non-final all fields directly storing the current context must be cleared.
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
    @CompilationFinal private Env env;
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
         * Unique name of your language. This name will be exposed to users via the
         * {@link com.oracle.truffle.api.vm.PolyglotEngine.Language#getName()} getter.
         *
         * @return identifier of your language
         */
        String name();

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
    protected abstract C createContext(@SuppressWarnings("hiding") Env env);

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
     * Forks a {@link #createContext(Env) created} context by returning an instance that behaves
     * like an equivalent copy of the given context. By default forking throws an
     * {@link UnsupportedOperationException} to indicate that forking is unsupported for this
     * language. Forks are requested only for language contexts that have been
     * {@link #createContext(Env) created}. Please note that the context might not yet be
     * {@link #initializeContext(Object) initialized}.
     * <p>
     * The forked context offers the same functionality and data as the given context. Every
     * operation that is performed on the original context must be functional in the forked context
     * and return the same value. Language specific configuration parameters {@link Env#getConfig()}
     * can be used to diverge from this default behavior. For example, not supporting certain
     * modifications to the context after forking for performance reasons.
     * <p>
     * Valid techniques to implement forking are:
     * <ul>
     * <li><b>Copy data and code:</b> Create a full copy of the original context including stored
     * code / {@link CallTarget call targets}. References to mutable state from one context to the
     * other are entirely eliminated. Copying code and data is a memory intensive operation but
     * allows to assume always {@link ContextReference#isFinal() final} context references.
     * <li><b>Copy data, share code:</b> Create a copy of all mutable data of the original context
     * but share code / {@link CallTarget call targets} with the other instance. Using this strategy
     * the code must be prepared for {@link ContextReference#isFinal() non-final} context
     * references.
     * <li><b>Copy on write:</b> Creates a copy lazily if either the original or forked context is
     * modified. This is the most efficient way of implementing forking, as it uses minimal memory,
     * but might allow to cache certain context operations, by assuming that the original is still
     * unmodified. Using this strategy the code must be prepared for
     * {@link ContextReference#isFinal() non-final} context references.
     * </ul>
     * <p>
     * Forking is invoked by an engines fork method or via
     * {@link TruffleLanguage.Env#createFork(CallTarget)}. Forked contexts are
     * {@link #disposeContext(Object) disposed} when it is requested by the engine.
     *
     * @see TruffleLanguage
     * @see ContextReference
     * @since 0.25
     */
    protected C forkContext(@SuppressWarnings("unused") C context) {
        throw new UnsupportedOperationException();
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
    protected abstract Object findExportedSymbol(C context, String globalName, boolean onlyExplicit);

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
     * Find a meta-object of a value, if any. The meta-object represents a description of the
     * object, reveals it's kind and it's features. Some information that a meta-object might define
     * includes the base object's type, interface, class, methods, attributes, etc.
     * <p>
     * A programmatic {@link #toString(java.lang.Object, java.lang.Object) textual representation}
     * should be provided for meta-objects, when possible. The meta-object may have properties
     * describing their structure.
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
     * or a new reference for each invocation of the method.
     * <p>
     * Please note that the current context can vary between {@link RootNode#execute(VirtualFrame)
     * executions}. Therefore the current context should not be stored in a field of an AST unless
     * the context reference is known to be always {@link ContextReference#isFinal() final} for your
     * language. For further details on final contexts please refer to the javadoc in
     * {@link ContextReference#isFinal()}.
     *
     * @see ContextReference#isFinal()
     * @since 0.25
     */
    public final ContextReference<C> getContextReference() {
        if (reference == null) {
            throw new IllegalStateException("TruffleLanguage instance is not initialized. Cannot get the current context reference.");
        }
        return reference;
    }

    void initialize(Env initEnv, boolean singleton) {
        this.singletonLanguage = singleton;
        if (!singleton) {
            this.env = initEnv;
            this.reference = new ContextReference<>(env.languageShared);
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
     * Represents public information about this language.
     *
     * @since 0.25
     */
    public static final class Info {

        private final String name;
        private final String version;
        private final Set<String> mimeTypes;
        final Env env;

        private Info(Env env, String name, String version, Set<String> mimeTypes) {
            this.name = name;
            this.version = version;
            this.mimeTypes = mimeTypes;
            this.env = env;
        }

        /**
         * Returns the unique name of the language. This name is equivalent to the name returned by
         * {@link com.oracle.truffle.api.vm.PolyglotEngine.Language#getName()}.
         *
         * @since 0.25
         */
        public String getName() {
            return name;
        }

        /**
         * Returns the version of the language. This version is equivalent to the name returned by
         * {@link com.oracle.truffle.api.vm.PolyglotEngine.Language#getVersion()}.
         *
         * @since 0.25
         */
        public String getVersion() {
            return version;
        }

        /**
         * Returns the MIME types supported by this language. This set is equivalent to the set
         * returned by {@link com.oracle.truffle.api.vm.PolyglotEngine.Language#getMimeTypes()}.
         *
         * @since 0.25
         */
        public Set<String> getMimeTypes() {
            return mimeTypes;
        }
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

        private final Object languageShared;
        private final TruffleLanguage<Object> lang;
        private final InputStream in;
        private final OutputStream err;
        private final OutputStream out;
        private final Map<String, Object> config;
        private List<Object> services;
        private TruffleLanguage.Info info;

        private Env(Object languageShared, TruffleLanguage<Object> lang, OutputStream out, OutputStream err, InputStream in, Map<String, Object> config) {
            this.languageShared = languageShared;
            this.in = in;
            this.err = err;
            this.out = out;
            this.lang = lang;
            this.config = config;
        }

        void checkDisposed() {
            if (AccessAPI.engineAccess().isDisposed(languageShared)) {
                throw new IllegalStateException("Language environment is already disposed.");
            }
        }

        /**
         * Forks all initialized language contexts and creates a {@link CallTarget call target} that
         * executes the given {@link CallTarget call target} within the newly forked context. Throws
         * {@link UnsupportedOperationException} if one of the initialized languages does not
         * support {@link TruffleLanguage#forkContext(Object) forking}. All arguments passed when
         * {@link CallTarget#call(Object...) calling} the returned target are provided as
         * {@link VirtualFrame#getArguments() arguments} within the
         * {@link RootNode#execute(VirtualFrame)} implementation of the given {@link RootNode}.
         * <p>
         * If possible make sure to {@link #disposeFork(CallTarget) dispose} the fork if it is not
         * longer used. If not disposed manually then the fork is disposed automatically with the
         * current context.
         * <p>
         * Example usage:{@link TruffleLanguageSnippets#forkLanguageContext}
         *
         * @see com.oracle.truffle.api.vm.PolyglotEngine#fork()
         * @since 0.25
         */
        public CallTarget createFork(CallTarget root) throws UnsupportedOperationException {
            checkDisposed();
            return AccessAPI.engineAccess().fork(languageShared, root);
        }

        /**
         * Throws {@link IllegalArgumentException} if the given {@link CallTarget call target} does
         * not originate from {@link #createFork(CallTarget)}.
         *
         * @since 0.25
         */
        public void disposeFork(CallTarget forkTarget) {
            checkDisposed();
            AccessAPI.engineAccess().disposeFork(languageShared, forkTarget);
        }

        /**
         * Asks the environment to go through other registered languages and find whether they
         * export global symbol of specified name. The expected return type is either
         * <code>TruffleObject</code>, or one of the wrappers of Java primitive types (
         * {@link Integer}, {@link Double}).
         *
         * @param globalName the name of the symbol to search for
         * @return object representing the symbol or <code>null</code>
         * @since 0.8 or earlier
         */
        public Object importSymbol(String globalName) {
            checkDisposed();
            Iterator<? extends Object> it = AccessAPI.engineAccess().importSymbols(languageShared, this, globalName).iterator();
            return it.hasNext() ? it.next() : null;
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
         */
        public Iterable<? extends Object> importSymbols(String globalName) {
            checkDisposed();
            return AccessAPI.engineAccess().importSymbols(languageShared, this, globalName);
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
            return AccessAPI.engineAccess().isMimeTypeSupported(languageShared, mimeType);
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
            checkDisposed();
            return AccessAPI.engineAccess().getEnvForLanguage(languageShared, source.getMimeType()).lang.parse(source, null, null, argumentNames);
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
            if (languageClass != lang.getClass()) {
                throw new IllegalArgumentException("Invalid access to language " + languageClass + ".");
            }
            return languageClass.cast(lang);
        }

        Object findExportedSymbol(Object context, String globalName, boolean onlyExplicit) {
            return lang.findExportedSymbol(context, globalName, onlyExplicit);
        }

        Object getLanguageGlobal(Object context) {
            return lang.getLanguageGlobal(context);
        }

        Object findMetaObject(Object context, Object obj) {
            final Object rawValue = AccessAPI.engineAccess().findOriginalObject(obj);
            return lang.findMetaObject(context, rawValue);
        }

        SourceSection findSourceLocation(Object context, Object obj) {
            final Object rawValue = AccessAPI.engineAccess().findOriginalObject(obj);
            return lang.findSourceLocation(context, rawValue);
        }

        void dispose(Object context) {
            lang.disposeContext(context);
        }

        void postInit(Object context) {
            try {
                lang.initializeContext(context);
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        String toStringIfVisible(Object context, Object value, boolean checkVisibility) {
            if (checkVisibility) {
                if (!lang.isVisible(context, value)) {
                    return null;
                }
            }
            return lang.toString(context, value);
        }

    }

    /**
     * Represents a reference to the current context to be stored in an AST. A reference can be
     * created using {@link TruffleLanguage#getContextReference()} and the current context can be
     * accessed using the {@link ContextReference#get()} method of the returned reference.
     * <p>
     * Please note that the current context returned by {@link ContextReference#get()} can vary
     * between {@link RootNode#execute(VirtualFrame) executions}. Therefore the current context
     * should not be stored in a field of an AST unless the context reference is known to be always
     * {@link ContextReference#isFinal() final} for your language. For further details on final
     * contexts please refer to the javadoc in {@link ContextReference#isFinal()}.
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
         * Please note that the current context can vary between
         * {@link RootNode#execute(VirtualFrame) executions}. Therefore the current context should
         * not be stored in a field of an AST unless the context reference is known to be always
         * {@link ContextReference#isFinal() final} for your language. For further details please
         * refer to the Context Mutability section in the {@link TruffleLanguage} javadoc.
         *
         * @since 0.25
         */
        @SuppressWarnings("unchecked")
        public C get() {
            return (C) AccessAPI.engineAccess().contextReferenceGet(languageShared);
        }

                        /**
                         * Returns <code>true</code> if this reference is expected to always return
                         * the same context instance. It returns <code>false</code> if the context
                         * returned by {@link #get()} can differ between
                         * {@link RootNode#execute(VirtualFrame) executions}. Therefore the current
                         * context should not be stored in a field of an AST unless the context
                         * reference is known to be always {@link ContextReference#isFinal() final}
                         * for the language. A context reference that that is non-final will never
                         * become final again. For further details please refer to the Context
                         * Mutability section in the {@link TruffleLanguage} javadoc.
                         *
                         * @since 0.25
                         */
                        /* NOTNOW public */ boolean isFinal() {
            return AccessAPI.engineAccess().contextReferenceFinal(languageShared);
        }

    }

    static final AccessAPI API = new AccessAPI();

    private static final class AccessAPI extends Accessor {

        static EngineSupport engineAccess() {
            return API.engineSupport();
        }

        static InstrumentSupport instrumentAccess() {
            return API.instrumentSupport();
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

        @SuppressWarnings("unchecked")
        @Override
        public Env createEnv(Object languageShared, TruffleLanguage<?> language, boolean legacyLanguage, OutputStream stdOut, OutputStream stdErr, InputStream stdIn, Map<String, Object> config,
                        String name,
                        String version, Set<String> mimeTypes) {
            Env env = new Env(languageShared, (TruffleLanguage<Object>) language, stdOut, stdErr, stdIn, config);
            Info info = new Info(env, name, version, mimeTypes);
            env.info = info;
            LinkedHashSet<Object> collectedServices = new LinkedHashSet<>();
            AccessAPI.instrumentAccess().collectEnvServices(collectedServices, languageShared, info);
            env.services = new ArrayList<>(collectedServices);
            language.initialize(env, legacyLanguage);
            return env;
        }

        @Override
        public void postInitEnv(Env env, Object context) {
            env.postInit(context);
        }

        @Override
        public CallTarget parse(Env env, Source code, Node context, String... argumentNames) {
            return env.lang.parse(code, context, null, argumentNames);
        }

        @Override
        public Info getInfo(Env env) {
            return env.info;
        }

        @Override
        public Object evalInContext(Object sourceVM, String code, Node node, final MaterializedFrame mFrame) {
            RootNode rootNode = node.getRootNode();
            if (rootNode == null) {
                throw new IllegalArgumentException("Cannot evaluate in context using a node that is not yet adopated using a RootNode.");
            }

            Env env = rootNode.getLanguageInfo().env;
            if (env == null) {
                throw new IllegalArgumentException("Cannot evaluate in context using a without an associated TruffleLanguage.");
            }

            final Source source = Source.newBuilder(code).name("eval in context").mimeType("content/unknown").build();
            CallTarget target = env.lang.parse(source, node, mFrame);

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
        public Object findExportedSymbol(TruffleLanguage.Env env, Object context, String globalName, boolean onlyExplicit) {
            return env.findExportedSymbol(context, globalName, onlyExplicit);
        }

        @Override
        public Info getLanguageInfo(TruffleLanguage<?> language) {
            return language.env.info;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Info getLegacyLanguageInfo(Class<? extends TruffleLanguage> languageClass) {
            Object vm = AccessAPI.engineAccess().getCurrentVM();
            if (vm == null) {
                return null;
            }
            Env env = AccessAPI.engineAccess().findEnv(vm, languageClass, false);
            if (env != null) {
                return env.info;
            } else {
                return null;
            }
        }

        @Override
        public TruffleLanguage<?> getLanguage(Env env) {
            return env.lang;
        }

        @Override
        public Env getEnv(Info info) {
            return info.env;
        }

        @Override
        public Object languageGlobal(TruffleLanguage.Env env, Object context) {
            return env.getLanguageGlobal(context);
        }

        @Override
        public Object createContext(Env env) {
            return env.lang.createContext(env);
        }

        @Override
        public Object forkContext(Env env, Object context) {
            return env.lang.forkContext(context);
        }

        @Override
        public void dispose(Env env, Object context) {
            env.dispose(context);
        }

        @Override
        public String toStringIfVisible(Env env, Object context, Object value, boolean checkVisibility, boolean resolveContext) {
            return env.toStringIfVisible(resolveContext(env, context, resolveContext), value, checkVisibility);
        }

        @Override
        public Object findMetaObject(Env env, Object context, Object obj, boolean resolveContext) {
            return env.findMetaObject(resolveContext(env, context, resolveContext), obj);
        }

        @Override
        public SourceSection findSourceLocation(Env env, Object context, Object obj, boolean resolveContext) {
            return env.findSourceLocation(resolveContext(env, context, resolveContext), obj);
        }

        private static Object resolveContext(Env env, Object context, boolean resolveContext) {
            if (context == null && resolveContext) {
                TruffleLanguage<Object> lang = env.lang;
                if (lang.env == null) {
                    // legacy mode to get to he current context
                    return lang.findContext(lang.createFindContextNode());
                } else {
                    return lang.getContextReference().get();
                }
            }
            return context;
        }

        @Override
        public Object getLanguageShared(Info info) {
            return info.env.languageShared;
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
    // BEGIN: TruffleLanguageSnippets#forkLanguageContext
    class MyForkingLanguage extends TruffleLanguage<Context> {

        @Override
        protected Context createContext(Env env) {
            return new Context(env);
        }

        @Override
        protected Context forkContext(Context context) {
            // our language needs to support forking
            return context.fork();
        }

        void forkLanguageContext(Env env) {
            Context originalContext = getContextReference().get();
            CallTarget target = env.createFork(
                            Truffle.getRuntime().createCallTarget(
                                            new RootNode(this) {

                final ContextReference<Context> reference = getContextReference();

                @Override
                public Object execute(VirtualFrame frame) {
                    Context forkedContext = reference.get();
                    // we have a forked context to use
                    assert forkedContext != originalContext;
                    return forkedContext;
                }
            }));

            // we can call many times. the fork is only created once.
            target.call();
            target.call();
            target.call();

            // we can dispose the forked context if we know we don't need it
            env.disposeFork(target);
        }
    }
    // END: TruffleLanguageSnippets#forkLanguageContext

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
