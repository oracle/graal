package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.espresso.impl.Method;

/**
 * All methods in this class that can be overridden in subclasses must be abstract. If a generic
 * implementation should be provided it should be in {@link EspressoInstrumentableRootNodeImpl}.
 */
@GenerateWrapper
public abstract class EspressoInstrumentableRootNode extends EspressoInstrumentableNode {

    abstract Object execute(VirtualFrame frame);

    abstract Method.MethodVersion getMethodVersion();

    // the wrapper must delegate this

    @Override
    public abstract SourceSection getSourceSection();

    abstract boolean canSplit();

    abstract EspressoInstrumentableRootNode split();

    abstract boolean isTrivial();

    @Override
    public WrapperNode createWrapper(ProbeNode probeNode) {
        return new EspressoInstrumentableRootNodeWrapper(this, probeNode);
    }
}
