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
 * Text document specific client capabilities.
 */
public class TextDocumentClientCapabilities extends JSONBase {

    TextDocumentClientCapabilities(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Defines which synchronization capabilities the client supports.
     */
    public TextDocumentSyncClientCapabilities getSynchronization() {
        return jsonData.has("synchronization") ? new TextDocumentSyncClientCapabilities(jsonData.optJSONObject("synchronization")) : null;
    }

    public TextDocumentClientCapabilities setSynchronization(TextDocumentSyncClientCapabilities synchronization) {
        jsonData.putOpt("synchronization", synchronization != null ? synchronization.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/completion`.
     */
    public CompletionClientCapabilities getCompletion() {
        return jsonData.has("completion") ? new CompletionClientCapabilities(jsonData.optJSONObject("completion")) : null;
    }

    public TextDocumentClientCapabilities setCompletion(CompletionClientCapabilities completion) {
        jsonData.putOpt("completion", completion != null ? completion.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/hover`.
     */
    public HoverClientCapabilities getHover() {
        return jsonData.has("hover") ? new HoverClientCapabilities(jsonData.optJSONObject("hover")) : null;
    }

    public TextDocumentClientCapabilities setHover(HoverClientCapabilities hover) {
        jsonData.putOpt("hover", hover != null ? hover.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/signatureHelp`.
     */
    public SignatureHelpClientCapabilities getSignatureHelp() {
        return jsonData.has("signatureHelp") ? new SignatureHelpClientCapabilities(jsonData.optJSONObject("signatureHelp")) : null;
    }

    public TextDocumentClientCapabilities setSignatureHelp(SignatureHelpClientCapabilities signatureHelp) {
        jsonData.putOpt("signatureHelp", signatureHelp != null ? signatureHelp.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/declaration`.
     *
     * @since 3.14.0
     */
    public DeclarationClientCapabilities getDeclaration() {
        return jsonData.has("declaration") ? new DeclarationClientCapabilities(jsonData.optJSONObject("declaration")) : null;
    }

    public TextDocumentClientCapabilities setDeclaration(DeclarationClientCapabilities declaration) {
        jsonData.putOpt("declaration", declaration != null ? declaration.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/definition`.
     */
    public DefinitionClientCapabilities getDefinition() {
        return jsonData.has("definition") ? new DefinitionClientCapabilities(jsonData.optJSONObject("definition")) : null;
    }

    public TextDocumentClientCapabilities setDefinition(DefinitionClientCapabilities definition) {
        jsonData.putOpt("definition", definition != null ? definition.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/typeDefinition`.
     *
     * @since 3.6.0
     */
    public TypeDefinitionClientCapabilities getTypeDefinition() {
        return jsonData.has("typeDefinition") ? new TypeDefinitionClientCapabilities(jsonData.optJSONObject("typeDefinition")) : null;
    }

    public TextDocumentClientCapabilities setTypeDefinition(TypeDefinitionClientCapabilities typeDefinition) {
        jsonData.putOpt("typeDefinition", typeDefinition != null ? typeDefinition.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/implementation`.
     *
     * @since 3.6.0
     */
    public ImplementationClientCapabilities getImplementation() {
        return jsonData.has("implementation") ? new ImplementationClientCapabilities(jsonData.optJSONObject("implementation")) : null;
    }

    public TextDocumentClientCapabilities setImplementation(ImplementationClientCapabilities implementation) {
        jsonData.putOpt("implementation", implementation != null ? implementation.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/references`.
     */
    public ReferenceClientCapabilities getReferences() {
        return jsonData.has("references") ? new ReferenceClientCapabilities(jsonData.optJSONObject("references")) : null;
    }

    public TextDocumentClientCapabilities setReferences(ReferenceClientCapabilities references) {
        jsonData.putOpt("references", references != null ? references.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/documentHighlight`.
     */
    public DocumentHighlightClientCapabilities getDocumentHighlight() {
        return jsonData.has("documentHighlight") ? new DocumentHighlightClientCapabilities(jsonData.optJSONObject("documentHighlight")) : null;
    }

    public TextDocumentClientCapabilities setDocumentHighlight(DocumentHighlightClientCapabilities documentHighlight) {
        jsonData.putOpt("documentHighlight", documentHighlight != null ? documentHighlight.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/documentSymbol`.
     */
    public DocumentSymbolClientCapabilities getDocumentSymbol() {
        return jsonData.has("documentSymbol") ? new DocumentSymbolClientCapabilities(jsonData.optJSONObject("documentSymbol")) : null;
    }

    public TextDocumentClientCapabilities setDocumentSymbol(DocumentSymbolClientCapabilities documentSymbol) {
        jsonData.putOpt("documentSymbol", documentSymbol != null ? documentSymbol.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/codeAction`.
     */
    public CodeActionClientCapabilities getCodeAction() {
        return jsonData.has("codeAction") ? new CodeActionClientCapabilities(jsonData.optJSONObject("codeAction")) : null;
    }

    public TextDocumentClientCapabilities setCodeAction(CodeActionClientCapabilities codeAction) {
        jsonData.putOpt("codeAction", codeAction != null ? codeAction.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/codeLens`.
     */
    public CodeLensClientCapabilities getCodeLens() {
        return jsonData.has("codeLens") ? new CodeLensClientCapabilities(jsonData.optJSONObject("codeLens")) : null;
    }

    public TextDocumentClientCapabilities setCodeLens(CodeLensClientCapabilities codeLens) {
        jsonData.putOpt("codeLens", codeLens != null ? codeLens.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/documentLink`.
     */
    public DocumentLinkClientCapabilities getDocumentLink() {
        return jsonData.has("documentLink") ? new DocumentLinkClientCapabilities(jsonData.optJSONObject("documentLink")) : null;
    }

    public TextDocumentClientCapabilities setDocumentLink(DocumentLinkClientCapabilities documentLink) {
        jsonData.putOpt("documentLink", documentLink != null ? documentLink.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/documentColor`.
     */
    public DocumentColorClientCapabilities getColorProvider() {
        return jsonData.has("colorProvider") ? new DocumentColorClientCapabilities(jsonData.optJSONObject("colorProvider")) : null;
    }

    public TextDocumentClientCapabilities setColorProvider(DocumentColorClientCapabilities colorProvider) {
        jsonData.putOpt("colorProvider", colorProvider != null ? colorProvider.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/formatting`.
     */
    public DocumentFormattingClientCapabilities getFormatting() {
        return jsonData.has("formatting") ? new DocumentFormattingClientCapabilities(jsonData.optJSONObject("formatting")) : null;
    }

    public TextDocumentClientCapabilities setFormatting(DocumentFormattingClientCapabilities formatting) {
        jsonData.putOpt("formatting", formatting != null ? formatting.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/rangeFormatting`.
     */
    public DocumentRangeFormattingClientCapabilities getRangeFormatting() {
        return jsonData.has("rangeFormatting") ? new DocumentRangeFormattingClientCapabilities(jsonData.optJSONObject("rangeFormatting")) : null;
    }

    public TextDocumentClientCapabilities setRangeFormatting(DocumentRangeFormattingClientCapabilities rangeFormatting) {
        jsonData.putOpt("rangeFormatting", rangeFormatting != null ? rangeFormatting.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/onTypeFormatting`.
     */
    public DocumentOnTypeFormattingClientCapabilities getOnTypeFormatting() {
        return jsonData.has("onTypeFormatting") ? new DocumentOnTypeFormattingClientCapabilities(jsonData.optJSONObject("onTypeFormatting")) : null;
    }

    public TextDocumentClientCapabilities setOnTypeFormatting(DocumentOnTypeFormattingClientCapabilities onTypeFormatting) {
        jsonData.putOpt("onTypeFormatting", onTypeFormatting != null ? onTypeFormatting.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to the `textDocument/rename`.
     */
    public RenameClientCapabilities getRename() {
        return jsonData.has("rename") ? new RenameClientCapabilities(jsonData.optJSONObject("rename")) : null;
    }

    public TextDocumentClientCapabilities setRename(RenameClientCapabilities rename) {
        jsonData.putOpt("rename", rename != null ? rename.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to `textDocument/foldingRange` requests.
     *
     * @since 3.10.0
     */
    public FoldingRangeClientCapabilities getFoldingRange() {
        return jsonData.has("foldingRange") ? new FoldingRangeClientCapabilities(jsonData.optJSONObject("foldingRange")) : null;
    }

    public TextDocumentClientCapabilities setFoldingRange(FoldingRangeClientCapabilities foldingRange) {
        jsonData.putOpt("foldingRange", foldingRange != null ? foldingRange.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to `textDocument/selectionRange` requests.
     *
     * @since 3.15.0
     */
    public SelectionRangeClientCapabilities getSelectionRange() {
        return jsonData.has("selectionRange") ? new SelectionRangeClientCapabilities(jsonData.optJSONObject("selectionRange")) : null;
    }

    public TextDocumentClientCapabilities setSelectionRange(SelectionRangeClientCapabilities selectionRange) {
        jsonData.putOpt("selectionRange", selectionRange != null ? selectionRange.jsonData : null);
        return this;
    }

    /**
     * Capabilities specific to `textDocument/publishDiagnostics`.
     */
    public PublishDiagnosticsClientCapabilities getPublishDiagnostics() {
        return jsonData.has("publishDiagnostics") ? new PublishDiagnosticsClientCapabilities(jsonData.optJSONObject("publishDiagnostics")) : null;
    }

    public TextDocumentClientCapabilities setPublishDiagnostics(PublishDiagnosticsClientCapabilities publishDiagnostics) {
        jsonData.putOpt("publishDiagnostics", publishDiagnostics != null ? publishDiagnostics.jsonData : null);
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
        if (!Objects.equals(this.getReferences(), other.getReferences())) {
            return false;
        }
        if (!Objects.equals(this.getDocumentHighlight(), other.getDocumentHighlight())) {
            return false;
        }
        if (!Objects.equals(this.getDocumentSymbol(), other.getDocumentSymbol())) {
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
        if (!Objects.equals(this.getFormatting(), other.getFormatting())) {
            return false;
        }
        if (!Objects.equals(this.getRangeFormatting(), other.getRangeFormatting())) {
            return false;
        }
        if (!Objects.equals(this.getOnTypeFormatting(), other.getOnTypeFormatting())) {
            return false;
        }
        if (!Objects.equals(this.getRename(), other.getRename())) {
            return false;
        }
        if (!Objects.equals(this.getFoldingRange(), other.getFoldingRange())) {
            return false;
        }
        if (!Objects.equals(this.getSelectionRange(), other.getSelectionRange())) {
            return false;
        }
        if (!Objects.equals(this.getPublishDiagnostics(), other.getPublishDiagnostics())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getSynchronization() != null) {
            hash = 43 * hash + Objects.hashCode(this.getSynchronization());
        }
        if (this.getCompletion() != null) {
            hash = 43 * hash + Objects.hashCode(this.getCompletion());
        }
        if (this.getHover() != null) {
            hash = 43 * hash + Objects.hashCode(this.getHover());
        }
        if (this.getSignatureHelp() != null) {
            hash = 43 * hash + Objects.hashCode(this.getSignatureHelp());
        }
        if (this.getDeclaration() != null) {
            hash = 43 * hash + Objects.hashCode(this.getDeclaration());
        }
        if (this.getDefinition() != null) {
            hash = 43 * hash + Objects.hashCode(this.getDefinition());
        }
        if (this.getTypeDefinition() != null) {
            hash = 43 * hash + Objects.hashCode(this.getTypeDefinition());
        }
        if (this.getImplementation() != null) {
            hash = 43 * hash + Objects.hashCode(this.getImplementation());
        }
        if (this.getReferences() != null) {
            hash = 43 * hash + Objects.hashCode(this.getReferences());
        }
        if (this.getDocumentHighlight() != null) {
            hash = 43 * hash + Objects.hashCode(this.getDocumentHighlight());
        }
        if (this.getDocumentSymbol() != null) {
            hash = 43 * hash + Objects.hashCode(this.getDocumentSymbol());
        }
        if (this.getCodeAction() != null) {
            hash = 43 * hash + Objects.hashCode(this.getCodeAction());
        }
        if (this.getCodeLens() != null) {
            hash = 43 * hash + Objects.hashCode(this.getCodeLens());
        }
        if (this.getDocumentLink() != null) {
            hash = 43 * hash + Objects.hashCode(this.getDocumentLink());
        }
        if (this.getColorProvider() != null) {
            hash = 43 * hash + Objects.hashCode(this.getColorProvider());
        }
        if (this.getFormatting() != null) {
            hash = 43 * hash + Objects.hashCode(this.getFormatting());
        }
        if (this.getRangeFormatting() != null) {
            hash = 43 * hash + Objects.hashCode(this.getRangeFormatting());
        }
        if (this.getOnTypeFormatting() != null) {
            hash = 43 * hash + Objects.hashCode(this.getOnTypeFormatting());
        }
        if (this.getRename() != null) {
            hash = 43 * hash + Objects.hashCode(this.getRename());
        }
        if (this.getFoldingRange() != null) {
            hash = 43 * hash + Objects.hashCode(this.getFoldingRange());
        }
        if (this.getSelectionRange() != null) {
            hash = 43 * hash + Objects.hashCode(this.getSelectionRange());
        }
        if (this.getPublishDiagnostics() != null) {
            hash = 43 * hash + Objects.hashCode(this.getPublishDiagnostics());
        }
        return hash;
    }

    public static TextDocumentClientCapabilities create() {
        final JSONObject json = new JSONObject();
        return new TextDocumentClientCapabilities(json);
    }
}
