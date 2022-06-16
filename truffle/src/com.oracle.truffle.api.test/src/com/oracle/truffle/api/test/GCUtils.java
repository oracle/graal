/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.sun.management.HotSpotDiagnosticMXBean;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.VMRuntime;
import org.graalvm.visualvm.lib.jfluid.heap.Heap;
import org.graalvm.visualvm.lib.jfluid.heap.HeapFactory;
import org.graalvm.visualvm.lib.jfluid.heap.Instance;
import org.graalvm.visualvm.lib.jfluid.heap.JavaClass;
import org.graalvm.visualvm.lib.jfluid.heap.ObjectArrayInstance;
import org.junit.Assert;

import javax.management.MBeanServer;

/**
 * Test collectible objects.
 */
public final class GCUtils {

    private static final ReachabilityAnalyser<?> analyser = selectAnalyser();

    private GCUtils() {
        throw new IllegalStateException("No instance allowed.");
    }

    /**
     * Number of iterations of {@link System#gc()} calls after which the GC is eventually run.
     */
    public static final int GC_TEST_ITERATIONS = 15;

    /**
     * Calls the objectFactory {@link #GC_TEST_ITERATIONS} times and asserts that at least one
     * object provided by the factory is collected.
     *
     * @param objectFactory producer of collectible object per an iteration
     */
    public static void assertObjectsCollectible(Function<Integer, Object> objectFactory) {
        List<Reference<Object>> collectibleObjects = new ArrayList<>();
        for (int i = 0; i < GC_TEST_ITERATIONS; i++) {
            collectibleObjects.add(new WeakReference<>(objectFactory.apply(i)));
            System.gc();
        }
        if (!analyser.anyCollected(collectibleObjects, true).isCollected()) {
            Assert.fail("Objects are not collected.");
        }
    }

    /**
     * Asserts that given reference is cleaned, the referent is freed by garbage collector.
     *
     * @param message the message for an {@link AssertionError} when referent is not freed by GC
     * @param ref the reference
     */
    public static void assertGc(final String message, final Reference<?> ref) {
        if (!analyser.allCollected(Collections.singleton(ref), true).isCollected()) {
            Assert.fail(message);
        }
    }

    /**
     * Asserts that given reference is not cleaned, the referent is freed by garbage collector.
     *
     * @param message the message for an {@link AssertionError} when referent is not freed by GC
     * @param ref the reference
     */
    public static void assertNotGc(final String message, final Reference<?> ref) {
        if (analyser.allCollected(Collections.singleton(ref), false).isCollected()) {
            Assert.fail(message);
        }
    }

    private static ReachabilityAnalyser<?> selectAnalyser() {
        if (ImageInfo.inImageCode()) {
            // In the native-image, the heap dump to slow to be used.
            return new GCAnalyser();
        } else {
            return new HeapDumpAnalyser();
        }
    }

    private static final class Result {

        private final boolean collected;

        Result(boolean collected) {
            this.collected = collected;
        }

        boolean isCollected() {
            return collected;
        }
    }

    private abstract static class ReachabilityAnalyser<T> {

        final Result allCollected(Collection<? extends Reference<?>> references, boolean force) {
            if (references.isEmpty()) {
                throw new IllegalArgumentException("References must be non empty.");
            }
            Function<T, Result> test = (ctx) -> {
                for (Reference<?> ref : references) {
                    Result result = isCollected(ref, ctx);
                    if (!result.isCollected()) {
                        return result;
                    }
                }
                return new Result(true);
            };
            return analyse(references, force, test);
        }

        final Result anyCollected(Collection<? extends Reference<?>> references, boolean force) {
            if (references.isEmpty()) {
                throw new IllegalArgumentException("References must be non empty.");
            }
            Function<T, Result> test = (ctx) -> {
                Result firstResult = null;
                for (Reference<?> ref : references) {
                    Result result = isCollected(ref, ctx);
                    firstResult = firstResult == null ? result : firstResult;
                    if (result.isCollected()) {
                        return result;
                    }
                }
                return firstResult;
            };
            return analyse(references, force, test);
        }

        abstract Result analyse(Collection<? extends Reference<?>> references, boolean force, Function<T, Result> testCollected);

        abstract Result isCollected(Reference<?> reference, T context);
    }

    private static final class HeapDumpAnalyser extends ReachabilityAnalyser<HeapDumpAnalyser.Context> {

        private static volatile HotSpotDiagnosticMXBean hotSpotDiagnosticMBean;
        private static volatile Reference<?>[] todo;

