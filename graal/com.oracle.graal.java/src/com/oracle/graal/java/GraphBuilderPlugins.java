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

import static java.lang.String.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;

/**
 * Interface for managing a set of graph builder {@link GraphBuilderPlugin}s.
 */
public interface GraphBuilderPlugins {

    public interface LoadFieldPlugin extends GraphBuilderPlugin {
        @SuppressWarnings("unused")
        default boolean apply(GraphBuilderContext builder, ValueNode receiver, ResolvedJavaField field) {
            return false;
        }

        @SuppressWarnings("unused")
        default boolean apply(GraphBuilderContext graphBuilderContext, ResolvedJavaField staticField) {
            return false;
        }
    }

    public interface ParameterPlugin extends GraphBuilderPlugin {
        FloatingNode interceptParameter(int index);
    }

    public interface InlineInvokePlugin extends GraphBuilderPlugin {
        boolean shouldInlineInvoke(ResolvedJavaMethod method, int depth);
    }

    public interface LoopExplosionPlugin extends GraphBuilderPlugin {
        boolean shouldExplodeLoops(ResolvedJavaMethod method);
    }

    /**
     * Plugin for handling a method invocation.
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

    /**
     * Utility for {@linkplain GraphBuilderPlugins#register(ResolvedJavaMethod, InvocationPlugin)
     * registration} of plugins.
     */
    public static class Registration {

        /**
         * Sentinel class for use with {@link Registration#register1},
         * {@link Registration#register2} or {@link Registration#register3} to denote the receiver
         * argument for a non-static method.
         */
        public static final class Receiver {
            private Receiver() {
                throw GraalInternalError.shouldNotReachHere();
            }
        }

        private final GraphBuilderPlugins plugins;
        private final MetaAccessProvider metaAccess;
        private final Class<?> declaringClass;

        /**
         * Creates an object for registering plugins for methods declared by a given class.
         *
         * @param plugins where to register the plugins
         * @param metaAccess used to resolve classes and methods
         * @param declaringClass the class declaring the methods for which plugins will be
         *            registered via this object
         */
        public Registration(GraphBuilderPlugins plugins, MetaAccessProvider metaAccess, Class<?> declaringClass) {
            this.plugins = plugins;
            this.metaAccess = metaAccess;
            this.declaringClass = declaringClass;
        }

        /**
         * Registers a plugin for a method with no arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register0(String name, InvocationPlugin plugin) {
            plugins.register(resolve(metaAccess, declaringClass, name), plugin);
        }

        /**
         * Registers a plugin for a method with 1 argument.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register1(String name, Class<?> arg, InvocationPlugin plugin) {
            ResolvedJavaMethod method = arg == Receiver.class ? resolve(metaAccess, declaringClass, name) : resolve(metaAccess, declaringClass, name, arg);
            plugins.register(method, plugin);
        }

        /**
         * Registers a plugin for a method with 2 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register2(String name, Class<?> arg1, Class<?> arg2, InvocationPlugin plugin) {
            ResolvedJavaMethod method = arg1 == Receiver.class ? resolve(metaAccess, declaringClass, name, arg2) : resolve(metaAccess, declaringClass, name, arg1, arg2);
            plugins.register(method, plugin);
        }

        /**
         * Registers a plugin for a method with 3 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register3(String name, Class<?> arg1, Class<?> arg2, Class<?> arg3, InvocationPlugin plugin) {
            ResolvedJavaMethod method = arg1 == Receiver.class ? resolve(metaAccess, declaringClass, name, arg2, arg3) : resolve(metaAccess, declaringClass, name, arg1, arg2, arg3);
            plugins.register(method, plugin);
        }

        /**
         * Registers a plugin for a method with 4 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register4(String name, Class<?> arg1, Class<?> arg2, Class<?> arg3, Class<?> arg4, InvocationPlugin plugin) {
            ResolvedJavaMethod method = arg1 == Receiver.class ? resolve(metaAccess, declaringClass, name, arg2, arg3, arg4) : resolve(metaAccess, declaringClass, name, arg1, arg2, arg3, arg4);
            plugins.register(method, plugin);
        }

        /**
         * Resolves a method given a declaring class, name and parameter types.
         */
        public static ResolvedJavaMethod resolve(MetaAccessProvider metaAccess, Class<?> declaringClass, String name, Class<?>... parameterTypes) {
            try {
                return metaAccess.lookupJavaMethod(name.equals("<init>") ? declaringClass.getDeclaredConstructor(parameterTypes) : declaringClass.getDeclaredMethod(name, parameterTypes));
            } catch (NoSuchMethodException | SecurityException e) {
                throw new GraalInternalError(e);
            }
        }
    }

    public static class InvocationPluginChecker {
        /**
         * The set of all {@link InvocationPlugin#apply} method signatures.
         */
        static final Class<?>[][] SIGS;
        static {
            ArrayList<Class<?>[]> sigs = new ArrayList<>(5);
            for (Method method : InvocationPlugin.class.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) && method.getName().equals("apply")) {
                    Class<?>[] sig = method.getParameterTypes();
                    assert sig[0] == GraphBuilderContext.class;
                    assert Arrays.asList(Arrays.copyOfRange(sig, 1, sig.length)).stream().allMatch(c -> c == ValueNode.class);
                    while (sigs.size() < sig.length) {
                        sigs.add(null);
                    }
                    sigs.set(sig.length - 1, sig);
                }
            }
            assert sigs.indexOf(null) == -1 : format("need to add an apply() method to %s that takes %d %s arguments ", InvocationPlugin.class.getName(), sigs.indexOf(null),
                            ValueNode.class.getSimpleName());
            SIGS = sigs.toArray(new Class<?>[sigs.size()][]);
        }

        public static boolean check(ResolvedJavaMethod method, InvocationPlugin plugin) {
            int arguments = method.getSignature().getParameterCount(!method.isStatic());
            assert arguments < SIGS.length : format("need to extend %s to support method with %d arguments: %s", InvocationPlugin.class.getSimpleName(), arguments, method.format("%H.%n(%p)"));
            Method expected = null;
            for (Method m : plugin.getClass().getDeclaredMethods()) {
                if (m.getName().equals("apply")) {
                    Class<?>[] parameterTypes = m.getParameterTypes();
                    assert Arrays.equals(SIGS[arguments], parameterTypes) : format("graph builder plugin for %s has wrong signature%nexpected: (%s)%n  actual: (%s)", method.format("%H.%n(%p)"),
                                    sigString(SIGS[arguments]), sigString(m.getParameterTypes()));
                    expected = m;
                }
            }
            assert expected != null : format("graph builder plugin %s must define exactly one \"apply\" method, none found", plugin);
            return true;
        }

        protected static String sigString(Class<?>... sig) {
            StringBuilder sb = new StringBuilder();
            for (Class<?> t : sig) {
                if (sb.length() != 0) {
                    sb.append(", ");
                }
                sb.append(t.getSimpleName());
            }
            return sb.toString();
        }

    }

    /**
     * Registers an {@link InvocationPlugin} for a given method. There must be no plugin currently
     * registered for {@code method}.
     */
    void register(ResolvedJavaMethod method, InvocationPlugin plugin);

    /**
     * Gets the {@link InvocationPlugin} for a given method.
     *
     * @param method the method to lookup
     * @return the plugin associated with {@code method} or {@code null} if none exists
     */
    InvocationPlugin lookupInvocation(ResolvedJavaMethod method);

    DefaultGraphBuilderPlugins copy();
}
