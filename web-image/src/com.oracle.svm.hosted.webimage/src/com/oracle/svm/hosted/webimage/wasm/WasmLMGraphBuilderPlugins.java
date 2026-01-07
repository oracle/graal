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
package com.oracle.svm.hosted.webimage.wasm;

import static jdk.graal.compiler.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation.POW;
import static jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.COS;
import static jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.EXP;
import static jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG;
import static jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG10;
import static jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.SIN;
import static jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.TAN;

import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.Arrays;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.JavaMemoryUtil;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.hosted.webimage.JSGraphBuilderPlugins;
import com.oracle.svm.hosted.webimage.wasm.nodes.WasmMemoryCopyNode;
import com.oracle.svm.hosted.webimage.wasm.nodes.WasmMemoryFillNode;
import com.oracle.svm.hosted.webimage.wasm.nodes.WasmPopcntNode;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool.RoundingMode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.CopySignNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.MaxNode;
import jdk.graal.compiler.nodes.calc.MinNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.RoundNode;
import jdk.graal.compiler.nodes.extended.JavaReadNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.nodes.memory.address.IndexAddressNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.BigIntegerSnippets;
import jdk.graal.compiler.replacements.SnippetSubstitutionInvocationPlugin;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.StringUTF16Snippets;
import jdk.graal.compiler.replacements.TargetGraphBuilderPlugins;
import jdk.graal.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import jdk.graal.compiler.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation;
import jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;
import jdk.graal.compiler.word.WordCastNode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Graph builder plugins for the WASM backend.
 * <p>
 * Creates specialized nodes for standard library methods that have an equivalent WASM instruction.
 */
public class WasmLMGraphBuilderPlugins implements TargetGraphBuilderPlugins {
    @Override
    public void registerPlugins(GraphBuilderConfiguration.Plugins plugins, OptionValues options) {
        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        invocationPlugins.defer(() -> {
            registerStringPlugins(invocationPlugins);
            registerIntegerLongPlugins(invocationPlugins, JavaKind.Int);
            registerIntegerLongPlugins(invocationPlugins, JavaKind.Long);
            registerCharacterPlugins(invocationPlugins);
            registerShortPlugins(invocationPlugins);
            registerMathPlugins(Math.class, invocationPlugins);
            registerMathPlugins(StrictMath.class, invocationPlugins);
            registerBigIntegerPlugins(invocationPlugins);
            registerArraysPlugins(invocationPlugins);
            unregisterArrayFillPlugins(invocationPlugins);
            registerMemoryPlugins(invocationPlugins);
            JSGraphBuilderPlugins.registerThreadPlugins(invocationPlugins);
            JSGraphBuilderPlugins.registerCurrentIsolatePlugins(invocationPlugins);
        });
    }

    private static void registerStringPlugins(InvocationPlugins plugins) {
        final Registration r = new Registration(plugins, String.class).setAllowOverwrite(true);
        // String.equals produces ArrayEqualsNodes which have no counterpart in WASM.
        unregisterEquals(r, InvocationPlugin.Receiver.class, Object.class);

        Registration utf16r = new Registration(plugins, StringUTF16Snippets.class);
        utf16r.register(new InvocationPlugin("getChar", byte[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
                b.addPush(JavaKind.Char, new JavaReadNode(JavaKind.Char,
                                new IndexAddressNode(arg1, new LeftShiftNode(arg2, ConstantNode.forInt(1)), JavaKind.Byte),
                                NamedLocationIdentity.getArrayLocation(JavaKind.Byte), BarrierType.NONE, MemoryOrderMode.PLAIN, false));
                return true;
            }
        });
    }

