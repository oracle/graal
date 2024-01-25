/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Objects;

import com.oracle.truffle.api.bytecode.introspection.ExceptionHandler;
import com.oracle.truffle.api.bytecode.introspection.Instruction;
import com.oracle.truffle.api.bytecode.introspection.SourceInformation;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

/**
 * A materialized bytecode location.
 * <p>
 * The current bytecode location can be bound using
 * <code>@Bind("$location") BytecodeLocation location</code> in {@link Operation operations}. In
 * order to avoid the overhead of the BytecodeLocation allocation, e.g. for exceptional cases, it is
 * possible to create the bytecode location lazily from two fields:
 * <code>@Bind("$bytecode") BytecodeNode bytecode</code> and <code>@Bind("$bci") int bci</code>.
 * This avoids the eager allocation of the bytecode location. To create a bytecode location when it
 * is needed the {@link BytecodeLocation#get(Node, int)} method can be used.
 *
 * @since 24.1
 */
public final class BytecodeLocation {

    private final BytecodeNode bytecodes;
    private final int internalBci;

    BytecodeLocation(BytecodeNode bytecodes, int bci) {
        this.bytecodes = bytecodes;
        this.internalBci = bci;
    }

    /**
     * Returns the bytecode index. This index is not stable and should only be used for debugging
     * purposes. The bytecode index is only valid for a given {@link #getBytecodeNode() bytecode
     * node}.
     *
     * @since 24.1
     */
    public int getBytecodeIndex() {
        return internalBci;
    }

    /**
     * @since 24.1
     */
    @Override
    public int hashCode() {
        return Objects.hash(bytecodes, internalBci);
    }

    /**
     * @since 24.1
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof BytecodeLocation other) {
            return bytecodes == other.bytecodes && internalBci == other.internalBci;
        } else {
            return false;
        }
    }

    /**
     * TODO
     *
     * @since 24.1
     */
    public int getIndex() {
        return internalBci;
    }

    /**
     * Dumps the bytecode, highlighting this location in the result.
     *
     * @return dump string
     * @see BytecodeNode#dump(BytecodeLocation)
     * @since 24.1
     */
    public String dump() {
        return bytecodes.dump(this);
    }

    /**
     * Computes the source location of this bytecode location.
     *
     * @since 24.1
     */
    public SourceSection getSourceLocation() {
        return bytecodes.findSourceLocation(internalBci);
    }

    /**
     * Computes the bytecode instruction at this location.
     *
     * @since 24.1
     */
    public Instruction getInstruction() {
        return bytecodes.findInstruction(internalBci);
    }

    /**
     * Computes the list of exception handlers guarding this location.
     *
     * @since 24.1
     */
    public List<ExceptionHandler> getExceptionHandlers() {
        var handlers = bytecodes.getIntrospectionData().getExceptionHandlers();
        if (handlers == null) {
            return null;
        }
        for (ExceptionHandler handler : handlers) {
            if (internalBci >= handler.getStartIndex() && internalBci < handler.getEndIndex()) {
                // TODO: this implementation is incomplete
                // multiple handlers? inner most?
                return List.of(handler);
            }
        }
        return null;
    }

    /**
     * Computes the source information at this location.
     *
     * @since 24.1
     */
    public SourceInformation getSourceInformation() {
        var sourceInfos = bytecodes.getIntrospectionData().getSourceInformation();
        if (sourceInfos == null) {
            return null;
        }
        for (SourceInformation info : sourceInfos) {
            if (internalBci >= info.getStartBci() && internalBci < info.getEndBci()) {
                // return multiple source infos?
                return info;
            }
        }
        return null;

    }

    /**
     * Returns the {@link BytecodeNode} associated with this location. The
     * {@link #getBytecodeIndex() bytecode index} is only valid for the returned node.
     *
     * @since 24.1
     */
    public BytecodeNode getBytecodeNode() {
        return bytecodes;
    }

    /**
     * Gets the bytecode location for a given FrameInstance. Frame instances are invalid as soon as
     * the execution of a frame is continued. A bytecode location can be used to materialize an
     * execution location in a bytecode interpreter, which can be used after the
     * {@link FrameInstance} is no longer valid.
     *
     * @param frameInstance the frame instance
     * @return the corresponding bytecode location or null if no location can be found.
     * @since 24.1
     */
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
        int internalBci = foundBytecodeNode.findBytecodeIndex(frameInstance);
        if (internalBci == -1) {
            return null;
        }
        return new BytecodeLocation(foundBytecodeNode, internalBci);
    }

    /**
     * Creates a {@link BytecodeLocation} associated with the given node and bci.
     *
     * @param location a node in the interpreter (can be bound using {@code @Bind("$bytecode")})
     * @param internalBci a bytecode index (can be bound using {@code @Bind("$bci")})
     * @return the {@link BytecodeLocation} or {@code null} if {@code location} is not adopted by a
     *         {@link BytecodeNode}.
     * @since 24.1
     */
    public static BytecodeLocation get(Node location, int internalBci) {
        Objects.requireNonNull(location);
        for (Node current = location; current != null; current = current.getParent()) {
            if (current instanceof BytecodeNode bytecodeNode) {
                return bytecodeNode.getBytecodeLocation(internalBci);
            }
        }
        return null;
    }

}
