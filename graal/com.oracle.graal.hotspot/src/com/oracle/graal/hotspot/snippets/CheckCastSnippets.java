/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.snippets;
import com.oracle.graal.graph.Node.Fold;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.snippets.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;


public class CheckCastSnippets implements SnippetsInterface {

    @Snippet
    public static Object checkcast(Object hub, Object object, Object[] hintHubs, boolean hintsAreExact) {
        if (object == null) {
            return object;
        }
        Object objectHub = UnsafeLoadNode.load(object, 0, hubOffset(), CiKind.Object);
        // if we get an exact match: succeed immediately
        for (int i = 0; i < hintHubs.length; i++) {
            Object hintHub = hintHubs[i];
            if (hintHub == objectHub) {
                return object;
            }
        }
        if (!hintsAreExact && TypeCheckSlowPath.check(objectHub, hub) == null) {
            DeoptimizeNode.deopt(RiDeoptAction.InvalidateReprofile, RiDeoptReason.ClassCastException);
        }
        return object;
    }

    @Fold
    private static int hubOffset() {
        return CompilerImpl.getInstance().getConfig().hubOffset;
    }

    @Fold
    private static int klassOopOffset() {
        return CompilerImpl.getInstance().getConfig().klassOopOffset;
    }
}
