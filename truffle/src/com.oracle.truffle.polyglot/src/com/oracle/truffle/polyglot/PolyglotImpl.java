/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.polyglot.EngineAccessor.INSTRUMENT;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Handler;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.ResourceLimitEvent;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.io.MessageTransport;
import org.graalvm.polyglot.proxy.Proxy;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.impl.DispatchOutputStream;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.polyglot.io.FileSystem;

/*
 * This class is exported to the GraalVM SDK. Keep that in mind when changing its class or package name.
 */
/**
 * Internal service implementation of the polyglot API.
 */
public final class PolyglotImpl extends AbstractPolyglotImpl {

    static final Object[] EMPTY_ARGS = new Object[0];

    @SuppressWarnings("serial") private static final HostException STACKOVERFLOW_ERROR = new HostException(new StackOverflowError() {
        @SuppressWarnings("sync-override")
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    });

    private final PolyglotSource sourceImpl = new PolyglotSource(this);
    private final PolyglotSourceSection sourceSectionImpl = new PolyglotSourceSection(this);
    private final PolyglotManagement executionListenerImpl = new PolyglotManagement(this);
    private final AtomicReference<PolyglotEngineImpl> preInitializedEngineRef = new AtomicReference<>();

    final Map<Class<?>, PolyglotValue> primitiveValues = new HashMap<>();
    Value hostNull; // effectively final
    PolyglotValue disconnectedHostValue;

    static volatile PolyglotImpl polyglotImpl;

    /**
     * Internal method do not use.
     */
    public PolyglotImpl() {
        assert polyglotImpl == null : "only one instance allowed";
        polyglotImpl = this;
    }

    static PolyglotImpl getInstance() {
        return polyglotImpl;
    }

    @Override
    protected void initialize() {
        this.hostNull = getAPIAccess().newValue(HostObject.NULL, PolyglotValue.createHostNull(this));
        PolyglotValue.createDefaultValues(this, null, primitiveValues);
        disconnectedHostValue = new PolyglotValue.HostValue(this);
    }

    @Override
    public Context getLimitEventContext(Object impl) {
        return (Context) impl;
    }

    @Override
    public Object buildLimits(long statementLimit, Predicate<org.graalvm.polyglot.Source> statementLimitSourceFilter,
                    Duration timeLimit, Duration timeLimitAccuracy,
                    Consumer<ResourceLimitEvent> onLimit) {
        try {
            return new PolyglotLimits(statementLimit, statementLimitSourceFilter, timeLimit, timeLimitAccuracy, onLimit);
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(this, t);
        }
    }

    /**
     * Internal method do not use.
     */
    @Override
    public AbstractSourceImpl getSourceImpl() {
        return sourceImpl;
    }

    /**
     * Internal method do not use.
     */
    @Override
    public AbstractSourceSectionImpl getSourceSectionImpl() {
        return sourceSectionImpl;
    }

    /**
     * Internal method do not use.
     */
    @Override
    public AbstractManagementImpl getManagementImpl() {
        return executionListenerImpl;
    }

    /**
     * Internal method do not use.
     */
    @Override
    public Context getCurrentContext() {
        try {
            PolyglotContextImpl context = PolyglotContextImpl.currentNotEntered();
            if (context == null) {
                throw PolyglotEngineException.illegalState(
                                "No current context is available. Make sure the Java method is invoked by a Graal guest language or a context is entered using Context.enter().");
            }
            return context.currentApi;
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(this, t);
        }
    }

