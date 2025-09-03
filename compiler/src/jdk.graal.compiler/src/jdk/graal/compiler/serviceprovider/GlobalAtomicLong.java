/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.core.common.NativeImageSupport.inRuntimeCode;

import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.debug.GraalError;
import jdk.internal.misc.Unsafe;

/**
 * A shareable long value in the JVM process that is updated atomically. The long value is stored in
 * native memory so that it is accessible across multiple libgraal isolates.
 * <p>
 * Objects of this type cannot be created during native image runtime.
 *
 * @see AtomicLong
 */
public class GlobalAtomicLong {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    /**
     * Cleaner for freeing {@link #address} when executing in jargraal.
     */
    @LibGraalSupport.HostedOnly //
    private static Cleaner cleaner;

    /**
     * Name of the global. This is only used for {@link #toString()} and is not guaranteed to be
     * unique.
     */
    private final String name;

    /**
     * Address of native memory storing the long value.
     */
    private volatile long address;

    /**
     * Supplies the address of the global memory storing the global value.
     */
    private final Supplier<Long> addressSupplier;

    /**
     * Value to which the value will be initialized.
     */
    private final long initialValue;

    /**
     * Creates a global long value that is atomically updated.
     *
     * @param initialValue initial value to which the long is set when its memory is allocated
     */
    public GlobalAtomicLong(String name, long initialValue) {
        this.name = name;
        this.initialValue = initialValue;
        if (inRuntimeCode()) {
            throw GraalError.shouldNotReachHere("Cannot create " + getClass().getName() + " objects in native image runtime");
        } else {
            LibGraalSupport libgraal = LibGraalSupport.INSTANCE;
            if (libgraal != null) {
                addressSupplier = libgraal.createGlobal(initialValue);
            } else {
                // Executing in jargraal
                addressSupplier = () -> {
                    long addr = UNSAFE.allocateMemory(Long.BYTES);
                    synchronized (GlobalAtomicLong.class) {
                        if (cleaner == null) {
                            cleaner = Cleaner.create();
                        }
                        cleaner.register(GlobalAtomicLong.this, () -> UNSAFE.freeMemory(addr));
                    }
                    UNSAFE.putLongVolatile(null, addr, initialValue);
                    return addr;
                };
            }
        }
    }

    public long getInitialValue() {
        return initialValue;
    }

    @Override
    public String toString() {
        if (address == 0L) {
            return name + ":" + initialValue;
        } else {
            return String.format("%s:%d(@0x%x)", name, get(), address);
        }
    }

    private long getAddress() {
        if (address == 0L) {
            synchronized (this) {
                if (address == 0L) {
                    address = addressSupplier.get();
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
        return UNSAFE.compareAndSetLong(null, getAddress(), expectedValue, newValue);
    }
}
