package com.oracle.truffle.api.operation.serialization;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

public class ByteBufferDataInput {

    public static DataInput createDataInput(ByteBuffer buffer) {
        return new Impl(buffer);
    }

    private static class Impl implements DataInput {

        private final ByteBuffer buffer;

        private Impl(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        private static EOFException wrap(BufferUnderflowException ex) {
            EOFException eof = new EOFException();
            eof.addSuppressed(ex);
            return eof;
        }

        public void readFully(byte[] b) throws IOException {
            readFully(b, 0, b.length);
        }

        public void readFully(byte[] b, int off, int len) throws IOException {
            try {
                buffer.get(b, 0, b.length);
            } catch (BufferUnderflowException ex) {
                throw wrap(ex);
            }
        }

        public int skipBytes(int n) throws IOException {
            int skip = buffer.remaining() > n ? buffer.remaining() : n;
            buffer.position(buffer.position() + skip);
            return skip;
        }

        public boolean readBoolean() throws IOException {
            try {
                return buffer.get() != 0;
            } catch (BufferUnderflowException ex) {
                throw wrap(ex);
            }
        }

        public byte readByte() throws IOException {
            try {
                return buffer.get();
            } catch (BufferUnderflowException ex) {
                throw wrap(ex);
            }
        }

        public int readUnsignedByte() throws IOException {
            try {
                return buffer.get() & 0xff;
            } catch (BufferUnderflowException ex) {
                throw wrap(ex);
            }
        }

        public short readShort() throws IOException {
            try {
                return buffer.getShort();
            } catch (BufferUnderflowException ex) {
                throw wrap(ex);
            }
        }

        public int readUnsignedShort() throws IOException {
            try {
                return buffer.getShort() & 0xffff;
            } catch (BufferUnderflowException ex) {
                throw wrap(ex);
            }
        }

        public char readChar() throws IOException {
            try {
                return buffer.getChar();
            } catch (BufferUnderflowException ex) {
                throw wrap(ex);
            }
        }

        public int readInt() throws IOException {
            try {
                return buffer.getInt();
            } catch (BufferUnderflowException ex) {
                throw wrap(ex);
            }
        }

        public long readLong() throws IOException {
            try {
                return buffer.getLong();
            } catch (BufferUnderflowException ex) {
                throw wrap(ex);
            }
        }

        public float readFloat() throws IOException {
            try {
                return buffer.getFloat();
            } catch (BufferUnderflowException ex) {
                throw wrap(ex);
            }
        }

        public double readDouble() throws IOException {
            try {
                return buffer.getDouble();
            } catch (BufferUnderflowException ex) {
                throw wrap(ex);
            }
        }

        /**
         * Reads the next line of text from the input stream. It reads successive bytes, converting
         * each byte separately into a character, until it encounters a line terminator or end of
         * file; the characters read are then returned as a {@code String}. Note that because this
         * method processes bytes, it does not support input of the full Unicode character set.
         * <p>
         * If end of file is encountered before even one byte can be read, then {@code null} is
         * returned. Otherwise, each byte that is read is converted to type {@code char} by
         * zero-extension. If the character {@code '\n'} is encountered, it is discarded and reading
         * ceases. If the character {@code '\r'} is encountered, it is discarded and, if the
         * following byte converts &#32;to the character {@code '\n'}, then that is discarded also;
         * reading then ceases. If end of file is encountered before either of the characters
         * {@code '\n'} and {@code '\r'} is encountered, reading ceases. Once reading has ceased, a
         * {@code String} is returned that contains all the characters read and not discarded, taken
         * in order. Note that every character in this string will have a value less than
         * {@code \u005Cu0100}, that is, {@code (char)256}.
         *
         * @return the next line of text from the input stream, or {@code null} if the end of file
         *         is encountered before a byte can be read.
         * @exception IOException if an I/O error occurs.
         */
        public String readLine() throws IOException {
            int remaining = buffer.remaining();
            if (remaining == 0) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            while (remaining > 0) {
                char c = (char) buffer.get();
                if (c == '\r') {
                    if (remaining > 1 && (char) buffer.get() != '\n') {
                        buffer.position(buffer.position() - 1);
                    }
                    break;
                } else if (c == '\n') {
                    break;
                } else {
                    sb.append(c);
                    remaining--;
                }
            }

            return sb.toString();
        }

        public String readUTF() throws IOException {
            return DataInputStream.readUTF(this);
        }
    }
}
