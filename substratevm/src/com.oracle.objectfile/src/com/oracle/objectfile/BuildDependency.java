/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.objectfile;

/**
 * A build dependency is a pair of LayoutDecisions (a, b), such that a depends on b.
 */
@SuppressWarnings("serial")
public final class BuildDependency implements Comparable<Object> {

    private static class DuplicateDependencyException extends Exception {

        static final long serialVersionUID = 42;

        BuildDependency existing;

        DuplicateDependencyException(BuildDependency existing) {
            this.existing = existing;
        }
    }

    public static BuildDependency createOrGet(LayoutDecision depending, LayoutDecision dependedOn) {
        try {
            return new BuildDependency(depending, dependedOn);
        } catch (DuplicateDependencyException ex) {
            return ex.existing;
        }
    }

    public final LayoutDecision depending;
    public final LayoutDecision dependedOn;

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BuildDependency)) {
            return false;
        }
        BuildDependency arg = (BuildDependency) obj;
        // we'd better have an identityHashCode with sensible semantics
        assert (!(arg.depending == this.depending && arg.dependedOn == this.dependedOn)) ||
                        (System.identityHashCode(arg.depending) == System.identityHashCode(this.depending) && System.identityHashCode(arg.dependedOn) == System.identityHashCode(this.dependedOn));

        return arg.depending == this.depending && arg.dependedOn == this.dependedOn;
    }

    @Override
    public int hashCode() {
        return depending.hashCode() ^ dependedOn.hashCode();
    }

    @Override
    public int compareTo(Object arg0) {
        if (arg0 == null) {
            throw new NullPointerException();
        }
        // all non-BuildDependency objects are less than us
        if (!(arg0 instanceof BuildDependency)) {
            return -1;
        } else {
            BuildDependency arg = (BuildDependency) arg0;
            int our0 = System.identityHashCode(depending);
            int our1 = System.identityHashCode(dependedOn);
            int their0 = System.identityHashCode(arg.depending);
            int their1 = System.identityHashCode(arg.dependedOn);
            // lexicographic comparison
            if (our0 < their0 || (our0 == their0 && our1 < their1)) {
                return -1;
            } else if (our0 > their0 || (our0 == their0 && our1 > their1)) {
                return 1;
            } else {
                assert our0 == their0 && our1 == their1;
                return 0;
            }
        }
    }

    private BuildDependency(LayoutDecision depending, LayoutDecision dependedOn) throws DuplicateDependencyException {
        assert depending != null;
        assert dependedOn != null;
        assert depending != dependedOn; // 1-cycle is bad (all cycles are bad)
        // must be same file
        assert depending.getElement().getOwner() == dependedOn.getElement().getOwner();
        ObjectFile of = depending.getElement().getOwner();

        this.depending = depending;
        this.dependedOn = dependedOn;

        // avoid adding duplicate entries
        if (depending.dependsOn().contains(dependedOn)) {
            assert dependedOn.dependedOnBy().contains(depending);
            BuildDependency existing = of.getExistingDependency(this);
            assert existing != null;
            throw new DuplicateDependencyException(existing);
        }
        depending.dependsOn().add(dependedOn);
        dependedOn.dependedOnBy().add(depending);
        of.putDependency(this);
    }
}
