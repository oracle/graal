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
 * Defines the capabilities provided by a language server.
 */
public class ServerCapabilities {

    final JSONObject jsonData;

    ServerCapabilities(JSONObject jsonData) {
        this.jsonData = jsonData;
    }

    /**
     * Defines how text documents are synced. Is either a detailed structure defining each
     * notification or for backwards compatibility the TextDocumentSyncKind number.
     */
    public Object getTextDocumentSync() {
        Object obj = jsonData.opt("textDocumentSync");
        if (obj instanceof JSONObject) {
            return new TextDocumentSyncOptions((JSONObject) obj);
        }
        TextDocumentSyncKind kind = obj instanceof Integer ? TextDocumentSyncKind.get((Integer) obj) : null;
        return kind != null ? kind : obj;
    }

    public ServerCapabilities setTextDocumentSync(Object textDocumentSync) {
        if (textDocumentSync instanceof TextDocumentSyncOptions) {
            jsonData.put("textDocumentSync", ((TextDocumentSyncOptions) textDocumentSync).jsonData);
        } else if (textDocumentSync instanceof TextDocumentSyncKind) {
            jsonData.put("textDocumentSync", ((TextDocumentSyncKind) textDocumentSync).getIntValue());
        } else {
            jsonData.putOpt("textDocumentSync", textDocumentSync);
        }
        return this;
    }

    /**
     * The server provides hover support.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getHoverProvider() {
        return jsonData.has("hoverProvider") ? jsonData.getBoolean("hoverProvider") : null;
    }

    public ServerCapabilities setHoverProvider(Boolean hoverProvider) {
        jsonData.putOpt("hoverProvider", hoverProvider);
        return this;
    }

    /**
     * The server provides completion support.
     */
    public CompletionOptions getCompletionProvider() {
        return jsonData.has("completionProvider") ? new CompletionOptions(jsonData.optJSONObject("completionProvider")) : null;
    }

    public ServerCapabilities setCompletionProvider(CompletionOptions completionProvider) {
        jsonData.putOpt("completionProvider", completionProvider != null ? completionProvider.jsonData : null);
        return this;
    }

    /**
     * The server provides signature help support.
     */
    public SignatureHelpOptions getSignatureHelpProvider() {
        return jsonData.has("signatureHelpProvider") ? new SignatureHelpOptions(jsonData.optJSONObject("signatureHelpProvider")) : null;
    }

    public ServerCapabilities setSignatureHelpProvider(SignatureHelpOptions signatureHelpProvider) {
        jsonData.putOpt("signatureHelpProvider", signatureHelpProvider != null ? signatureHelpProvider.jsonData : null);
        return this;
    }

    /**
     * The server provides goto definition support.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getDefinitionProvider() {
        return jsonData.has("definitionProvider") ? jsonData.getBoolean("definitionProvider") : null;
    }

    public ServerCapabilities setDefinitionProvider(Boolean definitionProvider) {
        jsonData.putOpt("definitionProvider", definitionProvider);
        return this;
    }

    /**
     * The server provides Goto Type Definition support.
     */
    public Object getTypeDefinitionProvider() {
        return jsonData.opt("typeDefinitionProvider");
    }

    public ServerCapabilities setTypeDefinitionProvider(Object typeDefinitionProvider) {
        jsonData.putOpt("typeDefinitionProvider", typeDefinitionProvider);
        return this;
    }

    /**
     * The server provides Goto Implementation support.
     */
    public Object getImplementationProvider() {
        return jsonData.opt("implementationProvider");
    }

    public ServerCapabilities setImplementationProvider(Object implementationProvider) {
        jsonData.putOpt("implementationProvider", implementationProvider);
        return this;
    }

