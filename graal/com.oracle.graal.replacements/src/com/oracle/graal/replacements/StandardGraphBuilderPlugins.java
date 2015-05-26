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

import static com.oracle.jvmci.code.MemoryBarriers.*;

import java.lang.reflect.*;
import java.util.*;

import sun.misc.*;

import com.oracle.graal.api.directives.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.InvocationPlugin.Receiver;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.debug.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.replacements.nodes.arithmetic.*;
import com.oracle.jvmci.code.*;
import com.oracle.jvmci.common.*;
import com.oracle.jvmci.meta.*;
import com.oracle.jvmci.options.*;

/**
 * Provides non-runtime specific {@link InvocationPlugin}s.
 */
public class StandardGraphBuilderPlugins {

    // @formatter:off
    static class Options {
        @Option(help = "Enable use of intrinsics for the JMH Blackhole class")
        public static final OptionValue<Boolean> UseBlackholeSubstitution = new OptionValue<>(true);
    }
    // @formatter:on

    public static void registerInvocationPlugins(MetaAccessProvider metaAccess, InvocationPlugins plugins, boolean useBoxingPlugins) {
        registerObjectPlugins(plugins);
        registerClassPlugins(plugins);
        registerMathPlugins(plugins);
        registerUnsignedMathPlugins(plugins);
        registerCharacterPlugins(plugins);
        registerShortPlugins(plugins);
        registerIntegerLongPlugins(plugins, Kind.Int);
        registerIntegerLongPlugins(plugins, Kind.Long);
        registerFloatPlugins(plugins);
        registerDoublePlugins(plugins);
        registerStringPlugins(plugins);
        registerArraysPlugins(plugins);
        registerArrayPlugins(plugins);
        registerUnsafePlugins(plugins);
        registerEdgesPlugins(metaAccess, plugins);
        registerGraalDirectivesPlugins(plugins);
        if (useBoxingPlugins) {
            registerBoxingPlugins(plugins);
        }
        if (Options.UseBlackholeSubstitution.getValue()) {
            registerJMHBlackholePlugins(plugins);
        }
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
                b.addPush(new LoadFieldNode(value, field));
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
        r.registerMethodSubstitution(ArraySubstitutions.class, "newInstance", Class.class, int.class);
        r.registerMethodSubstitution(ArraySubstitutions.class, "getLength", Object.class);
    }

