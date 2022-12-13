/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.truffle;

import static org.graalvm.compiler.java.BytecodeParserOptions.InlineDuringParsingMaxDepth;
import static org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.createStandardInlineInfo;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoublePredicate;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleToIntFunction;
import java.util.function.DoubleToLongFunction;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.IntToDoubleFunction;
import java.util.function.IntToLongFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.LongBinaryOperator;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;
import java.util.function.LongToDoubleFunction;
import java.util.function.LongToIntFunction;
import java.util.function.LongUnaryOperator;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.ObjLongConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongBiFunction;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.phases.HighTier;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.RequiredInvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.compiler.nodes.asserts.NeverPartOfCompilationNode;
import org.graalvm.compiler.truffle.compiler.phases.TruffleHostInliningPhase;
import org.graalvm.compiler.truffle.compiler.substitutions.KnownTruffleTypes;
import org.graalvm.compiler.truffle.compiler.substitutions.TruffleInvocationPlugins;
import org.graalvm.compiler.truffle.runtime.TruffleCallBoundary;
import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.hosted.RuntimeCompilationFeature;
import com.oracle.svm.graal.hosted.RuntimeCompilationFeature.AbstractCallTreeNode;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.AfterAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.truffle.api.SubstrateThreadLocalHandshake;
import com.oracle.svm.truffle.api.SubstrateThreadLocalHandshakeSnippets;
import com.oracle.svm.truffle.api.SubstrateTruffleCompiler;
import com.oracle.svm.truffle.api.SubstrateTruffleRuntime;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Feature that enables compilation of Truffle ASTs to machine code. This feature requires
 * {@link SubstrateTruffleRuntime} to be set as {@link TruffleRuntime}.
 */
public class TruffleFeature implements InternalFeature {

    @Override
    public String getURL() {
        return "https://github.com/oracle/graal/blob/master/substratevm/src/com.oracle.svm.truffle/src/com/oracle/svm/truffle/TruffleFeature.java";
    }

    @Override
    public String getDescription() {
        return "Enables compilation of Truffle ASTs to machine code";
    }

    public static class Options {
        @Option(help = "Print truffle boundaries found during the analysis")//
        public static final HostedOptionKey<Boolean> PrintStaticTruffleBoundaries = new HostedOptionKey<>(false);

        @Option(help = "Check that CompilerAsserts.neverPartOfCompilation is not reachable for runtime compilation")//
        public static final HostedOptionKey<Boolean> TruffleCheckNeverPartOfCompilation = new HostedOptionKey<>(true);

        @Option(help = "Enforce that the Truffle runtime provides the only implementation of Frame")//
        public static final HostedOptionKey<Boolean> TruffleCheckFrameImplementation = new HostedOptionKey<>(true);

        @Option(help = "Fail if a method known as not suitable for partial evaluation is reachable for runtime compilation", deprecated = true)//
        public static final HostedOptionKey<Boolean> TruffleCheckBlackListedMethods = new HostedOptionKey<>(true);

        @Option(help = "Fail if a method known as not suitable for partial evaluation is reachable for runtime compilation")//
        public static final HostedOptionKey<Boolean> TruffleCheckBlockListMethods = new HostedOptionKey<>(true);

        @Option(help = "Inline trivial methods in Truffle graphs during native image generation")//
        public static final HostedOptionKey<Boolean> TruffleInlineDuringParsing = new HostedOptionKey<>(true);

    }

