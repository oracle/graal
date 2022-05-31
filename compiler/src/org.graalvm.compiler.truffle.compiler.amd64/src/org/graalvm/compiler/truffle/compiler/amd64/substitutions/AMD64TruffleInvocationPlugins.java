/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.amd64.substitutions;

import static org.graalvm.compiler.core.common.StrideUtil.NONE;
import static org.graalvm.compiler.nodes.NamedLocationIdentity.getArrayLocation;
import static org.graalvm.compiler.replacements.nodes.ForeignCalls.strideAsPowerOf2;

import org.graalvm.compiler.core.common.StrideUtil;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.amd64.AMD64CalcStringAttributesOp;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.InlineOnlyInvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.replacements.amd64.AMD64CalcStringAttributesMacroNode;
import org.graalvm.compiler.replacements.nodes.ArrayCopyWithConversionsNode;
import org.graalvm.compiler.replacements.nodes.ArrayIndexOfNode;
import org.graalvm.compiler.replacements.nodes.ArrayRegionCompareToNode;
import org.graalvm.compiler.replacements.nodes.ArrayRegionEqualsNode;
import org.graalvm.compiler.replacements.nodes.MacroNode;
import org.graalvm.word.LocationIdentity;

import com.oracle.truffle.api.nodes.Node;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class AMD64TruffleInvocationPlugins {

    public static void register(Architecture architecture, InvocationPlugins plugins, Replacements replacements) {
        if (architecture instanceof AMD64) {
            registerArrayUtilsPlugins(plugins, replacements);
            registerTStringPlugins(plugins, replacements);
        }
    }

    private static void registerArrayUtilsPlugins(InvocationPlugins plugins, Replacements replacements) {
        plugins.registerIntrinsificationPredicate(t -> t.getName().equals("Lcom/oracle/truffle/api/ArrayUtils;"));
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins, "com.oracle.truffle.api.ArrayUtils", replacements);
        for (JavaKind stride : new JavaKind[]{JavaKind.Byte, JavaKind.Char}) {
            String strideStr = stride == JavaKind.Byte ? "1" : "2";
            r.register(new InlineOnlyInvocationPlugin("stubIndexOfB1S" + strideStr, byte[].class, long.class, int.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                                ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0) {
                    return arrayUtilsIndexOfAny(b, JavaKind.Byte, stride, array, fromIndex, maxIndex, v0);
                }
            });
            r.register(new InlineOnlyInvocationPlugin("stubIndexOfB2S" + strideStr, byte[].class, long.class, int.class, int.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                                ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1) {
                    return arrayUtilsIndexOfAny(b, JavaKind.Byte, stride, array, fromIndex, maxIndex, v0, v1);
                }
            });
            r.register(new InlineOnlyInvocationPlugin("stubIndexOfB3S" + strideStr, byte[].class, long.class, int.class, int.class, int.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                                ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1, ValueNode v2) {
                    return arrayUtilsIndexOfAny(b, JavaKind.Byte, stride, array, fromIndex, maxIndex, v0, v1, v2);
                }
            });
            r.register(new InlineOnlyInvocationPlugin("stubIndexOfB4S" + strideStr, byte[].class, long.class, int.class, int.class, int.class, int.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                                ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1, ValueNode v2, ValueNode v3) {
                    return arrayUtilsIndexOfAny(b, JavaKind.Byte, stride, array, fromIndex, maxIndex, v0, v1, v2, v3);
                }
            });
        }
        r.register(new InlineOnlyInvocationPlugin("stubIndexOfC1S2", char[].class, long.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0) {
                return arrayUtilsIndexOfAny(b, JavaKind.Char, JavaKind.Char, array, fromIndex, maxIndex, v0);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubIndexOfC2S2", char[].class, long.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1) {
                return arrayUtilsIndexOfAny(b, JavaKind.Char, JavaKind.Char, array, fromIndex, maxIndex, v0, v1);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubIndexOfC3S2", char[].class, long.class, int.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1, ValueNode v2) {
                return arrayUtilsIndexOfAny(b, JavaKind.Char, JavaKind.Char, array, fromIndex, maxIndex, v0, v1, v2);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubIndexOfC4S2", char[].class, long.class, int.class, int.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1, ValueNode v2, ValueNode v3) {
                return arrayUtilsIndexOfAny(b, JavaKind.Char, JavaKind.Char, array, fromIndex, maxIndex, v0, v1, v2, v3);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubIndexOf2ConsecutiveS1", byte[].class, long.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1) {
                return arrayUtilsIndexOf(b, JavaKind.Byte, JavaKind.Byte, true, false, array, fromIndex, maxIndex, v0, v1);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubIndexOf2ConsecutiveS2", byte[].class, long.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1) {
                return arrayUtilsIndexOf(b, JavaKind.Byte, JavaKind.Char, true, false, array, fromIndex, maxIndex, v0, v1);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubIndexOf2ConsecutiveS2", char[].class, long.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1) {
                return arrayUtilsIndexOf(b, JavaKind.Char, JavaKind.Char, true, false, array, fromIndex, maxIndex, v0, v1);
            }
        });

        r.register(new InlineOnlyInvocationPlugin("stubRegionEqualsS1", byte[].class, long.class, byte[].class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext graph, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length) {
                return arrayUtilsRegionEquals(graph.getMetaAccess(), graph, arrayA, offsetA, arrayB, offsetB, length, JavaKind.Byte, JavaKind.Byte, JavaKind.Byte);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubRegionEqualsS2S1", byte[].class, long.class, byte[].class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext graph, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length) {
                return arrayUtilsRegionEquals(graph.getMetaAccess(), graph, arrayA, offsetA, arrayB, offsetB, length, JavaKind.Byte, JavaKind.Char, JavaKind.Byte);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubRegionEqualsS2", char[].class, long.class, char[].class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext graph, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length) {
                return arrayUtilsRegionEquals(graph.getMetaAccess(), graph, arrayA, offsetA, arrayB, offsetB, length, JavaKind.Char, JavaKind.Char, JavaKind.Char);
            }
        });
    }

    private static boolean arrayUtilsIndexOfAny(GraphBuilderContext b, JavaKind arrayKind, JavaKind stride, ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode... values) {
        return arrayUtilsIndexOf(b, arrayKind, stride, false, false, array, fromIndex, maxIndex, values);
    }

    public static boolean arrayUtilsIndexOf(GraphBuilderContext b, JavaKind arrayKind, JavaKind stride, boolean findTwoConsecutive, boolean withMask, ValueNode array, ValueNode fromIndex,
                    ValueNode maxIndex, ValueNode... values) {
        ConstantNode zero = ConstantNode.forInt(0, b.getGraph());
        b.addPush(JavaKind.Int, new ArrayIndexOfNode(arrayKind, stride, findTwoConsecutive, withMask, null, getArrayLocation(arrayKind), array, zero, maxIndex, fromIndex, values));
        return true;
    }

    private static boolean arrayUtilsRegionEquals(MetaAccessProvider metaAccess, GraphBuilderContext graph, ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length,
                    JavaKind arrayKind, JavaKind strideA, JavaKind strideB) {
        ValueNode byteOffsetA = toByteOffset(metaAccess, graph, offsetA, strideA);
        ValueNode byteOffsetB = toByteOffset(metaAccess, graph, offsetB, strideB);
        graph.addPush(JavaKind.Boolean, new ArrayRegionEqualsNode(arrayA, byteOffsetA, arrayB, byteOffsetB, length, strideA, strideB, getArrayLocation(arrayKind)));
        return true;
    }

    public static ValueNode toByteOffset(MetaAccessProvider metaAccess, GraphBuilderContext graph, ValueNode offset, JavaKind stride) {
        ValueNode shifted = stride == JavaKind.Byte ? offset : graph.add(LeftShiftNode.create(offset, ConstantNode.forInt(strideAsPowerOf2(stride), graph.getGraph()), NodeView.DEFAULT));
        return graph.add(AddNode.create(shifted, ConstantNode.forLong(metaAccess.getArrayBaseOffset(stride), graph.getGraph()), NodeView.DEFAULT));
    }

    public static JavaKind constantStrideParam(ValueNode param) {
        if (!param.isJavaConstant()) {
            throw GraalError.shouldNotReachHere();
        }
        // TruffleString stores strides in log2
        return StrideUtil.log2ToStride(param.asJavaConstant().asInt());
    }

    private static boolean asBoolean(ValueNode param) {
        // using asInt here because a boolean's stack kind can be int
        return param.asJavaConstant().asInt() != 0;
    }

    public static boolean constantBooleanParam(ValueNode param) {
        if (!param.isJavaConstant()) {
            throw GraalError.shouldNotReachHere();
        }
        return asBoolean(param);
    }

    private static LocationIdentity inferLocationIdentity(ValueNode isNative) {
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

    private static void registerTStringPlugins(InvocationPlugins plugins, Replacements replacements) {
        plugins.registerIntrinsificationPredicate(t -> t.getName().equals("Lcom/oracle/truffle/api/strings/TStringOps;"));
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins, "com.oracle.truffle.api.strings.TStringOps", replacements);

        r.register(new InlineOnlyInvocationPlugin("runIndexOfAny1", Node.class, Object.class, long.class, int.class, int.class, boolean.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0) {
                return applyIndexOf(b, false, false, array, offset, length, stride, isNative, fromIndex, v0);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runIndexOfAny2", Node.class, Object.class, long.class, int.class, int.class, boolean.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0, ValueNode v1) {
                return applyIndexOf(b, false, false, array, offset, length, stride, isNative, fromIndex, v0, v1);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runIndexOfAny3", Node.class, Object.class, long.class, int.class, int.class, boolean.class, int.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0, ValueNode v1, ValueNode v2) {
                return applyIndexOf(b, false, false, array, offset, length, stride, isNative, fromIndex, v0, v1, v2);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runIndexOfAny4", Node.class, Object.class, long.class, int.class, int.class, boolean.class, int.class, int.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0, ValueNode v1, ValueNode v2,
                            ValueNode v3) {
                return applyIndexOf(b, false, false, array, offset, length, stride, isNative, fromIndex, v0, v1, v2, v3);
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runIndexOf2ConsecutiveWithStride", Node.class, Object.class, long.class, int.class, int.class, boolean.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0, ValueNode v1) {
                return applyIndexOf(b, true, false, array, offset, length, stride, isNative, fromIndex, v0, v1);
            }
        });

        r.register(new InlineOnlyInvocationPlugin("runRegionEqualsWithStride", Node.class,
                        Object.class, long.class, boolean.class,
                        Object.class, long.class, boolean.class, int.class, int.class) {
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
        r.register(new InlineOnlyInvocationPlugin("runMemCmp", Node.class,
                        Object.class, long.class, boolean.class,
                        Object.class, long.class, boolean.class, int.class, int.class) {
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
        r.register(new InlineOnlyInvocationPlugin("runArrayCopy", Node.class,
                        Object.class, long.class, boolean.class,
                        Object.class, long.class, boolean.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode arrayA, ValueNode offsetA, ValueNode isNativeA,
                            ValueNode arrayB, ValueNode offsetB, ValueNode isNativeB, ValueNode length, ValueNode dynamicStrides) {
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
        });
        r.register(new InlineOnlyInvocationPlugin("runCalcStringAttributesLatin1", Node.class, Object.class, long.class, int.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode isNative) {
                MacroNode.MacroParams params = MacroNode.MacroParams.of(b, targetMethod, location, array, offset, length, isNative);
                b.addPush(JavaKind.Int, new AMD64CalcStringAttributesMacroNode(params, AMD64CalcStringAttributesOp.Op.LATIN1, false, inferLocationIdentity(isNative)));
                return true;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runCalcStringAttributesBMP", Node.class, Object.class, long.class, int.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode isNative) {
                MacroNode.MacroParams params = MacroNode.MacroParams.of(b, targetMethod, location, array, offset, length, isNative);
                b.addPush(JavaKind.Int, new AMD64CalcStringAttributesMacroNode(params, AMD64CalcStringAttributesOp.Op.BMP, false, inferLocationIdentity(isNative)));
                return true;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runCalcStringAttributesUTF8", Node.class, Object.class, long.class, int.class, boolean.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode isNative, ValueNode assumeValid) {
                MacroNode.MacroParams params = MacroNode.MacroParams.of(b, targetMethod, location, array, offset, length, isNative, assumeValid);
                b.addPush(JavaKind.Long, new AMD64CalcStringAttributesMacroNode(params, AMD64CalcStringAttributesOp.Op.UTF_8, constantBooleanParam(assumeValid), inferLocationIdentity(isNative)));
                return true;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runCalcStringAttributesUTF16", Node.class, Object.class, long.class, int.class, boolean.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode isNative, ValueNode assumeValid) {
                MacroNode.MacroParams params = MacroNode.MacroParams.of(b, targetMethod, location, array, offset, length, isNative, assumeValid);
                b.addPush(JavaKind.Long, new AMD64CalcStringAttributesMacroNode(params, AMD64CalcStringAttributesOp.Op.UTF_16, constantBooleanParam(assumeValid), inferLocationIdentity(isNative)));
                return true;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runCalcStringAttributesUTF16C", Node.class, char[].class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length) {
                MacroNode.MacroParams params = MacroNode.MacroParams.of(b, targetMethod, location, array, offset, length);
                b.addPush(JavaKind.Long, new AMD64CalcStringAttributesMacroNode(params, AMD64CalcStringAttributesOp.Op.UTF_16, false, getArrayLocation(JavaKind.Char)));
                return true;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runCalcStringAttributesUTF32", Node.class, Object.class, long.class, int.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode isNative) {
                MacroNode.MacroParams params = MacroNode.MacroParams.of(b, targetMethod, location, array, offset, length, isNative);
                b.addPush(JavaKind.Int, new AMD64CalcStringAttributesMacroNode(params, AMD64CalcStringAttributesOp.Op.UTF_32, false, inferLocationIdentity(isNative)));
                return true;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runCalcStringAttributesUTF32I", Node.class, int[].class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length) {
                MacroNode.MacroParams params = MacroNode.MacroParams.of(b, targetMethod, location, array, offset, length);
                b.addPush(JavaKind.Int, new AMD64CalcStringAttributesMacroNode(params, AMD64CalcStringAttributesOp.Op.UTF_32, false, getArrayLocation(JavaKind.Int)));
                return true;
            }
        });
    }

    public static boolean applyIndexOf(GraphBuilderContext b, boolean findTwoConsecutive, boolean withMask,
                    ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode... values) {
        JavaKind constStride = constantStrideParam(stride);
        LocationIdentity locationIdentity = inferLocationIdentity(isNative);
        b.addPush(JavaKind.Int, new ArrayIndexOfNode(NONE, constStride, findTwoConsecutive, withMask, null, locationIdentity, array, offset, length, fromIndex, values));
        return true;
    }
}
