/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.atomic.AtomicLong;

import org.json.JSONArray;
import org.json.JSONObject;

import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.nodes.LanguageInfo;

public final class RemoteObject {

    private enum TYPE {

        OBJECT("object"),
        FUNCTION("function"),
        UNDEFINED("undefined"),
        STRING("string"),
        NUMBER("number"),
        BOOLEAN("boolean"),
        SYMBOL("symbol");

        private final String id;

        TYPE(String id) {
            this.id = id;
        }

        String getId() {
            return id;
        }
    }

    private static final AtomicLong LAST_ID = new AtomicLong(0);

    private final DebugValue valueValue;
    private final DebugScope valueScope;
    private final String type;
    private final String subtype;
    private final String className;
    private final String value;
    private final String objectId;
    private final String description;
    private final JSONObject jsonObject;

    public RemoteObject(DebugValue originalDebugValue, PrintWriter err) {
        DebugValue debugValue = originalDebugValue;
        this.valueValue = debugValue;
        this.valueScope = null;
        LanguageInfo originalLanguage = debugValue.getOriginalLanguage();
        // Setup the object with a language-specific value
        if (originalLanguage != null) {
            debugValue = debugValue.asInLanguage(originalLanguage);
        }
        DebugValue metaObject = getMetaObject(debugValue, originalLanguage, err);
        boolean isObject = isObject(debugValue, err);
        String vtype = null;
        String vsubtype = null;
        String vclassName = null;
        String vdescription = null;
        if (metaObject != null) {
            try {
                Collection<DebugValue> properties = metaObject.getProperties();
                if (properties != null) {
                    for (DebugValue prop : properties) {
                        String name = prop.getName();
                        if ("type".equals(name)) {
                            vtype = prop.as(String.class);
                        } else if ("subtype".equals(name)) {
                            vsubtype = prop.as(String.class);
                        } else if ("className".equals(name)) {
                            vclassName = prop.as(String.class);
                        } else if ("description".equals(name)) {
                            vdescription = prop.as(String.class);
                        }
                    }
                }
            } catch (Exception ex) {
                if (err != null) {
                    err.println("getProperties of meta object of (" + debugValue.getName() + ") has caused: " + ex);
                    ex.printStackTrace(err);
                }
            }
        }
        String descriptionType = null;
        if (vtype != null && (vsubtype != null || vclassName != null)) {
            this.type = vtype;
            this.subtype = vsubtype;
            this.className = vclassName;
        } else {
            if (debugValue.isArray()) {
                this.subtype = "array";
            } else {
                this.subtype = null;
            }
            if (isObject) {
                this.type = TYPE.OBJECT.getId();
                this.className = (metaObject != null) ? metaObject.as(String.class) : null;
            } else {
                String metaType = (metaObject != null) ? metaObject.as(String.class) : null;
                this.type = getType(debugValue, metaType);
                this.className = null;
                if (TYPE.OBJECT.getId().equals(this.type)) {
                    descriptionType = metaType;
                }
            }
        }
        if (descriptionType == null) {
            descriptionType = this.className;
        }
        String toString;
        try {
            toString = debugValue.as(String.class);
        } catch (Exception ex) {
            if (err != null) {
                err.println(debugValue.getName() + " as(String.class) has caused: " + ex);
                ex.printStackTrace(err);
            }
            toString = null;
        }
        this.value = (!isObject) ? toString : null;
        if ((vdescription == null || vdescription.equals(toString)) && descriptionType != null) {
            this.description = descriptionType + ((toString != null && !toString.isEmpty()) ? " " + toString : "");
        } else if (vdescription != null && !vdescription.isEmpty()) {
            this.description = vdescription;
        } else {
            this.description = toString;
        }
        this.objectId = (isObject) ? Long.toString(LAST_ID.incrementAndGet()) : null;
        this.jsonObject = createJSON();
    }

    public RemoteObject(DebugScope scope) {
        this.valueValue = null;
        this.valueScope = scope;
        this.type = "object";
        this.subtype = null;
        this.className = null;
        this.value = null;
        this.objectId = Long.toString(LAST_ID.incrementAndGet());
        this.description = scope.getName();
        this.jsonObject = createJSON();
    }

    private JSONObject createJSON() {
        JSONObject json = new JSONObject();
        json.put("type", type);
        json.putOpt("subtype", subtype);
        json.putOpt("className", className);
        json.putOpt("value", value);
        json.putOpt("description", description);
        json.putOpt("objectId", objectId);
        return json;
    }

