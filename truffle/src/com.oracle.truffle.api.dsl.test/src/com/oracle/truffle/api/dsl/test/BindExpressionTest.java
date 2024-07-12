/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Introspectable;
import com.oracle.truffle.api.dsl.Introspection;
import com.oracle.truffle.api.dsl.Introspection.SpecializationInfo;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.dsl.test.BindExpressionTestFactory.BindBindsCacheNodeGen;
import com.oracle.truffle.api.dsl.test.BindExpressionTestFactory.BindCachedNodeTestNodeGen;
import com.oracle.truffle.api.dsl.test.BindExpressionTestFactory.BindFieldNodeGen;
import com.oracle.truffle.api.dsl.test.BindExpressionTestFactory.BindInLimitNodeGen;
import com.oracle.truffle.api.dsl.test.BindExpressionTestFactory.BindMethodNodeGen;
import com.oracle.truffle.api.dsl.test.BindExpressionTestFactory.BindMethodTwiceNodeGen;
import com.oracle.truffle.api.dsl.test.BindExpressionTestFactory.BindNodeFieldNodeGen;
import com.oracle.truffle.api.dsl.test.BindExpressionTestFactory.BindThisMultipleInstancesTestNodeGen;
import com.oracle.truffle.api.dsl.test.BindExpressionTestFactory.BindThisTestNodeGen;
import com.oracle.truffle.api.dsl.test.BindExpressionTestFactory.BindTransitiveCachedInAssumptionNodeGen;
import com.oracle.truffle.api.dsl.test.BindExpressionTestFactory.BindTransitiveCachedInLimitNodeGen;
import com.oracle.truffle.api.dsl.test.BindExpressionTestFactory.BindTransitiveCachedWithLibraryNodeGen;
import com.oracle.truffle.api.dsl.test.BindExpressionTestFactory.BindTransitiveDynamicAndCachedNodeGen;
import com.oracle.truffle.api.dsl.test.BindExpressionTestFactory.BindTransitiveDynamicNodeGen;
import com.oracle.truffle.api.dsl.test.BindExpressionTestFactory.BindTransitiveDynamicWithLibraryNodeGen;
import com.oracle.truffle.api.dsl.test.BindExpressionTestFactory.DefaultBindingNodeGen;
import com.oracle.truffle.api.dsl.test.BindExpressionTestFactory.IntrospectableNodeGen;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

@SuppressWarnings({"truffle-inlining", "truffle-neverdefault", "truffle-sharing", "unused"})
public class BindExpressionTest extends AbstractPolyglotTest {

    static class TestObject {

        Object storage = new Object();
        Assumption assumption = Truffle.getRuntime().createAssumption();

        int counter = 0;

        int bind() {
            return counter++;
        }
    }

    /*
     * Test that storage field extraction works. Unfortunately here we cannot check how many times a
     * field is read.
     */
    @Test
    public void testBindField() {
        BindFieldNode node = BindFieldNodeGen.create();
        TestObject o = new TestObject();
        node.execute(o);

        assertFails(() -> node.execute(new TestObject()), UnsupportedSpecializationException.class);
    }

