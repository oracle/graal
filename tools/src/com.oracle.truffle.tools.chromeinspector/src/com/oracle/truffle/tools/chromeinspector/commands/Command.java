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
package com.oracle.truffle.tools.chromeinspector.commands;

import com.oracle.truffle.tools.utils.json.JSONException;
import com.oracle.truffle.tools.utils.json.JSONObject;

public final class Command {

    public static final String ID = "id";
    public static final String METHOD = "method";
    public static final String PARAMS = "params";

    private final long id;
    private final String method;
    private final Params params;

    public Command(String jsonStr) throws JSONException {
        JSONObject json = new JSONObject(jsonStr);
        this.id = json.getLong(ID);
        this.method = json.getString(METHOD);
        if (json.has(PARAMS)) {
            JSONObject paramsJson = json.getJSONObject(PARAMS);
            this.params = new Params(paramsJson);
        } else {
            this.params = null;
        }
    }

    public Command(long id, String method, Params params) {
        this.id = id;
        this.method = method;
        this.params = params;
    }

    public long getId() {
        return id;
    }

    public String getMethod() {
        return method;
    }

    public Params getParams() {
        return params;
    }

}