    private static DebugValue getMetaObject(DebugValue debugValue, LanguageInfo originalLanguage, PrintWriter err) {
        DebugValue metaObject;
        try {
            metaObject = debugValue.getMetaObject();
            if (originalLanguage != null && metaObject != null) {
                metaObject = metaObject.asInLanguage(originalLanguage);
            }
        } catch (Exception ex) {
            if (err != null) {
                err.println("getMetaObject(" + debugValue.getName() + ") has caused: " + ex);
                ex.printStackTrace(err);
            }
            metaObject = null;
        }
        return metaObject;
    }

    private static boolean isObject(DebugValue debugValue, PrintWriter err) {
        boolean isObject;
        try {
            isObject = debugValue.getProperties() != null;
        } catch (Exception ex) {
            if (err != null) {
                err.println("getProperties(" + debugValue.getName() + ") has caused: " + ex);
                ex.printStackTrace(err);
            }
            isObject = false;
        }
        return isObject;
    }

    /**
     * The type must be one of {@link TYPE}.
     */
    private static String getType(DebugValue value, String metaObject) {
        if (metaObject == null) {
            return TYPE.OBJECT.getId();
        }
        for (TYPE type : TYPE.values()) {
            if (metaObject.equalsIgnoreCase(type.getId())) {
                return type.getId();
            }
        }
        Number number = value.as(Number.class);
        if (number != null) {
            return TYPE.NUMBER.getId();
        }
        Boolean bool = value.as(Boolean.class);
        if (bool != null) {
            return TYPE.BOOLEAN.getId();
        }
        return TYPE.OBJECT.getId();
    }

    /**
     * Create a JSON object representing the provided {@link DebugValue}. Use when a reply by value
     * is requested.
     */
    public static JSONObject createJSONResultValue(DebugValue debugValue, PrintWriter err) {
        JSONObject json = new JSONObject();
        DebugValue metaObject = getMetaObject(debugValue, null, err);
        boolean isObject = isObject(debugValue, err);
        String vtype = null;
        if (metaObject != null) {
            try {
                Collection<DebugValue> properties = metaObject.getProperties();
                if (properties != null) {
                    for (DebugValue prop : properties) {
                        String name = prop.getName();
                        if ("type".equals(name)) {
                            vtype = prop.as(String.class);
                        }
                    }
                }
            } catch (Exception ex) {
                if (err != null) {
                    err.println("getProperties of meta object of (" + debugValue.getName() + ") has caused: " + ex);
                    ex.printStackTrace(err);
                }
            }
        }
        if (vtype == null) {
            if (isObject) {
                vtype = "object";
            } else {
                vtype = (metaObject != null) ? metaObject.as(String.class) : "object";
            }
        }
        json.put("type", vtype);
        json.put("value", createJSONValue(debugValue, err));
        return json;
    }

    public static Object createJSONValue(DebugValue debugValue, PrintWriter err) {
        Collection<DebugValue> properties = null;
        try {
            properties = debugValue.getProperties();
        } catch (Exception ex) {
            if (err != null) {
                err.println("getProperties(" + debugValue.getName() + ") has caused: " + ex);
                ex.printStackTrace(err);
            }
        }
        if (debugValue.isArray()) {
            JSONArray array = new JSONArray();
            for (DebugValue element : debugValue.getArray()) {
                array.put(createJSONValue(element, err));
            }
            return array;
        } else if (properties != null) {
            JSONObject props = new JSONObject();
            for (DebugValue property : properties) {
                props.put(property.getName(), createJSONValue(property, err));
            }
            return props;
        } else {
            return debugValue.as(String.class);
        }
    }

    public String getId() {
        return objectId;
    }

    public JSONObject toJSON() {
        return jsonObject;
    }

    /**
     * Get the value, or <code>null</code> when there is a {@link #getScope() scope}.
     */
    public DebugValue getDebugValue() {
        return valueValue;
    }

    /**
     * Get the frame, or <code>null</code> when there is a {@link #getDebugValue() value}.
     */
    public DebugScope getScope() {
        return valueScope;
    }

    /**
     * For test purposes only. Do not call from production code.
     */
    public static void resetIDs() {
        LAST_ID.set(0);
    }
}
