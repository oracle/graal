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
 * Defines the capabilities provided by a language server.
 */
public class ServerCapabilities extends JSONBase {

    ServerCapabilities(JSONObject jsonData) {
        super(jsonData);
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
        TextDocumentSyncKind textDocumentSyncKind = obj instanceof Integer ? TextDocumentSyncKind.get((Integer) obj) : null;
        return textDocumentSyncKind != null ? textDocumentSyncKind : obj;
    }

    public ServerCapabilities setTextDocumentSync(Object textDocumentSync) {
        if (textDocumentSync instanceof TextDocumentSyncOptions) {
            jsonData.put("textDocumentSync", ((TextDocumentSyncOptions) textDocumentSync).jsonData);
        } else if (textDocumentSync instanceof TextDocumentSyncKind) {
            jsonData.put("textDocumentSync", ((TextDocumentSyncKind) textDocumentSync).getIntValue());
        } else {
            jsonData.put("textDocumentSync", textDocumentSync);
        }
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
     * The server provides hover support.
     */
    public Object getHoverProvider() {
        Object obj = jsonData.opt("hoverProvider");
        if (obj instanceof JSONObject) {
            return new HoverOptions((JSONObject) obj);
        }
        return obj;
    }

    public ServerCapabilities setHoverProvider(Object hoverProvider) {
        if (hoverProvider instanceof HoverOptions) {
            jsonData.put("hoverProvider", ((HoverOptions) hoverProvider).jsonData);
        } else {
            jsonData.put("hoverProvider", hoverProvider);
        }
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
     * The server provides Goto Declaration support.
     */
    public Object getDeclarationProvider() {
        Object obj = jsonData.opt("declarationProvider");
        if (obj instanceof JSONObject) {
            return ((JSONObject) obj).has("documentSelector") ? new DeclarationRegistrationOptions((JSONObject) obj) : new DeclarationOptions((JSONObject) obj);
        }
        return obj;
    }

    public ServerCapabilities setDeclarationProvider(Object declarationProvider) {
        if (declarationProvider instanceof DeclarationRegistrationOptions) {
            jsonData.put("declarationProvider", ((DeclarationRegistrationOptions) declarationProvider).jsonData);
        } else if (declarationProvider instanceof DeclarationOptions) {
            jsonData.put("declarationProvider", ((DeclarationOptions) declarationProvider).jsonData);
        } else {
            jsonData.putOpt("declarationProvider", declarationProvider);
        }
        return this;
    }

    /**
     * The server provides goto definition support.
     */
    public Object getDefinitionProvider() {
        Object obj = jsonData.opt("definitionProvider");
        if (obj instanceof JSONObject) {
            return new DefinitionOptions((JSONObject) obj);
        }
        return obj;
    }

    public ServerCapabilities setDefinitionProvider(Object definitionProvider) {
        if (definitionProvider instanceof DefinitionOptions) {
            jsonData.put("definitionProvider", ((DefinitionOptions) definitionProvider).jsonData);
        } else {
            jsonData.put("definitionProvider", definitionProvider);
        }
        return this;
    }

    /**
     * The server provides Goto Type Definition support.
     */
    public Object getTypeDefinitionProvider() {
        Object obj = jsonData.opt("typeDefinitionProvider");
        if (obj instanceof JSONObject) {
            return ((JSONObject) obj).has("documentSelector") ? new TypeDefinitionRegistrationOptions((JSONObject) obj) : new TypeDefinitionOptions((JSONObject) obj);
        }
        return obj;
    }

    public ServerCapabilities setTypeDefinitionProvider(Object typeDefinitionProvider) {
        if (typeDefinitionProvider instanceof TypeDefinitionRegistrationOptions) {
            jsonData.put("typeDefinitionProvider", ((TypeDefinitionRegistrationOptions) typeDefinitionProvider).jsonData);
        } else if (typeDefinitionProvider instanceof TypeDefinitionOptions) {
            jsonData.put("typeDefinitionProvider", ((TypeDefinitionOptions) typeDefinitionProvider).jsonData);
        } else {
            jsonData.putOpt("typeDefinitionProvider", typeDefinitionProvider);
        }
        return this;
    }

    /**
     * The server provides Goto Implementation support.
     */
    public Object getImplementationProvider() {
        Object obj = jsonData.opt("implementationProvider");
        if (obj instanceof JSONObject) {
            return ((JSONObject) obj).has("implementationProvider") ? new ImplementationRegistrationOptions((JSONObject) obj) : new ImplementationOptions((JSONObject) obj);
        }
        return obj;
    }

    public ServerCapabilities setImplementationProvider(Object implementationProvider) {
        if (implementationProvider instanceof ImplementationRegistrationOptions) {
            jsonData.put("implementationProvider", ((ImplementationRegistrationOptions) implementationProvider).jsonData);
        } else if (implementationProvider instanceof ImplementationOptions) {
            jsonData.put("implementationProvider", ((ImplementationOptions) implementationProvider).jsonData);
        } else {
            jsonData.putOpt("implementationProvider", implementationProvider);
        }
        return this;
    }

    /**
     * The server provides find references support.
     */
    public Object getReferencesProvider() {
        Object obj = jsonData.opt("referencesProvider");
        if (obj instanceof JSONObject) {
            return new ReferenceOptions((JSONObject) obj);
        }
        return obj;
    }

    public ServerCapabilities setReferencesProvider(Object referencesProvider) {
        if (referencesProvider instanceof ReferenceOptions) {
            jsonData.put("referencesProvider", ((ReferenceOptions) referencesProvider).jsonData);
        } else {
            jsonData.put("referencesProvider", referencesProvider);
        }
        return this;
    }

    /**
     * The server provides document highlight support.
     */
    public Object getDocumentHighlightProvider() {
        Object obj = jsonData.opt("documentHighlightProvider");
        if (obj instanceof JSONObject) {
            return new DocumentHighlightOptions((JSONObject) obj);
        }
        return obj;
    }

    public ServerCapabilities setDocumentHighlightProvider(Object documentHighlightProvider) {
        if (documentHighlightProvider instanceof DocumentHighlightOptions) {
            jsonData.put("documentHighlightProvider", ((DocumentHighlightOptions) documentHighlightProvider).jsonData);
        } else {
            jsonData.put("documentHighlightProvider", documentHighlightProvider);
        }
        return this;
    }

    /**
     * The server provides document symbol support.
     */
    public Object getDocumentSymbolProvider() {
        Object obj = jsonData.opt("documentSymbolProvider");
        if (obj instanceof JSONObject) {
            return new DocumentSymbolOptions((JSONObject) obj);
        }
        return obj;
    }

    public ServerCapabilities setDocumentSymbolProvider(Object documentSymbolProvider) {
        if (documentSymbolProvider instanceof DocumentSymbolOptions) {
            jsonData.put("documentSymbolProvider", ((DocumentSymbolOptions) documentSymbolProvider).jsonData);
        } else {
            jsonData.put("documentSymbolProvider", documentSymbolProvider);
        }
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
            jsonData.put("codeActionProvider", codeActionProvider);
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
            return ((JSONObject) obj).has("documentSelector") ? new DocumentColorRegistrationOptions((JSONObject) obj) : new DocumentColorOptions((JSONObject) obj);
        }
        return obj;
    }

    public ServerCapabilities setColorProvider(Object colorProvider) {
        if (colorProvider instanceof DocumentColorRegistrationOptions) {
            jsonData.put("colorProvider", ((DocumentColorRegistrationOptions) colorProvider).jsonData);
        } else if (colorProvider instanceof DocumentColorOptions) {
            jsonData.put("colorProvider", ((DocumentColorOptions) colorProvider).jsonData);
        } else {
            jsonData.putOpt("colorProvider", colorProvider);
        }
        return this;
    }

    /**
     * The server provides workspace symbol support.
     */
    public Object getWorkspaceSymbolProvider() {
        Object obj = jsonData.opt("workspaceSymbolProvider");
        if (obj instanceof JSONObject) {
            return new WorkspaceSymbolOptions((JSONObject) obj);
        }
        return obj;
    }

    public ServerCapabilities setWorkspaceSymbolProvider(Object workspaceSymbolProvider) {
        if (workspaceSymbolProvider instanceof WorkspaceSymbolOptions) {
            jsonData.put("workspaceSymbolProvider", ((WorkspaceSymbolOptions) workspaceSymbolProvider).jsonData);
        } else {
            jsonData.put("workspaceSymbolProvider", workspaceSymbolProvider);
        }
        return this;
    }

    /**
     * The server provides document formatting.
     */
    public Object getDocumentFormattingProvider() {
        Object obj = jsonData.opt("documentFormattingProvider");
        if (obj instanceof JSONObject) {
            return new DocumentFormattingOptions((JSONObject) obj);
        }
        return obj;
    }

    public ServerCapabilities setDocumentFormattingProvider(Object documentFormattingProvider) {
        if (documentFormattingProvider instanceof DocumentFormattingOptions) {
            jsonData.put("documentFormattingProvider", ((DocumentFormattingOptions) documentFormattingProvider).jsonData);
        } else {
            jsonData.put("documentFormattingProvider", documentFormattingProvider);
        }
        return this;
    }

    /**
     * The server provides document range formatting.
     */
    public Object getDocumentRangeFormattingProvider() {
        Object obj = jsonData.opt("documentRangeFormattingProvider");
        if (obj instanceof JSONObject) {
            return new DocumentRangeFormattingOptions((JSONObject) obj);
        }
        return obj;
    }

    public ServerCapabilities setDocumentRangeFormattingProvider(Object documentRangeFormattingProvider) {
        if (documentRangeFormattingProvider instanceof DocumentRangeFormattingOptions) {
            jsonData.put("documentRangeFormattingProvider", ((DocumentRangeFormattingOptions) documentRangeFormattingProvider).jsonData);
        } else {
            jsonData.put("documentRangeFormattingProvider", documentRangeFormattingProvider);
        }
        return this;
    }

    /**
     * The server provides document formatting on typing.
     */
    public DocumentOnTypeFormattingOptions getDocumentOnTypeFormattingProvider() {
        return jsonData.has("documentOnTypeFormattingProvider") ? new DocumentOnTypeFormattingOptions(jsonData.optJSONObject("documentOnTypeFormattingProvider")) : null;
    }

    public ServerCapabilities setDocumentOnTypeFormattingProvider(DocumentOnTypeFormattingOptions documentOnTypeFormattingProvider) {
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
            jsonData.put("renameProvider", renameProvider);
        }
        return this;
    }

