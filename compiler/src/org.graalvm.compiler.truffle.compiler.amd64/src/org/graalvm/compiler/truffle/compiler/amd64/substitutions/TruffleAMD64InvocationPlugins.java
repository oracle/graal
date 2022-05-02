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

import static org.graalvm.compiler.nodes.NamedLocationIdentity.getArrayLocation;
import static org.graalvm.compiler.replacements.ArrayIndexOf.NONE;
import static org.graalvm.compiler.replacements.ArrayIndexOf.S1;
import static org.graalvm.compiler.replacements.ArrayIndexOf.S2;
import static org.graalvm.compiler.replacements.ArrayIndexOf.S4;
import static org.graalvm.compiler.replacements.ArrayIndexOf.strideAsPowerOf2;
import static org.graalvm.compiler.truffle.common.TruffleCompilerRuntime.getRuntime;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.amd64.AMD64CalcStringAttributesOp;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.ArrayIndexOfNode;
import org.graalvm.compiler.replacements.amd64.AMD64CalcStringAttributesNode;
import org.graalvm.compiler.replacements.nodes.ArrayCopyWithConversionsNode;
import org.graalvm.compiler.replacements.nodes.ArrayRegionCompareToNode;
import org.graalvm.compiler.replacements.nodes.ArrayRegionEqualsNode;
import org.graalvm.compiler.serviceprovider.ServiceProvider;
import org.graalvm.compiler.truffle.compiler.substitutions.GraphBuilderInvocationPluginProvider;
import org.graalvm.word.LocationIdentity;

import com.oracle.truffle.api.nodes.Node;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@ServiceProvider(GraphBuilderInvocationPluginProvider.class)
public class TruffleAMD64InvocationPlugins implements GraphBuilderInvocationPluginProvider {

    @Override
    public void registerInvocationPlugins(Providers providers, Architecture architecture, InvocationPlugins plugins, boolean canDelayIntrinsification) {
        if (architecture instanceof AMD64) {
            MetaAccessProvider metaAccess = providers.getMetaAccess();
            registerArrayUtilsPlugins(plugins, metaAccess, providers.getReplacements());
            registerTStringPlugins((AMD64) architecture, plugins, metaAccess, providers.getReplacements());
        }
    }

