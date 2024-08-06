/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.arraycopy;

import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.DEOPT_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.FAST_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.FREQUENT_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;
import static jdk.vm.ci.services.Services.IS_BUILDING_NATIVE_IMAGE;

import java.util.EnumMap;
import java.util.function.Supplier;

import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.api.replacements.Fold.InjectedParameter;
import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.api.replacements.Snippet.NonNullParameter;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState.GuardsStage;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.SnippetAnchorNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnreachableNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.GuardedUnsafeLoadNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.ReplacementsUtil;
import jdk.graal.compiler.replacements.SnippetCounter;
import jdk.graal.compiler.replacements.SnippetIntegerHistogram;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.Snippets;
import jdk.graal.compiler.replacements.nodes.BasicArrayCopyNode;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Snippets for lowering {@link System#arraycopy}.
 *
 * <ul>
 * <li>{@link #arraycopyNativeExceptionSnippet}: this snippet is used when the array copy is known
 * to throw an exception, either because at least one of the objects is not an array, or one is an
 * object array and the other one a primitive array.</li>
 * <li>{@link #arraycopyExactStubCallSnippet}: this snippet is used for array copies that do not
 * require a store check. This is the case if the array copy is either
 * {@linkplain BasicArrayCopyNode#isExact() exact}, i.e., we can prove that the source array type is
 * assignable to the destination array type, or if one of the objects is a primitive array (and the
 * other is unknown). In the latter case, it is sufficient to dynamically check that the array types
 * are the same. No store check is needed.</li>
 * <li>{@link #exactArraycopyWithExpandedLoopSnippet}: if we can do an
 * {@linkplain #arraycopyExactStubCallSnippet array copy without store check}, it might be better to
 * inline the copy loop to allow further compiler phases to optimize the code, instead of doing a
 * stub call. This is only done if {@code mayExpandThisArraycopy} is {@code true}. Lowering is
 * delayed until {@linkplain GuardsStage#FIXED_DEOPTS deopts are fixed} using
 * {@link #delayedExactArraycopyWithExpandedLoopSnippet}.</li>
 * <li>{@link #checkcastArraycopySnippet}: this snippet is used if at least one of the objects is an
 * object array, but the compatibility of source and destination cannot be proven statically. We
 * need to perform a store check on every element. Lowering is delayed {@link GuardsStage#AFTER_FSA
 * after framestate assignment} via {@link #delayedCheckcastArraycopySnippet})</li>
 * <li>{@link #genericArraycopySnippet}: this snippet is used if we have no information about the
 * types of the objects. The snippet needs to perform all required type and store checks. Lowering
 * is delayed {@link GuardsStage#AFTER_FSA after framestate assignment} via
 * {@link #delayedGenericArraycopySnippet}.</li>
 * </ul>
 *
 * See {@link Templates#lower(ArrayCopyNode, boolean, LoweringTool)} for the implementation details
 * of the lowering strategies.
 *
 * @see Templates#lower(ArrayCopyNode, boolean, LoweringTool)
 */
public abstract class ArrayCopySnippets implements Snippets {

    public static void registerSystemArraycopyPlugin(InvocationPlugins.Registration r) {
        registerSystemArraycopyPlugin(r, false);
    }

    public static void registerSystemArraycopyPlugin(InvocationPlugins.Registration r, boolean forceAnyLocation) {
        r.register(new InvocationPlugin("arraycopy", Object.class, int.class, Object.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode src, ValueNode srcPos, ValueNode dst, ValueNode dstPos, ValueNode length) {
                ValueNode nonNullSrc = b.nullCheckedValue(src);
                ValueNode nonNullDst = b.nullCheckedValue(dst);
                b.add(new ArrayCopyNode(b.bci(), nonNullSrc, srcPos, nonNullDst, dstPos, length, forceAnyLocation));
                return true;
            }
        });
    }

    protected enum ArrayCopyTypeCheck {
        UNDEFINED_ARRAY_TYPE_CHECK,
        // either we know that both objects are arrays and have the same type,
        // or we apply generic array copy snippet, which enforces type check
        NO_ARRAY_TYPE_CHECK,
        // can be used when we know that one of the objects is a primitive array
        HUB_BASED_ARRAY_TYPE_CHECK,
        // can be used when we know that one of the objects is an object array
        LAYOUT_HELPER_BASED_ARRAY_TYPE_CHECK
    }

    /** Marker value for the {@link InjectedParameter} injected parameter. */
    protected static final MetaAccessProvider INJECTED_META_ACCESS = null;

    /**
     * Checks whether the hubs for the given objects are equal. The objects must be non-null.
     */
    public abstract boolean hubsEqual(Object nonNullSrc, Object nonNullDest);

    /**
     * Checks whether the layout helpers for the given objects are equal. The objects must be
     * non-null.
     */
    public abstract boolean layoutHelpersEqual(Object nonNullSrc, Object nonNullDest);

    protected abstract int heapWordSize();

    /**
     * Returns {@code true} if the original {@link System#arraycopy} method can be called to
     * implement exceptional or fallback paths.
     */
    protected boolean useOriginalArraycopy() {
        return true;
    }

    /**
     * Snippet that performs an {@linkplain BasicArrayCopyNode#isExact() exact} array copy. Used
     * when the array copy might be
     * {@linkplain Templates#lower(ArrayCopyNode, boolean, LoweringTool) expanded}. Lowering is
     * delayed using an {@link ArrayCopyWithDelayedLoweringNode} which will dispatch to
     * {@link #exactArraycopyWithExpandedLoopSnippet}.
     *
     * @see Templates#lower(ArrayCopyNode, boolean, LoweringTool)
     * @see #exactArraycopyWithExpandedLoopSnippet
     * @see #doExactArraycopyWithExpandedLoopSnippet
     */
    @SuppressWarnings("unused")
    @Snippet
    public void delayedExactArraycopyWithExpandedLoopSnippet(@NonNullParameter Object src, int srcPos, @NonNullParameter Object dest, int destPos, int length,
                    @ConstantParameter ArrayCopyTypeCheck arrayTypeCheck,
                    @ConstantParameter JavaKind elementKind, @ConstantParameter LocationIdentity locationIdentity, @ConstantParameter SnippetCounter elementKindCounter,
                    @ConstantParameter SnippetCounter elementKindCopiedCounter, @ConstantParameter Counters counters) {
        checkArrayTypes(src, dest, arrayTypeCheck);
        int[] checked = checkLimits(src, srcPos, dest, destPos, length, elementKind, counters);
        int checkedSrcPos = checked[SRC_IDX];
        int checkedDestPos = checked[DEST_IDX];
        int checkedLength = checked[LENGTH_IDX];
        incrementLengthCounter(checkedLength, counters);

        elementKindCounter.inc();
        elementKindCopiedCounter.add(checkedLength);

        // Don't lower until floating guards are fixed.
        ArrayCopyWithDelayedLoweringNode.arraycopyNonThrowing(src, checkedSrcPos, dest, checkedDestPos, checkedLength, WorkSnippetID.exactArraycopyWithExpandedLoopSnippet, GuardsStage.FIXED_DEOPTS,
                        elementKind);
    }

    /**
     * Snippet that performs a stub call for an {@linkplain BasicArrayCopyNode#isExact() exact}
     * array copy.
     */
    @Snippet
    public void arraycopyExactStubCallSnippet(@NonNullParameter Object src, int srcPos, @NonNullParameter Object dest, int destPos, int length, @ConstantParameter ArrayCopyTypeCheck arrayTypeCheck,
                    @ConstantParameter JavaKind elementKind, @ConstantParameter LocationIdentity locationIdentity, @ConstantParameter SnippetCounter elementKindCounter,
                    @ConstantParameter SnippetCounter elementKindCopiedCounter, @ConstantParameter Counters counters) {
        checkArrayTypes(src, dest, arrayTypeCheck);
        int[] checked = checkLimits(src, srcPos, dest, destPos, length, elementKind, counters);
        int checkedSrcPos = checked[SRC_IDX];
        int checkedDestPos = checked[DEST_IDX];
        int checkedLength = checked[LENGTH_IDX];
        incrementLengthCounter(checkedLength, counters);

        elementKindCounter.inc();
        elementKindCopiedCounter.add(checkedLength);

        doArraycopyExactStubCallSnippet(src, checkedSrcPos, dest, checkedDestPos, checkedLength, elementKind, locationIdentity, counters);
    }

    protected void doArraycopyExactStubCallSnippet(Object src, int srcPos, Object dest, int destPos, int length, JavaKind elementKind, LocationIdentity locationIdentity,
                    @SuppressWarnings("unused") Counters counters) {
        ArrayCopyCallNode.arraycopy(src, srcPos, dest, destPos, length, elementKind, locationIdentity, heapWordSize());
    }

    /**
     * Performs an array copy with a type check for every store. At least one of the involved
     * objects is known to be an object array. Lowering is delayed using an
     * {@link ArrayCopyWithDelayedLoweringNode} which will dispatch to
     * {@link #checkcastArraycopySnippet}.
     *
     * @see #checkcastArraycopySnippet
     */
    @Snippet
    public void delayedCheckcastArraycopySnippet(@NonNullParameter Object src, int srcPos, @NonNullParameter Object dest, int destPos, int length, @ConstantParameter ArrayCopyTypeCheck arrayTypeCheck,
                    @ConstantParameter Counters counters, @ConstantParameter JavaKind elementKind) {
        checkArrayTypes(src, dest, arrayTypeCheck);
        int[] checked = checkLimits(src, srcPos, dest, destPos, length, elementKind, counters);
        int checkedSrcPos = checked[SRC_IDX];
        int checkedDestPos = checked[DEST_IDX];
        int checkedLength = checked[LENGTH_IDX];
        incrementLengthCounter(checkedLength, counters);

        // Don't lower until frame states are assigned to deoptimization points.
        ArrayCopyWithDelayedLoweringNode.arraycopy(src, checkedSrcPos, dest, checkedDestPos, checkedLength, WorkSnippetID.checkcastArraycopySnippet, GuardsStage.AFTER_FSA, elementKind);
    }

    /**
     * Performs an array copy using a generic stub. Used when we do not know anything about the
     * object types. Lowering is delayed using an {@link ArrayCopyWithDelayedLoweringNode} which
     * will dispatch to {@link #genericArraycopySnippet}.
     *
     * @see #genericArraycopySnippet
     */
    @Snippet
    public void delayedGenericArraycopySnippet(@NonNullParameter Object src, int srcPos, @NonNullParameter Object dest, int destPos, int length, @ConstantParameter ArrayCopyTypeCheck arrayTypeCheck,
                    @ConstantParameter Counters counters, @ConstantParameter JavaKind elementKind) {
        checkArrayTypes(src, dest, arrayTypeCheck);
        int[] checked = checkLimits(src, srcPos, dest, destPos, length, elementKind, counters);
        int checkedSrcPos = checked[SRC_IDX];
        int checkedDestPos = checked[DEST_IDX];
        int checkedLength = checked[LENGTH_IDX];
        incrementLengthCounter(checkedLength, counters);

        // Don't lower until frame states are assigned to deoptimization points.
        ArrayCopyWithDelayedLoweringNode.arraycopy(src, checkedSrcPos, dest, checkedDestPos, checkedLength, WorkSnippetID.genericArraycopySnippet, GuardsStage.AFTER_FSA, elementKind);
    }

    /**
     * Performs an array copy using the original {@link System#arraycopy} call. Currently, this is
     * only used in cases where we already know that the operation will throw an exception.
     */
    @Snippet
    public static void arraycopyNativeExceptionSnippet(@NonNullParameter Object src, int srcPos, @NonNullParameter Object dest, int destPos, int length, @ConstantParameter Counters counters) {
        // all checks are done in the native method, so no need to emit additional checks here
        incrementLengthCounter(length, counters);
        counters.systemArraycopyCounter.inc();
        counters.systemArraycopyCopiedCounter.add(length);

        System.arraycopy(src, srcPos, dest, destPos, length);
        // the call will never return
        UnreachableNode.unreachable();
    }

    /**
     * Inlines a loop that performs an {@linkplain BasicArrayCopyNode#isExact() exact}
     * element-by-element array copy. The explicit loop allows subsequent phases to optimize the
     * code.
     */
    @SuppressWarnings("unused")
    @Snippet(allowPartialIntrinsicArgumentMismatch = true)
    public void exactArraycopyWithExpandedLoopSnippet(@NonNullParameter Object src, int srcPos, @NonNullParameter Object dest, int destPos, int length, @ConstantParameter JavaKind elementKind,
                    @ConstantParameter LocationIdentity arrayLocation, @ConstantParameter Counters counters) {
        doExactArraycopyWithExpandedLoopSnippet(src, srcPos, dest, destPos, length, elementKind, arrayLocation);
    }

    protected void doExactArraycopyWithExpandedLoopSnippet(Object src, int srcPos, Object dest, int destPos, int length, JavaKind elementKind, LocationIdentity arrayLocation) {
        long scale = ReplacementsUtil.arrayIndexScale(INJECTED_META_ACCESS, elementKind);
        int arrayBaseOffset = ReplacementsUtil.getArrayBaseOffset(INJECTED_META_ACCESS, elementKind);
        long sourceOffset = arrayBaseOffset + srcPos * scale;
        long destOffset = arrayBaseOffset + destPos * scale;

        GuardingNode anchor = SnippetAnchorNode.anchor();
        if (probability(FREQUENT_PROBABILITY, src == dest) && probability(NOT_FREQUENT_PROBABILITY, srcPos < destPos)) {
            // bad aliased case so we need to copy the array from back to front
            for (int position = length - 1; probability(FAST_PATH_PROBABILITY, position >= 0); position--) {
                Object value = GuardedUnsafeLoadNode.guardedLoad(src, sourceOffset + position * scale, elementKind, arrayLocation, anchor);
                RawStoreNode.storeObject(dest, destOffset + position * scale, value, elementKind, arrayLocation, true);
            }
        } else {
            for (int position = 0; probability(FAST_PATH_PROBABILITY, position < length); position++) {
                Object value = GuardedUnsafeLoadNode.guardedLoad(src, sourceOffset + position * scale, elementKind, arrayLocation, anchor);
                RawStoreNode.storeObject(dest, destOffset + position * scale, value, elementKind, arrayLocation, true);
            }
        }
    }

    /**
     * Performs an array copy via fast checkcast stub.
     */
    @Snippet(allowPartialIntrinsicArgumentMismatch = true)
    public void checkcastArraycopySnippet(@NonNullParameter Object src, int srcPos, @NonNullParameter Object dest, int destPos, int length, @ConstantParameter JavaKind elementKind,
                    @ConstantParameter LocationIdentity arrayLocation,
                    @ConstantParameter Counters counters) {
        doCheckcastArraycopySnippet(src, srcPos, dest, destPos, length, elementKind, arrayLocation, counters);
    }

    /**
     * @see #checkcastArraycopySnippet
     */
    protected abstract void doCheckcastArraycopySnippet(Object src, int srcPos, Object dest, int destPos, int length, JavaKind elementKind, LocationIdentity arrayLocation, Counters counters);

    /**
     * Performs a generic array copy with all required type and store checks.
     *
     * @see #delayedGenericArraycopySnippet
     * @see #doGenericArraycopySnippet
     */
    @SuppressWarnings("unused")
    @Snippet(allowPartialIntrinsicArgumentMismatch = true)
    public void genericArraycopySnippet(@NonNullParameter Object src, int srcPos, @NonNullParameter Object dest, int destPos, int length, @ConstantParameter JavaKind elementKind,
                    @ConstantParameter LocationIdentity arrayLocation,
                    @ConstantParameter Counters counters) {
        doGenericArraycopySnippet(src, srcPos, dest, destPos, length, elementKind, arrayLocation, counters);
    }

    /**
     * @see #genericArraycopySnippet
     */
    protected abstract void doGenericArraycopySnippet(Object src, int srcPos, Object dest, int destPos, int length, JavaKind elementKind, LocationIdentity arrayLocation, Counters counters);

    private static void incrementLengthCounter(int length, Counters counters) {
        if (!IS_BUILDING_NATIVE_IMAGE) {
            counters.lengthHistogram.inc(length);
        }
    }

    private static final int SRC_IDX = 0;
    private static final int DEST_IDX = 1;
    private static final int LENGTH_IDX = 2;
    private static final int LIMITS_SIZE = 3;

    /**
     * Creates the return value for {@link #checkLimits}.
     */
    protected static int[] createCheckLimitsResult(int srcPos, int destPos, int length) {
        int[] values = new int[LIMITS_SIZE];
        values[SRC_IDX] = srcPos;
        values[DEST_IDX] = destPos;
        values[LENGTH_IDX] = length;
        return values;
    }

    /**
     * Checks the limits of {@code srcPost}, {@code destPos}, and {@code length} and return those
     * values with better stamps.
     *
     * Writing this as individual if statements to avoid a merge without a frame state.
     *
     * @see #createCheckLimitsResult
     * @return An integer array holding the {@code srcPos} (at offset {@link #SRC_IDX}),
     *         {@code destPos} (at offset {@link #DEST_IDX}), and {@code length} parameters (at
     *         offset {@link #LENGTH_IDX}), but annotated with {@link PiNode#piCastPositive}. This
     *         an array rather than a dedicated class with proper members to avoid storing
     *         information about the class in encoded snippets.
     */
    protected int[] checkLimits(Object src, int srcPos, Object dest, int destPos, int length, @SuppressWarnings("unused") JavaKind elementKind, Counters counters) {
        if (probability(DEOPT_PROBABILITY, srcPos < 0)) {
            counters.checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
        }
        int newSrcPos = PiNode.piCastPositive(srcPos, SnippetAnchorNode.anchor());
        if (probability(DEOPT_PROBABILITY, destPos < 0)) {
            counters.checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
        }
        int newDestPos = PiNode.piCastPositive(destPos, SnippetAnchorNode.anchor());
        if (probability(DEOPT_PROBABILITY, length < 0)) {
            counters.checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
        }
        int newLength = PiNode.piCastPositive(length, SnippetAnchorNode.anchor());
        if (probability(DEOPT_PROBABILITY, newSrcPos > ArrayLengthNode.arrayLength(src) - newLength)) {
            counters.checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
        }
        if (probability(DEOPT_PROBABILITY, newDestPos > ArrayLengthNode.arrayLength(dest) - newLength)) {
            counters.checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
        }
        counters.checkSuccessCounter.inc();
        return createCheckLimitsResult(newSrcPos, newDestPos, newLength);
    }

    protected void checkArrayTypes(Object nonNullSrc, Object nonNullDest, ArrayCopyTypeCheck arrayTypeCheck) {
        if (arrayTypeCheck == ArrayCopyTypeCheck.NO_ARRAY_TYPE_CHECK) {
            // nothing to do
        } else if (arrayTypeCheck == ArrayCopyTypeCheck.HUB_BASED_ARRAY_TYPE_CHECK) {
            if (probability(DEOPT_PROBABILITY, !hubsEqual(nonNullSrc, nonNullDest))) {
                DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
            }
        } else if (arrayTypeCheck == ArrayCopyTypeCheck.LAYOUT_HELPER_BASED_ARRAY_TYPE_CHECK) {
            if (probability(DEOPT_PROBABILITY, !layoutHelpersEqual(nonNullSrc, nonNullDest))) {
                DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
            }
        } else {
            ReplacementsUtil.staticAssert(false, "unknown array type check ", arrayTypeCheck);
        }
    }

    protected static class Counters {
        public final SnippetCounter checkSuccessCounter;
        public final SnippetCounter checkAIOOBECounter;

        public final SnippetCounter zeroLengthStaticCounter;
        public final SnippetIntegerHistogram lengthHistogram;

        public final SnippetCounter systemArraycopyCounter;
        public final SnippetCounter systemArraycopyCopiedCounter;

        public final SnippetCounter genericArraycopyDifferentTypeCopiedCounter;
        public final SnippetCounter genericArraycopyDifferentTypeCounter;

        public final SnippetCounter objectCheckcastSameTypeCopiedCounter;
        public final SnippetCounter objectCheckcastSameTypeCounter;
        public final SnippetCounter objectCheckcastDifferentTypeCopiedCounter;
        public final SnippetCounter objectCheckcastDifferentTypeCounter;

        public final EnumMap<JavaKind, SnippetCounter> arraycopyCallCounters = new EnumMap<>(JavaKind.class);
        public final EnumMap<JavaKind, SnippetCounter> arraycopyCallCopiedCounters = new EnumMap<>(JavaKind.class);

        Counters(SnippetCounter.Group.Factory factory) {
            final SnippetCounter.Group checkCounters = factory.createSnippetCounterGroup("System.arraycopy checkInputs");
            final SnippetCounter.Group callCounters = factory.createSnippetCounterGroup("System.arraycopy calls");
            final SnippetCounter.Group copiedElementsCounters = factory.createSnippetCounterGroup("System.arraycopy copied elements");
            final SnippetCounter.Group lengthCounters = factory.createSnippetCounterGroup("System.arraycopy with 0-length");

            checkSuccessCounter = new SnippetCounter(checkCounters, "checkSuccess", "checkSuccess");
            checkAIOOBECounter = new SnippetCounter(checkCounters, "checkAIOOBE", "checkAIOOBE");

            zeroLengthStaticCounter = new SnippetCounter(lengthCounters, "0-length copy static", "calls where the length is statically 0");
            lengthHistogram = new SnippetIntegerHistogram(lengthCounters, 2, "length", "length");

            systemArraycopyCounter = new SnippetCounter(callCounters, "native System.arraycopy", "JNI-based System.arraycopy call");
            systemArraycopyCopiedCounter = new SnippetCounter(copiedElementsCounters, "native System.arraycopy", "JNI-based System.arraycopy call");

            genericArraycopyDifferentTypeCounter = new SnippetCounter(callCounters, "generic[] stub", "generic arraycopy stub");
            genericArraycopyDifferentTypeCopiedCounter = new SnippetCounter(copiedElementsCounters, "generic[] stub", "generic arraycopy stub");

            objectCheckcastSameTypeCounter = new SnippetCounter(callCounters, "checkcast object[] (same-type)", "checkcast object[] stub but src.klass == dest.klass Object[] arrays");
            objectCheckcastSameTypeCopiedCounter = new SnippetCounter(copiedElementsCounters, "checkcast object[] (same-type)", "checkcast object[] stub but src.klass == dest.klass Object[] arrays");
            objectCheckcastDifferentTypeCounter = new SnippetCounter(callCounters, "checkcast object[] (store-check)", "checkcast object[] stub with store check");
            objectCheckcastDifferentTypeCopiedCounter = new SnippetCounter(copiedElementsCounters, "checkcast object[] (store-check)", "checkcast object[] stub with store check");

            createArraycopyCounter(JavaKind.Byte, callCounters, copiedElementsCounters);
            createArraycopyCounter(JavaKind.Boolean, callCounters, copiedElementsCounters);
            createArraycopyCounter(JavaKind.Char, callCounters, copiedElementsCounters);
            createArraycopyCounter(JavaKind.Short, callCounters, copiedElementsCounters);
            createArraycopyCounter(JavaKind.Int, callCounters, copiedElementsCounters);
            createArraycopyCounter(JavaKind.Long, callCounters, copiedElementsCounters);
            createArraycopyCounter(JavaKind.Float, callCounters, copiedElementsCounters);
            createArraycopyCounter(JavaKind.Double, callCounters, copiedElementsCounters);
            createArraycopyCounter(JavaKind.Object, callCounters, copiedElementsCounters);
        }

        void createArraycopyCounter(JavaKind kind, SnippetCounter.Group counters, SnippetCounter.Group copiedCounters) {
            arraycopyCallCounters.put(kind, new SnippetCounter(counters, kind + "[] stub", "arraycopy call for " + kind + "[] arrays"));
            arraycopyCallCopiedCounters.put(kind, new SnippetCounter(copiedCounters, kind + "[] stub", "arraycopy call for " + kind + "[] arrays"));
        }
    }

    /**
     * Returns a {@link Supplier} that builds a phase suite with extra phases to run on the snippet
     * in the mid tier. Extension point for subclasses, used to implement
     * {@link Templates#createMidTierPreLoweringPhases()}.
     *
     * @return a valid {@link Supplier} that returns either a valid {@link PhaseSuite} or
     *         {@code null} if no extra lowering phases are needed
     */
    protected Supplier<PhaseSuite<CoreProviders>> midTierPreLoweringPhaseFactory() {
        return () -> null;
    }

    /**
     * Identifies snippets used for {@linkplain ArrayCopyWithDelayedLoweringNode delayed lowering}
     * of {@link ArrayCopyNode}.
     *
     * @see Templates#getSnippet(WorkSnippetID)
     */
    public enum WorkSnippetID {
        /**
         * @see ArrayCopySnippets#exactArraycopyWithExpandedLoopSnippet
         */
        exactArraycopyWithExpandedLoopSnippet,
        /**
         * @see ArrayCopySnippets#checkcastArraycopySnippet
         */
        checkcastArraycopySnippet,
        /**
         * @see ArrayCopySnippets#genericArraycopySnippet
         */
        genericArraycopySnippet;
    }

    public static class Templates extends SnippetTemplate.AbstractTemplates {
        private final SnippetTemplate.SnippetInfo delayedGenericArraycopySnippet;
        private final SnippetTemplate.SnippetInfo delayedExactArraycopyWithExpandedLoopSnippet;
        private final SnippetTemplate.SnippetInfo arraycopyExactStubCallSnippet;
        private final SnippetTemplate.SnippetInfo delayedCheckcastArraycopySnippet;
        private final SnippetTemplate.SnippetInfo arraycopyNativeExceptionSnippet;
        private final SnippetTemplate.SnippetInfo checkcastArraycopySnippet;
        private final SnippetTemplate.SnippetInfo genericArraycopySnippet;
        private final SnippetTemplate.SnippetInfo exactArraycopyWithExpandedLoopSnippet;

        private final boolean useOriginalArraycopy;
        private ResolvedJavaMethod originalArraycopy;
        private final Counters counters;
        private final Supplier<PhaseSuite<CoreProviders>> midTierPreLoweringPhaseFactory;

        @SuppressWarnings("this-escape")
        public Templates(ArrayCopySnippets receiver, SnippetCounter.Group.Factory factory, OptionValues options, Providers providers) {
            super(options, providers);
            this.counters = new Counters(factory);

            useOriginalArraycopy = receiver.useOriginalArraycopy();
            delayedGenericArraycopySnippet = snippet(providers, receiver, "delayedGenericArraycopySnippet");
            delayedExactArraycopyWithExpandedLoopSnippet = snippet(providers, receiver, "delayedExactArraycopyWithExpandedLoopSnippet");
            arraycopyExactStubCallSnippet = snippet(providers, receiver, "arraycopyExactStubCallSnippet");
            delayedCheckcastArraycopySnippet = snippet(providers, receiver, "delayedCheckcastArraycopySnippet");
            arraycopyNativeExceptionSnippet = snippet(providers, null, "arraycopyNativeExceptionSnippet");
            checkcastArraycopySnippet = snippet(providers, receiver, "checkcastArraycopySnippet");
            genericArraycopySnippet = snippet(providers, receiver, "genericArraycopySnippet");
            exactArraycopyWithExpandedLoopSnippet = snippet(providers, receiver, "exactArraycopyWithExpandedLoopSnippet");
            midTierPreLoweringPhaseFactory = receiver.midTierPreLoweringPhaseFactory();
        }

        private SnippetTemplate.SnippetInfo getSnippet(WorkSnippetID workSnippetID) {
            switch (workSnippetID) {
                case exactArraycopyWithExpandedLoopSnippet:
                    return exactArraycopyWithExpandedLoopSnippet;
                case checkcastArraycopySnippet:
                    return checkcastArraycopySnippet;
                case genericArraycopySnippet:
                    return genericArraycopySnippet;
            }
            throw GraalError.shouldNotReachHere(workSnippetID.toString()); // ExcludeFromJacocoGeneratedReport
        }

        protected SnippetTemplate.SnippetInfo snippet(Providers providers, ArrayCopySnippets receiver, String methodName) {
            SnippetTemplate.SnippetInfo info = snippet(providers,
                            ArrayCopySnippets.class,
                            methodName,
                            originalArraycopy(providers.getMetaAccess()),
                            receiver,
                            LocationIdentity.any());
            return info;
        }

        public void lower(ArrayCopyNode arraycopy, LoweringTool tool) {
            lower(arraycopy, false, tool);
        }

        /**
         * Lowers an {@link ArrayCopyNode}. See the documentation of {@link ArrayCopySnippets} for
         * an overview of the lowering strategies.
         *
         * @param mayExpandThisArraycopy {@code true} if the array copy might be expanded to a copy
         *            loop.
         *
         * @see ArrayCopySnippets
         */
        public void lower(ArrayCopyNode arraycopy, boolean mayExpandThisArraycopy, LoweringTool tool) {
            JavaKind elementKind = BasicArrayCopyNode.selectComponentKind(arraycopy);
            SnippetTemplate.SnippetInfo snippetInfo;
            final ArrayCopyTypeCheck arrayTypeCheck;

            ResolvedJavaType srcType = StampTool.typeOrNull(arraycopy.getSource().stamp(NodeView.DEFAULT));
            ResolvedJavaType destType = StampTool.typeOrNull(arraycopy.getDestination().stamp(NodeView.DEFAULT));
            if (!canBeArray(srcType) || !canBeArray(destType)) {
                // at least one of the objects is definitely not an array - use the native call
                // right away as the copying will fail anyways
                snippetInfo = arraycopyNativeExceptionSnippet;
                arrayTypeCheck = ArrayCopyTypeCheck.UNDEFINED_ARRAY_TYPE_CHECK;
            } else {
                ResolvedJavaType srcComponentType = srcType == null ? null : srcType.getComponentType();
                ResolvedJavaType destComponentType = destType == null ? null : destType.getComponentType();

                if (arraycopy.isExact()) {
                    // there is a sufficient type match - we don't need any additional type checks
                    snippetInfo = arraycopyExactStubCallSnippet;
                    arrayTypeCheck = ArrayCopyTypeCheck.NO_ARRAY_TYPE_CHECK;
                } else if (srcComponentType == null && destComponentType == null) {
                    // we don't know anything about the types - use the generic copying
                    snippetInfo = delayedGenericArraycopySnippet;
                    // no need for additional type check to avoid duplicated work
                    arrayTypeCheck = ArrayCopyTypeCheck.NO_ARRAY_TYPE_CHECK;
                } else if (srcComponentType != null && destComponentType != null) {
                    if (!srcComponentType.isPrimitive() && !destComponentType.isPrimitive()) {
                        // it depends on the array content if the copy succeeds - we need
                        // a type check for every store
                        snippetInfo = delayedCheckcastArraycopySnippet;
                        arrayTypeCheck = ArrayCopyTypeCheck.NO_ARRAY_TYPE_CHECK;
                    } else {
                        // one object is an object array, the other one is a primitive array.
                        // this copy will always fail - use the native call right away
                        assert !srcComponentType.equals(destComponentType) : "must be handled by arraycopy.isExact()";
                        snippetInfo = arraycopyNativeExceptionSnippet;
                        arrayTypeCheck = ArrayCopyTypeCheck.UNDEFINED_ARRAY_TYPE_CHECK;
                    }
                } else {
                    ResolvedJavaType nonNullComponentType = srcComponentType != null ? srcComponentType : destComponentType;
                    if (nonNullComponentType.isPrimitive()) {
                        // one involved object is a primitive array - it is sufficient to directly
                        // compare the hub.
                        snippetInfo = arraycopyExactStubCallSnippet;
                        arrayTypeCheck = ArrayCopyTypeCheck.HUB_BASED_ARRAY_TYPE_CHECK;
                        elementKind = nonNullComponentType.getJavaKind();
                    } else {
                        // one involved object is an object array - the other array's element type
                        // may be primitive or object, hence we compare the layout helper.
                        snippetInfo = delayedCheckcastArraycopySnippet;
                        arrayTypeCheck = ArrayCopyTypeCheck.LAYOUT_HELPER_BASED_ARRAY_TYPE_CHECK;
                    }
                }
            }

            if (mayExpandThisArraycopy && snippetInfo == arraycopyExactStubCallSnippet) {
                snippetInfo = delayedExactArraycopyWithExpandedLoopSnippet;
            }

            // create the snippet
            SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(snippetInfo, arraycopy.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("src", arraycopy.getSource());
            args.add("srcPos", arraycopy.getSourcePosition());
            args.add("dest", arraycopy.getDestination());
            args.add("destPos", arraycopy.getDestinationPosition());
            args.add("length", arraycopy.getLength());
            if (snippetInfo != arraycopyNativeExceptionSnippet) {
                assert arrayTypeCheck != ArrayCopyTypeCheck.UNDEFINED_ARRAY_TYPE_CHECK : "Must not be arrayTypeCheck " + Assertions.errorMessageContext("arrayCopy", arraycopy);
                args.addConst("arrayTypeCheck", arrayTypeCheck);
            }
            Object locationIdentity = arraycopy.killsAnyLocation() ? LocationIdentity.any() : NamedLocationIdentity.getArrayLocation(elementKind);
            if (snippetInfo == arraycopyExactStubCallSnippet || snippetInfo == delayedExactArraycopyWithExpandedLoopSnippet) {
                assert elementKind != null;
                args.addConst("elementKind", elementKind);
                args.addConst("locationIdentity", locationIdentity);
                args.addConst("elementKindCounter", counters.arraycopyCallCounters.get(elementKind));
                args.addConst("elementKindCopiedCounter", counters.arraycopyCallCopiedCounters.get(elementKind));
            }
            args.addConst("counters", counters);
            if (snippetInfo == delayedCheckcastArraycopySnippet) {
                args.addConst("elementKind", JavaKind.Illegal);
            }
            if (snippetInfo == delayedGenericArraycopySnippet) {
                args.addConst("elementKind", JavaKind.Illegal);
            }

            instantiate(tool, args, arraycopy);
        }

        public void lower(ArrayCopyWithDelayedLoweringNode arraycopy, LoweringTool tool) {
            if (!arraycopy.reachedRequiredLoweringStage()) {
                return;
            }

            SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(getSnippet(arraycopy.getSnippet()), arraycopy.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("src", arraycopy.getSource());
            args.add("srcPos", arraycopy.getSourcePosition());
            args.add("dest", arraycopy.getDestination());
            args.add("destPos", arraycopy.getDestinationPosition());
            args.add("length", arraycopy.getLength());

            JavaKind elementKind = arraycopy.getElementKind();
            args.addConst("elementKind", (elementKind == null) ? JavaKind.Illegal : elementKind);

            Object locationIdentity = (elementKind == null) ? LocationIdentity.any() : NamedLocationIdentity.getArrayLocation(arraycopy.getElementKind());
            args.addConst("arrayLocation", locationIdentity);
            args.addConst("counters", counters);
            instantiate(tool, args, arraycopy);
        }

        private static boolean canBeArray(ResolvedJavaType type) {
            return type == null || type.isJavaLangObject() || type.isArray();
        }

        /**
         * Instantiate the snippet template and fix up the FrameState of any Invokes of
         * System.arraycopy and propagate the captured bci in the ArrayCopySlowPathNode.
         */
        private void instantiate(LoweringTool tool, SnippetTemplate.Arguments args, BasicArrayCopyNode arraycopy) {
            StructuredGraph graph = arraycopy.graph();
            SnippetTemplate template = template(tool, arraycopy, args);
            UnmodifiableEconomicMap<Node, Node> duplicates = template.instantiate(tool.getMetaAccess(), arraycopy, SnippetTemplate.DEFAULT_REPLACER, args, false);
            for (Node originalNode : duplicates.getKeys()) {
                if (originalNode instanceof InvokeNode) {
                    InvokeNode invoke = (InvokeNode) duplicates.get(originalNode);
                    assert invoke.asNode().graph() == graph : "Graphs must match " + Assertions.errorMessageContext("invoke", invoke, "invoke.graph", invoke.asNode().graph(), "graph", graph);
                    CallTargetNode call = invoke.callTarget();

                    if (!call.targetMethod().equals(originalArraycopy)) {
                        throw new GraalError("unexpected invoke %s in snippet", call.targetMethod());
                    }
                    // Here we need to fix the bci of the invoke
                    assert !BytecodeFrame.isPlaceholderBci(arraycopy.bci()) : arraycopy;
                    assert !arraycopy.graph().getGuardsStage().areFrameStatesAtSideEffects() || assertEquals(arraycopy.bci(), invoke.bci());
                    assert assertEquals(arraycopy.stateDuring(), invoke.stateDuring());
                    assert arraycopy.stateDuring() != null || arraycopy.stateAfter() != null : arraycopy;
                    assert assertEquals(arraycopy.stateAfter(), invoke.stateAfter());
                } else if (originalNode instanceof InvokeWithExceptionNode) {
                    throw new GraalError("unexpected invoke with exception %s in snippet", originalNode);
                }
            }
            GraphUtil.killCFG(arraycopy);
        }

        private static boolean assertEquals(int expected, int actual) {
            assert actual == expected : "Expected " + expected + ", got " + actual;
            return true;
        }

        private static boolean assertEquals(FrameState expected, FrameState actual) {
            assert (expected == null && actual == null) || actual.dataFlowEquals(expected) : "Expected " + expected + ", got " + actual;
            return true;
        }

        private ResolvedJavaMethod originalArraycopy(MetaAccessProvider metaAccess) throws GraalError {
            if (!useOriginalArraycopy) {
                return null;
            }
            if (originalArraycopy == null) {
                try {
                    originalArraycopy = findMethod(metaAccess, System.class, "arraycopy");
                } catch (SecurityException e) {
                    throw new GraalError(e);
                }
            }
            return originalArraycopy;
        }

        @Override
        protected PhaseSuite<CoreProviders> createMidTierPreLoweringPhases() {
            return midTierPreLoweringPhaseFactory.get();
        }
    }
}
