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

import com.oracle.truffle.espresso.debugger.BreakpointInfo;
import com.oracle.truffle.espresso.debugger.VMEventListener;
import com.oracle.truffle.espresso.debugger.VMEventListeners;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;

import java.util.regex.Pattern;

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
    private final VMEventListener eventListener;

    RequestedJDWPEvents(SocketConnection connection) {
        this.connection = connection;
        eventListener = new VMEventListenerImpl(connection);
        VMEventListeners.getDefault().registerListener(eventListener);
    }

    public PacketStream registerEvent(Packet packet, JDWPCommands callback) {

        PacketStream reply = null;
        PacketStream stream = new PacketStream(packet);

        byte eventKind = stream.readByte();
        byte suspendPolicy = stream.readByte();
        int modifiers = stream.readInt();

        for (int i = 0; i < modifiers; i++) {
            byte modCount = stream.readByte();
            handleModCount(stream, modCount, packet.id, suspendPolicy, callback);
        }

        switch (eventKind) {
            case BREAKPOINT:
                reply = toReply(packet);
                break;
            case THREAD_START:
                eventListener.addThreadStartedRequestId(packet.id);
                reply = toReply(packet);
                break;
            case THREAD_DEATH:
                eventListener.addThreadDiedRequestId(packet.id);
                reply = toReply(packet);
                break;
            case CLASS_PREPARE:
                reply = toReply(packet);
                break;
            case CLASS_UNLOAD:
                eventListener.addClassUnloadRequestId(packet.id);
                reply = toReply(packet);
                break;
            default:
                System.out.println("unhandled event kind " + eventKind);
                break;
        }

        return reply;
    }

    private PacketStream toReply(Packet packet) {
        PacketStream reply;
        reply = new PacketStream().replyPacket().id(packet.id);
        reply.writeInt(packet.id);
        return reply;
    }

    private void handleModCount(PacketStream stream, byte modCount, int id, byte suspendPolicy, JDWPCommands callback) {
        switch (modCount) {
            case 1:
                break;
            case 2:
                break;
            case 3:
                break;
            case 4:
                break;
            case 5: // class prepare positive pattern
                String classPattern = stream.readString();
                eventListener.addClassPrepareRequest(new ClassPrepareRequest(Pattern.compile(classPattern), id));
                break;
            case 6:
                break;
            case 7: // breakpoint
                byte typeTag = stream.readByte();
                long classId = stream.readLong();
                long methodId = stream.readLong();
                long bci = stream.readLong();
                BreakpointInfo info = new BreakpointInfo(id, typeTag, classId, methodId, bci);

                Klass klass = (Klass) Ids.fromId((int) classId);
                String slashName = ClassNameUtils.fromInternalObjectNametoSlashName(klass.getType().toString());
                Method method = (Method) Ids.fromId((int) methodId);
                int line = method.BCItoLineNumber((int) bci);
                callback.createLineBreakpointCommand(slashName, line, suspendPolicy, info);
                break;
            case 8:
                break;
            case 9:
                break;
            case 10:
                break;
            case 11:
                break;
            case 12:
                break;
            default:
                break;
        }
    }
}
