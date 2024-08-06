/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.ReferenceQueue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

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
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.EspressoBindings;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.analysis.hierarchy.ClassHierarchyOracle;
import com.oracle.truffle.espresso.blocking.BlockingSupport;
import com.oracle.truffle.espresso.blocking.EspressoLock;
import com.oracle.truffle.espresso.descriptors.Names;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.ffi.NativeAccess;
import com.oracle.truffle.espresso.ffi.NativeAccessCollector;
import com.oracle.truffle.espresso.ffi.nfi.NFIIsolatedNativeAccess;
import com.oracle.truffle.espresso.ffi.nfi.NFINativeAccess;
import com.oracle.truffle.espresso.ffi.nfi.NFISulongNativeAccess;
import com.oracle.truffle.espresso.impl.ClassLoadingEnv;
import com.oracle.truffle.espresso.impl.ClassRegistries;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.jdwp.api.Ids;
import com.oracle.truffle.espresso.jdwp.impl.DebuggerController;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.interop.EspressoForeignProxyGenerator;
import com.oracle.truffle.espresso.nodes.interop.PolyglotTypeMappings;
import com.oracle.truffle.espresso.perf.DebugCloseable;
import com.oracle.truffle.espresso.perf.DebugTimer;
import com.oracle.truffle.espresso.perf.TimerCollection;
import com.oracle.truffle.espresso.preinit.ContextPatchingException;
import com.oracle.truffle.espresso.preinit.EspressoLanguageCache;
import com.oracle.truffle.espresso.redefinition.ClassRedefinition;
import com.oracle.truffle.espresso.redefinition.plugins.api.InternalRedefinitionPlugin;
import com.oracle.truffle.espresso.redefinition.plugins.impl.RedefinitionPluginHandler;
import com.oracle.truffle.espresso.ref.FinalizationSupport;
import com.oracle.truffle.espresso.runtime.jimage.BasicImageReader;
import com.oracle.truffle.espresso.runtime.panama.DowncallStubs;
import com.oracle.truffle.espresso.runtime.panama.Platform;
import com.oracle.truffle.espresso.runtime.panama.UpcallStubs;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.Substitutions;
import com.oracle.truffle.espresso.threads.ThreadsAccess;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.vm.UnsafeAccess;
import com.oracle.truffle.espresso.vm.VM;

import sun.misc.SignalHandler;

public final class EspressoContext {

    // MaxJavaStackTraceDepth is 1024 by default
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
    @CompilationFinal private EspressoEnv espressoEnv;

    private String[] mainArguments;
    private long startupClockNanos = 0;

    // region Runtime
    private final StringTable strings;
    @CompilationFinal private ClassRegistries registries;
    private final Substitutions substitutions;
    private final MethodHandleIntrinsics methodHandleIntrinsics;
    // endregion Runtime

    // region Helpers
    @CompilationFinal private ThreadsAccess threads;
    @CompilationFinal private BlockingSupport<StaticObject> blockingSupport;
    @CompilationFinal private EspressoShutdownHandler shutdownManager;
    // endregion Helpers

    // region ID
    @CompilationFinal private long bootClassLoaderID;
    // endregion ID

    // region InitControl
    public long initDoneTimeNanos;

    @CompilationFinal private boolean modulesInitialized = false;
    @CompilationFinal private boolean metaInitialized = false;
    private boolean initialized = false;
    private boolean disposeCalled = false;
    private Classpath bootClasspath;
    @CompilationFinal private ClassLoadingEnv classLoadingEnv;
    // endregion InitControl

    // region JDWP
    private ClassRedefinition classRedefinition;
    private final Assumption anyHierarchyChanges = Truffle.getRuntime().createAssumption();
    // endregion JDWP

    @CompilationFinal private volatile LazyContextCaches lazyCaches;

    private Map<Class<? extends InternalRedefinitionPlugin>, InternalRedefinitionPlugin> redefinitionPlugins;

    // After a context is finalized, guest code cannot be executed.
    private volatile boolean isFinalized;

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

