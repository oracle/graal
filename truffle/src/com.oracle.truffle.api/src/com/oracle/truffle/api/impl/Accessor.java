/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.io.FileSystem;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.Source.SourceBuilder;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Communication between TruffleLanguage API/SPI, and other services.
 */
@SuppressWarnings("deprecation")
public abstract class Accessor {

    @SuppressWarnings("all")
    protected final Collection<ClassLoader> loaders() {
        return TruffleLocator.loaders();
    }

    protected void initializeNativeImageTruffleLocator() {
        TruffleLocator.initializeNativeImageTruffleLocator();
    }

    public abstract static class Nodes {

        public abstract boolean isInstrumentable(RootNode rootNode);

        public abstract boolean isTaggedWith(Node node, Class<?> tag);

        public abstract boolean isCloneUninitializedSupported(RootNode rootNode);

        public abstract RootNode cloneUninitialized(RootNode rootNode);

        public abstract int adoptChildrenAndCount(RootNode rootNode);

        public abstract Object getEngineObject(LanguageInfo languageInfo);

        public abstract TruffleLanguage<?> getLanguage(RootNode languageInfo);

        public abstract LanguageInfo createLanguage(Object vmObject, String id, String name, String version, String defaultMimeType, Set<String> mimeTypes, boolean internal, boolean interactive);

        public abstract Object getSourceVM(RootNode rootNode);

        public abstract int getRootNodeBits(RootNode root);

        public abstract void setRootNodeBits(RootNode root, int bits);

        public abstract Lock getLock(Node node);

        public void reportPolymorphicSpecialize(Node node) {
            SUPPORT.reportPolymorphicSpecialize(node);
        }
    }

    public abstract static class SourceSupport {

        public abstract Object getSourceIdentifier(Source source);

        public abstract Source copySource(Source source);

        public abstract void setPolyglotSource(Source source, org.graalvm.polyglot.Source polyglotSource);

        public abstract org.graalvm.polyglot.Source getPolyglotSource(Source source);

        public abstract String findMimeType(File file) throws IOException;

        public abstract String findMimeType(URL url) throws IOException;

        public abstract boolean isLegacySource(Source soure);

        public abstract SourceBuilder newBuilder(String language, File origin);

    }

    public abstract static class DumpSupport {
        public abstract void dump(Node newNode, Node newChild, CharSequence reason);
    }

    public abstract static class InteropSupport {
        public abstract boolean canHandle(Object foreignAccess, Object receiver);

        public abstract CallTarget canHandleTarget(Object access);

        public abstract boolean isTruffleObject(Object value);

        public abstract void checkInteropType(Object result);

        public abstract Object createDefaultNodeObject(Node node);

        public abstract boolean isValidNodeObject(Object obj);
    }

    public abstract static class EngineSupport {
        public static final int EXECUTION_EVENT = 1;
        public static final int SUSPENDED_EVENT = 2;

        @SuppressWarnings("rawtypes")
        public abstract Env findEnv(Object vm, Class<? extends TruffleLanguage> languageClass, boolean failIfNotFound);

        public abstract Object getInstrumentationHandler(Object languageShared);

        public abstract void exportSymbol(Object vmObject, String symbolName, Object value);

        public abstract Map<String, ? extends Object> getExportedSymbols(Object vmObject);

        public abstract Object importSymbol(Object vmObject, Env env, String symbolName);

        public abstract boolean isMimeTypeSupported(Object languageShared, String mimeType);

        public abstract void registerDebugger(Object vm, Object debugger);

        public abstract boolean isEvalRoot(RootNode target);

        public abstract <T> T lookupJavaInteropCodeCache(Object languageContext, Object key, Class<T> expectedType);

        public abstract <T> T installJavaInteropCodeCache(Object languageContext, Object key, T value, Class<T> expectedType);

        @SuppressWarnings("static-method")
        public final void attachOutputConsumer(DispatchOutputStream dos, OutputStream out) {
            dos.attach(out);
        }

