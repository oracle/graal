/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements.arraycopy;

import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.*;

import java.lang.reflect.*;
import java.util.*;

import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.common.*;
import jdk.internal.jvmci.hotspot.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.api.directives.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.word.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.word.*;

public class ArrayCopySnippets implements Snippets {

    private static int checkArrayType(KlassPointer hub) {
        int layoutHelper = readLayoutHelper(hub);
        if (probability(SLOW_PATH_PROBABILITY, layoutHelper >= 0)) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        return layoutHelper;
    }

    private static void checkLimits(Object src, int srcPos, Object dest, int destPos, int length) {
        if (probability(SLOW_PATH_PROBABILITY, srcPos < 0)) {
            checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        if (probability(SLOW_PATH_PROBABILITY, destPos < 0)) {
            checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        if (probability(SLOW_PATH_PROBABILITY, length < 0)) {
            checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        if (probability(SLOW_PATH_PROBABILITY, srcPos + length > ArrayLengthNode.arrayLength(src))) {
            checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        if (probability(SLOW_PATH_PROBABILITY, destPos + length > ArrayLengthNode.arrayLength(dest))) {
            checkAIOOBECounter.inc();
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        checkSuccessCounter.inc();
    }

    @Snippet
    public static void arraycopyZeroLengthIntrinsic(Object src, int srcPos, Object dest, int destPos, int length) {
        Object nonNullSrc = GraalDirectives.guardingNonNull(src);
        Object nonNullDest = GraalDirectives.guardingNonNull(dest);
        KlassPointer srcHub = loadHub(nonNullSrc);
        KlassPointer destHub = loadHub(nonNullDest);
        checkArrayType(srcHub);
        checkArrayType(destHub);
        checkLimits(nonNullSrc, srcPos, nonNullDest, destPos, length);
        zeroLengthStaticCounter.inc();
    }

    @Snippet
    public static void arraycopyExactIntrinsic(Object src, int srcPos, Object dest, int destPos, int length, @ConstantParameter Kind elementKind, @ConstantParameter SnippetCounter counter,
                    @ConstantParameter SnippetCounter copiedCounter) {
        Object nonNullSrc = GraalDirectives.guardingNonNull(src);
        Object nonNullDest = GraalDirectives.guardingNonNull(dest);
        checkLimits(nonNullSrc, srcPos, nonNullDest, destPos, length);
        counter.inc();
        copiedCounter.add(length);
        ArrayCopyCallNode.arraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length, elementKind);
        if (length == 0) {
            zeroLengthDynamicCounter.inc();
        } else {
            nonZeroLengthDynamicCounter.inc();
            nonZeroLengthDynamicCopiedCounter.add(length);
        }
    }

    /**
     * This intrinsic is useful for the case where we know something statically about one of the
     * inputs but not the other.
     */
    @Snippet
    public static void arraycopyPredictedExactIntrinsic(Object src, int srcPos, Object dest, int destPos, int length, @ConstantParameter Kind elementKind, @ConstantParameter SnippetCounter counter,
                    @ConstantParameter SnippetCounter copiedCounter) {
        Object nonNullSrc = GraalDirectives.guardingNonNull(src);
        Object nonNullDest = GraalDirectives.guardingNonNull(dest);
        KlassPointer srcHub = loadHub(nonNullSrc);
        KlassPointer destHub = loadHub(nonNullDest);
        if (probability(SLOW_PATH_PROBABILITY, srcHub != destHub)) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        checkLimits(nonNullSrc, srcPos, nonNullDest, destPos, length);
        counter.inc();
        copiedCounter.add(length);
        ArrayCopyCallNode.arraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length, elementKind);
        if (length == 0) {
            zeroLengthDynamicCounter.inc();
        } else {
            nonZeroLengthDynamicCounter.inc();
            nonZeroLengthDynamicCopiedCounter.add(length);
        }
    }

    @Snippet
    public static void arraycopyPredictedObjectWork(Object nonNullSrc, int srcPos, Object nonNullDest, int destPos, int length, KlassPointer objectArrayKlass,
                    @ConstantParameter SnippetCounter counter, @ConstantParameter SnippetCounter copiedCounter) {
        if (length > 0) {
            KlassPointer srcHub = loadHub(nonNullSrc);
            KlassPointer destHub = loadHub(nonNullDest);
            if (probability(FAST_PATH_PROBABILITY, srcHub == destHub || destHub == objectArrayKlass)) {
                counter.inc();
                copiedCounter.add(length);
                predictedObjectArrayCopyFastPathCounter.inc();
                predictedObjectArrayCopyFastPathCopiedCounter.add(length);
                ArrayCopyCallNode.arraycopyObjectKillsAny(nonNullSrc, srcPos, nonNullDest, destPos, length);
            } else {
                predictedObjectArrayCopySlowPathCounter.inc();
                predictedObjectArrayCopySlowPathCopiedCounter.add(length);
                System.arraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length);
            }
        }
    }

    /**
     * This is the basic template for the full arraycopy checks, including a check that the
     * underlying type is really an array type.
     */
    @Snippet
    public static void arraycopySlowPathIntrinsic(Object src, int srcPos, Object dest, int destPos, int length, @ConstantParameter Kind elementKind, @ConstantParameter SnippetInfo slowPath,
                    @ConstantParameter Object slowPathArgument) {
        Object nonNullSrc = GraalDirectives.guardingNonNull(src);
        Object nonNullDest = GraalDirectives.guardingNonNull(dest);
        KlassPointer srcHub = loadHub(nonNullSrc);
        KlassPointer destHub = loadHub(nonNullDest);
        checkArrayType(srcHub);
        checkArrayType(destHub);
        checkLimits(nonNullSrc, srcPos, nonNullDest, destPos, length);
        if (length == 0) {
            zeroLengthDynamicCounter.inc();
        } else {
            nonZeroLengthDynamicCounter.inc();
            nonZeroLengthDynamicCopiedCounter.add(length);
        }
        ArrayCopySlowPathNode.arraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length, elementKind, slowPath, slowPathArgument);
    }

    /**
     * Snippet for unrolled arraycopy.
     */
    @Snippet
    public static void arraycopyUnrolledIntrinsic(Object src, int srcPos, Object dest, int destPos, int length, @ConstantParameter int unrolledLength, @ConstantParameter Kind elementKind) {
        Object nonNullSrc = GraalDirectives.guardingNonNull(src);
        Object nonNullDest = GraalDirectives.guardingNonNull(dest);
        checkLimits(nonNullSrc, srcPos, nonNullDest, destPos, length);
        if (length == 0) {
            zeroLengthDynamicCounter.inc();
        } else {
            nonZeroLengthDynamicCounter.inc();
            nonZeroLengthDynamicCopiedCounter.add(length);
        }
        ArrayCopyUnrollNode.arraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length, unrolledLength, elementKind);
    }

