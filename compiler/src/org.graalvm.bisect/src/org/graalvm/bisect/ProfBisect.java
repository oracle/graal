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
import org.graalvm.bisect.parser.ExperimentFilesImpl;
import org.graalvm.bisect.parser.ExperimentParser;
import org.graalvm.bisect.parser.ExperimentParserException;

public class ProfBisect {
    public static void main(String[] args) {
        if (args.length != 4) {
            System.err.println(
                    "Usage: mx profbisect proftoolOutput1 optimizationLog1 proftoolOutput2 optimizationLog2"
            );
            System.exit(1);
        }
        HotMethodPolicy hotMethodPolicy = new HotMethodPolicy();
        ExperimentParser parser1 = new ExperimentParser(new ExperimentFilesImpl(ExperimentId.ONE, args[0], args[1]));
        Experiment experiment1 = parseOrExit(parser1);
        hotMethodPolicy.markHotMethods(experiment1);
        System.out.println(experiment1.createSummary());

        ExperimentParser parser2 = new ExperimentParser(new ExperimentFilesImpl(ExperimentId.TWO, args[2], args[3]));
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
