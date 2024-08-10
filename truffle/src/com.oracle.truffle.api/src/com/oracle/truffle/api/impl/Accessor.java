/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.HostAccess.TargetMappingPrecedence;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractHostAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractHostLanguageService;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.LogHandler;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.MessageTransport;
import org.graalvm.polyglot.io.ProcessHandler;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ContextLocal;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.InternalResource;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleFile.FileTypeDetector;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.TruffleSafepoint.Interrupter;
import com.oracle.truffle.api.TruffleSafepoint.InterruptibleFunction;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.io.TruffleProcessBuilder;
import com.oracle.truffle.api.nodes.BlockNode;
import com.oracle.truffle.api.nodes.BlockNode.ElementExecutor;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.ExecutionSignature;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.provider.InternalResourceProvider;
import com.oracle.truffle.api.provider.TruffleLanguageProvider;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.Source.SourceBuilder;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Communication between TruffleLanguage API/SPI, and other services.
 * <p>
 * All subclasses should be named "...Accessor" and should be top-level classes. They should have a
 * private constructor and a singleton instance in a static field named ACCESSOR, under the
 * "...Accessor" class. The implementation class "...Impl" extending "Accessor.<...>Support" should
 * be a static class nested under the "...Accessor" class. This is important to avoid cycles during
 * classloading and be able to initialize this class reliably.
 */
@SuppressWarnings({"deprecation", "static-method"})
public abstract class Accessor {

    protected void initializeNativeImageTruffleLocator() {
        TruffleLocator.initializeNativeImageTruffleLocator();
    }

    abstract static class Support {

        Support(String onlyAllowedClassName) {
            if (!getClass().getName().equals(onlyAllowedClassName)) {
                throw new AssertionError("No custom subclasses of support classes allowed. Implementation must be " + onlyAllowedClassName + ".");
            }
        }

    }

    public abstract static class NodeSupport extends Support {

        static final String IMPL_CLASS_NAME = "com.oracle.truffle.api.nodes.NodeAccessor$AccessNodes";

        protected NodeSupport() {
            super(IMPL_CLASS_NAME);
        }

        public abstract Lookup nodeLookup();

        public abstract boolean isInstrumentable(RootNode rootNode);

        public abstract boolean isCloneUninitializedSupported(RootNode rootNode);

        public abstract RootNode cloneUninitialized(CallTarget sourceCallTarget, RootNode rootNode, RootNode uninitializedRootNode);

        public abstract int adoptChildrenAndCount(RootNode rootNode);

        public abstract int computeSize(RootNode rootNode);

        public abstract Object getLanguageCache(LanguageInfo languageInfo);

        public abstract TruffleLanguage<?> getLanguage(RootNode languageInfo);

        public abstract LanguageInfo createLanguage(Object cache, String id, String name, String version, String defaultMimeType, Set<String> mimeTypes, boolean internal,
                        boolean interactive);

        public abstract Object getSharingLayer(RootNode rootNode);

        public abstract List<TruffleStackTraceElement> findAsynchronousFrames(CallTarget target, Frame frame);

        public abstract int getRootNodeBits(RootNode root);

        public abstract void setRootNodeBits(RootNode root, int bits);

        public abstract Lock getLock(Node node);

        public abstract void applySharingLayer(RootNode from, RootNode to);

        public abstract void forceAdoption(Node parent, Node child);

        public abstract boolean isTrivial(RootNode rootNode);

        public abstract FrameDescriptor getParentFrameDescriptor(RootNode rootNode);

        public abstract Object translateStackTraceElement(TruffleStackTraceElement stackTraceLement);

        public abstract ExecutionSignature prepareForAOT(RootNode rootNode);

        public abstract void setSharingLayer(RootNode rootNode, Object engine);

        public abstract boolean countsTowardsStackTraceLimit(RootNode rootNode);

        public abstract CallTarget getCallTargetWithoutInitialization(RootNode root);

        public abstract EncapsulatingNodeReference createEncapsulatingNodeReference(Thread thread);
    }

    public abstract static class SourceSupport extends Support {

        static final String IMPL_CLASS_NAME = "com.oracle.truffle.api.source.SourceAccessor$SourceSupportImpl";

        protected SourceSupport() {
            super(IMPL_CLASS_NAME);
        }

        public abstract Object getSourceIdentifier(Source source);

        public abstract Source copySource(Source source);

        public abstract Object getOrCreatePolyglotSource(Source source,
                        Function<Source, Object> createSource);

        public abstract String findMimeType(URL url, Object fileSystemContext) throws IOException;

        public abstract SourceBuilder newBuilder(String language, File origin);

        public abstract void setFileSystemContext(SourceBuilder builder, Object fileSystemContext);

        public abstract void invalidateAfterPreinitialiation(Source source);

        public abstract void mergeLoadedSources(Source[] sources);

        public abstract void setEmbedderSource(SourceBuilder builder, boolean b);

        public abstract void setURL(SourceBuilder builder, URL url);

        public abstract void setPath(SourceBuilder builder, String path);
    }

    public abstract static class InteropSupport extends Support {

        static final String IMPL_CLASS_NAME = "com.oracle.truffle.api.interop.InteropAccessor$InteropImpl";

        protected InteropSupport() {
            super(IMPL_CLASS_NAME);
        }

        public abstract boolean isTruffleObject(Object value);

        public abstract void checkInteropType(Object result);

        public abstract boolean isExecutableObject(Object value);

        public abstract Object createDefaultNodeObject(Node node);

        public abstract boolean isScopeObject(Object receiver);

        public abstract Object createDefaultIterator(Object receiver);

        public abstract Node createDispatchedInteropLibrary(int limit);

        public abstract Node getUncachedInteropLibrary();

        public abstract long unboxPointer(Node library, Object value);
    }

    public abstract static class HostSupport extends Support {

        static final String IMPL_CLASS_NAME = "com.oracle.truffle.host.HostAccessor$HostImpl";

        protected HostSupport() {
            super(IMPL_CLASS_NAME);
        }

        public abstract TruffleLanguage<?> createDefaultHostLanguage(AbstractPolyglotImpl polyglot, AbstractHostAccess access);

        public abstract boolean isHostBoundaryValue(Object value);

        public abstract Object convertPrimitiveLossLess(Object value, Class<?> requestedType);

        public abstract Object convertPrimitiveLossy(Object value, Class<?> requestedType);

        public abstract boolean isDisconnectedHostProxy(Object value);

        public abstract boolean isDisconnectedHostObject(Object obj);

        public abstract Object unboxDisconnectedHostObject(Object hostValue);

        public abstract Object unboxDisconnectedHostProxy(Object hostValue);

        public abstract Object toDisconnectedHostObject(Object hostValue);

        public abstract Object toDisconnectedHostProxy(Object hostValue);

