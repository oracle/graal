/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import java.util.List;

import com.oracle.truffle.api.nodes.Node;

/**
 * Represents a guest stack trace element.
 *
 * @since 0.27
 */
public final class TruffleStackTraceElement {

    private final Node location;
    private final RootCallTarget target;

    TruffleStackTraceElement(Node location, RootCallTarget target) {
        this.location = location;
        this.target = target;
    }

    /**
     * Returns a node representing the callsite on the stack. Returns <code>null</code> if no
     * detailed callsite information is available.
     *
     * @since 0.27
     **/
    public Node getLocation() {
        return location;
    }

    /**
     * Returns the call target on the stack. Returns never <code>null</code>.
     *
     * @since 0.27
     **/
    public RootCallTarget getTarget() {
        return target;
    }

    /**
     * Returns the guest language frames that are stored in this throwable or <code>null</code> if
     * no guest language frames are available. Guest language frames are automatically added by the
     * Truffle runtime the first time the exception is passed through a {@link CallTarget call
     * target} and the frames are not yet set. Therefore no guest language frames are available
     * immediatly after the exception was constructed. The returned list is not modifiable. The
     * number stack trace elements that are filled in can be customized by implementing
     * {@link TruffleException#getStackTraceElementLimit()} .
     *
     * @param throwable the throwable instance to look for guest language frames
     * @see #fillIn(Throwable) To force early filling of guest language stack frames.
     * @since 0.27
     */
    public static List<TruffleStackTraceElement> getStackTrace(Throwable throwable) {
        return TruffleStackTrace.find(throwable);
    }

    /**
     * Fills in the guest language stack frames from the current frames on the stack. If the stack
     * was already filled before then this method has no effect. The implementation attaches a
     * lightweight exception object to the last location in the {@link Throwable#getCause() cause}
     * chain of the exception. The number stack trace elements that are filled in can be customized
     * by implementing {@link TruffleException#getStackTraceElementLimit()} .
     *
     * @param throwable the throwable to fill
     * @since 0.27
     */
    public static void fillIn(Throwable throwable) {
        TruffleStackTrace.fillIn(throwable);
    }

}
