/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.types;

import com.oracle.truffle.tools.utils.json.JSONObject;

public final class CallArgument {

    private static final String POSITIVE_INFINITY_STR = Double.toString(Double.POSITIVE_INFINITY);
    private static final String NEGATIVE_INFINITY_STR = Double.toString(Double.NEGATIVE_INFINITY);
    private static final String NAN_STR = Double.toString(Double.NaN);
    private static final Double NEGATIVE_ZERO = Double.valueOf("-0");

    private final Object value;
    private final String objectId;
    private final boolean undefined;

    private CallArgument(Object value, String objectId, boolean undefined) {
        this.value = value;
        this.objectId = objectId;
        this.undefined = undefined;
    }

    public static CallArgument get(JSONObject json) {
        Object value = json.opt("unserializableValue");
        if (value != null) {
            if (POSITIVE_INFINITY_STR.equals(value)) {
                value = Double.POSITIVE_INFINITY;
            } else if (NEGATIVE_INFINITY_STR.equals(value)) {
                value = Double.NEGATIVE_INFINITY;
            } else if (NAN_STR.equals(value)) {
                value = Double.NaN;
            } else if ("-0".equals(value) || "-0.0".equals(value)) {
                value = NEGATIVE_ZERO;
            }
        } else {
            value = json.opt("value");
            if (value == JSONObject.NULL) {
                value = null;
            }
        }
        String objectId = json.optString("objectId", null);
        return new CallArgument(value, objectId, json.length() == 0);
    }

    /** A primitive value, or <code>null</code>. */
    public Object getPrimitiveValue() {
        return value;
    }

    /** An object ID, or <code>null</code>. */
    public String getObjectId() {
        return objectId;
    }

    /** Whether is represents an undefined value. */
    public boolean isUndefined() {
        return undefined;
    }
}
