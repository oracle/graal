package com.oracle.svm.graal.test.ai;

import com.oracle.svm.hosted.analysis.ai.domain.BooleanAndDomain;
import org.junit.Assert;
import org.junit.Test;

public class BooleanAndDomainTest {

    @Test
    public void testDefaultConstructor() {
        BooleanAndDomain domain = new BooleanAndDomain();
        Assert.assertTrue(domain.getValue());
    }

    @Test
    public void testValueConstructor() {
        BooleanAndDomain domain = new BooleanAndDomain(false);
        Assert.assertFalse(domain.getValue());
    }

    @Test
    public void testSetToBot() {
        BooleanAndDomain domain = new BooleanAndDomain(true);
        domain.setToBot();
        Assert.assertFalse(domain.getValue());
    }

    @Test
    public void testSetToTop() {
        BooleanAndDomain domain = new BooleanAndDomain(false);
        domain.setToTop();
        Assert.assertTrue(domain.getValue());
    }

    @Test
    public void testJoinWith() {
        BooleanAndDomain domain1 = new BooleanAndDomain(true);
        BooleanAndDomain domain2 = new BooleanAndDomain(true);
        domain1.joinWith(domain2);
        Assert.assertTrue(domain1.getValue());

        domain1 = new BooleanAndDomain(false);
        domain2 = new BooleanAndDomain(true);
        domain1.joinWith(domain2);
        Assert.assertFalse(domain1.getValue());

        domain1 = new BooleanAndDomain(true);
        domain2 = new BooleanAndDomain(false);
        domain1.joinWith(domain2);
        Assert.assertFalse(domain1.getValue());

        domain1 = new BooleanAndDomain(false);
        domain2 = new BooleanAndDomain(false);
        domain1.joinWith(domain2);
        Assert.assertFalse(domain1.getValue());
    }

    @Test
    public void testWidenWith() {
        BooleanAndDomain domain1 = new BooleanAndDomain(true);
        BooleanAndDomain domain2 = new BooleanAndDomain(false);
        domain1.widenWith(domain2);
        Assert.assertFalse(domain1.getValue());
    }

    @Test
    public void testMeetWith() {
        BooleanAndDomain domain1 = new BooleanAndDomain(true);
        BooleanAndDomain domain2 = new BooleanAndDomain(false);
        domain1.meetWith(domain2);
        Assert.assertTrue(domain1.getValue());

        domain1 = new BooleanAndDomain(false);
        domain2 = new BooleanAndDomain(true);
        domain1.meetWith(domain2);
        Assert.assertTrue(domain1.getValue());

        domain1 = new BooleanAndDomain(true);
        domain2 = new BooleanAndDomain(true);
        domain1.meetWith(domain2);
        Assert.assertTrue(domain1.getValue());

        domain1 = new BooleanAndDomain(false);
        domain2 = new BooleanAndDomain(false);
        domain1.meetWith(domain2);
        Assert.assertFalse(domain1.getValue());
    }

    @Test
    public void testEquals() {
        BooleanAndDomain domain1 = new BooleanAndDomain(true);
        BooleanAndDomain domain2 = new BooleanAndDomain(true);
        Assert.assertEquals(domain1, domain2);

        domain1 = new BooleanAndDomain(false);
        domain2 = new BooleanAndDomain(false);
        Assert.assertEquals(domain1, domain2);

        domain1 = new BooleanAndDomain(true);
        domain2 = new BooleanAndDomain(false);
        Assert.assertNotEquals(domain1, domain2);

        domain1 = new BooleanAndDomain(false);
        domain2 = new BooleanAndDomain(true);
        Assert.assertNotEquals(domain1, domain2);
    }

    @Test
    public void testLeq() {
        /*  false > true,
         *  this holds because of the way join is defined on BooleanAndDomain
         *  false.joinWith(true) = false, therefore false >= true
         */
        BooleanAndDomain domain1 = new BooleanAndDomain(false);
        BooleanAndDomain domain2 = new BooleanAndDomain(true);
        Assert.assertFalse(domain1.leq(domain2));

        /* true < false */
        domain1 = new BooleanAndDomain(true);
        domain2 = new BooleanAndDomain(false);
        Assert.assertTrue(domain1.leq(domain2));

        /* true <= true */
        domain1 = new BooleanAndDomain(true);
        domain2 = new BooleanAndDomain(true);
        Assert.assertTrue(domain1.leq(domain2));

        /* false <= false */
        domain1 = new BooleanAndDomain(false);
        domain2 = new BooleanAndDomain(false);
        Assert.assertTrue(domain1.leq(domain2));
    }
}