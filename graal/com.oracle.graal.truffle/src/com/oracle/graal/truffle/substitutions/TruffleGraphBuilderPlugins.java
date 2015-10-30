/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.truffle.substitutions;

import static java.lang.Character.toUpperCase;

import java.util.concurrent.Callable;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.LocationIdentity;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.compiler.common.calc.Condition;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.graphbuilderconf.InvocationPlugin.Receiver;
import com.oracle.graal.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.nodes.CallTargetNode;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.ConditionAnchorNode;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.DeoptimizeNode;
import com.oracle.graal.nodes.InvokeNode;
import com.oracle.graal.nodes.LogicConstantNode;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.PiArrayNode;
import com.oracle.graal.nodes.PiNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.ValuePhiNode;
import com.oracle.graal.nodes.calc.CompareNode;
import com.oracle.graal.nodes.extended.BoxNode;
import com.oracle.graal.nodes.extended.BranchProbabilityNode;
import com.oracle.graal.nodes.extended.UnsafeLoadNode;
import com.oracle.graal.nodes.extended.UnsafeStoreNode;
import com.oracle.graal.nodes.java.MethodCallTargetNode;
import com.oracle.graal.nodes.virtual.EnsureVirtualizedNode;
import com.oracle.graal.replacements.nodes.arithmetic.IntegerAddExactNode;
import com.oracle.graal.replacements.nodes.arithmetic.IntegerMulExactNode;
import com.oracle.graal.replacements.nodes.arithmetic.IntegerMulHighNode;
import com.oracle.graal.replacements.nodes.arithmetic.IntegerSubExactNode;
import com.oracle.graal.replacements.nodes.arithmetic.UnsignedMulHighNode;
import com.oracle.graal.truffle.FrameWithBoxing;
import com.oracle.graal.truffle.FrameWithoutBoxing;
import com.oracle.graal.truffle.OptimizedAssumption;
import com.oracle.graal.truffle.OptimizedCallTarget;
import com.oracle.graal.truffle.TruffleCompilerOptions;
import com.oracle.graal.truffle.nodes.AssumptionValidAssumption;
import com.oracle.graal.truffle.nodes.IsCompilationConstantNode;
import com.oracle.graal.truffle.nodes.ObjectLocationIdentity;
import com.oracle.graal.truffle.nodes.asserts.NeverPartOfCompilationNode;
import com.oracle.graal.truffle.nodes.frame.ForceMaterializeNode;
import com.oracle.graal.truffle.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.truffle.nodes.frame.NewFrameNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ExactMath;
import com.oracle.truffle.api.frame.FrameDescriptor;

/**
 * Provides {@link InvocationPlugin}s for Truffle classes.
 */
public class TruffleGraphBuilderPlugins {
    public static void registerInvocationPlugins(MetaAccessProvider metaAccess, InvocationPlugins plugins, boolean canDelayIntrinsification, SnippetReflectionProvider snippetReflection) {

        registerOptimizedAssumptionPlugins(plugins, snippetReflection);
        registerExactMathPlugins(plugins);
        registerCompilerDirectivesPlugins(plugins, canDelayIntrinsification);
        registerCompilerAssertsPlugins(plugins, canDelayIntrinsification);
        registerOptimizedCallTargetPlugins(metaAccess, plugins, snippetReflection, canDelayIntrinsification);

        if (TruffleCompilerOptions.TruffleUseFrameWithoutBoxing.getValue()) {
            registerFrameWithoutBoxingPlugins(plugins, canDelayIntrinsification);
        } else {
            registerFrameWithBoxingPlugins(plugins, canDelayIntrinsification);
        }

    }

