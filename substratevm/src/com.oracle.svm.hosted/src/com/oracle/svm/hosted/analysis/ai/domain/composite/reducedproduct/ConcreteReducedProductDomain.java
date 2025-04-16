package com.oracle.svm.hosted.analysis.ai.domain.composite.reducedproduct;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.domain.SetDomain;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;

import java.util.List;

/**
 * Concrete implementation of a reduced product domain for demonstration.
 * Combines interval and set domains with custom reduction logic.
 */
public class ConcreteReducedProductDomain extends ReducedProductDomain<ConcreteReducedProductDomain> {

    private ConcreteReducedProductDomain(List<AbstractDomain<?>> domains, List<Reducer> reducers) {
        super(domains, reducers);
    }

    private ConcreteReducedProductDomain(ConcreteReducedProductDomain other) {
        super(other);
    }

    @Override
    public ConcreteReducedProductDomain copyOf() {
        return new ConcreteReducedProductDomain(this);
    }

    public IntInterval getIntervalDomain() {
        return getDomain(0);
    }

    public SetDomain<Integer> getSetDomain() {
        return getDomain(1);
    }

    public static Builder<ConcreteReducedProductDomain> builder() {
        return new Builder<>(ConcreteReducedProductDomain::new);
    }

    /**
     * Example usage demonstrating how to create and use this domain.
     */
    public static ConcreteReducedProductDomain createExample() {
        IntInterval interval = new IntInterval(0, 10);
        SetDomain<Integer> set = new SetDomain<>();
        set.add(5);
        set.add(8);

        return builder()
                .withDomains(interval, set)
                .withReducer(domains -> {
                    // Example reduction: restrict interval based on set values
                    IntInterval intervalDomain = (IntInterval) domains.get(0);
                    @SuppressWarnings("unchecked")
                    SetDomain<Integer> setDomain = (SetDomain<Integer>) domains.get(1);

                    // If set is empty, make interval empty
                    if (setDomain.empty() && !intervalDomain.isBot()) {
                        intervalDomain.setToBot();
                    }

                    // Remove set elements outside interval
                    setDomain.removeIf(value ->
                            value < intervalDomain.getLowerBound() ||
                                    value > intervalDomain.getUpperBound());
                })
                .build();
    }
}
