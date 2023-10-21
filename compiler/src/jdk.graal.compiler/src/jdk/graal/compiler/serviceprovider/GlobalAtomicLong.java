/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.serviceprovider;

import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicLong;

import sun.misc.Unsafe;

/**
 * A shareable long value in the JVM process that is updated atomically. The long value is stored in
 * native memory so that it is accessible across multiple libgraal isolates.
 * <p>
 * Objects of this type cannot be created during native image runtime.
 *
 * @see AtomicLong
 */
public class GlobalAtomicLong {

    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

    /**
     * Cleaner for freeing {@link #address}.
     */
    private static Cleaner cleaner;

    /**
     * Address of native memory storing the long value.
     */
    private volatile long address;

    /**
     * Value to which the value will be initialized.
     */
    private final long initialValue;

    /**
     * Creates a global long value that is atomically updated.
     *
     * @param initialValue initial value to which the long is set when its memory is allocated
     */
    public GlobalAtomicLong(long initialValue) {
        this.initialValue = initialValue;
    }

    public long getInitialValue() {
        return initialValue;
    }

    @Override
    public String toString() {
        long addr = getAddress();
        if (addr == 0L) {
            return String.valueOf(initialValue);
        } else {
            return String.format("%d (@0x%x)", get(), addr);
        }
    }

    // Substituted by Target_jdk_graal_compiler_serviceprovider_GlobalAtomicLong
    private long getAddress() {
        if (address == 0L) {
            synchronized (this) {
                if (address == 0L) {
                    long addr = UNSAFE.allocateMemory(Long.BYTES);
                    synchronized (GlobalAtomicLong.class) {
                        if (cleaner == null) {
                            cleaner = Cleaner.create();
                        }
                        cleaner.register(this, () -> UNSAFE.freeMemory(addr));
                    }
                    UNSAFE.putLongVolatile(null, addr, initialValue);
                    address = addr;
                }
            }
        }
        return address;
    }

    /**
     * @see AtomicLong#get()
     */
    public long get() {
        return UNSAFE.getLongVolatile(null, getAddress());
    }

    /**
     * @see AtomicLong#set(long)
     */
    public void set(long newValue) {
        UNSAFE.putLongVolatile(null, getAddress(), newValue);
    }

    /**
     * @see AtomicLong#getAndSet(long)
     */
    public long getAndSet(long newValue) {
        return UNSAFE.getAndSetLong(null, getAddress(), newValue);
    }

    /**
     * @see AtomicLong#getAndAdd(long)
     */
    public long getAndAdd(long delta) {
        return UNSAFE.getAndAddLong(null, getAddress(), delta);
    }

    /**
     * @see AtomicLong#addAndGet(long)
     */
    public long addAndGet(long delta) {
        return getAndAdd(delta) + delta;
    }

    /**
     * @see AtomicLong#incrementAndGet()
     */
    public long incrementAndGet() {
        return addAndGet(1);
    }

    /**
     * @see AtomicLong#getAndIncrement()
     */
    public long getAndIncrement() {
        return getAndAdd(1);
    }

    /**
     * @see AtomicLong#decrementAndGet()
     */
    public long decrementAndGet() {
        return addAndGet(-1);
    }

    /**
     * @see AtomicLong#getAndDecrement()
     */
    public long getAndDecrement() {
        return getAndAdd(-1);
    }

    /**
     * @see AtomicLong#compareAndSet(long, long)
     */
    public boolean compareAndSet(long expectedValue, long newValue) {
        return UNSAFE.compareAndSwapLong(null, getAddress(), expectedValue, newValue);
    }
}
