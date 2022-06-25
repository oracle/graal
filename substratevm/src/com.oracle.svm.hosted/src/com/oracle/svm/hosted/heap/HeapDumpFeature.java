/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.core.common.util.TypeConversion;
import org.graalvm.compiler.core.common.util.UnsafeArrayTypeWriter;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.HeapDumpSupport;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heapdump.HeapDumpSupportImpl;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.util.ByteArrayReader;
import com.oracle.svm.hosted.FeatureImpl.AfterCompilationAccessImpl;

import jdk.vm.ci.meta.ResolvedJavaField;

@AutomaticallyRegisteredFeature
public class HeapDumpFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        /*
         * Include the feature unconditionally on Linux and macOS. The code and all its data are
         * only present in the final image if the heap dumping infrastructure is called by some
         * code.
         */
        return Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.DARWIN.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(HeapDumpSupport.class, new HeapDumpSupportImpl());
    }

    @Override
    public void afterCompilation(Feature.AfterCompilationAccess access) {
        AfterCompilationAccessImpl accessImpl = (AfterCompilationAccessImpl) access;
        byte[] metadata = computeMetadata(accessImpl.getTypes());

        HeapDumpSupportImpl support = (HeapDumpSupportImpl) ImageSingletons.lookup(HeapDumpSupport.class);
        support.setMetadata(metadata);
        access.registerAsImmutable(metadata);
    }

    /**
     * This method writes the metadata that is needed for heap dumping into one large byte[]. The
     * format is as follows:
     *
     * <pre>
     * |----------------------------|
     * | data in the byte[]         |
     * |----------------------------|
     * | s4 totalFieldCount         |
     * | s4 classCount              |
     * | s4 fieldNameCount          |
     * | uv maxTypeId               |
     * | (class information)*       |
     * | (field names)*             |
     * |----------------------------|
     *
     * |----------------------------|
     * | information per class      |
     * |----------------------------|
     * | uv typeId                  |
     * | uv instanceFieldCount      |
     * | uv staticFieldCount        |
     * | (instance field)*          |
     * | (static field)*            |
     * |----------------------------|
     *
     * |----------------------------|
     * | information per field      |
     * |----------------------------|
     * | u1 storageKind             |
     * | uv fieldNameIndex          |
     * | uv location                |
     * |----------------------------|
     *
     * |----------------------------|
     * | information per field name |
     * |----------------------------|
     * | uv lengthInBytes           |
     * | (s1 utf8 character)*       |
     * |----------------------------|
     * </pre>
     */
    private static byte[] computeMetadata(Collection<? extends SharedType> types) {
        int maxTypeId = types.stream().mapToInt(t -> t.getHub().getTypeID()).max().orElse(0);
        assert maxTypeId > 0;

        UnsafeArrayTypeWriter output = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());
        writeMetadata(output, types, maxTypeId);

        int length = TypeConversion.asS4(output.getBytesWritten());
        return output.toArray(new byte[length]);
    }

    private static void writeMetadata(UnsafeArrayTypeWriter output, Collection<? extends SharedType> types, int maxTypeId) {
        /* Write header. */
        long totalFieldCountOffset = output.getBytesWritten();
        output.putS4(0); // total field count (patched later on)
        long classCountOffset = output.getBytesWritten();
        output.putS4(0); // class count (patched later on)
        long fieldNameCountOffset = output.getBytesWritten();
        output.putS4(0); // field name count (patched later on)

        output.putUV(maxTypeId);

        /* Write the class and field information. */
        int totalFieldCount = 0;
        int classCount = 0;
        EconomicMap<String, Integer> fieldNames = EconomicMap.create();
        for (SharedType type : types) {
            if (type.isInstanceClass()) {
                ArrayList<SharedField> instanceFields = prepareFields(type.getInstanceFields(false));
                ArrayList<SharedField> staticFields = prepareFields(type.getStaticFields());
                if (instanceFields.size() == 0 && staticFields.size() == 0) {
                    continue;
                }

                classCount++;
                totalFieldCount += instanceFields.size() + staticFields.size();

                output.putUV(type.getHub().getTypeID());
                output.putUV(instanceFields.size());
                output.putUV(staticFields.size());

                /* Write direct instance fields. */
                for (SharedField field : instanceFields) {
                    writeField(field, output, fieldNames);
                }

                /* Write static fields. */
                for (SharedField field : staticFields) {
                    writeField(field, output, fieldNames);
                }
            }
        }

        /* Patch the header. */
        output.patchS4(totalFieldCount, totalFieldCountOffset);
        output.patchS4(classCount, classCountOffset);
        output.patchS4(fieldNames.size(), fieldNameCountOffset);

        /* Write the field names. */
        int index = 0;
        MapCursor<String, Integer> cursor = fieldNames.getEntries();
        while (cursor.advance()) {
            assert cursor.getValue() == index;
            byte[] utf8 = cursor.getKey().getBytes(StandardCharsets.UTF_8);
            output.putUV(utf8.length);
            for (byte b : utf8) {
                output.putS1(b);
            }
            index++;
        }
    }

    private static ArrayList<SharedField> prepareFields(ResolvedJavaField[] input) {
        /* Collect all fields that have a location. */
        ArrayList<SharedField> result = new ArrayList<>();
        for (ResolvedJavaField f : input) {
            if (f instanceof SharedField field) {
                if (field.getLocation() >= 0) {
                    result.add(field);
                }
            }
        }

        /* Sort fields by their location. */
        result.sort(Comparator.comparingInt(SharedField::getLocation));
        return result;
    }

    private static void writeField(SharedField field, UnsafeArrayTypeWriter output, EconomicMap<String, Integer> fieldNames) {
        int location = field.getLocation();
        assert location >= 0;
        output.putU1(field.getStorageKind().getTypeChar());
        output.putUV(addFieldName(field.getName(), fieldNames));
        output.putUV(location);
    }

    private static int addFieldName(String fieldName, EconomicMap<String, Integer> fieldNames) {
        Integer fieldNameIndex = fieldNames.get(fieldName);
        if (fieldNameIndex != null) {
            return fieldNameIndex;
        }

        int result = fieldNames.size();
        fieldNames.put(fieldName, result);
        return result;
    }
}
