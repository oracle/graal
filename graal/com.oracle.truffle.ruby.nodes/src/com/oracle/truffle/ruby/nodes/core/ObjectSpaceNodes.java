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

import java.math.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.objects.*;

@CoreClass(name = "ObjectSpace")
public abstract class ObjectSpaceNodes {

    @CoreMethod(names = "_id2ref", isModuleMethod = true, needsSelf = false, minArgs = 1, maxArgs = 1)
    public abstract static class ID2RefNode extends CoreMethodNode {

        public ID2RefNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ID2RefNode(ID2RefNode prev) {
            super(prev);
        }

        @Specialization
        public Object id2Ref(int id) {
            final Object object = getContext().getObjectSpaceManager().lookupId(id);

            if (object == null) {
                return NilPlaceholder.INSTANCE;
            } else {
                return object;
            }
        }

        @Specialization
        public Object id2Ref(BigInteger id) {
            final Object object = getContext().getObjectSpaceManager().lookupId(id.longValue());

            if (object == null) {
                return NilPlaceholder.INSTANCE;
            } else {
                return object;
            }
        }

    }

    @CoreMethod(names = "each_object", isModuleMethod = true, needsSelf = false, needsBlock = true, minArgs = 0, maxArgs = 1)
    public abstract static class EachObjectNode extends YieldingCoreMethodNode {

        public EachObjectNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public EachObjectNode(EachObjectNode prev) {
            super(prev);
        }

        @Specialization
        public NilPlaceholder eachObject(VirtualFrame frame, @SuppressWarnings("unused") UndefinedPlaceholder ofClass, RubyProc block) {
            for (RubyBasicObject object : getContext().getObjectSpaceManager().getObjects()) {
                yield(frame, block, object);
            }
            return NilPlaceholder.INSTANCE;
        }

        @Specialization
        public NilPlaceholder eachObject(VirtualFrame frame, RubyClass ofClass, RubyProc block) {
            for (RubyBasicObject object : getContext().getObjectSpaceManager().getObjects()) {
                if (object.getRubyClass().assignableTo(ofClass)) {
                    yield(frame, block, object);
                }
            }
            return NilPlaceholder.INSTANCE;
        }

    }

    @CoreMethod(names = "define_finalizer", isModuleMethod = true, needsSelf = false, minArgs = 2, maxArgs = 2)
    public abstract static class DefineFinalizerNode extends CoreMethodNode {

        public DefineFinalizerNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public DefineFinalizerNode(DefineFinalizerNode prev) {
            super(prev);
        }

        @Specialization
        public RubyProc defineFinalizer(Object object, RubyProc finalizer) {
            getContext().getObjectSpaceManager().defineFinalizer((RubyBasicObject) object, finalizer);
            return finalizer;
        }
    }

    @CoreMethod(names = {"garbage_collect", "start"}, isModuleMethod = true, needsSelf = false, maxArgs = 0)
    public abstract static class GarbageCollectNode extends CoreMethodNode {

        public GarbageCollectNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public GarbageCollectNode(GarbageCollectNode prev) {
            super(prev);
        }

        @Specialization
        public NilPlaceholder garbageCollect() {
            final RubyThread runningThread = getContext().getThreadManager().leaveGlobalLock();

            try {
                System.gc();
            } finally {
                getContext().getThreadManager().enterGlobalLock(runningThread);
            }

            return NilPlaceholder.INSTANCE;
        }
    }

    @CoreMethod(names = "undefine_finalizer", isModuleMethod = true, needsSelf = false, minArgs = 1, maxArgs = 1)
    public abstract static class UndefineFinalizerNode extends CoreMethodNode {

        public UndefineFinalizerNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public UndefineFinalizerNode(UndefineFinalizerNode prev) {
            super(prev);
        }

        @Specialization
        public Object undefineFinalizer(Object object) {
            getContext().getObjectSpaceManager().undefineFinalizer((RubyBasicObject) object);
            return object;
        }
    }

}
