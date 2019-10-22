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
package com.oracle.truffle.espresso.debugger.jdwp;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.LineNumberTable;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Local;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

class JDWP {

    public static Object suspenStartupLock = new Object();

    public static final String JAVA_LANG_STRING = "Ljava/lang/String;";

    static class VirtualMachine {
        public static final int ID = 1;

        static class VERSION {
            public static final int ID = 1;

            static PacketStream createReply(Packet packet) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeString(EspressoVirtualMachine.VM_Description);
                reply.writeInt(1);
                reply.writeInt(6);
                reply.writeString(EspressoVirtualMachine.vmVersion);
                reply.writeString(EspressoVirtualMachine.vmName);
                return reply;
            }
        }

        static class CLASSES_BY_SIGNATURE {
            public static final int ID = 2;

            static PacketStream createReply(Packet packet, JDWPDebuggerController controller) {
                // get the requested classes
                PacketStream input = new PacketStream(packet);
                String signature = input.readString();
                String slashName = ClassNameUtils.fromInternalObjectNametoSlashName(signature);
                Symbol<Symbol.Type> type = controller.getContext().getTypes().fromClassGetName(slashName);
                Klass[] loaded = controller.getContext().getRegistries().findLoadedClassAny(type);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeInt(loaded.length);
                for (Klass klass : loaded) {
                    reply.writeByte(TypeTag.getKind(klass));
                    reply.writeLong(Ids.getIdAsLong(klass));
                    if (klass instanceof ObjectKlass) {
                        ObjectKlass objectKlass = (ObjectKlass) klass;
                        reply.writeInt(ClassStatusConstants.fromEspressoStatus(objectKlass.getState()));
                    } else {
                        reply.writeInt(ClassStatusConstants.fromEspressoStatus(ObjectKlass.INITIALIZED));
                    }
                }

                return reply;
            }
        }

        static class ALL_THREADS {
            public static final int ID = 4;

