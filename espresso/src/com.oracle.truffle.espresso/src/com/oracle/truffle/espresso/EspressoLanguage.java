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
package com.oracle.truffle.espresso;

import static com.oracle.truffle.espresso.jni.JniEnv.JNI_OK;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.graalvm.home.HomeFinder;
import org.graalvm.home.Version;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.staticobject.DefaultStaticProperty;
import com.oracle.truffle.api.staticobject.StaticProperty;
import com.oracle.truffle.api.staticobject.StaticShape;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.JavaVersion;
import com.oracle.truffle.espresso.classfile.descriptors.NameSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.SignatureSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Symbols;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Utf8Symbols;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols;
import com.oracle.truffle.espresso.ffi.nfi.NFIIsolatedNativeAccess;
import com.oracle.truffle.espresso.ffi.nfi.NFINativeAccess;
import com.oracle.truffle.espresso.ffi.nfi.NFISulongNativeAccess;
import com.oracle.truffle.espresso.impl.EspressoType;
import com.oracle.truffle.espresso.impl.SuppressFBWarnings;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.commands.ExitCodeNode;
import com.oracle.truffle.espresso.nodes.commands.GetBindingsNode;
import com.oracle.truffle.espresso.nodes.commands.ReferenceProcessRootNode;
import com.oracle.truffle.espresso.preinit.ContextPatchingException;
import com.oracle.truffle.espresso.preinit.EspressoLanguageCache;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoThreadLocalState;
import com.oracle.truffle.espresso.runtime.GuestAllocator;
import com.oracle.truffle.espresso.runtime.OS;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject.StaticObjectFactory;
import com.oracle.truffle.espresso.shared.meta.SymbolPool;
import com.oracle.truffle.espresso.substitutions.JImageExtensions;
import com.oracle.truffle.espresso.substitutions.Substitutions;
import com.oracle.truffle.espresso.substitutions.standard.Target_sun_misc_Unsafe.CompactGuestFieldOffsetStrategy;
import com.oracle.truffle.espresso.substitutions.standard.Target_sun_misc_Unsafe.GraalGuestFieldOffsetStrategy;
import com.oracle.truffle.espresso.substitutions.standard.Target_sun_misc_Unsafe.GuestFieldOffsetStrategy;
import com.oracle.truffle.espresso.substitutions.standard.Target_sun_misc_Unsafe.SafetyGuestFieldOffsetStrategy;

// TODO: Update website once Espresso has one
@Registration(id = EspressoLanguage.ID, //
                name = EspressoLanguage.NAME, //
                implementationName = EspressoLanguage.IMPLEMENTATION_NAME, //
                contextPolicy = TruffleLanguage.ContextPolicy.SHARED, //
                dependentLanguages = {"nfi"}, //
                website = "https://www.graalvm.org/dev/reference-manual/java-on-truffle/")
@ProvidedTags({StandardTags.RootTag.class, StandardTags.RootBodyTag.class, StandardTags.StatementTag.class})
public final class EspressoLanguage extends TruffleLanguage<EspressoContext> implements SymbolPool {
    public static final String ID = "java";
    public static final String NAME = "Java";
    public static final String IMPLEMENTATION_NAME = "Espresso";

    // Espresso VM info
    public static final String VM_SPECIFICATION_NAME = "Java Virtual Machine Specification";
    public static final String VM_SPECIFICATION_VENDOR = "Oracle Corporation";
    public static final String VM_VERSION = /* 1.8|11 */ "espresso-" + Version.getCurrent();
    public static final String VM_VENDOR = "Oracle Corporation";
    public static final String VM_NAME = "Espresso 64-Bit VM";
    public static final String VM_INFO = "mixed mode";
    public static final String FILE_EXTENSION = ".class";

    @CompilationFinal private Utf8Symbols utf8Symbols;
    @CompilationFinal private NameSymbols nameSymbols;
    @CompilationFinal private TypeSymbols typeSymbols;
    @CompilationFinal private SignatureSymbols signatureSymbols;

