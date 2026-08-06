/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.shared.option.APIOption;
import com.oracle.svm.shared.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.shared.singletons.ImageSingletonLoader;
import com.oracle.svm.shared.singletons.ImageSingletonWriter;
import com.oracle.svm.shared.singletons.LayeredPersistFlags;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.LayeredCallbacksSingletonTrait;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.Duplicable;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;

/// Records independent build-time and runtime assertion settings.
///
/// Build-time settings control hosted folding and classes initialized during image building.
/// Runtime settings start with the Java defaults for every isolate and are populated only from
/// runtime VM arguments. Runtime-initialized image classes use build-time settings in
/// [non-strict Java option mode][SubstrateOptions#StrictRuntimeJavaOptions] and otherwise resolve
/// their status from runtime settings or from a [ClassLoader] assertion map initialized from them.
/// Classes defined at run time always use the runtime settings.
@AutomaticallyRegisteredImageSingleton
@SingletonTraits(access = AllAccess.class, layeredCallbacks = AssertionsSupport.LayeredCallbacks.class, layeredInstallationKind = Duplicable.class)
public final class AssertionsSupport {

    /// Defines native-image options that are compatible with standard Java launcher assertion flags.
    public static class Options {

        private static final char VALUE_SEPARATOR = ':';

        /// Configures assertion status for classes, packages, or all code at image build time.
        /// Build-time-initialized classes always use this configuration. Runtime-initialized image
        /// classes use it when [SubstrateOptions#StrictRuntimeJavaOptions] is false; otherwise they use
        /// the runtime assertion options. Runtime-loaded classes always use the runtime assertion options.
        @APIOption(name = {"-ea", "-enableassertions"}, launcherOption = true, valueSeparator = VALUE_SEPARATOR, valueTransformer = AssertionsOptionTransformer.Enable.class, defaultValue = "", //
                        customHelp = "also -ea[:[packagename]...|:classname] or -enableassertions[:[packagename]...|:classname]. Enable assertions with specified granularity at run time.")//
        @APIOption(name = {"-da",
                        "-disableassertions"}, launcherOption = true, valueSeparator = VALUE_SEPARATOR, valueTransformer = AssertionsOptionTransformer.Disable.class, defaultValue = "", //
                        customHelp = "also -da[:[packagename]...|:classname] or -disableassertions[:[packagename]...|:classname]. Disable assertions with specified granularity at run time.")//
        @Option(help = "Enable or disable Java assert statements at run time") //
        public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> RuntimeAssertions = new HostedOptionKey<>(AccumulatingLocatableMultiOptionValue.Strings.build());

        /// Configures assertion status for bootstrap-loaded classes at image build time.
        /// Build-time-initialized system classes always use this configuration. Runtime-initialized
        /// system classes use it when [SubstrateOptions#StrictRuntimeJavaOptions] is false; otherwise
        /// they always use the runtime system assertion options.
        @APIOption(name = {"-esa",
                        "-enablesystemassertions"}, launcherOption = true, customHelp = "also -enablesystemassertions. Enables assertions in all system classes at run time.") //
        @APIOption(name = {"-dsa", "-disablesystemassertions"}, launcherOption = true, kind = APIOption.APIOptionKind.Negated, //
                        customHelp = "also -disablesystemassertions. Disables assertions in all system classes at run time.") //
        @Option(help = "Enable or disable Java system assertions at run time") //
        public static final HostedOptionKey<Boolean> RuntimeSystemAssertions = new HostedOptionKey<>(false);
    }

    private static final String PACKAGE = "package";
    private static final String CLASS = "class";

    private static final String ASSERTION_STATUS_KEYS = "AssertionStatusKeys";
    private static final String ASSERTION_STATUS_VALUES = "AssertionStatusValues";

    private static final String DEFAULT_ASSERTION_STATUS = "defaultAssertionStatus";
    private static final String SYSTEM_ASSERTION_STATUS = "systemAssertionStatus";

    /// Name prefix used by javac for the synthetic assertion-status field.
    public static final String SYNTHETIC_ASSERTIONS_DISABLED_FIELD_NAME = "$assertionsDisabled";

    public static final char ENABLE_PREFIX = '+';
    public static final char DISABLE_PREFIX = '-';
    private static final String PACKAGE_SUFFIX = "...";

    /** Error text used when a runtime assertions option has an unrecognized prefix. */
    private static final String PREFIX_CHECK_MSG = "RuntimeAssertions value starts with `" + ENABLE_PREFIX + "` or `" + DISABLE_PREFIX + "`";
    /** Error text used when a runtime assertions option value is empty. */
    private static final String EMPTY_OPTION_VALUE_MSG = "Empty RuntimeAssertions option value";

    @Fold
    public static AssertionsSupport singleton() {
        return ImageSingletons.lookup(AssertionsSupport.class);
    }

