/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.sboutlining;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.StringConcatException;
import java.lang.invoke.StringConcatFactory;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.sboutlining.concat.SubstrateSBConcatHelper;
import com.oracle.svm.core.sboutlining.concat.SubstrateStringConcatHelper;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.core.util.CounterFeature;
import com.oracle.svm.hosted.sboutlining.concat.SubstrateSBConcatFactory;
import com.oracle.svm.hosted.sboutlining.concat.SubstrateSBConcatGraphBuilder;
import com.oracle.svm.hosted.sboutlining.concat.SubstrateStringConcatFactory;
import com.oracle.svm.hosted.sboutlining.concat.SubstrateStringConcatGraphBuilder;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.code.CompileQueue;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.phases.AnalysisGraphBuilderPhase;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.util.ReflectionUtil;

import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.GraphKit;
import jdk.vm.ci.meta.ConstantPool.BootstrapMethodInvocation;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Registers hosted support for sharing code that aggregates values into a {@link String},
 * {@link StringBuilder}, or {@link StringBuffer}.
 *
 * <p>
 * The feature enables two related optimizations:
 *
 * <ul>
 * <li>{@link SBOutliningPhase} replaces supported builder and buffer allocation/append chains with
 * delayed materializations.</li>
 * <li>{@link OutlineIndyStringConcatPlugin} replaces string-concatenation invokedynamic calls with
 * static calls to shared synthetic methods.</li>
 * </ul>
 *
 * <h2>Compilation scope</h2>
 *
 * Both transformations run during image generation. {@link SBOutliningPhase} processes only
 * original-method graphs, which are compiled ahead of time. The phase replaces allocations and
 * append operations, redirects their uses, and changes values referenced by frame states. These
 * changes do not preserve the bytecode-level frame-state mapping needed to resume execution in a
 * deoptimization target. Runtime-compiled methods must support deoptimization, so the phase does
 * not process runtime-compiled method variants.
 *
 * <p>
 * {@link OutlineIndyStringConcatPlugin} also runs while Native Image prepares graphs for runtime
 * compilation. These graphs are transformed during image generation and encoded in the image for
 * later compilation at run time.
 *
 * <p>
 * The shared {@link OutlinedSBMethod} implementations are also ahead-of-time only. They are
 * synthetic, non-bytecode methods whose graphs are assembled with {@link HostedGraphKit} and the
 * build-time concat factories. Native Image therefore creates and compiles these methods during
 * image generation instead of creating runtime-compiled variants. Both ahead-of-time and
 * runtime-compiled callers can call the resulting methods directly.
 *
 * <h2>Why builder and buffer chains are outlined</h2>
 *
 * User and JDK code commonly contains append chains such as:
 *
 * <pre>{@code
 * StringBuilder builder = new StringBuilder();
 * builder.append("value: ");
 * builder.append(value);
 * return builder.toString();
 * }</pre>
 *
 * javac also emitted this pattern for string concatenation in JDK 8 and earlier. Such chains have
 * two important costs in a native image:
 *
 * <ul>
 * <li>Each constructor, append, and {@code toString} remains an explicit call.</li>
 * <li>The builder or buffer stores intermediate states in a backing byte array. Growth can allocate
 * larger arrays and copy the accumulated content several times.</li>
 * </ul>
 *
 * <p>
 * Outlining keeps supported operations virtual and delays object creation until the state becomes
 * observable. The ideal result is one final {@link String} allocation. If code needs the builder or
 * buffer itself, materializing a partial append state still avoids the earlier intermediate arrays.
 * The transformation must preserve constructor checks, exception behavior, aliasing, and the
 * observable capacity of a materialized builder or buffer.
 *
 * <p>
 * Builder and buffer materialization is disabled when VM continuations are supported. Its outlined
 * method passes the current length and capacity between helper calls in a stack-allocated structure.
 * A virtual thread can resume at a different stack address, but continuation restoration does not
 * update pointers to this structure. String materialization does not use this structure and remains
 * enabled. This limitation is tracked by GR-49250.
 *
 * <h2>Finding and applying materializations</h2>
 *
 * {@link SBOutliningAnalysis} performs the planning in three stages. It first groups aliases by
 * their original allocation and records escaping uses. It then follows control flow to track each
 * virtual append state and select the latest point where the state must be materialized. Finally,
 * it raises compatible materializations to dominating blocks when doing so reduces duplicate calls
 * without creating an object on a path that does not need one.
 *
 * <p>
 * {@link SBOutliningPhase} applies the completed plan. It inserts required constructor validation
 * and operand stringification, adds calls to the outlined materialization methods, reconstructs
 * value phis, proxies, and exception paths, redirects uses, and removes the replaced builder or
 * buffer operations.
 *
 * <h2>Shared outlined methods</h2>
 *
 * {@link OutlinedSBMethodSupport} groups call sites by their result type and their ordered list of
 * parameter types. It creates one synthetic {@link OutlinedSBMethod} for each group. The argument
 * values are not part of the group and are passed to the shared method at run time. For example,
 * call sites with the type {@code String(Object, int)} share one method even when they concatenate
 * different text and integer values. A call site with the type {@code String(Object, long)} uses a
 * different method. The generated method performs these steps:
 *
 * <ol>
 * <li>Validate arguments from constructors whose checks were delayed.</li>
 * <li>Convert operands to their string representation. This is called stringification.</li>
 * <li>Compute the total character length and compact-string coder. Builder and buffer results also
 * compute the observable capacity.</li>
 * <li>Allocate one backing byte array with the required size.</li>
 * <li>Copy each stringified value into the array and create the final string, builder, or buffer.</li>
 * </ol>
 *
 * <p>
 * String results use {@link SubstrateStringConcatFactory},
 * {@link SubstrateStringConcatGraphBuilder}, and {@link SubstrateStringConcatHelper}. Builder and
 * buffer results use {@link SubstrateSBConcatFactory}, {@link SubstrateSBConcatGraphBuilder}, and
 * {@link SubstrateSBConcatHelper}.
 *
 * <p>
 * Each factory assembles the steps with a combinator structure derived from the JDK method-handle
 * inline-copy strategy. Its graph builder translates the combinators into {@link GraphKit}
 * operations during image generation. The method handles build a graph and do not dispatch at run
 * time. The helpers contain the operations that execute in the native image.
 *
 * <p>
 * {@link SubstrateStringConcatHelper} follows the packed length-and-coder API from JDK 25's internal
 * {@code StringConcatHelper}. A separate helper is required because the JDK class and its string
 * storage operations are not accessible outside {@code java.lang}. Substitution aliases provide
 * the required access. {@link SubstrateSBConcatHelper} additionally tracks the requested and grown
 * character capacity. This is necessary because capacity is observable and because inflation from
 * Latin-1 to UTF-16 changes the backing array size.
 *
 * <h2>Invokedynamic concatenation</h2>
 *
 * Modern javac versions normally compile string concatenation to an invokedynamic instruction whose
 * bootstrap method is {@link StringConcatFactory#makeConcatWithConstants}. During analysis parsing,
 * {@link OutlineIndyStringConcatPlugin} parses and validates the concatenation template, called the
 * recipe. The recipe describes how literal text, dynamic arguments, and constants are combined.
 * The plugin represents the constants as regular graph arguments and erases compatible reference
 * types so more call sites can share an outlined method. It then replaces the invokedynamic
 * instruction with a static call to that method. Calls that exceed the reserved JVM argument-slot
 * limit keep their original invokedynamic implementation.
 */
@AutomaticallyRegisteredFeature
@Platforms(Platform.HOSTED_ONLY.class)
public class SBOutliningFeature implements InternalFeature {

    public static final class Options {
        @Option(help = "Attempt to outline sequences of StringBuilder.append() operations.")//
        public static final HostedOptionKey<Boolean> OutlineStringBuilderAppends = new HostedOptionKey<>(true);

        @Option(help = "Attempt to outline sequences of StringBuffer.append() operations.")//
        public static final HostedOptionKey<Boolean> OutlineStringBufferAppends = new HostedOptionKey<>(true);

        @Option(help = "Outline string concatenation operations represented by invoke dynamic (indy) calls.")//
        public static final HostedOptionKey<Boolean> OutlineIndyStringConcatenations = new HostedOptionKey<>(true);

        @Option(help = "Allow outlined sequences of (StringBuilder|StringBuffer).append() to be materialized into a (StringBuilder|StringBuilder) instance.")//
        public static final HostedOptionKey<Boolean> OutlineSBMaterializations = new HostedOptionKey<>(true);

        @Option(help = "Print counters collected while attempting to outline sequences of StringBuffer and StringBuilder operations.")//
        public static final HostedOptionKey<Boolean> PrintSBOutliningCounters = new HostedOptionKey<>(false);

        @Option(help = "Print histogram and metrics about the OutlinedSBMethods created.")//
        public static final HostedOptionKey<Boolean> PrintOutlinedSBMethodMetrics = new HostedOptionKey<>(false);
    }

    public static boolean outlineSBSequences() {
        return Options.OutlineStringBuilderAppends.getValue() || Options.OutlineStringBufferAppends.getValue();
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return outlineSBSequences() || Options.OutlineIndyStringConcatenations.getValue();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Collections.singletonList(CounterFeature.class);
    }

    @Override
    public void registerGraphBuilderPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        if (Options.OutlineIndyStringConcatenations.getValue()) {
            plugins.appendNodePlugin(new OutlineIndyStringConcatPlugin());
        }
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(OutlinedSBMethodSupport.class, new OutlinedSBMethodSupport());
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        if (Options.PrintSBOutliningCounters.getValue()) {
            OutlinedSBMethodSupport.singleton().printCounters();
        }
        if (Options.PrintOutlinedSBMethodMetrics.getValue()) {
            OutlinedSBMethodSupport.singleton().printMethodMetrics();
        }
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        if (Options.PrintOutlinedSBMethodMetrics.getValue()) {
            var compilations = ((FeatureImpl.AfterCompilationAccessImpl) access).getCompilations();
            long outlinedSBMethodCount = 0;
            long outlinedSBMethodCodeSize = 0;
            long packageMethodCount = 0;
            long packageCodeSize = 0;
            for (Map.Entry<HostedMethod, CompileQueue.CompileTask> entry : compilations.entrySet()) {
                ResolvedJavaMethod method = entry.getKey().getWrapped().getWrapped();
                int codeSize = entry.getValue().result.getTargetCodeSize();

                if (method instanceof OutlinedSBMethod) {
                    outlinedSBMethodCount++;
                    outlinedSBMethodCodeSize += codeSize;
                }

                String packageName = method.format("%H");
                if (packageName != null && packageName.contains("com.oracle.svm.core.sboutlining")) {
                    packageMethodCount++;
                    packageCodeSize += codeSize;
                }
            }
            System.out.println("Number of outlinedSBMethods: " + outlinedSBMethodCount);
            System.out.println("OutlinedSBMethods' code size: " + outlinedSBMethodCodeSize);
            System.out.println("Number of SB package methods: " + packageMethodCount);
            System.out.println("SB package methods' code size: " + packageCodeSize);
        }
    }
}

