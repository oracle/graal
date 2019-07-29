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

import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractInstrumentImpl;

import com.oracle.truffle.api.InstrumentInfo;

class PolyglotInstrument extends AbstractInstrumentImpl implements com.oracle.truffle.polyglot.PolyglotImpl.VMObject {

    Instrument api;
    InstrumentInfo info;
    final InstrumentCache cache;
    final PolyglotEngineImpl engine;
    private final Object instrumentLock = new Object();

    private volatile OptionDescriptors options;
    private volatile OptionValuesImpl optionValues;
    private volatile boolean initialized;
    private volatile boolean created;

    PolyglotInstrument(PolyglotEngineImpl engine, InstrumentCache cache) {
        super(engine.impl);
        this.engine = engine;
        this.cache = cache;
    }

    @Override
    public OptionDescriptors getOptions() {
        engine.checkState();
        ensureInitialized();
        return options;
    }

    OptionValuesImpl getOptionValues() {
        if (optionValues == null) {
            synchronized (instrumentLock) {
                if (optionValues == null) {
                    optionValues = new OptionValuesImpl(engine, getOptions());
                }
            }
        }
        return optionValues;
    }

    @Override
    public PolyglotEngineImpl getEngine() {
        return engine;
    }

    void ensureInitialized() {
        if (!initialized) {
            synchronized (instrumentLock) {
                if (!initialized) {
                    try {
                        Class<?> loadedInstrument = cache.getInstrumentationClass();
                        INSTRUMENT.initializeInstrument(engine.instrumentationHandler, this, loadedInstrument);
                        this.options = INSTRUMENT.describeOptions(engine.instrumentationHandler, this, cache.getId());
                    } catch (Exception e) {
                        throw new IllegalStateException(String.format("Error initializing instrument '%s' using class '%s'.", cache.getId(), cache.getClassName()), e);
                    }
                    initialized = true;
                }
            }
        }
    }

    void ensureCreated() {
        if (!created) {
            synchronized (instrumentLock) {
                if (!created) {
                    if (!initialized) {
                        ensureInitialized();
                    }
                    INSTRUMENT.createInstrument(engine.instrumentationHandler, this, cache.services(), getOptionValues());
                    created = true;
                }
            }
        }
    }

    void notifyClosing() {
        if (created) {
            synchronized (instrumentLock) {
                if (created) {
                    INSTRUMENT.finalizeInstrument(engine.instrumentationHandler, this);
                }
            }
        }
    }

    void ensureClosed() {
        assert Thread.holdsLock(engine);
        if (created) {
            synchronized (instrumentLock) {
                if (created) {
                    INSTRUMENT.disposeInstrument(engine.instrumentationHandler, this, false);
                }
                created = false;
                initialized = false;
                options = null;
                optionValues = null;
            }
        }
    }

    @Override
    public <T> T lookup(Class<T> serviceClass) {
        return lookup(serviceClass, true);
    }

    <T> T lookup(Class<T> serviceClass, boolean wrapExceptions) {
        engine.checkState();
        if (cache.supportsService(serviceClass)) {
            try {
                ensureCreated();
            } catch (Throwable t) {
                if (wrapExceptions) {
                    throw PolyglotImpl.wrapGuestException(engine, t);
                } else {
                    throw t;
                }
            }
            return INSTRUMENT.getInstrumentationHandlerService(engine.instrumentationHandler, this, serviceClass);
        } else {
            return null;
        }
    }

    @Override
    public String getId() {
        return cache.getId();
    }

    @Override
    public String getName() {
        return cache.getName();
    }

    @Override
    public String getVersion() {
        final String version = cache.getVersion();
        if (version.equals("inherit")) {
            return engine.getVersion();
        } else {
            return version;
        }
    }

}
