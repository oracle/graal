/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;
import static com.oracle.truffle.api.source.Source.CONTENT_NONE;
import static com.oracle.truffle.polyglot.EngineAccessor.INSTRUMENT;
import static com.oracle.truffle.polyglot.EngineAccessor.LANGUAGE;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess.TargetMappingPrecedence;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.ResourceLimitEvent;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.MessageTransport;
import org.graalvm.polyglot.io.ProcessHandler;
import org.graalvm.polyglot.proxy.Proxy;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.impl.DispatchOutputStream;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.polyglot.EngineAccessor.AbstractClassLoaderSupplier;
import com.oracle.truffle.polyglot.PolyglotEngineImpl.LogConfig;
import com.oracle.truffle.polyglot.PolyglotLoggers.EngineLoggerProvider;

/*
 * This class is exported to the GraalVM SDK. Keep that in mind when changing its class or package name.
 */
/**
 * Internal service implementation of the polyglot API.
 */
public final class PolyglotImpl extends AbstractPolyglotImpl {

    static final Object[] EMPTY_ARGS = new Object[0];
    private final PolyglotSourceDispatch sourceDispatch = new PolyglotSourceDispatch(this);
    private final PolyglotSourceSectionDispatch sourceSectionDispatch = new PolyglotSourceSectionDispatch(this);
    private final PolyglotExecutionListenerDispatch executionListenerDispatch = new PolyglotExecutionListenerDispatch(this);
    private final PolyglotExecutionEventDispatch executionEventDispatch = new PolyglotExecutionEventDispatch(this);
    final PolyglotEngineDispatch engineDispatch = new PolyglotEngineDispatch(this);
    final PolyglotContextDispatch contextDispatch = new PolyglotContextDispatch(this);
    private final PolyglotExceptionDispatch exceptionDispatch = new PolyglotExceptionDispatch(this);
    final PolyglotInstrumentDispatch instrumentDispatch = new PolyglotInstrumentDispatch(this);
    final PolyglotLanguageDispatch languageDispatch = new PolyglotLanguageDispatch(this);

    private final AtomicReference<PolyglotEngineImpl> preInitializedEngineRef = new AtomicReference<>();

    private final Map<Class<?>, PolyglotValueDispatch> primitiveValues = new HashMap<>();
    Value hostNull; // effectively final
    private PolyglotValueDispatch disconnectedHostValue;
    private PolyglotValueDispatch disconnectedBigIntegerHostValue;
    private volatile Object defaultFileSystemContext;

    private static volatile AbstractPolyglotImpl abstractImpl;

    /**
     * Internal method do not use.
     */
    public PolyglotImpl() {
    }

    @Override
    public int getPriority() {
        return 0; // default priority
    }

