/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.time.chrono.HijrahChronology;
import java.util.Properties;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;

/**
 * Feature to configure {@link HijrahChronology} at build time. All {@link HijrahChronology} object
 * that end up in the image heap are fully initialized. This has the advantage that the resources
 * and files for configuring the {@link HijrahChronology} variants are not needed at run time. On
 * the other hand, the configuration can no longer be changed after building. See
 * {@link HijrahChronology} for configuration option.
 */
@AutomaticallyRegisteredFeature
public class HijrahChronologyFeature implements InternalFeature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        // implicitly initialize HijrahChronology objects via transitive call to checkCalendarInit()
        access.registerObjectReachabilityHandler(o -> o.isLeapYear(0), HijrahChronology.class);
    }
}

@TargetClass(HijrahChronology.class)
final class Target_java_time_chrono_HijrahChronology {
    // Checkstyle: stop
    /**
     * Config path with includes JAVA_HOME. We force full initialization at build time
     * {@link HijrahChronologyFeature#duringSetup}, so no need to keep this.
     */
    @Delete //
    private static Path CONF_PATH;
    // Checkstyle: resume

    /**
     * @see #CONF_PATH
     */
    @Delete
    private static native void registerCustomChrono();

    /**
     * @see #CONF_PATH
     */
    @Delete
    private native void loadCalendarData();

    /**
     * @see #CONF_PATH
     */
    @Delete
    private static native Properties readConfigProperties(String chronologyId, String calendarType);

    /**
     * @see #CONF_PATH
     */
    @Delete //
    private boolean initComplete;

    /**
     * No more initialization needed.
     *
     * @see #CONF_PATH
     */
    @Substitute
    private void checkCalendarInit() {
        // initialized at build time
    }
}
