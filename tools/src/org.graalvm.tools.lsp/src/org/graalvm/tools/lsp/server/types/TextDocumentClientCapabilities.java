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
 * Text document specific client capabilities.
 */
public class TextDocumentClientCapabilities {

    final JSONObject jsonData;

    TextDocumentClientCapabilities(JSONObject jsonData) {
        this.jsonData = jsonData;
    }

    /**
     * Defines which synchronization capabilities the client supports.
     */
    public SynchronizationCapabilities getSynchronization() {
        return jsonData.has("synchronization") ? new SynchronizationCapabilities(jsonData.optJSONObject("synchronization")) : null;
    }

    public TextDocumentClientCapabilities setSynchronization(SynchronizationCapabilities synchronization) {
        jsonData.putOpt("synchronization", synchronization != null ? synchronization.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/completion`.
     */
    public CompletionCapabilities getCompletion() {
        return jsonData.has("completion") ? new CompletionCapabilities(jsonData.optJSONObject("completion")) : null;
    }

    public TextDocumentClientCapabilities setCompletion(CompletionCapabilities completion) {
        jsonData.putOpt("completion", completion != null ? completion.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/hover`.
     */
    public HoverCapabilities getHover() {
        return jsonData.has("hover") ? new HoverCapabilities(jsonData.optJSONObject("hover")) : null;
    }

    public TextDocumentClientCapabilities setHover(HoverCapabilities hover) {
        jsonData.putOpt("hover", hover != null ? hover.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/signatureHelp`.
     */
    public SignatureHelpCapabilities getSignatureHelp() {
        return jsonData.has("signatureHelp") ? new SignatureHelpCapabilities(jsonData.optJSONObject("signatureHelp")) : null;
    }

    public TextDocumentClientCapabilities setSignatureHelp(SignatureHelpCapabilities signatureHelp) {
        jsonData.putOpt("signatureHelp", signatureHelp != null ? signatureHelp.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/references`.
     */
    public ReferencesCapabilities getReferences() {
        return jsonData.has("references") ? new ReferencesCapabilities(jsonData.optJSONObject("references")) : null;
    }

    public TextDocumentClientCapabilities setReferences(ReferencesCapabilities references) {
        jsonData.putOpt("references", references != null ? references.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/documentHighlight`.
     */
    public DocumentHighlightCapabilities getDocumentHighlight() {
        return jsonData.has("documentHighlight") ? new DocumentHighlightCapabilities(jsonData.optJSONObject("documentHighlight")) : null;
    }

    public TextDocumentClientCapabilities setDocumentHighlight(DocumentHighlightCapabilities documentHighlight) {
        jsonData.putOpt("documentHighlight", documentHighlight != null ? documentHighlight.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/documentSymbol`.
     */
    public DocumentSymbolCapabilities getDocumentSymbol() {
        return jsonData.has("documentSymbol") ? new DocumentSymbolCapabilities(jsonData.optJSONObject("documentSymbol")) : null;
    }

    public TextDocumentClientCapabilities setDocumentSymbol(DocumentSymbolCapabilities documentSymbol) {
        jsonData.putOpt("documentSymbol", documentSymbol != null ? documentSymbol.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/formatting`.
     */
    public FormattingCapabilities getFormatting() {
        return jsonData.has("formatting") ? new FormattingCapabilities(jsonData.optJSONObject("formatting")) : null;
    }

    public TextDocumentClientCapabilities setFormatting(FormattingCapabilities formatting) {
        jsonData.putOpt("formatting", formatting != null ? formatting.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/rangeFormatting`.
     */
    public RangeFormattingCapabilities getRangeFormatting() {
        return jsonData.has("rangeFormatting") ? new RangeFormattingCapabilities(jsonData.optJSONObject("rangeFormatting")) : null;
    }

    public TextDocumentClientCapabilities setRangeFormatting(RangeFormattingCapabilities rangeFormatting) {
        jsonData.putOpt("rangeFormatting", rangeFormatting != null ? rangeFormatting.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/onTypeFormatting`.
     */
    public OnTypeFormattingCapabilities getOnTypeFormatting() {
        return jsonData.has("onTypeFormatting") ? new OnTypeFormattingCapabilities(jsonData.optJSONObject("onTypeFormatting")) : null;
    }

    public TextDocumentClientCapabilities setOnTypeFormatting(OnTypeFormattingCapabilities onTypeFormatting) {
        jsonData.putOpt("onTypeFormatting", onTypeFormatting != null ? onTypeFormatting.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/declaration`.
     */
    public DeclarationCapabilities getDeclaration() {
        return jsonData.has("declaration") ? new DeclarationCapabilities(jsonData.optJSONObject("declaration")) : null;
    }

    public TextDocumentClientCapabilities setDeclaration(DeclarationCapabilities declaration) {
        jsonData.putOpt("declaration", declaration != null ? declaration.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/definition`.
     */
    public DefinitionCapabilities getDefinition() {
        return jsonData.has("definition") ? new DefinitionCapabilities(jsonData.optJSONObject("definition")) : null;
    }

    public TextDocumentClientCapabilities setDefinition(DefinitionCapabilities definition) {
        jsonData.putOpt("definition", definition != null ? definition.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/typeDefinition`.
     */
    public TypeDefinitionCapabilities getTypeDefinition() {
        return jsonData.has("typeDefinition") ? new TypeDefinitionCapabilities(jsonData.optJSONObject("typeDefinition")) : null;
    }

    public TextDocumentClientCapabilities setTypeDefinition(TypeDefinitionCapabilities typeDefinition) {
        jsonData.putOpt("typeDefinition", typeDefinition != null ? typeDefinition.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/implementation`.
     */
    public ImplementationCapabilities getImplementation() {
        return jsonData.has("implementation") ? new ImplementationCapabilities(jsonData.optJSONObject("implementation")) : null;
    }

    public TextDocumentClientCapabilities setImplementation(ImplementationCapabilities implementation) {
        jsonData.putOpt("implementation", implementation != null ? implementation.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/codeAction`.
     */
    public CodeActionCapabilities getCodeAction() {
        return jsonData.has("codeAction") ? new CodeActionCapabilities(jsonData.optJSONObject("codeAction")) : null;
    }

    public TextDocumentClientCapabilities setCodeAction(CodeActionCapabilities codeAction) {
        jsonData.putOpt("codeAction", codeAction != null ? codeAction.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/codeLens`.
     */
    public CodeLensCapabilities getCodeLens() {
        return jsonData.has("codeLens") ? new CodeLensCapabilities(jsonData.optJSONObject("codeLens")) : null;
    }

    public TextDocumentClientCapabilities setCodeLens(CodeLensCapabilities codeLens) {
        jsonData.putOpt("codeLens", codeLens != null ? codeLens.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/documentLink`.
     */
    public DocumentLinkCapabilities getDocumentLink() {
        return jsonData.has("documentLink") ? new DocumentLinkCapabilities(jsonData.optJSONObject("documentLink")) : null;
    }

    public TextDocumentClientCapabilities setDocumentLink(DocumentLinkCapabilities documentLink) {
        jsonData.putOpt("documentLink", documentLink != null ? documentLink.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the colorProvider.
     */
    public ColorProviderCapabilities getColorProvider() {
        return jsonData.has("colorProvider") ? new ColorProviderCapabilities(jsonData.optJSONObject("colorProvider")) : null;
    }

    public TextDocumentClientCapabilities setColorProvider(ColorProviderCapabilities colorProvider) {
        jsonData.putOpt("colorProvider", colorProvider != null ? colorProvider.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/rename`.
     */
    public RenameCapabilities getRename() {
        return jsonData.has("rename") ? new RenameCapabilities(jsonData.optJSONObject("rename")) : null;
    }

    public TextDocumentClientCapabilities setRename(RenameCapabilities rename) {
        jsonData.putOpt("rename", rename != null ? rename.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to `textDocument/publishDiagnostics`.
     */
    public PublishDiagnosticsCapabilities getPublishDiagnostics() {
        return jsonData.has("publishDiagnostics") ? new PublishDiagnosticsCapabilities(jsonData.optJSONObject("publishDiagnostics")) : null;
    }

    public TextDocumentClientCapabilities setPublishDiagnostics(PublishDiagnosticsCapabilities publishDiagnostics) {
        jsonData.putOpt("publishDiagnostics", publishDiagnostics != null ? publishDiagnostics.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to `textDocument/foldingRange` requests.
     */
    public FoldingRangeCapabilities getFoldingRange() {
        return jsonData.has("foldingRange") ? new FoldingRangeCapabilities(jsonData.optJSONObject("foldingRange")) : null;
    }

    public TextDocumentClientCapabilities setFoldingRange(FoldingRangeCapabilities foldingRange) {
        jsonData.putOpt("foldingRange", foldingRange != null ? foldingRange.jsonData : null);
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
        TextDocumentClientCapabilities other = (TextDocumentClientCapabilities) obj;
        if (!Objects.equals(this.getSynchronization(), other.getSynchronization())) {
            return false;
        }
        if (!Objects.equals(this.getCompletion(), other.getCompletion())) {
            return false;
        }
        if (!Objects.equals(this.getHover(), other.getHover())) {
            return false;
        }
        if (!Objects.equals(this.getSignatureHelp(), other.getSignatureHelp())) {
            return false;
        }
        if (!Objects.equals(this.getReferences(), other.getReferences())) {
            return false;
        }
        if (!Objects.equals(this.getDocumentHighlight(), other.getDocumentHighlight())) {
            return false;
        }
        if (!Objects.equals(this.getDocumentSymbol(), other.getDocumentSymbol())) {
            return false;
        }
        if (!Objects.equals(this.getFormatting(), other.getFormatting())) {
            return false;
        }
        if (!Objects.equals(this.getRangeFormatting(), other.getRangeFormatting())) {
            return false;
        }
        if (!Objects.equals(this.getOnTypeFormatting(), other.getOnTypeFormatting())) {
            return false;
        }
        if (!Objects.equals(this.getDeclaration(), other.getDeclaration())) {
            return false;
        }
        if (!Objects.equals(this.getDefinition(), other.getDefinition())) {
            return false;
        }
        if (!Objects.equals(this.getTypeDefinition(), other.getTypeDefinition())) {
            return false;
        }
        if (!Objects.equals(this.getImplementation(), other.getImplementation())) {
            return false;
        }
        if (!Objects.equals(this.getCodeAction(), other.getCodeAction())) {
            return false;
        }
        if (!Objects.equals(this.getCodeLens(), other.getCodeLens())) {
            return false;
        }
        if (!Objects.equals(this.getDocumentLink(), other.getDocumentLink())) {
            return false;
        }
        if (!Objects.equals(this.getColorProvider(), other.getColorProvider())) {
            return false;
        }
        if (!Objects.equals(this.getRename(), other.getRename())) {
            return false;
        }
        if (!Objects.equals(this.getPublishDiagnostics(), other.getPublishDiagnostics())) {
            return false;
        }
        if (!Objects.equals(this.getFoldingRange(), other.getFoldingRange())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getSynchronization() != null) {
            hash = 17 * hash + Objects.hashCode(this.getSynchronization());
        }
        if (this.getCompletion() != null) {
            hash = 17 * hash + Objects.hashCode(this.getCompletion());
        }
        if (this.getHover() != null) {
            hash = 17 * hash + Objects.hashCode(this.getHover());
        }
        if (this.getSignatureHelp() != null) {
            hash = 17 * hash + Objects.hashCode(this.getSignatureHelp());
        }
        if (this.getReferences() != null) {
            hash = 17 * hash + Objects.hashCode(this.getReferences());
        }
        if (this.getDocumentHighlight() != null) {
            hash = 17 * hash + Objects.hashCode(this.getDocumentHighlight());
        }
        if (this.getDocumentSymbol() != null) {
            hash = 17 * hash + Objects.hashCode(this.getDocumentSymbol());
        }
        if (this.getFormatting() != null) {
            hash = 17 * hash + Objects.hashCode(this.getFormatting());
        }
        if (this.getRangeFormatting() != null) {
            hash = 17 * hash + Objects.hashCode(this.getRangeFormatting());
        }
        if (this.getOnTypeFormatting() != null) {
            hash = 17 * hash + Objects.hashCode(this.getOnTypeFormatting());
        }
        if (this.getDeclaration() != null) {
            hash = 17 * hash + Objects.hashCode(this.getDeclaration());
        }
        if (this.getDefinition() != null) {
            hash = 17 * hash + Objects.hashCode(this.getDefinition());
        }
        if (this.getTypeDefinition() != null) {
            hash = 17 * hash + Objects.hashCode(this.getTypeDefinition());
        }
        if (this.getImplementation() != null) {
            hash = 17 * hash + Objects.hashCode(this.getImplementation());
        }
        if (this.getCodeAction() != null) {
            hash = 17 * hash + Objects.hashCode(this.getCodeAction());
        }
        if (this.getCodeLens() != null) {
            hash = 17 * hash + Objects.hashCode(this.getCodeLens());
        }
        if (this.getDocumentLink() != null) {
            hash = 17 * hash + Objects.hashCode(this.getDocumentLink());
        }
        if (this.getColorProvider() != null) {
            hash = 17 * hash + Objects.hashCode(this.getColorProvider());
        }
        if (this.getRename() != null) {
            hash = 17 * hash + Objects.hashCode(this.getRename());
        }
        if (this.getPublishDiagnostics() != null) {
            hash = 17 * hash + Objects.hashCode(this.getPublishDiagnostics());
        }
        if (this.getFoldingRange() != null) {
            hash = 17 * hash + Objects.hashCode(this.getFoldingRange());
        }
        return hash;
    }

    public static TextDocumentClientCapabilities create() {
        final JSONObject json = new JSONObject();
        return new TextDocumentClientCapabilities(json);
    }

    public static class SynchronizationCapabilities {

        final JSONObject jsonData;

        SynchronizationCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Whether text document synchronization supports dynamic registration.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public SynchronizationCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
            jsonData.putOpt("dynamicRegistration", dynamicRegistration);
            return this;
        }

        /**
         * The client supports sending will save notifications.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getWillSave() {
            return jsonData.has("willSave") ? jsonData.getBoolean("willSave") : null;
        }

        public SynchronizationCapabilities setWillSave(Boolean willSave) {
            jsonData.putOpt("willSave", willSave);
            return this;
        }

        /**
         * The client supports sending a will save request and waits for a response providing text
         * edits which will be applied to the document before it is saved.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getWillSaveWaitUntil() {
            return jsonData.has("willSaveWaitUntil") ? jsonData.getBoolean("willSaveWaitUntil") : null;
        }

        public SynchronizationCapabilities setWillSaveWaitUntil(Boolean willSaveWaitUntil) {
            jsonData.putOpt("willSaveWaitUntil", willSaveWaitUntil);
            return this;
        }

        /**
         * The client supports did save notifications.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDidSave() {
            return jsonData.has("didSave") ? jsonData.getBoolean("didSave") : null;
        }

        public SynchronizationCapabilities setDidSave(Boolean didSave) {
            jsonData.putOpt("didSave", didSave);
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
            SynchronizationCapabilities other = (SynchronizationCapabilities) obj;
            if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
                return false;
            }
            if (!Objects.equals(this.getWillSave(), other.getWillSave())) {
                return false;
            }
            if (!Objects.equals(this.getWillSaveWaitUntil(), other.getWillSaveWaitUntil())) {
                return false;
            }
            if (!Objects.equals(this.getDidSave(), other.getDidSave())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            if (this.getDynamicRegistration() != null) {
                hash = 41 * hash + Boolean.hashCode(this.getDynamicRegistration());
            }
            if (this.getWillSave() != null) {
                hash = 41 * hash + Boolean.hashCode(this.getWillSave());
            }
            if (this.getWillSaveWaitUntil() != null) {
                hash = 41 * hash + Boolean.hashCode(this.getWillSaveWaitUntil());
            }
            if (this.getDidSave() != null) {
                hash = 41 * hash + Boolean.hashCode(this.getDidSave());
            }
            return hash;
        }
    }

    public static class CompletionCapabilities {

        final JSONObject jsonData;

        CompletionCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Whether completion supports dynamic registration.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public CompletionCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
            jsonData.putOpt("dynamicRegistration", dynamicRegistration);
            return this;
        }

        /**
         * The client supports the following `CompletionItem` specific capabilities.
         */
        public CompletionItemCapabilities getCompletionItem() {
            return jsonData.has("completionItem") ? new CompletionItemCapabilities(jsonData.optJSONObject("completionItem")) : null;
        }

        public CompletionCapabilities setCompletionItem(CompletionItemCapabilities completionItem) {
            jsonData.putOpt("completionItem", completionItem != null ? completionItem.jsonData : null);
            return this;
        }

        public CompletionItemKindCapabilities getCompletionItemKind() {
            return jsonData.has("completionItemKind") ? new CompletionItemKindCapabilities(jsonData.optJSONObject("completionItemKind")) : null;
        }

        public CompletionCapabilities setCompletionItemKind(CompletionItemKindCapabilities completionItemKind) {
            jsonData.putOpt("completionItemKind", completionItemKind != null ? completionItemKind.jsonData : null);
            return this;
        }

        /**
         * The client supports to send additional context information for a
         * `textDocument/completion` requestion.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getContextSupport() {
            return jsonData.has("contextSupport") ? jsonData.getBoolean("contextSupport") : null;
        }

        public CompletionCapabilities setContextSupport(Boolean contextSupport) {
            jsonData.putOpt("contextSupport", contextSupport);
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
            CompletionCapabilities other = (CompletionCapabilities) obj;
            if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
                return false;
            }
            if (!Objects.equals(this.getCompletionItem(), other.getCompletionItem())) {
                return false;
            }
            if (!Objects.equals(this.getCompletionItemKind(), other.getCompletionItemKind())) {
                return false;
            }
            if (!Objects.equals(this.getContextSupport(), other.getContextSupport())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 2;
            if (this.getDynamicRegistration() != null) {
                hash = 79 * hash + Boolean.hashCode(this.getDynamicRegistration());
            }
            if (this.getCompletionItem() != null) {
                hash = 79 * hash + Objects.hashCode(this.getCompletionItem());
            }
            if (this.getCompletionItemKind() != null) {
                hash = 79 * hash + Objects.hashCode(this.getCompletionItemKind());
            }
            if (this.getContextSupport() != null) {
                hash = 79 * hash + Boolean.hashCode(this.getContextSupport());
            }
            return hash;
        }

        public static class CompletionItemCapabilities {

            final JSONObject jsonData;

            CompletionItemCapabilities(JSONObject jsonData) {
                this.jsonData = jsonData;
            }

            /**
             * Client supports snippets as insert text.
             *
             * A snippet can define tab stops and placeholders with `$1`, `$2` and `${3:foo}`. `$0`
             * defines the final tab stop, it defaults to the end of the snippet. Placeholders with
             * equal identifiers are linked, that is typing in one will update others too.
             */
            @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
            public Boolean getSnippetSupport() {
                return jsonData.has("snippetSupport") ? jsonData.getBoolean("snippetSupport") : null;
            }

            public CompletionItemCapabilities setSnippetSupport(Boolean snippetSupport) {
                jsonData.putOpt("snippetSupport", snippetSupport);
                return this;
            }

            /**
             * Client supports commit characters on a completion item.
             */
            @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
            public Boolean getCommitCharactersSupport() {
                return jsonData.has("commitCharactersSupport") ? jsonData.getBoolean("commitCharactersSupport") : null;
            }

            public CompletionItemCapabilities setCommitCharactersSupport(Boolean commitCharactersSupport) {
                jsonData.putOpt("commitCharactersSupport", commitCharactersSupport);
                return this;
            }

            /**
             * Client supports the follow content formats for the documentation property. The order
             * describes the preferred format of the client.
             */
            public List<MarkupKind> getDocumentationFormat() {
                final JSONArray json = jsonData.optJSONArray("documentationFormat");
                if (json == null) {
                    return null;
                }
                final List<MarkupKind> list = new ArrayList<>(json.length());
                for (int i = 0; i < json.length(); i++) {
                    list.add(MarkupKind.get(json.getString(i)));
                }
                return Collections.unmodifiableList(list);
            }

            public CompletionItemCapabilities setDocumentationFormat(List<MarkupKind> documentationFormat) {
                if (documentationFormat != null) {
                    final JSONArray json = new JSONArray();
                    for (MarkupKind markupKind : documentationFormat) {
                        json.put(markupKind.getStringValue());
                    }
                    jsonData.put("documentationFormat", json);
                }
                return this;
            }

            /**
             * Client supports the deprecated property on a completion item.
             */
            @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
            public Boolean getDeprecatedSupport() {
                return jsonData.has("deprecatedSupport") ? jsonData.getBoolean("deprecatedSupport") : null;
            }

            public CompletionItemCapabilities setDeprecatedSupport(Boolean deprecatedSupport) {
                jsonData.putOpt("deprecatedSupport", deprecatedSupport);
                return this;
            }

            /**
             * Client supports the preselect property on a completion item.
             */
            @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
            public Boolean getPreselectSupport() {
                return jsonData.has("preselectSupport") ? jsonData.getBoolean("preselectSupport") : null;
            }

            public CompletionItemCapabilities setPreselectSupport(Boolean preselectSupport) {
                jsonData.putOpt("preselectSupport", preselectSupport);
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
                CompletionItemCapabilities other = (CompletionItemCapabilities) obj;
                if (!Objects.equals(this.getSnippetSupport(), other.getSnippetSupport())) {
                    return false;
                }
                if (!Objects.equals(this.getCommitCharactersSupport(), other.getCommitCharactersSupport())) {
                    return false;
                }
                if (!Objects.equals(this.getDocumentationFormat(), other.getDocumentationFormat())) {
                    return false;
                }
                if (!Objects.equals(this.getDeprecatedSupport(), other.getDeprecatedSupport())) {
                    return false;
                }
                if (!Objects.equals(this.getPreselectSupport(), other.getPreselectSupport())) {
                    return false;
                }
                return true;
            }

            @Override
            public int hashCode() {
                int hash = 7;
                if (this.getSnippetSupport() != null) {
                    hash = 61 * hash + Boolean.hashCode(this.getSnippetSupport());
                }
                if (this.getCommitCharactersSupport() != null) {
                    hash = 61 * hash + Boolean.hashCode(this.getCommitCharactersSupport());
                }
                if (this.getDocumentationFormat() != null) {
                    hash = 61 * hash + Objects.hashCode(this.getDocumentationFormat());
                }
                if (this.getDeprecatedSupport() != null) {
                    hash = 61 * hash + Boolean.hashCode(this.getDeprecatedSupport());
                }
                if (this.getPreselectSupport() != null) {
                    hash = 61 * hash + Boolean.hashCode(this.getPreselectSupport());
                }
                return hash;
            }
        }

        public static class CompletionItemKindCapabilities {

            final JSONObject jsonData;

            CompletionItemKindCapabilities(JSONObject jsonData) {
                this.jsonData = jsonData;
            }

            /**
             * The completion item kind values the client supports. When this property exists the
             * client also guarantees that it will handle values outside its set gracefully and
             * falls back to a default value when unknown.
             *
             * If this property is not present the client only supports the completion items kinds
             * from `Text` to `Reference` as defined in the initial version of the protocol.
             */
            public List<CompletionItemKind> getValueSet() {
                final JSONArray json = jsonData.optJSONArray("valueSet");
                if (json == null) {
                    return null;
                }
                final List<CompletionItemKind> list = new ArrayList<>(json.length());
                for (int i = 0; i < json.length(); i++) {
                    list.add(CompletionItemKind.get(json.getInt(i)));
                }
                return Collections.unmodifiableList(list);
            }

            public CompletionItemKindCapabilities setValueSet(List<CompletionItemKind> valueSet) {
                if (valueSet != null) {
                    final JSONArray json = new JSONArray();
                    for (CompletionItemKind completionItemKind : valueSet) {
                        json.put(completionItemKind.getIntValue());
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
                CompletionItemKindCapabilities other = (CompletionItemKindCapabilities) obj;
                if (!Objects.equals(this.getValueSet(), other.getValueSet())) {
                    return false;
                }
                return true;
            }

            @Override
            public int hashCode() {
                int hash = 7;
                if (this.getValueSet() != null) {
                    hash = 89 * hash + Objects.hashCode(this.getValueSet());
                }
                return hash;
            }
        }
    }

    public static class HoverCapabilities {

        final JSONObject jsonData;

        HoverCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Whether hover supports dynamic registration.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public HoverCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
            jsonData.putOpt("dynamicRegistration", dynamicRegistration);
            return this;
        }

        /**
         * Client supports the follow content formats for the content property. The order describes
         * the preferred format of the client.
         */
        public List<MarkupKind> getContentFormat() {
            final JSONArray json = jsonData.optJSONArray("contentFormat");
            if (json == null) {
                return null;
            }
            final List<MarkupKind> list = new ArrayList<>(json.length());
            for (int i = 0; i < json.length(); i++) {
                list.add(MarkupKind.get(json.getString(i)));
            }
            return Collections.unmodifiableList(list);
        }

        public HoverCapabilities setContentFormat(List<MarkupKind> contentFormat) {
            if (contentFormat != null) {
                final JSONArray json = new JSONArray();
                for (MarkupKind markupKind : contentFormat) {
                    json.put(markupKind.getStringValue());
                }
                jsonData.put("contentFormat", json);
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
            HoverCapabilities other = (HoverCapabilities) obj;
            if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
                return false;
            }
            if (!Objects.equals(this.getContentFormat(), other.getContentFormat())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            if (this.getDynamicRegistration() != null) {
                hash = 19 * hash + Boolean.hashCode(this.getDynamicRegistration());
            }
            if (this.getContentFormat() != null) {
                hash = 19 * hash + Objects.hashCode(this.getContentFormat());
            }
            return hash;
        }
    }

    public static class SignatureHelpCapabilities {

        final JSONObject jsonData;

        SignatureHelpCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Whether signature help supports dynamic registration.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public SignatureHelpCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
            jsonData.putOpt("dynamicRegistration", dynamicRegistration);
            return this;
        }

        /**
         * The client supports the following `SignatureInformation` specific properties.
         */
        public SignatureInformationCapabilities getSignatureInformation() {
            return jsonData.has("signatureInformation") ? new SignatureInformationCapabilities(jsonData.optJSONObject("signatureInformation")) : null;
        }

        public SignatureHelpCapabilities setSignatureInformation(SignatureInformationCapabilities signatureInformation) {
            jsonData.putOpt("signatureInformation", signatureInformation != null ? signatureInformation.jsonData : null);
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
            SignatureHelpCapabilities other = (SignatureHelpCapabilities) obj;
            if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
                return false;
            }
            if (!Objects.equals(this.getSignatureInformation(), other.getSignatureInformation())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            if (this.getDynamicRegistration() != null) {
                hash = 59 * hash + Boolean.hashCode(this.getDynamicRegistration());
            }
            if (this.getSignatureInformation() != null) {
                hash = 59 * hash + Objects.hashCode(this.getSignatureInformation());
            }
            return hash;
        }

        public static class SignatureInformationCapabilities {

            final JSONObject jsonData;

            SignatureInformationCapabilities(JSONObject jsonData) {
                this.jsonData = jsonData;
            }

            /**
             * Client supports the follow content formats for the documentation property. The order
             * describes the preferred format of the client.
             */
            public List<MarkupKind> getDocumentationFormat() {
                final JSONArray json = jsonData.optJSONArray("documentationFormat");
                if (json == null) {
                    return null;
                }
                final List<MarkupKind> list = new ArrayList<>(json.length());
                for (int i = 0; i < json.length(); i++) {
                    list.add(MarkupKind.get(json.getString(i)));
                }
                return Collections.unmodifiableList(list);
            }

            public SignatureInformationCapabilities setDocumentationFormat(List<MarkupKind> documentationFormat) {
                if (documentationFormat != null) {
                    final JSONArray json = new JSONArray();
                    for (MarkupKind markupKind : documentationFormat) {
                        json.put(markupKind.getStringValue());
                    }
                    jsonData.put("documentationFormat", json);
                }
                return this;
            }

            /**
             * Client capabilities specific to parameter information.
             */
            public ParameterInformationCapabilities getParameterInformation() {
                return jsonData.has("parameterInformation") ? new ParameterInformationCapabilities(jsonData.optJSONObject("parameterInformation")) : null;
            }

            public SignatureInformationCapabilities setParameterInformation(ParameterInformationCapabilities parameterInformation) {
                jsonData.putOpt("parameterInformation", parameterInformation != null ? parameterInformation.jsonData : null);
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
                SignatureInformationCapabilities other = (SignatureInformationCapabilities) obj;
                if (!Objects.equals(this.getDocumentationFormat(), other.getDocumentationFormat())) {
                    return false;
                }
                if (!Objects.equals(this.getParameterInformation(), other.getParameterInformation())) {
                    return false;
                }
                return true;
            }

            @Override
            public int hashCode() {
                int hash = 7;
                if (this.getDocumentationFormat() != null) {
                    hash = 71 * hash + Objects.hashCode(this.getDocumentationFormat());
                }
                if (this.getParameterInformation() != null) {
                    hash = 71 * hash + Objects.hashCode(this.getParameterInformation());
                }
                return hash;
            }

            public static class ParameterInformationCapabilities {

                final JSONObject jsonData;

                ParameterInformationCapabilities(JSONObject jsonData) {
                    this.jsonData = jsonData;
                }

                /**
                 * The client supports processing label offsets instead of a simple label string.
                 */
                @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
                public Boolean getLabelOffsetSupport() {
                    return jsonData.has("labelOffsetSupport") ? jsonData.getBoolean("labelOffsetSupport") : null;
                }

                public ParameterInformationCapabilities setLabelOffsetSupport(Boolean labelOffsetSupport) {
                    jsonData.putOpt("labelOffsetSupport", labelOffsetSupport);
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
                    ParameterInformationCapabilities other = (ParameterInformationCapabilities) obj;
                    if (!Objects.equals(this.getLabelOffsetSupport(), other.getLabelOffsetSupport())) {
                        return false;
                    }
                    return true;
                }

                @Override
                public int hashCode() {
                    int hash = 5;
                    if (this.getLabelOffsetSupport() != null) {
                        hash = 47 * hash + Boolean.hashCode(this.getLabelOffsetSupport());
                    }
                    return hash;
                }
            }
        }
    }

    public static class ReferencesCapabilities {

        final JSONObject jsonData;

        ReferencesCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Whether references supports dynamic registration.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public ReferencesCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
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
            ReferencesCapabilities other = (ReferencesCapabilities) obj;
            if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            if (this.getDynamicRegistration() != null) {
                hash = 59 * hash + Boolean.hashCode(this.getDynamicRegistration());
            }
            return hash;
        }
    }

    public static class DocumentHighlightCapabilities {

        final JSONObject jsonData;

        DocumentHighlightCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Whether document highlight supports dynamic registration.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public DocumentHighlightCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
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
            DocumentHighlightCapabilities other = (DocumentHighlightCapabilities) obj;
            if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            if (this.getDynamicRegistration() != null) {
                hash = 29 * hash + Boolean.hashCode(this.getDynamicRegistration());
            }
            return hash;
        }
    }

    public static class DocumentSymbolCapabilities {

        final JSONObject jsonData;

        DocumentSymbolCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Whether document symbol supports dynamic registration.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public DocumentSymbolCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
            jsonData.putOpt("dynamicRegistration", dynamicRegistration);
            return this;
        }

        /**
         * Specific capabilities for the `SymbolKind`.
         */
        public SymbolKindCapabilities getSymbolKind() {
            return jsonData.has("symbolKind") ? new SymbolKindCapabilities(jsonData.optJSONObject("symbolKind")) : null;
        }

        public DocumentSymbolCapabilities setSymbolKind(SymbolKindCapabilities symbolKind) {
            jsonData.putOpt("symbolKind", symbolKind != null ? symbolKind.jsonData : null);
            return this;
        }

        /**
         * The client support hierarchical document symbols.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getHierarchicalDocumentSymbolSupport() {
            return jsonData.has("hierarchicalDocumentSymbolSupport") ? jsonData.getBoolean("hierarchicalDocumentSymbolSupport") : null;
        }

        public DocumentSymbolCapabilities setHierarchicalDocumentSymbolSupport(Boolean hierarchicalDocumentSymbolSupport) {
            jsonData.putOpt("hierarchicalDocumentSymbolSupport", hierarchicalDocumentSymbolSupport);
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
            DocumentSymbolCapabilities other = (DocumentSymbolCapabilities) obj;
            if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
                return false;
            }
            if (!Objects.equals(this.getSymbolKind(), other.getSymbolKind())) {
                return false;
            }
            if (!Objects.equals(this.getHierarchicalDocumentSymbolSupport(), other.getHierarchicalDocumentSymbolSupport())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            if (this.getDynamicRegistration() != null) {
                hash = 79 * hash + Boolean.hashCode(this.getDynamicRegistration());
            }
            if (this.getSymbolKind() != null) {
                hash = 79 * hash + Objects.hashCode(this.getSymbolKind());
            }
            if (this.getHierarchicalDocumentSymbolSupport() != null) {
                hash = 79 * hash + Boolean.hashCode(this.getHierarchicalDocumentSymbolSupport());
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
                int hash = 7;
                if (this.getValueSet() != null) {
                    hash = 67 * hash + Objects.hashCode(this.getValueSet());
                }
                return hash;
            }
        }
    }

    public static class FormattingCapabilities {

        final JSONObject jsonData;

        FormattingCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Whether formatting supports dynamic registration.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public FormattingCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
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
            FormattingCapabilities other = (FormattingCapabilities) obj;
            if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 2;
            if (this.getDynamicRegistration() != null) {
                hash = 19 * hash + Boolean.hashCode(this.getDynamicRegistration());
            }
            return hash;
        }
    }

    public static class RangeFormattingCapabilities {

        final JSONObject jsonData;

        RangeFormattingCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Whether range formatting supports dynamic registration.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public RangeFormattingCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
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
            RangeFormattingCapabilities other = (RangeFormattingCapabilities) obj;
            if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            if (this.getDynamicRegistration() != null) {
                hash = 71 * hash + Boolean.hashCode(this.getDynamicRegistration());
            }
            return hash;
        }
    }

    public static class OnTypeFormattingCapabilities {

        final JSONObject jsonData;

        OnTypeFormattingCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Whether on type formatting supports dynamic registration.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public OnTypeFormattingCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
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
            OnTypeFormattingCapabilities other = (OnTypeFormattingCapabilities) obj;
            if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            if (this.getDynamicRegistration() != null) {
                hash = 89 * hash + Boolean.hashCode(this.getDynamicRegistration());
            }
            return hash;
        }
    }

    public static class DeclarationCapabilities {

        final JSONObject jsonData;

        DeclarationCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Whether declaration supports dynamic registration. If this is set to `true` the client
         * supports the new `(TextDocumentRegistrationOptions & StaticRegistrationOptions)` return
         * value for the corresponding server capability as well.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public DeclarationCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
            jsonData.putOpt("dynamicRegistration", dynamicRegistration);
            return this;
        }

        /**
         * The client supports additional metadata in the form of declaration links.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getLinkSupport() {
            return jsonData.has("linkSupport") ? jsonData.getBoolean("linkSupport") : null;
        }

        public DeclarationCapabilities setLinkSupport(Boolean linkSupport) {
            jsonData.putOpt("linkSupport", linkSupport);
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
            DeclarationCapabilities other = (DeclarationCapabilities) obj;
            if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
                return false;
            }
            if (!Objects.equals(this.getLinkSupport(), other.getLinkSupport())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            if (this.getDynamicRegistration() != null) {
                hash = 13 * hash + Boolean.hashCode(this.getDynamicRegistration());
            }
            if (this.getLinkSupport() != null) {
                hash = 13 * hash + Boolean.hashCode(this.getLinkSupport());
            }
            return hash;
        }
    }

    public static class DefinitionCapabilities {

        final JSONObject jsonData;

        DefinitionCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Whether definition supports dynamic registration.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public DefinitionCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
            jsonData.putOpt("dynamicRegistration", dynamicRegistration);
            return this;
        }

        /**
         * The client supports additional metadata in the form of definition links.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getLinkSupport() {
            return jsonData.has("linkSupport") ? jsonData.getBoolean("linkSupport") : null;
        }

        public DefinitionCapabilities setLinkSupport(Boolean linkSupport) {
            jsonData.putOpt("linkSupport", linkSupport);
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
            DefinitionCapabilities other = (DefinitionCapabilities) obj;
            if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
                return false;
            }
            if (!Objects.equals(this.getLinkSupport(), other.getLinkSupport())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            if (this.getDynamicRegistration() != null) {
                hash = 41 * hash + Boolean.hashCode(this.getDynamicRegistration());
            }
            if (this.getLinkSupport() != null) {
                hash = 41 * hash + Boolean.hashCode(this.getLinkSupport());
            }
            return hash;
        }
    }

    public static class TypeDefinitionCapabilities {

        final JSONObject jsonData;

        TypeDefinitionCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Whether implementation supports dynamic registration. If this is set to `true` the client
         * supports the new `(TextDocumentRegistrationOptions & StaticRegistrationOptions)` return
         * value for the corresponding server capability as well.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public TypeDefinitionCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
            jsonData.putOpt("dynamicRegistration", dynamicRegistration);
            return this;
        }

        /**
         * The client supports additional metadata in the form of definition links.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getLinkSupport() {
            return jsonData.has("linkSupport") ? jsonData.getBoolean("linkSupport") : null;
        }

        public TypeDefinitionCapabilities setLinkSupport(Boolean linkSupport) {
            jsonData.putOpt("linkSupport", linkSupport);
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
            TypeDefinitionCapabilities other = (TypeDefinitionCapabilities) obj;
            if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
                return false;
            }
            if (!Objects.equals(this.getLinkSupport(), other.getLinkSupport())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            if (this.getDynamicRegistration() != null) {
                hash = 17 * hash + Boolean.hashCode(this.getDynamicRegistration());
            }
            if (this.getLinkSupport() != null) {
                hash = 17 * hash + Boolean.hashCode(this.getLinkSupport());
            }
            return hash;
        }
    }

    public static class ImplementationCapabilities {

        final JSONObject jsonData;

        ImplementationCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Whether implementation supports dynamic registration. If this is set to `true` the client
         * supports the new `(TextDocumentRegistrationOptions & StaticRegistrationOptions)` return
         * value for the corresponding server capability as well.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public ImplementationCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
            jsonData.putOpt("dynamicRegistration", dynamicRegistration);
            return this;
        }

        /**
         * The client supports additional metadata in the form of definition links.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getLinkSupport() {
            return jsonData.has("linkSupport") ? jsonData.getBoolean("linkSupport") : null;
        }

        public ImplementationCapabilities setLinkSupport(Boolean linkSupport) {
            jsonData.putOpt("linkSupport", linkSupport);
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
            ImplementationCapabilities other = (ImplementationCapabilities) obj;
            if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
                return false;
            }
            if (!Objects.equals(this.getLinkSupport(), other.getLinkSupport())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            if (this.getDynamicRegistration() != null) {
                hash = 89 * hash + Boolean.hashCode(this.getDynamicRegistration());
            }
            if (this.getLinkSupport() != null) {
                hash = 89 * hash + Boolean.hashCode(this.getLinkSupport());
            }
            return hash;
        }
    }

    public static class CodeActionCapabilities {

        final JSONObject jsonData;

        CodeActionCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Whether code action supports dynamic registration.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public CodeActionCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
            jsonData.putOpt("dynamicRegistration", dynamicRegistration);
            return this;
        }

        /**
         * The client support code action literals as a valid response of the
         * `textDocument/codeAction` request.
         */
        public CodeActionLiteralSupportCapabilities getCodeActionLiteralSupport() {
            return jsonData.has("codeActionLiteralSupport") ? new CodeActionLiteralSupportCapabilities(jsonData.optJSONObject("codeActionLiteralSupport")) : null;
        }

        public CodeActionCapabilities setCodeActionLiteralSupport(CodeActionLiteralSupportCapabilities codeActionLiteralSupport) {
            jsonData.putOpt("codeActionLiteralSupport", codeActionLiteralSupport != null ? codeActionLiteralSupport.jsonData : null);
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
            CodeActionCapabilities other = (CodeActionCapabilities) obj;
            if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
                return false;
            }
            if (!Objects.equals(this.getCodeActionLiteralSupport(), other.getCodeActionLiteralSupport())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            if (this.getDynamicRegistration() != null) {
                hash = 41 * hash + Boolean.hashCode(this.getDynamicRegistration());
            }
            if (this.getCodeActionLiteralSupport() != null) {
                hash = 41 * hash + Objects.hashCode(this.getCodeActionLiteralSupport());
            }
            return hash;
        }

        public static class CodeActionLiteralSupportCapabilities {

            final JSONObject jsonData;

            CodeActionLiteralSupportCapabilities(JSONObject jsonData) {
                this.jsonData = jsonData;
            }

            /**
             * The code action kind is support with the following value set.
             */
            public CodeActionKindCapabilities getCodeActionKind() {
                return new CodeActionKindCapabilities(jsonData.getJSONObject("codeActionKind"));
            }

            public CodeActionLiteralSupportCapabilities setCodeActionKind(CodeActionKindCapabilities codeActionKind) {
                jsonData.put("codeActionKind", codeActionKind.jsonData);
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
                CodeActionLiteralSupportCapabilities other = (CodeActionLiteralSupportCapabilities) obj;
                if (!Objects.equals(this.getCodeActionKind(), other.getCodeActionKind())) {
                    return false;
                }
                return true;
            }

            @Override
            public int hashCode() {
                int hash = 5;
                hash = 43 * hash + Objects.hashCode(this.getCodeActionKind());
                return hash;
            }

            public static class CodeActionKindCapabilities {

                final JSONObject jsonData;

                CodeActionKindCapabilities(JSONObject jsonData) {
                    this.jsonData = jsonData;
                }

                /**
                 * The code action kind values the client supports. When this property exists the
                 * client also guarantees that it will handle values outside its set gracefully and
                 * falls back to a default value when unknown.
                 */
                public List<CodeActionKind> getValueSet() {
                    final JSONArray json = jsonData.getJSONArray("valueSet");
                    final List<CodeActionKind> list = new ArrayList<>(json.length());
                    for (int i = 0; i < json.length(); i++) {
                        list.add(CodeActionKind.get(json.getString(i)));
                    }
                    return Collections.unmodifiableList(list);
                }

                public CodeActionKindCapabilities setValueSet(List<CodeActionKind> valueSet) {
                    final JSONArray json = new JSONArray();
                    for (CodeActionKind codeActionKind : valueSet) {
                        json.put(codeActionKind.getStringValue());
                    }
                    jsonData.put("valueSet", json);
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
                    CodeActionKindCapabilities other = (CodeActionKindCapabilities) obj;
                    if (!Objects.equals(this.getValueSet(), other.getValueSet())) {
                        return false;
                    }
                    return true;
                }

                @Override
                public int hashCode() {
                    int hash = 7;
                    hash = 41 * hash + Objects.hashCode(this.getValueSet());
                    return hash;
                }
            }
        }
    }

    public static class CodeLensCapabilities {

        final JSONObject jsonData;

        CodeLensCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Whether code lens supports dynamic registration.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public CodeLensCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
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
            CodeLensCapabilities other = (CodeLensCapabilities) obj;
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

    public static class DocumentLinkCapabilities {

        final JSONObject jsonData;

        DocumentLinkCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Whether document link supports dynamic registration.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public DocumentLinkCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
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
            DocumentLinkCapabilities other = (DocumentLinkCapabilities) obj;
            if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 2;
            if (this.getDynamicRegistration() != null) {
                hash = 71 * hash + Boolean.hashCode(this.getDynamicRegistration());
            }
            return hash;
        }
    }

    public static class ColorProviderCapabilities {

        final JSONObject jsonData;

        ColorProviderCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Whether implementation supports dynamic registration. If this is set to `true` the client
         * supports the new `(ColorProviderOptions & TextDocumentRegistrationOptions &
         * StaticRegistrationOptions)` return value for the corresponding server capability as well.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public ColorProviderCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
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
            ColorProviderCapabilities other = (ColorProviderCapabilities) obj;
            if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            if (this.getDynamicRegistration() != null) {
                hash = 67 * hash + Boolean.hashCode(this.getDynamicRegistration());
            }
            return hash;
        }
    }

    public static class RenameCapabilities {

        final JSONObject jsonData;

        RenameCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Whether rename supports dynamic registration.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public RenameCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
            jsonData.putOpt("dynamicRegistration", dynamicRegistration);
            return this;
        }

        /**
         * Client supports testing for validity of rename operations before execution.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getPrepareSupport() {
            return jsonData.has("prepareSupport") ? jsonData.getBoolean("prepareSupport") : null;
        }

        public RenameCapabilities setPrepareSupport(Boolean prepareSupport) {
            jsonData.putOpt("prepareSupport", prepareSupport);
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
            RenameCapabilities other = (RenameCapabilities) obj;
            if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
                return false;
            }
            if (!Objects.equals(this.getPrepareSupport(), other.getPrepareSupport())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            if (this.getDynamicRegistration() != null) {
                hash = 29 * hash + Boolean.hashCode(this.getDynamicRegistration());
            }
            if (this.getPrepareSupport() != null) {
                hash = 29 * hash + Boolean.hashCode(this.getPrepareSupport());
            }
            return hash;
        }
    }

    public static class PublishDiagnosticsCapabilities {

        final JSONObject jsonData;

        PublishDiagnosticsCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Whether the clients accepts diagnostics with related information.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getRelatedInformation() {
            return jsonData.has("relatedInformation") ? jsonData.getBoolean("relatedInformation") : null;
        }

        public PublishDiagnosticsCapabilities setRelatedInformation(Boolean relatedInformation) {
            jsonData.putOpt("relatedInformation", relatedInformation);
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
            PublishDiagnosticsCapabilities other = (PublishDiagnosticsCapabilities) obj;
            if (!Objects.equals(this.getRelatedInformation(), other.getRelatedInformation())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            if (this.getRelatedInformation() != null) {
                hash = 17 * hash + Boolean.hashCode(this.getRelatedInformation());
            }
            return hash;
        }
    }

    public static class FoldingRangeCapabilities {

        final JSONObject jsonData;

        FoldingRangeCapabilities(JSONObject jsonData) {
            this.jsonData = jsonData;
        }

        /**
         * Whether implementation supports dynamic registration for folding range providers. If this
         * is set to `true` the client supports the new `(FoldingRangeProviderOptions &
         * TextDocumentRegistrationOptions & StaticRegistrationOptions)` return value for the
         * corresponding server capability as well.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getDynamicRegistration() {
            return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
        }

        public FoldingRangeCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
            jsonData.putOpt("dynamicRegistration", dynamicRegistration);
            return this;
        }

        /**
         * The maximum number of folding ranges that the client prefers to receive per document. The
         * value serves as a hint, servers are free to follow the limit.
         */
        public Integer getRangeLimit() {
            return jsonData.has("rangeLimit") ? jsonData.getInt("rangeLimit") : null;
        }

        public FoldingRangeCapabilities setRangeLimit(Integer rangeLimit) {
            jsonData.putOpt("rangeLimit", rangeLimit);
            return this;
        }

        /**
         * If set, the client signals that it only supports folding complete lines. If set, client
         * will ignore specified `startCharacter` and `endCharacter` properties in a FoldingRange.
         */
        @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
        public Boolean getLineFoldingOnly() {
            return jsonData.has("lineFoldingOnly") ? jsonData.getBoolean("lineFoldingOnly") : null;
        }

        public FoldingRangeCapabilities setLineFoldingOnly(Boolean lineFoldingOnly) {
            jsonData.putOpt("lineFoldingOnly", lineFoldingOnly);
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
            FoldingRangeCapabilities other = (FoldingRangeCapabilities) obj;
            if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
                return false;
            }
            if (!Objects.equals(this.getRangeLimit(), other.getRangeLimit())) {
                return false;
            }
            if (!Objects.equals(this.getLineFoldingOnly(), other.getLineFoldingOnly())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            if (this.getDynamicRegistration() != null) {
                hash = 29 * hash + Boolean.hashCode(this.getDynamicRegistration());
            }
            if (this.getRangeLimit() != null) {
                hash = 29 * hash + Integer.hashCode(this.getRangeLimit());
            }
            if (this.getLineFoldingOnly() != null) {
                hash = 29 * hash + Boolean.hashCode(this.getLineFoldingOnly());
            }
            return hash;
        }
    }
}
