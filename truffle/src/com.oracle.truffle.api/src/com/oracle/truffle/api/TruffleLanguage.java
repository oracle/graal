/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.nio.file.FileSystemNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.FileSystem;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile.FileAdapter;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleStackTrace.LazyStackTrace;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.impl.ReadOnlyArrayList;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * A Truffle language implementation contains all the services a language should provide to make it
 * composable with other languages. Implementation classes must be annotated with
 * {@link Registration} in order to be discoverable by the {@linkplain org.graalvm.polyglot Polyglot
 * API}.
 *
 * {@link TruffleLanguage} subclasses must provide a public default constructor.
 *
 * <h4>Lifecycle</h4>
 *
 * A language implementation becomes available for use by an engine when metadata is added using the
 * {@link Registration} annotation and the implementation's JAR file placed on the host Java Virtual
 * Machine's class path.
 * <p>
 * A newly created engine locates all available language implementations and creates a
 * {@linkplain org.graalvm.polyglot.Language descriptor} for each. The descriptor holds the
 * language's registered metadata, but its execution environment is not initialized until the
 * language is needed for code execution. That execution environment remains initialized for the
 * lifetime of the engine and is isolated from the environment in any other engine instance.
 * <p>
 * Language global state can be shared between multiple context instances by saving them in a custom
 * field of the {@link TruffleLanguage} subclass. Languages may control sharing between multiple
 * contexts using its {@link Registration#contextPolicy() context policy}. By default the context
 * policy is {@link ContextPolicy#EXCLUSIVE exclusive}: each context has its own separate
 * TruffleLanguage instance.
 * <p>
 * If the context policy is more permissive then the implementation needs to manually ensure data
 * isolation between the contexts. This means that state associated with a context must not be
 * stored in a TruffleLanguage subclass. ASTs and assumptions can be shared across multiple contexts
 * if modifying them does not affect language semantics. Languages are strongly discouraged from
 * using static mutable state in their languages. Instead {@link TruffleLanguage} instances should
 * be used instead to store global state and their sharing should be configured using
 * {@link Registration#contextPolicy() context policy}.
 * <p>
 * Whenever an engine is disposed then each initialized language context will be
 * {@link #disposeContext(Object) disposed}.
 *
 *
 * <h4>Context Policy</h4>
 *
 * The number of {@link TruffleLanguage} instances per polyglot {@link org.graalvm.polyglot.Context
 * context} is configured by the {@link Registration#contextPolicy() context policy}. By default an
 * {@link ContextPolicy#EXCLUSIVE exclusive} {@link TruffleLanguage language} instance is created
 * for every {@link org.graalvm.polyglot.Context polyglot context} or
 * {@link TruffleLanguage.Env#newContextBuilder() inner context}. With policy
 * {@link ContextPolicy#REUSE reuse}, language instances will be reused after a language context was
 * {@link TruffleLanguage#disposeContext(Object) disposed}. With policy {@link ContextPolicy#SHARED
 * shared}, a language will also be reused if active contexts are not yet disposed. Language
 * instances will only be shared or reused if they are
 * {@link TruffleLanguage#areOptionsCompatible(OptionValues, OptionValues) compatible}. Language
 * implementations are encouraged to support the most permissive context policy possible. Please see
 * the individual {@link ContextPolicy policies} for details on the implications on the language
 * implementation.
 * <p>
 * The following illustration shows the cardinalities of the individual components:
 *
 * <pre>
 *  N: unbounded
 *  P: N for exclusive, 1 for shared context policy
 *  L: number of installed languages
 *  I: number of installed instruments
 *
 *  - 1 : Host VM Processs
 *   - N : {@linkplain org.graalvm.polyglot.Engine Engine}
 *     - N : {@linkplain org.graalvm.polyglot.Context Context}
 *       - L : Language Context
 *     - P * L : {@link TruffleLanguage TruffleLanguage}
 *     - I : {@linkplain org.graalvm.polyglot.Instrument Instrument}
 *       - 1 : {@link com.oracle.truffle.api.instrumentation.TruffleInstrument TruffleInstrument}
 * </pre>
 *
 * <h4>Parse Caching</h4>
 *
 * The result of the {@link #parse(ParsingRequest) parsing request} is cached per language instance,
 * {@link ParsingRequest#getSource() source}, {@link ParsingRequest#getArgumentNames() argument
 * names} and environment {@link Env#getOptions() options}. The scope of the caching is influenced
 * by the {@link Registration#contextPolicy() context policy}. Caching may be
 * {@link Source#isCached() disabled} for certain sources. It is enabled for new sources by default.
 *
 * <h4>Language Configuration</h4>
 *
 * On {@link #createContext(Env) context creation} each language context is provided with
 * information about the environment {@link Env environment }. Language can optionally declare
 * {@link org.graalvm.polyglot.Context.Builder#option(String, String) configurable} options in
 * {@link #getOptionDescriptors()}.
 *
 * <h4>Polyglot Bindings</h4>
 *
 * Language implementations communicate with one another (and with instrumentation-based tools such
 * as debuggers) by reading/writing named values into the {@link Env#getPolyglotBindings() polyglot
 * bindings}. This bindings object is used to implement guest language export/import statements used
 * for <em>language interoperation</em>.
 * <p>
 * A language implementation can also {@linkplain Env#importSymbol(String) import} or
 * {@linkplain Env#exportSymbol(String, Object) export} a global symbol by name. The scope may be
 * accessed from multiple threads at the same time. Existing keys are overwritten.
 *
 * <h4>Configuration vs. Initialization</h4>
 *
 * To ensure that a Truffle language can be used in a language-agnostic way, the implementation
 * should be designed to decouple its configuration and initialization from language specifics as
 * much as possible. One aspect of this is the initialization and start of execution via the
 * {@link org.graalvm.polyglot.Context}, which should be designed in a generic way.
 * Language-specific entry points, for instance to emulate the command-line interface of an existing
 * implementation, should be handled externally.
 *
 * <h4>Multi-threading</h4>
 *
 * There are two kinds of threads that access contexts of Truffle guest languages:
 * <ul>
 * <li>Internal threads are {@link Env#createThread(Runnable) created} and managed by a language for
 * a context. All internally created threads need to be stopped when the context is
 * {@link #disposeContext(Object) disposed}.
 * <li>External threads are created and managed by the host application / language launcher. The
 * host application is allowed to use language contexts from changing threads, sequentially or at
 * the same time if the language {@link #isThreadAccessAllowed(Thread, boolean) allows} it.
 * </ul>
 * <p>
 * By default every {@link #createContext(Env) context} only allows access from one thread at the
 * same time. Therefore if the context is tried to be accessed from multiple threads at the same
 * time the access will fail. Languages that want to allow multi-threaded access to a context may
 * override {@link #isThreadAccessAllowed(Thread, boolean)} and return <code>true</code> also for
 * multi-threaded accesses. Initialization actions for multi-threaded access can be performed by
 * overriding {@link #initializeMultiThreading(Object)}. Threads are
 * {@link #initializeThread(Object, Thread) initialized} and {@link #disposeContext(Object)
 * disposed} before and after use with a context. Languages may {@link Env#createThread(Runnable)
 * create} new threads if the environment {@link Env#isCreateThreadAllowed() allows} it.
 *
 * @param <C> internal state of the language associated with every thread that is executing program
 *            {@link #parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest) parsed} by the
 *            language
 * @see org.graalvm.polyglot.Context for embedding of Truffle languages in Java host applications.
 * @since 0.8 or earlier
 */
@SuppressWarnings({"javadoc"})
public abstract class TruffleLanguage<C> {

    // get and isFinal are frequent operations -> cache the engine access call
    @CompilationFinal private LanguageInfo languageInfo;
    @CompilationFinal private ContextReference<C> reference;

    /**
     * Constructor to be called by subclasses.
     *
     * @since 0.8 or earlier
     */
    protected TruffleLanguage() {
    }

    /**
     * The annotation to use to register your language to the {@link org.graalvm.polyglot Polyglot
     * API}. By annotating your implementation of {@link TruffleLanguage} by this annotation the
     * language can be discovered on the class path.
     *
     * @since 0.8 or earlier
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Registration {

        /**
         * Unique id of your language. This id will be exposed to users via the getter. It is used
         * as group identifier for options of the language.
         *
         * @return identifier of your language
         * @since 0.8 or earlier
         */
        String id() default "";

        /**
         * Unique name of your language. This name will be exposed to users via the
         * {@link org.graalvm.polyglot.Language#getName()} getter.
         *
         * @return identifier of your language
         * @since 0.8 or earlier
         */
        String name();

        /**
         * Unique name of your language implementation.
         *
         * @return the implementation name of your language
         * @since 0.8 or earlier
         */
        String implementationName() default "";

        /**
         * Unique string identifying the language version. This name will be exposed to users via
         * the {@link org.graalvm.polyglot.Language#getVersion()} getter. It inherits from
         * {@link org.graalvm.polyglot.Engine#getVersion()} by default.
         *
         * @return version of your language
         * @since 0.8 or earlier
         */
        String version() default "inherit";

        /**
         * @since 0.8 or earlier
         * @deprecated split up MIME types into {@link #characterMimeTypes() character} and
         *             {@link #byteMimeTypes() byte} based MIME types.
         */
        @Deprecated
        String[] mimeType() default {};

        /**
         * Returns the default MIME type of this language. The default MIME type allows embedders
         * and other language or instruments to find out how content is interpreted if no MIME type
         * was specified. The default MIME type must be specified in the list of supported
         * {@link #characterMimeTypes() character} or {@link #byteMimeTypes() byte} based MIME
         * types.
         * <p>
         * The default MIME type is mandatory if more than one supported MIME type was specified. If
         * no default MIME type and no supported MIME types were specified then all sources for this
         * language will be interpreted as {@link Source#hasCharacters() character} based sources.
         *
         * @see LanguageInfo#getDefaultMimeType()
         * @see Language#getDefaultMimeType()
         * @see #characterMimeTypes()
         * @see #byteMimeTypes()
         * @since 1.0
         */
        String defaultMimeType() default "";

        /**
         * List of MIME types supported by this language which sources should be interpreted as
         * {@link Source#hasCharacters() character} based sources. Languages may use MIME types to
         * differentiate supported source kinds. If a MIME type is declared as supported then the
         * language needs to be able to {@link TruffleLanguage#parse(ParsingRequest) parse} sources
         * of this kind. If only one supported MIME type was specified by a language then it will be
         * used as {@link #defaultMimeType() default} MIME type. If no supported character and byte
         * based MIME types are specified then all sources will be interpreted as
         * {@link Source#hasCharacters() character} based.
         *
         * @return array of MIME types assigned to your language files
         * @see #defaultMimeType()
         * @see #byteMimeTypes()
         * @since 1.0
         */
        String[] characterMimeTypes() default {};

        /**
         * List of MIME types supported by this language which sources should be interpreted as
         * {@link Source#hasBytes() byte} based sources. Languages may use MIME types to
         * differentiate supported source kinds. If a MIME type is declared as supported then the
         * language needs to be able to {@link TruffleLanguage#parse(ParsingRequest) parse} sources
         * of this kind. If only one supported MIME type was specified by a language then it will be
         * used as {@link #defaultMimeType() default} MIME type. If no supported character and byte
         * based MIME types are specified then all sources will be interpreted as
         * {@link Source#hasCharacters() character} based.
         *
         * @return array of MIME types assigned to your language files
         * @see #defaultMimeType()
         * @see #characterMimeTypes()
         * @since 1.0
         */
        String[] byteMimeTypes() default {};

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
         * Returns <code>true</code> if this language is intended for internal use only. Internal
         * languages cannot be used in the host environment directly, they can only be used from
         * other languages or from instruments.
         *
         * @since 0.27
         */
        boolean internal() default false;

        /**
         * Specifies a list of languages that this language depends on. Languages are referenced
         * using their {@link #id()}. This has the following effects:
         * <ul>
         * <li>This language always has access to dependent languages if this language is
         * accessible. Languages may not be accessible if language access is
         * {@link org.graalvm.polyglot.Context#create(String...) restricted}.
         * <li>This language is finalized before dependent language contexts are
         * {@link TruffleLanguage#finalizeContext(Object) finalized}.
         * <li>This language is disposed before dependent language contexts are
         * {@link TruffleLanguage#disposeContext(Object) disposed}.
         * </ul>
         * <p>
         * {@link #internal() Non-internal} languages implicitly depend on all internal languages.
         * Therefore by default non-internal languages are disposed and finalized before internal
         * languages.
         * <p>
         * Dependent languages references are optional. If a dependent language is not installed and
         * the language needs to fail in such a case then the language should fail on
         * {@link TruffleLanguage#initializeContext(Object) context initialization}. Cycles in
         * dependencies will cause an {@link IllegalStateException} when one of the cyclic languages
         * is {@link org.graalvm.polyglot.Context#initialize(String) initialized}.
         *
         * @since 0.30
         */
        String[] dependentLanguages() default {
        };

        /**
         * Defines the supported policy for reusing {@link TruffleLanguage languages} per context.
         * I.e. the policy specifies the degree of sharing that is allowed between multiple language
         * contexts. The default policy is {@link ContextPolicy#EXCLUSIVE exclusive}. Every language
         * is encouraged to try to support a context policy that is as permissive as possible, where
         * {@link ContextPolicy#EXCLUSIVE exclusive} is the least and {@link ContextPolicy#SHARED
         * shared} is the most permissive policy. {@link TruffleLanguage#parse(ParsingRequest) Parse
         * caching} is scoped per {@link TruffleLanguage language} instance, therefore the context
         * policy influences its behavior.
         * <p>
         * The context policy applies to contexts that were created using the
         * {@link org.graalvm.polyglot.Context polyglot API} as well as for {@link TruffleContext
         * inner contexts}. The context policy does not apply to nodes that were created using the
         * Truffle interop protocol. Therefore, interop message nodes always need to be prepared to
         * be used with policy {@link ContextPolicy#SHARED}.
         *
         * @see TruffleLanguage#parse(ParsingRequest)
         * @since 1.0
         */
        ContextPolicy contextPolicy() default ContextPolicy.EXCLUSIVE;
    }

    /**
     * Returns <code>true</code> if the combination of two sets of options allow to
     * {@link ContextPolicy#SHARED share} or {@link ContextPolicy#REUSE reuse} the same language
     * instance, else <code>false</code>. If options are incompatible then a new language instance
     * will be created for a new context. The first language context {@link #createContext(Env)
     * created} for a {@link TruffleLanguage} instance always has compatible options, therefore
     * {@link #areOptionsCompatible(OptionValues, OptionValues)} will not be invoked for it. The
     * default implementation returns <code>true</code>.
     * <p>
     * If the context policy of a language is set to {@link ContextPolicy#EXCLUSIVE exclusive}
     * (default behavior) then {@link #areOptionsCompatible(OptionValues, OptionValues)} will never
     * be invoked as {@link TruffleLanguage} instances will not be shared for multiple contexts. For
     * the other context policies {@link ContextPolicy#REUSE reuse} and {@link ContextPolicy#SHARED
     * shared} this method can be used to further restrict the reuse of language instances.
     * Compatibility influences {@link #parse(ParsingRequest) parse caching} because it uses the
     * {@link TruffleLanguage language} instance as a key.
     * <p>
     * Example usage of areOptionsCompatible if sharing of the language instances and parse caching
     * should be restricted by the script version option:
     *
     * {@link TruffleLanguageSnippets.CompatibleLanguage#areOptionsCompatible}
     *
     * @param firstOptions the options used to create the first context, never <code>null</code>
     * @param newOptions the options that will be used for the new context, never <code>null</code>
     * @see ContextPolicy
     * @see #parse(ParsingRequest)
     * @since 1.0
     */
    protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
        return true;
    }

    /**
     * Creates internal representation of the executing context suitable for given environment. Each
     * time the {@link TruffleLanguage language} is used by a new
     * {@link org.graalvm.polyglot.Context}, the system calls this method to let the
     * {@link TruffleLanguage language} prepare for <em>execution</em>. The returned execution
     * context is completely language specific; it is however expected it will contain reference to
     * here-in provided <code>env</code> and adjust itself according to parameters provided by the
     * <code>env</code> object.
     * <p>
     * The context created by this method is accessible using {@link #getContextReference()}. An
     * {@link IllegalStateException} is thrown if the context is tried to be accessed while the
     * createContext method is executed.
     * <p>
     * This method shouldn't perform any complex operations. The runtime system is just being
     * initialized and for example making
     * {@link Env#parse(com.oracle.truffle.api.source.Source, java.lang.String...) calls into other
     * languages} and assuming your language is already initialized and others can see it would be
     * wrong - until you return from this method, the initialization isn't over. The same is true
     * for instrumentation, the instruments cannot receive any meta data about code executed during
     * context creation. Should there be a need to perform complex initialization, do it by
     * overriding the {@link #initializeContext(java.lang.Object)} method.
     * <p>
     * May return {@code null} if the language does not need any per-{@linkplain Context context}
     * state. Otherwise it should return a new object instance every time it is called.
     *
     * @param env the environment the language is supposed to operate in
     * @return internal data of the language in given environment or {@code null}
     * @since 0.8 or earlier
     */
    protected abstract C createContext(Env env);

    /**
     * Perform any complex initialization. The
     * {@link #createContext(com.oracle.truffle.api.TruffleLanguage.Env) } factory method shouldn't
     * do any complex operations. Just create the instance of the context, let the runtime system
     * register it properly. Should there be a need to perform complex initialization, override this
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
     * Performs language context finalization actions that are necessary before language contexts
     * are {@link #disposeContext(Object) disposed}. All installed languages must remain usable
     * after finalization. The finalization order can be influenced by specifying
     * {@link Registration#dependentLanguages() language dependencies}. By default internal
     * languages are finalized last, otherwise the default order is unspecified but deterministic.
     * <p>
     * While finalization code is run, other language contexts may become initialized. In such a
     * case, the finalization order may be non-deterministic and/or not respect the order specified
     * by language dependencies.
     *
     * @see Registration#dependentLanguages() for specifying language dependencies.
     * @param context the context created by
     *            {@link #createContext(com.oracle.truffle.api.TruffleLanguage.Env)}
     * @since 0.30
     */
    protected void finalizeContext(C context) {
    }

    /**
     * @since 1.0
     * @deprecated in 1.0. Got renamed to {@link #initializeMultipleContexts()} instead. Instead of
     *             returning a boolean configure {@link Registration#contextPolicy() context policy}
     *             .
     */
    @Deprecated
    protected boolean initializeMultiContext() {
        return false;
    }

    /**
     * Initializes this language instance for use with multiple contexts. Whether a language
     * instance supports being used for multiple contexts depends on its
     * {@link Registration#contextPolicy() context policy}.
     * <p>
     * With the default context policy {@link ContextPolicy#EXCLUSIVE exclusive}, this method will
     * never be invoked. This method will be called prior or after the first context was created for
     * this language. In case an {@link org.graalvm.polyglot.Context.Builder#engine(Engine) explicit
     * engine} was used to create a context, then this method will be invoked prior to the
     * {@link #createContext(Env) creation} of the first language context of a language. For inner
     * contexts, this method may be invoked prior to the first
     * {@link TruffleLanguage.Env#newContextBuilder() inner context} that is created, but after the
     * the first outer context was created. No guest language code must be invoked in this method.
     * This method is called at most once per language instance.
     * <p>
     * A language may use this method to invalidate assumptions that assume a single context only.
     * For example, assumptions that are dependent on the language context data. It is required to
     * invalidate any such assumptions that are used in the AST when this method is invoked.
     *
     * @see #areOptionsCompatible(OptionValues, OptionValues)
     * @see ContextPolicy
     * @since 1.0
     */
    protected void initializeMultipleContexts() {
    }

    /**
     * Disposes the context created by
     * {@link #createContext(com.oracle.truffle.api.TruffleLanguage.Env)}. A dispose cleans up all
     * resources associated with a context. The context may become unusable after it was disposed.
     * It is not allowed to run guest language code while disposing a context. Finalization code
     * should be run in {@link #finalizeContext(Object)} instead. Finalization will be performed
     * prior to context {@link #disposeContext(Object) disposal}.
     * <p>
     * The disposal order can be influenced by specifying {@link Registration#dependentLanguages()
     * language dependencies}. By default internal languages are disposed last, otherwise the
     * default order is unspecified but deterministic. During disposal no other language must be
     * accessed using the {@link Env language environment}.
     * <p>
     * All threads {@link Env#createThread(Runnable) created} by a language must be stopped after
     * dispose was called. The languages are responsible for fulfilling that contract otherwise an
     * {@link AssertionError} is thrown. It is recommended to join all threads that were disposed.
     *
     * @param context the context created by
     *            {@link #createContext(com.oracle.truffle.api.TruffleLanguage.Env)}
     * @see #finalizeContext(Object) to run finalization code for a context.
     * @see #disposeThread(Object, Thread) to perform disposal actions when a thread is no longer
     *      used.
     *
     * @since 0.8 or earlier
     */
    protected void disposeContext(C context) {
    }

    /**
     * Parses the {@link ParsingRequest#getSource() provided source} and generates its appropriate
     * AST representation. The parsing should execute no user code, it should only create the
     * {@link Node} tree to represent the source. If the {@link ParsingRequest#getSource() provided
     * source} does not correspond naturally to a {@link CallTarget call target}, the returned call
     * target should create and if necessary initialize the corresponding language entity and return
     * it.
     * <p>
     * The result of the parsing request is cached per language instance,
     * {@link ParsingRequest#getSource() source} and {@link ParsingRequest#getArgumentNames()
     * argument names}. It is safe to assume that current {@link TruffleLanguage language} instance
     * and {@link ParsingRequest#getArgumentNames() argument names} will remain unchanged for a
     * parsed {@link CallTarget}. The scope of the caching is influenced by the
     * {@link Registration#contextPolicy() context policy} and option
     * {@link TruffleLanguage#areOptionsCompatible(OptionValues, OptionValues) compatibility}.
     * Caching may be {@link Source#isCached() disabled} for sources. It is enabled for new sources
     * by default.
     * <p>
     * The {@code argumentNames} may contain symbolic names for actual parameters of the call to the
     * returned value. The result should be a call target with method
     * {@link CallTarget#call(java.lang.Object...)} that accepts as many arguments as were provided
     * via the {@link ParsingRequest#getArgumentNames()} method.
     * <p>
     * Implement {@link #parse(com.oracle.truffle.api.TruffleLanguage.InlineParsingRequest)} to
     * parse source in a specific context location.
     *
     * @see TruffleLanguage.Registration#contextPolicy()
     * @param request request for parsing
     * @return a call target to invoke which also keeps in memory the {@link Node} tree representing
     *         just parsed <code>code</code>
     * @throws Exception exception can be thrown when parsing goes wrong. Here-in thrown exception
     *             is propagated to the user who called one of <code>eval</code> methods of
     *             {@link org.graalvm.polyglot.Context}
     * @since 0.22
     */
    protected CallTarget parse(ParsingRequest request) throws Exception {
        throw new UnsupportedOperationException(
                        String.format("Override parse method of %s, it will be made abstract in future version of Truffle API!", getClass().getName()));
    }

    /**
     * Parses the {@link InlineParsingRequest#getSource() provided source snippet} at the
     * {@link InlineParsingRequest#getLocation() provided location} and generates its appropriate
     * AST representation. The parsing should execute no user code, it should only create the
     * {@link Node} tree to represent the source.
     * <p>
     * The parsing should be performed in a context (specified by
     * {@link InlineParsingRequest#getLocation()}). The result should be an AST fragment with method
     * {@link ExecutableNode#execute(com.oracle.truffle.api.frame.VirtualFrame)} that accepts frames
     * valid at the {@link InlineParsingRequest#getLocation() provided location}.
     * <p>
     * When not implemented, <code>null</code> is returned by default.
     *
     * @param request request for parsing
     * @return a fragment to invoke which also keeps in memory the {@link Node} tree representing
     *         just parsed {@link InlineParsingRequest#getSource() code}, or <code>null</code> when
     *         inline parsing of code snippets is not implemented
     * @throws Exception exception can be thrown when parsing goes wrong.
     * @since 0.31
     */
    protected ExecutableNode parse(InlineParsingRequest request) throws Exception {
        return null;
    }

    /**
     * Returns a set of option descriptors that are supported by this language. Option values are
     * accessible using the {@link Env#getOptions() environment} when the context is
     * {@link #createContext(Env) created}. To construct option descriptors from a list then
     * {@link OptionDescriptors#create(List)} can be used. Languages must always return the same
     * option descriptors independent of the language instance or side-effects.
     *
     * @see Option For an example of declaring the option descriptor using an annotation.
     * @since 0.27
     */
    protected OptionDescriptors getOptionDescriptors() {
        return OptionDescriptors.EMPTY;
    }

    /**
     * Notifies the language with pre-initialized context about {@link Env} change. See
     * {@link org.graalvm.polyglot.Context} for information how to enable the Context
     * pre-initialization.
     * <p>
     * During the pre-initialization (in the native compilation time) the
     * {@link #createContext(com.oracle.truffle.api.TruffleLanguage.Env)} and
     * {@link #initializeContext(java.lang.Object)} methods are called. In the image execution time,
     * the {@link #patchContext(java.lang.Object, com.oracle.truffle.api.TruffleLanguage.Env)} is
     * called on all pre-initialized languages as a consequence of
     * {@link org.graalvm.polyglot.Context#create(java.lang.String...)} invocation. The contexts are
     * patched in a topological order starting from dependent languages. If the
     * {@link #patchContext(java.lang.Object, com.oracle.truffle.api.TruffleLanguage.Env)} is
     * successful for all pre-initialized languages the pre-initialized context is used, otherwise a
     * new context is created.
     * <p>
     * Typical implementation looks like:
     *
     * {@link TruffleLanguageSnippets.PreInitializedLanguage#patchContext}
     *
     * @param context the context created by
     *            {@link #createContext(com.oracle.truffle.api.TruffleLanguage.Env)} during
     *            pre-initialization
     * @param newEnv the new environment replacing the environment used in pre-initialization phase
     * @return true in case of successful environment update. When the context cannot be updated to
     *         a new environment return false to create a new context. By default it returns
     *         {@code false} to prevent an usage of pre-initialized context by a language which is
     *         not aware of context pre-initialization.
     * @since 0.31
     */
    protected boolean patchContext(C context, Env newEnv) {
        return false;
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
            Objects.requireNonNull(source);
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
         * @deprecated {@link #parse(com.oracle.truffle.api.TruffleLanguage.InlineParsingRequest)}
         *             and {@link InlineParsingRequest#getLocation()} is the preferred approach to
         *             parse a source at a {@link Node} location.
         */
        @Deprecated
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
         * @deprecated {@link #parse(com.oracle.truffle.api.TruffleLanguage.InlineParsingRequest)}
         *             and {@link InlineParsingRequest#getFrame()} is the preferred approach to
         *             parse a source with a frame context.
         */
        @Deprecated
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
            return truffleLanguage.parse(this);
        }
    }

    /**
     * Request for inline parsing. Contains information of what to parse and in which context.
     *
     * @since 0.31
     */
    public static final class InlineParsingRequest {
        private final Node node;
        private final MaterializedFrame frame;
        private final Source source;
        private boolean disposed;

        InlineParsingRequest(Source source, Node node, MaterializedFrame frame) {
            Objects.requireNonNull(source);
            this.node = node;
            this.frame = frame;
            this.source = source;
        }

        /**
         * The source code to parse.
         *
         * @return the source code, never <code>null</code>
         * @since 0.31
         */
        public Source getSource() {
            if (disposed) {
                throw new IllegalStateException();
            }
            return source;
        }

        /**
         * Specifies the code location for parsing. The location is specified as an instance of a
         * {@link Node} in the AST. The node can be
         * {@link com.oracle.truffle.api.instrumentation.EventContext#getInstrumentedNode()}, for
         * example.
         *
         * @return a {@link Node} defining AST context for the parsing, it's never <code>null</code>
         * @since 0.31
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
         * @since 0.31
         */
        public MaterializedFrame getFrame() {
            if (disposed) {
                throw new IllegalStateException();
            }
            return frame;
        }

        void dispose() {
            disposed = true;
        }

        ExecutableNode parse(TruffleLanguage<?> truffleLanguage) throws Exception {
            return truffleLanguage.parse(this);
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
     * @deprecated write to the {@link Env#getPolyglotBindings() polyglot bindings} object instead
     *             when symbols need to be exported. Implicit exported values should be exposed
     *             using {@link TruffleLanguage#findTopScopes(Object)} instead.
     */
    @Deprecated
    protected Object findExportedSymbol(C context, String globalName, boolean onlyExplicit) {
        return null;
    }

    /**
     * Returns <code>true</code> if code of this language is allowed to be executed on this thread.
     * The method returns <code>false</code> to deny execution on this thread. The default
     * implementation denies access to more than one thread at the same time. The
     * {@link Thread#currentThread() current thread} may differ from the passed thread.
     * <p>
     * <b>Example multi-threaded language implementation:</b>
     * {@link TruffleLanguageSnippets.MultiThreadedLanguage#initializeThread}
     *
     * @param thread the thread that accesses the context for the first time.
     * @param singleThreaded <code>true</code> if the access is considered single-threaded,
     *            <code>false</code> if more than one thread is active at the same time.
     * @since 0.28
     */
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        return singleThreaded;
    }

    /**
     * Invoked before the context is accessed from multiple threads at the same time. This allows
     * languages to perform actions that are required to support multi-threading. It will never be
     * invoked if {@link #isThreadAccessAllowed(Thread, boolean)} is implemented to deny access from
     * multiple threads at the same time. All initialized languages must allow multi-threading for
     * this method to be invoked.
     * <p>
     * <b>Example multi-threaded language implementation:</b>
     * {@link TruffleLanguageSnippets.MultiThreadedLanguage#initializeThread}
     *
     * @param context the context that should be prepared for multi-threading.
     * @since 0.28
     */
    protected void initializeMultiThreading(C context) {
    }

    /**
     * Invoked before a context is accessed from a new thread. This allows the language to perform
     * initialization actions for each thread before guest language code is executed. Also for
     * languages that deny access from multiple threads at the same time, multiple threads may be
     * initialized if they are used sequentially. This method will be invoked before the context is
     * {@link #initializeContext(Object) initialized} for the thread the context will be initialized
     * with.
     * <p>
     * The {@link Thread#currentThread() current thread} may differ from the initialized thread.
     * <p>
     * <b>Example multi-threaded language implementation:</b>
     * {@link TruffleLanguageSnippets.MultiThreadedLanguage#initializeThread}
     *
     * @param context the context that is entered
     * @param thread the thread that accesses the context for the first time.
     *
     * @since 0.28
     */
    protected void initializeThread(C context, Thread thread) {
    }

    /**
     * Invoked the last time code will be executed for this thread and context. This allows the
     * language to perform cleanup actions for each thread and context. Threads might be disposed
     * before after or while a context is disposed. The {@link Thread#currentThread() current
     * thread} may differ from the disposed thread.
     * <p>
     *
     * <b>Example multi-threaded language implementation: </b>
     * {@link TruffleLanguageSnippets.MultiThreadedLanguage#initializeThread}
     *
     * @since 0.28
     */
    @SuppressWarnings("unused")
    protected void disposeThread(C context, Thread thread) {
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
     * @deprecated in 0.33 implement {@link #findTopScopes(Object)} instead.
     */
    @Deprecated
    protected Object getLanguageGlobal(C context) {
        return null;
    }

    /**
     * Checks whether the object is provided by this language.
     *
     * @param object the object to check
     * @return <code>true</code> if this language can deal with such object in native way
     * @since 0.8 or earlier
     */
    protected abstract boolean isObjectOfLanguage(Object object);

    /**
     * Find a hierarchy of local scopes enclosing the given {@link Node node}. Unless the node is in
     * a global scope, it is expected that there is at least one scope provided, that corresponds to
     * the enclosing function. The language might provide additional block scopes, closure scopes,
     * etc. Global top scopes are provided by {@link #findTopScopes(java.lang.Object)}. The scope
     * hierarchy should correspond with the scope nesting, from the inner-most to the outer-most.
     * The scopes are expected to contain variables valid at the given node.
     * <p>
     * Scopes may depend on the information provided by the frame. <br/>
     * Lexical scopes are returned when <code>frame</code> argument is <code>null</code>.
     * <p>
     * When not overridden, the enclosing {@link RootNode}'s scope with variables read from its
     * {@link FrameDescriptor}'s {@link FrameSlot}s is provided by default.
     * <p>
     * The
     * {@link com.oracle.truffle.api.instrumentation.TruffleInstrument.Env#findLocalScopes(com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.frame.Frame)}
     * provides result of this method to instruments.
     *
     * @param context the current context of the language
     * @param node a node to find the enclosing scopes for. The node, is inside a {@link RootNode}
     *            associated with this language.
     * @param frame The current frame the node is in, or <code>null</code> for lexical access when
     *            the program is not running, or is not suspended at the node's location.
     * @return an iterable with scopes in their nesting order from the inner-most to the outer-most.
     * @since 0.30
     */
    protected Iterable<Scope> findLocalScopes(C context, Node node, Frame frame) {
        assert node != null;
        return AccessAPI.engineAccess().createDefaultLexicalScope(node, frame);
    }

    /**
     * Find a hierarchy of top-most scopes of the language, if any. The scopes should be returned
     * from the inner-most to the outer-most scope order. The language may return an empty iterable
     * to indicate no scopes. The returned scope objects may be cached by the caller per language
     * context. Therefore the method should always return equivalent top-scopes and variables
     * objects for a given language context. Changes to the top scope by executing guest language
     * code should be reflected by cached scope instances. It is recommended to store the top-scopes
     * iterable directly in the language context for efficient access.
     * <p>
     * <h3>Interpretation</h3> In most languages, just evaluating an expression like
     * <code>Math</code> is equivalent of a lookup with the identifier 'Math' in the top-most scopes
     * of the language. Looking up the identifier 'Math' should have equivalent semantics as reading
     * with the key 'Math' from the variables object of one of the top-most scopes of the language.
     * In addition languages may optionally allow modification and insertion with the variables
     * object of the returned top-scopes.
     * <p>
     * Languages may want to specify multiple top-scopes. It is recommended to stay as close as
     * possible to the set of top-scopes that as is described in the guest language specification,
     * if available. For example, in JavaScript, there is a 'global environment' and a 'global
     * object' scope. While the global environment scope contains class declarations and is not
     * insertable, the global object scope is used to insert new global variable values and is
     * therefore insertable.
     * <p>
     * <h3>Use Cases</h3>
     * <ul>
     * <li>Top scopes are accessible to instruments with
     * {@link com.oracle.truffle.api.instrumentation.TruffleInstrument.Env#findTopScopes(java.lang.String)}
     * . They are used by debuggers to access the top-most scopes of the language.
     * <li>Top scopes available in the {@link org.graalvm.polyglot polyglot API} as context
     * {@link Context#getBindings(String) bindings} object. When members of the bindings object are
     * {@link Value#getMember(String) read} then the first scope where the key exists is read. If a
     * member is {@link Value#putMember(String, Object) modified} in the bindings object, then the
     * value will be written to the first scope where the key exists. If a new member is added to
     * the bindings object then it is added to the first variables object where the key is
     * insertable. If a member is removed, it is only tried to be removed from the first scope of
     * where such a key exists. If {@link Value#getMemberKeys() member keys} are requested from the
     * bindings object, then the variable object keys are returned sorted from first to last.
     * </ul>
     * <p>
     * When not overridden then a single read-only scope named 'global' without any keys will be
     * returned.
     *
     * @param context the current context of the language
     * @return an iterable with scopes in their nesting order from the inner-most to the outer-most.
     * @since 0.30
     */
    protected Iterable<Scope> findTopScopes(C context) {
        Object global = getLanguageGlobal(context);
        return AccessAPI.engineAccess().createDefaultTopScope(global);
    }

    /**
     * Generates language specific textual representation of a value. Each language may have special
     * formating conventions - even primitive values may not follow the traditional Java formating
     * rules. As such when {@link org.graalvm.polyglot.Value#toString()} is requested, it consults
     * the language that produced the value by calling this method. By default this method calls
     * {@link Objects#toString(java.lang.Object)}.
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
     * This method affects behavior of
     * {@link org.graalvm.polyglot.Context#eval(org.graalvm.polyglot.Source)} - when evaluating an
     * {@link Source#isInteractive() interactive source} the result of the evaluation is tested for
     * {@link #isVisible(java.lang.Object, java.lang.Object) visibility} and if the value is found
     * visible, it gets {@link TruffleLanguage#toString(java.lang.Object, java.lang.Object)
     * converted to string} and printed to
     * {@link org.graalvm.polyglot.Context.Builder#out(OutputStream) standard output}.
     * <p>
     * A language can control whether a value is or isn't printed by overriding this method and
     * returning <code>false</code> for some or all values. In such case it is up to the language
     * itself to use the {@link Env#out()}, {@link Env#err()} and {@link Env#in()} streams of the
     * environment.
     *
     * When evaluation is called with an {@link Source#isInteractive() interactive source} of a
     * language that controls its interactive behavior, it is the responsibility of the language
     * itself to print the result to use the {@link Env#out()}, {@link Env#err()} and
     * {@link Env#in()} streams of the environment.
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
     * <code>null</code>. The meta-object should be an interop value. An interop value can be either
     * a <code>TruffleObject</code> (e.g. a native object from the other language) to support
     * interoperability between languages or a {@link String}.
     * <p>
     * It can be beneficial for performance to return the same value for each guest type (i.e. cache
     * the meta-objects per context).
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

    void initialize(LanguageInfo language, Object vmObject) {
        this.languageInfo = language;
        this.reference = new ContextReference<>(vmObject);
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

    ExecutableNode parseInline(Source source, Node context, MaterializedFrame frame) {
        assert context != null;
        InlineParsingRequest request = new InlineParsingRequest(source, context, frame);
        ExecutableNode snippet;
        try {
            snippet = request.parse(this);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            request.dispose();
        }
        return snippet;
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
     * Returns the current language context entered on the current thread. If a
     * {@link TruffleLanguage language} instance is available, a
     * {@link TruffleLanguage#getContextReference() context reference} should be used instead for
     * performance reasons. An {@link IllegalStateException} is thrown if the language is not yet
     * initialized or not executing on this thread. If invoked on the fast-path then
     * <code>languageClass</code> must be a compilation final value.
     *
     * @param <C> the context type
     * @param <T> the language type
     * @param languageClass the exact language class needs to be provided for the lookup.
     * @see TruffleLanguage#getContextReference()
     * @since 0.27
     */
    protected static <C, T extends TruffleLanguage<C>> C getCurrentContext(Class<T> languageClass) {
        return AccessAPI.engineAccess().getCurrentContext(languageClass);
    }

    /**
     * Returns the home location for this language. This corresponds to the directory in which the
     * Jar file is located, if run from a Jar file. For an AOT compiled binary, this corresponds to
     * the location of the language files in the default GraalVM distribution layout. executable or
     * shared library.
     *
     * @since 1.0
     */
    protected final String getLanguageHome() {
        return AccessAPI.engineAccess().getLanguageHome(AccessAPI.nodesAccess().getEngineObject(languageInfo));
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

        private static final Object UNSET_CONTEXT = new Object();
        private final Object vmObject; // PolylgotLanguageContext
        private final TruffleLanguage<Object> spi;
        private final InputStream in;
        private final OutputStream err;
        private final OutputStream out;
        private final Map<String, Object> config;
        private final OptionValues options;
        private final String[] applicationArguments;
        private final FileSystem fileSystem;

        @CompilationFinal private volatile List<Object> services;

        @CompilationFinal private volatile Object context = UNSET_CONTEXT;
        @CompilationFinal private volatile Assumption contextUnchangedAssumption = Truffle.getRuntime().createAssumption("Language context unchanged");
        @CompilationFinal private volatile boolean initialized = false;
        @CompilationFinal private volatile Assumption initializedUnchangedAssumption = Truffle.getRuntime().createAssumption("Language context initialized unchanged");
        @CompilationFinal private volatile boolean valid;

        @SuppressWarnings("unchecked")
        private Env(Object vmObject, TruffleLanguage<?> language, OutputStream out, OutputStream err, InputStream in, Map<String, Object> config, OptionValues options, String[] applicationArguments,
                        FileSystem fileSystem) {
            this.vmObject = vmObject;
            this.spi = (TruffleLanguage<Object>) language;
            this.in = in;
            this.err = err;
            this.out = out;
            this.config = config;
            this.options = options;
            this.applicationArguments = applicationArguments == null ? new String[0] : applicationArguments;
            this.valid = true;
            this.fileSystem = fileSystem;
        }

        Object getVMObject() {
            return vmObject;
        }

        TruffleLanguage<Object> getSpi() {
            return spi;
        }

        void checkDisposed() {
            if (AccessAPI.engineAccess().isDisposed(vmObject)) {
                throw new IllegalStateException("Language environment is already disposed.");
            }
            if (!valid) {
                throw new IllegalStateException("Language environment is already invalidated.");
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
         * Returns <code>true</code> if the creation of new threads is allowed in the current
         * environment.
         *
         * @see #createThread(Runnable)
         * @since 0.28
         */
        public boolean isCreateThreadAllowed() {
            return AccessAPI.engineAccess().isCreateThreadAllowed(vmObject);
        }

        /**
         * Creates a new thread that has access to the current language context. A thread is
         * {@link TruffleLanguage#initializeThread(Object, Thread) initialized} when it is
         * {@link Thread#start() started} and {@link TruffleLanguage#disposeThread(Object, Thread)
         * disposed} as soon as the thread finished the execution. In order to start threads the
         * language needs to {@link TruffleLanguage#isThreadAccessAllowed(Thread, boolean) allow}
         * access from multiple threads at the same time.
         * <p>
         * It is recommended to set an
         * {@link Thread#setUncaughtExceptionHandler(java.lang.Thread.UncaughtExceptionHandler)
         * uncaught exception handler} for the created thread. For example the thread can throw an
         * uncaught exception if one of the initialized language contexts don't support execution on
         * this thread.
         * <p>
         * The language that created and started the thread is responsible to complete all running
         * or waiting threads when the context is {@link TruffleLanguage#disposeContext(Object)
         * disposed}.
         *
         * @param runnable the runnable to run on this thread.
         * @throws IllegalStateException if thread creation is not {@link #isCreateThreadAllowed()
         *             allowed}.
         * @since 0.28
         */
        @TruffleBoundary
        public Thread createThread(Runnable runnable) {
            return createThread(runnable, null);
        }

        /**
         * Creates a new thread that has access to the given context. A thread is
         * {@link TruffleLanguage#initializeThread(Object, Thread) initialized} when it is
         * {@link Thread#start() started} and {@link TruffleLanguage#disposeThread(Object, Thread)
         * disposed} as soon as the thread finished the execution.
         * <p>
         * It is recommended to set an
         * {@link Thread#setUncaughtExceptionHandler(java.lang.Thread.UncaughtExceptionHandler)
         * uncaught exception handler} for the created thread. For example the thread can throw an
         * uncaught exception if one of the initialized language contexts don't support execution on
         * this thread.
         * <p>
         * The language that created and started the thread is responsible to complete all running
         * or waiting threads when the context is {@link TruffleLanguage#disposeContext(Object)
         * disposed}.
         * <p>
         * The {@link TruffleContext} can be either an inner context created by
         * {@link #newContextBuilder()}.{@link TruffleContext.Builder#build() build()}, or the
         * context associated with this environment obtained from {@link #getContext()}.
         *
         * @param runnable the runnable to run on this thread
         * @param context the context to enter and leave when the thread is started.
         * @throws IllegalStateException if thread creation is not {@link #isCreateThreadAllowed()
         *             allowed}.
         * @see #getContext()
         * @see #newContextBuilder()
         * @since 0.28
         */
        @TruffleBoundary
        public Thread createThread(Runnable runnable, @SuppressWarnings("hiding") TruffleContext context) {
            return AccessAPI.engineAccess().createThread(vmObject, runnable, context != null ? context.impl : null);
        }

        /**
         * Returns a new context builder useful to create inner context instances.
         *
         * @see TruffleContext for details on language inner contexts.
         * @since 0.27
         */
        public TruffleContext.Builder newContextBuilder() {
            return TruffleContext.EMPTY.new Builder(this);
        }

        /**
         * Returns a TruffleObject that represents the polyglot bindings. The polyglot bindings
         * consists of a set of symbols that have been exported explicitly by the languages or the
         * embedder. This set of symbols allows for data exchange between polyglot languages. The
         * polyglot bindings is separate from language bindings. The symbols can by read using
         * string identifiers, a list of symbols may be requested with the keys message. Existing
         * identifiers are removable, modifiable, readable and any new identifiers are insertable.
         *
         * @since 0.32
         */
        public Object getPolyglotBindings() {
            return AccessAPI.engineAccess().getPolyglotBindingsForLanguage(vmObject);
        }

        /**
         * Explicitly imports a symbol from the polyglot bindings. The behavior of this method is
         * equivalent to sending a READ message to the {@link #getPolyglotBindings() polyglot
         * bindings} object. Reading a symbol that does not exist will return <code>null</code>.
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
        @TruffleBoundary
        public Object importSymbol(String symbolName) {
            return AccessAPI.engineAccess().importSymbol(vmObject, this, symbolName);
        }

        /**
         * Explicitly exports a symbol to the polyglot bindings object. The behavior of this method
         * is equivalent to sending a WRITE message to the {@link #getPolyglotBindings() polyglot
         * bindings} object. Exporting a symbol with a <code>null</code> value will remove the
         * symbol from the polyglot object.
         * <p>
         * The exported symbol value can either be a <code>TruffleObject</code> (e.g. a native
         * object from the other language) to support interoperability between languages,
         * {@link String} or one of the Java primitive wrappers ( {@link Integer}, {@link Double},
         * {@link Byte}, {@link Boolean}, etc.).
         *
         * @param symbolName the name with which the symbol should be exported into the polyglot
         *            scope
         * @param value the value to export for
         * @since 0.27
         */
        @TruffleBoundary
        public void exportSymbol(String symbolName, Object value) {
            AccessAPI.engineAccess().exportSymbol(vmObject, symbolName, value);
        }

        /**
         * Returns <code>true</code> if host access is generally allowed. If this method returns
         * <code>false</code> then {@link #lookupHostSymbol(String)} will always fail.
         *
         * @since 0.27
         */
        @TruffleBoundary
        public boolean isHostLookupAllowed() {
            return AccessAPI.engineAccess().isHostAccessAllowed(vmObject, this);
        }

        /**
         * Adds an entry to the Java host class loader. All classes looked up with
         * {@link #lookupHostSymbol(String)} will lookup classes with this new entry. If the entry
         * was already added then calling this method again for the same entry has no effect. Given
         * entry must not be <code>null</code>.
         *
         * @throws SecurityException if the file is not {@link TruffleFile#isReadable() readable}.
         * @since 1.0
         */
        @TruffleBoundary
        public void addToHostClassPath(TruffleFile entry) {
            Objects.requireNonNull(entry);
            AccessAPI.engineAccess().addToHostClassPath(vmObject, entry);
        }

        /**
         * Looks up a Java class in the top-most scope the host environment. Throws an error if no
         * symbol was found or the symbol was not accessible. Symbols might not be accessible if a
         * {@link org.graalvm.polyglot.Context.Builder#hostClassFilter(java.util.function.Predicate)
         * class filter} prevents access. The returned object is always a <code>TruffleObject</code>
         * .
         *
         * @param symbolName the name of the symbol in the the host language.
         * @since 0.27
         */
        @TruffleBoundary
        public Object lookupHostSymbol(String symbolName) {
            return AccessAPI.engineAccess().lookupHostSymbol(vmObject, this, symbolName);
        }

        /**
         * Returns <code>true</code> if the argument is Java host language object wrapped using
         * Truffle interop.
         *
         * @see #asHostObject(Object)
         * @since 1.0
         */
        @SuppressWarnings("static-method")
        public boolean isHostObject(Object value) {
            return AccessAPI.engineAccess().isHostObject(value);
        }

        /**
         * Returns the java host representation of a Truffle guest object if it represents a Java
         * host language object. Throws {@link ClassCastException} if the provided argument is not a
         * {@link #isHostObject(Object) host object}.
         *
         * @since 1.0
         */
        public Object asHostObject(Object value) {
            if (!isHostObject(value)) {
                CompilerDirectives.transferToInterpreter();
                throw new ClassCastException();
            }
            return AccessAPI.engineAccess().asHostObject(value);
        }

        /**
         * Converts a existing Java host object to a guest language value. If the value is already
         * an interop value, then no conversion will be performed. Otherwise, the returned wraps the
         * host object and provides support for the interop contract to access the java members. The
         * interpretation of converted objects is described in {@link Context#asValue(Object)}.
         * <p>
         * This method should be used exclusively to convert already allocated Java objects to a
         * guest language representation. To allocate new host objects users should use
         * {@link #lookupHostSymbol(String)} to lookup the class and then send a NEW interop message
         * to that object to instantiate it. This method does not respect configured
         * {@link org.graalvm.polyglot.Context.Builder#hostClassFilter(java.util.function.Predicate)
         * class filters}.
         *
         * @param hostObject the host object to convert
         * @since 1.0
         */
        public Object asGuestValue(Object hostObject) {
            return AccessAPI.engineAccess().toGuestValue(hostObject, vmObject);
        }

        /**
         * Wraps primitive interop values in a TruffleObject exposing their methods as members. By
         * default primitive host values are not wrapped in TruffleObjects to expose their members.
         * This method is intended for compatibility with existing Java interop APIs that expect
         * such behavior. This method boxes the following primitive interop values: {@link Boolean},
         * {@link Byte}, {@link Short}, {@link Integer}, {@link Long}, {@link Float}, {@link Double}
         * , {@link Character}, {@link String}. If the provided value is already boxed then the
         * current value will be returned. If the provided value is not an interop value then an
         * {@link IllegalArgumentException} will be thrown.
         *
         * @throws IllegalArgumentException if value is an invalid interop value.
         * @param guestObject the primitive guest value to box
         * @see #asGuestValue(Object)
         * @since 1.0
         */
        public Object asBoxedGuestValue(Object guestObject) {
            return AccessAPI.engineAccess().asBoxedGuestValue(guestObject, vmObject);
        }

        /**
         * Returns <code>true</code> if the argument is a Java host language function wrapped using
         * Truffle interop.
         *
         * @since 1.0
         */
        @SuppressWarnings("static-method")
        public boolean isHostFunction(Object value) {
            return AccessAPI.engineAccess().isHostFunction(value);
        }

        /**
         * Find a meta-object of a value, if any. The meta-object represents a description of the
         * object, reveals it's kind and it's features. Some information that a meta-object might
         * define includes the base object's type, interface, class, methods, attributes, etc.
         * <p>
         * When no meta-object is known, returns <code>null</code>. The meta-object is an interop
         * value. An interop value can be either a <code>TruffleObject</code> (e.g. a native object
         * from the other language) to support interoperability between languages or a
         * {@link String}.
         *
         * @param value the value to find the meta object for.
         * @since 1.0
         */
        public Object findMetaObject(Object value) {
            return AccessAPI.engineAccess().findMetaObjectForLanguage(vmObject, value);
        }

        /**
         * Tests whether an exception is a host exception thrown by a Java Interop method
         * invocation.
         *
         * Host exceptions may be thrown by {@linkplain com.oracle.truffle.api.interop.Message
         * messages} sent to Java objects that involve the invocation of a Java method or
         * constructor ({@code EXECUTE}, {@code INVOKE}, {@code NEW}). The host exception may be
         * unwrapped using {@link #asHostException(Throwable)}.
         *
         * @param exception the {@link Throwable} to test
         * @return {@code true} if the {@code exception} is a host exception, {@code false}
         *         otherwise
         * @see #asHostException(Throwable)
         * @since 1.0
         */
        @SuppressWarnings("static-method")
        public boolean isHostException(Throwable exception) {
            return AccessAPI.engineAccess().isHostException(exception);
        }

        /**
         * Unwraps a host exception thrown by a Java method invocation.
         *
         * Host exceptions may be thrown by {@linkplain com.oracle.truffle.api.interop.Message
         * messages} sent to Java objects that involve the invocation of a Java method or
         * constructor ({@code EXECUTE}, {@code INVOKE}, {@code NEW}). Host exceptions can be
         * identified using {@link #isHostException(Throwable)} .
         *
         * @param exception the host exception to unwrap
         * @return the original Java exception
         * @throws IllegalArgumentException if the {@code exception} is not a host exception
         * @see #isHostException(Throwable)
         * @since 1.0
         */
        @SuppressWarnings("static-method")
        public Throwable asHostException(Throwable exception) {
            return AccessAPI.engineAccess().asHostException(exception);
        }

        /**
         * Returns {@code true} if the argument is a host symbol, representing the constructor and
         * static members of a Java class, as obtained by e.g. {@link #lookupHostSymbol}.
         *
         * @see #lookupHostSymbol(String)
         * @since 1.0
         */
        @SuppressWarnings("static-method")
        public boolean isHostSymbol(Object guestObject) {
            return AccessAPI.engineAccess().isHostSymbol(guestObject);
        }

        /**
         * Converts a Java class to a host symbol as if by
         * {@code lookupHostSymbol(symbolClass.getName())} but without an actual lookup. Must not be
         * used with Truffle or guest language classes.
         *
         * @see #lookupHostSymbol(String)
         * @since 1.0
         */
        @TruffleBoundary
        public Object asHostSymbol(Class<?> symbolClass) {
            return AccessAPI.engineAccess().asHostSymbol(vmObject, symbolClass);
        }

        /**
         * Returns <code>true</code> if access to native code is generally allowed. If this method
         * returns <code>false</code> then loading native libraries with the Truffle NFI will fail.
         *
         * @since 1.0
         */
        @TruffleBoundary
        public boolean isNativeAccessAllowed() {
            return AccessAPI.engineAccess().isNativeAccessAllowed(vmObject, this);
        }

        /**
         * Allows it to be determined if this {@link org.graalvm.polyglot.Context} can execute code
         * written in a language with a given MIME type.
         *
         * @see Source#getMimeType()
         * @see #parse(Source, String...)
         *
         * @return a boolean that indicates if the MIME type is supported
         * @since 0.11
         */
        @TruffleBoundary
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
        @TruffleBoundary
        public CallTarget parse(Source source, String... argumentNames) {
            CompilerAsserts.neverPartOfCompilation();
            checkDisposed();
            return AccessAPI.engineAccess().parseForLanguage(vmObject, source, argumentNames);
        }

        /**
         * Input stream provided by {@link org.graalvm.polyglot.Context.Builder#in(InputStream)}
         * this language is being executed in.
         *
         * @return reader, never <code>null</code>
         * @since 0.8 or earlier
         */
        @TruffleBoundary
        public InputStream in() {
            checkDisposed();
            return in;
        }

        /**
         * Standard output writer provided by
         * {@link org.graalvm.polyglot.Context.Builder#out(OutputStream)} this language is being
         * executed in.
         *
         * @return writer, never <code>null</code>
         * @since 0.8 or earlier
         */
        @TruffleBoundary
        public OutputStream out() {
            checkDisposed();
            return out;
        }

        /**
         * Standard error writer provided by
         * {@link org.graalvm.polyglot.Context.Builder#err(OutputStream)} this language is being
         * executed in.
         *
         * @return writer, never <code>null</code>
         * @since 0.8 or earlier
         */
        @TruffleBoundary
        public OutputStream err() {
            checkDisposed();
            return err;
        }

        /**
         * Looks additional service up. An environment for a particular {@link TruffleLanguage
         * language} may also be associated with additional services. One can request
         * implementations of such services by calling this method with the type identifying the
         * requested service and its API.
         *
         * Services that can be obtained via this method include
         * {@link com.oracle.truffle.api.instrumentation.Instrumenter} and others.
         *
         * @param <T> type of requested service
         * @param type class of requested service
         * @return instance of T or <code>null</code> if there is no such service available
         * @since 0.12
         */
        @TruffleBoundary
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
        @TruffleBoundary
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
        @TruffleBoundary
        public <S> S lookup(LanguageInfo language, Class<S> type) {
            if (this.getSpi().languageInfo == language) {
                throw new IllegalArgumentException("Cannot request services from the current language.");
            }

            Env otherEnv = AccessAPI.engineAccess().getLanguageEnv(this, language);
            return otherEnv.getSpi().lookup(type);
        }

        /**
         * Returns a map mime-type to language instance of all languages that are installed in the
         * environment. Using the language instance additional services can be
         * {@link #lookup(LanguageInfo, Class) looked up} .
         *
         * @since 0.26
         */
        @TruffleBoundary
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
        @TruffleBoundary
        public Map<String, InstrumentInfo> getInstruments() {
            return AccessAPI.engineAccess().getInstruments(vmObject);
        }

        /**
         * Configuration arguments passed from an outer language context to an inner language
         * context. Inner language contexts can be created using {@link #newContextBuilder()}.
         *
         * @see TruffleContext to create inner contexts.
         * @see TruffleContext.Builder#config(String, Object) to pass configuration objects to the
         *      inner context.
         * @since 0.11
         */
        @TruffleBoundary
        public Map<String, Object> getConfig() {
            checkDisposed();
            return config;
        }

        /**
         * Returns the polyglot context associated with this environment.
         *
         * @return the polyglot context
         * @since 0.30
         */
        public TruffleContext getContext() {
            return AccessAPI.engineAccess().getPolyglotContext(vmObject);
        }

        /**
         * Returns a {@link TruffleFile} for given path.
         *
         * @param path the absolute or relative path to create {@link TruffleFile} for
         * @return {@link TruffleFile}
         * @since 1.0
         */
        @TruffleBoundary
        public TruffleFile getTruffleFile(String path) {
            return new TruffleFile(fileSystem, fileSystem.parsePath(path));
        }

        /**
         * Returns a {@link TruffleFile} for given {@link URI}.
         *
         * @param uri the {@link URI} to create {@link TruffleFile} for
         * @return {@link TruffleFile}
         * @since 1.0
         */
        @TruffleBoundary
        public TruffleFile getTruffleFile(URI uri) {
            checkDisposed();
            try {
                return new TruffleFile(fileSystem, fileSystem.parsePath(uri));
            } catch (UnsupportedOperationException e) {
                throw new FileSystemNotFoundException("FileSystem for: " + uri.getScheme() + " scheme is not supported.");
            }
        }

        /**
         * @since 1.0
         * @deprecated use {@link Source#newBuilder(String, TruffleFile)} instead.
         */
        @SuppressWarnings({"static-method", "deprecation"})
        @Deprecated
        public Source.Builder<IOException, RuntimeException, RuntimeException> newSourceBuilder(final TruffleFile file) {
            Objects.requireNonNull(file, "File must be non null");
            return Source.newBuilder(new TruffleFile.FileAdapter(file));
        }

        @SuppressWarnings("rawtypes")
        @TruffleBoundary
        <E extends TruffleLanguage> E getLanguage(Class<E> languageClass) {
            checkDisposed();
            if (languageClass != getSpi().getClass()) {
                throw new IllegalArgumentException("Invalid access to language " + languageClass + ".");
            }
            return languageClass.cast(getSpi());
        }

        Object findExportedSymbol(String globalName, boolean onlyExplicit) {
            Object c = getLanguageContext();
            if (c != UNSET_CONTEXT) {
                return getSpi().findExportedSymbol(c, globalName, onlyExplicit);
            } else {
                return null;
            }
        }

        Object getLanguageGlobal() {
            Object c = getLanguageContext();
            if (c != UNSET_CONTEXT) {
                return getSpi().getLanguageGlobal(c);
            } else {
                return null;
            }
        }

        Object findMetaObjectImpl(Object obj) {
            Object c = getLanguageContext();
            if (c != UNSET_CONTEXT) {
                return getSpi().findMetaObject(c, obj);
            } else {
                return null;
            }
        }

        SourceSection findSourceLocation(Object obj) {
            Object c = getLanguageContext();
            if (c != UNSET_CONTEXT) {
                return getSpi().findSourceLocation(c, obj);
            } else {
                return null;
            }
        }

        boolean isObjectOfLanguage(Object obj) {
            return getSpi().isObjectOfLanguage(obj);
        }

        Iterable<Scope> findLocalScopes(Node node, Frame frame) {
            assert node != null;
            return getSpi().findLocalScopes(context, node, frame);
        }

        Iterable<Scope> findTopScopes() {
            return getSpi().findTopScopes(context);
        }

        void dispose() {
            Object c = getLanguageContext();
            if (c != UNSET_CONTEXT) {
                getSpi().disposeContext(c);
            } else {
                throw new IllegalStateException("Disposing while context has not been set yet.");
            }
        }

        @TruffleBoundary
        void postInit() {
            try {
                getSpi().initializeContext(context);
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
            if (CompilerDirectives.isPartialEvaluationConstant(this)) {
                boolean localInitialized = initialized;
                if (initializedUnchangedAssumption.isValid()) {
                    return localInitialized;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    return initialized;
                }
            } else {
                return initialized;
            }
        }

        String toStringIfVisible(Object value, boolean checkVisibility) {
            Object c = getLanguageContext();
            if (c != UNSET_CONTEXT) {
                if (checkVisibility) {
                    if (!getSpi().isVisible(c, value)) {
                        return null;
                    }
                }
                return getSpi().toString(c, value);
            } else {
                return null;
            }
        }

        private Object getLanguageContext() {
            if (CompilerDirectives.isPartialEvaluationConstant(this)) {
                Object languageContext = this.context;
                if (contextUnchangedAssumption.isValid()) {
                    return languageContext;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    return context;
                }
            } else {
                return this.context;
            }
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
            return (C) AccessAPI.engineAccess().getCurrentContext(languageShared);
        }

    }

    /**
     * Defines the supported policy for reusing {@link TruffleLanguage languages} per context. I.e.
     * the policy specifies the degree of sharing that is allowed between multiple language
     * contexts. The default policy is {@link #EXCLUSIVE exclusive}. Every language is encouraged to
     * try to support a context policy that is as permissive as possible, where {@link #EXCLUSIVE
     * exclusive} is the least and {@link #SHARED shared} is the most permissive policy.
     * {@link TruffleLanguage#parse(ParsingRequest) Parse caching} is scoped per
     * {@link TruffleLanguage language} instance, therefore the context policy influences its
     * behavior.
     * <p>
     * The context policy applies to contexts that were created using the polyglot API as well as
     * for {@link TruffleContext inner contexts}. The context policy does not apply to nodes that
     * were created using the Truffle interop protocol. Therefore, interop message nodes always need
     * to be prepared to be used with policy {@link ContextPolicy#SHARED}.
     *
     * @see Registration#contextPolicy() To configure context policy for a language.
     * @see TruffleLanguage#parse(ParsingRequest)
     * @since 1.0
     */
    public enum ContextPolicy {

        /**
         * Use one exclusive {@link TruffleLanguage} instance per language context instance.
         * <p>
         * Using this policy has the following implications:
         * <ul>
         * <li>{@link TruffleLanguage#parse(ParsingRequest) Parse caching} is scoped per
         * {@link TruffleLanguage language} instance. This means the language context instance may
         * be directly stored in instance fields of AST nodes without the use of
         * {@link ContextReference}. The use of {@link ContextReference} is still recommended to
         * simplify migration to more permissive policies.
         * <li>If the language does not allow
         * {@link TruffleLanguage#isThreadAccessAllowed(Thread, boolean) multi-threading} (default
         * behavior) then the language instance is guaranteed to be used from one thread at a time
         * only. Cached ASTs will not be used from multiple threads at the same time.
         * <li>{@link TruffleLanguage#initializeMultipleContexts()} is guaranteed to never be
         * invoked.
         * </ul>
         *
         * @since 1.0
         */
        EXCLUSIVE,

        /**
         * Use a single {@link TruffleLanguage} instance per context instance, but allow the reuse
         * of a language instance when a context was {@link TruffleLanguage#disposeContext(Object)
         * disposed}. This policy is useful when parsed ASTs should be shared, but multiple thread
         * execution of the AST is not yet supported by the language.
         * <p>
         * Using this policy has the following implications:
         * <ul>
         * <li>{@link TruffleLanguage#parse(ParsingRequest) Parse caching} is scoped per
         * {@link TruffleLanguage language} instance. This means language context instances must NOT
         * be directly stored in instance fields of AST nodes and the {@link ContextReference} must
         * be used instead.
         * <li>If the language does not
         * {@link TruffleLanguage#isThreadAccessAllowed(Thread, boolean) allow} access from multiple
         * threads (default behavior) then the language instance is guaranteed to be used from one
         * thread at a time only. In this case also cached ASTs will not be used from multiple
         * threads at the same time. If the language allows access from multiple threads then also
         * ASTs may be used from multiple threads at the same time.
         * <li>{@link TruffleLanguage#initializeMultipleContexts()} will be invoked to notify the
         * language that multiple contexts will be used with one language instance.
         * <li>{@link TruffleLanguage Language} instance fields must only be used for data that can
         * be shared across multiple contexts.
         * </ul>
         *
         * @since 1.0
         */
        REUSE,

        /**
         * Use one {@link TruffleLanguage} instance for many language context instances.
         * <p>
         * Using this policy has the following implications:
         * <ul>
         * <li>{@link TruffleLanguage#parse(ParsingRequest) Parse caching} is scoped per
         * {@link TruffleLanguage language} instance. With multiple language instances per context,
         * context instances must <i>not</i> be directly stored in instance fields of AST nodes and
         * the {@link ContextReference} must be used instead.
         * <li>All methods of the {@link TruffleLanguage language} instance and parsed ASTs may be
         * called from multiple threads at the same time independent of whether multiple thread
         * access is {@link TruffleLanguage#isThreadAccessAllowed(Thread, boolean) allowed} for the
         * language.
         * <li>{@link TruffleLanguage#initializeMultipleContexts()} will be invoked to notify the
         * language that multiple contexts will be used with one language instance.
         * <li>{@link TruffleLanguage Language} instance fields must only be used for data that can
         * be shared across multiple contexts and mutable data held by the language instance must be
         * synchronized to support concurrent access.
         * </ul>
         *
         * @since 1.0
         */
        SHARED;

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

        static InteropSupport interopAccess() {
            return API.interopSupport();
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
        public boolean isTruffleStackTrace(Throwable t) {
            return t instanceof LazyStackTrace;
        }

        @Override
        public StackTraceElement[] getInternalStackTraceElements(Throwable t) {
            TruffleStackTrace trace = ((LazyStackTrace) t).getInternalStackTrace();
            if (trace == null) {
                return new StackTraceElement[0];
            } else {
                return trace.getInternalStackTrace();
            }
        }

        @Override
        public void materializeHostFrames(Throwable original) {
            TruffleStackTrace.materializeHostFrames(original);
        }

        @Override
        public InstrumentInfo createInstrument(Object vmObject, String id, String name, String version) {
            return new InstrumentInfo(vmObject, id, name, version);
        }

        @Override
        public Object getVMObject(InstrumentInfo info) {
            return info.getVmObject();
        }

        @Override
        public void initializeLanguage(TruffleLanguage<?> impl, LanguageInfo language, Object vmObject) {
            impl.initialize(language, vmObject);
        }

        @Override
        public boolean initializeMultiContext(TruffleLanguage<?> language) {
            language.initializeMultipleContexts();
            return language.initializeMultiContext();
        }

        @Override
        public Object getContext(Env env) {
            Object c = env.getLanguageContext();
            if (c != Env.UNSET_CONTEXT) {
                return c;
            } else {
                return null;
            }
        }

        @Override
        public TruffleLanguage<?> getSPI(Env env) {
            return env.getSpi();
        }

        @Override
        public Env createEnv(Object vmObject, TruffleLanguage<?> language, OutputStream stdOut, OutputStream stdErr, InputStream stdIn, Map<String, Object> config, OptionValues options,
                        String[] applicationArguments, FileSystem fileSystem) {
            Env env = new Env(vmObject, language, stdOut, stdErr, stdIn, config, options, applicationArguments, fileSystem);
            LinkedHashSet<Object> collectedServices = new LinkedHashSet<>();
            LanguageInfo info = language.languageInfo;
            AccessAPI.instrumentAccess().collectEnvServices(collectedServices, API.nodes().getEngineObject(info), language);
            env.services = new ArrayList<>(collectedServices);
            return env;
        }

        @Override
        public Object createEnvContext(Env env) {
            Object context = env.getSpi().createContext(env);
            env.context = context;
            Assumption contextUnchanged = env.contextUnchangedAssumption;
            env.contextUnchangedAssumption = Truffle.getRuntime().createAssumption("Language context unchanged");
            contextUnchanged.invalidate();
            return context;
        }

        @Override
        public TruffleContext createTruffleContext(Object impl) {
            return new TruffleContext(impl);
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
        public ExecutableNode parseInline(Env env, Source code, Node context, MaterializedFrame frame) {
            return env.getSpi().parseInline(code, context, frame);
        }

        @Override
        public LanguageInfo getLanguageInfo(Env env) {
            return env.getSpi().languageInfo;
        }

        @Override
        public void onThrowable(Node callNode, RootCallTarget root, Throwable e, Frame frame) {
            TruffleStackTrace.addStackFrameInfo(callNode, e, root, frame);
        }

        @Override
        public void initializeThread(Env env, Thread current) {
            env.getSpi().initializeThread(env.context, current);
        }

        @Override
        public boolean isThreadAccessAllowed(Env language, Thread thread, boolean singleThread) {
            return language.getSpi().isThreadAccessAllowed(thread, singleThread);
        }

        @Override
        public void initializeMultiThreading(Env env) {
            env.getSpi().initializeMultiThreading(env.context);
        }

        @Override
        public void finalizeContext(Env env) {
            env.getSpi().finalizeContext(env.context);
        }

        @Override
        public void disposeThread(Env env, Thread current) {
            env.getSpi().disposeThread(env.context, current);
        }

        @Override
        public Object evalInContext(Source source, Node node, final MaterializedFrame mFrame) {
            CallTarget target = API.nodes().getLanguage(node.getRootNode()).parse(source, node, mFrame);
            try {
                if (target instanceof RootCallTarget) {
                    RootNode exec = ((RootCallTarget) target).getRootNode();
                    return exec.execute(mFrame);
                } else {
                    throw new IllegalStateException("" + target);
                }
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
                return env.getSpi().languageInfo;
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
            return env.findMetaObjectImpl(obj);
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
        public <S> S lookup(TruffleLanguage<?> language, Class<S> type) {
            return language.lookup(type);
        }

        @Override
        public Iterable<Scope> findLocalScopes(Env env, Node node, Frame frame) {
            return env.findLocalScopes(node, frame);
        }

        @Override
        public Iterable<Scope> findTopScopes(Env env) {
            return env.findTopScopes();
        }

        @Override
        public OptionDescriptors describeOptions(TruffleLanguage<?> language, String requiredGroup) {
            OptionDescriptors descriptors = language.getOptionDescriptors();
            if (descriptors == null) {
                return OptionDescriptors.EMPTY;
            }
            assert verifyDescriptors(language, requiredGroup, descriptors);
            return descriptors;
        }

        private static boolean verifyDescriptors(TruffleLanguage<?> language, String requiredGroup, OptionDescriptors descriptors) {
            String groupPlusDot = requiredGroup + ".";
            for (OptionDescriptor descriptor : descriptors) {
                if (!descriptor.getName().equals(requiredGroup) && !descriptor.getName().startsWith(groupPlusDot)) {
                    throw new IllegalArgumentException(String.format("Illegal option prefix in name '%s' specified for option described by language '%s'. " +
                                    "The option prefix must match the id of the language '%s'.",
                                    descriptor.getName(), language.getClass().getName(), requiredGroup));
                }
            }
            return true;
        }

        @Override
        public Env patchEnvContext(Env env, OutputStream stdOut, OutputStream stdErr, InputStream stdIn, Map<String, Object> config, OptionValues options, String[] applicationArguments,
                        FileSystem fileSystem) {
            assert env.spi != null;
            final Env newEnv = createEnv(
                            env.vmObject,
                            env.spi,
                            stdOut,
                            stdErr,
                            stdIn,
                            config,
                            options,
                            applicationArguments, fileSystem);

            newEnv.initialized = env.initialized;
            newEnv.context = env.context;
            env.valid = false;
            return env.getSpi().patchContext(env.context, newEnv) ? newEnv : null;
        }

        @Override
        public boolean checkTruffleFile(File file) {
            return file instanceof FileAdapter;
        }

        @Override
        public byte[] truffleFileContent(File file) throws IOException {
            assert file instanceof FileAdapter : "File must be " + FileAdapter.class.getSimpleName();
            final TruffleFile tf = ((FileAdapter) file).getTruffleFile();
            return tf.readAllBytes();
        }

        @Override
        public File asFile(TruffleFile file) {
            return new FileAdapter(file);
        }

        @Override
        public void configureLoggers(Object polyglotContext, Map<String, Level> logLevels) {
            if (logLevels == null) {
                TruffleLogger.LoggerCache.getInstance().removeLogLevelsForContext(polyglotContext);
            } else {
                TruffleLogger.LoggerCache.getInstance().addLogLevelsForContext(polyglotContext, logLevels);
            }
        }

        @Override
        public boolean areOptionsCompatible(TruffleLanguage<?> language, OptionValues firstContextOptions, OptionValues newContextOptions) {
            return language.areOptionsCompatible(firstContextOptions, newContextOptions);
        }

        @Override
        public TruffleLanguage<?> getLanguage(Env env) {
            return env.getSpi();
        }
    }
}

class TruffleLanguageSnippets {
    class Context {
        String[] args;
        Env env;
        CallTarget mul;
        InputStream in;
        OutputStream out;
        OutputStream err;
        String languageVersion;

        Context(String[] args) {
            this.args = args;
            this.env = null;
            this.languageVersion = null;
        }

        Context(Env env) {
            this.env = env;
            this.args = null;
            this.languageVersion = null;
        }

        Context(Env env, String languageVersion) {
            this.env = env;
            this.args = null;
            this.languageVersion = languageVersion;
        }

        Context fork() {
            return null;
        }

        final Assumption singleThreaded = Truffle.getRuntime().createAssumption();

    }

    // @formatter:off
    abstract
    class MyLanguage extends TruffleLanguage<Context> {
        @Override
        protected Context createContext(Env env) {
            String[] args = env.getApplicationArguments();
            return new Context(args);
        }
    }

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
            Source source = Source.newBuilder("js",
                                "function(x, y) x * y",
                                "mul.js").build();
            context.mul = context.env.parse(source);
        }
    }
    // END: TruffleLanguageSnippets.PostInitLanguage#createContext

    abstract static
    // BEGIN: TruffleLanguageSnippets.CompatibleLanguage#areOptionsCompatible
    class CompatibleLanguage extends TruffleLanguage<Env> {

        @Option(help = "", category = OptionCategory.USER)
        static final OptionKey<String> ScriptVersion
                    = new OptionKey<>("ECMA2017");

        @Override
        protected boolean areOptionsCompatible(OptionValues firstOptions,
                        OptionValues newOptions) {
            return firstOptions.get(ScriptVersion).
                            equals(newOptions.get(ScriptVersion));
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new CompatibleLanguageOptionDescriptors();
        }
    }
    // END: TruffleLanguageSnippets.CompatibleLanguage#areOptionsCompatible

    static class CompatibleLanguageOptionDescriptors implements OptionDescriptors{

        public OptionDescriptor get(String optionName) {
            return null;
        }

        public Iterator<OptionDescriptor> iterator() {
            return null;
        }
    }

    abstract
    class PreInitializedLanguage extends TruffleLanguage<Context> {

        private final OptionKey<String> version = new OptionKey<>("2.0");

        // BEGIN: TruffleLanguageSnippets.PreInitializedLanguage#patchContext
        @Override
        protected boolean patchContext(Context context, Env newEnv) {
            if (!optionsAllowPreInitializedContext(context, newEnv)) {
                // Incompatible options - cannot use pre-initialized context
                return false;
            }
            context.env = newEnv;
            context.args = newEnv.getApplicationArguments();
            context.in = newEnv.in();
            context.out = newEnv.out();
            context.err = newEnv.err();
            return true;
        }

        private boolean optionsAllowPreInitializedContext(Context context, Env newEnv) {
            // Verify that values of important options in the new Env do not differ
            // from values in the pre-initialized context
            final String newVersionValue = newEnv.getOptions().get(version);
            return Objects.equals(context.languageVersion, newVersionValue);
        }
        // END: TruffleLanguageSnippets.PreInitializedLanguage#patchContext
    }

    // BEGIN: TruffleLanguageSnippets#parseWithParams
    public void parseWithParams(Env env) {
        Source multiply = Source.newBuilder("js",
                        "a * b",
                        "mul.js").build();
        CallTarget method = env.parse(multiply, "a", "b");
        Number fortyTwo = (Number) method.call(6, 7);
        assert 42 == fortyTwo.intValue();
        Number ten = (Number) method.call(2, 5);
        assert 10 == ten.intValue();
    }
    // END: TruffleLanguageSnippets#parseWithParams


    abstract
    // BEGIN: TruffleLanguageSnippets.MultiThreadedLanguage#initializeThread
    class MultiThreadedLanguage extends TruffleLanguage<Context> {

        @Override
        protected Context createContext(Env env) {
            return new Context(env);
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread,
                        boolean singleThreaded) {
            // allow access from any thread instead of just one
            return true;
        }

        @Override
        protected void initializeMultiThreading(Context context) {
            // perform actions when the context is switched to multi-threading
            context.singleThreaded.invalidate();
        }

        @Override
        protected void initializeThread(Context context, Thread thread) {
            // perform initialization actions for threads
        }

        @Override
        protected void disposeThread(Context context, Thread thread) {
            // perform disposal actions for threads
        }
    }
    // END: TruffleLanguageSnippets.MultiThreadedLanguage#initializeThread



}
