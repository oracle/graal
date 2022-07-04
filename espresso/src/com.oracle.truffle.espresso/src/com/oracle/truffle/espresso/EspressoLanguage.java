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

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.graalvm.home.Version;
import org.graalvm.options.OptionDescriptors;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
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
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.nodes.commands.ExitCodeNode;
import com.oracle.truffle.espresso.nodes.commands.GetBindingsNode;
import com.oracle.truffle.espresso.nodes.commands.ReferenceProcessNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoThreadLocalState;
import com.oracle.truffle.espresso.runtime.JavaVersion;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObject.StaticObjectFactory;
import com.oracle.truffle.espresso.substitutions.Substitutions;

// TODO: Update website once Espresso has one
@Registration(id = EspressoLanguage.ID, //
                name = EspressoLanguage.NAME, //
                implementationName = EspressoLanguage.IMPLEMENTATION_NAME, //
                contextPolicy = TruffleLanguage.ContextPolicy.EXCLUSIVE, //
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

    private final Utf8ConstantTable utf8Constants;
    private final Names names;
    private final Types types;
    private final Signatures signatures;

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
    @CompilationFinal private EspressoOptions.VerifyMode verifyMode;
    @CompilationFinal private EspressoOptions.SpecComplianceMode specComplianceMode;
    @CompilationFinal private EspressoOptions.LivenessAnalysisMode livenessAnalysisMode;
    @CompilationFinal private int livenessAnalysisMinimumLocals;

    private boolean optionsInitialized;
    // endregion Options

    // region allocation tracking
    @CompilationFinal private final Assumption noAllocationTracking = Assumption.create("Espresso no allocation tracking assumption");

    private final ContextThreadLocal<EspressoThreadLocalState> threadLocalState = createContextThreadLocal((context, thread) -> new EspressoThreadLocalState(context));

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
        initializeOptions(env);

        // TODO(peterssen): Redirect in/out to env.in()/out()
        EspressoContext context = new EspressoContext(env, this);
        context.setMainArguments(env.getApplicationArguments());
        return context;
    }

    private void initializeOptions(final TruffleLanguage.Env env) {
        if (!optionsInitialized) {
            verifyMode = env.getOptions().get(EspressoOptions.Verify);
            specComplianceMode = env.getOptions().get(EspressoOptions.SpecCompliance);
            livenessAnalysisMode = env.getOptions().get(EspressoOptions.LivenessAnalysis);
            livenessAnalysisMinimumLocals = env.getOptions().get(EspressoOptions.LivenessAnalysisMinimumLocals);
            optionsInitialized = true;
        }
    }

    @Override
    protected void initializeContext(final EspressoContext context) throws Exception {
        context.initializeContext();
    }

    @Override
    protected void exitContext(EspressoContext context, ExitMode exitMode, int exitCode) {
        if (exitMode == ExitMode.NATURAL) {
            // Make sure current thread is no longer considered alive by guest code.
            if (context.getVM().DetachCurrentThread(context) == JNI_OK) {
                // Create a new guest thread to wait for other non-daemon threads
                context.createThread(Thread.currentThread(), context.getMainThreadGroup(), "DestroyJavaVM", false);
            }
            // Wait for ongoing threads to finish.
            context.destroyVM();
        } else {
            try {
                // Here we give a chance for our threads to exit gracefully in guest code before
                // Truffle kicks in with host thread deaths.
                context.doExit(exitCode);
            } finally {
                context.cleanupNativeEnv(); // This must be done here in case of a hard exit.
            }
        }
    }

    @Override
    protected void finalizeContext(EspressoContext context) {
        if (!context.isTruffleClosed()) {
            // If context is closed, we cannot run any guest code for cleanup.
            context.prepareDispose();
            context.cleanupNativeEnv();
        }
        long elapsedTimeNanos = System.nanoTime() - context.getStartupClockNanos();
        long seconds = TimeUnit.NANOSECONDS.toSeconds(elapsedTimeNanos);
        if (seconds > 10) {
            context.getLogger().log(Level.FINE, "Time spent in Espresso: {0} s", seconds);
        } else {
            context.getLogger().log(Level.FINE, "Time spent in Espresso: {0} ms", TimeUnit.NANOSECONDS.toMillis(elapsedTimeNanos));
        }
        context.setFinalized();
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
        if (ReferenceProcessNode.EVAL_NAME.equals(contents)) {
            RootNode node = new ReferenceProcessNode(this);
            return node.getCallTarget();
        }
        throw new EspressoParseError(
                        "Espresso cannot evaluate Java sources directly, only a few special commands are supported: " + GetBindingsNode.EVAL_NAME + " and " + ReferenceProcessNode.EVAL_NAME + "\n" +
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
        if (arrayShape == null) {
            arrayShape = createArrayShape();
        }
        return arrayShape;
    }

    @CompilerDirectives.TruffleBoundary
    private StaticShape<StaticObjectFactory> createArrayShape() {
        assert arrayShape == null;
        return StaticShape.newBuilder(this).property(arrayProperty, Object.class, true).build(StaticObject.class, StaticObjectFactory.class);
    }

    public StaticProperty getForeignProperty() {
        return foreignProperty;
    }

    public StaticShape<StaticObjectFactory> getForeignShape() {
        if (foreignShape == null) {
            foreignShape = createForeignShape();
        }
        return foreignShape;
    }

    @CompilerDirectives.TruffleBoundary
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
}
