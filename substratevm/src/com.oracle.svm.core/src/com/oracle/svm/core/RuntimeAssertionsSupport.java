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

import com.oracle.svm.shared.option.APIOption;
import com.oracle.svm.shared.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.shared.singletons.ImageSingletonLoader;
import com.oracle.svm.shared.singletons.ImageSingletonWriter;
import com.oracle.svm.shared.singletons.LayeredPersistFlags;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.LayeredCallbacksSingletonTrait;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.Duplicable;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;

class RuntimeAssertionsOptionTransformer implements Function<Object, Object> {

    private static final String SEPARATOR = ",";
    private final char prefix;

    /*
     * The option transformer gets built into the native-image image. Thus it cannot be HOSTED_ONLY.
     */
    RuntimeAssertionsOptionTransformer(char prefix) {
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

    static class Enable extends RuntimeAssertionsOptionTransformer {
        Enable() {
            super(RuntimeAssertionsSupport.ENABLE_PREFIX);
        }
    }

    static class Disable extends RuntimeAssertionsOptionTransformer {
        Disable() {
            super(RuntimeAssertionsSupport.DISABLE_PREFIX);
        }
    }
}

/**
 * Records the assertion settings that are configured for a native image and answers assertion status
 * queries for classes that are available at run time.
 * <p>
 * Classes built into an image get their assertion status computed at build-time. For those classes,
 * native-image class loaders use the image default assertion status (i.e. as set by the {@code -ea}
 * and {@code -da} native-image options), while non-native-image class loaders use the
 * system assertion status ({@code -esa} and {@code -dsa} native-image options). This applies even
 * for image classes initialized at runtime.
 * <p>
 * Classes that are defined after image startup have their assertion status resolved at run time
 * from the runtime {@link ClassLoader} assertion maps when those maps have been initialized,
 * otherwise from the image-level class/package settings and the appropriate
 * default for either bootstrap-loaded or application-loaded classes.
 */
@AutomaticallyRegisteredImageSingleton
@SingletonTraits(access = AllAccess.class, layeredCallbacks = RuntimeAssertionsSupport.LayeredCallbacks.class, layeredInstallationKind = Duplicable.class)
public final class RuntimeAssertionsSupport {

    private static final String PACKAGE = "package";
    private static final String CLASS = "class";

    private static final String ASSERTION_STATUS_KEYS = "AssertionStatusKeys";
    private static final String ASSERTION_STATUS_VALUES = "AssertionStatusValues";

    private static final String DEFAULT_ASSERTION_STATUS = "defaultAssertionStatus";
    private static final String SYSTEM_ASSERTION_STATUS = "systemAssertionStatus";

    public static final char ENABLE_PREFIX = '+';
    public static final char DISABLE_PREFIX = '-';
    public static final String PACKAGE_SUFFIX = "...";

    /** Error text used when a runtime assertions option has an unrecognized prefix. */
    public static final String PREFIX_CHECK_MSG = "RuntimeAssertions value starts with `" + ENABLE_PREFIX + "` or `" + DISABLE_PREFIX + "`";
    /** Error text used when a runtime assertions option value is empty. */
    public static final String EMPTY_OPTION_VALUE_MSG = "Empty RuntimeAssertions option value";

    /** Defines native-image options that are compatible with standard java launcher assertion flags. */
    public static class Options {

        private static final char VALUE_SEPARATOR = ':';

        /** Enables or disables Java assertion status at run time for classes, packages, or all code. */
        @APIOption(name = {"-ea", "-enableassertions"}, launcherOption = true, valueSeparator = VALUE_SEPARATOR, valueTransformer = RuntimeAssertionsOptionTransformer.Enable.class, defaultValue = "", //
                        customHelp = "also -ea[:[packagename]...|:classname] or -enableassertions[:[packagename]...|:classname]. Enable assertions with specified granularity at run time.")//
        @APIOption(name = {"-da",
                        "-disableassertions"}, launcherOption = true, valueSeparator = VALUE_SEPARATOR, valueTransformer = RuntimeAssertionsOptionTransformer.Disable.class, defaultValue = "", //
                        customHelp = "also -da[:[packagename]...|:classname] or -disableassertions[:[packagename]...|:classname]. Disable assertions with specified granularity at run time.")//
        @Option(help = "Enable or disable Java assert statements at run time") //
        public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> RuntimeAssertions = new HostedOptionKey<>(AccumulatingLocatableMultiOptionValue.Strings.build());

        /** Enables or disables Java assertion status at run time for bootstrap-loaded classes. */
        @APIOption(name = {"-esa", "-enablesystemassertions"}, launcherOption = true, customHelp = "also -enablesystemassertions. Enables assertions in all system classes at run time.") //
        @APIOption(name = {"-dsa", "-disablesystemassertions"}, launcherOption = true, kind = APIOption.APIOptionKind.Negated, //
                        customHelp = "also -disablesystemassertions. Disables assertions in all system classes at run time.") //
        @Option(help = "Enable or disable Java system assertions at run time") //
        public static final HostedOptionKey<Boolean> RuntimeSystemAssertions = new HostedOptionKey<>(false);
    }

