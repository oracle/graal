/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.chromeinspector.InspectorExecutionContext;
import com.oracle.truffle.tools.chromeinspector.LanguageChecks;
import com.oracle.truffle.tools.chromeinspector.objects.NullObject;
import com.oracle.truffle.tools.chromeinspector.types.TypeInfo.TYPE;

public final class RemoteObject {

    private static final Double NEGATIVE_DOUBLE_0 = Double.valueOf("-0");
    private static final Float NEGATIVE_FLOAT_0 = Float.valueOf("-0");

    private static final AtomicLong LAST_ID = new AtomicLong(0);

    private final DebugValue valueValue;
    private final DebugScope valueScope;
    private final boolean generatePreview;
    private final String objectId;
    private final InspectorExecutionContext context;
    private final IndexRange indexRange;
    private TypeInfo typeInfo;
    private Object value;
    private boolean replicableValue;
    private String unserializableValue;
    private String description;
    private JSONObject preview;
    private JSONObject customPreview;
    private JSONObject jsonObject;

    public RemoteObject(DebugValue debugValue, boolean generatePreview, InspectorExecutionContext context) {
        this(debugValue, false, generatePreview, context);
    }

    public RemoteObject(DebugValue debugValue, boolean readEagerly, boolean generatePreview, InspectorExecutionContext context) {
        this(debugValue, readEagerly, generatePreview, context, null);
    }

    public RemoteObject(DebugValue debugValue, boolean readEagerly, boolean generatePreview, InspectorExecutionContext context, IndexRange indexRange) {
        this.valueValue = debugValue;
        this.valueScope = null;
        this.generatePreview = generatePreview;
        this.context = context;
        this.indexRange = indexRange;
        if (!debugValue.hasReadSideEffects() || readEagerly) {
            boolean isObject = initFromValue();
            objectId = (isObject) ? Long.toString(LAST_ID.incrementAndGet()) : null;
            jsonObject = createJSON();
        } else {
            objectId = Long.toString(LAST_ID.incrementAndGet());
        }
    }

    private boolean initFromValue() {
        DebugValue debugValue = valueValue;
        LanguageInfo originalLanguage = debugValue.getOriginalLanguage();
        // Setup the object with a language-specific value
        if (originalLanguage != null) {
            debugValue = debugValue.asInLanguage(originalLanguage);
        }
        PrintWriter err = context != null ? context.getErr() : null;
        this.typeInfo = TypeInfo.fromValue(debugValue, originalLanguage, err);
        boolean readable = debugValue.isReadable();
        String toString;
        boolean addType = true;
        Object rawValue = null;
        String unserializable = null;
        boolean replicableRawValue = true;
        try {
            if (readable) {
                SourceSection sourceSection;
                if (typeInfo.isFunction && (sourceSection = debugValue.getSourceLocation()) != null && sourceSection.isAvailable() && sourceSection.getSource().hasCharacters()) {
                    toString = sourceSection.getCharacters().toString();
                    addType = false;
                } else {
                    toString = debugValue.toDisplayString(context.areToStringSideEffectsAllowed());
                    if (TYPE.STRING.getId().equals(typeInfo.type) || TYPE.SYMBOL.getId().equals(typeInfo.type)) {
                        // The whole description is rendered as a String in quotes, or highlighted
                        // as a symbol. Do not prepend the type.
                        addType = false;
                    }
                }
            } else {
                toString = InspectorExecutionContext.VALUE_NOT_READABLE;
                replicableRawValue = false;
            }
            if (readable && !typeInfo.isObject) {
                if ("null".equals(typeInfo.subtype) && TYPE.OBJECT.getId().equals(typeInfo.type)) {
                    replicableRawValue = false;
                } else if (TYPE.UNDEFINED.getId().equals(typeInfo.type)) {
                    replicableRawValue = false;
                } else {
                    if (debugValue.isBoolean()) {
                        rawValue = debugValue.asBoolean();
                    } else if (debugValue.isNumber()) {
                        rawValue = TypeInfo.toNumber(debugValue);
                        if (!isFinite((Number) rawValue)) {
                            unserializable = rawValue.toString();
                            rawValue = null;
                        }
                    } else {
                        replicableRawValue = false;
                        rawValue = toString;
                    }
                }
            }
        } catch (DebugException ex) {
            if (err != null && ex.isInternalError()) {
                err.println(debugValue.getName() + " toDisplayString() has caused: " + ex);
                ex.printStackTrace(err);
            }
            throw ex;
        }
        this.value = rawValue;
        this.replicableValue = replicableRawValue;
        this.unserializableValue = unserializable;
        if (addType && typeInfo.descriptionType != null && !typeInfo.descriptionType.equals(toString)) {
            this.description = typeInfo.descriptionType + ((toString != null && !toString.isEmpty()) ? " " + toString : "");
        } else {
            this.description = toString;
        }
        if (typeInfo.isObject && addType && typeInfo.isJS && generatePreview) {
            try {
                this.preview = ObjectPreview.create(debugValue, typeInfo.type, typeInfo.subtype, context.areToStringSideEffectsAllowed(), originalLanguage, err);
            } catch (DebugException ex) {
                if (err != null && ex.isInternalError()) {
                    err.println(debugValue.getName() + " preview has caused: " + ex);
                    ex.printStackTrace(err);
                }
            }
        }
        if (readable && context != null && context.isCustomObjectFormatterEnabled()) {
            if (originalLanguage != null) {
                try {
                    this.customPreview = CustomPreview.create(debugValue, originalLanguage, context);
                } catch (DebugException ex) {
                    if (err != null) {
                        if (ex.isInternalError()) {
                            err.println(debugValue.getName() + " custom preview has caused: " + ex);
                            ex.printStackTrace(err);
                        } else {
                            err.println("Custom Formatter Failed: " + ex.getLocalizedMessage());
                        }
                    }
                }
            }
        }
        return typeInfo.isObject;
    }

