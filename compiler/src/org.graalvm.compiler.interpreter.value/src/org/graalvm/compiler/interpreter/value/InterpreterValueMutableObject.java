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
    private final JVMContext jvmContext;
    private final InterpreterValueFactory valueFactory;

    public InterpreterValueMutableObject(JVMContext jvmContext, InterpreterValueFactory valueFactory, ResolvedJavaType type) {
        super(type);
        this.jvmContext = jvmContext;
        this.valueFactory = valueFactory;
        try {
            // Load the class of the native object using the class loader of GraalInterpreterTest.
            Class<?> clazz = Class.forName(type.toJavaName(), true, jvmContext.getClassLoader());
            // Construct the native object by reflection.
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            nativeObject = ctor.newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        // for (ResolvedJavaField field : type.getInstanceFields(true)) {
        //     this.instanceFields.put(field, InterpreterValue.createDefaultOfKind(getStorageKind.apply(field.getType())));
        // }
    }

    public InterpreterValueMutableObject(JVMContext jvmContext, InterpreterValueFactory valueFactory, ResolvedJavaType type, Object obj) {
        super(type);
        this.jvmContext = jvmContext;
        this.valueFactory = valueFactory;
        this.nativeObject = obj;
    }

    /**
     * This helper method get the VarHandle of the given field.
     */
    private VarHandle getFieldVarHandle(ResolvedJavaField field) throws NoSuchFieldException, IllegalAccessException {
        try {
            // Load the class of the native object.
            Class<?> clazz = Class.forName(field.getDeclaringClass().toJavaName(), true, jvmContext.getClassLoader());
            // Get the Class of the field.
            Class<?> fieldClass = getTypeClass(jvmContext, field.getType());
            // NOTE needs privateLookupIn() to access private fields
            return MethodHandles.privateLookupIn(clazz, jvmContext.getLookup()).findVarHandle(clazz, field.getName(), fieldClass);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean hasField(ResolvedJavaField field) {
        try {
            getFieldVarHandle(field);
            return true;
        } catch (NoSuchFieldException e) {
            // If the given field does not exist, NoSuchField will be thrown.
            return false;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setFieldValue(ResolvedJavaField field, InterpreterValue value) {
        try {
            // NOTE Boolean literal values are represented as integers in IR,
            // so we need to handle this situation.
            if (field.getJavaKind() == JavaKind.Boolean && value.getJavaKind() == JavaKind.Int) {
                boolean v = (int) ((Integer) value.asObject()) != 0;
                getFieldVarHandle(field).set(nativeObject, v);
            } else {
                // call InterpreterValue.asObject() to convert an InterpreterValue into a native object.
                getFieldVarHandle(field).set(nativeObject, value.asObject());
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public InterpreterValue getFieldValue(ResolvedJavaField field) {
        try {
            // call InterpreterValueFactory.createFromObject() to convert a native object into an InterpreterValue.
            return valueFactory.createFromObject(getFieldVarHandle(field).get(nativeObject));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object asObject() {
        return nativeObject;
    }
}
