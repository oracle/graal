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
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractEngineImpl;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.ProcessHandler;

import com.oracle.truffle.api.Truffle;

final class PolyglotEngineDispatch extends AbstractEngineImpl<PolyglotEngineImpl> {

    protected PolyglotEngineDispatch(AbstractPolyglotImpl impl) {
        super(impl);
    }

    @Override
    public Object getAPI(PolyglotEngineImpl receiver) {
        return receiver.api;
    }

    @Override
    public void setAPI(PolyglotEngineImpl receiver, Object key) {
        assert receiver.api == null : "API can only be initialized once";
        receiver.api = key;
    }

    @Override
    public Language requirePublicLanguage(PolyglotEngineImpl receiver, String id) {
        try {
            return receiver.requirePublicLanguage(id);
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(receiver, t);
        }
    }

    @Override
    public Instrument requirePublicInstrument(PolyglotEngineImpl receiver, String id) {
        try {
            return receiver.requirePublicInstrument(id);
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(receiver, t);
        }
    }

    @Override
    public void close(PolyglotEngineImpl receiver, Object apiObject, boolean cancelIfExecuting) {
        try {
            receiver.ensureClosed(cancelIfExecuting, false);
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(receiver, t);
        }
    }

    @Override
    public Map<String, Instrument> getInstruments(PolyglotEngineImpl receiver) {
        try {
            return receiver.getInstruments();
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(receiver, t);
        }
    }

    @Override
    public Map<String, Language> getLanguages(PolyglotEngineImpl receiver) {
        try {
            return receiver.getLanguages();
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(receiver, t);
        }
    }

    @Override
    public OptionDescriptors getOptions(PolyglotEngineImpl receiver) {
        try {
            return receiver.getOptions();
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(receiver, t);
        }
    }

    @Override
    public Object createContext(PolyglotEngineImpl receiver, OutputStream out, OutputStream err, InputStream in, boolean allowHostAccess, HostAccess hostAccess, PolyglotAccess polyglotAccess,
                    boolean allowNativeAccess, boolean allowCreateThread, boolean allowHostIO, boolean allowHostClassLoading, boolean allowExperimentalOptions, Predicate<String> classFilter,
                    Map<String, String> options, Map<String, String[]> arguments, String[] onlyLanguages, FileSystem fileSystem, Object logHandlerOrStream, boolean allowCreateProcess,
                    ProcessHandler processHandler, EnvironmentAccess environmentAccess, Map<String, String> environment, ZoneId zone, Object limitsImpl, String currentWorkingDirectory,
                    ClassLoader hostClassLoader) {
        return receiver.createContext(out, err, in, allowHostAccess, hostAccess, polyglotAccess, allowNativeAccess, allowCreateThread, allowHostIO, allowHostClassLoading, allowExperimentalOptions,
                        classFilter, options, arguments, onlyLanguages, fileSystem, logHandlerOrStream, allowCreateProcess, processHandler, environmentAccess, environment, zone, limitsImpl,
                        currentWorkingDirectory, hostClassLoader);
    }

    @Override
    public String getImplementationName(PolyglotEngineImpl receiver) {
        try {
            return Truffle.getRuntime().getName();
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(receiver, t);
        }
    }

    @Override
    public Set<Source> getCachedSources(PolyglotEngineImpl receiver) {
        try {
            return receiver.getCachedSources();
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(receiver, t);
        }
    }

    @Override
    public String getVersion(PolyglotEngineImpl receiver) {
        try {
            return receiver.getVersion();
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(receiver, t);
        }
    }

}
