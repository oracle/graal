/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.tools.chromeinspector.LanguageChecks;
import com.oracle.truffle.tools.chromeinspector.types.RemoteObject.TypeMark;

import static com.oracle.truffle.tools.chromeinspector.types.RemoteObject.getMetaObject;

/**
 * Collects value type information.
 */
public final class TypeInfo {

    public enum TYPE {

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

    public enum SUBTYPE {

        ARRAY("array"),
        NULL("null"),
        DATE("date"),
        MAP("map"),
        SET("set"),
        ITERATOR("iterator"),
        INTERNAL_ENTRY("internal#entry");

        private final String id;

        SUBTYPE(String id) {
            this.id = id;
        }

        String getId() {
            return id;
        }
    }

    final TYPE type;
    final SUBTYPE subtype;
    final String className;
    final String descriptionType;
    final boolean isObject;
    final boolean isFunction;
    final boolean isNull;
    final boolean isJS;

    TypeInfo(TYPE type, SUBTYPE subtype, String className, String descriptionType, boolean isObject, boolean isFunction, boolean isNull, boolean isJS) {
        this.type = type;
        this.subtype = subtype;
        this.className = className;
        this.descriptionType = descriptionType;
        this.isObject = isObject;
        this.isFunction = isFunction;
        this.isNull = isNull;
        this.isJS = isJS;
    }

    static TypeInfo fromValue(DebugValue debugValue, TypeMark typeMark, LanguageInfo originalLanguage, PrintWriter err) {
        DebugValue metaObject = getMetaObject(debugValue, originalLanguage, err);
        boolean isObject = isObject(debugValue, err);
        TYPE type;
        SUBTYPE subtype = null;
        String className = null;
        boolean isJS = LanguageChecks.isJS(originalLanguage);
        String descriptionType = null;
        String metaType = null;
        if (metaObject != null) {
            metaType = RemoteObject.toMetaName(metaObject, err);
        }
        type = getType(debugValue, typeMark, metaType, isObject);
        if (typeMark != null) {
            switch (typeMark) {
                case MAP_ENTRIES:
                    subtype = SUBTYPE.ARRAY;
                    break;
                case MAP_ENTRY:
                    subtype = SUBTYPE.INTERNAL_ENTRY;
                    break;
                default:
                    throw new UnsupportedOperationException(typeMark.name());
            }
        } else if (debugValue.isArray()) {
            subtype = SUBTYPE.ARRAY;
        } else if (debugValue.hasHashEntries()) {
            subtype = SUBTYPE.MAP;
        } else if (debugValue.isIterator()) {
            subtype = SUBTYPE.ITERATOR;
        }
        boolean isFunction = debugValue.canExecute();
        boolean isNull = false;
        if (isFunction) {
            type = TYPE.FUNCTION;
            className = metaType;
        } else if (isObject || TYPE.OBJECT.equals(type)) {
            className = metaType;
            isNull = debugValue.isNull();
            if (isNull) {
                subtype = SUBTYPE.NULL;
                className = null;
            } else if (debugValue.isDate()) {
                subtype = SUBTYPE.DATE;
            }
        } else {
            className = null;
            if (TYPE.OBJECT.equals(type)) {
                descriptionType = metaType;
            }
        }
        if (subtype != null) {
            // Whatever the type was set to, subtype is defined for object only
            type = TYPE.OBJECT;
        }
        if (descriptionType == null) {
            descriptionType = className;
        }
        return new TypeInfo(type, subtype, className, descriptionType, isObject, isFunction, isNull, isJS);
    }

    static boolean isObject(DebugValue debugValue, PrintWriter err) {
        boolean isObject;
        try {
            isObject = debugValue.getProperties() != null || debugValue.canExecute() || debugValue.isArray() || debugValue.hasHashEntries() || debugValue.isIterator();
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
    private static TYPE getType(DebugValue value, TypeMark typeMark, String metaObject, boolean isObject) {
        if (typeMark != null) {
            switch (typeMark) {
                case MAP_ENTRIES:
                case MAP_ENTRY:
                    return TYPE.OBJECT;
                default:
                    throw new UnsupportedOperationException(typeMark.name());
            }
        }
        if (metaObject != null) {
            for (TYPE type : TYPE.values()) {
                if (!TYPE.OBJECT.equals(type) && metaObject.equalsIgnoreCase(type.getId())) {
                    return type;
                }
            }
        }
        if (value.isString()) {
            return TYPE.STRING;
        }
        if (!isObject && value.isNumber()) {
            return TYPE.NUMBER;
        }
        if (!isObject && value.isBoolean()) {
            return TYPE.BOOLEAN;
        }
        return TYPE.OBJECT;
    }

    static Number toNumber(DebugValue value) {
        if (value.fitsInLong()) {
            return value.asLong();
        }
        if (value.fitsInDouble()) {
            return value.asDouble();
        }
        throw new IllegalArgumentException("Not a number: " + value.toDisplayString(false));
    }
}
