package com.oracle.svm.hosted.analysis.ai.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * A reduced product domain that combines multiple domains and allows information to flow between them
 * through reduction operations.
 * More detailed description of the reduced product domain can be found in the paper:
 * <a href="https://www.di.ens.fr/~cousot/COUSOTpapers/publications.www/CousotCousotMauborgne-FoSSaCS11-LNCS6604-proofs.pdf">...</a>
 *
 * @param <Domain> the common type of domains contained in this product
 */
public class ReducedProductDomain<Domain extends AbstractDomain<Domain>>
        extends AbstractDomain<ReducedProductDomain<Domain>> {

    private final List<Domain> domains;
    private final List<BiConsumer<Integer, List<Domain>>> reducers;

    public ReducedProductDomain(List<Domain> domains, List<BiConsumer<Integer, List<Domain>>> reducers) {
        this.domains = new ArrayList<>(domains.size());
        for (Domain domain : domains) {
            this.domains.add(domain.copyOf());
        }
        this.reducers = reducers;
        reduce();
    }

    public ReducedProductDomain(ReducedProductDomain<Domain> other) {
        this.domains = new ArrayList<>(other.domains.size());
        for (Domain domain : other.domains) {
            this.domains.add(domain.copyOf());
        }
        this.reducers = other.reducers;
    }

    public Domain getDomain(int index) {
        return domains.get(index);
    }

    public List<Domain> getDomains() {
        return new ArrayList<>(domains);
    }

    private void reduce() {
        boolean changed;
        do {
            changed = false;
            for (int i = 0; i < domains.size(); i++) {
                Domain before = domains.get(i).copyOf();

                // Apply all reducers for this domain
                for (BiConsumer<Integer, List<Domain>> reducer : reducers) {
                    reducer.accept(i, domains);
                }

                // Check if the domain changed
                if (!domains.get(i).equals(before)) {
                    changed = true;
                }
            }
        } while (changed);
    }

    @Override
    public boolean isBot() {
        for (Domain domain : domains) {
            if (domain.isBot()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isTop() {
        for (Domain domain : domains) {
            if (!domain.isTop()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean leq(ReducedProductDomain<Domain> other) {
        if (domains.size() != other.domains.size()) {
            throw new IllegalArgumentException("Domains must be of the same size");
        }
        for (int i = 0; i < domains.size(); i++) {
            if (!domains.get(i).leq(other.domains.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ReducedProductDomain<?> other = (ReducedProductDomain<?>) obj;
        if (domains.size() != other.domains.size()) {
            return false;
        }
        for (int i = 0; i < domains.size(); i++) {
            if (!domains.get(i).equals(other.domains.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(domains);
    }

    @Override
    public void setToBot() {
        domains.getFirst().setToBot();
    }

    @Override
    public void setToTop() {
        for (Domain domain : domains) {
            domain.setToTop();
        }
    }

    @Override
    public void joinWith(ReducedProductDomain<Domain> other) {
        if (domains.size() != other.domains.size()) {
            throw new IllegalArgumentException("Domains must be of the same size");
        }
        for (int i = 0; i < domains.size(); i++) {
            domains.get(i).joinWith(other.domains.get(i));
        }
        reduce();
    }

    @Override
    public void widenWith(ReducedProductDomain<Domain> other) {
        if (domains.size() != other.domains.size()) {
            throw new IllegalArgumentException("Domains must be of the same size");
        }
        for (int i = 0; i < domains.size(); i++) {
            domains.get(i).widenWith(other.domains.get(i));
        }
        reduce();
    }

    @Override
    public void meetWith(ReducedProductDomain<Domain> other) {
        if (domains.size() != other.domains.size()) {
            throw new IllegalArgumentException("Domains must be of the same size");
        }
        for (int i = 0; i < domains.size(); i++) {
            domains.get(i).meetWith(other.domains.get(i));
        }
        reduce();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ReducedProduct{");
        for (int i = 0; i < domains.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(domains.get(i));
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public ReducedProductDomain<Domain> copyOf() {
        return new ReducedProductDomain<>(this);
    }

    /**
     * Creates a reduced product domain builder for the specified domain type.
     *
     * @param <D> the type of domains to be combined
     * @return a new builder
     */
    public static <D extends AbstractDomain<D>> Builder<D> builder() {
        return new Builder<>();
    }

    /**
     * Builder for creating reduced product domains.
     *
     * @param <D> the type of domains to be combined
     */
    public static class Builder<D extends AbstractDomain<D>> {
        private final List<D> domains = new ArrayList<>();
        private final List<BiConsumer<Integer, List<D>>> reducers = new ArrayList<>();

        /**
         * Adds a domain to the product.
         *
         * @param domain the domain to add
         * @return this builder
         */
        public Builder<D> addDomain(D domain) {
            domains.add(domain);
            return this;
        }

        /**
         * Adds a reducer function that refines the domain at the specified index.
         *
         * @param domainIndex the index of the domain to refine
         * @param reducer     the reducer function
         * @return this builder
         */
        public Builder<D> addReducer(int domainIndex, BiConsumer<Integer, List<D>> reducer) {
            reducers.add(reducer);
            return this;
        }

        /**
         * Builds the reduced product domain.
         *
         * @return a new reduced product domain
         */
        public ReducedProductDomain<D> build() {
            return new ReducedProductDomain<>(domains, reducers);
        }
    }
}