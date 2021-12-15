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
package com.oracle.graal.pointsto.util;

import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.graal.pointsto.reports.StatisticsPrinter;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.graalvm.compiler.options.OptionType.User;

public class TimerManager {

    public static class Options {
        @Option(help = "File into which the statistics for each build phase should be stored.", type = User)//
        public static final OptionKey<String> TimerStatsOutputFile = new OptionKey<>("");
    }

    private final List<Timer> timers = Collections.synchronizedList(new ArrayList<>());

    public Timer register(Timer timer) {
        timers.add(timer);
        return timer;
    }

    public void onBuildFinished(OptionValues options) {
        if (!Options.TimerStatsOutputFile.hasBeenSet(options)) {
            printStatistics(options);
        }
    }

    private void printStatistics(OptionValues options) {
        File file = new File(Options.TimerStatsOutputFile.getValue(options));
        String description = "timer stats";
        ReportUtils.report(description, file.toPath(), this::doPrintTimerStatistics);
    }

    private void doPrintTimerStatistics(PrintWriter out) {
        StatisticsPrinter.beginObject(out);
        for (int i = 0; i < timers.size(); i++) {
            Timer timer = timers.get(i);
            StatisticsPrinter.print(out, timer.getName() + "_time", ((int) timer.getTotalTime()));
            if (i != timers.size() - 1) {
                StatisticsPrinter.print(out, timer.getName() + "_memory", ((int) timer.getTotalMemory()));
            } else {
                StatisticsPrinter.printLast(out, timer.getName() + "_memory", ((int) timer.getTotalMemory()));
            }
        }
        StatisticsPrinter.endObject(out);
    }
}
