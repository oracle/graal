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

    /**
     * A registry of well-known timers used when building images.
     */
    public enum Registry {
        TOTAL("[total]", false),
        SETUP("setup", true),
        CLASSLIST("classlist", false),
        CLINIT("(clinit)", true),
        FEATURES("(features)", false),
        OBJECTS("(objects)", false),
        ANALYSIS("analysis", true),
        UNIVERSE("universe", true),
        COMPILE_TOTAL("compile", true),
        PARSE("(parse)", true),
        INLINE("(inline)", true),
        COMPILE("(compile)", true),
        DEBUG_INFO("dbginfo", true),
        IMAGE("image", true),
        WRITE("write", true);

        public final String name;

        public final boolean autoPrint;

        Registry(String name, boolean autoPrint) {
            this.name = name;
            this.autoPrint = autoPrint;
        }

    }

    private final Map<String, Timer> timers = new ConcurrentHashMap<>();
    private final String imageName;

    public TimerCollection(String imageName) {
        this.imageName = imageName;
    }

    public Timer get(String name) {
        Timer timer = timers.get(name);
        GraalError.guarantee(timer != null, "Timer with name %s not found.", name);
        return timer;
    }

    public Timer get(TimerCollection.Registry type) {
        return timers.computeIfAbsent(type.name, (name) -> new Timer(imageName, name, type.autoPrint));
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
        GraalError.guarantee(!timers.containsKey(name), "Name %s for a timer is already taken.", name);
        Timer timer = new Timer(prefix, name, autoPrint);
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
                StatisticsPrinter.print(out, timer.getName() + "_memory", ((int) timer.getTotalMemory()));
            } else {
                StatisticsPrinter.printLast(out, timer.getName() + "_memory", ((int) timer.getTotalMemory()));
            }
        }
    }
}
