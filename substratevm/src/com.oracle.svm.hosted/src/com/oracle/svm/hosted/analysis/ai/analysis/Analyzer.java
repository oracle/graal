package com.oracle.svm.hosted.analysis.ai.analysis;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analysis.methodfilter.AnalysisMethodFilter;
import com.oracle.svm.hosted.analysis.ai.analysis.methodfilter.AnalysisMethodFilterManager;
import com.oracle.svm.hosted.analysis.ai.checker.core.Checker;
import com.oracle.svm.hosted.analysis.ai.checker.core.CheckerManager;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryFactory;

/**
 * Represents an abstract analyzer for performing analyses driven by a specific abstract {@link Domain}.
 * The analyzer uses transfer functions, interpreters, and iterator policies to compute analysis results.
 * It encapsulates the logic for creating analysis payloads and facilitates method-specific analysis.
 * To create an intra-procedural analyzer, it is enough to provide the initial domain and the node interpreter.
 * To create an inter-procedural analyzer, we need to also add an implementation of {@link Summary}, as well as logic
 * for creating the summary from an abstract context, which is handled by {@link SummaryFactory}.
 * We can also add additional parameters, like a list of checkers to be used during the analysis, or method filters,
 * to restrict the analysis of specific methods, extrapolation limits such as join/widen limits, etc.
 *
 * @param <Domain> the type of the abstract domain used for the analysis.
 */
public abstract class Analyzer<Domain extends AbstractDomain<Domain>> {

    protected final Domain initialDomain;
    protected final AbstractInterpreter<Domain> abstractInterpreter;
    protected final IteratorPolicy iteratorPolicy;
    protected final CheckerManager checkerManager;
    protected final AnalysisMethodFilterManager methodFilterManager;

    protected Analyzer(Builder<?, Domain> builder) {
        this.initialDomain = builder.initialDomain;
        this.abstractInterpreter = builder.abstractInterpreter;
        this.iteratorPolicy = builder.iteratorPolicy;
        this.checkerManager = builder.checkerManager;
        this.methodFilterManager = builder.methodFilterManager;
    }

    /**
     * Execute analysis starting from the given analysis method. Concrete analyzers are free to
     * traverse more methods (e.g., via invokes) as part of their strategy.
     */
    public abstract void runAnalysis(AnalysisMethod method);

    public static abstract class Builder<T extends Builder<T, Domain>, Domain extends AbstractDomain<Domain>> {
        protected final Domain initialDomain;
        protected final AbstractInterpreter<Domain> abstractInterpreter;
        protected IteratorPolicy iteratorPolicy = IteratorPolicy.DEFAULT_FORWARD_WTO;
        protected CheckerManager checkerManager = new CheckerManager();
        protected AnalysisMethodFilterManager methodFilterManager = new AnalysisMethodFilterManager();

        protected Builder(Domain initialDomain, AbstractInterpreter<Domain> abstractInterpreter) {
            this.initialDomain = initialDomain;
            this.abstractInterpreter = abstractInterpreter;
        }

        public T iteratorPolicy(IteratorPolicy iteratorPolicy) {
            this.iteratorPolicy = iteratorPolicy;
            return self();
        }

        public T registerChecker(Checker<?> checker) {
            checkerManager.registerChecker(checker);
            return self();
        }

        public T addMethodFilter(AnalysisMethodFilter filter) {
            methodFilterManager.addMethodFilter(filter);
            return self();
        }

        protected abstract T self();
    }
}
