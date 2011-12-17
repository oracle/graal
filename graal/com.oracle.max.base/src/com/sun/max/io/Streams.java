/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.io;

import java.io.*;

import com.sun.max.lang.*;

/**
 * Supplementary java.io utils.
 */
public final class Streams {

    private Streams() {
    }

    public static void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        final byte[] buffer = new byte[8192];
        int count;
        while ((count = inputStream.read(buffer, 0, buffer.length)) > 0) {
            outputStream.write(buffer, 0, count);
        }
        outputStream.flush();
    }

    public static void copy(Reader reader, Writer writer) throws IOException {
        final char[] buffer = new char[8192];
        int count;
        while ((count = reader.read(buffer, 0, buffer.length)) > 0) {
            writer.write(buffer, 0, count);
        }
        writer.flush();
    }

    public static boolean equals(InputStream inputStream1, InputStream inputStream2) throws IOException {
        final int n = 8192;
        final byte[] buffer1 = new byte[n];
        final byte[] buffer2 = new byte[n];
        while (true) {
            final int n1 = inputStream1.read(buffer1, 0, n);
            final int n2 = inputStream2.read(buffer2, 0, n);
            if (n1 != n2) {
                return false;
            }
            if (n1 <= 0) {
                return true;
            }
            if (!Bytes.equals(buffer1, buffer2, n)) {
                return false;
            }
        }
    }

    public static final class Redirector extends Thread {

        private final InputStream inputStream;
        private final OutputStream outputStream;
        private final String name;
        private final int maxLines;
        private final Process process;
        private boolean closed;

        private Redirector(Process process, InputStream inputStream, OutputStream outputStream, String name, int maxLines) {
            super("StreamRedirector{" + name + "}");
            this.inputStream = inputStream;
            this.outputStream = outputStream;
            this.name = name;
            this.maxLines = maxLines;
            this.process = process;
            start();
        }

        public void close() {
            closed = true;
        }

        @Override
        public void run() {
            try {
                try {
                    int line = 1;
                    while (!closed) {
                        if (inputStream.available() == 0) {
                            // A busy yielding loop is used so that this thread can be
                            // stopped via a call to close() by another thread. Otherwise,
                            // this thread could be blocked forever on an input stream
                            // that is not closed and does not have any available data.
                            // The prime example of course is System.in.
                            Thread.yield();
                            // wait for a few milliseconds to avoid eating too much CPU.
                            Thread.sleep(10);
                            continue;
                        }

                        final int b = inputStream.read();
                        if (b < 0) {
                            return;
                        }
                        if (line <= maxLines) {
                            outputStream.write(b);
                        }
                        if (b == '\n') {
                            if (line == maxLines) {
                                outputStream.write(("<redirected stream concatenated after " + maxLines + " lines>" + System.getProperty("line.separator", "\n")).getBytes());
                            }
                            ++line;
                        }
                    }
                    outputStream.flush();
                } catch (IOException ioe) {
                    try {
                        process.exitValue();

                        // This just means the process was terminated and the relevant pipe no longer exists
                    } catch (IllegalThreadStateException e) {
                        // Some other unexpected IO error occurred -> rethrow
                        throw e;
                    }
                }
            } catch (Throwable throwable) {
                if (name != null) {
                    System.err.println("Error while redirecting sub-process stream for \"" + name + "\"");
                }
                throwable.printStackTrace();
            }
        }

    }

    public static Redirector redirect(Process process, InputStream inputStream, OutputStream outputStream, String name, int maxLines) {
        return new Redirector(process, inputStream, outputStream, name, maxLines);
    }

    public static Redirector redirect(Process process, InputStream inputStream, OutputStream outputStream, String name) {
        return redirect(process, inputStream, outputStream, name, Integer.MAX_VALUE);
    }

    public static Redirector redirect(Process process, InputStream inputStream, OutputStream outputStream) {
        return redirect(process, inputStream, outputStream, null, Integer.MAX_VALUE);
    }

    /**
     * Scans a given buffered input stream for a given sequence of bytes. If the sequence is found, then the read
     * position of the stream is immediately after the sequence. Otherwise, the read position is at the end of the
     * stream.
     *
     * @param stream
     *            the stream to search
     * @param bytes
     *            the byte pattern to search for
     * @return true if {@code bytes} is found in {@code stream}
     */
    public static boolean search(BufferedInputStream stream, byte[] bytes) throws IOException {
        if (bytes.length == 0) {
            return true;
        }
        int b1 = stream.read();
    top:
        while (b1 != -1) {
            if (b1 == (bytes[0] & 0xff)) {
                for (int i = 1; i < bytes.length; ++i) {
                    b1 = stream.read();
                    if (b1 != (bytes[i] & 0xff)) {
                        continue top;
                    }
                }
                return true;
            }
            b1 = stream.read();
        }
        return false;
    }

    /**
     * Scans a given buffered reader for a given sequence of characters. If the sequence is found, then the read
     * position of the reader is immediately after the sequence. Otherwise, the read position is at the end of the
     * reader.
     *
     * @param reader
     *            the reader to search
     * @param chars
     *            the char pattern to search for
     * @return true if {@code chars} is found in {@code reader}
     */
    public static boolean search(BufferedReader reader, char[] chars) throws IOException {
        if (chars.length == 0) {
            return true;
        }
        int c1 = reader.read();
    top:
        while (c1 != -1) {
            if (c1 == chars[0]) {
                for (int i = 1; i < chars.length; ++i) {
                    c1 = reader.read();
                    if (c1 != chars[i]) {
                        continue top;
                    }
                }
                return true;
            }
            c1 = reader.read();
        }
        return false;
    }

    public static boolean startsWith(BufferedInputStream bufferedInputStream, byte[] bytes) throws IOException {
        final byte[] data = new byte[bytes.length];
        bufferedInputStream.mark(bytes.length);
        try {
            readFully(bufferedInputStream, data);
            if (java.util.Arrays.equals(data, bytes)) {
                return true;
            }
        } catch (IOException ioException) {
            // This is OK
        }
        bufferedInputStream.reset();
        return false;
    }

    public static boolean startsWith(BufferedReader bufferedReader, char[] chars) throws IOException {
        final char[] data = new char[chars.length];
        bufferedReader.mark(chars.length);
        try {
            readFully(bufferedReader, data);
            if (java.util.Arrays.equals(data, chars)) {
                return true;
            }
        } catch (IOException ioException) {
            // This is OK
        }
        bufferedReader.reset();
        return false;
    }

    /**
     * @see DataInput#readFully(byte[])
     */
    public static byte[] readFully(InputStream stream, byte[] buffer) throws IOException {
        return readFully(stream, buffer, 0, buffer.length);
    }

    /**
     * @see DataInput#readFully(byte[], int, int)
     */
    public static byte[] readFully(InputStream stream, byte[] buffer, int offset, int length) throws IOException {
        if (length < 0) {
            throw new IndexOutOfBoundsException();
        }
        int n = 0;
        while (n < length) {
            final int count = stream.read(buffer, offset + n, length - n);
            if (count < 0) {
                throw new EOFException((length - n) + " of " + length + " bytes unread");
            }
            n += count;
        }
        return buffer;
    }

    /**
     * The analogous operation as {@link DataInput#readFully(byte[])} for {@link Reader}s.
     */
    public static char[] readFully(Reader reader, char[] buffer) throws IOException {
        return readFully(reader, buffer, 0, buffer.length);
    }

    /**
     * The analogous operation as {@link DataInput#readFully(byte[], int, int)} for {@link Reader}s.
     */
    public static char[] readFully(Reader reader, char[] buffer, int offset, int length) throws IOException {
        if (length < 0) {
            throw new IndexOutOfBoundsException();
        }
        int n = 0;
        while (n < length) {
            final int count = reader.read(buffer, offset + n, length - n);
            if (count < 0) {
                throw new TruncatedInputException((length - n) + " of " + length + " characters unread", n);
            }
            n += count;
        }
        return buffer;
    }
}
