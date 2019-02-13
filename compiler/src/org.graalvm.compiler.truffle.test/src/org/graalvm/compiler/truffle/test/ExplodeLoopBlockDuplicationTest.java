/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public class ExplodeLoopBlockDuplicationTest extends TestWithSynchronousCompiling {

    /*
     * Test that polymorphic caches duplicate the cached block and can therefore resolve the
     * abstract method call and resolve the result to a constant.
     */
    @Test
    public void testBlockDuplication() {
        OptimizedCallTarget target = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(new ObjectCacheTestRootNode());
        AbstractType value1 = new ConcreteType1();
        AbstractType value2 = new ConcreteType2();
        target.call(value1);
        target.call(value2);

        target.compile(true);
        assertCompiled(target);
        target.call(value1);
        target.call(value2);

        assertCompiled(target);
    }

    final class ObjectCacheTestRootNode extends RootNode {

        protected ObjectCacheTestRootNode() {
            super(null);
        }

        @Child ObjectCacheTestNodeGen test = new ObjectCacheTestNodeGen();

        @Override
        public Object execute(VirtualFrame frame) {
            Object result = test.execute(frame.getArguments()[0]);
            if (!CompilerDirectives.isCompilationConstant(result)) {
                throw new AssertionError();
            }
            return result;
        }

    }

    private static final class ObjectCacheTestNodeGen extends Node {

        @CompilationFinal private CachedData cached;

        @ExplodeLoop(kind = LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
        public Object execute(Object arg0Value) {
            if ((arg0Value instanceof AbstractType)) {
                AbstractType arg0Value0 = (AbstractType) arg0Value;
                CachedData s1 = cached;
                while (s1 != null) {
                    if ((arg0Value0 == s1.cachedOperand)) {
                        return s1.cachedOperand.someMethod();
                    }
                    s1 = s1.next;
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return executeAndSpecialize(arg0Value);
        }

        private synchronized Object executeAndSpecialize(Object arg0Value) {
            if (arg0Value instanceof AbstractType) {
                AbstractType arg0Value0 = (AbstractType) arg0Value;
                CachedData s1 = cached;
                AbstractType cachedOperand = (arg0Value0);
                s1 = new CachedData(cached, cachedOperand);
                this.cached = s1;
                return s1.cachedOperand.someMethod();
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new UnsupportedSpecializationException(this, new Node[]{});
        }

        private static final class CachedData {

            final CachedData next;
            final AbstractType cachedOperand;

            CachedData(CachedData next, AbstractType cachedOperand) {
                this.next = next;
                this.cachedOperand = cachedOperand;
            }

        }
    }

    private abstract static class AbstractType {

        abstract Object someMethod();

    }

    private static final Object CONSTANT_OBJECT = new Object();

    private static final class ConcreteType1 extends AbstractType {

        @Override
        Object someMethod() {
            return CONSTANT_OBJECT;
        }

    }

    private static final class ConcreteType2 extends AbstractType {

        @Override
        Object someMethod() {
            return CONSTANT_OBJECT;
        }

    }

}
