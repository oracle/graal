/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.ObjectType;

public class LibrarySplittingStrategyTest extends AbstractSplittingStrategyTest {

    private static DynamicObject newInstance() {
        return Layout.createLayout().createShape(new SplittingObjectType()).newInstance();
    }

    @Before
    public void boostBudget() {
        createDummyTargetsToBoostGrowingSplitLimit();
    }

    @Test
    public void testCachedLibWithValue() {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) runtime.createCallTarget(
                        new SplittingTestRootNode(LibrarySplittingStrategyTestFactory.CachesLibOnValueNodeGen.create(
                                        new ReturnsFirstArgumentNode())));
        Object[] first = new Object[]{1};
        Object[] second = new Object[]{"2"};
        testSplitsDirectCallsHelper(callTarget, first, second);
    }

    @Test
    public void testCachedLibWithValueExclude() {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) runtime.createCallTarget(
                        new SplittingTestRootNode(
                                        LibrarySplittingStrategyTestFactory.CachesLibOnValueNodeExcludeNodeGen.create(
                                                        new ReturnsFirstArgumentNode())));
        Object[] first = new Object[]{1};
        Object[] second = new Object[]{"2"};
        testDoesNotSplitDirectCallHelper(callTarget, first, second);
    }

    @Test
    public void testExportedMessage() {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) runtime.createCallTarget(
                        new SplittingTestRootNode(
                                        LibrarySplittingStrategyTestFactory.SplitReadPropertyNodeGen.create(
                                                        new ReturnsFirstArgumentNode(), new ReturnsSecondArgumentNode())));
        Object[] first = new Object[]{newInstance(), "a"};
        Object[] second = new Object[]{newInstance(), "b"};
        testSplitsDirectCallsHelper(callTarget, first, second);
    }

    @Test
    public void testExportedMessageWithExcludedSpecialisation() {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) runtime.createCallTarget(
                        new SplittingTestRootNode(
                                        LibrarySplittingStrategyTestFactory.SplitReadPropertyNodeGen.create(
                                                        new ReturnsFirstArgumentNode(), new ReturnsSecondArgumentNode())));
        Object[] first = new Object[]{newInstance(), SplittingObjectType.ReadMember.CACHED_NAME};
        Object[] second = new Object[]{newInstance(), "b"};
        testDoesNotSplitDirectCallHelper(callTarget, first, second);
    }

    @NodeChild
    abstract static class CachesLibOnValueNode extends SplittingTestNode {
        @Specialization(limit = "3")
        Object doDefault(Object y, @CachedLibrary(value = "y") SplittingLibrary s) {
            return s.m(y);
        }
    }

    @NodeChild
    @ReportPolymorphism.Exclude
    abstract static class CachesLibOnValueNodeExclude extends SplittingTestNode {
        @Specialization(limit = "3")
        Object doDefault(Object y, @CachedLibrary(value = "y") SplittingLibrary s) {
            return s.m(y);
        }
    }

    @GenerateLibrary
    abstract static class SplittingLibrary extends Library {

        @SuppressWarnings("unused")
        public Object m(Object receiver) {
            return null;
        }
    }

    @ExportLibrary(value = InteropLibrary.class, receiverType = DynamicObject.class)
    public static final class SplittingObjectType extends ObjectType {

        @ExportMessage
        @SuppressWarnings("unused")
        static boolean hasMembers(DynamicObject receiver) {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("unused")
        static boolean isMemberReadable(DynamicObject receiver, String memeber) {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("unused")
        static Object getMembers(DynamicObject receiver, boolean includeInternal) {
            return receiver;
        }

        @Override
        public Class<?> dispatch() {
            return SplittingObjectType.class;
        }

        @ExportMessage
        @GenerateUncached
        @ReportPolymorphism
        abstract static class ReadMember {

            static final String CACHED_NAME = "cached";

            @Specialization(guards = "name == CACHED_NAME")
            @ReportPolymorphism.Exclude
            static Object readStaticCached(DynamicObject receiver, @SuppressWarnings("unused") String name,
                            @SuppressWarnings("unused") @Cached("name") String cachedName) {
                return receiver;
            }

            @Specialization(limit = "3", guards = "name == cachedName")
            static Object readCached(DynamicObject receiver, @SuppressWarnings("unused") String name,
                            @SuppressWarnings("unused") @Cached("name") String cachedName) {
                return receiver;
            }
        }
    }

    @NodeChild("receiverNode")
    @NodeChild("nameNode")
    abstract static class SplitReadPropertyNode extends SplittingTestNode {

        static final int LIBRARY_LIMIT = 3;

        @Specialization(guards = "objects.hasMembers(receiver)", limit = "LIBRARY_LIMIT")
        protected Object readObject(Object receiver, String name,
                        @CachedLibrary("receiver") InteropLibrary objects) {
            try {
                return objects.readMember(receiver, name);
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                throw new AssertionError("Should not reach here");
            }
        }
    }
}
