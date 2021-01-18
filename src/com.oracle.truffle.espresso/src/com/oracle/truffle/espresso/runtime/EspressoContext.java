/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.runtime;

import static com.oracle.truffle.espresso.jni.JniEnv.JNI_OK;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.ReferenceQueue;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.graalvm.polyglot.Engine;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.EspressoBindings;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.descriptors.Names;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.ClassRegistries;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.jdwp.api.VMListener;
import com.oracle.truffle.espresso.jdwp.impl.EmptyListener;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.perf.DebugCloseable;
import com.oracle.truffle.espresso.perf.DebugTimer;
import com.oracle.truffle.espresso.perf.TimerCollection;
import com.oracle.truffle.espresso.substitutions.Substitutions;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Thread;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_ref_Reference;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.vm.VM;

public final class EspressoContext {

    public static final int DEFAULT_STACK_SIZE = 32;
    public static final StackTraceElement[] EMPTY_STACK = new StackTraceElement[0];

    private static final DebugTimer SPAWN_VM = DebugTimer.create("spawnVM");
    private static final DebugTimer SYSTEM_INIT = DebugTimer.create("system init", SPAWN_VM);
    private static final DebugTimer KNOWN_CLASS_INIT = DebugTimer.create("known class init", SPAWN_VM);
    private static final DebugTimer META_INIT = DebugTimer.create("meta init", SPAWN_VM);
    private static final DebugTimer VM_INIT = DebugTimer.create("vm init", SPAWN_VM);
    private static final DebugTimer SYSTEM_CLASSLOADER = DebugTimer.create("system classloader", SPAWN_VM);

    private final TruffleLogger logger = TruffleLogger.getLogger(EspressoLanguage.ID);

    private final EspressoLanguage language;
    private final TruffleLanguage.Env env;

    private String[] mainArguments;

    // region Debug
    private final TimerCollection timers;
    // endregion Debug

    // region Profiling
    private final AllocationReporter allocationReporter;
    // endregion Profiling

    // region Runtime
    private final StringTable strings;
    private final ClassRegistries registries;
    private final Substitutions substitutions;
    private final MethodHandleIntrinsics methodHandleIntrinsics;
    // endregion Runtime

    // region Helpers
    private final EspressoThreadManager threadManager;
    private final EspressoShutdownHandler shutdownManager;
    private final EspressoReferenceDrainer referenceDrainer;
    // endregion Helpers

    // region ID
    private final AtomicInteger klassIdProvider = new AtomicInteger();
    private final AtomicInteger loaderIdProvider = new AtomicInteger();
    private final int bootClassLoaderID = getNewLoaderId();
    // endregion ID

    // region InitControl
    public long initDoneTimeNanos;

    @CompilationFinal private boolean modulesInitialized = false;
    @CompilationFinal private boolean metaInitialized = false;
    private boolean initialized = false;
    private Classpath bootClasspath;
    // endregion InitControl

    // region JDWP
    private JDWPContextImpl jdwpContext;
    private VMListener eventListener;
    // endregion JDWP

    // region Options
    // Checkstyle: stop field name check

    // Performance control
    public final boolean InlineFieldAccessors;
    public final boolean InlineMethodHandle;
    public final boolean SplitMethodHandles;
    public final EspressoOptions.LivenessAnalysisMode livenessAnalysisMode;

    // Behavior control
    public final boolean EnableManagement;
    public final boolean MultiThreaded;
    public final EspressoOptions.VerifyMode Verify;
    public final EspressoOptions.SpecCompliancyMode SpecCompliancyMode;
    public final boolean IsolatedNamespace;
    public final boolean Polyglot;
    public final boolean ExitHost;

    // Debug option
    public final com.oracle.truffle.espresso.jdwp.api.JDWPOptions JDWPOptions;

    // Checkstyle: resume field name check
    // endregion Options

    // Must be initialized after the context instance creation.

    // region VM
    @CompilationFinal private Meta meta;
    @CompilationFinal private VM vm;
    @CompilationFinal private JniEnv jniEnv;
    @CompilationFinal private InterpreterToVM interpreterToVM;
    @CompilationFinal private JImageLibrary jimageLibrary;
    @CompilationFinal private EspressoProperties vmProperties;
    @CompilationFinal private JavaVersion javaVersion;
    // endregion VM

    @CompilationFinal private EspressoException stackOverflow;
    @CompilationFinal private EspressoException outOfMemory;

