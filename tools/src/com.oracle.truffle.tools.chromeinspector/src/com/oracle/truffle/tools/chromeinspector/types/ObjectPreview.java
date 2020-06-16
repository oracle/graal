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
package com.oracle.truffle.tools.chromeinspector.types;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

final class ObjectPreview {

    private static final int OVERFLOW_LIMIT_PROPERTIES = 5;
    private static final int OVERFLOW_LIMIT_ARRAY_ELEMENTS = 100;

    private ObjectPreview() {
    }

    static JSONObject create(DebugValue debugValue, String type, String subtype, boolean allowToStringSideEffects, LanguageInfo language, PrintWriter err) {
        JSONObject json = new JSONObject();
        json.put("type", type);
        json.put("subtype", subtype);

        boolean isArray = debugValue.isArray();
        DebugValue metaObject = RemoteObject.getMetaObject(debugValue, language, err);
        String metaType = null;
        if (metaObject != null) {
            metaType = RemoteObject.toMetaName(metaObject, err);
            if (isArray) {
                metaType += "(" + debugValue.getArray().size() + ")";
            }
        }
        json.putOpt("description", metaType);
        boolean overflow;
        JSONArray properties = new JSONArray();
        if (isArray) {
            List<DebugValue> array = debugValue.getArray();
            int size = array.size();
            overflow = size > OVERFLOW_LIMIT_ARRAY_ELEMENTS;
            int n = Math.min(size, OVERFLOW_LIMIT_ARRAY_ELEMENTS);
            for (int i = 0; i < n; i++) {
                try {
                    properties.put(createPropertyPreview(array.get(i), allowToStringSideEffects, language, err));
                } catch (DebugException ex) {
                    overflow = true;
                    break;
                }
            }
        } else {
            Collection<DebugValue> valueProperties = debugValue.getProperties();
            if (valueProperties != null) {
                Iterator<DebugValue> propertyIterator = valueProperties.iterator();
                overflow = false;
                while (propertyIterator.hasNext()) {
                    DebugValue property = propertyIterator.next();
                    if (!property.isInternal() && !property.hasReadSideEffects() && property.isReadable()) {
                        if (properties.length() == OVERFLOW_LIMIT_PROPERTIES) {
                            overflow = true;
                            break;
                        }
                        try {
                            properties.put(createPropertyPreview(property, allowToStringSideEffects, language, err));
                        } catch (DebugException ex) {
                            overflow = true;
                            break;
                        }
                    }
                }
            } else {
                overflow = false;
            }
        }
        json.put("overflow", overflow);
        json.put("properties", properties);
        return json;
    }

    private static JSONObject createPropertyPreview(DebugValue origProperty, boolean allowToStringSideEffects, LanguageInfo language, PrintWriter err) {
        DebugValue property = origProperty;
        // Setup the object with a language-specific value
        if (language != null) {
            property = property.asInLanguage(language);
        }
        JSONObject json = new JSONObject();
        json.put("name", property.getName());
        boolean isArray = property.isArray();
        TypeInfo typeInfo = TypeInfo.fromValue(property, language, err);
        json.put("type", typeInfo.type);
        json.putOpt("subtype", typeInfo.subtype);
        String value;
        if (isArray) {
            String size = "(" + property.getArray().size() + ")";
            value = typeInfo.descriptionType != null ? typeInfo.descriptionType + size : size;
        } else {
            value = RemoteObject.toString(property, allowToStringSideEffects, err);
        }
        json.putOpt("value", value);
        return json;
    }

}
