/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Set;

import com.oracle.truffle.api.frame.Frame;

/**
 * Encapsulates information about a transition of the bytecode interpreter from one bytecode node to
 * another. Transitions can be intercepted by overriding
 * {@link BytecodeRootNode#traceTransition(BytecodeTransition, Frame)}.
 * <p>
 * Transition kinds are not mutually exclusive; for example, a single transition can simultaneously
 * report {@link #isBytecodeUpdate()} and {@link #isTransferToInterpreter()}.
 * <p>
 * Deoptimization transitions report precise bytecode locations for compilation roots and
 * continuation resumes. If a Bytecode DSL root is inlined into another compiled root, a
 * deoptimization in that inlined root may be reported at the enclosing compilation root instead of
 * the inlined root.
 *
 * @since 25.1
 */
public abstract class BytecodeTransition {

    /**
     * Internal constructor for generated code. Do not use.
     *
     * @since 25.1
     */
    protected BytecodeTransition(Object token) {
        BytecodeRootNodes.checkToken(token);
    }

    /**
     * Returns {@code true} if bytecode metadata or instructions changed for this transition, e.g.
     * due to a tier change, instrumentation update, or tag update.
     *
     * @since 25.1
     */
    public abstract boolean isBytecodeUpdate();

    /**
     * Returns {@code true} if this transition was triggered by a deoptimization from compiled code.
     * <p>
     * A deoptimization in an inlined Bytecode DSL root may not create a separate transition for that
     * inlined root. In such cases, the transition may instead be reported for the enclosing
     * compilation root.
     *
     * @since 25.1
     */
    public abstract boolean isTransferToInterpreter();

    /**
     * Returns tags that were newly added by this transition.
     * <p>
     * This set is typically non-empty only for {@link #isBytecodeUpdate()} transitions.
     *
     * @since 25.1
     */
    public abstract Set<Class<?>> getAddedTags();

    /**
     * Returns instrumentations that were newly added by this transition.
     * <p>
     * This set is typically non-empty only for {@link #isBytecodeUpdate()} transitions.
     *
     * @since 25.1
     */
    public abstract Set<Class<?>> getAddedInstrumentations();

    /**
     * Returns the bytecode location before the transition.
     *
     * @since 25.1
     */
    public abstract BytecodeLocation getOldLocation();

    /**
     * Returns the bytecode location after the transition.
     *
     * @since 25.1
     */
    public abstract BytecodeLocation getNewLocation();

    @Override
    public String toString() {
        return "BytecodeTransition [old=" + getOldLocation() + ", new=" + getNewLocation() +
                        ", transferToInterpreter=" + isTransferToInterpreter() +
                        ", bytecodeUpdate=" + isBytecodeUpdate() +
                        ", addedTags=" + getAddedTags() +
                        ", addedInstrumentations=" + getAddedInstrumentations() +
                        "]";
    }

}