        @SuppressWarnings("static-method")
        public final void detachOutputConsumer(DispatchOutputStream dos, OutputStream out) {
            dos.detach(out);
        }

        public abstract Object getCurrentVM();

        public abstract CallTarget parseForLanguage(Object vmObject, Source source, String[] argumentNames);

        public abstract Env getEnvForInstrument(Object vm, String languageId, String mimeType);

        public abstract Env getEnvForInstrument(LanguageInfo language);

        public abstract Env getExistingEnvForInstrument(LanguageInfo language);

        public abstract LanguageInfo getObjectLanguage(Object obj, Object vmObject);

        public abstract Object getCurrentContext(Object languageVMObject);

        public abstract boolean isDisposed(Object vmInstance);

        public abstract Map<String, LanguageInfo> getLanguages(Object vmInstance);

        public abstract Map<String, InstrumentInfo> getInstruments(Object vmInstance);

        public abstract org.graalvm.polyglot.SourceSection createSourceSection(Object vmObject, org.graalvm.polyglot.Source source, SourceSection sectionImpl);

        public abstract <T> T lookup(InstrumentInfo info, Class<T> serviceClass);

        public abstract <S> S lookup(LanguageInfo language, Class<S> type);

        public abstract <T extends TruffleLanguage<?>> T getCurrentLanguage(Class<T> languageClass);

        public abstract <C, T extends TruffleLanguage<C>> C getCurrentContext(Class<T> languageClass);

        public abstract TruffleContext getPolyglotContext(Object vmObject);

        public abstract Object toGuestValue(Object obj, Object languageContext);

        public abstract Object getVMFromLanguageObject(Object engineObject);

        public abstract OptionValues getCompilerOptionValues(RootNode rootNode);

        public abstract Object lookupHostSymbol(Object vmObject, Env env, String symbolName);

        public abstract Object asHostSymbol(Object vmObject, Class<?> symbolClass);

        public abstract boolean isHostAccessAllowed(Object vmObject, Env env);

        public abstract boolean isNativeAccessAllowed(Object vmObject, Env env);

        public abstract Object createInternalContext(Object vmObject, Map<String, Object> config, TruffleContext spiContext);

        public abstract void initializeInternalContext(Object vmObject, Object contextImpl);

        public abstract Object enterInternalContext(Object impl);

        public abstract void leaveInternalContext(Object impl, Object prev);

        public abstract void closeInternalContext(Object impl);

        public abstract void reportAllLanguageContexts(Object vmObject, Object contextsListener);

        public abstract void reportAllContextThreads(Object vmObject, Object threadsListener);

        public abstract TruffleContext getParentContext(Object impl);

        public abstract boolean isCreateThreadAllowed(Object vmObject);

        public abstract Thread createThread(Object vmObject, Runnable runnable, Object context);

        public abstract Iterable<Scope> createDefaultLexicalScope(Node node, Frame frame);

        public abstract Iterable<Scope> createDefaultTopScope(Object global);

        public abstract RuntimeException wrapHostException(Object languageContext, Throwable exception);

        public abstract boolean isHostException(Throwable exception);

        public abstract Throwable asHostException(Throwable exception);

        public abstract Object getCurrentHostContext();

        public abstract PolyglotException wrapGuestException(String languageId, Throwable exception);

        public abstract Object legacyTckEnter(Object vm);

        public abstract void legacyTckLeave(Object vm, Object prev);

        public abstract <T> T getOrCreateRuntimeData(Object sourceVM, Supplier<T> constructor);

        public abstract Class<? extends TruffleLanguage<?>> getLanguageClass(LanguageInfo language);

        public abstract Object getPolyglotBindingsForLanguage(Object vmObject);

        public abstract Object findMetaObjectForLanguage(Object vmObject, Object value);

        public abstract boolean isDefaultFileSystem(FileSystem fs);

        public abstract String getLanguageHome(Object engineObject);

        public abstract void addToHostClassPath(Object vmObject, TruffleFile entries);

        public abstract boolean isInstrumentExceptionsAreThrown(Object vmObject);

