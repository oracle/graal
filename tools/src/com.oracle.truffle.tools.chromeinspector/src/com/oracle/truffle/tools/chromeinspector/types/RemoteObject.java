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
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

import com.oracle.truffle.api.debug.DebugException;
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

    private static final Double NEGATIVE_DOUBLE_0 = Double.valueOf("-0");
    private static final Float NEGATIVE_FLOAT_0 = Float.valueOf("-0");

    private static final AtomicLong LAST_ID = new AtomicLong(0);

    private final DebugValue valueValue;
    private final DebugScope valueScope;
    private final String type;
    private final String subtype;
    private final String className;
    private final Object value;
    private final boolean replicableValue;
    private final boolean nullValue;
    private final String unserializableValue;
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
        if (metaObject != null && originalLanguage != null && "js".equals(originalLanguage.getId())) {
            // Get special JS properties:
            try {
                DebugValue property = metaObject.getProperty("type");
                if (property != null) {
                    vtype = property.as(String.class);
                    property = metaObject.getProperty("subtype");
                    if (property != null) {
                        vsubtype = property.as(String.class);
                    }
                    property = metaObject.getProperty("className");
                    if (property != null) {
                        vclassName = property.as(String.class);
                    }
                    property = metaObject.getProperty("description");
                    if (property != null) {
                        vdescription = property.as(String.class);
                    }
                }
            } catch (DebugException ex) {
                if (err != null && ex.isInternalError()) {
                    err.println("getProperties of meta object of (" + debugValue.getName() + ") has caused: " + ex);
                    ex.printStackTrace(err);
                }
                throw ex;
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
            String metaType = null;
            if (metaObject != null) {
                try {
                    metaType = metaObject.as(String.class);
                } catch (DebugException ex) {
                    if (err != null && ex.isInternalError()) {
                        err.println(debugValue.getName() + " as(String.class) has caused: " + ex);
                        ex.printStackTrace(err);
                    }
                    throw ex;
                }
            }
            if (debugValue.canExecute()) {
                this.type = TYPE.FUNCTION.getId();
                this.className = metaType;
            } else if (isObject) {
                this.type = TYPE.OBJECT.getId();
                this.className = metaType;
            } else {
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
        Object rawValue = null;
        String unserializable = null;
        boolean rawNullValue = false;
        boolean replicableRawValue = true;
        try {
            toString = debugValue.as(String.class);
            if (!isObject) {
                if ("null".equals(vsubtype) && "object".equals(vtype)) {
                    replicableRawValue = false;
                    rawNullValue = true;
                } else if ("undefined".equals(vtype)) {
                    replicableRawValue = false;
                } else {
                    rawValue = debugValue.as(Boolean.class);
                    if (rawValue == null) {
                        rawValue = debugValue.as(Number.class);
                        if (rawValue != null && !isFinite((Number) rawValue)) {
                            unserializable = rawValue.toString();
                            rawValue = null;
                        } else if (rawValue == null) {
                            replicableRawValue = false;
                            rawValue = toString;
                        }
                    }
                }
            }
        } catch (DebugException ex) {
            if (err != null && ex.isInternalError()) {
                err.println(debugValue.getName() + " as(class) has caused: " + ex);
                ex.printStackTrace(err);
            }
            throw ex;
        }
        this.value = rawValue;
        this.replicableValue = replicableRawValue;
        this.nullValue = rawNullValue;
        this.unserializableValue = unserializable;
        if (vdescription == null && descriptionType != null) {
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
        this.replicableValue = false;
        this.nullValue = false;
        this.unserializableValue = null;
        this.objectId = Long.toString(LAST_ID.incrementAndGet());
        this.description = scope.getName();
        this.jsonObject = createJSON();
    }

    private JSONObject createJSON() {
        JSONObject json = new JSONObject();
        json.put("type", type);
        json.putOpt("subtype", subtype);
        json.putOpt("className", className);
        json.putOpt("unserializableValue", unserializableValue);
        if (nullValue) {
            json.put("value", JSONObject.NULL);
        } else {
            json.putOpt("value", value);
        }
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
        } catch (DebugException ex) {
            if (err != null && ex.isInternalError()) {
                err.println("getMetaObject(" + debugValue.getName() + ") has caused: " + ex);
                ex.printStackTrace(err);
            }
            throw ex;
        }
        return metaObject;
    }

    private static boolean isObject(DebugValue debugValue, PrintWriter err) {
        boolean isObject;
        try {
            isObject = debugValue.getProperties() != null || debugValue.canExecute();
        } catch (DebugException ex) {
            if (err != null && ex.isInternalError()) {
                err.println("getProperties(" + debugValue.getName() + ") has caused: " + ex);
                ex.printStackTrace(err);
            }
            throw ex;
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
            } catch (DebugException ex) {
                if (err != null && ex.isInternalError()) {
                    err.println("getProperties of meta object of (" + debugValue.getName() + ") has caused: " + ex);
                    ex.printStackTrace(err);
                }
                throw ex;
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
        String[] unserializablePtr = new String[1];
        try {
            json.putOpt("value", createJSONValue(debugValue, unserializablePtr, err));
        } catch (DebugException ex) {
            if (err != null && ex.isInternalError()) {
                err.println("getProperties(" + debugValue.getName() + ") has caused: " + ex);
                ex.printStackTrace(err);
            }
            throw ex;
        }
        json.putOpt("unserializableValue", unserializablePtr[0]);
        return json;
    }

    private static Object createJSONValue(DebugValue debugValue, String[] unserializablePtr, PrintWriter err) {
        if (debugValue.isArray()) {
            List<DebugValue> valueArray = debugValue.getArray();
            if (valueArray != null) {
                JSONArray array = new JSONArray();
                for (DebugValue element : valueArray) {
                    array.put(createJSONValue(element, null, err));
                }
                return array;
            }
        }
        Collection<DebugValue> properties = debugValue.getProperties();
        if (properties != null) {
            JSONObject props = new JSONObject();
            for (DebugValue property : properties) {
                props.put(property.getName(), createJSONValue(property, null, err));
            }
            return props;
        } else {
            if (unserializablePtr != null) {
                Boolean bool = debugValue.as(Boolean.class);
                if (bool != null) {
                    return bool;
                }
                Number num = debugValue.as(Number.class);
                if (num != null) {
                    if (!isFinite(num)) {
                        unserializablePtr[0] = num.toString();
                        return null;
                    }
                    return num;
                }
            }
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
     * Test whether the JSON value can be parsed back to the equal DebugValue (by
     * {@link CallArgument}).
     */
    public boolean isReplicable() {
        return replicableValue;
    }

    /**
     * Get the value, or <code>null</code> when there is a {@link #getScope() scope}.
     */
    public DebugValue getDebugValue() {
        return valueValue;
    }

    /**
     * Get the raw (primitive, String, or null) value.
     */
    public Object getRawValue() {
        return value;
    }

    /**
     * Get the frame, or <code>null</code> when there is a {@link #getDebugValue() value}.
     */
    public DebugScope getScope() {
        return valueScope;
    }

    private static boolean isFinite(Number n) {
        if (n instanceof Double) {
            Double d = (Double) n;
            return !d.isInfinite() && !d.isNaN() && !d.equals(NEGATIVE_DOUBLE_0);
        } else if (n instanceof Float) {
            Float f = (Float) n;
            return !f.isInfinite() && !f.isNaN() && !f.equals(NEGATIVE_FLOAT_0);
        }
        return true;
    }

    /**
     * For test purposes only. Do not call from production code.
     */
    public static void resetIDs() {
        LAST_ID.set(0);
    }
}