    /**
     * The server provides find references support.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getReferencesProvider() {
        return jsonData.has("referencesProvider") ? jsonData.getBoolean("referencesProvider") : null;
    }

    public ServerCapabilities setReferencesProvider(Boolean referencesProvider) {
        jsonData.putOpt("referencesProvider", referencesProvider);
        return this;
    }

    /**
     * The server provides document highlight support.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getDocumentHighlightProvider() {
        return jsonData.has("documentHighlightProvider") ? jsonData.getBoolean("documentHighlightProvider") : null;
    }

    public ServerCapabilities setDocumentHighlightProvider(Boolean documentHighlightProvider) {
        jsonData.putOpt("documentHighlightProvider", documentHighlightProvider);
        return this;
    }

    /**
     * The server provides document symbol support.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getDocumentSymbolProvider() {
        return jsonData.has("documentSymbolProvider") ? jsonData.getBoolean("documentSymbolProvider") : null;
    }

    public ServerCapabilities setDocumentSymbolProvider(Boolean documentSymbolProvider) {
        jsonData.putOpt("documentSymbolProvider", documentSymbolProvider);
        return this;
    }

    /**
     * The server provides workspace symbol support.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getWorkspaceSymbolProvider() {
        return jsonData.has("workspaceSymbolProvider") ? jsonData.getBoolean("workspaceSymbolProvider") : null;
    }

    public ServerCapabilities setWorkspaceSymbolProvider(Boolean workspaceSymbolProvider) {
        jsonData.putOpt("workspaceSymbolProvider", workspaceSymbolProvider);
        return this;
    }

    /**
     * The server provides code actions. CodeActionOptions may only be specified if the client
     * states that it supports `codeActionLiteralSupport` in its initial `initialize` request.
     */
    public Object getCodeActionProvider() {
        Object obj = jsonData.opt("codeActionProvider");
        if (obj instanceof JSONObject) {
            return new CodeActionOptions((JSONObject) obj);
        }
        return obj;
    }

    public ServerCapabilities setCodeActionProvider(Object codeActionProvider) {
        if (codeActionProvider instanceof CodeActionOptions) {
            jsonData.put("codeActionProvider", ((CodeActionOptions) codeActionProvider).jsonData);
        } else {
            jsonData.putOpt("codeActionProvider", codeActionProvider);
        }
        return this;
    }

    /**
     * The server provides code lens.
     */
    public CodeLensOptions getCodeLensProvider() {
        return jsonData.has("codeLensProvider") ? new CodeLensOptions(jsonData.optJSONObject("codeLensProvider")) : null;
    }

    public ServerCapabilities setCodeLensProvider(CodeLensOptions codeLensProvider) {
        jsonData.putOpt("codeLensProvider", codeLensProvider != null ? codeLensProvider.jsonData : null);
        return this;
    }

    /**
     * The server provides document formatting.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getDocumentFormattingProvider() {
        return jsonData.has("documentFormattingProvider") ? jsonData.getBoolean("documentFormattingProvider") : null;
    }

    public ServerCapabilities setDocumentFormattingProvider(Boolean documentFormattingProvider) {
        jsonData.putOpt("documentFormattingProvider", documentFormattingProvider);
        return this;
    }

    /**
     * The server provides document range formatting.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getDocumentRangeFormattingProvider() {
        return jsonData.has("documentRangeFormattingProvider") ? jsonData.getBoolean("documentRangeFormattingProvider") : null;
    }

    public ServerCapabilities setDocumentRangeFormattingProvider(Boolean documentRangeFormattingProvider) {
        jsonData.putOpt("documentRangeFormattingProvider", documentRangeFormattingProvider);
        return this;
    }

    /**
     * The server provides document formatting on typing.
     */
    public DocumentOnTypeFormattingProviderCapabilities getDocumentOnTypeFormattingProvider() {
        return jsonData.has("documentOnTypeFormattingProvider") ? new DocumentOnTypeFormattingProviderCapabilities(jsonData.optJSONObject("documentOnTypeFormattingProvider")) : null;
    }

    public ServerCapabilities setDocumentOnTypeFormattingProvider(DocumentOnTypeFormattingProviderCapabilities documentOnTypeFormattingProvider) {
        jsonData.putOpt("documentOnTypeFormattingProvider", documentOnTypeFormattingProvider != null ? documentOnTypeFormattingProvider.jsonData : null);
        return this;
    }

    /**
     * The server provides rename support. RenameOptions may only be specified if the client states
     * that it supports `prepareSupport` in its initial `initialize` request.
     */
    public Object getRenameProvider() {
        Object obj = jsonData.opt("renameProvider");
        if (obj instanceof JSONObject) {
            return new RenameOptions((JSONObject) obj);
        }
        return obj;
    }

    public ServerCapabilities setRenameProvider(Object renameProvider) {
        if (renameProvider instanceof RenameOptions) {
            jsonData.put("renameProvider", ((RenameOptions) renameProvider).jsonData);
        } else {
            jsonData.putOpt("renameProvider", renameProvider);
        }
        return this;
    }

    /**
     * The server provides document link support.
     */
    public DocumentLinkOptions getDocumentLinkProvider() {
        return jsonData.has("documentLinkProvider") ? new DocumentLinkOptions(jsonData.optJSONObject("documentLinkProvider")) : null;
    }

