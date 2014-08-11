package com.oracle.truffle.dsl.processor.typesystem;

import java.util.*;

public final class GuardExpression {

    private GuardData resolvedGuard;

    private final String guardName;
    private final boolean negated;

    public GuardExpression(String expression) {
        if (expression.startsWith("!")) {
            guardName = expression.substring(1, expression.length());
            negated = true;
        } else {
            guardName = expression;
            negated = false;
        }
    }

    public boolean isResolved() {
        return resolvedGuard != null;
    }

    public String getGuardName() {
        return guardName;
    }

    public void setGuard(GuardData guard) {
        this.resolvedGuard = guard;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GuardExpression) {
            GuardExpression other = (GuardExpression) obj;
            if (isResolved() && other.isResolved()) {
                return resolvedGuard.equals(other.resolvedGuard) && negated == other.negated;
            } else {
                return guardName.equals(other.guardName) && negated == other.negated;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(guardName, negated, resolvedGuard);
    }

    public int compareConcreteness(GuardExpression other) {
        if (other == null) {
            return -1;
        } else if (this == other) {
            return 0;
        }

        /*
         * Positive and negated guard are always disjunct. So we can choose the positive to be
         * first.
         */
        if (getGuardName().equals(other.getGuardName())) {
            if (negated == !other.negated) {
                if (negated) {
                    return 1;
                } else {
                    return -1;
                }
            }
        }

        /*
         * Very simple version of the implies annotation implementation.
         */
        if (isResolved() && other.isResolved()) {
            if (impliesNot(other)) {
                return 1;
            } else if (other.impliesNot(this)) {
                return -1;
            }
        }
        return 0;
    }

    public final boolean implies(GuardExpression other) {
        if (other == this) {
            return true;
        }
        if (getGuardName().equals(other.getGuardName())) {
            if (isNegated() == other.isNegated()) {
                return true;
            }
        }

        if (isResolved() && other.isResolved()) {
            for (GuardExpression implies : getResolvedGuard().getImpliesExpressions()) {
                if (implies.getGuardName().equals(other.getGuardName())) {
                    if (implies.isNegated() == other.isNegated()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public final boolean impliesNot(GuardExpression other) {
        if (other == this) {
            return false;
        }
        if (getGuardName().equals(other.getGuardName())) {
            if (isNegated() != other.isNegated()) {
                return true;
            }
        }

        if (isResolved() && other.isResolved()) {
            for (GuardExpression implies : getResolvedGuard().getImpliesExpressions()) {
                if (implies.getGuardName().equals(other.getGuardName())) {
                    if (implies.isNegated() != other.isNegated()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return (negated ? "!" : "") + guardName;
    }

    public boolean isNegated() {
        return negated;
    }

    public GuardData getResolvedGuard() {
        return resolvedGuard;
    }

}
