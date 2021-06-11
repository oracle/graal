/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;

import org.graalvm.collections.UnmodifiableEconomicSet;
import org.graalvm.home.HomeFinder;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.HostAccess.TargetMappingPrecedence;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractContextDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractEngineDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractExceptionDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractInstrumentDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractLanguageDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractStackFrameImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractValueDispatch;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.MessageTransport;
import org.graalvm.polyglot.management.ExecutionEvent;

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
         * (com.oracle.svm.truffle.TruffleFeature).
         */
        @SuppressWarnings("unused")
        private static void preInitializeEngine() {
            IMPL.preInitializeEngine();
        }

        /**
         * Clears the pre-initialized engine.
         *
         * NOTE: this method is called reflectively by downstream projects
         * (com.oracle.svm.truffle.TruffleFeature).
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
    public Map<String, Language> getLanguages() {
        return dispatch.getLanguages(receiver);
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
    public Map<String, Instrument> getInstruments() {
        return dispatch.getInstruments(receiver);
    }

    /**
     * Returns all options available for the engine. The engine offers options with the following
     * {@link OptionDescriptor#getKey() groups}:
     * <ul>
     * <li><b>engine</b>: options to configure the behavior of this engine.
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
     * Creates a new engine instance with default configuration. The engine is constructed with the
     * same configuration as it will be as when constructed implicitly using the context builder.
     *
     * @see Context#create(String...) to create a new execution context.
     * @since 19.0
     */
    public static Engine create() {
        return newBuilder().build();
    }

    /**
     * Creates a new context builder that allows to configure an engine instance.
     *
     * @see Context#newBuilder(String...) to construct a new execution context.
     * @since 19.0
     */
    public static Builder newBuilder() {
        return EMPTY.new Builder();
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
    public Set<Source> getCachedSources() {
        return dispatch.getCachedSources(receiver);
    }

    static AbstractPolyglotImpl getImpl() {
        return ImplHolder.IMPL;
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
        return (Collection<Engine>) getImpl().findActiveEngines();
    }

    private static final Engine EMPTY = new Engine(null, null);

    /**
     *
     * @since 19.0
     */
    @SuppressWarnings("hiding")
    public final class Builder {

        private OutputStream out = System.out;
        private OutputStream err = System.err;
        private InputStream in = System.in;
        private Map<String, String> options = new HashMap<>();
        private boolean allowExperimentalOptions = false;
        private boolean useSystemProperties = true;
        private boolean boundEngine;
        private MessageTransport messageTransport;
        private Object customLogHandler;

        Builder() {
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
            Engine engine = polyglot.buildEngine(out, err, in, options, useSystemProperties, allowExperimentalOptions,
                            boundEngine, messageTransport, customLogHandler, polyglot.createHostLanguage(polyglot.createHostAccess()), false);
            return engine;
        }

    }

    static class APIAccessImpl extends AbstractPolyglotImpl.APIAccess {

        private static final APIAccessImpl INSTANCE = new APIAccessImpl();

        APIAccessImpl() {
        }

        @Override
        public Context newContext(AbstractContextDispatch dispatch, Object receiver, Engine engine) {
            return new Context(dispatch, receiver, engine);
        }

        @Override
        public Engine newEngine(AbstractEngineDispatch dispatch, Object receiver) {
            return new Engine(dispatch, receiver);
        }

        @Override
        public Language newLanguage(AbstractLanguageDispatch dispatch, Object receiver) {
            return new Language(dispatch, receiver);
        }

        @Override
        public Instrument newInstrument(AbstractInstrumentDispatch dispatch, Object receiver) {
            return new Instrument(dispatch, receiver);
        }

        @Override
        public Object getReceiver(Instrument instrument) {
            return instrument.receiver;
        }

        @Override
        public Object getContext(Value value) {
            return value.context;
        }

        @Override
        public Value newValue(AbstractValueDispatch dispatch, Object context, Object receiver) {
            return new Value(dispatch, context, receiver);
        }

        @Override
        public Source newSource(Object receiver) {
            return new Source(receiver);
        }

        @Override
        public SourceSection newSourceSection(Source source, Object receiver) {
            return new SourceSection(source, receiver);
        }

        @Override
        public AbstractValueDispatch getDispatch(Value value) {
            return value.dispatch;
        }

        @Override
        public AbstractInstrumentDispatch getDispatch(Instrument value) {
            return value.dispatch;
        }

        @Override
        public AbstractContextDispatch getDispatch(Context context) {
            return context.dispatch;
        }

        @Override
        public AbstractEngineDispatch getDispatch(Engine engine) {
            return engine.dispatch;
        }

        @Override
        public ResourceLimitEvent newResourceLimitsEvent(Context context) {
            return new ResourceLimitEvent(context);
        }

        @Override
        public AbstractLanguageDispatch getDispatch(Language value) {
            return value.dispatch;
        }

        @Override
        public Object getReceiver(ResourceLimits value) {
            return value.receiver;
        }

        @Override
        public PolyglotException newLanguageException(String message, AbstractExceptionDispatch dispatch, Object receiver) {
            return new PolyglotException(message, dispatch, receiver);
        }

        @Override
        public AbstractStackFrameImpl getDispatch(StackFrame value) {
            return value.impl;
        }

        @Override
        public Object getReceiver(Value value) {
            return value.receiver;
        }

        @Override
        public Object getReceiver(Context context) {
            return context.receiver;
        }

        @Override
        public Object getReceiver(Engine engine) {
            return engine.receiver;
        }

        @Override
        public Object getReceiver(PolyglotException polyglot) {
            return polyglot.impl;
        }

        @Override
        public StackFrame newPolyglotStackTraceElement(AbstractStackFrameImpl dispatch, Object receiver) {
            return ((PolyglotException) receiver).new StackFrame(dispatch);
        }

        @Override
        public boolean allowsAccess(HostAccess access, AnnotatedElement element) {
            return access.allowsAccess(element);
        }

        @Override
        public boolean allowsImplementation(HostAccess access, Class<?> type) {
            return access.allowsImplementation(type);
        }

        @Override
        public List<Object> getTargetMappings(HostAccess access) {
            return access.getTargetMappings();
        }

        @Override
        public boolean isArrayAccessible(HostAccess access) {
            return access.allowArrayAccess;
        }

        @Override
        public boolean isListAccessible(HostAccess access) {
            return access.allowListAccess;
        }

        @Override
        public boolean isBufferAccessible(HostAccess access) {
            return access.allowBufferAccess;
        }

        @Override
        public boolean isIterableAccessible(HostAccess access) {
            return access.allowIterableAccess;
        }

        @Override
        public boolean isIteratorAccessible(HostAccess access) {
            return access.allowIteratorAccess;
        }

        @Override
        public boolean isMapAccessible(HostAccess access) {
            return access.allowMapAccess;
        }

        @Override
        public Object getHostAccessImpl(HostAccess conf) {
            return conf.impl;
        }

        @Override
        public void setHostAccessImpl(HostAccess conf, Object impl) {
            conf.impl = impl;
        }

        @Override
        public UnmodifiableEconomicSet<String> getEvalAccess(PolyglotAccess access, String language) {
            return access.getEvalAccess(language);
        }

        @Override
        public UnmodifiableEconomicSet<String> getBindingsAccess(PolyglotAccess access) {
            return access.getBindingsAccess();
        }

        @Override
        public String validatePolyglotAccess(PolyglotAccess access, UnmodifiableEconomicSet<String> languages) {
            return access.validate(languages);
        }

    }

    private static final boolean JDK8_OR_EARLIER = System.getProperty("java.specification.version").compareTo("1.9") < 0;

    @SuppressWarnings("unchecked")
    private static AbstractPolyglotImpl initEngineImpl() {
        return AccessController.doPrivileged(new PrivilegedAction<AbstractPolyglotImpl>() {
            public AbstractPolyglotImpl run() {
                AbstractPolyglotImpl polyglot = null;
                Class<?> servicesClass = null;
                if (Boolean.getBoolean("graalvm.ForcePolyglotInvalid")) {
                    polyglot = loadAndValidateProviders(createInvalidPolyglotImpl());
                } else {
                    if (JDK8_OR_EARLIER) {
                        try {
                            servicesClass = Class.forName("jdk.vm.ci.services.Services");
                        } catch (ClassNotFoundException e) {
                        }
                        if (servicesClass != null) {
                            try {
                                Method m = servicesClass.getDeclaredMethod("load", Class.class);
                                polyglot = loadAndValidateProviders(((Iterable<? extends AbstractPolyglotImpl>) m.invoke(null, AbstractPolyglotImpl.class)).iterator());
                            } catch (Throwable e) {
                                // Fail fast for other errors
                                throw new InternalError(e);
                            }
                        }
                    }
                }

                if (polyglot == null) {
                    // >= JDK 9.
                    polyglot = loadAndValidateProviders(searchServiceLoader());
                }
                if (polyglot == null) {
                    polyglot = loadAndValidateProviders(createInvalidPolyglotImpl());
                }
                return polyglot;
            }

            private Iterator<? extends AbstractPolyglotImpl> searchServiceLoader() throws InternalError {
                return ServiceLoader.load(AbstractPolyglotImpl.class).iterator();
            }

            private AbstractPolyglotImpl loadAndValidateProviders(Iterator<? extends AbstractPolyglotImpl> providers) throws AssertionError {
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
                AbstractPolyglotImpl prev = null;
                for (AbstractPolyglotImpl impl : impls) {
                    impl.setNext(prev);
                    impl.setConstructors(APIAccessImpl.INSTANCE);
                    prev = impl;
                }
                return prev;
            }
        });
    }

    /*
     * Use static factory method with AbstractPolyglotImpl to avoid class loading of the
     * PolyglotInvalid class by the Java verifier.
     */
    static Iterator<? extends AbstractPolyglotImpl> createInvalidPolyglotImpl() {
        return Arrays.asList(new PolyglotInvalid()).iterator();
    }

    private static class PolyglotInvalid extends AbstractPolyglotImpl {

        private final EmptySource source = new EmptySource(this);

        /**
         * Forces ahead-of-time initialization.
         *
         * @since 0.8 or earlier
         */
        static boolean AOT;

        static {
            Boolean aot = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
                public Boolean run() {
                    return Boolean.getBoolean("com.oracle.graalvm.isaot");
                }
            });
            PolyglotInvalid.AOT = aot.booleanValue();
        }

        @Override
        public int getPriority() {
            return Integer.MIN_VALUE;
        }

        @Override
        public Context getCurrentContext() {
            throw noPolyglotImplementationFound();
        }

        @Override
        public Engine buildEngine(OutputStream out, OutputStream err, InputStream in, Map<String, String> arguments, boolean useSystemProperties, boolean allowExperimentalOptions, boolean boundEngine,
                        MessageTransport messageInterceptor, Object logHandlerOrStream, Object hostLanguage, boolean hostLanguageOnly) {
            throw noPolyglotImplementationFound();
        }

        @Override
        public Object createHostLanguage(AbstractHostAccess access) {
            throw noPolyglotImplementationFound();
        }

        @Override
        public Object buildLimits(long statementLimit, Predicate<Source> statementLimitSourceFilter, Consumer<ResourceLimitEvent> onLimit) {
            throw noPolyglotImplementationFound();
        }

        @Override
        public AbstractHostAccess createHostAccess() {
            throw noPolyglotImplementationFound();
        }

        @Override
        public AbstractManagementDispatch getManagementDispatch() {
            return new AbstractManagementDispatch(this) {

                @Override
                public boolean isExecutionEventStatement(Object impl) {
                    return false;
                }

                @Override
                public boolean isExecutionEventRoot(Object impl) {
                    return false;
                }

                @Override
                public boolean isExecutionEventExpression(Object impl) {
                    return false;
                }

                @Override
                public String getExecutionEventRootName(Object impl) {
                    throw noPolyglotImplementationFound();
                }

                @Override
                public PolyglotException getExecutionEventException(Object impl) {
                    throw noPolyglotImplementationFound();
                }

                @Override
                public Value getExecutionEventReturnValue(Object impl) {
                    throw noPolyglotImplementationFound();
                }

                @Override
                public SourceSection getExecutionEventLocation(Object impl) {
                    throw noPolyglotImplementationFound();
                }

                @Override
                public List<Value> getExecutionEventInputValues(Object impl) {
                    throw noPolyglotImplementationFound();
                }

                @Override
                public void closeExecutionListener(Object impl) {
                    throw noPolyglotImplementationFound();
                }

                @Override
                public Object attachExecutionListener(Object engine, Consumer<ExecutionEvent> onEnter, Consumer<ExecutionEvent> onReturn, boolean expressions, boolean statements,
                                boolean roots,
                                Predicate<Source> sourceFilter, Predicate<String> rootFilter, boolean collectInputValues, boolean collectReturnValues, boolean collectErrors) {
                    throw noPolyglotImplementationFound();
                }

            };
        }

        private static RuntimeException noPolyglotImplementationFound() {
            String suggestion;
            if (AOT) {
                suggestion = "Make sure a language is added to the classpath (e.g., native-image --language:js).";
            } else {
                suggestion = "Make sure the truffle-api.jar is on the classpath.";
            }
            return new IllegalStateException("No language and polyglot implementation was found on the classpath. " + suggestion);
        }

        @Override
        public AbstractSourceDispatch getSourceDispatch() {
            return source;
        }

        @Override
        public AbstractSourceSectionDispatch getSourceSectionDispatch() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Class<?> loadLanguageClass(String className) {
            return null;
        }

        @Override
        public Collection<Engine> findActiveEngines() {
            return Collections.emptyList();
        }

        @Override
        public void preInitializeEngine() {
        }

        @Override
        public void resetPreInitializedEngine() {
        }

        @Override
        public Value asValue(Object o) {
            throw noPolyglotImplementationFound();
        }

        @Override
        public FileSystem newDefaultFileSystem() {
            throw noPolyglotImplementationFound();
        }

        static class EmptySource extends AbstractSourceDispatch {

            protected EmptySource(AbstractPolyglotImpl engineImpl) {
                super(engineImpl);
            }

            @Override
            public Source build(String language, Object origin, URI uri, String name, String mimeType, Object content, boolean interactive, boolean internal, boolean cached, Charset encoding)
                            throws IOException {
                throw noPolyglotImplementationFound();
            }

            @Override
            public String getLanguage(Object impl) {
                throw new UnsupportedOperationException();
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
            public String getMimeType(Object impl) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String findLanguage(String mimeType) {
                return null;
            }

            @Override
            public String getName(Object impl) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getPath(Object impl) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isInteractive(Object impl) {
                throw new UnsupportedOperationException();
            }

            @Override
            public URL getURL(Object impl) {
                throw new UnsupportedOperationException();
            }

            @Override
            public URI getURI(Object impl) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Reader getReader(Object impl) {
                throw new UnsupportedOperationException();
            }

            @Override
            public InputStream getInputStream(Object impl) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getLength(Object impl) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CharSequence getCharacters(Object impl) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CharSequence getCharacters(Object impl, int lineNumber) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getLineCount(Object impl) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getLineNumber(Object impl, int offset) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getColumnNumber(Object impl, int offset) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getLineStartOffset(Object impl, int lineNumber) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getLineLength(Object impl, int lineNumber) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String toString(Object impl) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int hashCode(Object impl) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean equals(Object impl, Object otherImpl) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isInternal(Object impl) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ByteSequence getBytes(Object impl) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean hasCharacters(Object impl) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean hasBytes(Object impl) {
                throw new UnsupportedOperationException();
            }

        }

        @Override
        public <S, T> Object newTargetTypeMapping(Class<S> sourceType, Class<T> targetType, Predicate<S> acceptsValue, Function<S, T> convertValue, TargetMappingPrecedence precedence) {
            return new Object();
        }

    }

}
