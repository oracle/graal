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

import static com.oracle.graal.compiler.target.Backend.ARITHMETIC_COS;
import static com.oracle.graal.compiler.target.Backend.ARITHMETIC_EXP;
import static com.oracle.graal.compiler.target.Backend.ARITHMETIC_LOG;
import static com.oracle.graal.compiler.target.Backend.ARITHMETIC_LOG10;
import static com.oracle.graal.compiler.target.Backend.ARITHMETIC_POW;
import static com.oracle.graal.compiler.target.Backend.ARITHMETIC_SIN;
import static com.oracle.graal.compiler.target.Backend.ARITHMETIC_TAN;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import com.oracle.graal.compiler.common.spi.ForeignCallsProvider;
import com.oracle.graal.graphbuilderconf.ForeignCallPlugin;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.replacements.IntegerSubstitutions;
import com.oracle.graal.replacements.LongSubstitutions;
import com.oracle.graal.replacements.nodes.BitCountNode;

public class SPARCGraphBuilderPlugins {

    public static void register(Plugins plugins, ForeignCallsProvider foreignCalls) {
        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        registerIntegerLongPlugins(invocationPlugins, IntegerSubstitutions.class, JavaKind.Int);
        registerIntegerLongPlugins(invocationPlugins, LongSubstitutions.class, JavaKind.Long);
        registerMathPlugins(invocationPlugins, foreignCalls);
    }

    private static void registerIntegerLongPlugins(InvocationPlugins plugins, Class<?> substituteDeclaringClass, JavaKind kind) {
        Class<?> declaringClass = kind.toBoxedJavaClass();
        Class<?> type = kind.toJavaClass();
        Registration r = new Registration(plugins, declaringClass);
        r.registerMethodSubstitution(substituteDeclaringClass, "numberOfLeadingZeros", type);
        r.registerMethodSubstitution(substituteDeclaringClass, "numberOfTrailingZeros", type);

        r.register1("bitCount", type, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.recursiveAppend(new BitCountNode(value).canonical(null, value)));
                return true;
            }
        });
    }

    private static void registerMathPlugins(InvocationPlugins plugins, ForeignCallsProvider foreignCalls) {
        Registration r = new Registration(plugins, Math.class);
        r.register1("sin", Double.TYPE, new ForeignCallPlugin(foreignCalls, ARITHMETIC_SIN));
        r.register1("cos", Double.TYPE, new ForeignCallPlugin(foreignCalls, ARITHMETIC_COS));
        r.register1("tan", Double.TYPE, new ForeignCallPlugin(foreignCalls, ARITHMETIC_TAN));
        r.register1("exp", Double.TYPE, new ForeignCallPlugin(foreignCalls, ARITHMETIC_EXP));
        r.register1("log", Double.TYPE, new ForeignCallPlugin(foreignCalls, ARITHMETIC_LOG));
        r.register1("log10", Double.TYPE, new ForeignCallPlugin(foreignCalls, ARITHMETIC_LOG10));
        r.register2("pow", Double.TYPE, Double.TYPE, new ForeignCallPlugin(foreignCalls, ARITHMETIC_POW));
    }
}