/**
 * Graph-builder plugin that converts string-concatenation invokedynamic instructions into calls to
 * {@link OutlinedSBMethod} implementations.
 *
 * <p>
 * The plugin runs only during analysis parsing and recognizes
 * {@link StringConcatFactory#makeConcatWithConstants}. It validates and expands the concat recipe,
 * turns recipe constants into ordinary {@link ValueNode} arguments, and erases reference parameter
 * types where possible so more call sites share the same outlined method. If the expanded call
 * would exceed the reserved JVM argument-slot limit, the plugin leaves the invokedynamic
 * instruction unchanged.
 */
final class OutlineIndyStringConcatPlugin implements NodePlugin {

    /**
     * The max number of slots used within a call to the factory will be 199 (via
     * MAX_INDY_CONCAT_ARG_SLOTS within StringConcatFactory). However, since we pull constants out
     * from the recipe and instead pass them as arguments, it is possible for us to exceed the limit
     * of the number of arguments which can be passed to a JVM method.
     *
     * We could limit the number of slots stringifies use to 252 since we use 3 additional slots
     * (byte[], long) within our String factory method generator. However, as an additional
     * safeguard against future implementation changes, we limit the number of slots allowed to 240.
     *
     * Currently, javac limits the total number of elements (constants & args) to 199 as well, but
     * it is better to safeguard against future javac changes and other arbitrary class files.
     */
    private static final int MAX_STRINGIFY_SLOTS = 240;

