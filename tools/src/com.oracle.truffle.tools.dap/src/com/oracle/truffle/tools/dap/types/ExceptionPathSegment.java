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

import org.graalvm.shadowed.org.json.JSONArray;
import org.graalvm.shadowed.org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An ExceptionPathSegment represents a segment in a path that is used to match leafs or nodes in a
 * tree of exceptions. If a segment consists of more than one name, it matches the names provided if
 * 'negate' is false or missing or it matches anything except the names provided if 'negate' is
 * true.
 */
public class ExceptionPathSegment extends JSONBase {

    ExceptionPathSegment(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * If false or missing this segment matches the names provided, otherwise it matches anything
     * except the names provided.
     */
    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    public Boolean getNegate() {
        return jsonData.has("negate") ? jsonData.getBoolean("negate") : null;
    }

    public ExceptionPathSegment setNegate(Boolean negate) {
        jsonData.putOpt("negate", negate);
        return this;
    }

    /**
     * Depending on the value of 'negate' the names that should match or not match.
     */
    public List<String> getNames() {
        final JSONArray json = jsonData.getJSONArray("names");
        final List<String> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(json.getString(i));
        }
        return Collections.unmodifiableList(list);
    }

    public ExceptionPathSegment setNames(List<String> names) {
        final JSONArray json = new JSONArray();
        for (String string : names) {
            json.put(string);
        }
        jsonData.put("names", json);
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
        ExceptionPathSegment other = (ExceptionPathSegment) obj;
        if (!Objects.equals(this.getNegate(), other.getNegate())) {
            return false;
        }
        if (!Objects.equals(this.getNames(), other.getNames())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getNegate() != null) {
            hash = 37 * hash + Boolean.hashCode(this.getNegate());
        }
        hash = 37 * hash + Objects.hashCode(this.getNames());
        return hash;
    }

    public static ExceptionPathSegment create(List<String> names) {
        final JSONObject json = new JSONObject();
        JSONArray namesJsonArr = new JSONArray();
        for (String string : names) {
            namesJsonArr.put(string);
        }
        json.put("names", namesJsonArr);
        return new ExceptionPathSegment(json);
    }
}
