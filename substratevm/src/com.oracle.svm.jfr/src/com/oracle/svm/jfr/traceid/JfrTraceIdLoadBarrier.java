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

import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Consumer;

public class JfrTraceIdLoadBarrier {
    private static Queue<Class<?>> klassQueueOne = null;
    private static Queue<Class<?>> klassQueueTwo = null;

    private static boolean isNotTagged(long value) {
        long thisEpochBit = JfrTraceIdEpoch.thisEpochBit();
        return ((value & ((thisEpochBit << JfrTraceId.META_SHIFT) | thisEpochBit)) != thisEpochBit);
    }

    private static boolean shouldTag(Object obj) {
        assert obj != null;
        return isNotTagged(JfrTraceId.getTraceIdRaw(obj));
    }

    private static void enqueue(Class<?> clazz) {
        assert (JfrTraceId.isUsedThisEpoch(clazz));
        klassQueue().add(clazz);
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
        getKlassQueue(JfrTraceIdEpoch.previousEpoch()).clear();
    }

    public static long load(Class<?> clazz) {
        assert clazz != null;
        if (shouldTag(clazz)) {
            JfrTraceId.setUsedThisEpoch(clazz);
            enqueue(clazz);
            JfrTraceIdEpoch.setChangedTag();
        }
        assert JfrTraceId.isUsedThisEpoch(clazz);
        return JfrTraceId.getTraceId(clazz);
    }

    public static long load(ClassLoader classLoader) {
        assert classLoader != null;
        return setUsedAndGet(classLoader);
    }

    public static long load(Package pkg) {
        assert pkg != null;
        return setUsedAndGet(pkg);
    }

    public static long load(Module module) {
        assert module != null;
        return setUsedAndGet(module);
    }

    public static boolean initialize() {
        klassQueueOne = new LinkedList<>();
        klassQueueTwo = new LinkedList<>();
        return true;
    }

    private static Queue<Class<?>> klassQueue() {
        return klassQueue(false);
    }

    private static Queue<Class<?>> klassQueue(boolean previousEpoch) {
        if (previousEpoch) {
            return getKlassQueue(JfrTraceIdEpoch.previousEpoch());
        }
        return getKlassQueue(JfrTraceIdEpoch.currentEpoch());
    }

    private static Queue<Class<?>> getKlassQueue(boolean epoch) {
        if (epoch) {
            return klassQueueOne;
        } else {
            return klassQueueTwo;
        }
    }

    public static void doKlasses(Consumer<Class<?>> kc, boolean previousEpoch) {
        for (Class<?> c : klassQueue(previousEpoch)) {
            kc.accept(c);
        }
    }
}
