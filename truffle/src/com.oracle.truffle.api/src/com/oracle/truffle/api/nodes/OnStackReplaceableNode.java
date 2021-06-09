package com.oracle.truffle.api.nodes;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Interface for Truffle bytecode nodes which can be on-stack replaced.
 *
 * @since 21.2 TODO update
 */
public interface OnStackReplaceableNode extends NodeInterface {
    Object doOSR(Object target, Frame parentFrame, VirtualFrame innerFrame);
}
