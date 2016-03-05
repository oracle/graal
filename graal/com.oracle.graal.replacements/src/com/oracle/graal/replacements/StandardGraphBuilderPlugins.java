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
package com.oracle.graal.replacements;

import static com.oracle.graal.compiler.common.GraalOptions.UseGraalInstrumentation;
import static jdk.vm.ci.code.MemoryBarriers.JMM_POST_VOLATILE_READ;
import static jdk.vm.ci.code.MemoryBarriers.JMM_POST_VOLATILE_WRITE;
import static jdk.vm.ci.code.MemoryBarriers.JMM_PRE_VOLATILE_READ;
import static jdk.vm.ci.code.MemoryBarriers.JMM_PRE_VOLATILE_WRITE;
import static jdk.vm.ci.code.MemoryBarriers.LOAD_LOAD;
import static jdk.vm.ci.code.MemoryBarriers.LOAD_STORE;
import static jdk.vm.ci.code.MemoryBarriers.STORE_LOAD;
import static jdk.vm.ci.code.MemoryBarriers.STORE_STORE;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Arrays;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.LocationIdentity;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.misc.Unsafe;

import com.oracle.graal.api.directives.GraalDirectives;
import com.oracle.graal.compiler.common.calc.Condition;
import com.oracle.graal.compiler.common.calc.UnsignedMath;
import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.Edges;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeList;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.DeoptimizeNode;
import com.oracle.graal.nodes.FixedGuardNode;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.PiNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.AbsNode;
import com.oracle.graal.nodes.calc.CompareNode;
import com.oracle.graal.nodes.calc.ConditionalNode;
import com.oracle.graal.nodes.calc.IsNullNode;
import com.oracle.graal.nodes.calc.NarrowNode;
import com.oracle.graal.nodes.calc.ReinterpretNode;
import com.oracle.graal.nodes.calc.RightShiftNode;
import com.oracle.graal.nodes.calc.SignExtendNode;
import com.oracle.graal.nodes.calc.SqrtNode;
import com.oracle.graal.nodes.calc.UnsignedDivNode;
import com.oracle.graal.nodes.calc.UnsignedRemNode;
import com.oracle.graal.nodes.calc.ZeroExtendNode;
import com.oracle.graal.nodes.debug.BindToRegisterNode;
import com.oracle.graal.nodes.debug.BlackholeNode;
import com.oracle.graal.nodes.debug.ControlFlowAnchorNode;
import com.oracle.graal.nodes.debug.OpaqueNode;
import com.oracle.graal.nodes.debug.SpillRegistersNode;
import com.oracle.graal.nodes.extended.BoxNode;
import com.oracle.graal.nodes.extended.BranchProbabilityNode;
import com.oracle.graal.nodes.extended.GetClassNode;
import com.oracle.graal.nodes.extended.MembarNode;
import com.oracle.graal.nodes.extended.UnboxNode;
import com.oracle.graal.nodes.extended.UnsafeLoadNode;
import com.oracle.graal.nodes.extended.UnsafeStoreNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.nodes.java.ClassIsAssignableFromNode;
import com.oracle.graal.nodes.java.CompareAndSwapNode;
import com.oracle.graal.nodes.java.DynamicNewArrayNode;
import com.oracle.graal.nodes.java.DynamicNewInstanceNode;
import com.oracle.graal.nodes.java.InstanceOfDynamicNode;
import com.oracle.graal.nodes.java.LoadFieldNode;
import com.oracle.graal.nodes.java.RegisterFinalizerNode;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.nodes.virtual.EnsureVirtualizedNode;
import com.oracle.graal.phases.common.instrumentation.nodes.InstrumentationBeginNode;
import com.oracle.graal.phases.common.instrumentation.nodes.InstrumentationEndNode;
import com.oracle.graal.phases.common.instrumentation.nodes.IsMethodInlinedNode;
import com.oracle.graal.phases.common.instrumentation.nodes.RootNameNode;
import com.oracle.graal.phases.common.instrumentation.nodes.RuntimePathNode;
import com.oracle.graal.replacements.nodes.DeferredPiNode;
import com.oracle.graal.replacements.nodes.DirectReadNode;
import com.oracle.graal.replacements.nodes.DirectStoreNode;
import com.oracle.graal.replacements.nodes.ReverseBytesNode;
import com.oracle.graal.replacements.nodes.VirtualizableInvokeMacroNode;
import com.oracle.graal.replacements.nodes.arithmetic.IntegerAddExactNode;
import com.oracle.graal.replacements.nodes.arithmetic.IntegerMulExactNode;
import com.oracle.graal.replacements.nodes.arithmetic.IntegerSubExactNode;

