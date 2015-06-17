/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.nodes.*;

/**
 * Creator of {@linkplain AdvancedInstrumentRoot AST fragments} suitable for efficient execution,
 * subject to full Truffle optimization, by an
 * {@linkplain Instrument#create(AdvancedInstrumentResultListener, AdvancedInstrumentRootFactory, Class, String)
 * Advanced Instrument}.
 *
 * @see Instrument
 * @see AdvancedInstrumentRoot
 */
public interface AdvancedInstrumentRootFactory {

    /**
     * Provider of {@linkplain AdvancedInstrumentRoot AST fragment} instances to be executed by the
     * Instrumentation Framework at a {@linkplain Probe Probed} site in a guest-language AST.
     * <p>
     * <strong>Notes:</strong>
     * <ul>
     * <li>Once the factory has produced an AST fragment at a particular {@linkplain Node AST Node},
     * it will not be called again at that Node.</li>
     * <li>In some use cases, for example to implement a breakpoint at a specific program location,
     * the Probe argument will be the same for every call. Each Node argument will represent the
     * same program location associated with the Probe, but in different clones of the AST.</li>
     * <li>In other use cases, for example to implement a breakpoint at any Node with a particular
     * {@linkplain SyntaxTag tag}, both the Probe and Node argument may differ. Implementations that
     * are sensitive to the lexical context in which the AST fragment will be evaluated must take
     * care to build a new, possibly different AST fragment for each request.</li>
     * </ul>
     *
     * @param probe the Probe to which the Instrument requesting the AST fragment is attached
     * @param node the guest-language AST location that is the context in which the requesting
     *            Instrument must execute the AST fragment.
     * @return a newly created AST fragment suitable for execution, via instrumentation, in the
     *         execution context of the specified guest-language AST site.
     */
    AdvancedInstrumentRoot createInstrumentRoot(Probe probe, Node node);
}
