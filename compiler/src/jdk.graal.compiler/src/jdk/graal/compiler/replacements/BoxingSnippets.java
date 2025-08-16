/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements;

import java.util.EnumMap;
import java.util.List;

import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ProfileData;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.extended.AbstractBoxingNode;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.UnboxNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

public class BoxingSnippets implements Snippets {

    @Snippet
    public static Object booleanValueOf(boolean value, @ConstantParameter SnippetCounter valueOfCounter) {
        valueOfCounter.inc();
        return PiNode.piCastToSnippetReplaceeStamp(Boolean.valueOf(value));
    }

    @Snippet
    public static Object byteValueOf(byte value, @ConstantParameter SnippetCounter valueOfCounter) {
        valueOfCounter.inc();
        return PiNode.piCastToSnippetReplaceeStamp(Byte.valueOf(value));
    }

    @Snippet
    public static Object charValueOf(char value, @ConstantParameter SnippetCounter valueOfCounter) {
        valueOfCounter.inc();
        return PiNode.piCastToSnippetReplaceeStamp(Character.valueOf(value));
    }

    @Snippet
    public static Object doubleValueOf(double value, @ConstantParameter SnippetCounter valueOfCounter) {
        valueOfCounter.inc();
        return PiNode.piCastToSnippetReplaceeStamp(Double.valueOf(value));
    }

    @Snippet
    public static Object floatValueOf(float value, @ConstantParameter SnippetCounter valueOfCounter) {
        valueOfCounter.inc();
        return PiNode.piCastToSnippetReplaceeStamp(Float.valueOf(value));
    }

    @Snippet
    public static Object intValueOf(int value, @ConstantParameter SnippetCounter valueOfCounter) {
        valueOfCounter.inc();
        return PiNode.piCastToSnippetReplaceeStamp(Integer.valueOf(value));
    }

    @Snippet
    public static Object longValueOf(long value, @ConstantParameter SnippetCounter valueOfCounter) {
        valueOfCounter.inc();
        return PiNode.piCastToSnippetReplaceeStamp(Long.valueOf(value));
    }

    @Snippet
    public static Object shortValueOf(short value, @ConstantParameter SnippetCounter valueOfCounter) {
        valueOfCounter.inc();
        return PiNode.piCastToSnippetReplaceeStamp(Short.valueOf(value));
    }

    @Snippet
    public static boolean booleanValue(Boolean value, @ConstantParameter SnippetCounter valueCounter) {
        valueCounter.inc();
        return value.booleanValue();
    }

    @Snippet
    public static byte byteValue(Byte value, @ConstantParameter SnippetCounter valueCounter) {
        valueCounter.inc();
        return value.byteValue();
    }

    @Snippet
    public static char charValue(Character value, @ConstantParameter SnippetCounter valueCounter) {
        valueCounter.inc();
        return value.charValue();
    }

    @Snippet
    public static double doubleValue(Double value, @ConstantParameter SnippetCounter valueCounter) {
        valueCounter.inc();
        return value.doubleValue();
    }

    @Snippet
    public static float floatValue(Float value, @ConstantParameter SnippetCounter valueCounter) {
        valueCounter.inc();
        return value.floatValue();
    }

    @Snippet
    public static int intValue(Integer value, @ConstantParameter SnippetCounter valueCounter) {
        valueCounter.inc();
        return value.intValue();
    }

    @Snippet
    public static long longValue(Long value, @ConstantParameter SnippetCounter valueCounter) {
        valueCounter.inc();
        return value.longValue();
    }

    @Snippet
    public static short shortValue(Short value, @ConstantParameter SnippetCounter valueCounter) {
        valueCounter.inc();
        return value.shortValue();
    }

    public static final class Templates extends SnippetTemplate.AbstractTemplates {

        private final EnumMap<JavaKind, SnippetTemplate.SnippetInfo> boxSnippets = new EnumMap<>(JavaKind.class);
        private final EnumMap<JavaKind, SnippetTemplate.SnippetInfo> unboxSnippets = new EnumMap<>(JavaKind.class);

        private final SnippetCounter valueOfCounter;
        private final SnippetCounter valueCounter;

