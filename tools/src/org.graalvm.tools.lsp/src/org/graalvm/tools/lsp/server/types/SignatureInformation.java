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
 * Represents the signature of something callable. A signature can have a label, like a
 * function-name, a doc-comment, and a set of parameters.
 */
public class SignatureInformation extends JSONBase {

    SignatureInformation(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The label of this signature. Will be shown in the UI.
     */
    public String getLabel() {
        return jsonData.getString("label");
    }

    public SignatureInformation setLabel(String label) {
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

    public SignatureInformation setDocumentation(Object documentation) {
        if (documentation instanceof MarkupContent) {
            jsonData.put("documentation", ((MarkupContent) documentation).jsonData);
        } else {
            jsonData.put("documentation", documentation);
        }
        return this;
    }

    /**
     * The parameters of this signature.
     */
    public List<ParameterInformation> getParameters() {
        final JSONArray json = jsonData.optJSONArray("parameters");
        if (json == null) {
            return null;
        }
        final List<ParameterInformation> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new ParameterInformation(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public SignatureInformation setParameters(List<ParameterInformation> parameters) {
        if (parameters != null) {
            final JSONArray json = new JSONArray();
            for (ParameterInformation parameterInformation : parameters) {
                json.put(parameterInformation.jsonData);
            }
            jsonData.put("parameters", json);
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
        SignatureInformation other = (SignatureInformation) obj;
        if (!Objects.equals(this.getLabel(), other.getLabel())) {
            return false;
        }
        if (!Objects.equals(this.getDocumentation(), other.getDocumentation())) {
            return false;
        }
        if (!Objects.equals(this.getParameters(), other.getParameters())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 79 * hash + Objects.hashCode(this.getLabel());
        if (this.getDocumentation() != null) {
            hash = 79 * hash + Objects.hashCode(this.getDocumentation());
        }
        if (this.getParameters() != null) {
            hash = 79 * hash + Objects.hashCode(this.getParameters());
        }
        return hash;
    }

    public static SignatureInformation create(String label, String documentation, ParameterInformation... parameters) {
        final JSONObject json = new JSONObject();
        json.put("label", label);
        json.putOpt("documentation", documentation);
        if (parameters != null) {
            JSONArray parametersJsonArr = new JSONArray();
            for (ParameterInformation parameterInformation : parameters) {
                parametersJsonArr.put(parameterInformation.jsonData);
            }
            json.put("parameters", parametersJsonArr);
        }
        return new SignatureInformation(json);
    }
}
