/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.source;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.test.polyglot.PolyglotCachingTest;

@SuppressWarnings("deprecation")
public class SourceInternalizationLegacyTest {

    @Test
    public void testSourceIdentity() throws RuntimeException, URISyntaxException, IOException {
        assertNotSame(Source.newBuilder("1").mimeType("").language("").name("").build(),
                        Source.newBuilder("2").mimeType("").language("").name("").build());
        assertSame(Source.newBuilder("1").mimeType("").language("").name("").build(),
                        Source.newBuilder("1").mimeType("").language("").name("").build());

        assertNotSame(Source.newBuilder("").mimeType("1").language("").name("").build(),
                        Source.newBuilder("").mimeType("2").language("").name("").build());
        assertSame(Source.newBuilder("").mimeType("1").language("").name("").build(),
                        Source.newBuilder("").mimeType("1").language("").name("").build());

        assertNotSame(Source.newBuilder("").mimeType("").language("1").name("").build(),
                        Source.newBuilder("").mimeType("").language("2").name("").build());
        assertSame(Source.newBuilder("").mimeType("").language("1").name("").build(),
                        Source.newBuilder("").mimeType("").language("1").name("").build());

        assertNotSame(Source.newBuilder("").mimeType("").language("").name("1").build(),
                        Source.newBuilder("").mimeType("").language("").name("2").build());
        assertSame(Source.newBuilder("").mimeType("").language("").name("1").build(),
                        Source.newBuilder("").mimeType("").language("").name("1").build());

        assertNotSame(Source.newBuilder("").mimeType("").language("").name("").content("1").build(),
                        Source.newBuilder("").mimeType("").language("").name("").content("2").build());
        assertSame(Source.newBuilder("").mimeType("").language("").name("").content("1").build(),
                        Source.newBuilder("").mimeType("").language("").name("").content("1").build());

        assertNotSame(Source.newBuilder("").mimeType("").language("").name("").interactive().build(),
                        Source.newBuilder("").mimeType("").language("").name("").build());
        assertSame(Source.newBuilder("").mimeType("").language("").name("").interactive().build(),
                        Source.newBuilder("").mimeType("").language("").name("").interactive().build());

        assertNotSame(Source.newBuilder("").mimeType("").language("").name("").internal().build(),
                        Source.newBuilder("").mimeType("").language("").name("").build());
        assertSame(Source.newBuilder("").mimeType("").language("").name("").internal().build(),
                        Source.newBuilder("").mimeType("").language("").name("").internal().build());

        assertNotSame(Source.newBuilder("").mimeType("").language("").name("").uri(new URI("s:///333")).build(),
                        Source.newBuilder("").mimeType("").language("").name("").uri(new URI("s:///332")).build());
        assertSame(Source.newBuilder("").mimeType("").language("").name("").uri(new URI("s:///333")).build(),
                        Source.newBuilder("").mimeType("").language("").name("").uri(new URI("s:///333")).build());

        File file1 = createTempFile("1");
        File file2 = createTempFile("2");

        assertNotSame(Source.newBuilder(file1).mimeType("").language("").build(),
                        Source.newBuilder(file2).mimeType("").language("").build());
        assertSame(Source.newBuilder(file1).mimeType("").language("").build(),
                        Source.newBuilder(file1).mimeType("").language("").build());

        assertNotSame(Source.newBuilder(file1.toURI().toURL()).mimeType("").language("").build(),
                        Source.newBuilder(file2.toURI().toURL()).mimeType("").language("").build());
        assertSame(Source.newBuilder(file1.toURI().toURL()).mimeType("").language("").build(),
                        Source.newBuilder(file1.toURI().toURL()).mimeType("").language("").build());

        Source fileSource2 = Source.newBuilder(file2).mimeType("").language("").build();
        Source urlSource2 = Source.newBuilder(file2.toURI().toURL()).mimeType("").language("").build();
        Files.write(file2.toPath(), "3".getBytes());
        assertNotSame(Source.newBuilder(file2).mimeType("").language("").build(), fileSource2);
        assertNotSame(Source.newBuilder(file2.toURI().toURL()).mimeType("").language("").build(), urlSource2);

        file1.delete();
        file2.delete();
    }

    private File createTempFile(String content) throws IOException {
        File file = File.createTempFile(getClass().getSimpleName(), "tmp");
        Files.write(file.toPath(), content.getBytes());
        return file;
    }

    private static final AtomicInteger RUN = new AtomicInteger(0);

    @Test
    public void testMultiThreadedAccess() throws InterruptedException, ExecutionException {
        final int runs = 100;
        final int parallism = 100;
        final int sources = 100;
        final String testCharacters = "SourceInternalizationTest:" + RUN.incrementAndGet() + ":";

        ExecutorService service = Executors.newFixedThreadPool(18);

        for (int run = 0; run < runs; run++) {
            final int currentRun = run;
            List<Future<List<Source>>> futures = new ArrayList<>();
            for (int p = 0; p < parallism; p++) {
                futures.add(service.submit(() -> {
                    List<Source> returnedSources = new ArrayList<>();
                    for (int source = 0; source < sources; source++) {
                        String name = testCharacters + ":" + currentRun + ":" + source;
                        returnedSources.add(Source.newBuilder(name).mimeType("mime").interactive().internal().name("name").language("language").build());
                    }
                    return returnedSources;
                }));
            }

            List<Source> prevSources = null;
            for (Future<List<Source>> future : futures) {
                if (prevSources == null) {
                    prevSources = future.get();
                } else {
                    List<Source> currentSources = future.get();
                    Iterator<Source> currentIterator = currentSources.iterator();
                    Iterator<Source> prevIterator = prevSources.iterator();
                    while (currentIterator.hasNext() && prevIterator.hasNext()) {
                        assertSame(currentIterator.next(), prevIterator.next());
                    }
                    assertFalse(currentIterator.hasNext());
                    assertFalse(prevIterator.hasNext());
                    prevSources = currentSources;
                }
            }
        }
        service.shutdown();
        assertTrue(service.awaitTermination(10000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSourceInterning() {
        byte[] bytes = new byte[16 * 1024 * 1024];
        byte byteValue = (byte) 'a';
        Arrays.fill(bytes, byteValue);
        String testString = new String(bytes); // big string

        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        List<WeakReference<Object>> sources = new ArrayList<>();
        for (int i = 0; i < PolyglotCachingTest.GC_TEST_ITERATIONS; i++) {
            sources.add(new WeakReference<>(createTestSource(testString, i), queue));
            System.gc();
        }

        int refsCleared = 0;
        while (queue.poll() != null) {
            refsCleared++;
        }
        // we need to have any refs cleared for this test to have any value
        Assert.assertTrue(refsCleared > 0);
    }

    private static Object createTestSource(String testString, int i) {
        String testStringIteraton = testString.substring(i, testString.length());
        return Source.newBuilder(testStringIteraton).name(String.valueOf(i)).mimeType("").build();
    }

}
