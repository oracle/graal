/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.reflect;

import static com.oracle.svm.core.util.VMError.unimplemented;

import java.lang.reflect.Field;
import java.lang.reflect.Member;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;

/**
 * This class provides a "fake" constant pool to be used while parsing encoded annotation values in
 * reflection methods. The annotation encoding used by the JDK encodes values by their constant pool
 * indices, whereas the Native Image implementation stores offsets in the
 * {@link com.oracle.svm.core.code.CodeInfoEncoder.Encoders} class and string caches. Since the
 * runtime does not handle JDK constant pools, this substitution enables reusing much of the JDK
 * decoding logic for free.
 */
@SuppressWarnings({"unused", "static-method", "hiding"})
@TargetClass(className = "jdk.internal.reflect.ConstantPool")
public final class Target_jdk_internal_reflect_ConstantPool {
    // Number of entries in this constant pool (= maximum valid constant pool index)
    @Substitute
    public int getSize() {
        return Integer.MAX_VALUE;
    }

    @Substitute
    public Class<?> getClassAt(int index) {
        return NonmovableArrays.getObject(CodeInfoAccess.getFrameInfoSourceClasses(CodeInfoTable.getImageCodeInfo()), index);
    }

    @Substitute
    public Class<?> getClassAtIfLoaded(int index) {
        throw unimplemented();
    }

    // Returns a class reference index for a method or a field.
    @Substitute
    public int getClassRefIndexAt(int index) {
        throw unimplemented();
    }

    // Returns either a Method or Constructor.
    // Static initializers are returned as Method objects.
    @Substitute
    public Member getMethodAt(int index) {
        throw unimplemented();
    }

    @Substitute
    public Member getMethodAtIfLoaded(int index) {
        throw unimplemented();
    }

    @Substitute
    public Field getFieldAt(int index) {
        throw unimplemented();
    }

    @Substitute
    public Field getFieldAtIfLoaded(int index) {
        throw unimplemented();
    }

    // Fetches the class name, member (field, method or interface
    // method) name, and type descriptor as an array of three Strings
    @Substitute
    public String[] getMemberRefInfoAt(int index) {
        throw unimplemented();
    }

    // Returns a name and type reference index for a method, a field or an invokedynamic.
    @Substitute
    public int getNameAndTypeRefIndexAt(int index) {
        throw unimplemented();
    }

    // Fetches the name and type from name_and_type index as an array of two Strings
    @Substitute
    public String[] getNameAndTypeRefInfoAt(int index) {
        throw unimplemented();
    }

    @Substitute
    public int getIntAt(int index) {
        throw unimplemented();
    }

    @Substitute
    public long getLongAt(int index) {
        throw unimplemented();
    }

    @Substitute
    public float getFloatAt(int index) {
        throw unimplemented();
    }

    @Substitute
    public double getDoubleAt(int index) {
        throw unimplemented();
    }

    @Substitute
    public String getStringAt(int index) {
        return NonmovableArrays.getObject(CodeInfoAccess.getFrameInfoSourceMethodNames(CodeInfoTable.getImageCodeInfo()), index);
    }

    @Substitute
    public String getUTF8At(int index) {
        return getStringAt(index);
    }

    @Substitute
    public Target_jdk_internal_reflect_ConstantPool_Tag getTagAt(int index) {
        throw unimplemented();
    }

    @Delete private Object constantPoolOop;

    @Delete
    private native int getSize0(Object constantPoolOop);

    @Delete
    private native Class<?> getClassAt0(Object constantPoolOop, int index);

    @Delete
    private native Class<?> getClassAtIfLoaded0(Object constantPoolOop, int index);

    @Delete
    private native int getClassRefIndexAt0(Object constantPoolOop, int index);

    @Delete
    private native Member getMethodAt0(Object constantPoolOop, int index);

    @Delete
    private native Member getMethodAtIfLoaded0(Object constantPoolOop, int index);

    @Delete
    private native Field getFieldAt0(Object constantPoolOop, int index);

    @Delete
    private native Field getFieldAtIfLoaded0(Object constantPoolOop, int index);

    @Delete
    private native String[] getMemberRefInfoAt0(Object constantPoolOop, int index);

    @Delete
    private native int getNameAndTypeRefIndexAt0(Object constantPoolOop, int index);

    @Delete
    private native String[] getNameAndTypeRefInfoAt0(Object constantPoolOop, int index);

    @Delete
    private native int getIntAt0(Object constantPoolOop, int index);

    @Delete
    private native long getLongAt0(Object constantPoolOop, int index);

    @Delete
    private native float getFloatAt0(Object constantPoolOop, int index);

    @Delete
    private native double getDoubleAt0(Object constantPoolOop, int index);

    @Delete
    private native String getStringAt0(Object constantPoolOop, int index);

    @Delete
    private native String getUTF8At0(Object constantPoolOop, int index);

    @Delete
    private native byte getTagAt0(Object constantPoolOop, int index);
}

@TargetClass(className = "jdk.internal.reflect.ConstantPool", innerClass = "Tag")
final class Target_jdk_internal_reflect_ConstantPool_Tag {
}
