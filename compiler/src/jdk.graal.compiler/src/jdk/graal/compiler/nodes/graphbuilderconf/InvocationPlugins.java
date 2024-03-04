/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.nodes.graphbuilderconf;

import static java.lang.String.format;
import static jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.LateClassPlugins.CLOSED_LATE_CLASS_PLUGIN;
import static jdk.vm.ci.services.Services.IS_BUILDING_NATIVE_IMAGE;
import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import jdk.vm.ci.meta.JavaType;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.Pair;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.collections.UnmodifiableMapCursor;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.MethodFilter;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.serviceprovider.GlobalAtomicLong;
import jdk.graal.compiler.serviceprovider.IsolateUtil;
import jdk.vm.ci.common.NativeImageReinitialize;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Manages a set of {@link InvocationPlugin}s.
 * <p>
 * Most plugins are registered during initialization (i.e., before {@link #lookupInvocation} or
 * {@link #getInvocationPlugins} is called). These registrations can be made with
 * {@link Registration}, {@link #register(Type, InvocationPlugin)} . Initialization is not
 * thread-safe and so must only be performed by a single thread.
 * <p>
 * Plugins that are not guaranteed to be made during initialization must use
 * {@link LateRegistration}.
 */
public class InvocationPlugins {

    public static class Options {
        // @formatter:off
        @Option(help = "Print the registered intrinsics in a format compatible with DisableIntrinsics.", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintIntrinsics = new OptionKey<>(false);
        @Option(help = "Disable intrinsics matching the given method filter (see MethodFilter " +
                "option for details). For example, 'DisableIntrinsics=String.equals' disables " +
                "intrinsics for any method named 'equals' in a class whose simple name is 'String'. " +
                "You can append ':verbose' at the end of the filter value to print out disabled " +
                "intrinsics as they are encountered during compilation (e.g., 'String.equals:verbose').", type = OptionType.Debug)
        public static final OptionKey<String> DisableIntrinsics = new OptionKey<>(null);
        @Option(help = "Print a warning when a missing intrinsic is seen.", type = OptionType.Debug)
        public static final OptionKey<Boolean> WarnMissingIntrinsic = new OptionKey<>(false);
        // @formatter:on
    }

    public static class InvocationPluginReceiver implements InvocationPlugin.Receiver {
        private final GraphBuilderContext parser;
        private ValueNode[] args;

        public InvocationPluginReceiver(GraphBuilderContext parser) {
            this.parser = parser;
        }

        @Override
        public ValueNode get(boolean performNullCheck) {
            assert args != null : "Cannot get the receiver of a static method";
            if (performNullCheck) {
                args[0] = parser.nullCheckedValue(args[0]);
            }
            return args[0];
        }

        public InvocationPluginReceiver init(ResolvedJavaMethod targetMethod, ValueNode[] newArgs) {
            if (!targetMethod.isStatic()) {
                this.args = newArgs;
                return this;
            }
            this.args = null;
            return null;
        }

        /**
         * Determines if {@link #get(boolean)} was called with {@code true}.
         */
        public boolean nullCheckPerformed() {
            return StampTool.isPointerNonNull(args[0]);
        }
    }

    /**
     * A symbol for an already resolved method.
     */
    public static class ResolvedJavaSymbol implements Type {
        private final ResolvedJavaType resolved;

        public ResolvedJavaSymbol(ResolvedJavaType type) {
            this.resolved = type;
        }

        public ResolvedJavaType getResolved() {
            return resolved;
        }

        @Override
        public String toString() {
            return resolved.toJavaName();
        }
    }

    /**
     * A symbol that is lazily {@linkplain OptionalLazySymbol#resolve() resolved} to a {@link Type}.
     */
    public static class OptionalLazySymbol implements Type {
        private static final Class<?> MASK_NULL = OptionalLazySymbol.class;
        private final String name;
        private Class<?> resolved;

        @SuppressWarnings("this-escape")
        public OptionalLazySymbol(String name) {
            this.name = name;
            if (IS_BUILDING_NATIVE_IMAGE) {
                resolve();
            }
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
            if (!IS_IN_NATIVE_IMAGE && resolved == null) {
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
     * Utility for {@linkplain InvocationPlugins#register registration} of invocation plugins.
     */
    public static class Registration {

        private final InvocationPlugins plugins;

        private final Type declaringType;
        private final Replacements replacements;
        private boolean allowOverwrite;

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
            this.replacements = null;
        }

        /**
         * Creates an object for registering {@link InvocationPlugin}s for methods declared by a
         * given class.
         *
         * @param plugins where to register the plugins
         * @param declaringType the class declaring the methods for which plugins will be registered
         *            via this object
         * @param replacements the current Replacements provider
         */
        public Registration(InvocationPlugins plugins, Type declaringType, Replacements replacements) {
            this.plugins = plugins;
            this.declaringType = declaringType;
            this.replacements = replacements;
        }

        /**
         * Creates an object for registering {@link InvocationPlugin}s for methods declared by a
         * given class.
         *
         * @param plugins where to register the plugins
         * @param declaringClassName the name of the class class declaring the methods for which
         *            plugins will be registered via this object
         */
        public Registration(InvocationPlugins plugins, String declaringClassName) {
            this.plugins = plugins;
            this.declaringType = new OptionalLazySymbol(declaringClassName);
            this.replacements = null;
        }

        /**
         * Creates an object for registering {@link InvocationPlugin}s for methods declared by a
         * given class.
         *
         * @param plugins where to register the plugins
         * @param declaringClassName the name of the class class declaring the methods for which
         *            plugins will be registered via this object
         * @param replacements the current Replacements provider
         */
        public Registration(InvocationPlugins plugins, String declaringClassName, Replacements replacements) {
            this.plugins = plugins;
            this.declaringType = new OptionalLazySymbol(declaringClassName);
            this.replacements = replacements;
        }

        /**
         * Configures this registration to allow or disallow overwriting of invocation plugins.
         */
        public Registration setAllowOverwrite(boolean allowOverwrite) {
            this.allowOverwrite = allowOverwrite;
            return this;
        }

        /**
         * Registers a plugin for a method.
         */
        public void register(InvocationPlugin plugin) {
            plugins.register(declaringType, plugin, allowOverwrite);
        }

        /**
         * Registers a plugin for a method that is conditionally enabled. {@link Replacements} keeps
         * records of such plugins and avoids encoding method substitution graphs using these
         * plugins.
         *
         * @param isEnabled controls whether the plugin is actually registered.
         */
        public void registerConditional(boolean isEnabled, InvocationPlugin plugin) {
            replacements.registerConditionalPlugin(plugin);
            if (isEnabled) {
                plugins.register(declaringType, plugin, allowOverwrite);
            }
        }
    }

    /**
     * Utility for registering plugins after Graal may have been initialized. Registrations made via
     * this class are not finalized until {@link #close} is called.
     */
    public static class LateRegistration implements AutoCloseable {

        private InvocationPlugins plugins;
        private final List<InvocationPlugin> invocationPlugins = new ArrayList<>();
        private final Type declaringType;

        /**
         * Creates an object for registering {@link InvocationPlugin}s for methods declared by a
         * given class.
         *
         * @param plugins where to register the plugins
         * @param declaringType the class declaring the methods for which plugins will be registered
         *            via this object
         */
        public LateRegistration(InvocationPlugins plugins, Type declaringType) {
            this.plugins = plugins;
            this.declaringType = declaringType;
        }

        /**
         * Registers an invocation plugin for a given method. There must be no plugin currently
         * registered for {@code method}.
         */
        public void register(InvocationPlugin plugin) {
            assert plugins != null : String.format("Late registrations of invocation plugins for %s is already closed", declaringType);
            if (!plugin.isStatic) {
                plugin.rewriteReceiverType(declaringType);
            }

            invocationPlugins.add(plugin);
            assert IS_IN_NATIVE_IMAGE || Checks.check(this.plugins, declaringType, plugin);
            assert IS_IN_NATIVE_IMAGE || Checks.checkResolvable(declaringType, plugin);
        }

        @Override
        public void close() {
            assert plugins != null : String.format("Late registrations of invocation plugins for %s is already closed", declaringType);
            plugins.registerLate(declaringType, invocationPlugins);
            plugins = null;
        }
    }

    /**
     * Plugin registrations for already resolved methods. If non-null, then {@link #registrations}
     * is null and no further registrations can be made.
     */
    private final UnmodifiableEconomicMap<ResolvedJavaMethod, InvocationPlugin> resolvedRegistrations;

    /**
     * Map from class names in {@linkplain MetaUtil#toInternalName(String) internal} form to the
     * invocation plugins for the class. If non-null, then {@link #resolvedRegistrations} will be
     * null.
     */
    private final EconomicMap<String, ClassPlugins> registrations;

    /**
     * Deferred registrations as well as the guard for delimiting the initial registration phase.
     * The guard uses double-checked locking which is why this field is {@code volatile}.
     */
    private volatile List<Runnable> deferredRegistrations;

    /**
     * Flag to avoid recursive deferred registration.
     */
    private boolean processingDeferredRegistrations;

    /**
     * Adds a {@link Runnable} for doing registration deferred until the first time
     * {@link #get(ResolvedJavaMethod)} or {@link #closeRegistration()} is called on this object.
     */
    public void defer(Runnable deferrable) {
        assert deferredRegistrations != null : "registration is closed";
        deferredRegistrations.add(deferrable);
    }

    /**
     * Support for registering plugins once this object may be accessed by multiple threads.
     */
    private volatile LateClassPlugins lateRegistrations;

    /**
     * Per-class invocation plugins.
     */
    static class ClassPlugins {

        /**
         * Maps method names to InvocationPlugin lists.
         */
        final EconomicMap<String, InvocationPlugin> invocationPlugins = EconomicMap.create(Equivalence.DEFAULT);

        /**
         * Gets the invocation plugin for a given method.
         *
         * @return the invocation plugin for {@code method} or {@code null}
         */
        InvocationPlugin get(ResolvedJavaMethod method) {
            assert !method.isBridge();
            InvocationPlugin plugin = invocationPlugins.get(method.getName());
            while (plugin != null) {
                if (plugin.isSameType(method)) {
                    return plugin;
                }
                plugin = plugin.next;
            }
            return null;
        }

        public void register(InvocationPlugin plugin, boolean allowOverwrite) {
            if (allowOverwrite) {
                if (lookup(plugin) != null) {
                    register(plugin);
                    return;
                }
            } else {
                assert lookup(plugin) == null : "a value is already registered for " + plugin.getMethodNameWithArgumentsDescriptor();
            }
            register(plugin);
        }

        InvocationPlugin lookup(InvocationPlugin plugin) {
            InvocationPlugin registeredPlugin = invocationPlugins.get(plugin.name);
            while (registeredPlugin != null) {
                if (registeredPlugin.isSameType(plugin)) {
                    return registeredPlugin;
                }
                registeredPlugin = registeredPlugin.next;
            }
            return null;
        }

        /**
         * Registers an {@link InvocationPlugin}.
         */
        void register(InvocationPlugin plugin) {
            InvocationPlugin head = invocationPlugins.get(plugin.name);
            assert plugin.next == null;
            plugin.next = head;
            invocationPlugins.put(plugin.name, plugin);
        }

        void collectInvocationPluginsTo(List<InvocationPlugin> collection) {
            MapCursor<String, InvocationPlugin> plugins = invocationPlugins.getEntries();
            while (plugins.advance()) {
                for (InvocationPlugin plugin = plugins.getValue(); plugin != null; plugin = plugin.next) {
                    collection.add(plugin);
                }
            }
        }
    }

    static class LateClassPlugins extends ClassPlugins {
        static final String CLOSED_LATE_CLASS_PLUGIN = "-----";
        private final String className;
        private final LateClassPlugins next;

        LateClassPlugins(LateClassPlugins next, String className) {
            assert next == null || !next.isClosed() : "Late registration of invocation plugins is closed";
            this.next = next;
            this.className = className;
        }

        @SuppressFBWarnings(value = "ES_COMPARING_STRINGS_WITH_EQ", justification = "identity comparison against sentinel string value")
        boolean isClosed() {
            return className == CLOSED_LATE_CLASS_PLUGIN;
        }
    }

    /**
     * Registers an invocation plugin.
     *
     * @param declaringClass the class declaring the method
     * @param plugin invocation plugin to be associated with the specified method
     */
    void put(Type declaringClass, InvocationPlugin plugin, boolean allowOverwrite) {
        assert resolvedRegistrations == null : "registration is closed";
        String internalName = MetaUtil.toInternalName(declaringClass.getTypeName());
        assert plugin.isStatic || plugin.argumentTypes[0] == declaringClass : plugin;
        assert deferredRegistrations != null : "initial registration is closed - use " + LateRegistration.class.getName() + " for late registrations";

        ClassPlugins classPlugins = registrations.get(internalName);
        if (classPlugins == null) {
            classPlugins = new ClassPlugins();
            registrations.put(internalName, classPlugins);
        }
        classPlugins.register(plugin, allowOverwrite);
    }

    InvocationPlugin get(ResolvedJavaMethod method) {
        if (resolvedRegistrations != null) {
            return resolvedRegistrations.get(method);
        } else {
            if (!method.isBridge()) {
                ResolvedJavaType declaringClass = method.getDeclaringClass();
                flushDeferrables();
                String internalName = declaringClass.getName();
                ClassPlugins classPlugins = registrations.get(internalName);
                InvocationPlugin res = null;
                if (classPlugins != null) {
                    res = classPlugins.get(method);
                }
                if (res == null) {
                    LateClassPlugins lcp = findLateClassPlugins(internalName);
                    if (lcp != null) {
                        res = lcp.get(method);
                    }
                }
                if (res != null) {
                    // A decorator plugin is trusted since it does not replace
                    // the method it intrinsifies. A GeneratedInvocationPlugin
                    // is trusted since it only exists for @NodeIntrinsics and
                    // @Fold annotated methods (i.e., trusted Graal code).
                    if (res.isDecorator() || res instanceof GeneratedInvocationPlugin || canBeIntrinsified(declaringClass)) {
                        return res;
                    }
                }
                if (testExtensions != null) {
                    // Avoid the synchronization in the common case that there
                    // are no test extensions.
                    synchronized (this) {
                        if (testExtensions != null) {
                            List<InvocationPlugin> testInvocationPlugins = testExtensions.get(internalName);
                            if (testInvocationPlugins != null) {
                                for (InvocationPlugin testInvocationPlugin : testInvocationPlugins) {
                                    if (testInvocationPlugin.isSameType(method)) {
                                        return testInvocationPlugin;
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Supporting plugins for bridge methods would require including
                // the return type in the registered signature. Until needed,
                // this extra complexity is best avoided.
            }
        }
        return null;
    }

    /**
     * Determines if methods in a given class can have invocation plugins.
     *
     * @param declaringClass the class to test
     */
    public boolean canBeIntrinsified(ResolvedJavaType declaringClass) {
        return true;
    }

    /**
     * Subclasses can choose to only allow intrinsification of types matched by at least one
     * registered predicate. By default, InvocationPlugins allows any type to be intrinsified.
     *
     * @param predicate controls which types may be intrinsified.
     */
    public void registerIntrinsificationPredicate(Predicate<ResolvedJavaType> predicate) {
    }

    LateClassPlugins findLateClassPlugins(String internalClassName) {
        for (LateClassPlugins lcp = lateRegistrations; lcp != null; lcp = lcp.next) {
            if (lcp.className.equals(internalClassName)) {
                return lcp;
            }
        }
        return null;
    }

    @SuppressWarnings("serial")
    static class InvocationPluginRegistrationError extends GraalError {
        InvocationPluginRegistrationError(Throwable cause) {
            super(cause);
        }
    }

    public void flushDeferrables() {
        if (deferredRegistrations != null) {
            synchronized (this) {
                if (deferredRegistrations != null) {
                    if (processingDeferredRegistrations) {
                        throw new GraalError("recursively performing deferred registration");
                    }
                    try {
                        processingDeferredRegistrations = true;
                        for (Runnable deferrable : deferredRegistrations) {
                            deferrable.run();
                        }
                        deferredRegistrations = null;
                    } catch (InvocationPluginRegistrationError t) {
                        throw t;
                    } catch (Throwable t) {
                        /*
                         * Something went wrong during registration but it's possible we'll end up
                         * coming back into this code. nulling out deferredRegistrations would just
                         * cause other things to break and rerunning them would cause errors about
                         * already registered plugins, so rethrow the original exception during
                         * later invocations.
                         */
                        deferredRegistrations.clear();
                        Runnable rethrow = new Runnable() {
                            @Override
                            public void run() {
                                throw new InvocationPluginRegistrationError(t);
                            }
                        };
                        deferredRegistrations.add(rethrow);
                        rethrow.run();
                    } finally {
                        processingDeferredRegistrations = false;
                    }
                }
            }
        }
    }

    private volatile EconomicMap<String, List<InvocationPlugin>> testExtensions;

    private static int findInvocationPlugin(List<InvocationPlugin> list, InvocationPlugin key) {
        for (int i = 0; i < list.size(); i++) {
            InvocationPlugin invocationPlugin = list.get(i);
            if (invocationPlugin.isSameType(key)) {
                return i;
            }
        }
        return -1;
    }

    private static List<InvocationPlugin> getOrCreate(EconomicMap<String, List<InvocationPlugin>> res, String key) {
        List<InvocationPlugin> invocationPlugins = res.get(key);
        if (invocationPlugins == null) {
            invocationPlugins = new ArrayList<>();
            res.put(key, invocationPlugins);
        }
        return invocationPlugins;
    }

    /**
     * Extends the plugins in this object with those from {@code other}. The added plugins should be
     * {@linkplain #removeTestPlugins(InvocationPlugins) removed} after the test.
     * <p>
     * This extension mechanism exists only for tests that want to add extra invocation plugins
     * after the compiler has been initialized.
     *
     * @param ignored if non-null, the invocation plugins from {@code other} already in this object
     *            prior to calling this method are added to this list. These plugins are not added
     *            to this object.
     */
    public synchronized void addTestPlugins(InvocationPlugins other, List<Pair<String, InvocationPlugin>> ignored) {
        assert resolvedRegistrations == null : "registration is closed";
        EconomicMap<String, List<InvocationPlugin>> otherInvocationPlugins = other.getInvocationPlugins(true, false);
        if (otherInvocationPlugins.isEmpty()) {
            return;
        }
        if (testExtensions == null) {
            testExtensions = EconomicMap.create();
        }
        MapCursor<String, List<InvocationPlugin>> c = otherInvocationPlugins.getEntries();
        while (c.advance()) {
            String declaringClass = c.getKey();
            List<InvocationPlugin> testInvocationPlugins = getOrCreate(testExtensions, declaringClass);
            for (InvocationPlugin otherInvocationPlugin : c.getValue()) {
                int index = findInvocationPlugin(testInvocationPlugins, otherInvocationPlugin);
                if (index != -1) {
                    if (ignored != null) {
                        ignored.add(Pair.create(declaringClass, otherInvocationPlugin));
                    }
                } else {
                    testInvocationPlugins.add(otherInvocationPlugin);
                }
            }
        }
    }

    /**
     * Removes the plugins from {@code other} in this object that were added by
     * {@link #addTestPlugins}.
     */
    public synchronized void removeTestPlugins(InvocationPlugins other) {
        assert resolvedRegistrations == null : "registration is closed";
        if (testExtensions != null) {
            MapCursor<String, List<InvocationPlugin>> c = other.getInvocationPlugins(false).getEntries();
            while (c.advance()) {
                String declaringClass = c.getKey();
                List<InvocationPlugin> testInvocationPlugins = testExtensions.get(declaringClass);
                if (testInvocationPlugins != null) {
                    for (InvocationPlugin otherInvocationPlugin : c.getValue()) {
                        int index = findInvocationPlugin(testInvocationPlugins, otherInvocationPlugin);
                        if (index != -1) {
                            testInvocationPlugins.remove(index);
                        }
                    }
                    if (testInvocationPlugins.isEmpty()) {
                        testExtensions.removeKey(declaringClass);
                    }
                }
            }
            if (testExtensions.isEmpty()) {
                testExtensions = null;
            }
        }
    }

    synchronized void registerLate(Type declaringType, List<InvocationPlugin> invocationPlugins) {
        String internalName = MetaUtil.toInternalName(declaringType.getTypeName());
        assert findLateClassPlugins(internalName) == null : "Cannot have more than one late registration of invocation plugins for " + internalName;
        LateClassPlugins lateClassPlugins = new LateClassPlugins(lateRegistrations, internalName);
        for (InvocationPlugin plugin : invocationPlugins) {
            lateClassPlugins.register(plugin);
        }
        lateRegistrations = lateClassPlugins;
    }

    private synchronized boolean closeLateRegistrations() {
        if (lateRegistrations == null || !lateRegistrations.isClosed()) {
            lateRegistrations = new LateClassPlugins(lateRegistrations, CLOSED_LATE_CLASS_PLUGIN);
        }
        return true;
    }

    /**
     * Processes deferred registrations and then closes this object for future registration.
     */
    public void closeRegistration() {
        assert closeLateRegistrations();
        flushDeferrables();
    }

    /**
     * Determines if this object currently contains any plugins (in any state of registration). If
     * this object has any {@link #defer(Runnable) deferred registrations}, it is assumed that
     * executing them will result in at least one plugin being registered.
     */
    public boolean isEmpty() {
        if (parent != null && !parent.isEmpty()) {
            return false;
        }
        UnmodifiableEconomicMap<ResolvedJavaMethod, InvocationPlugin> resolvedRegs = resolvedRegistrations;
        if (resolvedRegs != null) {
            if (!resolvedRegs.isEmpty()) {
                return false;
            }
        }
        List<Runnable> deferred = deferredRegistrations;
        if (deferred != null) {
            if (!deferred.isEmpty()) {
                return false;
            }
        }
        for (LateClassPlugins late = lateRegistrations; late != null; late = late.next) {
            if (!late.invocationPlugins.isEmpty()) {
                return false;
            }
        }
        return registrations.size() == 0;
    }

    /**
     * The plugins {@linkplain #lookupInvocation searched} before searching in this object.
     */
    protected final InvocationPlugins parent;

    /**
     * Method filter for disabled invocation plugins. See {@link Options#DisableIntrinsics}.
     */
    private MethodFilter disabledIntrinsicsFilter;

    /**
     * Allows lazy initialization of {@link #disabledIntrinsicsFilter}.
     */
    private volatile boolean isDisabledIntrinsicsFilterInitialized;

    /**
     * Verbose mode for logging disabled invocation plugins. See {@link Options#DisableIntrinsics}.
     */
    private boolean logDisabledIntrinsics;

    /**
     * Creates a set of invocation plugins with no parent.
     */
    public InvocationPlugins() {
        this(null, null);
    }

    /**
     * Creates a set of invocation plugins.
     *
     * @param resolvedPlugins if non-null, this object will contain the closed set of invocation
     *            plugins for a set of resolved methods, and no further plugin registration is
     *            permitted.
     * @param parent if non-null, this object will be searched first when looking up plugins
     */
    public InvocationPlugins(Map<ResolvedJavaMethod, InvocationPlugin> resolvedPlugins, InvocationPlugins parent) {
        this.parent = parent;

        if (resolvedPlugins == null) {
            this.resolvedRegistrations = null;
            this.deferredRegistrations = new ArrayList<>();
            this.registrations = EconomicMap.create();
        } else {
            EconomicMap<ResolvedJavaMethod, InvocationPlugin> map = EconomicMap.create(resolvedPlugins.size());
            for (Map.Entry<ResolvedJavaMethod, InvocationPlugin> entry : resolvedPlugins.entrySet()) {
                map.put(entry.getKey(), entry.getValue());
            }
            this.resolvedRegistrations = map;
            this.deferredRegistrations = null;
            this.registrations = null;
        }
    }

    protected void register(Type declaringClass, InvocationPlugin plugin, boolean allowOverwrite) {
        if (!plugin.isStatic) {
            plugin.rewriteReceiverType(declaringClass);
        }
        put(declaringClass, plugin, allowOverwrite);
        assert IS_IN_NATIVE_IMAGE || Checks.check(this, declaringClass, plugin);
        assert IS_IN_NATIVE_IMAGE || Checks.checkResolvable(declaringClass, plugin);
    }

    /**
     * Registers an invocation plugin for a given method. There must be no plugin currently
     * registered for {@code method}.
     */
    public final void register(Type declaringClass, InvocationPlugin plugin) {
        register(declaringClass, plugin, false);
    }

    private void initializeDisabledIntrinsicsFilter(OptionValues options) {
        if (!isDisabledIntrinsicsFilterInitialized) {
            synchronized (this) {
                if (!isDisabledIntrinsicsFilterInitialized) {
                    String filterValue = Options.DisableIntrinsics.getValue(options);
                    if (filterValue != null) {
                        String[] values = filterValue.split(":");
                        if (values.length > 1 && "verbose".equals(values[1])) {
                            logDisabledIntrinsics = true;
                        }
                        disabledIntrinsicsFilter = MethodFilter.parse(values[0]);
                    }
                }
                isDisabledIntrinsicsFilterInitialized = true;
            }
        }
    }

    protected final boolean shouldLogDisabledIntrinsics(OptionValues options) {
        initializeDisabledIntrinsicsFilter(options);
        return logDisabledIntrinsics;
    }

    /**
     * Gets the plugin for a given method.
     *
     * @param method the method to lookup
     * @param allowDecorators return {@link InvocationPlugin#isDecorator()} plugins only if true
     * @param allowDisable whether to respect the DisableIntrinsics flag
     * @return the plugin associated with {@code method} or {@code null} if none exists
     */
    public InvocationPlugin lookupInvocation(ResolvedJavaMethod method, boolean allowDecorators, boolean allowDisable, OptionValues options) {
        initializeDisabledIntrinsicsFilter(options);

        if (parent != null) {
            InvocationPlugin plugin = parent.lookupInvocation(method, allowDecorators, allowDisable, options);
            if (plugin != null) {
                return plugin;
            }
        }
        InvocationPlugin invocationPlugin = get(method);
        if (invocationPlugin != null) {
            if (allowDecorators || !invocationPlugin.isDecorator()) {
                if (allowDisable && disabledIntrinsicsFilter != null && disabledIntrinsicsFilter.matches(method)) {
                    if (invocationPlugin.canBeDisabled()) {
                        if (logDisabledIntrinsics) {
                            TTY.println("[Warning] Intrinsic for %s is disabled.", method.format("%H.%n(%p)"));
                        }
                        return null;
                    } else {
                        if (logDisabledIntrinsics) {
                            TTY.println("[Warning] Intrinsic for %s cannot be disabled.", method.format("%H.%n(%p)"));
                        }
                    }
                }
                if (logDisabledIntrinsics && invocationPlugin.canBeDisabled()) {
                    TTY.println("[Warning] Intrinsic for %s is enabled.", method.format("%H.%n(%p)"));
                }
                return invocationPlugin;
            }
        }
        return null;
    }

    /**
     * Gets the plugin for a given method. By default this will hide
     * {@link InvocationPlugin#isDecorator()}} plugins since they can only be applied in certain
     * contexts.
     *
     * @param method the method to lookup
     * @return the plugin associated with {@code method} or {@code null} if none exists
     */
    public InvocationPlugin lookupInvocation(ResolvedJavaMethod method, OptionValues options) {
        return lookupInvocation(method, false, true, options);
    }

    /**
     * Gets the set of registered invocation plugins.
     *
     * @return a map from class names in {@linkplain MetaUtil#toInternalName(String) internal} form
     *         to the invocation plugins for methods in the class
     */
    public EconomicMap<String, List<InvocationPlugin>> getInvocationPlugins(boolean includeParents) {
        return getInvocationPlugins(includeParents, true);
    }

    /**
     * Gets the set of registered invocation plugins.
     *
     * @return a map from class names in {@linkplain MetaUtil#toInternalName(String) internal} form
     *         to the invocation plugins for methods in the class
     */
    private EconomicMap<String, List<InvocationPlugin>> getInvocationPlugins(boolean includeParents, boolean flushDeferrables) {
        EconomicMap<String, List<InvocationPlugin>> res = EconomicMap.create(Equivalence.DEFAULT);
        if (parent != null && includeParents) {
            res.putAll(parent.getInvocationPlugins(true, flushDeferrables));
        }
        if (resolvedRegistrations != null) {
            UnmodifiableMapCursor<ResolvedJavaMethod, InvocationPlugin> cursor = resolvedRegistrations.getEntries();
            while (cursor.advance()) {
                ResolvedJavaMethod method = cursor.getKey();
                InvocationPlugin plugin = cursor.getValue();
                String type = method.getDeclaringClass().getName();
                List<InvocationPlugin> pluginsPerClass = getOrCreate(res, type);
                pluginsPerClass.add(plugin);
            }
        } else {
            if (flushDeferrables) {
                flushDeferrables();
            }
            MapCursor<String, ClassPlugins> classes = registrations.getEntries();
            while (classes.advance()) {
                String type = classes.getKey();
                ClassPlugins cp = classes.getValue();
                List<InvocationPlugin> pluginsPerClass = getOrCreate(res, type);
                cp.collectInvocationPluginsTo(pluginsPerClass);
            }
            for (LateClassPlugins lcp = lateRegistrations; lcp != null; lcp = lcp.next) {
                if (!lcp.isClosed()) {
                    List<InvocationPlugin> pluginsPerClass = getOrCreate(res, lcp.className);
                    lcp.collectInvocationPluginsTo(pluginsPerClass);
                }
            }
            if (testExtensions != null) {
                // Avoid the synchronization in the common case that there
                // are no test extensions.
                synchronized (this) {
                    if (testExtensions != null) {
                        MapCursor<String, List<InvocationPlugin>> c = testExtensions.getEntries();
                        while (c.advance()) {
                            String type = c.getKey();
                            List<InvocationPlugin> pluginsPerClass = getOrCreate(res, type);
                            pluginsPerClass.addAll(c.getValue());
                        }
                    }
                }
            }
        }
        return res;
    }

    @Override
    public String toString() {
        UnmodifiableMapCursor<String, List<InvocationPlugin>> entries = getInvocationPlugins(false, false).getEntries();
        Set<String> all = new TreeSet<>();
        while (entries.advance()) {
            String c = MetaUtil.internalNameToJava(entries.getKey(), true, false);
            for (InvocationPlugin invocationPlugin : entries.getValue()) {
                all.add(c + '.' + invocationPlugin.getMethodNameWithArgumentsDescriptor());
            }
        }
        return String.join(System.lineSeparator(), all);
    }

    /**
     * The id of the single isolate to emit output for {@link Options#PrintIntrinsics}.
     */
    private static final GlobalAtomicLong PRINTING_ISOLATE = new GlobalAtomicLong(0L);

    /**
     * The intrinsic methods (in {@link Options#DisableIntrinsics} format) that have been printed by
     * {@link #maybePrintIntrinsics}.
     */
    @NativeImageReinitialize private static Set<String> PrintedIntrinsics = new HashSet<>();

    /**
     * Determines if {@code plugin} is disabled by {@link Options#DisableIntrinsics}.
     *
     * @param declaringClassInternalName name of the declaring class for {@code plugin} as returned
     *            by {@link JavaType#getName()}
     * @param declaringClassJavaName name of the declaring class for {@code plugin} as returned by
     *            by {@link JavaType#toJavaName()}
     */
    protected boolean isDisabled(InvocationPlugin plugin, String declaringClassInternalName, String declaringClassJavaName, OptionValues options) {
        initializeDisabledIntrinsicsFilter(options);
        if (disabledIntrinsicsFilter != null) {
            if (disabledIntrinsicsFilter.matchesWithArgs(declaringClassJavaName, plugin.name, List.of(plugin.argumentTypes))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Prints the methods for which there are intrinsics in this object if this is the first (or
     * only) isolate in which this method is called and {@link Options#PrintIntrinsics} is true in
     * {@code options}. Intrinsics that have already been printed are skipped.
     * <p>
     * Printing goes to {@link TTY} and emits one method per line in a format compatible with
     * {@link Options#DisableIntrinsics}. A header line of {@code "<Intrinsics>"} and a trailer line
     * of {@code "</Intrinsics>"} is also emitted around each batch of printing.
     *
     * @return whether printing was performed
     */
    public boolean maybePrintIntrinsics(OptionValues options) {
        if (InvocationPlugins.Options.PrintIntrinsics.getValue(options)) {
            long isolateID = IsolateUtil.getIsolateID();
            if (PRINTING_ISOLATE.get() == isolateID || PRINTING_ISOLATE.compareAndSet(0, isolateID)) {
                synchronized (PRINTING_ISOLATE) {
                    if (IS_IN_NATIVE_IMAGE && PrintedIntrinsics == null) {
                        PrintedIntrinsics = new HashSet<>();
                    }
                    UnmodifiableMapCursor<String, List<InvocationPlugin>> entries = getInvocationPlugins(false, true).getEntries();
                    Set<String> unique = new TreeSet<>();
                    while (entries.advance()) {
                        String declaringClassName = entries.getKey();
                        String c = MetaUtil.internalNameToJava(declaringClassName, true, false);
                        for (InvocationPlugin plugin : entries.getValue()) {
                            String method = c + '.' + plugin.asMethodFilterString();
                            if (!plugin.canBeDisabled()) {
                                method += " [cannot be disabled]";
                            } else if (isDisabled(plugin, declaringClassName, c, options)) {
                                method += " [disabled]";
                            }
                            if (PrintedIntrinsics.add(method)) {
                                unique.add(method);
                            }
                        }
                    }
                    boolean printedParent = false;
                    if (parent != null) {
                        printedParent = parent.maybePrintIntrinsics(options);
                    }
                    if (!unique.isEmpty()) {
                        TTY.printf("<Intrinsics>%n%s%n</Intrinsics>%n", String.join(System.lineSeparator(), unique));
                        return true;
                    }
                    return printedParent;
                }
            }
        }
        return false;
    }

    /**
     * Code only used in assertions. Putting this in a separate class reduces class load time.
     */
    private static class Checks {
        private static final int MAX_ARITY = 13;
        /**
         * The set of all {@link InvocationPlugin#apply} method signatures.
         */
        static final Class<?>[][] SIGS;

        static {
            if (!Assertions.assertionsEnabled() && !IS_BUILDING_NATIVE_IMAGE) {
                throw new GraalError("%s must only be used in assertions", Checks.class.getName());
            }
            ArrayList<Class<?>[]> sigs = new ArrayList<>(MAX_ARITY);
            if (!IS_IN_NATIVE_IMAGE) {
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
            }
            SIGS = sigs.toArray(new Class<?>[sigs.size()][]);
        }

        static boolean containsPlugin(InvocationPlugins p, Type declaringType, InvocationPlugin plugin) {
            String internalName = MetaUtil.toInternalName(declaringType.getTypeName());
            ClassPlugins classPlugins = p.registrations.get(internalName);
            return classPlugins != null && classPlugins.lookup(plugin) != null;
        }

        public static boolean check(InvocationPlugins plugins, Type declaringType, InvocationPlugin plugin) {
            InvocationPlugins p = plugins.parent;
            while (p != null) {
                assert !containsPlugin(p, declaringType, plugin) : "a plugin is already registered for " + plugin.getMethodNameWithArgumentsDescriptor();
                p = p.parent;
            }
            if (plugin instanceof ForeignCallPlugin || plugin instanceof GeneratedInvocationPlugin) {
                return true;
            }
            int arguments = plugin.getArgumentsSize();
            assert arguments < SIGS.length : format("need to extend %s to support method with %d arguments: %s", InvocationPlugin.class.getSimpleName(), arguments,
                            plugin.getMethodNameWithArgumentsDescriptor());

            Class<?> klass = plugin.getClass();
            while (klass != InvocationPlugin.class) {
                for (Method m : klass.getDeclaredMethods()) {
                    if (m.getName().equals("defaultHandler") || m.getName().equals("execute")) {
                        return true;
                    }
                    if (m.getName().equals("apply")) {
                        Class<?>[] parameterTypes = m.getParameterTypes();
                        if (Arrays.equals(SIGS[arguments], parameterTypes)) {
                            return true;
                        }
                    }
                }
                klass = klass.getSuperclass();
            }
            throw new AssertionError(format("graph builder plugin for %s not found. check that the plugin-method signature matches the target", plugin.getMethodNameWithArgumentsDescriptor()));
        }

        static boolean checkResolvable(Type declaringType, InvocationPlugin plugin) {
            if (declaringType instanceof ResolvedJavaSymbol) {
                return checkResolvable(((ResolvedJavaSymbol) declaringType).getResolved(), plugin);
            }
            Class<?> declaringClass = resolveType(declaringType, plugin.isOptional());
            if (declaringClass == null) {
                return true;
            }
            if ("<init>".equals(plugin.name)) {
                if (resolveConstructor(declaringClass, plugin) == null && !plugin.isOptional()) {
                    throw new AssertionError(String.format("Constructor not found: %s%s", declaringClass.getName(), plugin.argumentsDescriptor));
                }
            } else {
                if (resolveMethod(declaringClass, plugin) == null && !plugin.isOptional()) {
                    throw new NoSuchMethodError(String.format("%s.%s", declaringClass.getName(), plugin.getMethodNameWithArgumentsDescriptor()));
                }
            }
            return true;
        }

        private static boolean checkResolvable(ResolvedJavaType declaringType, InvocationPlugin plugin) {
            if (resolveJavaMethod(declaringType, plugin) == null && !plugin.isOptional()) {
                throw new AssertionError(String.format("Method not found: %s.%s", declaringType.toJavaName(), plugin.getMethodNameWithArgumentsDescriptor()));
            }
            return true;
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
        if (IS_IN_NATIVE_IMAGE) {
            throw new GraalError("Unresolved type in native image image:" + type.getTypeName());
        }
        return resolveClass(type.getTypeName(), optional);
    }

    /**
     * Resolves a given invocation plugin to a method in a given class. If more than one method with
     * the parameter types matching {@code plugin} is found and the return types of all the matching
     * methods form an inheritance chain, the one with the most specific type is returned; otherwise
     * {@link NoSuchMethodError} is thrown.
     *
     * @param declaringClass the class to search for a method matching {@code plugin}
     * @return the method (if any) in {@code declaringClass} matching {@code plugin}
     */
    public static Method resolveMethod(Class<?> declaringClass, InvocationPlugin plugin) {
        if ("<init>".equals(plugin.name)) {
            return null;
        }
        Method[] methods = declaringClass.getDeclaredMethods();
        Method match = null;
        for (Method m : methods) {
            if (plugin.isSameType(m)) {
                if (match == null) {
                    match = m;
                } else if (match.getReturnType().isAssignableFrom(m.getReturnType())) {
                    // `m` has a more specific return type - choose it
                    // (`match` is most likely a bridge method)
                    match = m;
                } else {
                    if (!m.getReturnType().isAssignableFrom(match.getReturnType())) {
                        throw new NoSuchMethodError(String.format(
                                        "Found 2 methods with same name and parameter types but unrelated return types:%n %s%n %s", match, m));
                    }
                }
            }
        }
        return match;
    }

    /**
     * Same as {@link #resolveMethod(Class, InvocationPlugin)} and
     * {@link #resolveConstructor(Class, InvocationPlugin)} except in terms of
     * {@link ResolvedJavaType} and {@link ResolvedJavaMethod}.
     */
    public static ResolvedJavaMethod resolveJavaMethod(ResolvedJavaType declaringClass, InvocationPlugin plugin) {
        ResolvedJavaMethod[] methods = declaringClass.getDeclaredMethods(false);
        if (plugin.name.equals("<init>")) {
            for (ResolvedJavaMethod m : methods) {
                if (m.getName().equals("<init>") && m.getSignature().toMethodDescriptor().startsWith(plugin.argumentsDescriptor)) {
                    return m;
                }
            }
            return null;
        }

        ResolvedJavaMethod match = null;
        for (int i = 0; i < methods.length; ++i) {
            ResolvedJavaMethod m = methods[i];
            if (plugin.isSameType(m)) {
                if (match == null) {
                    match = m;
                } else {
                    final ResolvedJavaType matchReturnType = (ResolvedJavaType) match.getSignature().getReturnType(declaringClass);
                    final ResolvedJavaType mReturnType = (ResolvedJavaType) m.getSignature().getReturnType(declaringClass);
                    if (matchReturnType.isAssignableFrom(mReturnType)) {
                        // `m` has a more specific return type - choose it
                        // (`match` is most likely a bridge method)
                        match = m;
                    } else {
                        if (!mReturnType.isAssignableFrom(matchReturnType)) {
                            throw new NoSuchMethodError(String.format(
                                            "Found 2 methods with same name and parameter types but unrelated return types:%n %s%n %s", match, m));
                        }
                    }
                }
            }
        }
        return match;
    }

    /**
     * Resolves a given invocation plugin to a constructor in a given class.
     *
     * @param declaringClass the class to search for a constructor matching {@code plugin}
     * @return the constructor (if any) in {@code declaringClass} matching {@code plugin}
     */
    public static Constructor<?> resolveConstructor(Class<?> declaringClass, InvocationPlugin plugin) {
        if (!"<init>".equals(plugin.name)) {
            return null;
        }
        Constructor<?>[] constructors = declaringClass.getDeclaredConstructors();
        for (Constructor<?> c : constructors) {
            if (plugin.isSameType(c)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Called when no invocation plugin can be found for the given target method.
     */
    @SuppressWarnings("unused")
    public void notifyNoPlugin(ResolvedJavaMethod targetMethod, OptionValues options) {
        if (parent != null) {
            parent.notifyNoPlugin(targetMethod, options);
        }
    }
}
