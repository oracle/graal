package com.oracle.svm.graal.test.ai;

import com.oracle.svm.hosted.analysis.ai.domain.PairDomain;
import com.oracle.svm.hosted.analysis.ai.domain.IntInterval;
import com.oracle.svm.hosted.analysis.ai.domain.BooleanAndDomain;
import org.junit.Assert;
import org.junit.Test;

public class PairDomainTest {

    @Test
    public void testDefaultConstructor() {
        IntInterval interval = new IntInterval();
        BooleanAndDomain booleanDomain = new BooleanAndDomain();
        PairDomain<IntInterval, BooleanAndDomain> pairDomain = new PairDomain<>(interval, booleanDomain);
    }

    @Test
    public void testValueConstructor() {
        IntInterval interval = new IntInterval(5, 10);
        BooleanAndDomain booleanDomain = new BooleanAndDomain(true);
        PairDomain<IntInterval, BooleanAndDomain> pairDomain = new PairDomain<>(interval, booleanDomain);
        Assert.assertEquals(interval, pairDomain.getFirst());
        Assert.assertEquals(booleanDomain, pairDomain.getSecond());
    }

    @Test
    public void testSetToBot() {
        IntInterval interval = new IntInterval(5, 10);
        BooleanAndDomain booleanDomain = new BooleanAndDomain(true);
        PairDomain<IntInterval, BooleanAndDomain> pairDomain = new PairDomain<>(interval, booleanDomain);
        pairDomain.setToBot();
        Assert.assertTrue(pairDomain.isBot());
    }

    @Test
    public void testSetToTop() {
        IntInterval interval = new IntInterval(5, 10);
        BooleanAndDomain booleanDomain = new BooleanAndDomain(true);
        PairDomain<IntInterval, BooleanAndDomain> pairDomain = new PairDomain<>(interval, booleanDomain);
        pairDomain.setToTop();
        Assert.assertTrue(pairDomain.isTop());
    }

    @Test
    public void testJoinWith() {
        IntInterval interval1 = new IntInterval(1, 5);
        IntInterval interval2 = new IntInterval(3, 7);
        BooleanAndDomain booleanDomain1 = new BooleanAndDomain(true);
        BooleanAndDomain booleanDomain2 = new BooleanAndDomain(false);
        PairDomain<IntInterval, BooleanAndDomain> pairDomain1 = new PairDomain<>(interval1, booleanDomain1);
        PairDomain<IntInterval, BooleanAndDomain> pairDomain2 = new PairDomain<>(interval2, booleanDomain2);
        pairDomain1.joinWith(pairDomain2);
        Assert.assertEquals(1, pairDomain1.getFirst().getLowerBound());
        Assert.assertEquals(7, pairDomain1.getFirst().getUpperBound());
        Assert.assertFalse(pairDomain1.getSecond().getValue());
    }

    @Test
    public void testMeetWith() {
        IntInterval interval1 = new IntInterval(1, 5);
        IntInterval interval2 = new IntInterval(3, 7);
        BooleanAndDomain booleanDomain1 = new BooleanAndDomain(true);
        BooleanAndDomain booleanDomain2 = new BooleanAndDomain(false);
        PairDomain<IntInterval, BooleanAndDomain> pairDomain1 = new PairDomain<>(interval1, booleanDomain1);
        PairDomain<IntInterval, BooleanAndDomain> pairDomain2 = new PairDomain<>(interval2, booleanDomain2);
        pairDomain1.meetWith(pairDomain2);
        Assert.assertEquals(3, pairDomain1.getFirst().getLowerBound());
        Assert.assertEquals(5, pairDomain1.getFirst().getUpperBound());
        Assert.assertTrue(pairDomain1.getSecond().getValue());
    }

    @Test
    public void testCopyOf() {
        IntInterval interval = new IntInterval(5, 10);
        BooleanAndDomain booleanDomain = new BooleanAndDomain(true);
        PairDomain<IntInterval, BooleanAndDomain> pairDomain = new PairDomain<>(interval, booleanDomain);
        PairDomain<IntInterval, BooleanAndDomain> copy = pairDomain.copyOf();
        Assert.assertTrue(pairDomain.getFirst().equals(copy.getFirst()));
        Assert.assertTrue(pairDomain.getSecond().equals(copy.getSecond()));
    }

    @Test
    public void testEquals() {
        IntInterval interval1 = new IntInterval(5, 10);
        IntInterval interval2 = new IntInterval(5, 10);
        BooleanAndDomain booleanDomain1 = new BooleanAndDomain(true);
        BooleanAndDomain booleanDomain2 = new BooleanAndDomain(true);
        PairDomain<IntInterval, BooleanAndDomain> pairDomain1 = new PairDomain<>(interval1, booleanDomain1);
        PairDomain<IntInterval, BooleanAndDomain> pairDomain2 = new PairDomain<>(interval2, booleanDomain2);
        Assert.assertTrue(pairDomain1.equals(pairDomain2));
    }

    @Test
    public void testLeq() {
        IntInterval interval1 = new IntInterval(1, 10);
        IntInterval interval2 = new IntInterval(3, 7);
        BooleanAndDomain booleanDomain1 = new BooleanAndDomain(false);
        BooleanAndDomain booleanDomain2 = new BooleanAndDomain(true);
        PairDomain<IntInterval, BooleanAndDomain> pairDomain1 = new PairDomain<>(interval1, booleanDomain1);
        PairDomain<IntInterval, BooleanAndDomain> pairDomain2 = new PairDomain<>(interval2, booleanDomain2);
        Assert.assertFalse(pairDomain1.leq(pairDomain2));
        Assert.assertTrue(pairDomain2.leq(pairDomain1));
    }
}