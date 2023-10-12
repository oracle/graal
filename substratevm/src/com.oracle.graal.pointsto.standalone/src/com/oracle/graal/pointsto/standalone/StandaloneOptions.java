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

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;

import java.nio.file.Path;
import java.nio.file.Paths;

public class StandaloneOptions {

    @Option(help = "File system splitor separated classpath for the analysis target application.")//
    public static final OptionKey<String> AnalysisTargetAppCP = new OptionKey<>(null);

    @Option(help = "File system splitor separated module path for the analysis target application.")//
    public static final OptionKey<String> AnalysisTargetAppModulePath = new OptionKey<>(null);

    @Option(help = "file:doc-files/AnalysisEntryPointsFileHelp.txt")//
    public static final OptionKey<String> AnalysisEntryPointsFile = new OptionKey<>(null);

    @Option(help = "Directory of analysis reports to be generated", type = OptionType.User)//
    public static final OptionKey<String> ReportsPath = new OptionKey<>("./");

    public static Path reportsPath(OptionValues options, String relativePath) {
        return Paths.get(Paths.get(ReportsPath.getValue(options)).toString(), relativePath).normalize().toAbsolutePath();
    }

    @Option(help = "Print the unresolved class warnings, disabled by default.")//
    public static final OptionKey<Boolean> PrintUnresolvedElementWarning = new OptionKey<>(false);

    @Option(help = "Enable eager class initialization analysis.")//
    public static final OptionKey<Boolean> EnableClassInitializationAnalyze = new OptionKey<>(false);

    @Option(help = "Directory for temporary files generated during native image generation. If this option is specified, the temporary files are not deleted so that you can inspect them after native image generation")//
    public static final OptionKey<String> AnalysisTempDirectory = new OptionKey<>("");
}
