package com.oracle.truffle.llvm.initialization;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LibraryLocator;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;

public abstract class StaticInitsNode extends LLVMStatementNode {

    @Children final LLVMStatementNode[] statements;
    final Object moduleName;
    final String prefix;

    public StaticInitsNode(LLVMStatementNode[] statements, String prefix, Object moduleName) {
        this.statements = statements;
        this.prefix = prefix;
        this.moduleName = moduleName;
    }

    @ExplodeLoop
    @Specialization
    public void doInit(VirtualFrame frame,
                    @CachedContext(LLVMLanguage.class) LLVMContext ctx) {
        if (ctx.loaderTraceStream() != null) {
            traceExecution(ctx);
        }
        for (LLVMStatementNode stmt : statements) {
            stmt.execute(frame);
        }
    }

    @CompilerDirectives.TruffleBoundary
    private void traceExecution(LLVMContext ctx) {
        LibraryLocator.traceStaticInits(ctx, prefix, moduleName, String.format("[%d inst]", statements.length));
    }
}