        @SuppressWarnings("hiding")
        public Templates(OptionValues options, SnippetCounter.Group.Factory factory, Providers providers) {
            super(options, providers);

            for (JavaKind kind : new JavaKind[]{JavaKind.Boolean, JavaKind.Byte, JavaKind.Char, JavaKind.Double, JavaKind.Float, JavaKind.Int, JavaKind.Long, JavaKind.Short}) {
                LocationIdentity accessedLocation = null;
                LocationIdentity cacheLocation = null;
                boolean mustHaveCacheField = false;
                switch (kind) {
                    case Byte:
                    case Short:
                    case Char:
                    case Int:
                    case Long:
                        accessedLocation = new FieldLocationIdentity(AbstractBoxingNode.getValueField(providers.getMetaAccess().lookupJavaType(kind.toBoxedJavaClass())));
                        cacheLocation = getCacheLocation(providers, kind);
                        mustHaveCacheField = true;
                        break;
                    case Boolean:
                    case Float:
                    case Double:
                        accessedLocation = new FieldLocationIdentity(AbstractBoxingNode.getValueField(providers.getMetaAccess().lookupJavaType(kind.toBoxedJavaClass())));
                        break;
                    default:
                        throw GraalError.shouldNotReachHereUnexpectedValue(kind); // ExcludeFromJacocoGeneratedReport
                }
                assert accessedLocation != null;
                // Boxing may write to cache or init location
                if (kind == JavaKind.Boolean) {
                    assert cacheLocation == null;
                    FieldLocationIdentity trueField = null;
                    FieldLocationIdentity falseField = null;
                    for (ResolvedJavaField field : providers.getMetaAccess().lookupJavaType(kind.toBoxedJavaClass()).getStaticFields()) {
                        if (field.getName().equals("TRUE")) {
                            trueField = new FieldLocationIdentity(field);
                        } else if (field.getName().equals("FALSE")) {
                            falseField = new FieldLocationIdentity(field);
                        }
                    }
                    // does no allocation
                    boxSnippets.put(kind, snippet(providers,
                                    BoxingSnippets.class,
                                    kind.getJavaName() + "ValueOf",
                                    trueField,
                                    falseField));
                } else {
                    GraalError.guarantee(!mustHaveCacheField || cacheLocation != null, "Must have a cache location for kind %s", kind);
                    if (cacheLocation != null) {
                        boxSnippets.put(kind, snippet(providers,
                                        BoxingSnippets.class,
                                        kind.getJavaName() + "ValueOf",
                                        LocationIdentity.INIT_LOCATION,
                                        accessedLocation,
                                        cacheLocation,
                                        NamedLocationIdentity.getArrayLocation(JavaKind.Object)));
                    } else {
                        boxSnippets.put(kind, snippet(providers,
                                        BoxingSnippets.class,
                                        kind.getJavaName() + "ValueOf",
                                        LocationIdentity.INIT_LOCATION,
                                        accessedLocation,
                                        NamedLocationIdentity.getArrayLocation(JavaKind.Object)));
                    }
                }
                unboxSnippets.put(kind, snippet(providers,
                                BoxingSnippets.class,
                                kind.getJavaName() + "Value",
                                accessedLocation));
            }
            SnippetCounter.Group group = factory.createSnippetCounterGroup("Boxing");
            valueOfCounter = new SnippetCounter(group, "valueOf", "box intrinsification");
            valueCounter = new SnippetCounter(group, "<kind>Value", "unbox intrinsification");
        }

        static Class<?> getCacheClass(JavaKind kind) {
            Class<?>[] innerClasses = kind.toBoxedJavaClass().getDeclaredClasses();
            for (Class<?> innerClass : innerClasses) {
                if (innerClass.getSimpleName().equals(kind.toBoxedJavaClass().getSimpleName() + "Cache")) {
                    return innerClass;
                }
            }
            return null;
        }

        private static LocationIdentity getCacheLocation(CoreProviders providers, JavaKind kind) {
            Class<?> cacheClass = getCacheClass(kind);
            if (cacheClass == null) {
                throw GraalError.shouldNotReachHere(String.format("Cache class for %s not found", kind)); // ExcludeFromJacocoGeneratedReport
            }
            try {
                return new FieldLocationIdentity(providers.getMetaAccess().lookupJavaField(cacheClass.getDeclaredField("cache")));
            } catch (Throwable e) {
                throw GraalError.shouldNotReachHere(e); // ExcludeFromJacocoGeneratedReport
            }
        }

