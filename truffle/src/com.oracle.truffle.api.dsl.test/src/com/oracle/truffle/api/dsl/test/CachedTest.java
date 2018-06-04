/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import static com.oracle.truffle.api.dsl.test.TestHelper.assertionsEnabled;
import static com.oracle.truffle.api.dsl.test.TestHelper.createCallTarget;
import static com.oracle.truffle.api.dsl.test.TestHelper.createNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.BoundCacheFactory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.BoundCacheOverflowFactory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.CacheDimensions1Factory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.CacheDimensions2Factory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.CacheNodeWithReplaceFactory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.ChildrenAdoption1Factory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.ChildrenAdoption2Factory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.ChildrenAdoption3Factory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.ChildrenAdoption4Factory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.ChildrenAdoption5Factory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.ChildrenAdoption6Factory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.ChildrenAdoption7Factory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.NullChildAdoptionNodeGen;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.TestBoundCacheOverflowContainsFactory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.TestCacheFieldFactory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.TestCacheMethodFactory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.TestCacheNodeFieldFactory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.TestCachesOrder2Factory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.TestCachesOrderFactory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.TestCodeGenerationPosNegGuardNodeGen;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.TestGuardWithCachedAndDynamicParameterFactory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.TestGuardWithJustCachedParameterFactory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.TestMultipleCachesFactory;
import com.oracle.truffle.api.dsl.test.CachedTestFactory.UnboundCacheFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.dsl.test.examples.ExampleTypes;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInterface;

@SuppressWarnings("unused")
public class CachedTest {

    @Test
    public void testUnboundCache() {
        CallTarget root = createCallTarget(UnboundCacheFactory.getInstance());
        assertEquals(42, root.call(42));
        assertEquals(42, root.call(43));
        assertEquals(42, root.call(44));
    }

    @NodeChild
    static class UnboundCache extends ValueNode {
        @Specialization
        static int do1(int value, @Cached("value") int cachedValue) {
            return cachedValue;
        }
    }

    @Test
    public void testBoundCache() {
        CallTarget root = createCallTarget(BoundCacheFactory.getInstance());
        assertEquals(42, root.call(42));
        assertEquals(43, root.call(43));
        assertEquals(44, root.call(44));
        try {
            root.call(45);
            fail();
        } catch (UnsupportedSpecializationException e) {
        }
    }

    @NodeChild
    static class BoundCache extends ValueNode {

        @Specialization(guards = "value == cachedValue", limit = "3")
        static int do1(int value, @Cached("value") int cachedValue) {
            return cachedValue;
        }

    }

    @Test
    public void testBoundCacheOverflow() {
        CallTarget root = createCallTarget(BoundCacheOverflowFactory.getInstance());
        assertEquals(42, root.call(42));
        assertEquals(43, root.call(43));
        assertEquals(-1, root.call(44));
        assertEquals(42, root.call(42));
        assertEquals(43, root.call(43));
        assertEquals(-1, root.call(44));
    }

    @NodeChild
    static class BoundCacheOverflow extends ValueNode {

        @Specialization(guards = "value == cachedValue", limit = "2")
        static int do1(int value, @Cached("value") int cachedValue) {
            return cachedValue;
        }

        @Specialization
        static int do2(int value) {
            return -1;
        }

    }

    @Test
    public void testBoundCacheOverflowContains() {
        CallTarget root = createCallTarget(TestBoundCacheOverflowContainsFactory.getInstance());
        assertEquals(42, root.call(42));
        assertEquals(43, root.call(43));
        assertEquals(-1, root.call(44));
        assertEquals(-1, root.call(42));
        assertEquals(-1, root.call(43));
        assertEquals(-1, root.call(44));
    }

    @NodeChild
    static class TestBoundCacheOverflowContains extends ValueNode {

        @Specialization(guards = "value == cachedValue", limit = "2")
        static int do1(int value, @Cached("value") int cachedValue) {
            return cachedValue;
        }

        @Specialization(replaces = "do1")
        static int do2(int value) {
            return -1;
        }

    }

    @Test
    public void testCacheField() {
        CallTarget root = createCallTarget(TestCacheFieldFactory.getInstance());
        assertEquals(3, root.call(42));
        assertEquals(3, root.call(43));
    }

    @NodeChild
    static class TestCacheField extends ValueNode {

