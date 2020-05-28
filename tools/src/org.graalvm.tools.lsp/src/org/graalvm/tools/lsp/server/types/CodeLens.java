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

import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.Objects;

/**
 * A code lens represents a [command](#Command) that should be shown along with source text, like
 * the number of references, a way to run tests, etc.
 *
 * A code lens is _unresolved_ when no command is associated to it. For performance reasons the
 * creation of a code lens and resolving should be done to two stages.
 */
public class CodeLens extends JSONBase {

    CodeLens(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The range in which this code lens is valid. Should only span a single line.
     */
    public Range getRange() {
        return new Range(jsonData.getJSONObject("range"));
    }

    public CodeLens setRange(Range range) {
        jsonData.put("range", range.jsonData);
        return this;
    }

    /**
     * The command this code lens represents.
     */
    public Command getCommand() {
        return jsonData.has("command") ? new Command(jsonData.optJSONObject("command")) : null;
    }

    public CodeLens setCommand(Command command) {
        jsonData.putOpt("command", command != null ? command.jsonData : null);
        return this;
    }

    /**
     * An data entry field that is preserved on a code lens item between a
     * [CodeLensRequest](#CodeLensRequest) and a [CodeLensResolveRequest] (#CodeLensResolveRequest).
     */
    public Object getData() {
        return jsonData.opt("data");
    }

    public CodeLens setData(Object data) {
        jsonData.putOpt("data", data);
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
        CodeLens other = (CodeLens) obj;
        if (!Objects.equals(this.getRange(), other.getRange())) {
            return false;
        }
        if (!Objects.equals(this.getCommand(), other.getCommand())) {
            return false;
        }
        if (!Objects.equals(this.getData(), other.getData())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 73 * hash + Objects.hashCode(this.getRange());
        if (this.getCommand() != null) {
            hash = 73 * hash + Objects.hashCode(this.getCommand());
        }
        if (this.getData() != null) {
            hash = 73 * hash + Objects.hashCode(this.getData());
        }
        return hash;
    }

    /**
     * Creates a new CodeLens literal.
     */
    public static CodeLens create(Range range, Object data) {
        final JSONObject json = new JSONObject();
        json.put("range", range.jsonData);
        json.putOpt("data", data);
        return new CodeLens(json);
    }
}
