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

import java.io.InputStream;
import java.io.OutputStream;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractEngineDispatch;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.ProcessHandler;

import com.oracle.truffle.api.Truffle;

final class PolyglotEngineDispatch extends AbstractEngineDispatch {

    private final PolyglotImpl polyglot;

    protected PolyglotEngineDispatch(PolyglotImpl polyglot) {
        super(polyglot);
        this.polyglot = polyglot;
    }

    @Override
    public void setAPI(Object oreceiver, Engine engine) {
        ((PolyglotEngineImpl) oreceiver).api = engine;
    }

    @Override
    public Language requirePublicLanguage(Object oreceiver, String id) {
        PolyglotEngineImpl receiver = (PolyglotEngineImpl) oreceiver;
        try {
            return receiver.requirePublicLanguage(id);
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(receiver, t);
        }
    }

    @Override
    public Instrument requirePublicInstrument(Object oreceiver, String id) {
        PolyglotEngineImpl receiver = (PolyglotEngineImpl) oreceiver;
        try {
            return receiver.requirePublicInstrument(id);
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(receiver, t);
        }
    }

    @Override
    public void close(Object oreceiver, Object apiObject, boolean cancelIfExecuting) {
        PolyglotEngineImpl receiver = (PolyglotEngineImpl) oreceiver;
        try {
            receiver.ensureClosed(cancelIfExecuting, false);
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(receiver, t);
        }
    }

    @Override
    public Map<String, Instrument> getInstruments(Object oreceiver) {
        PolyglotEngineImpl receiver = (PolyglotEngineImpl) oreceiver;
        try {
            return receiver.getInstruments();
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(receiver, t);
        }
    }

    @Override
    public Map<String, Language> getLanguages(Object oreceiver) {
        PolyglotEngineImpl receiver = (PolyglotEngineImpl) oreceiver;
        try {
            return receiver.getLanguages();
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(receiver, t);
        }
    }

    @Override
    public OptionDescriptors getOptions(Object oreceiver) {
        PolyglotEngineImpl receiver = (PolyglotEngineImpl) oreceiver;
        try {
            return receiver.getOptions();
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(receiver, t);
        }
    }

    @Override
    public Context createContext(Object oreceiver, OutputStream out, OutputStream err, InputStream in, boolean allowHostAccess, HostAccess hostAccess, PolyglotAccess polyglotAccess,
                    boolean allowNativeAccess, boolean allowCreateThread, boolean allowHostIO, boolean allowHostClassLoading, boolean allowExperimentalOptions, Predicate<String> classFilter,
                    Map<String, String> options, Map<String, String[]> arguments, String[] onlyLanguages, FileSystem fileSystem, Object logHandlerOrStream, boolean allowCreateProcess,
                    ProcessHandler processHandler, EnvironmentAccess environmentAccess, Map<String, String> environment, ZoneId zone, Object limitsImpl, String currentWorkingDirectory,
                    ClassLoader hostClassLoader) {
        PolyglotEngineImpl receiver = (PolyglotEngineImpl) oreceiver;
        PolyglotContextImpl context = receiver.createContext(out, err, in, allowHostAccess, hostAccess, polyglotAccess, allowNativeAccess, allowCreateThread, allowHostIO, allowHostClassLoading,
                        allowExperimentalOptions,
                        classFilter, options, arguments, onlyLanguages, fileSystem, logHandlerOrStream, allowCreateProcess, processHandler, environmentAccess, environment, zone, limitsImpl,
                        currentWorkingDirectory, hostClassLoader);
        return polyglot.getAPIAccess().newContext(polyglot.contextDispatch, context, context.engine.api);
    }

    @Override
    public String getImplementationName(Object oreceiver) {
        PolyglotEngineImpl receiver = (PolyglotEngineImpl) oreceiver;
        try {
            return Truffle.getRuntime().getName();
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(receiver, t);
        }
    }

    @Override
    public Set<Source> getCachedSources(Object oreceiver) {
        PolyglotEngineImpl receiver = (PolyglotEngineImpl) oreceiver;
        try {
            return receiver.getCachedSources();
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(receiver, t);
        }
    }

    @Override
    public String getVersion(Object oreceiver) {
        PolyglotEngineImpl receiver = (PolyglotEngineImpl) oreceiver;
        try {
            return receiver.getVersion();
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(receiver, t);
        }
    }

}
