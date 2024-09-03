/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.io.MessageTransport;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.impl.DispatchOutputStream;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler.InstrumentClientInstrumenter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.ContextLocalFactory;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.ContextThreadLocalFactory;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

final class InstrumentAccessor extends Accessor {

    static final InstrumentAccessor ACCESSOR = new InstrumentAccessor();

    static final NodeSupport NODES = ACCESSOR.nodeSupport();
    static final SourceSupport SOURCE = ACCESSOR.sourceSupport();
    static final LanguageSupport LANGUAGE = ACCESSOR.languageSupport();
    static final EngineSupport ENGINE = ACCESSOR.engineSupport();
    static final InteropSupport INTEROP = ACCESSOR.interopSupport();

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

    static RuntimeSupport runtimeAccess() {
        return ACCESSOR.runtimeSupport();
    }

    protected boolean isTruffleObject(Object value) {
        return interopSupport().isTruffleObject(value);
    }

    static final class InstrumentImpl extends InstrumentSupport {

        @Override
        public Object createInstrumentationHandler(Object polyglotEngine, DispatchOutputStream out, DispatchOutputStream err, InputStream in, MessageTransport messageInterceptor,
                        boolean strongReferences) {
            return new InstrumentationHandler(polyglotEngine, out, err, in, messageInterceptor);
        }

        @Override
        public void initializeInstrument(Object instrumentationHandler, Object polyglotInstrument, String instrumentClassName, Supplier<? extends Object> instrumentSupplier) {
            ((InstrumentationHandler) instrumentationHandler).initializeInstrument(polyglotInstrument, instrumentClassName, instrumentSupplier);
        }