        private static class Context {

            final List<Instance> todoInstances;
            final Map<Reference<?>, Integer> todoIndexes;

            Context(List<Instance> todoInstances, Map<Reference<?>, Integer> todoIndexes) {
                this.todoInstances = todoInstances;
                this.todoIndexes = todoIndexes;
            }
        }

        @Override
        Result analyse(Collection<? extends Reference<?>> references, boolean force, Function<Context, Result> testCollected) {
            try {
                Path tmpDirectory = Files.createTempDirectory(GCUtils.class.getSimpleName().toLowerCase());
                try {
                    Path heapDumpFile = tmpDirectory.resolve("heapdump.hprof");
                    System.gc();    // Perform GC to minimize heap size and speed up heap queries.
                    Map<Reference<?>, Integer> todoIndexes = prepareTodoAndTakeHeapDump(references, heapDumpFile);
                    Heap heap = HeapFactory.createHeap(heapDumpFile.toFile());
                    JavaClass trackableReferenceClass = heap.getJavaClassByName(HeapDumpAnalyser.class.getName());
                    ObjectArrayInstance todoArray = (ObjectArrayInstance) trackableReferenceClass.getValueOfStaticField("todo");
                    return testCollected.apply(new Context(todoArray.getValues(), todoIndexes));
                } finally {
                    delete(tmpDirectory);
                }
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        @Override
        Result isCollected(Reference<?> reference, Context context) {
            int index = context.todoIndexes.get(reference);
            Instance referenceInstance = context.todoInstances.get(index);
            Instance referent = (Instance) referenceInstance.getValueOfField("referent");
            return new Result(referent == null || referent.getNearestGCRootPointer() == null);
        }

        private static Map<Reference<?>, Integer> prepareTodoAndTakeHeapDump(Collection<? extends Reference<?>> references, Path outputFile) throws IOException {
            synchronized (HeapDumpAnalyser.class) {
                assert todo == null : "Todo must be null before assign.";
                todo = references.toArray(new Reference<?>[0]);
                Map<Reference<?>, Integer> todoIndexes = new IdentityHashMap<>();
                for (int i = 0; i < todo.length; i++) {
                    todoIndexes.put(todo[i], i);
                }
                try {
                    takeHeapDump(outputFile);
                } finally {
                    todo = null;
                }
                return todoIndexes;
            }
        }

        private static void takeHeapDump(Path outputFile) throws IOException, UnsupportedOperationException {
            if (ImageInfo.inImageRuntimeCode()) {
                VMRuntime.dumpHeap(outputFile.toString(), false);
            } else {
                getHotSpotDiagnosticMBean().dumpHeap(outputFile.toString(), false);
            }
        }

        private static HotSpotDiagnosticMXBean getHotSpotDiagnosticMBean() throws IOException {
            HotSpotDiagnosticMXBean res = hotSpotDiagnosticMBean;
            if (res == null) {
                synchronized (GCUtils.class) {
                    res = hotSpotDiagnosticMBean;
                    if (res == null) {
                        MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
                        res = ManagementFactory.newPlatformMXBeanProxy(platformMBeanServer, "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class);
                        hotSpotDiagnosticMBean = res;
                    }
                }
            }
            return res;
        }

        private static void delete(Path file) throws IOException {
            if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
                try (DirectoryStream<Path> content = Files.newDirectoryStream(file)) {
                    for (Path child : content) {
                        delete(child);
                    }
                }
            }
            Files.delete(file);
        }
    }

    private static final class GCAnalyser extends ReachabilityAnalyser<Void> {

        @Override
        Result analyse(Collection<? extends Reference<?>> references, boolean performAllocations, Function<Void, Result> testCollected) {
            int blockSize = 100_000;
            final List<byte[]> blocks = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                Result result = testCollected.apply(null);
                if (result.isCollected()) {
                    return result;
                }
                try {
                    System.gc();
                } catch (OutOfMemoryError oom) {
                }
                try {
                    System.runFinalization();
                } catch (OutOfMemoryError oom) {
                }
                if (performAllocations) {
                    try {
                        blocks.add(new byte[blockSize]);
                        blockSize = (int) (blockSize * 1.3);
                    } catch (OutOfMemoryError oom) {
                        blockSize >>>= 1;
                    }
                }
                if (i % 10 == 0) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
            return new Result(false);
        }

        @Override
        Result isCollected(Reference<?> reference, Void context) {
            return new Result(reference.get() == null);
        }
    }
}
