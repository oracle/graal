/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Copyright (c) 2004-2008 Brent Fulgham, 2005-2020 Isaac Gouy
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided with
 * the distribution.
 *
 * 3. Neither the name "The Computer Language Benchmarks Game" nor the name "The Benchmarks Game" nor
 * the name "The Computer Language Shootout Benchmarks" nor the names of its contributors may be used
 * to endorse or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package bench.shootouts;
/*
 * The Computer Language Benchmarks Game
 * http://benchmarksgame.alioth.debian.org/
 * transliterated from C++ (Ben St. John) and D (Michael Deardeuff) by Amir K aka Razii
 */

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

public class Meteor {
    static final int X = 0;
    static final int Y = 1;
    static final int N_DIM = 2;

    static final int EVEN = 0;
    static final int ODD = 1;
    static final int N_PARITY = 2;

    static final int GOOD = 0;
    static final int BAD = 1;
    static final int ALWAYS_BAD = 2;

    static final int OPEN = 0;
    static final int CLOSED = 1;
    static final int N_FIXED = 2;

    static final int MAX_ISLAND_OFFSET = 1024;
    static final int N_COL = 5;
    static final int N_ROW = 10;
    static final int N_CELL = N_COL * N_ROW;
    static final int N_PIECE_TYPE = 10;
    static final int N_ORIENT = 12;
    static final int g_firstRegion[] = {
            0x00, 0x01, 0x02, 0x03, 0x04, 0x01, 0x06, 0x07,
            0x08, 0x01, 0x02, 0x03, 0x0c, 0x01, 0x0e, 0x0f,

            0x10, 0x01, 0x02, 0x03, 0x04, 0x01, 0x06, 0x07,
            0x18, 0x01, 0x02, 0x03, 0x1c, 0x01, 0x1e, 0x1f
    };
    static final int g_flip[] = {
            0x00, 0x10, 0x08, 0x18, 0x04, 0x14, 0x0c, 0x1c,
            0x02, 0x12, 0x0a, 0x1a, 0x06, 0x16, 0x0e, 0x1e,

            0x01, 0x11, 0x09, 0x19, 0x05, 0x15, 0x0d, 0x1d,
            0x03, 0x13, 0x0b, 0x1b, 0x07, 0x17, 0x0f, 0x1f,
    };
    static final int[] s_firstOne = {
            0, 0, 1, 0, 2, 0, 1, 0,
            3, 0, 1, 0, 2, 0, 1, 0,

            4, 0, 1, 0, 2, 0, 1, 0,
            3, 0, 1, 0, 2, 0, 1, 0,
    };
    // -- Globals -------------------------
    static IslandInfo[] g_islandInfo = new IslandInfo[MAX_ISLAND_OFFSET];
    static int g_nIslandInfo = 0;
    static OkPieces[][] g_okPieces = new OkPieces[N_ROW][N_COL];

    static int getMask(int iPos) {
        return (1 << (iPos));
    }

    static int floor(int top, int bot) {
        int toZero = top / bot;
        // negative numbers should be rounded down, not towards zero;
        if ((toZero * bot != top) && ((top < 0) != (bot <= 0)))
            toZero--;

        return toZero;
    }

    static int getFirstOne(int v) {
        int startPos = 0;
        if (v == 0)
            return 0;

        int iPos = startPos;
        int mask = 0xff << startPos;
        while ((mask & v) == 0) {
            mask <<= 8;
            iPos += 8;
        }
        int result = (mask & v) >> iPos;
        int resultLow = result & 0x0f;
        if (resultLow != 0)
            iPos += s_firstOne[resultLow];
        else
            iPos += 4 + s_firstOne[result >> 4];

        return iPos;
    }

    static int countOnes(int v) {
        int n = 0;
        while (v != 0) {
            n++;
            v = v & (v - 1);
        }

        return n;
    }

    static int flipTwoRows(int bits) {
        int flipped = g_flip[bits >> N_COL] << N_COL;
        return (flipped | g_flip[bits & Board.TOP_ROW]);
    }

    static void markBad(IslandInfo info, int mask, int eo, boolean always) {
        info.hasBad[eo][OPEN] |= mask;
        info.hasBad[eo][CLOSED] |= mask;

        if (always)
            info.alwaysBad[eo] |= mask;
    }

