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
import com.oracle.truffle.espresso.jdwp.api.CallFrame;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;
import com.oracle.truffle.espresso.jdwp.api.LineNumberTableRef;
import com.oracle.truffle.espresso.jdwp.api.LocalRef;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.jdwp.api.TagConstants;

import java.util.Collections;
import java.util.concurrent.Callable;

import static com.oracle.truffle.espresso.jdwp.api.TagConstants.BOOLEAN;

final class JDWP {

    public static final String JAVA_LANG_OBJECT = "Ljava/lang/Object;";
    public static final Object INVALID_VALUE = new Object();

    private JDWP() {
    }

    static class VirtualMachine {
        public static final int ID = 1;

        static class VERSION {
            public static final int ID = 1;

            static CommandResult createReply(Packet packet, com.oracle.truffle.espresso.jdwp.impl.VirtualMachine vm) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeString(vm.getVmDescription());
                reply.writeInt(1);
                reply.writeInt(8);
                reply.writeString(vm.getVmVersion());
                reply.writeString(vm.getVmName());
                return new CommandResult(reply);
            }
        }

        static class CLASSES_BY_SIGNATURE {
            public static final int ID = 2;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                // get the requested classes
                PacketStream input = new PacketStream(packet);
                String signature = input.readString();
                // we know it's a class type in the format Lsomething;
                String slashName = signature.substring(1, signature.length() - 1);
                KlassRef[] loaded = context.findLoadedClass(slashName);

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeInt(loaded.length);
                for (KlassRef klass : loaded) {
                    reply.writeByte(TypeTag.getKind(klass));
                    reply.writeLong(context.getIds().getIdAsLong(klass));
                    reply.writeInt(klass.getStatus());
                }
                return new CommandResult(reply);
            }
        }

        static class ALL_THREADS {
            public static final int ID = 4;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                Object[] allThreads = context.getAllGuestThreads();
                reply.writeInt(allThreads.length);

                for (Object t : allThreads) {
                    reply.writeLong(context.getIds().getIdAsLong(t));
                }
                return new CommandResult(reply);
            }
        }

        static class TOP_LEVEL_THREAD_GROUPS {
            public static final int ID = 5;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                Object[] threadGroups = context.getTopLevelThreadGroups();
                reply.writeInt(threadGroups.length);
                for (Object threadGroup : threadGroups) {
                    reply.writeLong(context.getIds().getIdAsLong(threadGroup));
                }
                return new CommandResult(reply);
            }
        }

        static class DISPOSE {
            public static final int ID = 6;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                return new CommandResult(reply, Collections.singletonList(new Callable<Void>() {
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

            static CommandResult createReply(Packet packet, com.oracle.truffle.espresso.jdwp.impl.VirtualMachine vm) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeInt(vm.getSizeOfFieldRef());
                reply.writeInt(vm.getSizeOfMethodRef());
                reply.writeInt(vm.getSizeofObjectRef());
                reply.writeInt(vm.getSizeOfClassRef());
                reply.writeInt(vm.getSizeOfFrameRef());
                return new CommandResult(reply);
            }
        }

        static class SUSPEND {
            public static final int ID = 8;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                JDWPLogger.log("Suspend all packet", JDWPLogger.LogLevel.THREAD);

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                controller.suspendAll();
                return new CommandResult(reply);
            }
        }

        static class RESUME {
            public static final int ID = 9;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                JDWPLogger.log("Resume all packet", JDWPLogger.LogLevel.THREAD);

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                controller.resumeAll(false);
                return new CommandResult(reply);
            }
        }

        static class CREATE_STRING {
            public static final int ID = 11;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                String utf = input.readString();
                // we must create a new StaticObject instance representing the String

                Object string = context.toGuestString(utf);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeLong(context.getIds().getIdAsLong(string));
                return new CommandResult(reply);
            }
        }

        static class CAPABILITIES {
            public static final int ID = 12;

            static CommandResult createReply(Packet packet) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeBoolean(true); // canWatchFieldModification
                reply.writeBoolean(true); // canWatchFieldAccess
                reply.writeBoolean(false); // canGetBytecodes
                reply.writeBoolean(true); // canGetSyntheticAttribute
                reply.writeBoolean(false); // canGetOwnedMonitorInfo
                reply.writeBoolean(false); // canGetCurrentContendedMonitor
                reply.writeBoolean(false); // canGetMonitorInfo
                return new CommandResult(reply);
            }
        }

        static class DISPOSE_OBJECTS {
            public static final int ID = 14;

            static CommandResult createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                // currently, we don't free up IDs
                int count = input.readInt();
                for (int i = 0; i < count; i++) {
                    /* long objectID = */ input.readLong();
                    /* int refCount = */ input.readInt();
                }
                return new CommandResult(reply);
            }
        }

        static class CAPABILITIES_NEW {
            public static final int ID = 17;

            static CommandResult createReply(Packet packet) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

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
                return new CommandResult(reply);
            }
        }

        static class SET_DEFAULT_STRATUM {
            public static final int ID = 19;

            static CommandResult createReply(Packet packet) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id).errorCode(ErrorCodes.NOT_IMPLEMENTED);
                return new CommandResult(reply);
            }
        }

        static class ALL_CLASSES_WITH_GENERIC {
            public static final int ID = 20;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                KlassRef[] allLoadedClasses = context.getAllLoadedClasses();
                reply.writeInt(allLoadedClasses.length);

                for (KlassRef klass : allLoadedClasses) {
                    reply.writeByte(TypeTag.getKind(klass));
                    reply.writeLong(context.getIds().getIdAsLong(klass));
                    reply.writeString(klass.getTypeAsString());
                    // TODO(Gregersen) - generic signature if any
                    // tracked by /browse/GR-19818
                    reply.writeString("");
                    reply.writeInt(klass.getStatus());
                }

                return new CommandResult(reply);
            }
        }

        static class INSTANCE_COUNTS {
            public static final int ID = 21;

            static CommandResult createReply(Packet packet) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id).errorCode(ErrorCodes.NOT_IMPLEMENTED);
                return new CommandResult(reply);
            }
        }
    }

    static class ReferenceType {
        public static final int ID = 2;

        static class SIGNATURE {
            public static final int ID = 1;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long refTypeId = input.readLong();
                KlassRef klass = verifyRefType(refTypeId, reply, context);
                if (klass == null) {
                    return new CommandResult(reply);
                }

                reply.writeString(klass.getTypeAsString());
                return new CommandResult(reply);
            }
        }

        static class CLASSLOADER {
            public static final int ID = 2;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long refTypeId = input.readLong();
                KlassRef klass = verifyRefType(refTypeId, reply, context);
                if (klass == null) {
                    return new CommandResult(reply);
                }

                Object loader = klass.getDefiningClassLoader();
                if (loader == null || loader == context.getNullObject()) { // system class loader
                    reply.writeLong(0);
                } else {
                    reply.writeLong(context.getIds().getIdAsLong(loader));
                }
                return new CommandResult(reply);
            }
        }

        static class MODIFIERS {
            public static final int ID = 3;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long refTypeId = input.readLong();
                KlassRef klass = verifyRefType(refTypeId, reply, context);
                if (klass == null) {
                    return new CommandResult(reply);
                }

                reply.writeInt(klass.getModifiers());

                return new CommandResult(reply);
            }
        }

        static class FIELDS {
            public static final int ID = 4;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long refTypeId = input.readLong();
                KlassRef klass = verifyRefType(refTypeId, reply, context);

                if (klass == null) {
                    return new CommandResult(reply);
                }

                // check if class has been prepared
                if (klass.getStatus() < ClassStatusConstants.PREPARED) {
                    reply.errorCode(ErrorCodes.CLASS_NOT_PREPARED);
                    return new CommandResult(reply);
                }

                FieldRef[] declaredFields = klass.getDeclaredFields();
                int numDeclaredFields = declaredFields.length;
                reply.writeInt(numDeclaredFields);
                for (FieldRef field : declaredFields) {
                    reply.writeLong(context.getIds().getIdAsLong(field));
                    reply.writeString(field.getNameAsString());
                    reply.writeString(field.getTypeAsString());
                    reply.writeInt(field.getModifiers());
                }
                return new CommandResult(reply);
            }
        }

        static class METHODS {
            public static final int ID = 5;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long refTypeId = input.readLong();
                KlassRef klass = verifyRefType(refTypeId, reply, context);

                if (klass == null) {
                    return new CommandResult(reply);
                }

                // check if class has been prepared
                if (klass.getStatus() < ClassStatusConstants.PREPARED) {
                    reply.errorCode(ErrorCodes.CLASS_NOT_PREPARED);
                    return new CommandResult(reply);
                }

                MethodRef[] declaredMethods = klass.getDeclaredMethods();
                int numDeclaredMethods = declaredMethods.length;
                reply.writeInt(numDeclaredMethods);
                for (MethodRef method : declaredMethods) {
                    reply.writeLong(context.getIds().getIdAsLong(method));
                    reply.writeString(method.getNameAsString());
                    reply.writeString(method.getSignatureAsString());
                    reply.writeInt(method.getModifiers());
                }
                return new CommandResult(reply);
            }
        }

        static class GET_VALUES {
            public static final int ID = 6;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long refTypeId = input.readLong();

                if (verifyRefType(refTypeId, reply, context) == null) {
                    return new CommandResult(reply);
                }

                int fields = input.readInt();
                reply.writeInt(fields);

                for (int i = 0; i < fields; i++) {
                    long fieldId = input.readLong();
                    FieldRef field = verifyFieldRef(fieldId, reply, context);

                    if (field == null) {
                        return new CommandResult(reply);
                    }

                    // check if class has been initialized
                    // if not, we're probably suspended in <clinit>
                    // and should not try to read static field values
                    Object value;
                    if (field.getDeclaringKlass().getStatus() != ClassStatusConstants.INITIALIZED) {
                        value = null;
                    } else {
                        value = context.getStaticFieldValue(field);
                    }

                    byte tag = context.getTag(value);

                    writeValue(tag, value, reply, true, context);
                }
                return new CommandResult(reply);
            }
        }

        static class SOURCE_FILE {
            public static final int ID = 7;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long refTypeId = input.readLong();

                KlassRef klass = verifyRefType(refTypeId, reply, context);
                if (klass == null) {
                    return new CommandResult(reply);
                }

                String sourceFile = null;
                MethodRef[] methods = klass.getDeclaredMethods();
                for (MethodRef method : methods) {
                    // we need only look at one method to find
                    // the source file of the declaring class
                    if (!method.hasSourceFileAttribute()) {
                        reply.errorCode(ErrorCodes.ABSENT_INFORMATION);
                        return new CommandResult(reply);
                    }
                    sourceFile = method.getSourceFile();
                    break;
                }
                if (sourceFile == null) {
                    reply.errorCode(ErrorCodes.ABSENT_INFORMATION);
                    return new CommandResult(reply);
                }
                reply.writeString(sourceFile);
                return new CommandResult(reply);
            }
        }

        static class STATUS {
            public static final int ID = 9;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long klassRef = input.readLong();
                KlassRef klass = verifyRefType(klassRef, reply, context);

                if (klass == null) {
                    return new CommandResult(reply);
                }

                reply.writeInt(klass.getStatus());
                return new CommandResult(reply);
            }

        }

        static class INTERFACES {
            public static final int ID = 10;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                long refTypeId = input.readLong();

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                KlassRef klass = verifyRefType(refTypeId, reply, context);

                if (klass == null) {
                    return new CommandResult(reply);
                }

                KlassRef[] interfaces = klass.getImplementedInterfaces();

                reply.writeInt(interfaces.length);
                for (KlassRef itf : interfaces) {
                    reply.writeLong(context.getIds().getIdAsLong(itf));
                }
                return new CommandResult(reply);
            }
        }

        static class CLASS_OBJECT {
            public static final int ID = 11;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                long refTypeId = input.readLong();

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                KlassRef klass = verifyRefType(refTypeId, reply, context);

                if (klass == null) {
                    return new CommandResult(reply);
                }

                Object classObject = klass.getKlassObject();

                reply.writeLong(context.getIds().getIdAsLong(classObject));
                return new CommandResult(reply);
            }
        }

        static class SIGNATURE_WITH_GENERIC {
            public static final int ID = 13;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                long refTypeId = input.readLong();

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                KlassRef klass = verifyRefType(refTypeId, reply, context);

                if (klass == null) {
                    return new CommandResult(reply);
                }

                reply.writeString(klass.getTypeAsString());
                // TODO(Gregersen) - generic signature
                // tracked by /browse/GR-19818
                reply.writeString("");
                return new CommandResult(reply);
            }
        }

        static class FIELDS_WITH_GENERIC {
            public static final int ID = 14;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long refTypeId = input.readLong();
                KlassRef klass = verifyRefType(refTypeId, reply, context);

                if (klass == null) {
                    return new CommandResult(reply);
                }

                // check if class has been prepared
                if (klass.getStatus() < ClassStatusConstants.PREPARED) {
                    reply.errorCode(ErrorCodes.CLASS_NOT_PREPARED);
                    return new CommandResult(reply);
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
                return new CommandResult(reply);
            }
        }

        static class METHODS_WITH_GENERIC {
            public static final int ID = 15;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long refTypeId = input.readLong();
                KlassRef klass = verifyRefType(refTypeId, reply, context);

                if (klass == null) {
                    return new CommandResult(reply);
                }

                // check if class has been prepared
                if (klass.getStatus() < ClassStatusConstants.PREPARED) {
                    reply.errorCode(ErrorCodes.CLASS_NOT_PREPARED);
                    return new CommandResult(reply);
                }

                MethodRef[] declaredMethods = klass.getDeclaredMethods();
                int numDeclaredMethods = declaredMethods.length;
                reply.writeInt(numDeclaredMethods);
                for (MethodRef method : declaredMethods) {
                    reply.writeLong(context.getIds().getIdAsLong(method));
                    reply.writeString(method.getNameAsString());
                    reply.writeString(method.getSignatureAsString());
                    // TODO(Gregersen) - get the generic signature
                    // tracked by /browse/GR-19818
                    reply.writeString("");
                    reply.writeInt(method.getModifiers());
                }
                return new CommandResult(reply);
            }
        }
    }

    static class ClassType {
        public static final int ID = 3;

        static class SUPERCLASS {

            public static final int ID = 1;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long classId = input.readLong();
                KlassRef klassRef = verifyRefType(classId, reply, context);

                if (klassRef == null) {
                    return new CommandResult(reply);
                }

                boolean isJavaLangObject = JAVA_LANG_OBJECT.equals(klassRef.getTypeAsString());

                if (isJavaLangObject) {
                    reply.writeLong(0);
                } else {
                    KlassRef superKlass = klassRef.getSuperClass();
                    reply.writeLong(context.getIds().getIdAsLong(superKlass));
                }
                return new CommandResult(reply);
            }
        }

        static class SET_VALUES {

            public static final int ID = 2;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long classId = input.readLong();
                KlassRef klass = verifyRefType(classId, reply, context);

                if (klass == null) {
                    return new CommandResult(reply);
                }

                // check if class has been prepared
                if (klass.getStatus() < ClassStatusConstants.PREPARED) {
                    reply.errorCode(ErrorCodes.CLASS_NOT_PREPARED);
                    return new CommandResult(reply);
                }

                int values = input.readInt();

                for (int i = 0; i < values; i++) {
                    long fieldId = input.readLong();
                    FieldRef field = verifyFieldRef(fieldId, reply, context);

                    if (field == null) {
                        return new CommandResult(reply);
                    }

                    byte tag = field.getTagConstant();

                    if (tag == TagConstants.OBJECT) {
                        tag = context.getTag(field.getTypeAsString());
                    }
                    Object value = readValue(tag, input, context);
                    context.setStaticFieldValue(field, value);
                }
                return new CommandResult(reply);
            }
        }

        static class INVOKE_METHOD {

            public static final int ID = 3;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                JDWPContext context = controller.getContext();

                KlassRef klass = verifyRefType(input.readLong(), reply, context);
                if (klass == null) {
                    return new CommandResult(reply);
                }

                Object thread = verifyThread(input.readLong(), reply, context);
                if (thread == null) {
                    return new CommandResult(reply);
                }

                MethodRef method = verifyMethodRef(input.readLong(), reply, context);
                if (method == null) {
                    return new CommandResult(reply);
                }

                JDWPLogger.log("trying to invoke static method: %s", JDWPLogger.LogLevel.PACKET, method.getNameAsString());

                int arguments = input.readInt();

                Object[] args = new Object[arguments];
                for (int i = 0; i < arguments; i++) {
                    byte valueKind = input.readByte();
                    args[i] = readValue(valueKind, input, context);
                }

                /* int invocationOptions = */ input.readInt();
                try {
                    // we have to call the method in the correct thread, so post a
                    // Callable to the controller and wait for the result to appear
                    ThreadJob job = new ThreadJob(thread, new Callable<Object>() {

                        @Override
                        public Object call() throws Exception {
                            return method.invokeMethod(null, args);
                        }
                    });
                    controller.postJobForThread(job);
                    ThreadJob.JobResult result = job.getResult();

                    writeMethodResult(reply, context, result);
                } catch (Throwable t) {
                    throw new RuntimeException("not able to invoke static method through jdwp", t);
                }

                return new CommandResult(reply);
            }
        }

        static class NEW_INSTANCE {

            public static final int ID = 4;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                PacketStream input = new PacketStream(packet);

                JDWPContext context = controller.getContext();

                KlassRef klass = verifyRefType(input.readLong(), reply, context);
                if (klass == null) {
                    return new CommandResult(reply);
                }

                Object thread = verifyThread(input.readLong(), reply, context);
                if (thread == null) {
                    return new CommandResult(reply);
                }

                MethodRef method = verifyMethodRef(input.readLong(), reply, context);
                if (method == null) {
                    JDWPLogger.log("not a valid method", JDWPLogger.LogLevel.PACKET);
                    return new CommandResult(reply);
                }

                JDWPLogger.log("trying to invoke constructor in klass: %s", JDWPLogger.LogLevel.PACKET, klass.getNameAsString());

                int arguments = input.readInt();

                Object[] args = new Object[arguments];
                for (int i = 0; i < arguments; i++) {
                    byte valueKind = input.readByte();
                    args[i] = readValue(valueKind, input, context);
                }

                /* int invocationOptions = */ input.readInt();
                try {
                    // we have to call the method in the correct thread, so post a
                    // Callable to the controller and wait for the result to appear
                    ThreadJob job = new ThreadJob(thread, new Callable<Object>() {

                        @Override
                        public Object call() throws Exception {
                            return method.invokeMethod(null, args);
                        }
                    });
                    controller.postJobForThread(job);
                    ThreadJob.JobResult result = job.getResult();

                    writeMethodResult(reply, context, result);
                } catch (Throwable t) {
                    throw new RuntimeException("not able to invoke static method through jdwp", t);
                }

                return new CommandResult(reply);
            }
        }
    }

    static class ArrayType {
        public static final int ID = 4;

        static class NEW_INSTANCE {
            public static final int ID = 1;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                PacketStream input = new PacketStream(packet);

                KlassRef klass = verifyRefType(input.readLong(), reply, context);

                if (klass == null) {
                    return new CommandResult(reply);
                }

                int length = input.readInt();
                Object newArray = context.newArray(klass, length);

                reply.writeByte(TagConstants.ARRAY);
                reply.writeLong(context.getIds().getIdAsLong(newArray));

                return new CommandResult(reply);
            }
        }
    }

    static class InterfaceType {
        public static final int ID = 5;

        static class INVOKE_METHOD {
            public static final int ID = 1;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                PacketStream input = new PacketStream(packet);
                JDWPContext context = controller.getContext();

                KlassRef itf = verifyRefType(input.readLong(), reply, context);

                if (itf == null) {
                    return new CommandResult(reply);
                }

                if (!itf.isInterface()) {
                    reply.errorCode(ErrorCodes.INVALID_CLASS);
                    return new CommandResult(reply);
                }

                Object thread = verifyThread(input.readLong(), reply, context);
                if (thread == null) {
                    return new CommandResult(reply);
                }

                MethodRef method = verifyMethodRef(input.readLong(), reply, context);
                if (method == null) {
                    return new CommandResult(reply);
                }

                JDWPLogger.log("trying to invoke interface method: %s", JDWPLogger.LogLevel.PACKET, method.getNameAsString());

                int arguments = input.readInt();

                Object[] args = new Object[arguments];
                for (int i = 0; i < arguments; i++) {
                    byte valueKind = input.readByte();
                    args[i] = readValue(valueKind, input, context);
                }

                /* int invocationOptions = */ input.readInt();
                try {
                    // we have to call the method in the correct thread, so post a
                    // Callable to the controller and wait for the result to appear
                    ThreadJob job = new ThreadJob(thread, new Callable<Object>() {

                        @Override
                        public Object call() throws Exception {
                            return method.invokeMethod(null, args);
                        }
                    });
                    controller.postJobForThread(job);
                    ThreadJob.JobResult result = job.getResult();

                    if (result.getException() != null) {
                        JDWPLogger.log("interface method threw exception", JDWPLogger.LogLevel.PACKET);
                        reply.writeByte(TagConstants.OBJECT);
                        reply.writeLong(0);
                        reply.writeByte(TagConstants.OBJECT);
                        reply.writeLong(context.getIds().getIdAsLong(result.getException()));
                    } else {
                        Object value = context.toGuest(result.getResult());
                        JDWPLogger.log("Got converted result from interface method invocation: %s", JDWPLogger.LogLevel.PACKET, value);
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
                    throw new RuntimeException("not able to invoke interface method through jdwp", t);
                }
                return new CommandResult(reply);
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

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                PacketStream input = new PacketStream(packet);
                long refTypeId = input.readLong();
                long methodId = input.readLong();

                KlassRef klass = verifyRefType(refTypeId, reply, context);

                if (klass == null) {
                    return new CommandResult(reply);
                }

                MethodRef method = verifyMethodRef(methodId, reply, context);

                if (method == null) {
                    return new CommandResult(reply);
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
                return new CommandResult(reply);
            }
        }

        // TODO(Gregersen) - current disabled by Capabilities.
        // tracked by /browse/GR-19817
        // Enabling causes the NetBeans debugger to send wrong stepping
        // events for step into/over so disabled for now. Perhaps the bytecode
        // returned from method.getCode() is incorrect?
        static class BYTECODES {
            public static final int ID = 3;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                KlassRef klassRef = verifyRefType(input.readLong(), reply, context); // ref type
                if (klassRef == null) {
                    return new CommandResult(reply);
                }

                long methodId = input.readLong();
                MethodRef method = verifyMethodRef(methodId, reply, context);
                if (method == null) {
                    return new CommandResult(reply);
                }

                byte[] code = method.getCode();

                reply.writeInt(code.length);
                reply.writeByteArray(code);

                return new CommandResult(reply);
            }
        }

        static class VARIABLE_TABLE_WITH_GENERIC {
            public static final int ID = 5;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                KlassRef klassRef = verifyRefType(input.readLong(), reply, context); // ref type
                if (klassRef == null) {
                    return new CommandResult(reply);
                }

                long methodId = input.readLong();
                MethodRef method = verifyMethodRef(methodId, reply, context);
                if (method == null) {
                    return new CommandResult(reply);
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
                    // TODO(Gregersen) - generic signature
                    // tracked by /browse/GR-19818
                    reply.writeString("");
                    reply.writeInt(local.getEndBCI() - local.getStartBCI());
                    reply.writeInt(local.getSlot());
                }
                return new CommandResult(reply);
            }
        }
    }

    static class ObjectReference {
        public static final int ID = 9;

        static class REFERENCE_TYPE {
            public static final int ID = 1;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                long objectId = input.readLong();
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                Object object = context.getIds().fromId((int) objectId);

                if (object == context.getNullObject()) {
                    reply.errorCode(ErrorCodes.INVALID_OBJECT);
                    return new CommandResult(reply);
                }

                KlassRef klassRef = context.getRefType(object);

                reply.writeByte(TypeTag.getKind(klassRef));
                reply.writeLong(context.getIds().getIdAsLong(klassRef));

                return new CommandResult(reply);
            }
        }

        static class GET_VALUES {
            public static final int ID = 2;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long objectId = input.readLong();
                Object object = context.getIds().fromId((int) objectId);

                if (object == context.getNullObject()) {
                    reply.errorCode(ErrorCodes.INVALID_OBJECT);
                    return new CommandResult(reply);
                }

                int numFields = input.readInt();

                reply.writeInt(numFields);

                for (int i = 0; i < numFields; i++) {
                    long fieldId = input.readLong();
                    FieldRef field = verifyFieldRef(fieldId, reply, context);

                    if (field == null) {
                        return new CommandResult(reply);
                    }

                    Object value = field.getValue(object);
                    byte tag = field.getTagConstant();

                    if (tag == TagConstants.OBJECT) {
                        tag = context.getTag(value);
                    }
                    writeValue(tag, value, reply, true, context);
                }
                return new CommandResult(reply);
            }
        }

        static class SET_VALUES {
            public static final int ID = 3;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long objectId = input.readLong();
                Object object = context.getIds().fromId((int) objectId);

                if (object == context.getNullObject()) {
                    reply.errorCode(ErrorCodes.INVALID_OBJECT);
                    return new CommandResult(reply);
                }

                int numFields = input.readInt();

                for (int i = 0; i < numFields; i++) {
                    long fieldId = input.readLong();
                    FieldRef field = verifyFieldRef(fieldId, reply, context);

                    if (field == null) {
                        return new CommandResult(reply);
                    }

                    byte tag = field.getTagConstant();
                    if (tag == TagConstants.OBJECT) {
                        tag = context.getTag(field.getTypeAsString());
                    }
                    Object value = readValue(tag, input, context);
                    field.setValue(object, value);
                }
                return new CommandResult(reply);
            }
        }

        static class INVOKE_METHOD {
            public static final int ID = 6;

            static CommandResult createReply(Packet packet, DebuggerController controller) {

                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                JDWPLogger.log("Invoke method through jdwp", JDWPLogger.LogLevel.PACKET);

                JDWPContext context = controller.getContext();

                long objectId = input.readLong();
                long threadId = input.readLong();

                Object thread = verifyThread(threadId, reply, context);

                if (thread == null) {
                    return new CommandResult(reply);
                }

                if (verifyRefType(input.readLong(), reply, context) == null) {
                    return new CommandResult(reply);
                }

                long methodId = input.readLong();
                int arguments = input.readInt();

                Object[] args = new Object[arguments];
                for (int i = 0; i < arguments; i++) {
                    byte valueKind = input.readByte();
                    args[i] = readValue(valueKind, input, context);
                }
                // TODO(Gregersen) - handle invocation options
                // tracked by /browse/GR-19819
                /* int options = */ input.readInt();

                Object callee = context.getIds().fromId((int) objectId);
                MethodRef method = verifyMethodRef(methodId, reply, context);

                if (method == null) {
                    return new CommandResult(reply);
                }

                JDWPLogger.log("trying to invoke method: %s", JDWPLogger.LogLevel.PACKET, method.getNameAsString());

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

                    writeMethodResult(reply, context, result);
                } catch (Throwable t) {
                    throw new RuntimeException("not able to invoke method through jdwp", t);
                }
                return new CommandResult(reply);
            }
        }

        static class DISABLE_COLLECTION {
            public static final int ID = 7;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                long objectId = input.readLong();
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                Object object = controller.getContext().getIds().fromId((int) objectId);

                if (object == controller.getContext().getNullObject()) {
                    reply.errorCode(ErrorCodes.INVALID_OBJECT);
                    return new CommandResult(reply);
                }

                controller.getGCPrevention().disableGC(object);
                return new CommandResult(reply);
            }
        }

        static class ENABLE_COLLECTION {
            public static final int ID = 8;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                long objectId = input.readLong();
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                Object object = controller.getContext().getIds().fromId((int) objectId);

                if (object == controller.getContext().getNullObject()) {
                    reply.errorCode(ErrorCodes.INVALID_OBJECT);
                    return new CommandResult(reply);
                }

                controller.getGCPrevention().enableGC(object);
                return new CommandResult(reply);
            }
        }

        static class IS_COLLECTED {
            public static final int ID = 9;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                long objectId = input.readLong();
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                Object object = context.getIds().fromId((int) objectId);

                if (object == context.getNullObject()) {
                    reply.errorCode(ErrorCodes.INVALID_OBJECT);
                    return new CommandResult(reply);
                }

                reply.writeBoolean(object == context.getNullObject());
                return new CommandResult(reply);
            }
        }
    }

    static class StringReference {
        public static final int ID = 10;

        static class VALUE {
            public static final int ID = 1;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long objectId = input.readLong();
                Object string = verifyString(objectId, reply, context);

                if (string == null) {
                    return new CommandResult(reply);
                }

                if (string == context.getNullObject()) {
                    reply.errorCode(ErrorCodes.INVALID_OBJECT);
                    return new CommandResult(reply);
                } else {
                    reply.writeString(context.getStringValue(string));
                }
                return new CommandResult(reply);
            }
        }
    }

    static class ThreadReference {
        public static final int ID = 11;

        private static final long SUSPEND_TIMEOUT = 200;

        static class NAME {
            public static final int ID = 1;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadId = input.readLong();
                Object thread = verifyThread(threadId, reply, context);

                if (thread == null) {
                    JDWPLogger.log("null thread discovered with ID: %s", JDWPLogger.LogLevel.THREAD, threadId);

                    return new CommandResult(reply);
                }

                String threadName = context.getThreadName(thread);

                reply.writeString(threadName);

                JDWPLogger.log("thread name: %s", JDWPLogger.LogLevel.THREAD, threadName);

                return new CommandResult(reply);
            }
        }

        static class SUSPEND {
            public static final int ID = 2;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadId = input.readLong();
                Object thread = verifyThread(threadId, reply, controller.getContext());

                if (thread == null) {
                    return new CommandResult(reply);
                }

                JDWPLogger.log("suspend thread packet for thread: %s", JDWPLogger.LogLevel.THREAD, controller.getContext().getThreadName(thread));

                controller.suspend(thread);
                return new CommandResult(reply);
            }
        }

        static class RESUME {
            public static final int ID = 3;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadId = input.readLong();
                Object thread = verifyThread(threadId, reply, controller.getContext());

                if (thread == null) {
                    return new CommandResult(reply);
                }

                JDWPLogger.log("resume thread packet for thread: %s", JDWPLogger.LogLevel.THREAD, controller.getContext().getThreadName(thread));

                controller.resume(thread, false);
                return new CommandResult(reply);
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

            public static final int JVMTI_JAVA_LANG_THREAD_STATE_MASK = JVMTI_THREAD_STATE_TERMINATED |
                            JVMTI_THREAD_STATE_ALIVE |
                            JVMTI_THREAD_STATE_RUNNABLE |
                            JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER |
                            JVMTI_THREAD_STATE_WAITING |
                            JVMTI_THREAD_STATE_WAITING_INDEFINITELY |
                            JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                JDWPContext context = controller.getContext();

                long threadId = input.readLong();
                Object thread = verifyThread(threadId, reply, context);

                if (thread == null) {
                    return new CommandResult(reply);
                }

                int jvmtiThreadStatus = context.getThreadStatus(thread);
                int threadStatus = getThreadStatus(jvmtiThreadStatus);
                reply.writeInt(threadStatus);
                int suspended = controller.getThreadSuspension().getSuspensionCount(thread) > 0 ? 1 : 0;
                reply.writeInt(suspended);

                JDWPLogger.log("status command for thread: %s with status: %s, suspended: %s", JDWPLogger.LogLevel.THREAD, context.getThreadName(thread), threadStatus, suspended);

                return new CommandResult(reply, null);
            }

            private static int getThreadStatus(int jvmtiThreadStatus) {
                int masked = jvmtiThreadStatus & JVMTI_JAVA_LANG_THREAD_STATE_MASK;

                if ((masked & JVMTI_THREAD_STATE_TERMINATED) != 0) {
                    return ThreadStatusConstants.ZOMBIE;
                } else if ((masked & JVMTI_THREAD_STATE_ALIVE) != 0) {
                    if ((masked & JVMTI_THREAD_STATE_RUNNABLE) != 0) {
                        return ThreadStatusConstants.RUNNING;
                    } else if ((masked & JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER) != 0) {
                        return ThreadStatusConstants.WAIT;
                    }
                    return ThreadStatusConstants.RUNNING;
                } else if ((masked & JVMTI_THREAD_STATE_WAITING) != 0) {
                    return ThreadStatusConstants.WAIT;
                } else if (masked == 0) {
                    // new threads are returned as running
                    return ThreadStatusConstants.RUNNING;
                }
                return ThreadStatusConstants.RUNNING;
            }
        }

        static class THREAD_GROUP {
            public static final int ID = 5;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadId = input.readLong();
                Object thread = verifyThread(threadId, reply, context);

                if (thread == null) {
                    return new CommandResult(reply);
                }

                Object threadGroup = context.getThreadGroup(thread);
                reply.writeLong(context.getIds().getIdAsLong(threadGroup));
                return new CommandResult(reply);
            }
        }

        static class FRAMES {
            public static final int ID = 6;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadId = input.readLong();
                Object thread = verifyThread(threadId, reply, controller.getContext());

                if (thread == null) {
                    return new CommandResult(reply);
                }

                int startFrame = input.readInt();
                int length = input.readInt();

                JDWPLogger.log("requesting frames for thread: %s", JDWPLogger.LogLevel.THREAD, controller.getContext().getThreadName(thread));
                JDWPLogger.log("startFrame requested: %s", JDWPLogger.LogLevel.THREAD, startFrame);
                JDWPLogger.log("Number of frames requested: %d", JDWPLogger.LogLevel.THREAD, length);

                SuspendedInfo suspendedInfo = controller.getSuspendedInfo(thread);

                if (suspendedInfo == null) {
                    JDWPLogger.log("THREAD_NOT_SUSPENDED: %s", JDWPLogger.LogLevel.THREAD, controller.getContext().getThreadName(thread));
                    reply.errorCode(ErrorCodes.THREAD_NOT_SUSPENDED);
                    return new CommandResult(reply);
                }

                if (suspendedInfo instanceof UnknownSuspendedInfo) {
                    JDWPLogger.log("Unknown suspension info for thread: %s", JDWPLogger.LogLevel.THREAD, controller.getContext().getThreadName(thread));
                    suspendedInfo = awaitSuspendedInfo(controller, thread, suspendedInfo);
                    if (suspendedInfo instanceof UnknownSuspendedInfo) {
                        // we can't return any frames for a not yet suspended thread
                        reply.errorCode(ErrorCodes.THREAD_NOT_SUSPENDED);
                        return new CommandResult(reply);
                    }
                }

                CallFrame[] frames = suspendedInfo.getStackFrames();

                if (length == -1) {
                    length = frames.length;
                }
                reply.writeInt(length);
                JDWPLogger.log("returning %d frames for thread: %s", JDWPLogger.LogLevel.THREAD, length, controller.getContext().getThreadName(thread));

                for (int i = startFrame; i < startFrame + length; i++) {
                    CallFrame frame = frames[i];
                    reply.writeLong(controller.getContext().getIds().getIdAsLong(frame));
                    reply.writeByte(frame.getTypeTag());
                    reply.writeLong(frame.getClassId());
                    reply.writeLong(frame.getMethodId());
                    reply.writeLong(frame.getCodeIndex());
                }
                return new CommandResult(reply);
            }
        }

        static class FRAME_COUNT {
            public static final int ID = 7;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadId = input.readLong();
                Object thread = verifyThread(threadId, reply, controller.getContext());

                if (thread == null) {
                    return new CommandResult(reply);
                }

                SuspendedInfo suspendedInfo = controller.getSuspendedInfo(thread);

                if (suspendedInfo == null) {
                    reply.errorCode(ErrorCodes.THREAD_NOT_SUSPENDED);
                    return new CommandResult(reply);
                }

                if (suspendedInfo instanceof UnknownSuspendedInfo) {
                    suspendedInfo = awaitSuspendedInfo(controller, thread, suspendedInfo);
                }
                int length = suspendedInfo.getStackFrames().length;
                reply.writeInt(suspendedInfo.getStackFrames().length);
                JDWPLogger.log("current frame count: %d for thread: %s", JDWPLogger.LogLevel.THREAD, length, controller.getContext().getThreadName(thread));

                return new CommandResult(reply);
            }
        }

        static class SUSPEND_COUNT {
            public static final int ID = 12;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadId = input.readLong();
                Object thread = verifyThread(threadId, reply, controller.getContext());

                if (thread == null) {
                    return new CommandResult(reply);
                }

                int suspensionCount = controller.getThreadSuspension().getSuspensionCount(thread);
                JDWPLogger.log("suspension count: %d returned for thread: %s", JDWPLogger.LogLevel.THREAD, suspensionCount, controller.getContext().getThreadName(thread));

                reply.writeInt(suspensionCount);
                return new CommandResult(reply);
            }
        }

        private static SuspendedInfo awaitSuspendedInfo(DebuggerController controller, Object thread, SuspendedInfo suspendedInfo) {
            // OK, we hard suspended this thread, but it hasn't yet actually suspended
            // in a code location known to Truffle
            // let's check if the thread is RUNNING and give it a moment to reach
            // the suspended state
            SuspendedInfo result = suspendedInfo;
            Thread hostThread = controller.getContext().asHostThread(thread);
            if (hostThread.getState() == Thread.State.RUNNABLE) {
                JDWPLogger.log("Awaiting suspended info for thread %s", JDWPLogger.LogLevel.THREAD, controller.getContext().getThreadName(thread));

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
                JDWPLogger.log("Still no suspended info for thread %s", JDWPLogger.LogLevel.THREAD, controller.getContext().getThreadName(thread));
            }
            return result;
        }
    }

    static class ThreadGroupReference {
        public static final int ID = 12;

        static class NAME {
            public static final int ID = 1;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadGroupId = input.readLong();
                Object threadGroup = verifyThreadGroup(threadGroupId, reply, context);

                if (threadGroup == null) {
                    return new CommandResult(reply);
                }

                reply.writeString("main");
                return new CommandResult(reply);
            }
        }

        static class PARENT {
            public static final int ID = 2;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadGroupId = input.readLong();
                Object thread = verifyThreadGroup(threadGroupId, reply, context);

                if (thread == null) {
                    return new CommandResult(reply);
                }

                reply.writeLong(0); // no parent thread group
                return new CommandResult(reply);
            }
        }
    }

    static class ArrayReference {
        public static final int ID = 13;

        static class LENGTH {
            public static final int ID = 1;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long arrayId = input.readLong();
                Object array = verifyArray(arrayId, reply, context);

                if (array == null) {
                    return new CommandResult(reply);
                }

                int arrayLength = context.getArrayLength(array);

                reply.writeInt(arrayLength);
                return new CommandResult(reply, null);
            }
        }

        static class GET_VALUES {
            public static final int ID = 2;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                long arrayId = input.readLong();
                int index = input.readInt();
                int length = input.readInt();

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                Object array = verifyArray(arrayId, reply, context);

                if (array == null || !verifyArrayLength(array, length, reply, context)) {
                    return new CommandResult(reply);
                }

                byte tag = context.getTypeTag(array);
                boolean isPrimitive = TagConstants.isPrimitive(tag);

                reply.writeByte(tag);
                reply.writeInt(length);
                for (int i = index; i < index + length; i++) {
                    Object theValue = context.getArrayValue(array, i);
                    writeValue(tag, theValue, reply, !isPrimitive, context);
                }
                return new CommandResult(reply);
            }
        }

        static class SET_VALUES {
            public static final int ID = 3;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long arrayId = input.readLong();
                int index = input.readInt();
                int values = input.readInt();

                Object array = verifyArray(arrayId, reply, context);

                if (array == null || !verifyArrayLength(array, index + values, reply, context)) {
                    return new CommandResult(reply);
                }

                byte tag = context.getTypeTag(array);

                setArrayValues(context, input, index, values, array, tag);
                return new CommandResult(reply);
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

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long classLoaderId = input.readLong();

                Object classLoader = verifyClassLoader(classLoaderId, reply, context);
                if (classLoader == null) {
                    return new CommandResult(reply);
                }

                // TODO(Gregersen) - we will need all classes for which this classloader was the
                // initiating loader
                // tracked by /browse/GR-19820
                KlassRef[] klasses = context.getInitiatedClasses(classLoader);

                reply.writeInt(klasses.length);

                for (KlassRef klass : klasses) {
                    reply.writeByte(TypeTag.getKind(klass));
                    reply.writeLong(context.getIds().getIdAsLong(klass));
                }
                return new CommandResult(reply);
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

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                if (verifyThread(input.readLong(), reply, context) == null) {
                    return new CommandResult(reply);
                }

                long frameId = input.readLong();
                int slots = input.readInt();
                reply.writeInt(slots);

                CallFrame frame = verifyClassFrame(frameId, reply, context);

                if (frame == null) {
                    return new CommandResult(reply);
                }

                Object thisValue = frame.getThisValue();

                if (thisValue == INVALID_VALUE) {
                    reply.errorCode(ErrorCodes.INVALID_OBJECT);
                    return new CommandResult(reply);
                }

                Object[] variables = frame.getVariables();
                int offset = thisValue != null ? 1 : 0;

                try {
                    // below assumes the debugger asks for slot values in increasing order
                    for (int i = 0; i < slots; i++) {
                        int slot = input.readInt();
                        Object value = variables[slot - offset];

                        if (value == INVALID_VALUE) {
                            reply.errorCode(ErrorCodes.INVALID_OBJECT);
                            return new CommandResult(reply);
                        }

                        byte sigbyte = input.readByte();

                        writeValue(sigbyte, value, reply, true, context);
                    }
                } catch (ArrayIndexOutOfBoundsException ex) {
                    // invalid slot provided
                    reply.errorCode(ErrorCodes.INVALID_SLOT);
                    return new CommandResult(reply);
                }
                return new CommandResult(reply);
            }
        }

        static class SET_VALUES {
            public static final int ID = 2;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                if (verifyThread(input.readLong(), reply, context) == null) {
                    return new CommandResult(reply);
                }

                long frameId = input.readLong();
                int slots = input.readInt();

                CallFrame frame = verifyClassFrame(frameId, reply, context);

                if (frame == null) {
                    return new CommandResult(reply);
                }

                Object thisValue = frame.getThisValue();

                if (thisValue == INVALID_VALUE) {
                    reply.errorCode(ErrorCodes.INVALID_OBJECT);
                    return new CommandResult(reply);
                }

                Object[] variables = frame.getVariables();
                int offset = thisValue != null ? 1 : 0;

                // below assumes the debugger asks for slot values in increasing order
                for (int i = 0; i < slots; i++) {
                    int slot = input.readInt();
                    byte kind = input.readByte();
                    variables[slot - offset] = readValue(kind, input, context);
                }
                return new CommandResult(reply);
            }
        }

        static class THIS_OBJECT {
            public static final int ID = 3;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                if (verifyThread(input.readLong(), reply, context) == null) {
                    return new CommandResult(reply);
                }
                long frameId = input.readLong();

                CallFrame frame = verifyClassFrame(frameId, reply, context);

                if (frame == null) {
                    return new CommandResult(reply);
                }

                Object thisValue = frame.getThisValue();

                if (thisValue == INVALID_VALUE) {
                    reply.errorCode(ErrorCodes.INVALID_OBJECT);
                    return new CommandResult(reply);
                }

                reply.writeByte(TagConstants.OBJECT);

                if (thisValue != null) {
                    reply.writeLong(context.getIds().getIdAsLong(thisValue));
                } else {
                    reply.writeLong(0);
                }
                return new CommandResult(reply);
            }
        }
    }

    static class ClassObjectReference {
        public static final int ID = 17;

        static class REFLECTED_TYPE {
            public static final int ID = 1;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long classObjectId = input.readLong();

                Object classObject = verifyClassObject(classObjectId, reply, context);

                if (classObject == null) {
                    return new CommandResult(reply);
                }

                KlassRef klass = context.getReflectedType(classObject);

                reply.writeByte(TypeTag.getKind(klass));
                reply.writeLong(context.getIds().getIdAsLong(klass));
                return new CommandResult(reply);
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
            case TagConstants.THREAD:
            case TagConstants.THREAD_GROUP:
            case TagConstants.CLASS_LOADER:
            case TagConstants.CLASS_OBJECT:
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
            case TagConstants.THREAD:
            case TagConstants.THREAD_GROUP:
            case TagConstants.CLASS_OBJECT:
            case TagConstants.CLASS_LOADER:
                if (value == null || value == context.getNullObject()) {
                    reply.writeLong(0);
                } else {
                    reply.writeLong(context.getIds().getIdAsLong(value));
                }
                break;
            default:
                throw new RuntimeException("Should not reach here!");
        }
    }

    private static void writeMethodResult(PacketStream reply, JDWPContext context, ThreadJob.JobResult result) {
        if (result.getException() != null) {
            JDWPLogger.log("method threw exception", JDWPLogger.LogLevel.PACKET);
            reply.writeByte(TagConstants.OBJECT);
            reply.writeLong(0);
            reply.writeByte(TagConstants.OBJECT);
            Object guestException = context.getGuestException(result.getException());
            reply.writeLong(context.getIds().getIdAsLong(guestException));
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
    }

    private static KlassRef verifyRefType(long refTypeId, PacketStream reply, JDWPContext context) {
        KlassRef klass;
        try {
            klass = (KlassRef) context.getIds().fromId((int) refTypeId);
        } catch (ClassCastException ex) {
            reply.errorCode(ErrorCodes.INVALID_CLASS);
            return null;
        }
        return klass;
    }

    private static FieldRef verifyFieldRef(long fieldId, PacketStream reply, JDWPContext context) {
        FieldRef field;
        try {
            field = (FieldRef) context.getIds().fromId((int) fieldId);
        } catch (ClassCastException ex) {
            reply.errorCode(ErrorCodes.INVALID_FIELDID);
            return null;
        }
        return field;
    }

    private static MethodRef verifyMethodRef(long methodId, PacketStream reply, JDWPContext context) {
        MethodRef method;
        try {
            method = (MethodRef) context.getIds().fromId((int) methodId);
        } catch (ClassCastException ex) {
            reply.errorCode(ErrorCodes.INVALID_METHODID);
            return null;
        }
        return method;
    }

    private static Object verifyThread(long threadId, PacketStream reply, JDWPContext context) {
        Object thread = context.getIds().fromId((int) threadId);

        if (thread == context.getNullObject()) {
            reply.errorCode(ErrorCodes.INVALID_OBJECT);
            return null;
        }

        if (!context.isValidThread(thread)) {
            reply.errorCode(ErrorCodes.INVALID_THREAD);
            return null;
        }
        return thread;
    }

    private static Object verifyThreadGroup(long threadGroupId, PacketStream reply, JDWPContext context) {
        Object threadGroup = context.getIds().fromId((int) threadGroupId);

        if (threadGroup == context.getNullObject()) {
            reply.errorCode(ErrorCodes.INVALID_OBJECT);
            return null;
        }

        if (!context.isValidThreadGroup(threadGroup)) {
            reply.errorCode(ErrorCodes.INVALID_THREAD_GROUP);
            return null;
        }

        return threadGroup;
    }

    private static Object verifyArray(long arrayId, PacketStream reply, JDWPContext context) {
        Object array = context.getIds().fromId((int) arrayId);

        if (array == context.getNullObject()) {
            reply.errorCode(ErrorCodes.INVALID_OBJECT);
            return null;
        }

        if (!context.isArray(array)) {
            reply.errorCode(ErrorCodes.INVALID_ARRAY);
            return null;
        }
        return array;
    }

    private static boolean verifyArrayLength(Object array, int maxIndex, PacketStream reply, JDWPContext context) {
        if (!context.verifyArrayLength(array, maxIndex)) {
            reply.errorCode(ErrorCodes.INVALID_LENGTH);
            return false;
        }
        return true;
    }

    private static Object verifyString(long objectId, PacketStream reply, JDWPContext context) {
        Object string = context.getIds().fromId((int) objectId);

        if (!context.isString(string)) {
            reply.errorCode(ErrorCodes.INVALID_STRING);
            return null;
        }
        return string;
    }

    private static Object verifyClassLoader(long classLoaderId, PacketStream reply, JDWPContext context) {
        Object classLoader = context.getIds().fromId((int) classLoaderId);

        if (!context.isValidClassLoader(classLoader)) {
            reply.errorCode(ErrorCodes.INVALID_CLASS_LOADER);
            return null;
        }
        return classLoader;
    }

    private static CallFrame verifyClassFrame(long frameId, PacketStream reply, JDWPContext context) {
        Object frame = context.getIds().fromId((int) frameId);
        if (!(frame instanceof CallFrame)) {
            reply.errorCode(ErrorCodes.INVALID_FRAMEID);
            return null;
        }

        return (CallFrame) frame;
    }

    private static Object verifyClassObject(long classObjectId, PacketStream reply, JDWPContext context) {
        Object object = context.getIds().fromId((int) classObjectId);

        if (object == context.getNullObject()) {
            reply.errorCode(ErrorCodes.INVALID_OBJECT);
            return null;
        }
        return object;
    }
}