    @CompilationFinal private EspressoBindings topBindings;
    @CompilationFinal private StaticObject bindingsLoader;
    private final WeakHashMap<StaticObject, SignalHandler> hostSignalHandlers = new WeakHashMap<>();
    @CompilationFinal private DowncallStubs downcallStubs;
    @CompilationFinal private UpcallStubs upcallStubs;

    public TruffleLogger getLogger() {
        return logger;
    }

    public long getBootClassLoaderID() {
        return bootClassLoaderID;
    }

    public EspressoContext(TruffleLanguage.Env env, EspressoLanguage language) {
        this.language = language;

        this.strings = new StringTable(this);
        this.substitutions = new Substitutions(this);
        this.methodHandleIntrinsics = new MethodHandleIntrinsics();

        this.espressoEnv = new EspressoEnv(this, env);
        this.classLoadingEnv = new ClassLoadingEnv(getLanguage(), getLogger(), getTimers());
        this.bootClassLoaderID = classLoadingEnv.getNewLoaderId();
    }

    public ClassRegistries getRegistries() {
        return registries;
    }

    public InputStream in() {
        return getEnv().in();
    }

    public OutputStream out() {
        return getEnv().out();
    }

    public OutputStream err() {
        return getEnv().err();
    }

    public StringTable getStrings() {
        return strings;
    }

    public TruffleLanguage.Env getEnv() {
        return espressoEnv.env();
    }

    public EspressoEnv getEspressoEnv() {
        return espressoEnv;
    }

    public EspressoLanguage getLanguage() {
        return language;
    }

    public boolean multiThreadingEnabled() {
        return espressoEnv.multiThreadingEnabled();
    }

    public String getMultiThreadingDisabledReason() {
        return espressoEnv.getMultiThreadingDisabledReason();
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
        return espressoEnv.getVmArguments();
    }

    public long getStartupClockNanos() {
        return startupClockNanos;
    }

