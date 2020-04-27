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
 * Represents programming constructs like variables, classes, interfaces etc. that appear in a
 * document. Document symbols can be hierarchical and they have two ranges: one that encloses its
 * definition and one that points to its most interesting range, e.g. the range of an identifier.
 */
public class DocumentSymbol extends JSONBase {

    DocumentSymbol(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The name of this symbol. Will be displayed in the user interface and therefore must not be an
     * empty string or a string only consisting of white spaces.
     */
    public String getName() {
        return jsonData.getString("name");
    }

    public DocumentSymbol setName(String name) {
        jsonData.put("name", name);
        return this;
    }

    /**
     * More detail for this symbol, e.g the signature of a function.
     */
    public String getDetail() {
        return jsonData.optString("detail", null);
    }

    public DocumentSymbol setDetail(String detail) {
        jsonData.putOpt("detail", detail);
        return this;
    }

    /**
     * The kind of this symbol.
     */
    public SymbolKind getKind() {
        return SymbolKind.get(jsonData.getInt("kind"));
    }

    public DocumentSymbol setKind(SymbolKind kind) {
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

    public DocumentSymbol setDeprecated(Boolean deprecated) {
        jsonData.putOpt("deprecated", deprecated);
        return this;
    }

    /**
     * The range enclosing this symbol not including leading/trailing whitespace but everything else
     * like comments. This information is typically used to determine if the the clients cursor is
     * inside the symbol to reveal in the symbol in the UI.
     */
    public Range getRange() {
        return new Range(jsonData.getJSONObject("range"));
    }

    public DocumentSymbol setRange(Range range) {
        jsonData.put("range", range.jsonData);
        return this;
    }

    /**
     * The range that should be selected and revealed when this symbol is being picked, e.g the name
     * of a function. Must be contained by the the `range`.
     */
    public Range getSelectionRange() {
        return new Range(jsonData.getJSONObject("selectionRange"));
    }

    public DocumentSymbol setSelectionRange(Range selectionRange) {
        jsonData.put("selectionRange", selectionRange.jsonData);
        return this;
    }

    /**
     * Children of this symbol, e.g. properties of a class.
     */
    public List<DocumentSymbol> getChildren() {
        final JSONArray json = jsonData.optJSONArray("children");
        if (json == null) {
            return null;
        }
        final List<DocumentSymbol> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new DocumentSymbol(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public DocumentSymbol setChildren(List<DocumentSymbol> children) {
        if (children != null) {
            final JSONArray json = new JSONArray();
            for (DocumentSymbol documentSymbol : children) {
                json.put(documentSymbol.jsonData);
            }
            jsonData.put("children", json);
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
        DocumentSymbol other = (DocumentSymbol) obj;
        if (!Objects.equals(this.getName(), other.getName())) {
            return false;
        }
        if (!Objects.equals(this.getDetail(), other.getDetail())) {
            return false;
        }
        if (this.getKind() != other.getKind()) {
            return false;
        }
        if (!Objects.equals(this.getDeprecated(), other.getDeprecated())) {
            return false;
        }
        if (!Objects.equals(this.getRange(), other.getRange())) {
            return false;
        }
        if (!Objects.equals(this.getSelectionRange(), other.getSelectionRange())) {
            return false;
        }
        if (!Objects.equals(this.getChildren(), other.getChildren())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + Objects.hashCode(this.getName());
        if (this.getDetail() != null) {
            hash = 29 * hash + Objects.hashCode(this.getDetail());
        }
        hash = 29 * hash + Objects.hashCode(this.getKind());
        if (this.getDeprecated() != null) {
            hash = 29 * hash + Boolean.hashCode(this.getDeprecated());
        }
        hash = 29 * hash + Objects.hashCode(this.getRange());
        hash = 29 * hash + Objects.hashCode(this.getSelectionRange());
        if (this.getChildren() != null) {
            hash = 29 * hash + Objects.hashCode(this.getChildren());
        }
        return hash;
    }

    /**
     * Creates a new symbol information literal.
     *
     * @param name The name of the symbol.
     * @param detail The detail of the symbol.
     * @param kind The kind of the symbol.
     * @param range The range of the symbol.
     * @param selectionRange The selectionRange of the symbol.
     * @param children Children of the symbol.
     */
    public static DocumentSymbol create(String name, String detail, SymbolKind kind, Range range, Range selectionRange, List<DocumentSymbol> children) {
        final JSONObject json = new JSONObject();
        json.put("name", name);
        json.putOpt("detail", detail);
        json.put("kind", kind.getIntValue());
        json.put("range", range.jsonData);
        json.put("selectionRange", selectionRange.jsonData);
        if (children != null) {
            JSONArray childrenJsonArr = new JSONArray();
            for (DocumentSymbol documentSymbol : children) {
                childrenJsonArr.put(documentSymbol.jsonData);
            }
            json.put("children", childrenJsonArr);
        }
        return new DocumentSymbol(json);
    }
}
