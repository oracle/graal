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
package com.oracle.graal.replacements.sparc;

import static com.oracle.graal.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.COS;
import static com.oracle.graal.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.EXP;
import static com.oracle.graal.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG;
import static com.oracle.graal.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG10;
import static com.oracle.graal.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.SIN;
import static com.oracle.graal.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.TAN;

import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.replacements.IntegerSubstitutions;
import com.oracle.graal.replacements.LongSubstitutions;
import com.oracle.graal.replacements.nodes.BinaryMathIntrinsicNode;
import com.oracle.graal.replacements.nodes.BitCountNode;
import com.oracle.graal.replacements.nodes.UnaryMathIntrinsicNode;
import com.oracle.graal.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SPARCGraphBuilderPlugins {

    public static void register(Plugins plugins) {
        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        invocationPlugins.defer(new Runnable() {
            @Override
            public void run() {
                registerIntegerLongPlugins(invocationPlugins, IntegerSubstitutions.class, JavaKind.Int);
                registerIntegerLongPlugins(invocationPlugins, LongSubstitutions.class, JavaKind.Long);
                registerMathPlugins(invocationPlugins);
            }
        });
    }

    private static void registerIntegerLongPlugins(InvocationPlugins plugins, Class<?> substituteDeclaringClass, JavaKind kind) {
        Class<?> declaringClass = kind.toBoxedJavaClass();
        Class<?> type = kind.toJavaClass();
        Registration r = new Registration(plugins, declaringClass);
        r.registerMethodSubstitution(substituteDeclaringClass, "numberOfLeadingZeros", type);
        r.registerMethodSubstitution(substituteDeclaringClass, "numberOfTrailingZeros", type);

        r.register1("bitCount", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.recursiveAppend(new BitCountNode(value).canonical(null)));
                return true;
            }
        });
    }

    private static void registerMathPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Math.class);
        registerUnaryMath(r, "sin", SIN);
        registerUnaryMath(r, "cos", COS);
        registerUnaryMath(r, "tan", TAN);
        registerUnaryMath(r, "exp", EXP);
        registerUnaryMath(r, "log", LOG);
        registerUnaryMath(r, "log10", LOG10);
        r.register2("pow", Double.TYPE, Double.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(JavaKind.Double, b.recursiveAppend(BinaryMathIntrinsicNode.create(x, y, BinaryMathIntrinsicNode.BinaryOperation.POW)));
                return true;
            }
        });
    }

    private static void registerUnaryMath(Registration r, String name, UnaryOperation operation) {
        r.register1(name, Double.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.recursiveAppend(UnaryMathIntrinsicNode.create(value, operation)));
                return true;
            }
        });
    }
}
