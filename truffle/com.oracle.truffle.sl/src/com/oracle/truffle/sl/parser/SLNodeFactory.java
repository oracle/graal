/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.sl.parser;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.instrument.Instrumenter;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.sl.nodes.SLExpressionNode;
import com.oracle.truffle.sl.nodes.SLRootNode;
import com.oracle.truffle.sl.nodes.SLStatementNode;
import com.oracle.truffle.sl.nodes.access.SLReadPropertyNode;
import com.oracle.truffle.sl.nodes.access.SLReadPropertyNodeGen;
import com.oracle.truffle.sl.nodes.access.SLWritePropertyNode;
import com.oracle.truffle.sl.nodes.access.SLWritePropertyNodeGen;
import com.oracle.truffle.sl.nodes.call.SLInvokeNode;
import com.oracle.truffle.sl.nodes.call.SLInvokeNodeGen;
import com.oracle.truffle.sl.nodes.controlflow.SLBlockNode;
import com.oracle.truffle.sl.nodes.controlflow.SLBreakNode;
import com.oracle.truffle.sl.nodes.controlflow.SLContinueNode;
import com.oracle.truffle.sl.nodes.controlflow.SLFunctionBodyNode;
import com.oracle.truffle.sl.nodes.controlflow.SLIfNode;
import com.oracle.truffle.sl.nodes.controlflow.SLReturnNode;
import com.oracle.truffle.sl.nodes.controlflow.SLWhileNode;
import com.oracle.truffle.sl.nodes.expression.SLAddNodeGen;
import com.oracle.truffle.sl.nodes.expression.SLBigIntegerLiteralNode;
import com.oracle.truffle.sl.nodes.expression.SLDivNodeGen;
import com.oracle.truffle.sl.nodes.expression.SLEqualNodeGen;
import com.oracle.truffle.sl.nodes.expression.SLFunctionLiteralNode;
import com.oracle.truffle.sl.nodes.expression.SLLessOrEqualNodeGen;
import com.oracle.truffle.sl.nodes.expression.SLLessThanNodeGen;
import com.oracle.truffle.sl.nodes.expression.SLLogicalAndNodeGen;
import com.oracle.truffle.sl.nodes.expression.SLLogicalNotNodeGen;
import com.oracle.truffle.sl.nodes.expression.SLLogicalOrNodeGen;
import com.oracle.truffle.sl.nodes.expression.SLLongLiteralNode;
import com.oracle.truffle.sl.nodes.expression.SLMulNodeGen;
import com.oracle.truffle.sl.nodes.expression.SLParenExpressionNode;
import com.oracle.truffle.sl.nodes.expression.SLStringLiteralNode;
import com.oracle.truffle.sl.nodes.expression.SLSubNodeGen;
import com.oracle.truffle.sl.nodes.local.SLReadArgumentNode;
import com.oracle.truffle.sl.nodes.local.SLReadLocalVariableNode;
import com.oracle.truffle.sl.nodes.local.SLReadLocalVariableNodeGen;
import com.oracle.truffle.sl.nodes.local.SLWriteLocalVariableNode;
import com.oracle.truffle.sl.nodes.local.SLWriteLocalVariableNodeGen;
import com.oracle.truffle.sl.runtime.SLContext;

/**
 * Helper class used by the SL {@link Parser} to create nodes. The code is factored out of the
 * automatically generated parser to keep the attributed grammar of SL small.
 */
public class SLNodeFactory {

    /* Tags for instrumentations */
    private static final String[] ROOT_TAGS = {"ROOT"};
    private static final String[] BLOCK_TAGS = {"BLOCK"};
    private static final String[] STATEMENT_TAGS = {"STATEMENT"};
    private static final String[] EXPRESSION_TAGS = {"EXPRESSION"};

    /**
     * Local variable names that are visible in the current block. Variables are not visible outside
     * of their defining block, to prevent the usage of undefined variables. Because of that, we can
     * decide during parsing if a name references a local variable or is a function name.
     */
    static class LexicalScope {
        protected final LexicalScope outer;
        protected final Map<String, FrameSlot> locals;

        LexicalScope(LexicalScope outer) {
            this.outer = outer;
            this.locals = new HashMap<>();
            if (outer != null) {
                locals.putAll(outer.locals);
            }
        }
    }

    /* State while parsing a source unit. */
    private final SLContext context;
    private final Source source;

    /* State while parsing a function. */
    private int functionStartPos;
    private String functionName;
    private int functionBodyStartPos; // includes parameter list
    private int parameterCount;
    private FrameDescriptor frameDescriptor;
    private List<SLStatementNode> methodNodes;

