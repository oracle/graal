/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
// Checkstyle: stop
//@formatter:off
package com.oracle.truffle.llvm.runtime.debug.debugexpr.parser;

import java.util.LinkedList;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.ArithmeticOperation;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.*;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprNodeFactory.CompareKind;
// Set the name of your grammar here (and at the end of this grammar):

import java.util.List;

public class Parser {
    public static final int _EOF = 0;
    public static final int _ident = 1;
    public static final int _number = 2;
    public static final int _floatnumber = 3;
    public static final int _charConst = 4;
    public static final int _lpar = 5;
    public static final int _asterisc = 6;
    public static final int _signed = 7;
    public static final int _unsigned = 8;
    public static final int _int = 9;
    public static final int _long = 10;
    public static final int _short = 11;
    public static final int _float = 12;
    public static final int _double = 13;
    public static final int _char = 14;
    public static final int _typeof = 15;
    public static final int maxT = 45;

    static final boolean _T = true;
    static final boolean _x = false;
    static final int minErrDist = 2;

    public Token t;    // last recognized token
    public Token la;   // lookahead token
    int errDist = minErrDist;

    public Scanner scanner;
    public Errors errors;

    // only for the editor
    private boolean isContentAssistant = false;
    private boolean errorsDetected = false;
    private boolean updateProposals = false;

    private int stopPosition = 0;
    private int proposalToken = _EOF;
    private List<String> ccSymbols = null;
    private List<String> proposals = null;
    private Token dummy = new Token();

    boolean IsCast() {
        Token peek = scanner.Peek();
        if (la.kind == _lpar) {
            while (peek.kind == _asterisc)
                peek = scanner.Peek();
            int k = peek.kind;
            if (k == _signed || k == _unsigned || k == _int || k == _long || k == _char || k == _short || k == _float || k == _double || k == _typeof)
                return true;
        }
        return false;
    }

    private LLVMExpressionNode astRoot = null;
    private DebugExprNodeFactory NF = null;

    public void setNodeFactory(DebugExprNodeFactory nodeFactory) {
        if (NF == null)
            NF = nodeFactory;
    }

    public int GetErrors() {
        return errors.count;
    }

    public LLVMExpressionNode GetASTRoot() {
        return astRoot;
    }
// If you want your generated compiler case insensitive add the
// keyword IGNORECASE here.

    public Parser(Scanner scanner) {
        this.scanner = scanner;
        errors = new Errors();
    }

    void SynErr(int n) {
        if (errDist >= minErrDist)
            errors.SynErr(la.line, la.col, n);
        errDist = 0;

        // for the editor
        errorsDetected = true;
    }

    public void SemErr(String msg) {
        if (errDist >= minErrDist)
            errors.SemErr(t.line, t.col, msg);
        errDist = 0;

        // for the editor
        errorsDetected = true;
    }

    void Get() {
        if (isContentAssistant && updateProposals) {
            la = la.next;
            if (!errorsDetected) {
                proposals = ccSymbols;

                errorsDetected = true;
            }
        }

        else {
            for (;;) {
                t = la;
                la = scanner.Scan();
                if (la.kind <= maxT) {
                    ++errDist;
                    break;
                }

                la = t;

            }
        }

        // auch aktuellen token mitgeben,
        // if la.charPos >= current Token && la.charPos < stopPosition + la.val.length()
        // Token temp = la.clone();
        // la.kind = proposalToken;

        // only for the Editor
        if (isContentAssistant && !errorsDetected && la.charPos >= stopPosition + la.val.length()) {
            dummy = createDummy();
            dummy.next = la;
            la = dummy;
            updateProposals = true;

        }
        ccSymbols = null;
    }

    void Expect(int n) {
        if (la.kind == n)
            Get();
        else {
            SynErr(n);
        }
    }

    boolean StartOf(int s) {
        return set[s][la.kind];
    }

    void ExpectWeak(int n, int follow) {
        if (la.kind == n)
            Get();
        else {
            SynErr(n);
            while (!StartOf(follow))
                Get();
        }
    }

    boolean WeakSeparator(int n, int syFol, int repFol) {
        int kind = la.kind;
        if (kind == n) {
            Get();
            return true;
        } else if (StartOf(repFol))
            return false;
        else {
            SynErr(n);
            while (!(set[syFol][kind] || set[repFol][kind] || set[0][kind])) {
                Get();
                kind = la.kind;
            }
            return StartOf(syFol);
        }
    }

