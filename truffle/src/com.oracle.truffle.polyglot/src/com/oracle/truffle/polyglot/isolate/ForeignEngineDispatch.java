/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot.isolate;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;

import org.graalvm.nativebridge.ByLocalReference;
import org.graalvm.nativebridge.ByRemoteReference;
import org.graalvm.nativebridge.CustomDispatchAccessor;
import org.graalvm.nativebridge.CustomReceiverAccessor;
import org.graalvm.nativebridge.ForeignObject;
import org.graalvm.nativebridge.GenerateHotSpotToNativeBridge;
import org.graalvm.nativebridge.GenerateNativeToNativeBridge;
import org.graalvm.nativebridge.GenerateProcessToProcessBridge;
import org.graalvm.nativebridge.Isolate;
import org.graalvm.nativebridge.IsolateDeathException;
import org.graalvm.nativebridge.IsolateDeathHandler;
import org.graalvm.nativebridge.IsolateThread;
import org.graalvm.nativebridge.Peer;
import org.graalvm.nativebridge.ProcessPeer;
import org.graalvm.nativebridge.ReceiverMethod;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractContextDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractEngineDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractHostLanguageService;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractInstrumentDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractLanguageDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.LogHandler;
import org.graalvm.polyglot.io.ProcessHandler;

import com.oracle.truffle.polyglot.isolate.PolyglotMarshallerConfig.InstrumentPeerMap;
import com.oracle.truffle.polyglot.isolate.PolyglotMarshallerConfig.Null;

@GenerateHotSpotToNativeBridge(factory = PolyglotIsolateForeignFactory.class)
@GenerateNativeToNativeBridge(factory = PolyglotIsolateForeignFactory.class)
@GenerateProcessToProcessBridge(factory = PolyglotIsolateForeignFactory.class)
@IsolateDeathHandler(ForeignEngineDispatch.AsCancelledPolyglotException.class)
abstract class ForeignEngineDispatch extends AbstractEngineDispatch {

    private static final String[] EMPTY_LANGUAGES = new String[0];

    private final AbstractPolyglotImpl impl;
    private final APIAccess apiAccess;

    protected ForeignEngineDispatch(AbstractPolyglotImpl impl) {
        super(impl);
        this.impl = impl;
        this.apiAccess = impl.getAPIAccess();
    }

    @Override
    public abstract @ByRemoteReference(ForeignOptionDescriptors.class) OptionDescriptors getOptions(Object receiver);

    @Override
    public final void close(Object receiver, @Null Object apiObject, boolean cancelIfExecuting) {
        ForeignEngine foreignEngine = (ForeignEngine) receiver;
        Isolate<?> isolate = foreignEngine.getPeer().getIsolate();
        IsolateThread isolateThread = isolate.tryEnter();
        // Check for null, the isolate may be closing or already closed.
        if (isolateThread != null) {
            try {
                /*
                 * A) Close isolated engine
                 */
                closeImpl(receiver, apiObject, cancelIfExecuting);
            } finally {
                isolateThread.leave();
            }
        } else {
            /*
             * B) In case the isolated engine is not accessible, close just the local engine. It
             * should be already closed as the isolated engine close operation notifies the local
             * engine. However, engine close might not be a no-op even if the engine is already
             * closed, so we have to execute it.
             */
            Object localEngine = foreignEngine.getLocalEngine();
            apiAccess.getEngineDispatch(localEngine).close(apiAccess.getEngineReceiver(localEngine), localEngine, cancelIfExecuting);
        }
    }

    @ReceiverMethod("close")
    abstract void closeImpl(Object receiver, @Null Object apiObject, boolean cancelIfExecuting);

    @Override
    public final void setEngineAPIReference(Object receiver, Reference<Engine> key) {
        ((ForeignEngine) receiver).setEngineAPIReference(key);
    }

