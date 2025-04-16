package com.oracle.svm.graal.test.ai.domain;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.domain.composite.reducedproduct.ReducedProductDomain;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.SignDomain;
import com.oracle.svm.hosted.analysis.ai.domain.value.Sign;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class ReducedProductDomainTest {

    /**
     * Implementation of ReducedProductDomain that combines interval and sign domains.
     */
    static class IntervalSignDomain extends ReducedProductDomain<IntervalSignDomain> {

        private IntervalSignDomain(List<AbstractDomain<?>> domains, List<Reducer> reducers) {
            super(domains, reducers);
        }

        private IntervalSignDomain(IntervalSignDomain other) {
            super(other);
        }

        @Override
        public IntervalSignDomain copyOf() {
            return new IntervalSignDomain(this);
        }

        public IntInterval getInterval() {
            return getDomain(0);
        }

        public SignDomain getSign() {
            return (SignDomain) getDomain(1);
        }

        /**
         * Creates an IntervalSignDomain with the given interval and sign domains.
         */
        public static IntervalSignDomain create(IntInterval interval, SignDomain sign) {
            return new Builder<>(IntervalSignDomain::new)
                    .withDomains(interval, sign)
                    .withReducer(domains -> {
                        IntInterval intervalDomain = (IntInterval) domains.get(0);
                        SignDomain signDomain = (SignDomain) domains.get(1);

                        // Refine interval based on sign
                        long lowerBound = intervalDomain.getLowerBound();
                        long upperBound = intervalDomain.getUpperBound();
                        boolean changed = false;

                        if (signDomain.getState() == Sign.POS) {
                            if (lowerBound < 1) {
                                lowerBound = 1;
                                changed = true;
                            }
                        } else if (signDomain.getState() == Sign.NEG) {
                            if (upperBound > -1) {
                                upperBound = -1;
                                changed = true;
                            }
                        } else if (signDomain.getState() == Sign.ZERO) {
                            if (lowerBound != 0 || upperBound != 0) {
                                lowerBound = 0;
                                upperBound = 0;
                                changed = true;
                            }
                        }

                        // Create new interval if bounds changed
                        if (changed) {
                            domains.set(0, new IntInterval(lowerBound, upperBound));
                            intervalDomain = (IntInterval) domains.getFirst();
                        }

                        // Refine sign based on interval
                        if (intervalDomain.getLowerBound() > 0) {
                            signDomain.setState(Sign.POS);
                        } else if (intervalDomain.getUpperBound() < 0) {
                            signDomain.setState(Sign.NEG);
                        } else if (intervalDomain.getLowerBound() == 0 && intervalDomain.getUpperBound() == 0) {
                            signDomain.setState(Sign.ZERO);
                        }
                    })
                    .build();
        }
    }

    @Test
    public void testIntervalRefinedBySign() {
        IntInterval interval = new IntInterval(-10, 10);
        SignDomain sign = new SignDomain(Sign.POS);

        IntervalSignDomain domain = IntervalSignDomain.create(interval, sign);

        // Since sign is positive, interval should be refined to [1, 10]
        Assert.assertEquals(1, domain.getInterval().getLowerBound());
        Assert.assertEquals(10, domain.getInterval().getUpperBound());
    }

    @Test
    public void testSignRefinedByInterval() {
        IntInterval interval = new IntInterval(5, 10);
        SignDomain sign = new SignDomain(Sign.TOP);

        IntervalSignDomain domain = IntervalSignDomain.create(interval, sign);

        // Since interval is [5, 10], sign should be refined to positive
        Assert.assertEquals(Sign.POS, domain.getSign().getState());
    }
//
//    @Test
//    public void testJoinWith() {
//        IntervalSignDomain domain1 = IntervalSignDomain.create(
//                new IntInterval(1, 5), new SignDomain(Sign.POS));
//        IntervalSignDomain domain2 = IntervalSignDomain.create(
//                new IntInterval(-3, -1), new SignDomain(Sign.NEG));
//
//        domain1.joinWith(domain2);
//
//
//        Assert.assertEquals(5, domain1.getInterval().getUpperBound());
//        Assert.assertEquals(Sign.TOP, domain1.getSign().getState());
//    }

    @Test
    public void testMeetWith() {
        IntervalSignDomain domain1 = IntervalSignDomain.create(
                new IntInterval(-5, 10), new SignDomain(Sign.TOP));
        IntervalSignDomain domain2 = IntervalSignDomain.create(
                new IntInterval(-10, 7), new SignDomain(Sign.POS));

        domain1.meetWith(domain2);

        // Meeting [-5,10] and [-10,7] should result in [-5,7]
        Assert.assertEquals(1, domain1.getInterval().getLowerBound());
        Assert.assertEquals(7, domain1.getInterval().getUpperBound());

        // Meeting TOP and POS should result in POS
        // Additionally, the reducer should refine the interval to [1,7]
        Assert.assertEquals(Sign.POS, domain1.getSign().getState());
        Assert.assertEquals(1, domain1.getInterval().getLowerBound());
    }

    @Test
    public void testZeroHandling() {
        IntInterval interval = new IntInterval(0, 0);
        SignDomain sign = new SignDomain(Sign.TOP);

        IntervalSignDomain domain = IntervalSignDomain.create(interval, sign);

        // Interval [0,0] should refine sign to ZERO
        Assert.assertEquals(Sign.ZERO, domain.getSign().getState());

        // Test the opposite direction
        domain = IntervalSignDomain.create(new IntInterval(-10, 10), new SignDomain(Sign.ZERO));

        // Sign ZERO should refine interval to [0,0]
        Assert.assertEquals(0, domain.getInterval().getLowerBound());
        Assert.assertEquals(0, domain.getInterval().getUpperBound());
    }
}