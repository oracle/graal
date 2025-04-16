package com.oracle.svm.hosted.analysis.ai.domain.composite.reducedproduct;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A reduced product domain that combines multiple domains and allows information to flow between them
 * through reduction operations.
 *
 * @param <Domain> type of the derived ReducedProductDomain
 */
public class ReducedProductDomain<Domain extends ReducedProductDomain<Domain>> extends AbstractDomain<Domain> {

    private final List<AbstractDomain<?>> domains;
    private final List<Reducer> reducers;

    /**
     * Interface for defining reduction operations between domains.
     */
    @FunctionalInterface
    public interface Reducer {
        void reduce(List<AbstractDomain<?>> domains);
    }

    /**
     * Constructor for a reduced product domain with the given domains and reducers.
     *
     * @param domains  the abstract domains forming the product
     * @param reducers the reduction operations between domains
     */
    @SuppressWarnings("this-escape")
    protected ReducedProductDomain(List<AbstractDomain<?>> domains, List<Reducer> reducers) {
        this.domains = new ArrayList<>(domains.size());
        for (AbstractDomain<?> domain : domains) {
            this.domains.add(domain.copyOf());
        }
        this.reducers = new ArrayList<>(reducers);
        applyReduction();
    }

    /**
     * Copy constructor.
     *
     * @param other the domain to copy
     */
    protected ReducedProductDomain(ReducedProductDomain<Domain> other) {
        this.domains = new ArrayList<>(other.domains.size());
        for (AbstractDomain<?> domain : other.domains) {
            this.domains.add(domain.copyOf());
        }
        this.reducers = new ArrayList<>(other.reducers);
    }

    /**
     * Gets the domain at the specified index.
     *
     * @param index the index of the domain
     * @return the domain at the given index
     */
    @SuppressWarnings("unchecked")
    public <T extends AbstractDomain<T>> T getDomain(int index) {
        return (T) domains.get(index);
    }

    /**
     * Applies all reduction operations.
     */
    protected void applyReduction() {
        for (Reducer reducer : reducers) {
            reducer.reduce(domains);
        }
    }

    @Override
    public boolean isBot() {
        return domains.stream().anyMatch(AbstractDomain::isBot);
    }

    @Override
    public boolean isTop() {
        return domains.stream().allMatch(AbstractDomain::isTop);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean leq(Domain other) {
        ReducedProductDomain<?> otherDomain = other;
        if (domains.size() != otherDomain.domains.size()) {
            return false;
        }

        for (int i = 0; i < domains.size(); i++) {
            if (!leqDomains(domains.get(i), otherDomain.domains.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReducedProductDomain<?> that = (ReducedProductDomain<?>) o;
        return Objects.equals(domains, that.domains);
    }

    @Override
    public int hashCode() {
        return Objects.hash(domains);
    }

    @Override
    public void setToBot() {
        if (!domains.isEmpty()) {
            domains.get(0).setToBot();
        }
    }

    @Override
    public void setToTop() {
        domains.forEach(AbstractDomain::setToTop);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void joinWith(Domain other) {
        ReducedProductDomain<?> otherDomain = other;
        for (int i = 0; i < domains.size(); i++) {
            joinDomains(domains.get(i), otherDomain.domains.get(i));
        }
        applyReduction();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void widenWith(Domain other) {
        ReducedProductDomain<?> otherDomain = other;
        for (int i = 0; i < domains.size(); i++) {
            widenDomains(domains.get(i), otherDomain.domains.get(i));
        }
        applyReduction();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void meetWith(Domain other) {
        ReducedProductDomain<?> otherDomain = other;
        for (int i = 0; i < domains.size(); i++) {
            meetDomains(domains.get(i), otherDomain.domains.get(i));
        }
        applyReduction();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ReducedProductDomain{");
        for (int i = 0; i < domains.size(); i++) {
            builder.append("\n  ").append(i).append(": ").append(domains.get(i));
        }
        builder.append("\n}");
        return builder.toString();
    }

    @Override
    public Domain copyOf() {
        /* Should be implemented in the subclasses */
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T extends AbstractDomain<T>> void joinDomains(AbstractDomain<?> thisDomain, AbstractDomain<?> otherDomain) {
        ((T) thisDomain).joinWith((T) otherDomain);
    }

    @SuppressWarnings("unchecked")
    private <T extends AbstractDomain<T>> void widenDomains(AbstractDomain<?> thisDomain, AbstractDomain<?> otherDomain) {
        ((T) thisDomain).widenWith((T) otherDomain);
    }

    @SuppressWarnings("unchecked")
    private <T extends AbstractDomain<T>> void meetDomains(AbstractDomain<?> thisDomain, AbstractDomain<?> otherDomain) {
        ((T) thisDomain).meetWith((T) otherDomain);
    }

    @SuppressWarnings("unchecked")
    private <T extends AbstractDomain<T>> boolean leqDomains(AbstractDomain<?> thisDomain, AbstractDomain<?> otherDomain) {
        return ((T) thisDomain).leq((T) otherDomain);
    }

    /**
     * Builder for creating reduced product domains.
     *
     * @param <D> the type of the reduced product domain
     */
    public static class Builder<D extends ReducedProductDomain<D>> {
        private final List<AbstractDomain<?>> domains = new ArrayList<>();
        private final List<Reducer> reducers = new ArrayList<>();
        private final Factory<D> factory;

        /**
         * Factory interface for creating instances of the reduced product domain.
         *
         * @param <D> the type of the reduced product domain
         */
        @FunctionalInterface
        public interface Factory<D extends ReducedProductDomain<D>> {
            D create(List<AbstractDomain<?>> domains, List<Reducer> reducers);
        }

        /**
         * Creates a new builder with the given factory.
         *
         * @param factory the factory for creating the reduced product domain
         */
        public Builder(Factory<D> factory) {
            this.factory = factory;
        }

        /**
         * Adds domains to the reduced product.
         *
         * @param newDomains the domains to add
         * @return this builder
         */
        public Builder<D> withDomains(AbstractDomain<?>... newDomains) {
            domains.addAll(Arrays.asList(newDomains));
            return this;
        }

        /**
         * Adds a reducer between domains.
         *
         * @param reducer the reducer to add
         * @return this builder
         */
        public Builder<D> withReducer(Reducer reducer) {
            reducers.add(reducer);
            return this;
        }

        /**
         * Adds a reducer that operates on specific domains.
         *
         * @param indices the indices of the domains to reduce
         * @param reducer the reducer operation
         * @return this builder
         */
        public final Builder<D> withReducer(int[] indices, Consumer<AbstractDomain<?>[]> reducer) {
            reducers.add(domains -> {
                AbstractDomain<?>[] selectedDomains = new AbstractDomain<?>[indices.length];
                for (int i = 0; i < indices.length; i++) {
                    selectedDomains[i] = domains.get(indices[i]);
                }
                reducer.accept(selectedDomains);
            });
            return this;
        }

        /**
         * Builds the reduced product domain.
         *
         * @return the reduced product domain
         */
        public D build() {
            return factory.create(domains, reducers);
        }
    }
}
