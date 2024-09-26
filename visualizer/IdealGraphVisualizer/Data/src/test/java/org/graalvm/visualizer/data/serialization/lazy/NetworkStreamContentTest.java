/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.data.serialization.lazy;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.Semaphore;

import org.netbeans.junit.NbTestCase;
import org.openide.util.RequestProcessor;
import org.openide.util.RequestProcessor.Task;

/**
 * @author sdedic
 */
public class NetworkStreamContentTest extends NbTestCase {
    public NetworkStreamContentTest(String name) {
        super(name);
    }

    private byte[] data;
    private ReadableByteChannel dataChannel;
    NetworkStreamContent content;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        NetworkStreamContent.DEFAULT_RECEIVE_BUFFER_SIZE = 1024;
        data = new byte[NetworkStreamContent.DEFAULT_RECEIVE_BUFFER_SIZE * COUNT];
        int what = 0;
        for (int i = 0; i < data.length; i++, what++) {
            data[i] = (byte) (what % 251);
        }
        dataChannel = Channels.newChannel(new ByteArrayInputStream(data));
    }

    private static final int COUNT = 10;

    protected void tearDown() throws Exception {
        if (content != null) {
            content.dumpFile.delete();
        }
    }

    private long end() {
        return data.length;
    }

    private long endBlock(int i) {
        return NetworkStreamContent.DEFAULT_RECEIVE_BUFFER_SIZE * (COUNT - 1 - i);
    }

    private long endBlock() {
        return NetworkStreamContent.DEFAULT_RECEIVE_BUFFER_SIZE * (COUNT - 1);
    }

    private long block(int index) {
        return index * NetworkStreamContent.DEFAULT_RECEIVE_BUFFER_SIZE;
    }

    private void testFullReadFromTo(long start, long to) throws IOException {
        testFullReadFromTo(start, to, data.length);
    }

    private void initContent() throws IOException {
        File name = File.createTempFile("igvtest_", "");
        name.delete();
        name.mkdirs();
        content = new NetworkStreamContent(dataChannel, name);
    }

    private void testFullReadFromTo(long start, long to, long dataLen) throws IOException {
        initContent();
        ByteBuffer dst = ByteBuffer.allocate((int) dataLen);
        content.read(dst);
        int len = (int) (to - start);

        ReadableByteChannel subchannel = content.subChannel(start, to);
        ReadableByteChannel subchannel2 = content.subChannel(start, to);
        ByteBuffer check = ByteBuffer.allocate(len);
        ByteBuffer check2 = ByteBuffer.allocate(len);
        // receive to smaller buffers, force underflow within the content
        ByteBuffer recv = ByteBuffer.allocate(NetworkStreamContent.DEFAULT_RECEIVE_BUFFER_SIZE / 3 * 2);
        int read;
        do {
            read = subchannel.read(check);
        } while (read >= 0);
        do {
            read = subchannel2.read(recv);
            recv.flip();
            check2.put(recv);
            recv.clear();
        } while (read >= 0);

        assertEquals(len, check.position());
        assertContent(check, start, to);

        assertEquals(len, check2.position());
        assertContent(check2, start, to);
    }

    private void assertContent(ByteBuffer check, long start, long to) {
        byte[] checkArray = check.array();
        long j = start;
        int len = (int) (to - start);
        for (int i = 0; i < len; i++, j++) {
            assertEquals("Position " + i + "differs", data[(int) j], checkArray[i]);
        }
    }

    public void testOnlyPartsStartEnd() throws Exception {
        testFullReadFromTo(100, endBlock() + 100);
    }

    public void testOnlyParts() throws Exception {
        testFullReadFromTo(block(1) + 100, endBlock(1) + 100);
    }

    public void testPartialStartBoundaryEnd() throws Exception {
        testFullReadFromTo(block(1) + 100, endBlock(1));
    }

    public void testBoundaryStartPartialEnd() throws Exception {
        testFullReadFromTo(block(1), endBlock(1) + 100);
    }

    public void testBothBoundaries() throws Exception {
        testFullReadFromTo(block(1), endBlock(1));
    }

    public void testBeginningToMiddle() throws Exception {
        testFullReadFromTo(0, block(2) + 100);
    }

    public void testBeginningToEnd() throws Exception {
        testFullReadFromTo(0, end());
    }

    public void testOneFullBlock() throws Exception {
        testFullReadFromTo(100, block(1) + 100);
    }

    public void testMoreFullBlocks() throws Exception {
        testFullReadFromTo(100, block(3) + 100);
    }

    public void testPartToBoundaryWithReceive() throws Exception {
        testFullReadFromTo(100, block(3), end() - 10);
    }

    public void testPartUpToExactReceive() throws Exception {
        testFullReadFromTo(100, endBlock(), end() - 10);
    }

    public void testPartUpToInsideReceive() throws Exception {
        testFullReadFromTo(100, endBlock() + 100, end() - 10);
    }

    public void testFromReceivePartial() throws Exception {
        testFullReadFromTo(endBlock(), end() - 30, end() - 10);
    }

    final RequestProcessor RP = new RequestProcessor(NetworkStreamContentTest.class.getName(), 10);

    class Consumer implements Runnable {
        Semaphore block = new Semaphore(0);
        Semaphore signal = new Semaphore(0);
        volatile long drain = -1;
        ByteBuffer buf = ByteBuffer.allocate(1111);
        volatile boolean eof;
        Throwable failure;
        ReadableByteChannel channel = content;
        ByteBuffer dest;

        @Override
        public void run() {
            try {
                dest = ByteBuffer.allocate((int) drain);
                while (drain == -1 || drain > 0) {
                    int read = channel.read(buf);
                    if (read == -1) {
                        eof = true;
                        return;
                    }
                    if (drain >= 0) {
                        drain -= read;
                    }
                    buf.flip();
                    dest.put(buf);
                    buf.clear();
                }
                // attempt to read past the end:
                int read = channel.read(buf);
                if (read == -1) {
                    eof = true;
                }
                signal.release();
                block.acquire();
            } catch (Exception ex) {
                failure = ex;
            } finally {
                signal.release();
            }
        }

    }

    public void testPartialToEOFClosed() throws Exception {
        initContent();

        // drain, up to some non-full receive buffer
        // now read "till the end":
        ByteBuffer dst = ByteBuffer.allocate((int) (endBlock(1) - 100));
        content.read(dst);

        Consumer c = new Consumer();
        long l = endBlock() + 100;
        c.drain = l;
        c.channel = content.subChannel(0, -1);

        Task t = RP.post(c);
        Thread.sleep(300);
        assertFalse(c.eof);

        // read some more data
        dst.clear();
        dst.limit((int) l - dst.capacity());
        content.read(dst);
        content.close();

        c.signal.acquire();
        c.block.release();

        assertTrue(c.eof);
        assertContent((ByteBuffer) c.dest.flip(), 0, endBlock() + 100);
        assertNull(c.failure);
        t.waitFinished(2000);
    }
}
