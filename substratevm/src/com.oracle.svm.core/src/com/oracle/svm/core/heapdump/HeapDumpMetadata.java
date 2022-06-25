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
package com.oracle.svm.core.heapdump;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawPointerTo;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.c.struct.PinnedObjectField;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.util.coder.ByteStream;
import com.oracle.svm.core.util.coder.ByteStreamAccess;
import com.oracle.svm.core.util.coder.NativeCoder;
import com.oracle.svm.core.util.coder.Pack200Coder;

class HeapDumpMetadata {
    private static final ComputeHubDataVisitor COMPUTE_HUB_DATA_VISITOR = new ComputeHubDataVisitor();

    private static int fieldNameCount;
    private static int classInfoCount;
    private static ClassInfo classInfos;
    private static FieldInfoPointer fieldInfoTable;
    private static FieldNamePointer fieldNameTable;

    public static boolean allocate(byte[] metadata) {
        assert classInfos.isNull() && fieldInfoTable.isNull() && fieldNameTable.isNull();

        Pointer start = NonmovableArrays.getArrayBase(NonmovableArrays.fromImageHeap(metadata));
        Pointer end = start.add(metadata.length);

        ByteStream stream = StackValue.get(ByteStream.class);
        ByteStreamAccess.initialize(stream, start);

        /* Read the header. */
        int totalFieldCount = NativeCoder.readInt(stream);
        int classCount = NativeCoder.readInt(stream);
        fieldNameCount = NativeCoder.readInt(stream);
        int maxTypeId = Pack200Coder.readUVAsInt(stream);
        classInfoCount = maxTypeId + 1;

        /*
         * Precompute some data structures so that the heap dumping can access the encoded data more
         * efficiently.
         */
        UnsignedWord classInfosSize = WordFactory.unsigned(classInfoCount).multiply(SizeOf.get(ClassInfo.class));
        classInfos = ImageSingletons.lookup(UnmanagedMemorySupport.class).calloc(classInfosSize);
        if (classInfos.isNull()) {
            return false;
        }

        UnsignedWord fieldStartsSize = WordFactory.unsigned(totalFieldCount).multiply(SizeOf.get(FieldInfoPointer.class));
        fieldInfoTable = ImageSingletons.lookup(UnmanagedMemorySupport.class).calloc(fieldStartsSize);
        if (fieldInfoTable.isNull()) {
            return false;
        }

        UnsignedWord fieldNameTableSize = WordFactory.unsigned(fieldNameCount).multiply(SizeOf.get(FieldNamePointer.class));
        fieldNameTable = ImageSingletons.lookup(UnmanagedMemorySupport.class).calloc(fieldNameTableSize);
        if (fieldNameTable.isNull()) {
            return false;
        }

        /* Read the classes and fields. */
        int fieldIndex = 0;
        for (int i = 0; i < classCount; i++) {
            int typeId = Pack200Coder.readUVAsInt(stream);

            ClassInfo classInfo = getClassInfo(typeId);

            int numInstanceFields = Pack200Coder.readUVAsInt(stream);
            classInfo.setInstanceFieldCount(numInstanceFields);

            int numStaticFields = Pack200Coder.readUVAsInt(stream);
            classInfo.setStaticFieldCount(numStaticFields);

            classInfo.setInstanceFields(fieldInfoTable.addressOf(fieldIndex));
            for (int j = 0; j < numInstanceFields; j++) {
                Pointer fieldInfo = (Pointer) fieldInfoTable.addressOf(fieldIndex);
                fieldInfo.writeWord(0, stream.getPosition());
                FieldInfoAccess.skipFieldInfo(stream);
                fieldIndex++;
            }

            classInfo.setStaticFields(fieldInfoTable.addressOf(fieldIndex));
            for (int j = 0; j < numStaticFields; j++) {
                Pointer fieldInfo = (Pointer) fieldInfoTable.addressOf(fieldIndex);
                fieldInfo.writeWord(0, stream.getPosition());
                FieldInfoAccess.skipFieldInfo(stream);
                fieldIndex++;
            }
        }

        /* Fill the symbol table. */
        for (int i = 0; i < fieldNameCount; i++) {
            Pointer fieldName = (Pointer) fieldNameTable.addressOf(i);
            fieldName.writeWord(0, stream.getPosition());
            int length = Pack200Coder.readUVAsInt(stream);
            stream.setPosition(stream.getPosition().add(length));
        }
        assert stream.getPosition().equal(end);

        /* Store the DynamicHubs in their corresponding ClassInfo structs. */
        COMPUTE_HUB_DATA_VISITOR.initialize();
        Heap.getHeap().walkImageHeapObjects(COMPUTE_HUB_DATA_VISITOR);

        /* Compute the size that the instance fields of the classes. */
        for (int i = 0; i < classInfoCount; i++) {
            ClassInfo classInfo = HeapDumpMetadata.getClassInfo(i);
            if (ClassInfoAccess.isValid(classInfo)) {
                computeInstanceFieldsDumpSize(classInfo);
            }
        }
        return true;
    }

    public static void free() {
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(classInfos);
        classInfos = WordFactory.nullPointer();

        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(fieldInfoTable);
        fieldInfoTable = WordFactory.nullPointer();

        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(fieldNameTable);
        fieldNameTable = WordFactory.nullPointer();
    }

    public static int getClassInfoCount() {
        return classInfoCount;
    }

    public static ClassInfo getClassInfo(Class<?> clazz) {
        return getClassInfo(DynamicHub.fromClass(clazz));
    }

    public static ClassInfo getClassInfo(DynamicHub hub) {
        if (hub == null) {
            return WordFactory.nullPointer();
        }
        return getClassInfo(hub.getTypeID());
    }

