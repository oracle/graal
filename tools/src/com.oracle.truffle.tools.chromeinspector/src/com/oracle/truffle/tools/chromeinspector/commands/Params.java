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

import java.util.Optional;

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

public final class Params {

    private final JSONObject json;

    public Params(JSONObject json) {
        this.json = json;
    }

    public static Params createContext(long id, String name) {
        JSONObject params = new JSONObject();
        JSONObject context = new JSONObject();
        context.put("id", id);
        context.put("name", name);
        context.put("origin", "");
        params.put("context", context);
        return new Params(params);
    }

    public static Params createContextId(long id) {
        JSONObject params = new JSONObject();
        params.put("executionContextId", id);
        return new Params(params);
    }

    public static Params createConsoleAPICalled(String type, Object text, long contextId) {
        JSONObject params = new JSONObject();
        params.put("type", type);
        JSONArray args = new JSONArray();
        if (text != null) {
            JSONObject outObject = new JSONObject();
            if (text instanceof String) {
                outObject.put("type", "string");
            } else if (text instanceof Number) {
                outObject.put("type", "number");
            }
            outObject.put("value", text);
            args.put(outObject);
        }
        params.put("args", args);
        params.put("executionContextId", contextId);
        params.put("timestamp", System.nanoTime() / 1000_000.0);
        return new Params(params);
    }

    public JSONObject getJSONObject() {
        return json;
    }

    public String[] getPatterns() {
        if (json.has("patterns")) {
            JSONArray patterns = json.getJSONArray("patterns");
            return patterns.toList().toArray(new String[patterns.length()]);
        }
        return new String[]{};
    }

    public long getSamplingInterval() {
        if (json.has("interval")) {
            return json.getLong("interval");
        } else {
            return -1;
        }
    }

    public int getMaxDepth() {
        if (json.has("maxDepth")) {
            return json.getInt("maxDepth");
        } else {
            return 0;
        }
    }

    public String getState() {
        if (json.has("state")) {
            return json.getString("state");
        } else {
            return "none";
        }
    }

    public String getScriptId() {
        if (json.has("scriptId")) {
            return json.getString("scriptId");
        } else {
            return null;
        }
    }

    public Optional<Boolean> getBoolean(String name) {
        if (json.has(name)) {
            return Optional.of(json.getBoolean(name));
        } else {
            return Optional.empty();
        }
    }

    public String getBreakpointId() {
        if (json.has("breakpointId")) {
            return json.getString("breakpointId");
        } else {
            return null;
        }
    }
}
