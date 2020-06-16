/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.llvm.replacements;

import static org.graalvm.compiler.replacements.StandardGraphBuilderPlugins.registerPlatformSpecificUnsafePlugins;

import java.util.Arrays;

import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.java.AtomicReadAndAddNode;
import org.graalvm.compiler.nodes.java.AtomicReadAndWriteNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.replacements.StandardGraphBuilderPlugins.UnsafeAccessPlugin;
import org.graalvm.compiler.replacements.StandardGraphBuilderPlugins.UnsafeGetPlugin;
import org.graalvm.compiler.replacements.StandardGraphBuilderPlugins.UnsafePutPlugin;
import org.graalvm.compiler.replacements.TargetGraphBuilderPlugins;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation;
import org.graalvm.compiler.replacements.nodes.BitCountNode;
import org.graalvm.compiler.replacements.nodes.FusedMultiplyAddNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

import com.oracle.svm.core.graal.llvm.replacements.LLVMIntrinsicNode.LLVMIntrinsicOperation;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

// Checkstyle: stop
import java.lang.reflect.Type;
import sun.misc.Unsafe;
// Checkstyle: resume

public class LLVMGraphBuilderPlugins implements TargetGraphBuilderPlugins {

    @Override
    public void register(Plugins plugins, Replacements replacements, Architecture arch, boolean explicitUnsafeNullChecks, boolean registerMathPlugins,
                    boolean emitJDK9StringSubstitutions, boolean useFMAIntrinsics) {
        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        invocationPlugins.defer(new Runnable() {
            @Override
            public void run() {
                registerIntegerLongPlugins(invocationPlugins, JavaKind.Int, replacements);
                registerIntegerLongPlugins(invocationPlugins, JavaKind.Long, replacements);
                registerPlatformSpecificUnsafePlugins(invocationPlugins, replacements, explicitUnsafeNullChecks,
                                new JavaKind[]{JavaKind.Int, JavaKind.Long, JavaKind.Object, JavaKind.Boolean, JavaKind.Byte, JavaKind.Short, JavaKind.Char, JavaKind.Float, JavaKind.Double});
                registerUnsafePlugins(invocationPlugins, replacements, explicitUnsafeNullChecks);
                registerMathPlugins(invocationPlugins, replacements);
            }
        });
    }

    private static void registerIntegerLongPlugins(InvocationPlugins plugins, JavaKind kind, Replacements replacements) {
        Class<?> declaringClass = kind.toBoxedJavaClass();
        Registration r = new Registration(plugins, declaringClass, replacements);
        registerUnaryLLVMIntrinsic(r, "numberOfLeadingZeros", LLVMIntrinsicOperation.CTLZ, JavaKind.Int, kind.toJavaClass());
        registerUnaryLLVMIntrinsic(r, "numberOfTrailingZeros", LLVMIntrinsicOperation.CTTZ, JavaKind.Int, kind.toJavaClass());
        r.register1("bitCount", kind.toJavaClass(), new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.append(new BitCountNode(value).canonical(null)));
                return true;
            }
        });
    }

    private static void registerMathPlugins(InvocationPlugins plugins, Replacements replacements) {
        Registration r = new Registration(plugins, Math.class, replacements);
        registerUnaryMath(r, "log", UnaryOperation.LOG);
        registerUnaryMath(r, "log10", UnaryOperation.LOG10);
        registerUnaryMath(r, "exp", UnaryOperation.EXP);
        registerBinaryMath(r, "pow", BinaryOperation.POW);
        registerUnaryMath(r, "sin", UnaryOperation.SIN);
        registerUnaryMath(r, "cos", UnaryOperation.COS);
        registerUnaryMath(r, "tan", UnaryOperation.TAN);

        registerUnaryLLVMIntrinsic(r, "ceil", LLVMIntrinsicOperation.CEIL, JavaKind.Double, double.class);
        registerUnaryLLVMIntrinsic(r, "floor", LLVMIntrinsicOperation.FLOOR, JavaKind.Double, double.class);

        for (JavaKind kind : Arrays.asList(JavaKind.Float, JavaKind.Double)) {
            registerBinaryLLVMIntrinsic(r, "copySign", LLVMIntrinsicOperation.COPYSIGN, kind, kind.toJavaClass(), kind.toJavaClass());
        }

        if (JavaVersionUtil.JAVA_SPEC > 8) {
            registerFMA(r);
        }
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

    private static void registerFMA(Registration r) {
        r.register3("fma",
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
        r.register3("fma",
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

    private static void registerUnsafePlugins(InvocationPlugins plugins, Replacements replacements, boolean explicitUnsafeNullChecks) {
        registerUnsafePlugins(new Registration(plugins, Unsafe.class), explicitUnsafeNullChecks, new JavaKind[]{JavaKind.Int, JavaKind.Long, JavaKind.Object}, true);
        if (JavaVersionUtil.JAVA_SPEC > 8) {
            registerUnsafePlugins(new Registration(plugins, "jdk.internal.misc.Unsafe", replacements), explicitUnsafeNullChecks,
                            new JavaKind[]{JavaKind.Boolean, JavaKind.Byte, JavaKind.Char, JavaKind.Short, JavaKind.Int, JavaKind.Long, JavaKind.Object},
                            JavaVersionUtil.JAVA_SPEC <= 11);
        }
    }

    private static void registerUnsafePlugins(Registration r, boolean explicitUnsafeNullChecks, JavaKind[] unsafeJavaKinds, boolean java11OrEarlier) {
        for (JavaKind kind : unsafeJavaKinds) {
            Class<?> javaClass = kind == JavaKind.Object ? Object.class : kind.toJavaClass();
            String kindName = (kind == JavaKind.Object && !java11OrEarlier) ? "Reference" : kind.name();
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
                        createUnsafeAccess(object, b, (obj, loc) -> new AtomicReadAndAddNode(b.add(new OffsetAddressNode(obj, offset)), delta, kind, loc));
                        return true;
                    }
                });
            }
        }

        for (JavaKind kind : new JavaKind[]{JavaKind.Char, JavaKind.Short, JavaKind.Int, JavaKind.Long}) {
            Class<?> javaClass = kind.toJavaClass();
            r.registerOptional3("get" + kind.name() + "Unaligned", Receiver.class, Object.class, long.class, new UnsafeGetPlugin(kind, explicitUnsafeNullChecks));
            r.registerOptional4("put" + kind.name() + "Unaligned", Receiver.class, Object.class, long.class, javaClass, new UnsafePutPlugin(kind, explicitUnsafeNullChecks));
        }
    }

    @SuppressWarnings("unused")
    private static void registerUnaryLLVMIntrinsic(Registration registration, String name, LLVMIntrinsicOperation op, JavaKind returnKind, Type argType) {
        registration.register1(name, argType, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                ConstantNode folded = LLVMUnaryIntrinsicNode.tryFold(op, arg);
                b.addPush(returnKind, (folded != null) ? folded : LLVMUnaryIntrinsicNode.factory(op, returnKind, arg));
                return true;
            }
        });
    }

    @SuppressWarnings("unused")
    private static void registerBinaryLLVMIntrinsic(Registration registration, String name, LLVMIntrinsicOperation op, JavaKind returnKind, Type argType1, Type argType2) {
        registration.register2(name, argType1, argType2, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
                ConstantNode folded = LLVMBinaryIntrinsicNode.tryFold(op, arg1, arg2);
                b.addPush(returnKind, (folded != null) ? folded : LLVMBinaryIntrinsicNode.factory(op, returnKind, arg1, arg2));
                return true;
            }
        });
    }
}
