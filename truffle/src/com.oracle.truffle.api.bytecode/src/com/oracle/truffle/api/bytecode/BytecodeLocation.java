/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

/**
 * A materialized bytecode location.
 * <p>
 * The current bytecode location can be bound using <code>@Bind BytecodeLocation location</code> in
 * {@link Operation operations}. In order to avoid the overhead of the BytecodeLocation allocation,
 * e.g. for exceptional cases, it is possible to create the bytecode location lazily from two
 * fields: <code>@Bind BytecodeNode bytecode</code> and
 * <code>@Bind("$bytecodeIndex") int bci</code>. This avoids the eager allocation of the bytecode
 * location. To create a bytecode location when it is needed the
 * {@link BytecodeLocation#get(Node, int)} method can be used.
 *
 * @since 24.2
 */
@Bind.DefaultExpression("$bytecodeNode.getBytecodeLocation($bytecodeIndex)")
public final class BytecodeLocation {

    private final BytecodeNode bytecodes;
    private final int bytecodeIndex;

    BytecodeLocation(BytecodeNode bytecodes, int bytecodeIndex) {
        this.bytecodes = bytecodes;
        this.bytecodeIndex = bytecodeIndex;
        assert bytecodes.validateBytecodeIndex(bytecodeIndex);
    }

    /**
     * Returns the bytecode index. This index is not stable and should only be used for debugging
     * purposes. The bytecode index is only meaningful when coupled with a particular
     * {@link #getBytecodeNode() bytecode node}.
     *
     * @since 24.2
     */
    public int getBytecodeIndex() {
        return bytecodeIndex;
    }

    /**
     * Returns the {@link BytecodeNode} associated with this location. The
     * {@link #getBytecodeIndex() bytecode index} is only valid for the returned node.
     *
     * @since 24.2
     */
    public BytecodeNode getBytecodeNode() {
        return bytecodes;
    }

    /**
     * {@inheritDoc}
     *
     * @since 24.2
     */
    @Override
    public int hashCode() {
        return Objects.hash(bytecodes, bytecodeIndex);
    }

    /**
     * {@inheritDoc}
     *
     * @since 24.2
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof BytecodeLocation other) {
            return bytecodes == other.bytecodes && bytecodeIndex == other.bytecodeIndex;
        } else {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 24.2
     */
    @Override
    public String toString() {
        return String.format("BytecodeLocation [bytecode=%s, bci=%d]", bytecodes, bytecodeIndex);
    }

    /**
     * Dumps the bytecode debug information, highlighting this location in the result.
     *
     * @return dump string
     * @see BytecodeNode#dump(BytecodeLocation)
     * @since 24.2
     */
    public String dump() {
        return bytecodes.dump(this);
    }

    /**
     * Updates this location to the newest {@link BytecodeNode bytecode node} of the parent
     * {@link BytecodeRootNode bytecode root node}, translating the {@link #getBytecodeIndex()
     * bytecode index} to the new bytecode node in the process. It is useful to update the location
     * if source information or instrumentations were materialized in the meantime. Note that the
     * {@link #getBytecodeIndex() bytecode index} may be different in the updated location.
     *
     * @since 24.2
     */
    public BytecodeLocation update() {
        BytecodeNode thisNode = this.bytecodes;
        BytecodeNode newNode = this.bytecodes.getBytecodeRootNode().getBytecodeNode();
        if (thisNode == newNode) {
            return this;
        }
        int newBytecodeIndex = thisNode.translateBytecodeIndex(newNode, this.bytecodeIndex);
        return new BytecodeLocation(newNode, newBytecodeIndex);
    }

    /**
     * Ensures source information available for this location and {@link #update() updates} this
     * location to a new location of the bytecode node with source information. Materialization of
     * source information may be an expensive operation if the source information was not yet
     * materialized yet.
     *
     * @since 24.2
     */
    public BytecodeLocation ensureSourceInformation() {
        BytecodeNode thisNode = this.bytecodes.ensureSourceInformation();
        if (thisNode != this.bytecodes) {
            return update();
        }
        return this;
    }

    /**
     * Computes the most concrete source location of this bytecode location.
     *
     * @see BytecodeNode#getSourceLocation(int)
     * @since 24.2
     */
    public SourceSection getSourceLocation() {
        return bytecodes.getSourceLocation(bytecodeIndex);
    }

    /**
     * Computes all source locations of this bytecode location. Returns an empty array if no source
     * locations are available. The list is ordered from most to least concrete.
     *
     * @see BytecodeNode#getSourceLocations(int)
     * @since 24.2
     */
    public SourceSection[] getSourceLocations() {
        return bytecodes.getSourceLocations(bytecodeIndex);
    }

