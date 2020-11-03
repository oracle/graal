/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.dap.types;

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Arguments for 'runInTerminal' request.
 */
public class RunInTerminalRequestArguments extends JSONBase {

    RunInTerminalRequestArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * What kind of terminal to launch.
     */
    public String getKind() {
        return jsonData.optString("kind", null);
    }

    public RunInTerminalRequestArguments setKind(String kind) {
        jsonData.putOpt("kind", kind);
        return this;
    }

    /**
     * Optional title of the terminal.
     */
    public String getTitle() {
        return jsonData.optString("title", null);
    }

    public RunInTerminalRequestArguments setTitle(String title) {
        jsonData.putOpt("title", title);
        return this;
    }

    /**
     * Working directory of the command.
     */
    public String getCwd() {
        return jsonData.getString("cwd");
    }

    public RunInTerminalRequestArguments setCwd(String cwd) {
        jsonData.put("cwd", cwd);
        return this;
    }

    /**
     * List of arguments. The first argument is the command to run.
     */
    public List<String> getArgs() {
        final JSONArray json = jsonData.getJSONArray("args");
        final List<String> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(json.getString(i));
        }
        return Collections.unmodifiableList(list);
    }

    public RunInTerminalRequestArguments setArgs(List<String> args) {
        final JSONArray json = new JSONArray();
        for (String string : args) {
            json.put(string);
        }
        jsonData.put("args", json);
        return this;
    }

    /**
     * Environment key-value pairs that are added to or removed from the default environment.
     */
    public Map<String, String> getEnv() {
        final JSONObject json = jsonData.optJSONObject("env");
        if (json == null) {
            return null;
        }
        final Map<String, String> map = new HashMap<>(json.length());
        for (String key : json.keySet()) {
            map.put(key, json.getString(key));
        }
        return map;
    }

    public RunInTerminalRequestArguments setEnv(Map<String, String> env) {
        if (env != null) {
            final JSONObject json = new JSONObject();
            for (Map.Entry<String, String> entry : env.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }
            jsonData.put("env", json);
        }
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (this.getClass() != obj.getClass()) {
            return false;
        }
        RunInTerminalRequestArguments other = (RunInTerminalRequestArguments) obj;
        if (!Objects.equals(this.getKind(), other.getKind())) {
            return false;
        }
        if (!Objects.equals(this.getTitle(), other.getTitle())) {
            return false;
        }
        if (!Objects.equals(this.getCwd(), other.getCwd())) {
            return false;
        }
        if (!Objects.equals(this.getArgs(), other.getArgs())) {
            return false;
        }
        if (!Objects.equals(this.getEnv(), other.getEnv())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        if (this.getKind() != null) {
            hash = 97 * hash + Objects.hashCode(this.getKind());
        }
        if (this.getTitle() != null) {
            hash = 97 * hash + Objects.hashCode(this.getTitle());
        }
        hash = 97 * hash + Objects.hashCode(this.getCwd());
        hash = 97 * hash + Objects.hashCode(this.getArgs());
        if (this.getEnv() != null) {
            hash = 97 * hash + Objects.hashCode(this.getEnv());
        }
        return hash;
    }

    public static RunInTerminalRequestArguments create(String cwd, List<String> args) {
        final JSONObject json = new JSONObject();
        json.put("cwd", cwd);
        JSONArray argsJsonArr = new JSONArray();
        for (String string : args) {
            argsJsonArr.put(string);
        }
        json.put("args", argsJsonArr);
        return new RunInTerminalRequestArguments(json);
    }
}
