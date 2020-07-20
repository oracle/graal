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

public class FoldingRangeRegistrationOptions extends FoldingRangeOptions {

    FoldingRangeRegistrationOptions(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * A document selector to identify the scope of the registration. If set to null the document
     * selector provided on the client side will be used.
     */
    public List<Object> getDocumentSelector() {
        final JSONArray json = jsonData.optJSONArray("documentSelector");
        if (json == null) {
            return null;
        }
        final List<Object> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            Object obj = json.get(i);
            if (obj instanceof JSONObject) {
                list.add(new DocumentFilter((JSONObject) obj));
            }
            list.add(obj);
        }
        return Collections.unmodifiableList(list);
    }

    public FoldingRangeRegistrationOptions setDocumentSelector(List<Object> documentSelector) {
        if (documentSelector != null) {
            final JSONArray json = new JSONArray();
            for (Object object : documentSelector) {
                if (object instanceof DocumentFilter) {
                    json.put(((DocumentFilter) object).jsonData);
                } else {
                    json.put(object);
                }
            }
            jsonData.put("documentSelector", json);
        } else {
            jsonData.put("documentSelector", JSONObject.NULL);
        }
        return this;
    }

    /**
     * The id used to register the request. The id can be used to deregister the request again. See
     * also Registration#id.
     */
    public String getId() {
        return jsonData.optString("id", null);
    }

    public FoldingRangeRegistrationOptions setId(String id) {
        jsonData.putOpt("id", id);
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
        FoldingRangeRegistrationOptions other = (FoldingRangeRegistrationOptions) obj;
        if (!Objects.equals(this.getDocumentSelector(), other.getDocumentSelector())) {
            return false;
        }
        if (!Objects.equals(this.getId(), other.getId())) {
            return false;
        }
        if (!Objects.equals(this.getWorkDoneProgress(), other.getWorkDoneProgress())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        if (this.getDocumentSelector() != null) {
            hash = 53 * hash + Objects.hashCode(this.getDocumentSelector());
        }
        if (this.getId() != null) {
            hash = 53 * hash + Objects.hashCode(this.getId());
        }
        if (this.getWorkDoneProgress() != null) {
            hash = 53 * hash + Boolean.hashCode(this.getWorkDoneProgress());
        }
        return hash;
    }

    public static FoldingRangeRegistrationOptions create(List<Object> documentSelector) {
        final JSONObject json = new JSONObject();
        if (documentSelector != null) {
            JSONArray documentSelectorJsonArr = new JSONArray();
            for (Object object : documentSelector) {
                if (object instanceof DocumentFilter) {
                    documentSelectorJsonArr.put(((DocumentFilter) object).jsonData);
                } else {
                    documentSelectorJsonArr.put(object);
                }
            }
            json.put("documentSelector", documentSelectorJsonArr);
        } else {
            json.put("documentSelector", JSONObject.NULL);
        }
        return new FoldingRangeRegistrationOptions(json);
    }
}
