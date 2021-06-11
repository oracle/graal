package com.oracle.truffle.api.nodes;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ReplaceObserver;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Interface for Truffle bytecode nodes which can be on-stack replaced.
 *
 * @since 21.2 TODO update
 */
public abstract class OnStackReplaceableNode extends ExecutableNode implements ReplaceObserver {
    private Object osrState;

    protected OnStackReplaceableNode(TruffleLanguage<?> language) {
        super(language);
    }

    abstract public Object doOSR(VirtualFrame innerFrame, Frame parentFrame, int target);

    /**
     * Reports a back edge to the target location. This information could be used to trigger
     * on-stack replacement (OSR)
     *
     * @param parentFrame frame at current point of execution
     * @param target target location of the jump (e.g., bytecode index).
     * @return result if OSR was performed, or {@code null} otherwise.
     */
    public final Object reportOSRBackEdge(VirtualFrame parentFrame, int target) {
        // Report loop count for the standard compilation path.
        LoopNode.reportLoopCount(this, 1);
        if (!CompilerDirectives.inInterpreter()) {
            return null;
        }
        return NodeAccessor.RUNTIME.onOSRBackEdge(this, parentFrame, target, getLanguage());
    }

    @Override
    public final boolean nodeReplaced(Node oldNode, Node newNode, CharSequence reason) {
        NodeAccessor.RUNTIME.onOSRNodeReplaced(this, oldNode, newNode, reason);
        return false;
    }

    public final Object getOSRState() {
        return osrState;
    }

    public final void setOSRState(Object osrState) {
        this.osrState = osrState;
    }
}
