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

import java.io.PrintWriter;
import java.util.Scanner;

public class Sudoku {
    final Scanner in = new Scanner(System.in);
    final PrintWriter out = new PrintWriter(System.out);

    final int BASE = 3;
    final int DIMENSION = BASE * BASE;

    int solutionCount;
    int[][] board = new int[DIMENSION][DIMENSION];
    boolean[][] row = new boolean[DIMENSION][DIMENSION];
    boolean[][] col = new boolean[DIMENSION][DIMENSION];
    boolean[][][] box = new boolean[BASE][BASE][DIMENSION];

    void set(int r, int c, int d, boolean flag) {
        row[r][d] = flag;
        col[c][d] = flag;
        box[r / BASE][c / BASE][d] = flag;
        board[r][c] = flag ? d : -1;
    }

    void printBoard() {
        for (int i = 0; i < DIMENSION; ++i) {
            for (int j = 0; j < DIMENSION; j++) {
                out.print(board[i][j] + 1);
            }
            out.println();
        }
        out.println();
    }

    void solve(final int r, final int c) {
        if (r >= DIMENSION) {
            ++solutionCount;
            printBoard();
            return;
        }
        if (c >= DIMENSION) {
            solve(r + 1, 0);
            return;
        }
        if (board[r][c] != -1) {
            solve(r, c + 1);
            return;
        }
        for (int d = 0; d < DIMENSION; ++d) {
            if (!row[r][d] && !col[c][d] && !box[r / BASE][c / BASE][d]) {
                set(r, c, d, true);
                solve(r, c + 1);
                set(r, c, d, false);
            }
        }
    }

    void run() {
        for (int i = 0; i < DIMENSION; ++i) {
            for (int j = 0; j < DIMENSION; j++) {
                int d = in.nextInt() - 1; // 0 for empty
                board[i][j] = d;
                if (d != -1) {
                    set(i, j, d, true);
                }
            }
        }
        long ticks = System.currentTimeMillis();
        solve(0, 0);
        out.println(solutionCount + " solution(s) found.");
        out.println("Elapsed: " + (System.currentTimeMillis() - ticks) + " ms");
        out.flush();
    }

    public static void main(String[] args) {
        new Sudoku().run();
    }
}
