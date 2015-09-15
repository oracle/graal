/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.io.Reader;
import java.io.Writer;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.oracle.truffle.api.debug.DebugSupportProvider;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.impl.FindContextNode;
import com.oracle.truffle.api.instrument.ASTProber;
import com.oracle.truffle.api.instrument.AdvancedInstrumentResultListener;
import com.oracle.truffle.api.instrument.AdvancedInstrumentRoot;
import com.oracle.truffle.api.instrument.AdvancedInstrumentRootFactory;
import com.oracle.truffle.api.instrument.Instrument;
import com.oracle.truffle.api.instrument.ToolSupportProvider;
import com.oracle.truffle.api.instrument.Visualizer;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;

/**
 * An entry point for everyone who wants to implement a Truffle based language. By providing an
 * implementation of this type and registering it using {@link Registration} annotation, your
 * language becomes accessible to users of the {@link com.oracle.truffle.api.vm.TruffleVM Truffle
 * virtual machine} - all they will need to do is to include your JAR into their application and all
 * the Truffle goodies (multi-language support, multitenant hosting, debugging, etc.) will be made
 * available to them.
 *
 * @param <C> internal state of the language associated with every thread that is executing program
 *            {@link #parse(com.oracle.truffle.api.source.Source, com.oracle.truffle.api.nodes.Node, java.lang.String...)
 *            parsed} by the language
 */
@SuppressWarnings("javadoc")
public abstract class TruffleLanguage<C> {
    /**
     * Constructor to be called by subclasses.
     */
    protected TruffleLanguage() {
    }

    /**
     * The annotation to use to register your language to the
     * {@link com.oracle.truffle.api.vm.TruffleVM Truffle} system. By annotating your implementation
     * of {@link TruffleLanguage} by this annotation you are just a
     * <em>one JAR drop to the class path</em> away from your users. Once they include your JAR in
     * their application, your language will be available to the
     * {@link com.oracle.truffle.api.vm.TruffleVM Truffle virtual machine}.
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    public @interface Registration {
        /**
         * Unique name of your language. This name will be exposed to users via the
         * {@link com.oracle.truffle.api.vm.TruffleVM.Language#getName()} getter.
         *
         * @return identifier of your language
         */
        String name();

        /**
         * Unique string identifying the language version. This name will be exposed to users via
         * the {@link com.oracle.truffle.api.vm.TruffleVM.Language#getVersion()} getter.
         *
         * @return version of your language
         */
        String version();

