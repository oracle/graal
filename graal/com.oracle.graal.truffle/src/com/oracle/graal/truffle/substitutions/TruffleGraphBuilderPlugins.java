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
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.truffle.*;
import com.oracle.graal.truffle.nodes.*;
import com.oracle.graal.truffle.nodes.arithmetic.*;
import com.oracle.graal.truffle.nodes.asserts.*;
import com.oracle.graal.truffle.nodes.frame.*;
import com.oracle.graal.truffle.unsafe.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;

/**
 * Provides {@link InvocationPlugin}s for Truffle classes.
 */
public class TruffleGraphBuilderPlugins {
    public static void registerInvocationPlugins(MetaAccessProvider metaAccess, InvocationPlugins plugins) {

        registerOptimizedAssumptionPlugins(plugins);
        registerExactMathPlugins(plugins);
        registerCompilerDirectivesPlugins(plugins);
        registerOptimizedCallTargetPlugins(metaAccess, plugins);
        registerUnsafeAccessImplPlugins(plugins);

        if (TruffleCompilerOptions.TruffleUseFrameWithoutBoxing.getValue()) {
            registerFrameWithoutBoxingPlugins(plugins);
        } else {
            registerFrameWithBoxingPlugins(plugins);
        }

    }

    public static void registerOptimizedAssumptionPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, OptimizedAssumption.class);
        r.register1("isValid", Receiver.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode arg) {
                if (arg.isConstant()) {
                    Constant constant = arg.asConstant();
                    OptimizedAssumption assumption = b.getSnippetReflection().asObject(OptimizedAssumption.class, (JavaConstant) constant);
                    b.push(Kind.Boolean.getStackKind(), b.append(ConstantNode.forBoolean(assumption.isValid())));
                    if (assumption.isValid()) {
                        b.getAssumptions().record(new AssumptionValidAssumption(assumption));
                    }
                } else {
                    throw b.bailout("assumption could not be reduced to a constant");
                }
                return true;
            }
        });
    }

    public static void registerExactMathPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, ExactMath.class);
        for (Kind kind : new Kind[]{Kind.Int, Kind.Long}) {
            Class<?> type = kind.toJavaClass();
            r.register2("addExact", type, type, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode x, ValueNode y) {
                    b.push(kind.getStackKind(), b.append(new IntegerAddExactNode(x, y)));
                    return true;
                }
            });
            r.register2("subtractExact", type, type, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode x, ValueNode y) {
                    b.push(kind.getStackKind(), b.append(new IntegerSubExactNode(x, y)));
                    return true;
                }
            });
            r.register2("multiplyExact", type, type, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode x, ValueNode y) {
                    b.push(kind.getStackKind(), b.append(new IntegerMulExactNode(x, y)));
                    return true;
                }
            });
            r.register2("multiplyHigh", type, type, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode x, ValueNode y) {
                    b.push(kind.getStackKind(), b.append(new IntegerMulHighNode(x, y)));
                    return true;
                }
            });
            r.register2("multiplyHighUnsigned", type, type, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode x, ValueNode y) {
                    b.push(kind.getStackKind(), b.append(new UnsignedMulHighNode(x, y)));
                    return true;
                }
            });
        }
    }

    public static void registerCompilerDirectivesPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, CompilerDirectives.class);
        r.register0("inInterpreter", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod) {
                b.push(Kind.Boolean.getStackKind(), b.append(ConstantNode.forBoolean(false)));
                return true;
            }
        });
        r.register0("inCompiledCode", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod) {
                b.push(Kind.Boolean.getStackKind(), b.append(ConstantNode.forBoolean(true)));
                return true;
            }
        });
        r.register0("transferToInterpreter", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod) {
                b.append(new DeoptimizeNode(DeoptimizationAction.None, DeoptimizationReason.TransferToInterpreter));
                return true;
            }
        });
        r.register0("transferToInterpreterAndInvalidate", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod) {
                b.append(new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.TransferToInterpreter));
                return true;
            }
        });
        r.register1("interpreterOnly", Runnable.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode arg) {
                return true;
            }
        });
        r.register1("interpreterOnly", Callable.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode arg) {
                return true;
            }
        });
        r.register2("injectBranchProbability", double.class, boolean.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode probability, ValueNode condition) {
                b.push(Kind.Boolean.getStackKind(), b.append(new BranchProbabilityNode(probability, condition)));
                return true;
            }
        });
        r.register1("bailout", String.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode message) {
                if (message.isConstant()) {
                    throw b.bailout(message.asConstant().toValueString());
                }
                throw b.bailout("bailout (message is not compile-time constant, so no additional information is available)");
            }
        });
        r.register1("isCompilationConstant", Object.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode value) {
                if ((value instanceof BoxNode ? ((BoxNode) value).getValue() : value).isConstant()) {
                    b.push(Kind.Boolean.getStackKind(), b.append(ConstantNode.forBoolean(true)));
                } else {
                    b.push(Kind.Boolean.getStackKind(), b.append(new IsCompilationConstantNode(value)));
                }
                return true;
            }
        });
        r.register1("materialize", Object.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode value) {
                b.append(new ForceMaterializeNode(value));
                return true;
            }
        });

        r = new Registration(plugins, CompilerAsserts.class);
        r.register1("partialEvaluationConstant", Object.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode value) {
                ValueNode curValue = value;
                if (curValue instanceof BoxNode) {
                    BoxNode boxNode = (BoxNode) curValue;
                    curValue = boxNode.getValue();
                }
                if (curValue.isConstant()) {
                    return true;
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
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode message) {
                if (message.isConstant()) {
                    String messageString = message.asConstant().toValueString();
                    b.append(new NeverPartOfCompilationNode(messageString));
                    return true;
                }
                throw b.bailout("message for never part of compilation is non-constant");
            }
        });
    }

    public static void registerOptimizedCallTargetPlugins(MetaAccessProvider metaAccess, InvocationPlugins plugins) {
        Registration r = new Registration(plugins, OptimizedCallTarget.class);
        r.register2("createFrame", FrameDescriptor.class, Object[].class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode descriptor, ValueNode args) {
                Class<?> frameClass = TruffleCompilerOptions.TruffleUseFrameWithoutBoxing.getValue() ? FrameWithoutBoxing.class : FrameWithBoxing.class;
                b.push(Kind.Object, b.append(new NewFrameNode(StampFactory.exactNonNull(metaAccess.lookupJavaType(frameClass)), descriptor, args)));
                return true;
            }
        });
        r.register2("castArrayFixedLength", Object[].class, int.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode args, ValueNode length) {
                b.push(Kind.Object, b.append(new PiArrayNode(args, length, args.stamp())));
                return true;
            }
        });
    }

    public static void registerFrameWithoutBoxingPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, FrameWithoutBoxing.class);
        registerMaterialize(r);
        registerUnsafeCast(r);
        registerUnsafeLoadStorePlugins(r, Kind.Int, Kind.Long, Kind.Float, Kind.Double, Kind.Object);
    }

    public static void registerFrameWithBoxingPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, FrameWithBoxing.class);
        registerMaterialize(r);
        registerUnsafeCast(r);
    }

    public static void registerUnsafeAccessImplPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, UnsafeAccessImpl.class);
        registerUnsafeCast(r);
        registerUnsafeLoadStorePlugins(r, Kind.Boolean, Kind.Byte, Kind.Int, Kind.Short, Kind.Long, Kind.Float, Kind.Double, Kind.Object);
    }

    private static void registerMaterialize(Registration r) {
        r.register1("materialize", Receiver.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode frame) {
                b.push(Kind.Object, b.append(new MaterializeFrameNode(frame)));
                return true;
            }
        });
    }

    private static void registerUnsafeCast(Registration r) {
        r.register4("unsafeCast", Object.class, Class.class, boolean.class, boolean.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode object, ValueNode clazz, ValueNode condition, ValueNode nonNull) {
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
                            valueAnchorNode = b.append(new ConditionAnchorNode(compareNode));
                        }
                        PiNode piCast = b.append(new PiNode(object, piStamp, valueAnchorNode));
                        b.push(Kind.Object, piCast);
                    }
                    return true;
                }
                throw GraalInternalError.shouldNotReachHere("unsafeCast arguments could not reduce to a constant: " + clazz + ", " + nonNull);
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

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode object, ValueNode offset, ValueNode condition, ValueNode location) {
            if (location.isConstant()) {
                LocationIdentity locationIdentity;
                if (location.isNullConstant()) {
                    locationIdentity = LocationIdentity.ANY_LOCATION;
                } else {
                    locationIdentity = ObjectLocationIdentity.create(location.asJavaConstant());
                }
                LogicNode compare = b.append(CompareNode.createCompareNode(Condition.EQ, condition, ConstantNode.forBoolean(true, object.graph()), b.getConstantReflection()));
                b.push(returnKind.getStackKind(), b.append(new UnsafeLoadNode(object, offset, returnKind, locationIdentity, compare)));
                return true;
            }
            // TODO: should we throw GraalInternalError.shouldNotReachHere() here?
            return false;
        }
    }

    static class CustomizedUnsafeStorePlugin implements InvocationPlugin {

        private final Kind kind;

        public CustomizedUnsafeStorePlugin(Kind kind) {
            this.kind = kind;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode object, ValueNode offset, ValueNode value, ValueNode location) {
            ValueNode locationArgument = location;
            if (locationArgument.isConstant()) {
                LocationIdentity locationIdentity;
                if (locationArgument.isNullConstant()) {
                    locationIdentity = LocationIdentity.ANY_LOCATION;
                } else {
                    locationIdentity = ObjectLocationIdentity.create(locationArgument.asJavaConstant());
                }

                b.append(new UnsafeStoreNode(object, offset, value, kind, locationIdentity, null));
                return true;
            }
            // TODO: should we throw GraalInternalError.shouldNotReachHere() here?
            return false;
        }
    }
}
