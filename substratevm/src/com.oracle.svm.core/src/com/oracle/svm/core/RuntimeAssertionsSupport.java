/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton.PersistFlags;
import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.SingletonLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTrait;
import com.oracle.svm.core.traits.SingletonTraitKind;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.util.VMError;

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

@AutomaticallyRegisteredImageSingleton
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = RuntimeAssertionsSupport.LayeredCallbacks.class, layeredInstallationKind = Independent.class)
@Platforms(Platform.HOSTED_ONLY.class)
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

    public static final String PREFIX_CHECK_MSG = "RuntimeAssertions value starts with `" + ENABLE_PREFIX + "` or `" + DISABLE_PREFIX + "`";
    public static final String EMPTY_OPTION_VALUE_MSG = "Empty RuntimeAssertions option value";

    public static class Options {

        private static final char VALUE_SEPARATOR = ':';

        @APIOption(name = {"-ea", "-enableassertions"}, launcherOption = true, valueSeparator = VALUE_SEPARATOR, valueTransformer = RuntimeAssertionsOptionTransformer.Enable.class, defaultValue = "", //
                        customHelp = "also -ea[:[packagename]...|:classname] or -enableassertions[:[packagename]...|:classname]. Enable assertions with specified granularity at run time.")//
        @APIOption(name = {"-da",
                        "-disableassertions"}, launcherOption = true, valueSeparator = VALUE_SEPARATOR, valueTransformer = RuntimeAssertionsOptionTransformer.Disable.class, defaultValue = "", //
                        customHelp = "also -da[:[packagename]...|:classname] or -disableassertions[:[packagename]...|:classname]. Disable assertions with specified granularity at run time.")//
        @Option(help = "Enable or disable Java assert statements at run time") //
        public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> RuntimeAssertions = new HostedOptionKey<>(AccumulatingLocatableMultiOptionValue.Strings.build());

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
     * Same algorithm as {@code ClassLoader#desiredAssertionStatus(java.lang.String)}. Kept in sync.
     */
    private boolean desiredAssertionStatusImpl(String name, boolean fallback) {
        String className = name;
        // Check for a class entry
        Boolean result = classAssertionStatus.get(className);
        if (result != null) {
            return result;
        }

        // Check for most specific package entry
        int dotIndex = className.lastIndexOf('.');
        if (dotIndex < 0) {
            /* default package (represented as "") */
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

    private boolean desiredAssertionStatusImpl(String name, ClassLoader classLoader) {
        boolean isNativeImageClassLoader = ImageSingletons.lookup(ClassLoaderSupport.class).isNativeImageClassLoader(classLoader);
        return desiredAssertionStatusImpl(name, isNativeImageClassLoader ? defaultAssertionStatus : systemAssertionStatus);
    }

    public boolean desiredAssertionStatus(Class<?> clazz) {
        return desiredAssertionStatusImpl(clazz.getName(), clazz.getClassLoader());
    }

    public boolean getDefaultAssertionStatus() {
        return defaultAssertionStatus;
    }

    public boolean getDefaultSystemAssertionStatus() {
        return systemAssertionStatus;
    }

    static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
        @Override
        public SingletonTrait getLayeredCallbacksTrait() {
            SingletonLayeredCallbacks action = new SingletonLayeredCallbacks() {
                @Override
                public LayeredImageSingleton.PersistFlags doPersist(ImageSingletonWriter writer, Object singleton) {
                    RuntimeAssertionsSupport runtimeAssertionsSupport = (RuntimeAssertionsSupport) singleton;
                    persistAssertionStatus(writer, PACKAGE, runtimeAssertionsSupport.packageAssertionStatus);
                    persistAssertionStatus(writer, CLASS, runtimeAssertionsSupport.classAssertionStatus);
                    writer.writeInt(DEFAULT_ASSERTION_STATUS, runtimeAssertionsSupport.defaultAssertionStatus ? 1 : 0);
                    writer.writeInt(SYSTEM_ASSERTION_STATUS, runtimeAssertionsSupport.systemAssertionStatus ? 1 : 0);
                    return PersistFlags.CALLBACK_ON_REGISTRATION;
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
                public void onSingletonRegistration(ImageSingletonLoader loader, Object singleton) {
                    RuntimeAssertionsSupport runtimeAssertionsSupport = (RuntimeAssertionsSupport) singleton;
                    checkMaps(loadAssertionStatus(loader, PACKAGE), runtimeAssertionsSupport.packageAssertionStatus);
                    checkMaps(loadAssertionStatus(loader, CLASS), runtimeAssertionsSupport.classAssertionStatus);
                    checkBoolean(runtimeAssertionsSupport.defaultAssertionStatus, loader, DEFAULT_ASSERTION_STATUS);
                    checkBoolean(runtimeAssertionsSupport.systemAssertionStatus, loader, SYSTEM_ASSERTION_STATUS);
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
            return new SingletonTrait(SingletonTraitKind.LAYERED_CALLBACKS, action);
        }
    }
}
