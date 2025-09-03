/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.LanguageAccessor.ENGINE;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;

import org.graalvm.home.Version;
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
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.InternalResource.Id;
import com.oracle.truffle.api.TruffleFile.FileSystemContext;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleSafepoint.Interrupter;
import com.oracle.truffle.api.TruffleSafepoint.Interruptible;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.ReadOnlyArrayList;
import com.oracle.truffle.api.io.TruffleProcessBuilder;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

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
 * <h4>Context Policy and Sharing</h4>
 *
 * The number of {@link TruffleLanguage} instances per polyglot {@link org.graalvm.polyglot.Context
 * context} is configured by the {@link Registration#contextPolicy() context policy}. By default an
 * {@link ContextPolicy#EXCLUSIVE exclusive} {@link TruffleLanguage language} instance is created
 * for every {@link org.graalvm.polyglot.Context polyglot context} or
 * {@link TruffleLanguage.Env#newInnerContextBuilder(String...) inner context}. With policy
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
 * For more information on sharing between multiple contexts please see {@link ContextPolicy}.
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
 * <li>Internal threads are {@link Env#newTruffleThreadBuilder(Runnable) created} and managed by a
 * language for a context. All internally created threads need to be stopped when the context is
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
 * disposed} before and after use with a context. Languages may
 * {@link Env#newTruffleThreadBuilder(Runnable) create} new threads if the environment
 * {@link Env#isCreateThreadAllowed() allows} it.
 * <p>
 * In addition to the two kinds of threads described above, there are
 * {@link Env#createSystemThread(Runnable, ThreadGroup) system threads} designed to process language
 * internal tasks in the background. They do not require the {@link Env#isCreateThreadAllowed()
 * creation permit}, but they must not enter any language context.
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
        String name() default "";

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

        /**
         * Returns {@code true} if the language uses {@code TruffleString}s with encodings not
         * present in the following list.
         * <ul>
         * <li>{@code UTF-8}</li>
         * <li>{@code UTF-16}</li>
         * <li>{@code UTF-32}</li>
         * <li>{@code ISO-8859-1}</li>
         * <li>{@code US-ASCII}</li>
         * <li>{@code BYTES}</li>
         * </ul>
         *
         * @since 22.1
         * @deprecated To enable all {@code TruffleString} encodings, it is sufficient to add the
         *             {@code org.graalvm.shadowed.jcodings} module to the language's module-info
         *             {@code requires} and ensure it is on the module path when languages are
         *             loaded. If the module can be found, and at least one languages requires it,
         *             all encodings are automatically enabled. When languages are loaded from the
         *             class path (i.e. as an unnamed module), having jcodings on the class path is
         *             enough to enable it.
         */
        @Deprecated(since = "24.1")
        boolean needsAllEncodings() default false;

        /**
         * A link to a website with more information about the language. Will be shown in the help
         * text of GraalVM launchers.
         * <p>
         * The link can contain the following substitutions:
         * <dl>
         * <dt>{@code ${graalvm-version}}</dt>
         * <dd>the current GraalVM version. Optionally, a format string can be provided for the
         * version using {@code ${graalvm-version:format}}. See {@link Version#format}.
         * <dt>{@code ${graalvm-website-version}}</dt>
         * <dd>the current GraalVM version in a format suitable for links to the GraalVM reference
         * manual. The exact format may change without notice.</dd>
         * </dl>
         *
         * @since 22.1.0
         * @return URL for language website.
         */
        String website() default "";

        /**
         * Specifies the most strict sandbox policy in which the language can be used. The language
         * can be used in a context with the specified sandbox policy or a weaker one. For example,
         * if a language specifies {@code ISOLATED} policy, it can be used in a context configured
         * with sandbox policy {@code TRUSTED}, {@code CONSTRAINED} or {@code ISOLATED}. But it
         * cannot be used in a context configured with the {@code UNTRUSTED} sandbox policy.
         *
         * @see SandboxPolicy
         * @since 23.0
         */
        SandboxPolicy sandbox() default SandboxPolicy.TRUSTED;

        /**
         * Declarative list of {@link InternalResource} classes that is associated with this
         * language. Use the {@code internalResources} attribute solely for registering required
         * internal resources. Optional internal resources should provide the associated language
         * identifier using the {@link Id#componentId()} method. To unpack all resources of a
         * language embedders may use {@link Engine#copyResources(Path, String...)}.
         *
         * @see InternalResource
         * @see Id
         * @since 23.1
         */
        Class<? extends InternalResource>[] internalResources() default {};

        /**
         * A declarative list of optional {@link InternalResource} identifiers associated with this
         * language. It is recommended to register all optional resource identifiers here.
         * Specifying the resource identifier allows the language use the resource even if its
         * implementation was omitted at runtime. To use the resource when its implementation was
         * omitted the {@code polyglot.engine.resourcePath.languageId} system property must be
         * specified and point to a path with the resources. This allows to omit the resource
         * implementation class in standalone distributions.
         *
         * @see InternalResource
         * @see Id
         * @since 25.0
         */
        String[] optionalResources() default {};
    }

    /**
     * Provider for creating context local and context thread local references.
     *
     * @see TruffleLanguage#locals
     * @see TruffleLanguage.ContextLocalProvider#createContextLocal(TruffleLanguage.ContextLocalFactory)
     * @see TruffleLanguage.ContextLocalProvider#createContextThreadLocal(TruffleLanguage.ContextThreadLocalFactory)
     * @since 23.1
     */
    protected static final class ContextLocalProvider<C> {

        List<ContextLocal<?>> contextLocals;
        List<ContextThreadLocal<?>> contextThreadLocals;

        private ContextLocalProvider() {
        }

        /**
         * Creates a new context local reference for this Truffle language. Context locals for
         * languages allow to store additional top-level values for each context besides the
         * language context. The advantage of context locals compared to storing the value in a
         * field of the language context is that reading a context local requires one indirection
         * less. It is recommended to use context locals for languages only if the read is critical
         * for performance.
         * <p>
         * Context local references must be created during the invocation in the
         * {@link TruffleLanguage} constructor. Calling this method at a later point in time will
         * throw an {@link IllegalStateException}. For each registered {@link TruffleLanguage}
         * subclass it is required to always produce the same number of context local references.
         * The values produced by the factory must not be <code>null</code> and use a stable exact
         * value type for each instance of a registered language class. If the return value of the
         * factory is not stable or <code>null</code> then an {@link IllegalStateException} is
         * thrown. These restrictions allow the Truffle runtime to read the value more efficiently.
         * <p>
         * Usage example:
         *
         * <pre>
         * &#64;TruffleLanguage.Registration(id = "example", name = "ExampleLanguage")
         * public final class ExampleLanguage extends TruffleLanguage<Env> {
         *
         *     final ContextLocal<ExampleLocal> contextLocal = locals.createContextLocal(ExampleLocal::new);
         *
         *     &#64;Override
         *     protected Env createContext(Env env) {
         *         return env;
         *     }
         *
         *     &#64;Override
         *     protected CallTarget parse(ParsingRequest request) throws Exception {
         *         return new RootNode(this) {
         *             &#64;Override
         *             public Object execute(VirtualFrame frame) {
         *                 // fast read
         *                 ExampleLocal local = contextLocal.get();
         *                 // access local
         *                 return "";
         *             }
         *         }.getCallTarget();
         *     }
         *
         *     static final class ExampleLocal {
         *
         *         final Env env;
         *
         *         ExampleLocal(Env env) {
         *             this.env = env;
         *         }
         *
         *     }
         * }
         * </pre>
         *
         * @since 23.1
         */
        public <T> ContextLocal<T> createContextLocal(ContextLocalFactory<C, T> factory) {
            ContextLocal<T> local = ENGINE.createLanguageContextLocal(factory);
            if (contextLocals == null) {
                contextLocals = new ArrayList<>();
            }
            try {
                contextLocals.add(local);
            } catch (UnsupportedOperationException e) {
                throw new IllegalStateException("The set of context locals is frozen. Context locals can only be created during construction of the TruffleLanguage subclass.");
            }
            return local;
        }

        /**
         * Creates a new context thread local reference for this Truffle language. Context
         * threadlocals for languages allow storing additional top-level values for each context and
         * thread. The factory may be invoked on any thread other than the thread of the context
         * thread local value.
         * <p>
         * Context thread local references must be created during the invocation in the
         * {@link TruffleLanguage} constructor. Calling this method at a later point in time will
         * throw an {@link IllegalStateException}. For each registered {@link TruffleLanguage}
         * subclass it is required to always produce the same number of context thread local
         * references. The values produced by the factory must not be <code>null</code> and use a
         * stable exact value type for each instance of a registered language class. If the return
         * value of the factory is not stable or <code>null</code> then an
         * {@link IllegalStateException} is thrown. These restrictions allow the Truffle runtime to
         * read the value more efficiently.
         * <p>
         * Context thread locals should not contain a strong reference to the provided thread. Use a
         * weak reference instance for that purpose.
         * <p>
         * Usage example:
         *
         * <pre>
         * &#64;TruffleLanguage.Registration(id = "example", name = "ExampleLanguage")
         * public static class ExampleLanguage extends TruffleLanguage<Env> {
         *
         *     final ContextThreadLocal<ExampleLocal> threadLocal = locals.createContextThreadLocal(ExampleLocal::new);
         *
         *     &#64;Override
         *     protected Env createContext(Env env) {
         *         return env;
         *     }
         *
         *     &#64;Override
         *     protected CallTarget parse(ParsingRequest request) throws Exception {
         *         return new RootNode(this) {
         *             &#64;Override
         *             public Object execute(VirtualFrame frame) {
         *                 // fast read
         *                 ExampleLocal local = threadLocal.get();
         *                 // access local
         *                 return "";
         *             }
         *         }.getCallTarget();
         *     }
         *
         *     static final class ExampleLocal {
         *
         *         final Env env;
         *         final WeakReference<Thread> thread;
         *
         *         ExampleLocal(Env env, Thread thread) {
         *             this.env = env;
         *             this.thread = new WeakReference&lt;&gt;(thread);
         *         }
         *
         *     }
         * }
         * </pre>
         *
         * @since 23.1
         */
        public <T> ContextThreadLocal<T> createContextThreadLocal(ContextThreadLocalFactory<C, T> factory) {
            ContextThreadLocal<T> local = ENGINE.createLanguageContextThreadLocal(factory);
            if (contextThreadLocals == null) {
                contextThreadLocals = new ArrayList<>();
            }
            try {
                contextThreadLocals.add(local);
            } catch (UnsupportedOperationException e) {
                throw new IllegalStateException("The set of context thread locals is frozen. Context thread locals can only be created during construction of the TruffleLanguage subclass.");
            }
            return local;
        }
    }

    /**
     * Provider for creating context local and context thread local references.
     *
     * @see TruffleLanguage.ContextLocalProvider#createContextLocal(TruffleLanguage.ContextLocalFactory)
     * @see TruffleLanguage.ContextLocalProvider#createContextThreadLocal(TruffleLanguage.ContextThreadLocalFactory)
     * @since 23.1
     */
    protected final ContextLocalProvider<C> locals = new ContextLocalProvider<>();

    /**
     * Returns <code>true</code> if the combination of two sets of options allow to
     * {@link ContextPolicy#SHARED share} or {@link ContextPolicy#REUSE reuse} the same language
     * instance, else <code>false</code>. If options are incompatible then a new language instance
     * will be created for a new context. The default implementation returns <code>true</code>.
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
     * {@snippet file = "com/oracle/truffle/api/TruffleLanguage.java" region =
     * "TruffleLanguageSnippets.CompatibleLanguage#areOptionsCompatible"}
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
     * The context created by this method is accessible using {@link ContextReference context
     * references}. An {@link IllegalStateException} is thrown if the context is tried to be
     * accessed while the createContext method is executed.
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
     * actions. Example: {@snippet file = "com/oracle/truffle/api/TruffleLanguage.java" region =
     * "TruffleLanguageSnippets.PostInitLanguage#createContext"}
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
     * are {@link #disposeContext(Object) disposed}. However, in case the underlying polyglot
     * context is being cancelled or hard-exited, {@link #disposeContext(Object)} is called even if
     * {@link #finalizeContext(Object)} throws an
     * {@link com.oracle.truffle.api.exception.AbstractTruffleException} or a {@link ThreadDeath}
     * cancel or exit exception.
     * <p>
     * For the hard exit a language is supposed to run its finalization actions that require running
     * guest code in {@link #exitContext(Object, ExitMode, int)}, but please note that after
     * {@link #exitContext(Object, ExitMode, int)} runs for a language, the language can still be
     * invoked from {@link #exitContext(Object, ExitMode, int)} for a different language. Therefore,
     * for instance, finalization for standard streams should both flush and set them to unbuffered
     * mode at the end of exitContext, so that running guest code is not required to dispose the
     * streams after that point.
     * <p>
     * Context finalization is invoked even if a context was cancelled. In such a case, if guest
     * code would run as part of the finalization, it would be cancelled at the next polled
     * {@link TruffleSafepoint safepoint}. If there is guest code that always needs to run even if
     * cancelled, e.g. to prevent resource leakage, use
     * {@link TruffleSafepoint#setAllowActions(boolean)} to temporarily disable safepoints while
     * executing that code.
     * <p>
     * All installed languages must remain usable after finalization. The finalization order can be
     * influenced by specifying {@link Registration#dependentLanguages() language dependencies}. By
     * default internal languages are finalized last, otherwise, the default order is unspecified
     * but deterministic.
     * <p>
     * While the finalization code is run, other language contexts may become initialized. In such a
     * case, the finalization order may be non-deterministic and/or not respect the order specified
     * by language dependencies.
     * <p>
     * All threads {@link Env#newTruffleThreadBuilder(Runnable) created} by the language must be
     * stopped and joined during finalizeContext. The languages are responsible for fulfilling that
     * contract, otherwise, an {@link AssertionError} is thrown. It's not safe to use the
     * {@link ExecutorService#awaitTermination(long, java.util.concurrent.TimeUnit)} to detect
     * Thread termination as the polyglot thread may be cancelled before executing the executor
     * worker.
     * <p>
     * During finalizeContext, all unclosed inner contexts
     * {@link Env#newInnerContextBuilder(String...) created} by the language must be left on all
     * threads where the contexts are still active. No active inner context is allowed after
     * {@link #finalizeContext(Object)} returns, otherwise it is an internal error.
     * <p>
     * Non-active inner contexts {@link Env#newInnerContextBuilder(String...) created} by the
     * language that are still unclosed after {@link #finalizeContext(Object)} returns are
     * automatically closed by Truffle.
     * <p>
     * Typical implementation looks like:
     *
     * {@snippet file = "com/oracle/truffle/api/TruffleLanguage.java" region =
     * "TruffleLanguageSnippets.AsyncThreadLanguage#finalizeContext"}
     *
     * @see Registration#dependentLanguages() for specifying language dependencies.
     * @param context the context created by
     *            {@link #createContext(com.oracle.truffle.api.TruffleLanguage.Env)}
     * @since 0.30
     */
    protected void finalizeContext(C context) {
    }

    /**
     * Performs language exit event actions that are necessary before language contexts are
     * {@link #finalizeContext(Object) finalized}. However, in case the underlying polyglot context
     * is being cancelled, {@link #exitContext(Object, ExitMode, int) exit notifications} are not
     * executed. Also, for {@link ExitMode#HARD hard exit}, {@link #finalizeContext(Object)} is
     * called even if {@link #exitContext(Object, ExitMode, int)} throws an
     * {@link com.oracle.truffle.api.exception.AbstractTruffleException} or a {@link ThreadDeath}
     * cancel or exit exception. All initialized language contexts must remain usable after exit
     * notifications. In case a {@link com.oracle.truffle.api.exception.AbstractTruffleException} or
     * the {@link ThreadDeath} exit exception is thrown during an {@link ExitMode#HARD hard exit
     * notification}, it is just logged and otherwise ignored and the notification process continues
     * with the next language in order. In case the {@link ThreadDeath} cancel exception is thrown,
     * it means the context is being cancelled in which case the exit notification process
     * immediately stops. The exit notification order can be influenced by specifying
     * {@link Registration#dependentLanguages() language dependencies} - The exit notification for
     * language A that depends on language B is executed before the exit notification for language
     * B. By default, notifications for internal languages are executed last, otherwise, the default
     * order is unspecified but deterministic.
     * <p>
     * During {@link ExitMode#HARD hard exit} notification, a language is supposed to run its
     * finalization actions that require running guest code instead of running them in
     * {@link #finalizeContext(Object)}.
     * <p>
     * While the exit notification code is run, all languages remain fully functional. Also, other
     * language contexts may become initialized. In such a case, the notification order may be
     * non-deterministic and/or not respect the order specified by language dependencies.
     * <p>
     * In case {@link TruffleContext#closeExited(Node, int)} is called during a
     * {@link ExitMode#NATURAL natural exit} notification, natural exit notifications for remaining
     * language contexts are not executed and the {@link ExitMode#HARD hard exit} process starts by
     * executing {@link ExitMode#HARD hard exit} notifications. In case
     * {@link TruffleContext#closeExited(Node, int)} is called during a {@link ExitMode#HARD hard
     * exit} notification, it just throws the special {@link ThreadDeath} exit exception, which is
     * then just logged as described above.
     * <p>
     * In case the underlying polyglot context is cancelled by e.g.
     * {@link TruffleContext#closeCancelled(Node, String)} during exit notifications, guest code
     * executed during exit notification is regularly cancelled, i.e., throws the
     * {@link ThreadDeath} cancel exception, and the notification process does not continue as
     * described above.
     * <p>
     * In the case of {@link ExitMode#HARD hard exit}, if the current context has inner contexts
     * that are still active, the execution for them will be stopped as well after all language exit
     * notifications are executed for the outer context, but the exit notifications for the inner
     * contexts are not automatically executed. The language needs to take appropriate action to
     * make sure inner contexts are exited properly.
     *
     * @param context language context.
     * @param exitMode mode of exit.
     * @param exitCode exit code that was specified for exit.
     * @see <a href= "https://github.com/oracle/graal/blob/master/truffle/docs/Exit.md">Context
     *      Exit</a>
     *
     * @since 22.0
     */
    protected void exitContext(C context, ExitMode exitMode, int exitCode) {
    }

    /**
     * Initializes this language instance for use with multiple contexts. Whether a language
     * instance supports being used for multiple contexts depends on its
     * {@link Registration#contextPolicy() context policy}.
     * <p>
     * For any sharing of a language instance for multiple language contexts to take place a context
     * must be created with sharing enabled. By default sharing is disabled for polyglot contexts.
     * Sharing can be enabled by specifying an {@link Builder#engine(Engine) explicit engine} or
     * using an option. Before any language is used for sharing
     * {@link #initializeMultipleContexts()} is invoked. It is guaranteed that sharing between
     * multiple contexts is {@link #initializeMultipleContexts() initialized} before any language
     * context is {@link #createContext(Env) created}.
     * <p>
     * A language may use this method to configure itself to use context independent speculations
     * only. Since Truffle nodes are never shared between multiple language instances it is
     * sufficient to keep track of whether sharing is enabled using a non-volatile boolean field
     * instead of an assumption. They field may also be annotated with {@link CompilationFinal} as
     * it is guaranteed that this method is called prior to any compilation. The following criteria
     * should be satisfied when supporting context independent code:
     * <ul>
     * <li>All speculation on runtime value identity must be disabled with multiple contexts
     * initialized, as they will lead to a guaranteed deoptimization when used with a second
     * context.
     * <li>Function inline caches should be modified and implemented as a two-level inline cache.
     * The first level speculates on the function instance's identity and the second level on the
     * underlying CallTarget instance. The first level cache must be disabled if multiple contexts
     * are initialized, as this would unnecessarily cause deoptimization.
     * <li>The DynamicObject root Shape instance should be stored in the language instance instead
     * of the language context. Otherwise, any inline cache on shapes will not stabilize and
     * ultimately end up in the generic state.
     * <li>All Node implementations must not store context-dependent data structures or
     * context-dependent runtime values.
     * <li>All assumption instances should be stored in the language instance instead of the
     * context. With multiple contexts initialized, the context instance read using context
     * references may no longer be a constant. In this case any assumption read from the context
     * would not be folded and they would cause significant runtime performance overhead.
     * Assumptions from the language can always be folded by the compiler in both single and
     * multiple context mode.
     *
     * @see ContextPolicy More information on sharing language instances for multiple contexts.
     * @see #areOptionsCompatible(OptionValues, OptionValues) Specify option configurations that
     *      make sharing incompatible.
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
     * prior to context {@link #disposeContext(Object) disposal}. However, in case the underlying
     * polyglot context is being cancelled, {@link #disposeContext(Object)} is called even if
     * {@link #finalizeContext(Object)} throws
     * {@link com.oracle.truffle.api.exception.AbstractTruffleException} or {@link ThreadDeath}
     * exception..
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
     * Returns a set of source option descriptors that are supported by this language. Option values
     * are accessible using the {@link Source#getOptions(TruffleLanguage) environment} or using a
     * {@link ParsingRequest#getOptionValues() parsing request} when the source is parsed. Languages
     * must always return the same option descriptors independent of the language instance or
     * side-effects.
     *
     * @see Option For an example of declaring the option descriptor using an annotation.
     * @since 25.0
     */
    protected OptionDescriptors getSourceOptionDescriptors() {
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
     * Typical implementation looks like: {@snippet file =
     * "com/oracle/truffle/api/TruffleLanguage.java" region =
     * "TruffleLanguageSnippets.PreInitializedLanguage#patchContext"}
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
        private final OptionValues optionValues;
        private boolean disposed;

        ParsingRequest(Source source, OptionValues optionValues, String... argumentNames) {
            Objects.requireNonNull(source);
            this.source = source;
            this.optionValues = optionValues;
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
         * Returns the source option values associated with the parsing request's source and
         * language. This method should be preferred over {@link Source#getOptions(TruffleLanguage)}
         * when accessing the source options of a source when possible, to avoid parsing the options
         * twice.
         * <p>
         * In order to allow users of the language to specify source options they must be declared
         * by implementing {@link TruffleLanguage#getSourceOptionDescriptors()}.
         *
         * @since 25.0
         */
        public OptionValues getOptionValues() {
            if (disposed) {
                throw new IllegalStateException();
            }
            return optionValues;
        }

        /**
         * Argument names. The result of
         * {@link #parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest) parsing} is an
         * instance of {@link CallTarget} that {@link CallTarget#call(java.lang.Object...) can be
         * invoked} without or with some parameters. If the invocation requires some arguments, and
         * the {@link #getSource()} references them, it is essential to name them. Example that uses
         * the argument names:
         *
         * {@snippet file = "com/oracle/truffle/api/TruffleLanguage.java" region =
         * "TruffleLanguageSnippets#parseWithParams"}
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
        private final OptionValues optionValues;
        private boolean disposed;

        InlineParsingRequest(Source source, OptionValues optionValues, Node node, MaterializedFrame frame) {
            Objects.requireNonNull(source);
            this.node = node;
            this.frame = frame;
            this.source = source;
            this.optionValues = optionValues;
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
         * Returns the source option values associated with the parsing request's source and
         * language. This method should be preferred over {@link Source#getOptions(TruffleLanguage)}
         * when accessing the source options of a source when possible, to avoid parsing the options
         * twice.
         * <p>
         * In order to allow users of the language to specify source options they must be declared
         * by implementing {@link TruffleLanguage#getSourceOptionDescriptors()}.
         *
         * @since 25.0
         */
        public OptionValues getOptionValues() {
            if (disposed) {
                throw new IllegalStateException();
            }
            return optionValues;
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
     * Returns <code>true</code> if code of this language is allowed to be executed on this thread.
     * The method returns <code>false</code> to deny execution on this thread. The default
     * implementation denies access to more than one thread at the same time. The
     * {@link Thread#currentThread() current thread} may differ from the passed thread. If this
     * method throws an {@link com.oracle.truffle.api.exception.AbstractTruffleException} the
     * exception interop messages may be executed without a context being entered.
     * <p>
     * <b>Example multi-threaded language implementation:</b> {@snippet file =
     * "com/oracle/truffle/api/TruffleLanguage.java" region =
     * "TruffleLanguageSnippets.MultiThreadedLanguage#initializeThread"}
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
     * this method to be invoked. If this method throws an
     * {@link com.oracle.truffle.api.exception.AbstractTruffleException} the exception interop
     * messages may be executed without a context being entered.
     * <p>
     * <b>Example multi-threaded language implementation:</b> {@snippet file =
     * "com/oracle/truffle/api/TruffleLanguage.java" region =
     * "TruffleLanguageSnippets.MultiThreadedLanguage#initializeThread"}
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
     * The {@link Thread#currentThread() current thread} is equal to the initialized thread.
     * <p>
     * If this method throws an {@link com.oracle.truffle.api.exception.AbstractTruffleException}
     * the exception interop messages may be executed without a context being entered.
     * <p>
     * <b>Example multi-threaded language implementation:</b> {@snippet file =
     * "com/oracle/truffle/api/TruffleLanguage.java" region =
     * "TruffleLanguageSnippets.MultiThreadedLanguage#initializeThread"}
     *
     * @param context the context that is entered
     * @param thread the thread that accesses the context for the first time.
     *
     * @since 0.28
     */
    protected void initializeThread(C context, Thread thread) {
    }

    /**
     * The behavior of this notification is different for
     * {@link Env#newTruffleThreadBuilder(Runnable) polyglot threads} and embedder threads.
     * <p>
     * For {@link Env#newTruffleThreadBuilder(Runnable) polyglot threads} the
     * <code>finalizeThread</code> notification is invoked just before the context is left for the
     * last time in the thread, and it is still safe to run guest code unless the context is
     * cancelled or hard-exited. This allows the language to perform finalization actions for each
     * thread and context. Polyglot threads are finalized before or while the context is finalized
     * and it always holds that <code>thread == Thread.currentThread()</code>.
     * <p>
     * Embedder threads are finalized after the context is finalized, but before it is disposed.
     * <code>thread == Thread.currentThread()</code> holds only for the embedder thread that
     * performed the context finalization. Finalization of other embedder threads is invoked in the
     * context finalization thread as well, and so <code>thread != Thread.currentThread()</code>. It
     * is still safe to run guest code in <code>finalizeThread</code> for an embedder thread unless
     * the context is cancelled or hard-exited, but it is not allowed to initialize new language
     * contexts or create polyglot threads. A language context initialization or creation of a
     * polyglot thread in <code>finalizeThread</code> for an embedder thread results in
     * {@link IllegalStateException}. Please note that embedder threads may be collected by the
     * garbage collector before they can be finalized and may therefore not be finalized.
     * <p>
     * Thread finalization is invoked before {@link #disposeThread(Object, Thread) thread disposal}.
     *
     * @see #initializeThread(Object, Thread) For usage details.
     * @since 23.1
     */
    @SuppressWarnings("unused")
    protected void finalizeThread(C context, Thread thread) {
    }

    /**
     * Invoked the last time code will be executed for this thread and context. This allows the
     * language to perform cleanup actions for each thread and context. Threads might be disposed
     * before after or while a context is disposed. The {@link Thread#currentThread() current
     * thread} may differ from the disposed thread. Disposal of threads is only guaranteed for
     * threads that were created by guest languages, so called
     * {@link Env#newTruffleThreadBuilder(Runnable) polyglot threads}. Other threads, created by the
     * embedder, may be collected by the garbage collector before they can be disposed and may
     * therefore not be disposed.
     *
     * @see #initializeThread(Object, Thread) For usage details.
     * @since 0.28
     */
    @SuppressWarnings("unused")
    protected void disposeThread(C context, Thread thread) {
    }

    /**
     * Get a top scope of the language, if any. The returned object must be an
     * {@link com.oracle.truffle.api.interop.InteropLibrary#isScope(Object) interop scope object}
     * and may have {@link com.oracle.truffle.api.interop.InteropLibrary#hasScopeParent(Object)
     * parent scopes}. The scope object exposes all top scopes variables as flattened
     * {@link com.oracle.truffle.api.interop.InteropLibrary#getMembers(Object) members}. Top scopes
     * are independent of a {@link Frame}. See
     * {@link com.oracle.truffle.api.interop.InteropLibrary#isScope(Object)} for details.
     * <p>
     * The returned scope objects may be cached by the caller per language context. Therefore the
     * method should always return equivalent top-scopes and variables objects for a given language
     * context. Changes to the top scope by executing guest language code should be reflected by
     * cached scope instances. It is recommended to store the top-scope directly in the language
     * context for efficient access.
     * <p>
     * <h3>Interpretation</h3> In most languages, just evaluating an expression like
     * <code>Math</code> is equivalent of a lookup with the identifier 'Math' in the top-most scopes
     * of the language. Looking up the identifier 'Math' should have equivalent semantics as reading
     * with the key 'Math' from the variables object of one of the top-most scopes of the language.
     * In addition languages may optionally allow modification and insertion with the variables
     * object of the returned top-scopes.
     * <p>
     * Languages may want to specify multiple parent top-scopes. It is recommended to stay as close
     * as possible to the set of top-scopes that as is described in the guest language
     * specification, if available. For example, in JavaScript, there is a 'global environment' and
     * a 'global object' scope. While the global environment scope contains class declarations and
     * is not insertable, the global object scope is used to insert new global variable values and
     * is therefore insertable.
     * <p>
     * <h3>Use Cases</h3>
     * <ul>
     * <li>Top scopes are accessible to instruments with
     * {@link com.oracle.truffle.api.instrumentation.TruffleInstrument.Env#getScope(LanguageInfo)}.
     * They are used by debuggers to access the top-most scopes of the language.
     * <li>Top scopes available in the {@link org.graalvm.polyglot polyglot API} as context
     * {@link Context#getBindings(String) bindings} object. Access to members of the bindings object
     * is applied to the returned scope object via interop.
     * <li>Languages may expose other language scopes using a polyglot bindings builtin. E.g with
     * {@link com.oracle.truffle.api.TruffleLanguage.Env#getScopePublic(LanguageInfo)}.
     * </ul>
     * <p>
     *
     * @param context the context to find the language top scope in
     * @return the scope object or <code>null</code> if the language does not support such concept
     * @since 20.3
     */
    protected Object getScope(C context) {
        return null;
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
     * library messages will be forwarded to the delegate value.
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

    CallTarget parse(Source source, OptionValues optionValues, String... argumentNames) {
        ParsingRequest request = new ParsingRequest(source, optionValues, argumentNames);
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

    ExecutableNode parseInline(Source source, OptionValues optionValues, Node context, MaterializedFrame frame) {
        assert context != null;
        InlineParsingRequest request = new InlineParsingRequest(source, optionValues, context, frame);
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
     * @since 0.27
     * @deprecated in 21.3, use static final context references instead. See
     *             {@link ContextReference} for the new intended usage.
     */
    @Deprecated(since = "21.3")
    protected static <T extends TruffleLanguage<?>> T getCurrentLanguage(Class<T> languageClass) {
        try {
            return LanguageAccessor.engineAccess().getCurrentLanguage(languageClass);
        } catch (Throwable t) {
            CompilerDirectives.transferToInterpreter();
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * @since 0.27
     * @deprecated in 21.3, use static final context references instead. See
     *             {@link LanguageReference} for the new intended usage.
     */
    @Deprecated(since = "21.3")
    protected static <C, T extends TruffleLanguage<C>> C getCurrentContext(Class<T> languageClass) {
        try {
            return ENGINE.getCurrentContext(languageClass);
        } catch (Throwable t) {
            CompilerDirectives.transferToInterpreter();
            throw Env.engineToLanguageException(t);
        }
    }

    /**
     * Creates a new context local reference for this Truffle language.
     *
     * Starting with JDK 21, using this method leads to a this-escape warning. Use
     * {@link TruffleLanguage.ContextLocalProvider#createContextLocal(TruffleLanguage.ContextLocalFactory)}
     * instead.
     *
     * @deprecated in 23.1, use
     *             {@link TruffleLanguage.ContextLocalProvider#createContextLocal(TruffleLanguage.ContextLocalFactory)}
     *             instead
     * @since 20.3
     */
    @Deprecated
    protected final <T> ContextLocal<T> createContextLocal(ContextLocalFactory<C, T> factory) {
        return locals.createContextLocal(factory);
    }

    /**
     * Creates a new context thread local reference for this Truffle language.
     *
     * Starting with JDK 21, using this method leads to a this-escape warning. Use
     * {@link TruffleLanguage.ContextLocalProvider#createContextThreadLocal(TruffleLanguage.ContextThreadLocalFactory)}
     * instead.
     *
     * @deprecated in 23.1, use
     *             {@link TruffleLanguage.ContextLocalProvider#createContextThreadLocal(TruffleLanguage.ContextThreadLocalFactory)}
     *             instead
     * @since 20.3
     */
    @Deprecated
    protected final <T> ContextThreadLocal<T> createContextThreadLocal(ContextThreadLocalFactory<C, T> factory) {
        return locals.createContextThreadLocal(factory);
    }

    /**
     * Returns the home location for this language or {@code null} if the language home is not set.
     * Languages consumed from the Maven repository typically don't have a language home. For legacy
     * graalvm or standalone builds the language home corresponds to the directory in which the Jar
     * file is located, if run from a Jar file. For an AOT compiled binary, this corresponds to the
     * location of the language files in the default GraalVM distribution layout.
     *
     * @since 19.0
     */
    protected final String getLanguageHome() {
        try {
            return ENGINE.getLanguageHome(languageInfo);
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
        assert polyglotLanguageInstance != null : "getAsynchronousStackDepth not supported for host language";
        return LanguageAccessor.engineAccess().getAsynchronousStackDepth(polyglotLanguageInstance);
    }

    /**
     * Context local factory for Truffle languages. Creates a new value per context.
     *
     * @since 20.3
     */
    @FunctionalInterface
    protected interface ContextLocalFactory<C, T> {

        /**
         * Returns a new value for a context local of a language. The returned value must not be
         * <code>null</code> and must return a stable and exact type per registered language. A
         * thread local must always return the same {@link Object#getClass() class}, even for
         * multiple instances of the same {@link TruffleLanguage}. If this method throws an
         * {@link com.oracle.truffle.api.exception.AbstractTruffleException} the exception interop
         * messages may be executed without a context being entered.
         *
         * @see TruffleLanguage.ContextLocalProvider#createContextLocal(TruffleLanguage.ContextLocalFactory)
         * @since 20.3
         */
        T create(C context);
    }

    /**
     * Context thread local factory for Truffle languages. Creates a new value per context and
     * thread.
     *
     * @since 20.3
     */
    @FunctionalInterface
    protected interface ContextThreadLocalFactory<C, T> {

        /**
         * Returns a new value for a context thread local for a language context and thread. The
         * returned value must not be <code>null</code> and must return a stable and exact type per
         * TruffleLanguage subclass. A thread local must always return the same
         * {@link Object#getClass() class}, even for multiple instances of the same
         * {@link TruffleLanguage}. If this method throws an
         * {@link com.oracle.truffle.api.exception.AbstractTruffleException} the exception interop
         * messages may be executed without a context being entered.
         *
         * @see TruffleLanguage.ContextLocalProvider#createContextThreadLocal(TruffleLanguage.ContextThreadLocalFactory)
         * @since 20.3
         */
        T create(C context, Thread thread);
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
         * @see #newTruffleThreadBuilder(Runnable)
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
         * @deprecated Use {@link #newTruffleThreadBuilder(Runnable)} instead.
         */
        @TruffleBoundary
        @Deprecated
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
         * @see #newInnerContextBuilder(String...)
         * @since 0.28
         * @deprecated Use {@link #newTruffleThreadBuilder(Runnable)} instead.
         */
        @TruffleBoundary
        @Deprecated
        public Thread createThread(Runnable runnable, @SuppressWarnings("hiding") TruffleContext context) {
            return createThread(runnable, context, null, 0);
        }

        /**
         * Creates a new thread that has access to the given context. See
         * {@link #createThread(Runnable, TruffleContext, ThreadGroup, long)} for a detailed
         * description of the parameters. The <code>stackSize</code> set to 0.
         *
         * @see #getContext()
         * @see #newInnerContextBuilder(String...)
         * @since 0.28
         * @deprecated Use {@link #newTruffleThreadBuilder(Runnable)} instead.
         */
        @TruffleBoundary
        @Deprecated
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
         * The language that created and started the thread is responsible to stop and join it
         * during the {@link TruffleLanguage#finalizeContext(Object) finalizeContext}, otherwise an
         * {@link AssertionError} is thrown. It's not safe to use the
         * {@link ExecutorService#awaitTermination(long, java.util.concurrent.TimeUnit)} to detect
         * Thread termination as the polyglot thread may be cancelled before executing the executor
         * worker.<br/>
         * A typical implementation looks like: {@snippet file =
         * "com/oracle/truffle/api/TruffleLanguage.java" region =
         * "TruffleLanguageSnippets.AsyncThreadLanguage#finalizeContext"}
         * <p>
         * The {@link TruffleContext} can be either an inner context created by
         * {@link #newInnerContextBuilder(String...)}.{@link TruffleContext.Builder#build()
         * build()}, or the context associated with this environment obtained from
         * {@link #getContext()}.
         *
         * @param runnable the runnable to run on this thread.
         * @param context the context to enter and leave when the thread is started.
         * @param group the thread group, passed on to the underlying {@link Thread}.
         * @param stackSize the desired stack size for the new thread, or zero if this parameter is
         *            to be ignored.
         * @throws IllegalStateException if thread creation is not {@link #isCreateThreadAllowed()
         *             allowed}.
         * @see #getContext()
         * @see #newInnerContextBuilder(String...)
         * @since 0.28
         * @deprecated Use {@link #newTruffleThreadBuilder(Runnable)} instead.
         */
        @TruffleBoundary
        @Deprecated
        public Thread createThread(Runnable runnable, @SuppressWarnings("hiding") TruffleContext context, ThreadGroup group, long stackSize) {
            return new TruffleThreadBuilder(polyglotLanguageContext, runnable).context(context).threadGroup(group).stackSize(stackSize).build();
        }

        /**
         * Creates a builder for threads that have access to the given context.
         *
         * @param runnable the runnable to run on the threads created by the builder.
         * @return the builder for threads that have access to the given context.
         * @since 23.0
         */
        public TruffleThreadBuilder newTruffleThreadBuilder(Runnable runnable) {
            return new TruffleThreadBuilder(polyglotLanguageContext, runnable);
        }

        /**
         * Creates a new thread designed to process language internal tasks in the background. See
         * {@link #createSystemThread(Runnable, ThreadGroup)} for a detailed description of the
         * parameters.
         *
         * @see #createSystemThread(Runnable, ThreadGroup)
         * @since 22.3
         */
        @TruffleBoundary
        public Thread createSystemThread(Runnable runnable) {
            return createSystemThread(runnable, null);
        }

        /**
         * Creates a new thread designed to process language internal tasks in the background. The
         * created thread cannot enter the context, if it tries an {@link IllegalStateException} is
         * thrown. Creating or terminating a system thread does not notify
         * {@link TruffleLanguage#initializeThread(Object, Thread) languages} or instruments'
         * thread-listeners. Creating a system thread does not cause a transition to multi-threaded
         * access. The {@link Env#isCreateThreadAllowed() creation permit} is not required to create
         * a system thread. The caller must be either entered in a context, or in another system
         * thread to create a new system thread. If not an {@link IllegalStateException} is thrown.
         * <p>
         * It is recommended to set an
         * {@link Thread#setUncaughtExceptionHandler(java.lang.Thread.UncaughtExceptionHandler)
         * uncaught exception handler} for the created thread. For example the thread can throw an
         * uncaught exception if the context is closed before the thread is started.
         * <p>
         * The language that created and started the thread is responsible to stop and join it
         * during the {@link TruffleLanguage#disposeContext(Object)} disposeContext}, otherwise an
         * {@link IllegalStateException} is thrown. It's not safe to use the
         * {@link ExecutorService#awaitTermination(long, java.util.concurrent.TimeUnit)} to detect
         * Thread termination as the system thread may be cancelled before executing the executor
         * worker.<br/>
         * A typical implementation looks like: {@snippet file =
         * "com/oracle/truffle/api/TruffleLanguage.java" region =
         * "TruffleLanguageSnippets.SystemThreadLanguage"}
         *
         * @param runnable the runnable to run on this thread.
         * @param threadGroup the thread group, passed on to the underlying {@link Thread}.
         * @throws IllegalStateException if the context is already closed.
         * @see #createSystemThread(Runnable)
         * @since 22.3
         */
        @TruffleBoundary
        public Thread createSystemThread(Runnable runnable, ThreadGroup threadGroup) {
            try {
                return LanguageAccessor.engineAccess().createLanguageSystemThread(polyglotLanguageContext, runnable, threadGroup);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Returns a new context builder useful to create inner context instances.
         *
         * @see TruffleContext for details on language inner contexts.
         * @since 0.27
         *
         * @deprecated use {@link #newInnerContextBuilder(String...)} instead. Note that the
         *             replacement method configures the context differently by default. To restore
         *             the old behavior: <code>newInnerContextBuilder()
         *                   .initializeCreatorContext(true).inheritAllAccess(true).build() </code>
         */
        @Deprecated
        public TruffleContext.Builder newContextBuilder() {
            return newInnerContextBuilder().initializeCreatorContext(true).inheritAllAccess(true);
        }

        /**
         * Returns a new context builder useful to create inner context instances.
         * <p>
         * By default the inner context inherits none of the access privileges. To inherit access
         * set {@link TruffleContext.Builder#inheritAllAccess(boolean)} to <code>true</code>.
         * <p>
         * No language context will be initialized by default in the inner context. In order to
         * initialize the creator context use
         * {@link TruffleContext.Builder#initializeCreatorContext(boolean)}, initialize the language
         * after context creation using {@link TruffleContext#initializePublic(Node, String)} or
         * evaluate a source using {@link TruffleContext#evalPublic(Node, Source)}.
         *
         * @param permittedLanguages ids of languages permitted in the context. If no languages are
         *            provided, then all languages permitted to the outer context will be permitted.
         *            Languages are validated when the context is {@link Builder#build() built}. An
         *            {@link IllegalArgumentException} will be thrown if an unknown or a language
         *            denied by the engine was used.
         * @see TruffleContext for details on language inner contexts.
         * @since 22.3
         */
        public TruffleContext.Builder newInnerContextBuilder(String... permittedLanguages) {
            return TruffleContext.EMPTY.new Builder(this).permittedLanguages(permittedLanguages);
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
         * <p>
         * Note that classes added by this method are in the unnamed module.
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
                return LanguageAccessor.engineAccess().isHostObject(polyglotLanguageContext, value);
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
                return LanguageAccessor.engineAccess().asHostObject(polyglotLanguageContext, value);
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
                return LanguageAccessor.engineAccess().toGuestValue(null, hostObject, polyglotLanguageContext);
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
                return LanguageAccessor.engineAccess().isHostFunction(polyglotLanguageContext, value);
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
                return LanguageAccessor.engineAccess().isHostException(polyglotLanguageContext, exception);
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
                return LanguageAccessor.engineAccess().asHostException(polyglotLanguageContext, exception);
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
                return LanguageAccessor.engineAccess().isHostSymbol(polyglotLanguageContext, guestObject);
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
         * Returns <code>true</code> if context options are allowed to be modified for inner
         * contexts, or <code>false</code> if not. This method only indicates whether the embedder
         * granted wildcard all access for new privileges using
         * {@link org.graalvm.polyglot.Context.Builder#allowInnerContextOptions(boolean)}.
         * <p>
         * This method is useful to find out whether it is possible to specify options for inner
         * contexts using {@link TruffleContext.Builder#option(String, String)}, as options can only
         * be specified with all access enabled.
         *
         * @see TruffleContext.Builder#option(String, String)
         * @since 22.3
         */
        @TruffleBoundary
        public boolean isInnerContextOptionsAllowed() {
            try {
                return LanguageAccessor.engineAccess().isInnerContextOptionsAllowed(polyglotLanguageContext, this);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Returns {@code true} if access to files is allowed, else {@code false}.
         *
         * @since 22.3
         * @deprecated since 23.0; replaced by {@link #isFileIOAllowed()}.
         */
        @Deprecated(since = "23.0")
        public boolean isIOAllowed() {
            return isFileIOAllowed();
        }

        /**
         * Returns {@code true} if access to files is allowed, else {@code false}.
         *
         * @since 23.0
         */
        public boolean isFileIOAllowed() {
            try {
                return LanguageAccessor.engineAccess().isIOAllowed(polyglotLanguageContext, this);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Returns {@code true} if access to network sockets is allowed, else {@code false}.
         *
         * @since 23.0
         */
        public boolean isSocketIOAllowed() {
            try {
                return LanguageAccessor.engineAccess().isSocketIOAllowed(polyglotLanguageContext);
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
         *
         * @since 19.2
         * @deprecated in 24.1 use {@link #isPolyglotEvalAllowed(LanguageInfo)
         *             isPolyglotEvalAllowed(null) instead}. Note that language implementations
         *             should now check whether polyglot eval is allowed also for individual
         *             languages, as access could be denied only for an individual language.
         */
        @TruffleBoundary
        @Deprecated
        public boolean isPolyglotEvalAllowed() {
            return isPolyglotEvalAllowed(null);
        }

        /**
         * Returns <code>true</code> if the current language is allowed to evaluate guest
         * application provided code of the given language, else <code>false</code>. If this method
         * returns <code>true</code> then the current language has permission to use
         * {@link #parsePublic(Source, String...)} and {@link #getScopePublic(LanguageInfo)} for the
         * given language. If the given language is <code>null</code> then this method will return
         * <code>true</code> if polyglot access is allowed in principle, else <code>false</code>.
         * Languages may want to call this method twice. Once with <code>null</code> to determine
         * whether polyglot eval and scope based builtins should be available to the application at
         * all and once with the concrete language just before the access.
         * <p>
         * The result of this method is safe to be cached per language context, it is guaranteed to
         * not change for a context and target language combination.
         *
         * @see org.graalvm.polyglot.PolyglotAccess.Builder#allowEval(String, String) Embedders can
         *      restrict polyglot eval access between certain languages.
         * @see #parsePublic(Source, String...)
         * @see #getScopePublic(LanguageInfo)
         * @since 24.1
         */
        @TruffleBoundary
        public boolean isPolyglotEvalAllowed(LanguageInfo targetLanguage) {
            try {
                return LanguageAccessor.engineAccess().isPolyglotEvalAllowed(polyglotLanguageContext, targetLanguage);
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
         * Parses the source of a public or internal language and returns the parse result as
         * {@link CallTarget}. The {@link Source#getLanguage() language id} is used to identify the
         * {@link TruffleLanguage} to use to perform the
         * {@link #parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest)}. The names of
         * arguments are parameters for the resulting {#link CallTarget} that allow the
         * <code>source</code> to reference the actual parameters passed to
         * {@link CallTarget#call(java.lang.Object...)}.
         * <p>
         * The suffix <code>internal</code> in the method name indicates that the result of this
         * method is <b>not</b> safe to exposed to arbitrary guest code, by default. Use
         * {@link #parsePublic(Source, String...)} for that purpose instead. Compared to
         * {@link #parsePublic(Source, String...)} this method provides also access to
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
         *             entered, or if the language is neither a {@link #getPublicLanguages() public
         *             language} nor an {@link #getInternalLanguages() internal language}.
         * @throws IllegalArgumentException if the language is not allowed to evaluate code by the
         *             current language
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
         * The suffix <code>public</code> in the method name indicates that the result of this
         * method is safe to be exposed by default to guest applications. Public languages are
         * configured by the embedder to be accessible to the guest language program. For example,
         * in JavaScript, a call to the eval builtin should forward to
         * {@link #parsePublic(Source, String...)} as it contains code provided by the guest
         * language user. Parsing regular expressions with the internal regular expression engine
         * should call {@link #parseInternal(Source, String...)} instead, as this is considered an
         * implementation detail of the language.
         * <p>
         * It is recommended that the language uses {@link Env#parsePublic(Source, String...)} or
         * {@link Env#parseInternal(Source, String...)} instead of directly passing the Source to
         * the parser, in order to support code caching with {@link ContextPolicy#SHARED} and
         * {@link ContextPolicy#REUSE}.
         * <p>
         * Languages should check for {@link #isPolyglotEvalAllowed(LanguageInfo) eval permissions}
         * for each {@link #getPublicLanguages() public language} prior to calling this method,
         * otherwise {@link IllegalArgumentException} is thrown if not sufficient privileges are
         * available.
         *
         * @param source the source to evaluate
         * @param argumentNames the names of {@link CallTarget#call(java.lang.Object...)} arguments
         *            that can be referenced from the source
         * @return the call target representing the parsed result
         * @throws IllegalStateException if polyglot context associated with this environment is not
         *             entered, or if the language is not a {@link #getPublicLanguages() public
         *             language}
         * @throws IllegalArgumentException if the language is not allowed to evaluate code by the
         *             current language
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
         * Retrieves the host language used in this environment. The returned language can be used
         * to obtain the host language top scope object using
         * {@link #getScopeInternal(LanguageInfo)} method.
         *
         * @since 24.2
         */
        @TruffleBoundary
        public LanguageInfo getHostLanguage() {
            try {
                return LanguageAccessor.engineAccess().getHostLanguage(polyglotLanguageContext);
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
         * Returns the {@link LanguageInfo language info} for a given language class if available.
         * The class may be obtained with <code>InteropLibrary.getLanguage(Object)</code>. Throws an
         * {@link IllegalArgumentException} if the provided language is not registered.
         *
         * @param languageClass the language class to convert
         * @return the associated language info
         * @throws IllegalArgumentException if the language class is not valid.
         * @since 24.2
         */
        @TruffleBoundary
        public LanguageInfo getLanguageInfo(Class<? extends TruffleLanguage<?>> languageClass) {
            try {
                Objects.requireNonNull(languageClass);
                return LanguageAccessor.engineAccess().getLanguageInfo(polyglotLanguageContext, languageClass);
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
         * context. Inner language contexts can be created using
         * {@link #newInnerContextBuilder(String...)}.
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
                return LanguageAccessor.engineAccess().getTruffleContext(polyglotLanguageContext).currentAPI;
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
         * Returns a {@link TruffleFile} for given path. The returned {@link TruffleFile} access
         * depends on the file system used by the context and can vary from all access in case of
         * {@link IOAccess#ALL allowed IO} to no access in case of {@link IOAccess#NONE denied IO}.
         * When IO is not enabled by the {@code Context} the {@link TruffleFile} operations throw
         * {@link SecurityException}. The {@code getPublicTruffleFile} method should be used to
         * access user files or to implement language IO builtins.
         *
         * @param path the absolute or relative path to create {@link TruffleFile} for
         * @return {@link TruffleFile}
         * @throws UnsupportedOperationException when the {@link FileSystem} supports only
         *             {@link URI}
         * @throws IllegalArgumentException if the {@code path} string cannot be converted to a
         *             {@link Path}
         * @see IOAccess
         * @see Builder#allowIO(IOAccess)
         * @since 19.3.0
         */
        @TruffleBoundary
        public TruffleFile getPublicTruffleFile(String path) {
            checkDisposed();
            FileSystemContext fs = getPublicFileSystemContext();
            try {
                return new TruffleFile(fs, fs.fileSystem.parsePath(path));
            } catch (UnsupportedOperationException | IllegalArgumentException e) {
                throw e;
            } catch (Throwable t) {
                throw TruffleFile.wrapHostException(t, fs);
            }
        }

        /**
         * Returns a {@link TruffleFile} for given path. See {@link #getPublicTruffleFile(String)}
         * for detailed information.
         *
         * @param uri the {@link URI} to create {@link TruffleFile} for
         * @return {@link TruffleFile}
         * @throws UnsupportedOperationException when {@link URI} scheme is not supported
         * @throws IllegalArgumentException if preconditions on the {@code uri} do not hold.
         * @throws java.nio.file.FileSystemNotFoundException is the file system, identified by the
         *             {@code uri}, does not exist and cannot be created automatically
         * @since 19.3.0
         */
        @TruffleBoundary
        public TruffleFile getPublicTruffleFile(URI uri) {
            checkDisposed();
            FileSystemContext fs = getPublicFileSystemContext();
            try {
                return new TruffleFile(fs, fs.fileSystem.parsePath(uri));
            } catch (UnsupportedOperationException | IllegalArgumentException e) {
                throw e;
            } catch (Throwable t) {
                throw TruffleFile.wrapHostException(t, fs);
            }
        }

        /**
         * Returns a {@link TruffleFile} for given path. This method allows to access files in the
         * guest language home even if file system privileges might be limited or denied. If the
         * path locates a file under the guest language home it is guaranteed that the returned
         * {@link TruffleFile} has at least read access. Otherwise, the returned {@link TruffleFile}
         * access depends on the file system used by the context and can vary from all access in
         * case of allowed IO to no access in case of denied IO. The {@code getInternalTruffleFile}
         * method should be used to read language standard libraries in a language home. This method
         * is an equivalent to {@code getTruffleFileInternal(path, p -> true)}. For security reasons
         * the language should check that the file is a language source file in language standard
         * libraries folder before using this method for a file in a language home. For performance
         * reasons consider to use {@link #getTruffleFileInternal(String, Predicate)} and perform
         * the language standard libraries check using a predicate.
         *
         * @param path the absolute or relative path to create {@link TruffleFile} for
         * @return {@link TruffleFile}
         * @since 19.3.0
         * @throws UnsupportedOperationException when the {@link FileSystem} supports only
         *             {@link URI}
         * @throws IllegalArgumentException if the {@code path} string cannot be converted to a
         *             {@link Path}
         * @see #getTruffleFileInternal(String, Predicate)
         * @see #getPublicTruffleFile(java.lang.String)
         */
        @TruffleBoundary
        public TruffleFile getInternalTruffleFile(String path) {
            checkDisposed();
            FileSystemContext fs = getInternalFileSystemContext();
            try {
                return new TruffleFile(fs, fs.fileSystem.parsePath(path));
            } catch (UnsupportedOperationException | IllegalArgumentException e) {
                throw e;
            } catch (Throwable t) {
                throw TruffleFile.wrapHostException(t, fs);
            }
        }

        /**
         * Returns {@link TruffleFile} for given {@link URI}. See
         * {@link #getInternalTruffleFile(String)} for detailed information.
         *
         * @param uri the {@link URI} to create {@link TruffleFile} for
         * @return {@link TruffleFile}
         * @since 19.3.0
         * @throws UnsupportedOperationException when {@link URI} scheme is not supported
         * @throws IllegalArgumentException if preconditions on the {@code uri} do not hold.
         * @throws java.nio.file.FileSystemNotFoundException is the file system, identified by the
         *             {@code uri}, does not exist and cannot be created automatically
         * @see #getTruffleFileInternal(URI, Predicate)
         * @see #getPublicTruffleFile(java.net.URI)
         */
        @TruffleBoundary
        public TruffleFile getInternalTruffleFile(URI uri) {
            checkDisposed();
            FileSystemContext fs = getInternalFileSystemContext();
            try {
                return new TruffleFile(fs, fs.fileSystem.parsePath(uri));
            } catch (UnsupportedOperationException | IllegalArgumentException e) {
                throw e;
            } catch (Throwable t) {
                throw TruffleFile.wrapHostException(t, fs);
            }
        }

        /**
         * Returns a {@link TruffleFile} for the given path. This method allows to access files in
         * the guest language home even if file system privileges might be limited or denied. If the
         * path locates a file under the guest language home and satisfies the given {@code filter},
         * it is guaranteed that the returned {@link TruffleFile} has at least read access.
         * Otherwise, the returned {@link TruffleFile} access depends on the file system used by the
         * context and can vary from all access in case of allowed IO to no access in case of denied
         * IO.
         * <p>
         * A common use case for this method is a filter granting read access to the language
         * standard libraries.
         * <p>
         * The method performs the following checks:
         * <ol>
         * <li>If the IO is enabled by the {@link Context} an accessible {@link TruffleFile} is
         * returned without any other checks.</li>
         * <li>If the given path does not locate a file in a language home a {@link TruffleFile}
         * with no access is returned.</li>
         * <li>If the given filter accepts the file a readable {@link TruffleFile} is returned.
         * Otherwise, a {@link TruffleFile} with no access is returned.</li>
         * </ol>
         * <p>
         * The relation to {@link #getPublicTruffleFile(String)} and
         * {@link #getInternalTruffleFile(String)} is:
         * <ul>
         * <li>The {@link #getPublicTruffleFile(String)} is equivalent to
         * {@code getTruffleFileInternal(path, p -> false)}.</li>
         * <li>The {@link #getInternalTruffleFile(String)} is equivalent to
         * {@code getTruffleFileInternal(path, p -> true)}.</li>
         * </ul>
         *
         * @param path the absolute or relative path to create {@link TruffleFile} for
         * @param filter to enable read access to {@link TruffleFile}. Multiple invocations of
         *            {@code filter.test(file)} must consistently return {@code true} or
         *            consistently return {@code false} for a given path.
         * @return {@link TruffleFile}
         * @throws UnsupportedOperationException when the {@link FileSystem} supports only
         *             {@link URI}
         * @throws IllegalArgumentException if the {@code path} string cannot be converted to a
         *             {@link Path}
         * @since 21.1.0
         * @see #getTruffleFileInternal(URI, Predicate)
         * @see #getPublicTruffleFile(String)
         * @see #getInternalTruffleFile(String)
         *
         */
        @TruffleBoundary
        public TruffleFile getTruffleFileInternal(String path, Predicate<TruffleFile> filter) {
            return getTruffleFileInternalImpl(path, filter, TruffleFileFactory.PATH);
        }

        /**
         * Returns a {@link TruffleFile} for given URI. See
         * {@link #getTruffleFileInternal(String, Predicate)} for detailed information.
         *
         * @param uri the {@link URI} to create {@link TruffleFile} for
         * @param filter to enable read access to {@link TruffleFile}. Multiple invocations of
         *            {@code filter.test(file)} must consistently return {@code true} or
         *            consistently return {@code false} for a given path.
         * @return {@link TruffleFile}
         * @throws UnsupportedOperationException when the {@link FileSystem} supports only
         *             {@link URI}
         * @throws IllegalArgumentException if preconditions on the {@code uri} do not hold.
         * @throws java.nio.file.FileSystemNotFoundException is the file system, identified by the
         *             {@code uri}, does not exist and cannot be created automatically
         * @since 21.1.0
         * @see #getTruffleFileInternal(String, Predicate)
         * @see #getPublicTruffleFile(URI)
         * @see #getInternalTruffleFile(URI)
         *
         */
        @TruffleBoundary
        public TruffleFile getTruffleFileInternal(URI uri, Predicate<TruffleFile> filter) {
            return getTruffleFileInternalImpl(uri, filter, TruffleFileFactory.URI);
        }

        private <P> TruffleFile getTruffleFileInternalImpl(P path, Predicate<TruffleFile> isStdLibFile, TruffleFileFactory<P> truffleFileFactory) {
            checkDisposed();
            FileSystemContext publicFsContext = getPublicFileSystemContext();
            if (LanguageAccessor.engineAccess().hasNoAccess(publicFsContext.fileSystem)) {
                FileSystemContext internalFsContext = getInternalFileSystemContext();
                TruffleFile internalFile = truffleFileFactory.apply(path, internalFsContext);
                if (LanguageAccessor.engineAccess().getRelativePathInResourceRoot(internalFile) != null && isStdLibFile.test(internalFile.getAbsoluteFile())) {
                    return internalFile;
                }
            }
            return truffleFileFactory.apply(path, publicFsContext);
        }

        private abstract static class TruffleFileFactory<P> implements BiFunction<P, FileSystemContext, TruffleFile> {

            static final TruffleFileFactory<String> PATH = new TruffleFileFactory<>() {
                @Override
                Path parsePath(String path, FileSystemContext fileSystemContext) {
                    return fileSystemContext.fileSystem.parsePath(path);
                }
            };

            static final TruffleFileFactory<URI> URI = new TruffleFileFactory<>() {
                @Override
                public Path parsePath(URI uri, FileSystemContext fileSystemContext) {
                    return fileSystemContext.fileSystem.parsePath(uri);
                }
            };

            private TruffleFileFactory() {
            }

            @Override
            public final TruffleFile apply(P p, FileSystemContext fileSystemContext) {
                try {
                    return new TruffleFile(fileSystemContext, parsePath(p, fileSystemContext));
                } catch (UnsupportedOperationException e) {
                    throw e;
                } catch (Throwable t) {
                    throw TruffleFile.wrapHostException(t, fileSystemContext);
                }
            }

            abstract Path parsePath(P p, FileSystemContext fileSystemContext);
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
                throw TruffleFile.wrapHostException(t, fileSystemContext);
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
                throw TruffleFile.wrapHostException(t, fs);
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
                throw TruffleFile.wrapHostException(t, fs);
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
                throw new SecurityException("Process creation is not allowed, to enable it set Context.Builder.allowCreateProcess(true).");
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
                throw TruffleFile.wrapHostException(t, fs);
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
                throw TruffleFile.wrapHostException(t, fs);
            }
        }

        /**
         * @since 20.3.0
         * @deprecated since 22.1; replaced by {@link #createHostAdapter(Object[])}.
         */
        @Deprecated(since = "22.1")
        @TruffleBoundary
        public Object createHostAdapterClass(Class<?>[] types) {
            Objects.requireNonNull(types, "types");
            return createHostAdapterClassLegacyImpl(types, null);
        }

        /**
         * @since 20.3.0
         * @deprecated since 22.1; replaced by
         *             {@link #createHostAdapterWithClassOverrides(Object[], Object)}.
         */
        @Deprecated(since = "22.1")
        @TruffleBoundary
        public Object createHostAdapterClassWithStaticOverrides(Class<?>[] types, Object classOverrides) {
            Objects.requireNonNull(types, "types");
            Objects.requireNonNull(classOverrides, "classOverrides");
            return createHostAdapterClassLegacyImpl(types, classOverrides);
        }

        /**
         * Creates a Java host adapter class that can be
         * {@linkplain com.oracle.truffle.api.interop.InteropLibrary#instantiate instantiated} with
         * a guest object (as the last argument) in order to create adapter instances of the
         * provided host types, (non-final) methods of which delegate to the guest object's
         * {@linkplain com.oracle.truffle.api.interop.InteropLibrary#isMemberInvocable invocable}
         * members. Implementations must be
         * {@linkplain org.graalvm.polyglot.HostAccess.Builder#allowImplementations(Class) allowed}
         * for these types. The returned host adapter class is an instantiable
         * {@linkplain #isHostObject(Object) host object} that is also a
         * {@linkplain com.oracle.truffle.api.interop.InteropLibrary#isMetaObject meta object}, so
         * {@link com.oracle.truffle.api.interop.InteropLibrary#isMetaInstance isMetaInstance} can
         * be used to check if an object is an instance of this adapter class. See usage example
         * below.
         * <p>
         * Please note that only classes from the unnamed module or classes exported to the unnamed
         * module can be used in <code>types</code>. The generated host adapter class is also in the
         * unnamed module.
         * <p>
         * A host class is generated as follows:
         * <p>
         * For every protected or public constructor in the extended class, the adapter class will
         * have one public constructor (visibility of protected constructors in the extended class
         * is promoted to public).
         * <p>
         * For every super constructor, a constructor taking a trailing {@link Value} argument
         * preceded by original constructor's arguments is generated. When such a constructor is
         * invoked, the passed {@link Value}'s member functions are used to implement and/or
         * override methods on the original class, dispatched by name. A single invocable member
         * will act as the implementation for all overloaded methods of the same name. When methods
         * on an adapter instance are invoked, the functions are invoked having the {@link Value}
         * passed in the instance constructor as their receiver. Subsequent changes to the members
         * of that {@link Value} (reassignment or removal of its functions) are reflected in the
         * adapter instance; the method implementations are not bound to functions at constructor
         * invocation time.
         * <p>
         * The generated host class extends all non-final public or protected methods, and forwards
         * their invocations to the guest object provided to the constructor. If the guest object
         * does not contain an invocable member with that name, the super/default method is invoked
         * instead. If the super method is abstract, an {@link AbstractMethodError} is thrown.
         * <p>
         * If the original types collectively have only one abstract method, or have several of
         * them, but all share the same name, the constructor(s) will check if the {@link Value} is
         * executable, and if so, will use the passed function as the implementation for all
         * abstract methods. For consistency, any concrete methods sharing the single abstract
         * method name will also be overridden by the function.
         * <p>
         * For non-void methods, all the conversions supported by {@link Value#as} will be in effect
         * to coerce the guest methods' return value to the expected Java return type.
         * <p>
         * Instances of the host class have the following additional special members:
         * <ul>
         * <li>{@code super}: provides access to the super methods of the host class via a wrapper
         * object. Can be used to call super methods from guest method overrides.
         * <li>{@code this}: returns the original guest object.
         * </ul>
         * <p>
         * Example:<br>
         *
         * <pre>
         * <code>
         * Object hostClass = env.createHostAdapter(new Object[]{
         *      env.asHostSymbol(Superclass.class),
         *      env.asHostSymbol(Interface.class)});
         * // generates a class along the lines of:
         *
         * public class Adapter extends Superclass implements Interface {
         *   private Value delegate;
         *
         *   public Adapter(Value delegate) { this.delegate = delegate; }
         *
         *   public method(Object... args) {
         *      if (delegate.canInvokeMember("method") {
         *        return delegate.invokeMember("method", args);
         *      } else {
         *        return super.method(args);
         *      }
         *   }
         * }
         *
         * // and can be instantiated as follows:
         * Object instance = InteropLibrary.getUncached().instantiate(hostClass, guestObject);
         * assert InteropLibrary.getUncached().isMetaInstance(hostClass, instance);
         * </code>
         * </pre>
         *
         * @param types the types to extend. Must be non-null and contain at least one extensible
         *            superclass or interface, and at most one superclass. All types must be public,
         *            accessible, and allow implementation.
         * @return a host class that can be instantiated to create instances of a host class that
         *         extends all the provided host types. The host class may be cached.
         * @throws IllegalArgumentException if the types are not extensible or more than one
         *             superclass is given.
         * @throws SecurityException if host access does not allow creating adapter classes for
         *             these types.
         * @throws UnsupportedOperationException if creating adapter classes is not supported on
         *             this runtime at all, which is currently the case for native images.
         * @throws NullPointerException if {@code types} is null
         *
         * @see #createHostAdapterWithClassOverrides(Object[], Object)
         * @since 22.1
         */
        @TruffleBoundary
        public Object createHostAdapter(Object[] types) {
            Objects.requireNonNull(types, "types");
            return createHostAdapterClassImpl(types, null);
        }

        /**
         * Like {@link #createHostAdapter(Object[])} but creates a Java host adapter class with
         * class-level overrides, i.e., the guest object provided as {@code classOverrides} is
         * statically bound to the class rather than instances of the class. Returns a host class
         * that can be {@linkplain com.oracle.truffle.api.interop.InteropLibrary#instantiate
         * instantiated} to create instances of the provided host {@code types}, (non-final) methods
         * of which delegate to the guest object provided as {@code classOverrides}.
         * <p>
         * Allows creating host adapter class hierarchies, i.e., the returned class can be used as a
         * superclass of other host adapter classes. Note that classes created with method cannot be
         * cached. Therefore, this feature should be used sparingly.
         * <p>
         * See {@link #createHostAdapter(Object[])} for more details.
         *
         * @param types the types to extend. Must be non-null and contain at least one extensible
         *            superclass or interface, and at most one superclass. All types must be public,
         *            accessible, and allow implementation.
         * @param classOverrides a guest object with class-level overrides. If not null, the object
         *            is bound to the class, not to any instance. Consequently, the generated
         *            constructors are changed to not take an object; all instances will share the
         *            same overrides object. Note that since a new class has to be generated for
         *            every overrides object instance and cannot be shared, use of this feature is
         *            discouraged; it is provided only for compatibility reasons.
         * @return a host class symbol that can be instantiated to create instances of a host class
         *         that extends all the provided host types.
         * @throws IllegalArgumentException if the types are not extensible or more than one
         *             superclass is given.
         * @throws SecurityException if host access does not allow creating adapter classes for
         *             these types.
         * @throws UnsupportedOperationException if creating adapter classes is not supported on
         *             this runtime at all, which is currently the case for native images.
         * @throws NullPointerException if either {@code types} or {@code classOverrides} is null.
         *
         * @see #createHostAdapter(Object[])
         * @since 22.1
         */
        @TruffleBoundary
        public Object createHostAdapterWithClassOverrides(Object[] types, Object classOverrides) {
            Objects.requireNonNull(types, "types");
            Objects.requireNonNull(classOverrides, "classOverrides");
            return createHostAdapterClassImpl(types, classOverrides);
        }

        /**
         * Returns the {@link TruffleFile} representing the target directory of an internal
         * resource. The internal resource is guaranteed to be fully
         * {@link InternalResource#unpackFiles(InternalResource.Env, Path)} s unpacked} before this
         * method returns. When this method is called for the first time and the resource is not
         * cached than the resource will be unpacked. Unpacking an internal resource can be an
         * expensive operation, but the implementation makes sure that unpacked internal resources
         * are cached.
         * <p>
         * The returned {@link TruffleFile} will only grant read-only access to the target
         * directory, but access is provided even if IO access is disabled.
         * <p>
         * On a HotSpot VM the internal resource is typically cached in the user directory, so
         * unpacking would be repeated once per operating system user. When the language was
         * compiled using native-image internal resources are unpacked at native-image compile time
         * and stored relative to the native-image.
         *
         * @param resource the resource class to load
         * @throws IllegalArgumentException if {@code resource} is not associated with this language
         * @throws IOException in case of IO error
         * @since 23.1
         */
        public TruffleFile getInternalResource(Class<? extends InternalResource> resource) throws IOException {
            return LanguageAccessor.ENGINE.getInternalResource(this.polyglotLanguageContext, resource);
        }

        /**
         * Returns the {@link TruffleFile} representing the target directory of an internal
         * resource. Unlike the {@link #getInternalResource(Class)}, this method can be used for
         * optional resources whose classes may not exist at runtime. In this case the optional
         * resource must be unpacked at build time, see
         * {@link Engine#copyResources(Path, String...)}. If the resource with the specified
         * {@code resourceId} is not associated to this language, the function returns {@code null}.
         *
         * @param resourceId unique id of the resource to be loaded
         * @return internal resource directory or {@code null} if resource with the
         *         {@code resourceId} is not associated with this language
         * @throws IOException in case of IO error
         * @see #getInternalResource(Class)
         * @see Engine#copyResources(Path, String...)
         * @since 23.1
         */
        public TruffleFile getInternalResource(String resourceId) throws IOException {
            return LanguageAccessor.ENGINE.getInternalResource(this.polyglotLanguageContext, resourceId);
        }

        /**
         * Find or create a context-bound logger. The returned {@link TruffleLogger} always uses a
         * logging handler and options from this execution environment context and does not depend
         * on being entered on any thread.
         * <p>
         * If a logger with a given name already exists it's returned. Otherwise, a new logger is
         * created.
         * <p>
         * Unlike loggers created by
         * {@link TruffleLogger#getLogger(java.lang.String, java.lang.String)
         * TruffleLogger.getLogger} loggers created by this method are bound to a single context.
         * There may be more logger instances having the same name but each bound to a different
         * context. Languages should never store the returned logger into a static field. If the
         * context policy is more permissive than {@link ContextPolicy#EXCLUSIVE} the returned
         * logger must not be stored in a TruffleLanguage subclass. It is recommended to create all
         * language loggers in {@link TruffleLanguage#createContext(Env)}.
         *
         * @param loggerName the name of a {@link TruffleLogger}, if a {@code loggerName} is null or
         *            empty a root logger for language or instrument is returned
         * @return a {@link TruffleLogger}
         * @since 21.1
         *
         */
        @TruffleBoundary
        public TruffleLogger getLogger(String loggerName) {
            String languageId = this.spi.languageInfo.getId();
            TruffleLogger.LoggerCache loggerCache = (TruffleLogger.LoggerCache) LanguageAccessor.engineAccess().getContextLoggerCache(this.polyglotLanguageContext);
            return TruffleLogger.getLogger(languageId, loggerName, loggerCache);
        }

        /**
         * Find or create a context-bound logger. The returned {@link TruffleLogger} always uses a
         * logging handler and options from this execution environment context and does not depend
         * on being entered on any thread.
         * <p>
         * If a logger with a given name already exists it's returned. Otherwise, a new logger is
         * created.
         * <p>
         * Unlike loggers created by
         * {@link TruffleLogger#getLogger(java.lang.String, java.lang.String)
         * TruffleLogger.getLogger} loggers created by this method are bound to a single context.
         * There may be more logger instances having the same name but each bound to a different
         * context. Languages should never store the returned logger into a static field. If the
         * context policy is more permissive than {@link ContextPolicy#EXCLUSIVE} the returned
         * logger must not be stored in a TruffleLanguage subclass. It is recommended to create all
         * language loggers in {@link TruffleLanguage#createContext(Env)}.
         *
         * @param forClass the {@link Class} to create a logger for
         * @return a {@link TruffleLogger}
         * @since 21.1
         */
        @TruffleBoundary
        public TruffleLogger getLogger(Class<?> forClass) {
            Objects.requireNonNull(forClass, "Class must be non null.");
            return getLogger(forClass.getName());
        }

        /**
         * Submits a thread local action to be performed at the next guest language safepoint on a
         * provided set of threads, once for each thread. If the threads array is <code>null</code>
         * then the thread local action will be performed on all alive threads. The submitted
         * actions are processed in the same order as they are submitted in. The action can be
         * synchronous or asynchronous, side-effecting or non-side-effecting. Please see
         * {@link ThreadLocalAction} for details.
         * <p>
         * It is ensured that a thread local action will get processed as long as the thread stays
         * active for this context. If a thread becomes inactive before the action can get processed
         * then the action will not be performed for this thread. If a thread becomes active while
         * the action is being processed then the action will be performed for that thread as long
         * as the thread filter includes the thread or <code>null</code> was passed. Already started
         * synchronous actions will block on activation of a new thread. If the synchronous action
         * was not yet started on any thread, then the synchronous action will also be performed for
         * the newly activated thread.
         * <p>
         * The method returns a {@link Future} instance that allows to wait for the thread local
         * action to complete or to cancel a currently performed event.
         * <p>
         * Example Usage:
         *
         * <pre>
         * Env env; // supplied
         *
         * env.submitThreadLocal(null, new ThreadLocalAction(true, true) {
         *     &#64;Override
         *     protected void perform(Access access) {
         *         // perform action
         *     }
         * });
         * </pre>
         * <p>
         * By default thread-local actions are executed once per configured thread and do not repeat
         * themselves. If a ThreadLocalAction is configured to be
         * {@link ThreadLocalAction#ThreadLocalAction(boolean, boolean, boolean) recurring} then the
         * action will automatically be rescheduled in the same configuration until it is
         * {@link Future#cancel(boolean) cancelled}. For recurring actions, an invocation of
         * {@link Future#get()} will only wait for the first action to to be performed.
         * {@link Future#isDone()} will return <code>true</code> only if the action was canceled.
         * Canceling a recurring action will result in the current event being canceled and no
         * further events being submitted. Using recurring events should be preferred over
         * submitting the event again for the current thread while performing the thread-local
         * action as recurring events are also resubmitted in case all threads leave and later
         * reenter.
         * <p>
         * If the thread local action future needs to be waited on and this might be prone to
         * deadlocks the
         * {@link TruffleSafepoint#setBlocked(Node, Interrupter, Interruptible, Object, Runnable, Consumer)
         * blocking API} can be used to allow other thread local actions to be processed while the
         * current thread is waiting. The returned {@link Future#get()} method can be used as
         * {@link Interruptible}. If the underlying polyglot context is already closed, the method
         * returns a completed {@link Future}.
         *
         * @param threads the threads to execute the action on. <code>null</code> for all threads
         * @param action the action to perform on that thread.
         * @see ThreadLocalAction
         * @see TruffleSafepoint
         * @since 21.1
         */
        // Note keep the javadoc in sync with TruffleInstrument.Env.submitThreadLocal
        public Future<Void> submitThreadLocal(Thread[] threads, ThreadLocalAction action) {
            return submitThreadLocalInternal(threads, action, true);
        }

        /**
         * Registers {@link Closeable} for automatic close on context dispose. In most cases,
         * closeable should be closed using try-with-resources construct. When a closeable must keep
         * being opened for the lifetime of a context it should be registered using this method for
         * automatic close on context dispose. The registered {@link Closeable} is weakly
         * referenced. The guest language must strongly reference it otherwise, it may be garbage
         * collected before it's closed.
         * <p>
         * If the registered closeable throws an {@link IOException} during close, the thrown
         * exception does not prevent successful context dispose. The IOException is logged to the
         * engine logger with a {@link Level#WARNING} level. Other exceptions are rethrown as
         * internal {@link PolyglotException}.
         *
         * @param closeable to be closed on context dispose.
         * @since 21.2
         */
        public void registerOnDispose(Closeable closeable) {
            LanguageAccessor.engineAccess().registerOnDispose(polyglotLanguageContext, closeable);
        }

        /**
         * Returns the context's {@link SandboxPolicy}. A language can use the returned sandbox
         * policy to make language-specific verifications that the sandbox requirements are met.
         * These verifications should be made as early as possible in the
         * {@link TruffleLanguage#createContext(Env)} method.
         *
         * @see SandboxPolicy
         * @see TruffleLanguage#createContext(Env)
         * @since 23.0
         */
        public SandboxPolicy getSandboxPolicy() {
            return LanguageAccessor.engineAccess().getContextSandboxPolicy(this.polyglotLanguageContext);
        }

        /**
         * Returns the scope object of a non-null {@link #getPublicLanguages() public language}. The
         * returned value follows the contract of {@link TruffleLanguage#getScope(Object)} of the
         * other language, therefore it must be an
         * {@link com.oracle.truffle.api.interop.InteropLibrary#isScope(Object) interop scope
         * object} and may have
         * {@link com.oracle.truffle.api.interop.InteropLibrary#hasScopeParent(Object) parent
         * scopes}. The bindings object exposes all top scopes variables as flattened
         * {@link com.oracle.truffle.api.interop.InteropLibrary#getMembers(Object) members}. In
         * addition to being a scope the returned object may implement any number of the
         * {@link com.oracle.truffle.api.interop.InteropLibrary interop traits}. The interop members
         * of the returned object are typically writable, but that is not guaranteed.
         * <p>
         * The suffix <code>public</code> in the method name indicates that the result of this
         * method is safe to be exposed by default to guest applications. For example, languages
         * should use this method to implement the semantics of their polyglot bindings builtin, but
         * is not limited to that.
         * <p>
         * All {@link #getPublicLanguages() public} languages with
         * {@link #isPolyglotEvalAllowed(LanguageInfo) eval permissions} are accessible. If a
         * language is not accessible then an {@link SecurityException} is thrown. The scope of the
         * current language is always accessible, but it is recommended to use a language specific
         * way to access symbols of the current language.
         * <p>
         * Languages should check for {@link #isPolyglotEvalAllowed(LanguageInfo) eval permissions}
         * for each {@link #getPublicLanguages() public language} prior to calling this method,
         * otherwise it might cause {@link SecurityException} if not sufficient privileges are
         * available.
         *
         * @return the scope, or <code>null</code> if the requested language does not support such a
         *         concept
         * @throws IllegalStateException if polyglot context associated with this environment is not
         *             entered
         * @throws SecurityException if polyglot scope access is not enabled for this language.
         * @see #getScopeInternal(LanguageInfo)
         * @see #isPolyglotEvalAllowed(LanguageInfo)
         * @see #getPolyglotBindings()
         * @since 24.1
         */
        @TruffleBoundary
        public Object getScopePublic(LanguageInfo language) throws IllegalArgumentException {
            Objects.requireNonNull(language);
            checkDisposed();
            try {
                Object result = LanguageAccessor.engineAccess().getScope(polyglotLanguageContext, language, false);
                assert result == null || LanguageAccessor.interopAccess().isScopeObject(result) : String.format("%s is not a scope", result);
                return result;
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /**
         * Returns the scope object of a non-null {@link #getInternalLanguages() internal language}.
         * Works the same as {@link #getScopePublic(LanguageInfo)} but it also returns scopes of
         * internal and dependent languages.
         * <p>
         * The suffix <code>internal</code> in the method name indicates that the result of this
         * method is <b>not</b> safe to be exposed to arbitrary guest code, by default. Use
         * {@link #getScopePublic(LanguageInfo)} for that purpose instead. For example, this method
         * could be used to access a fixed set of symbols from the Truffle Native Interface, e.g. to
         * load native language extensions. Languages may decide to expose scopes of internal
         * languages to arbitrary guest code for testing purposes. Make sure this option is disabled
         * by default and enabled using an {@link OptionCategory#INTERNAL internal} option only.
         * <p>
         * The scope of the current language is always accessible, but it is recommended to use a
         * language specific way to access symbols of the current language.
         * <p>
         * It is recommended to only use this method for {@link LanguageInfo#isInternal() internal}
         * or {@link TruffleLanguage.Registration#dependentLanguages() dependent languages}. For
         * convenience, this method will also succeed for all accessible
         * {@link #getPublicLanguages() public languages} with
         * {@link #isPolyglotEvalAllowed(LanguageInfo) eval permissions}.
         *
         * @return the scope, or <code>null</code> if the requested language does not support such a
         *         concept
         * @throws IllegalStateException if polyglot context associated with this environment is not
         *             entered
         * @throws SecurityException if polyglot scope access is not enabled for this language.
         * @see #getScopePublic(LanguageInfo)
         * @since 24.1
         */
        @TruffleBoundary
        public Object getScopeInternal(LanguageInfo language) {
            checkDisposed();
            try {
                Object result = LanguageAccessor.engineAccess().getScope(polyglotLanguageContext, language, true);
                assert result == null || LanguageAccessor.interopAccess().isScopeObject(result) : String.format("%s is not a scope", result);
                return result;
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        /*
         * For reflective use in tests.
         */
        Future<Void> submitThreadLocalInternal(Thread[] threads, ThreadLocalAction action, boolean needsEnter) {
            checkDisposed();
            try {
                return LanguageAccessor.ENGINE.submitThreadLocal(LanguageAccessor.ENGINE.getContext(polyglotLanguageContext), polyglotLanguageContext, threads, action, needsEnter);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
            }
        }

        private Object createHostAdapterClassLegacyImpl(Class<?>[] types, Object classOverrides) {
            checkDisposed();
            Object[] hostTypes = new Object[types.length];
            for (int i = 0; i < types.length; i++) {
                Class<?> type = types[i];
                hostTypes[i] = asHostSymbol(type);
            }
            return createHostAdapterClassImpl(hostTypes, classOverrides);
        }

        private Object createHostAdapterClassImpl(Object[] types, Object classOverrides) {
            checkDisposed();
            try {
                if (types.length == 0) {
                    throw new IllegalArgumentException("Expected at least one type.");
                }
                return LanguageAccessor.engineAccess().createHostAdapterClass(polyglotLanguageContext, types, classOverrides);
            } catch (Throwable t) {
                throw engineToLanguageException(t);
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
     * Represents a reference to the current language instance. The current language is a thread
     * local value that potentially changes when polyglot context is entered or left. Language
     * references are created using {@link #create(Class)} and are intended to be stored in static
     * final fields and accessed at runtime using {@link LanguageReference#get(Node)} with the
     * current {@link Node}, if available, as parameter.
     * <p>
     * Example intended usage:
     *
     * See {@link ContextReference} for a full usage example.
     *
     * @since 0.25 revised in 21.3
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
         * Returns the current language instance associated with the current thread. An enclosing
         * node should be provided as parameter if available, otherwise <code>null</code> may be
         * provided. This method is designed to be called safely from compiled code paths. In order
         * to maximize efficiency in compiled code paths, a partial evaluation constant and adopted
         * node should be passed as parameter. If this is the case then the return language will get
         * constant folded in compiled code paths.
         * <p>
         * The current language is fixed per {@link RootNode}. It is recommended to *not* cache
         * values of the language in the AST to reduce footprint. Getting it through a language
         * reference will either constant fold or be very efficient in compiled code paths.
         * <p>
         * If a language is accessed on an unknown Thread, or in the language class constructor an
         * {@link IllegalStateException} is thrown.
         *
         * @see ContextReference for a full usage example
         * @since 21.3
         */
        public abstract L get(Node node);

        /**
         * Creates a new instance of a langauge reference for an registered language. Throws
         * {@link IllegalArgumentException} if the provided language class is not
         * {@link Registration registered}. Guaranteed to always return the same context reference
         * for a given language class.
         * <p>
         * See {@link LanguageReference} for a usage example.
         *
         * @since 21.3
         */
        public static <T extends TruffleLanguage<?>> LanguageReference<T> create(Class<T> languageClass) {
            Objects.requireNonNull(languageClass);
            return LanguageAccessor.ENGINE.createLanguageReference(languageClass);
        }

    }

    /**
     * Represents a reference to the current context. The current context is a thread local value
     * that changes when polyglot context is entered or left. Context references are created using
     * {@link #create(Class)} and are intended to be stored in static final fields and accessed at
     * runtime using {@link ContextReference#get(Node)} with the current {@link Node}, if available,
     * as parameter.
     * <p>
     * Example intended usage:
     *
     * <pre>
     * public final class MyContext  {
     *
     *     private static final ContextReference&lt;MyContext&gt; REFERENCE =
     *                    ContextReference.create(MyLanguage.class);
     *
     *     public static MyContext get(Node node) {
     *          return REFERENCE.get(node);
     *     }
     * }
     *
     * &#64;Registration(...)
     * public final class MyLanguage extends TruffleLanguage<MyContext> {
     *
     *    // ...
     *
     *    private static final LanguageReference&lt;MyLanguage&gt; REFERENCE =
     *                   LanguageReference.create(MyLanguage.class);
     *
     *    public static MyLanguage get(Node node) {
     *         return REFERENCE.get(node);
     *    }
     * }
     *
     * public final class MyLanguageNode extends Node {
     *
     *     // ...
     *     public Object execute(VirtualFrame frame) {
     *         MyContext currentContext = getContext();
     *         MyLanguage currentLanguage = getLanguage();
     *
     *         // use context or language on the fast-path
     *
     *         // references can also be used behind the boundary
     *         exampleBoundary();
     *     }
     *
     *     &#64;TruffleBoundary
     *     public void exampleBoundary() {
     *        MyLanguage currentLanguage = MyLanguage.get(null);
     *        MyContext currentContext = MyContext.get(null);
     *
     *        // use context or language on the slow-path
     *     }
     *
     *     public final MyLanguage getLanguage() {
     *         return MyLanguage.get(this);
     *     }
     *
     *     public final MyContext getContext() {
     *         return MyContext.get(this);
     *     }
     * }
     * </pre>
     *
     * @since 0.25 revised in 21.3
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
         * Returns the current language context associated with the current thread. An enclosing
         * node should be provided as parameter if available, otherwise <code>null</code> may be
         * provided. This method is designed to be called safely from compiled code paths. In order
         * to maximize efficiency in compiled code paths, a partial evaluation constant and adopted
         * node should be passed as parameter. If this is the case then the return context will get
         * constant folded in compiled code paths if there is only a single context instance for the
         * enclosing engine or lookup location/node.
         * <p>
         * The current context might vary between {@link RootNode#execute(VirtualFrame) executions}
         * if resources or code is {@link ContextPolicy#SHARED shared} between multiple contexts or
         * when used as part of an InteropLibrary export. It is recommended to *not* cache values of
         * the context in the AST to reduce footprint. Getting it through a context reference will
         * either constant fold or be very efficient in compiled code paths.
         * <p>
         * If a context is accessed during {@link TruffleLanguage#createContext(Env) context
         * creation}, on an unknown Thread, or in the language class constructor an
         * {@link IllegalStateException} is thrown.
         *
         * @see ContextReference for a full usage example
         * @since 21.3
         */
        public abstract C get(Node node);

        /**
         * Creates a new instance of a context reference for an registered language. Throws
         * {@link IllegalArgumentException} if the provided language class is not
         * {@link Registration registered}. Guaranteed to always return the same context reference
         * for a given language class.
         * <p>
         * See {@link ContextReference} for a usage example.
         *
         * @since 21.3
         */
        public static <T extends TruffleLanguage<C>, C> ContextReference<C> create(Class<T> languageClass) {
            Objects.requireNonNull(languageClass);
            return LanguageAccessor.ENGINE.createContextReference(languageClass);
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
     * If multiple sharable languages are used at the same time, nodes of sharable languages may
     * adopt nodes of a non-sharable languages indirectly, e.g. through Truffle interoperability.
     * This would complicate language implementations as they would always need to support sharing
     * for their nodes. To avoid this problem, Truffle uses sharing layers. Sharing layers ensure
     * that every node created by a language instance can only be observed by a single language
     * instance at a time. Sharing layers operate on the principle that all languages share the code
     * with the same language instance or no language shares. As a convenient consequence any call
     * to {@link LanguageReference#get(Node)} with an adopted {@link Node} can fold to a constant
     * during partial evaluation.
     * <p>
     * For any sharing to take place a context must be created with sharing enabled. By default
     * sharing is disabled for polyglot contexts. Sharing can be enabled by specifying an
     * {@link Builder#engine(Engine) explicit engine} or using an option. Before any language is
     * used for sharing {@link TruffleLanguage#initializeMultipleContexts()} is invoked. It is
     * guaranteed that sharing between multiple contexts is
     * {@link TruffleLanguage#initializeMultipleContexts() initialized} before any language context
     * is {@link TruffleLanguage#createContext(Env) created}.
     *
     * @see Registration#contextPolicy() To configure context policy for a language.
     * @see TruffleLanguage#parse(ParsingRequest)
     * @see TruffleLanguage#initializeMultipleContexts()
     * @since 19.0
     */
    public enum ContextPolicy {

        /*
         * The declared order of the enum constants is used. Please do not change.
         */

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

    /**
     * Mode of exit operation.
     *
     * @since 22.0
     * @see #exitContext(Object, ExitMode, int)
     */
    public enum ExitMode {
        /**
         * Natural exit that occurs during normal context close.
         *
         * @since 22.0
         *
         */
        NATURAL,
        /**
         * Hard exit triggered by {@link TruffleContext#closeExited(Node, int)}.
         *
         * @since 22.0
         */
        HARD
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

    // @formatter:off // @replace regex='.*' replacement=''
    abstract
    class MyLanguage extends TruffleLanguage<Context> {
        @Override
        protected Context createContext(Env env) {
            String[] args = env.getApplicationArguments();
            return new Context(args);
        }
    }

    abstract
    // @start region="TruffleLanguageSnippets.PostInitLanguage#createContext"
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
    // @end region="TruffleLanguageSnippets.PostInitLanguage#createContext"

    abstract static
    // @start region="TruffleLanguageSnippets.CompatibleLanguage#areOptionsCompatible"
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
    // @end region="TruffleLanguageSnippets.CompatibleLanguage#areOptionsCompatible"

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

        // @start region="TruffleLanguageSnippets.PreInitializedLanguage#patchContext"
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
        // @end region="TruffleLanguageSnippets.PreInitializedLanguage#patchContext"
    }

    // @start region="TruffleLanguageSnippets#parseWithParams"
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
    // @end region="TruffleLanguageSnippets#parseWithParams"


    abstract
    // @start region="TruffleLanguageSnippets.MultiThreadedLanguage#initializeThread"
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
    // @end region="TruffleLanguageSnippets.MultiThreadedLanguage#initializeThread"

    abstract
    // @start region="TruffleLanguageSnippets.AsyncThreadLanguage#finalizeContext"
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
            Thread t = context.env.newTruffleThreadBuilder(new Runnable() {
                @Override
                public void run() {
                    // asynchronous task
                }
            }).build();
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
    // @end region="TruffleLanguageSnippets.AsyncThreadLanguage#finalizeContext"

    abstract static
    // @start region="TruffleLanguageSnippets.SystemThreadLanguage"
    class SystemThreadLanguage extends
            TruffleLanguage<SystemThreadLanguage.Context> {

        static class Context {
            private final BlockingQueue<Runnable> tasks;
            private final Env env;
            private volatile Thread systemThread;
            private volatile boolean cancelled;

            Context(Env env) {
                this.tasks = new LinkedBlockingQueue<>();
                this.env = env;
            }
        }

        @Override
        protected Context createContext(Env env) {
            return new Context(env);
        }

        @Override
        protected void initializeContext(Context context) {
            // Create and start a Thread for the asynchronous internal task.
            // Remember the Thread to stop and join it in the disposeContext.
            context.systemThread = context.env.createSystemThread(() -> {
                while (!context.cancelled) {
                    try {
                        Runnable task = context.tasks.
                                poll(Integer.MAX_VALUE, SECONDS);
                        if (task != null) {
                            task.run();
                        }
                    } catch (InterruptedException ie) {
                        // pass to cancelled check
                    }
                }
            });
            context.systemThread.start();
        }

        @Override
        protected void disposeContext(Context context) {
            // Stop and join system thread.
            context.cancelled = true;
            Thread threadToJoin = context.systemThread;
            if (threadToJoin != null) {
                threadToJoin.interrupt();
                boolean interrupted = false;
                boolean terminated = false;
                while (!terminated) {
                    try {
                        threadToJoin.join();
                        terminated = true;
                    } catch (InterruptedException ie) {
                        interrupted = true;
                    }
                }
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    // @end region="TruffleLanguageSnippets.SystemThreadLanguage"
}
