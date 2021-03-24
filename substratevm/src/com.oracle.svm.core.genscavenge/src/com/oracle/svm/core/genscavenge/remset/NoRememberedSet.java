package com.oracle.svm.core.genscavenge.remset;

import java.util.List;

import org.graalvm.compiler.nodes.gc.BarrierSet;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
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
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.util.HostedByteBufferPointer;
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
    @Platforms(Platform.HOSTED_ONLY.class)
    public void enableRememberedSetForAlignedChunk(HostedByteBufferPointer chunk, int chunkPosition, List<ImageHeapObject> objects) {
        // Nothing to do.
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public void enableRememberedSetForUnalignedChunk(HostedByteBufferPointer chunk) {
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
    public void enableRememberedSetForObject(AlignedHeader chunk, Object obj) {
        // Nothing to do.
    }

    @Override
    public void clearRememberedSet(AlignedHeader chunk) {
        // Nothing to do.
    }

    @Override
    public void clearRememberedSet(UnalignedHeader chunk) {
        // Nothing to do.
    }

    @Override
    @AlwaysInline("GC performance")
    public boolean isRememberedSetEnabled(UnsignedWord header) {
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
    public void walkDirtyObjects(AlignedHeader chunk, GreyToBlackObjectVisitor visitor) {
        throw VMError.shouldNotReachHere();
    }

    @Override
    public void walkDirtyObjects(UnalignedHeader chunk, GreyToBlackObjectVisitor visitor) {
        throw VMError.shouldNotReachHere();
    }

    @Override
    public void walkDirtyObjects(Space space, GreyToBlackObjectVisitor visitor) {
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
