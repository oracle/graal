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

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.PrimitiveConstant;

import java.util.HashMap;
import java.util.function.Function;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
 
public class InterpreterValueMutableObject extends InterpreterValueObject {
    private final Object nativeObject;

    public InterpreterValueMutableObject(JVMContext jvmContext, ResolvedJavaType type) {
        super(type);
        try {
            Class<?> clazz = Class.forName(type.toJavaName(), true, jvmContext.getClassLoader());
            nativeObject = clazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        // for (ResolvedJavaField field : type.getInstanceFields(true)) {
        //     this.instanceFields.put(field, InterpreterValue.createDefaultOfKind(getStorageKind.apply(field.getType())));
        // }
    }

    public InterpreterValueMutableObject(ResolvedJavaType type, Object obj) {
        super(type);

        nativeObject = obj;
    }

    private static VarHandle getFieldVarHandle(JVMContext jvmContext, ResolvedJavaField field) throws NoSuchFieldException, IllegalAccessException {
        try {
            Class<?> clazz = Class.forName(field.getDeclaringClass().toJavaName(), true, jvmContext.getClassLoader());
            Class<?> fieldClass = getTypeClass(jvmContext, field.getType());
            return MethodHandles.privateLookupIn(clazz, jvmContext.getLookup()).findVarHandle(clazz, field.getName(), fieldClass);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean hasField(JVMContext jvmContext, ResolvedJavaField field) {
        try {
            getFieldVarHandle(jvmContext, field);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setFieldValue(JVMContext jvmContext, ResolvedJavaField field, InterpreterValue value) {
        try {
            if (field.getJavaKind() == JavaKind.Boolean && value.getJavaKind() == JavaKind.Int) {
                boolean v = (int) ((Integer) value.asObject()) != 0;
                getFieldVarHandle(jvmContext, field).set(nativeObject, v);
            } else {
                getFieldVarHandle(jvmContext, field).set(nativeObject, value.asObject());
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public InterpreterValue getFieldValue(JVMContext jvmContext, InterpreterValueFactory valueFactory, ResolvedJavaField field) {
        try {
            return valueFactory.createFromObject(getFieldVarHandle(jvmContext, field).get(nativeObject));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object asObject() {
        return nativeObject;
    }
}
