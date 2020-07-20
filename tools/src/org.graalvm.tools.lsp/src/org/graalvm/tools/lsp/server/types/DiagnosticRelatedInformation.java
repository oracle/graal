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
 * Represents a related message and source code location for a diagnostic. This should be used to
 * point to code locations that cause or related to a diagnostics, e.g when duplicating a symbol in
 * a scope.
 */
public class DiagnosticRelatedInformation extends JSONBase {

    DiagnosticRelatedInformation(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The location of this related diagnostic information.
     */
    public Location getLocation() {
        return new Location(jsonData.getJSONObject("location"));
    }

    public DiagnosticRelatedInformation setLocation(Location location) {
        jsonData.put("location", location.jsonData);
        return this;
    }

    /**
     * The message of this related diagnostic information.
     */
    public String getMessage() {
        return jsonData.getString("message");
    }

    public DiagnosticRelatedInformation setMessage(String message) {
        jsonData.put("message", message);
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
        DiagnosticRelatedInformation other = (DiagnosticRelatedInformation) obj;
        if (!Objects.equals(this.getLocation(), other.getLocation())) {
            return false;
        }
        if (!Objects.equals(this.getMessage(), other.getMessage())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 2;
        hash = 59 * hash + Objects.hashCode(this.getLocation());
        hash = 59 * hash + Objects.hashCode(this.getMessage());
        return hash;
    }

    /**
     * Creates a new DiagnosticRelatedInformation literal.
     */
    public static DiagnosticRelatedInformation create(Location location, String message) {
        final JSONObject json = new JSONObject();
        json.put("location", location.jsonData);
        json.put("message", message);
        return new DiagnosticRelatedInformation(json);
    }
}