    @Override
    public final Context createContext(Object receiver, Engine engineApi, SandboxPolicy sandboxPolicy, OutputStream out, OutputStream err, InputStream in, boolean allowHostAccess, Object hostAccess,
                    Object polyglotAccess, boolean allowNativeAccess, boolean allowCreateThread, boolean allowHostClassLoading, boolean allowInnerContextOptions,
                    boolean allowExperimentalOptions, Predicate<String> classFilter, Map<String, String> options, Map<String, String[]> arguments, String[] onlyLanguages,
                    Object ioAccess, Object logHandler, boolean allowCreateProcess, ProcessHandler processHandler, Consumer<PolyglotException> exceptionHandler, Object environmentAccess,
                    Map<String, String> environment, ZoneId zone, Object limitsImpl, String currentWorkingDirectory, String tmpDir, ClassLoader hostClassLoader, boolean allowValueSharing,
                    boolean useSystemExit, boolean registerInActiveContexts) {
        ForeignEngine foreignEngine = (ForeignEngine) receiver;
        Engine localEngine = foreignEngine.getLocalEngine();
        AbstractEngineDispatch dispatch = apiAccess.getEngineDispatch(localEngine);
        Object engineReceiver = apiAccess.getEngineReceiver(localEngine);
        if (hostAccess != HostAccess.NONE && !apiAccess.isMethodScopingEnabled(hostAccess)) {
            OptionValues engineOptions = PolyglotIsolateAccessor.ENGINE.getEngineOptionValues(engineReceiver);
            if (engineOptions.get(PolyglotIsolateAccessor.ENGINE.getWarnMethodScopingOption())) {
                String warningMessage = """
                                An isolated polyglot context uses host access without host method scoping. Guest values passed to host methods may create cross-heap reference cycles that might not be reclaimable.
                                To resolve this, enable method scoping using HostAccess.Builder.methodScoping(true).
                                To disable this warning use the '--engine.WarnMethodScoping=false' option or the '-Dpolyglot.engine.WarnMethodScoping=false' system property.
                                """;
                PolyglotIsolateAccessor.ENGINE.getEngineLogger(engineReceiver).log(Level.WARNING, warningMessage);
            }
        }
        Context localContext = dispatch.createContext(engineReceiver, localEngine, sandboxPolicy, out, err, in, allowHostAccess, hostAccess, apiAccess.getPolyglotAccessAll(), allowNativeAccess,
                        allowCreateThread,
                        allowHostClassLoading,
                        allowInnerContextOptions, allowExperimentalOptions, classFilter, PolyglotIsolateAccessor.ENGINE.filterHostOptions(engineReceiver, options), Collections.emptyMap(),
                        EMPTY_LANGUAGES,
                        ioAccess, logHandler,
                        false, null, exceptionHandler, apiAccess.getEnvironmentAccessNone(), environment, null, null, currentWorkingDirectory, tmpDir, hostClassLoader, allowValueSharing,
                        useSystemExit, false);
        AbstractHostLanguageService localHostService = PolyglotIsolateAccessor.ENGINE.getHostService(apiAccess.getEngineReceiver(localEngine));
        Object localContextReceiver = apiAccess.getContextReceiver(localContext);
        AbstractContextDispatch localContextDispatch = apiAccess.getContextDispatch(localContext);
        LogHandler localContextLogHandler = PolyglotIsolateAccessor.ENGINE.getContextLogHandler(localContextReceiver);
        HostObjectReferences hostObjectReflection = HostObjectReferences.create();
        ProcessHandler useProcessHandler = processHandler != null ? processHandler : PolyglotIsolateAccessor.ENGINE.newDefaultProcessHandler();
        long contextHandle = foreignEngine.getPolyglotIsolateServices().createContext(foreignEngine, sandboxPolicy, out, err, in,
                        allowHostAccess, polyglotAccess, ioAccess, impl.getIO().getFileSystem(ioAccess), allowNativeAccess, allowCreateThread, allowHostClassLoading,
                        allowInnerContextOptions,
                        allowExperimentalOptions,
                        allowCreateProcess, options, arguments, onlyLanguages, currentWorkingDirectory, tmpDir, useProcessHandler, environmentAccess, environment,
                        zone != null ? zone : ZoneId.systemDefault(),
                        foreignEngine.getHostStackHeadRoom(), localHostService, allowValueSharing, useSystemExit, localContextLogHandler, hostObjectReflection);
        Peer contextPeer = Peer.create(foreignEngine.getPeer().getIsolate(), contextHandle);
        ReflectionLibraryDispatch guestObjectReflection = foreignEngine.getPolyglotIsolateServices().getGuestObjectReflection(ForeignObject.createUnbound(contextPeer));
        ForeignContext foreignContext = new ForeignContext(contextPeer, localContext, guestObjectReflection, hostObjectReflection,
                        foreignEngine.getPolyglotIsolateServices(), foreignEngine.getSourceCache());
        Context res = apiAccess.newContext(PolyglotIsolateHostSupport.getContextDispatch(foreignContext), foreignContext, engineApi, registerInActiveContexts);
        localContextDispatch.setContextAPIReference(localContextReceiver, foreignContext.getContextAPIReference());
        foreignEngine.onContextCreated(foreignContext);
        initializeSandboxInstrument(foreignContext, sandboxPolicy);
        if (registerInActiveContexts) {
            apiAccess.processReferenceQueue();
        }
        return res;
    }

