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
 * The publish diagnostic notification's parameters.
 */
public class PublishDiagnosticsParams extends JSONBase {

    PublishDiagnosticsParams(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The URI for which diagnostic information is reported.
     */
    public String getUri() {
        return jsonData.getString("uri");
    }

    public PublishDiagnosticsParams setUri(String uri) {
        jsonData.put("uri", uri);
        return this;
    }

    /**
     * Optional the version number of the document the diagnostics are published for.
     *
     * @since 3.15.0
     */
    public Integer getVersion() {
        return jsonData.has("version") ? jsonData.getInt("version") : null;
    }

    public PublishDiagnosticsParams setVersion(Integer version) {
        jsonData.putOpt("version", version);
        return this;
    }

    /**
     * An array of diagnostic information items.
     */
    public List<Diagnostic> getDiagnostics() {
        final JSONArray json = jsonData.getJSONArray("diagnostics");
        final List<Diagnostic> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new Diagnostic(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public PublishDiagnosticsParams setDiagnostics(List<Diagnostic> diagnostics) {
        final JSONArray json = new JSONArray();
        for (Diagnostic diagnostic : diagnostics) {
            json.put(diagnostic.jsonData);
        }
        jsonData.put("diagnostics", json);
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
        PublishDiagnosticsParams other = (PublishDiagnosticsParams) obj;
        if (!Objects.equals(this.getUri(), other.getUri())) {
            return false;
        }
        if (!Objects.equals(this.getVersion(), other.getVersion())) {
            return false;
        }
        if (!Objects.equals(this.getDiagnostics(), other.getDiagnostics())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 2;
        hash = 53 * hash + Objects.hashCode(this.getUri());
        if (this.getVersion() != null) {
            hash = 53 * hash + Integer.hashCode(this.getVersion());
        }
        hash = 53 * hash + Objects.hashCode(this.getDiagnostics());
        return hash;
    }

    public static PublishDiagnosticsParams create(String uri, List<Diagnostic> diagnostics) {
        final JSONObject json = new JSONObject();
        json.put("uri", uri);
        JSONArray diagnosticsJsonArr = new JSONArray();
        for (Diagnostic diagnostic : diagnostics) {
            diagnosticsJsonArr.put(diagnostic.jsonData);
        }
        json.put("diagnostics", diagnosticsJsonArr);
        return new PublishDiagnosticsParams(json);
    }
}