    void DebugExpr() {
        DebugExpressionPair p = null;
        p = Expr();
        if (errors.count == 0)
            astRoot = p.getNode();
    }

    DebugExpressionPair Expr() {
        DebugExpressionPair p;
        DebugExpressionPair pThen = null, pElse = null;
        p = LogOrExpr();
        if (la.kind == 42) {
            Get();
            pThen = Expr();
            Expect(43);
            pElse = Expr();
            p = NF.createTernaryNode(p, pThen, pElse);
        }
        return p;
    }

    DebugExpressionPair PrimExpr() {
        DebugExpressionPair p;
        p = null;
        if (la.kind == 1) {
            Get();
            p = NF.createVarNode(t.val);
        } else if (la.kind == 2) {
            Get();
            p = NF.createIntegerConstant(Integer.parseInt(t.val));
        } else if (la.kind == 3) {
            Get();
            p = NF.createFloatConstant(Float.parseFloat(t.val));
        } else if (la.kind == 4) {
            Get();
            p = NF.createCharacterConstant(t.val);
        } else if (la.kind == 5) {
            Get();
            p = Expr();
            Expect(16);
        } else
            SynErr(46);
        return p;
    }

    DebugExpressionPair Designator() {
        DebugExpressionPair p;
        DebugExpressionPair idxPair = null;
        List<DebugExpressionPair> l;
        p = PrimExpr();
        while (StartOf(1)) {
            if (la.kind == 17) {
                Get();
                idxPair = Expr();
                Expect(18);
                p = NF.createArrayElement(p, idxPair);
            } else if (la.kind == 5) {
                l = ActPars();
                p = NF.createFunctionCall(p, l);
            } else if (la.kind == 19) {
                Get();
                Expect(1);
                p = NF.createObjectMember(p, t.val);
            } else {
                Get();
                Expect(1);
                p = NF.createObjectPointerMember(p, t.val);
            }
        }
        return p;
    }

    List ActPars() {
        List l;
        DebugExpressionPair p1 = null, p2 = null;
        l = new LinkedList<DebugExpressionPair>();
        Expect(5);
        if (StartOf(2)) {
            p1 = Expr();
            l.add(p1);
            while (la.kind == 21) {
                Get();
                p2 = Expr();
                l.add(p2);
            }
        }
        Expect(16);
        return l;
    }

    DebugExpressionPair UnaryExpr() {
        DebugExpressionPair p;
        p = null;
        char kind = '\0';
        DebugExprType typeP = null;
        if (StartOf(3)) {
            p = Designator();
        } else if (StartOf(4)) {
            kind = UnaryOp();
            p = CastExpr();
            p = NF.createUnaryOpNode(p, kind);
        } else if (la.kind == 22) {
            Get();
            Expect(5);
            typeP = DType();
            Expect(16);
            p = NF.createSizeofNode(typeP);
        } else
            SynErr(47);
        return p;
    }

    char UnaryOp() {
        char kind;
        kind = '\0';
        if (la.kind == 6) {
            Get();
        } else if (la.kind == 23) {
            Get();
        } else if (la.kind == 24) {
            Get();
        } else if (la.kind == 25) {
            Get();
        } else if (la.kind == 26) {
            Get();
        } else
            SynErr(48);
        kind = t.val.charAt(0);
        return kind;
    }

    DebugExpressionPair CastExpr() {
        DebugExpressionPair p;
        DebugExprType typeP = null;
        DebugExprTypeofNode typeNode = null;
        if (IsCast()) {
            Expect(5);
            if (StartOf(5)) {
                typeP = DType();
            } else if (la.kind == 15) {
                Get();
                Expect(5);
                Expect(1);
                typeNode = NF.createTypeofNode(t.val);
                Expect(16);
            } else
                SynErr(49);
            Expect(16);
        }
        p = UnaryExpr();
        if (typeP != null) {
            p = NF.createCastIfNecessary(p, typeP);
        }
        if (typeNode != null) {
            p = NF.createPointerCastNode(p, typeNode);
        }
        return p;
    }

