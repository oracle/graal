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
package org.graalvm.compiler.truffle.compiler.substitutions;

import static java.lang.Character.toUpperCase;
import static org.graalvm.compiler.replacements.PEGraphDecoder.Options.MaximumLoopExplosionCount;
import static org.graalvm.compiler.truffle.common.TruffleCompilerRuntime.getRuntime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool.RoundingMode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConditionAnchorNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.DynamicPiNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiArrayNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.IntegerMulHighNode;
import org.graalvm.compiler.nodes.calc.RoundNode;
import org.graalvm.compiler.nodes.debug.BlackholeNode;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.GuardedUnsafeLoadNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.extended.RawStoreNode;
import org.graalvm.compiler.nodes.extended.UnsafeAccessNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.ResolvedJavaSymbol;
import org.graalvm.compiler.nodes.java.InstanceOfDynamicNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.virtual.EnsureVirtualizedNode;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.nodes.arithmetic.UnsignedMulHighNode;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.TruffleDebugJavaMethod;
import org.graalvm.compiler.truffle.compiler.PerformanceInformationHandler;
import org.graalvm.compiler.truffle.compiler.nodes.IsCompilationConstantNode;
import org.graalvm.compiler.truffle.compiler.nodes.ObjectLocationIdentity;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleAssumption;
import org.graalvm.compiler.truffle.compiler.nodes.asserts.NeverPartOfCompilationNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.AllowMaterializeNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.ForceMaterializeNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.NewFrameNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.VirtualFrameAccessType;
import org.graalvm.compiler.truffle.compiler.nodes.frame.VirtualFrameClearNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.VirtualFrameCopyNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.VirtualFrameGetNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.VirtualFrameGetTagNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.VirtualFrameIsNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.VirtualFrameSetNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.VirtualFrameSwapNode;
import org.graalvm.compiler.truffle.compiler.phases.TruffleSafepointInsertionPhase;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.PerformanceWarningKind;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog.Speculation;

/**
 * Provides {@link InvocationPlugin}s for Truffle classes.
 */
public class TruffleGraphBuilderPlugins {

    public static void registerInvocationPlugins(InvocationPlugins plugins, boolean canDelayIntrinsification, Providers providers, KnownTruffleTypes types) {
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        registerObjectsPlugins(plugins, metaAccess);
        registerOptimizedAssumptionPlugins(plugins, metaAccess, types);
        registerExactMathPlugins(plugins, providers.getReplacements(), providers.getLowerer(), metaAccess);
        registerCompilerDirectivesPlugins(plugins, metaAccess, canDelayIntrinsification);
        registerCompilerAssertsPlugins(plugins, metaAccess, canDelayIntrinsification);
        EconomicSet<ResolvedJavaType> primitiveBoxTypes = EconomicSet.create();
        for (JavaKind kind : new JavaKind[]{JavaKind.Boolean, JavaKind.Byte, JavaKind.Char, JavaKind.Double, JavaKind.Float, JavaKind.Int, JavaKind.Long, JavaKind.Short}) {
            ResolvedJavaType boxingType = metaAccess.lookupJavaType(kind.toBoxedJavaClass());
            assert boxingType != null;
            primitiveBoxTypes.add(boxingType);
        }
        registerOptimizedCallTargetPlugins(plugins, metaAccess, canDelayIntrinsification, types, primitiveBoxTypes);
        registerFrameWithoutBoxingPlugins(plugins, metaAccess, canDelayIntrinsification, providers.getConstantReflection(), types, primitiveBoxTypes);
        registerTruffleSafepointPlugins(plugins, metaAccess, canDelayIntrinsification);
        registerNodePlugins(plugins, metaAccess, canDelayIntrinsification, providers.getConstantReflection(), types);
    }

