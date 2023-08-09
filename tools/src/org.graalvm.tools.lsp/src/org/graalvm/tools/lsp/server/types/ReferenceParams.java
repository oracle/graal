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

import org.graalvm.shadowed.org.json.JSONObject;
import java.util.Objects;

/**
 * Parameters for a [ReferencesRequest](#ReferencesRequest).
 */
public class ReferenceParams extends TextDocumentPositionParams {

    ReferenceParams(JSONObject jsonData) {
        super(jsonData);
    }

    public ReferenceContext getContext() {
        return new ReferenceContext(jsonData.getJSONObject("context"));
    }

    public ReferenceParams setContext(ReferenceContext context) {
        jsonData.put("context", context.jsonData);
        return this;
    }

    /**
     * An optional token that a server can use to report work done progress.
     */
    public Object getWorkDoneToken() {
        return jsonData.opt("workDoneToken");
    }

    public ReferenceParams setWorkDoneToken(Object workDoneToken) {
        jsonData.putOpt("workDoneToken", workDoneToken);
        return this;
    }

    /**
     * An optional token that a server can use to report partial results (e.g. streaming) to the
     * client.
     */
    public Object getPartialResultToken() {
        return jsonData.opt("partialResultToken");
    }

    public ReferenceParams setPartialResultToken(Object partialResultToken) {
        jsonData.putOpt("partialResultToken", partialResultToken);
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
        ReferenceParams other = (ReferenceParams) obj;
        if (!Objects.equals(this.getContext(), other.getContext())) {
            return false;
        }
        if (!Objects.equals(this.getWorkDoneToken(), other.getWorkDoneToken())) {
            return false;
        }
        if (!Objects.equals(this.getPartialResultToken(), other.getPartialResultToken())) {
            return false;
        }
        if (!Objects.equals(this.getTextDocument(), other.getTextDocument())) {
            return false;
        }
        if (!Objects.equals(this.getPosition(), other.getPosition())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 37 * hash + Objects.hashCode(this.getContext());
        if (this.getWorkDoneToken() != null) {
            hash = 37 * hash + Objects.hashCode(this.getWorkDoneToken());
        }
        if (this.getPartialResultToken() != null) {
            hash = 37 * hash + Objects.hashCode(this.getPartialResultToken());
        }
        hash = 37 * hash + Objects.hashCode(this.getTextDocument());
        hash = 37 * hash + Objects.hashCode(this.getPosition());
        return hash;
    }

    public static ReferenceParams create(ReferenceContext context, TextDocumentIdentifier textDocument, Position position) {
        final JSONObject json = new JSONObject();
        json.put("context", context.jsonData);
        json.put("textDocument", textDocument.jsonData);
        json.put("position", position.jsonData);
        return new ReferenceParams(json);
    }
}
