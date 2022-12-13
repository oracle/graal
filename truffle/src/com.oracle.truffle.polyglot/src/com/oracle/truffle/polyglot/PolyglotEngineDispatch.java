/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
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
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.LogHandler;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.io.ProcessHandler;
import org.graalvm.polyglot.management.ExecutionEvent;
import org.graalvm.polyglot.management.ExecutionListener;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;

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
            receiver.ensureClosed(cancelIfExecuting, false, false);
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
    public Context createContext(Object oreceiver, OutputStream out, OutputStream err, InputStream in,
                    boolean allowHostLookup,
                    HostAccess hostAccess, PolyglotAccess polyglotAccess, boolean allowNativeAccess,
                    boolean allowCreateThread, boolean allowHostClassLoading, boolean allowInnerContextOptions,
                    boolean allowExperimentalOptions, Predicate<String> classFilter,
                    Map<String, String> options, Map<String, String[]> arguments, String[] onlyLanguages, IOAccess ioAccess, LogHandler logHandler, boolean allowCreateProcess,
                    ProcessHandler processHandler, EnvironmentAccess environmentAccess, Map<String, String> environment, ZoneId zone, Object limitsImpl, String currentWorkingDirectory,
                    ClassLoader hostClassLoader, boolean allowValueSharing, boolean useSystemExit) {
        PolyglotEngineImpl receiver = (PolyglotEngineImpl) oreceiver;
        PolyglotContextImpl context = receiver.createContext(out, err, in, allowHostLookup, hostAccess, polyglotAccess,
                        allowNativeAccess, allowCreateThread, allowHostClassLoading,
                        allowInnerContextOptions,
                        allowExperimentalOptions,
                        classFilter, options, arguments, onlyLanguages, ioAccess, logHandler, allowCreateProcess, processHandler, environmentAccess, environment, zone, limitsImpl,
                        currentWorkingDirectory, hostClassLoader, allowValueSharing, useSystemExit);
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

    @Override
    public ExecutionListener attachExecutionListener(Object engineReceiver, Consumer<ExecutionEvent> onEnter, Consumer<ExecutionEvent> onReturn, boolean expressions, boolean statements,
                    boolean roots,
                    Predicate<Source> sourceFilter, Predicate<String> rootFilter, boolean collectInputValues, boolean collectReturnValues, boolean collectExceptions) {
        PolyglotEngineImpl engine = (PolyglotEngineImpl) engineReceiver;
        Instrumenter instrumenter = (Instrumenter) EngineAccessor.INSTRUMENT.getEngineInstrumenter(engine.instrumentationHandler);

        List<Class<? extends Tag>> tags = new ArrayList<>();
        if (expressions) {
            tags.add(StandardTags.ExpressionTag.class);
        }
        if (statements) {
            tags.add(StandardTags.StatementTag.class);
        }
        if (roots) {
            tags.add(StandardTags.RootTag.class);
        }

        if (tags.isEmpty()) {
            throw new IllegalArgumentException("No elements specified to listen to for execution listener. Need to specify at least one element kind: expressions, statements or roots.");
        }
        if (onReturn == null && onEnter == null) {
            throw new IllegalArgumentException("At least one event consumer must be provided for onEnter or onReturn.");
        }

        SourceSectionFilter.Builder filterBuilder = SourceSectionFilter.newBuilder().tagIs(tags.toArray(new Class<?>[0]));
        filterBuilder.includeInternal(false);

        PolyglotExecutionListenerDispatch.ListenerImpl config = new PolyglotExecutionListenerDispatch.ListenerImpl(polyglot.getExecutionEventDispatch(), engine,
                        onEnter, onReturn, collectInputValues, collectReturnValues, collectExceptions);

        filterBuilder.sourceIs(new SourceSectionFilter.SourcePredicate() {
            public boolean test(com.oracle.truffle.api.source.Source s) {
                String language = s.getLanguage();
                if (language == null) {
                    return false;
                } else if (!engine.idToLanguage.containsKey(language)) {
                    return false;
                } else if (sourceFilter != null) {
                    try {
                        return sourceFilter.test(PolyglotImpl.getOrCreatePolyglotSource(polyglot, s));
                    } catch (Throwable e) {
                        if (config.closing) {
                            // configuration is closing ignore errors.
                            return false;
                        }
                        throw engine.host.toHostException(null, e);
                    }
                } else {
                    return true;
                }
            }
        });

        if (rootFilter != null) {
            filterBuilder.rootNameIs(new Predicate<String>() {
                public boolean test(String s) {
                    try {
                        return rootFilter.test(s);
                    } catch (Throwable e) {
                        if (config.closing) {
                            // configuration is closing ignore errors.
                            return false;
                        }
                        throw engine.host.toHostException(null, e);
                    }
                }
            });
        }

        SourceSectionFilter filter = filterBuilder.build();
        EventBinding<?> binding;
        try {
            boolean mayNeedInputValues = config.collectInputValues && config.onReturn != null;
            boolean mayNeedReturnValue = config.collectReturnValues && config.onReturn != null;
            boolean mayNeedExceptions = config.collectExceptions;

            if (mayNeedInputValues || mayNeedReturnValue || mayNeedExceptions) {
                binding = instrumenter.attachExecutionEventFactory(filter, mayNeedInputValues ? filter : null, new ExecutionEventNodeFactory() {
                    public ExecutionEventNode create(EventContext context) {
                        return new PolyglotExecutionListenerDispatch.ProfilingNode(config, context);
                    }
                });
            } else {
                // fast path no collection of additional profiles
                binding = instrumenter.attachExecutionEventFactory(filter, null, new ExecutionEventNodeFactory() {
                    public ExecutionEventNode create(EventContext context) {
                        return new PolyglotExecutionListenerDispatch.DefaultNode(config, context);
                    }
                });
            }
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(engine, t);
        }
        config.binding = binding;
        return polyglot.getManagement().newExecutionListener(polyglot.getExecutionListenerDispatch(), config);
    }

    @Override
    public void shutdown(Object engine) {
        ((PolyglotEngineImpl) engine).onVMShutdown();
    }

    @Override
    public RuntimeException hostToGuestException(Object engineReceiver, Throwable throwable) {
        return PolyglotImpl.hostToGuestException((PolyglotEngineImpl) engineReceiver, throwable);
    }
}
