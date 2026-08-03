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
package jdk.graal.compiler.vector.replacements;

import static jdk.graal.compiler.api.directives.GraalDirectives.FASTPATH_PROBABILITY;
import static jdk.graal.compiler.api.directives.GraalDirectives.injectBranchProbability;
import static jdk.graal.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.vector.nodes.consumer.MaterializeVectorNode;
import jdk.graal.compiler.vector.nodes.op.ConcatVectorNode;
import jdk.graal.compiler.vector.nodes.producer.FillVectorNode;
import jdk.graal.compiler.vector.nodes.producer.LoadVectorNode;
import jdk.graal.compiler.vector.nodes.type.Vector.BooleanVector;
import jdk.graal.compiler.vector.nodes.type.Vector.ByteVector;
import jdk.graal.compiler.vector.nodes.type.Vector.CharVector;
import jdk.graal.compiler.vector.nodes.type.Vector.DoubleVector;
import jdk.graal.compiler.vector.nodes.type.Vector.FloatVector;
import jdk.graal.compiler.vector.nodes.type.Vector.IntVector;
import jdk.graal.compiler.vector.nodes.type.Vector.LongVector;
import jdk.graal.compiler.vector.nodes.type.Vector.ObjectVector;
import jdk.graal.compiler.vector.nodes.type.Vector.ShortVector;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodeinfo.StructuralInput.Memory;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.SnippetAnchorNode;
import jdk.graal.compiler.nodes.UnreachableNode;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.memory.MemoryAnchorNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.SnippetTemplate.AbstractTemplates;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;
import jdk.graal.compiler.replacements.nodes.AssertionNode;
import jdk.graal.compiler.replacements.nodes.FallbackInvokeWithExceptionNode;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Snippets for lowering {@link java.util.Arrays#copyOf} and {@link java.util.Arrays#copyOfRange}.
 * Lowering happens in two phases. First, a {@link CopyOfNode} is lowered into a range check and an
 * {@link UncheckedCopyOfNode} via
 * {@link CopyOfSnippets.Templates#lower(CopyOfNode, LoweringTool, boolean)}. Then, the
 * {@link UncheckedCopyOfNode} is lowered into vector nodes via
 * {@link CopyOfSnippets.Templates#lower(CoreProviders, UncheckedCopyOfNode)}. Therefore, the
 * snippets used for the later lowering (that are all except {@link #copyOfRangeCheck}) do not
 * contain any range or null checks.
 *
 * @see CopyOfNode
 */
public class CopyOfSnippets implements Snippets {

    @Snippet
    public static boolean[] copyOfBoolean(boolean[] source, int sourceLength, int from, int newLength) {

        int readLength = computeReadLength(sourceLength, from, newLength);

        Memory memory = MemoryAnchorNode.anchor();
        BooleanVector start = LoadVectorNode.loadVector(JavaKind.Boolean, source, from, memory);
        BooleanVector rest = FillVectorNode.fill(false);
        BooleanVector ret = ConcatVectorNode.concat(start, readLength, rest, newLength - readLength);

        return MaterializeVectorNode.materializeVector(JavaKind.Boolean, ret, newLength);
    }

    @Snippet
    public static byte[] copyOfByte(byte[] source, int sourceLength, int from, int newLength) {

        int readLength = computeReadLength(sourceLength, from, newLength);

        Memory memory = MemoryAnchorNode.anchor();
        ByteVector start = LoadVectorNode.loadVector(JavaKind.Byte, source, from, memory);
        ByteVector rest = FillVectorNode.fill((byte) 0);
        ByteVector ret = ConcatVectorNode.concat(start, readLength, rest, newLength - readLength);

        return MaterializeVectorNode.materializeVector(JavaKind.Byte, ret, newLength);
    }

    @Snippet
    public static short[] copyOfShort(short[] source, int sourceLength, int from, int newLength) {

        int readLength = computeReadLength(sourceLength, from, newLength);

        Memory memory = MemoryAnchorNode.anchor();
        ShortVector start = LoadVectorNode.loadVector(JavaKind.Short, source, from, memory);
        ShortVector rest = FillVectorNode.fill((short) 0);
        ShortVector ret = ConcatVectorNode.concat(start, readLength, rest, newLength - readLength);

        return MaterializeVectorNode.materializeVector(JavaKind.Short, ret, newLength);
    }

    @Snippet
    public static char[] copyOfChar(char[] source, int sourceLength, int from, int newLength) {

        int readLength = computeReadLength(sourceLength, from, newLength);

        Memory memory = MemoryAnchorNode.anchor();
        CharVector start = LoadVectorNode.loadVector(JavaKind.Char, source, from, memory);
        CharVector rest = FillVectorNode.fill((char) 0);
        CharVector ret = ConcatVectorNode.concat(start, readLength, rest, newLength - readLength);

        return MaterializeVectorNode.materializeVector(JavaKind.Char, ret, newLength);
    }

    @Snippet
    public static int[] copyOfInt(int[] source, int sourceLength, int from, int newLength) {

        int readLength = computeReadLength(sourceLength, from, newLength);

        Memory memory = MemoryAnchorNode.anchor();
        IntVector start = LoadVectorNode.loadVector(JavaKind.Int, source, from, memory);
        IntVector rest = FillVectorNode.fill(0);
        IntVector ret = ConcatVectorNode.concat(start, readLength, rest, newLength - readLength);

        return MaterializeVectorNode.materializeVector(JavaKind.Int, ret, newLength);
    }

    @Snippet
    public static long[] copyOfLong(long[] source, int sourceLength, int from, int newLength) {

        int readLength = computeReadLength(sourceLength, from, newLength);

        Memory memory = MemoryAnchorNode.anchor();
        LongVector start = LoadVectorNode.loadVector(JavaKind.Long, source, from, memory);
        LongVector rest = FillVectorNode.fill(0L);
        LongVector ret = ConcatVectorNode.concat(start, readLength, rest, newLength - readLength);

        return MaterializeVectorNode.materializeVector(JavaKind.Long, ret, newLength);
    }

    @Snippet
    public static float[] copyOfFloat(float[] source, int sourceLength, int from, int newLength) {

        int readLength = computeReadLength(sourceLength, from, newLength);

        Memory memory = MemoryAnchorNode.anchor();
        FloatVector start = LoadVectorNode.loadVector(JavaKind.Float, source, from, memory);
        FloatVector rest = FillVectorNode.fill(0.f);
        FloatVector ret = ConcatVectorNode.concat(start, readLength, rest, newLength - readLength);

        return MaterializeVectorNode.materializeVector(JavaKind.Float, ret, newLength);
    }

    @Snippet
    public static double[] copyOfDouble(double[] source, int sourceLength, int from, int newLength) {

        int readLength = computeReadLength(sourceLength, from, newLength);

        Memory memory = MemoryAnchorNode.anchor();
        DoubleVector start = LoadVectorNode.loadVector(JavaKind.Double, source, from, memory);
        DoubleVector rest = FillVectorNode.fill(0.);
        DoubleVector ret = ConcatVectorNode.concat(start, readLength, rest, newLength - readLength);

        return MaterializeVectorNode.materializeVector(JavaKind.Double, ret, newLength);
    }

    @Snippet
    @SuppressWarnings("unchecked")
    public static <T, U> T[] copyOfObject(U[] source, int sourceLength, int from, int newLength, Class<? extends T[]> newArrayType) {

        int readLength = computeReadLength(sourceLength, from, newLength);

        Memory memory = MemoryAnchorNode.anchor();
        ObjectVector start = LoadVectorNode.loadVector(JavaKind.Object, source, from, memory);
        ObjectVector rest = FillVectorNode.fill(null);
        ObjectVector ret = ConcatVectorNode.concat(start, readLength, rest, newLength - readLength);

        return (T[]) MaterializeVectorNode.materializeVector(newArrayType.getComponentType(), ret, newLength);
    }

    /**
     * Preforms the range checks for {@link java.util.Arrays#copyOf} and
     * {@link java.util.Arrays#copyOfRange} and insert an {@link UncheckedCopyOfNode}.
     *
     * This {@link Snippet} is carefully crafted so that there is only a single
     * {@link FallbackInvokeWithExceptionNode} and a single {@link UnwindNode}. This requirement is
     * due to a limitation of {@link SnippetTemplate}. Lifting this requirement is possible, but it
     * would make matters more complex than they currently need to be.
     */
    @Snippet
    public static Object copyOfRangeCheck(@Snippet.ConstantParameter boolean needsDynamicTypeCheck, @Snippet.ConstantParameter JavaKind elementKind,
                    @Snippet.ConstantParameter boolean needsExplicitException, Object source, int sourceLength, int from, int newLength, Class<?> newArrayType) {
        // range check
        if (injectBranchProbability(FASTPATH_PROBABILITY, from >= 0)) {
            int fromPositive = PiNode.piCastPositive(from, SnippetAnchorNode.anchor());
            if (injectBranchProbability(FASTPATH_PROBABILITY, newLength >= 0)) {
                int newLengthPositive = PiNode.piCastPositive(newLength, SnippetAnchorNode.anchor());
                if (injectBranchProbability(FASTPATH_PROBABILITY, fromPositive <= sourceLength)) {
                    if (elementKind != JavaKind.Object) {
                        AssertionNode.staticAssert(!needsDynamicTypeCheck, "primitive arrays should never need a dynamic type check");
                        // primitive arrays never need a dynamic type check
                        return UncheckedCopyOfNode.copyOfPrimitiveArray(elementKind, source, sourceLength, fromPositive, newLengthPositive);
                    }
                    // object arrays
                    Class<?> nonNullArrayType = GraalDirectives.guardingNonNull(newArrayType);
                    // dynamic type check
                    if (!needsDynamicTypeCheck || injectBranchProbability(FASTPATH_PROBABILITY, nonNullArrayType.isAssignableFrom(source.getClass()))) {
                        /*
                         * We either do not need a dynamic type check (we have proven it to always
                         * hold) or the dynamic type check is successful.
                         */
                        return UncheckedCopyOfNode.copyOfObjectArray(source, sourceLength, fromPositive, newLengthPositive, nonNullArrayType);
                    }
                    // fall through to the exception/fallback case
                }
            }
        }
        // exception case
        if (needsExplicitException || needsDynamicTypeCheck) {
            Object result = FallbackInvokeWithExceptionNode.fallbackFunctionCall();
            if (needsDynamicTypeCheck) {
                /*
                 * The dynamic array type check above failed. The fallback call needs to perform
                 * per-element type checks and can return if they all succeed.
                 */
                return result;
            }
            /*
             * No dynamic type check, so we know that one of the range checks failed. The fallback
             * call will not return.
             */
            throw UnreachableNode.unreachable();
        }
        /*
         * The range check failed and we do not need an explicit exception via the fallback call, so
         * we can simply deopt.
         */
        DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        throw UnreachableNode.unreachable();
    }

    private static int computeReadLength(int sourceLength, int from, int newLength) {
        return Math.min(newLength, sourceLength - from);
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo[] copyOfSnippets;
        private final SnippetInfo copyOfRangeCheckSnippet;

        @SuppressWarnings("this-escape")
        public Templates(OptionValues options, Providers providers) {
            super(options, providers);

            copyOfSnippets = new SnippetInfo[JavaKind.values().length];
            copyOfSnippets[JavaKind.Boolean.ordinal()] = snippet(providers, CopyOfSnippets.class, "copyOfBoolean");
            copyOfSnippets[JavaKind.Byte.ordinal()] = snippet(providers, CopyOfSnippets.class, "copyOfByte");
            copyOfSnippets[JavaKind.Short.ordinal()] = snippet(providers, CopyOfSnippets.class, "copyOfShort");
            copyOfSnippets[JavaKind.Char.ordinal()] = snippet(providers, CopyOfSnippets.class, "copyOfChar");
            copyOfSnippets[JavaKind.Int.ordinal()] = snippet(providers, CopyOfSnippets.class, "copyOfInt");
            copyOfSnippets[JavaKind.Long.ordinal()] = snippet(providers, CopyOfSnippets.class, "copyOfLong");
            copyOfSnippets[JavaKind.Float.ordinal()] = snippet(providers, CopyOfSnippets.class, "copyOfFloat");
            copyOfSnippets[JavaKind.Double.ordinal()] = snippet(providers, CopyOfSnippets.class, "copyOfDouble");
            copyOfRangeCheckSnippet = snippet(providers, CopyOfSnippets.class, "copyOfRangeCheck");

            ResolvedJavaField componentTypeField = getComponentTypeField(providers);
            if (componentTypeField == null) {
                copyOfSnippets[JavaKind.Object.ordinal()] = snippet(providers, CopyOfSnippets.class, "copyOfObject");
            } else {
                LocationIdentity componentTypeLocation = new FieldLocationIdentity(componentTypeField);
                copyOfSnippets[JavaKind.Object.ordinal()] = snippet(providers, CopyOfSnippets.class, "copyOfObject", componentTypeLocation);
            }
        }

        /**
         * The {@link CopyOfSnippets#copyOfObject} snippet uses {@link Class#getComponentType()} and
         * as of JDK 9 (https://bugs.openjdk.java.net/browse/JDK-8047737) that's just a read of the
         * {@code Class.componentType} field. We have to mark the memory location of this field as
         * private so we don't get problems with snippet related assertions.
         *
         * Note that we don't want to configure the snippet template based purely on JDK version as
         * SVM does not have a {@code DynamicHub.componentType} field, regardless of JDK version.
         */
        private static ResolvedJavaField getComponentTypeField(Providers runtime) {
            ResolvedJavaType type = runtime.getMetaAccess().lookupJavaType(Class.class);
            for (ResolvedJavaField field : type.getInstanceFields(false)) {
                if (field.getName().equals("componentType")) {
                    return field;
                }
            }
            return null;
        }

        public void lower(CoreProviders context, UncheckedCopyOfNode copyOf) {
            SnippetInfo snippet = copyOfSnippets[copyOf.getElementKind().ordinal()];
            assert snippet != null : "copyOf snippet for " + copyOf.getElementKind().name() + " not found";

            Arguments args = new Arguments(snippet, copyOf.graph(), LoweringTool.StandardLoweringStage.MID_TIER);
            copyOf.addSnippetArguments(args);
            copyOf.rewireMemoryUsages();

            SnippetTemplate template = template(context, copyOf, args);
            template.instantiate(context.getMetaAccess(), copyOf, DEFAULT_REPLACER, args);
        }

        public void lower(CopyOfNode copyOf, LoweringTool tool, boolean needsDynamicTypeCheck) {
            Arguments args = new Arguments(copyOfRangeCheckSnippet, copyOf.graph(), LoweringTool.StandardLoweringStage.HIGH_TIER);
            args.add("needsDynamicTypeCheck", needsDynamicTypeCheck);
            copyOf.addSnippetArguments(args);

            SnippetTemplate template = template(tool, copyOf, args);
            UnmodifiableEconomicMap<Node, Node> duplicates = template.instantiate(tool.getMetaAccess(), copyOf, DEFAULT_REPLACER, args);
            Node copyOfReplacement = template.getReturnValue(duplicates);
            if (copyOfReplacement instanceof UncheckedCopyOfNode) {
                // update the stamp of the UncheckedCopyOfNode...
                ((UncheckedCopyOfNode) copyOfReplacement).computeBestStamp(tool.getConstantReflection());
            } else {
                /*
                 * If return node is not an UncheckedCopyOfNode, it must be a phi with exactly one
                 * UncheckedCopyOfNode.
                 */
                GraalError.guarantee(copyOfReplacement instanceof ValuePhiNode, "Return node must be an %s or phi but was %s", UncheckedCopyOfNode.class.getSimpleName(), copyOfReplacement);
                ValuePhiNode phi = (ValuePhiNode) copyOfReplacement;
                for (Node phiInput : phi.values()) {
                    if (phiInput instanceof UncheckedCopyOfNode) {
                        // update the stamp of the UncheckedCopyOfNode...
                        ((UncheckedCopyOfNode) phiInput).computeBestStamp(tool.getConstantReflection());
                    } else {
                        GraalError.guarantee(phiInput instanceof Invoke, "Unexpected input to the return phi %s", phiInput);
                    }
                }
                // ...and ensure that the phi stamp is up to date
                phi.inferStamp();
                GraalError.guarantee(phi.stamp(NodeView.DEFAULT).equals(copyOf.stamp(NodeView.DEFAULT)), "Expected the same stamp, but got %s vs. %s",
                                phi.stamp(NodeView.DEFAULT), copyOf.stamp(NodeView.DEFAULT));
            }
            for (Node original : duplicates.getKeys()) {
                if (original instanceof FallbackInvokeWithExceptionNode) {
                    Node replacement = duplicates.get(original);
                    if (replacement instanceof Lowerable) {
                        tool.getLowerer().lower(replacement, tool);
                    }
                }
            }
        }
    }
}