    public ServerCapabilities setDocumentLinkProvider(DocumentLinkOptions documentLinkProvider) {
        jsonData.putOpt("documentLinkProvider", documentLinkProvider != null ? documentLinkProvider.jsonData : null);
        return this;
    }

    /**
     * The server provides color provider support.
     */
    public Object getColorProvider() {
        Object obj = jsonData.opt("colorProvider");
        if (obj instanceof JSONObject) {
            return new ColorProviderOptions((JSONObject) obj);
        }
        return obj;
    }

    public ServerCapabilities setColorProvider(Object colorProvider) {
        if (colorProvider instanceof ColorProviderOptions) {
            jsonData.put("colorProvider", ((ColorProviderOptions) colorProvider).jsonData);
        } else {
            jsonData.putOpt("colorProvider", colorProvider);
        }
        return this;
    }

    /**
     * The server provides folding provider support.
     */
    public Object getFoldingRangeProvider() {
        Object obj = jsonData.opt("foldingRangeProvider");
        if (obj instanceof JSONObject) {
            return new FoldingRangeProviderOptions((JSONObject) obj);
        }
        return obj;
    }

    public ServerCapabilities setFoldingRangeProvider(Object foldingRangeProvider) {
        if (foldingRangeProvider instanceof FoldingRangeProviderOptions) {
            jsonData.put("foldingRangeProvider", ((FoldingRangeProviderOptions) foldingRangeProvider).jsonData);
        } else {
            jsonData.putOpt("foldingRangeProvider", foldingRangeProvider);
        }
        return this;
    }

    /**
     * The server provides Goto Type Definition support.
     */
    public Object getDeclarationProvider() {
        return jsonData.opt("declarationProvider");
    }

    public ServerCapabilities setDeclarationProvider(Object declarationProvider) {
        jsonData.putOpt("declarationProvider", declarationProvider);
        return this;
    }

    /**
     * The server provides execute command support.
     */
    public ExecuteCommandOptions getExecuteCommandProvider() {
        return jsonData.has("executeCommandProvider") ? new ExecuteCommandOptions(jsonData.optJSONObject("executeCommandProvider")) : null;
    }

    public ServerCapabilities setExecuteCommandProvider(ExecuteCommandOptions executeCommandProvider) {
        jsonData.putOpt("executeCommandProvider", executeCommandProvider != null ? executeCommandProvider.jsonData : null);
        return this;
    }

    /**
     * The workspace server capabilities.
     */
    public WorkspaceCapabilities getWorkspace() {
        return jsonData.has("workspace") ? new WorkspaceCapabilities(jsonData.optJSONObject("workspace")) : null;
    }

    public ServerCapabilities setWorkspace(WorkspaceCapabilities workspace) {
        jsonData.putOpt("workspace", workspace != null ? workspace.jsonData : null);
        return this;
    }

    /**
     * Experimental server capabilities.
     */
    public Object getExperimental() {
        return jsonData.opt("experimental");
    }

