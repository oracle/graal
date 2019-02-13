/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.genscavenge;

import com.oracle.svm.core.annotate.Uninterruptible;

/**
 * An AutoCloseable boolean.
 *
 * A Latch can be used in
 *
 * <pre>
 * try (final Latch i = myLatch.open()) {
 *     ....
 * }
 * </pre>
 *
 * or it can be used without the "try" if you do not want it to be auto-closed, as in
 *
 * <pre>
 * myLatch.open();
 * ....
 * myLatch.close();
 * </pre>
 *
 * so that exceptions coming out of the <code>....</code> will not close the Latch.
 */
public class Latch implements AutoCloseable {

    /** Create a latch that is closed. */
    public static Latch factory(final String name) {
        return new Latch(name);
    }

    /** Open the Latch, e.g., in a try statement. */
    public Latch open() {
        assert state != true : "Already open.";
        state = true;
        return this;
    }

    /** Close the Latch, e.g., in a try statement. */
    @Override
    public void close() {
        assert state != false : "Already closed.";
        state = false;
    }

    /** Get the state. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean getState() {
        return state;
    }

    /** Get the name, for debugging. */
    public String getName() {
        return name;
    }

    /** Constructor. */
    protected Latch(final String name) {
        this.name = name;
        this.state = false;
    }

    // Mutable state.
    protected boolean state;

    // Immutable state.
    protected final String name;
}
