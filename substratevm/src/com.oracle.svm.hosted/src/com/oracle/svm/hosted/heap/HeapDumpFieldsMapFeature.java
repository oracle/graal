/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.heap;

import java.io.UnsupportedEncodingException;
import java.util.Collection;

import org.graalvm.compiler.core.common.util.TypeConversion;
import org.graalvm.compiler.core.common.util.UnsafeArrayTypeWriter;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heapdump.HeapDumpUtils;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.util.ByteArrayReader;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.AfterCompilationAccessImpl;

import jdk.vm.ci.meta.ResolvedJavaField;

class HeapDumpHostedUtils {

    @Platforms(Platform.HOSTED_ONLY.class)
    public static byte[] dumpFieldsMap(Collection<? extends SharedType> types) {
        UnsafeArrayTypeWriter writeBuffer = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());

        writeFieldsInfo(writeBuffer, types);
        int length = TypeConversion.asS4(writeBuffer.getBytesWritten());
        return writeBuffer.toArray(new byte[length]);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void writeFieldsInfo(UnsafeArrayTypeWriter writeBuffer, Collection<? extends SharedType> types) {
        for (SharedType type : types) {
            /* I am only interested in instance types. */
            if (type.isInstanceClass()) {
                /* Get the direct fields of the class. */
                final ResolvedJavaField[] fields = type.getInstanceFields(false);
                /* Get the static fields of the class. */
                final ResolvedJavaField[] sfields = type.getStaticFields();
                /* I am only interested in classes with some fields. */
                if (fields.length == 0 && sfields.length == 0) {
                    continue;
                }
                /* Write the class name */
                writeString(writeBuffer, type.toClassName());

                /* Write each direct field and offset. */
                for (ResolvedJavaField resolvedJavaField : inHotSpotFieldOrder(fields)) {
                    if (resolvedJavaField instanceof SharedField) {
                        final SharedField field = (SharedField) resolvedJavaField;

                        writeField(field, writeBuffer);
                    }
                }
                writeBuffer.putU1(0);
                /* Write each static field and offset. */
                for (ResolvedJavaField resolvedJavaField : inHotSpotFieldOrder(sfields)) {
                    if (resolvedJavaField instanceof SharedField) {
                        final SharedField field = (SharedField) resolvedJavaField;
                        if (!field.isWritten()) {
                            /* I am only interested in fields that are not constants. */
                            continue;
                        }
                        if (!field.isAccessed()) {
                            /* I am only interested in fields that are used. */
                            continue;
                        }
                        writeField(field, writeBuffer);
                    }
                }
                writeBuffer.putU1(0);
            }
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static void writeField(final SharedField field, UnsafeArrayTypeWriter writeBuffer) {
        final int location = field.getLocation();
        /* I am only interested in fields that have locations. */
        if (location < 0) {
            return;
        }
        writeString(writeBuffer, field.getName());
        writeBuffer.putU1(field.getJavaKind().getTypeChar());
        writeBuffer.putU1(field.getStorageKind().getTypeChar());
        writeBuffer.putU1((location >>> 24) & 0xFF);
        writeBuffer.putU1((location >>> 16) & 0xFF);
        writeBuffer.putU1((location >>> 8) & 0xFF);
        writeBuffer.putU1((location >>> 0) & 0xFF);
    }

    /*
     * Write fields in the same order as in HotSpot heap dump. This is the reverse order of what SVM
     * hands out. See also GR-6758.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    private static ResolvedJavaField[] inHotSpotFieldOrder(ResolvedJavaField[] fields) {
        ResolvedJavaField[] reversed = new ResolvedJavaField[fields.length];

        for (int i = 0; i < fields.length; i++) {
            reversed[fields.length - 1 - i] = fields[i];
        }
        return reversed;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static void writeString(UnsafeArrayTypeWriter writeBuffer, String name) {
        try {
            byte[] buf = name.getBytes("UTF-8");
            for (byte b : buf) {
                writeBuffer.putU1(b);
            }
            writeBuffer.putU1(0);
        } catch (UnsupportedEncodingException ex) {
            VMError.shouldNotReachHere(ex);
        }
    }
}

@AutomaticallyRegisteredFeature
class HeapDumpFieldsMapFeature implements InternalFeature {

    /**
     * Write out fields info and their offsets.
     */
    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public void afterCompilation(Feature.AfterCompilationAccess access) {
        AfterCompilationAccessImpl accessImpl = (AfterCompilationAccessImpl) access;
        byte[] fieldMap = HeapDumpHostedUtils.dumpFieldsMap(accessImpl.getTypes());

        HeapDumpUtils.getHeapDumpUtils().setFieldsMap(fieldMap);
        access.registerAsImmutable(fieldMap);
    }
}
