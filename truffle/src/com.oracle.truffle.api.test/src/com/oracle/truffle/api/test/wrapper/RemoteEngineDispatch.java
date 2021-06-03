/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.wrapper;

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

public class RemoteEngineDispatch extends AbstractEngineDispatch {

    final RemotePolyglotDispatch dispatch;
    final HostToGuest hostToGuest;
    final RemoteContextDispatch remoteContext;

    protected RemoteEngineDispatch(RemotePolyglotDispatch impl) {
        super(impl);
        this.dispatch = impl;
        this.hostToGuest = impl.getHostToGuest();
        this.remoteContext = new RemoteContextDispatch(dispatch);
    }

    @Override
    public Context createContext(Object receiver, OutputStream out, OutputStream err, InputStream in, boolean allowHostAccess, HostAccess hostAccess, PolyglotAccess polyglotAccess,
                    boolean allowNativeAccess, boolean allowCreateThread, boolean allowHostIO, boolean allowHostClassLoading, boolean allowExperimentalOptions, Predicate<String> classFilter,
                    Map<String, String> options, Map<String, String[]> arguments, String[] onlyLanguages, FileSystem fileSystem, Object logHandlerOrStream, boolean allowCreateProcess,
                    ProcessHandler processHandler, EnvironmentAccess environmentAccess, Map<String, String> environment, ZoneId zone, Object limitsImpl, String currentWorkingDirectory,
                    ClassLoader hostClassLoader) {
        RemoteEngine engine = ((RemoteEngine) receiver);
        Object hostContext = engine.hostAccess.createHostContext(hostAccess, hostClassLoader, classFilter, allowHostClassLoading, allowHostAccess);
        long contextId = hostToGuest.remoteCreateContext(engine.id);
        RemoteContext context = new RemoteContext(engine, contextId, hostContext);
        return dispatch.getAPIAccess().newContext(remoteContext, context, engine.api);
    }

    @Override
    public void setAPI(Object receiver, Engine key) {
        ((RemoteEngine) receiver).setApi(key);
    }

    @Override
    public Language requirePublicLanguage(Object receiver, String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Instrument requirePublicInstrument(Object receiver, String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close(Object receiver, Object apiObject, boolean cancelIfExecuting) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, Instrument> getInstruments(Object receiver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, Language> getLanguages(Object receiver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public OptionDescriptors getOptions(Object receiver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getImplementationName(Object receiver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Source> getCachedSources(Object receiver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getVersion(Object receiver) {
        throw new UnsupportedOperationException();
    }

}
