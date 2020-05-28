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
 * Workspace specific client capabilities.
 */
public class WorkspaceClientCapabilities extends JSONBase {

    WorkspaceClientCapabilities(JSONObject jsonData) {
        super(jsonData);
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
    public WorkspaceEditClientCapabilities getWorkspaceEdit() {
        return jsonData.has("workspaceEdit") ? new WorkspaceEditClientCapabilities(jsonData.optJSONObject("workspaceEdit")) : null;
    }

    public WorkspaceClientCapabilities setWorkspaceEdit(WorkspaceEditClientCapabilities workspaceEdit) {
        jsonData.putOpt("workspaceEdit", workspaceEdit != null ? workspaceEdit.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `workspace/didChangeConfiguration` notification.
     */
    public DidChangeConfigurationClientCapabilities getDidChangeConfiguration() {
        return jsonData.has("didChangeConfiguration") ? new DidChangeConfigurationClientCapabilities(jsonData.optJSONObject("didChangeConfiguration")) : null;
    }

    public WorkspaceClientCapabilities setDidChangeConfiguration(DidChangeConfigurationClientCapabilities didChangeConfiguration) {
        jsonData.putOpt("didChangeConfiguration", didChangeConfiguration != null ? didChangeConfiguration.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `workspace/didChangeWatchedFiles` notification.
     */
    public DidChangeWatchedFilesClientCapabilities getDidChangeWatchedFiles() {
        return jsonData.has("didChangeWatchedFiles") ? new DidChangeWatchedFilesClientCapabilities(jsonData.optJSONObject("didChangeWatchedFiles")) : null;
    }

    public WorkspaceClientCapabilities setDidChangeWatchedFiles(DidChangeWatchedFilesClientCapabilities didChangeWatchedFiles) {
        jsonData.putOpt("didChangeWatchedFiles", didChangeWatchedFiles != null ? didChangeWatchedFiles.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `workspace/symbol` request.
     */
    public WorkspaceSymbolClientCapabilities getSymbol() {
        return jsonData.has("symbol") ? new WorkspaceSymbolClientCapabilities(jsonData.optJSONObject("symbol")) : null;
    }

    public WorkspaceClientCapabilities setSymbol(WorkspaceSymbolClientCapabilities symbol) {
        jsonData.putOpt("symbol", symbol != null ? symbol.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `workspace/executeCommand` request.
     */
    public ExecuteCommandClientCapabilities getExecuteCommand() {
        return jsonData.has("executeCommand") ? new ExecuteCommandClientCapabilities(jsonData.optJSONObject("executeCommand")) : null;
    }

    public WorkspaceClientCapabilities setExecuteCommand(ExecuteCommandClientCapabilities executeCommand) {
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
            hash = 71 * hash + Boolean.hashCode(this.getApplyEdit());
        }
        if (this.getWorkspaceEdit() != null) {
            hash = 71 * hash + Objects.hashCode(this.getWorkspaceEdit());
        }
        if (this.getDidChangeConfiguration() != null) {
            hash = 71 * hash + Objects.hashCode(this.getDidChangeConfiguration());
        }
        if (this.getDidChangeWatchedFiles() != null) {
            hash = 71 * hash + Objects.hashCode(this.getDidChangeWatchedFiles());
        }
        if (this.getSymbol() != null) {
            hash = 71 * hash + Objects.hashCode(this.getSymbol());
        }
        if (this.getExecuteCommand() != null) {
            hash = 71 * hash + Objects.hashCode(this.getExecuteCommand());
        }
        if (this.getWorkspaceFolders() != null) {
            hash = 71 * hash + Boolean.hashCode(this.getWorkspaceFolders());
        }
        if (this.getConfiguration() != null) {
            hash = 71 * hash + Boolean.hashCode(this.getConfiguration());
        }
        return hash;
    }

    public static WorkspaceClientCapabilities create() {
        final JSONObject json = new JSONObject();
        return new WorkspaceClientCapabilities(json);
    }
}