    /**
     * The server provides folding provider support.
     */
    public Object getFoldingRangeProvider() {
        Object obj = jsonData.opt("foldingRangeProvider");
        if (obj instanceof JSONObject) {
            return ((JSONObject) obj).has("documentSelector") ? new FoldingRangeRegistrationOptions((JSONObject) obj) : new FoldingRangeOptions((JSONObject) obj);
        }
        return obj;
    }

    public ServerCapabilities setFoldingRangeProvider(Object foldingRangeProvider) {
        if (foldingRangeProvider instanceof FoldingRangeRegistrationOptions) {
            jsonData.put("foldingRangeProvider", ((FoldingRangeRegistrationOptions) foldingRangeProvider).jsonData);
        } else if (foldingRangeProvider instanceof FoldingRangeOptions) {
            jsonData.put("foldingRangeProvider", ((FoldingRangeOptions) foldingRangeProvider).jsonData);
        } else {
            jsonData.putOpt("foldingRangeProvider", foldingRangeProvider);
        }
        return this;
    }

    /**
     * The server provides selection range support.
     */
    public Object getSelectionRangeProvider() {
        Object obj = jsonData.opt("selectionRangeProvider");
        if (obj instanceof JSONObject) {
            return ((JSONObject) obj).has("documentSelector") ? new SelectionRangeRegistrationOptions((JSONObject) obj) : new SelectionRangeOptions((JSONObject) obj);
        }
        return obj;
    }

