/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;

final class JavaObject implements TruffleObject {

    static final JavaObject NULL = new JavaObject(null, Object.class, null);

    final Object obj;
    final Class<?> clazz;
    final Object languageContext;

    JavaObject(Object obj, Class<?> clazz, Object languageContext) {
        this.obj = obj;
        this.clazz = clazz;
        this.languageContext = languageContext;
    }

    static JavaObject forClass(Class<?> clazz, Object languageContext) {
        return new JavaObject(null, clazz, languageContext);
    }

    static boolean isInstance(TruffleObject obj) {
        return obj instanceof JavaObject;
    }

    static boolean isJavaInstance(Class<?> targetType, TruffleObject javaObject) {
        if (javaObject instanceof JavaObject) {
            final Object value = valueOf(javaObject);
            return targetType.isInstance(value);
        } else {
            return false;
        }
    }

    static Object valueOf(TruffleObject value) {
        final JavaObject obj = (JavaObject) value;
        if (obj.isClass()) {
            return obj.clazz;
        } else {
            return obj.obj;
        }
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return JavaObjectMessageResolutionForeign.ACCESS;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(obj);
    }

    boolean isClass() {
        return NULL != this && obj == null;
    }

    boolean isArray() {
        return obj != null && obj.getClass().isArray();
    }

    Class<?> getClazz() {
        return clazz;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof JavaObject) {
            JavaObject other = (JavaObject) o;
            return this.obj == other.obj && clazz == other.clazz && this.languageContext == other.languageContext;
        }
        return false;
    }

    @Override
    public String toString() {
        if (obj == NULL) {
            return "null";
        }
        if (isClass()) {
            return "JavaClass[" + clazz.getTypeName() + "]";
        }
        return "JavaObject[" + obj + " (" + clazz.getTypeName() + ")" + "]";
    }
}
