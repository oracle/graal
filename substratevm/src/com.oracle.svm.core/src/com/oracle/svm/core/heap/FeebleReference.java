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
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.annotate.UnknownClass;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicReference;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

// Checkstyle: stop
import sun.misc.Unsafe;
// Checkstyle: resume

/* @formatter:off */
/**
 *
 * This class is the plumbing under java.lang.ref.Reference. Instances of this class are discovered
 * by the collector and put on a list. The list can then be inspected to implement
 * java.lang.ref.Reference-like operations.
 *
 * Instances are treated specially by the collector.
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

 * This is missing the notification methodsof java.lang.ref.Reference, but has list methods.
 *
 * Fields for putting an instance on a list is here in the instance, because space can't be
 * allocated during a collection. But they should only be used by list-manipulating classes.
 *
 * <p>
 * The lifecycle of a FeebleReference:
 * <ul>
 *   <li>If the FeebleReference does not have a list:
 *     <table>
 *       <tr>  <th align=left>Stage</th>  <th>.referent</th>      <th>.list</th>  <th>.next</th>  </tr>
 *       <tr>  <td>At construction</td>   <td>referent</td>       <td>null</td>   <td>this</td>   </tr>
 *       <tr>  <td>At discovery</td>      <td><em>null</em></td>  <td>null</td>   <td>this</td>   </tr>
 *     </table>
 *   </li>
 *   <li>If the FeebleReference does have a list:</li>
 *     <table>
 *       <tr>  <th align=left>Stage</th>  <th>.referent</th>      <th>.list</th>           <th>.next</th>           </tr>
 *       <tr>  <td>At construction</td>   <td>referent</td>       <td>list</td>            <td>this</td>            </tr>
 *       <tr>  <td>At discovery</td>      <td><em>null</em></td>  <td>list</td>            <td>this</td>            </tr>
 *       <tr>  <td>Before pushing</td>    <td>null</td>           <td><em>null</em></td>   <td>this</td>            </tr>
 *       <tr>  <td>After pushing</td>     <td>null</td>           <td>null</td>            <td><em>next</em></td>   </tr>
 *       <tr>  <td>After popping</td>     <td>null</td>           <td>null</td>            <td><em>this</em></td>   </tr>
 *     </table>
 *     Note that after being pushed and popped a FeebleReference with a list
 *     is in the same state as a discovered FeebleReference without a list.
 *   </li>
 * </ul>
 */
 /* @formatter:on */
@UnknownClass
public class FeebleReference<T> {

    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

