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

import java.util.concurrent.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.java.*;
import com.oracle.graal.java.GraphBuilderPlugins.InvocationPlugin;
import com.oracle.graal.java.GraphBuilderPlugins.Registration;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.truffle.*;
import com.oracle.graal.truffle.nodes.frame.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;

/**
 * Provider of {@link GraphBuilderPlugin}s for Truffle classes.
 */
public class TruffleGraphBuilderPluginsProvider implements GraphBuilderPluginsProvider {
    public void registerPlugins(MetaAccessProvider metaAccess, GraphBuilderPlugins plugins) {

        Registration r2 = new Registration(plugins, metaAccess, OptimizedCallTarget.class);
        r2.register2("createFrame", FrameDescriptor.class, Object[].class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder, ValueNode arg1, ValueNode arg2) {
                builder.push(Kind.Object, builder.append(new NewFrameNode(StampFactory.exactNonNull(metaAccess.lookupJavaType(FrameWithoutBoxing.class)), arg1, arg2)));
                return true;
            }
        });

        Registration r = new Registration(plugins, metaAccess, CompilerDirectives.class);
        r.register0("inInterpreter", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder) {
                builder.push(Kind.Boolean.getStackKind(), builder.append(ConstantNode.forBoolean(false)));
                return true;
            }
        });
        r.register0("inCompiledCode", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder) {
                builder.push(Kind.Boolean.getStackKind(), builder.append(ConstantNode.forBoolean(true)));
                return true;
            }
        });
        r.register0("transferToInterpreter", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder) {
                builder.append(new DeoptimizeNode(DeoptimizationAction.None, DeoptimizationReason.TransferToInterpreter));
                return true;
            }
        });
        r.register0("transferToInterpreterAndInvalidate", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder) {
                builder.append(new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.TransferToInterpreter));
                return true;
            }
        });
        r.register1("interpreterOnly", Runnable.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder, ValueNode arg) {
                return true;
            }
        });
        r.register1("interpreterOnly", Callable.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder, ValueNode arg) {
                return true;
            }
        });
        r.register2("injectBranchProbability", double.class, boolean.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder, ValueNode probability, ValueNode condition) {
                builder.push(Kind.Boolean.getStackKind(), builder.append(new BranchProbabilityNode(probability, condition)));
                return true;
            }
        });
        r.register1("bailout", String.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder, ValueNode message) {
                if (message.isConstant()) {
                    throw new BailoutException(message.asConstant().toValueString());
                }
                throw new BailoutException("bailout (message is not compile-time constant, so no additional information is available)");
            }
        });
        r.register1("isCompilationConstant", Object.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder, ValueNode value) {
                if ((value instanceof BoxNode ? ((BoxNode) value).getValue() : value).isConstant()) {
                    builder.push(Kind.Boolean.getStackKind(), builder.append(ConstantNode.forBoolean(true)));
                    return true;
                }
                return false;
            }
        });
        r.register1("materialize", Object.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder, ValueNode value) {
                builder.append(new ForceMaterializeNode(value));
                return true;
            }
        });
    }
}
