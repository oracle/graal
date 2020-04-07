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

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Workspace specific client capabilities.
 */
public class WorkspaceClientCapabilities {

    final JSONObject jsonData;

    WorkspaceClientCapabilities(JSONObject jsonData) {
        this.jsonData = jsonData;
    }

    /**
     * The client supports applying batch edits to the workspace by supporting the request
     * 'workspace/applyEdit'.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getApplyEdit() {
        return jsonData.has("applyEdit") ? jsonData.getBoolean("applyEdit") : null;
    }

    public WorkspaceClientCapabilities setApplyEdit(Boolean applyEdit) {
        jsonData.putOpt("applyEdit", applyEdit);
        return this;
    }

    /**
     * Capabilities specific to `WorkspaceEdit`s.
     */
    public WorkspaceEditCapabilities getWorkspaceEdit() {
        return jsonData.has("workspaceEdit") ? new WorkspaceEditCapabilities(jsonData.optJSONObject("workspaceEdit")) : null;
    }

    public WorkspaceClientCapabilities setWorkspaceEdit(WorkspaceEditCapabilities workspaceEdit) {
        jsonData.putOpt("workspaceEdit", workspaceEdit != null ? workspaceEdit.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `workspace/didChangeConfiguration` notification.
     */
    public DidChangeConfigurationCapabilities getDidChangeConfiguration() {
        return jsonData.has("didChangeConfiguration") ? new DidChangeConfigurationCapabilities(jsonData.optJSONObject("didChangeConfiguration")) : null;
    }

    public WorkspaceClientCapabilities setDidChangeConfiguration(DidChangeConfigurationCapabilities didChangeConfiguration) {
        jsonData.putOpt("didChangeConfiguration", didChangeConfiguration != null ? didChangeConfiguration.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `workspace/didChangeWatchedFiles` notification.
     */
    public DidChangeWatchedFilesCapabilities getDidChangeWatchedFiles() {
        return jsonData.has("didChangeWatchedFiles") ? new DidChangeWatchedFilesCapabilities(jsonData.optJSONObject("didChangeWatchedFiles")) : null;
    }

    public WorkspaceClientCapabilities setDidChangeWatchedFiles(DidChangeWatchedFilesCapabilities didChangeWatchedFiles) {
        jsonData.putOpt("didChangeWatchedFiles", didChangeWatchedFiles != null ? didChangeWatchedFiles.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `workspace/symbol` request.
     */
    public SymbolCapabilities getSymbol() {
        return jsonData.has("symbol") ? new SymbolCapabilities(jsonData.optJSONObject("symbol")) : null;
    }

    public WorkspaceClientCapabilities setSymbol(SymbolCapabilities symbol) {
        jsonData.putOpt("symbol", symbol != null ? symbol.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `workspace/executeCommand` request.
     */
    public ExecuteCommandCapabilities getExecuteCommand() {
        return jsonData.has("executeCommand") ? new ExecuteCommandCapabilities(jsonData.optJSONObject("executeCommand")) : null;
    }

    public WorkspaceClientCapabilities setExecuteCommand(ExecuteCommandCapabilities executeCommand) {
        jsonData.putOpt("executeCommand", executeCommand != null ? executeCommand.jsonData : null);
        return this;
    }

    /**
     * The client has support for workspace folders.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getWorkspaceFolders() {
        return jsonData.has("workspaceFolders") ? jsonData.getBoolean("workspaceFolders") : null;
    }

    public WorkspaceClientCapabilities setWorkspaceFolders(Boolean workspaceFolders) {
        jsonData.putOpt("workspaceFolders", workspaceFolders);
        return this;
    }

    /**
     * The client supports `workspace/configuration` requests.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getConfiguration() {
        return jsonData.has("configuration") ? jsonData.getBoolean("configuration") : null;
    }

    public WorkspaceClientCapabilities setConfiguration(Boolean configuration) {
        jsonData.putOpt("configuration", configuration);
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
        WorkspaceClientCapabilities other = (WorkspaceClientCapabilities) obj;
        if (!Objects.equals(this.getApplyEdit(), other.getApplyEdit())) {
            return false;
        }
        if (!Objects.equals(this.getWorkspaceEdit(), other.getWorkspaceEdit())) {
            return false;
        }
        if (!Objects.equals(this.getDidChangeConfiguration(), other.getDidChangeConfiguration())) {
            return false;
        }
        if (!Objects.equals(this.getDidChangeWatchedFiles(), other.getDidChangeWatchedFiles())) {
            return false;
        }
        if (!Objects.equals(this.getSymbol(), other.getSymbol())) {
            return false;
        }
        if (!Objects.equals(this.getExecuteCommand(), other.getExecuteCommand())) {
            return false;
        }
        if (!Objects.equals(this.getWorkspaceFolders(), other.getWorkspaceFolders())) {
            return false;
        }
        if (!Objects.equals(this.getConfiguration(), other.getConfiguration())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getApplyEdit() != null) {
            hash = 41 * hash + Boolean.hashCode(this.getApplyEdit());
        }
        if (this.getWorkspaceEdit() != null) {
            hash = 41 * hash + Objects.hashCode(this.getWorkspaceEdit());
        }
        if (this.getDidChangeConfiguration() != null) {
            hash = 41 * hash + Objects.hashCode(this.getDidChangeConfiguration());
        }
        if (this.getDidChangeWatchedFiles() != null) {
            hash = 41 * hash + Objects.hashCode(this.getDidChangeWatchedFiles());
        }
        if (this.getSymbol() != null) {
            hash = 41 * hash + Objects.hashCode(this.getSymbol());
        }
        if (this.getExecuteCommand() != null) {
            hash = 41 * hash + Objects.hashCode(this.getExecuteCommand());
        }
        if (this.getWorkspaceFolders() != null) {
            hash = 41 * hash + Boolean.hashCode(this.getWorkspaceFolders());
        }
        if (this.getConfiguration() != null) {
            hash = 41 * hash + Boolean.hashCode(this.getConfiguration());
        }
        return hash;
    }

    public static WorkspaceClientCapabilities create() {
        final JSONObject json = new JSONObject();
        return new WorkspaceClientCapabilities(json);
    }

    public static class WorkspaceEditCapabilities {

        final JSONObject jsonData;

        WorkspaceEditCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * The client supports versioned document changes in `WorkspaceEdit`s.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDocumentChanges() {
            return jsonData.has("documentChanges") ? jsonData.getBoolean("documentChanges") : null;
        }

        public WorkspaceEditCapabilities setDocumentChanges(Boolean documentChanges) {
            jsonData.putOpt("documentChanges", documentChanges);
            return this;
        }

        /**
         * The resource operations the client supports. Clients should at least support 'create',
         * 'rename' and 'delete' files and folders.
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

        public WorkspaceEditCapabilities setResourceOperations(List<ResourceOperationKind> resourceOperations) {
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
         * The failure handling strategy of a client if applying the workspace edit failes.
         */
        public FailureHandlingKind getFailureHandling() {
            return FailureHandlingKind.get(jsonData.optString("failureHandling", null));
        }

        public WorkspaceEditCapabilities setFailureHandling(FailureHandlingKind failureHandling) {
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
            WorkspaceEditCapabilities other = (WorkspaceEditCapabilities) obj;
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
            int hash = 7;
            if (this.getDocumentChanges() != null) {
                hash = 97 * hash + Boolean.hashCode(this.getDocumentChanges());
            }
            if (this.getResourceOperations() != null) {
                hash = 97 * hash + Objects.hashCode(this.getResourceOperations());
            }
            if (this.getFailureHandling() != null) {
                hash = 97 * hash + Objects.hashCode(this.getFailureHandling());
            }
            return hash;
        }
    }

    public static class DidChangeConfigurationCapabilities {

        final JSONObject jsonData;

        DidChangeConfigurationCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Did change configuration notification supports dynamic registration.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public DidChangeConfigurationCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
            jsonData.putOpt("dynamicRegistration", dynamicRegistration);
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
            DidChangeConfigurationCapabilities other = (DidChangeConfigurationCapabilities) obj;
            if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            if (this.getDynamicRegistration() != null) {
                hash = 31 * hash + Boolean.hashCode(this.getDynamicRegistration());
            }
            return hash;
        }
    }

    public static class DidChangeWatchedFilesCapabilities {

        final JSONObject jsonData;

        DidChangeWatchedFilesCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Did change watched files notification supports dynamic registration.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public DidChangeWatchedFilesCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
            jsonData.putOpt("dynamicRegistration", dynamicRegistration);
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
            DidChangeWatchedFilesCapabilities other = (DidChangeWatchedFilesCapabilities) obj;
            if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            if (this.getDynamicRegistration() != null) {
                hash = 23 * hash + Boolean.hashCode(this.getDynamicRegistration());
            }
            return hash;
        }
    }

    public static class SymbolCapabilities {

        final JSONObject jsonData;

        SymbolCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Symbol request supports dynamic registration.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public SymbolCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
            jsonData.putOpt("dynamicRegistration", dynamicRegistration);
            return this;
        }

        /**
         * Specific capabilities for the `SymbolKind` in the `workspace/symbol` request.
         */
        public SymbolKindCapabilities getSymbolKind() {
            return jsonData.has("symbolKind") ? new SymbolKindCapabilities(jsonData.optJSONObject("symbolKind")) : null;
        }

        public SymbolCapabilities setSymbolKind(SymbolKindCapabilities symbolKind) {
            jsonData.putOpt("symbolKind", symbolKind != null ? symbolKind.jsonData : null);
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
            SymbolCapabilities other = (SymbolCapabilities) obj;
            if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
                return false;
            }
            if (!Objects.equals(this.getSymbolKind(), other.getSymbolKind())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            if (this.getDynamicRegistration() != null) {
                hash = 47 * hash + Boolean.hashCode(this.getDynamicRegistration());
            }
            if (this.getSymbolKind() != null) {
                hash = 47 * hash + Objects.hashCode(this.getSymbolKind());
            }
            return hash;
        }

        public static class SymbolKindCapabilities {

            final JSONObject jsonData;

            SymbolKindCapabilities(JSONObject jsonData) {
                this.jsonData = jsonData;
            }

            /**
             * The symbol kind values the client supports. When this property exists the client also
             * guarantees that it will handle values outside its set gracefully and falls back to a
             * default value when unknown.
             *
             * If this property is not present the client only supports the symbol kinds from `File`
             * to `Array` as defined in the initial version of the protocol.
             */
            public List<SymbolKind> getValueSet() {
                final JSONArray json = jsonData.optJSONArray("valueSet");
                if (json == null) {
                    return null;
                }
                final List<SymbolKind> list = new ArrayList<>(json.length());
                for (int i = 0; i < json.length(); i++) {
                    list.add(SymbolKind.get(json.getInt(i)));
                }
                return Collections.unmodifiableList(list);
            }

            public SymbolKindCapabilities setValueSet(List<SymbolKind> valueSet) {
                if (valueSet != null) {
                    final JSONArray json = new JSONArray();
                    for (SymbolKind symbolKind : valueSet) {
                        json.put(symbolKind.getIntValue());
                    }
                    jsonData.put("valueSet", json);
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
                SymbolKindCapabilities other = (SymbolKindCapabilities) obj;
                if (!Objects.equals(this.getValueSet(), other.getValueSet())) {
                    return false;
                }
                return true;
            }

            @Override
            public int hashCode() {
                int hash = 5;
                if (this.getValueSet() != null) {
                    hash = 41 * hash + Objects.hashCode(this.getValueSet());
                }
                return hash;
            }
        }
    }

    public static class ExecuteCommandCapabilities {

        final JSONObject jsonData;

        ExecuteCommandCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Execute command supports dynamic registration.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public ExecuteCommandCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
            jsonData.putOpt("dynamicRegistration", dynamicRegistration);
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
            ExecuteCommandCapabilities other = (ExecuteCommandCapabilities) obj;
            if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            if (this.getDynamicRegistration() != null) {
                hash = 89 * hash + Boolean.hashCode(this.getDynamicRegistration());
            }
            return hash;
        }
    }
}
