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
package com.oracle.svm.core.heapdump;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.stack.JavaStackFrameVisitor;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;

/*
 * This class writes Java heap in hprof binary format. The class is heavily
 * influenced by 'HeapHprofBinWriter' implementation.
 */

/* hprof binary format originally published at:
* <https://java.net/downloads/heap-snapshot/hprof-binary-format.html>
*
*
* header    "JAVA PROFILE 1.0.1" or "JAVA PROFILE 1.0.2" (0-terminated)
* u4        size of identifiers. Identifiers are used to represent
*            UTF8 strings, objects, stack traces, etc. They usually
*            have the same size as host pointers. For example, on
*            Solaris and Win32, the size is 4.
* u4         high word
* u4         low word    number of milliseconds since 0:00 GMT, 1/1/70
* [record]*  a sequence of records.
*
*/

/*
*
* Record format:
*
* u1         a TAG denoting the type of the record
* u4         number of *microseconds* since the time stamp in the
*            header. (wraps around in a little more than an hour)
* u4         number of bytes *remaining* in the record. Note that
*            this number excludes the tag and the length field itself.
* [u1]*      BODY of the record (a sequence of bytes)
*/

/*
* The following TAGs are supported:
*
* TAG           BODY       notes
*----------------------------------------------------------
* HPROF_UTF8               a UTF8-encoded name
*
*               id         name ID
*               [u1]*      UTF8 characters (no trailing zero)
*
* HPROF_LOAD_CLASS         a newly loaded class
*
*                u4        class serial number (> 0)
*                id        class object ID
*                u4        stack trace serial number
*                id        class name ID
*
* HPROF_UNLOAD_CLASS       an unloading class
*
*                u4        class serial_number
*
* HPROF_FRAME              a Java stack frame
*
*                id        stack frame ID
*                id        method name ID
*                id        method signature ID
*                id        source file name ID
*                u4        class serial number
*                i4        line number. >0: normal
*                                       -1: unknown
*                                       -2: compiled method
*                                       -3: native method
*
* HPROF_TRACE              a Java stack trace
*
*               u4         stack trace serial number
*               u4         thread serial number
*               u4         number of frames
*               [id]*      stack frame IDs
*
*
* HPROF_ALLOC_SITES        a set of heap allocation sites, obtained after GC
*
*               u2         flags 0x0001: incremental vs. complete
*                                0x0002: sorted by allocation vs. live
*                                0x0004: whether to force a GC
*               u4         cutoff ratio
*               u4         total live bytes
*               u4         total live instances
*               u8         total bytes allocated
*               u8         total instances allocated
*               u4         number of sites that follow
*               [u1        is_array: 0:  normal object
*                                    2:  object array
*                                    4:  boolean array
*                                    5:  char array
*                                    6:  float array
*                                    7:  double array
*                                    8:  byte array
*                                    9:  short array
*                                    10: int array
*                                    11: long array
*                u4        class serial number (may be zero during startup)
*                u4        stack trace serial number
*                u4        number of bytes alive
*                u4        number of instances alive
*                u4        number of bytes allocated
*                u4]*      number of instance allocated
*
* HPROF_START_THREAD       a newly started thread.
*
*               u4         thread serial number (> 0)
*               id         thread object ID
*               u4         stack trace serial number
*               id         thread name ID
*               id         thread group name ID
*               id         thread group parent name ID
*
* HPROF_END_THREAD         a terminating thread.
*
*               u4         thread serial number
*
* HPROF_HEAP_SUMMARY       heap summary
*
*               u4         total live bytes
*               u4         total live instances
*               u8         total bytes allocated
*               u8         total instances allocated
*
* HPROF_HEAP_DUMP          denote a heap dump
*
*               [heap dump sub-records]*
*
*                          There are four kinds of heap dump sub-records:
*
*               u1         sub-record type
*
*               HPROF_GC_ROOT_UNKNOWN         unknown root
*
*                          id         object ID
*
*               HPROF_GC_ROOT_THREAD_OBJ      thread object
*
*                          id         thread object ID  (may be 0 for a
*                                     thread newly attached through JNI)
*                          u4         thread sequence number
*                          u4         stack trace sequence number
*
*               HPROF_GC_ROOT_JNI_GLOBAL      JNI global ref root
*
*                          id         object ID
*                          id         JNI global ref ID
*
*               HPROF_GC_ROOT_JNI_LOCAL       JNI local ref
*
*                          id         object ID
*                          u4         thread serial number
*                          u4         frame # in stack trace (-1 for empty)
*
*               HPROF_GC_ROOT_JAVA_FRAME      Java stack frame
*
*                          id         object ID
*                          u4         thread serial number
*                          u4         frame # in stack trace (-1 for empty)
*
*               HPROF_GC_ROOT_NATIVE_STACK    Native stack
*
*                          id         object ID
*                          u4         thread serial number
*
*               HPROF_GC_ROOT_STICKY_CLASS    System class
*
*                          id         object ID
*
*               HPROF_GC_ROOT_THREAD_BLOCK    Reference from thread block
*
*                          id         object ID
*                          u4         thread serial number
*
*               HPROF_GC_ROOT_MONITOR_USED    Busy monitor
*
*                          id         object ID
*
*               HPROF_GC_CLASS_DUMP           dump of a class object
*
*                          id         class object ID
*                          u4         stack trace serial number
*                          id         super class object ID
*                          id         class loader object ID
*                          id         signers object ID
*                          id         protection domain object ID
*                          id         reserved
*                          id         reserved
*
*                          u4         instance size (in bytes)
*
*                          u2         size of constant pool
*                          [u2,       constant pool index,
*                           ty,       type
*                                     2:  object
*                                     4:  boolean
*                                     5:  char
*                                     6:  float
*                                     7:  double
*                                     8:  byte
*                                     9:  short
*                                     10: int
*                                     11: long
*                           vl]*      and value
*
*                          u2         number of static fields
*                          [id,       static field name,
*                           ty,       type,
*                           vl]*      and value
*
*                          u2         number of inst. fields (not inc. super)
*                          [id,       instance field name,
*                           ty]*      type
*
*               HPROF_GC_INSTANCE_DUMP        dump of a normal object
*
*                          id         object ID
*                          u4         stack trace serial number
*                          id         class object ID
*                          u4         number of bytes that follow
*                          [vl]*      instance field values (class, followed
*                                     by super, super's super ...)
*
*               HPROF_GC_OBJ_ARRAY_DUMP       dump of an object array
*
*                          id         array object ID
*                          u4         stack trace serial number
*                          u4         number of elements
*                          id         array class ID
*                          [id]*      elements
*
*               HPROF_GC_PRIM_ARRAY_DUMP      dump of a primitive array
*
*                          id         array object ID
*                          u4         stack trace serial number
*                          u4         number of elements
*                          u1         element type
*                                     4:  boolean array
*                                     5:  char array
*                                     6:  float array
*                                     7:  double array
*                                     8:  byte array
*                                     9:  short array
*                                     10: int array
*                                     11: long array
*                          [u1]*      elements
*
* HPROF_CPU_SAMPLES        a set of sample traces of running threads
*
*                u4        total number of samples
*                u4        # of traces
*               [u4        # of samples
*                u4]*      stack trace serial number
*
* HPROF_CONTROL_SETTINGS   the settings of on/off switches
*
*                u4        0x00000001: alloc traces on/off
*                          0x00000002: cpu sampling on/off
*                u2        stack trace depth
*
*
* When the header is "JAVA PROFILE 1.0.2" a heap dump can optionally
* be generated as a sequence of heap dump segments. This sequence is
* terminated by an end record. The additional tags allowed by format
* "JAVA PROFILE 1.0.2" are:
*
* HPROF_HEAP_DUMP_SEGMENT  denote a heap dump segment
*
*               [heap dump sub-records]*
*               The same sub-record types allowed by HPROF_HEAP_DUMP
*
* HPROF_HEAP_DUMP_END      denotes the end of a heap dump
*
*/
public class HeapDumpWriterImpl extends HeapDumpWriter {

