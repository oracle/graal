/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.oracle.truffle.espresso.ffi.nfi.NFIIsolatedNativeAccess;
import com.oracle.truffle.espresso.ffi.nfi.NFINativeAccess;
import com.oracle.truffle.espresso.ffi.nfi.NFISulongNativeAccess;
import org.graalvm.options.OptionMap;
import org.graalvm.polyglot.Engine;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.EspressoBindings;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.FinalizationSupport;
import com.oracle.truffle.espresso.analysis.hierarchy.ClassHierarchyOracle;
import com.oracle.truffle.espresso.analysis.hierarchy.DefaultClassHierarchyOracle;
import com.oracle.truffle.espresso.analysis.hierarchy.NoOpClassHierarchyOracle;
import com.oracle.truffle.espresso.descriptors.Names;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.ffi.NativeAccess;
import com.oracle.truffle.espresso.ffi.NativeAccessCollector;
import com.oracle.truffle.espresso.impl.ClassRegistries;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.jdwp.api.Ids;
import com.oracle.truffle.espresso.jdwp.api.VMEventListenerImpl;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.perf.DebugCloseable;
import com.oracle.truffle.espresso.perf.DebugTimer;
import com.oracle.truffle.espresso.perf.TimerCollection;
import com.oracle.truffle.espresso.redefinition.ClassRedefinition;
import com.oracle.truffle.espresso.redefinition.plugins.api.InternalRedefinitionPlugin;
import com.oracle.truffle.espresso.redefinition.plugins.impl.RedefinitionPluginHandler;
import com.oracle.truffle.espresso.substitutions.Substitutions;
import com.oracle.truffle.espresso.threads.EspressoThreadRegistry;
import com.oracle.truffle.espresso.threads.ThreadsAccess;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.vm.UnsafeAccess;
import com.oracle.truffle.espresso.vm.VM;

