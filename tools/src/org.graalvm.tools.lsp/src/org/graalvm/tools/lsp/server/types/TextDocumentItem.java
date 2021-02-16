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
 * An item to transfer a text document from the client to the server.
 */
public class TextDocumentItem extends JSONBase {

    TextDocumentItem(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The text document's uri.
     */
    public String getUri() {
        return jsonData.getString("uri");
    }

    public TextDocumentItem setUri(String uri) {
        jsonData.put("uri", uri);
        return this;
    }

    /**
     * The text document's language identifier.
     */
    public String getLanguageId() {
        return jsonData.getString("languageId");
    }

    public TextDocumentItem setLanguageId(String languageId) {
        jsonData.put("languageId", languageId);
        return this;
    }

    /**
     * The version number of this document (it will increase after each change, including
     * undo/redo).
     */
    public int getVersion() {
        return jsonData.getInt("version");
    }

    public TextDocumentItem setVersion(int version) {
        jsonData.put("version", version);
        return this;
    }

    /**
     * The content of the opened text document.
     */
    public String getText() {
        return jsonData.getString("text");
    }

    public TextDocumentItem setText(String text) {
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
        TextDocumentItem other = (TextDocumentItem) obj;
        if (!Objects.equals(this.getUri(), other.getUri())) {
            return false;
        }
        if (!Objects.equals(this.getLanguageId(), other.getLanguageId())) {
            return false;
        }
        if (this.getVersion() != other.getVersion()) {
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
        hash = 97 * hash + Objects.hashCode(this.getUri());
        hash = 97 * hash + Objects.hashCode(this.getLanguageId());
        hash = 97 * hash + Integer.hashCode(this.getVersion());
        hash = 97 * hash + Objects.hashCode(this.getText());
        return hash;
    }

    /**
     * Creates a new TextDocumentItem literal.
     *
     * @param uri The document's uri.
     * @param languageId The document's language identifier.
     * @param version The document's version number.
     * @param text The document's text.
     */
    public static TextDocumentItem create(String uri, String languageId, int version, String text) {
        final JSONObject json = new JSONObject();
        json.put("uri", uri);
        json.put("languageId", languageId);
        json.put("version", version);
        json.put("text", text);
        return new TextDocumentItem(json);
    }
}