    DebugExprType DType() {
        DebugExprType ty;
        ty = BaseType();
        while (la.kind == 6) {
            Get();
            ty = ty.createPointer();
        }
        while (la.kind == 17) {
            Get();
            if (la.kind == 2) {
                Get();
                ty = ty.createArrayType(Integer.parseInt(t.val));
            } else if (la.kind == 18) {
                ty = ty.createArrayType(-1);
            } else
                SynErr(50);
            Expect(18);
        }
        return ty;
    }

    DebugExpressionPair MultExpr() {
        DebugExpressionPair p;
        DebugExpressionPair p1 = null;
        p = CastExpr();
        while (la.kind == 6 || la.kind == 27 || la.kind == 28) {
            if (la.kind == 6) {
                Get();
                p1 = CastExpr();
                p = NF.createArithmeticOp(ArithmeticOperation.MUL, p, p1);
            } else if (la.kind == 27) {
                Get();
                p1 = CastExpr();
                p = NF.createDivNode(p, p1);
            } else {
                Get();
                p1 = CastExpr();
                p = NF.createRemNode(p, p1);
            }
        }
        return p;
    }

    DebugExpressionPair AddExpr() {
        DebugExpressionPair p;
        DebugExpressionPair p1 = null;
        p = MultExpr();
        while (la.kind == 23 || la.kind == 24) {
            if (la.kind == 23) {
                Get();
                p1 = MultExpr();
                p = NF.createArithmeticOp(ArithmeticOperation.ADD, p, p1);
            } else {
                Get();
                p1 = MultExpr();
                p = NF.createArithmeticOp(ArithmeticOperation.SUB, p, p1);
            }
        }
        return p;
    }

    DebugExpressionPair ShiftExpr() {
        DebugExpressionPair p;
        DebugExpressionPair p1 = null;
        p = AddExpr();
        while (la.kind == 29 || la.kind == 30) {
            if (la.kind == 29) {
                Get();
                p1 = AddExpr();
                p = NF.createShiftLeft(p, p1);
            } else {
                Get();
                p1 = AddExpr();
                p = NF.createShiftRight(p, p1);
            }
        }
        return p;
    }

    DebugExpressionPair RelExpr() {
        DebugExpressionPair p;
        DebugExpressionPair p1 = null;
        p = ShiftExpr();
        while (StartOf(6)) {
            if (la.kind == 31) {
                Get();
                p1 = ShiftExpr();
                p = NF.createCompareNode(p, CompareKind.LT, p1);
            } else if (la.kind == 32) {
                Get();
                p1 = ShiftExpr();
                p = NF.createCompareNode(p, CompareKind.GT, p1);
            } else if (la.kind == 33) {
                Get();
                p1 = ShiftExpr();
                p = NF.createCompareNode(p, CompareKind.LE, p1);
            } else {
                Get();
                p1 = ShiftExpr();
                p = NF.createCompareNode(p, CompareKind.GE, p1);
            }
        }
        return p;
    }

    DebugExpressionPair EqExpr() {
        DebugExpressionPair p;
        DebugExpressionPair p1 = null;
        p = RelExpr();
        while (la.kind == 35 || la.kind == 36) {
            if (la.kind == 35) {
                Get();
                p1 = RelExpr();
                p = NF.createCompareNode(p, CompareKind.EQ, p1);
            } else {
                Get();
                p1 = RelExpr();
                p = NF.createCompareNode(p, CompareKind.NE, p1);
            }
        }
        return p;
    }

    DebugExpressionPair AndExpr() {
        DebugExpressionPair p;
        DebugExpressionPair p1 = null;
        p = EqExpr();
        while (la.kind == 37) {
            Get();
            p1 = EqExpr();
            p = NF.createArithmeticOp(ArithmeticOperation.AND, p, p1);
        }
        return p;
    }

    DebugExpressionPair XorExpr() {
        DebugExpressionPair p;
        DebugExpressionPair p1 = null;
        p = AndExpr();
        while (la.kind == 38) {
            Get();
            p1 = AndExpr();
            p = NF.createArithmeticOp(ArithmeticOperation.XOR, p, p1);
        }
        return p;
    }

    DebugExpressionPair OrExpr() {
        DebugExpressionPair p;
        DebugExpressionPair p1 = null;
        p = XorExpr();
        while (la.kind == 39) {
            Get();
            p1 = XorExpr();
            p = NF.createArithmeticOp(ArithmeticOperation.OR, p, p1);
        }
        return p;
    }

