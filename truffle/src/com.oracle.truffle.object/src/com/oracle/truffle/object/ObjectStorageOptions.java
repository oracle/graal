/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.object;

import static com.oracle.truffle.api.object.Layout.OPTION_PREFIX;

/** @since 0.17 or earlier */
@Deprecated
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