    private final StaticProperty arrayProperty = new DefaultStaticProperty("array");
    private final StaticProperty arrayHashCodeProperty = new DefaultStaticProperty("ihashcode");
    // This field should be final, but creating a shape requires a fully-initialized instance of
    // TruffleLanguage.
    @CompilationFinal //
    private StaticShape<StaticObjectFactory> arrayShape;

    private final StaticProperty foreignProperty = new DefaultStaticProperty("foreignObject");
    private final StaticProperty typeArgumentProperty = new DefaultStaticProperty("typeArguments");
    // This field should be final, but creating a shape requires a fully-initialized instance of
    // TruffleLanguage.
    @CompilationFinal //
    private StaticShape<StaticObjectFactory> foreignShape;

    @CompilationFinal private JavaVersion javaVersion;

    // region Options
    // Note: All options are initialized during the bootstrapping of the first context
    @CompilationFinal private EspressoOptions.VerifyMode verifyMode;
    @CompilationFinal private EspressoOptions.SpecComplianceMode specComplianceMode;
    @CompilationFinal private EspressoOptions.LivenessAnalysisMode livenessAnalysisMode;
    @CompilationFinal private int livenessAnalysisMinimumLocals;
    @CompilationFinal private boolean previewEnabled;
    @CompilationFinal private boolean whiteBoxEnabled;
    @CompilationFinal private boolean eagerFrameAnalysis;
    @CompilationFinal private boolean internalJvmciEnabled;
    @CompilationFinal private boolean useEspressoLibs;
    @CompilationFinal private boolean continuum;
    @CompilationFinal private String nativeBackendId;
    @CompilationFinal private boolean useTRegex;
    @CompilationFinal private int maxStackTraceDepth;
    // endregion Options

    // region Allocation
    // Note: Initialized during the bootstrapping of the first context; See initializeOptions()
    @CompilationFinal private GuestAllocator allocator;
    @CompilationFinal private final Assumption noAllocationTracking = Assumption.create("Espresso no allocation tracking assumption");
    // endregion Allocation

    // region Preinit and sharing
    private final EspressoLanguageCache languageCache = new EspressoLanguageCache();
    @CompilationFinal private boolean isShared = false;
    // endregion Preinit and sharing

    @CompilationFinal private volatile boolean fullyInitialized;

    @CompilationFinal private JImageExtensions jImageExtensions;

    @CompilationFinal private GuestFieldOffsetStrategy guestFieldOffsetStrategy;

    private final ContextThreadLocal<EspressoThreadLocalState> threadLocalState = locals.createContextThreadLocal(EspressoThreadLocalState::new);

    public EspressoLanguage() {
        // Initialize statically defined symbols and substitutions.
        // Initialization order is very fragile.
        ParserSymbols.ensureInitialized();
        JavaKind.ensureInitialized();
        Substitutions.ensureInitialized();
        EspressoSymbols.ensureInitialized();
        // Raw symbols are not exposed directly, use the typed interfaces: NameSymbols, TypeSymbols
        // and SignatureSymbols instead.
        // HelloWorld requires ~25K symbols. Give enough space to the symbol table to avoid resizing
        // during startup.
        int initialSymbolTableCapacity = 1 << 16;
        Symbols symbols = Symbols.fromExisting(EspressoSymbols.SYMBOLS.freeze(), initialSymbolTableCapacity);
        this.utf8Symbols = new Utf8Symbols(symbols);
        this.nameSymbols = new NameSymbols(symbols);
        this.typeSymbols = new TypeSymbols(symbols);
        this.signatureSymbols = new SignatureSymbols(symbols, typeSymbols);
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new EspressoOptionsOptionDescriptors();
    }

    public EspressoThreadLocalState getThreadLocalState() {
        return threadLocalState.get();
    }

    public EspressoThreadLocalState getThreadLocalStateFor(Thread t) {
        return threadLocalState.get(t);
    }