    /**
     * Returns the bytecode instruction at this location, which provides additional debug
     * information for debugging and tracing.
     *
     * @since 24.2
     */
    public Instruction getInstruction() {
        return bytecodes.findInstruction(bytecodeIndex);
    }

    /**
     * Returns all exception handlers that span over this bytecode location. Returns an empty list
     * if no exception handlers span over this location.
     *
     * @since 24.2
     */
    public List<ExceptionHandler> getExceptionHandlers() {
        var handlers = bytecodes.getExceptionHandlers();
        List<ExceptionHandler> result = null;
        for (ExceptionHandler handler : handlers) {
            if (bytecodeIndex >= handler.getStartBytecodeIndex() && bytecodeIndex < handler.getEndBytecodeIndex()) {
                if (result == null) {
                    result = new ArrayList<>();
                }
                result.add(handler);
            }
        }
        return result == null ? List.of() : result;
    }

    /**
     * Returns all source informations available at this location.
     *
     * @since 24.2
     */
    public List<SourceInformation> getSourceInformation() {
        var sourceInfos = bytecodes.getSourceInformation();
        if (sourceInfos == null) {
            return null;
        }
        List<SourceInformation> found = null;
        for (SourceInformation info : sourceInfos) {
            if (bytecodeIndex >= info.getStartBytecodeIndex() && bytecodeIndex < info.getEndBytecodeIndex()) {
                if (found == null) {
                    found = new ArrayList<>();
                }
                found.add(info);
            }
        }
        return found == null ? List.of() : found;

    }

    /**
     * Gets the bytecode location for a given FrameInstance. Frame instances are invalid as soon as
     * the execution of a frame is continued. A bytecode location can be used to materialize an
     * execution location in a bytecode interpreter, which can be used after the
     * {@link FrameInstance} is no longer valid.
     *
     * @param frameInstance the frame instance
     * @return the corresponding bytecode location or null if no location can be found.
     * @since 24.2
     */
    @TruffleBoundary
    public static BytecodeLocation get(FrameInstance frameInstance) {
        /*
         * We use two strategies to communicate the current bci.
         *
         * For cached interpreters, each operation node corresponds to a unique bci. We can walk the
         * parent chain of the call node to find the operation node, and then use it to compute a
         * bci. This incurs no overhead during regular execution.
         *
         * For uncached interpreters, we use uncached nodes, so the call node (if any) is not
         * adopted by an operation node. Instead, the uncached interpreter stores the current bci
         * into the frame before any operation that might call another node. This incurs a bit of
         * overhead during regular execution (but just for the uncached interpreter).
         */
        Node location = frameInstance.getCallNode();
        BytecodeNode foundBytecodeNode = null;
        for (Node current = location; current != null; current = current.getParent()) {
            if (current instanceof BytecodeNode bytecodeNode) {
                foundBytecodeNode = bytecodeNode;
                break;
            }
        }
        if (foundBytecodeNode == null) {
            return null;
        }
        int bci = foundBytecodeNode.findBytecodeIndex(frameInstance);
        if (bci == -1) {
            return null;
        }
        return new BytecodeLocation(foundBytecodeNode, bci);
    }

    /**
     * Creates a {@link BytecodeLocation} associated with the given node and bci.
     *
     * @param location a node in the interpreter (can be bound using
     *            {@code @Bind BytecodeNode bytecode})
     * @param bci a bytecode index (can be bound using {@code @Bind("$bytecodeIndex") int bci})
     * @return the {@link BytecodeLocation} or {@code null} if {@code location} is not adopted by a
     *         {@link BytecodeNode}.
     * @since 24.2
     */
    public static BytecodeLocation get(Node location, int bci) {
        Objects.requireNonNull(location);
        CompilerAsserts.partialEvaluationConstant(location);
        for (Node current = location; current != null; current = current.getParent()) {
            if (current instanceof BytecodeNode bytecodeNode) {
                return bytecodeNode.getBytecodeLocation(bci);
            }
        }
        return null;
    }

    /**
     * Creates a {@link BytecodeLocation} associated with a {@link TruffleStackTraceElement}.
     *
     * @return the {@link BytecodeLocation} or {@code null} if no bytecode interpreter can be found
     *         in the stack trace element.
     * @since 24.2
     */
    public static BytecodeLocation get(TruffleStackTraceElement element) {
        Node location = element.getLocation();
        if (location == null) {
            return null;
        }
        return get(location, element.getBytecodeIndex());
    }

}
