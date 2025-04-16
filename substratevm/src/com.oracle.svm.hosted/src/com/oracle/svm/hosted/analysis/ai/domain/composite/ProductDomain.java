package com.oracle.svm.hosted.analysis.ai.domain.composite;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/*
    This abstract domain represents a cartesian product of other abstract domains.
    Future improvement could be implementing reduced product domain.
 */
public final class ProductDomain extends AbstractDomain<ProductDomain> {

    private final List<AbstractDomain<?>> domains;

    public ProductDomain() {
        this.domains = new ArrayList<>();
    }

    public ProductDomain(List<AbstractDomain<?>> domains) {
        this.domains = new ArrayList<>(domains);
    }

    public ProductDomain(ProductDomain other) {
        this.domains = new ArrayList<>();
        for (AbstractDomain<?> domain : other.domains) {
            this.domains.add(domain.copyOf());
        }
    }

    public List<AbstractDomain<?>> getDomains() {
        return domains;
    }

    @Override
    public boolean isBot() {
        return domains.stream().allMatch(AbstractDomain::isBot);
    }

    @Override
    public boolean isTop() {
        return domains.stream().allMatch(AbstractDomain::isTop);
    }

    @Override
    public boolean leq(ProductDomain other) {
        if (domains.size() != other.domains.size()) {
            return false;
        }
        for (int i = 0; i < domains.size(); i++) {
            AbstractDomain<?> thisDomain = domains.get(i);
            AbstractDomain<?> otherDomain = other.domains.get(i);
            if (thisDomain.getClass() != otherDomain.getClass()) {
                return false;
            }

            if (!leqDomains(thisDomain, otherDomain))
                return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ProductDomain that = (ProductDomain) o;
        return Objects.equals(domains, that.domains);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(domains);
    }

    @Override
    public void setToBot() {
        domains.forEach(AbstractDomain::setToBot);
    }

    @Override
    public void setToTop() {
        domains.forEach(AbstractDomain::setToTop);
    }

    @Override
    public void joinWith(ProductDomain other) {
        for (int i = 0; i < domains.size(); i++) {
            AbstractDomain<?> thisDomain = domains.get(i);
            AbstractDomain<?> otherDomain = other.domains.get(i);
            if (thisDomain.getClass() != otherDomain.getClass()) {
                throw new RuntimeException("Cannot join domains of different types");
            }

            joinDomains(thisDomain, otherDomain);
        }
    }

    @Override
    public void widenWith(ProductDomain other) {
        for (int i = 0; i < domains.size(); i++) {
            AbstractDomain<?> thisDomain = domains.get(i);
            AbstractDomain<?> otherDomain = other.domains.get(i);
            if (thisDomain.getClass() != otherDomain.getClass()) {
                throw new RuntimeException("Cannot widen domains of different types");
            }
            widenDomains(thisDomain, otherDomain);
        }
    }

    @Override
    public void meetWith(ProductDomain other) {
        for (int i = 0; i < domains.size(); i++) {
            AbstractDomain<?> thisDomain = domains.get(i);
            AbstractDomain<?> otherDomain = other.domains.get(i);
            if (thisDomain.getClass() != otherDomain.getClass()) {
                throw new RuntimeException("Cannot meet domains of different types");
            }

            meetDomains(thisDomain, otherDomain);
        }
    }

    @Override
    public String toString() {
        return "ProductDomain{" +
                "domains=" + domains +
                '}';
    }

    @Override
    public ProductDomain copyOf() {
        return new ProductDomain(this);
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
}
