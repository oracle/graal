/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * Event node factories are factories of event nodes for a {@link EventContext program location}.
 * The factory might be invoked multiple times for one and the same source location but the location
 * does never change for a particular returned event node.
 *
 * <p>
 * For example it makes sense to register a performance counter on {@link #create(EventContext) }
 * and increment the counter in the {@link ExecutionEventNode} implementation. The counter can be
 * stored as a {@link CompilationFinal compilation final}, so no peak performance overhead persists
 * for looking up the counter on the fast path.
 * </p>
 *
 * @since 0.12
 */
public interface ExecutionEventNodeFactory {

    /**
     * Returns a new instance of {@link ExecutionEventNode} for this particular source location.
     * This method might be invoked multiple times for one particular source location
     * {@link EventContext context}. The implementation must ensure that this is handled
     * accordingly.
     *
     * @param context the current context where this event node should get created.
     * @return a new event node instance, or <code>null</code> for no event node at the location
     * @since 0.12
     */
    ExecutionEventNode create(EventContext context);

}