    public ServerCapabilities setSelectionRangeProvider(Object selectionRangeProvider) {
        if (selectionRangeProvider instanceof SelectionRangeRegistrationOptions) {
            jsonData.put("selectionRangeProvider", ((SelectionRangeRegistrationOptions) selectionRangeProvider).jsonData);
        } else if (selectionRangeProvider instanceof SelectionRangeOptions) {
            jsonData.put("selectionRangeProvider", ((SelectionRangeOptions) selectionRangeProvider).jsonData);
        } else {
            jsonData.putOpt("selectionRangeProvider", selectionRangeProvider);
        }
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
     * Experimental server capabilities.
     */
    public Object getExperimental() {
        return jsonData.opt("experimental");
    }

    public ServerCapabilities setExperimental(Object experimental) {
        jsonData.putOpt("experimental", experimental);
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
        if (!Objects.equals(this.getCompletionProvider(), other.getCompletionProvider())) {
            return false;
        }
        if (!Objects.equals(this.getHoverProvider(), other.getHoverProvider())) {
            return false;
        }
        if (!Objects.equals(this.getSignatureHelpProvider(), other.getSignatureHelpProvider())) {
            return false;
        }
        if (!Objects.equals(this.getDeclarationProvider(), other.getDeclarationProvider())) {
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
        if (!Objects.equals(this.getCodeActionProvider(), other.getCodeActionProvider())) {
            return false;
        }
        if (!Objects.equals(this.getCodeLensProvider(), other.getCodeLensProvider())) {
            return false;
        }
        if (!Objects.equals(this.getDocumentLinkProvider(), other.getDocumentLinkProvider())) {
            return false;
        }
        if (!Objects.equals(this.getColorProvider(), other.getColorProvider())) {
            return false;
        }
        if (!Objects.equals(this.getWorkspaceSymbolProvider(), other.getWorkspaceSymbolProvider())) {
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
        if (!Objects.equals(this.getFoldingRangeProvider(), other.getFoldingRangeProvider())) {
            return false;
        }
        if (!Objects.equals(this.getSelectionRangeProvider(), other.getSelectionRangeProvider())) {
            return false;
        }
        if (!Objects.equals(this.getExecuteCommandProvider(), other.getExecuteCommandProvider())) {
            return false;
        }
        if (!Objects.equals(this.getExperimental(), other.getExperimental())) {
            return false;
        }
        if (!Objects.equals(this.getWorkspace(), other.getWorkspace())) {
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
        if (this.getCompletionProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getCompletionProvider());
        }
        if (this.getHoverProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getHoverProvider());
        }
        if (this.getSignatureHelpProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getSignatureHelpProvider());
        }
        if (this.getDeclarationProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getDeclarationProvider());
        }
        if (this.getDefinitionProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getDefinitionProvider());
        }
        if (this.getTypeDefinitionProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getTypeDefinitionProvider());
        }
        if (this.getImplementationProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getImplementationProvider());
        }
        if (this.getReferencesProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getReferencesProvider());
        }
        if (this.getDocumentHighlightProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getDocumentHighlightProvider());
        }
        if (this.getDocumentSymbolProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getDocumentSymbolProvider());
        }
        if (this.getCodeActionProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getCodeActionProvider());
        }
        if (this.getCodeLensProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getCodeLensProvider());
        }
        if (this.getDocumentLinkProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getDocumentLinkProvider());
        }
        if (this.getColorProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getColorProvider());
        }
        if (this.getWorkspaceSymbolProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getWorkspaceSymbolProvider());
        }
        if (this.getDocumentFormattingProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getDocumentFormattingProvider());
        }
        if (this.getDocumentRangeFormattingProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getDocumentRangeFormattingProvider());
        }
        if (this.getDocumentOnTypeFormattingProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getDocumentOnTypeFormattingProvider());
        }
        if (this.getRenameProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getRenameProvider());
        }
        if (this.getFoldingRangeProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getFoldingRangeProvider());
        }
        if (this.getSelectionRangeProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getSelectionRangeProvider());
        }
        if (this.getExecuteCommandProvider() != null) {
            hash = 83 * hash + Objects.hashCode(this.getExecuteCommandProvider());
        }
        if (this.getExperimental() != null) {
            hash = 83 * hash + Objects.hashCode(this.getExperimental());
        }
        if (this.getWorkspace() != null) {
            hash = 83 * hash + Objects.hashCode(this.getWorkspace());
        }
        return hash;
    }

    public static ServerCapabilities create() {
        final JSONObject json = new JSONObject();
        return new ServerCapabilities(json);
    }

    public static class WorkspaceCapabilities extends JSONBase {

        WorkspaceCapabilities(JSONObject jsonData) {
            super(jsonData);
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
                hash = 53 * hash + Objects.hashCode(this.getWorkspaceFolders());
            }
            return hash;
        }

        public static class WorkspaceFoldersCapabilities extends JSONBase {

            WorkspaceFoldersCapabilities(JSONObject jsonData) {
                super(jsonData);
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
                    hash = 43 * hash + Boolean.hashCode(this.getSupported());
                }
                if (this.getChangeNotifications() != null) {
                    hash = 43 * hash + Objects.hashCode(this.getChangeNotifications());
                }
                return hash;
            }
        }
    }
}
