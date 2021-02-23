/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

import com.oracle.svm.jfr.JfrRuntimeAccess;
import jdk.jfr.internal.JVM;
import org.graalvm.nativeimage.ImageSingletons;

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

    private static final AtomicLong classLoaderCounter = new AtomicLong(1);
    private static final AtomicLong packageCounter = new AtomicLong(1);
    private static final AtomicLong moduleCounter = new AtomicLong(1);
    private static final AtomicLong threadCounter = new AtomicLong(1);

    private static JfrTraceIdMap getTraceIdMap() {
        return ImageSingletons.lookup(JfrRuntimeAccess.class).getTraceIdMap();
    }

    public static void tag(Object obj, long bits) {
        JfrTraceIdMap map = getTraceIdMap();
        long id = map.getId(obj);
        assert id != -1;
        map.setId(obj, (id & ~0xff) | (bits & 0xff));
    }

    public static boolean predicate(Object obj, long bits) {
        JfrTraceIdMap map = getTraceIdMap();
        long id = map.getId(obj);
        assert id != -1;
        return (id & bits) != 0;
    }

    public static boolean predicate(Package p, long bits) {
        Map<String, Long> map = getTraceIdMap().getPackageMap();
        long id = map.get(p.getName());
        assert id != -1;
        return (id & bits) != 0;
    }

    public static void setUsedThisEpoch(Object obj) {
        tag(obj, JfrTraceIdEpoch.thisEpochBit());
    }

    public static boolean isUsedThisEpoch(Object obj) {
        return predicate(obj, TRANSIENT_BIT | JfrTraceIdEpoch.thisEpochBit());
    }

    public static long getTraceIdRaw(Object obj) {
        return getTraceIdMap().getId(obj);
    }

    public static long getTraceId(Package p) {
        Long traceid = getTraceIdMap().getPackageMap().get(p.getName());
        if (traceid != null) {
            return traceid >> TRACE_ID_SHIFT;
        } else {
            return -1;
        }
    }

    public static long getTraceId(Object obj) {
        return getTraceIdMap().getId(obj) >> TRACE_ID_SHIFT;
    }

    public static long load(Class<?> clazz) {
        return JfrTraceIdLoadBarrier.load(clazz);
    }

    private static void tagAsJdkJfrEvent(Class<?> clazz) {
        JfrTraceIdMap map = getTraceIdMap();
        long id = map.getId(clazz);
        assert id != -1;
        map.setId(clazz, id | JDK_JFR_EVENT_KLASS);
    }

    private static void tagAsJdkJfrEventSub(Class<?> clazz) {
        JfrTraceIdMap map = getTraceIdMap();
        long id = map.getId(clazz);
        assert id != -1;
        map.setId(clazz, id | JDK_JFR_EVENT_SUBKLASS);
    }

    private static boolean isEventClass(Class<?> clazz) {
        JfrTraceIdMap map = getTraceIdMap();
        long id = map.getId(clazz);
        assert id != -1;
        return (id & (JDK_JFR_EVENT_KLASS | JDK_JFR_EVENT_SUBKLASS)) != 0;
    }

    private static boolean setSystemEventClass(Class<?> clazz) {
        String className = clazz.getCanonicalName();
        if (className != null && className.equals("jdk.internal.event.Event")
                && clazz.getClassLoader() == null || clazz.getClassLoader() == ClassLoader.getSystemClassLoader()) {
            tagAsJdkJfrEvent(clazz);
            return true;
        }

        if (className != null && className.equals("jdk.jfr.Event")
                && clazz.getClassLoader() == null || clazz.getClassLoader() == ClassLoader.getSystemClassLoader()) {
            tagAsJdkJfrEvent(clazz);
            return true;
        }
        return false;
    }

    public static long assign(Class<?> clazz) {
        assert clazz != null;
        assert getTraceIdMap().getId(clazz) == -1;
        long typeId = JVM.getJVM().getTypeId(clazz);
        getTraceIdMap().setId(clazz, typeId << TRACE_ID_SHIFT);
        if (!setSystemEventClass(clazz)) {
            Class<?> superClazz = clazz.getSuperclass();
            if (superClazz != null) {
                if (getTraceIdMap().getId(superClazz) == -1) {
                    assign(superClazz);
                }
                if (isEventClass(superClazz)) {
                    tagAsJdkJfrEventSub(clazz);
                }
            }
        }

        return typeId;
    }

    public static long assign(ClassLoader classLoader) {
        assert classLoader != null;
        long nextId = classLoaderCounter.getAndIncrement();
        getTraceIdMap().setId(classLoader, nextId << TRACE_ID_SHIFT);
        return nextId;
    }

    public static long assign(Package pkg) {
        assert pkg != null;
        long nextId = packageCounter.getAndIncrement();
        getTraceIdMap().getPackageMap().put(pkg.getName(), nextId << TRACE_ID_SHIFT);
        assert (getTraceId(pkg) == nextId);
        return nextId;
    }

    public static long assign(Module module) {
        assert module != null;
        long nextId = moduleCounter.getAndIncrement();
        getTraceIdMap().setId(module, nextId << TRACE_ID_SHIFT);
        return nextId;
    }

    public static void assign(Thread thread) {
//        if (!JfrAvailability.withJfr) {
//            return;
//        }
        assert thread != null;
        long nextId = threadCounter.incrementAndGet();
        getTraceIdMap().setId(thread, nextId << TRACE_ID_SHIFT);
    }

    public static void unassign(Thread thread) {
//        if (!JfrAvailability.withJfr) {
//            return;
//        }
        assert thread != null;
        getTraceIdMap().clearId(thread);
    }

    public static long load(ClassLoader classLoader) {
        return JfrTraceIdLoadBarrier.load(classLoader);
    }

    public static long load(Package pkg) {
        return JfrTraceIdLoadBarrier.load(pkg);
    }

    public static long load(Module module) {
        return JfrTraceIdLoadBarrier.load(module);
    }

    public static void setTransient(ClassLoader classLoader) {
        long id = getTraceIdMap().getId(classLoader);
        getTraceIdMap().setId(classLoader, id | TRANSIENT_BIT);
    }

    public static void setTransient(Module m) {
        long id = getTraceIdMap().getId(m);
        getTraceIdMap().setId(m, id | TRANSIENT_BIT);
    }

    public static boolean isSerialized(Object obj) {
        return predicate(obj, SERIALIZED_BIT);
    }

    public static void setSerialized(Object obj) {
        long id = getTraceIdMap().getId(obj);
        assert (id != -1);
        getTraceIdMap().setId(obj, id | SERIALIZED_BIT);
    }

    public static boolean isSerialized(Package p) {
        return predicate(p, SERIALIZED_BIT);
    }

    public static void setSerialized(Package p) {
        long id = getTraceIdMap().getPackageMap().get(p.getName());
        assert (id != -1);
        getTraceIdMap().getPackageMap().put(p.getName(), id | SERIALIZED_BIT);
    }

    public static void clearSerialized(Object obj) {
        long id = getTraceIdMap().getId(obj);
        assert (id != -1);
        if (isSerialized(obj)) {
            getTraceIdMap().setId(obj, id ^ SERIALIZED_BIT);
        }
        assert (!isSerialized(obj));
    }

    public static void clearSerialized(Package p) {
        long id = getTraceIdMap().getPackageMap().get(p.getName());
        assert (id != -1);
        if (isSerialized(p)) {
            getTraceIdMap().getPackageMap().put(p.getName(), id ^ SERIALIZED_BIT);
        }
        assert (!isSerialized(p));
    }

}
