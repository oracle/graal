/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.api.code;

import com.oracle.graal.api.meta.*;

/**
 * An instance of this class represents an object whose allocation was removed by escape analysis. The information stored in the {@link VirtualObject} is used during
 * deoptimization to recreate the object.
 */
public final class VirtualObject extends Value {
    private static final long serialVersionUID = -2907197776426346021L;

    private final JavaType type;
    private Value[] values;
    private final int id;

    /**
     * Creates a new {@link VirtualObject} for the given type, with the given fields. If the type is an instance class
     * then the values array needs to have one entry for each field, ordered in like the fields returned by
     * {@link ResolvedJavaType#declaredFields()}. If the type is an array then the length of the values array determines
     * the reallocated array length.
     *
     * @param type the type of the object whose allocation was removed during compilation. This can be either an
     *            instance of an array type.
     * @param values an array containing all the values to be stored into the object when it is recreated.
     * @param id a unique id that identifies the object within the debug information for one position in the compiled
     *            code.
     * @return a new {@link VirtualObject} instance.
     */
    public static VirtualObject get(JavaType type, Value[] values, int id) {
        return new VirtualObject(type, values, id);
    }

    private VirtualObject(JavaType type, Value[] values, int id) {
        super(Kind.Object);
        this.type = type;
        this.values = values;
        this.id = id;
    }

    @Override
    public String toString() {
        return "vobject:" + id;
    }

    /**
     * @return the type of the object whose allocation was removed during compilation. This can be either an instance of an array type.
     */
    public JavaType type() {
        return type;
    }

    /**
     * @return an array containing all the values to be stored into the object when it is recreated.
     */
    public Value[] values() {
        return values;
    }

    /**
     * @return the unique id that identifies the object within the debug information for one position in the compiled code.
     */
    public int id() {
        return id;
    }

    /**
     * Overwrites the current set of values with a new one.
     * @param values an array containing all the values to be stored into the object when it is recreated.
     */
    public void setValues(Value[] values) {
        this.values = values;
    }

    @Override
    public int hashCode() {
        return kind.ordinal() + type.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof VirtualObject) {
            VirtualObject l = (VirtualObject) o;
            if (l.type != type || l.values.length != values.length) {
                return false;
            }
            for (int i = 0; i < values.length; i++) {
                if (values[i] != l.values[i]) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
