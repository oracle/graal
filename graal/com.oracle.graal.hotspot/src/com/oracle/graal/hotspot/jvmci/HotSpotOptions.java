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
package com.oracle.graal.hotspot.jvmci;

import static com.oracle.graal.compiler.GraalDebugConfig.*;
import static com.oracle.graal.hotspot.jvmci.HotSpotOptionsLoader.*;
import static java.lang.Double.*;

import com.oracle.graal.api.runtime.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.options.*;
import com.oracle.graal.options.OptionUtils.OptionConsumer;

//JaCoCo Exclude

/**
 * Sets Graal options from the HotSpot command line. Such options are distinguished by the
 * {@link #GRAAL_OPTION_PREFIX} prefix.
 */
public class HotSpotOptions {

    private static final String GRAAL_OPTION_PREFIX = "-G:";

    /**
     * Parses the Graal specific options specified to HotSpot (e.g., on the command line).
     */
    private static native void parseVMOptions();

    static {
        parseVMOptions();

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

    /**
     * Ensures {@link HotSpotOptions} is initialized.
     */
    public static void initialize() {
    }

    /**
     * Helper for the VM code called by {@link #parseVMOptions()}.
     *
     * @param name the name of a parsed option
     * @param option the object encapsulating the option
     * @param spec specification of boolean option value, type of option value or action to take
     */
    static void setOption(String name, OptionValue<?> option, char spec, String stringValue, long primitiveValue) {
        switch (spec) {
            case '+':
                option.setValue(Boolean.TRUE);
                break;
            case '-':
                option.setValue(Boolean.FALSE);
                break;
            case '?':
                OptionUtils.printFlags(options, GRAAL_OPTION_PREFIX);
                break;
            case ' ':
                OptionUtils.printNoMatchMessage(options, name, GRAAL_OPTION_PREFIX);
                break;
            case 'i':
                option.setValue((int) primitiveValue);
                break;
            case 'f':
                option.setValue((float) longBitsToDouble(primitiveValue));
                break;
            case 'd':
                option.setValue(longBitsToDouble(primitiveValue));
                break;
            case 's':
                option.setValue(stringValue);
                break;
        }
    }

    /**
     * Parses a given option value specification.
     *
     * @param option the specification of an option and its value
     * @param setter the object to notify of the parsed option and value. If null, the
     *            {@link OptionValue#setValue(Object)} method of the specified option is called
     *            instead.
     */
    public static boolean parseOption(String option, OptionConsumer setter) {
        return OptionUtils.parseOption(options, option, GRAAL_OPTION_PREFIX, setter);
    }
}
