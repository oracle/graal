/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.core;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.ruby.nodes.call.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.objects.*;

@CoreClass(name = "Comparable")
public abstract class ComparableNodes {

    public abstract static class ComparableCoreMethodNode extends CoreMethodNode {

        @Child protected DispatchHeadNode compareNode;

        public ComparableCoreMethodNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
            compareNode = adoptChild(new DispatchHeadNode(context, getSourceSection(), "<=>", false));
        }

        public ComparableCoreMethodNode(ComparableCoreMethodNode prev) {
            super(prev);
            compareNode = adoptChild(prev.compareNode);
        }

        public int compare(VirtualFrame frame, RubyBasicObject receiverObject, Object comparedTo) {
            return (int) compareNode.dispatch(frame, receiverObject, null, comparedTo);
        }

    }

    @CoreMethod(names = "<", isModuleMethod = true, minArgs = 1, maxArgs = 1)
    public abstract static class LessNode extends ComparableCoreMethodNode {

        public LessNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public LessNode(LessNode prev) {
            super(prev);
        }

        @Specialization
        public boolean less(VirtualFrame frame, RubyBasicObject self, Object comparedTo) {
            return compare(frame, self, comparedTo) < 0;
        }

    }

    @CoreMethod(names = "<=", isModuleMethod = true, minArgs = 1, maxArgs = 1)
    public abstract static class LessEqualNode extends ComparableCoreMethodNode {

        public LessEqualNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public LessEqualNode(LessEqualNode prev) {
            super(prev);
        }

        @Specialization
        public boolean lessEqual(VirtualFrame frame, RubyBasicObject self, Object comparedTo) {
            return compare(frame, self, comparedTo) <= 0;
        }

    }

    @CoreMethod(names = "==", isModuleMethod = true, minArgs = 1, maxArgs = 1)
    public abstract static class EqualNode extends ComparableCoreMethodNode {

        public EqualNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public EqualNode(EqualNode prev) {
            super(prev);
        }

        @Specialization
        public boolean equal(VirtualFrame frame, RubyBasicObject self, Object comparedTo) {
            if (self == comparedTo) {
                return true;
            }

            try {
                return compare(frame, self, comparedTo) == 0;
            } catch (Exception e) {
                // Comparable#== catches and ignores all exceptions in <=>, returning false
                return false;
            }
        }
    }

    @CoreMethod(names = ">=", isModuleMethod = true, minArgs = 1, maxArgs = 1)
    public abstract static class GreaterEqualNode extends ComparableCoreMethodNode {

        public GreaterEqualNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public GreaterEqualNode(GreaterEqualNode prev) {
            super(prev);
        }

        @Specialization
        public boolean greaterEqual(VirtualFrame frame, RubyBasicObject self, Object comparedTo) {
            return compare(frame, self, comparedTo) >= 0;
        }

    }

    @CoreMethod(names = ">", isModuleMethod = true, minArgs = 1, maxArgs = 1)
    public abstract static class GreaterNode extends ComparableCoreMethodNode {

        public GreaterNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public GreaterNode(GreaterNode prev) {
            super(prev);
        }

        @Specialization
        public boolean greater(VirtualFrame frame, RubyBasicObject self, Object comparedTo) {
            return compare(frame, self, comparedTo) > 0;
        }

    }

    @CoreMethod(names = "between?", isModuleMethod = true, minArgs = 2, maxArgs = 2)
    public abstract static class BetweenNode extends ComparableCoreMethodNode {

        public BetweenNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public BetweenNode(BetweenNode prev) {
            super(prev);
        }

        @Specialization
        public boolean between(VirtualFrame frame, RubyBasicObject self, Object min, Object max) {
            return !(compare(frame, self, min) < 0 || compare(frame, self, max) > 0);
        }

    }

}