    public static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(TruffleFeature.class);
        }
    }

    private final Set<ResolvedJavaMethod> blocklistMethods;
    private final Set<ResolvedJavaMethod> tempTargetAllowlistMethods;
    private final Set<ResolvedJavaMethod> implementationOnlyBlocklist;
    private final Set<ResolvedJavaMethod> warnMethods;
    private final Set<ResolvedJavaMethod> warnViolations;
    private final Set<Pair<ResolvedJavaMethod, String>> neverPartOfCompilationViolations;
    Set<AnalysisMethod> runtimeCompiledMethods;

    public TruffleFeature() {
        blocklistMethods = new HashSet<>();
        tempTargetAllowlistMethods = new HashSet<>();
        implementationOnlyBlocklist = new HashSet<>();
        warnMethods = new HashSet<>();
        warnViolations = ConcurrentHashMap.newKeySet();
        neverPartOfCompilationViolations = ConcurrentHashMap.newKeySet();
    }

    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(SubstrateThreadLocalHandshake.FOREIGN_POLL);
    }

    @Override
    @SuppressWarnings("unused")
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Providers providers,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        new SubstrateThreadLocalHandshakeSnippets(options, providers, lowerings);
    }

    @Override
    public void registerInvocationPlugins(Providers providers, SnippetReflectionProvider snippetReflection, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        Architecture arch = providers.getLowerer().getTarget().arch;
        if (arch instanceof AMD64 || arch instanceof AArch64) {
            TruffleInvocationPlugins.register(arch, plugins.getInvocationPlugins(), providers.getReplacements());
        }
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(RuntimeCompilationFeature.getRuntimeCompilationFeature(), TruffleBaseFeature.class);
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return Truffle.getRuntime() instanceof SubstrateTruffleRuntime;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        UserError.guarantee(Truffle.getRuntime() instanceof SubstrateTruffleRuntime,
                        "TruffleFeature requires SubstrateTruffleRuntime");
        ((SubstrateTruffleRuntime) Truffle.getRuntime()).resetHosted();
    }

    @Override
    public void cleanup() {
        if (Truffle.getRuntime() instanceof SubstrateTruffleRuntime) {
            ((SubstrateTruffleRuntime) Truffle.getRuntime()).resetNativeImageState();
        }
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        var access = (DuringSetupAccessImpl) a;
        access.getHostVM().registerNeverInlineTrivialHandler(this::neverInlineTrivial);

        if (!ImageSingletons.contains(TruffleSupport.class)) {
            ImageSingletons.add(TruffleSupport.class, new TruffleSupport());
        }
    }

    private void registerNeverPartOfCompilation(InvocationPlugins plugins) {
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins, CompilerAsserts.class);
        r.setAllowOverwrite(true);
        r.register(new RequiredInvocationPlugin("neverPartOfCompilation") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                return handleNeverPartOfCompilation(b, targetMethod, null);
            }
        });
        r.register(new RequiredInvocationPlugin("neverPartOfCompilation", String.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode message) {
                return handleNeverPartOfCompilation(b, targetMethod, message);
            }
        });
    }

    private boolean handleNeverPartOfCompilation(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode messageNode) {
        String message = "CompilerAsserts.neverPartOfCompilation()";
        if (messageNode != null && messageNode.isConstant()) {
            message = messageNode.asConstant().toValueString();
        }
        NeverPartOfCompilationNode neverPartOfCompilation = b.add(new NeverPartOfCompilationNode(message));

        if (Options.TruffleCheckNeverPartOfCompilation.getValue()) {
            if (neverPartOfCompilation.stateAfter().getMethod().getDeclaringClass().equals(targetMethod.getDeclaringClass())) {
                /* Ignore internal use from another method in CompilerAsserts class. */
            } else {
                LinkedList<String> callTree = new LinkedList<>();
                FrameState cur = neverPartOfCompilation.stateAfter();
                while (cur != null) {
                    callTree.add(cur.getMethod().format("%H.%n(%p)"));
                    cur = cur.outerFrameState();
                }
                callTree.removeLast(); // this method will be b.getMethod
                neverPartOfCompilationViolations.add(Pair.create(b.getMethod(), String.join(",", callTree)));
            }
        }

        return true;
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        SubstrateTruffleRuntime truffleRuntime = (SubstrateTruffleRuntime) Truffle.getRuntime();
        BeforeAnalysisAccessImpl config = (BeforeAnalysisAccessImpl) access;

        ImageSingletons.lookup(TruffleBaseFeature.class).setProfilingEnabled(truffleRuntime.isProfilingEnabled());

        for (Class<?> initType : truffleRuntime.getLookupTypes()) {
            config.registerAsUsed(initType, "Truffle runtime init type.");
        }

        // register thread local foreign poll as compiled otherwise the stub won't work
        config.registerAsRoot((AnalysisMethod) SubstrateThreadLocalHandshake.FOREIGN_POLL.findMethod(config.getMetaAccess()), true);

        RuntimeCompilationFeature runtimeCompilationFeature = RuntimeCompilationFeature.singleton();
        SnippetReflectionProvider snippetReflection = runtimeCompilationFeature.getHostedProviders().getSnippetReflection();
        SubstrateTruffleCompiler truffleCompiler = truffleRuntime.initTruffleCompiler();
        truffleRuntime.initializeKnownMethods(config.getMetaAccess());
        truffleRuntime.initializeHostedKnownMethods(config.getUniverse().getOriginalMetaAccess());

        PartialEvaluator partialEvaluator = truffleCompiler.getPartialEvaluator();
        registerKnownTruffleFields(config, partialEvaluator.getKnownTruffleTypes());
        TruffleSupport.singleton().registerInterpreterEntryMethodsAsCompiled(partialEvaluator, access);

        GraphBuilderConfiguration graphBuilderConfig = partialEvaluator.getConfigPrototype();

        if (Options.TruffleInlineDuringParsing.getValue()) {
            graphBuilderConfig.getPlugins().appendInlineInvokePlugin(
                            new TruffleParsingInlineInvokePlugin(config.getHostVM(), runtimeCompilationFeature.getHostedProviders().getReplacements(),
                                            graphBuilderConfig.getPlugins().getInvocationPlugins(),
                                            partialEvaluator, this::allowRuntimeCompilation));
        }

        registerNeverPartOfCompilation(graphBuilderConfig.getPlugins().getInvocationPlugins());
        graphBuilderConfig.getPlugins().getInvocationPlugins().closeRegistration();

        Providers peProviders = partialEvaluator.getProviders();
        HostedProviders newHostedProviders = new HostedProviders(
                        peProviders.getMetaAccess(),
                        peProviders.getCodeCache(),
                        peProviders.getConstantReflection(),
                        new HostedTruffleConstantFieldProvider(peProviders.getConstantFieldProvider()),
                        peProviders.getForeignCalls(),
                        peProviders.getLowerer(),
                        peProviders.getReplacements(),
                        peProviders.getStampProvider(),
                        snippetReflection,
                        runtimeCompilationFeature.getHostedProviders().getWordTypes(),
                        runtimeCompilationFeature.getHostedProviders().getPlatformConfigurationProvider(),
                        runtimeCompilationFeature.getHostedProviders().getMetaAccessExtensionProvider(),
                        runtimeCompilationFeature.getHostedProviders().getLoopsDataProvider());
        newHostedProviders.setGraphBuilderPlugins(graphBuilderConfig.getPlugins());

        runtimeCompilationFeature.initializeRuntimeCompilationConfiguration(newHostedProviders, graphBuilderConfig, this::allowRuntimeCompilation, this::deoptimizeOnException);
        for (ResolvedJavaMethod method : partialEvaluator.getCompilationRootMethods()) {
            runtimeCompilationFeature.prepareMethodForRuntimeCompilation(method, config);
        }

        initializeMethodBlocklist(config.getMetaAccess(), access);

        /*
         * Stack frames that are visited by Truffle-level stack walking must have full frame
         * information available, otherwise SubstrateStackIntrospection cannot visit them.
         */
        for (ResolvedJavaMethod method : truffleRuntime.getAnyFrameMethod()) {
            runtimeCompilationFeature.requireFrameInformationForMethod(method);
            /*
             * To avoid corner case errors, we also force compilation of these methods. This only
             * affects builds where no Truffle language is included, because any real language makes
             * these methods reachable (and therefore compiled).
             */
            config.registerAsRoot((AnalysisMethod) method, true);
        }

        /*
         * The concrete subclass of OptimizedCallTarget needs to be registered as in heap for the
         * forced compilation of frame methods to work. Forcing compilation of a method effectively
         * adds it as a root and non-static root methods are only compiled if types implementing
         * them or any of their subtypes are allocated.
         */
        config.registerAsInHeap(TruffleSupport.singleton().getOptimizedCallTargetClass(), "Concrete subclass of OptimizedCallTarget registered by TruffleFeature.");

        /*
         * This effectively initializes the Truffle fallback engine which does all the system
         * property option parsing to initialize the profilingEnabled flag correctly. A polyglot
         * fallback engine can not stay in the the image though, so we clear it right after. We
         * don't expect it to be used except for profiling enabled check.
         */
        TruffleBaseFeature.invokeStaticMethod("com.oracle.truffle.polyglot.PolyglotEngineImpl", "resetFallbackEngine", Collections.emptyList());
        TruffleBaseFeature.preInitializeEngine();

        /* Ensure org.graalvm.polyglot.io.IOHelper.IMPL is initialized. */
        ((BeforeAnalysisAccessImpl) access).ensureInitialized("org.graalvm.polyglot.io.IOHelper");

        /* Support for deprecated bytecode osr frame transfer: GR-38296 */
        config.registerSubtypeReachabilityHandler((acc, klass) -> {
            DuringAnalysisAccessImpl impl = (DuringAnalysisAccessImpl) acc;
            /* Pass known reachable classes to the initializer: it will decide there what to do. */
            Boolean modified = TruffleBaseFeature.invokeStaticMethod(
                            "org.graalvm.compiler.truffle.runtime.BytecodeOSRRootNode",
                            "initializeClassUsingDeprecatedFrameTransfer",
                            Collections.singleton(Class.class),
                            klass);
            if (modified != null && modified) {
                if (!impl.concurrentReachabilityHandlers()) {
                    impl.requireAnalysisIteration();
                }
            }
        }, BytecodeOSRNode.class);
    }

    static class TruffleParsingInlineInvokePlugin implements InlineInvokePlugin {

        private final SVMHost hostVM;
        private final Replacements replacements;
        private final InvocationPlugins invocationPlugins;
        private final PartialEvaluator partialEvaluator;
        private final Predicate<ResolvedJavaMethod> allowRuntimeCompilationPredicate;

        TruffleParsingInlineInvokePlugin(SVMHost hostVM, Replacements replacements, InvocationPlugins invocationPlugins, PartialEvaluator partialEvaluator,
                        Predicate<ResolvedJavaMethod> allowRuntimeCompilationPredicate) {
            this.hostVM = hostVM;
            this.replacements = replacements;
            this.invocationPlugins = invocationPlugins;
            this.partialEvaluator = partialEvaluator;
            this.allowRuntimeCompilationPredicate = allowRuntimeCompilationPredicate;
        }

        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext builder, ResolvedJavaMethod original, ValueNode[] arguments) {
            if (original.hasNeverInlineDirective()) {
                return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            } else if (invocationPlugins.lookupInvocation(original, builder.getOptions()) != null) {
                return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            } else if (original.getAnnotation(ExplodeLoop.class) != null) {
                /*
                 * We cannot inline a method annotated with @ExplodeLoop, because then loops are no
                 * longer exploded.
                 */
                return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            } else if (builder.getMethod().getAnnotation(ExplodeLoop.class) != null) {
                /*
                 * We cannot inline anything into a method annotated with @ExplodeLoop, because then
                 * loops of the inlined callee are exploded too.
                 */
                return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            } else if (replacements.hasSubstitution(original, builder.getOptions())) {
                return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            }

            for (ResolvedJavaMethod m : partialEvaluator.getNeverInlineMethods()) {
                if (original.equals(m)) {
                    return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
                }
            }

            if (original.getCode() != null && allowRuntimeCompilationPredicate.test(original) && hostVM.isAnalysisTrivialMethod((AnalysisMethod) original) &&
                            builder.getDepth() < InlineDuringParsingMaxDepth.getValue(HostedOptionValues.singleton())) {
                return createStandardInlineInfo(original);
            }

            return null;
        }
    }

    private static void registerKnownTruffleFields(BeforeAnalysisAccessImpl config, KnownTruffleTypes knownTruffleFields) {
        for (Class<?> klass = knownTruffleFields.getClass(); klass != Object.class; klass = klass.getSuperclass()) {
            for (Field field : klass.getDeclaredFields()) {
                if (Modifier.isPublic(field.getModifiers())) {
                    try {
                        Object value = field.get(knownTruffleFields);
                        if (value instanceof ResolvedJavaField) {
                            config.registerAsAccessed((AnalysisField) value, "known truffle field");
                        }
                    } catch (IllegalAccessException ex) {
                        throw VMError.shouldNotReachHere(ex);
                    }
                }
            }
        }
    }

    private boolean allowRuntimeCompilation(ResolvedJavaMethod method) {
        if (runtimeCompilationForbidden(method)) {
            return false;
        } else if (isBlocklisted(method)) {
            return false;
        } else if (warnMethods.contains(method)) {
            warnViolations.add(method);
        }

        return true;
    }

    private static boolean runtimeCompilationForbidden(ResolvedJavaMethod method) {
        if (method.getAnnotation(CompilerDirectives.TruffleBoundary.class) != null) {
            return true;
        } else if (!((SubstrateTruffleRuntime) Truffle.getRuntime()).isInlineable(method)) {
            return true;
        } else if (method.getAnnotation(TruffleCallBoundary.class) != null) {
            return true;
        }
        return false;
    }

    boolean isBlocklisted(ResolvedJavaMethod method) {
        if (!((AnalysisMethod) method).allowRuntimeCompilation()) {
            return true;
        }

        return blocklistMethods.contains(method);
    }

    boolean isTargetBlocklisted(ResolvedJavaMethod target, ResolvedJavaMethod implementation) {
        boolean blocklisted = !((AnalysisMethod) target).allowRuntimeCompilation() || blocklistMethods.contains(target);

        if (blocklisted && implementation != target && implementationOnlyBlocklist.contains(target)) {
            blocklisted = isBlocklisted(implementation);
        }

        return blocklisted;
    }

    @SuppressWarnings("deprecation")
    private boolean deoptimizeOnException(ResolvedJavaMethod method) {
        if (method == null) {
            return false;
        }
        CompilerDirectives.TruffleBoundary truffleBoundary = method.getAnnotation(CompilerDirectives.TruffleBoundary.class);
        return truffleBoundary != null && truffleBoundary.transferToInterpreterOnException();
    }

    private void initializeMethodBlocklist(MetaAccessProvider metaAccess, FeatureAccess featureAccess) {
        blocklistMethod(metaAccess, Object.class, "equals", Object.class);
        blocklistMethod(metaAccess, Object.class, "hashCode");
        blocklistMethod(metaAccess, Object.class, "toString");
        blocklistMethod(metaAccess, String.class, "valueOf", Object.class);
        blocklistMethod(metaAccess, String.class, "getBytes");
        blocklistMethod(metaAccess, String.class, "indexOf", int.class);
        blocklistMethod(metaAccess, String.class, "indexOf", int.class, int.class);
        blocklistMethod(metaAccess, String.class, "indexOf", String.class);
        blocklistMethod(metaAccess, String.class, "indexOf", String.class, int.class);
        blocklistMethod(metaAccess, Throwable.class, "fillInStackTrace");
        // Implementations which don't call Throwable.fillInStackTrace are allowed
        implementationOnlyBlocklist(metaAccess, Throwable.class, "fillInStackTrace");
        blocklistMethod(metaAccess, Throwable.class, "initCause", Throwable.class);
        blocklistMethod(metaAccess, Throwable.class, "addSuppressed", Throwable.class);
        blocklistMethod(metaAccess, System.class, "getProperty", String.class);

        blocklistAllMethods(metaAccess, AssertionError.class);
        blocklistAllMethods(metaAccess, BigInteger.class);
        blocklistAllMethods(metaAccess, BigDecimal.class);
        blocklistAllMethods(metaAccess, Comparable.class);
        blocklistAllMethods(metaAccess, Comparator.class);
        blocklistAllMethods(metaAccess, Collection.class);
        blocklistAllMethods(metaAccess, List.class);
        blocklistAllMethods(metaAccess, Set.class);
        blocklistAllMethods(metaAccess, Map.class);
        blocklistAllMethods(metaAccess, Map.Entry.class);
        blocklistAllMethods(metaAccess, TreeMap.class);
        blocklistAllMethods(metaAccess, HashMap.class);
        blocklistAllMethods(metaAccess, ConcurrentHashMap.class);
        blocklistAllMethods(metaAccess, WeakHashMap.class);
        blocklistAllMethods(metaAccess, IdentityHashMap.class);
        blocklistAllMethods(metaAccess, Iterable.class);
        blocklistAllMethods(metaAccess, Iterator.class);
        blocklistAllMethods(metaAccess, ListIterator.class);
        blocklistAllMethods(metaAccess, ReentrantLock.class);

        removeFromBlocklist(metaAccess, BigInteger.class, "signum");
        removeFromBlocklist(metaAccess, ReentrantLock.class, "isLocked");
        removeFromBlocklist(metaAccess, ReentrantLock.class, "isHeldByCurrentThread");
        removeFromBlocklist(metaAccess, ReentrantLock.class, "getOwner");
        removeFromBlocklist(metaAccess, ReentrantLock.class, "<init>");

        /* Methods with synchronization are currently not supported as deoptimization targets. */
        blocklistAllMethods(metaAccess, StringBuffer.class);
        blocklistAllMethods(metaAccess, Vector.class);
        blocklistAllMethods(metaAccess, Hashtable.class);

        /* Block list generic functional interfaces. */
        blocklistAllMethods(metaAccess, BiConsumer.class);
        blocklistAllMethods(metaAccess, BiFunction.class);
        blocklistAllMethods(metaAccess, BinaryOperator.class);
        blocklistAllMethods(metaAccess, BiPredicate.class);
        blocklistAllMethods(metaAccess, BooleanSupplier.class);
        blocklistAllMethods(metaAccess, Consumer.class);
        blocklistAllMethods(metaAccess, DoubleBinaryOperator.class);
        blocklistAllMethods(metaAccess, DoubleConsumer.class);
        blocklistAllMethods(metaAccess, DoubleFunction.class);
        blocklistAllMethods(metaAccess, DoublePredicate.class);
        blocklistAllMethods(metaAccess, DoubleSupplier.class);
        blocklistAllMethods(metaAccess, DoubleToIntFunction.class);
        blocklistAllMethods(metaAccess, DoubleToLongFunction.class);
        blocklistAllMethods(metaAccess, DoubleUnaryOperator.class);
        blocklistAllMethods(metaAccess, Function.class);
        blocklistAllMethods(metaAccess, IntBinaryOperator.class);
        blocklistAllMethods(metaAccess, IntConsumer.class);
        blocklistAllMethods(metaAccess, IntFunction.class);
        blocklistAllMethods(metaAccess, IntPredicate.class);
        blocklistAllMethods(metaAccess, IntSupplier.class);
        blocklistAllMethods(metaAccess, IntToDoubleFunction.class);
        blocklistAllMethods(metaAccess, IntToLongFunction.class);
        blocklistAllMethods(metaAccess, IntUnaryOperator.class);
        blocklistAllMethods(metaAccess, LongBinaryOperator.class);
        blocklistAllMethods(metaAccess, LongConsumer.class);
        blocklistAllMethods(metaAccess, LongFunction.class);
        blocklistAllMethods(metaAccess, LongPredicate.class);
        blocklistAllMethods(metaAccess, LongSupplier.class);
        blocklistAllMethods(metaAccess, LongToDoubleFunction.class);
        blocklistAllMethods(metaAccess, LongToIntFunction.class);
        blocklistAllMethods(metaAccess, LongUnaryOperator.class);
        blocklistAllMethods(metaAccess, ObjDoubleConsumer.class);
        blocklistAllMethods(metaAccess, ObjIntConsumer.class);
        blocklistAllMethods(metaAccess, ObjLongConsumer.class);
        blocklistAllMethods(metaAccess, Predicate.class);
        blocklistAllMethods(metaAccess, Supplier.class);
        blocklistAllMethods(metaAccess, ToDoubleBiFunction.class);
        blocklistAllMethods(metaAccess, ToDoubleFunction.class);
        blocklistAllMethods(metaAccess, ToIntBiFunction.class);
        blocklistAllMethods(metaAccess, ToIntFunction.class);
        blocklistAllMethods(metaAccess, ToLongBiFunction.class);
        blocklistAllMethods(metaAccess, ToLongFunction.class);
        blocklistAllMethods(metaAccess, UnaryOperator.class);
        /* Block list string concatenation. */
        blocklistAllMethods(metaAccess, featureAccess.findClassByName("java.lang.StringConcatHelper"));

        /*
         * Core Substrate VM classes that very certainly should not be reachable for runtime
         * compilation. Warn when they get reachable to detect explosion of reachable methods.
         */
        warnAllMethods(metaAccess, JavaStackWalker.class);
        warnAllMethods(metaAccess, Deoptimizer.class);
        warnAllMethods(metaAccess, Heap.getHeap().getClass());

        /*
         * GR-41564 These methods should become part of the blocklist once the Truffle language
         * implementations are refactored.
         */
        tempTargetAllowlistMethod(metaAccess, Function.class, "apply", Object.class);
        tempTargetAllowlistMethod(metaAccess, Predicate.class, "test", Object.class);
        tempTargetAllowlistMethod(metaAccess, LongBinaryOperator.class, "applyAsLong", long.class, long.class);
        tempTargetAllowlistMethod(metaAccess, IntBinaryOperator.class, "applyAsInt", int.class, int.class);
        tempTargetAllowlistMethod(metaAccess, IntBinaryOperator.class, "applyAsInt", int.class, int.class);

        tempTargetAllowlistMethod(metaAccess, List.class, "size");
        tempTargetAllowlistMethod(metaAccess, List.class, "isEmpty");
        tempTargetAllowlistMethod(metaAccess, List.class, "clear");
        tempTargetAllowlistMethod(metaAccess, List.class, "get", int.class);
        tempTargetAllowlistMethod(metaAccess, List.class, "remove", int.class);
        tempTargetAllowlistMethod(metaAccess, List.class, "toArray", Object[].class);

        tempTargetAllowlistMethod(metaAccess, Iterator.class, "next");
        tempTargetAllowlistMethod(metaAccess, Iterator.class, "hasNext");

        tempTargetAllowlistMethod(metaAccess, Iterable.class, "iterator");

        tempTargetAllowlistMethod(metaAccess, Object.class, "equals", Object.class);
        tempTargetAllowlistMethod(metaAccess, Object.class, "hashCode");
    }

    private void blocklistAllMethods(MetaAccessProvider metaAccess, Class<?> clazz) {
        for (Executable m : clazz.getDeclaredMethods()) {
            blocklistMethods.add(metaAccess.lookupJavaMethod(m));
        }
        for (Executable m : clazz.getDeclaredConstructors()) {
            blocklistMethods.add(metaAccess.lookupJavaMethod(m));
        }
    }

    private void blocklistMethod(MetaAccessProvider metaAccess, Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            blocklistMethods.add(metaAccess.lookupJavaMethod(clazz.getDeclaredMethod(name, parameterTypes)));
        } catch (NoSuchMethodException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private void tempTargetAllowlistMethod(MetaAccessProvider metaAccess, Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            tempTargetAllowlistMethods.add(metaAccess.lookupJavaMethod(clazz.getDeclaredMethod(name, parameterTypes)));
        } catch (NoSuchMethodException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    /**
     * Removes a previously blocklisted method from the blocklist.
     */
    private void removeFromBlocklist(MetaAccessProvider metaAccess, Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            Executable method;
            if ("<init>".equals(name)) {
                method = clazz.getDeclaredConstructor(parameterTypes);
            } else {
                method = clazz.getDeclaredMethod(name, parameterTypes);
            }
            if (!blocklistMethods.remove(metaAccess.lookupJavaMethod(method))) {
                throw VMError.shouldNotReachHere();
            }
        } catch (NoSuchMethodException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    /**
     * Methods on this list are allowed to be runtime compiled as long as the method being runtime
     * compiled (i.e., the implementation method & not the target) is not on blocklist.
     */
    private void implementationOnlyBlocklist(MetaAccessProvider metaAccess, Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            Executable method;
            if ("<init>".equals(name)) {
                method = clazz.getDeclaredConstructor(parameterTypes);
            } else {
                method = clazz.getDeclaredMethod(name, parameterTypes);
            }
            implementationOnlyBlocklist.add(metaAccess.lookupJavaMethod(method));
        } catch (NoSuchMethodException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private void warnAllMethods(MetaAccessProvider metaAccess, Class<?> clazz) {
        for (Executable m : clazz.getDeclaredMethods()) {
            /*
             * Filter out methods that are, e.g., only present on a certain platform. We do not need
             * all methods in the warning list, just enough to trigger the warnings. Accessors are
             * generally allowed too.
             */
            if (AnnotationAccess.getAnnotationTypes(m).length == 0 && !m.getName().startsWith("get") && !m.getName().startsWith("set")) {
                warnMethods.add(metaAccess.lookupJavaMethod(m));
            }
        }
        for (Executable m : clazz.getDeclaredConstructors()) {
            if (AnnotationAccess.getAnnotationTypes(m).length == 0) {
                warnMethods.add(metaAccess.lookupJavaMethod(m));
            }
        }
    }

    private static int blocklistViolationComparator(AbstractCallTreeNode n1, AbstractCallTreeNode n2) {
        int result = n1.getImplementationMethod().getQualifiedName().compareTo(n2.getImplementationMethod().getQualifiedName());
        if (result == 0) {
            result = n1.getTargetMethod().getQualifiedName().compareTo(n2.getTargetMethod().getQualifiedName());
        }
        if (result == 0) {
            result = n1.getSourceReference().compareTo(n2.getSourceReference());
        }
        if (result == 0) {
            if (n1.getParent() == null || n2.getParent() == null) {
                return Boolean.compare(n1.getParent() == null, n2.getParent() == null);
            }
            result = blocklistViolationComparator(n1.getParent(), n2.getParent());
        }
        return result;
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess config) {
        FeatureImpl.BeforeCompilationAccessImpl access = (FeatureImpl.BeforeCompilationAccessImpl) config;

        boolean failBlockListViolations = Options.TruffleCheckBlockListMethods.getValue() || Options.TruffleCheckBlackListedMethods.getValue();
        boolean printBlockListViolations = RuntimeCompilationFeature.Options.PrintRuntimeCompileMethods.getValue() || failBlockListViolations;
        if (printBlockListViolations) {
            Set<AbstractCallTreeNode> blocklistViolations = new TreeSet<>(TruffleFeature::blocklistViolationComparator);
            for (AbstractCallTreeNode node : RuntimeCompilationFeature.singleton().getAllRuntimeCompilationCandidates()) {

                // Determine blocklist violations
                if (!runtimeCompilationForbidden(node.getImplementationMethod())) {
                    if (isBlocklisted(node.getImplementationMethod()) || isTargetBlocklisted(node.getTargetMethod(), node.getImplementationMethod())) {
                        boolean tempAllow = !node.getTargetMethod().equals(node.getImplementationMethod()) &&
                                        tempTargetAllowlistMethods.contains(node.getTargetMethod()) &&
                                        !isBlocklisted(node.getImplementationMethod());
                        if (!tempAllow) {
                            blocklistViolations.add(node);
                        }
                    }
                }
            }
            if (!blocklistViolations.isEmpty()) {
                System.out.println();
                System.out.println("=== Found " + blocklistViolations.size() + " compilation blocklist violations ===");
                System.out.println();
                for (AbstractCallTreeNode node : blocklistViolations) {
                    System.out.println("Blocklisted method");
                    System.out.format("   %s (target: %s)\n", node.getImplementationMethod().format("%H.%n(%p)"), node.getTargetMethod().format("%H.%n(%p)"));
                    System.out.println("called from");
                    for (AbstractCallTreeNode cur = node; cur != null; cur = cur.getParent()) {
                        System.out.println("  " + cur.getSourceReference());
                    }
                }
                if (failBlockListViolations) {
                    throw VMError.shouldNotReachHere("Blocklisted methods are reachable for runtime compilation");
                }
            }
        }

        if (warnViolations.size() > 0) {
            /*
             * It is enough to print one warning message with one stack trace. Take the shortest
             * stack trace.
             */
            System.out.println("Warning: suspicious methods reachable for runtime compilation.");
            System.out.println("Check the complete tree of reachable methods using the option " + RuntimeCompilationFeature.Options.PrintRuntimeCompileMethods.getDescriptor().getFieldName());
            for (ResolvedJavaMethod violation : warnViolations) {
                System.out.println("Suspicious method: " + violation.format("%H.%n(%p)"));
                AbstractCallTreeNode node = RuntimeCompilationFeature.singleton().getCallTreeNode(violation);
                for (AbstractCallTreeNode cur = node; cur != null; cur = cur.getParent()) {
                    System.out.println("  " + cur.getSourceReference());
                }
            }
        }

        if (neverPartOfCompilationViolations.size() > 0) {
            System.out.println("Error: CompilerAsserts.neverPartOfCompilation reachable for runtime compilation from " + neverPartOfCompilationViolations.size() + " places:");
            for (Pair<ResolvedJavaMethod, String> violation : neverPartOfCompilationViolations) {
                System.out.println("called from");
                System.out.println("(inlined call path): " + violation.getRight());
                AbstractCallTreeNode node = RuntimeCompilationFeature.singleton().getCallTreeNode(violation.getLeft());
                for (AbstractCallTreeNode cur = node; cur != null; cur = cur.getParent()) {
                    System.out.println("  " + cur.getSourceReference());
                }
            }
            throw VMError.shouldNotReachHere("CompilerAsserts.neverPartOfCompilation reachable for runtime compilation");
        }

        if (Options.TruffleCheckFrameImplementation.getValue()) {
            /*
             * Check that only one Frame implementation is seen as instantiated by the static
             * analysis. That allows de-virtualization of all calls to Frame methods in the
             * interpreter.
             *
             * The DefaultTruffleRuntime uses multiple Frame implementations (DefaultVirtualFrame,
             * DefaultMaterializedFrame, ReadOnlyFrame) to detect wrong usages of the Frame API, so
             * we can only check when running with compilation enabled.
             */
            Optional<? extends ResolvedJavaType> optionalFrameType = access.getMetaAccess().optionalLookupJavaType(Frame.class);
            if (optionalFrameType.isPresent()) {
                HostedType frameType = (HostedType) optionalFrameType.get();
                Set<HostedType> implementations = new HashSet<>();
                collectImplementations(frameType, implementations);

                if (implementations.size() > 1) {
                    throw UserError.abort("More than one implementation of %s found. For performance reasons, Truffle languages must not provide new implementations, " +
                                    "and instead only use the single implementation provided by the Truffle runtime. To disable this check, add %s to the native-image command line. Found classes: %s",
                                    Frame.class.getTypeName(), SubstrateOptionsParser.commandArgument(Options.TruffleCheckFrameImplementation, "-"),
                                    implementations.stream().map(m -> m.toJavaName(true)).collect(Collectors.joining(", ")));
                } else {
                    assert implementations.size() == 0 || implementations.iterator().next() == frameType.getSingleImplementor();
                }
            }
        }
    }

    /**
     * Keep this method in sync with
     * {@link SubstrateTruffleHostInliningPhase#isTruffleBoundary(ResolvedJavaMethod)}.
     */
    private boolean neverInlineTrivial(AnalysisMethod caller, AnalysisMethod callee) {
        if (runtimeCompiledMethods != null && !runtimeCompiledMethods.contains(caller)) {
            /*
             * Make sure we do not make any decisions for non-runtime compiled methods.
             */
            return false;
        } else if (TruffleHostInliningPhase.shouldDenyTrivialInlining(callee)) {
            return true;
        }
        return false;
    }

    private static void collectImplementations(HostedType type, Set<HostedType> implementations) {
        for (HostedType subType : type.getSubTypes()) {
            if (!subType.isAbstract()) {
                implementations.add(subType);
            }
            collectImplementations(subType, implementations);
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        if (Options.PrintStaticTruffleBoundaries.getValue()) {
            printStaticTruffleBoundaries();
        }

        SubstrateTruffleRuntime truffleRuntime = (SubstrateTruffleRuntime) Truffle.getRuntime();
        AfterAnalysisAccessImpl config = (AfterAnalysisAccessImpl) access;
        truffleRuntime.initializeHostedKnownMethods(config.getMetaAccess());

        runtimeCompiledMethods = new LinkedHashSet<>();
        runtimeCompiledMethods.addAll(Arrays.asList(config.getMetaAccess().lookupJavaType(CompilerDirectives.class).getDeclaredMethods()));
        runtimeCompiledMethods.addAll(Arrays.asList(config.getMetaAccess().lookupJavaType(CompilerAsserts.class).getDeclaredMethods()));

        for (AbstractCallTreeNode runtimeCompiledMethod : RuntimeCompilationFeature.singleton().getRuntimeCompiledMethods().values()) {

            runtimeCompiledMethods.add(runtimeCompiledMethod.getImplementationMethod());

            /*
             * The list of runtime compiled methods is not containing all methods that are always
             * inlined for runtime compilation. See for example {@link
             * TruffleParsingInlineInvokePlugin}, which always inlines certain methods to improve
             * footprint. Luckily the Graal graph keeps track of all methods ever inlined in a
             * graph. So we just need to remember them. The set of runtime compiled methods is later
             * used for driving the entry points of the TruffleHostInliningPhase.
             */
            StructuredGraph graph = runtimeCompiledMethod.getGraph();
            if (graph != null) {
                for (ResolvedJavaMethod method : graph.getMethods()) {
                    if (!(method instanceof AnalysisMethod)) {
                        throw VMError.shouldNotReachHere("method should be an analysis method");
                    }
                    if (method.getAnnotation(TruffleBoundary.class) != null) {
                        throw VMError.shouldNotReachHere("method used during runtime compilation must never be annotated with a truffle boundary");
                    }
                    runtimeCompiledMethods.add((AnalysisMethod) method);
                }
            }
        }
    }

    private static void printStaticTruffleBoundaries() {
        HashSet<ResolvedJavaMethod> foundBoundaries = new HashSet<>();
        int callSiteCount = 0;
        int calleeCount = 0;
        for (AbstractCallTreeNode node : RuntimeCompilationFeature.singleton().getRuntimeCompiledMethods().values()) {
            StructuredGraph graph = node.getGraph();
            for (MethodCallTargetNode callTarget : graph.getNodes(MethodCallTargetNode.TYPE)) {
                ResolvedJavaMethod targetMethod = callTarget.targetMethod();
                TruffleBoundary truffleBoundary = targetMethod.getAnnotation(TruffleBoundary.class);
                if (truffleBoundary != null) {
                    ++callSiteCount;
                    if (foundBoundaries.contains(targetMethod)) {
                        // nothing to do
                    } else {
                        foundBoundaries.add(targetMethod);
                        System.out.println("Truffle boundary found: " + targetMethod);
                        calleeCount++;
                    }
                }
            }
        }
        System.out.printf("Number of Truffle call boundaries: %d, number of unique called methods outside the boundary: %d%n", callSiteCount, calleeCount);
    }

    @Override
    public void registerGraalPhases(Providers providers, SnippetReflectionProvider snippetReflection, Suites suites, boolean hosted) {
        if (hosted && TruffleHostInliningPhase.Options.TruffleHostInlining.getValue(HostedOptionValues.singleton()) && suites.getHighTier() instanceof HighTier) {
            suites.getHighTier().prependPhase(new SubstrateTruffleHostInliningPhase(CanonicalizerPhase.create()));
        }
    }
}

@TargetClass(className = "org.graalvm.compiler.truffle.runtime.OptimizedCallTarget", onlyWith = TruffleFeature.IsEnabled.class)
final class Target_org_graalvm_compiler_truffle_runtime_OptimizedCallTarget {

    /*
     * Retry compilation when they failed during image generation.
     */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    boolean compilationFailed;
    /*
     * The initialized time stamp is not useful when collected during image generation.
     */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    long initializedTimestamp;
}