            static PacketStream createReply(Packet packet, JDWPDebuggerController controller) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                StaticObject[] allThreads = controller.getContext().getAllGuestThreads();
                reply.writeInt(allThreads.length);
                for (StaticObject t : allThreads) {
                    reply.writeLong(Ids.getIdAsLong(t));
                }
                return reply;
            }
        }

        static class IDSIZES {
            public static final int ID = 7;

            static PacketStream createReply(Packet packet) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeInt(EspressoVirtualMachine.sizeofFieldRef);
                reply.writeInt(EspressoVirtualMachine.sizeofMethodRef);
                reply.writeInt(EspressoVirtualMachine.sizeofObjectRef);
                reply.writeInt(EspressoVirtualMachine.sizeofClassRef);
                reply.writeInt(EspressoVirtualMachine.sizeofFrameRef);
                return reply;
            }
        }

        static class RESUME {
            public static final int ID = 9;

            static PacketStream createReply(Packet packet, JDWPDebuggerController controller) {
                controller.resume();
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                return reply;
            }
        }

        static class CREATE_STRING {
            public static final int ID = 11;

            static PacketStream createReply(Packet packet, JDWPDebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                String utf = input.readString();
                // we must create a new StaticObject instance representing the String
                Meta meta = controller.getContext().getMeta();
                StaticObject createdString = meta.toGuestString(utf, meta);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeLong(Ids.getIdAsLong(createdString));
                return reply;
            }
        }

        static class CAPABILITIES {
            public static final int ID = 12;

            static PacketStream createReply(Packet packet) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeBoolean(true); // canWatchFieldModification
                reply.writeBoolean(true); // canWatchFieldAccess
                reply.writeBoolean(false); // canGetBytecodes
                reply.writeBoolean(true); // canGetSyntheticAttribute
                reply.writeBoolean(false); // canGetOwnedMonitorInfo
                reply.writeBoolean(false); // canGetCurrentContendedMonitor
                reply.writeBoolean(false); // canGetMonitorInfo
                return reply;
            }
        }

        static class CAPABILITIES_NEW {
            public static final int ID = 17;

            static PacketStream createReply(Packet packet) {
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
                reply.writeBoolean(true); // canSetDefaultStratum
                reply.writeBoolean(true); // canGetInstanceInfo
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
                return reply;
            }
        }
    }

    static class ReferenceType {
        public static final int ID = 2;

        static class CLASSLOADER {
            public static final int ID = 2;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long refTypeId = input.readLong();
                Klass klass = (Klass) Ids.fromId((int)refTypeId);

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                StaticObject loader = klass.getDefiningClassLoader();
                reply.writeLong(Ids.getIdAsLong(loader));
                return reply;
            }
        }

        static class GET_VALUES {
            public static final int ID = 6;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long refTypeId = input.readLong();
                Klass klass = (Klass) Ids.fromId((int)refTypeId);

                int fields = input.readInt();
                reply.writeInt(fields);

                for (int i = 0; i < fields; i++) {
                    long fieldId = input.readLong();
                    Field field = (Field) Ids.fromId((int)fieldId);

                    byte tag = TagConstants.fromJavaKind(field.getKind());

                    Object value = field.get(field.getDeclaringKlass().tryInitializeAndGetStatics());
                    if (tag == TagConstants.OBJECT) {
                        // check specifically for String
                        if (value instanceof StaticObject) {
                            StaticObject staticObject = (StaticObject) value;
                            if (staticObject.isArray()) {
                                tag = TagConstants.ARRAY;
                            } else if (JAVA_LANG_STRING.equals(staticObject.getKlass().getType().toString())) {
                                tag = TagConstants.STRING;
                            }
                        }
                    }
                    writeValue(tag, value, reply, true);
                }
                return reply;
            }
        }

        static class SOURCE_FILE {
            public static final int ID = 7;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long refTypeId = input.readLong();

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                Klass klass = (Klass) Ids.fromId((int)refTypeId);

                String sourceFile = "Generated";
                Method[] methods = klass.getDeclaredMethods();
                for (Method method : methods) {
                    if (method.getSourceFile() != null && !"Generated".equals(method.getSourceFile())) {
                        sourceFile = method.getSourceFile();
                        break;
                    }
                }
                reply.writeString(sourceFile);
                return reply;
            }
        }

        static class INTERFACES {
            public static final int ID = 10;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long refTypeId = input.readLong();

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                Klass klass = (Klass) Ids.fromId((int)refTypeId);
                ObjectKlass[] interfaces = klass.getInterfaces();

                reply.writeInt(interfaces.length);
                for (ObjectKlass itf : interfaces) {
                    reply.writeLong(Ids.getIdAsLong(itf));
                }
                return reply;
            }
        }

        // list which only purpose is to have hard references to created ClassObjectId objects
        private static final List<ClassObjectId> classObjectIds = new ArrayList<>();

        static class CLASS_OBJECT {
            public static final int ID = 11;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long refTypeId = input.readLong();

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                Klass klass = (Klass) Ids.fromId((int)refTypeId);

                // wrap this in ClassIdObject
                ClassObjectId id = new ClassObjectId(klass);
                classObjectIds.add(id);
                reply.writeLong(Ids.getIdAsLong(id));
                return reply;
            }
        }

        static class SIGNATURE_WITH_GENERIC {
            public static final int ID = 13;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long refTypeId = input.readLong();

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                Klass klass = (Klass) Ids.fromId((int) refTypeId);
                Symbol<Symbol.Type> type = klass.getType();

                reply.writeString(type.toString());
                reply.writeString(""); // TODO(Gregersen) - generic signature
                return reply;
            }
        }

        static class FIELDS_WITH_GENERIC {
            public static final int ID = 14;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long refTypeId = input.readLong();
                Klass refType = (Klass) Ids.fromId((int) refTypeId);

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                Field[] declaredFields = refType.getDeclaredFields();
                int numDeclaredFields = declaredFields.length;
                reply.writeInt(numDeclaredFields);
                for (Field field : declaredFields) {
                    reply.writeLong(Ids.getIdAsLong(field));
                    reply.writeString(field.getName().toString());
                    reply.writeString(field.getType().toString());
                    reply.writeString(field.getGenericSignature().toString());
                    reply.writeInt(field.getModifiers());
                }
                return reply;
            }
        }

        static class METHODS_WITH_GENERIC {
            public static final int ID = 15;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long refTypeId = input.readLong();
                Klass refType = (Klass) Ids.fromId((int) refTypeId);

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                Method[] declaredMethods = refType.getDeclaredMethods();
                int numDeclaredMethods = declaredMethods.length;
                reply.writeInt(numDeclaredMethods);
                for (Method method : declaredMethods) {
                    reply.writeLong(Ids.getIdAsLong(method));
                    reply.writeString(method.getName().toString());
                    reply.writeString(method.getRawSignature().toString());
                    reply.writeString(""); // TODO(Gregersen) - get the generic signature
                    reply.writeInt(method.getModifiers());
                }
                return reply;
            }
        }

        static class CONSTANT_POOL {
            public static final int ID = 18;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long refTypeId = input.readLong();
                Klass refType = (Klass) Ids.fromId((int) refTypeId);

                ConstantPool pool = refType.getConstantPool();
                reply.writeInt(pool.length() + 1);
                // TODO(Gregersen) - raw bytes of the contant pool
                // byte[] bytes = pool.

                return reply;
            }
        }
    }

    static class ClassType {
        public static final int ID = 3;

        static class SUPERCLASS {

            public static final int ID = 1;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long classId = input.readLong();
                Klass refType = (Klass) Ids.fromId((int) classId);
                boolean isJavaLangObject = refType.isJavaLangObject();

                if (isJavaLangObject) {
                    reply.writeLong(0);
                } else {
                    ObjectKlass superKlass = refType.getSuperKlass();
                    reply.writeLong(Ids.getIdAsLong(superKlass));
                }
                return reply;
            }
        }

        static class SET_VALUES {

            public static final int ID = 2;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long classId = input.readLong();
                Klass refType = (Klass) Ids.fromId((int) classId);
                int values = input.readInt();

                for (int i = 0; i < values; i++) {
                    long fieldId = input.readLong();
                    Field field = (Field) Ids.fromId((int)fieldId);
                    byte tag = TagConstants.fromJavaKind(field.getKind());
                    if (tag == TagConstants.OBJECT) {
                        if (JAVA_LANG_STRING.equals(field.getType().toString())) {
                            tag = TagConstants.STRING;
                        }
                    }
                    Object value = readValue(tag, input);
                    field.set(refType.getStatics(), value);
                }

                return reply;
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

            static PacketStream createReply(Packet packet) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                PacketStream input = new PacketStream(packet);
                long refTypeId = input.readLong();
                long methodId = input.readLong();

                Klass refType = (Klass) Ids.fromId((int) refTypeId);
                Method method = (Method) Ids.fromId((int) methodId);
                //System.out.println("asked for lines for: " + refType.getName().toString() + "." + method.getName());

                LineNumberTable table = method.getLineNumberTable();

                if (table != null) {
                    LineNumberTable.Entry[] entries = table.getEntries();
                    long start = method.isNative() ? -1 : Integer.MAX_VALUE;
                    long end = method.isNative() ? -1 : 0;
                    int lines = entries.length;
                    Line[] allLines = new Line[lines];

                    for (int i = 0; i < entries.length; i++) {
                        LineNumberTable.Entry entry = entries[i];
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
                return reply;
            }
        }

        // TODO(Gregersen) - current disabled by Capabilities.
        // Enabling causes the NetBeans debugger to send wrong stepping
        // events for step into/over so diabled for now. Perhaps the bytecode
        // returned from method.getCode() is incorrect?
        static class BYTECODES {
            public static final int ID = 3;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                input.readLong(); // ref type
                long methodId = input.readLong();
                Method method = (Method) Ids.fromId((int) methodId);

                byte[] code = method.getCode();

                reply.writeInt(code.length);
                reply.writeByteArray(code);

                return reply;
            }
        }

        static class VARIABLE_TABLE_WITH_GENERIC {
            public static final int ID = 5;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long refTypeId = input.readLong();
                long methodId = input.readLong();

                Method method = (Method) Ids.fromId((int)methodId);
                Klass[] params = method.resolveParameterKlasses();
                int argCnt = 0; // the number of words in the frame used by the arguments
                for (Klass klass : params)  {
                    if (klass.isPrimitive()) {
                        if (klass.getJavaKind() == JavaKind.Double || klass.getJavaKind() == JavaKind.Long) {
                            argCnt += 2;
                        } else {
                            argCnt++;
                        }
                    }
                }
                Local[] locals = method.getLocalVariableTable().getLocals();

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeInt(argCnt);
                reply.writeInt(locals.length);
                for (Local local : locals) {
                    reply.writeLong(local.getStartBCI());
                    reply.writeString(local.getName().toString());
                    reply.writeString(local.getType().toString());
                    reply.writeString(""); // TODO(Gregersen) - generic signature
                    reply.writeInt(local.getEndBCI() - local.getStartBCI());
                    reply.writeInt(local.getSlot());
                }

                return reply;
            }
        }
    }

    static class ObjectReference {
        public static final int ID = 9;

        static class REFERENCE_TYPE {
            public static final int ID = 1;

            static PacketStream createReply(Packet packet, EspressoContext context) {
                PacketStream input = new PacketStream(packet);
                long objectId = input.readLong();
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                Object object = Ids.fromId((int) objectId);
                // can be either a ClassObjectId or a StaticObject
                Klass klass;
                if (object instanceof StaticObject) {
                    if (StaticObject.NULL == object) {
                        // null object
                        klass = NullKlass.getKlass(context);
                    } else {
                        klass = ((StaticObject) object).getKlass();
                    }
                } else {
                    klass = ((ClassObjectId) object).getRefType();
                }

                reply.writeByte(TypeTag.getKind(klass));
                reply.writeLong(Ids.getIdAsLong(klass));

                return reply;
            }
        }

        static class GET_VALUES {
            public static final int ID = 2;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long objectId = input.readLong();
                StaticObject staticObject = (StaticObject) Ids.fromId((int) objectId);
                int numFields = input.readInt();

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeInt(numFields);

                for (int i = 0; i < numFields; i++) {
                    long fieldId = input.readLong();
                    Field field = (Field) Ids.fromId((int) fieldId);
                    Object value = field.get(staticObject);
                    byte tag = TagConstants.fromJavaKind(field.getKind());
                    if (tag == TagConstants.OBJECT) {
                        if (value instanceof StaticObject) {
                            if (((StaticObject) value).isArray()) {
                                tag = TagConstants.ARRAY;
                            }
                            else if (JAVA_LANG_STRING.equals(((StaticObject) value).getKlass().getType().toString())) {
                                tag = TagConstants.STRING;
                            }
                        }
                    }
                    writeValue(tag, value, reply, true);
                }
                return reply;
            }
        }

        static class SET_VALUES {
            public static final int ID = 3;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long objectId = input.readLong();
                StaticObject staticObject = (StaticObject) Ids.fromId((int) objectId);
                int numFields = input.readInt();

                for (int i = 0; i < numFields; i++) {
                    long fieldId = input.readLong();
                    Field field = (Field) Ids.fromId((int) fieldId);
                    byte tag = TagConstants.fromJavaKind(field.getKind());
                    if (tag == TagConstants.OBJECT) {
                        if (JAVA_LANG_STRING.equals(field.getType().toString())) {
                            tag = TagConstants.STRING;
                        }
                    }
                    Object value = readValue(tag, input);
                    field.set(staticObject, value);
                }
                return new PacketStream().replyPacket().id(packet.id);
            }
        }

        static class INVOKE_METHOD {
            public static final int ID = 6;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long objectId = input.readLong();
                long threadId = input.readLong();
                long classId = input.readLong();
                long methodId = input.readLong();
                int arguments = input.readInt();

                Object[] args = new Object[arguments];
                for (int i = 0; i < arguments; i++) {
                    byte valueKind = input.readByte();
                    args[i] = readValue(valueKind, input);
                    // TODO(Gregersen) - convert to guest objects and locate real objects by IDs
                }
                int options = input.readInt(); // TODO(Gregersen) - handle invocation options

                Object callee = Ids.fromId((int)objectId);
                Method method = (Method) Ids.fromId((int)methodId);
                StaticObject thread = (StaticObject) Ids.fromId((int) threadId);

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                try {
                    Object value = method.invokeWithConversions(callee, args);
                    if (value != null) {
                        if (value instanceof StaticObject) {
                            StaticObject staticObject = (StaticObject) value;
                            byte tag = TagConstants.fromJavaKind(staticObject.getKlass().getJavaKind());
                            if (tag == TagConstants.OBJECT) {
                                // check specifically for String
                                if (JAVA_LANG_STRING.equals(staticObject.getKlass().getType().toString())) {
                                    tag = TagConstants.STRING;
                                }
                            }
                            writeValue(tag, value, reply, true);
                        }
                    }
                    else { // return value in null
                        reply.writeByte(TagConstants.OBJECT);
                        reply.writeLong(0);
                    }
                } catch (ClassCastException ex) {
                    throw new RuntimeException("Not implemented yet!");
                } catch (Throwable t) {
                    reply.writeLong(0);
                    reply.writeByte(TagConstants.OBJECT);
                    reply.writeLong(Ids.getIdAsLong(t));
                }
                return reply;
            }
        }

        static class DISABLE_COLLECTION {
            public static final int ID = 7;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long objectId = input.readLong();
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                StaticObject object = (StaticObject) Ids.fromId((int) objectId);
                GCPrevention.disableGC(object);
                return reply;
            }
        }

        static class ENABLE_COLLECTION {
            public static final int ID = 8;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long objectId = input.readLong();
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                StaticObject object = (StaticObject) Ids.fromId((int) objectId);
                GCPrevention.enableGC(object);
                return reply;
            }
        }

        static class IS_COLLECTED {
            public static final int ID = 9;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long objectId = input.readLong();
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                StaticObject object = (StaticObject) Ids.fromId((int) objectId);
                reply.writeBoolean(object == StaticObject.NULL);
                return reply;
            }
        }
    }

    static class StringReference {
        public static final int ID = 10;

        static class VALUE {
            public static final int ID = 1;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long objectId = input.readLong();
                StaticObject string = (StaticObject) Ids.fromId((int) objectId);

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                if (string == StaticObject.NULL) {
                    reply.writeString("null");
                } else {
                    reply.writeString(string.asString());
                }
                return reply;
            }
        }
    }

    static class ThreadReference {
        public static final int ID = 11;

        static class NAME {
            public static final int ID = 1;

            static PacketStream createReply(Packet packet, EspressoContext context) {
                PacketStream input = new PacketStream(packet);
                long threadId = input.readLong();
                StaticObject thread = (StaticObject) Ids.fromId((int)threadId);
                String threadName = context.getMeta().Thread_name.get(thread).toString();

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeString(threadName);
                return reply;
            }
        }

        static class RESUME {
            public static final int ID = 3;

            static PacketStream createReply(Packet packet, JDWPDebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                long threadId = input.readLong();
                StaticObject thread = (StaticObject) Ids.fromId((int)threadId);
                controller.resume(); // TODO(Gregersen) - resume the specified thread
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                return reply;
            }
        }

        static class STATUS {
            public static final int ID = 4;

            static PacketStream createReply(Packet packet, EspressoContext context) {
                PacketStream input = new PacketStream(packet);
                long threadId = input.readLong();
                StaticObject thread = (StaticObject) Ids.fromId((int)threadId);
                int espressoThreadState = (int) context.getMeta().Thread_state.get(thread);
                int threadStatus = getThreadStatus(espressoThreadState);
                //System.out.println("thread state for thread " + thread + " is: " + threadStatus);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeInt(threadStatus);
                //System.out.println("suspended thread? " + ThreadSuspension.isSuspended(thread));
                reply.writeInt(ThreadSuspension.isSuspended(thread));
                return reply;
            }

            private static int getThreadStatus(int espressoThreadState) {
                switch (espressoThreadState) {
                    case 0:
                    case 4:
                        return ThreadStatusConstants.RUNNING;
                    case 16:
                    case 32:
                        return ThreadStatusConstants.WAIT;
                    case 1024:
                        return ThreadStatusConstants.MONITOR;
                    case 2:
                        return ThreadStatusConstants.ZOMBIE;
                    default: return ThreadStatusConstants.ZOMBIE;
                }
            }
        }

        static class THREAD_GROUP {
            public static final int ID = 5;

            static PacketStream createReply(Packet packet, EspressoContext context) {
                PacketStream input = new PacketStream(packet);
                long threadId = input.readLong();
                StaticObject thread = (StaticObject) Ids.fromId((int)threadId);

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                StaticObject threadGroup = (StaticObject) context.getMeta().Thread_group.get(thread);
                reply.writeLong(Ids.getIdAsLong(threadGroup));
                return reply;
            }
        }

        static class FRAMES {
            public static final int ID = 6;

            static PacketStream createReply(Packet packet, JDWPDebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                long threadId = input.readLong();
                int startFrame = input.readInt();
                int length = input.readInt();

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                JDWPCallFrame[] frames = controller.getSuspendedInfo().getStackFrames();
                if (length == -1) {
                    length = frames.length;
                }
                reply.writeInt(length);

                for (int i = startFrame; i < startFrame + length; i++) {
                    JDWPCallFrame frame = frames[i];
                    reply.writeLong(Ids.getIdAsLong(frame));
                    reply.writeByte(frame.getTypeTag());
                    reply.writeLong(frame.getClassId());
                    reply.writeLong(frame.getMethodId());
                    reply.writeLong(frame.getCodeIndex());
                }
                return reply;
            }
        }

        static class FRAME_COUNT {
            public static final int ID = 7;

            static PacketStream createReply(Packet packet, JDWPDebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                long threadId = input.readLong();

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                // verify that we're replying for the currently suspended thread
                if (Ids.fromId((int) threadId) == StaticObject.NULL) {
                    reply.errorCode(20); // TODO(Gregersen) - setup and use error code constant
                    return reply;
                }
                if (controller.getSuspendedInfo().getThread() != Ids.fromId((int) threadId)) {
                    reply.errorCode(10); // TODO(Gregersen) - setup and use error code constant
                    return reply;
                }

                reply.writeInt(controller.getSuspendedInfo().getStackFrames().length);
                return reply;
            }
        }

        static class SUSPEND_COUNT {
            public static final int ID = 12;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long threadId = input.readLong();
                StaticObject thread = (StaticObject) Ids.fromId((int)threadId);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                int suspensionCount = ThreadSuspension.getSuspensionCount(thread);
                //System.out.println("Suspension count: " + suspensionCount);
                reply.writeInt(suspensionCount);
                return reply;
            }
        }
    }

    static class ThreadGroupReference {
        public static final int ID = 12;

        static class NAME {
            public static final int ID = 1;

            static PacketStream createReply(Packet packet, EspressoContext context) {
                PacketStream input = new PacketStream(packet);
                long threadGroupId = input.readLong();
                StaticObject threadGroup = (StaticObject) Ids.fromId((int)threadGroupId);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeString("threadGroup-1"); // TODO(Gregersen) - implement retrieving threadgroup name
                return reply;
            }
        }
    }

    static class ArrayReference {
        public static final int ID = 13;

        static class LENGTH {
            public static final int ID = 1;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long arrayId = input.readLong();
                StaticObject array = (StaticObject) Ids.fromId((int)arrayId);

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeInt(array.length());
                return reply;
            }
        }

        static class GET_VALUES {
            public static final int ID = 2;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long arrayId = input.readLong();
                int index = input.readInt();
                int length = input.readInt();

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                StaticObject array = (StaticObject) Ids.fromId((int)arrayId);
                ArrayKlass arrayKlass = (ArrayKlass) array.getKlass();
                byte tag = TagConstants.fromJavaKind(arrayKlass.getComponentType().getJavaKind());
                boolean tagged = false;

                if (arrayKlass.getDimension() > 1) {
                    tag = TagConstants.ARRAY;
                    tagged = true;
                }
                if (tag == TagConstants.OBJECT) {
                    tagged = true;
                    if (JAVA_LANG_STRING.equals(arrayKlass.getComponentType().getType().toString())) {
                        tag = TagConstants.STRING;
                    }
                }
                reply.writeByte(tag);
                reply.writeInt(length);
                for (int i = index; i < index + length; i++) {
                    Object theValue;
                    if (!tagged) {
                        // primitive array type needs wrapping
                        Object boxedArray = array.unwrap();
                        theValue = Array.get(boxedArray, i);
                    } else {
                        theValue = array.get(i);
                    }
                    writeValue(tag, theValue, reply, tagged);
                }
                return reply;
            }
        }

        static class SET_VALUES {
            public static final int ID = 3;

            static PacketStream createReply(Packet packet, Meta meta) {
                PacketStream input = new PacketStream(packet);

                long arrayId = input.readLong();
                int index = input.readInt();
                int values = input.readInt();

                StaticObject array = (StaticObject) Ids.fromId((int) arrayId);
                byte tag = TagConstants.fromJavaKind(array.getKlass().getComponentType().getJavaKind());
                if (tag == TagConstants.OBJECT) {
                    if (JAVA_LANG_STRING.equals(array.getKlass().getComponentType().getType().toString())) {
                        tag = TagConstants.STRING;
                    }
                }
                if (((ArrayKlass) array.getKlass()).getDimension() > 1) {
                    tag = TagConstants.ARRAY;
                }
                setArrayValues(meta, input, index, values, array, tag);

                return new PacketStream().replyPacket().id(packet.id);
            }

            private static void setArrayValues(Meta meta, PacketStream input, int index, int values, StaticObject array, byte tag) {
                for (int i = index; i < index + values; i++) {
                    switch (tag) {
                        case TagConstants.BOOLEAN:
                            boolean bool = input.readBoolean();
                            boolean[] boolArray = array.unwrap();
                            boolArray[i] = bool;
                            break;
                        case TagConstants.BYTE:
                            byte b = input.readByte();
                            byte[] byteArray = array.unwrap();
                            byteArray[i] = b;
                            break;
                        case TagConstants.SHORT:
                            short s = input.readShort();
                            short[] shortArray = array.unwrap();
                            shortArray[i] = s;
                            break;
                        case TagConstants.CHAR:
                            char c = input.readChar();
                            char[] charArray = array.unwrap();
                            charArray[i] = c;
                            break;
                        case TagConstants.INT:
                            int j = input.readInt();
                            int[] intArray = array.unwrap();
                            intArray[i] = j;
                            break;
                        case TagConstants.FLOAT:
                            float f = input.readFloat();
                            float[] floatArray = array.unwrap();
                            floatArray[i] = f;
                            break;
                        case TagConstants.LONG:
                            long l = input.readLong();
                            long[] longArray = array.unwrap();
                            longArray[i] = l;
                            break;
                        case TagConstants.DOUBLE:
                            double d = input.readDouble();
                            double[] doubleArray = array.unwrap();
                            doubleArray[i] = d;
                            break;
                        case TagConstants.ARRAY:
                        case TagConstants.STRING:
                        case TagConstants.OBJECT:
                            StaticObject so = (StaticObject) Ids.fromId((int)input.readLong());
                            array.putObject(so, i, meta);
                            break;
                        default: throw EspressoError.shouldNotReachHere();
                    }
                }
            }
        }
    }

    static class ClassLoaderReference {
        public static final int ID = 14;

        static class VISIBLE_CLASSES {

            public static final int ID = 1;

            static PacketStream createReply(Packet packet, EspressoContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long classLoaderId = input.readLong();

                StaticObject classLoader = (StaticObject) Ids.fromId((int) classLoaderId);

                // TODO(Gregersen) - we will need all classes for which this classloader was the initiating loader
                Klass[] klasses = context.getRegistries().getLoadedClassesByLoader(classLoader);

                reply.writeInt(klasses.length);

                for (Klass klass : klasses) {
                    reply.writeByte(TypeTag.getKind(klass));
                    reply.writeLong(Ids.getIdAsLong(klass));
                }
                return reply;
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

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadId = input.readLong();
                long frameId = input.readLong();
                int slots = input.readInt();
                reply.writeInt(slots);

                JDWPCallFrame frame = (JDWPCallFrame) Ids.fromId((int)frameId);
                StaticObject thisValue = frame.getThisValue();
                Object[] variables = frame.getVariables();
                int offset = thisValue != null ? 1 : 0;

                // below assumes the debugger asks for slot values in increasing order
                for (int i = 0; i < slots; i++) {
                    int slot = input.readInt();
                    Object value = variables[slot - offset];

                    byte sigbyte = input.readByte();
                    if (sigbyte == TagConstants.ARRAY) {
                        // Array type
                        reply.writeByte(TagConstants.ARRAY);
                        reply.writeLong(Ids.getIdAsLong(value));
                    } else if (sigbyte == TagConstants.OBJECT) {
                        if (value instanceof StaticObject) {
                            StaticObject staticObject = (StaticObject) value;
                            if (JAVA_LANG_STRING.equals(staticObject.getKlass().getType().toString())) {
                                sigbyte = TagConstants.STRING;
                            }
                            writeValue(sigbyte, value, reply, true);
                        }
                    } else {
                        writeValue(sigbyte, value, reply, true);
                    }
                    // TODO(Gregersen) - verify sigbyte against actual value type
                }
                return reply;
            }
        }

        static class THIS_OBJECT {
            public static final int ID = 3;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long threadId = input.readLong();
                long frameId = input.readLong();

                JDWPCallFrame frame = (JDWPCallFrame) Ids.fromId((int)frameId);
                StaticObject thisValue = frame.getThisValue();
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeByte(TagConstants.OBJECT);

                if (thisValue != null) {
                    reply.writeLong(Ids.getIdAsLong(thisValue));
                }
                else {
                    reply.writeLong(0);
                }
                return reply;
            }
        }
    }

    static class ClassObjectReference {
        public static final int ID = 17;

        static class REFLECTED_TYPE {
            public static final int ID = 1;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long classObjectId = input.readLong();

                ClassObjectId id = (ClassObjectId) Ids.fromId((int)classObjectId);

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeByte(TypeTag.getKind(id.getRefType()));
                reply.writeLong(Ids.getIdAsLong(id.getRefType()));
                return reply;
            }
        }
    }

    private static Object readValue(byte valueKind, PacketStream input) {
        switch (valueKind) {
            case TagConstants.BOOLEAN: return input.readBoolean();
            case TagConstants.BYTE: return input.readByte();
            case TagConstants.SHORT: return input.readShort();
            case TagConstants.CHAR: return input.readChar();
            case TagConstants.INT: return input.readInt();
            case TagConstants.FLOAT: return input.readFloat();
            case TagConstants.LONG: return input.readLong();
            case TagConstants.DOUBLE: return input.readDouble();
            case TagConstants.ARRAY:
            case TagConstants.STRING:
            case TagConstants.OBJECT: return Ids.fromId((int)input.readLong());
            default: throw EspressoError.shouldNotReachHere();
        }
    }

    private static void writeValue(byte tag, Object value, PacketStream reply, boolean tagged) {
        if (tagged) {
            reply.writeByte(tag);
        }
        switch (tag) {
            case TagConstants.BOOLEAN:
                reply.writeBoolean((boolean)value);
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
                    reply.writeFloat((float) unboxed);
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
                    reply.writeDouble((double) unboxed);
                } else {
                    reply.writeDouble((double) value);
                }
                break;
            case TagConstants.OBJECT:
            case TagConstants.STRING:
            case TagConstants.ARRAY:
                reply.writeLong(Ids.getIdAsLong(value));
                break;
            default: throw EspressoError.shouldNotReachHere();
        }
    }
}
