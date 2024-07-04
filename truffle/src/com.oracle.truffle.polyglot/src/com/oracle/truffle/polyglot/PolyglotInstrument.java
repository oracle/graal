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
package com.oracle.truffle.polyglot;

import static com.oracle.truffle.polyglot.EngineAccessor.INSTRUMENT;
import static com.oracle.truffle.polyglot.EngineAccessor.LANGUAGE;

import java.util.function.Supplier;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;

import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.polyglot.PolyglotLocals.LocalLocation;

/** The data corresponding to a given {@link TruffleInstrument}. */
class PolyglotInstrument implements com.oracle.truffle.polyglot.PolyglotImpl.VMObject {

    Object api;
    InstrumentInfo info;
    final InstrumentCache cache;
    final PolyglotEngineImpl engine;

    private final Object instrumentLock = new Object();
    private volatile OptionDescriptors engineOptions;
    private volatile OptionDescriptors contextOptions;
    private volatile OptionDescriptors allOptions;
    private volatile OptionValuesImpl optionValues;
    private volatile boolean initialized;
    private volatile boolean created;
    private volatile boolean readyForContextEvents;
    private volatile boolean finalized;
    private volatile boolean closed;
    int requestedAsyncStackDepth = 0;
    LocalLocation[] contextLocalLocations;
    LocalLocation[] contextThreadLocalLocations;

    PolyglotInstrument(PolyglotEngineImpl engine, InstrumentCache cache) {
        this.engine = engine;
        this.cache = cache;
    }

    public OptionDescriptors getOptions() {
        try {
            engine.checkState();
            return getAllOptionsInternal();
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(engine, t);
        }
    }

    OptionDescriptors getAllOptionsInternal() {
        ensureInitialized();
        return allOptions;
    }

    OptionDescriptors getEngineOptionsInternal() {
        ensureInitialized();
        return engineOptions;
    }

    OptionDescriptors getContextOptionsInternal() {
        ensureInitialized();
        return contextOptions;
    }

    OptionValuesImpl getEngineOptionValues() {
        if (optionValues == null) {
            synchronized (instrumentLock) {
                if (optionValues == null) {
                    optionValues = new OptionValuesImpl(getAllOptionsInternal(), engine.sandboxPolicy, false, false);
                }
            }
        }
        return optionValues;
    }

    OptionValuesImpl getOptionValuesIfExists() {
        return optionValues;
    }

    @Override
    public PolyglotEngineImpl getEngine() {
        return engine;
    }

    @Override
    public APIAccess getAPIAccess() {
        return engine.apiAccess;
    }

    @Override
    public PolyglotImpl getImpl() {
        return engine.impl;
    }

    private void ensureInitialized() {
        if (!initialized) {
            synchronized (instrumentLock) {
                if (!initialized) {
                    try {
                        INSTRUMENT.initializeInstrument(engine.instrumentationHandler, this, cache.getClassName(), new Supplier<TruffleInstrument>() {
                            @Override
                            public TruffleInstrument get() {
                                return cache.loadInstrument();
                            }
                        });
                        OptionDescriptors eOptions = INSTRUMENT.describeEngineOptions(engine.instrumentationHandler, this, cache.getId());
                        OptionDescriptors cOptions = INSTRUMENT.describeContextOptions(engine.instrumentationHandler, this, cache.getId());
                        assert verifyNoOverlap(eOptions, cOptions);
                        this.engineOptions = eOptions;
                        this.contextOptions = cOptions;
                        this.allOptions = LANGUAGE.createOptionDescriptorsUnion(eOptions, cOptions);
                    } catch (Exception e) {
                        throw new IllegalStateException(String.format("Error initializing instrument '%s' using class '%s'. Message: %s.", cache.getId(), cache.getClassName(), e.getMessage()), e);
                    }
                    assert contextLocalLocations != null : "context local locations not initialized";
                    initialized = true;
                }
            }
        }
    }

    private static boolean verifyNoOverlap(OptionDescriptors engineOptions, OptionDescriptors contextOptions) {
        for (OptionDescriptor engineDescriptor : engineOptions) {
            if (contextOptions.get(engineDescriptor.getName()) != null) {
                throw new AssertionError("Overlapping descriptor name " + engineDescriptor.getName() + " between context and engine options detected.");
            }
        }
        return true;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isCreated() {
        return created;
    }

    public boolean isReadyForContextEvents() {
        return readyForContextEvents;
    }

    void ensureCreated() {
        if (!created) {
            validateSandbox(engine.sandboxPolicy);
            PolyglotContextImpl[] contexts = null;
            synchronized (instrumentLock) {
                if (!created) {
                    if (!initialized) {
                        ensureInitialized();
                    }
                    if (contextLocalLocations.length > 0) {
                        // trigger initialization of locals under context lock.
                        synchronized (engine.lock) {
                            contexts = engine.collectAliveContexts().toArray(new PolyglotContextImpl[0]);
                        }
                    }
                    INSTRUMENT.createInstrument(engine.instrumentationHandler, this, cache.services(), getEngineOptionValues());
                    created = true;
                }
            }
            if (contexts != null) {
                for (PolyglotContextImpl context : contexts) {
                    context.invokeLocalsFactories(contextLocalLocations, contextThreadLocalLocations);
                }
            }
            /*
             * This instrument might have installed various listeners that access context locals
             * defined by this instrument. Therefore, the listener events must not be invoked before
             * the context locals for all contexts are initialized.
             */
            readyForContextEvents = true;
        }
    }

    void ensureFinalized() {
        if (created && !finalized && !closed) {
            synchronized (instrumentLock) {
                if (created && !finalized && !closed) {
                    INSTRUMENT.finalizeInstrument(engine.instrumentationHandler, this);
                    finalized = true;
                }
            }
        }
    }

    void ensureClosed() {
        if (created && !closed) {
            synchronized (instrumentLock) {
                if (created && !closed) {
                    INSTRUMENT.disposeInstrument(engine.instrumentationHandler, this, false);
                }
                closed = true;
                engineOptions = null;
                optionValues = null;
            }
        }
    }

    public <T> T lookup(Class<T> serviceClass) {
        try {
            engine.checkState();
            return lookupInternal(serviceClass);
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(engine, t);
        }
    }

    <T> T lookupInternal(Class<T> serviceClass) {
        if (cache.supportsService(serviceClass)) {
            ensureCreated();
            return INSTRUMENT.getInstrumentationHandlerService(engine.instrumentationHandler, this, serviceClass);
        } else {
            return null;
        }
    }

    public String getId() {
        return cache.getId();
    }

    public String getName() {
        return cache.getName();
    }

    public String getVersion() {
        final String version = cache.getVersion();
        if (version.equals("inherit")) {
            return engine.getVersion();
        } else {
            return version;
        }
    }

    public String getWebsite() {
        return PolyglotLanguage.websiteSubstitutions(cache.getWebsite());
    }

    private void validateSandbox(SandboxPolicy sandboxPolicy) {
        SandboxPolicy instrumentSandboxPolicy = cache.getSandboxPolicy();
        if (sandboxPolicy.isStricterThan(instrumentSandboxPolicy)) {
            throw PolyglotEngineException.illegalArgument(PolyglotImpl.sandboxPolicyException(sandboxPolicy,
                            String.format("The instrument %s can only be used up to the %s sandbox policy.", getId(), instrumentSandboxPolicy),
                            String.format("do not enable %s instrument by removing any of the instrument's options from Builder.option(String,String)", getId())));
        }
    }
}