    public static ClassInfo getClassInfo(int typeId) {
        return classInfos.addressOf(typeId);
    }

    public static int getFieldNameCount() {
        return fieldNameCount;
    }

    public static FieldNamePointer getFieldNameTable() {
        return fieldNameTable;
    }

    /**
     * Get the size in bytes that is required for the fields of the given class when an object of
     * that class is written to the heap dump file. Note that this is not the same as the object
     * size in the heap. The size in the heap will include the object header, padding/alignment, and
     * the size of object references may be different.
     */
    private static int computeInstanceFieldsDumpSize(ClassInfo classInfo) {
        /* Check if this class was already processed earlier. */
        if (classInfo.getInstanceFieldsDumpSize() != -1) {
            return classInfo.getInstanceFieldsDumpSize();
        }

        /* Compute the size of the immediate fields. */
        int result = computeFieldsDumpSize(classInfo.getInstanceFields(), classInfo.getInstanceFieldCount());

        /* Add the size of all inherited fields. */
        DynamicHub superHub = classInfo.getHub().getSuperHub();
        if (superHub != null) {
            result += computeInstanceFieldsDumpSize(HeapDumpMetadata.getClassInfo(superHub));
        }
        classInfo.setInstanceFieldsDumpSize(result);
        return result;
    }

    static int computeFieldsDumpSize(FieldInfoPointer fields, int fieldCount) {
        int result = 0;
        for (int i = 0; i < fieldCount; i++) {
            FieldInfo field = fields.addressOf(i).read();
            byte storageKind = FieldInfoAccess.getStorageKind(field);
            result += StorageKind.getSize(storageKind);
        }
        return result;
    }

    @RawStructure
    public interface ClassInfo extends PointerBase {
        @RawField
        @PinnedObjectField
        DynamicHub getHub();

        @RawField
        @PinnedObjectField
        void setHub(DynamicHub value);

        @RawField
        int getSerialNum();

        @RawField
        void setSerialNum(int value);

        @RawField
        int getInstanceFieldsDumpSize();

        @RawField
        void setInstanceFieldsDumpSize(int value);

        @RawField
        int getInstanceFieldCount();

        @RawField
        void setInstanceFieldCount(int value);

        @RawField
        FieldInfoPointer getInstanceFields();

        @RawField
        void setInstanceFields(FieldInfoPointer value);

        @RawField
        int getStaticFieldCount();

        @RawField
        void setStaticFieldCount(int value);

        @RawField
        FieldInfoPointer getStaticFields();

        @RawField
        void setStaticFields(FieldInfoPointer value);

        ClassInfo addressOf(int index);
    }

    public static class ClassInfoAccess {
        static boolean isValid(ClassInfo classInfo) {
            return classInfo.getHub() != null;
        }
    }

    @RawStructure
    public interface FieldInfo extends PointerBase {
        // u1 storageKind
        // uv fieldNameIndex
        // uv location
    }

    public static class FieldInfoAccess {
        static byte getStorageKind(FieldInfo field) {
            return ((Pointer) field).readByte(0);
        }

        static FieldName getFieldName(FieldInfo field) {
            int fieldNameIndex = Pack200Coder.readUVAsInt(getFieldNameIndexAddress(field));
            return HeapDumpMetadata.getFieldNameTable().addressOf(fieldNameIndex).read();
        }

        static int getLocation(FieldInfo field) {
            Pointer fieldNameIndex = getFieldNameIndexAddress(field); // skip storageKind
            ByteStream stream = StackValue.get(ByteStream.class);
            ByteStreamAccess.initialize(stream, fieldNameIndex);
            Pack200Coder.readUVAsInt(stream); // skip fieldNameIndex
            return Pack200Coder.readUVAsInt(stream);
        }

        static void skipFieldInfo(ByteStream stream) {
            NativeCoder.readByte(stream); // storage kind
            Pack200Coder.readUVAsInt(stream); // field name index
            Pack200Coder.readUVAsInt(stream); // location
        }

        private static Pointer getFieldNameIndexAddress(FieldInfo field) {
            return ((Pointer) field).add(1);
        }
    }

    @RawPointerTo(FieldInfo.class)
    public interface FieldInfoPointer extends PointerBase {
        FieldInfoPointer addressOf(int index);

        FieldInfo read();
    }

    /** Data structure has a variable size. */
    @RawStructure
    public interface FieldName extends PointerBase {
        // uv lengthInBytes
        // (s1 utf8 character)*
    }

    public static class FieldNameAccess {
        static int getLength(FieldName fieldName) {
            return Pack200Coder.readUVAsInt((Pointer) fieldName);
        }

        static CCharPointer getChars(FieldName fieldName) {
            ByteStream data = StackValue.get(ByteStream.class);
            ByteStreamAccess.initialize(data, (Pointer) fieldName);
            Pack200Coder.readUV(data); // skip length
            return (CCharPointer) data.getPosition();
        }
    }

    @RawPointerTo(FieldName.class)
    public interface FieldNamePointer extends PointerBase {
        FieldNamePointer addressOf(int index);

        FieldName read();
    }

    private static class ComputeHubDataVisitor implements ObjectVisitor {
        private int classSerialNum;

        public void initialize() {
            this.classSerialNum = 0;
        }

        @Override
        public boolean visitObject(Object o) {
            if (o instanceof DynamicHub hub) {
                ClassInfo classInfo = HeapDumpMetadata.getClassInfo(hub.getTypeID());
                if (classInfo.getHub() == null) {
                    /* Initialize the relevant data for all classes that don't declare any fields. */
                    classInfo.setHub(hub);
                    classInfo.setSerialNum(++classSerialNum);
                    classInfo.setInstanceFieldsDumpSize(-1);
                }
            }
            return true;
        }
    }
}
