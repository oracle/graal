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
package org.graalvm.polyglot;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.ModuleLayer.Controller;
import java.lang.invoke.MethodHandles;
import java.lang.module.Configuration;
import java.lang.module.FindException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Provides;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;

import org.graalvm.home.HomeFinder;
import org.graalvm.home.Version;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.HostAccess.MutableTargetMapping;
import org.graalvm.polyglot.HostAccess.TargetMappingPrecedence;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractContextDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractEngineDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractExceptionDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractInstrumentDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractLanguageDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractSourceDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractSourceSectionDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractStackFrameImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractValueDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.IOAccessor;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.ManagementAccess;
import org.graalvm.polyglot.impl.UnnamedToModuleBridge;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.io.MessageTransport;
import org.graalvm.polyglot.io.ProcessHandler;
import org.graalvm.polyglot.proxy.Proxy;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyDate;
import org.graalvm.polyglot.proxy.ProxyDuration;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyHashMap;
import org.graalvm.polyglot.proxy.ProxyInstant;
import org.graalvm.polyglot.proxy.ProxyInstantiable;
import org.graalvm.polyglot.proxy.ProxyIterable;
import org.graalvm.polyglot.proxy.ProxyIterator;
import org.graalvm.polyglot.proxy.ProxyNativeObject;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.graalvm.polyglot.proxy.ProxyTime;
import org.graalvm.polyglot.proxy.ProxyTimeZone;

/**
 * An execution engine for Graal {@linkplain Language guest languages} that allows to inspect the
 * the installed {@link #getLanguages() guest languages}, {@link #getInstruments() instruments} and
 * their available options.
 * <p>
 * By default every context creates its own {@link Engine engine} instance implicitly when
 * {@link Context.Builder#build() instantiated}. Multiple contexts can use an
 * {@link Context.Builder#engine(Engine) explicit engine} when using a context builder. If contexts
 * share the same engine instance then they share instruments and their configuration.
 * <p>
 * It can be useful to {@link Engine#create() create} an engine instance without a context to only
 * access meta-data for installed languages, instruments and their available options.
 *
 * @since 19.0
 */
public final class Engine implements AutoCloseable {

    private static volatile Throwable initializationException;
    private static volatile boolean shutdownHookInitialized;
    private static final Map<Engine, Void> ENGINES = Collections.synchronizedMap(new WeakHashMap<>());

    final AbstractEngineDispatch dispatch;
    final Object receiver;
    final Engine currentAPI;

