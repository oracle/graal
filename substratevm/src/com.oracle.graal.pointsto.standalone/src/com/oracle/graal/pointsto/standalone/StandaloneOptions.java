/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.oracle.svm.shared.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.shared.option.HostedOptionKey;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;

/**
 * Hosted options consumed by the standalone points-to entry point.
 */
public class StandaloneOptions {

    @Option(help = "File-system-separator-separated classpath for the analysis target application.")//
    public static final OptionKey<String> StandaloneAnalysisTargetAppCP = new OptionKey<>(null);

    @Option(help = """
                    Use a file to specify the analysis entry point methods.
                    These methods will be added as root methods for analysis besides
                    the main entry method (if set). At least one of this option and the main entry method must be set.
                    Each line of the file represents an entry point method. See MethodFilter option for method format details.
                    To specify a class initialization method, using <clinit> as the method name. E.g. C.<clinit> matches Class C's initialization
                    method.
                    Notice:
                    Although this option allows to specify any method, only direct methods can work as expected. Virtual call need allocation
                    information to bound to the actual implementations. But the allocation may be missed when the virtual call is the entry point.""")//
    public static final OptionKey<String> StandaloneAnalysisEntryPointsFile = new OptionKey<>(null);

    @Option(help = "Directory of analysis reports to be generated", type = OptionType.User)//
    public static final OptionKey<String> StandaloneAnalysisReportsPath = new OptionKey<>("./");

    /**
     * Prints a standalone summary of guest class initializations that failed and then fell back to
     * runtime handling during analysis.
     */
    @Option(help = "Prints standalone class-initialization failures that fall back to runtime handling, with the first stack trace per class.")//
    public static final HostedOptionKey<Boolean> StandalonePrintClassInitializationFailures = new HostedOptionKey<>(true);

    /**
     * Additional exact class names that standalone should force to runtime handling instead of
     * eager build-time initialization. Intended for standalone experiments and tests.
     */
    @Option(help = "Additional exact class names that standalone should keep runtime initialized for experiments.", type = OptionType.Debug)//
    public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> StandaloneExtraRuntimeInitializedClasses = new HostedOptionKey<>(
                    AccumulatingLocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

    /**
     * Additional package prefixes that standalone should force to runtime handling instead of
     * eager build-time initialization. Intended for standalone experiments and tests.
     */
    @Option(help = "Additional package prefixes that standalone should keep runtime initialized for experiments.", type = OptionType.Debug)//
    public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> StandaloneExtraRuntimeInitializedPackages = new HostedOptionKey<>(
                    AccumulatingLocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

    /**
     * Controls whether standalone analysis should assume a closed type world for dispatch and
     * saturation behavior.
     */
    @Option(help = "Controls whether standalone analysis uses closed-type-world assumptions for dispatch and flow saturation.")//
    public static final HostedOptionKey<Boolean> StandaloneClosedTypeWorld = new HostedOptionKey<>(true);

    public static Path reportsPath(OptionValues options, String relativePath) {
        return Paths.get(Paths.get(StandaloneAnalysisReportsPath.getValue(options)).toString(), relativePath).normalize().toAbsolutePath();
    }
}
