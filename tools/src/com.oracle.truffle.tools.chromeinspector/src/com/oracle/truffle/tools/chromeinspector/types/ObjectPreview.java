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
import com.oracle.truffle.tools.chromeinspector.types.TypeInfo.SUBTYPE;
import com.oracle.truffle.tools.chromeinspector.types.TypeInfo.TYPE;
import org.graalvm.shadowed.org.json.JSONArray;
import org.graalvm.shadowed.org.json.JSONObject;

final class ObjectPreview {

    private static final int OVERFLOW_LIMIT_PROPERTIES = 5;
    private static final int OVERFLOW_LIMIT_ARRAY_ELEMENTS = 100;

    private ObjectPreview() {
    }

    static JSONObject create(DebugValue debugValue, TYPE type, SUBTYPE subtype, boolean allowToStringSideEffects, LanguageInfo language, PrintWriter err) {
        return create(debugValue, type, subtype, allowToStringSideEffects, language, err, false);
    }

    private static JSONObject create(DebugValue debugValue, boolean allowToStringSideEffects, LanguageInfo language, PrintWriter err, boolean isMapEntryKV) {
        TypeInfo typeInfo = TypeInfo.fromValue(debugValue, null, language, err);
        return create(debugValue, typeInfo.type, typeInfo.subtype, allowToStringSideEffects, language, err, isMapEntryKV);
    }

    private static JSONObject create(DebugValue debugValue, TYPE type, SUBTYPE subtype, boolean allowToStringSideEffects, LanguageInfo language, PrintWriter err, boolean isMapEntryKV) {
        JSONObject json = new JSONObject();
        json.put("type", type.getId());
        if (subtype != null) {
            json.put("subtype", subtype.getId());
        }

        boolean isArray = debugValue.isArray();
        boolean isMap = debugValue.hasHashEntries();
        if (isMapEntryKV) {
            String valueStr = RemoteObject.toString(debugValue, allowToStringSideEffects, err);
            json.putOpt("description", valueStr);
        } else {
            DebugValue metaObject = RemoteObject.getMetaObject(debugValue, language, err);
            String metaType = null;
            if (metaObject != null) {
                metaType = RemoteObject.toMetaName(metaObject, err);
                if (isArray) {
                    metaType += "(" + debugValue.getArray().size() + ")";
                } else if (isMap) {
                    metaType += "(" + debugValue.getHashSize() + ")";
                }
            }
            json.putOpt("description", metaType);
        }
        boolean overflow;
        JSONArray properties = new JSONArray();
        JSONArray entries = new JSONArray();
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
        } else if (isMap && !isMapEntryKV) {
            DebugValue entriesIter = debugValue.getHashEntriesIterator();
            overflow = false;
            while (entriesIter.hasIteratorNextElement()) {
                DebugValue entry = entriesIter.getIteratorNextElement();
                JSONObject entryPreview;
                try {
                    entryPreview = createEntryPreview(entry, allowToStringSideEffects, language, err);
                } catch (DebugException ex) {
                    overflow = true;
                    break;
                }
                if (entryPreview != null) {
                    if (entries.length() == OVERFLOW_LIMIT_PROPERTIES) {
                        overflow = true;
                        break;
                    }
                    entries.put(entryPreview);
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
        if (isMap && !isMapEntryKV) {
            json.put("entries", entries);
        }
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
        TypeInfo typeInfo = TypeInfo.fromValue(property, null, language, err);
        json.put("type", typeInfo.type.getId());
        if (typeInfo.subtype != null) {
            json.put("subtype", typeInfo.subtype.getId());
        }
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

    private static JSONObject createEntryPreview(DebugValue entry, boolean allowToStringSideEffects, LanguageInfo language, PrintWriter err) {
        List<DebugValue> entryArray = entry.getArray();
        DebugValue key = entryArray.get(0);
        DebugValue value = entryArray.get(1);
        // Setup the object with a language-specific value
        if (language != null) {
            key = key.asInLanguage(language);
            value = value.asInLanguage(language);
        }
        JSONObject json = new JSONObject();
        json.put("key", create(key, allowToStringSideEffects, language, err, true));
        json.put("value", create(value, allowToStringSideEffects, language, err, true));
        return json;
    }
}