    // region ThreadDeprecated
    // Set on calling guest Thread.stop0(), or when closing context.
    @CompilationFinal private Assumption noThreadStop = Truffle.getRuntime().createAssumption();
    @CompilationFinal private Assumption noSuspend = Truffle.getRuntime().createAssumption();
    @CompilationFinal private Assumption noThreadDeprecationCalled = Truffle.getRuntime().createAssumption();
    // endregion ThreadDeprecated

    @CompilationFinal private TruffleObject topBindings;

    public TruffleLogger getLogger() {
        return logger;
    }

    public int getNewKlassId() {
        return klassIdProvider.getAndIncrement();
    }

    public int getNewLoaderId() {
        return loaderIdProvider.getAndIncrement();
    }

    public int getBootClassLoaderID() {
        return bootClassLoaderID;
    }

    public EspressoContext(TruffleLanguage.Env env, EspressoLanguage language) {
        this.env = env;
        this.language = language;

        this.registries = new ClassRegistries(this);
        this.strings = new StringTable(this);
        this.substitutions = new Substitutions(this);
        this.methodHandleIntrinsics = new MethodHandleIntrinsics(this);

        this.threadManager = new EspressoThreadManager(this);
        this.referenceDrainer = new EspressoReferenceDrainer(this);

        boolean softExit = env.getOptions().get(EspressoOptions.SoftExit);
        this.ExitHost = env.getOptions().get(EspressoOptions.ExitHost);
        this.shutdownManager = new EspressoShutdownHandler(this, threadManager, referenceDrainer, softExit);

        this.timers = TimerCollection.create(env.getOptions().get(EspressoOptions.EnableTimers));
        this.allocationReporter = env.lookup(AllocationReporter.class);

        // null if not specified
        this.JDWPOptions = env.getOptions().get(EspressoOptions.JDWPOptions);

        this.InlineFieldAccessors = JDWPOptions == null && env.getOptions().get(EspressoOptions.InlineFieldAccessors);
        this.InlineMethodHandle = JDWPOptions == null && env.getOptions().get(EspressoOptions.InlineMethodHandle);
        this.SplitMethodHandles = JDWPOptions == null && env.getOptions().get(EspressoOptions.SplitMethodHandles);
        this.Verify = env.getOptions().get(EspressoOptions.Verify);
        this.SpecCompliancyMode = env.getOptions().get(EspressoOptions.SpecCompliancy);
        this.livenessAnalysisMode = env.getOptions().get(EspressoOptions.LivenessAnalysis);
        this.EnableManagement = env.getOptions().get(EspressoOptions.EnableManagement);
        if (!env.isCreateThreadAllowed()) {
            if (env.getOptions().hasBeenSet(EspressoOptions.MultiThreaded)) {
                throw new IllegalStateException("Creating threads is not allowed by the env; cannot set 'MultiThreaded=true'");
            } else {
                this.MultiThreaded = false;
            }
        } else {
            this.MultiThreaded = env.getOptions().get(EspressoOptions.MultiThreaded);
        }
        this.Polyglot = env.getOptions().get(EspressoOptions.Polyglot);

        // Isolated (native) namespaces via dlmopen is only supported on Linux.
        this.IsolatedNamespace = env.getOptions().get(EspressoOptions.UseTruffleNFIIsolatedNamespace) && OS.getCurrent() == OS.Linux;

    }

    public ClassRegistries getRegistries() {
        return registries;
    }

    public InputStream in() {
        return env.in();
    }

    public OutputStream out() {
        return env.out();
    }

    public OutputStream err() {
        return env.err();
    }

    public StringTable getStrings() {
        return strings;
    }

    public TruffleLanguage.Env getEnv() {
        return env;
    }

    public EspressoLanguage getLanguage() {
        return language;
    }

    /**
     * @return The {@link String}[] array passed to the main function.
     */
    public String[] getMainArguments() {
        return mainArguments;
    }

    public void setMainArguments(String[] mainArguments) {
        this.mainArguments = mainArguments;
    }

    public Classpath getBootClasspath() {
        if (bootClasspath == null) {
            CompilerAsserts.neverPartOfCompilation();
            bootClasspath = new Classpath(
                            getVmProperties().bootClasspath().stream().map(new Function<Path, String>() {
                                @Override
                                public String apply(Path path) {
                                    return path.toString();
                                }
                            }).collect(Collectors.joining(File.pathSeparator)));
        }
        return bootClasspath;
    }

