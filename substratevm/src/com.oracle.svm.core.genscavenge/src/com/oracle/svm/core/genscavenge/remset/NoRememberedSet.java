package com.oracle.svm.core.genscavenge.remset;

import org.graalvm.compiler.nodes.gc.BarrierSet;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.GreyToBlackObjectVisitor;
import com.oracle.svm.core.genscavenge.Space;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.genscavenge.graal.SubstrateNoBarrierSet;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.MetaAccessProvider;

public final class NoRememberedSet implements RememberedSet {
    @Override
    public BarrierSet createBarrierSet(MetaAccessProvider metaAccess) {
        return new SubstrateNoBarrierSet();
    }

    @Override
    public UnsignedWord getHeaderSizeOfAlignedChunk() {
        UnsignedWord headerSize = WordFactory.unsigned(SizeOf.get(AlignedHeader.class));
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(headerSize, alignment);
    }

    @Override
    public UnsignedWord getHeaderSizeOfUnalignedChunk() {
        UnsignedWord headerSize = WordFactory.unsigned(SizeOf.get(UnalignedHeader.class));
        UnsignedWord alignment = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getAlignment());
        return UnsignedUtils.roundUp(headerSize, alignment);
    }

    @Override
    public void enableRememberedSetForObject(AlignedHeader chunk, Object obj) {
        // Nothing to do.
    }

    @Override
    public void enableRememberedSetForChunk(AlignedHeader chunk) {
        // Nothing to do.
    }

    @Override
    public void enableRememberedSetForChunk(UnalignedHeader chunk) {
        // Nothing to do.
    }

    @Override
    @AlwaysInline("GC performance")
    public boolean hasRememberedSet(UnsignedWord header) {
        return false;
    }

    @Override
    public void dirtyCardForAlignedObject(Object object, boolean verifyOnly) {
        throw VMError.shouldNotReachHere();
    }

    @Override
    public void dirtyCardForUnalignedObject(Object object, boolean verifyOnly) {
        throw VMError.shouldNotReachHere();
    }

    @Override
    @AlwaysInline("GC performance")
    public void dirtyCardIfNecessary(Object holderObject, Object object) {
        // Nothing to do.
    }

    @Override
    public void initializeChunk(AlignedHeader chunk) {
        // Nothing to do.
    }

    @Override
    public void initializeChunk(UnalignedHeader chunk) {
        // Nothing to do.
    }

    @Override
    public void resetChunk(AlignedHeader chunk) {
        // Nothing to do.
    }

    @Override
    public void cleanCardTable(Space space) {
        // Nothing to do.
    }

    @Override
    public boolean walkDirtyObjects(AlignedHeader chunk, GreyToBlackObjectVisitor visitor, boolean clean) {
        throw VMError.shouldNotReachHere();
    }

    @Override
    public boolean walkDirtyObjects(UnalignedHeader chunk, GreyToBlackObjectVisitor visitor, boolean clean) {
        throw VMError.shouldNotReachHere();
    }

    @Override
    public boolean walkDirtyObjects(Space space, GreyToBlackObjectVisitor visitor, boolean clean) {
        throw VMError.shouldNotReachHere();
    }

    @Override
    public boolean verify(AlignedHeader firstAlignedHeapChunk) {
        return true;
    }

    @Override
    public boolean verify(UnalignedHeader firstUnalignedHeapChunk) {
        return true;
    }
}
