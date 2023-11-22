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

import org.graalvm.shadowed.org.json.JSONArray;
import org.graalvm.shadowed.org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Client capabilities for a [WorkspaceSymbolRequest](#WorkspaceSymbolRequest).
 */
public class WorkspaceSymbolClientCapabilities extends JSONBase {

    WorkspaceSymbolClientCapabilities(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Symbol request supports dynamic registration.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getDynamicRegistration() {
        return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
    }

    public WorkspaceSymbolClientCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
        jsonData.putOpt("dynamicRegistration", dynamicRegistration);
        return this;
    }

    /**
     * Specific capabilities for the `SymbolKind` in the `workspace/symbol` request.
     */
    public SymbolKindCapabilities getSymbolKind() {
        return jsonData.has("symbolKind") ? new SymbolKindCapabilities(jsonData.optJSONObject("symbolKind")) : null;
    }

    public WorkspaceSymbolClientCapabilities setSymbolKind(SymbolKindCapabilities symbolKind) {
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
        WorkspaceSymbolClientCapabilities other = (WorkspaceSymbolClientCapabilities) obj;
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
        int hash = 2;
        if (this.getDynamicRegistration() != null) {
            hash = 83 * hash + Boolean.hashCode(this.getDynamicRegistration());
        }
        if (this.getSymbolKind() != null) {
            hash = 83 * hash + Objects.hashCode(this.getSymbolKind());
        }
        return hash;
    }

    public static WorkspaceSymbolClientCapabilities create() {
        final JSONObject json = new JSONObject();
        return new WorkspaceSymbolClientCapabilities(json);
    }

    public static class SymbolKindCapabilities extends JSONBase {

        SymbolKindCapabilities(JSONObject jsonData) {
            super(jsonData);
        }

        /**
         * The symbol kind values the client supports. When this property exists the client also
         * guarantees that it will handle values outside its set gracefully and falls back to a
         * default value when unknown.
         *
         * If this property is not present the client only supports the symbol kinds from `File` to
         * `Array` as defined in the initial version of the protocol.
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
            int hash = 2;
            if (this.getValueSet() != null) {
                hash = 11 * hash + Objects.hashCode(this.getValueSet());
            }
            return hash;
        }
    }
}