    public void setBootClassPath(Classpath classPath) {
        this.bootClasspath = classPath;
    }

    public EspressoProperties getVmProperties() {
        assert vmProperties != null;
        return vmProperties;
    }

    public void initializeContext() {
        EspressoError.guarantee(getEnv().isNativeAccessAllowed(),
                        "Native access is not allowed by the host environment but it's required to load Espresso/Java native libraries. " +
                                        "Allow native access on context creation e.g. contextBuilder.allowNativeAccess(true)");
        assert !this.initialized;
        eventListener = new EmptyListener();
        // Inject PublicFinalReference in the host VM.
        Target_java_lang_ref_Reference.ensureInitialized();
        spawnVM();
        this.initialized = true;
        this.jdwpContext = new JDWPContextImpl(this);
        this.eventListener = jdwpContext.jdwpInit(env, getMainThread());
        referenceDrainer.startReferenceDrain();
    }

    public VMListener getJDWPListener() {
        return eventListener;
    }

    public Source findOrCreateSource(Method method) {
        String sourceFile = method.getSourceFile();
        if (sourceFile == null) {
            return null;
        } else {
            TruffleFile file = env.getInternalTruffleFile(sourceFile);
            Source source = Source.newBuilder("java", file).content(Source.CONTENT_NONE).build();
            // sources are interned so no cache needed (hopefully)
            return source;
        }
    }

    public Meta getMeta() {
        return meta;
    }

    @SuppressWarnings("try")
    private void spawnVM() {
        try (DebugCloseable spawn = SPAWN_VM.scope(timers)) {

            long initStartTimeNanos = System.nanoTime();

            initVmProperties();

            if (getJavaVersion().modulesEnabled()) {
                registries.initJavaBaseModule();
                registries.getBootClassRegistry().initUnnamedModule(StaticObject.NULL);
            }

            // Spawn JNI first, then the VM.
            try (DebugCloseable vmInit = VM_INIT.scope(timers)) {
                this.vm = VM.create(getJNI()); // Mokapot is loaded
                vm.attachThread(Thread.currentThread());
            }

            // TODO: link libjimage

            try (DebugCloseable metaInit = META_INIT.scope(timers)) {
                this.meta = new Meta(this);
            }
            this.metaInitialized = true;

            this.interpreterToVM = new InterpreterToVM(this);

            try (DebugCloseable knownClassInit = KNOWN_CLASS_INIT.scope(timers)) {
                initializeKnownClass(Type.java_lang_Object);

                for (Symbol<Type> type : Arrays.asList(
                                Type.java_lang_String,
                                Type.java_lang_System,
                                Type.java_lang_ThreadGroup,
                                Type.java_lang_Thread,
                                Type.java_lang_Class,
                                Type.java_lang_reflect_Method)) {
                    initializeKnownClass(type);
                }
            }

            threadManager.createMainThread(meta);

            try (DebugCloseable knownClassInit = KNOWN_CLASS_INIT.scope(timers)) {
                initializeKnownClass(Type.java_lang_ref_Finalizer);
            }

            referenceDrainer.initReferenceDrain();

            try (DebugCloseable systemInit = SYSTEM_INIT.scope(timers)) {
                // Call guest initialization
                if (getJavaVersion().java8OrEarlier()) {
                    meta.java_lang_System_initializeSystemClass.invokeDirect(null);
                } else {
                    assert getJavaVersion().java9OrLater();
                    meta.java_lang_System_initPhase1.invokeDirect(null);
                    int e = (int) meta.java_lang_System_initPhase2.invokeDirect(null, false, false);
                    if (e != 0) {
                        throw EspressoError.shouldNotReachHere();
                    }
                    modulesInitialized = true;
                    meta.java_lang_System_initPhase3.invokeDirect(null);
                }
            }

            meta.postSystemInit();

            try (DebugCloseable knownClassInit = KNOWN_CLASS_INIT.scope(timers)) {
                // System exceptions.
                for (Symbol<Type> type : Arrays.asList(
                                Type.java_lang_OutOfMemoryError,
                                Type.java_lang_NullPointerException,
                                Type.java_lang_ClassCastException,
                                Type.java_lang_ArrayStoreException,
                                Type.java_lang_ArithmeticException,
                                Type.java_lang_StackOverflowError,
                                Type.java_lang_IllegalMonitorStateException,
                                Type.java_lang_IllegalArgumentException)) {
                    initializeKnownClass(type);
                }
            }
            // Init memoryError instances
            StaticObject stackOverflowErrorInstance = meta.java_lang_StackOverflowError.allocateInstance();
            StaticObject outOfMemoryErrorInstance = meta.java_lang_OutOfMemoryError.allocateInstance();

            // Preemptively set stack trace.
            stackOverflowErrorInstance.setHiddenField(meta.HIDDEN_FRAMES, VM.StackTrace.EMPTY_STACK_TRACE);
            stackOverflowErrorInstance.setField(meta.java_lang_Throwable_backtrace, stackOverflowErrorInstance);
            outOfMemoryErrorInstance.setHiddenField(meta.HIDDEN_FRAMES, VM.StackTrace.EMPTY_STACK_TRACE);
            outOfMemoryErrorInstance.setField(meta.java_lang_Throwable_backtrace, outOfMemoryErrorInstance);

            this.stackOverflow = EspressoException.wrap(stackOverflowErrorInstance);
            this.outOfMemory = EspressoException.wrap(outOfMemoryErrorInstance);
            meta.java_lang_StackOverflowError.lookupDeclaredMethod(Name._init_, Signature._void_String).invokeDirect(stackOverflowErrorInstance, meta.toGuestString("VM StackOverFlow"));
            meta.java_lang_OutOfMemoryError.lookupDeclaredMethod(Name._init_, Signature._void_String).invokeDirect(outOfMemoryErrorInstance, meta.toGuestString("VM OutOfMemory"));

            // Create application (system) class loader.
            StaticObject systemClassLoader = null;
            try (DebugCloseable systemLoader = SYSTEM_CLASSLOADER.scope(timers)) {
                systemClassLoader = (StaticObject) meta.java_lang_ClassLoader_getSystemClassLoader.invokeDirect(null);
            }
            topBindings = new EspressoBindings(systemClassLoader, getEnv().getOptions().get(EspressoOptions.ExposeNativeJavaVM));

            initDoneTimeNanos = System.nanoTime();
            long elapsedNanos = initDoneTimeNanos - initStartTimeNanos;
            getLogger().log(Level.FINE, "VM booted in {0} ms", TimeUnit.NANOSECONDS.toMillis(elapsedNanos));
        }
    }

