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
 * The change text document notification's parameters.
 */
public class DidChangeTextDocumentParams extends JSONBase {

    DidChangeTextDocumentParams(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The document that did change. The version number points to the version after all provided
     * content changes have been applied.
     */
    public VersionedTextDocumentIdentifier getTextDocument() {
        return new VersionedTextDocumentIdentifier(jsonData.getJSONObject("textDocument"));
    }

    public DidChangeTextDocumentParams setTextDocument(VersionedTextDocumentIdentifier textDocument) {
        jsonData.put("textDocument", textDocument.jsonData);
        return this;
    }

    /**
     * The actual content changes. The content changes describe single state changes to the
     * document. So if there are two content changes c1 (at array index 0) and c2 (at array index 1)
     * for a document in state S then c1 moves the document from S to S' and c2 from S' to S''. So
     * c1 is computed on the state S and c2 is computed on the state S'.
     */
    public List<TextDocumentContentChangeEvent> getContentChanges() {
        final JSONArray json = jsonData.getJSONArray("contentChanges");
        final List<TextDocumentContentChangeEvent> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new TextDocumentContentChangeEvent(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public DidChangeTextDocumentParams setContentChanges(List<TextDocumentContentChangeEvent> contentChanges) {
        final JSONArray json = new JSONArray();
        for (TextDocumentContentChangeEvent textDocumentContentChangeEvent : contentChanges) {
            json.put(textDocumentContentChangeEvent.jsonData);
        }
        jsonData.put("contentChanges", json);
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
        DidChangeTextDocumentParams other = (DidChangeTextDocumentParams) obj;
        if (!Objects.equals(this.getTextDocument(), other.getTextDocument())) {
            return false;
        }
        if (!Objects.equals(this.getContentChanges(), other.getContentChanges())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 53 * hash + Objects.hashCode(this.getTextDocument());
        hash = 53 * hash + Objects.hashCode(this.getContentChanges());
        return hash;
    }

    public static DidChangeTextDocumentParams create(VersionedTextDocumentIdentifier textDocument, List<TextDocumentContentChangeEvent> contentChanges) {
        final JSONObject json = new JSONObject();
        json.put("textDocument", textDocument.jsonData);
        JSONArray contentChangesJsonArr = new JSONArray();
        for (TextDocumentContentChangeEvent textDocumentContentChangeEvent : contentChanges) {
            contentChangesJsonArr.put(textDocumentContentChangeEvent.jsonData);
        }
        json.put("contentChanges", contentChangesJsonArr);
        return new DidChangeTextDocumentParams(json);
    }
}