    static void initGlobals() {
        for (int i = 0; i < MAX_ISLAND_OFFSET; i++) {
            g_islandInfo[i] = new IslandInfo();
        }

        for (int i = 0; i < N_ROW; i++) {
            for (int j = 0; j < N_COL; j++)
                g_okPieces[i][j] = new OkPieces();
        }
    }

// -- Classes -------------------------;

    @Benchmark
    public static void bench(Blackhole blackhole) {

        initGlobals();
        Board b = new Board();
        Piece.genAllOrientations();
        Board.calcAlwaysBad();
        b.genAllSolutions(0, 0, 0);

        blackhole.consume(b);
    }

    static class OkPieces {
        byte[] nPieces = new byte[N_PIECE_TYPE];
        int[][] pieceVec = new int[N_PIECE_TYPE][N_ORIENT];
    }

    static class IslandInfo {
        int[][] hasBad = new int[N_FIXED][N_PARITY];
        int[][] isKnown = new int[N_FIXED][N_PARITY];
        int[] alwaysBad = new int[N_PARITY];
    }

    static class Soln {
        static final int NO_PIECE = -1;
        SPiece[] m_pieces = new SPiece[N_PIECE_TYPE];
        int m_nPiece;
        byte[][] m_cells = new byte[N_ROW][N_COL];
        boolean m_synched;

        Soln() {
            m_synched = false;
            m_nPiece = 0;
            init();
        }

        Soln(int fillVal) {
            init();
            m_nPiece = 0;
            fill(fillVal);
        }

        boolean isEmpty() {
            return (m_nPiece == 0);
        }

        void popPiece() {
            m_nPiece--;
            m_synched = false;
        }

        void pushPiece(int vec, int iPiece, int row) {
            SPiece p = m_pieces[m_nPiece++];
            p.vec = vec;
            p.iPiece = (short) iPiece;
            p.row = (short) row;
        }

        void init() {
            for (int i = 0; i < N_PIECE_TYPE; i++)
                m_pieces[i] = new SPiece();
        }

        public Soln clone2() {
            Soln s = new Soln();
            for (int i = 0; i < m_pieces.length; i++)
                s.m_pieces[i] = new SPiece(m_pieces[i]);

            s.m_nPiece = m_nPiece;
            // System.arraycopy(m_cells, 0, s.m_cells, 0, N_CELL);
            for (int i = 0; i < N_ROW; i++) {
                for (int j = 0; j < N_COL; j++) {
                    s.m_cells[i][j] = m_cells[i][j];
                }
            }

            s.m_synched = m_synched;
            return s;
        }

        void fill(int val) {
            m_synched = false;
            for (int i = 0; i < N_ROW; i++) {
                for (int j = 0; j < N_COL; j++)
                    m_cells[i][j] = (byte) val;
            }
        }

        @Override
        public String toString() {
            StringBuffer result = new StringBuffer(N_CELL * 2);

            for (int y = 0; y < N_ROW; y++) {
                for (int x = 0; x < N_COL; x++) {
                    int val = m_cells[y][x];
                    // if (val == NO_PIECE) result.append('.');
                    {
                        result.append(val);
                    }
                    result.append(' ');
                }
                result.append('\n');

                // indent every second line
                if (y % 2 == 0)
                    result.append(" ");
            }
            return result.toString();
        }

        void setCells() {
            if (m_synched)
                return;

            for (int iPiece = 0; iPiece < m_nPiece; iPiece++) {
                SPiece p = m_pieces[iPiece];
                int vec = p.vec;
                byte pID = (byte) p.iPiece;
                int rowOffset = p.row;

                int nNewCells = 0;
                for (int y = rowOffset; y < N_ROW; y++) {
                    for (int x = 0; x < N_COL; x++) {
                        if ((vec & 1) != 0) {
                            m_cells[y][x] = pID;
                            nNewCells++;
                        }
                        vec >>= 1;
                    }
                    if (nNewCells == Piece.N_ELEM)
                        break;
                }
            }
            m_synched = true;
        }

