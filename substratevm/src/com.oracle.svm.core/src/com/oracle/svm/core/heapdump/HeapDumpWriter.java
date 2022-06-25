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

import static com.oracle.svm.core.heap.RestrictHeapAccess.Access.NO_ALLOCATION;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoDecoder;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.RuntimeCodeCache;
import com.oracle.svm.core.code.RuntimeCodeInfoAccess;
import com.oracle.svm.core.code.RuntimeCodeInfoMemory;
import com.oracle.svm.core.code.SimpleCodeInfoQueryResult;
import com.oracle.svm.core.collections.GrowableArray;
import com.oracle.svm.core.collections.GrowableArrayAccess;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.heap.CodeReferenceMapDecoder;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.ReferenceMapIndex;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.heapdump.HeapDumpMetadata.ClassInfo;
import com.oracle.svm.core.heapdump.HeapDumpMetadata.ClassInfoAccess;
import com.oracle.svm.core.heapdump.HeapDumpMetadata.FieldInfo;
import com.oracle.svm.core.heapdump.HeapDumpMetadata.FieldInfoAccess;
import com.oracle.svm.core.heapdump.HeapDumpMetadata.FieldInfoPointer;
import com.oracle.svm.core.heapdump.HeapDumpMetadata.FieldName;
import com.oracle.svm.core.heapdump.HeapDumpMetadata.FieldNameAccess;
import com.oracle.svm.core.heapdump.HeapDumpMetadata.FieldNamePointer;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.os.BufferedFileOperationSupport;
import com.oracle.svm.core.os.BufferedFileOperationSupport.BufferedFile;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFileDescriptor;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.StackFrameVisitor;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.ThreadingSupportImpl;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.VMThreadLocalMTSupport;
import com.oracle.svm.core.util.VMError;

/**
 * This class dumps the image heap and the Java heap into a file (HPROF binary format), similar to
 * the HotSpot class {@code heapDumper}. The heap dumping needs additional metadata that is encoded
 * during the image build and that is stored in the image heap in a compact binary format (see
 * {@code HeapDumpFeature}).
 *
 * The actual heap dumping logic is executed in a VM operation, so it is safe to iterate over all
 * live threads and to, for example, access their thread local values. The heap dumper is
 * implemented as a singleton and only a single heap dumping operation can be in progress at a given
 * time.
 *
 * The heap dumping code must not modify the Java heap in any way as this would pollute or corrupt
 * the heap dump. Therefore, no Java datastructures can be used and all data structures must be
 * allocated using native memory instead. This also guarantees that the heap dumping works in case
 * that Native Image is completely out of Java heap memory (see option
 * {@code -XX:HeapDumpOnOutOfMemoryError}).
 *
 * The heap dump is stored in the HPROF binary format (big endian byte order). The high-level
 * structure of the file is roughly as follows:
 *
 * <pre>
 *   file header
 *   ([top level record] [sub record]*)+
 *   end marker
 * </pre>
 *
 * Other relevant file format aspects:
 * <ul>
 * <li>References to objects or symbols are encoded using their word-sized address. This is done
 * regardless if compressed references are enabled or not.</li>
 * <li>Symbols such as class or method names are encoded as UTF8.</li>
 * <li>The size of individual records is limited to {@code MAX_UINT}. So, very large arrays that
 * have a larger size than {@code MAX_UINT} need to be truncated.</li>
 * </ul>
 */
public class HeapDumpWriter {
    private static final int DUMMY_STACK_TRACE_ID = 1;
    private static final int LARGE_OBJECT_THRESHOLD = 1 * 1024 * 1024;
    private static final int HEAP_DUMP_SEGMENT_TARGET_SIZE = 1 * 1024 * 1024;

    private final NoAllocationVerifier noAllocationVerifier = NoAllocationVerifier.factory("HeapDumpWriter", false);
    private final DumpStackFrameVisitor dumpStackFrameVisitor = new DumpStackFrameVisitor();
    private final DumpObjectsVisitor dumpObjectsVisitor = new DumpObjectsVisitor();
    private final CodeMetadataVisitor codeMetadataVisitor = new CodeMetadataVisitor();
    private final ThreadLocalVisitor threadLocalVisitor = new ThreadLocalVisitor();
    @UnknownObjectField(types = {byte[].class}) private byte[] metadata;

    private BufferedFile f;
    private long topLevelRecordBegin = -1;
    private long subRecordBegin = -1;
    private boolean error;

