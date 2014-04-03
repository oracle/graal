/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle;

import java.util.*;

import com.oracle.truffle.api.*;

public final class TruffleCallPath implements Iterable<TruffleCallPath>, Comparable<TruffleCallPath> {

    private final OptimizedCallNode callNode;
    private final TruffleCallPath parent;
    private final OptimizedCallTarget rootCallTarget;

    public TruffleCallPath(OptimizedCallTarget parent) {
        this.rootCallTarget = Objects.requireNonNull(parent);
        this.callNode = null;
        this.parent = null;
    }

    public TruffleCallPath(TruffleCallPath parent, OptimizedCallNode node) {
        this.parent = Objects.requireNonNull(parent);
        assert !parent.containsPath(this) : "cyclic path detected";
        this.callNode = Objects.requireNonNull(node);
        this.rootCallTarget = parent.getRootCallTarget();
    }

    public boolean isRoot() {
        return parent == null && rootCallTarget != null;
    }

    private boolean containsPath(TruffleCallPath p) {
        return p == this || (getParent() != null && this.getParent().containsPath(p));
    }

    public OptimizedCallTarget getRootCallTarget() {
        return rootCallTarget;
    }

    @Override
    public String toString() {
        return (parent != null ? (parent.toString() + " -> ") : "") + getCallTarget().toString();
    }

    public Iterator<TruffleCallPath> iterator() {
        return toList().iterator();
    }

    public OptimizedCallTarget getCallTarget() {
        return parent == null ? rootCallTarget : callNode.getCurrentCallTarget();
    }

    public List<TruffleCallPath> toList() {
        List<TruffleCallPath> list = new ArrayList<>();
        toListImpl(list);
        return list;
    }

    private void toListImpl(List<TruffleCallPath> list) {
        if (parent != null) {
            parent.toListImpl(list);
        }
        list.add(this);
    }

    public TruffleCallPath getParent() {
        return parent;
    }

    public OptimizedCallNode getCallNode() {
        return callNode;
    }

    public int getDepth() {
        return parent == null ? 0 : (parent.getDepth() + 1);
    }

    public int compareTo(TruffleCallPath o) {
        return Objects.compare(this, o, new PathComparator());
    }

    private static class PathComparator implements Comparator<TruffleCallPath> {
        public int compare(TruffleCallPath c1, TruffleCallPath c2) {
            if (c1 == c2) {
                return 0;
            }

            Iterator<TruffleCallPath> p1 = c1.toList().iterator();
            Iterator<TruffleCallPath> p2 = c2.toList().iterator();

            int cmp = 0;
            while (cmp == 0 && (p1.hasNext() || p2.hasNext())) {
                TruffleCallPath o1;
                TruffleCallPath o2;
                if (p1.hasNext()) {
                    o1 = p1.next();
                } else {
                    return -1;
                }
                if (p2.hasNext()) {
                    o2 = p2.next();
                } else {
                    return 1;
                }

                if (o1 == o2) {
                    continue;
                }

                SourceSection s1;
                if (o1.callNode != null) {
                    s1 = o1.callNode.getEncapsulatingSourceSection();
                } else {
                    s1 = o1.getCallTarget().getRootNode().getSourceSection();
                }

                SourceSection s2;
                if (o2.callNode != null) {
                    s2 = o2.callNode.getEncapsulatingSourceSection();
                } else {
                    s2 = o2.getCallTarget().getRootNode().getSourceSection();
                }
                cmp = compareSourceSection(s2, s1);

                if (cmp == 0) {
                    cmp = o1.getCallTarget().toString().compareTo(o2.getCallTarget().toString());
                }
            }
            return cmp;
        }
    }

    private static int compareSourceSection(SourceSection s1, SourceSection s2) {
        return Objects.compare(s1, s2, new Comparator<SourceSection>() {
            public int compare(SourceSection o1, SourceSection o2) {
                if (o1 == o2) {
                    return 0;
                }
                if (o1 == null || o2 == null) {
                    return 0;
                }
                int cmp = 0;
                if (o1.getSource() != null && o2.getSource() != null && !Objects.equals(o1.getSource().getName(), o2.getSource().getName())) {
                    cmp = o2.getSource().getName().compareTo(o1.getSource().getName());
                }
                if (cmp == 0) {
                    cmp = o2.getStartLine() - o1.getStartLine();
                }
                if (cmp == 0) {
                    cmp = o2.getStartColumn() - o1.getStartColumn();
                }
                return cmp;
            }
        });
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((callNode == null) ? 0 : callNode.hashCode());
        result = prime * result + ((parent == null) ? 0 : parent.hashCode());
        result = prime * result + ((rootCallTarget == null) ? 0 : rootCallTarget.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TruffleCallPath) {
            TruffleCallPath other = (TruffleCallPath) obj;
            if (other.callNode != callNode) {
                return false;
            }
            if (!Objects.equals(other.parent, parent)) {
                return false;
            }
            if (!Objects.equals(other.rootCallTarget, rootCallTarget)) {
                return false;
            }
            return true;
        }
        return false;
    }

    public TruffleCallPath append(TruffleCallPath path) {
        if (getCallTarget() != path.getRootCallTarget()) {
            throw new IllegalArgumentException("Pathes are not compatible and can therfore not be appended.");
        }

        TruffleCallPath append = this;
        for (TruffleCallPath childPath : path) {
            if (!childPath.isRoot()) {
                append = new TruffleCallPath(append, childPath.getCallNode());
            }
        }
        return append;
    }

}