        public abstract Object asBoxedGuestValue(Object guestObject, Object vmObject);

        public abstract Handler getLogHandler();

        public abstract Map<String, Level> getLogLevels(Object context);

        public abstract LogRecord createLogRecord(Level level, String loggerName, String message, String className, String methodName, Object[] parameters, Throwable thrown);

        public abstract Object getCurrentOuterContext();

        public abstract Env getLanguageEnv(Object languageContextVMObject, LanguageInfo otherLanguage);

        public abstract boolean isCharacterBasedSource(String language, String mimeType);

        public abstract Set<String> getValidMimeTypes(String language);

        public abstract Object asHostObject(Object value);

        public abstract boolean isHostObject(Object value);

        public abstract boolean isHostFunction(Object value);

        public abstract boolean isHostSymbol(Object guestObject);

    }

    public abstract static class LanguageSupport {

        public abstract void initializeLanguage(TruffleLanguage<?> impl, LanguageInfo language, Object vmObject);

        public abstract Env createEnv(Object vmObject, TruffleLanguage<?> language, OutputStream stdOut, OutputStream stdErr, InputStream stdIn, Map<String, Object> config, OptionValues options,
                        String[] applicationArguments, FileSystem fileSystem);

        public abstract boolean areOptionsCompatible(TruffleLanguage<?> language, OptionValues firstContextOptions, OptionValues newContextOptions);

        public abstract Object createEnvContext(Env localEnv);

        public abstract TruffleContext createTruffleContext(Object impl);

        public abstract void postInitEnv(Env env);

        public abstract Object evalInContext(Source source, Node node, MaterializedFrame frame);

        public abstract Object findExportedSymbol(TruffleLanguage.Env env, String globalName, boolean onlyExplicit);

        public abstract Object languageGlobal(TruffleLanguage.Env env);

        public abstract void dispose(Env env);

        public abstract LanguageInfo getLanguageInfo(TruffleLanguage.Env env);

        public abstract LanguageInfo getLanguageInfo(TruffleLanguage<?> language);

        public abstract LanguageInfo getLegacyLanguageInfo(Object vm, @SuppressWarnings("rawtypes") Class<? extends TruffleLanguage> languageClass);

        public abstract CallTarget parse(Env env, Source code, Node context, String... argumentNames);

        public abstract ExecutableNode parseInline(Env env, Source code, Node context, MaterializedFrame frame);

        public abstract String toStringIfVisible(Env env, Object obj, boolean checkVisibility);

        public abstract Object findMetaObject(Env env, Object value);

        public abstract SourceSection findSourceLocation(Env env, Object value);

        public abstract boolean isObjectOfLanguage(Env env, Object value);

        public abstract Object getContext(Env env);

        public abstract TruffleLanguage<?> getSPI(Env env);

        public abstract InstrumentInfo createInstrument(Object vmObject, String id, String name, String version);

        public abstract Object getVMObject(InstrumentInfo info);

        public abstract <S> S lookup(TruffleLanguage<?> languageEnsureInitialized, Class<S> type);

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

        public abstract Env patchEnvContext(Env env, OutputStream stdOut, OutputStream stdErr, InputStream stdIn, Map<String, Object> config, OptionValues options, String[] applicationArguments,
                        FileSystem fileSystem);

        public abstract boolean initializeMultiContext(TruffleLanguage<?> language);

        public abstract boolean isTruffleStackTrace(Throwable t);

        public abstract StackTraceElement[] getInternalStackTraceElements(Throwable t);

        public abstract void materializeHostFrames(Throwable original);

        public abstract boolean checkTruffleFile(File file);

        public abstract byte[] truffleFileContent(File file) throws IOException;

        public abstract void configureLoggers(Object polyglotContext, Map<String, Level> logLevels);

        public abstract TruffleLanguage<?> getLanguage(Env env);

        public abstract File asFile(TruffleFile file);

    }

    public abstract static class InstrumentSupport {

        public abstract void initializeInstrument(Object instrumentationHandler, Object key, Class<?> instrumentClass);