        protected int field = 3;

        @Specialization()
        static int do1(int value, @Cached("field") int cachedValue) {
            return cachedValue;
        }

    }

    @Test
    public void testCacheNodeField() {
        CallTarget root = createCallTarget(TestCacheNodeFieldFactory.getInstance(), 21);
        assertEquals(21, root.call(42));
        assertEquals(21, root.call(43));
    }

    @NodeChild
    @NodeField(name = "field", type = int.class)
    static class TestCacheNodeField extends ValueNode {

        @Specialization
        static int do1(int value, @Cached("field") int cachedValue) {
            return cachedValue;
        }

    }

    @Test
    public void testCacheNodeWithReplace() {
        CallTarget root = createCallTarget(CacheNodeWithReplaceFactory.getInstance());
        assertEquals(42, root.call(41));
        assertEquals(42, root.call(40));
        assertEquals(42, root.call(39));
    }

    @NodeChild
    static class CacheNodeWithReplace extends ValueNode {

        @Specialization
        static int do1(int value, @Cached("new()") NodeSubClass cachedNode) {
            return cachedNode.execute(value);
        }

    }

    public static class NodeSubClass extends Node {

        private int increment = 1;

        public int execute(int value) {
            replace(new NodeSubClass()).increment = increment + 1;
            return value + increment;
        }

    }

    @Test
    public void testCacheMethod() {
        TestCacheMethod.invocations = 0;
        CallTarget root = createCallTarget(TestCacheMethodFactory.getInstance());
        assertEquals(42, root.call(42));
        assertEquals(42, root.call(43));
        assertEquals(42, root.call(44));
        assertEquals(1, TestCacheMethod.invocations);
    }

    @NodeChild
    static class TestCacheMethod extends ValueNode {

        static int invocations = 0;

        @Specialization
        static int do1(int value, @Cached("someMethod(value)") int cachedValue) {
            return cachedValue;
        }

        static int someMethod(int value) {
            invocations++;
            return value;
        }

    }

    @Test
    public void testGuardWithJustCachedParameter() {
        TestGuardWithJustCachedParameter.invocations = 0;
        CallTarget root = createCallTarget(TestGuardWithJustCachedParameterFactory.getInstance());
        assertEquals(42, root.call(42));
        assertEquals(42, root.call(43));
        assertEquals(42, root.call(44));
        if (assertionsEnabled()) {
            Assert.assertTrue(TestGuardWithJustCachedParameter.invocations >= 3);
        } else {
            assertEquals(1, TestGuardWithJustCachedParameter.invocations);
        }
    }

    @NodeChild
    static class TestGuardWithJustCachedParameter extends ValueNode {

        static int invocations = 0;

        @Specialization(guards = "someMethod(cachedValue)")
        static int do1(int value, @Cached("value") int cachedValue) {
            return cachedValue;
        }

        static boolean someMethod(int value) {
            invocations++;
            return true;
        }

    }

    @Test
    public void testGuardWithCachedAndDynamicParameter() {
        TestGuardWithCachedAndDynamicParameter.cachedMethodInvocations = 0;
        TestGuardWithCachedAndDynamicParameter.dynamicMethodInvocations = 0;
        CallTarget root = createCallTarget(TestGuardWithCachedAndDynamicParameterFactory.getInstance());
        assertEquals(42, root.call(42));
        assertEquals(42, root.call(43));
        assertEquals(42, root.call(44));
        // guards with just cached parameters are just invoked on the slow path
        if (assertionsEnabled()) {
            Assert.assertTrue(TestGuardWithCachedAndDynamicParameter.cachedMethodInvocations >= 3);
        } else {
            assertEquals(1, TestGuardWithCachedAndDynamicParameter.cachedMethodInvocations);
        }
        Assert.assertTrue(TestGuardWithCachedAndDynamicParameter.dynamicMethodInvocations >= 3);
    }

    @NodeChild
    static class TestGuardWithCachedAndDynamicParameter extends ValueNode {

        static int cachedMethodInvocations = 0;
        static int dynamicMethodInvocations = 0;

        @Specialization(guards = {"dynamicMethod(value)", "cachedMethod(cachedValue)"})
        static int do1(int value, @Cached("value") int cachedValue) {
            return cachedValue;
        }

        static boolean cachedMethod(int value) {
            cachedMethodInvocations++;
            return true;
        }