        public abstract <S, T> Object newTargetTypeMapping(Class<S> sourceType, Class<T> targetType, Predicate<S> acceptsValue, Function<S, T> convertValue, TargetMappingPrecedence precedence);

        public abstract Object getHostNull();

        public abstract boolean isPrimitiveTarget(Class<?> c);

        public abstract boolean isGuestToHostRootNode(RootNode root);

        public abstract boolean isHostLanguage(Class<?> languageClass);

        public abstract Node inlineToHostNode(Object target);

        public abstract boolean bigIntegerFitsInFloat(BigInteger b);

        public abstract boolean bigIntegerFitsInDouble(BigInteger b);
    }

    public abstract static class EngineSupport extends Support {

        static final String IMPL_CLASS_NAME = "com.oracle.truffle.polyglot.EngineAccessor$EngineImpl";

        protected EngineSupport() {
            super(IMPL_CLASS_NAME);
        }

        public abstract <T> Iterable<T> loadServices(Class<T> type);

        public abstract Object getInstrumentationHandler(Object polyglotObject);

        public abstract Object getInstrumentationHandler(RootNode rootNode);

        public abstract void exportSymbol(Object polyglotLanguageContext, String symbolName, Object value);

        public abstract Object getPolyglotBindingsObject();

        public abstract Object importSymbol(Object polyglotLanguageContext, Env env, String symbolName);

        public abstract boolean isMimeTypeSupported(Object polyglotLanguageContext, String mimeType);

        public abstract boolean isEvalRoot(RootNode target);

        public abstract boolean isMultiThreaded(Object guestObject);

        @SuppressWarnings("static-method")
        public final void attachOutputConsumer(DispatchOutputStream dos, OutputStream out) {
            dos.attach(out);
        }

        @SuppressWarnings("static-method")
        public final void detachOutputConsumer(DispatchOutputStream dos, OutputStream out) {
            dos.detach(out);
        }

        public abstract Object getCurrentSharingLayer();

        public abstract Object getCurrentPolyglotEngine();

        public abstract CallTarget parseForLanguage(Object sourceLanguageContext, Source source, String[] argumentNames, boolean allowInternal);

        public abstract Env getEnvForInstrument(String languageId, String mimeType);

        public abstract Env getEnvForInstrument(LanguageInfo language);

        public abstract boolean hasCurrentContext();

        public abstract boolean isDisposed(Object polyglotLanguageContext);

        public abstract Map<String, LanguageInfo> getInternalLanguages(Object polyglotObject);

        public abstract Map<String, LanguageInfo> getPublicLanguages(Object polyglotObject);

        public abstract Map<String, InstrumentInfo> getInstruments(Object polyglotObject);

        public abstract boolean isInstrumentReadyForContextEvents(Object polyglotInstrument);

        public abstract Object createPolyglotSourceSection(Object polyglotObject, Object source, SourceSection sectionImpl);

        public abstract <T> T lookup(InstrumentInfo info, Class<T> serviceClass);

        public abstract <S> S lookup(LanguageInfo language, Class<S> type);

        public abstract <T extends TruffleLanguage<?>> T getCurrentLanguage(Class<T> languageClass);

        public abstract <C, T extends TruffleLanguage<C>> C getCurrentContext(Class<T> languageClass);

        public abstract TruffleContext getTruffleContext(Object polyglotLanguageContext);

        public abstract TruffleContext getCurrentCreatorTruffleContext();

        public abstract Object toGuestValue(Node node, Object obj, Object languageContext);

        public abstract Object getPolyglotEngine(Object polyglotLanguageInstance);

        public abstract Object getPolyglotSharingLayer(Object polyglotLanguageInstance);

        public abstract Object lookupHostSymbol(Object polyglotLanguageContext, Env env, String symbolName);

        public abstract Object asHostSymbol(Object polyglotLanguageContext, Class<?> symbolClass);

        public abstract boolean isHostAccessAllowed(Object polyglotLanguageContext, Env env);

        public abstract boolean isNativeAccessAllowed(Object polyglotLanguageContext, Env env);

        public abstract boolean isInnerContextOptionsAllowed(Object polyglotLanguageContext, Env env);

        public abstract boolean isCurrentNativeAccessAllowed(Node node);

        public abstract boolean inContextPreInitialization(Object polyglotObject);

        public abstract TruffleContext createInternalContext(Object sourcePolyglotLanguageContext, OutputStream out, OutputStream err, InputStream in,
                        ZoneId timeZone, String[] permittedLanguages, Map<String, Object> config, Map<String, String> options, Map<String, String[]> arguments,
                        Boolean sharingEnabled, boolean initializeCreatorContext, Runnable onCancelled, Consumer<Integer> onExited,
                        Runnable onClosed, boolean inheritAccess, Boolean allowCreateThreads, Boolean allowNativeAccess, Boolean allowIO,
                        Boolean allowHostLookup, Boolean allowHostClassLoading, Boolean allowCreateProcess, Boolean allowPolyglotAccess,
                        Boolean allowEnvironmentAccess, Map<String, String> environment, Boolean allowInnerContextOptions);

        public abstract Object enterInternalContext(Node node, Object polyglotContext);

        public abstract void leaveInternalContext(Node node, Object polyglotContext, Object prev);

        public abstract Object[] enterContextAsPolyglotThread(Object polyglotContext);

        public abstract void leaveContextAsPolyglotThread(Object polyglotContext, Object[] prev);

        public abstract <T, R> R leaveAndEnter(Object polyglotContext, Interrupter interrupter, InterruptibleFunction<T, R> runWhileOutsideContext, T object);

        public abstract Object enterIfNeeded(Object polyglotContext);

        public abstract void leaveIfNeeded(Object polyglotContext, Object prev);

        public abstract boolean initializeInnerContext(Node location, Object polyglotContext, String languageId, boolean allowInternal);

        public abstract Object evalInternalContext(Node node, Object polyglotContext, Source source, boolean allowInternal);

        public abstract void clearExplicitContextStack(Object polyglotContext);

        public abstract void initiateCancelOrExit(Object polyglotContext, boolean exit, int exitCode, boolean resourceLimit, String message);

        public abstract void closeContext(Object polyglotContext, boolean force, Node closeLocation, boolean resourceExhaused, String resourceExhausedReason);

        public abstract void closeContext(Object polyglotContext, boolean force, boolean resourceExhaused, String resourceExhausedReason);

        public abstract void closeEngine(Object polyglotEngine, boolean force);

        public abstract void exitContext(Object polyglotContext, Node exitLocation, int exitCode);

        public abstract boolean isContextEntered(Object polyglotContext);

        public abstract boolean isContextActive(Object polyglotContext);

        public abstract void reportAllLanguageContexts(Object polyglotEngine, Object contextsListener);

        public abstract void reportAllContextThreads(Object polyglotEngine, Object threadsListener);

        public abstract TruffleContext getParentContext(Object polyglotContext);

        public abstract boolean isCreateThreadAllowed(Object polyglotLanguageContext);

