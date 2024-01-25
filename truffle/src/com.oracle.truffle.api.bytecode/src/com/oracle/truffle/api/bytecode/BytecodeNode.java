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
import java.util.function.Predicate;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.introspection.BytecodeIntrospection;
import com.oracle.truffle.api.bytecode.introspection.ExceptionHandler;
import com.oracle.truffle.api.bytecode.introspection.Instruction;
import com.oracle.truffle.api.bytecode.introspection.SourceInformation;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Represents the bytecode for an interpreter. The bytecode can change over time; this class
 * encapsulates the current state.
 *
 * The current bytecode node can be bound using
 * <code>@Bind("$bytecode") BytecodeNode bytecode</code> from {@link Operation operations}.
 *
 * @since 24.1
 */
public abstract class BytecodeNode extends Node {

    protected BytecodeNode(Object token) {
        BytecodeNodes.checkToken(token);
    }

    // needs to translate locations if the bytecodes were rewritten in the meantime
    public final BytecodeLocation getBytecodeLocation(Frame frame, Node location) {
        int internalBci = findBytecodeIndexImpl(frame, location);
        if (internalBci == -1) {
            return null;
        }
        return new BytecodeLocation(this, internalBci);
    }

    /**
     * Gets the {@code bci} associated with a particular
     * {@link com.oracle.truffle.api.frame.FrameInstance frameInstance} obtained from a stack walk.
     *
     * @param frameInstance the frame instance
     * @return the corresponding bytecode index, or -1 if the index could not be found
     */
    public final BytecodeLocation getBytecodeLocation(FrameInstance frameInstance) {
        int internalBci = findBytecodeIndex(frameInstance);
        if (internalBci == -1) {
            return null;
        }
        return new BytecodeLocation(this, internalBci);
    }

    public final BytecodeLocation getBytecodeLocation(int internalBci) {
        if (internalBci < 0) {
            return null;
        }
        return findLocation(internalBci);
    }

    /**
     * Gets the {@link SourceSection source location} associated with a particular location. Returns
     * {@code null} if the node was not parsed {@link BytecodeConfig#WITH_SOURCE with sources} or if
     * there is no associated source section for the given location. A location must always be
     * provided to get a source location otherwise <code>null</code> will be returned.
     *
     * @param frame the current frame
     * @param location the current location
     * @return a source section corresponding to the bci, or {@code null} if no source section is
     *         available
     */
    public final SourceSection getSourceLocation(Frame frame, Node location) {
        int internalIndex = findBytecodeIndexImpl(frame, location);
        if (internalIndex == -1) {
            return null;
        }
        return findSourceLocation(internalIndex);
    }

    private int findBytecodeIndexImpl(Frame frame, Node location) {
        Objects.requireNonNull(frame, "Provided frame must not be null.");
        Objects.requireNonNull(location, "Provided location must not be null.");

        Node prev = null;
        BytecodeNode bytecode = null;
        for (Node current = location; current != null; current = current.getParent()) {
            if (current == this) {
                bytecode = this;
                break;
            }
            prev = current;
        }
        if (bytecode == null) {
            return -1;
        }
        int internalIndex = findBytecodeIndex(frame, prev);
        return internalIndex;
    }

    public final SourceSection getSourceLocation(FrameInstance frameInstance) {
        int internalIndex = findBytecodeIndex(frameInstance);
        if (internalIndex == -1) {
            return null;
        }
        return findSourceLocation(internalIndex);
    }

    public abstract BytecodeIntrospection getIntrospectionData();

    /**
     * Sets a threshold that must be reached before the
     * {@link GenerateBytecode#enableUncachedInterpreter uncached interpreter} switches to a cached
     * interpreter. The interpreter can switch to cached when the number of times it returns,
     * yields, and branches backwards exceeds the threshold.
     * <p>
     * This method has no effect if there is no uncached interpreter or the root node has node has
     * already switched to a specializing interpreter.
     *
     * @since 24.1
     */
    public abstract void setUncachedThreshold(int threshold);

    public abstract BytecodeTier getTier();

    public final String dump() {
        return dump(null);
    }

