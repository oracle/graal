package com.oracle.svm.graal.test.ai.domain;

import com.oracle.svm.hosted.analysis.ai.domain.InvertedDomain;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import org.junit.Assert;
import org.junit.Test;

public class InvertedDomainTest {

    @Test
    public void testDefaultConstructor() {
        /* IntInterval is BOT when created with default constructor */
        IntInterval interval = new IntInterval();
        InvertedDomain<IntInterval> invertedDomain = new InvertedDomain<>(interval);
        Assert.assertTrue(invertedDomain.isTop());
    }

    @Test
    public void testValueConstructor() {
        IntInterval interval = new IntInterval(5, 10);
        InvertedDomain<IntInterval> invertedDomain = new InvertedDomain<>(interval);
        Assert.assertEquals(interval, invertedDomain.getDomain());
    }

    @Test
    public void testSetToBot() {
        IntInterval interval = new IntInterval(5, 10);
        InvertedDomain<IntInterval> invertedDomain = new InvertedDomain<>(interval);
        invertedDomain.setToBot();
        Assert.assertTrue(invertedDomain.isBot());
    }

    @Test
    public void testSetToTop() {
        IntInterval interval = new IntInterval(5, 10);
        InvertedDomain<IntInterval> invertedDomain = new InvertedDomain<>(interval);
        invertedDomain.setToTop();
        Assert.assertTrue(invertedDomain.isTop());
    }

    @Test
    public void testJoinWith() {
        IntInterval interval1 = new IntInterval(1, 5);
        IntInterval interval2 = new IntInterval(3, 7);
        InvertedDomain<IntInterval> invertedDomain1 = new InvertedDomain<>(interval1);
        InvertedDomain<IntInterval> invertedDomain2 = new InvertedDomain<>(interval2);
        invertedDomain1.joinWith(invertedDomain2);
        Assert.assertEquals(3, invertedDomain1.getDomain().getLowerBound());
        Assert.assertEquals(5, invertedDomain1.getDomain().getUpperBound());
    }

    @Test
    public void testMeetWith() {
        IntInterval interval1 = new IntInterval(1, 5);
        IntInterval interval2 = new IntInterval(3, 7);
        InvertedDomain<IntInterval> invertedDomain1 = new InvertedDomain<>(interval1);
        InvertedDomain<IntInterval> invertedDomain2 = new InvertedDomain<>(interval2);
        invertedDomain1.meetWith(invertedDomain2);
        Assert.assertEquals(1, invertedDomain1.getDomain().getLowerBound());
        Assert.assertEquals(7, invertedDomain1.getDomain().getUpperBound());
    }

    @Test
    public void testCopyOf() {
        IntInterval interval = new IntInterval(5, 10);
        InvertedDomain<IntInterval> invertedDomain = new InvertedDomain<>(interval);
        InvertedDomain<IntInterval> copy = invertedDomain.copyOf();
        Assert.assertEquals(5, copy.getDomain().getLowerBound());
        Assert.assertEquals(10, copy.getDomain().getUpperBound());
    }

    @Test
    public void testEquals() {
        IntInterval interval1 = new IntInterval(5, 10);
        IntInterval interval2 = new IntInterval(5, 10);
        InvertedDomain<IntInterval> invertedDomain1 = new InvertedDomain<>(interval1);
        InvertedDomain<IntInterval> invertedDomain2 = new InvertedDomain<>(interval2);
        Assert.assertEquals(invertedDomain1, invertedDomain2);
    }

    @Test
    public void testLeq() {
        /* interval2 <= interval1, so in inverted domain, invertedDomain1 <= invertedDomain2 */
        IntInterval interval1 = new IntInterval(1, 10);
        IntInterval interval2 = new IntInterval(3, 7);
        InvertedDomain<IntInterval> invertedDomain1 = new InvertedDomain<>(interval1);
        InvertedDomain<IntInterval> invertedDomain2 = new InvertedDomain<>(interval2);
        Assert.assertTrue(invertedDomain1.leq(invertedDomain2));
        Assert.assertFalse(invertedDomain2.leq(invertedDomain1));
    }
}
