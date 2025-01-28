package com.oracle.svm.graal.test.ai;

import com.oracle.svm.hosted.analysis.ai.domain.BooleanAndDomain;
import com.oracle.svm.hosted.analysis.ai.domain.MapDomain;
import com.oracle.svm.hosted.analysis.ai.domain.IntInterval;
import com.oracle.svm.hosted.analysis.ai.value.AbstractValueKind;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class MapDomainTest {

    @Test
    public void testDefaultConstructor() {
        /* Default MapDomain is BOT */
        MapDomain<String, IntInterval> mapDomain = new MapDomain<>(new IntInterval());
        Assert.assertTrue(mapDomain.isBot());
        Assert.assertTrue(mapDomain.get("x").isTop());
        Assert.assertEquals(0, mapDomain.getSize());
    }

    @Test
    public void testPutAndGet() {
        MapDomain<String, IntInterval> mapDomain = new MapDomain<>(new IntInterval());
        mapDomain.put("x", new IntInterval(1, 5));
        Assert.assertTrue(new IntInterval(1, 5).equals(mapDomain.get("x")));
    }

    @Test
    public void testSetToBot() {
        MapDomain<String, IntInterval> mapDomain = new MapDomain<>(new IntInterval());
        mapDomain.put("x", new IntInterval(1, 5));
        mapDomain.setToBot();
        Assert.assertTrue(mapDomain.isBot());
        Assert.assertEquals(0, mapDomain.getSize());
    }

    @Test
    public void testJoinWith() {
        MapDomain<String, IntInterval> mapDomain1 = new MapDomain<>(new IntInterval());
        MapDomain<String, IntInterval> mapDomain2 = new MapDomain<>(new IntInterval());
        mapDomain1.put("x", new IntInterval(1, 5));
        mapDomain2.put("x", new IntInterval(3, 7));
        Assert.assertEquals(AbstractValueKind.VAL, mapDomain1.getKind());
        Assert.assertEquals(AbstractValueKind.VAL, mapDomain2.getKind());
        mapDomain1.joinWith(mapDomain2);
        Assert.assertTrue(new IntInterval(1, 7).equals(mapDomain1.get("x")));
    }

    @Test
    public void testJoinWithNonEmptyDifference() {
        MapDomain<String, IntInterval> mapDomain1 = new MapDomain<>(new IntInterval());
        MapDomain<String, IntInterval> mapDomain2 = new MapDomain<>(new IntInterval());
        mapDomain1.put("x", new IntInterval(1, 5));
        mapDomain2.put("y", new IntInterval(3, 7));
        Assert.assertEquals(AbstractValueKind.VAL, mapDomain1.getKind());
        Assert.assertEquals(AbstractValueKind.VAL, mapDomain2.getKind());
        mapDomain1.joinWith(mapDomain2);
        Assert.assertTrue(new IntInterval(1, 5).equals(mapDomain1.get("x")));
        Assert.assertTrue(new IntInterval(3, 7).equals(mapDomain1.get("y")));
    }

    @Test
    public void testMeetWith() {
        MapDomain<String, IntInterval> mapDomain1 = new MapDomain<>(new IntInterval());
        MapDomain<String, IntInterval> mapDomain2 = new MapDomain<>(new IntInterval());
        mapDomain1.put("x", new IntInterval(1, 5));
        mapDomain2.put("x", new IntInterval(3, 7));
        Assert.assertEquals(AbstractValueKind.VAL, mapDomain1.getKind());
        Assert.assertEquals(AbstractValueKind.VAL, mapDomain2.getKind());
        mapDomain1.meetWith(mapDomain2);
        Assert.assertTrue(new IntInterval(3, 5).equals(mapDomain1.get("x")));
    }

    @Test
    public void testEquals() {
        Map<String, IntInterval> map1 = new HashMap<>();
        map1.put("x", new IntInterval(1, 5));
        Map<String, IntInterval> map2 = new HashMap<>();
        map2.put("x", new IntInterval(1, 5));
        MapDomain<String, IntInterval> mapDomain1 = new MapDomain<>(map1, new IntInterval());
        MapDomain<String, IntInterval> mapDomain2 = new MapDomain<>(map2, new IntInterval());
        Assert.assertTrue(mapDomain1.equals(mapDomain2));
    }

    @Test
    public void testLeq() {
        MapDomain<String, IntInterval> mapDomain1 = new MapDomain<>(new IntInterval());
        MapDomain<String, IntInterval> mapDomain2 = new MapDomain<>(new IntInterval());
        mapDomain1.put("x", new IntInterval(1, 5));
        mapDomain2.put("x", new IntInterval(1, 10));
        Assert.assertTrue(mapDomain1.leq(mapDomain2));
        Assert.assertFalse(mapDomain2.leq(mapDomain1));
    }

    @Test
    public void testCopyOf() {
        MapDomain<String, IntInterval> mapDomain = new MapDomain<>(new IntInterval());
        mapDomain.put("x", new IntInterval(1, 5));
        MapDomain<String, IntInterval> copy = mapDomain.copyOf();
        Assert.assertTrue(mapDomain.equals(copy));
        Assert.assertNotSame(mapDomain, copy);
    }

    @Test
    public void testGetDomainAtKey() {
        Map<String, BooleanAndDomain> map = new HashMap<>();
        map.put("key1", new BooleanAndDomain(true));
        MapDomain<String, BooleanAndDomain> mapDomain = new MapDomain<>(map, new BooleanAndDomain(false));

        Assert.assertTrue(mapDomain.get("key1").getValue());
        BooleanAndDomain result = mapDomain.get("key2");
        Assert.assertTrue(result.isTop());
    }

    @Test
    public void testTransform() {
        Map<String, BooleanAndDomain> map = new HashMap<>();
        map.put("key1", new BooleanAndDomain(true));
        map.put("key2", new BooleanAndDomain(false));
        MapDomain<String, BooleanAndDomain> mapDomain = new MapDomain<>(map, new BooleanAndDomain(false));

        mapDomain.transform(BooleanAndDomain::getNegated);

        Assert.assertFalse(mapDomain.get("key1").getValue());
        Assert.assertTrue(mapDomain.get("key2").getValue());
    }

    @Test
    public void testFilter() {
        Map<String, BooleanAndDomain> map = new HashMap<>();
        map.put("key1", new BooleanAndDomain(true));
        map.put("key2", new BooleanAndDomain(false));
        MapDomain<String, BooleanAndDomain> mapDomain = new MapDomain<>(map, new BooleanAndDomain(false));

        /* keep only the true values in the map */
        mapDomain.filter((entry) -> entry.getValue().getValue());
        Assert.assertTrue(mapDomain.get("key1").getValue());
        Assert.assertEquals(1, mapDomain.getSize());
    }
}