import sun.misc.SignalHandler;

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
    private String[] vmArguments;

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
    private final ClassHierarchyOracle classHierarchyOracle;
    // endregion Runtime

    // region Helpers
    private final EspressoThreadRegistry threadRegistry;
    @CompilationFinal private ThreadsAccess threads;
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
    private final JDWPContextImpl jdwpContext;
    private final boolean shouldReportVMEvents;
    private final VMEventListenerImpl eventListener;
    private ClassRedefinition classRedefinition;
    // endregion JDWP

    private Map<Class<? extends InternalRedefinitionPlugin>, InternalRedefinitionPlugin> redefinitionPlugins;

    // After a context is finalized, guest code cannot be executed.
    private volatile boolean isFinalized;

    // region Options
    // Checkstyle: stop field name check

    // Performance control
    public final boolean InlineFieldAccessors;
    public final boolean InlineMethodHandle;
    public final boolean SplitMethodHandles;
    public final boolean livenessAnalysis;
    public final boolean EnableClassHierarchyAnalysis;

    // Behavior control
    public final boolean EnableManagement;
    public final EspressoOptions.VerifyMode Verify;
    public final EspressoOptions.SpecCompliancyMode SpecCompliancyMode;
    public final boolean Polyglot;
    public final boolean HotSwapAPI;
    public final boolean ExitHost;
    public final boolean EnableSignals;
    private final String multiThreadingDisabled;
    public final boolean NativeAccessAllowed;
    public final boolean EnableAgents;
    public final int TrivialMethodSize;

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
    @CompilationFinal private AgentLibraries agents;
    @CompilationFinal private NativeAccess nativeAccess;
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
    private final WeakHashMap<StaticObject, SignalHandler> hostSignalHandlers = new WeakHashMap<>();

    public TruffleLogger getLogger() {
        return logger;
    }

    public int getNewKlassId() {
        int id = klassIdProvider.getAndIncrement();
        if (id < 0) {
            throw EspressoError.shouldNotReachHere("Exhausted klass IDs");
        }
        return id;
    }

    public int getNewLoaderId() {
        int id = loaderIdProvider.getAndIncrement();
        if (id < 0) {
            throw EspressoError.shouldNotReachHere("Exhausted loader IDs");
        }
        return id;
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

        this.threadRegistry = new EspressoThreadRegistry(this);
        this.referenceDrainer = new EspressoReferenceDrainer(this);

        boolean softExit = env.getOptions().get(EspressoOptions.SoftExit);
        this.ExitHost = env.getOptions().get(EspressoOptions.ExitHost);
        this.shutdownManager = new EspressoShutdownHandler(this, threadRegistry, referenceDrainer, softExit);

        this.timers = TimerCollection.create(env.getOptions().get(EspressoOptions.EnableTimers));
        this.allocationReporter = env.lookup(AllocationReporter.class);

        // null if not specified
        this.JDWPOptions = env.getOptions().get(EspressoOptions.JDWPOptions);
        this.shouldReportVMEvents = JDWPOptions != null;
        this.eventListener = new VMEventListenerImpl();

        this.InlineFieldAccessors = JDWPOptions == null && env.getOptions().get(EspressoOptions.InlineFieldAccessors);
        this.InlineMethodHandle = JDWPOptions == null && env.getOptions().get(EspressoOptions.InlineMethodHandle);
        this.SplitMethodHandles = JDWPOptions == null && env.getOptions().get(EspressoOptions.SplitMethodHandles);
        this.Verify = env.getOptions().get(EspressoOptions.Verify);
        this.EnableSignals = env.getOptions().get(EspressoOptions.EnableSignals);
        this.SpecCompliancyMode = env.getOptions().get(EspressoOptions.SpecCompliancy);
        this.livenessAnalysis = env.getOptions().get(EspressoOptions.LivenessAnalysis);
        this.EnableClassHierarchyAnalysis = env.getOptions().get(EspressoOptions.CHA);
        this.EnableManagement = env.getOptions().get(EspressoOptions.EnableManagement);
        this.EnableAgents = getEnv().getOptions().get(EspressoOptions.EnableAgents);
        this.TrivialMethodSize = getEnv().getOptions().get(EspressoOptions.TrivialMethodSize);
        String multiThreadingDisabledReason = null;
        if (!env.getOptions().get(EspressoOptions.MultiThreaded)) {
            multiThreadingDisabledReason = "java.MultiThreaded option is set to false";
        }
        if (!env.isCreateThreadAllowed()) {
            multiThreadingDisabledReason = "polyglot context does not allow thread creation (`allowCreateThread(false)`)";
        }
        if (multiThreadingDisabledReason == null && !env.getOptions().hasBeenSet(EspressoOptions.MultiThreaded)) {
            Set<String> singleThreadedLanguages = knownSingleThreadedLanguages(env);
            if (!singleThreadedLanguages.isEmpty()) {
                multiThreadingDisabledReason = "context seems to contain single-threaded languages: " + singleThreadedLanguages;
                logger.warning(() -> "Disabling multi-threading since the context seems to contain single-threaded languages: " + singleThreadedLanguages);
            }
        }
        this.multiThreadingDisabled = multiThreadingDisabledReason;
        this.NativeAccessAllowed = env.isNativeAccessAllowed();
        this.Polyglot = env.getOptions().get(EspressoOptions.Polyglot);
        this.HotSwapAPI = env.getOptions().get(EspressoOptions.HotSwapAPI);

        this.vmArguments = buildVmArguments();
        this.jdwpContext = new JDWPContextImpl(this);
        if (this.EnableClassHierarchyAnalysis) {
            this.classHierarchyOracle = new DefaultClassHierarchyOracle();
        } else {
            this.classHierarchyOracle = new NoOpClassHierarchyOracle();
        }
    }

    private static Set<String> knownSingleThreadedLanguages(TruffleLanguage.Env env) {
        Set<String> singleThreaded = new HashSet<>();
        for (LanguageInfo languageInfo : env.getPublicLanguages().values()) {
            switch (languageInfo.getId()) {
                case "wasm":    // fallthrough
                case "js":      // fallthrough
                case "R":       // fallthrough
                case "python":  // it's configurable for python, be shy
                    singleThreaded.add(languageInfo.getId());
            }
        }
        return singleThreaded;
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

    public boolean multiThreadingEnabled() {
        return multiThreadingDisabled == null;
    }

    public String getMultiThreadingDisabledReason() {
        return multiThreadingDisabled;
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

    public String[] getVmArguments() {
        return vmArguments;
    }

    private String[] buildVmArguments() {
        OptionMap<String> argsMap = getEnv().getOptions().get(EspressoOptions.VMArguments);
        if (argsMap == null) {
            return new String[0];
        }
        Set<Map.Entry<String, String>> set = argsMap.entrySet();
        int length = set.size();
        String[] array = new String[length];
        for (Map.Entry<String, String> entry : set) {
            try {
                String key = entry.getKey();
                int idx = Integer.parseInt(key.substring(key.lastIndexOf('.') + 1));
                if (idx < 0 || idx >= length) {
                    getLogger().severe("Unsupported use of the 'java.VMArguments' option: " +
                                    "Declared index: " + idx + ", actual number of arguments: " + length + ".\n" +
                                    "Please only declare positive index starting from 0, and growing by 1 each.");
                    throw EspressoError.shouldNotReachHere();
                }
                array[idx] = entry.getValue();
            } catch (NumberFormatException e) {
                getLogger().warning("Unsupported use of the 'java.VMArguments' option: java.VMArguments." + entry.getKey() + "=" + entry.getValue() + "\n" +
                                "Should be of the form: java.VMArguments.<int>=<value>");
                throw EspressoError.shouldNotReachHere();
            }
        }
        return array;
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

        // Setup finalization support in the host VM.
        FinalizationSupport.ensureInitialized();

        spawnVM();
        this.initialized = true;
        // enable JDWP instrumenter only if options are set (assumed valid if non-null)
        if (JDWPOptions != null) {
            jdwpContext.jdwpInit(env, getMainThread(), eventListener);
        }
        referenceDrainer.startReferenceDrain();
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

    public NativeAccess getNativeAccess() {
        return nativeAccess;
    }

    @SuppressWarnings("try")
    private void spawnVM() {
        try (DebugCloseable spawn = SPAWN_VM.scope(timers)) {

            long initStartTimeNanos = System.nanoTime();

            this.nativeAccess = spawnNativeAccess();
            initVmProperties();

            // Spawn JNI first, then the VM.
            try (DebugCloseable vmInit = VM_INIT.scope(timers)) {
                this.vm = VM.create(getJNI()); // Mokapot is loaded
                vm.attachThread(Thread.currentThread());
            }

            if (getJavaVersion().modulesEnabled()) {
                registries.initJavaBaseModule();
                registries.getBootClassRegistry().initUnnamedModule(StaticObject.NULL);
            }

            // TODO: link libjimage

            initializeAgents();

            try (DebugCloseable metaInit = META_INIT.scope(timers)) {
                this.meta = new Meta(this);
            }
            this.metaInitialized = true;
            this.threads = new ThreadsAccess(meta);

            this.interpreterToVM = new InterpreterToVM(this);

            try (DebugCloseable knownClassInit = KNOWN_CLASS_INIT.scope(timers)) {
                initializeKnownClass(Type.java_lang_Object);

                for (Symbol<Type> type : Arrays.asList(
                                Type.java_lang_String,
                                Type.java_lang_System,
                                Type.java_lang_Class, // JDK-8069005
                                Type.java_lang_ThreadGroup,
                                Type.java_lang_Thread)) {
                    initializeKnownClass(type);
                }
            }

            if (meta.jdk_internal_misc_UnsafeConstants != null) {
                initializeKnownClass(Type.jdk_internal_misc_UnsafeConstants);
                UnsafeAccess.initializeGuestUnsafeConstants(meta);
            }

            // Create main thread as soon as Thread class is initialized.
            threadRegistry.createMainThread(meta);

            try (DebugCloseable knownClassInit = KNOWN_CLASS_INIT.scope(timers)) {
                initializeKnownClass(Type.java_lang_Object);

                for (Symbol<Type> type : Arrays.asList(
                                Type.java_lang_reflect_Method,
                                Type.java_lang_ref_Finalizer)) {
                    initializeKnownClass(type);
                }
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

                    getVM().getJvmti().postVmStart();

                    modulesInitialized = true;
                    meta.java_lang_System_initPhase3.invokeDirect(null);
                }
            }

            getVM().getJvmti().postVmInit();

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
            meta.HIDDEN_FRAMES.setHiddenObject(stackOverflowErrorInstance, VM.StackTrace.EMPTY_STACK_TRACE);
            meta.java_lang_Throwable_backtrace.setObject(stackOverflowErrorInstance, stackOverflowErrorInstance);
            meta.HIDDEN_FRAMES.setHiddenObject(outOfMemoryErrorInstance, VM.StackTrace.EMPTY_STACK_TRACE);
            meta.java_lang_Throwable_backtrace.setObject(outOfMemoryErrorInstance, outOfMemoryErrorInstance);

            this.stackOverflow = EspressoException.wrap(stackOverflowErrorInstance, meta);
            this.outOfMemory = EspressoException.wrap(outOfMemoryErrorInstance, meta);
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

    private NativeAccess spawnNativeAccess() {
        String nativeBackend;
        if (getEnv().getOptions().hasBeenSet(EspressoOptions.NativeBackend)) {
            nativeBackend = getEnv().getOptions().get(EspressoOptions.NativeBackend);
        } else {
            // Pick a sane "default" native backend depending on the platform.
            if (EspressoOptions.RUNNING_ON_SVM) {
                nativeBackend = NFINativeAccess.Provider.ID;
            } else {
                if (OS.getCurrent() == OS.Linux) {
                    nativeBackend = NFIIsolatedNativeAccess.Provider.ID;
                } else {
                    nativeBackend = NFISulongNativeAccess.Provider.ID;
                }
            }
        }

        List<String> available = new ArrayList<>();
        for (NativeAccess.Provider provider : NativeAccessCollector.getInstances(NativeAccess.Provider.class)) {
            available.add(provider.id());
            if (nativeBackend.equals(provider.id())) {
                getLogger().fine("Native backend: " + nativeBackend);
                return provider.create(getEnv());
            }
        }
        throw abort("Cannot find native backend '" + nativeBackend + "'. Available backends: " + available);
    }

    private void initializeAgents() {
        agents = new AgentLibraries(this);
        if (getEnv().getOptions().hasBeenSet(EspressoOptions.AgentLib)) {
            agents.registerAgents(getEnv().getOptions().get(EspressoOptions.AgentLib), false);
        }
        if (getEnv().getOptions().hasBeenSet(EspressoOptions.AgentPath)) {
            agents.registerAgents(getEnv().getOptions().get(EspressoOptions.AgentPath), true);
        }
        if (getEnv().getOptions().hasBeenSet(EspressoOptions.JavaAgent)) {
            agents.registerAgent("instrument", getEnv().getOptions().get(EspressoOptions.JavaAgent), false);
        }
        if (EnableAgents) {
            agents.initialize();
        } else {
            if (!agents.isEmpty()) {
                getLogger().warning("Agents support is currently disabled in Espresso. Ignoring passed agent options.");
            }
        }
    }

    private void initVmProperties() {
        final EspressoProperties.Builder builder = EspressoProperties.newPlatformBuilder();
        // If --java.JavaHome is not specified, Espresso tries to use the same (jars and native)
        // libraries bundled with GraalVM.
        builder.javaHome(Engine.findHome());
        EspressoProperties.processOptions(builder, getEnv().getOptions());
        getNativeAccess().updateEspressoProperties(builder, getEnv().getOptions());
        vmProperties = builder.build();
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
        return vm.getJavaVersion();
    }

    public boolean advancedRedefinitionEnabled() {
        return JDWPOptions != null;
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

    public boolean needsVerify(StaticObject classLoader) {
        switch (Verify) {
            case NONE:
                return false;
            case REMOTE:
                return !StaticObject.isNull(classLoader);
            case ALL:
                return true;
            default:
                return true;
        }
    }

    public void prepareDispose() {
        if (jdwpContext != null) {
            jdwpContext.finalizeContext();
        }
    }

    public void registerRedefinitionPlugin(InternalRedefinitionPlugin plugin) {
        // lazy initialization
        if (redefinitionPlugins == null) {
            redefinitionPlugins = Collections.synchronizedMap(new HashMap<>(2));
        }
        redefinitionPlugins.put(plugin.getClass(), plugin);
    }

    @SuppressWarnings("unchecked")
    @TruffleBoundary
    public <T> T lookup(Class<? extends InternalRedefinitionPlugin> pluginType) {
        if (redefinitionPlugins == null) {
            return null;
        }
        return (T) redefinitionPlugins.get(pluginType);
    }

    // region Agents

    public TruffleObject bindToAgent(Method method, String mangledName) {
        if (EnableAgents) {
            return agents.bind(method, mangledName);
        }
        return null;
    }

    // endregion Agents

    // region Thread management

    public ThreadsAccess getThreadAccess() {
        return threads;
    }

    /**
     * Creates a new guest thread from the host thread, and adds it to the main thread group.
     */
    public StaticObject createThread(Thread hostThread) {
        return threadRegistry.createGuestThreadFromHost(hostThread, meta, vm);
    }

    public StaticObject createThread(Thread hostThread, StaticObject group, String name) {
        return threadRegistry.createGuestThreadFromHost(hostThread, meta, vm, name, group);
    }

    public void disposeThread(@SuppressWarnings("unused") Thread hostThread) {
        StaticObject guestThread = getGuestThreadFromHost(hostThread);
        if (guestThread == null) {
            return;
        }
        if (hostThread != Thread.currentThread()) {
            String guestName = threads.getThreadName(guestThread);
            getLogger().warning("unimplemented: disposeThread for non-current thread: " + hostThread + " / " + guestName);
            return;
        }
        // Cannot run guest code after finalizeContext was called (GR-35712).
        if (isFinalized()) {
            return;
        }
        if (vm.DetachCurrentThread(this) != JNI_OK) {
            throw new RuntimeException("Could not detach thread correctly");
        }
    }

    public StaticObject getGuestThreadFromHost(Thread host) {
        return threadRegistry.getGuestThreadFromHost(host);
    }

    public StaticObject getCurrentThread() {
        return threadRegistry.getGuestThreadFromHost(Thread.currentThread());
    }

    /**
     * Returns the maximum number of alive (registered) threads at any point, since the VM started.
     */
    public long getPeakThreadCount() {
        return threadRegistry.peakThreadCount.get();
    }

    /**
     * Returns the number of created threads since the VM started.
     */
    public long getCreatedThreadCount() {
        return threadRegistry.createdThreadCount.get();
    }

    public StaticObject[] getActiveThreads() {
        return threadRegistry.activeThreads();
    }

    public void registerThread(Thread host, StaticObject self) {
        threadRegistry.registerThread(host, self);
        if (shouldReportVMEvents) {
            eventListener.threadStarted(self);
        }
    }

    public void unregisterThread(StaticObject self) {
        threadRegistry.unregisterThread(self);
        if (shouldReportVMEvents) {
            eventListener.threadDied(self);
        }
    }

    public void interruptThread(StaticObject guestThread) {
        threads.interruptThread(guestThread);
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
        return threadRegistry.isMainThreadCreated();
    }

    public StaticObject getMainThread() {
        return threadRegistry.getMainThread();
    }

    public StaticObject getMainThreadGroup() {
        return threadRegistry.getMainThreadGroup();
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

    public EspressoError abort(String message) {
        getLogger().severe(message);
        if (ExitHost) {
            System.exit(1);
            throw EspressoError.shouldNotReachHere();
        }
        throw new EspressoExitException(1);
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

    public WeakHashMap<StaticObject, SignalHandler> getHostSignalHandlers() {
        return hostSignalHandlers;
    }
    // endregion DebugAccess

    // region VM event reporting
    public boolean shouldReportVMEvents() {
        return shouldReportVMEvents;
    }

    public void reportMonitorWait(StaticObject monitor, long timeout) {
        assert shouldReportVMEvents;
        eventListener.monitorWait(monitor, timeout);
    }

    public void reportMonitorWaited(StaticObject monitor, boolean timedOut) {
        assert shouldReportVMEvents;
        eventListener.monitorWaited(monitor, timedOut);
    }

    public void reportClassPrepared(ObjectKlass objectKlass, Object prepareThread) {
        assert shouldReportVMEvents;
        eventListener.classPrepared(objectKlass, prepareThread);
    }

    public void reportOnContendedMonitorEnter(StaticObject obj) {
        assert shouldReportVMEvents;
        eventListener.onContendedMonitorEnter(obj);
    }

    public void reportOnContendedMonitorEntered(StaticObject obj) {
        assert shouldReportVMEvents;
        eventListener.onContendedMonitorEntered(obj);
    }

    public boolean reportOnMethodEntry(Method.MethodVersion method, Object scope) {
        assert shouldReportVMEvents;
        return eventListener.onMethodEntry(method, scope);
    }

    public boolean reportOnMethodReturn(Method.MethodVersion method, Object returnValue) {
        assert shouldReportVMEvents;
        return eventListener.onMethodReturn(method, returnValue);
    }

    public boolean reportOnFieldModification(Field field, StaticObject receiver, Object value) {
        assert shouldReportVMEvents;
        return eventListener.onFieldModification(field, receiver, value);
    }

    public boolean reportOnFieldAccess(Field field, StaticObject receiver) {
        assert shouldReportVMEvents;
        return eventListener.onFieldAccess(field, receiver);
    }
    // endregion VM event reporting

    public void registerExternalHotSwapHandler(StaticObject handler) {
        jdwpContext.registerExternalHotSwapHandler(handler);
    }

    public void rerunclinit(ObjectKlass oldKlass) {
        jdwpContext.rerunclinit(oldKlass);
    }

    private static final ContextReference<EspressoContext> REFERENCE = ContextReference.create(EspressoLanguage.class);

    /**
     * Returns the <em>current</em>, thread-local, context.
     */
    public static EspressoContext get(Node node) {
        return REFERENCE.get(node);
    }

    public synchronized ClassRedefinition createClassRedefinition(Ids<Object> ids, RedefinitionPluginHandler redefinitionPluginHandler) {
        if (classRedefinition == null) {
            classRedefinition = new ClassRedefinition(this, ids, redefinitionPluginHandler);
        }
        return classRedefinition;
    }

    public ClassRedefinition getClassRedefinition() {
        return classRedefinition;
    }

    public ClassHierarchyOracle getClassHierarchyOracle() {
        return classHierarchyOracle;
    }

    public boolean isFinalized() {
        return isFinalized;
    }

    public void setFinalized() {
        isFinalized = true;
    }
}
