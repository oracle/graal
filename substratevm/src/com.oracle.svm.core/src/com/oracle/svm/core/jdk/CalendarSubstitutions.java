/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;

import sun.util.calendar.CalendarSystem;

/*
 * The JDK performs dynamic lookup of calendar systems by name, which leads to dynamic class
 * loading. We cannot do that, because we need to know all classes ahead of time to perform our
 * static analysis. Therefore, we limited ourselfs to the basic calendars for now, and create them
 * statically.
 */

@TargetClass(java.util.Calendar.class)
final class Target_java_util_Calendar {

    @Substitute
    private static Calendar createCalendar(TimeZone zone, Locale aLocale) {
        return new GregorianCalendar(zone, aLocale);
    }
}

@TargetClass(sun.util.calendar.CalendarSystem.class)
final class Target_sun_util_calendar_CalendarSystem {

    @Substitute
    private static CalendarSystem forName(String calendarName) {
        if ("gregorian".equals(calendarName)) {
            return Util_sun_util_calendar_CalendarSystem.GREGORIAN;
        } else if ("julian".equals(calendarName)) {
            return Util_sun_util_calendar_CalendarSystem.JULIAN;
        } else {
            throw VMError.unsupportedFeature("CalendarSystem.forName " + calendarName);
        }
    }
}

final class Util_sun_util_calendar_CalendarSystem {

    // The static fields are initialized during native image generation.
    static final CalendarSystem GREGORIAN = CalendarSystem.forName("gregorian");
    static final CalendarSystem JULIAN = CalendarSystem.forName("julian");
}

@TargetClass(sun.util.BuddhistCalendar.class)
@Delete
final class Target_sun_util_BuddhistCalendar {
}

@TargetClass(className = "java.util.JapaneseImperialCalendar")
@Delete
final class Target_java_util_JapaneseImperialCalendar {
}

/** Dummy class to have a class with the file's name. */
public final class CalendarSubstitutions {
}
