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
package org.graalvm.compiler.nodes.graphbuilderconf;

import static java.lang.String.format;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitutionRegistry;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.util.EconomicMap;
import org.graalvm.util.Equivalence;
import org.graalvm.util.MapCursor;
import org.graalvm.util.UnmodifiableMapCursor;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

/**
 * Manages a set of {@link InvocationPlugin}s.
 */
public class InvocationPlugins {

    public static class InvocationPluginReceiver implements InvocationPlugin.Receiver {
        private final GraphBuilderContext parser;
        private ValueNode[] args;
        private ValueNode value;

        public InvocationPluginReceiver(GraphBuilderContext parser) {
            this.parser = parser;
        }

        @Override
        public ValueNode get(boolean performNullCheck) {
            assert args != null : "Cannot get the receiver of a static method";
            if (!performNullCheck) {
                return args[0];
            }
            if (value == null) {
                value = parser.nullCheckedValue(args[0]);
                if (value != args[0]) {
                    args[0] = value;
                }
            }
            return value;
        }

        @Override
        public boolean isConstant() {
            return args[0].isConstant();
        }

        public InvocationPluginReceiver init(ResolvedJavaMethod targetMethod, ValueNode[] newArgs) {
            if (!targetMethod.isStatic()) {
                this.args = newArgs;
                this.value = null;
                return this;
            }
            return null;
        }
    }

    /**
     * A symbol that is lazily {@linkplain OptionalLazySymbol#resolve() resolved} to a {@link Type}.
     */
    static class OptionalLazySymbol implements Type {
        private static final Class<?> MASK_NULL = OptionalLazySymbol.class;
        private final String name;
        private Class<?> resolved;

        OptionalLazySymbol(String name) {
            this.name = name;
        }

        @Override
        public String getTypeName() {
            return name;
        }

