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

    public enum Registry {
        TOTAL("[total]"),
        SETUP("setup"),
        CLASSLIST("classlist"),
        CLINIT("(clinit)"),
        FEATURES("(features)"),
        OBJECTS("(objects)"),
        ANALYSIS("analysis"),
        UNIVERSE("universe"),
        COMPILE_TOTAL("compile"),
        PARSE("(parse)"),
        INLINE("(inline)"),
        COMPILE("(compile)"),
        DEBUG_INFO("dbginfo"),
        IMAGE("image"),
        WRITE("write");

        public final String name;

        Registry(String name) {
            this.name = name;
        }
    }

    private final Map<String, Timer> timers = new ConcurrentHashMap<>();

    public void initDefaultTimers(String imageName) {
        add(new Timer(imageName, Registry.TOTAL.name, false));
        add(new Timer(imageName, Registry.SETUP.name, true));
        add(new Timer(imageName, Registry.CLASSLIST.name, false));
        add(new Timer(imageName, Registry.CLINIT.name, true));
        add(new Timer(imageName, Registry.ANALYSIS.name, true));
        add(new Timer(imageName, Registry.FEATURES.name, false));
        add(new Timer(imageName, Registry.OBJECTS.name, false));
        add(new Timer(imageName, Registry.UNIVERSE.name, true));
        add(new Timer(imageName, Registry.COMPILE_TOTAL.name, true));
        add(new Timer(imageName, Registry.PARSE.name, true));
        add(new Timer(imageName, Registry.INLINE.name, true));
        add(new Timer(imageName, Registry.COMPILE.name, true));
        add(new Timer(imageName, Registry.DEBUG_INFO.name, true));
        add(new Timer(imageName, Registry.IMAGE.name, true));
        add(new Timer(imageName, Registry.WRITE.name, true));
    }

    private void add(Timer timer) {
        GraalError.guarantee(!timers.containsKey(timer.getName()), "Name %s for a timer is already taken.", timer.getName());
        timers.put(timer.getName(), timer);
    }

    public Timer get(String name) {
        Timer timer = timers.get(name);
        GraalError.guarantee(timer != null, "Timer with name %s not found.", name);
        return timer;
    }

    public Timer get(TimerCollection.Registry type) {
        return get(type.name);
    }

    public Timer createTimer(String name) {
        return createTimer(null, name, true);
    }

    public Timer createTimer(String prefix, String name) {
        return createTimer(prefix, name, true);
    }

    public Timer createTimer(String name, boolean autoPrint) {
        return createTimer(null, name, autoPrint);
    }

    public Timer createTimer(String prefix, String name, boolean autoPrint) {
        Timer timer = new Timer(prefix, name, autoPrint);
        add(timer);
        return timer;
    }

    @Override
    public void printTimerStats(PrintWriter out) {
        Iterator<Timer> it = this.timers.values().iterator();
        while (it.hasNext()) {
            Timer timer = it.next();
            StatisticsPrinter.print(out, timer.getName() + "_time", ((int) timer.getTotalTime()));
            if (it.hasNext()) {
                StatisticsPrinter.print(out, timer.getName() + "_memory", ((int) timer.getTotalMemory()));
            } else {
                StatisticsPrinter.printLast(out, timer.getName() + "_memory", ((int) timer.getTotalMemory()));
            }
        }
    }
}
