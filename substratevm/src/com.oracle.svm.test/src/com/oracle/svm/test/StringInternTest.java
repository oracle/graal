/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests {@link String#intern()} of strings created at run time: image strings are the canonical
 * instances, run-time strings intern to the first instance while it is reachable, interned strings
 * can be collected, and strings with colliding hash codes neither break nor slow down interning.
 */
public class StringInternTest {

    /** A literal of the image, so interning an equal string must return this instance. */
    private static final String COLLIDING_LITERAL = "AaAaAaAa";

    private static String copy(String s) {
        return new String(s.toCharArray());
    }

    private static String runtimeString(String prefix) {
        return prefix + System.nanoTime();
    }

    private static void collectGarbage() {
        for (int i = 0; i < 3; i++) {
            System.gc();
        }
    }

    @Test
    public void imageStringsAreCanonical() {
        String[] literals = {"Signature", "SHA-256", "Gr\u00fc\u00dfe", "\u20acuro \u2211", "", "f5a5a608", COLLIDING_LITERAL};
        Assert.assertEquals("the test needs a literal whose hash code is 0", 0, "f5a5a608".hashCode());
        for (String literal : literals) {
            /* The second call can be answered differently than the first one. */
            Assert.assertSame(literal, copy(literal).intern());
            Assert.assertSame(literal, copy(literal).intern());
        }
    }

    @Test
    public void runtimeStringsInternToTheFirstInstance() {
        String[] strings = {runtimeString("intern.latin1."), runtimeString("intern.utf16.\u20ac."), runtimeString("")};
        for (String s : strings) {
            Assert.assertSame(s, s.intern());
            Assert.assertSame(s, copy(s).intern());
            Assert.assertSame(s, copy(s).intern());
        }
    }

    @Test
    public void runtimeStringWithTheHashCodeOfAnImageStringStaysDistinct() {
        /* Not a literal, so not an image string. */
        String colliding = new String(new char[]{'B', 'B', 'A', 'a', 'B', 'B', 'A', 'a'});
        Assert.assertEquals(COLLIDING_LITERAL.hashCode(), colliding.hashCode());
        Assert.assertNotEquals(COLLIDING_LITERAL, colliding);
        Assert.assertSame(colliding, colliding.intern());
        Assert.assertSame(colliding, copy(colliding).intern());
        Assert.assertSame(COLLIDING_LITERAL, copy(COLLIDING_LITERAL).intern());
    }

    @Test
    public void internedStringsCanBeCollected() {
        List<WeakReference<String>> references = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) {
            String interned = runtimeString("intern.weak." + i + ".").intern();
            /* Intern equal strings again so that every lookup structure has seen the string. */
            Assert.assertSame(interned, copy(interned).intern());
            if (i % 100 == 0) {
                references.add(new WeakReference<>(interned));
            }
        }
        collectGarbage();
        int alive = 0;
        for (WeakReference<String> reference : references) {
            if (reference.get() != null) {
                alive++;
            }
        }
        Assert.assertEquals("interned strings that nothing else references must be collectable", 0, alive);
    }

    @Test
    public void stringsWithCollidingHashCodes() {
        String[] strings = new String[1 << 16];
        for (int i = 0; i < strings.length; i++) {
            StringBuilder sb = new StringBuilder(32);
            sb.append("intern.flood.");
            for (int block = 0; block < 16; block++) {
                sb.append((i >>> block & 1) == 0 ? "Aa" : "BB");
            }
            strings[i] = sb.toString();
        }
        Assert.assertEquals(strings[0].hashCode(), strings[strings.length - 1].hashCode());
        long start = System.nanoTime();
        for (String s : strings) {
            Assert.assertSame(s, s.intern());
        }
        for (String s : strings) {
            Assert.assertSame(s, copy(s).intern());
        }
        /*
         * Linear lookups would take about 2^31 string comparisons; a far too generous bound still
         * tells them apart from the expected constant or logarithmic ones.
         */
        long seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);
        Assert.assertTrue("interning 2^16 colliding strings twice took " + seconds + " s", seconds < 30);
    }

    @Test
    public void threadsAgreeOnTheInternedInstance() throws InterruptedException {
        String[] contents = new String[20_000];
        String prefix = runtimeString("intern.threads.") + ".";
        for (int i = 0; i < contents.length; i++) {
            contents[i] = prefix + i;
        }
        /* Holds the first interned instance of each content, so that it stays the canonical one. */
        ConcurrentHashMap<String, String> first = new ConcurrentHashMap<>();
        AtomicInteger mismatches = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            threads.add(new Thread(() -> {
                for (int round = 0; round < 10; round++) {
                    for (String content : contents) {
                        String interned = copy(content).intern();
                        String previous = first.putIfAbsent(content, interned);
                        if (previous != null && previous != interned) {
                            mismatches.incrementAndGet();
                        }
                    }
                }
            }));
        }
        threads.add(new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                System.gc();
            }
        }));
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        Assert.assertEquals(0, mismatches.get());
    }
}
