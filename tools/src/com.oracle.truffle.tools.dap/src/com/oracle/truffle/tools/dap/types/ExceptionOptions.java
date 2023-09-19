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
 * An ExceptionOptions assigns configuration options to a set of exceptions.
 */
public class ExceptionOptions extends JSONBase {

    ExceptionOptions(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * A path that selects a single or multiple exceptions in a tree. If 'path' is missing, the
     * whole tree is selected. By convention the first segment of the path is a category that is
     * used to group exceptions in the UI.
     */
    public List<ExceptionPathSegment> getPath() {
        final JSONArray json = jsonData.optJSONArray("path");
        if (json == null) {
            return null;
        }
        final List<ExceptionPathSegment> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(new ExceptionPathSegment(json.getJSONObject(i)));
        }
        return Collections.unmodifiableList(list);
    }

    public ExceptionOptions setPath(List<ExceptionPathSegment> path) {
        if (path != null) {
            final JSONArray json = new JSONArray();
            for (ExceptionPathSegment exceptionPathSegment : path) {
                json.put(exceptionPathSegment.jsonData);
            }
            jsonData.put("path", json);
        }
        return this;
    }

    /**
     * Condition when a thrown exception should result in a break.
     */
    public String getBreakMode() {
        return jsonData.getString("breakMode");
    }

    public ExceptionOptions setBreakMode(String breakMode) {
        jsonData.put("breakMode", breakMode);
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
        ExceptionOptions other = (ExceptionOptions) obj;
        if (!Objects.equals(this.getPath(), other.getPath())) {
            return false;
        }
        if (!Objects.equals(this.getBreakMode(), other.getBreakMode())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getPath() != null) {
            hash = 11 * hash + Objects.hashCode(this.getPath());
        }
        hash = 11 * hash + Objects.hashCode(this.getBreakMode());
        return hash;
    }

    public static ExceptionOptions create(String breakMode) {
        final JSONObject json = new JSONObject();
        json.put("breakMode", breakMode);
        return new ExceptionOptions(json);
    }
}