    public static void registerMathPlugins(Class<?> mathImpl, InvocationPlugins plugins) {
        Registration r = new Registration(plugins, mathImpl).setAllowOverwrite(true);
        registerUnaryMath(r, "log", LOG);
        registerUnaryMath(r, "log10", LOG10);
        registerUnaryMath(r, "exp", EXP);
        registerBinaryMath(r, "pow", POW);
        registerUnaryMath(r, "sin", SIN);
        registerUnaryMath(r, "cos", COS);
        registerUnaryMath(r, "tan", TAN);

        registerMinMax(r);

        r.register(new InvocationPlugin("copySign", float.class, float.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode magnitude, ValueNode sign) {
                b.addPush(JavaKind.Float, new CopySignNode(magnitude, sign));
                return true;
            }
        });
        r.register(new InvocationPlugin("copySign", double.class, double.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode magnitude, ValueNode sign) {
                b.addPush(JavaKind.Double, new CopySignNode(magnitude, sign));
                return true;
            }
        });
        r.register(new InvocationPlugin("rint", double.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                b.push(JavaKind.Double, b.append(RoundNode.create(arg, RoundingMode.NEAREST)));
                return true;
            }
        });
        r.register(new InvocationPlugin("ceil", double.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                b.push(JavaKind.Double, b.append(RoundNode.create(arg, RoundingMode.UP)));
                return true;
            }
        });
        r.register(new InvocationPlugin("floor", double.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                b.push(JavaKind.Double, b.append(RoundNode.create(arg, RoundingMode.DOWN)));
                return true;
            }
        });

        // WASM does not have a signum operation (but it does support copySign)

        r.register(new InvocationPlugin("signum", float.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode f) {
                return false;
            }
        });
        r.register(new InvocationPlugin("signum", double.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode d) {
                return false;
            }
        });

        // WASM does not support the abs operation for integers.

        r.register(new InvocationPlugin("abs", int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                return false;
            }
        });
        r.register(new InvocationPlugin("abs", long.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                return false;
            }
        });

        /*
         * Disable multiplyHigh and unsignedMultiplyHigh as it is not yet supported.
         *
         * TODO: GR-44083
         */
        r.register(new InvocationPlugin("multiplyHigh", long.class, long.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                return false;
            }
        });

        r.register(new InvocationPlugin("unsignedMultiplyHigh", long.class, long.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                return false;
            }
        });
    }

    private static void registerUnaryMath(Registration r, String name, UnaryOperation operation) {
        r.register(new InvocationPlugin(name, double.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(UnaryMathIntrinsicNode.create(value, operation)));
                return true;
            }
        });
    }

    private static void registerBinaryMath(Registration r, String name, BinaryOperation operation) {
        r.register(new InvocationPlugin(name, double.class, double.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(JavaKind.Double, b.append(BinaryMathIntrinsicNode.create(x, y, operation)));
                return true;
            }
        });
    }

    private static void registerMinMax(Registration r) {
        JavaKind[] supportedKinds = {JavaKind.Float, JavaKind.Double};

        for (JavaKind kind : supportedKinds) {
            r.register(new InvocationPlugin("max", kind.toJavaClass(), kind.toJavaClass()) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.push(kind, b.append(MaxNode.create(x, y, NodeView.DEFAULT)));
                    return true;
                }
            });
            r.register(new InvocationPlugin("min", kind.toJavaClass(), kind.toJavaClass()) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.push(kind, b.append(MinNode.create(x, y, NodeView.DEFAULT)));
                    return true;
                }
            });
        }
    }

    public static void registerIntegerLongPlugins(InvocationPlugins plugins, JavaKind kind) {
        Class<?> declaringClass = kind.toBoxedJavaClass();
        Class<?> type = kind.toJavaClass();
        Registration r = new Registration(plugins, declaringClass).setAllowOverwrite(true);

        r.register(new InvocationPlugin("bitCount", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                ValueNode popcnt = b.add(new WasmPopcntNode(value).canonical(null));

                if (popcnt.getStackKind() == JavaKind.Long) {
                    // i64.popcnt produces an i64, but bitCount expects a 32-bit int
                    b.addPush(JavaKind.Int, b.add(new NarrowNode(popcnt, 64, 32)));
                } else {
                    b.addPush(JavaKind.Int, popcnt);
                }
                return true;
            }
        });

        /*
         * In the standard GBP, these methods produce nodes which do not have an equivalent in WASM,
         * it is easier to just lower the method implementation.
         */

        r.register(new InvocationPlugin("reverseBytes", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                return false;
            }
        });
        r.register(new InvocationPlugin("reverse", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                return false;
            }
        });
    }

    public static void registerCharacterPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Character.class).setAllowOverwrite(true);
        r.register(new InvocationPlugin("reverseBytes", char.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                /*
                 * In the standard GBP, this produces a ReverseBytesNode, for which there is no good
                 * lowering in WASM.
                 */
                return false;
            }
        });
    }

    public static void registerShortPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Short.class).setAllowOverwrite(true);
        r.register(new InvocationPlugin("reverseBytes", short.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                /*
                 * In the standard GBP, this produces a ReverseBytesNode, for which there is no good
                 * lowering in WASM.
                 */
                return false;
            }
        });
    }

    public static void registerArraysPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Arrays.class).setAllowOverwrite(true);
        // Arrays.equals produces ArrayEqualsNodes which have no counterpart in WASM.
        unregisterEquals(r, boolean[].class, boolean[].class);
        unregisterEquals(r, byte[].class, byte[].class);
        unregisterEquals(r, short[].class, short[].class);
        unregisterEquals(r, char[].class, char[].class);
        unregisterEquals(r, int[].class, int[].class);
        unregisterEquals(r, long[].class, long[].class);
    }

    /**
     * Unregisters {@code X.equals} for some type X (given by a registration).
     * <p>
     * Supports both static and non-static variants.
     *
     * @param r The registration for type {@code X}
     * @param argumentTypes Argument types for the {@code equals} method (with receiver if
     *            necessary)
     */
    private static void unregisterEquals(Registration r, Type... argumentTypes) {
        r.register(new InvocationPlugin("equals", argumentTypes) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1) {
                return false;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
                return false;
            }
        });
    }

    /**
     * Unregisters all {@link Arrays#fill} graph builder plugins. They may produce
     * {@link jdk.graal.compiler.replacements.nodes.ArrayFillNode}, for which there is no equivalent
     * in WasmLM (filling memory only works at the byte level).
     */
    public static void unregisterArrayFillPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Arrays.class).setAllowOverwrite(true);

        unregisterArrayFill(r, boolean.class);
        unregisterArrayFill(r, byte.class);
        unregisterArrayFill(r, char.class);
        unregisterArrayFill(r, short.class);
        unregisterArrayFill(r, int.class);
        unregisterArrayFill(r, float.class);
        unregisterArrayFill(r, long.class);
        unregisterArrayFill(r, double.class);
        unregisterArrayFill(r, Object.class);
    }

    /**
     * Unregisters {@link Arrays#fill} for arrays with the given component type.
     * <p>
     * Supports variants with and without and explicit range.
     *
     * @param r The registration for {@link Arrays}
     */
    private static void unregisterArrayFill(Registration r, Class<?> componentType) {
        r.register(new InvocationPlugin("fill", componentType.arrayType(), componentType) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode array, ValueNode value) {
                return false;
            }
        });

        r.register(new InvocationPlugin("fill", componentType.arrayType(), int.class, int.class, componentType) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode array, ValueNode fromIndex, ValueNode toIndex, ValueNode value) {
                return false;
            }
        });
    }

    public static void registerBigIntegerPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, BigInteger.class).setAllowOverwrite(true);
        // the upstream plugin introduces a BigIntegerMultiplyToLenNode that is not supported
        r.register(new SnippetSubstitutionInvocationPlugin<>(BigIntegerSnippets.Templates.class,
                        "implMultiplyToLen", int[].class, int.class, int[].class, int.class, int[].class) {
            @Override
            public SnippetTemplate.SnippetInfo getSnippet(BigIntegerSnippets.Templates templates) {
                return null;
            }

            @Override
            public boolean execute(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode[] args) {
                return false;
            }
        });
    }

    private static void registerMemoryPlugins(InvocationPlugins plugins) {
        Registration unmanagedMemUtil = new Registration(plugins, UnmanagedMemoryUtil.class);
        Registration javaMemUtil = new Registration(plugins, JavaMemoryUtil.class);
        registerFillPlugins(unmanagedMemUtil, javaMemUtil);
        registerRawCopyPlugins(unmanagedMemUtil, javaMemUtil);
    }

    private static void registerFillPlugins(Registration unmanagedMemUtil, Registration javaMemUtil) {
        unmanagedMemUtil.register(new FillPlugin("fill"));
        javaMemUtil.register(new FillPlugin("fill"));
    }

    /**
     * Replaces methods that fill memory with byte values with a {@link WasmMemoryFillNode}.
     */
    private static class FillPlugin extends InvocationPlugin.RequiredInvocationPlugin {

        FillPlugin(String name) {
            super(name, Pointer.class, UnsignedWord.class, byte.class);
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode to, ValueNode size, ValueNode value) {
            /*
             * TODO GR-42105 stop narrowing, the inputs should already be 32-bits with a 32-bit
             * architecture
             */
            b.add(new WasmMemoryFillNode(
                            NarrowNode.create(to, 32, NodeView.DEFAULT),
                            value,
                            NarrowNode.create(size, 32, NodeView.DEFAULT)));
            return true;
        }
    }

    private static void registerRawCopyPlugins(Registration unmanagedMemUtil, Registration javaMemUtil) {
        unmanagedMemUtil.register(new CopyPlugin("copy"));
        unmanagedMemUtil.register(new CopyPlugin("copyForward"));
        unmanagedMemUtil.register(new CopyPlugin("copyBackward"));
        unmanagedMemUtil.register(new CopyPlugin("copyWordsForward"));
        unmanagedMemUtil.register(new CopyPlugin("copyLongsForward"));
        unmanagedMemUtil.register(new CopyPlugin("copyLongsBackward"));

        javaMemUtil.register(new CopyPlugin("copyForward"));
        javaMemUtil.register(new CopyPlugin("copyBackward"));
        javaMemUtil.register(new CopyOffsetPlugin("copy"));
        javaMemUtil.register(new CopyOffsetPlugin("copyForward"));
        javaMemUtil.register(new CopyOffsetPlugin("copyBackward"));
    }

    /**
     * Replaces methods that copy data with the signature
     * {@code (Pointer from, Pointer to, UnsignedWord size} with a {@link WasmMemoryCopyNode}.
     */
    private static class CopyPlugin extends InvocationPlugin.RequiredInvocationPlugin {
        CopyPlugin(String name) {
            super(name, Pointer.class, Pointer.class, UnsignedWord.class);
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused, ValueNode from, ValueNode to, ValueNode size) {
            /*
             * TODO GR-42105 stop narrowing, the inputs should already be 32-bits with a 32-bit
             * architecture
             */
            b.add(new WasmMemoryCopyNode(
                            NarrowNode.create(to, 32, NodeView.DEFAULT),
                            NarrowNode.create(from, 32, NodeView.DEFAULT),
                            NarrowNode.create(size, 32, NodeView.DEFAULT)));
            return true;
        }
    }

    /**
     * Replaces methods that copy data with the signature
     * {@code (Object from, UnsignedWord fromOffset, Object to, UnsignedWord toOffset, UnsignedWord size}
     * with a {@link WasmMemoryCopyNode} by first adding {@code from + fromOffset} and
     * {@code to + toOffset} to get the absolute addresse to get the absolute addressess.
     */
    private static class CopyOffsetPlugin extends InvocationPlugin.RequiredInvocationPlugin {

        private final CopyPlugin copyPlugin;

        CopyOffsetPlugin(String name) {
            super(name, Object.class, UnsignedWord.class, Object.class, UnsignedWord.class, UnsignedWord.class);

            this.copyPlugin = new CopyPlugin(name);
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode from, ValueNode fromOffset, ValueNode to, ValueNode toOffset, ValueNode size) {
            WordCastNode fromUntracked = b.add(WordCastNode.objectToUntrackedPointer(from, ConfigurationValues.getWordKind()));
            WordCastNode toUntracked = b.add(WordCastNode.objectToUntrackedPointer(to, ConfigurationValues.getWordKind()));
            ValueNode fromPointer = b.add(BinaryArithmeticNode.add(fromUntracked, fromOffset));
            ValueNode toPointer = b.add(BinaryArithmeticNode.add(toUntracked, toOffset));

            return copyPlugin.apply(b, targetMethod, receiver, fromPointer, toPointer, size);
        }
    }
}