    public EspressoLanguageCache getLanguageCache() {
        return getLanguage().getLanguageCache();
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

    public ClassLoadingEnv getClassLoadingEnv() {
        return classLoadingEnv;
    }

    public void setBootClassPath(Classpath classPath) {
        this.bootClasspath = classPath;
    }

    public EspressoProperties getVmProperties() {
        assert vmProperties != null;
        return vmProperties;
    }

    public void initializeContext() throws ContextPatchingException {
        EspressoError.guarantee(getEnv().isNativeAccessAllowed(),
                        "Native access is not allowed by the host environment but it's required to load Espresso/Java native libraries. " +
                                        "Allow native access on context creation e.g. contextBuilder.allowNativeAccess(true). If you are attempting to pre-initialize " +
                                        "an Espresso context, allow native access for pre-initialized languages through Truffle's image-build-time options.");
        assert !this.initialized;
        startupClockNanos = System.nanoTime();

        // Setup finalization support in the host VM.
        FinalizationSupport.ensureInitialized();

        spawnVM();

        getEspressoEnv().getPolyglotTypeMappings().resolve(this);

        this.initialized = true;

        getEspressoEnv().getReferenceDrainer().startReferenceDrain();

        // enable JDWP instrumenter only if options are set (assumed valid if non-null)
        if (espressoEnv.JDWPOptions != null) {
            espressoEnv.getJdwpContext().jdwpInit(getEnv(), getMainThread(), espressoEnv.getEventListener());
        }
    }

    public void patchContext(TruffleLanguage.Env newEnv) {
        this.espressoEnv = new EspressoEnv(this, newEnv);
    }

    @TruffleBoundary
    public Source findOrCreateSource(ObjectKlass klass) {
        String sourceFile = klass.getSourceFile();
        if (sourceFile == null) {
            return null;
        }
        if (!sourceFile.contains("/") && !sourceFile.contains("\\")) {
            // try to come up with a more unique name
            Symbol<Name> runtimePackage = klass.getRuntimePackage();
            if (runtimePackage != null && runtimePackage.length() > 0) {
                sourceFile = runtimePackage + "/" + sourceFile;
            }
        }
        TruffleFile file = getEnv().getInternalTruffleFile(sourceFile);
        // sources are interned so no cache needed (hopefully)
        return Source.newBuilder("java", file).content(Source.CONTENT_NONE).build();
    }

    public Meta getMeta() {
        return meta;
    }

    public GuestAllocator getAllocator() {
        return getLanguage().getAllocator();
    }

    public NativeAccess getNativeAccess() {
        return nativeAccess;
    }

    @SuppressWarnings("try")
    private void spawnVM() throws ContextPatchingException {
        try (DebugCloseable spawn = SPAWN_VM.scope(espressoEnv.getTimers())) {

            long initStartTimeNanos = System.nanoTime();

            this.nativeAccess = spawnNativeAccess();
            initVmProperties();

            // Find guest java version
            JavaVersion contextJavaVersion = javaVersionFromReleaseFile(vmProperties.javaHome());
            if (contextJavaVersion == null) {
                contextJavaVersion = JavaVersion.latestSupported();
                getLogger().warning(() -> "Couldn't find Java version for %s / %s: defaulting to %s".formatted(
                                vmProperties.javaHome(), vmProperties.bootLibraryPath(), JavaVersion.latestSupported()));
            } else if (contextJavaVersion.compareTo(JavaVersion.latestSupported()) > 0) {
                throw EspressoError.fatal("Unsupported Java version: " + contextJavaVersion);
            }

            // Ensure that the extracted Java version equals the language's Java version, if it
            // is set
            JavaVersion languageJavaVersion = getLanguage().getJavaVersion();
            if (languageJavaVersion != null) {
                if (!contextJavaVersion.equals(languageJavaVersion)) {
                    throw ContextPatchingException.javaVersionMismatch(languageJavaVersion, contextJavaVersion);
                }
            } else {
                getLanguage().tryInitializeJavaVersion(contextJavaVersion);
            }

            if (!contextJavaVersion.java21OrLater() && getEspressoEnv().RegexSubstitutions) {
                logger.warning("UseTRegex is not available for context running Java version < 21");
            }

            // Spawn JNI first, then the VM.
            try (DebugCloseable vmInit = VM_INIT.scope(espressoEnv.getTimers())) {
                this.jniEnv = JniEnv.create(this); // libnespresso
                this.vm = VM.create(this.jniEnv); // libjvm
                vm.attachThread(Thread.currentThread());
                vm.loadJavaLibrary(vmProperties.bootLibraryPath()); // libjava
                this.downcallStubs = new DowncallStubs(Platform.getHostPlatform());
                this.upcallStubs = new UpcallStubs(Platform.getHostPlatform(), nativeAccess, language);

                vm.initializeJavaLibrary();
                EspressoError.guarantee(getJavaVersion() != null, "Java version");
            }

            this.registries = new ClassRegistries(this);

            if (getJavaVersion().modulesEnabled()) {
                registries.initJavaBaseModule();
                registries.getBootClassRegistry().initUnnamedModule(StaticObject.NULL);
            }

            initializeAgents();

            try (DebugCloseable metaInit = META_INIT.scope(espressoEnv.getTimers())) {
                this.meta = new Meta(this);
            }
            this.classLoadingEnv.setMeta(meta);
            this.metaInitialized = true;
            this.threads = new ThreadsAccess(meta);
            this.blockingSupport = BlockingSupport.create(threads);
            this.shutdownManager = new EspressoShutdownHandler(this, espressoEnv.getThreadRegistry(), espressoEnv.getReferenceDrainer(), espressoEnv.SoftExit);

            this.interpreterToVM = new InterpreterToVM(this);
            this.lazyCaches = new LazyContextCaches(this);

            try (DebugCloseable knownClassInit = KNOWN_CLASS_INIT.scope(espressoEnv.getTimers())) {
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
            espressoEnv.getThreadRegistry().createMainThread(meta);

            try (DebugCloseable knownClassInit = KNOWN_CLASS_INIT.scope(espressoEnv.getTimers())) {
                for (Symbol<Type> type : Arrays.asList(
                                Type.java_lang_reflect_Method,
                                Type.java_lang_ref_Finalizer)) {
                    initializeKnownClass(type);
                }
            }

            espressoEnv.getReferenceDrainer().initReferenceDrain();

            try (DebugCloseable systemInit = SYSTEM_INIT.scope(espressoEnv.getTimers())) {
                // Call guest initialization
                if (getJavaVersion().java8OrEarlier()) {
                    meta.java_lang_System_initializeSystemClass.invokeDirect(null);
                } else {
                    assert getJavaVersion().java9OrLater();
                    meta.java_lang_System_initPhase1.invokeDirect(null);
                    for (Symbol<Type> type : Arrays.asList(
                                    Type.java_lang_invoke_MethodHandle,
                                    Type.java_lang_invoke_MemberName,
                                    Type.java_lang_invoke_MethodHandleNatives)) {
                        // Type.java_lang_invoke_ResolvedMethodName is not used atm
                        initializeKnownClass(type);
                    }
                    int e = (int) meta.java_lang_System_initPhase2.invokeDirect(null, false, logger.isLoggable(Level.FINE));
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

            try (DebugCloseable knownClassInit = KNOWN_CLASS_INIT.scope(espressoEnv.getTimers())) {
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
            StaticObject stackOverflowErrorInstance = meta.java_lang_StackOverflowError.allocateInstance(this);
            StaticObject outOfMemoryErrorInstance = meta.java_lang_OutOfMemoryError.allocateInstance(this);

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
            try (DebugCloseable systemLoader = SYSTEM_CLASSLOADER.scope(espressoEnv.getTimers())) {
                systemClassLoader = (StaticObject) meta.java_lang_ClassLoader_getSystemClassLoader.invokeDirect(null);
            }
            bindingsLoader = createBindingsLoader(systemClassLoader);
            topBindings = new EspressoBindings(
                            getEnv().getOptions().get(EspressoOptions.ExposeNativeJavaVM),
                            bindingsLoader != systemClassLoader);

            initDoneTimeNanos = System.nanoTime();
            long elapsedNanos = initDoneTimeNanos - initStartTimeNanos;
            getLogger().log(Level.FINE, "VM booted in {0} ms", TimeUnit.NANOSECONDS.toMillis(elapsedNanos));
        }
    }

    private JavaVersion javaVersionFromReleaseFile(Path javaHome) {
        Path releaseFilePath = javaHome.resolve("release");
        if (!Files.isRegularFile(releaseFilePath)) {
            Path maybeJre = javaHome.getFileName();
            if (maybeJre == null || !"jre".equals(maybeJre.toString())) {
                return null;
            }
            Path parent = javaHome.getParent();
            if (parent == null) {
                return null;
            }
            // pre-jdk9 layout
            releaseFilePath = parent.resolve("release");
            if (!Files.isRegularFile(releaseFilePath)) {
                return null;
            }
        }
        try {
            for (String line : Files.readAllLines(releaseFilePath)) {
                if (line.startsWith("JAVA_VERSION=")) {
                    String version = line.substring("JAVA_VERSION=".length()).trim();
                    // JAVA_VERSION=<value> may be quoted or unquoted, both cases are supported.
                    if (version.length() > 2 && version.startsWith("\"") && version.endsWith("\"")) {
                        version = version.substring(1, version.length() - 1);
                    }
                    return JavaVersion.forVersion(version);
                }
            }
        } catch (IOException | NumberFormatException e) {
            getLogger().log(Level.WARNING, "Error while trying to read Java version from release file", e);
            // cannot read file, skip
        }
        return null; // JAVA_VERSION not found
    }

    public void preInitializeContext() {
        assert isInitialized();

        long initStartTimeNanos = System.nanoTime();

        getLogger().fine("Loading classes from lib/classlist");
        Path classlistPath = getVmProperties().javaHome().resolve("lib").resolve("classlist");
        List<Symbol<Type>> classlist = readClasslist(classlistPath);
        for (Symbol<Type> type : classlist) {
            getMeta().loadKlassOrFail(type, StaticObject.NULL, StaticObject.NULL);
        }

        long elapsedNanos = System.nanoTime() - initStartTimeNanos;
        getLogger().log(Level.FINE, "Loaded lib/classlist in {0} ms", TimeUnit.NANOSECONDS.toMillis(elapsedNanos));

        Path userClasslistPath = getEnv().getOptions().get(EspressoOptions.PreInitializationClasslist);
        if (!userClasslistPath.toString().isEmpty()) {
            getLanguageCache().logCacheStatus();
            getLogger().fine(() -> "Loading classes from user-specified classlist: " + userClasslistPath);
            initStartTimeNanos = System.nanoTime();

            List<Symbol<Type>> additionalClasslist = readClasslist(userClasslistPath);

            StaticObject systemClassLoader = (StaticObject) meta.java_lang_ClassLoader_getSystemClassLoader.invokeDirect(null);
            for (Symbol<Type> type : additionalClasslist) {
                Klass klass = getMeta().loadKlassOrNull(type, systemClassLoader, StaticObject.NULL);
                if (Objects.isNull(klass)) {
                    getLogger().warning(() -> "Failed to load class from user-specified classlist: " + type);
                }
            }

            elapsedNanos = System.nanoTime() - initStartTimeNanos;
            getLogger().log(Level.FINE, "Loaded user-specified classlist in {0} ms", TimeUnit.NANOSECONDS.toMillis(elapsedNanos));
        }
    }

    private List<Symbol<Type>> readClasslist(Path classlistFilePath) {
        try {
            List<Symbol<Type>> classlist = Files.readAllLines(classlistFilePath) //
                            .stream() //
                            .filter(line -> !line.isBlank() && !line.startsWith("#") && !line.startsWith("@")) //
                            .map(Types::internalFromClassName) //
                            .map(t -> getTypes().getOrCreate(t)) //
                            .filter(Objects::nonNull) //
                            .collect(Collectors.toList());
            return classlist;
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Failed to read classlist", e);
            return List.of();
        }
    }

    private StaticObject createBindingsLoader(StaticObject systemClassLoader) {
        if (!getEspressoEnv().UseBindingsLoader) {
            return systemClassLoader;
        }
        Klass k = getMeta().loadKlassOrNull(Type.java_net_URLClassLoader, StaticObject.NULL, StaticObject.NULL);
        if (k == null) {
            return systemClassLoader;
        }
        Method init = k.lookupDeclaredMethod(Name._init_, Signature._void_URL_array_ClassLoader);
        if (init == null) {
            return systemClassLoader;
        }
        StaticObject loader = k.allocateInstance();
        init.invokeDirect(loader,
                        /* URLs */ getMeta().java_net_URL.allocateReferenceArray(0),
                        /* parent */ systemClassLoader);
        return loader;
    }

    private NativeAccess spawnNativeAccess() {
        String nativeBackend;
        if (getEnv().getOptions().hasBeenSet(EspressoOptions.NativeBackend)) {
            nativeBackend = getEnv().getOptions().get(EspressoOptions.NativeBackend);
        } else {
            // Pick a sane "default" native backend depending on the platform.
            boolean isInPreInit = (boolean) getEnv().getConfig().getOrDefault("preinit", false);
            if (isInPreInit || !EspressoOptions.RUNNING_ON_SVM) {
                if (OS.getCurrent() == OS.Linux) {
                    nativeBackend = NFIIsolatedNativeAccess.Provider.ID;
                } else {
                    nativeBackend = NFISulongNativeAccess.Provider.ID;
                }
            } else {
                nativeBackend = NFINativeAccess.Provider.ID;
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
        if (espressoEnv.EnableAgents) {
            agents.initialize();
        } else {
            if (!agents.isEmpty()) {
                getLogger().warning("Agents support is currently disabled in Espresso. Ignoring passed agent options.");
            }
        }
    }

    private void initVmProperties() {
        EspressoProperties.Builder builder = EspressoProperties.newPlatformBuilder(getEspressoLibs());
        builder.javaHome(getEspressoRuntime());
        EspressoProperties.processOptions(builder, getEnv().getOptions(), this);
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

    private JImageLibrary jimageLibrary() {
        if (jimageLibrary == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            EspressoError.guarantee(getJavaVersion().modulesEnabled(), "Jimage available for java >= 9");
            this.jimageLibrary = new JImageLibrary(this);
        }
        return jimageLibrary;
    }

    public JImageHelper createJImageHelper(String jimagePath) {
        if (espressoEnv.JImageMode == EspressoOptions.JImageMode.NATIVE) {
            JImageLibrary library = jimageLibrary();
            TruffleObject image = library.open(jimagePath);
            if (InteropLibrary.getUncached().isNull(image)) {
                return null;
            }
            return new NativeJImageHelper(library, image);
        } else {
            assert espressoEnv.JImageMode == EspressoOptions.JImageMode.JAVA;
            try {
                return new JavaJImageHelper(BasicImageReader.open(Paths.get(jimagePath)), this);
            } catch (BasicImageReader.NotAnImageFile e) {
                return null;
            } catch (IOException e) {
                logger.log(Level.SEVERE, "failed to open jimage", e);
                return null;
            }
        }
    }

    public JavaVersion getJavaVersion() {
        return getLanguage().getJavaVersion();
    }

    public boolean advancedRedefinitionEnabled() {
        return espressoEnv.JDWPOptions != null;
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
        synchronized (this) {
            if (disposeCalled) {
                getLogger().warning("Context is being disposed multiple times");
                return;
            }
            disposeCalled = true;
        }
    }

    public void cleanupNativeEnv() {
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

    public LazyContextCaches getLazyCaches() {
        LazyContextCaches cache = this.lazyCaches;
        if (cache == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.fatal("Accessing lazy context cache before context initialization");
        }
        return cache;
    }

    public void prepareDispose() {
        if (espressoEnv.getJdwpContext() != null) {
            espressoEnv.getJdwpContext().finalizeContext();
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
        if (espressoEnv.EnableAgents) {
            return agents.bind(method, mangledName);
        }
        return null;
    }

    // endregion Agents

    // region Thread management

    public ThreadsAccess getThreadAccess() {
        return threads;
    }

    public BlockingSupport<StaticObject> getBlockingSupport() {
        return blockingSupport;
    }

    /**
     * Creates a new guest thread from the host thread, and adds it to the main thread group. This
     * thread is not in Espresso's control.
     */
    public StaticObject createThread(Thread hostThread) {
        return createThread(hostThread, getMainThreadGroup(), null, false);
    }

    public StaticObject createThread(Thread hostThread, StaticObject group, String name) {
        return createThread(hostThread, group, name, true);
    }

    public StaticObject createThread(Thread hostThread, StaticObject group, String name, boolean managedByEspresso) {
        return espressoEnv.getThreadRegistry().createGuestThreadFromHost(hostThread, meta, vm, name, group, managedByEspresso);
    }

    public void disposeThread(Thread hostThread) {
        StaticObject guestThread = getGuestThreadFromHost(hostThread);
        if (guestThread == null) {
            return;
        }
        try {
            // Cannot run guest code after finalizeContext was called (GR-35712).
            if (isFinalized()) {
                return;
            }
            if (hostThread != Thread.currentThread()) {
                String guestName = threads.getThreadName(guestThread);
                getLogger().warning("unimplemented: disposeThread for non-current thread: " + hostThread + " / " + guestName + ". Called from thread: " + Thread.currentThread());
                return;
            }
            if (vm.DetachCurrentThread(this) != JNI_OK) {
                throw new RuntimeException("Could not detach thread correctly");
            }
        } finally {
            unregisterThread(guestThread);
        }
    }

    public StaticObject getGuestThreadFromHost(Thread host) {
        return espressoEnv.getThreadRegistry().getGuestThreadFromHost(host);
    }

    public void registerCurrentThread(StaticObject guestThread) {
        getLanguage().getThreadLocalState().initializeCurrentThread(guestThread);
    }

    public StaticObject getCurrentPlatformThread() {
        return getLanguage().getThreadLocalState().getCurrentPlatformThread(this);
    }

    /**
     * Returns the maximum number of alive (registered) threads at any point, since the VM started.
     */
    public long getPeakThreadCount() {
        return espressoEnv.getThreadRegistry().peakThreadCount.get();
    }

    public void resetPeakThreadCount() {
        espressoEnv.getThreadRegistry().resetPeakThreadCount();
    }

    /**
     * Returns the number of created threads since the VM started.
     */
    public long getCreatedThreadCount() {
        return espressoEnv.getThreadRegistry().createdThreadCount.get();
    }

    public StaticObject[] getActiveThreads() {
        return espressoEnv.getThreadRegistry().activeThreads();
    }

    public void registerThread(Thread host, StaticObject self) {
        espressoEnv.getThreadRegistry().registerThread(host, self);
        if (shouldReportVMEvents()) {
            espressoEnv.getEventListener().threadStarted(self);
        }
    }

    public void unregisterThread(StaticObject self) {
        boolean unregistered = getEspressoEnv().getThreadRegistry().unregisterThread(self);
        if (shouldReportVMEvents() && unregistered) {
            getEspressoEnv().getEventListener().threadDied(self);
        }
    }

    public void interruptThread(StaticObject guestThread) {
        threads.callInterrupt(guestThread);
    }

    public boolean isMainThreadCreated() {
        return espressoEnv.getThreadRegistry().isMainThreadCreated();
    }

    public StaticObject getMainThread() {
        return espressoEnv.getThreadRegistry().getMainThread();
    }

    public StaticObject getMainThreadGroup() {
        return espressoEnv.getThreadRegistry().getMainThreadGroup();
    }

    // endregion Thread management

    // region Shutdown

    public void notifyShutdownSynchronizer() {
        EspressoLock lock = shutdownManager.getShutdownSynchronizer();
        lock.lock();
        try {
            lock.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void truffleExit(Node location, int exitCode) {
        getEnv().getContext().closeExited(location, exitCode);
    }

    public void doExit(int code) {
        shutdownManager.doExit(code);
    }

    public void destroyVM() {
        shutdownManager.destroyVM();
    }

    public void ensureThreadsJoined() {
        // shutdownManager could be null if we are closing a pre-initialized context
        if (shutdownManager != null) {
            shutdownManager.ensureThreadsJoined();
        }
    }

    public boolean isClosing() {
        return shutdownManager.isClosing();
    }

    public boolean isTruffleClosed() {
        return getEnv().getContext().isClosed();
    }

    public int getExitStatus() {
        return shutdownManager.getExitStatus();
    }

    public EspressoError abort(String message) {
        getLogger().severe(message);
        throw new EspressoExitException(1);
    }

    // endregion Shutdown

    // region ReferenceDrain

    public ReferenceQueue<StaticObject> getReferenceQueue() {
        return espressoEnv.getReferenceDrainer().getReferenceQueue();
    }

    public StaticObject getAndClearReferencePendingList() {
        return espressoEnv.getReferenceDrainer().getAndClearReferencePendingList();
    }

    public boolean hasReferencePendingList() {
        return espressoEnv.getReferenceDrainer().hasReferencePendingList();
    }

    public void waitForReferencePendingList() {
        espressoEnv.getReferenceDrainer().waitForReferencePendingList();
    }

    public void triggerDrain() {
        espressoEnv.getReferenceDrainer().triggerDrain();
    }

    // endregion ReferenceDrain

    // region DebugAccess

    public TimerCollection getTimers() {
        return espressoEnv.getTimers();
    }

    public EspressoBindings getBindings() {
        return topBindings;
    }

    public StaticObject getBindingsLoader() {
        return bindingsLoader;
    }

    public WeakHashMap<StaticObject, SignalHandler> getHostSignalHandlers() {
        return hostSignalHandlers;
    }
    // endregion DebugAccess

    // region VM event reporting
    public boolean shouldReportVMEvents() {
        return espressoEnv.shouldReportVMEvents();
    }

    public void reportMonitorWait(StaticObject monitor, long timeout) {
        assert shouldReportVMEvents();
        espressoEnv.getEventListener().monitorWait(monitor, timeout);
    }

    public void reportMonitorWaited(StaticObject monitor, boolean timedOut) {
        assert shouldReportVMEvents();
        espressoEnv.getEventListener().monitorWaited(monitor, timedOut);
    }

    public void reportClassPrepared(ObjectKlass objectKlass, Object prepareThread) {
        assert shouldReportVMEvents();
        espressoEnv.getEventListener().classPrepared(objectKlass, prepareThread);
    }

    public void reportOnContendedMonitorEnter(StaticObject obj) {
        assert shouldReportVMEvents();
        espressoEnv.getEventListener().onContendedMonitorEnter(obj);
    }

    public void reportOnContendedMonitorEntered(StaticObject obj) {
        assert shouldReportVMEvents();
        espressoEnv.getEventListener().onContendedMonitorEntered(obj);
    }

    public boolean reportOnMethodEntry(Method.MethodVersion method, Object scope) {
        assert shouldReportVMEvents();
        return espressoEnv.getEventListener().onMethodEntry(method, scope);
    }

    public boolean reportOnMethodReturn(Method.MethodVersion method, Object returnValue) {
        assert shouldReportVMEvents();
        return espressoEnv.getEventListener().onMethodReturn(method, returnValue);
    }

    public boolean reportOnFieldModification(Field field, StaticObject receiver, Object value) {
        assert shouldReportVMEvents();
        return espressoEnv.getEventListener().onFieldModification(field, receiver, value);
    }

    public boolean reportOnFieldAccess(Field field, StaticObject receiver) {
        assert shouldReportVMEvents();
        return espressoEnv.getEventListener().onFieldAccess(field, receiver);
    }
    // endregion VM event reporting

    public void registerExternalHotSwapHandler(StaticObject handler) {
        espressoEnv.getJdwpContext().registerExternalHotSwapHandler(handler);
    }

    public void rerunclinit(ObjectKlass oldKlass) {
        espressoEnv.getJdwpContext().rerunclinit(oldKlass);
    }

    private static final ContextReference<EspressoContext> REFERENCE = ContextReference.create(EspressoLanguage.class);

    /**
     * Returns the <em>current</em>, thread-local, context.
     */
    public static EspressoContext get(Node node) {
        return REFERENCE.get(node);
    }

    public synchronized ClassRedefinition createClassRedefinition(Ids<Object> ids, RedefinitionPluginHandler redefinitionPluginHandler, DebuggerController controller) {
        if (classRedefinition == null) {
            classRedefinition = new ClassRedefinition(this, ids, redefinitionPluginHandler, controller);
        }
        return classRedefinition;
    }

    public ClassRedefinition getClassRedefinition() {
        return classRedefinition;
    }

    public boolean anyHierarchyChanged() {
        return !anyHierarchyChanges.isValid();
    }

    public void markChangedHierarchy() {
        anyHierarchyChanges.invalidate();
    }

    public ClassHierarchyOracle getClassHierarchyOracle() {
        return espressoEnv.getClassHierarchyOracle();
    }

    public boolean isFinalized() {
        return isFinalized;
    }

    public void setFinalized() {
        isFinalized = true;
    }

    public boolean explicitTypeMappingsEnabled() {
        return getEspressoEnv().getPolyglotTypeMappings().hasMappings();
    }

    public boolean interfaceMappingsEnabled() {
        return getEspressoEnv().getPolyglotTypeMappings().hasInterfaceMappings();
    }

    @Idempotent
    public boolean regexSubstitutionsEnabled() {
        return getEspressoEnv().RegexSubstitutions && getJavaVersion().java21OrLater();
    }

    public PolyglotTypeMappings getPolyglotTypeMappings() {
        return getEspressoEnv().getPolyglotTypeMappings();
    }

    public EspressoForeignProxyGenerator.GeneratedProxyBytes getProxyBytesOrNull(String metaName) {
        if (getEspressoEnv().getProxyCache() != null) {
            return getEspressoEnv().getProxyCache().get(metaName);
        } else {
            return null;
        }
    }

    public void registerProxyBytes(String metaName, EspressoForeignProxyGenerator.GeneratedProxyBytes generatedProxyBytes) {
        if (getEspressoEnv().getProxyCache() != null) {
            getEspressoEnv().getProxyCache().put(metaName, generatedProxyBytes);
        } else {
            throw EspressoError.shouldNotReachHere();
        }
    }

    public long nextThreadId() {
        return espressoEnv.getThreadRegistry().nextThreadId();
    }

    public DowncallStubs getDowncallStubs() {
        return downcallStubs;
    }

    public UpcallStubs getUpcallStubs() {
        return upcallStubs;
    }

    public Path getEspressoLibs() {
        return EspressoLanguage.getEspressoLibs(getEnv());
    }

    public Path getEspressoRuntime() {
        return EspressoLanguage.getEspressoRuntime(getEnv());
    }
}
