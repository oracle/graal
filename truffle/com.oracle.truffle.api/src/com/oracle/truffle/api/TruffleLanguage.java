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

import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.Objects;

import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.impl.FindContextNode;
import com.oracle.truffle.api.impl.ReadOnlyArrayList;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * <p>
 * An entry point for everyone who wants to implement a Truffle-based language. By providing an
 * implementation of this type and registering it using {@link Registration} annotation, your
 * language becomes accessible to users of the {@link com.oracle.truffle.api.vm.PolyglotEngine
 * polyglot execution engine} - all they will need to do is to include your JAR into their
 * application and all the Truffle goodies (multi-language support, multitenant hosting, debugging,
 * etc.) will be made available to them.
 *
 * <p>
 * To ensure that a Truffle language can be used in a language-agnostic way, the implementation
 * should be designed to decouple its configuration and initialization from language specifics as
 * much as possible. One aspect of this is the initialization and start of execution via the
 * {@link com.oracle.truffle.api.vm.PolyglotEngine}, which should be designed in a generic way.
 * Language-specific entry points, for instance to emulate the command-line interface of an existing
 * implementation, should be handled externally.
 *
 * @param <C> internal state of the language associated with every thread that is executing program
 *            {@link #parse(com.oracle.truffle.api.source.Source, com.oracle.truffle.api.nodes.Node, java.lang.String...)
 *            parsed} by the language
 * @since 0.8 or earlier
 */
