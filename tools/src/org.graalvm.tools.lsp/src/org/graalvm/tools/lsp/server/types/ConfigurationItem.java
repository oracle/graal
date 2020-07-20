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

public class ConfigurationItem extends JSONBase {

    ConfigurationItem(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The scope to get the configuration section for.
     */
    public String getScopeUri() {
        return jsonData.optString("scopeUri", null);
    }

    public ConfigurationItem setScopeUri(String scopeUri) {
        jsonData.putOpt("scopeUri", scopeUri);
        return this;
    }

    /**
     * The configuration section asked for.
     */
    public String getSection() {
        return jsonData.optString("section", null);
    }

    public ConfigurationItem setSection(String section) {
        jsonData.putOpt("section", section);
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
        ConfigurationItem other = (ConfigurationItem) obj;
        if (!Objects.equals(this.getScopeUri(), other.getScopeUri())) {
            return false;
        }
        if (!Objects.equals(this.getSection(), other.getSection())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getScopeUri() != null) {
            hash = 37 * hash + Objects.hashCode(this.getScopeUri());
        }
        if (this.getSection() != null) {
            hash = 37 * hash + Objects.hashCode(this.getSection());
        }
        return hash;
    }

    public static ConfigurationItem create() {
        final JSONObject json = new JSONObject();
        return new ConfigurationItem(json);
    }
}
