/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.nodes.Node;

/**
 * Controls breaking out of an execution context, such as a shell or eval. This exception now
 * extends {@link ThreadDeath} as that is the error that is supposed to not be ever caught. As its
 * Javadoc puts it: <em> An application should catch instances of this class only if it must clean
 * up after being terminated asynchronously. If {@code ThreadDeath} is caught by a method, it is
 * important that it be re-thrown so that the thread actually dies. </em> The re-throwing is
 * important aspect of <code>KillException</code> and as such it piggy-backs on this aspect of
 * {@link ThreadDeath}. For code that can distinguish between classical {@link ThreadDeath} and
 * {@link KillException}, is still OK to catch the exception and not propagate it any further.
 *
 * @since 0.12
 */
final class KillException extends ThreadDeath implements TruffleException {
    private static final long serialVersionUID = -8638020836970813894L;
    private final Node node;

    /**
     * Default constructor.
     *
     * @since 0.12
     */
    KillException(Node node) {
        this.node = node;
    }

    @Override
    public String getMessage() {
        return "Execution cancelled by a debugging session.";
    }

    public Node getLocation() {
        return node;
    }

    public boolean isCancelled() {
        return true;
    }
}