    public RemoteObject(DebugScope scope) {
        this(scope, null);
    }

    public RemoteObject(DebugScope scope, String objectId) {
        this.valueValue = null;
        this.valueScope = scope;
        this.generatePreview = false;
        this.context = null;
        this.indexRange = null;
        this.typeInfo = new TypeInfo(TYPE.OBJECT.getId(), null, null, null, true, false, false, false);
        this.value = null;
        this.replicableValue = false;
        this.unserializableValue = null;
        this.objectId = (objectId == null) ? Long.toString(LAST_ID.incrementAndGet()) : objectId;
        this.description = scope.getName();
        this.jsonObject = createJSON();
    }

    private RemoteObject(String type, String subtype, String className, String description) {
        this.valueValue = null;
        this.valueScope = null;
        this.generatePreview = false;
        this.context = null;
        this.indexRange = null;
        this.typeInfo = new TypeInfo(type, subtype, className, null, true, false, false, false);
        this.value = null;
        this.replicableValue = false;
        this.unserializableValue = null;
        this.objectId = Long.toString(LAST_ID.incrementAndGet());
        this.description = description;
        this.jsonObject = createJSON();
    }

    public static RemoteObject createSimpleObject(String type, String className, String description) {
        return new RemoteObject(type, null, className, description);
    }

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    public static RemoteObject createNullObject(TruffleInstrument.Env env, LanguageInfo language) {
        String nullStr;
        try {
            nullStr = INTEROP.asString(INTEROP.toDisplayString(env.getLanguageView(language, NullObject.INSTANCE)));
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(e);
        }
        return new RemoteObject("object", "null", null, nullStr);
    }

    private JSONObject createJSON() {
        JSONObject json = new JSONObject();
        json.put("type", typeInfo.type);
        json.putOpt("subtype", typeInfo.subtype);
        json.putOpt("className", typeInfo.className);
        json.putOpt("unserializableValue", unserializableValue);
        json.putOpt("value", value);
        if (typeInfo.isNull) {
            json.put("value", JSONObject.NULL);
        }
        json.putOpt("description", description);
        json.putOpt("objectId", objectId);
        json.putOpt("preview", preview);
        json.putOpt("customPreview", customPreview);
        return json;
    }

