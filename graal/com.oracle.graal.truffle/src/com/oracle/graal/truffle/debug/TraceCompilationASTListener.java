package com.oracle.graal.truffle.debug;

import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

import java.io.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.truffle.*;
import com.oracle.graal.truffle.TruffleInlining.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.NodeUtil.*;

public class TraceCompilationASTListener extends AbstractDebugCompilationListener {

    public static void install(GraalTruffleRuntime runtime) {
        if (TraceTruffleCompilationAST.getValue()) {
            runtime.addCompilationListener(new TraceCompilationASTListener());
        }
    }

    @Override
    public void notifyCompilationSuccess(OptimizedCallTarget target, StructuredGraph graph, CompilationResult result) {
        log(0, "opt AST", target.toString(), target.getDebugProperties());
        printCompactTree(new PrintWriter(OUT), target);
    }

    private static void printCompactTree(PrintWriter p, OptimizedCallTarget target) {
        target.accept(new CallTreeNodeVisitor() {

            public boolean visit(List<TruffleInlining> decisionStack, Node node) {
                if (node == null) {
                    return true;
                }
                int level = CallTreeNodeVisitor.getNodeDepth(decisionStack, node);
                for (int i = 0; i < level; i++) {
                    p.print("  ");
                }
                Node parent = node.getParent();

                if (parent == null) {
                    p.println(node.getClass().getSimpleName());
                } else {
                    String fieldName = "unknownField";
                    NodeField[] fields = NodeClass.get(parent.getClass()).getFields();
                    for (NodeField field : fields) {
                        Object value = field.loadValue(parent);
                        if (value == node) {
                            fieldName = field.getName();
                            break;
                        } else if (value instanceof Node[]) {
                            int index = 0;
                            for (Node arrayNode : (Node[]) value) {
                                if (arrayNode == node) {
                                    fieldName = field.getName() + "[" + index + "]";
                                    break;
                                }
                                index++;
                            }
                        }
                    }
                    p.print(fieldName);
                    p.print(" = ");
                    p.println(node.getClass().getSimpleName());
                }
                p.flush();
                return true;
            }

        }, true);
    }

}
