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

import java.io.ByteArrayOutputStream;

public final class PacketStream {

    private final Packet packet;
    private int readPosition;

    private final ByteArrayOutputStream dataStream = new ByteArrayOutputStream();

    PacketStream() {
        packet = new Packet();

    }

    PacketStream(Packet packet) {
        this.packet = packet;
    }

    PacketStream commandPacket() {
        packet.id = Packet.uID;
        return this;
    }

    PacketStream command(int command) {
        packet.cmd = (short) command;
        return this;
    }

    PacketStream commandSet(int commandSet) {
        packet.cmdSet = (short) commandSet;
        return this;
    }

    PacketStream replyPacket() {
        packet.flags = Packet.Reply;
        packet.errorCode = Packet.ReplyNoError;
        return this;
    }

    PacketStream id(int id) {
        packet.id = id;
        return this;
    }

    PacketStream errorCode(int errorCode) {
        packet.errorCode = (short) errorCode;
        return this;
    }

    byte[] prepareForShipment() {
        packet.data = dataStream.toByteArray();
        return packet.toByteArray();
    }

    public void writeBoolean(boolean data) {
        if (data) {
            dataStream.write(1);
        } else {
            dataStream.write(0);
        }
    }

    public void writeByte(byte data) {
        dataStream.write(data);
    }

    public void writeChar(char data) {
        dataStream.write((byte) ((data >>> 8) & 0xFF));
        dataStream.write((byte) ((data >>> 0) & 0xFF));
    }

    public void writeShort(short data) {
        dataStream.write((byte) ((data >>> 8) & 0xFF));
        dataStream.write((byte) ((data >>> 0) & 0xFF));
    }

    public void writeInt(int data) {
        dataStream.write((byte) ((data >>> 24) & 0xFF));
        dataStream.write((byte) ((data >>> 16) & 0xFF));
        dataStream.write((byte) ((data >>> 8) & 0xFF));
        dataStream.write((byte) ((data >>> 0) & 0xFF));
    }

    public void writeLong(long data) {
        dataStream.write((byte) ((data >>> 56) & 0xFF));
        dataStream.write((byte) ((data >>> 48) & 0xFF));
        dataStream.write((byte) ((data >>> 40) & 0xFF));
        dataStream.write((byte) ((data >>> 32) & 0xFF));

        dataStream.write((byte) ((data >>> 24) & 0xFF));
        dataStream.write((byte) ((data >>> 16) & 0xFF));
        dataStream.write((byte) ((data >>> 8) & 0xFF));
        dataStream.write((byte) ((data >>> 0) & 0xFF));
    }

    public void writeFloat(float data) {
        writeInt(Float.floatToIntBits(data));
    }

    public void writeDouble(double data) {
        writeLong(Double.doubleToLongBits(data));
    }

    void writeByteArray(byte[] data) {
        dataStream.write(data, 0, data.length);
    }

    public void writeString(String string) {
        try {
            byte[] stringBytes = string.getBytes("UTF8");
            writeInt(stringBytes.length);
            writeByteArray(stringBytes);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException("Cannot convert string to UTF8 bytes");
        }
    }

    byte readByte() {
        byte ret = packet.data[readPosition];
        readPosition += 1;
        return ret;
    }

    boolean readBoolean() {
        byte ret = readByte();
        return (ret != 0);
    }

    char readChar() {
        int b1, b2;

        b1 = packet.data[readPosition++] & 0xff;
        b2 = packet.data[readPosition++] & 0xff;

        return (char)((b1 << 8) + b2);
    }

    short readShort() {
        int b1, b2;

        b1 = packet.data[readPosition++] & 0xff;
        b2 = packet.data[readPosition++] & 0xff;

        return (short)((b1 << 8) + b2);
    }

    int readInt() {
        int b1,b2,b3,b4;

        b1 = packet.data[readPosition++] & 0xff;
        b2 = packet.data[readPosition++] & 0xff;
        b3 = packet.data[readPosition++] & 0xff;
        b4 = packet.data[readPosition++] & 0xff;

        return ((b1 << 24) + (b2 << 16) + (b3 << 8) + b4);
    }

    long readLong() {
        long b1,b2,b3,b4;
        long b5,b6,b7,b8;

        b1 = packet.data[readPosition++] & 0xff;
        b2 = packet.data[readPosition++] & 0xff;
        b3 = packet.data[readPosition++] & 0xff;
        b4 = packet.data[readPosition++] & 0xff;

        b5 = packet.data[readPosition++] & 0xff;
        b6 = packet.data[readPosition++] & 0xff;
        b7 = packet.data[readPosition++] & 0xff;
        b8 = packet.data[readPosition++] & 0xff;

        return ((b1 << 56) + (b2 << 48) + (b3 << 40) + (b4 << 32)
                + (b5 << 24) + (b6 << 16) + (b7 << 8) + b8);
    }

    float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    String readString() {
        String ret;
        int len = readInt();

        try {
            ret = new String(packet.data, readPosition, len, "UTF8");
        } catch(java.io.UnsupportedEncodingException e) {
            ret = "Conversion error!";
        }
        readPosition += len;
        return ret;
    }
}