        public abstract void createInstrument(Object instrumentationHandler, Object key, String[] expectedServices, OptionValues options);

        public abstract void finalizeInstrument(Object instrumentationHandler, Object key);

        public abstract void disposeInstrument(Object instrumentationHandler, Object key, boolean cleanupRequired);

        public abstract <T> T getInstrumentationHandlerService(Object handler, Object key, Class<T> type);

        public abstract Object createInstrumentationHandler(Object vm, DispatchOutputStream out, DispatchOutputStream err, InputStream in);

        public abstract void collectEnvServices(Set<Object> collectTo, Object languageShared, TruffleLanguage<?> language);

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

    }

    public abstract static class Frames {
        protected abstract void markMaterializeCalled(FrameDescriptor descriptor);

        protected abstract boolean getMaterializeCalled(FrameDescriptor descriptor);
    }

    @CompilationFinal private static Accessor.LanguageSupport API;
    @CompilationFinal private static Accessor.EngineSupport SPI;
    private static Accessor.Nodes NODES;
    private static Accessor.InstrumentSupport INSTRUMENTHANDLER;
    private static Accessor.DumpSupport DUMP;
    private static Accessor.InteropSupport INTEROP;
    private static Accessor.Frames FRAMES;
    private static Accessor.SourceSupport SOURCE;

