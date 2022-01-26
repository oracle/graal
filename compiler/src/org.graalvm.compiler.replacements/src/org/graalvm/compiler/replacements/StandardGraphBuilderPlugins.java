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
package org.graalvm.compiler.replacements;

import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateReprofile;
import static jdk.vm.ci.meta.DeoptimizationAction.None;
import static jdk.vm.ci.meta.DeoptimizationReason.TransferToInterpreter;
import static org.graalvm.compiler.nodes.NamedLocationIdentity.OFF_HEAP_LOCATION;
import static org.graalvm.compiler.replacements.ArrayIndexOf.STUB_INDEX_OF_1_BYTE;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiFunction;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.calc.Condition.CanonicalizedCondition;
import org.graalvm.compiler.core.common.calc.UnsignedMath;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.type.AbstractObjectStamp;
import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Edges;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeList;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool.RoundingMode;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.BreakpointNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ProfileData.BranchProbabilityData;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.AbsNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.FloatEqualsNode;
import org.graalvm.compiler.nodes.calc.IntegerBelowNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.graalvm.compiler.nodes.calc.ReinterpretNode;
import org.graalvm.compiler.nodes.calc.RightShiftNode;
import org.graalvm.compiler.nodes.calc.RoundNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.SignumNode;
import org.graalvm.compiler.nodes.calc.SqrtNode;
import org.graalvm.compiler.nodes.calc.UnsignedDivNode;
import org.graalvm.compiler.nodes.calc.UnsignedRemNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.debug.BindToRegisterNode;
import org.graalvm.compiler.nodes.debug.BlackholeNode;
import org.graalvm.compiler.nodes.debug.ControlFlowAnchorNode;
import org.graalvm.compiler.nodes.debug.NeverStripMineNode;
import org.graalvm.compiler.nodes.debug.SideEffectNode;
import org.graalvm.compiler.nodes.debug.SpillRegistersNode;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.extended.BoxNode.TrustedBoxedValue;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode.BytecodeExceptionKind;
import org.graalvm.compiler.nodes.extended.CacheWritebackNode;
import org.graalvm.compiler.nodes.extended.CacheWritebackSyncNode;
import org.graalvm.compiler.nodes.extended.ClassIsArrayNode;
import org.graalvm.compiler.nodes.extended.GetClassNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.extended.JavaWriteNode;
import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.nodes.extended.ObjectIsArrayNode;
import org.graalvm.compiler.nodes.extended.OpaqueNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.extended.RawOrderedLoadNode;
import org.graalvm.compiler.nodes.extended.RawStoreNode;
import org.graalvm.compiler.nodes.extended.UnboxNode;
import org.graalvm.compiler.nodes.extended.UnsafeMemoryLoadNode;
import org.graalvm.compiler.nodes.extended.UnsafeMemoryStoreNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.AtomicReadAndAddNode;
import org.graalvm.compiler.nodes.java.AtomicReadAndWriteNode;
import org.graalvm.compiler.nodes.java.ClassIsAssignableFromNode;
import org.graalvm.compiler.nodes.java.DynamicNewArrayNode;
import org.graalvm.compiler.nodes.java.InstanceOfDynamicNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.RegisterFinalizerNode;
import org.graalvm.compiler.nodes.java.UnsafeCompareAndExchangeNode;
import org.graalvm.compiler.nodes.java.UnsafeCompareAndSwapNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.address.IndexAddressNode;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.ConstantReflectionUtil;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.nodes.virtual.EnsureVirtualizedNode;
import org.graalvm.compiler.replacements.nodes.ArrayEqualsNode;
import org.graalvm.compiler.replacements.nodes.MacroNode.MacroParams;
import org.graalvm.compiler.replacements.nodes.ProfileBooleanNode;
import org.graalvm.compiler.replacements.nodes.ReverseBytesNode;
import org.graalvm.compiler.replacements.nodes.VirtualizableInvokeMacroNode;
import org.graalvm.compiler.replacements.nodes.arithmetic.IntegerAddExactNode;
import org.graalvm.compiler.replacements.nodes.arithmetic.IntegerAddExactOverflowNode;
import org.graalvm.compiler.replacements.nodes.arithmetic.IntegerAddExactSplitNode;
import org.graalvm.compiler.replacements.nodes.arithmetic.IntegerExactArithmeticSplitNode;
import org.graalvm.compiler.replacements.nodes.arithmetic.IntegerMulExactNode;
import org.graalvm.compiler.replacements.nodes.arithmetic.IntegerMulExactOverflowNode;
import org.graalvm.compiler.replacements.nodes.arithmetic.IntegerMulExactSplitNode;
import org.graalvm.compiler.replacements.nodes.arithmetic.IntegerSubExactNode;
import org.graalvm.compiler.replacements.nodes.arithmetic.IntegerSubExactOverflowNode;
import org.graalvm.compiler.replacements.nodes.arithmetic.IntegerSubExactSplitNode;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.serviceprovider.SpeculationReasonGroup;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.Speculation;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;
import sun.misc.Unsafe;

/**
 * Provides non-runtime specific {@link InvocationPlugin}s.
 */
public class StandardGraphBuilderPlugins {

    public static void registerInvocationPlugins(MetaAccessProvider metaAccess,
                    SnippetReflectionProvider snippetReflection,
                    InvocationPlugins plugins,
                    Replacements replacements,
                    boolean allowDeoptimization,
                    boolean explicitUnsafeNullChecks,
                    boolean arrayEqualsSubstitution,
                    LoweringProvider lowerer) {
        registerObjectPlugins(plugins);
        registerClassPlugins(plugins);
        registerMathPlugins(plugins, allowDeoptimization, replacements, lowerer);
        registerStrictMathPlugins(plugins);
        registerUnsignedMathPlugins(plugins);
        registerStringPlugins(plugins, replacements, snippetReflection, arrayEqualsSubstitution);
        registerCharacterPlugins(plugins);
        registerShortPlugins(plugins);
        registerIntegerLongPlugins(plugins, JavaKind.Int);
        registerIntegerLongPlugins(plugins, JavaKind.Long);
        registerFloatPlugins(plugins);
        registerDoublePlugins(plugins);
        if (arrayEqualsSubstitution) {
            registerArraysPlugins(plugins, replacements);
        }
        registerArrayPlugins(plugins, replacements);
        registerUnsafePlugins(plugins, replacements, explicitUnsafeNullChecks);
        registerEdgesPlugins(metaAccess, plugins);
        registerGraalDirectivesPlugins(plugins, snippetReflection);
        registerBoxingPlugins(plugins);
        registerJMHBlackholePlugins(plugins, replacements);
        registerJFRThrowablePlugins(plugins, replacements);
        registerMethodHandleImplPlugins(plugins, replacements);
        registerPreconditionsPlugins(plugins, replacements);
        registerJcovCollectPlugins(plugins, replacements);
    }

    public static final Field STRING_VALUE_FIELD;
    private static final Field STRING_CODER_FIELD;

    static {
        Field coder = null;
        try {
            STRING_VALUE_FIELD = String.class.getDeclaredField("value");
            if (JavaVersionUtil.JAVA_SPEC > 8) {
                coder = String.class.getDeclaredField("coder");
            }
        } catch (NoSuchFieldException e) {
            throw new GraalError(e);
        }
        STRING_CODER_FIELD = coder;
    }

