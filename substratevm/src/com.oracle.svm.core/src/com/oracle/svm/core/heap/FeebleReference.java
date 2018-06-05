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

/**
 * A feeble substitute for java.lang.ref.Reference.
 *
 * This is missing the notification methods, but has list methods.
 *
 * Fields for putting an instance on a list is here in the instance, because space can't be
 * allocated during a collection. But they should only be used by list-manipulating classes.
 */
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
    boolean isEnlisted() {
        return (next != this);
    }

    /*
     * Access methods.
     */

    public FeebleReferenceList<T> getList() {
        return list;
    }

    public void clearList() {
        list = null;
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
    @SuppressWarnings("unchecked")
    void listPrepend(FeebleReference<?> value) {
        assert value != this;
        assert !isEnlisted();
        next = (FeebleReference<? extends T>) value;
    }

    /** Remove this element from a list. */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    void listRemove() {
        next = this;
    }

    /** Constructor for subclasses. */
    protected FeebleReference(final T referent, final FeebleReferenceList<T> list) {
        super(referent);
        this.list = list;
        FeebleReferenceList.clean(this);
    }

    /*
     * Instance state.
     */

    /**
     * The list to which this FeebleReference is added when the referent is unreachable. This is
     * initialized and then becomes null when the FeebleReference is put on its list.
     */
    private FeebleReferenceList<T> list;

    /*
     * Instance state for FeebleReferenceList.
     */

    /**
     * The next element in the FeebleReferenceList or null if this FeebleReference is not on a list.
     * <p>
     * If this field points to this instance, then this instance is not on any list.
     */
    private FeebleReference<? extends T> next;
}
