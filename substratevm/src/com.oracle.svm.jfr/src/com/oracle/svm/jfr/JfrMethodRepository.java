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
import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.thread.VMOperation;

public class JfrMethodRepository implements JfrRepository {
    private final VMMutex mutex;
    private final JfrTypeRepository classRepo;
    private final JfrSymbolRepository symbolRepo;
    @UnknownObjectField(types = boolean[].class) private boolean[] usedMethods;

    private int count;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrMethodRepository(JfrTypeRepository classRepo, JfrSymbolRepository symbolRepo) {
        this.mutex = new VMMutex();
        this.classRepo = classRepo;
        this.symbolRepo = symbolRepo;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void initialize(int methodCount) {
        usedMethods = new boolean[methodCount];
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    public long getMethodId(MethodInfo method) {
        int id = method.getId();

        if (!usedMethods[id]) {
            mutex.lock();
            try {
                if (!usedMethods[id]) {
                    usedMethods[id] = true;
                    count++;
                }
            } finally {
                mutex.unlock();
            }
        }

        return id;
    }

    @Override
    public void write(JfrChunkWriter writer) throws IOException {
        assert VMOperation.isInProgressAtSafepoint();
        if (count == 0) {
            return;
        }
        writer.writeCompressedLong(JfrTypes.Method.getId());
        writer.writeCompressedLong(count);

        for (MethodInfo method : getMethodList()) {
            int id = method.getId();
            if (usedMethods[id]) {
                usedMethods[id] = false;
                writer.writeCompressedLong(classRepo.getClassId(method.getParentClass()));
                writer.writeCompressedLong(symbolRepo.getSymbolId(method.getName()));
                writer.writeCompressedLong(symbolRepo.getSymbolId(method.getSignature()));
                writer.writeCompressedInt(0); // package id
                writer.writeBoolean(false); // hidden
            }
        }
    }

    @Override
    public boolean hasItems() {
        return count > 0;
    }

    // TODO: just a dummy implementation
    private static List<MethodInfo> getMethodList() {
        return Collections.emptyList();
    }

    // TODO: just a dummy implementation - for each method, one info object must live in the image
    // heap.
    public static class MethodInfo {
        public int getId() {
            return 0;
        }

        public Class<?> getParentClass() {
            return null;
        }

        public String getName() {
            return null;
        }

        public String getSignature() {
            return null;
        }
    }
}
