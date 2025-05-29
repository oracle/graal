/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.gc;

import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;

import java.lang.ref.Reference;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.HeapVerifier;
import com.oracle.svm.core.heap.GC;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.OutOfMemoryUtil;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.ReferenceInternals;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.NativeVMOperation;
import com.oracle.svm.core.thread.NativeVMOperationData;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.webimage.wasm.stack.WebImageWasmStackFrameVisitor;
import com.oracle.svm.hosted.webimage.wasm.stack.WebImageWasmStackWalker;
import com.oracle.svm.webimage.platform.WebImageWasmLMPlatform;
import com.oracle.svm.webimage.wasm.code.WasmSimpleCodeInfoQueryResult;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.word.Word;

/**
 * Simple mark-sweep garbage collector using tri-coloring for the WasmLM backend. Objects have one
 * of three colors (part of their {@linkplain WasmObjectHeader object header}):
 * <ul>
 * <li>White: The object is not referenced by black objects</li>
 * <li>Gray: The object is reachable from a GC-root but its references have not been processed</li>
 * <li>Black: The object is reachable from a GC-root and non of its references are white</li>
 * </ul>
 *
 * The GC-roots are all objects referenced in the image heap or the shadow stack.
 * <p>
 * The collection proceeds as follows:
 * <ol>
 * <li>The collection starts out with all objects in the collected heap as white</li>
 * <li>All GC-root objects are marked gray and visited by {@link GrayToBlackObjectVisitor} to mark
 * them and their transitive references black (see {@link #blackenRoots()})</li>
 * <li>If there are still gray objects (due to the limited size stack), they are again processed by
 * the {@link GrayToBlackObjectVisitor} until there are no remaining gray objects. (see
 * {@link #blackenCollectedHeap()})</li>
 * <li>Now, each object in the collected heap are either black or white, all white objects can be
 * freed since they are not reachable from any of the GC-roots (see {@link #releaseSpace()})</li>
 * </ol>
 *
 * Objects in the image heap get special treatment since they cannot be freed. At the start of the
 * collection, they are implicitly gray and once all objects in the image heap are scanned, they are
 * implicitly black.
 * <p>
 *
 * Collection is triggered in any of the following ways:
 *
 * <ul>
 * <li>By the user (e.g. {@code System.gc()})</li>
 * <li>Always before allocations if {@link Options#GCStressTest} is enabled</li>
 * <li>During an allocation if the allocator cannot find a suitable block of memory. (see
 * {@link WasmAllocation#doMalloc})</li>
 * </ul>
 *
 * After collection, the heap is grown by a factor of {@link Options#HeapGrowthFactor} if the size
 * of objects exceeds {@link Options#GrowthTriggerThreshold} percent of the total heap size.
 */
public class WasmLMGC implements GC {

    public static class Options {
        @Option(help = "Run GC at every possible opportunity", type = OptionType.Debug)//
        public static final HostedOptionKey<Boolean> GCStressTest = new HostedOptionKey<>(false);

        @Option(help = "Verify all object references if VerifyHeap is enabled.", type = OptionType.Debug)//
        public static final HostedOptionKey<Boolean> WasmVerifyReferences = new HostedOptionKey<>(true);

        @Option(help = "When requesting more heap memory, the heap is grown by this factor.", type = OptionType.User)//
        public static final HostedOptionKey<Double> HeapGrowthFactor = new HostedOptionKey<>(1.8) {
            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Double oldValue, Double newValue) {
                super.onValueUpdate(values, oldValue, newValue);
                UserError.guarantee(newValue > 1.0, "%s must be larger than 1", getName());
            }
        };

