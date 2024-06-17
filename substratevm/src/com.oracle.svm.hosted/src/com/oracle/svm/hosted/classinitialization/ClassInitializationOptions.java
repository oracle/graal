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

import java.util.function.Function;

import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;

public final class ClassInitializationOptions {

    public static final String SEPARATOR = ":";
    public static final String SUFFIX_BUILD_TIME = SEPARATOR + "build_time";
    public static final String SUFFIX_RUN_TIME = SEPARATOR + "run_time";

    private static class InitializationValueTransformer implements Function<Object, Object> {
        private final String suffix;

        InitializationValueTransformer(String suffix) {
            this.suffix = suffix;
        }

        @Override
        public Object apply(Object o) {
            String[] elements = o.toString().split(",");
            if (elements.length == 0) {
                return suffix;
            }
            String[] results = new String[elements.length];
            for (int i = 0; i < elements.length; i++) {
                results[i] = elements[i] + suffix;
            }
            return String.join(",", results);
        }
    }

    private static class InitializationValueRunTime extends InitializationValueTransformer {
        InitializationValueRunTime() {
            super(SUFFIX_RUN_TIME);
        }
    }

    private static class InitializationValueBuildTime extends InitializationValueTransformer {
        InitializationValueBuildTime() {
            super(SUFFIX_BUILD_TIME);
        }
    }

    @APIOption(name = "initialize-at-run-time", valueTransformer = InitializationValueRunTime.class, defaultValue = "", //
                    customHelp = "A comma-separated list of packages and classes (and implicitly all of their subclasses) that must be initialized at runtime and not during image building. An empty string is currently not supported.")//
    @APIOption(name = "initialize-at-build-time", valueTransformer = InitializationValueBuildTime.class, defaultValue = "", //
                    customHelp = "A comma-separated list of packages and classes (and implicitly all of their superclasses) that are initialized during image generation. An empty string designates all packages.")//
    @APIOption(name = "delay-class-initialization-to-runtime", valueTransformer = InitializationValueRunTime.class, deprecated = "Use --initialize-at-run-time.", defaultValue = "")//
    @APIOption(name = "rerun-class-initialization-at-runtime", valueTransformer = InitializationValueRunTime.class, deprecated = "Equivalent to --initialize-at-run-time.", defaultValue = "") //
    @Option(help = "A comma-separated list of classes appended with their initialization strategy ('" + SUFFIX_BUILD_TIME + "' or '" + SUFFIX_RUN_TIME + "')", type = OptionType.User)//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> ClassInitialization = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.build());

    @Option(help = "Instead of abort, only warn if --initialize-at-build-time= is used.", type = OptionType.Debug, //
                    deprecated = true, deprecationMessage = "This option was introduced to simplify migration to GraalVM 23.0 and will be removed in a future release")//
    public static final HostedOptionKey<Boolean> AllowDeprecatedInitializeAllClassesAtBuildTime = new HostedOptionKey<>(false);

    @Option(help = "Prints class initialization info for all classes detected by analysis.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> PrintClassInitialization = new HostedOptionKey<>(false);

    @Option(help = "Assert class initialization is specified for all classes.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> AssertInitializationSpecifiedForAllClasses = new HostedOptionKey<>(false);

    @APIOption(name = "strict-image-heap", deprecated = "'--strict-image-heap' is now the default. You can remove the option.") //
    @Option(help = "Deprecated, option no longer has any effect.", deprecated = true, deprecationMessage = "It no longer has any effect, and no replacement is available")//
    static final HostedOptionKey<Boolean> StrictImageHeap = new HostedOptionKey<>(true);

    @Option(help = "Simulate the effects of class initializer at image build time, to avoid class initialization at run time.", type = OptionType.Expert)//
    public static final HostedOptionKey<Boolean> SimulateClassInitializer = new HostedOptionKey<>(true);

    @Option(help = "Configuration for SimulateClassInitializer: Collect all reasons why a class initializer cannot be simulated.", type = OptionType.Expert)//
    static final HostedOptionKey<Boolean> SimulateClassInitializerCollectAllReasons = new HostedOptionKey<>(false);

    @Option(help = "Configuration for SimulateClassInitializer: Maximum inlining depth during simulation.", type = OptionType.Expert)//
    static final HostedOptionKey<Integer> SimulateClassInitializerMaxInlineDepth = new HostedOptionKey<>(200);

    @Option(help = "Configuration for SimulateClassInitializer: Maximum number of loop iterations that are unrolled during simulation.", type = OptionType.Expert)//
    static final HostedOptionKey<Integer> SimulateClassInitializerMaxLoopIterations = new HostedOptionKey<>(2_000);

    @Option(help = "Configuration for SimulateClassInitializer: Maximum number of bytes allocated in the image heap for each class initializer.", type = OptionType.Expert)//
    static final HostedOptionKey<Integer> SimulateClassInitializerMaxAllocatedBytes = new HostedOptionKey<>(40_000);
}