        boolean lessThan(Soln r) {
            if (m_pieces[0].iPiece != r.m_pieces[0].iPiece) {
                return m_pieces[0].iPiece < r.m_pieces[0].iPiece;
            }

            setCells();
            r.setCells();

            for (int y = 0; y < N_ROW; y++) {
                for (int x = 0; x < N_COL; x++) {
                    int lval = m_cells[y][x];
                    int rval = r.m_cells[y][x];

                    if (lval != rval)
                        return (lval < rval);
                }
            }

            return false;
        }

        void spin(Soln spun) {
            setCells();

            for (int y = 0; y < N_ROW; y++) {
                for (int x = 0; x < N_COL; x++) {
                    byte flipped = m_cells[N_ROW - y - 1][N_COL - x - 1];
                    spun.m_cells[y][x] = flipped;
                }
            }

            spun.m_pieces[0].iPiece = m_pieces[N_PIECE_TYPE - 1].iPiece;
            spun.m_synched = true;
        }

        class SPiece {
            int vec;
            short iPiece;
            short row;

            SPiece() {
            }

            SPiece(int avec, int apiece, int arow) {
                vec = avec;
                iPiece = (short) apiece;
                row = (short) arow;
            }

            SPiece(SPiece other) {
                vec = other.vec;
                iPiece = other.iPiece;
                row = other.row;
            }
        }
    }

    // -----------------------
    static class Board {
        static final int L_EDGE_MASK =
                ((1 << 0) | (1 << 5) | (1 << 10) | (1 << 15) |
                        (1 << 20) | (1 << 25) | (1 << 30));
        static final int R_EDGE_MASK = L_EDGE_MASK << 4;
        static final int TOP_ROW = (1 << N_COL) - 1;
        static final int ROW_0_MASK =
                TOP_ROW | (TOP_ROW << 10) | (TOP_ROW << 20) | (TOP_ROW << 30);
        static final int ROW_1_MASK = ROW_0_MASK << 5;
        static final int BOARD_MASK = (1 << 30) - 1;
        Soln m_curSoln;
        Soln m_minSoln;
        Soln m_maxSoln;
        int m_nSoln;
        Board() {
            m_curSoln = new Soln(Soln.NO_PIECE);
            m_minSoln = new Soln(N_PIECE_TYPE);
            m_maxSoln = new Soln(Soln.NO_PIECE);
            m_nSoln = (0);
        }

        static int getIndex(int x, int y) {
            return y * N_COL + x;
        }

        static boolean badRegion(int[] toFill, int rNew) {
            // grow empty region, until it doesn't change any more;
            int region;
            do {
                region = rNew;

                // simple grow up/down
                rNew |= (region >> N_COL);
                rNew |= (region << N_COL);

                // grow right/left
                rNew |= (region & ~L_EDGE_MASK) >> 1;
                rNew |= (region & ~R_EDGE_MASK) << 1;

                // tricky growth
                int evenRegion = region & (ROW_0_MASK & ~L_EDGE_MASK);
                rNew |= evenRegion >> (N_COL + 1);
                rNew |= evenRegion << (N_COL - 1);
                int oddRegion = region & (ROW_1_MASK & ~R_EDGE_MASK);
                rNew |= oddRegion >> (N_COL - 1);
                rNew |= oddRegion << (N_COL + 1);

                // clamp against existing pieces
                rNew &= toFill[0];
            } while ((rNew != toFill[0]) && (rNew != region));

            // subtract empty region from board
            toFill[0] ^= rNew;

            int nCells = countOnes(toFill[0]);
            return (nCells % Piece.N_ELEM != 0);
        }

        static int hasBadIslands(int boardVec, int row) {
            // skip over any filled rows
            while ((boardVec & TOP_ROW) == TOP_ROW) {
                boardVec >>= N_COL;
                row++;
            }

            int iInfo = boardVec & ((1 << 2 * N_COL) - 1);
            IslandInfo info = g_islandInfo[iInfo];

            int lastRow = (boardVec >> (2 * N_COL)) & TOP_ROW;
            int mask = getMask(lastRow);
            int isOdd = row & 1;

            if ((info.alwaysBad[isOdd] & mask) != 0)
                return BAD;

            if ((boardVec & (TOP_ROW << N_COL * 3)) != 0)
                return calcBadIslands(boardVec, row);

            int isClosed = (row > 6) ? 1 : 0;

            if ((info.isKnown[isOdd][isClosed] & mask) != 0)
                return (info.hasBad[isOdd][isClosed] & mask);

            if (boardVec == 0)
                return GOOD;

            int hasBad = calcBadIslands(boardVec, row);

            info.isKnown[isOdd][isClosed] |= mask;
            if (hasBad != 0)
                info.hasBad[isOdd][isClosed] |= mask;

            return hasBad;
        }

