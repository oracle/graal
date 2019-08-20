/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.impl.DispatchOutputStream;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler.InstrumentClientInstrumenter;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.io.MessageTransport;

import java.io.InputStream;
import java.util.Set;

final class InstrumentAccessor extends Accessor {

    static final InstrumentAccessor ACCESSOR = new InstrumentAccessor();

    private InstrumentAccessor() {
    }

    static NodeSupport nodesAccess() {
        return ACCESSOR.nodeSupport();
    }

    static LanguageSupport langAccess() {
        return ACCESSOR.languageSupport();
    }

    static EngineSupport engineAccess() {
        return ACCESSOR.engineSupport();
    }

    static InteropSupport interopAccess() {
        return ACCESSOR.interopSupport();
    }

    protected boolean isTruffleObject(Object value) {
        return interopSupport().isTruffleObject(value);
    }

    static final class InstrumentImpl extends InstrumentSupport {

        @Override
        public Object createInstrumentationHandler(Object vm, DispatchOutputStream out, DispatchOutputStream err, InputStream in, MessageTransport messageInterceptor) {
            return new InstrumentationHandler(vm, out, err, in, messageInterceptor);
        }

        @Override
        public void initializeInstrument(Object instrumentationHandler, Object key, Class<?> instrumentClass) {
            ((InstrumentationHandler) instrumentationHandler).initializeInstrument(key, instrumentClass);
        }

        @Override
        public void createInstrument(Object instrumentationHandler, Object key, String[] expectedServices, OptionValues options) {
            ((InstrumentationHandler) instrumentationHandler).createInstrument(key, expectedServices, options);
        }

        @Override
        public Object getEngineInstrumenter(Object instrumentationHandler) {
            return ((InstrumentationHandler) instrumentationHandler).engineInstrumenter;
        }

        @Override
        public void onNodeInserted(RootNode rootNode, Node tree) {
            InstrumentationHandler handler = getHandler(rootNode);
            if (handler != null) {
                handler.onNodeInserted(rootNode, tree);
            }
        }

        @Override
        public OptionDescriptors describeOptions(Object instrumentationHandler, Object key, String requiredGroup) {
            InstrumentClientInstrumenter instrumenter = (InstrumentClientInstrumenter) ((InstrumentationHandler) instrumentationHandler).instrumenterMap.get(key);
            OptionDescriptors descriptors = instrumenter.instrument.getOptionDescriptors();
            if (descriptors == null) {
                descriptors = OptionDescriptors.EMPTY;
            }
            String groupPlusDot = requiredGroup + ".";
            for (OptionDescriptor descriptor : descriptors) {
                if (!descriptor.getName().equals(requiredGroup) && !descriptor.getName().startsWith(groupPlusDot)) {
                    throw new IllegalArgumentException(String.format("Illegal option prefix in name '%s' specified for option described by instrument '%s'. " +
                                    "The option prefix must match the id of the instrument '%s'.",
                                    descriptor.getName(), instrumenter.instrument.getClass().getName(), requiredGroup));
                }
            }
            return descriptors;
        }

        @Override
        public void finalizeInstrument(Object instrumentationHandler, Object key) {
            ((InstrumentationHandler) instrumentationHandler).finalizeInstrumenter(key);
        }

        @Override
        public void disposeInstrument(Object instrumentationHandler, Object key, boolean cleanupRequired) {
            ((InstrumentationHandler) instrumentationHandler).disposeInstrumenter(key, cleanupRequired);
        }

        @Override
        public void collectEnvServices(Set<Object> collectTo, Object languageShared, TruffleLanguage<?> language) {
            InstrumentationHandler instrumentationHandler = (InstrumentationHandler) engineAccess().getInstrumentationHandler(languageShared);
            Instrumenter instrumenter = instrumentationHandler.forLanguage(language);
            collectTo.add(instrumenter);
            AllocationReporter allocationReporter = instrumentationHandler.getAllocationReporter(InstrumentAccessor.langAccess().getLanguageInfo(language));
            collectTo.add(allocationReporter);
        }

        @Override
        public <T> T getInstrumentationHandlerService(Object vm, Object key, Class<T> type) {
            InstrumentationHandler instrumentationHandler = (InstrumentationHandler) vm;
            return instrumentationHandler.lookup(key, type);
        }