    @SuppressWarnings("unchecked")
    <T> Engine(AbstractEngineDispatch dispatch, T receiver) {
        this.dispatch = dispatch;
        this.receiver = receiver;
        this.currentAPI = new Engine(this);
        if (dispatch != null) {
            dispatch.setAPI(receiver, this);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Engine(Engine engine) {
        this.dispatch = engine.dispatch;
        this.receiver = engine.receiver;
        this.currentAPI = null;
    }

    private static final class ImplHolder {
        private static AbstractPolyglotImpl IMPL = initEngineImpl();

        /**
         * Performs context pre-initialization.
         *
         * NOTE: this method is called reflectively by downstream projects
         * (com.oracle.svm.truffle.TruffleBaseFeature).
         */
        @SuppressWarnings("unused")
        private static void preInitializeEngine() {
            IMPL.preInitializeEngine();
        }

        /**
         * Clears the pre-initialized engine.
         *
         * NOTE: this method is called reflectively by downstream projects
         * (com.oracle.svm.truffle.TruffleBaseFeature).
         */
        @SuppressWarnings("unused")
        private static void resetPreInitializedEngine() {
            IMPL.resetPreInitializedEngine();
        }

        /**
         * Support for Context pre-initialization debugging in HotSpot.
         */
        private static void debugContextPreInitialization() {
            if (!ImageInfo.inImageCode() && System.getProperty("polyglot.image-build-time.PreinitializeContexts") != null) {
                IMPL.preInitializeEngine();
            }
        }

        static {
            debugContextPreInitialization();
        }
    }

    /**
     * Gets a map of all installed languages with the language id as key and the language object as
     * value. The returned map is unmodifiable and might be used from multiple threads.
     *
     * @since 19.0
     */
    @SuppressWarnings("unchecked")
    public Map<String, Language> getLanguages() {
        return (Map<String, Language>) (Map<String, ?>) dispatch.getLanguages(receiver);
    }

    /**
     * Gets all installed instruments of this engine. An instrument alters and/or monitors the
     * execution of guest language source code. Common examples for instruments are debuggers,
     * profilers, or monitoring tools. Instruments are enabled via {@link Instrument#getOptions()
     * options} passed to the {@link Builder#option(String, String) engine} when the engine or
     * context is constructed.
     *
     * @since 19.0
     */
    @SuppressWarnings("unchecked")
    public Map<String, Instrument> getInstruments() {
        return (Map<String, Instrument>) (Map<String, ?>) dispatch.getInstruments(receiver);
    }

    /**
     * Returns all options available for the engine. The engine offers options with the following
     * {@link OptionDescriptor#getKey() groups}:
     * <ul>
     * <li><b>engine</b>: options to configure the behavior of this engine.
     * <li><b>compiler</b>: options to configure the behavior of the compiler.
     * </ul>
     * The language and instrument specific options need to be retrieved using
     * {@link Instrument#getOptions()} or {@link Language#getOptions()}.
     *
     * @see Language#getOptions() To get a list of options for a language.
     * @see Instrument#getOptions() To get a list of options for an instrument.
     * @see Builder#option(String, String) To set an option for an engine, language, or instrument.
     * @see Context.Builder#option(String, String) To set an option for a context.
     *
     * @since 19.0
     */
    public OptionDescriptors getOptions() {
        return dispatch.getOptions(receiver);
    }

    /**
     * Gets the version string of the engine in an unspecified format.
     *
     * @since 19.0
     */
    @SuppressWarnings("static-method")
    public String getVersion() {
        return dispatch.getVersion(receiver);
    }

    /**
     * Closes this engine and frees up allocated native resources. If there are still open context
     * instances that were created using this engine and they are currently not being executed then
     * they will be closed automatically. If an attempt to close an engine was successful then
     * consecutive calls to close have no effect. If a context is cancelled then the currently
     * executing thread will throw a {@link PolyglotException}. The exception indicates that it was
     * {@link PolyglotException#isCancelled() cancelled}.
     *
     * @param cancelIfExecuting if <code>true</code> then currently executing contexts will be
     *            cancelled, else an {@link IllegalStateException} is thrown.
     * @since 19.0
     */
    public void close(boolean cancelIfExecuting) {
        if (currentAPI == null) {
            throw new IllegalStateException("Engine instances that were indirectly received using Context.getCurrent() cannot be closed.");
        }
        dispatch.close(receiver, this, cancelIfExecuting);
    }

    /**
     * Closes this engine and frees up allocated native resources. If there are still open context
     * instances that were created using this engine and they are currently not being executed then
     * they will be closed automatically. If an attempt to close the engine was successful then
     * consecutive calls to close have no effect.
     *
     * @throws IllegalStateException if there currently executing open context instances.
     * @see #close(boolean)
     * @see Engine#close()
     * @since 19.0
     */
    @Override
    public void close() {
        close(false);
    }

    /**
     * Gets a human-readable name of the polyglot implementation (for example, "Default Truffle
     * Engine" or "Graal Truffle Engine"). The returned value may change without notice. The value
     * is never <code>null</code>.
     *
     * @since 19.0
     */
    public String getImplementationName() {
        return dispatch.getImplementationName(receiver);
    }

    /**
     * Creates a new engine instance with default configuration. This method is a shortcut for
     * {@link #newBuilder(String...) newBuilder().build()}.
     *
     * @see Context#create(String...) to create a new execution context.
     * @since 19.0
     */
    public static Engine create() {
        return newBuilder().build();
    }

    /**
     * Creates a new engine instance with default configuration with a set of permitted languages.
     * This method is a shortcut for {@link #newBuilder(String...)
     * newBuilder(permittedLanuages).build()}.
     *
     * @see Context#create(String...) to create a new execution context.
     * @since 21.3
     */
    public static Engine create(String... permittedLanguages) {
        return newBuilder(permittedLanguages).build();
    }

    /**
     * Creates a new engine builder that allows to configure an engine instance. This method is
     * equivalent to calling {@link #newBuilder(String...)} with an empty set of permitted
     * languages.
     *
     * @since 21.3
     */
    public static Builder newBuilder() {
        return EMPTY.new Builder(new String[0]);
    }

    /**
     * Creates a new engine builder that allows to configure an engine instance.
     *
     * @param permittedLanguages names of languages permitted in the engine. If no languages are
     *            provided, then all installed languages will be permitted. All contexts created
     *            with this engine will inherit the set of permitted languages.
     * @return a builder that can create a context
     * @since 21.3
     */
    public static Builder newBuilder(String... permittedLanguages) {
        Objects.requireNonNull(permittedLanguages);
        return EMPTY.new Builder(permittedLanguages);
    }

    /**
     * Finds the GraalVM home folder.
     *
     * This is equivalent to {@link HomeFinder#getHomeFolder()} which should be preferred.
     *
     * @return the path to a folder containing the GraalVM or {@code null} if it cannot be found
     * @since 19.0
     */
    public static Path findHome() {
        return HomeFinder.getInstance().getHomeFolder();
    }

    /**
     * Returns the sources previously cached by this engine. Only sources may be returned that allow
     * {@link Source.Builder#cached(boolean) caching} (default on). The source cache of the engine
     * is using weak references to refer to the source objects. Calling this method will result in a
     * strong reference to all cached sources of this engine until the returned set is no longer
     * referenced. This method is useful to find out which sources very already evaluated by this
     * engine. This method only returns sources that were evaluated using
     * {@link Context#eval(Source)}. Sources evaluated by the guest application will not be
     * returned. The return set is never <code>null</code> and not modifiable.
     *
     * @since 20.3
     */
    @SuppressWarnings("unchecked")
    public Set<Source> getCachedSources() {
        return (Set<Source>) (Set<?>) dispatch.getCachedSources(receiver);
    }

    /**
     * Unpacks the language or instrument internal resources specified by the {@code components}
     * into the {@code targetFolder} directory.
     * <p>
     * During execution, the internal resource cache location can be overridden by the following
     * system properties:
     * <ul>
     * <li>{@code polyglot.engine.resourcePath}: Sets the cache location to the given path. The
     * expected folder structure is the structure generated by this method.</li>
     * <li>{@code polyglot.engine.resourcePath.<component>}: Retains the default cache folder but
     * overrides caches for the specified component with the given path. The expected folder
     * structure is {@code targetFolder/<component>}.</li>
     * <li>{@code polyglot.engine.resourcePath.<component>.<resource-id>} : Retains the default
     * cache folder but overrides caches for the specified resource within the component with the
     * given path. The expected folder structure is
     * {@code targetFolder/<component>/<resource-id>}.</li>
     * </ul>
     *
     * @param targetFolder the folder to unpack resources into
     * @param components names of languages or instruments whose resources should be unpacked. If no
     *            languages or instruments are provided, then all installed languages and
     *            instruments are used
     * @return {@code true} if at least one of the {@code components} has associated resources that
     *         were unpacked into {@code targetFolder}
     * @throws IllegalArgumentException if the {@code components} list contains an id of a language
     *             or instrument that is not installed
     * @throws IOException in case of an IO error
     * @since 23.1
     */
    public static boolean copyResources(Path targetFolder, String... components) throws IOException {
        return getImpl().copyResources(targetFolder, components);
    }

    static AbstractPolyglotImpl getImpl() {
        try {
            return ImplHolder.IMPL;
        } catch (NoClassDefFoundError e) {
            // Workaround for https://bugs.openjdk.java.net/browse/JDK-8048190
            Throwable cause = initializationException;
            if (cause != null && e.getCause() == null) {
                e.initCause(cause);
            }
            throw e;
        } catch (Throwable e) {
            initializationException = e;
            throw e;
        }
    }

    /*
     * Used internally to load language specific classes.
     */
    static Class<?> loadLanguageClass(String className) {
        return getImpl().loadLanguageClass(className);
    }

    /*
     * Used internally to find all active engines. Do not hold on to the returned collection
     * permanently as this may cause memory leaks.
     */
    @SuppressWarnings("unchecked")
    static Collection<Engine> findActiveEngines() {
        synchronized (ENGINES) {
            return new ArrayList<>(ENGINES.keySet());
        }
    }

    static void validateSandboxPolicy(SandboxPolicy previous, SandboxPolicy policy) {
        Objects.requireNonNull(policy, "The set policy must not be null.");
        if (previous != null && previous.isStricterThan(policy)) {
            throw new IllegalArgumentException(
                            String.format("The sandbox policy %s was set for this builder and the newly set policy %s is less restrictive than the previous policy. " +
                                            "Only equal or more strict policies are allowed. ",
                                            previous, policy));
        }
    }

    static boolean isSystemStream(InputStream in) {
        return System.in == in;
    }

    static boolean isSystemStream(OutputStream out) {
        return System.out == out || System.err == out;
    }

    private static final Engine EMPTY = new Engine(null, null);

    /**
     *
     * @since 19.0
     */
    @SuppressWarnings("hiding")
    public final class Builder {

        /**
         * The value of the system property for enabling experimental options must be read before
         * the first engine is created and cached so that languages cannot affect its value. It
         * cannot be a final value because an Engine is initialized at image build time.
         */
        private static final AtomicReference<Boolean> allowExperimentalOptionSystemPropertyValue = new AtomicReference<>();

        private OutputStream out = System.out;
        private OutputStream err = System.err;
        private InputStream in = null;
        private Map<String, String> options = new HashMap<>();
        private boolean allowExperimentalOptions = false;
        private boolean useSystemProperties = true;
        private boolean boundEngine;
        private MessageTransport messageTransport;
        private Object customLogHandler;
        private String[] permittedLanguages;
        private SandboxPolicy sandboxPolicy;

        Builder(String[] permittedLanguages) {
            sandboxPolicy = SandboxPolicy.TRUSTED;
            Objects.requireNonNull(permittedLanguages);
            for (String language : permittedLanguages) {
                Objects.requireNonNull(language);
            }
            this.permittedLanguages = permittedLanguages;
        }

        Builder setBoundEngine(boolean boundEngine) {
            this.boundEngine = boundEngine;
            return this;
        }

        /**
         * Sets the standard output stream to be used for this engine. Every context that uses this
         * engine will inherit the configured output stream if it is not specified in the context.
         * If not set then the system output stream will be used.
         *
         * @since 19.0
         */
        public Builder out(OutputStream out) {
            Objects.requireNonNull(out);
            this.out = out;
            return this;
        }

        /**
         * Sets the standard error stream to be used for this engine. Every context that uses this
         * engine will inherit the configured error stream if it is not specified in the context. If
         * not set then the system error stream will be used.
         *
         * @since 19.0
         */
        public Builder err(OutputStream err) {
            Objects.requireNonNull(err);
            this.err = err;
            return this;
        }

        /**
         * Sets the standard input stream to be used for this engine. Every context that uses this
         * engine will inherit the configured input stream if it is not specified in the context. If
         * not set then the system input stream will be used.
         *
         * @since 19.0
         */
        public Builder in(InputStream in) {
            Objects.requireNonNull(in);
            this.in = in;
            return this;
        }

        /**
         * Allow experimental options to be used for instruments and engine options. Do not use
         * experimental options in production environments. If set to {@code false} (the default),
         * then passing an experimental option results in an {@link IllegalArgumentException} when
         * the context is built.
         *
         * @since 19.0
         */
        public Builder allowExperimentalOptions(boolean enabled) {
            this.allowExperimentalOptions = enabled;
            return this;
        }

        /**
         * Specifies whether the engine should use {@link System#getProperty(String) system
         * properties} if no explicit option is {@link #option(String, String) set}. The default
         * value is <code>true</code> indicating that the system properties should be used. System
         * properties are looked up with the prefix <i>"polyglot"</i> in order to disambiguate
         * existing system properties. For example, for the option with the key
         * <code>"js.ECMACompatiblity"</code>, the system property
         * <code>"polyglot.js.ECMACompatiblity"</code> is read. Invalid options specified using
         * system properties will cause the {@link #build() build} method to fail using an
         * {@link IllegalArgumentException}. System properties are read once when the engine is
         * built and are never updated after that.
         *
         * @param enabled if <code>true</code> system properties will be used as options.
         * @see #option(String, String) To specify option values directly.
         * @see #build() To build the engine instance.
         * @since 19.0
         */
        public Builder useSystemProperties(boolean enabled) {
            useSystemProperties = enabled;
            return this;
        }

        /**
         * Sets an option for an {@link Engine#getOptions() engine}, {@link Language#getOptions()
         * language} or {@link Instrument#getOptions() instrument}.
         * <p>
         * If one of the set option keys or values is invalid then an
         * {@link IllegalArgumentException} is thrown when the engine is {@link #build() built}. The
         * given key and value must not be <code>null</code>.
         *
         * @see Engine#getOptions() To list all available options for engines.
         * @see Language#getOptions() To list all available options for a {@link Language language}.
         * @see Instrument#getOptions() To list all available options for an {@link Instrument
         *      instrument}.
         * @since 19.0
         */
        public Builder option(String key, String value) {
            Objects.requireNonNull(key, "Key must not be null.");
            Objects.requireNonNull(value, "Value must not be null.");
            options.put(key, value);
            return this;
        }

        /**
         * Sets a code sandbox policy to an engine. By default, the engine's sandbox policy is
         * {@link SandboxPolicy#TRUSTED}, there are no restrictions to the engine configuration.
         *
         * @see SandboxPolicy
         * @since 23.0
         */
        public Builder sandbox(SandboxPolicy policy) {
            validateSandboxPolicy(this.sandboxPolicy, policy);
            this.sandboxPolicy = policy;
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
                Objects.requireNonNull(options.get(key), "All option values must be non-null.");
            }
            this.options.putAll(options);
            return this;
        }

        /**
         * Take over transport of message communication with a server peer. Provide an
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
         * using the {@link #options(java.util.Map) Engine's options}. The level option key has the
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
         *
         * @param logHandler the {@link Handler} to use for logging in engine's {@link Context}s.
         *            The passed {@code logHandler} is closed when the engine is
         *            {@link Engine#close() closed}.
         * @return the {@link Builder}
         * @since 19.0
         */
        public Builder logHandler(final Handler logHandler) {
            Objects.requireNonNull(logHandler, "Handler must be non null.");
            this.customLogHandler = logHandler;
            return this;
        }

        /**
         * Installs a new logging {@link Handler} using given {@link OutputStream}. The logger's
         * {@link Level} configuration is done using the {@link #options(java.util.Map) Engine's
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
         *
         * @param logOut the {@link OutputStream} to use for logging in engine's {@link Context}s.
         *            The passed {@code logOut} stream is closed when the engine is
         *            {@link Engine#close() closed}.
         * @return the {@link Builder}
         * @since 19.0
         */
        public Builder logHandler(final OutputStream logOut) {
            Objects.requireNonNull(logOut, "LogOut must be non null.");
            this.customLogHandler = logOut;
            return this;
        }

        /**
         * Creates a new engine instance from the configuration provided in the builder. The same
         * engine builder can be used to create multiple engine instances.
         *
         * @since 19.0
         */
        public Engine build() {
            AbstractPolyglotImpl polyglot = getImpl();
            if (polyglot == null) {
                throw new IllegalStateException("The Polyglot API implementation failed to load.");
            }
            validateSandbox();
            InputStream useIn = in;
            if (useIn == null) {
                useIn = switch (sandboxPolicy) {
                    case TRUSTED -> System.in;
                    case CONSTRAINED, ISOLATED, UNTRUSTED -> InputStream.nullInputStream();
                    default -> throw new IllegalArgumentException(String.valueOf(sandboxPolicy));
                };
            }
            Object logHandler = customLogHandler != null ? polyglot.newLogHandler(customLogHandler) : null;
            Map<String, String> useOptions = useSystemProperties ? readOptionsFromSystemProperties(options) : options;
            boolean useAllowExperimentalOptions = allowExperimentalOptions || readAllowExperimentalOptionsFromSystemProperties();
            Engine engine = (Engine) polyglot.buildEngine(permittedLanguages, sandboxPolicy, out, err, useIn, useOptions, useAllowExperimentalOptions,
                            boundEngine, messageTransport, logHandler, polyglot.createHostLanguage(polyglot.createHostAccess()), false, true, null);
            return engine;
        }

        static Map<String, String> readOptionsFromSystemProperties(Map<String, String> options) {
            Properties properties = System.getProperties();
            Map<String, String> newOptions = null;
            String systemPropertyPrefix = "polyglot.";
            synchronized (properties) {
                for (Object systemKey : properties.keySet()) {
                    String key = (String) systemKey;
                    if ("polyglot.engine.AllowExperimentalOptions".equals(key) || key.equals("polyglot.engine.resourcePath") || key.startsWith("polyglot.engine.resourcePath.")) {
                        continue;
                    }
                    if (key.startsWith(systemPropertyPrefix)) {
                        final String optionKey = key.substring(systemPropertyPrefix.length());
                        // Image build time options are not set in runtime options
                        if (!optionKey.startsWith("image-build-time")) {
                            // system properties cannot override existing options
                            if (!options.containsKey(optionKey)) {
                                if (newOptions == null) {
                                    newOptions = new HashMap<>(options);
                                }
                                newOptions.put(optionKey, System.getProperty(key));
                            }
                        }
                    }
                }
            }
            if (newOptions == null) {
                return options;
            } else {
                return newOptions;
            }
        }

        private static boolean readAllowExperimentalOptionsFromSystemProperties() {
            Boolean res = allowExperimentalOptionSystemPropertyValue.get();
            if (res == null) {
                res = Boolean.getBoolean("polyglot.engine.AllowExperimentalOptions");
                Boolean old = allowExperimentalOptionSystemPropertyValue.compareAndExchange(null, res);
                if (old != null) {
                    res = old;
                }
            }
            return res;
        }

        /**
         * Validates configured sandbox policy constrains.
         *
         * @throws IllegalArgumentException if the engine configuration is not compatible with the
         *             requested sandbox policy.
         */
        private void validateSandbox() {
            if (sandboxPolicy == SandboxPolicy.TRUSTED) {
                return;
            }
            if (permittedLanguages.length == 0) {
                throw throwSandboxException(sandboxPolicy, "Builder does not have a list of permitted languages.",
                                String.format("create a Builder with a list of permitted languages, for example, %s.newBuilder(\"js\")", boundEngine ? "Context" : "Engine"));
            }
            if (isSystemStream(in)) {
                throw throwSandboxException(sandboxPolicy, "Builder uses the standard input stream, but the input must be redirected.",
                                "do not set Builder.in(InputStream) to use InputStream.nullInputStream() or redirect it to other stream than System.in");
            }
            if (isSystemStream(out)) {
                throw throwSandboxException(sandboxPolicy, "Builder uses the standard output stream, but the output must be redirected.",
                                "set Builder.out(OutputStream)");
            }
            if (isSystemStream(err)) {
                throw throwSandboxException(sandboxPolicy, "Builder uses the standard error stream, but the error output must be redirected.",
                                "set Builder.err(OutputStream)");
            }
            if (messageTransport != null) {
                throw throwSandboxException(sandboxPolicy, "Builder.serverTransport(MessageTransport) is set, but must not be set.",
                                "do not set Builder.serverTransport(MessageTransport)");
            }
        }

        static IllegalArgumentException throwSandboxException(SandboxPolicy sandboxPolicy, String reason, String fix) {
            Objects.requireNonNull(sandboxPolicy);
            Objects.requireNonNull(reason);
            Objects.requireNonNull(fix);
            String spawnIsolateHelp;
            if (sandboxPolicy.isStricterOrEqual(SandboxPolicy.ISOLATED)) {
                spawnIsolateHelp = " If you switch to a less strict sandbox policy you can still spawn an isolate with an isolated heap using Builder.option(\"engine.SpawnIsolate\",\"true\").";
            } else {
                spawnIsolateHelp = "";
            }
            String message = String.format("The validation for the given sandbox policy %s failed. %s " +
                            "In order to resolve this %s or switch to a less strict sandbox policy using Builder.sandbox(SandboxPolicy).%s",
                            sandboxPolicy, reason, fix, spawnIsolateHelp);
            throw new IllegalArgumentException(message);
        }
    }

    static class APIAccessImpl extends AbstractPolyglotImpl.APIAccess {

        private static final APIAccessImpl INSTANCE = new APIAccessImpl();

        private static final ProxyArray EMPTY = new ProxyArray() {

            public void set(long index, Value value) {
                throw new ArrayIndexOutOfBoundsException();
            }

            public long getSize() {
                return 0;
            }

            public Object get(long index) {
                throw new ArrayIndexOutOfBoundsException();
            }
        };

        APIAccessImpl() {
        }

        @Override
        public Object newContext(AbstractContextDispatch dispatch, Object receiver, Object engine) {
            return new Context(dispatch, receiver, (Engine) engine);
        }

        @Override
        public Object newEngine(AbstractEngineDispatch dispatch, Object receiver, boolean registerInActiveEngines) {
            Engine engine = new Engine(dispatch, receiver);
            if (registerInActiveEngines) {
                if (!shutdownHookInitialized) {
                    synchronized (ENGINES) {
                        if (!shutdownHookInitialized) {
                            shutdownHookInitialized = true;
                            try {
                                Runtime.getRuntime().addShutdownHook(new Thread(new EngineShutDownHook()));
                            } catch (IllegalStateException e) {
                                // shutdown already in progress
                                // catching the exception is the only way to detect this.
                            }
                        }
                    }
                }
                ENGINES.put(engine, null);
            }
            return engine;
        }

        @Override
        public void engineClosed(Object engine) {
            ENGINES.remove(engine);
        }

        @Override
        public Object newLanguage(AbstractLanguageDispatch dispatch, Object receiver) {
            return new Language(dispatch, receiver);
        }

        @Override
        public Object newInstrument(AbstractInstrumentDispatch dispatch, Object receiver) {
            return new Instrument(dispatch, receiver);
        }

        @Override
        public Object getInstrumentReceiver(Object instrument) {
            return ((Instrument) instrument).receiver;
        }

        @Override
        public Object getValueContext(Object value) {
            return ((Value) value).context;
        }

        @Override
        public Object newValue(AbstractValueDispatch dispatch, Object context, Object receiver) {
            return new Value(dispatch, context, receiver);
        }

        @Override
        public Source newSource(AbstractSourceDispatch dispatch, Object receiver) {
            return new Source(dispatch, receiver);
        }

        @Override
        public Object getLanguageReceiver(Object language) {
            return ((Language) language).receiver;
        }

        @Override
        public Object newSourceSection(Object source, AbstractSourceSectionDispatch dispatch, Object receiver) {
            return new SourceSection((Source) source, dispatch, receiver);
        }

        @Override
        public Object getSourceSectionSource(Object sourceSection) {
            return ((SourceSection) sourceSection).getSource();
        }

        @Override
        public AbstractValueDispatch getValueDispatch(Object value) {
            return ((Value) value).dispatch;
        }

        @Override
        public AbstractInstrumentDispatch getInstrumentDispatch(Object value) {
            return ((Instrument) value).dispatch;
        }

        @Override
        public AbstractContextDispatch getContextDispatch(Object context) {
            return ((Context) context).dispatch;
        }

        @Override
        public AbstractEngineDispatch getEngineDispatch(Object engine) {
            return ((Engine) engine).dispatch;
        }

        @Override
        public AbstractSourceDispatch getSourceDispatch(Object source) {
            return ((Source) source).dispatch;
        }

        @Override
        public AbstractSourceSectionDispatch getSourceSectionDispatch(Object sourceSection) {
            return ((SourceSection) sourceSection).dispatch;
        }

        @Override
        public Object newResourceLimitsEvent(Object context) {
            return new ResourceLimitEvent((Context) context);
        }

        @Override
        public AbstractLanguageDispatch getLanguageDispatch(Object value) {
            return ((Language) value).dispatch;
        }

        @Override
        public Object getResourceLimitsReceiver(Object value) {
            return ((ResourceLimits) value).receiver;
        }

        @Override
        public Object getSourceReceiver(Object source) {
            return ((Source) source).receiver;
        }

        @Override
        public Object getSourceSectionReceiver(Object sourceSection) {
            return ((SourceSection) sourceSection).receiver;
        }

        @Override
        public RuntimeException newLanguageException(String message, AbstractExceptionDispatch dispatch, Object receiver) {
            return new PolyglotException(message, dispatch, receiver);
        }

        @Override
        public AbstractStackFrameImpl getStackFrameDispatch(Object value) {
            return ((StackFrame) value).impl;
        }

        @Override
        public Object getValueReceiver(Object value) {
            return ((Value) value).receiver;
        }

        @Override
        public Object getContextReceiver(Object context) {
            return ((Context) context).receiver;
        }

        @Override
        public Object getEngineReceiver(Object engine) {
            return ((Engine) engine).receiver;
        }

        @Override
        public Object getPolyglotExceptionReceiver(RuntimeException polyglot) {
            return ((PolyglotException) polyglot).impl;
        }

        @Override
        public StackFrame newPolyglotStackTraceElement(AbstractStackFrameImpl dispatch, RuntimeException receiver) {
            return ((PolyglotException) receiver).new StackFrame(dispatch);
        }

        @Override
        public boolean allowsAccess(Object access, AnnotatedElement element) {
            return ((HostAccess) access).allowsAccess(element);
        }

        @Override
        public boolean allowsImplementation(Object access, Class<?> type) {
            return ((HostAccess) access).allowsImplementation(type);
        }

        @Override
        public boolean isMethodScopingEnabled(Object access) {
            return ((HostAccess) access).isMethodScopingEnabled();
        }

        @Override
        public boolean isMethodScoped(Object access, Executable e) {
            return ((HostAccess) access).isMethodScoped(e);
        }

        @Override
        public MutableTargetMapping[] getMutableTargetMappings(Object access) {
            return ((HostAccess) access).getMutableTargetMappings();
        }

        @Override
        public List<Object> getTargetMappings(Object hostAccess) {
            return ((HostAccess) hostAccess).getTargetMappings();
        }

        @Override
        public boolean isArrayAccessible(Object access) {
            return ((HostAccess) access).allowArrayAccess;
        }

        @Override
        public boolean isListAccessible(Object access) {
            return ((HostAccess) access).allowListAccess;
        }

        @Override
        public boolean isBufferAccessible(Object access) {
            return ((HostAccess) access).allowBufferAccess;
        }

        @Override
        public boolean isIterableAccessible(Object access) {
            return ((HostAccess) access).allowIterableAccess;
        }

        @Override
        public boolean isIteratorAccessible(Object access) {
            return ((HostAccess) access).allowIteratorAccess;
        }

        @Override
        public boolean isMapAccessible(Object access) {
            return ((HostAccess) access).allowMapAccess;
        }

        @Override
        public boolean isBigIntegerAccessibleAsNumber(Object access) {
            return ((HostAccess) access).allowBigIntegerNumberAccess;
        }

        @Override
        public boolean allowsPublicAccess(Object access) {
            return ((HostAccess) access).allowPublic;
        }

        @Override
        public boolean allowsAccessInheritance(Object access) {
            return ((HostAccess) access).allowAccessInheritance;
        }

        @Override
        public Object getHostAccessImpl(Object access) {
            return ((HostAccess) access).impl;
        }

        @Override
        public MethodHandles.Lookup getMethodLookup(Object access) {
            return ((HostAccess) access).methodLookup;
        }

        @Override
        public void setHostAccessImpl(Object access, Object impl) {
            ((HostAccess) access).impl = impl;
        }

        @Override
        public Set<String> getEvalAccess(Object access, String language) {
            return ((PolyglotAccess) access).getEvalAccess(language);
        }

        @Override
        public Map<String, Set<String>> getEvalAccess(Object access) {
            return ((PolyglotAccess) access).getEvalAccess();
        }

        @Override
        public Set<String> getBindingsAccess(Object access) {
            return ((PolyglotAccess) access).getBindingsAccess();
        }

        @Override
        public String validatePolyglotAccess(Object access, Set<String> languages) {
            return ((PolyglotAccess) access).validate(languages);
        }

        @Override
        public Map<String, String> readOptionsFromSystemProperties() {
            return Builder.readOptionsFromSystemProperties(Collections.emptyMap());
        }

        @Override
        public boolean isByteSequence(Object origin) {
            return origin instanceof ByteSequence;
        }

        @Override
        public ByteSequence asByteSequence(Object origin) {
            return (ByteSequence) origin;
        }

        @Override
        public boolean isInstrument(Object instrument) {
            return instrument instanceof Instrument;
        }

        @Override
        public boolean isLanguage(Object language) {
            return language instanceof Language;
        }

        @Override
        public boolean isEngine(Object engine) {
            return engine instanceof Engine;
        }

        @Override
        public boolean isContext(Object context) {
            return context instanceof Context;
        }

        @Override
        public boolean isPolyglotException(Object exception) {
            return exception instanceof PolyglotException;
        }

        @Override
        public boolean isValue(Object value) {
            return value instanceof Value;
        }

        @Override
        public boolean isSource(Object value) {
            return value instanceof Source;
        }

        @Override
        public boolean isSourceSection(Object value) {
            return value instanceof SourceSection;
        }

        @Override
        public AbstractStackFrameImpl getStackFrameReceiver(Object value) {
            return ((StackFrame) value).impl;
        }

        @Override
        public boolean isProxyArray(Object proxy) {
            return proxy instanceof ProxyArray;
        }

        @Override
        public boolean isProxyDate(Object proxy) {
            return proxy instanceof ProxyDate;
        }

        @Override
        public boolean isProxyDuration(Object proxy) {
            return proxy instanceof ProxyDuration;
        }

        @Override
        public boolean isProxyExecutable(Object proxy) {
            return proxy instanceof ProxyExecutable;
        }

        @Override
        public boolean isProxyHashMap(Object proxy) {
            return proxy instanceof ProxyHashMap;
        }

        @Override
        public boolean isProxyInstant(Object proxy) {
            return proxy instanceof ProxyInstant;
        }

        @Override
        public boolean isProxyInstantiable(Object proxy) {
            return proxy instanceof ProxyInstantiable;
        }

        @Override
        public boolean isProxyIterable(Object proxy) {
            return proxy instanceof ProxyIterable;
        }

        @Override
        public boolean isProxyIterator(Object proxy) {
            return proxy instanceof ProxyIterator;
        }

        @Override
        public boolean isProxyNativeObject(Object proxy) {
            return proxy instanceof ProxyNativeObject;
        }

        @Override
        public boolean isProxyObject(Object proxy) {
            return proxy instanceof ProxyObject;
        }

        @Override
        public boolean isProxyTime(Object proxy) {
            return proxy instanceof ProxyTime;
        }

        @Override
        public boolean isProxyTimeZone(Object proxy) {
            return proxy instanceof ProxyTimeZone;
        }

        @Override
        public boolean isProxy(Object proxy) {
            return proxy instanceof Proxy;
        }

        @Override
        public Class<?> getProxyArrayClass() {
            return ProxyArray.class;
        }

        @Override
        public Class<?> getProxyDateClass() {
            return ProxyDate.class;
        }

        @Override
        public Class<?> getProxyDurationClass() {
            return ProxyDuration.class;
        }

        @Override
        public Class<?> getProxyExecutableClass() {
            return ProxyExecutable.class;
        }

        @Override
        public Class<?> getProxyHashMapClass() {
            return ProxyHashMap.class;
        }

        @Override
        public Class<?> getProxyInstantClass() {
            return ProxyInstant.class;
        }

        @Override
        public Class<?> getProxyInstantiableClass() {
            return ProxyInstantiable.class;
        }

        @Override
        public Class<?> getProxyIterableClass() {
            return ProxyIterable.class;
        }

        @Override
        public Class<?> getProxyIteratorClass() {
            return ProxyIterator.class;
        }

        @Override
        public Class<?> getProxyNativeObjectClass() {
            return ProxyNativeObject.class;
        }

        @Override
        public Class<?> getProxyObjectClass() {
            return ProxyObject.class;
        }

        @Override
        public Class<?> getProxyTimeClass() {
            return ProxyTime.class;
        }

        @Override
        public Class<?> getProxyTimeZoneClass() {
            return ProxyTimeZone.class;
        }

        @Override
        public Class<?> getProxyClass() {
            return Proxy.class;
        }

        @Override
        public Object callProxyExecutableExecute(Object proxy, Object[] objects) {
            return ((ProxyExecutable) proxy).execute((Value[]) objects);
        }

        @Override
        public Object callProxyNativeObjectAsPointer(Object proxy) {
            return ((ProxyNativeObject) proxy).asPointer();
        }

        @Override
        public Object callProxyInstantiableNewInstance(Object proxy, Object[] objects) {
            return ((ProxyInstantiable) proxy).newInstance((Value[]) objects);
        }

        @Override
        public Object callProxyArrayGet(Object proxy, long index) {
            return ((ProxyArray) proxy).get(index);
        }

        @Override
        public void callProxyArraySet(Object proxy, long index, Object value) {
            ((ProxyArray) proxy).set(index, (Value) value);
        }

        @Override
        public boolean callProxyArrayRemove(Object proxy, long index) {
            return ((ProxyArray) proxy).remove(index);
        }

        @Override
        public Object callProxyArraySize(Object proxy) {
            return ((ProxyArray) proxy).getSize();
        }

        @Override
        public Object callProxyObjectMemberKeys(Object proxy) {
            Object result = ((ProxyObject) proxy).getMemberKeys();
            if (result == null) {
                result = EMPTY;
            }
            return result;
        }

        @Override
        public Object callProxyObjectGetMember(Object proxy, String member) {
            return ((ProxyObject) proxy).getMember(member);
        }

        @Override
        public void callProxyObjectPutMember(Object proxy, String member, Object value) {
            ((ProxyObject) proxy).putMember(member, (Value) value);
        }

        @Override
        public boolean callProxyObjectRemoveMember(Object proxy, String member) {
            return ((ProxyObject) proxy).removeMember(member);
        }

        @Override
        public Object callProxyObjectHasMember(Object proxy, String member) {
            return ((ProxyObject) proxy).hasMember(member);
        }

        @Override
        public ZoneId callProxyTimeZoneAsTimeZone(Object proxy) {
            return ((ProxyTimeZone) proxy).asTimeZone();
        }

        @Override
        public LocalDate callProxyDateAsDate(Object proxy) {
            return ((ProxyDate) proxy).asDate();
        }

        @Override
        public LocalTime callProxyTimeAsTime(Object proxy) {
            return ((ProxyTime) proxy).asTime();
        }

        @Override
        public Instant callProxyInstantAsInstant(Object proxy) {
            return ((ProxyInstant) proxy).asInstant();
        }

        @Override
        public Duration callProxyDurationAsDuration(Object proxy) {
            return ((ProxyDuration) proxy).asDuration();
        }

        @Override
        public Object callProxyIterableGetIterator(Object proxy) {
            return ((ProxyIterable) proxy).getIterator();
        }

        @Override
        public Object callProxyIteratorHasNext(Object proxy) {
            return ((ProxyIterator) proxy).hasNext();
        }

        @Override
        public Object callProxyIteratorGetNext(Object proxy) {
            return ((ProxyIterator) proxy).getNext();
        }

        @Override
        public Object callProxyHashMapHasHashEntry(Object proxy, Object key) {
            return ((ProxyHashMap) proxy).hasHashEntry((Value) key);
        }

        @Override
        public Object callProxyHashMapGetHashSize(Object proxy) {
            return ((ProxyHashMap) proxy).getHashSize();
        }

        @Override
        public Object callProxyHashMapGetHashValue(Object proxy, Object key) {
            return ((ProxyHashMap) proxy).getHashValue((Value) key);
        }

        @Override
        public void callProxyHashMapPutHashEntry(Object proxy, Object key, Object value) {
            ((ProxyHashMap) proxy).putHashEntry((Value) key, (Value) value);
        }

        @Override
        public Object callProxyHashMapRemoveHashEntry(Object proxy, Object key) {
            return ((ProxyHashMap) proxy).removeHashEntry((Value) key);
        }

        @Override
        public Object callProxyHashMapGetEntriesIterator(Object proxy) {
            return ((ProxyHashMap) proxy).getHashEntriesIterator();
        }

        @Override
        public Object getIOAccessNone() {
            return IOAccess.NONE;
        }

        @Override
        public Object getEnvironmentAccessNone() {
            return EnvironmentAccess.NONE;
        }

        @Override
        public Object getEnvironmentAccessInherit() {
            return EnvironmentAccess.INHERIT;
        }

        @Override
        public Object getPolyglotAccessNone() {
            return PolyglotAccess.NONE;
        }

        @Override
        public Object getPolyglotAccessAll() {
            return PolyglotAccess.ALL;
        }

        @Override
        public Object createPolyglotAccess(Set<String> bindingsAccess, Map<String, Set<String>> evalAccess) {
            PolyglotAccess.Builder builder = PolyglotAccess.newBuilder();
            for (String lang : bindingsAccess) {
                builder.allowBindingsAccess(lang);
            }
            for (Map.Entry<String, Set<String>> e : evalAccess.entrySet()) {
                String from = e.getKey();
                for (String to : e.getValue()) {
                    builder.allowEval(from, to);
                }
            }
            return builder.build();
        }

        @Override
        public Object getHostAccessNone() {
            return HostAccess.NONE;
        }

        @Override
        public Object getIOAccessAll() {
            return IOAccess.ALL;
        }

        @Override
        public Object[] newValueArray(int size) {
            return new Value[size];
        }

        @Override
        public Class<?> getValueClass() {
            return Value.class;
        }

        @Override
        public <T> T callValueAs(Object delegateBindings, Class<T> targetType) {
            return ((Value) delegateBindings).as(targetType);
        }

        @Override
        public <T> T callValueAs(Object delegateBindings, Class<T> rawType, Type type) {
            Value v = (Value) delegateBindings;
            return v.dispatch.asTypeLiteral(v.context, v.receiver, rawType, type);
        }

        @Override
        public Object callValueGetMetaObject(Object delegateBindings) {
            return ((Value) delegateBindings).getMetaObject();
        }

        @Override
        public long callValueGetArraySize(Object value) {
            return ((Value) value).getArraySize();
        }

        @Override
        public Object callValueGetArrayElement(Object value, int i) {
            return ((Value) value).getArrayElement(i);
        }

        @Override
        public boolean callValueIsString(Object value) {
            return ((Value) value).isString();
        }

        @Override
        public String callValueAsString(Object value) {
            return ((Value) value).asString();
        }

        @Override
        public Object contextAsValue(Object context, Object hostValue) {
            return ((Context) context).asValue(hostValue);
        }

        @Override
        public void contextClose(Object context, boolean cancelIfClosing) {
            ((Context) context).close(cancelIfClosing);
        }

        @Override
        public void contextEnter(Object context) {
            ((Context) context).enter();
        }

        @Override
        public void contextLeave(Object context) {
            ((Context) context).leave();
        }

        @Override
        public Object getContextEngine(Object context) {
            return ((Context) context).getEngine();
        }

        @Override
        public Class<?> getPolyglotExceptionClass() {
            return PolyglotException.class;
        }

        @Override
        public Object callContextAsValue(Object current, Object classOverrides) {
            return ((Context) current).asValue(classOverrides);
        }

        @Override
        public Object callContextGetCurrent() {
            return Context.getCurrent();
        }

    }

    private static AbstractPolyglotImpl loadAndValidateProviders(Iterator<? extends AbstractPolyglotImpl> providers) throws AssertionError {
        List<AbstractPolyglotImpl> impls = new ArrayList<>();
        while (providers.hasNext()) {
            AbstractPolyglotImpl found = providers.next();
            for (AbstractPolyglotImpl impl : impls) {
                if (impl.getClass().getName().equals(found.getClass().getName())) {
                    throw new AssertionError("Same polyglot impl found twice on the classpath.");
                }
            }
            impls.add(found);
        }
        Collections.sort(impls, Comparator.comparing(AbstractPolyglotImpl::getPriority));
        Version polyglotVersion = Boolean.getBoolean("polyglotimpl.DisableVersionChecks") ? null : getPolyglotVersion();
        AbstractPolyglotImpl prev = null;
        for (AbstractPolyglotImpl impl : impls) {
            if (impl.getPriority() == Integer.MIN_VALUE) {
                // disabled
                continue;
            }
            if (polyglotVersion != null) {
                String truffleVersionString = impl.getTruffleVersion();
                Version truffleVersion = truffleVersionString != null ? Version.parse(truffleVersionString) : Version.create(23, 1, 1);
                if (!polyglotVersion.equals(truffleVersion)) {
                    StringBuilder errorMessage = new StringBuilder(String.format("""
                                    Polyglot version compatibility check failed.
                                    The polyglot version '%s' is not compatible to the used Truffle version '%s'.
                                    """, polyglotVersion, truffleVersion));
                    if (polyglotVersion.compareTo(truffleVersion) < 0) {
                        errorMessage.append(String.format("""
                                        The polyglot version is older than the Truffle or language version in use.
                                        The polygot and truffle version must always match.
                                        Update the org.graalvm.polyglot versions to '%s' to resolve this.
                                        """, truffleVersion));
                    } else {
                        errorMessage.append((String.format("""
                                        The Truffle or language version is older than the polyglot version in use.
                                        The polygot and truffle version must always match.
                                        Update the Truffle or language versions to '%s' to resolve this.
                                        """, polyglotVersion)));
                    }
                    errorMessage.append("""
                                    To disable this version check the '-Dpolyglotimpl.DisableVersionChecks=true' system property can be used.
                                    It is not recommended to disable version checks.
                                    """);
                    throw new IllegalStateException(errorMessage.toString());
                }
            }
            impl.setNext(prev);
            try {
                impl.setConstructors(APIAccessImpl.INSTANCE);

                Field ioAccess = Class.forName("org.graalvm.polyglot.io.IOHelper").getDeclaredField("ACCESS");
                ioAccess.setAccessible(true);
                impl.setIO((IOAccessor) ioAccess.get(null));

                Field managementAccess = Class.forName("org.graalvm.polyglot.management.Management").getDeclaredField("ACCESS");
                managementAccess.setAccessible(true);
                impl.setMonitoring((ManagementAccess) managementAccess.get(null));
            } catch (ReflectiveOperationException e) {
                throw new InternalError(e);
            }
            impl.initialize();
            prev = impl;
        }

        return prev;
    }

    private static Version getPolyglotVersion() {
        InputStream in = Engine.class.getResourceAsStream("/META-INF/graalvm/org.graalvm.polyglot/version");
        if (in == null) {
            throw new InternalError("Polyglot must have a version file.");
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return Version.parse(r.readLine());
        } catch (IOException ioe) {
            throw new InternalError(ioe);
        }
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    private static AbstractPolyglotImpl initEngineImpl() {
        return AccessController.doPrivileged(new PrivilegedAction<AbstractPolyglotImpl>() {

            public AbstractPolyglotImpl run() {
                AbstractPolyglotImpl polyglot = null;
                if (!Boolean.getBoolean("graalvm.ForcePolyglotInvalid")) {
                    polyglot = loadAndValidateProviders(searchServiceLoader());
                }
                if (polyglot == null) {
                    polyglot = loadAndValidateProviders(createInvalidPolyglotImpl());
                }
                return polyglot;
            }

            private Iterator<? extends AbstractPolyglotImpl> searchServiceLoader() throws InternalError {
                Class<AbstractPolyglotImpl> serviceClass = AbstractPolyglotImpl.class;
                Iterator<? extends AbstractPolyglotImpl> iterator;
                Module polyglotModule = serviceClass.getModule();

                if (!polyglotModule.isNamed() && ClassPathIsolation.isEnabled()) {
                    return ClassPathIsolation.createIsolatedTruffle(serviceClass);
                } else {
                    Iterable<? extends AbstractPolyglotImpl> services;
                    if (polyglotModule.isNamed()) {
                        services = ServiceLoader.load(polyglotModule.getLayer(), AbstractPolyglotImpl.class);
                    } else {
                        services = ServiceLoader.load(serviceClass, serviceClass.getClassLoader());
                    }
                    iterator = services.iterator();
                    if (!iterator.hasNext()) {
                        services = ServiceLoader.load(AbstractPolyglotImpl.class);
                        iterator = services.iterator();
                    }
                }
                return iterator;
            }

        });
    }

    /**
     * If Truffle is on the class-path (or a language), we do not want to expose these classes to
     * embedders (users of the polyglot API). Unless disabled, we load all Truffle jars on the
     * class-path in a special module layer instead of loading it through the class-path in the
     * unnamed module.
     */
    private static class ClassPathIsolation {

        private static final String TRUFFLE_MODULE_NAME = "org.graalvm.truffle";
        private static final String POLYGLOT_MODULE_NAME = "org.graalvm.polyglot";
        private static final String OPTION_DISABLE_CLASS_PATH_ISOLATION = "polyglotimpl.DisableClassPathIsolation";
        private static final String OPTION_TRACE_CLASS_PATH_ISOLATION = "polyglotimpl.TraceClassPathIsolation";

        private static final boolean TRACE_CLASS_PATH_ISOLATION = Boolean.getBoolean(OPTION_TRACE_CLASS_PATH_ISOLATION);
        private static final boolean DISABLE_CLASS_PATH_ISOLATION = Boolean.parseBoolean(System.getProperty(OPTION_DISABLE_CLASS_PATH_ISOLATION, "true"));

        static boolean isEnabled() {
            return !DISABLE_CLASS_PATH_ISOLATION;
        }

        static Iterator<? extends AbstractPolyglotImpl> createIsolatedTruffle(Class<?> serviceClass) {
            Module truffleModule = ClassPathIsolation.createIsolatedTruffleModule(serviceClass);
            if (truffleModule == null) {
                /*
                 * Class path isolation did not work for some reason then we create an empty
                 * polyglot.
                 */
                return createInvalidPolyglotImpl();
            }

            Class<?> modulePolyglot;
            try {
                modulePolyglot = truffleModule.getClassLoader().loadClass(AbstractPolyglotImpl.class.getName());
            } catch (ClassNotFoundException e) {
                // class must be found on a layer with the truffle module.
                throw new InternalError(e);
            }
            if (modulePolyglot == serviceClass) {
                throw new AssertionError("Expected module polyglot to be a different class to the service class.");
            }
            Iterator<? extends Object> modulePolyglotIterator = ServiceLoader.load(truffleModule.getLayer(), modulePolyglot).iterator();
            /*
             * If the polylgot implementation is in separate named module. We need to use polyglot
             * module bridge to wrap the entire API to delegate from the unnamed module to a named
             * module.
             */
            Object polyglotImpl;
            try {
                Class<?> engine = truffleModule.getClassLoader().loadClass(Engine.class.getName());
                Method m = engine.getDeclaredMethod("loadAndValidateProviders", Iterator.class);
                m.setAccessible(true);
                polyglotImpl = m.invoke(null, modulePolyglotIterator);
                return List.of(UnnamedToModuleBridge.create(modulePolyglot, polyglotImpl).getPolyglot()).iterator();
            } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                throw new InternalError(e);
            }
        }

        static Module createIsolatedTruffleModule(Class<?> polyglotClass) {
            if (TRACE_CLASS_PATH_ISOLATION) {
                trace("Start polyglot class-path isolation");
            }
            assert !polyglotClass.getModule().isNamed();
            assert isEnabled();

            ModuleLayer parent = ModuleLayer.boot();
            ClassLoader polyglotClassLoader = polyglotClass.getClassLoader();

            Optional<Module> alreadyLoadedTruffle = parent.findModule(TRUFFLE_MODULE_NAME);
            if (alreadyLoadedTruffle.isPresent()) {
                /*
                 * This may happen if truffle is already configured on the module-path or if if
                 * polyglot was used in the unnamed module (class-path) and an isolated module class
                 * loader was spawned to load truffle.
                 */
                if (TRACE_CLASS_PATH_ISOLATION) {
                    trace("Polyglot is available on the module-path. Class isolation is not needed.");
                }
                return alreadyLoadedTruffle.get();
            }

            List<Path> classpath = collectClassPathJars(polyglotClassLoader);
            if (TRACE_CLASS_PATH_ISOLATION) {
                for (Path p : classpath) {
                    trace("Class-path entry: %s", p);
                }
            }
            List<Path> relevantPaths = filterClasspath(parent, classpath);
            if (relevantPaths == null) {
                if (TRACE_CLASS_PATH_ISOLATION) {
                    trace("No truffle-api and/or polyglot found on classpath. ");
                }
                return null;
            }

            if (TRACE_CLASS_PATH_ISOLATION) {
                trace("Filtering complete using %s out of %s class-path entries.", relevantPaths.size(), classpath.size());
            }

            if (TRACE_CLASS_PATH_ISOLATION) {
                for (Path p : classpath) {
                    trace("Class-path entry: %s", p);
                }
            }

            ModuleFinder finder = ModuleFinder.of(relevantPaths.toArray(new Path[relevantPaths.size()]));
            Configuration config;
            try {
                config = parent.configuration().resolveAndBind(finder, ModuleFinder.of(), Set.of(TRUFFLE_MODULE_NAME));
            } catch (Throwable t) {
                throw new InternalError(
                                "The polyglot class isolation failed to load and resolve the module layer. This is typically caused by invalid class-path-entries or duplicated packages on the class-path. Use -D" +
                                                OPTION_TRACE_CLASS_PATH_ISOLATION +
                                                "=true to print more details about class loading isolation. " +
                                                "If the problem persists, it is recommended to use the module-path instead of the class-path to load polyglot.",
                                t);
            }
            if (config.modules().isEmpty()) {
                // no new modules found
                if (TRACE_CLASS_PATH_ISOLATION) {
                    trace("No polyglot related implementation modules found on the class-path");
                }
                return null;
            }
            if (TRACE_CLASS_PATH_ISOLATION) {
                trace("Successfuly resolved modules from class-path for class loader isolation.");
                for (ResolvedModule module : config.modules()) {
                    trace("Resolved module: %s", module.name());
                }
            }

            Controller polyglotController = ModuleLayer.defineModulesWithOneLoader(config, List.of(parent), polyglotClassLoader);
            Module polyglotModule = polyglotController.layer().findModule("org.graalvm.polyglot").get();

            // Special addOpens required for the unnamed to module bridge in polyglot.
            polyglotController.addOpens(polyglotModule, "org.graalvm.polyglot.impl", polyglotClassLoader.getUnnamedModule());
            polyglotController.addOpens(polyglotModule, "org.graalvm.polyglot", polyglotClassLoader.getUnnamedModule());

            Module truffle = polyglotController.layer().findModule(TRUFFLE_MODULE_NAME).orElse(null);
            if (truffle == null) {
                if (TRACE_CLASS_PATH_ISOLATION) {
                    trace("Final resolve of Truffle failed. This could indicate a bug.");
                }
            }

            return truffle;
        }

        private static void trace(String s, Object... args) {
            PrintStream out = System.out;
            out.printf("[class-path-isolation] %s%n", String.format(s, args));
        }

        /*
         * Class path specifications are not build for the module-path. So we need to be careful
         * what we put on the module-path for Truffle. This method aims to filter the classpath for
         * only relevent path entries.
         */
        private static List<Path> filterClasspath(ModuleLayer parent, List<Path> classpath) throws InternalError {
            record ParsedModule(Path p, Set<ModuleReference> modules) {
            }

            List<ParsedModule> parsedModules = new ArrayList<>();
            for (Path path : classpath) {
                ModuleFinder finder = ModuleFinder.of(path);
                try {
                    Set<ModuleReference> modules = finder.findAll();
                    if (modules.size() > 0) {
                        parsedModules.add(new ParsedModule(path, modules));
                    } else {
                        if (TRACE_CLASS_PATH_ISOLATION) {
                            trace("No modules found in class-path entry %s", path);
                        }
                    }
                } catch (FindException t) {
                    // error in module finding -> not a valid module descriptor ignore
                    if (TRACE_CLASS_PATH_ISOLATION) {
                        trace("FinderException resolving path %s: %s", path, t.toString());
                    }
                } catch (Throwable t) {
                    throw new InternalError("Parsing class-path for path " + path + " failed.", t);
                }
            }

            // first find truffle and polyglot on the class-path
            Set<String> includedModules = new LinkedHashSet<>();
            for (ParsedModule parsedModule : parsedModules) {
                for (ModuleReference m : parsedModule.modules()) {
                    String name = m.descriptor().name();
                    switch (name) {
                        case TRUFFLE_MODULE_NAME:
                        case POLYGLOT_MODULE_NAME:
                            includedModules.add(name);
                            break;
                    }
                    if (includedModules.size() == 2) {
                        // found truffle and polyglot
                        break;
                    }
                }
            }
            if (includedModules.size() != 2) {
                // truffle or polyglot not found on the class-path
                return null;
            }

            // now iteratively resolve modules until no more modules are included
            List<ParsedModule> toProcess = new ArrayList<>(parsedModules);
            Set<String> usedServices = new HashSet<>();
            int size = 0;
            while (includedModules.size() != size) {
                size = includedModules.size();

                ListIterator<ParsedModule> modules = toProcess.listIterator();
                while (modules.hasNext()) {
                    ParsedModule module = modules.next();
                    for (ModuleReference m : module.modules) {
                        ModuleDescriptor d = m.descriptor();

                        for (Provides p : d.provides()) {
                            if (usedServices.contains(p.service())) {
                                if (includedModules.add(d.name())) {
                                    if (TRACE_CLASS_PATH_ISOLATION) {
                                        trace("Include module '%s' because an implementation for '%s' is provided that is used.", d.name(), p.service());
                                    }
                                }
                                break;
                            }
                        }
                        if (includedModules.contains(d.name())) {
                            usedServices.addAll(d.uses());
                            for (Requires r : d.requires()) {
                                /*
                                 * We deliberately follow static resources here, even though they
                                 * are not real dependencies in the module-graph. If we don't follow
                                 * static requires we might fallback to the parent class-loader for
                                 * these classes causing weird problems. So if the module is on the
                                 * class-path and there is a static requires we pick it up into the
                                 * module-graph well.
                                 */
                                if (includedModules.add(r.name())) {
                                    if (TRACE_CLASS_PATH_ISOLATION) {
                                        trace("Include module '%s' because it is required by '%s'.", r.name(), d.name());
                                    }
                                }
                            }
                            // module processed. we no longer need to visit it
                            modules.remove();
                        }
                    }
                }
            }

            List<Path> filteredList = new ArrayList<>();
            for (ParsedModule module : parsedModules) {
                for (ModuleReference ref : module.modules()) {
                    String name = ref.descriptor().name();
                    if (!includedModules.contains(name)) {
                        if (TRACE_CLASS_PATH_ISOLATION) {
                            trace("Filter module '%s' because not reachable on the module graph.", name);
                        }
                        continue;
                    }

                    if (parent.findModule(name).isPresent()) {
                        if (TRACE_CLASS_PATH_ISOLATION) {
                            trace("Filter module '%s' because already available in the parent module-layer.", name);
                        }
                        continue;
                    }

                    filteredList.add(module.p());
                    break;
                }
            }

            return filteredList;
        }

        @SuppressWarnings("unchecked")
        private static List<Path> collectClassPathJars(ClassLoader cl) {
            List<Path> paths = new ArrayList<>();
            if (cl instanceof URLClassLoader) {
                URLClassLoader urlClassLoader = (URLClassLoader) cl;
                for (URL url : urlClassLoader.getURLs()) {
                    try {
                        paths.add(Path.of(url.toURI().getPath()));
                    } catch (URISyntaxException e) {
                        // ignore invalid syntax
                    }
                }
                if (TRACE_CLASS_PATH_ISOLATION) {
                    trace("Collected %s class-path entries from URLClassloader", paths.size());
                }

            } else if (System.getProperty("java.class.path") != null) {
                String classpath = System.getProperty("java.class.path");
                String[] classpathEntries = classpath.split(File.pathSeparator);
                for (String entry : classpathEntries) {
                    paths.add(Paths.get(entry));
                }
                if (TRACE_CLASS_PATH_ISOLATION) {
                    trace("Collected %s class-path entries from java.class.path system property", paths.size());
                }
            } else {
                trace("Could not resolve class-path entries from environment. The class-path isolation only supports URLClassLoader and the java.class.path system property. " +
                                "If you are using a custom class loader use a URLClassLoader base class or set the java.class.path system property to allow scanning of class-path entries.");
            }
            return paths;
        }

    }

    /*
     * Use static factory method with AbstractPolyglotImpl to avoid class loading of the
     * PolyglotInvalid class by the Java verifier.
     */
    static Iterator<? extends AbstractPolyglotImpl> createInvalidPolyglotImpl() {
        return Arrays.asList(new PolyglotInvalid()).iterator();
    }

    private static class PolyglotInvalid extends AbstractPolyglotImpl {
        PolyglotInvalid() {
        }

        @Override
        public int getPriority() {
            // make sure polyglot invalid has lowest priority but is not filtered (hence + 1)
            return Integer.MIN_VALUE + 1;
        }

        @Override
        public Object getCurrentContext() {
            throw noPolyglotImplementationFound();
        }

        @Override
        public Object buildEngine(String[] permittedLanguages, SandboxPolicy sandboxPolicy, OutputStream out, OutputStream err, InputStream in, Map<String, String> arguments,
                        boolean allowExperimentalOptions, boolean boundEngine, MessageTransport messageInterceptor, Object logHandler, Object hostLanguage,
                        boolean hostLanguageOnly, boolean registerInActiveEngines, Object polyglotHostService) {
            throw noPolyglotImplementationFound();
        }

        @Override
        public Object createHostLanguage(Object access) {
            throw noPolyglotImplementationFound();
        }

        @Override
        public Object buildLimits(long statementLimit, Predicate<Object> statementLimitSourceFilter, Consumer<Object> onLimit) {
            throw noPolyglotImplementationFound();
        }

        @Override
        public AbstractHostAccess createHostAccess() {
            throw noPolyglotImplementationFound();
        }

        @Override
        public boolean copyResources(Path targetFolder, String... components) {
            throw noPolyglotImplementationFound();
        }

        private static RuntimeException noPolyglotImplementationFound() {
            if (PolyglotInvalid.class.getModule().isNamed()) {
                return new IllegalStateException(
                                "No language and polyglot implementation was found on the module-path. " +
                                                "Make sure at last one language is added to the module-path. ");
            } else {
                return new IllegalStateException(
                                "No language and polyglot implementation was found on the class-path. " +
                                                "Make sure at last one language is added on the class-path. " +
                                                "If you put a language on the class-path and you encounter this error then there could be a problem with isolated class loading. " +
                                                "Use -Dpolyglotimpl.TraceClassPathIsolation=true to debug class loader islation problems. " +
                                                "For best performance it is recommended to use polyglot from the module-path instead of the class-path.");
            }
        }

        @Override
        public Class<?> loadLanguageClass(String className) {
            return null;
        }

        @Override
        public void preInitializeEngine() {
        }

        @Override
        public void resetPreInitializedEngine() {
        }

        @Override
        public Object asValue(Object o) {
            throw noPolyglotImplementationFound();
        }

        @Override
        public FileSystem newDefaultFileSystem(String hostTmpDir) {
            throw noPolyglotImplementationFound();
        }

        @Override
        public FileSystem allowLanguageHomeAccess(FileSystem fileSystem) {
            throw noPolyglotImplementationFound();
        }

        @Override
        public FileSystem newReadOnlyFileSystem(FileSystem fileSystem) {
            throw noPolyglotImplementationFound();
        }

        @Override
        public FileSystem newNIOFileSystem(java.nio.file.FileSystem fileSystem) {
            throw noPolyglotImplementationFound();
        }

        @Override
        public ProcessHandler newDefaultProcessHandler() {
            throw noPolyglotImplementationFound();
        }

        @Override
        public boolean isDefaultProcessHandler(ProcessHandler processHandler) {
            return false;
        }

        @Override
        public boolean isInternalFileSystem(FileSystem fileSystem) {
            return false;
        }

        @Override
        public ThreadScope createThreadScope() {
            return null;
        }

        @Override
        public boolean isInCurrentEngineHostCallback(Object engine) {
            return false;
        }

        @Override
        public OptionDescriptors createUnionOptionDescriptors(OptionDescriptors... optionDescriptors) {
            return OptionDescriptors.createUnion(optionDescriptors);
        }

        @Override
        public <S, T> Object newTargetTypeMapping(Class<S> sourceType, Class<T> targetType, Predicate<S> acceptsValue, Function<S, T> convertValue, TargetMappingPrecedence precedence) {
            return new Object();
        }

        @Override
        public Source buildSource(String language, Object origin, URI uri, String name, String mimeType, Object content, boolean interactive, boolean internal, boolean cached, Charset encoding,
                        URL url,
                        String path)
                        throws IOException {
            throw noPolyglotImplementationFound();
        }

        @Override
        public String findLanguage(File file) throws IOException {
            return null;
        }

        @Override
        public String findLanguage(URL url) throws IOException {
            return null;
        }

        @Override
        public String findMimeType(File file) throws IOException {
            return null;
        }

        @Override
        public String findMimeType(URL url) throws IOException {
            return null;
        }

        @Override
        public String findLanguage(String mimeType) {
            return null;
        }

        @Override
        public String getTruffleVersion() {
            return getPolyglotVersion().toString();
        }
    }

    private static final class EngineShutDownHook implements Runnable {

        public void run() {
            Engine[] engines;
            synchronized (ENGINES) {
                engines = ENGINES.keySet().toArray(new Engine[0]);
            }
            for (Engine engine : engines) {
                engine.dispatch.shutdown(engine.receiver);
            }
        }
    }
}
