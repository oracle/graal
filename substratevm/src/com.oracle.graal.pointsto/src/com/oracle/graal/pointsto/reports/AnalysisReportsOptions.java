/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.reports;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.options.EnumOptionKey;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;

import static com.oracle.graal.pointsto.api.PointstoOptions.TrackAccessChain;

public class AnalysisReportsOptions {

    @Option(help = "Print analysis results statistics.")//
    public static final OptionKey<Boolean> PrintAnalysisStatistics = new OptionKey<>(false);

    @Option(help = "Analysis results statistics file.")//
    public static final OptionKey<String> AnalysisStatisticsFile = new OptionKey<>(null);

    @Option(help = "Print analysis call tree, a breadth-first tree reduction of the call graph.")//
    public static final OptionKey<Boolean> PrintAnalysisCallTree = new OptionKey<>(false);

    @Option(help = "Print call edges with other analysis results statistics.")//
    public static final OptionKey<Boolean> PrintCallEdges = new OptionKey<>(false) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
            if (newValue) {
                PrintAnalysisStatistics.update(values, true);
                TrackAccessChain.update(values, true);
            }
        }
    };

    @Option(help = "Change the output format of the analysis call tree, available options are TXT and CSV. See: Reports.md.")//
    public static final EnumOptionKey<CallTreeType> PrintAnalysisCallTreeType = new EnumOptionKey<>(CallTreeType.TXT) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, CallTreeType oldValue, CallTreeType newValue) {
            super.onValueUpdate(values, oldValue, newValue);
            PrintAnalysisCallTree.update(values, true);
        }
    };

    @Option(help = "Print image object hierarchy.")//
    public static final OptionKey<Boolean> PrintImageObjectTree = new OptionKey<>(false);

    @Option(help = "Override the default suppression of specified roots. See: Reports.md.")//
    public static final OptionKey<String> ImageObjectTreeExpandRoots = new OptionKey<>("");

    @Option(help = "Suppress the expansion of specified roots. See: Reports.md.")//
    public static final OptionKey<String> ImageObjectTreeSuppressRoots = new OptionKey<>("");

    @Option(help = "Override the default suppression of specified types. See: Reports.md.")//
    public static final OptionKey<String> ImageObjectTreeExpandTypes = new OptionKey<>("");

    @Option(help = "Suppress the expansion of specified types. See: Reports.md.")//
    public static final OptionKey<String> ImageObjectTreeSuppressTypes = new OptionKey<>("");

    enum CallTreeType {
        TXT,
        CSV;
    }
}
