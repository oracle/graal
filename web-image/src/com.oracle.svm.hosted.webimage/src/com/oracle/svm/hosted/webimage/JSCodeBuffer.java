/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage;

import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.ENTIRE_IMAGE_SIZE;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.file.Path;
import java.util.List;

import com.oracle.svm.hosted.webimage.codegen.WebImageJSNodeLowerer;
import com.oracle.svm.hosted.webimage.codegen.WebImageProviders;
import com.oracle.svm.hosted.webimage.js.JSKeyword;
import com.oracle.svm.hosted.webimage.logging.LoggerContext;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.webimage.hightiercodegen.CodeBuffer;

import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public class JSCodeBuffer extends CodeBuffer {

    private static final byte SOURCE_MAP_EXPECTED_LINE_SEPARATOR = '\n';

    private final SourceMapBuilder sourceMapBuilder;

    /**
     * Column of the source map.
     */
    private int column;

    public JSCodeBuffer(OptionValues options) {
        super();
        this.sourceMapBuilder = WebImageOptions.GenerateSourceMap.getValue(options)
                        ? new SourceMapBuilder(WebImageOptions.SourceMapSourceRoot.getValue(options))
                        : null;
        this.column = 0;
    }

    public void emitSourceMap(Path f) {
        try (PrintWriter ps = new PrintWriter(f.toFile(), "UTF-8")) {
            sourceMapBuilder.printTo(ps);
        } catch (Exception e) {
            throw JVMCIError.shouldNotReachHere(e);
        }
    }

    private void updateSourceMapPosition(byte[] b) {
        if (sourceMapBuilder == null) {
            return;
        }
        for (byte bi : b) {
            if (bi == SOURCE_MAP_EXPECTED_LINE_SEPARATOR) {
                column = 0;
                sourceMapBuilder.endLine();
            } else {
                column++;
            }
        }
    }

    @Override
    protected void emitMultLineCommentStart() {
        emitKeyword(JSKeyword.MultiLineCommentStart);
    }

    @Override
    protected void emitMultLineCommentEnd() {
        emitKeyword(JSKeyword.MultiLineCommentEnd);
    }

    @Override
    public void emitCode(byte[] b) {
        updateSourceMapPosition(b);
        super.emitCode(b);
    }

    @Override
    public void emitBoolLiteral(boolean b) {
        emitText(b ? "1" : "0");
    }

    /**
     * emit js "var" keyword.
     */
    @Override
    public void emitDeclarationPrefix() {
        emitKeyword(JSKeyword.VAR);
        emitWhiteSpace();
    }

    /**
     * emit js "let" keyword.
     */
    public void emitLetDeclarationPrefix() {
        emitKeyword(JSKeyword.LET);
        emitWhiteSpace();
    }

    /**
     * emit js "const" keyword.
     */
    public void emitConstDeclarationPrefix() {
        emitKeyword(JSKeyword.CONST);
        emitWhiteSpace();
    }

    public void emitLetDeclPrefix(String name) {
        emitLetDeclarationPrefix();
        emitDeclarationName(name);
        emitWhiteSpace();
        emitAssignmentSymbol();
        emitWhiteSpace();
    }

    public void emitConstDeclPrefix(String name) {
        emitConstDeclarationPrefix();
        emitDeclarationName(name);
        emitWhiteSpace();
        emitAssignmentSymbol();
        emitWhiteSpace();
    }

    @Override
    public void emitAnonymousClassHeader(String superClass) {
        emitKeyword(JSKeyword.CLASS);
        emitWhiteSpace();

        if (superClass != null && !superClass.isEmpty()) {
            emitKeyword(JSKeyword.EXTENDS);
            emitWhiteSpace();
            emitText(superClass);
            emitWhiteSpace();
        }

        emitScopeBegin();
    }

    @Override
    public void emitEscapedStringLiteral(Reader r) throws IOException {
        // these characters need to be escaped
        final int newlineChar = '\n';
        final int backslashChar = '\\';
        final int quoteChar = '\"';
        // characters in this range (except abovementioned three) may be emitted verbatim
        final int minAsciiNonControl = 0x20;
        final int maxAsciiNonControl = 0x7e;
        // \x escapes can encode up to two hex digits - i.e. values less than or equal to xEscapeMax
        final int xEscapeMax = 0xff;

        byte[] quote = {'"'};
        byte[] newlineEscape = "\\n\" +".getBytes();
        byte[] backslashEscape = "\\\\".getBytes();
        byte[] quoteEscape = "\\\"".getBytes();
        byte[] xEscape = "\\x##".getBytes();
        byte[] uEscape = "\\u####".getBytes();
        byte[] noEscape = new byte[1];

        emitCode(quote);
        for (;;) {
            int c = r.read();
            switch (c) {
                case -1:
                    emitCode(quote);
                    return;
                case newlineChar:
                    emitCode(newlineEscape);
                    emitNewLine();
                    emitCode(quote);
                    break;
                case backslashChar:
                    emitCode(backslashEscape);
                    break;
                case quoteChar:
                    emitCode(quoteEscape);
                    break;
                default:
                    if (c >= minAsciiNonControl && c <= maxAsciiNonControl) {
                        noEscape[0] = (byte) c;
                        emitCode(noEscape);
                    } else if (c <= xEscapeMax) {
                        xEscape[2] = hexDigit(c >> 4);
                        xEscape[3] = hexDigit(c);
                        emitCode(xEscape);
                    } else {
                        uEscape[2] = hexDigit(c >> 12);
                        uEscape[3] = hexDigit(c >> 8);
                        uEscape[4] = hexDigit(c >> 4);
                        uEscape[5] = hexDigit(c);
                        emitCode(uEscape);
                    }
            }
        }
    }

    private static byte hexDigit(int value) {
        return (byte) Character.forDigit(value & 0xf, 16);
    }

    public void infoDump(WebImageProviders providers) {
        long numBytes = LoggerContext.counter(ENTIRE_IMAGE_SIZE).get();
        providers.stdout().println("---------------------------------------------------------------------");
        providers.stdout().println("\t Image Size is: " + bytesToString(numBytes, true) + " (" + numBytes + "B)");
        providers.stdout().println("---------------------------------------------------------------------");
    }

    public static String bytesToString(long bytes, boolean si) {
        int u = si ? 1000 : 1024;
        if (bytes < u) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(u));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(u, exp), pre);
    }

    private void emitParameterDeclaration(String name, @SuppressWarnings("unused") ResolvedJavaType type) {
        /*
         * type not necessary for js, but give it if needed later
         */
        emitText(name);
    }

    public static String getParamName(int i) {
        return "p" + i;
    }

    @Override
    public void emitMethodHeader(ResolvedJavaMethod method, String name, Signature sig, List<ParameterNode> parameters) {
        assert scopeIndent == 1 : "Scope indent must be 1 for method lowering: " + name + ", current = " + scopeIndent;

        emitNewLine();

        if (method != null) {
            if (WebImageOptions.genJSComments(WebImageOptions.CommentVerbosity.VERBOSE)) {
                emitComment(method.toString());
                emitNewLine();
            } else if (WebImageOptions.genJSComments(WebImageOptions.CommentVerbosity.MINIMAL)) {
                emitComment(method.format("%H.%n(%P)%R"));
                emitNewLine();
            }
        }

        if (method != null && method.isStatic()) {
            emitText("static ");
        }

        if (sourceMapBuilder != null && method != null) {
            String file = method.getDeclaringClass().getSourceFileName();
            if (file != null) {
                LineNumberTable lineNumberTable = method.getLineNumberTable();
                int line = lineNumberTable == null ? 0 : lineNumberTable.getLineNumbers()[0] - 1;
                markSourceLocation(file, line, 0);
                markSymbol(method.getName());
            }
        }

        emitText(name);
        emitKeyword(JSKeyword.LPAR);

        /*
         * the signature of the method can contain more parameters than given by the graph, this
         * happens if certain parameters are not needed and therefore omitted, for js code lowering
         * this must not apply as the call site always emits all specified arguments (by the call
         * target node), for dynamic calls the receiver is always the first parameter.
         */
        boolean useReceiver = method == null || !method.isStatic();

        int psize = sig.getParameterCount(useReceiver);
        for (int i = 0; i < psize; i++) {
            String pname = getParamName(i);
            if (sourceMapBuilder != null && method != null) {
                if (!method.hasBytecodes() || method.getParameters() == null) {
                    if (method.hasBytecodes()) {
                        LogUtils.warning("The method " + method.format("%H.%n(%R)") + " is not compiled with -parameters. No parameter mapping in source map.");
                    }
                } else {
                    markSymbol(getOrigParamName(method, i));
                }
            }
            emitParameterDeclaration(pname, null);
            if (i < psize - 1) {
                emitKeyword(JSKeyword.COMMA);
            }
        }

        emitKeyword(JSKeyword.RPAR);
        emitWhiteSpace();
        emitScopeBegin();

    }

    /**
     * To be safe we strip everything that is not a printable ASCII character as well as multiline
     * comment delimiters. Stripped characters are replaced with a dot '.'
     */
    @Override
    protected String stripCommentMultiline(String s) {
        char[] b = s.toCharArray();
        for (int i = 0; i < b.length; i++) {
            char c = b[i];

            if (c != '\n' && (c < 0x20 || c > 0x7E || c == '\\')) {
                b[i] = '.';
            }
        }
        return new String(b).replace(JSKeyword.MultiLineCommentStart.toString(), "..").replace(JSKeyword.MultiLineCommentEnd.toString(), "..");
    }

    @Override
    public void emitIfHeaderLeft() {
        emitKeyword(JSKeyword.IF);
        emitKeyword(JSKeyword.LPAR);
    }

    @Override
    public void emitIfHeaderRight() {
        emitKeyword(JSKeyword.RPAR);
        emitScopeBegin();
    }

    @Override
    public void emitScopeBegin() {
        emitKeyword(JSKeyword.LBRACE);
        scopeBegin();
        emitNewLine();
    }

    @Override
    public void emitScopeEnd() {
        scopeEnd();
        emitKeyword(JSKeyword.RBRACE);
    }

    @Override
    public void emitElse() {
        scopeEnd();
        emitKeyword(JSKeyword.RBRACE);
        emitKeyword(JSKeyword.ELSE);
        emitScopeBegin();
    }

    @Override
    public void emitWhileTrueHeader() {
        emitKeyword(JSKeyword.WHILE);
        emitKeyword(JSKeyword.LPAR);
        emitKeyword(JSKeyword.TRUE);
        emitKeyword(JSKeyword.RPAR);
        emitScopeBegin();
    }

    @Override
    protected void emitBreakSymbol() {
        emitKeyword(JSKeyword.BREAK);
    }

    @Override
    public void emitBreakLabel(String labelName) {
        emitKeyword(JSKeyword.BREAK);
        emitWhiteSpace();
        emitText(labelName);
        emitInsEnd();
    }

    @Override
    protected void emitContinueSymbol() {
        emitKeyword(JSKeyword.CONTINUE);
    }

    @Override
    public void emitReturnSymbol() {
        emitKeyword(JSKeyword.RETURN);
    }

    @Override
    public void emitSwitchHeaderLeft() {
        emitKeyword(JSKeyword.SWITCH);
        emitKeyword(JSKeyword.LPAR);
    }

    @Override
    public void emitLabel(String labelName) {
        emitText(labelName);
        emitKeyword(JSKeyword.COLON);
    }

    @Override
    public void emitIntCaseChain(int[] val) {
        for (int j : val) {
            emitKeyword(JSKeyword.CASE);
            emitWhiteSpace();
            emitIntLiteral(j);
            emitKeyword(JSKeyword.COLON);
        }

        emitScopeBegin();
    }

    @Override
    public void emitDefaultCase() {
        emitKeyword(JSKeyword.DEFAULT);
        emitKeyword(JSKeyword.COLON);
        emitScopeBegin();
    }

    @Override
    public void emitAssignmentSymbol() {
        emitKeyword(JSKeyword.Assignment);
    }

    @Override
    protected void emitNewSymbol() {
        emitKeyword(JSKeyword.NEW);
    }

    @Override
    protected void emitTrySymbol() {
        emitKeyword(JSKeyword.TRY);
    }

    @Override
    public void emitCatch(String expName) {
        emitScopeEnd();
        emitKeyword(JSKeyword.CATCH);
        emitKeyword(JSKeyword.LPAR);
        emitWhiteSpace();
        emitText(expName);
        emitWhiteSpace();
        emitKeyword(JSKeyword.RPAR);
        emitWhiteSpace();
        emitScopeBegin();
    }

    @Override
    public void emitCatch(String expName, ResolvedJavaType type) {
        emitCatch(expName);
    }

    @Override
    public void emitInsEnd() {
        emitKeyword(JSKeyword.Semicolon);
        emitNewLine();
    }

    /**
     * precondition: {@code method.getParameters() != null }.
     */
    private static String getOrigParamName(ResolvedJavaMethod method, int index) {
        int i = index;
        if (!method.isStatic()) {
            if (i == 0) {
                return "this?";
            } else {
                i--;
            }
        }
        return method.getParameters()[i].getName() + "?";
    }

    @Override
    public void emitFunctionEnd() {
        super.emitFunctionEnd();
        if (sourceMapBuilder != null) {
            sourceMapBuilder.endMark(column);
        }
    }

    /**
     * Marks the output starting at the current position as mapping to the specified source location
     * by adding an item to the source map file. This method must not be called if source maps are
     * turned off ({@code GenerateSourceMap} option set to false).
     *
     * @param srcFile Input file name.
     * @param srcLine Zero-based line number in {@code srcFile}.
     * @param srcColumn Zero-based column index in {@code srcFile:srcLine}.
     * @throws IllegalStateException if source maps are turned off
     */
    public void markSourceLocation(String srcFile, int srcLine, int srcColumn) {
        checkSourceMapsEnabled();
        sourceMapBuilder.markSourceLocation(column, srcFile, srcLine, srcColumn);
    }

    /**
     * Marks the output starting at the current position as mapping to specified symbol (variable or
     * method) by adding an item to the source map file. Call this method immediately before
     * emitting the symbol. This method must not be called if source maps are turned off
     * ({@code GenerateSourceMap} option set to false).
     *
     * @param srcName The name of the symbol.
     * @throws IllegalStateException if source maps are turned off
     */
    public void markSymbol(String srcName) {
        checkSourceMapsEnabled();
        sourceMapBuilder.markSymbol(column, srcName);
    }

    private void checkSourceMapsEnabled() {
        if (sourceMapBuilder == null) {
            throw new IllegalStateException("Source maps are not enabled");
        }
    }

    /**
     * Creates a JS string literal for the given string.
     *
     * Any character that could be misinterpreted by the JS runtime has to be escaped:
     * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String#escape_notation
     */
    @Override
    public String getStringLiteral(String s) {
        return WebImageJSNodeLowerer.getStringLiteral(s);
    }
}
