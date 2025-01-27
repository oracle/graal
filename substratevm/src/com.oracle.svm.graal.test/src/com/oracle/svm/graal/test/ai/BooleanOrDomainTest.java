package com.oracle.svm.graal.test.ai;

import com.oracle.svm.hosted.analysis.ai.domain.BooleanOrDomain;
import org.junit.Assert;
import org.junit.Test;

public class BooleanOrDomainTest {

    @Test
    public void testDefaultConstructor() {
        BooleanOrDomain domain = new BooleanOrDomain();
        Assert.assertFalse(domain.getValue());
    }

    @Test
    public void testValueConstructor() {
        BooleanOrDomain domain = new BooleanOrDomain(true);
        Assert.assertTrue(domain.getValue());
    }

    @Test
    public void testSetToBot() {
        BooleanOrDomain domain = new BooleanOrDomain(true);
        domain.setToBot();
        Assert.assertFalse(domain.getValue());
    }

    @Test
    public void testSetToTop() {
        BooleanOrDomain domain = new BooleanOrDomain(false);
        domain.setToTop();
        Assert.assertTrue(domain.getValue());
    }

    @Test
    public void testJoinWith() {
        BooleanOrDomain domain1 = new BooleanOrDomain(true);
        BooleanOrDomain domain2 = new BooleanOrDomain(true);
        domain1.joinWith(domain2);
        Assert.assertTrue(domain1.getValue());

        domain1 = new BooleanOrDomain(false);
        domain2 = new BooleanOrDomain(true);
        domain1.joinWith(domain2);
        Assert.assertTrue(domain1.getValue());

        domain1 = new BooleanOrDomain(true);
        domain2 = new BooleanOrDomain(false);
        domain1.joinWith(domain2);
        Assert.assertTrue(domain1.getValue());

        domain1 = new BooleanOrDomain(false);
        domain2 = new BooleanOrDomain(false);
        domain1.joinWith(domain2);
        Assert.assertFalse(domain1.getValue());
    }

    @Test
    public void testWidenWith() {
        BooleanOrDomain domain1 = new BooleanOrDomain(false);
        BooleanOrDomain domain2 = new BooleanOrDomain(true);
        domain1.widenWith(domain2);
        Assert.assertTrue(domain1.getValue());
    }


    @Test
    public void testMeetWith() {
        BooleanOrDomain domain1 = new BooleanOrDomain(true);
        BooleanOrDomain domain2 = new BooleanOrDomain(false);
        domain1.meetWith(domain2);
        Assert.assertFalse(domain1.getValue());

        domain1 = new BooleanOrDomain(false);
        domain2 = new BooleanOrDomain(true);
        domain1.meetWith(domain2);
        Assert.assertFalse(domain1.getValue());

        domain1 = new BooleanOrDomain(true);
        domain2 = new BooleanOrDomain(true);
        domain1.meetWith(domain2);
        Assert.assertTrue(domain1.getValue());

        domain1 = new BooleanOrDomain(false);
        domain2 = new BooleanOrDomain(false);
        domain1.meetWith(domain2);
        Assert.assertFalse(domain1.getValue());
    }

    @Test
    public void testEquals() {
        BooleanOrDomain domain1 = new BooleanOrDomain(true);
        BooleanOrDomain domain2 = new BooleanOrDomain(true);
        Assert.assertTrue(domain1.equals(domain2));

        domain1 = new BooleanOrDomain(false);
        domain2 = new BooleanOrDomain(false);
        Assert.assertTrue(domain1.equals(domain2));

        domain1 = new BooleanOrDomain(true);
        domain2 = new BooleanOrDomain(false);
        Assert.assertFalse(domain1.equals(domain2));

        domain1 = new BooleanOrDomain(false);
        domain2 = new BooleanOrDomain(true);
        Assert.assertFalse(domain1.equals(domain2));
    }

    @Test
    public void testLeq() {
        /* false < true */
        BooleanOrDomain domain1 = new BooleanOrDomain(false);
        BooleanOrDomain domain2 = new BooleanOrDomain(true);
        Assert.assertTrue(domain1.leq(domain2));

        /* true > false */
        domain1 = new BooleanOrDomain(true);
        domain2 = new BooleanOrDomain(false);
        Assert.assertFalse(domain1.leq(domain2));

        /* true <= true */
        domain1 = new BooleanOrDomain(true);
        domain2 = new BooleanOrDomain(true);
        Assert.assertTrue(domain1.leq(domain2));

        /* false <= false */
        domain1 = new BooleanOrDomain(false);
        domain2 = new BooleanOrDomain(false);
        Assert.assertTrue(domain1.leq(domain2));
    }
}