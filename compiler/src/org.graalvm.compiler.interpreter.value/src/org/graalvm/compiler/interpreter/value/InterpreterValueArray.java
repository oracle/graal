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
import jdk.vm.ci.meta.ResolvedJavaType;
import java.lang.reflect.Array;

public class InterpreterValueArray extends InterpreterValue {
    private final ResolvedJavaType componentType;
    private final int length;
    // NOTE We cannot use Object[] because primitive arrays like int[] cannot be
    // case into Object[].
    // 类型不能用 Object[]，因为基础类型的数组不能转换为 Object[]，比如 int[] 不能转换为 Object[]
    private final Object contents;

    public InterpreterValueArray(JVMContext jvmContext, ResolvedJavaType componentType, Object nativeArray) {
        this.componentType = componentType;
        this.contents = nativeArray;
        this.length = Array.getLength(nativeArray);
    }

    public InterpreterValueArray(JVMContext jvmContext, ResolvedJavaType componentType, int length) {
        if (length < 0) {
            throw new IllegalArgumentException("Negative array length");
        }

        this.componentType = componentType;
        this.length = length;
            switch (componentType.getJavaKind()) {
            case Boolean:
                contents = new boolean[length];
                break;
            case Byte:
                contents = new byte[length];
                break;
            case Char:
                contents = new char[length];
                break;
            case Short:
                contents = new short[length];
                break;
            case Int:
                contents = new int[length];
                break;
            case Long:
                contents = new long[length];
                break;
            case Float:
                contents = new float[length];
                break;
            case Double:
                contents = new double[length];
                break;
            default:
                try {
                    Class<?> clazz = getTypeClass(jvmContext, componentType);
                    contents = (Object[]) Array.newInstance(clazz, length);
                    break;
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        // NOTE no need to populate elements because JVM will do it for us.
        // 没必要初始化数组元素的值，因为 JVM 默认会初始化新创建的数组的所有元素
        // if (populateDefault) {
        //     populateContentsWithDefaultValues(storageKind);
        // }
    }

    // private void populateContentsWithDefaultValues(JavaKind storageKind) {
    //     for (int i = 0; i < length; i++) {
    //         contents[i] = InterpreterValue.createDefaultOfKind(storageKind);
    //     }
    // }

    public int getLength() {
        return length;
    }

    @Override
    public ResolvedJavaType getObjectType() {
        return componentType.getArrayClass();
    }

    public ResolvedJavaType getComponentType() {
        return componentType;
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    @Override
    public boolean isNull() {
        return false;
    }

    @Override
    public Object asObject() {
        return contents;
    }

    @Override
    public boolean isArray() {
        return true;
    }

    public InterpreterValue getAtIndex(InterpreterValueFactory valueFactory, int index) {
        checkBounds(index);
        return valueFactory.createFromObject(Array.get(contents, index));
    }

    public void setAtIndex(int index, InterpreterValue value) {
        checkBounds(index);
        // TODO: should we bother checking type compatbilitity?
        // NOTE Boolean literal values are represented as integers in IR, so
        // we need to handle this situation.
        // IR里会把 true/false 表示成整数 1/0，所以在interpter运行时会发生这样的情况：
        // 给boolean类型的变量赋值整数0或1。这里就是处理这种情况，需要把0或1分别转换成false或true。
        if (componentType.getJavaKind() == JavaKind.Boolean && value.getJavaKind() == JavaKind.Int) {
            ((boolean[]) contents)[index] = (int) ((Integer) value.asObject()) != 0;
        } else {
            Array.set(contents, index, value.asObject());
        }
    }

    private void checkBounds(int index) {
        if (index < 0 || index >= length) {
            throw new IllegalArgumentException("Invalid array access index");
        }
    }
}
