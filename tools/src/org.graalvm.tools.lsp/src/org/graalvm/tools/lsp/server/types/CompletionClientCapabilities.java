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
 * Completion client capabilities.
 */
public class CompletionClientCapabilities extends JSONBase {

    CompletionClientCapabilities(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Whether completion supports dynamic registration.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getDynamicRegistration() {
        return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
    }

    public CompletionClientCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
        jsonData.putOpt("dynamicRegistration", dynamicRegistration);
        return this;
    }

    /**
     * The client supports the following `CompletionItem` specific capabilities.
     */
    public CompletionItemCapabilities getCompletionItem() {
        return jsonData.has("completionItem") ? new CompletionItemCapabilities(jsonData.optJSONObject("completionItem")) : null;
    }

    public CompletionClientCapabilities setCompletionItem(CompletionItemCapabilities completionItem) {
        jsonData.putOpt("completionItem", completionItem != null ? completionItem.jsonData : null);
        return this;
    }

    public CompletionItemKindCapabilities getCompletionItemKind() {
        return jsonData.has("completionItemKind") ? new CompletionItemKindCapabilities(jsonData.optJSONObject("completionItemKind")) : null;
    }

    public CompletionClientCapabilities setCompletionItemKind(CompletionItemKindCapabilities completionItemKind) {
        jsonData.putOpt("completionItemKind", completionItemKind != null ? completionItemKind.jsonData : null);
        return this;
    }

    /**
     * The client supports to send additional context information for a `textDocument/completion`
     * requestion.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getContextSupport() {
        return jsonData.has("contextSupport") ? jsonData.getBoolean("contextSupport") : null;
    }

    public CompletionClientCapabilities setContextSupport(Boolean contextSupport) {
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
        CompletionClientCapabilities other = (CompletionClientCapabilities) obj;
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
        int hash = 7;
        if (this.getDynamicRegistration() != null) {
            hash = 47 * hash + Boolean.hashCode(this.getDynamicRegistration());
        }
        if (this.getCompletionItem() != null) {
            hash = 47 * hash + Objects.hashCode(this.getCompletionItem());
        }
        if (this.getCompletionItemKind() != null) {
            hash = 47 * hash + Objects.hashCode(this.getCompletionItemKind());
        }
        if (this.getContextSupport() != null) {
            hash = 47 * hash + Boolean.hashCode(this.getContextSupport());
        }
        return hash;
    }

    public static CompletionClientCapabilities create() {
        final JSONObject json = new JSONObject();
        return new CompletionClientCapabilities(json);
    }

    public static class CompletionItemCapabilities extends JSONBase {

        CompletionItemCapabilities(JSONObject jsonData) {
            super(jsonData);
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

        /**
         * Client supports the tag property on a completion item. Clients supporting tags have to
         * handle unknown tags gracefully. Clients especially need to preserve unknown tags when
         * sending a completion item back to the server in a resolve call.
         *
         * @since 3.15.0
         */
        public TagSupportCapabilities getTagSupport() {
            return jsonData.has("tagSupport") ? new TagSupportCapabilities(jsonData.optJSONObject("tagSupport")) : null;
        }

        public CompletionItemCapabilities setTagSupport(TagSupportCapabilities tagSupport) {
            jsonData.putOpt("tagSupport", tagSupport != null ? tagSupport.jsonData : null);
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
            if (!Objects.equals(this.getTagSupport(), other.getTagSupport())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            if (this.getSnippetSupport() != null) {
                hash = 71 * hash + Boolean.hashCode(this.getSnippetSupport());
            }
            if (this.getCommitCharactersSupport() != null) {
                hash = 71 * hash + Boolean.hashCode(this.getCommitCharactersSupport());
            }
            if (this.getDocumentationFormat() != null) {
                hash = 71 * hash + Objects.hashCode(this.getDocumentationFormat());
            }
            if (this.getDeprecatedSupport() != null) {
                hash = 71 * hash + Boolean.hashCode(this.getDeprecatedSupport());
            }
            if (this.getPreselectSupport() != null) {
                hash = 71 * hash + Boolean.hashCode(this.getPreselectSupport());
            }
            if (this.getTagSupport() != null) {
                hash = 71 * hash + Objects.hashCode(this.getTagSupport());
            }
            return hash;
        }

        public static class TagSupportCapabilities extends JSONBase {

            TagSupportCapabilities(JSONObject jsonData) {
                super(jsonData);
            }

            /**
             * The tags supported by the client.
             */
            public List<CompletionItemTag> getValueSet() {
                final JSONArray json = jsonData.getJSONArray("valueSet");
                final List<CompletionItemTag> list = new ArrayList<>(json.length());
                for (int i = 0; i < json.length(); i++) {
                    list.add(CompletionItemTag.get(json.getInt(i)));
                }
                return Collections.unmodifiableList(list);
            }

            public TagSupportCapabilities setValueSet(List<CompletionItemTag> valueSet) {
                final JSONArray json = new JSONArray();
                for (CompletionItemTag completionItemTag : valueSet) {
                    json.put(completionItemTag.getIntValue());
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
                TagSupportCapabilities other = (TagSupportCapabilities) obj;
                if (!Objects.equals(this.getValueSet(), other.getValueSet())) {
                    return false;
                }
                return true;
            }

            @Override
            public int hashCode() {
                int hash = 7;
                hash = 43 * hash + Objects.hashCode(this.getValueSet());
                return hash;
            }
        }
    }

    public static class CompletionItemKindCapabilities extends JSONBase {

        CompletionItemKindCapabilities(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * The completion item kind values the client supports. When this property exists the client
         * also guarantees that it will handle values outside its set gracefully and falls back to a
         * default value when unknown.
         *
         * If this property is not present the client only supports the completion items kinds from
         * `Text` to `Reference` as defined in the initial version of the protocol.
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
                hash = 53 * hash + Objects.hashCode(this.getValueSet());
            }
            return hash;
        }
    }
}
