/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.filter.profiles.spi;

import jdk.graal.compiler.graphio.parsing.model.GraphContainer;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import org.graalvm.visualizer.filter.profiles.FilterProfile;
import org.openide.util.Lookup;

/**
 * SPI, which determines whether a FilterProfile is suitable for
 * a graph.
 *
 * @author sdedic
 */
public interface ProfileGraphMatcher {
    /**
     * The matcher rejected the graph.
     */
    public static final int REJECT = -1;

    /**
     * Returns a weighted match, or a priority for the given graph. Should return
     * {@link #REJECT} if the profile should not be used.
     *
     * @param profile the profile
     * @param gr      graph instance
     * @param parent  the container used to present the graph
     * @param context additional context
     * @return priority for the profile.
     */
    public int matchesInputGraph(FilterProfile profile, InputGraph gr, GraphContainer parent, Lookup context);
}
