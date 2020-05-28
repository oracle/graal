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

public class FoldingRangeClientCapabilities extends JSONBase {

    FoldingRangeClientCapabilities(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Whether implementation supports dynamic registration for folding range providers. If this is
     * set to `true` the client supports the new `FoldingRangeRegistrationOptions` return value for
     * the corresponding server capability as well.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getDynamicRegistration() {
        return jsonData.has("dynamicRegistration") ? jsonData.getBoolean("dynamicRegistration") : null;
    }

    public FoldingRangeClientCapabilities setDynamicRegistration(Boolean dynamicRegistration) {
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

    public FoldingRangeClientCapabilities setRangeLimit(Integer rangeLimit) {
        jsonData.putOpt("rangeLimit", rangeLimit);
        return this;
    }

    /**
     * If set, the client signals that it only supports folding complete lines. If set, client will
     * ignore specified `startCharacter` and `endCharacter` properties in a FoldingRange.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getLineFoldingOnly() {
        return jsonData.has("lineFoldingOnly") ? jsonData.getBoolean("lineFoldingOnly") : null;
    }

    public FoldingRangeClientCapabilities setLineFoldingOnly(Boolean lineFoldingOnly) {
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
        FoldingRangeClientCapabilities other = (FoldingRangeClientCapabilities) obj;
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
        int hash = 2;
        if (this.getDynamicRegistration() != null) {
            hash = 83 * hash + Boolean.hashCode(this.getDynamicRegistration());
        }
        if (this.getRangeLimit() != null) {
            hash = 83 * hash + Integer.hashCode(this.getRangeLimit());
        }
        if (this.getLineFoldingOnly() != null) {
            hash = 83 * hash + Boolean.hashCode(this.getLineFoldingOnly());
        }
        return hash;
    }

    public static FoldingRangeClientCapabilities create() {
        final JSONObject json = new JSONObject();
        return new FoldingRangeClientCapabilities(json);
    }
}
