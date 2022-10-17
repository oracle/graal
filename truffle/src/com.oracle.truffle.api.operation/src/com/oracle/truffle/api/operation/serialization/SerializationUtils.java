package com.oracle.truffle.api.operation.serialization;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

public class SerializationUtils {

    private SerializationUtils() {
    }

    public static DataInput createDataInput(ByteBuffer buffer) {
        return new ByteBufferDataInput(buffer);
    }

    private static class ByteBufferDataInput implements DataInput {

        private final ByteBuffer buffer;
        private char[] lineBuffer;

        private ByteBufferDataInput(ByteBuffer buffer) {
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

        private static int get(ByteBuffer buf) {
            if (buf.position() >= buf.limit()) {
                return -1;
            } else {
                return buf.get();
            }
        }

        /**
         * Modified from {@link DataInputStream#readLine()}.
         */
        @Deprecated
        public String readLine() throws IOException {
            char[] buf = lineBuffer;

            if (buf == null) {
                buf = lineBuffer = new char[128];
            }

            int room = buf.length;
            int offset = 0;
            int c;

            loop: while (true) {
                switch (c = get(buffer)) {
                    case -1:
                    case '\n':
                        break loop;

                    case '\r':
                        int c2 = get(buffer);
                        if ((c2 != '\n') && (c2 != -1)) {
                            buffer.position(buffer.position() - 1);
                        }
                        break loop;

                    default:
                        if (--room < 0) {
                            buf = new char[offset + 128];
                            room = buf.length - offset - 1;
                            System.arraycopy(lineBuffer, 0, buf, 0, offset);
                            lineBuffer = buf;
                        }
                        buf[offset++] = (char) c;
                        break;
                }
            }
            if ((c == -1) && (offset == 0)) {
                return null;
            }
            return String.copyValueOf(buf, 0, offset);
        }

        public String readUTF() throws IOException {
            return DataInputStream.readUTF(this);
        }
    }
}
