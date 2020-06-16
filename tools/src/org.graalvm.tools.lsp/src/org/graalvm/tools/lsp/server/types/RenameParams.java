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
 * The parameters of a [RenameRequest](#RenameRequest).
 */
public class RenameParams extends WorkDoneProgressParams {

    RenameParams(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The document to rename.
     */
    public TextDocumentIdentifier getTextDocument() {
        return new TextDocumentIdentifier(jsonData.getJSONObject("textDocument"));
    }

    public RenameParams setTextDocument(TextDocumentIdentifier textDocument) {
        jsonData.put("textDocument", textDocument.jsonData);
        return this;
    }

    /**
     * The position at which this request was sent.
     */
    public Position getPosition() {
        return new Position(jsonData.getJSONObject("position"));
    }

    public RenameParams setPosition(Position position) {
        jsonData.put("position", position.jsonData);
        return this;
    }

    /**
     * The new name of the symbol. If the given name is not valid the request must return a
     * [ResponseError](#ResponseError) with an appropriate message set.
     */
    public String getNewName() {
        return jsonData.getString("newName");
    }

    public RenameParams setNewName(String newName) {
        jsonData.put("newName", newName);
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
        RenameParams other = (RenameParams) obj;
        if (!Objects.equals(this.getTextDocument(), other.getTextDocument())) {
            return false;
        }
        if (!Objects.equals(this.getPosition(), other.getPosition())) {
            return false;
        }
        if (!Objects.equals(this.getNewName(), other.getNewName())) {
            return false;
        }
        if (!Objects.equals(this.getWorkDoneToken(), other.getWorkDoneToken())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 2;
        hash = 43 * hash + Objects.hashCode(this.getTextDocument());
        hash = 43 * hash + Objects.hashCode(this.getPosition());
        hash = 43 * hash + Objects.hashCode(this.getNewName());
        if (this.getWorkDoneToken() != null) {
            hash = 43 * hash + Objects.hashCode(this.getWorkDoneToken());
        }
        return hash;
    }

    public static RenameParams create(TextDocumentIdentifier textDocument, Position position, String newName) {
        final JSONObject json = new JSONObject();
        json.put("textDocument", textDocument.jsonData);
        json.put("position", position.jsonData);
        json.put("newName", newName);
        return new RenameParams(json);
    }
}
