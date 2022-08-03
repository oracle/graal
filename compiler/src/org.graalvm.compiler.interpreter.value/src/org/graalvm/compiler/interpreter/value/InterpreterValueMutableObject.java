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
            // Load the class of the native object using the class loader of GraalInterpreterTest.
            // 使用 GraalInterpreterTest 这个类的 class loader 来加载 GraalInterpreterTest 里面的测试类，
            // 这样就可以跨 module 加载类。如果使用 InterpterValueMutableObject 本身的 class loader 来加载
            // GraalInterpterTest 的话，就无法加载，会抛出 ClassNotFoundException 异常，因为这两个类属于不同的 module。
            Class<?> clazz = Class.forName(type.toJavaName(), true, jvmContext.getClassLoader());
            // Construct the native object by reflection.
            // 使用反射创建一个 native object。
            nativeObject = clazz.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        // 没必要初始化 field，因为 JVM 会自动给创建的 native object 初始化 field
        // for (ResolvedJavaField field : type.getInstanceFields(true)) {
        //     this.instanceFields.put(field, InterpreterValue.createDefaultOfKind(getStorageKind.apply(field.getType())));
        // }
    }

    public InterpreterValueMutableObject(ResolvedJavaType type, Object obj) {
        super(type);

        nativeObject = obj;
    }

    /**
     * This helper method get the VarHandle of the given field.
     * 获取指定 field 的 VarHandle
     */
    private static VarHandle getFieldVarHandle(JVMContext jvmContext, ResolvedJavaField field) throws NoSuchFieldException, IllegalAccessException {
        try {
            // Load the class of the native object.
            // 加载 field 所属的 object 的 class
            Class<?> clazz = Class.forName(field.getDeclaringClass().toJavaName(), true, jvmContext.getClassLoader());
            // Get the Class of the field.
            // 加载 field 的 class，field 的类型可能是基础类型，或者数组，这时候不能使用 
            // Class.forName() 来加载类，所以用一个额外的方法 getTypeClass() 来获取 class
            Class<?> fieldClass = getTypeClass(jvmContext, field.getType());
            // NOTE needs privateLookupIn() to access private fields
            // 需要使用 privateLookupIn() 来访问 private field
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
            // If the given field does not exist, NoSuchField will be thrown.
            // 如果 field 不存在，findVarHandle() 会抛出 NoSuchFieldException 异常，捕获到这个异常然后返回 false
            return false;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setFieldValue(JVMContext jvmContext, ResolvedJavaField field, InterpreterValue value) {
        try {
            // NOTE Boolean literal values are represented as integers in IR,
            // so we need to handle this situation.
            // IR里会把 true/false 表示成整数 1/0，所以在interpter运行时会发生这样的情况：
            // 给boolean类型的变量赋值整数0或1。这里就是处理这种情况，需要把0或1分别转换成false或true。
            if (field.getJavaKind() == JavaKind.Boolean && value.getJavaKind() == JavaKind.Int) {
                boolean v = (int) ((Integer) value.asObject()) != 0;
                getFieldVarHandle(jvmContext, field).set(nativeObject, v);
            } else {
                // call InterpreterValue.asObject() to convert an InterpreterValue into a native object.
                // 调用 InterpterValue 的 asObject 方法把 InterpterValue 转换为 native object，
                // 然后调用 VarHandle 的 set 方法设置 native object 里面的 field 值。
                getFieldVarHandle(jvmContext, field).set(nativeObject, value.asObject());
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public InterpreterValue getFieldValue(JVMContext jvmContext, InterpreterValueFactory valueFactory, ResolvedJavaField field) {
        try {
            // call InterpreterValueFactory.createFromObject() to convert a native object into an InterpreterValue.
            // 调用 InterpterValueFactory 的 createFromObject 方法把 native object 转换为 InterpterValue，
            // 然后调用 VarHandle 的 get 方法获取 native object 里的 field 值。
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
