package com.oracle.svm.graal.test.ai.domain;

import com.oracle.svm.hosted.analysis.ai.domain.BooleanAndDomain;
import com.oracle.svm.hosted.analysis.ai.domain.ProductDomain;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class ProductDomainTest {

    @Test
    public void testDefaultConstructor() {
        ProductDomain productDomain = new ProductDomain();
        Assert.assertTrue(productDomain.isBot());
    }

    @Test
    public void testConstructorWithDomains() {
        IntInterval intInterval = new IntInterval(1, 5);
        BooleanAndDomain booleanDomain = new BooleanAndDomain(true);
        ProductDomain productDomain = new ProductDomain(Arrays.asList(intInterval, booleanDomain));
        Assert.assertFalse(productDomain.isBot());
    }

    @Test
    public void testCopyConstructor() {
        IntInterval intInterval = new IntInterval(1, 5);
        BooleanAndDomain booleanDomain = new BooleanAndDomain(true);
        ProductDomain productDomain = new ProductDomain(Arrays.asList(intInterval, booleanDomain));
        ProductDomain copy = new ProductDomain(productDomain);
        Assert.assertTrue(productDomain.equals(copy));
        Assert.assertNotSame(productDomain, copy);
    }

    @Test
    public void testLeq() {
        Assert.assertEquals(new IntInterval(1, 2), new IntInterval(1, 2));
        IntInterval intInterval1 = new IntInterval(1, 5);
        BooleanAndDomain booleanDomain1 = new BooleanAndDomain(true);
        ProductDomain productDomain1 = new ProductDomain(Arrays.asList(intInterval1, booleanDomain1));

        IntInterval intInterval2 = new IntInterval(1, 10);
        BooleanAndDomain booleanDomain2 = new BooleanAndDomain(true);
        ProductDomain productDomain2 = new ProductDomain(Arrays.asList(intInterval2, booleanDomain2));

        Assert.assertTrue(productDomain1.leq(productDomain2));
        Assert.assertFalse(productDomain2.leq(productDomain1));
    }

    @Test
    public void testEquals() {
        IntInterval intInterval1 = new IntInterval(1, 5);
        BooleanAndDomain booleanDomain1 = new BooleanAndDomain(true);
        ProductDomain productDomain1 = new ProductDomain(Arrays.asList(intInterval1, booleanDomain1));

        IntInterval intInterval2 = new IntInterval(1, 5);
        BooleanAndDomain booleanDomain2 = new BooleanAndDomain(true);
        ProductDomain productDomain2 = new ProductDomain(Arrays.asList(intInterval2, booleanDomain2));
        Assert.assertEquals(productDomain1, productDomain2);
        Assert.assertTrue(productDomain1.equals(productDomain2));
    }

    @Test
    public void testSetToBot() {
        IntInterval intInterval = new IntInterval(1, 5);
        BooleanAndDomain booleanDomain = new BooleanAndDomain(true);
        ProductDomain productDomain = new ProductDomain(Arrays.asList(intInterval, booleanDomain));
        productDomain.setToBot();
        Assert.assertTrue(productDomain.isBot());
    }

    @Test
    public void testSetToTop() {
        IntInterval intInterval = new IntInterval(1, 5);
        BooleanAndDomain booleanDomain = new BooleanAndDomain(true);
        ProductDomain productDomain = new ProductDomain(Arrays.asList(intInterval, booleanDomain));
        productDomain.setToTop();
        Assert.assertTrue(productDomain.isTop());
    }

    @Test
    public void testJoinWith() {
        IntInterval intInterval1 = new IntInterval(1, 5);
        BooleanAndDomain booleanDomain1 = new BooleanAndDomain(true);
        ProductDomain productDomain1 = new ProductDomain(Arrays.asList(intInterval1, booleanDomain1));

        IntInterval intInterval2 = new IntInterval(3, 7);
        BooleanAndDomain booleanDomain2 = new BooleanAndDomain(false);
        ProductDomain productDomain2 = new ProductDomain(Arrays.asList(intInterval2, booleanDomain2));

        productDomain1.joinWith(productDomain2);
        Assert.assertEquals(new IntInterval(1, 7), productDomain1.getDomains().get(0));
        Assert.assertFalse(((BooleanAndDomain) productDomain1.getDomains().get(1)).getValue());
    }

    @Test
    public void testWidenWith() {
        IntInterval intInterval1 = new IntInterval(1, 5);
        BooleanAndDomain booleanDomain1 = new BooleanAndDomain(true);
        ProductDomain productDomain1 = new ProductDomain(Arrays.asList(intInterval1, booleanDomain1));

        IntInterval intInterval2 = new IntInterval(3, 7);
        BooleanAndDomain booleanDomain2 = new BooleanAndDomain(false);
        ProductDomain productDomain2 = new ProductDomain(Arrays.asList(intInterval2, booleanDomain2));

        productDomain1.widenWith(productDomain2);
        Assert.assertEquals(new IntInterval(1, IntInterval.MAX), productDomain1.getDomains().get(0));
        Assert.assertFalse(((BooleanAndDomain) productDomain1.getDomains().get(1)).getValue());
    }

    @Test
    public void testMeetWith() {
        IntInterval intInterval1 = new IntInterval(1, 5);
        BooleanAndDomain booleanDomain1 = new BooleanAndDomain(true);
        ProductDomain productDomain1 = new ProductDomain(Arrays.asList(intInterval1, booleanDomain1));

        IntInterval intInterval2 = new IntInterval(3, 7);
        BooleanAndDomain booleanDomain2 = new BooleanAndDomain(false);
        ProductDomain productDomain2 = new ProductDomain(Arrays.asList(intInterval2, booleanDomain2));

        productDomain1.meetWith(productDomain2);
        Assert.assertEquals(new IntInterval(3, 5), productDomain1.getDomains().get(0));
        Assert.assertTrue(((BooleanAndDomain) productDomain1.getDomains().get(1)).getValue());
    }
}