    /**
     * The heap size threshold used to determine if segmented format ("JAVA PROFILE 1.0.2") should
     * be used.
     */
    private static final long HPROF_SEGMENTED_HEAP_DUMP_THRESHOLD = 2L * 1024 * 1024 * 1024;

    /**
     * The approximate size of a heap segment. Used to calculate when to create a new segment.
     */
    private static final long HPROF_SEGMENTED_HEAP_DUMP_SEGMENT_SIZE = 1L * 1024 * 1024 * 1024;

    /**
     * The approximate size of a heap segment for no seek case. Used to calculate when to create a
     * new segment.
     */
    private static final int HPROF_NOSEEK_HEAP_DUMP_SEGMENT_SIZE = 1 * 1024 * 1024;

    /** hprof binary file header. */
    private static final String HPROF_HEADER_1_0_1 = "JAVA PROFILE 1.0.1";
    private static final String HPROF_HEADER_1_0_2 = "JAVA PROFILE 1.0.2";

    /** Constants in enum HprofTag. */
    private static final int HPROF_UTF8 = 0x01;
    private static final int HPROF_LOAD_CLASS = 0x02;
    /* private static final int HPROF_UNLOAD_CLASS = 0x03; */
    private static final int HPROF_FRAME = 0x04;
    private static final int HPROF_TRACE = 0x05;
    /* private static final int HPROF_ALLOC_SITES = 0x06; */
    /* private static final int HPROF_HEAP_SUMMARY = 0x07; */
    /* private static final int HPROF_START_THREAD = 0x0A; */
    /* private static final int HPROF_END_THREAD = 0x0B; */
    private static final int HPROF_HEAP_DUMP = 0x0C;
    /* private static final int HPROF_CPU_SAMPLES = 0x0D; */
    /* private static final int HPROF_CONTROL_SETTINGS = 0x0E; */

    /* 1.0.2 record types. */
    private static final int HPROF_HEAP_DUMP_SEGMENT = 0x1C;
    private static final int HPROF_HEAP_DUMP_END = 0x2C;

    /* Heap dump constants */
    /* Constants in enum HprofGcTag. */
    private static final int HPROF_GC_ROOT_UNKNOWN = 0xFF;
    private static final int HPROF_GC_ROOT_JNI_GLOBAL = 0x01;
    /* private static final int HPROF_GC_ROOT_JNI_LOCAL = 0x02; */
    /* private static final int HPROF_GC_ROOT_JAVA_FRAME = 0x03; */
    /* private static final int HPROF_GC_ROOT_NATIVE_STACK = 0x04; */
    private static final int HPROF_GC_ROOT_STICKY_CLASS = 0x05;
    /* private static final int HPROF_GC_ROOT_THREAD_BLOCK = 0x06; */
    /* private static final int HPROF_GC_ROOT_MONITOR_USED = 0x07; */
    private static final int HPROF_GC_ROOT_THREAD_OBJ = 0x08;
    private static final int HPROF_GC_CLASS_DUMP = 0x20;
    private static final int HPROF_GC_INSTANCE_DUMP = 0x21;
    private static final int HPROF_GC_OBJ_ARRAY_DUMP = 0x22;
    private static final int HPROF_GC_PRIM_ARRAY_DUMP = 0x23;

    /* Constants in enum HprofType. */
    private static final int HPROF_NORMAL_OBJECT = 2;
    private static final int HPROF_BOOLEAN = 4;
    private static final int HPROF_CHAR = 5;
    private static final int HPROF_FLOAT = 6;
    private static final int HPROF_DOUBLE = 7;
    private static final int HPROF_BYTE = 8;
    private static final int HPROF_SHORT = 9;
    private static final int HPROF_INT = 10;
    private static final int HPROF_LONG = 11;

    /* Java type codes. */
    private static final char JVM_SIGNATURE_BOOLEAN = 'Z';
    private static final char JVM_SIGNATURE_CHAR = 'C';
    private static final char JVM_SIGNATURE_BYTE = 'B';
    private static final char JVM_SIGNATURE_SHORT = 'S';
    private static final char JVM_SIGNATURE_INT = 'I';
    private static final char JVM_SIGNATURE_LONG = 'J';
    private static final char JVM_SIGNATURE_FLOAT = 'F';
    private static final char JVM_SIGNATURE_DOUBLE = 'D';
    private static final char JVM_SIGNATURE_ARRAY = '[';
    private static final char JVM_SIGNATURE_CLASS = 'L';

    /*
     * We don't have allocation site info. We write a dummy stack trace with this id.
     */
    private static final int DUMMY_STACK_TRACE_ID = 1;
    /* private static final int EMPTY_FRAME_DEPTH = -1; */

    private static final Field[] ZERO_FIELD_ARR = new Field[0];

    /** Pre-allocated exceptions, for throwing from code that must not allocate. */
    private static final RuntimeException heapSegmentSizeOverflowException = new RuntimeException("Heap segment size overflow.");

    private AllocationFreeDataOutputStream out;
    private HeapDumpUtils heapDumpUtils;
    private Map<String, List<Field>> fieldsMap;
    private ClassToClassDataMap classDataCache;

    /* Added for hprof file format 1.0.2 support. */
    private boolean useSegmentedHeapDump;
    private long currentSegmentStart;
    private long segmentSize;