    private static void registerArrayUtilsPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess, Replacements replacements) {
        final ResolvedJavaType arrayUtilsType = getRuntime().resolveType(metaAccess, "com.oracle.truffle.api.ArrayUtils");
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins, new InvocationPlugins.ResolvedJavaSymbol(arrayUtilsType), replacements);

        for (JavaKind stride : new JavaKind[]{JavaKind.Byte, JavaKind.Char}) {
            String strideStr = stride == JavaKind.Byte ? "1" : "2";
            r.register(new InvocationPlugin("stubIndexOfB1S" + strideStr, byte[].class, long.class, int.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                                ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0) {
                    return arrayUtilsIndexOfAny(b, JavaKind.Byte, stride, array, fromIndex, maxIndex, v0);
                }
            });
            r.register(new InvocationPlugin("stubIndexOfB2S" + strideStr, byte[].class, long.class, int.class, int.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                                ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1) {
                    return arrayUtilsIndexOfAny(b, JavaKind.Byte, stride, array, fromIndex, maxIndex, v0, v1);
                }
            });
            r.register(new InvocationPlugin("stubIndexOfB3S" + strideStr, byte[].class, long.class, int.class, int.class, int.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                                ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1, ValueNode v2) {
                    return arrayUtilsIndexOfAny(b, JavaKind.Byte, stride, array, fromIndex, maxIndex, v0, v1, v2);
                }
            });
            r.register(new InvocationPlugin("stubIndexOfB4S" + strideStr, byte[].class, long.class, int.class, int.class, int.class, int.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                                ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1, ValueNode v2, ValueNode v3) {
                    return arrayUtilsIndexOfAny(b, JavaKind.Byte, stride, array, fromIndex, maxIndex, v0, v1, v2, v3);
                }
            });
        }
        r.register(new InvocationPlugin("stubIndexOfC1S2", char[].class, long.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0) {
                return arrayUtilsIndexOfAny(b, JavaKind.Char, JavaKind.Char, array, fromIndex, maxIndex, v0);
            }
        });
        r.register(new InvocationPlugin("stubIndexOfC2S2", char[].class, long.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1) {
                return arrayUtilsIndexOfAny(b, JavaKind.Char, JavaKind.Char, array, fromIndex, maxIndex, v0, v1);
            }
        });
        r.register(new InvocationPlugin("stubIndexOfC3S2", char[].class, long.class, int.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1, ValueNode v2) {
                return arrayUtilsIndexOfAny(b, JavaKind.Char, JavaKind.Char, array, fromIndex, maxIndex, v0, v1, v2);
            }
        });
        r.register(new InvocationPlugin("stubIndexOfC4S2", char[].class, long.class, int.class, int.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1, ValueNode v2, ValueNode v3) {
                return arrayUtilsIndexOfAny(b, JavaKind.Char, JavaKind.Char, array, fromIndex, maxIndex, v0, v1, v2, v3);
            }
        });
        r.register(new InvocationPlugin("stubIndexOf2ConsecutiveS1", byte[].class, long.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1) {
                return arrayUtilsIndexOf(b, JavaKind.Byte, JavaKind.Byte, true, false, array, fromIndex, maxIndex, v0, v1);
            }
        });
        r.register(new InvocationPlugin("stubIndexOf2ConsecutiveS2", byte[].class, long.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1) {
                return arrayUtilsIndexOf(b, JavaKind.Byte, JavaKind.Char, true, false, array, fromIndex, maxIndex, v0, v1);
            }
        });
        r.register(new InvocationPlugin("stubIndexOf2ConsecutiveS2", char[].class, long.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1) {
                return arrayUtilsIndexOf(b, JavaKind.Char, JavaKind.Char, true, false, array, fromIndex, maxIndex, v0, v1);
            }
        });

        r.register(new InvocationPlugin("stubRegionEqualsS1", byte[].class, long.class, byte[].class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext graph, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length) {
                return arrayUtilsRegionEquals(metaAccess, graph, arrayA, offsetA, arrayB, offsetB, length, JavaKind.Byte, JavaKind.Byte, JavaKind.Byte);
            }
        });
        r.register(new InvocationPlugin("stubRegionEqualsS2S1", byte[].class, long.class, byte[].class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext graph, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length) {
                return arrayUtilsRegionEquals(metaAccess, graph, arrayA, offsetA, arrayB, offsetB, length, JavaKind.Byte, JavaKind.Char, JavaKind.Byte);
            }
        });
        r.register(new InvocationPlugin("stubRegionEqualsS2", char[].class, long.class, char[].class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext graph, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length) {
                return arrayUtilsRegionEquals(metaAccess, graph, arrayA, offsetA, arrayB, offsetB, length, JavaKind.Char, JavaKind.Char, JavaKind.Char);
            }
        });
    }

    private static boolean arrayUtilsIndexOfAny(GraphBuilderContext b, JavaKind arrayKind, JavaKind stride, ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode... values) {
        return arrayUtilsIndexOf(b, arrayKind, stride, false, false, array, fromIndex, maxIndex, values);
    }

    public static boolean arrayUtilsIndexOf(GraphBuilderContext b, JavaKind arrayKind, JavaKind stride, boolean findTwoConsecutive, boolean withMask, ValueNode array, ValueNode fromIndex,
                    ValueNode maxIndex, ValueNode... values) {
        ConstantNode zero = ConstantNode.forInt(0, b.getGraph());
        b.addPush(JavaKind.Int, new ArrayIndexOfNode(arrayKind, stride, findTwoConsecutive, withMask, getArrayLocation(arrayKind), array, zero, maxIndex, fromIndex, values));
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
        switch (param.asJavaConstant().asInt()) {
            case 0:
                return S1;
            case 1:
                return S2;
            case 2:
                return S4;
            default:
                throw GraalError.shouldNotReachHere();
        }
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

    private static void registerTStringPlugins(AMD64 architecture, InvocationPlugins plugins, MetaAccessProvider metaAccess, Replacements replacements) {
        final ResolvedJavaType tStringOps = getRuntime().resolveType(metaAccess, "com.oracle.truffle.api.strings.TStringOps");
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins, new InvocationPlugins.ResolvedJavaSymbol(tStringOps), replacements);

        r.register(new InvocationPlugin("runIndexOfAny1", Node.class, Object.class, long.class, int.class, int.class, boolean.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0) {
                return applyIndexOf(b, false, false, array, offset, length, stride, isNative, fromIndex, v0);
            }
        });
        r.register(new InvocationPlugin("runIndexOfAny2", Node.class, Object.class, long.class, int.class, int.class, boolean.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0, ValueNode v1) {
                return applyIndexOf(b, false, false, array, offset, length, stride, isNative, fromIndex, v0, v1);
            }
        });
        r.register(new InvocationPlugin("runIndexOfAny3", Node.class, Object.class, long.class, int.class, int.class, boolean.class, int.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0, ValueNode v1, ValueNode v2) {
                return applyIndexOf(b, false, false, array, offset, length, stride, isNative, fromIndex, v0, v1, v2);
            }
        });
        r.register(new InvocationPlugin("runIndexOfAny4", Node.class, Object.class, long.class, int.class, int.class, boolean.class, int.class, int.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0, ValueNode v1, ValueNode v2,
                            ValueNode v3) {
                return applyIndexOf(b, false, false, array, offset, length, stride, isNative, fromIndex, v0, v1, v2, v3);
            }
        });
        r.register(new InvocationPlugin("runIndexOf2ConsecutiveWithStride", Node.class, Object.class, long.class, int.class, int.class, boolean.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0, ValueNode v1) {
                return applyIndexOf(b, true, false, array, offset, length, stride, isNative, fromIndex, v0, v1);
            }
        });

        r.register(new InvocationPlugin("runRegionEqualsWithStride", Node.class,
                        Object.class, long.class, int.class, boolean.class,
                        Object.class, long.class, int.class, boolean.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode arrayA, ValueNode offsetA, ValueNode strideA, ValueNode isNativeA,
                            ValueNode arrayB, ValueNode offsetB, ValueNode strideB, ValueNode isNativeB, ValueNode length) {
                b.addPush(JavaKind.Boolean, new ArrayRegionEqualsNode(arrayA, offsetA, arrayB, offsetB, length,
                                constantStrideParam(strideA),
                                constantStrideParam(strideB),
                                inferLocationIdentity(isNativeA, isNativeB, false)));
                return true;
            }
        });
        r.register(new InvocationPlugin("runMemCmp", Node.class,
                        Object.class, long.class, int.class, boolean.class,
                        Object.class, long.class, int.class, boolean.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode arrayA, ValueNode offsetA, ValueNode strideA, ValueNode isNativeA,
                            ValueNode arrayB, ValueNode offsetB, ValueNode strideB, ValueNode isNativeB, ValueNode length) {
                b.addPush(JavaKind.Int, new ArrayRegionCompareToNode(arrayA, offsetA, arrayB, offsetB, length,
                                constantStrideParam(strideA),
                                constantStrideParam(strideB),
                                inferLocationIdentity(isNativeA, isNativeB, false)));
                return true;
            }
        });
        r.register(new InvocationPlugin("runArrayCopy", Node.class,
                        Object.class, long.class, int.class, boolean.class,
                        Object.class, long.class, int.class, boolean.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode arrayA, ValueNode offsetA, ValueNode strideA, ValueNode isNativeA,
                            ValueNode arrayB, ValueNode offsetB, ValueNode strideB, ValueNode isNativeB, ValueNode length) {
                b.add(new ArrayCopyWithConversionsNode(arrayA, offsetA, arrayB, offsetB, length,
                                constantStrideParam(strideA),
                                constantStrideParam(strideB)));
                return true;
            }
        });
        if (architecture.getFeatures().contains(AMD64.CPUFeature.SSE4_1)) {
            r.register(new InvocationPlugin("runCalcStringAttributesLatin1", Node.class, Object.class, long.class, int.class, boolean.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                                ValueNode array, ValueNode offset, ValueNode length, ValueNode isNative) {
                    b.addPush(JavaKind.Int, new AMD64CalcStringAttributesNode(AMD64CalcStringAttributesOp.Op.LATIN1, false, inferLocationIdentity(isNative), array, offset, length));
                    return true;
                }
            });
            r.register(new InvocationPlugin("runCalcStringAttributesBMP", Node.class, Object.class, long.class, int.class, boolean.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                                ValueNode array, ValueNode offset, ValueNode length, ValueNode isNative) {
                    b.addPush(JavaKind.Int, new AMD64CalcStringAttributesNode(AMD64CalcStringAttributesOp.Op.BMP, false, inferLocationIdentity(isNative), array, offset, length));
                    return true;
                }
            });
            r.register(new InvocationPlugin("runCalcStringAttributesUTF8", Node.class, Object.class, long.class, int.class, boolean.class, boolean.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                                ValueNode array, ValueNode offset, ValueNode length, ValueNode isNative, ValueNode isValid) {
                    b.addPush(JavaKind.Long, new AMD64CalcStringAttributesNode(AMD64CalcStringAttributesOp.Op.UTF_8,
                                    constantBooleanParam(isValid), inferLocationIdentity(isNative), array, offset, length));
                    return true;
                }
            });
            r.register(new InvocationPlugin("runCalcStringAttributesUTF16", Node.class, Object.class, long.class, int.class, boolean.class, boolean.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                                ValueNode array, ValueNode offset, ValueNode length, ValueNode isNative, ValueNode isValid) {
                    b.addPush(JavaKind.Long, new AMD64CalcStringAttributesNode(AMD64CalcStringAttributesOp.Op.UTF_16,
                                    constantBooleanParam(isValid), inferLocationIdentity(isNative), array, offset, length));
                    return true;
                }
            });
            r.register(new InvocationPlugin("runCalcStringAttributesUTF16C", Node.class, char[].class, long.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                                ValueNode array, ValueNode offset, ValueNode length) {
                    b.addPush(JavaKind.Long, new AMD64CalcStringAttributesNode(AMD64CalcStringAttributesOp.Op.UTF_16,
                                    false, getArrayLocation(JavaKind.Char), array, offset, length));
                    return true;
                }
            });
            r.register(new InvocationPlugin("runCalcStringAttributesUTF32", Node.class, Object.class, long.class, int.class, boolean.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                                ValueNode array, ValueNode offset, ValueNode length, ValueNode isNative) {
                    b.addPush(JavaKind.Int, new AMD64CalcStringAttributesNode(AMD64CalcStringAttributesOp.Op.UTF_32, false, inferLocationIdentity(isNative), array, offset, length));
                    return true;
                }
            });
            r.register(new InvocationPlugin("runCalcStringAttributesUTF32I", Node.class, int[].class, long.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                                ValueNode array, ValueNode offset, ValueNode length) {
                    b.addPush(JavaKind.Int, new AMD64CalcStringAttributesNode(AMD64CalcStringAttributesOp.Op.UTF_32, false,
                                    getArrayLocation(JavaKind.Int), array, offset, length));
                    return true;
                }
            });
        }
    }

    public static boolean applyIndexOf(GraphBuilderContext b, boolean findTwoConsecutive, boolean withMask,
                    ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode... values) {
        JavaKind constStride = constantStrideParam(stride);
        LocationIdentity locationIdentity = inferLocationIdentity(isNative);
        b.addPush(JavaKind.Int, new ArrayIndexOfNode(NONE, constStride, findTwoConsecutive, withMask, locationIdentity, array, offset, length, fromIndex, values));
        return true;
    }
}
