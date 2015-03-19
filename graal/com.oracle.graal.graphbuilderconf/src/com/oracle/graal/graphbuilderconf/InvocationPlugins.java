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
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.iterators.*;
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

    static final class MethodInfo {
        final boolean isStatic;
        final Class<?> declaringClass;
        final String name;
        final Class<?>[] argumentTypes;
        final InvocationPlugin plugin;

        int id;

        MethodInfo(InvocationPlugin plugin, Class<?> declaringClass, String name, Class<?>... argumentTypes) {
            this.plugin = plugin;
            this.isStatic = argumentTypes.length == 0 || argumentTypes[0] != Receiver.class;
            this.declaringClass = declaringClass;
            this.name = name;
            this.argumentTypes = argumentTypes;
            if (!isStatic) {
                argumentTypes[0] = declaringClass;
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof MethodInfo) {
                MethodInfo that = (MethodInfo) obj;
                boolean res = this.name.equals(that.name) && this.declaringClass.equals(that.declaringClass) && Arrays.equals(this.argumentTypes, that.argumentTypes);
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

        ResolvedJavaMethod resolve(MetaAccessProvider metaAccess) {
            try {
                ResolvedJavaMethod method;
                Class<?>[] parameterTypes = isStatic ? argumentTypes : Arrays.copyOfRange(argumentTypes, 1, argumentTypes.length);
                if (name.equals("<init>")) {
                    method = metaAccess.lookupJavaMethod(declaringClass.getDeclaredConstructor(parameterTypes));
                } else {
                    method = metaAccess.lookupJavaMethod(declaringClass.getDeclaredMethod(name, parameterTypes));
                }
                assert method.isStatic() == isStatic;
                return method;
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
    private final List<MethodInfo> registrations;
    private final Thread registrationThread;

    /**
     * The minimum {@linkplain InvocationPluginIdHolder#getInvocationPluginId() id} for a method
     * associated with a plugin in {@link #plugins}.
     */
    private int minId = Integer.MAX_VALUE;

    /**
     * Resolved methods to plugins map. The keys (i.e., indexes) are derived from
     * {@link InvocationPluginIdHolder#getInvocationPluginId()}.
     */
    private volatile InvocationPlugin[] plugins;

    /**
     * The plugins {@linkplain #lookupInvocation(ResolvedJavaMethod) searched} before searching in
     * this object.
     */
    private InvocationPlugins parent;

    private InvocationPlugins(InvocationPlugins parent, MetaAccessProvider metaAccess, int estimatePluginCount) {
        this.registrationThread = Thread.currentThread();
        this.metaAccess = metaAccess;
        this.registrations = new ArrayList<>(estimatePluginCount);
        InvocationPlugins p = parent;
        // Only adopt a non-empty parent
        while (p != null && p.size() == 0) {
            p = p.parent;
        }
        this.parent = p;
    }

    private static final int DEFAULT_ESTIMATE_PLUGIN_COUNT = 16;

    /**
     * Creates a set of invocation plugins with a non-null {@linkplain #getParent() parent}.
     */
    public InvocationPlugins(InvocationPlugins parent) {
        this(parent, parent.metaAccess, DEFAULT_ESTIMATE_PLUGIN_COUNT);
    }

    public InvocationPlugins(MetaAccessProvider metaAccess) {
        this(metaAccess, DEFAULT_ESTIMATE_PLUGIN_COUNT);
    }

    public InvocationPlugins(MetaAccessProvider metaAccess, int estimatePluginCount) {
        this(null, metaAccess, estimatePluginCount);
    }

    /**
     * Registers an invocation plugin for a given method. There must be no plugin currently
     * registered for {@code method}.
     */
    public void register(InvocationPlugin plugin, Class<?> declaringClass, String name, Class<?>... argumentTypes) {
        assert Thread.currentThread() == registrationThread : "invocation plugin registration must be single threaded";
        MethodInfo methodInfo = new MethodInfo(plugin, declaringClass, name, argumentTypes);
        assert Checker.check(this, methodInfo, plugin);
        assert plugins == null : "invocation plugin registration is closed";
        registrations.add(methodInfo);
    }

    private static int nextInvocationPluginId = 1;

    /**
     * Gets the plugin for a given method.
     *
     * @param method the method to lookup
     * @return the plugin associated with {@code method} or {@code null} if none exists
     */
    public InvocationPlugin lookupInvocation(ResolvedJavaMethod method) {
        assert method instanceof InvocationPluginIdHolder;
        if (parent != null) {
            InvocationPlugin plugin = parent.lookupInvocation(method);
            if (plugin != null) {
                return plugin;
            }
        }
        InvocationPluginIdHolder pluggable = (InvocationPluginIdHolder) method;
        if (plugins == null) {
            // Must synchronize across all InvocationPlugins objects to ensure thread safe
            // allocation of InvocationPlugin identifiers
            synchronized (InvocationPlugins.class) {
                if (plugins == null) {
                    if (registrations.isEmpty()) {
                        plugins = new InvocationPlugin[0];
                    } else {
                        int max = Integer.MIN_VALUE;
                        for (MethodInfo methodInfo : registrations) {
                            InvocationPluginIdHolder p = (InvocationPluginIdHolder) methodInfo.resolve(metaAccess);
                            int id = p.getInvocationPluginId();
                            if (id == 0) {
                                id = nextInvocationPluginId++;
                                p.setInvocationPluginId(id);
                            }
                            if (id < minId) {
                                minId = id;
                            }
                            if (id > max) {
                                max = id;

                            }
                            methodInfo.id = id;
                        }

                        int length = (max - minId) + 1;
                        plugins = new InvocationPlugin[length];
                        for (MethodInfo m : registrations) {
                            int index = m.id - minId;
                            plugins[index] = m.plugin;
                        }
                    }
                }
            }
        }

        int id = pluggable.getInvocationPluginId();
        int index = id - minId;
        return index >= 0 && index < plugins.length ? plugins[index] : null;
    }

    /**
     * Gets the invocation plugins {@linkplain #lookupInvocation(ResolvedJavaMethod) searched}
     * before searching in this object.
     */
    public InvocationPlugins getParent() {
        return parent;
    }

    @Override
    public String toString() {
        return registrations.stream().map(MethodInfo::toString).collect(Collectors.joining(", ")) + " / parent: " + this.parent;
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

        public static boolean check(InvocationPlugins plugins, MethodInfo method, InvocationPlugin plugin) {
            InvocationPlugins p = plugins;
            while (p != null) {
                assert !p.registrations.contains(method) : "a plugin is already registered for " + method;
                p = p.parent;
            }
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

    /**
     * Checks a set of nodes added to the graph by an {@link InvocationPlugin}.
     *
     * @param b the graph builder that applied the plugin
     * @param plugin a plugin that was just applied
     * @param newNodes the nodes added to the graph by {@code plugin}
     * @throws AssertionError if any check fail
     */
    public void checkNewNodes(GraphBuilderContext b, InvocationPlugin plugin, NodeIterable<Node> newNodes) {
        if (parent != null) {
            parent.checkNewNodes(b, plugin, newNodes);
        }
    }
}