        public abstract Thread createThread(Object polyglotLanguageContext, Runnable runnable, Object innerContextImpl, ThreadGroup group, long stackSize, Runnable beforeEnter, Runnable afterLeave);

        public abstract RuntimeException wrapHostException(Node callNode, Object languageContext, Throwable exception);

        public abstract boolean isHostException(Object polyglotLanguageContext, Throwable exception);

        public abstract Throwable asHostException(Object polyglotLanguageContext, Throwable exception);

        public abstract Object getCurrentHostContext();

        public abstract RuntimeException wrapGuestException(Object polyglotObject, Throwable e);

        public abstract RuntimeException wrapGuestException(String languageId, Throwable exception);

        public abstract <T> T getOrCreateRuntimeData(Object polyglotEngine);

        public abstract Set<? extends Class<?>> getProvidedTags(LanguageInfo language);

        public abstract Object getPolyglotBindingsForLanguage(Object polyglotLanguageContext);

        public abstract Object findMetaObjectForLanguage(Object polyglotLanguageContext, Object value);

        public abstract boolean isInternal(Object engineObject, FileSystem fs);

        public abstract boolean hasNoAccess(FileSystem fs);

        public abstract boolean isSocketIOAllowed(Object engineFileSystemContext);

        public abstract boolean isInternal(TruffleFile file);

        public abstract String getLanguageHome(LanguageInfo languageInfo);

        public abstract void addToHostClassPath(Object polyglotLanguageContext, TruffleFile entries);

        public abstract boolean isInstrumentExceptionsAreThrown(Object polyglotInstrument);

        public abstract Object asBoxedGuestValue(Object guestObject, Object polyglotLanguageContext);

        public abstract Object createDefaultLoggerCache();

        public abstract Object getContextLoggerCache(Object polyglotLanguageContext);

        public abstract void publish(Object loggerCache, LogRecord logRecord);

        public abstract Map<String, Level> getLogLevels(Object loggerCache);

        public abstract Object getLoggerOwner(Object loggerCache);

        public abstract TruffleLogger getLogger(Object polyglotInstrument, String name);

        public abstract LogRecord createLogRecord(Object loggerCache, Level level, String loggerName, String message, String className, String methodName, Object[] parameters, Throwable thrown);

        public abstract Object getOuterContext(Object polyglotContext);

        public abstract boolean isCharacterBasedSource(Object fsEngineObject, String language, String mimeType);

        public abstract Set<String> getValidMimeTypes(Object engineObject, String language);

        public abstract Object asHostObject(Object languageContext, Object value);

        public abstract boolean isHostObject(Object languageContext, Object value);

        public abstract boolean isHostFunction(Object languageContext, Object value);

        public abstract boolean isHostSymbol(Object languageContext, Object guestObject);

        public abstract <S> S lookupService(Object polyglotLanguageContext, LanguageInfo language, LanguageInfo accessingLanguage, Class<S> type);

        public abstract <T extends TruffleLanguage<C>, C> ContextReference<C> createContextReference(Class<T> languageClass);

        public abstract <T extends TruffleLanguage<?>> LanguageReference<T> createLanguageReference(Class<T> targetLanguageClass);

        public abstract FileSystem getFileSystem(Object polyglotContext);

        public abstract boolean isPolyglotEvalAllowed(Object polyglotLanguageContext);

        public abstract boolean isPolyglotBindingsAccessAllowed(Object polyglotLanguageContext);

        public abstract TruffleFile getTruffleFile(TruffleContext truffleContext, String path);

        public abstract TruffleFile getTruffleFile(TruffleContext truffleContext, URI uri);

        public abstract int getAsynchronousStackDepth(Object polylgotLanguageInstance);

        public abstract void setAsynchronousStackDepth(Object polyglotInstrument, int depth);

        public abstract boolean isCreateProcessAllowed(Object polylgotLanguageContext);

        public abstract Map<String, String> getProcessEnvironment(Object polyglotLanguageContext);

        public abstract Process createSubProcess(Object polyglotLanguageContext, List<String> cmd, String cwd, Map<String, String> environment, boolean redirectErrorStream,
                        ProcessHandler.Redirect inputRedirect, ProcessHandler.Redirect outputRedirect, ProcessHandler.Redirect errorRedirect) throws IOException;

        public abstract boolean hasDefaultProcessHandler(Object polyglotLanguageContext);

        public abstract boolean isIOAllowed(Object polyglotLanguageContext, Env env);

        public abstract boolean isIOSupported();

        public abstract boolean isCreateProcessSupported();

        public abstract ZoneId getTimeZone(Object polyglotLanguageContext);

        public abstract Set<String> getLanguageIds();

        public abstract Set<String> getInstrumentIds();

        public abstract Set<String> getInternalIds();

        public abstract String getUnparsedOptionValue(OptionValues optionValues, OptionKey<?> optionKey);

        public abstract String getRelativePathInResourceRoot(TruffleFile truffleFile);

        public abstract void onSourceCreated(Source source);

        public abstract void registerOnDispose(Object engineObject, Closeable closeable);

        public abstract String getReinitializedPath(TruffleFile truffleFile);

        public abstract URI getReinitializedURI(TruffleFile truffleFile);

        public abstract LanguageInfo getLanguageInfo(Object polyglotInstrument, Class<? extends TruffleLanguage<?>> languageClass);

        public abstract Object getDefaultLanguageView(TruffleLanguage<?> truffleLanguage, Object value);

        public abstract Object getLanguageView(LanguageInfo viewLanguage, Object value);

        public abstract boolean initializeLanguage(Object polyglotLanguageContext, LanguageInfo targetLanguage);

        public abstract RuntimeException engineToLanguageException(Throwable t);

        public abstract RuntimeException engineToInstrumentException(Throwable t);

        public abstract Object getCurrentFileSystemContext();

        public abstract Object getPublicFileSystemContext(Object polyglotContextImpl);

        public abstract Object getInternalFileSystemContext(Object polyglotContextImpl);

        public abstract Map<String, Collection<? extends FileTypeDetector>> getEngineFileTypeDetectors(Object engineFileSystemContext);

        public abstract boolean skipEngineValidation(RootNode rootNode);

        public abstract AssertionError invalidSharingError(Node node, Object previousSharingLayer, Object newSharingLayer) throws AssertionError;

        public abstract boolean isPolyglotSecret(Object polyglotObject);

        public abstract void initializeLanguageContextLocal(List<? extends ContextLocal<?>> local, Object polyglotLanguageInstance);

        public abstract void initializeLanguageContextThreadLocal(List<? extends ContextThreadLocal<?>> local, Object polyglotLanguageInstance);

        public abstract void initializeInstrumentContextLocal(List<? extends ContextLocal<?>> local, Object polyglotInstrument);

        public abstract void initializeInstrumentContextThreadLocal(List<? extends ContextThreadLocal<?>> local, Object polyglotInstrument);

