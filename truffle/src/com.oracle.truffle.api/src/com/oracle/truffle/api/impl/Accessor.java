/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Value;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Communication between PolyglotEngine, TruffleLanguage API/SPI, and other services.
 */
public abstract class Accessor {

    @SuppressWarnings("all")
    protected final Collection<ClassLoader> loaders() {
        return TruffleLocator.loaders();
    }

    public abstract static class Nodes {

        public abstract boolean isInstrumentable(RootNode rootNode);

        public abstract boolean isTaggedWith(Node node, Class<?> tag);

        public abstract boolean isCloneUninitializedSupported(RootNode rootNode);

        public abstract RootNode cloneUninitialized(RootNode rootNode);

        public abstract Object getEngineObject(LanguageInfo languageInfo);

        public abstract TruffleLanguage<?> getLanguageSpi(LanguageInfo languageInfo);

        public abstract void setLanguageSpi(LanguageInfo languageInfo, TruffleLanguage<?> spi);

        public abstract LanguageInfo createLanguage(Object vmObject, String id, String name, String version, Set<String> mimeTypes);

        public abstract Object getSourceVM(RootNode rootNode);

        public abstract int getRootNodeBits(RootNode root);

        public abstract void setRootNodeBits(RootNode root, int bits);
    }

    public abstract static class DumpSupport {
        public abstract void dump(Node newNode, Node newChild, CharSequence reason);
    }

    public abstract static class InteropSupport {
        public abstract boolean canHandle(Object foreignAccess, Object receiver);

        public abstract CallTarget canHandleTarget(Object access);

        public abstract boolean isTruffleObject(Object value);
    }

    public abstract static class JavaInteropSupport {
        public abstract Node createToJavaNode();

        public abstract Object toJava(Node toJavaNode, Class<?> type, Object value);

        public abstract Object toJavaGuestObject(Object obj, Object languageContext);
    }

    public abstract static class EngineSupport {
        public static final int EXECUTION_EVENT = 1;
        public static final int SUSPENDED_EVENT = 2;

        @SuppressWarnings("deprecation")
        public abstract <C> com.oracle.truffle.api.impl.FindContextNode<C> createFindContextNode(TruffleLanguage<C> lang);

        @SuppressWarnings("rawtypes")
        public abstract Env findEnv(Object vm, Class<? extends TruffleLanguage> languageClass, boolean failIfNotFound);

        public abstract Object getInstrumentationHandler(Object languageShared);

        public abstract Iterable<? extends Object> importSymbols(Object languageShared, Env env, String globalName);

        public abstract void exportSymbol(Object vmObject, String symbolName, Object value);

        public abstract Object importSymbol(Object vmObject, Env env, String symbolName);

        public abstract Object lookupSymbol(Object vmObject, Env env, LanguageInfo language, String symbolName);

        public abstract boolean isMimeTypeSupported(Object languageShared, String mimeType);

        public abstract void registerDebugger(Object vm, Object debugger);

        public abstract boolean isEvalRoot(RootNode target);

        public abstract Object findOriginalObject(Object truffleObject);

        public abstract CallTarget lookupOrRegisterComputation(Object truffleObject, RootNode symbolNode, Object... keyOrKeys);

        @SuppressWarnings("static-method")
        public final void attachOutputConsumer(DispatchOutputStream dos, OutputStream out) {
            dos.attach(out);
        }

        @SuppressWarnings("static-method")
        public final void detachOutputConsumer(DispatchOutputStream dos, OutputStream out) {
            dos.detach(out);
        }

        public abstract Object getCurrentVM();

        public abstract Env getEnvForLanguage(Object languageShared, String mimeType);

        public abstract Env getEnvForInstrument(Object vm, String mimeType);

        public abstract Env getEnvForInstrument(LanguageInfo language);

        public abstract LanguageInfo getObjectLanguage(Object obj, Object vmObject);

        public abstract Object contextReferenceGet(Object reference);

        public abstract boolean isDisposed(Object vmInstance);

        public abstract Map<String, LanguageInfo> getLanguages(Object vmInstance);

        public abstract Map<String, InstrumentInfo> getInstruments(Object vmInstance);

