/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.vmaccess;

import org.graalvm.polyglot.Value;

import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedJavaRecordComponent;

import jdk.vm.ci.meta.JavaType;

/** External Espresso representation of a resolved Java record component. */
final class EspressoExternalResolvedJavaRecordComponent extends AbstractEspressoResolvedJavaRecordComponent {
    private final Value recordComponentInfo;

    /** Creates a record-component wrapper around Espresso class-file metadata. */
    EspressoExternalResolvedJavaRecordComponent(EspressoExternalResolvedInstanceType declaringRecord, int index, Value recordComponentInfo) {
        super(declaringRecord, index, getNameIndex(recordComponentInfo), getType(declaringRecord, recordComponentInfo));
        this.recordComponentInfo = recordComponentInfo;
    }

    /** Reads the component name index through the external JVMCI bridge. */
    private static int getNameIndex(Value recordComponentInfo) {
        return recordComponentInfo.getMember("nameIndex").asInt();
    }

    /** Resolves the component descriptor through the declaring record's constant pool. */
    private static JavaType getType(EspressoExternalResolvedInstanceType declaringRecord, Value recordComponentInfo) {
        EspressoExternalVMAccess access = declaringRecord.getAccess();
        int descriptorIndex = recordComponentInfo.getMember("descriptorIndex").asInt();
        String descriptor = declaringRecord.getConstantPool().lookupUtf8(descriptorIndex);
        return access.lookupType(descriptor, declaringRecord, false);
    }

    @Override
    protected byte[] getRawAnnotationBytes(int category) {
        return getDeclaringRecord().getAccess().getRawAnnotationBytes(recordComponentInfo, category);
    }

    @Override
    public EspressoExternalResolvedInstanceType getDeclaringRecord() {
        return (EspressoExternalResolvedInstanceType) super.getDeclaringRecord();
    }
}
