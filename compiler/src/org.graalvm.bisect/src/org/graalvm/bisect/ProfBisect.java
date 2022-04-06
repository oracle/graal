/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.bisect;

import java.io.IOException;

import org.graalvm.bisect.core.Experiment;
import org.graalvm.bisect.core.ExperimentId;
import org.graalvm.bisect.core.HotMethodPolicy;
import org.graalvm.bisect.matching.ExperimentMatcher;
import org.graalvm.bisect.parser.experiment.ExperimentFiles;
import org.graalvm.bisect.parser.experiment.ExperimentFilesImpl;
import org.graalvm.bisect.parser.experiment.ExperimentParser;
import org.graalvm.bisect.parser.experiment.ExperimentParserException;
import org.graalvm.bisect.parser.args.ArgumentParser;
import org.graalvm.bisect.parser.args.DoubleArgument;
import org.graalvm.bisect.parser.args.IntegerArgument;
import org.graalvm.bisect.parser.args.InvalidArgumentException;
import org.graalvm.bisect.parser.args.MissingArgumentException;
import org.graalvm.bisect.parser.args.StringArgument;
import org.graalvm.bisect.parser.args.UnknownArgumentException;

public class ProfBisect {
    public static void main(String[] args) {
        ArgumentParser argumentParser = new ArgumentParser(
                "mx profbisect", "Compares performed optimizations in hot methods of two experiments.");
        IntegerArgument hotMinArgument = argumentParser.addIntegerArgument(
                "--hotMinLimit", 1,
                "The minimum number of methods to mark as hot");
        IntegerArgument hotMaxArgument = argumentParser.addIntegerArgument(
                "--hotMaxLimit", 10,
                "The maximum number of methods to mark as hot");
        DoubleArgument percentileArgument = argumentParser.addDoubleArgument(
                "--hotPercentile", 0.9,
                "The percentile of the execution period that is spent executing hot methods");
        StringArgument proftoolArgument1 = argumentParser.addStringArgument(
                "proftoolOutput1", "Proftool output of the first experiment in JSON.");
        StringArgument optimizationLogArgument1 = argumentParser.addStringArgument(
                "optimizationLog1",
                "Directory with optimization logs for each method compiled in the first experiment.");
        StringArgument proftoolArgument2 = argumentParser.addStringArgument(
                "proftoolOutput2", "Proftool output of the second experiment in JSON.");
        StringArgument optimizationLogArgument2 = argumentParser.addStringArgument(
                "optimizationLog2",
                "Directory with optimization logs for each method compiled in the second experiment.");
        try {
            argumentParser.parse(args);
        } catch (InvalidArgumentException | MissingArgumentException | UnknownArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(argumentParser.createUsage());
            System.exit(1);
        }

        if (percentileArgument.getValue() > 1 || percentileArgument.getValue() < 0) {
            System.err.println("The hot method percentile must be in the range [0;1].");
            System.exit(1);
        }

        if (hotMinArgument.getValue() < 0 || hotMinArgument.getValue() > hotMaxArgument.getValue()) {
            System.err.println("The condition 0 <= hotMinLimit <= hotMaxLimit must be satisfied.");
            System.exit(1);
        }

        HotMethodPolicy hotMethodPolicy = new HotMethodPolicy();
        hotMethodPolicy.setHotMethodMinLimit(hotMinArgument.getValue());
        hotMethodPolicy.setHotMethodMaxLimit(hotMaxArgument.getValue());
        hotMethodPolicy.setHotMethodPercentile(percentileArgument.getValue());

        ExperimentFiles experimentFiles1 = new ExperimentFilesImpl(
                ExperimentId.ONE, proftoolArgument1.getValue(), optimizationLogArgument1.getValue()
        );
        ExperimentParser parser1 = new ExperimentParser(experimentFiles1);
        Experiment experiment1 = parseOrExit(parser1);
        hotMethodPolicy.markHotMethods(experiment1);
        System.out.println(experiment1.createSummary());

        ExperimentFiles experimentFiles2 = new ExperimentFilesImpl(
                ExperimentId.TWO, proftoolArgument2.getValue(), optimizationLogArgument2.getValue()
        );
        ExperimentParser parser2 = new ExperimentParser(experimentFiles2);
        Experiment experiment2 = parseOrExit(parser2);
        hotMethodPolicy.markHotMethods(experiment2);
        System.out.println(experiment2.createSummary());

        ExperimentMatcher experimentMatcher = new ExperimentMatcher();
        String summary = experimentMatcher.matchAndSummarize(experiment1, experiment2);
        System.out.println(summary);
    }

    private static Experiment parseOrExit(ExperimentParser parser) {
        try {
            return parser.parse();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println(
                    "Could not read the files of the experiment " + parser.getExperimentFiles().getExperimentId()
            );
            System.exit(1);
        } catch (ExperimentParserException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        return null;
    }
}
