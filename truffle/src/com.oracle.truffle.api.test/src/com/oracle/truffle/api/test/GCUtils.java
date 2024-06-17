/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.GZIPOutputStream;

import com.sun.management.HotSpotDiagnosticMXBean;

import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.VMRuntime;
import org.graalvm.visualvm.lib.jfluid.heap.ArrayItemValue;
import org.graalvm.visualvm.lib.jfluid.heap.GCRoot;
import org.graalvm.visualvm.lib.jfluid.heap.Heap;
import org.graalvm.visualvm.lib.jfluid.heap.HeapFactory;
import org.graalvm.visualvm.lib.jfluid.heap.Instance;
import org.graalvm.visualvm.lib.jfluid.heap.JavaClass;
import org.graalvm.visualvm.lib.jfluid.heap.JavaFrameGCRoot;
import org.graalvm.visualvm.lib.jfluid.heap.JniLocalGCRoot;
import org.graalvm.visualvm.lib.jfluid.heap.ObjectArrayInstance;
import org.graalvm.visualvm.lib.jfluid.heap.ObjectFieldValue;
import org.graalvm.visualvm.lib.jfluid.heap.ThreadObjectGCRoot;
import org.graalvm.visualvm.lib.jfluid.heap.Value;
import org.junit.Assert;

import javax.management.MBeanServer;

/**
 * Test collectible objects.
 */
public final class GCUtils {

    private static final boolean PRESERVE_HEAP_DUMP_ON_FAILURE = Boolean.parseBoolean(System.getProperty(GCUtils.class.getSimpleName() + ".preserveHeapDumpOnFailure", "true"));

