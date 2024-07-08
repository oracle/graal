/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes;

/**
 * Marks a node as an uncached node. To find out whether a node represents an uncached node use
 * {@link Node#isUncached()} instead of checking whether a node implements this interface.
 * <p>
 * Uncached nodes are nodes with execute methods which are not designed for partial evaluation.
 * Uncached nodes do not collect profiling feedback, that is why they can be useful in runtime
 * routines without a location to store the profiling feedback. Also, uncached nodes can be used to
 * implement an initial interpreter tier where no profiling feedback is collected.
 * <p>
 * Nodes that implement {@link UncachedNode} are also {@link Node#isAdoptable() unadoptable} by
 * default, unless {@link Node#isAdoptable()} is overriden explicitly by a subclass. Truffle DSL
 * automatically generates uncached versions of nodes if the GenerateUncached annotation is
 * specified.
 *
 * @see Node#isAdoptable()
 * @see Node#isUncached()
 * @see com.oracle.truffle.api.dsl.GenerateUncached
 * @see DenyReplace
 * @since 24.2
 */
public interface UncachedNode extends UnadoptableNode {

    /**
     * Resolves the actual location for an uncached node using the location set in
     * {@link EncapsulatingNodeReference} if the passed location node is {@link Node#isUncached()
     * uncached}.
     *
     * @since 24.2
     */
    static Node resolveLocation(Node location) {
        if (location == null) {
            return null;
        }
        if (location.isUncached()) {
            return EncapsulatingNodeReference.getCurrent().get();
        } else {
            return location;
        }
    }
}
