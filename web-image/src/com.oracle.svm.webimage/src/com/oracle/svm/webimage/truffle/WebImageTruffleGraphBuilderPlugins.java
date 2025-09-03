/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.truffle;

import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.InlineOnlyInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.replacements.nodes.IntrinsicMethodNodeInterface;
import jdk.graal.compiler.truffle.substitutions.TruffleInvocationPlugins;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * {@link TruffleInvocationPlugins} registers various Truffle-specific graph build plugins that
 * produce {@link IntrinsicMethodNodeInterface} nodes. These do not have lowerings and require
 * custom code generation in each backend.
 * <p>
 * For Web Image, we simply turn off all these plugins. For performance some of these replacements
 * make sense, but they would negatively impact code size and require handwritten code to be
 * generated for all Web Image backends.
 */
public class WebImageTruffleGraphBuilderPlugins {

    public static void register(InvocationPlugins plugins) {
        registerTStringPlugins(plugins);
        registerArrayUtilsPlugins(plugins);
        registerExactMathPlugins(plugins);
    }

    private static void registerTStringPlugins(InvocationPlugins plugins) {
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins, "com.oracle.truffle.api.strings.TStringOps").setAllowOverwrite(true);

        InvocationPlugins.OptionalLazySymbol nodeType = new InvocationPlugins.OptionalLazySymbol("com.oracle.truffle.api.nodes.Node");

