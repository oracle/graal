package com.oracle.svm.graal.test.ai.domain;

import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.PentagonDomain;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the Pentagon domain implementation.
 */
public class PentagonDomainTest {

    @Test
    public void testBasicOperations() {
        // Create two pentagon domains
        PentagonDomain<String> domain1 = new PentagonDomain<>();
        PentagonDomain<String> domain2 = new PentagonDomain<>();

        // Set up intervals
        domain1.setInterval("x", new IntInterval(0, 10));
        domain1.setInterval("y", new IntInterval(5, 15));

        domain2.setInterval("x", new IntInterval(5, 15));
        domain2.setInterval("y", new IntInterval(10, 20));

        // Set up less-than relations
        // y should be in the range [11, 15] because of this relation
        domain1.addLessThanRelation("x", "y");
        Assert.assertEquals(new IntInterval(11, 15), domain1.getInterval("y"));

        // Test join
        PentagonDomain<String> joined = domain1.join(domain2);
        Assert.assertEquals(new IntInterval(0, 15), joined.getInterval("x"));
        // y is refined
        Assert.assertEquals(new IntInterval(10, 20), joined.getInterval("y"));
        // Less-than relation is maintained only if present in both domains
        Assert.assertFalse(joined.lessThan("x", "y"));

        // Test meet
        PentagonDomain<String> met = domain1.meet(domain2);
        Assert.assertEquals(new IntInterval(), met.getInterval("x"));
        Assert.assertEquals(new IntInterval(11, 15), met.getInterval("y"));
        Assert.assertFalse(met.lessThan("x", "y"));

        // Test reduction
        PentagonDomain<String> reduced = new PentagonDomain<>();
        reduced.setInterval("x", new IntInterval(0, 100));
        reduced.setInterval("y", new IntInterval(0, 100));
        reduced.addLessThanRelation("x", "y");

        // After reduction, x interval should be refined to [0, 99] due to x < y
        Assert.assertTrue(reduced.getInterval("x").isBot());
        // After reduction, y interval should be refined to [1, 100] due to x < y
        Assert.assertEquals(new IntInterval(0, 100), reduced.getInterval("y"));
    }

    @Test
    public void testWidening() {
        PentagonDomain<String> domain1 = new PentagonDomain<>();
        PentagonDomain<String> domain2 = new PentagonDomain<>();

        domain1.setInterval("i", new IntInterval(0, 10));
        domain2.setInterval("i", new IntInterval(0, 20));

        // Widening should accelerate convergence
        PentagonDomain<String> widened = domain1.widen(domain2);
        Assert.assertEquals(new IntInterval(0, IntInterval.MAX), widened.getInterval("i"));
    }

    @Test
    public void testTransitivity() {
        PentagonDomain<String> domain = new PentagonDomain<>();

        domain.setInterval("x", new IntInterval(0, 100));
        domain.setInterval("y", new IntInterval(0, 100));
        domain.setInterval("z", new IntInterval(0, 100));

        /* These two relations make x and y the BOT */
        domain.addLessThanRelation("x", "y");
        domain.addLessThanRelation("y", "z");

        // Transitivity should be applied automatically during reduction
        Assert.assertTrue(domain.lessThan("x", "z"));

        // Check interval refinement through transitive relations
        Assert.assertTrue(domain.getInterval("x").isBot());
        Assert.assertTrue(domain.getInterval("y").isBot());
        Assert.assertEquals(new IntInterval(0, 100), domain.getInterval("z"));
    }

    @Test
    public void testContradictoryJoinRemovesRelation() {
        PentagonDomain<String> d1 = new PentagonDomain<>();
        d1.setInterval("x", new IntInterval(0, 10));
        d1.setInterval("y", new IntInterval(20, 30));
        d1.addLessThanRelation("x", "y");

        PentagonDomain<String> d2 = new PentagonDomain<>();
        d2.setInterval("x", new IntInterval(25, 35));
        d2.setInterval("y", new IntInterval(0, 5));
        d2.addLessThanRelation("y", "x");

        d1.joinWith(d2);

        Assert.assertFalse(d1.lessThan("x", "y"));
        Assert.assertFalse(d1.lessThan("y", "x"));
    }

    @Test
    public void testMeetWithBot() {
        PentagonDomain<String> domain1 = new PentagonDomain<>();
        PentagonDomain<String> domain2 = new PentagonDomain<>();

        domain1.setInterval("a", new IntInterval(0, 50));
        domain2.setInterval("a", new IntInterval(60, 100));
        domain1.addLessThanRelation("a", "a"); // force an unsatisfiable constraint

        domain1.meetWith(domain2);
        Assert.assertTrue(domain1.getInterval("a").isBot());
    }

    @Test
    public void testSetToTopAndBot() {
        PentagonDomain<String> domain = new PentagonDomain<>();
        domain.setInterval("b", new IntInterval(10, 20));
        domain.addLessThanRelation("b", "b"); // a contradictory relation
        Assert.assertTrue(domain.isBot());
        domain.setToTop();
        Assert.assertTrue(domain.isTop());
        domain.setToBot();
        Assert.assertTrue(domain.isBot());
    }
}