    private void initializeSandboxInstrument(ForeignContext foreignContext, SandboxPolicy sandboxPolicy) {
        if (sandboxPolicy.isStricterOrEqual(SandboxPolicy.ISOLATED)) {
            try {
                foreignContext.getPolyglotIsolateServices().ensureInstrumentCreated(foreignContext, "sandbox");
            } catch (Exception e) {
                throw PolyglotIsolateAccessor.ENGINE.wrapGuestException(apiAccess.getContextReceiver(foreignContext.getLocalContext()), e);
            }
        }
    }

    @Override
    public final Set<Object> getCachedSources(Object receiver) {
        ForeignEngine foreignEngine = (ForeignEngine) receiver;
        return foreignEngine.getSourceCache().getCachedSources();
    }

    @Override
    public final String getImplementationName(Object receiver) {
        return String.format("%s Isolated", getImplementationNameImpl(receiver));
    }

    @ReceiverMethod("getImplementationName")
    abstract String getImplementationNameImpl(Object receiver);

    @Override
    public final Map<String, Object> getInstruments(Object receiver) {
        Map<String, Object> instruments = getInstrumentsImpl(receiver);
        ForeignEngine foreignEngine = (ForeignEngine) receiver;
        Engine engine = foreignEngine.getEngineAPI();
        AbstractInstrumentDispatch dispatch = PolyglotIsolateHostSupport.getInstrumentDispatch(foreignEngine);
        instruments.entrySet().forEach(
                        (e) -> e.setValue(PolyglotIsolateHostSupport.getPolyglot().getAPIAccess().newInstrument(dispatch, new ForeignInstrument(foreignEngine, (Peer) e.getValue()), engine)));
        return instruments;
    }

    @ReceiverMethod("getInstruments")
    @InstrumentPeerMap
    abstract Map<String, Object> getInstrumentsImpl(Object engine);

    @Override
    public final Map<String, Object> getLanguages(Object receiver) {
        Map<String, Object> languages = getLanguagesImpl(receiver);
        ForeignEngine foreignEngine = (ForeignEngine) receiver;
        Engine engine = foreignEngine.getEngineAPI();
        AbstractLanguageDispatch dispatch = PolyglotIsolateHostSupport.getLanguageDispatch(foreignEngine);
        languages.entrySet().forEach((e) -> e.setValue(PolyglotIsolateHostSupport.getPolyglot().getAPIAccess().newLanguage(dispatch, new ForeignLanguage(foreignEngine, (Peer) e.getValue()), engine)));
        return languages;
    }

    @ReceiverMethod("getLanguages")
    @PolyglotMarshallerConfig.LanguagePeerMap
    abstract Map<String, Object> getLanguagesImpl(Object engine);