        static int calcBadIslands(int boardVec, int row) {
            int[] toFill = {~boardVec};
            if ((row & 1) != 0) {
                row--;
                toFill[0] <<= N_COL;
            }

            int boardMask = BOARD_MASK;
            if (row > 4) {
                int boardMaskShift = (row - 4) * N_COL;
                boardMask >>= boardMaskShift;
            }
            toFill[0] &= boardMask;

            // a little pre-work to speed things up
            int bottom = (TOP_ROW << (5 * N_COL));
            boolean filled = ((bottom & toFill[0]) == bottom);
            while ((bottom & toFill[0]) == bottom) {
                toFill[0] ^= bottom;
                bottom >>= N_COL;
            }

            int startRegion;
            if (filled || (row < 4))
                startRegion = bottom & toFill[0];
            else {
                startRegion = g_firstRegion[toFill[0] & TOP_ROW];
                if (startRegion == 0) {
                    startRegion = (toFill[0] >> N_COL) & TOP_ROW;
                    startRegion = g_firstRegion[startRegion];
                    startRegion <<= N_COL;
                }
                startRegion |= (startRegion << N_COL) & toFill[0];
            }

            while (toFill[0] != 0) {
                if (badRegion(toFill, startRegion))
                    return ((toFill[0] != 0) ? ALWAYS_BAD : BAD);
                int iPos = getFirstOne(toFill[0]);
                startRegion = getMask(iPos);
            }

            return GOOD;
        }

        static void calcAlwaysBad() {
            for (int iWord = 1; iWord < MAX_ISLAND_OFFSET; iWord++) {
                IslandInfo isleInfo = g_islandInfo[iWord];
                IslandInfo flipped = g_islandInfo[flipTwoRows(iWord)];

                for (int i = 0, mask = 1; i < 32; i++, mask <<= 1) {
                    int boardVec = (i << (2 * N_COL)) | iWord;
                    if ((isleInfo.isKnown[0][OPEN] & mask) != 0)
                        continue;

                    int hasBad = calcBadIslands(boardVec, 0);
                    if (hasBad != GOOD) {
                        boolean always = (hasBad == ALWAYS_BAD);
                        markBad(isleInfo, mask, EVEN, always);

                        int flipMask = getMask(g_flip[i]);
                        markBad(flipped, flipMask, ODD, always);
                    }
                }
                flipped.isKnown[1][OPEN] = -1;
                isleInfo.isKnown[0][OPEN] = -1;
            }
        }

        static boolean hasBadIslandsSingle(int boardVec, int row) {
            int[] toFill = {~boardVec};
            boolean isOdd = ((row & 1) != 0);
            if (isOdd) {
                row--;
                toFill[0] <<= N_COL; // shift to even aligned
                toFill[0] |= TOP_ROW;
            }

            int startRegion = TOP_ROW;
            int lastRow = TOP_ROW << (5 * N_COL);
            int boardMask = BOARD_MASK; // all but the first two bits
            if (row >= 4)
                boardMask >>= ((row - 4) * N_COL);
            else if (isOdd || (row == 0))
                startRegion = lastRow;

            toFill[0] &= boardMask;
            startRegion &= toFill[0];

            while (toFill[0] != 0) {
                if (badRegion(toFill, startRegion))
                    return true;
                int iPos = getFirstOne(toFill[0]);
                startRegion = getMask(iPos);
            }

            return false;
        }

