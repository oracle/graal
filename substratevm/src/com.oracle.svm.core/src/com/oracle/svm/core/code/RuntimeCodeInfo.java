/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.code;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.PinnedArrays;
import com.oracle.svm.core.c.PinnedObjectArray;
import com.oracle.svm.core.c.UnmanagedReferenceWalkers;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectReferenceWalker;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.log.StringBuilderLog;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.Counter;
import com.oracle.svm.core.util.RingBuffer;

public class RuntimeCodeInfo {

    public static class Options {
        @Option(help = "Print logging information for runtime code cache modifications")//
        public static final RuntimeOptionKey<Boolean> TraceCodeCache = new RuntimeOptionKey<>(false);
    }

    private final RuntimeCodeInfoAccessor accessor = new RuntimeCodeInfoAccessor(this);

    private final RingBuffer<String> recentCodeCacheOperations = new RingBuffer<>();
    private long codeCacheOperationSequenceNumber;

    private final Counter.Group counters = new Counter.Group(CodeInfoTable.Options.CodeCacheCounters, "RuntimeCodeInfo");
    private final Counter lookupMethodCount = new Counter(counters, "lookupMethod", "");
    private final Counter addMethodCount = new Counter(counters, "addMethod", "");
    private final Counter invalidateMethodCount = new Counter(counters, "invalidateMethod", "");

    static final String INFO_ADD = "Add";
    static final String INFO_INVALIDATE = "Invalidate";

    private static final int INITIAL_TABLE_SIZE = 100;

    private PinnedObjectArray<RuntimeMethodInfo> methodInfos;
    private int numMethods;

    @Platforms(Platform.HOSTED_ONLY.class)
    public RuntimeCodeInfo() {
    }

    public RuntimeCodeInfoAccessor getAccessor() {
        return accessor;
    }

    /** Tear down the heap, return all allocated virtual memory chunks to VirtualMemoryProvider. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final void tearDown() {
        for (int i = 0; i < numMethods; i++) {
            PinnedArrays.getObject(methodInfos, i).freeInstalledCode();
        }
        PinnedArrays.releaseUnmanagedArray(methodInfos);
    }

    protected RuntimeMethodInfo lookupMethod(CodePointer ip) {
        lookupMethodCount.inc();
        return lookupMethodUninterruptible(ip);
    }

    /**
     * Looking up a method is lock-free: it is called frequently during stack walking, so locking or
     * even a {@link VMOperation} would be too slow. The lookup must access the {@link #methodInfos}
     * array, which is modified non-atomically when adding or removing methods. All modifications
     * are done from within a {@link VMOperation}. Making this method {@link Uninterruptible}
     * ensures that we see one consistent snapshot of the array, without the possibility for a
     * concurrent modification.
     */
    @Uninterruptible(reason = "methodInfos is accessed without holding a lock, so must not be interrupted by a safepoint that can add/remove code")
    private RuntimeMethodInfo lookupMethodUninterruptible(CodePointer ip) {
        assert verifyTable();
        if (numMethods == 0) {
            return null;
        }

        int idx = binarySearch(methodInfos, 0, numMethods, ip);
        if (idx >= 0) {
            /* Exact hit, ip is the begin of the method. */
            return PinnedArrays.getObject(methodInfos, idx);
        }

        int insertionPoint = -idx - 1;
        if (insertionPoint == 0) {
            /* ip is below the first method, so no hit. */
            assert ((UnsignedWord) ip).belowThan((UnsignedWord) PinnedArrays.getObject(methodInfos, 0).getCodeStart());
            return null;
        }

        RuntimeMethodInfo methodInfo = PinnedArrays.getObject(methodInfos, insertionPoint - 1);
        assert ((UnsignedWord) ip).aboveThan((UnsignedWord) methodInfo.getCodeStart());
        if (((UnsignedWord) ip).subtract((UnsignedWord) methodInfo.getCodeStart()).aboveOrEqual(methodInfo.getCodeSize())) {
            /* ip is not within the range of a method. */
            return null;
        }

        return methodInfo;
    }

