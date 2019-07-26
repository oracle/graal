package com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes;

import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprType;

public interface MemberAccessible {
    public DebugExprType getType();

    public Object getMember();
}
