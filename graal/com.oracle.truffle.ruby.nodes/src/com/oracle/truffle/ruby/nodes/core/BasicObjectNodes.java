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
import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.objects.*;

@CoreClass(name = "BasicObject")
public abstract class BasicObjectNodes {

    @CoreMethod(names = "!", needsSelf = false, maxArgs = 0)
    public abstract static class NotNode extends CoreMethodNode {

        public NotNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public NotNode(NotNode prev) {
            super(prev);
        }

        @Specialization
        public boolean not() {
            return false;
        }

    }

    @CoreMethod(names = "==", minArgs = 1, maxArgs = 1)
    public abstract static class EqualNode extends CoreMethodNode {

        public EqualNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public EqualNode(EqualNode prev) {
            super(prev);
        }

        @Specialization
        public boolean equal(Object a, Object b) {
            // TODO(CS) ideally all classes would do this in their own nodes
            return a.equals(b);
        }

    }

    @CoreMethod(names = "!=", minArgs = 1, maxArgs = 1)
    public abstract static class NotEqualNode extends CoreMethodNode {

        public NotEqualNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public NotEqualNode(NotEqualNode prev) {
            super(prev);
        }

        @Specialization
        public boolean notEqual(Object a, Object b) {
            // TODO(CS) ideally all classes would do this in their own nodes
            return !a.equals(b);
        }

    }

    @CoreMethod(names = "equal?", minArgs = 1, maxArgs = 1)
    public abstract static class ReferenceEqualNode extends CoreMethodNode {

        public ReferenceEqualNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ReferenceEqualNode(ReferenceEqualNode prev) {
            super(prev);
        }

        @Specialization(order = 1)
        public boolean equal(@SuppressWarnings("unused") NilPlaceholder a, @SuppressWarnings("unused") NilPlaceholder b) {
            return true;
        }

        @Specialization(order = 2)
        public boolean equal(int a, int b) {
            return a == b;
        }

        @Specialization(order = 3)
        public boolean equal(double a, double b) {
            return a == b;
        }

        @Specialization(order = 4)
        public boolean equal(BigInteger a, BigInteger b) {
            return a.compareTo(b) == 0;
        }

        @Specialization(order = 5)
        public boolean equal(RubyBasicObject a, RubyBasicObject b) {
            return a == b;
        }
    }

    @CoreMethod(names = "initialize", needsSelf = false, maxArgs = 0)
    public abstract static class InitializeNode extends CoreMethodNode {

        public InitializeNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public InitializeNode(InitializeNode prev) {
            super(prev);
        }

        @Specialization
        public NilPlaceholder initiailze() {
            return NilPlaceholder.INSTANCE;
        }

    }

    @CoreMethod(names = {"send", "__send__"}, needsSelf = true, needsBlock = true, minArgs = 1, isSplatted = true)
    public abstract static class SendNode extends CoreMethodNode {

        public SendNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public SendNode(SendNode prev) {
            super(prev);
        }

        @Specialization
        public Object send(RubyBasicObject self, Object[] args, @SuppressWarnings("unused") UndefinedPlaceholder block) {
            final String name = args[0].toString();
            final Object[] sendArgs = Arrays.copyOfRange(args, 1, args.length);
            return self.send(name, null, sendArgs);
        }

        @Specialization
        public Object send(RubyBasicObject self, Object[] args, RubyProc block) {
            final String name = args[0].toString();
            final Object[] sendArgs = Arrays.copyOfRange(args, 1, args.length);
            return self.send(name, block, sendArgs);
        }

    }

}
