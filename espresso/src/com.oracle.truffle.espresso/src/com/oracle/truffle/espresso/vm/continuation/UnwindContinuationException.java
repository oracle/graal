/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.vm.continuation;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

/**
 * The exception thrown host-side to unwind the stack when a continuation suspends. Frame info is
 * gathered up into a linked list.
 */
@SuppressWarnings("serial")
public class UnwindContinuationException extends ControlFlowException {
    private final transient StaticObject continuation;

    public transient HostFrameRecord head = null;

    public UnwindContinuationException(StaticObject continuation) {
        this.continuation = continuation;
    }

    public StaticObject getContinuation() {
        return continuation;
    }

    @CompilerDirectives.TruffleBoundary
    public StaticObject toGuest(Meta meta) {
        // Convert the linked list from host to guest.
        HostFrameRecord cursor = head;
        StaticObject guestHead = null;
        StaticObject guestCursor = null;
        while (cursor != null) {
            StaticObject next = cursor.copyToGuest(meta);
            if (guestHead == null) {
                guestHead = next;
            }
            if (guestCursor != null) {
                meta.continuum.com_oracle_truffle_espresso_continuations_Continuation_FrameRecord_next.setObject(guestCursor, next);
            }
            guestCursor = next;
            cursor = cursor.next;
        }
        return guestHead;
    }
}
