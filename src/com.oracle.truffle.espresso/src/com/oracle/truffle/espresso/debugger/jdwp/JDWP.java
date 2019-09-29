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

class JDWP {

    static class VirtualMachine {
        public static final int ID = 1;

        static class VERSION {
            public static final int ID = 1;

            static PacketStream createReply(Packet packet) {
                PacketStream p = new PacketStream().replyPacket(packet.id, Packet.ReplyNoError);
                p.writeString(EspressoVirtualMachine.VM_Description);
                p.writeInt(1);
                p.writeInt(6);
                p.writeString(EspressoVirtualMachine.vmVersion);
                p.writeString(EspressoVirtualMachine.vmName);
                return p;
            }
        }

        static class ALL_THREADS {
            public static final int ID = 4;

            static PacketStream createReply(Packet packet, JDWPDebuggerController controller) {
                PacketStream p = new PacketStream().replyPacket(packet.id, Packet.ReplyNoError);
                Thread[] allThreads = controller.getContext().getAllActiveTrheads();
                p.writeInt(allThreads.length);
                for (Thread t : allThreads) {
                    p.writeByteArray(ObjectIds.getID(t));
                }
                return p;
            }
        }

        static class IDSIZES {
            public static final int ID = 7;

            static PacketStream createReply(Packet packet) {
                PacketStream p = new PacketStream().replyPacket(packet.id, Packet.ReplyNoError);
                p.writeInt(EspressoVirtualMachine.sizeofFieldRef);
                p.writeInt(EspressoVirtualMachine.sizeofMethodRef);
                p.writeInt(EspressoVirtualMachine.sizeofObjectRef);
                p.writeInt(EspressoVirtualMachine.sizeofClassRef);
                p.writeInt(EspressoVirtualMachine.sizeofFrameRef);
                return p;
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
