package com.oracle.truffle.espresso;

import java.util.Scanner;
import java.io.PrintWriter;

public class Sudoku {
    final Scanner in = new Scanner(System.in);
    final PrintWriter out = new PrintWriter(System.out);

    final int BASE = 3;
    final int DIMENSION = BASE * BASE;

    int solutionCount;
    int[][] board = new int[DIMENSION][DIMENSION];
    boolean[][] row = new boolean[DIMENSION][DIMENSION];
    boolean[][] col = new boolean[DIMENSION][DIMENSION];
    boolean[][][] box = new boolean [BASE][BASE][DIMENSION];

    void set(int r, int c, int d, boolean flag) {
        row[r][d] = flag;
        col[c][d] = flag;
        box[r/BASE][c/BASE][d] = flag;
        board[r][c] = flag ? d : -1;
    }

    void printBoard() {
        for (int i = 0; i < DIMENSION; ++i) {
            for (int j = 0; j < DIMENSION; j++) {
                out.print(board[i][j]);
            }
            out.println();
        }
        out.println();
    }

    void solve(final int r, final int c) {
        if (r >= DIMENSION) {
            ++solutionCount;
            printBoard();
            return ;
        }
        if (c >= DIMENSION) {
            solve(r + 1, 0);
            return ;
        }
        if (board[r][c] != -1) {
            solve(r, c + 1);
            return;
        }
        for (int d = 0; d < DIMENSION; ++d) {
            if (!row[r][d] && !col[c][d] && !box[r/BASE][c/BASE][d]) {
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
        solve(0, 0);
        out.println(solutionCount + " solution(s) found.");
    }

    public static void main() {
        new Sudoku().run();
    }

    public static void main(String[] args) {
        main();
    }
}

