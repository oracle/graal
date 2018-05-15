/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