    private static long getFieldOffset(String fieldName) {
        try {
            return UNSAFE.objectFieldOffset(FeebleReference.class.getDeclaredField(fieldName));
        } catch (NoSuchFieldException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private static final long RAW_REFERENT_FIELD_OFFSET = getFieldOffset("rawReferent");
    private static final long NEXT_DISCOVERED_FIELD_OFFSET = getFieldOffset("nextDiscovered");

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
     * Is this DiscoverableReference on a list?
     * <p>
     * DiscoverableReference does not use the self-link secret of the ancients that FeebleReference
     * uses, because the discovery happens during blackening, so the DiscoverableReference has been
     * promoted, but the next field has not yet been updated, so this == next fails.
     */
    private boolean isDiscovered;

    /** The next element in whichever list of DiscoverableReferences. */
    @SuppressWarnings("unused") //
    private FeebleReference<?> nextDiscovered;

    public static <T> FeebleReference<T> factory(final T referent, final FeebleReferenceList<? super T> list) {
        return new FeebleReference<>(referent, list);
    }

    @SuppressWarnings("unchecked")
    public T get() {
        return (T) getReferentObject();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isOnList() {
        return (nextInList != this);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean hasList() {
        return (list != null);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public FeebleReferenceList<? super T> getList() {
        return list.get();
    }

    /** Clears the list, returning the previous value, which might be null. */
    public FeebleReferenceList<? super T> clearList() {
        return list.getAndSet(null);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    FeebleReference<?> listGetNext() {
        return (isOnList() ? nextInList : null);
    }

    void listPrepend(FeebleReference<?> newNext) {
        assert newNext != this : "Creating self-loop.";
        nextInList = newNext;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void listRemove() {
        nextInList = this;
    }

    protected FeebleReference(T referent, FeebleReferenceList<? super T> list) {
        /* Allocate the AtomicReference for the constructor before I become uninterruptible. */
        this(referent, new AtomicReference<>(list));
    }

    @Uninterruptible(reason = "The initialization of the fields must be atomic with respect to collection.")
    private FeebleReference(T referent, AtomicReference<FeebleReferenceList<? super T>> list) {
        this.rawReferent = referent;
        this.nextDiscovered = null;
        this.isDiscovered = false;
        this.list = list;
        FeebleReferenceList.clean(this);
        this.initialized = true;
    }

    /**
     * The list to which this FeebleReference is added when the referent is unreachable. This is
     * initialized and then becomes null when the FeebleReference is put on its list.
     */
    private final AtomicReference<FeebleReferenceList<? super T>> list;

    /**
     * The next element in the FeebleReferenceList or null if this FeebleReference is not on a list.
     * <p>
     * If this field points to this instance, then this instance is not on any list.
     */
    private FeebleReference<?> nextInList;

    private final boolean initialized;

    public FeebleReference<T> prependToDiscoveredReference(FeebleReference<?> newNext) {
        setNextDiscoveredReference(newNext, true);
        return this;
    }

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
    public boolean isInitialized() {
        return initialized;
    }

    Object getReferentObject() {
        return rawReferent;
    }

    public void clear() {
        rawReferent = null;
    }

    /**
     * Read access to the referent, as a Pointer. This is the low-level access for the garbage
     * collector, so no barriers are used.
     */
    public Pointer getReferentPointer() {
        return Word.objectToUntrackedPointer(ObjectAccess.readObject(this, WordFactory.signed(RAW_REFERENT_FIELD_OFFSET)));
    }

    /**
     * Write access to the referent, as a Pointer. This is the low-level access for the garbage
     * collector, so no barriers are used.
     */
    public void setReferentPointer(Pointer value) {
        ObjectAccess.writeObject(this, WordFactory.signed(RAW_REFERENT_FIELD_OFFSET), value.toObject());
    }

    /**
     * Read access to the next field. Must use ObjectAccess to read the field because it is written
     * with ObjectAccess only.
     */
    public FeebleReference<?> getNextDiscoveredReference() {
        return KnownIntrinsics.convertUnknownValue(ObjectAccess.readObject(this, WordFactory.signed(NEXT_DISCOVERED_FIELD_OFFSET)), FeebleReference.class);
    }

    /**
     * Write access to the next field. Must use ObjectAccess to bypass the write barrier.
     */
    private void setNextDiscoveredReference(FeebleReference<?> newNext, boolean newIsDiscovered) {
        ObjectAccess.writeObject(this, WordFactory.signed(NEXT_DISCOVERED_FIELD_OFFSET), newNext);
        isDiscovered = newIsDiscovered;
    }

    public boolean getIsDiscovered() {
        return isDiscovered;
    }

    public void cleanDiscovered() {
        setNextDiscoveredReference(null, false);
    }

    /**
     * Read access to the next field, as a Pointer. This is the low-level access for the garbage
     * collector, so no barriers are used.
     */
    public Pointer getNextDiscoveredRefPointer() {
        return Word.objectToUntrackedPointer(this).add(WordFactory.signed(NEXT_DISCOVERED_FIELD_OFFSET));
    }

    public static final class TestingBackDoor {

        private TestingBackDoor() {
        }

        @NeverInline("Prevent the access from moving around")
        public static Pointer getReferentPointer(FeebleReference<?> that) {
            return that.getReferentPointer();
        }

        public static FeebleReference<?> getNextDiscoveredReference(FeebleReference<?> that) {
            return that.getNextDiscoveredReference();
        }
    }
}
