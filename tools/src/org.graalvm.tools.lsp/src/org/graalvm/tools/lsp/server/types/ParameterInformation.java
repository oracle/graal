/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * Represents a parameter of a callable-signature. A parameter can have a label and a doc-comment.
 */
public class ParameterInformation {

    final JSONObject jsonData;

    ParameterInformation(JSONObject jsonData) {
        this.jsonData = jsonData;
    }

    /**
     * The label of this parameter information.
     *
     * Either a string or inclusive start and exclusive end offsets within its containing [signature
     * label](#SignatureInformation.label). *Note*: A label of type string must be a substring of
     * its containing signature information's [label](#SignatureInformation.label).
     */
    public Object getLabel() {
        return jsonData.get("label");
    }

    public ParameterInformation setLabel(Object label) {
        jsonData.put("label", label);
        return this;
    }

    /**
     * The human-readable doc-comment of this signature. Will be shown in the UI but can be omitted.
     */
    public Object getDocumentation() {
        Object obj = jsonData.opt("documentation");
        if (obj instanceof JSONObject) {
            return new MarkupContent((JSONObject) obj);
        }
        return obj;
    }

    public ParameterInformation setDocumentation(Object documentation) {
        if (documentation instanceof MarkupContent) {
            jsonData.put("documentation", ((MarkupContent) documentation).jsonData);
        } else {
            jsonData.putOpt("documentation", documentation);
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
        ParameterInformation other = (ParameterInformation) obj;
        if (!Objects.equals(this.getLabel(), other.getLabel())) {
            return false;
        }
        if (!Objects.equals(this.getDocumentation(), other.getDocumentation())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 11 * hash + Objects.hashCode(this.getLabel());
        if (this.getDocumentation() != null) {
            hash = 11 * hash + Objects.hashCode(this.getDocumentation());
        }
        return hash;
    }

    /**
     * Creates a new parameter information literal.
     *
     * @param label A label string.
     * @param documentation A doc string.
     */
    public static ParameterInformation create(Object label, String documentation) {
        final JSONObject json = new JSONObject();
        json.put("label", label);
        json.putOpt("documentation", documentation);
        return new ParameterInformation(json);
    }
}
