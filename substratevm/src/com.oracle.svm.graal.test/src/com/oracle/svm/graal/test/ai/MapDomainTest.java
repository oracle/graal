package com.oracle.svm.graal.test.ai;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.domain.BooleanAndDomain;
import com.oracle.svm.hosted.analysis.ai.domain.MapDomain;
import com.oracle.svm.hosted.analysis.ai.domain.IntInterval;
import com.oracle.svm.hosted.analysis.ai.value.AbstractValueKind;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

class TestMapDomain<Key, Domain extends AbstractDomain<Domain>>
        extends MapDomain<Key, Domain, TestMapDomain<Key, Domain>> {

    public TestMapDomain(Domain initialDomain) {
        super(initialDomain);
    }

    public TestMapDomain(Map<Key, Domain> map, Domain initialDomain) {
        super(map, initialDomain);
    }

    public TestMapDomain(TestMapDomain<Key, Domain> other) {
        super(other);
    }

    @Override
    public TestMapDomain<Key, Domain> copyOf() {
        return new TestMapDomain<>(this);
    }
}

public class MapDomainTest {

    @Test
    public void testDefaultConstructor() {
        /* Default MapDomain is BOT */
        TestMapDomain<String, IntInterval> mapDomain = new TestMapDomain<>(new IntInterval());
        Assert.assertTrue(mapDomain.isBot());
        Assert.assertTrue(mapDomain.get("x").isTop());
        Assert.assertEquals(0, mapDomain.getSize());
    }

    @Test
    public void testPutAndGet() {
        TestMapDomain<String, IntInterval> mapDomain = new TestMapDomain<>(new IntInterval());
        mapDomain.put("x", new IntInterval(1, 5));
        Assert.assertEquals(new IntInterval(1, 5), mapDomain.get("x"));
    }

    @Test
    public void testSetToBot() {
        TestMapDomain<String, IntInterval> mapDomain = new TestMapDomain<>(new IntInterval());
        mapDomain.put("x", new IntInterval(1, 5));
        mapDomain.setToBot();
        Assert.assertTrue(mapDomain.isBot());
        Assert.assertEquals(0, mapDomain.getSize());
    }

    @Test
    public void testJoinWith() {
        TestMapDomain<String, IntInterval> mapDomain1 = new TestMapDomain<>(new IntInterval());
        TestMapDomain<String, IntInterval> mapDomain2 = new TestMapDomain<>(new IntInterval());
        mapDomain1.put("x", new IntInterval(1, 5));
        mapDomain2.put("x", new IntInterval(3, 7));
        Assert.assertEquals(AbstractValueKind.VAL, mapDomain1.getKind());
        Assert.assertEquals(AbstractValueKind.VAL, mapDomain2.getKind());
        mapDomain1.joinWith(mapDomain2);
        Assert.assertEquals(new IntInterval(1, 7), mapDomain1.get("x"));
    }

    @Test
    public void testJoinWithNonEmptyDifference() {
        TestMapDomain<String, IntInterval> mapDomain1 = new TestMapDomain<>(new IntInterval());
        TestMapDomain<String, IntInterval> mapDomain2 = new TestMapDomain<>(new IntInterval());
        mapDomain1.put("x", new IntInterval(1, 5));
        mapDomain2.put("y", new IntInterval(3, 7));
        Assert.assertEquals(AbstractValueKind.VAL, mapDomain1.getKind());
        Assert.assertEquals(AbstractValueKind.VAL, mapDomain2.getKind());
        mapDomain1.joinWith(mapDomain2);
        Assert.assertEquals(new IntInterval(1, 5), mapDomain1.get("x"));
        Assert.assertEquals(new IntInterval(3, 7), mapDomain1.get("y"));
    }

    @Test
    public void testMeetWith() {
        TestMapDomain<String, IntInterval> mapDomain1 = new TestMapDomain<>(new IntInterval());
        TestMapDomain<String, IntInterval> mapDomain2 = new TestMapDomain<>(new IntInterval());
        mapDomain1.put("x", new IntInterval(1, 5));
        mapDomain2.put("x", new IntInterval(3, 7));
        Assert.assertEquals(AbstractValueKind.VAL, mapDomain1.getKind());
        Assert.assertEquals(AbstractValueKind.VAL, mapDomain2.getKind());
        mapDomain1.meetWith(mapDomain2);
        Assert.assertEquals(new IntInterval(3, 5), mapDomain1.get("x"));
    }