        public abstract <T> ContextThreadLocal<T> createLanguageContextThreadLocal(Object factory);

        public abstract <T> ContextThreadLocal<T> createInstrumentContextThreadLocal(Object factory);

        public abstract <T> ContextLocal<T> createLanguageContextLocal(Object factory);

        public abstract <T> ContextLocal<T> createInstrumentContextLocal(Object factory);

        public abstract OptionValues getInstrumentContextOptions(Object polyglotInstrument, Object polyglotContext);

        public abstract boolean isContextClosed(Object polyglotContext);

        public abstract boolean isContextCancelling(Object polyglotContext);

        public abstract boolean isContextExiting(Object polyglotContext);

        public abstract Future<Void> pause(Object polyglotContext);

        public abstract void resume(Object polyglotContext, Future<Void> pauseFuture);

        public abstract <T, G> Iterator<T> mergeHostGuestFrames(Object polyglotEngine, StackTraceElement[] hostStack, Iterator<G> guestFrames, boolean inHostLanguage,
                        boolean includeHostFrames, Function<StackTraceElement, T> hostFrameConvertor, Function<G, T> guestFrameConvertor);

        public abstract boolean isHostToGuestRootNode(RootNode root);

        public abstract Object createHostAdapterClass(Object polyglotLanguageContext, Object[] types, Object classOverrides);

        public abstract OptionValues getEngineOptionValues(Object polyglotEngine);

        public abstract Collection<? extends CallTarget> findCallTargets(Object polyglotEngine);

        public abstract void preinitializeContext(Object polyglotEngine);

        public abstract void finalizeStore(Object polyglotEngine);

        public abstract Object getEngineLock(Object polyglotEngine);

        public abstract long calculateContextHeapSize(Object polyglotContext, long stopAtBytes, AtomicBoolean cancelled);

        public abstract Future<Void> submitThreadLocal(Object polyglotLanguageContext, Object sourcePolyglotObject, Thread[] threads, ThreadLocalAction action, boolean needsEnter);

        public abstract Object getContext(Object polyglotLanguageContext);

        public abstract Object getStaticObjectClassLoaders(Object polyglotLanguageInstance, Class<?> referenceClass);

        public abstract void setStaticObjectClassLoaders(Object polyglotLanguageInstance, Class<?> referenceClass, Object value);

        public abstract ConcurrentHashMap<Pair<Class<?>, Class<?>>, Object> getGeneratorCache(Object polyglotLanguageInstance);

        public abstract boolean areStaticObjectSafetyChecksRelaxed(Object polyglotLanguageInstance);

        public abstract String getStaticObjectStorageStrategy(Object polyglotLanguageInstance);

        public abstract Object getHostContext(Object valueContext);

        public abstract Object asValue(Object polyglotContextImpl, Object guestValue);

        public abstract Object enterLanguageFromRuntime(TruffleLanguage<?> language);

        public abstract void leaveLanguageFromRuntime(TruffleLanguage<?> language, Object prev);

        public abstract Object enterRootNodeVisit(RootNode root);

        public abstract void leaveRootNodeVisit(RootNode root, Object prev);

        public abstract Throwable getPolyglotExceptionCause(Object polyglotExceptionImpl);

        public abstract Object getPolyglotExceptionContext(Object polyglotExceptionImpl);

        public abstract Object getPolyglotExceptionEngine(Object polyglotExceptionImpl);

        public abstract boolean isCancelExecution(Throwable throwable);

        public abstract boolean isExitException(Throwable throwable);

        public abstract boolean isInterruptExecution(Throwable throwable);

        public abstract boolean isResourceLimitCancelExecution(Throwable cancelExecution);

        public abstract boolean isPolyglotEngineException(Throwable throwable);

        public abstract Throwable getPolyglotEngineExceptionCause(Throwable engineException);

        public abstract RuntimeException createPolyglotEngineException(RuntimeException cause);

        public abstract int getExitExceptionExitCode(Throwable cancelExecution);

        public abstract SourceSection getCancelExecutionSourceLocation(Throwable cancelExecution);

        public abstract ThreadDeath createCancelExecution(SourceSection sourceSection, String message, boolean resourceLimit);

        public abstract SourceSection getExitExceptionSourceLocation(Throwable cancelExecution);

        public abstract ThreadDeath createExitException(SourceSection sourceSection, String message, int exitCode);

        public abstract Throwable createInterruptExecution(SourceSection sourceSection);

        public abstract AbstractHostLanguageService getHostService(Object polyglotEngineImpl);

        public abstract LogHandler getEngineLogHandler(Object polyglotEngineImpl);

        public abstract LogHandler getContextLogHandler(Object polyglotContextImpl);

        public abstract LogRecord createLogRecord(Level level, String loggerName, String message, String className, String methodName, Object[] parameters, Throwable thrown, String formatKind);

        public abstract String getFormatKind(LogRecord logRecord);

        public abstract boolean isPolyglotThread(Thread thread);

        public abstract Object getHostNull();

        public abstract Object getGuestToHostCodeCache(Object polyglotContextImpl);

        public abstract Object installGuestToHostCodeCache(Object polyglotContextImpl, Object cache);

        public abstract boolean getNeedsAllEncodings();

        public abstract boolean requireLanguageWithAllEncodings(Object encoding);

        public abstract AutoCloseable createPolyglotThreadScope();

        public abstract Object getPolyglotEngineAPI(Object polyglotEngineImpl);

        public abstract Object getPolyglotContextAPI(Object polyglotContextImpl);

        public abstract EncapsulatingNodeReference getEncapsulatingNodeReference(boolean invalidateOnNull);

        public abstract Thread createInstrumentSystemThread(Object polyglotInstrument, Runnable runnable, ThreadGroup threadGroup);

        public abstract Thread createLanguageSystemThread(Object polyglotLanguageContext, Runnable runnable, ThreadGroup threadGroup);

        public abstract Object getEngineFromPolyglotObject(Object polyglotObject);

        public abstract SandboxPolicy getContextSandboxPolicy(Object polyglotLanguageContext);

        public abstract SandboxPolicy getEngineSandboxPolicy(Object polyglotInstrument);

        public abstract void ensureInstrumentCreated(Object polyglotContextImpl, String instrumentId);

        public abstract TruffleFile getInternalResource(Object owner, Class<? extends InternalResource> resourceType) throws IOException;

        public abstract TruffleFile getInternalResource(Object owner, String resourceId) throws IOException;

        public abstract Path getEngineResource(Object polyglotEngine, String resourceId) throws IOException;

        public abstract Collection<String> getResourceIds(String componentId);

        public abstract void setIsolatePolyglot(AbstractPolyglotImpl instance);

        public abstract long getEngineId(Object polyglotEngine);
    }

    public abstract static class LanguageSupport extends Support {

        static final String IMPL_CLASS_NAME = "com.oracle.truffle.api.LanguageAccessor$LanguageImpl";

