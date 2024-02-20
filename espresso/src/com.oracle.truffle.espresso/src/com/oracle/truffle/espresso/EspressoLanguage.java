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
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

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
import com.oracle.truffle.espresso.descriptors.Names;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.StaticSymbols;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Symbols;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.descriptors.Utf8ConstantTable;
import com.oracle.truffle.espresso.impl.SuppressFBWarnings;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.nodes.commands.ExitCodeNode;
import com.oracle.truffle.espresso.nodes.commands.GetBindingsNode;
import com.oracle.truffle.espresso.nodes.commands.ReferenceProcessRootNode;
import com.oracle.truffle.espresso.preinit.ContextPatchingException;
import com.oracle.truffle.espresso.preinit.EspressoLanguageCache;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoThreadLocalState;
import com.oracle.truffle.espresso.runtime.GuestAllocator;
import com.oracle.truffle.espresso.runtime.JavaVersion;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject.StaticObjectFactory;
import com.oracle.truffle.espresso.substitutions.Substitutions;

// TODO: Update website once Espresso has one
@Registration(id = EspressoLanguage.ID, //
                name = EspressoLanguage.NAME, //
                implementationName = EspressoLanguage.IMPLEMENTATION_NAME, //
                contextPolicy = TruffleLanguage.ContextPolicy.SHARED, //
                dependentLanguages = "nfi", //
                website = "https://www.graalvm.org/dev/reference-manual/java-on-truffle/")
@ProvidedTags({StandardTags.RootTag.class, StandardTags.RootBodyTag.class, StandardTags.StatementTag.class})
public final class EspressoLanguage extends TruffleLanguage<EspressoContext> {

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

    @CompilationFinal private Utf8ConstantTable utf8Constants;
    @CompilationFinal private Names names;
    @CompilationFinal private Types types;
    @CompilationFinal private Signatures signatures;

    private final StaticProperty arrayProperty = new DefaultStaticProperty("array");
    // This field should be final, but creating a shape requires a fully-initialized instance of
    // TruffleLanguage.
    @CompilationFinal //
    private StaticShape<StaticObjectFactory> arrayShape;

    private final StaticProperty foreignProperty = new DefaultStaticProperty("foreignObject");
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

    private final ContextThreadLocal<EspressoThreadLocalState> threadLocalState = locals.createContextThreadLocal((context, thread) -> new EspressoThreadLocalState(context));

