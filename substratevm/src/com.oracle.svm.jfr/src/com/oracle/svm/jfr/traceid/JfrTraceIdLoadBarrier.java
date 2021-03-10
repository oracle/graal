/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.jfr.traceid;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.heap.Heap;

import java.util.List;
import java.util.function.Consumer;

public class JfrTraceIdLoadBarrier {
    private static Class<?>[] allClasses;
    private static int classCount0;
    private static int classCount1;

    private static boolean isNotTagged(long value) {
        long thisEpochBit = JfrTraceIdEpoch.thisEpochBit();
        return ((value & ((thisEpochBit << JfrTraceId.META_SHIFT) | thisEpochBit)) != thisEpochBit);
    }

    private static boolean shouldTag(Object obj) {
        assert obj != null;
        return isNotTagged(JfrTraceId.getTraceIdRaw(obj));
    }

    private static long setUsedAndGet(Object obj) {
        assert obj != null;
        if (shouldTag(obj)) {
            JfrTraceId.setUsedThisEpoch(obj);
            JfrTraceIdEpoch.setChangedTag();
        }
        assert JfrTraceId.isUsedThisEpoch(obj);
        return JfrTraceId.getTraceId(obj);
    }

    public static void clear() {
        clearClassCount(JfrTraceIdEpoch.previousEpoch());
    }

    private static void clearClassCount(boolean epoch) {
        if (epoch) {
            classCount1 = 0;
        } else {
            classCount0 = 0;
        }
    }

    private static void increaseClassCount(boolean epoch) {
        if (epoch) {
            classCount1++;
        } else {
            classCount0++;
        }
    }

    @Uninterruptible(reason = "Called by uninterruptible code")
    public static long classCount(boolean epoch) {
        return epoch ? classCount1 : classCount0;
    }

    @Uninterruptible(reason = "Called by uninterruptible code")
    public static long load(Class<?> clazz) {
        assert clazz != null;
        if (shouldTag(clazz)) {
            JfrTraceId.setUsedThisEpoch(clazz);
            increaseClassCount(JfrTraceIdEpoch.currentEpoch());
            JfrTraceIdEpoch.setChangedTag();
        }
        assert JfrTraceId.isUsedThisEpoch(clazz);
        return JfrTraceId.getTraceId(clazz);
    }

    @Uninterruptible(reason = "Called by uninterruptible code")
    public static long load(ClassLoader classLoader) {
        assert classLoader != null;
        return setUsedAndGet(classLoader);
    }

    @Uninterruptible(reason = "Called by uninterruptible code")
    public static long load(Package pkg) {
        assert pkg != null;
        return setUsedAndGet(pkg);
    }

    @Uninterruptible(reason = "Called by uninterruptible code")
    public static long load(Module module) {
        assert module != null;
        return setUsedAndGet(module);
    }

    public static boolean initialize() {
        classCount0 = 0;
        classCount1 = 0;
        allClasses = new Class<?>[Heap.getHeap().getClassCount()];
        List<Class<?>> classes = Heap.getHeap().getClassList();
        int idx = 0;
        for (Class<?> clazz : classes) {
            allClasses[idx++] = clazz;
        }
        return true;
    }

    // Note: Using Consumer<Class<?>> directly drags in other implementations which are not uninterruptible.
    public interface ClassConsumer extends Consumer<Class<?>> {}

    @Uninterruptible(reason = "Called by uninterruptible code")
    public static void doClasses(ClassConsumer kc, boolean epoch) {
        long predicate = JfrTraceId.TRANSIENT_BIT;
        predicate |= epoch ? JfrTraceIdEpoch.EPOCH_1_BIT : JfrTraceIdEpoch.EPOCH_0_BIT;
        int usedClassCount = 0;
        for (Class<?> clazz : allClasses) {
            if (JfrTraceId.predicate(clazz, predicate)) {
                kc.accept(clazz);
                usedClassCount++;
            }
        }
        assert usedClassCount == classCount(epoch);
    }
}
