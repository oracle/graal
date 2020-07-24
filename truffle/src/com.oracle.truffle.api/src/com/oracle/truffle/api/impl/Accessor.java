/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.MessageTransport;
import org.graalvm.polyglot.io.ProcessHandler;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.TruffleFile.FileTypeDetector;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.io.TruffleProcessBuilder;
import com.oracle.truffle.api.nodes.BlockNode;
import com.oracle.truffle.api.nodes.BlockNode.ElementExecutor;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
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

        public abstract boolean isInstrumentable(RootNode rootNode);

        public abstract void setCallTarget(RootNode rootNode, RootCallTarget callTarget);

        public abstract boolean isCloneUninitializedSupported(RootNode rootNode);

        public abstract RootNode cloneUninitialized(RootNode rootNode);

        public abstract int adoptChildrenAndCount(RootNode rootNode);

        public abstract Object getPolyglotLanguage(LanguageInfo languageInfo);

        public abstract TruffleLanguage<?> getLanguage(RootNode languageInfo);

        public abstract LanguageInfo createLanguage(Object polyglotLanguage, String id, String name, String version, String defaultMimeType, Set<String> mimeTypes, boolean internal,
                        boolean interactive);

        public abstract Object getPolyglotEngine(RootNode rootNode);

        public abstract List<TruffleStackTraceElement> findAsynchronousFrames(CallTarget target, Frame frame);

        public abstract int getRootNodeBits(RootNode root);

        public abstract void setRootNodeBits(RootNode root, int bits);

        public abstract Lock getLock(Node node);

        public abstract void applyPolyglotEngine(RootNode from, RootNode to);

        public abstract void forceAdoption(Node parent, Node child);

    }

    public abstract static class SourceSupport extends Support {

        static final String IMPL_CLASS_NAME = "com.oracle.truffle.api.source.SourceAccessor$SourceSupportImpl";

        protected SourceSupport() {
            super(IMPL_CLASS_NAME);
        }

        public abstract Object getSourceIdentifier(Source source);

        public abstract Source copySource(Source source);

        public abstract void setPolyglotSource(Source source, org.graalvm.polyglot.Source polyglotSource);

        public abstract org.graalvm.polyglot.Source getPolyglotSource(Source source);

        public abstract String findMimeType(URL url, Object fileSystemContext) throws IOException;

        public abstract SourceBuilder newBuilder(String language, File origin);

        public abstract void setFileSystemContext(SourceBuilder builder, Object fileSystemContext);

        public abstract void invalidateAfterPreinitialiation(Source source);
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

        public abstract Object createLegacyMetaObjectWrapper(Object receiver, Object result);

        public abstract Object unwrapLegacyMetaObjectWrapper(Object receiver);
    }

    public abstract static class EngineSupport extends Support {

        static final String IMPL_CLASS_NAME = "com.oracle.truffle.polyglot.EngineAccessor$EngineImpl";

        protected EngineSupport() {
            super(IMPL_CLASS_NAME);
        }

        public abstract <T> Iterable<T> loadServices(Class<T> type);

        public abstract Object getInstrumentationHandler(Object polyglotObject);

        public abstract void exportSymbol(Object polyglotLanguageContext, String symbolName, Object value);

        public abstract Map<String, ? extends Object> getExportedSymbols();

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

        public abstract Object getCurrentPolyglotEngine();

        public abstract CallTarget parseForLanguage(Object sourceLanguageContext, Source source, String[] argumentNames, boolean allowInternal);

        public abstract Env getEnvForInstrument(String languageId, String mimeType);

        public abstract Env getEnvForInstrument(LanguageInfo language);

        public abstract Env getLegacyLanguageEnv(Object obj, boolean nullForHost);

        public abstract ContextReference<Object> getCurrentContextReference(Object polyglotLanguage);

        public abstract boolean isDisposed(Object polyglotLanguageContext);

        public abstract Map<String, LanguageInfo> getInternalLanguages(Object polyglotObject);

        public abstract Map<String, LanguageInfo> getPublicLanguages(Object polyglotObject);

        public abstract Map<String, InstrumentInfo> getInstruments(Object polyglotObject);

        public abstract org.graalvm.polyglot.SourceSection createSourceSection(Object polyglotObject, org.graalvm.polyglot.Source source, SourceSection sectionImpl);

        public abstract <T> T lookup(InstrumentInfo info, Class<T> serviceClass);

        public abstract <S> S lookup(LanguageInfo language, Class<S> type);

        public abstract <T extends TruffleLanguage<?>> T getCurrentLanguage(Class<T> languageClass);

        public abstract <C, T extends TruffleLanguage<C>> C getCurrentContext(Class<T> languageClass);

        public abstract TruffleContext getTruffleContext(Object polyglotLanguageContext);

        public abstract Object toGuestValue(Object obj, Object languageContext);

        public abstract Object getPolyglotEngine(Object polyglotLanguageInstance);

        public abstract Object lookupHostSymbol(Object polyglotLanguageContext, Env env, String symbolName);

        public abstract Object asHostSymbol(Object polyglotLanguageContext, Class<?> symbolClass);

        public abstract boolean isHostAccessAllowed(Object polyglotLanguageContext, Env env);

        public abstract boolean isNativeAccessAllowed(Object polyglotLanguageContext, Env env);

        public abstract boolean inContextPreInitialization(Object polyglotObject);

        public abstract Object createInternalContext(Object sourcePolyglotLanguageContext, Map<String, Object> config, TruffleContext spiContext);

        public abstract void initializeInternalContext(Object sourcePolyglotLanguageContext, Object polyglotContext);

        public abstract Object enterInternalContext(Object polyglotContext);

        public abstract void leaveInternalContext(Object polyglotContext, Object prev);

        public abstract void closeInternalContext(Object polyglotContext);

        public abstract boolean isInternalContextEntered(Object polyglotContext);

        public abstract void reportAllLanguageContexts(Object polyglotEngine, Object contextsListener);

        public abstract void reportAllContextThreads(Object polyglotEngine, Object threadsListener);

        public abstract TruffleContext getParentContext(Object polyglotContext);

        public abstract boolean isCreateThreadAllowed(Object polyglotLanguageContext);

        public final Thread createThread(Object polyglotLanguageContext, Runnable runnable, Object innerContextImpl, ThreadGroup group) {
            return createThread(polyglotLanguageContext, runnable, innerContextImpl, group, 0);
        }

        public final Thread createThread(Object polyglotLanguageContext, Runnable runnable, Object innerContextImpl) {
            return createThread(polyglotLanguageContext, runnable, innerContextImpl, null, 0);
        }

        public abstract Thread createThread(Object polyglotLanguageContext, Runnable runnable, Object innerContextImpl, ThreadGroup group, long stackSize);

        public abstract Iterable<Scope> createDefaultLexicalScope(Node node, Frame frame);

        public abstract Iterable<Scope> createDefaultTopScope(Object global);

        public abstract RuntimeException wrapHostException(Node callNode, Object languageContext, Throwable exception);

        public abstract boolean isHostException(Throwable exception);

        public abstract Throwable asHostException(Throwable exception);

        public abstract Object getCurrentHostContext();

        public abstract PolyglotException wrapGuestException(String languageId, Throwable exception);

        public abstract <T> T getOrCreateRuntimeData(Object polyglotEngine, BiFunction<OptionValues, Supplier<TruffleLogger>, T> constructor);

        public abstract Set<? extends Class<?>> getProvidedTags(LanguageInfo language);

        public abstract Object getPolyglotBindingsForLanguage(Object polyglotLanguageContext);

        public abstract Object findMetaObjectForLanguage(Object polyglotLanguageContext, Object value);

        public abstract boolean isInternal(FileSystem fs);

        public abstract boolean hasAllAccess(FileSystem fs);

        public abstract String getLanguageHome(Object engineObject);

        public abstract void addToHostClassPath(Object polyglotLanguageContext, TruffleFile entries);

        public abstract boolean isInstrumentExceptionsAreThrown(Object polyglotEngine);

        public abstract Object asBoxedGuestValue(Object guestObject, Object polyglotLanguageContext);

        public abstract Object createDefaultLoggerCache();

        public abstract Handler getLogHandler(Object loggerCache);

        public abstract Map<String, Level> getLogLevels(Object loggerCache);

        public abstract Object getLoggerOwner(Object loggerCache);

        public abstract TruffleLogger getLogger(Object polyglotInstrument, String name);

        public abstract LogRecord createLogRecord(Object loggerCache, Level level, String loggerName, String message, String className, String methodName, Object[] parameters, Throwable thrown);

        public abstract Object getCurrentOuterContext();

        public abstract boolean isCharacterBasedSource(Object fsEngineObject, String language, String mimeType);

        public abstract Set<String> getValidMimeTypes(Object engineObject, String language);

        public abstract Object asHostObject(Object value);

        public abstract boolean isHostObject(Object value);

        public abstract boolean isHostFunction(Object value);

        public abstract boolean isHostSymbol(Object guestObject);

        public abstract <S> S lookupService(Object polyglotLanguageContext, LanguageInfo language, LanguageInfo accessingLanguage, Class<S> type);

        public abstract Object convertPrimitive(Object value, Class<?> requestedType);

        public abstract <T extends TruffleLanguage<?>> LanguageReference<T> lookupLanguageReference(Object polyglotEngine, TruffleLanguage<?> sourceLanguage, Class<T> targetLanguageClass);

        public abstract <T extends TruffleLanguage<?>> LanguageReference<T> getDirectLanguageReference(Object polyglotEngine, TruffleLanguage<?> sourceLanguage, Class<T> targetLanguageClass);

        public abstract <T extends TruffleLanguage<C>, C> ContextReference<C> lookupContextReference(Object polyglotEngine, TruffleLanguage<?> language, Class<T> languageClass);

        public abstract <T extends TruffleLanguage<C>, C> ContextReference<C> getDirectContextReference(Object polyglotEngine, TruffleLanguage<?> language, Class<T> languageClass);

        public abstract FileSystem getFileSystem(Object polyglotContext);

        public abstract boolean isPolyglotEvalAllowed(Object polyglotLanguageContext);

        public abstract boolean isPolyglotBindingsAccessAllowed(Object polyglotLanguageContext);

        public abstract TruffleFile getTruffleFile(String path);

        public abstract TruffleFile getTruffleFile(URI uri);

        public abstract int getAsynchronousStackDepth(Object polylgotLanguage);

        public abstract void setAsynchronousStackDepth(Object polyglotInstrument, int depth);

        public abstract boolean isCreateProcessAllowed(Object polylgotLanguageContext);

        public abstract Map<String, String> getProcessEnvironment(Object polyglotLanguageContext);

        public abstract Process createSubProcess(Object polyglotLanguageContext, List<String> cmd, String cwd, Map<String, String> environment, boolean redirectErrorStream,
                        ProcessHandler.Redirect inputRedirect, ProcessHandler.Redirect outputRedirect, ProcessHandler.Redirect errorRedirect) throws IOException;

        public abstract boolean hasDefaultProcessHandler(Object polyglotLanguageContext);

        public abstract ProcessHandler.Redirect createRedirectToOutputStream(Object polyglotLanguageContext, OutputStream stream);

        public abstract boolean isIOAllowed();

        public abstract ZoneId getTimeZone(Object polyglotLanguageContext);

        public abstract Set<String> getLanguageIds();

        public abstract Set<String> getInstrumentIds();

        public abstract Set<String> getInternalIds();

        public abstract String getUnparsedOptionValue(OptionValues optionValues, OptionKey<?> optionKey);

        public abstract String getRelativePathInLanguageHome(TruffleFile truffleFile);

        public abstract void onSourceCreated(Source source);

        public abstract String getReinitializedPath(TruffleFile truffleFile);

        public abstract URI getReinitializedURI(TruffleFile truffleFile);

        public abstract LanguageInfo getLanguageInfo(Object polyglotInstrument, Class<? extends TruffleLanguage<?>> languageClass);

        public abstract <C> Object getDefaultLanguageView(TruffleLanguage<C> truffleLanguage, C context, Object value);

        public abstract Object getLanguageView(LanguageInfo viewLanguage, Object value);

        public abstract Object getScopedView(LanguageInfo viewLanguage, Node location, Frame frame, Object value);

        public abstract boolean initializeLanguage(Object polyglotLanguageContext, LanguageInfo targetLanguage);

        public abstract RuntimeException engineToLanguageException(Throwable t);

        public abstract RuntimeException engineToInstrumentException(Throwable t);

        public abstract Object getCurrentFileSystemContext();

        public abstract Object getPublicFileSystemContext(Object polyglotContextImpl);

        public abstract Object getInternalFileSystemContext(Object polyglotContextImpl);

        public abstract Map<String, Collection<? extends FileTypeDetector>> getEngineFileTypeDetectors(Object engineFileSystemContext);

        public abstract boolean isHostToGuestRootNode(RootNode rootNode);

        public abstract AssertionError invalidSharingError(Object polyglotEngine) throws AssertionError;

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

        public abstract TruffleContext createTruffleContext(Object impl);

        public abstract void postInitEnv(Env env);

        public abstract Object evalInContext(Source source, Node node, MaterializedFrame frame);

        public abstract Object findExportedSymbol(TruffleLanguage.Env env, String globalName, boolean onlyExplicit);

        public abstract void dispose(Env env);

        public abstract LanguageInfo getLanguageInfo(TruffleLanguage.Env env);

        public abstract LanguageInfo getLanguageInfo(TruffleLanguage<?> language);

        public abstract Object getPolyglotLanguageInstance(TruffleLanguage<?> language);

        public abstract CallTarget parse(Env env, Source code, Node context, String... argumentNames);

        public abstract ExecutableNode parseInline(Env env, Source code, Node context, MaterializedFrame frame);

        public abstract boolean isVisible(Env env, Object value);

        public abstract String legacyToString(Env env, Object obj);

        public abstract <C> String legacyToString(TruffleLanguage<C> language, C context, Object obj);

        public abstract Object legacyFindMetaObject(Env env, Object value);

        public abstract <C> Object legacyFindMetaObject(TruffleLanguage<C> language, C context, Object value);

        public abstract SourceSection legacyFindSourceLocation(Env env, Object value);

        public abstract <C> SourceSection legacyFindSourceLocation(TruffleLanguage<C> language, C context, Object value);

        public abstract boolean isObjectOfLanguage(Env env, Object value);

        public abstract Object getContext(Env env);

        public abstract TruffleLanguage<?> getSPI(Env env);

        public abstract InstrumentInfo createInstrument(Object polyglotInstrument, String id, String name, String version);

        public abstract Object getPolyglotInstrument(InstrumentInfo info);

        public abstract boolean isContextInitialized(Env env);

        public abstract OptionDescriptors describeOptions(TruffleLanguage<?> language, String requiredGroup);

        public abstract void onThrowable(Node callNode, RootCallTarget root, Throwable e, Frame frame);

        public abstract boolean isThreadAccessAllowed(Env env, Thread current, boolean singleThread);

        public abstract void initializeThread(Env env, Thread current);

        public abstract void initializeMultiThreading(Env env);

        public abstract void disposeThread(Env env, Thread thread);

        public abstract void finalizeContext(Env localEnv);

        public abstract Iterable<Scope> findLocalScopes(Env env, Node node, Frame frame);

        public abstract Iterable<Scope> findTopScopes(Env env);

        public abstract Env patchEnvContext(Env env, OutputStream stdOut, OutputStream stdErr, InputStream stdIn, Map<String, Object> config, OptionValues options, String[] applicationArguments);

        public abstract boolean initializeMultiContext(TruffleLanguage<?> language);

        public abstract boolean isTruffleStackTrace(Throwable t);

        public abstract StackTraceElement[] getInternalStackTraceElements(Throwable t);

        public abstract void materializeHostFrames(Throwable original);

        public abstract void configureLoggers(Object polyglotContext, Map<String, Level> logLevels, Object... loggers);

        public abstract Object getDefaultLoggers();

        public abstract Object createEngineLoggers(Object spi, Map<String, Level> logLevels);

        public abstract void closeEngineLoggers(Object loggers);

        public abstract TruffleLogger getLogger(String id, String loggerName, Object loggers);

        public abstract TruffleLanguage<?> getLanguage(Env env);

        public abstract Object createFileSystemContext(Object engineObject, FileSystem fileSystem);

        public abstract String detectMimeType(TruffleFile file, Set<String> validMimeTypes);

        public abstract Charset detectEncoding(TruffleFile file, String mimeType);

        public abstract TruffleFile getTruffleFile(String path, Object fileSystemContext);

        public abstract boolean hasAllAccess(Object fileSystemContext);

        public abstract TruffleFile getTruffleFile(Object context, String path);

        public abstract TruffleFile getTruffleFile(Object context, URI uri);

        public abstract SecurityException throwSecurityException(String message);

        public abstract FileSystem getFileSystem(TruffleFile truffleFile);

        public abstract Path getPath(TruffleFile truffleFile);

        public abstract Object getScopedView(TruffleLanguage.Env env, Node node, Frame frame, Object value);

        public abstract Object getLanguageView(TruffleLanguage.Env env, Object value);

        public abstract Object getFileSystemContext(TruffleFile file);

        public abstract Object getFileSystemEngineObject(Object fileSystemContext);

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

        public abstract Object createInstrumentationHandler(Object polyglotEngine, DispatchOutputStream out, DispatchOutputStream err, InputStream in, MessageTransport messageInterceptor);

        public abstract void collectEnvServices(Set<Object> collectTo, Object polyglotLanguage, TruffleLanguage<?> language);

        public abstract void onFirstExecution(RootNode rootNode);

        public abstract void onLoad(RootNode rootNode);

        public abstract Iterable<?> findTopScopes(TruffleLanguage.Env env);

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

        public abstract OptionDescriptors describeOptions(Object instrumentationHandler, Object key, String requiredGroup);

        public abstract Object getEngineInstrumenter(Object instrumentationHandler);

        public abstract void onNodeInserted(RootNode rootNode, Node tree);

        public abstract boolean hasContextBindings(Object engine);

        public abstract void notifyContextCreated(Object engine, TruffleContext context);

        public abstract void notifyContextClosed(Object engine, TruffleContext context);

        public abstract void notifyLanguageContextCreated(Object engine, TruffleContext context, LanguageInfo info);

        public abstract void notifyLanguageContextInitialized(Object engine, TruffleContext context, LanguageInfo info);

        public abstract void notifyLanguageContextFinalized(Object engine, TruffleContext context, LanguageInfo info);

        public abstract void notifyLanguageContextDisposed(Object engine, TruffleContext context, LanguageInfo info);

        public abstract void notifyThreadStarted(Object engine, TruffleContext context, Thread thread);

        public abstract void notifyThreadFinished(Object engine, TruffleContext context, Thread thread);

        public abstract org.graalvm.polyglot.SourceSection createSourceSection(Object instrumentEnv, org.graalvm.polyglot.Source source, com.oracle.truffle.api.source.SourceSection ss);

        public abstract void patchInstrumentationHandler(Object instrumentationHandler, DispatchOutputStream out, DispatchOutputStream err, InputStream in);

        public abstract boolean isInputValueSlotIdentifier(Object identifier);

        public abstract boolean isInstrumentable(Node node);

    }

    public abstract static class FrameSupport extends Support {

        static final String IMPL_CLASS_NAME = "com.oracle.truffle.api.frame.FrameAccessor$FramesImpl";

        protected FrameSupport() {
            super(IMPL_CLASS_NAME);
        }

        public abstract void markMaterializeCalled(FrameDescriptor descriptor);

        public abstract boolean getMaterializeCalled(FrameDescriptor descriptor);
    }

    public abstract static class IOSupport extends Support {

        static final String IMPL_CLASS_NAME = "com.oracle.truffle.api.io.IOAccessor$IOSupportImpl";

        protected IOSupport() {
            super(IMPL_CLASS_NAME);
        }

        public abstract TruffleProcessBuilder createProcessBuilder(Object polylgotLanguageContext, FileSystem fileSystem, List<String> command);
    }

    public abstract static class RuntimeSupport {

        static final Object PERMISSION = new Object();

        protected RuntimeSupport(Object permission) {
            if (permission != PERMISSION) {
                throw new AssertionError("Invalid permission to create runtime support.");
            }
        }

        public abstract IndirectCallNode createUncachedIndirectCall();

        /**
         * Reports the execution count of a loop.
         *
         * @param source the Node which invoked the loop.
         * @param iterations the number iterations to report to the runtime system
         */
        public abstract void onLoopCount(Node source, int iterations);

        /**
         * Returns the compiler options specified available from the runtime.
         */
        public abstract OptionDescriptors getCompilerOptionDescriptors();

        /**
         * Returns <code>true</code> if the java stack frame is a representing a guest language
         * call. Needs to return <code>true</code> only once per java stack frame per guest language
         * call.
         */
        public abstract boolean isGuestCallStackFrame(@SuppressWarnings("unused") StackTraceElement e);

        public abstract void initializeProfile(CallTarget target, Class<?>[] argumentTypes);

        public abstract <T extends Node> BlockNode<T> createBlockNode(T[] elements, ElementExecutor<T> executor);

        public abstract void reloadEngineOptions(Object runtimeData, OptionValues optionValues);

        public abstract void onEngineClosed(Object runtimeData);

        public abstract OutputStream getConfiguredLogStream();

        public abstract String getSavedProperty(String key);

        public abstract void reportPolymorphicSpecialize(Node source);

        public abstract Object callInlined(Node callNode, CallTarget target, Object... arguments);

        public abstract Object callProfiled(CallTarget target, Object... arguments);

        public abstract Object[] castArrayFixedLength(Object[] args, int length);

        @SuppressWarnings({"unchecked"})
        public abstract <T> T unsafeCast(Object value, Class<T> type, boolean condition, boolean nonNull, boolean exact);

    }

    public static final class JDKSupport {

        private JDKSupport() {
        }

        public void exportTo(ClassLoader loader, String moduleName) {
            TruffleJDKServices.exportTo(loader, moduleName);
        }

        public void exportTo(Class<?> client) {
            TruffleJDKServices.exportTo(client);
        }

        public <Service> List<Iterable<Service>> getTruffleRuntimeLoaders(Class<Service> serviceClass) {
            return TruffleJDKServices.getTruffleRuntimeLoaders(serviceClass);
        }

        public <S> void addUses(Class<S> service) {
            TruffleJDKServices.addUses(service);
        }

        public Object getUnnamedModule(ClassLoader classLoader) {
            return TruffleJDKServices.getUnnamedModule(classLoader);
        }

        public boolean verifyModuleVisibility(Object lookupModule, Class<?> memberClass) {
            return TruffleJDKServices.verifyModuleVisibility(lookupModule, memberClass);
        }

        public boolean isNonTruffleClass(Class<?> clazz) {
            return TruffleJDKServices.isNonTruffleClass(clazz);
        }

    }

    // A separate class to break the cycle such that Accessor can fully initialize
    // before ...Accessor classes static initializers run, which call methods from Accessor.
    private static class Constants {

        private static final Accessor.LanguageSupport LANGUAGE;
        private static final Accessor.NodeSupport NODES;
        private static final Accessor.InstrumentSupport INSTRUMENT;
        private static final Accessor.SourceSupport SOURCE;
        private static final Accessor.InteropSupport INTEROP;
        private static final Accessor.IOSupport IO;
        private static final Accessor.FrameSupport FRAMES;
        private static final Accessor.EngineSupport ENGINE;
        private static final Accessor.RuntimeSupport RUNTIME;

        static {
            // Eager load all accessors so the above fields are all set and all methods are
            // usable
            LANGUAGE = loadSupport(LanguageSupport.IMPL_CLASS_NAME);
            NODES = loadSupport(NodeSupport.IMPL_CLASS_NAME);
            INSTRUMENT = loadSupport(InstrumentSupport.IMPL_CLASS_NAME);
            SOURCE = loadSupport(SourceSupport.IMPL_CLASS_NAME);
            INTEROP = loadSupport(InteropSupport.IMPL_CLASS_NAME);
            IO = loadSupport(IOSupport.IMPL_CLASS_NAME);
            FRAMES = loadSupport(FrameSupport.IMPL_CLASS_NAME);
            ENGINE = loadSupport(EngineSupport.IMPL_CLASS_NAME);
            RUNTIME = getTVMCI().createRuntimeSupport(RuntimeSupport.PERMISSION);
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

    private static final Accessor.JDKSupport JDKSERVICES = new JDKSupport();

    protected Accessor() {
        switch (this.getClass().getName()) {
            case "com.oracle.truffle.api.LanguageAccessor":
            case "com.oracle.truffle.api.nodes.NodeAccessor":
            case "com.oracle.truffle.api.instrumentation.InstrumentAccessor":
            case "com.oracle.truffle.api.source.SourceAccessor":
            case "com.oracle.truffle.api.interop.InteropAccessor":
            case "com.oracle.truffle.api.io.IOAccessor":
            case "com.oracle.truffle.api.frame.FrameAccessor":
            case "com.oracle.truffle.polyglot.EngineAccessor":
            case "com.oracle.truffle.api.utilities.JSONHelper.DumpAccessor":
                // OK, classes initializing accessors
                break;
            case "com.oracle.truffle.api.debug.Debugger$AccessorDebug":
            case "com.oracle.truffle.tck.instrumentation.VerifierInstrument$TruffleTCKAccessor":
            case "com.oracle.truffle.api.instrumentation.test.AbstractInstrumentationTest$TestAccessor":
            case "com.oracle.truffle.api.test.polyglot.TestAPIAccessor":
            case "com.oracle.truffle.api.impl.TVMCIAccessor":
            case "com.oracle.truffle.api.impl.DefaultRuntimeAccessor":
            case "org.graalvm.compiler.truffle.runtime.GraalRuntimeAccessor":
            case "org.graalvm.compiler.truffle.runtime.debug.CompilerDebugAccessor":
            case "com.oracle.truffle.api.library.LibraryAccessor":
                // OK, classes allowed to use accessors
                break;
            default:
                throw new IllegalStateException(this.getClass().getName());
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

    public final SourceSupport sourceSupport() {
        return Constants.SOURCE;
    }

    public final FrameSupport framesSupport() {
        return Constants.FRAMES;
    }

    public final RuntimeSupport runtimeSupport() {
        return Constants.RUNTIME;
    }

    public final IOSupport ioSupport() {
        return Constants.IO;
    }

    public final JDKSupport jdkSupport() {
        return JDKSERVICES;
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
     * NOTE: this method is called reflectively by {@code TruffleFeature} to initialize
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
