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
import com.oracle.truffle.ruby.runtime.*;

@CoreClass(name = "Math")
public abstract class MathNodes {

    @CoreMethod(names = "sqrt", isModuleMethod = true, needsSelf = false, minArgs = 1, maxArgs = 1)
    public abstract static class SqrtNode extends CoreMethodNode {

        public SqrtNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public SqrtNode(SqrtNode prev) {
            super(prev);
        }

        @Specialization
        public double sqrt(int a) {
            return Math.sqrt(a);
        }

        @Specialization
        public double sqrt(BigInteger a) {
            return Math.sqrt(a.doubleValue());
        }

        @Specialization
        public double sqrt(double a) {
            return Math.sqrt(a);
        }

    }

    @CoreMethod(names = "exp", isModuleMethod = true, needsSelf = false, minArgs = 1, maxArgs = 1)
    public abstract static class ExpNode extends CoreMethodNode {

        public ExpNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public ExpNode(ExpNode prev) {
            super(prev);
        }

        @Specialization
        public double exp(int a) {
            return Math.exp(a);
        }

        @Specialization
        public double exp(BigInteger a) {
            return Math.exp(a.doubleValue());
        }

        @Specialization
        public double exp(double a) {
            return Math.exp(a);
        }

    }

}
