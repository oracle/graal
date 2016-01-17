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

import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * A listener attached by an {@link Instrumenter} using a {@link SourceSectionFilter} to specific
 * locations of a guest language program. Listeners are shared across all source locations specified
 * by the {@link SourceSectionFilter}. Use event listeners if your instrumentation does not need to
 * store state per source location. If it is recommended to use a {@link EventNodeFactory} instead.
 */
public interface EventListener {

    /**
     * Invoked before an instrumented node is executed. The provided frame is the frame of
     * instrumented node.
     */
    void onEnter(EventContext context, VirtualFrame frame);

    /**
     * Invoked after an instrumented node is successfully executed. The provided frame is the frame
     * of instrumented node.
     */
    void onReturnValue(EventContext context, VirtualFrame frame, Object result);

    /**
     * Invoked after an instrumented node did not successfully execute. The provided frame is the
     * frame of instrumented node.
     */
    void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception);

}