        @Option(help = "If after a collection the allocated memory exceeds this percentage of the heap size, more heap memory is requested.", type = OptionType.User)//
        public static final HostedOptionKey<Integer> GrowthTriggerThreshold = new HostedOptionKey<>(50) {
            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Integer oldValue, Integer newValue) {
                super.onValueUpdate(values, oldValue, newValue);
                UserError.guarantee(0 <= newValue && newValue <= 100, "%s must be in [0, 100]", getName());
            }
        };
    }

    private final GrayToBlackObjectVisitor grayToBlackObjectVisitor = new GrayToBlackObjectVisitor();

    private final BlackenImageHeapRootsVisitor blackenImageHeapRootsVisitor = new BlackenImageHeapRootsVisitor(grayToBlackObjectVisitor);

    private final WebImageWasmStackFrameVisitor blackenStackRootsVisitor = new WebImageWasmStackFrameVisitor() {
        @Override
        public boolean visitFrame(Pointer sp, CodePointer ip) {
            WasmSimpleCodeInfoQueryResult queryResult = StackValue.get(WasmSimpleCodeInfoQueryResult.class);
            WebImageWasmStackWalker.getCodeInfo(ip, queryResult);

            for (int offset : queryResult.getOffsets()) {
                Object o = sp.readObject(offset);

                if (o != null && !WasmHeap.getHeapImpl().isInImageHeap(o)) {

                    // All objects reachable from the stack have to first be marked gray.
                    grayToBlackObjectVisitor.promoteToGray(o);
                    grayToBlackObjectVisitor.visitObject(o);
                }
            }

            return true;
        }
    };

    private final CollectionVMOperation collectOperation = new CollectionVMOperation();

    private final NoAllocationVerifier noAllocationVerifier = NoAllocationVerifier.factory("WasmLMGC.WasmLMGC()", false);

    private boolean collectionInProgress;

    @Fold
    public static WasmLMGC getGC() {
        WasmLMGC gcImpl = WasmHeap.getHeapImpl().getGC();
        assert gcImpl != null;
        return gcImpl;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean isCollectionInProgress() {
        return collectionInProgress;
    }

    private void startCollectionOrExit() {
        CollectionInProgressError.exitIf(collectionInProgress);
        collectionInProgress = true;
    }

    private void finishCollection() {
        assert collectionInProgress;
        collectionInProgress = false;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void collect(GCCause cause) {
        collect(cause, false);
    }

    @Override
    public void collectCompletely(GCCause cause) {
        collect(cause, true);
    }

    @Override
    public void collectionHint(boolean fullGC) {
        /* Ignore collection hints. */
    }

    @Override
    public String getName() {
        return "Wasm GC";
    }

    @Override
    public String getDefaultMaxHeapSize() {
        return "unknown";
    }

    public void maybeCollectOnAllocation() {
        boolean outOfMemory = false;
        if (shouldCollectOnAllocation()) {
            outOfMemory = collectWithoutAllocating(WasmGCCause.OnAllocation, false);
        }
        if (outOfMemory) {
            throw OutOfMemoryUtil.heapSizeExceeded();
        }
    }

    private static boolean shouldCollectOnAllocation() {
        return Options.GCStressTest.getValue();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void collect(GCCause cause, boolean forceFullGC) {
        boolean outOfMemory = collectWithoutAllocating(cause, forceFullGC);
        if (outOfMemory) {
            throwOutOfMemoryError();
        }
    }

    @Uninterruptible(reason = "Switch from uninterruptible to interruptible code.", calleeMustBe = false)
    private static void throwOutOfMemoryError() {
        throw OutOfMemoryUtil.heapSizeExceeded();
    }

    @Uninterruptible(reason = "Avoid races with other threads that also try to trigger a GC", calleeMustBe = false)
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in the implementation of garbage collection.")
    boolean collectWithoutAllocating(GCCause cause, boolean forceFullGC) {
        int size = SizeOf.get(CollectionVMOperationData.class);
        CollectionVMOperationData data = StackValue.get(size);
        UnmanagedMemoryUtil.fill((Pointer) data, Word.unsigned(size), (byte) 0);
        data.setNativeVMOperation(collectOperation);
        data.setCauseId(cause.getId());
        data.setRequestingEpoch(Word.zero());
        data.setRequestingNanoTime(System.nanoTime());
        data.setForceFullGC(forceFullGC);
        enqueueCollectOperation(data);
        return data.getOutOfMemory();
    }

    @Uninterruptible(reason = "Used as a transition between uninterruptible and interruptible code", calleeMustBe = false)
    private void enqueueCollectOperation(CollectionVMOperationData data) {
        collectOperation.enqueue(data);
    }

    @SuppressWarnings("try")
    private void collectOperation(CollectionVMOperationData data) {
        assert VMOperation.isGCInProgress() : "Collection should be a VMOperation.";
        startCollectionOrExit();

        /*
         * A try-with-resource block generates code that calls Throwable#addSUppressed (which may
         * allocate) on the exception edge of the generated 'close' call, so the
         * NoAllocationVerifier is closed explicitly in a finally block, which avoids this extra
         * exception handling.
         */
        NoAllocationVerifier nav = noAllocationVerifier.open();
        try {
            verifyGC(HeapVerifier.Occasion.Before);
            blackenRoots();
            blackenCollectedHeap();
            releaseSpace();

            if (WasmAllocation.getObjectPercentage() > Options.GrowthTriggerThreshold.getValue()) {
                long numBytes = (long) (WasmAllocation.getHeapSize() * (Options.HeapGrowthFactor.getValue() - 1.0));
                WasmAllocation.growAllocatorRegion(Word.unsigned(numBytes));
            }

            verifyGC(HeapVerifier.Occasion.After);
        } finally {
            nav.close();
        }

        finishCollection();

        data.setOutOfMemory(false);
    }

    private static void verifyGC(HeapVerifier.Occasion occasion) {
        if (!SubstrateGCOptions.VerifyHeap.getValue()) {
            return;
        }

        boolean success = true;
        success &= WasmHeapVerifier.verify();
        success &= WasmStackVerifier.verify();

        if (!success) {
            Log.log().string("Heap verification ").string(occasion.name()).string(" GC failed").newline();
            throw VMError.shouldNotReachHere("Heap verification failed");
        }
    }

    /**
     * After this, all objects referenced from GC roots (image heap and stack) are marked black and
     * all their references are at least marked gray.
     * <p>
     * This means that the gray objects in the collected heap can be used as roots to reach the
     * remaining reachable objects.
     */
    private void blackenRoots() {
        blackenImageHeapRoots();
        blackenStackRoots();
    }

    private void blackenImageHeapRoots() {
        WasmHeap.getHeapImpl().walkNativeImageHeapRegions(blackenImageHeapRootsVisitor);
    }

    @NeverInline("Starts a stack walk in the caller frame")
    private void blackenStackRoots() {
        WebImageWasmStackWalker.walkCurrentThread(KnownIntrinsics.readCallerStackPointer(), blackenStackRootsVisitor);
    }

    /**
     * If any gray objects remain, they are visited here.
     * <p>
     * This should only happen if the DFS tree of all objects reachable from some object is very
     * wide (exceeding the size of the fixed size stack).
     */
    private void blackenCollectedHeap() {
        while (grayToBlackObjectVisitor.hasGray()) {
            WasmHeap.getHeapImpl().walkCollectedHeapObjects(grayToBlackObjectVisitor);
        }
    }

    /**
     * Once all GC roots have been traversed, all white objects in the collected heap can be freed
     * since they are not reachable from any of the GC roots.
     */
    private static void releaseSpace() {
        WasmHeap.getHeapImpl().walkCollectedHeapObjects(o -> {
            if (WasmObjectHeader.isWhiteObject(o)) {
                WasmAllocation.logicalFree(Word.objectToUntrackedPointer(o));
            } else {
                VMError.guarantee(WasmObjectHeader.isBlackObject(o), "Found gray object after mark phase");
                WasmObjectHeader.markWhite(o);
            }
        });

        WasmAllocation.coalesce();
    }

    @SuppressWarnings("serial")
    static final class CollectionInProgressError extends Error {
        static void exitIf(boolean state) {
            if (state) {
                Log.log().string("[CollectionInProgressError]").newline();
                throw CollectionInProgressError.SINGLETON;
            }
        }

        private CollectionInProgressError() {
        }

        private static final CollectionInProgressError SINGLETON = new CollectionInProgressError();
    }

    private static class CollectionVMOperation extends NativeVMOperation {
        CollectionVMOperation() {
            super(VMOperationInfos.get(CollectionVMOperation.class, "Garbage collection", SystemEffect.SAFEPOINT));
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public boolean isGC() {
            return true;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while collecting")
        protected void operate(NativeVMOperationData data) {
            /*
             * Exceptions during collections are fatal. The heap is likely in an inconsistent state.
             * The GC must also be allocation free, i.e., we cannot allocate exception stack traces
             * while in the GC. This is bad for diagnosing errors in the GC. To improve the
             * situation a bit, we switch on the flag to make implicit exceptions such as
             * NullPointerExceptions fatal errors. This ensures that we fail early at the place
             * where the fatal error reporting can still dump the full stack trace.
             */
            ImplicitExceptions.activateImplicitExceptionsAreFatal();
            try {
                WasmLMGC.getGC().collectOperation((CollectionVMOperationData) data);
            } catch (Throwable t) {
                throw VMError.shouldNotReachHere(t);
            } finally {
                ImplicitExceptions.deactivateImplicitExceptionsAreFatal();
            }
        }

        @Override
        protected boolean hasWork(NativeVMOperationData data) {
            return true;
        }
    }

    @RawStructure
    private interface CollectionVMOperationData extends NativeVMOperationData {
        @RawField
        int getCauseId();

        @RawField
        void setCauseId(int value);

        @RawField
        UnsignedWord getRequestingEpoch();

        @RawField
        void setRequestingEpoch(UnsignedWord value);

        @RawField
        long getRequestingNanoTime();

        @RawField
        void setRequestingNanoTime(long value);

        @RawField
        boolean getForceFullGC();

        @RawField
        void setForceFullGC(boolean value);

        @RawField
        boolean getOutOfMemory();

        @RawField
        void setOutOfMemory(boolean value);
    }
}

final class BlackenImageHeapRootsVisitor implements MemoryWalker.ImageHeapRegionVisitor {

    private final ObjectVisitor visitor;

    BlackenImageHeapRootsVisitor(ObjectVisitor visitor) {
        this.visitor = visitor;
    }

    @Override
    public <T> void visitNativeImageHeapRegion(T region, MemoryWalker.NativeImageHeapRegionAccess<T> access) {
        if (access.isWritable(region)) {
            access.visitObjects(region, visitor);
        }
    }
}

/**
 * {@link ObjectVisitor} that promotes gray objects to black by promoting all their (transitive)
 * references to gray.
 * <p>
 * It also maintains a stack work list to walk transitive references in DFS-order, because the code
 * has to be allocation free, it uses a fixed size stack. If the stack is full, no further
 * references will be added; the references are still marked gray and will be processed once all
 * gray objects are visited again. This optimization greatly reduces how often all gray objects need
 * to be traversed.
 */
final class GrayToBlackObjectVisitor implements ObjectVisitor {

    /**
     * Keeps track of the number of gray objects in the collected heap.
     */
    private int numberOfGrayObjects = 0;

    private final GrayHeapVisitor grayReferenceVisitor = new GrayHeapVisitor();

    /**
     * Fixed size stack work list to visit transitively reachable objects in DFS-order.
     * <p>
     * The stack should be enough to completely process all reachable objects if the DFS tree width
     * of the starting object is less than the stack size.
     */
    private final SizedObjectStack worklist = new SizedObjectStack(128);

    public boolean hasGray() {
        return numberOfGrayObjects > 0;
    }

    @AlwaysInline("GC Performance")
    private void promoteToBlack(Object o) {
        if (!WasmHeap.getHeapImpl().isInImageHeap(o)) {
            assert WasmObjectHeader.isGrayObject(o) : "Tried to promote a non-gray object to black";
            WasmObjectHeader.markBlack(o);
            numberOfGrayObjects--;
        }
    }

    @AlwaysInline("GC Performance")
    boolean promoteToGray(Object o) {
        assert !WasmHeap.getHeapImpl().isInImageHeap(o) : "Tried to promote an image heap object to gray";
        if (WasmObjectHeader.isWhiteObject(o)) {
            numberOfGrayObjects++;
            WasmObjectHeader.markGray(o);
            return true;
        }

        return false;
    }

    /**
     * This visitor is used for visiting image heap and non image heap objects and image heap
     * objects are implicitly gray.
     */
    @AlwaysInline("GC Performance")
    private static boolean isGray(Object o) {
        if (WasmHeap.getHeapImpl().isInImageHeap(o)) {
            return true;
        }

        return WasmObjectHeader.isGrayObject(o);
    }

    /**
     * Tries to mark the given objects as well as all objects reachable from it as black.
     * <p>
     * Due to using a fixed-size stack, we may not be able to visit all reachable objects. In any
     * case, the method guarantees progress by at least marking the given object black and all its
     * direct references gray.
     */
    @Override
    @AlwaysInline("GC performance")
    public void visitObject(Object o) {
        if (!isGray(o)) {
            return;
        }

        assert worklist.isEmpty() : "Worklist must be empty before every visit";

        worklist.push(o);

        /*
         * Visit the object and all white objects reachable from it in DFS order.
         */
        while (!worklist.isEmpty()) {
            Object obj = worklist.pop();
            assert isGray(obj) : "Object in worklist is not gray";

            // Mark all references in the object gray
            if (probability(SLOW_PATH_PROBABILITY, KnownIntrinsics.readHub(obj).isReferenceInstanceClass())) {
                discoverReference(obj, grayReferenceVisitor);
            }
            InteriorObjRefWalker.walkObject(obj, grayReferenceVisitor);

            // The object no longer has any references to white objects, it can be marked black
            promoteToBlack(obj);

        }
    }

    /**
     * Apply the given visitor to the referent of the given {@link Reference}.
     *
     * @param obj Must be a {@link Reference}
     */
    @AlwaysInline("GC Performance")
    private static void discoverReference(Object obj, ObjectReferenceVisitor referenceVisitor) {
        Reference<?> dr = (Reference<?>) obj;
        Pointer referentAddr = ReferenceInternals.getReferentPointer(dr);

        if (referentAddr.isNull()) {
            // There is no referent yet.
            return;
        }

        if (Heap.getHeap().isInImageHeap(referentAddr)) {
            // Referents in the image heap cannot be moved or reclaimed, no need to look closer.
            return;
        }

        // Always mark referent as reachable (no support for soft or weak references yet)
        // TODO GR-43486 allow for soft and weak referenced referents to be potentially freed by the
        // GC
        int referenceSize = ConfigurationValues.getObjectLayout().getReferenceSize();
        referenceVisitor.visitObjectReferences(ReferenceInternals.getReferentFieldAddress(dr), false, referenceSize, dr, 1);
    }

    /**
     * Marks all visited references as gray.
     */
    private final class GrayHeapVisitor implements ObjectReferenceVisitor {
        @Override
        @AlwaysInline("GC performance")
        public void visitObjectReferences(Pointer firstObjRef, boolean compressed, int referenceSize, Object holderObject, int count) {
            Pointer pos = firstObjRef;
            Pointer end = firstObjRef.add(Word.unsigned(count).multiply(referenceSize));
            while (pos.belowThan(end)) {
                visitObjectReference(pos, compressed);
                pos = pos.add(referenceSize);
            }
        }

        public void visitObjectReference(Pointer objRef, boolean compressed) {
            assert !objRef.isNull() : "Tried to visit object references of null object";

            Pointer p = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
            if (p.isNull()) {
                return;
            }

            if (WasmHeap.getHeapImpl().isInImageHeap(p)) {
                return;
            }

            ObjectHeader oh = Heap.getHeap().getObjectHeader();
            UnsignedWord header = oh.readHeaderFromPointer(p);

            /*
             * Gray objects are already being processed and black objects have already finished
             * processing.
             */
            if (!WasmObjectHeader.isWhiteHeader(header)) {
                return;
            }

            Object obj = p.toObjectNonNull();
            if (promoteToGray(obj)) {
                /*
                 * Newly gray objects are pushed onto the worklist, if there is space. Otherwise,
                 * the object is still gray and will be processed in the next traversal of all gray
                 * objects.
                 */
                if (worklist.hasSpace()) {
                    worklist.push(obj);
                }
            }
        }
    }
}

/**
 * Fixed size stack for storing objects. The objects are stored as untracked pointers to prevent
 * them from affecting the GC.
 */
final class SizedObjectStack {

    public final int maxSize;

    private int currentSize = 0;

    private final Word[] stack;

    SizedObjectStack(int maxSize) {
        this.maxSize = maxSize;
        this.stack = new Word[maxSize];
    }

    @AlwaysInline("GC performance")
    public boolean isEmpty() {
        return size() == 0;
    }

    @AlwaysInline("GC performance")
    public boolean hasSpace() {
        return currentSize < maxSize;
    }

    @AlwaysInline("GC performance")
    public int size() {
        return currentSize;
    }

    @AlwaysInline("GC performance")
    public Object pop() {
        VMError.guarantee(!isEmpty(), "Tried to pop empty stack");
        currentSize--;
        return stack[currentSize].toObjectNonNull();
    }

    @AlwaysInline("GC performance")
    public void push(Object o) {
        VMError.guarantee(hasSpace(), "Tried to push onto full stack");
        assert o != null;
        stack[currentSize] = Word.objectToUntrackedPointer(o);
        currentSize++;
    }
}

@TargetClass(value = java.lang.Runtime.class)
@SuppressWarnings("static-method")
@Platforms(WebImageWasmLMPlatform.class)
final class Target_java_lang_Runtime {
    @Substitute
    private long freeMemory() {
        return maxMemory() - WasmHeap.getHeapImpl().getUsedBytes().rawValue();
    }

    @Substitute
    private long totalMemory() {
        return maxMemory();
    }

    @Substitute
    private long maxMemory() {
        return 1L << 32;
    }

    @Substitute
    private void gc() {
        WasmLMGC.getGC().collectCompletely(GCCause.JavaLangSystemGC);
    }

    @Substitute
    public int availableProcessors() {
        return 1;
    }
}
