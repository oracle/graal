/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object;

import static com.oracle.truffle.api.object.Layout.OPTION_PREFIX;

/** @since 0.17 or earlier */
public final class ObjectStorageOptions {
    private ObjectStorageOptions() {
    }

    /** @since 0.17 or earlier */
    public static final boolean PrimitiveLocations = booleanOption(OPTION_PREFIX + "PrimitiveLocations", true);
    /** @since 0.17 or earlier */
    public static final boolean IntegerLocations = booleanOption(OPTION_PREFIX + "IntegerLocations", true);
    /** @since 0.17 or earlier */
    public static final boolean DoubleLocations = booleanOption(OPTION_PREFIX + "DoubleLocations", true);
    /** @since 0.17 or earlier */
    public static final boolean LongLocations = booleanOption(OPTION_PREFIX + "LongLocations", true);
    /** @since 0.17 or earlier */
    public static final boolean BooleanLocations = booleanOption(OPTION_PREFIX + "BooleanLocations", true);
    /** @since 0.17 or earlier */
    public static final boolean TypedObjectLocations = booleanOption(OPTION_PREFIX + "TypedObjectLocations", true);

    /**
     * Allocation of in-object fields.
     *
     * @since 0.17 or earlier
     */
    public static final boolean InObjectFields = booleanOption(OPTION_PREFIX + "InObjectFields", true);

    static final boolean TriePropertyMap = booleanOption(OPTION_PREFIX + "TriePropertyMap", true);

    // Debug options (should be final)
    /** @since 0.17 or earlier */
    public static final boolean TraceReshape = booleanOption(OPTION_PREFIX + "TraceReshape", false);

    static final boolean DebugCounters = booleanOption(OPTION_PREFIX + "DebugCounters", false);
    static final boolean DumpDebugCounters = booleanOption(OPTION_PREFIX + "DumpDebugCounters", true);

    static final boolean DumpShapesDOT = booleanOption(OPTION_PREFIX + "DumpShapesDOT", false);
    static final boolean DumpShapesJSON = booleanOption(OPTION_PREFIX + "DumpShapesJSON", false);
    static final boolean DumpShapesIGV = booleanOption(OPTION_PREFIX + "DumpShapesIGV", false);
    static final boolean DumpShapes = DumpShapesDOT || DumpShapesJSON || DumpShapesIGV;
    static final String DumpShapesPath = System.getProperty(OPTION_PREFIX + "DumpShapesPath", "");

    /** @since 0.17 or earlier */
    static final boolean Profile = booleanOption(OPTION_PREFIX + "Profile", false);
    /** @since 0.17 or earlier */
    static final int ProfileTopResults = Integer.getInteger(OPTION_PREFIX + "ProfileTopResults", -1);

    /** @since 0.17 or earlier */
    public static boolean booleanOption(String name, boolean defaultValue) {
        String value = System.getProperty(name);
        return value == null ? defaultValue : value.equalsIgnoreCase("true");
    }
}
