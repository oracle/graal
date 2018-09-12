/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Brainfck {
    private Scanner sc = new Scanner(System.in);
    private final int LENGTH = 1 << 16;
    private int[] mem = new int[LENGTH];
    private int pc;

    public void interpret(String code) {
        int l = 0;
        for (int i = 0; i < code.length(); i++) {
            switch (code.charAt(i)) {
                case '>':
                    pc = (pc == LENGTH - 1) ? 0 : pc + 1;
                    break;
                case '<':
                    pc = (pc == 0) ? LENGTH - 1 : pc - 1;
                    break;
                case '+':
                    mem[pc]++;
                    break;
                case '-':
                    mem[pc]--;
                    break;
                case '.':
                    System.out.print((char) mem[pc]);
                    break;
                case ',':
                    mem[pc] = (byte) sc.next().charAt(0);
                    break;
                case '[':
                    if (mem[pc] == 0) {
                        i++;
                        while (l > 0 || code.charAt(i) != ']') {
                            if (code.charAt(i) == '[')
                                l++;
                            if (code.charAt(i) == ']')
                                l--;
                            i++;
                        }
                    }
                    break;
                case ']':
                    if (mem[pc] != 0) {
                        i--;
                        while (l > 0 || code.charAt(i) != '[') {
                            if (code.charAt(i) == ']')
                                l++;
                            if (code.charAt(i) == '[')
                                l--;
                            i--;
                        }
                        i--;
                    }
                    break;
            }
        }
    }

    private static String readFile(String path) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        try (FileInputStream fis = new FileInputStream(path)) {
            int bytesRead = 0;
            do {
                bytesRead = fis.read(buf);
                if (bytesRead > 0) {
                    bytes.write(buf, 0, bytesRead);
                }
            } while (bytesRead == buf.length);
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws IOException {
        String program = readFile(args[0]);
        new Brainfck().interpret(program);
    }
}