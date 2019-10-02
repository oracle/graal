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
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

class JDWP {

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
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                StaticObject[] allThreads = controller.getContext().getAllGuestThreads();
                reply.writeInt(allThreads.length);
                for (StaticObject t : allThreads) {
                    reply.writeByteArray(Ids.getId(t));
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

            static PacketStream createReply(Packet packet) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                return reply;
            }
        }

        static class CAPABILITIES_NEW {
            public static final int ID = 17;

            static PacketStream createReply(Packet packet) {
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);

                // TODO(Gregersen) - figure out what capabilities we want to expose
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

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long refTypeId = input.readLong();

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                Object object = Ids.fromId((int) refTypeId);
                Klass refType = null;
                if (object instanceof Klass) {
                    refType = (Klass) object;
                } else if (object instanceof StaticObject) {
                    StaticObject staticObject = (StaticObject) object;
                    refType = staticObject.getKlass();
                } else {
                    throw new RuntimeException("not implemented yet");
                }
                reply.writeString(refType.getType().toString());
                reply.writeString(""); // TODO(Gregersen) - generic signature
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
    }

    static class ObjectReference {
        public static final int ID = 9;

        static class REFERENCE_TYPE {
            public static final int ID = 1;

            static PacketStream createReply(Packet packet) {
                PacketStream input = new PacketStream(packet);
                long objectId = input.readLong();
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                StaticObject object = (StaticObject) Ids.fromId((int) objectId);
                Klass klass = object.getKlass();
                reply.writeByte(TypeTag.getKind(klass));
                reply.writeByteArray(Ids.getId(klass));
                return reply;
            }
        }
    }

    static class THREAD_REFERENCE {
        public static final int ID = 11;

        static class NAME {
            public static final int ID = 1;

            static PacketStream createReply(Packet packet, EspressoContext context) {
                PacketStream input = new PacketStream(packet);
                long threadId = input.readLong();
                StaticObject thread = (StaticObject) Ids.fromId((int)threadId);
                String threadName = ((StaticObject) context.getMeta().Thread_name.get(thread)).toString();

                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.writeString(threadName);
                return reply;
            }
        }

        static class RESUME {
            public static final int ID = 3;

            static PacketStream createReply(Packet packet, EspressoContext context) {
                PacketStream input = new PacketStream(packet);
                long threadId = input.readLong();
                StaticObject thread = (StaticObject) Ids.fromId((int)threadId);
                String threadName = ((StaticObject) context.getMeta().Thread_name.get(thread)).toString();
                // TODO(Gregersen) - implement resume call here
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
                if (Ids.fromId((int) threadId) == Ids.UNKNOWN) {
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
                reply.writeInt(ThreadSuspension.getSuspensionCount(thread));
                return reply;
            }
        }
    }

    static class THREAD_GROUP_REFERENCE {
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

    static class EventRequest {
        public static final int ID = 15;

        static class SET {
            public static final int ID = 1;
        }
    }
}