    @Fold
    public static RuntimeAssertionsSupport singleton() {
        return ImageSingletons.lookup(RuntimeAssertionsSupport.class);
    }

    private final Map<String, Boolean> packageAssertionStatus;
    private final Map<String, Boolean> classAssertionStatus;
    private final boolean defaultAssertionStatus;
    private final boolean systemAssertionStatus;

    /**
     * Creates assertion support from the configured native-image assertion options.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    protected RuntimeAssertionsSupport() {
        packageAssertionStatus = new HashMap<>();
        classAssertionStatus = new HashMap<>();
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
            boolean isPackage = classOrPackage.endsWith(PACKAGE_SUFFIX);
            if (isPackage) {
                String packageStr = classOrPackage.substring(0, classOrPackage.length() - PACKAGE_SUFFIX.length());
                packageAssertionStatus.put(packageStr, enable);
            } else {
                if (classOrPackage.isEmpty()) {
                    tmpDefaultAssertionStatus = enable;
                } else {
                    classAssertionStatus.put(classOrPackage, enable);
                }
            }
        }

        defaultAssertionStatus = tmpDefaultAssertionStatus;
        systemAssertionStatus = Options.RuntimeSystemAssertions.getValue();
    }

    /**
     * Looks up assertion status with the same class and package precedence as
     * {@code ClassLoader#desiredAssertionStatus(java.lang.String)}.
     */
    private boolean lookupAssertionStatus(String name, boolean fallback) {
        String className = name;
        // Check for a class entry
        Boolean result = classAssertionStatus.get(className);
        if (result != null) {
            return result;
        }

        // Check for most specific package entry
        int dotIndex = className.lastIndexOf('.');
        if (dotIndex < 0) {
            /* Image-level maps use an empty string for the unnamed package. */
            result = packageAssertionStatus.get("");
            if (result != null) {
                return result;
            }
        }
        while (dotIndex > 0) {
            className = className.substring(0, dotIndex);
            result = packageAssertionStatus.get(className);
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
            /*
             * At build-time, only classes loaded by a native-image class loader are
             * considered as app classes governed by `-ea`. All other classes are
             * system classes governed by `-esa`.
             */
            boolean nativeImageClassLoader = ImageSingletons.lookup(ClassLoaderSupport.class).isNativeImageClassLoader(classLoader);
            return lookupAssertionStatus(name, nativeImageClassLoader ? defaultAssertionStatus : systemAssertionStatus);
        }
        /* The bootstrap loader is represented as null and uses the system assertion status. */
        return lookupAssertionStatus(name, classLoader == null ? systemAssertionStatus : defaultAssertionStatus);
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
        String[] classes = new String[classAssertionStatus.size()];
        boolean[] classEnabled = new boolean[classAssertionStatus.size()];
        initializeDirectiveArrays(classAssertionStatus, classes, classEnabled);

        String[] packages = new String[packageAssertionStatus.size()];
        boolean[] packageEnabled = new boolean[packageAssertionStatus.size()];
        initializeDirectiveArrays(packageAssertionStatus, packages, packageEnabled);

        return new ClassLoaderAssertionStatusDirectives(classes, classEnabled, packages, packageEnabled, defaultAssertionStatus);
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

    /**
     * Gets the default assertion status for non-system classes.
     */
    public boolean getDefaultAssertionStatus() {
        return defaultAssertionStatus;
    }

    /**
     * Gets the default assertion status for system classes.
     */
    public boolean getDefaultSystemAssertionStatus() {
        return systemAssertionStatus;
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
            var action = new SingletonLayeredCallbacks<RuntimeAssertionsSupport>() {
                @Override
                public LayeredPersistFlags doPersist(ImageSingletonWriter writer, RuntimeAssertionsSupport singleton) {
                    persistAssertionStatus(writer, PACKAGE, singleton.packageAssertionStatus);
                    persistAssertionStatus(writer, CLASS, singleton.classAssertionStatus);
                    writer.writeInt(DEFAULT_ASSERTION_STATUS, singleton.defaultAssertionStatus ? 1 : 0);
                    writer.writeInt(SYSTEM_ASSERTION_STATUS, singleton.systemAssertionStatus ? 1 : 0);
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
                public void onSingletonRegistration(ImageSingletonLoader loader, RuntimeAssertionsSupport singleton) {
                    checkMaps(loadAssertionStatus(loader, PACKAGE), singleton.packageAssertionStatus);
                    checkMaps(loadAssertionStatus(loader, CLASS), singleton.classAssertionStatus);
                    checkBoolean(singleton.defaultAssertionStatus, loader, DEFAULT_ASSERTION_STATUS);
                    checkBoolean(singleton.systemAssertionStatus, loader, SYSTEM_ASSERTION_STATUS);
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
