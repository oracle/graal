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

import static com.oracle.truffle.tools.chromeinspector.objects.JSONTruffleObject.getTruffleValueFromJSONValue;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.tools.utils.json.JSONArray;

/**
 * TruffleObject of a JSON array.
 */
public final class JSONTruffleArray extends AbstractInspectorArray {

    private final JSONArray json;

    public JSONTruffleArray(JSONArray json) {
        this.json = json;
    }

    @Override
    int getArraySize() {
        return json.length();
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    Object readArrayElement(long index) throws InvalidArrayIndexException {
        if (index < 0 || index >= json.length()) {
            throw InvalidArrayIndexException.create(index);
        }
        Object value = json.get((int) index);
        return getTruffleValueFromJSONValue(value);
    }

}
