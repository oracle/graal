package com.oracle.truffle.st;

import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.source.SourceSection;

/**
 * A factory for nodes that track coverage
 *
 * Because we {@link SimpleCoverageInstrument#enable(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env)
 * attached} an instance of this factory, each time a AST node of interest is created, it is
 * instrumented with a node created by this factory.
 */
class CoverageEventFactory implements ExecutionEventNodeFactory {

    private SimpleCoverageInstrument simpleCoverageInstrument;

    public CoverageEventFactory(SimpleCoverageInstrument simpleCoverageInstrument) {
        this.simpleCoverageInstrument = simpleCoverageInstrument;
    }

    /**
     * @param ec context of the event, used in our case to lookup the {@link SourceSection} that
     *            our node is instrumenting.
     * @return An {@link ExecutionEventNode}
     */
    public ExecutionEventNode create(final EventContext ec) {
        return new CoverageNode(simpleCoverageInstrument, ec.getInstrumentedSourceSection());
    }
}