    @Override
    public void writeHeapTo(AllocationFreeOutputStream dataOutputStream, boolean gcBefore) throws IOException {
        initialize(true, HPROF_NOSEEK_HEAP_DUMP_SEGMENT_SIZE);

        WriterOperation writerOperation = new WriterOperation(dataOutputStream, gcBefore, HPROF_NOSEEK_HEAP_DUMP_SEGMENT_SIZE);
        writerOperation.enqueue();
        IOException operationException = writerOperation.getException();
        if (operationException != null) {
            throw operationException;
        }

        /*
         * Close the data stream. Needs to be done outside of the VMOperation because it uses
         * synchronization.
         */
        dataOutputStream.close();
    }

    @Override
    public void writeHeapTo(FileOutputStream fileOutputStream, boolean gcBefore) throws IOException {
        initialize(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() > HPROF_SEGMENTED_HEAP_DUMP_THRESHOLD, HPROF_SEGMENTED_HEAP_DUMP_SEGMENT_SIZE);

        WriterOperation writerOperation = new WriterOperation(fileOutputStream, gcBefore);
        writerOperation.enqueue();
        IOException operationException = writerOperation.getException();
        if (operationException != null) {
            throw operationException;
        }

        /*
         * Close the file stream. Needs to be done outside of the VMOperation because it uses
         * synchronization.
         */
        fileOutputStream.close();
    }

    @SuppressWarnings("hiding")
    private void initialize(boolean useSegmentedHeapDump, long segmentSize) {
        this.currentSegmentStart = 0L;
        this.useSegmentedHeapDump = useSegmentedHeapDump;
        this.segmentSize = segmentSize;
    }

    /* This method runs as part of a VMOperation. */
    private void writeTo(AllocationFreeDataOutputStream outputStream, boolean gcBefore) throws IOException {
        /* If requested, clean up the heap. */
        if (gcBefore) {
            System.gc();
        }

        out = outputStream;
        heapDumpUtils = HeapDumpUtils.getHeapDumpUtils();

        /* hprof bin format header. */
        writeFileHeader();

        /* Dummy stack trace. */
        writeDummyTrace();

        List<Class<?>> classList = Heap.getHeap().getLoadedClasses();

        /* hprof UTF-8 symbols section. */
        writeClassNames(classList);

        byte[] fieldsMapData = heapDumpUtils.getFieldsMap();
        if (fieldsMapData.length == 0) {
            throw new IOException("Empty fieldsMap");
        }
        fieldsMap = createFieldsMap(fieldsMapData);

        /* HPROF_LOAD_CLASS records for all classes. */
        writeClasses(classList);

        /* write HPROF_FRAME and HPROF_TRACE records */
        dumpStackTraces();

        /* Write CLASS_DUMP records. */
        writeClassDumpRecords(classList);

        /* Write HEAP_DUMP record. */
        writeInstanceDumpRecords(classList);

        /* get current position to calculate length. */
        long dumpEnd = out.position();

        /* Calculate length of heap data. */
        long dumpLenLong = (dumpEnd - currentSegmentStart - 4L);
        /* Fill in final length. */
        fillInHeapRecordLength(dumpLenLong);

        if (useSegmentedHeapDump) {
            /* Write heap segment-end record. */
            out.writeByte((byte) HPROF_HEAP_DUMP_END);
            out.writeInt(0);
            out.writeInt(0);
        }

        /* Flush buffer stream and throw fields away. */
        out.flush();
        out = null;
        heapDumpUtils = null;
        fieldsMap = null;
        classDataCache = null;
    }

    private void writeHeapRecordPrologue() throws IOException {
        if (currentSegmentStart == 0) {
            out.flush();
            /* Write heap data header, depending on heap size use segmented heap format. */
            out.writeByte((byte) (useSegmentedHeapDump ? HPROF_HEAP_DUMP_SEGMENT
                            : HPROF_HEAP_DUMP));
            out.writeInt(0);

            /*
             * Remember position of dump length, we will fixup length later: hprof format requires
             * length.
             */
            currentSegmentStart = out.position();

            /* Write dummy length of 0 and we'll fix it later. */
            out.writeInt(0);
        }
    }

    private void writeHeapRecordEpilogue() throws IOException {
        writeHeapRecordEpilogue(0);
    }

    private void writeHeapRecordEpilogue(long dumpLenSize) throws IOException {
        if (useSegmentedHeapDump) {
            /* get current position (plus dumpLenLong) to calculate length. */
            long dumpEnd = out.position() + dumpLenSize;

            /* Calculate length of heap data. */
            long dumpLenLong = dumpEnd - currentSegmentStart - 4L;
            if (dumpLenLong >= segmentSize) {
                fillInHeapRecordLength(dumpLenLong);
                out.flush();
                currentSegmentStart = 0;
            }
        }
    }

    private void fillInHeapRecordLength(long dumpLenLong) throws IOException {
        /* Check length boundary, overflow of 4GB could happen but is _very_ unlikely. */
        if (dumpLenLong >= (4L * 1024 * 1024 * 1024)) {
            throw heapSegmentSizeOverflowException;
        }

        /* Save the current position. */
        long currentPosition = out.position();

        /* Seek the position to write length. */
        out.position(currentSegmentStart);

        int dumpLen = (int) dumpLenLong;

        /* Write length as integer. */
        out.writeInt(dumpLen);

        /* Reset to previous current position. */
        out.position(currentPosition);
    }

    private void writeClassDumpRecords(List<Class<?>> classList) throws IOException {
        for (Class<?> cls : classList) {
            writeHeapRecordPrologue();
            writeClassDumpRecord(cls);
            writeHeapRecordEpilogue();
        }
    }

    private void writeInstanceDumpRecords(List<Class<?>> classList) throws IOException {
        final StacksSlotsVisitorImpl stackVisitor = new StacksSlotsVisitorImpl();
        final CollectedHeapVisitorImpl collectedVisitor = new CollectedHeapVisitorImpl();
        final ImageHeapVisitorImpl imageVisitor = new ImageHeapVisitorImpl();
        IOException visitorException;

        heapDumpUtils.walkStacks(stackVisitor);
        visitorException = stackVisitor.getException();
        if (visitorException != null) {
            throw visitorException;
        }

        heapDumpUtils.walkHeapObjects(imageVisitor, collectedVisitor);
        visitorException = collectedVisitor.getException();
        if (visitorException != null) {
            throw visitorException;
        }
        visitorException = imageVisitor.getException();
        if (visitorException != null) {
            throw visitorException;
        }
        /* Write root sticky class. */
        writeStickyClasses(classList);

        /* write JavaThreads */
        writeJavaThreads();
    }

    private void writeStickyClasses(List<Class<?>> classList) throws IOException {
        for (Class<?> cls : classList) {
            writeHeapRecordPrologue();
            out.writeByte((byte) HPROF_GC_ROOT_STICKY_CLASS);
            writeObjectID(cls);
            writeHeapRecordEpilogue();
        }
    }

