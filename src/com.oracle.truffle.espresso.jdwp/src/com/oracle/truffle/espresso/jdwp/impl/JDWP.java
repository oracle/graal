/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jdwp.impl;

import com.oracle.truffle.espresso.jdwp.api.ClassStatusConstants;
import com.oracle.truffle.espresso.jdwp.api.FieldRef;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;
import com.oracle.truffle.espresso.jdwp.api.LineNumberTableRef;
import com.oracle.truffle.espresso.jdwp.api.LocalRef;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import static com.oracle.truffle.espresso.jdwp.impl.TagConstants.BOOLEAN;

class JDWP {

    public static final String JAVA_LANG_OBJECT = "Ljava/lang/Object;";

    static class VirtualMachine {
        public static final int ID = 1;

        static class VERSION {
            public static final int ID = 1;

            static JDWPResult createReply(Packet packet, JDWPVirtualMachine vm) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeString(vm.getVmDescription());
                reply.writeInt(1);
                reply.writeInt(6);
                reply.writeString(vm.getVmVersion());
                reply.writeString(vm.getVmName());
                return new JDWPResult(reply);
            }
        }

        static class CLASSES_BY_SIGNATURE {
            public static final int ID = 2;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                // get the requested classes
                PacketStream input = new PacketStream(packet);
                String signature = input.readString();
                String slashName = ClassNameUtils.fromInternalObjectNametoSlashName(signature);
                KlassRef[] loaded = context.findLoadedClass(slashName);

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeInt(loaded.length);
                for (KlassRef klass : loaded) {
                    reply.writeByte(TypeTag.getKind(klass));
                    reply.writeLong(context.getIds().getIdAsLong(klass));
                    reply.writeInt(klass.getStatus());
                }
                return new JDWPResult(reply);
            }
        }

        static class ALL_THREADS {
            public static final int ID = 4;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                Object[] allThreads = context.getAllGuestThreads();
                reply.writeInt(allThreads.length);
                for (Object t : allThreads) {
                    reply.writeLong(context.getIds().getIdAsLong(t));
                }
                return new JDWPResult(reply);
            }
        }

        static class TOP_LEVEL_THREAD_GROUPS {
            public static final int ID = 5;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                Object[] threadGroups = context.getTopLevelThreadGroups();
                reply.writeInt(threadGroups.length);
                for (Object threadGroup : threadGroups) {
                    reply.writeLong(context.getIds().getIdAsLong(threadGroup));
                }
                return new JDWPResult(reply);
            }
        }

        static class DISPOSE {
            public static final int ID = 6;

            static JDWPResult createReply(Packet packet, JDWPDebuggerController controller) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                return new JDWPResult(reply, Collections.singletonList(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        controller.disposeDebugger(true);
                        return null;
                    }
                }));
            }
        }

        static class IDSIZES {
            public static final int ID = 7;

            static JDWPResult createReply(Packet packet, JDWPVirtualMachine vm) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeInt(vm.getSizeOfFieldRef());
                reply.writeInt(vm.getSizeOfMethodRef());
                reply.writeInt(vm.getSizeofObjectRef());
                reply.writeInt(vm.getSizeOfClassRef());
                reply.writeInt(vm.getSizeOfFrameRef());
                return new JDWPResult(reply);
            }
        }

        static class SUSPEND {
            public static final int ID = 8;

            static JDWPResult createReply(Packet packet, JDWPDebuggerController controller) {
                if (JDWPDebuggerController.isDebug(JDWPDebuggerController.Debug.THREAD)) {
                    System.out.println("Suspend all packet");
                }

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                controller.suspendAll();
                return new JDWPResult(reply);
            }
        }

        static class RESUME {
            public static final int ID = 9;

            static JDWPResult createReply(Packet packet, JDWPDebuggerController controller) {
                if (JDWPDebuggerController.isDebug(JDWPDebuggerController.Debug.THREAD)) {
                    System.out.println("Resume all packet");
                }
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                controller.resumeAll(false);
                return new JDWPResult(reply);
            }
        }

        static class CREATE_STRING {
            public static final int ID = 11;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                String utf = input.readString();
                // we must create a new StaticObject instance representing the String

                Object string = context.toGuestString(utf);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeLong(context.getIds().getIdAsLong(string));
                return new JDWPResult(reply);
            }
        }

        static class CAPABILITIES {
            public static final int ID = 12;

            static JDWPResult createReply(Packet packet) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeBoolean(true); // canWatchFieldModification
                reply.writeBoolean(true); // canWatchFieldAccess
                reply.writeBoolean(false); // canGetBytecodes
                reply.writeBoolean(true); // canGetSyntheticAttribute
                reply.writeBoolean(false); // canGetOwnedMonitorInfo
                reply.writeBoolean(false); // canGetCurrentContendedMonitor
                reply.writeBoolean(false); // canGetMonitorInfo
                return new JDWPResult(reply);
            }
        }

        static class CAPABILITIES_NEW {
            public static final int ID = 17;

            static JDWPResult createReply(Packet packet) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                // TODO(Gregersen) - figure out what capabilities we want to expose
                reply.writeBoolean(true); // canWatchFieldModification
                reply.writeBoolean(true); // canWatchFieldAccess
                reply.writeBoolean(false); // canGetBytecodes
                reply.writeBoolean(true); // canGetSyntheticAttribute
                reply.writeBoolean(false); // canGetOwnedMonitorInfo
                reply.writeBoolean(false); // canGetCurrentContendedMonitor
                reply.writeBoolean(false); // canGetMonitorInfo
                reply.writeBoolean(false); // canRedefineClasses
                reply.writeBoolean(false); // canAddMethod
                reply.writeBoolean(false); // canUnrestrictedlyRedefineClasses
                reply.writeBoolean(false); // canPopFrames
                reply.writeBoolean(true); // canUseInstanceFilters
                reply.writeBoolean(false); // canGetSourceDebugExtension
                reply.writeBoolean(true); // canRequestVMDeathEvent
                reply.writeBoolean(false); // canSetDefaultStratum
                reply.writeBoolean(false); // canGetInstanceInfo
                reply.writeBoolean(false); // canRequestMonitorEvents
                reply.writeBoolean(false); // canGetMonitorFrameInfo
                reply.writeBoolean(false); // canUseSourceNameFilters
                reply.writeBoolean(false); // canGetConstantPool
                reply.writeBoolean(false); // canForceEarlyReturn
                reply.writeBoolean(false); // reserved for future
                reply.writeBoolean(false); // reserved for future
                reply.writeBoolean(false); // reserved for future
                reply.writeBoolean(false); // reserved for future
                reply.writeBoolean(false); // reserved for future
                reply.writeBoolean(false); // reserved for future
                reply.writeBoolean(false); // reserved for future
                reply.writeBoolean(false); // reserved for future
                reply.writeBoolean(false); // reserved for future
                reply.writeBoolean(false); // reserved for future
                reply.writeBoolean(false); // reserved for future
                return new JDWPResult(reply);
            }
        }

        static class SET_DEFAULT_STRATUM {
            public static final int ID = 19;

            static JDWPResult createReply(Packet packet) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id).errorCode(JDWPErrorCodes.NOT_IMPLEMENTED);
                return new JDWPResult(reply);
            }
        }

        static class ALL_CLASSES_WITH_GENERIC {
            public static final int ID = 20;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                KlassRef[] allLoadedClasses = context.getAllLoadedClasses();
                reply.writeInt(allLoadedClasses.length);

                for (KlassRef klass : allLoadedClasses) {
                    reply.writeByte(TypeTag.getKind(klass));
                    reply.writeLong(context.getIds().getIdAsLong(klass));
                    reply.writeString(klass.getTypeAsString());
                    reply.writeString(""); // TODO(Gregersen) - generic signature if any
                    reply.writeInt(klass.getStatus());
                }

                return new JDWPResult(reply);
            }
        }

        static class INSTANCE_COUNTS {
            public static final int ID = 21;

            static JDWPResult createReply(Packet packet) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id).errorCode(JDWPErrorCodes.NOT_IMPLEMENTED);
                return new JDWPResult(reply);
            }
        }
    }

    static class ReferenceType {
        public static final int ID = 2;

        static class CLASSLOADER {
            public static final int ID = 2;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long refTypeId = input.readLong();
                KlassRef klass = verifyRefType(refTypeId, reply, context);
                if (klass == null) {
                    return new JDWPResult(reply);
                }

                Object loader = klass.getDefiningClassLoader();
                reply.writeLong(context.getIds().getIdAsLong(loader));
                return new JDWPResult(reply);
            }
        }

        static class GET_VALUES {
            public static final int ID = 6;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long refTypeId = input.readLong();

                if (verifyRefType(refTypeId, reply, context) == null) {
                    return new JDWPResult(reply);
                }

                int fields = input.readInt();
                reply.writeInt(fields);

                for (int i = 0; i < fields; i++) {
                    long fieldId = input.readLong();
                    FieldRef field = verifyFieldRef(fieldId, reply, context);

                    if (field == null) {
                        return new JDWPResult(reply);
                    }

                    byte tag = field.getTagConstant();

                    Object value = context.getStaticFieldValue(field);

                    if (tag == TagConstants.OBJECT) {
                        tag = context.getTag(value);
                    }
                    writeValue(tag, value, reply, true, context);
                }
                return new JDWPResult(reply);
            }
        }

        static class SOURCE_FILE {
            public static final int ID = 7;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long refTypeId = input.readLong();

                KlassRef klass = verifyRefType(refTypeId, reply, context);
                if (klass == null) {
                    return new JDWPResult(reply);
                }

                String sourceFile = null;
                MethodRef[] methods = klass.getDeclaredMethods();
                for (MethodRef method : methods) {
                    // we need only look at one method to find
                    // the source file of the declaring class
                    if (!method.hasSourceFileAttribute()) {
                        reply.errorCode(JDWPErrorCodes.ABSENT_INFORMATION);
                        return new JDWPResult(reply);
                    }
                    sourceFile = method.getSourceFile();
                    break;
                }
                reply.writeString(sourceFile);
                return new JDWPResult(reply);
            }
        }

        static class INTERFACES {
            public static final int ID = 10;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                long refTypeId = input.readLong();

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                KlassRef klass = verifyRefType(refTypeId, reply, context);

                if (klass == null) {
                    return new JDWPResult(reply);
                }

                KlassRef[] interfaces = klass.getImplementedInterfaces();

                reply.writeInt(interfaces.length);
                for (KlassRef itf : interfaces) {
                    reply.writeLong(context.getIds().getIdAsLong(itf));
                }
                return new JDWPResult(reply);
            }
        }

        // list which only purpose is to have hard references to created ClassObjectId objects
        private static final List<ClassObjectId> classObjectIds = new ArrayList<>();

        static class CLASS_OBJECT {
            public static final int ID = 11;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                long refTypeId = input.readLong();

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                KlassRef klass = verifyRefType(refTypeId, reply, context);

                if (klass == null) {
                    return new JDWPResult(reply);
                }

                // wrap this in ClassIdObject
                ClassObjectId id = new ClassObjectId(klass);
                classObjectIds.add(id);
                reply.writeLong(context.getIds().getIdAsLong(id));
                return new JDWPResult(reply);
            }
        }

        static class SIGNATURE_WITH_GENERIC {
            public static final int ID = 13;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                long refTypeId = input.readLong();

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                KlassRef klass = verifyRefType(refTypeId, reply, context);

                if (klass == null) {
                    return new JDWPResult(reply);
                }

                reply.writeString(klass.getTypeAsString());
                reply.writeString(""); // TODO(Gregersen) - generic signature
                return new JDWPResult(reply);
            }
        }

        static class FIELDS_WITH_GENERIC {
            public static final int ID = 14;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long refTypeId = input.readLong();
                KlassRef klass = verifyRefType(refTypeId, reply, context);

                if (klass == null) {
                    return new JDWPResult(reply);
                }

                // check if class has been prepared
                if (klass.getStatus() < ClassStatusConstants.PREPARED) {
                    reply.errorCode(JDWPErrorCodes.CLASS_NOT_PREPARED);
                    return new JDWPResult(reply);
                }

                FieldRef[] declaredFields = klass.getDeclaredFields();
                int numDeclaredFields = declaredFields.length;
                reply.writeInt(numDeclaredFields);
                for (FieldRef field : declaredFields) {
                    reply.writeLong(context.getIds().getIdAsLong(field));
                    reply.writeString(field.getNameAsString());
                    reply.writeString(field.getTypeAsString());
                    reply.writeString(field.getGenericSignatureAsString());
                    reply.writeInt(field.getModifiers());
                }
                return new JDWPResult(reply);
            }
        }

        static class METHODS_WITH_GENERIC {
            public static final int ID = 15;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long refTypeId = input.readLong();
                KlassRef klass = verifyRefType(refTypeId, reply, context);

                if (klass == null) {
                    return new JDWPResult(reply);
                }

                // check if class has been prepared
                if (klass.getStatus() < ClassStatusConstants.PREPARED) {
                    reply.errorCode(JDWPErrorCodes.CLASS_NOT_PREPARED);
                    return new JDWPResult(reply);
                }

                MethodRef[] declaredMethods = klass.getDeclaredMethods();
                int numDeclaredMethods = declaredMethods.length;
                reply.writeInt(numDeclaredMethods);
                for (MethodRef method : declaredMethods) {
                    reply.writeLong(context.getIds().getIdAsLong(method));
                    reply.writeString(method.getNameAsString());
                    reply.writeString(method.getSignatureAsString());
                    reply.writeString(""); // TODO(Gregersen) - get the generic signature
                    reply.writeInt(method.getModifiers());
                }
                return new JDWPResult(reply);
            }
        }

        /*
        static class CONSTANT_POOL {
            public static final int ID = 18;

            static PacketStream createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long refTypeId = input.readLong();
                RefType refType = (RefType) context.getIds().fromId((int) refTypeId);

                ConstantPool pool = refType.getConstantPool();
                reply.writeInt(pool.length() + 1);
                // TODO(Gregersen) - raw bytes of the contant pool
                // byte[] bytes = pool.

                return reply;
            }
        }
    */

    }

    static class ClassType {
        public static final int ID = 3;

        static class SUPERCLASS {

            public static final int ID = 1;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long classId = input.readLong();
                KlassRef klassRef = verifyRefType(classId, reply, context);

                if (klassRef == null) {
                    return new JDWPResult(reply);
                }

                boolean isJavaLangObject = JAVA_LANG_OBJECT.equals(klassRef.getTypeAsString());

                if (isJavaLangObject) {
                    reply.writeLong(0);
                } else {
                    KlassRef superKlass = klassRef.getSuperClass();
                    reply.writeLong(context.getIds().getIdAsLong(superKlass));
                }
                return new JDWPResult(reply);
            }
        }

        static class SET_VALUES {

            public static final int ID = 2;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long classId = input.readLong();
                KlassRef klass = verifyRefType(classId, reply, context);

                if (klass == null) {
                    return new JDWPResult(reply);
                }

                // check if class has been prepared
                if (klass.getStatus() < ClassStatusConstants.PREPARED) {
                    reply.errorCode(JDWPErrorCodes.CLASS_NOT_PREPARED);
                    return new JDWPResult(reply);
                }

                int values = input.readInt();

                for (int i = 0; i < values; i++) {
                    long fieldId = input.readLong();
                    FieldRef field = verifyFieldRef(fieldId, reply, context);

                    if (field == null) {
                        return new JDWPResult(reply);
                    }

                    byte tag = field.getTagConstant();

                    if (tag == TagConstants.OBJECT) {
                        tag = context.getTag(field.getTypeAsString());
                    }
                    Object value = readValue(tag, input, context);
                    context.setStaticFieldValue(field, value);
                }
                return new JDWPResult(reply);
            }
        }
    }

    static class Methods {
        public static final int ID = 6;

        static class LINE_TABLE {
            public static final int ID = 1;

            static class Line {
                final long lineCodeIndex;
                final int lineNumber;

                Line(long lineCodeIndex, int lineNumber) {
                    this.lineCodeIndex = lineCodeIndex;
                    this.lineNumber = lineNumber;
                }
            }

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                PacketStream input = new PacketStream(packet);
                long refTypeId = input.readLong();
                long methodId = input.readLong();

                KlassRef klass = verifyRefType(refTypeId, reply, context);

                if (klass == null) {
                    return new JDWPResult(reply);
                }

                MethodRef method = verifyMethodRef(methodId, reply, context);

                if (method == null) {
                    return new JDWPResult(reply);
                }

                LineNumberTableRef table = method.getLineNumberTable();

                if (table != null) {
                    LineNumberTableRef.EntryRef[] entries = table.getEntries();
                    long start = method.isMethodNative() ? -1 : Integer.MAX_VALUE;
                    long end = method.isMethodNative() ? -1 : 0;
                    int lines = entries.length;
                    Line[] allLines = new Line[lines];

                    for (int i = 0; i < entries.length; i++) {
                        LineNumberTableRef.EntryRef entry = entries[i];
                        int bci = entry.getBCI();
                        int line = entry.getLineNumber();
                        if (bci < start) {
                            start = bci;
                        }
                        if (bci > end) {
                            end = bci;
                        }
                        allLines[i] = new Line(bci, line);
                    }
                    reply.writeLong(start);
                    reply.writeLong(end);
                    reply.writeInt(lines);
                    for (Line line : allLines) {
                        reply.writeLong(line.lineCodeIndex);
                        reply.writeInt(line.lineNumber);
                    }
                }
                return new JDWPResult(reply);
            }
        }

        // TODO(Gregersen) - current disabled by Capabilities.
        // Enabling causes the NetBeans debugger to send wrong stepping
        // events for step into/over so diabled for now. Perhaps the bytecode
        // returned from method.getCode() is incorrect?
        static class BYTECODES {
            public static final int ID = 3;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                KlassRef klassRef = verifyRefType(input.readLong(), reply, context);// ref type
                if (klassRef == null) {
                    return new JDWPResult(reply);
                }

                long methodId = input.readLong();
                MethodRef method = verifyMethodRef(methodId, reply, context);
                if (method == null) {
                    return new JDWPResult(reply);
                }

                byte[] code = method.getCode();

                reply.writeInt(code.length);
                reply.writeByteArray(code);

                return new JDWPResult(reply);
            }
        }

        static class VARIABLE_TABLE_WITH_GENERIC {
            public static final int ID = 5;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                KlassRef klassRef = verifyRefType(input.readLong(), reply, context);// ref type
                if (klassRef == null) {
                    return new JDWPResult(reply);
                }

                long methodId = input.readLong();
                MethodRef method = verifyMethodRef(methodId, reply, context);
                if (method == null) {
                    return new JDWPResult(reply);
                }

                KlassRef[] params = method.getParameters();
                int argCnt = 0; // the number of words in the frame used by the arguments
                for (KlassRef klass : params) {
                    if (klass.isPrimitive()) {
                        byte tag = klass.getTagConstant();
                        if (tag == TagConstants.DOUBLE || tag == TagConstants.LONG) {
                            argCnt += 2;
                        } else {
                            argCnt++;
                        }
                    }
                }
                LocalRef[] locals = method.getLocalVariableTable().getLocals();

                reply.writeInt(argCnt);
                reply.writeInt(locals.length);
                for (LocalRef local : locals) {
                    reply.writeLong(local.getStartBCI());
                    reply.writeString(local.getNameAsString());
                    reply.writeString(local.getTypeAsString());
                    reply.writeString(""); // TODO(Gregersen) - generic signature
                    reply.writeInt(local.getEndBCI() - local.getStartBCI());
                    reply.writeInt(local.getSlot());
                }
                return new JDWPResult(reply);
            }
        }
    }

    static class ObjectReference {
        public static final int ID = 9;

        static class REFERENCE_TYPE {
            public static final int ID = 1;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                long objectId = input.readLong();
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                Object object = context.getIds().fromId((int) objectId);

                if (object == context.getNullObject()) {
                    reply.errorCode(JDWPErrorCodes.INVALID_OBJECT);
                    return new JDWPResult(reply);
                }

                // can be either a ClassObjectId or a StaticObject
                KlassRef klassRef = context.getRefType(object);

                reply.writeByte(TypeTag.getKind(klassRef));
                reply.writeLong(context.getIds().getIdAsLong(klassRef));

                return new JDWPResult(reply);
            }
        }

        static class GET_VALUES {
            public static final int ID = 2;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long objectId = input.readLong();
                Object object = context.getIds().fromId((int) objectId);

                if (object == context.getNullObject()) {
                    reply.errorCode(JDWPErrorCodes.INVALID_OBJECT);
                    return new JDWPResult(reply);
                }

                int numFields = input.readInt();

                reply.writeInt(numFields);

                for (int i = 0; i < numFields; i++) {
                    long fieldId = input.readLong();
                    FieldRef field = verifyFieldRef(fieldId, reply, context);

                    if (field == null) {
                        return new JDWPResult(reply);
                    }

                    Object value = field.getValue(object);
                    byte tag = field.getTagConstant();

                    if (tag == TagConstants.OBJECT) {
                        tag = context.getTag(value);
                    }
                    writeValue(tag, value, reply, true, context);
                }
                return new JDWPResult(reply);
            }
        }

        static class SET_VALUES {
            public static final int ID = 3;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long objectId = input.readLong();
                Object object = context.getIds().fromId((int) objectId);

                if (object == context.getNullObject()) {
                    reply.errorCode(JDWPErrorCodes.INVALID_OBJECT);
                    return new JDWPResult(reply);
                }

                int numFields = input.readInt();

                for (int i = 0; i < numFields; i++) {
                    long fieldId = input.readLong();
                    FieldRef field = verifyFieldRef(fieldId, reply, context);

                    if (field == null) {
                        return new JDWPResult(reply);
                    }

                    byte tag = field.getTagConstant();
                    if (tag == TagConstants.OBJECT) {
                        tag = context.getTag(field.getTypeAsString());
                    }
                    Object value = readValue(tag, input, context);
                    field.setValue(object, value);
                }
                return new JDWPResult(reply);
            }
        }

        static class INVOKE_METHOD {
            public static final int ID = 6;

            static JDWPResult createReply(Packet packet, JDWPDebuggerController controller) {

                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                JDWPContext context = controller.getContext();

                long objectId = input.readLong();
                long threadId = input.readLong();

                Object thread = verifyThread(threadId, reply, context);

                if (thread == null) {
                    return new JDWPResult(reply);
                }

                if (verifyRefType(input.readLong(), reply, context) == null) {
                    return new JDWPResult(reply);
                }

                long methodId = input.readLong();
                int arguments = input.readInt();

                Object[] args = new Object[arguments];
                for (int i = 0; i < arguments; i++) {
                    byte valueKind = input.readByte();
                    args[i] = readValue(valueKind, input, context);
                }
                /*int options = */ input.readInt(); // TODO(Gregersen) - handle invocation options

                Object callee = context.getIds().fromId((int) objectId);
                MethodRef method = verifyMethodRef(methodId, reply, context);

                if (method == null) {
                    return new JDWPResult(reply);
                }

                if (JDWPDebuggerController.isDebug(JDWPDebuggerController.Debug.PACKET)) {
                    System.out.println("trying to invoke method: " + method.getNameAsString());
                }
                try {
                    // we have to call the method in the correct thread, so post a
                    // Callable to the controller and wait for the result to appear
                    ThreadJob job = new ThreadJob(thread, new Callable<Object>() {

                        @Override
                        public Object call() throws Exception {
                            return method.invokeMethod(callee, args);
                        }
                    });
                    controller.postJobForThread(job);
                    ThreadJob.JobResult result = job.getResult();

                    if (result.getException() != null) {
                        reply.writeByte(TagConstants.OBJECT);
                        reply.writeLong(0);
                        reply.writeByte(TagConstants.OBJECT);
                        reply.writeLong(context.getIds().getIdAsLong(result.getException()));
                    } else {
                        Object value = context.toGuest(result.getResult());

                        if (value != null) {
                            byte tag = context.getTag(value);
                            writeValue(tag, value, reply, true, context);

                        } else { // return value is null
                            reply.writeByte(TagConstants.OBJECT);
                            reply.writeLong(0);
                        }
                        // no exception, so zero object ID
                        reply.writeByte(TagConstants.OBJECT);
                        reply.writeLong(0);
                    }
                } catch (Throwable t) {
                    throw new RuntimeException("not able to invoke method through jdwp", t);
                }
                return new JDWPResult(reply);
            }
        }

        static class DISABLE_COLLECTION {
            public static final int ID = 7;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                long objectId = input.readLong();
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                Object object = context.getIds().fromId((int) objectId);

                if (object == context.getNullObject()) {
                    reply.errorCode(JDWPErrorCodes.INVALID_OBJECT);
                    return new JDWPResult(reply);
                }

                GCPrevention.disableGC(object);
                return new JDWPResult(reply);
            }
        }

        static class ENABLE_COLLECTION {
            public static final int ID = 8;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                long objectId = input.readLong();
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                Object object = context.getIds().fromId((int) objectId);

                if (object == context.getNullObject()) {
                    reply.errorCode(JDWPErrorCodes.INVALID_OBJECT);
                    return new JDWPResult(reply);
                }

                GCPrevention.enableGC(object);
                return new JDWPResult(reply);
            }
        }

        static class IS_COLLECTED {
            public static final int ID = 9;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                long objectId = input.readLong();
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                Object object = context.getIds().fromId((int) objectId);

                if (object == context.getNullObject()) {
                    reply.errorCode(JDWPErrorCodes.INVALID_OBJECT);
                    return new JDWPResult(reply);
                }

                reply.writeBoolean(object == context.getNullObject());
                return new JDWPResult(reply);
            }
        }
    }

    static class StringReference {
        public static final int ID = 10;

        static class VALUE {
            public static final int ID = 1;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long objectId = input.readLong();
                Object string = verifyString(objectId, reply, context);

                if (string == null) {
                    return new JDWPResult(reply);
                }

                if (string == context.getNullObject()) {
                    reply.errorCode(JDWPErrorCodes.INVALID_OBJECT);
                    return new JDWPResult(reply);
                } else {
                    reply.writeString(context.getStringValue(string));
                }
                return new JDWPResult(reply);
            }
        }
    }

    static class ThreadReference {
        public static final int ID = 11;

        private static final long SUSPEND_TIMEOUT = 200;

        static class NAME {
            public static final int ID = 1;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadId = input.readLong();
                Object thread = verifyThread(threadId, reply, context);

                if (thread == null) {
                    return new JDWPResult(reply);
                }

                String threadName = context.getThreadName(thread);

                reply.writeString(threadName);
                return new JDWPResult(reply);
            }
        }

        static class SUSPEND {
            public static final int ID = 2;

            static JDWPResult createReply(Packet packet, JDWPDebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadId = input.readLong();
                Object thread = verifyThread(threadId, reply, controller.getContext());

                if (thread == null) {
                    return new JDWPResult(reply);
                }

                if (JDWPDebuggerController.isDebug(JDWPDebuggerController.Debug.THREAD)) {
                    System.out.println("suspend thread packet for thread: " + thread);
                }
                controller.suspend(thread);
                return new JDWPResult(reply);
            }
        }

        static class RESUME {
            public static final int ID = 3;

            static JDWPResult createReply(Packet packet, JDWPDebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadId = input.readLong();
                Object thread = verifyThread(threadId, reply, controller.getContext());

                if (thread == null) {
                    return new JDWPResult(reply);
                }

                if (JDWPDebuggerController.isDebug(JDWPDebuggerController.Debug.THREAD)) {
                    System.out.println("resume thread packet for thread: " + thread);
                }
                controller.resume(thread, false);
                return new JDWPResult(reply);
            }
        }

        static class STATUS {
            public static final int ID = 4;

            public static final int JVMTI_THREAD_STATE_ALIVE = 0x0001;
            public static final int JVMTI_THREAD_STATE_TERMINATED = 0x0002;
            public static final int JVMTI_THREAD_STATE_RUNNABLE = 0x0004;
            public static final int JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER = 0x0400;
            public static final int JVMTI_THREAD_STATE_WAITING = 0x0080;
            public static final int JVMTI_THREAD_STATE_WAITING_INDEFINITELY = 0x0010;
            public static final int JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT = 0x0020;
            public static final int JVMTI_THREAD_STATE_SLEEPING = 0x0040;
            public static final int JVMTI_THREAD_STATE_IN_OBJECT_WAIT = 0x0100;
            public static final int JVMTI_THREAD_STATE_PARKED = 0x0200;
            public static final int JVMTI_THREAD_STATE_SUSPENDED = 0x100000;
            public static final int JVMTI_THREAD_STATE_INTERRUPTED = 0x200000;
            public static final int JVMTI_THREAD_STATE_IN_NATIVE = 0x400000;

            public static final int JVMTI_JAVA_LANG_THREAD_STATE_MASK =
                    JVMTI_THREAD_STATE_TERMINATED |
                            JVMTI_THREAD_STATE_ALIVE |
                            JVMTI_THREAD_STATE_RUNNABLE |
                            JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER |
                            JVMTI_THREAD_STATE_WAITING |
                            JVMTI_THREAD_STATE_WAITING_INDEFINITELY |
                            JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadId = input.readLong();
                Object thread = verifyThread(threadId, reply, context);

                if (thread == null) {
                    return new JDWPResult(reply);
                }

                int jvmtiThreadStatus = context.getThreadStatus(thread);
                int threadStatus = getThreadStatus(jvmtiThreadStatus);
                reply.writeInt(threadStatus);
                reply.writeInt(ThreadSuspension.getSuspensionCount(thread) > 0 ? 1 : 0);
                return new JDWPResult(reply, null);
            }

            private static int getThreadStatus(int jvmtiThreadStatus) {
                int masked = jvmtiThreadStatus & JVMTI_JAVA_LANG_THREAD_STATE_MASK;

                if ((masked & JVMTI_THREAD_STATE_TERMINATED) != 0) {
                    return ThreadStatusConstants.ZOMBIE;
                }
                if ((masked & JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_RUNNABLE) != 0) {
                    return ThreadStatusConstants.RUNNING;
                }
                if ((masked & JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER) != 0) {
                    return ThreadStatusConstants.WAIT;
                }
                if ((masked & JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_WAITING | JVMTI_THREAD_STATE_WAITING_INDEFINITELY) != 0) {
                    return ThreadStatusConstants.WAIT;
                }
                if ((masked & JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_WAITING | JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT) != 0) {
                    return ThreadStatusConstants.WAIT;
                }
                if (masked == 0) {
                    // new threads are returned as running
                    return ThreadStatusConstants.RUNNING;
                }
                return ThreadStatusConstants.RUNNING;
            }
        }

        static class THREAD_GROUP {
            public static final int ID = 5;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadId = input.readLong();
                Object thread = verifyThread(threadId, reply, context);

                if (thread == null) {
                    return new JDWPResult(reply);
                }

                Object threadGroup = context.getThreadGroup(thread);
                reply.writeLong(context.getIds().getIdAsLong(threadGroup));
                return new JDWPResult(reply);
            }
        }

        static class FRAMES {
            public static final int ID = 6;

            static JDWPResult createReply(Packet packet, JDWPDebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadId = input.readLong();
                Object thread = verifyThread(threadId, reply, controller.getContext());

                if (thread == null) {
                    return new JDWPResult(reply);
                }

                int startFrame = input.readInt();
                int length = input.readInt();

                SuspendedInfo suspendedInfo = controller.getSuspendedInfo(thread);

                if (suspendedInfo == null) {
                    reply.errorCode(JDWPErrorCodes.THREAD_NOT_SUSPENDED);
                    return new JDWPResult(reply);
                }

                if (suspendedInfo instanceof UnknownSuspendedInfo) {
                    suspendedInfo = awaitSuspendedInfo(controller, thread, suspendedInfo);
                }

                JDWPCallFrame[] frames = suspendedInfo.getStackFrames();

                if (length == -1) {
                    length = frames.length;
                }
                reply.writeInt(length);

                for (int i = startFrame; i < startFrame + length; i++) {
                    JDWPCallFrame frame = frames[i];
                    reply.writeLong(controller.getContext().getIds().getIdAsLong(frame));
                    reply.writeByte(frame.getTypeTag());
                    reply.writeLong(frame.getClassId());
                    reply.writeLong(frame.getMethodId());
                    reply.writeLong(frame.getCodeIndex());
                }
                return new JDWPResult(reply);
            }
        }

        static class FRAME_COUNT {
            public static final int ID = 7;

            static JDWPResult createReply(Packet packet, JDWPDebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadId = input.readLong();
                Object thread = verifyThread(threadId, reply, controller.getContext());

                if (thread == null) {
                    return new JDWPResult(reply);
                }

                SuspendedInfo suspendedInfo = controller.getSuspendedInfo(thread);

                if (suspendedInfo == null) {
                    reply.errorCode(JDWPErrorCodes.THREAD_NOT_SUSPENDED);
                    return new JDWPResult(reply);
                }

                if (suspendedInfo instanceof UnknownSuspendedInfo) {
                    suspendedInfo = awaitSuspendedInfo(controller, thread, suspendedInfo);
                }

                reply.writeInt(suspendedInfo.getStackFrames().length);
                return new JDWPResult(reply);
            }
        }

        static class SUSPEND_COUNT {
            public static final int ID = 12;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadId = input.readLong();
                Object thread = verifyThread(threadId, reply, context);

                if (thread == null) {
                    return new JDWPResult(reply);
                }

                int suspensionCount = ThreadSuspension.getSuspensionCount(thread);
                if (JDWPDebuggerController.isDebug(JDWPDebuggerController.Debug.THREAD)) {
                    System.out.println("suspension count: " + suspensionCount + " returned for thread: " + thread);
                }
                reply.writeInt(suspensionCount);
                return new JDWPResult(reply);
            }
        }

        private static SuspendedInfo awaitSuspendedInfo(JDWPDebuggerController controller, Object thread, SuspendedInfo suspendedInfo) {
            // OK, we hard suspended this thread, but it hasn't yet actually suspended
            // in a code location known to Truffle
            // let's check if the thread is RUNNING and give it a moment to reach
            // the suspended state
            SuspendedInfo result = suspendedInfo;
            Thread hostThread = controller.getContext().getGuest2HostThread(thread);
            if (hostThread.getState() == Thread.State.RUNNABLE) {
                if (JDWPDebuggerController.isDebug(JDWPDebuggerController.Debug.THREAD)) {
                    System.out.println("Awaiting suspended info for thread " + controller.getContext().getThreadName(thread));
                }
                long timeout = System.currentTimeMillis() + SUSPEND_TIMEOUT;
                while (result instanceof UnknownSuspendedInfo && System.currentTimeMillis() < timeout) {
                    try {
                        Thread.sleep(10);
                        result = controller.getSuspendedInfo(thread);
                    } catch (InterruptedException e) {
                        // ignore this here
                    }
                }
            }
            if (result instanceof UnknownSuspendedInfo) {
                if (JDWPDebuggerController.isDebug(JDWPDebuggerController.Debug.THREAD)) {
                    System.out.println("Still no suspended info for thread " + controller.getContext().getThreadName(thread));
                }
            }
            return result;
        }
    }

    static class ThreadGroupReference {
        public static final int ID = 12;

        static class NAME {
            public static final int ID = 1;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadGroupId = input.readLong();
                Object threadGroup = verifyThreadGroup(threadGroupId, reply, context);

                if (threadGroup == null) {
                    return new JDWPResult(reply);
                }

                reply.writeString("threadGroup-1"); // TODO(Gregersen) - implement retrieving threadgroup name
                return new JDWPResult(reply);
            }
        }
    }

    static class ArrayReference {
        public static final int ID = 13;

        static class LENGTH {
            public static final int ID = 1;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long arrayId = input.readLong();
                Object array = verifyArray(arrayId, reply, context);

                if (array == null) {
                    return new JDWPResult(reply);
                }

                reply.writeInt(context.getArrayLength(array));
                return new JDWPResult(reply, null);
            }
        }

        static class GET_VALUES {
            public static final int ID = 2;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                long arrayId = input.readLong();
                int index = input.readInt();
                int length = input.readInt();

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                Object array = verifyArray(arrayId, reply, context);

                if (array == null || !verifyArrayLength(array, length, reply, context)) {
                    return new JDWPResult(reply);
                }

                byte tag = context.getTypeTag(array);
                boolean isPrimitive = TagConstants.isPrimitive(tag);

                reply.writeByte(tag);
                reply.writeInt(length);
                for (int i = index; i < index + length; i++) {
                    Object theValue = context.getArrayValue(array, i);
                    writeValue(tag, theValue, reply, !isPrimitive, context);
                }
                return new JDWPResult(reply);
            }
        }

        static class SET_VALUES {
            public static final int ID = 3;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long arrayId = input.readLong();
                int index = input.readInt();
                int values = input.readInt();

                Object array = verifyArray(arrayId, reply, context);

                if (array == null || !verifyArrayLength(array, values, reply, context)) {
                    return new JDWPResult(reply);
                }

                byte tag = context.getTypeTag(array);

                setArrayValues(context, input, index, values, array, tag);

                return new JDWPResult(reply);
            }

            private static void setArrayValues(JDWPContext context, PacketStream input, int index, int values, Object array, byte tag) {
                for (int i = index; i < index + values; i++) {
                    switch (tag) {
                        case BOOLEAN:
                            boolean bool = input.readBoolean();
                            boolean[] boolArray = context.getUnboxedArray(array);
                            boolArray[i] = bool;
                            break;
                        case TagConstants.BYTE:
                            byte b = input.readByte();
                            byte[] byteArray = context.getUnboxedArray(array);
                            byteArray[i] = b;
                            break;
                        case TagConstants.SHORT:
                            short s = input.readShort();
                            short[] shortArray = context.getUnboxedArray(array);
                            shortArray[i] = s;
                            break;
                        case TagConstants.CHAR:
                            char c = input.readChar();
                            char[] charArray = context.getUnboxedArray(array);
                            charArray[i] = c;
                            break;
                        case TagConstants.INT:
                            int j = input.readInt();
                            int[] intArray = context.getUnboxedArray(array);
                            intArray[i] = j;
                            break;
                        case TagConstants.FLOAT:
                            float f = input.readFloat();
                            float[] floatArray = context.getUnboxedArray(array);
                            floatArray[i] = f;
                            break;
                        case TagConstants.LONG:
                            long l = input.readLong();
                            long[] longArray = context.getUnboxedArray(array);
                            longArray[i] = l;
                            break;
                        case TagConstants.DOUBLE:
                            double d = input.readDouble();
                            double[] doubleArray = context.getUnboxedArray(array);
                            doubleArray[i] = d;
                            break;
                        case TagConstants.ARRAY:
                        case TagConstants.STRING:
                        case TagConstants.OBJECT:
                            Object value = context.getIds().fromId((int) input.readLong());
                            context.setArrayValue(array, i, value);
                            break;
                        default:
                            throw new RuntimeException("should not reach here");
                    }
                }
            }
        }
    }

    static class ClassLoaderReference {
        public static final int ID = 14;

        static class VISIBLE_CLASSES {

            public static final int ID = 1;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long classLoaderId = input.readLong();

                Object classLoader = verifyClassLoader(classLoaderId, reply, context);
                if (classLoader == null) {
                    return new JDWPResult(reply);
                }

                // TODO(Gregersen) - we will need all classes for which this classloader was the initiating loader
                KlassRef[] klasses = context.getInitiatedClasses(classLoader);

                reply.writeInt(klasses.length);

                for (KlassRef klass : klasses) {
                    reply.writeByte(TypeTag.getKind(klass));
                    reply.writeLong(context.getIds().getIdAsLong(klass));
                }
                return new JDWPResult(reply);
            }
        }
    }

    static class EventRequest {
        public static final int ID = 15;

        static class SET {
            public static final int ID = 1;
        }

        static class CLEAR {
            public static final int ID = 2;
        }
    }

    static class StackFrame {
        public static final int ID = 16;

        static class GET_VALUES {
            public static final int ID = 1;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                if (verifyThread(input.readLong(), reply, context) == null) {
                    return new JDWPResult(reply);
                }

                long frameId = input.readLong();
                int slots = input.readInt();
                reply.writeInt(slots);

                JDWPCallFrame frame = verifyClassFrame(frameId, reply, context);

                if (frame == null) {
                    return new JDWPResult(reply);
                }

                Object thisValue = frame.getThisValue();
                Object[] variables = frame.getVariables();
                int offset = thisValue != null ? 1 : 0;

                try {
                    // below assumes the debugger asks for slot values in increasing order
                    for (int i = 0; i < slots; i++) {
                        int slot = input.readInt();
                        Object value = variables[slot - offset];

                        byte sigbyte = input.readByte();
                        // TODO(Gregersen) - verify sigbyte against actual value type
                        if (sigbyte == TagConstants.ARRAY) {
                            reply.writeByte(sigbyte);
                            reply.writeLong(context.getIds().getIdAsLong(value));
                        } else if (sigbyte == TagConstants.OBJECT) {
                            sigbyte = context.getTag(value);
                            reply.writeByte(sigbyte);
                            reply.writeLong(context.getIds().getIdAsLong(value));
                        } else {
                            writeValue(sigbyte, value, reply, true, context);
                        }
                    }
                } catch (ArrayIndexOutOfBoundsException ex) {
                    // invalid slot provided
                    reply.errorCode(JDWPErrorCodes.INVALID_SLOT);
                    return new JDWPResult(reply);
                }

                return new JDWPResult(reply);
            }
        }

        static class SET_VALUES {
            public static final int ID = 2;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                if (verifyThread(input.readLong(), reply, context) == null) {
                    return new JDWPResult(reply);
                }

                long frameId = input.readLong();
                int slots = input.readInt();

                JDWPCallFrame frame = verifyClassFrame(frameId, reply, context);

                if (frame == null) {
                    return new JDWPResult(reply);
                }

                Object thisValue = frame.getThisValue();
                Object[] variables = frame.getVariables();
                int offset = thisValue != null ? 1 : 0;

                // below assumes the debugger asks for slot values in increasing order
                for (int i = 0; i < slots; i++) {
                    int slot = input.readInt();
                    byte kind = input.readByte();
                    variables[slot - offset] = readValue(kind, input, context);
                }
                return new JDWPResult(reply);
            }
        }

        static class THIS_OBJECT {
            public static final int ID = 3;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                if (verifyThread(input.readLong(), reply, context) == null) {
                    return new JDWPResult(reply);
                }
                long frameId = input.readLong();

                JDWPCallFrame frame = verifyClassFrame(frameId, reply, context);

                if (frame == null) {
                    return new JDWPResult(reply);
                }

                Object thisValue = frame.getThisValue();
                reply.writeByte(TagConstants.OBJECT);

                if (thisValue != null) {
                    reply.writeLong(context.getIds().getIdAsLong(thisValue));
                } else {
                    reply.writeLong(0);
                }
                return new JDWPResult(reply);
            }
        }
    }

    static class ClassObjectReference {
        public static final int ID = 17;

        static class REFLECTED_TYPE {
            public static final int ID = 1;

            static JDWPResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long classObjectId = input.readLong();

                ClassObjectId id = verifyClassObject(classObjectId, reply, context);

                reply.writeByte(TypeTag.getKind(id.getKlassRef()));
                reply.writeLong(context.getIds().getIdAsLong(id.getKlassRef()));
                return new JDWPResult(reply);
            }
        }
    }

    private static Object readValue(byte valueKind, PacketStream input, JDWPContext context) {
        switch (valueKind) {
            case BOOLEAN:
                return input.readBoolean();
            case TagConstants.BYTE:
                return input.readByte();
            case TagConstants.SHORT:
                return input.readShort();
            case TagConstants.CHAR:
                return input.readChar();
            case TagConstants.INT:
                return input.readInt();
            case TagConstants.FLOAT:
                return input.readFloat();
            case TagConstants.LONG:
                return input.readLong();
            case TagConstants.DOUBLE:
                return input.readDouble();
            case TagConstants.ARRAY:
            case TagConstants.STRING:
            case TagConstants.OBJECT:
                return context.getIds().fromId((int) input.readLong());
            default:
                throw new RuntimeException("Should not reach here!");
        }
    }

    public static void writeValue(byte tag, Object value, PacketStream reply, boolean tagged, JDWPContext context) {
        if (tagged) {
            reply.writeByte(tag);
        }
        switch (tag) {
            case BOOLEAN:
                if (value.getClass() == Long.class) {
                    long unboxed = (long) value;
                    reply.writeBoolean(unboxed > 0 ? true : false);
                } else {
                    reply.writeBoolean((boolean) value);
                }
                break;
            case TagConstants.BYTE:
                if (value.getClass() == Long.class) {
                    long unboxed = (long) value;
                    reply.writeByte((byte) unboxed);
                } else {
                    reply.writeByte((byte) value);
                }
                break;
            case TagConstants.SHORT:
                if (value.getClass() == Long.class) {
                    long unboxed = (long) value;
                    reply.writeShort((short) unboxed);
                } else {
                    reply.writeShort((short) (value));
                }
                break;
            case TagConstants.CHAR:
                if (value.getClass() == Long.class) {
                    long unboxed = (long) value;
                    reply.writeChar((char) unboxed);
                } else {
                    reply.writeChar((char) value);
                }
                break;
            case TagConstants.INT:
                if (value.getClass() == Long.class) {
                    long unboxed = (long) value;
                    reply.writeInt((int) unboxed);
                } else {
                    reply.writeInt((int) value);
                }
                break;
            case TagConstants.FLOAT:
                if (value.getClass() == Long.class) {
                    long unboxed = (long) value;
                    reply.writeFloat(unboxed);
                } else {
                    reply.writeFloat((float) value);
                }
                break;
            case TagConstants.LONG:
                reply.writeLong((long) value);
                break;
            case TagConstants.DOUBLE:
                if (value.getClass() == Long.class) {
                    long unboxed = (long) value;
                    reply.writeDouble(unboxed);
                } else {
                    reply.writeDouble((double) value);
                }
                break;
            case TagConstants.OBJECT:
            case TagConstants.STRING:
            case TagConstants.ARRAY:
                reply.writeLong(context.getIds().getIdAsLong(value));
                break;
            default:
                throw new RuntimeException("Should not reach here!");
        }
    }

    private static KlassRef verifyRefType(long refTypeId, PacketStream reply, JDWPContext context) {
        KlassRef klass;
        try {
            klass = (KlassRef) context.getIds().fromId((int) refTypeId);
        } catch (ClassCastException ex) {
            reply.errorCode(JDWPErrorCodes.INVALID_CLASS);
            return null;
        }
        if (klass == context.getNullKlass()) {
            reply.errorCode(JDWPErrorCodes.INVALID_OBJECT);
            return null;
        }
        return klass;
    }

    private static FieldRef verifyFieldRef(long fieldId, PacketStream reply, JDWPContext context) {
        FieldRef field;
        try {
            field = (FieldRef) context.getIds().fromId((int) fieldId);
        } catch (ClassCastException ex) {
            reply.errorCode(JDWPErrorCodes.INVALID_FIELDID);
            return null;
        }
        return field;
    }

    private static MethodRef verifyMethodRef(long methodId, PacketStream reply, JDWPContext context) {
        MethodRef method;
        try {
            method = (MethodRef) context.getIds().fromId((int) methodId);
        } catch (ClassCastException ex) {
            reply.errorCode(JDWPErrorCodes.INVALID_METHODID);
            return null;
        }
        return method;
    }

    private static Object verifyThread(long threadId, PacketStream reply, JDWPContext context) {
        Object thread = context.getIds().fromId((int) threadId);

        if (thread == context.getNullObject()) {
            reply.errorCode(JDWPErrorCodes.INVALID_OBJECT);
            return null;
        }

        if (!context.isValidThread(thread)) {
            reply.errorCode(JDWPErrorCodes.INVALID_THREAD);
            return null;
        }
        return thread;
    }

    private static Object verifyThreadGroup(long threadGroupId, PacketStream reply, JDWPContext context) {
        Object threadGroup = context.getIds().fromId((int) threadGroupId);

        if (threadGroup == context.getNullObject()) {
            reply.errorCode(JDWPErrorCodes.INVALID_OBJECT);
            return null;
        }

        if (!context.isValidThreadGroup(threadGroup)) {
            reply.errorCode(JDWPErrorCodes.INVALID_THREAD_GROUP);
            return null;
        }

        return threadGroup;
    }

    private static Object verifyArray(long arrayId, PacketStream reply, JDWPContext context) {
        Object array = context.getIds().fromId((int) arrayId);

        if (array == context.getNullObject()) {
            reply.errorCode(JDWPErrorCodes.INVALID_OBJECT);
            return null;
        }

        if (!context.isArray(array)) {
            reply.errorCode(JDWPErrorCodes.INVALID_ARRAY);
            return null;
        }
        return array;
    }

    private static boolean verifyArrayLength(Object array, int length, PacketStream reply, JDWPContext context) {
        if (!context.verifyArrayLength(array, length)) {
            reply.errorCode(JDWPErrorCodes.INVALID_LENGTH);
            return false;
        }
        return true;
    }

    private static Object verifyString(long objectId, PacketStream reply, JDWPContext context) {
        Object string = context.getIds().fromId((int) objectId);

        if (!context.isString(string)) {
            reply.errorCode(JDWPErrorCodes.INVALID_STRING);
            return null;
        }
        return string;
    }

    private static Object verifyClassLoader(long classLoaderId, PacketStream reply, JDWPContext context) {
        Object classLoader = context.getIds().fromId((int) classLoaderId);

        if (!context.isValidClassLoader(classLoader)) {
            reply.errorCode(JDWPErrorCodes.INVALID_CLASS_LOADER);
            return null;
        }
        return classLoader;
    }

    private static JDWPCallFrame verifyClassFrame(long frameId, PacketStream reply, JDWPContext context) {
        Object frame = context.getIds().fromId((int) frameId);
        if (!(frame instanceof JDWPCallFrame)) {
            reply.errorCode(JDWPErrorCodes.INVALID_FRAMEID);
            return null;
        }

        return (JDWPCallFrame) frame;
    }

    private static ClassObjectId verifyClassObject(long classObjectId, PacketStream reply, JDWPContext context) {
        Object object = context.getIds().fromId((int) classObjectId);

        if (object == context.getNullObject() || !(object instanceof ClassObjectId)) {
            reply.errorCode(JDWPErrorCodes.INVALID_OBJECT);
            return null;
        }
        return (ClassObjectId) object;
    }
}

