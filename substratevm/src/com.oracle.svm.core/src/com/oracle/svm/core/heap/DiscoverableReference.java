/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.ExcludeFromReferenceMap;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

// Checkstyle: stop
import sun.misc.Unsafe;
// Checkstyle resume

/**
 * This class is the plumbing under java.lang.ref.Reference. Instances of this class are discovered
 * by the collector and put on a list. The list can then be inspected to implement
 * java.lang.ref.Reference-like operations.
 *
 * Instances of DiscoverableReference are treated specially by the collector.
 * <ul>
 * <li>The "referent" field will *not* be followed during live object discovery. It is a Pointer,
 * not an Object.</li>
 * <li>During live object discovery, each DiscoverableReference will be added to the "discovered"
 * list.</li>
 * <li>If the object referenced by the referent field is not live at the end of a collection, the
 * "referent" field will be set to null.</li>
 * <li>If the object referenced by the referent field is live at the end of a collection, the
 * referent field is updated if the object moved.</li>
 * <li>If the object referenced by the referent field is live at the end of a collection, the
 * DiscoverableReference will be removed from the discovered list.</li>
 * </ul>
 * After a collection, anyone who is interested can run down the discovered list and do something
 * with each DiscoverableReference.
 *
 * On top of this, I think you could build PhantomReference and WeakReference classes by adding a
 * getReferent() method that returned the referent as an Object. See {@linkplain FeebleReference}
 * and {@linkplain FeebleReferenceList}. Reference classes that subsequently promote their referent
 * (SoftReference and FinalReference) would be more complicated.
 *
 * All the state that is needed to build lists, etc., is allocated in the DiscoverableReference
 * instances, because space can not be allocated for lists during a collection.
 */
public class DiscoverableReference {

    /*
     * List access methods.
     */

    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

    /** Push a DiscoverableReference onto the list. */
    public DiscoverableReference prependToDiscoveredReference(DiscoverableReference newNext) {
        setNextDiscoverableReference(newNext, true);
        return this;
    }

    /*
     * Instance access methods.
     */

    /**
     * Is this instance initialized?
     *
     * This seems like a funny method to have, because by the time I could see an instance class
     * from ordinary Java code it would be initialized. But the collector might see a reference to
     * an instance between when it is allocated and when it is initialized, and so must be able to
     * detect if the fields of the instance are safe to access. The constructor is
     * unininterruptible, so the collector either sees an uninitialized instance or fully
     * initialized instance.
     */
    public boolean isDiscoverableReferenceInitialized() {
        return discoverableReferenceInitialized;
    }

    /** Read access to the referent, as an Object. */
    Object getReferentObject() {
        return rawReferent;
    }

    /** Clear the referent. */
    public void clear() {
        rawReferent = null;
    }

    /**
     * Read access to the referent, as a Pointer. This is the low-level access for the garbage
     * collector, so no barriers are used.
     */
    public Pointer getReferentPointer() {
        return Word.objectToUntrackedPointer(ObjectAccess.readObject(this, WordFactory.signed(RAW_REFERENT_OFFSET)));
    }

    /**
     * Write access to the referent, as a Pointer. This is the low-level access for the garbage
     * collector, so no barriers are used.
     */
    public void setReferentPointer(Pointer value) {
        ObjectAccess.writeObject(this, WordFactory.signed(RAW_REFERENT_OFFSET), value.toObject());
    }

    /**
     * Read access to the next field. Must use ObjectAccess to read the field because it is written
     * with ObjectAccess only.
     */
    public DiscoverableReference getNextDiscoverableReference() {
        return KnownIntrinsics.convertUnknownValue(ObjectAccess.readObject(this, WordFactory.signed(NEXT_FIELD_OFFSET)), DiscoverableReference.class);
    }

    /**
     * Write access to the next field. Must use ObjectAccess to bypass the write barrier.
     */
    private void setNextDiscoverableReference(DiscoverableReference newNext, boolean newIsDiscovered) {
        ObjectAccess.writeObject(this, WordFactory.signed(NEXT_FIELD_OFFSET), newNext);
        isDiscovered = newIsDiscovered;
    }

    public boolean getIsDiscovered() {
        return isDiscovered;
    }

    public void clean() {
        setNextDiscoverableReference(null, false);
    }

    /**
     * Constructor for sub-classes.
     *
     * Only sub-types of this base type should be constructed.
     *
     * @param referent The Object to be tracked by this DiscoverableReference.
     */
    @Uninterruptible(reason = "The initialization of the fields must be atomic with respect to collection.")
    protected DiscoverableReference(Object referent) {
        this.rawReferent = referent;
        this.next = null;
        this.isDiscovered = false;
        this.discoverableReferenceInitialized = true;
    }

    /*
     * Instance state.
     *
     * Private rather than protected because users make subclasses of this class.
     */

    /**
     * The referent. The declared type must be {@link Object}, so that the static analysis can track
     * reads and writes. However, the field must not be in the regular reference map since we do all
     * the garbage collection support manually. The garbage collector performs Pointer-level access
     * to the field. This is fine from the point of view of the static analysis, since field stores
     * by the garbage collector do not change the type of the referent.
     */
    @ExcludeFromReferenceMap("Field is manually processed by the garbage collector.") //
    private Object rawReferent;

    /**
     * The offset of the fields for Pointer-level access to the field.
     */
    private static final long RAW_REFERENT_OFFSET = getFieldOffset("rawReferent");
    private static final long NEXT_FIELD_OFFSET = getFieldOffset("next");

    private static long getFieldOffset(String fieldName) {
        try {
            return UNSAFE.objectFieldOffset(DiscoverableReference.class.getDeclaredField(fieldName));
        } catch (NoSuchFieldException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    /**
     * Is this DiscoverableReference on a list?
     * <p>
     * DiscoverableReference does not use the self-link secret of the ancients that FeebleReference
     * uses, because the discovery happens during blackening, so the DiscoverableReference has been
     * promoted, but the next field has not yet been updated, so this == next fails.
     */
    private boolean isDiscovered;

    /** The next element in whichever list of DiscoverableReferences. */
    @SuppressWarnings("unused") //
    private DiscoverableReference next;

    /** Has the constructor of this instance run? */
    private final boolean discoverableReferenceInitialized;

    /** For testing and debugging. */
    public static final class TestingBackDoor {

        private TestingBackDoor() {
            /* No instances. */
        }

        public static Pointer getReferentPointer(DiscoverableReference that) {
            return that.getReferentPointer();
        }

        public static DiscoverableReference getNextDiscoverableReference(DiscoverableReference that) {
            return that.getNextDiscoverableReference();
        }
    }
}