    @Override
    protected EspressoContext createContext(final TruffleLanguage.Env env) {
        // We cannot use env.isPreinitialization() here because the language instance that holds the
        // inner context is not under pre-initialization
        boolean isPreinitLanguageInstance = (boolean) env.getConfig().getOrDefault("preinit", false);
        if (isPreinitLanguageInstance) {
            languageCache.addCapability(EspressoLanguageCache.CacheCapability.PRE_INITIALIZED);
        }
        ensureInitialized(env);
        // TODO(peterssen): Redirect in/out to env.in()/out()
        EspressoContext context = new EspressoContext(env, this);
        context.setMainArguments(env.getApplicationArguments());
        return context;
    }

    public void ensureInitialized(final TruffleLanguage.Env env) {
        if (!fullyInitialized) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                if (!fullyInitialized) {
                    // Initialize required options.
                    initializeOptions(env);
                    initializeGuestAllocator(env);
                    // Create known shapes.
                    arrayShape = createArrayShape();
                    foreignShape = createForeignShape();
                    // Prevent further changes in cache capabilities,
                    // languageCache.freezeCapabilities();
                    // Publish initialization.
                    fullyInitialized = true;
                }
            }
        }
    }

    private void initializeOptions(final TruffleLanguage.Env env) {
        assert Thread.holdsLock(this);
        eagerFrameAnalysis = env.getOptions().get(EspressoOptions.EagerFrameAnalysis);
        verifyMode = eagerFrameAnalysis ? EspressoOptions.VerifyMode.ALL : env.getOptions().get(EspressoOptions.Verify);
        specComplianceMode = env.getOptions().get(EspressoOptions.SpecCompliance);
        livenessAnalysisMode = env.getOptions().get(EspressoOptions.LivenessAnalysis);
        livenessAnalysisMinimumLocals = env.getOptions().get(EspressoOptions.LivenessAnalysisMinimumLocals);
        previewEnabled = env.getOptions().get(EspressoOptions.EnablePreview);
        whiteBoxEnabled = env.getOptions().get(EspressoOptions.WhiteBoxAPI);
        internalJvmciEnabled = env.getOptions().get(EspressoOptions.EnableJVMCI);
        continuum = env.getOptions().get(EspressoOptions.Continuum);
        maxStackTraceDepth = env.getOptions().get(EspressoOptions.MaxJavaStackTraceDepth);

        useTRegex = env.getOptions().get(EspressoOptions.UseTRegex);
        if (useTRegex && !env.getInternalLanguages().containsKey("regex")) {
            throw EspressoError.fatal("UseTRegex is set to true but the 'regex' language is not available.");
        }

        EspressoOptions.GuestFieldOffsetStrategyEnum strategy = env.getOptions().get(EspressoOptions.GuestFieldOffsetStrategy);
        guestFieldOffsetStrategy = switch (strategy) {
            case safety -> new SafetyGuestFieldOffsetStrategy();
            case compact -> new CompactGuestFieldOffsetStrategy();
            case graal -> new GraalGuestFieldOffsetStrategy();
        };
        this.useEspressoLibs = env.getOptions().get(EspressoOptions.UseEspressoLibs);
        this.nativeBackendId = setNativeBackendId(env);
        assert guestFieldOffsetStrategy.name().equals(strategy.name());
    }

    @Override
    protected void initializeMultipleContexts() {
        // Called before any context is created. No racing issues expected.
        languageCache.addCapability(EspressoLanguageCache.CacheCapability.SHARED);
        isShared = true;
    }

    @Override
    protected void initializeContext(final EspressoContext context) throws Exception {
        if (context.getEnv().isPreInitialization()) {
            // Spawn Espresso VM in an inner context. Make sure to initialize the context
            TruffleContext ctx = context.getEnv() //
                            .newInnerContextBuilder() //
                            .initializeCreatorContext(true) //
                            .inheritAllAccess(true) //
                            .config("preinit", true) //
                            .build();
            Object prev = ctx.enter(null);
            try {
                // Retrieve caches and options and store them in the pre-initialized language
                // instance.
                EspressoContext inner = EspressoContext.get(null);
                inner.preInitializeContext();
                languageCache.addCapability(EspressoLanguageCache.CacheCapability.PRE_INITIALIZED);
                extractDataFrom(inner.getLanguage());
                languageCache.logCacheStatus();

                if (!inner.multiThreadingEnabled()) {
                    // Force collection of guest references.
                    inner.getLazyCaches().getReferenceProcessCache().execute();
                }
                // This is needed to ensure that there are no references to the inner context
                inner = null;
            } finally {
                ctx.leave(null, prev);
                ctx.close();

                // This is needed to ensure that there are no references to the inner context
                ctx = null;

                // Ensure that weak references will get collected
                System.gc();
            }
        } else {
            context.initializeContext();
        }
    }

    private void extractDataFrom(EspressoLanguage other) {
        javaVersion = other.javaVersion;
        utf8Symbols = other.getUtf8Symbols();
        nameSymbols = other.getNames();
        typeSymbols = other.getTypes();
        signatureSymbols = other.getSignatures();
        languageCache.importFrom(other.getLanguageCache());
    }

    private static String setNativeBackendId(final TruffleLanguage.Env env) {
        String nativeBackend;
        if (env.getOptions().hasBeenSet(EspressoOptions.NativeBackend)) {
            nativeBackend = env.getOptions().get(EspressoOptions.NativeBackend);
        } else {
            // Pick a sane "default" native backend depending on the platform.
            boolean isInPreInit = (boolean) env.getConfig().getOrDefault("preinit", false);
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
        return nativeBackend;
    }

    @Override
    protected boolean patchContext(EspressoContext context, Env newEnv) {
        // This check has to be done manually as long as language uses exclusive context sharing
        // policy.
        if (!areOptionsCompatible(context.getEnv().getOptions(), newEnv.getOptions())) {
            return false;
        }
        context.patchContext(newEnv);
        try {
            context.initializeContext();
        } catch (ContextPatchingException e) {
            context.getLogger().severe(e.getMessage());
            return false;
        }
        return true;
    }

    @Override
    protected boolean areOptionsCompatible(OptionValues oldOptions, OptionValues newOptions) {
        return isOptionCompatible(newOptions, oldOptions, EspressoOptions.JavaHome) &&
                        isOptionCompatible(newOptions, oldOptions, EspressoOptions.BootClasspath) &&
                        isOptionCompatible(newOptions, oldOptions, EspressoOptions.Verify) &&
                        isOptionCompatible(newOptions, oldOptions, EspressoOptions.EagerFrameAnalysis) &&
                        isOptionCompatible(newOptions, oldOptions, EspressoOptions.SpecCompliance) &&
                        isOptionCompatible(newOptions, oldOptions, EspressoOptions.LivenessAnalysis) &&
                        isOptionCompatible(newOptions, oldOptions, EspressoOptions.LivenessAnalysisMinimumLocals) &&
                        isOptionCompatible(newOptions, oldOptions, EspressoOptions.EnablePreview) &&
                        isOptionCompatible(newOptions, oldOptions, EspressoOptions.WhiteBoxAPI) &&
                        isOptionCompatible(newOptions, oldOptions, EspressoOptions.EnableJVMCI) &&
                        isOptionCompatible(newOptions, oldOptions, EspressoOptions.Continuum) &&
                        isOptionCompatible(newOptions, oldOptions, EspressoOptions.UseTRegex) &&
                        isOptionCompatible(newOptions, oldOptions, EspressoOptions.GuestFieldOffsetStrategy) &&
                        isOptionCompatible(newOptions, oldOptions, EspressoOptions.UseEspressoLibs) &&
                        isOptionCompatible(newOptions, oldOptions, EspressoOptions.NativeBackend) &&
                        isOptionCompatible(newOptions, oldOptions, EspressoOptions.MaxJavaStackTraceDepth);
    }

    private static boolean isOptionCompatible(OptionValues oldOptions, OptionValues newOptions, OptionKey<?> option) {
        return oldOptions.get(option).equals(newOptions.get(option));
    }

    @Override
    protected void exitContext(EspressoContext context, ExitMode exitMode, int exitCode) {
        if (!context.isInitialized()) {
            return;
        }

        if (exitMode == ExitMode.NATURAL) {
            // Make sure current thread is no longer considered alive by guest code.
            if (context.getVM().DetachCurrentThread(context, this) == JNI_OK) {
                // Create a new guest thread to wait for other non-daemon threads
                context.createThread(Thread.currentThread(), context.getMainThreadGroup(), "DestroyJavaVM", false);
            }
            // Wait for ongoing threads to finish.
            context.destroyVM();
        } else {
            // Here we give a chance for our threads to exit gracefully in guest code before
            // Truffle kicks in with host thread deaths.
            context.doExit(exitCode);
        }
    }

    @Override
    protected void finalizeContext(EspressoContext context) {
        context.ensureThreadsJoined();
        TruffleSafepoint sp = TruffleSafepoint.getCurrent();
        boolean prev = sp.setAllowActions(false);
        try {
            // we can still run limited guest code, even if the context is already invalid
            context.prepareDispose();
            context.cleanupNativeEnv();
        } catch (Throwable t) {
            context.getLogger().log(Level.FINER, "Exception while finalizing Espresso context", t);
            throw t;
        } finally {
            sp.setAllowActions(prev);
            context.setFinalized();
        }
        long elapsedTimeNanos = System.nanoTime() - context.getStartupClockNanos();
        long seconds = TimeUnit.NANOSECONDS.toSeconds(elapsedTimeNanos);
        if (seconds > 10) {
            context.getLogger().log(Level.FINE, "Time spent in Espresso: {0} s", seconds);
        } else {
            context.getLogger().log(Level.FINE, "Time spent in Espresso: {0} ms", TimeUnit.NANOSECONDS.toMillis(elapsedTimeNanos));
        }
    }

    @Override
    protected Object getScope(EspressoContext context) {
        return context.getBindings();
    }

    @Override
    protected void disposeContext(final EspressoContext context) {
        context.disposeContext();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected CallTarget parse(final ParsingRequest request) throws Exception {
        assert EspressoContext.get(null).isInitialized();
        String contents = request.getSource().getCharacters().toString();
        if (com.oracle.truffle.espresso.nodes.commands.DestroyVMNode.EVAL_NAME.equals(contents)) {
            RootNode node = new com.oracle.truffle.espresso.nodes.commands.DestroyVMNode(this);
            return node.getCallTarget();
        }
        if (ExitCodeNode.EVAL_NAME.equals(contents)) {
            RootNode node = new ExitCodeNode(this);
            return node.getCallTarget();
        }
        if (GetBindingsNode.EVAL_NAME.equals(contents)) {
            RootNode node = new GetBindingsNode(this);
            return node.getCallTarget();
        }
        if (ReferenceProcessRootNode.EVAL_NAME.equals(contents)) {
            RootNode node = new ReferenceProcessRootNode(this);
            return node.getCallTarget();
        }
        throw new EspressoParseError(
                        "Espresso cannot evaluate Java sources directly, only a few special commands are supported: " + GetBindingsNode.EVAL_NAME + " and " + ReferenceProcessRootNode.EVAL_NAME +
                                        "\n" +
                                        "Use the \"" + ID + "\" language bindings to load guest Java classes e.g. context.getBindings(\"" + ID + "\").getMember(\"java.lang.Integer\")");
    }

    @Override
    public NameSymbols getNames() {
        return nameSymbols;
    }

    public Utf8Symbols getUtf8Symbols() {
        return utf8Symbols;
    }

    @Override
    public TypeSymbols getTypes() {
        return typeSymbols;
    }

    @Override
    public SignatureSymbols getSignatures() {
        return signatureSymbols;
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread,
                    boolean singleThreaded) {
        // allow access from any thread instead of just one
        return true;
    }

    @Override
    protected void initializeMultiThreading(EspressoContext context) {
        // perform actions when the context is switched to multi-threading
        // context.singleThreaded.invalidate();
    }

    @Override
    protected void initializeThread(EspressoContext context, Thread thread) {
        if (context.isFinalized()) {
            // we can no longer run guest code
            context.getLogger().log(Level.FINE, "Context is already finalized, ignoring request to initialize a new thread");
            return;
        }
        context.createThread(thread);
    }

    @Override
    protected void disposeThread(EspressoContext context, Thread thread) {
        context.disposeThread(thread);
    }

    public StaticProperty getArrayProperty() {
        return arrayProperty;
    }

    public StaticProperty getArrayHashCodeProperty() {
        if (!continuum) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("Accessing array hash code property without continuum set up.");
        }
        return arrayHashCodeProperty;
    }

    public StaticShape<StaticObjectFactory> getArrayShape() {
        assert fullyInitialized : "Array shape accessed before language is fully initialized";
        return arrayShape;
    }

    @TruffleBoundary
    private StaticShape<StaticObjectFactory> createArrayShape() {
        assert arrayShape == null;
        StaticShape.Builder builder = StaticShape.newBuilder(this).property(arrayProperty, Object.class, true);
        if (continuum) {
            builder.property(arrayHashCodeProperty, int.class, false);
        }
        return builder.build(StaticObject.class, StaticObjectFactory.class);
    }

    public StaticProperty getForeignProperty() {
        return foreignProperty;
    }

    public StaticProperty getTypeArgumentProperty() {
        return typeArgumentProperty;
    }

    public StaticShape<StaticObjectFactory> getForeignShape() {
        assert fullyInitialized : "Array shape accessed before language is fully initialized";
        return foreignShape;
    }

    @TruffleBoundary
    private StaticShape<StaticObjectFactory> createForeignShape() {
        assert foreignShape == null;
        return StaticShape.newBuilder(this).property(foreignProperty, Object.class, true).property(typeArgumentProperty, EspressoType[].class, true).build(StaticObject.class,
                        StaticObjectFactory.class);
    }

    private static final LanguageReference<EspressoLanguage> REFERENCE = LanguageReference.create(EspressoLanguage.class);

    public static EspressoLanguage get(Node node) {
        return REFERENCE.get(node);
    }

    public JavaVersion getJavaVersion() {
        return javaVersion;
    }

    public EspressoOptions.SpecComplianceMode getSpecComplianceMode() {
        return specComplianceMode;
    }

    public EspressoOptions.LivenessAnalysisMode getLivenessAnalysisMode() {
        return livenessAnalysisMode;
    }

    public EspressoOptions.VerifyMode getVerifyMode() {
        return verifyMode;
    }

    public int livenessAnalysisMinimumLocals() {
        return livenessAnalysisMinimumLocals;
    }

    public boolean isAllocationTrackingDisabled() {
        return noAllocationTracking.isValid();
    }

    public void invalidateAllocationTrackingDisabled() {
        noAllocationTracking.invalidate();
    }

    public boolean isPreviewEnabled() {
        return previewEnabled;
    }

    public boolean isWhiteBoxEnabled() {
        return whiteBoxEnabled;
    }

    public boolean isEagerFrameAnalysisEnabled() {
        return eagerFrameAnalysis;
    }

    public boolean isInternalJVMCIEnabled() {
        return internalJvmciEnabled;
    }

    public boolean isJVMCIEnabled() {
        return internalJvmciEnabled;
    }

    public boolean useTRegex() {
        return useTRegex;
    }

    public boolean useEspressoLibs() {
        return useEspressoLibs;
    }

    public String nativeBackendId() {
        return nativeBackendId;
    }

    public boolean isContinuumEnabled() {
        return continuum;
    }

    public EspressoLanguageCache getLanguageCache() {
        return languageCache;
    }

    public GuestAllocator getAllocator() {
        return allocator;
    }

    public void initializeGuestAllocator(TruffleLanguage.Env env) {
        this.allocator = new GuestAllocator(this, env.lookup(AllocationReporter.class));
    }

    @Idempotent
    public boolean isShared() {
        return isShared;
    }

    @SuppressFBWarnings(value = "DC_DOUBLECHECK", //
                    justification = "non-volatile for performance reasons, javaVersion is initialized very early during context creation with an enum value, only benign races expected.")
    public void tryInitializeJavaVersion(JavaVersion version) {
        Objects.requireNonNull(version);
        JavaVersion ref = this.javaVersion;
        if (ref == null) {
            synchronized (this) {
                ref = this.javaVersion;
                if (ref == null) {
                    if (!getGuestFieldOffsetStrategy().isAllowed(version)) {
                        throw EspressoError.fatal("This guest field offset strategy (" + getGuestFieldOffsetStrategy().name() + ") is not allowed with this Java version (" + version + ")");
                    }
                    if (useTRegex && !version.java21OrLater()) {
                        throw EspressoError.fatal("UseTRegex is not available for context running Java version < 21.");
                    }
                    this.javaVersion = ref = version;
                }
            }
        }
        EspressoError.guarantee(version.equals(ref), "incompatible Java versions");
    }

    public StaticObject getCurrentVirtualThread() {
        return getThreadLocalState().getCurrentVirtualThread();
    }

    public void setCurrentVirtualThread(StaticObject thread) {
        getThreadLocalState().setCurrentVirtualThread(thread);
    }

    public static Path getEspressoLibs(TruffleLanguage.Env env) {
        Path espressoHome = HomeFinder.getInstance().getLanguageHomes().get(EspressoLanguage.ID);
        if (espressoHome != null) {
            Path libs = espressoHome.resolve("lib");
            if (Files.isDirectory(libs)) {
                env.getLogger(EspressoContext.class).config(() -> "Using espresso libs from language home at " + libs);
                return libs;
            }
        }
        try {
            String resources = env.getInternalResource("espresso-libs").getAbsoluteFile().toString();
            Path libs = Path.of(resources, "lib");
            assert Files.isDirectory(libs);
            env.getLogger(EspressoContext.class).config(() -> "Using espresso libs from resources at " + libs);
            return libs;
        } catch (IOException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    private static final String[] KNOWN_ESPRESSO_RUNTIMES = {"jdk25", "openjdk25", "jdk21", "openjdk21", "jdk" + JavaVersion.HOST_VERSION, "openjdk" + JavaVersion.HOST_VERSION};
    private static final Pattern VALID_RESOURCE_ID = Pattern.compile("[0-9a-z\\-]+");

    public static Path getEspressoRuntime(TruffleLanguage.Env env) {
        if (env.getOptions().hasBeenSet(EspressoOptions.JavaHome)) {
            if (env.getOptions().hasBeenSet(EspressoOptions.RuntimeResourceId)) {
                env.getLogger(EspressoContext.class).warning("Both java.JavaHome and java.RuntimeResourceId are set. RuntimeResourceId will be ignored.");
            }
            // This option's value will be used, no need to guess
            return null;
        }
        try {
            if (env.getOptions().hasBeenSet(EspressoOptions.RuntimeResourceId)) {
                String runtimeName = env.getOptions().get(EspressoOptions.RuntimeResourceId);
                if (!VALID_RESOURCE_ID.matcher(runtimeName).matches()) {
                    throw EspressoError.fatal("Invalid RuntimeResourceId: " + runtimeName);
                }
                TruffleFile resource = env.getInternalResource("espresso-runtime-" + runtimeName);
                if (resource == null) {
                    throw EspressoError.fatal("Couldn't find: espresso-runtime-" + runtimeName + " internal resource.\n" +
                                    "Did you add the corresponding jar to the class or module path?");
                }
                Path resources = Path.of(resource.getAbsoluteFile().toString());
                assert Files.isDirectory(resources);
                env.getLogger(EspressoContext.class).config(() -> "Using " + runtimeName + " runtime at " + resources);
                return resources;
            }
            for (String runtimeName : KNOWN_ESPRESSO_RUNTIMES) {
                TruffleFile resource = env.getInternalResource("espresso-runtime-" + runtimeName);
                if (resource != null) {
                    Path resources = Path.of(resource.getAbsoluteFile().toString());
                    if (Files.isDirectory(resources)) {
                        env.getLogger(EspressoContext.class).config(() -> "Selected " + runtimeName + " runtime at " + resources);
                        return resources;
                    }
                }
            }
        } catch (IOException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
        // Try to figure out if we are running in a legacy GraalVM or standalone
        Path espressoHome = HomeFinder.getInstance().getLanguageHomes().get(EspressoLanguage.ID);
        if (espressoHome != null && Files.isDirectory(espressoHome)) {
            // ESPRESSO_HOME = GRAALVM_JAVA_HOME/languages/java
            Path graalvmHome = HomeFinder.getInstance().getHomeFolder();
            try {
                if (graalvmHome != null) {
                    Path expectedLanguageHome = graalvmHome.resolve("languages").resolve("java");
                    if (Files.isDirectory(expectedLanguageHome) && Files.isSameFile(espressoHome, expectedLanguageHome)) {
                        env.getLogger(EspressoContext.class).config(() -> "Using graalvm home at " + graalvmHome);
                        return graalvmHome;
                    }
                }
                Path tentativeHome = espressoHome.resolve("..").resolve("..");
                Path expectedReleaseFile = tentativeHome.resolve("release");
                if (Files.isRegularFile(expectedReleaseFile)) {
                    Path normalized = tentativeHome.normalize();
                    env.getLogger(EspressoContext.class).config(() -> "Using graalvm-like home at " + normalized);
                    return normalized;
                }
            } catch (IOException e) {
                env.getLogger(EspressoContext.class).log(Level.WARNING, "Error while probing espresso and graalvm home", e);
            }
        }
        if (OS.getCurrent() == OS.Linux && JavaVersion.HOST_VERSION.compareTo(JavaVersion.latestSupported()) <= 0) {
            if (!EspressoOptions.RUNNING_ON_SVM || (boolean) env.getConfig().getOrDefault("preinit", false)) {
                // we might be able to use the host runtime libraries
                env.getLogger(EspressoContext.class).config("Trying to use the host's runtime libraries");
                return Paths.get(System.getProperty("java.home"));
            }
        }
        throw EspressoError.fatal("Couldn't find suitable runtime libraries for espresso. You can try to\n" +
                        "add a jar with the necessary resources such as org.graalvm.espresso:espresso-runtime-resources-*,\n" +
                        "or set java.JavaHome explicitly.");
    }

    public DisableSingleStepping disableStepping() {
        return new DisableSingleStepping();
    }

    public int getMaxStackTraceDepth() {
        return maxStackTraceDepth;
    }

    public final class DisableSingleStepping implements AutoCloseable {

        private final boolean steppingDisabled;

        private DisableSingleStepping() {
            steppingDisabled = getThreadLocalState().disableSingleStepping(false);
        }

        @Override
        public void close() {
            if (steppingDisabled) {
                getThreadLocalState().enableSingleStepping();
            }
        }
    }

    public JImageExtensions getJImageExtensions() {
        return jImageExtensions;
    }

    public GuestFieldOffsetStrategy getGuestFieldOffsetStrategy() {
        return guestFieldOffsetStrategy;
    }

    public StaticObject getPendingException() {
        return getThreadLocalState().getPendingExceptionObject();
    }

    public EspressoException getPendingEspressoException() {
        return getThreadLocalState().getPendingException();
    }

    public void clearPendingException() {
        getThreadLocalState().clearPendingException();
    }

    public void setPendingException(EspressoException ex) {
        getThreadLocalState().setPendingException(ex);
    }
}