/**
 * Provides non-runtime specific {@link InvocationPlugin}s.
 */
public class StandardGraphBuilderPlugins {

    public static void registerInvocationPlugins(MetaAccessProvider metaAccess, InvocationPlugins plugins, boolean allowDeoptimization) {
        registerObjectPlugins(plugins);
        registerClassPlugins(plugins);
        registerMathPlugins(plugins, allowDeoptimization);
        registerUnsignedMathPlugins(plugins);
        registerCharacterPlugins(plugins);
        registerShortPlugins(plugins);
        registerIntegerLongPlugins(plugins, JavaKind.Int);
        registerIntegerLongPlugins(plugins, JavaKind.Long);
        registerFloatPlugins(plugins);
        registerDoublePlugins(plugins);
        if (System.getProperty("java.specification.version").compareTo("1.9") < 0) {
            registerStringPlugins(plugins);
        }
        registerArraysPlugins(plugins);
        registerArrayPlugins(plugins);
        registerUnsafePlugins(plugins);
        registerEdgesPlugins(metaAccess, plugins);
        registerGraalDirectivesPlugins(plugins);
        registerBoxingPlugins(plugins);
        registerJMHBlackholePlugins(plugins);
        registerJFRThrowablePlugins(plugins);
    }

    private static final Field STRING_VALUE_FIELD;

    static {
        try {
            STRING_VALUE_FIELD = String.class.getDeclaredField("value");
        } catch (NoSuchFieldException e) {
            throw new JVMCIError(e);
        }
    }