        /**
         * Gets the resolved {@link Class} corresponding to this symbol or {@code null} if
         * resolution fails.
         */
        public Class<?> resolve() {
            if (resolved == null) {
                Class<?> resolvedOrNull = resolveClass(name, true);
                resolved = resolvedOrNull == null ? MASK_NULL : resolvedOrNull;
            }
            return resolved == MASK_NULL ? null : resolved;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Utility for {@linkplain InvocationPlugins#register(InvocationPlugin, Class, String, Class...)
     * registration} of invocation plugins.
     */
    public static class Registration implements MethodSubstitutionRegistry {

        private final InvocationPlugins plugins;
        private final Type declaringType;
        private final BytecodeProvider methodSubstitutionBytecodeProvider;
        private boolean allowOverwrite;

        @Override
        public Class<?> getReceiverType() {
            return Receiver.class;
        }

        /**
         * Creates an object for registering {@link InvocationPlugin}s for methods declared by a
         * given class.
         *
         * @param plugins where to register the plugins
         * @param declaringType the class declaring the methods for which plugins will be registered
         *            via this object
         */
        public Registration(InvocationPlugins plugins, Type declaringType) {
            this.plugins = plugins;
            this.declaringType = declaringType;
            this.methodSubstitutionBytecodeProvider = null;
        }

        /**
         * Creates an object for registering {@link InvocationPlugin}s for methods declared by a
         * given class.
         *
         * @param plugins where to register the plugins
         * @param declaringType the class declaring the methods for which plugins will be registered
         *            via this object
         * @param methodSubstitutionBytecodeProvider provider used to get the bytecodes to parse for
         *            method substitutions
         */
        public Registration(InvocationPlugins plugins, Type declaringType, BytecodeProvider methodSubstitutionBytecodeProvider) {
            this.plugins = plugins;
            this.declaringType = declaringType;
            this.methodSubstitutionBytecodeProvider = methodSubstitutionBytecodeProvider;
        }

        /**
         * Creates an object for registering {@link InvocationPlugin}s for methods declared by a
         * given class.
         *
         * @param plugins where to register the plugins
         * @param declaringClassName the name of the class class declaring the methods for which
         *            plugins will be registered via this object
         * @param methodSubstitutionBytecodeProvider provider used to get the bytecodes to parse for
         *            method substitutions
         */
        public Registration(InvocationPlugins plugins, String declaringClassName, BytecodeProvider methodSubstitutionBytecodeProvider) {
            this.plugins = plugins;
            this.declaringType = new OptionalLazySymbol(declaringClassName);
            this.methodSubstitutionBytecodeProvider = methodSubstitutionBytecodeProvider;
        }

        /**
         * Configures this registration to allow or disallow overwriting of invocation plugins.
         */
        public Registration setAllowOverwrite(boolean allowOverwrite) {
            this.allowOverwrite = allowOverwrite;
            return this;
        }

        /**
         * Registers a plugin for a method with no arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register0(String name, InvocationPlugin plugin) {
            plugins.register(plugin, false, allowOverwrite, declaringType, name);
        }

        /**
         * Registers a plugin for a method with 1 argument.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register1(String name, Type arg, InvocationPlugin plugin) {
            plugins.register(plugin, false, allowOverwrite, declaringType, name, arg);
        }

        /**
         * Registers a plugin for a method with 2 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register2(String name, Type arg1, Type arg2, InvocationPlugin plugin) {
            plugins.register(plugin, false, allowOverwrite, declaringType, name, arg1, arg2);
        }

        /**
         * Registers a plugin for a method with 3 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register3(String name, Type arg1, Type arg2, Type arg3, InvocationPlugin plugin) {
            plugins.register(plugin, false, allowOverwrite, declaringType, name, arg1, arg2, arg3);
        }

        /**
         * Registers a plugin for a method with 4 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register4(String name, Type arg1, Type arg2, Type arg3, Type arg4, InvocationPlugin plugin) {
            plugins.register(plugin, false, allowOverwrite, declaringType, name, arg1, arg2, arg3, arg4);
        }

        /**
         * Registers a plugin for a method with 5 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register5(String name, Type arg1, Type arg2, Type arg3, Type arg4, Type arg5, InvocationPlugin plugin) {
            plugins.register(plugin, false, allowOverwrite, declaringType, name, arg1, arg2, arg3, arg4, arg5);
        }

        /**
         * Registers a plugin for a method with 6 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register6(String name, Type arg1, Type arg2, Type arg3, Type arg4, Type arg5, Type arg6, InvocationPlugin plugin) {
            plugins.register(plugin, false, allowOverwrite, declaringType, name, arg1, arg2, arg3, arg4, arg5, arg6);
        }

        /**
         * Registers a plugin for a method with 7 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register7(String name, Type arg1, Type arg2, Type arg3, Type arg4, Type arg5, Type arg6, Type arg7, InvocationPlugin plugin) {
            plugins.register(plugin, false, allowOverwrite, declaringType, name, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
        }

        /**
         * Registers a plugin for an optional method with no arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void registerOptional0(String name, InvocationPlugin plugin) {
            plugins.register(plugin, true, allowOverwrite, declaringType, name);
        }

        /**
         * Registers a plugin for an optional method with 1 argument.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void registerOptional1(String name, Type arg, InvocationPlugin plugin) {
            plugins.register(plugin, true, allowOverwrite, declaringType, name, arg);
        }

        /**
         * Registers a plugin for an optional method with 2 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void registerOptional2(String name, Type arg1, Type arg2, InvocationPlugin plugin) {
            plugins.register(plugin, true, allowOverwrite, declaringType, name, arg1, arg2);
        }

        /**
         * Registers a plugin for an optional method with 3 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void registerOptional3(String name, Type arg1, Type arg2, Type arg3, InvocationPlugin plugin) {
            plugins.register(plugin, true, allowOverwrite, declaringType, name, arg1, arg2, arg3);
        }

        /**
         * Registers a plugin for an optional method with 4 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void registerOptional4(String name, Type arg1, Type arg2, Type arg3, Type arg4, InvocationPlugin plugin) {
            plugins.register(plugin, true, allowOverwrite, declaringType, name, arg1, arg2, arg3, arg4);
        }

        /**
         * Registers a plugin that implements a method based on the bytecode of a substitute method.
         *
         * @param substituteDeclaringClass the class declaring the substitute method
         * @param name the name of both the original and substitute method
         * @param argumentTypes the argument types of the method. Element 0 of this array must be
         *            the {@link Class} value for {@link InvocationPlugin.Receiver} iff the method
         *            is non-static. Upon returning, element 0 will have been rewritten to
         *            {@code declaringClass}
         */
        @Override
        public void registerMethodSubstitution(Class<?> substituteDeclaringClass, String name, Type... argumentTypes) {
            registerMethodSubstitution(substituteDeclaringClass, name, name, argumentTypes);
        }

        /**
         * Registers a plugin that implements a method based on the bytecode of a substitute method.
         *
         * @param substituteDeclaringClass the class declaring the substitute method
         * @param name the name of both the original method
         * @param substituteName the name of the substitute method
         * @param argumentTypes the argument types of the method. Element 0 of this array must be
         *            the {@link Class} value for {@link InvocationPlugin.Receiver} iff the method
         *            is non-static. Upon returning, element 0 will have been rewritten to
         *            {@code declaringClass}
         */
        @Override
        public void registerMethodSubstitution(Class<?> substituteDeclaringClass, String name, String substituteName, Type... argumentTypes) {
            MethodSubstitutionPlugin plugin = createMethodSubstitution(substituteDeclaringClass, substituteName, argumentTypes);
            plugins.register(plugin, false, allowOverwrite, declaringType, name, argumentTypes);
        }

        public MethodSubstitutionPlugin createMethodSubstitution(Class<?> substituteDeclaringClass, String substituteName, Type... argumentTypes) {
            assert methodSubstitutionBytecodeProvider != null : "Registration used for method substitutions requires a non-null methodSubstitutionBytecodeProvider";
            MethodSubstitutionPlugin plugin = new MethodSubstitutionPlugin(methodSubstitutionBytecodeProvider, substituteDeclaringClass, substituteName, argumentTypes);
            return plugin;
        }

    }

    boolean checkResolvable(boolean isOptional, Type declaringType, boolean isStatic, String name, Type... argumentTypes) {
        Class<?> declaringClass = resolveType(declaringType, isOptional);
        if (declaringClass == null) {
            return true;
        }
        try {
            Executable res;
            Class<?>[] parameterTypes = resolveTypes(argumentTypes, isStatic ? 0 : 1, argumentTypes.length);
            if (name.equals("<init>")) {
                res = declaringClass.getDeclaredConstructor(parameterTypes);
            } else {
                res = declaringClass.getDeclaredMethod(name, parameterTypes);
            }
            assert Modifier.isStatic(res.getModifiers()) == isStatic : res;
        } catch (NoSuchMethodException | SecurityException e) {
            if (isOptional) {
                return true;
            }
            throw new AssertionError(e);
        }
        return true;
    }

    /**
     * Associates an {@link InvocationPlugin} with the details of a method it substitutes.
     */
    public static class Binding {
        /**
         * The plugin this binding is for.
         */
        public final InvocationPlugin plugin;

        /**
         * Specifies if the associated method is static.
         */
        public final boolean isStatic;

        /**
         * The name of the associated method.
         */
        public final String name;

        /**
         * A partial
         * <a href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.3">method
         * descriptor</a> for the associated method. The descriptor includes enclosing {@code '('}
         * and {@code ')'} characters but omits the return type suffix.
         */
        public final String argumentsDescriptor;

        /**
         * Link in a list of bindings.
         */
        private Binding next;

        Binding(InvocationPlugin data, boolean isStatic, String name, Type... argumentTypes) {
            this.plugin = data;
            this.isStatic = isStatic;
            this.name = name;
            StringBuilder buf = new StringBuilder();
            buf.append('(');
            for (int i = isStatic ? 0 : 1; i < argumentTypes.length; i++) {
                buf.append(MetaUtil.toInternalName(argumentTypes[i].getTypeName()));
            }
            buf.append(')');
            this.argumentsDescriptor = buf.toString();
        }

        Binding(ResolvedJavaMethod resolved, InvocationPlugin data) {
            this.plugin = data;
            this.isStatic = resolved.isStatic();
            this.name = resolved.getName();
            Signature sig = resolved.getSignature();
            String desc = sig.toMethodDescriptor();
            assert desc.indexOf(')') != -1 : desc;
            this.argumentsDescriptor = desc.substring(0, desc.indexOf(')') + 1);
        }

        @Override
        public String toString() {
            return name + argumentsDescriptor;
        }

        /**
         * Gets next binding in a list of bindings headed by this object.
         */
        public Binding getNext() {
            return next;
        }
    }

    private final MetaAccessProvider metaAccess;

    /**
     * Map from class names in {@linkplain MetaUtil#toInternalName(String) internal} form to the
     * invocation plugin bindings for the class.
     */
    private final EconomicMap<String, ClassPlugins> registrations = EconomicMap.create(Equivalence.DEFAULT);

    /**
     * Deferred registrations as well as guard for ensuring no registrations are made after the
     * first read access to the plugins. The guard uses double-checked locking which is why this
     * field is {@code volatile}.
     */
    private volatile List<Runnable> deferredRegistrations = new ArrayList<>();

    /**
     * Adds a {@link Runnable} for doing registration deferred until the first time
     * {@link #get(ResolvedJavaMethod)} or {@link #closeRegistration()} is called on this object.
     */
    public void defer(Runnable deferrable) {
        assert deferredRegistrations != null : "registration is closed";
        deferredRegistrations.add(deferrable);
    }

    /**
     * Per-class bindings.
     */
    protected static class ClassPlugins {

        /**
         * Maps method names to binding lists.
         */
        private final EconomicMap<String, Binding> bindings = EconomicMap.create(Equivalence.DEFAULT);

        /**
         * Gets the invocation plugin for a given method.
         *
         * @return the invocation plugin for {@code method} or {@code null}
         */
        public InvocationPlugin get(ResolvedJavaMethod method) {
            Binding binding = bindings.get(method.getName());
            while (binding != null) {
                if (method.isStatic() == binding.isStatic && method.getSignature().toMethodDescriptor().startsWith(binding.argumentsDescriptor)) {
                    return binding.plugin;
                }
                binding = binding.next;
            }
            return null;
        }

        public void register(Binding binding, boolean allowOverwrite) {
            if (allowOverwrite) {
                if (lookup(binding) != null) {
                    register(binding);
                    return;
                }
            } else {
                assert lookup(binding) == null : "a value is already registered for " + binding;
            }
            register(binding);
        }

        InvocationPlugin lookup(Binding binding) {
            Binding b = bindings.get(binding.name);
            while (b != null) {
                if (b.isStatic == binding.isStatic && b.argumentsDescriptor.equals(binding.argumentsDescriptor)) {
                    return b.plugin;
                }
                b = b.next;
            }
            return null;
        }

        /**
         * Registers {@code binding}.
         */
        void register(Binding binding) {
            Binding head = bindings.get(binding.name);
            assert binding.next == null;
            binding.next = head;
            bindings.put(binding.name, binding);
        }
    }

    /**
     * Registers a binding of a method to an invocation plugin.
     *
     * @param plugin invocation plugin to be associated with the specified method
     * @param isStatic specifies if the method is static
     * @param declaringClass the class declaring the method
     * @param name the name of the method
     * @param argumentTypes the argument types of the method. Element 0 of this array must be
     *            {@code declaringClass} iff the method is non-static.
     * @return an object representing the method
     */
    Binding put(InvocationPlugin plugin, boolean isStatic, boolean allowOverwrite, Type declaringClass, String name, Type... argumentTypes) {
        assert deferredRegistrations != null : "registration is closed";
        assert isStatic || argumentTypes[0] == declaringClass;

        String internalName = MetaUtil.toInternalName(declaringClass.getTypeName());
        ClassPlugins classPlugins = registrations.get(internalName);
        if (classPlugins == null) {
            classPlugins = new ClassPlugins();
            registrations.put(internalName, classPlugins);
        }
        assert isStatic || argumentTypes[0] == declaringClass;
        Binding binding = new Binding(plugin, isStatic, name, argumentTypes);
        classPlugins.register(binding, allowOverwrite);
        return binding;
    }

    /**
     * Determines if a method denoted by a given {@link Binding} is in this map.
     */
    boolean containsKey(Type declaringType, Binding key) {
        String internalName = MetaUtil.toInternalName(declaringType.getTypeName());
        ClassPlugins classPlugins = registrations.get(internalName);
        return classPlugins != null && classPlugins.lookup(key) != null;
    }

    InvocationPlugin get(ResolvedJavaMethod method) {
        flushDeferrables();
        String internalName = method.getDeclaringClass().getName();
        ClassPlugins classPlugins = registrations.get(internalName);
        if (classPlugins != null) {
            return classPlugins.get(method);
        }
        return null;
    }

    private void flushDeferrables() {
        if (deferredRegistrations != null) {
            synchronized (this) {
                if (deferredRegistrations != null) {
                    for (Runnable deferrable : deferredRegistrations) {
                        deferrable.run();
                    }
                    deferredRegistrations = null;
                }
            }
        }
    }

    /**
     * Disallows new registrations of new plugins, and creates the internal tables for method
     * lookup.
     */
    public void closeRegistration() {
        flushDeferrables();
    }

    public int size() {
        return registrations.size();
    }

    /**
     * The plugins {@linkplain #lookupInvocation(ResolvedJavaMethod) searched} before searching in
     * this object.
     */
    protected final InvocationPlugins parent;

    private InvocationPlugins(InvocationPlugins parent, MetaAccessProvider metaAccess) {
        this.metaAccess = metaAccess;
        InvocationPlugins p = parent;
        this.parent = p;
    }

    /**
     * Creates a set of invocation plugins with a non-null {@linkplain #getParent() parent}.
     */
    public InvocationPlugins(InvocationPlugins parent) {
        this(parent, parent.getMetaAccess());
    }

    public InvocationPlugins(Map<ResolvedJavaMethod, InvocationPlugin> plugins, InvocationPlugins parent, MetaAccessProvider metaAccess) {
        this.metaAccess = metaAccess;
        this.parent = parent;

        this.deferredRegistrations = null;

        for (Map.Entry<ResolvedJavaMethod, InvocationPlugin> entry : plugins.entrySet()) {
            ResolvedJavaMethod method = entry.getKey();
            InvocationPlugin plugin = entry.getValue();

            String internalName = method.getDeclaringClass().getName();
            ClassPlugins classPlugins = registrations.get(internalName);
            if (classPlugins == null) {
                classPlugins = new ClassPlugins();
                registrations.put(internalName, classPlugins);
            }
            classPlugins.register(new Binding(method, plugin));
        }
    }

    public MetaAccessProvider getMetaAccess() {
        return metaAccess;
    }

    public InvocationPlugins(MetaAccessProvider metaAccess) {
        this(null, metaAccess);
    }

    protected void register(InvocationPlugin plugin, boolean isOptional, boolean allowOverwrite, Type declaringClass, String name, Type... argumentTypes) {
        boolean isStatic = argumentTypes.length == 0 || argumentTypes[0] != InvocationPlugin.Receiver.class;
        if (!isStatic) {
            argumentTypes[0] = declaringClass;
        }
        Binding binding = put(plugin, isStatic, allowOverwrite, declaringClass, name, argumentTypes);
        assert Checker.check(this, declaringClass, binding);
        assert checkResolvable(isOptional, declaringClass, isStatic, name, argumentTypes);
    }

    /**
     * Registers an invocation plugin for a given method. There must be no plugin currently
     * registered for {@code method}.
     *
     * @param argumentTypes the argument types of the method. Element 0 of this array must be the
     *            {@link Class} value for {@link InvocationPlugin.Receiver} iff the method is
     *            non-static. Upon returning, element 0 will have been rewritten to
     *            {@code declaringClass}
     */
    public void register(InvocationPlugin plugin, Type declaringClass, String name, Type... argumentTypes) {
        register(plugin, false, false, declaringClass, name, argumentTypes);
    }

    public void register(InvocationPlugin plugin, String declaringClass, String name, Type... argumentTypes) {
        register(plugin, false, false, new OptionalLazySymbol(declaringClass), name, argumentTypes);
    }

    /**
     * Registers an invocation plugin for a given, optional method. There must be no plugin
     * currently registered for {@code method}.
     *
     * @param argumentTypes the argument types of the method. Element 0 of this array must be the
     *            {@link Class} value for {@link InvocationPlugin.Receiver} iff the method is
     *            non-static. Upon returning, element 0 will have been rewritten to
     *            {@code declaringClass}
     */
    public void registerOptional(InvocationPlugin plugin, Type declaringClass, String name, Type... argumentTypes) {
        register(plugin, true, false, declaringClass, name, argumentTypes);
    }

    /**
     * Gets the plugin for a given method.
     *
     * @param method the method to lookup
     * @return the plugin associated with {@code method} or {@code null} if none exists
     */
    public InvocationPlugin lookupInvocation(ResolvedJavaMethod method) {
        if (parent != null) {
            InvocationPlugin plugin = parent.lookupInvocation(method);
            if (plugin != null) {
                return plugin;
            }
        }
        return get(method);
    }

    /**
     * Gets the set of registered invocation plugins.
     *
     * @return a map from class names in {@linkplain MetaUtil#toInternalName(String) internal} form
     *         to the invocation plugin bindings for methods in the class
     */
    public EconomicMap<String, List<Binding>> getBindings() {
        EconomicMap<String, List<Binding>> res = EconomicMap.create(Equivalence.DEFAULT);
        if (parent != null) {
            res.putAll(parent.getBindings());
        }
        flushDeferrables();
        MapCursor<String, ClassPlugins> classes = registrations.getEntries();
        while (classes.advance()) {
            String type = classes.getKey();
            ClassPlugins cp = classes.getValue();
            MapCursor<String, Binding> methods = cp.bindings.getEntries();
            while (methods.advance()) {
                List<Binding> bindings = res.get(type);
                if (bindings == null) {
                    bindings = new ArrayList<>();
                    res.put(type, bindings);
                }
                for (Binding b = methods.getValue(); b != null; b = b.next) {
                    bindings.add(b);
                }
            }
        }
        return res;
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
        StringBuilder buf = new StringBuilder();
        UnmodifiableMapCursor<String, ClassPlugins> entries = registrations.getEntries();
        while (entries.advance()) {
            buf.append(entries.getKey()).append('.').append(entries.getValue()).append(", ");
        }

        String s = buf.toString();
        if (buf.length() != 0) {
            s = s.substring(buf.length() - ", ".length());
        }
        return s + " / parent: " + this.parent;
    }

    private static class Checker {
        private static final int MAX_ARITY = 7;
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
                    assert sig[2] == InvocationPlugin.Receiver.class;
                    assert Arrays.asList(sig).subList(3, sig.length).stream().allMatch(c -> c == ValueNode.class);
                    while (sigs.size() < sig.length - 2) {
                        sigs.add(null);
                    }
                    sigs.set(sig.length - 3, sig);
                }
            }
            assert sigs.indexOf(null) == -1 : format("need to add an apply() method to %s that takes %d %s arguments ", InvocationPlugin.class.getName(), sigs.indexOf(null),
                            ValueNode.class.getSimpleName());
            SIGS = sigs.toArray(new Class<?>[sigs.size()][]);
        }

