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

import org.graalvm.shadowed.org.json.JSONObject;

import java.util.Objects;

/**
 * CompletionItems are the suggestions returned from the CompletionsRequest.
 */
public class CompletionItem extends JSONBase {

    CompletionItem(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The label of this completion item. By default this is also the text that is inserted when
     * selecting this completion.
     */
    public String getLabel() {
        return jsonData.getString("label");
    }

    public CompletionItem setLabel(String label) {
        jsonData.put("label", label);
        return this;
    }

    /**
     * If text is not falsy then it is inserted instead of the label.
     */
    public String getText() {
        return jsonData.optString("text", null);
    }

    public CompletionItem setText(String text) {
        jsonData.putOpt("text", text);
        return this;
    }

    /**
     * A string that should be used when comparing this item with other items. When `falsy` the
     * label is used.
     */
    public String getSortText() {
        return jsonData.optString("sortText", null);
    }

    public CompletionItem setSortText(String sortText) {
        jsonData.putOpt("sortText", sortText);
        return this;
    }

    /**
     * The item's type. Typically the client uses this information to render the item in the UI with
     * an icon.
     */
    public String getType() {
        return jsonData.optString("type", null);
    }

    public CompletionItem setType(String type) {
        jsonData.putOpt("type", type);
        return this;
    }

    /**
     * This value determines the location (in the CompletionsRequest's 'text' attribute) where the
     * completion text is added. If missing the text is added at the location specified by the
     * CompletionsRequest's 'column' attribute.
     */
    public Integer getStart() {
        return jsonData.has("start") ? jsonData.getInt("start") : null;
    }

    public CompletionItem setStart(Integer start) {
        jsonData.putOpt("start", start);
        return this;
    }

    /**
     * This value determines how many characters are overwritten by the completion text. If missing
     * the value 0 is assumed which results in the completion text being inserted.
     */
    public Integer getLength() {
        return jsonData.has("length") ? jsonData.getInt("length") : null;
    }

    public CompletionItem setLength(Integer length) {
        jsonData.putOpt("length", length);
        return this;
    }

    /**
     * Determines the start of the new selection after the text has been inserted (or replaced). The
     * start position must in the range 0 and length of the completion text. If omitted the
     * selection starts at the end of the completion text.
     */
    public Integer getSelectionStart() {
        return jsonData.has("selectionStart") ? jsonData.getInt("selectionStart") : null;
    }

    public CompletionItem setSelectionStart(Integer selectionStart) {
        jsonData.putOpt("selectionStart", selectionStart);
        return this;
    }

    /**
     * Determines the length of the new selection after the text has been inserted (or replaced).
     * The selection can not extend beyond the bounds of the completion text. If omitted the length
     * is assumed to be 0.
     */
    public Integer getSelectionLength() {
        return jsonData.has("selectionLength") ? jsonData.getInt("selectionLength") : null;
    }

    public CompletionItem setSelectionLength(Integer selectionLength) {
        jsonData.putOpt("selectionLength", selectionLength);
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
        CompletionItem other = (CompletionItem) obj;
        if (!Objects.equals(this.getLabel(), other.getLabel())) {
            return false;
        }
        if (!Objects.equals(this.getText(), other.getText())) {
            return false;
        }
        if (!Objects.equals(this.getSortText(), other.getSortText())) {
            return false;
        }
        if (!Objects.equals(this.getType(), other.getType())) {
            return false;
        }
        if (!Objects.equals(this.getStart(), other.getStart())) {
            return false;
        }
        if (!Objects.equals(this.getLength(), other.getLength())) {
            return false;
        }
        if (!Objects.equals(this.getSelectionStart(), other.getSelectionStart())) {
            return false;
        }
        if (!Objects.equals(this.getSelectionLength(), other.getSelectionLength())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(this.getLabel());
        if (this.getText() != null) {
            hash = 41 * hash + Objects.hashCode(this.getText());
        }
        if (this.getSortText() != null) {
            hash = 41 * hash + Objects.hashCode(this.getSortText());
        }
        if (this.getType() != null) {
            hash = 41 * hash + Objects.hashCode(this.getType());
        }
        if (this.getStart() != null) {
            hash = 41 * hash + Integer.hashCode(this.getStart());
        }
        if (this.getLength() != null) {
            hash = 41 * hash + Integer.hashCode(this.getLength());
        }
        if (this.getSelectionStart() != null) {
            hash = 41 * hash + Integer.hashCode(this.getSelectionStart());
        }
        if (this.getSelectionLength() != null) {
            hash = 41 * hash + Integer.hashCode(this.getSelectionLength());
        }
        return hash;
    }

    public static CompletionItem create(String label) {
        final JSONObject json = new JSONObject();
        json.put("label", label);
        return new CompletionItem(json);
    }
}
