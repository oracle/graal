/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.ref.Reference;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractEngineDispatch;
import org.graalvm.polyglot.io.ProcessHandler;

public class HostEngineDispatch extends AbstractEngineDispatch {

    final APIAccess api;
    final HostPolyglotDispatch polyglot;
    final HostEntryPoint hostToGuest;
    final HostContextDispatch remoteContext;

    protected HostEngineDispatch(HostPolyglotDispatch polyglot) {
        super(polyglot);
        this.api = polyglot.getAPIAccess();
        this.polyglot = polyglot;
        this.hostToGuest = polyglot.getHostToGuest();
        this.remoteContext = new HostContextDispatch(polyglot);
    }

    @Override
    public Context createContext(Object receiver, Engine engineApi, SandboxPolicy sandboxPolicy, OutputStream out, OutputStream err, InputStream in, boolean allowHostAccess, Object hostAccess,
                    Object polyglotAccess,
                    boolean allowNativeAccess, boolean allowCreateThread, boolean allowHostClassLoading, boolean allowInnerContextOptions,
                    boolean allowExperimentalOptions,
                    Predicate<String> classFilter, Map<String, String> options, Map<String, String[]> arguments, String[] onlyLanguages, Object ioAccess, Object logHandler,
                    boolean allowCreateProcess, ProcessHandler processHandler, Object environmentAccess, Map<String, String> environment, ZoneId zone, Object limitsImpl,
                    String currentWorkingDirectory, String tmpDir, ClassLoader hostClassLoader, boolean allowValueSharing, boolean useSystemExit, boolean registerInActiveContexts) {
        HostEngine engine = (HostEngine) receiver;
        Engine localEngine = (Engine) engine.localEngine;
        AbstractEngineDispatch dispatch = api.getEngineDispatch(localEngine);
        Object engineReceiver = api.getEngineReceiver(localEngine);
        Context localContext = dispatch.createContext(engineReceiver, localEngine, sandboxPolicy, out, err, in, allowHostAccess, hostAccess, polyglotAccess, allowNativeAccess,
                        allowCreateThread,
                        allowHostClassLoading,
                        allowInnerContextOptions, allowExperimentalOptions, classFilter, options, arguments, onlyLanguages, ioAccess, logHandler, allowCreateProcess, processHandler,
                        environmentAccess, environment, zone, limitsImpl, currentWorkingDirectory, tmpDir, hostClassLoader, true, useSystemExit, false);
        long guestContextId = hostToGuest.remoteCreateContext(engine.remoteEngine, sandboxPolicy, tmpDir);
        HostContext context = new HostContext(engine, guestContextId, localContext);
        hostToGuest.registerHostContext(guestContextId, context);
        Context contextApi = polyglot.getAPIAccess().newContext(remoteContext, context, engineApi, registerInActiveContexts);
        if (registerInActiveContexts) {
            polyglot.getAPIAccess().processReferenceQueue();
        }
        return contextApi;
    }

    @Override
    public void setEngineAPIReference(Object receiver, Reference<Engine> key) {
        ((HostEngine) receiver).setPolyglotAPIReference(key);
    }

    @Override
    public Object requirePublicLanguage(Object receiver, String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object requirePublicInstrument(Object receiver, String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close(Object receiver, Object apiObject, boolean cancelIfExecuting) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, Object> getInstruments(Object receiver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, Object> getLanguages(Object receiver) {
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
    public Set<Object> getCachedSources(Object receiver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getVersion(Object receiver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object attachExecutionListener(Object engine, Consumer<Object> onEnter, Consumer<Object> onReturn, boolean expressions, boolean statements, boolean roots,
                    Predicate<Object> sourceFilter, Predicate<String> rootFilter, boolean collectInputValues, boolean collectReturnValues, boolean collectExceptions) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void shutdown(Object receiver) {
        HostEngine engine = (HostEngine) receiver;
        hostToGuest.shutdown(engine.remoteEngine);
    }

    @Override
    public RuntimeException hostToGuestException(Object receiver, Throwable throwable) {
        HostEngine engine = (HostEngine) receiver;
        Engine localEngine = (Engine) engine.localEngine;
        AbstractEngineDispatch dispatch = api.getEngineDispatch(localEngine);
        Object engineReceiver = api.getEngineReceiver(localEngine);
        return dispatch.hostToGuestException(engineReceiver, throwable);
    }

    @Override
    public SandboxPolicy getSandboxPolicy(Object receiver) {
        HostEngine engine = (HostEngine) receiver;
        Engine localEngine = (Engine) engine.localEngine;
        AbstractEngineDispatch dispatch = api.getEngineDispatch(localEngine);
        Object engineReceiver = api.getEngineReceiver(localEngine);
        return dispatch.getSandboxPolicy(engineReceiver);
    }

    @Override
    public void onEngineCollected(Object receiver) {
        HostEngine engine = (HostEngine) receiver;
        Engine localEngine = (Engine) engine.localEngine;
        AbstractEngineDispatch dispatch = api.getEngineDispatch(localEngine);
        Object engineReceiver = api.getEngineReceiver(localEngine);
        dispatch.onEngineCollected(engineReceiver);
        hostToGuest.shutdown(engine.remoteEngine);
    }

    @Override
    public boolean storeCache(Object engineReceiver, Path targetFile, long cancelledWord) {
        throw new UnsupportedOperationException();
    }

}
