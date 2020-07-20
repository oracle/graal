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
 * Represents a diagnostic, such as a compiler error or warning. Diagnostic objects are only valid
 * in the scope of a resource.
 */
public class Diagnostic extends JSONBase {

    Diagnostic(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The range at which the message applies.
     */
    public Range getRange() {
        return new Range(jsonData.getJSONObject("range"));
    }

    public Diagnostic setRange(Range range) {
        jsonData.put("range", range.jsonData);
        return this;
    }

    /**
     * The diagnostic's severity. Can be omitted. If omitted it is up to the client to interpret
     * diagnostics as error, warning, info or hint.
     */
    public DiagnosticSeverity getSeverity() {
        return DiagnosticSeverity.get(jsonData.has("severity") ? jsonData.getInt("severity") : null);
    }

    public Diagnostic setSeverity(DiagnosticSeverity severity) {
        jsonData.putOpt("severity", severity != null ? severity.getIntValue() : null);
        return this;
    }

    /**
     * The diagnostic's code, which usually appear in the user interface.
     */
    public Object getCode() {
        return jsonData.opt("code");
    }

    public Diagnostic setCode(Object code) {
        jsonData.putOpt("code", code);
        return this;
    }

    /**
     * A human-readable string describing the source of this diagnostic, e.g. 'typescript' or 'super
     * lint'. It usually appears in the user interface.
     */
    public String getSource() {
        return jsonData.optString("source", null);
    }

    public Diagnostic setSource(String source) {
        jsonData.putOpt("source", source);
        return this;
    }

    /**
     * The diagnostic's message. It usually appears in the user interface
     */
    public String getMessage() {
        return jsonData.getString("message");
    }

    public Diagnostic setMessage(String message) {
        jsonData.put("message", message);
        return this;
    }

    /**
     * Additional metadata about the diagnostic.
     */
    public List<DiagnosticTag> getTags() {
        final JSONArray json = jsonData.optJSONArray("tags");
        if (json == null) {
            return null;
        }
        final List<DiagnosticTag> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(DiagnosticTag.get(json.getInt(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public Diagnostic setTags(List<DiagnosticTag> tags) {
        if (tags != null) {
            final JSONArray json = new JSONArray();
            for (DiagnosticTag diagnosticTag : tags) {
                json.put(diagnosticTag.getIntValue());
            }
            jsonData.put("tags", json);
        }
        return this;
    }

    /**
     * An array of related diagnostic information, e.g. when symbol-names within a scope collide all
     * definitions can be marked via this property.
     */
    public List<DiagnosticRelatedInformation> getRelatedInformation() {
        final JSONArray json = jsonData.optJSONArray("relatedInformation");
        if (json == null) {
            return null;
        }
        final List<DiagnosticRelatedInformation> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new DiagnosticRelatedInformation(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public Diagnostic setRelatedInformation(List<DiagnosticRelatedInformation> relatedInformation) {
        if (relatedInformation != null) {
            final JSONArray json = new JSONArray();
            for (DiagnosticRelatedInformation diagnosticRelatedInformation : relatedInformation) {
                json.put(diagnosticRelatedInformation.jsonData);
            }
            jsonData.put("relatedInformation", json);
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
        Diagnostic other = (Diagnostic) obj;
        if (!Objects.equals(this.getRange(), other.getRange())) {
            return false;
        }
        if (this.getSeverity() != other.getSeverity()) {
            return false;
        }
        if (!Objects.equals(this.getCode(), other.getCode())) {
            return false;
        }
        if (!Objects.equals(this.getSource(), other.getSource())) {
            return false;
        }
        if (!Objects.equals(this.getMessage(), other.getMessage())) {
            return false;
        }
        if (!Objects.equals(this.getTags(), other.getTags())) {
            return false;
        }
        if (!Objects.equals(this.getRelatedInformation(), other.getRelatedInformation())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + Objects.hashCode(this.getRange());
        if (this.getSeverity() != null) {
            hash = 83 * hash + Objects.hashCode(this.getSeverity());
        }
        if (this.getCode() != null) {
            hash = 83 * hash + Objects.hashCode(this.getCode());
        }
        if (this.getSource() != null) {
            hash = 83 * hash + Objects.hashCode(this.getSource());
        }
        hash = 83 * hash + Objects.hashCode(this.getMessage());
        if (this.getTags() != null) {
            hash = 83 * hash + Objects.hashCode(this.getTags());
        }
        if (this.getRelatedInformation() != null) {
            hash = 83 * hash + Objects.hashCode(this.getRelatedInformation());
        }
        return hash;
    }

    /**
     * Creates a new Diagnostic literal.
     */
    public static Diagnostic create(Range range, String message, DiagnosticSeverity severity, Object code, String source, List<DiagnosticRelatedInformation> relatedInformation) {
        final JSONObject json = new JSONObject();
        json.put("range", range.jsonData);
        json.putOpt("severity", severity != null ? severity.getIntValue() : null);
        json.putOpt("code", code);
        json.putOpt("source", source);
        json.put("message", message);
        if (relatedInformation != null) {
            JSONArray relatedInformationJsonArr = new JSONArray();
            for (DiagnosticRelatedInformation diagnosticRelatedInformation : relatedInformation) {
                relatedInformationJsonArr.put(diagnosticRelatedInformation.jsonData);
            }
            json.put("relatedInformation", relatedInformationJsonArr);
        }
        return new Diagnostic(json);
    }
}
