/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.espresso.jdwp.api.CallFrame;
import com.oracle.truffle.espresso.jdwp.api.ClassStatusConstants;
import com.oracle.truffle.espresso.jdwp.api.ErrorCodes;
import com.oracle.truffle.espresso.jdwp.api.FieldRef;
import com.oracle.truffle.espresso.jdwp.api.JDWPConstantPool;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.jdwp.api.LineNumberTableRef;
import com.oracle.truffle.espresso.jdwp.api.LocalRef;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jdwp.api.ModuleRef;
import com.oracle.truffle.espresso.jdwp.api.MonitorStackInfo;
import com.oracle.truffle.espresso.jdwp.api.RedefineInfo;
import com.oracle.truffle.espresso.jdwp.api.TagConstants;

public final class JDWP {

    public static final String JAVA_LANG_OBJECT = "Ljava/lang/Object;";

    private static final boolean CAN_GET_INSTANCE_INFO = false;
    private static final long SUSPEND_TIMEOUT = 400;

    private static final int ACC_SYNTHETIC = 0x00001000;
    private static final int JDWP_SYNTHETIC = 0xF0000000;

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

            static CommandResult createReply(Packet packet, DebuggerController controller, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                final String signature = input.readString();
                String slashName = signature;

                if (!signature.startsWith("[") && signature.length() != 1) {
                    // we know it's a class type in the format Lsomething;
                    slashName = signature.substring(1, signature.length() - 1);
                }
                try {
                    KlassRef[] loaded = context.findLoadedClass(slashName);

                    reply.writeInt(loaded.length);
                    for (KlassRef klass : loaded) {
                        reply.writeByte(TypeTag.getKind(klass));
                        reply.writeLong(context.getIds().getIdAsLong(klass));
                        reply.writeInt(klass.getStatus());
                    }
                } catch (IllegalStateException e) {
                    controller.warning(() -> "Invalid class name in CLASSES_BY_SIGNATURE: " + signature);
                    reply.writeInt(0);
                }
                return new CommandResult(reply);
            }
        }

        static class ALL_CLASSES {
            public static final int ID = 3;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                KlassRef[] allLoadedClasses = context.getAllLoadedClasses();
                reply.writeInt(allLoadedClasses.length);

                for (KlassRef klass : allLoadedClasses) {
                    reply.writeByte(TypeTag.getKind(klass));
                    reply.writeLong(context.getIds().getIdAsLong(klass));
                    reply.writeString(klass.getTypeAsString());
                    reply.writeInt(klass.getStatus());
                }

                return new CommandResult(reply);
            }
        }

        static class ALL_THREADS {
            public static final int ID = 4;

            static CommandResult createReply(Packet packet, JDWPContext context, DebuggerController controller) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                Object[] allThreads = controller.getVisibleGuestThreads();
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

                return new CommandResult(reply, null, Collections.singletonList(new Callable<Void>() {
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
                controller.fine(() -> "Suspend all packet");

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                controller.suspendAll();

                // give threads time to suspend before returning
                for (Object guestThread : controller.getVisibleGuestThreads()) {
                    SuspendedInfo info = controller.getSuspendedInfo(guestThread);
                    if (info instanceof UnknownSuspendedInfo) {
                        awaitSuspendedInfo(controller, guestThread, info);
                    }
                }
                return new CommandResult(reply);
            }
        }

        static class RESUME {
            public static final int ID = 9;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                controller.fine(() -> "Resume all packet");

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                controller.resumeAll(false);
                return new CommandResult(reply);
            }
        }

        static class EXIT {
            public static final int ID = 10;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                if (context.systemExitImplemented()) {
                    return new CommandResult(reply,
                                    null,
                                    Collections.singletonList(new Callable<Void>() {
                                        @Override
                                        public Void call() {
                                            context.exit(input.readInt());
                                            return null;
                                        }
                                    }));
                } else {
                    reply.errorCode(ErrorCodes.NOT_IMPLEMENTED);
                    return new CommandResult(reply);
                }
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
                reply.writeBoolean(true); // canGetBytecodes
                reply.writeBoolean(true); // canGetSyntheticAttribute
                reply.writeBoolean(true); // canGetOwnedMonitorInfo
                reply.writeBoolean(true); // canGetCurrentContendedMonitor
                reply.writeBoolean(true); // canGetMonitorInfo
                return new CommandResult(reply);
            }
        }

        static class CLASS_PATHS {
            public static final int ID = 13;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                reply.writeString("");
                List<Path> classPath = context.getClassPath();
                reply.writeInt(classPath.size());
                for (Path path : classPath) {
                    reply.writeString(path.toAbsolutePath().toString());
                }
                List<Path> bootClassPath = context.getBootClassPath();
                reply.writeInt(bootClassPath.size());
                for (Path path : bootClassPath) {
                    reply.writeString(path.toAbsolutePath().toString());
                }

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

        static class HOLD_EVENTS {
            public static final int ID = 15;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                controller.getEventListener().holdEvents();

                return new CommandResult(reply);
            }
        }

        static class RELEASE_EVENTS {
            public static final int ID = 16;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                controller.getEventListener().releaseEvents();

                return new CommandResult(reply);
            }
        }

        static class CAPABILITIES_NEW {
            public static final int ID = 17;

            static CommandResult createReply(Packet packet) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                reply.writeBoolean(true); // canWatchFieldModification
                reply.writeBoolean(true); // canWatchFieldAccess
                reply.writeBoolean(true); // canGetBytecodes
                reply.writeBoolean(true); // canGetSyntheticAttribute
                reply.writeBoolean(true); // canGetOwnedMonitorInfo
                reply.writeBoolean(true); // canGetCurrentContendedMonitor
                reply.writeBoolean(true); // canGetMonitorInfo
                reply.writeBoolean(true); // canRedefineClasses
                reply.writeBoolean(true); // canAddMethod
                reply.writeBoolean(true); // canUnrestrictedlyRedefineClasses
                reply.writeBoolean(true); // canPopFrames
                reply.writeBoolean(true); // canUseInstanceFilters
                reply.writeBoolean(true); // canGetSourceDebugExtension
                reply.writeBoolean(true); // canRequestVMDeathEvent
                reply.writeBoolean(false); // canSetDefaultStratum
                reply.writeBoolean(CAN_GET_INSTANCE_INFO); // canGetInstanceInfo
                reply.writeBoolean(true); // canRequestMonitorEvents
                reply.writeBoolean(true); // canGetMonitorFrameInfo
                reply.writeBoolean(false); // canUseSourceNameFilters
                reply.writeBoolean(true); // canGetConstantPool
                reply.writeBoolean(true); // canForceEarlyReturn
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

        static class REDEFINE_CLASSES {
            public static final int ID = 18;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                JDWPContext context = controller.getContext();
                int classes = input.readInt();

                controller.fine(() -> "Request to redefine %d classes received " + classes);
                List<RedefineInfo> redefineInfos = new ArrayList<>(classes);

                for (int i = 0; i < classes; i++) {
                    KlassRef klass = null;
                    long refTypeId = input.readLong();
                    if (refTypeId != -1) { // -1 for new classes in tests
                        klass = verifyRefType(refTypeId, reply, context);
                        if (klass == null) {
                            // check if klass was removed by a previous redefinition
                            if (!context.getIds().checkRemoved(refTypeId)) {
                                reply.errorCode(ErrorCodes.INVALID_CLASS);
                                return new CommandResult(reply);
                            }
                        }
                        if (klass == context.getNullObject() || klass == null) {
                            reply.errorCode(ErrorCodes.INVALID_CLASS);
                            return new CommandResult(reply);
                        }
                    }

                    int byteLength = input.readInt();
                    byte[] classBytes = input.readByteArray(byteLength);
                    redefineInfos.add(new RedefineInfo(klass, classBytes));
                }

                // ensure redefinition atomicity by suspending all
                // guest threads during the redefine transaction
                Object[] allGuestThreads = controller.getVisibleGuestThreads();
                Object prev = null;
                try {
                    prev = controller.enterTruffleContext();
                    for (Object guestThread : allGuestThreads) {
                        controller.suspend(guestThread);
                    }
                    int errorCode = context.redefineClasses(redefineInfos);
                    if (errorCode != 0) {
                        reply.errorCode(errorCode);
                        controller.warning(() -> "Redefine failed with error code: " + errorCode);
                        return new CommandResult(reply);
                    }
                    controller.fine(() -> "Redefine successful");
                } finally {
                    for (Object guestThread : allGuestThreads) {
                        controller.resume(guestThread, false);
                    }
                    controller.leaveTruffleContext(prev);
                }
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
                    reply.writeString(klass.getGenericTypeAsString());
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

        static class ALL_MODULES {
            public static final int ID = 22;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                ModuleRef[] moduleRefs = context.getAllModulesRefs();
                reply.writeInt(moduleRefs.length);
                for (ModuleRef moduleRef : moduleRefs) {
                    reply.writeLong(context.getIds().getIdAsLong(moduleRef));
                }
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
                    int modBits = checkSyntheticFlag(field.getModifiers());
                    reply.writeInt(modBits);
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

                MethodRef[] declaredMethods = klass.getDeclaredMethodRefs();
                int numDeclaredMethods = declaredMethods.length;
                reply.writeInt(numDeclaredMethods);
                for (MethodRef method : declaredMethods) {
                    reply.writeLong(context.getIds().getIdAsLong(method));
                    reply.writeString(method.getNameAsString());
                    reply.writeString(method.getSignatureAsString());
                    int modBits = checkSyntheticFlag(method.getModifiers());
                    reply.writeInt(modBits);
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
                    if (field.getDeclaringKlass().getStatus() == ClassStatusConstants.ERROR || field.getDeclaringKlass().getStatus() < ClassStatusConstants.INITIALIZED) {
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
                MethodRef[] methods = klass.getDeclaredMethodRefs();
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

        static class NESTED_TYPES {
            public static final int ID = 8;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long klassRef = input.readLong();
                KlassRef klass = verifyRefType(klassRef, reply, context);

                if (klass == null) {
                    return new CommandResult(reply);
                }

                KlassRef[] nestedTypes = context.getNestedTypes(klass);

                reply.writeInt(nestedTypes.length);
                for (KlassRef nestedType : nestedTypes) {
                    reply.writeByte(TypeTag.getKind(nestedType));
                    reply.writeLong(context.getIds().getIdAsLong(nestedType));
                }

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

        static class SOURCE_DEBUG_EXTENSION {
            public static final int ID = 12;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                long refTypeId = input.readLong();

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                KlassRef klass = verifyRefType(refTypeId, reply, context);

                if (klass == null) {
                    return new CommandResult(reply);
                }

                String sourceDebugExtension = klass.getSourceDebugExtension();

                if (sourceDebugExtension == null) {
                    reply.errorCode(ErrorCodes.ABSENT_INFORMATION);
                    return new CommandResult(reply);
                }

                reply.writeString(sourceDebugExtension);
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
                reply.writeString(klass.getGenericTypeAsString());
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
                    String signature = field.getTypeAsString();
                    reply.writeString(signature);
                    String genericSignature = field.getGenericSignatureAsString();
                    // only return a generic signature if the type has generics
                    if (genericSignature.equals(signature)) {
                        reply.writeString("");
                    } else {
                        reply.writeString(field.getGenericSignatureAsString());
                    }
                    int modBits = checkSyntheticFlag(field.getModifiers());
                    reply.writeInt(modBits);
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

                MethodRef[] declaredMethods = klass.getDeclaredMethodRefs();
                int numDeclaredMethods = declaredMethods.length;
                reply.writeInt(numDeclaredMethods);
                for (MethodRef method : declaredMethods) {
                    reply.writeLong(context.getIds().getIdAsLong(method));
                    reply.writeString(method.getNameAsString());
                    reply.writeString(method.getSignatureAsString());
                    reply.writeString(method.getGenericSignatureAsString());
                    int modBits = checkSyntheticFlag(method.getModifiers());
                    reply.writeInt(modBits);
                }
                return new CommandResult(reply);
            }
        }

        static class INSTANCES {
            public static final int ID = 16;

            static CommandResult createReply(Packet packet) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                if (!CAN_GET_INSTANCE_INFO) {
                    // tracked by: /browse/GR-20325
                    reply.errorCode(ErrorCodes.NOT_IMPLEMENTED);
                    return new CommandResult(reply);
                } else {
                    throw new RuntimeException("Not implemented!");
                }
            }
        }

        static class CLASS_FILE_VERSION {
            public static final int ID = 17;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long typeId = input.readLong();

                KlassRef klassRef = verifyRefType(typeId, reply, context);

                if (klassRef == null) {
                    // input could be a classObjectId
                    Object object = context.getIds().fromId((int) typeId);
                    klassRef = context.getReflectedType(object);
                }

                if (klassRef == null) {
                    return new CommandResult(reply);
                }

                if (klassRef.isArray() || klassRef.isPrimitive()) {
                    reply.errorCode(ErrorCodes.ABSENT_INFORMATION);
                    return new CommandResult(reply);
                }

                reply.writeInt(klassRef.getMajorVersion());
                reply.writeInt(klassRef.getMinorVersion());

                return new CommandResult(reply);
            }
        }

        static class CONSTANT_POOL {

            public static final int ID = 18;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long typeId = input.readLong();
                KlassRef klass = verifyRefType(typeId, reply, context);

                if (klass == null) {
                    // input could be a classObjectId
                    Object object = context.getIds().fromId((int) typeId);
                    klass = context.getReflectedType(object);
                }

                if (klass == null) {
                    return new CommandResult(reply);
                }

                if (klass.isPrimitive() || klass.isArray()) {
                    reply.errorCode(ErrorCodes.ABSENT_INFORMATION);
                    return new CommandResult(reply);
                }

                JDWPConstantPool constantPool = klass.getJDWPConstantPool();

                int count = constantPool.getCount();
                reply.writeInt(count);

                byte[] poolBytes = constantPool.getBytes();
                reply.writeInt(poolBytes.length);
                reply.writeByteArray(poolBytes);

                return new CommandResult(reply);
            }
        }

        static class MODULE {

            public static final int ID = 19;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long typeId = input.readLong();
                KlassRef klass = verifyRefType(typeId, reply, context);

                if (klass == null) {
                    // input could be a classObjectId
                    Object object = context.getIds().fromId((int) typeId);
                    klass = context.getReflectedType(object);
                }

                if (klass == null) {
                    return new CommandResult(reply);
                }

                ModuleRef module = klass.getModule();
                long moduleID = context.getIds().getIdAsLong(module);
                reply.writeLong(moduleID);

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

            static CommandResult createReply(Packet packet, DebuggerController controller, DebuggerConnection connection) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                JDWPContext context = controller.getContext();

                KlassRef klass = verifyRefType(input.readLong(), reply, context);
                if (klass == null) {
                    return new CommandResult(reply);
                }

                Object thread = verifyThread(input.readLong(), reply, context, true);
                if (thread == null) {
                    return new CommandResult(reply);
                }

                // make sure the thread is suspended
                int suspensionCount = controller.getThreadSuspension().getSuspensionCount(thread);
                if (suspensionCount < 1) {
                    reply.errorCode(ErrorCodes.THREAD_NOT_SUSPENDED);
                    return new CommandResult(reply);
                }

                MethodRef method = verifyMethodRef(input.readLong(), reply, context);
                if (method == null) {
                    return new CommandResult(reply);
                }

                // check that method is member of the class type or a super class
                KlassRef declaringKlass = method.getDeclaringKlassRef();
                KlassRef checkedKlass = klass;
                boolean isMember = false;
                while (checkedKlass != null) {
                    if (checkedKlass == declaringKlass) {
                        isMember = true;
                        break;
                    }
                    checkedKlass = checkedKlass.getSuperClass();
                }
                if (!isMember) {
                    reply.errorCode(ErrorCodes.INVALID_METHODID);
                    return new CommandResult(reply);
                }

                controller.fine(() -> "trying to invoke static method: " + method.getNameAsString());

                int arguments = input.readInt();

                Object[] args = new Object[arguments];
                for (int i = 0; i < arguments; i++) {
                    byte valueKind = input.readByte();
                    args[i] = readValue(valueKind, input, context);
                }

                int invocationOptions = input.readInt();
                byte suspensionStrategy = invocationOptions == 1 ? SuspendStrategy.EVENT_THREAD : SuspendStrategy.ALL;
                try {
                    // we have to call the method in the correct thread, so post a
                    // Callable to the controller and wait for the result to appear
                    ThreadJob<Object> job = new ThreadJob<>(thread, new Callable<>() {
                        @Override
                        public Object call() {
                            return method.invokeMethod(null, args);
                        }
                    }, suspensionStrategy);
                    controller.postJobForThread(job);

                    // invocation of a method can cause events with possible thread suspension
                    // to happen, e.g. class prepare events for newly loaded classes
                    // to avoid blocking here, we fire up a new thread that will post
                    // the result when available
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            CommandResult commandResult = new CommandResult(reply);
                            try {
                                ThreadJob<?>.JobResult<?> result = job.getResult();
                                writeMethodResult(reply, context, result, thread, controller);
                            } catch (Throwable t) {
                                reply.errorCode(ErrorCodes.INTERNAL);
                                // Checkstyle: stop allow error output
                                System.err.println("Internal Espresso error: " + t.getMessage());
                                t.printStackTrace();
                                // Checkstyle: resume allow error output
                            } finally {
                                connection.handleReply(packet, commandResult);
                            }
                        }
                    }).start();
                } catch (Throwable t) {
                    throw new RuntimeException("not able to invoke static method through jdwp", t);
                }
                // the spawned thread will ship the reply when method invocation finishes
                return null;
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

                Object thread = verifyThread(input.readLong(), reply, context, true);
                if (thread == null) {
                    return new CommandResult(reply);
                }

                // make sure the thread is suspended
                int suspensionCount = controller.getThreadSuspension().getSuspensionCount(thread);
                if (suspensionCount < 1) {
                    reply.errorCode(ErrorCodes.THREAD_NOT_SUSPENDED);
                    return new CommandResult(reply);
                }

                MethodRef method = verifyMethodRef(input.readLong(), reply, context);
                if (method == null) {
                    controller.warning(() -> "not a valid method");
                    return new CommandResult(reply);
                }

                controller.fine(() -> "trying to invoke constructor in klass: " + klass.getNameAsString());

                int arguments = input.readInt();

                Object[] args = new Object[arguments];
                for (int i = 0; i < arguments; i++) {
                    byte valueKind = input.readByte();
                    args[i] = readValue(valueKind, input, context);
                }

                int invocationOptions = input.readInt();
                byte suspensionStrategy = invocationOptions == 1 ? SuspendStrategy.EVENT_THREAD : SuspendStrategy.ALL;
                try {
                    // we have to call the method in the correct thread, so post a
                    // Callable to the controller and wait for the result to appear
                    ThreadJob<?> job = new ThreadJob<>(thread, new Callable<>() {

                        @Override
                        public Object call() throws Exception {
                            return method.invokeMethod(null, args);
                        }
                    }, suspensionStrategy);
                    controller.postJobForThread(job);
                    ThreadJob<?>.JobResult<?> result = job.getResult();

                    writeMethodResult(reply, context, result, thread, controller);
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

            static CommandResult createReply(Packet packet, DebuggerController controller, DebuggerConnection connection) {
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

                Object thread = verifyThread(input.readLong(), reply, context, true);
                if (thread == null) {
                    return new CommandResult(reply);
                }

                // make sure the thread is suspended
                int suspensionCount = controller.getThreadSuspension().getSuspensionCount(thread);
                if (suspensionCount < 1) {
                    reply.errorCode(ErrorCodes.THREAD_NOT_SUSPENDED);
                    return new CommandResult(reply);
                }

                MethodRef method = verifyMethodRef(input.readLong(), reply, context);
                if (method == null) {
                    return new CommandResult(reply);
                }

                if (method.getDeclaringKlassRef() != itf) {
                    reply.errorCode(ErrorCodes.INVALID_METHODID);
                    return new CommandResult(reply);
                }

                controller.fine(() -> "trying to invoke interface method: " + method.getNameAsString());

                int arguments = input.readInt();

                Object[] args = new Object[arguments];
                for (int i = 0; i < arguments; i++) {
                    byte valueKind = input.readByte();
                    args[i] = readValue(valueKind, input, context);
                }

                int invocationOptions = input.readInt();
                byte suspensionStrategy = invocationOptions == 1 ? SuspendStrategy.EVENT_THREAD : SuspendStrategy.ALL;
                try {
                    // we have to call the method in the correct thread, so post a
                    // Callable to the controller and wait for the result to appear
                    ThreadJob<Object> job = new ThreadJob<>(thread, new Callable<>() {

                        @Override
                        public Object call() throws Exception {
                            return method.invokeMethod(null, args);
                        }
                    }, suspensionStrategy);
                    controller.postJobForThread(job);
                    // invocation of a method can cause events with possible thread suspension
                    // to happen, e.g. class prepare events for newly loaded classes
                    // to avoid blocking here, we fire up a new thread that will post
                    // the result when available
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            CommandResult commandResult = new CommandResult(reply);
                            try {
                                ThreadJob<?>.JobResult<?> result = job.getResult();
                                writeMethodResult(reply, context, result, thread, controller);
                            } catch (Throwable t) {
                                reply.errorCode(ErrorCodes.INTERNAL);
                                // Checkstyle: stop allow error output
                                System.err.println("Internal Espresso error: " + t.getMessage());
                                t.printStackTrace();
                                // Checkstyle: resume allow error output
                            } finally {
                                connection.handleReply(packet, commandResult);
                            }
                        }
                    }).start();
                } catch (Throwable t) {
                    throw new RuntimeException("not able to invoke interface method through jdwp", t);
                }
                // spawned thread will handle reply when method invocation is done
                return null;
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
                    List<? extends LineNumberTableRef.EntryRef> entries = table.getEntries();
                    long start = method.isMethodNative() ? -1 : 0;
                    long end = method.isMethodNative() ? -1 : method.getLastBCI();
                    int lines = entries.size();
                    Line[] allLines = new Line[lines];

                    for (int i = 0; i < entries.size(); i++) {
                        LineNumberTableRef.EntryRef entry = entries.get(i);
                        int bci = entry.getBCI();
                        int line = entry.getLineNumber();
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

        static class VARIABLE_TABLE {
            public static final int ID = 2;

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

                int argCnt = getArgCount(method.getSignatureAsString());
                LocalRef[] locals = method.getLocalVariableTable().getLocals();

                reply.writeInt(argCnt);
                reply.writeInt(locals.length);
                for (LocalRef local : locals) {
                    reply.writeLong(local.getStartBCI());
                    reply.writeString(local.getNameAsString());
                    reply.writeString(local.getTypeAsString());
                    reply.writeInt(local.getEndBCI() - local.getStartBCI());
                    reply.writeInt(local.getSlot());
                }
                return new CommandResult(reply);
            }
        }

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

                byte[] code = method.getOriginalCode();

                reply.writeInt(code.length);
                reply.writeByteArray(code);

                return new CommandResult(reply);
            }
        }

        static class IS_OBSOLETE {
            public static final int ID = 4;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                KlassRef refType = verifyRefType(input.readLong(), reply, context);

                if (refType == null) {
                    return new CommandResult(reply);
                }
                long methodId = input.readLong();
                if (methodId == 0) {
                    reply.writeBoolean(true);
                } else {
                    MethodRef method = verifyMethodRef(methodId, reply, context);

                    if (method == null) {
                        return new CommandResult(reply);
                    }
                    reply.writeBoolean(method.isObsolete());
                }
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
                // the number of words in the frame used by the arguments
                int argCnt = getArgCount(method.getSignatureAsString());
                LocalRef[] locals = method.getLocalVariableTable().getLocals();
                LocalRef[] genericLocals = method.getLocalVariableTypeTable().getLocals();

                reply.writeInt(argCnt);
                reply.writeInt(locals.length);
                for (LocalRef local : locals) {
                    reply.writeLong(local.getStartBCI());
                    reply.writeString(local.getNameAsString());
                    reply.writeString(local.getTypeAsString());
                    String genericSignature = "";
                    for (LocalRef genericLocal : genericLocals) {
                        if (genericLocal.getNameAsString().equals(local.getNameAsString())) {
                            // found a generic local
                            genericSignature = genericLocal.getTypeAsString();
                        }
                    }
                    reply.writeString(genericSignature);
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

        static class MONITOR_INFO {
            public static final int ID = 5;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                PacketStream input = new PacketStream(packet);

                JDWPContext context = controller.getContext();
                long objectId = input.readLong();
                Object monitor = context.getIds().fromId((int) objectId);

                if (monitor == context.getNullObject()) {
                    reply.errorCode(ErrorCodes.INVALID_OBJECT);
                    return new CommandResult(reply);
                }

                Object monitorOwnerThread = context.getMonitorOwnerThread(monitor);
                if (monitorOwnerThread == null) {
                    reply.writeLong(0);
                    reply.writeInt(0);
                    reply.writeInt(0);
                } else {
                    reply.writeLong(context.getIds().getIdAsLong(monitorOwnerThread));

                    // go through the suspended info to obtain the entry count
                    SuspendedInfo info = controller.getSuspendedInfo(monitorOwnerThread);

                    if (info == null) {
                        reply.errorCode(ErrorCodes.THREAD_NOT_SUSPENDED);
                        return new CommandResult(reply);
                    }

                    if (info instanceof UnknownSuspendedInfo) {
                        awaitSuspendedInfo(controller, monitorOwnerThread, info);
                        if (info instanceof UnknownSuspendedInfo) {
                            // still no known suspension state
                            reply.errorCode(ErrorCodes.THREAD_NOT_SUSPENDED);
                            return new CommandResult(reply);
                        }
                    }
                    int entryCount = info.getMonitorEntryCount(monitor);

                    if (entryCount == -1) {
                        reply.errorCode(ErrorCodes.INVALID_OBJECT);
                        return new CommandResult(reply);
                    }
                    reply.writeInt(entryCount);

                    ArrayList<Object> waiters = new ArrayList<>();
                    for (Object activeThread : controller.getVisibleGuestThreads()) {
                        if (activeThread == monitorOwnerThread) {
                            continue;
                        }
                        Object contendedMonitor = controller.getEventListener().getCurrentContendedMonitor(activeThread);
                        if (contendedMonitor != null && contendedMonitor == monitor) {
                            waiters.add(activeThread);
                        }
                    }

                    reply.writeInt(waiters.size());

                    for (Object waiter : waiters) {
                        reply.writeLong(context.getIds().getIdAsLong(waiter));
                    }
                }
                return new CommandResult(reply);
            }
        }

        static class INVOKE_METHOD {
            public static final int ID = 6;

            static CommandResult createReply(Packet packet, DebuggerController controller, DebuggerConnection connection) {

                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                controller.fine(() -> "Invoke method through jdwp");

                JDWPContext context = controller.getContext();

                long objectId = input.readLong();
                long threadId = input.readLong();

                Object thread = verifyThread(threadId, reply, context, true);

                if (thread == null) {
                    return new CommandResult(reply);
                }

                // make sure the thread is suspended
                int suspensionCount = controller.getThreadSuspension().getSuspensionCount(thread);
                if (suspensionCount < 1) {
                    reply.errorCode(ErrorCodes.THREAD_NOT_SUSPENDED);
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

                Object callee = context.getIds().fromId((int) objectId);
                if (callee == null) {
                    // object was garbage collected
                    reply.errorCode(ErrorCodes.INVALID_OBJECT);
                    return new CommandResult(reply);
                }
                MethodRef method = verifyMethodRef(methodId, reply, context);

                if (method == null) {
                    return new CommandResult(reply);
                }

                if (!context.isMemberOf(callee, method.getDeclaringKlassRef())) {
                    reply.errorCode(ErrorCodes.INVALID_METHODID);
                    return new CommandResult(reply);
                }

                controller.fine(() -> "trying to invoke method: " + method.getNameAsString());

                int invocationOptions = input.readInt();
                byte suspensionStrategy = invocationOptions == 1 ? SuspendStrategy.EVENT_THREAD : SuspendStrategy.ALL;
                try {
                    // we have to call the method in the correct thread, so post a
                    // Callable to the controller and wait for the result to appear
                    ThreadJob<Object> job = new ThreadJob<>(thread, new Callable<>() {
                        @Override
                        public Object call() throws Exception {
                            return method.invokeMethod(callee, args);
                        }
                    }, suspensionStrategy);
                    controller.postJobForThread(job);
                    // invocation of a method can cause events with possible thread suspension
                    // to happen, e.g. class prepare events for newly loaded classes
                    // to avoid blocking here, we fire up a new thread that will post
                    // the result when available
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            CommandResult commandResult = new CommandResult(reply);
                            try {
                                ThreadJob<?>.JobResult<?> result = job.getResult();
                                writeMethodResult(reply, context, result, thread, controller);
                            } catch (Throwable t) {
                                reply.errorCode(ErrorCodes.INTERNAL);
                                // Checkstyle: stop allow error output
                                System.err.println("Internal Espresso error: " + t.getMessage());
                                t.printStackTrace();
                                // Checkstyle: resume allow error output
                            } finally {
                                connection.handleReply(packet, commandResult);
                            }
                        }
                    }).start();
                } catch (Throwable t) {
                    throw new RuntimeException("not able to invoke method through jdwp", t);
                }
                // spawned thread will handle reply when method invocation is done
                return null;
            }
        }

        static class DISABLE_COLLECTION {
            public static final int ID = 7;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                long objectId = input.readLong();
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                Object object = controller.getContext().getIds().fromId((int) objectId);

                if (object == null || object == controller.getContext().getNullObject()) {
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

        static class REFERRING_OBJECTS {
            public static final int ID = 10;

            static CommandResult createReply(Packet packet) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                if (!CAN_GET_INSTANCE_INFO) {
                    // tracked by: /browse/GR-20325
                    reply.errorCode(ErrorCodes.NOT_IMPLEMENTED);
                    return new CommandResult(reply);
                } else {
                    throw new RuntimeException("Not implemented!");
                }
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

        static class NAME {
            public static final int ID = 1;

            static CommandResult createReply(Packet packet, DebuggerController controller, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadId = input.readLong();
                Object thread = verifyThread(threadId, reply, context, false);

                if (thread == null) {
                    controller.fine(() -> "null thread discovered with ID: " + threadId);

                    return new CommandResult(reply);
                }

                String threadName = context.getThreadName(thread);

                reply.writeString(threadName);

                controller.fine(() -> "thread name: " + threadName);

                return new CommandResult(reply);
            }
        }

        static class SUSPEND {
            public static final int ID = 2;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadId = input.readLong();
                Object thread = verifyThread(threadId, reply, controller.getContext(), false);

                if (thread == null) {
                    return new CommandResult(reply);
                }

                controller.fine(() -> "suspend thread packet for thread: " + controller.getContext().getThreadName(thread));

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
                Object thread = verifyThread(threadId, reply, controller.getContext(), false);

                if (thread == null) {
                    return new CommandResult(reply);
                }

                controller.fine(() -> "resume thread packet for thread: " + controller.getContext().getThreadName(thread));

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
                Object thread = verifyThread(threadId, reply, context, false);

                if (thread == null) {
                    return new CommandResult(reply);
                }

                int jvmtiThreadStatus = context.getThreadStatus(thread);
                int threadStatus = getThreadStatus(jvmtiThreadStatus);
                reply.writeInt(threadStatus);
                int suspended = controller.getThreadSuspension().getSuspensionCount(thread) > 0 ? 1 : 0;
                reply.writeInt(suspended);

                controller.fine(() -> "status command for thread: " + context.getThreadName(thread) + " with status: " + threadStatus + " suspended: " + suspended);

                return new CommandResult(reply);
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
                Object thread = verifyThread(threadId, reply, context, false);

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
                Object thread = verifyThread(threadId, reply, controller.getContext(), true);

                if (thread == null) {
                    return new CommandResult(reply);
                }

                int startFrame = input.readInt();
                int length = input.readInt();
                final int requestedLength = length;

                controller.fine(() -> "requesting frames for thread: " + controller.getContext().getThreadName(thread));
                controller.fine(() -> "startFrame requested: " + startFrame);
                controller.fine(() -> "Number of frames requested: " + requestedLength);

                SuspendedInfo suspendedInfo = controller.getSuspendedInfo(thread);

                if (suspendedInfo == null) {
                    controller.fine(() -> "THREAD_NOT_SUSPENDED: " + controller.getContext().getThreadName(thread));
                    reply.errorCode(ErrorCodes.THREAD_NOT_SUSPENDED);
                    return new CommandResult(reply);
                }

                if (suspendedInfo instanceof UnknownSuspendedInfo) {
                    controller.fine(() -> "Unknown suspension info for thread: " + controller.getContext().getThreadName(thread));
                    suspendedInfo = awaitSuspendedInfo(controller, thread, suspendedInfo);
                    if (suspendedInfo instanceof UnknownSuspendedInfo) {
                        // we can't return any frames for a not yet suspended thread
                        reply.errorCode(ErrorCodes.THREAD_NOT_SUSPENDED);
                        return new CommandResult(reply);
                    }
                }

                CallFrame[] frames = suspendedInfo.getStackFrames();

                if (length == -1 || length > frames.length) {
                    length = frames.length;
                }
                reply.writeInt(length);
                final int finalLength = length;
                controller.fine(() -> "returning " + finalLength + " frames for thread: " + controller.getContext().getThreadName(thread));

                for (int i = startFrame; i < startFrame + length; i++) {
                    CallFrame frame = frames[i];
                    reply.writeLong(controller.getContext().getIds().getIdAsLong(frame));
                    reply.writeByte(frame.getTypeTag());
                    reply.writeLong(frame.getClassId());
                    reply.writeLong(frame.getMethod().isObsolete() ? 0 : controller.getContext().getIds().getIdAsLong(frame.getMethod()));
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
                Object thread = verifyThread(threadId, reply, controller.getContext(), true);

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
                controller.fine(() -> "current frame count: " + length + " for thread: " + controller.getContext().getThreadName(thread));

                return new CommandResult(reply);
            }
        }

        static class OWNED_MONITORS {
            public static final int ID = 8;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                JDWPContext context = controller.getContext();

                Object thread = verifyThread(input.readLong(), reply, context, true);

                if (thread == null) {
                    reply.errorCode(ErrorCodes.INVALID_THREAD);
                    return new CommandResult(reply);
                }

                SuspendedInfo info = controller.getSuspendedInfo(thread);

                if (info == null) {
                    reply.errorCode(ErrorCodes.THREAD_NOT_SUSPENDED);
                    return new CommandResult(reply);
                }

                if (info instanceof UnknownSuspendedInfo) {
                    info = awaitSuspendedInfo(controller, thread, info);
                    if (info instanceof UnknownSuspendedInfo) {
                        // still no known suspension state
                        reply.errorCode(ErrorCodes.THREAD_NOT_SUSPENDED);
                        return new CommandResult(reply);
                    }
                }

                // fetch all monitors on current stack
                MonitorStackInfo[] ownedMonitors = context.getOwnedMonitors(info.getStackFrames());

                // filter out monitors not owned by thread
                ArrayList<Object> filtered = new ArrayList<>(ownedMonitors.length);
                for (MonitorStackInfo ownedMonitor : ownedMonitors) {
                    Object monitor = ownedMonitor.getMonitor();
                    if (context.getMonitorOwnerThread(monitor) == thread) {
                        filtered.add(monitor);
                    }
                }

                reply.writeInt(filtered.size());

                for (Object monitor : filtered) {
                    reply.writeByte(context.getTag(monitor));
                    reply.writeLong(context.getIds().getIdAsLong(monitor));
                }
                return new CommandResult(reply);
            }
        }

        static class CURRENT_CONTENDED_MONITOR {
            public static final int ID = 9;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                JDWPContext context = controller.getContext();

                Object thread = verifyThread(input.readLong(), reply, context, true);

                if (thread == null) {
                    reply.errorCode(ErrorCodes.INVALID_THREAD);
                    return new CommandResult(reply);
                }

                Object currentContendedMonitor = controller.getEventListener().getCurrentContendedMonitor(thread);
                if (currentContendedMonitor == null) {
                    reply.writeByte(TagConstants.OBJECT);
                    reply.writeLong(0);
                } else {
                    reply.writeByte(context.getTag(currentContendedMonitor));
                    reply.writeLong(context.getIds().getIdAsLong(currentContendedMonitor));
                }
                return new CommandResult(reply);
            }
        }

        static class STOP {
            public static final int ID = 10;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadId = input.readLong();
                Object thread = verifyThread(threadId, reply, context, false);

                if (thread == null) {
                    return new CommandResult(reply);
                }

                long throwableId = input.readLong();
                Object throwable = context.getIds().fromId((int) throwableId);

                context.stopThread(thread, throwable);

                return new CommandResult(reply);
            }
        }

        static class INTERRUPT {
            public static final int ID = 11;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadId = input.readLong();
                Object thread = verifyThread(threadId, reply, context, false);

                if (thread == null) {
                    return new CommandResult(reply);
                }

                context.interruptThread(thread);

                return new CommandResult(reply);
            }
        }

        static class SUSPEND_COUNT {
            public static final int ID = 12;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadId = input.readLong();
                Object thread = verifyThread(threadId, reply, controller.getContext(), false);

                if (thread == null) {
                    return new CommandResult(reply);
                }

                int suspensionCount = controller.getThreadSuspension().getSuspensionCount(thread);
                controller.fine(() -> "suspension count: " + suspensionCount + " returned for thread: " + controller.getContext().getThreadName(thread));

                reply.writeInt(suspensionCount);
                return new CommandResult(reply);
            }
        }

        static class OWNED_MONITORS_STACK_DEPTH_INFO {
            public static final int ID = 13;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                JDWPContext context = controller.getContext();

                PacketStream input = new PacketStream(packet);
                Object thread = verifyThread(input.readLong(), reply, context, true);

                if (thread == null) {
                    return new CommandResult(reply);
                }

                SuspendedInfo suspendedInfo = controller.getSuspendedInfo(thread);

                if (suspendedInfo instanceof UnknownSuspendedInfo) {
                    suspendedInfo = awaitSuspendedInfo(controller, thread, suspendedInfo);
                    if (suspendedInfo instanceof UnknownSuspendedInfo) {
                        reply.errorCode(ErrorCodes.THREAD_NOT_SUSPENDED);
                        return new CommandResult(reply);
                    }
                }

                MonitorStackInfo[] ownedMonitorInfos = context.getOwnedMonitors(suspendedInfo.getStackFrames());
                // filter out monitors not owned by thread
                ArrayList<MonitorStackInfo> filtered = new ArrayList<>(ownedMonitorInfos.length);
                for (MonitorStackInfo ownedMonitor : ownedMonitorInfos) {
                    Object monitor = ownedMonitor.getMonitor();
                    if (context.getMonitorOwnerThread(monitor) == thread) {
                        filtered.add(ownedMonitor);
                    }
                }

                reply.writeInt(filtered.size());
                for (MonitorStackInfo ownedMonitorInfo : filtered) {
                    reply.writeByte(context.getTag(ownedMonitorInfo.getMonitor()));
                    reply.writeLong(context.getIds().getIdAsLong(ownedMonitorInfo.getMonitor()));
                    reply.writeInt(ownedMonitorInfo.getStackDepth());
                }
                return new CommandResult(reply);
            }
        }

        static class FORCE_EARLY_RETURN {
            public static final int ID = 14;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                PacketStream input = new PacketStream(packet);

                Object thread = verifyThread(input.readLong(), reply, controller.getContext(), true);

                if (thread == null) {
                    reply.errorCode(ErrorCodes.INVALID_THREAD);
                    return new CommandResult(reply);
                }

                SuspendedInfo info = controller.getSuspendedInfo(thread);

                if (info == null) {
                    reply.errorCode(ErrorCodes.THREAD_NOT_SUSPENDED);
                    return new CommandResult(reply);
                }

                if (info instanceof UnknownSuspendedInfo) {
                    info = awaitSuspendedInfo(controller, thread, info);
                    if (info instanceof UnknownSuspendedInfo) {
                        // still no known suspension state
                        reply.errorCode(ErrorCodes.THREAD_NOT_SUSPENDED);
                        return new CommandResult(reply);
                    }
                }

                final SuspendedInfo suspendedInfo = info;

                Object returnValue = readValue(input, controller.getContext());
                if (returnValue == Void.TYPE) {
                    // we have to use an Interop value, so simply use
                    // the NULL object, since it will be popped for void
                    // return type methods anyway
                    returnValue = controller.getContext().getNullObject();
                }
                CallFrame topFrame = suspendedInfo.getStackFrames().length > 0 ? suspendedInfo.getStackFrames()[0] : null;
                if (!controller.forceEarlyReturn(thread, topFrame, returnValue)) {
                    reply.errorCode(ErrorCodes.OPAQUE_FRAME);
                }

                // make sure owned monitors taken in frame are exited
                ThreadJob<Void> job = new ThreadJob<>(thread, new Callable<Void>() {
                    @Override
                    public Void call() {
                        controller.getContext().clearFrameMonitors(topFrame);
                        return null;
                    }
                });
                controller.postJobForThread(job);
                // don't return here before job completed
                job.getResult();

                return new CommandResult(reply);
            }
        }

        static class IS_VIRTUAL {
            public static final int ID = 15;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadId = input.readLong();
                Object thread = verifyThread(threadId, reply, controller.getContext(), false);

                if (thread == null) {
                    return new CommandResult(reply);
                }

                reply.writeBoolean(controller.getContext().isVirtualThread(thread));
                return new CommandResult(reply);
            }
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

        static class CHILDREN {
            public static final int ID = 3;

            static CommandResult createReply(Packet packet, JDWPContext context, DebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long threadGroupId = input.readLong();
                Object threadGroup = verifyThreadGroup(threadGroupId, reply, context);

                if (threadGroup == null) {
                    return new CommandResult(reply);
                }

                ArrayList<Object> children = new ArrayList<>();
                for (Object thread : controller.getVisibleGuestThreads()) {
                    Object otherGroup = context.getThreadGroup(thread);
                    if (otherGroup == threadGroup) {
                        children.add(thread);
                    }
                }

                reply.writeInt(children.size());
                for (Object child : children) {
                    reply.writeLong(context.getIds().getIdAsLong(child));
                }
                reply.writeInt(0); // no children thread groups
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
                if (arrayLength == -1) {
                    // can happen for foreign arrays
                    reply.errorCode(ErrorCodes.INVALID_OBJECT);
                    return new CommandResult(reply);
                }

                reply.writeInt(arrayLength);
                return new CommandResult(reply);
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
                    byte valueTag = context.getTag(theValue);
                    writeValue(valueTag, theValue, reply, !isPrimitive, context);
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
                        case TagConstants.BOOLEAN:
                            boolean bool = input.readBoolean();
                            byte[] boolArray = context.getUnboxedArray(array);
                            boolArray[i] = bool ? (byte) 1 : (byte) 0;
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
                List<? extends KlassRef> klasses = context.getInitiatedClasses(classLoader);

                reply.writeInt(klasses.size());

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

        static class CLEAR_ALL_BREAKPOINTS {
            public static final int ID = 3;
        }
    }

    static class StackFrame {
        public static final int ID = 16;

        static class GET_VALUES {
            public static final int ID = 1;

            static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                if (verifyThread(input.readLong(), reply, context, true) == null) {
                    return new CommandResult(reply);
                }

                long frameId = input.readLong();
                int slots = input.readInt();
                reply.writeInt(slots);

                CallFrame frame = verifyCallFrame(frameId, reply, context);

                if (frame == null) {
                    return new CommandResult(reply);
                }

                try {
                    for (int i = 0; i < slots; i++) {
                        int slot = input.readInt();
                        Object value = frame.getVariable("" + slot);

                        if (value == CallFrame.INVALID_VALUE) {
                            reply.errorCode(ErrorCodes.INVALID_OBJECT);
                            return new CommandResult(reply);
                        }

                        byte sigbyte = input.readByte();
                        if (sigbyte == TagConstants.OBJECT) {
                            sigbyte = context.getTag(value);
                        }

                        writeValue(sigbyte, value, reply, true, context);
                    }
                } catch (ArrayIndexOutOfBoundsException | InteropException ex) {
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

                if (verifyThread(input.readLong(), reply, context, true) == null) {
                    return new CommandResult(reply);
                }

                long frameId = input.readLong();
                int slots = input.readInt();

                CallFrame frame = verifyCallFrame(frameId, reply, context);

                if (frame == null) {
                    return new CommandResult(reply);
                }

                // below assumes the debugger asks for slot values in increasing order
                for (int i = 0; i < slots; i++) {
                    String identifier = input.readInt() + ""; // slot index
                    byte kind = input.readByte();
                    Object value = readValue(kind, input, context);
                    frame.setVariable(value, identifier);
                }
                return new CommandResult(reply);
            }
        }

        static class THIS_OBJECT {
            public static final int ID = 3;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                JDWPContext context = controller.getContext();

                Object thread = verifyThread(input.readLong(), reply, context, true);

                if (thread == null) {
                    return new CommandResult(reply);
                }
                long frameId = input.readLong();

                CallFrame frame = verifyCallFrame(frameId, reply, context);

                if (frame == null) {
                    return new CommandResult(reply);
                }

                Object thisValue = null;
                if (!Modifier.isStatic(frame.getMethod().getModifiers())) {
                    thisValue = frame.getThisValue();
                }

                if (thisValue == CallFrame.INVALID_VALUE) {
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

        static class POP_FRAMES {
            public static final int ID = 4;

            static CommandResult createReply(Packet packet, DebuggerController controller) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                Object thread = verifyThread(input.readLong(), reply, controller.getContext(), true);
                CallFrame frame = verifyCallFrame(input.readLong(), reply, controller.getContext());

                if (thread == null || frame == null) {
                    return new CommandResult(reply);
                }

                // make sure the thread is suspended
                int suspensionCount = controller.getThreadSuspension().getSuspensionCount(thread);
                if (suspensionCount < 1) {
                    reply.errorCode(ErrorCodes.THREAD_NOT_SUSPENDED);
                    return new CommandResult(reply);
                }

                if (!controller.popFrames(thread, frame, packet.id)) {
                    reply.errorCode(ErrorCodes.INVALID_FRAMEID);
                    return new CommandResult(reply);
                }
                // don't send a reply before we have completed the pop frames
                // the reply packet will be sent when thread is suspended after
                // popping the requested frames
                return null;
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

    static class ModuleReference {
        public static final int ID = 18;

        static class NAME {

            public static final int ID = 1;

            public static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long moduleId = input.readLong();
                ModuleRef module = verifyModule(moduleId, reply, context);

                if (module == null) {
                    return new CommandResult(reply);
                }

                reply.writeString(module.jdwpName());
                return new CommandResult(reply);
            }
        }

        static class CLASSLOADER {

            public static final int ID = 2;

            public static CommandResult createReply(Packet packet, JDWPContext context) {
                PacketStream input = new PacketStream(packet);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                long moduleId = input.readLong();
                ModuleRef module = verifyModule(moduleId, reply, context);

                if (module == null) {
                    return new CommandResult(reply);
                }

                Object loader = module.classLoader();
                if (loader == null || loader == context.getNullObject()) { // system class loader
                    reply.writeLong(0);
                } else {
                    reply.writeLong(context.getIds().getIdAsLong(loader));
                }
                return new CommandResult(reply);
            }
        }
    }

    static class Event {
        public static final int ID = 64;

        static class COMPOSITE {
            public static final int ID = 100;

            static CommandResult createReply(Packet packet) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.errorCode(ErrorCodes.NOT_IMPLEMENTED);
                return new CommandResult(reply);
            }
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
            controller.fine(() -> "Awaiting suspended info for thread " + controller.getContext().getThreadName(thread));

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
            controller.fine(() -> "Still no suspended info for thread " + controller.getContext().getThreadName(thread));
        }
        return result;
    }

    private static Object readValue(byte valueKind, PacketStream input, JDWPContext context) {
        switch (valueKind) {
            case TagConstants.BOOLEAN:
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
            case TagConstants.STRING:
            case TagConstants.ARRAY:
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

    private static Object readValue(PacketStream input, JDWPContext context) {
        byte valueKind = input.readByte();
        switch (valueKind) {
            case TagConstants.VOID:
                return Void.TYPE;
            case TagConstants.BOOLEAN:
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
            case TagConstants.BOOLEAN:
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

    private static void writeMethodResult(PacketStream reply, JDWPContext context, ThreadJob<?>.JobResult<?> result, Object thread, DebuggerController controller) {
        if (result.getException() != null) {
            reply.writeByte(TagConstants.OBJECT);
            reply.writeLong(0);
            reply.writeByte(TagConstants.OBJECT);
            Object guestException = context.getGuestException(result.getException());
            reply.writeLong(context.getIds().getIdAsLong(guestException));
            if (controller.getThreadSuspension().getSuspensionCount(thread) > 0) {
                controller.getGCPrevention().setActiveWhileSuspended(thread, guestException);
            }
        } else {
            Object value = context.toGuest(result.getResult());
            if (value != null) {
                byte tag = context.getTag(value);
                writeValue(tag, value, reply, true, context);
                if (controller.getThreadSuspension().getSuspensionCount(thread) > 0) {
                    controller.getGCPrevention().setActiveWhileSuspended(thread, value);
                }
            } else { // return value is null
                reply.writeByte(TagConstants.OBJECT);
                reply.writeLong(0);
            }
            // no exception, so zero object ID
            reply.writeByte(TagConstants.OBJECT);
            reply.writeLong(0);
        }
    }

    static boolean isSynthetic(int mod) {
        return (mod & ACC_SYNTHETIC) != 0;
    }

    private static int checkSyntheticFlag(int modBits) {
        int mod = modBits;
        if (isSynthetic(modBits)) {
            // JDWP has a different bit for synthetic
            mod &= ~ACC_SYNTHETIC;
            mod |= JDWP_SYNTHETIC;
        }
        return mod;
    }

    private static int getArgCount(String signature) {
        int startIndex = signature.indexOf('(') + 1;
        int endIndex = signature.indexOf(')');
        String parameterSig = signature.substring(startIndex, endIndex);
        int currentCount = 0;
        int currentIndex = 0;
        char[] charArray = parameterSig.toCharArray();
        while (currentIndex < charArray.length) {
            switch (charArray[currentIndex]) {
                case 'D':
                case 'J': {
                    currentCount += 2;
                    currentIndex++;
                    break;
                }
                case 'B':
                case 'C':
                case 'F':
                case 'I':
                case 'S':
                case 'Z': {
                    currentCount++;
                    currentIndex++;
                    break;
                }
                case 'L':
                    currentCount++;
                    currentIndex = parameterSig.indexOf(';', currentIndex) + 1;
                    break;
                case 'T':
                    throw new RuntimeException("unexpected type variable");
                case '[':
                    currentCount++;
                    currentIndex += parseArrayType(parameterSig, charArray, currentIndex + 1);
                    break;
                default:
                    throw new RuntimeException("should not reach here");
            }
        }
        return currentCount;
    }

    private static int parseArrayType(String signature, char[] charArray, int currentIndex) {
        switch (charArray[currentIndex]) {
            case 'D':
            case 'J':
            case 'B':
            case 'C':
            case 'F':
            case 'I':
            case 'S':
            case 'Z':
                return 2;
            case 'L':
                return 2 + signature.indexOf(';', currentIndex) - currentIndex;
            case 'T':
                throw new RuntimeException("unexpected type variable");
            case '[':
                return 1 + parseArrayType(signature, charArray, currentIndex + 1);
            default:
                throw new RuntimeException("should not reach here");
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

    private static ModuleRef verifyModule(long moduleId, PacketStream reply, JDWPContext context) {
        ModuleRef module;
        try {
            module = (ModuleRef) context.getIds().fromId((int) moduleId);
        } catch (ClassCastException ex) {
            reply.errorCode(ErrorCodes.INVALID_MODULE);
            return null;
        }
        return module;
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

    private static Object verifyThread(long threadId, PacketStream reply, JDWPContext context, boolean checkTerminated) {
        Object thread = context.getIds().fromId((int) threadId);

        if (thread == null) {
            reply.errorCode(ErrorCodes.INVALID_OBJECT);
            return null;
        }
        if (thread == context.getNullObject() || !context.isValidThread(thread, checkTerminated)) {
            reply.errorCode(ErrorCodes.INVALID_THREAD);
            return null;
        }
        return thread;
    }

    private static Object verifyThreadGroup(long threadGroupId, PacketStream reply, JDWPContext context) {
        Object threadGroup = context.getIds().fromId((int) threadGroupId);

        if (threadGroup == null || threadGroup == context.getNullObject()) {
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

    private static CallFrame verifyCallFrame(long frameId, PacketStream reply, JDWPContext context) {
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
