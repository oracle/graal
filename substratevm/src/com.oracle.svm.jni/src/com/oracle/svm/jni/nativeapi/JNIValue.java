/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni.nativeapi;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.PointerBase;

@CContext(JNIHeaderDirectives.class)
@CStruct(value = "jvalue") // a union, actually, but it behaves the same as a struct.
public interface JNIValue extends PointerBase {

    @CField("z")
    boolean getBoolean();

    @CField("z")
    void setBoolean(boolean z);

    @CField("b")
    byte getByte();

    @CField("b")
    void setByte(byte b);

    @CField("c")
    char getChar();

    @CField("c")
    void setChar(char c);

    @CField("s")
    short getShort();

    @CField("s")
    void setShort(short s);

    @CField("i")
    int getInt();

    @CField("i")
    void setInt(int i);

    @CField("j")
    long getLong();

    @CField("j")
    void setLong(long j);

    @CField("f")
    float getFloat();

    @CField("f")
    void setFloat(float f);

    @CField("d")
    double getDouble();

    @CField("d")
    void setDouble(double d);

    @CField("l")
    JNIObjectHandle getObject();

    @CField("l")
    void setObject(JNIObjectHandle l);

    /** @see org.graalvm.nativeimage.c.type.WordPointer#addressOf(int) */
    JNIValue addressOf(int index);
}
