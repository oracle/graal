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
 * Contains additional diagnostic information about the context in which a [code
 * action](#CodeActionProvider.provideCodeActions) is run.
 */
public class CodeActionContext extends JSONBase {

    CodeActionContext(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * An array of diagnostics known on the client side overlapping the range provided to the
     * `textDocument/codeAction` request. They are provied so that the server knows which errors are
     * currently presented to the user for the given range. There is no guarantee that these
     * accurately reflect the error state of the resource. The primary parameter to compute code
     * actions is the provided range.
     */
    public List<Diagnostic> getDiagnostics() {
        final JSONArray json = jsonData.getJSONArray("diagnostics");
        final List<Diagnostic> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new Diagnostic(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public CodeActionContext setDiagnostics(List<Diagnostic> diagnostics) {
        final JSONArray json = new JSONArray();
        for (Diagnostic diagnostic : diagnostics) {
            json.put(diagnostic.jsonData);
        }
        jsonData.put("diagnostics", json);
        return this;
    }

    /**
     * Requested kind of actions to return.
     *
     * Actions not of this kind are filtered out by the client before being shown. So servers can
     * omit computing them.
     */
    public List<CodeActionKind> getOnly() {
        final JSONArray json = jsonData.optJSONArray("only");
        if (json == null) {
            return null;
        }
        final List<CodeActionKind> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(CodeActionKind.get(json.getString(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public CodeActionContext setOnly(List<CodeActionKind> only) {
        if (only != null) {
            final JSONArray json = new JSONArray();
            for (CodeActionKind codeActionKind : only) {
                json.put(codeActionKind.getStringValue());
            }
            jsonData.put("only", json);
        }
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
        CodeActionContext other = (CodeActionContext) obj;
        if (!Objects.equals(this.getDiagnostics(), other.getDiagnostics())) {
            return false;
        }
        if (!Objects.equals(this.getOnly(), other.getOnly())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.getDiagnostics());
        if (this.getOnly() != null) {
            hash = 97 * hash + Objects.hashCode(this.getOnly());
        }
        return hash;
    }

    /**
     * Creates a new CodeActionContext literal.
     */
    public static CodeActionContext create(List<Diagnostic> diagnostics, List<CodeActionKind> only) {
        final JSONObject json = new JSONObject();
        JSONArray diagnosticsJsonArr = new JSONArray();
        for (Diagnostic diagnostic : diagnostics) {
            diagnosticsJsonArr.put(diagnostic.jsonData);
        }
        json.put("diagnostics", diagnosticsJsonArr);
        if (only != null) {
            JSONArray onlyJsonArr = new JSONArray();
            for (CodeActionKind codeActionKind : only) {
                onlyJsonArr.put(codeActionKind.getStringValue());
            }
            json.put("only", onlyJsonArr);
        }
        return new CodeActionContext(json);
    }
}