    static DebugValue getMetaObject(DebugValue debugValue, LanguageInfo originalLanguage, PrintWriter err) {
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

    static String toString(DebugValue value, boolean allowSideEffects, PrintWriter err) {
        if (!value.isReadable()) {
            return InspectorExecutionContext.VALUE_NOT_READABLE;
        }
        try {
            return value.toDisplayString(allowSideEffects);
        } catch (DebugException ex) {
            if (err != null && ex.isInternalError()) {
                err.println(value.getName() + " toDisplayString() has caused: " + ex);
                ex.printStackTrace(err);
            }
            throw ex;
        }
    }

    static String toMetaName(DebugValue metaValue, PrintWriter err) {
        try {
            return metaValue.getMetaSimpleName();
        } catch (DebugException ex) {
            if (err != null && ex.isInternalError()) {
                err.println(" getMetaSimpleName() has caused: " + ex);
                ex.printStackTrace(err);
            }
            return null;
        }
    }

    /**
     * Create a JSON object representing the provided {@link DebugValue}. Use when a reply by value
     * is requested.
     */
    public static JSONObject createJSONResultValue(DebugValue debugValue, boolean allowToStringSideEffects, PrintWriter err) {
        JSONObject json = new JSONObject();
        DebugValue metaObject = getMetaObject(debugValue, null, err);
        boolean isObject = TypeInfo.isObject(debugValue, err);
        boolean isJS = LanguageChecks.isJS(debugValue.getOriginalLanguage());
        String vtype = null;
        if (metaObject != null & isJS) {
            try {
                Collection<DebugValue> properties = metaObject.getProperties();
                if (properties != null) {
                    for (DebugValue prop : properties) {
                        String name = prop.getName();
                        if ("type".equals(name)) {
                            vtype = toMetaName(prop, err);
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
                vtype = (metaObject != null) ? toString(metaObject, allowToStringSideEffects, err) : "object";
            }
        }
        json.put("type", vtype);
        String[] unserializablePtr = new String[1];
        try {
            json.putOpt("value", createJSONValue(debugValue, allowToStringSideEffects, unserializablePtr, err));
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

    private static Object createJSONValue(DebugValue debugValue, boolean allowToStringSideEffects, String[] unserializablePtr, PrintWriter err) {
        if (!debugValue.isReadable()) {
            return InspectorExecutionContext.VALUE_NOT_READABLE;
        }
        if (debugValue.isArray()) {
            List<DebugValue> valueArray = debugValue.getArray();
            if (valueArray != null) {
                JSONArray array = new JSONArray();
                for (DebugValue element : valueArray) {
                    array.put(createJSONValue(element, allowToStringSideEffects, null, err));
                }
                return array;
            }
        }
        Collection<DebugValue> properties = debugValue.getProperties();
        if (properties != null) {
            JSONObject props = new JSONObject();
            for (DebugValue property : properties) {
                props.put(property.getName(), createJSONValue(property, allowToStringSideEffects, null, err));
            }
            return props;
        } else {
            if (unserializablePtr != null) {
                if (debugValue.isBoolean()) {
                    return debugValue.asBoolean();
                }
                if (debugValue.isNumber()) {
                    Number num = TypeInfo.toNumber(debugValue);
                    if (num != null) {
                        if (!isFinite(num)) {
                            unserializablePtr[0] = num.toString();
                            return null;
                        }
                        return num;
                    }
                }
            }
            return debugValue.toDisplayString(allowToStringSideEffects);
        }
    }

    public String getId() {
        return objectId;
    }

    public JSONObject toJSON() {
        if (jsonObject == null) {
            initFromValue();
            jsonObject = createJSON();
        }
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

    public IndexRange getIndexRange() {
        return indexRange;
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

    public static final class IndexRange {
        private int start;
        private int end;
        private boolean named;

        public IndexRange(int start, int end, boolean named) {
            this.start = start;
            this.end = end;
            this.named = named;
        }

        public boolean isNamed() {
            return named;
        }

        public int start() {
            return start;
        }

        public int end() {
            return end;
        }
    }
}
