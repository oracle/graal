package com.oracle.truffle.api.operation.test.subpackage;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public abstract class NestedNodeOperationProxy extends Node {
    public abstract Object execute(VirtualFrame frame, Object obj);

    public abstract static class NestedNode extends Node {
        public abstract Object execute(VirtualFrame frame, Object obj);

        // Though "obj" is not visible to the OperationRootNode, this should pass without an error
        // because the node is nested. The NodeParser should ignore nested nodes when parsing in
        // Operation mode.
        @Specialization(guards = "obj != null")
        static Object doNonNull(Object obj) {
            return obj;
        }

        @Specialization
        static Object doNull(Object obj) {
            return null;
        }
    }

    @Specialization
    public static Object doObject(VirtualFrame frame, Object obj, @Cached NestedNode nested) {
        return nested.execute(frame, obj);
    }

}
