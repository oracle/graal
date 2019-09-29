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

import com.oracle.truffle.espresso.debugger.VMEventListener;
import com.oracle.truffle.espresso.debugger.VMEventListeners;

public class RequestedJDWPEvents {

    public static final byte SINGLE_STEP = 1;
    public static final byte BREAKPOINT = 2;
    public static final byte FRAME_POP = 3;
    public static final byte EXCEPTION = 4;
    public static final byte USER_DEFINED = 5;
    public static final byte THREAD_START = 6;
    public static final byte THREAD_DEATH = 7;
    public static final byte CLASS_PREPARE = 8;
    public static final byte CLASS_UNLOAD = 9;
    public static final byte CLASS_LOAD = 10;
    public static final byte FIELD_ACCESS = 20;
    public static final byte FIELD_MODIFICATION = 21;
    public static final byte EXCEPTION_CATCH = 30;
    public static final byte METHOD_ENTRY = 40;
    public static final byte METHOD_EXIT = 41;
    public static final byte METHOD_EXIT_WITH_RETURN_VALUE = 42;
    public static final byte MONITOR_CONTENDED_ENTER = 43;
    public static final byte MONITOR_CONTENDED_ENTERED = 44;
    public static final byte MONITOR_WAIT = 45;
    public static final byte MONITOR_WAITED = 46;
    public static final byte VM_START = 90;
    public static final byte VM_DEATH = 99;
    public static final byte VM_DISCONNECTED = 100;

    private final SocketConnection connection;
    private final VMEventListener VMEventListener;

    RequestedJDWPEvents(SocketConnection connection) {
        this.connection = connection;
        VMEventListener = new VMEventListenerImpl(connection);
    }

    public PacketStream registerEvent(Packet packet) {

        PacketStream reply = null;
        PacketStream stream = new PacketStream(packet);

        byte eventKind = stream.readByte();
        byte suspendPolicy = stream.readByte();
        int modifiers = stream.readInt();

        //System.out.println("event kind is: " + eventKind);

        for (int i = 0; i < modifiers; i++) {
            // TODO(Gregersen) - not implemented
        }

        switch (eventKind) {
            case THREAD_START:
                VMEventListener.addThreadStartedRequestId(packet.id);
                VMEventListeners.getDefault().registerListener(VMEventListener);
                reply = new PacketStream().replyPacket(packet.id, Packet.ReplyNoError);
                reply.writeInt(packet.id);
                break;
            case THREAD_DEATH:
                VMEventListener.addThreadDiedRequestId(packet.id);
                VMEventListeners.getDefault().registerListener(VMEventListener);
                reply = new PacketStream().replyPacket(packet.id, Packet.ReplyNoError);
                reply.writeInt(packet.id);
                break;
            case CLASS_PREPARE:
                VMEventListener.addClassPrepareRequestId(packet.id);
                VMEventListeners.getDefault().registerListener(VMEventListener);
                reply = new PacketStream().replyPacket(packet.id, Packet.ReplyNoError);
                reply.writeInt(packet.id);
                break;
            case CLASS_UNLOAD:
                VMEventListener.addClassUnloadRequestId(packet.id);
                reply = new PacketStream().replyPacket(packet.id, Packet.ReplyNoError);
                reply.writeInt(packet.id);
                break;
            default:
                break;
        }

        return reply;
    }



}
