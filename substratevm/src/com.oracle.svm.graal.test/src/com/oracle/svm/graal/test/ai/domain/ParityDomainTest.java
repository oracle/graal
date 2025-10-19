package com.oracle.svm.graal.test.ai.domain;

import com.oracle.svm.hosted.analysis.ai.domain.ParityDomain;
import com.oracle.svm.hosted.analysis.ai.domain.value.Parity;
import org.junit.Assert;
import org.junit.Test;

public class ParityDomainTest {

    @Test
    public void testDefaultConstructor() {
        ParityDomain parityDomain = new ParityDomain();
        Assert.assertTrue(parityDomain.isBot());
    }

    @Test
    public void testParityConstructor() {
        ParityDomain parityDomain = new ParityDomain(Parity.ODD);
        Assert.assertEquals(Parity.ODD, parityDomain.getState());

        ParityDomain botDomain = new ParityDomain(Parity.BOT);
        Assert.assertTrue(botDomain.isBot());

        ParityDomain topDomain = new ParityDomain(Parity.TOP);
        Assert.assertTrue(topDomain.isTop());
    }

    @Test
    public void testCopyConstructor() {
        ParityDomain original = new ParityDomain(Parity.EVEN);
        ParityDomain copy = new ParityDomain(original);
        Assert.assertEquals(original.getState(), copy.getState());
    }

    @Test
    public void testSetToBot() {
        ParityDomain parityDomain = new ParityDomain(Parity.ODD);
        parityDomain.setToBot();
        Assert.assertTrue(parityDomain.isBot());
    }

    @Test
    public void testSetToTop() {
        ParityDomain parityDomain = new ParityDomain(Parity.EVEN);
        parityDomain.setToTop();
        Assert.assertTrue(parityDomain.isTop());
    }

    @Test
    public void testJoinWith() {
        ParityDomain parityDomain1 = new ParityDomain(Parity.ODD);
        ParityDomain parityDomain2 = new ParityDomain(Parity.EVEN);
        parityDomain1.joinWith(parityDomain2);
        Assert.assertTrue(parityDomain1.isTop());
    }

    @Test
    public void testWidenWith() {
        ParityDomain parityDomain1 = new ParityDomain(Parity.ODD);
        ParityDomain parityDomain2 = new ParityDomain(Parity.EVEN);
        parityDomain1.widenWith(parityDomain2);
        Assert.assertTrue(parityDomain1.isTop());
    }

    @Test
    public void testMeetWith() {
        ParityDomain parityDomain1 = new ParityDomain(Parity.ODD);
        ParityDomain parityDomain2 = new ParityDomain(Parity.ODD);
        parityDomain1.meetWith(parityDomain2);
        Assert.assertEquals(Parity.ODD, parityDomain1.getState());

        parityDomain1 = new ParityDomain(Parity.ODD);
        parityDomain2 = new ParityDomain(Parity.EVEN);
        parityDomain1.meetWith(parityDomain2);
        Assert.assertTrue(parityDomain1.isBot());
    }

    @Test
    public void testEquals() {
        ParityDomain parityDomain1 = new ParityDomain(Parity.ODD);
        ParityDomain parityDomain2 = new ParityDomain(Parity.ODD);
        Assert.assertEquals(parityDomain1, parityDomain2);

        parityDomain2 = new ParityDomain(Parity.EVEN);
        Assert.assertNotEquals(parityDomain1, parityDomain2);
    }

    @Test
    public void testLeq() {
        ParityDomain parityDomain1 = new ParityDomain(Parity.ODD);
        ParityDomain parityDomain2 = new ParityDomain(Parity.TOP);
        Assert.assertTrue(parityDomain1.leq(parityDomain2));

        parityDomain2 = new ParityDomain(Parity.BOT);
        Assert.assertFalse(parityDomain1.leq(parityDomain2));

        parityDomain2 = new ParityDomain(Parity.ODD);
        Assert.assertTrue(parityDomain1.leq(parityDomain2));
    }
}
