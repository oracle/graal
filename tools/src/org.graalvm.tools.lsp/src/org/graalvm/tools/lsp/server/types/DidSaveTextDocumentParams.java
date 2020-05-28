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
 * The parameters send in a save text document notification.
 */
public class DidSaveTextDocumentParams extends JSONBase {

    DidSaveTextDocumentParams(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The document that was closed.
     */
    public VersionedTextDocumentIdentifier getTextDocument() {
        return new VersionedTextDocumentIdentifier(jsonData.getJSONObject("textDocument"));
    }

    public DidSaveTextDocumentParams setTextDocument(VersionedTextDocumentIdentifier textDocument) {
        jsonData.put("textDocument", textDocument.jsonData);
        return this;
    }

    /**
     * Optional the content when saved. Depends on the includeText value when the save notification
     * was requested.
     */
    public String getText() {
        return jsonData.optString("text", null);
    }

    public DidSaveTextDocumentParams setText(String text) {
        jsonData.putOpt("text", text);
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
        DidSaveTextDocumentParams other = (DidSaveTextDocumentParams) obj;
        if (!Objects.equals(this.getTextDocument(), other.getTextDocument())) {
            return false;
        }
        if (!Objects.equals(this.getText(), other.getText())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 79 * hash + Objects.hashCode(this.getTextDocument());
        if (this.getText() != null) {
            hash = 79 * hash + Objects.hashCode(this.getText());
        }
        return hash;
    }

    public static DidSaveTextDocumentParams create(VersionedTextDocumentIdentifier textDocument) {
        final JSONObject json = new JSONObject();
        json.put("textDocument", textDocument.jsonData);
        return new DidSaveTextDocumentParams(json);
    }
}
