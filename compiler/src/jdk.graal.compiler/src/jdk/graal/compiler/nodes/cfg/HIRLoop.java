/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.cfg;

import jdk.graal.compiler.core.common.cfg.Loop;
import jdk.graal.compiler.nodes.LoopBeginNode;
import org.graalvm.word.LocationIdentity;

public final class HIRLoop extends Loop<HIRBlock> {

    private LocationSet killLocations;

    protected HIRLoop(Loop<HIRBlock> parent, int index, HIRBlock header) {
        super(parent, index, header);
    }

    @Override
    public int numBackedges() {
        return ((LoopBeginNode) getHeader().getBeginNode()).loopEnds().count();
    }

    public LocationSet getKillLocations() {
        if (killLocations == null) {
            killLocations = new LocationSet();
            for (HIRBlock b : this.getBlocks()) {
                if (b.getLoop() == this) {
                    killLocations.addAll(b.getKillLocations());
                    if (killLocations.isAny()) {
                        break;
                    }
                }
            }
        }
        for (Loop<HIRBlock> child : this.getChildren()) {
            if (killLocations.isAny()) {
                break;
            }
            killLocations.addAll(((HIRLoop) child).getKillLocations());
        }
        return killLocations;
    }

    public boolean canKill(LocationIdentity location) {
        return getKillLocations().contains(location);
    }

    @Override
    public String toString() {
        return super.toString() + " header:" + getHeader().getBeginNode();
    }
}
