package com.oracle.truffle.llvm.runtime.debug.debugexpr.parser;

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleLanguage.InlineParsingRequest;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMDebuggerScopeFactory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public class DebugExprParser {
    private final Parser parser;
    private final Scanner scanner;
    private final CocoInputStream cis;

    public DebugExprParser(CharSequence cs) {
        cis = new CocoInputStream(cs);
        scanner = new Scanner(cis);
        parser = new Parser(scanner);
    }

    public DebugExprParser(InlineParsingRequest request, LLVMContext context) {
        cis = new CocoInputStream(request.getSource().getCharacters());
        scanner = new Scanner(cis);
        parser = new Parser(scanner);
        final Iterable<Scope> scopes = LLVMDebuggerScopeFactory.createSourceLevelScope(request.getLocation(), request.getFrame(), context);
        parser.SetScopes(scopes);
        parser.SetContext(context);
    }

    public LLVMExpressionNode parse() throws Exception {
        parser.Parse();
        if (parser.errors.count > 0) {
            throw new DebugExprException("DebugExpr incorrect\n" + parser.errors.toString());
        } else {
            return parser.GetASTRoot();
        }
    }
}
