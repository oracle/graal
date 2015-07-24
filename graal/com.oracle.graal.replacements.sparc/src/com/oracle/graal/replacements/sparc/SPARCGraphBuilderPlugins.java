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

import jdk.internal.jvmci.meta.*;
import static com.oracle.graal.compiler.target.Backend.*;

import com.oracle.graal.compiler.common.spi.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.replacements.*;

public class SPARCGraphBuilderPlugins {

    public static void register(Plugins plugins, ForeignCallsProvider foreignCalls) {
        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        registerIntegerLongPlugins(invocationPlugins, IntegerSubstitutions.class, Kind.Int);
        registerIntegerLongPlugins(invocationPlugins, LongSubstitutions.class, Kind.Long);
        registerMathPlugins(invocationPlugins, foreignCalls);
    }

    private static void registerIntegerLongPlugins(InvocationPlugins plugins, Class<?> substituteDeclaringClass, Kind kind) {
        Class<?> declaringClass = kind.toBoxedJavaClass();
        Class<?> type = kind.toJavaClass();
        Registration r = new Registration(plugins, declaringClass);
        r.registerMethodSubstitution(substituteDeclaringClass, "numberOfLeadingZeros", type);
        r.registerMethodSubstitution(substituteDeclaringClass, "numberOfTrailingZeros", type);
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
