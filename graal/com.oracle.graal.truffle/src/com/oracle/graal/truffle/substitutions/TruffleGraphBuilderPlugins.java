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

import static java.lang.Character.*;

import java.util.concurrent.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Receiver;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.replacements.nodes.arithmetic.*;
import com.oracle.graal.truffle.*;
import com.oracle.graal.truffle.nodes.*;
import com.oracle.graal.truffle.nodes.asserts.*;
import com.oracle.graal.truffle.nodes.frame.*;
import com.oracle.graal.truffle.unsafe.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;

/**
 * Provides {@link InvocationPlugin}s for Truffle classes.
 */
public class TruffleGraphBuilderPlugins {
    public static void registerInvocationPlugins(MetaAccessProvider metaAccess, InvocationPlugins plugins, boolean canDelayIntrinsification, SnippetReflectionProvider snippetReflection) {

        registerOptimizedAssumptionPlugins(plugins, canDelayIntrinsification, snippetReflection);
        registerExactMathPlugins(plugins);
        registerCompilerDirectivesPlugins(plugins);
        registerCompilerAssertsPlugins(plugins, canDelayIntrinsification);
        registerOptimizedCallTargetPlugins(metaAccess, plugins);
        registerUnsafeAccessImplPlugins(plugins, canDelayIntrinsification);

        if (TruffleCompilerOptions.TruffleUseFrameWithoutBoxing.getValue()) {
            registerFrameWithoutBoxingPlugins(plugins, canDelayIntrinsification);
        } else {
            registerFrameWithBoxingPlugins(plugins, canDelayIntrinsification);
        }

    }