        public abstract <T> T lookup(InstrumentInfo info, Class<T> serviceClass);

        public abstract <S> S lookup(LanguageInfo language, Class<S> type);

        public abstract <T extends TruffleLanguage<?>> T getCurrentLanguage(Class<T> languageClass);

        public abstract <C, T extends TruffleLanguage<C>> C getCurrentContext(Class<T> languageClass);

        public abstract Value toHostValue(Object obj, Object languageContext);

        public abstract Object toGuestValue(Object obj, Object languageContext);

        public abstract Object getVMFromLanguageObject(Object engineObject);

        public abstract OptionValues getCompilerOptionValues(RootNode rootNode);

        public abstract Object lookupHostSymbol(Object vmObject, Env env, String symbolName);

        public abstract boolean isHostAccessAllowed(Object vmObject, Env env);

        public abstract Object createInternalContext(Object vmObject, Map<String, Object> config);

        public abstract Object enterInternalContext(Object impl);

        public abstract void leaveInternalContext(Object impl, Object prev);

        public abstract void closeInternalContext(Object impl);

        public abstract boolean isCreateThreadAllowed(Object vmObject);

        public abstract Thread createThread(Object vmObject, Runnable runnable);

    }

    public abstract static class LanguageSupport {

        public abstract void initializeLanguage(LanguageInfo language, TruffleLanguage<?> impl, boolean legacyLanguage);

        public abstract Env createEnv(Object vmObject, LanguageInfo info, OutputStream stdOut, OutputStream stdErr, InputStream stdIn, Map<String, Object> config, OptionValues options,
                        String[] applicationArguments);

        public abstract Object createEnvContext(Env localEnv);

        public abstract void postInitEnv(Env env);

        public abstract Object evalInContext(String code, Node node, MaterializedFrame frame);

        public abstract Object findExportedSymbol(TruffleLanguage.Env env, String globalName, boolean onlyExplicit);

        public abstract Object lookupSymbol(TruffleLanguage.Env env, String globalName);

        public abstract Object languageGlobal(TruffleLanguage.Env env);

        public abstract void dispose(Env env);

        public abstract LanguageInfo getLanguageInfo(TruffleLanguage.Env env);

        public abstract LanguageInfo getLanguageInfo(TruffleLanguage<?> language);

        public abstract LanguageInfo getLegacyLanguageInfo(Object vm, @SuppressWarnings("rawtypes") Class<? extends TruffleLanguage> languageClass);

        public abstract CallTarget parse(Env env, Source code, Node context, String... argumentNames);

        public abstract String toStringIfVisible(Env env, Object obj, boolean checkVisibility);

        public abstract Object findMetaObject(Env env, Object value);

        public abstract SourceSection findSourceLocation(Env env, Object value);

        public abstract boolean isObjectOfLanguage(Env env, Object value);

        public abstract Object getContext(Env env);

        public abstract TruffleLanguage<?> getSPI(Env env);

        public abstract InstrumentInfo createInstrument(Object vmObject, String id, String name, String version);

        public abstract Object getVMObject(InstrumentInfo info);

        public abstract <S> S lookup(LanguageInfo languageEnsureInitialized, Class<S> type);

        public abstract boolean isContextInitialized(Env env);

        public abstract OptionDescriptors describeOptions(TruffleLanguage<?> language, String requiredGroup);

        public abstract void onThrowable(RootNode root, Throwable e);

        public abstract boolean isThreadAccessAllowed(LanguageInfo env, Thread current, boolean singleThread);

        public abstract void initializeThread(Env env, Thread current);

        public abstract void initializeMultiThreading(Env env);

        public abstract void disposeThread(Env env, Thread thread);

    }

    public abstract static class InstrumentSupport {

        public abstract void initializeInstrument(Object instrumentationHandler, Object key, Class<?> instrumentClass);

        public abstract void createInstrument(Object instrumentationHandler, Object key, String[] expectedServices, OptionValues options);

        public abstract void disposeInstrument(Object instrumentationHandler, Object key, boolean cleanupRequired);

        public abstract <T> T getInstrumentationHandlerService(Object handler, Object key, Class<T> type);

        public abstract Object createInstrumentationHandler(Object vm, DispatchOutputStream out, DispatchOutputStream err, InputStream in);

