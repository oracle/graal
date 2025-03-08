package com.oracle.svm.graal.test.ai;

import com.oracle.svm.hosted.analysis.ai.domain.EnvironmentDomain;
import com.oracle.svm.hosted.analysis.ai.domain.IntInterval;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.access.PlaceHolderAccessPathBase;
import org.junit.Assert;
import org.junit.Test;

public class EnvironmentDomainTest {

    @Test
    public void testPutAndGet() {
        EnvironmentDomain<IntInterval> domain = new EnvironmentDomain<>(new IntInterval());
        AccessPath path = new AccessPath(new PlaceHolderAccessPathBase("var")).appendField("field", 0);
        domain.put(path, new IntInterval(1, 5));
        Assert.assertEquals(new IntInterval(1, 5), domain.get(path));
    }

    @Test
    public void testRemove() {
        EnvironmentDomain<IntInterval> domain = new EnvironmentDomain<>(new IntInterval());
        AccessPath path = new AccessPath(new PlaceHolderAccessPathBase("var")).appendField("field", 0);
        domain.put(path, new IntInterval(1, 5));
        domain.remove(path);
        Assert.assertTrue(domain.get(path).isTop());
    }

    @Test
    public void testAlias() {
        EnvironmentDomain<IntInterval> domain = new EnvironmentDomain<>(new IntInterval());
        AccessPath original = new AccessPath(new PlaceHolderAccessPathBase("var")).appendField("field", 0);
        AccessPath alias = new AccessPath(new PlaceHolderAccessPathBase("alias")).appendField("field", 0);
        domain.createAlias(original, alias);
        domain.put(original, new IntInterval(1, 5));
        Assert.assertEquals(new IntInterval(1, 5), domain.get(alias));
    }

    @Test
    public void testJoinWith() {
        EnvironmentDomain<IntInterval> domain1 = new EnvironmentDomain<>(new IntInterval());
        EnvironmentDomain<IntInterval> domain2 = new EnvironmentDomain<>(new IntInterval());
        AccessPath path1 = new AccessPath(new PlaceHolderAccessPathBase("var1")).appendField("field", 0);
        AccessPath path2 = new AccessPath(new PlaceHolderAccessPathBase("var2")).appendField("field", 0);
        domain1.put(path1, new IntInterval(1, 5));
        domain2.put(path2, new IntInterval(3, 7));
        domain1.joinWith(domain2);
        Assert.assertEquals(new IntInterval(1, 5), domain1.get(path1));
        Assert.assertEquals(new IntInterval(3, 7), domain1.get(path2));
    }

    @Test
    public void testIntersectionWith() {
        EnvironmentDomain<IntInterval> domain1 = new EnvironmentDomain<>(new IntInterval());
        EnvironmentDomain<IntInterval> domain2 = new EnvironmentDomain<>(new IntInterval());
        AccessPath path = new AccessPath(new PlaceHolderAccessPathBase("var")).appendField("field", 0);
        domain1.put(path, new IntInterval(1, 5));
        domain2.put(path, new IntInterval(3, 7));
        domain1.intersectionWith(domain2);
        Assert.assertEquals(new IntInterval(3, 5), domain1.get(path));
    }

    @Test
    public void testDifferenceWith() {
        EnvironmentDomain<IntInterval> domain1 = new EnvironmentDomain<>(new IntInterval());
        EnvironmentDomain<IntInterval> domain2 = new EnvironmentDomain<>(new IntInterval());
        AccessPath path = new AccessPath(new PlaceHolderAccessPathBase("var")).appendField("field", 0);
        domain1.put(path, new IntInterval(1, 5));
        domain2.put(path, new IntInterval(3, 7));
        domain1.differenceWith((a, b) -> new IntInterval(a.getLowerBound() - b.getLowerBound(), a.getUpperBound() - b.getUpperBound()), domain2.getValue());
        Assert.assertEquals(new IntInterval(-2, -2), domain1.get(path));
    }

    @Test
    public void testMeetWith() {
        EnvironmentDomain<IntInterval> domain1 = new EnvironmentDomain<>(new IntInterval());
        EnvironmentDomain<IntInterval> domain2 = new EnvironmentDomain<>(new IntInterval());

        AccessPath path1 = new AccessPath(new PlaceHolderAccessPathBase("x")).appendField("field1", 0);
        AccessPath path2 = new AccessPath(new PlaceHolderAccessPathBase("y")).appendField("field2", 0);

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
        EnvironmentDomain<IntInterval> domain = new EnvironmentDomain<>(new IntInterval());

        AccessPath originalPath = new AccessPath(new PlaceHolderAccessPathBase("x")).appendField("field1", 0);
        AccessPath aliasPath = new AccessPath(new PlaceHolderAccessPathBase("y")).appendField("field2", 0);

        domain.put(originalPath, new IntInterval(1, 5));
        domain.createAlias(originalPath, aliasPath);

        Assert.assertEquals(new IntInterval(1, 5), domain.get(aliasPath));
    }

    @Test
    public void testRemoveAlias() {
        EnvironmentDomain<IntInterval> domain = new EnvironmentDomain<>(new IntInterval());

        AccessPath originalPath = new AccessPath(new PlaceHolderAccessPathBase("x")).appendField("field1", 0);
        AccessPath aliasPath = new AccessPath(new PlaceHolderAccessPathBase("y")).appendField("field2", 0);

        domain.put(originalPath, new IntInterval(1, 5));
        domain.createAlias(originalPath, aliasPath);
        domain.remove(aliasPath);

        Assert.assertTrue(domain.get(originalPath).isTop());
    }

    @Test
    public void testCopyOf() {
        EnvironmentDomain<IntInterval> domain = new EnvironmentDomain<>(new IntInterval());

        AccessPath path = new AccessPath(new PlaceHolderAccessPathBase("x")).appendField("field1", 0);
        domain.put(path, new IntInterval(1, 5));

        EnvironmentDomain<IntInterval> copy = domain.copyOf();
        Assert.assertEquals(domain, copy);
        Assert.assertNotSame(domain, copy);
    }
}