        r.register(new InlineOnlyInvocationPlugin("runIndexOfAny1", nodeType, byte[].class, long.class, int.class, int.class, boolean.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runIndexOfAny2", nodeType, byte[].class, long.class, int.class, int.class, boolean.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0, ValueNode v1) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runIndexOfAny3", nodeType, byte[].class, long.class, int.class, int.class, boolean.class, int.class, int.class, int.class,
                        int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0, ValueNode v1, ValueNode v2) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runIndexOfAny4", nodeType, byte[].class, long.class, int.class, int.class, boolean.class, int.class, int.class, int.class,
                        int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0, ValueNode v1, ValueNode v2,
                            ValueNode v3) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runIndexOfRange1", nodeType, byte[].class, long.class, int.class, int.class, boolean.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0, ValueNode v1) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runIndexOfRange2", nodeType, byte[].class, long.class, int.class, int.class, boolean.class, int.class, int.class, int.class, int.class,
                        int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0, ValueNode v1, ValueNode v2,
                            ValueNode v3) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runIndexOfTable", nodeType, byte[].class, long.class, int.class, int.class, boolean.class, int.class, byte[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode tables) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runIndexOf2ConsecutiveWithStride", nodeType, byte[].class, long.class, int.class, int.class, boolean.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative, ValueNode fromIndex, ValueNode v0, ValueNode v1) {
                return false;
            }
        });

        r.register(new InlineOnlyInvocationPlugin("runRegionEqualsWithStride", nodeType,
                        byte[].class, long.class, boolean.class,
                        byte[].class, long.class, boolean.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode arrayA, ValueNode offsetA, ValueNode isNativeA,
                            ValueNode arrayB, ValueNode offsetB, ValueNode isNativeB, ValueNode length, ValueNode dynamicStrides) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runMemCmp", nodeType,
                        byte[].class, long.class, boolean.class,
                        byte[].class, long.class, boolean.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode arrayA, ValueNode offsetA, ValueNode isNativeA,
                            ValueNode arrayB, ValueNode offsetB, ValueNode isNativeB, ValueNode length, ValueNode dynamicStrides) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runArrayCopy", nodeType,
                        byte[].class, long.class, boolean.class,
                        byte[].class, long.class, boolean.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode arrayA, ValueNode offsetA, ValueNode isNativeA,
                            ValueNode arrayB, ValueNode offsetB, ValueNode isNativeB, ValueNode length, ValueNode dynamicStrides) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runArrayCopy", nodeType,
                        char[].class, long.class,
                        byte[].class, long.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode arrayA, ValueNode offsetA,
                            ValueNode arrayB, ValueNode offsetB, ValueNode length, ValueNode dynamicStrides) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runArrayCopy", nodeType,
                        int[].class, long.class,
                        byte[].class, long.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode arrayA, ValueNode offsetA,
                            ValueNode arrayB, ValueNode offsetB, ValueNode length, ValueNode dynamicStrides) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runByteSwapS1", nodeType,
                        byte[].class, long.class, boolean.class,
                        byte[].class, long.class, boolean.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode arrayA, ValueNode offsetA, ValueNode isNativeA,
                            ValueNode arrayB, ValueNode offsetB, ValueNode isNativeB, ValueNode length) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runByteSwapS2", nodeType,
                        byte[].class, long.class, boolean.class,
                        byte[].class, long.class, boolean.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode arrayA, ValueNode offsetA, ValueNode isNativeA,
                            ValueNode arrayB, ValueNode offsetB, ValueNode isNativeB, ValueNode length) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runCalcStringAttributesLatin1", nodeType, byte[].class, long.class, int.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode isNative) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runCalcStringAttributesBMP", nodeType, byte[].class, long.class, int.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode isNative) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runCalcStringAttributesUTF8", nodeType, byte[].class, long.class, int.class, boolean.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode isNative, ValueNode assumeValid) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runCalcStringAttributesUTF16", nodeType, byte[].class, long.class, int.class, boolean.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode isNative, ValueNode assumeValid) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runCalcStringAttributesUTF16C", nodeType, char[].class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runCalcStringAttributesUTF32", nodeType, byte[].class, long.class, int.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode isNative) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("runCalcStringAttributesUTF32I", nodeType, int[].class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length) {
                return false;
            }
        });

        r.register(new InlineOnlyInvocationPlugin(
                        "runHashCode", nodeType, byte[].class, long.class, int.class, int.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode location,
                            ValueNode array, ValueNode offset, ValueNode length, ValueNode stride, ValueNode isNative) {
                return false;
            }
        });
    }

    private static void registerArrayUtilsPlugins(InvocationPlugins plugins) {
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins, "com.oracle.truffle.api.ArrayUtils").setAllowOverwrite(true);
        for (Stride stride : new Stride[]{Stride.S1, Stride.S2}) {
            r.register(new InlineOnlyInvocationPlugin("stubIndexOfB1" + stride.name(), byte[].class, int.class, int.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                                ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0) {
                    return false;
                }
            });
            r.register(new InlineOnlyInvocationPlugin("stubIndexOfB2" + stride.name(), byte[].class, int.class, int.class, int.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                                ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1) {
                    return false;
                }
            });
            r.register(new InlineOnlyInvocationPlugin("stubIndexOfB3" + stride.name(), byte[].class, int.class, int.class, int.class, int.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                                ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1, ValueNode v2) {
                    return false;
                }
            });
            r.register(new InlineOnlyInvocationPlugin("stubIndexOfB4" + stride.name(), byte[].class, int.class, int.class, int.class, int.class, int.class, int.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                                ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1, ValueNode v2, ValueNode v3) {
                    return false;
                }
            });
        }
        r.register(new InlineOnlyInvocationPlugin("stubIndexOfC1S2", char[].class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubIndexOfC2S2", char[].class, int.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubIndexOfC3S2", char[].class, int.class, int.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1, ValueNode v2) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubIndexOfC4S2", char[].class, int.class, int.class, int.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1, ValueNode v2, ValueNode v3) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubIndexOf2ConsecutiveS1", byte[].class, int.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubIndexOf2ConsecutiveS2", byte[].class, int.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubIndexOf2ConsecutiveS2", char[].class, int.class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode array, ValueNode fromIndex, ValueNode maxIndex, ValueNode v0, ValueNode v1) {
                return false;
            }
        });

        r.register(new InlineOnlyInvocationPlugin("stubRegionEqualsS1", byte[].class, long.class, byte[].class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext graph, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubRegionEqualsS2S1", byte[].class, long.class, byte[].class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext graph, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length) {
                return false;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("stubRegionEqualsS2", char[].class, long.class, char[].class, long.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext graph, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode arrayA, ValueNode offsetA, ValueNode arrayB, ValueNode offsetB, ValueNode length) {
                return false;
            }
        });
    }

    public static void registerExactMathPlugins(InvocationPlugins plugins) {
        plugins.registerIntrinsificationPredicate(t -> t.getName().equals("Lcom/oracle/truffle/api/ExactMath;"));
        var r = new InvocationPlugins.Registration(plugins, "com.oracle.truffle.api.ExactMath").setAllowOverwrite(true);

        // TODO GR-65897 Remove this once we support unsigned float conversions in Wasm backend
        for (JavaKind floatKind : new JavaKind[]{JavaKind.Float, JavaKind.Double}) {
            for (JavaKind integerKind : new JavaKind[]{JavaKind.Int, JavaKind.Long}) {
                r.register(new InvocationPlugin.OptionalInvocationPlugin(
                                integerKind == JavaKind.Long ? "truncateToUnsignedLong" : "truncateToUnsignedInt",
                                floatKind.toJavaClass()) {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x) {
                        return false;
                    }
                });
            }

            r.register(new InvocationPlugin.OptionalInvocationPlugin(
                            floatKind == JavaKind.Double ? "unsignedToDouble" : "unsignedToFloat",
                            long.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x) {
                    return false;
                }
            });
        }
    }
}