    /* Copied and adapted from Arrays.binarySearch. */
    @Uninterruptible(reason = "called from uninterruptible code")
    private static int binarySearch(PinnedObjectArray<RuntimeMethodInfo> a, int fromIndex, int toIndex, CodePointer key) {
        int low = fromIndex;
        int high = toIndex - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            CodePointer midVal = PinnedArrays.getObject(a, mid).getCodeStart();

            if (((UnsignedWord) midVal).belowThan((UnsignedWord) key)) {
                low = mid + 1;
            } else if (((UnsignedWord) midVal).aboveThan((UnsignedWord) key)) {
                high = mid - 1;
            } else {
                return mid; // key found
            }
        }
        return -(low + 1);  // key not found.
    }

    public void addMethod(RuntimeMethodInfo methodInfo) {
        VMOperation.enqueueBlockingSafepoint("AddMethod", () -> {
            InstalledCodeObserverSupport.activateObservers(methodInfo.codeObserverHandles);
            long num = logMethodOperation(methodInfo, INFO_ADD);
            addMethodOperation(methodInfo);
            logMethodOperationEnd(num);
        });
    }

    private void addMethodOperation(RuntimeMethodInfo methodInfo) {
        VMOperation.guaranteeInProgress("Modifying code tables that are used by the GC");
        addMethodCount.inc();
        assert verifyTable();
        if (Options.TraceCodeCache.getValue()) {
            Log.log().string("[" + INFO_ADD + " method: ");
            logMethod(Log.log(), methodInfo);
            Log.log().string("]").newline();
        }

        if (methodInfos.isNull() || numMethods >= PinnedArrays.lengthOf(methodInfos)) {
            enlargeTable();
            assert verifyTable();
        }
        assert numMethods < PinnedArrays.lengthOf(methodInfos);

        int idx = binarySearch(methodInfos, 0, numMethods, methodInfo.getCodeStart());
        assert idx < 0 : "must not find code already in table";
        int insertionPoint = -idx - 1;
        PinnedArrays.arraycopy(methodInfos, insertionPoint, methodInfos, insertionPoint + 1, numMethods - insertionPoint);
        numMethods++;
        PinnedArrays.setObject(methodInfos, insertionPoint, methodInfo);

        if (Options.TraceCodeCache.getValue()) {
            logTable();
        }
        assert verifyTable();
    }

    @Uninterruptible(reason = "Prevent garbage collection before new table is published.")
    private void enlargeTable() {
        int newTableSize = numMethods * 2;
        if (newTableSize < INITIAL_TABLE_SIZE) {
            newTableSize = INITIAL_TABLE_SIZE;
        }
        PinnedObjectArray<RuntimeMethodInfo> newMethodInfos = PinnedArrays.createObjectArray(newTableSize);
        if (methodInfos.isNonNull()) {
            PinnedArrays.arraycopy(methodInfos, 0, newMethodInfos, 0, PinnedArrays.lengthOf(methodInfos));
            PinnedArrays.releaseUnmanagedArray(methodInfos);
        }
        methodInfos = newMethodInfos;
    }

    protected void invalidateMethod(RuntimeMethodInfo methodInfo) {
        VMOperation.guaranteeInProgress("Modifying code tables that are used by the GC");
        invalidateMethodCount.inc();
        assert verifyTable();
        if (Options.TraceCodeCache.getValue()) {
            Log.log().string("[" + INFO_INVALIDATE + " method: ");
            logMethod(Log.log(), methodInfo);
            Log.log().string("]").newline();
        }

        SubstrateInstalledCode installedCode = methodInfo.installedCode.get();
        if (installedCode != null) {
            assert !installedCode.isValid() || methodInfo.getCodeStart().rawValue() == installedCode.getAddress();
            /*
             * Until this point, the InstalledCode is valid. It can be invoked, and frames can be on
             * the stack. All the metadata must be valid until this point. Make it non-entrant,
             * i.e., ensure it cannot be invoked any more.
             */
            installedCode.clearAddress();
        }

        InstalledCodeObserverSupport.removeObservers(methodInfo.codeObserverHandles);

        /*
         * Deoptimize all invocations that are on the stack. This performs a stack walk, so all
         * metadata must be intact (even though the method was already marked as non-invokable).
         */
        Deoptimizer.deoptimizeInRange(methodInfo.getCodeStart(), methodInfo.getCodeEnd(), false);
        /*
         * Now it is guaranteed that the InstalledCode is not on the stack and cannot be invoked
         * anymore, so we can free the code and all metadata.
         */

        /* Remove methodInfo entry from our table. */
        int idx = binarySearch(methodInfos, 0, numMethods, methodInfo.getCodeStart());
        assert idx >= 0 : "methodInfo must be in table";
        PinnedArrays.arraycopy(methodInfos, idx + 1, methodInfos, idx, numMethods - (idx + 1));
        numMethods--;
        PinnedArrays.setObject(methodInfos, numMethods, null);

        Heap.getHeap().getGC().unregisterObjectReferenceWalker(methodInfo.constantsWalker);
        UnmanagedReferenceWalkers.singleton().unregister(RuntimeMethodInfo.walkReferencesFunction.getFunctionPointer(), Word.objectToUntrackedPointer(methodInfo));
        methodInfo.releaseArrays();

        /*
         * The arrays are in a pinned chunk that probably still contains metadata for other methods
         * that are still alive. So even though we release our allocator, the arrays are not garbage
         * collected any time soon. By clearing the object arrays, we make sure that we do not keep
         * objects in the regular unpinned heap alive.
         */
// Arrays.fill(methodInfo.frameInfoObjectConstants, null);
// if (methodInfo.frameInfoSourceClasses != null) {
// Arrays.fill(methodInfo.frameInfoSourceClasses, null);
// }
// if (methodInfo.frameInfoSourceMethodNames != null) {
// Arrays.fill(methodInfo.frameInfoSourceMethodNames, null);
// }
// if (methodInfo.frameInfoNames != null) {
// Arrays.fill(methodInfo.frameInfoNames, null);
// }

        methodInfo.allocator.release();
        methodInfo.freeInstalledCode();

        if (Options.TraceCodeCache.getValue()) {
            logTable();
        }
        assert verifyTable();
    }

    @Uninterruptible(reason = "called from uninterruptible code")
    private boolean verifyTable() {
        if (methodInfos.isNull()) {
            assert numMethods == 0 : "a1";
            return true;
        }

        assert numMethods <= PinnedArrays.lengthOf(methodInfos) : "a11";

        for (int i = 0; i < numMethods; i++) {
            RuntimeMethodInfo methodInfo = PinnedArrays.getObject(methodInfos, i);
            assert methodInfo != null : "a20";
            assert i == 0 || ((UnsignedWord) PinnedArrays.getObject(methodInfos, i - 1).getCodeStart())
                            .belowThan((UnsignedWord) PinnedArrays.getObject(methodInfos, i).getCodeStart()) : "a22";
            assert i == 0 || ((UnsignedWord) PinnedArrays.getObject(methodInfos, i - 1).getCodeEnd()).belowOrEqual((UnsignedWord) methodInfo.getCodeStart()) : "a23";
        }

        for (int i = numMethods; i < PinnedArrays.lengthOf(methodInfos); i++) {
            assert PinnedArrays.getObject(methodInfos, i) == null : "a31";
        }
        return true;
    }

    public void logTable() {
        logTable(Log.log());
    }

    public void logRecentOperations(Log log) {
        log.string("== [Recent RuntimeCodeCache operations: ");
        recentCodeCacheOperations.foreach((context, entry) -> {
            Log.log().newline().string(entry);
        });
        log.string("]").newline();
    }

    public void logTable(Log log) {
        log.string("== [RuntimeCodeCache: ").signed(numMethods).string(" methods");
        for (int i = 0; i < numMethods; i++) {
            log.newline().hex(PinnedArrays.getObject(methodInfos, i).getCodeStart()).string("  ");
            logMethod(log, PinnedArrays.getObject(methodInfos, i));
        }
        log.string("]").newline();
    }

    private static void logMethod(Log log, RuntimeMethodInfo methodInfo) {
        log.string(methodInfo.name);
        log.string("  ip: ").hex(methodInfo.getCodeStart()).string(" - ").hex(methodInfo.getCodeEnd());
        log.string("  size: ").unsigned(methodInfo.getCodeSize());
        /*
         * Note that we are not trying to output methodInfo.installedCode. It is not a pinned
         * object, so when log printing (for, e.g., a fatal error) occurs during a GC, then the VM
         * could segfault.
         */
    }

    long logMethodOperation(RuntimeMethodInfo methodInfo, String kind) {
        long current = ++codeCacheOperationSequenceNumber;
        StringBuilderLog log = new StringBuilderLog();
        log.string(kind).string(": ");
        logMethod(log, methodInfo);
        log.string(" ").unsigned(current).string(":{");
        recentCodeCacheOperations.append(log.getResult());
        return current;
    }

    void logMethodOperationEnd(long operationNumber) {
        StringBuilderLog log = new StringBuilderLog();
        log.string("}:").unsigned(operationNumber);
        recentCodeCacheOperations.append(log.getResult());
    }

    public boolean walkRuntimeMethods(MemoryWalker.Visitor visitor) {
        VMOperation.guaranteeInProgress("Modifying code tables that are used by the GC");
        boolean continueVisiting = true;
        for (int i = 0; (continueVisiting && (i < numMethods)); i += 1) {
            continueVisiting = visitor.visitRuntimeCompiledMethod(PinnedArrays.getObject(methodInfos, i),
                            ImageSingletons.lookup(RuntimeCodeInfo.MemoryWalkerAccessImpl.class));
        }
        return continueVisiting;
    }

    final class RuntimeCodeInfoReferenceWalker extends ObjectReferenceWalker {
        @Override
        public boolean walk(ObjectReferenceVisitor referenceVisitor) {
            if (methodInfos.isNonNull()) {
                PinnedArrays.walkUnmanagedObjectArray(methodInfos, referenceVisitor);
            }
            return true;
        }
    }

    /** Methods for a MemoryWalker to access runtime compiled code. */
    public static final class MemoryWalkerAccessImpl implements MemoryWalker.RuntimeCompiledMethodAccess<RuntimeMethodInfo> {

        /** A private constructor used only to make up the singleton instance. */
        @Platforms(Platform.HOSTED_ONLY.class)
        protected MemoryWalkerAccessImpl() {
            super();
        }

        /*
         * Methods on VisitableRuntimeMethod.
         *
         * These take a VisitableRuntimeMethod as a parameter and cast it to a RuntimeMethodInfo to
         * get access to the implementation.
         *
         * These return Unsigned instead of Pointer, to protect the implementation.
         */

        @Override
        public UnsignedWord getStart(RuntimeMethodInfo runtimeMethod) {
            return (UnsignedWord) runtimeMethod.getCodeStart();
        }

        @Override
        public UnsignedWord getSize(RuntimeMethodInfo runtimeMethod) {
            return runtimeMethod.getCodeSize();
        }

        @Override
        public String getName(RuntimeMethodInfo runtimeMethod) {
            return runtimeMethod.getName();
        }
    }
}

@AutomaticFeature
class RuntimeCodeInfoWalkersFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(RuntimeCodeInfo.MemoryWalkerAccessImpl.class, new RuntimeCodeInfo.MemoryWalkerAccessImpl());
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        Heap.getHeap().getGC().registerObjectReferenceWalker(CodeInfoTable.getRuntimeCodeCache().new RuntimeCodeInfoReferenceWalker());
    }
}
