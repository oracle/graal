/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import com.oracle.svm.core.annotate.Uninterruptible;
import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.snippets.KnownIntrinsics;

/**
 * Manipulations of an Object header.
 * <p>
 * An ObjectHeader is a reference-sized collection of bits in each Object instance. It holds
 * meta-information about this instance. The ObjectHeader holds a DynamicHub, which identifies the
 * Class of the instance, and flags used by the garbage collector. Alternatively, e.g., during
 * garbage collection, the ObjectHeader may hold a forwarding reference to the new location of this
 * instance if the Object has been moved by the collector.
 * <p>
 * I treat an ObjectHeader as an Unsigned, until careful examination allows me to cast it to a
 * Pointer, or to an Object. Since an ObjectHeader is just a collection of bits, rather than an
 * Object, the methods in this class are all static methods.
 * <p>
 * These methods operate on a bewildering mixture of Object *or* Pointer. Because a Pointer *is* an
 * Object, the methods have different names, rather than just different signatures. Also a mixture
 * of Object and Unsigned (and Unsigned) to distinguish methods that read from Objects, from methods
 * that operate on a previously read ObjectHeader, or methods that operate on just the low-order
 * bits of an ObjectHeader. The variants that take an Object as a parameter have the reasonable
 * names because they are used from outside. The variants that take an Unsigned ObjectHeader have
 * "Header" in their name or the name of the argument, and the variants that take an Unsigned with
 * just the header bits in them have "HeaderBits" in their name or in the name of the argument. (I
 * hope I did that consistently.)
 */

public abstract class ObjectHeader {

    /*
     * Read and write of Object headers.
     */

    /**
     * Read the header of the object at the specified address. When compressed references are
     * enabled, the specified address must be the uncompressed absolute address of the object in
     * memory.
     */
    public static UnsignedWord readHeaderFromPointer(Pointer objectPointer) {
        if (getReferenceSize() == Integer.BYTES) {
            return WordFactory.unsigned(objectPointer.readInt(getHubOffset()));
        } else {
            return objectPointer.readWord(getHubOffset());
        }
    }

    /**
     * Write the header of a newly created object, using {@link LocationIdentity#init()}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void initializeHeaderOfNewObject(Pointer objectPointer, WordBase header) {
        if (getReferenceSize() == Integer.BYTES) {
            objectPointer.writeInt(getHubOffset(), (int) header.rawValue(), LocationIdentity.INIT_LOCATION);
        } else {
            objectPointer.writeWord(getHubOffset(), header, LocationIdentity.INIT_LOCATION);
        }
    }

    /**
     * Read the header of the specified object.
     */
    public static UnsignedWord readHeaderFromObject(Object o) {
        if (getReferenceSize() == Integer.BYTES) {
            return WordFactory.unsigned(ObjectAccess.readInt(o, getHubOffset()));
        } else {
            return ObjectAccess.readWord(o, getHubOffset());
        }
    }

    /**
     * Write the header of the specified object.
     */
    public static void writeHeaderToObject(Object o, WordBase header) {
        if (getReferenceSize() == Integer.BYTES) {
            ObjectAccess.writeInt(o, getHubOffset(), (int) header.rawValue());
        } else {
            ObjectAccess.writeWord(o, getHubOffset(), header);
        }
    }

    public static DynamicHub readDynamicHubFromObject(Object o) {
        return KnownIntrinsics.readHub(o);
    }

    /** Decode a DynamicHub from an Object header. */
    protected static DynamicHub dynamicHubFromObjectHeader(UnsignedWord header) {
        // Turn the Unsigned header into a Pointer, and then to an Object of type DynamicHub.
        final UnsignedWord pointerBits = clearBits(header);
        final Object objectValue;
        if (ReferenceAccess.singleton().haveCompressedReferences()) {
            UnsignedWord compressedBits = pointerBits.unsignedShiftRight(ReferenceAccess.singleton().getCompressEncoding().getShift());
            objectValue = ReferenceAccess.singleton().uncompressReference(compressedBits);
        } else {
            objectValue = ((Pointer) pointerBits).toObject();
        }
        return KnownIntrinsics.unsafeCast(objectValue, DynamicHub.class);
    }

    /*
     * Unpacking methods.
     */

    /** Clear the object header bits from a header. */
    public static UnsignedWord clearBits(UnsignedWord header) {
        return header.and(BITS_CLEAR);
    }

    /*
     * Forwarding pointer methods.
     */

    /** Is this header a forwarding pointer? */
    public abstract boolean isForwardedHeader(UnsignedWord header);

    /** Extract a forwarding Pointer from a header. */
    public abstract Pointer getForwardingPointer(Pointer objectPointer);

    /** Extract a forwarded Object from a header. */
    public abstract Object getForwardedObject(Pointer objectPointer);

    /*
     * ObjectHeaders record (among other things) if it was allocated by a SystemAllocator or in the
     * heap. If the object is in the heap, it might be either aligned or unaligned.
     *
     * The default is heap-allocated aligned objects, so the others have "set" methods.
     */

    /** A special method for use during native image construction. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public abstract long setBootImageOnLong(long l);

    /** Objects are aligned by default. This marks them as unaligned. */
    protected abstract void setUnaligned(Object o);

    /*
     * Complex predicates.
     */

    protected abstract boolean isHeapAllocated(Object o);

    public abstract boolean isNonHeapAllocatedHeader(UnsignedWord header);

    protected abstract boolean isNonHeapAllocated(Object o);

    public abstract boolean isAlignedObject(Object o);

    public abstract boolean isUnalignedObject(Object o);

    /*
     * Convenience methods.
     */

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected static int getReferenceSize() {
        return ConfigurationValues.getObjectLayout().getReferenceSize();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected static int getHubOffset() {
        return ConfigurationValues.getObjectLayout().getHubOffset();
    }

    /*
     * Debugging.
     */

    public String toStringFromObject(Object o) {
        final UnsignedWord header = readHeaderFromObject(o);
        return toStringFromHeader(header);
    }

    public abstract String toStringFromHeader(UnsignedWord header);

    // Private static final state.

    /** Constructor for concrete subclasses. */
    protected ObjectHeader() {
        // All-static class: no instances.
    }

    // Constants.

    protected static final UnsignedWord BITS_MASK = WordFactory.unsigned(0b111);
    public static final UnsignedWord BITS_CLEAR = BITS_MASK.not();
}