    private static void registerUnsafePlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Unsafe.class);
        for (Kind kind : Kind.values()) {
            if ((kind.isPrimitive() && kind != Kind.Void) || kind == Kind.Object) {
                Class<?> javaClass = kind == Kind.Object ? Object.class : kind.toJavaClass();
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
                if (kind == Kind.Int || kind == Kind.Long || kind == Kind.Object) {
                    r.register4("putOrdered" + kindName, Receiver.class, Object.class, long.class, javaClass, new UnsafePutPlugin(kind, true));
                }
                if (kind != Kind.Boolean && kind != Kind.Object) {
                    // Raw accesses to memory addresses
                    r.register2(getName, Receiver.class, long.class, new UnsafeGetPlugin(kind, false));
                    r.register3(putName, Receiver.class, long.class, kind.toJavaClass(), new UnsafePutPlugin(kind, false));
                }
            }
        }

        // Accesses to native memory addresses.
        r.register2("getAddress", Receiver.class, long.class, new UnsafeGetPlugin(Kind.Long, false));
        r.register3("putAddress", Receiver.class, long.class, long.class, new UnsafePutPlugin(Kind.Long, false));

        for (Kind kind : new Kind[]{Kind.Int, Kind.Long, Kind.Object}) {
            Class<?> javaClass = kind == Kind.Object ? Object.class : kind.toJavaClass();
            r.register5("compareAndSwap" + kind.name(), Receiver.class, Object.class, long.class, javaClass, javaClass, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode object, ValueNode offset, ValueNode expected, ValueNode x) {
                    // Emits a null-check for the otherwise unused receiver
                    unsafe.get();
                    b.addPush(Kind.Int, new CompareAndSwapNode(object, offset, expected, x, kind, LocationIdentity.any()));
                    return true;
                }
            });
        }
    }

    private static void registerIntegerLongPlugins(InvocationPlugins plugins, Kind kind) {
        Class<?> declaringClass = kind.toBoxedJavaClass();
        Class<?> type = kind.toJavaClass();
        Registration r = new Registration(plugins, declaringClass);
        r.register1("reverseBytes", type, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.recursiveAppend(new ReverseBytesNode(value).canonical(null, value)));
                return true;
            }
        });
        r.register1("bitCount", type, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(Kind.Int, b.recursiveAppend(new BitCountNode(value).canonical(null, value)));
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
                b.push(Kind.Char.getStackKind(), b.recursiveAppend(charCast.canonical(null, value)));
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
                b.push(Kind.Short.getStackKind(), b.recursiveAppend(charCast.canonical(null, value)));
                return true;
            }
        });
    }

    private static void registerFloatPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Float.class);
        r.register1("floatToRawIntBits", float.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(Kind.Int, b.recursiveAppend(new ReinterpretNode(Kind.Int, value).canonical(null, value)));
                return true;
            }
        });
        r.register1("intBitsToFloat", int.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(Kind.Float, b.recursiveAppend(new ReinterpretNode(Kind.Float, value).canonical(null, value)));
                return true;
            }
        });
    }

    private static void registerDoublePlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Double.class);
        r.register1("doubleToRawLongBits", double.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(Kind.Long, b.recursiveAppend(new ReinterpretNode(Kind.Long, value).canonical(null, value)));
                return true;
            }
        });
        r.register1("longBitsToDouble", long.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(Kind.Double, b.recursiveAppend(new ReinterpretNode(Kind.Double, value).canonical(null, value)));
                return true;
            }
        });
    }

    private static void registerMathPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Math.class);
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
        }
        r.register1("abs", Float.TYPE, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(Kind.Float, b.recursiveAppend(new AbsNode(value).canonical(null, value)));
                return true;
            }
        });
        r.register1("abs", Double.TYPE, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(Kind.Double, b.recursiveAppend(new AbsNode(value).canonical(null, value)));
                return true;
            }
        });
        r.register1("sqrt", Double.TYPE, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(Kind.Double, b.recursiveAppend(new SqrtNode(value).canonical(null, value)));
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
            b.addPush(Kind.Boolean.getStackKind(), new ConditionalNode(compare, trueValue, falseValue));
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
        r.register2("divide", int.class, int.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(Kind.Int, b.recursiveAppend(new UnsignedDivNode(x, y).canonical(null, x, y)));
                return true;
            }
        });
        r.register2("divide", long.class, long.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(Kind.Long, b.recursiveAppend(new UnsignedDivNode(x, y).canonical(null, x, y)));
                return true;
            }
        });
        r.register2("remainder", int.class, int.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(Kind.Int, b.recursiveAppend(new UnsignedRemNode(x, y).canonical(null, x, y)));
                return true;
            }
        });
        r.register2("remainder", long.class, long.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(Kind.Long, b.recursiveAppend(new UnsignedRemNode(x, y).canonical(null, x, y)));
                return true;
            }
        });
    }

    protected static void registerBoxingPlugins(InvocationPlugins plugins) {
        for (Kind kind : Kind.values()) {
            if (kind.isPrimitive() && kind != Kind.Void) {
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
                ValueNode folded = GetClassNode.tryFold(b.getMetaAccess(), GraphUtil.originalValue(object));
                if (folded != null) {
                    b.addPush(Kind.Object, folded);
                } else {
                    Stamp stamp = StampFactory.declaredNonNull(b.getMetaAccess().lookupJavaType(Class.class));
                    b.addPush(Kind.Object, new GetClassNode(stamp, object));
                }
                return true;
            }
        });
    }

    private static void registerClassPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Class.class);
        r.register2("isInstance", Receiver.class, Object.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode object) {
                LogicNode condition = b.add(InstanceOfDynamicNode.create(b.getConstantReflection(), type.get(), object));
                b.push(Kind.Boolean.getStackKind(), b.recursiveAppend(new ConditionalNode(condition).canonical(null)));
                return true;
            }
        });
        r.register2("isAssignableFrom", Receiver.class, Class.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode otherType) {
                ClassIsAssignableFromNode condition = b.recursiveAppend(new ClassIsAssignableFromNode(type.get(), otherType));
                b.push(Kind.Boolean.getStackKind(), b.recursiveAppend(new ConditionalNode(condition).canonical(null)));
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
                    ValueNode value = b.add(new UnsafeLoadNode(node, offset, Kind.Object, LocationIdentity.any()));
                    boolean exactType = false;
                    boolean nonNull = false;
                    b.addPush(Kind.Object, new PiNode(value, metaAccess.lookupJavaType(c), exactType, nonNull));
                    return true;
                }
            });
            r.register3("put" + c.getSimpleName() + "Unsafe", Node.class, long.class, c, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode node, ValueNode offset, ValueNode value) {
                    b.add(new UnsafeStoreNode(node, offset, value, Kind.Object, LocationIdentity.any()));
                    return true;
                }
            });
        }
    }

    public static class BoxPlugin implements InvocationPlugin {

        private final Kind kind;

        BoxPlugin(Kind kind) {
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
            b.addPush(Kind.Object, new BoxNode(value, resultType, kind));
            return true;
        }

        void register(InvocationPlugins plugins) {
            plugins.register(this, kind.toBoxedJavaClass(), "valueOf", kind.toJavaClass());
        }
    }

    public static class UnboxPlugin implements InvocationPlugin {

        private final Kind kind;

        UnboxPlugin(Kind kind) {
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
            b.addPush(kind.getStackKind(), valueNode);
            return true;
        }

        void register(InvocationPlugins plugins) {
            String name = kind.toJavaClass().getSimpleName() + "Value";
            plugins.register(this, kind.toBoxedJavaClass(), name, Receiver.class);
        }
    }

    public static class UnsafeGetPlugin implements InvocationPlugin {

        private final Kind returnKind;
        private final boolean isVolatile;

        public UnsafeGetPlugin(Kind returnKind, boolean isVolatile) {
            this.returnKind = returnKind;
            this.isVolatile = isVolatile;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode address) {
            // Emits a null-check for the otherwise unused receiver
            unsafe.get();
            b.addPush(returnKind.getStackKind(), new DirectReadNode(address, returnKind));
            return true;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode object, ValueNode offset) {
            // Emits a null-check for the otherwise unused receiver
            unsafe.get();
            if (isVolatile) {
                b.add(new MembarNode(JMM_PRE_VOLATILE_READ));
            }
            b.addPush(returnKind.getStackKind(), new UnsafeLoadNode(object, offset, returnKind, LocationIdentity.any()));
            if (isVolatile) {
                b.add(new MembarNode(JMM_POST_VOLATILE_READ));
            }
            return true;
        }
    }

    static class UnsafePutPlugin implements InvocationPlugin {

        private final Kind kind;
        private final boolean isVolatile;

        public UnsafePutPlugin(Kind kind, boolean isVolatile) {
            this.kind = kind;
            this.isVolatile = isVolatile;
        }

        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode address, ValueNode value) {
            // Emits a null-check for the otherwise unused receiver
            unsafe.get();
            b.add(new DirectStoreNode(address, value, kind));
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
                b.add(new MembarNode(JMM_PRE_VOLATILE_WRITE));
            }
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
                b.addPush(Kind.Int, ConstantNode.forInt(1));
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
                b.addPush(Kind.Int, new BranchProbabilityNode(probability, condition));
                return true;
            }
        });

        InvocationPlugin blackholePlugin = new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.add(new BlackholeNode(value));
                return true;
            }
        };

        for (Kind kind : Kind.values()) {
            if ((kind.isPrimitive() && kind != Kind.Void) || kind == Kind.Object) {
                Class<?> javaClass = kind == Kind.Object ? Object.class : kind.toJavaClass();
                r.register1("blackhole", javaClass, blackholePlugin);

                final Kind stackKind = kind.getStackKind();
                r.register1("opaque", javaClass, new InvocationPlugin() {
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                        b.addPush(stackKind, new OpaqueNode(value));
                        return true;
                    }
                });
            }
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
            Class<?> blackholeClass;
            blackholeClass = MethodSubstitutionPlugin.resolveClass(name, true);
            if (blackholeClass != null) {
                Registration r = new Registration(plugins, blackholeClass);
                for (Kind kind : Kind.values()) {
                    if ((kind.isPrimitive() && kind != Kind.Void) || kind == Kind.Object) {
                        Class<?> javaClass = kind == Kind.Object ? Object.class : kind.toJavaClass();
                        r.register2("consume", Receiver.class, javaClass, blackholePlugin);
                    }
                }
                r.register2("consume", Receiver.class, Object[].class, blackholePlugin);
            }
        }
    }

    private static void registerJFRThrowablePlugins(InvocationPlugins plugins) {
        String name = "oracle.jrockit.jfr.jdkevents.ThrowableTracer";
        Class<?> tracerClass = MethodSubstitutionPlugin.resolveClass(name, true);
        if (tracerClass != null) {
            Registration r = new Registration(plugins, tracerClass);
            r.register2("traceThrowable", Throwable.class, String.class, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode throwable, ValueNode message) {
                    b.add(new VirtualizableInvokeMacroNode(b.getInvokeKind(), targetMethod, b.bci(), b.getInvokeReturnType(), throwable, message));
                    return true;
                }
            });
        }
    }
}
