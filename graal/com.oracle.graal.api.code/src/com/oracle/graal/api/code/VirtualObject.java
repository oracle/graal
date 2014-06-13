/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.api.meta.MetaUtil.*;

import java.util.*;

import com.oracle.graal.api.meta.*;

/**
 * An instance of this class represents an object whose allocation was removed by escape analysis.
 * The information stored in the {@link VirtualObject} is used during deoptimization to recreate the
 * object.
 */
public final class VirtualObject extends Value {

    private static final long serialVersionUID = -2907197776426346021L;

    private final ResolvedJavaType type;
    private Value[] values;
    private final int id;

    /**
     * Creates a new {@link VirtualObject} for the given type, with the given fields. If
     * {@code type} is an instance class then {@code values} provides the values for the fields
     * returned by {@link ResolvedJavaType#getInstanceFields(boolean) getInstanceFields(true)}. If
     * {@code type} is an array then the length of the values array determines the reallocated array
     * length.
     *
     * @param type the type of the object whose allocation was removed during compilation. This can
     *            be either an instance of an array type.
     * @param values an array containing all the values to be stored into the object when it is
     *            recreated
     * @param id a unique id that identifies the object within the debug information for one
     *            position in the compiled code.
     * @return a new {@link VirtualObject} instance.
     */
    public static VirtualObject get(ResolvedJavaType type, Value[] values, int id) {
        return new VirtualObject(type, values, id);
    }

    private VirtualObject(ResolvedJavaType type, Value[] values, int id) {
        super(LIRKind.reference(Kind.Object));
        this.type = type;
        this.values = values;
        this.id = id;
    }

    private static StringBuilder appendValue(StringBuilder buf, Value value, Set<VirtualObject> visited) {
        if (value instanceof VirtualObject) {
            VirtualObject vo = (VirtualObject) value;
            buf.append("vobject:").append(toJavaName(vo.type, false)).append(':').append(vo.id);
            if (!visited.contains(vo)) {
                visited.add(vo);
                buf.append('{');
                if (vo.values == null) {
                    buf.append("<uninitialized>");
                } else {
                    if (vo.type.isArray()) {
                        for (int i = 0; i < vo.values.length; i++) {
                            if (i != 0) {
                                buf.append(',');
                            }
                            buf.append(i).append('=');
                            appendValue(buf, vo.values[i], visited);
                        }
                    } else {
                        ResolvedJavaField[] fields = vo.type.getInstanceFields(true);
                        assert fields.length == vo.values.length : vo.type + ", fields=" + Arrays.toString(fields) + ", values=" + Arrays.toString(vo.values);
                        for (int i = 0; i < vo.values.length; i++) {
                            if (i != 0) {
                                buf.append(',');
                            }
                            buf.append(fields[i].getName()).append('=');
                            appendValue(buf, vo.values[i], visited);
                        }
                    }
                }
                buf.append('}');
            }
        } else {
            buf.append(value);
        }
        return buf;
    }

    @Override
    public String toString() {
        Set<VirtualObject> visited = Collections.newSetFromMap(new IdentityHashMap<VirtualObject, Boolean>());
        return appendValue(new StringBuilder(), this, visited).toString();
    }

    /**
     * Returns the type of the object whose allocation was removed during compilation. This can be
     * either an instance of an array type.
     */
    public ResolvedJavaType getType() {
        return type;
    }

    /**
     * Returns an array containing all the values to be stored into the object when it is recreated.
     */
    public Value[] getValues() {
        return values;
    }

    /**
     * Returns the unique id that identifies the object within the debug information for one
     * position in the compiled code.
     */
    public int getId() {
        return id;
    }

    private static boolean checkValues(ResolvedJavaType type, Value[] values) {
        if (values != null) {
            if (!type.isArray()) {
                ResolvedJavaField[] fields = type.getInstanceFields(true);
                int fieldIndex = 0;
                for (int i = 0; i < values.length; i++) {
                    ResolvedJavaField field = fields[fieldIndex++];
                    Kind valKind = values[i].getKind().getStackKind();
                    if ((valKind == Kind.Double || valKind == Kind.Long) && field.getKind() == Kind.Int) {
                        assert fields[fieldIndex].getKind() == Kind.Int;
                        fieldIndex++;
                    } else {
                        assert valKind == field.getKind().getStackKind() : field + ": " + valKind + " != " + field.getKind().getStackKind();
                    }
                }
                assert fields.length == fieldIndex : type + ": fields=" + Arrays.toString(fields) + ", field values=" + Arrays.toString(values);
            } else {
                Kind componentKind = type.getComponentType().getKind().getStackKind();
                for (int i = 0; i < values.length; i++) {
                    assert values[i].getKind().getStackKind() == componentKind || componentKind.getBitCount() >= values[i].getKind().getStackKind().getBitCount() : values[i].getKind() + " != " +
                                    componentKind;
                }
            }

        }
        return true;
    }

    /**
     * Overwrites the current set of values with a new one.
     *
     * @param values an array containing all the values to be stored into the object when it is
     *            recreated.
     */
    public void setValues(Value[] values) {
        assert checkValues(type, values);
        this.values = values;
    }

    @Override
    public int hashCode() {
        return getLIRKind().hashCode() + type.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof VirtualObject) {
            VirtualObject l = (VirtualObject) o;
            if (!l.type.equals(type) || l.values.length != values.length) {
                return false;
            }
            for (int i = 0; i < values.length; i++) {
                if (!Objects.equals(values[i], l.values[i])) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
