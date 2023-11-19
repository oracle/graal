/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.substitutions;

import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.BMP;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.LATIN1;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.UTF_16;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.UTF_32;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.UTF_8;
import static jdk.graal.compiler.nodes.NamedLocationIdentity.getArrayLocation;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.StrideUtil;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool.ArrayIndexOfVariant;
import jdk.graal.compiler.nodes.ComputeObjectAddressNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.InlineOnlyInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.OptionalLazySymbol;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.replacements.InvocationPluginHelper;
import jdk.graal.compiler.replacements.nodes.ArrayCopyWithConversionsNode;
import jdk.graal.compiler.replacements.nodes.ArrayIndexOfMacroNode;
import jdk.graal.compiler.replacements.nodes.ArrayIndexOfNode;
import jdk.graal.compiler.replacements.nodes.ArrayRegionCompareToNode;
import jdk.graal.compiler.replacements.nodes.ArrayRegionEqualsNode;
import jdk.graal.compiler.replacements.nodes.CalcStringAttributesMacroNode;
import jdk.graal.compiler.replacements.nodes.MacroNode;
import jdk.graal.compiler.replacements.nodes.VectorizedHashCodeNode;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Provides {@link InvocationPlugin}s for Truffle classes. These plugins are applied for host and
 * guest compilation.
 */
public class TruffleInvocationPlugins {

    public static void register(Architecture architecture, InvocationPlugins plugins, Replacements replacements) {
        if (architecture instanceof AMD64 || architecture instanceof AArch64) {
            registerTStringPlugins(plugins, replacements, architecture);
            registerArrayUtilsPlugins(plugins, replacements);
        }
    }

