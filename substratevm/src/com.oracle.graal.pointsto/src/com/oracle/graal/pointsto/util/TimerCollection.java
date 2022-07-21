/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.pointsto.reports.StatisticsPrinter;
import com.oracle.svm.util.ImageBuildStatistics;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.nativeimage.ImageSingletons;

import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TimerCollection implements ImageBuildStatistics.TimerCollectionPrinter {

    public static TimerCollection singleton() {
        return ImageSingletons.lookup(TimerCollection.class);
    }

    /**
     * A registry of well-known timers used when building images.
     */
    public enum Registry {
        TOTAL("total"),
        SETUP("setup"),
        CLASSLIST("classlist"),
        CLINIT("(clinit)"),
        FEATURES("(features)"),
        VERIFY_HEAP("(verify)"),
        ANALYSIS("analysis"),
        UNIVERSE("universe"),
        COMPILE_TOTAL("compile"),
        PARSE("(parse)"),
        INLINE("(inline)"),
        COMPILE("(compile)"),
        LAYOUT("(layout)"),
        DEBUG_INFO("dbginfo"),
        IMAGE("image"),
        WRITE("write");

        public final String name;

        Registry(String name) {
            this.name = name;
        }

    }

    private final Map<String, Timer> timers = new ConcurrentHashMap<>();

    public Timer get(String name) {
        Timer timer = timers.get(name);
        GraalError.guarantee(timer != null, "Timer with name %s not found.", name);
        return timer;
    }

    public Timer get(TimerCollection.Registry type) {
        return timers.computeIfAbsent(type.name, (name) -> new Timer(name));
    }

    public static Timer.StopTimer createTimerAndStart(String name) {
        return singleton().createTimer(name).start();
    }

    public static Timer.StopTimer createTimerAndStart(TimerCollection.Registry type) {
        return singleton().get(type).start();
    }

    public Timer createTimer(String name) {
        GraalError.guarantee(!timers.containsKey(name), "Name %s for a timer is already taken.", name);
        Timer timer = new Timer(name);
        timers.put(timer.getName(), timer);
        return timer;
    }

    @Override
    public void printTimerStats(PrintWriter out) {
        Iterator<Timer> it = this.timers.values().iterator();
        while (it.hasNext()) {
            Timer timer = it.next();
            StatisticsPrinter.print(out, timer.getName() + "_time", ((int) timer.getTotalTime()));
            if (it.hasNext()) {
                StatisticsPrinter.print(out, timer.getName() + "_memory", timer.getTotalMemory());
            } else {
                StatisticsPrinter.printLast(out, timer.getName() + "_memory", timer.getTotalMemory());
            }
        }
    }
}
