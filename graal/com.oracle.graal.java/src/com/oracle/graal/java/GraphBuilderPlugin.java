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
package com.oracle.graal.java;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;

/**
 * Marker interface for graph builder plugins.
 */
public interface GraphBuilderPlugin {

    public interface LoadFieldPlugin extends GraphBuilderPlugin {
        @SuppressWarnings("unused")
        default boolean apply(GraphBuilderContext builder, ValueNode receiver, ResolvedJavaField field) {
            return false;
        }

        @SuppressWarnings("unused")
        default boolean apply(GraphBuilderContext graphBuilderContext, ResolvedJavaField staticField) {
            return false;
        }

        default boolean tryConstantFold(GraphBuilderContext builder, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, ResolvedJavaField field, JavaConstant asJavaConstant) {
            JavaConstant result = constantReflection.readConstantFieldValue(field, asJavaConstant);
            if (result != null) {
                ConstantNode constantNode = builder.append(ConstantNode.forConstant(result, metaAccess));
                builder.push(constantNode.getKind().getStackKind(), constantNode);
                return true;
            }
            return false;
        }
    }

    /**
     * Plugin for customizing how the graph builder handles a CHECKCAST instruction in the context
     * of the instruction that consumes it from the stack.
     */
    public interface CheckCastPlugin extends GraphBuilderPlugin {
        boolean apply(GraphBuilderContext builder, ResolvedJavaType type, ValueNode object, JavaTypeProfile profileForTypeCheck);
    }

    public interface InlineInvokePlugin extends GraphBuilderPlugin {

        default void postInline(@SuppressWarnings("unused") ResolvedJavaMethod inlinedTargetMethod) {

        }

        ResolvedJavaMethod getInlinedMethod(GraphBuilderContext builder, ResolvedJavaMethod method, ValueNode[] args, JavaType returnType, int depth);
    }

    public interface LoopExplosionPlugin extends GraphBuilderPlugin {
        boolean shouldExplodeLoops(ResolvedJavaMethod method);
    }

    public interface ParameterPlugin extends GraphBuilderPlugin {
        FloatingNode interceptParameter(int index);
    }

    /**
     * Plugin for handling an invocation based on the annotations of the invoked method.
     */
    public interface AnnotatedInvocationPlugin extends GraphBuilderPlugin {
        /**
         * Executes this plugin for an invocation of a given method with a given set of arguments.
         *
         * @return {@code true} if this plugin handled the invocation, {@code false} if not
         */
        boolean apply(GraphBuilderContext builder, ResolvedJavaMethod method, ValueNode[] args);
    }

    /**
     * Plugin for handling a specific method invocation.
     */
    public interface InvocationPlugin extends GraphBuilderPlugin {
        /**
         * @see #execute(GraphBuilderContext, InvocationPlugin, ValueNode[])
         */
        default boolean apply(GraphBuilderContext builder) {
            throw invalidHandler(builder);
        }

        /**
         * @see #execute(GraphBuilderContext, InvocationPlugin, ValueNode[])
         */
        default boolean apply(GraphBuilderContext builder, ValueNode arg) {
            throw invalidHandler(builder, arg);
        }

        /**
         * @see #execute(GraphBuilderContext, InvocationPlugin, ValueNode[])
         */
        default boolean apply(GraphBuilderContext builder, ValueNode arg1, ValueNode arg2) {
            throw invalidHandler(builder, arg1, arg2);
        }

        /**
         * @see #execute(GraphBuilderContext, InvocationPlugin, ValueNode[])
         */
        default boolean apply(GraphBuilderContext builder, ValueNode arg1, ValueNode arg2, ValueNode arg3) {
            throw invalidHandler(builder, arg1, arg2, arg3);
        }

        /**
         * @see #execute(GraphBuilderContext, InvocationPlugin, ValueNode[])
         */
        default boolean apply(GraphBuilderContext builder, ValueNode arg1, ValueNode arg2, ValueNode arg3, ValueNode arg4) {
            throw invalidHandler(builder, arg1, arg2, arg3, arg4);
        }

        /**
         * Executes a given plugin against a set of invocation arguments by dispatching to the
         * plugin's {@code apply(...)} method that matches the number of arguments.
         *
         * @return {@code true} if the plugin handled the invocation, {@code false} if the graph
         *         builder should process the invoke further (e.g., by inlining it or creating an
         *         {@link Invoke} node). A plugin that does not handle an invocation must not modify
         *         the graph being constructed.
         */
        static boolean execute(GraphBuilderContext builder, InvocationPlugin plugin, ValueNode[] args) {
            if (args.length == 0) {
                return plugin.apply(builder);
            } else if (args.length == 1) {
                return plugin.apply(builder, args[0]);
            } else if (args.length == 2) {
                return plugin.apply(builder, args[0], args[1]);
            } else if (args.length == 3) {
                return plugin.apply(builder, args[0], args[1], args[2]);
            } else if (args.length == 4) {
                return plugin.apply(builder, args[0], args[1], args[2], args[3]);
            } else {
                throw plugin.invalidHandler(builder, args);
            }
        }

        default Error invalidHandler(@SuppressWarnings("unused") GraphBuilderContext builder, ValueNode... args) {
            return new GraalInternalError("Invocation plugin %s does not handle invocations with %d arguments", getClass().getSimpleName(), args.length);
        }
    }
}
