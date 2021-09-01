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
package org.graalvm.compiler.replacements.amd64;

import static org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation.POW;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.COS;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.EXP;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG10;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.SIN;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.TAN;

import java.util.Arrays;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.PauseNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.CopySignNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess;
import org.graalvm.compiler.nodes.memory.address.IndexAddressNode;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.ArraysSubstitutions;
import org.graalvm.compiler.replacements.StringLatin1Substitutions;
import org.graalvm.compiler.replacements.StringUTF16Substitutions;
import org.graalvm.compiler.replacements.TargetGraphBuilderPlugins;
import org.graalvm.compiler.replacements.nodes.ArrayCompareToNode;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation;
import org.graalvm.compiler.replacements.nodes.BitCountNode;
import org.graalvm.compiler.replacements.nodes.FusedMultiplyAddNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class AMD64GraphBuilderPlugins implements TargetGraphBuilderPlugins {
    @Override
    public void register(Plugins plugins, Replacements replacements, Architecture architecture, boolean registerForeignCallMath, boolean useFMAIntrinsics, OptionValues options) {
        register(plugins, replacements, (AMD64) architecture, useFMAIntrinsics, options);
    }

    public static void register(Plugins plugins, Replacements replacements, AMD64 arch, boolean useFMAIntrinsics, OptionValues options) {
        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        invocationPlugins.defer(new Runnable() {
            @Override
            public void run() {
                registerThreadPlugins(invocationPlugins, arch);
                registerIntegerLongPlugins(invocationPlugins, AMD64IntegerSubstitutions.class, JavaKind.Int, arch, replacements);
                registerIntegerLongPlugins(invocationPlugins, AMD64LongSubstitutions.class, JavaKind.Long, arch, replacements);
                if (GraalOptions.EmitStringSubstitutions.getValue(options)) {
                    if (JavaVersionUtil.JAVA_SPEC <= 8) {
                        registerStringPlugins(invocationPlugins, replacements);
                    } else {
                        registerStringLatin1Plugins(invocationPlugins, replacements);
                        registerStringUTF16Plugins(invocationPlugins, replacements);
                    }
                }
                registerMathPlugins(invocationPlugins, useFMAIntrinsics, arch, replacements);
                registerArraysEqualsPlugins(invocationPlugins, replacements);
            }
        });
    }

    private static void registerThreadPlugins(InvocationPlugins plugins, AMD64 arch) {
        if (JavaVersionUtil.JAVA_SPEC > 8) {
            // Pause instruction introduced with SSE2
            assert (arch.getFeatures().contains(AMD64.CPUFeature.SSE2));
            Registration r = new Registration(plugins, Thread.class);
            r.register0("onSpinWait", new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    b.append(new PauseNode());
                    return true;
                }
            });
        }
    }

    private static void registerIntegerLongPlugins(InvocationPlugins plugins, Class<?> substituteDeclaringClass, JavaKind kind, AMD64 arch, Replacements replacements) {
        Class<?> declaringClass = kind.toBoxedJavaClass();
        Class<?> type = kind.toJavaClass();
        Registration r = new Registration(plugins, declaringClass, replacements);
        r.registerMethodSubstitution(substituteDeclaringClass, "numberOfLeadingZeros", type);
        r.registerMethodSubstitution(substituteDeclaringClass, "numberOfTrailingZeros", type);

        r.registerConditional1(arch.getFeatures().contains(AMD64.CPUFeature.POPCNT),
                        "bitCount", type, new InvocationPlugin() {
                            @Override
                            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                                b.push(JavaKind.Int, b.append(new BitCountNode(value).canonical(null)));
                                return true;
                            }
                        });
    }

    private static void registerMathPlugins(InvocationPlugins plugins, boolean useFMAIntrinsics, AMD64 arch, Replacements replacements) {
        Registration r = new Registration(plugins, Math.class, replacements);
        registerUnaryMath(r, "log", LOG);
        registerUnaryMath(r, "log10", LOG10);
        registerUnaryMath(r, "exp", EXP);
        registerBinaryMath(r, "pow", POW);
        registerUnaryMath(r, "sin", SIN);
        registerUnaryMath(r, "cos", COS);
        registerUnaryMath(r, "tan", TAN);

        if (JavaVersionUtil.JAVA_SPEC > 8) {
            registerFMA(r, useFMAIntrinsics && arch.getFeatures().contains(CPUFeature.FMA));
        }

        if (arch.getFeatures().contains(CPUFeature.AVX512VL)) {
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
    }

    private static void registerFMA(Registration r, boolean isEnabled) {
        r.registerConditional3(isEnabled, "fma",
                        Double.TYPE,
                        Double.TYPE,
                        Double.TYPE,
                        new InvocationPlugin() {
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
        r.registerConditional3(isEnabled, "fma",
                        Float.TYPE,
                        Float.TYPE,
                        Float.TYPE,
                        new InvocationPlugin() {
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

    private static void registerUnaryMath(Registration r, String name, UnaryOperation operation) {
        r.register1(name, Double.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(UnaryMathIntrinsicNode.create(value, operation)));
                return true;
            }
        });
    }

    private static void registerBinaryMath(Registration r, String name, BinaryOperation operation) {
        r.register2(name, Double.TYPE, Double.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(JavaKind.Double, b.append(BinaryMathIntrinsicNode.create(x, y, operation)));
                return true;
            }
        });
    }

    private static void registerStringPlugins(InvocationPlugins plugins, Replacements replacements) {
        if (JavaVersionUtil.JAVA_SPEC <= 8) {
            Registration r;
            r = new Registration(plugins, String.class, replacements);
            r.setAllowOverwrite(true);
            r.registerMethodSubstitution(AMD64StringSubstitutions.class, "indexOf", char[].class, int.class,
                            int.class, char[].class, int.class, int.class, int.class);
            r.registerMethodSubstitution(AMD64StringSubstitutions.class, "indexOf", Receiver.class, int.class, int.class);
            r.registerMethodSubstitution(AMD64StringSubstitutions.class, "compareTo", Receiver.class, String.class);
        }
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
        r.registerMethodSubstitution(AMD64StringLatin1Substitutions.class, "inflate", byte[].class, int.class, char[].class, int.class, int.class);
        r.registerMethodSubstitution(AMD64StringLatin1Substitutions.class, "inflate", byte[].class, int.class, byte[].class, int.class, int.class);
        r.registerMethodSubstitution(StringLatin1Substitutions.class, "indexOf", byte[].class, int.class, byte[].class, int.class, int.class);
        r.registerMethodSubstitution(StringLatin1Substitutions.class, "indexOf", byte[].class, int.class, int.class);
    }

    private static void registerStringUTF16Plugins(InvocationPlugins plugins, Replacements replacements) {
        Registration r = new Registration(plugins, "java.lang.StringUTF16", replacements);
        r.setAllowOverwrite(true);
        r.register2("compareTo", byte[].class, byte[].class, new ArrayCompareToPlugin(JavaKind.Char, JavaKind.Char));
        r.register2("compareToLatin1", byte[].class, byte[].class, new ArrayCompareToPlugin(JavaKind.Char, JavaKind.Byte, true));
        r.registerMethodSubstitution(AMD64StringUTF16Substitutions.class, "compress", char[].class, int.class, byte[].class, int.class, int.class);
        r.registerMethodSubstitution(AMD64StringUTF16Substitutions.class, "compress", byte[].class, int.class, byte[].class, int.class, int.class);
        r.registerMethodSubstitution(StringUTF16Substitutions.class, "indexOfUnsafe", byte[].class, int.class, byte[].class, int.class, int.class);
        r.registerMethodSubstitution(StringUTF16Substitutions.class, "indexOfLatin1Unsafe", byte[].class, int.class, byte[].class, int.class, int.class);
        r.registerMethodSubstitution(StringUTF16Substitutions.class, "indexOfCharUnsafe", byte[].class, int.class, int.class, int.class);
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

    private static void registerArraysEqualsPlugins(InvocationPlugins plugins, Replacements replacements) {
        Registration r = new Registration(plugins, Arrays.class, replacements);
        r.registerMethodSubstitution(ArraysSubstitutions.class, "equals", float[].class, float[].class);
        r.registerMethodSubstitution(ArraysSubstitutions.class, "equals", double[].class, double[].class);
    }

}