    /**
     * Internal method do not use.
     */
    @Override
    public Engine buildEngine(OutputStream out, OutputStream err, InputStream in, Map<String, String> options, long timeout, TimeUnit timeoutUnit, boolean sandbox,
                    long maximumAllowedAllocationBytes, boolean useSystemProperties, boolean allowExperimentalOptions, boolean boundEngine, MessageTransport messageInterceptor,
                    Object logHandlerOrStream,
                    HostAccess conf) {
        PolyglotEngineImpl impl = null;
        try {
            if (TruffleOptions.AOT) {
                EngineAccessor.ACCESSOR.initializeNativeImageTruffleLocator();
            }
            OutputStream resolvedOut = out == null ? System.out : out;
            OutputStream resolvedErr = err == null ? System.err : err;
            InputStream resolvedIn = in == null ? System.in : in;
            DispatchOutputStream dispatchOut = INSTRUMENT.createDispatchOutput(resolvedOut);
            DispatchOutputStream dispatchErr = INSTRUMENT.createDispatchOutput(resolvedErr);
            Handler logHandler = PolyglotLoggers.asHandler(logHandlerOrStream);
            logHandler = logHandler != null ? logHandler : PolyglotLoggers.createDefaultHandler(resolvedErr);
            ClassLoader contextClassLoader = TruffleOptions.AOT ? null : Thread.currentThread().getContextClassLoader();

            impl = boundEngine ? preInitializedEngineRef.getAndSet(null) : null;
            if (impl != null) {
                if (!impl.patch(dispatchOut, dispatchErr, resolvedIn, options, useSystemProperties, allowExperimentalOptions, contextClassLoader, boundEngine, logHandler)) {
                    impl.ensureClosed(false, true);
                    impl = null;
                }
            }
            if (impl == null) {
                impl = new PolyglotEngineImpl(this, dispatchOut, dispatchErr, resolvedIn, options, allowExperimentalOptions, useSystemProperties, contextClassLoader, boundEngine, messageInterceptor,
                                logHandler);
            }
            Engine engine = getAPIAccess().newEngine(impl);
            impl.creatorApi = engine;
            impl.currentApi = getAPIAccess().newEngine(impl);

            return engine;
        } catch (Throwable t) {
            if (impl == null) {
                throw PolyglotImpl.guestToHostException(this, t);
            } else {
                throw PolyglotImpl.guestToHostException(impl, t);
            }
        }
    }

    /**
     * Pre-initializes a polyglot engine instance.
     */
    @Override
    public void preInitializeEngine() {
        final Handler logHandler = PolyglotLoggers.createStreamHandler(System.err, false, true);
        try {
            final PolyglotEngineImpl preInitializedEngine = PolyglotEngineImpl.preInitialize(
                            this,
                            INSTRUMENT.createDispatchOutput(System.out),
                            INSTRUMENT.createDispatchOutput(System.err),
                            System.in,
                            TruffleOptions.AOT ? null : Thread.currentThread().getContextClassLoader(),
                            logHandler);
            preInitializedEngineRef.set(preInitializedEngine);
        } finally {
            logHandler.flush();
        }
    }

    /**
     * Cleans the pre-initialized polyglot engine instance.
     */
    @Override
    public void resetPreInitializedEngine() {
        preInitializedEngineRef.set(null);
        PolyglotEngineImpl.resetPreInitializedEngine();
    }

    /**
     * Internal method do not use.
     */
    @Override
    public Class<?> loadLanguageClass(String className) {
        for (Supplier<ClassLoader> supplier : EngineAccessor.locatorOrDefaultLoaders()) {
            ClassLoader loader = supplier.get();
            if (loader != null) {
                try {
                    Class<?> c = loader.loadClass(className);
                    if (!TruffleOptions.AOT) {
                        /*
                         * In JDK 9+, the Truffle API packages must be dynamically exported to a
                         * Truffle API client since the Truffle API module descriptor only exports
                         * these packages to modules known at build time (such as the Graal module).
                         */
                        EngineAccessor.JDKSERVICES.exportTo(loader, null);
                    }
                    return c;
                } catch (ClassNotFoundException e) {
                }
            }
        }
        return null;
    }

    @Override
    public Collection<Engine> findActiveEngines() {
        return PolyglotEngineImpl.findActiveEngines();
    }