    @Snippet
    public static void checkcastArraycopyWork(Object nonNullSrc, int srcPos, Object nonNullDest, int destPos, int length) {
        if (length > 0) {
            KlassPointer destKlass = loadHub(nonNullDest);
            KlassPointer srcKlass = loadHub(nonNullSrc);
            if (probability(SLOW_PATH_PROBABILITY, srcKlass == destKlass)) {
                // no storecheck required.
                objectCheckcastSameTypeCounter.inc();
                objectCheckcastSameTypeCopiedCounter.add(length);
                ArrayCopyCallNode.arraycopyObjectKillsAny(nonNullSrc, srcPos, nonNullDest, destPos, length);
            } else {
                KlassPointer destElemKlass = destKlass.readKlassPointer(arrayClassElementOffset(), OBJ_ARRAY_KLASS_ELEMENT_KLASS_LOCATION);
                Word superCheckOffset = Word.signed(destElemKlass.readInt(superCheckOffsetOffset(), KLASS_SUPER_CHECK_OFFSET_LOCATION));
                objectCheckcastCounter.inc();
                objectCheckcastCopiedCounter.add(length);
                int copiedElements = CheckcastArrayCopyCallNode.checkcastArraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length, superCheckOffset, destElemKlass, false);
                if (copiedElements != 0) {
                    /*
                     * the checkcast stub doesn't throw the ArrayStoreException, but returns the
                     * number of copied elements (xor'd with -1).
                     */
                    copiedElements ^= -1;
                    System.arraycopy(nonNullSrc, srcPos + copiedElements, nonNullDest, destPos + copiedElements, length - copiedElements);
                }
            }
        }
    }

    @Snippet
    public static void arraycopyGeneric(Object src, int srcPos, Object dest, int destPos, int length) {
        Object nonNullSrc = GraalDirectives.guardingNonNull(src);
        Object nonNullDest = GraalDirectives.guardingNonNull(dest);
        KlassPointer srcHub = loadHub(nonNullSrc);
        KlassPointer destHub = loadHub(nonNullDest);
        if (probability(FAST_PATH_PROBABILITY, srcHub.equal(destHub)) && probability(FAST_PATH_PROBABILITY, nonNullSrc != nonNullDest)) {
            int layoutHelper = checkArrayType(srcHub);
            final boolean isObjectArray = ((layoutHelper & layoutHelperElementTypePrimitiveInPlace()) == 0);
            checkLimits(nonNullSrc, srcPos, nonNullDest, destPos, length);
            if (probability(FAST_PATH_PROBABILITY, isObjectArray)) {
                genericObjectExactCallCounter.inc();
                genericObjectExactCallCopiedCounter.add(length);
                ArrayCopyCallNode.disjointArraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length, Kind.Object);
            } else {
                genericPrimitiveCallCounter.inc();
                genericPrimitiveCallCopiedCounter.add(length);
                UnsafeArrayCopyNode.arraycopyPrimitive(nonNullSrc, srcPos, nonNullDest, destPos, length, layoutHelper);
            }
        } else {
            SystemArraycopyCounter.inc();
            SystemArraycopyCopiedCounter.add(length);
            System.arraycopy(nonNullSrc, srcPos, nonNullDest, destPos, length);
        }
    }

    @Fold
    private static LocationIdentity getArrayLocation(Kind kind) {
        return NamedLocationIdentity.getArrayLocation(kind);
    }

    @Snippet
    public static void arraycopyUnrolledWork(Object nonNullSrc, int srcPos, Object nonNullDest, int destPos, @ConstantParameter int length, @ConstantParameter Kind elementKind) {
        final int scale = arrayIndexScale(elementKind);
        int arrayBaseOffset = arrayBaseOffset(elementKind);
        LocationIdentity arrayLocation = getArrayLocation(elementKind);
        if (nonNullSrc == nonNullDest && srcPos < destPos) { // bad aliased case
            long start = (long) (length - 1) * scale;
            long i = start;
            ExplodeLoopNode.explodeLoop();
            for (int iteration = 0; iteration < length; iteration++) {
                if (i >= 0) {
                    Object a = UnsafeLoadNode.load(nonNullSrc, arrayBaseOffset + i + (long) srcPos * scale, elementKind, arrayLocation);
                    DirectObjectStoreNode.storeObject(nonNullDest, arrayBaseOffset, i + (long) destPos * scale, a, arrayLocation, elementKind);
                    i -= scale;
                }
            }
        } else {
            long end = (long) length * scale;
            long i = 0;
            ExplodeLoopNode.explodeLoop();
            for (int iteration = 0; iteration < length; iteration++) {
                if (i < end) {
                    Object a = UnsafeLoadNode.load(nonNullSrc, arrayBaseOffset + i + (long) srcPos * scale, elementKind, arrayLocation);
                    DirectObjectStoreNode.storeObject(nonNullDest, arrayBaseOffset, i + (long) destPos * scale, a, arrayLocation, elementKind);
                    i += scale;
                }
            }
        }
    }

    private static final SnippetCounter.Group checkCounters = SnippetCounters.getValue() ? new SnippetCounter.Group("System.arraycopy checkInputs") : null;
    private static final SnippetCounter checkSuccessCounter = new SnippetCounter(checkCounters, "checkSuccess", "checkSuccess");
    private static final SnippetCounter checkAIOOBECounter = new SnippetCounter(checkCounters, "checkAIOOBE", "checkAIOOBE");

    private static final SnippetCounter.Group counters = SnippetCounters.getValue() ? new SnippetCounter.Group("System.arraycopy") : null;

    private static final SnippetCounter objectCheckcastCounter = new SnippetCounter(counters, "Object[]{non-exact}", "arraycopy for non-exact Object[] arrays");
    private static final SnippetCounter objectCheckcastSameTypeCounter = new SnippetCounter(counters, "Object[]{same-type}", "arraycopy call for src.klass == dest.klass Object[] arrays");
    private static final SnippetCounter predictedObjectArrayCopySlowPathCounter = new SnippetCounter(counters, "Object[]{slow-path}", "used System.arraycopy slow path for predicted Object[] arrays");
    private static final SnippetCounter predictedObjectArrayCopyFastPathCounter = new SnippetCounter(counters, "Object[]{fast-path}", "used oop_arraycopy for predicted Object[] arrays");

    private static final EnumMap<Kind, SnippetCounter> arraycopyCallCounters = new EnumMap<>(Kind.class);
    private static final EnumMap<Kind, SnippetCounter> arraycopyCounters = new EnumMap<>(Kind.class);

    private static final EnumMap<Kind, SnippetCounter> arraycopyCallCopiedCounters = new EnumMap<>(Kind.class);
    private static final EnumMap<Kind, SnippetCounter> arraycopyCopiedCounters = new EnumMap<>(Kind.class);

    static void createArraycopyCounter(Kind kind) {
        arraycopyCallCounters.put(kind, new SnippetCounter(counters, kind + "[]{stub}", "arraycopy call for " + kind + "[] arrays"));
        arraycopyCounters.put(kind, new SnippetCounter(counters, kind + "[]{inline}", "inline arraycopy for " + kind + "[] arrays"));

        arraycopyCallCopiedCounters.put(kind, new SnippetCounter(copiedCounters, kind + "[]{stub}", "arraycopy call for " + kind + "[] arrays"));
        arraycopyCopiedCounters.put(kind, new SnippetCounter(copiedCounters, kind + "[]{inline}", "inline arraycopy for " + kind + "[] arrays"));
    }

    static {
        createArraycopyCounter(Kind.Byte);
        createArraycopyCounter(Kind.Boolean);
        createArraycopyCounter(Kind.Char);
        createArraycopyCounter(Kind.Short);
        createArraycopyCounter(Kind.Int);
        createArraycopyCounter(Kind.Long);
        createArraycopyCounter(Kind.Float);
        createArraycopyCounter(Kind.Double);
        createArraycopyCounter(Kind.Object);
    }

    private static final SnippetCounter genericPrimitiveCallCounter = new SnippetCounter(counters, "genericPrimitive", "generic arraycopy snippet for primitive arrays");
    private static final SnippetCounter genericObjectExactCallCounter = new SnippetCounter(counters, "genericObjectExact", "generic arraycopy snippet for special object arrays");
    private static final SnippetCounter SystemArraycopyCounter = new SnippetCounter(counters, "genericObject", "call to System.arraycopy");

    private static final SnippetCounter.Group lengthCounters = SnippetCounters.getValue() ? new SnippetCounter.Group("System.arraycopy 0-length checks") : null;

    private static final SnippetCounter zeroLengthStaticCounter = new SnippetCounter(lengthCounters, "0-lengthcopy static", "arraycopy where the length is statically 0");
    private static final SnippetCounter zeroLengthDynamicCounter = new SnippetCounter(lengthCounters, "0-lengthcopy dynamically", "arraycopy where the length is dynamically 0");
    private static final SnippetCounter nonZeroLengthDynamicCounter = new SnippetCounter(lengthCounters, "non-0-lengthcopy dynamically", "arraycopy where the length is dynamically not zero");

    private static final SnippetCounter.Group copiedCounters = SnippetCounters.getValue() ? new SnippetCounter.Group("System.arraycopy copied elements") : null;

    private static final SnippetCounter nonZeroLengthDynamicCopiedCounter = new SnippetCounter(copiedCounters, "non-0-lengthcopy dynamically", "arraycopy where the length is dynamically not zero");
    private static final SnippetCounter genericPrimitiveCallCopiedCounter = new SnippetCounter(copiedCounters, "genericPrimitive", "generic arraycopy snippet for primitive arrays");
    private static final SnippetCounter genericObjectExactCallCopiedCounter = new SnippetCounter(copiedCounters, "genericObjectExact", "generic arraycopy snippet for special object arrays");
    private static final SnippetCounter SystemArraycopyCopiedCounter = new SnippetCounter(copiedCounters, "genericObject", "call to System.arraycopy");

    private static final SnippetCounter objectCheckcastCopiedCounter = new SnippetCounter(copiedCounters, "Object[]{non-exact}", "arraycopy for non-exact Object[] arrays");
    private static final SnippetCounter objectCheckcastSameTypeCopiedCounter = new SnippetCounter(copiedCounters, "Object[]{same-type}", "arraycopy call for src.klass == dest.klass Object[] arrays");
    private static final SnippetCounter predictedObjectArrayCopySlowPathCopiedCounter = new SnippetCounter(copiedCounters, "Object[]{slow-path}",
                    "used System.arraycopy slow path for predicted Object[] arrays");
    private static final SnippetCounter predictedObjectArrayCopyFastPathCopiedCounter = new SnippetCounter(copiedCounters, "Object[]{fast-path}", "used oop_arraycopy for predicted Object[] arrays");

    public static class Templates extends SnippetTemplate.AbstractTemplates {

        public Templates(HotSpotProviders providers, TargetDescription target) {
            super(providers, providers.getSnippetReflection(), target);
        }

        private ResolvedJavaMethod originalArraycopy() throws JVMCIError {
            if (originalArraycopy == null) {
                Method method;
                try {
                    method = System.class.getDeclaredMethod("arraycopy", Object.class, int.class, Object.class, int.class, int.class);
                } catch (NoSuchMethodException | SecurityException e) {
                    throw new JVMCIError(e);
                }
                originalArraycopy = providers.getMetaAccess().lookupJavaMethod(method);
            }
            return originalArraycopy;
        }

        private ResolvedJavaMethod originalArraycopy;

        private final SnippetInfo checkcastArraycopyWorkSnippet = snippet("checkcastArraycopyWork");
        private final SnippetInfo arraycopyGenericSnippet = snippet("arraycopyGeneric");

        private final SnippetInfo arraycopySlowPathIntrinsicSnippet = snippet("arraycopySlowPathIntrinsic");
        private final SnippetInfo arraycopyUnrolledIntrinsicSnippet = snippet("arraycopyUnrolledIntrinsic");
        private final SnippetInfo arraycopyExactIntrinsicSnippet = snippet("arraycopyExactIntrinsic");
        private final SnippetInfo arraycopyZeroLengthIntrinsicSnippet = snippet("arraycopyZeroLengthIntrinsic");
        private final SnippetInfo arraycopyPredictedExactIntrinsicSnippet = snippet("arraycopyPredictedExactIntrinsic");
        private final SnippetInfo arraycopyPredictedObjectWorkSnippet = snippet("arraycopyPredictedObjectWork");

        private final SnippetInfo arraycopyUnrolledWorkSnippet = snippet("arraycopyUnrolledWork");

        protected SnippetInfo snippet(String methodName) {
            SnippetInfo info = snippet(ArrayCopySnippets.class, methodName, LocationIdentity.any());
            info.setOriginalMethod(originalArraycopy());
            return info;
        }

        public static Kind selectComponentKind(BasicArrayCopyNode arraycopy) {
            return selectComponentKind(arraycopy, true);
        }

        public static Kind selectComponentKind(BasicArrayCopyNode arraycopy, boolean exact) {
            ResolvedJavaType srcType = StampTool.typeOrNull(arraycopy.getSource().stamp());
            ResolvedJavaType destType = StampTool.typeOrNull(arraycopy.getDestination().stamp());

            if (srcType == null || !srcType.isArray() || destType == null || !destType.isArray()) {
                if (!exact) {
                    Kind component = getComponentKind(srcType);
                    if (component != null) {
                        return component;
                    }
                    return getComponentKind(destType);
                }
                return null;
            }
            if (exact) {
                if (!destType.getComponentType().isAssignableFrom(srcType.getComponentType())) {
                    return null;
                }
                if (!arraycopy.isExact()) {
                    return null;
                }
            }
            return srcType.getComponentType().getKind();
        }

        private static Kind getComponentKind(ResolvedJavaType type) {
            if (type != null && type.isArray()) {
                return type.getComponentType().getKind();
            }
            return null;
        }

        private static boolean shouldUnroll(ValueNode length) {
            return length.isConstant() && length.asJavaConstant().asInt() <= 8 && length.asJavaConstant().asInt() != 0;
        }

        public void lower(ArrayCopyNode arraycopy, LoweringTool tool) {
            Kind componentKind = selectComponentKind(arraycopy);
            SnippetInfo snippetInfo = null;
            SnippetInfo slowPathSnippetInfo = null;
            Object slowPathArgument = null;

            if (arraycopy.getLength().isConstant() && arraycopy.getLength().asJavaConstant().asLong() == 0) {
                snippetInfo = arraycopyZeroLengthIntrinsicSnippet;
            } else if (arraycopy.isExact()) {
                snippetInfo = arraycopyExactIntrinsicSnippet;
                if (shouldUnroll(arraycopy.getLength())) {
                    snippetInfo = arraycopyUnrolledIntrinsicSnippet;
                }
            } else {
                if (componentKind == Kind.Object) {
                    ResolvedJavaType srcType = StampTool.typeOrNull(arraycopy.getSource().stamp());
                    ResolvedJavaType destType = StampTool.typeOrNull(arraycopy.getDestination().stamp());
                    ResolvedJavaType srcComponentType = srcType == null ? null : srcType.getComponentType();
                    ResolvedJavaType destComponentType = destType == null ? null : destType.getComponentType();
                    if (srcComponentType != null && destComponentType != null && !srcComponentType.isPrimitive() && !destComponentType.isPrimitive()) {
                        snippetInfo = arraycopySlowPathIntrinsicSnippet;
                        slowPathSnippetInfo = checkcastArraycopyWorkSnippet;
                        slowPathArgument = LocationIdentity.any();
                        /*
                         * Because this snippet has to use Sysytem.arraycopy as a slow path, we must
                         * pretend to kill any() so clear the componentKind.
                         */
                        componentKind = null;
                    }
                }
                if (componentKind == null && snippetInfo == null) {
                    Kind predictedKind = selectComponentKind(arraycopy, false);
                    if (predictedKind != null) {
                        /*
                         * At least one array is of a known type requiring no store checks, so
                         * assume the other is of the same type. Generally this is working around
                         * deficiencies in our propation of type information.
                         */
                        componentKind = predictedKind;
                        if (predictedKind == Kind.Object) {
                            snippetInfo = arraycopySlowPathIntrinsicSnippet;
                            slowPathSnippetInfo = arraycopyPredictedObjectWorkSnippet;
                            slowPathArgument = predictedKind;
                            componentKind = null;
                        } else {
                            snippetInfo = arraycopyPredictedExactIntrinsicSnippet;
                        }
                    }
                }
                if (snippetInfo == null) {
                    snippetInfo = arraycopyGenericSnippet;
                }
            }
            Arguments args = new Arguments(snippetInfo, arraycopy.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("src", arraycopy.getSource());
            args.add("srcPos", arraycopy.getSourcePosition());
            args.add("dest", arraycopy.getDestination());
            args.add("destPos", arraycopy.getDestinationPosition());
            args.add("length", arraycopy.getLength());
            if (snippetInfo == arraycopyUnrolledIntrinsicSnippet) {
                args.addConst("unrolledLength", arraycopy.getLength().asJavaConstant().asInt());
                args.addConst("elementKind", componentKind != null ? componentKind : Kind.Illegal);
            } else if (snippetInfo == arraycopySlowPathIntrinsicSnippet) {
                args.addConst("elementKind", componentKind != null ? componentKind : Kind.Illegal);
                args.addConst("slowPath", slowPathSnippetInfo);
                assert slowPathArgument != null;
                args.addConst("slowPathArgument", slowPathArgument);
            } else if (snippetInfo == arraycopyExactIntrinsicSnippet || snippetInfo == arraycopyPredictedExactIntrinsicSnippet) {
                assert componentKind != null;
                args.addConst("elementKind", componentKind);
                args.addConst("counter", arraycopyCallCounters.get(componentKind));
                args.addConst("copiedCounter", arraycopyCallCopiedCounters.get(componentKind));
            }
            instantiate(args, arraycopy);
        }

        public void lower(ArrayCopySlowPathNode arraycopy, LoweringTool tool) {
            StructuredGraph graph = arraycopy.graph();
            if (!graph.getGuardsStage().areFrameStatesAtDeopts()) {
                // Can't be lowered yet
                return;
            }
            SnippetInfo snippetInfo = arraycopy.getSnippet();
            Arguments args = new Arguments(snippetInfo, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("nonNullSrc", arraycopy.getSource());
            args.add("srcPos", arraycopy.getSourcePosition());
            args.add("nonNullDest", arraycopy.getDestination());
            args.add("destPos", arraycopy.getDestinationPosition());
            if (snippetInfo == arraycopyUnrolledWorkSnippet) {
                args.addConst("length", ((Integer) arraycopy.getArgument()).intValue());
                args.addConst("elementKind", arraycopy.getElementKind());
            } else {
                args.add("length", arraycopy.getLength());
            }
            if (snippetInfo == arraycopyPredictedObjectWorkSnippet) {
                HotSpotResolvedObjectType arrayKlass = (HotSpotResolvedObjectType) tool.getMetaAccess().lookupJavaType(Object[].class);
                ValueNode objectArrayKlass = ConstantNode.forConstant(tool.getStampProvider().createHubStamp(true), arrayKlass.klass(), tool.getMetaAccess(), arraycopy.graph());
                args.add("objectArrayKlass", objectArrayKlass);
                args.addConst("counter", arraycopyCallCounters.get(Kind.Object));
                args.addConst("copiedCounter", arraycopyCallCopiedCounters.get(Kind.Object));
            }
            instantiate(args, arraycopy);
        }

        public void lower(ArrayCopyUnrollNode arraycopy, LoweringTool tool) {
            StructuredGraph graph = arraycopy.graph();
            if (!graph.getGuardsStage().areFrameStatesAtDeopts()) {
                // Can't be lowered yet
                return;
            }
            SnippetInfo snippetInfo = arraycopyUnrolledWorkSnippet;
            Arguments args = new Arguments(snippetInfo, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("nonNullSrc", arraycopy.getSource());
            args.add("srcPos", arraycopy.getSourcePosition());
            args.add("nonNullDest", arraycopy.getDestination());
            args.add("destPos", arraycopy.getDestinationPosition());
            args.addConst("length", arraycopy.getUnrollLength());
            args.addConst("elementKind", arraycopy.getElementKind());
            template(args).instantiate(providers.getMetaAccess(), arraycopy, SnippetTemplate.DEFAULT_REPLACER, args);
        }

        /**
         * Instantiate the snippet template and fix up the FrameState of any Invokes of
         * System.arraycopy and propagate the captured bci in the ArrayCopySlowPathNode.
         *
         * @param args
         * @param arraycopy
         */
        private void instantiate(Arguments args, BasicArrayCopyNode arraycopy) {
            StructuredGraph graph = arraycopy.graph();
            SnippetTemplate template = template(args);
            Map<Node, Node> replacements = template.instantiate(providers.getMetaAccess(), arraycopy, SnippetTemplate.DEFAULT_REPLACER, args);
            for (Node originalNode : replacements.keySet()) {
                if (originalNode instanceof Invoke) {
                    Invoke invoke = (Invoke) replacements.get(originalNode);
                    assert invoke.asNode().graph() == graph;
                    CallTargetNode call = invoke.callTarget();

                    if (!call.targetMethod().equals(originalArraycopy)) {
                        throw new JVMCIError("unexpected invoke %s in snippet", call.targetMethod());
                    }
                    // Here we need to fix the bci of the invoke
                    InvokeNode newInvoke = graph.add(new InvokeNode(invoke.callTarget(), arraycopy.getBci()));
                    if (arraycopy.stateDuring() != null) {
                        newInvoke.setStateDuring(arraycopy.stateDuring());
                    } else {
                        assert arraycopy.stateAfter() != null;
                        newInvoke.setStateAfter(arraycopy.stateAfter());
                    }
                    graph.replaceFixedWithFixed((InvokeNode) invoke.asNode(), newInvoke);
                } else if (originalNode instanceof ArrayCopySlowPathNode) {
                    ArrayCopySlowPathNode slowPath = (ArrayCopySlowPathNode) replacements.get(originalNode);
                    assert arraycopy.stateAfter() != null;
                    slowPath.setStateAfter(arraycopy.stateAfter());
                    slowPath.setBci(arraycopy.getBci());
                }
            }
        }
    }
}
