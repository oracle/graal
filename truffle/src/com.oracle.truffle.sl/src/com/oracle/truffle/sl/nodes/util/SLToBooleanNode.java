package com.oracle.truffle.sl.nodes.util;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.sl.SLException;
import com.oracle.truffle.sl.nodes.SLExpressionNode;

@NodeChild
@NodeInfo(shortName = "toBoolean")
public abstract class SLToBooleanNode extends SLExpressionNode {
    @Override
    public abstract boolean executeBoolean(VirtualFrame vrame);

    @Specialization
    public static boolean doBoolean(boolean value) {
        return value;
    }

    @Fallback
    public static boolean doFallback(Object value, @Bind("this") Node node, @Bind("$bci") int bci) {
        throw SLException.typeError(node, bci, value);
    }
}
