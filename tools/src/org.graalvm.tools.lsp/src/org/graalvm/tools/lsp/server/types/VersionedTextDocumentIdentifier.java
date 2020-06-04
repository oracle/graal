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
 * An identifier to denote a specific version of a text document.
 */
public class VersionedTextDocumentIdentifier extends TextDocumentIdentifier {

    VersionedTextDocumentIdentifier(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The version number of this document. If a versioned text document identifier is sent from the
     * server to the client and the file is not open in the editor (the server has not received an
     * open notification before) the server can send `null` to indicate that the version is unknown
     * and the content on disk is the truth (as speced with document content ownership).
     */
    public Integer getVersion() {
        Object obj = jsonData.get("version");
        return JSONObject.NULL.equals(obj) ? null : (Integer) obj;
    }

    public VersionedTextDocumentIdentifier setVersion(Integer version) {
        jsonData.put("version", version == null ? JSONObject.NULL : version);
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
        VersionedTextDocumentIdentifier other = (VersionedTextDocumentIdentifier) obj;
        if (!Objects.equals(this.getVersion(), other.getVersion())) {
            return false;
        }
        if (!Objects.equals(this.getUri(), other.getUri())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getVersion() != null) {
            hash = 73 * hash + Integer.hashCode(this.getVersion());
        }
        hash = 73 * hash + Objects.hashCode(this.getUri());
        return hash;
    }

    /**
     * Creates a new VersionedTextDocumentIdentifier literal.
     *
     * @param uri The document's uri.
     * @param version The document's version.
     */
    public static VersionedTextDocumentIdentifier create(String uri, Integer version) {
        final JSONObject json = new JSONObject();
        json.put("version", version == null ? JSONObject.NULL : version);
        json.put("uri", uri);
        return new VersionedTextDocumentIdentifier(json);
    }
}