    public final String dump(BytecodeLocation highlighedLocation) {
        if (highlighedLocation != null && highlighedLocation.getBytecodeNode() != this) {
            throw new IllegalArgumentException("Invalid highlighted location. Belongs to a different BytecodeNode.");
        }
        BytecodeIntrospection id = getIntrospectionData();
        List<Instruction> instructions = id.getInstructions();
        List<ExceptionHandler> exceptions = id.getExceptionHandlers();
        List<SourceInformation> sourceInformation = id.getSourceInformation();
        int highlightedBci = highlighedLocation == null ? -1 : highlighedLocation.getBytecodeIndex();
        return String.format("""
                        %s(name=%s)[
                            instructions(%s) = %s
                            exceptionHandlers(%s) = %s
                            sourceInformation(%s) = %s
                        ]""",
                        getClass().getSimpleName(),
                        ((RootNode) getParent()).getQualifiedName(),
                        instructions.size(),
                        formatList(instructions, (i) -> i.getBci() == highlightedBci),
                        exceptions.size(),
                        formatList(exceptions, (e) -> highlightedBci >= e.getStartIndex() && highlightedBci < e.getEndIndex()),
                        sourceInformation != null ? sourceInformation.size() : "-",
                        formatList(sourceInformation, (s) -> highlightedBci >= s.getStartBci() && highlightedBci < s.getEndBci()));
    }

    private static <T> String formatList(List<T> list, Predicate<T> highlight) {
        if (list == null) {
            return "Not Available";
        } else if (list.isEmpty()) {
            return "Empty";
        }
        StringBuilder b = new StringBuilder();
        for (T o : list) {
            if (highlight.test(o)) {
                b.append("\n    ==> ");
            } else {
                b.append("\n        ");
            }
            b.append(o.toString());
        }
        return b.toString();
    }

    protected abstract SourceSection findSourceLocation(int internalBci);

    /**
     * Gets the {@code bci} associated with a particular operation node.
     *
     * Note: this is a slow path operation that gets invoked by Truffle internal code. It should not
     * be called directly. Operation specializations can use {@code @Bind("$bci")} to obtain the
     * current bytecode index on the fast path.
     *
     * This method will be generated by the Bytecode DSL. Do not override.
     *
     * @param operationNode the operation node
     * @return the corresponding bytecode index, or -1 if the index could not be found
     * @since 24.1
     */
    protected abstract int findBytecodeIndex(Frame frame, Node operationNode);

    /**
     * Reads the {@code bci} stored in the frame.
     *
     * This method should only be invoked by the language when
     * {@link GenerateBytecode#storeBciInFrame} is {@code true}, because there is otherwise no
     * guarantee that the {@code bci} will be stored in the frame.
     *
     * Note: When possible, it is preferable to obtain the {@code bci} from Operation
     * specializations using {@code @Bind("$bci")} for performance reasons. This method should only
     * be used by the language when the {@code bci} is needed in non-local contexts (e.g., when the
     * frame has escaped to another root node).
     *
     * This method will be generated by the Bytecode DSL. Do not override.
     *
     * @param frameInstance the frame instance obtained from a stack walk
     * @return the corresponding bytecode index, or -1 if the index could not be found
     * @since 24.1
     */
    protected abstract int findBytecodeIndex(FrameInstance frameInstance);

    protected abstract Instruction findInstruction(int bci);

    protected final BytecodeLocation findLocation(int bci) {
        return new BytecodeLocation(this, bci);
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
    public static BytecodeNode get(FrameInstance frameInstance) {
        Node location = frameInstance.getCallNode();
        for (Node currentNode = location; currentNode != null; currentNode = currentNode.getParent()) {
            if (currentNode instanceof BytecodeNode bytecodeNode) {
                return bytecodeNode;
            }
        }
        CallTarget target = frameInstance.getCallTarget();
        if (target instanceof RootCallTarget rootCallTarget) {
            RootNode rootNode = rootCallTarget.getRootNode();
            if (rootNode instanceof BytecodeRootNode bytecodeRoot) {
                // should only ever happen for a top-frame.
                return bytecodeRoot.getBytecodeNode();
            }
        }
        return null;
    }

}