        @Override
        public void onFirstExecution(RootNode rootNode) {
            InstrumentationHandler handler = getHandler(rootNode);
            if (handler != null) {
                handler.onFirstExecution(rootNode);
            }
        }

        @Override
        public void onLoad(RootNode rootNode) {
            InstrumentationHandler handler = getHandler(rootNode);
            if (handler != null) {
                handler.onLoad(rootNode);
            }
        }

        @Override
        public Iterable<Scope> findTopScopes(TruffleLanguage.Env env) {
            return TruffleInstrument.Env.findTopScopes(env);
        }

        @Override
        @CompilerDirectives.TruffleBoundary
        public void notifyContextCreated(Object engine, TruffleContext context) {
            InstrumentationHandler instrumentationHandler = (InstrumentationHandler) engineAccess().getInstrumentationHandler(engine);
            instrumentationHandler.notifyContextCreated(context);
        }

        @Override
        @CompilerDirectives.TruffleBoundary
        public void notifyContextClosed(Object engine, TruffleContext context) {
            InstrumentationHandler instrumentationHandler = (InstrumentationHandler) engineAccess().getInstrumentationHandler(engine);
            instrumentationHandler.notifyContextClosed(context);
        }

        @Override
        public void notifyLanguageContextCreated(Object engine, TruffleContext context, LanguageInfo info) {
            InstrumentationHandler instrumentationHandler = (InstrumentationHandler) engineAccess().getInstrumentationHandler(engine);
            instrumentationHandler.notifyLanguageContextCreated(context, info);
        }

        @Override
        public void notifyLanguageContextInitialized(Object engine, TruffleContext context, LanguageInfo info) {
            InstrumentationHandler instrumentationHandler = (InstrumentationHandler) engineAccess().getInstrumentationHandler(engine);
            instrumentationHandler.notifyLanguageContextInitialized(context, info);
        }

        @Override
        public void notifyLanguageContextFinalized(Object engine, TruffleContext context, LanguageInfo info) {
            InstrumentationHandler instrumentationHandler = (InstrumentationHandler) engineAccess().getInstrumentationHandler(engine);
            instrumentationHandler.notifyLanguageContextFinalized(context, info);
        }

        @Override
        public void notifyLanguageContextDisposed(Object engine, TruffleContext context, LanguageInfo info) {
            InstrumentationHandler instrumentationHandler = (InstrumentationHandler) engineAccess().getInstrumentationHandler(engine);
            instrumentationHandler.notifyLanguageContextDisposed(context, info);
        }

        @Override
        @CompilerDirectives.TruffleBoundary
        public void notifyThreadStarted(Object engine, TruffleContext context, Thread thread) {
            InstrumentationHandler instrumentationHandler = (InstrumentationHandler) engineAccess().getInstrumentationHandler(engine);
            instrumentationHandler.notifyThreadStarted(context, thread);
        }

        @Override
        @CompilerDirectives.TruffleBoundary
        public void notifyThreadFinished(Object engine, TruffleContext context, Thread thread) {
            InstrumentationHandler instrumentationHandler = (InstrumentationHandler) engineAccess().getInstrumentationHandler(engine);
            instrumentationHandler.notifyThreadFinished(context, thread);
        }

        @Override
        public org.graalvm.polyglot.SourceSection createSourceSection(Object instrumentEnv, org.graalvm.polyglot.Source source, com.oracle.truffle.api.source.SourceSection ss) {
            TruffleInstrument.Env env = (TruffleInstrument.Env) instrumentEnv;
            return engineAccess().createSourceSection(env.getVMObject(), source, ss);
        }

        @Override
        public void patchInstrumentationHandler(Object vm, DispatchOutputStream out, DispatchOutputStream err, InputStream in) {
            final InstrumentationHandler instrumentationHandler = (InstrumentationHandler) vm;
            instrumentationHandler.patch(out, err, in);
        }

        @Override
        public boolean isInputValueSlotIdentifier(Object identifier) {
            return identifier instanceof ProbeNode.EventProviderWithInputChainNode.SavedInputValueID;
        }

        private static InstrumentationHandler getHandler(RootNode rootNode) {
            LanguageInfo info = rootNode.getLanguageInfo();
            if (info == null) {
                return null;
            }
            Object languageShared = nodesAccess().getEngineObject(info);
            if (languageShared == null) {
                return null;
            }
            return (InstrumentationHandler) engineAccess().getInstrumentationHandler(languageShared);
        }

    }
}
