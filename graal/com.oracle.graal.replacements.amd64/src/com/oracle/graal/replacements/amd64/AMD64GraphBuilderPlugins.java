/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.amd64;

import jdk.internal.jvmci.amd64.*;
import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.meta.*;
import static com.oracle.graal.compiler.target.Backend.*;
import static com.oracle.graal.replacements.amd64.AMD64MathIntrinsicNode.Operation.*;
import sun.misc.*;

import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.graphbuilderconf.InvocationPlugin.Receiver;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.memory.address.*;
import com.oracle.graal.replacements.*;

public class AMD64GraphBuilderPlugins {

    public static void register(Plugins plugins, ForeignCallsProvider foreignCalls, AMD64 arch) {
        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        registerIntegerLongPlugins(invocationPlugins, IntegerSubstitutions.class, Kind.Int, arch);
        registerIntegerLongPlugins(invocationPlugins, LongSubstitutions.class, Kind.Long, arch);
        registerUnsafePlugins(invocationPlugins);
        registerMathPlugins(invocationPlugins, foreignCalls);
    }

    private static void registerIntegerLongPlugins(InvocationPlugins plugins, Class<?> substituteDeclaringClass, Kind kind, AMD64 arch) {
        Class<?> declaringClass = kind.toBoxedJavaClass();
        Class<?> type = kind.toJavaClass();
        Registration r = new Registration(plugins, declaringClass);
        if (arch.getFlags().contains(AMD64.Flag.UseCountLeadingZerosInstruction)) {
            r.register1("numberOfLeadingZeros", type, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                    ValueNode folded = AMD64CountLeadingZerosNode.tryFold(value);
                    if (folded != null) {
                        b.addPush(Kind.Int, folded);
                    } else {
                        b.addPush(Kind.Int, new AMD64CountLeadingZerosNode(value));
                    }
                    return true;
                }
            });
        } else {
            r.registerMethodSubstitution(substituteDeclaringClass, "numberOfLeadingZeros", type);
        }
        if (arch.getFlags().contains(AMD64.Flag.UseCountTrailingZerosInstruction)) {
            r.register1("numberOfTrailingZeros", type, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                    ValueNode folded = AMD64CountTrailingZerosNode.tryFold(value);
                    if (folded != null) {
                        b.addPush(Kind.Int, folded);
                    } else {
                        b.addPush(Kind.Int, new AMD64CountTrailingZerosNode(value));
                    }
                    return true;
                }
            });
        } else {
            r.registerMethodSubstitution(substituteDeclaringClass, "numberOfTrailingZeros", type);
        }
    }

    private static void registerMathPlugins(InvocationPlugins plugins, ForeignCallsProvider foreignCalls) {
        Registration r = new Registration(plugins, Math.class);
        r.register1("log", Double.TYPE, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(Kind.Double, b.recursiveAppend(AMD64MathIntrinsicNode.create(value, LOG)));
                return true;
            }
        });
        r.register1("log10", Double.TYPE, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(Kind.Double, b.recursiveAppend(AMD64MathIntrinsicNode.create(value, LOG10)));
                return true;
            }
        });
        r.registerMethodSubstitution(AMD64MathSubstitutions.class, "sin", double.class);
        r.registerMethodSubstitution(AMD64MathSubstitutions.class, "cos", double.class);
        r.registerMethodSubstitution(AMD64MathSubstitutions.class, "tan", double.class);
        r.registerMethodSubstitution(AMD64MathSubstitutions.class, "pow", double.class, double.class);
        r.register1("exp", Double.TYPE, new ForeignCallPlugin(foreignCalls, ARITHMETIC_EXP));
    }

    private static void registerUnsafePlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Unsafe.class);

        for (Kind kind : new Kind[]{Kind.Int, Kind.Long, Kind.Object}) {
            Class<?> javaClass = kind == Kind.Object ? Object.class : kind.toJavaClass();

            r.register4("getAndSet" + kind.name(), Receiver.class, Object.class, long.class, javaClass, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode object, ValueNode offset, ValueNode value) {
                    // Emits a null-check for the otherwise unused receiver
                    unsafe.get();
                    b.addPush(kind, new AtomicReadAndWriteNode(object, offset, value, kind, LocationIdentity.any()));
                    b.getGraph().markUnsafeAccess();
                    return true;
                }
            });
            if (kind != Kind.Object) {
                r.register4("getAndAdd" + kind.name(), Receiver.class, Object.class, long.class, javaClass, new InvocationPlugin() {
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode object, ValueNode offset, ValueNode delta) {
                        // Emits a null-check for the otherwise unused receiver
                        unsafe.get();
                        AddressNode address = b.add(new OffsetAddressNode(object, offset));
                        b.addPush(kind, new AtomicReadAndAddNode(address, delta, LocationIdentity.any()));
                        b.getGraph().markUnsafeAccess();
                        return true;
                    }
                });
            }
        }
    }
}
