package org.graalvm.compiler.truffle.test;

import java.util.HashSet;
import java.util.Set;

import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;

public class LibrarySplittingStrategyTest extends AbstractSplittingStrategyTest {

    @Before
    public void boostBudget() {
        createDummyTargetsToBoostGrowingSplitLimit();
    }

    @Test
    public void testCachedLibWithValue() {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) runtime.createCallTarget(
                        new SplittingTestRootNode(LibrarySplittingStrategyTestFactory.CachesLibOnValueNodeGen.create(new ReturnsArgumentNode())));
        Object[] first = new Object[]{1};
        Object[] second = new Object[]{"2"};
        testSplitsDirectCallsHelper(callTarget, first, second);
    }

    @Test
    public void testCachedLibWithValueExclude() {
        OptimizedCallTarget callTarget = (OptimizedCallTarget) runtime.createCallTarget(
                        new SplittingTestRootNode(LibrarySplittingStrategyTestFactory.CachesLibOnValueNodeExcludeNodeGen.create(new ReturnsArgumentNode())));
        Object[] first = new Object[]{1};
        Object[] second = new Object[]{"2"};
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

        public Object m(Object receiver) {
            return null;
        }
    }
}
