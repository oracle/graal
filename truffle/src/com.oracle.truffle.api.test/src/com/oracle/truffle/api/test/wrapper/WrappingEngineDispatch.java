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
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractEngineDispatch;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.ProcessHandler;

public class WrappingEngineDispatch extends AbstractEngineDispatch {

    final AbstractEngineDispatch delegate;
    final AbstractPolyglotImpl impl;

    protected WrappingEngineDispatch(AbstractPolyglotImpl impl, AbstractEngineDispatch next) {
        super(impl);
        this.impl = impl;
        this.delegate = next;
    }

    @Override
    public Language requirePublicLanguage(Object receiver, String id) {
        return delegate.requirePublicLanguage(((WrappingEngine) receiver).delegate, id);
    }

    @Override
    public Instrument requirePublicInstrument(Object receiver, String id) {
        return delegate.requirePublicInstrument(((WrappingEngine) receiver).delegate, id);
    }

    @Override
    public void close(Object receiver, Object apiObject, boolean cancelIfExecuting) {
        delegate.close(((WrappingEngine) receiver).delegate, apiObject, cancelIfExecuting);
    }

    @Override
    public Map<String, Instrument> getInstruments(Object receiver) {
        return delegate.getInstruments(((WrappingEngine) receiver).delegate);
    }

    @Override
    public Map<String, Language> getLanguages(Object receiver) {
        return delegate.getLanguages(((WrappingEngine) receiver).delegate);
    }

    @Override
    public OptionDescriptors getOptions(Object receiver) {
        return delegate.getOptions(((WrappingEngine) receiver).delegate);
    }

    @Override
    public Context createContext(Object receiver, OutputStream out, OutputStream err, InputStream in, boolean allowHostAccess, HostAccess hostAccess, PolyglotAccess polyglotAccess,
                    boolean allowNativeAccess, boolean allowCreateThread, boolean allowHostIO, boolean allowHostClassLoading, boolean allowExperimentalOptions, Predicate<String> classFilter,
                    Map<String, String> options, Map<String, String[]> arguments, String[] onlyLanguages, FileSystem fileSystem, Object logHandlerOrStream, boolean allowCreateProcess,
                    ProcessHandler processHandler, EnvironmentAccess environmentAccess, Map<String, String> environment, ZoneId zone, Object limitsImpl, String currentWorkingDirectory,
                    ClassLoader hostClassLoader) {
        Context context = delegate.createContext(((WrappingEngine) receiver).delegate, out, err, in, allowHostAccess, hostAccess, polyglotAccess, allowNativeAccess, allowCreateThread, allowHostIO,
                        allowHostClassLoading,
                        allowExperimentalOptions, classFilter, options, arguments, onlyLanguages, fileSystem, logHandlerOrStream, allowCreateProcess, processHandler, environmentAccess, environment,
                        zone, limitsImpl, currentWorkingDirectory, hostClassLoader);
        WrappingContext wrapping = new WrappingContext(impl.getAPIAccess().getReceiver(context));
        WrappingContextDispatch dispatch = new WrappingContextDispatch(impl, impl.getAPIAccess().getDispatch(context));
        return impl.getAPIAccess().newContext(dispatch, wrapping, context.getEngine());
    }

    @Override
    public String getImplementationName(Object receiver) {
        return delegate.getImplementationName(((WrappingEngine) receiver).delegate);
    }

    @Override
    public Set<Source> getCachedSources(Object receiver) {
        return delegate.getCachedSources(((WrappingEngine) receiver).delegate);
    }

    @Override
    public void setAPI(Object receiver, Engine key) {
        delegate.setAPI(((WrappingEngine) receiver).delegate, key);
    }

    @Override
    public String getVersion(Object receiver) {
        return delegate.getVersion(((WrappingEngine) receiver).delegate);
    }

}
