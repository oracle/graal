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
 * {@linkplain Instrument#create(AdvancedInstrumentResultListener, AdvancedInstrumentRootFactory, String)
 * Advanced Instrument}.
 *
 * @see Instrument
 * @see AdvancedInstrumentRoot
 */
public interface AdvancedInstrumentRootFactory {

    /**
     * Provider of {@linkplain AdvancedInstrumentRoot AST fragment} instances for efficient
     * execution via instrumentation, subject to full Truffle optimization, at a {@linkplain Probe
     * Probed} site in a guest-language AST.
     *
     * @param probe the Probe to which the Instrument requesting the AST fragment is attached
     * @param node the guest-language AST location that is the context in which the requesting
     *            Instrument must execute the AST fragment.
     * @return a newly created AST fragment suitable for execution, via instrumentation, in the
     *         execution context of the specified guest-language AST site.
     */
    AdvancedInstrumentRoot createInstrumentRoot(Probe probe, Node node);
}
