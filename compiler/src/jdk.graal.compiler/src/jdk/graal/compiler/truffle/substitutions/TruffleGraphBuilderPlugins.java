/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.Character.toUpperCase;
import static jdk.graal.compiler.replacements.PEGraphDecoder.Options.MaximumLoopExplosionCount;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import org.graalvm.word.LocationIdentity;

import com.oracle.truffle.compiler.TruffleCompilationTask;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool.RoundingMode;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ConditionAnchorNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.DynamicPiNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiArrayNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerMulHighNode;
import jdk.graal.compiler.nodes.calc.RoundNode;
import jdk.graal.compiler.nodes.debug.BlackholeNode;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.GuardedUnsafeLoadNode;
import jdk.graal.compiler.nodes.extended.RawLoadNode;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.extended.UnsafeAccessNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.ConditionalInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.OptionalInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.RequiredInlineOnlyInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.RequiredInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.ResolvedJavaSymbol;
import jdk.graal.compiler.nodes.java.InstanceOfDynamicNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.virtual.EnsureVirtualizedNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.StandardGraphBuilderPlugins;
import jdk.graal.compiler.replacements.arraycopy.ArrayCopySnippets;
import jdk.graal.compiler.replacements.nodes.arithmetic.UnsignedMulHighNode;
import jdk.graal.compiler.serviceprovider.SpeculationReasonGroup;
import jdk.graal.compiler.truffle.KnownTruffleTypes;
import jdk.graal.compiler.truffle.PerformanceInformationHandler;
import jdk.graal.compiler.truffle.TruffleCompilation;
import jdk.graal.compiler.truffle.TruffleCompilerOptions.PerformanceWarningKind;
import jdk.graal.compiler.truffle.TruffleDebugJavaMethod;
import jdk.graal.compiler.truffle.nodes.AnyExtendNode;
import jdk.graal.compiler.truffle.nodes.AnyNarrowNode;
import jdk.graal.compiler.truffle.nodes.IsCompilationConstantNode;
import jdk.graal.compiler.truffle.nodes.ObjectLocationIdentity;
import jdk.graal.compiler.truffle.nodes.TruffleAssumption;
import jdk.graal.compiler.truffle.nodes.asserts.NeverPartOfCompilationNode;
import jdk.graal.compiler.truffle.nodes.frame.AllowMaterializeNode;
import jdk.graal.compiler.truffle.nodes.frame.ForceMaterializeNode;
import jdk.graal.compiler.truffle.nodes.frame.NewFrameNode;
import jdk.graal.compiler.truffle.nodes.frame.VirtualFrameAccessFlags;
import jdk.graal.compiler.truffle.nodes.frame.VirtualFrameAccessType;
import jdk.graal.compiler.truffle.nodes.frame.VirtualFrameClearNode;
import jdk.graal.compiler.truffle.nodes.frame.VirtualFrameCopyNode;
import jdk.graal.compiler.truffle.nodes.frame.VirtualFrameGetNode;
import jdk.graal.compiler.truffle.nodes.frame.VirtualFrameGetTagNode;
import jdk.graal.compiler.truffle.nodes.frame.VirtualFrameIsNode;
import jdk.graal.compiler.truffle.nodes.frame.VirtualFrameSetNode;
import jdk.graal.compiler.truffle.nodes.frame.VirtualFrameSwapNode;
import jdk.graal.compiler.truffle.phases.TruffleSafepointInsertionPhase;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.ConstantReflectionProvider;
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

/**
 * Provides {@link InvocationPlugin}s for Truffle classes. These plugins are used only during
 * partial evaluation.
 */
public class TruffleGraphBuilderPlugins {

    public static class Options {

        @Option(help = "Whether Truffle trusted non-null casts are enabled.", type = OptionType.Debug) //
        public static final OptionKey<Boolean> TruffleTrustedNonNullCast = new OptionKey<>(true);
        @Option(help = "Whether Truffle trusted type casts are enabled.", type = OptionType.Debug) //
        public static final OptionKey<Boolean> TruffleTrustedTypeCast = new OptionKey<>(true);
        @Option(help = "Whether Truffle frame field reads are trusted final.", type = OptionType.Debug) //
        public static final OptionKey<Boolean> TruffleTrustedFinalFrameFields = new OptionKey<>(true);

    }

    public static void registerInvocationPlugins(InvocationPlugins plugins, KnownTruffleTypes types, Providers providers, boolean canDelayIntrinsification) {
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        registerObjectsPlugins(plugins, types);
        registerOptimizedAssumptionPlugins(plugins, types);
        registerExactMathPlugins(plugins, types);
        registerHostCompilerDirectivesPlugins(plugins, types);
        registerCompilerDirectivesPlugins(plugins, types, canDelayIntrinsification);
        registerCompilerAssertsPlugins(plugins, types, canDelayIntrinsification);
        registerOptimizedCallTargetPlugins(plugins, types, canDelayIntrinsification);
        registerFrameWithoutBoxingPlugins(plugins, types, canDelayIntrinsification);
        registerTruffleSafepointPlugins(plugins, types, canDelayIntrinsification);
        registerNodePlugins(plugins, types, metaAccess, canDelayIntrinsification, providers.getConstantReflection());
        registerDynamicObjectPlugins(plugins, types, canDelayIntrinsification, providers.getConstantReflection());
        registerBufferPlugins(plugins, types, canDelayIntrinsification);
        registerMemorySegmentPlugins(plugins, types, canDelayIntrinsification);
    }

