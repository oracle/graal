/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package at.ssw.visualizer.nc.model;

import at.ssw.visualizer.texteditor.model.Scanner;
import java.util.HashSet;
import java.util.Set;
import org.netbeans.editor.TokenID;

/**
 *
 * @author Alexander Reder
 * @author Christian Wimmer
 */
public class NCScanner extends Scanner {
    private Set<String> registerNames;

    public NCScanner() {
        super("\n\r\t ,;:()$[]", NCTokenContext.contextPath);

        registerNames = new HashSet<String>();
        registerNames.add("eax");
        registerNames.add("ebx");
        registerNames.add("ecx");
        registerNames.add("edx");
        registerNames.add("esi");
        registerNames.add("edi");
        registerNames.add("esp");
        registerNames.add("ebp");
        registerNames.add("rax");
        registerNames.add("rbx");
        registerNames.add("rcx");
        registerNames.add("rdx");
        registerNames.add("rsi");
        registerNames.add("rdi");
        registerNames.add("rsp");
        registerNames.add("rbp");
        registerNames.add("r8");
        registerNames.add("r9");
        registerNames.add("r10");
        registerNames.add("r11");
        registerNames.add("r12");
        registerNames.add("r13");
        registerNames.add("r14");
        registerNames.add("r15");
        registerNames.add("r8d");
        registerNames.add("r9d");
        registerNames.add("r10d");
        registerNames.add("r11d");
        registerNames.add("r12d");
        registerNames.add("r13d");
        registerNames.add("r14d");
        registerNames.add("r15d");
        registerNames.add("xmm0");
        registerNames.add("xmm1");
        registerNames.add("xmm2");
        registerNames.add("xmm3");
        registerNames.add("xmm4");
        registerNames.add("xmm5");
        registerNames.add("xmm6");
        registerNames.add("xmm7");
        registerNames.add("xmm8");
        registerNames.add("xmm9");
        registerNames.add("xmm10");
        registerNames.add("xmm11");
        registerNames.add("xmm12");
        registerNames.add("xmm13");
        registerNames.add("xmm14");
        registerNames.add("xmm15");
    }

    private boolean isBlock() {
        return (expectChar('B') && expectChar(DIGIT) && expectChars(DIGIT) && expectEnd()) ||
               (expectChar('L') && expectChar(DIGIT) && expectChars(DIGIT) && expectEnd());
    }

    private boolean isAddress() {
        return expectChar('0') && expectChar('x') && expectChar(HEX) && expectChars(HEX) && expectEnd();
    }

    private boolean isRegister() {
        return (expectChar('%') && expectChar(LETTER) && expectChars(LETTER_DIGIT) && expectEnd()) ||
                isKeyword(registerNames);
    }

    private boolean isInstruction() {
        boolean result = (beforeChar(':') || beforeChars(DIGIT)) && expectChar(LC_LETTER) && expectChars(LC_LETTER_DIGIT);
        while (result && ch == ' ' && offset + 1 < stopOffset && LC_LETTER.get(buffer[offset + 1])) {
            readNext();
            result = expectChars(LC_LETTER_DIGIT);
        }
        return result && expectEnd();
    }

    private boolean isComment() {
        int curOffset = offset;
        boolean startFound = false;
        while (curOffset >= 0 && buffer[curOffset] != '\n') {
            if (buffer[curOffset] == ';') {
                startFound = true;
                tokenOffset = curOffset;
            }
            curOffset--;
        }
        if (!startFound) {
            return false;
        }
        while (ch != '\n' && ch != EOF) {
            readNext();
        }
        return true;
    }

    @Override
    protected TokenID parseToken() {
        findTokenBegin();
        if (ch == EOF) {
            return NCTokenContext.EOF_TOKEN;
        } else if (isComment()) {
            return NCTokenContext.COMMENT_TOKEN;
        } else if (isWhitespace()) {
            return NCTokenContext.WHITESPACE_TOKEN;
        } else if (isBlock()) {
            return NCTokenContext.BLOCK_TOKEN;
        } else if (isAddress()) {
            return NCTokenContext.ADDRESS_TOKEN;
        } else if (isRegister()) {
            return NCTokenContext.REGISTER_TOKEN;
        } else if (isInstruction()) {
            return NCTokenContext.INSTRUCTION_TOKEN;
        } else {
            readToWhitespace();
            return NCTokenContext.OTHER_TOKEN;
        }
    }

}