        @Override
        public void createInstrument(Object instrumentationHandler, Object polyglotInstrument, String[] expectedServices, OptionValues options) {
            ((InstrumentationHandler) instrumentationHandler).createInstrument(polyglotInstrument, expectedServices, options);
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
        public OptionDescriptors describeEngineOptions(Object instrumentationHandler, Object key, String requiredGroup) {
            InstrumentClientInstrumenter instrumenter = (InstrumentClientInstrumenter) ((InstrumentationHandler) instrumentationHandler).instrumenterMap.get(key);
            OptionDescriptors descriptors = instrumenter.instrument.getOptionDescriptors();
            return validateOptions(requiredGroup, instrumenter, descriptors);
        }

        @Override
        public OptionDescriptors describeContextOptions(Object instrumentationHandler, Object key, String requiredGroup) {
            InstrumentClientInstrumenter instrumenter = (InstrumentClientInstrumenter) ((InstrumentationHandler) instrumentationHandler).instrumenterMap.get(key);
            OptionDescriptors descriptors = instrumenter.instrument.getContextOptionDescriptors();
            return validateOptions(requiredGroup, instrumenter, descriptors);
        }

        private static OptionDescriptors validateOptions(String requiredGroup, InstrumentClientInstrumenter instrumenter, OptionDescriptors descriptors) {
            if (descriptors == null) {
                return OptionDescriptors.EMPTY;
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
        public void finalizeInstrument(Object instrumentationHandler, Object polyglotInstrument) {
            ((InstrumentationHandler) instrumentationHandler).finalizeInstrumenter(polyglotInstrument);
        }

        @Override
        public void disposeInstrument(Object instrumentationHandler, Object polyglotInstrument, boolean cleanupRequired) {
            ((InstrumentationHandler) instrumentationHandler).disposeInstrumenter(polyglotInstrument, cleanupRequired);
        }

        @Override
        public void collectEnvServices(Set<Object> collectTo, Object polyglotLanguageContext, TruffleLanguage<?> language) {
            InstrumentationHandler instrumentationHandler = (InstrumentationHandler) engineAccess().getInstrumentationHandler(polyglotLanguageContext);
            Instrumenter instrumenter = instrumentationHandler.forLanguage(language);
            collectTo.add(instrumenter);
            AllocationReporter allocationReporter = instrumentationHandler.getAllocationReporter(InstrumentAccessor.langAccess().getLanguageInfo(language));
            collectTo.add(allocationReporter);
        }

        @Override
        public <T> T getInstrumentationHandlerService(Object instrumentationHandler, Object key, Class<T> type) {
            return ((InstrumentationHandler) instrumentationHandler).lookup(key, type);
        }

        @Override
        public void onFirstExecution(RootNode rootNode, boolean validate) {
            assert !validate || validEngine(rootNode);
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
        public boolean hasContextBindings(Object engine) {
            InstrumentationHandler instrumentationHandler = (InstrumentationHandler) engineAccess().getInstrumentationHandler(engine);
            return instrumentationHandler.hasContextBindings();
        }

        @Override
        public boolean hasThreadBindings(Object engine) {
            InstrumentationHandler instrumentationHandler = (InstrumentationHandler) engineAccess().getInstrumentationHandler(engine);
            return instrumentationHandler.hasThreadBindings();
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
        public void notifyContextResetLimit(Object engine, TruffleContext context) {
            InstrumentationHandler instrumentationHandler = (InstrumentationHandler) engineAccess().getInstrumentationHandler(engine);
            instrumentationHandler.notifyContextResetLimit(context);
        }

        @Override
        public void notifyLanguageContextCreate(Object engine, TruffleContext context, LanguageInfo info) {
            InstrumentationHandler instrumentationHandler = (InstrumentationHandler) engineAccess().getInstrumentationHandler(engine);
            instrumentationHandler.notifyLanguageContextCreate(context, info);
        }

        @Override
        public void notifyLanguageContextCreated(Object engine, TruffleContext context, LanguageInfo info) {
            InstrumentationHandler instrumentationHandler = (InstrumentationHandler) engineAccess().getInstrumentationHandler(engine);
            instrumentationHandler.notifyLanguageContextCreated(context, info);
        }

        @Override
        public void notifyLanguageContextCreateFailed(Object engine, TruffleContext context, LanguageInfo info) {
            InstrumentationHandler instrumentationHandler = (InstrumentationHandler) engineAccess().getInstrumentationHandler(engine);
            instrumentationHandler.notifyLanguageContextCreateFailed(context, info);
        }

        @Override
        public void notifyLanguageContextInitialize(Object engine, TruffleContext context, LanguageInfo info) {
            InstrumentationHandler instrumentationHandler = (InstrumentationHandler) engineAccess().getInstrumentationHandler(engine);
            instrumentationHandler.notifyLanguageContextInitialize(context, info);
        }

        @Override
        public void notifyLanguageContextInitialized(Object engine, TruffleContext context, LanguageInfo info) {
            InstrumentationHandler instrumentationHandler = (InstrumentationHandler) engineAccess().getInstrumentationHandler(engine);
            instrumentationHandler.notifyLanguageContextInitialized(context, info);
        }

        @Override
        public void notifyLanguageContextInitializeFailed(Object engine, TruffleContext context, LanguageInfo info) {
            InstrumentationHandler instrumentationHandler = (InstrumentationHandler) engineAccess().getInstrumentationHandler(engine);
            instrumentationHandler.notifyLanguageContextInitializeFailed(context, info);
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
        public Object createPolyglotSourceSection(Object instrumentEnv, Object polyglotSource, SourceSection ss) {
            TruffleInstrument.Env env = (TruffleInstrument.Env) instrumentEnv;
            return engineAccess().createPolyglotSourceSection(env.getPolyglotInstrument(), polyglotSource, ss);
        }

        @Override
        public void patchInstrumentationHandler(Object instrumentationHandler, DispatchOutputStream out, DispatchOutputStream err, InputStream in) {
            ((InstrumentationHandler) instrumentationHandler).patch(out, err, in);
        }

        @Override
        public void finalizeStoreInstrumentationHandler(Object instrumentationHandler) {
            ((InstrumentationHandler) instrumentationHandler).finalizeStore();
        }

        @Override
        public boolean isInputValueSlotIdentifier(Object identifier) {
            return identifier instanceof ProbeNode.EventProviderWithInputChainNode.SavedInputValueID;
        }

        @Override
        public Collection<CallTarget> getLoadedCallTargets(Object instrumentationHandler) {
            Collection<RootNode> roots = ((InstrumentationHandler) instrumentationHandler).loadedRoots;
            List<CallTarget> targets = new ArrayList<>();
            for (RootNode root : roots) {
                CallTarget target = root.getCallTarget();
                if (target != null) {
                    targets.add(target);
                }
            }
            return targets;
        }

        @Override
        public Object getPolyglotInstrument(Object instrumentEnv) {
            return ((TruffleInstrument.Env) instrumentEnv).getPolyglotInstrument();
        }

        private static InstrumentationHandler getHandler(RootNode rootNode) {
            return (InstrumentationHandler) engineAccess().getInstrumentationHandler(rootNode);
        }

        @Override
        public boolean isInstrumentable(Node node) {
            return InstrumentationHandler.isInstrumentableNode(node);
        }

        private static boolean validEngine(RootNode rootNode) {
            if (InstrumentAccessor.engineAccess().skipEngineValidation(rootNode)) {
                return true;
            }
            Object currentSharingLayer = InstrumentAccessor.engineAccess().getCurrentSharingLayer();
            Object previousSharingLayer = InstrumentAccessor.nodesAccess().getSharingLayer(rootNode);
            if (!Objects.equals(previousSharingLayer, currentSharingLayer)) {
                throw InstrumentAccessor.engineAccess().invalidSharingError(rootNode, previousSharingLayer, currentSharingLayer);
            }
            return true;
        }

        @Override
        public Object invokeContextLocalFactory(Object factory, TruffleContext truffleContext) {
            Object result = ((ContextLocalFactory<?>) factory).create(truffleContext);
            if (result == null) {
                throw new IllegalStateException(String.format("%s.create is not allowed to return null.", ContextLocalFactory.class.getSimpleName()));
            }
            return result;
        }

        @Override
        public Object invokeContextThreadLocalFactory(Object factory, TruffleContext truffleContext, Thread t) {
            Object result = ((ContextThreadLocalFactory<?>) factory).create(truffleContext, t);
            if (result == null) {
                throw new IllegalStateException(String.format("%s.create is not allowed to return null.", ContextThreadLocalFactory.class.getSimpleName()));
            }
            return result;
        }

        @Override
        @ExplodeLoop
        public void notifyEnter(Object instrumentationHandler, TruffleContext truffleContext) {
            InstrumentationHandler handler = (InstrumentationHandler) instrumentationHandler;
            CompilerAsserts.partialEvaluationConstant(handler);
            for (ThreadsActivationListener listener : handler.getThreadsActivationListeners()) {
                listener.onEnterThread(truffleContext);
            }
        }

        @Override
        @ExplodeLoop
        public void notifyLeave(Object instrumentationHandler, TruffleContext truffleContext) {
            InstrumentationHandler handler = (InstrumentationHandler) instrumentationHandler;
            CompilerAsserts.partialEvaluationConstant(handler);
            for (ThreadsActivationListener listener : handler.getThreadsActivationListeners()) {
                listener.onLeaveThread(truffleContext);
            }
        }

    }
}
