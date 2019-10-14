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
 * Defines the capabilities provided by the client.
 */
public class ClientCapabilities {

    final JSONObject jsonData;

    ClientCapabilities(JSONObject jsonData) {
        this.jsonData = jsonData;
    }

    /**
     * Workspace specific client capabilities.
     */
    public WorkspaceClientCapabilities getWorkspace() {
        return jsonData.has("workspace") ? new WorkspaceClientCapabilities(jsonData.optJSONObject("workspace")) : null;
    }

    public ClientCapabilities setWorkspace(WorkspaceClientCapabilities workspace) {
        jsonData.putOpt("workspace", workspace != null ? workspace.jsonData : null);
        return this;
    }

    /**
     * Text document specific client capabilities.
     */
    public TextDocumentClientCapabilities getTextDocument() {
        return jsonData.has("textDocument") ? new TextDocumentClientCapabilities(jsonData.optJSONObject("textDocument")) : null;
    }

    public ClientCapabilities setTextDocument(TextDocumentClientCapabilities textDocument) {
        jsonData.putOpt("textDocument", textDocument != null ? textDocument.jsonData : null);
        return this;
    }

    /**
     * Experimental client capabilities.
     */
    public Object getExperimental() {
        return jsonData.opt("experimental");
    }

    public ClientCapabilities setExperimental(Object experimental) {
        jsonData.putOpt("experimental", experimental);
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
        ClientCapabilities other = (ClientCapabilities) obj;
        if (!Objects.equals(this.getWorkspace(), other.getWorkspace())) {
            return false;
        }
        if (!Objects.equals(this.getTextDocument(), other.getTextDocument())) {
            return false;
        }
        if (!Objects.equals(this.getExperimental(), other.getExperimental())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getWorkspace() != null) {
            hash = 79 * hash + Objects.hashCode(this.getWorkspace());
        }
        if (this.getTextDocument() != null) {
            hash = 79 * hash + Objects.hashCode(this.getTextDocument());
        }
        if (this.getExperimental() != null) {
            hash = 79 * hash + Objects.hashCode(this.getExperimental());
        }
        return hash;
    }

    public static ClientCapabilities create() {
        final JSONObject json = new JSONObject();
        return new ClientCapabilities(json);
    }
}