        public static boolean check(InvocationPlugins plugins, Type declaringType, Binding binding) {
            InvocationPlugin plugin = binding.plugin;
            InvocationPlugins p = plugins.parent;
            while (p != null) {
                assert !p.containsKey(declaringType, binding) : "a plugin is already registered for " + binding;
                p = p.parent;
            }
            if (plugin instanceof ForeignCallPlugin || plugin instanceof GeneratedInvocationPlugin) {
                return true;
            }
            if (plugin instanceof MethodSubstitutionPlugin) {
                MethodSubstitutionPlugin msplugin = (MethodSubstitutionPlugin) plugin;
                Method substitute = msplugin.getJavaSubstitute();
                assert substitute.getAnnotation(MethodSubstitution.class) != null : format("Substitute method must be annotated with @%s: %s", MethodSubstitution.class.getSimpleName(), substitute);
                return true;
            }
            int arguments = plugins.metaAccess.parseMethodDescriptor(binding.argumentsDescriptor + "V").getParameterCount(false);
            assert arguments < SIGS.length : format("need to extend %s to support method with %d arguments: %s", InvocationPlugin.class.getSimpleName(), arguments, binding);
            for (Method m : plugin.getClass().getDeclaredMethods()) {
                if (m.getName().equals("apply")) {
                    Class<?>[] parameterTypes = m.getParameterTypes();
                    if (Arrays.equals(SIGS[arguments], parameterTypes)) {
                        return true;
                    }
                }
            }
            throw new AssertionError(format("graph builder plugin for %s not found", binding));
        }
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

