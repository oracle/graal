/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.classinitialization;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;

import org.graalvm.collections.Pair;

import com.oracle.svm.core.option.OptionOrigin;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;

/**
 * The initialization kind for a class. The order of the enum values matters, {@link #max} depends
 * on it.
 */
enum InitKind {
    /** Class is initialized during image building, so it is already initialized at runtime. */
    BUILD_TIME,
    /** Class is initialized both at runtime and during image building. */
    RERUN,
    /** Class should be initialized at runtime and not during image building. */
    RUN_TIME;

    InitKind max(InitKind other) {
        return this.ordinal() > other.ordinal() ? this : other;
    }

    InitKind min(InitKind other) {
        return this.ordinal() < other.ordinal() ? this : other;
    }

    boolean isRunTime() {
        return this.equals(RUN_TIME);
    }

    public static final String SEPARATOR = ":";

    String suffix() {
        return SEPARATOR + name().toLowerCase();
    }

    Consumer<String> stringConsumer(ClassInitializationSupport support, OptionOrigin origin) {
        if (this == RUN_TIME) {
            return name -> support.initializeAtRunTime(name, reason(origin, name));
        } else if (this == RERUN) {
            return name -> support.rerunInitialization(name, reason(origin, name));
        } else {
            return name -> {
                if (name.equals("") && !origin.commandLineLike()) {
                    String msg = "--initialize-at-build-time without arguments is not allowed." + System.lineSeparator() +
                                    "Origin of the option: " + origin + System.lineSeparator() +
                                    "The reason for deprecation is that --initalize-at-build-time does not compose, i.e., a single library can make assumptions that the whole classpath can be safely initialized at build time;" +
                                    " that assumption is often incorrect.";
                    if (ClassInitializationOptions.AllowDeprecatedInitializeAllClassesAtBuildTime.getValue()) {
                        System.out.println("Warning: " + msg);
                    } else {
                        throw UserError.abort("%s%nAs a workaround, %s allows turning this error into a warning. Note that this option is deprecated and will be removed in a future version.", msg,
                                        SubstrateOptionsParser.commandArgument(ClassInitializationOptions.AllowDeprecatedInitializeAllClassesAtBuildTime, "+"));
                    }
                }
                support.initializeAtBuildTime(name, reason(origin, name));
            };
        }
    }

    private static String reason(OptionOrigin origin, String name) {
        return "from " + origin + " with '" + name + "'";
    }

    static Pair<String, InitKind> strip(String input) {
        Optional<InitKind> it = Arrays.stream(values()).filter(x -> input.endsWith(x.suffix())).findAny();
        assert it.isPresent();
        return Pair.create(input.substring(0, input.length() - it.get().suffix().length()), it.get());
    }

}
