/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

public final class PropertyDescriptor {

    private final JSONObject jsonObject;

    /**
     * Create an object property descriptor.
     *
     * @param name Property name or symbol description.
     * @param value The value associated with the property.
     * @param writable True if the value associated with the property may be changed (data
     *            descriptors only).
     * @param get A function which serves as a getter for the property, or <code>undefined</code> if
     *            there is no getter (accessor descriptors only).
     * @param set A function which serves as a setter for the property, or <code>undefined</code> if
     *            there is no setter (accessor descriptors only).
     * @param configurable True if the type of this property descriptor may be changed and if the
     *            property may be deleted from the corresponding object.
     * @param enumerable True if this property shows up during enumeration of the properties on the
     *            corresponding object.
     * @param wasThrown True if the result was thrown during the evaluation.
     * @param isOwn True if the property is owned for the object.
     * @param symbol Property symbol object, if the property is of the <code>symbol</code> type.
     */
    public PropertyDescriptor(String name, RemoteObject value, Boolean writable,
                    RemoteObject get, RemoteObject set, boolean configurable,
                    boolean enumerable, Boolean wasThrown, Boolean isOwn, RemoteObject symbol) {
        jsonObject = createJSON(name, value, writable, get, set, configurable, enumerable, wasThrown, isOwn, symbol);
    }

    private static JSONObject createJSON(String name, RemoteObject value, Boolean writable,
                    RemoteObject get, RemoteObject set, boolean configurable,
                    boolean enumerable, Boolean wasThrown, Boolean isOwn, RemoteObject symbol) {
        JSONObject json = new JSONObject();
        json.put("name", name);
        if (value != null && get == null) {
            json.put("value", value.toJSON());
        }
        json.putOpt("writable", writable);
        if (get != null) {
            json.put("get", get.toJSON());
        }
        if (set != null) {
            json.put("set", set.toJSON());
        }
        json.put("configurable", configurable);
        json.put("enumerable", enumerable);
        json.putOpt("wasThrown", wasThrown);
        json.putOpt("isOwn", isOwn);
        if (symbol != null) {
            json.put("symbol", symbol.toJSON());
        }
        return json;
    }

    public JSONObject toJSON() {
        return jsonObject;
    }
}