    /**
     * Resolves a name to a class.
     *
     * @param className the name of the class to resolve
     * @param optional if true, resolution failure returns null
     * @return the resolved class or null if resolution fails and {@code optional} is true
     */
    public static Class<?> resolveClass(String className, boolean optional) {
        try {
            // Need to use the system class loader to handle classes
            // loaded by the application class loader which is not
            // delegated to by the JVMCI class loader.
            ClassLoader cl = ClassLoader.getSystemClassLoader();
            return Class.forName(className, false, cl);
        } catch (ClassNotFoundException e) {
            if (optional) {
                return null;
            }
            throw new GraalError("Could not resolve type " + className);
        }
    }

    /**
     * Resolves a {@link Type} to a {@link Class}.
     *
     * @param type the type to resolve
     * @param optional if true, resolution failure returns null
     * @return the resolved class or null if resolution fails and {@code optional} is true
     */
    public static Class<?> resolveType(Type type, boolean optional) {
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (type instanceof OptionalLazySymbol) {
            return ((OptionalLazySymbol) type).resolve();
        }
        return resolveClass(type.getTypeName(), optional);
    }

    private static final Class<?>[] NO_CLASSES = {};

    /**
     * Resolves an array of {@link Type}s to an array of {@link Class}es.
     *
     * @param types the types to resolve
     * @param from the initial index of the range to be resolved, inclusive
     * @param to the final index of the range to be resolved, exclusive
     * @return the resolved class or null if resolution fails and {@code optional} is true
     */
    public static Class<?>[] resolveTypes(Type[] types, int from, int to) {
        int length = to - from;
        if (length <= 0) {
            return NO_CLASSES;
        }
        Class<?>[] classes = new Class<?>[length];
        for (int i = 0; i < length; i++) {
            classes[i] = resolveType(types[i + from], false);
        }
        return classes;
    }
}
