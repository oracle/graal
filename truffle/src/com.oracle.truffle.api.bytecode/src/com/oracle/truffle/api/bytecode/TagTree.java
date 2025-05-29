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

import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Logical tree representation of the {@code Tag} operations of a bytecode program.
 *
 * @since 24.2
 * @see Tag
 * @see BytecodeNode#getTagTree
 */
public interface TagTree {
    /**
     * Returns the child trees corresponding to {@code Tag} operations nested in this node.
     *
     * @since 24.2
     */
    List<TagTree> getTreeChildren();

    /**
     * Returns the {@link Tag tags} associated with this node.
     *
     * @since 24.2
     */
    List<Class<? extends Tag>> getTags();

    /**
     * Returns whether the given {@code tag} is associated with this node.
     *
     * @param tag the tag to search for
     *
     * @since 24.2
     */
    boolean hasTag(Class<? extends Tag> tag);

    /**
     * Returns the bytecode index at which the interpreter enters the tag operation. The bytecode
     * interpreter will invoke {@link ProbeNode#onEnter} at this point in the program.
     *
     * @since 24.2
     */
    int getEnterBytecodeIndex();

    /**
     * Returns the bytecode index at which the interpreter "returns" from the tag operation. The
     * bytecode interpreter will invoke {@link ProbeNode#onReturnValue} with the child operation's
     * result (if any) at this point in the program.
     * <p>
     * Note: the instruction at this index is not necessarily a return, but the value it produces is
     * treated as a return value for the sake of instrumentation. There can also be other return
     * points if the child operation has early exits; this method returns the regular return point.
     *
     * @since 24.2
     */
    int getReturnBytecodeIndex();

    /**
     * Gets the most concrete {@link SourceSection source location} associated with the tag
     * operation.
     *
     * @since 24.2
     * @see BytecodeNode#getSourceLocation(com.oracle.truffle.api.frame.Frame,
     *      com.oracle.truffle.api.nodes.Node)
     */
    SourceSection getSourceSection();

    /**
     * Gets all {@link SourceSection source locations} associated with the tag operation, ordered
     * from most to least concrete.
     *
     * @since 24.2
     * @see BytecodeNode#getSourceLocations(com.oracle.truffle.api.frame.Frame,
     *      com.oracle.truffle.api.nodes.Node)
     */
    SourceSection[] getSourceSections();

}
