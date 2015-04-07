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
package com.oracle.graal.replacements.amd64;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Receiver;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.nodes.*;
import com.oracle.graal.replacements.*;

public class AMD64GraphBuilderPlugins {

    public static void register(Plugins plugins, AMD64 arch) {
        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        registerIntegerLongPlugins(invocationPlugins, IntegerSubstitutions.class, Kind.Int, arch);
        registerIntegerLongPlugins(invocationPlugins, LongSubstitutions.class, Kind.Long, arch);
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
                        b.addPush(folded);
                    } else {
                        b.addPush(new AMD64CountLeadingZerosNode(value));
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
                        b.addPush(folded);
                    } else {
                        b.addPush(new AMD64CountTrailingZerosNode(value));
                    }
                    return true;
                }
            });
        } else {
            r.registerMethodSubstitution(substituteDeclaringClass, "numberOfTrailingZeros", type);
        }
    }

}
