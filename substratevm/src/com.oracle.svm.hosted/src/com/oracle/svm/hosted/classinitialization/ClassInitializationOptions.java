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

import java.util.Locale;
import java.util.function.Function;

import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.util.LogUtils;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;

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
            super(RUN_TIME.name().toLowerCase(Locale.ROOT));
        }
    }

    private static class InitializationValueRerun extends InitializationValueTransformer {
        InitializationValueRerun() {
            super(RERUN.name().toLowerCase(Locale.ROOT));
        }
    }

    private static class InitializationValueEager extends InitializationValueTransformer {
        InitializationValueEager() {
            super(BUILD_TIME.name().toLowerCase(Locale.ROOT));
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
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> ClassInitialization = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.build());

    @Option(help = "Instead of abort, only warn if --initialize-at-build-time= is used.", type = OptionType.Debug, //
                    deprecated = true, deprecationMessage = "This option was introduced to simplify migration to GraalVM 23.0 and will be removed in a future release")//
    public static final HostedOptionKey<Boolean> AllowDeprecatedInitializeAllClassesAtBuildTime = new HostedOptionKey<>(false);

    @Option(help = "Prints class initialization info for all classes detected by analysis.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> PrintClassInitialization = new HostedOptionKey<>(false);

    @Option(help = "Assert class initialization is specified for all classes.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> AssertInitializationSpecifiedForAllClasses = new HostedOptionKey<>(false);

    @APIOption(name = "strict-image-heap", deprecated = "'--strict-image-heap' is now the default. You can remove the option.") //
    @Option(help = "Enable the strict image heap mode that allows all classes to be used at build-time but also requires types of all objects in the heap to be explicitly marked for build-time initialization.", //
                    type = OptionType.User, deprecated = true, deprecationMessage = "This option was introduced to simplify migration to GraalVM 24.0 and will be removed in a future release") //
    public static final HostedOptionKey<Boolean> StrictImageHeap = new HostedOptionKey<>(true, k -> {
        if (k.hasBeenSet() && Boolean.FALSE.equals(k.getValue())) {
            LogUtils.warning("The non-strict image heap mode should be avoided as it is deprecated and marked for removal.");
        }
    });

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