    private static void registerTruffleSafepointPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess, boolean canDelayIntrinsification) {
        final ResolvedJavaType truffleSafepoint = getRuntime().resolveType(metaAccess, "com.oracle.truffle.api.TruffleSafepoint");
        Registration r = new Registration(plugins, new ResolvedJavaSymbol(truffleSafepoint));
        r.register1("poll", com.oracle.truffle.api.nodes.Node.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                if (arg.isConstant()) {
                    assert TruffleSafepointInsertionPhase.allowsSafepoints(b.getGraph()) : "TruffleSafepoint.poll only expected to be removed in Truffle compilations.";
                    return true;
                } else if (canDelayIntrinsification) {
                    return false;
                } else {
                    throw failPEConstant(b, arg);
                }
            }
        });
    }

    private static void registerObjectsPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess) {
        ResolvedJavaType objectsType = getRuntime().resolveType(metaAccess, "java.util.Objects");
        Registration r = new Registration(plugins, new ResolvedJavaSymbol(objectsType));
        InvocationPlugin requireNonNullPlugin = new InvocationPlugin() {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                ValueNode nullChecked = b.nullCheckedValue(arg);
                b.addPush(JavaKind.Object, nullChecked);
                return true;
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode obj, ValueNode msg) {
                return apply(b, targetMethod, receiver, obj);
            }
        };
        r.register1("requireNonNull", Object.class, requireNonNullPlugin);
        r.register2("requireNonNull", Object.class, String.class, requireNonNullPlugin);
        r.register2("requireNonNull", Object.class, Supplier.class, requireNonNullPlugin);
    }

    public static void registerOptimizedAssumptionPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess, KnownTruffleTypes types) {
        ResolvedJavaType optimizedAssumptionType = getRuntime().resolveType(metaAccess, "org.graalvm.compiler.truffle.runtime.OptimizedAssumption");
        Registration r = new Registration(plugins, new ResolvedJavaSymbol(optimizedAssumptionType));
        InvocationPlugin plugin = new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (receiver.isConstant() && b.getAssumptions() != null) {
                    JavaConstant assumption = (JavaConstant) receiver.get().asConstant();
                    if (b.getConstantReflection().readFieldValue(types.fieldOptimizedAssumptionIsValid, assumption).asBoolean()) {
                        if (targetMethod.getName().equals("isValid")) {
                            b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(true));
                        } else {
                            assert targetMethod.getName().equals("check") : targetMethod;
                        }
                        b.getAssumptions().record(new TruffleAssumption(assumption));
                    } else {
                        if (targetMethod.getName().equals("isValid")) {
                            b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(false));
                        } else {
                            assert targetMethod.getName().equals("check") : targetMethod;
                            b.add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.None));
                        }
                    }
                    return true;
                } else {
                    return false;
                }
            }
        };
        r.register1("isValid", Receiver.class, plugin);
        r.register1("check", Receiver.class, plugin);
    }

    public static void registerExactMathPlugins(InvocationPlugins plugins, Replacements replacements, LoweringProvider lowerer, MetaAccessProvider metaAccess) {
        final ResolvedJavaType exactMathType = getRuntime().resolveType(metaAccess, "com.oracle.truffle.api.ExactMath");
        Registration r = new Registration(plugins, new ResolvedJavaSymbol(exactMathType), replacements);
        for (JavaKind kind : new JavaKind[]{JavaKind.Int, JavaKind.Long}) {
            Class<?> type = kind.toJavaClass();
            r.register2("multiplyHigh", type, type, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.addPush(kind, new IntegerMulHighNode(x, y));
                    return true;
                }
            });
            r.register2("multiplyHighUnsigned", type, type, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.addPush(kind, new UnsignedMulHighNode(x, y));
                    return true;
                }
            });
        }
        for (JavaKind kind : new JavaKind[]{JavaKind.Float, JavaKind.Double}) {
            r.registerConditional1(lowerer.supportsRounding(), "truncate", kind.toJavaClass(), new InvocationPlugin() {

                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x) {
                    b.addPush(kind, new RoundNode(x, RoundingMode.TRUNCATE));
                    return true;
                }

            });
        }
    }

    public static void registerCompilerDirectivesPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess, boolean canDelayIntrinsification) {
        final ResolvedJavaType compilerDirectivesType = getRuntime().resolveType(metaAccess, "com.oracle.truffle.api.CompilerDirectives");
        Registration r = new Registration(plugins, new ResolvedJavaSymbol(compilerDirectivesType));
        r.register0("inInterpreter", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(false));
                return true;
            }
        });
        r.register0("hasNextTier", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (!canDelayIntrinsification && b.getGraph().getCancellable() instanceof TruffleCompilationTask) {
                    TruffleCompilationTask task = (TruffleCompilationTask) b.getGraph().getCancellable();
                    b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(task.hasNextTier()));
                    return true;
                }
                return false;
            }
        });
        r.register0("inCompiledCode", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(true));
                return true;
            }
        });
        r.register0("inCompilationRoot", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                GraphBuilderContext.ExternalInliningContext inliningContext = b.getExternalInliningContext();
                if (inliningContext != null) {
                    b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(inliningContext.getInlinedDepth() == 0));
                    return true;
                }
                return false;
            }
        });
        r.register0("transferToInterpreter", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new DeoptimizeNode(DeoptimizationAction.None, DeoptimizationReason.TransferToInterpreter));
                return true;
            }
        });
        r.register0("transferToInterpreterAndInvalidate", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.TransferToInterpreter));
                return true;
            }
        });
        r.register1("interpreterOnly", Runnable.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                return true;
            }
        });
        r.register1("interpreterOnly", Callable.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                return true;
            }
        });
        r.register2("injectBranchProbability", double.class, boolean.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode probability, ValueNode condition) {
                b.addPush(JavaKind.Boolean, new BranchProbabilityNode(probability, condition));
                return true;
            }
        });
        r.register1("bailout", String.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode message) {
                if (canDelayIntrinsification) {
                    /*
                     * We do not want to bailout yet, since we are still parsing individual methods
                     * and constant folding could still eliminate the call to bailout(). However, we
                     * also want to stop parsing, since we are sure that we will never need the
                     * graph beyond the bailout point.
                     *
                     * Therefore, we manually emit the call to bailout, which will be intrinsified
                     * later when intrinsifications can no longer be delayed. The call is followed
                     * by a NeverPartOfCompilationNode, which is a control sink and therefore stops
                     * any further parsing.
                     */
                    StampPair returnStamp = b.getInvokeReturnStamp(b.getAssumptions());
                    CallTargetNode callTarget = b.add(new MethodCallTargetNode(InvokeKind.Static, targetMethod, new ValueNode[]{message}, returnStamp, null));
                    b.add(new InvokeNode(callTarget, b.bci()));

                    b.add(new NeverPartOfCompilationNode("intrinsification of call to bailout() will abort entire compilation"));
                    return true;
                }

                if (message.isConstant()) {
                    throw b.bailout(message.asConstant().toValueString());
                }
                throw b.bailout("bailout (message is not compile-time constant, so no additional information is available)");
            }
        });
        r.register1("isCompilationConstant", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                if ((value instanceof BoxNode ? ((BoxNode) value).getValue() : value).isConstant()) {
                    b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(true));
                } else {
                    b.addPush(JavaKind.Boolean, new IsCompilationConstantNode(value));
                }
                return true;
            }
        });
        r.register1("isPartialEvaluationConstant", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                if ((value instanceof BoxNode ? ((BoxNode) value).getValue() : value).isConstant()) {
                    b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(true));
                } else if (canDelayIntrinsification) {
                    return false;
                } else {
                    b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(false));
                }
                return true;
            }
        });
        r.register1("materialize", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                AllowMaterializeNode materializedValue = b.append(new AllowMaterializeNode(value));
                b.add(new ForceMaterializeNode(materializedValue));
                return true;
            }
        });
        r.register1("ensureVirtualized", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                b.add(new EnsureVirtualizedNode(object, false));
                return true;
            }
        });
        r.register1("ensureVirtualizedHere", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                b.add(new EnsureVirtualizedNode(object, true));
                return true;
            }
        });

        for (JavaKind kind : JavaKind.values()) {
            if ((kind.isPrimitive() && kind != JavaKind.Void) || kind == JavaKind.Object) {
                Class<?> javaClass = getJavaClass(kind);
                r.register1("blackhole", javaClass, new InvocationPlugin() {
                    @Override
                    public boolean inlineOnly() {
                        return true;
                    }

                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                        b.add(new BlackholeNode(value));
                        return true;
                    }
                });
            }
        }

        r.register2("castExact", Object.class, Class.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object, ValueNode javaClass) {
                ValueNode nullCheckedClass = b.addNonNullCast(javaClass);
                LogicNode condition = b.append(InstanceOfDynamicNode.create(b.getAssumptions(), b.getConstantReflection(), nullCheckedClass, object, true, true));
                if (condition.isTautology()) {
                    b.addPush(JavaKind.Object, object);
                } else {
                    FixedGuardNode fixedGuard = b.add(new FixedGuardNode(condition,
                                    DeoptimizationReason.ClassCastException, DeoptimizationAction.InvalidateReprofile, false));
                    b.addPush(JavaKind.Object, DynamicPiNode.create(b.getAssumptions(), b.getConstantReflection(), object, fixedGuard, nullCheckedClass, true, true));
                }
                return true;
            }
        });

        r.register2("isExact", Object.class, Class.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object, ValueNode javaClass) {
                ValueNode nullCheckedClass = b.addNonNullCast(javaClass);
                LogicNode condition = b.append(InstanceOfDynamicNode.create(b.getAssumptions(), b.getConstantReflection(), nullCheckedClass, object, false, true));
                b.addPush(JavaKind.Boolean, b.append(new ConditionalNode(condition)));
                return true;
            }
        });
    }

    private static Class<?> getJavaClass(JavaKind kind) {
        return kind == JavaKind.Object ? Object.class : kind.toJavaClass();
    }

    public static void registerCompilerAssertsPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess, boolean canDelayIntrinsification) {
        final ResolvedJavaType compilerAssertsType = getRuntime().resolveType(metaAccess, "com.oracle.truffle.api.CompilerAsserts");
        Registration r = new Registration(plugins, new ResolvedJavaSymbol(compilerAssertsType));
        PEConstantPlugin peConstantPlugin = new PEConstantPlugin(canDelayIntrinsification);
        r.register1("partialEvaluationConstant", Object.class, peConstantPlugin);
        r.register1("partialEvaluationConstant", int.class, peConstantPlugin);
        r.register1("partialEvaluationConstant", long.class, peConstantPlugin);
        r.register1("partialEvaluationConstant", float.class, peConstantPlugin);
        r.register1("partialEvaluationConstant", double.class, peConstantPlugin);
        r.register1("partialEvaluationConstant", boolean.class, peConstantPlugin);
        r.register0("neverPartOfCompilation", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new NeverPartOfCompilationNode("CompilerAsserts.neverPartOfCompilation()"));
                return true;
            }
        });
        r.register1("neverPartOfCompilation", String.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode message) {
                if (message.isConstant()) {
                    String messageString = message.asConstant().toValueString();
                    b.add(new NeverPartOfCompilationNode(messageString));
                    return true;
                } else {
                    throw b.bailout("message for never part of compilation is non-constant");
                }
            }
        });
    }

    public static void registerOptimizedCallTargetPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess, boolean canDelayIntrinsification, KnownTruffleTypes types,
                    EconomicSet<ResolvedJavaType> primitiveBoxingTypes) {
        final ResolvedJavaType optimizedCallTargetType = getRuntime().resolveType(metaAccess, "org.graalvm.compiler.truffle.runtime.OptimizedCallTarget");
        Registration r = new Registration(plugins, new ResolvedJavaSymbol(optimizedCallTargetType));
        r.register2("createFrame", new ResolvedJavaSymbol(types.classFrameDescriptor), Object[].class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode descriptor, ValueNode args) {
                if (canDelayIntrinsification) {
                    return false;
                }
                if (!descriptor.isJavaConstant()) {
                    throw b.bailout("Parameter 'descriptor' is not a compile-time constant");
                }

                ValueNode nonNullArguments = b.add(PiNode.create(args, StampFactory.objectNonNull(StampTool.typeReferenceOrNull(args))));
                b.addPush(JavaKind.Object, new NewFrameNode(b, descriptor, nonNullArguments, types));
                return true;
            }
        });
        r.register2("castArrayFixedLength", Object[].class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode args, ValueNode length) {
                if (canDelayIntrinsification) {
                    return false;
                }
                if (args.isConstant()) {
                    b.addPush(JavaKind.Object, args);
                    return true;
                }

                b.addPush(JavaKind.Object, new PiArrayNode(args, length, args.stamp(NodeView.DEFAULT)));
                return true;
            }
        });
        registerUnsafeCast(r, canDelayIntrinsification, primitiveBoxingTypes);
    }

    public static void registerFrameWithoutBoxingPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess, boolean canDelayIntrinsification, ConstantReflectionProvider constantReflection,
                    KnownTruffleTypes types, EconomicSet<ResolvedJavaType> primitiveBoxingTypes) {
        ResolvedJavaType frameWithoutBoxingType = getRuntime().resolveType(metaAccess, "com.oracle.truffle.api.impl.FrameWithoutBoxing");
        Registration r = new Registration(plugins, new ResolvedJavaSymbol(frameWithoutBoxingType));
        registerFrameMethods(r, constantReflection, types);
        registerUnsafeCast(r, canDelayIntrinsification, primitiveBoxingTypes);
        registerUnsafeLoadStorePlugins(r, canDelayIntrinsification, null, JavaKind.Int, JavaKind.Long, JavaKind.Float, JavaKind.Double, JavaKind.Object);
        registerFrameAccessors(r, JavaKind.Object, constantReflection, types);
        registerFrameAccessors(r, JavaKind.Long, constantReflection, types);
        registerFrameAccessors(r, JavaKind.Int, constantReflection, types);
        registerFrameAccessors(r, JavaKind.Double, constantReflection, types);
        registerFrameAccessors(r, JavaKind.Float, constantReflection, types);
        registerFrameAccessors(r, JavaKind.Boolean, constantReflection, types);
        registerFrameAccessors(r, JavaKind.Byte, constantReflection, types);

        registerFrameTagAccessor(r);
        registerFrameAuxiliaryAccessors(r);

    }

    /**
     * We intrinsify the getXxx, setXxx, and isXxx methods for all type tags. The intrinsic nodes
     * are lightweight fixed nodes without a {@link FrameState}. No {@link FrameState} is important
     * for partial evaluation performance, because creating and later on discarding FrameStates for
     * the setXxx methods have a high compile time cost.
     *
     * Intrinsification requires the following conditions: (1) the accessed frame is directly the
     * {@link NewFrameNode}, (2) the accessed FrameSlot is a constant, and (3) the FrameDescriptor
     * was never materialized before. All three conditions together guarantee that the escape
     * analysis can virtualize the access. The condition (3) is necessary because a possible
     * materialization of the frame can prevent escape analysis - so in that case a FrameState for
     * setXxx methods is actually necessary since they stores can be state-changing memory
     * operations.
     *
     * Note that we do not register an intrinsification for {@code FrameWithoutBoxing.getValue()}.
     * It is a complicated method to intrinsify, and it is not used frequently enough to justify the
     * complexity of an intrinsification.
     */
    private static void registerFrameAccessors(Registration r, JavaKind accessKind, ConstantReflectionProvider constantReflection, KnownTruffleTypes types) {
        TruffleCompilerRuntime runtime = getRuntime();
        int accessTag = runtime.getFrameSlotKindTagForJavaKind(accessKind);
        String nameSuffix = accessKind.name();
        ResolvedJavaSymbol frameSlotType = new ResolvedJavaSymbol(types.classFrameSlot);
        r.register2("get" + nameSuffix, Receiver.class, frameSlotType, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frameNode, ValueNode frameSlotNode) {
                int frameSlotIndex = maybeGetConstantFrameSlotIndex(frameNode, frameSlotNode, constantReflection, types);
                if (frameSlotIndex >= 0) {
                    b.addPush(accessKind, new VirtualFrameGetNode(frameNode, frameSlotIndex, accessKind, accessTag, VirtualFrameAccessType.Legacy));
                    return true;
                }
                return false;
            }
        });

        r.register3("set" + nameSuffix, Receiver.class, frameSlotType, getJavaClass(accessKind), new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frameNode, ValueNode frameSlotNode, ValueNode value) {
                int frameSlotIndex = maybeGetConstantFrameSlotIndex(frameNode, frameSlotNode, constantReflection, types);
                if (frameSlotIndex >= 0) {
                    b.add(new VirtualFrameSetNode(frameNode, frameSlotIndex, accessTag, value, VirtualFrameAccessType.Legacy));
                    return true;
                }
                return false;
            }
        });

        r.register2("is" + nameSuffix, Receiver.class, frameSlotType, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frameNode, ValueNode frameSlotNode) {
                int frameSlotIndex = maybeGetConstantFrameSlotIndex(frameNode, frameSlotNode, constantReflection, types);
                if (frameSlotIndex >= 0) {
                    b.addPush(JavaKind.Boolean, new VirtualFrameIsNode(frameNode, frameSlotIndex, accessTag, VirtualFrameAccessType.Legacy));
                    return true;
                }
                return false;
            }
        });
        r.register2("get" + nameSuffix, Receiver.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frameNode, ValueNode frameSlotNode) {
                int frameSlotIndex = maybeGetConstantNumberedFrameSlotIndex(frameNode, frameSlotNode);
                if (frameSlotIndex >= 0) {
                    b.addPush(accessKind, new VirtualFrameGetNode(frameNode, frameSlotIndex, accessKind, accessTag, VirtualFrameAccessType.Indexed));
                    return true;
                }
                return false;
            }
        });

        r.register3("set" + nameSuffix, Receiver.class, int.class, getJavaClass(accessKind), new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frameNode, ValueNode frameSlotNode, ValueNode value) {
                int frameSlotIndex = maybeGetConstantNumberedFrameSlotIndex(frameNode, frameSlotNode);
                if (frameSlotIndex >= 0) {
                    b.add(new VirtualFrameSetNode(frameNode, frameSlotIndex, accessTag, value, VirtualFrameAccessType.Indexed));
                    return true;
                }
                return false;
            }
        });

        r.register2("is" + nameSuffix, Receiver.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frameNode, ValueNode frameSlotNode) {
                int frameSlotIndex = maybeGetConstantNumberedFrameSlotIndex(frameNode, frameSlotNode);
                if (frameSlotIndex >= 0) {
                    b.addPush(JavaKind.Boolean, new VirtualFrameIsNode(frameNode, frameSlotIndex, accessTag, VirtualFrameAccessType.Indexed));
                    return true;
                }
                return false;
            }
        });
    }

    private static void registerFrameTagAccessor(Registration r) {
        r.register2("getTag", Receiver.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frameNode, ValueNode frameSlotNode) {
                int frameSlotIndex = maybeGetConstantNumberedFrameSlotIndex(frameNode, frameSlotNode);
                if (frameSlotIndex >= 0) {
                    b.addPush(JavaKind.Boolean, new VirtualFrameGetTagNode(frameNode, frameSlotIndex));
                    return true;
                }
                return false;
            }
        });
    }

    private static void registerFrameAuxiliaryAccessors(Registration r) {
        TruffleCompilerRuntime runtime = getRuntime();
        int accessTag = runtime.getFrameSlotKindTagForJavaKind(JavaKind.Object);
        r.register2("getAuxiliarySlot", Receiver.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frameNode, ValueNode frameSlotNode) {
                int frameSlotIndex = maybeGetConstantNumberedFrameSlotIndex(frameNode, frameSlotNode);
                if (frameSlotIndex >= 0) {
                    b.addPush(JavaKind.Object, new VirtualFrameGetNode(frameNode, frameSlotIndex, JavaKind.Object, accessTag, VirtualFrameAccessType.Auxiliary));
                    return true;
                }
                return false;
            }
        });

        r.register3("setAuxiliarySlot", Receiver.class, int.class, Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frameNode, ValueNode frameSlotNode, ValueNode value) {
                int frameSlotIndex = maybeGetConstantNumberedFrameSlotIndex(frameNode, frameSlotNode);
                if (frameSlotIndex >= 0) {
                    b.add(new VirtualFrameSetNode(frameNode, frameSlotIndex, accessTag, value, VirtualFrameAccessType.Auxiliary));
                    return true;
                }
                return false;
            }
        });
    }

    static int maybeGetConstantFrameSlotIndex(Receiver frameNode, ValueNode frameSlotNode, ConstantReflectionProvider constantReflection, KnownTruffleTypes types) {
        if (frameSlotNode.isConstant()) {
            ValueNode frameNodeValue = frameNode.get(false);
            if (frameNodeValue instanceof NewFrameNode) {
                NewFrameNode newFrameNode = (NewFrameNode) frameNodeValue;
                if (newFrameNode.getIntrinsifyAccessors()) {
                    int index = constantReflection.readFieldValue(types.fieldFrameSlotIndex, frameSlotNode.asJavaConstant()).asInt();
                    if (newFrameNode.isValidSlotIndex(index)) {
                        return index;
                    }
                }
            }
        }
        return -1;
    }

    static int maybeGetConstantNumberedFrameSlotIndex(Receiver frameNode, ValueNode frameSlotNode) {
        if (frameSlotNode.isConstant()) {
            ValueNode frameNodeValue = frameNode.get(false);
            if (frameNodeValue instanceof NewFrameNode) {
                NewFrameNode newFrameNode = (NewFrameNode) frameNodeValue;
                if (newFrameNode.getIntrinsifyAccessors()) {
                    int index = frameSlotNode.asJavaConstant().asInt();
                    if (newFrameNode.isValidIndexedSlotIndex(index)) {
                        return index;
                    }
                }
            }
        }
        return -1;
    }

    private static void registerFrameMethods(Registration r, ConstantReflectionProvider constantReflection, KnownTruffleTypes types) {
        r.register1("getArguments", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frame) {
                if (frame.get(false) instanceof NewFrameNode) {
                    b.push(JavaKind.Object, ((NewFrameNode) frame.get()).getArguments());
                    return true;
                }
                return false;
            }
        });

        r.register1("getFrameDescriptor", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frame) {
                if (frame.get(false) instanceof NewFrameNode) {
                    b.push(JavaKind.Object, ((NewFrameNode) frame.get()).getDescriptor());
                    return true;
                }
                return false;
            }
        });

        r.register1("materialize", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode frame = receiver.get();
                if (frame instanceof NewFrameNode && ((NewFrameNode) frame).getIntrinsifyAccessors()) {
                    Speculation speculation = b.getGraph().getSpeculationLog().speculate(((NewFrameNode) frame).getIntrinsifyAccessorsSpeculation());
                    b.add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.RuntimeConstraint, speculation));
                    return true;
                }

                b.addPush(JavaKind.Object, new AllowMaterializeNode(frame));
                return true;
            }
        });

        r.register2("clear", Receiver.class, new ResolvedJavaSymbol(types.classFrameSlot), new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode frameSlot) {
                int frameSlotIndex = maybeGetConstantFrameSlotIndex(receiver, frameSlot, constantReflection, types);
                if (frameSlotIndex >= 0) {
                    TruffleCompilerRuntime runtime = getRuntime();
                    b.add(new VirtualFrameClearNode(receiver, frameSlotIndex, runtime.getFrameSlotKindTagForJavaKind(JavaKind.Illegal), VirtualFrameAccessType.Legacy));
                    return true;
                }
                return false;
            }
        });
        r.register2("clear", Receiver.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode frameSlot) {
                int frameSlotIndex = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot);
                if (frameSlotIndex >= 0) {
                    TruffleCompilerRuntime runtime = getRuntime();
                    b.add(new VirtualFrameClearNode(receiver, frameSlotIndex, runtime.getFrameSlotKindTagForJavaKind(JavaKind.Illegal), VirtualFrameAccessType.Indexed));
                    return true;
                }
                return false;
            }
        });
        r.register3("swap", Receiver.class, int.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode frameSlot1, ValueNode frameSlot2) {
                int frameSlot1Index = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot1);
                int frameSlot2Index = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot2);
                if (frameSlot1Index >= 0 && frameSlot2Index >= 0) {
                    b.add(new VirtualFrameSwapNode(receiver, frameSlot1Index, frameSlot2Index, VirtualFrameAccessType.Indexed));
                    return true;
                }
                return false;
            }
        });
        r.register3("copy", Receiver.class, int.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode frameSlot1, ValueNode frameSlot2) {
                int frameSlot1Index = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot1);
                int frameSlot2Index = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot2);
                if (frameSlot1Index >= 0 && frameSlot2Index >= 0) {
                    b.add(new VirtualFrameCopyNode(receiver, frameSlot1Index, frameSlot2Index, VirtualFrameAccessType.Indexed));
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * @see com.oracle.truffle.api.nodes.Node
     */
    public static void registerNodePlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess, boolean canDelayIntrinsification,
                    ConstantReflectionProvider constantReflection, KnownTruffleTypes types) {
        final ResolvedJavaType nodeType = getRuntime().resolveType(metaAccess, "com.oracle.truffle.api.nodes.Node");
        Registration r = new Registration(plugins, new ResolvedJavaSymbol(nodeType));
        r.register1("getRootNodeImpl", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (canDelayIntrinsification) {
                    return false;
                }

                ValueNode thisValue = receiver.get();
                if (!thisValue.isJavaConstant() || thisValue.isNullConstant()) {
                    throw b.bailout("getRootNode() receiver is not a compile-time constant or is null.");
                }

                final int parentLimit = MaximumLoopExplosionCount.getValue(b.getOptions());
                JavaConstant parentNode = thisValue.asJavaConstant();
                JavaConstant prevNode;
                int parentsVisited = 0;
                do {
                    if (parentsVisited++ > parentLimit) {
                        // Protect against parent cycles and extremely long parent chains.
                        throw b.bailout("getRootNode() did not terminate in " + parentLimit + " iterations.");
                    }
                    prevNode = parentNode;
                    parentNode = constantReflection.readFieldValue(types.fieldNodeParent, prevNode);
                } while (parentNode.isNonNull());

                JavaConstant rootNode = prevNode;
                ConstantNode result = ConstantNode.forConstant(rootNode, metaAccess, b.getGraph());
                // getRootNodeImpl() returns null if parent is not an instance of RootNode.
                if (rootNode.isNonNull() && !types.classRootNode.isAssignableFrom(result.stamp(NodeView.DEFAULT).javaType(metaAccess))) {
                    result = ConstantNode.defaultForKind(JavaKind.Object, b.getGraph());
                }
                b.addPush(JavaKind.Object, result);
                return true;
            }
        });
    }

    public static void registerUnsafeCast(Registration r, boolean canDelayIntrinsification, EconomicSet<ResolvedJavaType> primitiveBoxTypes) {
        r.register5("unsafeCast", Object.class, Class.class, boolean.class, boolean.class, boolean.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object, ValueNode clazz, ValueNode condition, ValueNode nonNull,
                            ValueNode isExactType) {
                if (clazz.isConstant() && nonNull.isConstant() && isExactType.isConstant()) {
                    ConstantReflectionProvider constantReflection = b.getConstantReflection();
                    ResolvedJavaType javaType = constantReflection.asJavaType(clazz.asConstant());
                    if (javaType == null) {
                        b.push(JavaKind.Object, object);
                    } else {
                        TypeReference type;
                        if (isExactType.asJavaConstant().asInt() != 0) {
                            assert javaType.isConcrete() || javaType.isArray() : "exact type is not a concrete class: " + javaType;
                            type = TypeReference.createExactTrusted(javaType);
                        } else {
                            type = TypeReference.createTrusted(b.getAssumptions(), javaType);
                        }

                        Stamp piStamp = StampFactory.object(type, nonNull.asJavaConstant().asInt() != 0);

                        ConditionAnchorNode valueAnchorNode = null;
                        if (condition.isConstant() && condition.asJavaConstant().asInt() == 1) {
                            // Nothing to do.
                        } else {
                            boolean skipAnchor = false;
                            LogicNode compareNode = CompareNode.createCompareNode(object.graph(), CanonicalCondition.EQ, condition, ConstantNode.forBoolean(true, object.graph()), constantReflection,
                                            NodeView.DEFAULT);

                            if (compareNode instanceof LogicConstantNode) {
                                LogicConstantNode logicConstantNode = (LogicConstantNode) compareNode;
                                if (logicConstantNode.getValue()) {
                                    skipAnchor = true;
                                }
                            }

                            if (!skipAnchor) {
                                valueAnchorNode = b.add(new ConditionAnchorNode(compareNode));
                            }
                        }

                        b.addPush(JavaKind.Object, trustedBox(type, primitiveBoxTypes, PiNode.create(object, piStamp, valueAnchorNode)));
                    }
                    return true;
                } else if (canDelayIntrinsification) {
                    return false;
                } else {
                    logPerformanceWarningUnsafeCastArgNotConst(targetMethod, clazz, nonNull, isExactType);
                    b.push(JavaKind.Object, object);
                    return true;
                }
            }
        });
    }

    private static ValueNode trustedBox(TypeReference type, EconomicSet<ResolvedJavaType> primitiveBoxTypes, ValueNode v) {
        if (primitiveBoxTypes.contains(type.getType())) {
            return new BoxNode.TrustedBoxedValue(v);
        }
        return v;
    }

    public static void registerUnsafeLoadStorePlugins(Registration r, boolean canDelayIntrinsification, JavaConstant anyConstant, JavaKind... kinds) {
        for (JavaKind kind : kinds) {
            String kindName = kind.getJavaName();
            kindName = toUpperCase(kindName.charAt(0)) + kindName.substring(1);
            String getName = "unsafeGet" + kindName;
            String putName = "unsafePut" + kindName;
            r.register4(getName, Object.class, long.class, boolean.class, Object.class, new CustomizedUnsafeLoadPlugin(kind, canDelayIntrinsification));
            r.register4(putName, Object.class, long.class, getJavaClass(kind), Object.class,
                            new CustomizedUnsafeStorePlugin(kind, anyConstant, canDelayIntrinsification));
        }
    }

    static class CustomizedUnsafeLoadPlugin implements InvocationPlugin {

        private final JavaKind returnKind;
        private final boolean canDelayIntrinsification;

        CustomizedUnsafeLoadPlugin(JavaKind returnKind, boolean canDelayIntrinsification) {
            this.returnKind = returnKind;
            this.canDelayIntrinsification = canDelayIntrinsification;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object, ValueNode offset, ValueNode condition, ValueNode location) {
            if (location.isConstant()) {
                LocationIdentity locationIdentity;
                boolean forceLocation;
                if (location.isNullConstant()) {
                    locationIdentity = LocationIdentity.any();
                    forceLocation = false;
                } else {
                    locationIdentity = ObjectLocationIdentity.create(location.asJavaConstant());
                    forceLocation = true;
                }
                LogicNode compare = b.add(CompareNode.createCompareNode(b.getConstantReflection(), b.getMetaAccess(), b.getOptions(), null, CanonicalCondition.EQ, condition,
                                ConstantNode.forBoolean(true, object.graph()), NodeView.DEFAULT));
                ConditionAnchorNode anchor = b.add(new ConditionAnchorNode(compare));
                b.addPush(returnKind, b.add(new GuardedUnsafeLoadNode(b.addNonNullCast(object), offset, returnKind, locationIdentity, anchor, forceLocation)));
                return true;
            } else if (canDelayIntrinsification) {
                return false;
            } else {
                RawLoadNode load = b.addPush(returnKind, new RawLoadNode(object, offset, returnKind, LocationIdentity.any(), true));
                logPerformanceWarningLocationNotConstant(location, targetMethod, load);
                return true;
            }
        }
    }

    static class CustomizedUnsafeStorePlugin implements InvocationPlugin {

        private final JavaKind kind;
        private final JavaConstant anyConstant;
        private final boolean canDelayIntrinsification;

        CustomizedUnsafeStorePlugin(JavaKind kind, JavaConstant anyConstant, boolean canDelayIntrinsification) {
            this.kind = kind;
            this.anyConstant = anyConstant;
            this.canDelayIntrinsification = canDelayIntrinsification;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object, ValueNode offset, ValueNode value, ValueNode location) {
            ValueNode locationArgument = location;
            if (locationArgument.isConstant()) {
                LocationIdentity locationIdentity;
                boolean forceLocation;
                if (locationArgument.isNullConstant()) {
                    locationIdentity = LocationIdentity.any();
                    forceLocation = false;
                } else if (locationArgument.asJavaConstant().equals(anyConstant)) {
                    locationIdentity = LocationIdentity.any();
                    forceLocation = true;
                } else {
                    locationIdentity = ObjectLocationIdentity.create(locationArgument.asJavaConstant());
                    forceLocation = true;
                }
                b.add(new RawStoreNode(object, offset, value, kind, locationIdentity, true, null, forceLocation));
                return true;
            } else if (canDelayIntrinsification) {
                return false;
            } else {
                RawStoreNode store = b.add(new RawStoreNode(object, offset, value, kind, LocationIdentity.any(), true, null, true));
                logPerformanceWarningLocationNotConstant(location, targetMethod, store);
                return true;
            }
        }
    }

    @SuppressWarnings("try")
    static void logPerformanceWarningLocationNotConstant(ValueNode location, ResolvedJavaMethod targetMethod, UnsafeAccessNode access) {
        if (PerformanceInformationHandler.isWarningEnabled(PerformanceWarningKind.VIRTUAL_STORE)) {
            StructuredGraph graph = location.graph();
            DebugContext debug = access.getDebug();
            try (DebugContext.Scope s = debug.scope("TrufflePerformanceWarnings", graph)) {
                TruffleDebugJavaMethod truffleMethod = debug.contextLookup(TruffleDebugJavaMethod.class);
                if (truffleMethod != null) {    // Never null in compilation but can be null in
                                                // TruffleCompilerImplTest
                    Map<String, Object> properties = new LinkedHashMap<>();
                    properties.put("location", location);
                    properties.put("method", targetMethod.format("%h.%n"));
                    PerformanceInformationHandler.logPerformanceWarning(PerformanceWarningKind.VIRTUAL_STORE, truffleMethod.getCompilable(),
                                    Collections.singletonList(access),
                                    "location argument not PE-constant", properties);
                    debug.dump(DebugContext.VERBOSE_LEVEL, graph, "perf warn: Location argument is not a partial evaluation constant: %s", location);
                }
            } catch (Throwable t) {
                debug.handle(t);
            }
        }
    }

    @SuppressWarnings("try")
    static void logPerformanceWarningUnsafeCastArgNotConst(ResolvedJavaMethod targetMethod, ValueNode type, ValueNode nonNull, ValueNode isExactType) {
        if (PerformanceInformationHandler.isWarningEnabled(PerformanceWarningKind.VIRTUAL_STORE)) {
            StructuredGraph graph = type.graph();
            DebugContext debug = type.getDebug();
            try (DebugContext.Scope s = debug.scope("TrufflePerformanceWarnings", graph)) {
                TruffleDebugJavaMethod truffleMethod = debug.contextLookup(TruffleDebugJavaMethod.class);
                if (truffleMethod != null) {    // Never null in compilation but can be null in
                                                // TruffleCompilerImplTest
                    Map<String, Object> properties = new LinkedHashMap<>();
                    List<ValueNode> nonConstArgs = new ArrayList<>();
                    properties.put("type", type);
                    if (!type.isConstant()) {
                        nonConstArgs.add(type);
                    }
                    properties.put("nonNull", nonNull);
                    if (!nonNull.isConstant()) {
                        nonConstArgs.add(nonNull);
                    }
                    properties.put("exactType", isExactType);
                    if (!isExactType.isConstant()) {
                        nonConstArgs.add(isExactType);
                    }
                    properties.put("method", targetMethod.format("%h.%n"));
                    PerformanceInformationHandler.logPerformanceWarning(PerformanceWarningKind.VIRTUAL_STORE, truffleMethod.getCompilable(), nonConstArgs,
                                    "unsafeCast arguments could not reduce to a constant", properties);
                    debug.dump(DebugContext.VERBOSE_LEVEL, graph, "perf warn: unsafeCast arguments could not reduce to a constant: %s, %s, %s", type, nonNull, isExactType);
                }
            } catch (Throwable t) {
                debug.handle(t);
            }
        }
    }

    static BailoutException failPEConstant(GraphBuilderContext b, ValueNode value) {
        StringBuilder sb = new StringBuilder();
        sb.append(value);
        if (value instanceof ValuePhiNode) {
            ValuePhiNode valuePhi = (ValuePhiNode) value;
            sb.append(" (");
            for (Node n : valuePhi.inputs()) {
                sb.append(n);
                sb.append("; ");
            }
            sb.append(")");
        }
        value.getDebug().dump(DebugContext.VERBOSE_LEVEL, value.graph(), "Graph before bailout at node %s", sb);
        throw b.bailout("Partial evaluation did not reduce value to a constant, is a regular compiler node: " + sb);
    }

    private static final class PEConstantPlugin implements InvocationPlugin {
        private final boolean canDelayIntrinsification;

        private PEConstantPlugin(boolean canDelayIntrinsification) {
            this.canDelayIntrinsification = canDelayIntrinsification;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
            ValueNode curValue = value;
            if (curValue instanceof BoxNode) {
                BoxNode boxNode = (BoxNode) curValue;
                curValue = boxNode.getValue();
            }
            if (curValue.isConstant()) {
                return true;
            } else if (canDelayIntrinsification) {
                return false;
            } else {
                throw failPEConstant(b, value);
            }
        }

    }
}