    private void writeImageGCRoot(Object obj) throws IOException {
        if (!(obj instanceof Class)) {
            writeHeapRecordPrologue();
            out.writeByte((byte) HPROF_GC_ROOT_JNI_GLOBAL);
            writeObjectID(obj);
            writeObjectID(null);
            writeHeapRecordEpilogue();
        }
    }

    private void writeUnknownGCRoot(Object obj) throws IOException {
        if (obj != null) {
            writeHeapRecordPrologue();
            out.writeByte((byte) HPROF_GC_ROOT_UNKNOWN);
            writeObjectID(obj);
            writeHeapRecordEpilogue();
        }
    }

    private void writeJavaThreads() throws IOException {
        int threadSerialNum = 1; // Note that the thread serial number range is 1-to-N

        for (IsolateThread vmThread = VMThreads.firstThread(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
            if (vmThread == CurrentIsolate.getCurrentThread()) {
                /* Skip itself */
                continue;
            }

            Thread jt = PlatformThreads.fromVMThread(vmThread);
            writeJavaThread(jt, threadSerialNum++);
        }
    }

    private void writeJavaThread(Thread jt, int threadSerialNum) throws IOException {
        writeHeapRecordPrologue();
        out.writeByte((byte) HPROF_GC_ROOT_THREAD_OBJ);
        writeObjectID(jt);
        out.writeInt(threadSerialNum);                      // thread serial number
        out.writeInt(threadSerialNum + DUMMY_STACK_TRACE_ID); // stack trace serial number
        writeHeapRecordEpilogue();
    }

    private void writeClass(Class<?> clazz) throws IOException {
        /*
         * All ordinary Class objects are covered by writeClassDumpRecords and they are in
         * classDataCache.
         */
        if (classDataCache.get(clazz) == null) {
            /* Unknown class. */
            writeInstance(clazz);
        }
    }

    private void writeClassDumpRecord(Class<?> cls) throws IOException {
        out.writeByte((byte) HPROF_GC_CLASS_DUMP);
        writeObjectID(cls);
        out.writeInt(DUMMY_STACK_TRACE_ID);
        writeObjectID(cls.getSuperclass());

        if (!isArray(cls)) {
            writeObjectID(cls.getClassLoader());
            writeObjectID(null); /* Signers. */
            writeObjectID(null); /* Protection domain. */
            writeObjectID(null); /* Reserved field 1. */
            writeObjectID(null); /* Reserved field 2. */
            out.writeInt(heapDumpUtils.instanceSizeOf(cls));

            /* Ignore constant pool output number of cp entries as zero. */
            out.writeShort((short) 0);

            List<Field> declaredFields = getImmediateFields(cls);
            int staticFields = 0;
            int instanceFields = 0;
            for (int i = 0; i < declaredFields.size(); i++) {
                Field field = declaredFields.get(i);
                if (field.isStatic()) {
                    staticFields++;
                } else {
                    instanceFields++;
                }
            }

            /* Dump static field descriptors. */
            writeFieldDescriptors(true, staticFields, declaredFields);

            /* Dump instance field descriptors. */
            writeFieldDescriptors(false, instanceFields, declaredFields);
        } else {
            /* Array. */
            Class<?> baseClass = getBaseClass(cls);
            writeObjectID(baseClass.getClassLoader());
            writeObjectID(null);
            writeObjectID(null);
            /* Two reserved id fields. */
            writeObjectID(null);
            writeObjectID(null);
            /* Write zero instance size: instance size is variable for arrays. */
            out.writeInt(0);
            /* No constant pool for array klasses. */
            out.writeShort((short) 0);
            /* No static fields for array klasses. */
            out.writeShort((short) 0);
            /* No instance fields for array klasses. */
            out.writeShort((short) 0);
        }
    }

    private void dumpStackTraces() throws IOException {
        // write a HPROF_TRACE record without any frames to be referenced as object alloc sites
        writeHeader(HPROF_TRACE, 3 * 4);
        out.writeInt(DUMMY_STACK_TRACE_ID);
        out.writeInt(0);                    // thread number
        out.writeInt(0);                    // frame count

        int frameSerialNum = 0;
        int numThreads = 0;
        Set<String> names = new HashSet<>();
        for (IsolateThread vmThread = VMThreads.firstThread(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
            if (vmThread == CurrentIsolate.getCurrentThread()) {
                /* Skip itself */
                continue;
            }

            final List<FrameInfoQueryResult> stack = new ArrayList<>();

            // dump thread stack trace
            JavaStackFrameVisitor visitor = new JavaStackFrameVisitor() {
                @Override
                public boolean visitFrame(FrameInfoQueryResult frameInfo) {
                    if (frameInfo.getSourceClass() != null) {
                        stack.add(frameInfo);
                    }
                    return true;
                }
            };
            JavaStackWalker.walkThread(vmThread, visitor);
            numThreads++;

            // write HPROF_FRAME records for this thread's stack trace
            int depth = stack.size();
            int threadFrameStart = frameSerialNum;
            for (int j = 0; j < depth; j++) {
                FrameInfoQueryResult frame = stack.get(j);
                ClassData cd = classDataCache.get(frame.getSourceClass());
                int classSerialNum = cd.serialNum;

                // the class serial number starts from 1
                assert classSerialNum > 0 : "class not found";
                dumpStackFrame(++frameSerialNum, classSerialNum, frame, names);
            }

            // write HPROF_TRACE record for one thread
            writeHeader(HPROF_TRACE, 3 * 4 + depth * getObjIDSize());
            int stackSerialNum = numThreads + DUMMY_STACK_TRACE_ID;
            out.writeInt(stackSerialNum);      // stack trace serial number
            out.writeInt(numThreads);          // thread serial number
            out.writeInt(depth);               // frame count
            for (int j = 1; j <= depth; j++) {
                writeObjectAddress(threadFrameStart + j);
            }
        }
        names = null;
    }

    private void dumpStackFrame(int frameSN, int classSN, FrameInfoQueryResult frame, Set<String> names) throws IOException {
        int lineNumber;
        if (frame.isNativeMethod()) {
            lineNumber = -3; // native frame
        } else {
            lineNumber = frame.getSourceLineNumber();
        }
        // First dump UTF8 if needed
        String method = frame.getSourceMethodName();
        String source = frame.getSourceFileName();
        if (method == null || method.isEmpty()) {
            method = "";
        }
        if (source == null || source.isEmpty()) {
            source = "Unknown Source";
        }
        writeName(method, names);                                // method's name
        writeName("", names);                                    // method's signature
        writeName(source, names);                                // source file name
        // Then write FRAME descriptor
        writeHeader(HPROF_FRAME, 4 * getObjIDSize() + 2 * 4);
        writeObjectAddress(frameSN);                             // frame serial number
        writeSymbolID(method);                                   // method's name
        writeSymbolID("");                                       // method's signature
        writeSymbolID(source);                                   // source file name
        out.writeInt(classSN);                                   // class serial number
        out.writeInt(lineNumber);                                // line number
    }

    private void writeName(String name, Set<String> names) throws IOException {
        if (names.add(name)) {
            writeSymbol(name);
        }
    }

    private void writeHeapInstance(Object obj) throws IOException {
        writeHeapRecordPrologue();
        if (obj instanceof Class<?>) {
            writeClass((Class<?>) obj);
            writeHeapRecordEpilogue();
        } else if (heapDumpUtils.isJavaPrimitiveArray(obj)) {
            writePrimitiveArray(obj);
        } else if (isArray(obj.getClass())) {
            writeObjectArray((Object[]) obj);
        } else {
            writeInstance(obj);
            writeHeapRecordEpilogue();
        }
    }

    private List<Field> getImmediateFields(Class<?> cls) {
        String clsName = cls.getName();
        List<Field> fields = fieldsMap.get(clsName);
        if (fields == null) {
            return Collections.emptyList();
        }
        return fields;
    }

    private void writeObjectArray(Object[] array) throws IOException {
        out.writeByte((byte) HPROF_GC_OBJ_ARRAY_DUMP);
        writeObjectID(array);
        out.writeInt(DUMMY_STACK_TRACE_ID);
        out.writeInt(array.length);
        writeObjectID(array.getClass());
        writeHeapRecordEpilogue(array.length * getObjIDSize());
        for (Object o : array) {
            writeObjectID(o);
        }
    }

    private void writePrimitiveArray(Object pArray) throws IOException {
        out.writeByte((byte) HPROF_GC_PRIM_ARRAY_DUMP);
        writeObjectID(pArray);
        out.writeInt(DUMMY_STACK_TRACE_ID);
        /* These are ordered by expected frequency. */
        if (pArray instanceof char[]) {
            writeCharArray((char[]) pArray);
        } else if (pArray instanceof byte[]) {
            writeByteArray((byte[]) pArray);
        } else if (pArray instanceof int[]) {
            writeIntArray((int[]) pArray);
        } else if (pArray instanceof long[]) {
            writeLongArray((long[]) pArray);
        } else if (pArray instanceof boolean[]) {
            writeBooleanArray((boolean[]) pArray);
        } else if (pArray instanceof short[]) {
            writeShortArray((short[]) pArray);
        } else if (pArray instanceof double[]) {
            writeDoubleArray((double[]) pArray);
        } else if (pArray instanceof float[]) {
            writeFloatArray((float[]) pArray);
        } else {
            throw VMError.shouldNotReachHere(pArray.getClass().getName());
        }
    }

    private void writeBooleanArray(boolean[] array) throws IOException {
        out.writeInt(array.length);
        out.writeByte((byte) HPROF_BOOLEAN);
        writeHeapRecordEpilogue(array.length * 1);
        for (boolean b : array) {
            out.writeBoolean(b);
        }
    }

    private void writeByteArray(byte[] array) throws IOException {
        out.writeInt(array.length);
        out.writeByte((byte) HPROF_BYTE);
        writeHeapRecordEpilogue(array.length * 1);
        for (byte b : array) {
            out.writeByte(b);
        }
    }

    private void writeShortArray(short[] array) throws IOException {
        out.writeInt(array.length);
        out.writeByte((byte) HPROF_SHORT);
        writeHeapRecordEpilogue(array.length * 2);
        for (short s : array) {
            out.writeShort(s);
        }
    }

    private void writeIntArray(int[] array) throws IOException {
        out.writeInt(array.length);
        out.writeByte((byte) HPROF_INT);
        writeHeapRecordEpilogue(array.length * 4);
        for (int i : array) {
            out.writeInt(i);
        }
    }

    private void writeLongArray(long[] array) throws IOException {
        out.writeInt(array.length);
        out.writeByte((byte) HPROF_LONG);
        writeHeapRecordEpilogue(array.length * 8);
        for (long l : array) {
            out.writeLong(l);
        }
    }

    private void writeCharArray(char[] array) throws IOException {
        out.writeInt(array.length);
        out.writeByte((byte) HPROF_CHAR);
        writeHeapRecordEpilogue(array.length * 2);
        for (char c : array) {
            out.writeChar(c);
        }
    }

    private void writeFloatArray(float[] array) throws IOException {
        out.writeInt(array.length);
        out.writeByte((byte) HPROF_FLOAT);
        writeHeapRecordEpilogue(array.length * 4);
        for (float f : array) {
            out.writeFloat(f);
        }
    }

    private void writeDoubleArray(double[] array) throws IOException {
        out.writeInt(array.length);
        out.writeByte((byte) HPROF_DOUBLE);
        writeHeapRecordEpilogue(array.length * 8);
        for (double d : array) {
            out.writeDouble(d);
        }
    }

    private void writeInstance(Object instance) throws IOException {
        out.writeByte((byte) HPROF_GC_INSTANCE_DUMP);
        writeObjectID(instance);
        out.writeInt(DUMMY_STACK_TRACE_ID);
        Class<?> cls = instance.getClass();
        writeObjectID(cls);

        final ClassData cd = classDataCache.get(cls);
        out.writeInt(cd.instSize);
        final Pointer objRef = heapDumpUtils.objectToPointer(instance);
        for (int i = 0; i < cd.fields.length; i++) {
            writeField(cd.fields[i], objRef);
        }
    }

    private void writeFieldDescriptors(boolean isStatic, int size, List<Field> fields) throws IOException {
        /* cls == null for instance fields. */
        out.writeShort((short) size);
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            if (isStatic == field.isStatic()) {
                writeSymbolIDFromField(field);
                char typeCode = field.getStorageSignature();
                int kind = signatureToHprofKind(typeCode);
                out.writeByte((byte) kind);
                if (field.isStatic()) {
                    /* Static field. */
                    Object staticData;
                    char javaSignature = field.getStorageSignature();
                    if (javaSignature == JVM_SIGNATURE_CLASS || javaSignature == JVM_SIGNATURE_ARRAY) {
                        staticData = StaticFieldsSupport.getStaticObjectFields();
                    } else {
                        staticData = StaticFieldsSupport.getStaticPrimitiveFields();
                    }
                    writeField(field, heapDumpUtils.objectToPointer(staticData));
                }
            }
        }
    }

    private static int signatureToHprofKind(char ch) {
        switch (ch) {
            case JVM_SIGNATURE_CLASS:
            case JVM_SIGNATURE_ARRAY:
                return HPROF_NORMAL_OBJECT;
            case JVM_SIGNATURE_BOOLEAN:
                return HPROF_BOOLEAN;
            case JVM_SIGNATURE_CHAR:
                return HPROF_CHAR;
            case JVM_SIGNATURE_FLOAT:
                return HPROF_FLOAT;
            case JVM_SIGNATURE_DOUBLE:
                return HPROF_DOUBLE;
            case JVM_SIGNATURE_BYTE:
                return HPROF_BYTE;
            case JVM_SIGNATURE_SHORT:
                return HPROF_SHORT;
            case JVM_SIGNATURE_INT:
                return HPROF_INT;
            case JVM_SIGNATURE_LONG:
                return HPROF_LONG;
            default:
                throw new RuntimeException("should not reach here");
        }
    }

    private void writeField(Field field, Pointer p) throws IOException {
        char storageSignature = field.getStorageSignature();
        int location = field.getLocation();

        switch (storageSignature) {
            case JVM_SIGNATURE_BOOLEAN:
                out.writeByte(p.readByte(location));
                break;
            case JVM_SIGNATURE_CHAR:
                out.writeChar(p.readChar(location));
                break;
            case JVM_SIGNATURE_BYTE:
                out.writeByte(p.readByte(location));
                break;
            case JVM_SIGNATURE_SHORT:
                out.writeShort(p.readShort(location));
                break;
            case JVM_SIGNATURE_INT:
                out.writeInt(p.readInt(location));
                break;
            case JVM_SIGNATURE_LONG:
                out.writeLong(p.readLong(location));
                break;
            case JVM_SIGNATURE_FLOAT:
                out.writeFloat(p.readFloat(location));
                break;
            case JVM_SIGNATURE_DOUBLE:
                out.writeDouble(p.readDouble(location));
                break;
            case JVM_SIGNATURE_CLASS:
            case JVM_SIGNATURE_ARRAY:
                writeObjectID(ReferenceAccess.singleton().readObjectAt(p.add(location), true));
                break;
            default:
                throw VMError.shouldNotReachHere("HeapDumpWriter.writeField: storageSignature");
        }
    }

    private void writeHeader(int tag, int len) throws IOException {
        out.writeByte((byte) tag);
        out.writeInt(0); /* current ticks. */
        out.writeInt(len);
    }

    private void writeDummyTrace() throws IOException {
        writeHeader(HPROF_TRACE, 3 * 4);
        out.writeInt(DUMMY_STACK_TRACE_ID);
        out.writeInt(0);
        out.writeInt(0);
    }

    private void writeSymbolFromField(byte[] data, Field field) throws IOException {
        writeHeader(HPROF_UTF8, field.getNameLength() + getObjIDSize());
        writeSymbolIDFromField(field);
        out.write(data, field.getNameStartOffset(), field.getNameLength());
    }

    private void writeSymbol(String clsName) throws IOException {
        byte[] buf = clsName.getBytes(StandardCharsets.UTF_8);
        writeHeader(HPROF_UTF8, buf.length + getObjIDSize());
        writeSymbolID(clsName);
        out.write(buf);
    }

    private void writeClassNames(List<Class<?>> classList) throws IOException {
        /* hprof UTF-8 symbols section. */
        for (Class<?> cls : classList) {
            writeSymbol(cls.getName());
        }
    }

    private void writeClasses(List<Class<?>> classList) throws IOException {
        int serialNum = 1;
        /*
         * Build a temporary map from Class to ClassData while I can allocate, but turn it into a
         * map between arrays for later use.
         */
        Map<Class<?>, ClassData> classDataMap = new HashMap<>();
        List<Field> fields = new ArrayList<>();
        for (Class<?> cls : classList) {
            writeHeader(HPROF_LOAD_CLASS, 2 * (getObjIDSize() + 4));
            out.writeInt(serialNum);
            writeObjectID(cls);
            out.writeInt(DUMMY_STACK_TRACE_ID);
            writeSymbolID(cls.getName());
            assert fields.isEmpty();
            addInstanceFieldsTo(fields, cls);
            int instSize = getSizeForFields(fields);
            classDataMap.put(cls, new ClassData(serialNum, instSize, fields.toArray(ZERO_FIELD_ARR)));
            fields.clear();
            serialNum++;
        }
        classDataCache = new ClassToClassDataMap(classDataMap);
    }

    /** Writes hprof binary file header. */
    private void writeFileHeader() throws IOException {
        /* Version string. */
        if (useSegmentedHeapDump) {
            out.writeBytes(HPROF_HEADER_1_0_2);
        } else {
            out.writeBytes(HPROF_HEADER_1_0_1);
        }
        out.writeByte((byte) '\0');

        /* Write identifier size. we use pointers as identifiers. */
        out.writeInt(getObjIDSize());

        /* Time stamp: file creation time. */
        out.writeLong(System.currentTimeMillis());
    }

    /** Writes unique ID for an object. */
    private void writeObjectID(Object obj) throws IOException {
        if (obj != null) {
            WordBase ptr = ReferenceAccess.singleton().getCompressedRepresentation(obj);
            writeObjectAddress(ptr.rawValue());
        } else {
            writeObjectAddress(0L);
        }
    }

    private void writeSymbolID(String clsName) throws IOException {
        writeObjectID(clsName);
    }

    private void writeSymbolIDFromField(Field field) throws IOException {
        writeObjectID(field);
    }

    private void writeObjectAddress(long address) throws IOException {
        if (getObjIDSize() == 4) {
            out.writeInt((int) address);
        } else {
            out.writeLong(address);
        }
    }

    /** Get all declared as well as inherited (directly/indirectly) fields. */
    private void addInstanceFieldsTo(List<Field> res, final Class<?> cls) {
        Class<?> clazz = cls;
        while (clazz != null) {
            List<Field> curFields = getImmediateFields(clazz);
            for (int i = 0; i < curFields.size(); i++) {
                Field f = curFields.get(i);

                if (!f.isStatic()) {
                    res.add(f);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * Get size in bytes (in stream) required for given fields. Note that this is not the same as
     * object size in heap. The size in heap will include size of padding/alignment bytes as well.
     */
    private static int getSizeForFields(List<Field> fields) {
        int size = 0;
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            char typeCode = field.getStorageSignature();
            switch (typeCode) {
                case JVM_SIGNATURE_BOOLEAN:
                case JVM_SIGNATURE_BYTE:
                    size++;
                    break;
                case JVM_SIGNATURE_CHAR:
                case JVM_SIGNATURE_SHORT:
                    size += 2;
                    break;
                case JVM_SIGNATURE_INT:
                case JVM_SIGNATURE_FLOAT:
                    size += 4;
                    break;
                case JVM_SIGNATURE_CLASS:
                case JVM_SIGNATURE_ARRAY:
                    size += getObjIDSize();
                    break;
                case JVM_SIGNATURE_LONG:
                case JVM_SIGNATURE_DOUBLE:
                    size += 8;
                    break;
                default:
                    throw new RuntimeException("should not reach here");
            }
        }
        return size;
    }

    private static int getObjIDSize() {
        return ConfigurationValues.getObjectLayout().getReferenceSize();
    }

    private static boolean isArray(Class<?> cls) {
        return cls.getName().startsWith("[");
    }

    private static Class<?> getBaseClass(final Class<?> array) {
        Class<?> arr = array;
        while (isArray(arr)) {
            arr = arr.getComponentType();
        }
        return arr;
    }

    private Map<String, List<Field>> createFieldsMap(byte[] data) throws IOException {
        int offset = 0;
        Map<String, List<Field>> fldMap = new HashMap<>();
        while (offset < data.length) {
            List<Field> fields;
            String className = readString(data, offset);
            offset += className.length() + 1;

            if (data[offset] == 0 && data[offset + 1] == 0) {
                /* No fields. */
                fields = Collections.emptyList();
                offset += 2;
            } else {
                fields = new ArrayList<>();
                offset = readFields(false, data, offset, fields);
                offset++;
                offset = readFields(true, data, offset, fields);
                offset++;
            }
            fldMap.put(className, fields);
        }
        return fldMap;
    }

    private int readFields(boolean isStatic, byte[] data, int dataOffset, List<Field> fields) throws IOException {
        int offset = dataOffset;
        while (data[offset] != 0) {
            /* Read field. */
            int stringStart = offset;
            int stringLength = readStringLength(data, offset);
            offset += stringLength + 1;
            char javaSig = (char) data[offset++];
            char storageSig = (char) data[offset++];
            int location = readInt(data, offset);
            offset += 4;
            Field fieldDef = new Field(stringStart, stringLength, javaSig, storageSig, isStatic, location);
            writeSymbolFromField(data, fieldDef);
            fields.add(fieldDef);
        }
        return offset;
    }

    private static int readInt(final byte[] data, final int st) {
        int start = st;
        int ch1 = data[start++] & 0xFF;
        int ch2 = data[start++] & 0xFF;
        int ch3 = data[start++] & 0xFF;
        int ch4 = data[start++] & 0xFF;
        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
    }

    private static String readString(byte[] data, int start) {
        int len = readStringLength(data, start);

        return new String(data, start, len, StandardCharsets.UTF_8);
    }

    /**
     * Returns size of the null-terminated string.
     *
     * @param data byte[] array that is the source of string.
     * @param start the initial offset to <code>data</code> array.
     * @return the number of characters (bytes) in a null-terminated character sequence, without
     *         including the null-terminating character.
     */
    private static int readStringLength(byte[] data, int start) {
        int offset = start;

        while (data[offset] != 0) {
            offset++;
        }
        return offset - start;
    }

    private static class ClassData {

        int serialNum;
        int instSize;
        Field[] fields;

        ClassData(int serialNum, int instSize, Field[] fields) {
            this.serialNum = serialNum;
            this.instSize = instSize;
            this.fields = fields;
        }
    }

    private static final class Field {

        private final int nameStart;
        private final int nameLength;
        private final char javaSig;
        private final char storageSig;
        private final boolean isStatic;
        private final int location;

        private Field(final int ss, final int sl, final char jsig, final char ssig,
                        final boolean s, int loc) {
            nameStart = ss;
            nameLength = sl;
            javaSig = jsig == 'A' ? JVM_SIGNATURE_CLASS : jsig;
            storageSig = ssig == 'A' ? JVM_SIGNATURE_CLASS : ssig;
            isStatic = s;
            location = loc;
        }

        private boolean isStatic() {
            return isStatic;
        }

        @SuppressWarnings({"unused"})
        private char getJavaSignature() {
            return javaSig;
        }

        private char getStorageSignature() {
            return storageSig;
        }

        private int getNameStartOffset() {
            return nameStart;
        }

        private int getNameLength() {
            return nameLength;
        }

        private int getLocation() {
            return location;
        }
    }

    private class CollectedHeapVisitorImpl implements ObjectVisitor {

        private IOException exception;

        @Override
        public boolean visitObject(Object obj) {
            Object asObject = obj;
            try {
                writeHeapInstance(asObject);
            } catch (IOException ex) {
                /* Remember exception and abort VM operation. */
                exception = ex;
                return false;
            }
            return true;
        }

        private IOException getException() {
            return exception;
        }
    }

    private class ImageHeapVisitorImpl implements ObjectVisitor {

        private IOException exception;

        @Override
        public boolean visitObject(Object obj) {
            Object asObject = obj;
            try {
                writeHeapInstance(asObject);
                writeImageGCRoot(asObject);
            } catch (IOException ex) {
                /* Remember exception and abort VM operation. */
                exception = ex;
                return false;
            }
            return true;
        }

        private IOException getException() {
            return exception;
        }
    }

    private class StacksSlotsVisitorImpl extends HeapDumpUtils.StacksSlotsVisitor {

        private IOException exception;

        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed, Object holderObject) {
            try {
                /* Get the referent of the reference. */
                final Object obj = ReferenceAccess.singleton().readObjectAt(objRef, compressed);
                writeUnknownGCRoot(obj);
                return true;
            } catch (IOException ex) {
                /* Remember exception. */
                exception = ex;
                return false;
            }
        }

        private IOException getException() {
            return exception;
        }
    }

    /**
     * Abstract Allocation-free output stream created from FileOutputStream. This is the base class
     * used by HeapDumpWriteImpl class.
     */
    public abstract static class AllocationFreeFileOutputStream extends OutputStream {

        // constructor
        public abstract AllocationFreeFileOutputStream newStreamFor(FileOutputStream fileOutputStream) throws IOException;

        @Override
        public abstract void write(int b) throws IOException;

        @Override
        public abstract void write(byte[] b, int offset, int length) throws IOException;

        @Override
        public abstract void close() throws IOException;

        @Override
        public void flush() throws IOException {
        }

        /** Read the current position in a file descriptor. */
        protected abstract long position() throws IOException;

        /** Set the current position in a file descriptor. */
        protected abstract long position(long offset) throws IOException;
    }

    /**
     * Implementation of allocation-free output stream, which delegates to
     * AllocationFreeOutputStream interface.
     */
    private final class AllocationFreeFileOutputStreamWrapper extends AllocationFreeFileOutputStream {

        private final AllocationFreeOutputStream out;
        private long position;

        private AllocationFreeFileOutputStreamWrapper(AllocationFreeOutputStream outputStream) {
            out = outputStream;
            position = 0;
        }

        @Override
        public AllocationFreeFileOutputStream newStreamFor(FileOutputStream fileOutputStream) throws IOException {
            throw VMError.shouldNotReachHere();
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            position++;
        }

        @Override
        public void write(byte[] b, int offset, int length) throws IOException {
            out.write(b, offset, length);
            position += length;
        }

        @Override
        public void close() throws IOException {
            out.close();
        }

        @Override
        public void flush() throws IOException {
            out.flush();
        }

        @Override
        protected long position() throws IOException {
            return position;
        }

        @Override
        protected long position(long offset) throws IOException {
            throw VMError.shouldNotReachHere();
        }
    }

    private static final class AllocationFreeDataOutputStream {

        private final AllocationFreeBufferedOutputStream out;

        private AllocationFreeDataOutputStream(AllocationFreeBufferedOutputStream o) {
            out = o;
        }

        private void writeBoolean(boolean v) throws IOException {
            out.write(v ? 1 : 0);
        }

        private void writeByte(int v) throws IOException {
            out.write(v);
        }

        private void writeChar(int v) throws IOException {
            out.write((v >>> 8) & 0xFF);
            out.write((v >>> 0) & 0xFF);
        }

        private void writeShort(int v) throws IOException {
            out.write((v >>> 8) & 0xFF);
            out.write((v >>> 0) & 0xFF);
        }

        private void writeInt(int v) throws IOException {
            out.write((v >>> 24) & 0xFF);
            out.write((v >>> 16) & 0xFF);
            out.write((v >>> 8) & 0xFF);
            out.write((v >>> 0) & 0xFF);
        }

        private void writeFloat(float v) throws IOException {
            writeInt(Float.floatToIntBits(v));
        }

        private void writeLong(long v) throws IOException {
            out.write((byte) (v >>> 56));
            out.write((byte) (v >>> 48));
            out.write((byte) (v >>> 40));
            out.write((byte) (v >>> 32));
            out.write((byte) (v >>> 24));
            out.write((byte) (v >>> 16));
            out.write((byte) (v >>> 8));
            out.write((byte) (v >>> 0));
        }

        private void writeDouble(double v) throws IOException {
            writeLong(Double.doubleToLongBits(v));
        }

        private void flush() throws IOException {
            out.flush();
        }

        private void write(byte[] buf) throws IOException {
            out.write(buf, 0, buf.length);
        }

        private void write(byte[] buf, int off, int len) throws IOException {
            out.write(buf, off, len);
        }

        private void writeBytes(String s) throws IOException {
            int len = s.length();
            for (int i = 0; i < len; i++) {
                out.write((byte) s.charAt(i));
            }
        }

        private long position() throws IOException {
            return out.position();
        }

        private void position(long pos) throws IOException {
            out.position(pos);
        }
    }

    private static final class AllocationFreeBufferedOutputStream {

        private byte[] buf;
        // current index in buf array
        private int position;
        // size of valid data in buf array
        private int size;
        private AllocationFreeFileOutputStream out;

        private AllocationFreeBufferedOutputStream(AllocationFreeFileOutputStream out) {
            this(out, 8192);
        }

        private AllocationFreeBufferedOutputStream(AllocationFreeFileOutputStream out, int size) {
            this.out = out;
            if (size <= 0) {
                throw new IllegalArgumentException("Buffer size <= 0");
            }
            buf = new byte[size];
        }

        private void flushBuffer() throws IOException {
            if (size > 0) {
                out.write(buf, 0, size);
                position = 0;
                size = 0;
            }
        }

        private void write(int b) throws IOException {
            if (position >= buf.length) {
                flushBuffer();
            }
            buf[position++] = (byte) b;
            setSize();
        }

        public void write(byte[] b, int off, int len) throws IOException {
            if (len >= buf.length) {
                flushBuffer();
                out.write(b, off, len);
                return;
            }
            if (len > buf.length - position) {
                flushBuffer();
            }
            System.arraycopy(b, off, buf, position, len);
            position += len;
            setSize();
        }

        void flush() throws IOException {
            flushBuffer();
            out.flush();
        }

        private long position() throws IOException {
            return out.position() + position;
        }

        private void position(long pos) throws IOException {
            long currentFlushPos = out.position();
            long newCount = pos - currentFlushPos;

            if (newCount >= 0 && newCount <= size) {
                position = (int) newCount;
            } else {
                flush();
                out.position(pos);
            }
        }

        private void setSize() {
            if (position > size) {
                size = position;
            }
        }
    }

    private class WriterOperation extends JavaVMOperation {

        private final AllocationFreeDataOutputStream dataOutput;
        private final boolean gcBefore;
        private IOException exception;

        WriterOperation(FileOutputStream fileOutputStream, boolean gcBefore) throws IOException {
            super(VMOperationInfos.get(WriterOperation.class, "Write heap dump", VMOperation.SystemEffect.SAFEPOINT));
            /* open file stream and create buffered data output stream. */
            AllocationFreeFileOutputStream fos = ImageSingletons.lookup(AllocationFreeFileOutputStream.class).newStreamFor(fileOutputStream);
            dataOutput = new AllocationFreeDataOutputStream(new AllocationFreeBufferedOutputStream(fos));
            this.gcBefore = gcBefore;
        }

        WriterOperation(AllocationFreeOutputStream outputStream, boolean gcBefore, int bufferSize) {
            super(VMOperationInfos.get(WriterOperation.class, "Write heap dump", VMOperation.SystemEffect.SAFEPOINT));
            /* open file stream and create buffered data output stream. */
            AllocationFreeFileOutputStream fos = new AllocationFreeFileOutputStreamWrapper(outputStream);
            dataOutput = new AllocationFreeDataOutputStream(new AllocationFreeBufferedOutputStream(fos, bufferSize + 32768));
            this.gcBefore = gcBefore;
        }

        @Override
        protected void operate() {
            try {
                writeTo(dataOutput, gcBefore);
            } catch (IOException ex) {
                exception = ex;
            }
        }

        private IOException getException() {
            return exception;
        }

    }

    /** A map from Class to ClassData. */
    private static class ClassToClassDataMap {

        private final ClassData[] classDataArray;

        ClassToClassDataMap(Map<Class<?>, ClassData> map) {
            /* Find the maximum typeID among the classes. */
            int maxTypeID = 0;
            for (Class<?> key : map.keySet()) {
                maxTypeID = Integer.max(maxTypeID, typeIDFromClass(key));
            }
            /* Make up an array large enough to be indexed by typeID. */
            classDataArray = new ClassData[maxTypeID + 1];
            /* Fill in the array. */
            for (Class<?> key : map.keySet()) {
                classDataArray[typeIDFromClass(key)] = map.get(key);
            }
        }

        /** Use typeID to find the Class. */
        ClassData get(Class<?> clazz) {
            int id = typeIDFromClass(clazz);
            if (id >= classDataArray.length) {
                return null; // class not loaded, there can be no instances
            }
            return classDataArray[id];
        }

        /** Look up the typeID of a Class from the DynamicHub. */
        private static int typeIDFromClass(Class<?> clazz) {
            return DynamicHub.fromClass(clazz).getTypeID();
        }
    }
}
