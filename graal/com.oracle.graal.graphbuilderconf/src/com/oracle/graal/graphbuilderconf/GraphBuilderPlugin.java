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
package com.oracle.graal.graphbuilderconf;

import java.lang.reflect.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;

/**
 * Marker interface for graph builder plugins.
 */
public interface GraphBuilderPlugin {

    public interface LoadFieldPlugin extends GraphBuilderPlugin {
        @SuppressWarnings("unused")
        default boolean apply(GraphBuilderContext b, ValueNode receiver, ResolvedJavaField field) {
            return false;
        }

        @SuppressWarnings("unused")
        default boolean apply(GraphBuilderContext graphBuilderContext, ResolvedJavaField staticField) {
            return false;
        }

        default boolean tryConstantFold(GraphBuilderContext b, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, ResolvedJavaField field, JavaConstant receiver) {
            JavaConstant result = constantReflection.readConstantFieldValue(field, receiver);
            if (result != null) {
                ConstantNode constantNode = b.append(ConstantNode.forConstant(result, metaAccess));
                b.push(constantNode.getKind().getStackKind(), constantNode);
                return true;
            }
            return false;
        }
    }

    public interface LoadIndexedPlugin extends GraphBuilderPlugin {
        @SuppressWarnings("unused")
        default boolean apply(GraphBuilderContext b, ValueNode array, ValueNode index, Kind elementKind) {
            return false;
        }
    }

    /**
     * Plugin for specifying what is inlined during graph parsing or for post-processing non-inlined
     * invocations that result in {@link Invoke} nodes.
     */
    public interface InlineInvokePlugin extends GraphBuilderPlugin {

        public static class InlineInfo {

            /**
             * The method to be inlined. If this is not equal to the {@code method} argument passed
             * to {@link InlineInvokePlugin#getClass()}, the graph builder context interprets it as
             * a {@linkplain GraphBuilderContext.Replacement replacement}.
             */
            public final ResolvedJavaMethod methodToInline;

            /**
             * Specifies if {@link #methodToInline} is an intrinsic for the original method. If so,
             * any {@link StateSplit} node created in the (recursive) inlining scope will be given a
             * frame state that restarts the interpreter just before the intrinsified invocation.
             */
            public final boolean adoptBeforeCallFrameState;

            public InlineInfo(ResolvedJavaMethod methodToInline, boolean adoptBeforeCallFrameState) {
                this.methodToInline = methodToInline;
                this.adoptBeforeCallFrameState = adoptBeforeCallFrameState;
            }
        }

        /**
         * Determines whether a call to a given method is to be inlined.
         *
         * @param method the target method of an invoke
         * @param args the arguments to the invoke
         * @param returnType the return type derived from {@code method}'s signature
         */
        default InlineInfo getInlineInfo(@SuppressWarnings("unused") GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args, JavaType returnType) {
            return null;
        }

        /**
         * @param inlinedTargetMethod
         */
        default void postInline(ResolvedJavaMethod inlinedTargetMethod) {
        }

        /**
         * Notifies this plugin of the {@link Invoke} node created for a method that was not inlined
         * per {@link #getInlineInfo}.
         *
         * @param method the method that was not inlined
         * @param invoke the invoke node created for the call to {@code method}
         */
        default void notifyOfNoninlinedInvoke(@SuppressWarnings("unused") GraphBuilderContext b, ResolvedJavaMethod method, Invoke invoke) {
        }
    }

    public interface LoopExplosionPlugin extends GraphBuilderPlugin {
        boolean shouldExplodeLoops(ResolvedJavaMethod method);

        boolean shouldMergeExplosions(ResolvedJavaMethod method);
    }

    public interface ParameterPlugin extends GraphBuilderPlugin {
        FloatingNode interceptParameter(GraphBuilderContext b, int index, Stamp stamp);
    }

