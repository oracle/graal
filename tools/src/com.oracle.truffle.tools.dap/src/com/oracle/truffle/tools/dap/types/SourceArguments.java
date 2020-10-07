/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.dap.types;

import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.Objects;

/**
 * Arguments for 'source' request.
 */
public class SourceArguments extends JSONBase {

    SourceArguments(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * Specifies the source content to load. Either source.path or source.sourceReference must be
     * specified.
     */
    public Source getSource() {
        return jsonData.has("source") ? new Source(jsonData.optJSONObject("source")) : null;
    }

    public SourceArguments setSource(Source source) {
        jsonData.putOpt("source", source != null ? source.jsonData : null);
        return this;
    }

    /**
     * The reference to the source. This is the same as source.sourceReference. This is provided for
     * backward compatibility since old backends do not understand the 'source' attribute.
     */
    public int getSourceReference() {
        return jsonData.getInt("sourceReference");
    }

    public SourceArguments setSourceReference(int sourceReference) {
        jsonData.put("sourceReference", sourceReference);
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
        SourceArguments other = (SourceArguments) obj;
        if (!Objects.equals(this.getSource(), other.getSource())) {
            return false;
        }
        if (this.getSourceReference() != other.getSourceReference()) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getSource() != null) {
            hash = 23 * hash + Objects.hashCode(this.getSource());
        }
        hash = 23 * hash + Integer.hashCode(this.getSourceReference());
        return hash;
    }

    public static SourceArguments create(Integer sourceReference) {
        final JSONObject json = new JSONObject();
        json.put("sourceReference", sourceReference);
        return new SourceArguments(json);
    }
}
