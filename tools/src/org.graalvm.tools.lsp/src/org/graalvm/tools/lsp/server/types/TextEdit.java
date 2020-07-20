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
 * A text edit applicable to a text document.
 */
public class TextEdit extends JSONBase {

    TextEdit(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The range of the text document to be manipulated. To insert text into a document create a
     * range where start === end.
     */
    public Range getRange() {
        return new Range(jsonData.getJSONObject("range"));
    }

    public TextEdit setRange(Range range) {
        jsonData.put("range", range.jsonData);
        return this;
    }

    /**
     * The string to be inserted. For delete operations use an empty string.
     */
    public String getNewText() {
        return jsonData.getString("newText");
    }

    public TextEdit setNewText(String newText) {
        jsonData.put("newText", newText);
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
        TextEdit other = (TextEdit) obj;
        if (!Objects.equals(this.getRange(), other.getRange())) {
            return false;
        }
        if (!Objects.equals(this.getNewText(), other.getNewText())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.getRange());
        hash = 97 * hash + Objects.hashCode(this.getNewText());
        return hash;
    }

    /**
     * Creates a replace text edit.
     *
     * @param range The range of text to be replaced.
     * @param newText The new text.
     */
    public static TextEdit replace(Range range, String newText) {
        final JSONObject json = new JSONObject();
        json.put("range", range.jsonData);
        json.put("newText", newText);
        return new TextEdit(json);
    }

    /**
     * Creates a insert text edit.
     *
     * @param position The position to insert the text at.
     * @param newText The text to be inserted.
     */
    public static TextEdit insert(Position position, String newText) {
        final JSONObject json = new JSONObject();
        json.put("range", Range.create(position, position).jsonData);
        json.put("newText", newText);
        return new TextEdit(json);
    }

    /**
     * Creates a delete text edit.
     *
     * @param range The range of text to be deleted.
     */
    public static TextEdit del(Range range) {
        final JSONObject json = new JSONObject();
        json.put("range", range.jsonData);
        json.put("newText", "");
        return new TextEdit(json);
    }
}
