/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.vm;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;

final class ConvertedObject implements TruffleObject {
    private static final TruffleObject NULL = JavaInterop.asTruffleObject(null);

    private final TruffleObject original;
    private final Object value;

    ConvertedObject(TruffleObject obj, Object newValue) {
        this.original = obj;
        this.value = newValue == null ? NULL : newValue;
    }

    static Object original(Object obj) {
        if (obj instanceof ConvertedObject) {
            return ((ConvertedObject) obj).original;
        }
        return obj;
    }

    static Object value(Object obj) {
        if (obj instanceof ConvertedObject) {
            return ((ConvertedObject) obj).value;
        }
        return obj;
    }

    static boolean isNull(Object result) {
        return NULL == result;
    }

    static <T> boolean isInstance(Class<T> representation, Object obj) {
        if (representation.isInstance(obj)) {
            return true;
        }
        if (obj instanceof ConvertedObject) {
            return representation.isInstance(((ConvertedObject) obj).value);
        }
        return false;
    }

    static <T> T cast(Class<T> representation, Object obj) {
        if (obj instanceof ConvertedObject) {
            return representation.cast(((ConvertedObject) obj).value);
        }
        return representation.cast(obj);
    }

    @Override
    public ForeignAccess getForeignAccess() {
        throw new IllegalStateException();
    }
}
