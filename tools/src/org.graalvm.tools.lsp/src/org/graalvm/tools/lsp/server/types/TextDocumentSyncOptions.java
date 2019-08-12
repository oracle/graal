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

public class TextDocumentSyncOptions {

    final JSONObject jsonData;

    TextDocumentSyncOptions(JSONObject jsonData) {
        this.jsonData = jsonData;
    }

    /**
     * Open and close notifications are sent to the server.
     */
    public Boolean getOpenClose() {
        return jsonData.optBoolean("openClose");
    }

    public TextDocumentSyncOptions setOpenClose(Boolean openClose) {
        jsonData.putOpt("openClose", openClose);
        return this;
    }

    /**
     * Change notifications are sent to the server. See TextDocumentSyncKind.None, TextDocumentSyncKind.Full
     * and TextDocumentSyncKind.Incremental.
     */
    public TextDocumentSyncKind getChange() {
        return TextDocumentSyncKind.get(jsonData.optInt("change"));
    }

    public TextDocumentSyncOptions setChange(TextDocumentSyncKind change) {
        jsonData.putOpt("change", change != null ? change.getIntValue() : null);
        return this;
    }

    /**
     * Will save notifications are sent to the server.
     */
    public Boolean getWillSave() {
        return jsonData.optBoolean("willSave");
    }

    public TextDocumentSyncOptions setWillSave(Boolean willSave) {
        jsonData.putOpt("willSave", willSave);
        return this;
    }

    /**
     * Will save wait until requests are sent to the server.
     */
    public Boolean getWillSaveWaitUntil() {
        return jsonData.optBoolean("willSaveWaitUntil");
    }

    public TextDocumentSyncOptions setWillSaveWaitUntil(Boolean willSaveWaitUntil) {
        jsonData.putOpt("willSaveWaitUntil", willSaveWaitUntil);
        return this;
    }

    /**
     * Save notifications are sent to the server.
     */
    public SaveOptions getSave() {
        return jsonData.has("save") ? new SaveOptions(jsonData.optJSONObject("save")) : null;
    }

    public TextDocumentSyncOptions setSave(SaveOptions save) {
        jsonData.putOpt("save", save != null ? save.jsonData : null);
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
        TextDocumentSyncOptions other = (TextDocumentSyncOptions) obj;
        if (this.getOpenClose() != other.getOpenClose()) {
            return false;
        }
        if (this.getChange() != other.getChange()) {
            return false;
        }
        if (this.getWillSave() != other.getWillSave()) {
            return false;
        }
        if (this.getWillSaveWaitUntil() != other.getWillSaveWaitUntil()) {
            return false;
        }
        if (!Objects.equals(this.getSave(), other.getSave())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getOpenClose() != null) {
            hash = 31 * hash + Boolean.hashCode(this.getOpenClose());
        }
        if (this.getChange() != null) {
            hash = 31 * hash + Objects.hashCode(this.getChange());
        }
        if (this.getWillSave() != null) {
            hash = 31 * hash + Boolean.hashCode(this.getWillSave());
        }
        if (this.getWillSaveWaitUntil() != null) {
            hash = 31 * hash + Boolean.hashCode(this.getWillSaveWaitUntil());
        }
        if (this.getSave() != null) {
            hash = 31 * hash + Objects.hashCode(this.getSave());
        }
        return hash;
    }

    public static TextDocumentSyncOptions create() {
        final JSONObject json = new JSONObject();
        return new TextDocumentSyncOptions(json);
    }
}