        public void lower(BoxNode box, LoweringTool tool) {
            SnippetTemplate.Arguments args = null;
            args = new SnippetTemplate.Arguments(boxSnippets.get(box.getBoxingKind()), box.graph(), tool.getLoweringStage());
            args.add("value", box.getValue());
            args.add("valueOfCounter", valueOfCounter);
            SnippetTemplate template = template(tool, box, args);
            box.getDebug().log("Lowering integerValueOf in %s: node=%s, template=%s, arguments=%s", box.graph(), box, template, args);

            UnmodifiableEconomicMap<Node, Node> duplicates = template.instantiate(tool.getMetaAccess(), box, SnippetTemplate.DEFAULT_REPLACER, args);
            assignPrimitiveCacheProfiles(box.getBoxingKind(), duplicates);
        }

        public void lower(UnboxNode unbox, LoweringTool tool) {
            SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(unboxSnippets.get(unbox.getBoxingKind()), unbox.graph(), tool.getLoweringStage());
            args.add("value", unbox.getValue());
            args.add("valueCounter", valueCounter);

            SnippetTemplate template = template(tool, unbox, args);
            unbox.getDebug().log("Lowering integerValueOf in %s: node=%s, template=%s, arguments=%s", unbox.graph(), unbox, template, args);
            template.instantiate(tool.getMetaAccess(), unbox, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    /**
     * Boxing primitive values typically utilizes a primitive cache, but profiling information for
     * this cache is absent within snippets. This function injects the necessary profiles to address
     * that gap.
     */
    private static void assignPrimitiveCacheProfiles(JavaKind kind, UnmodifiableEconomicMap<Node, Node> duplicates) {
        NodeBitMap controlFlow = null;
        for (Node originalNode : duplicates.getKeys()) {
            if (originalNode instanceof IfNode originalIf) {
                if (isBoundsCheck(originalIf)) {
                    // Ignore the bounds check of the cache, that is not relevant
                    continue;
                }
                if (controlFlow == null) {
                    controlFlow = originalIf.graph().createNodeBitMap();
                }
                controlFlow.mark(originalIf);
                ProfileData.ProfileSource source = originalIf.getProfileData().getProfileSource();
                assert source.isUnknown() || source.isInjected() : Assertions.errorMessage(originalIf, originalIf.getProfileData(), "Profile should be unknown inside the snippet");
            }
        }
        if (controlFlow == null) {
            return;
        }
        GraalError.guarantee(controlFlow.count() == 1, "Must only have a single control flow element - the branch into the cache but found more %s", controlFlow);

        IfNode cacheIf = (IfNode) controlFlow.first();
        IfNode inlinedNode = (IfNode) duplicates.get(cacheIf);

        ProfileData.BranchProbabilityData b = null;
        switch (kind) {
            case Byte:
                // cache contains all byte values, should not see any control flow and thus never
                // enter this branch
                throw GraalError.shouldNotReachHere("Byte.valueOf should all go to cache and never contain control flow " + controlFlow);
            case Boolean:
                // only two cases, truly 50 / 50
                b = ProfileData.BranchProbabilityData.injected(0.5);
                break;
            case Char:
            case Short:
            case Int:
            case Long:
                AbstractBeginNode trueSucc = cacheIf.trueSuccessor();
                GraalError.guarantee(trueSucc.next() instanceof IfNode, "Must have the bounds check next but found %s", trueSucc.next());
                IfNode boundsIf = (IfNode) trueSucc.next();
                GraalError.guarantee(isBoundsCheck(boundsIf), "Must have the bounds check next but found %s", boundsIf);
                List<Node> anchored = boundsIf.trueSuccessor().anchored().snapshot();
                GraalError.guarantee(anchored.stream().filter(x -> x instanceof MemoryAccess m && NamedLocationIdentity.isArrayLocation(m.getLocationIdentity())).count() == 1,
                                "Remaining control flow should read from the cache but is %s", boundsIf.trueSuccessor());

                b = ProfileData.BranchProbabilityData.injected(BranchProbabilityNode.FREQUENT_PROBABILITY);
                break;
            default:
                throw GraalError.shouldNotReachHere("Unknown control flow in boxing code, did a JDK change trigger this error? Consider adding new logic to set the profile of " + controlFlow);
        }
        inlinedNode.setTrueSuccessorProbability(b);
        inlinedNode.graph().getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, inlinedNode.graph(), "After updating profile of %s", inlinedNode);
    }

    private static boolean isBoundsCheck(IfNode ifNode) {
        if (ifNode.condition() instanceof CompareNode) {
            AbstractBeginNode fs = ifNode.falseSuccessor();
            return fs.next() instanceof DeoptimizeNode deopt && deopt.getReason() == DeoptimizationReason.BoundsCheckException;
        }
        return false;
    }
}
