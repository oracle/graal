/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;

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

@Platforms(Platform.HOSTED_ONLY.class)
public final class RuntimeAssertionsSupport {

    public static final char ENABLE_PREFIX = '+';
    public static final char DISABLE_PREFIX = '-';
    public static final String PACKAGE_SUFFIX = "...";

    public static final String PREFIX_CHECK_MSG = "RuntimeAssertions value starts with `" + ENABLE_PREFIX + "` or `" + DISABLE_PREFIX + "`";
    public static final String EMPTY_OPTION_VALUE_MSG = "Empty RuntimeAssertions option value";

    public static class Options {

        private static final char VALUE_SEPARATOR = ':';

        @APIOption(name = {"-ea", "-enableassertions"}, valueSeparator = VALUE_SEPARATOR, valueTransformer = RuntimeAssertionsOptionTransformer.Enable.class, defaultValue = "", //
                        customHelp = "also -ea[:[packagename]...|:classname] or -enableassertions[:[packagename]...|:classname]. Enable assertions with specified granularity.")//
        @APIOption(name = {"-da", "-disableassertions"}, valueSeparator = VALUE_SEPARATOR, valueTransformer = RuntimeAssertionsOptionTransformer.Disable.class, defaultValue = "", //
                        customHelp = "also -da[:[packagename]...|:classname] or -disableassertions[:[packagename]...|:classname]. Disable assertions with specified granularity.")//
        @Option(help = "Enable or disable Java assert statements at run time") //
        public static final HostedOptionKey<String[]> RuntimeAssertions = new HostedOptionKey<>(new String[0]);

        @APIOption(name = {"-esa", "-enablesystemassertions"}, customHelp = "also -enablesystemassertions. Enables assertions in all system classes.") //
        @APIOption(name = {"-dsa", "-disablesystemassertions"}, kind = APIOption.APIOptionKind.Negated, customHelp = "also -disablesystemassertions. Disables assertions in all system classes.") //
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
        String[] runtimeAssertionsOptions = Options.RuntimeAssertions.getValue();

        packageAssertionStatus = new HashMap<>();
        classAssertionStatus = new HashMap<>();
        boolean tmpDefaultAssertionStatus = false;

        for (String option : runtimeAssertionsOptions) {
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
        boolean isNativeImageClassLoader = ImageSingletons.lookup(ClassLoaderQuery.class).isNativeImageClassLoader(classLoader);
        return desiredAssertionStatusImpl(name, isNativeImageClassLoader ? defaultAssertionStatus : systemAssertionStatus);
    }

    public boolean desiredAssertionStatus(Class<?> clazz) {
        return desiredAssertionStatusImpl(clazz.getName(), clazz.getClassLoader());
    }
}

@AutomaticFeature
class RuntimeAssertionsFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(RuntimeAssertionsSupport.class, new RuntimeAssertionsSupport());
    }
}
