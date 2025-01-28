package com.oracle.svm.graal.test.ai;

import com.oracle.svm.hosted.analysis.ai.domain.AccessPathMapDomain;
import com.oracle.svm.hosted.analysis.ai.domain.IntInterval;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPath;
import org.junit.Assert;
import org.junit.Test;

public class AccessPathDomainTest {

    @Test
    public void testPutAndGet() {
        AccessPathMapDomain<IntInterval> domain = new AccessPathMapDomain<>(new IntInterval());
        AccessPath path = new AccessPath("var").appendField("field");
        domain.put(path, new IntInterval(1, 5));
        Assert.assertEquals(new IntInterval(1, 5), domain.get(path));
    }

    @Test
    public void testRemove() {
        AccessPathMapDomain<IntInterval> domain = new AccessPathMapDomain<>(new IntInterval());
        AccessPath path = new AccessPath("var").appendField("field");
        domain.put(path, new IntInterval(1, 5));
        domain.remove(path);
        Assert.assertTrue(domain.get(path).isTop());
    }

    @Test
    public void testAlias() {
        AccessPathMapDomain<IntInterval> domain = new AccessPathMapDomain<>(new IntInterval());
        AccessPath original = new AccessPath("var").appendField("field");
        AccessPath alias = new AccessPath("alias").appendField("field");
        domain.createAlias(original, alias);
        domain.put(original, new IntInterval(1, 5));
        Assert.assertEquals(new IntInterval(1, 5), domain.get(alias));
    }

    @Test
    public void testJoinWith() {
        AccessPathMapDomain<IntInterval> domain1 = new AccessPathMapDomain<>(new IntInterval());
        AccessPathMapDomain<IntInterval> domain2 = new AccessPathMapDomain<>(new IntInterval());
        AccessPath path = new AccessPath("var").appendField("field");
        domain1.put(path, new IntInterval(1, 5));
        domain2.put(path, new IntInterval(3, 7));
        domain1.joinWith(domain2);
        Assert.assertEquals(new IntInterval(1, 7), domain1.get(path));
    }

    @Test
    public void testIntersectionWith() {
        AccessPathMapDomain<IntInterval> domain1 = new AccessPathMapDomain<>(new IntInterval());
        AccessPathMapDomain<IntInterval> domain2 = new AccessPathMapDomain<>(new IntInterval());
        AccessPath path = new AccessPath("var").appendField("field");
        domain1.put(path, new IntInterval(1, 5));
        domain2.put(path, new IntInterval(3, 7));
        domain1.intersectionWith(domain2);
        Assert.assertEquals(new IntInterval(3, 5), domain1.get(path));
    }

    @Test
    public void testDifferenceWith() {
        AccessPathMapDomain<IntInterval> domain1 = new AccessPathMapDomain<>(new IntInterval());
        AccessPathMapDomain<IntInterval> domain2 = new AccessPathMapDomain<>(new IntInterval());
        AccessPath path = new AccessPath("var").appendField("field");
        domain1.put(path, new IntInterval(1, 5));
        domain2.put(path, new IntInterval(3, 7));
        domain1.differenceWith((a, b) -> new IntInterval(a.getLowerBound() - b.getLowerBound(), a.getUpperBound() - b.getUpperBound()), domain2.getValue());
        Assert.assertEquals(new IntInterval(-2, -2), domain1.get(path));
    }

    @Test
    public void testMeetWith() {
        AccessPathMapDomain<IntInterval> domain1 = new AccessPathMapDomain<>(new IntInterval());
        AccessPathMapDomain<IntInterval> domain2 = new AccessPathMapDomain<>(new IntInterval());

        AccessPath path1 = new AccessPath("x").appendField("field1");
        AccessPath path2 = new AccessPath("y").appendField("field2");

        domain1.put(path1, new IntInterval(1, 5));
        domain1.put(path2, new IntInterval(2, 6));
        domain2.put(path1, new IntInterval(3, 7));
        domain2.put(path2, new IntInterval(4, 8));

        domain1.meetWith(domain2);

        Assert.assertEquals(new IntInterval(3, 5), domain1.get(path1));
        Assert.assertEquals(new IntInterval(4, 6), domain1.get(path2));
    }

    @Test
    public void testAliasResolution() {
        AccessPathMapDomain<IntInterval> domain = new AccessPathMapDomain<>(new IntInterval());

        AccessPath originalPath = new AccessPath("x").appendField("field1");
        AccessPath aliasPath = new AccessPath("y").appendField("field2");

        domain.put(originalPath, new IntInterval(1, 5));
        domain.createAlias(originalPath, aliasPath);

        Assert.assertEquals(new IntInterval(1, 5), domain.get(aliasPath));
    }

    @Test
    public void testRemoveAlias() {
        AccessPathMapDomain<IntInterval> domain = new AccessPathMapDomain<>(new IntInterval());

        AccessPath originalPath = new AccessPath("x").appendField("field1");
        AccessPath aliasPath = new AccessPath("y").appendField("field2");

        domain.put(originalPath, new IntInterval(1, 5));
        domain.createAlias(originalPath, aliasPath);
        domain.remove(aliasPath);

        Assert.assertTrue(domain.get(originalPath).isTop());
    }

    @Test
    public void testCopyOf() {
        AccessPathMapDomain<IntInterval> domain = new AccessPathMapDomain<>(new IntInterval());

        AccessPath path = new AccessPath("x").appendField("field1");
        domain.put(path, new IntInterval(1, 5));

        AccessPathMapDomain<IntInterval> copy = domain.copyOf();
        Assert.assertEquals(domain, copy);
        Assert.assertNotSame(domain, copy);
    }
}