    /**
     * Plugin for handling an invocation based on some property of the method being invoked such as
     * any annotations it may have.
     */
    public interface GenericInvocationPlugin extends GraphBuilderPlugin {
        /**
         * Executes this plugin for an invocation of a given method with a given set of arguments.
         *
         * @return {@code true} if this plugin handled the invocation, {@code false} if not
         */
        boolean apply(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args);
    }

    /**
     * Plugin for handling a specific method invocation.
     */
    public interface InvocationPlugin extends GraphBuilderPlugin {
        /**
         * @see #execute
         */
        default boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod) {
            throw invalidHandler(b, targetMethod);
        }

        /**
         * @see #execute
         */
        default boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode arg) {
            throw invalidHandler(b, targetMethod, arg);
        }

        /**
         * @see #execute
         */
        default boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode arg1, ValueNode arg2) {
            throw invalidHandler(b, targetMethod, arg1, arg2);
        }

        /**
         * @see #execute
         */
        default boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode arg1, ValueNode arg2, ValueNode arg3) {
            throw invalidHandler(b, targetMethod, arg1, arg2, arg3);
        }

        /**
         * @see #execute
         */
        default boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode arg1, ValueNode arg2, ValueNode arg3, ValueNode arg4) {
            throw invalidHandler(b, targetMethod, arg1, arg2, arg3, arg4);
        }

        /**
         * @see #execute
         */
        default boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode arg1, ValueNode arg2, ValueNode arg3, ValueNode arg4, ValueNode arg5) {
            throw invalidHandler(b, targetMethod, arg1, arg2, arg3, arg4, arg5);
        }

        default ResolvedJavaMethod getSubstitute() {
            return null;
        }

        boolean ALLOW_INVOCATION_PLUGIN_TO_DO_INLINING = false;

        /**
         * Executes a given plugin against a set of invocation arguments by dispatching to the
         * {@code apply(...)} method that matches the number of arguments.
         *
         * @param targetMethod the method for which plugin is being applied
         * @return {@code true} if the plugin handled the invocation of {@code targetMethod}
         *         {@code false} if the graph builder should process the invoke further (e.g., by
         *         inlining it or creating an {@link Invoke} node). A plugin that does not handle an
         *         invocation must not modify the graph being constructed.
         */
        static boolean execute(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin plugin, ValueNode[] args) {
// if (ALLOW_INVOCATION_PLUGIN_TO_DO_INLINING) {
// ResolvedJavaMethod subst = plugin.getSubstitute();
// if (subst != null) {
// return ((BytecodeParser) b).inline(null, targetMethod, new InlineInfo(subst, false), args);
// }
// }
            if (args.length == 0) {
                return plugin.apply(b, targetMethod);
            } else if (args.length == 1) {
                return plugin.apply(b, targetMethod, args[0]);
            } else if (args.length == 2) {
                return plugin.apply(b, targetMethod, args[0], args[1]);
            } else if (args.length == 3) {
                return plugin.apply(b, targetMethod, args[0], args[1], args[2]);
            } else if (args.length == 4) {
                return plugin.apply(b, targetMethod, args[0], args[1], args[2], args[3]);
            } else if (args.length == 5) {
                return plugin.apply(b, targetMethod, args[0], args[1], args[2], args[3], args[4]);
            } else {
                throw plugin.invalidHandler(b, targetMethod, args);
            }
        }

        default Error invalidHandler(@SuppressWarnings("unused") GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode... args) {
            return new GraalInternalError("Invocation plugin for %s does not handle invocations with %d arguments", targetMethod.format("%H.%n(%p)"), args.length);
        }

        default StackTraceElement getApplySourceLocation(MetaAccessProvider metaAccess) {
            Class<?> c = getClass();
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals("apply")) {
                    return metaAccess.lookupJavaMethod(m).asStackTraceElement(0);
                }
            }
            throw new GraalInternalError("could not find method named \"apply\" in " + c.getName());
        }
    }
}
