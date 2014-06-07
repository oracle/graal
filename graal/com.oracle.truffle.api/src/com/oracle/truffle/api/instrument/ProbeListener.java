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

import com.oracle.truffle.api.source.*;

/**
 * Client for receiving events relate to {@link Probe} management. Does not report AST copying.
 * <p>
 * <strong>Disclaimer:</strong> experimental interface under development.
 *
 * @See Instrumentation
 */
public interface ProbeListener {

    /**
     * Notifies that a newly created (untagged) {@link Probe} has been inserted into a Truffle AST.
     * There will be no notification when an existing {@link Probe} is shared by an AST copy.
     */
    void newProbeInserted(SourceSection location, Probe probe);

    /**
     * Notifies that a (fully constructed) {@link Probe} has been tagged. A subsequent marking with
     * the same tag is idempotent and generates no notification.
     */
    void probeTaggedAs(Probe probe, PhylumTag tag);

}