    private final Map<String, Boolean> buildTimePackageAssertionStatus;
    private final Map<String, Boolean> buildTimeClassAssertionStatus;
    private final boolean buildTimeDefaultAssertionStatus;
    private final boolean buildTimeSystemAssertionStatus;

    private final Map<String, Boolean> runtimePackageAssertionStatus;
    private final Map<String, Boolean> runtimeClassAssertionStatus;
    private boolean runtimeDefaultAssertionStatus;
    private boolean runtimeSystemAssertionStatus;

    /**
     * Creates assertion support from the configured native-image assertion options.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public AssertionsSupport() {
        Map<String, Boolean> packageStatus = new HashMap<>();
        Map<String, Boolean> classStatus = new HashMap<>();
        boolean tmpDefaultAssertionStatus = false;

        for (String option : Options.RuntimeAssertions.getValue().values()) {
            VMError.guarantee(!option.isEmpty(), EMPTY_OPTION_VALUE_MSG);

            char prefix = option.charAt(0);
            boolean enable;
            if (prefix == ENABLE_PREFIX) {
                enable = true;
            } else if (prefix == DISABLE_PREFIX) {
                enable = false;
            } else {
                throw VMError.shouldNotReachHere(PREFIX_CHECK_MSG);
            }

            String classOrPackage = option.substring(1);
            tmpDefaultAssertionStatus = updateAssertionStatus(packageStatus, classStatus, tmpDefaultAssertionStatus, classOrPackage, enable);
        }

        buildTimePackageAssertionStatus = Map.copyOf(packageStatus);
        buildTimeClassAssertionStatus = Map.copyOf(classStatus);
        buildTimeDefaultAssertionStatus = tmpDefaultAssertionStatus;
        buildTimeSystemAssertionStatus = Options.RuntimeSystemAssertions.getValue();
        /* Runtime assertion state is intentionally independent of the image build options. */
        runtimePackageAssertionStatus = new HashMap<>();
        runtimeClassAssertionStatus = new HashMap<>();
        runtimeDefaultAssertionStatus = false;
        runtimeSystemAssertionStatus = false;
    }

    /// Applies a class, package, or default assertion directive and returns the updated default.
    private static boolean updateAssertionStatus(Map<String, Boolean> packageStatus, Map<String, Boolean> classStatus, boolean defaultStatus, String classOrPackage, boolean enable) {
        if (classOrPackage.endsWith(PACKAGE_SUFFIX)) {
            String packageName = classOrPackage.substring(0, classOrPackage.length() - PACKAGE_SUFFIX.length());
            packageStatus.put(packageName, enable);
            return defaultStatus;
        } else if (classOrPackage.isEmpty()) {
            return enable;
        } else {
            classStatus.put(classOrPackage, enable);
            return defaultStatus;
        }
    }

    /// Applies a runtime assertion directive for a class, package, or the application default.
    ///
    /// @param classOrPackage target class or package to which directive applies.
    /// A value of `""` applies the directive to all classes.
    public void updateRuntimeAssertionStatus(String classOrPackage, boolean enable) {
        runtimeDefaultAssertionStatus = updateAssertionStatus(
                        runtimePackageAssertionStatus,
                        runtimeClassAssertionStatus,
                        runtimeDefaultAssertionStatus,
                        classOrPackage, enable);
    }

    /// Updates the runtime assertion default for bootstrap-loaded classes.
    public void updateRuntimeSystemAssertionStatus(boolean enable) {
        runtimeSystemAssertionStatus = enable;
    }

    /**
     * Looks up assertion status with the same class and package precedence as
     * {@code ClassLoader#desiredAssertionStatus(java.lang.String)}.
     */
    private static boolean lookupAssertionStatus(Map<String, Boolean> packageStatus, Map<String, Boolean> classStatus, String name, boolean fallback) {
        String className = name;
        // Check for a class entry
        Boolean result = classStatus.get(className);
        if (result != null) {
            return result;
        }

        // Check for most specific package entry
        int dotIndex = className.lastIndexOf('.');
        if (dotIndex < 0) {
            /* Image-level maps use an empty string for the unnamed package. */
            result = packageStatus.get("");
            if (result != null) {
                return result;
            }
        }
        while (dotIndex > 0) {
            className = className.substring(0, dotIndex);
            result = packageStatus.get(className);
            if (result != null) {
                return result;
            }
            dotIndex = className.lastIndexOf('.', dotIndex - 1);
        }

        return fallback;
    }

    /**
     * Determines whether assertions should be enabled for class `name` when it is loaded by
     * `classLoader`.
     */
    public boolean desiredAssertionStatus(String name, ClassLoader classLoader) {
        if (SubstrateUtil.HOSTED) {
            return desiredHostedAssertionStatus(name, classLoader);
        }
        /* The bootstrap loader is represented as null and uses the system assertion status. */
        return lookupAssertionStatus(runtimePackageAssertionStatus, runtimeClassAssertionStatus, name,
                        classLoader == null ? runtimeSystemAssertionStatus : runtimeDefaultAssertionStatus);
    }

    /// Determines assertion status for `hub`.
    public boolean desiredAssertionStatus(DynamicHub hub) {
        String name = hub.getName();
        ClassLoader classLoader = hub.getClassLoader();
        boolean imageClass = !hub.isRuntimeLoaded();
        boolean buildTimeInitialized = hub.getClassInitializationInfo().isBuildTimeInitialized();
        if (imageClass && (buildTimeInitialized || !SubstrateOptions.StrictRuntimeJavaOptions.getValue())) {
            return SubstrateUtil.HOSTED ? //
                            desiredHostedAssertionStatus(name, classLoader) : //
                            desiredRuntimeBuildTimeAssertionStatus(name, classLoader);
        }
        return desiredAssertionStatus(name, classLoader);
    }

    private boolean desiredHostedAssertionStatus(String name, ClassLoader classLoader) {
        /* Hosted class-loader support distinguishes platform loaders from application loaders. */
        boolean nativeImageClassLoader = ImageSingletons.lookup(ClassLoaderSupport.class).isNativeImageClassLoader(classLoader);
        return lookupAssertionStatus(buildTimePackageAssertionStatus, buildTimeClassAssertionStatus, name,
                        nativeImageClassLoader ? buildTimeDefaultAssertionStatus : buildTimeSystemAssertionStatus);
    }

    private boolean desiredRuntimeBuildTimeAssertionStatus(String name, ClassLoader classLoader) {
        /* The runtime loader distinction matches ClassLoader's bootstrap versus application split. */
        boolean nativeImageClassLoader = classLoader != null;
        return lookupAssertionStatus(buildTimePackageAssertionStatus, buildTimeClassAssertionStatus, name,
                        nativeImageClassLoader ? buildTimeDefaultAssertionStatus : buildTimeSystemAssertionStatus);
    }

    /**
     * Determines whether assertions should be enabled for `clazz`.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean desiredAssertionStatus(Class<?> clazz) {
        return desiredAssertionStatus(clazz.getName(), clazz.getClassLoader());
    }

    /**
     * Creates assertion status directives using the array layout expected by
     * `java.lang.AssertionStatusDirectives`.
     */
    public ClassLoaderAssertionStatusDirectives createClassLoaderAssertionStatusDirectives() {
        Map<String, Boolean> classStatus = new HashMap<>(runtimeClassAssertionStatus);
        /*
         * Build-time initialized classes do not need entries in the runtime assertion map because
         * their assertion status was determined when they were initialized during image building.
         * The result of ClassLoader.desiredAssertionStatus for such a class does not
         * affect its behavior after image construction. Furthermore, the API for
         * ClassLoader.desiredAssertionStatus explicitly states that it is "not guaranteed
         * to return the actual assertion status that was (or will be) associated with
         * the specified class when it was (or will be) initialized."
         */

        String[] classes = new String[classStatus.size()];
        boolean[] classEnabled = new boolean[classStatus.size()];
        initializeDirectiveArrays(classStatus, classes, classEnabled);

        String[] packages = new String[runtimePackageAssertionStatus.size()];
        boolean[] packageEnabled = new boolean[runtimePackageAssertionStatus.size()];
        initializeDirectiveArrays(runtimePackageAssertionStatus, packages, packageEnabled);

        return new ClassLoaderAssertionStatusDirectives(classes, classEnabled, packages, packageEnabled, runtimeDefaultAssertionStatus);
    }

    /**
     * Copies assertion directive entries into the arrays expected by
     * `java.lang.AssertionStatusDirectives`.
     */
    private static void initializeDirectiveArrays(Map<String, Boolean> values, String[] keys, boolean[] enabled) {
        int index = 0;
        for (Map.Entry<String, Boolean> entry : values.entrySet()) {
            String key = entry.getKey();
            /* ClassLoader uses null, not the empty string, for the unnamed package entry. */
            keys[index] = key.isEmpty() ? null : key;
            enabled[index] = entry.getValue();
            index++;
        }
    }

    /// Gets the build-time default assertion status for non-system classes.
    public boolean getDefaultAssertionStatus() {
        return buildTimeDefaultAssertionStatus;
    }

    /// Gets the build-time default assertion status for system classes.
    public boolean getDefaultSystemAssertionStatus() {
        return buildTimeSystemAssertionStatus;
    }

    /**
     * Stores assertion status directives in the shape needed to initialize a `ClassLoader`.
     * Used to initialize a `java.lang.AssertionStatusDirectives` object.
     */
    public record ClassLoaderAssertionStatusDirectives(
                    String[] classes,
                    boolean[] classEnabled,
                    String[] packages,
                    boolean[] packageEnabled,
                    boolean deflt) {
    }

    /** Provides layered-image persistence checks for assertion settings. */
    static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
        /**
         * Gets the layered callbacks trait that persists and validates assertion settings.
         */
        @Override
        public LayeredCallbacksSingletonTrait getLayeredCallbacksTrait() {
            var action = new SingletonLayeredCallbacks<AssertionsSupport>() {
                @Override
                public LayeredPersistFlags doPersist(ImageSingletonWriter writer, AssertionsSupport singleton) {
                    persistAssertionStatus(writer, PACKAGE, singleton.buildTimePackageAssertionStatus);
                    persistAssertionStatus(writer, CLASS, singleton.buildTimeClassAssertionStatus);
                    writer.writeInt(DEFAULT_ASSERTION_STATUS, singleton.buildTimeDefaultAssertionStatus ? 1 : 0);
                    writer.writeInt(SYSTEM_ASSERTION_STATUS, singleton.buildTimeSystemAssertionStatus ? 1 : 0);
                    return LayeredPersistFlags.CALLBACK_ON_REGISTRATION;
                }

                private void persistAssertionStatus(ImageSingletonWriter writer, String type, Map<String, Boolean> assertionStatus) {
                    List<String> keys = new ArrayList<>();
                    List<Boolean> values = new ArrayList<>();
                    for (var entry : assertionStatus.entrySet()) {
                        keys.add(entry.getKey());
                        values.add(entry.getValue());
                    }
                    writer.writeStringList(type + ASSERTION_STATUS_KEYS, keys);
                    writer.writeBoolList(type + ASSERTION_STATUS_VALUES, values);
                }

                @Override
                public void onSingletonRegistration(ImageSingletonLoader loader, AssertionsSupport singleton) {
                    checkMaps(loadAssertionStatus(loader, PACKAGE), singleton.buildTimePackageAssertionStatus);
                    checkMaps(loadAssertionStatus(loader, CLASS), singleton.buildTimeClassAssertionStatus);
                    checkBoolean(singleton.buildTimeDefaultAssertionStatus, loader, DEFAULT_ASSERTION_STATUS);
                    checkBoolean(singleton.buildTimeSystemAssertionStatus, loader, SYSTEM_ASSERTION_STATUS);
                }

                private void checkBoolean(boolean currentLayerAssertionStatus, ImageSingletonLoader loader, String assertionStatusKey) {
                    boolean previousLayerStatus = loader.readInt(assertionStatusKey) == 1;
                    VMError.guarantee(currentLayerAssertionStatus == previousLayerStatus, "The assertion status is the previous layer was %s, but the assertion status in the current layer is %s",
                                    currentLayerAssertionStatus, previousLayerStatus);
                }

                private Map<String, Boolean> loadAssertionStatus(ImageSingletonLoader loader, String type) {
                    HashMap<String, Boolean> result = new HashMap<>();
                    var keys = loader.readStringList(type + ASSERTION_STATUS_KEYS);
                    var values = loader.readBoolList(type + ASSERTION_STATUS_VALUES);
                    for (int i = 0; i < keys.size(); ++i) {
                        result.put(keys.get(i), values.get(i));
                    }
                    return result;
                }

                public static <T, U> void checkMaps(Map<T, U> previousLayerMap, Map<T, U> currentLayerMap) {
                    VMError.guarantee(previousLayerMap.equals(currentLayerMap),
                                    "The assertion status maps should be the same across layers, but the map in previous layers is %s and the map in the current layer is %s",
                                    previousLayerMap, currentLayerMap);
                }
            };
            return new LayeredCallbacksSingletonTrait(action);
        }
    }
}

class AssertionsOptionTransformer implements Function<Object, Object> {

    private static final String SEPARATOR = ",";
    private final char prefix;

    /*
     * The option transformer gets built into the native-image image. Thus it cannot be HOSTED_ONLY.
     */
    AssertionsOptionTransformer(char prefix) {
        this.prefix = prefix;
    }

    @Override
    public Object apply(Object o) {
        StringJoiner joiner = new StringJoiner(SEPARATOR);
        for (String entry : o.toString().split(SEPARATOR)) {
            String s = prefix + entry;
            joiner.add(s);
        }
        return joiner.toString();
    }

    static class Enable extends AssertionsOptionTransformer {
        Enable() {
            super(AssertionsSupport.ENABLE_PREFIX);
        }
    }

    static class Disable extends AssertionsOptionTransformer {
        Disable() {
            super(AssertionsSupport.DISABLE_PREFIX);
        }
    }
}
