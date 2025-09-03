/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.resident.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.FrameSourceInfo;
import com.oracle.svm.core.deopt.DeoptState;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.interpreter.InterpreterFrameSourceInfo;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.interpreter.DebuggerSupport;
import com.oracle.svm.interpreter.EspressoFrame;
import com.oracle.svm.interpreter.InterpreterFrame;
import com.oracle.svm.interpreter.InterpreterToVM;
import com.oracle.svm.interpreter.SemanticJavaException;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaField;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedObjectType;
import com.oracle.svm.interpreter.metadata.InterpreterUniverse;
import com.oracle.svm.interpreter.metadata.MetadataUtil;
import com.oracle.svm.jdwp.bridge.ErrorCode;
import com.oracle.svm.jdwp.bridge.FrameId;
import com.oracle.svm.jdwp.bridge.InvokeOptions;
import com.oracle.svm.jdwp.bridge.JDWP;
import com.oracle.svm.jdwp.bridge.JDWPException;
import com.oracle.svm.jdwp.bridge.Logger;
import com.oracle.svm.jdwp.bridge.Packet;
import com.oracle.svm.jdwp.bridge.SymbolicRefs;
import com.oracle.svm.jdwp.bridge.TagConstants;
import com.oracle.svm.jdwp.bridge.TypeTag;
import com.oracle.svm.jdwp.bridge.WritablePacket;
import com.oracle.svm.jdwp.resident.ClassUtils;
import com.oracle.svm.jdwp.resident.JDWPBridgeImpl;
import com.oracle.svm.jdwp.resident.ThreadStartDeathSupport;
import com.oracle.svm.jdwp.resident.api.StackframeDescriptor;

