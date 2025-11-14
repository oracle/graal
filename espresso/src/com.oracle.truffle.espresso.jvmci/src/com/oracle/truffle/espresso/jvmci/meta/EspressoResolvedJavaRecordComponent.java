/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jvmci.meta;

import static com.oracle.truffle.espresso.jvmci.EspressoJVMCIRuntime.runtime;
import static com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedInstanceType.DECLARED_ANNOTATIONS;
import static com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedInstanceType.TYPE_ANNOTATIONS;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaRecordComponent;
import jdk.vm.ci.meta.annotation.AbstractAnnotated;
import jdk.vm.ci.meta.annotation.AnnotationsInfo;

public final class EspressoResolvedJavaRecordComponent extends AbstractAnnotated implements ResolvedJavaRecordComponent {
    private final EspressoResolvedInstanceType declaringRecord;
    private final int index;
    private final String name;
    private final JavaType type;

    EspressoResolvedJavaRecordComponent(EspressoResolvedInstanceType declaringRecord, int recordIndex, int nameIndex, int typeIndex) {
        this.declaringRecord = declaringRecord;
        this.index = recordIndex;
        this.name = declaringRecord.getConstantPool().lookupUtf8(nameIndex);
        this.type = runtime().lookupType(declaringRecord.getConstantPool().lookupUtf8(typeIndex), declaringRecord, false);
    }

    @Override
    public EspressoResolvedInstanceType getDeclaringRecord() {
        return declaringRecord;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public JavaType getType() {
        return type;
    }

    @Override
    public AnnotationsInfo getRawDeclaredAnnotationInfo() {
        byte[] bytes = getRawAnnotationBytes(declaringRecord, index, DECLARED_ANNOTATIONS);
        EspressoResolvedInstanceType container = getDeclaringRecord();
        return AnnotationsInfo.make(bytes, container.getConstantPool(), container);
    }

    @Override
    public AnnotationsInfo getTypeAnnotationInfo() {
        byte[] bytes = getRawAnnotationBytes(declaringRecord, index, TYPE_ANNOTATIONS);
        EspressoResolvedInstanceType container = getDeclaringRecord();
        return AnnotationsInfo.make(bytes, container.getConstantPool(), container);
    }

    private static native byte[] getRawAnnotationBytes(EspressoResolvedInstanceType declaringRecord, int index, int category);

    @Override
    public int hashCode() {
        return declaringRecord.hashCode() + index * 31;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof EspressoResolvedJavaRecordComponent other)) {
            return false;
        }
        /*
         * No need for a native equals0 helper (cf EspressoResolvedJavaField.equals0) as there is no
         * metadata object that needs an identity equality check.
         */
        return other.index == this.index && other.declaringRecord.equals(this.declaringRecord);
    }

    @Override
    public String toString() {
        return "EspressoResolvedJavaRecordComponent<" + declaringRecord.getName() + "." + name + " " + type.getUnqualifiedName() + ">";
    }
}
