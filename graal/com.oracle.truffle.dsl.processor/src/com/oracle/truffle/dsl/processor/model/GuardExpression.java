package com.oracle.truffle.dsl.processor.model;

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