    private static void registerTruffleSafepointPlugins(InvocationPlugins plugins, KnownTruffleTypes types, boolean canDelayIntrinsification) {
        final ResolvedJavaType truffleSafepoint = types.TruffleSafepoint;
        Registration r = new Registration(plugins, new ResolvedJavaSymbol(truffleSafepoint));
        r.register(new RequiredInvocationPlugin("poll", new ResolvedJavaSymbol(types.Node)) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                if (!TruffleSafepointInsertionPhase.allowsSafepoints(b.getGraph())) {
                    if (!canDelayIntrinsification) {
                        /*
                         * TruffleSafepoint.poll only expected to be removed in Truffle
                         * compilations.
                         */
                        throw failPEConstant(b, arg);
                    }
                    return false;
                } else if (arg.isConstant()) {
                    return true;
                } else if (canDelayIntrinsification) {
                    return false;
                } else {
                    throw failPEConstant(b, arg);
                }
            }
        });
    }

    private static class RequireNonNullPlugin extends RequiredInvocationPlugin {

        RequireNonNullPlugin(Type... argumentTypes) {
            super("requireNonNull", argumentTypes);
        }

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
    }

    private static void registerObjectsPlugins(InvocationPlugins plugins, KnownTruffleTypes types) {
        Registration r = new Registration(plugins, new ResolvedJavaSymbol(types.Objects));
        r.register(new RequireNonNullPlugin(Object.class));
        r.register(new RequireNonNullPlugin(Object.class, String.class));
        r.register(new RequireNonNullPlugin(Object.class, Supplier.class));
    }

    public static void registerOptimizedAssumptionPlugins(InvocationPlugins plugins, KnownTruffleTypes types) {
        Registration r = new Registration(plugins, new ResolvedJavaSymbol(types.OptimizedAssumption));
        r.register(new RequiredInvocationPlugin("isValid", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                JavaConstant assumption = receiver.get(false).asJavaConstant();
                if (b.getAssumptions() != null && assumption != null && assumption.isNonNull()) {
                    JavaConstant isValid = b.getConstantReflection().readFieldValue(types.AbstractAssumption_isValid, assumption);
                    if (isValid != null) {
                        if (isValid.asBoolean()) {
                            b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(true));
                            b.getAssumptions().record(new TruffleAssumption(assumption));
                        } else {
                            b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(false));
                        }
                        return true;
                    }
                }
                return false;
            }
        });
        r.register(new RequiredInvocationPlugin("check", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                JavaConstant assumption = receiver.get(false).asJavaConstant();
                if (b.getAssumptions() != null && assumption != null && assumption.isNonNull()) {
                    JavaConstant isValid = b.getConstantReflection().readFieldValue(types.AbstractAssumption_isValid, assumption);
                    if (isValid != null) {
                        if (isValid.asBoolean()) {
                            b.getAssumptions().record(new TruffleAssumption(assumption));
                        } else {
                            b.add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.None));
                        }
                        return true;
                    }
                }
                return false;
            }
        });
    }

    public static void registerExactMathPlugins(InvocationPlugins plugins, KnownTruffleTypes types) {
        Registration r = new Registration(plugins, new ResolvedJavaSymbol(types.ExactMath));
        for (JavaKind kind : new JavaKind[]{JavaKind.Int, JavaKind.Long}) {
            Class<?> type = kind.toJavaClass();
            r.register(new InvocationPlugin("multiplyHigh", type, type) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.addPush(kind, new IntegerMulHighNode(x, y));
                    return true;
                }
            });
            r.register(new InvocationPlugin("multiplyHighUnsigned", type, type) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.addPush(kind, new UnsignedMulHighNode(x, y));
                    return true;
                }
            });
        }
        for (JavaKind kind : new JavaKind[]{JavaKind.Float, JavaKind.Double}) {
            r.register(new ConditionalInvocationPlugin("truncate", kind.toJavaClass()) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x) {
                    b.addPush(kind, RoundNode.create(x, RoundingMode.TRUNCATE));
                    return true;
                }

                @Override
                public boolean isApplicable(Architecture arch) {
                    return RoundNode.isSupported(arch);
                }
            });
        }
    }

    public static void registerHostCompilerDirectivesPlugins(InvocationPlugins plugins, KnownTruffleTypes types) {
        final ResolvedJavaType compilerDirectivesType = types.HostCompilerDirectives;
        Registration r = new Registration(plugins, new ResolvedJavaSymbol(compilerDirectivesType));
        r.register(new RequiredInvocationPlugin("inInterpreterFastPath") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(false));
                return true;
            }
        });
    }

    public static void registerCompilerDirectivesPlugins(InvocationPlugins plugins, KnownTruffleTypes types, boolean canDelayIntrinsification) {
        final ResolvedJavaType compilerDirectivesType = types.CompilerDirectives;
        Registration r = new Registration(plugins, new ResolvedJavaSymbol(compilerDirectivesType));
        r.register(new RequiredInvocationPlugin("inInterpreter") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(false));
                return true;
            }
        });
        r.register(new RequiredInvocationPlugin("hasNextTier") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (canDelayIntrinsification) {
                    return false;
                }
                TruffleCompilationTask task = TruffleCompilation.lookupTask(b.getGraph());
                if (task != null) {
                    b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(task.hasNextTier()));
                    return true;
                }
                return false;
            }
        });
        r.register(new RequiredInvocationPlugin("inCompiledCode") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(true));
                return true;
            }
        });
        r.register(new RequiredInvocationPlugin("inCompilationRoot") {
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
        r.register(new RequiredInvocationPlugin("transferToInterpreter") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new DeoptimizeNode(DeoptimizationAction.None, DeoptimizationReason.TransferToInterpreter));
                return true;
            }
        });
        r.register(new RequiredInvocationPlugin("transferToInterpreterAndInvalidate") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.TransferToInterpreter));
                return true;
            }
        });
        r.register(new RequiredInvocationPlugin("interpreterOnly", Runnable.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                return true;
            }
        });
        r.register(new RequiredInvocationPlugin("interpreterOnly", Callable.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                return true;
            }
        });
        r.register(new RequiredInvocationPlugin("injectBranchProbability", double.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode probability, ValueNode condition) {
                b.addPush(JavaKind.Boolean, new BranchProbabilityNode(probability, condition));
                return true;
            }
        });
        r.register(new RequiredInvocationPlugin("bailout", String.class) {
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
        r.register(new RequiredInvocationPlugin("isCompilationConstant", Object.class) {
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
        r.register(new RequiredInvocationPlugin("isPartialEvaluationConstant", Object.class) {
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
        r.register(new RequiredInvocationPlugin("materialize", Object.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                AllowMaterializeNode materializedValue = b.append(new AllowMaterializeNode(value));
                b.add(new ForceMaterializeNode(materializedValue));
                return true;
            }
        });
        r.register(new RequiredInvocationPlugin("ensureVirtualized", Object.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                b.add(new EnsureVirtualizedNode(object, false));
                return true;
            }
        });
        r.register(new RequiredInvocationPlugin("ensureVirtualizedHere", Object.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                b.add(new EnsureVirtualizedNode(object, true));
                return true;
            }
        });
        r.register(new RequiredInlineOnlyInvocationPlugin("ensureAllocatedHere", Object.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                StandardGraphBuilderPlugins.registerEnsureAllocatedHereIntrinsic(b, object);
                return true;
            }
        });

        for (JavaKind kind : JavaKind.values()) {
            if ((kind.isPrimitive() && kind != JavaKind.Void) || kind == JavaKind.Object) {
                Class<?> javaClass = getJavaClass(kind);
                r.register(new RequiredInlineOnlyInvocationPlugin("blackhole", javaClass) {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                        b.add(new BlackholeNode(value));
                        return true;
                    }
                });
            }
        }

        r.register(new RequiredInvocationPlugin("castExact", Object.class, Class.class) {
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

        r.register(new RequiredInvocationPlugin("isExact", Object.class, Class.class) {
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

    public static void registerCompilerAssertsPlugins(InvocationPlugins plugins, KnownTruffleTypes types, boolean canDelayIntrinsification) {
        final ResolvedJavaType compilerAssertsType = types.CompilerAsserts;
        Registration r = new Registration(plugins, new ResolvedJavaSymbol(compilerAssertsType));
        r.register(new PEConstantPlugin(canDelayIntrinsification, Object.class));
        r.register(new PEConstantPlugin(canDelayIntrinsification, int.class));
        r.register(new PEConstantPlugin(canDelayIntrinsification, long.class));
        r.register(new PEConstantPlugin(canDelayIntrinsification, float.class));
        r.register(new PEConstantPlugin(canDelayIntrinsification, double.class));
        r.register(new PEConstantPlugin(canDelayIntrinsification, boolean.class));
        r.register(new RequiredInvocationPlugin("neverPartOfCompilation") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new NeverPartOfCompilationNode("CompilerAsserts.neverPartOfCompilation()"));
                return true;
            }
        });
        r.register(new RequiredInvocationPlugin("neverPartOfCompilation", String.class) {
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

    public static void registerOptimizedCallTargetPlugins(InvocationPlugins plugins, KnownTruffleTypes types, boolean canDelayIntrinsification) {
        final ResolvedJavaType optimizedCallTargetType = types.OptimizedCallTarget;
        Registration r = new Registration(plugins, new ResolvedJavaSymbol(optimizedCallTargetType));
        r.register(new RequiredInvocationPlugin("createFrame", new ResolvedJavaSymbol(types.FrameDescriptor), Object[].class) {
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
        r.register(new RequiredInvocationPlugin("castArrayFixedLength", Object[].class, int.class) {
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
        registerUnsafeCast(r, types, canDelayIntrinsification);
    }

    public static void registerFrameWithoutBoxingPlugins(InvocationPlugins plugins, KnownTruffleTypes types, boolean canDelayIntrinsification) {
        Registration r = new Registration(plugins, new ResolvedJavaSymbol(types.FrameWithoutBoxing));
        registerFrameMethods(r, types);
        registerUnsafeLoadStorePlugins(r, canDelayIntrinsification, null, JavaKind.Long, JavaKind.Object);
        registerFrameAccessors(r, types, JavaKind.Object);
        registerFrameAccessors(r, types, JavaKind.Long);
        registerFrameAccessors(r, types, JavaKind.Int);
        registerFrameAccessors(r, types, JavaKind.Double);
        registerFrameAccessors(r, types, JavaKind.Float);
        registerFrameAccessors(r, types, JavaKind.Boolean);
        registerFrameAccessors(r, types, JavaKind.Byte);

        int accessTag = types.FrameSlotKind_javaKindToTagIndex.get(JavaKind.Object);
        registerGet(r, JavaKind.Object, accessTag, "unsafeUncheckedGet" + JavaKind.Object.name(), true);

        registerOSRFrameTransferMethods(r);

        registerFrameTagAccessor(r);
        registerFrameAuxiliaryAccessors(types, r);

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
    private static void registerFrameAccessors(Registration r, KnownTruffleTypes types, JavaKind accessKind) {
        int accessTag = types.FrameSlotKind_javaKindToTagIndex.get(accessKind);
        String nameSuffix = accessKind.name();
        boolean isPrimitiveAccess = accessKind.isPrimitive();
        registerGet(r, accessKind, accessTag, "get" + nameSuffix, false);
        for (String prefix : new String[]{"unsafeGet", "expect", "unsafeExpect"}) {
            registerGet(r, accessKind, accessTag, prefix + nameSuffix, true);
        }
        r.register(new RequiredInvocationPlugin("get" + nameSuffix + "Static", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frameNode, ValueNode frameSlotNode) {
                int frameSlotIndex = maybeGetConstantNumberedFrameSlotIndex(frameNode, frameSlotNode);
                if (frameSlotIndex >= 0) {
                    b.addPush(accessKind, new VirtualFrameGetNode(frameNode, frameSlotIndex, accessKind, accessTag, VirtualFrameAccessType.Indexed,
                                    isPrimitiveAccess ? VirtualFrameAccessFlags.STATIC_PRIMITIVE : VirtualFrameAccessFlags.STATIC_OBJECT));
                    return true;
                }
                return false;
            }
        });

        registerSet(r, accessKind, accessTag, "set" + nameSuffix, false);
        registerSet(r, accessKind, accessTag, "unsafeSet" + nameSuffix, true);
        r.register(new RequiredInvocationPlugin("set" + nameSuffix + "Static", Receiver.class, int.class, getJavaClass(accessKind)) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frameNode, ValueNode frameSlotNode, ValueNode value) {
                int frameSlotIndex = maybeGetConstantNumberedFrameSlotIndex(frameNode, frameSlotNode);
                if (frameSlotIndex >= 0) {
                    b.add(new VirtualFrameSetNode(frameNode, frameSlotIndex, accessTag, value, VirtualFrameAccessType.Indexed,
                                    isPrimitiveAccess ? VirtualFrameAccessFlags.STATIC_PRIMITIVE_UPDATE : VirtualFrameAccessFlags.STATIC_OBJECT_UPDATE));
                    return true;
                }
                return false;
            }
        });
        r.register(new RequiredInvocationPlugin("is" + nameSuffix, Receiver.class, int.class) {
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

    private static void registerGet(Registration r, JavaKind accessKind, int accessTag, String name, boolean optional) {
        r.register(new InvocationPlugin(name, Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frameNode, ValueNode frameSlotNode) {
                int frameSlotIndex = maybeGetConstantNumberedFrameSlotIndex(frameNode, frameSlotNode);
                if (frameSlotIndex >= 0) {
                    b.addPush(accessKind, new VirtualFrameGetNode(frameNode, frameSlotIndex, accessKind, accessTag, VirtualFrameAccessType.Indexed, VirtualFrameAccessFlags.NON_STATIC));
                    return true;
                }
                return false;
            }

            @Override
            public boolean isOptional() {
                return optional;
            }
        });
    }

    private static void registerSet(Registration r, JavaKind accessKind, int accessTag, String name, boolean optional) {
        r.register(new InvocationPlugin(name, Receiver.class, int.class, getJavaClass(accessKind)) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frameNode, ValueNode frameSlotNode, ValueNode value) {
                int frameSlotIndex = maybeGetConstantNumberedFrameSlotIndex(frameNode, frameSlotNode);
                if (frameSlotIndex >= 0) {
                    b.add(new VirtualFrameSetNode(frameNode, frameSlotIndex, accessTag, value, VirtualFrameAccessType.Indexed, VirtualFrameAccessFlags.NON_STATIC_UPDATE));
                    return true;
                }
                return false;
            }

            @Override
            public boolean isOptional() {
                return optional;
            }
        });
    }

    private static void registerCopy(Registration r, String name, boolean optional) {
        r.register(new InvocationPlugin(name, Receiver.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode frameSlot1, ValueNode frameSlot2) {
                int frameSlot1Index = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot1);
                int frameSlot2Index = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot2);
                if (frameSlot1Index >= 0 && frameSlot2Index >= 0) {
                    b.add(new VirtualFrameCopyNode(receiver, frameSlot1Index, frameSlot2Index, VirtualFrameAccessType.Indexed, VirtualFrameAccessFlags.NON_STATIC_UPDATE));
                    return true;
                }
                return false;
            }

            @Override
            public boolean isOptional() {
                return optional;
            }
        });
    }

    private static void registerClear(Registration r, String name, int illegalTag, boolean optional) {
        r.register(new InvocationPlugin(name, Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode frameSlot) {
                int frameSlotIndex = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot);
                if (frameSlotIndex >= 0) {
                    b.add(new VirtualFrameClearNode(receiver, frameSlotIndex, illegalTag, VirtualFrameAccessType.Indexed,
                                    VirtualFrameAccessFlags.NON_STATIC_UPDATE));
                    return true;
                }
                return false;
            }

            @Override
            public boolean isOptional() {
                return optional;
            }
        });
    }

    private static void registerFrameTagAccessor(Registration r) {
        r.register(new RequiredInvocationPlugin("getTag", Receiver.class, int.class) {
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

    private static void registerFrameAuxiliaryAccessors(KnownTruffleTypes types, Registration r) {
        int objectTagIndex = types.FrameSlotKind_javaKindToTagIndex.get(JavaKind.Object);
        r.register(new RequiredInvocationPlugin("getAuxiliarySlot", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frameNode, ValueNode frameSlotNode) {
                int frameSlotIndex = maybeGetConstantNumberedFrameSlotIndex(frameNode, frameSlotNode);
                if (frameSlotIndex >= 0) {
                    b.addPush(JavaKind.Object,
                                    new VirtualFrameGetNode(frameNode, frameSlotIndex, JavaKind.Object, objectTagIndex, VirtualFrameAccessType.Auxiliary, VirtualFrameAccessFlags.NON_STATIC));
                    return true;
                }
                return false;
            }
        });

        r.register(new RequiredInvocationPlugin("setAuxiliarySlot", Receiver.class, int.class, Object.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frameNode, ValueNode frameSlotNode, ValueNode value) {
                int frameSlotIndex = maybeGetConstantNumberedFrameSlotIndex(frameNode, frameSlotNode);
                if (frameSlotIndex >= 0) {
                    b.add(new VirtualFrameSetNode(frameNode, frameSlotIndex, objectTagIndex, value, VirtualFrameAccessType.Auxiliary, VirtualFrameAccessFlags.NON_STATIC_UPDATE));
                    return true;
                }
                return false;
            }
        });
    }

    static int maybeGetConstantNumberedFrameSlotIndex(Receiver frameNode, ValueNode frameSlotNode) {
        if (frameSlotNode.isJavaConstant()) {
            if (frameNode.get(false) instanceof NewFrameNode newFrameNode) {
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

    private static void registerOSRFrameTransferMethods(Registration r) {
        r.register(new RequiredInvocationPlugin("startOSRTransfer", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frameNode) {
                if (frameNode.get(false) instanceof NewFrameNode newFrameNode) {
                    newFrameNode.setBytecodeOSRTransferTarget();
                    return true;
                }
                return false;
            }
        });
    }

    private static void registerFrameMethods(Registration r, KnownTruffleTypes types) {
        final int illegalTag = types.FrameSlotKind_javaKindToTagIndex.get(JavaKind.Illegal);

        r.register(new RequiredInvocationPlugin("getArguments", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frame) {
                if (frame.get(false) instanceof NewFrameNode newFrameNode) {
                    b.push(JavaKind.Object, newFrameNode.getArguments());
                    return true;
                }
                return false;
            }
        });

        r.register(new RequiredInvocationPlugin("getFrameDescriptor", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frame) {
                if (frame.get(false) instanceof NewFrameNode newFrameNode) {
                    b.push(JavaKind.Object, newFrameNode.getDescriptor());
                    return true;
                }
                return false;
            }
        });

        r.register(new RequiredInvocationPlugin("materialize", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode frame = receiver.get(true);
                if (frame instanceof NewFrameNode newFrameNode && newFrameNode.getIntrinsifyAccessors()) {
                    Speculation speculation = b.getGraph().getSpeculationLog().speculate(newFrameNode.getIntrinsifyAccessorsSpeculation());
                    b.add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.RuntimeConstraint, speculation));
                    return true;
                }

                b.addPush(JavaKind.Object, new AllowMaterializeNode(frame));
                return true;
            }
        });

        r.register(new RequiredInvocationPlugin("swap", Receiver.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode frameSlot1, ValueNode frameSlot2) {
                int frameSlot1Index = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot1);
                int frameSlot2Index = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot2);
                if (frameSlot1Index >= 0 && frameSlot2Index >= 0) {
                    b.add(new VirtualFrameSwapNode(receiver, frameSlot1Index, frameSlot2Index, VirtualFrameAccessType.Indexed, VirtualFrameAccessFlags.NON_STATIC_UPDATE));
                    return true;
                }
                return false;
            }
        });

        registerCopy(r, "copy", false);
        registerCopy(r, "unsafeCopy", true);
        registerClear(r, "clear", illegalTag, false);
        registerClear(r, "unsafeClear", illegalTag, true);

        r.register(new RequiredInvocationPlugin("clearPrimitiveStatic", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode frameSlot) {
                int frameSlotIndex = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot);
                if (frameSlotIndex >= 0) {
                    b.add(new VirtualFrameClearNode(receiver, frameSlotIndex, illegalTag, VirtualFrameAccessType.Indexed,
                                    VirtualFrameAccessFlags.STATIC_PRIMITIVE_UPDATE));
                    return true;
                }
                return false;
            }
        });
        r.register(new RequiredInvocationPlugin("clearObjectStatic", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode frameSlot) {
                int frameSlotIndex = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot);
                if (frameSlotIndex >= 0) {
                    b.add(new VirtualFrameClearNode(receiver, frameSlotIndex, illegalTag, VirtualFrameAccessType.Indexed,
                                    VirtualFrameAccessFlags.STATIC_OBJECT_UPDATE));
                    return true;
                }
                return false;
            }
        });
        r.register(new RequiredInvocationPlugin("clearStatic", Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode frameSlot) {
                int frameSlotIndex = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot);
                if (frameSlotIndex >= 0) {
                    b.add(new VirtualFrameClearNode(receiver, frameSlotIndex, illegalTag, VirtualFrameAccessType.Indexed,
                                    VirtualFrameAccessFlags.STATIC_BOTH_UPDATE));
                    return true;
                }
                return false;
            }
        });

        r.register(new RequiredInvocationPlugin("swapPrimitiveStatic", Receiver.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode frameSlot1, ValueNode frameSlot2) {
                int frameSlot1Index = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot1);
                int frameSlot2Index = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot2);
                if (frameSlot1Index >= 0 && frameSlot2Index >= 0) {
                    b.add(new VirtualFrameSwapNode(receiver, frameSlot1Index, frameSlot2Index, VirtualFrameAccessType.Indexed, VirtualFrameAccessFlags.STATIC_PRIMITIVE_UPDATE));
                    return true;
                }
                return false;
            }
        });
        r.register(new RequiredInvocationPlugin("swapObjectStatic", Receiver.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode frameSlot1, ValueNode frameSlot2) {
                int frameSlot1Index = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot1);
                int frameSlot2Index = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot2);
                if (frameSlot1Index >= 0 && frameSlot2Index >= 0) {
                    b.add(new VirtualFrameSwapNode(receiver, frameSlot1Index, frameSlot2Index, VirtualFrameAccessType.Indexed, VirtualFrameAccessFlags.STATIC_OBJECT_UPDATE));
                    return true;
                }
                return false;
            }
        });
        r.register(new RequiredInvocationPlugin("swapStatic", Receiver.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode frameSlot1, ValueNode frameSlot2) {
                int frameSlot1Index = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot1);
                int frameSlot2Index = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot2);
                if (frameSlot1Index >= 0 && frameSlot2Index >= 0) {
                    b.add(new VirtualFrameSwapNode(receiver, frameSlot1Index, frameSlot2Index, VirtualFrameAccessType.Indexed, VirtualFrameAccessFlags.STATIC_BOTH_UPDATE));
                    return true;
                }
                return false;
            }
        });
        r.register(new RequiredInvocationPlugin("copyPrimitiveStatic", Receiver.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode frameSlot1, ValueNode frameSlot2) {
                int frameSlot1Index = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot1);
                int frameSlot2Index = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot2);
                if (frameSlot1Index >= 0 && frameSlot2Index >= 0) {
                    b.add(new VirtualFrameCopyNode(receiver, frameSlot1Index, frameSlot2Index, VirtualFrameAccessType.Indexed, VirtualFrameAccessFlags.STATIC_PRIMITIVE_UPDATE));
                    return true;
                }
                return false;
            }
        });
        r.register(new RequiredInvocationPlugin("copyObjectStatic", Receiver.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode frameSlot1, ValueNode frameSlot2) {
                int frameSlot1Index = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot1);
                int frameSlot2Index = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot2);
                if (frameSlot1Index >= 0 && frameSlot2Index >= 0) {
                    b.add(new VirtualFrameCopyNode(receiver, frameSlot1Index, frameSlot2Index, VirtualFrameAccessType.Indexed, VirtualFrameAccessFlags.STATIC_OBJECT_UPDATE));
                    return true;
                }
                return false;
            }
        });
        r.register(new RequiredInvocationPlugin("copyStatic", Receiver.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode frameSlot1, ValueNode frameSlot2) {
                int frameSlot1Index = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot1);
                int frameSlot2Index = maybeGetConstantNumberedFrameSlotIndex(receiver, frameSlot2);
                if (frameSlot1Index >= 0 && frameSlot2Index >= 0) {
                    b.add(new VirtualFrameCopyNode(receiver, frameSlot1Index, frameSlot2Index, VirtualFrameAccessType.Indexed, VirtualFrameAccessFlags.STATIC_BOTH_UPDATE));
                    return true;
                }
                return false;
            }
        });
        r.register(new RequiredInvocationPlugin("extend", int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.addPush(JavaKind.Long, new AnyExtendNode(value));
                return true;
            }
        });
        r.register(new RequiredInvocationPlugin("narrow", long.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.addPush(JavaKind.Int, new AnyNarrowNode(value));
                return true;
            }
        });
    }

    public static void registerNodePlugins(InvocationPlugins plugins, KnownTruffleTypes types, MetaAccessProvider metaAccess, boolean canDelayIntrinsification,
                    ConstantReflectionProvider constantReflection) {
        Registration r = new Registration(plugins, new ResolvedJavaSymbol(types.Node));
        r.register(new RequiredInvocationPlugin("getRootNodeImpl", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (canDelayIntrinsification) {
                    return false;
                }

                ValueNode thisValue = receiver.get(false);
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
                    parentNode = constantReflection.readFieldValue(types.Node_parent, prevNode);
                } while (parentNode.isNonNull());

                JavaConstant rootNode = prevNode;
                ConstantNode result = ConstantNode.forConstant(rootNode, metaAccess, b.getGraph());
                // getRootNodeImpl() returns null if parent is not an instance of RootNode.
                if (rootNode.isNonNull() && !types.RootNode.isAssignableFrom(result.stamp(NodeView.DEFAULT).javaType(metaAccess))) {
                    result = ConstantNode.defaultForKind(JavaKind.Object, b.getGraph());
                }
                b.addPush(JavaKind.Object, result);
                return true;
            }
        });
    }

    private static void registerBufferPlugins(InvocationPlugins plugins, KnownTruffleTypes types, boolean canDelayIntrinsification) {
        Registration r = new Registration(plugins, new ResolvedJavaSymbol(types.Buffer));

        final class CreateExceptionPlugin extends RequiredInvocationPlugin {
            CreateExceptionPlugin(String name, Type... argumentTypes) {
                super(name, argumentTypes);
            }

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode newLimit) {
                if (canDelayIntrinsification || b.needsExplicitException()) {
                    return false;
                }
                b.add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.RuntimeConstraint));
                return true;
            }
        }

        r.register(new CreateExceptionPlugin("createLimitException", Receiver.class, int.class));
        r.register(new CreateExceptionPlugin("createPositionException", Receiver.class, int.class));
    }

    private static final SpeculationReasonGroup BUFFER_SEGMENT_NULL_SPECULATION = new SpeculationReasonGroup("BufferSegmentNull");

    private static void registerMemorySegmentPlugins(InvocationPlugins plugins, KnownTruffleTypes types, boolean canDelayIntrinsification) {
        ResolvedJavaType memorySegmentImplType = types.AbstractMemorySegmentImpl;
        if (memorySegmentImplType != null) {
            Registration r = new Registration(plugins, new ResolvedJavaSymbol(memorySegmentImplType));
            r.register(new OptionalInvocationPlugin("sessionImpl", Receiver.class) {
                /**
                 * ByteBuffer methods and VarHandles use the following code pattern to get any
                 * memory session that needs to be checked:
                 *
                 * <pre>
                 * {@code
                 * MemorySessionImpl session() {
                 *     if (segment != null) {
                 *         return ((AbstractMemorySegmentImpl) segment).sessionImpl();
                 *     } else {
                 *         return null;
                 *     }
                 * }
                 * }
                 * </pre>
                 *
                 * In order to optimize for the case where the ByteBuffer was not obtained from a
                 * memory segment and we can skip the memory session check, we insert a
                 * deoptimization in {@code sessionImpl()}, speculating that the {@code segment}
                 * field will always be null so that we will never reach the branch checking the
                 * memory session. Note that {@code sessionImpl()} is also used by memory segment
                 * views, so we need to make sure the segment was actually loaded from a Buffer.
                 */
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    SpeculationLog speculationLog = b.getGraph().getSpeculationLog();
                    if (!canDelayIntrinsification && speculationLog != null) {
                        ValueNode segment = receiver.get(false);
                        Stamp stamp = segment.stamp(NodeView.DEFAULT);
                        if (stamp instanceof ObjectStamp && !((ObjectStamp) stamp).nonNull() && !((ObjectStamp) stamp).alwaysNull()) {
                            ValueNode load = GraphUtil.unproxify(segment);
                            SpeculationLog.SpeculationReason bufferSegmentNullSpeculationReason = BUFFER_SEGMENT_NULL_SPECULATION.createSpeculationReason();
                            if (load instanceof LoadFieldNode && types.Buffer_segment.equals(((LoadFieldNode) load).field()) &&
                                            speculationLog.maySpeculate(bufferSegmentNullSpeculationReason)) {
                                Speculation speculation = speculationLog.speculate(bufferSegmentNullSpeculationReason);
                                b.add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.UnreachedCode, speculation));
                                return true;
                            }
                        }
                    }
                    return false;
                }
            });
        }
    }

    private static void registerDynamicObjectPlugins(InvocationPlugins plugins, KnownTruffleTypes types,
                    boolean canDelayIntrinsification, ConstantReflectionProvider constantReflection) {
        if (!types.UnsafeAccess.isInitialized()) {
            types.UnsafeAccess.initialize();
        }

        ResolvedJavaField[] staticFields = types.UnsafeAccess.getStaticFields();
        JavaConstant anyConstant = null;
        for (ResolvedJavaField field : staticFields) {
            if (field.getName().equals("ANY_LOCATION")) {
                anyConstant = constantReflection.readFieldValue(field, null);
                break;
            }
        }

        JavaKind[] usedJavaKinds = {JavaKind.Boolean, JavaKind.Byte, JavaKind.Int, JavaKind.Short, JavaKind.Long, JavaKind.Float, JavaKind.Double, JavaKind.Object};

        Registration r = new Registration(plugins, new ResolvedJavaSymbol(types.UnsafeAccess));
        registerUnsafeLoadStorePlugins(r, canDelayIntrinsification, anyConstant, usedJavaKinds);
        registerUnsafeCast(r, types, canDelayIntrinsification);
        registerBooleanCast(r);
        registerArrayCopy(r);

        registerDynamicObjectShapePlugins(plugins, types, canDelayIntrinsification);
    }

    private static void registerDynamicObjectShapePlugins(InvocationPlugins plugins, KnownTruffleTypes types, boolean canDelayIntrinsification) {
        ResolvedJavaType dynamicObjectType = types.DynamicObject;
        ResolvedJavaType shapeType = types.Shape;
        ResolvedJavaSymbol dynamicObjectSymbol = new ResolvedJavaSymbol(dynamicObjectType);
        ResolvedJavaSymbol shapeSymbol = new ResolvedJavaSymbol(shapeType);
        Registration r = new Registration(plugins, dynamicObjectSymbol);
        r.register(new RequiredInvocationPlugin("getShapeHelper", shapeSymbol) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode shape) {
                Stamp piStamp = StampFactory.object(TypeReference.createTrusted(b.getAssumptions(), shapeType), true);
                ValueNode piNode = PiNode.create(shape, piStamp);
                b.addPush(JavaKind.Object, piNode);
                return true;
            }
        });
        r.register(new RequiredInvocationPlugin("setShapeHelper", Receiver.class, shapeSymbol, long.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode shape, ValueNode shapeOffset) {
                if (canDelayIntrinsification) {
                    return false;
                }
                if (!shapeOffset.isConstant()) {
                    return false;
                }
                LocationIdentity locationIdentity = LocationIdentity.any();
                boolean forceAnyLocation = true;
                b.add(new RawStoreNode(receiver.get(true), shapeOffset, shape, JavaKind.Object, locationIdentity, true, null, forceAnyLocation));
                return true;
            }
        });
    }

    private static void registerBooleanCast(Registration r) {
        r.register(new RequiredInvocationPlugin("booleanCast", int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.addPush(JavaKind.Boolean, value);
                return true;
            }
        });
        r.register(new RequiredInvocationPlugin("intCast", boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.addPush(JavaKind.Int, value);
                return true;
            }
        });
    }

    private static void registerArrayCopy(Registration r) {
        ArrayCopySnippets.registerSystemArraycopyPlugin(r, true);
    }

    public static void registerUnsafeCast(Registration r, KnownTruffleTypes types, boolean canDelayIntrinsification) {
        r.register(new RequiredInvocationPlugin("unsafeCast", Object.class, Class.class, boolean.class, boolean.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object, ValueNode clazz, ValueNode condition, ValueNode nonNull,
                            ValueNode isExactType) {
                if (clazz.isConstant() && nonNull.isConstant() && isExactType.isConstant()) {
                    if (!Options.TruffleTrustedTypeCast.getValue(b.getOptions())) {
                        b.push(JavaKind.Object, object);
                        return true;
                    }
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

                        boolean trustedNonNull = nonNull.asJavaConstant().asInt() != 0 && Options.TruffleTrustedNonNullCast.getValue(b.getOptions());
                        Stamp piStamp = StampFactory.object(type, trustedNonNull);

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

                        b.addPush(JavaKind.Object, trustedBox(type, types, PiNode.create(object, piStamp, valueAnchorNode)));
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

    private static ValueNode trustedBox(TypeReference type, KnownTruffleTypes types, ValueNode v) {
        if (types.primitiveBoxTypes.contains(type.getType())) {
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
            r.register(new CustomizedUnsafeLoadPlugin(kind, canDelayIntrinsification,
                            getName, Object.class, long.class, boolean.class, Object.class));
            r.register(new CustomizedUnsafeStorePlugin(kind, anyConstant, canDelayIntrinsification,
                            putName, Object.class, long.class, getJavaClass(kind), Object.class));
        }
    }

    static class CustomizedUnsafeLoadPlugin extends RequiredInvocationPlugin {

        private final JavaKind returnKind;
        private final boolean canDelayIntrinsification;

        CustomizedUnsafeLoadPlugin(JavaKind returnKind, boolean canDelayIntrinsification, String name, Type... argumentTypes) {
            super(name, argumentTypes);
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
                ValueNode guard = null;
                // If the condition is the constant true then no guard is needed
                if (!condition.isConstant() || condition.asJavaConstant().asInt() == 0) {
                    LogicNode compare = b.add(CompareNode.createCompareNode(b.getConstantReflection(), b.getMetaAccess(), b.getOptions(), null, CanonicalCondition.EQ, condition,
                                    ConstantNode.forBoolean(true, object.graph()), NodeView.DEFAULT));
                    guard = b.add(new ConditionAnchorNode(compare));
                }
                b.addPush(returnKind, b.add(new GuardedUnsafeLoadNode(b.addNonNullCast(object), offset, returnKind, locationIdentity, guard, forceLocation)));
                return true;
            } else if (canDelayIntrinsification) {
                return false;
            } else {
                RawLoadNode load = b.addPush(returnKind, new RawLoadNode(object, offset, returnKind, LocationIdentity.any(), true, MemoryOrderMode.PLAIN));
                logPerformanceWarningLocationNotConstant(location, targetMethod, load);
                return true;
            }
        }
    }

    static class CustomizedUnsafeStorePlugin extends RequiredInvocationPlugin {

        private final JavaKind kind;
        private final JavaConstant anyConstant;
        private final boolean canDelayIntrinsification;

        CustomizedUnsafeStorePlugin(JavaKind kind, JavaConstant anyConstant, boolean canDelayIntrinsification, String name, Type... argumentTypes) {
            super(name, argumentTypes);
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

    private static final class PEConstantPlugin extends RequiredInvocationPlugin {
        private final boolean canDelayIntrinsification;

        private PEConstantPlugin(boolean canDelayIntrinsification, Type... argumentTypes) {
            super("partialEvaluationConstant", argumentTypes);
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