    @Platforms(Platform.HOSTED_ONLY.class)
    public HeapDumpWriter() {
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setMetadata(byte[] value) {
        this.metadata = value;
    }

    public boolean dumpHeap(RawFileDescriptor fd) {
        assert VMOperation.isInProgressAtSafepoint();
        assert ThreadingSupportImpl.isRecurringCallbackPaused();
        noAllocationVerifier.open();
        try {
            Heap.getHeap().suspendAllocation();
            return dumpHeap0(fd);
        } finally {
            noAllocationVerifier.close();
        }
    }

    private boolean dumpHeap0(RawFileDescriptor fd) {
        boolean initialized = initialize(fd);
        try {
            if (initialized) {
                return writeHeapDump();
            } else {
                Log.log().string("An error occurred while initializing the heap dump infrastructure. No heap data will be dumped.").newline();
                return false;
            }
        } finally {
            reset();
        }
    }

    private boolean initialize(RawFileDescriptor fd) {
        assert topLevelRecordBegin == -1 && subRecordBegin == -1 && !error;
        this.f = getFileSupport().allocate(fd);
        if (f.isNull()) {
            return false;
        }
        return HeapDumpMetadata.allocate(metadata);
    }

    private void reset() {
        getFileSupport().free(f);
        this.f = WordFactory.nullPointer();
        this.topLevelRecordBegin = -1;
        this.subRecordBegin = -1;
        this.error = false;
        HeapDumpMetadata.free();
    }

    @NeverInline("Starting a stack walk in the caller frame.")
    private boolean writeHeapDump() {
        Pointer currentThreadSp = KnownIntrinsics.readCallerStackPointer();

        writeHeader();
        writeClassNames(); // UTF-8 symbols
        writeFieldNames(); // UTF-8 symbols
        writeLoadClassRecords(); // LOAD_CLASS
        writeStackTraces(currentThreadSp); // FRAME and TRACE

        /* 1..n HEAP_DUMP_SEGMENT records */
        startTopLevelRecord(HProfTopLevelRecord.HEAP_DUMP_SEGMENT);
        writeClasses(); // GC_CLASS_DUMP
        writeThreads(currentThreadSp); // GC_ROOT_THREAD_OBJ, GC_ROOT_JAVA_FRAME, GC_ROOT_JNI_LOCAL
        writeJNIGlobals(); // GC_ROOT_JNI_GLOBAL
        writeStickyClasses(); // GC_ROOT_STICKY_CLASS
        writeObjects(); // GC_INSTANCE_DUMP, GC_OBJ_ARRAY_DUMP, GC_PRIM_ARRAY_DUMP
        endTopLevelRecord();

        startTopLevelRecord(HProfTopLevelRecord.HEAP_DUMP_END);
        endTopLevelRecord();

        flush();

        if (error) {
            Log.log().string("An error occurred while writing the heap dump data. The data in the heap dump file may be corrupt.").newline();
            return false;
        }
        return true;
    }

    private void writeHeader() {
        writeUTF8("JAVA PROFILE 1.0.2");
        writeByte((byte) 0);
        writeInt(getWordSize());
        writeLong(System.currentTimeMillis());
    }

    private void startTopLevelRecord(HProfTopLevelRecord tag) {
        assert topLevelRecordBegin == -1;
        writeByte(tag.getValue());
        writeInt(0); // timestamp
        writeInt(0); // length (patched later on)
        topLevelRecordBegin = getPosition();
    }

    private void endTopLevelRecord() {
        assert topLevelRecordBegin >= 0;
        long currentPosition = getPosition();
        setPosition(topLevelRecordBegin - Integer.BYTES);
        writeInt(NumUtil.safeToUInt(currentPosition - topLevelRecordBegin));
        setPosition(currentPosition);
        topLevelRecordBegin = -1;
    }

    private void startSubRecord(HProfSubRecord tag, long size) {
        assert topLevelRecordBegin >= 0 : "must be within a HEAP_DUMP_SEGMENT";
        long heapDumpSegmentSize = getPosition() - topLevelRecordBegin;
        if (heapDumpSegmentSize > 0 && heapDumpSegmentSize + size > HEAP_DUMP_SEGMENT_TARGET_SIZE) {
            endTopLevelRecord();
            startTopLevelRecord(HProfTopLevelRecord.HEAP_DUMP_SEGMENT);
        }

        subRecordBegin = getPosition();
        writeByte(tag.getValue());
    }

    private void endSubRecord(long recordSize) {
        assert subRecordBegin >= 0;
        assert subRecordBegin + recordSize == getPosition();
        subRecordBegin = -1;
    }

    private void writeClassNames() {
        for (int i = 0; i < HeapDumpMetadata.getClassInfoCount(); i++) {
            ClassInfo classInfo = HeapDumpMetadata.getClassInfo(i);
            if (ClassInfoAccess.isValid(classInfo)) {
                writeSymbol(classInfo.getHub().getName());
            }
        }
    }

    private void writeSymbol(String value) {
        startTopLevelRecord(HProfTopLevelRecord.UTF8);
        writeObjectId(value);
        writeUTF8(value);
        endTopLevelRecord();
    }

    private void writeSymbol(FieldName fieldName) {
        startTopLevelRecord(HProfTopLevelRecord.UTF8);
        writeFieldNameId(fieldName);
        write((Pointer) FieldNameAccess.getChars(fieldName), WordFactory.unsigned(FieldNameAccess.getLength(fieldName)));
        endTopLevelRecord();
    }

    private void writeFieldNames() {
        FieldNamePointer fieldNameTable = HeapDumpMetadata.getFieldNameTable();
        for (int i = 0; i < HeapDumpMetadata.getFieldNameCount(); i++) {
            FieldName fieldName = fieldNameTable.addressOf(i).read();
            writeSymbol(fieldName);
        }
    }

    private void writeLoadClassRecords() {
        for (int i = 0; i < HeapDumpMetadata.getClassInfoCount(); i++) {
            ClassInfo classInfo = HeapDumpMetadata.getClassInfo(i);
            if (ClassInfoAccess.isValid(classInfo)) {
                DynamicHub hub = classInfo.getHub();
                if (hub.isLoaded()) {
                    startTopLevelRecord(HProfTopLevelRecord.LOAD_CLASS);
                    writeInt(classInfo.getSerialNum());
                    writeClassId(hub);
                    writeInt(DUMMY_STACK_TRACE_ID);
                    writeObjectId(hub.getName());
                    endTopLevelRecord();
                }
            }
        }
    }

    private void writeStackTraces(Pointer currentThreadSp) {
        writeDummyStackTrace();

        /* Write the stack traces of all threads. */
        long nextFrameId = 1;
        int threadSerialNum = 1;
        for (IsolateThread isolateThread = VMThreads.firstThread(); isolateThread.isNonNull(); isolateThread = VMThreads.nextThread(isolateThread)) {
            /* Write FRAME records. */
            int writtenFrames = dumpStackData(isolateThread, currentThreadSp, threadSerialNum, nextFrameId, false);

            /* Write TRACE record that references the FRAME records. */
            startTopLevelRecord(HProfTopLevelRecord.TRACE);
            int stackSerialNum = threadSerialNum + DUMMY_STACK_TRACE_ID;
            writeInt(stackSerialNum);
            writeInt(threadSerialNum);
            writeInt(writtenFrames);
            for (int i = 0; i < writtenFrames; i++) {
                writeFrameId(nextFrameId++);
            }
            endTopLevelRecord();

            threadSerialNum++;
        }
    }

    private int dumpStackData(IsolateThread isolateThread, Pointer currentThreadSp, int threadSerialNum, long nextFrameId, boolean markGCRoots) {
        dumpStackFrameVisitor.initialize(threadSerialNum, nextFrameId, markGCRoots);
        if (isolateThread == CurrentIsolate.getCurrentThread()) {
            JavaStackWalker.walkCurrentThread(currentThreadSp, dumpStackFrameVisitor);
        } else {
            JavaStackWalker.walkThread(isolateThread, dumpStackFrameVisitor);
        }
        return dumpStackFrameVisitor.getWrittenFrames();
    }

    /** Writes an empty TRACE record that can be used in other records. */
    private void writeDummyStackTrace() {
        startTopLevelRecord(HProfTopLevelRecord.TRACE);
        writeInt(DUMMY_STACK_TRACE_ID);
        writeInt(0); // thread serial number
        writeInt(0); // number of frames
        endTopLevelRecord();
    }

    private void writeClasses() {
        for (int i = 0; i < HeapDumpMetadata.getClassInfoCount(); i++) {
            ClassInfo classInfo = HeapDumpMetadata.getClassInfo(i);
            if (ClassInfoAccess.isValid(classInfo)) {
                if (classInfo.getHub().isLoaded()) {
                    writeClassDumpRecord(classInfo);
                }
            }
        }
    }

    private void writeClassDumpRecord(ClassInfo classInfo) {
        int wordSize = getWordSize();
        int staticFieldsCount = classInfo.getStaticFieldCount();
        int staticFieldsSize = staticFieldsCount * (wordSize + 1) + HeapDumpMetadata.computeFieldsDumpSize(classInfo.getStaticFields(), classInfo.getStaticFieldCount());
        int instanceFieldsCount = classInfo.getInstanceFieldCount();
        int instanceFieldsSize = instanceFieldsCount * (wordSize + 1);
        int recordSize = 1 + wordSize + 4 + 6 * wordSize + 4 + 2 + 2 + staticFieldsSize + 2 + instanceFieldsSize;

        Class<?> clazz = DynamicHub.toClass(classInfo.getHub());
        startSubRecord(HProfSubRecord.GC_CLASS_DUMP, recordSize);
        writeClassId(clazz);
        writeInt(DUMMY_STACK_TRACE_ID);
        writeClassId(clazz.getSuperclass());
        writeObjectId(getClassLoader(clazz));
        writeObjectId(clazz.getSigners());
        writeObjectId(null); // protection domain
        writeObjectId(null); // reserved field
        writeObjectId(null); // reserved field
        writeInt(getObjectSizeInHeap(clazz));
        writeShort((short) 0); // size of constant pool
        writeFieldDescriptors(staticFieldsCount, classInfo.getStaticFields(), true);
        writeFieldDescriptors(instanceFieldsCount, classInfo.getInstanceFields(), false);
        endSubRecord(recordSize);
    }

    private void writeThreads(Pointer currentThreadSp) {
        long nextFrameId = 1;
        int threadSerialNum = 1;
        for (IsolateThread isolateThread = VMThreads.firstThread(); isolateThread.isNonNull(); isolateThread = VMThreads.nextThread(isolateThread)) {
            int stackTraceSerialNum = threadSerialNum + DUMMY_STACK_TRACE_ID;
            /*
             * If a thread is not fully initialized yet, then the java.lang.Thread object may still
             * be null. In this case, a thread object id of 0 is written, which is fine according to
             * the HPROF specification.
             */
            Thread thread = PlatformThreads.fromVMThread(isolateThread);
            writeThread(thread, threadSerialNum, stackTraceSerialNum);
            nextFrameId += dumpStackData(isolateThread, currentThreadSp, threadSerialNum, nextFrameId, true);

            writeThreadLocals(isolateThread, threadSerialNum);
            threadSerialNum++;
        }
    }

    private void writeThread(Thread threadObj, int threadSerialNum, int stackTraceSerialNum) {
        int recordSize = 1 + getWordSize() + 4 + 4;
        startSubRecord(HProfSubRecord.GC_ROOT_THREAD_OBJ, recordSize);
        writeObjectId(threadObj);
        writeInt(threadSerialNum);
        writeInt(stackTraceSerialNum);
        endSubRecord(recordSize);
    }

    private void writeThreadLocals(IsolateThread isolateThread, int threadSerialNum) {
        if (SubstrateOptions.MultiThreaded.getValue()) {
            threadLocalVisitor.initialize(threadSerialNum);
            VMThreadLocalMTSupport.singleton().walk(isolateThread, threadLocalVisitor);
        }
    }

    private void writeJNIGlobals() {
        /* All objects that are referenced by the runtime code cache are GC roots. */
        RuntimeCodeInfoMemory.singleton().walkRuntimeMethods(codeMetadataVisitor);
    }

    private void writeStickyClasses() {
        for (int i = 0; i < HeapDumpMetadata.getClassInfoCount(); i++) {
            ClassInfo classInfo = HeapDumpMetadata.getClassInfo(i);
            if (ClassInfoAccess.isValid(classInfo)) {
                int recordSize = 1 + getWordSize();
                startSubRecord(HProfSubRecord.GC_ROOT_STICKY_CLASS, recordSize);
                writeClassId(classInfo.getHub());
                endSubRecord(recordSize);
            }
        }
    }

    private void writeObjects() {
        GrowableArray largeImageHeapObjects = StackValue.get(GrowableArray.class);
        GrowableArrayAccess.initialize(largeImageHeapObjects);

        dumpObjectsVisitor.initialize(true, largeImageHeapObjects);
        Heap.getHeap().walkImageHeapObjects(dumpObjectsVisitor);

        GrowableArray largeCollectedHeapObjects = StackValue.get(GrowableArray.class);
        GrowableArrayAccess.initialize(largeCollectedHeapObjects);

        dumpObjectsVisitor.initialize(false, largeCollectedHeapObjects);
        Heap.getHeap().walkCollectedHeapObjects(dumpObjectsVisitor);

        /* Large objects are collected and written separately. */
        writeLargeObjects(largeImageHeapObjects, true);
        GrowableArrayAccess.freeData(largeImageHeapObjects);
        largeImageHeapObjects = WordFactory.nullPointer();

        writeLargeObjects(largeCollectedHeapObjects, false);
        GrowableArrayAccess.freeData(largeCollectedHeapObjects);
        largeCollectedHeapObjects = WordFactory.nullPointer();
    }

    private void writeLargeObjects(GrowableArray largeObjects, boolean inImageHeap) {
        int count = largeObjects.getSize();
        WordPointer objects = largeObjects.getData();

        for (int i = 0; i < count; i++) {
            Word rawObj = objects.addressOf(i).read();
            writeObject(rawObj.toObject(), inImageHeap);
        }
    }

    private static ClassLoader getClassLoader(Class<?> clazz) {
        Class<?> c = clazz;
        while (c.isArray()) {
            c = c.getComponentType();
        }
        return c.getClassLoader();
    }

    private static int getObjectSizeInHeap(Class<?> cls) {
        DynamicHub hub = DynamicHub.fromClass(cls);
        int encoding = hub.getLayoutEncoding();
        if (LayoutEncoding.isPureInstance(encoding)) {
            /*
             * May underestimate the object size if the identity hashcode field is optional. This is
             * the best that what can do because the HPROF format does not support that instances of
             * one class have different object sizes.
             */
            return (int) LayoutEncoding.getPureInstanceAllocationSize(encoding).rawValue();
        } else if (LayoutEncoding.isHybrid(encoding)) {
            /* For hybrid objects, return the size of the fields. */
            return LayoutEncoding.getArrayBaseOffsetAsInt(encoding);
        } else {
            /* Variable size. */
            return 0;
        }
    }

    private void writeFieldDescriptors(int fieldCount, FieldInfoPointer fieldInfos, boolean staticFields) {
        writeShort(NumUtil.safeToUShort(fieldCount));
        for (int i = 0; i < fieldCount; i++) {
            FieldInfo field = fieldInfos.addressOf(i).read();
            writeFieldNameId(FieldInfoAccess.getFieldName(field));
            byte typeCode = FieldInfoAccess.getStorageKind(field);
            writeType(HProfType.fromStorageKind(typeCode));

            if (staticFields) {
                /* For static fields, write the field value to the heap dump as well. */
                boolean isObjectField = typeCode == StorageKind.OBJECT;
                writeField(getStaticFieldData(isObjectField), field);
            }
        }
    }

    private static Object getStaticFieldData(boolean isObjectField) {
        if (isObjectField) {
            return StaticFieldsSupport.getStaticObjectFields();
        } else {
            return StaticFieldsSupport.getStaticPrimitiveFields();
        }
    }

    private void writeField(Object obj, FieldInfo field) {
        Pointer p = Word.objectToUntrackedPointer(obj);
        int location = FieldInfoAccess.getLocation(field);
        byte storageKind = FieldInfoAccess.getStorageKind(field);
        switch (storageKind) {
            case StorageKind.BOOLEAN, StorageKind.BYTE -> writeByte(p.readByte(location));
            case StorageKind.CHAR -> writeChar(p.readChar(location));
            case StorageKind.SHORT -> writeShort(p.readShort(location));
            case StorageKind.INT -> writeInt(p.readInt(location));
            case StorageKind.LONG -> writeLong(p.readLong(location));
            case StorageKind.FLOAT -> writeFloat(p.readFloat(location));
            case StorageKind.DOUBLE -> writeDouble(p.readDouble(location));
            case StorageKind.OBJECT -> writeObjectId(ReferenceAccess.singleton().readObjectAt(p.add(location), true));
            default -> throw VMError.shouldNotReachHere("Unexpected storage kind.");
        }
    }

    private void writeObject(Object obj, boolean inImageHeap) {
        DynamicHub hub = KnownIntrinsics.readHub(obj);
        int layoutEncoding = hub.getLayoutEncoding();
        if (LayoutEncoding.isArray(layoutEncoding)) {
            if (LayoutEncoding.isPrimitiveArray(layoutEncoding)) {
                writePrimitiveArray(obj, layoutEncoding);
            } else {
                writeObjectArray(obj);
            }
        } else {
            /*
             * Hybrid objects are handled here as well. This means that the array part of hybrid
             * objects is currently skipped. It would be better to dump the array part as a separate
             * array object.
             */
            writeInstance(obj);
        }

        if (inImageHeap) {
            markImageHeapObjectAsGCRoot(obj);
        }

        /*
         * Ideally, we would model Java monitors as instance fields (they are only reachable if the
         * object that owns the monitor is reachable), however that is not possible because the
         * synthetic monitor field may overlap or collide with a normal field of a subclass.
         * Therefore, we simply mark all monitors as GC roots.
         */
        int monitorOffset = hub.getMonitorOffset();
        if (monitorOffset != 0) {
            Object monitor = ObjectAccess.readObject(obj, monitorOffset);
            if (monitor != null) {
                markMonitorAsGCRoot(monitor);
            }
        }
    }

    private void markImageHeapObjectAsGCRoot(Object obj) {
        assert Heap.getHeap().isInImageHeap(obj);
        markAsGCRoot0(obj);
    }

    private void markCodeMetadataAsGCRoot(Object obj) {
        markAsGCRoot0(obj);
    }

    /**
     * We use GC_ROOT_JNI_GLOBAL for all concepts that don't exist on HotSpot, e.g., the image heap
     * or {@link DeoptimizedFrame}s.
     */
    private void markAsGCRoot0(Object obj) {
        int recordSize = 1 + 2 * getWordSize();
        startSubRecord(HProfSubRecord.GC_ROOT_JNI_GLOBAL, recordSize);
        writeObjectId(obj);
        writeObjectId(null); // global ref ID
        endSubRecord(recordSize);
    }

    private void markMonitorAsGCRoot(Object monitor) {
        int recordSize = 1 + getWordSize();
        startSubRecord(HProfSubRecord.GC_ROOT_MONITOR_USED, recordSize);
        writeObjectId(monitor);
        endSubRecord(recordSize);
    }

    private void markThreadLocalAsGCRoot(Object obj, int threadSerialNum) {
        int recordSize = 1 + getWordSize() + 4 + 4;
        startSubRecord(HProfSubRecord.GC_ROOT_JNI_LOCAL, recordSize);
        writeObjectId(obj);
        writeInt(threadSerialNum);
        writeInt(-1); // empty stack
        endSubRecord(recordSize);
    }

    private void writeInstance(Object obj) {
        ClassInfo classInfo = HeapDumpMetadata.getClassInfo(obj.getClass());
        int wordSize = getWordSize();
        int instanceFieldsSize = classInfo.getInstanceFieldsDumpSize();
        int recordSize = 1 + wordSize + 4 + wordSize + 4 + instanceFieldsSize;

        startSubRecord(HProfSubRecord.GC_INSTANCE_DUMP, recordSize);
        writeObjectId(obj);
        writeInt(DUMMY_STACK_TRACE_ID);
        writeClassId(obj.getClass());
        writeInt(instanceFieldsSize);

        /* Write the field data. */
        do {
            int instanceFieldCount = classInfo.getInstanceFieldCount();
            FieldInfoPointer instanceFields = classInfo.getInstanceFields();
            for (int i = 0; i < instanceFieldCount; i++) {
                FieldInfo field = instanceFields.addressOf(i).read();
                writeField(obj, field);
            }
            classInfo = HeapDumpMetadata.getClassInfo(classInfo.getHub().getSuperHub());
        } while (classInfo.isNonNull());

        endSubRecord(recordSize);
    }

    private void writePrimitiveArray(Object array, int layoutEncoding) {
        int arrayBaseOffset = LayoutEncoding.getArrayBaseOffsetAsInt(layoutEncoding);
        int elementSize = LayoutEncoding.getArrayIndexScale(layoutEncoding);

        int recordHeaderSize = 1 + getWordSize() + 2 * 4 + 1;
        int length = calculateMaxArrayLength(array, elementSize, recordHeaderSize);
        long recordSize = recordHeaderSize + ((long) length) * elementSize;

        startSubRecord(HProfSubRecord.GC_PRIM_ARRAY_DUMP, recordSize);
        writeObjectId(array);
        writeInt(DUMMY_STACK_TRACE_ID);
        writeInt(length);

        /* The file is big endian, so we need to read & write the data element-wise. */
        if (array instanceof boolean[]) {
            writeType(HProfType.BOOLEAN);
            writeU1ArrayData(array, length, arrayBaseOffset);
        } else if (array instanceof byte[]) {
            writeType(HProfType.BYTE);
            writeU1ArrayData(array, length, arrayBaseOffset);
        } else if (array instanceof short[]) {
            writeType(HProfType.SHORT);
            writeU2ArrayData(array, length, arrayBaseOffset);
        } else if (array instanceof char[]) {
            writeType(HProfType.CHAR);
            writeU2ArrayData(array, length, arrayBaseOffset);
        } else if (array instanceof int[]) {
            writeType(HProfType.INT);
            writeU4ArrayData(array, length, arrayBaseOffset);
        } else if (array instanceof float[]) {
            writeType(HProfType.FLOAT);
            writeU4ArrayData(array, length, arrayBaseOffset);
        } else if (array instanceof long[]) {
            writeType(HProfType.LONG);
            writeU8ArrayData(array, length, arrayBaseOffset);
        } else if (array instanceof double[]) {
            writeType(HProfType.DOUBLE);
            writeU8ArrayData(array, length, arrayBaseOffset);
        } else {
            /* Word arrays are primitive arrays as well */
            assert WordBase.class.isAssignableFrom(array.getClass().getComponentType());
            assert elementSize == getWordSize();
            writeWordArray(array, length, arrayBaseOffset);
        }
        endSubRecord(recordSize);
    }

    private void writeObjectArray(Object array) {
        int wordSize = getWordSize();
        int recordHeaderSize = 1 + 2 * 4 + 2 * wordSize;
        /* In the heap dump, object array elements are always uncompressed. */
        int length = calculateMaxArrayLength(array, wordSize, recordHeaderSize);
        long recordSize = recordHeaderSize + ((long) length) * wordSize;

        startSubRecord(HProfSubRecord.GC_OBJ_ARRAY_DUMP, recordSize);
        writeObjectId(array);
        writeInt(DUMMY_STACK_TRACE_ID);
        writeInt(length);
        writeClassId(array.getClass());

        Object[] data = (Object[]) array;
        for (int i = 0; i < length; i++) {
            writeObjectId(data[i]);
        }
        endSubRecord(recordSize);
    }

    /*
     * Hprof uses an u4 as the record length field, which means we need to truncate arrays that are
     * too long.
     */
    private static int calculateMaxArrayLength(Object array, int elementSize, int recordHeaderSize) {
        int length = ArrayLengthNode.arrayLength(array);
        UnsignedWord lengthInBytes = WordFactory.unsigned(length).multiply(elementSize);
        UnsignedWord maxBytes = WordFactory.unsigned((1L << 32) - 1).subtract(recordHeaderSize);

        if (lengthInBytes.belowOrEqual(maxBytes)) {
            return length;
        }

        UnsignedWord newLength = maxBytes.unsignedDivide(elementSize);
        Log.log().string("Cannot dump very large arrays. Array is truncated to ").unsigned(newLength).string(" elements.").newline();
        return NumUtil.safeToInt(newLength.rawValue());
    }

    private void writeWordArray(Object array, int length, int arrayBaseOffset) {
        if (getWordSize() == 8) {
            writeType(HProfType.LONG);
            writeU8ArrayData(array, length, arrayBaseOffset);
        } else {
            assert getWordSize() == 4;
            writeType(HProfType.INT);
            writeU4ArrayData(array, length, arrayBaseOffset);
        }
    }

    private void writeU1ArrayData(Object array, int length, int arrayBaseOffset) {
        Pointer data = getArrayData(array, arrayBaseOffset);
        write(data, WordFactory.unsigned(length));
    }

    private void writeU2ArrayData(Object array, int length, int arrayBaseOffset) {
        Pointer cur = getArrayData(array, arrayBaseOffset);
        for (int i = 0; i < length; i++) {
            writeChar(cur.readChar(0));
            cur = cur.add(2);
        }
    }

    private void writeU4ArrayData(Object array, int length, int arrayBaseOffset) {
        Pointer cur = getArrayData(array, arrayBaseOffset);
        for (int i = 0; i < length; i++) {
            writeInt(cur.readInt(0));
            cur = cur.add(4);
        }
    }

    private void writeU8ArrayData(Object array, int length, int arrayBaseOffset) {
        Pointer cur = getArrayData(array, arrayBaseOffset);
        for (int i = 0; i < length; i++) {
            writeLong(cur.readLong(0));
            cur = cur.add(8);
        }
    }

    private static Pointer getArrayData(Object array, int arrayBaseOffset) {
        return Word.objectToUntrackedPointer(array).add(arrayBaseOffset);
    }

    private void writeByte(byte value) {
        boolean success = getFileSupport().writeByte(f, value);
        handleError(success);
    }

    private void writeType(HProfType type) {
        writeByte(type.getValue());
    }

    private void writeShort(short value) {
        boolean success = getFileSupport().writeShort(f, value);
        handleError(success);
    }

    private void writeChar(char value) {
        boolean success = getFileSupport().writeChar(f, value);
        handleError(success);
    }

    private void writeInt(int value) {
        boolean success = getFileSupport().writeInt(f, value);
        handleError(success);
    }

    private void writeLong(long value) {
        boolean success = getFileSupport().writeLong(f, value);
        handleError(success);
    }

    private void writeFloat(float value) {
        boolean success = getFileSupport().writeFloat(f, value);
        handleError(success);
    }

    private void writeDouble(double value) {
        boolean success = getFileSupport().writeDouble(f, value);
        handleError(success);
    }

    private void writeObjectId(Object obj) {
        writeId(Word.objectToUntrackedPointer(obj).rawValue());
    }

    private void writeClassId(Class<?> clazz) {
        writeClassId(DynamicHub.fromClass(clazz));
    }

    private void writeClassId(DynamicHub hub) {
        /*
         * HotSpot writes Class objects only as GC_CLASS_DUMP and never as GC_INSTANCE_DUMP records.
         * It also always uses the address of the mirror class as the class id. This has the effect
         * that the heap dump only contains a very limited set of information for class objects.
         *
         * It is handy to have detailed information about the DynamicHub in the heap dump. Ideally,
         * we would just write both a GC_CLASS_DUMP and a GC_INSTANCE_DUMP record with the same id
         * but that breaks VisualVM in a weird way. Therefore, we are using different ids for the
         * GC_CLASS_DUMP and GC_INSTANCE_DUMP records.
         */
        Word hubAddress = Word.objectToUntrackedPointer(hub);
        if (hubAddress.isNonNull()) {
            hubAddress = hubAddress.add(1);
        }
        writeId(hubAddress.rawValue());
    }

    private void writeId(long value) {
        boolean success;
        if (getWordSize() == 8) {
            success = getFileSupport().writeLong(f, value);
        } else {
            assert getWordSize() == 4;
            success = getFileSupport().writeInt(f, (int) value);
        }
        handleError(success);
    }

    private void writeFieldNameId(FieldName fieldName) {
        boolean success = getFileSupport().writeLong(f, fieldName.rawValue());
        handleError(success);
    }

    private void writeFrameId(long frameId) {
        boolean success = getFileSupport().writeLong(f, frameId);
        handleError(success);
    }

    private void writeUTF8(String value) {
        boolean success = getFileSupport().writeUTF8(f, value);
        handleError(success);
    }

    private void write(Pointer data, UnsignedWord size) {
        boolean success = getFileSupport().write(f, data, size);
        handleError(success);
    }

    private long getPosition() {
        long result = getFileSupport().position(f);
        handleError(result >= 0);
        return result;
    }

    private void setPosition(long newPos) {
        boolean success = getFileSupport().seek(f, newPos);
        handleError(success);
    }

    private void flush() {
        boolean success = getFileSupport().flush(f);
        handleError(success);
    }

    private void handleError(boolean success) {
        if (!success) {
            error = true;
        }
    }

    @Fold
    static BufferedFileOperationSupport getFileSupport() {
        return BufferedFileOperationSupport.bigEndian();
    }

    @Fold
    static int getWordSize() {
        return ConfigurationValues.getTarget().wordSize;
    }

    /**
     * This class is used for different purposes:
     * <ul>
     * <li>Write a {@link HProfTopLevelRecord#FRAME} top-level record for every frame on the
     * stack.</li>
     * <li>Write a GC_ROOT sub-record for every deoptimized frame and every references that is on
     * the stack.</li>
     * </ul>
     *
     * Unfortunately, it is not possible to write all the information in a single pass because the
     * data needs to end up in different HPROF records (top-level vs. sub-records). The data from
     * the different passes needs to match though, therefore we use a {@link #markGCRoots field} to
     * determine which data should be written.
     */
    private class DumpStackFrameVisitor extends StackFrameVisitor implements ObjectReferenceVisitor {
        private static final int LINE_NUM_NATIVE_METHOD = -3;

        private final CodeInfoDecoder.FrameInfoCursor frameInfoCursor = new CodeInfoDecoder.FrameInfoCursor();

        private int threadSerialNum;
        private long initialNextFrameId;
        private long nextFrameId;
        private boolean markGCRoots;

        @Platforms(Platform.HOSTED_ONLY.class)
        DumpStackFrameVisitor() {
        }

        @SuppressWarnings("hiding")
        public void initialize(int threadSerialNum, long nextFrameId, boolean markGCRoots) {
            assert nextFrameId > 0;
            assert threadSerialNum > 0;
            assert nextFrameId > 0;

            this.threadSerialNum = threadSerialNum;
            this.initialNextFrameId = nextFrameId;
            this.nextFrameId = nextFrameId;
            this.markGCRoots = markGCRoots;
        }

        public int getWrittenFrames() {
            return NumUtil.safeToInt(nextFrameId - initialNextFrameId);
        }

        @Override
        @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Heap dumping must not allocate.")
        protected boolean visitFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptimizedFrame) {
            if (deoptimizedFrame != null) {
                markAsGCRoot(deoptimizedFrame);

                for (DeoptimizedFrame.VirtualFrame frame = deoptimizedFrame.getTopFrame(); frame != null; frame = frame.getCaller()) {
                    visitFrame(frame.getFrameInfo());
                    nextFrameId++;
                }
            } else {
                /*
                 * All references that are on the stack need to be marked as GC roots. Our
                 * information is not necessarily precise enough to identify to which Java-level
                 * stack frame a reference belongs. Therefore, we just dump the data in a way that
                 * it gets associated with the deepest inlined Java-level stack frame of each
                 * compilation unit.
                 */
                markStackValuesAsGCRoots(sp, ip, codeInfo);

                frameInfoCursor.initialize(codeInfo, ip);
                while (frameInfoCursor.advance()) {
                    FrameInfoQueryResult frame = frameInfoCursor.get();
                    visitFrame(frame);
                    nextFrameId++;
                }
            }
            return true;
        }

        private void markAsGCRoot(DeoptimizedFrame frame) {
            if (markGCRoots) {
                markAsGCRoot0(frame);
            }
        }

        private void markStackValuesAsGCRoots(Pointer sp, CodePointer ip, CodeInfo codeInfo) {
            if (markGCRoots) {
                SimpleCodeInfoQueryResult queryResult = StackValue.get(SimpleCodeInfoQueryResult.class);
                CodeInfoAccess.lookupCodeInfo(codeInfo, CodeInfoAccess.relativeIP(codeInfo, ip), queryResult);

                NonmovableArray<Byte> referenceMapEncoding = CodeInfoAccess.getStackReferenceMapEncoding(codeInfo);
                long referenceMapIndex = queryResult.getReferenceMapIndex();
                if (referenceMapIndex == ReferenceMapIndex.NO_REFERENCE_MAP) {
                    throw CodeInfoTable.reportNoReferenceMap(sp, ip, codeInfo);
                }
                CodeReferenceMapDecoder.walkOffsetsFromPointer(sp, referenceMapEncoding, referenceMapIndex, this, null);
            }
        }

        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed, Object holderObject) {
            assert markGCRoots;

            Object obj = ReferenceAccess.singleton().readObjectAt(objRef, compressed);
            if (obj != null) {
                int recordSize = 1 + getWordSize() + 4 + 4;
                startSubRecord(HProfSubRecord.GC_ROOT_JAVA_FRAME, recordSize);
                writeObjectId(obj);
                writeInt(threadSerialNum);
                /* Position of the stack frame in the stack trace. */
                writeInt(getWrittenFrames());
            }
            return true;
        }

