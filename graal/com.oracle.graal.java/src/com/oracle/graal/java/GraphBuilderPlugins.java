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
        boolean apply(GraphBuilderContext builder, ValueNode receiver, ResolvedJavaField field);
    }

    public interface ParameterPlugin extends GraphBuilderPlugin {
        FloatingNode interceptParameter(int index);
    }

    /**
     * Plugin for handling a method invocation.
     */
    public interface InvocationPlugin extends GraphBuilderPlugin {
        /**
         * Tries to handle an invocation to a method with no arguments.
         *
         * @return {@code true} this plugin handled the invocation
         */
        default boolean apply(GraphBuilderContext builder) {
            throw invalidHandler(builder);
        }

        /**
         * Tries to handle an invocation to a method with one argument.
         *
         * @return {@code true} this plugin handled the invocation
         */
        default boolean apply(GraphBuilderContext builder, ValueNode arg) {
            throw invalidHandler(builder, arg);
        }

        /**
         * Tries to handle an invocation to a method with two arguments.
         *
         * @return {@code true} this plugin handled the invocation
         */
        default boolean apply(GraphBuilderContext builder, ValueNode arg1, ValueNode arg2) {
            throw invalidHandler(builder, arg1, arg2);
        }

        /**
         * Tries to handle an invocation to a method with three arguments.
         *
         * @return {@code true} this plugin handled the invocation
         */
        default boolean apply(GraphBuilderContext builder, ValueNode arg1, ValueNode arg2, ValueNode arg3) {
            throw invalidHandler(builder, arg1, arg2, arg3);
        }

        default boolean apply(GraphBuilderContext builder, ValueNode[] args) {
            if (args.length == 0) {
                return apply(builder);
            } else if (args.length == 1) {
                return apply(builder, args[0]);
            } else if (args.length == 2) {
                return apply(builder, args[0], args[1]);
            } else if (args.length == 3) {
                return apply(builder, args[0], args[1], args[2]);
            } else {
                throw invalidHandler(builder, args);
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
        static final Class<?>[] APPLY0 = {GraphBuilderContext.class};
        static final Class<?>[] APPLY1 = {GraphBuilderContext.class, ValueNode.class};
        static final Class<?>[] APPLY2 = {GraphBuilderContext.class, ValueNode.class, ValueNode.class};
        static final Class<?>[] APPLY3 = {GraphBuilderContext.class, ValueNode.class, ValueNode.class, ValueNode.class};
        static final Class<?>[][] SIGS = {APPLY0, APPLY1, APPLY2, APPLY3};

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
}
