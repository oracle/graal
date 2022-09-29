/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmstat;

import static com.oracle.svm.core.jvmstat.PerfManager.Options.PerfDataMemoryMappedFile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.jdk.Target_java_nio_Buffer;
import com.oracle.svm.core.jdk.Target_java_nio_DirectByteBuffer;

/**
 * Provides access to the underlying OS-specific memory that stores the performance data.
 *
 * For security reasons, we must not read any data from the performance data file (it is usually
 * shared memory). There is only one exception to that rule and that is when we increment a
 * performance data counter (i.e., we read the counter value, increment it, and write it back to the
 * same memory location).
 */
public class PerfMemory {
    private static final CGlobalData<Pointer> PERF_DATA_ISOLATE = CGlobalDataFactory.createWord();

    private PerfMemoryProvider memoryProvider;
    private ByteBuffer buffer;
    private int capacity;
    private Word rawMemory;
    private int used;
    private long initialTime;
    private Word[] overflowMemory;
    private int overflowMemoryPos;

    @Platforms(Platform.HOSTED_ONLY.class)
    public PerfMemory() {
    }

    public boolean initialize() {
        if (!createBuffer()) {
            return false;
        }

        PerfMemoryPrologue.initialize(rawMemory, ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN));

        used = PerfMemoryPrologue.getPrologueSize();
        initialTime = System.nanoTime();
        return true;
    }

    private boolean createBuffer() {
        PerfMemoryProvider m = null;
        ByteBuffer b = null;
        if (PerfDataMemoryMappedFile.getValue() && tryAcquirePerfDataFile()) {
            m = ImageSingletons.lookup(PerfMemoryProvider.class);
            b = m.create();
            if (b == null) {
                releasePerfDataFile();
            }
        }

        if (b == null) {
            /*
             * Memory mapped file support is disabled, another isolate already owns the perf data
             * file, or the file or buffer could not be created. Either way, this isolate needs to
             * use C heap memory instead.
             */
            m = new CHeapPerfMemoryProvider();
            b = m.create();
        }

        if (b == null || b.capacity() < PerfMemoryPrologue.getPrologueSize()) {
            return false;
        }

        memoryProvider = m;
        buffer = b;
        capacity = b.capacity();
        rawMemory = WordFactory.pointer(SubstrateUtil.cast(b, Target_java_nio_Buffer.class).address);
        assert verifyRawMemoryAccess();

        return true;
    }

    private boolean verifyRawMemoryAccess() {
        byte originalValue = buffer.get(0);
        byte writtenValue = (byte) 0xC9;
        assert originalValue != writtenValue;

        buffer.put(0, writtenValue);
        assert rawMemory.readByte(0) == writtenValue;

        rawMemory.writeByte(0, originalValue);
        assert buffer.get(0) == originalValue;
        return true;
    }

    /**
     * Creates a {@link ByteBuffer} that allows direct access to the underlying memory. This method
     * may only be used for JDK code that needs direct memory access.
     */
    public ByteBuffer createByteBuffer() {
        return SubstrateUtil.cast(new Target_java_nio_DirectByteBuffer(rawMemory.rawValue(), capacity), ByteBuffer.class);
    }

    /**
     * Allocate an aligned block of memory from the PerfData memory region. No matter what happens,
     * this method always either returns a usable pointer or throws an OutOfMemoryError.
     */
    @NeverInline("Workaround for GR-26795.")
    public synchronized Word allocate(int size) {
        // Check that there is enough memory for this request.
        if (used + size >= capacity) {
            PerfMemoryPrologue.addOverflow(rawMemory, size);
            // Always return a valid pointer (external tools won't see this memory though).
            Word result = UnmanagedMemory.calloc(size);
            addOverflowMemory(result);
            return result;
        }

        Word result = rawMemory.add(used);
        used += size;
        PerfMemoryPrologue.setUsed(rawMemory, used);
        PerfMemoryPrologue.incrementNumEntries(rawMemory);
        return result;
    }

    private void addOverflowMemory(Word result) {
        if (overflowMemory == null) {
            overflowMemory = new Word[8];
        } else if (overflowMemory.length == overflowMemoryPos) {
            overflowMemory = new Word[overflowMemory.length * 2];
        }
        overflowMemory[overflowMemoryPos] = result;
        overflowMemoryPos++;
    }

    /**
     * Marks the performance data memory as updated. This is for example necessary after adding
     * further performance data entries.
     */
    public void markUpdated() {
        PerfMemoryPrologue.markUpdated(rawMemory, initialTime);
    }

    /**
     * Marks the performance data memory as accessible and allows external tools such as VisualVM to
     * query the performance data memory.
     */
    public void setAccessible() {
        PerfMemoryPrologue.setAccessible(rawMemory, true);
    }

    public void teardown() {
        if (buffer != null) {
            buffer = null;
            rawMemory = WordFactory.zero();
            capacity = 0;
            used = 0;
        }

        if (overflowMemory != null) {
            for (int i = 0; i < overflowMemory.length; i++) {
                UnmanagedMemory.free(overflowMemory[i]);
            }
            overflowMemory = null;
        }

        memoryProvider.teardown();
        releasePerfDataFile();
    }

    private static boolean tryAcquirePerfDataFile() {
        Pointer perfDataIsolatePtr = PERF_DATA_ISOLATE.get();
        return perfDataIsolatePtr.logicCompareAndSwapWord(0, WordFactory.nullPointer(), CurrentIsolate.getIsolate(), LocationIdentity.ANY_LOCATION);
    }

    private static void releasePerfDataFile() {
        Pointer perfDataIsolatePtr = PERF_DATA_ISOLATE.get();
        if (perfDataIsolatePtr.readWord(0) == CurrentIsolate.getIsolate()) {
            perfDataIsolatePtr.writeWord(0, WordFactory.nullPointer());
        }
    }
}
