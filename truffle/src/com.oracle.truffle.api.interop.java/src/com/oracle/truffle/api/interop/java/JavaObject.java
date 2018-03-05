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

    static final JavaObject NULL = new JavaObject(null, null);

    final Object obj;
    final Object languageContext;

    private JavaObject(Object obj, Object languageContext) {
        this.obj = obj;
        this.languageContext = languageContext;
    }

    static JavaObject forClass(Class<?> clazz, Object languageContext) {
        assert clazz != null;
        return new JavaObject(clazz, languageContext);
    }

    static JavaObject forObject(Object object, Object languageContext) {
        assert object != null && !(object instanceof Class<?>);
        return new JavaObject(object, languageContext);
    }

    static boolean isInstance(TruffleObject obj) {
        return obj instanceof JavaObject;
    }

    static boolean isJavaInstance(Class<?> targetType, Object javaObject) {
        if (javaObject instanceof JavaObject) {
            final Object value = valueOf((JavaObject) javaObject);
            return targetType.isInstance(value);
        } else {
            return false;
        }
    }

    static Object valueOf(TruffleObject value) {
        final JavaObject obj = (JavaObject) value;
        return obj.obj;
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
        return obj instanceof Class<?>;
    }

    boolean isArray() {
        return obj != null && obj.getClass().isArray();
    }

    boolean isNull() {
        return obj == null;
    }

    Class<?> getObjectClass() {
        return obj == null ? null : obj.getClass();
    }

    Class<?> getLookupClass() {
        if (obj == null) {
            return null;
        } else if (obj instanceof Class) {
            return (Class<?>) obj;
        } else {
            return obj.getClass();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof JavaObject) {
            JavaObject other = (JavaObject) o;
            return this.obj == other.obj && this.languageContext == other.languageContext;
        }
        return false;
    }

    @Override
    public String toString() {
        if (obj == null) {
            return "null";
        }
        if (isClass()) {
            return "JavaClass[" + getLookupClass().getTypeName() + "]";
        }
        return "JavaObject[" + obj + " (" + getLookupClass().getTypeName() + ")" + "]";
    }
}
