/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021, Red Hat Inc. All rights reserved.
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

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import jdk.jfr.internal.JVM;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

public class JfrTraceId {

    private static final long MaxJfrEventId = 403;

    public static final long BIT = 1;
    public static final long META_SHIFT = 8;
    public static final long TRANSIENT_META_BIT = (BIT << 3);
    public static final long TRANSIENT_BIT = (TRANSIENT_META_BIT << META_SHIFT);
    public static final long SERIALIZED_META_BIT = (BIT << 4);
    public static final long SERIALIZED_BIT = (SERIALIZED_META_BIT << META_SHIFT);

    private static final int TRACE_ID_SHIFT = 16;

    // Epoch stuff
    private static final long USED_BIT = 1;
    private static final int EPOCH_1_SHIFT = 0;
    private static final int EPOCH_2_SHIFT = 1;
    private static final long USED_EPOCH_1_BIT = USED_BIT << EPOCH_1_SHIFT;
    private static final long USED_EPOCH_2_BIT = USED_BIT << EPOCH_2_SHIFT;

    private static final long JDK_JFR_EVENT_SUBKLASS = 16;
    private static final long JDK_JFR_EVENT_KLASS = 32;
    private static final long EVENT_HOST_KLASS = 64;

    private static final AtomicLong threadCounter = new AtomicLong(1);

    @Fold
    static JfrTraceIdMap getTraceIdMap() {
        return ImageSingletons.lookup(JfrTraceIdMap.class);
    }

    public static void tag(Class<?> clazz, long bits) {
        JfrTraceIdMap map = getTraceIdMap();
        long id = map.getId(clazz);
        map.setId(clazz, (id & ~0xff) | (bits & 0xff));
    }

    public static boolean predicate(Class<?> clazz, long bits) {
        JfrTraceIdMap map = getTraceIdMap();
        long id = map.getId(clazz);
        return (id & bits) != 0;
    }

    public static void setUsedThisEpoch(Class<?> clazz) {
        tag(clazz, JfrTraceIdEpoch.getInstance().thisEpochBit());
    }

    public static boolean isUsedThisEpoch(Class<?> clazz) {
        return predicate(clazz, TRANSIENT_BIT | JfrTraceIdEpoch.getInstance().thisEpochBit());
    }

    public static long getTraceIdRaw(Class<?> clazz) {
        JfrTraceIdMap map = getTraceIdMap();
        assert map != null;
        return getTraceIdMap().getId(clazz);
    }

    public static long getTraceId(Class<?> clazz) {
        long traceid = getTraceIdRaw(clazz);
        return traceid >>> TRACE_ID_SHIFT;
    }

    public static long load(Class<?> clazz) {
        return JfrTraceIdLoadBarrier.load(clazz);
    }

    private static void tagAsJdkJfrEvent(int index) {
        JfrTraceIdMap map = getTraceIdMap();
        long id = map.getId(index);
        assert id != -1;
        map.setId(index, id | JDK_JFR_EVENT_KLASS);
    }

    private static void tagAsJdkJfrEventSub(int index) {
        JfrTraceIdMap map = getTraceIdMap();
        long id = map.getId(index);
        assert id != -1;
        map.setId(index, id | JDK_JFR_EVENT_SUBKLASS);
    }

    private static boolean isEventClass(int index) {
        JfrTraceIdMap map = getTraceIdMap();
        long id = map.getId(index);
        assert id != -1;
        return (id & (JDK_JFR_EVENT_KLASS | JDK_JFR_EVENT_SUBKLASS)) != 0;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void assign(Class<?> clazz, int index) {
        assert clazz != null;
        index = index + 1; // Off-set by one for error-catcher
        if (getTraceIdMap().getId(index) != -1) {
            return;
        }
        long typeId = JVM.getJVM().getTypeId(clazz);
        getTraceIdMap().setId(index, typeId << TRACE_ID_SHIFT);

        if ((jdk.internal.event.Event.class == clazz || jdk.jfr.Event.class == clazz) &&
                clazz.getClassLoader() == null || clazz.getClassLoader() == ClassLoader.getSystemClassLoader()) {

            tagAsJdkJfrEvent(index);
        }
        if ((jdk.internal.event.Event.class.isAssignableFrom(clazz) || jdk.jfr.Event.class.isAssignableFrom(clazz)) &&
                clazz.getClassLoader() == null || clazz.getClassLoader() == ClassLoader.getSystemClassLoader()) {

            tagAsJdkJfrEventSub(index);
        }
    }

    public static boolean isSerialized(Class<?> clazz) {
        return predicate(clazz, SERIALIZED_BIT);
    }

    public static void setSerialized(Class<?> clazz) {
        long id = getTraceIdMap().getId(clazz);
        getTraceIdMap().setId(clazz, id | SERIALIZED_BIT);
    }

    public static void clearSerialized(Class<?> clazz) {
        long id = getTraceIdMap().getId(clazz);
        if (isSerialized(clazz)) {
            getTraceIdMap().setId(clazz, id ^ SERIALIZED_BIT);
        }
        assert (!isSerialized(clazz));
    }
}
