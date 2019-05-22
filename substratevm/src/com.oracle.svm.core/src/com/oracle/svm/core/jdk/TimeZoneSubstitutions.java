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
package com.oracle.svm.core.jdk;

import static java.util.stream.Collectors.toMap;

import java.util.Arrays;
import java.util.Map;
import java.util.TimeZone;

import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.option.HostedOptionKey;

final class TimeZoneSupport {

    final Map<String, TimeZone> zones;
    TimeZone defaultZone;

    TimeZoneSupport(Map<String, TimeZone> zones, TimeZone defaultZone) {
        this.zones = zones;
        this.defaultZone = defaultZone;
    }

    public static TimeZoneSupport instance() {
        return ImageSingletons.lookup(TimeZoneSupport.class);
    }
}

@TargetClass(java.util.TimeZone.class)
final class Target_java_util_TimeZone {

    @Substitute
    private static TimeZone getDefaultRef() {
        return TimeZoneSupport.instance().defaultZone;
    }

    @Substitute
    private static void setDefault(TimeZone zone) {
        TimeZoneSupport.instance().defaultZone = zone;
    }

    @Substitute
    public static TimeZone getTimeZone(String id) {
        return TimeZoneSupport.instance().zones.getOrDefault(id, TimeZoneSupport.instance().zones.get("GMT"));
    }
}

@AutomaticFeature
final class TimeZoneFeature implements Feature {
    static class Options {
        @Option(help = "When true, all time zones will be pre-initialized in the image.")//
        public static final HostedOptionKey<Boolean> IncludeAllTimeZones = new HostedOptionKey<>(false);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        TimeZone defaultZone = TimeZone.getDefault();
        String[] supportedZoneIDs = Options.IncludeAllTimeZones.getValue() ? TimeZone.getAvailableIDs() : new String[]{"GMT", "UTC", defaultZone.getID()};
        Map<String, TimeZone> supportedZones = Arrays.stream(supportedZoneIDs)
                        .map(TimeZone::getTimeZone)
                        .collect(toMap(TimeZone::getID, tz -> tz, (tz1, tz2) -> tz1));
        ImageSingletons.add(TimeZoneSupport.class, new TimeZoneSupport(supportedZones, defaultZone));
    }
}

/**
 * This whole file should be eventually removed: GR-11844.
 */
public class TimeZoneSubstitutions {
}