import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class ResidentJDWP implements JDWP {

    private static final boolean LOGGING = false;
    public static Logger LOGGER = new Logger(LOGGING, "[ResidentJDWP]", System.err);

    private final SymbolicRefs symbolicRefs = new ResidentSymbolicRefs();

    public ResidentJDWP() {
    }

    /**
     * Reads a reference, can be null.
     *
     * @throws JDWPException {@link ErrorCode#INVALID_OBJECT} if the object was collected or the
     *             object id is invalid
     */
    private static Object readReferenceOrNull(Packet.Reader reader) throws JDWPException {
        long objectId = reader.readLong();
        Object value = JDWPBridgeImpl.getIds().getObject(objectId);
        if (objectId != 0 && value == null) {
            throw JDWPException.raise(ErrorCode.INVALID_OBJECT);
        }
        return value;
    }

    /**
     * Reads an {@link Class#isArray() array}.
     *
     * @throws JDWPException {@link ErrorCode#INVALID_ARRAY} if the reference is null,
     *             {@link ErrorCode#INVALID_OBJECT} if the object id was collected or is invalid
     */
    private static Object readArray(Packet.Reader reader) throws JDWPException {
        Object array = readReferenceOrNull(reader);
        if (array == null || !array.getClass().isArray()) {
            throw JDWPException.raise(ErrorCode.INVALID_ARRAY);
        }
        return array;
    }

    /**
     * Reads an object of the given class. If the object is {@code null} or not and instance of the
     * given class, this method throws {@link JDWPException} with the provided error code is thrown.
     *
     * @throws JDWPException {@link ErrorCode#INVALID_OBJECT} if the object id was collected or is
     *             invalid
     */
    private static <T> T readTypedObject(Packet.Reader reader, Class<T> clazz, ErrorCode errorCode) throws JDWPException {
        Object object = readReferenceOrNull(reader);
        if (clazz.isInstance(object)) {
            return clazz.cast(object);
        }
        throw JDWPException.raise(errorCode);
    }

    /**
     * Reads a {@link String} reference. If the object is {@code null} or not an instance of
     * {@link String}, this method throws {@link JDWPException} with
     * {@link ErrorCode#INVALID_STRING}.
     *
     * @throws JDWPException {@link ErrorCode#INVALID_OBJECT} if the object id was collected or is
     *             invalid
     */
    private static String readStringObject(Packet.Reader reader) throws JDWPException {
        return readTypedObject(reader, String.class, ErrorCode.INVALID_STRING);
    }

    /**
     * Reads an {@link InterpreterResolvedJavaType}. If the object is {@code null} or not an
     * instance of {@link InterpreterResolvedJavaType}, or it is <b>NOT</b> part of the
     * {@link DebuggerSupport#getUniverse() interpreter universe}, this method throws
     * {@link JDWPException} with {@link ErrorCode#INVALID_CLASS}.
     *
     * @throws JDWPException {@link ErrorCode#INVALID_OBJECT} if the object id was collected or is
     *             invalid
     */
    private static InterpreterResolvedJavaType readType(Packet.Reader reader) throws JDWPException {
        Object object = readReferenceOrNull(reader);
        if (object instanceof InterpreterResolvedJavaType interpreterResolvedJavaType) {
            InterpreterUniverse universe = DebuggerSupport.singleton().getUniverse();
            OptionalInt knownInUniverse = universe.getTypeIndexFor(interpreterResolvedJavaType);
            if (knownInUniverse.isPresent()) {
                return interpreterResolvedJavaType;
            }
        }
        throw JDWPException.raise(ErrorCode.INVALID_CLASS); // cannot be null
    }

    /**
     * Reads an {@link InterpreterResolvedJavaField}. If the object is {@code null} or not an
     * instance of {@link InterpreterResolvedJavaField}, or it is <b>NOT</b> part of the
     * {@link DebuggerSupport#getUniverse() interpreter universe}, this method throws
     * {@link JDWPException} with {@link ErrorCode#INVALID_FIELDID}.
     *
     * @throws JDWPException {@link ErrorCode#INVALID_OBJECT} if the object id was collected or is
     *             invalid
     */
    private static InterpreterResolvedJavaField readField(Packet.Reader reader) throws JDWPException {
        Object object = readReferenceOrNull(reader);
        if (object instanceof InterpreterResolvedJavaField interpreterResolvedJavaField) {
            InterpreterUniverse universe = DebuggerSupport.singleton().getUniverse();
            OptionalInt knownInUniverse = universe.getFieldIndexFor(interpreterResolvedJavaField);
            if (knownInUniverse.isPresent()) {
                return interpreterResolvedJavaField;
            }
        }
        throw JDWPException.raise(ErrorCode.INVALID_FIELDID); // cannot be null
    }

    /**
     * Reads an {@link InterpreterResolvedJavaMethod}. If the object is {@code null} or not an
     * instance of {@link InterpreterResolvedJavaMethod}, or it is <b>NOT</b> part of the
     * {@link DebuggerSupport#getUniverse() interpreter universe}, this method throws
     * {@link JDWPException} with {@link ErrorCode#INVALID_METHODID}.
     *
     * @throws JDWPException {@link ErrorCode#INVALID_OBJECT} if the object id was collected or is
     *             invalid
     */
    @SuppressWarnings("unused")
    private static InterpreterResolvedJavaMethod readMethod(Packet.Reader reader) throws JDWPException {
        Object object = readReferenceOrNull(reader);
        if (object instanceof InterpreterResolvedJavaMethod interpreterResolvedJavaMethod) {
            InterpreterUniverse universe = DebuggerSupport.singleton().getUniverse();
            OptionalInt knownInUniverse = universe.getMethodIndexFor(interpreterResolvedJavaMethod);
            if (knownInUniverse.isPresent()) {
                return interpreterResolvedJavaMethod;
            }
        }
        throw JDWPException.raise(ErrorCode.INVALID_METHODID); // cannot be null
    }

    /**
     * Reads a {@link ThreadGroup}. If the object is {@code null} or not an instance of
     * {@link ThreadGroup} this method throws {@link JDWPException} with
     * {@link ErrorCode#INVALID_THREAD_GROUP}.
     *
     * @throws JDWPException {@link ErrorCode#INVALID_OBJECT} if the object id was collected or is
     *             invalid
     */
    private static ThreadGroup readThreadGroup(Packet.Reader reader) throws JDWPException {
        return readTypedObject(reader, ThreadGroup.class, ErrorCode.INVALID_THREAD_GROUP);
    }

    /**
     * Reads a {@link Module}. If the object is {@code null} or not an instance of {@link Module}
     * this method throws {@link JDWPException} with {@link ErrorCode#INVALID_MODULE}.
     *
     * @throws JDWPException {@link ErrorCode#INVALID_OBJECT} if the object id was collected or is
     *             invalid
     */
    private static Module readModule(Packet.Reader reader) throws JDWPException {
        return readTypedObject(reader, Module.class, ErrorCode.INVALID_MODULE);
    }

    /**
     * Reads a {@link Class}. If the object is {@code null} or not an instance of {@link Class} this
     * method throws {@link JDWPException} with {@link ErrorCode#INVALID_CLASS}.
     *
     * @throws JDWPException {@link ErrorCode#INVALID_OBJECT} if the object id was collected or is
     *             invalid
     */
    private static Class<?> readClassObject(Packet.Reader reader) throws JDWPException {
        return readTypedObject(reader, Class.class, ErrorCode.INVALID_CLASS);
    }

    /**
     * Reads a {@link ClassLoader}. If the object is {@code null} or not an instance of
     * {@link Class} this method throws {@link JDWPException} with
     * {@link ErrorCode#INVALID_CLASS_LOADER}.
     *
     * @throws JDWPException {@link ErrorCode#INVALID_OBJECT} if the object id was collected or is
     *             invalid
     */
    @SuppressWarnings("unused")
    private static ClassLoader readClassLoader(Packet.Reader reader) throws JDWPException {
        return readTypedObject(reader, ClassLoader.class, ErrorCode.INVALID_CLASS_LOADER);
    }

    /**
     * Verify that the given object id is valid and not collected. An object id can be
     * {@link SymbolicRefs#NULL null}, which is valid in this method, thus the {@code ...orNull}
     * suffix.
     * 
     * @throws JDWPException {@link ErrorCode#INVALID_OBJECT} if the object id was collected or is
     *             invalid
     */
    private static Object verifyObjectIdOrNull(long objectId) throws JDWPException {
        Object object = JDWPBridgeImpl.getIds().getObject(objectId);
        if (object != null || objectId == SymbolicRefs.NULL) {
            return object;
        }
        // Reference was collected, or is unknown.
        throw JDWPException.raise(ErrorCode.INVALID_OBJECT);
    }

    /**
     * Verify that the given object id is a valid {@link InterpreterResolvedJavaType type}, not
     * null, not collected and part of the {@link DebuggerSupport#getUniverse() interpreter
     * universe}; otherwise it throws {@link JDWPException} with {@link ErrorCode#INVALID_CLASS}.
     *
     * @throws JDWPException {@link ErrorCode#INVALID_OBJECT} if the object id was collected or is
     *             invalid
     */
    @SuppressWarnings("unused")
    private static ResolvedJavaType verifyRefType(long refTypeId) throws JDWPException {
        Object object = verifyObjectIdOrNull(refTypeId);
        if (object instanceof InterpreterResolvedJavaType interpreterResolvedJavaType) {
            InterpreterUniverse universe = DebuggerSupport.singleton().getUniverse();
            OptionalInt knownInUniverse = universe.getTypeIndexFor(interpreterResolvedJavaType);
            if (knownInUniverse.isPresent()) {
                return interpreterResolvedJavaType;
            }
        }
        throw JDWPException.raise(ErrorCode.INVALID_CLASS); // cannot be null
    }

    /**
     * Verify that the given object id is a valid {@link InterpreterResolvedJavaField field}, not
     * null, not collected and part of the {@link DebuggerSupport#getUniverse() interpreter
     * universe}; otherwise it throws {@link JDWPException} with {@link ErrorCode#INVALID_FIELDID}.
     *
     * @throws JDWPException {@link ErrorCode#INVALID_OBJECT} if the object id was collected or is
     *             invalid
     */
    @SuppressWarnings("unused")
    private static ResolvedJavaField verifyRefField(long fieldId) throws JDWPException {
        Object object = verifyObjectIdOrNull(fieldId);
        if (object instanceof InterpreterResolvedJavaField interpreterResolvedJavaField) {
            InterpreterUniverse universe = DebuggerSupport.singleton().getUniverse();
            OptionalInt knownInUniverse = universe.getFieldIndexFor(interpreterResolvedJavaField);
            if (knownInUniverse.isPresent()) {
                return interpreterResolvedJavaField;
            }
        }
        throw JDWPException.raise(ErrorCode.INVALID_FIELDID); // cannot be null
    }

    /**
     * Verify that the given object id is a valid {@link InterpreterResolvedJavaMethod method}, not
     * null, not collected and part of the {@link DebuggerSupport#getUniverse() interpreter
     * universe}; otherwise it throws {@link JDWPException} with {@link ErrorCode#INVALID_METHODID}.
     *
     * @throws JDWPException {@link ErrorCode#INVALID_OBJECT} if the object id was collected or is
     *             invalid
     */
    @SuppressWarnings("unused")
    private static ResolvedJavaMethod verifyRefMethod(long methodId) throws JDWPException {
        Object object = verifyObjectIdOrNull(methodId);
        if (object instanceof InterpreterResolvedJavaMethod interpreterResolvedJavaMethod) {
            InterpreterUniverse universe = DebuggerSupport.singleton().getUniverse();
            OptionalInt knownInUniverse = universe.getMethodIndexFor(interpreterResolvedJavaMethod);
            if (knownInUniverse.isPresent()) {
                return interpreterResolvedJavaMethod;
            }
        }
        throw JDWPException.raise(ErrorCode.INVALID_METHODID); // cannot be null
    }

    /**
     * Writes a tagged-object, as specified in the JDWP spec. The {@link TagConstants tag} is
     * derived from the given object. {@code null} is tagged as {@link TagConstants#OBJECT}.
     */
    private static void writeTaggedObject(Packet.Writer writer, Object value) {
        writer.writeByte(TagConstants.getTagFromReference(value));
        writer.writeLong(JDWPBridgeImpl.getIds().getIdOrCreateWeak(value));
    }

    @Override
    public Packet VirtualMachine_AllThreads(Packet packet) throws JDWPException {
        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer data = reply.dataWriter();

        long[] allThreads = getAllThreadIds();
        data.writeInt(allThreads.length);
        for (long threadId : allThreads) {
            data.writeLong(threadId);
        }

        return reply;
    }

    private static VMMutex lockThreads() {
        VMMutex mutex;
        try {
            Field mutexField = VMThreads.class.getDeclaredField("THREAD_MUTEX");
            mutexField.setAccessible(true);
            mutex = (VMMutex) mutexField.get(null);
        } catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException | SecurityException ex) {
            ex.printStackTrace();
            throw JDWPException.raise(ErrorCode.INTERNAL);
        }
        mutex.lock();
        return mutex;
    }

    private static long[] getAllThreadIds() {
        long[] ids = new long[10];
        int i = 0;
        VMMutex mutex = lockThreads();
        try {
            for (IsolateThread thread = VMThreads.firstThreadUnsafe(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
                Thread t = ThreadStartDeathSupport.get().filterAppThread(thread);
                if (t == null) {
                    continue;
                }
                if (i >= ids.length) {
                    ids = Arrays.copyOf(ids, i + i / 2);
                }
                ids[i++] = JDWPBridgeImpl.getIds().getIdOrCreateWeak(t);
            }
        } finally {
            mutex.unlock();
        }
        ids = Arrays.copyOf(ids, i);
        if (LOGGER.isLoggable()) {
            LOGGER.log("getAllThreadIds(): " + Arrays.toString(ids));
        }
        return ids;
    }

    @Override
    public Packet VirtualMachine_AllClassesWithGeneric(Packet packet) throws JDWPException {
        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer data = reply.dataWriter();

        ResolvedJavaType[] allClasses = allReferenceTypes();

        data.writeInt(allClasses.length);
        for (ResolvedJavaType type : allClasses) {
            data.writeByte(TypeTag.getKind(type));
            data.writeLong(JDWPBridgeImpl.getIds().getIdOrCreateWeak(type));
            data.writeString(ClassUtils.getTypeAsString(type));
            data.writeString(ClassUtils.getGenericTypeAsString(type));
            data.writeInt(ClassUtils.getStatus(type));
        }

        return reply;
    }

    @Override
    public Packet VirtualMachine_Dispose(Packet packet) throws JDWPException {
        JDWPBridgeImpl.getIds().reset();
        return WritablePacket.newReplyTo(packet);
    }

    @Override
    public Packet VirtualMachine_DisposeObjects(Packet packet) throws JDWPException {
        Packet.Reader input = packet.newDataReader();
        int numRequests = input.readInt();

        for (int i = 0; i < numRequests; i++) {
            long objectId = input.readLong();
            int refCount = input.readInt();
            JDWPBridgeImpl.getIds().enableCollection(objectId, refCount, true);
        }

        return WritablePacket.newReplyTo(packet);
    }

    @Override
    public Packet ObjectReference_DisableCollection(Packet packet) throws JDWPException {
        Packet.Reader input = packet.newDataReader();
        long objectId = input.readLong();

        boolean success = JDWPBridgeImpl.getIds().disableCollection(objectId);
        if (!success) {
            throw JDWPException.raise(ErrorCode.INVALID_OBJECT);
        }
        return WritablePacket.newReplyTo(packet);
    }

    @Override
    public Packet ObjectReference_EnableCollection(Packet packet) throws JDWPException {
        Packet.Reader input = packet.newDataReader();
        long objectId = input.readLong();

        boolean success = JDWPBridgeImpl.getIds().enableCollection(objectId);
        if (!success) {
            throw JDWPException.raise(ErrorCode.INVALID_OBJECT);
        }
        return WritablePacket.newReplyTo(packet);
    }

    @Override
    public Packet ObjectReference_IsCollected(Packet packet) throws JDWPException {
        Packet.Reader input = packet.newDataReader();
        long objectId = input.readLong();

        Boolean collected = JDWPBridgeImpl.getIds().isCollected(objectId);
        if (collected == null) {
            throw JDWPException.raise(ErrorCode.INVALID_OBJECT);
        }

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer data = reply.dataWriter();

        data.writeBoolean(collected);

        return reply;
    }

    @Override
    public Packet ThreadReference_Name(Packet packet) throws JDWPException {
        Packet.Reader input = packet.newDataReader();

        Thread thread = readThread(input);

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer data = reply.dataWriter();

        // The thread name.
        data.writeString(thread.getName());

        return reply;
    }

    @Override
    public Packet ThreadReference_ThreadGroup(Packet packet) throws JDWPException {
        Packet.Reader input = packet.newDataReader();

        Thread thread = readThread(input);

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer data = reply.dataWriter();

        // The thread group.
        ThreadGroup threadGroup = thread.getThreadGroup();
        if (threadGroup == null) {
            // Thread has terminated
            throw JDWPException.raise(ErrorCode.INVALID_THREAD);
        }
        long id = JDWPBridgeImpl.getIds().getIdOrCreateWeak(threadGroup);
        data.writeLong(id);

        return reply;
    }

    private static Thread readThread(Packet.Reader reader) throws JDWPException {
        return readTypedObject(reader, Thread.class, ErrorCode.INVALID_THREAD);
    }

    public static Thread getThread(long threadId) {
        Thread thread;
        try {
            thread = JDWPBridgeImpl.getIds().toObject(threadId, Thread.class);
        } catch (ClassCastException e) {
            throw JDWPException.raise(ErrorCode.INVALID_THREAD);
        }
        if (thread == null) {
            if (threadId == 0) {
                // A null thread is invalid
                throw JDWPException.raise(ErrorCode.INVALID_THREAD);
            } else {
                // Unknown ID
                throw JDWPException.raise(ErrorCode.INVALID_OBJECT);
            }
        }
        return thread;
    }

    @Override
    public Packet ThreadGroupReference_Name(Packet packet) throws JDWPException {
        Packet.Reader input = packet.newDataReader();

        ThreadGroup threadGroup = readThreadGroup(input);

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer data = reply.dataWriter();

        // The thread group name.
        data.writeString(threadGroup.getName());

        return reply;
    }

    @Override
    public Packet ThreadGroupReference_Children(Packet packet) throws JDWPException {
        Packet.Reader input = packet.newDataReader();

        ThreadGroup threadGroup = readThreadGroup(input);
        long[] threadIds = new long[10];
        long[] threadGroupIds = new long[10];
        int ti = 0;
        int tgi = 0;
        VMMutex mutex = lockThreads();
        try {
            // Find child threads and child groups:
            for (IsolateThread thread = VMThreads.firstThreadUnsafe(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
                Thread t = ThreadStartDeathSupport.get().filterAppThread(thread);
                if (t == null) {
                    continue;
                }
                ThreadGroup tg = t.getThreadGroup();
                if (tg == threadGroup) {
                    // A direct child thread
                    if (ti >= threadIds.length) {
                        threadIds = Arrays.copyOf(threadIds, ti + ti / 2);
                    }
                    threadIds[ti++] = JDWPBridgeImpl.getIds().getIdOrCreateWeak(t);
                }
                if (tg != null && tg.getParent() == threadGroup) {
                    // A direct child thread group
                    long id = JDWPBridgeImpl.getIds().getIdOrCreateWeak(tg);
                    boolean contains = false;
                    for (int i = 0; i < tgi; i++) {
                        if (threadGroupIds[i] == id) {
                            contains = true;
                            break;
                        }
                    }
                    if (!contains) {
                        if (tgi >= threadGroupIds.length) {
                            threadGroupIds = Arrays.copyOf(threadGroupIds, tgi + tgi / 2);
                        }
                        threadGroupIds[tgi++] = id;
                    }
                }
            }
        } finally {
            mutex.unlock();
        }

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer data = reply.dataWriter();

        data.writeInt(ti);
        for (int i = 0; i < ti; i++) {
            data.writeLong(threadIds[i]);
        }
        data.writeInt(tgi);
        for (int i = 0; i < tgi; i++) {
            data.writeLong(threadGroupIds[i]);
        }

        return reply;

    }

    @Override
    public Packet ThreadGroupReference_Parent(Packet packet) throws JDWPException {
        Packet.Reader input = packet.newDataReader();
        ThreadGroup threadGroup = readThreadGroup(input);
        assert input.isEndOfInput();

        ThreadGroup parentGroup = threadGroup.getParent();
        long parentId = JDWPBridgeImpl.getIds().getIdOrCreateWeak(parentGroup);

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer data = reply.dataWriter();
        data.writeLong(parentId);

        return reply;
    }

    @Override
    public Packet VirtualMachine_TopLevelThreadGroups(Packet packet) throws JDWPException {
        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer data = reply.dataWriter();

        long[] threadGroupIds = new long[5];
        int tgi = 0;
        VMMutex mutex = lockThreads();
        try {
            // Find all top thread groups:
            for (IsolateThread thread = VMThreads.firstThreadUnsafe(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
                Thread t = ThreadStartDeathSupport.get().filterAppThread(thread);
                if (t == null) {
                    continue;
                }
                ThreadGroup tg = t.getThreadGroup();
                if (tg != null) {
                    ThreadGroup rootGroup = tg;
                    while ((tg = tg.getParent()) != null) {
                        rootGroup = tg;
                    }
                    long id = JDWPBridgeImpl.getIds().getIdOrCreateWeak(rootGroup);
                    boolean contains = false;
                    for (int i = 0; i < tgi; i++) {
                        if (threadGroupIds[i] == id) {
                            contains = true;
                            break;
                        }
                    }
                    if (!contains) {
                        // A new top-level group
                        if (tgi >= threadGroupIds.length) {
                            threadGroupIds = Arrays.copyOf(threadGroupIds, tgi + tgi / 2);
                        }
                        threadGroupIds[tgi++] = id;
                    }
                }
            }
        } finally {
            mutex.unlock();
        }

        data.writeInt(tgi);
        for (int i = 0; i < tgi; i++) {
            data.writeLong(threadGroupIds[i]);
        }

        return reply;
    }

    @Override
    public Packet VirtualMachine_AllClasses(Packet packet) throws JDWPException {
        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer data = reply.dataWriter();

        ResolvedJavaType[] allClasses = allReferenceTypes();

        data.writeInt(allClasses.length);
        for (ResolvedJavaType type : allClasses) {
            data.writeByte(TypeTag.getKind(type));
            data.writeLong(JDWPBridgeImpl.getIds().getIdOrCreateWeak(type));
            data.writeString(ClassUtils.getTypeAsString(type));
            data.writeInt(ClassUtils.getStatus(type));
        }

        return reply;
    }

    private static ResolvedJavaType[] allReferenceTypes() {
        InterpreterUniverse universe = DebuggerSupport.singleton().getUniverse();
        return universe.getTypes()
                        .stream()
                        // AllClasses cannot return primitive types.
                        .filter(type -> !type.isPrimitive())
                        .toArray(ResolvedJavaType[]::new);
    }

    @Override
    public Packet ObjectReference_ReferenceType(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        Object object = readReferenceOrNull(reader);
        assert reader.isEndOfInput();
        if (object == null) {
            throw JDWPException.raise(ErrorCode.INVALID_OBJECT);
        }
        Class<?> c = object.getClass();
        InterpreterUniverse universe = DebuggerSupport.singleton().getUniverse();
        ResolvedJavaType type = universe.lookupType(c);
        long typeId = JDWPBridgeImpl.getIds().getIdOrCreateWeak(type);
        byte typeKind = TypeTag.getKind(type);

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer data = reply.dataWriter();

        data.writeByte(typeKind);
        data.writeLong(typeId);

        return reply;
    }

    @Override
    public Packet VirtualMachine_CreateString(Packet packet) throws JDWPException {
        assert packet.commandSet() == JDWP.VirtualMachine;
        assert packet.command() == JDWP.VirtualMachine_CreateString;

        Packet.Reader reader = packet.newDataReader();
        String str = reader.readString();
        assert reader.isEndOfInput();
        long stringId = JDWPBridgeImpl.getIds().getIdOrCreateWeak(str);
        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();
        writer.writeLong(stringId);
        return reply;
    }

    @Override
    public Packet StringReference_Value(Packet packet) throws JDWPException {
        assert packet.commandSet() == JDWP.StringReference;
        assert packet.command() == JDWP.StringReference_Value;

        Packet.Reader reader = packet.newDataReader();
        String string = readStringObject(reader);
        assert reader.isEndOfInput();
        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();
        writer.writeString(string);
        return reply;
    }

    @Override
    public Packet ReferenceType_ClassObject(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        InterpreterResolvedJavaType type = readType(reader);
        assert reader.isEndOfInput();

        Class<?> javaClass = type.getJavaClass();
        long classId = JDWPBridgeImpl.getIds().getIdOrCreateWeak(javaClass);

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();
        writer.writeLong(classId);

        return reply;
    }

    @Override
    public Packet ClassObjectReference_ReflectedType(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        Class<?> classObject = readClassObject(reader);
        assert reader.isEndOfInput();

        ResolvedJavaType type = DebuggerSupport.lookupType(classObject);
        assert type != null;
        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();
        long typeId = symbolicRefs.toTypeRef(type);
        writer.writeByte(TypeTag.getKind(type));
        writer.writeLong(typeId);
        return reply;
    }

    @Override
    public Packet ReferenceType_ClassLoader(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        InterpreterResolvedJavaType type = readType(reader);
        assert reader.isEndOfInput();

        Class<?> javaClass = type.getJavaClass();
        ClassLoader classLoader = javaClass.getClassLoader();
        long classLoaderId = JDWPBridgeImpl.getIds().getIdOrCreateWeak(classLoader);

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();
        writer.writeLong(classLoaderId);

        return reply;
    }

    @Override
    public Packet ReferenceType_Module(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        InterpreterResolvedJavaType type = readType(reader);
        assert reader.isEndOfInput();

        Class<?> javaClass = type.getJavaClass();
        Module module = javaClass.getModule();
        long moduleId = JDWPBridgeImpl.getIds().getIdOrCreateWeak(module);

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();
        writer.writeLong(moduleId);

        return reply;
    }

    @Override
    public Packet ModuleReference_Name(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        Module module = readModule(reader);
        assert reader.isEndOfInput();

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();

        String moduleName = module.getName();
        // From HotSpot: The JDWP converts null into an empty string.
        if (moduleName == null) {
            moduleName = "";
        }
        writer.writeString(moduleName);
        return reply;
    }

    /**
     * This method return all the modules in the native image. Note that
     * {@code ModuleLayer.boot().modules()} does not include unnamed modules.
     */
    private static Module[] allModules() {
        Set<Module> allModules = new HashSet<>(ModuleLayer.boot().modules());
        InterpreterUniverse universe = DebuggerSupport.singleton().getUniverse();
        for (ResolvedJavaType type : universe.getTypes()) {
            if (type instanceof InterpreterResolvedObjectType objectType) {
                Class<?> javaClass = objectType.getJavaClass();
                if (javaClass != null) {
                    Module module = javaClass.getModule();
                    if (module != null) {
                        allModules.add(module);
                    }
                }
            }
        }
        return allModules.toArray(Module[]::new);
    }

    @Override
    public Packet VirtualMachine_AllModules(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        assert reader.isEndOfInput();

        Module[] allModules = allModules();

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();

        writer.writeInt(allModules.length);
        for (Module module : allModules) {
            long moduleId = JDWPBridgeImpl.getIds().getIdOrCreateWeak(module);
            writer.writeLong(moduleId);
        }

        return reply;
    }

    @Override
    public Packet ModuleReference_ClassLoader(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        Module module = readModule(reader);
        assert reader.isEndOfInput();

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();
        ClassLoader classLoader = module.getClassLoader();
        long classLoaderId = JDWPBridgeImpl.getIds().getIdOrCreateWeak(classLoader);
        writer.writeLong(classLoaderId);
        return reply;
    }

    @Override
    public Packet ReferenceType_NestedTypes(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        InterpreterResolvedJavaType type = readType(reader);
        assert reader.isEndOfInput();

        Class<?> javaClass = type.getJavaClass();
        Class<?>[] declaredClasses = javaClass.getDeclaredClasses();

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();
        writer.writeInt(declaredClasses.length);
        for (Class<?> declaredClass : declaredClasses) {
            ResolvedJavaType declaredType = DebuggerSupport.lookupType(declaredClass);
            long declaredTypeId = JDWPBridgeImpl.getIds().getIdOrCreateWeak(declaredType);
            writer.writeLong(declaredTypeId);
        }

        return reply;
    }

    @Override
    public Packet ThreadReference_IsVirtual(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        Thread thread = readThread(reader);
        assert reader.isEndOfInput();

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();
        writer.writeBoolean(thread.isVirtual());
        return reply;
    }

    @Override
    public Packet ArrayReference_Length(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        Object array = readArray(reader);
        assert array.getClass().isArray();
        assert reader.isEndOfInput();
        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();
        int arrayLength = InterpreterToVM.arrayLength(array);
        writer.writeInt(arrayLength);
        return reply;
    }

    private static void validateArrayRegion(int firstIndex, int length, int arrayLength) {
        if (firstIndex < 0 || firstIndex >= arrayLength) {
            throw JDWPException.raise(ErrorCode.INVALID_INDEX);
        }
        if (length < 0 || firstIndex > arrayLength - length) {
            throw JDWPException.raise(ErrorCode.INVALID_LENGTH);
        }
    }

    @Override
    public Packet ArrayReference_GetValues(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        Object array = readArray(reader);

        int firstIndex = reader.readInt();
        int length = reader.readInt();

        assert reader.isEndOfInput();

        int arrayLength = InterpreterToVM.arrayLength(array);
        if (length == -1) {
            // Read all remaining values.
            // Not in the JDWP spec, but included in HotSpot's JDWP implementation.
            length = arrayLength - firstIndex;
        }
        validateArrayRegion(firstIndex, length, arrayLength);

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();

        Class<?> componentType = array.getClass().getComponentType();
        boolean isWordTypeComponent = WordBase.class.isAssignableFrom(componentType);

        byte componentStorageTag;
        if (isWordTypeComponent) {
            componentStorageTag = switch (InterpreterToVM.wordJavaKind()) {
                case Int -> TagConstants.INT;
                case Long -> TagConstants.LONG;
                default ->
                    throw VMError.shouldNotReachHere("Unexpected word kind " + InterpreterToVM.wordJavaKind());
            };
        } else {
            componentStorageTag = TagConstants.getTagFromClass(componentType);
        }

        // The first byte is a signature byte which is used to identify the type.
        writer.writeByte(componentStorageTag);

        // Next is a four-byte integer indicating the number of values in the sequence.
        writer.writeInt(length);

        assert firstIndex >= 0;
        assert firstIndex < arrayLength;
        assert firstIndex <= arrayLength - length;

        // This is followed by the values themselves.
        if (isWordTypeComponent) {
            for (int i = firstIndex; i - firstIndex < length; ++i) {
                WordBase value = InterpreterToVM.getArrayWord(i, (WordBase[]) array);
                switch (InterpreterToVM.wordJavaKind()) {
                    case Int -> writer.writeInt((int) value.rawValue());
                    case Long -> writer.writeLong(value.rawValue());
                    default ->
                        throw VMError.shouldNotReachHere("Unexpected word kind " + InterpreterToVM.wordJavaKind());
                }
            }
        } else if (componentType.isPrimitive()) {
            // Primitive values are encoded as a sequence of untagged-values.
            for (int i = firstIndex; i - firstIndex < length; ++i) {
                switch (componentStorageTag) {
                    case TagConstants.INT -> {
                        int value = InterpreterToVM.getArrayInt(i, (int[]) array);
                        writer.writeInt(value);
                    }
                    case TagConstants.FLOAT -> {
                        float value = InterpreterToVM.getArrayFloat(i, (float[]) array);
                        writer.writeFloat(value);
                    }
                    case TagConstants.DOUBLE -> {
                        double value = InterpreterToVM.getArrayDouble(i, (double[]) array);
                        writer.writeDouble(value);
                    }
                    case TagConstants.LONG -> {
                        long value = InterpreterToVM.getArrayLong(i, (long[]) array);
                        writer.writeLong(value);
                    }
                    case TagConstants.BYTE -> {
                        byte value = InterpreterToVM.getArrayByte(i, array);
                        writer.writeByte(value);
                    }
                    case TagConstants.SHORT -> {
                        short value = InterpreterToVM.getArrayShort(i, (short[]) array);
                        writer.writeShort(value);
                    }
                    case TagConstants.CHAR -> {
                        char value = InterpreterToVM.getArrayChar(i, (char[]) array);
                        writer.writeChar(value);
                    }
                    case TagConstants.BOOLEAN -> {
                        byte value = InterpreterToVM.getArrayByte(i, array);
                        writer.writeBoolean(value != 0);
                    }
                    default -> throw VMError.shouldNotReachHere("Illegal primitive component tag: " + componentStorageTag);
                }
            }
        } else {
            // Object values are encoded as a sequence of values.
            for (int i = firstIndex; i - firstIndex < length; ++i) {
                Object value = InterpreterToVM.getArrayObject(i, (Object[]) array);
                writeTaggedObject(writer, value);
            }
        }

        return reply;
    }

    @Override
    public Packet ArrayReference_SetValues(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        Object array = readArray(reader);

        int firstIndex = reader.readInt();
        int length = reader.readInt();
        int arrayLength = InterpreterToVM.arrayLength(array);
        validateArrayRegion(firstIndex, length, arrayLength);

        Class<?> componentType = array.getClass().getComponentType();
        byte componentStorageTag;
        boolean isWordTypeComponent = WordBase.class.isAssignableFrom(componentType);
        if (isWordTypeComponent) {
            componentStorageTag = switch (InterpreterToVM.wordJavaKind()) {
                case Int -> TagConstants.INT;
                case Long -> TagConstants.LONG;
                default ->
                    throw VMError.shouldNotReachHere("Unexpected word kind " + InterpreterToVM.wordJavaKind());
            };
        } else {
            componentStorageTag = TagConstants.getTagFromClass(componentType);
        }

        assert firstIndex >= 0;
        assert firstIndex < arrayLength;
        assert firstIndex <= arrayLength - length;

        // This is followed by the values themselves.
        if (isWordTypeComponent) {
            for (int i = firstIndex; i - firstIndex < length; ++i) {
                switch (InterpreterToVM.wordJavaKind()) {
                    case Int -> {
                        WordBase value = Word.signed(reader.readInt());
                        InterpreterToVM.setArrayWord(value, i, (WordBase[]) array);
                    }
                    case Long -> {
                        WordBase value = Word.signed(reader.readLong());
                        InterpreterToVM.setArrayWord(value, i, (WordBase[]) array);
                    }
                    default ->
                        throw VMError.shouldNotReachHere("Unexpected word kind " + InterpreterToVM.wordJavaKind());
                }
            }
        } else if (componentType.isPrimitive()) {
            // For primitive values, each value's type must match the array component type exactly.
            // Primitive values are encoded as a sequence of untagged-values.
            for (int i = firstIndex; i - firstIndex < length; ++i) {
                switch (componentStorageTag) {
                    case TagConstants.INT -> {
                        int value = reader.readInt();
                        InterpreterToVM.setArrayInt(value, i, (int[]) array);
                    }
                    case TagConstants.FLOAT -> {
                        float value = reader.readFloat();
                        InterpreterToVM.setArrayFloat(value, i, (float[]) array);
                    }
                    case TagConstants.DOUBLE -> {
                        double value = reader.readDouble();
                        InterpreterToVM.setArrayDouble(value, i, (double[]) array);
                    }
                    case TagConstants.LONG -> {
                        long value = reader.readLong();
                        InterpreterToVM.setArrayLong(value, i, (long[]) array);
                    }
                    case TagConstants.BYTE -> {
                        byte value = (byte) reader.readByte();
                        InterpreterToVM.setArrayByte(value, i, array);
                    }
                    case TagConstants.SHORT -> {
                        short value = reader.readShort();
                        InterpreterToVM.setArrayShort(value, i, (short[]) array);
                    }
                    case TagConstants.CHAR -> {
                        char value = reader.readChar();
                        InterpreterToVM.setArrayChar(value, i, (char[]) array);
                    }
                    case TagConstants.BOOLEAN -> {
                        boolean value = reader.readBoolean();
                        InterpreterToVM.setArrayByte(value ? (byte) 1 : (byte) 0, i, array);
                    }
                    default -> throw VMError.shouldNotReachHere("Illegal primitive component tag: " + componentStorageTag);
                }
            }
        } else {
            // For object values, there must be a widening reference conversion from the value's
            // type to the array component type and the array component type must be loaded.
            // Object values are encoded as a sequence of untagged-values.
            for (int i = firstIndex; i - firstIndex < length; ++i) {
                Object value = readReferenceOrNull(reader);
                if (value != null && !componentType.isInstance(value)) {
                    throw JDWPException.raise(ErrorCode.TYPE_MISMATCH);
                }
                InterpreterToVM.setArrayObject(value, i, (Object[]) array);
            }
        }

        assert reader.isEndOfInput();

        return WritablePacket.newReplyTo(packet); // empty reply
    }

    @Override
    public Packet ArrayType_NewInstance(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        ResolvedJavaType arrayType = readType(reader);
        if (!arrayType.isArray()) {
            throw JDWPException.raise(ErrorCode.TYPE_MISMATCH);
        }

        int length = reader.readInt();
        if (length < 0) {
            throw JDWPException.raise(ErrorCode.INVALID_LENGTH);
        }
        assert reader.isEndOfInput();

        ResolvedJavaType componentType = arrayType.getComponentType();
        Object array;
        try {
            if (componentType.isPrimitive()) {
                assert componentType.getJavaKind() != JavaKind.Void;
                array = InterpreterToVM.createNewPrimitiveArray((byte) componentType.getJavaKind().getBasicType(), length);
            } else {
                array = InterpreterToVM.createNewReferenceArray((InterpreterResolvedJavaType) componentType, length);
            }
        } catch (OutOfMemoryError e) {
            throw JDWPException.raise(ErrorCode.OUT_OF_MEMORY);
        }

        assert array != null;
        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();
        writeTaggedObject(writer, array);
        return reply;
    }

    private static StackframeDescriptor getStackframeDescriptor(Thread thread, int frameDepth) {
        assert !thread.isVirtual();
        GetStackframeDescriptorAtDepthVisitor visitor = new GetStackframeDescriptorAtDepthVisitor(false, frameDepth);
        SafeStackWalker.safeStackWalk(thread, visitor);
        return visitor;
    }

    public static Object getThis(Thread thread, int frameDepth) {
        StackframeDescriptor stackframeDescriptor = getStackframeDescriptor(thread, frameDepth);
        FrameSourceInfo frameSourceInfo = stackframeDescriptor.getFrameSourceInfo();
        require(frameSourceInfo != null, ErrorCode.INVALID_FRAMEID,
                        "Frame depth %s not found for thread.threadId()=%s", frameDepth, thread.threadId());

        /*
         * Return null for static or native methods; otherwise, the JVM spec guarantees that "this"
         * is in slot 0.
         */
        Object thisObject;
        if (frameSourceInfo instanceof InterpreterFrameSourceInfo interpreterJavaFrameInfo) {
            ResolvedJavaMethod method = interpreterJavaFrameInfo.getInterpretedMethod();
            if (method.isStatic() || method.isNative()) {
                thisObject = null;
            } else {
                InterpreterFrame interpreterFrame = (InterpreterFrame) interpreterJavaFrameInfo.getInterpreterFrame();
                thisObject = EspressoFrame.getThis(interpreterFrame);
            }
        } else {
            FrameInfoQueryResult frameInfoQueryResult = (FrameInfoQueryResult) frameSourceInfo;

            if (frameInfoQueryResult.isNativeMethod()) {
                thisObject = null;
            } else {
                InterpreterUniverse universe = DebuggerSupport.singleton().getUniverse();
                ResolvedJavaType type = universe.lookupType(frameInfoQueryResult.getSourceClass());
                ResolvedJavaMethod method = JDWPBridgeImpl.findSourceMethod(type, frameInfoQueryResult);

                assert !method.isNative();

                if (method.isStatic()) {
                    thisObject = null;
                } else {
                    DeoptState deoptState = new DeoptState(stackframeDescriptor.getStackPointer(), Word.zero());
                    JavaConstant javaConstant = deoptState.readLocalVariable(0, frameInfoQueryResult);
                    thisObject = SubstrateObjectConstant.asObject(javaConstant);
                }
            }
        }
        return thisObject;
    }

    @Override
    public Packet StackFrame_ThisObject(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        Thread thread = readThread(reader);

        // The server ensures that the thread is suspended/parked.
        // A thread may be parked or even in a RUNNABLE state, but still be considered suspended by
        // the debugger.
        assert !thread.isVirtual();
        long frameId = reader.readLong();
        // frameId is validated on the server.
        int frameDepth = FrameId.getFrameDepth(frameId);
        assert reader.isEndOfInput();

        Object thisObject = getThis(thread, frameDepth);

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();
        writeTaggedObject(writer, thisObject);
        return reply;
    }

    @Override
    public Packet StackFrame_GetValues(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        Thread thread = readThread(reader);
        assert !thread.isVirtual();

        long frameId = reader.readLong();
        int frameDepth = FrameId.getFrameDepth(frameId);
        assert frameDepth >= 0;

        int slots = reader.readInt();
        assert slots >= 0;

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();

        StackframeDescriptor stackframeDescriptor = getStackframeDescriptor(thread, frameDepth);
        FrameSourceInfo frameSourceInfo = stackframeDescriptor.getFrameSourceInfo();
        require(frameSourceInfo != null, ErrorCode.INVALID_FRAMEID,
                        "Frame depth %s not found for thread.threadId()=%s", frameDepth, thread.threadId());

        // The number of values retrieved, always equal to slots, the number of values to get.
        writer.writeInt(slots);

        Pointer stackPointer = stackframeDescriptor.getStackPointer();
        for (int i = 0; i < slots; i++) {
            // The local variable's index in the frame.
            int slot = reader.readInt();
            byte tag = JDWP.readTag(reader);

            if (frameSourceInfo instanceof InterpreterFrameSourceInfo interpreterJavaFrameInfo) {
                readLocalFromInterpreterFrame(tag, interpreterJavaFrameInfo, slot, writer);
            } else {
                FrameInfoQueryResult frameInfoQueryResult = (FrameInfoQueryResult) frameSourceInfo;
                readLocalFromCompiledFrame(tag, frameInfoQueryResult, stackPointer, slot, writer);
            }
        }

        assert reader.isEndOfInput();

        return reply;
    }

    private static void sharedReadField(Packet.Writer writer, Object typeOrReceiver, InterpreterResolvedJavaField field) {

        Object receiver;
        JavaKind fieldKind = field.getJavaKind();
        if (field.isStatic()) {
            assert typeOrReceiver instanceof InterpreterResolvedJavaType;
            // typeOrReceiver is ignored, all static fields are grouped together.
            receiver = (fieldKind.isPrimitive() || field.getType().isWordType())
                            ? StaticFieldsSupport.getStaticPrimitiveFieldsAtRuntime(MultiLayeredImageSingleton.UNKNOWN_LAYER_NUMBER)
                            : StaticFieldsSupport.getStaticObjectFieldsAtRuntime(MultiLayeredImageSingleton.UNKNOWN_LAYER_NUMBER);
        } else {
            receiver = typeOrReceiver;
            assert receiver != null;
        }

        if (field.isUndefined()) {
            writer.writeByte(TagConstants.VOID);
            return;
        }

        assert !field.isUndefined() : "Cannot read undefined field " + field;

        if (field.getType().isWordType()) {
            switch (InterpreterToVM.wordJavaKind()) {
                case Int -> {
                    writer.writeByte(TagConstants.INT);
                    writer.writeInt((int) InterpreterToVM.getFieldWord(receiver, field).rawValue());
                }
                case Long -> {
                    writer.writeByte(TagConstants.LONG);
                    writer.writeLong(InterpreterToVM.getFieldWord(receiver, field).rawValue());
                }
            }
            return;
        }

        switch (fieldKind) {
            case Boolean -> {
                writer.writeByte(TagConstants.BOOLEAN);
                writer.writeBoolean(InterpreterToVM.getFieldBoolean(receiver, field));
            }
            case Byte -> {
                writer.writeByte(TagConstants.BYTE);
                writer.writeByte(InterpreterToVM.getFieldByte(receiver, field));
            }
            case Short -> {
                writer.writeByte(TagConstants.SHORT);
                writer.writeShort(InterpreterToVM.getFieldShort(receiver, field));
            }
            case Char -> {
                writer.writeByte(TagConstants.CHAR);
                writer.writeChar(InterpreterToVM.getFieldChar(receiver, field));
            }
            case Int -> {
                writer.writeByte(TagConstants.INT);
                writer.writeInt(InterpreterToVM.getFieldInt(receiver, field));
            }
            case Float -> {
                writer.writeByte(TagConstants.FLOAT);
                writer.writeFloat(InterpreterToVM.getFieldFloat(receiver, field));
            }
            case Long -> {
                writer.writeByte(TagConstants.LONG);
                writer.writeLong(InterpreterToVM.getFieldLong(receiver, field));
            }
            case Double -> {
                writer.writeByte(TagConstants.DOUBLE);
                writer.writeDouble(InterpreterToVM.getFieldDouble(receiver, field));
            }
            case Object -> {
                Object value = InterpreterToVM.getFieldObject(receiver, field);
                writeTaggedObject(writer, value);
            }
            default -> throw JDWPException.raise(ErrorCode.INVALID_FIELDID);
        }
    }

    @Override
    public Packet ObjectReference_GetValues(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        Object object = readReferenceOrNull(reader);
        if (object == null) {
            throw JDWPException.raise(ErrorCode.INVALID_OBJECT);
        }

        int length = reader.readInt();
        assert length >= 0;

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();
        writer.writeInt(length);

        for (int i = 0; i < length; i++) {
            InterpreterResolvedJavaField field = readField(reader);
            if (field.isStatic() || !InterpreterToVM.instanceOf(object, field.getDeclaringClass())) {
                // Field is static or not present in the given object.
                throw JDWPException.raise(ErrorCode.INVALID_FIELDID);
            }
            sharedReadField(writer, object, field);
        }

        assert reader.isEndOfInput();

        return reply;
    }

    @Override
    public Packet ReferenceType_GetValues(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        InterpreterResolvedJavaType type = readType(reader);
        int length = reader.readInt();
        assert length >= 0;

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();
        writer.writeInt(length);

        for (int i = 0; i < length; i++) {
            InterpreterResolvedJavaField field = readField(reader);
            if (!field.isStatic() ||
                            !field.getDeclaringClass().getJavaClass().isAssignableFrom(type.getJavaClass())) {
                // Instance field or field is not included in superclasses, superinterfaces, or
                // implemented interfaces.
                throw JDWPException.raise(ErrorCode.INVALID_FIELDID);
            }
            sharedReadField(writer, type, field);
        }

        assert reader.isEndOfInput();

        return reply;
    }

    @Override
    public Packet ClassLoaderReference_VisibleClasses(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        // The boot class loader (null) is accepted.
        Object object = readReferenceOrNull(reader);
        if (object != null && !(object instanceof ClassLoader)) {
            throw JDWPException.raise(ErrorCode.INVALID_CLASS_LOADER);
        }
        assert reader.isEndOfInput();

        ClassLoader classLoader = (ClassLoader) object;
        List<ResolvedJavaType> visibleTypes = classesWithInitiatingClassLoader(classLoader);
        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();
        writer.writeInt(visibleTypes.size());
        for (ResolvedJavaType type : visibleTypes) {
            writer.writeByte(TypeTag.getKind(type));
            writer.writeLong(JDWPBridgeImpl.getIds().getIdOrCreateWeak(type));
        }

        return reply;
    }

    private static Class<?> getElementalClass(Class<?> clazz) {
        Class<?> elemental = clazz;
        while (elemental.isArray()) {
            elemental = elemental.getComponentType();
        }
        return elemental;
    }

    /**
     * Returns the set of types reachable by the given class loader e.g. all classes for which the
     * given class loader is an "initiating" class loader.
     */
    private static List<ResolvedJavaType> classesWithInitiatingClassLoader(ClassLoader classLoader) {
        List<ResolvedJavaType> visibleTypes = new ArrayList<>();
        // Traverse all types in the universe and check if the class is reachable
        // by name, without resolution; this can be slow.
        InterpreterUniverse universe = DebuggerSupport.singleton().getUniverse();
        for (ResolvedJavaType type : universe.getTypes()) {
            if (type.isPrimitive()) {
                continue;
            }
            Class<?> javaClass = ((InterpreterResolvedJavaType) type).getJavaClass();
            // On SVM, some classes may not be loaded yet, the Class instance is present but
            // doesn't have a class loader associated with it yet.
            if (!DynamicHub.fromClass(javaClass).isLoaded()) {
                continue;
            }
            // Do not include hidden classes or interfaces or array classes whose element type is a
            // hidden class or interface as they cannot be discovered by any class loader.
            if (getElementalClass(javaClass).isHidden()) {
                continue;
            }

            ClassLoader bootLoader = null;
            ClassLoader platformLoader = ClassLoader.getPlatformClassLoader();
            ClassLoader appLoader = ClassLoader.getSystemClassLoader();

            ClassLoader loader = javaClass.getClassLoader();

            // TODO(peterssen): GR-55067 SVM's ClassLoader#findLoadedClass doesn't respect
            // class-loader hierarchy.
            // SVM's ClassLoader#findLoadedClass is out-of spec e.g. the platform
            // class loader can "find" classes defined by the application class loader.
            // This is an attempt to fix the issue for known class loaders.
            // These checks enforce the following class loader delegation hierarchy:
            // System/application -> Platform -> Boot (null).
            boolean isClassVisible = false;
            if (classLoader == bootLoader) {
                isClassVisible = (loader == bootLoader);
            } else if (classLoader == platformLoader) {
                isClassVisible = (loader == bootLoader || loader == platformLoader);
            } else if (classLoader == appLoader) {
                isClassVisible = (loader == bootLoader || loader == platformLoader || loader == appLoader);
            } else {
                // SVM equivalent to ClassLoader#findLoadedClass.
                Class<?> forNameClass = ClassForNameSupport.forNameOrNull(type.toClassName(), classLoader);
                if (javaClass == forNameClass) {
                    isClassVisible = true;
                }
            }

            if (isClassVisible) {
                visibleTypes.add(type);
            }
        }

        return visibleTypes;
    }

    private static void readLocalFromCompiledFrame(byte tag, FrameInfoQueryResult frame, Pointer stackPointer, int slot, Packet.Writer writer) throws JDWPException {
        if (!(slot >= 0 && slot <= frame.getNumLocals())) {
            throw JDWPException.raise(ErrorCode.INVALID_SLOT);
        }

        if (frame.getValueInfos() == null) {
            /* missing locals info for this method */
            // ABSENT_INFORMATION not expected for this command.
            // IDE's should deal with the error code, reporting that the information is missing
            // is better than reporting nothing at all.
            throw JDWPException.raise(ErrorCode.ABSENT_INFORMATION);
        }

        IsolateThread targetThread = Word.zero();
        DeoptState deoptState = new DeoptState(stackPointer, targetThread);
        JavaConstant javaConstant = deoptState.readLocalVariable(slot, frame);

        if (javaConstant.getJavaKind().equals(JavaKind.Illegal)) {
            /* either value was not encoded or it is unknown */
            // ABSENT_INFORMATION not expected for this command.
            // IDE's should deal with the error code, reporting that the information is missing
            // is better than reporting nothing at all.
            throw JDWPException.raise(ErrorCode.ABSENT_INFORMATION);
        } else {
            switch (tag) {
                case TagConstants.BYTE -> {
                    expectKind(javaConstant, JavaKind.Byte.getStackKind());
                    writer.writeByte(TagConstants.BYTE);
                    writer.writeByte((byte) javaConstant.asInt());
                }
                case TagConstants.BOOLEAN -> {
                    expectKind(javaConstant, JavaKind.Boolean.getStackKind());
                    writer.writeByte(TagConstants.BOOLEAN);
                    writer.writeBoolean(javaConstant.asInt() != 0);
                }
                case TagConstants.SHORT -> {
                    expectKind(javaConstant, JavaKind.Short.getStackKind());
                    writer.writeByte(TagConstants.SHORT);
                    writer.writeShort((short) javaConstant.asInt());
                }
                case TagConstants.CHAR -> {
                    expectKind(javaConstant, JavaKind.Char.getStackKind());
                    writer.writeByte(TagConstants.CHAR);
                    writer.writeChar((char) javaConstant.asInt());
                }
                case TagConstants.INT -> {
                    expectKind(javaConstant, JavaKind.Int);
                    writer.writeByte(TagConstants.INT);
                    writer.writeInt(javaConstant.asInt());
                }
                case TagConstants.LONG -> {
                    expectKind(javaConstant, JavaKind.Long);
                    writer.writeByte(TagConstants.LONG);
                    writer.writeLong(javaConstant.asLong());
                }
                case TagConstants.FLOAT -> {
                    expectKind(javaConstant, JavaKind.Float);
                    writer.writeByte(TagConstants.FLOAT);
                    writer.writeFloat(javaConstant.asFloat());
                }
                case TagConstants.DOUBLE -> {
                    expectKind(javaConstant, JavaKind.Double);
                    writer.writeByte(TagConstants.DOUBLE);
                    writer.writeDouble(javaConstant.asDouble());
                }
                case TagConstants.VOID -> {
                    // Should this be unreachable instead?
                    writer.writeByte(TagConstants.VOID);
                }
                default -> {
                    expectKind(javaConstant, JavaKind.Object);
                    Object value = SubstrateObjectConstant.asObject(javaConstant);
                    byte valueTag = TagConstants.getTagFromReference(value);
                    // Value tag overrides provided tag.
                    writer.writeByte(valueTag);
                    writer.writeLong(JDWPBridgeImpl.getIds().getIdOrCreateWeak(value));
                }
            }
        }
    }

    private static void expectKind(JavaConstant jc, JavaKind expectedKind) {
        JavaKind givenKind = jc.getJavaKind();
        if (givenKind != expectedKind) {
            throw JDWPException.raise(ErrorCode.INVALID_TAG);
        }
    }

    private static void readLocalFromInterpreterFrame(byte tag, InterpreterFrameSourceInfo interpreterJavaFrameInfo, int slot, Packet.Writer writer) throws JDWPException {
        LocalVariableTable localVariableTable = interpreterJavaFrameInfo.getInterpretedMethod().getLocalVariableTable();
        /*
         * Even if local variable information is not available, values can be retrieved if the
         * front-end is able to determine the correct local variable index. Typically, this index
         * can be determined for method arguments from the method signature without access to the
         * local variable table information.
         */
        if (localVariableTable != null) {
            Local local = localVariableTable.getLocal(slot, interpreterJavaFrameInfo.getBci());
            if (local == null) {
                throw JDWPException.raise(ErrorCode.INVALID_SLOT);
            }
            JavaKind localKind = local.getType().getJavaKind();
            JavaKind tagKind = TagConstants.tagToKind(tag);
            if (localKind != tagKind) {
                throw JDWPException.raise(ErrorCode.INVALID_TAG);
            }
        }
        InterpreterFrame interpreterFrame = (InterpreterFrame) interpreterJavaFrameInfo.getInterpreterFrame();
        switch (tag) {
            case TagConstants.BYTE -> {
                int value = EspressoFrame.getLocalInt(interpreterFrame, slot);
                writer.writeByte(TagConstants.BYTE);
                writer.writeByte((byte) value);
            }
            case TagConstants.BOOLEAN -> {
                int value = EspressoFrame.getLocalInt(interpreterFrame, slot);
                writer.writeByte(TagConstants.BOOLEAN);
                writer.writeBoolean(value != 0);
            }
            case TagConstants.SHORT -> {
                int value = EspressoFrame.getLocalInt(interpreterFrame, slot);
                writer.writeByte(TagConstants.SHORT);
                writer.writeShort((short) value);
            }
            case TagConstants.CHAR -> {
                int value = EspressoFrame.getLocalInt(interpreterFrame, slot);
                writer.writeByte(TagConstants.CHAR);
                writer.writeChar((char) value);
            }
            case TagConstants.INT -> {
                int value = EspressoFrame.getLocalInt(interpreterFrame, slot);
                writer.writeByte(TagConstants.INT);
                writer.writeInt(value);
            }
            case TagConstants.LONG -> {
                long value = EspressoFrame.getLocalLong(interpreterFrame, slot);
                writer.writeByte(TagConstants.LONG);
                writer.writeLong(value);
            }
            case TagConstants.FLOAT -> {
                float value = EspressoFrame.getLocalFloat(interpreterFrame, slot);
                writer.writeByte(TagConstants.FLOAT);
                writer.writeFloat(value);
            }
            case TagConstants.DOUBLE -> {
                double value = EspressoFrame.getLocalDouble(interpreterFrame, slot);
                writer.writeByte(TagConstants.DOUBLE);
                writer.writeDouble(value);
            }
            case TagConstants.VOID -> {
                writer.writeByte(TagConstants.VOID);
                // Write nothing here.
            }
            default -> {
                Object value = EspressoFrame.getLocalObject(interpreterFrame, slot);
                writeTaggedObject(writer, value);
            }
        }
    }

    @Override
    public Packet ClassType_SetValues(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        InterpreterResolvedJavaType type = readType(reader);

        if (!DynamicHub.fromClass(type.getJavaClass()).isLoaded()) {
            throw JDWPException.raise(ErrorCode.CLASS_NOT_PREPARED);
        }

        int fieldCount = reader.readInt();
        assert fieldCount >= 0;
        for (int i = 0; i < fieldCount; i++) {
            InterpreterResolvedJavaField field = readField(reader);
            InterpreterResolvedJavaType fieldType = field.getType();
            if (!field.isStatic()) {
                throw JDWPException.raise(ErrorCode.ILLEGAL_ARGUMENT);
            }
            if (field.isUndefined() || fieldType.isWordType() || field.isUnmaterializedConstant()) {
                throw JDWPException.raise(ErrorCode.ILLEGAL_ARGUMENT);
            }
            sharedWriteField(reader, type, field);
        }

        assert reader.isEndOfInput();

        // Empty response.
        return WritablePacket.newReplyTo(packet);
    }

    @Override
    public Packet ObjectReference_SetValues(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        Object receiver = readReferenceOrNull(reader);
        if (receiver == null) {
            throw JDWPException.raise(ErrorCode.ILLEGAL_ARGUMENT);
        }
        int fieldCount = reader.readInt();
        assert fieldCount >= 0;
        for (int i = 0; i < fieldCount; i++) {
            InterpreterResolvedJavaField field = readField(reader);
            InterpreterResolvedJavaType fieldType = field.getType();
            if (field.isStatic()) {
                throw JDWPException.raise(ErrorCode.ILLEGAL_ARGUMENT);
            }
            if (field.isUndefined() || fieldType.isWordType() || field.isUnmaterializedConstant()) {
                throw JDWPException.raise(ErrorCode.ILLEGAL_ARGUMENT);
            }
            sharedWriteField(reader, receiver, field);
        }

        assert reader.isEndOfInput();

        // Empty response.
        return WritablePacket.newReplyTo(packet);
    }

    private static void sharedWriteField(Packet.Reader reader, Object typeOrReceiver, InterpreterResolvedJavaField field) {
        Object receiver;
        JavaKind fieldKind = field.getJavaKind();
        if (field.isStatic()) {
            assert typeOrReceiver instanceof InterpreterResolvedJavaType;
            // typeOrReceiver is ignored, all static fields are grouped together.
            receiver = (fieldKind.isPrimitive() || field.getType().isWordType())
                            ? StaticFieldsSupport.getStaticPrimitiveFieldsAtRuntime(MultiLayeredImageSingleton.UNKNOWN_LAYER_NUMBER)
                            : StaticFieldsSupport.getStaticObjectFieldsAtRuntime(MultiLayeredImageSingleton.UNKNOWN_LAYER_NUMBER);
        } else {
            receiver = typeOrReceiver;
            assert receiver != null;
        }

        if (field.isUndefined() || field.isUnmaterializedConstant()) {
            throw JDWPException.raise(ErrorCode.ILLEGAL_ARGUMENT);
        }

        assert !field.isUndefined() && !field.isUnmaterializedConstant() //
                        : "Cannot write undefined or unmaterialized field " + field;

        if (field.getType().isWordType()) {
            switch (InterpreterToVM.wordJavaKind()) {
                case Int ->
                    InterpreterToVM.setFieldWord(Word.signed(reader.readInt()), receiver, field);
                case Long ->
                    InterpreterToVM.setFieldWord(Word.signed(reader.readLong()), receiver, field);
                default ->
                    throw VMError.shouldNotReachHere("Unexpected word kind " + InterpreterToVM.wordJavaKind());
            }
            return;
        }

        // @formatter:off
        switch (fieldKind) {
            case Boolean -> InterpreterToVM.setFieldBoolean(reader.readBoolean(), receiver, field);
            case Byte    -> InterpreterToVM.setFieldByte((byte) reader.readByte(), receiver, field);
            case Short   -> InterpreterToVM.setFieldShort(reader.readShort(), receiver, field);
            case Char    -> InterpreterToVM.setFieldChar(reader.readChar(), receiver, field);
            case Int     -> InterpreterToVM.setFieldInt(reader.readInt(), receiver, field);
            case Float   -> InterpreterToVM.setFieldFloat(reader.readFloat(), receiver, field);
            case Long    -> InterpreterToVM.setFieldLong(reader.readLong(), receiver, field);
            case Double  -> InterpreterToVM.setFieldDouble(reader.readDouble(), receiver, field);
            case Object  -> {
                assert !field.getType().isWordType() : field; // handled above
                Object value = readReferenceOrNull(reader);
                if (value != null && !field.getType().getJavaClass().isInstance(value)) {
                    throw JDWPException.raise(ErrorCode.TYPE_MISMATCH);
                }
                InterpreterToVM.setFieldObject(value, receiver, field);
            }
            default -> throw JDWPException.raise(ErrorCode.INVALID_FIELDID);
        }
        // @formatter:on
    }

    @Override
    public Packet StackFrame_SetValues(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        Thread thread = readThread(reader);
        assert !thread.isVirtual();

        long frameId = reader.readLong();
        int frameDepth = FrameId.getFrameDepth(frameId);
        assert frameDepth >= 0;

        // The number of values to set.
        int slotValues = reader.readInt();
        assert slotValues >= 0;

        StackframeDescriptor stackframeDescriptor = getStackframeDescriptor(thread, frameDepth);
        FrameSourceInfo frameSourceInfo = stackframeDescriptor.getFrameSourceInfo();
        require(frameSourceInfo != null, ErrorCode.INVALID_FRAMEID,
                        "Frame depth %s not found for thread.threadId()=%s", frameDepth, thread.threadId());

        if (!(frameSourceInfo instanceof InterpreterFrameSourceInfo)) {
            // GR-55013: Add support for writing locals in compiled frames.
            throw JDWPException.raise(ErrorCode.NOT_IMPLEMENTED);
        }

        for (int i = 0; i < slotValues; i++) {
            // The local variable's index in the frame.
            int slot = reader.readInt();
            byte tag = JDWP.readTag(reader);
            InterpreterFrameSourceInfo interpreterJavaFrameInfo = (InterpreterFrameSourceInfo) frameSourceInfo;
            writeLocalToInterpreterFrame(tag, interpreterJavaFrameInfo, slot, reader);
        }

        assert reader.isEndOfInput();

        // Empty response.
        return WritablePacket.newReplyTo(packet);
    }

    private static void writeLocalToInterpreterFrame(byte tag, InterpreterFrameSourceInfo interpreterJavaFrameInfo, int slot, Packet.Reader reader) {
        LocalVariableTable localVariableTable = interpreterJavaFrameInfo.getInterpretedMethod().getLocalVariableTable();
        /*
         * Even if local variable information is not available, values can be retrieved if the
         * front-end is able to determine the correct local variable index. Typically, this index
         * can be determined for method arguments from the method signature without access to the
         * local variable table information.
         */
        if (localVariableTable != null) {
            Local local = localVariableTable.getLocal(slot, interpreterJavaFrameInfo.getBci());
            if (local == null) {
                throw JDWPException.raise(ErrorCode.INVALID_SLOT);
            }
            JavaKind localKind = local.getType().getJavaKind();
            JavaKind tagKind = TagConstants.tagToKind(tag);
            if (localKind != tagKind) {
                throw JDWPException.raise(ErrorCode.INVALID_TAG);
            }
        }
        InterpreterFrame interpreterFrame = (InterpreterFrame) interpreterJavaFrameInfo.getInterpreterFrame();
        // @formatter:off
        switch (tag) {
            case TagConstants.BYTE    -> EspressoFrame.setLocalInt(interpreterFrame, slot, (byte) reader.readByte());
            case TagConstants.BOOLEAN -> EspressoFrame.setLocalInt(interpreterFrame, slot, reader.readBoolean() ? 1 : 0);
            case TagConstants.SHORT   -> EspressoFrame.setLocalInt(interpreterFrame, slot, reader.readShort());
            case TagConstants.CHAR    -> EspressoFrame.setLocalInt(interpreterFrame, slot, reader.readChar());
            case TagConstants.INT     -> EspressoFrame.setLocalInt(interpreterFrame, slot, reader.readInt());
            case TagConstants.LONG    -> EspressoFrame.setLocalLong(interpreterFrame, slot, reader.readLong());
            case TagConstants.FLOAT   -> EspressoFrame.setLocalFloat(interpreterFrame, slot, reader.readFloat());
            case TagConstants.DOUBLE  -> EspressoFrame.setLocalDouble(interpreterFrame, slot, reader.readDouble());
            case TagConstants.VOID -> { } // nothing
            default -> EspressoFrame.setLocalObject(interpreterFrame, slot, readReferenceOrNull(reader));
        }
        // @formatter:on
    }

    private static Object[] readArguments(Packet.Reader reader) {
        int argCount = reader.readInt();
        assert argCount >= 0;

        Object[] args = new Object[argCount];
        for (int i = 0; i < argCount; i++) {
            byte tag = JDWP.readTag(reader);
            switch (tag) {
                case TagConstants.BYTE -> args[i] = (byte) reader.readByte();
                case TagConstants.BOOLEAN -> args[i] = reader.readBoolean();
                case TagConstants.SHORT -> args[i] = reader.readShort();
                case TagConstants.CHAR -> args[i] = reader.readChar();
                case TagConstants.INT -> args[i] = reader.readInt();
                case TagConstants.LONG -> args[i] = reader.readLong();
                case TagConstants.FLOAT -> args[i] = reader.readFloat();
                case TagConstants.DOUBLE -> args[i] = reader.readDouble();
                case TagConstants.VOID -> {
                    // Read nothing.
                }
                default -> args[i] = readReferenceOrNull(reader);
            }
        }

        return args;
    }

    record Result(Object value, Throwable throwable) {

        static Result fromValue(Object value) {
            return new Result(value, null);
        }

        static Result fromThrowable(Throwable throwable) {
            return new Result(null, MetadataUtil.requireNonNull(throwable));
        }

        static Result ofInvoke(boolean isVirtual, InterpreterResolvedJavaMethod method, Object... args) {
            try {
                return fromValue(InterpreterToVM.dispatchInvocation(method, args, isVirtual, false, false, false));
            } catch (SemanticJavaException e) {
                return fromThrowable(e.getCause());
            } catch (StackOverflowError | OutOfMemoryError error) {
                return fromThrowable(error);
            }
        }
    }

    private static void writeTaggedValue(Packet.Writer writer, Object value, JavaKind valueKind) {
        switch (valueKind) {
            case Boolean -> {
                writer.writeByte(TagConstants.BOOLEAN);
                writer.writeBoolean((boolean) value);
            }
            case Byte -> {
                writer.writeByte(TagConstants.BYTE);
                writer.writeByte((byte) value);
            }
            case Short -> {
                writer.writeByte(TagConstants.SHORT);
                writer.writeShort((short) value);
            }
            case Char -> {
                writer.writeByte(TagConstants.CHAR);
                writer.writeChar((char) value);
            }
            case Int -> {
                writer.writeByte(TagConstants.INT);
                writer.writeInt((int) value);
            }
            case Float -> {
                writer.writeByte(TagConstants.FLOAT);
                writer.writeFloat((float) value);
            }
            case Long -> {
                writer.writeByte(TagConstants.LONG);
                writer.writeLong((long) value);
            }
            case Double -> {
                writer.writeByte(TagConstants.DOUBLE);
                writer.writeDouble((double) value);
            }
            case Object -> {
                writeTaggedObject(writer, value);
            }
            case Void -> {
                writer.writeByte(TagConstants.VOID);
                // write nothing
            }
            default ->
                throw VMError.shouldNotReachHere("unexpected kind " + valueKind);
        }
    }

    /**
     * Ensures that a given condition is true, throwing a {@link JDWPException} with the provided
     * {@link ErrorCode error code} otherwise. Before throwing the {@link JDWPException exception},
     * the message is {@link Logger#log(String, Object...) logged}.
     *
     * @param logMessageSimpleFormat a "simple" format string
     * @param args arguments referenced by the "simple" format string
     */
    private static void require(boolean condition, ErrorCode errorCode, String logMessageSimpleFormat, Object... args) throws JDWPException {
        if (!condition) {
            LOGGER.log(logMessageSimpleFormat, args);
            throw JDWPException.raise(errorCode);
        }
    }

    /**
     * Ensures that a given condition is true, throwing a {@link JDWPException} with the provided
     * {@link ErrorCode error code} otherwise. Before throwing the {@link JDWPException exception},
     * the {@link ErrorCode#getMessage() error message} associated with the {@link ErrorCode error
     * code} is {@link Logger#log(String) logged}.
     */
    @SuppressWarnings("unused")
    private static void require(boolean condition, ErrorCode errorCode) throws JDWPException {
        if (!condition) {
            throw JDWPException.raise(errorCode);
        }
    }

    static Packet invokeReply(Packet packet, Result invokeResult, JavaKind returnValueKind) {
        return invokeReply(packet, invokeResult.value(), invokeResult.throwable(), returnValueKind);
    }

    static Packet invokeReply(Packet packet, Object value, Throwable throwable, JavaKind returnValueKind) {
        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();
        /*
         * The JDWP spec states that both, the receiver and the exception must be written. If an
         * exception was thrown, write a 'null' return value.
         * 
         * In case of exception, cannot reply with a return value of type/tag void since the vanilla
         * JDI implementation expects the ClassType.NewInstance command to always return a value of
         * type/tag object, regardless of exceptions and that <init> methods actually returns void.
         */
        if (throwable != null) {
            writeTaggedObject(writer, null);
        } else {
            writeTaggedValue(writer, value, returnValueKind);
        }
        writeTaggedObject(writer, throwable);
        return reply;
    }

    @Override
    public Packet ClassType_InvokeMethod(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        InterpreterResolvedJavaType type = readType(reader);
        Thread thread = readThread(reader);
        InterpreterResolvedJavaMethod method = readMethod(reader);
        Object[] args = readArguments(reader);
        @SuppressWarnings("unused")
        int options = reader.readInt();
        assert reader.isEndOfInput();

        require(thread == Thread.currentThread(), ErrorCode.ILLEGAL_ARGUMENT, "method invocation only supports current/same thread");
        require(method.isStatic(), ErrorCode.ILLEGAL_ARGUMENT, "method must be static %s", method);
        require(type.equals(method.getDeclaringClass()), ErrorCode.ILLEGAL_ARGUMENT, "method declaring type %s and type %s differ", method.getDeclaringClass(), type);
        require(!thread.isVirtual(), ErrorCode.ILLEGAL_ARGUMENT, "virtual threads not supported");
        // InvokeOptions.INVOKE_NONVIRTUAL is ignored.

        return invokeReply(packet, Result.ofInvoke(false, method, args), method.getSignature().getReturnKind());
    }

    @Override
    public Packet InterfaceType_InvokeMethod(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        InterpreterResolvedJavaType type = readType(reader);
        Thread thread = readThread(reader);
        InterpreterResolvedJavaMethod method = readMethod(reader);
        Object[] args = readArguments(reader);
        @SuppressWarnings("unused")
        int options = reader.readInt();
        assert reader.isEndOfInput();

        require(!method.isClassInitializer(), ErrorCode.ILLEGAL_ARGUMENT, "method cannot be a static initializer %s", method);
        require(method.isStatic(), ErrorCode.ILLEGAL_ARGUMENT, "method must be be static %s", method);
        require(type.equals(method.getDeclaringClass()), ErrorCode.ILLEGAL_ARGUMENT, "method declaring type %s and type %s differ", method.getDeclaringClass(), type);
        require(type.isInterface(), ErrorCode.ILLEGAL_ARGUMENT, "type %s is not an interface");
        require(type.equals(method.getDeclaringClass()), ErrorCode.ILLEGAL_ARGUMENT, "method %s is not a member of the interface type %s", method, type);
        require(!thread.isVirtual(), ErrorCode.ILLEGAL_ARGUMENT, "virtual threads not supported");
        // InvokeOptions.INVOKE_NONVIRTUAL is ignored.

        return invokeReply(packet, Result.ofInvoke(false, method, args), method.getSignature().getReturnKind());
    }

    @Override
    public Packet ObjectReference_InvokeMethod(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        Object receiver = readReferenceOrNull(reader);
        Thread thread = readThread(reader);
        @SuppressWarnings("unused")
        InterpreterResolvedJavaType type = readType(reader);
        InterpreterResolvedJavaMethod method = readMethod(reader);
        Object[] argsWithoutReceiver = readArguments(reader);
        int options = reader.readInt();
        assert reader.isEndOfInput();

        require(receiver != null, ErrorCode.ILLEGAL_ARGUMENT, "receiver is null");
        require(!method.isStatic(), ErrorCode.ILLEGAL_ARGUMENT, "method cannot be static %s", method);
        require(method.getDeclaringClass().isAssignableFrom(type), ErrorCode.ILLEGAL_ARGUMENT,
                        "method %s is not declared in type %s nor any of its super types (or super interfaces)", method, type);
        require(method.getDeclaringClass().getJavaClass().isInstance(receiver), ErrorCode.ILLEGAL_ARGUMENT,
                        "method %s is not declared in the receiver type %s nor any of its super types (or super interfaces)", method, receiver.getClass());
        require(!thread.isVirtual(), ErrorCode.ILLEGAL_ARGUMENT, "virtual threads not supported");

        Object[] args = prepend(receiver, argsWithoutReceiver);
        boolean isVirtual = !InvokeOptions.nonVirtual(options);
        return invokeReply(packet, Result.ofInvoke(isVirtual, method, args), method.getSignature().getReturnKind());
    }

    @Override
    public Packet ClassType_NewInstance(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        InterpreterResolvedJavaType type = readType(reader);
        Thread thread = readThread(reader);
        InterpreterResolvedJavaMethod method = readMethod(reader);
        Object[] argsWithoutReceiver = readArguments(reader);
        @SuppressWarnings("unused")
        int options = reader.readInt();
        assert reader.isEndOfInput();

        require(!type.isPrimitive(), ErrorCode.ILLEGAL_ARGUMENT, "invalid primitive type %s", type);
        require(!type.isArray(), ErrorCode.ILLEGAL_ARGUMENT, "invalid array type %s", type);
        require(!type.isAbstract(), ErrorCode.ILLEGAL_ARGUMENT, "invalid abstract type %s", type);
        require(method.isConstructor(), ErrorCode.ILLEGAL_ARGUMENT, "method is not a constructor %s", method);
        require(!method.isStatic(), ErrorCode.ILLEGAL_ARGUMENT, "constructor cannot be static %s", method);
        require(type.equals(method.getDeclaringClass()), ErrorCode.ILLEGAL_ARGUMENT, "constructor %s is not a member of the given type %s", method, type);
        require(!thread.isVirtual(), ErrorCode.ILLEGAL_ARGUMENT, "virtual threads not supported");

        Object instance;
        try {
            instance = InterpreterToVM.createNewReference(type);
            assert instance != null;
        } catch (SemanticJavaException e) {
            return invokeReply(packet, null, e.getCause(), JavaKind.Object);
        }

        Object[] args = prepend(instance, argsWithoutReceiver);
        return invokeReply(packet, instance, Result.ofInvoke(false, method, args).throwable(), JavaKind.Object);
    }

    private static Object[] prepend(Object newFirst, Object[] array) {
        Object[] newArray = new Object[array.length + 1];
        newArray[0] = newFirst;
        System.arraycopy(array, 0, newArray, 1, array.length);
        return newArray;
    }

    @Override
    public Packet dispatch(Packet packet) throws JDWPException {
        try {
            return JDWP.super.dispatch(packet);
        } catch (JDWPException e) {
            ResidentJDWP.LOGGER.log(e, "JDWP exception");
            throw e;
        } catch (Throwable t) {
            ResidentJDWP.LOGGER.log(t, "Internal error");
            throw t;
        }
    }
}
