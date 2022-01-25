/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Type;
import java.util.Arrays;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PauseNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.CopySignNode;
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
import org.graalvm.compiler.replacements.ArrayIndexOfNode;
import org.graalvm.compiler.replacements.InvocationPluginHelper;
import org.graalvm.compiler.replacements.SnippetSubstitutionInvocationPlugin;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.StandardGraphBuilderPlugins;
import org.graalvm.compiler.replacements.StandardGraphBuilderPlugins.StringLatin1IndexOfCharPlugin;
import org.graalvm.compiler.replacements.StringLatin1Snippets;
import org.graalvm.compiler.replacements.StringUTF16Snippets;
import org.graalvm.compiler.replacements.TargetGraphBuilderPlugins;
import org.graalvm.compiler.replacements.nodes.ArrayCompareToNode;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation;
import org.graalvm.compiler.replacements.nodes.BitCountNode;
import org.graalvm.compiler.replacements.nodes.CountLeadingZerosNode;
import org.graalvm.compiler.replacements.nodes.CountTrailingZerosNode;
import org.graalvm.compiler.replacements.nodes.FusedMultiplyAddNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class AMD64GraphBuilderPlugins implements TargetGraphBuilderPlugins {
    @Override
    public void register(Plugins plugins, Replacements replacements, Architecture architecture, boolean registerForeignCallMath, OptionValues options) {
        register(plugins, replacements, (AMD64) architecture, options);
    }

    public static void register(Plugins plugins, Replacements replacements, AMD64 arch, OptionValues options) {
        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        invocationPlugins.defer(new Runnable() {
            @Override
            public void run() {
                registerThreadPlugins(invocationPlugins, arch);
                registerIntegerLongPlugins(invocationPlugins, JavaKind.Int, arch, replacements);
                registerIntegerLongPlugins(invocationPlugins, JavaKind.Long, arch, replacements);
                if (GraalOptions.EmitStringSubstitutions.getValue(options)) {
                    registerStringLatin1Plugins(invocationPlugins, replacements);
                    registerStringUTF16Plugins(invocationPlugins, replacements);
                }
                registerMathPlugins(invocationPlugins, arch, replacements);
                registerArraysEqualsPlugins(invocationPlugins, replacements);
            }
        });
    }

    private static void registerThreadPlugins(InvocationPlugins plugins, AMD64 arch) {
        // Pause instruction introduced with SSE2
        assert (arch.getFeatures().contains(AMD64.CPUFeature.SSE2));
        Registration r = new Registration(plugins, Thread.class);
        r.register(new InvocationPlugin("onSpinWait") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.append(new PauseNode());
                return true;
            }
        });
    }

    private static void registerIntegerLongPlugins(InvocationPlugins plugins, JavaKind kind, AMD64 arch, Replacements replacements) {
        Class<?> declaringClass = kind.toBoxedJavaClass();
        Class<?> type = kind.toJavaClass();
        Registration r = new Registration(plugins, declaringClass, replacements);
        r.register(new InvocationPlugin("numberOfLeadingZeros", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                b.addPush(JavaKind.Int, CountLeadingZerosNode.create(arg));
                return true;
            }
        });
        r.register(new InvocationPlugin("numberOfTrailingZeros", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                b.addPush(JavaKind.Int, CountTrailingZerosNode.create(arg));
                return true;
            }
        });

        r.registerConditional(arch.getFeatures().contains(CPUFeature.POPCNT), new InvocationPlugin("bitCount", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.append(new BitCountNode(value).canonical(null)));
                return true;
            }
        });
    }

    private static void registerMathPlugins(InvocationPlugins plugins, AMD64 arch, Replacements replacements) {
        Registration r = new Registration(plugins, Math.class, replacements);
        registerUnaryMath(r, "log", LOG);
        registerUnaryMath(r, "log10", LOG10);
        registerUnaryMath(r, "exp", EXP);
        registerBinaryMath(r, "pow", POW);
        registerUnaryMath(r, "sin", SIN);
        registerUnaryMath(r, "cos", COS);
        registerUnaryMath(r, "tan", TAN);

        registerFMA(r, arch);
        registerMinMax(r, arch);

        r.registerConditional(arch.getFeatures().contains(CPUFeature.AVX512VL), new InvocationPlugin("copySign", float.class, float.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode magnitude, ValueNode sign) {
                b.addPush(JavaKind.Float, new CopySignNode(magnitude, sign));
                return true;
            }
        });
        r.registerConditional(arch.getFeatures().contains(CPUFeature.AVX512VL), new InvocationPlugin("copySign", double.class, double.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode magnitude, ValueNode sign) {
                b.addPush(JavaKind.Double, new CopySignNode(magnitude, sign));
                return true;
            }
        });
    }

    private static void registerFMA(Registration r, AMD64 arch) {
        r.registerConditional(arch.getFeatures().contains(CPUFeature.FMA), new InvocationPlugin("fma", double.class, double.class, double.class) {
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
        r.registerConditional(arch.getFeatures().contains(CPUFeature.FMA), new InvocationPlugin("fma", float.class, float.class, float.class) {
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

    private static void registerMinMax(Registration r, AMD64 arch) {
        JavaKind[] supportedMinMaxKinds = {JavaKind.Float, JavaKind.Double};
        for (JavaKind kind : supportedMinMaxKinds) {
            r.registerConditional(arch.getFeatures().contains(CPUFeature.AVX), new InvocationPlugin("max", kind.toJavaClass(), kind.toJavaClass()) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.push(kind, b.append(MaxNode.create(x, y, NodeView.DEFAULT)));
                    return true;
                }
            });
            r.registerConditional(arch.getFeatures().contains(CPUFeature.AVX), new InvocationPlugin("min", kind.toJavaClass(), kind.toJavaClass()) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.push(kind, b.append(MinNode.create(x, y, NodeView.DEFAULT)));
                    return true;
                }
            });
        }
    }

    private static final class ArrayCompareToPlugin extends InvocationPlugin {
        private final JavaKind valueKind;
        private final JavaKind otherKind;
        private final boolean swapped;

        private ArrayCompareToPlugin(JavaKind valueKind, JavaKind otherKind, boolean swapped, String name, Type... argumentTypes) {
            super(name, argumentTypes);
            this.valueKind = valueKind;
            this.otherKind = otherKind;
            this.swapped = swapped;
        }

        private ArrayCompareToPlugin(JavaKind valueKind, JavaKind otherKind, String name, Type... argumentTypes) {
            this(valueKind, otherKind, false, name, argumentTypes);
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
        r.register(new ArrayCompareToPlugin(JavaKind.Byte, JavaKind.Byte, "compareTo", byte[].class, byte[].class));
        r.register(new ArrayCompareToPlugin(JavaKind.Byte, JavaKind.Char, "compareToUTF16", byte[].class, byte[].class));
        r.register(new InvocationPlugin("inflate", byte[].class, int.class, byte[].class, int.class, int.class) {
            @SuppressWarnings("try")
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode src, ValueNode srcIndex, ValueNode dest, ValueNode destIndex, ValueNode len) {
                // @formatter:off
                //         if (injectBranchProbability(SLOWPATH_PROBABILITY, len < 0) ||
                //                        injectBranchProbability(SLOWPATH_PROBABILITY, srcIndex < 0) ||
                //                        injectBranchProbability(SLOWPATH_PROBABILITY, srcIndex + len > src.length) ||
                //                        injectBranchProbability(SLOWPATH_PROBABILITY, destIndex < 0) ||
                //                        injectBranchProbability(SLOWPATH_PROBABILITY, destIndex * 2 + len * 2 > dest.length)) {
                //            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
                //        }
                //
                //        // Offset calc. outside of the actual intrinsic.
                //        Pointer srcPointer = Word.objectToTrackedPointer(src).add(byteArrayBaseOffset(INJECTED)).add(srcIndex * byteArrayIndexScale(INJECTED));
                //        Pointer destPointer = Word.objectToTrackedPointer(dest).add(byteArrayBaseOffset(INJECTED)).add(destIndex * 2 * byteArrayIndexScale(INJECTED));
                //        AMD64StringLatin1InflateNode.inflate(srcPointer, destPointer, len, JavaKind.Byte);
                // @formatter:on
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    helper.intrinsicRangeCheck(len, Condition.LT, ConstantNode.forInt(0));

                    helper.intrinsicRangeCheck(srcIndex, Condition.LT, ConstantNode.forInt(0));
                    ValueNode srcLength = helper.length(b.nullCheckedValue(src));
                    helper.intrinsicRangeCheck(helper.add(srcIndex, len), Condition.GT, srcLength);

                    ValueNode scaledDestIndex = helper.scale(destIndex, JavaKind.Char);
                    helper.intrinsicRangeCheck(scaledDestIndex, Condition.LT, ConstantNode.forInt(0));
                    ValueNode end = helper.add(scaledDestIndex, helper.scale(len, JavaKind.Char));
                    ValueNode destLength = helper.length(b.nullCheckedValue(dest));
                    helper.intrinsicRangeCheck(end, Condition.GT, destLength);

                    ValueNode srcPointer = helper.arrayElementPointer(src, JavaKind.Byte, srcIndex);
                    ValueNode destPointer = helper.arrayElementPointer(dest, JavaKind.Byte, scaledDestIndex);
                    b.add(new AMD64StringLatin1InflateNode(srcPointer, destPointer, len, JavaKind.Byte));
                }
                return true;
            }
        });
        r.register(new InvocationPlugin("inflate", byte[].class, int.class, char[].class, int.class, int.class) {
            @SuppressWarnings("try")
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode src, ValueNode srcIndex, ValueNode dest, ValueNode destIndex, ValueNode len) {
                // @formatter:off
                //         if (injectBranchProbability(SLOWPATH_PROBABILITY, len < 0) ||
                //                        injectBranchProbability(SLOWPATH_PROBABILITY, srcIndex < 0) ||
                //                        injectBranchProbability(SLOWPATH_PROBABILITY, srcIndex + len > src.length) ||
                //                        injectBranchProbability(SLOWPATH_PROBABILITY, destIndex < 0) ||
                //                        injectBranchProbability(SLOWPATH_PROBABILITY, destIndex + len > dest.length)) {
                //            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
                //        }
                //
                //        // Offset calc. outside of the actual intrinsic.
                //        Pointer srcPointer = Word.objectToTrackedPointer(src).add(byteArrayBaseOffset(INJECTED)).add(srcIndex * byteArrayIndexScale(INJECTED));
                //        Pointer destPointer = Word.objectToTrackedPointer(dest).add(charArrayBaseOffset(INJECTED)).add(destIndex * charArrayIndexScale(INJECTED));
                //        AMD64StringLatin1InflateNode.inflate(srcPointer, destPointer, len, JavaKind.Char);
                // @formatter:on
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    helper.intrinsicRangeCheck(len, Condition.LT, ConstantNode.forInt(0));

                    helper.intrinsicRangeCheck(srcIndex, Condition.LT, ConstantNode.forInt(0));
                    ValueNode srcLength = helper.length(b.nullCheckedValue(src));
                    helper.intrinsicRangeCheck(helper.add(srcIndex, len), Condition.GT, srcLength);

                    helper.intrinsicRangeCheck(destIndex, Condition.LT, ConstantNode.forInt(0));
                    ValueNode end = helper.add(destIndex, len);
                    ValueNode destLength = helper.length(b.nullCheckedValue(dest));
                    helper.intrinsicRangeCheck(end, Condition.GT, destLength);

                    ValueNode srcPointer = helper.arrayElementPointer(src, JavaKind.Byte, srcIndex);
                    ValueNode destPointer = helper.arrayElementPointer(dest, JavaKind.Char, destIndex);
                    b.add(new AMD64StringLatin1InflateNode(srcPointer, destPointer, len, JavaKind.Char));
                }
                return true;
            }
        });

        r.register(new SnippetSubstitutionInvocationPlugin(false, "indexOf", byte[].class, int.class, byte[].class, int.class, int.class) {
            StringLatin1Snippets.Templates templates;

            @Override
            public SnippetTemplate.SnippetInfo getSnippet() {
                return getTemplates().indexOf;
            }

            @Override
            public StringLatin1Snippets.Templates getTemplates() {
                if (templates == null) {
                    templates = replacements.getSnippetTemplateCache(StringLatin1Snippets.Templates.class);
                    assert templates != null;
                }
                return templates;
            }
        });
        r.register(new StringLatin1IndexOfCharPlugin());
    }

    private static void registerStringUTF16Plugins(InvocationPlugins plugins, Replacements replacements) {

        Registration r = new Registration(plugins, "java.lang.StringUTF16", replacements);
        r.setAllowOverwrite(true);
        r.register(new ArrayCompareToPlugin(JavaKind.Char, JavaKind.Char, "compareTo", byte[].class, byte[].class));
        r.register(new ArrayCompareToPlugin(JavaKind.Char, JavaKind.Byte, true, "compareToLatin1", byte[].class, byte[].class));
        r.register(new InvocationPlugin("compress", byte[].class, int.class, byte[].class, int.class, int.class) {
            @SuppressWarnings("try")
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode src, ValueNode srcIndex, ValueNode dest, ValueNode destIndex, ValueNode len) {
                // @formatter:off
                //         if (injectBranchProbability(SLOWPATH_PROBABILITY, len < 0) ||
                //                        injectBranchProbability(SLOWPATH_PROBABILITY, srcIndex < 0) ||
                //                        injectBranchProbability(SLOWPATH_PROBABILITY, srcIndex + len > src.length >> 1) ||
                //                        injectBranchProbability(SLOWPATH_PROBABILITY, destIndex < 0) ||
                //                        injectBranchProbability(SLOWPATH_PROBABILITY, destIndex + len > dest.length)) {
                //            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
                //        }
                //
                //        Pointer srcPointer = Word.objectToTrackedPointer(src).add(byteArrayBaseOffset(INJECTED)).add(srcIndex * 2 * byteArrayIndexScale(INJECTED));
                //        Pointer destPointer = Word.objectToTrackedPointer(dest).add(byteArrayBaseOffset(INJECTED)).add(destIndex * byteArrayIndexScale(INJECTED));
                //        return AMD64StringUTF16CompressNode.compress(srcPointer, destPointer, len, JavaKind.Byte);
                // @formatter:on

                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    helper.intrinsicRangeCheck(len, Condition.LT, ConstantNode.forInt(0));

                    ValueNode scaledSrcIndex = helper.scale(srcIndex, JavaKind.Char);
                    helper.intrinsicRangeCheck(scaledSrcIndex, Condition.LT, ConstantNode.forInt(0));
                    ValueNode end = helper.add(scaledSrcIndex, helper.scale(len, JavaKind.Char));
                    ValueNode srcLength = helper.length(b.nullCheckedValue(src));
                    helper.intrinsicRangeCheck(end, Condition.GT, srcLength);

                    helper.intrinsicRangeCheck(destIndex, Condition.LT, ConstantNode.forInt(0));
                    ValueNode destLength = helper.length(b.nullCheckedValue(dest));
                    helper.intrinsicRangeCheck(helper.add(destIndex, len), Condition.GT, destLength);

                    ValueNode srcPointer = helper.arrayElementPointer(src, JavaKind.Byte, scaledSrcIndex);
                    ValueNode destPointer = helper.arrayElementPointer(dest, JavaKind.Byte, destIndex);
                    b.addPush(JavaKind.Int, new AMD64StringUTF16CompressNode(srcPointer, destPointer, len, JavaKind.Byte));
                }
                return true;
            }
        });
        r.register(new InvocationPlugin("compress", char[].class, int.class, byte[].class, int.class, int.class) {
            @SuppressWarnings("try")
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode src, ValueNode srcIndex, ValueNode dest, ValueNode destIndex, ValueNode len) {
                // @formatter:off
                //         if (injectBranchProbability(SLOWPATH_PROBABILITY, len < 0) ||
                //                        injectBranchProbability(SLOWPATH_PROBABILITY, srcIndex < 0) ||
                //                        injectBranchProbability(SLOWPATH_PROBABILITY, srcIndex + len > src.length) ||
                //                        injectBranchProbability(SLOWPATH_PROBABILITY, destIndex < 0) ||
                //                        injectBranchProbability(SLOWPATH_PROBABILITY, destIndex + len > dest.length)) {
                //            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
                //        }
                //
                //        Pointer srcPointer = Word.objectToTrackedPointer(src).add(charArrayBaseOffset(INJECTED)).add(srcIndex * charArrayIndexScale(INJECTED));
                //        Pointer destPointer = Word.objectToTrackedPointer(dest).add(byteArrayBaseOffset(INJECTED)).add(destIndex * byteArrayIndexScale(INJECTED));
                //        return AMD64StringUTF16CompressNode.compress(srcPointer, destPointer, len, JavaKind.Char);
                // @formatter:on
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    helper.intrinsicRangeCheck(len, Condition.LT, ConstantNode.forInt(0));

                    helper.intrinsicRangeCheck(srcIndex, Condition.LT, ConstantNode.forInt(0));
                    ValueNode end = helper.add(srcIndex, len);
                    ValueNode srcLength = helper.length(b.nullCheckedValue(src));
                    helper.intrinsicRangeCheck(end, Condition.GT, srcLength);

                    helper.intrinsicRangeCheck(destIndex, Condition.LT, ConstantNode.forInt(0));
                    ValueNode destLength = helper.length(b.nullCheckedValue(dest));
                    helper.intrinsicRangeCheck(helper.add(destIndex, len), Condition.GT, destLength);

                    ValueNode srcPointer = helper.arrayElementPointer(src, JavaKind.Char, srcIndex);
                    ValueNode destPointer = helper.arrayElementPointer(dest, JavaKind.Byte, destIndex);
                    b.addPush(JavaKind.Int, new AMD64StringUTF16CompressNode(srcPointer, destPointer, len, JavaKind.Char));
                }
                return true;
            }
        });

        r.register(new SnippetSubstitutionInvocationPlugin(false, "indexOfUnsafe", byte[].class, int.class, byte[].class, int.class, int.class) {
            StringUTF16Snippets.Templates templates;

            @Override
            public SnippetTemplate.SnippetInfo getSnippet() {
                return getTemplates().indexOfUnsafe;
            }

            @Override
            public StringUTF16Snippets.Templates getTemplates() {
                if (templates == null) {
                    templates = replacements.getSnippetTemplateCache(StringUTF16Snippets.Templates.class);
                    assert templates != null;
                }
                return templates;
            }
        });
        r.register(new SnippetSubstitutionInvocationPlugin(false, "indexOfLatin1Unsafe", byte[].class, int.class, byte[].class, int.class, int.class) {
            StringUTF16Snippets.Templates templates;

            @Override
            public SnippetTemplate.SnippetInfo getSnippet() {
                return getTemplates().indexOfLatin1Unsafe;
            }

            @Override
            public StringUTF16Snippets.Templates getTemplates() {
                if (templates == null) {
                    templates = replacements.getSnippetTemplateCache(StringUTF16Snippets.Templates.class);
                    assert templates != null;
                }
                return templates;
            }
        });
        r.register(new InvocationPlugin("indexOfCharUnsafe", byte[].class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value, ValueNode ch, ValueNode fromIndex, ValueNode max) {
                ZeroExtendNode toChar = b.add(new ZeroExtendNode(b.add(new NarrowNode(ch, JavaKind.Char.getBitCount())), JavaKind.Int.getBitCount()));
                b.addPush(JavaKind.Int, new ArrayIndexOfNode(JavaKind.Byte, JavaKind.Char, false, false, value, ConstantNode.forLong(0), max, fromIndex, toChar));
                return true;
            }
        });
        Registration r2 = new Registration(plugins, StringUTF16Snippets.class, replacements);
        r2.register(new InvocationPlugin("getChar", byte[].class, int.class) {
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
        r.register(new StandardGraphBuilderPlugins.ArrayEqualsInvocationPlugin(JavaKind.Float, float[].class, float[].class));
        r.register(new StandardGraphBuilderPlugins.ArrayEqualsInvocationPlugin(JavaKind.Double, double[].class, double[].class));
    }
}
