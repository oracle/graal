package com.oracle.svm.hosted.analysis.ai.domain.composite.reducedproduct;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.SignDomain;
import com.oracle.svm.hosted.analysis.ai.domain.value.Sign;

import java.util.List;


public final class IntervalSignReducedProductDomain extends ReducedProductDomain<IntervalSignReducedProductDomain> {

    private IntervalSignReducedProductDomain(List<AbstractDomain<?>> domains, List<Reducer> reducers) {
        super(domains, reducers);
    }

    private IntervalSignReducedProductDomain(IntervalSignReducedProductDomain other) {
        super(other);
    }

    @Override
    public IntervalSignReducedProductDomain copyOf() {
        return new IntervalSignReducedProductDomain(this);
    }

    public IntInterval getInterval() {
        return getDomain(0);
    }

    public SignDomain getSign() {
        return (SignDomain) getDomain(1);
    }

    /**
     * Creates an IntervalSignReducedProductDomain with the given interval and sign domains.
     */
    public static IntervalSignReducedProductDomain create(IntInterval interval, SignDomain sign) {
        return new Builder<>(IntervalSignReducedProductDomain::new)
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
