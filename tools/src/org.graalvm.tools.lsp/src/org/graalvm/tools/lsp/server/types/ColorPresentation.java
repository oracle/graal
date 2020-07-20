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

public class ColorPresentation extends JSONBase {

    ColorPresentation(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The label of this color presentation. It will be shown on the color picker header. By default
     * this is also the text that is inserted when selecting this color presentation.
     */
    public String getLabel() {
        return jsonData.getString("label");
    }

    public ColorPresentation setLabel(String label) {
        jsonData.put("label", label);
        return this;
    }

    /**
     * An [edit](#TextEdit) which is applied to a document when selecting this presentation for the
     * color. When `falsy` the [label](#ColorPresentation.label) is used.
     */
    public TextEdit getTextEdit() {
        return jsonData.has("textEdit") ? new TextEdit(jsonData.optJSONObject("textEdit")) : null;
    }

    public ColorPresentation setTextEdit(TextEdit textEdit) {
        jsonData.putOpt("textEdit", textEdit != null ? textEdit.jsonData : null);
        return this;
    }

    /**
     * An optional array of additional [text edits](#TextEdit) that are applied when selecting this
     * color presentation. Edits must not overlap with the main [edit](#ColorPresentation.textEdit)
     * nor with themselves.
     */
    public List<TextEdit> getAdditionalTextEdits() {
        final JSONArray json = jsonData.optJSONArray("additionalTextEdits");
        if (json == null) {
            return null;
        }
        final List<TextEdit> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new TextEdit(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public ColorPresentation setAdditionalTextEdits(List<TextEdit> additionalTextEdits) {
        if (additionalTextEdits != null) {
            final JSONArray json = new JSONArray();
            for (TextEdit textEdit : additionalTextEdits) {
                json.put(textEdit.jsonData);
            }
            jsonData.put("additionalTextEdits", json);
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
        ColorPresentation other = (ColorPresentation) obj;
        if (!Objects.equals(this.getLabel(), other.getLabel())) {
            return false;
        }
        if (!Objects.equals(this.getTextEdit(), other.getTextEdit())) {
            return false;
        }
        if (!Objects.equals(this.getAdditionalTextEdits(), other.getAdditionalTextEdits())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + Objects.hashCode(this.getLabel());
        if (this.getTextEdit() != null) {
            hash = 83 * hash + Objects.hashCode(this.getTextEdit());
        }
        if (this.getAdditionalTextEdits() != null) {
            hash = 83 * hash + Objects.hashCode(this.getAdditionalTextEdits());
        }
        return hash;
    }

    /**
     * Creates a new ColorInformation literal.
     */
    public static ColorPresentation create(String label, TextEdit textEdit, List<TextEdit> additionalTextEdits) {
        final JSONObject json = new JSONObject();
        json.put("label", label);
        json.putOpt("textEdit", textEdit != null ? textEdit.jsonData : null);
        if (additionalTextEdits != null) {
            JSONArray additionalTextEditsJsonArr = new JSONArray();
            for (TextEdit additionalTextEdit : additionalTextEdits) {
                additionalTextEditsJsonArr.put(additionalTextEdit.jsonData);
            }
            json.put("additionalTextEdits", additionalTextEditsJsonArr);
        }
        return new ColorPresentation(json);
    }
}
