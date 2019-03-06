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
package com.oracle.truffle.tools.chromeinspector.objects;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

/**
 * TruffleObject of a JSON object.
 */
public final class JSONTruffleObject extends AbstractInspectorObject {

    private final JSONObject json;
    private final TruffleObject keys;
    @CompilationFinal(dimensions = 1) private volatile String[] names;

    public JSONTruffleObject(JSONObject json) {
        this.json = json;
        this.keys = new JSONKeys(this);
    }

    @Override
    protected TruffleObject getMembers(boolean includeInternal) {
        return keys;
    }

    @Override
    @TruffleBoundary
    protected boolean isField(String name) {
        return json.has(name);
    }

    @Override
    protected boolean isMethod(String name) {
        return false;
    }

    private String[] getNames() {
        if (names == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            names = json.keySet().toArray(new String[json.length()]);
        }
        return names;
    }

    @Override
    @TruffleBoundary
    protected Object getFieldValueOrNull(String name) {
        Object value = json.opt(name);
        if (value != null) {
            value = getTruffleValueFromJSONValue(value);
        }
        return value;
    }

    @Override
    protected Object invokeMember(String name, Object[] arguments) throws UnknownIdentifierException {
        CompilerDirectives.transferToInterpreter();
        throw UnknownIdentifierException.create(name);
    }

    static Object getTruffleValueFromJSONValue(Object value) {
        CompilerAsserts.neverPartOfCompilation();
        if (value instanceof JSONObject) {
            return new JSONTruffleObject((JSONObject) value);
        } else if (value instanceof JSONArray) {
            return new JSONTruffleArray((JSONArray) value);
        } else {
            return value;
        }
    }

    static final class JSONKeys extends AbstractInspectorArray {

        private final JSONTruffleObject obj;

        JSONKeys(JSONTruffleObject obj) {
            this.obj = obj;
        }

        @Override
        int getArraySize() {
            return obj.getNames().length;
        }

        @Override
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            String[] allNames = obj.getNames();
            if (index < 0 || index >= allNames.length) {
                CompilerDirectives.transferToInterpreter();
                throw InvalidArrayIndexException.create(index);
            }
            return allNames[(int) index];
        }
    }

}
