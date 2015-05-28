/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot;

import static com.oracle.graal.compiler.GraalDebugConfig.*;
import static com.oracle.graal.options.OptionsLoader.*;

import com.oracle.graal.api.runtime.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.options.*;

//JaCoCo Exclude

/**
 * Sets Graal options from the HotSpot command line. Such options are distinguished by the
 * {@link #GRAAL_OPTION_PREFIX} prefix.
 */
public class HotSpotOptions {

    private static final String GRAAL_OPTION_PREFIX = "-G:";

    static {
        assert !Debug.Initialization.isDebugInitialized() : "The class " + Debug.class.getName() + " must not be initialized before the Graal runtime has been initialized. " +
                        "This can be fixed by placing a call to " + Graal.class.getName() + ".runtime() on the path that triggers initialization of " + Debug.class.getName();
        if (areDebugScopePatternsEnabled()) {
            System.setProperty(Debug.Initialization.INITIALIZER_PROPERTY_NAME, "true");
        }
        if ("".equals(Meter.getValue())) {
            System.setProperty(Debug.ENABLE_UNSCOPED_METRICS_PROPERTY_NAME, "true");
        }
        if ("".equals(Time.getValue())) {
            System.setProperty(Debug.ENABLE_UNSCOPED_TIMERS_PROPERTY_NAME, "true");
        }
        if ("".equals(TrackMemUse.getValue())) {
            System.setProperty(Debug.ENABLE_UNSCOPED_MEM_USE_TRACKERS_PROPERTY_NAME, "true");
        }
    }

    static void printFlags() {
        OptionUtils.printFlags(options, GRAAL_OPTION_PREFIX);
    }

    public native Object getOptionValue(String optionName);
}