    private static AbstractPolyglotImpl getImpl() {
        AbstractPolyglotImpl local = abstractImpl;
        if (local == null) {
            try {
                Method f = Engine.class.getDeclaredMethod("getImpl");
                f.setAccessible(true);
                abstractImpl = local = (AbstractPolyglotImpl) f.invoke(null);
                assert local != null : "polyglot impl not found";
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
        return local;
    }

    static PolyglotImpl getInstance() {
        AbstractPolyglotImpl polyglot = getImpl();
        while (polyglot != null && !(polyglot instanceof PolyglotImpl)) {
            polyglot = polyglot.getNext();
        }
        if (polyglot == null) {
            throw new AssertionError(String.format("%s not found or installed but required.", PolyglotImpl.class.getSimpleName()));
        }
        return (PolyglotImpl) polyglot;
    }

    PolyglotEngineImpl getPreinitializedEngine() {
        return preInitializedEngineRef.get();
    }

    @Override
    protected void initialize() {
        this.hostNull = getAPIAccess().newValue(PolyglotValueDispatch.createHostNull(this), null, EngineAccessor.HOST.getHostNull());
        this.disconnectedHostValue = new PolyglotValueDispatch.HostValue(this);
        this.disconnectedBigIntegerHostValue = new PolyglotValueDispatch.BigIntegerHostValue(this);
        PolyglotValueDispatch.createDefaultValues(this, null, primitiveValues);
    }

    @Override
    public Object buildLimits(long statementLimit, Predicate<org.graalvm.polyglot.Source> statementLimitSourceFilter,
                    Consumer<ResourceLimitEvent> onLimit) {
        try {
            return new PolyglotLimits(statementLimit, statementLimitSourceFilter, onLimit);
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(this, t);
        }
    }

    /**
     * Internal method do not use.
     */
    AbstractSourceDispatch getSourceDispatch() {
        return sourceDispatch;
    }

    /**
     * Internal method do not use.
     */
    AbstractSourceSectionDispatch getSourceSectionDispatch() {
        return sourceSectionDispatch;
    }

    /**
     * Internal method do not use.
     */
    AbstractExecutionListenerDispatch getExecutionListenerDispatch() {
        return executionListenerDispatch;
    }

    /**
     * Internal method do not use.
     */
    AbstractExecutionEventDispatch getExecutionEventDispatch() {
        return executionEventDispatch;
    }

    /**
     * Internal method do not use.
     */
    @Override
    public Context getCurrentContext() {
        try {
            PolyglotContextImpl context = PolyglotFastThreadLocals.getContext(null);
            if (context == null) {
                throw PolyglotEngineException.illegalState(
                                "No current context is available. Make sure the Java method is invoked by a Graal guest language or a context is entered using Context.enter().");
            }
            Context api = context.api;
            if (api == null) {
                context.api = api = getAPIAccess().newContext(contextDispatch, context, context.engine.api);
            }
            return api;
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(this, t);
        }
    }

    /**
     * Internal method do not use.
     */
    @SuppressWarnings("unchecked")
    @Override
    public Engine buildEngine(String[] permittedLanguages, SandboxPolicy sandboxPolicy, OutputStream out, OutputStream err, InputStream in, Map<String, String> options,
                    boolean allowExperimentalOptions, boolean boundEngine, MessageTransport messageInterceptor, LogHandler logHandler, Object hostLanguage, boolean hostLanguageOnly,
                    boolean registerInActiveEngines, AbstractPolyglotHostService polyglotHostService) {
        PolyglotEngineImpl impl = null;
        try {
            validateSandbox(sandboxPolicy);
            if (TruffleOptions.AOT) {
                EngineAccessor.ACCESSOR.initializeNativeImageTruffleLocator();
            }
            OutputStream resolvedOut = out == null ? System.out : out;
            OutputStream resolvedErr = err == null ? System.err : err;
            InputStream resolvedIn = in == null ? System.in : in;
            DispatchOutputStream dispatchOut = INSTRUMENT.createDispatchOutput(resolvedOut);
            DispatchOutputStream dispatchErr = INSTRUMENT.createDispatchOutput(resolvedErr);

            LogConfig logConfig = new LogConfig();
            OptionValuesImpl engineOptions = createEngineOptions(options, logConfig, sandboxPolicy, allowExperimentalOptions);

            LogHandler useHandler = logHandler != null ? logHandler : PolyglotEngineImpl.createLogHandler(this, logConfig, dispatchErr, sandboxPolicy);
            EngineLoggerProvider loggerProvider = new PolyglotLoggers.EngineLoggerProvider(useHandler, logConfig.logLevels);

            AbstractPolyglotHostService usePolyglotHostService;
            if (polyglotHostService != null) {
                usePolyglotHostService = polyglotHostService;
            } else {
                usePolyglotHostService = new DefaultPolyglotHostService(this);
            }

            impl = (PolyglotEngineImpl) EngineAccessor.RUNTIME.tryLoadCachedEngine(engineOptions, loggerProvider);
            if (impl == null && boundEngine && !hostLanguageOnly && !EngineAccessor.RUNTIME.isStoreEnabled(engineOptions)) {
                impl = preInitializedEngineRef.getAndSet(null);
            }

            if (impl != null) {
                assert hostLanguage.getClass() == impl.getHostLanguageSPI().getClass() || PreInitContextHostLanguage.isInstance(impl.hostLanguage);
                impl.patch(sandboxPolicy, dispatchOut,
                                dispatchErr,
                                resolvedIn,
                                engineOptions,
                                logConfig,
                                loggerProvider,
                                options,
                                allowExperimentalOptions,
                                boundEngine,
                                useHandler,
                                (TruffleLanguage<?>) hostLanguage,
                                usePolyglotHostService);

            }
            if (impl == null) {
                impl = new PolyglotEngineImpl(this, sandboxPolicy,
                                permittedLanguages,
                                dispatchOut,
                                dispatchErr,
                                resolvedIn,
                                engineOptions,
                                logConfig.logLevels,
                                loggerProvider,
                                options,
                                allowExperimentalOptions,
                                boundEngine, false,
                                messageInterceptor,
                                useHandler,
                                (TruffleLanguage<Object>) hostLanguage,
                                hostLanguageOnly,
                                usePolyglotHostService);
            }
            return getAPIAccess().newEngine(engineDispatch, impl, registerInActiveEngines);
        } catch (Throwable t) {
            if (impl == null) {
                throw PolyglotImpl.guestToHostException(this, t);
            } else {
                throw PolyglotImpl.guestToHostException(impl, t);
            }
        }
    }

    private void validateSandbox(SandboxPolicy sandboxPolicy) {
        // When The PolyglotImpl is used as a root polyglot it supports at most the CONSTRAINED
        // sandboxing policy . When it's used as a delegate of other polyglot it needs to support
        // all sandboxing policies.
        if (this == getRootImpl() && sandboxPolicy.isStricterThan(SandboxPolicy.CONSTRAINED)) {
            throw PolyglotEngineException.illegalArgument(String.format(
                            "The Builder.sandbox(SandboxPolicy) is set to %s, but the GraalVM community edition supports only sandbox policy TRUSTED or CONSTRAINED." +
                                            "In order to resolve this switch to a less strict sandbox policy using Builder.sandbox(SandboxPolicy).",
                            sandboxPolicy));
        }
    }

    @Override
    protected OptionDescriptors createEngineOptionDescriptors() {
        return PolyglotEngineImpl.createEngineOptionDescriptors();
    }

    static OptionValuesImpl createEngineOptions(Map<String, String> options, LogConfig logOptions, SandboxPolicy sandboxPolicy, boolean allowExperimentalOptions) {
        OptionDescriptors engineOptionDescriptors = PolyglotImpl.getInstance().createAllEngineOptionDescriptors();
        Map<String, String> engineOptions = new HashMap<>();
        PolyglotEngineImpl.parseEngineOptions(options, engineOptions, logOptions);
        OptionValuesImpl values = new OptionValuesImpl(engineOptionDescriptors, sandboxPolicy, true, true);
        values.putAll(null, engineOptions, allowExperimentalOptions);
        return values;
    }

    /**
     * Pre-initializes a polyglot engine instance.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void preInitializeEngine() {
        PolyglotEngineImpl engine = createDefaultEngine(new PreInitContextHostLanguage());
        getAPIAccess().newEngine(engineDispatch, engine, false);
        try {
            engine.preInitialize();
        } finally {
            // Reset language homes from native-image compilation time, will be recomputed in
            // image execution time
            LanguageCache.resetNativeImageCacheLanguageHomes();
            // Clear logger settings
            engine.logLevels.clear();
            engine.logHandler.close();
            engine.logHandler = null;
        }
        preInitializedEngineRef.set(engine);
    }

    /*
     * Used for preinitialized contexts and fallback engine.
     */
    PolyglotEngineImpl createDefaultEngine(TruffleLanguage<Object> hostLanguage) {
        Map<String, String> options = getAPIAccess().readOptionsFromSystemProperties();
        LogConfig logConfig = new LogConfig();
        SandboxPolicy sandboxPolicy = SandboxPolicy.TRUSTED;
        OptionValuesImpl engineOptions = PolyglotImpl.createEngineOptions(options, logConfig, sandboxPolicy, true);
        DispatchOutputStream out = INSTRUMENT.createDispatchOutput(System.out);
        DispatchOutputStream err = INSTRUMENT.createDispatchOutput(System.err);
        LogHandler logHandler = PolyglotEngineImpl.createLogHandler(this, logConfig, err, sandboxPolicy);
        EngineLoggerProvider loggerProvider = new PolyglotLoggers.EngineLoggerProvider(logHandler, logConfig.logLevels);
        final PolyglotEngineImpl engine = new PolyglotEngineImpl(this, sandboxPolicy, new String[0], out, err, System.in, engineOptions, logConfig.logLevels, loggerProvider, options, true,
                        true, true, null, logHandler, hostLanguage, false, new DefaultPolyglotHostService(this));
        getAPIAccess().newEngine(engineDispatch, engine, false);
        return engine;
    }

    @SuppressWarnings("unchecked")
    @Override
    public TruffleLanguage<Object> createHostLanguage(AbstractHostAccess access) {
        return (TruffleLanguage<Object>) EngineAccessor.HOST.createDefaultHostLanguage(this, access);
    }

    /**
     * Cleans the pre-initialized polyglot engine instance.
     */
    @Override
    public void resetPreInitializedEngine() {
        preInitializedEngineRef.set(null);
    }

    /**
     * Internal method do not use.
     */
    @Override
    public Class<?> loadLanguageClass(String className) {
        for (AbstractClassLoaderSupplier supplier : EngineAccessor.locatorOrDefaultLoaders()) {
            ClassLoader loader = supplier.get();
            if (loader != null) {
                try {
                    Class<?> clazz = loader.loadClass(className);
                    if (supplier.accepts(clazz)) {
                        Module clazzModule = clazz.getModule();
                        ModuleUtils.exportTransitivelyTo(clazzModule);
                        return clazz;
                    }
                } catch (ClassNotFoundException e) {
                }
            }
        }
        return null;
    }

    @Override
    public <S, T> Object newTargetTypeMapping(Class<S> sourceType, Class<T> targetType, Predicate<S> acceptsValue, Function<S, T> convertValue, TargetMappingPrecedence precedence) {
        return EngineAccessor.HOST.newTargetTypeMapping(sourceType, targetType, acceptsValue, convertValue, precedence);
    }

    Value asValue(PolyglotContextImpl currentContext, Object hostValue) {
        if (currentContext != null) {
            // if we are currently entered in a context just use it and bind the value to it.
            return currentContext.asValue(hostValue);
        }
        /*
         * No entered context. Try to do something reasonable.
         */
        assert !(hostValue instanceof Value);
        Object guestValue = null;
        if (hostValue == null) {
            return hostNull;
        } else if (isGuestPrimitive(hostValue)) {
            return getAPIAccess().newValue(primitiveValues.get(hostValue.getClass()), null, hostValue);
        } else if (PolyglotWrapper.isInstance(hostValue)) {
            PolyglotWrapper hostWrapper = PolyglotWrapper.asInstance(hostValue);
            // host wrappers can nicely reuse the associated context
            PolyglotLanguageContext languageContext = hostWrapper.getLanguageContext();
            assert languageContext != null : "HostWrappers must be guaranteed to have non-null language context.";
            guestValue = hostWrapper.getGuestObject();
            return languageContext.asValue(guestValue);
        } else {
            /*
             * We currently cannot support doing interop without a context so we create our own
             * value representations wit null for this case. No interop messages are used until they
             * are unboxed in PolyglotContextImpl#toGuestValue where a context will be attached.
             */
            if (hostValue instanceof TruffleObject) {
                guestValue = hostValue;
            } else if (hostValue instanceof Proxy) {
                guestValue = EngineAccessor.HOST.toDisconnectedHostProxy((Proxy) hostValue);
            } else {
                guestValue = EngineAccessor.HOST.toDisconnectedHostObject(hostValue);
            }
            return getAPIAccess().newValue(hostValue instanceof BigInteger ? disconnectedBigIntegerHostValue : disconnectedHostValue, null, guestValue);
        }
    }

    @Override
    @TruffleBoundary
    public Value asValue(Object hostValue) {
        try {
            PolyglotContextImpl currentContext = PolyglotFastThreadLocals.getContext(null);
            return asValue(currentContext, hostValue);
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(this, t);
        }
    }

    @Override
    public FileSystem newDefaultFileSystem(String hostTmpDir) {
        return FileSystems.newDefaultFileSystem(hostTmpDir);
    }

    @Override
    public FileSystem allowLanguageHomeAccess(FileSystem fileSystem) {
        return FileSystems.allowLanguageHomeAccess(fileSystem);
    }

    @Override
    public FileSystem newReadOnlyFileSystem(FileSystem fileSystem) {
        return FileSystems.newReadOnlyFileSystem(fileSystem);
    }

    @Override
    public FileSystem newNIOFileSystem(java.nio.file.FileSystem fileSystem) {
        return FileSystems.newNIOFileSystem(fileSystem);
    }

    @Override
    public ProcessHandler newDefaultProcessHandler() {
        if (PolyglotEngineImpl.ALLOW_CREATE_PROCESS) {
            return ProcessHandlers.newDefaultProcessHandler();
        } else {
            return null;
        }
    }

    @Override
    public boolean isDefaultProcessHandler(ProcessHandler processHandler) {
        return ProcessHandlers.isDefault(processHandler);
    }

    @Override
    public boolean isInternalFileSystem(FileSystem fileSystem) {
        return FileSystems.isInternal(getRootImpl(), fileSystem);
    }

    @Override
    public boolean isHostFileSystem(FileSystem fileSystem) {
        return FileSystems.isHostFileSystem(fileSystem);
    }

    @Override
    public ThreadScope createThreadScope() {
        return null;
    }

    @Override
    public LogHandler newLogHandler(Object logHandlerOrStream) {
        return PolyglotLoggers.asLogHandler(this, logHandlerOrStream);
    }

    @Override
    public OptionDescriptors createUnionOptionDescriptors(OptionDescriptors... optionDescriptors) {
        return LANGUAGE.createOptionDescriptorsUnion(optionDescriptors);
    }

    @Override
    public AbstractHostAccess createHostAccess() {
        return new PolyglotHostAccess(this);
    }

    @Override
    public boolean copyResources(Path targetFolder, String... components) throws IOException {
        return InternalResourceCache.copyResourcesForNativeImage(targetFolder, components);
    }

    @Override
    public String findLanguage(File file) throws IOException {
        Objects.requireNonNull(file);
        String mimeType = findMimeType(file);
        if (mimeType != null) {
            return findLanguage(mimeType);
        } else {
            return null;
        }
    }

    @Override
    public String findLanguage(URL url) throws IOException {
        String mimeType = findMimeType(url);
        if (mimeType != null) {
            return findLanguage(mimeType);
        } else {
            return null;
        }
    }

    @Override
    public String findMimeType(File file) throws IOException {
        Objects.requireNonNull(file);
        TruffleFile truffleFile;
        try {
            truffleFile = EngineAccessor.LANGUAGE.getTruffleFile(file.toPath().toString(), getDefaultFileSystemContext());
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
            throw new AssertionError("Inconsistent path", e);
        }
        return truffleFile.detectMimeType();
    }

    @Override
    public String findMimeType(URL url) throws IOException {
        Objects.requireNonNull(url);
        return EngineAccessor.SOURCE.findMimeType(url, getDefaultFileSystemContext());
    }

    @Override
    public String findLanguage(String mimeType) {
        Objects.requireNonNull(mimeType);
        LanguageCache cache = LanguageCache.languageMimes().get(mimeType);
        if (cache != null) {
            return cache.getId();
        }
        return null;
    }

    @Override
    public org.graalvm.polyglot.Source build(String language, Object origin, URI uri, String name, String mimeType, Object content, boolean interactive, boolean internal, boolean cached,
                    Charset encoding, URL url, String path)
                    throws IOException {
        assert language != null;
        com.oracle.truffle.api.source.Source.SourceBuilder builder;
        if (origin instanceof File) {
            builder = EngineAccessor.SOURCE.newBuilder(language, (File) origin);
        } else if (origin instanceof CharSequence) {
            builder = com.oracle.truffle.api.source.Source.newBuilder(language, ((CharSequence) origin), name);
        } else if (origin instanceof ByteSequence) {
            builder = com.oracle.truffle.api.source.Source.newBuilder(language, ((ByteSequence) origin), name);
        } else if (origin instanceof Reader) {
            builder = com.oracle.truffle.api.source.Source.newBuilder(language, (Reader) origin, name);
        } else if (origin instanceof URL) {
            builder = com.oracle.truffle.api.source.Source.newBuilder(language, (URL) origin);
        } else if (origin == CONTENT_NONE) {
            builder = com.oracle.truffle.api.source.Source.newBuilder(language, "", name).content(CONTENT_NONE);
        } else {
            throw shouldNotReachHere();
        }

        if (origin instanceof File || origin instanceof URL) {
            EngineAccessor.SOURCE.setFileSystemContext(builder, getDefaultFileSystemContext());
        }

        EngineAccessor.SOURCE.setEmbedderSource(builder, true);
        if (url != null) {
            EngineAccessor.SOURCE.setURL(builder, url);
        }
        if (path != null) {
            EngineAccessor.SOURCE.setPath(builder, path);
        }

        if (content instanceof CharSequence) {
            builder.content((CharSequence) content);
        } else if (content instanceof ByteSequence) {
            builder.content((ByteSequence) content);
        }

        builder.uri(uri);
        builder.name(name);
        builder.internal(internal);
        builder.interactive(interactive);
        builder.mimeType(mimeType);
        builder.cached(cached);
        builder.encoding(encoding);

        try {
            return PolyglotImpl.getOrCreatePolyglotSource(this, builder.build());
        } catch (IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw shouldNotReachHere(e);
        }
    }

    private Object getDefaultFileSystemContext() {
        Object res = defaultFileSystemContext;
        if (res == null) {
            synchronized (this) {
                res = defaultFileSystemContext;
                if (res == null) {
                    EmbedderFileSystemContext context = new EmbedderFileSystemContext(this);
                    res = EngineAccessor.LANGUAGE.createFileSystemContext(context, context.fileSystem);
                    defaultFileSystemContext = res;
                }
            }
        }
        return res;
    }

    static final class EmbedderFileSystemContext {

        private final PolyglotImpl impl;
        final FileSystem fileSystem;

        final Map<String, LanguageCache> cachedLanguages = LanguageCache.languages();
        final Supplier<Map<String, Collection<? extends TruffleFile.FileTypeDetector>>> fileTypeDetectors = FileSystems.newFileTypeDetectorsSupplier(cachedLanguages.values());

        EmbedderFileSystemContext(PolyglotImpl impl) {
            this.impl = Objects.requireNonNull(impl);
            this.fileSystem = FileSystems.newDefaultFileSystem(null);
        }

        PolyglotImpl getImpl() {
            return impl;
        }

    }

    static org.graalvm.polyglot.Source getOrCreatePolyglotSource(PolyglotImpl polyglot, Source source) {
        return EngineAccessor.SOURCE.getOrCreatePolyglotSource(source, (t) -> polyglot.getAPIAccess().newSource(polyglot.sourceDispatch, t));
    }

    static org.graalvm.polyglot.SourceSection getPolyglotSourceSection(PolyglotImpl polyglot, com.oracle.truffle.api.source.SourceSection sourceSection) {
        if (sourceSection == null) {
            return null;
        }
        org.graalvm.polyglot.Source polyglotSource = getOrCreatePolyglotSource(polyglot, sourceSection.getSource());
        return polyglot.getAPIAccess().newSourceSection(polyglotSource, polyglot.sourceSectionDispatch, sourceSection);
    }

    /**
     * Performs necessary conversions for exceptions coming from the engine and thrown to the
     * instrument API. The conversion must happen exactly once per API call, that is why this
     * coercion should only be used in the catch block at the outermost API call.
     */
    @SuppressWarnings("unchecked")
    @TruffleBoundary
    static <T extends Throwable> RuntimeException engineToLanguageException(Throwable t) throws T {
        assert !(t instanceof PolyglotException) : "polyglot exceptions must not be thrown to the guest language";
        PolyglotEngineException.rethrow(t);
        throw (T) t;
    }

    /**
     * Performs necessary conversions for exceptions coming from the engine and thrown to the
     * language API. The conversion must happen exactly once per API call, that is why this coercion
     * should only be used in the catch block at the outermost instrumentation API call.
     */
    @SuppressWarnings("unchecked")
    @TruffleBoundary
    static <T extends Throwable> RuntimeException engineToInstrumentException(Throwable t) throws T {
        assert !(t instanceof PolyglotException) : "polyglot exceptions must not be thrown to the guest instrument";
        PolyglotEngineException.rethrow(t);
        throw (T) t;
    }

    /**
     * Performs necessary conversions for exceptions coming from the engine or language and thrown
     * to the polyglot embedding API. The conversion must happen exactly once per API call, that is
     * why this coercion should only be used in the catch block at the outermost API call.
     */
    @TruffleBoundary
    static <T extends Throwable> PolyglotException guestToHostException(PolyglotLanguageContext languageContext, T e, boolean entered) {
        assert !(e instanceof PolyglotException) : "polyglot exceptions must not be thrown to the host: " + e;
        PolyglotEngineException.rethrow(e);

        if (languageContext == null) {
            throw new RuntimeException(e);
        }

        PolyglotContextImpl context = languageContext.context;
        PolyglotExceptionImpl exceptionImpl;
        PolyglotExceptionImpl suppressedImpl = null;
        PolyglotContextImpl.State localContextState = context.state;
        if (localContextState.isInvalidOrClosed()) {
            exceptionImpl = new PolyglotExceptionImpl(context.engine.impl, context.engine, localContextState, context.invalidResourceLimit, context.exitCode, languageContext, e, false, false);
        } else {
            try {
                exceptionImpl = new PolyglotExceptionImpl(languageContext.getImpl(), languageContext.context.engine, localContextState, false, 0,
                                languageContext, e, true, entered);
            } catch (Throwable t) {
                /*
                 * It is possible that we fail to produce a guest value or interop message failed.
                 * We report the original exception without using interop messages. We also convert
                 * the exception thrown from the PolyglotExceptionImpl constructor to a new
                 * PolyglotException and add it to resulting exception suppressed exceptions.
                 */
                exceptionImpl = new PolyglotExceptionImpl(context.engine, localContextState, false, 0, e);
                suppressedImpl = new PolyglotExceptionImpl(context.engine, localContextState, false, 0, t);
            }
        }
        APIAccess access = getInstance().getAPIAccess();
        PolyglotException polyglotException = access.newLanguageException(exceptionImpl.getMessage(), getInstance().exceptionDispatch, exceptionImpl);
        if (suppressedImpl != null) {
            polyglotException.addSuppressed(access.newLanguageException(exceptionImpl.getMessage(), getInstance().exceptionDispatch, suppressedImpl));
        }
        return polyglotException;
    }

    static <T extends Throwable> PolyglotException guestToHostException(PolyglotEngineImpl engine, T e) {
        assert !(e instanceof PolyglotException) : "polyglot exceptions must not be thrown to the host: " + e;
        PolyglotEngineException.rethrow(e);

        APIAccess access = engine.getAPIAccess();
        PolyglotExceptionImpl exceptionImpl = new PolyglotExceptionImpl(engine, null, false, 0, e);
        return access.newLanguageException(exceptionImpl.getMessage(), getInstance().exceptionDispatch, exceptionImpl);
    }

    /**
     * Performs necessary conversions for exceptions coming from the engine or instrument and thrown
     * to the polyglot embedding API. The conversion must happen exactly once per API call, that is
     * why this coercion should only be used in the catch block at the outermost API call. Should
     * only be used when no engine is accessible.
     */
    @TruffleBoundary
    static <T extends Throwable> PolyglotException guestToHostException(PolyglotImpl polyglot, T e) {
        assert !(e instanceof PolyglotException) : "polyglot exceptions must not be thrown to the host: " + e;
        PolyglotEngineException.rethrow(e);

        APIAccess access = polyglot.getAPIAccess();
        PolyglotExceptionImpl exceptionImpl = new PolyglotExceptionImpl(polyglot, e);
        return access.newLanguageException(exceptionImpl.getMessage(), getInstance().exceptionDispatch, exceptionImpl);
    }

    static RuntimeException hostToGuestException(PolyglotEngineImpl engine, Throwable t) {
        return engine.polyglotHostService.hostToGuestException(engine.host, t);
    }

    static IllegalArgumentException sandboxPolicyException(SandboxPolicy sandboxPolicy, String reason, String fix) {
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
        return new IllegalArgumentException(message);
    }

    static boolean isGuestPrimitive(Object receiver) {
        return receiver instanceof Integer || receiver instanceof Double //
                        || receiver instanceof Long || receiver instanceof Float //
                        || receiver instanceof Boolean || receiver instanceof Character //
                        || receiver instanceof Byte || receiver instanceof Short //
                        || receiver instanceof String || receiver instanceof TruffleString;
    }

    interface VMObject {

        PolyglotEngineImpl getEngine();

        default PolyglotImpl getImpl() {
            return getEngine().impl;
        }

        default APIAccess getAPIAccess() {
            return getEngine().impl.getAPIAccess();
        }

    }
}