    @Test
    public void testEquals() {
        Map<String, IntInterval> map1 = new HashMap<>();
        map1.put("x", new IntInterval(1, 5));
        Map<String, IntInterval> map2 = new HashMap<>();
        map2.put("x", new IntInterval(1, 5));
        TestMapDomain<String, IntInterval> mapDomain1 = new TestMapDomain<>(map1, new IntInterval());
        TestMapDomain<String, IntInterval> mapDomain2 = new TestMapDomain<>(map2, new IntInterval());
        Assert.assertEquals(mapDomain1, mapDomain2);
    }

    @Test
    public void testLeq() {
        TestMapDomain<String, IntInterval> mapDomain1 = new TestMapDomain<>(new IntInterval());
        TestMapDomain<String, IntInterval> mapDomain2 = new TestMapDomain<>(new IntInterval());
        mapDomain1.put("x", new IntInterval(1, 5));
        mapDomain2.put("x", new IntInterval(1, 10));
        Assert.assertTrue(mapDomain1.leq(mapDomain2));
        Assert.assertFalse(mapDomain2.leq(mapDomain1));
    }

    @Test
    public void testCopyOf() {
        TestMapDomain<String, IntInterval> mapDomain = new TestMapDomain<>(new IntInterval());
        mapDomain.put("x", new IntInterval(1, 5));
        TestMapDomain<String, IntInterval> copy = mapDomain.copyOf();
        Assert.assertEquals(mapDomain, copy);
        Assert.assertNotSame(mapDomain, copy);
    }

    @Test
    public void testGetDomainAtKey() {
        Map<String, BooleanAndDomain> map = new HashMap<>();
        map.put("key1", new BooleanAndDomain(true));
        TestMapDomain<String, BooleanAndDomain> mapDomain = new TestMapDomain<>(map, new BooleanAndDomain(false));

        Assert.assertTrue(mapDomain.get("key1").getValue());
        BooleanAndDomain result = mapDomain.get("key2");
        Assert.assertTrue(result.isTop());
    }

    @Test
    public void testRemove() {
        TestMapDomain<String, IntInterval> mapDomain = new TestMapDomain<>(new IntInterval());
        mapDomain.put("x", new IntInterval(1, 5));
        mapDomain.remove("x");
        Assert.assertTrue(mapDomain.isBot());
    }

    @Test
    public void testUpdate() {
        TestMapDomain<String, IntInterval> mapDomain = new TestMapDomain<>(new IntInterval());
        mapDomain.put("x", new IntInterval(1, 5));
        mapDomain.update(interval -> new IntInterval(interval.getLowerBound() + 1, interval.getUpperBound() + 1), "x");
        Assert.assertEquals(new IntInterval(2, 6), mapDomain.get("x"));
    }

    @Test
    public void testTransform() {
        Map<String, BooleanAndDomain> map = new HashMap<>();
        map.put("key1", new BooleanAndDomain(true));
        map.put("key2", new BooleanAndDomain(false));
        TestMapDomain<String, BooleanAndDomain> mapDomain = new TestMapDomain<>(map, new BooleanAndDomain(false));

        mapDomain.transform(BooleanAndDomain::getNegated);

        Assert.assertFalse(mapDomain.get("key1").getValue());
        Assert.assertTrue(mapDomain.get("key2").getValue());
    }

    @Test
    public void testRemoveIf() {
        Map<String, BooleanAndDomain> map = new HashMap<>();
        map.put("key1", new BooleanAndDomain(true));
        map.put("key2", new BooleanAndDomain(false));
        TestMapDomain<String, BooleanAndDomain> mapDomain = new TestMapDomain<>(map, new BooleanAndDomain(false));

        /* keep only the true values in the map */
        mapDomain.removeIf((entry) -> entry.getValue().getValue());
        Assert.assertTrue(mapDomain.get("key1").getValue());
        Assert.assertEquals(1, mapDomain.getSize());
    }
}