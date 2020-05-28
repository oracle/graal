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

public class WorkspaceEditClientCapabilities extends JSONBase {

    WorkspaceEditClientCapabilities(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The client supports versioned document changes in `WorkspaceEdit`s.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getDocumentChanges() {
        return jsonData.has("documentChanges") ? jsonData.getBoolean("documentChanges") : null;
    }

    public WorkspaceEditClientCapabilities setDocumentChanges(Boolean documentChanges) {
        jsonData.putOpt("documentChanges", documentChanges);
        return this;
    }

    /**
     * The resource operations the client supports. Clients should at least support 'create',
     * 'rename' and 'delete' files and folders.
     *
     * @since 3.13.0
     */
    public List<ResourceOperationKind> getResourceOperations() {
        final JSONArray json = jsonData.optJSONArray("resourceOperations");
        if (json == null) {
            return null;
        }
        final List<ResourceOperationKind> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(ResourceOperationKind.get(json.getString(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public WorkspaceEditClientCapabilities setResourceOperations(List<ResourceOperationKind> resourceOperations) {
        if (resourceOperations != null) {
            final JSONArray json = new JSONArray();
            for (ResourceOperationKind resourceOperationKind : resourceOperations) {
                json.put(resourceOperationKind.getStringValue());
            }
            jsonData.put("resourceOperations", json);
        }
        return this;
    }

    /**
     * The failure handling strategy of a client if applying the workspace edit fails.
     *
     * @since 3.13.0
     */
    public FailureHandlingKind getFailureHandling() {
        return FailureHandlingKind.get(jsonData.optString("failureHandling", null));
    }

    public WorkspaceEditClientCapabilities setFailureHandling(FailureHandlingKind failureHandling) {
        jsonData.putOpt("failureHandling", failureHandling != null ? failureHandling.getStringValue() : null);
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
        WorkspaceEditClientCapabilities other = (WorkspaceEditClientCapabilities) obj;
        if (!Objects.equals(this.getDocumentChanges(), other.getDocumentChanges())) {
            return false;
        }
        if (!Objects.equals(this.getResourceOperations(), other.getResourceOperations())) {
            return false;
        }
        if (this.getFailureHandling() != other.getFailureHandling()) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 2;
        if (this.getDocumentChanges() != null) {
            hash = 17 * hash + Boolean.hashCode(this.getDocumentChanges());
        }
        if (this.getResourceOperations() != null) {
            hash = 17 * hash + Objects.hashCode(this.getResourceOperations());
        }
        if (this.getFailureHandling() != null) {
            hash = 17 * hash + Objects.hashCode(this.getFailureHandling());
        }
        return hash;
    }

    public static WorkspaceEditClientCapabilities create() {
        final JSONObject json = new JSONObject();
        return new WorkspaceEditClientCapabilities(json);
    }
}
