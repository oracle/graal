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
 * The parameters passed via a apply workspace edit request.
 */
public class ApplyWorkspaceEditParams extends JSONBase {

    ApplyWorkspaceEditParams(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * An optional label of the workspace edit. This label is presented in the user interface for
     * example on an undo stack to undo the workspace edit.
     */
    public String getLabel() {
        return jsonData.optString("label", null);
    }

    public ApplyWorkspaceEditParams setLabel(String label) {
        jsonData.putOpt("label", label);
        return this;
    }

    /**
     * The edits to apply.
     */
    public WorkspaceEdit getEdit() {
        return new WorkspaceEdit(jsonData.getJSONObject("edit"));
    }

    public ApplyWorkspaceEditParams setEdit(WorkspaceEdit edit) {
        jsonData.put("edit", edit.jsonData);
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
        ApplyWorkspaceEditParams other = (ApplyWorkspaceEditParams) obj;
        if (!Objects.equals(this.getLabel(), other.getLabel())) {
            return false;
        }
        if (!Objects.equals(this.getEdit(), other.getEdit())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getLabel() != null) {
            hash = 19 * hash + Objects.hashCode(this.getLabel());
        }
        hash = 19 * hash + Objects.hashCode(this.getEdit());
        return hash;
    }

    public static ApplyWorkspaceEditParams create(WorkspaceEdit edit) {
        final JSONObject json = new JSONObject();
        json.put("edit", edit.jsonData);
        return new ApplyWorkspaceEditParams(json);
    }
}
