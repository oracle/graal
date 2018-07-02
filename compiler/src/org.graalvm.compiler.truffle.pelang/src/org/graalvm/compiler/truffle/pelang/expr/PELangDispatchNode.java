package org.graalvm.compiler.truffle.pelang.expr;

import org.graalvm.compiler.truffle.pelang.PELangFunction;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;

public abstract class PELangDispatchNode extends Node {

    public static final int INLINE_CACHE_SIZE = 2;

    public abstract Object executeDispatch(PELangFunction function, Object[] arguments);

    @Specialization(limit = "INLINE_CACHE_SIZE", //
                    guards = "function.getCallTarget() == cachedTarget")
    @SuppressWarnings("unused")
    protected static Object doDirect(PELangFunction function, Object[] arguments,
                    @Cached("function.getCallTarget()") RootCallTarget cachedTarget,
                    @Cached("create(cachedTarget)") DirectCallNode callNode) {
        return callNode.call(arguments);
    }

    @Specialization(replaces = "doDirect")
    protected static Object doIndirect(PELangFunction function, Object[] arguments,
                    @Cached("create()") IndirectCallNode callNode) {
        return callNode.call(function.getCallTarget(), arguments);
    }

    public static PELangDispatchNode create() {
        return PELangDispatchNodeGen.create();
    }

}