        private void visitFrame(FrameInfoQueryResult frame) {
            if (!markGCRoots) {
                /*
                 * Write all UTF-8 symbols that are needed when writing the frame. Ideally, we would
                 * de-duplicate the symbols but it is not really crucial to do so.
                 */
                String methodName = getMethodName(frame);
                String sourceFileName = getSourceFileName(frame);
                writeSymbol(methodName);
                writeSymbol(""); // method signature
                writeSymbol(sourceFileName);

                /* Write the FRAME record. */
                ClassInfo classInfo = HeapDumpMetadata.getClassInfo(frame.getSourceClass());
                int lineNumber = getLineNumber(frame);
                writeFrame(classInfo.getSerialNum(), lineNumber, methodName, sourceFileName);
            }
        }

        private void writeFrame(int classSerialNum, int lineNumber, String methodName, String sourceFileName) {
            assert !markGCRoots;

            startTopLevelRecord(HProfTopLevelRecord.FRAME);
            writeFrameId(nextFrameId);
            writeObjectId(methodName);
            writeObjectId(""); // method signature
            writeObjectId(sourceFileName);
            writeInt(classSerialNum);
            writeInt(lineNumber);
            endTopLevelRecord();
        }

        private String getMethodName(FrameInfoQueryResult frame) {
            String methodName = frame.getSourceMethodName();
            if (methodName == null || methodName.isEmpty()) {
                methodName = "";
            }
            return methodName;
        }

