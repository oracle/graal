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

package org.graalvm.visualizer.data.serialization;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import junit.framework.TestCase;

/**
 *
 */
public class BinarySourceTest extends TestCase {
    public BinarySourceTest(String name) {
        super(name);
    }
    
    static class Channel implements ReadableByteChannel {
        boolean open;
        List<byte[]>    content = new ArrayList<>();
        int pos;
        boolean eof;
        ByteBuffer      current = ByteBuffer.allocate(1000);
        
        Channel appendContent(byte[] arr) {
            content.add(arr);
            return this;
        }
        
        Channel newChunk() {
            byte[] chunk = new byte[current.position()];
            System.arraycopy(current.array(), 0, chunk, 0, current.position());
            content.add(chunk);
            current.clear();
            return this;
        }
        
        @Override
        public int read(ByteBuffer dst) throws IOException {
            if (content.isEmpty()) {
                if (eof) {
                    throw new EOFException();
                } else {
                    eof = true;
                    return -1;
                }
            }
            byte[] arr = content.get(0);
            if (pos < arr.length) {
                int l = Math.min(dst.remaining(), arr.length - pos);
                dst.put(arr, pos, l);
                pos += l;
                if (pos >= arr.length) {
                    content.remove(0);
                    pos = 0;
                }
                return l;
            } else {
                content.remove(0);
                pos = 0;
                return 0;
            }
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() throws IOException {
            open = false;
        }
        
        void updateDigest(MessageDigest digest) {
            for (byte[] chunk : content) {
                digest.update(chunk);
            }
        }
        
    }
    
    byte[] slice(byte[] arr, int off, int l) {
        byte []r = new byte[l];
        System.arraycopy(arr, off, r, 0, l);
        return r;
    }
    
    public void testDigestAcrossFill() throws Exception {
        Channel ch = new Channel();
        String s = "Whatever";
        byte[] bytes = s.getBytes("UTF-8");
        ch.current.putInt(bytes.length).put(slice(bytes, 0, 5));
        ch.newChunk();
        ch.current.put(slice(bytes, 5, bytes.length - 5));
        ch.newChunk();
        
        MessageDigest checkDigest = MessageDigest.getInstance("SHA-1"); // NOI18N
        ch.updateDigest(checkDigest);

        BinarySource src = new BinarySource(null, ch);
        src.startDigest();
        String read = src.readString();
        assertEquals(s, read);
        byte[] digest = src.finishDigest();
        
        byte[] toCheck = checkDigest.digest();
        assertTrue(Arrays.equals(toCheck, digest));
    }
}
