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
package com.oracle.truffle.tools.chromeinspector.types;

import java.io.PrintWriter;

import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.tools.chromeinspector.LanguageChecks;

import static com.oracle.truffle.tools.chromeinspector.types.RemoteObject.getMetaObject;

/**
 * Collects value type information.
 */
final class TypeInfo {

    enum TYPE {

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

    final String type;
    final String subtype;
    final String className;
    final String descriptionType;
    final boolean isObject;
    final boolean isFunction;
    final boolean isNull;
    final boolean isJS;

    TypeInfo(String type, String subtype, String className, String descriptionType, boolean isObject, boolean isFunction, boolean isNull, boolean isJS) {
        this.type = type;
        this.subtype = subtype;
        this.className = className;
        this.descriptionType = descriptionType;
        this.isObject = isObject;
        this.isFunction = isFunction;
        this.isNull = isNull;
        this.isJS = isJS;
    }

    static TypeInfo fromValue(DebugValue debugValue, LanguageInfo originalLanguage, PrintWriter err) {
        DebugValue metaObject = getMetaObject(debugValue, originalLanguage, err);
        boolean isObject = isObject(debugValue, err);
        String type = null;
        String subtype = null;
        String className = null;
        boolean isJS = LanguageChecks.isJS(originalLanguage);
        String descriptionType = null;
        String metaType = null;
        if (metaObject != null) {
            metaType = RemoteObject.toMetaName(metaObject, err);
        }
        type = getType(debugValue, metaType, isObject);
        if (debugValue.isArray()) {
            subtype = "array";
        }
        boolean isFunction = debugValue.canExecute();
        boolean isNull = false;
        if (isFunction) {
            type = TYPE.FUNCTION.getId();
            className = metaType;
        } else if (isObject || TYPE.OBJECT.getId().equals(type)) {
            className = metaType;
            isNull = debugValue.isNull();
            if (isNull) {
                subtype = "null";
                className = null;
            } else if (debugValue.isDate()) {
                subtype = "date";
            }
        } else {
            className = null;
            if (TYPE.OBJECT.getId().equals(type)) {
                descriptionType = metaType;
            }
        }
        if (descriptionType == null) {
            descriptionType = className;
        }
        return new TypeInfo(type, subtype, className, descriptionType, isObject, isFunction, isNull, isJS);
    }

    static boolean isObject(DebugValue debugValue, PrintWriter err) {
        boolean isObject;
        try {
            isObject = debugValue.getProperties() != null || debugValue.canExecute() || debugValue.isArray();
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
    private static String getType(DebugValue value, String metaObject, boolean isObject) {
        if (metaObject != null) {
            for (TYPE type : TYPE.values()) {
                if (!TYPE.OBJECT.equals(type) && metaObject.equalsIgnoreCase(type.getId())) {
                    return type.getId();
                }
            }
        }
        if (!isObject && value.isNumber()) {
            return TYPE.NUMBER.getId();
        }
        if (!isObject && value.isBoolean()) {
            return TYPE.BOOLEAN.getId();
        }
        return TYPE.OBJECT.getId();
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
