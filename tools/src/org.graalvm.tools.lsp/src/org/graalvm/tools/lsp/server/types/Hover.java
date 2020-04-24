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
 * The result of a hover request.
 */
public class Hover extends JSONBase {

    Hover(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The hover's content.
     */
    @SuppressWarnings("deprecation")
    public Object getContents() {
        Object obj = jsonData.get("contents");
        if (obj instanceof JSONArray) {
            final List<Object> list = new ArrayList<>(((JSONArray) obj).length());
            for (int i = 0; i < ((JSONArray) obj).length(); i++) {
                Object o = ((JSONArray) obj).get(i);
                list.add(o instanceof JSONObject ? new MarkedString((JSONObject) o) : o);
            }
            return Collections.unmodifiableList(list);
        }
        if (obj instanceof JSONObject) {
            if (((JSONObject) obj).has("kind")) {
                return new MarkupContent((JSONObject) obj);
            }
            return new MarkedString((JSONObject) obj);
        }
        return obj;
    }

    @SuppressWarnings("deprecation")
    public Hover setContents(Object contents) {
        if (contents instanceof List) {
            final JSONArray json = new JSONArray();
            for (Object obj : (List<?>) contents) {
                json.put(obj instanceof MarkedString ? ((MarkedString) obj).jsonData : obj);
            }
            jsonData.put("contents", json);
        } else if (contents instanceof MarkupContent) {
            jsonData.put("contents", ((MarkupContent) contents).jsonData);
        } else if (contents instanceof MarkedString) {
            jsonData.put("contents", ((MarkedString) contents).jsonData);
        } else {
            jsonData.put("contents", contents);
        }
        return this;
    }

    /**
     * An optional range.
     */
    public Range getRange() {
        return jsonData.has("range") ? new Range(jsonData.optJSONObject("range")) : null;
    }

    public Hover setRange(Range range) {
        jsonData.putOpt("range", range != null ? range.jsonData : null);
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
        Hover other = (Hover) obj;
        if (!Objects.equals(this.getContents(), other.getContents())) {
            return false;
        }
        if (!Objects.equals(this.getRange(), other.getRange())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 47 * hash + Objects.hashCode(this.getContents());
        if (this.getRange() != null) {
            hash = 47 * hash + Objects.hashCode(this.getRange());
        }
        return hash;
    }

    @SuppressWarnings("deprecation")
    public static Hover create(Object contents) {
        final JSONObject json = new JSONObject();
        if (contents instanceof List) {
            final JSONArray jsonArr = new JSONArray();
            for (Object obj : (List<?>) contents) {
                jsonArr.put(obj instanceof MarkedString ? ((MarkedString) obj).jsonData : obj);
            }
            json.put("contents", jsonArr);
        } else if (contents instanceof MarkupContent) {
            json.put("contents", ((MarkupContent) contents).jsonData);
        } else if (contents instanceof MarkedString) {
            json.put("contents", ((MarkedString) contents).jsonData);
        } else {
            json.put("contents", contents);
        }
        return new Hover(json);
    }
}
