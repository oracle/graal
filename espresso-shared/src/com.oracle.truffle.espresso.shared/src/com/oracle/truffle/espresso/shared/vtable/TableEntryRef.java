/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.shared.vtable;

import com.oracle.truffle.espresso.shared.meta.FieldAccess;
import com.oracle.truffle.espresso.shared.meta.MethodAccess;
import com.oracle.truffle.espresso.shared.meta.TypeAccess;

/**
 * A reference to a {@link TableEntry} in a table, annotated with slot-local state computed
 * by the {@link VTable} builder.
 */
public final class TableEntryRef<C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> {
    private static final byte IMPLICIT_INTERFACE_METHOD = 1;
    private static final byte USE_VTABLE_SLOT_INDEX = 1 << 1;
    private static final byte NON_PUBLIC_INTERFACE_SELECTION = 1 << 6;
    private static final byte SELECTION_FAILURE = (byte) (1 << 7);

    private final TableEntry<C, M, F> entry;
    private byte state;

    private TableEntryRef(TableEntry<C, M, F> entry) {
        this.entry = entry;
    }

    static <C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> TableEntryRef<C, M, F> create(TableEntry<C, M, F> entry) {
        return new TableEntryRef<>(entry);
    }

    TableEntryRef<C, M, F> useVTableSlotIndex() {
        assert (state & USE_VTABLE_SLOT_INDEX) == 0;
        state |= USE_VTABLE_SLOT_INDEX;
        return this;
    }

    TableEntryRef<C, M, F> asImplicitInterfaceMethod() {
        assert (state & IMPLICIT_INTERFACE_METHOD) == 0;
        state |= IMPLICIT_INTERFACE_METHOD;
        return this;
    }

    TableEntryRef<C, M, F> asSelectionFailure() {
        assert (state & SELECTION_FAILURE) == 0;
        state |= SELECTION_FAILURE;
        return this;
    }

    TableEntryRef<C, M, F> asNonPublicInterfaceSelection() {
        assert (state & NON_PUBLIC_INTERFACE_SELECTION) == 0;
        state |= NON_PUBLIC_INTERFACE_SELECTION;
        return this;
    }

    /**
     * Returns the {@link TableEntry} this reference points to.
     */
    public TableEntry<C, M, F> getEntry() {
        return entry;
    }

    /**
     * Whether this table entry can use its position in the virtual table as its vtable index.
     */
    public boolean canUseVTableSlotIndex() {
        return (state & USE_VTABLE_SLOT_INDEX) != 0;
    }

    /**
     * Whether this table entry is a failing entry. Any call-site that selects this entry should
     * throw {@link IncompatibleClassChangeError}.
     */
    public boolean isSelectionFailure() {
        return (state & SELECTION_FAILURE) != 0;
    }

    /**
     * Whether this table entry represents an interface selection failure due to selecting a
     * non-public method.
     */
    public boolean isNonPublicInterfaceSelection() {
        return (state & NON_PUBLIC_INTERFACE_SELECTION) != 0;
    }

    /**
     * Whether this table entry represents an interface method added to a non-interface vtable.
     */
    public boolean isImplicitInterfaceMethod() {
        return (state & IMPLICIT_INTERFACE_METHOD) != 0;
    }
}