    static {
        TruffleLanguage<?> lng = new TruffleLanguage<Object>() {

            @Override
            protected boolean isObjectOfLanguage(Object object) {
                return false;
            }

            @Override
            protected Object createContext(TruffleLanguage.Env env) {
                return null;
            }

        };
        lng.hashCode();
        new Node() {
        }.getRootNode();

        conditionallyInitDebugger();
        conditionallyInitInterop();
        conditionallyInitInstrumentation();
        conditionallyInitSourceAccessor();
        if (TruffleOptions.TraceASTJSON) {
            try {
                Class.forName("com.oracle.truffle.api.utilities.JSONHelper", true, Accessor.class.getClassLoader());
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    @SuppressWarnings("all")
    private static void conditionallyInitDebugger() throws IllegalStateException {
        try {
            Class.forName("com.oracle.truffle.api.debug.Debugger", true, Accessor.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            boolean assertOn = false;
            assert assertOn = true;
            if (!assertOn) {
                throw new IllegalStateException(ex);
            }
        }
    }

    @SuppressWarnings("all")
    private static void conditionallyInitInstrumentation() throws IllegalStateException {
        try {
            Class.forName("com.oracle.truffle.api.instrumentation.InstrumentationHandler", true, Accessor.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            boolean assertOn = false;
            assert assertOn = true;
            if (!assertOn) {
                throw new IllegalStateException(ex);
            }
        }
    }

    @SuppressWarnings("all")
    private static void conditionallyInitSourceAccessor() throws IllegalStateException {
        try {
            Class.forName("com.oracle.truffle.api.source.Source", true, Accessor.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            boolean assertOn = false;
            assert assertOn = true;
            if (!assertOn) {
                throw new IllegalStateException(ex);
            }
        }
    }

    @SuppressWarnings("all")
    private static void conditionallyInitInterop() throws IllegalStateException {
        try {
            Class.forName("com.oracle.truffle.api.interop.ForeignAccess", true, Accessor.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            boolean assertOn = false;
            assert assertOn = true;
            if (!assertOn) {
                throw new IllegalStateException(ex);
            }
        }
    }

    protected Accessor() {
        if (!this.getClass().getName().startsWith("com.oracle.truffle") && !this.getClass().getName().startsWith("com.oracle.truffle.tck")) {
            throw new IllegalStateException();
        }
        String simpleName = this.getClass().getSimpleName();
        if (simpleName.endsWith("API")) {
            if (API != null) {
                throw new IllegalStateException();
            }
            API = this.languageSupport();
        } else if (simpleName.endsWith("Nodes")) {
            if (NODES != null) {
                throw new IllegalStateException();
            }
            NODES = this.nodes();
        } else if (simpleName.endsWith("InstrumentHandler")) {
            if (INSTRUMENTHANDLER != null) {
                throw new IllegalStateException();
            }
            INSTRUMENTHANDLER = this.instrumentSupport();
        } else if (simpleName.endsWith("Frames")) {
            if (FRAMES != null) {
                throw new IllegalStateException();
            }
            FRAMES = this.framesSupport();
        } else if (simpleName.endsWith("SourceAccessor")) {
            SOURCE = this.sourceSupport();
        } else if (simpleName.endsWith("DumpAccessor")) {
            DUMP = this.dumpSupport();
        } else if (simpleName.endsWith("InteropAccessor")) {
            INTEROP = this.interopSupport();
        } else if (simpleName.endsWith("ScopeAccessor")) {
            // O.K.
        } else if (simpleName.endsWith("AccessorDebug")) {
            // O.K.
        } else if (simpleName.endsWith("TruffleTCKAccessor")) {
            // O.K.
        } else if (simpleName.endsWith("TestAccessor")) {
            // O.K.
        } else {
            assert simpleName.endsWith("VMAccessor");
            SPI = this.engineSupport();
        }
    }

    protected Accessor.Nodes nodes() {
        return NODES;
    }

    protected LanguageSupport languageSupport() {
        return API;
    }

    protected DumpSupport dumpSupport() {
        return DUMP;
    }

    protected EngineSupport engineSupport() {
        return SPI;
    }

    protected InstrumentSupport instrumentSupport() {
        return INSTRUMENTHANDLER;
    }

    protected InteropSupport interopSupport() {
        return INTEROP;
    }

    protected SourceSupport sourceSupport() {
        return SOURCE;
    }

    static InstrumentSupport instrumentAccess() {
        return INSTRUMENTHANDLER;
    }

    static LanguageSupport languageAccess() {
        return API;
    }

    static EngineSupport engineAccess() {
        return SPI;
    }

    static Accessor.Nodes nodesAccess() {
        return NODES;
    }

    protected Accessor.Frames framesSupport() {
        return FRAMES;
    }

    static Accessor.Frames framesAccess() {
        return FRAMES;
    }

    /**
     * Don't call me. I am here only to let NetBeans debug any Truffle project.
     *
     * @param args
     */
    public static void main(String... args) {
        throw new IllegalStateException();
    }

    private static final TVMCI SUPPORT = Truffle.getRuntime().getCapability(TVMCI.class);

    protected OptionDescriptors getCompilerOptions() {
        if (SUPPORT == null) {
            return OptionDescriptors.EMPTY;
        }
        return SUPPORT.getCompilerOptionDescriptors();
    }

    protected boolean isGuestCallStackElement(StackTraceElement element) {
        if (SUPPORT == null) {
            return false;
        }
        return SUPPORT.isGuestCallStackFrame(element);
    }

    protected void initializeProfile(CallTarget target, Class<?>[] argmentTypes) {
        SUPPORT.initializeProfile(target, argmentTypes);
    }

    protected Object callProfiled(CallTarget target, Object... args) {
        return SUPPORT.callProfiled(target, args);
    }

    protected void onLoopCount(Node source, int iterations) {
        if (SUPPORT != null) {
            SUPPORT.onLoopCount(source, iterations);
        } else {
            // needs an additional compatibility check so older graal runtimes
            // still run with newer truffle versions
            RootNode root = source.getRootNode();
            if (root != null) {
                RootCallTarget target = root.getCallTarget();
                if (target instanceof com.oracle.truffle.api.LoopCountReceiver) {
                    ((com.oracle.truffle.api.LoopCountReceiver) target).reportLoopCount(iterations);
                }
            }
        }
    }

    /*
     * Do not remove: This is accessed reflectively in AccessorTest
     */
    static <T extends TruffleLanguage<?>> T findLanguageByClass(Object vm, Class<T> languageClass) {
        Env env = SPI.findEnv(vm, languageClass, true);
        TruffleLanguage<?> language = API.getLanguage(env);
        return languageClass.cast(language);
    }

}