        static boolean dynamicMethod(int value) {
            dynamicMethodInvocations++;
            return true;
        }

    }

    /*
     * Node should not produce any warnings in isIdentical of the generated code. Unnecessary casts
     * were generated for isIdentical on the fast path.
     */
    @NodeChildren({@NodeChild, @NodeChild})
    static class RegressionTestWarningInIsIdentical extends ValueNode {

        @Specialization(guards = {"cachedName == name"})
        protected Object directAccess(String receiver, String name, //
                        @Cached("name") String cachedName, //
                        @Cached("create(receiver, name)") Object callHandle) {
            return receiver;
        }

        protected static Object create(String receiver, String name) {
            return receiver;
        }

    }

    @NodeChild
    static class TestMultipleCaches extends ValueNode {

        @Specialization
        static int do1(int value, @Cached("value") int cachedValue1, @Cached("value") int cachedValue2) {
            return cachedValue1 + cachedValue2;
        }

    }

    @Test
    public void testMultipleCaches() {
        CallTarget root = createCallTarget(TestMultipleCachesFactory.getInstance());
        assertEquals(42, root.call(21));
        assertEquals(42, root.call(22));
        assertEquals(42, root.call(23));
    }

    @NodeChild
    static class TestCachedWithProfile extends ValueNode {

        @Specialization
        static int do1(int value, @Cached("create()") MySubClass mySubclass) {
            return 42;
        }
    }

    public static class MyClass {

        public static MyClass create() {
            return new MyClass();
        }
    }

    public static class MySubClass extends MyClass {

        public static MySubClass create() {
            return new MySubClass();
        }

    }

    @NodeChild
    static class TestCachesOrder extends ValueNode {

        @Specialization(guards = "boundByGuard != 0")
        static int do1(int value, //
                        @Cached("get(value)") int intermediateValue, //
                        @Cached("transform(intermediateValue)") int boundByGuard, //
                        @Cached("new()") Object notBoundByGuards) {
            return intermediateValue;
        }

        protected int get(int i) {
            return i * 2;
        }

        protected int transform(int i) {
            return i * 3;
        }

    }

    @Test
    public void testCachesOrder() {
        CallTarget root = createCallTarget(TestCachesOrderFactory.getInstance());
        assertEquals(42, root.call(21));
        assertEquals(42, root.call(22));
        assertEquals(42, root.call(23));
    }

    @NodeChild
    static class TestCachesOrder2 extends ValueNode {

        @Specialization(guards = "cachedValue == value", limit = "3")
        static int do1(int value, //
                        @Cached("value") int cachedValue,
                        @Cached("get(cachedValue)") int intermediateValue, //
                        @Cached("transform(intermediateValue)") int boundByGuard, //
                        @Cached("new()") Object notBoundByGuards) {
            return intermediateValue;
        }

        protected int get(int i) {
            return i * 2;
        }

        protected int transform(int i) {
            return i * 3;
        }

    }

    @Test
    public void testCachesOrder2() {
        CallTarget root = createCallTarget(TestCachesOrder2Factory.getInstance());
        assertEquals(42, root.call(21));
        assertEquals(44, root.call(22));
        assertEquals(46, root.call(23));
    }

    @TypeSystemReference(ExampleTypes.class)
    abstract static class TestCodeGenerationPosNegGuard extends Node {

        public abstract int execute(Object execute);

        @Specialization(guards = "guard(value)")
        static int do0(int value) {
            return value;
        }

        @Specialization(guards = {"!guard(value)", "value != cachedValue"}, limit = "3")
        static int do1(int value, @Cached("get(value)") int cachedValue) {
            return cachedValue;
        }

        protected static boolean guard(int i) {
            return i == 0;
        }

        protected int get(int i) {
            return i * 2;
        }

    }

    @Test
    public void testCodeGenerationPosNegGuard() {
        TestCodeGenerationPosNegGuard root = TestCodeGenerationPosNegGuardNodeGen.create();
        assertEquals(0, root.execute(0));
        assertEquals(2, root.execute(1));
        assertEquals(4, root.execute(2));
    }

    @NodeChild
    static class CacheDimensions1 extends ValueNode {

        @Specialization(guards = "value == cachedValue", limit = "3")
        static int[] do1(int[] value, //
                        @Cached(value = "value", dimensions = 1) int[] cachedValue) {
            return cachedValue;
        }

    }

