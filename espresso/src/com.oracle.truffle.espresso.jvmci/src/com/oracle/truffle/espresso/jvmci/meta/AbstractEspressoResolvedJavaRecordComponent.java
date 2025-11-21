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

import static com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedInstanceType.DECLARED_ANNOTATIONS;
import static com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedInstanceType.TYPE_ANNOTATIONS;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaRecordComponent;
import jdk.vm.ci.meta.annotation.AbstractAnnotated;
import jdk.vm.ci.meta.annotation.AnnotationsInfo;

public abstract class AbstractEspressoResolvedJavaRecordComponent extends AbstractAnnotated implements ResolvedJavaRecordComponent {
    private final AbstractEspressoResolvedInstanceType declaringRecord;
    private final int index;
    private final String name;
    private final JavaType type;

    AbstractEspressoResolvedJavaRecordComponent(AbstractEspressoResolvedInstanceType declaringRecord, int recordIndex, int nameIndex, JavaType type) {
        this.declaringRecord = declaringRecord;
        this.index = recordIndex;
        this.name = declaringRecord.getConstantPool().lookupUtf8(nameIndex);
        this.type = type;
    }

    @Override
    public AbstractEspressoResolvedInstanceType getDeclaringRecord() {
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
        byte[] bytes = getRawAnnotationBytes(DECLARED_ANNOTATIONS);
        AbstractEspressoResolvedInstanceType container = getDeclaringRecord();
        return AnnotationsInfo.make(bytes, container.getConstantPool(), container);
    }

    @Override
    public AnnotationsInfo getTypeAnnotationInfo() {
        byte[] bytes = getRawAnnotationBytes(TYPE_ANNOTATIONS);
        AbstractEspressoResolvedInstanceType container = getDeclaringRecord();
        return AnnotationsInfo.make(bytes, container.getConstantPool(), container);
    }

    protected final int getIndex() {
        return index;
    }

    protected abstract byte[] getRawAnnotationBytes(int category);

    @Override
    public int hashCode() {
        return declaringRecord.hashCode() + index * 31;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AbstractEspressoResolvedJavaRecordComponent other)) {
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
