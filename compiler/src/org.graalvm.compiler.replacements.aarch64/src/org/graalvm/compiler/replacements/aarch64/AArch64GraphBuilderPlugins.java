/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.aarch64;

import static org.graalvm.compiler.replacements.ArrayIndexOf.STUB_INDEX_OF_1_CHAR_COMPACT;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.COS;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.EXP;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG10;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.SIN;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.TAN;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AbsNode;
import org.graalvm.compiler.nodes.calc.CopySignNode;
import org.graalvm.compiler.nodes.calc.IntegerMulHighNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.MaxNode;
import org.graalvm.compiler.nodes.calc.MinNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess;
import org.graalvm.compiler.nodes.memory.address.IndexAddressNode;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.ArrayIndexOfDispatchNode;
import org.graalvm.compiler.replacements.StandardGraphBuilderPlugins.StringLatin1IndexOfCharPlugin;
import org.graalvm.compiler.replacements.StringLatin1Substitutions;
import org.graalvm.compiler.replacements.StringUTF16Substitutions;
import org.graalvm.compiler.replacements.TargetGraphBuilderPlugins;
import org.graalvm.compiler.replacements.nodes.ArrayCompareToNode;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.CountLeadingZerosNode;
import org.graalvm.compiler.replacements.nodes.CountTrailingZerosNode;
import org.graalvm.compiler.replacements.nodes.FusedMultiplyAddNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class AArch64GraphBuilderPlugins implements TargetGraphBuilderPlugins {
    @Override
    public void register(Plugins plugins, Replacements replacements, Architecture arch, boolean registerForeignCallMath, boolean useFMAIntrinsics, OptionValues options) {
        register(plugins, replacements, registerForeignCallMath, useFMAIntrinsics, options);
    }

    public static void register(Plugins plugins, Replacements replacements, boolean registerForeignCallMath, boolean useFMAIntrinsics, OptionValues options) {
        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        invocationPlugins.defer(new Runnable() {
            @Override
            public void run() {
                registerIntegerLongPlugins(invocationPlugins, JavaKind.Int, replacements);
                registerIntegerLongPlugins(invocationPlugins, JavaKind.Long, replacements);
                registerMathPlugins(invocationPlugins, registerForeignCallMath, useFMAIntrinsics);
                if (JavaVersionUtil.JAVA_SPEC >= 9 && GraalOptions.EmitStringSubstitutions.getValue(options)) {
                    registerStringLatin1Plugins(invocationPlugins, replacements);
                    registerStringUTF16Plugins(invocationPlugins, replacements);
                }
            }
        });
    }

    private static void registerIntegerLongPlugins(InvocationPlugins plugins, JavaKind kind, Replacements replacements) {
        Class<?> declaringClass = kind.toBoxedJavaClass();
        Class<?> type = kind.toJavaClass();
        Registration r = new Registration(plugins, declaringClass, replacements);
        r.register1("numberOfLeadingZeros", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.addPush(JavaKind.Int, CountLeadingZerosNode.create(value));
                return true;
            }
        });
        r.register1("numberOfTrailingZeros", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.addPush(JavaKind.Int, CountTrailingZerosNode.create(value));
                return true;
            }
        });
        r.register1("bitCount", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.append(new AArch64BitCountNode(value).canonical(null)));
                return true;
            }
        });
    }

    private static void registerMathPlugins(InvocationPlugins plugins, boolean registerForeignCallMath, boolean useFMAIntrinsics) {
        Registration r = new Registration(plugins, Math.class);
        if (registerForeignCallMath) {
            registerUnaryMath(r, "sin", SIN);
            registerUnaryMath(r, "cos", COS);
            registerUnaryMath(r, "tan", TAN);
            registerUnaryMath(r, "exp", EXP);
            registerUnaryMath(r, "log", LOG);
            registerUnaryMath(r, "log10", LOG10);
            r.register2("pow", Double.TYPE, Double.TYPE, new InvocationPlugin() {
                @Override
                public boolean inlineOnly() {
                    return true;
                }

                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.push(JavaKind.Double, b.append(BinaryMathIntrinsicNode.create(x, y, BinaryMathIntrinsicNode.BinaryOperation.POW)));
                    return true;
                }
            });
        }
        if (useFMAIntrinsics && JavaVersionUtil.JAVA_SPEC > 8) {
            registerFMA(r);
        }
        registerIntegerAbs(r);

        if (JavaVersionUtil.JAVA_SPEC >= 10) {
            r.register2("multiplyHigh", Long.TYPE, Long.TYPE, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.push(JavaKind.Long, b.append(new IntegerMulHighNode(x, y)));
                    return true;
                }
            });
        }
        registerMinMax(r);

        r.register2("copySign", float.class, float.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode magnitude, ValueNode sign) {
                b.addPush(JavaKind.Float, new CopySignNode(magnitude, sign));
                return true;
            }
        });
        r.register2("copySign", double.class, double.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode magnitude, ValueNode sign) {
                b.addPush(JavaKind.Double, new CopySignNode(magnitude, sign));
                return true;
            }
        });
    }

    private static void registerFMA(Registration r) {
        r.register3("fma", Double.TYPE, Double.TYPE, Double.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b,
                            ResolvedJavaMethod targetMethod,
                            Receiver receiver,
                            ValueNode na,
                            ValueNode nb,
                            ValueNode nc) {
                b.push(JavaKind.Double, b.append(new FusedMultiplyAddNode(na, nb, nc)));
                return true;
            }
        });
        r.register3("fma", Float.TYPE, Float.TYPE, Float.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b,
                            ResolvedJavaMethod targetMethod,
                            Receiver receiver,
                            ValueNode na,
                            ValueNode nb,
                            ValueNode nc) {
                b.push(JavaKind.Float, b.append(new FusedMultiplyAddNode(na, nb, nc)));
                return true;
            }
        });
    }

    private static void registerIntegerAbs(Registration r) {
        r.register1("abs", Integer.TYPE, new InvocationPlugin() {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.append(new AbsNode(value).canonical(null)));
                return true;
            }
        });
        r.register1("abs", Long.TYPE, new InvocationPlugin() {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Long, b.append(new AbsNode(value).canonical(null)));
                return true;
            }
        });
    }

    private static void registerMinMax(Registration r) {
        JavaKind[] supportedKinds = {JavaKind.Float, JavaKind.Double};

        for (JavaKind kind : supportedKinds) {
            r.register2("max", kind.toJavaClass(), kind.toJavaClass(), new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.push(kind, b.append(MaxNode.create(x, y, NodeView.DEFAULT)));
                    return true;
                }
            });
            r.register2("min", kind.toJavaClass(), kind.toJavaClass(), new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.push(kind, b.append(MinNode.create(x, y, NodeView.DEFAULT)));
                    return true;
                }
            });
        }
    }

    private static void registerUnaryMath(Registration r, String name, UnaryOperation operation) {
        r.register1(name, Double.TYPE, new InvocationPlugin() {
            @Override
            public boolean inlineOnly() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(UnaryMathIntrinsicNode.create(value, operation)));
                return true;
            }
        });
    }

    private static final class ArrayCompareToPlugin implements InvocationPlugin {
        private final JavaKind valueKind;
        private final JavaKind otherKind;
        private final boolean swapped;

        private ArrayCompareToPlugin(JavaKind valueKind, JavaKind otherKind, boolean swapped) {
            this.valueKind = valueKind;
            this.otherKind = otherKind;
            this.swapped = swapped;
        }

        private ArrayCompareToPlugin(JavaKind valueKind, JavaKind otherKind) {
            this(valueKind, otherKind, false);
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value, ValueNode other) {
            ValueNode nonNullValue = b.nullCheckedValue(value);
            ValueNode nonNullOther = b.nullCheckedValue(other);

            ValueNode valueLength = b.add(new ArrayLengthNode(nonNullValue));
            ValueNode otherLength = b.add(new ArrayLengthNode(nonNullOther));
            if (swapped) {
                /*
                 * Swapping array arguments because intrinsic expects order to be byte[]/char[] but
                 * kind arguments stay in original order.
                 */
                b.addPush(JavaKind.Int, new ArrayCompareToNode(nonNullOther, nonNullValue, otherLength, valueLength, valueKind, otherKind));
            } else {
                b.addPush(JavaKind.Int, new ArrayCompareToNode(nonNullValue, nonNullOther, valueLength, otherLength, valueKind, otherKind));
            }
            return true;
        }
    }

    private static void registerStringLatin1Plugins(InvocationPlugins plugins, Replacements replacements) {
        Registration r = new Registration(plugins, "java.lang.StringLatin1", replacements);
        r.setAllowOverwrite(true);
        r.register2("compareTo", byte[].class, byte[].class, new ArrayCompareToPlugin(JavaKind.Byte, JavaKind.Byte));
        r.register2("compareToUTF16", byte[].class, byte[].class, new ArrayCompareToPlugin(JavaKind.Byte, JavaKind.Char));
        r.registerMethodSubstitution(StringLatin1Substitutions.class, "indexOf", byte[].class, int.class, byte[].class, int.class, int.class);
        r.register3("indexOf", byte[].class, int.class, int.class, new StringLatin1IndexOfCharPlugin());
    }

    private static void registerStringUTF16Plugins(InvocationPlugins plugins, Replacements replacements) {
        Registration r = new Registration(plugins, "java.lang.StringUTF16", replacements);
        r.setAllowOverwrite(true);
        r.register2("compareTo", byte[].class, byte[].class, new ArrayCompareToPlugin(JavaKind.Char, JavaKind.Char));
        r.register2("compareToLatin1", byte[].class, byte[].class, new ArrayCompareToPlugin(JavaKind.Char, JavaKind.Byte, true));
        r.registerMethodSubstitution(StringUTF16Substitutions.class, "indexOfUnsafe", byte[].class, int.class, byte[].class, int.class, int.class);
        r.register4("indexOfCharUnsafe", byte[].class, int.class, int.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value, ValueNode ch, ValueNode fromIndex, ValueNode max) {
                ZeroExtendNode toChar = b.add(new ZeroExtendNode(b.add(new NarrowNode(ch, JavaKind.Char.getBitCount())), JavaKind.Int.getBitCount()));
                b.addPush(JavaKind.Int, new ArrayIndexOfDispatchNode(STUB_INDEX_OF_1_CHAR_COMPACT, JavaKind.Byte, JavaKind.Char, false, value, max, fromIndex,
                                toChar));
                return true;
            }
        });
        Registration r2 = new Registration(plugins, StringUTF16Substitutions.class, replacements);
        r2.register2("getChar", byte[].class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
                b.addPush(JavaKind.Char, new JavaReadNode(JavaKind.Char,
                                new IndexAddressNode(arg1, new LeftShiftNode(arg2, ConstantNode.forInt(1)), JavaKind.Byte),
                                NamedLocationIdentity.getArrayLocation(JavaKind.Byte), OnHeapMemoryAccess.BarrierType.NONE, false));
                return true;
            }
        });
    }

}
