/*
 * Copyright (c) 2009, 2023, Oracle and/or its affiliates. All rights reserved.
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
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.HashMap;


/**
 * Manages mutable objects in the interpreter by creating a wrapper around a standard Java object.
 */
public class InterpreterValueMutableObject extends InterpreterValueObject {

    private static Unsafe unsafe = GraalUnsafeAccess.getUnsafe();
    private final Object nativeObject;
    private final JVMContext jvmContext;
    private final InterpreterValueFactory valueFactory;
    private final HashMap<ResolvedJavaField, VarHandle> fieldCache;

    public InterpreterValueMutableObject(JVMContext jvmContext,
                                         InterpreterValueFactory valueFactory, ResolvedJavaType type) {
        super(type);
        this.fieldCache = new HashMap<>();
        this.jvmContext = jvmContext;
        this.valueFactory = valueFactory;
        try {
            // Load the class of the native object using the class loader of GraalInterpreterTest.
            Class<?> clazz = Class.forName(type.toJavaName(), true, jvmContext.getClassLoader());
            nativeObject = unsafe.allocateInstance(clazz);
        } catch (InstantiationException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public InterpreterValueMutableObject(JVMContext jvmContext,
                                         InterpreterValueFactory valueFactory, ResolvedJavaType type, Object obj) {
        super(type);
        this.fieldCache = new HashMap<>();
        this.jvmContext = jvmContext;
        this.valueFactory = valueFactory;
        if (type.isPrimitive()) {
            obj = coerceUpToInt(obj);
        }
        this.nativeObject = obj;
    }

    /**
     * This helper method gets the VarHandle of the given field.
     */
    private VarHandle getFieldVarHandle(ResolvedJavaField field) throws NoSuchFieldException, IllegalAccessException {
        VarHandle handle = fieldCache.get(field);
        if (handle != null) {
            return handle;
        }
        try {
            Class<?> clazz = Class.forName(field.getDeclaringClass().toJavaName(), true, jvmContext.getClassLoader());
            Class<?> fieldClass = getTypeClass(jvmContext, field.getType());
            handle = MethodHandles.privateLookupIn(clazz, jvmContext.getLookup()).findVarHandle(clazz, field.getName(), fieldClass);
            fieldCache.put(field, handle);
            return handle;
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
            return false;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setFieldValue(ResolvedJavaField field, InterpreterValue value) {
        try {
            JavaKind expected = field.getJavaKind();
            JavaKind actual = value.getJavaKind();
            if (expected == JavaKind.Boolean && actual == JavaKind.Int) {
                boolean v = (int) ((Integer) value.asObject()) != 0;
                getFieldVarHandle(field).set(nativeObject, v);
            } else {
                if (expected != actual && value.isPrimitive()) {
                    value = ((InterpreterValuePrimitive) value).coerce(expected);
                    System.out.printf("\nsetField %s RHS coerced from type %s to %s: %s\n",
                           field.getName(), actual, expected, value);
                }
                getFieldVarHandle(field).set(nativeObject, value.asObject());
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public InterpreterValue getFieldValue(ResolvedJavaField field) {
        try {
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

