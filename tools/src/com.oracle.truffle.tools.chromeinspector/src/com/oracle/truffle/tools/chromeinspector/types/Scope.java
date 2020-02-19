/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

public final class Scope {

    private final String type;
    private final RemoteObject object;
    private final String name;
    private final Location startLocation;
    private final Location endLocation;
    private final int internalIndex; // the index of language implementation scope

    public Scope(String type, RemoteObject object, String name, Location startLocation, Location endLocation, int internalIndex) {
        this.type = type;
        this.object = object;
        this.name = name;
        this.startLocation = startLocation;
        this.endLocation = endLocation;
        this.internalIndex = internalIndex;
    }

    public String getType() {
        return type;
    }

    public RemoteObject getObject() {
        return object;
    }

    public Location getStartLocation() {
        return startLocation;
    }

    public Location getEndLocation() {
        return endLocation;
    }

    public int getInternalIndex() {
        return internalIndex;
    }

    private JSONObject createJSON() {
        JSONObject json = new JSONObject();
        json.put("type", type);
        json.put("object", object.toJSON());
        json.putOpt("name", name);
        if (startLocation != null) {
            json.put("startLocation", startLocation.toJSON());
        }
        if (endLocation != null) {
            json.put("endLocation", endLocation.toJSON());
        }
        return json;
    }

    static JSONArray createScopesJSON(Scope[] scopes) {
        JSONArray array = new JSONArray();
        for (Scope scope : scopes) {
            DebugScope dscope = scope.object.getScope();
            if (dscope.isFunctionScope() || dscope.getDeclaredValues().iterator().hasNext()) {
                // provide only scopes that have some variables
                array.put(scope.createJSON());
            }
        }
        return array;
    }

}
