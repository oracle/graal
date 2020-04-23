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
 * The parameters of a [CodeActionRequest](#CodeActionRequest).
 */
public class CodeActionParams extends WorkDoneProgressParams {

    CodeActionParams(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The document in which the command was invoked.
     */
    public TextDocumentIdentifier getTextDocument() {
        return new TextDocumentIdentifier(jsonData.getJSONObject("textDocument"));
    }

    public CodeActionParams setTextDocument(TextDocumentIdentifier textDocument) {
        jsonData.put("textDocument", textDocument.jsonData);
        return this;
    }

    /**
     * The range for which the command was invoked.
     */
    public Range getRange() {
        return new Range(jsonData.getJSONObject("range"));
    }

    public CodeActionParams setRange(Range range) {
        jsonData.put("range", range.jsonData);
        return this;
    }

    /**
     * Context carrying additional information.
     */
    public CodeActionContext getContext() {
        return new CodeActionContext(jsonData.getJSONObject("context"));
    }

    public CodeActionParams setContext(CodeActionContext context) {
        jsonData.put("context", context.jsonData);
        return this;
    }

    /**
     * An optional token that a server can use to report partial results (e.g. streaming) to the
     * client.
     */
    public Object getPartialResultToken() {
        return jsonData.opt("partialResultToken");
    }

    public CodeActionParams setPartialResultToken(Object partialResultToken) {
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
        CodeActionParams other = (CodeActionParams) obj;
        if (!Objects.equals(this.getTextDocument(), other.getTextDocument())) {
            return false;
        }
        if (!Objects.equals(this.getRange(), other.getRange())) {
            return false;
        }
        if (!Objects.equals(this.getContext(), other.getContext())) {
            return false;
        }
        if (!Objects.equals(this.getPartialResultToken(), other.getPartialResultToken())) {
            return false;
        }
        if (!Objects.equals(this.getWorkDoneToken(), other.getWorkDoneToken())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 43 * hash + Objects.hashCode(this.getTextDocument());
        hash = 43 * hash + Objects.hashCode(this.getRange());
        hash = 43 * hash + Objects.hashCode(this.getContext());
        if (this.getPartialResultToken() != null) {
            hash = 43 * hash + Objects.hashCode(this.getPartialResultToken());
        }
        if (this.getWorkDoneToken() != null) {
            hash = 43 * hash + Objects.hashCode(this.getWorkDoneToken());
        }
        return hash;
    }

    public static CodeActionParams create(TextDocumentIdentifier textDocument, Range range, CodeActionContext context) {
        final JSONObject json = new JSONObject();
        json.put("textDocument", textDocument.jsonData);
        json.put("range", range.jsonData);
        json.put("context", context.jsonData);
        return new CodeActionParams(json);
    }
}