    DebugExpressionPair LogAndExpr() {
        DebugExpressionPair p;
        DebugExpressionPair p1 = null;
        p = OrExpr();
        while (la.kind == 40) {
            Get();
            p1 = OrExpr();
            p = NF.createLogicalAndNode(p, p1);
        }
        return p;
    }

    DebugExpressionPair LogOrExpr() {
        DebugExpressionPair p;
        DebugExpressionPair p1 = null;
        p = LogAndExpr();
        while (la.kind == 41) {
            Get();
            p = LogAndExpr();
            p = NF.createLogicalOrNode(p, p1);
        }
        return p;
    }

    DebugExprType BaseType() {
        DebugExprType ty;
        ty = null;
        boolean signed = false;
        switch (la.kind) {
            case 5: {
                Get();
                ty = DType();
                Expect(16);
                break;
            }
            case 44: {
                Get();
                ty = DebugExprType.getVoidType();
                break;
            }
            case 7:
            case 8: {
                if (la.kind == 7) {
                    Get();
                    signed = true;
                } else {
                    Get();
                    signed = false;
                }
                if (StartOf(7)) {
                    if (la.kind == 14) {
                        Get();
                        ty = DebugExprType.getIntType(8, signed);
                    } else if (la.kind == 11) {
                        Get();
                        ty = DebugExprType.getIntType(16, signed);
                    } else if (la.kind == 9) {
                        Get();
                        ty = DebugExprType.getIntType(32, signed);
                    } else {
                        Get();
                        ty = DebugExprType.getIntType(64, signed);
                    }
                }
                break;
            }
            case 14: {
                Get();
                ty = DebugExprType.getIntType(8, false);
                break;
            }
            case 11: {
                Get();
                ty = DebugExprType.getIntType(16, true);
                break;
            }
            case 9: {
                Get();
                ty = DebugExprType.getIntType(32, true);
                break;
            }
            case 10: {
                Get();
                ty = DebugExprType.getIntType(64, true);
                if (la.kind == 13) {
                    Get();
                    ty = DebugExprType.getFloatType(128);
                }
                break;
            }
            case 12: {
                Get();
                ty = DebugExprType.getFloatType(32);
                break;
            }
            case 13: {
                Get();
                ty = DebugExprType.getFloatType(64);
                break;
            }
            default:
                SynErr(51);
                break;
        }
        return ty;
    }

    public void Parse() {
        la = new Token();
        la.val = "";
        Get();
        DebugExpr();
        Expect(0);

    }

    private static final boolean[][] set = {
                    {_T, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x,
                                    _x, _x},
                    {_x, _x, _x, _x, _x, _T, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _T, _x, _T, _T, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x,
                                    _x, _x},
                    {_x, _T, _T, _T, _T, _T, _T, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _T, _T, _T, _T, _T, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x,
                                    _x, _x},
                    {_x, _T, _T, _T, _T, _T, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x,
                                    _x, _x},
                    {_x, _x, _x, _x, _x, _x, _T, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _T, _T, _T, _T, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x,
                                    _x, _x},
                    {_x, _x, _x, _x, _x, _T, _x, _T, _T, _T, _T, _T, _T, _T, _T, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _T,
                                    _x, _x},
                    {_x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _T, _T, _T, _T, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x,
                                    _x, _x},
                    {_x, _x, _x, _x, _x, _x, _x, _x, _x, _T, _T, _T, _x, _x, _T, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x, _x,
                                    _x, _x}

    };

    // only for the editor
    public Parser(Scanner scanner, int proposalToken, int stopPosition) {
        this(scanner);
        isContentAssistant = true;
        this.proposalToken = proposalToken;
        this.stopPosition = stopPosition;
    }

    public String ParseErrors() {
        java.io.PrintStream oldStream = System.out;

        java.io.OutputStream out = new java.io.ByteArrayOutputStream();
        java.io.PrintStream newStream = new java.io.PrintStream(out);

        errors.errorStream = newStream;

        Parse();

        String errorStream = out.toString();
        errors.errorStream = oldStream;

        return errorStream;

    }

    public List<String> getCodeCompletionProposals() {
        return proposals;
    }

    private Token createDummy() {
        Token token = new Token();

        token.pos = la.pos;
        token.charPos = la.charPos;
        token.line = la.line;
        token.col = la.col;

        token.kind = proposalToken;
        token.val = "";

        return token;
    }
} // end Parser

