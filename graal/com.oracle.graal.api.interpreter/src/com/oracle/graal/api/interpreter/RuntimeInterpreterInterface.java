/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.interpreter;

import com.oracle.graal.api.meta.*;

/**
 * Please note: The parameters of the interface are currently in reversed order since it was derived
 * from the java ByteCodeInterpreter implementation. There it was simpler to use the parameters in
 * reversed order since they are popped from the stack in reversed order.
 */
public interface RuntimeInterpreterInterface {

    Object invoke(ResolvedJavaMethod method, Object... args);

    void monitorEnter(Object value);

    void monitorExit(Object value);

    Object newObject(ResolvedJavaType type) throws InstantiationException;

    Object getFieldObject(Object base, ResolvedJavaField field);

    boolean getFieldBoolean(Object base, ResolvedJavaField field);

    byte getFieldByte(Object base, ResolvedJavaField field);

    char getFieldChar(Object base, ResolvedJavaField field);

    short getFieldShort(Object base, ResolvedJavaField field);

    int getFieldInt(Object base, ResolvedJavaField field);

    long getFieldLong(Object base, ResolvedJavaField field);

    double getFieldDouble(Object base, ResolvedJavaField field);

    float getFieldFloat(Object base, ResolvedJavaField field);

    void setFieldObject(Object value, Object base, ResolvedJavaField field);

    void setFieldInt(int value, Object base, ResolvedJavaField field);

    void setFieldFloat(float value, Object base, ResolvedJavaField field);

    void setFieldDouble(double value, Object base, ResolvedJavaField field);

    void setFieldLong(long value, Object base, ResolvedJavaField field);

    byte getArrayByte(long index, Object array);

    char getArrayChar(long index, Object array);

    short getArrayShort(long index, Object array);

    int getArrayInt(long index, Object array);

    long getArrayLong(long index, Object array);

    double getArrayDouble(long index, Object array);

    float getArrayFloat(long index, Object array);

    Object getArrayObject(long index, Object array);

    void setArrayByte(byte value, long index, Object array);

    void setArrayChar(char value, long index, Object array);

    void setArrayShort(short value, long index, Object array);

    void setArrayInt(int value, long index, Object array);

    void setArrayLong(long value, long index, Object array);

    void setArrayFloat(float value, long index, Object array);

    void setArrayDouble(double value, long index, Object array);

    void setArrayObject(Object value, long index, Object array);

    Class<?> getMirror(ResolvedJavaType type);
}
