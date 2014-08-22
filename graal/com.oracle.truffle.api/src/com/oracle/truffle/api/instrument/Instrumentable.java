/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrument;

import com.oracle.truffle.api.*;

public interface Instrumentable {

    /**
     * Optionally applies <em>instrumentation</em> at a Truffle AST node, depending on guest
     * language characteristics and use-case policy. Ideally, the parent node of the guest language
     * implements this interface.
     * <ul>
     * <li>if no instrumentation is to be applied, returns the AST node unmodified;</li>
     * <li>if an AST node is to be instrumented, then creates a new Wrapper that <em>decorates</em>
     * the AST node. Additionally, this creates a probe on the wrapper that is to be used for
     * attaching instruments. This {@link Probe} is notified of all {@link ExecutionEvents} at the
     * wrapped AST node.</li>
     * </ul>
     *
     * @param context The {@link ExecutionContext} of the guest language used to create probes on
     *            the wrapper.
     * @return The probe that was created.
     */
    public Probe probe(ExecutionContext context);
}
