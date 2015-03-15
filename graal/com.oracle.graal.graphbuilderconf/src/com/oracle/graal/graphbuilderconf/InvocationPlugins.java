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

import static java.lang.String.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderPlugin.*;
import com.oracle.graal.nodes.*;

/**
 * Manages a set of {@link InvocationPlugin}s.
 */
public class InvocationPlugins {

    /**
     * Sentinel class for use with
     * {@link InvocationPlugins#register(InvocationPlugin, Class, String, Class...)} to denote the
     * receiver argument for a non-static method.
     */
    public static final class Receiver {
        private Receiver() {
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    /**
     * Utility for
     * {@linkplain InvocationPlugins#register(InvocationPlugin, Class, String, Class...)
     * registration} of invocation plugins.
     */
    public static class Registration {

        private final InvocationPlugins plugins;
        private final Class<?> declaringClass;

        /**
         * Creates an object for registering {@link InvocationPlugin}s for methods declared by a
         * given class.
         *
         * @param plugins where to register the plugins
         * @param declaringClass the class declaring the methods for which plugins will be
         *            registered via this object
         */
        public Registration(InvocationPlugins plugins, Class<?> declaringClass) {
            this.plugins = plugins;
            this.declaringClass = declaringClass;
        }

        /**
         * Registers a plugin for a method with no arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register0(String name, InvocationPlugin plugin) {
            plugins.register(plugin, declaringClass, name);
        }

        /**
         * Registers a plugin for a method with 1 argument.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register1(String name, Class<?> arg, InvocationPlugin plugin) {
            plugins.register(plugin, declaringClass, name, arg);
        }

        /**
         * Registers a plugin for a method with 2 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register2(String name, Class<?> arg1, Class<?> arg2, InvocationPlugin plugin) {
            plugins.register(plugin, declaringClass, name, arg1, arg2);
        }

        /**
         * Registers a plugin for a method with 3 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register3(String name, Class<?> arg1, Class<?> arg2, Class<?> arg3, InvocationPlugin plugin) {
            plugins.register(plugin, declaringClass, name, arg1, arg2, arg3);
        }

        /**
         * Registers a plugin for a method with 4 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register4(String name, Class<?> arg1, Class<?> arg2, Class<?> arg3, Class<?> arg4, InvocationPlugin plugin) {
            plugins.register(plugin, declaringClass, name, arg1, arg2, arg3, arg4);
        }

        /**
         * Registers a plugin for a method with 5 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register5(String name, Class<?> arg1, Class<?> arg2, Class<?> arg3, Class<?> arg4, Class<?> arg5, InvocationPlugin plugin) {
            plugins.register(plugin, declaringClass, name, arg1, arg2, arg3, arg4, arg5);
        }
    }

    public static final class MethodInfo {
        public final boolean isStatic;
        public final Class<?> declaringClass;
        public final String name;
        public final Class<?>[] argumentTypes;

        public MethodInfo(Class<?> declaringClass, String name, Class<?>... argumentTypes) {
            this.isStatic = argumentTypes.length == 0 || argumentTypes[0] != Receiver.class;
            this.declaringClass = declaringClass;
            this.name = name;
            this.argumentTypes = argumentTypes;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof MethodInfo) {
                MethodInfo that = (MethodInfo) obj;
                boolean res = this.declaringClass == that.declaringClass && this.name.equals(that.name) && Arrays.equals(this.argumentTypes, that.argumentTypes);
                assert !res || this.isStatic == that.isStatic;
                return res;
            }
            return false;
        }

        @Override
        public int hashCode() {
            // Replay compilation mandates use of stable hash codes
            return declaringClass.getName().hashCode() ^ name.hashCode();
        }

        public ResolvedJavaMethod resolve(MetaAccessProvider metaAccess) {
            try {
                Class<?>[] parameterTypes = isStatic ? argumentTypes : Arrays.copyOfRange(argumentTypes, 1, argumentTypes.length);
                ResolvedJavaMethod resolved;
                if (name.equals("<init>")) {
                    resolved = metaAccess.lookupJavaMethod(declaringClass.getDeclaredConstructor(parameterTypes));
                } else {
                    resolved = metaAccess.lookupJavaMethod(declaringClass.getDeclaredMethod(name, parameterTypes));
                }
                assert resolved.isStatic() == isStatic;
                return resolved;
            } catch (NoSuchMethodException | SecurityException e) {
                throw new GraalInternalError(e);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(declaringClass.getName()).append('.').append(name).append('(');
            for (Class<?> p : argumentTypes) {
                if (sb.charAt(sb.length() - 1) != '(') {
                    sb.append(", ");
                }
                sb.append(p.getSimpleName());
            }
            return sb.append(')').toString();
        }
    }

    protected final MetaAccessProvider metaAccess;

    private final Map<MethodInfo, InvocationPlugin> registrations;

    private final Thread registrationThread;

    /**
     * Null while registration is open, non-null when registration is closed.
     */
    private volatile Map<ResolvedJavaMethod, InvocationPlugin> plugins;

    /**
     * The invocation plugins deferred to if a plugin is not found in this object.
     */
    private InvocationPlugins defaults;

    /**
     * Creates a set of invocation plugins with a given non-null set of plugins as the
     * {@linkplain #getDefaults defaults}.
     */
    public InvocationPlugins(InvocationPlugins defaults) {
        this.registrationThread = Thread.currentThread();
        this.metaAccess = defaults.getMetaAccess();
        this.registrations = new HashMap<>();
        InvocationPlugins defs = defaults;
        // Only adopt non-empty defaults
        while (defs != null && defs.size() == 0) {
            defs = defs.defaults;
        }
        this.defaults = defs;
    }

    public InvocationPlugins(MetaAccessProvider metaAccess) {
        this(metaAccess, 16);
    }

    public InvocationPlugins(MetaAccessProvider metaAccess, int estimatePluginCount) {
        this.metaAccess = metaAccess;
        this.registrations = new HashMap<>(estimatePluginCount);
        this.registrationThread = Thread.currentThread();
    }

    /**
     * Registers an invocation plugin for a given method. There must be no plugin currently
     * registered for {@code method}.
     */
    public void register(InvocationPlugin plugin, Class<?> declaringClass, String name, Class<?>... argumentTypes) {
        assert Thread.currentThread() == registrationThread : "invocation plugin registration must be single threaded";
        MethodInfo method = new MethodInfo(declaringClass, name, argumentTypes);
        assert Checker.check(method, plugin);
        assert plugins == null : "invocation plugin registration is closed";
        GraphBuilderPlugin oldValue = registrations.put(method, plugin);
        assert oldValue == null : "a plugin is already registered for " + method;
    }

    /**
     * Gets the plugin for a given method.
     *
     * @param method the method to lookup
     * @return the plugin associated with {@code method} or {@code null} if none exists
     */
    public InvocationPlugin lookupInvocation(ResolvedJavaMethod method) {
        InvocationPlugin res = null;
        if (plugins == null) {
            synchronized (this) {
                if (plugins == null) {
                    if (registrations.isEmpty()) {
                        plugins = Collections.emptyMap();
                    } else {
                        // System.out.println("resolving " + registrations.size() + " plugins");
                        plugins = new HashMap<>(registrations.size());
                        for (Map.Entry<MethodInfo, InvocationPlugin> e : registrations.entrySet()) {
                            plugins.put(e.getKey().resolve(metaAccess), e.getValue());
                        }
                    }
                }
            }
        }
        res = plugins.get(method);
        if (res == null && defaults != null) {
            return defaults.lookupInvocation(method);
        }
        return res;
    }

    /**
     * Gets the invocation plugins {@linkplain #lookupInvocation(ResolvedJavaMethod) searched} if a
     * plugin is not found in this object.
     */
    public InvocationPlugins getDefaults() {
        return defaults;
    }

    @Override
    public String toString() {
        return registrations.keySet().stream().map(MethodInfo::toString).collect(Collectors.joining(", ")) + " / defaults: " + this.defaults;
    }

    private static class Checker {
        private static final int MAX_ARITY = 5;
        /**
         * The set of all {@link InvocationPlugin#apply} method signatures.
         */
        static final Class<?>[][] SIGS;
        static {
            ArrayList<Class<?>[]> sigs = new ArrayList<>(MAX_ARITY);
            for (Method method : InvocationPlugin.class.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) && method.getName().equals("apply")) {
                    Class<?>[] sig = method.getParameterTypes();
                    assert sig[0] == GraphBuilderContext.class;
                    assert sig[1] == ResolvedJavaMethod.class;
                    assert Arrays.asList(Arrays.copyOfRange(sig, 2, sig.length)).stream().allMatch(c -> c == ValueNode.class);
                    while (sigs.size() < sig.length - 1) {
                        sigs.add(null);
                    }
                    sigs.set(sig.length - 2, sig);
                }
            }
            assert sigs.indexOf(null) == -1 : format("need to add an apply() method to %s that takes %d %s arguments ", InvocationPlugin.class.getName(), sigs.indexOf(null),
                            ValueNode.class.getSimpleName());
            SIGS = sigs.toArray(new Class<?>[sigs.size()][]);
        }

        public static boolean check(MethodInfo method, InvocationPlugin plugin) {
            int arguments = method.argumentTypes.length;
            assert arguments < SIGS.length : format("need to extend %s to support method with %d arguments: %s", InvocationPlugin.class.getSimpleName(), arguments, method);
            for (Method m : plugin.getClass().getDeclaredMethods()) {
                if (m.getName().equals("apply")) {
                    Class<?>[] parameterTypes = m.getParameterTypes();
                    if (Arrays.equals(SIGS[arguments], parameterTypes)) {
                        return true;
                    }
                }
            }
            throw new AssertionError(format("graph builder plugin for %s not found", method));
        }
    }

    public MetaAccessProvider getMetaAccess() {
        return metaAccess;
    }

    public int size() {
        return registrations.size();
    }
}
