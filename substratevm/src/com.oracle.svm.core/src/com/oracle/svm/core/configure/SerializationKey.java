/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Alibaba Group Holding Limited. All Rights Reserved.
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
package com.oracle.svm.core.configure;

import java.util.Arrays;

public class SerializationKey<T> {
    private T serializationTargetClass;
    private T[] parameterTypes;
    private T[] checkedExceptions;
    private int modifiers;
    private T targetConstructorClass;
    private String flattenString;

    public T getSerializationTargetClass() {
        return serializationTargetClass;
    }

    public T[] getParameterTypes() {
        return parameterTypes;
    }

    public T[] getCheckedExceptions() {
        return checkedExceptions;
    }

    public int getModifiers() {
        return modifiers;
    }

    public T getTargetConstructorClass() {
        return targetConstructorClass;
    }

    public SerializationKey(T serializationTargetClass, T[] parameterTypes, T[] checkedExceptions, int modifiers, T targetConstructorClass) {
        this.serializationTargetClass = serializationTargetClass;
        this.parameterTypes = parameterTypes;
        this.checkedExceptions = checkedExceptions;
        this.modifiers = modifiers;
        this.targetConstructorClass = targetConstructorClass;

        StringBuilder sb = new StringBuilder();
        sb.append("serializationTargetClass:").append(getStringValue(serializationTargetClass)).append("\n");
        sb.append("parameterTypes:").append(parameterTypes.length).append(".");
        Arrays.stream(parameterTypes).forEach(c -> sb.append(getStringValue(c)).append(";"));
        sb.append("\n");
        sb.append("checkedExceptions:").append(checkedExceptions.length).append(".");
        Arrays.stream(checkedExceptions).forEach(c -> sb.append(getStringValue(c)).append(";"));
        sb.append("\n");
        sb.append("modifiers:").append(modifiers).append("\n");
        sb.append("targetConstructorClass:").append(getStringValue(targetConstructorClass)).append("\n");
        flattenString = sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SerializationKey) {
            SerializationKey<?> that = SerializationKey.class.cast(obj);
            return this.flattenString.equals(that.flattenString);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return flattenString;
    }

    @Override
    public int hashCode() {
        return flattenString.hashCode();
    }

    private String getStringValue(T t) {
        if (t instanceof Class) {
            return ((Class<?>) t).getName();
        } else if (t instanceof String) {
            return (String) t;
        } else {
            throw new RuntimeException("SerializeKey<T> should be either Class of String, but is " + t.getClass());
        }
    }

    public String asKey() {
        return flattenString;
    }
}
