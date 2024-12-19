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

public final class UnmodifiablePacket implements Packet {

    private final int id;
    private final byte flags;
    private final byte commandSet;
    private final byte command;
    private final short errorCode;
    private final byte[] packetBytes;

    public static UnmodifiablePacket parseAndWrap(byte[] packetBytes) {
        return new UnmodifiablePacket(packetBytes);
    }

    private UnmodifiablePacket(byte[] packetBytes) {
        if (packetBytes.length < HEADER_SIZE) {
            throw new IllegalArgumentException();
        }

        Reader reader = new Reader() {
            private int position;

            @Override
            public int readByte() {
                return Byte.toUnsignedInt(packetBytes[position++]);
            }

            @Override
            public boolean isEndOfInput() {
                return position >= packetBytes.length;
            }
        };
        int length = reader.readInt();
        if (length != packetBytes.length) {
            throw new IllegalArgumentException();
        }

        this.id = reader.readInt();
        this.flags = (byte) reader.readByte();

        if (isReply()) {
            this.errorCode = reader.readShort();
            this.commandSet = 0;
            this.command = 0;
        } else {
            this.errorCode = REPLY_NO_ERROR;
            this.commandSet = (byte) reader.readByte();
            this.command = (byte) reader.readByte();
        }
        this.packetBytes = packetBytes;
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public byte flags() {
        return flags;
    }

    @Override
    public short errorCode() {
        return errorCode;
    }

    @Override
    public byte commandSet() {
        return commandSet;
    }

    @Override
    public byte command() {
        return command;
    }

    @Override
    public int dataSize() {
        return packetBytes.length - HEADER_SIZE;
    }

    @Override
    public byte data(int index) {
        if (index < 0) {
            throw new ArrayIndexOutOfBoundsException();
        }
        return packetBytes[index + HEADER_SIZE];
    }
}
