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

import java.nio.charset.StandardCharsets;

public interface Packet {

    int NO_FLAGS = 0x0;
    int REPLY = 0x80;
    int REPLY_NO_ERROR = 0x0;
    int HEADER_SIZE = 11;

    /**
     * The size, in bytes, of JDWP packet, including the {@link #HEADER_SIZE header} or not.
     * 
     * @param includeHeader specify if the {@link #HEADER_SIZE header bytes} are included or not
     */
    default int length(boolean includeHeader) {
        int packetLength = dataSize();
        if (includeHeader) {
            packetLength += HEADER_SIZE;
        }
        return packetLength;
    }

    /**
     * The id field is used to uniquely identify each packet command/reply pair. A reply packet has
     * the same id as the command packet to which it replies. This allows asynchronous commands and
     * replies to be matched. The id field must be unique among all outstanding commands sent from
     * one source. (Outstanding commands originating from the debugger may use the same id as
     * outstanding commands originating from the target VM.) Other than that, there are no
     * requirements on the allocation of id's.
     *
     * A simple monotonic counter should be adequate for most implementations. It will allow 2^32
     * unique outstanding packets and is the simplest implementation alternative.
     */
    int id();

    /**
     * Flags are used to alter how any command is queued and processed and to tag command packets
     * that originate from the target VM. There is currently one flag bits defined; future versions
     * of the protocol may define additional flags. The reply bit {@code 0x80}, when set, indicates
     * that this packet is a reply.
     */
    byte flags();

    default boolean isReply() {
        return (flags() & REPLY) != 0;
    }

    /**
     * This field is used to indicate if the command packet that is being replied to was
     * successfully processed. A value of zero indicates success, a non-zero value indicates an
     * error. The error code returned may be specific to each command set/command, but it is often
     * mapped to a JVM TI error code.
     */
    short errorCode();

    /**
     * This field is useful as a means for grouping commands in a meaningful way. The Sun defined
     * command sets are used to group commands by the interfaces they support in the JDI. For
     * example, all commands that support the JDI VirtualMachine interface are grouped in a
     * VirtualMachine command set. The command set space is roughly divided as follows:
     * <ul>
     * <li>0 - 63 Sets of commands sent to the target VM</li>
     * <li>64 - 127 Sets of commands sent to the debugger</li>
     * <li>128 - 256 Vendor-defined commands and extensions.</li>
     * </ul>
     */
    byte commandSet();

    /**
     * This field identifies a particular command in a command set. This field, together with the
     * command set field, is used to indicate how the command packet should be processed. More
     * succinctly, they tell the receiver what to do. Specific commands are presented later in this
     * document.
     */
    byte command();

    /**
     * The size in bytes of the payload of this packet.
     */
    int dataSize();

    /**
     * Returns the byte at the specified position in this packet's payload.
     *
     * @param index index of the bytes to return
     * @throws IndexOutOfBoundsException if the index is out of range
     *             ({@code index < 0 || index >= dataSize()})
     */
    byte data(int index);

    default byte[] toByteArray() {
        int length = this.length(true);
        PacketWriterBuffer writer = new PacketWriterBufferImpl(length);
        writer.writeInt(length);
        writer.writeInt(id());
        writer.writeByte(flags());
        if (this.isReply()) {
            writer.writeShort(errorCode());
        } else {
            writer.writeByte(commandSet());
            writer.writeByte(command());
        }
        int dataSize = this.dataSize();
        for (int i = 0; i < dataSize; ++i) {
            writer.writeByte(data(i));
        }
        return writer.toByteArray();
    }

    default Reader newDataReader() {
        return new Reader() {

            private int position;

            @Override
            public int readByte() {
                return Byte.toUnsignedInt(data(position++));
            }

            @Override
            public boolean isEndOfInput() {
                return position >= dataSize();
            }
        };
    }

    /**
     * Utility to read data from JDWP packets in big-endian, avoids using JDK code to prevent the
     * debugger to breakpoint itself.
     */
    interface Reader {