    @Override
    public <S, T> Object newTargetTypeMapping(Class<S> sourceType, Class<T> targetType, Predicate<S> acceptsValue, Function<S, T> convertValue) {
        try {
            return new PolyglotTargetMapping(sourceType, targetType, acceptsValue, convertValue);
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(this, t);
        }
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
        PolyglotContextImpl valueContext = null;
        Object guestValue = null;
        if (hostValue == null) {
            return hostNull;
        } else if (isGuestPrimitive(hostValue)) {
            return getAPIAccess().newValue(hostValue, primitiveValues.get(hostValue.getClass()));
        } else if (HostWrapper.isInstance(hostValue)) {
            HostWrapper hostWrapper = HostWrapper.asInstance(hostValue);
            // host wrappers can nicely reuse the associated context
            guestValue = hostWrapper.getGuestObject();
            valueContext = hostWrapper.getContext();
            return valueContext.asValue(guestValue);
        } else {
            /*
             * We currently cannot support doing interop without a context so we create our own
             * value representations wit null for this case. No interop messages are used until they
             * are unboxed in PolyglotContextImpl#toGuestValue where a context will be attached.
             */
            if (hostValue instanceof TruffleObject) {
                guestValue = hostValue;
            } else if (hostValue instanceof Proxy) {
                guestValue = PolyglotProxy.toProxyGuestObject((Proxy) hostValue);
            } else if (hostValue instanceof Class) {
                guestValue = HostObject.forClass((Class<?>) hostValue, null);
            } else {
                guestValue = HostObject.forObject(hostValue, null);
            }
            return getAPIAccess().newValue(guestValue, disconnectedHostValue);
        }
    }

    @Override
    @TruffleBoundary
    public Value asValue(Object hostValue) {
        try {
            PolyglotContextImpl currentContext = PolyglotContextImpl.currentNotEntered();
            return asValue(currentContext, hostValue);
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(this, t);
        }
    }

    @Override
    public FileSystem newDefaultFileSystem() {
        return FileSystems.newDefaultFileSystem();
    }

    org.graalvm.polyglot.Source getPolyglotSource(Source source) {
        org.graalvm.polyglot.Source polyglotSource = EngineAccessor.SOURCE.getPolyglotSource(source);
        if (polyglotSource == null) {
            polyglotSource = getAPIAccess().newSource(source.getLanguage(), source);
            EngineAccessor.SOURCE.setPolyglotSource(source, polyglotSource);
        }
        return polyglotSource;
    }

    org.graalvm.polyglot.SourceSection getPolyglotSourceSection(SourceSection sourceSection) {
        if (sourceSection == null) {
            return null;
        }
        org.graalvm.polyglot.Source polyglotSource = getPolyglotSource(sourceSection.getSource());
        return getAPIAccess().newSourceSection(polyglotSource, sourceSection);
    }

    static <T extends Throwable> RuntimeException hostToGuestException(PolyglotLanguageContext context, T e) {
        return hostToGuestException(context.context, e);
    }