    private static void registerStringPlugins(InvocationPlugins plugins, Replacements replacements, SnippetReflectionProvider snippetReflection, boolean arrayEqualsSubstitution) {
        final Registration r = new Registration(plugins, String.class, replacements);
        r.register1("hashCode", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (receiver.isConstant()) {
                    String s = snippetReflection.asObject(String.class, (JavaConstant) receiver.get().asConstant());
                    if (s != null) {
                        b.addPush(JavaKind.Int, b.add(ConstantNode.forInt(s.hashCode())));
                        return true;
                    }
                }
                return false;
            }
        });
        r.register1("intern", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (receiver.isConstant()) {
                    String s = snippetReflection.asObject(String.class, (JavaConstant) receiver.get().asConstant());
                    if (s != null) {
                        JavaConstant interned = snippetReflection.forObject(s.intern());
                        b.addPush(JavaKind.Object, b.add(ConstantNode.forConstant(interned, b.getMetaAccess(), b.getGraph())));
                        return true;
                    }
                }
                return false;
            }
        });

        if (arrayEqualsSubstitution) {
            r.register2("equals", Receiver.class, Object.class, new StringEqualsInvocationPlugin());
        }
        if (JavaVersionUtil.JAVA_SPEC <= 8) {
            Registration sr = new Registration(plugins, StringSubstitutions.class);
            sr.register1("getValue", String.class, new InvocationPlugin() {
                @Override
                public boolean inlineOnly() {
                    return true;
                }

                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                    ResolvedJavaField field = b.getMetaAccess().lookupJavaField(STRING_VALUE_FIELD);
                    ValueNode object = b.nullCheckedValue(value);
                    b.addPush(JavaKind.Object, LoadFieldNode.create(b.getConstantFieldProvider(), b.getConstantReflection(), b.getMetaAccess(),
                                    b.getOptions(), b.getAssumptions(), object, field, false, false));
                    return true;
                }
            });
        } else {

            final Registration utf16r = new Registration(plugins, "java.lang.StringUTF16", replacements);
            utf16r.setAllowOverwrite(true);

            utf16r.register2("getChar", byte[].class, int.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
                    b.addPush(JavaKind.Char, new JavaReadNode(JavaKind.Char,
                                    new IndexAddressNode(arg1, new LeftShiftNode(arg2, ConstantNode.forInt(1)), JavaKind.Byte),
                                    NamedLocationIdentity.getArrayLocation(JavaKind.Byte), BarrierType.NONE, false));
                    return true;
                }
            });
            utf16r.register3("putChar", byte[].class, int.class, int.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2, ValueNode arg3) {
                    b.add(new JavaWriteNode(JavaKind.Char,
                                    new IndexAddressNode(arg1, new LeftShiftNode(arg2, ConstantNode.forInt(1)), JavaKind.Byte),
                                    NamedLocationIdentity.getArrayLocation(JavaKind.Byte), arg3, BarrierType.NONE, false));
                    return true;
                }
            });

            Registration sr = new Registration(plugins, JDK9StringSubstitutions.class);
            sr.register1("getValue", String.class, new InvocationPlugin() {
                @Override
                public boolean inlineOnly() {
                    return true;
                }

                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                    ResolvedJavaField field = b.getMetaAccess().lookupJavaField(STRING_VALUE_FIELD);
                    ValueNode object = b.nullCheckedValue(value);
                    b.addPush(JavaKind.Object, LoadFieldNode.create(b.getConstantFieldProvider(), b.getConstantReflection(), b.getMetaAccess(),
                                    b.getOptions(), b.getAssumptions(), object, field, false, false));
                    return true;
                }
            });
            sr.register1("getCoder", String.class, new InvocationPlugin() {
                @Override
                public boolean inlineOnly() {
                    return true;
                }

                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                    ResolvedJavaField field = b.getMetaAccess().lookupJavaField(STRING_CODER_FIELD);
                    b.addPush(JavaKind.Int, LoadFieldNode.create(b.getConstantFieldProvider(), b.getConstantReflection(), b.getMetaAccess(),
                                    b.getOptions(), b.getAssumptions(), value, field, false, false));
                    return true;
                }
            });
            sr.register2("getByte", byte[].class, int.class, new InvocationPlugin() {
                @Override
                public boolean inlineOnly() {
                    return true;
                }

                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
                    b.addPush(JavaKind.Byte, new JavaReadNode(JavaKind.Byte,
                                    new IndexAddressNode(arg1, arg2, JavaKind.Byte),
                                    NamedLocationIdentity.getArrayLocation(JavaKind.Byte), OnHeapMemoryAccess.BarrierType.NONE, false));
                    return true;
                }
            });
        }
    }

    public static class ArrayEqualsInvocationPlugin implements InvocationPlugin {
        private final JavaKind kind;

        public ArrayEqualsInvocationPlugin(JavaKind kind) {
            this.kind = kind;
        }

        @SuppressWarnings("try")
        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
            if (!b.canMergeIntrinsicReturns()) {
                return false;
            }
            try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                helper.emitReturnIf(b.add(new ObjectEqualsNode(arg1, arg2)), b.add(ConstantNode.forBoolean(true)), BranchProbabilityNode.SLOW_PATH_PROBABILITY);
                GuardingNode nonNullArg1guard = helper.emitReturnIf(IsNullNode.create(arg1), b.add(ConstantNode.forBoolean(false)), BranchProbabilityNode.SLOW_PATH_PROBABILITY);
                GuardingNode nonNullArg2guard = helper.emitReturnIf(IsNullNode.create(arg2), b.add(ConstantNode.forBoolean(false)), BranchProbabilityNode.SLOW_PATH_PROBABILITY);
                Stamp stamp1 = AbstractPointerStamp.pointerNonNull(arg1.stamp(NodeView.DEFAULT));
                ValueNode nonNullArg1 = b.add(new PiNode(arg1, stamp1, nonNullArg1guard.asNode()));
                ValueNode arg1Length = b.add(new ArrayLengthNode(nonNullArg1));
                Stamp stamp2 = AbstractPointerStamp.pointerNonNull(arg1.stamp(NodeView.DEFAULT));
                ValueNode nonNullArg2 = b.add(new PiNode(arg2, stamp2, nonNullArg2guard.asNode()));
                ValueNode arg2Length = b.add(new ArrayLengthNode(nonNullArg2));
                helper.emitReturnIfNot(IntegerEqualsNode.create(arg1Length, arg2Length, NodeView.DEFAULT), b.add(ConstantNode.forBoolean(false)), BranchProbabilityNode.FAST_PATH_PROBABILITY);
                helper.emitFinalReturn(JavaKind.Boolean, b.append(new ArrayEqualsNode(nonNullArg1, nonNullArg2, arg1Length, kind)));
            }
            return true;
        }
    }

    static class StringEqualsInvocationPlugin implements InvocationPlugin {

        StringEqualsInvocationPlugin() {
        }

        @SuppressWarnings("try")
        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode other) {
            if (!b.canMergeIntrinsicReturns()) {
                return false;
            }
            try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                ConstantNode trueValue = ConstantNode.forBoolean(true);
                ConstantNode falseValue = ConstantNode.forBoolean(false);

                // if (this == other) return true
                ValueNode thisString = receiver.get();
                helper.emitReturnIf(b.add(new ObjectEqualsNode(thisString, other)), trueValue, BranchProbabilityNode.SLOW_PATH_PROBABILITY);

                // if (!(other instanceof String)) return false
                TypeReference stringType = TypeReference.createTrusted(b.getAssumptions(), b.getMetaAccess().lookupJavaType(String.class));
                GuardingNode stringArg2Guard = helper.emitReturnIfNot(InstanceOfNode.create(stringType, other), b.add(falseValue),
                                BranchProbabilityNode.SLOW_PATH_PROBABILITY);
                Stamp stamp = StampFactory.objectNonNull(stringType);
                ValueNode otherString = b.add(new PiNode(other, stamp, stringArg2Guard.asNode()));

                if (JavaVersionUtil.JAVA_SPEC > 8) {
                    ResolvedJavaField coderField = b.getMetaAccess().lookupJavaField(STRING_CODER_FIELD);
                    ValueNode thisCoder = helper.loadField(thisString, coderField);
                    ValueNode thatCoder = helper.loadField(otherString, coderField);
                    // if (thisString.coder != otherString.coder) return false
                    helper.emitReturnIfNot(b.add(new IntegerEqualsNode(thisCoder, thatCoder)), falseValue, BranchProbabilityNode.SLOW_PATH_PROBABILITY);
                }

                ResolvedJavaField valueField = b.getMetaAccess().lookupJavaField(STRING_VALUE_FIELD);
                ValueNode thisValue = b.nullCheckedValue(helper.loadField(otherString, valueField));
                ValueNode thatValue = b.nullCheckedValue(helper.loadField(thisString, valueField));

                ValueNode thisLength = helper.arraylength(thisValue);
                ValueNode thatLength = helper.arraylength(thatValue);
                // if (thisString.value.length != otherString.value.length) return false
                helper.emitReturnIfNot(IntegerEqualsNode.create(thisLength, thatLength, NodeView.DEFAULT), falseValue, BranchProbabilityNode.SLOW_PATH_PROBABILITY);
                // if (length == 0) return true
                helper.emitReturnIf(IntegerEqualsNode.create(thisLength, ConstantNode.forInt(0), NodeView.DEFAULT), trueValue,
                                BranchProbabilityNode.SLOW_PATH_PROBABILITY);
                // compare the array bodies
                helper.emitFinalReturn(JavaKind.Boolean, b.append(new ArrayEqualsNode(thisValue, thatValue, thisLength.isConstant() ? thisLength : thatLength,
                                JavaVersionUtil.JAVA_SPEC > 8 ? JavaKind.Byte : JavaKind.Char)));
            }
            return true;
        }
    }

    private static void registerArraysPlugins(InvocationPlugins plugins, Replacements replacements) {
        Registration r = new Registration(plugins, Arrays.class, replacements);
        r.register2("equals", boolean[].class, boolean[].class, new ArrayEqualsInvocationPlugin(JavaKind.Boolean));
        r.register2("equals", byte[].class, byte[].class, new ArrayEqualsInvocationPlugin(JavaKind.Byte));
        r.register2("equals", short[].class, short[].class, new ArrayEqualsInvocationPlugin(JavaKind.Short));
        r.register2("equals", char[].class, char[].class, new ArrayEqualsInvocationPlugin(JavaKind.Char));
        r.register2("equals", int[].class, int[].class, new ArrayEqualsInvocationPlugin(JavaKind.Int));
        r.register2("equals", long[].class, long[].class, new ArrayEqualsInvocationPlugin(JavaKind.Long));
    }

    private static void registerArrayPlugins(InvocationPlugins plugins, Replacements replacements) {
        Registration r = new Registration(plugins, Array.class, replacements);
        r.register2("newInstance", Class.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused, ValueNode componentType, ValueNode length) {
                ValueNode componentTypeNonNull = b.nullCheckedValue(componentType);
                ValueNode lengthPositive = b.maybeEmitExplicitNegativeArraySizeCheck(length);
                b.addPush(JavaKind.Object, new DynamicNewArrayNode(componentTypeNonNull, lengthPositive, true));
                return true;
            }
        });
        r.register1("getLength", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused, ValueNode object) {
                ValueNode objectNonNull = b.nullCheckedValue(object);
                LogicNode isArray = b.add(ObjectIsArrayNode.create(objectNonNull));
                GuardingNode isArrayGuard;
                if (b.needsExplicitException()) {
                    isArrayGuard = b.emitBytecodeExceptionCheck(isArray, true, BytecodeExceptionKind.ILLEGAL_ARGUMENT_EXCEPTION_ARGUMENT_IS_NOT_AN_ARRAY);
                } else {
                    isArrayGuard = b.add(new FixedGuardNode(isArray, DeoptimizationReason.RuntimeConstraint, DeoptimizationAction.InvalidateRecompile, false));
                }

                ValueNode array;
                if (isArrayGuard != null) {
                    AbstractObjectStamp alwaysArrayStamp = ((AbstractObjectStamp) objectNonNull.stamp(NodeView.DEFAULT)).asAlwaysArray();
                    array = b.add(new PiNode(objectNonNull, alwaysArrayStamp, isArrayGuard.asNode()));
                } else {
                    array = objectNonNull;
                }
                b.addPush(JavaKind.Int, new ArrayLengthNode(array));
                return true;
            }
        });
    }

    /**
     * The intrinsic for {@link Math#sqrt(double)} is shared with {@link StrictMath#sqrt(double)}.
     *
     * @see "http://hg.openjdk.java.net/jdk/jdk/file/621efe32eb0b/src/hotspot/share/oops/method.cpp#l1504"
     */
    static final class MathSqrtPlugin implements InvocationPlugin {
        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
            b.push(JavaKind.Double, b.append(SqrtNode.create(value, NodeView.DEFAULT)));
            return true;
        }
    }

    private static Class<?> getJavaClass(JavaKind kind) {
        return kind == JavaKind.Object ? Object.class : kind.toJavaClass();
    }

    private static String getKindName(boolean isSunMiscUnsafe, JavaKind kind) {
        return (kind == JavaKind.Object && !isSunMiscUnsafe && !(JavaVersionUtil.JAVA_SPEC <= 11)) ? "Reference" : kind.name();
    }

    private static void registerUnsafePlugins(InvocationPlugins plugins, Replacements replacements, boolean explicitUnsafeNullChecks) {
        registerUnsafePlugins0(new Registration(plugins, Unsafe.class), true, explicitUnsafeNullChecks);

        JavaKind[] supportedJavaKinds = {JavaKind.Int, JavaKind.Long, JavaKind.Object};
        registerUnsafeGetAndOpPlugins(new Registration(plugins, Unsafe.class), explicitUnsafeNullChecks, supportedJavaKinds, "Object");
        registerUnsafeAtomicsPlugins(new Registration(plugins, Unsafe.class), true, explicitUnsafeNullChecks, "compareAndSwap", new String[]{""},
                        supportedJavaKinds);

        if (JavaVersionUtil.JAVA_SPEC > 8) {
            Registration r = new Registration(plugins, "jdk.internal.misc.Unsafe", replacements);

            registerUnsafePlugins0(r, false, explicitUnsafeNullChecks);
            registerUnsafeUnalignedPlugins(r, explicitUnsafeNullChecks);

            supportedJavaKinds = new JavaKind[]{JavaKind.Boolean, JavaKind.Byte, JavaKind.Char, JavaKind.Short, JavaKind.Int, JavaKind.Long, JavaKind.Object};
            registerUnsafeGetAndOpPlugins(r, explicitUnsafeNullChecks, supportedJavaKinds, JavaVersionUtil.JAVA_SPEC > 11 ? "Reference" : "Object");
            registerUnsafeAtomicsPlugins(r, false, explicitUnsafeNullChecks, "weakCompareAndSet", new String[]{"", "Acquire", "Release", "Plain"}, supportedJavaKinds);
            registerUnsafeAtomicsPlugins(r, false, explicitUnsafeNullChecks, "compareAndExchange", new String[]{"Acquire", "Release"}, supportedJavaKinds);

            supportedJavaKinds = new JavaKind[]{JavaKind.Boolean, JavaKind.Byte, JavaKind.Char, JavaKind.Short, JavaKind.Int, JavaKind.Long, JavaKind.Float, JavaKind.Double, JavaKind.Object};
            registerUnsafeAtomicsPlugins(r, false, explicitUnsafeNullChecks, "compareAndSet", new String[]{""}, supportedJavaKinds);
            registerUnsafeAtomicsPlugins(r, false, explicitUnsafeNullChecks, "compareAndExchange", new String[]{""}, supportedJavaKinds);
        }
    }

    private static void registerUnsafeAtomicsPlugins(Registration r, boolean isSunMiscUnsafe, boolean explicitUnsafeNullChecks, String casPrefix, String[] memoryOrders,
                    JavaKind[] supportedJavaKinds) {
        for (JavaKind kind : supportedJavaKinds) {
            Class<?> javaClass = getJavaClass(kind);
            String kindName = getKindName(isSunMiscUnsafe, kind);
            boolean isLogic = true;
            JavaKind returnKind = JavaKind.Boolean.getStackKind();
            if (casPrefix.startsWith("compareAndExchange")) {
                isLogic = false;
                returnKind = kind.isNumericInteger() ? kind.getStackKind() : kind;
            }
            for (String memoryOrderString : memoryOrders) {
                MemoryOrderMode memoryOrder = memoryOrderString.equals("") ? MemoryOrderMode.VOLATILE : MemoryOrderMode.valueOf(memoryOrderString.toUpperCase());
                r.register5(casPrefix + kindName + memoryOrderString, Receiver.class, Object.class, long.class, javaClass, javaClass,
                                new UnsafeCompareAndSwapPlugin(returnKind, kind, memoryOrder, isLogic, explicitUnsafeNullChecks));
            }
        }
    }

    private static void registerUnsafeUnalignedPlugins(Registration r, boolean explicitUnsafeNullChecks) {
        for (JavaKind kind : new JavaKind[]{JavaKind.Char, JavaKind.Short, JavaKind.Int, JavaKind.Long}) {
            Class<?> javaClass = kind.toJavaClass();
            r.registerOptional3("get" + kind.name() + "Unaligned", Receiver.class, Object.class, long.class, new UnsafeGetPlugin(kind, explicitUnsafeNullChecks));
            r.registerOptional4("put" + kind.name() + "Unaligned", Receiver.class, Object.class, long.class, javaClass, new UnsafePutPlugin(kind, explicitUnsafeNullChecks));
        }
    }

    private static void registerUnsafeGetAndOpPlugins(Registration r, boolean explicitUnsafeNullChecks, JavaKind[] unsafeJavaKinds, String objectKindName) {

        for (JavaKind kind : unsafeJavaKinds) {
            Class<?> javaClass = kind == JavaKind.Object ? Object.class : kind.toJavaClass();
            String kindName = kind == JavaKind.Object ? objectKindName : kind.name();
            r.register4("getAndSet" + kindName, Receiver.class, Object.class, long.class, javaClass, new UnsafeAccessPlugin(kind, explicitUnsafeNullChecks) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode object, ValueNode offset, ValueNode value) {
                    // Emits a null-check for the otherwise unused receiver
                    unsafe.get();
                    createUnsafeAccess(object, b, (obj, loc) -> new AtomicReadAndWriteNode(obj, offset, value, kind, loc));
                    return true;
                }
            });

            if (kind != JavaKind.Boolean && kind.isNumericInteger()) {
                r.register4("getAndAdd" + kindName, Receiver.class, Object.class, long.class, javaClass, new UnsafeAccessPlugin(kind, explicitUnsafeNullChecks) {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode object, ValueNode offset, ValueNode delta) {
                        // Emits a null-check for the otherwise unused receiver
                        unsafe.get();
                        createUnsafeAccess(object, b, (obj, loc) -> new AtomicReadAndAddNode(obj, offset, delta, kind, loc));
                        return true;
                    }
                });
            }
        }
    }

    private static void registerUnsafePlugins0(Registration r, boolean sunMiscUnsafe, boolean explicitUnsafeNullChecks) {
        for (JavaKind kind : JavaKind.values()) {
            if ((kind.isPrimitive() && kind != JavaKind.Void) || kind == JavaKind.Object) {
                Class<?> javaClass = kind == JavaKind.Object ? Object.class : kind.toJavaClass();
                String kindName = getKindName(sunMiscUnsafe, kind);
                String getName = "get" + kindName;
                String putName = "put" + kindName;
                // Object-based accesses
                r.register3(getName, Receiver.class, Object.class, long.class, new UnsafeGetPlugin(kind, explicitUnsafeNullChecks));
                r.register4(putName, Receiver.class, Object.class, long.class, javaClass, new UnsafePutPlugin(kind, explicitUnsafeNullChecks));
                // Volatile object-based accesses
                r.register3(getName + "Volatile", Receiver.class, Object.class, long.class, new UnsafeGetPlugin(kind, MemoryOrderMode.VOLATILE, explicitUnsafeNullChecks));
                r.register4(putName + "Volatile", Receiver.class, Object.class, long.class, javaClass, new UnsafePutPlugin(kind, MemoryOrderMode.VOLATILE, explicitUnsafeNullChecks));
                // Ordered object-based accesses
                if (sunMiscUnsafe) {
                    if (kind == JavaKind.Int || kind == JavaKind.Long || kind == JavaKind.Object) {
                        r.register4("putOrdered" + kindName, Receiver.class, Object.class, long.class, javaClass,
                                        new UnsafePutPlugin(kind, MemoryOrderMode.RELEASE, explicitUnsafeNullChecks));
                    }
                } else {
                    r.register4("put" + kindName + "Release", Receiver.class, Object.class, long.class, javaClass,
                                    new UnsafePutPlugin(kind, MemoryOrderMode.RELEASE, explicitUnsafeNullChecks));
                    r.register3("get" + kindName + "Acquire", Receiver.class, Object.class, long.class, new UnsafeGetPlugin(kind, MemoryOrderMode.ACQUIRE, explicitUnsafeNullChecks));
                    r.register4("put" + kindName + "Opaque", Receiver.class, Object.class, long.class, javaClass, new UnsafePutPlugin(kind, MemoryOrderMode.OPAQUE, explicitUnsafeNullChecks));
                    r.register3("get" + kindName + "Opaque", Receiver.class, Object.class, long.class, new UnsafeGetPlugin(kind, MemoryOrderMode.OPAQUE, explicitUnsafeNullChecks));
                }
                if (kind != JavaKind.Boolean && kind != JavaKind.Object) {
                    // Raw accesses to memory addresses
                    r.register2(getName, Receiver.class, long.class, new UnsafeGetPlugin(kind, explicitUnsafeNullChecks));
                    r.register3(putName, Receiver.class, long.class, kind.toJavaClass(), new UnsafePutPlugin(kind, explicitUnsafeNullChecks));
                }
            }
        }

        // Accesses to native memory addresses.
        r.register2("getAddress", Receiver.class, long.class, new UnsafeGetPlugin(JavaKind.Long, explicitUnsafeNullChecks));
        r.register3("putAddress", Receiver.class, long.class, long.class, new UnsafePutPlugin(JavaKind.Long, explicitUnsafeNullChecks));

        r.register1("loadFence", Receiver.class, new UnsafeFencePlugin(MembarNode.FenceKind.LOAD_ACQUIRE));
        r.register1("storeFence", Receiver.class, new UnsafeFencePlugin(MembarNode.FenceKind.STORE_RELEASE));
        r.register1("fullFence", Receiver.class, new UnsafeFencePlugin(MembarNode.FenceKind.FULL));

        if (!sunMiscUnsafe) {
            r.register2("getUncompressedObject", Receiver.class, long.class, new UnsafeGetPlugin(JavaKind.Object, explicitUnsafeNullChecks));

            if (JavaVersionUtil.JAVA_SPEC >= 14) {
                // These methods are only called if UnsafeConstants.DATA_CACHE_LINE_FLUSH_SIZE != 0
                // which implies that the current processor and OS supports writeback to memory.
                r.register2("writeback0", Receiver.class, long.class, new CacheWritebackPlugin(false));
                r.register1("writebackPreSync0", Receiver.class, new CacheWritebackPlugin(true));
                r.register1("writebackPostSync0", Receiver.class, new CacheWritebackPlugin(false));
            }
        }
    }

    private static void registerIntegerLongPlugins(InvocationPlugins plugins, JavaKind kind) {
        Class<?> declaringClass = kind.toBoxedJavaClass();
        Class<?> type = kind.toJavaClass();
        Registration r = new Registration(plugins, declaringClass);
        r.register1("reverseBytes", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(new ReverseBytesNode(value).canonical(null)));
                return true;
            }
        });
        r.register2("divideUnsigned", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode dividend, ValueNode divisor) {
                GuardingNode zeroCheck = b.maybeEmitExplicitDivisionByZeroCheck(divisor);
                b.push(kind, b.append(UnsignedDivNode.create(dividend, divisor, zeroCheck, NodeView.DEFAULT)));
                return true;
            }
        });
        r.register2("remainderUnsigned", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode dividend, ValueNode divisor) {
                GuardingNode zeroCheck = b.maybeEmitExplicitDivisionByZeroCheck(divisor);
                b.push(kind, b.append(UnsignedRemNode.create(dividend, divisor, zeroCheck, NodeView.DEFAULT)));
                return true;
            }
        });
    }

    private static void registerCharacterPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Character.class);
        r.register1("reverseBytes", char.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                // return (char) (Integer.reverse(i) >> 16);
                ReverseBytesNode reverse = b.add(new ReverseBytesNode(value));
                RightShiftNode rightShift = b.add(new RightShiftNode(reverse, b.add(ConstantNode.forInt(16))));
                ZeroExtendNode charCast = b.add(new ZeroExtendNode(b.add(new NarrowNode(rightShift, 16)), 32));
                b.push(JavaKind.Char, b.append(charCast.canonical(null)));
                return true;
            }
        });
    }

    private static void registerShortPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Short.class);
        r.register1("reverseBytes", short.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                // return (short) (Integer.reverse(i) >> 16);
                ReverseBytesNode reverse = b.add(new ReverseBytesNode(value));
                RightShiftNode rightShift = b.add(new RightShiftNode(reverse, b.add(ConstantNode.forInt(16))));
                SignExtendNode charCast = b.add(new SignExtendNode(b.add(new NarrowNode(rightShift, 16)), 32));
                b.push(JavaKind.Short, b.append(charCast.canonical(null)));
                return true;
            }
        });
    }

    private static void registerFloatPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Float.class);
        r.register1("floatToRawIntBits", float.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.append(ReinterpretNode.create(JavaKind.Int, value, NodeView.DEFAULT)));
                return true;
            }
        });
        r.register1("floatToIntBits", float.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                LogicNode notNan = b.append(FloatEqualsNode.create(value, value, NodeView.DEFAULT));
                ValueNode raw = b.append(ReinterpretNode.create(JavaKind.Int, value, NodeView.DEFAULT));
                ValueNode result = b.append(ConditionalNode.create(notNan, raw, ConstantNode.forInt(0x7fc00000), NodeView.DEFAULT));
                b.push(JavaKind.Int, result);
                return true;
            }
        });
        r.register1("intBitsToFloat", int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Float, b.append(ReinterpretNode.create(JavaKind.Float, value, NodeView.DEFAULT)));
                return true;
            }
        });
    }

    private static void registerDoublePlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Double.class);
        r.register1("doubleToRawLongBits", double.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Long, b.append(ReinterpretNode.create(JavaKind.Long, value, NodeView.DEFAULT)));
                return true;
            }
        });
        r.register1("doubleToLongBits", double.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                LogicNode notNan = b.append(FloatEqualsNode.create(value, value, NodeView.DEFAULT));
                ValueNode raw = b.append(ReinterpretNode.create(JavaKind.Long, value, NodeView.DEFAULT));
                ValueNode result = b.append(ConditionalNode.create(notNan, raw, ConstantNode.forLong(0x7ff8000000000000L), NodeView.DEFAULT));
                b.push(JavaKind.Long, result);
                return true;
            }
        });
        r.register1("longBitsToDouble", long.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(ReinterpretNode.create(JavaKind.Double, value, NodeView.DEFAULT)));
                return true;
            }
        });
    }

    public enum IntegerExactOp {
        INTEGER_ADD_EXACT,
        INTEGER_INCREMENT_EXACT,
        INTEGER_SUBTRACT_EXACT,
        INTEGER_DECREMENT_EXACT,
        INTEGER_MULTIPLY_EXACT
    }

    private static GuardingNode createIntegerExactArithmeticGuardNode(GraphBuilderContext b, ValueNode x, ValueNode y, IntegerExactOp op) {
        LogicNode overflowCheck;
        switch (op) {
            case INTEGER_ADD_EXACT:
            case INTEGER_INCREMENT_EXACT: {
                overflowCheck = new IntegerAddExactOverflowNode(x, y);
                break;
            }
            case INTEGER_SUBTRACT_EXACT:
            case INTEGER_DECREMENT_EXACT: {
                overflowCheck = new IntegerSubExactOverflowNode(x, y);
                break;
            }
            case INTEGER_MULTIPLY_EXACT: {
                overflowCheck = new IntegerMulExactOverflowNode(x, y);
                break;
            }
            default:
                throw GraalError.shouldNotReachHere("Unknown integer exact operation.");
        }
        return b.add(new FixedGuardNode(overflowCheck, DeoptimizationReason.ArithmeticException, DeoptimizationAction.InvalidateRecompile, true));
    }

    private static ValueNode createIntegerExactArithmeticNode(GraphBuilderContext b, ValueNode x, ValueNode y, IntegerExactOp op) {
        switch (op) {
            case INTEGER_ADD_EXACT:
            case INTEGER_INCREMENT_EXACT:
                return new IntegerAddExactNode(x, y, createIntegerExactArithmeticGuardNode(b, x, y, op));
            case INTEGER_SUBTRACT_EXACT:
            case INTEGER_DECREMENT_EXACT:
                return new IntegerSubExactNode(x, y, createIntegerExactArithmeticGuardNode(b, x, y, op));
            case INTEGER_MULTIPLY_EXACT:
                return new IntegerMulExactNode(x, y, createIntegerExactArithmeticGuardNode(b, x, y, op));
            default:
                throw GraalError.shouldNotReachHere("Unknown integer exact operation.");
        }
    }

    private static IntegerExactArithmeticSplitNode createIntegerExactSplit(ValueNode x, ValueNode y, AbstractBeginNode exceptionEdge, IntegerExactOp op) {
        switch (op) {
            case INTEGER_ADD_EXACT:
            case INTEGER_INCREMENT_EXACT:
                return new IntegerAddExactSplitNode(x.stamp(NodeView.DEFAULT).unrestricted(), x, y, null, exceptionEdge);
            case INTEGER_SUBTRACT_EXACT:
            case INTEGER_DECREMENT_EXACT:
                return new IntegerSubExactSplitNode(x.stamp(NodeView.DEFAULT).unrestricted(), x, y, null, exceptionEdge);
            case INTEGER_MULTIPLY_EXACT:
                return new IntegerMulExactSplitNode(x.stamp(NodeView.DEFAULT).unrestricted(), x, y, null, exceptionEdge);
            default:
                throw GraalError.shouldNotReachHere("Unknown integer exact operation.");
        }
    }

    private static void createIntegerExactOperation(GraphBuilderContext b, JavaKind kind, ValueNode x, ValueNode y, IntegerExactOp op) {
        if (b.needsExplicitException()) {
            BytecodeExceptionKind exceptionKind = kind == JavaKind.Int ? BytecodeExceptionKind.INTEGER_EXACT_OVERFLOW : BytecodeExceptionKind.LONG_EXACT_OVERFLOW;
            AbstractBeginNode exceptionEdge = b.genExplicitExceptionEdge(exceptionKind);
            IntegerExactArithmeticSplitNode split = b.addPush(kind, createIntegerExactSplit(x, y, exceptionEdge, op));
            split.setNext(b.add(new BeginNode()));
        } else {
            b.addPush(kind, createIntegerExactArithmeticNode(b, x, y, op));
        }
    }

    private static void registerMathPlugins(InvocationPlugins plugins, boolean allowDeoptimization, Replacements replacements, LoweringProvider lowerer) {
        Registration r = new Registration(plugins, Math.class, replacements);
        if (allowDeoptimization) {
            for (JavaKind kind : new JavaKind[]{JavaKind.Int, JavaKind.Long}) {
                Class<?> type = kind.toJavaClass();
                r.register1("decrementExact", type, new InvocationPlugin() {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x) {
                        ConstantNode y = b.add(ConstantNode.forIntegerKind(kind, 1));
                        createIntegerExactOperation(b, kind, x, y, IntegerExactOp.INTEGER_DECREMENT_EXACT);
                        return true;
                    }
                });

                r.register1("incrementExact", type, new InvocationPlugin() {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x) {
                        ConstantNode y = b.add(ConstantNode.forIntegerKind(kind, 1));
                        createIntegerExactOperation(b, kind, x, y, IntegerExactOp.INTEGER_INCREMENT_EXACT);
                        return true;
                    }
                });
                r.register2("addExact", type, type, new InvocationPlugin() {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                        createIntegerExactOperation(b, kind, x, y, IntegerExactOp.INTEGER_ADD_EXACT);
                        return true;
                    }
                });
                r.register2("subtractExact", type, type, new InvocationPlugin() {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                        createIntegerExactOperation(b, kind, x, y, IntegerExactOp.INTEGER_SUBTRACT_EXACT);
                        return true;
                    }
                });

                r.register2("multiplyExact", type, type, new InvocationPlugin() {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                        createIntegerExactOperation(b, kind, x, y, IntegerExactOp.INTEGER_MULTIPLY_EXACT);
                        return true;
                    }
                });
            }
        }
        r.register1("abs", Float.TYPE, new InvocationPlugin() {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Float, b.append(new AbsNode(value).canonical(null)));
                return true;
            }
        });
        r.register1("abs", Double.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(new AbsNode(value).canonical(null)));
                return true;
            }
        });
        r.register1("sqrt", Double.TYPE, new MathSqrtPlugin());

        boolean supportsRound = lowerer.supportsRounding();
        registerRound(supportsRound, r, "rint", RoundingMode.NEAREST);
        registerRound(supportsRound, r, "ceil", RoundingMode.UP);
        registerRound(supportsRound, r, "floor", RoundingMode.DOWN);

        r.register1("signum", float.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode f) {
                b.addPush(JavaKind.Float, new SignumNode(f));
                return true;
            }
        });
        r.register1("signum", double.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode d) {
                b.addPush(JavaKind.Double, new SignumNode(d));
                return true;
            }
        });
    }

    private static void registerRound(boolean supportsRound, Registration r, String name, RoundingMode mode) {
        r.registerConditional1(supportsRound, name, Double.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                b.push(JavaKind.Double, b.append(new RoundNode(arg, mode)));
                return true;
            }
        });
    }

    private static void registerStrictMathPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, StrictMath.class);
        r.register1("sqrt", Double.TYPE, new MathSqrtPlugin());
    }

    public static class UnsignedMathPlugin implements InvocationPlugin {
        private final Condition condition;

        public UnsignedMathPlugin(Condition condition) {
            this.condition = condition;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
            CanonicalizedCondition canonical = condition.canonicalize();
            StructuredGraph graph = b.getGraph();

            ValueNode lhs = canonical.mustMirror() ? y : x;
            ValueNode rhs = canonical.mustMirror() ? x : y;

            ValueNode trueValue = ConstantNode.forBoolean(!canonical.mustNegate(), graph);
            ValueNode falseValue = ConstantNode.forBoolean(canonical.mustNegate(), graph);

            LogicNode compare = CompareNode.createCompareNode(graph, b.getConstantReflection(), b.getMetaAccess(), b.getOptions(), null, canonical.getCanonicalCondition(), lhs, rhs, NodeView.DEFAULT);
            b.addPush(JavaKind.Boolean, new ConditionalNode(compare, trueValue, falseValue));
            return true;
        }
    }

    private static void registerUnsignedMathPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, UnsignedMath.class);
        r.register2("aboveThan", int.class, int.class, new UnsignedMathPlugin(Condition.AT));
        r.register2("aboveThan", long.class, long.class, new UnsignedMathPlugin(Condition.AT));
        r.register2("belowThan", int.class, int.class, new UnsignedMathPlugin(Condition.BT));
        r.register2("belowThan", long.class, long.class, new UnsignedMathPlugin(Condition.BT));
        r.register2("aboveOrEqual", int.class, int.class, new UnsignedMathPlugin(Condition.AE));
        r.register2("aboveOrEqual", long.class, long.class, new UnsignedMathPlugin(Condition.AE));
        r.register2("belowOrEqual", int.class, int.class, new UnsignedMathPlugin(Condition.BE));
        r.register2("belowOrEqual", long.class, long.class, new UnsignedMathPlugin(Condition.BE));
    }

    protected static void registerBoxingPlugins(InvocationPlugins plugins) {
        for (JavaKind kind : JavaKind.values()) {
            if (kind.isPrimitive() && kind != JavaKind.Void) {
                new BoxPlugin(kind).register(plugins);
                new UnboxPlugin(kind).register(plugins);
            }
        }
    }

    private static void registerObjectPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Object.class);
        r.register1("<init>", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean inlineOnly() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                /*
                 * Object.<init> is a common instrumentation point so only perform this rewrite if
                 * the current definition is the normal empty method with a single return bytecode.
                 * The finalizer registration will instead be performed by the BytecodeParser.
                 */
                if (targetMethod.canBeInlined() && targetMethod.getCodeSize() == 1) {
                    ValueNode object = receiver.get();
                    if (RegisterFinalizerNode.mayHaveFinalizer(object, b.getMetaAccess(), b.getAssumptions())) {
                        RegisterFinalizerNode regFin = new RegisterFinalizerNode(object);
                        b.add(regFin);
                        assert regFin.stateAfter() != null;
                    }
                    return true;
                }
                return false;
            }
        });
        r.register1("getClass", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode object = receiver.get();
                ValueNode folded = GetClassNode.tryFold(b.getMetaAccess(), b.getConstantReflection(), NodeView.DEFAULT, GraphUtil.originalValue(object, true));
                if (folded != null) {
                    b.addPush(JavaKind.Object, folded);
                } else {
                    Stamp stamp = StampFactory.objectNonNull(TypeReference.createTrusted(b.getAssumptions(), b.getMetaAccess().lookupJavaType(Class.class)));
                    b.addPush(JavaKind.Object, new GetClassNode(stamp, object));
                }
                return true;
            }
        });
    }

    private static void registerClassPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Class.class);
        r.register2("isInstance", Receiver.class, Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode object) {
                LogicNode condition = b.append(InstanceOfDynamicNode.create(b.getAssumptions(), b.getConstantReflection(), type.get(), object, false));
                b.push(JavaKind.Boolean, b.append(new ConditionalNode(condition).canonical(null)));
                return true;
            }
        });
        r.register2("isAssignableFrom", Receiver.class, Class.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode otherType) {
                ClassIsAssignableFromNode condition = b.append(new ClassIsAssignableFromNode(type.get(), b.nullCheckedValue(otherType)));
                b.push(JavaKind.Boolean, b.append(new ConditionalNode(condition).canonical(null)));
                return true;
            }
        });
        r.register1("isArray", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                LogicNode isArray = b.add(ClassIsArrayNode.create(b.getConstantReflection(), receiver.get()));
                b.addPush(JavaKind.Boolean, ConditionalNode.create(isArray, NodeView.DEFAULT));
                return true;
            }
        });

        r.register2("cast", Receiver.class, Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                b.genCheckcastDynamic(object, receiver.get());
                return true;
            }

            @Override
            public boolean inlineOnly() {
                return true;
            }
        });
    }

    /**
     * Substitutions for improving the performance of some critical methods in {@link Edges}. These
     * substitutions improve the performance by forcing the relevant methods to be inlined
     * (intrinsification being a special form of inlining) and removing a checked cast.
     */
    private static void registerEdgesPlugins(MetaAccessProvider metaAccess, InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Edges.class);
        for (Class<?> c : new Class<?>[]{Node.class, NodeList.class}) {
            r.register2("get" + c.getSimpleName() + "Unsafe", Node.class, long.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode node, ValueNode offset) {
                    ObjectStamp stamp = StampFactory.object(TypeReference.createTrusted(b.getAssumptions(), metaAccess.lookupJavaType(c)));
                    RawLoadNode value = b.add(new RawLoadNode(stamp, node, offset, LocationIdentity.any(), JavaKind.Object));
                    b.addPush(JavaKind.Object, value);
                    return true;
                }
            });
            r.register3("put" + c.getSimpleName() + "Unsafe", Node.class, long.class, c, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode node, ValueNode offset, ValueNode value) {
                    b.add(new RawStoreNode(node, offset, value, JavaKind.Object, LocationIdentity.any()));
                    return true;
                }
            });
        }
    }

    public static class BoxPlugin implements InvocationPlugin {

        private final JavaKind kind;

        BoxPlugin(JavaKind kind) {
            this.kind = kind;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
            if (b.parsingIntrinsic()) {
                ResolvedJavaMethod rootMethod = b.getGraph().method();
                if (b.getMetaAccess().lookupJavaType(BoxingSnippets.class).isAssignableFrom(rootMethod.getDeclaringClass())) {
                    // Disable invocation plugins for boxing snippets so that the
                    // original JDK methods are inlined
                    return false;
                }
            }
            ResolvedJavaType resultType = b.getMetaAccess().lookupJavaType(kind.toBoxedJavaClass());
            b.addPush(JavaKind.Object, BoxNode.create(value, resultType, kind));
            return true;
        }

        void register(InvocationPlugins plugins) {
            plugins.register(this, kind.toBoxedJavaClass(), "valueOf", kind.toJavaClass());
        }
    }

    public static class UnboxPlugin implements InvocationPlugin {

        private final JavaKind kind;

        UnboxPlugin(JavaKind kind) {
            this.kind = kind;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
            if (b.parsingIntrinsic()) {
                ResolvedJavaMethod rootMethod = b.getGraph().method();
                if (b.getMetaAccess().lookupJavaType(BoxingSnippets.class).isAssignableFrom(rootMethod.getDeclaringClass())) {
                    // Disable invocation plugins for unboxing snippets so that the
                    // original JDK methods are inlined
                    return false;
                }
            }
            ValueNode valueNode = UnboxNode.create(b.getMetaAccess(), b.getConstantReflection(), receiver.get(), kind);
            b.addPush(kind, valueNode);
            return true;
        }

        void register(InvocationPlugins plugins) {
            String name = kind.toJavaClass().getSimpleName() + "Value";
            plugins.register(this, kind.toBoxedJavaClass(), name, Receiver.class);
        }
    }

    public abstract static class UnsafeAccessPlugin implements InvocationPlugin {
        @FunctionalInterface
        public interface UnsafeNodeConstructor {
            FixedWithNextNode create(ValueNode value, LocationIdentity location);
        }

        protected final JavaKind unsafeAccessKind;
        private final boolean explicitUnsafeNullChecks;

        public UnsafeAccessPlugin(JavaKind kind, boolean explicitUnsafeNullChecks) {
            unsafeAccessKind = kind;
            this.explicitUnsafeNullChecks = explicitUnsafeNullChecks;
        }

        private static FixedWithNextNode createObjectAccessNode(ValueNode value, UnsafeNodeConstructor nodeConstructor) {
            return nodeConstructor.create(value, LocationIdentity.ANY_LOCATION);
        }

        private static FixedWithNextNode createMemoryAccessNode(StructuredGraph graph, UnsafeNodeConstructor nodeConstructor) {
            return nodeConstructor.create(ConstantNode.forLong(0L, graph), OFF_HEAP_LOCATION);
        }

        private static boolean isLoad(ValueNode node) {
            return node.getStackKind() != JavaKind.Void;
        }

        private void setAccessNodeResult(FixedWithNextNode node, GraphBuilderContext b) {
            if (isLoad(node)) {
                b.addPush(unsafeAccessKind, node);
            } else {
                b.add(node);
            }
        }

        protected final void createUnsafeAccess(ValueNode value, GraphBuilderContext b, UnsafeNodeConstructor nodeConstructor) {
            StructuredGraph graph = b.getGraph();
            graph.markUnsafeAccess();
            /* For unsafe access object pointers can only be stored in the heap */
            if (unsafeAccessKind == JavaKind.Object) {
                ValueNode object = value;
                if (explicitUnsafeNullChecks) {
                    object = b.nullCheckedValue(object);
                }
                setAccessNodeResult(createObjectAccessNode(object, nodeConstructor), b);
            } else if (StampTool.isPointerAlwaysNull(value)) {
                setAccessNodeResult(createMemoryAccessNode(graph, nodeConstructor), b);
            } else if (!explicitUnsafeNullChecks || StampTool.isPointerNonNull(value)) {
                /*
                 * If !explicitUnsafeNullChecks and value is not guaranteed to be non-null, the
                 * created node or its lowering must ensure that a null value will lead to an
                 * off-heap access using an absolute address. Really, this would be handled better
                 * in platform-dependent lowering or parser-dependent predicate rather than a
                 * boolean flag.
                 */
                setAccessNodeResult(createObjectAccessNode(value, nodeConstructor), b);
            } else {
                PiNode nonNullObject = graph.addWithoutUnique(new PiNode(value, StampFactory.objectNonNull()));
                FixedWithNextNode objectAccess = graph.add(createObjectAccessNode(nonNullObject, nodeConstructor));
                FixedWithNextNode memoryAccess = graph.add(createMemoryAccessNode(graph, nodeConstructor));
                FixedWithNextNode[] accessNodes = new FixedWithNextNode[]{objectAccess, memoryAccess};

                LogicNode condition = graph.addOrUniqueWithInputs(IsNullNode.create(value));
                IfNode ifNode = b.add(new IfNode(condition, memoryAccess, objectAccess, BranchProbabilityData.unknown()));
                nonNullObject.setGuard(ifNode.falseSuccessor());

                MergeNode merge = b.append(new MergeNode());
                for (FixedWithNextNode node : accessNodes) {
                    EndNode endNode = graph.add(new EndNode());
                    node.setNext(endNode);
                    if (node instanceof StateSplit) {
                        if (isLoad(node)) {
                            /*
                             * Temporarily push the access node so that the frame state has the node
                             * on the expression stack.
                             */
                            b.push(unsafeAccessKind, node);
                        }
                        b.setStateAfter((StateSplit) node);
                        if (isLoad(node)) {
                            ValueNode popped = b.pop(unsafeAccessKind);
                            assert popped == node;
                        }
                    }
                    merge.addForwardEnd(endNode);
                }

                if (isLoad(objectAccess)) {
                    ValuePhiNode phi = new ValuePhiNode(objectAccess.stamp(NodeView.DEFAULT), merge, accessNodes);
                    b.push(unsafeAccessKind, graph.addOrUnique(phi));
                }
                b.setStateAfter(merge);
            }
        }
    }

    public static class UnsafeGetPlugin extends UnsafeAccessPlugin {
        private final MemoryOrderMode memoryOrder;

        public UnsafeGetPlugin(JavaKind returnKind, boolean explicitUnsafeNullChecks) {
            this(returnKind, MemoryOrderMode.PLAIN, explicitUnsafeNullChecks);
        }

        public UnsafeGetPlugin(JavaKind kind, MemoryOrderMode memoryOrder, boolean explicitUnsafeNullChecks) {
            super(kind, explicitUnsafeNullChecks);
            this.memoryOrder = memoryOrder;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode address) {
            // Emits a null-check for the otherwise unused receiver
            unsafe.get();
            b.addPush(unsafeAccessKind, new UnsafeMemoryLoadNode(address, unsafeAccessKind, OFF_HEAP_LOCATION));
            b.getGraph().markUnsafeAccess();
            return true;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode object, ValueNode offset) {
            // Opaque mode does not directly impose any ordering constraints with respect to other
            // variables beyond Plain mode.
            if (memoryOrder == MemoryOrderMode.OPAQUE && StampTool.isPointerAlwaysNull(object)) {
                // OFF_HEAP_LOCATION accesses are not floatable => no membars needed for opaque.
                return apply(b, targetMethod, unsafe, offset);
            }
            // Emits a null-check for the otherwise unused receiver
            unsafe.get();
            // Note that non-ordered raw accesses can be turned into floatable field accesses.
            UnsafeNodeConstructor unsafeNodeConstructor;
            if (MemoryOrderMode.ordersMemoryAccesses(memoryOrder)) {
                unsafeNodeConstructor = (obj, loc) -> new RawOrderedLoadNode(obj, offset, unsafeAccessKind, loc, memoryOrder);
            } else {
                unsafeNodeConstructor = (obj, loc) -> new RawLoadNode(obj, offset, unsafeAccessKind, loc);
            }
            createUnsafeAccess(object, b, unsafeNodeConstructor);
            return true;
        }
    }

    public static class UnsafePutPlugin extends UnsafeAccessPlugin {
        private final MemoryOrderMode memoryOrder;

        public UnsafePutPlugin(JavaKind kind, boolean explicitUnsafeNullChecks) {
            this(kind, MemoryOrderMode.PLAIN, explicitUnsafeNullChecks);
        }

        private UnsafePutPlugin(JavaKind kind, MemoryOrderMode memoryOrder, boolean explicitUnsafeNullChecks) {
            super(kind, explicitUnsafeNullChecks);
            this.memoryOrder = memoryOrder;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode address, ValueNode value) {
            assert !memoryOrder.hasFences() : "Barriers for address based Unsafe put is not supported.";
            // Emits a null-check for the otherwise unused receiver
            unsafe.get();
            ValueNode maskedValue = b.maskSubWordValue(value, unsafeAccessKind);
            b.add(new UnsafeMemoryStoreNode(address, maskedValue, unsafeAccessKind, OFF_HEAP_LOCATION));
            b.getGraph().markUnsafeAccess();
            return true;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode object, ValueNode offset, ValueNode value) {
            // Opaque mode does not directly impose any ordering constraints with respect to other
            // variables beyond Plain mode.
            if (memoryOrder == MemoryOrderMode.OPAQUE && StampTool.isPointerAlwaysNull(object)) {
                // OFF_HEAP_LOCATION accesses are not floatable => no membars needed for opaque.
                return apply(b, targetMethod, unsafe, offset, value);
            }
            // Emits a null-check for the otherwise unused receiver
            unsafe.get();
            ValueNode maskedValue = b.maskSubWordValue(value, unsafeAccessKind);
            createUnsafeAccess(object, b, (obj, loc) -> new RawStoreNode(obj, offset, maskedValue, unsafeAccessKind, loc, true, memoryOrder));
            return true;
        }
    }

    public static class UnsafeCompareAndSwapPlugin extends UnsafeAccessPlugin {
        private final MemoryOrderMode memoryOrder;
        private final JavaKind accessKind;
        private final boolean isLogic;

        public UnsafeCompareAndSwapPlugin(JavaKind returnKind, JavaKind accessKind, MemoryOrderMode memoryOrder, boolean isLogic, boolean explicitUnsafeNullChecks) {
            super(returnKind, explicitUnsafeNullChecks);
            this.memoryOrder = memoryOrder;
            this.accessKind = accessKind;
            this.isLogic = isLogic;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode object, ValueNode offset, ValueNode expected, ValueNode newValue) {
            // Emits a null-check for the otherwise unused receiver
            unsafe.get();
            if (isLogic) {
                createUnsafeAccess(object, b, (obj, loc) -> new UnsafeCompareAndSwapNode(obj, offset, expected, newValue, accessKind, loc, memoryOrder));
            } else {
                createUnsafeAccess(object, b, (obj, loc) -> new UnsafeCompareAndExchangeNode(obj, offset, expected, newValue, accessKind, loc, memoryOrder));
            }
            return true;
        }
    }

    public static class UnsafeFencePlugin implements InvocationPlugin {

        private final MembarNode.FenceKind fence;

        public UnsafeFencePlugin(MembarNode.FenceKind fence) {
            this.fence = fence;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe) {
            // Emits a null-check for the otherwise unused receiver
            unsafe.get();
            b.add(new MembarNode(fence));
            return true;
        }
    }

    public static final class CacheWritebackPlugin implements InvocationPlugin {
        final boolean isPreSync;

        public CacheWritebackPlugin(boolean isPreSync) {
            this.isPreSync = isPreSync;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode address) {
            // Emits a null-check for the otherwise unused receiver
            unsafe.get();
            b.add(new CacheWritebackNode(address));
            return true;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe) {
            // Emits a null-check for the otherwise unused receiver
            unsafe.get();
            b.add(new CacheWritebackSyncNode(isPreSync));
            return true;
        }
    }

    private static final SpeculationReasonGroup DIRECTIVE_SPECULATIONS = new SpeculationReasonGroup("GraalDirective", BytecodePosition.class);

    static class DeoptimizePlugin implements InvocationPlugin {
        private final SnippetReflectionProvider snippetReflection;
        private final DeoptimizationAction action;
        private final DeoptimizationReason reason;
        private final Boolean withSpeculation;

        DeoptimizePlugin(SnippetReflectionProvider snippetReflection, DeoptimizationAction action, DeoptimizationReason reason, Boolean withSpeculation) {
            this.snippetReflection = snippetReflection;
            this.action = action;
            this.reason = reason;
            this.withSpeculation = withSpeculation;
        }

        @Override
        public boolean inlineOnly() {
            return true;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
            add(b, action, reason, withSpeculation);
            return true;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode actionValue, ValueNode reasonValue, ValueNode speculationValue) {
            DeoptimizationAction deoptAction = asConstant(DeoptimizationAction.class, actionValue);
            DeoptimizationReason deoptReason = asConstant(DeoptimizationReason.class, reasonValue);
            JavaConstant javaConstant = Objects.requireNonNull(speculationValue.asJavaConstant(), speculationValue + " must be a non-null compile time constant");
            if (javaConstant.getJavaKind().isObject()) {
                SpeculationReason speculationReason = snippetReflection.asObject(SpeculationReason.class, javaConstant);
                add(b, deoptAction, deoptReason, speculationReason);
            } else {
                boolean speculation = javaConstant.asInt() != 0;
                add(b, deoptAction, deoptReason, speculation);
            }
            return true;
        }

        private <T> T asConstant(Class<T> type, ValueNode value) {
            return Objects.requireNonNull(snippetReflection.asObject(type, value.asJavaConstant()), value + " must be a non-null compile time constant");
        }

        static void add(GraphBuilderContext b, DeoptimizationAction action, DeoptimizationReason reason, boolean withSpeculation) {
            SpeculationReason speculationReason = null;
            if (withSpeculation) {
                BytecodePosition pos = new BytecodePosition(null, b.getMethod(), b.bci());
                speculationReason = DIRECTIVE_SPECULATIONS.createSpeculationReason(pos);
            }
            add(b, action, reason, speculationReason);
        }

        static void add(GraphBuilderContext b, DeoptimizationAction action, DeoptimizationReason reason, SpeculationReason speculationReason) {
            Speculation speculation = SpeculationLog.NO_SPECULATION;
            if (speculationReason != null) {
                GraalError.guarantee(b.getGraph().getSpeculationLog() != null, "A speculation log is needed to use `deoptimize with speculation`");
                if (b.getGraph().getSpeculationLog().maySpeculate(speculationReason)) {
                    speculation = b.getGraph().getSpeculationLog().speculate(speculationReason);
                }
            }
            b.add(new DeoptimizeNode(action, reason, speculation));
        }
    }

    private static void registerGraalDirectivesPlugins(InvocationPlugins plugins, SnippetReflectionProvider snippetReflection) {
        Registration r = new Registration(plugins, GraalDirectives.class);
        r.register0("deoptimize", new DeoptimizePlugin(snippetReflection, None, TransferToInterpreter, false));
        r.register0("deoptimizeAndInvalidate", new DeoptimizePlugin(snippetReflection, InvalidateReprofile, TransferToInterpreter, false));
        r.register3("deoptimize", DeoptimizationAction.class, DeoptimizationReason.class, boolean.class,
                        new DeoptimizePlugin(snippetReflection, null, null, null));
        r.register3("deoptimize", DeoptimizationAction.class, DeoptimizationReason.class, SpeculationReason.class,
                        new DeoptimizePlugin(snippetReflection, null, null, null));

        r.register0("inCompiledCode", new InvocationPlugin() {
            @Override
            public boolean inlineOnly() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(true));
                return true;
            }
        });

        r.register0("inIntrinsic", new InvocationPlugin() {
            @Override
            public boolean inlineOnly() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(b.parsingIntrinsic()));
                return true;
            }
        });

        r.register0("controlFlowAnchor", new InvocationPlugin() {
            @Override
            public boolean inlineOnly() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new ControlFlowAnchorNode());
                return true;
            }
        });
        r.register0("neverStripMine", new InvocationPlugin() {
            @Override
            public boolean inlineOnly() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new NeverStripMineNode());
                return true;
            }
        });
        r.register0("sideEffect", new InvocationPlugin() {
            @Override
            public boolean inlineOnly() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new SideEffectNode());
                return true;
            }
        });
        r.register1("sideEffect", int.class, new InvocationPlugin() {
            @Override
            public boolean inlineOnly() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode a) {
                b.addPush(JavaKind.Int, new SideEffectNode(a));
                return true;
            }
        });
        r.register1("trustedBox", Object.class, new InvocationPlugin() {
            @Override
            public boolean inlineOnly() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode a) {
                b.addPush(JavaKind.Object, new TrustedBoxedValue(a));
                return true;
            }
        });
        r.register2("assumeStableDimension", Object.class, int.class, new InvocationPlugin() {
            @Override
            public boolean inlineOnly() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode array, ValueNode dimension) {
                if (array instanceof ConstantNode && b.getMetaAccess().lookupJavaType(array.asJavaConstant()).isArray()) {
                    if (dimension instanceof ConstantNode && dimension.stamp(NodeView.DEFAULT) instanceof IntegerStamp) {
                        int stableDim = dimension.asJavaConstant().asInt();
                        ConstantNode c = ConstantNode.forConstant(array.asJavaConstant(), stableDim, false, b.getMetaAccess());
                        b.addPush(JavaKind.Object, c);
                        return true;
                    }
                }
                throw GraalError.shouldNotReachHere("Illegal usage of stable array intrinsic assumeStableDimension(array, dimension): " +
                                "This compiler intrinsic can only be used iff array is a constant node (i.e., constant field) and iff " +
                                "dimension is a constant int. It will replace the constant array with a new constant that additionally sets the stable" +
                                "dimensions to the int parameter supplied.");
            }
        });
        r.register2("injectBranchProbability", double.class, boolean.class, new InvocationPlugin() {
            @Override
            public boolean inlineOnly() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode probability, ValueNode condition) {
                b.addPush(JavaKind.Boolean, new BranchProbabilityNode(probability, condition));
                return true;
            }
        });
        r.register2("injectIterationCount", double.class, boolean.class, new InvocationPlugin() {
            @Override
            public boolean inlineOnly() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode iterations, ValueNode condition) {
                // This directive has an actual definition that only works well if the bytecode
                // parser inlines it, so also provide this plugin equivalent to its definition:
                // injectBranchProbability(1. - 1. / iterations, condition)
                if (iterations.isJavaConstant()) {
                    double iterationsConstant;
                    if (iterations.stamp(NodeView.DEFAULT) instanceof IntegerStamp) {
                        iterationsConstant = iterations.asJavaConstant().asLong();
                    } else if (iterations.stamp(NodeView.DEFAULT) instanceof FloatStamp) {
                        iterationsConstant = iterations.asJavaConstant().asDouble();
                    } else {
                        return false;
                    }
                    double probability = 1. - 1. / iterationsConstant;
                    ValueNode probabilityNode = b.add(ConstantNode.forDouble(probability));
                    b.addPush(JavaKind.Boolean, new BranchProbabilityNode(probabilityNode, condition));
                    return true;
                }
                return false;
            }
        });

        InvocationPlugin blackholePlugin = new InvocationPlugin() {
            @Override
            public boolean inlineOnly() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.add(new BlackholeNode(value));
                return true;
            }
        };

        InvocationPlugin bindToRegisterPlugin = new InvocationPlugin() {
            @Override
            public boolean inlineOnly() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.add(new BindToRegisterNode(value));
                return true;
            }
        };
        for (JavaKind kind : JavaKind.values()) {
            if ((kind.isPrimitive() && kind != JavaKind.Void) || kind == JavaKind.Object) {
                Class<?> javaClass = getJavaClass(kind);
                r.register1("blackhole", javaClass, blackholePlugin);
                r.register1("bindToRegister", javaClass, bindToRegisterPlugin);

                r.register1("opaque", javaClass, new InvocationPlugin() {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                        b.addPush(kind, new OpaqueNode(value));
                        return true;
                    }
                });
            }
        }

        InvocationPlugin spillPlugin = new InvocationPlugin() {
            @Override
            public boolean inlineOnly() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new SpillRegistersNode());
                return true;
            }
        };
        r.register0("spillRegisters", spillPlugin);

        r.register1("guardingNonNull", Object.class, new InvocationPlugin() {
            @Override
            public boolean inlineOnly() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.addPush(value.getStackKind(), b.nullCheckedValue(value));
                return true;
            }
        });

        r.register1("ensureVirtualized", Object.class, new InvocationPlugin() {
            @Override
            public boolean inlineOnly() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                b.add(new EnsureVirtualizedNode(object, false));
                return true;
            }
        });
        r.register1("ensureVirtualizedHere", Object.class, new InvocationPlugin() {
            @Override
            public boolean inlineOnly() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                b.add(new EnsureVirtualizedNode(object, true));
                return true;
            }
        });
        r.register0("breakpoint", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new BreakpointNode());
                return true;
            }
        });
        r.register1("isCompilationConstant", Object.class, new InvocationPlugin() {
            @Override
            public boolean inlineOnly() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(value.isJavaConstant()));
                return true;
            }
        });
    }

    private static void registerJMHBlackholePlugins(InvocationPlugins plugins, Replacements replacements) {
        InvocationPlugin blackholePlugin = new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver blackhole, ValueNode value) {
                blackhole.get();
                b.add(new BlackholeNode(value));
                return true;
            }

            @Override
            public boolean isDecorator() {
                return true;
            }
        };
        // The purpose of this plugin is to help Blackhole.consume function mostly correctly even if
        // it's been inlined.
        String[] names = {"org.openjdk.jmh.infra.Blackhole", "org.openjdk.jmh.logic.BlackHole"};
        for (String name : names) {
            Registration r = new Registration(plugins, name, replacements);
            for (JavaKind kind : JavaKind.values()) {
                if ((kind.isPrimitive() && kind != JavaKind.Void) || kind == JavaKind.Object) {
                    Class<?> javaClass = getJavaClass(kind);
                    r.registerOptional2("consume", Receiver.class, javaClass, blackholePlugin);
                }
            }
            r.registerOptional2("consume", Receiver.class, Object[].class, blackholePlugin);
        }
    }

    private static void registerJFRThrowablePlugins(InvocationPlugins plugins, Replacements replacements) {
        Registration r = new Registration(plugins, "oracle.jrockit.jfr.jdkevents.ThrowableTracer", replacements);
        r.register2("traceThrowable", Throwable.class, String.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode throwable, ValueNode message) {
                b.add(new VirtualizableInvokeMacroNode(MacroParams.of(b, targetMethod, throwable, message)));
                return true;
            }

            @Override
            public boolean inlineOnly() {
                return true;
            }
        });
    }

    private static void registerMethodHandleImplPlugins(InvocationPlugins plugins, Replacements replacements) {
        Registration r = new Registration(plugins, "java.lang.invoke.MethodHandleImpl", replacements);
        // In later JDKs this no longer exists and the usage is replace by Class.cast which is
        // already an intrinsic
        r.registerOptional2("castReference", Class.class, Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode javaClass, ValueNode object) {
                b.genCheckcastDynamic(object, javaClass);
                return true;
            }

            @Override
            public boolean inlineOnly() {
                return true;
            }
        });
        r.register2("profileBoolean", boolean.class, int[].class, new InvocationPlugin() {
            @Override
            public boolean inlineOnly() {
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode result, ValueNode counters) {
                if (b.needsExplicitException()) {
                    return false;
                }
                if (result.isConstant()) {
                    b.push(JavaKind.Boolean, result);
                    return true;
                }
                if (counters.isConstant()) {
                    ValueNode newResult = result;
                    int[] ctrs = ConstantReflectionUtil.loadIntArrayConstant(b.getConstantReflection(), (JavaConstant) counters.asConstant(), 2);
                    if (ctrs != null && ctrs.length == 2) {
                        int falseCount = ctrs[0];
                        int trueCount = ctrs[1];
                        int totalCount = trueCount + falseCount;

                        if (totalCount == 0) {
                            b.add(new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.TransferToInterpreter));
                        } else if (falseCount == 0 || trueCount == 0) {
                            boolean expected = falseCount == 0;
                            LogicNode condition = b.add(
                                            IntegerEqualsNode.create(b.getConstantReflection(), b.getMetaAccess(), b.getOptions(), null, result, b.add(ConstantNode.forBoolean(!expected)),
                                                            NodeView.DEFAULT));
                            b.append(new FixedGuardNode(condition, DeoptimizationReason.UnreachedCode, DeoptimizationAction.InvalidateReprofile, true));
                            newResult = b.add(ConstantNode.forBoolean(expected));
                        } else {
                            // We cannot use BranchProbabilityNode here since there's no guarantee
                            // the result of MethodHandleImpl.profileBoolean() is used as the
                            // test in an `if` statement (as required by BranchProbabilityNode).
                        }
                    }
                    b.addPush(JavaKind.Boolean, newResult);
                    return true;
                }
                b.addPush(JavaKind.Boolean,
                                new ProfileBooleanNode(b.getConstantReflection(), MacroParams.of(b, targetMethod, result, counters)));
                return true;
            }
        });
        if (JavaVersionUtil.JAVA_SPEC >= 9) {
            r.register1("isCompileConstant", Object.class, new InvocationPlugin() {
                @Override
                public boolean inlineOnly() {
                    return true;
                }

                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode obj) {
                    b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(obj.isConstant()));
                    return true;
                }
            });
        }
    }

    private static void registerPreconditionsPlugins(InvocationPlugins plugins, Replacements replacements) {
        if (JavaVersionUtil.JAVA_SPEC >= 9) {
            final Registration preconditions = new Registration(plugins, "jdk.internal.util.Preconditions", replacements);
            preconditions.register3("checkIndex", int.class, int.class, BiFunction.class, new InvocationPlugin() {
                @Override
                public boolean inlineOnly() {
                    return true;
                }

                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode index, ValueNode length, ValueNode oobef) {
                    if (b.needsExplicitException()) {
                        return false;
                    } else {
                        ValueNode checkedIndex = index;
                        ValueNode checkedLength = length;
                        LogicNode lengthNegative = IntegerLessThanNode.create(length, ConstantNode.forInt(0), NodeView.DEFAULT);
                        if (!lengthNegative.isContradiction()) {
                            FixedGuardNode guard = b.append(new FixedGuardNode(lengthNegative, DeoptimizationReason.BoundsCheckException, DeoptimizationAction.InvalidateRecompile, true));
                            checkedLength = PiNode.create(length, length.stamp(NodeView.DEFAULT).improveWith(StampFactory.positiveInt()), guard);
                        }
                        LogicNode rangeCheck = IntegerBelowNode.create(index, checkedLength, NodeView.DEFAULT);
                        if (!rangeCheck.isTautology()) {
                            FixedGuardNode guard = b.append(new FixedGuardNode(rangeCheck, DeoptimizationReason.BoundsCheckException, DeoptimizationAction.InvalidateRecompile));
                            long upperBound = Math.max(0, ((IntegerStamp) checkedLength.stamp(NodeView.DEFAULT)).upperBound() - 1);
                            checkedIndex = PiNode.create(index, index.stamp(NodeView.DEFAULT).improveWith(StampFactory.forInteger(JavaKind.Int, 0, upperBound)), guard);
                        }
                        b.addPush(JavaKind.Int, checkedIndex);
                        return true;
                    }
                }
            });
        }
    }

    /**
     * Registers a plugin to ignore {@code com.sun.tdk.jcov.runtime.Collect.hit} within an
     * intrinsic.
     */
    private static void registerJcovCollectPlugins(InvocationPlugins plugins, Replacements replacements) {
        Registration r = new Registration(plugins, "com.sun.tdk.jcov.runtime.Collect", replacements);
        r.register1("hit", int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                if (b.parsingIntrinsic()) {
                    return true;
                }
                return false;
            }
        });
    }

    public static class StringLatin1IndexOfCharPlugin implements InvocationPlugin {
        @SuppressWarnings("try")
        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value, ValueNode ch, ValueNode origFromIndex) {
            if (!b.canMergeIntrinsicReturns()) {
                return false;
            }
            try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                ConstantNode zero = ConstantNode.forInt(0);
                // if (ch >>> 8 != 0) return -1
                helper.emitReturnIf(helper.ushr(ch, 8), Condition.NE, zero, ConstantNode.forInt(-1), GraalDirectives.UNLIKELY_PROBABILITY);
                ValueNode nonNullValue = b.nullCheckedValue(value);

                // if (fromIndex >= value.length) return -1
                ValueNode length = helper.arraylength(nonNullValue);
                helper.emitReturnIf(origFromIndex, Condition.GE, length, ConstantNode.forInt(-1), GraalDirectives.UNLIKELY_PROBABILITY);
                LogicNode condition = helper.createCompare(origFromIndex, CanonicalCondition.LT, zero);
                // fromIndex = max(fromIndex, 0)
                ValueNode fromIndex = ConditionalNode.create(condition, zero, origFromIndex, NodeView.DEFAULT);
                SignExtendNode toByte = b.add(new SignExtendNode(b.add(new NarrowNode(ch, JavaKind.Byte.getBitCount())), JavaKind.Int.getBitCount()));
                helper.emitFinalReturn(JavaKind.Int, new ArrayIndexOfDispatchNode(STUB_INDEX_OF_1_BYTE, JavaKind.Byte, JavaKind.Byte,
                                false, nonNullValue, length, fromIndex, toByte));
            }
            return true;
        }
    }
}
