package com.oracle.svm.core.genscavenge.remset;

import org.graalvm.compiler.nodes.gc.BarrierSet;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.GCImpl;
import com.oracle.svm.core.genscavenge.GreyToBlackObjectVisitor;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.genscavenge.HeapPolicy;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.Space;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.genscavenge.graal.SubstrateCardTableBarrierSet;
import com.oracle.svm.core.log.Log;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

public class CardTableBasedRememberedSet implements RememberedSet {
    @Platforms(Platform.HOSTED_ONLY.class)
    public CardTableBasedRememberedSet() {
    }

    @Override
    public BarrierSet createBarrierSet(MetaAccessProvider metaAccess) {
        ResolvedJavaType objectArrayType = metaAccess.lookupJavaType(Object[].class);
        return new SubstrateCardTableBarrierSet(objectArrayType);
    }

    @Override
    public UnsignedWord getHeaderSizeOfAlignedChunk() {
        return AlignedChunkRememberedSet.getHeaderSize();
    }

    @Override
    public UnsignedWord getHeaderSizeOfUnalignedChunk() {
        return UnalignedChunkRememberedSet.getHeaderSize();
    }

    @Override
    public void enableRememberedSetForObject(AlignedHeader chunk, Object obj) {
        AlignedChunkRememberedSet.enableRememberedSetForObject(chunk, obj);
    }

    @Override
    public void enableRememberedSetForChunk(AlignedHeader chunk) {
        AlignedChunkRememberedSet.enableRememberedSetForChunk(chunk);
    }

    @Override
    public void enableRememberedSetForChunk(UnalignedHeader chunk) {
        UnalignedChunkRememberedSet.enableRememberedSetForChunk(chunk);
    }

    @Override
    @AlwaysInline("GC performance")
    public boolean hasRememberedSet(UnsignedWord header) {
        return ObjectHeaderImpl.hasRememberedSet(header);
    }

    @Override
    @AlwaysInline("GC performance")
    public void dirtyCardForAlignedObject(Object object, boolean verifyOnly) {
        AlignedChunkRememberedSet.dirtyCardForObject(object, verifyOnly);
    }

    @Override
    @AlwaysInline("GC performance")
    public void dirtyCardForUnalignedObject(Object object, boolean verifyOnly) {
        UnalignedChunkRememberedSet.dirtyCardForObject(object, verifyOnly);
    }

    @Override
    @AlwaysInline("GC performance")
    public void dirtyCardIfNecessary(Object holderObject, Object object) {
        if (HeapPolicy.getMaxSurvivorSpaces() == 0 || holderObject == null || object == null || GCImpl.getGCImpl().isCompleteCollection() ||
                        !HeapImpl.getHeapImpl().getYoungGeneration().contains(object)) {
            return;
        }

        UnsignedWord objectHeader = ObjectHeaderImpl.readHeaderFromObject(holderObject);
        if (ObjectHeaderImpl.hasRememberedSet(objectHeader)) {
            if (ObjectHeaderImpl.isAlignedObject(holderObject)) {
                AlignedChunkRememberedSet.dirtyCardForObject(holderObject, false);
            } else {
                assert ObjectHeaderImpl.isUnalignedObject(holderObject) : "sanity";
                UnalignedChunkRememberedSet.dirtyCardForObject(holderObject, false);
            }
        }
    }

    @Override
    public void initializeChunk(AlignedHeader chunk) {
        AlignedChunkRememberedSet.initializeChunk(chunk);
    }

    @Override
    public void initializeChunk(UnalignedHeader chunk) {
        UnalignedChunkRememberedSet.initializeChunk(chunk);
    }

    @Override
    public void resetChunk(AlignedHeader chunk) {
        AlignedChunkRememberedSet.resetChunk(chunk);
    }

    @Override
    public void cleanCardTable(Space space) {
        AlignedHeapChunk.AlignedHeader aChunk = space.getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            AlignedChunkRememberedSet.cleanCardTable(aChunk);
            aChunk = HeapChunk.getNext(aChunk);
        }

        UnalignedHeapChunk.UnalignedHeader uChunk = space.getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            UnalignedChunkRememberedSet.cleanCardTable(uChunk);
            uChunk = HeapChunk.getNext(uChunk);
        }
    }

    @Override
    public boolean walkDirtyObjects(AlignedHeader chunk, GreyToBlackObjectVisitor visitor, boolean clean) {
        return AlignedChunkRememberedSet.walkDirtyObjects(chunk, visitor, clean);
    }

    @Override
    public boolean walkDirtyObjects(UnalignedHeader chunk, GreyToBlackObjectVisitor visitor, boolean clean) {
        return UnalignedChunkRememberedSet.walkDirtyObjects(chunk, visitor, clean);
    }

    @Override
    public boolean walkDirtyObjects(Space space, GreyToBlackObjectVisitor visitor, boolean clean) {
        AlignedHeader aChunk = space.getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            if (!walkDirtyObjects(aChunk, visitor, clean)) {
                Log failureLog = Log.log().string("[Space.walkDirtyObjects:");
                failureLog.string("  aChunk.walkDirtyObjects fails").string("]").newline();
                return false;
            }
            aChunk = HeapChunk.getNext(aChunk);
        }
        UnalignedHeader uChunk = space.getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            if (!walkDirtyObjects(uChunk, visitor, clean)) {
                Log failureLog = Log.log().string("[Space.walkDirtyObjects:");
                failureLog.string("  uChunk.walkDirtyObjects fails").string("]").newline();
                return false;
            }
            uChunk = HeapChunk.getNext(uChunk);
        }
        return true;
    }

    @Override
    public boolean verify(AlignedHeader firstAlignedHeapChunk) {
        boolean success = true;
        AlignedHeader aChunk = firstAlignedHeapChunk;
        while (aChunk.isNonNull()) {
            success &= AlignedChunkRememberedSet.verify(aChunk);
            aChunk = HeapChunk.getNext(aChunk);
        }
        return success;
    }

    @Override
    public boolean verify(UnalignedHeader firstUnalignedHeapChunk) {
        boolean success = true;
        UnalignedHeader uChunk = firstUnalignedHeapChunk;
        while (uChunk.isNonNull()) {
            success &= UnalignedChunkRememberedSet.verify(uChunk);
            uChunk = HeapChunk.getNext(uChunk);
        }
        return success;
    }
}
