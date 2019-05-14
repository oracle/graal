package com.oracle.truffle.llvm.runtime.debug.debugexpr.parser;

import java.util.function.BiFunction;

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleLanguage.InlineParsingRequest;
import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprOperandNode;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMDebuggerScopeFactory;

public class DebugExprParser {
    private final Parser parser;
    private final Scanner scanner;
    private final CocoInputStream cis;
    private final InlineParsingRequest request;
    private DebugExprSymbolTable symtab;

    public DebugExprParser(CharSequence cs) {
        cis = new CocoInputStream(cs);
        scanner = new Scanner(cis);
        parser = new Parser(scanner);
        request = null;
        symtab = null;
    }

    public DebugExprParser(InlineParsingRequest request, LLVMContext context, BiFunction<LLVMContext, Object, Object> metaObj) {
        cis = new CocoInputStream(request.getSource().getCharacters());
        scanner = new Scanner(cis);
        parser = new Parser(scanner);
        this.request = request;
        final Iterable<Scope> scopes = LLVMDebuggerScopeFactory.createSourceLevelScope(request.getLocation(), request.getFrame(), context);
        symtab = new DebugExprSymbolTable(scopes, context, metaObj);
        parser.SetSymtab(symtab);
        parser.SetContext(context);
    }

    public Object parse() throws Exception {
        parser.Parse();
        DebugExprOperandNode node = parser.GetOperand();

        if (parser.errors.count > 0) {
            System.out.print("EXC");
            throw new DebugExprException("DebugExpr incorrect\n" + parser.errors.toString());
        } else if (node.value == null) {
            throw new DebugExprException("DebugExpr incorrect\n" + parser.errors.toString());
        } else {
            return node.value;
        }
    }
}