        protected LanguageSupport() {
            super(IMPL_CLASS_NAME);
        }

        public abstract void initializeLanguage(TruffleLanguage<?> impl, LanguageInfo language, Object polyglotLanguage, Object polyglotLanguageInstance);

        public abstract Env createEnv(Object polyglotLanguageContext, TruffleLanguage<?> language, OutputStream stdOut, OutputStream stdErr, InputStream stdIn, Map<String, Object> config,
                        OptionValues options,
                        String[] applicationArguments);

        public abstract boolean areOptionsCompatible(TruffleLanguage<?> language, OptionValues firstContextOptions, OptionValues newContextOptions);

        public abstract Object createEnvContext(Env localEnv, List<Object> servicesCollector);

        public abstract TruffleContext createTruffleContext(Object impl, boolean creator);

        public abstract void postInitEnv(Env env);

        public abstract Object evalInContext(Source source, Node node, MaterializedFrame frame);

        public abstract void dispose(Env env);

        public abstract LanguageInfo getLanguageInfo(TruffleLanguage.Env env);

        public abstract LanguageInfo getLanguageInfo(TruffleLanguage<?> language);

        public abstract Object getPolyglotLanguageInstance(TruffleLanguage<?> language);

        public abstract CallTarget parse(Env env, Source code, Node context, String... argumentNames);

        public abstract ExecutableNode parseInline(Env env, Source code, Node context, MaterializedFrame frame);

        public abstract boolean isVisible(Env env, Object value);

        public abstract Object getContext(Env env);

        public abstract Object getPolyglotLanguageContext(Env env);

        public abstract TruffleLanguage<?> getSPI(Env env);

        public abstract InstrumentInfo createInstrument(Object polyglotInstrument, String id, String name, String version);

        public abstract Object getPolyglotInstrument(InstrumentInfo info);

        public abstract boolean isContextInitialized(Env env);

        public abstract OptionDescriptors describeOptions(TruffleLanguage<?> language, String requiredGroup);

        public abstract void addStackFrameInfo(Node callNode, RootCallTarget root, Throwable e, Frame frame);

        public abstract boolean isThreadAccessAllowed(Env env, Thread current, boolean singleThread);

        public abstract void initializeThread(Env env, Thread current);

        public abstract void initializeMultiThreading(Env env);

        public abstract void finalizeThread(Env env, Thread thread);

        public abstract void disposeThread(Env env, Thread thread);

        public abstract void finalizeContext(Env localEnv);

        public abstract void exitContext(Env localEnv, TruffleLanguage.ExitMode exitMode, int exitCode);

        public abstract Env patchEnvContext(Env env, OutputStream stdOut, OutputStream stdErr, InputStream stdIn, Map<String, Object> config, OptionValues options, String[] applicationArguments);

        public abstract void initializeMultiContext(TruffleLanguage<?> language);

        public abstract boolean isTruffleStackTrace(Throwable t);

        public abstract StackTraceElement[] getInternalStackTraceElements(Throwable t);

        public abstract Throwable getOrCreateLazyStackTrace(Throwable t);

        public abstract void configureLoggers(Object polyglotContext, Map<String, Level> logLevels, Object... loggers);

        public abstract Object getDefaultLoggers();

        public abstract Object createEngineLoggers(Object spi);

        public abstract Object getLoggersSPI(Object loggerCache);

        public abstract void closeEngineLoggers(Object loggers);

        public abstract TruffleLogger getLogger(String id, String loggerName, Object loggers);

        public abstract Object getLoggerCache(TruffleLogger logger);

        public abstract TruffleLanguage<?> getLanguage(Env env);

        public abstract Object createFileSystemContext(Object engineObject, FileSystem fileSystem);

        public abstract String detectMimeType(TruffleFile file, Set<String> validMimeTypes);

        public abstract Charset detectEncoding(TruffleFile file, String mimeType);

        public abstract TruffleFile getTruffleFile(String path, Object fileSystemContext);

        public abstract TruffleFile getTruffleFile(Path path, Object fileSystemContext);

        public abstract TruffleFile getTruffleFile(URI uri, Object fileSystemContext);

        public abstract boolean isSocketIOAllowed(Object fileSystemContext);

        public abstract FileSystem getFileSystem(TruffleFile truffleFile);

        public abstract Path getPath(TruffleFile truffleFile);

        public abstract Object getLanguageView(TruffleLanguage.Env env, Object value);

        public abstract Object getFileSystemContext(TruffleFile file);

        public abstract Object getFileSystemEngineObject(Object fileSystemContext);

        public abstract Object getPolyglotContext(TruffleContext context);

        public abstract Object invokeContextLocalFactory(Object factory, Object contextImpl);

        public abstract Object invokeContextThreadLocalFactory(Object factory, Object contextImpl, Thread thread);

        public abstract Object getScope(Env env);

        public abstract boolean isSynchronousTLAction(ThreadLocalAction action);

        public abstract boolean isSideEffectingTLAction(ThreadLocalAction action);

        public abstract boolean isRecurringTLAction(ThreadLocalAction action);

        public abstract void performTLAction(ThreadLocalAction action, ThreadLocalAction.Access access);

        public abstract OptionDescriptors createOptionDescriptorsUnion(OptionDescriptors... descriptors);

        public abstract InternalResource.Env createInternalResourceEnv(InternalResource resource, BooleanSupplier contextPreinitializationCheck);

    }

    public abstract static class InstrumentSupport extends Support {

        static final String IMPL_CLASS_NAME = "com.oracle.truffle.api.instrumentation.InstrumentAccessor$InstrumentImpl";

        protected InstrumentSupport() {
            super(IMPL_CLASS_NAME);
        }

        public abstract void initializeInstrument(Object instrumentationHandler, Object polyglotInstrument, String instrumentClassName, Supplier<? extends Object> instrumentSupplier);

        public abstract void createInstrument(Object instrumentationHandler, Object polyglotInstrument, String[] expectedServices, OptionValues options);

        public abstract void finalizeInstrument(Object instrumentationHandler, Object polyglotInstrument);

        public abstract void disposeInstrument(Object instrumentationHandler, Object polyglotInstrument, boolean cleanupRequired);

        public abstract <T> T getInstrumentationHandlerService(Object handler, Object polyglotInstrument, Class<T> type);

        public abstract Object createInstrumentationHandler(Object polyglotEngine, DispatchOutputStream out, DispatchOutputStream err, InputStream in, MessageTransport messageInterceptor,
                        boolean strongReferences);

        public abstract void collectEnvServices(Set<Object> collectTo, Object polyglotLanguageContext, TruffleLanguage<?> language);

        public abstract void onFirstExecution(RootNode rootNode, boolean validate);

        public abstract void onLoad(RootNode rootNode);

