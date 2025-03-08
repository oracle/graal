package com.oracle.svm.graal.test.ai;

import com.oracle.svm.hosted.analysis.ai.domain.SetDomain;
import com.oracle.svm.hosted.analysis.ai.value.AbstractValueKind;
import org.junit.Assert;
import org.junit.Test;

public class SetDomainTest {

    @Test
    public void testDefaultConstructor() {
        SetDomain<String> setDomain = new SetDomain<>();
        Assert.assertTrue(setDomain.empty());
        Assert.assertEquals(AbstractValueKind.BOT, setDomain.getKind());
    }

    @Test
    public void testAdd() {
        SetDomain<String> setDomain = new SetDomain<>();
        setDomain.add("element");
        Assert.assertFalse(setDomain.empty());
        Assert.assertTrue(setDomain.getSet().contains("element"));
        Assert.assertEquals(AbstractValueKind.VAL, setDomain.getKind());
    }

    @Test
    public void testRemove() {
        SetDomain<String> setDomain = new SetDomain<>();
        setDomain.add("element");
        setDomain.remove("element");
        Assert.assertTrue(setDomain.empty());
        Assert.assertFalse(setDomain.getSet().contains("element"));
        Assert.assertEquals(AbstractValueKind.BOT, setDomain.getKind());
    }

    @Test
    public void testUnionWith() {
        SetDomain<String> setDomain1 = new SetDomain<>();
        setDomain1.add("element1");
        SetDomain<String> setDomain2 = new SetDomain<>();
        setDomain2.add("element2");
        setDomain1.unionWith(setDomain2);
        Assert.assertTrue(setDomain1.getSet().contains("element1"));
        Assert.assertTrue(setDomain1.getSet().contains("element2"));
    }

    @Test
    public void testIntersectionWith() {
        SetDomain<String> setDomain1 = new SetDomain<>();
        setDomain1.add("element1");
        setDomain1.add("element2");
        SetDomain<String> setDomain2 = new SetDomain<>();
        setDomain2.add("element2");
        setDomain1.intersectionWith(setDomain2);
        Assert.assertFalse(setDomain1.getSet().contains("element1"));
        Assert.assertTrue(setDomain1.getSet().contains("element2"));
    }

    @Test
    public void testDifferenceWith() {
        SetDomain<String> setDomain1 = new SetDomain<>();
        setDomain1.add("element1");
        setDomain1.add("element2");
        SetDomain<String> setDomain2 = new SetDomain<>();
        setDomain2.add("element2");
        setDomain1.differenceWith(setDomain2);
        Assert.assertTrue(setDomain1.getSet().contains("element1"));
        Assert.assertFalse(setDomain1.getSet().contains("element2"));
    }

    @Test
    public void testFilter() {
        SetDomain<String> setDomain = new SetDomain<>();
        setDomain.add("element1");
        setDomain.add("element2");
        setDomain.filter(e -> e.equals("element1"));
        Assert.assertTrue(setDomain.getSet().contains("element1"));
        Assert.assertFalse(setDomain.getSet().contains("element2"));
    }

    @Test
    public void testCopyOf() {
        SetDomain<String> setDomain = new SetDomain<>();
        setDomain.add("element");
        SetDomain<String> copy = setDomain.copyOf();
        Assert.assertEquals(setDomain, copy);
        Assert.assertNotSame(setDomain, copy);
    }

    @Test
    public void testEquals() {
        SetDomain<String> setDomain1 = new SetDomain<>();
        setDomain1.add("element");
        SetDomain<String> setDomain2 = new SetDomain<>();
        setDomain2.add("element");
        Assert.assertEquals(setDomain1, setDomain2);
    }

    @Test
    public void testSetToTop() {
        SetDomain<String> setDomain = new SetDomain<>();
        setDomain.setToTop();
        Assert.assertTrue(setDomain.isTop());
    }

    @Test
    public void testLeq() {
        SetDomain<String> setDomain1 = new SetDomain<>();
        setDomain1.add("element1");
        SetDomain<String> setDomain2 = new SetDomain<>();
        setDomain2.add("element1");
        setDomain2.add("element2");
        Assert.assertTrue(setDomain1.leq(setDomain2));
        Assert.assertFalse(setDomain2.leq(setDomain1));
    }

    @Test
    public void testJoin() {
        SetDomain<String> setDomain1 = new SetDomain<>();
        setDomain1.add("element1");
        SetDomain<String> setDomain2 = new SetDomain<>();
        setDomain2.add("element2");
        setDomain1.joinWith(setDomain2);
        Assert.assertTrue(setDomain1.getSet().contains("element1"));
        Assert.assertTrue(setDomain1.getSet().contains("element2"));
    }

    @Test
    public void testWiden() {
        SetDomain<String> setDomain1 = new SetDomain<>();
        setDomain1.add("element1");
        SetDomain<String> setDomain2 = new SetDomain<>();
        setDomain2.add("element2");
        setDomain1.widenWith(setDomain2);
        Assert.assertTrue(setDomain1.getSet().contains("element1"));
        Assert.assertTrue(setDomain1.getSet().contains("element2"));
    }

    @Test
    public void testMeet() {
        SetDomain<String> setDomain1 = new SetDomain<>();
        setDomain1.add("element1");
        setDomain1.add("element2");
        SetDomain<String> setDomain2 = new SetDomain<>();
        setDomain2.add("element2");
        setDomain1.meetWith(setDomain2);
        Assert.assertFalse(setDomain1.getSet().contains("element1"));
        Assert.assertTrue(setDomain1.getSet().contains("element2"));
    }
}