        /**
         * Reads returns a single byte as an unsigned int. To signal the end-of-input a negative
         * integer can be returned or an exception thrown.
         */
        int readByte();

        default boolean readBoolean() {
            return (readByte() != 0);
        }

        default char readChar() {
            return (char) readShort();
        }

        default short readShort() {
            int b0 = readByte() & 0xff;
            int b1 = readByte() & 0xff;
            return (short) ((b0 << 8) | b1);
        }

        default int readInt() {
            int b0 = readByte() & 0xff;
            int b1 = readByte() & 0xff;
            int b2 = readByte() & 0xff;
            int b3 = readByte() & 0xff;
            return ((b0 << 24) | (b1 << 16) | (b2 << 8) | b3);
        }

        default long readLong() {
            long b0 = readByte() & 0xff;
            long b1 = readByte() & 0xff;
            long b2 = readByte() & 0xff;
            long b3 = readByte() & 0xff;
            long b4 = readByte() & 0xff;
            long b5 = readByte() & 0xff;
            long b6 = readByte() & 0xff;
            long b7 = readByte() & 0xff;
            return ((b0 << 56) | (b1 << 48) | (b2 << 40) | (b3 << 32) | (b4 << 24) | (b5 << 16) | (b6 << 8) | b7);
        }

        default float readFloat() {
            return Float.intBitsToFloat(readInt());
        }

        default double readDouble() {
            return Double.longBitsToDouble(readLong());
        }

        default void readBytes(byte[] bytes) {
            readBytes(bytes, 0, bytes.length);
        }

        default void readBytes(byte[] bytes, int offset, int length) {
            if (offset < 0 || length < 0 || offset > bytes.length - length) {
                throw new ArrayIndexOutOfBoundsException();
            }
            for (int i = 0; i < length; i++) {
                bytes[offset + i] = (byte) (readByte() & 0xff);
            }
        }

        default String readString() {
            int length = readInt();
            byte[] bytes = new byte[length];
            readBytes(bytes, 0, length);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        default boolean isEndOfInput() {
            return false;
        }
    }

    /**
     * Utility to write payloads for JDWP packets in big-endian, avoids using JDK code to prevent
     * the debugger to breakpoint itself.
     */
    interface Writer {
        /**
         * Writes a single byte, represented by the least-significant 8-bits of the given integer,
         * the remaining bits are ignored.
         */
        void writeByte(int value);

        default void writeBoolean(boolean value) {
            writeByte(value ? 1 : 0);
        }

        default void writeChar(char value) {
            writeShort((short) value);
        }

        default void writeShort(short value) {
            writeByte(value >> 8);
            writeByte(value);
        }

        default void writeInt(int value) {
            writeByte(value >> 24);
            writeByte(value >> 16);
            writeByte(value >> 8);
            writeByte(value);
        }

        default void writeLong(long value) {
            writeByte((int) (value >> 56));
            writeByte((int) (value >> 48));
            writeByte((int) (value >> 40));
            writeByte((int) (value >> 32));
            writeByte((int) (value >> 24));
            writeByte((int) (value >> 16));
            writeByte((int) (value >> 8));
            writeByte((int) value);
        }

        default void writeFloat(float value) {
            writeInt(Float.floatToRawIntBits(value));
        }

        default void writeDouble(double value) {
            writeLong(Double.doubleToRawLongBits(value));
        }

        default void writeBytes(byte[] bytes) {
            writeBytes(bytes, 0, bytes.length);
        }

        default void writeBytes(byte[] bytes, int offset, int length) {
            if (offset < 0 || length < 0 || offset > bytes.length - length) {
                throw new ArrayIndexOutOfBoundsException();
            }
            for (int i = 0; i < length; ++i) {
                writeByte(bytes[offset + i]);
            }
        }

        default void writeString(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            writeInt(bytes.length);
            writeBytes(bytes);
        }
    }
}
