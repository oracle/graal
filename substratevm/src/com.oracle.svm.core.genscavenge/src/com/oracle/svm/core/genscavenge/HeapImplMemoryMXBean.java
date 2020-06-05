/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

//Checkstyle: stop
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

import javax.management.MBeanNotificationInfo;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.option.RuntimeOptionValues;

import sun.management.Util;
//Checkstyle: resume

/**
 * A MemoryMXBean for this heap.
 *
 * Note: This implementation is somewhat inefficient, in that each time it is asked for the
 * <em>current</em> heap memory usage or non-heap memory usage, it uses the MemoryWalker.Visitor to
 * walk all of memory. If someone asks for only the heap memory usage <em>or</em> the non-heap
 * memory usage, the other kind of memory will still be walked. If someone asks for both the heap
 * memory usage <em>and</em> the non-heap memory usage, all the memory will be walked twice.
 */
public final class HeapImplMemoryMXBean implements MemoryMXBean, NotificationEmitter {
    static final long UNDEFINED_MEMORY_USAGE = -1L;

    private final MemoryMXBeanMemoryVisitor visitor;

    @Platforms(Platform.HOSTED_ONLY.class)
    public HeapImplMemoryMXBean() {
        this.visitor = new MemoryMXBeanMemoryVisitor();
    }

    @Override
    public ObjectName getObjectName() {
        return Util.newObjectName(ManagementFactory.MEMORY_MXBEAN_NAME);
    }

    @Override
    public int getObjectPendingFinalizationCount() {
        return 0;
    }

    @Override
    public MemoryUsage getHeapMemoryUsage() {
        visitor.reset();
        MemoryWalker.getMemoryWalker().visitMemory(visitor);
        long used = visitor.getHeapUsed().rawValue();
        long committed = visitor.getHeapCommitted().rawValue();
        return new MemoryUsage(UNDEFINED_MEMORY_USAGE, used, committed, UNDEFINED_MEMORY_USAGE);
    }

    @Override
    public MemoryUsage getNonHeapMemoryUsage() {
        visitor.reset();
        MemoryWalker.getMemoryWalker().visitMemory(visitor);
        long used = visitor.getNonHeapUsed().rawValue();
        long committed = visitor.getNonHeapCommitted().rawValue();
        return new MemoryUsage(UNDEFINED_MEMORY_USAGE, used, committed, UNDEFINED_MEMORY_USAGE);
    }

    @Override
    public boolean isVerbose() {
        return SubstrateOptions.PrintGC.getValue();
    }

    @Override
    public void setVerbose(boolean value) {
        RuntimeOptionValues.singleton().update(SubstrateOptions.PrintGC, value);
    }

    @Override
    public void gc() {
        System.gc();
    }

    @Override
    public void removeNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
    }

    @Override
    public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
    }

    @Override
    public void removeNotificationListener(NotificationListener listener) {
    }

    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        return new MBeanNotificationInfo[0];
    }
}

/** A MemoryWalker.Visitor that records used and committed memory sizes. */
final class MemoryMXBeanMemoryVisitor implements MemoryWalker.Visitor {
    private UnsignedWord heapUsed;
    private UnsignedWord heapCommitted;
    private UnsignedWord nonHeapUsed;
    private UnsignedWord nonHeapCommitted;

    MemoryMXBeanMemoryVisitor() {
        reset();
    }

    public UnsignedWord getHeapUsed() {
        return heapUsed;
    }

    public UnsignedWord getHeapCommitted() {
        return heapCommitted;
    }

    public UnsignedWord getNonHeapUsed() {
        return nonHeapUsed;
    }

    public UnsignedWord getNonHeapCommitted() {
        return nonHeapCommitted;
    }

    public void reset() {
        heapUsed = WordFactory.zero();
        heapCommitted = WordFactory.zero();
        nonHeapUsed = WordFactory.zero();
        nonHeapCommitted = WordFactory.zero();
    }

    @Override
    public <T> boolean visitNativeImageHeapRegion(T region, MemoryWalker.NativeImageHeapRegionAccess<T> access) {
        UnsignedWord size = access.getSize(region);
        heapUsed = heapUsed.add(size);
        heapCommitted = heapCommitted.add(size);
        return true;
    }

    @Override
    public <T extends PointerBase> boolean visitHeapChunk(T heapChunk, MemoryWalker.HeapChunkAccess<T> access) {
        UnsignedWord used = access.getAllocationEnd(heapChunk).subtract(access.getAllocationStart(heapChunk));
        UnsignedWord committed = access.getSize(heapChunk);
        heapUsed = heapUsed.add(used);
        heapCommitted = heapCommitted.add(committed);
        return true;
    }

    @Override
    public <T extends CodeInfo> boolean visitCode(T codeInfo, MemoryWalker.CodeAccess<T> access) {
        UnsignedWord size = access.getSize(codeInfo).add(access.getMetadataSize(codeInfo));
        nonHeapUsed = nonHeapUsed.add(size);
        nonHeapCommitted = nonHeapCommitted.add(size);
        return true;
    }
}