    /* State while parsing a block. */
    private LexicalScope lexicalScope;

    public SLNodeFactory(SLContext context, Source source) {
        this.context = context;
        this.source = source;
    }

    public void startFunction(Token nameToken, int bodyStartPos) {
        assert functionStartPos == 0;
        assert functionName == null;
        assert functionBodyStartPos == 0;
        assert parameterCount == 0;
        assert frameDescriptor == null;
        assert lexicalScope == null;

        functionStartPos = nameToken.charPos;
        functionName = nameToken.val;
        functionBodyStartPos = bodyStartPos;
        frameDescriptor = new FrameDescriptor();
        methodNodes = new ArrayList<>();
        startBlock();
    }

    public void addFormalParameter(Token nameToken) {
        /*
         * Method parameters are assigned to local variables at the beginning of the method. This
         * ensures that accesses to parameters are specialized the same way as local variables are
         * specialized.
         */
        final SourceSection src = srcFromToken(nameToken, EXPRESSION_TAGS);
        final SLReadArgumentNode readArg = new SLReadArgumentNode(src, parameterCount);
        methodNodes.add(createAssignment(nameToken, readArg));
        parameterCount++;
    }

    public void finishFunction(SLStatementNode bodyNode) {
        methodNodes.add(bodyNode);
        final int bodyEndPos = bodyNode.getSourceSection().getCharEndIndex();
        final SourceSection functionSrc = source.createSection(functionName, functionStartPos, bodyEndPos - functionStartPos, ROOT_TAGS);
        final SLStatementNode methodBlock = finishBlock(methodNodes, functionBodyStartPos, bodyEndPos - functionBodyStartPos);
        assert lexicalScope == null : "Wrong scoping of blocks in parser";

        final SLFunctionBodyNode functionBodyNode = new SLFunctionBodyNode(functionSrc, methodBlock);
        final SLRootNode rootNode = new SLRootNode(this.context, frameDescriptor, functionBodyNode, functionSrc, functionName);

        context.getFunctionRegistry().register(functionName, rootNode);

        functionStartPos = 0;
        functionName = null;
        functionBodyStartPos = 0;
        parameterCount = 0;
        frameDescriptor = null;
        lexicalScope = null;
    }

    public void startBlock() {
        lexicalScope = new LexicalScope(lexicalScope);
    }

    public SLStatementNode finishBlock(List<SLStatementNode> bodyNodes, int startPos, int length) {
        lexicalScope = lexicalScope.outer;

        List<SLStatementNode> flattenedNodes = new ArrayList<>(bodyNodes.size());
        flattenBlocks(bodyNodes, flattenedNodes);

        final SourceSection src = source.createSection("block", startPos, length, BLOCK_TAGS);
        return new SLBlockNode(src, flattenedNodes.toArray(new SLStatementNode[flattenedNodes.size()]));
    }

    private void flattenBlocks(Iterable<? extends Node> bodyNodes, List<SLStatementNode> flattenedNodes) {
        for (Node n : bodyNodes) {
            if (n instanceof SLBlockNode) {
                flattenBlocks(n.getChildren(), flattenedNodes);
            } else {
                flattenedNodes.add((SLStatementNode) n);
            }
        }
    }

    /**
     * Returns an {@link SLBreakNode} for the given token.
     *
     * @param breakToken The token containing the break node's info.
     * @return A SLBreakNode for the given token.
     */
    public SLStatementNode createBreak(Token breakToken) {
        final SLBreakNode breakNode = new SLBreakNode(srcFromToken(breakToken, STATEMENT_TAGS));
        return breakNode;
    }

    /**
     * Returns an {@link SLContinueNode} for the given token.
     *
     * @param continueToken The token containing the continue node's info.
     * @return A SLContinueNode built using the given token.
     */
    public SLStatementNode createContinue(Token continueToken) {
        final SLContinueNode continueNode = new SLContinueNode(srcFromToken(continueToken, STATEMENT_TAGS));
        return continueNode;
    }

    /**
     * Returns an {@link SLWhileNode} for the given parameters.
     *
     * @param whileToken The token containing the while node's info
     * @param conditionNode The conditional node for this while loop
     * @param bodyNode The body of the while loop
     * @return A SLWhileNode built using the given parameters.
     */
    public SLStatementNode createWhile(Token whileToken, SLExpressionNode conditionNode, SLStatementNode bodyNode) {
        final int start = whileToken.charPos;
        final int end = bodyNode.getSourceSection().getCharEndIndex();
        final SLWhileNode whileNode = new SLWhileNode(source.createSection(whileToken.val, start, end - start), conditionNode, bodyNode);
        return whileNode;
    }

