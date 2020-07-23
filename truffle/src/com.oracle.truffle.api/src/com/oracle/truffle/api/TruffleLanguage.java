/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.FileSystem;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile.FileSystemContext;
import com.oracle.truffle.api.TruffleFile.FileTypeDetector;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.ReadOnlyArrayList;
import com.oracle.truffle.api.io.TruffleProcessBuilder;
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
    @CompilationFinal LanguageInfo languageInfo;
    @CompilationFinal ContextReference<Object> reference;
    @CompilationFinal Object polyglotLanguageInstance;

    /**
     * Constructor to be called by subclasses. Language should not create any {@link RootNode}s in
     * its constructor. The RootNodes created in the language constructor are not associated with a
     * Context and they don't respect Context's engine options. The needed RootNodes can be created
     * in the {@link #createContext(Env)}.
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
         * @since 19.0
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
         * @since 19.0
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
         * @since 19.0
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
         * Dependent languages should be parsed with {@link Env#parseInternal(Source, String...)} as
         * the embedder might choose to disable access to it for
         * {@link Env#parsePublic(Source, String...)}.
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
         * @since 19.0
         */
        ContextPolicy contextPolicy() default ContextPolicy.EXCLUSIVE;

        /**
         * Declarative list of classes this language is known to provide. The language is supposed
         * to override its {@link #createContext(com.oracle.truffle.api.TruffleLanguage.Env)
         * createContext} method and instantiate and {@link Env#registerService(java.lang.Object)
         * register} all here in defined services.
         * <p>
         * Languages automatically get created but not yet initialized when their registered
         * {@link Env#lookup(com.oracle.truffle.api.nodes.LanguageInfo, java.lang.Class) service is
         * requested}.
         *
         * @since 19.0
         * @return list of service types that this language can provide
         */
        Class<?>[] services() default {};

        /**
         * Declarative list of {@link TruffleFile.FileTypeDetector} classes provided by this
         * language.
         * <p>
         * The language has to support all MIME types recognized by the registered
         * {@link TruffleFile.FileTypeDetector file type detectors}.
         *
         * @return list of file type detectors
         * @since 19.0
         */
        Class<? extends TruffleFile.FileTypeDetector>[] fileTypeDetectors() default {};
    }

    /**
     * Used to register a {@link TruffleLanguage} using a {@link ServiceLoader}. This interface is
     * not intended to be implemented directly by a language developer, rather the implementation is
     * generated by the Truffle DSL. The generated implementation has to inherit the
     * {@link Registration} and {@code ProvidedTags} annotations from the {@link TruffleLanguage}.
     *
     * @since 19.3.0
     */
    public interface Provider {

        /**
         * Returns the name of a class implementing the {@link TruffleLanguage}.
         *
         * @since 19.3.0
         */
        String getLanguageClassName();

        /**
         * Creates a new instance of a {@link TruffleLanguage}.
         *
         * @since 19.3.0
         */
        TruffleLanguage<?> create();

        /**
         * Creates file type detectors used by the {@link TruffleLanguage}.
         *
         * @since 19.3.0
         */
        List<FileTypeDetector> createFileTypeDetectors();

        /**
         * Returns the class names of provided services.
         *
         * @since 19.3.0
         */
        Collection<String> getServicesClassNames();
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
     * @since 19.0
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
     * The context created by this method is accessible using {@link #getCurrentContext(Class)}. An
     * {@link IllegalStateException} is thrown if the context is tried to be accessed while the
     * createContext method is executed.
     * <p>
     * This method shouldn't perform any complex operations. The runtime system is just being
     * initialized and for example making
     * {@link Env#parsePublic(com.oracle.truffle.api.source.Source, java.lang.String...) calls into
     * other languages} and assuming your language is already initialized and others can see it
     * would be wrong - until you return from this method, the initialization isn't over. The same
     * is true for instrumentation, the instruments cannot receive any meta data about code executed
     * during context creation. Should there be a need to perform complex initialization, do it by
     * overriding the {@link #initializeContext(java.lang.Object)} method.
     * <p>
     * Additional services provided by the language must be
     * {@link Env#registerService(java.lang.Object) registered} by this method otherwise
     * {@link IllegalStateException} is thrown.
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
     * <p>
     * All threads {@link Env#createThread(Runnable) created} by a language must be stopped and
     * joined during finalizeContext. The languages are responsible for fulfilling that contract,
     * otherwise an {@link AssertionError} is thrown. It's not safe to use the
     * {@link ExecutorService#awaitTermination(long, java.util.concurrent.TimeUnit)} to detect
     * Thread termination as the polyglot thread may be cancelled before executing the executor
     * worker.
     * <p>
     * Typical implementation looks like:
     *
     * {@link TruffleLanguageSnippets.AsyncThreadLanguage#finalizeContext}
     *
     * @see Registration#dependentLanguages() for specifying language dependencies.
     * @param context the context created by
     *            {@link #createContext(com.oracle.truffle.api.TruffleLanguage.Env)}
     * @since 0.30
     */
    protected void finalizeContext(C context) {
    }

    /**
     * @since 19.0
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
     * @since 19.0
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
     * called on all languages whose contexts were created during the pre-initialization a
     * consequence of {@link org.graalvm.polyglot.Context#create(java.lang.String...)} invocation.
     * The contexts are patched in a topological order starting from dependent languages. If the
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
        private final Source source;
        private final String[] argumentNames;
        private boolean disposed;

        ParsingRequest(Source source, String... argumentNames) {
            Objects.requireNonNull(source);
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
     * with. If the thread is stored in the context it must be referenced using
     * {@link WeakReference} to avoid leaking thread objects.
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
     * thread} may differ from the disposed thread. Disposal of threads is only guaranteed for
     * threads that were created by guest languages, so called {@link Env#createThread(Runnable)
     * polyglot threads}. Other threads, created by the embedder, may be collected by the garbage
     * collector before they can be disposed and may therefore not be disposed.
     *
     * @see #initializeThread(Object, Thread) For usage details.
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
     * @deprecated implement {@link #getLanguageView(Object, Object)} and export
     *             {@link com.oracle.truffle.api.interop.InteropLibrary#getLanguage(Object)}
     *             instead.
     */
    @Deprecated
    protected boolean isObjectOfLanguage(Object object) {
        return false;
    }

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
        return LanguageAccessor.engineAccess().createDefaultLexicalScope(node, frame);
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
        return LanguageAccessor.engineAccess().createDefaultTopScope(global);
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
     * @deprecated implement {@link #getLanguageView(Object, Object)} and
     *             {@link com.oracle.truffle.api.interop.InteropLibrary#toDisplayString(Object)}
     *             instead.
     */
    @Deprecated
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
     * visible, it gets converted to string and printed to
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
     * Wraps the value to provide language-specific information for primitive and foreign values.
     * Foreign values should be enhanced to look like the most generic object type of the language.
     * The wrapper needs to introduce any "virtual" methods and properties that are commonly used in
     * language constructs and in algorithms that are written to work on this generic object type.
     * The wrapper may add or remove existing interop traits, but it is not allowed to change the
     * {@link com.oracle.truffle.api.interop.InteropLibrary interop type}. For example, it is not
     * allowed to change the type from number to string. If the behavior of an existing trait is
     * modified then all writes on the mapper need to be forwarded to the underlying object, apart
     * from the virtual members. Writes to the virtual members should be persisted in the wrapper if
     * this is the behavior of the object type that is being mapped to.
     * <p>
     * Every language view wrapper must return the current language as their associated
     * {@link com.oracle.truffle.api.interop.InteropLibrary#getLanguage(Object) language}. An
     * {@link AssertionError} is thrown when a language view is requested if this contract is
     * violated.
     * <p>
     * Example modifications language view wrappers may perform:
     * <ul>
     * <li>Provide a language specific
     * {@link com.oracle.truffle.api.interop.InteropLibrary#toDisplayString(Object) display string}
     * for primitive and foreign values.
     * <li>Return a language specific
     * {@link com.oracle.truffle.api.interop.InteropLibrary#getMetaObject(Object) metaobject} for
     * primitive or foreign values.
     * <li>Add virtual members to the object for the view. For example, any JavaScript object is
     * expected to have an implicit __proto__ member. Foreign objects, even if they do not have such
     * a member, are interpreted as if they have.
     * <li>There are languages where all scalar values are also vectors. In such a case the array
     * element trait may be added using the language wrapper to such values.
     * </ul>
     * <p>
     * The default implementation returns <code>null</code>. If <code>null</code> is returned then
     * the default language view will be used. The default language view wraps the value and returns
     * the current language as their associated language. With the default view wrapper all interop
     * library messages will be forwarded to the delegate value, except the messages for
     * {@link com.oracle.truffle.api.interop.InteropLibrary#getMetaObject(Object) metaobjects} and
     * {@link com.oracle.truffle.api.interop.InteropLibrary#toDisplayString(Object) display strings}
     * .
     * <p>
     * This following example shows a simplified language view. For a full implementation including
     * an example of metaobjects can be found in the Truffle examples language "SimpleLanguage".
     *
     * <pre>
     * &#64;ExportLibrary(value = InteropLibrary.class, delegateTo = "delegate")
     * final class ExampleLanguageView implements TruffleObject {
     *
     *     protected final Object delegate;
     *
     *     ExampleLanguageView(Object delegate) {
     *         this.delegate = delegate;
     *     }
     *
     *     &#64;ExportMessage
     *     boolean hasLanguage() {
     *         return true;
     *     }
     *
     *     &#64;ExportMessage
     *     Class&lt;? extends TruffleLanguage&lt;?&gt;&gt; getLanguage() {
     *         return MyLanguage.class;
     *     }
     *
     *     &#64;ExportMessage
     *     Object toDisplayString(boolean allowSideEffects,
     *                     &#64;CachedLibrary("this.delegate") InteropLibrary dLib) {
     *         try {
     *             if (dLib.isString(this.delegate)) {
     *                 return dLib.asString(this.delegate);
     *             } else if (dLib.isBoolean(this.delegate)) {
     *                 return dLib.asBoolean(this.delegate) ? "TRUE" : "FALSE";
     *             } else if (dLib.fitsInLong(this.delegate)) {
     *                 return longToString(dLib.asLong(this.delegate));
     *             } else {
     *                 // full list truncated for this language
     *                 return "Unsupported value";
     *             }
     *         } catch (UnsupportedMessageException e) {
     *             CompilerDirectives.transferToInterpreter();
     *             throw new AssertionError(e);
     *         }
     *     }
     *
     *     &#64;TruffleBoundary
     *     private static String longToString(long value) {
     *         return String.valueOf(value);
     *     }
     *
     *     &#64;ExportMessage
     *     boolean hasMetaObject(@CachedLibrary("this.delegate") InteropLibrary dLib) {
     *         return dLib.isString(this.delegate)//
     *                         || dLib.fitsInLong(this.delegate)//
     *                         || dLib.isBoolean(this.delegate);
     *     }
     *
     *     &#64;ExportMessage
     *     Object getMetaObject(@CachedLibrary("this.delegate") InteropLibrary dLib)
     *                     throws UnsupportedMessageException {
     *         if (dLib.isString(this.delegate)) {
     *             return MyMetaObject.PRIMITIVE_STRING;
     *         } else if (dLib.isBoolean(this.delegate)) {
     *             return MyMetaObject.PRIMITIVE_LONG;
     *         } else if (dLib.fitsInLong(this.delegate)) {
     *             return MyMetaObject.PRIMITIVE_BOOLEAN;
     *         } else {
     *             // no associable metaobject
     *             throw UnsupportedMessageException.create();
     *         }
     *     }
     * }
     * </pre>
     *
     * @param context the current context.
     * @param value the value
     * @since 20.1
     */
    protected Object getLanguageView(C context, Object value) {
        return null;
    }

    /**
     * Wraps the value to filter or add scoping specific information for values associated with the
     * current language and location in the code. Allows the language to augment the perspective
     * tools have on values depending on location and frame. This may be useful to apply local
     * specific visibility rules. By default this method does return the passed value, not applying
     * any scope information to the value. If a language does not implement any scoping and/or has
     * not concept of visibility then this method typically can stay without an implementation. A
     * typical implementation of this method may do the following:
     * <ul>
     * <li>Apply visiblity and scoping rules to the value hiding or removing members from the
     * object.
     * <li>Add or remove implicit members that are only available within this source location.
     * </ul>
     * <p>
     * This method is only invoked with values that are associated with the current
     * {@link com.oracle.truffle.api.interop.InteropLibrary#getLanguage(Object) language}. For
     * values without language the {@link #getLanguageView(Object, Object) language view} is
     * requested first before the scoped view is requested. If this method needs an implementation
     * then {@link #getLanguageView(Object, Object)} should be implemented as well.
     * <p>
     * Scoped views may be implemented in a very similar way to
     * {@link #getLanguageView(Object, Object) language views}. Please refer to the examples from
     * this method.
     *
     * @param context the current context.
     * @param location the current source location. Guaranteed to be a node from a {@link RootNode}
     *            associated with this language. Never <code>null</code>.
     * @param frame the current active frame. Guaranteed to be a frame from a {@link RootNode}
     *            associated with this language. Never <code>null</code>.
     * @param value the value to provide scope information for. Never <code>null</code>. Always
     *            associated with this language.
     * @since 20.1
     */
    @SuppressWarnings("unused")
    protected Object getScopedView(C context, Node location, Frame frame, Object value) {
        return value;
    }

    /**
     * Find a metaobject of a value, if any. The metaobject represents a description of the object,
     * reveals it's kind and it's features. Some information that a metaobject might define includes
     * the base object's type, interface, class, methods, attributes, etc.
     * <p>
     * A programmatic {@link #toString(java.lang.Object, java.lang.Object) textual representation}
     * should be provided for metaobjects, when possible. The metaobject may have properties
     * describing their structure.
     * <p>
     * NOTE: Allocating the meta object must not be treated as or cause any
     * {@link com.oracle.truffle.api.instrumentation.AllocationListener reported guest language
     * value allocations}
     * <p>
     * When no metaobject is known, return <code>null</code>. The default implementation returns
     * <code>null</code>. The metaobject should be an interop value. An interop value can be either
     * a <code>TruffleObject</code> (e.g. a native object from the other language) to support
     * interoperability between languages or a {@link String}.
     * <p>
     * It can be beneficial for performance to return the same value for each guest type (i.e. cache
     * the metaobjects per context).
     *
     * @param context the execution context
     * @param value a value to find the metaobject of
     * @return the metaobject, or <code>null</code>
     * @since 0.22
     * @deprecated implement {@link #getLanguageView(Object, Object)} and export
     *             {@link com.oracle.truffle.api.interop.InteropLibrary#getMetaObject(Object)}
     *             instead.
     */
    @Deprecated
    protected Object findMetaObject(C context, Object value) {
        return null;
    }

    /**
     * Find a source location where a value is declared, if any. This is often useful especially for
     * retrieval of source locations of {@link #findMetaObject metaobjects}. The default
     * implementation returns <code>null</code>.
     *
     * @param context the execution context
     * @param value a value to get the source location for
     * @return a source location of the object, or <code>null</code>
     * @since 0.22
     * @deprecated implement {@link #getLanguageView(Object, Object)} and export
     *             {@link com.oracle.truffle.api.interop.InteropLibrary#getSourceLocation(Object)}
     *             instead.
     */
    @Deprecated
    protected SourceSection findSourceLocation(C context, Object value) {
        return null;
    }

    /**
     * @deprecated in 19.3 as this method is inefficient in many situations. The most efficient
     *             context lookup can be achieved knowing the current AST in which it is used. As
     *             this method does not know the current {@link Node node} it must be unnecessarily
     *             conservative about the lookup and therefore inefficient. More efficient context
     *             reference versions are available for fast-paths by calling
     *             {@link Node#lookupContextReference(Class)} or for slow-paths
     *             {@link TruffleLanguage#getCurrentContext(Class)}. Truffle DSL has support for
     *             context lookup with {@link com.oracle.truffle.api.dsl.CachedContext
     *             CachedContext} that uses the most efficient lookup automatically.
     *
     * @since 0.25
     */
    @SuppressWarnings("unchecked")
    @Deprecated
    public final ContextReference<C> getContextReference() {
        if (reference == null) {
            throw new IllegalStateException("TruffleLanguage instance is not initialized. Cannot get the current context reference.");
        }
        return (ContextReference<C>) reference;
    }

    CallTarget parse(Source source, String... argumentNames) {
        ParsingRequest request = new ParsingRequest(source, argumentNames);
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
     * Returns the current language instance for the current {@link Thread thread}. If a {@link Node
     * node} is accessible then {@link Node#lookupLanguageReference(Class)} should be used instead.
     * Throws an {@link IllegalStateException} if the language is not yet initialized or not
     * executing on this thread. If invoked on the fast-path then <code>languageClass</code> must be
     * a compilation final value.
     *
     * @param <T> the language type
     * @param languageClass the exact language class needs to be provided for the lookup.
     * @see Node#lookupLanguageReference(Class)
     * @see com.oracle.truffle.api.dsl.CachedLanguage
     * @since 0.27
     */
    protected static <T extends TruffleLanguage<?>> T getCurrentLanguage(Class<T> languageClass) {
        try {
            return LanguageAccessor.engineAccess().getCurrentLanguage(languageClass);
        } catch (Throwable t) {
            CompilerDirectives.transferToInterpreter();
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * Returns the current language context entered on the current thread. If a {@link Node node} is
     * accessible then {@link Node#lookupContextReference(Class)} should be used instead. An
     * {@link IllegalStateException} is thrown if the language is not yet initialized or not
     * executing on this thread. If invoked on the fast-path then <code>languageClass</code> must be
     * a compilation final value.
     *
     * @param <C> the context type
     * @param <T> the language type
     * @param languageClass the exact language class needs to be provided for the lookup.
     * @see Node#lookupContextReference(Class)
     * @see com.oracle.truffle.api.dsl.CachedContext
     * @since 0.27
     */
    protected static <C, T extends TruffleLanguage<C>> C getCurrentContext(Class<T> languageClass) {
        try {
            return LanguageAccessor.engineAccess().getCurrentContext(languageClass);
        } catch (Throwable t) {
            CompilerDirectives.transferToInterpreter();
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * Returns the home location for this language. This corresponds to the directory in which the
     * Jar file is located, if run from a Jar file. For an AOT compiled binary, this corresponds to
     * the location of the language files in the default GraalVM distribution layout. executable or
     * shared library.
     *
     * @since 19.0
     */
    protected final String getLanguageHome() {
        try {
            return LanguageAccessor.engineAccess().getLanguageHome(LanguageAccessor.nodesAccess().getPolyglotLanguage(languageInfo));
        } catch (Throwable t) {
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * Get the depth of asynchronous stack. When zero, the language should not sacrifice performance
     * to be able to provide asynchronous stack. When the depth is non-zero, the language should
     * provide asynchronous stack up to that depth. The language may provide more asynchronous
     * frames than this depth if it's of no performance penalty, or if requested by other (e.g.
     * language-specific) options. The returned depth may change at any time.
     * <p>
     * Override {@link RootNode#findAsynchronousFrames(Frame)} to provide the asynchronous stack
     * frames.
     *
     * @see RootNode#findAsynchronousFrames(Frame)
     * @since 20.1.0
     */
    protected final int getAsynchronousStackDepth() {
        return LanguageAccessor.engineAccess().getAsynchronousStackDepth(LanguageAccessor.nodesAccess().getPolyglotLanguage(languageInfo));
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

        static final Object UNSET_CONTEXT = new Object();
        final Object polyglotLanguageContext; // PolylgotLanguageContext
        final TruffleLanguage<Object> spi;
        private final InputStream in;
        private final OutputStream err;
        private final OutputStream out;
        private final Map<String, Object> config;
        private final OptionValues options;
        private final String[] applicationArguments;

        @CompilationFinal volatile List<Object> services;

        @CompilationFinal volatile Object context = UNSET_CONTEXT;
        @CompilationFinal volatile Assumption contextUnchangedAssumption = Truffle.getRuntime().createAssumption("Language context unchanged");
        @CompilationFinal volatile boolean initialized = false;
        @CompilationFinal private volatile Assumption initializedUnchangedAssumption = Truffle.getRuntime().createAssumption("Language context initialized unchanged");
        @CompilationFinal volatile boolean valid;
        volatile List<Object> languageServicesCollector;

        @SuppressWarnings("unchecked")
        Env(Object polyglotLanguageContext, TruffleLanguage<?> language, OutputStream out, OutputStream err, InputStream in, Map<String, Object> config, OptionValues options,
                        String[] applicationArguments) {
            this.polyglotLanguageContext = polyglotLanguageContext;
            this.spi = (TruffleLanguage<Object>) language;
            this.in = in;
            this.err = err;
            this.out = out;
            this.config = config;
            this.options = options;
            this.applicationArguments = applicationArguments == null ? new String[0] : applicationArguments;
            this.valid = true;
        }

        TruffleFile.FileSystemContext getPublicFileSystemContext() {
            return (TruffleFile.FileSystemContext) LanguageAccessor.engineAccess().getPublicFileSystemContext(polyglotLanguageContext);
        }

        TruffleFile.FileSystemContext getInternalFileSystemContext() {
            return (TruffleFile.FileSystemContext) LanguageAccessor.engineAccess().getInternalFileSystemContext(polyglotLanguageContext);
        }

        Object getPolyglotLanguageContext() {
            return polyglotLanguageContext;
        }

        TruffleLanguage<Object> getSpi() {
            return spi;
        }

        void checkDisposed() {
            if (LanguageAccessor.engineAccess().isDisposed(polyglotLanguageContext)) {
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
            try {
                return LanguageAccessor.engineAccess().isCreateThreadAllowed(polyglotLanguageContext);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Creates a new thread that has access to the current language context. See
         * {@link #createThread(Runnable, TruffleContext, ThreadGroup, long)} for a detailed
         * description of the parameters. The <code>group</code> is null and <code>stackSize</code>
         * set to 0.
         *
         * @since 0.28
         */
        @TruffleBoundary
        public Thread createThread(Runnable runnable) {
            return createThread(runnable, null);
        }

        /**
         * Creates a new thread that has access to the given context. See
         * {@link #createThread(Runnable, TruffleContext, ThreadGroup, long)} for a detailed
         * description of the parameters. The <code>group</code> is null and <code>stackSize</code>
         * set to 0.
         *
         * @see #getContext()
         * @see #newContextBuilder()
         * @since 0.28
         */
        @TruffleBoundary
        public Thread createThread(Runnable runnable, @SuppressWarnings("hiding") TruffleContext context) {
            return createThread(runnable, context, null, 0);
        }

        /**
         * Creates a new thread that has access to the given context. See
         * {@link #createThread(Runnable, TruffleContext, ThreadGroup, long)} for a detailed
         * description of the parameters. The <code>stackSize</code> set to 0.
         *
         * @see #getContext()
         * @see #newContextBuilder()
         * @since 0.28
         */
        @TruffleBoundary
        public Thread createThread(Runnable runnable, @SuppressWarnings("hiding") TruffleContext context, ThreadGroup group) {
            return createThread(runnable, context, group, 0);
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
         * @param runnable the runnable to run on this thread.
         * @param context the context to enter and leave when the thread is started.
         * @param group the thread group, passed on to the underlying {@link Thread}.
         * @param stackSize the desired stack size for the new thread, or zero if this parameter is
         *            to be ignored.
         * @throws IllegalStateException if thread creation is not {@link #isCreateThreadAllowed()
         *             allowed}.
         * @see #getContext()
         * @see #newContextBuilder()
         * @since 0.28
         */
        @TruffleBoundary
        public Thread createThread(Runnable runnable, @SuppressWarnings("hiding") TruffleContext context, ThreadGroup group, long stackSize) {
            try {
                return LanguageAccessor.engineAccess().createThread(polyglotLanguageContext, runnable, context != null ? context.polyglotContext : null, group, stackSize);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
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
         * @throws SecurityException if polyglot access is not enabled
         * @see #isPolyglotBindingsAccessAllowed()
         * @since 0.32
         */
        @TruffleBoundary
        public Object getPolyglotBindings() {
            try {
                if (!isPolyglotBindingsAccessAllowed()) {
                    throw new SecurityException("Polyglot bindings are not accessible for this language. Use --polyglot or allowPolyglotAccess when building the context.");
                }
                return LanguageAccessor.engineAccess().getPolyglotBindingsForLanguage(polyglotLanguageContext);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
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
         * Polyglot symbols can only be imported if the {@link #isPolyglotBindingsAccessAllowed()
         * polyglot bindings access} is allowed.
         *
         * @param symbolName the name of the symbol to search for
         * @return object representing the symbol or <code>null</code> if it does not exist
         * @throws SecurityException if importing polyglot symbols is not allowed
         * @since 0.8 or earlier
         */
        @TruffleBoundary
        public Object importSymbol(String symbolName) {
            try {
                if (!isPolyglotBindingsAccessAllowed()) {
                    throw new SecurityException("Polyglot bindings are not accessible for this language. Use --polyglot or allowPolyglotAccess when building the context.");
                }
                return LanguageAccessor.engineAccess().importSymbol(polyglotLanguageContext, this, symbolName);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
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
         * <p>
         * Polyglot symbols can only be export if the {@link #isPolyglotBindingsAccessAllowed()
         * polyglot bindings access} is allowed.
         *
         * @param symbolName the name with which the symbol should be exported into the polyglot
         *            scope
         * @param value the value to export for
         * @throws SecurityException if exporting polyglot symbols is not allowed
         * @since 0.27
         */
        @TruffleBoundary
        public void exportSymbol(String symbolName, Object value) {
            try {
                if (!isPolyglotBindingsAccessAllowed()) {
                    throw new SecurityException("Polyglot bindings are not accessible for this language. Use --polyglot or allowPolyglotAccess when building the context.");
                }
                LanguageAccessor.engineAccess().exportSymbol(polyglotLanguageContext, symbolName, value);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Returns <code>true</code> if host access is generally allowed. If this method returns
         * <code>false</code> then {@link #lookupHostSymbol(String)} will always fail. Host lookup
         * is generally disallowed if the embedder provided a null
         * {@link org.graalvm.polyglot.Context.Builder#allowHostClassLookup(java.util.function.Predicate)
         * host class filter}.
         *
         * @since 0.27
         */
        @TruffleBoundary
        public boolean isHostLookupAllowed() {
            try {
                return LanguageAccessor.engineAccess().isHostAccessAllowed(polyglotLanguageContext, this);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Adds an entry to the Java host class loader. All classes looked up with
         * {@link #lookupHostSymbol(String)} will lookup classes with this new entry. If the entry
         * was already added then calling this method again for the same entry has no effect. Given
         * entry must not be <code>null</code>.
         *
         * @throws SecurityException if the file is not {@link TruffleFile#isReadable() readable}.
         * @since 19.0
         */
        @TruffleBoundary
        public void addToHostClassPath(TruffleFile entry) {
            try {
                Objects.requireNonNull(entry);
                LanguageAccessor.engineAccess().addToHostClassPath(polyglotLanguageContext, entry);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Looks up a Java class in the top-most scope the host environment. Throws an error if no
         * symbol was found or the symbol was not accessible. Symbols might not be accessible if a
         * {@link org.graalvm.polyglot.Context.Builder#allowHostClassLookup(java.util.function.Predicate)
         * host class filter} prevents access. The returned object is always a
         * <code>TruffleObject</code> that represents the class symbol.
         *
         * @param symbolName the qualified class name in the host language.
         * @since 0.27
         */
        @TruffleBoundary
        public Object lookupHostSymbol(String symbolName) {
            try {
                return LanguageAccessor.engineAccess().lookupHostSymbol(polyglotLanguageContext, this, symbolName);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Returns <code>true</code> if the argument is Java host language object wrapped using
         * Truffle interop.
         *
         * @see #asHostObject(Object)
         * @since 19.0
         */
        @SuppressWarnings("static-method")
        public boolean isHostObject(Object value) {
            try {
                return LanguageAccessor.engineAccess().isHostObject(value);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Returns the java host representation of a Truffle guest object if it represents a Java
         * host language object. Throws {@link ClassCastException} if the provided argument is not a
         * {@link #isHostObject(Object) host object}.
         *
         * @since 19.0
         */
        public Object asHostObject(Object value) {
            if (!isHostObject(value)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new ClassCastException();
            }
            try {
                return LanguageAccessor.engineAccess().asHostObject(value);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
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
         * {@link org.graalvm.polyglot.Context.Builder#allowHostClassLookup(java.util.function.Predicate)
         * class filters}.
         *
         * @param hostObject the host object to convert
         * @since 19.0
         */
        public Object asGuestValue(Object hostObject) {
            try {
                return LanguageAccessor.engineAccess().toGuestValue(hostObject, polyglotLanguageContext);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
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
         * @since 19.0
         */
        public Object asBoxedGuestValue(Object guestObject) {
            try {
                return LanguageAccessor.engineAccess().asBoxedGuestValue(guestObject, polyglotLanguageContext);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Returns <code>true</code> if the argument is a Java host language function wrapped using
         * Truffle interop.
         *
         * @since 19.0
         */
        @SuppressWarnings("static-method")
        public boolean isHostFunction(Object value) {
            try {
                return LanguageAccessor.engineAccess().isHostFunction(value);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Find a metaobject of a value, if any. The metaobject represents a description of the
         * object, reveals it's kind and it's features. Some information that a metaobject might
         * define includes the base object's type, interface, class, methods, attributes, etc.
         * <p>
         * When no metaobject is known, returns <code>null</code>. The metaobject is an interop
         * value. An interop value can be either a <code>TruffleObject</code> (e.g. a native object
         * from the other language) to support interoperability between languages or a
         * {@link String}.
         *
         * @param value the value to find the meta object for.
         * @since 19.0
         */
        public Object findMetaObject(Object value) {
            try {
                return LanguageAccessor.engineAccess().findMetaObjectForLanguage(polyglotLanguageContext, value);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Tests whether an exception is a host exception thrown by a Java Interop method
         * invocation.
         *
         * Host exceptions may be thrown by interoperability messages. The host exception may be
         * unwrapped using {@link #asHostException(Throwable)}.
         *
         * @param exception the {@link Throwable} to test
         * @return {@code true} if the {@code exception} is a host exception, {@code false}
         *         otherwise
         * @see #asHostException(Throwable)
         * @since 19.0
         */
        @SuppressWarnings("static-method")
        public boolean isHostException(Throwable exception) {
            try {
                return LanguageAccessor.engineAccess().isHostException(exception);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Unwraps a host exception thrown by a Java method invocation.
         *
         * Host exceptions may be thrown by interoperability messages. The host exception may be
         * unwrapped using {@link #asHostException(Throwable)}.
         *
         * @param exception the host exception to unwrap
         * @return the original Java exception
         * @throws IllegalArgumentException if the {@code exception} is not a host exception
         * @see #isHostException(Throwable)
         * @since 19.0
         */
        @SuppressWarnings("static-method")
        public Throwable asHostException(Throwable exception) {
            try {
                return LanguageAccessor.engineAccess().asHostException(exception);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Returns {@code true} if the argument is a host symbol, representing the constructor and
         * static members of a Java class, as obtained by e.g. {@link #lookupHostSymbol}.
         *
         * @see #lookupHostSymbol(String)
         * @since 19.0
         */
        @SuppressWarnings("static-method")
        public boolean isHostSymbol(Object guestObject) {
            try {
                return LanguageAccessor.engineAccess().isHostSymbol(guestObject);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Converts a Java class to a host symbol as if by
         * {@code lookupHostSymbol(symbolClass.getName())} but without an actual lookup. Must not be
         * used with Truffle or guest language classes.
         *
         * @see #lookupHostSymbol(String)
         * @since 19.0
         */
        @TruffleBoundary
        public Object asHostSymbol(Class<?> symbolClass) {
            try {
                return LanguageAccessor.engineAccess().asHostSymbol(polyglotLanguageContext, symbolClass);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Returns <code>true</code> if access to native code is generally allowed. If this method
         * returns <code>false</code> then loading native libraries with the Truffle NFI will fail.
         *
         * @since 19.0
         */
        @TruffleBoundary
        public boolean isNativeAccessAllowed() {
            try {
                return LanguageAccessor.engineAccess().isNativeAccessAllowed(polyglotLanguageContext, this);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * @since 19.0
         * @deprecated use either {@link #isPolyglotEvalAllowed()} or
         *             {@link #isPolyglotBindingsAccessAllowed()} instead
         */
        @Deprecated
        public boolean isPolyglotAccessAllowed() {
            return isPolyglotEvalAllowed() || isPolyglotBindingsAccessAllowed();
        }

        /**
         * Returns <code>true</code> if polyglot evaluation is allowed, else <code>false</code>.
         * Guest languages should hide or disable all polyglot evaluation builtins if this flag is
         * set to <code>false</code>. Note that if polyglot evaluation access is disabled, then the
         * {@link #getInternalLanguages() available languages list} only shows the current language,
         * {@link Registration#dependentLanguages() dependent languages} and
         * {@link Registration#internal() internal languages}.
         *
         * @see org.graalvm.polyglot.Context.Builder#allowPolyglotAccess(org.graalvm.polyglot.PolyglotAccess)
         * @since 19.2
         */
        @TruffleBoundary
        public boolean isPolyglotEvalAllowed() {
            try {
                return LanguageAccessor.engineAccess().isPolyglotEvalAllowed(polyglotLanguageContext);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Returns <code>true</code> if polyglot bindings access is allowed, else <code>false</code>
         * . Guest languages should hide or disable all polyglot bindings builtins if this flag is
         * set to <code>false</code>. If polyglot bindings access is disabled then
         * {@link #getPolyglotBindings()}, {@link #importSymbol(String)} or
         * {@link #exportSymbol(String, Object)} fails with a SecurityException.
         *
         * @see org.graalvm.polyglot.Context.Builder#allowPolyglotAccess(org.graalvm.polyglot.PolyglotAccess)
         * @since 19.2
         */
        @TruffleBoundary
        public boolean isPolyglotBindingsAccessAllowed() {
            try {
                return LanguageAccessor.engineAccess().isPolyglotBindingsAccessAllowed(polyglotLanguageContext);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Allows it to be determined if this {@link org.graalvm.polyglot.Context} can execute code
         * written in a language with a given MIME type.
         *
         * @see Source#getMimeType()
         * @see #parsePublic(Source, String...)
         *
         * @return a boolean that indicates if the MIME type is supported
         * @since 0.11
         */
        @TruffleBoundary
        public boolean isMimeTypeSupported(String mimeType) {
            checkDisposed();
            try {
                return LanguageAccessor.engineAccess().isMimeTypeSupported(polyglotLanguageContext, mimeType);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * @throws IllegalStateException if polyglot context associated with this environment is not
         *             entered
         * @since 0.8 or earlier
         * @deprecated use {@link #parseInternal(Source, String...)} or
         *             {@link #parsePublic(Source, String...)} instead.
         */
        @TruffleBoundary
        @Deprecated
        public CallTarget parse(Source source, String... argumentNames) {
            CompilerAsserts.neverPartOfCompilation();
            checkDisposed();
            try {
                return LanguageAccessor.engineAccess().parseForLanguage(polyglotLanguageContext, source, argumentNames, true);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Parses the source of a public or internal language and returns the parse result as
         * {@link CallTarget}. The {@link Source#getLanguage() language id} is used to identify the
         * {@link TruffleLanguage} to use to perform the
         * {@link #parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest)}. The names of
         * arguments are parameters for the resulting {#link CallTarget} that allow the
         * <code>source</code> to reference the actual parameters passed to
         * {@link CallTarget#call(java.lang.Object...)}.
         * <p>
         * Compared to {@link #parsePublic(Source, String...)} this method provides also access to
         * {@link TruffleLanguage.Registration#internal() internal} and dependent languages in
         * addition to public languages. For example, in JavaScript, a call to the eval builtin
         * should forward to {@link #parsePublic(Source, String...)} as it contains code provided by
         * the guest language user. Parsing regular expressions with the internal regular expression
         * engine should call {@link #parseInternal(Source, String...)} instead, as this is
         * considered an implementation detail of the language.
         * <p>
         * It is recommended that the language uses {@link Env#parsePublic(Source, String...)} or
         * {@link Env#parseInternal(Source, String...)} instead of directly passing the Source to
         * the parser, in order to support code caching with {@link ContextPolicy#SHARED} and
         * {@link ContextPolicy#REUSE}.
         *
         * @param source the source to evaluate
         * @param argumentNames the names of {@link CallTarget#call(java.lang.Object...)} arguments
         *            that can be referenced from the source
         * @return the call target representing the parsed result
         * @throws IllegalStateException if polyglot context associated with this environment is not
         *             entered
         * @see #parsePublic(Source, String...)
         * @since 19.2
         */
        @TruffleBoundary
        public CallTarget parseInternal(Source source, String... argumentNames) {
            CompilerAsserts.neverPartOfCompilation();
            checkDisposed();
            try {
                return LanguageAccessor.engineAccess().parseForLanguage(polyglotLanguageContext, source, argumentNames, true);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Parses the source of a public language and returns the parse result as {@link CallTarget}
         * . The {@link Source#getLanguage() language id} is used to identify the
         * {@link TruffleLanguage} to use to perform the
         * {@link #parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest)}. The names of
         * arguments are parameters for the resulting {#link CallTarget} that allow the
         * <code>source</code> to reference the actual parameters passed to
         * {@link CallTarget#call(java.lang.Object...)}.
         * <p>
         * Compared to {@link #parseInternal(Source, String...)} this method does only provide
         * access to non internal, non dependent, public languages. Public languages are configured
         * by the embedder to be accessible to the guest language program. For example, in
         * JavaScript, a call to the eval builtin should forward to
         * {@link #parsePublic(Source, String...)} as it contains code provided by the guest
         * language user. Parsing regular expressions with the internal regular expression engine
         * should call {@link #parseInternal(Source, String...)} instead, as this is considered an
         * implementation detail of the language.
         * <p>
         * It is recommended that the language uses {@link Env#parsePublic(Source, String...)} or
         * {@link Env#parseInternal(Source, String...)} instead of directly passing the Source to
         * the parser, in order to support code caching with {@link ContextPolicy#SHARED} and
         * {@link ContextPolicy#REUSE}.
         *
         * @param source the source to evaluate
         * @param argumentNames the names of {@link CallTarget#call(java.lang.Object...)} arguments
         *            that can be referenced from the source
         * @return the call target representing the parsed result
         * @throws IllegalStateException if polyglot context associated with this environment is not
         *             entered
         * @see #parseInternal(Source, String...)
         * @since 19.2
         */
        @TruffleBoundary
        public CallTarget parsePublic(Source source, String... argumentNames) {
            CompilerAsserts.neverPartOfCompilation();
            checkDisposed();
            try {
                return LanguageAccessor.engineAccess().parseForLanguage(polyglotLanguageContext, source, argumentNames, false);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
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
            if (isPreInitialization()) {
                throw new IllegalStateException("Instrument lookup is not allowed during context pre-initialization.");
            }
            try {
                return LanguageAccessor.engineAccess().lookup(instrument, type);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Returns an additional service provided by the given language, specified by type. For
         * services registered by {@link Registration#services()} the service lookup will ensure
         * that the language is loaded and services are registered. The provided langauge and type
         * must not be <code>null</code>.
         *
         * @param <S> the requested type
         * @param language the language to query
         * @param type the class of the requested type
         * @return the registered service or <code>null</code> if none is found
         * @since 0.26
         * @since 19.0 supports services registered by {@link Env#registerService(java.lang.Object)
         *        registerService}
         */
        @TruffleBoundary
        public <S> S lookup(LanguageInfo language, Class<S> type) {
            if (this.getSpi().languageInfo == language) {
                throw new IllegalArgumentException("Cannot request services from the current language.");
            }
            try {
                Objects.requireNonNull(language);
                return LanguageAccessor.engineAccess().lookupService(polyglotLanguageContext, language, this.getSpi().languageInfo, type);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Ensures that the target language is initialized. This method firstly verifies that the
         * target language is accessible from this language. If not the {@link SecurityException} is
         * thrown. Then the target language initialization is performed if not already done.
         *
         * @param targetLanguage the language to initialize
         * @throws SecurityException if an access to {@code targetLanguage} is not permitted
         * @since 20.1.0
         */
        @TruffleBoundary
        public boolean initializeLanguage(LanguageInfo targetLanguage) {
            Objects.requireNonNull(targetLanguage, "TargetLanguage must be non null.");
            try {
                return LanguageAccessor.engineAccess().initializeLanguage(polyglotLanguageContext, targetLanguage);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * @since 0.26
         * @deprecated
         */
        @Deprecated
        @TruffleBoundary
        public Map<String, LanguageInfo> getLanguages() {
            try {
                return LanguageAccessor.engineAccess().getInternalLanguages(polyglotLanguageContext);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Returns all languages that are installed and internally accessible in the environment.
         * Using the language instance additional services can be
         * {@link #lookup(LanguageInfo, Class) looked up}. {@link #parseInternal(Source, String...)}
         * is allowed for all languages returned by this method. This list of languages should not
         * be exposed to guest language programs, as it lists internal languages.
         *
         * @see #lookup(LanguageInfo, Class)
         * @see #parseInternal(Source, String...)
         * @since 19.2
         */
        @TruffleBoundary
        public Map<String, LanguageInfo> getInternalLanguages() {
            try {
                return LanguageAccessor.engineAccess().getInternalLanguages(polyglotLanguageContext);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Returns all languages that are installed and publicly accessible in the environment.
         * Using the language instance additional services can be
         * {@link #lookup(LanguageInfo, Class) looked up}. {@link #parsePublic(Source, String...)}
         * is allowed for all languages returned by this method. This list of languages may be
         * exposed ot the guest language program.
         *
         * @see #parsePublic(Source, String...)
         * @since 19.2
         */
        @TruffleBoundary
        public Map<String, LanguageInfo> getPublicLanguages() {
            try {
                return LanguageAccessor.engineAccess().getPublicLanguages(polyglotLanguageContext);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
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
            try {
                return LanguageAccessor.engineAccess().getInstruments(polyglotLanguageContext);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Returns the default time zone of this environment. If the time-zone was not explicitly
         * set by the embedder then the {@link ZoneId#systemDefault() system default} time-zone will
         * be returned.
         *
         * @see ZoneId#systemDefault()
         * @since 19.2
         */
        public ZoneId getTimeZone() {
            checkDisposed();
            try {
                return LanguageAccessor.engineAccess().getTimeZone(polyglotLanguageContext);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
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
            try {
                return LanguageAccessor.engineAccess().getTruffleContext(polyglotLanguageContext);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Returns <code>true</code> if this {@link org.graalvm.polyglot.Context} is being
         * pre-initialized. For a given {@link Env environment}, the return value of this method
         * never changes.
         *
         * @see #initializeContext(Object)
         * @see #patchContext(Object, Env)
         * @since 19.0
         */
        @TruffleBoundary
        public boolean isPreInitialization() {
            try {
                return LanguageAccessor.engineAccess().inContextPreInitialization(polyglotLanguageContext);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Returns a {@link TruffleFile} for given path.
         *
         * @param path the absolute or relative path to create {@link TruffleFile} for
         * @return {@link TruffleFile}
         * @since 19.0
         * @deprecated use {@link #getInternalTruffleFile(java.lang.String) getInternalTruffleFile}
         *             for language standard library files or
         *             {@link #getPublicTruffleFile(java.lang.String) getPublicTruffleFile} for user
         *             files.
         */
        @TruffleBoundary
        @Deprecated
        public TruffleFile getTruffleFile(String path) {
            return getInternalTruffleFile(path);
        }

        /**
         * Returns a {@link TruffleFile} for given {@link URI}.
         *
         * @param uri the {@link URI} to create {@link TruffleFile} for
         * @return {@link TruffleFile}
         * @since 19.0
         * @deprecated use {@link #getInternalTruffleFile(java.lang.String) getInternalTruffleFile}
         *             for language standard library files or
         *             {@link #getPublicTruffleFile(java.lang.String) getPublicTruffleFile} for user
         *             files.
         */
        @TruffleBoundary
        @Deprecated
        public TruffleFile getTruffleFile(URI uri) {
            return getInternalTruffleFile(uri);
        }

        /**
         * Returns a public {@link TruffleFile} for given path. The access to public files depends
         * on {@link Builder#allowIO(boolean) Context's IO policy}. When IO is not enabled by the
         * {@code Context} the {@link TruffleFile} operations throw {@link SecurityException}. The
         * {@code getPublicTruffleFile} method should be used to access user files or to implement
         * language IO builtins.
         *
         * @param path the absolute or relative path to create {@link TruffleFile} for
         * @return {@link TruffleFile}
         * @since 19.3.0
         */
        @TruffleBoundary
        public TruffleFile getPublicTruffleFile(String path) {
            checkDisposed();
            FileSystemContext fs = getPublicFileSystemContext();
            try {
                return new TruffleFile(fs, fs.fileSystem.parsePath(path));
            } catch (UnsupportedOperationException e) {
                throw e;
            } catch (Throwable t) {
                throw TruffleFile.wrapHostException(t, fs.fileSystem);
            }
        }

        /**
         * Returns a public {@link TruffleFile} for given {@link URI}. The access to public files
         * depends on {@link Builder#allowIO(boolean) Context's IO policy}. When IO is not enabled
         * by the {@code Context} the {@link TruffleFile} operations throw {@link SecurityException}
         * . The {@code getPublicTruffleFile} method should be used to access user files or to
         * implement language IO builtins.
         *
         * @param uri the {@link URI} to create {@link TruffleFile} for
         * @return {@link TruffleFile}
         * @since 19.3.0
         */
        @TruffleBoundary
        public TruffleFile getPublicTruffleFile(URI uri) {
            checkDisposed();
            FileSystemContext fs = getPublicFileSystemContext();
            try {
                return new TruffleFile(fs, fs.fileSystem.parsePath(uri));
            } catch (UnsupportedOperationException e) {
                throw new FileSystemNotFoundException("FileSystem for: " + uri.getScheme() + " scheme is not supported.");
            } catch (Throwable t) {
                throw TruffleFile.wrapHostException(t, fs.fileSystem);
            }
        }

        /**
         * Returns a public or internal {@link TruffleFile} for given path. Unlike
         * {@link #getPublicTruffleFile(java.lang.String) getPublicTruffleFile} the
         * {@link TruffleFile} returned by this method for a file in a language home is readable
         * even when IO is not enabled by the {@code Context}. The {@code getInternalTruffleFile}
         * method should be used to read language standard libraries in a language home. For
         * security reasons the language should check that the file is a language source file in
         * language standard libraries folder before using this method for a file in a language
         * home.
         *
         * @param path the absolute or relative path to create {@link TruffleFile} for
         * @return {@link TruffleFile}
         * @since 19.3.0
         * @see #getPublicTruffleFile(java.lang.String)
         */
        @TruffleBoundary
        public TruffleFile getInternalTruffleFile(String path) {
            checkDisposed();
            FileSystemContext fs = getInternalFileSystemContext();
            try {
                return new TruffleFile(fs, fs.fileSystem.parsePath(path));
            } catch (UnsupportedOperationException e) {
                throw e;
            } catch (Throwable t) {
                throw TruffleFile.wrapHostException(t, fs.fileSystem);
            }
        }

        /**
         * Returns a public or internal {@link TruffleFile} for given {@link URI}. Unlike
         * {@link #getPublicTruffleFile(java.lang.String) getPublicTruffleFile} the
         * {@link TruffleFile} returned by this method for a file in a language home is readable
         * even when IO is not enabled by the {@code Context}. The {@code getInternalTruffleFile}
         * method should be used to read language standard libraries in a language home. For
         * security reasons the language should check that the file is a language source file in
         * language standard libraries folder before using this method for a file in a language
         * home.
         *
         * @param uri the {@link URI} to create {@link TruffleFile} for
         * @return {@link TruffleFile}
         * @since 19.3.0
         * @see #getPublicTruffleFile(java.net.URI)
         */
        @TruffleBoundary
        public TruffleFile getInternalTruffleFile(URI uri) {
            checkDisposed();
            FileSystemContext fs = getInternalFileSystemContext();
            try {
                return new TruffleFile(fs, fs.fileSystem.parsePath(uri));
            } catch (UnsupportedOperationException e) {
                throw new FileSystemNotFoundException("FileSystem for: " + uri.getScheme() + " scheme is not supported.");
            } catch (Throwable t) {
                throw TruffleFile.wrapHostException(t, fs.fileSystem);
            }
        }

        /**
         * Gets the current working directory. The current working directory is used to resolve non
         * absolute paths in {@link TruffleFile} methods.
         *
         * @return the current working directory
         * @throws SecurityException if the {@link FileSystem filesystem} denies reading of the
         *             current working directory
         * @since 19.0
         */
        @TruffleBoundary
        public TruffleFile getCurrentWorkingDirectory() {
            return getPublicTruffleFile("").getAbsoluteFile();
        }

        /**
         * Sets the current working directory. The current working directory is used to resolve non
         * absolute paths in {@link TruffleFile} methods.
         *
         * @param currentWorkingDirectory the new current working directory
         * @throws UnsupportedOperationException if setting of the current working directory is not
         *             supported
         * @throws IllegalArgumentException if the {@code currentWorkingDirectory} is not a valid
         *             current working directory
         * @throws SecurityException if {@code currentWorkingDirectory} is not readable
         * @since 19.0
         */
        @TruffleBoundary
        public void setCurrentWorkingDirectory(TruffleFile currentWorkingDirectory) {
            checkDisposed();
            Objects.requireNonNull(currentWorkingDirectory, "Current working directory must be non null.");
            if (!currentWorkingDirectory.isAbsolute()) {
                throw new IllegalArgumentException("Current working directory must be absolute.");
            }
            if (!currentWorkingDirectory.isDirectory()) {
                throw new IllegalArgumentException("Current working directory must be directory.");
            }
            FileSystemContext fileSystemContext = getPublicFileSystemContext();
            FileSystemContext internalFileSystemContext = getInternalFileSystemContext();
            try {
                fileSystemContext.fileSystem.setCurrentWorkingDirectory(currentWorkingDirectory.getSPIPath());
                if (fileSystemContext.fileSystem != internalFileSystemContext.fileSystem) {
                    internalFileSystemContext.fileSystem.setCurrentWorkingDirectory(currentWorkingDirectory.getSPIPath());
                }
            } catch (UnsupportedOperationException | IllegalArgumentException | SecurityException e) {
                throw e;
            } catch (Throwable t) {
                throw TruffleFile.wrapHostException(t, fileSystemContext.fileSystem);
            }
        }

        /**
         * Returns the name separator used to separate names in {@link TruffleFile}'s path string.
         *
         * @return the name separator
         * @since 19.0
         */
        @TruffleBoundary
        public String getFileNameSeparator() {
            checkDisposed();
            FileSystemContext fs = getPublicFileSystemContext();
            try {
                return fs.fileSystem.getSeparator();
            } catch (Throwable t) {
                throw TruffleFile.wrapHostException(t, fs.fileSystem);
            }
        }

        /**
         * Returns the path separator used to separate filenames in a path list. On UNIX the path
         * separator is {@code ':'}. On Windows it's {@code ';'}.
         *
         * @return the path separator
         * @since 19.1.0
         */
        @TruffleBoundary
        public String getPathSeparator() {
            checkDisposed();
            FileSystemContext fs = getPublicFileSystemContext();
            try {
                return fs.fileSystem.getPathSeparator();
            } catch (Throwable t) {
                throw TruffleFile.wrapHostException(t, fs.fileSystem);
            }
        }

        /**
         * Registers additional services provided by the language. The registered services are made
         * available to users via
         * {@link #lookup(com.oracle.truffle.api.nodes.LanguageInfo, java.lang.Class)} query method.
         * <p>
         * For each service interface enumerated in {@link Registration#services() language
         * registration} the language has to register a single service implementation.
         * <p>
         * This method can be called only during the execution of the
         * {@link #createContext(com.oracle.truffle.api.TruffleLanguage.Env) createContext method},
         * then the services are collected and cannot be changed anymore.
         *
         * @param service a service to be returned from associated
         *            {@link Env#lookup(com.oracle.truffle.api.nodes.LanguageInfo, java.lang.Class)
         *            lookup method}
         * @throws IllegalStateException if the method is called outside of
         *             {@link #createContext(com.oracle.truffle.api.TruffleLanguage.Env)} method
         * @since 19.0
         */
        public void registerService(Object service) {
            if (languageServicesCollector == null) {
                throw new IllegalStateException("The registerService method can only be called during the execution of the Env.createContext method.");
            }
            languageServicesCollector.add(service);
        }

        /**
         * Returns {@code true} if the creation of a sub-process is allowed in the current
         * environment.
         *
         * @see #newProcessBuilder(java.lang.String...)
         * @since 19.1.0
         */
        public boolean isCreateProcessAllowed() {
            try {
                return LanguageAccessor.engineAccess().isCreateProcessAllowed(polyglotLanguageContext);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Creates a new process builder with the specified operating program and arguments.
         *
         * @param command the executable and its arguments
         * @throws SecurityException when process creation is not allowed
         * @since 19.1.0
         */
        @TruffleBoundary
        public TruffleProcessBuilder newProcessBuilder(String... command) {
            if (!isCreateProcessAllowed()) {
                throw new TruffleSecurityException("Process creation is not allowed, to enable it set Context.Builder.allowCreateProcess(true).");
            }
            FileSystemContext fs = getPublicFileSystemContext();
            List<String> cmd = new ArrayList<>(command.length);
            Collections.addAll(cmd, command);
            return LanguageAccessor.ioAccess().createProcessBuilder(polyglotLanguageContext, fs.fileSystem, cmd);
        }

        /**
         * Returns an unmodifiable map of the process environment. When the {@code Context} is
         * configured with {@link EnvironmentAccess#INHERIT} it returns the {@link System#getenv()}
         * and the environment variables configured on the {@code Context}. For the
         * {@link EnvironmentAccess#NONE} only the environment variables configured on the
         * {@code Context} are returned.
         *
         * @return the process environment as a map of variable names to values
         * @since 19.1.0
         */
        @TruffleBoundary
        public Map<String, String> getEnvironment() {
            try {
                return LanguageAccessor.engineAccess().getProcessEnvironment(polyglotLanguageContext);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Creates a new empty file in the specified or default temporary directory, using the given
         * prefix and suffix to generate its name.
         * <p>
         * This method provides only part of a temporary file facility. To arrange for a file
         * created by this method to be deleted automatically the resulting file must be opened
         * using the {@link StandardOpenOption#DELETE_ON_CLOSE DELETE_ON_CLOSE} option. In this case
         * the file is deleted when the appropriate {@code close} method is invoked. Alternatively,
         * a {@link Runtime#addShutdownHook shutdown hook} may be used to delete the file
         * automatically.
         *
         * @param dir the directory in which the file should be created or {@code null} for a
         *            default temporary directory
         * @param prefix the prefix to generate the file's name or {@code null}
         * @param suffix the suffix to generate the file's name or {@code null} in which case
         *            "{@code .tmp}" is used
         * @param attrs the optional attributes to set atomically when creating the file
         * @return the {@link TruffleFile} representing the newly created file that did not exist
         *         before this method was invoked
         * @throws IOException in case of IO error
         * @throws IllegalArgumentException if the prefix or suffix cannot be used to generate a
         *             valid file name
         * @throws UnsupportedOperationException if the attributes contain an attribute which cannot
         *             be set atomically or {@link FileSystem} does not support default temporary
         *             directory
         * @throws SecurityException if the {@link FileSystem} denied the operation
         * @since 19.3.0
         */
        @TruffleBoundary
        public TruffleFile createTempFile(TruffleFile dir, String prefix, String suffix, FileAttribute<?>... attrs) throws IOException {
            FileSystemContext fs = getPublicFileSystemContext();
            try {
                TruffleFile useDir = dir == null ? new TruffleFile(fs, fs.fileSystem.getTempDirectory()) : dir;
                return TruffleFile.createTempFile(useDir, prefix, suffix, false, attrs);
            } catch (UnsupportedOperationException | IllegalArgumentException | IOException | SecurityException e) {
                throw e;
            } catch (Throwable t) {
                throw TruffleFile.wrapHostException(t, fs.fileSystem);
            }
        }

        /**
         * Creates a new directory in the specified or default temporary directory, using the given
         * prefix to generate its name.
         * <p>
         * This method provides only part of a temporary file facility. A
         * {@link Runtime#addShutdownHook shutdown hook} may be used to delete the directory
         * automatically.
         *
         * @param dir the directory in which the directory should be created or {@code null} for a
         *            default temporary directory
         * @param prefix the prefix to generate the directory's name or {@code null}
         * @param attrs the optional attributes to set atomically when creating the directory
         * @return the {@link TruffleFile} representing the newly created directory that did not
         *         exist before this method was invoked
         * @throws IOException in case of IO error
         * @throws IllegalArgumentException if the prefix cannot be used to generate a valid file
         *             name
         * @throws UnsupportedOperationException if the attributes contain an attribute which cannot
         *             be set atomically or {@link FileSystem} does not support default temporary
         *             directory
         * @throws SecurityException if the {@link FileSystem} denied the operation
         * @since 19.3.0
         */
        @TruffleBoundary
        public TruffleFile createTempDirectory(TruffleFile dir, String prefix, FileAttribute<?>... attrs) throws IOException {
            FileSystemContext fs = getPublicFileSystemContext();
            try {
                TruffleFile useDir = dir == null ? new TruffleFile(fs, fs.fileSystem.getTempDirectory()) : dir;
                return TruffleFile.createTempFile(useDir, prefix, null, true, attrs);
            } catch (UnsupportedOperationException | IllegalArgumentException | IOException | SecurityException e) {
                throw e;
            } catch (Throwable t) {
                throw TruffleFile.wrapHostException(t, fs.fileSystem);
            }
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

        boolean isInitialized() {
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

        boolean isVisible(Object value) {
            Object c = getLanguageContext();
            if (c != UNSET_CONTEXT) {
                return getSpi().isVisible(c, value);
            } else {
                return false;
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

        Object getLanguageContext() {
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

        @SuppressWarnings("unchecked")
        @TruffleBoundary
        static <T extends RuntimeException> RuntimeException engineToLanguageException(Throwable t) {
            return LanguageAccessor.engineAccess().engineToLanguageException(t);
        }
    }

    /**
     * Represents a reference to the language to be stored in an AST. A reference can be accessed
     * using {@link Node#lookupLanguageReference(Class)} and the current language can be accessed
     * using the {@link LanguageReference#get()} method of the returned reference.
     * <p>
     * The current language might vary between {@link RootNode#execute(VirtualFrame) executions} if
     * the reference is used with interoperability APIs in the AST of a foreign language.
     *
     * @since 19.0
     */
    @SuppressWarnings("rawtypes")
    public abstract static class LanguageReference<L extends TruffleLanguage> {

        /**
         * Constructors for subclasses.
         *
         * @since 19.0
         */
        protected LanguageReference() {
        }

        /**
         * Returns the current language of the current execution context. If a context is accessed
         * during {@link TruffleLanguage#createContext(Env) context creation} or in the language
         * class constructor an {@link IllegalStateException} is thrown. This methods is designed to
         * be called safely from compiled code paths.
         * <p>
         * The current language might vary between {@link RootNode#execute(VirtualFrame) executions}
         * if the reference is used with interoperability APIs in the AST of a foreign language.
         *
         * @since 19.0
         */
        public abstract L get();

    }

    /**
     * Represents a reference to the current context to be stored in an AST. A reference can be
     * accessed using {@link Node#lookupContextReference(Class)} and the current context can be
     * accessed using the {@link ContextReference#get()} method of the returned reference.
     * <p>
     * The current context might vary between {@link RootNode#execute(VirtualFrame) executions} if
     * resources or code is shared between multiple contexts.
     *
     * @since 0.25
     */
    public abstract static class ContextReference<C> {

        /**
         * Constructors for subclasses.
         *
         * @since 19.0
         */
        protected ContextReference() {
        }

        /**
         * Returns the current language context of the current execution context. If a context is
         * accessed during {@link TruffleLanguage#createContext(Env) context creation} or in the
         * language class constructor an {@link IllegalStateException} is thrown. This methods is
         * designed to be called safely from compiled code paths.
         * <p>
         * The current context might vary between {@link RootNode#execute(VirtualFrame) executions}
         * if resources or code is shared between multiple contexts.
         *
         * @since 0.25
         */
        @SuppressWarnings("unchecked")
        public abstract C get();
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
     * @since 19.0
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
         * @since 19.0
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
         * @since 19.0
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
         * @since 19.0
         */
        SHARED;

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
        final List<Thread> startedThreads = new ArrayList<>();

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
            context.mul = context.env.parsePublic(source);
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
        CallTarget method = env.parsePublic(multiply, "a", "b");
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

    abstract
    // BEGIN: TruffleLanguageSnippets.AsyncThreadLanguage#finalizeContext
    class AsyncThreadLanguage extends TruffleLanguage<Context> {

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
        protected void initializeContext(Context context) throws Exception {
            // create and start a Thread for the asynchronous task
            // remeber the Thread reference to stop and join it in
            // the finalizeContext
            Thread t = context.env.createThread(new Runnable() {
                @Override
                public void run() {
                    // asynchronous task
                }
            });
            context.startedThreads.add(t);
            t.start();
        }

        @Override
        protected void finalizeContext(Context context) {
            // stop and join all the created Threads
            boolean interrupted = false;
            for (int i = 0; i < context.startedThreads.size();) {
                Thread threadToJoin  = context.startedThreads.get(i);
                try {
                    if (threadToJoin != Thread.currentThread()) {
                        threadToJoin.interrupt();
                        threadToJoin.join();
                    }
                    i++;
                } catch (InterruptedException ie) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
    // END: TruffleLanguageSnippets.AsyncThreadLanguage#finalizeContext



}
