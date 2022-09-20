/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.wasm.memory;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class NativeDataInstanceStore {
    private static final Unsafe unsafe = initUnsafe();
    private long[] addresses;
    private int[] sizes;

    private static Unsafe initUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException se) {
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(Unsafe.class);
            } catch (Exception e) {
                throw new RuntimeException("exception while trying to get Unsafe", e);
            }
        }
    }

    public NativeDataInstanceStore() {
        this.addresses = null;
        this.sizes = null;
    }

    private void ensureCapacity(int index) {
        if (addresses == null) {
            final int length = Math.max(Integer.highestOneBit(index) << 1, 2);
            addresses = new long[length];
            sizes = new int[length];
        } else if (index >= addresses.length) {
            final int length = Math.max(Integer.highestOneBit(index) << 1, 2 * addresses.length);
            final long[] nAddresses = new long[length];
            final int[] nSizes = new int[length];
            System.arraycopy(addresses, 0, nAddresses, 0, addresses.length);
            System.arraycopy(sizes, 0, nSizes, 0, sizes.length);
            addresses = nAddresses;
            sizes = nSizes;
        }
    }

    public void setInstance(int index, byte[] dataInstance) {
        assert dataInstance != null;
        ensureCapacity(index);
        final long address = unsafe.allocateMemory(dataInstance.length);
        addresses[index] = address;
        sizes[index] = dataInstance.length;
        for (int i = 0; i < dataInstance.length; i++) {
            unsafe.putByte(address + i, dataInstance[i]);
        }
    }

    public void dropInstance(int index) {
        if (addresses == null) {
            return;
        }
        assert index < addresses.length;
        final long address = addresses[index];
        unsafe.freeMemory(address);
        addresses[index] = 0L;
        sizes[index] = 0;
    }

    public long instanceAddress(int index) {
        if (addresses == null) {
            return 0L;
        }
        assert index < addresses.length;
        return addresses[index];
    }

    public int instanceSize(int index) {
        if (sizes == null) {
            return 0;
        }
        assert index < sizes.length;
        return sizes[index];
    }
}
