/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.vm;

import static com.oracle.truffle.api.vm.PolyglotImpl.checkEngine;
import static com.oracle.truffle.api.vm.VMAccessor.INSTRUMENT;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractInstrumentImpl;

import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.vm.PolyglotImpl.VMObject;

class PolyglotInstrumentImpl extends AbstractInstrumentImpl implements VMObject {

    Instrument api;
    InstrumentInfo info;
    final InstrumentCache cache;
    final PolyglotEngineImpl engine;

    private volatile OptionDescriptors options;
    private volatile OptionValuesImpl optionValues;
    private volatile boolean initialized;
    private volatile boolean created;

    PolyglotInstrumentImpl(PolyglotEngineImpl engine, InstrumentCache cache) {
        super(engine.impl);
        this.engine = engine;
        this.cache = cache;
    }

    @Override
    public OptionDescriptors getOptions() {
        checkEngine(engine);
        ensureInitialized();
        return options;
    }

    OptionValuesImpl getOptionValues() {
        if (optionValues == null) {
            synchronized (engine) {
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
            synchronized (engine) {
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
            synchronized (engine) {
                if (!created) {
                    if (!initialized) {
                        ensureInitialized();
                    }
                    try {
                        INSTRUMENT.createInstrument(engine.instrumentationHandler, this, cache.services(), getOptionValues());
                    } catch (Exception e) {
                        throw new IllegalStateException(String.format("Error initializing instrument '%s' using class '%s'.", cache.getId(), cache.getClassName()), e);
                    }
                    created = true;
                }
            }
        }
    }

    void ensureClosed() {
        assert Thread.holdsLock(engine);
        if (created) {
            if (created) {
                INSTRUMENT.disposeInstrument(engine.instrumentationHandler, this, false);
            }
            created = false;
            initialized = false;
            options = null;
            optionValues = null;
        }
    }

    @Override
    public <T> T lookup(Class<T> serviceClass) {
        if (engine.closed) {
            return null;
        } else if (cache.supportsService(serviceClass)) {
            ensureCreated();
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
        return cache.getVersion();
    }

}