        @SuppressWarnings("static-method")
        public final DispatchOutputStream createDispatchOutput(OutputStream out) {
            if (out instanceof DispatchOutputStream) {
                return (DispatchOutputStream) out;
            }
            return new DispatchOutputStream(out);
        }

        @SuppressWarnings("static-method")
        public final DelegatingOutputStream createDelegatingOutput(OutputStream out, DispatchOutputStream delegate) {
            return new DelegatingOutputStream(out, delegate);
        }

        @SuppressWarnings("static-method")
        public final OutputStream getOut(DispatchOutputStream out) {
            return out.getOut();
        }

        public abstract OptionDescriptors describeEngineOptions(Object instrumentationHandler, Object key, String requiredGroup);

        public abstract OptionDescriptors describeContextOptions(Object instrumentationHandler, Object key, String requiredGroup);

        public abstract Object getEngineInstrumenter(Object instrumentationHandler);

        public abstract void onNodeInserted(RootNode rootNode, Node tree);

        public abstract boolean hasContextBindings(Object engine);

        public abstract boolean hasThreadBindings(Object engine);

        public abstract void notifyContextCreated(Object engine, TruffleContext context);

        public abstract void notifyContextClosed(Object engine, TruffleContext context);

        public abstract void notifyContextResetLimit(Object engine, TruffleContext context);

        public abstract void notifyLanguageContextCreate(Object engine, TruffleContext context, LanguageInfo info);

        public abstract void notifyLanguageContextCreated(Object engine, TruffleContext context, LanguageInfo info);

        public abstract void notifyLanguageContextCreateFailed(Object engine, TruffleContext context, LanguageInfo info);

        public abstract void notifyLanguageContextInitialize(Object engine, TruffleContext context, LanguageInfo info);

        public abstract void notifyLanguageContextInitialized(Object engine, TruffleContext context, LanguageInfo info);

        public abstract void notifyLanguageContextInitializeFailed(Object engine, TruffleContext context, LanguageInfo info);

        public abstract void notifyLanguageContextFinalized(Object engine, TruffleContext context, LanguageInfo info);

        public abstract void notifyLanguageContextDisposed(Object engine, TruffleContext context, LanguageInfo info);

        public abstract void notifyThreadStarted(Object engine, TruffleContext context, Thread thread);

        public abstract void notifyThreadFinished(Object engine, TruffleContext context, Thread thread);

        public abstract Object createPolyglotSourceSection(Object instrumentEnv, Object polyglotSource, SourceSection ss);

        public abstract void patchInstrumentationHandler(Object instrumentationHandler, DispatchOutputStream out, DispatchOutputStream err, InputStream in);

        public abstract void finalizeStoreInstrumentationHandler(Object instrumentationHandler);

        public abstract boolean isInputValueSlotIdentifier(Object identifier);

        public abstract boolean isInstrumentable(Node node);

        public abstract Object invokeContextLocalFactory(Object factory, TruffleContext truffleContext);

        public abstract Object invokeContextThreadLocalFactory(Object factory, TruffleContext truffleContext, Thread t);

        public abstract void notifyEnter(Object instrumentationHandler, TruffleContext truffleContext);

        public abstract void notifyLeave(Object instrumentationHandler, TruffleContext truffleContext);

        public abstract Collection<CallTarget> getLoadedCallTargets(Object instrumentationHandler);

        public abstract Object getPolyglotInstrument(Object instrumentEnv);

    }

    public abstract static class FrameSupport extends Support {

        static final String IMPL_CLASS_NAME = "com.oracle.truffle.api.frame.FrameAccessor$FramesImpl";

        protected FrameSupport() {
            super(IMPL_CLASS_NAME);
        }

        public abstract void markMaterializeCalled(FrameDescriptor descriptor);

        public abstract boolean getMaterializeCalled(FrameDescriptor descriptor);

        public abstract boolean usesAllStaticMode(FrameDescriptor descriptor);

        public abstract boolean usesMixedStaticMode(FrameDescriptor descriptor);
    }

    public abstract static class ExceptionSupport extends Support {

        static final String IMPL_CLASS_NAME = "com.oracle.truffle.api.exception.ExceptionAccessor$ExceptionSupportImpl";

        protected ExceptionSupport() {
            super(IMPL_CLASS_NAME);
        }

        public abstract Throwable getLazyStackTrace(Throwable exception);

        public abstract void setLazyStackTrace(Throwable exception, Throwable stackTrace);

        public abstract Object createDefaultStackTraceElementObject(RootNode rootNode, SourceSection sourceSection);

        public abstract boolean isException(Object receiver);

        public abstract RuntimeException throwException(Object receiver);

        public abstract Object getExceptionType(Object receiver);

        public abstract boolean isExceptionIncompleteSource(Object receiver);

        public abstract int getExceptionExitStatus(Object receiver);

        public abstract boolean hasExceptionCause(Object receiver);

        public abstract Object getExceptionCause(Object receiver);

        public abstract boolean hasExceptionMessage(Object receiver);

        public abstract Object getExceptionMessage(Object receiver);

        public abstract boolean hasExceptionStackTrace(Object receiver);

        public abstract Object getExceptionStackTrace(Object receiver, Object polyglotContext);

        public abstract boolean hasSourceLocation(Object receiver);

        public abstract SourceSection getSourceLocation(Object receiver);

        public abstract int getStackTraceElementLimit(Object receiver);

        public abstract Node getLocation(Object receiver);

        public abstract boolean assertGuestObject(Object guestObject);

    }

    public abstract static class IOSupport extends Support {

        static final String IMPL_CLASS_NAME = "com.oracle.truffle.api.io.IOAccessor$IOSupportImpl";

        protected IOSupport() {
            super(IMPL_CLASS_NAME);
        }

        public abstract TruffleProcessBuilder createProcessBuilder(Object polylgotLanguageContext, FileSystem fileSystem, List<String> command);
    }

    public abstract static class SomSupport extends Support {

        static final String IMPL_CLASS_NAME = "com.oracle.truffle.api.staticobject.SomAccessor";

        protected SomSupport() {
            super(IMPL_CLASS_NAME);
        }

    }

    public abstract static class RuntimeSupport {

        static final Object PERMISSION = new Object();

        protected RuntimeSupport(Object permission) {
            if (permission != PERMISSION) {
                throw new AssertionError("Invalid permission to create runtime support.");
            }
        }

        public abstract RootCallTarget newCallTarget(CallTarget source, RootNode rootNode);

        public abstract long getCallTargetId(CallTarget target);

        public abstract boolean isLoaded(CallTarget callTarget);

        public abstract void notifyOnLoad(CallTarget callTarget);

        public ThreadLocalHandshake getThreadLocalHandshake() {
            return DefaultThreadLocalHandshake.SINGLETON;
        }

        /**
         * Reports the execution count of a loop.
         *
         * @param source the Node which invoked the loop.
         * @param iterations the number iterations to report to the runtime system
         */
        public abstract void onLoopCount(Node source, int iterations);

