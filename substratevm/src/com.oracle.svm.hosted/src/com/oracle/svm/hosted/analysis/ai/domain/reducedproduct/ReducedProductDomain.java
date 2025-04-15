package com.oracle.svm.hosted.analysis.ai.domain.reducedproduct;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

/**
 * A reduced product domain that combines multiple domains and allows information to flow between them
 * through reduction operations.
 * More detailed description of the reduced product domain can be found in the paper:
 * <a href="https://www.di.ens.fr/~cousot/COUSOTpapers/publications.www/CousotCousotMauborgne-FoSSaCS11-LNCS6604-proofs.pdf">...</a>
 */
public class ReducedProductDomain extends AbstractDomain<ReducedProductDomain> {

    @Override
    public boolean isBot() {
        return false;
    }

    @Override
    public boolean isTop() {
        return false;
    }

    @Override
    public boolean leq(ReducedProductDomain other) {
        return false;
    }

    @Override
    public boolean equals(Object other) {
        return false;
    }

    @Override
    public void setToBot() {

    }

    @Override
    public void setToTop() {

    }

    @Override
    public void joinWith(ReducedProductDomain other) {

    }

    @Override
    public void widenWith(ReducedProductDomain other) {

    }

    @Override
    public void meetWith(ReducedProductDomain other) {

    }

    @Override
    public String toString() {
        return "";
    }

    @Override
    public ReducedProductDomain copyOf() {
        return null;
    }
}
