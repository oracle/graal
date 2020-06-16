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
 * An event describing a change to a text document. If range and rangeLength are omitted the new
 * text is considered to be the full content of the document.
 */
public class TextDocumentContentChangeEvent extends JSONBase {

    TextDocumentContentChangeEvent(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The range of the document that changed.
     */
    public Range getRange() {
        return jsonData.has("range") ? new Range(jsonData.optJSONObject("range")) : null;
    }

    public TextDocumentContentChangeEvent setRange(Range range) {
        jsonData.putOpt("range", range != null ? range.jsonData : null);
        return this;
    }

    /**
     * The optional length of the range that got replaced.
     *
     * @deprecated use range instead.
     */
    @Deprecated
    public Integer getRangeLength() {
        return jsonData.has("rangeLength") ? jsonData.getInt("rangeLength") : null;
    }

    public TextDocumentContentChangeEvent setRangeLength(Integer rangeLength) {
        jsonData.putOpt("rangeLength", rangeLength);
        return this;
    }

    /**
     * The new text for the provided range.
     */
    public String getText() {
        return jsonData.getString("text");
    }

    public TextDocumentContentChangeEvent setText(String text) {
        jsonData.put("text", text);
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
        TextDocumentContentChangeEvent other = (TextDocumentContentChangeEvent) obj;
        if (!Objects.equals(this.getRange(), other.getRange())) {
            return false;
        }
        if (!Objects.equals(this.getRangeLength(), other.getRangeLength())) {
            return false;
        }
        if (!Objects.equals(this.getText(), other.getText())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.getRange());
        if (this.getRangeLength() != null) {
            hash = 97 * hash + Integer.hashCode(this.getRangeLength());
        }
        hash = 97 * hash + Objects.hashCode(this.getText());
        return hash;
    }

    public static TextDocumentContentChangeEvent create(String text) {
        final JSONObject json = new JSONObject();
        json.put("text", text);
        return new TextDocumentContentChangeEvent(json);
    }
}