    @Test
    public void testCacheDimension1() throws NoSuchFieldException, SecurityException {
        CacheDimensions1 node = TestHelper.createNode(CacheDimensions1Factory.getInstance(), false);
        Field field = node.getClass().getDeclaredField("do1_cache");
        field.setAccessible(true);
        Field cachedField = field.getType().getDeclaredField("cachedValue_");
        cachedField.setAccessible(true);
        assertEquals(1, cachedField.getAnnotation(CompilationFinal.class).dimensions());
    }

    @NodeChild
    static class CacheDimensions2 extends ValueNode {

        @Specialization
        static int[] do1(int[] value, //
                        @Cached(value = "value", dimensions = 1) int[] cachedValue) {
            return cachedValue;
        }

    }

    @Test
    public void testCacheDimension2() throws NoSuchFieldException, SecurityException {
        CacheDimensions2 node = TestHelper.createNode(CacheDimensions2Factory.getInstance(), false);
        Field cachedField = node.getClass().getDeclaredField("do1_cachedValue_");
        cachedField.setAccessible(true);
        assertEquals(1, cachedField.getAnnotation(CompilationFinal.class).dimensions());
    }

    abstract static class NullChildAdoption extends Node {

        abstract Object execute(Object value);

        @Specialization
        static int do1(int value, //
                        @Cached("createNode()") Node cachedValue) {
            return value;
        }

        static Node createNode() {
            return null;
        }

    }

    @Test
    public void testNullChildAdoption() throws NoSuchFieldException, SecurityException {
        NullChildAdoption node;

        node = NullChildAdoptionNodeGen.create();

        // we should be able to return null from nodes.
        node.execute(42);
    }

    @NodeChild
    abstract static class ChildrenAdoption1 extends ValueNode {

        abstract NodeInterface[] execute(Object value);

        @Specialization(guards = "value == cachedValue", limit = "3")
        static NodeInterface[] do1(NodeInterface[] value, @Cached("value") NodeInterface[] cachedValue) {
            return cachedValue;
        }

    }

    @NodeChild
    abstract static class ChildrenAdoption2 extends ValueNode {

        abstract NodeInterface execute(Object value);

        @Specialization(guards = "value == cachedValue", limit = "3")
        static NodeInterface do1(NodeInterface value, @Cached("value") NodeInterface cachedValue) {
            return cachedValue;
        }

    }

    @NodeChild
    abstract static class ChildrenAdoption3 extends ValueNode {

        abstract Node[] execute(Object value);

        @Specialization(guards = "value == cachedValue", limit = "3")
        static Node[] do1(Node[] value, @Cached("value") Node[] cachedValue) {
            return cachedValue;
        }

    }

    @NodeChild
    abstract static class ChildrenAdoption4 extends ValueNode {

        abstract Node execute(Object value);

        @Specialization(guards = "value == cachedValue", limit = "3")
        static Node do1(Node value, @Cached("value") Node cachedValue) {
            return cachedValue;
        }

    }

    @NodeChild
    abstract static class ChildrenAdoption5 extends ValueNode {

        abstract Node[] execute(Object value);

        @Specialization
        static Node[] do1(Node value, @Cached("createChildren(value)") Node[] cachedValue) {
            return cachedValue;
        }

        protected static Node[] createChildren(Node value) {
            return new Node[]{value};
        }

    }

    @NodeChild
    abstract static class ChildrenAdoption6 extends ValueNode {

        abstract NodeInterface execute(Node value);

        @Specialization
        static NodeInterface do1(Node value, @Cached("createChild(value)") NodeInterface cachedValue) {
            return cachedValue;
        }

        protected static NodeInterface createChild(Node node) {
            return node;
        }

    }

    @NodeChild
    abstract static class ChildrenAdoption7 extends ValueNode {

        abstract NodeInterface[] execute(Object value);

        @Specialization
        static NodeInterface[] do1(Node value, @Cached("createChildren(value)") NodeInterface[] cachedValue) {
            return cachedValue;
        }

        protected static NodeInterface[] createChildren(Node value) {
            return new Node[]{value};
        }

    }