        /**
         * List of MIME types associated with your language. Users will use them (directly or
         * indirectly) when
         * {@link com.oracle.truffle.api.vm.TruffleVM#eval(com.oracle.truffle.api.source.Source)
         * executing} their code snippets or their {@link Source files}.
         *
         * @return array of MIME types assigned to your language files
         */
        String[] mimeType();
    }

    /**
     * Creates internal representation of the executing context suitable for given environment. Each
     * time the {@link TruffleLanguage language} is used by a new
     * {@link com.oracle.truffle.api.vm.TruffleVM} or in a new thread, the system calls this method
     * to let the {@link TruffleLanguage language} prepare for <em>execution</em>. The returned
     * execution context is completely language specific; it is however expected it will contain
     * reference to here-in provided <code>env</code> and adjust itself according to parameters
     * provided by the <code>env</code> object.
     *
     * @param env the environment the language is supposed to operate in
     * @return internal data of the language in given environment
     */
    protected abstract C createContext(Env env);

    /**
     * Parses the provided source and generates appropriate AST. The parsing should execute no user
     * code, it should only create the {@link Node} tree to represent the source. The parsing may be
     * performed in a context (specified as another {@link Node}) or without context. The
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
     * @throws IOException thrown when I/O or parsing goes wrong. Here-in thrown exception is
     *             propagate to the user who called one of <code>eval</code> methods of
     *             {@link com.oracle.truffle.api.vm.TruffleVM}
     */
    protected abstract CallTarget parse(Source code, Node context, String... argumentNames) throws IOException;

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
     * @param globalName the name of the global symbol to find
     * @param onlyExplicit should the language seek for implicitly exported object or only consider
     *            the explicitly exported ones?
     * @return an exported object or <code>null</code>, if the symbol does not represent anything
     *         meaningful in this language
     */
    protected abstract Object findExportedSymbol(C context, String globalName, boolean onlyExplicit);

    /**
     * Returns global object for the language.
     * <p>
     * The object is expected to be <code>TruffleObject</code> (e.g. a native object from the other
     * language) but technically it can be one of Java primitive wrappers ({@link Integer},
     * {@link Double}, {@link Short}, etc.).
     *
     * @return the global object or <code>null</code> if the language does not support such concept
     */
    protected abstract Object getLanguageGlobal(C context);

    /**
     * Checks whether the object is provided by this language.
     *
     * @param object the object to check
     * @return <code>true</code> if this language can deal with such object in native way
     */
    protected abstract boolean isObjectOfLanguage(Object object);

    @Deprecated
    protected abstract ToolSupportProvider getToolSupport();

    @Deprecated
    protected abstract DebugSupportProvider getDebugSupport();

    /**
     * Gets visualization services for language-specific information.
     */
    protected abstract Visualizer getVisualizer();

    /**
     * Enables AST probing on all subsequently created ASTs (sources parsed).
     *
     * @param astProber optional AST prober to enable; the default for the language used if
     *            {@code null}
     */
    @Deprecated
    protected abstract void enableASTProbing(ASTProber astProber);

    /**
     * Gets the current specification for AST instrumentation for the language; <em>empty</em> if
     * none.
     */
    protected abstract List<ASTProber> getASTProbers();

    /**
     * Runs source code in a halted execution context, or at top level.
     *
     * @param source the code to run
     * @param node node where execution halted, {@code null} if no execution context
     * @param mFrame frame where execution halted, {@code null} if no execution context
     * @return result of running the code in the context, or at top level if no execution context.
     * @throws IOException if the evaluation cannot be performed
     */
    protected abstract Object evalInContext(Source source, Node node, MaterializedFrame mFrame) throws IOException;

    /**
     * Creates a language-specific factory to produce instances of {@link AdvancedInstrumentRoot}
     * that, when executed, computes the result of a textual expression in the language; used to
     * create an
     * {@linkplain Instrument#create(AdvancedInstrumentResultListener, AdvancedInstrumentRootFactory, Class, String)
     * Advanced Instrument}.
     *
     * @param expr a guest language expression
     * @param resultListener optional listener for the result of each evaluation.
     * @return a new factory
     * @throws IOException if the factory cannot be created, for example if the expression is badly
     *             formed.
     */
    protected abstract AdvancedInstrumentRootFactory createAdvancedInstrumentRootFactory(String expr, AdvancedInstrumentResultListener resultListener) throws IOException;

    /**
     * Allows a language implementor to create a node that can effectively lookup up the context
     * associated with current execution. The context is created by
     * {@link #createContext(com.oracle.truffle.api.TruffleLanguage.Env)} method.
     *
     * @return node to be inserted into program to effectively find out current execution context
     *         for this language
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected final Node createFindContextNode() {
        final Class<? extends TruffleLanguage<?>> c = (Class<? extends TruffleLanguage<?>>) getClass();
        return new FindContextNode(c);
    }

    /**
     * Uses the {@link #createFindContextNode()} node to obtain the current context.
     *
     * @param n the node created by this language's {@link #createFindContextNode()}
     * @return the context created by
     *         {@link #createContext(com.oracle.truffle.api.TruffleLanguage.Env)} method at the
     *         beginning of the language execution
     * @throws ClassCastException if the node has not been created by <code>this</code>.
     *             {@link #createFindContextNode()} method.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected final C findContext(Node n) {
        FindContextNode fcn = (FindContextNode) n;
        if (fcn.getLanguageClass() != getClass()) {
            throw new ClassCastException();
        }
        return (C) fcn.executeFindContext();
    }

    private static final class LangCtx<C> {
        final TruffleLanguage<C> lang;
        final C ctx;

        public LangCtx(TruffleLanguage<C> lang, Env env) {
            this.lang = lang;
            // following call verifies that Accessor.CURRENT_VM is provided
            assert API.findLanguage(null, null) == null;
            this.ctx = lang.createContext(env);
        }

        Object findExportedSymbol(String globalName, boolean onlyExplicit) {
            return lang.findExportedSymbol(ctx, globalName, onlyExplicit);
        }

        Object getLanguageGlobal() {
            return lang.getLanguageGlobal(ctx);
        }
    }

    /**
     * Represents execution environment of the {@link TruffleLanguage}. Each active
     * {@link TruffleLanguage} receives instance of the environment before any code is executed upon
     * it. The environment has knowledge of all active languages and can exchange symbols between
     * them.
     */
    public static final class Env {
        private final Object vm;
        private final TruffleLanguage<?> lang;
        private final LangCtx<?> langCtx;
        private final Reader in;
        private final Writer err;
        private final Writer out;

        Env(Object vm, TruffleLanguage<?> lang, Writer out, Writer err, Reader in) {
            this.vm = vm;
            this.in = in;
            this.err = err;
            this.out = out;
            this.lang = lang;
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
         */
        public Object importSymbol(String globalName) {
            return API.importSymbol(vm, lang, globalName);
        }

        /**
         * Input associated with {@link com.oracle.truffle.api.vm.TruffleVM} this language is being
         * executed in.
         *
         * @return reader, never <code>null</code>
         */
        public Reader stdIn() {
            return in;
        }

        /**
         * Standard output writer for {@link com.oracle.truffle.api.vm.TruffleVM} this language is
         * being executed in.
         *
         * @return writer, never <code>null</code>
         */
        public Writer stdOut() {
            return out;
        }

        /**
         * Standard error writer for {@link com.oracle.truffle.api.vm.TruffleVM} this language is
         * being executed in.
         *
         * @return writer, never <code>null</code>
         */
        public Writer stdErr() {
            return err;
        }
    }

    private static final AccessAPI API = new AccessAPI();

    @SuppressWarnings("rawtypes")
    private static final class AccessAPI extends Accessor {
        @Override
        protected Env attachEnv(Object vm, TruffleLanguage<?> language, Writer stdOut, Writer stdErr, Reader stdIn) {
            Env env = new Env(vm, language, stdOut, stdErr, stdIn);
            return env;
        }

        @Override
        protected Object importSymbol(Object vm, TruffleLanguage<?> queryingLang, String globalName) {
            return super.importSymbol(vm, queryingLang, globalName);
        }

        private static final Map<Source, CallTarget> COMPILED = Collections.synchronizedMap(new WeakHashMap<Source, CallTarget>());

        @Override
        protected Object eval(TruffleLanguage<?> language, Source source) throws IOException {
            CallTarget target = COMPILED.get(source);
            if (target == null) {
                target = language.parse(source, null);
                if (target == null) {
                    throw new IOException("Parsing has not produced a CallTarget for " + source);
                }
                COMPILED.put(source, target);
            }
            try {
                return target.call();
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }

        @Override
        protected AdvancedInstrumentRootFactory createAdvancedInstrumentRootFactory(Object vm, Class<? extends TruffleLanguage> languageClass, String expr,
                        AdvancedInstrumentResultListener resultListener) throws IOException {

            final TruffleLanguage language = findLanguageImpl(vm, languageClass);
            return language.createAdvancedInstrumentRootFactory(expr, resultListener);
        }

        @Override
        protected Object findExportedSymbol(TruffleLanguage.Env env, String globalName, boolean onlyExplicit) {
            return env.langCtx.findExportedSymbol(globalName, onlyExplicit);
        }

        @Override
        protected Env findLanguage(Object vm, Class<? extends TruffleLanguage> languageClass) {
            return super.findLanguage(vm, languageClass);
        }

        @Override
        protected TruffleLanguage<?> findLanguage(Env env) {
            return env.lang;
        }

        @Override
        protected Object languageGlobal(TruffleLanguage.Env env) {
            return env.langCtx.getLanguageGlobal();
        }

        @Override
        protected Object findContext(Env env) {
            return env.langCtx.ctx;
        }

        @Override
        protected List<ASTProber> getASTProbers(Object vm, Class<? extends TruffleLanguage> languageClass) {
            TruffleLanguage impl = findLanguageImpl(vm, languageClass);
            return impl.getASTProbers();
        }

        @SuppressWarnings("deprecation")
        @Override
        protected ToolSupportProvider getToolSupport(TruffleLanguage<?> l) {
            return l.getToolSupport();
        }

        @SuppressWarnings("deprecation")
        @Override
        protected DebugSupportProvider getDebugSupport(TruffleLanguage<?> l) {
            return l.getDebugSupport();
        }
    }

}
