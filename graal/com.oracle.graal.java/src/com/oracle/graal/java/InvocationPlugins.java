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
import java.util.stream.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.java.GraphBuilderPlugin.InvocationPlugin;
import com.oracle.graal.nodes.*;

/**
 * Manages a set of {@link InvocationPlugin}s.
 */
public class InvocationPlugins {
    /**
     * Utility for {@linkplain InvocationPlugins#register(ResolvedJavaMethod, InvocationPlugin)
     * registration} of invocation plugins.
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

        private final InvocationPlugins plugins;
        private final MetaAccessProvider metaAccess;
        private final Class<?> declaringClass;

        /**
         * Creates an object for registering {@link InvocationPlugin}s for methods declared by a
         * given class.
         *
         * @param plugins where to register the plugins
         * @param metaAccess used to resolve classes and methods
         * @param declaringClass the class declaring the methods for which plugins will be
         *            registered via this object
         */
        public Registration(InvocationPlugins plugins, MetaAccessProvider metaAccess, Class<?> declaringClass) {
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

    private final Map<ResolvedJavaMethod, InvocationPlugin> plugins = new HashMap<>();

    /**
     * The invocation plugins deferred to if a plugin is not found in this object.
     */
    private InvocationPlugins defaults;

    /**
     * Registers an invocation plugin for a given method. There must be no plugin currently
     * registered for {@code method}.
     */
    public void register(ResolvedJavaMethod method, InvocationPlugin plugin) {
        assert Checker.check(method, plugin);
        GraphBuilderPlugin oldValue = plugins.put(method, plugin);
        // System.out.println("registered: " + plugin);
        assert oldValue == null;
    }

    /**
     * Gets the plugin for a given method.
     *
     * @param method the method to lookup
     * @return the plugin associated with {@code method} or {@code null} if none exists
     */
    public InvocationPlugin lookupInvocation(ResolvedJavaMethod method) {
        InvocationPlugin res = plugins.get(method);
        if (res == null && defaults != null) {
            return defaults.lookupInvocation(method);
        }
        return res;
    }

    /**
     * Sets the invocation plugins {@linkplain #lookupInvocation(ResolvedJavaMethod) searched} if a
     * plugin is not found in this object.
     */
    public InvocationPlugins setDefaults(InvocationPlugins defaults) {
        InvocationPlugins old = this.defaults;
        this.defaults = defaults;
        return old;
    }

    /**
     * Adds all the plugins from {@code other} to this object.
     */
    public void updateFrom(InvocationPlugins other) {
        this.plugins.putAll(other.plugins);
    }

    @Override
    public String toString() {
        return plugins.keySet().stream().map(m -> m.format("%H.%n(%p)")).collect(Collectors.joining(", "));
    }

    private static class Checker {
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
}