    public EspressoLanguage() {
        // Initialize statically defined symbols and substitutions.
        JavaKind.ensureInitialized();
        Name.ensureInitialized();
        Type.ensureInitialized();
        Signature.ensureInitialized();
        Substitutions.ensureInitialized();

        // Raw symbols are not exposed directly, use the typed interfaces: Names, Types and
        // Signatures instead.
        Symbols symbols = new Symbols(StaticSymbols.freeze());
        this.utf8Constants = new Utf8ConstantTable(symbols);
        this.names = new Names(symbols);
        this.types = new Types(symbols);
        this.signatures = new Signatures(symbols, types);
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
        verifyMode = env.getOptions().get(EspressoOptions.Verify);
        specComplianceMode = env.getOptions().get(EspressoOptions.SpecCompliance);
        livenessAnalysisMode = env.getOptions().get(EspressoOptions.LivenessAnalysis);
        livenessAnalysisMinimumLocals = env.getOptions().get(EspressoOptions.LivenessAnalysisMinimumLocals);
        previewEnabled = env.getOptions().get(EspressoOptions.EnablePreview);
        whiteBoxEnabled = env.getOptions().get(EspressoOptions.WhiteBoxAPI);
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
        utf8Constants = other.getUtf8ConstantTable();
        names = other.getNames();
        types = other.getTypes();
        signatures = other.getSignatures();
        languageCache.importFrom(other.getLanguageCache());
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
                        isOptionCompatible(newOptions, oldOptions, EspressoOptions.SpecCompliance) &&
                        isOptionCompatible(newOptions, oldOptions, EspressoOptions.LivenessAnalysis) &&
                        isOptionCompatible(newOptions, oldOptions, EspressoOptions.LivenessAnalysisMinimumLocals) &&
                        isOptionCompatible(newOptions, oldOptions, EspressoOptions.EnablePreview) &&
                        isOptionCompatible(newOptions, oldOptions, EspressoOptions.WhiteBoxAPI);
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
            if (context.getVM().DetachCurrentThread(context) == JNI_OK) {
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

    public Utf8ConstantTable getUtf8ConstantTable() {
        return utf8Constants;
    }

    public Names getNames() {
        return names;
    }

    public Types getTypes() {
        return types;
    }

    public Signatures getSignatures() {
        return signatures;
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

    public StaticShape<StaticObjectFactory> getArrayShape() {
        assert fullyInitialized : "Array shape accessed before language is fully initialized";
        return arrayShape;
    }

    @TruffleBoundary
    private StaticShape<StaticObjectFactory> createArrayShape() {
        assert arrayShape == null;
        return StaticShape.newBuilder(this).property(arrayProperty, Object.class, true).build(StaticObject.class, StaticObjectFactory.class);
    }

    public StaticProperty getForeignProperty() {
        return foreignProperty;
    }

    public StaticShape<StaticObjectFactory> getForeignShape() {
        assert fullyInitialized : "Array shape accessed before language is fully initialized";
        return foreignShape;
    }

    @TruffleBoundary
    private StaticShape<StaticObjectFactory> createForeignShape() {
        assert foreignShape == null;
        return StaticShape.newBuilder(this).property(foreignProperty, Object.class, true).build(StaticObject.class, StaticObjectFactory.class);
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
        JavaVersion ref = this.javaVersion;
        if (ref == null) {
            synchronized (this) {
                ref = this.javaVersion;
                if (ref == null) {
                    this.javaVersion = ref = Objects.requireNonNull(version);
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
                return libs;
            }
        }
        try {
            String resources = env.getInternalResource("espresso-libs").getAbsoluteFile().toString();
            Path libs = Path.of(resources, "lib");
            assert Files.isDirectory(libs);
            return libs;
        } catch (IOException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    public static Path getEspressoRuntime(TruffleLanguage.Env env) {
        // If --java.JavaHome is not specified, Espresso tries to use the same (jars and native)
        // libraries bundled with GraalVM.
        // Try to figure out if we are running in the GraalVM
        Path espressoHome = HomeFinder.getInstance().getLanguageHomes().get(EspressoLanguage.ID);
        if (espressoHome != null && Files.isDirectory(espressoHome)) {
            // ESPRESSO_HOME = GRAALVM_JAVA_HOME/languages/java
            Path graalvmHome = HomeFinder.getInstance().getHomeFolder();
            if (graalvmHome != null) {
                try {
                    Path expectedLanguageHome = graalvmHome.resolve("languages").resolve("java");
                    if (Files.isDirectory(expectedLanguageHome) && Files.isSameFile(espressoHome, expectedLanguageHome)) {
                        return graalvmHome;
                    }
                } catch (IOException e) {
                    env.getLogger(EspressoContext.class).log(Level.WARNING, "Error while probing espresso and graalvm home", e);
                }
            }
        }
        try {
            Path resources = Path.of(env.getInternalResource("espresso-runtime").getAbsoluteFile().toString());
            assert Files.isDirectory(resources);
            return resources;
        } catch (IOException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    public DisableSingleStepping disableStepping() {
        return new DisableSingleStepping();
    }

    public final class DisableSingleStepping implements AutoCloseable {

        private DisableSingleStepping() {
            getThreadLocalState().disableSingleStepping();
        }

        @Override
        public void close() {
            getThreadLocalState().enableSingleStepping();
        }
    }
}