        void genAllSolutions(int boardVec, int placedPieces, int row) {
            while ((boardVec & TOP_ROW) == TOP_ROW) {
                boardVec >>= N_COL;
                row++;
            }
            int iNextFill = s_firstOne[~boardVec & TOP_ROW];
            OkPieces allowed = g_okPieces[row][iNextFill];

            int iPiece = getFirstOne(~placedPieces);
            int pieceMask = getMask(iPiece);
            for (; iPiece < N_PIECE_TYPE; iPiece++, pieceMask <<= 1) {
                if ((pieceMask & placedPieces) != 0)
                    continue;

                placedPieces |= pieceMask;
                for (int iOrient = 0; iOrient < allowed.nPieces[iPiece]; iOrient++) {
                    int pieceVec = allowed.pieceVec[iPiece][iOrient];

                    if ((pieceVec & boardVec) != 0)
                        continue;

                    boardVec |= pieceVec;

                    if ((hasBadIslands(boardVec, row)) != 0) {
                        boardVec ^= pieceVec;
                        continue;
                    }

                    m_curSoln.pushPiece(pieceVec, iPiece, row);

                    // recur or record solution
                    if (placedPieces != Piece.ALL_PIECE_MASK)
                        genAllSolutions(boardVec, placedPieces, row);
                    else
                        recordSolution(m_curSoln);

                    boardVec ^= pieceVec;
                    m_curSoln.popPiece();
                }

                placedPieces ^= pieceMask;
            }
        }

        void recordSolution(Soln s) {
            m_nSoln += 2;

            if (m_minSoln.isEmpty()) {
                m_minSoln = m_maxSoln = s.clone2();
                return;
            }

            if (s.lessThan(m_minSoln))
                m_minSoln = s.clone2();
            else if (m_maxSoln.lessThan(s))
                m_maxSoln = s.clone2();

            Soln spun = new Soln();
            s.spin(spun);
            if (spun.lessThan(m_minSoln))
                m_minSoln = spun;
            else if (m_maxSoln.lessThan(spun))
                m_maxSoln = spun;
        }
    }

    // -- Main ---------------------------

    // ----------------------
    static class Piece {
        static final int N_ELEM = 5;
        static final int ALL_PIECE_MASK = (1 << N_PIECE_TYPE) - 1;
        static final int SKIP_PIECE = 5;
        static final int BaseVecs[] = {
                0x10f, 0x0cb, 0x1087, 0x427, 0x465,
                0x0c7, 0x8423, 0x0a7, 0x187, 0x08f
        };
        static Piece[][] s_basePiece = new Piece[N_PIECE_TYPE][N_ORIENT];

        static {
            for (int i = 0; i < N_PIECE_TYPE; i++) {
                for (int j = 0; j < N_ORIENT; j++)
                    s_basePiece[i][j] = new Piece();
            }
        }

        Instance[] m_instance = new Instance[N_PARITY];

        Piece() {
            init();
        }

        static void setCoordList(int vec, int[][] pts) {
            int iPt = 0;
            int mask = 1;
            for (int y = 0; y < N_ROW; y++) {
                for (int x = 0; x < N_COL; x++) {
                    if ((mask & vec) != 0) {
                        pts[iPt][X] = x;
                        pts[iPt][Y] = y;

                        iPt++;
                    }
                    mask <<= 1;
                }
            }
        }

        static int toBitVector(int[][] pts) {
            int y, x;
            int result = 0;
            for (int iPt = 0; iPt < N_ELEM; iPt++) {
                x = pts[iPt][X];
                y = pts[iPt][Y];

                int pos = Board.getIndex(x, y);
                result |= (1 << pos);
            }

            return result;
        }

        static void shiftUpLines(int[][] pts, int shift) {

            for (int iPt = 0; iPt < N_ELEM; iPt++) {
                if ((pts[iPt][Y] & shift & 0x1) != 0)
                    (pts[iPt][X])++;
                pts[iPt][Y] -= shift;
            }
        }

        static int shiftToX0(int[][] pts, Instance instance, int offsetRow) {
            int x, y, iPt;
            int xMin = pts[0][X];
            int xMax = xMin;
            for (iPt = 1; iPt < N_ELEM; iPt++) {
                x = pts[iPt][X];
                y = pts[iPt][Y];

                if (x < xMin)
                    xMin = x;
                else if (x > xMax)
                    xMax = x;
            }

            int offset = N_ELEM;
            for (iPt = 0; iPt < N_ELEM; iPt++) {

                pts[iPt][X] -= xMin;

                if ((pts[iPt][Y] == offsetRow) && (pts[iPt][X] < offset))
                    offset = pts[iPt][X];
            }

            instance.m_offset = offset;
            instance.m_vec = toBitVector(pts);
            return xMax - xMin;
        }

