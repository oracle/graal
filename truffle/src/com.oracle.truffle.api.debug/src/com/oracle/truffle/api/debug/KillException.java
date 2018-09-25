/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
