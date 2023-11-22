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

import org.graalvm.shadowed.org.json.JSONArray;
import org.graalvm.shadowed.org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The server capabilities of a [ExecuteCommandRequest](#ExecuteCommandRequest).
 */
public class ExecuteCommandOptions extends WorkDoneProgressOptions {

    ExecuteCommandOptions(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The commands to be executed on the server.
     */
    public List<String> getCommands() {
        final JSONArray json = jsonData.getJSONArray("commands");
        final List<String> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(json.getString(i));
        }
        return Collections.unmodifiableList(list);
    }

    public ExecuteCommandOptions setCommands(List<String> commands) {
        final JSONArray json = new JSONArray();
        for (String string : commands) {
            json.put(string);
        }
        jsonData.put("commands", json);
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
        ExecuteCommandOptions other = (ExecuteCommandOptions) obj;
        if (!Objects.equals(this.getCommands(), other.getCommands())) {
            return false;
        }
        if (!Objects.equals(this.getWorkDoneProgress(), other.getWorkDoneProgress())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 41 * hash + Objects.hashCode(this.getCommands());
        if (this.getWorkDoneProgress() != null) {
            hash = 41 * hash + Boolean.hashCode(this.getWorkDoneProgress());
        }
        return hash;
    }

    public static ExecuteCommandOptions create(List<String> commands) {
        final JSONObject json = new JSONObject();
        JSONArray commandsJsonArr = new JSONArray();
        for (String string : commands) {
            commandsJsonArr.put(string);
        }
        json.put("commands", commandsJsonArr);
        return new ExecuteCommandOptions(json);
    }
}
