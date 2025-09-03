/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode;

import java.util.List;

/**
 * Introspection class modeling a tree of {@link SourceInformation} instances. Like
 * {@link SourceInformation}, this class models the source section for a bytecode range. Its
 * children model the source sections of subranges directly contained by this node's bytecode range.
 * <p>
 * Note: it is possible for {@link SourceInformationTree#getSourceSection()} to return {@code null}
 * for the root of the tree when the Root operation is not enclosed in a SourceSection operation.
 * See the discussion in {@link BytecodeNode#getSourceInformationTree()} for more information.
 * <p>
 * Note: Introspection classes are intended to be used for debugging purposes only. These APIs may
 * change in the future.
 *
 * @since 24.2
 * @see BytecodeNode#getSourceInformationTree()
 */
public abstract class SourceInformationTree extends SourceInformation {

    /**
     * Internal constructor for generated code. Do not use.
     *
     * @since 24.2
     */
    protected SourceInformationTree(Object token) {
        super(token);
    }

    /**
     * Returns a list of child trees, ordered by bytecode range.
     *
     * @since 24.2
     */
    public abstract List<SourceInformationTree> getChildren();

    /**
     * {@inheritDoc}
     *
     * @since 24.2
     */
    @Override
    public final String toString() {
        return toString(0);
    }

    private String toString(int depth) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            result.append("  ");
        }
        result.append(super.toString());
        result.append("\n");
        for (SourceInformationTree child : getChildren()) {
            result.append(child.toString(depth + 1));
        }
        return result.toString();
    }

}
