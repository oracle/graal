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

import com.oracle.truffle.espresso.classfile.LineNumberTable;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;

class JDWP {

    static class VirtualMachine {
        public static final int ID = 1;

        static class VERSION {
            public static final int ID = 1;

            static PacketStream createReply(Packet packet) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id).errorCode(Packet.ReplyNoError);
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
                PacketStream reply = new PacketStream().replyPacket().id(packet.id).errorCode(Packet.ReplyNoError);
                reply.writeInt(loaded.length);
                for (Klass klass : loaded) {
                    reply.writeByte(TypeTag.getKind(klass));
                    reply.writeByteArray(Ids.getId(klass));
                    if (klass instanceof ObjectKlass) {
                        ObjectKlass objectKlass = (ObjectKlass) klass;
                        reply.writeInt(ClassStatusConstants.fromEspressoStatus(objectKlass.getState()));
                    } else {
                        // TODO(Gregersen) - not implemented for arrays and primitive types
                        throw new RuntimeException("not implementet yet");
                    }
                }

                return reply;
            }
        }

        static class ALL_THREADS {
            public static final int ID = 4;

            static PacketStream createReply(Packet packet, JDWPDebuggerController controller) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id).errorCode(Packet.ReplyNoError);
                Thread[] allThreads = controller.getContext().getAllActiveTrheads();
                reply.writeInt(allThreads.length);
                for (Thread t : allThreads) {
                    reply.writeByteArray(Ids.getId(t));
                }
                return reply;
            }
        }

        static class IDSIZES {
            public static final int ID = 7;

            static PacketStream createReply(Packet packet) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id).errorCode(Packet.ReplyNoError);
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

            static PacketStream createReply(Packet packet) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id).errorCode(Packet.ReplyNoError);
                return reply;
            }
        }

        static class CAPABILITIES_NEW {
            public static final int ID = 17;

            static PacketStream createReply(Packet packet) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id).errorCode(Packet.ReplyNoError);
                reply.writeBoolean(true);
                reply.writeBoolean(true);
                reply.writeBoolean(false);
                reply.writeBoolean(true);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                reply.writeBoolean(false);
                return reply;
            }
        }
    }

    static class ReferenceType {
        public static final int ID = 2;

        static class SIGNATURE_WITH_GENERIC {
            public static final int ID = 13;
        }
        static class METHODS_WITH_GENERIC {
            public static final int ID = 15;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long refTypeId = input.readLong();
                Klass refType = (Klass) Ids.fromId((int) refTypeId);

                PacketStream reply = new PacketStream().replyPacket().id(packet.id).errorCode(Packet.ReplyNoError);
                Method[] declaredMethods = refType.getDeclaredMethods();
                int numDeclaredMethods = declaredMethods.length;
                reply.writeInt(numDeclaredMethods);
                for (Method method : declaredMethods) {
                    reply.writeByteArray(Ids.getId(method));
                    reply.writeString(method.getName().toString());
                    reply.writeString(method.getRawSignature().toString());
                    reply.writeString(""); // TODO(Gregersen) - get the generic signature
                    reply.writeInt(method.getModifiers());
                }
                return reply;
            }
        }
    }

    static class METHOD {
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
                PacketStream reply = new PacketStream().replyPacket().id(packet.id).errorCode(Packet.ReplyNoError);

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
    }

    static class ObjectReference {
        public static final int ID = 9;

        static class REFERENCE_TYPE {
            public static final int ID = 1;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long objectId = input.readLong();
                PacketStream reply = new PacketStream().replyPacket().id(packet.id).errorCode(Packet.ReplyNoError);
                Object obj = Ids.fromId((int)objectId);
                reply.writeByte(TypeTag.CLASS); // TODO(Gregersen) - determine actual type
                reply.writeByteArray(Ids.toByteArray((int)objectId)); // TODO(Gregersen) - determine actual referenceType ID
                return reply;
            }
        }
    }

    static class EventRequest {
        public static final int ID = 15;

        static class SET {
            public static final int ID = 1;
        }
    }
}
