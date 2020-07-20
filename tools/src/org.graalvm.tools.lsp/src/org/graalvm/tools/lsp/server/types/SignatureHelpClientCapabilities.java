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
 * Client Capabilities for a [SignatureHelpRequest](#SignatureHelpRequest).
 */
public class SignatureHelpClientCapabilities extends JSONBase {

    SignatureHelpClientCapabilities(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Whether signature help supports dynamic registration.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getDynamicRegistration() {
        return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
    }

    public SignatureHelpClientCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
        jsonData.putOpt("dynamicRegistration", dynamicRegistration);
        return this;
    }

    /**
     * The client supports the following `SignatureInformation` specific properties.
     */
    public SignatureInformationCapabilities getSignatureInformation() {
        return jsonData.has("signatureInformation") ? new SignatureInformationCapabilities(jsonData.optJSONObject("signatureInformation")) : null;
    }

    public SignatureHelpClientCapabilities setSignatureInformation(SignatureInformationCapabilities signatureInformation) {
        jsonData.putOpt("signatureInformation", signatureInformation != null ? signatureInformation.jsonData : null);
        return this;
    }

    /**
     * The client supports to send additional context information for a `textDocument/signatureHelp`
     * request. A client that opts into contextSupport will also support the `retriggerCharacters`
     * on `SignatureHelpOptions`.
     *
     * @since 3.15.0
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getContextSupport() {
        return jsonData.has("contextSupport") ? jsonData.getBoolean("contextSupport") : null;
    }

    public SignatureHelpClientCapabilities setContextSupport(Boolean contextSupport) {
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
        SignatureHelpClientCapabilities other = (SignatureHelpClientCapabilities) obj;
        if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
            return false;
        }
        if (!Objects.equals(this.getSignatureInformation(), other.getSignatureInformation())) {
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
            hash = 23 * hash + Boolean.hashCode(this.getDynamicRegistration());
        }
        if (this.getSignatureInformation() != null) {
            hash = 23 * hash + Objects.hashCode(this.getSignatureInformation());
        }
        if (this.getContextSupport() != null) {
            hash = 23 * hash + Boolean.hashCode(this.getContextSupport());
        }
        return hash;
    }

    public static SignatureHelpClientCapabilities create() {
        final JSONObject json = new JSONObject();
        return new SignatureHelpClientCapabilities(json);
    }

    public static class SignatureInformationCapabilities extends JSONBase {

        SignatureInformationCapabilities(JSONObject jsonData) {
            super(jsonData);
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
            int hash = 5;
            if (this.getDocumentationFormat() != null) {
                hash = 67 * hash + Objects.hashCode(this.getDocumentationFormat());
            }
            if (this.getParameterInformation() != null) {
                hash = 67 * hash + Objects.hashCode(this.getParameterInformation());
            }
            return hash;
        }

        public static class ParameterInformationCapabilities extends JSONBase {

            ParameterInformationCapabilities(JSONObject jsonData) {
                super(jsonData);
            }

            /**
             * The client supports processing label offsets instead of a simple label string.
             *
             * @since 3.14.0
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
                    hash = 37 * hash + Boolean.hashCode(this.getLabelOffsetSupport());
                }
                return hash;
            }
        }
    }
}
