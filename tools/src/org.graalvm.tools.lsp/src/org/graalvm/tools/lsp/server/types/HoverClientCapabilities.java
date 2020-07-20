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

public class HoverClientCapabilities extends JSONBase {

    HoverClientCapabilities(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Whether hover supports dynamic registration.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getDynamicRegistration() {
        return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
    }

    public HoverClientCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
        jsonData.putOpt("dynamicRegistration", dynamicRegistration);
        return this;
    }

    /**
     * Client supports the follow content formats for the content property. The order describes the
     * preferred format of the client.
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

    public HoverClientCapabilities setContentFormat(List<MarkupKind> contentFormat) {
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
        HoverClientCapabilities other = (HoverClientCapabilities) obj;
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
        int hash = 7;
        if (this.getDynamicRegistration() != null) {
            hash = 53 * hash + Boolean.hashCode(this.getDynamicRegistration());
        }
        if (this.getContentFormat() != null) {
            hash = 53 * hash + Objects.hashCode(this.getContentFormat());
        }
        return hash;
    }

    public static HoverClientCapabilities create() {
        final JSONObject json = new JSONObject();
        return new HoverClientCapabilities(json);
    }
}
