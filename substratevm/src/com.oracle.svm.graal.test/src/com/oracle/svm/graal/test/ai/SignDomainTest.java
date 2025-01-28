package com.oracle.svm.graal.test.ai;

import com.oracle.svm.hosted.analysis.ai.domain.SignDomain;
import com.oracle.svm.hosted.analysis.ai.value.Sign;
import org.junit.Assert;
import org.junit.Test;

public class SignDomainTest {

    @Test
    public void testDefaultConstructor() {
        SignDomain signDomain = new SignDomain();
        Assert.assertTrue(signDomain.isBot());
    }

    @Test
    public void testSignConstructor() {
        SignDomain signDomain = new SignDomain(Sign.POS);
        Assert.assertEquals(Sign.POS, signDomain.getState());

        SignDomain botDomain = new SignDomain(Sign.BOT);
        Assert.assertTrue(botDomain.isBot());

        SignDomain topDomain = new SignDomain(Sign.TOP);
        Assert.assertTrue(topDomain.isTop());
    }

    @Test
    public void testCopyConstructor() {
        SignDomain original = new SignDomain(Sign.NEG);
        SignDomain copy = new SignDomain(original);
        Assert.assertEquals(original.getState(), copy.getState());
    }

    @Test
    public void testSetToBot() {
        SignDomain signDomain = new SignDomain(Sign.POS);
        signDomain.setToBot();
        Assert.assertTrue(signDomain.isBot());
    }

    @Test
    public void testSetToTop() {
        SignDomain signDomain = new SignDomain(Sign.NEG);
        signDomain.setToTop();
        Assert.assertTrue(signDomain.isTop());
    }

    @Test
    public void testJoinWith() {
        SignDomain signDomain1 = new SignDomain(Sign.POS);
        SignDomain signDomain2 = new SignDomain(Sign.NEG);
        signDomain1.joinWith(signDomain2);
        Assert.assertTrue(signDomain1.isTop());
    }

    @Test
    public void testWidenWith() {
        SignDomain signDomain1 = new SignDomain(Sign.POS);
        SignDomain signDomain2 = new SignDomain(Sign.NEG);
        signDomain1.widenWith(signDomain2);
        Assert.assertTrue(signDomain1.isTop());
    }

    @Test
    public void testMeetWith() {
        SignDomain signDomain1 = new SignDomain(Sign.POS);
        SignDomain signDomain2 = new SignDomain(Sign.POS);
        signDomain1.meetWith(signDomain2);
        Assert.assertEquals(Sign.POS, signDomain1.getState());

        /* Meet with a different sign results in BOT */
        signDomain1 = new SignDomain(Sign.POS);
        signDomain2 = new SignDomain(Sign.NEG);
        signDomain1.meetWith(signDomain2);
        Assert.assertTrue(signDomain1.isBot());
    }

    @Test
    public void testEquals() {
        SignDomain signDomain1 = new SignDomain(Sign.POS);
        SignDomain signDomain2 = new SignDomain(Sign.POS);
        Assert.assertEquals(signDomain1, signDomain2);

        signDomain2 = new SignDomain(Sign.NEG);
        Assert.assertNotEquals(signDomain1, signDomain2);
    }

    @Test
    public void testLeq() {
        SignDomain signDomain1 = new SignDomain(Sign.POS);
        SignDomain signDomain2 = new SignDomain(Sign.TOP);
        Assert.assertTrue(signDomain1.leq(signDomain2));

        signDomain2 = new SignDomain(Sign.BOT);
        Assert.assertFalse(signDomain1.leq(signDomain2));

        signDomain2 = new SignDomain(Sign.POS);
        Assert.assertTrue(signDomain1.leq(signDomain2));
    }
}