    static final Method factoryMethod;

    static {
        factoryMethod = ReflectionUtil.lookupMethod(StringConcatFactory.class, "makeConcatWithConstants", MethodHandles.Lookup.class, String.class, MethodType.class, String.class, Object[].class);
    }

    private final OutlinedSBMethodSupport outlinedSBMethodSupport;
    private ResolvedJavaMethod resolvedFactoryMethod = null;

    OutlineIndyStringConcatPlugin() {
        outlinedSBMethodSupport = OutlinedSBMethodSupport.singleton();
    }

    private ResolvedJavaMethod getResolvedFactoryMethod(GraphBuilderContext b) {
        if (resolvedFactoryMethod == null) {
            resolvedFactoryMethod = b.getMetaAccess().lookupJavaMethod(factoryMethod);
        }
        return resolvedFactoryMethod;
    }

    /**
     * Converts string concatenation indy calls to a calls to our custom logic for string
     * concatenation.
     */
    @Override
    public Pair<ResolvedJavaMethod, ValueNode[]> convertInvokeDynamic(GraphBuilderContext b, BootstrapMethodInvocation m) {
        if (b instanceof AnalysisGraphBuilderPhase.AnalysisBytecodeParser) {
            if (getResolvedFactoryMethod(b).equals(m.getMethod())) {
                List<JavaConstant> staticArgs = m.getStaticArguments();
                MethodType originalType = b.getSnippetReflection().asObject(MethodType.class, m.getType());
                // erasing types to reduce the number of outlined method signature types.
                MethodType erasedType = originalType.erase();
                MethodType concatType = MethodType.methodType(originalType.returnType(), erasedType.parameterArray());

                String recipe = b.getSnippetReflection().asObject(String.class, staticArgs.getFirst());
                assert recipe != null;

                Object[] constants = new Object[staticArgs.size() - 1];
                for (int i = 0; i < constants.length; i++) {
                    constants[i] = b.getSnippetReflection().asObject(Object.class, staticArgs.get(i + 1));
                }

                /* Extract all elements from recipe, converting constants to arguments. */
                List<String> elements;
                try {
                    elements = SubstrateStringConcatFactory.parseRecipe(originalType, recipe, constants);
                } catch (StringConcatException e) {
                    throw b.bailout(String.format("String concat recipe does not match args passed to StringConcatFactory.makeConcatWithConstants: %s", e.getMessage()));
                }

                // check number of slots used by the parameters.
                if (calculateSlotsUsed(originalType, elements) > MAX_STRINGIFY_SLOTS) {
                    /*
                     * We have to bail out due to too many parameters. We cannot handle this case.
                     */
                    return null;
                }

                ValueNode[] arguments = b.popArguments(concatType.parameterCount());

                // erasing to recipe constants to allow more sharing of outlined methods.
                Pair<MethodType, ValueNode[]> convertedParams = convertCallFormat(b, concatType, arguments, elements);

                ResolvedJavaMethod target = outlinedSBMethodSupport.lookup((AnalysisMetaAccess) b.getMetaAccess(), convertedParams.getLeft());
                outlinedSBMethodSupport.indysOutlined.inc();
                OutlinedSBMethodSupport.registerOutliningUse(b.getMethod(), OutlinedSBMethodSupport.UseKind.Indy);
                return Pair.create(target, convertedParams.getRight());
            }
        }
        return null;
    }

