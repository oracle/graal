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

//
// There is a later copy of the same code/idea available at
// https://github.com/apache/netbeans/blob/release122/profiler/lib.profiler/test/unit/src/org/netbeans/lib/profiler/heap/HeapUtils.java
//

package com.oracle.truffle.tools.heapdump;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class HeapUtils {
    // constants for the Java Profiler Heap Dump Format
    // http://hg.openjdk.java.net/jdk6/jdk6/jdk/raw-file/tip/src/share/demo/jvmti/hprof/manual.html
    //
    private static final String MAGIC_WITH_SEGMENTS = "JAVA PROFILE 1.0.1";
    private static final int TAG_STRING = 0x01;
    private static final int TAG_LOAD_CLASS = 0x02;
    private static final int TAG_STACK_FRAME = 0x04;
    private static final int TAG_STACK_TRACE = 0x05;
    private static final int TAG_START_THREAD = 0x0A;
    private static final int TAG_HEAP_DUMP = 0x0c;

    // heap dump codes
    private static final int HEAP_ROOT_JAVA_FRAME = 0x03;
    private static final int HEAP_ROOT_THREAD_OBJECT = 0x08;
    private static final int HEAP_CLASS_DUMP = 0x20;
    private static final int HEAP_INSTANCE_DUMP = 0x21;
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

    static final class HprofGenerator implements Closeable {
        private final Identifiers ids;
        private final Map<String, Integer> wholeStrings = new HashMap<>();
        private final Map<String, Integer> heapStrings = new HashMap<>();
        private final Map<Class<?>, ClassInstance> primitiveClasses = new HashMap<>();
        private final Map<Object, Integer> primitives = new HashMap<>();
        private final DataOutputStream whole;
        private int objectCounter;
        private ClassInstance typeObject;
        private ClassInstance typeString;
        private ClassInstance typeThread;

        HprofGenerator(OutputStream os) throws IOException {
            this(Identifiers.FOUR, os);
        }

        private HprofGenerator(Identifiers ids, OutputStream os) throws IOException {
            this.whole = new DataOutputStream(os);
            this.ids = ids;
            whole.write(MAGIC_WITH_SEGMENTS.getBytes());
            whole.write(0); // null terminated string
            whole.writeInt(ids.sizeOf());
            whole.writeLong(System.currentTimeMillis());
        }

        interface Generator<T> {
            void generate(T data) throws IOException;
        }

        public final class HeapDump {
            private final DataOutputStream heap;

            private HeapDump(OutputStream out) {
                this.heap = new DataOutputStream(out);
            }

            public ClassBuilder newClass(String name) throws IOException {
                int classId = writeLoadClass(0, name);
                return new ClassBuilder(classId, typeObject.id);
            }

            public ThreadBuilder newThread(String name) {
                return new ThreadBuilder(name);
            }

            public InstanceBuilder newInstance(ClassInstance clazz) {
                return new InstanceBuilder(clazz, ++objectCounter);
            }

            private void flush() throws IOException {
                heap.flush();
            }

            public int dumpString(String text) throws IOException {
                if (text == null) {
                    return 0;
                }
                Integer id = heapStrings.get(text);
                if (id != null) {
                    return id;
                }

                int instanceId = ++objectCounter;

                heap.writeByte(HEAP_PRIMITIVE_ARRAY_DUMP);
                ids.writeID(heap, instanceId);
                heap.writeInt(instanceId); // serial number
                heap.writeInt(text.length()); // number of elements
                heap.writeByte(TYPE_CHAR);
                for (char ch : text.toCharArray()) {
                    heap.writeChar(ch);
                }
                int stringId = newInstance(typeString).put("value", instanceId).put("hash", 0).dumpInstance();

                heapStrings.put(text, stringId);
                return stringId;
            }

            public int dumpPrimitive(Object obj) throws IOException {
                Integer id = primitives.get(obj);
                if (id != null) {
                    return id;
                }

                final Class<? extends Object> clazz = obj.getClass();
                ClassInstance wrapperClass = primitiveClasses.get(clazz);
                if (wrapperClass == null) {
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

                    wrapperClass = newClass(clazz.getName()).addField("value", primitiveType).dumpClass();
                    primitiveClasses.put(clazz, wrapperClass);
                }
                int instanceId = newInstance(wrapperClass).put("value", obj).dumpInstance();
                primitives.put(obj, instanceId);
                return instanceId;
            }

            public final class ThreadBuilder {

                private String groupName;
                private final List<Object[]> stacks;
                private final String name;

                private ThreadBuilder(String name) {
                    this.stacks = new ArrayList<>();
                    this.name = name;
                }

                public ThreadBuilder group(String group) {
                    this.groupName = group;
                    return this;
                }

                public ThreadBuilder addStackFrame(ClassInstance language, String rootName, String sourceFile, int lineNumber, int... locals) {
                    stacks.add(new Object[]{rootName, sourceFile, lineNumber, locals, language});
                    return this;
                }

                public int dumpThread() throws IOException {
                    if (typeThread == null) {
                        typeThread = newClass("java.lang.Thread").addField("daemon", Boolean.TYPE).addField("name", String.class).addField("priority", Integer.TYPE).dumpClass();
                    }
                    int nameId = dumpString(name);
                    int threadId = newInstance(typeThread).put("daemon", 0).put("name", nameId).put("priority", 0).dumpInstance();

                    int[] frameIds = new int[stacks.size()];
                    int cnt = 0;
                    for (Object[] frame : stacks) {
                        frameIds[cnt++] = writeStackFrame((ClassInstance) frame[4], (String) frame[0], (String) frame[1], (Integer) frame[2]);
                    }
                    int stackTraceId = writeStackTrace(threadId, frameIds);
                    writeThreadStarted(threadId, name, groupName, stackTraceId);

                    heap.writeByte(HEAP_ROOT_THREAD_OBJECT);
                    ids.writeID(heap, threadId);
                    heap.writeInt(threadId); // serial #
                    heap.writeInt(stackTraceId); // stacktrace #

                    cnt = 0;
                    for (Object[] frame : stacks) {
                        int[] locals = (int[]) frame[3];
                        for (int objId : locals) {
                            heap.writeByte(HEAP_ROOT_JAVA_FRAME); // frame GC root
                            ids.writeID(heap, objId);
                            heap.writeInt(threadId); // thread serial #
                            heap.writeInt(cnt); // frame number
                        }
                        cnt++;
                    }

                    return threadId;
                }
            }

            public final class ClassBuilder {

                private final int classId;
                private final int superId;
                private TreeMap<String, Class<?>> fieldNamesAndTypes = new TreeMap<>();

                private ClassBuilder(int id, int superId) {
                    this.classId = id;
                    this.superId = superId;
                }

                public ClassBuilder addField(String name, Class<?> type) {
                    fieldNamesAndTypes.put(name, type);
                    return this;
                }

                public ClassInstance dumpClass() throws IOException {
                    heap.writeByte(HEAP_CLASS_DUMP);
                    ids.writeID(heap, classId);
                    heap.writeInt(classId); // stacktrace serial number
                    ids.writeID(heap, superId);
                    ids.writeID(heap, 0); // classloader ID
                    ids.writeID(heap, 0); // signers ID
                    ids.writeID(heap, 0); // protection domain ID
                    ids.writeID(heap, 0); // reserved 1
                    ids.writeID(heap, 0); // reserved 2
                    heap.writeInt(0); // instance size
                    heap.writeShort(0); // # of constant pool entries
                    heap.writeShort(0); // # of static fields
                    heap.writeShort(fieldNamesAndTypes.size()); // # of instance fields
                    int fieldBytes = 0;
                    for (Map.Entry<String, Class<?>> entry : fieldNamesAndTypes.entrySet()) {
                        int nId = writeString(entry.getKey());
                        heap.writeInt(nId);
                        final Class<?> type = entry.getValue();
                        if (type.isPrimitive()) {
                            if (type == Boolean.TYPE) {
                                heap.writeByte(TYPE_BOOLEAN);
                                fieldBytes++;
                            } else if (type == Character.TYPE) {
                                heap.writeByte(TYPE_CHAR);
                                fieldBytes += 2;
                            } else if (type == Float.TYPE) {
                                heap.writeByte(TYPE_FLOAT);
                                fieldBytes += 4;
                            } else if (type == Double.TYPE) {
                                heap.writeByte(TYPE_DOUBLE);
                                fieldBytes += 8;
                            } else if (type == Byte.TYPE) {
                                heap.writeByte(TYPE_BYTE);
                                fieldBytes++;
                            } else if (type == Short.TYPE) {
                                heap.writeByte(TYPE_SHORT);
                                fieldBytes += 2;
                            } else if (type == Integer.TYPE) {
                                heap.writeByte(TYPE_INT);
                                fieldBytes += 4;
                            } else if (type == Long.TYPE) {
                                heap.writeByte(TYPE_LONG);
                                fieldBytes += 8;
                            } else {
                                throw new IllegalStateException("Unsupported primitive type: " + type);
                            }
                        } else {
                            heap.writeByte(TYPE_OBJECT);
                            fieldBytes += 4;
                        }
                    }
                    ClassInstance inst = new ClassInstance(classId, fieldNamesAndTypes, fieldBytes);
                    fieldNamesAndTypes = new TreeMap<>();
                    return inst;
                }
            }

            public final class InstanceBuilder {
                private final ClassInstance clazz;
                private final int instanceId;
                private final List<Object> namesAndValues = new ArrayList<>();

                private InstanceBuilder(ClassInstance clazz, int instanceId) {
                    this.clazz = clazz;
                    this.instanceId = instanceId;
                }

                public InstanceBuilder put(String name, Object value) {
                    namesAndValues.add(name);
                    namesAndValues.add(value);
                    return this;
                }

                public int dumpInstance() throws IOException {
                    dumpInstance(namesAndValues.toArray());
                    return instanceId;
                }

                public int id() {
                    return instanceId;
                }

                private void dumpInstance(Object... stringValueSeq) throws IOException {
                    HashMap<String, Object> values = new HashMap<>();
                    for (int i = 0; i < stringValueSeq.length; i += 2) {
                        values.put((String) stringValueSeq[i], stringValueSeq[i + 1]);
                    }
                    heap.writeByte(HEAP_INSTANCE_DUMP);
                    ids.writeID(heap, instanceId);
                    heap.writeInt(instanceId); // serial number
                    ids.writeID(heap, clazz.id);
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
                            heap.writeInt(ref == null ? 0 : ((Number) ref).intValue());
                        }
                    }
                }
            }
        }

        public final class ClassInstance {

            private final int id;
            private final TreeMap<String, Class<?>> fieldNamesAndTypes;
            private final int fieldBytes;

            private ClassInstance(int id, TreeMap<String, Class<?>> fieldNamesAndTypes, int fieldBytes) {
                this.id = id;
                this.fieldNamesAndTypes = fieldNamesAndTypes;
                this.fieldBytes = fieldBytes;
            }

            public Set<String> names() {
                return Collections.unmodifiableSet(fieldNamesAndTypes.keySet());
            }
        }

        public void dumpHeap(Generator<HeapDump> generator) throws IOException {
            final ByteArrayOutputStream rawHeap = new ByteArrayOutputStream();
            HeapDump seg = new HeapDump(rawHeap);
            if (typeObject == null) {
                int classId = writeLoadClass(0, "java.lang.Object");
                typeObject = seg.new ClassBuilder(classId, 0).dumpClass();
                seg.newClass("char[]").dumpClass();
                typeString = seg.newClass("java.lang.String").addField("value", char[].class).addField("hash", Integer.TYPE).dumpClass();
            }
            generator.generate(seg);
            seg.flush();
            if (rawHeap.size() > 0) {
                whole.writeByte(TAG_HEAP_DUMP);
                whole.writeInt(0); // ms
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

        // internal primitives
        private void writeThreadStarted(int id, String threadName, String groupName, int stackTraceId) throws IOException {
            int threadNameId = writeString(threadName);
            int groupNameId = writeString(groupName);

            whole.writeByte(TAG_START_THREAD);
            whole.writeInt(0); // ms
            whole.writeInt(8 + ids.sizeOf() * 4); // size of following entries
            whole.writeInt(id); // serial number
            ids.writeID(whole, id); // object id
            whole.writeInt(stackTraceId); // stacktrace serial number
            ids.writeID(whole, threadNameId);
            ids.writeID(whole, groupNameId);
            ids.writeID(whole, 0); // parent group
        }

        private int writeStackFrame(ClassInstance language, String rootName, String sourceFile, int lineNumber) throws IOException {
            int id = ++objectCounter;

            int rootNameId = writeString(rootName);
            int signatureId = 0;
            int sourceFileId = writeString(sourceFile);

            whole.writeByte(TAG_STACK_FRAME);
            whole.writeInt(0); // ms
            whole.writeInt(8 + ids.sizeOf() * 4); // size of following entries
            ids.writeID(whole, id);
            ids.writeID(whole, rootNameId);
            ids.writeID(whole, signatureId);
            ids.writeID(whole, sourceFileId);
            whole.writeInt(language.id);
            whole.writeInt(lineNumber);

            return id;
        }

        private int writeStackTrace(int threadId, int... frames) throws IOException {
            int id = ++objectCounter;

            whole.writeByte(TAG_STACK_TRACE);
            whole.writeInt(0); // ms
            whole.writeInt(12 + ids.sizeOf() * frames.length); // size of following entries
            whole.writeInt(id); // stack trace serial number
            whole.writeInt(threadId); // thread serial number
            whole.writeInt(frames.length);
            for (int fId : frames) {
                ids.writeID(whole, fId);
            }

            return id;
        }

        private int writeLoadClass(int stackTrace, String className) throws IOException {
            int classId = ++objectCounter;
            int classNameId = writeString(className);

            whole.writeByte(TAG_LOAD_CLASS);
            whole.writeInt(0); // ms
            whole.writeInt(8 + ids.sizeOf() * 2); // size of following entries
            whole.writeInt(classId); // class serial number
            ids.writeID(whole, classId); // class object ID
            whole.writeInt(stackTrace); // stack trace serial number
            ids.writeID(whole, classNameId); // class name string ID

            return classId;
        }

        private int writeString(String text) throws IOException {
            if (text == null) {
                return 0;
            }
            Integer prevId = wholeStrings.get(text);
            if (prevId != null) {
                return prevId;
            }
            int stringId = ++objectCounter;
            whole.writeByte(TAG_STRING);
            whole.writeInt(0); // ms
            byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
            whole.writeInt(ids.sizeOf() + utf8.length);
            ids.writeID(whole, stringId);
            whole.write(utf8);

            wholeStrings.put(text, stringId);
            return stringId;
        }
    }

}
