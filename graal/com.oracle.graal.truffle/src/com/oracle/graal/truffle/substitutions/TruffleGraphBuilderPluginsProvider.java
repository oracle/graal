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
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.truffle.nodes.frame.*;
import com.oracle.truffle.api.*;

/**
 * Provider of {@link GraphBuilderPlugin}s for Truffle classes.
 */
@ServiceProvider(GraphBuilderPluginsProvider.class)
public class TruffleGraphBuilderPluginsProvider implements GraphBuilderPluginsProvider {
    public void registerPlugins(MetaAccessProvider metaAccess, GraphBuilderPlugins plugins) {
        plugins.register(metaAccess, CompilerDirectivesPlugin.class);
    }

    /**
     * Plugins for {@link CompilerDirectives}.
     */
    enum CompilerDirectivesPlugin implements GraphBuilderPlugin {
        inInterpreter() {
            public boolean handleInvocation(GraphBuilderContext builder, ValueNode[] args) {
                builder.append(ConstantNode.forBoolean(false));
                return true;
            }
        },
        inCompiledCode() {
            public boolean handleInvocation(GraphBuilderContext builder, ValueNode[] args) {
                builder.append(ConstantNode.forBoolean(true));
                return true;
            }
        },
        transferToInterpreter() {
            public boolean handleInvocation(GraphBuilderContext builder, ValueNode[] args) {
                builder.append(new DeoptimizeNode(DeoptimizationAction.None, DeoptimizationReason.TransferToInterpreter));
                return true;
            }
        },
        transferToInterpreterAndInvalidate() {
            public boolean handleInvocation(GraphBuilderContext builder, ValueNode[] args) {
                builder.append(new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.TransferToInterpreter));
                return true;
            }
        },
        interpreterOnly(Runnable.class) {
            public boolean handleInvocation(GraphBuilderContext builder, ValueNode[] args) {
                return true;
            }
        },
        interpreterOnly$(Callable.class) {
            public boolean handleInvocation(GraphBuilderContext builder, ValueNode[] args) {
                return true;
            }
        },
        injectBranchProbability(double.class, boolean.class) {
            public boolean handleInvocation(GraphBuilderContext builder, ValueNode[] args) {
                ValueNode probability = args[0];
                ValueNode condition = args[1];
                builder.append(new BranchProbabilityNode(probability, condition));
                return true;
            }
        },
        bailout(String.class) {
            public boolean handleInvocation(GraphBuilderContext builder, ValueNode[] args) {
                // TODO: is this too eager? Should a BailoutNode be created instead?
                ValueNode message = args[0];
                if (message.isConstant()) {
                    throw new BailoutException(message.asConstant().toValueString());
                }
                throw new BailoutException("bailout (message is not compile-time constant, so no additional information is available)");
            }
        },

        isCompilationConstant(Object.class) {
            public boolean handleInvocation(GraphBuilderContext builder, ValueNode[] args) {
                ValueNode arg0 = args[0];
                if (arg0 instanceof BoxNode) {
                    arg0 = ((BoxNode) arg0).getValue();
                }
                if (arg0.isConstant()) {
                    builder.push(Kind.Boolean, builder.append(ConstantNode.forBoolean(true)));
                    return true;
                }

                // Cannot create MacroNodes in a plugin (yet)
                return false;
            }
        },
        materialize(Object.class) {
            public boolean handleInvocation(GraphBuilderContext builder, ValueNode[] args) {
                builder.append(new ForceMaterializeNode(args[0]));
                return true;
            }
        };

        CompilerDirectivesPlugin(Class<?>... parameterTypes) {
            this.parameterTypes = parameterTypes;
        }

        private final Class<?>[] parameterTypes;

        public ResolvedJavaMethod getInvocationTarget(MetaAccessProvider metaAccess) {
            return GraphBuilderPlugin.resolveTarget(metaAccess, CompilerDirectives.class, name(), parameterTypes);
        }
    }
}
