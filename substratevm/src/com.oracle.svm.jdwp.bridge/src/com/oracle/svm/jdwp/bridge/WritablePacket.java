/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.bridge;

public final class WritablePacket implements Packet {

    private int id;
    private byte flags;
    private short errorCode;
    private byte commandSet;
    private byte command;

    private PacketWriterBuffer dataWriter = new PacketWriterBufferImpl();

    @Override
    public int id() {
        return id;
    }

    public WritablePacket id(int newId) {
        this.id = newId;
        return this;
    }

    @Override
    public byte flags() {
        return flags;
    }

    public WritablePacket flags(byte newFlags) {
        this.flags = newFlags;
        return this;
    }

    @Override
    public short errorCode() {
        return errorCode;
    }

    public WritablePacket errorCode(short newErrorCode) {
        this.errorCode = newErrorCode;
        return this;
    }

    public WritablePacket errorCode(ErrorCode newErrorCode) {
        this.errorCode = (short) newErrorCode.value();
        return this;
    }

    @Override
    public byte commandSet() {
        return commandSet;
    }

    public WritablePacket commandSet(byte newCommandSet) {
        this.commandSet = newCommandSet;
        return this;
    }

    @Override
    public byte command() {
        return command;
    }

    public WritablePacket command(byte newCommand) {
        this.command = newCommand;
        return this;
    }

    @Override
    public int dataSize() {
        return dataWriter.size();
    }

    @Override
    public byte data(int index) {
        return dataWriter.byteAt(index);
    }

    public Packet.Writer dataWriter() {
        return this.dataWriter;
    }

    public WritablePacket dataWriter(PacketWriterBuffer newDataWriter) {
        this.dataWriter = newDataWriter;
        return this;
    }

    public static WritablePacket newReplyTo(Packet pkt) {
        return new WritablePacket().id(pkt.id()).flags((byte) REPLY).errorCode((byte) REPLY_NO_ERROR);
    }

    public static WritablePacket commandPacket() {
        // Note: Command packet uses the current Packet.uID (without incrementing it?).
        return new WritablePacket().commandSet((byte) JDWP.Event).command((byte) JDWP.Event_Composite);
    }
}
