/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.dsl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.dsl.test.RewriteUnexpectedResultTestFactory.DoubleReplaceNodeGen;
import com.oracle.truffle.api.dsl.test.RewriteUnexpectedResultTestFactory.InlineCacheBoxingOverloadNodeGen;
import com.oracle.truffle.api.dsl.test.RewriteUnexpectedResultTestFactory.InlineCacheWithGenericBoxingOverloadNodeGen;
import com.oracle.truffle.api.dsl.test.RewriteUnexpectedResultTestFactory.InlinedBoxingOverloadNodeGen;
import com.oracle.truffle.api.dsl.test.RewriteUnexpectedResultTestFactory.PrimitiveOverloadNodeGen;
import com.oracle.truffle.api.dsl.test.RewriteUnexpectedResultTestFactory.RewriteUnexpected3NodeGen;
import com.oracle.truffle.api.dsl.test.RewriteUnexpectedResultTestFactory.RewriteUnexpectedForwardNodeGen;
import com.oracle.truffle.api.dsl.test.RewriteUnexpectedResultTestFactory.RewriteUnexpectedMultipleExceptionsFactory;
import com.oracle.truffle.api.dsl.test.RewriteUnexpectedResultTestFactory.RewriteUnexpectedNoReexecuteFactory;
import com.oracle.truffle.api.dsl.test.RewriteUnexpectedResultTestFactory.SharedCacheNodeGen;
import com.oracle.truffle.api.dsl.test.RewriteUnexpectedResultTestFactory.SimpleBoxingOverloadNodeGen;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public class RewriteUnexpectedResultTest {

    @SuppressWarnings("truffle-unexpected-result-rewrite")
    abstract static class FailureFromImplicitObject extends Node {

        public abstract int executeInt(Object value);

        @ExpectError("Implicit 'Object' return type from UnexpectedResultException not compatible with generic type 'int'.")
        @Specialization(rewriteOn = UnexpectedResultException.class)
        int f1(int a) throws UnexpectedResultException {
            if (a == 5) {
                throw new UnexpectedResultException("foo");
            }
            return a + 1;
        }

        @Specialization
        int f2(int a) {
            return a + 2;
        }
    }

    abstract static class FailureFromObjectReturn extends Node {

        public abstract Object executeInt(Object value);

        @ExpectError("A specialization with return type 'Object' cannot throw UnexpectedResultException.")
        @Specialization(rewriteOn = UnexpectedResultException.class)
        Object f1(int a) throws UnexpectedResultException {
            if (a == 5) {
                throw new UnexpectedResultException("foo");
            }
            return 1;
        }

        @Specialization
        Object f2(int a) {
            return a + 2;
        }
    }

    private static final class NonRepeatableNode extends ValueNode {
        private int counter = 0;

        @Override
        public Object execute(VirtualFrame frame) {
            return counter++;
        }
    }

    @Test
    public void testNoReexecute() throws UnexpectedResultException {
        RewriteUnexpectedNoReexecute node = RewriteUnexpectedNoReexecuteFactory.create(new NonRepeatableNode());

        Assert.assertEquals(200, node.execute(null));
        Assert.assertEquals(102, node.executeInt(null));
        Assert.assertEquals("bar4", node.execute(null));
        Assert.assertEquals("bar6", node.execute(null));
    }

    @NodeChild("a")
    abstract static class RewriteUnexpectedNoReexecute extends ValueNode {
        @Child private NonRepeatableNode child = new NonRepeatableNode();

        @Specialization(rewriteOn = UnexpectedResultException.class)
        int f1(int a) throws UnexpectedResultException {
            int value = a + (int) child.execute(null);
            if (value >= 4) {
                throw new UnexpectedResultException("foo" + value);
            }
            return value + 100;
        }

        @Specialization(replaces = "f1")
        Object f2(int a) {
            int value = a + (int) child.execute(null);
            if (value >= 4) {
                return "bar" + value;
            }
            return value + 200;
        }
    }

    @Test
    public void testMultipleExceptions() {
        RewriteUnexpectedMultipleExceptions node1 = RewriteUnexpectedMultipleExceptionsFactory.create(false, new NonRepeatableNode());

        Assert.assertEquals(200, node1.execute(null));
        Assert.assertEquals(202, node1.execute(null));
        Assert.assertEquals("bar4", node1.execute(null));
        Assert.assertEquals("bar6", node1.execute(null));

        // with an IllegalArgumentException, "child" is re-executed
        RewriteUnexpectedMultipleExceptions node2 = RewriteUnexpectedMultipleExceptionsFactory.create(true, new NonRepeatableNode());

        Assert.assertEquals(200, node2.execute(null));
        Assert.assertEquals(202, node2.execute(null));
        assertThrows(IllegalArgumentException.class, () -> node2.executeInt(null));
        Assert.assertEquals("bar6", node2.execute(null));
    }

    @NodeChild("a")
    abstract static class RewriteUnexpectedMultipleExceptions extends ValueNode {

        private final boolean throwIllegal;
        @Child private NonRepeatableNode child = new NonRepeatableNode();

        protected RewriteUnexpectedMultipleExceptions(boolean throwIllegal) {
            this.throwIllegal = throwIllegal;
        }

        @Specialization(rewriteOn = {UnexpectedResultException.class, IllegalArgumentException.class})
        int f1(int a) throws UnexpectedResultException, IllegalArgumentException {
            int value = a + (int) child.execute(null);
            if (value >= 4) {
                if (throwIllegal) {
                    throw new IllegalArgumentException();
                } else {
                    throw new UnexpectedResultException("foo" + value);
                }
            }
            return value + 100;
        }

        @Specialization(replaces = "f1")
        Object f2(int a) {
            int value = a + (int) child.execute(null);
            if (value >= 4) {
                return "bar" + value;
            }
            return value + 200;
        }
    }

    @Test
    public void testIncompatibleResult() {
        RewriteUnexpected3 node = RewriteUnexpected3NodeGen.create();

        Assert.assertEquals(200, node.execute(0));
        Assert.assertEquals(202, node.execute(1));
        UnexpectedResultException e = assertThrows(UnexpectedResultException.class, () -> node.executeInt(2));
        Assert.assertEquals("foo4", e.getResult());
        Assert.assertEquals(206, node.execute(3));
    }

    abstract static class RewriteUnexpected3 extends Node {
        @Child private NonRepeatableNode child = new NonRepeatableNode();

        public abstract Object execute(Object value);

        public abstract int executeInt(Object value) throws UnexpectedResultException;

        @Specialization(rewriteOn = UnexpectedResultException.class)
        int f1(int a) throws UnexpectedResultException {
            int value = a + (int) child.execute(null);
            if (value >= 4) {
                throw new UnexpectedResultException("foo" + value);
            }
            return value + 100;
        }

        @Specialization(replaces = "f1")
        Object f2(int a) {
            int value = a + (int) child.execute(null);
            return value + 200;
        }
    }

    @Test
    public void testForward() {
        RewriteUnexpectedForward node = RewriteUnexpectedForwardNodeGen.create();

        Assert.assertEquals(200, node.execute(0));
        Assert.assertEquals(202, node.execute(1));
        UnexpectedResultException e = assertThrows(UnexpectedResultException.class, () -> node.executeInt(2));
        Assert.assertEquals("foo4", e.getResult());
        Assert.assertEquals(206, node.execute(3));
    }

    abstract static class RewriteUnexpectedForward extends Node {
        @Child private NonRepeatableNode child = new NonRepeatableNode();

        public abstract Object execute(Object value);

        public abstract int executeInt(Object value) throws UnexpectedResultException;

        @Specialization(rewriteOn = UnexpectedResultException.class)
        int f1(int a) throws UnexpectedResultException {
            int value = a + (int) child.execute(null);
            if (value >= 4) {
                throw new UnexpectedResultException("foo" + value);
            }
            return value + 100;
        }

        @Specialization(replaces = "f1")
        Object f2(int a) {
            int value = a + (int) child.execute(null);
            return value + 200;
        }
    }

    @Test
    public void testSimpleBoxingOverload() throws UnexpectedResultException {
        SimpleBoxingOverloadNode node = SimpleBoxingOverloadNodeGen.create();

        // normally you would expect 42 but since
        // doInt is a boxing overload for doGeneric its not triggered with
        // the generic execute method
        assertEquals("generic42", node.executeGeneric(42));
        // doInt will only be used
        assertEquals(42, node.executeInt(42));
    }

    @GenerateInline(false)
    abstract static class SimpleBoxingOverloadNode extends BaseNode {

        // this is detected as a boxing overload of doInt because:
        // 1) its rewriting itself using UnexpectedResultException
        // 2) its replaced by a more generic specialization
        // 3) the generic specialization has exactly the same guards and cached fields
        @Specialization(rewriteOn = UnexpectedResultException.class)
        int doInt(Object arg) throws UnexpectedResultException {
            if (arg instanceof Integer) {
                return 42;
            }
            throw new UnexpectedResultException(arg);
        }

        @Specialization(replaces = "doInt")
        @TruffleBoundary
        Object doGeneric(Object arg) {
            return "generic" + arg;
        }

    }

    @Test
    public void testInlineCacheBoxingOverload() throws UnexpectedResultException {
        InlineCacheBoxingOverloadNode node = InlineCacheBoxingOverloadNodeGen.create();

        Object o1 = 42;
        Object o2 = "43";
        Object o3 = 43;

        assertEquals(o1, node.executeGeneric(o1));
        assertEquals(o2, node.executeGeneric(o2));
        assertEquals(o1, node.executeInt(o1));
        UnexpectedResultException e = assertThrows(UnexpectedResultException.class, () -> node.executeInt(o2));
        assertEquals(o2, e.getResult());
        assertEquals(o2, node.executeGeneric(o2));
        assertEquals(o3, node.executeInt(o3));

        // inline cache full.
        assertThrows(UnsupportedSpecializationException.class, () -> node.executeInt(44));
        assertThrows(UnsupportedSpecializationException.class, () -> node.executeGeneric(44));
    }

    @GenerateInline(false)
    @SuppressWarnings("unused")
    abstract static class InlineCacheBoxingOverloadNode extends BaseNode {

        @Specialization(guards = "arg == cachedArg", limit = "3", rewriteOn = UnexpectedResultException.class)
        int doInt(Object arg, @Cached("arg") Object cachedArg) throws UnexpectedResultException {
            if (cachedArg instanceof Integer i) {
                return 42;
            }
            throw new UnexpectedResultException(cachedArg);
        }

        @Specialization(guards = "arg == cachedArg", limit = "3", replaces = "doInt")
        Object doGeneric(Object arg, @Cached("arg") Object cachedArg) {
            return cachedArg;
        }

    }

    @Test
    public void testInlinedBoxingOverloadNode() throws UnexpectedResultException {
        InlinedBoxingOverloadNode node = InlinedBoxingOverloadNodeGen.create();

        Object o1 = 42;
        Object o2 = "43";
        Object o3 = 43;

        assertEquals(o1, node.executeGeneric(o1));
        assertEquals(o2, node.executeGeneric(o2));
        assertEquals(o1, node.executeInt(o1));
        UnexpectedResultException e = assertThrows(UnexpectedResultException.class, () -> node.executeInt(o2));
        assertEquals(o2, e.getResult());
        assertEquals(o2, node.executeGeneric(o2));
        assertEquals(o3, node.executeInt(o3));

        // inline cache full.
        assertThrows(UnsupportedSpecializationException.class, () -> node.executeInt(44));
        assertThrows(UnsupportedSpecializationException.class, () -> node.executeGeneric(44));
    }

    @GenerateInline(false)
    @SuppressWarnings("unused")
    abstract static class InlineCacheWithGenericBoxingOverloadNode extends BaseNode {

        @Specialization(guards = "arg == cachedArg", limit = "3", rewriteOn = UnexpectedResultException.class)
        static int doIntCached(Object arg, @Cached("arg") Object cachedArg) throws UnexpectedResultException {
            if (cachedArg instanceof Integer i) {
                return 42;
            }
            throw new UnexpectedResultException(cachedArg);
        }

        @Specialization(guards = "arg == cachedArg", limit = "3", replaces = {"doIntCached"})
        static Object doObjectCached(Object arg, @Cached("arg") Object cachedArg) {
            return cachedArg;
        }

        @Specialization(replaces = {"doIntCached", "doObjectCached"}, rewriteOn = UnexpectedResultException.class)
        static int doIntGeneric(Object arg) throws UnexpectedResultException {
            if (arg instanceof Integer i) {
                return 43;
            }
            throw new UnexpectedResultException(arg);
        }

        @Specialization(replaces = {"doIntGeneric", "doIntCached", "doObjectCached"})
        static Object doGeneric(Object arg) {
            return 44;
        }
    }

    @Test
    public void testInlineCacheWithGenericBoxingOverloadNode() throws UnexpectedResultException {
        InlineCacheWithGenericBoxingOverloadNode node = InlineCacheWithGenericBoxingOverloadNodeGen.create();

        Object o1 = 42;
        Object o2 = "43";
        Object o3 = 43;

        assertEquals(o1, node.executeGeneric(o1));
        assertEquals(o2, node.executeGeneric(o2));
        assertEquals(o1, node.executeInt(o1));
        UnexpectedResultException e = assertThrows(UnexpectedResultException.class, () -> node.executeInt(o2));
        assertEquals(o2, e.getResult());
        assertEquals(o2, node.executeGeneric(o2));
        assertEquals(o3, node.executeInt(o3));

        // inline cache full -> generic executeAndSpecialize
        assertEquals(44, node.executeGeneric(44));

        assertEquals(43, node.executeInt(44));
        assertEquals(44, node.executeGeneric(44));
    }

    @GenerateInline(false)
    @SuppressWarnings("unused")
    abstract static class InlinedBoxingOverloadNode extends BaseNode {

        @Specialization(guards = "arg == cachedArg", limit = "3", rewriteOn = UnexpectedResultException.class)
        static int doInt(Object arg, @Cached("arg") Object cachedArg, @Bind Node node, @Shared @Cached InlinableNode inlinableNode) throws UnexpectedResultException {
            return inlinableNode.executeInt(node, cachedArg);
        }

        @Specialization(guards = "arg == cachedArg", limit = "3", replaces = "doInt")
        static Object doGeneric(Object arg, @Cached("arg") Object cachedArg, @Bind Node node, @Shared @Cached InlinableNode inlinableNode) {
            return cachedArg;
        }

    }

    @Test
    public void testSharedCache() throws UnexpectedResultException {
        SharedCache node = SharedCacheNodeGen.create();

        assertEquals("", node.executeGeneric(""));
        assertThrows(UnexpectedResultException.class, () -> node.executeInt(42));
        assertThrows(UnexpectedResultException.class, () -> node.executeInt(""));

        SharedCache node2 = SharedCacheNodeGen.create();

        assertEquals(42, node2.executeGeneric(42));
        assertEquals(42, node2.executeGeneric(""));
        assertEquals(42, node2.executeInt(42));
        assertEquals(42, node2.executeInt(""));
    }

    @GenerateInline(false)
    @SuppressWarnings("unused")
    abstract static class SharedCache extends BaseNode {

        @Specialization(rewriteOn = UnexpectedResultException.class)
        static int doInt(Object arg, @Shared @Cached("createCache(arg)") Object sharedArg) throws UnexpectedResultException {
            if (sharedArg instanceof Integer i) {
                return i;
            }
            throw new UnexpectedResultException(arg);
        }

        @Specialization(replaces = "doInt")
        static Object doGeneric(Object arg, @Shared @Cached("createCache(arg)") Object sharedArg) {
            return sharedArg;
        }

        @NeverDefault
        static Object createCache(Object arg) {
            return arg;
        }

    }

    /*
     * Boxing overloads does never make sense for primitive return types. As we first need to return
     * a generic Object type to find out which type to test for boxing elimination.
     */
    @Test
    public void testPrimitiveOverloadNode() throws UnexpectedResultException {
        PrimitiveOverloadNode node = PrimitiveOverloadNodeGen.create();

        assertEquals(41, node.executeGeneric(41));
        assertEquals(41, node.executeInt(41));

        assertThrows(UnexpectedResultException.class, () -> node.executeInt(41L));
        assertEquals(42L, node.executeGeneric(41L));
    }

    @GenerateInline(false)
    @SuppressWarnings("unused")
    abstract static class PrimitiveOverloadNode extends BaseNode {

        @Specialization(rewriteOn = UnexpectedResultException.class)
        protected static int doUncachedIntLength(Object target) throws UnexpectedResultException {
            if (target instanceof Integer i) {
                return i;
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new UnexpectedResultException(target);
        }

        @Specialization(replaces = {"doUncachedIntLength"})
        protected static long doUncachedLongLength(Object target) {
            return 42L;
        }
    }

    /*
     * Tests that boxing overloads still work if the boxing overload replaces a specialization.
     */
    @Test
    public void testDoubleReplaceNode() throws UnexpectedResultException {
        DoubleReplaceNode node = DoubleReplaceNodeGen.create();

        assertEquals(41, node.executeInt(41));
        assertEquals(42, node.executeInt(41L));
        assertEquals(41, node.executeInt(41L));
        assertEquals(42, node.executeGeneric(41L));
    }

    @GenerateInline(false)
    @SuppressWarnings("unused")
    abstract static class DoubleReplaceNode extends BaseNode {

        @Specialization
        protected static int doDefault(int target) {
            return target;
        }

        @Specialization(replaces = "doDefault", rewriteOn = UnexpectedResultException.class)
        protected static int doInt(Object target) throws UnexpectedResultException {
            if (target instanceof Long i) {
                return i.intValue();
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new UnexpectedResultException(target);
        }

        @Specialization(replaces = {"doInt"})
        protected static Object doGeneric(Object target) {
            if (target instanceof Long i) {
                return i.intValue() + 1;
            }
            return target;
        }
    }

    @GenerateInline(false)
    @SuppressWarnings("unused")
    abstract static class MultipleReplacesCache extends BaseNode {

        @Specialization(rewriteOn = UnexpectedResultException.class)
        static int doInt(int arg, @Shared @Cached("createCache(arg)") Object sharedArg) throws UnexpectedResultException {
            if (sharedArg instanceof Integer i) {
                return i;
            }
            throw new UnexpectedResultException(arg);
        }

        @Specialization(replaces = "doInt", rewriteOn = UnexpectedResultException.class)
        static double doDouble(int arg, @Shared @Cached("createCache(arg)") Object sharedArg) throws UnexpectedResultException {
            if (sharedArg instanceof Double i) {
                return i;
            }
            throw new UnexpectedResultException(arg);
        }

        @Specialization(replaces = "doDouble")
        static Object doGeneric(int arg, @Shared @Cached("createCache(arg)") Object sharedArg) {
            return sharedArg;
        }

        @NeverDefault
        static Object createCache(Object arg) {
            return arg;
        }

    }

    @GenerateInline(false)
    @SuppressWarnings("unused")
    abstract static class VoidTestNode extends BaseNode {

        @Specialization(rewriteOn = UnexpectedResultException.class)
        static void doVoid(int arg, @Shared @Cached("createCache(arg)") Object sharedArg) throws UnexpectedResultException {
            if (sharedArg instanceof Integer i) {
                return;
            }
            throw new UnexpectedResultException(arg);
        }

        @Specialization(replaces = "doVoid")
        static Object doGeneric(int arg, @Shared @Cached("createCache(arg)") Object sharedArg) {
            return sharedArg;
        }

        @NeverDefault
        static Object createCache(Object arg) {
            return arg;
        }
    }

    @GenerateInline(false)
    @SuppressWarnings("unused")
    abstract static class DoubleOverloadNode extends BaseNode {

        @Specialization(rewriteOn = UnexpectedResultException.class)
        static int doInt1(int arg, @Shared @Cached("createCache(arg)") Object sharedArg) throws UnexpectedResultException {
            if (sharedArg instanceof Integer i) {
                return i;
            }
            throw new UnexpectedResultException(arg);
        }

        @ExpectError("The given boxing overload specialization shadowed by 'RewriteUnexpectedResultTest.DoubleOverloadNode.doInt1(int, Object)' and is never used. Remove this specialization to resolve this.%")
        @Specialization(rewriteOn = UnexpectedResultException.class)
        static int doInt2(int arg, @Shared @Cached("createCache(arg)") Object sharedArg) throws UnexpectedResultException {
            throw new AssertionError(arg);
        }

        @Specialization(replaces = {"doInt1", "doInt2"})
        static Object doGeneric(int arg, @Shared @Cached("createCache(arg)") Object sharedArg) {
            return sharedArg;
        }

        @NeverDefault
        static Object createCache(Object arg) {
            return arg;
        }

    }

    /*
     * Warning if specialization is replaced but signature does not match exactly.
     */
    @GenerateInline(false)
    @SuppressWarnings("unused")
    @AlwaysGenerateOnlySlowPath // avoids problems with error emission in slow-path only mode
    abstract static class WarnBoxingOverload1Node extends BaseNode {

        @Specialization(guards = "arg == cachedArg", limit = "3", rewriteOn = UnexpectedResultException.class)
        static int doInt(Object arg, @Cached("arg") Object cachedArg, @Bind Node node, @Cached InlinableNode inlinableNode) throws UnexpectedResultException {
            return inlinableNode.executeInt(node, cachedArg);
        }

        @ExpectError("The specialization 'doInt(Object, Object, Node, InlinableNode)' throws an UnexpectedResultException and is replaced by this specialization but their signature, " +
                        "guards or cached state are not compatible with each other so it cannot be used for boxing elimination. It is recommended to align the specializations to resolve this.")
        @Specialization(guards = "arg == cachedArg", limit = "3", replaces = "doInt")
        Object doGeneric(Object arg, @Cached("arg") Object cachedArg) {
            return cachedArg;
        }

    }

    /*
     * Warning if specialization siganture match but the specialization is not replaced.
     */
    @GenerateInline(false)
    @SuppressWarnings({"unused", "truffle-sharing"})
    abstract static class WarnBoxingOverload2Node extends BaseNode {

        @ExpectError("This specialization throws an UnexpectedResultException and is replaced by the 'doGeneric(Object, Object, Node, InlinableNode)' specialization but their signature, " +
                        "guards or cached state are not compatible with each other so it cannot be used for boxing elimination. It is recommended to align the specializations to resolve this.")
        @Specialization(guards = "arg == cachedArg", limit = "3", rewriteOn = UnexpectedResultException.class)
        static int doInt(Object arg, @Cached("arg") Object cachedArg, @Bind Node node, @Cached InlinableNode inlinableNode) throws UnexpectedResultException {
            return inlinableNode.executeInt(node, cachedArg);
        }

        @ExpectError("The specialization 'doInt(Object, Object, Node, InlinableNode)' throws an UnexpectedResultException and is compatible for boxing elimination but the specialization does not replace it. " +
                        "It is recommmended to specify a @Specialization(..., replaces=\"doInt\") attribute to resolve this.")
        @Specialization(guards = "arg == cachedArg", limit = "3")
        static Object doGeneric(Object arg, @Cached("arg") Object cachedArg, @Bind Node node,
                        @Cached InlinableNode inlinableNode) {
            return cachedArg;
        }

    }

    @GenerateInline(true)
    @GenerateCached(false)
    abstract static class InlinableNode extends Node {

        public abstract Object execute(Node inliningContext, Object parameter);

        public abstract int executeInt(Node inliningContext, Object parameter) throws UnexpectedResultException;

        @Specialization(rewriteOn = UnexpectedResultException.class)
        int doInt(Object arg) throws UnexpectedResultException {
            if (arg instanceof Integer) {
                return 42;
            }
            throw new UnexpectedResultException(arg);
        }

        @Specialization(replaces = "doInt")
        @TruffleBoundary
        Object doGeneric(Object arg) {
            return arg;
        }

    }

    @GenerateInline(true)
    @GenerateCached(false)
    abstract static class ErrorNode extends Node {

        public abstract Object execute(Node inliningContext, Object parameter);

        public abstract int executeInt(Node inliningContext, Object parameter) throws UnexpectedResultException;

        @Specialization(rewriteOn = UnexpectedResultException.class)
        int doInt(Object arg) throws UnexpectedResultException {
            if (arg instanceof Integer) {
                return 42;
            }
            throw new UnexpectedResultException(arg);
        }

    }

    abstract static class BaseNode extends Node {

        abstract Object executeGeneric(Object arg);

        int executeInt(Object arg) throws UnexpectedResultException {
            Object o = executeGeneric(arg);
            if (o instanceof Integer i) {
                return i;
            }
            throw new UnexpectedResultException(o);
        }

        void executeVoid(Object arg) throws UnexpectedResultException {
            Object o = executeGeneric(arg);
            if (o instanceof Integer) {
                return;
            }
            throw new UnexpectedResultException(o);
        }

        double executeDouble(Object arg) throws UnexpectedResultException {
            Object o = executeGeneric(arg);
            if (o instanceof Integer i) {
                return i;
            }
            throw new UnexpectedResultException(o);
        }

    }
}
