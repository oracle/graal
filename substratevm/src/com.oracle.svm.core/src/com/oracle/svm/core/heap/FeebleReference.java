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

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.annotate.UnknownClass;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicReference;

/* @formatter:off */
/**
 *
 * A feeble substitute for java.lang.ref.Reference.
 *
 * This is missing the notification methods, but has list methods.
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
public class FeebleReference<T> extends DiscoverableReference {

    public static <T> FeebleReference<T> factory(final T referent, final FeebleReferenceList<T> list) {
        return new FeebleReference<>(referent, list);
    }

    /*
     * Methods like those from java.lang.ref.Reference.
     */

    /** Turn the referent into a strong reference. */
    @SuppressWarnings("unchecked")
    public T get() {
        return (T) getReferentObject();
    }

    /** Is this FeebleReference on the list? */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public boolean isEnlisted() {
        return (next != this);
    }

    /*
     * Access methods.
     */

    /* For GR-14335 */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public boolean hasList() {
        return (list != null);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public FeebleReferenceList<T> getList() {
        return list.get();
    }

    /** Clears the list, returning the previous value, which might be null. */
    public FeebleReferenceList<T> clearList() {
        return list.getAndSet(null);
    }

    /** For GR-14335. */
    public boolean isFeeblReferenceInitialized() {
        return feebleReferenceInitialized;
    }

    /*
     * Access methods for FeebleReferenceList.
     */

    /** Follow the next pointer. */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    FeebleReference<? extends T> listGetNext() {
        return (isEnlisted() ? next : null);
    }

    /** Prepend this element to a list element. */
    void listPrepend(FeebleReference<?> newNext) {
        assert newNext != this : "Creating self-loop.";
        next = uncheckedNarrow(newNext);
    }

    /** Remove this element from a list. */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    void listRemove() {
        next = this;
    }

    /** Constructor for subclasses. */
    protected FeebleReference(T referent, FeebleReferenceList<T> list) {
        /* Allocate the AtomicReference for the constructor before I become uninterruptible. */
        this(referent, new AtomicReference<>(list));
    }

    @Uninterruptible(reason = "The initialization of the fields must be atomic with respect to collection.")
    private FeebleReference(T referent, AtomicReference<FeebleReferenceList<T>> list) {
        super(referent);
        this.list = list;
        FeebleReferenceList.clean(this);
        this.feebleReferenceInitialized = true;
    }

    /** Narrow a FeebleReference<?> to a FeebleReference<S>. */
    @SuppressWarnings("unchecked")
    static <S> FeebleReference<S> uncheckedNarrow(FeebleReference<?> fr) {
        return (FeebleReference<S>) fr;
    }

    /*
     * Instance state.
     */

    /**
     * The list to which this FeebleReference is added when the referent is unreachable. This is
     * initialized and then becomes null when the FeebleReference is put on its list.
     */
    private final AtomicReference<FeebleReferenceList<T>> list;

    /*
     * Instance state for FeebleReferenceList.
     */

    /**
     * The next element in the FeebleReferenceList or null if this FeebleReference is not on a list.
     * <p>
     * If this field points to this instance, then this instance is not on any list.
     */
    private FeebleReference<? extends T> next;

    /** For GR-14355: Whether this instance has been initialized. */
    private final boolean feebleReferenceInitialized;
}