    /**
     * Returns an {@link SLIfNode} for the given parameters.
     *
     * @param ifToken The token containing the if node's info
     * @param conditionNode The condition node of this if statement
     * @param thenPartNode The then part of the if
     * @param elsePartNode The else part of the if
     * @return An SLIfNode for the given parameters.
     */
    public SLStatementNode createIf(Token ifToken, SLExpressionNode conditionNode, SLStatementNode thenPartNode, SLStatementNode elsePartNode) {
        final int start = ifToken.charPos;
        final int end = elsePartNode == null ? thenPartNode.getSourceSection().getCharEndIndex() : elsePartNode.getSourceSection().getCharEndIndex();
        final SLIfNode ifNode = new SLIfNode(source.createSection(ifToken.val, start, end - start), conditionNode, thenPartNode, elsePartNode);
        return ifNode;
    }

    /**
     * Returns an {@link SLReturnNode} for the given parameters.
     *
     * @param t The token containing the return node's info
     * @param valueNode The value of the return
     * @return An SLReturnNode for the given parameters.
     */
    public SLStatementNode createReturn(Token t, SLExpressionNode valueNode) {
        final int start = t.charPos;
        final int length = valueNode == null ? t.val.length() : valueNode.getSourceSection().getCharEndIndex() - start;
        final SLReturnNode returnNode = new SLReturnNode(source.createSection(t.val, start, length, STATEMENT_TAGS), valueNode);
        return returnNode;
    }

    /**
     * Returns the corresponding subclass of {@link SLExpressionNode} for binary expressions. </br>
     * These nodes are currently not instrumented.
     *
     * @param opToken The operator of the binary expression
     * @param leftNode The left node of the expression
     * @param rightNode The right node of the expression
     * @return A subclass of SLExpressionNode using the given parameters based on the given opToken.
     */
    public SLExpressionNode createBinary(Token opToken, SLExpressionNode leftNode, SLExpressionNode rightNode) {
        int start = leftNode.getSourceSection().getCharIndex();
        int length = rightNode.getSourceSection().getCharEndIndex() - start;
        final SourceSection src = source.createSection(opToken.val, start, length, EXPRESSION_TAGS);
        switch (opToken.val) {
            case "+":
                return SLAddNodeGen.create(src, leftNode, rightNode);
            case "*":
                return SLMulNodeGen.create(src, leftNode, rightNode);
            case "/":
                return SLDivNodeGen.create(src, leftNode, rightNode);
            case "-":
                return SLSubNodeGen.create(src, leftNode, rightNode);
            case "<":
                return SLLessThanNodeGen.create(src, leftNode, rightNode);
            case "<=":
                return SLLessOrEqualNodeGen.create(src, leftNode, rightNode);
            case ">":
                return SLLogicalNotNodeGen.create(src, SLLessOrEqualNodeGen.create(null, leftNode, rightNode));
            case ">=":
                return SLLogicalNotNodeGen.create(src, SLLessThanNodeGen.create(null, leftNode, rightNode));
            case "==":
                return SLEqualNodeGen.create(src, leftNode, rightNode);
            case "!=":
                return SLLogicalNotNodeGen.create(src, SLEqualNodeGen.create(null, leftNode, rightNode));
            case "&&":
                return SLLogicalAndNodeGen.create(src, leftNode, rightNode);
            case "||":
                return SLLogicalOrNodeGen.create(src, leftNode, rightNode);
            default:
                throw new RuntimeException("unexpected operation: " + opToken.val);
        }
    }

    /**
     * Returns an {@link SLInvokeNode} for the given parameters.
     *
     * @param functionNode The function being called
     * @param parameterNodes The parameters of the function call
     * @param finalToken A token used to determine the end of the sourceSelection for this call
     * @return An SLInvokeNode for the given parameters.
     */
    public SLExpressionNode createCall(SLExpressionNode functionNode, List<SLExpressionNode> parameterNodes, Token finalToken) {
        final int startPos = functionNode.getSourceSection().getCharIndex();
        final int endPos = finalToken.charPos + finalToken.val.length();
        final SourceSection src = source.createSection(functionNode.getSourceSection().getIdentifier(), startPos, endPos - startPos, EXPRESSION_TAGS);
        return SLInvokeNodeGen.create(src, parameterNodes.toArray(new SLExpressionNode[parameterNodes.size()]), functionNode);
    }

