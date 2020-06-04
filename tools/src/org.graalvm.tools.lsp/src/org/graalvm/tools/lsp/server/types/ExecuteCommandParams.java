/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.lsp.server.types;

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The parameters of a [ExecuteCommandRequest](#ExecuteCommandRequest).
 */
public class ExecuteCommandParams extends WorkDoneProgressParams {

    ExecuteCommandParams(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The identifier of the actual command handler.
     */
    public String getCommand() {
        return jsonData.getString("command");
    }

    public ExecuteCommandParams setCommand(String command) {
        jsonData.put("command", command);
        return this;
    }

    /**
     * Arguments that the command should be invoked with.
     */
    public List<Object> getArguments() {
        final JSONArray json = jsonData.optJSONArray("arguments");
        if (json == null) {
            return null;
        }
        final List<Object> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(json.get(i));
        }
        return Collections.unmodifiableList(list);
    }

    public ExecuteCommandParams setArguments(List<Object> arguments) {
        if (arguments != null) {
            final JSONArray json = new JSONArray();
            for (Object object : arguments) {
                json.put(object);
            }
            jsonData.put("arguments", json);
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
        ExecuteCommandParams other = (ExecuteCommandParams) obj;
        if (!Objects.equals(this.getCommand(), other.getCommand())) {
            return false;
        }
        if (!Objects.equals(this.getArguments(), other.getArguments())) {
            return false;
        }
        if (!Objects.equals(this.getWorkDoneToken(), other.getWorkDoneToken())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 79 * hash + Objects.hashCode(this.getCommand());
        if (this.getArguments() != null) {
            hash = 79 * hash + Objects.hashCode(this.getArguments());
        }
        if (this.getWorkDoneToken() != null) {
            hash = 79 * hash + Objects.hashCode(this.getWorkDoneToken());
        }
        return hash;
    }

    public static ExecuteCommandParams create(String command) {
        final JSONObject json = new JSONObject();
        json.put("command", command);
        return new ExecuteCommandParams(json);
    }
}
