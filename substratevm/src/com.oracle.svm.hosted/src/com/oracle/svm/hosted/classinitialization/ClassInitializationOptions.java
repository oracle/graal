/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.hosted.classinitialization.InitKind.BUILD_TIME;
import static com.oracle.svm.hosted.classinitialization.InitKind.RERUN;
import static com.oracle.svm.hosted.classinitialization.InitKind.RUN_TIME;
import static com.oracle.svm.hosted.classinitialization.InitKind.SEPARATOR;

import java.util.function.Function;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;

import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;

public final class ClassInitializationOptions {

    private static class InitializationValueTransformer implements Function<Object, Object> {
        private final String val;

        InitializationValueTransformer(String val) {
            this.val = val;
        }

        @Override
        public Object apply(Object o) {
            String[] elements = o.toString().split(",");
            if (elements.length == 0) {
                return SEPARATOR + val;
            }
            String[] results = new String[elements.length];
            for (int i = 0; i < elements.length; i++) {
                results[i] = elements[i] + SEPARATOR + val;
            }
            return String.join(",", results);
        }
    }

    private static class InitializationValueDelay extends InitializationValueTransformer {
        InitializationValueDelay() {
            super(RUN_TIME.name().toLowerCase());
        }
    }

    private static class InitializationValueRerun extends InitializationValueTransformer {
        InitializationValueRerun() {
            super(RERUN.name().toLowerCase());
        }
    }

    private static class InitializationValueEager extends InitializationValueTransformer {
        InitializationValueEager() {
            super(BUILD_TIME.name().toLowerCase());
        }
    }

    @APIOption(name = "initialize-at-run-time", valueTransformer = InitializationValueDelay.class, defaultValue = "", //
                    customHelp = "A comma-separated list of packages and classes (and implicitly all of their subclasses) that must be initialized at runtime and not during image building. An empty string is currently not supported.")//
    @APIOption(name = "initialize-at-build-time", valueTransformer = InitializationValueEager.class, defaultValue = "", //
                    customHelp = "A comma-separated list of packages and classes (and implicitly all of their superclasses) that are initialized during image generation. An empty string designates all packages.")//
    @APIOption(name = "delay-class-initialization-to-runtime", valueTransformer = InitializationValueDelay.class, deprecated = "Use --initialize-at-run-time.", //
                    defaultValue = "", customHelp = "A comma-separated list of classes (and implicitly all of their subclasses) that are initialized at runtime and not during image building")//
    @APIOption(name = "rerun-class-initialization-at-runtime", valueTransformer = InitializationValueRerun.class, //
                    deprecated = "Currently there is no replacement for this option. Try using --initialize-at-run-time or use the non-API option -H:ClassInitialization directly.", //
                    defaultValue = "", customHelp = "A comma-separated list of classes (and implicitly all of their subclasses) that are initialized both at runtime and during image building") //
    @Option(help = "A comma-separated list of classes appended with their initialization strategy (':build_time', ':rerun', or ':run_time')", type = OptionType.User)//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> ClassInitialization = new HostedOptionKey<>(new LocatableMultiOptionValue.Strings());

    @Option(help = "Prints class initialization info for all classes detected by analysis.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> PrintClassInitialization = new HostedOptionKey<>(false);

    @Option(help = "Assert class initialization is specified for all classes.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> AssertInitializationSpecifiedForAllClasses = new HostedOptionKey<>(false);
}