class Errors {
    public int count = 0;                                    // number of errors detected
    public java.io.PrintStream errorStream = System.out;     // error messages go to this stream
    public String errMsgFormat = "col {1}: {2}"; // 0=line, 1=column, 2=text

    protected void printMsg(int line, int column, String msg) {
        StringBuffer b = new StringBuffer(errMsgFormat);
        int pos = b.indexOf("{1}");
        if (pos >= 0) {
            b.delete(pos, pos + 3);
            b.insert(pos, column);
        }
        pos = b.indexOf("{2}");
        if (pos >= 0)
            b.replace(pos, pos + 3, msg);
        errorStream.println(b.toString());
    }

    public void SynErr(int line, int col, int n) {
        String s;
        switch (n) {
            case 0:
                s = "EOF expected";
                break;
            case 1:
                s = "ident expected";
                break;
            case 2:
                s = "number expected";
                break;
            case 3:
                s = "floatnumber expected";
                break;
            case 4:
                s = "charConst expected";
                break;
            case 5:
                s = "lpar expected";
                break;
            case 6:
                s = "asterisc expected";
                break;
            case 7:
                s = "signed expected";
                break;
            case 8:
                s = "unsigned expected";
                break;
            case 9:
                s = "int expected";
                break;
            case 10:
                s = "long expected";
                break;
            case 11:
                s = "short expected";
                break;
            case 12:
                s = "float expected";
                break;
            case 13:
                s = "double expected";
                break;
            case 14:
                s = "char expected";
                break;
            case 15:
                s = "typeof expected";
                break;
            case 16:
                s = "\")\" expected";
                break;
            case 17:
                s = "\"[\" expected";
                break;
            case 18:
                s = "\"]\" expected";
                break;
            case 19:
                s = "\".\" expected";
                break;
            case 20:
                s = "\"->\" expected";
                break;
            case 21:
                s = "\",\" expected";
                break;
            case 22:
                s = "\"sizeof\" expected";
                break;
            case 23:
                s = "\"+\" expected";
                break;
            case 24:
                s = "\"-\" expected";
                break;
            case 25:
                s = "\"~\" expected";
                break;
            case 26:
                s = "\"!\" expected";
                break;
            case 27:
                s = "\"/\" expected";
                break;
            case 28:
                s = "\"%\" expected";
                break;
            case 29:
                s = "\"<<\" expected";
                break;
            case 30:
                s = "\">>\" expected";
                break;
            case 31:
                s = "\"<\" expected";
                break;
            case 32:
                s = "\">\" expected";
                break;
            case 33:
                s = "\"<=\" expected";
                break;
            case 34:
                s = "\">=\" expected";
                break;
            case 35:
                s = "\"==\" expected";
                break;
            case 36:
                s = "\"!=\" expected";
                break;
            case 37:
                s = "\"&\" expected";
                break;
            case 38:
                s = "\"^\" expected";
                break;
            case 39:
                s = "\"|\" expected";
                break;
            case 40:
                s = "\"&&\" expected";
                break;
            case 41:
                s = "\"||\" expected";
                break;
            case 42:
                s = "\"?\" expected";
                break;
            case 43:
                s = "\":\" expected";
                break;
            case 44:
                s = "\"void\" expected";
                break;
            case 45:
                s = "??? expected";
                break;
            case 46:
                s = "invalid PrimExpr";
                break;
            case 47:
                s = "invalid UnaryExpr";
                break;
            case 48:
                s = "invalid UnaryOp";
                break;
            case 49:
                s = "invalid CastExpr";
                break;
            case 50:
                s = "invalid DType";
                break;
            case 51:
                s = "invalid BaseType";
                break;
            default:
                s = "error " + n;
                break;
        }
        printMsg(line, col, s);
        count++;
    }

    public void SemErr(int line, int col, String s) {
        printMsg(line, col, s);
        count++;
    }

    public void SemErr(String s) {
        errorStream.println(s);
        count++;
    }

    public void Warning(int line, int col, String s) {
        printMsg(line, col, s);
    }

    public void Warning(String s) {
        errorStream.println(s);
    }
} // Errors

class FatalError extends RuntimeException {
    public static final long serialVersionUID = 1L;

    public FatalError(String s) {
        super(s);
    }
}