        /**
         * Reports a back edge to the target location. This information can be used to trigger
         * on-stack replacement (OSR) for a {@link BytecodeOSRNode}.
         *
         * @param osrNode the node which can be on-stack replaced
         * @return result if OSR was performed, or {@code null}.
         */
        public abstract boolean pollBytecodeOSRBackEdge(BytecodeOSRNode osrNode);

        public abstract Object tryBytecodeOSR(BytecodeOSRNode osrNode, int target, Object interpreterState, Runnable beforeTransfer, VirtualFrame parentFrame);

        /**
         * Reports that a child node of an {@link BytecodeOSRNode} was replaced. Allows the runtime
         * system to invalidate any OSR targets it has created.
         *
         * @param osrNode the node whose child was replaced
         * @param oldNode the replaced node
         * @param newNode the replacement node
         * @param reason the replacement reason
         */
        public abstract void onOSRNodeReplaced(BytecodeOSRNode osrNode, Node oldNode, Node newNode, CharSequence reason);

        /**
         * Same as {@link #transferOSRFrame(BytecodeOSRNode, Frame, Frame, int, Object)}, but
         * fetches the target metadata.
         */
        // Support for deprecated frame transfer: GR-38296
        public abstract void transferOSRFrame(BytecodeOSRNode osrNode, Frame source, Frame target, int bytecodeTarget);

        /**
         * Transfers state from the {@code source} frame into the {@code target} frame. This method
         * should only be used inside OSR code. The frames must have the same layout as the frame
         * passed when executing the {@code osrNode}.
         *
         * @param osrNode the node being on-stack replaced.
         * @param source the frame to transfer state from
         * @param target the frame to transfer state into
         * @param bytecodeTarget the target location OSR executes from (e.g., bytecode index).
         */
        public abstract void transferOSRFrame(BytecodeOSRNode osrNode, Frame source, Frame target, int bytecodeTarget, Object targetMetadata);

        /**
         * Restores state from the {@code source} frame into the {@code target} frame. This method
         * should only be used inside OSR code. The frames must have the same layout as the frame
         * passed when executing the {@code osrNode}.
         *
         * @param osrNode the node being on-stack replaced.
         * @param source the frame to transfer state from
         * @param target the frame to transfer state into
         */
        public abstract void restoreOSRFrame(BytecodeOSRNode osrNode, Frame source, Frame target);

        /**
         * Returns the compiler options specified available from the runtime.
         */
        public abstract OptionDescriptors getRuntimeOptionDescriptors();

        /**
         * Returns <code>true</code> if the java stack frame is a representing a guest language
         * call. Needs to return <code>true</code> only once per java stack frame per guest language
         * call.
         */
        public abstract boolean isGuestCallStackFrame(@SuppressWarnings("unused") StackTraceElement e);

        public abstract void initializeProfile(CallTarget target, Class<?>[] argumentTypes);

        public abstract <T extends Node> BlockNode<T> createBlockNode(T[] elements, ElementExecutor<T> executor);

        public abstract Assumption createAlwaysValidAssumption();

        public abstract void reportPolymorphicSpecialize(Node source);

        public abstract Object callInlined(Node callNode, CallTarget target, Object... arguments);

        public abstract Object callProfiled(CallTarget target, Object... arguments);

        public abstract Object[] castArrayFixedLength(Object[] args, int length);

        @SuppressWarnings({"unchecked"})
        public abstract <T> T unsafeCast(Object value, Class<T> type, boolean condition, boolean nonNull, boolean exact);

        public abstract void flushCompileQueue(Object runtimeData);

        public abstract Object createRuntimeData(Object engine, OptionValues engineOptions, Function<String, TruffleLogger> loggerFactory, SandboxPolicy sandboxPolicy);

        public abstract Object tryLoadCachedEngine(OptionValues runtimeData, Function<String, TruffleLogger> logger);

        public abstract void onEngineCreate(Object engine, Object runtimeData);

        public abstract boolean isStoreEnabled(OptionValues options);

        public abstract void onEnginePatch(Object runtimeData, OptionValues runtimeOptions, Function<String, TruffleLogger> logSupplier, SandboxPolicy sandboxPolicy);

        public abstract boolean onEngineClosing(Object runtimeData);

        public abstract void onEngineClosed(Object runtimeData);

        public abstract boolean isOSRRootNode(RootNode rootNode);

        public abstract int getObjectAlignment();

        public abstract int getArrayBaseOffset(Class<?> componentType);

        public abstract int getArrayIndexScale(Class<?> componentType);

        public abstract int getBaseInstanceSize(Class<?> type);

        public abstract int[] getFieldOffsets(Class<?> type, boolean includePrimitive, boolean includeSuperclasses);

        public AbstractFastThreadLocal getContextThreadLocal() {
            return DefaultContextThreadLocal.SINGLETON;
        }

        public abstract boolean isLegacyCompilerOption(String key);

        public abstract <T> ThreadLocal<T> createTerminatingThreadLocal(Supplier<T> initialValue, Consumer<T> onThreadTermination);
    }

    public abstract static class LanguageProviderSupport extends Support {

        static final String IMPL_CLASS_NAME = "com.oracle.truffle.api.provider.LanguageProviderSupportImpl";

        protected LanguageProviderSupport() {
            super(IMPL_CLASS_NAME);
        }

        public abstract String getLanguageClassName(TruffleLanguageProvider provider);

        public abstract Object create(TruffleLanguageProvider provider);

        public abstract Collection<String> getServicesClassNames(TruffleLanguageProvider provider);

        public abstract List<FileTypeDetector> createFileTypeDetectors(TruffleLanguageProvider provider);

        public abstract List<String> getInternalResourceIds(TruffleLanguageProvider provider);

        public abstract InternalResource createInternalResource(TruffleLanguageProvider provider, String resourceId);

        public abstract String getInternalResourceComponentId(InternalResourceProvider provider);

        public abstract String getInternalResourceId(InternalResourceProvider provider);

        public abstract InternalResource createInternalResource(InternalResourceProvider provider);

    }

    public abstract static class InstrumentProviderSupport extends Support {

        static final String IMPL_CLASS_NAME = "com.oracle.truffle.api.instrumentation.provider.InstrumentProviderSupportImpl";

        protected InstrumentProviderSupport() {
            super(IMPL_CLASS_NAME);
        }

        public abstract String getInstrumentClassName(Object truffleInstrumentProvider);

        public abstract Object create(Object truffleInstrumentProvider);

        public abstract Collection<String> getServicesClassNames(Object truffleInstrumentProvider);

        public abstract List<String> getInternalResourceIds(Object truffleInstrumentProvider);

        public abstract InternalResource createInternalResource(Object truffleInstrumentProvider, String resourceId);
    }

    public abstract static class DynamicObjectSupport extends Support {

        static final String IMPL_CLASS_NAME = "com.oracle.truffle.object.DynamicObjectSupportImpl";

