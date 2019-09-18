/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Handler;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.io.MessageTransport;
import org.graalvm.polyglot.proxy.Proxy;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.impl.DispatchOutputStream;
import com.oracle.truffle.api.impl.HomeFinder;
import com.oracle.truffle.api.impl.TruffleLocator;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

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
    private final PolyglotExecutionListener executionListenerImpl = new PolyglotExecutionListener(this);
    private final AtomicReference<PolyglotEngineImpl> preInitializedEngineRef = new AtomicReference<>();

    final Map<Class<?>, PolyglotValue> primitiveValues = new HashMap<>();
    Value hostNull; // effectively final
    PolyglotValue disconnectedHostValue;

    /**
     * Internal method do not use.
     */
    public PolyglotImpl() {
    }

    @Override
    protected void initialize() {
        this.hostNull = getAPIAccess().newValue(HostObject.NULL, PolyglotValue.createHostNull(this));
        PolyglotValue.createDefaultValues(this, null, primitiveValues);
        disconnectedHostValue = new PolyglotValue.HostValue(this);
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
    public AbstractExecutionListenerImpl getExecutionListenerImpl() {
        return executionListenerImpl;
    }

    /**
     * Internal method do not use.
     */
    @Override
    public Context getCurrentContext() {
        PolyglotContextImpl context = PolyglotContextImpl.current();
        if (context == null) {
            return super.getCurrentContext();
        }
        return context.currentApi;
    }

    /**
     * Internal method do not use.
     */
    @Override
    public Engine buildEngine(OutputStream out, OutputStream err, InputStream in, Map<String, String> options, long timeout, TimeUnit timeoutUnit, boolean sandbox,
                    long maximumAllowedAllocationBytes, boolean useSystemProperties, boolean allowExperimentalOptions, boolean boundEngine, MessageTransport messageInterceptor,
                    Object logHandlerOrStream,
                    HostAccess conf) {
        if (TruffleOptions.AOT) {
            EngineAccessor.ACCESSOR.initializeNativeImageTruffleLocator();
        }
        OutputStream resolvedOut = out == null ? System.out : out;
        OutputStream resolvedErr = err == null ? System.err : err;
        InputStream resolvedIn = in == null ? System.in : in;
        DispatchOutputStream dispatchOut = INSTRUMENT.createDispatchOutput(resolvedOut);
        DispatchOutputStream dispatchErr = INSTRUMENT.createDispatchOutput(resolvedErr);
        Handler logHandler = PolyglotLogHandler.asHandler(logHandlerOrStream);
        logHandler = logHandler != null ? logHandler : PolyglotLogHandler.createStreamHandler(resolvedErr, false, true);
        ClassLoader contextClassLoader = TruffleOptions.AOT ? null : Thread.currentThread().getContextClassLoader();

        PolyglotEngineImpl impl = boundEngine ? preInitializedEngineRef.getAndSet(null) : null;
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
    }

    /**
     * Pre-initializes a polyglot engine instance.
     */
    @Override
    public void preInitializeEngine() {
        final Handler logHandler = PolyglotLogHandler.createStreamHandler(System.err, false, true);
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
        for (ClassLoader loader : TruffleLocator.loaders()) {
            try {
                return loader.loadClass(className);
            } catch (ClassNotFoundException e) {
            }
        }
        return null;
    }

    @Override
    public Collection<Engine> findActiveEngines() {
        return PolyglotEngineImpl.findActiveEngines();
    }

    @Override
    public Path findHome() {
        final HomeFinder homeFinder = HomeFinder.getInstance();
        return homeFinder == null ? null : homeFinder.getHomeFolder();
    }

    @Override
    public <S, T> Object newTargetTypeMapping(Class<S> sourceType, Class<T> targetType, Predicate<S> acceptsValue, Function<S, T> convertValue) {
        return new PolyglotTargetMapping(sourceType, targetType, acceptsValue, convertValue);
    }

    @Override
    @TruffleBoundary
    public Value asValue(Object hostValue) {
        PolyglotContextImpl currentContext = PolyglotContextImpl.current();
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
                guestValue = PolyglotProxy.toProxyGuestObject(null, (Proxy) hostValue);
            } else if (hostValue instanceof Class) {
                guestValue = HostObject.forClass((Class<?>) hostValue, null);
            } else {
                guestValue = HostObject.forObject(hostValue, null);
            }
            return getAPIAccess().newValue(guestValue, disconnectedHostValue);
        }
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

    static RuntimeException engineError(RuntimeException e) {
        throw new EngineException(e);
    }

    static <T extends Throwable> RuntimeException wrapHostException(PolyglotLanguageContext context, T e) {
        return wrapHostException(context.context, e);
    }

    @SuppressWarnings("deprecation")
    @TruffleBoundary
    static <T extends Throwable> RuntimeException wrapHostException(PolyglotContextImpl context, T e) {
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
        } else if (e instanceof EngineException) {
            return ((EngineException) e).e;
        } else if (e instanceof HostException) {
            return (HostException) e;
        } else if (e instanceof InteropException) {
            throw ((InteropException) e).raise();
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

    @TruffleBoundary
    // Wrapping language exception
    static <T extends Throwable> PolyglotException wrapGuestException(PolyglotLanguageContext context, T e) {
        if (e instanceof PolyglotException) {
            return (PolyglotException) e;
        } else {
            doRethrowPolyglotVariants(e);
        }

        APIAccess access = context.getEngine().impl.getAPIAccess();
        PolyglotExceptionImpl exceptionImpl = new PolyglotExceptionImpl(context, e);
        return access.newLanguageException(exceptionImpl.getMessage(), exceptionImpl);
    }

    @TruffleBoundary
    // Wrapping instrument exception
    static <T extends Throwable> PolyglotException wrapGuestException(PolyglotEngineImpl engine, T e) {
        if (e instanceof PolyglotException) {
            return (PolyglotException) e;
        } else {
            doRethrowPolyglotVariants(e);
        }

        APIAccess access = engine.impl.getAPIAccess();
        PolyglotExceptionImpl exceptionImpl = new PolyglotExceptionImpl(engine, e);
        return access.newLanguageException(exceptionImpl.getMessage(), exceptionImpl);
    }

    private static void doRethrowPolyglotVariants(Throwable e) {
        if (e instanceof EngineException) {
            throw ((EngineException) e).e;
        } else if (e instanceof PolyglotUnsupportedException) {
            throw (PolyglotUnsupportedException) e;
        } else if (e instanceof PolyglotClassCastException) {
            throw (PolyglotClassCastException) e;
        } else if (e instanceof PolyglotIllegalStateException) {
            throw (PolyglotIllegalStateException) e;
        } else if (e instanceof PolyglotNullPointerException) {
            throw (PolyglotNullPointerException) e;
        } else if (e instanceof PolyglotIllegalArgumentException) {
            throw (PolyglotIllegalArgumentException) e;
        } else if (e instanceof PolyglotArrayIndexOutOfBoundsException) {
            throw (PolyglotArrayIndexOutOfBoundsException) e;
        }
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

    @SuppressWarnings("serial")
    private static class EngineException extends RuntimeException {

        final RuntimeException e;

        EngineException(RuntimeException e) {
            this.e = e;
        }

        @SuppressWarnings("sync-override")
        @Override
        public final Throwable fillInStackTrace() {
            return this;
        }

    }
}
