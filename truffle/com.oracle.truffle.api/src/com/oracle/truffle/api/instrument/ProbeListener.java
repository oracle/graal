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
import com.oracle.truffle.api.source.*;

/**
 * An observer of events related to {@link Probe}s: creating and tagging.
 */
public interface ProbeListener {

    /**
     * Notifies that all registered {@link ASTProber}s are about to be applied to a newly
     * constructed AST.
     *
     * @param source source code from which the AST was constructed
     */
    void startASTProbing(Source source);

    /**
     * Notifies that a {@link Probe} has been newly attached to an AST via {@link Node#probe()}.
     * <p>
     * There can be no more than one {@link Probe} at a node; this notification will only be
     * delivered the first time {@linkplain Node#probe() probe()} is called at a particular AST
     * node. There will also be no notification when the AST to which the Probe is attached is
     * cloned.
     */
    void newProbeInserted(Probe probe);

    /**
     * Notifies that a {@link SyntaxTag} has been newly added to the set of tags associated with a
     * {@link Probe} via {@link Probe#tagAs(SyntaxTag, Object)}.
     * <p>
     * The {@linkplain SyntaxTag tags} at a {@link Probe} are a <em>set</em>; this notification will
     * only be delivered the first time a particular {@linkplain SyntaxTag tag} is added at a
     * {@link Probe}.
     * <p>
     * An optional value supplied with {@linkplain Probe#tagAs(SyntaxTag, Object) tagAs(SyntaxTag,
     * Object)} is reported to all listeners, but not stored. As a consequence, the optional value
     * will have no effect at all if the tag had already been added.
     *
     * @param probe where a tag has been added
     * @param tag the tag that has been newly added (subsequent additions of the tag are
     *            unreported).
     * @param tagValue an optional value associated with the tag for the purposes of reporting.
     */
    void probeTaggedAs(Probe probe, SyntaxTag tag, Object tagValue);

    /**
     * Notifies that the application of all registered {@link ASTProber}s to a newly constructed AST
     * has completed.
     *
     * @param source source code from which the AST was constructed
     */
    void endASTProbing(Source source);

}
