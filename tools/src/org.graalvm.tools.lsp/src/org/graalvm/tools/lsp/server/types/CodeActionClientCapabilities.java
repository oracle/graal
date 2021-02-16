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
 * The Client Capabilities of a [CodeActionRequest](#CodeActionRequest).
 */
public class CodeActionClientCapabilities extends JSONBase {

    CodeActionClientCapabilities(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Whether code action supports dynamic registration.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getDynamicRegistration() {
        return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
    }

    public CodeActionClientCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
        jsonData.putOpt("dynamicRegistration", dynamicRegistration);
        return this;
    }

    /**
     * The client support code action literals as a valid response of the `textDocument/codeAction`
     * request.
     *
     * @since 3.8.0
     */
    public CodeActionLiteralSupportCapabilities getCodeActionLiteralSupport() {
        return jsonData.has("codeActionLiteralSupport") ? new CodeActionLiteralSupportCapabilities(jsonData.optJSONObject("codeActionLiteralSupport")) : null;
    }

    public CodeActionClientCapabilities setCodeActionLiteralSupport(CodeActionLiteralSupportCapabilities codeActionLiteralSupport) {
        jsonData.putOpt("codeActionLiteralSupport", codeActionLiteralSupport != null ? codeActionLiteralSupport.jsonData : null);
        return this;
    }

    /**
     * Whether code action supports the `isPreferred` property.
     *
     * @since 3.15.0
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getIsPreferredSupport() {
        return jsonData.has("isPreferredSupport") ? jsonData.getBoolean("isPreferredSupport") : null;
    }

    public CodeActionClientCapabilities setIsPreferredSupport(Boolean isPreferredSupport) {
        jsonData.putOpt("isPreferredSupport", isPreferredSupport);
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
        CodeActionClientCapabilities other = (CodeActionClientCapabilities) obj;
        if (!Objects.equals(this.getDynamicRegistration(), other.getDynamicRegistration())) {
            return false;
        }
        if (!Objects.equals(this.getCodeActionLiteralSupport(), other.getCodeActionLiteralSupport())) {
            return false;
        }
        if (!Objects.equals(this.getIsPreferredSupport(), other.getIsPreferredSupport())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getDynamicRegistration() != null) {
            hash = 79 * hash + Boolean.hashCode(this.getDynamicRegistration());
        }
        if (this.getCodeActionLiteralSupport() != null) {
            hash = 79 * hash + Objects.hashCode(this.getCodeActionLiteralSupport());
        }
        if (this.getIsPreferredSupport() != null) {
            hash = 79 * hash + Boolean.hashCode(this.getIsPreferredSupport());
        }
        return hash;
    }

    public static CodeActionClientCapabilities create() {
        final JSONObject json = new JSONObject();
        return new CodeActionClientCapabilities(json);
    }

    public static class CodeActionLiteralSupportCapabilities extends JSONBase {

        CodeActionLiteralSupportCapabilities(JSONObject jsonData) {
            super(jsonData);
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
            int hash = 3;
            hash = 29 * hash + Objects.hashCode(this.getCodeActionKind());
            return hash;
        }

        public static class CodeActionKindCapabilities extends JSONBase {

            CodeActionKindCapabilities(JSONObject jsonData) {
                super(jsonData);
            }

            /**
             * The code action kind values the client supports. When this property exists the client
             * also guarantees that it will handle values outside its set gracefully and falls back
             * to a default value when unknown.
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
                hash = 59 * hash + Objects.hashCode(this.getValueSet());
                return hash;
            }
        }
    }
}
