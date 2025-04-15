package com.oracle.svm.graal.test.ai.domain;

import com.oracle.svm.hosted.analysis.ai.domain.ConstantDomain;
import com.oracle.svm.hosted.analysis.ai.domain.value.AbstractValueKind;
import org.junit.Assert;
import org.junit.Test;

public class ConstantDomainTest {

    @Test
    public void testDefaultConstructor() {
        ConstantDomain<Integer> domain = new ConstantDomain<>();
        Assert.assertTrue(domain.isBot());
    }

    @Test
    public void testValueConstructor() {
        ConstantDomain<Integer> domain = new ConstantDomain<>(5);
        Assert.assertTrue(domain.isValue());
        Assert.assertEquals(Integer.valueOf(5), domain.getValue());
    }

    @Test
    public void testKindConstructor() {
        ConstantDomain<Integer> domain = new ConstantDomain<>(AbstractValueKind.BOT);
        Assert.assertTrue(domain.isBot());
    }

    @Test
    public void testSetToBot() {
        ConstantDomain<Integer> domain = new ConstantDomain<>(5);
        domain.setToBot();
        Assert.assertTrue(domain.isBot());
    }

    @Test
    public void testSetToTop() {
        ConstantDomain<Integer> domain = new ConstantDomain<>(5);
        domain.setToTop();
        Assert.assertTrue(domain.isTop());
    }

    @Test
    public void testLeq() {
        ConstantDomain<Integer> domain1 = new ConstantDomain<>(5);
        ConstantDomain<Integer> domain2 = new ConstantDomain<>(10);
        Assert.assertFalse(domain1.leq(domain2));

        domain2 = new ConstantDomain<>(5);
        Assert.assertTrue(domain1.leq(domain2));

        domain2 = new ConstantDomain<>(AbstractValueKind.TOP);
        Assert.assertTrue(domain1.leq(domain2));

        domain2 = new ConstantDomain<>(AbstractValueKind.BOT);
        Assert.assertFalse(domain1.leq(domain2));
    }

    @Test
    public void testEquals() {
        ConstantDomain<Integer> domain1 = new ConstantDomain<>(5);
        ConstantDomain<Integer> domain2 = new ConstantDomain<>(5);
        Assert.assertEquals(domain1, domain2);

        domain2 = new ConstantDomain<>(10);
        Assert.assertNotEquals(domain1, domain2);

        domain2 = new ConstantDomain<>(AbstractValueKind.TOP);
        Assert.assertNotEquals(domain1, domain2);
    }

    @Test
    public void testJoinWith() {
        ConstantDomain<Integer> domain1 = new ConstantDomain<>(5);
        ConstantDomain<Integer> domain2 = new ConstantDomain<>(10);
        domain1.joinWith(domain2);
        Assert.assertTrue(domain1.isTop());

        domain1 = new ConstantDomain<>(5);
        domain2 = new ConstantDomain<>(5);
        domain1.joinWith(domain2);
        Assert.assertTrue(domain1.isValue());
        Assert.assertEquals(Integer.valueOf(5), domain1.getValue());
    }

    @Test
    public void testWidenWith() {
        ConstantDomain<Integer> domain1 = new ConstantDomain<>(5);
        ConstantDomain<Integer> domain2 = new ConstantDomain<>(10);
        domain1.widenWith(domain2);
        Assert.assertTrue(domain1.isTop());
    }

    @Test
    public void testMeetWith() {
        ConstantDomain<Integer> domain1 = new ConstantDomain<>(5);
        ConstantDomain<Integer> domain2 = new ConstantDomain<>(10);
        domain1.meetWith(domain2);
        Assert.assertTrue(domain1.isBot());

        domain1 = new ConstantDomain<>(5);
        domain2 = new ConstantDomain<>(5);
        domain1.meetWith(domain2);
        Assert.assertTrue(domain1.isValue());
        Assert.assertEquals(Integer.valueOf(5), domain1.getValue());
    }

    @Test
    public void testCopyOf() {
        ConstantDomain<Integer> domain1 = new ConstantDomain<>(5);
        ConstantDomain<Integer> copy = domain1.copyOf();
        Assert.assertEquals(domain1, copy);
    }
}
