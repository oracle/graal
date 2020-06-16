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
 * Provider options for a [CodeActionRequest](#CodeActionRequest).
 */
public class CodeActionOptions extends WorkDoneProgressOptions {

    CodeActionOptions(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * CodeActionKinds that this server may return.
     *
     * The list of kinds may be generic, such as `CodeActionKind.Refactor`, or the server may list
     * out every specific kind they provide.
     */
    public List<CodeActionKind> getCodeActionKinds() {
        final JSONArray json = jsonData.optJSONArray("codeActionKinds");
        if (json == null) {
            return null;
        }
        final List<CodeActionKind> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(CodeActionKind.get(json.getString(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public CodeActionOptions setCodeActionKinds(List<CodeActionKind> codeActionKinds) {
        if (codeActionKinds != null) {
            final JSONArray json = new JSONArray();
            for (CodeActionKind codeActionKind : codeActionKinds) {
                json.put(codeActionKind.getStringValue());
            }
            jsonData.put("codeActionKinds", json);
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
        CodeActionOptions other = (CodeActionOptions) obj;
        if (!Objects.equals(this.getCodeActionKinds(), other.getCodeActionKinds())) {
            return false;
        }
        if (!Objects.equals(this.getWorkDoneProgress(), other.getWorkDoneProgress())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getCodeActionKinds() != null) {
            hash = 89 * hash + Objects.hashCode(this.getCodeActionKinds());
        }
        if (this.getWorkDoneProgress() != null) {
            hash = 89 * hash + Boolean.hashCode(this.getWorkDoneProgress());
        }
        return hash;
    }

    public static CodeActionOptions create() {
        final JSONObject json = new JSONObject();
        return new CodeActionOptions(json);
    }
}