    public static void registerOptimizedAssumptionPlugins(InvocationPlugins plugins, SnippetReflectionProvider snippetReflection) {
        Registration r = new Registration(plugins, OptimizedAssumption.class);
        InvocationPlugin plugin = new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (receiver.isConstant() && b.getAssumptions() != null) {
                    Constant constant = receiver.get().asConstant();
                    OptimizedAssumption assumption = snippetReflection.asObject(OptimizedAssumption.class, (JavaConstant) constant);
                    if (assumption.isValid()) {
                        if (targetMethod.getName().equals("isValid")) {
                            b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(true));
                        } else {
                            assert targetMethod.getName().equals("check") : targetMethod;
                        }
                        b.getAssumptions().record(new AssumptionValidAssumption(assumption));
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

    public static void registerExactMathPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, ExactMath.class);
        for (JavaKind kind : new JavaKind[]{JavaKind.Int, JavaKind.Long}) {
            Class<?> type = kind.toJavaClass();
            r.register2("addExact", type, type, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.addPush(kind, new IntegerAddExactNode(x, y));
                    return true;
                }
            });
            r.register2("subtractExact", type, type, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.addPush(kind, new IntegerSubExactNode(x, y));
                    return true;
                }
            });
            r.register2("multiplyExact", type, type, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.addPush(kind, new IntegerMulExactNode(x, y));
                    return true;
                }
            });
            r.register2("multiplyHigh", type, type, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.addPush(kind, new IntegerMulHighNode(x, y));
                    return true;
                }
            });
            r.register2("multiplyHighUnsigned", type, type, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.addPush(kind, new UnsignedMulHighNode(x, y));
                    return true;
                }
            });
        }
    }

    public static void registerCompilerDirectivesPlugins(InvocationPlugins plugins, boolean canDelayIntrinsification) {
        Registration r = new Registration(plugins, CompilerDirectives.class);
        r.register0("inInterpreter", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(false));
                return true;
            }
        });
        r.register0("inCompiledCode", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(true));
                return true;
            }
        });
        r.register0("transferToInterpreter", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new DeoptimizeNode(DeoptimizationAction.None, DeoptimizationReason.TransferToInterpreter));
                return true;
            }
        });
        r.register0("transferToInterpreterAndInvalidate", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.TransferToInterpreter));
                return true;
            }
        });
        r.register1("interpreterOnly", Runnable.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                return true;
            }
        });
        r.register1("interpreterOnly", Callable.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                return true;
            }
        });
        r.register2("injectBranchProbability", double.class, boolean.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode probability, ValueNode condition) {
                b.addPush(JavaKind.Boolean, new BranchProbabilityNode(probability, condition));
                return true;
            }
        });
        r.register1("bailout", String.class, new InvocationPlugin() {
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
                    CallTargetNode callTarget = b.add(new MethodCallTargetNode(InvokeKind.Static, targetMethod, new ValueNode[]{message}, targetMethod.getSignature().getReturnType(null), null));
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
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.add(new ForceMaterializeNode(value));
                return true;
            }
        });
        r.register1("ensureVirtualized", Object.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                b.add(new EnsureVirtualizedNode(object, false));
                return true;
            }
        });
        r.register1("ensureVirtualizedHere", Object.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                b.add(new EnsureVirtualizedNode(object, true));
                return true;
            }
        });
    }

    public static void registerCompilerAssertsPlugins(InvocationPlugins plugins, boolean canDelayIntrinsification) {
        Registration r = new Registration(plugins, CompilerAsserts.class);
        r.register1("partialEvaluationConstant", Object.class, new InvocationPlugin() {
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
                    StringBuilder sb = new StringBuilder();
                    sb.append(curValue);
                    if (curValue instanceof ValuePhiNode) {
                        ValuePhiNode valuePhi = (ValuePhiNode) curValue;
                        sb.append(" (");
                        for (Node n : valuePhi.inputs()) {
                            sb.append(n);
                            sb.append("; ");
                        }
                        sb.append(")");
                    }
                    throw b.bailout("Partial evaluation did not reduce value to a constant, is a regular compiler node: " + sb.toString());
                }
            }
        });
        r.register0("neverPartOfCompilation", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new NeverPartOfCompilationNode("CompilerAsserts.neverPartOfCompilation()"));
                return true;
            }
        });
        r.register1("neverPartOfCompilation", String.class, new InvocationPlugin() {
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

    public static void registerOptimizedCallTargetPlugins(MetaAccessProvider metaAccess, InvocationPlugins plugins, SnippetReflectionProvider snippetReflection, boolean canDelayIntrinsification) {
        Registration r = new Registration(plugins, OptimizedCallTarget.class);
        r.register2("createFrame", FrameDescriptor.class, Object[].class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode descriptor, ValueNode args) {
                Class<?> frameClass = TruffleCompilerOptions.TruffleUseFrameWithoutBoxing.getValue() ? FrameWithoutBoxing.class : FrameWithBoxing.class;
                b.addPush(JavaKind.Object, new NewFrameNode(snippetReflection, StampFactory.exactNonNull(metaAccess.lookupJavaType(frameClass)), descriptor, args));
                return true;
            }
        });
        r.register2("castArrayFixedLength", Object[].class, int.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode args, ValueNode length) {
                b.addPush(JavaKind.Object, new PiArrayNode(args, length, args.stamp()));
                return true;
            }
        });
        registerUnsafeCast(r, canDelayIntrinsification);
    }

    public static void registerFrameWithoutBoxingPlugins(InvocationPlugins plugins, boolean canDelayIntrinsification) {
        Registration r = new Registration(plugins, FrameWithoutBoxing.class);
        registerMaterialize(r);
        registerUnsafeCast(r, canDelayIntrinsification);
        registerUnsafeLoadStorePlugins(r, JavaKind.Int, JavaKind.Long, JavaKind.Float, JavaKind.Double, JavaKind.Object);
    }

    public static void registerFrameWithBoxingPlugins(InvocationPlugins plugins, boolean canDelayIntrinsification) {
        Registration r = new Registration(plugins, FrameWithBoxing.class);
        registerMaterialize(r);
        registerUnsafeCast(r, canDelayIntrinsification);
    }

    private static void registerMaterialize(Registration r) {
        r.register1("materialize", Receiver.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frame) {
                b.addPush(JavaKind.Object, new MaterializeFrameNode(frame.get()));
                return true;
            }
        });
    }

    public static void registerUnsafeCast(Registration r, boolean canDelayIntrinsification) {
        r.register4("unsafeCast", Object.class, Class.class, boolean.class, boolean.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object, ValueNode clazz, ValueNode condition, ValueNode nonNull) {
                if (clazz.isConstant() && nonNull.isConstant()) {
                    ConstantReflectionProvider constantReflection = b.getConstantReflection();
                    ResolvedJavaType javaType = constantReflection.asJavaType(clazz.asConstant());
                    if (javaType == null) {
                        b.push(JavaKind.Object, object);
                    } else {
                        Stamp piStamp = null;
                        if (javaType.isArray()) {
                            if (nonNull.asJavaConstant().asInt() != 0) {
                                piStamp = StampFactory.exactNonNull(javaType);
                            } else {
                                piStamp = StampFactory.exact(javaType);
                            }
                        } else {
                            piStamp = StampFactory.declaredTrusted(javaType, nonNull.asJavaConstant().asInt() != 0);
                        }
                        LogicNode compareNode = CompareNode.createCompareNode(object.graph(), Condition.EQ, condition, ConstantNode.forBoolean(true, object.graph()), constantReflection);
                        boolean skipAnchor = false;
                        if (compareNode instanceof LogicConstantNode) {
                            LogicConstantNode logicConstantNode = (LogicConstantNode) compareNode;
                            if (logicConstantNode.getValue()) {
                                skipAnchor = true;
                            }
                        }
                        ConditionAnchorNode valueAnchorNode = null;
                        if (!skipAnchor) {
                            valueAnchorNode = b.add(new ConditionAnchorNode(compareNode));
                        }
                        b.addPush(JavaKind.Object, new PiNode(object, piStamp, valueAnchorNode));
                    }
                    return true;
                } else if (canDelayIntrinsification) {
                    return false;
                } else {
                    throw b.bailout("unsafeCast arguments could not reduce to a constant: " + clazz + ", " + nonNull);
                }
            }
        });
    }

    public static void registerUnsafeLoadStorePlugins(Registration r, JavaKind... kinds) {
        for (JavaKind kind : kinds) {
            String kindName = kind.getJavaName();
            kindName = toUpperCase(kindName.charAt(0)) + kindName.substring(1);
            String getName = "unsafeGet" + kindName;
            String putName = "unsafePut" + kindName;
            r.register4(getName, Object.class, long.class, boolean.class, Object.class, new CustomizedUnsafeLoadPlugin(kind));
            r.register4(putName, Object.class, long.class, kind == JavaKind.Object ? Object.class : kind.toJavaClass(), Object.class, new CustomizedUnsafeStorePlugin(kind));
        }
    }

    static class CustomizedUnsafeLoadPlugin implements InvocationPlugin {

        private final JavaKind returnKind;

        public CustomizedUnsafeLoadPlugin(JavaKind returnKind) {
            this.returnKind = returnKind;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object, ValueNode offset, ValueNode condition, ValueNode location) {
            if (location.isConstant()) {
                LocationIdentity locationIdentity;
                if (location.isNullConstant()) {
                    locationIdentity = LocationIdentity.any();
                } else {
                    locationIdentity = ObjectLocationIdentity.create(location.asJavaConstant());
                }
                LogicNode compare = b.add(CompareNode.createCompareNode(Condition.EQ, condition, ConstantNode.forBoolean(true, object.graph()), b.getConstantReflection()));
                b.addPush(returnKind, b.add(new UnsafeLoadNode(object, offset, returnKind, locationIdentity, compare)));
                return true;
            }
            // TODO: should we throw b.bailout() here?
            return false;
        }
    }

    static class CustomizedUnsafeStorePlugin implements InvocationPlugin {

        private final JavaKind kind;

        public CustomizedUnsafeStorePlugin(JavaKind kind) {
            this.kind = kind;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object, ValueNode offset, ValueNode value, ValueNode location) {
            ValueNode locationArgument = location;
            if (locationArgument.isConstant()) {
                LocationIdentity locationIdentity;
                if (locationArgument.isNullConstant()) {
                    locationIdentity = LocationIdentity.any();
                } else {
                    locationIdentity = ObjectLocationIdentity.create(locationArgument.asJavaConstant());
                }

                b.add(new UnsafeStoreNode(object, offset, value, kind, locationIdentity, null));
                return true;
            }
            // TODO: should we throw b.bailout() here?
            return false;
        }
    }
}