    private void initVmProperties() {
        final EspressoProperties.Builder builder = EspressoProperties.newPlatformBuilder();
        // If --java.JavaHome is not specified, Espresso tries to use the same (jars and native)
        // libraries bundled with GraalVM.
        builder.javaHome(Engine.findHome());
        vmProperties = EspressoProperties.processOptions(getLanguage(), builder, getEnv().getOptions()).build();
        javaVersion = new JavaVersion(vmProperties.bootClassPathType().getJavaVersion());
    }

    private void initializeKnownClass(Symbol<Type> type) {
        Klass klass = getMeta().loadKlassOrFail(type, StaticObject.NULL, StaticObject.NULL);
        klass.safeInitialize();
    }

    public boolean metaInitialized() {
        return metaInitialized;
    }

    public boolean modulesInitialized() {
        return modulesInitialized;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public InterpreterToVM getInterpreterToVM() {
        return interpreterToVM;
    }

    public VM getVM() {
        return vm;
    }

    public JImageLibrary jimageLibrary() {
        if (jimageLibrary == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            EspressoError.guarantee(getJavaVersion().modulesEnabled(), "Jimage available for java >= 9");
            this.jimageLibrary = new JImageLibrary(this);
        }
        return jimageLibrary;
    }

    public JavaVersion getJavaVersion() {
        return javaVersion;
    }

    public Types getTypes() {
        return getLanguage().getTypes();
    }

    public Signatures getSignatures() {
        return getLanguage().getSignatures();
    }

    public JniEnv getJNI() {
        if (jniEnv == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            jniEnv = JniEnv.create(this);
        }
        return jniEnv;
    }

    public void disposeContext() {
        if (initialized) {
            getVM().dispose();
            getJNI().dispose();
        }
    }

    public Substitutions getSubstitutions() {
        return substitutions;
    }

    public void setBootstrapMeta(Meta meta) {
        this.meta = meta;
    }

    public Names getNames() {
        return getLanguage().getNames();
    }

    public MethodHandleIntrinsics getMethodHandleIntrinsics() {
        return methodHandleIntrinsics;
    }

    public EspressoException getStackOverflow() {
        return stackOverflow;
    }

    public EspressoException getOutOfMemory() {
        return outOfMemory;
    }

    public <T> T trackAllocation(T object) {
        if (allocationReporter != null) {
            allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
            allocationReporter.onReturnValue(object, 0, AllocationReporter.SIZE_UNKNOWN);
        }
        return object;
    }

    public void prepareDispose() {
        jdwpContext.finalizeContext();
    }

    // region Thread management

    /**
     * Creates a new guest thread from the host thread, and adds it to the main thread group.
     */
    public StaticObject createThread(Thread hostThread) {
        return threadManager.createGuestThreadFromHost(hostThread, meta, vm);
    }

    public StaticObject createThread(Thread hostThread, StaticObject group, String name) {
        return threadManager.createGuestThreadFromHost(hostThread, meta, vm, name, group);
    }

    public void disposeThread(@SuppressWarnings("unused") Thread hostThread) {
        StaticObject guestThread = getGuestThreadFromHost(hostThread);
        if (guestThread == null) {
            return;
        }
        if (hostThread != Thread.currentThread()) {
            String guestName = Target_java_lang_Thread.getThreadName(meta, guestThread);
            getLogger().warning("unimplemented: disposeThread for non-current thread: " + hostThread + " / " + guestName);
            return;
        }
        if (vm.DetachCurrentThread() != JNI_OK) {
            throw new RuntimeException("Could not detach thread correctly");
        }
    }

    public StaticObject getGuestThreadFromHost(Thread host) {
        return threadManager.getGuestThreadFromHost(host);
    }

    public StaticObject getCurrentThread() {
        return threadManager.getGuestThreadFromHost(Thread.currentThread());
    }

    /**
     * Returns the maximum number of alive (registered) threads at any point, since the VM started.
     */
    public long getPeakThreadCount() {
        return threadManager.peakThreadCount.get();
    }

    /**
     * Returns the number of created threads since the VM started.
     */
    public long getCreatedThreadCount() {
        return threadManager.createdThreadCount.get();
    }

    public StaticObject[] getActiveThreads() {
        return threadManager.activeThreads();
    }

    public void registerThread(Thread host, StaticObject self) {
        threadManager.registerThread(host, self);
        if (eventListener != null) {
            eventListener.threadStarted(self);
        }
    }

    public void unregisterThread(StaticObject self) {
        threadManager.unregisterThread(self);
        if (eventListener != null) {
            eventListener.threadDied(self);
        }
    }

    public void invalidateNoThreadStop(String message) {
        noThreadDeprecationCalled.invalidate();
        noThreadStop.invalidate(message);
    }

    public boolean shouldCheckStop() {
        return !noThreadStop.isValid();
    }

    public void invalidateNoSuspend(String message) {
        noThreadDeprecationCalled.invalidate();
        noSuspend.invalidate(message);
    }

    public boolean shouldCheckDeprecationStatus() {
        return !noThreadDeprecationCalled.isValid();
    }

    public boolean shouldCheckSuspend() {
        return !noSuspend.isValid();
    }

    public boolean isMainThreadCreated() {
        return threadManager.isMainThreadCreated();
    }

    public StaticObject getMainThread() {
        return threadManager.getMainThread();
    }

    public StaticObject getMainThreadGroup() {
        return threadManager.getMainThreadGroup();
    }

    // endregion Thread management

    // region Shutdown

    public Object getShutdownSynchronizer() {
        return shutdownManager.getShutdownSynchronizer();
    }

    public void doExit(int code) {
        shutdownManager.doExit(code);
    }

    public void destroyVM(boolean killThreads) {
        shutdownManager.destroyVM(killThreads);
    }

    public boolean isClosing() {
        return shutdownManager.isClosing();
    }

    public int getExitStatus() {
        return shutdownManager.getExitStatus();
    }

    // endregion Shutdown

    // region ReferenceDrain

    public ReferenceQueue<StaticObject> getReferenceQueue() {
        return referenceDrainer.getReferenceQueue();
    }

    public StaticObject getAndClearReferencePendingList() {
        return referenceDrainer.getAndClearReferencePendingList();
    }

    public boolean hasReferencePendingList() {
        return referenceDrainer.hasReferencePendingList();
    }

    public void waitForReferencePendingList() {
        referenceDrainer.waitForReferencePendingList();
    }

    // endregion ReferenceDrain

    // region DebugAccess

    public TimerCollection getTimers() {
        return timers;
    }

    public TruffleObject getBindings() {
        return topBindings;
    }

    // endregion DebugAccess
}