        static void genOrientation(int vec, int iOrient, Piece target) {
            int[][] pts = new int[N_ELEM][N_DIM];
            setCoordList(vec, pts);

            int y, x, iPt;
            int rot = iOrient % 6;
            int flip = iOrient >= 6 ? 1 : 0;
            if (flip != 0) {
                for (iPt = 0; iPt < N_ELEM; iPt++)
                    pts[iPt][Y] = -pts[iPt][Y];
            }

            while ((rot--) != 0) {
                for (iPt = 0; iPt < N_ELEM; iPt++) {
                    x = pts[iPt][X];
                    y = pts[iPt][Y];

                    int xNew = floor((2 * x - 3 * y + 1), 4);
                    int yNew = floor((2 * x + y + 1), 2);
                    pts[iPt][X] = xNew;
                    pts[iPt][Y] = yNew;
                }
            }

            int yMin = pts[0][Y];
            int yMax = yMin;
            for (iPt = 1; iPt < N_ELEM; iPt++) {
                y = pts[iPt][Y];

                if (y < yMin)
                    yMin = y;
                else if (y > yMax)
                    yMax = y;
            }
            int h = yMax - yMin;
            Instance even = target.m_instance[EVEN];
            Instance odd = target.m_instance[ODD];

            shiftUpLines(pts, yMin);
            int w = shiftToX0(pts, even, 0);
            target.setOkPos(EVEN, w, h);
            even.m_vec >>= even.m_offset;

            shiftUpLines(pts, -1);
            w = shiftToX0(pts, odd, 1);
            odd.m_vec >>= N_COL;
            target.setOkPos(ODD, w, h);
            odd.m_vec >>= odd.m_offset;
        }

        static void genAllOrientations() {
            for (int iPiece = 0; iPiece < N_PIECE_TYPE; iPiece++) {
                int refPiece = BaseVecs[iPiece];
                for (int iOrient = 0; iOrient < N_ORIENT; iOrient++) {
                    Piece p = s_basePiece[iPiece][iOrient];
                    genOrientation(refPiece, iOrient, p);
                    if ((iPiece == SKIP_PIECE) && (((iOrient / 3) & 1) != 0))
                        p.m_instance[0].m_allowed = p.m_instance[1].m_allowed = 0;
                }
            }
            for (int iPiece = 0; iPiece < N_PIECE_TYPE; iPiece++) {
                for (int iOrient = 0; iOrient < N_ORIENT; iOrient++) {
                    long mask = 1;
                    for (int iRow = 0; iRow < N_ROW; iRow++) {
                        Instance p = getPiece(iPiece, iOrient, (iRow & 1));
                        for (int iCol = 0; iCol < N_COL; iCol++) {
                            OkPieces allowed = g_okPieces[iRow][iCol];
                            if ((p.m_allowed & mask) != 0) {
                                allowed.pieceVec[iPiece][allowed.nPieces[iPiece]] = p.m_vec << iCol;
                                (allowed.nPieces[iPiece])++;
                            }

                            mask <<= 1;
                        }
                    }
                }
            }
        }

        static Instance getPiece(int iPiece, int iOrient, int iParity) {
            return s_basePiece[iPiece][iOrient].m_instance[iParity];
        }

        void init() {
            for (int i = 0; i < N_PARITY; i++)
                m_instance[i] = new Instance();
        }

        void setOkPos(int isOdd, int w, int h) {
            Instance p = m_instance[isOdd];
            p.m_allowed = 0;
            long posMask = 1L << (isOdd * N_COL);

            for (int y = isOdd; y < N_ROW - h; y += 2, posMask <<= N_COL) {
                if ((p.m_offset) != 0)
                    posMask <<= p.m_offset;

                for (int xPos = 0; xPos < N_COL - p.m_offset; xPos++, posMask <<= 1) {

                    if (xPos >= N_COL - w)
                        continue;

                    int pieceVec = p.m_vec << xPos;

                    if (Board.hasBadIslandsSingle(pieceVec, y))
                        continue;

                    p.m_allowed |= posMask;
                }
            }
        }

        class Instance {
            long m_allowed;
            int m_vec;
            int m_offset;
        }
    }
}
