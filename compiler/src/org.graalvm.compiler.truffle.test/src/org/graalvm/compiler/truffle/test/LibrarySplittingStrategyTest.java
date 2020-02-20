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
    public void testExportedMessage() throws UnknownIdentifierException, UnsupportedMessageException {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) runtime.createCallTarget(
                        new SplittingTestRootNode(
                                        LibrarySplittingStrategyTestFactory.SplitReadPropertyNodeGen.create(
                                                        new ReturnsFirstArgumentNode(), new ReturnsSecondArgumentNode())));
        final DynamicObject dynamicObject = newInstance();
        InteropLibrary interop = InteropLibrary.getFactory().getUncached(dynamicObject);
        Object[] first = new Object[]{dynamicObject, "a"};
        Object[] second = new Object[]{newInstance(), "b"};
        testSplitsDirectCallsHelper(callTarget, first, second);
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

            @Specialization(limit = "3", guards = "name == cachedName")
            static Object readCached(DynamicObject receiver, @SuppressWarnings("unused") String name,
                            @SuppressWarnings("unused") @Cached("name") String cachedName) {
                return receiver;
            }
        }
    }

    @NodeChild("receiverNode")
    @NodeChild("nameNode")
    static abstract class SplitReadPropertyNode extends SplittingTestNode {

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
