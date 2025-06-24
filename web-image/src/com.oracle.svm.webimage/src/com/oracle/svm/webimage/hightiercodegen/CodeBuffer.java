/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.webimage.hightiercodegen;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Utility class that provides an abstraction layer for emitting code.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public abstract class CodeBuffer {

    /**
     * Current indent level.
     */
    protected int scopeIndent = 0;

    /**
     * Container of the bytes of the generated code.
     */
    protected final ByteArrayOutputStream codeBytes;

    /**
     * Determines if some indentation should be emitted before new code is emitted. Must be set to
     * true when a new line is started.
     */
    private boolean shouldEmitIndent;

    public CodeBuffer() {
        this.codeBytes = new ByteArrayOutputStream();
    }

    /**
     * Generate a comment in the code.
     *
     * @param s the comment to generate
     */
    public void emitComment(String s) {
        emitMultLineCommentStart();
        emitText(stripCommentMultiline(s));
        emitMultLineCommentEnd();
    }

    public void emitInlineComment(String s) {
        emitMultLineCommentStart();
        emitText(stripComment(s));
        emitMultLineCommentEnd();
    }

    protected abstract void emitMultLineCommentStart();

    protected abstract void emitMultLineCommentEnd();

    /**
     * Returns the current size of the file.
     */
    public int codeSize() {
        return codeBytes.size();
    }

    public void setCodeBytes(byte[] code) {
        assert scopeIndent == 0 : scopeIndent;
        assert !shouldEmitIndent;
        codeBytes.reset();
        emitCode(code);
    }

    public String getCode() {
        return codeBytes.toString();
    }

    public void emitImage(Path f) {
        try (BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(f))) {
            bos.write(codeBytes.toByteArray());
            bos.flush();
        } catch (Exception e) {
            throw JVMCIError.shouldNotReachHere(e);
        }
    }

    /**
     * Emit code plain, unchecked.
     */
    public void emitCode(byte[] b) {
        if (shouldEmitIndent) {
            shouldEmitIndent = false;
            emitIndent();
        }
        codeBytes.write(b, 0, b.length);
    }

    /**
     * Emit whitespace for one level of indentation.
     */
    protected void emitSingleIndent() {
        emitText("\t");
    }

    /**
     * Emit whitespace for the current level of indentation.
     */
    private void emitIndent() {
        for (int i = 0; i < scopeIndent; i++) {
            emitSingleIndent();
        }
    }

    public void emitIntLiteral(int i) {
        emitText(CodeGenTool.getEfficientIntLiteral(i));
    }

    public void emitFloatLiteral(float f) {
        emitText(String.valueOf(f));
    }

    public void emitDoubleLiteral(double d) {
        emitText(String.valueOf(d));
    }

    public void emitStringLiteral(String s) {
        emitText(getStringLiteral(s));
    }

    public void emitBoolLiteral(boolean b) {
        emitText(b ? "true" : "false");
    }

    public void emitKeyword(Keyword keyword) {
        emitText(keyword.toString());
    }

    public abstract void emitDeclarationPrefix();

    @SuppressWarnings("unused")
    public void emitDeclarationPrefix(ResolvedJavaType type) {
        emitDeclarationPrefix();
    }

    public abstract void emitAnonymousClassHeader(String superClass);

    /**
     * Emit name of variable declaration.
     *
     * @param name the name of the variable
     */
    public void emitDeclarationName(String name) {
        emitText(name);
    }

    public abstract String getStringLiteral(String s);

    public abstract void emitInsEnd();

    /**
     * Emit text to the resulting code file.
     *
     * @param text the text to emit e.g. "main"
     */
    public void emitText(String text) {
        emitCode(text.getBytes());
    }

    public void emitWhiteSpace() {
        emitText(" ");
    }

    public abstract void emitEscapedStringLiteral(Reader r) throws IOException;

    public void emitFunctionEnd() {
        emitScopeEnd();
        emitNewLine();
    }

    public abstract void emitMethodHeader(ResolvedJavaMethod method, String name, Signature sig, List<ParameterNode> parameters);

    /**
     * Cleans up the given string, so that it can be used in a multiline comment without issues.
     */
    protected abstract String stripCommentMultiline(String s);

    /**
     * Cleans up the given string, so that it can be used in a single-line comment without issues.
     */
    String stripComment(String s) {
        return stripCommentMultiline(s).replace(System.lineSeparator(), "..").replace('\n', '.');
    }

    public abstract void emitIfHeaderLeft();

    public abstract void emitIfHeaderRight();

    protected void scopeBegin() {
        scopeIndent++;
    }

    protected void scopeEnd() {
        scopeIndent--;
        // implicit lowering invariant
        assert scopeIndent >= 0 : "Indenting must not go below 0 ";
    }

    public abstract void emitScopeBegin();

    public abstract void emitScopeEnd();

    public abstract void emitElse();

    public abstract void emitWhileTrueHeader();

    public void emitBreak() {
        emitBreakSymbol();
        emitInsEnd();
    }

    protected abstract void emitBreakSymbol();

    public abstract void emitBreakLabel(String labelName);

    public void emitContinue() {
        emitContinueSymbol();
        emitInsEnd();
    }

    protected abstract void emitContinueSymbol();

    public void emitReturn() {
        emitReturnSymbol();
    }

    public abstract void emitReturnSymbol();

    public abstract void emitSwitchHeaderLeft();

    public void emitSwitchHeaderRight() {
        emitIfHeaderRight();
    }

    public abstract void emitLabel(String labelName);

    public abstract void emitIntCaseChain(int[] val);

    public abstract void emitDefaultCase();

    public void emitCondition(CanonicalCondition cond) {
        emitWhiteSpace();
        emitText(cond.asCondition().operator);
        emitWhiteSpace();
    }

    public void emitResolvedBuiltInVarDeclPostfix(String comment) {
        if (comment != null) {
            emitWhiteSpace();
            emitInlineComment(comment);
        }
        emitInsEnd();
    }

    public void emitDeclPrefix(String name) {
        emitDeclarationPrefix();
        emitDeclarationName(name);
        emitWhiteSpace();
        emitAssignmentSymbol();
        emitWhiteSpace();
    }

    public void emitDeclPrefix(String name, ResolvedJavaType type) {
        emitDeclarationPrefix(type);
        emitDeclarationName(name);
        emitWhiteSpace();
        emitAssignmentSymbol();
        emitWhiteSpace();
    }

    public abstract void emitAssignmentSymbol();

    public void emitNew() {
        emitNewSymbol();
        emitWhiteSpace();
    }

    protected abstract void emitNewSymbol();

    public void emitTry() {
        emitTrySymbol();
        emitScopeBegin();
    }

    protected abstract void emitTrySymbol();

    public abstract void emitCatch(String expName);

    public abstract void emitCatch(String expName, ResolvedJavaType type);

    /**
     * Emit a simple linebreak.
     */
    public void emitNewLine() {
        emitText(System.lineSeparator());
        shouldEmitIndent = true;
    }

    public int getScopeIndent() {
        return scopeIndent;
    }

    public void setScopeIndent(int indent) {
        assert indent >= 0 : "Indenting must not go below 0 ";
        this.scopeIndent = indent;
    }

    @Override
    public String toString() {
        return codeBytes.toString();
    }

}
