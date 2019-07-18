package com.oracle.truffle.st;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Node that "wraps" AST nodes of interest (Nodes that correspond to expressions in our case as
 * defined by the filter given to the {@link Instrumenter} in
 * {@link SimpleCoverageInstrument#onCreate(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env) }),
 * and informs the {@link SimpleCoverageInstrument} that we
 * {@link SimpleCoverageInstrument#addCovered(SourceSection) covered} it's
 * {@link #instrumentedSourceSection source section}.
 */
class CoverageNode extends ExecutionEventNode {

    private SimpleCoverageInstrument simpleCoverageInstrument;
    @CompilerDirectives.CompilationFinal private boolean covered;

    /**
     * Each node knows which {@link SourceSection} it instruments.
     */
    private final SourceSection instrumentedSourceSection;

    CoverageNode(SimpleCoverageInstrument simpleCoverageInstrument, SourceSection instrumentedSourceSection) {
        this.simpleCoverageInstrument = simpleCoverageInstrument;
        this.instrumentedSourceSection = instrumentedSourceSection;
    }

    /**
     * The {@link ExecutionEventNode} class let's us define several events that we can intercept.
     * The one of interest to us is
     * {@link ExecutionEventNode#onReturnValue(com.oracle.truffle.api.frame.VirtualFrame, Object) }
     * as we wish to track this nodes {@link #instrumentedSourceSection} in the
     * {@link SimpleCoverageInstrument#coverageMap} only once the node is successfully executed (as
     * oppose to, for example,
     * {@link ExecutionEventNode#onReturnExceptional(com.oracle.truffle.api.frame.VirtualFrame, Throwable) }).
     *
     * Each node keeps a {@link #covered} flag so that the removal only happens once. The fact that
     * the flag is annotated with {@link CompilerDirectives.CompilationFinal} means that this flag
     * will be treated as {@code final} during compilation of instrumented source code (i.e. the
     * {@code false} branch of the if statement can be optimized away).
     *
     * The way it's used in this method is a pattern when writing Truffle nodes:
     * <ul>
     * <li>If we are compiling a covered node, the if condition will evaluate to false and the
     * if-guarded code will be optimized away. This means that once this {@link SourceSection} is
     * confirmed to be covered, there is no further instrumentation overhead on performance.
     * <li>If we are compiling a not-yet-covered node, the if condition will evaluate to true, and
     * the if-guarded code will be included for compilation. The first statement in this block is a
     * {@link CompilerDirectives#transferToInterpreterAndInvalidate() directive to the compiler} to
     * make sure that if this point in the execution is reached, the execution should return to the
     * interpreter and the existing compiled code is no longer valid (since once the covered flag is
     * set to true, the check is unnecessary). The code following the directive is thus always
     * executed in the interpreter: We set the {@link #covered} flag to true, ensuring that the next
     * compilation will have no instrumentation overhead on performance.</li>
     * </ul>
     *
     * @param vFrame unused
     * @param result unused
     */
    @Override
    public void onReturnValue(VirtualFrame vFrame, Object result) {
        if (!covered) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            covered = true;
            final Source source = instrumentedSourceSection.getSource();
            // TODO: This should not be necesery becuase of the filter. Bug!
            if (!source.isInternal()) {
                simpleCoverageInstrument.addCovered(instrumentedSourceSection);
            }
        }
    }

}