    /**
     * Performs necessary conversions for exceptions coming from the polyglot embedding API and
     * thrown to the language or engine. The conversion must happen exactly once per API call, that
     * is why this coercion should only be used in the catch block at the outermost API call.
     */
    @SuppressWarnings("deprecation")
    @TruffleBoundary
    static <T extends Throwable> RuntimeException hostToGuestException(PolyglotContextImpl context, T e) {
        assert !(e instanceof PolyglotEngineException) : "engine exceptions not expected here";
        assert !(e instanceof HostException) : "host exceptions not expected here";

        if (e instanceof ThreadDeath) {
            throw (ThreadDeath) e;
        } else if (e instanceof PolyglotException) {
            PolyglotException polyglot = (PolyglotException) e;
            if (context != null) {
                PolyglotExceptionImpl exceptionImpl = ((PolyglotExceptionImpl) context.getImpl().getAPIAccess().getImpl(polyglot));
                if (exceptionImpl.context == context || exceptionImpl.context == null || exceptionImpl.isHostException()) {
                    // for values of the same context the TruffleException is allowed to be unboxed
                    // for host exceptions no guest values are bound therefore it can also be
                    // unboxed
                    Throwable original = ((PolyglotExceptionImpl) context.getImpl().getAPIAccess().getImpl(polyglot)).exception;
                    if (original instanceof RuntimeException) {
                        throw (RuntimeException) original;
                    } else if (original instanceof Error) {
                        throw (Error) original;
                    }
                }
                // fall-through and treat it as any other host exception
            }
        }
        try {
            return new HostException(e);
        } catch (StackOverflowError stack) {
            /*
             * Cannot create a new host exception. Use a readily prepared instance.
             */
            return STACKOVERFLOW_ERROR;
        }
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
    static <T extends Throwable> PolyglotException guestToHostException(PolyglotLanguageContext languageContext, T e) {
        assert !(e instanceof PolyglotException) : "polyglot exceptions must not be thrown to the host";
        PolyglotEngineException.rethrow(e);

        if (languageContext == null) {
            throw new RuntimeException(e);
        }

        PolyglotContextImpl context = languageContext.context;
        PolyglotExceptionImpl exceptionImpl;
        if (context.closed || context.invalid) {
            exceptionImpl = new PolyglotExceptionImpl(context.engine, e);
        } else {
            try {
                Object prev = context.engine.enterIfNeeded(context);
                try {
                    exceptionImpl = new PolyglotExceptionImpl(languageContext, e);
                } finally {
                    context.engine.leaveIfNeeded(prev, context);
                }
            } catch (Throwable t) {
                /*
                 * It is possible that we fail to enter or produce a guest value using a context at
                 * this point, because the context might be closed or invalidated. This can happen
                 * as a race condition. We don't want to lock here, because this would be very prone
                 * to deadlocks. So if we fail to produce a guest value here we construct polyglot
                 * exception only using the engine, which does not require a context to be entered.
                 */
                e.addSuppressed(t);
                exceptionImpl = new PolyglotExceptionImpl(context.engine, e);
            }
        }
        APIAccess access = getInstance().getAPIAccess();
        return access.newLanguageException(exceptionImpl.getMessage(), exceptionImpl);
    }

    static <T extends Throwable> PolyglotException guestToHostException(PolyglotEngineImpl engine, T e) {
        assert !(e instanceof PolyglotException) : "polyglot exceptions must not be thrown to the host";
        PolyglotEngineException.rethrow(e);

        APIAccess access = engine.getAPIAccess();
        PolyglotExceptionImpl exceptionImpl = new PolyglotExceptionImpl(engine, e);
        return access.newLanguageException(exceptionImpl.getMessage(), exceptionImpl);
    }

    /**
     * Performs necessary conversions for exceptions coming from the engine or instrument and thrown
     * to the polyglot embedding API. The conversion must happen exactly once per API call, that is
     * why this coercion should only be used in the catch block at the outermost API call. Should
     * only be used when no engine is accessible.
     */
    @TruffleBoundary
    static <T extends Throwable> PolyglotException guestToHostException(PolyglotImpl polyglot, T e) {
        assert !(e instanceof PolyglotException) : "polyglot exceptions must not be thrown to the host";
        PolyglotEngineException.rethrow(e);

        APIAccess access = polyglot.getAPIAccess();
        PolyglotExceptionImpl exceptionImpl = new PolyglotExceptionImpl(polyglot, e);
        return access.newLanguageException(exceptionImpl.getMessage(), exceptionImpl);
    }

    static boolean isGuestPrimitive(Object receiver) {
        return receiver instanceof Integer || receiver instanceof Double //
                        || receiver instanceof Long || receiver instanceof Float //
                        || receiver instanceof Boolean || receiver instanceof Character //
                        || receiver instanceof Byte || receiver instanceof Short //
                        || receiver instanceof String;
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
