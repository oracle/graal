package org.graalvm.compiler.truffle.pelang;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public abstract class PELangStatementNode extends Node {

    public abstract void executeVoid(VirtualFrame frame);

}