    /**
     * When set, and if the {@link #PRESERVE_HEAP_DUMP_ON_FAILURE} system property is set to
     * {@code true}, {@code GCUtils} relocates the heap dump to the designated folder. The target
     * heap dump file has the pattern {@code gcutils_heapdump_.+\.hprof} as produced by
     * {@link Files#createTempFile(Path, String, String, FileAttribute[])}.
     */
    private static final String SAVE_HEAP_DUMP_TO = System.getProperty(GCUtils.class.getSimpleName() + ".saveHeapDumpTo");

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
        Result result = analyser.anyCollected(collectibleObjects, true, true, PRESERVE_HEAP_DUMP_ON_FAILURE);
        if (!result.isCollected()) {
            Assert.fail(formatShortestGCRootPath("Objects are not collected.", result));
        }
    }

    /**
     * Asserts that given reference is cleaned, the referent is freed by garbage collector. From a
     * performance point of view, it is always better to call {@link #assertGc(String, Collection)}
     * or {@link #assertGc(Collection)} with a collection of references rather than repeatedly
     * calling this method with individual references.
     *
     * @param message the message for an {@link AssertionError} when referent is not freed by the GC
     * @param ref the reference
     * @see #assertGc(String, Collection)
     * @see #assertGc(Collection)
     */
    public static void assertGc(String message, Reference<?> ref) {
        Result result = analyser.allCollected(Collections.singleton(ref), true, true, PRESERVE_HEAP_DUMP_ON_FAILURE);
        if (!result.isCollected()) {
            Assert.fail(formatShortestGCRootPath(message, result));
        }
    }

    /**
     * Asserts that all given references are cleaned, the referents are freed by garbage collector.
     * From a performance point of view, it is always better to call this method with a collection
     * of references rather than repeatedly calling {@link #assertGc(String, Reference)} with
     * individual references.
     *
     * @param message the message for an {@link AssertionError} when referent is not freed by the GC
     * @param refs references their referents are to be released
     */
    public static void assertGc(String message, Collection<? extends Reference<?>> refs) {
        Result result = analyser.allCollected(refs, true, true, PRESERVE_HEAP_DUMP_ON_FAILURE);
        if (!result.isCollected()) {
            Assert.fail(formatShortestGCRootPath(message, result));
        }
    }

    /**
     * Asserts that all given references are cleaned, the referents are freed by garbage collector.
     * From a performance point of view, it is always better to call this method with a collection
     * of references rather than repeatedly calling {@link #assertGc(String, Reference)} with
     * individual references.
     *
     * @param refsWithMessages references, their referents are to be released, with messages for an
     *            {@link AssertionError} thrown when the referent is not freed by the GC
     */
    public static void assertGc(Collection<? extends Pair<String, Reference<?>>> refsWithMessages) {
        Map<Reference<?>, String> refsMap = new IdentityHashMap<>();
        for (Pair<String, Reference<?>> pair : refsWithMessages) {
            refsMap.putIfAbsent(pair.getRight(), pair.getLeft());
        }
        Result result = analyser.allCollected(refsMap.keySet(), true, true, PRESERVE_HEAP_DUMP_ON_FAILURE);
        if (!result.isCollected()) {
            String message = refsMap.get(result.getReference());
            Assert.fail(formatShortestGCRootPath(message, result));
        }
    }

    /**
     * Asserts that given reference is not cleaned, the referent is freed by garbage collector.
     *
     * @param message the message for an {@link AssertionError} when referent is not freed by GC
     * @param ref the reference
     */
    public static void assertNotGc(final String message, final Reference<?> ref) {
        if (analyser.allCollected(Collections.singleton(ref), false, false, false).isCollected()) {
            Assert.fail(message);
        }
    }

    private static ReachabilityAnalyser<?> selectAnalyser() {
        if (ImageInfo.inImageCode() || OSUtils.isWindows()) {
            // In the native-image, the heap dump to slow to be used.
            // On Windows there are problems with the Heap release, which prevents the headump file
            // from being deleted.
            return new GCAnalyser();
        } else {
            return new HeapDumpAnalyser();
        }
    }

    private static String formatShortestGCRootPath(String message, Result result) {
        Iterable<? extends InstanceReference> path = result.getShortestGCRootPath();
        if (path == null) {
            return message;
        }
        StringBuilder sb = new StringBuilder("Shortest GC root path:\n");
        boolean first = true;
        for (InstanceReference ref : path) {
            if (!first) {
                sb.append("\n");
            }
            sb.append("        ");
            sb.append(ref.className);
            switch (ref.kind) {
                case OBJECT_FIELD:
                    sb.append(".");
                    sb.append(ref.memberName);
                    break;
                case ARRAY_ELEMENT:
                    sb.append("[");
                    sb.append(ref.memberName);
                    sb.append("]");
                    break;
                default:
                    throw new IllegalArgumentException(ref.kind.toString());
            }
            if (first) {
                sb.append(" (instance id ").append(result.getInstanceId()).append(")");
                first = false;
            }
        }
        sb.append(" (");
        sb.append(result.getGCRootKind());
        sb.append(")");
        Iterable<? extends StackTraceElement> stack = result.getGcRootStackTrace();
        if (stack != null) {
            sb.append("\nheld by Java thread with thread id ").append(result.getGcRootThreadId());
            for (StackTraceElement frame : stack) {
                sb.append("\n");
                sb.append("        ");
                sb.append(frame);
            }
        }
        Path heapDumpFile = result.getHeapDumpFile();
        if (heapDumpFile != null) {
            sb.append("\nHeap dump stored in ").append(heapDumpFile.toAbsolutePath());
        }
        return String.format("%s%n%s", message, sb);
    }

    private enum ReferenceKind {
        OBJECT_FIELD,
        ARRAY_ELEMENT
    }

    private static final class InstanceReference {
        final ReferenceKind kind;
        final String className;
        final String memberName;

        InstanceReference(ReferenceKind kind, String className, String memberName) {
            this.kind = kind;
            this.className = className;
            this.memberName = memberName;
        }
    }

    private static final class Result {

        static final Result COLLECTED = new Result();

        private final boolean collected;
        private final Reference<?> reference;
        private final long instanceId;
        private final String gcRootKind;
        private final Iterable<? extends InstanceReference> shortestGCRootPath;
        private final long gcRootThreadId;
        private final Iterable<? extends StackTraceElement> gcRootStackTrace;
        private final Path heapDumpFile;

        private Result() {
            this.collected = true;
            this.reference = null;
            this.gcRootKind = null;
            this.instanceId = -1;
            this.shortestGCRootPath = null;
            this.gcRootThreadId = -1;
            this.gcRootStackTrace = null;
            this.heapDumpFile = null;
        }

        Result(Reference<?> reference) {
            this.collected = false;
            this.reference = reference;
            this.gcRootKind = null;
            this.instanceId = -1;
            this.shortestGCRootPath = null;
            this.gcRootThreadId = -1;
            this.gcRootStackTrace = null;
            this.heapDumpFile = null;
        }

        Result(Reference<?> reference, long instanceId, String gcRootKind, Iterable<? extends InstanceReference> shortestGCRootPath,
                        long gcRootThreadId, Iterable<? extends StackTraceElement> gcRootStackTrace, Path heapDumpFile) {
            this.collected = false;
            this.reference = reference;
            this.instanceId = instanceId;
            this.gcRootKind = gcRootKind;
            this.shortestGCRootPath = shortestGCRootPath;
            this.gcRootThreadId = gcRootThreadId;
            this.gcRootStackTrace = gcRootStackTrace;
            this.heapDumpFile = heapDumpFile;
        }

        boolean isCollected() {
            return collected;
        }

        Reference<?> getReference() {
            if (collected) {
                throw new IllegalStateException("Object was collected");
            }
            return reference;
        }

        long getInstanceId() {
            if (collected) {
                throw new IllegalStateException("Object was collected");
            }
            return instanceId;
        }

        String getGCRootKind() {
            if (collected) {
                throw new IllegalStateException("Object was collected");
            }
            return gcRootKind;
        }

        Iterable<? extends InstanceReference> getShortestGCRootPath() {
            if (collected) {
                throw new IllegalStateException("Object was collected");
            }
            return shortestGCRootPath;
        }

        long getGcRootThreadId() {
            if (collected) {
                throw new IllegalStateException("Object was collected");
            }
            return gcRootThreadId;
        }

        Iterable<? extends StackTraceElement> getGcRootStackTrace() {
            if (collected) {
                throw new IllegalStateException("Object was collected");
            }
            return gcRootStackTrace;
        }

        Path getHeapDumpFile() {
            if (collected) {
                throw new IllegalStateException("Object was collected");
            }
            return heapDumpFile;
        }
    }

    private abstract static class ReachabilityAnalyser<T> {

        final Result allCollected(Collection<? extends Reference<?>> references, boolean force, boolean collectGCRootPath,
                        boolean preserveHeapDumpIfNonCollectable) {
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
                return Result.COLLECTED;
            };
            return analyse(references, force, collectGCRootPath, preserveHeapDumpIfNonCollectable, test);
        }

        final Result anyCollected(Collection<? extends Reference<?>> references, boolean force, boolean collectGCRootPath,
                        boolean preserveHeapDumpIfNonCollectable) {
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
            return analyse(references, force, collectGCRootPath, preserveHeapDumpIfNonCollectable, test);
        }

        abstract Result analyse(Collection<? extends Reference<?>> references, boolean force, boolean collectGCRootPath,
                        boolean preserveHeapDumpIfNonCollectable, Function<T, Result> testCollected);

        abstract Result isCollected(Reference<?> reference, T context);
    }

    private static final class HeapDumpAnalyser extends ReachabilityAnalyser<HeapDumpAnalyser.State> {

        /**
         * The size of the buffer used for copying heap dump files is set at 16KB. This size is
         * chosen to accommodate the variability in file system block sizes, which can range from
         * 4KB to 16KB. The upper bound of 16KB is selected, considering that heap dumps are
         * frequently large in size.
         */
        private static final int BLOCK_SIZE = 16384;

        private static volatile HotSpotDiagnosticMXBean hotSpotDiagnosticMBean;
        private static volatile Reference<?>[] todo;

        private static class State {
            final boolean collectGCRootPath;
            final Path heapDumpFile;
            final Heap heap;
            final List<Instance> todoInstances;
            final Map<Reference<?>, Integer> todoIndexes;

            State(boolean collectGCRootPath, Path heapDumpFile, Heap heap,
                            List<Instance> todoInstances, Map<Reference<?>, Integer> todoIndexes) {
                this.collectGCRootPath = collectGCRootPath;
                this.heapDumpFile = heapDumpFile;
                this.heap = heap;
                this.todoInstances = todoInstances;
                this.todoIndexes = todoIndexes;
            }
        }

        @Override
        Result analyse(Collection<? extends Reference<?>> references, boolean force, boolean collectGCRootPath, boolean preserveHeapDumpIfNonCollectable,
                        Function<State, Result> testCollected) {
            try {
                Result result = null;
                Path tmpDirectory = Files.createTempDirectory(GCUtils.class.getSimpleName().toLowerCase());
                Path heapDumpFile = tmpDirectory.resolve("heapdump.hprof");
                Path targetFile = null;
                boolean copyHeapDump = false;
                try {
                    System.gc();    // Perform GC to minimize heap size and speed up heap queries.
                    Map<Reference<?>, Integer> todoIndexes = prepareTodoAndTakeHeapDump(references, heapDumpFile);
                    Heap heap = HeapFactory.createHeap(heapDumpFile.toFile());
                    JavaClass trackableReferenceClass = heap.getJavaClassByName(HeapDumpAnalyser.class.getName());
                    ObjectArrayInstance todoArray = (ObjectArrayInstance) trackableReferenceClass.getValueOfStaticField("todo");
                    List<Instance> instances = todoArray.getValues();
                    if (preserveHeapDumpIfNonCollectable) {
                        if (SAVE_HEAP_DUMP_TO != null) {
                            Path targetFolder = Path.of(SAVE_HEAP_DUMP_TO);
                            targetFile = Files.createTempFile(targetFolder, "gcutils_heapdump_", ".hprof.gz");
                            copyHeapDump = true;
                        } else {
                            targetFile = heapDumpFile;
                        }
                    }
                    result = testCollected.apply(new State(collectGCRootPath, targetFile, heap, instances, todoIndexes));
                } finally {
                    if (targetFile == null) {
                        delete(tmpDirectory);
                    } else if (result == null || result.isCollected()) {
                        delete(targetFile);
                        delete(tmpDirectory);
                    } else if (copyHeapDump) {
                        compress(heapDumpFile, targetFile);
                        delete(tmpDirectory);
                    }
                }
                return result;
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        private static void compress(Path src, Path target) throws IOException {
            try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(src));
                            GZIPOutputStream out = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(target)))) {
                byte[] buffer = new byte[BLOCK_SIZE];
                while (true) {
                    int count = in.read(buffer, 0, buffer.length);
                    if (count < 0) {
                        break;
                    }
                    out.write(buffer, 0, count);
                }
            }
        }

        @Override
        Result isCollected(Reference<?> reference, State context) {
            int index = context.todoIndexes.get(reference);
            Instance referenceInstance = context.todoInstances.get(index);
            Instance referent = (Instance) referenceInstance.getValueOfField("referent");
            if (referent == null) {
                return Result.COLLECTED;
            } else {
                String gcRootKind = null;
                List<InstanceReference> gcRootPath = null;
                long gcRootThreadId = -1;
                Iterable<? extends StackTraceElement> gcRootStackTrace = null;
                if (context.collectGCRootPath) {
                    gcRootPath = new ArrayList<>();
                    GCRoot gcRoot = collectGCRootPath(gcRootPath, context.heap, referent, null);
                    if (gcRoot == null) {
                        return Result.COLLECTED;
                    }
                    gcRootKind = gcRoot.getKind();
                    if (GCRoot.JAVA_FRAME.equals(gcRootKind)) {
                        JavaFrameGCRoot frameGCRoot = (JavaFrameGCRoot) gcRoot;
                        ThreadObjectGCRoot threadObjectGCRoot = frameGCRoot.getThreadGCRoot();
                        gcRootStackTrace = stackUpToAllocationFrame(threadObjectGCRoot, frameGCRoot.getFrameNumber());
                        gcRootThreadId = getThreadId(threadObjectGCRoot);
                    } else if (GCRoot.JNI_LOCAL.equals(gcRootKind)) {
                        JniLocalGCRoot jniLocalGCRoot = (JniLocalGCRoot) gcRoot;
                        ThreadObjectGCRoot threadObjectGCRoot = jniLocalGCRoot.getThreadGCRoot();
                        gcRootStackTrace = stackUpToAllocationFrame(threadObjectGCRoot, jniLocalGCRoot.getFrameNumber());
                        gcRootThreadId = getThreadId(threadObjectGCRoot);
                    }
                }
                return new Result(reference, referent.getInstanceId(), gcRootKind, gcRootPath, gcRootThreadId, gcRootStackTrace,
                                context.heapDumpFile != null ? context.heapDumpFile : null);
            }
        }

        private GCRoot collectGCRootPath(List<InstanceReference> gcRootPath, Heap heap, Instance instance, Instance prev) {
            if (instance == null) {
                return null;
            }
            String className = instance.getJavaClass().getName();
            if (Class.class.getName().equals(className)) {
                className = heap.getJavaClassByID(instance.getInstanceId()).getName();
            }
            ReferenceKind referenceKind;
            String memberName;
            if (prev == null) {
                referenceKind = ReferenceKind.OBJECT_FIELD;
                memberName = "this";
            } else {
                Value value = prev.getReferences().get(0);
                assert value.getDefiningInstance().getInstanceId() == instance.getInstanceId() : String.format("Expected reference from %s(0x%x), but got reference from %s(0x%x).",
                                instance.getJavaClass().getName(), instance.getInstanceId(), value.getDefiningInstance().getJavaClass().getName(), value.getDefiningInstance().getInstanceId());
                if (value instanceof ObjectFieldValue) {
                    ObjectFieldValue objectFieldValue = (ObjectFieldValue) value;
                    referenceKind = ReferenceKind.OBJECT_FIELD;
                    memberName = objectFieldValue.getField().getName();
                } else if (value instanceof ArrayItemValue) {
                    ArrayItemValue arrayItemValue = (ArrayItemValue) value;
                    referenceKind = ReferenceKind.ARRAY_ELEMENT;
                    memberName = String.valueOf(arrayItemValue.getIndex());
                } else {
                    throw new IllegalArgumentException("Unknown reference value: " + value);
                }
            }
            gcRootPath.add(new InstanceReference(referenceKind, className, memberName));
            if (instance.isGCRoot()) {
                return heap.getGCRoots(instance).iterator().next();
            }
            return collectGCRootPath(gcRootPath, heap, instance.getNearestGCRootPointer(), instance);
        }

        private static Iterable<? extends StackTraceElement> stackUpToAllocationFrame(ThreadObjectGCRoot threadObject, int frameNumber) {
            StackTraceElement[] stack = threadObject.getStackTrace();
            return Arrays.asList(Arrays.copyOfRange(stack, frameNumber, stack.length));
        }

        private static long getThreadId(ThreadObjectGCRoot threadObjectGCRoot) {
            return (long) threadObjectGCRoot.getInstance().getValueOfField("tid");
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
                VMRuntime.dumpHeap(outputFile.toString(), true);
            } else {
                getHotSpotDiagnosticMBean().dumpHeap(outputFile.toString(), true);
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
        Result analyse(Collection<? extends Reference<?>> references, boolean performAllocations, boolean collectGCRootPath,
                        boolean preserveHeapDumpIfNonCollectable, Function<Void, Result> testCollected) {
            int blockSize = 100_000;
            final List<byte[]> blocks = new ArrayList<>();
            Result result = null;
            for (int i = 0; i < 50; i++) {
                result = testCollected.apply(null);
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
            return result;
        }

        @Override
        Result isCollected(Reference<?> reference, Void context) {
            return reference.get() == null ? Result.COLLECTED : new Result(reference);
        }
    }
}
