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
import static org.junit.Assert.assertEquals;

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
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.PolyglotCachingTest;

public class SourceInternalizationTest extends AbstractPolyglotTest {

    @Test
    public void testSourceIdentity() throws RuntimeException, URISyntaxException, IOException {
        setupEnv();
        assertNotSame(Source.newBuilder("", "1", "").build(),
                        Source.newBuilder("", "2", "").build());
        assertSame(Source.newBuilder("", "1", "").build(),
                        Source.newBuilder("", "1", "").build());

        assertNotSame(Source.newBuilder("", "", "").mimeType("text/javascript").build(),
                        Source.newBuilder("", "", "").mimeType("text/R").build());
        assertSame(Source.newBuilder("", "", "").mimeType("text/javascript").build(),
                        Source.newBuilder("", "", "").mimeType("text/javascript").build());

        assertNotSame(Source.newBuilder("1", "", "").build(),
                        Source.newBuilder("2", "", "").build());
        assertSame(Source.newBuilder("1", "", "").build(),
                        Source.newBuilder("1", "", "").build());

        assertNotSame(Source.newBuilder("", "", "1").build(),
                        Source.newBuilder("", "", "2").build());
        assertSame(Source.newBuilder("", "", "1").build(),
                        Source.newBuilder("", "", "1").build());

        assertNotSame(Source.newBuilder("", "", "").content("1").build(),
                        Source.newBuilder("", "", "").content("2").build());
        assertSame(Source.newBuilder("", "", "").content("1").build(),
                        Source.newBuilder("", "", "").content("1").build());

        assertNotSame(Source.newBuilder("", "", "").interactive(true).build(),
                        Source.newBuilder("", "", "").interactive(false).build());
        assertSame(Source.newBuilder("", "", "").interactive(true).build(),
                        Source.newBuilder("", "", "").interactive(true).build());

        assertNotSame(Source.newBuilder("", "", "").internal(true).build(),
                        Source.newBuilder("", "", "").internal(false).build());
        assertSame(Source.newBuilder("", "", "").internal(true).build(),
                        Source.newBuilder("", "", "").internal(true).build());

        assertNotSame(Source.newBuilder("", "", "").uri(new URI("s:///333")).uri(new URI("s:///333")).build(),
                        Source.newBuilder("", "", "").uri(new URI("s:///332")).build());
        assertSame(Source.newBuilder("", "", "").uri(new URI("s:///333")).build(),
                        Source.newBuilder("", "", "").uri(new URI("s:///333")).build());

        TruffleFile file1 = languageEnv.getTruffleFile(createTempFile("1").getPath());
        TruffleFile file2 = languageEnv.getTruffleFile(createTempFile("2").getPath());

        assertNotSame(Source.newBuilder("", file1).build(),
                        Source.newBuilder("", file2).build());
        assertSame(Source.newBuilder("", file1).build(),
                        Source.newBuilder("", file1).build());

        assertNotSame(Source.newBuilder("", file1.toUri().toURL()).build(),
                        Source.newBuilder("", file2.toUri().toURL()).build());
        assertSame(Source.newBuilder("", file1.toUri().toURL()).build(),
                        Source.newBuilder("", file1.toUri().toURL()).build());

        Source fileSource2 = Source.newBuilder("", file2).build();
        Source urlSource2 = Source.newBuilder("", file2).build();

        file2.newBufferedWriter().write("3".toCharArray());
        assertNotSame(Source.newBuilder("", file2), fileSource2);
        assertNotSame(Source.newBuilder("", file2.toUri().toURL()), urlSource2);

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
                        returnedSources.add(Source.newBuilder("language", name, "name").interactive(true).internal(true).build());
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
    public void testUncachedAreNotInterned() {
        Source source1 = Source.newBuilder("language", "", "name").interactive(true).internal(true).cached(false).build();
        Source source2 = Source.newBuilder("language", "", "name").interactive(true).internal(true).cached(false).build();
        assertNotSame(source1, source2);
        assertEquals(source1, source2);

        source1 = Source.newBuilder("language", "", "name").interactive(true).internal(true).cached(true).build();
        source2 = Source.newBuilder("language", "", "name").interactive(true).internal(true).cached(true).build();

        assertSame(source1, source2);
    }

    @Test
    public void testSourceInterning() {
        Assume.assumeFalse("This test is too slow in fastdebug.", System.getProperty("java.vm.version").contains("fastdebug"));

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
        return Source.newBuilder("language", testStringIteraton, String.valueOf(i)).name(String.valueOf(i)).build();
    }

}
