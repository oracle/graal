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
 * The publish diagnostic client capabilities.
 */
public class PublishDiagnosticsClientCapabilities extends JSONBase {

    PublishDiagnosticsClientCapabilities(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Whether the clients accepts diagnostics with related information.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getRelatedInformation() {
        return jsonData.has("relatedInformation") ? jsonData.getBoolean("relatedInformation") : null;
    }

    public PublishDiagnosticsClientCapabilities setRelatedInformation(Boolean relatedInformation) {
        jsonData.putOpt("relatedInformation", relatedInformation);
        return this;
    }

    /**
     * Client supports the tag property to provide meta data about a diagnostic. Clients supporting
     * tags have to handle unknown tags gracefully.
     *
     * @since 3.15.0
     */
    public TagSupportCapabilities getTagSupport() {
        return jsonData.has("tagSupport") ? new TagSupportCapabilities(jsonData.optJSONObject("tagSupport")) : null;
    }

    public PublishDiagnosticsClientCapabilities setTagSupport(TagSupportCapabilities tagSupport) {
        jsonData.putOpt("tagSupport", tagSupport != null ? tagSupport.jsonData : null);
        return this;
    }

    /**
     * Whether the client interprets the version property of the `textDocument/publishDiagnostics`
     * notification`s parameter.
     *
     * @since 3.15.0
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getVersionSupport() {
        return jsonData.has("versionSupport") ? jsonData.getBoolean("versionSupport") : null;
    }

    public PublishDiagnosticsClientCapabilities setVersionSupport(Boolean versionSupport) {
        jsonData.putOpt("versionSupport", versionSupport);
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
        PublishDiagnosticsClientCapabilities other = (PublishDiagnosticsClientCapabilities) obj;
        if (!Objects.equals(this.getRelatedInformation(), other.getRelatedInformation())) {
            return false;
        }
        if (!Objects.equals(this.getTagSupport(), other.getTagSupport())) {
            return false;
        }
        if (!Objects.equals(this.getVersionSupport(), other.getVersionSupport())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 2;
        if (this.getRelatedInformation() != null) {
            hash = 29 * hash + Boolean.hashCode(this.getRelatedInformation());
        }
        if (this.getTagSupport() != null) {
            hash = 29 * hash + Objects.hashCode(this.getTagSupport());
        }
        if (this.getVersionSupport() != null) {
            hash = 29 * hash + Boolean.hashCode(this.getVersionSupport());
        }
        return hash;
    }

    public static PublishDiagnosticsClientCapabilities create() {
        final JSONObject json = new JSONObject();
        return new PublishDiagnosticsClientCapabilities(json);
    }

    public static class TagSupportCapabilities extends JSONBase {

        TagSupportCapabilities(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * The tags supported by the client.
         */
        public List<DiagnosticTag> getValueSet() {
            final JSONArray json = jsonData.getJSONArray("valueSet");
            final List<DiagnosticTag> list = new ArrayList<>(json.length());
            for (int i = 0; i < json.length(); i++) {
                list.add(DiagnosticTag.get(json.getInt(i)));
            }
            return Collections.unmodifiableList(list);
        }

        public TagSupportCapabilities setValueSet(List<DiagnosticTag> valueSet) {
            final JSONArray json = new JSONArray();
            for (DiagnosticTag diagnosticTag : valueSet) {
                json.put(diagnosticTag.getIntValue());
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
            hash = 73 * hash + Objects.hashCode(this.getValueSet());
            return hash;
        }
    }
}