    /*
     * Calculate the number of slots used for passing arguments.
     */
    private static int calculateSlotsUsed(MethodType originalType, List<String> elements) {
        int slotCount = 0;
        int origIdx = 0;
        for (String element : elements) {
            if (element == null) {
                JavaKind kind = JavaKind.fromJavaClass(originalType.parameterType(origIdx));
                slotCount += kind.getSlotCount();
                origIdx++;
            } else {
                // is a String constant - takes 1 slot
                slotCount++;
            }
        }
        return slotCount;
    }

    /**
     * Converts the call to pass constant values as arguments to the string concatenation function.
     * In the original version, these constants could be within the recipe or a constant arg passed
     * to the indy call.
     */
    private static Pair<MethodType, ValueNode[]> convertCallFormat(GraphBuilderContext b, MethodType originalType, ValueNode[] originalArgs, List<String> elements) {

        // determine new type and pop original args from stack
        int origIdx = 0;
        ValueNode[] newArgs = new ValueNode[elements.size()];
        Class<?>[] newParams = new Class<?>[elements.size()];
        for (int i = 0; i < newArgs.length; i++) {
            String element = elements.get(i);
            if (element == null) {
                JavaKind kind = JavaKind.fromJavaClass(originalType.parameterType(origIdx));
                newParams[i] = kind.isObject() ? Object.class : kind.toJavaClass();
                newArgs[i] = originalArgs[origIdx];

                origIdx++;
            } else {
                // is a String constant
                newParams[i] = Object.class;
                JavaConstant constant = b.getConstantReflection().forString(element);
                newArgs[i] = b.add(ConstantNode.forConstant(constant, b.getMetaAccess()));
            }
        }
        return Pair.create(MethodType.methodType(originalType.returnType(), newParams), newArgs);
    }

}
