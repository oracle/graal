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
package com.oracle.graal.java;

import com.oracle.graal.api.directives.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.java.GraphBuilderPlugins.InvocationPlugin;
import com.oracle.graal.java.GraphBuilderPlugins.Registration;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.debug.*;
import com.oracle.graal.nodes.extended.*;

public class GraalDirectivePlugins {

    private static ResolvedJavaMethod lookup(MetaAccessProvider metaAccess, String name, Class<?>... parameterTypes) {
        return Registration.resolve(metaAccess, GraalDirectives.class, name, parameterTypes);
    }

    public static void registerPlugins(MetaAccessProvider metaAccess, GraphBuilderPlugins plugins) {
        plugins.register(lookup(metaAccess, "deoptimize"), new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder) {
                builder.append(new DeoptimizeNode(DeoptimizationAction.None, DeoptimizationReason.TransferToInterpreter));
                return true;
            }
        });

        plugins.register(lookup(metaAccess, "deoptimizeAndInvalidate"), new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder) {
                builder.append(new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.TransferToInterpreter));
                return true;
            }
        });

        plugins.register(lookup(metaAccess, "inCompiledCode"), new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder) {
                builder.push(Kind.Int, builder.append(ConstantNode.forInt(1)));
                return true;
            }
        });

        plugins.register(lookup(metaAccess, "controlFlowAnchor"), new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder) {
                builder.append(new ControlFlowAnchorNode());
                return true;
            }
        });

        plugins.register(lookup(metaAccess, "injectBranchProbability", double.class, boolean.class), new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder, ValueNode probability, ValueNode condition) {
                builder.push(Kind.Int, builder.append(new BranchProbabilityNode(probability, condition)));
                return true;
            }
        });

        InvocationPlugin blackholePlugin = new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder, ValueNode value) {
                builder.append(new BlackholeNode(value));
                return true;
            }
        };

        for (Kind kind : Kind.values()) {
            Class<?> cls = null;
            switch (kind) {
                case Object:
                    cls = Object.class;
                    break;
                case Void:
                case Illegal:
                    continue;
                default:
                    cls = kind.toJavaClass();
            }

            plugins.register(lookup(metaAccess, "blackhole", cls), blackholePlugin);

            final Kind stackKind = kind.getStackKind();
            plugins.register(lookup(metaAccess, "opaque", cls), new InvocationPlugin() {
                public boolean apply(GraphBuilderContext builder, ValueNode value) {
                    builder.push(stackKind, builder.append(new OpaqueNode(value)));
                    return true;
                }
            });
        }
    }
}