    abstract static class BindFieldNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization(guards = "storage == cachedStorage", limit = "1")
        Object s0(TestObject a0,
                        @Bind("a0.storage") Object storage,
                        @Cached("storage") Object cachedStorage) {
            assertSame(storage, cachedStorage);
            assertSame(a0.storage, storage);
            return a0;
        }
    }

    /*
     * This test verifies that the extract expression is invoked exactly once. Even though it is
     * used multiple times.
     */
    @Test
    public void testBindMethod() {
        BindMethodNode node = BindMethodNodeGen.create();
        TestObject o = new TestObject();
        node.execute(o);

        assertFails(() -> node.execute(o), UnsupportedSpecializationException.class);
    }

    abstract static class BindMethodNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization(guards = "counter == 0")
        Object s0(TestObject a0,
                        @Bind("a0.bind()") int counter,
                        @Cached("counter") int cachedCounter) {
            assertEquals(0, counter);
            assertEquals(0, cachedCounter);
            return a0;
        }
    }

    /*
     * This test verifies that the expression is invoked once even if used multiple times in the
     * guard.
     */
    @Test
    public void testBindMethodTwice() {
        BindMethodTwiceNode node = BindMethodTwiceNodeGen.create();
        TestObject o = new TestObject();
        node.execute(o);
        node.execute(o);
        assertFails(() -> node.execute(o), UnsupportedSpecializationException.class);
    }

    abstract static class BindMethodTwiceNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization(guards = "counter == 0 || counter == 1")
        Object s0(TestObject a0,
                        @Bind("a0.bind()") int counter,
                        @Cached("counter") int cachedCounter) {
            assertEquals(0, cachedCounter);
            return a0;
        }
    }

    /*
     * Tests that if a guard binds a cached expression indirectly through extract it is not actually
     * executed multiple times.
     */
    @Test
    public void testBindBindsCache() {
        BindBindsCacheNode node = BindBindsCacheNodeGen.create();
        TestObject o = new TestObject();
        node.execute(o);
        node.execute(o);
        node.execute(o);
    }

    abstract static class BindBindsCacheNode extends Node {

        abstract Object execute(Object arg0);

        @ExpectError("The limit expression has no effect.%")
        // this guard is trivially true but the DSL cannot detect that yet.
        @Specialization(guards = "counter == cachedCounter", limit = "3")
        Object s0(TestObject a0,
                        @Cached("0") int cachedCounter,
                        @Bind("cachedCounter") int counter) {
            assertEquals(0, counter);
            assertEquals(0, cachedCounter);
            return a0;
        }
    }

    /*
     * We need to make sure introspectable does not include bind parameters.
     */
    @Test
    public void testIntrospectable() {
        IntrospectableNode node = IntrospectableNodeGen.create();
        TestObject o = new TestObject();
        node.execute(o);
        List<SpecializationInfo> infos = Introspection.getSpecializations(node);
        assertEquals(1, infos.size());
        for (SpecializationInfo info : infos) {
            assertEquals(1, info.getInstances());
            List<Object> cachedData = info.getCachedData(0);
            assertEquals(1, cachedData.size());
            assertSame(o.storage, cachedData.iterator().next());
        }
    }

    @Introspectable
    abstract static class IntrospectableNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization(guards = "storage == cachedStorage", limit = "2")
        Object s0(TestObject a0,
                        @Bind("a0.storage") Object storage,
                        @Cached("storage") Object cachedStorage) {
            assertSame(storage, cachedStorage);
            assertSame(a0.storage, storage);
            return a0;
        }

    }

    /*
     * This test verifies that an extract expression can be used in a limit expression if it only
     * binds cached values.
     */
    @Test
    public void testBindInLimit() {
        BindInLimitNode node = BindInLimitNodeGen.create();
        TestObject o = new TestObject();
        node.execute(o);
        node.execute(o);
        assertFails(() -> node.execute(o), UnsupportedSpecializationException.class);
    }

    abstract static class BindInLimitNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization(guards = "counter == cachedCounter", limit = "limit")
        Object s0(TestObject a0,
                        @Bind("a0.bind()") int counter,
                        @Bind("2") int limit,
                        @Cached("counter") int cachedCounter) {
            assertEquals(cachedCounter, counter);
            assertEquals(cachedCounter, cachedCounter);
            return a0;
        }
    }

    /*
     * This test verifies that an extract expression can be chained transitively and its dynamic
     * nature is preserved.
     */
    @Test
    public void testBindTransitiveDynamic() {
        BindTransitiveDynamicNode node = BindTransitiveDynamicNodeGen.create();
        TestObject o = new TestObject();
        node.execute(o);
        node.execute(o);
        assertFails(() -> node.execute(o), UnsupportedSpecializationException.class);
    }

    abstract static class BindTransitiveDynamicNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization(guards = "counter4 == cachedCounter", limit = "2")
        Object s0(TestObject a0,
                        @Bind("a0.bind()") int counter1,
                        @Bind("counter1") int counter2,
                        @Bind("counter2") int counter3,
                        @Bind("counter3") int counter4,
                        @Cached("counter3") int cachedCounter) {
            return a0;
        }
    }

    /*
     * This test verifies that an extract expression can be chained transitively and its dynamic
     * nature is preserved.
     */
    @Test
    public void testBindTransitiveDynamicAndCachedNode() {
        BindTransitiveDynamicAndCachedNode node = BindTransitiveDynamicAndCachedNodeGen.create();
        TestObject o = new TestObject();
        node.execute(o);
        node.execute(o);
        assertFails(() -> node.execute(o), UnsupportedSpecializationException.class);
    }

    abstract static class BindTransitiveDynamicAndCachedNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization(guards = "counter1 == counter2", limit = "2")
        Object s0(TestObject a0,
                        @Bind("a0.bind()") int counter1,
                        @Cached("counter1") int cachedCounter,
                        @Bind("cachedCounter") int counter2) {
            return a0;
        }
    }

    /*
     * This test verifies that an extract expression can be chained transitively and its dynamic
     * nature is preserved when used with a library.
     */
    @Test
    public void testBindTransitiveDynamicWithLibrary() {
        BindTransitiveDynamicWithLibraryNode node = adoptNode(BindTransitiveDynamicWithLibraryNodeGen.create()).get();
        TestObject o = new TestObject();
        node.execute(o);
        node.execute(o);
        assertFails(() -> node.execute(o), UnsupportedSpecializationException.class);
    }

    abstract static class BindTransitiveDynamicWithLibraryNode extends Node {

        abstract Object execute(Object arg0);

        // this should not trigger a warning
        @Specialization(guards = "counter2 < 2", limit = "3")
        Object s0(TestObject a0,
                        @Bind("a0.bind()") int counter1,
                        @Bind("counter1") int counter2,
                        @CachedLibrary("counter2") InteropLibrary lib) {
            assertTrue(lib.isNumber(counter2));
            return a0;
        }
    }

    /*
     * This test verifies that an extract expression can be chained transitively and its cached
     * nature is preserved when used with a library.
     */
    @Test
    public void testBindTransitiveCachedWithLibrary() {
        BindTransitiveCachedWithLibraryNode node = BindTransitiveCachedWithLibraryNodeGen.create();
        TestObject o = new TestObject();
        node.execute(o);
        node.execute(o);
    }

    abstract static class BindTransitiveCachedWithLibraryNode extends Node {

        abstract Object execute(Object arg0);

        @ExpectError("The limit expression has no effect.%")
        @Specialization(limit = "3")
        Object s0(TestObject a0,
                        @Cached("a0.bind()") int cachedCounter,
                        @Bind("cachedCounter") int counter1,
                        @Bind("counter1") int counter2,
                        @CachedLibrary("counter2") InteropLibrary lib) {
            assertTrue(lib.isNumber(counter2));
            return a0;
        }
    }

    @Test
    public void testBindTransitiveCachedInLimit() {
        BindTransitiveCachedInLimitNode node = BindTransitiveCachedInLimitNodeGen.create();
        TestObject o = new TestObject();
        node.execute(o);
        node.execute(o);
        assertFails(() -> node.execute(o), UnsupportedSpecializationException.class);
    }

    abstract static class BindTransitiveCachedInLimitNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization(guards = "cachedBind == counter", limit = "extractCachedLimit2")
        Object s0(TestObject a0,
                        @Bind("a0.bind()") int counter,
                        @Cached("counter") int cachedBind,
                        @Cached("2") int cachedLimit,
                        @Bind("cachedLimit") int extractCachedLimit1,
                        @Bind("extractCachedLimit1") int extractCachedLimit2) {
            assertEquals(2, cachedLimit);
            return a0;
        }
    }

    @Test
    public void testBindTransitiveCachedInAssumption() {
        BindTransitiveCachedInAssumptionNode node = BindTransitiveCachedInAssumptionNodeGen.create();
        TestObject o = new TestObject();
        node.execute(o);
        node.execute(o);
        o.assumption.invalidate();
        assertFails(() -> node.execute(o), UnsupportedSpecializationException.class);
    }

    /*
     * Test use of assumption is allowed for transitive extracted cached values.
     */
    @SuppressWarnings("truffle-assumption")
    abstract static class BindTransitiveCachedInAssumptionNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization(assumptions = "extractAssumption2")
        Object s0(TestObject a0,
                        @Cached(value = "a0.assumption", neverDefault = true) Assumption assumption,
                        @Bind("assumption") Assumption extractAssumption1,
                        @Bind("extractAssumption1") Assumption extractAssumption2) {
            return a0;
        }
    }

    @Test
    public void testBindNodeField() {
        BindNodeFieldNode node = BindNodeFieldNodeGen.create(2);
        TestObject o = new TestObject();
        assertEquals(2, node.execute(o));
        assertEquals(2, node.execute(o));
    }

    @NodeField(name = "field0", type = int.class)
    abstract static class BindNodeFieldNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization
        int s0(TestObject a0,
                        @Bind("field0") int field) {
            return field;
        }
    }

    @Test
    public void testBindThis() {
        BindThisTest node = BindThisTestNodeGen.create();
        node.execute();
    }

    abstract static class BindThisTest extends Node {

        abstract void execute();

        @Specialization
        void s0(@Bind Node thisNode) {
            assertSame(this, thisNode);
        }
    }

    abstract static class BindThisParentTest extends Node {

        abstract void execute();

        @Specialization
        void s0(@Bind("$node.getParent()") Node thisNode) {
            assertSame(this.getParent(), thisNode);
        }
    }

    @Test
    public void testBindThisMultipleInstances() {
        BindThisMultipleInstancesTest node = BindThisMultipleInstancesTestNodeGen.create();
        node.execute(42);
        node.execute(43);
        node.execute(44);
        node.execute(45);
    }

    abstract static class BindThisMultipleInstancesTest extends Node {

        abstract void execute(int arg);

        @Specialization(guards = "arg == cachedArg", limit = "2")
        void s0(int arg,
                        @Cached("arg") int cachedArg,
                        @Bind Node thisNode) {
            /*
             * The specialization does not bind nodes therefore it returns the current node instead
             * of the specialization class.
             */
            assertSame(this, this);
        }

        @SuppressWarnings("truffle-static-method")
        @Specialization(guards = "arg == cachedArg", limit = "2")
        void s1(int arg,
                        @Cached("arg") int cachedArg,
                        @Cached InlinedBranchProfile branchProfile,
                        @Bind Node thisNode) {
            /*
             * The specialization does not bind nodes therefore it returns the current node instead
             * of the specialization class.
             */
            branchProfile.enter(thisNode);
            assertNotSame(this, thisNode);
            assertSame(thisNode.getParent(), this);
        }
    }

    @Test
    public void testBindCachedNodeTest() {
        BindCachedNodeTest node = adoptNode(BindCachedNodeTestNodeGen.create()).get();
        TestObject o = new TestObject();
        assertEquals("42", node.execute("42"));
    }

    abstract static class BoundTestNode extends Node {

        abstract Object execute(Object arg);

        @Specialization
        Object s0(Object arg) {
            return arg;
        }

    }

    abstract static class BindCachedNodeTest extends Node {

        static int LIMIT = 0;

        abstract Object execute(Object arg0);

        @Specialization(limit = "LIMIT")
        Object s0(Object arg0,
                        @Cached BoundTestNode testNode,
                        @Bind("testNode.execute(arg0)") Object result,
                        @CachedLibrary("result") InteropLibrary lib) {
            try {
                return lib.asString(result);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

    }

    @SuppressWarnings("truffle-assumption")
    abstract static class ErrorUseInAssumptionsNode extends Node {

        abstract Object execute(Object arg0);

        @ExpectError("Assumption expressions must not bind dynamic parameter values.")
        @Specialization(assumptions = "assumption")
        Object s0(TestObject a0,
                        @Bind("a0.assumption") Assumption assumption) {
            return a0;
        }
    }

    abstract static class ErrorUseInLimitNode extends Node {

        abstract Object execute(Object arg0);

        @ExpectError("Limit expressions must not bind dynamic parameter values.")
        @Specialization(guards = "counter == cachedCounter", limit = "counter")
        Object s0(TestObject a0,
                        @Bind("a0.bind()") int counter,
                        @Cached("counter") int cachedCounter) {
            assertEquals(1, counter);
            assertEquals(1, cachedCounter);
            return a0;
        }
    }

    abstract static class ErrorCyclicUseNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization
        Object s0(TestObject a0,
                        @Bind("a0.bind()") int extract,
                        @ExpectError("The initializer expression of parameter 'counter1' binds uninitialized parameter 'counter2. Reorder the parameters to resolve the problem.")//
                        @Bind("counter2") int counter1,
                        @Bind("counter1") int counter2) {
            return a0;
        }
    }

    abstract static class ErrorSyntaxNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization
        Object s0(TestObject a0,
                        @ExpectError("Error parsing expression 'asdf32': asdf32 cannot be resolved.")//
                        @Bind("asdf32") int extract) {
            return a0;
        }
    }

    abstract static class ErrorEmptyNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization
        Object s0(TestObject a0,
                        @ExpectError("Error parsing expression '': line 1:0 mismatched input '<EOF>'%")//
                        @Bind("") int extract) {
            return a0;
        }
    }

    /*
     * We don't want that static node constructors are looked up for extract annotations. This
     * should only work for Cached.
     */
    abstract static class ErrorUseNodeNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization
        Object s0(TestObject a0,
                        @ExpectError("Error parsing expression 'create()': The method create is undefined for the enclosing scope.")//
                        @Bind("create()") BindFieldNode node) {
            return a0;
        }

    }

    abstract static class ErrorBindThisWithCachedTest extends Node {

        abstract void execute();

        @Specialization
        void s0(@ExpectError("Cannot use 'this' with @Cached use @Bind instead.") //
        @Cached("this") Node thisNode) {
        }

    }

    abstract static class WarningRedundantBindingTest1 extends Node {

        abstract Object execute();

        @Specialization
        Object s0(
                        @ExpectError("Bind expression '$node' is redundant and can be automatically be resolved from the parameter type.%") //
                        @Bind Node result) {
            return null;
        }

    }

    abstract static class WarningRedundantBindingTest2 extends Node {

        abstract Object execute();

        @Specialization
        Object s0(
                        @ExpectError("Bind expression 'INSTANCE' is redundant and can be automatically be resolved from the parameter type.%") //
                        @Bind("INSTANCE") DefaultBindType result) {
            return result;
        }

    }

    @Test
    public void testDefaultBinding() {
        DefaultBindingNode node = adoptNode(DefaultBindingNodeGen.create()).get();
        assertSame(DefaultBindType.INSTANCE, node.execute());
    }

    @Bind.DefaultExpression("INSTANCE")
    static class DefaultBindType {

        static final DefaultBindTypeSubclass INSTANCE = new DefaultBindTypeSubclass();
    }

    static class NoBindingType {
    }

    abstract static class DefaultBindingNode extends Node {

        abstract Object execute();

        @Specialization
        Object s0(@Bind DefaultBindType result) {
            return result;
        }

    }

    static class DefaultBindTypeSubclass extends DefaultBindType {

    }

    abstract static class DefaultBindingSubclassNode extends Node {

        abstract Object execute();

        @Specialization
        Object s0(@Bind DefaultBindTypeSubclass result) {
            return result;
        }

    }

    @Test
    public void testDefaultBindingSubclass() {
        DefaultBindingNode node = adoptNode(DefaultBindingNodeGen.create()).get();
        assertSame(DefaultBindType.INSTANCE, node.execute());
    }

    abstract static class ErrorNoDefaultBindingTestNode extends Node {

        abstract Object execute();

        @Specialization
        Object s0(
                        @ExpectError("No expression specified for @Bind annotation and no @DefaultExpression could be resolved from the parameter type.%") //
                        @Bind NoBindingType result) {
            return result;
        }

    }

    abstract static class BindRootNodeTestNode extends Node {

        abstract Object execute();

        @Specialization
        Object s0(
                        @ExpectError("Incompatible return type Node. The expression type must be equal to the parameter type RootNode.") //
                        @Bind RootNode result) {
            return result;
        }

    }

    abstract static class BindNodeTestNode extends Node {

        abstract Object execute();

        @Specialization
        Object s0(@Bind Node result) {
            return result;
        }

    }

    abstract static class BindThisWarningTestNode extends Node {

        abstract Object execute();

        @Specialization
        Object s0(
                        @ExpectError("This expression binds variable 'this' which should no longer be used. Use the '$node' variable instead to resolve this warning.%")//
                        @Bind("this") Node result) {
            return result;
        }

    }

}