    @Override
    public final Object attachExecutionListener(Object engine, Consumer<Object> onEnter, Consumer<Object> onReturn, boolean expressions, boolean statements, boolean roots,
                    Predicate<Object> sourceFilter, Predicate<String> rootFilter, boolean collectInputValues, boolean collectReturnValues, boolean collectExceptions) {
        ForeignEngine foreignEngine = (ForeignEngine) engine;
        AbstractEngineDispatch localEngineDispatch = apiAccess.getEngineDispatch(foreignEngine.getLocalEngine());
        Object localEngineReceiver = apiAccess.getEngineReceiver(foreignEngine.getLocalEngine());
        Object hostListener = localEngineDispatch.attachExecutionListener(localEngineReceiver, onEnter, onReturn, expressions,
                        statements, roots, sourceFilter, rootFilter, collectInputValues, collectReturnValues, collectExceptions);
        // We have to create the HostException during consumer call to prevent the host -> isolate
        // -> host round trip, which can lose the exception type for exceptions that are not
        // supported by the ThrowableMarshaller.
        Consumer<Object> useOnEnter = toHostExceptionConsumer(onEnter, localEngineDispatch, localEngineReceiver);
        Consumer<Object> useOnReturn = toHostExceptionConsumer(onReturn, localEngineDispatch, localEngineReceiver);
        Peer isolatedListenerPeer = (Peer) attachExecutionListenerImpl(foreignEngine, useOnEnter, useOnReturn, expressions, statements, roots,
                        sourceFilter, rootFilter, collectInputValues, collectReturnValues, collectExceptions);
        return impl.getManagement().newExecutionListener(PolyglotIsolateHostSupport.getExecutionListenerDispatch(foreignEngine),
                        new ForeignExecutionListener(foreignEngine, (AutoCloseable) hostListener, isolatedListenerPeer),
                        foreignEngine.getEngineAPI());
    }

    /**
     * Decorates the given {@code consumer} by an instance that translates exception thrown by the
     * {@code consumer} into a {@code HostException}. We have to create the {@code HostException}
     * during {@code consumer} call to prevent the host -> isolate -> host round trip, which can
     * lose the exception type for exceptions that are not supported by the
     * {@code ThrowableMarshaller}. The created consumer captures local engine rather than
     * {@link ForeignEngine} to prevent cycles among object in host and isolate heap.
     *
     */
    private static Consumer<Object> toHostExceptionConsumer(Consumer<Object> consumer, AbstractEngineDispatch localEngineDispatch, Object localEngineReceiver) {
        if (consumer != null) {
            return (event) -> {
                try {
                    consumer.accept(event);
                } catch (Throwable throwable) {
                    throw localEngineDispatch.hostToGuestException(localEngineReceiver, throwable);
                }
            };
        } else {
            return null;
        }
    }

    @ReceiverMethod("attachExecutionListener")
    @ByRemoteReference(Peer.class)
    abstract Object attachExecutionListenerImpl(Object engine,
                    @ByLocalReference(ForeignExecutionEventConsumer.class) Consumer<Object> onEnter,
                    @ByLocalReference(ForeignExecutionEventConsumer.class) Consumer<Object> onReturn,
                    boolean expressions,
                    boolean statements,
                    boolean roots,
                    @ByLocalReference(ForeignSourcePredicate.class) Predicate<Object> sourceFilter,
                    @ByLocalReference(ForeignStringPredicate.class) Predicate<String> rootFilter,
                    boolean collectInputValues,
                    boolean collectReturnValues,
                    boolean collectExceptions);

    @Override
    public final Object requirePublicLanguage(Object receiver, String id) {
        ForeignEngine foreignEngine = (ForeignEngine) receiver;
        Peer languagePeer = (Peer) requirePublicLanguageImpl(receiver, id);
        ForeignLanguage foreignLanguage = new ForeignLanguage(foreignEngine, languagePeer);
        Engine engine = foreignEngine.getEngineAPI();
        return PolyglotIsolateHostSupport.getPolyglot().getAPIAccess().newLanguage(PolyglotIsolateHostSupport.getLanguageDispatch(foreignEngine), foreignLanguage, engine);
    }

    @ReceiverMethod("requirePublicLanguage")
    @ByRemoteReference(Peer.class)
    abstract Object requirePublicLanguageImpl(Object receiver, String id);

    @Override
    public final Object requirePublicInstrument(Object receiver, String id) {
        ForeignEngine foreignEngine = (ForeignEngine) receiver;
        Peer instrumentPeer = (Peer) requirePublicInstrumentImpl(receiver, id);
        ForeignInstrument foreignInstrument = new ForeignInstrument(foreignEngine, instrumentPeer);
        Engine engine = foreignEngine.getEngineAPI();
        return PolyglotIsolateHostSupport.getPolyglot().getAPIAccess().newInstrument(PolyglotIsolateHostSupport.getInstrumentDispatch(foreignEngine), foreignInstrument, engine);
    }

    @ReceiverMethod("requirePublicInstrument")
    @ByRemoteReference(Peer.class)
    abstract Object requirePublicInstrumentImpl(Object receiver, String id);

