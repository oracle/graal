/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * Parameters for a [ColorPresentationRequest](#ColorPresentationRequest).
 */
public class ColorPresentationParams {

    final JSONObject jsonData;

    ColorPresentationParams(JSONObject jsonData) {
        this.jsonData = jsonData;
    }

    /**
     * The text document.
     */
    public TextDocumentIdentifier getTextDocument() {
        return new TextDocumentIdentifier(jsonData.getJSONObject("textDocument"));
    }

    public ColorPresentationParams setTextDocument(TextDocumentIdentifier textDocument) {
        jsonData.put("textDocument", textDocument.jsonData);
        return this;
    }

    /**
     * The color to request presentations for.
     */
    public Color getColor() {
        return new Color(jsonData.getJSONObject("color"));
    }

    public ColorPresentationParams setColor(Color color) {
        jsonData.put("color", color.jsonData);
        return this;
    }

    /**
     * The range where the color would be inserted. Serves as a context.
     */
    public Range getRange() {
        return new Range(jsonData.getJSONObject("range"));
    }

    public ColorPresentationParams setRange(Range range) {
        jsonData.put("range", range.jsonData);
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
        ColorPresentationParams other = (ColorPresentationParams) obj;
        if (!Objects.equals(this.getTextDocument(), other.getTextDocument())) {
            return false;
        }
        if (!Objects.equals(this.getColor(), other.getColor())) {
            return false;
        }
        if (!Objects.equals(this.getRange(), other.getRange())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 2;
        hash = 83 * hash + Objects.hashCode(this.getTextDocument());
        hash = 83 * hash + Objects.hashCode(this.getColor());
        hash = 83 * hash + Objects.hashCode(this.getRange());
        return hash;
    }

    public static ColorPresentationParams create(TextDocumentIdentifier textDocument, Color color, Range range) {
        final JSONObject json = new JSONObject();
        json.put("textDocument", textDocument.jsonData);
        json.put("color", color.jsonData);
        json.put("range", range.jsonData);
        return new ColorPresentationParams(json);
    }
}
