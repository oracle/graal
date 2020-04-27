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

public class TextDocumentSyncClientCapabilities extends JSONBase {

    TextDocumentSyncClientCapabilities(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Whether text document synchronization supports dynamic registration.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getDynamicRegistration() {
        return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
    }

    public TextDocumentSyncClientCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
        jsonData.putOpt("dynamicRegistration", dynamicRegistration);
        return this;
    }

    /**
     * The client supports sending will save notifications.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getWillSave() {
        return jsonData.has("willSave") ? jsonData.getBoolean("willSave") : null;
    }

    public TextDocumentSyncClientCapabilities setWillSave(Boolean willSave) {
        jsonData.putOpt("willSave", willSave);
        return this;
    }

    /**
     * The client supports sending a will save request and waits for a response providing text edits
     * which will be applied to the document before it is saved.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getWillSaveWaitUntil() {
        return jsonData.has("willSaveWaitUntil") ? jsonData.getBoolean("willSaveWaitUntil") : null;
    }

    public TextDocumentSyncClientCapabilities setWillSaveWaitUntil(Boolean willSaveWaitUntil) {
        jsonData.putOpt("willSaveWaitUntil", willSaveWaitUntil);
        return this;
    }

    /**
     * The client supports did save notifications.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getDidSave() {
        return jsonData.has("didSave") ? jsonData.getBoolean("didSave") : null;
    }

    public TextDocumentSyncClientCapabilities setDidSave(Boolean didSave) {
        jsonData.putOpt("didSave", didSave);
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
        TextDocumentSyncClientCapabilities other = (TextDocumentSyncClientCapabilities) obj;
        if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
            return false;
        }
        if (!Objects.equals(this.getWillSave(), other.getWillSave())) {
            return false;
        }
        if (!Objects.equals(this.getWillSaveWaitUntil(), other.getWillSaveWaitUntil())) {
            return false;
        }
        if (!Objects.equals(this.getDidSave(), other.getDidSave())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getDynamicRegistration() != null) {
            hash = 29 * hash + Boolean.hashCode(this.getDynamicRegistration());
        }
        if (this.getWillSave() != null) {
            hash = 29 * hash + Boolean.hashCode(this.getWillSave());
        }
        if (this.getWillSaveWaitUntil() != null) {
            hash = 29 * hash + Boolean.hashCode(this.getWillSaveWaitUntil());
        }
        if (this.getDidSave() != null) {
            hash = 29 * hash + Boolean.hashCode(this.getDidSave());
        }
        return hash;
    }

    public static TextDocumentSyncClientCapabilities create() {
        final JSONObject json = new JSONObject();
        return new TextDocumentSyncClientCapabilities(json);
    }
}
