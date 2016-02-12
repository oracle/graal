/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object;

import static com.oracle.truffle.api.object.Layout.OPTION_PREFIX;

public final class ObjectStorageOptions {
    private ObjectStorageOptions() {
    }

    // Shape configuration
    /** Primitive location switch. */
    public static final boolean PrimitiveLocations = booleanOption(OPTION_PREFIX + "PrimitiveLocations", true);
    public static final boolean IntegerLocations = booleanOption(OPTION_PREFIX + "IntegerLocations", true);
    public static final boolean DoubleLocations = booleanOption(OPTION_PREFIX + "DoubleLocations", true);
    public static final boolean LongLocations = booleanOption(OPTION_PREFIX + "LongLocations", true);
    public static final boolean BooleanLocations = booleanOption(OPTION_PREFIX + "BooleanLocations", true);
    public static final boolean TypedObjectLocations = booleanOption(OPTION_PREFIX + "TypedObjectLocations", true);

    /** Allocation of in-object fields. */
    public static final boolean InObjectFields = booleanOption(OPTION_PREFIX + "InObjectFields", true);

    // Debug options (should be final)
    public static final boolean DebugCounters = booleanOption(OPTION_PREFIX + "DebugCounters", true);
    public static final boolean TraceReshape = booleanOption(OPTION_PREFIX + "TraceReshape", false);
    public static final boolean DumpShapesDOT = booleanOption(OPTION_PREFIX + "DumpShapesDOT", false);
    public static final boolean DumpShapesJSON = booleanOption(OPTION_PREFIX + "DumpShapesJSON", false);
    public static final boolean DumpShapesIGV = booleanOption(OPTION_PREFIX + "DumpShapesIGV", false);
    public static final boolean DumpShapes = DumpShapesDOT || DumpShapesJSON || DumpShapesIGV;
    public static final String DumpShapesPath = System.getProperty(OPTION_PREFIX + "DumpShapesPath", "");

    public static final boolean Profile = booleanOption(OPTION_PREFIX + "Profile", false);
    public static final int ProfileTopResults = Integer.getInteger(OPTION_PREFIX + "ProfileTopResults", -1);

    public static boolean booleanOption(String name, boolean defaultValue) {
        String value = System.getProperty(name);
        return value == null ? defaultValue : value.equalsIgnoreCase("true");
    }
}
