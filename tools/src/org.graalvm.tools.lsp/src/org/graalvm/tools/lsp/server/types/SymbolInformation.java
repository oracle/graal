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
 * Represents information about programming constructs like variables, classes, interfaces etc.
 */
public class SymbolInformation extends JSONBase {

    SymbolInformation(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The name of this symbol.
     */
    public String getName() {
        return jsonData.getString("name");
    }

    public SymbolInformation setName(String name) {
        jsonData.put("name", name);
        return this;
    }

    /**
     * The kind of this symbol.
     */
    public SymbolKind getKind() {
        return SymbolKind.get(jsonData.getInt("kind"));
    }

    public SymbolInformation setKind(SymbolKind kind) {
        jsonData.put("kind", kind.getIntValue());
        return this;
    }

    /**
     * Indicates if this symbol is deprecated.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getDeprecated() {
        return jsonData.has("deprecated") ? jsonData.getBoolean("deprecated") : null;
    }

    public SymbolInformation setDeprecated(Boolean deprecated) {
        jsonData.putOpt("deprecated", deprecated);
        return this;
    }

    /**
     * The location of this symbol. The location's range is used by a tool to reveal the location in
     * the editor. If the symbol is selected in the tool the range's start information is used to
     * position the cursor. So the range usually spans more than the actual symbol's name and does
     * normally include thinks like visibility modifiers.
     *
     * The range doesn't have to denote a node range in the sense of a abstract syntax tree. It can
     * therefore not be used to re-construct a hierarchy of the symbols.
     */
    public Location getLocation() {
        return new Location(jsonData.getJSONObject("location"));
    }

    public SymbolInformation setLocation(Location location) {
        jsonData.put("location", location.jsonData);
        return this;
    }

    /**
     * The name of the symbol containing this symbol. This information is for user interface
     * purposes (e.g. to render a qualifier in the user interface if necessary). It can't be used to
     * re-infer a hierarchy for the document symbols.
     */
    public String getContainerName() {
        return jsonData.optString("containerName", null);
    }

    public SymbolInformation setContainerName(String containerName) {
        jsonData.putOpt("containerName", containerName);
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
        SymbolInformation other = (SymbolInformation) obj;
        if (!Objects.equals(this.getName(), other.getName())) {
            return false;
        }
        if (this.getKind() != other.getKind()) {
            return false;
        }
        if (!Objects.equals(this.getDeprecated(), other.getDeprecated())) {
            return false;
        }
        if (!Objects.equals(this.getLocation(), other.getLocation())) {
            return false;
        }
        if (!Objects.equals(this.getContainerName(), other.getContainerName())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.getName());
        hash = 97 * hash + Objects.hashCode(this.getKind());
        if (this.getDeprecated() != null) {
            hash = 97 * hash + Boolean.hashCode(this.getDeprecated());
        }
        hash = 97 * hash + Objects.hashCode(this.getLocation());
        if (this.getContainerName() != null) {
            hash = 97 * hash + Objects.hashCode(this.getContainerName());
        }
        return hash;
    }

    /**
     * Creates a new symbol information literal.
     *
     * @param name The name of the symbol.
     * @param kind The kind of the symbol.
     * @param range The range of the location of the symbol.
     * @param uri The resource of the location of symbol, defaults to the current document.
     * @param containerName The name of the symbol containing the symbol.
     */
    public static SymbolInformation create(String name, SymbolKind kind, Range range, String uri, String containerName) {
        final JSONObject json = new JSONObject();
        json.put("name", name);
        json.put("kind", kind.getIntValue());
        json.put("location", Location.create(uri, range).jsonData);
        json.putOpt("containerName", containerName);
        return new SymbolInformation(json);
    }
}
