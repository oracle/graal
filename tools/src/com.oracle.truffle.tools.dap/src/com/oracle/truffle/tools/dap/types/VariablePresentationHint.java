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

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Optional properties of a variable that can be used to determine how to render the variable in the
 * UI.
 */
public class VariablePresentationHint extends JSONBase {

    VariablePresentationHint(JSONObject jsonData) {
        super(jsonData);
    }

    /**
     * The kind of variable. Before introducing additional values, try to use the listed values.
     * Values: 'property': Indicates that the object is a property. 'method': Indicates that the
     * object is a method. 'class': Indicates that the object is a class. 'data': Indicates that the
     * object is data. 'event': Indicates that the object is an event. 'baseClass': Indicates that
     * the object is a base class. 'innerClass': Indicates that the object is an inner class.
     * 'interface': Indicates that the object is an interface. 'mostDerivedClass': Indicates that
     * the object is the most derived class. 'virtual': Indicates that the object is virtual, that
     * means it is a synthetic object introducedby the adapter for rendering purposes, e.g. an index
     * range for large arrays. 'dataBreakpoint': Indicates that a data breakpoint is registered for
     * the object. etc.
     */
    public String getKind() {
        return jsonData.optString("kind", null);
    }

    public VariablePresentationHint setKind(String kind) {
        jsonData.putOpt("kind", kind);
        return this;
    }

    /**
     * Set of attributes represented as an array of strings. Before introducing additional values,
     * try to use the listed values. Values: 'static': Indicates that the object is static.
     * 'constant': Indicates that the object is a constant. 'readOnly': Indicates that the object is
     * read only. 'rawString': Indicates that the object is a raw string. 'hasObjectId': Indicates
     * that the object can have an Object ID created for it. 'canHaveObjectId': Indicates that the
     * object has an Object ID associated with it. 'hasSideEffects': Indicates that the evaluation
     * had side effects. etc.
     */
    public List<String> getAttributes() {
        final JSONArray json = jsonData.optJSONArray("attributes");
        if (json == null) {
            return null;
        }
        final List<String> list = new ArrayList<>(json.length());
        for (int i = 0; i < json.length(); i++) {
            list.add(json.getString(i));
        }
        return Collections.unmodifiableList(list);
    }

    public VariablePresentationHint setAttributes(List<String> attributes) {
        if (attributes != null) {
            final JSONArray json = new JSONArray();
            for (String string : attributes) {
                json.put(string);
            }
            jsonData.put("attributes", json);
        }
        return this;
    }

    /**
     * Visibility of variable. Before introducing additional values, try to use the listed values.
     * Values: 'public', 'private', 'protected', 'internal', 'final', etc.
     */
    public String getVisibility() {
        return jsonData.optString("visibility", null);
    }

    public VariablePresentationHint setVisibility(String visibility) {
        jsonData.putOpt("visibility", visibility);
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
        VariablePresentationHint other = (VariablePresentationHint) obj;
        if (!Objects.equals(this.getKind(), other.getKind())) {
            return false;
        }
        if (!Objects.equals(this.getAttributes(), other.getAttributes())) {
            return false;
        }
        if (!Objects.equals(this.getVisibility(), other.getVisibility())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        if (this.getKind() != null) {
            hash = 89 * hash + Objects.hashCode(this.getKind());
        }
        if (this.getAttributes() != null) {
            hash = 89 * hash + Objects.hashCode(this.getAttributes());
        }
        if (this.getVisibility() != null) {
            hash = 89 * hash + Objects.hashCode(this.getVisibility());
        }
        return hash;
    }

    public static VariablePresentationHint create() {
        final JSONObject json = new JSONObject();
        return new VariablePresentationHint(json);
    }
}
