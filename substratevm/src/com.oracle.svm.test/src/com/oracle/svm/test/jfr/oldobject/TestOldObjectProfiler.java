/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.test.jfr.oldobject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jdk.graal.compiler.word.Word;
import jdk.jfr.Recording;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.oldobject.JfrOldObject;
import com.oracle.svm.core.jfr.oldobject.JfrOldObjectProfiler;
import com.oracle.svm.test.jfr.AbstractJfrTest;

import jdk.graal.compiler.api.directives.GraalDirectives;

public class TestOldObjectProfiler extends AbstractJfrTest {

    /*
     * Old object samples will not have allocation ticks set correctly if JfrTicks is not first
     * initialized. We need to create the first JFR recording to lazily initialize JfrTicks.
     */
    @BeforeClass
    public static void initializeJfrTicks() {
        GraalDirectives.blackhole(new Recording());
    }

    @Test
    public void testScavenge() {
        int count = 10;
        JfrOldObjectProfiler profiler = newProfiler(count);
        List<TinyObject> objectsA = sampleObjects(profiler, 1, count);
        validate(profiler, objectsA);

        /* Free all existing samples. */
        GraalDirectives.blackhole(objectsA);
        objectsA = null;
        System.gc();

        /*
         * Queue is full but the old samples will be scavenged because the referenced objects are no
         * longer alive.
         */
        List<TinyObject> objectsB = sampleObjects(profiler, 100, count);
        validate(profiler, objectsB);
        GraalDirectives.blackhole(objectsB);
    }

    @Test
    public void testEmitSkipMiddle() {
        testEmitSkip(4);
    }

    @Test
    public void testEmitSkipYoungest() {
        testEmitSkip(9);
    }

    @Test
    public void testEmitSkipOldest() {
        testEmitSkip(0);
    }

    private static void testEmitSkip(int index) {
        int count = 10;
        JfrOldObjectProfiler profiler = newProfiler(count);
        List<TinyObject> a = sampleObjects(profiler, 1, count);
        validate(profiler, a);

        /* Free one specific sample. */
        a.remove(index);
        System.gc();

        validate(profiler, a);
        GraalDirectives.blackhole(a);
    }

    @Test
    public void testEmitSkipAll() {
        int count = 10;
        JfrOldObjectProfiler profiler = newProfiler(count);
        List<TinyObject> objects = sampleObjects(profiler, 1, count);
        validate(profiler, objects);

        /* Free all samples. */
        GraalDirectives.blackhole(objects);
        objects = null;
        System.gc();

        validate(profiler, Collections.emptyList());
    }

    @Test
    public void testEvictYoungest() {
        int count = 10;
        JfrOldObjectProfiler profiler = newProfiler(count);

        /* Add samples until there is only space for one more sample. */
        List<TinyObject> objectsA = sampleObjects(profiler, 10, count - 1);
        validate(profiler, objectsA);

        /* Add one sample with a small allocation size. */
        List<TinyObject> objectsB = sampleObjects(profiler, 1, 1);
        objectsB.addAll(objectsA);
        validate(profiler, objectsB);

        /* Add one sample with a large allocation size. */
        List<TinyObject> objectsC = sampleObjects(profiler, 100, 1);
        objectsC.addAll(objectsA);
        validate(profiler, objectsC);

        /* Keep all objects alive. */
        GraalDirectives.blackhole(objectsA);
        GraalDirectives.blackhole(objectsB);
        GraalDirectives.blackhole(objectsC);
    }

    @Test
    public void testEvictMiddle() {
        int count = 10;
        JfrOldObjectProfiler profiler = newProfiler(count);

        /* Add a few samples. */
        List<TinyObject> objectsA = sampleObjects(profiler, 10, count / 2);
        validate(profiler, objectsA);

        /* Add one sample with a small allocation size. */
        List<TinyObject> objectsB = sampleObjects(profiler, 1, 1);
        objectsB.addAll(objectsA);
        validate(profiler, objectsB);

        /* Add a few more samples. */
        List<TinyObject> objectsC = sampleObjects(profiler, 100, count / 2);
        objectsC.addAll(objectsA);
        validate(profiler, objectsC);

        /* Keep all objects alive. */
        GraalDirectives.blackhole(objectsA);
        GraalDirectives.blackhole(objectsB);
        GraalDirectives.blackhole(objectsC);
    }

    @Test
    public void testEvictOldest() {
        int count = 10;
        JfrOldObjectProfiler profiler = newProfiler(count);

        /* Add one sample with a small allocation size. */
        List<TinyObject> objectsA = sampleObjects(profiler, 1, 1);
        validate(profiler, objectsA);

        /* Add samples with a higher allocation size. */
        List<TinyObject> objectsB = sampleObjects(profiler, 100, count);
        validate(profiler, objectsB);

        /* Keep all objects alive. */
        GraalDirectives.blackhole(objectsA);
        GraalDirectives.blackhole(objectsB);
    }

    private static JfrOldObjectProfiler newProfiler(int queueSize) {
        JfrOldObjectProfiler profiler = new JfrOldObjectProfiler();
        profiler.configure(queueSize);
        profiler.reset();
        return profiler;
    }

    private static List<TinyObject> sampleObjects(JfrOldObjectProfiler profiler, int initialValue, int count) {
        if (!HasJfrSupport.get()) {
            /* Prevent that the code below is reachable on platforms that don't support JFR. */
            Assert.fail("JFR is not supported on this platform.");
            return null;
        }

        ArrayList<TinyObject> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int value = initialValue + i;
            TinyObject obj = new TinyObject(value);
            result.add(obj);

            boolean success = JfrOldObjectProfiler.TestingBackdoor.sample(profiler, obj, Word.unsigned(value), Integer.MIN_VALUE);
            assertTrue(success);
        }
        return result;
    }

    private static void validate(JfrOldObjectProfiler profiler, List<TinyObject> objects) {
        List<TinyObject> remainingObjects = new ArrayList<>(objects);

        JfrOldObject cur = JfrOldObjectProfiler.TestingBackdoor.getOldestObject(profiler);
        while (cur != null) {
            Object obj = cur.getReferent();
            if (obj != null) {
                assertTrue(obj instanceof TinyObject);
                assertTrue(remainingObjects.remove(obj));
                assertTrue(cur.getObjectSize().aboveThan(0));
                assertTrue(cur.getAllocationTicks() > 0);
                assertEquals(Thread.currentThread().threadId(), cur.getThreadId());
                assertEquals(Integer.MIN_VALUE, cur.getArrayLength());
            }
            cur = (JfrOldObject) cur.getNext();
        }

        assertEquals(0, remainingObjects.size());
    }
}
