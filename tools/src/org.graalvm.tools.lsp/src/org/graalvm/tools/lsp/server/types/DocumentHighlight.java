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
 * A document highlight is a range inside a text document which deserves special attention. Usually
 * a document highlight is visualized by changing the background color of its range.
 */
public class DocumentHighlight extends JSONBase {

    DocumentHighlight(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The range this highlight applies to.
     */
    public Range getRange() {
        return new Range(jsonData.getJSONObject("range"));
    }

    public DocumentHighlight setRange(Range range) {
        jsonData.put("range", range.jsonData);
        return this;
    }

    /**
     * The highlight kind, default is [text](#DocumentHighlightKind.Text).
     */
    public DocumentHighlightKind getKind() {
        return DocumentHighlightKind.get(jsonData.has("kind") ? jsonData.getInt("kind") : null);
    }

    public DocumentHighlight setKind(DocumentHighlightKind kind) {
        jsonData.putOpt("kind", kind != null ? kind.getIntValue() : null);
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
        DocumentHighlight other = (DocumentHighlight) obj;
        if (!Objects.equals(this.getRange(), other.getRange())) {
            return false;
        }
        if (this.getKind() != other.getKind()) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 47 * hash + Objects.hashCode(this.getRange());
        if (this.getKind() != null) {
            hash = 47 * hash + Objects.hashCode(this.getKind());
        }
        return hash;
    }

    /**
     * Create a DocumentHighlight object.
     *
     * @param range The range the highlight applies to.
     */
    public static DocumentHighlight create(Range range, DocumentHighlightKind kind) {
        final JSONObject json = new JSONObject();
        json.put("range", range.jsonData);
        json.putOpt("kind", kind != null ? kind.getIntValue() : null);
        return new DocumentHighlight(json);
    }
}
