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

import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.Objects;

/**
 * A client or debug adapter initiated request.
 */
public class Request extends ProtocolMessage {

    Request(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The command to execute.
     */
    public String getCommand() {
        return jsonData.getString("command");
    }

    public Request setCommand(String command) {
        jsonData.put("command", command);
        return this;
    }

    /**
     * Object containing arguments for the command.
     */
    public Object getArguments() {
        return jsonData.opt("arguments");
    }

    public Request setArguments(Object arguments) {
        jsonData.putOpt("arguments", arguments);
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
        Request other = (Request) obj;
        if (!Objects.equals(this.getType(), other.getType())) {
            return false;
        }
        if (!Objects.equals(this.getCommand(), other.getCommand())) {
            return false;
        }
        if (!Objects.equals(this.getArguments(), other.getArguments())) {
            return false;
        }
        if (this.getSeq() != other.getSeq()) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.getType());
        hash = 67 * hash + Objects.hashCode(this.getCommand());
        if (this.getArguments() != null) {
            hash = 67 * hash + Objects.hashCode(this.getArguments());
        }
        hash = 67 * hash + Integer.hashCode(this.getSeq());
        return hash;
    }

    public static Request create(String command, Integer seq) {
        final JSONObject json = new JSONObject();
        json.put("type", "request");
        json.put("command", command);
        json.put("seq", seq);
        return new Request(json);
    }
}
