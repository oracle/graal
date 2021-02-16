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
 * Completion parameters.
 */
public class CompletionParams extends TextDocumentPositionParams {

    CompletionParams(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The completion context. This is only available it the client specifies to send this using the
     * client capability `textDocument.completion.contextSupport === true`
     */
    public CompletionContext getContext() {
        return jsonData.has("context") ? new CompletionContext(jsonData.optJSONObject("context")) : null;
    }

    public CompletionParams setContext(CompletionContext context) {
        jsonData.putOpt("context", context != null ? context.jsonData : null);
        return this;
    }

    /**
     * An optional token that a server can use to report work done progress.
     */
    public Object getWorkDoneToken() {
        return jsonData.opt("workDoneToken");
    }

    public CompletionParams setWorkDoneToken(Object workDoneToken) {
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

    public CompletionParams setPartialResultToken(Object partialResultToken) {
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
        CompletionParams other = (CompletionParams) obj;
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
        int hash = 7;
        if (this.getContext() != null) {
            hash = 29 * hash + Objects.hashCode(this.getContext());
        }
        if (this.getWorkDoneToken() != null) {
            hash = 29 * hash + Objects.hashCode(this.getWorkDoneToken());
        }
        if (this.getPartialResultToken() != null) {
            hash = 29 * hash + Objects.hashCode(this.getPartialResultToken());
        }
        hash = 29 * hash + Objects.hashCode(this.getTextDocument());
        hash = 29 * hash + Objects.hashCode(this.getPosition());
        return hash;
    }

    public static CompletionParams create(TextDocumentIdentifier textDocument, Position position) {
        final JSONObject json = new JSONObject();
        json.put("textDocument", textDocument.jsonData);
        json.put("position", position.jsonData);
        return new CompletionParams(json);
    }
}