    public ServerCapabilities setExperimental(Object experimental) {
        jsonData.putOpt("experimental", experimental);
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
        ServerCapabilities other = (ServerCapabilities) obj;
        if (!Objects.equals(this.getTextDocumentSync(), other.getTextDocumentSync())) {
            return false;
        }
        if (!Objects.equals(this.getHoverProvider(), other.getHoverProvider())) {
            return false;
        }
        if (!Objects.equals(this.getCompletionProvider(), other.getCompletionProvider())) {
            return false;
        }
        if (!Objects.equals(this.getSignatureHelpProvider(), other.getSignatureHelpProvider())) {
            return false;
        }
        if (!Objects.equals(this.getDefinitionProvider(), other.getDefinitionProvider())) {
            return false;
        }
        if (!Objects.equals(this.getTypeDefinitionProvider(), other.getTypeDefinitionProvider())) {
            return false;
        }
        if (!Objects.equals(this.getImplementationProvider(), other.getImplementationProvider())) {
            return false;
        }
        if (!Objects.equals(this.getReferencesProvider(), other.getReferencesProvider())) {
            return false;
        }
        if (!Objects.equals(this.getDocumentHighlightProvider(), other.getDocumentHighlightProvider())) {
            return false;
        }
        if (!Objects.equals(this.getDocumentSymbolProvider(), other.getDocumentSymbolProvider())) {
            return false;
        }
        if (!Objects.equals(this.getWorkspaceSymbolProvider(), other.getWorkspaceSymbolProvider())) {
            return false;
        }
        if (!Objects.equals(this.getCodeActionProvider(), other.getCodeActionProvider())) {
            return false;
        }
        if (!Objects.equals(this.getCodeLensProvider(), other.getCodeLensProvider())) {
            return false;
        }
        if (!Objects.equals(this.getDocumentFormattingProvider(), other.getDocumentFormattingProvider())) {
            return false;
        }
        if (!Objects.equals(this.getDocumentRangeFormattingProvider(), other.getDocumentRangeFormattingProvider())) {
            return false;
        }
        if (!Objects.equals(this.getDocumentOnTypeFormattingProvider(), other.getDocumentOnTypeFormattingProvider())) {
            return false;
        }
        if (!Objects.equals(this.getRenameProvider(), other.getRenameProvider())) {
            return false;
        }
        if (!Objects.equals(this.getDocumentLinkProvider(), other.getDocumentLinkProvider())) {
            return false;
        }
        if (!Objects.equals(this.getColorProvider(), other.getColorProvider())) {
            return false;
        }
        if (!Objects.equals(this.getFoldingRangeProvider(), other.getFoldingRangeProvider())) {
            return false;
        }
        if (!Objects.equals(this.getDeclarationProvider(), other.getDeclarationProvider())) {
            return false;
        }
        if (!Objects.equals(this.getExecuteCommandProvider(), other.getExecuteCommandProvider())) {
            return false;
        }
        if (!Objects.equals(this.getWorkspace(), other.getWorkspace())) {
            return false;
        }
        if (!Objects.equals(this.getExperimental(), other.getExperimental())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getTextDocumentSync() != null) {
            hash = 83 * hash + Objects.hashCode(this.getTextDocumentSync());
        }
        if (this.getHoverProvider() != null) {
            hash = 83 * hash + Boolean.hashCode(this.getHoverProvider());
        }
        if (this.getCompletionProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getCompletionProvider());
        }
        if (this.getSignatureHelpProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getSignatureHelpProvider());
        }
        if (this.getDefinitionProvider() != null) {
            hash = 83 * hash + Boolean.hashCode(this.getDefinitionProvider());
        }
        if (this.getTypeDefinitionProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getTypeDefinitionProvider());
        }
        if (this.getImplementationProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getImplementationProvider());
        }
        if (this.getReferencesProvider() != null) {
            hash = 83 * hash + Boolean.hashCode(this.getReferencesProvider());
        }
        if (this.getDocumentHighlightProvider() != null) {
            hash = 83 * hash + Boolean.hashCode(this.getDocumentHighlightProvider());
        }
        if (this.getDocumentSymbolProvider() != null) {
            hash = 83 * hash + Boolean.hashCode(this.getDocumentSymbolProvider());
        }
        if (this.getWorkspaceSymbolProvider() != null) {
            hash = 83 * hash + Boolean.hashCode(this.getWorkspaceSymbolProvider());
        }
        if (this.getCodeActionProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getCodeActionProvider());
        }
        if (this.getCodeLensProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getCodeLensProvider());
        }
        if (this.getDocumentFormattingProvider() != null) {
            hash = 83 * hash + Boolean.hashCode(this.getDocumentFormattingProvider());
        }
        if (this.getDocumentRangeFormattingProvider() != null) {
            hash = 83 * hash + Boolean.hashCode(this.getDocumentRangeFormattingProvider());
        }
        if (this.getDocumentOnTypeFormattingProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getDocumentOnTypeFormattingProvider());
        }
        if (this.getRenameProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getRenameProvider());
        }
        if (this.getDocumentLinkProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getDocumentLinkProvider());
        }
        if (this.getColorProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getColorProvider());
        }
        if (this.getFoldingRangeProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getFoldingRangeProvider());
        }
        if (this.getDeclarationProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getDeclarationProvider());
        }
        if (this.getExecuteCommandProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getExecuteCommandProvider());
        }
        if (this.getWorkspace() != null) {
            hash = 83 * hash + Objects.hashCode(this.getWorkspace());
        }
        if (this.getExperimental() != null) {
            hash = 83 * hash + Objects.hashCode(this.getExperimental());
        }
        return hash;
    }

    public static ServerCapabilities create() {
        final JSONObject json = new JSONObject();
        return new ServerCapabilities(json);
    }

    public static class DocumentOnTypeFormattingProviderCapabilities {

        final JSONObject jsonData;

        DocumentOnTypeFormattingProviderCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * A character on which formatting should be triggered, like `}`.
         */
        public String getFirstTriggerCharacter() {
            return jsonData.getString("firstTriggerCharacter");
        }

        public DocumentOnTypeFormattingProviderCapabilities setFirstTriggerCharacter(String firstTriggerCharacter) {
            jsonData.put("firstTriggerCharacter", firstTriggerCharacter);
            return this;
        }

        /**
         * More trigger characters.
         */
        public List<String> getMoreTriggerCharacter() {
            final JSONArray json = jsonData.optJSONArray("moreTriggerCharacter");
            if (json == null) {
                return null;
            }
            final List<String> list = new ArrayList<>(json.length());
            for (int i = 0; i < json.length(); i++) {
                list.add(json.getString(i));
            }
            return Collections.unmodifiableList(list);
        }

        public DocumentOnTypeFormattingProviderCapabilities setMoreTriggerCharacter(List<String> moreTriggerCharacter) {
            if (moreTriggerCharacter != null) {
                final JSONArray json = new JSONArray();
                for (String string : moreTriggerCharacter) {
                    json.put(string);
                }
                jsonData.put("moreTriggerCharacter", json);
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
            DocumentOnTypeFormattingProviderCapabilities other = (DocumentOnTypeFormattingProviderCapabilities) obj;
            if (!Objects.equals(this.getFirstTriggerCharacter(), other.getFirstTriggerCharacter())) {
                return false;
            }
            if (!Objects.equals(this.getMoreTriggerCharacter(), other.getMoreTriggerCharacter())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 89 * hash + Objects.hashCode(this.getFirstTriggerCharacter());
            if (this.getMoreTriggerCharacter() != null) {
                hash = 89 * hash + Objects.hashCode(this.getMoreTriggerCharacter());
            }
            return hash;
        }
    }

    public static class WorkspaceCapabilities {

        final JSONObject jsonData;

        WorkspaceCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        public WorkspaceFoldersCapabilities getWorkspaceFolders() {
            return jsonData.has("workspaceFolders") ? new WorkspaceFoldersCapabilities(jsonData.optJSONObject("workspaceFolders")) : null;
        }

        public WorkspaceCapabilities setWorkspaceFolders(WorkspaceFoldersCapabilities workspaceFolders) {
            jsonData.putOpt("workspaceFolders", workspaceFolders != null ? workspaceFolders.jsonData : null);
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
            WorkspaceCapabilities other = (WorkspaceCapabilities) obj;
            if (!Objects.equals(this.getWorkspaceFolders(), other.getWorkspaceFolders())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            if (this.getWorkspaceFolders() != null) {
                hash = 41 * hash + Objects.hashCode(this.getWorkspaceFolders());
            }
            return hash;
        }

        public static class WorkspaceFoldersCapabilities {

            final JSONObject jsonData;

            WorkspaceFoldersCapabilities(JSONObject jsonData) {
                this.jsonData = jsonData;
            }

            /**
             * The Server has support for workspace folders.
             */
            @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
            public Boolean getSupported() {
                return jsonData.has("supported") ? jsonData.getBoolean("supported") : null;
            }

            public WorkspaceFoldersCapabilities setSupported(Boolean supported) {
                jsonData.putOpt("supported", supported);
                return this;
            }

            /**
             * Whether the server wants to receive workspace folder change notifications.
             *
             * If a strings is provided the string is treated as a ID under which the notification
             * is registed on the client side. The ID can be used to unregister for these events
             * using the `client/unregisterCapability` request.
             */
            public Object getChangeNotifications() {
                return jsonData.opt("changeNotifications");
            }

            public WorkspaceFoldersCapabilities setChangeNotifications(Object changeNotifications) {
                jsonData.putOpt("changeNotifications", changeNotifications);
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
                WorkspaceFoldersCapabilities other = (WorkspaceFoldersCapabilities) obj;
                if (!Objects.equals(this.getSupported(), other.getSupported())) {
                    return false;
                }
                if (!Objects.equals(this.getChangeNotifications(), other.getChangeNotifications())) {
                    return false;
                }
                return true;
            }

            @Override
            public int hashCode() {
                int hash = 5;
                if (this.getSupported() != null) {
                    hash = 71 * hash + Boolean.hashCode(this.getSupported());
                }
                if (this.getChangeNotifications() != null) {
                    hash = 71 * hash + Objects.hashCode(this.getChangeNotifications());
                }
                return hash;
            }
        }
    }
}