    @Override
    public final void shutdown(Object engine) {
        // The shutdown method is called by the Java shutdown hook concurrently to the running
        // application. The application may close the engine in the meantime. We try to enter the
        // isolate. If the enter throws an IllegalStateException we don't have to do anything
        // because the engine and the isolate are already closed.
        IsolateThread isolateThread = ((ForeignEngine) engine).getPeer().getIsolate().tryEnter();
        // Check for null, the engine's isolate may be already closed.
        if (isolateThread != null) {
            try {
                shutdownImpl(engine);
            } finally {
                isolateThread.leave();
            }
        }
    }

    @ReceiverMethod("shutdown")
    abstract void shutdownImpl(Object engine);

    @Override
    public final RuntimeException hostToGuestException(Object engineReceiver, Throwable throwable) {
        ForeignEngine engine = (ForeignEngine) engineReceiver;
        Object localEngine = engine.getLocalEngine();
        AbstractEngineDispatch localEngineDispatch = apiAccess.getEngineDispatch(localEngine);
        Object localEngineReceiver = apiAccess.getEngineReceiver(localEngine);
        return localEngineDispatch.hostToGuestException(localEngineReceiver, throwable);
    }

    @Override
    public final void onEngineCollected(Object engineReceiver) {
        ForeignEngine foreignEngine = (ForeignEngine) engineReceiver;
        Isolate<?> isolate = foreignEngine.getPeer().getIsolate();
        IsolateThread isolateThread = isolate.tryEnter();
        /*
         * The order of references enqueued in the ReferenceQueue is not deterministic. As a result,
         * the context can be polled before a bound engine, and the bound engine and isolate may be
         * already closed as part of context close. We need to use tryEnter to prevent entering a
         * disposed isolate.
         */
        boolean isolateValid = isolateThread != null;
        if (isolateValid) {
            try {
                onEngineCollectedImpl(engineReceiver);
            } catch (IsolateDeathException isolateDeath) {
                isolateValid = false;
            } finally {
                isolateThread.leave();
            }
        }
        if (!isolateValid) {
            // The isolate is no more available try to clean the local context and call
            // onIsolateTearDown hook
            Object localEngineReceiver = apiAccess.getEngineReceiver(foreignEngine.getLocalEngine());
            PolyglotIsolateAccessor.ENGINE.closeEngine(localEngineReceiver, false);
            foreignEngine.getPeer().getIsolate().shutdown();
        }
    }

    @Override
    public final boolean storeCache(Object receiver, Path targetFile, long cancelledWord) {
        if (cancelledWord != 0 && ((ForeignEngine) receiver).getPeer() instanceof ProcessPeer) {
            throw new UnsupportedOperationException("Passing a controlWorld when storing the cache of an engine is not supported with external isolate isolation. Set it to null to resolve this.");
        }
        return storeCacheImpl(receiver, targetFile, cancelledWord);
    }

    @ReceiverMethod("storeCache")
    abstract boolean storeCacheImpl(Object receiver, Path targetFile, long cancelledWord);

    @Override
    public final ByteBuffer persistCache(Object receiver, Engine.CancellationCallback callback) {
        throw new UnsupportedOperationException("Persisting the cache of an engine is not supported with polyglot isolate isolation.");
    }

    @ReceiverMethod("onEngineCollected")
    @IsolateDeathHandler(IsolateDeathHandlerSupport.KeepIsolateDeathException.class)
    abstract void onEngineCollectedImpl(Object engine);

    @CustomDispatchAccessor
    static AbstractEngineDispatch resolveDelegate(GuestEngine guestEngine) {
        return PolyglotIsolateHostSupport.getPolyglot().getAPIAccess().getEngineDispatch(guestEngine.engine);
    }

    @CustomReceiverAccessor
    static Object resolveReceiver(GuestEngine guestEngine) {
        return PolyglotIsolateHostSupport.getPolyglot().getAPIAccess().getEngineReceiver(guestEngine.engine);
    }

    static final class AsCancelledPolyglotException {

        private AsCancelledPolyglotException() {
        }

        static void handleIsolateDeath(Object receiver, IsolateDeathException isolateDeath) {
            throw IsolateDeathHandlerSupport.createCancelledPolyglotException((ForeignEngine) receiver, isolateDeath);
        }
    }
}