        public abstract void collectEnvServices(Set<Object> collectTo, Object languageShared, LanguageInfo languageInfo);

        public abstract void onFirstExecution(RootNode rootNode);

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

        public abstract OptionDescriptors describeOptions(Object instrumentationHandler, Object key, String requiredGroup);

        public abstract Object getEngineInstrumenter(Object instrumentationHandler);

        public abstract void onNodeInserted(RootNode rootNode, Node tree);

    }

    protected abstract static class Frames {
        protected abstract void markMaterializeCalled(FrameDescriptor descriptor);

        protected abstract boolean getMaterializeCalled(FrameDescriptor descriptor);
    }

    private static Accessor.LanguageSupport API;
    @CompilationFinal private static Accessor.EngineSupport SPI;
    private static Accessor.Nodes NODES;
    private static Accessor.InstrumentSupport INSTRUMENTHANDLER;
    private static Accessor.DumpSupport DUMP;
    private static Accessor.InteropSupport INTEROP;
    private static Accessor.JavaInteropSupport JAVAINTEROP;
    private static Accessor.Frames FRAMES;
    @SuppressWarnings("unused") private static Accessor SOURCE;

    static {
        TruffleLanguage<?> lng = new TruffleLanguage<Object>() {

            @Override
            protected Object getLanguageGlobal(Object context) {
                return null;
            }

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
        conditionallyInitJavaInterop();
        conditionallyInitInstrumentation();
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
    private static void conditionallyInitEngine() throws IllegalStateException {
        try {
            Class.forName("com.oracle.truffle.api.vm.PolyglotEngine", true, Accessor.class.getClassLoader());
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

    @SuppressWarnings("all")
    private static void conditionallyInitJavaInterop() throws IllegalStateException {
        try {
            Class.forName("com.oracle.truffle.api.interop.java.JavaInterop", true, Accessor.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            boolean assertOn = false;
            assert assertOn = true;
            if (!assertOn) {
                throw new IllegalStateException(ex);
            }
        }
    }

    protected Accessor() {
        if (!this.getClass().getName().startsWith("com.oracle.truffle.api") && !this.getClass().getName().startsWith("com.oracle.truffle.tck")) {
            throw new IllegalStateException();
        }
        if (this.getClass().getSimpleName().endsWith("API")) {
            if (API != null) {
                throw new IllegalStateException();
            }
            API = this.languageSupport();
        } else if (this.getClass().getSimpleName().endsWith("Nodes")) {
            if (NODES != null) {
                throw new IllegalStateException();
            }
            NODES = this.nodes();
        } else if (this.getClass().getSimpleName().endsWith("InstrumentHandler")) {
            if (INSTRUMENTHANDLER != null) {
                throw new IllegalStateException();
            }
            INSTRUMENTHANDLER = this.instrumentSupport();
        } else if (this.getClass().getSimpleName().endsWith("Frames")) {
            if (FRAMES != null) {
                throw new IllegalStateException();
            }
            FRAMES = this.framesSupport();
        } else if (this.getClass().getSimpleName().endsWith("SourceAccessor")) {
            SOURCE = this;
        } else if (this.getClass().getSimpleName().endsWith("DumpAccessor")) {
            DUMP = this.dumpSupport();
        } else if (this.getClass().getSimpleName().endsWith("JavaInteropAccessor")) {
            JAVAINTEROP = this.javaInteropSupport();
        } else if (this.getClass().getSimpleName().endsWith("InteropAccessor")) {
            INTEROP = this.interopSupport();
        } else if (this.getClass().getSimpleName().endsWith("ScopeAccessor")) {
            // O.K.
        } else if (this.getClass().getSimpleName().endsWith("AccessorDebug")) {
            // O.K.
        } else if (this.getClass().getSimpleName().endsWith("TruffleTCKAccessor")) {
            // O.K.
        } else {
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

    protected JavaInteropSupport javaInteropSupport() {
        return JAVAINTEROP;
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

    @SuppressWarnings("deprecation")
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
        TruffleLanguage<?> language = NODES.getLanguageSpi(API.getLanguageInfo(env));
        return languageClass.cast(language);
    }

}
