/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jfr;

import java.io.IOException;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.thread.VMOperation;

public class JfrTypeRepository implements JfrRepository {
    private final VMMutex mutex;
    private final JfrSymbolRepository symbolRepo;
    @UnknownObjectField(types = boolean[].class) private boolean[] usedTypes;

    private int count;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrTypeRepository(JfrSymbolRepository symbolRepo) {
        this.mutex = new VMMutex();
        this.symbolRepo = symbolRepo;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void initialize(int typeCount) {
        usedTypes = new boolean[typeCount];
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    public long getClassId(Class<?> clazz) {
        int typeId = DynamicHub.fromClass(clazz).getTypeID();

        if (!usedTypes[typeId]) {
            mutex.lock();
            try {
                if (!usedTypes[typeId]) {
                    usedTypes[typeId] = true;
                    count++;
                }
            } finally {
                mutex.unlock();
            }
        }

        return typeId;
    }

    @Override
    public void write(JfrChunkWriter writer) throws IOException {
        assert VMOperation.isInProgressAtSafepoint();
        writer.writeCompressedLong(JfrTypes.Class.getId());
        writer.writeCompressedLong(count);

        // TODO: this won't work as the id doesn't match the order/index of the written data.
        // JfrMethodRepository has a similar issue. Possible solutions are:
        // - just write all klasses
        // - use a long[] instead of the boolean[]
        // - use an UninterruptibleHashmap
        for (Class<?> clazz : Heap.getHeap().getClassList()) {
            int id = DynamicHub.fromClass(clazz).getTypeID();
            if (usedTypes[id]) {
                usedTypes[id] = false;
                writer.writeCompressedLong(0L); // classloader
                writer.writeCompressedLong(symbolRepo.getSymbolId(clazz));
                writer.writeCompressedLong(0); // package id
                writer.writeCompressedLong(clazz.getModifiers());
            }
        }
    }
}
