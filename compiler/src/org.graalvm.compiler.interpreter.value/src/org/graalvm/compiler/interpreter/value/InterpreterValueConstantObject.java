/*
 * Copyright (c) 2009, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.interpreter.value;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.lang.reflect.Field;

/**
 * Portrays a read-only view of a real Java object.
 *
 * Fields can be read, but not written.
 */
public class InterpreterValueConstantObject extends InterpreterValueObject {
    final Object realObject;

    public InterpreterValueConstantObject(ResolvedJavaType type, Object value) {
        super(type);
        this.realObject = value;
    }

    @Override
    public boolean hasField(ResolvedJavaField field) {
        try {
            // TODO: try superclass fields too
            Field f = realObject.getClass().getDeclaredField(field.getName());
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    @Override
    public void setFieldValue(ResolvedJavaField field, InterpreterValue value) {
        throw new IllegalArgumentException("cannot set field of constant object: " + realObject);
    }

    @Override
    public InterpreterValue getFieldValue(ResolvedJavaField field) {
        try {
            Field f = realObject.getClass().getDeclaredField(field.getName());
            Object obj = f.get(this.realObject);
            if (obj instanceof Integer) {
                return InterpreterValuePrimitive.ofInt(((Integer) obj).intValue());
            } else if (obj instanceof Boolean) {
                return InterpreterValuePrimitive.ofBoolean(((Boolean) obj).booleanValue());
            } else {
                return new InterpreterValueConstantObject(this.type, obj);
            }
        } catch (NoSuchFieldException ex) {
            throw new RuntimeException(ex);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

}