    /**
     * Returns an {@link SLWriteLocalVariableNode} for the given parameters.
     *
     * @param nameToken The name of the variable being assigned
     * @param valueNode The value to be assigned
     * @return An SLExpressionNode for the given parameters.
     */
    public SLExpressionNode createAssignment(Token nameToken, SLExpressionNode valueNode) {
        FrameSlot frameSlot = frameDescriptor.findOrAddFrameSlot(nameToken.val);
        lexicalScope.locals.put(nameToken.val, frameSlot);
        final int start = nameToken.charPos;
        final int length = valueNode.getSourceSection().getCharEndIndex() - start;
        return SLWriteLocalVariableNodeGen.create(source.createSection("=", start, length, EXPRESSION_TAGS), valueNode, frameSlot);
    }

    /**
     * Returns a {@link SLReadLocalVariableNode} if this read is a local variable or a
     * {@link SLFunctionLiteralNode} if this read is global. In Simple, the only global names are
     * functions. </br> There is currently no instrumentation{@linkplain Instrumenter
     * Instrumentation} for this node.
     *
     * @param nameToken The name of the variable/function being read
     * @return either:
     *         <ul>
     *         <li>A SLReadLocalVariableNode representing the local variable being read.</li>
     *         <li>A SLFunctionLiteralNode representing the function definition</li>
     *         </ul>
     */
    public SLExpressionNode createRead(Token nameToken) {
        final FrameSlot frameSlot = lexicalScope.locals.get(nameToken.val);
        final SourceSection src = srcFromToken(nameToken, EXPRESSION_TAGS);
        if (frameSlot != null) {
            /* Read of a local variable. */
            return SLReadLocalVariableNodeGen.create(src, frameSlot);
        } else {
            /* Read of a global name. In our language, the only global names are functions. */
            return new SLFunctionLiteralNode(src, nameToken.val);
        }
    }

    public SLExpressionNode createStringLiteral(Token literalToken) {
        /* Remove the trailing and ending " */
        String literal = literalToken.val;
        assert literal.length() >= 2 && literal.startsWith("\"") && literal.endsWith("\"");
        final SourceSection src = srcFromToken(literalToken, EXPRESSION_TAGS);
        literal = literal.substring(1, literal.length() - 1);

        return new SLStringLiteralNode(src, literal);
    }

    public SLExpressionNode createNumericLiteral(Token literalToken) {
        final SourceSection src = srcFromToken(literalToken, EXPRESSION_TAGS);
        try {
            /* Try if the literal is small enough to fit into a long value. */
            return new SLLongLiteralNode(src, Long.parseLong(literalToken.val));
        } catch (NumberFormatException ex) {
            /* Overflow of long value, so fall back to BigInteger. */
            return new SLBigIntegerLiteralNode(src, new BigInteger(literalToken.val));
        }
    }

    public SLExpressionNode createParenExpression(SLExpressionNode expressionNode, int start, int length) {
        final SourceSection src = source.createSection("()", start, length);
        return new SLParenExpressionNode(src, expressionNode);
    }

    /**
     * Returns an {@link SLReadPropertyNode} for the given parameters.
     *
     * @param receiverNode The receiver of the property access
     * @param nameToken The name of the property being accessed
     * @return An SLExpressionNode for the given parameters.
     */
    public SLExpressionNode createReadProperty(SLExpressionNode receiverNode, Token nameToken) {
        final int startPos = receiverNode.getSourceSection().getCharIndex();
        final int endPos = nameToken.charPos + nameToken.val.length();
        final SourceSection src = source.createSection(".", startPos, endPos - startPos, EXPRESSION_TAGS);
        return SLReadPropertyNodeGen.create(src, nameToken.val, receiverNode);
    }

    /**
     * Returns an {@link SLWritePropertyNode} for the given parameters.
     *
     * @param receiverNode The receiver object of the property assignment
     * @param nameToken The name of the property being assigned
     * @param valueNode The value to be assigned
     * @return An SLExpressionNode for the given parameters.
     */
    public SLExpressionNode createWriteProperty(SLExpressionNode receiverNode, Token nameToken, SLExpressionNode valueNode) {
        final int start = receiverNode.getSourceSection().getCharIndex();
        final int length = valueNode.getSourceSection().getCharEndIndex() - start;
        SourceSection src = source.createSection("=", start, length, EXPRESSION_TAGS);
        return SLWritePropertyNodeGen.create(src, nameToken.val, receiverNode, valueNode);
    }

    /**
     * Creates source description of a single token.
     */
    private SourceSection srcFromToken(Token token, String[] tags) {
        return source.createSection(token.val, token.charPos, token.val.length(), tags);
    }

}