    public static void registerOptimizedAssumptionPlugins(InvocationPlugins plugins, boolean canDelayIntrinsification, SnippetReflectionProvider snippetReflection) {
        Registration r = new Registration(plugins, OptimizedAssumption.class);
        InvocationPlugin plugin = new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (receiver.isConstant()) {
                    Constant constant = receiver.get().asConstant();
                    OptimizedAssumption assumption = snippetReflection.asObject(OptimizedAssumption.class, (JavaConstant) constant);
                    if (assumption.isValid()) {
                        if (targetMethod.getName().equals("isValid")) {
                            b.addPush(ConstantNode.forBoolean(true));
                        } else {
                            assert targetMethod.getName().equals("check") : targetMethod;
                        }
                        b.getAssumptions().record(new AssumptionValidAssumption(assumption));
                    } else {
                        if (targetMethod.getName().equals("isValid")) {
                            b.addPush(ConstantNode.forBoolean(false));
                        } else {
                            assert targetMethod.getName().equals("check") : targetMethod;
                            b.add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.None));
                        }
                    }
                    return true;
                } else if (canDelayIntrinsification) {
                    return false;
                } else {
                    throw b.bailout("assumption could not be reduced to a constant");
                }
            }
        };
        r.register1("isValid", Receiver.class, plugin);
        r.register1("check", Receiver.class, plugin);
    }

    public static void registerExactMathPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, ExactMath.class);
        for (Kind kind : new Kind[]{Kind.Int, Kind.Long}) {
            Class<?> type = kind.toJavaClass();
            r.register2("addExact", type, type, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.addPush(kind.getStackKind(), new IntegerAddExactNode(x, y));
                    return true;
                }
            });
            r.register2("subtractExact", type, type, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.addPush(kind.getStackKind(), new IntegerSubExactNode(x, y));
                    return true;
                }
            });
            r.register2("multiplyExact", type, type, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.addPush(kind.getStackKind(), new IntegerMulExactNode(x, y));
                    return true;
                }
            });
            r.register2("multiplyHigh", type, type, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.addPush(kind.getStackKind(), new IntegerMulHighNode(x, y));
                    return true;
                }
            });
            r.register2("multiplyHighUnsigned", type, type, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.addPush(kind.getStackKind(), new UnsignedMulHighNode(x, y));
                    return true;
                }
            });
        }
    }

    public static void registerCompilerDirectivesPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, CompilerDirectives.class);
        r.register0("inInterpreter", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(Kind.Boolean.getStackKind(), ConstantNode.forBoolean(false));
                return true;
            }
        });
        r.register0("inCompiledCode", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(Kind.Boolean.getStackKind(), ConstantNode.forBoolean(true));
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
                b.addPush(Kind.Boolean.getStackKind(), new BranchProbabilityNode(probability, condition));
                return true;
            }
        });
        r.register1("bailout", String.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode message) {
                if (message.isConstant()) {
                    throw b.bailout(message.asConstant().toValueString());
                }
                throw b.bailout("bailout (message is not compile-time constant, so no additional information is available)");
            }
        });
        r.register1("isCompilationConstant", Object.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                if ((value instanceof BoxNode ? ((BoxNode) value).getValue() : value).isConstant()) {
                    b.addPush(Kind.Boolean.getStackKind(), ConstantNode.forBoolean(true));
                } else {
                    b.addPush(Kind.Boolean.getStackKind(), new IsCompilationConstantNode(value));
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
        r.register1("neverPartOfCompilation", String.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode message) {
                if (message.isConstant()) {
                    String messageString = message.asConstant().toValueString();
                    b.add(new NeverPartOfCompilationNode(messageString, b.createStateAfter()));
                    return true;
                } else if (canDelayIntrinsification) {
                    return false;
                } else {
                    throw b.bailout("message for never part of compilation is non-constant");
                }
            }
        });
    }

    public static void registerOptimizedCallTargetPlugins(MetaAccessProvider metaAccess, InvocationPlugins plugins) {
        Registration r = new Registration(plugins, OptimizedCallTarget.class);
        r.register2("createFrame", FrameDescriptor.class, Object[].class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode descriptor, ValueNode args) {
                Class<?> frameClass = TruffleCompilerOptions.TruffleUseFrameWithoutBoxing.getValue() ? FrameWithoutBoxing.class : FrameWithBoxing.class;
                b.addPush(Kind.Object, new NewFrameNode(StampFactory.exactNonNull(metaAccess.lookupJavaType(frameClass)), descriptor, args));
                return true;
            }
        });
        r.register2("castArrayFixedLength", Object[].class, int.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode args, ValueNode length) {
                b.addPush(Kind.Object, new PiArrayNode(args, length, args.stamp()));
                return true;
            }
        });
    }

    public static void registerFrameWithoutBoxingPlugins(InvocationPlugins plugins, boolean canDelayIntrinsification) {
        Registration r = new Registration(plugins, FrameWithoutBoxing.class);
        registerMaterialize(r);
        registerUnsafeCast(r, canDelayIntrinsification);
        registerUnsafeLoadStorePlugins(r, Kind.Int, Kind.Long, Kind.Float, Kind.Double, Kind.Object);
    }

    public static void registerFrameWithBoxingPlugins(InvocationPlugins plugins, boolean canDelayIntrinsification) {
        Registration r = new Registration(plugins, FrameWithBoxing.class);
        registerMaterialize(r);
        registerUnsafeCast(r, canDelayIntrinsification);
    }

    public static void registerUnsafeAccessImplPlugins(InvocationPlugins plugins, boolean canDelayIntrinsification) {
        Registration r = new Registration(plugins, UnsafeAccessImpl.class);
        registerUnsafeCast(r, canDelayIntrinsification);
        registerUnsafeLoadStorePlugins(r, Kind.Boolean, Kind.Byte, Kind.Int, Kind.Short, Kind.Long, Kind.Float, Kind.Double, Kind.Object);
    }

    private static void registerMaterialize(Registration r) {
        r.register1("materialize", Receiver.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver frame) {
                b.addPush(Kind.Object, new MaterializeFrameNode(frame.get()));
                return true;
            }
        });
    }

    private static void registerUnsafeCast(Registration r, boolean canDelayIntrinsification) {
        r.register4("unsafeCast", Object.class, Class.class, boolean.class, boolean.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object, ValueNode clazz, ValueNode condition, ValueNode nonNull) {
                if (clazz.isConstant() && nonNull.isConstant()) {
                    ConstantReflectionProvider constantReflection = b.getConstantReflection();
                    ResolvedJavaType javaType = constantReflection.asJavaType(clazz.asConstant());
                    if (javaType == null) {
                        b.push(Kind.Object, object);
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
                        b.addPush(Kind.Object, new PiNode(object, piStamp, valueAnchorNode));
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

    protected static void registerUnsafeLoadStorePlugins(Registration r, Kind... kinds) {
        for (Kind kind : kinds) {
            String kindName = kind.getJavaName();
            kindName = toUpperCase(kindName.charAt(0)) + kindName.substring(1);
            String getName = "unsafeGet" + kindName;
            String putName = "unsafePut" + kindName;
            r.register4(getName, Object.class, long.class, boolean.class, Object.class, new CustomizedUnsafeLoadPlugin(kind));
            r.register4(putName, Object.class, long.class, kind == Kind.Object ? Object.class : kind.toJavaClass(), Object.class, new CustomizedUnsafeStorePlugin(kind));
        }
    }

    static class CustomizedUnsafeLoadPlugin implements InvocationPlugin {

        private final Kind returnKind;

        public CustomizedUnsafeLoadPlugin(Kind returnKind) {
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
                b.addPush(returnKind.getStackKind(), b.add(new UnsafeLoadNode(object, offset, returnKind, locationIdentity, compare)));
                return true;
            }
            // TODO: should we throw b.bailout() here?
            return false;
        }
    }

    static class CustomizedUnsafeStorePlugin implements InvocationPlugin {

        private final Kind kind;

        public CustomizedUnsafeStorePlugin(Kind kind) {
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
