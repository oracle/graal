/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.util.StringUtil;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;

/**
 * Enables the --future-defaults=value flag that is used for evolution of Native Image semantics.
 * Each enabled future default also sets a build-time, and run-time property named
 * <code>'{@value #SYSTEM_PROPERTY_PREFIX}{@literal <property-name>}'</code> to <code>true</code>.
 * That property can be used by the community to adjust their code to work both with the previous
 * and with the new behavior of Native Image.
 * </p>
 * Note 1: What makes it into the future-defaults must not be an experimental feature that can be
 * rolled back. The changes must be aligning native image with the Java spec and must be thoroughly
 * reviewed.
 * </p>
 * Note 2: future defaults can not be simply removed as user code can depend on the system property
 * values that are set by the option. When removing a future-default option, one has to leave the
 * system property both a build time and at run time set to <code>true</code>.
 */
public class FutureDefaultsOptions {
    private static final String OPTION_NAME = "future-defaults";

    private static final String DEFAULT_NAME = "<default-value>";
    private static final String ALL_NAME = "all";
    private static final String NONE_NAME = "none";
    private static final String RUN_TIME_INITIALIZE_JDK_NAME = "run-time-initialized-jdk";
    private static final String TREAT_NAME_AS_TYPE_NAME = "treat-name-as-type";

    public static final String RUN_TIME_INITIALIZE_JDK_REASON = "Initialize JDK classes at run time (--" + OPTION_NAME + " includes " + RUN_TIME_INITIALIZE_JDK_NAME + ")";

    public static final String SYSTEM_PROPERTY_PREFIX = ImageInfo.PROPERTY_NATIVE_IMAGE_PREFIX + OPTION_NAME + ".";

    private static final Set<String> ALL_FUTURE_DEFAULTS = Set.of(RUN_TIME_INITIALIZE_JDK_NAME, TREAT_NAME_AS_TYPE_NAME);
    private static final Set<String> ALL_COMMANDS = Set.of(ALL_NAME, NONE_NAME);

    private static String futureDefaultsAllValues() {
        return StringUtil.joinSingleQuoted(getAllValues());
    }

    private static Set<String> getAllValues() {
        Set<String> result = new LinkedHashSet<>(ALL_FUTURE_DEFAULTS.size() + ALL_COMMANDS.size());
        result.addAll(ALL_FUTURE_DEFAULTS);
        result.addAll(ALL_COMMANDS);
        return result;
    }

    static {
        assert getAllValues().stream().allMatch(futureDefaultsAllValues()::contains) : "A value is missing in the user-facing help text";
    }

    @APIOption(name = OPTION_NAME, defaultValue = DEFAULT_NAME) //
    @Option(help = "file:doc-files/FutureDefaultsHelp.txt", type = OptionType.User) //
    static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> FutureDefaults = new HostedOptionKey<>(
                    AccumulatingLocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

    private static Set<String> futureDefaults;

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void parseAndVerifyOptions() {
        futureDefaults = new LinkedHashSet<>(getAllValues().size());
        var valuesWithOrigin = FutureDefaults.getValue().getValuesWithOrigins();
        valuesWithOrigin.forEach(valueWithOrigin -> {
            String value = valueWithOrigin.value();
            if (DEFAULT_NAME.equals(value)) {
                throw UserError.abort("The '%s' from %s is forbidden. It can only contain: %s.",
                                SubstrateOptionsParser.commandArgument(FutureDefaults, DEFAULT_NAME),
                                valueWithOrigin.origin(),
                                futureDefaultsAllValues());
            }

            if (!getAllValues().contains(value)) {
                throw UserError.abort("The '%s' option from %s contains invalid value '%s'. It can only contain: %s.",
                                SubstrateOptionsParser.commandArgument(FutureDefaults, value),
                                valueWithOrigin.origin(),
                                value,
                                futureDefaultsAllValues());
            }

            if (value.equals(NONE_NAME)) {
                if (!valueWithOrigin.origin().commandLineLike()) {
                    throw UserError.abort("The '%s' option can only be used from the command line. Detected usage from %s.",
                                    SubstrateOptionsParser.commandArgument(FutureDefaults, NONE_NAME),
                                    valueWithOrigin.origin());
                }
                futureDefaults.clear();
            }

            if (value.equals(ALL_NAME)) {
                futureDefaults.addAll(ALL_FUTURE_DEFAULTS);
            } else {
                futureDefaults.add(value);
            }
        });

        /* Set build-time properties for user features */
        for (String futureDefault : getFutureDefaults()) {
            System.setProperty(FutureDefaultsOptions.SYSTEM_PROPERTY_PREFIX + futureDefault, Boolean.TRUE.toString());
        }
    }

    public static Set<String> getFutureDefaults() {
        return Collections.unmodifiableSet(Objects.requireNonNull(futureDefaults, "must be initialized before usage"));
    }

    public static boolean allFutureDefaults() {
        return getFutureDefaults().containsAll(ALL_FUTURE_DEFAULTS);
    }

    public static boolean isJDKInitializedAtRunTime() {
        return getFutureDefaults().contains(RUN_TIME_INITIALIZE_JDK_NAME);
    }

    public static boolean treatNameAsType() {
        return getFutureDefaults().contains(TREAT_NAME_AS_TYPE_NAME);
    }
}