    @Test
    public void testChildrenAdoption1() {
        ChildrenAdoption1 root = createNode(ChildrenAdoption1Factory.getInstance(), false);
        Node[] children = new Node[]{new ValueNode()};
        root.execute(children);
        Assert.assertTrue(hasParent(root, children[0]));
    }

    @Test
    public void testChildrenAdoption2() {
        ChildrenAdoption2 root = createNode(ChildrenAdoption2Factory.getInstance(), false);
        Node child = new ValueNode();
        root.execute(child);
        root.adoptChildren();
        Assert.assertTrue(hasParent(root, child));
    }

    @Test
    public void testChildrenAdoption3() {
        ChildrenAdoption3 root = createNode(ChildrenAdoption3Factory.getInstance(), false);
        Node[] children = new Node[]{new ValueNode()};
        root.execute(children);
        Assert.assertTrue(hasParent(root, children[0]));
    }

    @Test
    public void testChildrenAdoption4() {
        ChildrenAdoption4 root = createNode(ChildrenAdoption4Factory.getInstance(), false);
        Node child = new ValueNode();
        root.execute(child);
        Assert.assertTrue(hasParent(root, child));
    }

    @Test
    public void testChildrenAdoption5() {
        ChildrenAdoption5 root = createNode(ChildrenAdoption5Factory.getInstance(), false);
        Node child = new ValueNode();
        root.execute(child);
        Assert.assertTrue(hasParent(root, child));
    }

    @Test
    public void testChildrenAdoption6() {
        ChildrenAdoption6 root = createNode(ChildrenAdoption6Factory.getInstance(), false);
        Node child = new ValueNode();
        root.execute(child);
        Assert.assertTrue(hasParent(root, child));
    }

    @Test
    public void testChildrenAdoption7() {
        ChildrenAdoption7 root = createNode(ChildrenAdoption7Factory.getInstance(), false);
        Node child = new ValueNode();
        root.execute(child);
        Assert.assertTrue(hasParent(root, child));
    }

    private static boolean hasParent(Node parent, Node node) {
        Node current = node != null ? node.getParent() : null;
        while (current != null) {
            if (current == parent) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    @NodeChild
    static class CacheDimensionsError1 extends ValueNode {

        @Specialization(guards = "value == cachedValue")
        static int[] do1(int[] value, //
                        @ExpectError("The cached dimensions attribute must be specified for array types.") @Cached("value") int[] cachedValue) {
            return cachedValue;
        }

    }

    @NodeChild
    static class CacheDimensionsError2 extends ValueNode {

        @Specialization(guards = "value == cachedValue")
        static Node[] do1(Node[] value, //
                        @ExpectError("The dimensions attribute has no affect for the type Node[].") @Cached(value = "value", dimensions = 1) Node[] cachedValue) {
            return cachedValue;
        }

    }

    @NodeChild
    static class CacheDimensionsError3 extends ValueNode {

        @Specialization(guards = "value == cachedValue")
        static NodeInterface[] do1(NodeInterface[] value, //
                        @ExpectError("The dimensions attribute has no affect for the type NodeInterface[].") @Cached(value = "value", dimensions = 1) NodeInterface[] cachedValue) {
            return cachedValue;
        }

    }

    @NodeChild
    static class CachedError1 extends ValueNode {
        @Specialization
        static int do1(int value, @ExpectError("Incompatible return type int. The expression type must be equal to the parameter type double.")//
        @Cached("value") double cachedValue) {
            return value;
        }
    }

    @NodeChild
    static class CachedError2 extends ValueNode {

        // caches are not allowed to make backward references

        @Specialization
        static int do1(int value,
                        @ExpectError("The initializer expression of parameter 'cachedValue1' binds unitialized parameter 'cachedValue2. Reorder the parameters to resolve the problem.") @Cached("cachedValue2") int cachedValue1,
                        @Cached("value") int cachedValue2) {
            return cachedValue1 + cachedValue2;
        }

    }

    @NodeChild
    static class CachedError3 extends ValueNode {

        // cyclic dependency between cached expressions
        @Specialization
        static int do1(int value,
                        @ExpectError("The initializer expression of parameter 'cachedValue1' binds unitialized parameter 'cachedValue2. Reorder the parameters to resolve the problem.") @Cached("cachedValue2") int cachedValue1,
                        @Cached("cachedValue1") int cachedValue2) {
            return cachedValue1 + cachedValue2;
        }

    }

}