    private static void registerArrayUtilsPlugins(InvocationPlugins plugins, Replacements replacements) {
        plugins.registerIntrinsificationPredicate(t -> t.getName().equals("Lcom/oracle/truffle/api/ArrayUtils;"));
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins, "com.oracle.truffle.api.ArrayUtils", replacements);
        for (Stride stride : new Stride[]{Stride.S1, Stride.S2}) {
            r.register(new InlineOnlyInvocationPlugin("stubIndexOfB1" + stride.name(), byte[].class, int.class, int.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                                ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0) {
                    return arrayUtilsIndexOfAny(b, JavaKind.Byte, stride, array, fromIndex, maxIndex, v0);
                }
            });
            r.register(new InlineOnlyInvocationPlugin("stubIndexOfB2" + stride.name(), byte[].class, int.class, int.class, int.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                                ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1) {
                    return arrayUtilsIndexOfAny(b, JavaKind.Byte, stride, array, fromIndex, maxIndex, v0, v1);
                }
            });
            r.register(new InlineOnlyInvocationPlugin("stubIndexOfB3" + stride.name(), byte[].class, int.class, int.class, int.class, int.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                                ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1, ValueNode v2) {
                    return arrayUtilsIndexOfAny(b, JavaKind.Byte, stride, array, fromIndex, maxIndex, v0, v1, v2);
                }
            });
            r.register(new InlineOnlyInvocationPlugin("stubIndexOfB4" + stride.name(), byte[].class, int.class, int.class, int.class, int.class, int.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                                ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1, ValueNode v2, ValueNode v3) {
                    return arrayUtilsIndexOfAny(b, JavaKind.Byte, stride, array, fromIndex, maxIndex, v0, v1, v2, v3);
                }
            });
        }
        r.register(new InlineOnlyInvocationPlugin("stubIndexOfC1S2", char[].class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0) {
                return arrayUtilsIndexOfAny(b, JavaKind.Char, Stride.S2, array, fromIndex, maxIndex, v0);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubIndexOfC2S2", char[].class, int.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1) {
                return arrayUtilsIndexOfAny(b, JavaKind.Char, Stride.S2, array, fromIndex, maxIndex, v0, v1);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubIndexOfC3S2", char[].class, int.class, int.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1, ValueNode v2) {
                return arrayUtilsIndexOfAny(b, JavaKind.Char, Stride.S2, array, fromIndex, maxIndex, v0, v1, v2);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubIndexOfC4S2", char[].class, int.class, int.class, int.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1, ValueNode v2, ValueNode v3) {
                return arrayUtilsIndexOfAny(b, JavaKind.Char, Stride.S2, array, fromIndex, maxIndex, v0, v1, v2, v3);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubIndexOf2ConsecutiveS1", byte[].class, int.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1) {
                return arrayUtilsIndexOf(b, JavaKind.Byte, Stride.S1, ArrayIndexOfVariant.FindTwoConsecutive, array, fromIndex, maxIndex, v0, v1);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubIndexOf2ConsecutiveS2", byte[].class, int.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1) {
                return arrayUtilsIndexOf(b, JavaKind.Byte, Stride.S2, ArrayIndexOfVariant.FindTwoConsecutive, array, fromIndex, maxIndex, v0, v1);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubIndexOf2ConsecutiveS2", char[].class, int.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1) {
                return arrayUtilsIndexOf(b, JavaKind.Char, Stride.S2, ArrayIndexOfVariant.FindTwoConsecutive, array, fromIndex, maxIndex, v0, v1);
            }
        });

        r.register(new InlineOnlyInvocationPlugin("stubRegionEqualsS1", byte[].class, long.class, byte[].class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext graph, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length) {
                return arrayUtilsRegionEquals(graph, arrayA, offsetA, arrayB, offsetB, length, JavaKind.Byte, Stride.S1, Stride.S1);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubRegionEqualsS2S1", byte[].class, long.class, byte[].class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext graph, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length) {
                return arrayUtilsRegionEquals(graph, arrayA, offsetA, arrayB, offsetB, length, JavaKind.Byte, Stride.S2, Stride.S1);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubRegionEqualsS2", char[].class, long.class, char[].class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext graph, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length) {
                return arrayUtilsRegionEquals(graph, arrayA, offsetA, arrayB, offsetB, length, JavaKind.Char, Stride.S2, Stride.S2);
            }
        });
    }

    private static boolean arrayUtilsIndexOfAny(GraphBuilderContext b, JavaKind arrayKind, Stride stride, ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode... values) {
        return arrayUtilsIndexOf(b, arrayKind, stride, ArrayIndexOfVariant.MatchAny, array, fromIndex, maxIndex, values);
    }

    public static boolean arrayUtilsIndexOf(GraphBuilderContext b, JavaKind arrayKind, Stride stride, ArrayIndexOfVariant variant, ValueNode array, ValueNode fromIndex,
                    ValueNode maxIndex, ValueNode... values) {
        ValueNode baseOffset = ConstantNode.forLong(b.getMetaAccess().getArrayBaseOffset(arrayKind), b.getGraph());
        GraalError.guarantee(variant != ArrayIndexOfVariant.MatchRange && variant != ArrayIndexOfVariant.Table,
                        "ArrayIndexOf variants \"matchRange\" and \"table\" require more CPU features than just SSE2 and must be inserted via ArrayIndexOfMacroNode");
        b.addPush(JavaKind.Int, new ArrayIndexOfNode(stride, variant, null, getArrayLocation(arrayKind), array, baseOffset, maxIndex, fromIndex, values));
        return true;
    }

    private static boolean arrayUtilsRegionEquals(GraphBuilderContext graph, ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length,
                    JavaKind arrayKind, Stride strideA, Stride strideB) {
        ValueNode byteOffsetA = toByteOffset(graph, arrayKind, strideA, offsetA);
        ValueNode byteOffsetB = toByteOffset(graph, arrayKind, strideB, offsetB);
        graph.addPush(JavaKind.Boolean, new ArrayRegionEqualsNode(arrayA, byteOffsetA, arrayB, byteOffsetB, length, strideA, strideB, getArrayLocation(arrayKind)));
        return true;
    }

    public static ValueNode toByteOffset(GraphBuilderContext graph, JavaKind arrayKind, Stride stride, ValueNode offset) {
        ValueNode shifted = stride == Stride.S1 ? offset : graph.add(LeftShiftNode.create(offset, ConstantNode.forInt(stride.log2, graph.getGraph()), NodeView.DEFAULT));
        return graph.add(AddNode.create(shifted, ConstantNode.forLong(graph.getMetaAccess().getArrayBaseOffset(arrayKind), graph.getGraph()), NodeView.DEFAULT));
    }

    public static Stride constantStrideParam(ValueNode param) {
        if (!param.isJavaConstant()) {
            throw GraalError.shouldNotReachHere("Java constant expected"); // ExcludeFromJacocoGeneratedReport
        }
        // TruffleString stores strides in log2
        return Stride.fromLog2(param.asJavaConstant().asInt());
    }

    private static boolean asBoolean(ValueNode param) {
        // using asInt here because a boolean's stack kind can be int
        return param.asJavaConstant().asInt() != 0;
    }

    public static boolean constantBooleanParam(ValueNode param) {
        if (!param.isJavaConstant()) {
            throw GraalError.shouldNotReachHere("Java constant expected"); // ExcludeFromJacocoGeneratedReport
        }
        return asBoolean(param);
    }

    public static LocationIdentity inferLocationIdentity(ValueNode isNative) {
        if (isNative.isJavaConstant()) {
            return asBoolean(isNative) ? NamedLocationIdentity.OFF_HEAP_LOCATION : getArrayLocation(JavaKind.Byte);
        }
        return LocationIdentity.any();
    }

    /**
     * Infer the location identity of two TruffleString data pointers from boolean parameters
     * {@code isNativeA} and {@code isNativeB}.
     *
     * If both parameters are constant and {@code true}, the resulting locationIdentity is
     * {@link NamedLocationIdentity#OFF_HEAP_LOCATION}, except when {@code nativeToAny} is
     * {@code true}: in that case, the result is {@link NamedLocationIdentity#any()}. This is used
     * for methods with an additional java array parameter that is never native.
     *
     * If both {@code isNativeA} and {@code isNativeB} are constant and {@code false}, the result is
     * {@code getArrayLocation(JavaKind.Byte)}.
     *
     * Otherwise, the result is {@link LocationIdentity#any()}.
     */
    public static LocationIdentity inferLocationIdentity(ValueNode isNativeA, ValueNode isNativeB, boolean nativeToAny) {
        if (isNativeA.isJavaConstant() && isNativeB.isJavaConstant()) {
            boolean isNativeAConst = asBoolean(isNativeA);
            boolean isNativeBConst = asBoolean(isNativeB);
            if (isNativeAConst == isNativeBConst) {
                if (isNativeAConst) {
                    return nativeToAny ? LocationIdentity.any() : NamedLocationIdentity.OFF_HEAP_LOCATION;
                } else {
                    return getArrayLocation(JavaKind.Byte);
                }
            }
        }
        return LocationIdentity.any();
    }

    @SuppressWarnings("try")
    private static void registerTStringPlugins(InvocationPlugins plugins, Replacements replacements, Architecture arch) {
        plugins.registerIntrinsificationPredicate(t -> t.getName().equals("Lcom/oracle/truffle/api/strings/TStringOps;"));
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins, "com.oracle.truffle.api.strings.TStringOps", replacements);

        OptionalLazySymbol nodeType = new OptionalLazySymbol("com.oracle.truffle.api.nodes.Node");

        r.register(new InlineOnlyInvocationPlugin("runIndexOfAny1", nodeType, byte[].class, long.class, int.class, int.class, boolean.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0) {
                return applyIndexOf(b, targetMethod, ArrayIndexOfVariant.MatchAny, location, array, offset, length, stride, isNative, fromIndex, v0);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runIndexOfAny2", nodeType, byte[].class, long.class, int.class, int.class, boolean.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0, ValueNode v1) {
                return applyIndexOf(b, targetMethod, ArrayIndexOfVariant.MatchAny, location, array, offset, length, stride, isNative, fromIndex, v0, v1);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runIndexOfAny3", nodeType, byte[].class, long.class, int.class, int.class, boolean.class, int.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0, ValueNode v1, ValueNode v2) {
                return applyIndexOf(b, targetMethod, ArrayIndexOfVariant.MatchAny, location, array, offset, length, stride, isNative, fromIndex, v0, v1, v2);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runIndexOfAny4", nodeType, byte[].class, long.class, int.class, int.class, boolean.class, int.class, int.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0, ValueNode v1, ValueNode v2,
                            ValueNode v3) {
                return applyIndexOf(b, targetMethod, ArrayIndexOfVariant.MatchAny, location, array, offset, length, stride, isNative, fromIndex, v0, v1, v2, v3);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runIndexOfRange1", nodeType, byte[].class, long.class, int.class, int.class, boolean.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0, ValueNode v1) {
                return applyIndexOf(b, targetMethod, ArrayIndexOfVariant.MatchRange, location, array, offset, length, stride, isNative, fromIndex, v0, v1);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runIndexOfRange2", nodeType, byte[].class, long.class, int.class, int.class, boolean.class, int.class, int.class, int.class, int.class,
                        int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0, ValueNode v1, ValueNode v2,
                            ValueNode v3) {
                return applyIndexOf(b, targetMethod, ArrayIndexOfVariant.MatchRange, location, array, offset, length, stride, isNative, fromIndex, v0, v1, v2, v3);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runIndexOfTable", nodeType, byte[].class, long.class, int.class, int.class, boolean.class, int.class, byte[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode tables) {
                return applyIndexOf(b, targetMethod, ArrayIndexOfVariant.Table, location, array, offset, length, stride, isNative, fromIndex, tables);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runIndexOf2ConsecutiveWithStride", nodeType, byte[].class, long.class, int.class, int.class, boolean.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0, ValueNode v1) {
                return applyIndexOf(b, targetMethod, ArrayIndexOfVariant.FindTwoConsecutive, location, array, offset, length, stride, isNative, fromIndex, v0, v1);
            }
        });

        r.register(new InlineOnlyInvocationPlugin("runRegionEqualsWithStride", nodeType,
                        byte[].class, long.class, boolean.class,
                        byte[].class, long.class, boolean.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode arrayA, ValueNode offsetA, ValueNode isNativeA,
                            ValueNode arrayB, ValueNode offsetB, ValueNode isNativeB, ValueNode length, ValueNode dynamicStrides) {
                LocationIdentity locationIdentity = inferLocationIdentity(isNativeA, isNativeB, false);
                if (dynamicStrides.isJavaConstant()) {
                    int directStubCallIndex = dynamicStrides.asJavaConstant().asInt();
                    b.addPush(JavaKind.Boolean, new ArrayRegionEqualsNode(arrayA, offsetA, arrayB, offsetB, length,
                                    StrideUtil.getConstantStrideA(directStubCallIndex),
                                    StrideUtil.getConstantStrideB(directStubCallIndex),
                                    locationIdentity));
                } else {
                    b.addPush(JavaKind.Boolean, new ArrayRegionEqualsNode(arrayA, offsetA, arrayB, offsetB, length, dynamicStrides, locationIdentity));
                }
                return true;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runMemCmp", nodeType,
                        byte[].class, long.class, boolean.class,
                        byte[].class, long.class, boolean.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode arrayA, ValueNode offsetA, ValueNode isNativeA,
                            ValueNode arrayB, ValueNode offsetB, ValueNode isNativeB, ValueNode length, ValueNode dynamicStrides) {
                LocationIdentity locationIdentity = inferLocationIdentity(isNativeA, isNativeB, false);
                if (dynamicStrides.isJavaConstant()) {
                    int directStubCallIndex = dynamicStrides.asJavaConstant().asInt();
                    b.addPush(JavaKind.Int, new ArrayRegionCompareToNode(arrayA, offsetA, arrayB, offsetB, length,
                                    StrideUtil.getConstantStrideA(directStubCallIndex),
                                    StrideUtil.getConstantStrideB(directStubCallIndex),
                                    locationIdentity));
                } else {
                    b.addPush(JavaKind.Int, new ArrayRegionCompareToNode(arrayA, offsetA, arrayB, offsetB, length, dynamicStrides, locationIdentity));
                }
                return true;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runArrayCopy", nodeType,
                        byte[].class, long.class, boolean.class,
                        byte[].class, long.class, boolean.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode arrayA, ValueNode offsetA, ValueNode isNativeA,
                            ValueNode arrayB, ValueNode offsetB, ValueNode isNativeB, ValueNode length, ValueNode dynamicStrides) {
                return applyArrayCopy(b, arrayA, offsetA, arrayB, offsetB, length, dynamicStrides);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runArrayCopy", nodeType,
                        char[].class, long.class,
                        byte[].class, long.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode arrayA, ValueNode offsetA,
                            ValueNode arrayB, ValueNode offsetB, ValueNode length, ValueNode dynamicStrides) {
                return applyArrayCopy(b, arrayA, offsetA, arrayB, offsetB, length, dynamicStrides);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runArrayCopy", nodeType,
                        int[].class, long.class,
                        byte[].class, long.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode arrayA, ValueNode offsetA,
                            ValueNode arrayB, ValueNode offsetB, ValueNode length, ValueNode dynamicStrides) {
                return applyArrayCopy(b, arrayA, offsetA, arrayB, offsetB, length, dynamicStrides);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runCalcStringAttributesLatin1", nodeType, byte[].class, long.class, int.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode isNative) {
                MacroNode.MacroParams params = MacroNode.MacroParams.of(b, targetMethod, location, array, offset, length, isNative);
                b.addPush(JavaKind.Int, new CalcStringAttributesMacroNode(params, LATIN1, false, inferLocationIdentity(isNative)));
                return true;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runCalcStringAttributesBMP", nodeType, byte[].class, long.class, int.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode isNative) {
                MacroNode.MacroParams params = MacroNode.MacroParams.of(b, targetMethod, location, array, offset, length, isNative);
                b.addPush(JavaKind.Int, new CalcStringAttributesMacroNode(params, BMP, false, inferLocationIdentity(isNative)));
                return true;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runCalcStringAttributesUTF8", nodeType, byte[].class, long.class, int.class, boolean.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode isNative, ValueNode assumeValid) {
                MacroNode.MacroParams params = MacroNode.MacroParams.of(b, targetMethod, location, array, offset, length, isNative, assumeValid);
                b.addPush(JavaKind.Long, new CalcStringAttributesMacroNode(params, UTF_8, constantBooleanParam(assumeValid), inferLocationIdentity(isNative)));
                return true;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runCalcStringAttributesUTF16", nodeType, byte[].class, long.class, int.class, boolean.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode isNative, ValueNode assumeValid) {
                MacroNode.MacroParams params = MacroNode.MacroParams.of(b, targetMethod, location, array, offset, length, isNative, assumeValid);
                b.addPush(JavaKind.Long, new CalcStringAttributesMacroNode(params, UTF_16, constantBooleanParam(assumeValid), inferLocationIdentity(isNative)));
                return true;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runCalcStringAttributesUTF16C", nodeType, char[].class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length) {
                MacroNode.MacroParams params = MacroNode.MacroParams.of(b, targetMethod, location, array, offset, length);
                b.addPush(JavaKind.Long, new CalcStringAttributesMacroNode(params, UTF_16, false, getArrayLocation(JavaKind.Char)));
                return true;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runCalcStringAttributesUTF32", nodeType, byte[].class, long.class, int.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode isNative) {
                MacroNode.MacroParams params = MacroNode.MacroParams.of(b, targetMethod, location, array, offset, length, isNative);
                b.addPush(JavaKind.Int, new CalcStringAttributesMacroNode(params, UTF_32, false, inferLocationIdentity(isNative)));
                return true;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runCalcStringAttributesUTF32I", nodeType, int[].class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length) {
                MacroNode.MacroParams params = MacroNode.MacroParams.of(b, targetMethod, location, array, offset, length);
                b.addPush(JavaKind.Int, new CalcStringAttributesMacroNode(params, UTF_32, false, getArrayLocation(JavaKind.Int)));
                return true;
            }
        });

        r.registerConditional(VectorizedHashCodeNode.isSupported(arch), new InlineOnlyInvocationPlugin(
                        "runHashCode", nodeType, byte[].class, long.class, int.class, int.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    Stride constStride = constantStrideParam(stride);
                    JavaKind componentType = switch (constStride) {
                        case S1 -> JavaKind.Boolean; // unsigned bytes
                        case S2 -> JavaKind.Char;
                        case S4 -> JavaKind.Int;
                        default -> throw GraalError.shouldNotReachHereUnexpectedValue(constStride);
                    };

                    var arrayStart = b.add(new ComputeObjectAddressNode(array, offset));
                    var initialValue = ConstantNode.forInt(0, b.getGraph());
                    b.addPush(JavaKind.Int, new VectorizedHashCodeNode(arrayStart, length, initialValue, componentType));
                    return true;
                }
            }
        });
    }

    private static boolean applyArrayCopy(GraphBuilderContext b, ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length, ValueNode dynamicStrides) {
        if (dynamicStrides.isJavaConstant()) {
            int directStubCallIndex = dynamicStrides.asJavaConstant().asInt();
            b.add(new ArrayCopyWithConversionsNode(arrayA, offsetA, arrayB, offsetB, length,
                            StrideUtil.getConstantStrideA(directStubCallIndex),
                            StrideUtil.getConstantStrideB(directStubCallIndex)));
        } else {
            b.add(new ArrayCopyWithConversionsNode(arrayA, offsetA, arrayB, offsetB, length, dynamicStrides));
        }
        return true;
    }

    public static boolean applyIndexOf(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ArrayIndexOfVariant variant,
                    ValueNode location, ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode... values) {
        Stride constStride = constantStrideParam(stride);
        LocationIdentity locationIdentity = inferLocationIdentity(isNative);
        if (variant == ArrayIndexOfVariant.MatchRange || variant == ArrayIndexOfVariant.Table) {
            // matchRange and table variants require more that just baseline features, so we have to
            // use a MacroNode here
            ValueNode[] args = new ValueNode[7 + values.length];
            args[0] = location;
            args[1] = array;
            args[2] = offset;
            args[3] = length;
            args[4] = stride;
            args[5] = isNative;
            args[6] = fromIndex;
            System.arraycopy(values, 0, args, 7, values.length);
            MacroNode.MacroParams params = MacroNode.MacroParams.of(b, targetMethod, args);
            b.addPush(JavaKind.Int, new ArrayIndexOfMacroNode(params, constStride, variant, locationIdentity));
        } else {
            b.addPush(JavaKind.Int, new ArrayIndexOfNode(constStride, variant, null, locationIdentity, array, offset, length, fromIndex, values));
        }
        return true;
    }
}
