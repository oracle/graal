/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.insight.heap;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Support for generating {@code .hprof} files in <a target="_blank" href=
 * "http://hg.openjdk.java.net/jdk6/jdk6/jdk/raw-file/tip/src/share/demo/jvmti/hprof/manual.html">
 * Java Profiler Heap Dump Format</a>.
 * <p>
 * {@codesnippet org.graalvm.tools.insight.test.heap.HeapDumpTest#generateSampleHeapDump}
 *
 * @since 21.1
 */
public final class HeapDump {
    // constants for the Java Profiler Heap Dump Format
    private static final String MAGIC = "JAVA PROFILE 1.0.1";
    private static final int TAG_STRING = 0x01;
    private static final int TAG_LOAD_CLASS = 0x02;
    private static final int TAG_STACK_FRAME = 0x04;
    private static final int TAG_STACK_TRACE = 0x05;
    private static final int TAG_HEAP_DUMP = 0x0c;

    // heap dump codes
    private static final int HEAP_ROOT_JAVA_FRAME = 0x03;
    private static final int HEAP_ROOT_THREAD_OBJECT = 0x08;
    private static final int HEAP_CLASS_DUMP = 0x20;
    private static final int HEAP_INSTANCE_DUMP = 0x21;
    private static final int HEAP_OBJECT_ARRAY_DUMP = 0x22;
    private static final int HEAP_PRIMITIVE_ARRAY_DUMP = 0x23;

    // types used in heap dump
    private static final int TYPE_OBJECT = 0x02;
    private static final int TYPE_BOOLEAN = 0x04;
    private static final int TYPE_CHAR = 0x05;
    private static final int TYPE_FLOAT = 0x06;
    private static final int TYPE_DOUBLE = 0x07;
    private static final int TYPE_BYTE = 0x08;
    private static final int TYPE_SHORT = 0x09;
    private static final int TYPE_INT = 0x0a;
    private static final int TYPE_LONG = 0x0b;

    private enum Identifiers {
        FOUR,
        EIGHT;

        int sizeOf() {
            return this == FOUR ? 4 : 8;
        }

        void writeID(DataOutputStream os, long id) throws IOException {
            if (this == FOUR) {
                int intId = (int) id;
                assert intId == id;
                os.writeInt(intId);
            } else {
                os.writeLong(id);
            }
        }
    }

    private final DataOutputStream heap;
    private final Builder builder;
    private final Map<String, ObjectInstance> heapStrings = new HashMap<>();
    private final Map<Class<?>, ClassInstance> primitiveClasses = new HashMap<>();
    private final Map<Object, ObjectInstance> primitives = new HashMap<>();
    private final ClassInstance typeObject;
    private final ClassInstance typeString;
    private final ClassInstance typeThread;
    private ClassInstance typeObjectArray;

    HeapDump() {
        this.builder = null;
        this.heap = null;
        this.typeObject = null;
        this.typeString = null;
        this.typeThread = null;
    }

    HeapDump(OutputStream out, final Builder builder) {
        this.builder = builder;
        this.heap = new DataOutputStream(out);
        this.typeObject = new ClassBuilder("java.lang.Object", 0).dumpClass();
        newClass("char[]").dumpClass();
        this.typeString = newClass("java.lang.String").field("value", char[].class).field("hash", Integer.TYPE).dumpClass();
        this.typeThread = newClass("java.lang.Thread").field("daemon", Boolean.TYPE).field("name", String.class).field("priority", Integer.TYPE).dumpClass();
        this.typeObjectArray = newClass("[Ljava/lang/Object;").dumpClass();
    }

    /**
     * Starts generating new {@code .hprof} file. Follow with {@link Builder#dumpHeap} call.
     * <p>
     * {@codesnippet org.graalvm.tools.insight.test.heap.HeapDumpTest#generateSampleHeapDump}
     *
     * @param os output stream to write data to
     * @return new builder
     * @throws IOException on I/O error
     */
    public static Builder newHeapBuilder(OutputStream os) throws IOException {
        final HeapDump dummyWrapper = new HeapDump();
        return dummyWrapper.new Builder(Identifiers.FOUR, os);
    }

    /**
     * Builder to construct content of the heap dump file.
     * <p>
     * {@codesnippet org.graalvm.tools.insight.test.heap.HeapDumpTest#generateSampleHeapDump}
     *
     * @since 21.1
     */
    public final class Builder implements Closeable {
        final Identifiers ids;
        private final Map<String, Integer> wholeStrings = new HashMap<>();
        private final DataOutputStream whole;
        private int defaultStackTrace;
        private Long timeBase;
        final Counter objectCounter = new Counter("object");
        private final Counter stackFrameCounter = new Counter("stackFrame");
        private final Counter stackTraceCounter = new Counter("stackTrace");
        private final Counter classCounter = new Counter("class");
        private final Counter threadCounter = new Counter("thread");

        private Builder(Identifiers ids, OutputStream os) {
            this.whole = new DataOutputStream(os);
            this.ids = ids;
        }

        private void dumpPrologue(Identifiers ids1, final long millis) throws IOException {
            whole.write(MAGIC.getBytes());
            whole.write(0); // null terminated string
            whole.writeInt(ids1.sizeOf());
            whole.writeLong(millis);
            defaultStackTrace = writeStackTrace(0);
        }

        /**
         * Generates heap dump.
         * <p>
         * {@codesnippet org.graalvm.tools.insight.test.heap.HeapDumpTest#generateSampleHeapDump}
         *
         * @param generator callback that performs the heap generating operations
         * @throws IOException when an I/O error occurs
         *
         * @see HeapDump#newHeapBuilder(java.io.OutputStream)
         * @since 21.1
         */
        public void dumpHeap(Consumer<HeapDump> generator) throws IOException {
            dumpHeap(System.currentTimeMillis(), generator);
        }

        /**
         * Generates heap dump with an explicitly specified time stamp. Should there be multiple
         * {@link HeapDump dumps} in a single file, it is expected the subsequent values of
         * {@code timeStamp} are not going to be decreasing.
         * <p>
         * {@codesnippet org.graalvm.tools.insight.test.heap.HeapDumpTest#generateSampleHeapDump}
         *
         * @param timeStamp time when the heap dump is supposed to be taken in milliseconds
         * @param generator callback that performs the heap generating operations
         * @throws IOException when an I/O error occurs
         *
         * @see HeapDump#newHeapBuilder(java.io.OutputStream)
         * @since 21.1
         */
        public void dumpHeap(long timeStamp, Consumer<HeapDump> generator) throws IOException {
            if (timeBase == null) {
                dumpPrologue(ids, timeBase = timeStamp);
            }

            final ByteArrayOutputStream rawHeap = new ByteArrayOutputStream();
            HeapDump seg = new HeapDump(rawHeap, this);
            try {
                generator.accept(seg);
            } catch (UncheckedIOException ex) {
                throw ex.getCause();
            }
            seg.flush();
            if (rawHeap.size() > 0) {
                whole.writeByte(TAG_HEAP_DUMP);
                final long diffMillis = Math.max(0, timeStamp - timeBase);
                long diffMicroseconds = Math.min(diffMillis * 1000, Integer.MAX_VALUE);
                whole.writeInt((int) diffMicroseconds);
                final byte[] bytes = rawHeap.toByteArray();
                whole.writeInt(bytes.length);
                whole.write(bytes);
                whole.flush();
            }
        }

        @Override
        public void close() throws IOException {
            whole.close();
        }

        void writeDefaultStackTraceSerialNumber(DataOutputStream os) throws IOException {
            os.writeInt(defaultStackTrace);
        }

        int writeStackFrame(HeapDump thiz, ClassInstance clazz, String rootName, String sourceFile, int lineNumber) throws IOException {
            int id = stackFrameCounter.next();
            int rootNameId = writeString(rootName);
            int signatureId = writeString("");
            int sourceFileId = writeString(sourceFile);
            whole.writeByte(TAG_STACK_FRAME);
            whole.writeInt(0); // microseconds
            whole.writeInt(8 + ids.sizeOf() * 4); // size of following entries
            ids.writeID(whole, id);
            ids.writeID(whole, rootNameId);
            ids.writeID(whole, signatureId);
            ids.writeID(whole, sourceFileId);
            whole.writeInt(clazz.serialId(thiz));
            whole.writeInt(lineNumber);
            return id;
        }

        int writeStackTrace(int threadSerialId, int... frames) throws IOException {
            int id = stackTraceCounter.next();
            whole.writeByte(TAG_STACK_TRACE);
            whole.writeInt(0); // microseconds
            whole.writeInt(12 + ids.sizeOf() * frames.length); // size of following entries
            whole.writeInt(id); // stack trace serial number
            whole.writeInt(threadSerialId); // thread serial number
            whole.writeInt(frames.length);
            for (int fId : frames) {
                ids.writeID(whole, fId);
            }
            return id;
        }

        int writeLoadClass(String className, int classSerial) throws IOException {
            int classId = objectCounter.next();
            int classNameId = writeString(className);
            whole.writeByte(TAG_LOAD_CLASS);
            whole.writeInt(0); // microseconds
            whole.writeInt(8 + ids.sizeOf() * 2); // size of following entries
            whole.writeInt(classSerial); // class serial number
            ids.writeID(whole, classId); // class object ID
            writeDefaultStackTraceSerialNumber(whole);
            ids.writeID(whole, classNameId); // class name string ID
            return classId;
        }

        int writeString(String text) throws IOException {
            if (text == null) {
                return 0;
            }
            Integer prevId = wholeStrings.get(text);
            if (prevId != null) {
                return prevId;
            }
            int stringId = objectCounter.next();
            whole.writeByte(TAG_STRING);
            whole.writeInt(0); // microseconds
            byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
            whole.writeInt(ids.sizeOf() + utf8.length);
            ids.writeID(whole, stringId);
            whole.write(utf8);
            wholeStrings.put(text, stringId);
            return stringId;
        }
    }

    /**
     * Represents an object instance in the {@link HeapDump}.
     *
     * @see HeapDump#newInstance(org.graalvm.tools.insight.heap.HeapDump.ClassInstance)
     * @since 21.1
     */
    public final class ObjectInstance {
        private final int id;

        ObjectInstance(int id) {
            this.id = id;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 79 * hash + this.id;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ObjectInstance other = (ObjectInstance) obj;
            return this.id == other.id;
        }

        int id(HeapDump requestor) {
            if (requestor != HeapDump.this) {
                throw new IllegalStateException();
            }
            return id;
        }
    }

    /**
     * Represents a class in the {@link HeapDump}.
     *
     * @see HeapDump#newClass(java.lang.String)
     * @see HeapDump#newInstance(org.graalvm.tools.insight.heap.HeapDump.ClassInstance)
     * @since 21.1
     */
    public final class ClassInstance {
        private final int serialId;
        private final int id;
        private final int fieldBytes;
        private final TreeMap<String, Class<?>> fieldNamesAndTypes;

        private ClassInstance(int serialId, int id, TreeMap<String, Class<?>> fieldNamesAndTypes, int fieldBytes) {
            this.serialId = serialId;
            this.id = id;
            this.fieldBytes = fieldBytes;
            this.fieldNamesAndTypes = fieldNamesAndTypes;
        }

        /**
         * Immutable sorted set of field names this class has.
         *
         * @return immutable sorted set of names
         * @since 21.1
         */
        public NavigableSet<String> names() {
            return Collections.unmodifiableNavigableSet(fieldNamesAndTypes.navigableKeySet());
        }

        int id(HeapDump requestor) {
            if (requestor != HeapDump.this) {
                throw new IllegalStateException();
            }
            return id;
        }

        int serialId(HeapDump requestor) {
            if (requestor != HeapDump.this) {
                throw new IllegalStateException();
            }
            return serialId;
        }

    }

    /**
     * Starts building new class for the {@link HeapDump}.
     *
     * @param name the name of the class
     * @return builder to specify field names and types
     * @throws UncheckedIOException when an I/O error occurs
     * @since 21.1
     */
    public ClassBuilder newClass(String name) throws UncheckedIOException {
        return new ClassBuilder(name, typeObject.id(HeapDump.this));
    }

    /**
     * Starts building new thread/event with a stacktrace and local variables.
     *
     * @param name name of the thread
     * @return new thread builder
     * @see ThreadBuilder
     *
     * @since 21.1
     */
    public ThreadBuilder newThread(String name) {
        return new ThreadBuilder(name);
    }

    /**
     * Starts building an instance of given class.
     *
     * @param clazz class with defined fields and their types
     * @return new instance builder
     *
     * @since 21.1
     */
    public InstanceBuilder newInstance(ClassInstance clazz) {
        try {
            return new InstanceBuilder(clazz, builder.objectCounter.next());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Starts building an object array.
     *
     * @param len the size of the array
     * @return new array builder
     *
     * @since 21.3.2
     */
    public ArrayBuilder newArray(int len) {
        try {
            return new ArrayBuilder(this.typeObjectArray, len, builder.objectCounter.next());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    void flush() throws IOException {
        heap.flush();
    }

    /**
     * Builds new string instance in the {@link HeapDump}. Encodes the value as an instance of
     * {@code java.lang.String} with {@code value} field holding the {@code char[]} of the provided
     * {@code text}.
     *
     * @param text the text of the string
     * @return instance representing the string in the heap.
     * @throws UncheckedIOException when an I/O error occurs
     *
     * @since 21.1
     */
    public ObjectInstance dumpString(String text) throws UncheckedIOException {
        try {
            return dumpStringImpl(text);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private ObjectInstance dumpStringImpl(String text) throws IOException {
        if (text == null) {
            return new ObjectInstance(0);
        }
        ObjectInstance id = heapStrings.get(text);
        if (id != null) {
            return id;
        }
        int instanceId = builder.objectCounter.next();
        heap.writeByte(HEAP_PRIMITIVE_ARRAY_DUMP);
        builder.ids.writeID(heap, instanceId);
        builder.writeDefaultStackTraceSerialNumber(heap);
        heap.writeInt(text.length()); // number of elements
        heap.writeByte(TYPE_CHAR);
        for (char ch : text.toCharArray()) {
            heap.writeChar(ch);
        }
        ObjectInstance stringId = newInstance(typeString).putImpl("value", instanceId).putInt("hash", text.hashCode()).dumpInstance();
        heapStrings.put(text, stringId);
        return stringId;
    }

    /**
     * Dumps a primitive value ({@code int}, {@code long}, {@code float}, {@code byte}, {@code char}
     * & other) into the {@link HeapDump}. Encodes the value as appropriate boxed object (
     * {@link Integer}, {@link Long}, {@link Float}, {@link Byte}, {@link Character} & co.).
     *
     * @param obj primitive value
     * @return object instance representing the value in the heap
     * @throws UncheckedIOException when an I/O error occurs
     *
     * @since 21.1
     */
    public ObjectInstance dumpPrimitive(Object obj) throws UncheckedIOException {
        ObjectInstance id = primitives.get(obj);
        if (id != null) {
            return id;
        }
        final Class<? extends Object> clazz = obj.getClass();
        ClassInstance wrapperClass = primitiveClasses.get(clazz);
        if (wrapperClass == null) {
            Class<?> primitiveType = findPrimitiveType(clazz);
            wrapperClass = newClass(clazz.getName()).field("value", primitiveType).dumpClass();
            primitiveClasses.put(clazz, wrapperClass);
        }
        ObjectInstance instanceId = newInstance(wrapperClass).putImpl("value", obj).dumpInstance();
        primitives.put(obj, instanceId);
        return instanceId;
    }

    /**
     * Allows one to describe a state of a thread with local variables and record it in the
     * generated {@link HeapDump}.
     * <p>
     * {@codesnippet org.graalvm.tools.insight.test.heap.HeapDumpTest#cyclic}
     *
     * @since 21.1
     * @see HeapDump#newThread(java.lang.String)
     */
    public final class ThreadBuilder {

        private final List<Object[]> stacks;
        private final String name;

        private ThreadBuilder(String name) {
            this.stacks = new ArrayList<>();
            this.name = name;
        }

        /**
         * Adds a {@link StackTraceElement} to the thread representation. Contains class/source
         * location information as well as references to local variables.
         *
         * @param clazz class for this frame
         * @param methodName method for this frame
         * @param sourceFile path/location of a file for this frame
         * @param lineNumber line number for this frame
         * @param locals array of references to local objects referenced from the frame
         * @return {@code this} builder
         *
         * @since 21.1
         */
        public ThreadBuilder addStackFrame(ClassInstance clazz, String methodName, String sourceFile, int lineNumber, ObjectInstance... locals) {
            stacks.add(new Object[]{methodName, sourceFile, lineNumber, locals, clazz});
            return this;
        }

        /**
         * Records the prepared thread information into the {@link HeapDump}.
         *
         * @return object instance representing the {@link Thread} object in the heap
         * @throws UncheckedIOException when an I/O error occurs
         * @since 21.1
         */
        public ObjectInstance dumpThread() throws UncheckedIOException {
            try {
                return dumpThreadImpl();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        private ObjectInstance dumpThreadImpl() throws IOException {
            ObjectInstance nameId = dumpString(name);
            int threadSerialId = builder.threadCounter.next();
            ObjectInstance threadId = newInstance(typeThread).putBoolean("daemon", false).put("name", nameId).putInt("priority", 0).dumpInstance();
            int[] frameIds = new int[stacks.size()];
            int cnt = 0;
            for (Object[] frame : stacks) {
                final ClassInstance language = (ClassInstance) frame[4];
                final String rootName = (String) frame[0];
                final String sourceFile = (String) frame[1];
                final int lineNumber = (Integer) frame[2];
                frameIds[cnt++] = builder.writeStackFrame(HeapDump.this, language, rootName, sourceFile, lineNumber);
            }
            int stackTraceId = builder.writeStackTrace(threadSerialId, frameIds);
            heap.writeByte(HEAP_ROOT_THREAD_OBJECT);
            builder.ids.writeID(heap, threadId.id(HeapDump.this));
            heap.writeInt(threadSerialId);
            heap.writeInt(stackTraceId); // stacktrace #
            cnt = 0;
            for (Object[] frame : stacks) {
                ObjectInstance[] localObjects = (ObjectInstance[]) frame[3];
                for (int i = 0; i < localObjects.length; i++) {
                    int objId = localObjects[i].id(HeapDump.this);
                    heap.writeByte(HEAP_ROOT_JAVA_FRAME); // frame GC root
                    builder.ids.writeID(heap, objId);
                    heap.writeInt(threadSerialId); // thread serial #
                    heap.writeInt(cnt); // frame number
                }
                cnt++;
            }
            return threadId;
        }
    }

    /**
     * Builds structure of a new class for the {@link HeapDump}.
     * <p>
     * {@codesnippet org.graalvm.tools.insight.test.heap.HeapDumpTest#generateSampleHeapDump}
     *
     * @since 21.1
     * @see HeapDump#newClass(java.lang.String)
     */
    public final class ClassBuilder {
        private final String className;
        private final int superId;
        private TreeMap<String, Class<?>> fieldNamesAndTypes = new TreeMap<>();

        private ClassBuilder(String name, int superId) {
            this.className = name;
            this.superId = superId;
        }

        /**
         * Adds new field with given name and type to the class definition. Primitive {@code type}
         * (like ({@code int.class} & co.) fields values are stored directly in the
         * {@link InstanceBuilder instance dump}. Other types are references to other
         * {@link ObjectInstance instances} in the dump.
         *
         *
         * @param name name of the field
         * @param type the type of the view
         * @return {@code this} builder
         *
         * @since 21.1
         */
        public ClassBuilder field(String name, Class<?> type) {
            if (type.isPrimitive()) {
                fieldNamesAndTypes.put(name, type);
            } else {
                fieldNamesAndTypes.put(name, Object.class);
            }
            return this;
        }

        /**
         * Adds new field with given name and type to the class definition. Values of such field are
         * to other {@link ObjectInstance instances} in the dump.
         *
         *
         * @param name name of the field
         * @param type the type of the view
         * @return {@code this} builder
         *
         * @since 21.1
         */
        public ClassBuilder field(String name, ClassInstance type) {
            fieldNamesAndTypes.put(name, Object.class);
            return this;
        }

        /**
         * Dumps the class definition into the {@link HeapDump}.
         *
         * @return class instance to use when building instances
         * @throws UncheckedIOException when an I/O error occurs
         *
         * @see HeapDump#newInstance(org.graalvm.tools.insight.heap.HeapDump.ClassInstance)
         * @since 21.1
         */
        public ClassInstance dumpClass() throws UncheckedIOException {
            try {
                return dumpClassImpl();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        private ClassInstance dumpClassImpl() throws IOException {
            final int classSerialId = builder.classCounter.next();
            int classId = builder.writeLoadClass(className, classSerialId);
            heap.writeByte(HEAP_CLASS_DUMP);
            builder.ids.writeID(heap, classId);
            builder.writeDefaultStackTraceSerialNumber(heap);
            builder.ids.writeID(heap, superId);
            builder.ids.writeID(heap, 0); // classloader ID
            builder.ids.writeID(heap, 0); // signers ID
            builder.ids.writeID(heap, 0); // protection domain ID
            builder.ids.writeID(heap, 0); // reserved 1
            builder.ids.writeID(heap, 0); // reserved 2
            int instanceSize = 0;
            for (Map.Entry<String, Class<?>> entry : fieldNamesAndTypes.entrySet()) {
                final Class<?> type = entry.getValue();
                instanceSize += switchOnType(type, builder.ids)[1];
            }
            heap.writeInt(instanceSize);
            heap.writeShort(0); // # of constant pool entries
            heap.writeShort(0); // # of static fields
            heap.writeShort(fieldNamesAndTypes.size()); // # of instance fields
            for (Map.Entry<String, Class<?>> entry : fieldNamesAndTypes.entrySet()) {
                int nId = builder.writeString(entry.getKey());
                heap.writeInt(nId);
                final Class<?> type = entry.getValue();
                heap.writeByte(switchOnType(type, builder.ids)[0]);
            }
            ClassInstance inst = new ClassInstance(classSerialId, classId, fieldNamesAndTypes, instanceSize);
            fieldNamesAndTypes = new TreeMap<>();
            return inst;
        }
    }

    /**
     * Fills data for new object instance to put into the {@link HeapDump}.
     * <p>
     * {@codesnippet org.graalvm.tools.insight.test.heap.HeapDumpTest#generateSampleHeapDump}
     *
     * @since 21.1
     * @see HeapDump#newInstance(org.graalvm.tools.insight.heap.HeapDump.ClassInstance)
     */
    public final class InstanceBuilder {

        private final ClassInstance clazz;
        private final ObjectInstance instanceId;
        private final List<Object> namesAndValues = new ArrayList<>();

        private InstanceBuilder(ClassInstance clazz, int instanceId) {
            this.clazz = clazz;
            this.instanceId = new ObjectInstance(instanceId);
        }

        /**
         * Puts reference to another object as a value of the field.
         *
         * @param name the name of the field
         * @param value reference to object in the {@link HeapDump}
         * @return {@code this} builder
         * @throws IllegalArgumentException if the field doesn't exist or its type isn't correct
         *
         * @since 21.1
         */
        public InstanceBuilder put(String name, ObjectInstance value) {
            assertType(name, value.getClass());
            return putImpl(name, value.id(HeapDump.this));
        }

        /**
         * Puts value into a field.
         *
         * @param name the name of the field
         * @param value primitive value to assign to the field
         * @return {@code this} builder
         * @throws IllegalArgumentException if the field doesn't exist or its type isn't correct
         *
         * @since 21.1
         */
        public InstanceBuilder putByte(String name, byte value) {
            assertType(name, Byte.TYPE);
            return putImpl(name, value);
        }

        /**
         * Puts value into a field.
         *
         * @param name the name of the field
         * @param value primitive value to assign to the field
         * @return {@code this} builder
         * @throws IllegalArgumentException if the field doesn't exist or its type isn't correct
         *
         * @since 21.1
         */
        public InstanceBuilder putShort(String name, short value) {
            assertType(name, Short.TYPE);
            return putImpl(name, value);
        }

        /**
         * Puts value into a field.
         *
         * @param name the name of the field
         * @param value primitive value to assign to the field
         * @return {@code this} builder
         * @throws IllegalArgumentException if the field doesn't exist or its type isn't correct
         *
         * @since 21.1
         */
        public InstanceBuilder putInt(String name, int value) {
            assertType(name, Integer.TYPE);
            return putImpl(name, value);
        }

        /**
         * Puts value into a field.
         *
         * @param name the name of the field
         * @param value primitive value to assign to the field
         * @return {@code this} builder
         * @throws IllegalArgumentException if the field doesn't exist or its type isn't correct
         *
         * @since 21.1
         */
        public InstanceBuilder putLong(String name, long value) {
            assertType(name, Long.TYPE);
            return putImpl(name, value);
        }

        /**
         * Puts value into a field.
         *
         * @param name the name of the field
         * @param value primitive value to assign to the field
         * @return {@code this} builder
         * @throws IllegalArgumentException if the field doesn't exist or its type isn't correct
         *
         * @since 21.1
         */
        public InstanceBuilder putFloat(String name, float value) {
            assertType(name, Float.TYPE);
            return putImpl(name, value);
        }

        /**
         * Puts value into a field.
         *
         * @param name the name of the field
         * @param value primitive value to assign to the field
         * @return {@code this} builder
         * @throws IllegalArgumentException if the field doesn't exist or its type isn't correct
         *
         * @since 21.1
         */
        public InstanceBuilder putDouble(String name, double value) {
            assertType(name, Double.TYPE);
            return putImpl(name, value);
        }

        /**
         * Puts value into a field.
         *
         * @param name the name of the field
         * @param value primitive value to assign to the field
         * @return {@code this} builder
         * @throws IllegalArgumentException if the field doesn't exist or its type isn't correct
         *
         * @since 21.1
         */
        public InstanceBuilder putBoolean(String name, boolean value) {
            assertType(name, Boolean.TYPE);
            return putImpl(name, value);
        }

        /**
         * Puts value into a field.
         *
         * @param name the name of the field
         * @param value primitive value to assign to the field
         * @return {@code this} builder
         * @throws IllegalArgumentException if the field doesn't exist or its type isn't correct
         *
         * @since 21.1
         */
        public InstanceBuilder putChar(String name, char value) {
            assertType(name, Character.TYPE);
            return putImpl(name, value);
        }

        private void assertType(String name, Class<?> valueType) throws IllegalArgumentException {
            Class<?> type = clazz.fieldNamesAndTypes.get(name);
            if (type == null) {
                throw new IllegalArgumentException("Unknown field '" + name + "'");
            }
            if (!type.isAssignableFrom(valueType)) {
                throw new IllegalArgumentException("Wrong type for field '" + name + "'");
            }
        }

        InstanceBuilder putImpl(String name, Object value) {
            namesAndValues.add(name);
            namesAndValues.add(value);
            return this;
        }

        /**
         * Dumps the gathered field values into the {@link HeapDump}.
         *
         * @return object representing the written instance
         * @throws UncheckedIOException when an I/O error occurs
         *
         * @see #put(java.lang.String, org.graalvm.tools.insight.heap.HeapDump.ObjectInstance)
         * @since 21.1
         */
        public ObjectInstance dumpInstance() throws UncheckedIOException {
            try {
                dumpInstance(HeapDump.this, namesAndValues.toArray());
                return instanceId;
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        /**
         * The ID assigned to the instance. Allows one to obtain ID of an instance before it is
         * dumped into the {@link HeapDump}.
         * <p>
         * {@codesnippet org.graalvm.tools.insight.test.heap.HeapDumpTest#cyclic}
         *
         * @return object reference for the instance that's going to be built when
         *         {@link #dumpInstance()} method is invoked
         *
         * @since 21.1
         */
        public ObjectInstance id() {
            return instanceId;
        }

        private void dumpInstance(HeapDump thiz, Object... stringValueSeq) throws IOException {
            HashMap<String, Object> values = new HashMap<>();
            for (int i = 0; i < stringValueSeq.length; i += 2) {
                values.put((String) stringValueSeq[i], stringValueSeq[i + 1]);
            }
            heap.writeByte(HEAP_INSTANCE_DUMP);
            builder.ids.writeID(heap, instanceId.id(thiz));
            builder.writeDefaultStackTraceSerialNumber(heap);
            builder.ids.writeID(heap, clazz.id(thiz));
            heap.writeInt(clazz.fieldBytes);
            for (Map.Entry<String, Class<?>> entry : clazz.fieldNamesAndTypes.entrySet()) {
                final Class<?> type = entry.getValue();
                final Object ref = values.get(entry.getKey());
                if (type == Boolean.TYPE || type == Byte.TYPE) {
                    int n;
                    if (ref instanceof Number) {
                        n = ((Number) ref).byteValue();
                    } else if (ref instanceof Boolean) {
                        n = ((Boolean) ref) ? 1 : 0;
                    } else {
                        n = 0;
                    }
                    heap.writeByte(n);
                } else if (entry.getValue() == Integer.TYPE) {
                    heap.writeInt(ref == null ? 0 : ((Number) ref).intValue());
                } else if (entry.getValue() == Short.TYPE) {
                    heap.writeShort(ref == null ? 0 : ((Number) ref).shortValue());
                } else if (entry.getValue() == Long.TYPE) {
                    heap.writeLong(ref == null ? 0 : ((Number) ref).longValue());
                } else if (entry.getValue() == Float.TYPE) {
                    heap.writeFloat(ref == null ? 0 : ((Number) ref).floatValue());
                } else if (entry.getValue() == Double.TYPE) {
                    heap.writeDouble(ref == null ? 0 : ((Number) ref).doubleValue());
                } else if (entry.getValue() == Character.TYPE) {
                    heap.writeChar(ref == null ? 0 : ((Character) ref));
                } else {
                    builder.ids.writeID(heap, ref == null ? 0 : ((Number) ref).intValue());
                }
            }
        }
    }

    /**
     * Fills data for new array to put into the {@link HeapDump}.
     *
     * @since 21.3.2
     * @see HeapDump#newArray(int)
     */
    public final class ArrayBuilder {
        private final ClassInstance clazz;
        private final ObjectInstance instanceId;
        private final List<ObjectInstance> elements;

        private ArrayBuilder(ClassInstance clazz, int length, int instanceId) {
            this.clazz = clazz;
            this.elements = Arrays.asList(new ObjectInstance[length]);
            this.instanceId = new ObjectInstance(instanceId);
        }

        /**
         * Puts reference to another object into the array.
         *
         * @param index zero based index into the array
         * @param value reference to object in the {@link HeapDump}
         * @return {@code this} builder
         *
         * @since 21.3.2
         */
        public ArrayBuilder put(int index, ObjectInstance value) {
            elements.set(index, value);
            return this;
        }

        /**
         * Dumps the gathered array values into the {@link HeapDump}.
         *
         * @return object representing the written instance
         * @throws UncheckedIOException when an I/O error occurs
         *
         * @see #put(int, org.graalvm.tools.insight.heap.HeapDump.ObjectInstance)
         * @since 21.3.2
         */
        public ObjectInstance dumpInstance() throws UncheckedIOException {
            try {
                dumpArray(HeapDump.this);
                return instanceId;
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        /**
         * The ID assigned to the instance. Allows one to obtain ID of an instance before it is
         * dumped into the {@link HeapDump}.
         * <p>
         * {@codesnippet org.graalvm.tools.insight.test.heap.HeapDumpTest#cyclic}
         *
         * @return object reference for the instance that's going to be built when
         *         {@link #dumpInstance()} method is invoked
         *
         * @since 21.3.2
         */
        public ObjectInstance id() {
            return instanceId;
        }

        private void dumpArray(HeapDump thiz) throws IOException {
            heap.writeByte(HEAP_OBJECT_ARRAY_DUMP);
            builder.ids.writeID(heap, instanceId.id(thiz));
            builder.writeDefaultStackTraceSerialNumber(heap);
            heap.writeInt(elements.size());
            builder.ids.writeID(heap, clazz.id(thiz));
            for (ObjectInstance ref : elements) {
                builder.ids.writeID(heap, ref == null ? 0 : ref.id(thiz));
            }
        }
    }

    private static int[] switchOnType(final Class<?> type, Identifiers ids) throws IllegalStateException {
        if (type.isPrimitive()) {
            if (type == Boolean.TYPE) {
                return new int[]{TYPE_BOOLEAN, 1};
            } else if (type == Character.TYPE) {
                return new int[]{TYPE_CHAR, 2};
            } else if (type == Float.TYPE) {
                return new int[]{TYPE_FLOAT, 4};
            } else if (type == Double.TYPE) {
                return new int[]{TYPE_DOUBLE, 8};
            } else if (type == Byte.TYPE) {
                return new int[]{TYPE_BYTE, 1};
            } else if (type == Short.TYPE) {
                return new int[]{TYPE_SHORT, 2};
            } else if (type == Integer.TYPE) {
                return new int[]{TYPE_INT, 4};
            } else if (type == Long.TYPE) {
                return new int[]{TYPE_LONG, 8};
            } else {
                throw new IllegalStateException("Unsupported primitive type: " + type);
            }
        } else {
            return new int[]{TYPE_OBJECT, ids.sizeOf()};
        }
    }

    private static Class<?> findPrimitiveType(final Class<? extends Object> clazz) throws IllegalStateException {
        assert clazz.getName().startsWith("java.lang.");
        Class<?> primitiveType;
        switch (clazz.getName()) {
            case "java.lang.Boolean":
                primitiveType = Boolean.TYPE;
                break;
            case "java.lang.Byte":
                primitiveType = Byte.TYPE;
                break;
            case "java.lang.Short":
                primitiveType = Short.TYPE;
                break;
            case "java.lang.Integer":
                primitiveType = Integer.TYPE;
                break;
            case "java.lang.Long":
                primitiveType = Long.TYPE;
                break;
            case "java.lang.Float":
                primitiveType = Float.TYPE;
                break;
            case "java.lang.Double":
                primitiveType = Double.TYPE;
                break;
            case "java.lang.Character":
                primitiveType = Character.TYPE;
                break;
            default:
                throw new IllegalStateException(clazz.getName());
        }
        assert primitiveType.isPrimitive();
        return primitiveType;
    }

    private static final class Counter {
        private final String name;
        private int value;

        Counter(String name) {
            this.name = name;
        }

        int next() throws IOException {
            if (value == Integer.MAX_VALUE) {
                throw new IOException("Overflow of " + name + "counter");
            }
            return ++value;
        }
    }
}
