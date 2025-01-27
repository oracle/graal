package com.oracle.svm.graal.test.ai;

import com.oracle.svm.hosted.analysis.ai.domain.IntInterval;
import org.junit.Assert;
import org.junit.Test;

public class IntIntervalDomainTest {

    @Test
    public void testDefaultConstructor() {
        IntInterval interval = new IntInterval();
        Assert.assertTrue(interval.isBot());
        interval.setToTop();
        Assert.assertTrue(interval.isTop());
    }

    @Test
    public void testConstantConstructor() {
        IntInterval interval = new IntInterval(5);
        Assert.assertEquals(5, interval.getLowerBound());
        Assert.assertEquals(5, interval.getUpperBound());
    }

    @Test
    public void testRangeConstructor() {
        IntInterval interval = new IntInterval(3, 7);
        Assert.assertEquals(3, interval.getLowerBound());
        Assert.assertEquals(7, interval.getUpperBound());
    }

    @Test
    public void testCopyConstructor() {
        IntInterval original = new IntInterval(3, 7);
        IntInterval copy = new IntInterval(original);
        Assert.assertEquals(original.getLowerBound(), copy.getLowerBound());
        Assert.assertEquals(original.getUpperBound(), copy.getUpperBound());
    }

    @Test
    public void testJoin() {
        /* Classic joining of two intervals */
        IntInterval interval1 = new IntInterval(1, 5);
        IntInterval interval2 = new IntInterval(3, 7);
        interval1.joinWith(interval2);
        Assert.assertEquals(1, interval1.getLowerBound());
        Assert.assertEquals(7, interval1.getUpperBound());

        /* Joining an interval with a BOT should not change the interval */
        interval1 = new IntInterval(1, 5);
        IntInterval bottom = new IntInterval();
        interval1.joinWith(bottom);
        Assert.assertEquals(1, interval1.getLowerBound());
        Assert.assertEquals(5, interval1.getUpperBound());

        /* Joining an interval with a TOP should result in a TOP interval */
        interval1 = new IntInterval(1, 5);
        IntInterval top = new IntInterval();
        top.setToTop();
        interval1.joinWith(top);
        Assert.assertTrue(interval1.isTop());

        /* Joining a bot with non-bot interval should result in the non-bot interval */
        interval1 = new IntInterval();
        interval2 = new IntInterval(1, 5);
        interval1.joinWith(interval2);
        Assert.assertEquals(1, interval1.getLowerBound());
        Assert.assertEquals(5, interval1.getUpperBound());
    }

    @Test
    public void testWiden() {
        IntInterval interval1 = new IntInterval(1, 5);
        IntInterval interval2 = new IntInterval(3, 7);
        interval1.widenWith(interval2);
        Assert.assertEquals(1, interval1.getLowerBound());
        Assert.assertEquals(IntInterval.MAX, interval1.getUpperBound());

        /* Widen with a BOT should not change the interval */
        interval1 = new IntInterval(1, 5);
        IntInterval bottom = new IntInterval();
        interval1.widenWith(bottom);
        Assert.assertEquals(1, interval1.getLowerBound());
        Assert.assertEquals(5, interval1.getUpperBound());

        /* Widen with a TOP should result in a TOP interval */
        interval1 = new IntInterval(1, 5);
        IntInterval top = new IntInterval();
        top.setToTop();
        interval1.widenWith(top);
        Assert.assertTrue(interval1.isTop());

        /* Widen a bot with non-bot interval should result in the non-bot interval */
        interval1 = new IntInterval();
        interval2 = new IntInterval(1, 5);
        interval1.widenWith(interval2);
        Assert.assertEquals(1, interval1.getLowerBound());
        Assert.assertEquals(5, interval1.getUpperBound());
    }

    @Test
    public void testMeet() {
        IntInterval interval1 = new IntInterval(1, 5);
        IntInterval interval2 = new IntInterval(3, 7);
        interval1.meetWith(interval2);
        Assert.assertEquals(3, interval1.getLowerBound());
        Assert.assertEquals(5, interval1.getUpperBound());

        /* Meet with a BOT should result in a BOT interval */
        interval1 = new IntInterval(1, 5);
        IntInterval bottom = new IntInterval();
        bottom.setToBot();
        interval1.meetWith(bottom);
        Assert.assertTrue(interval1.isBot());
    }

    @Test
    public void testAdd() {
        IntInterval interval1 = new IntInterval(1, 2);
        IntInterval interval2 = new IntInterval(3, 4);
        IntInterval result = interval1.add(interval2);
        Assert.assertEquals(4, result.getLowerBound());
        Assert.assertEquals(6, result.getUpperBound());

        IntInterval interval3 = new IntInterval(-7, 4);
        IntInterval interval4 = new IntInterval(-3, 6);
        IntInterval expected = interval3.add(interval4);
        Assert.assertEquals(-10, expected.getLowerBound());
        Assert.assertEquals(10, expected.getUpperBound());
    }

    @Test
    public void testSub() {
        IntInterval interval1 = new IntInterval(5, 7);
        IntInterval interval2 = new IntInterval(2, 3);
        IntInterval result = interval1.sub(interval2);
        Assert.assertEquals(3, result.getLowerBound());
        Assert.assertEquals(4, result.getUpperBound());
    }

    @Test
    public void testMul() {
        IntInterval interval1 = new IntInterval(2, 3);
        IntInterval interval2 = new IntInterval(4, 5);
        IntInterval result = interval1.mul(interval2);
        Assert.assertEquals(8, result.getLowerBound());
        Assert.assertEquals(15, result.getUpperBound());
    }

    @Test
    public void testDiv() {
        IntInterval interval1 = new IntInterval(8, 10);
        IntInterval interval2 = new IntInterval(2, 2);
        IntInterval result = interval1.div(interval2);
        Assert.assertEquals(4, result.getLowerBound());
        Assert.assertEquals(5, result.getUpperBound());
    }

    @Test
    public void testRem() {
        IntInterval interval1 = new IntInterval(8, 10);
        IntInterval interval2 = new IntInterval(3, 3);
        IntInterval result = interval1.rem(interval2);
        Assert.assertEquals(1, result.getLowerBound());
        Assert.assertEquals(2, result.getUpperBound());
    }

    @Test
    public void testInverse() {
        IntInterval interval = new IntInterval(IntInterval.MIN, 3);
        interval.inverse();
        Assert.assertEquals(4, interval.getLowerBound());
        Assert.assertEquals(IntInterval.MAX, interval.getUpperBound());
    }
}