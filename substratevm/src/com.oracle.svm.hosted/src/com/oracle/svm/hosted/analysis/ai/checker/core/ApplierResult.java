package com.oracle.svm.hosted.analysis.ai.checker.core;

/**
 * Aggregates per-suite results from running all FactAppliers on a method's graph.
 */
public final class ApplierResult {
    public final int appliedFacts;
    private final int boundsEliminated;
    private final int branchesTrue;
    private final int branchesFalse;
    private final int constantsStamped;
    private final int constantsPropagated;
    private final int invokesReplacedWithConstants;

    private ApplierResult(int appliedFacts,
                          int boundsEliminated,
                          int branchesTrue,
                          int branchesFalse,
                          int constantsStamped,
                          int constantsPropagated,
                          int invokesReplacedWithConstants) {
        this.appliedFacts = appliedFacts;
        this.boundsEliminated = boundsEliminated;
        this.branchesTrue = branchesTrue;
        this.branchesFalse = branchesFalse;
        this.constantsStamped = constantsStamped;
        this.constantsPropagated = constantsPropagated;
        this.invokesReplacedWithConstants = invokesReplacedWithConstants;
    }

    public static ApplierResult empty() {
        return new ApplierResult(0, 0, 0, 0, 0, 0, 0);
    }

    public ApplierResult plus(ApplierResult other) {
        return new ApplierResult(
                this.appliedFacts + other.appliedFacts,
                this.boundsEliminated + other.boundsEliminated,
                this.branchesTrue + other.branchesTrue,
                this.branchesFalse + other.branchesFalse,
                this.constantsStamped + other.constantsStamped,
                this.constantsPropagated + other.constantsPropagated,
                this.invokesReplacedWithConstants + other.invokesReplacedWithConstants
        );
    }

    public int boundsChecksEliminated() {
        return boundsEliminated;
    }

    public int branchesFoldedTrue() {
        return branchesTrue;
    }

    public int branchesFoldedFalse() {
        return branchesFalse;
    }

    public int constantsStamped() {
        return constantsStamped;
    }

    public int constantsPropagated() {
        return constantsPropagated;
    }

    public int invokesReplacedWithConstants() {
        return invokesReplacedWithConstants;
    }

    public static final class Builder {
        private int appliedFacts;
        private int boundsEliminated;
        private int branchesTrue;
        private int branchesFalse;
        private int constantsStamped;
        private int constantsPropagated;
        private int invokesReplacedWithConstants;

        public Builder appliedFacts(int v) {
            this.appliedFacts += v;
            return this;
        }

        public Builder boundsEliminated(int v) {
            this.boundsEliminated += v;
            return this;
        }

        public Builder branchesFoldedTrue(int v) {
            this.branchesTrue += v;
            return this;
        }

        public Builder branchesFoldedFalse(int v) {
            this.branchesFalse += v;
            return this;
        }

        public Builder constantsStamped(int v) {
            this.constantsStamped += v;
            return this;
        }

        public Builder constantsPropagated(int v) {
            this.constantsPropagated += v;
            return this;
        }

        public Builder invokesReplacedWithConstants(int v) {
            this.invokesReplacedWithConstants += v;
            return this;
        }

        public ApplierResult build() {
            return new ApplierResult(appliedFacts, boundsEliminated, branchesTrue, branchesFalse, constantsStamped, constantsPropagated, invokesReplacedWithConstants);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ApplierResult boundsEliminated(int count) {
        return builder()
                .appliedFacts(count)
                .boundsEliminated(count)
                .build();
    }

    public static ApplierResult constantsStamped(int count) {
        return builder()
                .appliedFacts(count)
                .constantsStamped(count)
                .build();
    }

    public static ApplierResult constantsPropagated(int count) {
        return builder()
                .appliedFacts(count)
                .constantsPropagated(count)
                .build();
    }

    public static ApplierResult invokesReplaced(int count) {
        return builder()
                .appliedFacts(count)
                .invokesReplacedWithConstants(count)
                .build();
    }
}
