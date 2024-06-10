/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Base exception class for bytecode DSL interpreters which provides additional bytecode DSL utility
 * methods like {@link #getBytecodeLocation()} as well as validation of bytecode locations. It is
 * recommended, but not required for bytecode DSL interpreters to use this base class for their
 * guest exceptions.
 *
 * @since 24.1
 */
public abstract class AbstractBytecodeException extends AbstractTruffleException {

    private static final long serialVersionUID = -534184847100559365L;
    private static final int INVALID_BYTECODE_INDEX = -1;

    private final int bytecodeIndex;

    /**
     * Creates an exception with no message or bytecode location.
     *
     * @since 24.1
     */
    public AbstractBytecodeException() {
        super();
        this.bytecodeIndex = INVALID_BYTECODE_INDEX;
    }

    /**
     * Creates an exception with no bytecode location.
     *
     * @since 24.1
     */
    public AbstractBytecodeException(String message) {
        super(message);
        this.bytecodeIndex = INVALID_BYTECODE_INDEX;
    }

    /**
     * Creates an exception from an existing exception.
     *
     * @since 24.1
     */
    public AbstractBytecodeException(AbstractBytecodeException prototype) {
        super(prototype);
        this.bytecodeIndex = prototype.bytecodeIndex;
    }

    /**
     * Creates an exception with the given location.
     *
     * @since 24.1
     */
    public AbstractBytecodeException(Node location, int bytecodeIndex) {
        super(location);
        assert validateBytecodeLocation(location, bytecodeIndex);
        this.bytecodeIndex = bytecodeIndex;
    }

    /**
     * Creates an exception with the given location and message.
     *
     * @since 24.1
     */
    public AbstractBytecodeException(String message, Node location, int bytecodeIndex) {
        super(message, location);
        assert validateBytecodeLocation(location, bytecodeIndex);
        this.bytecodeIndex = bytecodeIndex;
    }

    /**
     * Creates an exception with the given location and message. Limits the length of the captured
     * stack trace.
     *
     * @since 24.1
     */
    public AbstractBytecodeException(String message, Throwable cause, int stackTraceElementLimit, Node location, int bytecodeIndex) {
        super(message, cause, stackTraceElementLimit, location);
        assert validateBytecodeLocation(location, bytecodeIndex);
        this.bytecodeIndex = bytecodeIndex;
    }

    private static boolean validateBytecodeLocation(Node locationNode, int bytecodeIndex) {
        if (locationNode == null) {
            if (bytecodeIndex >= 0) {
                throw new IllegalArgumentException("A non-negative bytecodeIndex was provided but no location node. If a bytecodeIndex is provided a location node must be provided as well.");
            }
        } else {
            BytecodeNode bytecode = BytecodeNode.get(locationNode);
            if (bytecode == null) {
                throw new IllegalArgumentException("A location node was provided that could not resolve a BytecodeNode using BytecodeNode.get(Node).");
            }
            if (bytecodeIndex < 0) {
                throw new IllegalArgumentException(
                                "A non-null location node was provided but a negative bytecodeIndex was provided. " +
                                                "Provide a null location node and a negative bytecodeIndex for no bytecode location or return a positive bytecodeIndex in addition to the non-null locatio node to resolve this.");
            }
            boolean valid = false;
            for (Instruction i : bytecode.getInstructions()) {
                if (i.getBytecodeIndex() == bytecodeIndex) {
                    valid = true;
                    break;
                }
            }
            if (!valid) {
                throw new IllegalArgumentException(
                                "The provided bytecodeIndex does not point to a valid bytecodeIndex of the given bytecode node. A common cause for this is");
            }

        }
        return true;
    }

    /**
     * Returns a bytecode location associated with the exception or <code>null</code> if the
     * exception does not have a location.
     *
     * @since 24.1
     */
    public final BytecodeLocation getBytecodeLocation() {
        Node location = getLocation();
        int bci = this.bytecodeIndex;
        if (location == null || bci < 0) {
            return null;
        }
        return BytecodeLocation.get(getLocation(), this.bytecodeIndex);
    }

    /**
     * Returns a source section associated with the exception or <code>null</code> if the exception
     * does not have a location.
     *
     * @since 24.1
     */
    @Override
    public final SourceSection getEncapsulatingSourceSection() {
        Node location = getLocation();
        int bci = this.bytecodeIndex;
        if (location == null || bci < 0) {
            return null;
        }
        return BytecodeLocation.get(location, bci).getSourceLocation();
    }
}