        private String getSourceFileName(FrameInfoQueryResult frame) {
            String sourceFileName = frame.getSourceFileName();
            if (sourceFileName == null || sourceFileName.isEmpty()) {
                sourceFileName = "Unknown Source";
            }
            return sourceFileName;
        }

        private int getLineNumber(FrameInfoQueryResult frame) {
            if (frame.isNativeMethod()) {
                return LINE_NUM_NATIVE_METHOD;
            }
            return frame.getSourceLineNumber();
        }
    }

    private class DumpObjectsVisitor implements ObjectVisitor {
        private boolean inImageHeap;
        private GrowableArray largeObjects;

        @Platforms(Platform.HOSTED_ONLY.class)
        DumpObjectsVisitor() {
        }

        @SuppressWarnings("hiding")
        public void initialize(boolean inImageHeap, GrowableArray largeObjects) {
            this.inImageHeap = inImageHeap;
            this.largeObjects = largeObjects;
        }

        @Override
        public boolean visitObject(Object obj) {
            if (isLarge(obj)) {
                boolean added = GrowableArrayAccess.add(largeObjects, Word.objectToUntrackedPointer(obj));
                if (!added) {
                    Log.log().string("Failed to add an element to the large object list. Heap dump will be incomplete.").newline();
                }
                return true;
            }

            writeObject(obj, inImageHeap);
            return true;
        }

        private boolean isLarge(Object obj) {
            return getObjectSize(obj).aboveThan(LARGE_OBJECT_THRESHOLD);
        }

        private UnsignedWord getObjectSize(Object obj) {
            int layoutEncoding = KnownIntrinsics.readHub(obj).getLayoutEncoding();
            if (LayoutEncoding.isArray(layoutEncoding)) {
                int elementSize;
                if (LayoutEncoding.isPrimitiveArray(layoutEncoding)) {
                    elementSize = LayoutEncoding.getArrayIndexScale(layoutEncoding);
                } else {
                    elementSize = getWordSize();
                }
                int length = ArrayLengthNode.arrayLength(obj);
                return WordFactory.unsigned(length).multiply(elementSize);
            } else {
                ClassInfo classInfo = HeapDumpMetadata.getClassInfo(obj.getClass());
                return WordFactory.unsigned(classInfo.getInstanceFieldsDumpSize());
            }
        }
    }

    private class CodeMetadataVisitor implements RuntimeCodeCache.CodeInfoVisitor, ObjectReferenceVisitor {
        @Platforms(Platform.HOSTED_ONLY.class)
        CodeMetadataVisitor() {
        }

        @Override
        public boolean visitCode(CodeInfo info) {
            RuntimeCodeInfoAccess.walkObjectFields(info, this);
            return true;
        }

        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed, Object holderObject) {
            Object obj = ReferenceAccess.singleton().readObjectAt(objRef, compressed);
            if (obj != null) {
                markCodeMetadataAsGCRoot(obj);
            }
            return true;
        }
    }

    private class ThreadLocalVisitor implements ObjectReferenceVisitor {
        private int threadSerialNum;

        @Platforms(Platform.HOSTED_ONLY.class)
        ThreadLocalVisitor() {
        }

        @SuppressWarnings("hiding")
        public void initialize(int threadSerialNum) {
            this.threadSerialNum = threadSerialNum;
        }

        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed, Object holderObject) {
            Object obj = ReferenceAccess.singleton().readObjectAt(objRef, compressed);
            if (obj != null) {
                markThreadLocalAsGCRoot(obj, threadSerialNum);
            }
            return true;
        }
    }
}