@SuppressWarnings({"javadoc"})
public abstract class TruffleLanguage<C> {
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
         * Determines if the language needs a special support for interactiveness. When
         * {@link com.oracle.truffle.api.vm.PolyglotEngine#eval} is used over
         * {@link Source#isInteractive() interactive source}, it prints the result to
         * {@link com.oracle.truffle.api.vm.PolyglotEngine.Builder#setOut(OutputStream) standard
         * output} by default. Should a language need a special support, it has to specify
         * <code>true</code> for the <code>interactiveness</code> attribute and then treat
         * {@link Source#isInteractive interactive sources} in its own special way.
         *
         * @return false if the default support for interactive sources is enough
         * @since 0.22
         */
        boolean interactiveness() default false;
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
     * The standard way of accessing the here-in generated context is to create a {@link Node} and
     * insert it into own AST hierarchy - use {@link #createFindContextNode()} to obtain the
     * {@link Node findNode} and later {@link #findContext(com.oracle.truffle.api.nodes.Node)
     * findContext(findNode)} to get back your language context.
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

        @SuppressWarnings("unused")
        ParsingRequest(Object vm, TruffleLanguage<?> language, Source source, Node node, MaterializedFrame frame, String... argumentNames) {
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
         * {@link com.oracle.truffle.api.debug.SuspendedEvent#eval} method, this method provides
         * access to current {@link MaterializedFrame frame} with local variables, etc.
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
     * other language) to support interoperability between languages, {@link String} or one of Java
     * primitive wrappers ( {@link Integer}, {@link Double}, {@link Short}, {@link Boolean}, etc.).
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
     * language) but technically it can be one of Java primitive wrappers ({@link Integer},
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
        return evalInContext(null, source, node, mFrame);
    }

    final Object evalInContext(Object profile, Source source, Node node, MaterializedFrame mFrame) throws IOException {
        ParsingRequest request = new ParsingRequest(profile, this, source, node, mFrame);
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
     * Allows a language implementor to create a node that can effectively lookup up the context
     * associated with current execution. The context is created by
     * {@link #createContext(com.oracle.truffle.api.TruffleLanguage.Env)} method.
     *
     * @return node to be inserted into program to effectively find out current execution context
     *         for this language
     * @since 0.8 or earlier
     */
    protected final Node createFindContextNode() {
        return AccessAPI.engineAccess().createFindContextNode(this);
    }

    /**
     * Uses the {@link #createFindContextNode()} node to obtain the current context. In case you
     * don't care about performance (e.g. your are on a slow execution path), you can chain the
     * calls directly as <code>findContext({@link #createFindContextNode()})</code> and forget the
     * node all together.
     *
     * @param n the node created by this language's {@link #createFindContextNode()}
     * @return the context created by
     *         {@link #createContext(com.oracle.truffle.api.TruffleLanguage.Env)} method at the
     *         beginning of the language execution
     * @throws ClassCastException if the node has not been created by <code>this</code>.
     *             {@link #createFindContextNode()} method.
     * @since 0.8 or earlier
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected final C findContext(Node n) {
        FindContextNode fcn = (FindContextNode) n;
        if (fcn.getTruffleLanguage() != this) {
            throw new ClassCastException();
        }
        return (C) fcn.executeFindContext();
    }

    private static final class LangCtx<C> {
        final TruffleLanguage<C> lang;
        final C ctx;

        LangCtx(TruffleLanguage<C> lang, Env env) {
            this.lang = lang;
            this.ctx = lang.createContext(env);
        }

        Object findExportedSymbol(String globalName, boolean onlyExplicit) {
            return lang.findExportedSymbol(ctx, globalName, onlyExplicit);
        }

        Object getLanguageGlobal() {
            return lang.getLanguageGlobal(ctx);
        }

        void dispose() {
            lang.disposeContext(ctx);
        }

        String toString(TruffleLanguage<?> language, Object obj) {
            assert lang == language;
            return lang.toString(ctx, obj);
        }

        private Object findMetaObject(TruffleLanguage<?> language, Object obj) {
            assert lang == language;
            return lang.findMetaObject(ctx, obj);
        }

        private SourceSection findSourceLocation(TruffleLanguage<?> language, Object obj) {
            assert lang == language;
            return lang.findSourceLocation(ctx, obj);
        }

        void postInit() {
            try {
                lang.initializeContext(ctx);
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
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
        private final Object vm;
        private final TruffleLanguage<?> lang;
        private final LangCtx<?> langCtx;
        private final InputStream in;
        private final OutputStream err;
        private final OutputStream out;
        private final List<Object> services;
        private final Map<String, Object> config;

        Env(Object vm, TruffleLanguage<?> lang, OutputStream out, OutputStream err, InputStream in, Map<String, Object> config) {
            this.vm = vm;
            this.in = in;
            this.err = err;
            this.out = out;
            this.lang = lang;
            LinkedHashSet<Object> collectedServices = new LinkedHashSet<>();
            AccessAPI.instrumentAccess().collectEnvServices(collectedServices, vm, lang, this);
            this.services = new ArrayList<>(collectedServices);
            this.config = config;
            this.langCtx = new LangCtx<>(lang, this);
        }

        /**
         * Asks the environment to go through other registered languages and find whether they
         * export global symbol of specified name. The expected return type is either
         * <code>TruffleObject</code>, or one of wrappers of Java primitive types ({@link Integer},
         * {@link Double}).
         *
         * @param globalName the name of the symbol to search for
         * @return object representing the symbol or <code>null</code>
         * @since 0.8 or earlier
         */
        public Object importSymbol(String globalName) {
            return AccessAPI.engineAccess().importSymbol(vm, lang, globalName);
        }

        /**
         * Allows it to be determined if this {@link com.oracle.truffle.api.vm.PolyglotEngine} can
         * execute code written in a language with a given MIME type.
         *
         * @see Source#withMimeType(String)
         * @see #parse(Source, String...)
         *
         * @return a boolean that indicates if the MIME type is supported
         * @since 0.11
         */
        public boolean isMimeTypeSupported(String mimeType) {
            return AccessAPI.engineAccess().isMimeTypeSupported(vm, mimeType);
        }

        /**
         * Evaluates source of (potentially different) language. The {@link Source#getMimeType()
         * MIME type} is used to identify the {@link TruffleLanguage} to use to perform the
         * {@link #parse(com.oracle.truffle.api.source.Source, com.oracle.truffle.api.nodes.Node, java.lang.String...)}
         * . The names of arguments are parameters for the resulting {#link CallTarget} that allow
         * the <code>source</code> to reference the actual parameters passed to
         * {@link CallTarget#call(java.lang.Object...)}.
         *
         * @param source the source to evaluate
         * @param argumentNames the names of {@link CallTarget#call(java.lang.Object...)} arguments
         *            that can be referenced from the source
         * @return the call target representing the parsed result
         * @since 0.8 or earlier
         */
        public CallTarget parse(Source source, String... argumentNames) {
            TruffleLanguage<?> language = AccessAPI.engineAccess().findLanguageImpl(vm, null, source.getMimeType());
            return parseForLanguage(vm, language, source, argumentNames);
        }

        private static <C> CallTarget parseForLanguage(Object profile, TruffleLanguage<C> language, Source source, String... argumentNames) {
            ParsingRequest env = new ParsingRequest(profile, language, source, null, null, argumentNames);
            CallTarget target;
            try {
                target = env.parse(language);
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            } finally {
                env.dispose();
            }
            return target;
        }

        /**
         * Input associated with {@link com.oracle.truffle.api.vm.PolyglotEngine} this language is
         * being executed in.
         *
         * @return reader, never <code>null</code>
         * @since 0.8 or earlier
         */
        public InputStream in() {
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
            return config;
        }

        void postInit() {
            langCtx.postInit();
        }
    }

    static final AccessAPI API = new AccessAPI();

    private static final class AccessAPI extends Accessor {
        static Nodes nodesAccess() {
            return API.nodes();
        }

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
    }

    static final class LanguageImpl extends Accessor.LanguageSupport {
        @Override
        public Env attachEnv(Object vm, TruffleLanguage<?> language, OutputStream stdOut, OutputStream stdErr, InputStream stdIn, Map<String, Object> config) {
            Env env = new Env(vm, language, stdOut, stdErr, stdIn, config);
            return env;
        }

        @Override
        public void postInitEnv(Env env) {
            env.postInit();
        }

        @Override
        public CallTarget parse(TruffleLanguage<?> truffleLanguage, Source code, Node context, String... argumentNames) {
            try {
                return truffleLanguage.parse(code, context, argumentNames);
            } catch (UnsupportedOperationException ex) {
                return parseForLanguage(null, truffleLanguage, code, context, argumentNames);
            } catch (Exception ex) {
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                }
                throw new RuntimeException(ex);
            }
        }

        private static <C> CallTarget parseForLanguage(Object profile, TruffleLanguage<C> truffleLanguage, Source code, Node context, String... argumentNames) {
            ParsingRequest env = new ParsingRequest(profile, truffleLanguage, code, context, null, argumentNames);
            CallTarget target;
            try {
                target = env.parse(truffleLanguage);
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            env.dispose();
            return target;
        }

        @Override
        @SuppressWarnings({"rawtypes"})
        public Object evalInContext(Object sourceVM, String code, Node node, final MaterializedFrame mFrame) {
            RootNode rootNode = node.getRootNode();
            Class<? extends TruffleLanguage> languageType = AccessAPI.nodesAccess().findLanguage(rootNode);
            final Env env = AccessAPI.engineAccess().findEnv(sourceVM, languageType);
            final TruffleLanguage<?> lang = findLanguage(env);
            final Source source = Source.newBuilder(code).name("eval in context").mimeType("content/unknown").build();
            CallTarget target = parseForLanguage(sourceVM, lang, source, node);

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
            return env.langCtx.findExportedSymbol(globalName, onlyExplicit);
        }

        @Override
        public TruffleLanguage<?> findLanguage(Env env) {
            return env.lang;
        }

        @Override
        public Object languageGlobal(TruffleLanguage.Env env) {
            return env.langCtx.getLanguageGlobal();
        }

        @Override
        public Object findContext(Env env) {
            return env.langCtx.ctx;
        }

        @Override
        public void dispose(TruffleLanguage<?> impl, Env env) {
            assert impl == env.langCtx.lang;
            env.langCtx.dispose();
        }

        @Override
        public String toString(TruffleLanguage<?> language, Env env, Object obj) {
            return env.langCtx.toString(language, obj);
        }

        @Override
        public Object findMetaObject(TruffleLanguage<?> language, Env env, Object obj) {
            return env.langCtx.findMetaObject(language, obj);
        }

        @Override
        public SourceSection findSourceLocation(TruffleLanguage<?> language, Env env, Object obj) {
            return env.langCtx.findSourceLocation(language, obj);
        }

        @Override
        public Object getVM(Env env) {
            return env.vm;
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