        protected DynamicObjectSupport() {
            super(IMPL_CLASS_NAME);
        }

        public abstract <T> Iterable<T> lookupTruffleService(Class<T> type);

    }

    public final void transferOSRFrameStaticSlot(FrameWithoutBoxing sourceFrame, FrameWithoutBoxing targetFrame, int slot) {
        sourceFrame.transferOSRStaticSlot(targetFrame, slot);
    }

    public final void startOSRFrameTransfer(FrameWithoutBoxing target) {
        target.startOSRTransfer();
    }

// A separate class to break the cycle such that Accessor can fully initialize
// before ...Accessor classes static initializers run, which call methods from Accessor.
    private static class Constants {

        private static final Accessor.LanguageSupport LANGUAGE;
        private static final Accessor.NodeSupport NODES;
        private static final Accessor.InstrumentSupport INSTRUMENT;
        private static final Accessor.SourceSupport SOURCE;
        private static final Accessor.InteropSupport INTEROP;
        private static final Accessor.ExceptionSupport EXCEPTION;
        private static final Accessor.IOSupport IO;
        private static final Accessor.FrameSupport FRAMES;
        private static final Accessor.EngineSupport ENGINE;
        private static final Accessor.HostSupport HOST;
        private static final Accessor.RuntimeSupport RUNTIME;
        private static final Accessor.LanguageProviderSupport LANGUAGE_PROVIDER;
        private static final Accessor.InstrumentProviderSupport INSTRUMENT_PROVIDER;
        private static final DynamicObjectSupport DYNAMIC_OBJECT;

        static {
            // Eager load all accessors so the above fields are all set and all methods are
            // usable
            LANGUAGE = loadSupport(LanguageSupport.IMPL_CLASS_NAME);
            NODES = loadSupport(NodeSupport.IMPL_CLASS_NAME);
            INSTRUMENT = loadSupport(InstrumentSupport.IMPL_CLASS_NAME);
            SOURCE = loadSupport(SourceSupport.IMPL_CLASS_NAME);
            INTEROP = loadSupport(InteropSupport.IMPL_CLASS_NAME);
            EXCEPTION = loadSupport(ExceptionSupport.IMPL_CLASS_NAME);
            IO = loadSupport(IOSupport.IMPL_CLASS_NAME);
            FRAMES = loadSupport(FrameSupport.IMPL_CLASS_NAME);
            ENGINE = loadSupport(EngineSupport.IMPL_CLASS_NAME);
            HOST = loadSupport(HostSupport.IMPL_CLASS_NAME);
            RUNTIME = getTVMCI().createRuntimeSupport(RuntimeSupport.PERMISSION);
            LANGUAGE_PROVIDER = loadSupport(LanguageProviderSupport.IMPL_CLASS_NAME);
            INSTRUMENT_PROVIDER = loadSupport(InstrumentProviderSupport.IMPL_CLASS_NAME);
            DYNAMIC_OBJECT = loadSupport(DynamicObjectSupport.IMPL_CLASS_NAME);
        }

        @SuppressWarnings("unchecked")
        private static <T> T loadSupport(String className) {
            try {
                Class<T> klass = (Class<T>) Class.forName(className, true, Accessor.class.getClassLoader());
                Constructor<T> constructor = klass.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    protected Accessor() {
        String thisClassName = this.getClass().getName();
        if ("com.oracle.truffle.api.LanguageAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.api.TruffleAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.api.nodes.NodeAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.api.instrumentation.InstrumentAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.api.source.SourceAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.api.interop.InteropAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.api.exception.ExceptionAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.api.io.IOAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.api.frame.FrameAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.host.HostAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.polyglot.EngineAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.api.utilities.JSONHelper.DumpAccessor".equals(thisClassName)) {
            // OK, classes initializing accessors
        } else if ("com.oracle.truffle.api.debug.Debugger$AccessorDebug".equals(thisClassName) ||
                        "com.oracle.truffle.tck.instrumentation.VerifierInstrument$TruffleTCKAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.api.instrumentation.test.AbstractInstrumentationTest$TestAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.api.test.TestAPIAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.api.impl.TVMCIAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.api.impl.DefaultRuntimeAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.runtime.OptimizedRuntimeAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.api.dsl.DSLAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.api.impl.ImplAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.api.memory.MemoryFenceAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.api.library.LibraryAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.polyglot.enterprise.EnterpriseEngineAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.polyglot.enterprise.test.EnterpriseDispatchTestAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.api.staticobject.SomAccessor".equals(thisClassName) ||
                        "com.oracle.truffle.api.strings.TStringAccessor".equals(thisClassName)) {
            // OK, classes allowed to use accessors
        } else {
            throw new IllegalStateException(thisClassName);
        }
    }

    public final NodeSupport nodeSupport() {
        return Constants.NODES;
    }

    public final LanguageSupport languageSupport() {
        return Constants.LANGUAGE;
    }

    public final EngineSupport engineSupport() {
        return Constants.ENGINE;
    }

    public final InstrumentSupport instrumentSupport() {
        return Constants.INSTRUMENT;
    }

    public final InteropSupport interopSupport() {
        return Constants.INTEROP;
    }

    public final ExceptionSupport exceptionSupport() {
        return Constants.EXCEPTION;
    }

    public final SourceSupport sourceSupport() {
        return Constants.SOURCE;
    }

    public final FrameSupport framesSupport() {
        return Constants.FRAMES;
    }

    public final RuntimeSupport runtimeSupport() {
        return Constants.RUNTIME;
    }

    public final HostSupport hostSupport() {
        return Constants.HOST;
    }

    public final IOSupport ioSupport() {
        return Constants.IO;
    }

    public final LanguageProviderSupport languageProviderSupport() {
        return Constants.LANGUAGE_PROVIDER;
    }

    public final InstrumentProviderSupport instrumentProviderSupport() {
        return Constants.INSTRUMENT_PROVIDER;
    }

    public final DynamicObjectSupport dynamicObjectSupport() {
        return Constants.DYNAMIC_OBJECT;
    }

    /**
     * Don't call me. I am here only to let NetBeans debug any Truffle project.
     *
     * @param args
     */
    public static void main(String... args) {
        throw new IllegalStateException();
    }

    @CompilerDirectives.CompilationFinal //
    private static volatile TVMCI tvmci;

    /**
     * Returns a {@link TVMCI} obtained from {@link TruffleRuntime}.
     *
     * NOTE: this method is called reflectively by {@code TruffleBaseFeature} to initialize
     * {@code tvmci} instance.
     */
    private static TVMCI getTVMCI() {
        if (ImageInfo.inImageRuntimeCode()) {
            return tvmci;
        }
        TVMCI result = tvmci;
        if (result == null) {
            result = Truffle.getRuntime().getCapability(TVMCI.class);
            tvmci = result;
        }
        return result;
    }

}