    private static void registerStringPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, String.class);
        r.registerMethodSubstitution(StringSubstitutions.class, "equals", Receiver.class, Object.class);

        r = new Registration(plugins, StringSubstitutions.class);
        r.register1("getValue", String.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                ResolvedJavaField field = b.getMetaAccess().lookupJavaField(STRING_VALUE_FIELD);
                b.addPush(JavaKind.Object, new LoadFieldNode(value, field));
                return true;
            }
        });
    }

    private static void registerArraysPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Arrays.class);
        r.registerMethodSubstitution(ArraysSubstitutions.class, "equals", boolean[].class, boolean[].class);
        r.registerMethodSubstitution(ArraysSubstitutions.class, "equals", byte[].class, byte[].class);
        r.registerMethodSubstitution(ArraysSubstitutions.class, "equals", short[].class, short[].class);
        r.registerMethodSubstitution(ArraysSubstitutions.class, "equals", char[].class, char[].class);
        r.registerMethodSubstitution(ArraysSubstitutions.class, "equals", int[].class, int[].class);
        r.registerMethodSubstitution(ArraysSubstitutions.class, "equals", float[].class, float[].class);
        r.registerMethodSubstitution(ArraysSubstitutions.class, "equals", long[].class, long[].class);
        r.registerMethodSubstitution(ArraysSubstitutions.class, "equals", double[].class, double[].class);
    }

    private static void registerArrayPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Array.class);
        r.register2("newInstance", Class.class, int.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused, ValueNode componentType, ValueNode length) {
                b.addPush(JavaKind.Object, new DynamicNewArrayNode(componentType, length, true));
                return true;
            }
        });
        r.registerMethodSubstitution(ArraySubstitutions.class, "getLength", Object.class);
    }

    private static void registerUnsafePlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Unsafe.class);
        for (JavaKind kind : JavaKind.values()) {
            if ((kind.isPrimitive() && kind != JavaKind.Void) || kind == JavaKind.Object) {
                Class<?> javaClass = kind == JavaKind.Object ? Object.class : kind.toJavaClass();
                String kindName = kind.name();
                String getName = "get" + kindName;
                String putName = "put" + kindName;
                // Object-based accesses
                r.register3(getName, Receiver.class, Object.class, long.class, new UnsafeGetPlugin(kind, false));
                r.register4(putName, Receiver.class, Object.class, long.class, javaClass, new UnsafePutPlugin(kind, false));
                // Volatile object-based accesses
                r.register3(getName + "Volatile", Receiver.class, Object.class, long.class, new UnsafeGetPlugin(kind, true));
                r.register4(putName + "Volatile", Receiver.class, Object.class, long.class, javaClass, new UnsafePutPlugin(kind, true));
                // Ordered object-based accesses
                if (kind == JavaKind.Int || kind == JavaKind.Long || kind == JavaKind.Object) {
                    r.register4("putOrdered" + kindName, Receiver.class, Object.class, long.class, javaClass, new UnsafePutPlugin(kind, true));
                }
                if (kind != JavaKind.Boolean && kind != JavaKind.Object) {
                    // Raw accesses to memory addresses
                    r.register2(getName, Receiver.class, long.class, new UnsafeGetPlugin(kind, false));
                    r.register3(putName, Receiver.class, long.class, kind.toJavaClass(), new UnsafePutPlugin(kind, false));
                }
            }
        }

        // Accesses to native memory addresses.
        r.register2("getAddress", Receiver.class, long.class, new UnsafeGetPlugin(JavaKind.Long, false));
        r.register3("putAddress", Receiver.class, long.class, long.class, new UnsafePutPlugin(JavaKind.Long, false));

        for (JavaKind kind : new JavaKind[]{JavaKind.Int, JavaKind.Long, JavaKind.Object}) {
            Class<?> javaClass = kind == JavaKind.Object ? Object.class : kind.toJavaClass();
            r.register5("compareAndSwap" + kind.name(), Receiver.class, Object.class, long.class, javaClass, javaClass, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode object, ValueNode offset, ValueNode expected, ValueNode x) {
                    // Emits a null-check for the otherwise unused receiver
                    unsafe.get();
                    b.addPush(JavaKind.Int, new CompareAndSwapNode(object, offset, expected, x, kind, LocationIdentity.any()));
                    b.getGraph().markUnsafeAccess();
                    return true;
                }
            });
        }

        r.register2("allocateInstance", Receiver.class, Class.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode clazz) {
                // Emits a null-check for the otherwise unused receiver
                unsafe.get();
                b.addPush(JavaKind.Object, new DynamicNewInstanceNode(clazz, true));
                return true;
            }
        });

        r.register1("loadFence", Receiver.class, new UnsafeFencePlugin(LOAD_LOAD | LOAD_STORE));
        r.register1("storeFence", Receiver.class, new UnsafeFencePlugin(STORE_STORE | LOAD_STORE));
        r.register1("fullFence", Receiver.class, new UnsafeFencePlugin(LOAD_LOAD | STORE_STORE | LOAD_STORE | STORE_LOAD));
    }

    private static void registerIntegerLongPlugins(InvocationPlugins plugins, JavaKind kind) {
        Class<?> declaringClass = kind.toBoxedJavaClass();
        Class<?> type = kind.toJavaClass();
        Registration r = new Registration(plugins, declaringClass);
        r.register1("reverseBytes", type, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.recursiveAppend(new ReverseBytesNode(value).canonical(null, value)));
                return true;
            }
        });
        r.register2("divideUnsigned", type, type, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode dividend, ValueNode divisor) {
                b.push(kind, b.recursiveAppend(new UnsignedDivNode(dividend, divisor).canonical(null, dividend, divisor)));
                return true;
            }
        });
        r.register2("remainderUnsigned", type, type, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode dividend, ValueNode divisor) {
                b.push(kind, b.recursiveAppend(new UnsignedRemNode(dividend, divisor).canonical(null, dividend, divisor)));
                return true;
            }
        });
    }

    private static void registerCharacterPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Character.class);
        r.register1("reverseBytes", char.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                // return (char) (Integer.reverse(i) >> 16);
                ReverseBytesNode reverse = b.add(new ReverseBytesNode(value));
                RightShiftNode rightShift = b.add(new RightShiftNode(reverse, b.add(ConstantNode.forInt(16))));
                ZeroExtendNode charCast = b.add(new ZeroExtendNode(b.add(new NarrowNode(rightShift, 16)), 32));
                b.push(JavaKind.Char, b.recursiveAppend(charCast.canonical(null, value)));
                return true;
            }
        });
    }

    private static void registerShortPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Short.class);
        r.register1("reverseBytes", short.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                // return (short) (Integer.reverse(i) >> 16);
                ReverseBytesNode reverse = b.add(new ReverseBytesNode(value));
                RightShiftNode rightShift = b.add(new RightShiftNode(reverse, b.add(ConstantNode.forInt(16))));
                SignExtendNode charCast = b.add(new SignExtendNode(b.add(new NarrowNode(rightShift, 16)), 32));
                b.push(JavaKind.Short, b.recursiveAppend(charCast.canonical(null, value)));
                return true;
            }
        });
    }

    private static void registerFloatPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Float.class);
        r.register1("floatToRawIntBits", float.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.recursiveAppend(new ReinterpretNode(JavaKind.Int, value).canonical(null, value)));
                return true;
            }
        });
        r.register1("intBitsToFloat", int.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Float, b.recursiveAppend(new ReinterpretNode(JavaKind.Float, value).canonical(null, value)));
                return true;
            }
        });
    }

    private static void registerDoublePlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Double.class);
        r.register1("doubleToRawLongBits", double.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Long, b.recursiveAppend(new ReinterpretNode(JavaKind.Long, value).canonical(null, value)));
                return true;
            }
        });
        r.register1("longBitsToDouble", long.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.recursiveAppend(new ReinterpretNode(JavaKind.Double, value).canonical(null, value)));
                return true;
            }
        });
    }

    private static void registerMathPlugins(InvocationPlugins plugins, boolean allowDeoptimization) {
        Registration r = new Registration(plugins, Math.class);
        if (allowDeoptimization) {
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
            }
        }
        r.register1("abs", Float.TYPE, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Float, b.recursiveAppend(new AbsNode(value).canonical(null, value)));
                return true;
            }
        });
        r.register1("abs", Double.TYPE, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.recursiveAppend(new AbsNode(value).canonical(null, value)));
                return true;
            }
        });
        r.register1("sqrt", Double.TYPE, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.recursiveAppend(new SqrtNode(value).canonical(null, value)));
                return true;
            }
        });
    }

    public static class UnsignedMathPlugin implements InvocationPlugin {
        private final Condition condition;

        public UnsignedMathPlugin(Condition condition) {
            this.condition = condition;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
            // the mirroring and negation operations get the condition into canonical form
            boolean mirror = condition.canonicalMirror();
            boolean negate = condition.canonicalNegate();
            StructuredGraph graph = b.getGraph();

            ValueNode lhs = mirror ? y : x;
            ValueNode rhs = mirror ? x : y;

            ValueNode trueValue = ConstantNode.forBoolean(!negate, graph);
            ValueNode falseValue = ConstantNode.forBoolean(negate, graph);

            Condition cond = mirror ? condition.mirror() : condition;
            if (negate) {
                cond = cond.negate();
            }

            LogicNode compare = CompareNode.createCompareNode(graph, cond, lhs, rhs, b.getConstantReflection());
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
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode object = receiver.get();
                if (RegisterFinalizerNode.mayHaveFinalizer(object, b.getAssumptions())) {
                    b.add(new RegisterFinalizerNode(object));
                }
                return true;
            }
        });
        r.register1("getClass", Receiver.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode object = receiver.get();
                ValueNode folded = GetClassNode.tryFold(b.getMetaAccess(), b.getConstantReflection(), GraphUtil.originalValue(object));
                if (folded != null) {
                    b.addPush(JavaKind.Object, folded);
                } else {
                    Stamp stamp = StampFactory.declaredNonNull(b.getMetaAccess().lookupJavaType(Class.class));
                    b.addPush(JavaKind.Object, new GetClassNode(stamp, object));
                }
                return true;
            }
        });
    }

    private static void registerClassPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Class.class);
        r.register2("isInstance", Receiver.class, Object.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode object) {
                LogicNode condition = b.add(InstanceOfDynamicNode.create(b.getAssumptions(), b.getConstantReflection(), type.get(), object));
                b.push(JavaKind.Boolean, b.recursiveAppend(new ConditionalNode(condition).canonical(null)));
                return true;
            }
        });
        r.register2("isAssignableFrom", Receiver.class, Class.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode otherType) {
                ClassIsAssignableFromNode condition = b.recursiveAppend(new ClassIsAssignableFromNode(type.get(), otherType));
                b.push(JavaKind.Boolean, b.recursiveAppend(new ConditionalNode(condition).canonical(null)));
                return true;
            }
        });
    }

    /**
     * Substitutions for improving the performance of some critical methods in {@link Edges}. These
     * substitutions improve the performance by forcing the relevant methods to be inlined
     * (intrinsification being a special form of inlining) and removing a checked cast. The latter
     * cannot be done directly in Java code as {@link DeferredPiNode} is not available to the
     * project containing {@link Edges}.
     */
    private static void registerEdgesPlugins(MetaAccessProvider metaAccess, InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Edges.class);
        for (Class<?> c : new Class<?>[]{Node.class, NodeList.class}) {
            r.register2("get" + c.getSimpleName() + "Unsafe", Node.class, long.class, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode node, ValueNode offset) {
                    UnsafeLoadNode value = b.add(new UnsafeLoadNode(node, offset, JavaKind.Object, LocationIdentity.any()));
                    value.setStamp(StampFactory.declared(metaAccess.lookupJavaType(c)));
                    b.addPush(JavaKind.Object, value);
                    return true;
                }
            });
            r.register3("put" + c.getSimpleName() + "Unsafe", Node.class, long.class, c, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode node, ValueNode offset, ValueNode value) {
                    b.add(new UnsafeStoreNode(node, offset, value, JavaKind.Object, LocationIdentity.any()));
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
            b.addPush(JavaKind.Object, new BoxNode(value, resultType, kind));
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

    public static class UnsafeGetPlugin implements InvocationPlugin {

        private final JavaKind returnKind;
        private final boolean isVolatile;

        public UnsafeGetPlugin(JavaKind returnKind, boolean isVolatile) {
            this.returnKind = returnKind;
            this.isVolatile = isVolatile;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode address) {
            // Emits a null-check for the otherwise unused receiver
            unsafe.get();
            b.addPush(returnKind, new DirectReadNode(address, returnKind));
            b.getGraph().markUnsafeAccess();
            return true;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode object, ValueNode offset) {
            // Emits a null-check for the otherwise unused receiver
            unsafe.get();
            if (isVolatile) {
                b.add(new MembarNode(JMM_PRE_VOLATILE_READ));
            }
            b.addPush(returnKind, new UnsafeLoadNode(object, offset, returnKind, LocationIdentity.any()));
            if (isVolatile) {
                b.add(new MembarNode(JMM_POST_VOLATILE_READ));
            }
            b.getGraph().markUnsafeAccess();
            return true;
        }
    }

    public static class UnsafePutPlugin implements InvocationPlugin {

        private final JavaKind kind;
        private final boolean isVolatile;

        public UnsafePutPlugin(JavaKind kind, boolean isVolatile) {
            this.kind = kind;
            this.isVolatile = isVolatile;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode address, ValueNode value) {
            // Emits a null-check for the otherwise unused receiver
            unsafe.get();
            b.add(new DirectStoreNode(address, value, kind));
            b.getGraph().markUnsafeAccess();
            return true;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode object, ValueNode offset, ValueNode value) {
            // Emits a null-check for the otherwise unused receiver
            unsafe.get();
            if (isVolatile) {
                b.add(new MembarNode(JMM_PRE_VOLATILE_WRITE));
            }
            b.add(new UnsafeStoreNode(object, offset, value, kind, LocationIdentity.any()));
            if (isVolatile) {
                b.add(new MembarNode(JMM_POST_VOLATILE_WRITE));
            }
            b.getGraph().markUnsafeAccess();
            return true;
        }
    }

    public static class UnsafeFencePlugin implements InvocationPlugin {

        private final int barriers;

        public UnsafeFencePlugin(int barriers) {
            this.barriers = barriers;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe) {
            // Emits a null-check for the otherwise unused receiver
            unsafe.get();
            b.add(new MembarNode(barriers));
            return true;
        }
    }

    private static void registerGraalDirectivesPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, GraalDirectives.class);
        r.register0("deoptimize", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new DeoptimizeNode(DeoptimizationAction.None, DeoptimizationReason.TransferToInterpreter));
                return true;
            }
        });

        r.register0("deoptimizeAndInvalidate", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.TransferToInterpreter));
                return true;
            }
        });

        r.register0("inCompiledCode", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(true));
                return true;
            }
        });

        r.register0("controlFlowAnchor", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new ControlFlowAnchorNode());
                return true;
            }
        });

        r.register2("injectBranchProbability", double.class, boolean.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode probability, ValueNode condition) {
                b.addPush(JavaKind.Boolean, new BranchProbabilityNode(probability, condition));
                return true;
            }
        });

        InvocationPlugin blackholePlugin = new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.add(new BlackholeNode(value));
                return true;
            }
        };

        InvocationPlugin bindToRegisterPlugin = new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.add(new BindToRegisterNode(value));
                return true;
            }
        };
        for (JavaKind kind : JavaKind.values()) {
            if ((kind.isPrimitive() && kind != JavaKind.Void) || kind == JavaKind.Object) {
                Class<?> javaClass = kind == JavaKind.Object ? Object.class : kind.toJavaClass();
                r.register1("blackhole", javaClass, blackholePlugin);
                r.register1("bindToRegister", javaClass, bindToRegisterPlugin);

                r.register1("opaque", javaClass, new InvocationPlugin() {
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                        b.addPush(kind, new OpaqueNode(value));
                        return true;
                    }
                });
            }
        }

        InvocationPlugin spillPlugin = new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new SpillRegistersNode());
                return true;
            }
        };
        r.register0("spillRegisters", spillPlugin);

        r.register1("guardingNonNull", Object.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                ObjectStamp objectStamp = (ObjectStamp) value.stamp();
                if (objectStamp.nonNull()) {
                    b.addPush(value.getStackKind(), value);
                    return true;
                } else if (objectStamp.alwaysNull()) {
                    b.add(new DeoptimizeNode(DeoptimizationAction.None, DeoptimizationReason.NullCheckException));
                    return true;
                }
                IsNullNode isNull = b.add(new IsNullNode(value));
                FixedGuardNode fixedGuard = b.add(new FixedGuardNode(isNull, DeoptimizationReason.NullCheckException, DeoptimizationAction.None, true));
                Stamp newStamp = objectStamp.improveWith(StampFactory.objectNonNull());
                b.addPush(value.getStackKind(), new PiNode(value, newStamp, fixedGuard));
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
        r.register0("rootName", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Object, new RootNameNode(b.getInvokeReturnStamp()));
                return true;
            }
        });

        if (UseGraalInstrumentation.getValue()) {
            r.register1("instrumentationBegin", int.class, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode offset) {
                    b.add(new InstrumentationBeginNode(offset, false));
                    return true;
                }
            });
            r.register1("instrumentationToInvokeBegin", int.class, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode offset) {
                    b.add(new InstrumentationBeginNode(offset, true));
                    return true;
                }
            });
            r.register0("instrumentationEnd", new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    b.add(new InstrumentationEndNode());
                    return true;
                }
            });
            r.register0("isMethodInlined", new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    b.addPush(JavaKind.Boolean, new IsMethodInlinedNode());
                    return true;
                }
            });
            r.register0("runtimePath", new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    b.addPush(JavaKind.Int, new RuntimePathNode());
                    return true;
                }
            });
        }
    }

    private static void registerJMHBlackholePlugins(InvocationPlugins plugins) {
        InvocationPlugin blackholePlugin = new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver blackhole, ValueNode value) {
                blackhole.get();
                b.add(new BlackholeNode(value));
                return true;
            }
        };
        String[] names = {"org.openjdk.jmh.infra.Blackhole", "org.openjdk.jmh.logic.BlackHole"};
        for (String name : names) {
            Registration r = new Registration(plugins, name);
            for (JavaKind kind : JavaKind.values()) {
                if ((kind.isPrimitive() && kind != JavaKind.Void) || kind == JavaKind.Object) {
                    Class<?> javaClass = kind == JavaKind.Object ? Object.class : kind.toJavaClass();
                    r.register2("consume", Receiver.class, javaClass, blackholePlugin);
                }
            }
            r.register2("consume", Receiver.class, Object[].class, blackholePlugin);
        }
    }

    private static void registerJFRThrowablePlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, "oracle.jrockit.jfr.jdkevents.ThrowableTracer");
        r.register2("traceThrowable", Throwable.class, String.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode throwable, ValueNode message) {
                b.add(new VirtualizableInvokeMacroNode(b.getInvokeKind(), targetMethod, b.bci(), b.getInvokeReturnType(), throwable, message));
                return true;
            }
        });
    }
}
