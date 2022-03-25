package org.graalvm.bisect.core.optimization;

import java.util.Objects;

public abstract class OptimizationImpl implements Optimization {

    public OptimizationImpl(Integer bci) {
        this.bci = bci;
    }

    private final Integer bci;

    @Override
    public Integer getBCI() {
        return bci;
    }

    @Override
    public int hashCode() {
        return getDescription().hashCode() + Integer.hashCode(bci);
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof OptimizationImpl)) {
            return false;
        }
        OptimizationImpl other = (OptimizationImpl) object;
        return Objects.equals(bci, other.bci) && getDescription() == other.getDescription();
    }
}
