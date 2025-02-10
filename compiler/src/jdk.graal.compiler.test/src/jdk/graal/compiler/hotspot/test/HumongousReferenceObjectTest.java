/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import jdk.graal.compiler.core.test.SubprocessTest;
import jdk.internal.vm.annotation.Contended;

@SuppressWarnings("unused")
public class HumongousReferenceObjectTest extends SubprocessTest {
    /*
     * Due to 300 fields with 8K @Contended padding around each field, it takes 2.4M bytes per
     * instance. With small G1 regions, it is bound to cross regions. G1 should properly (card) mark
     * the object nevertheless. With 128M heap, it is enough to allocate ~100 of these objects to
     * provoke at least one GC.
     */

    static volatile Object instance;

    public static void testSnippet() {
        for (int c = 0; c < 100; c++) {
            instance = new HumongousReferenceObjectTest();
        }
    }

    public void runSubprocessTest(String... args) throws IOException, InterruptedException {
        List<String> newArgs = new ArrayList<>();
        Collections.addAll(newArgs, args);
        // Filter out any explicitly selected GC
        newArgs.remove("-XX:+UseZGC");
        newArgs.remove("-XX:+UseG1GC");
        newArgs.remove("-XX:+UseParallelGC");

        launchSubprocess(() -> {
            test("testSnippet");
        }, newArgs.toArray(new String[0]));

        // Test without assertions as well
        newArgs.add("-da");
        launchSubprocess(() -> {
            test("testSnippet");
        }, newArgs.toArray(new String[0]));
    }

    // Checkstyle: stop method name check
    @Test
    public void testG1_1M() throws IOException, InterruptedException {
        testG1("-XX:G1HeapRegionSize=1M");
    }

    @Test
    public void testG1_2M() throws IOException, InterruptedException {
        testG1("-XX:G1HeapRegionSize=2M");
    }

    @Test
    public void testG1_4M() throws IOException, InterruptedException {
        testG1("-XX:G1HeapRegionSize=4M");
    }

    @Test
    public void testG1_8M() throws IOException, InterruptedException {
        testG1("-XX:G1HeapRegionSize=9M");
    }
    // Checkstyle: resume method name check

    public void testG1(String size) throws IOException, InterruptedException {
        runSubprocessTest("-XX:+UseG1GC", "-XX:+EnableContended", "-XX:-RestrictContended", "-Xmx128m", "-XX:ContendedPaddingWidth=8192", size);
    }

    @Test
    public void testParallel() throws IOException, InterruptedException {
        runSubprocessTest("-XX:+UseParallelGC", "-XX:+EnableContended", "-XX:-RestrictContended", "-Xmx128m", "-XX:ContendedPaddingWidth=8192");
    }

    @Contended Integer int1 = 1;
    @Contended Integer int2 = 2;
    @Contended Integer int3 = 3;
    @Contended Integer int4 = 4;
    @Contended Integer int5 = 5;
    @Contended Integer int6 = 6;
    @Contended Integer int7 = 7;
    @Contended Integer int8 = 8;
    @Contended Integer int9 = 9;
    @Contended Integer int10 = 10;
    @Contended Integer int11 = 11;
    @Contended Integer int12 = 12;
    @Contended Integer int13 = 13;
    @Contended Integer int14 = 14;
    @Contended Integer int15 = 15;
    @Contended Integer int16 = 16;
    @Contended Integer int17 = 17;
    @Contended Integer int18 = 18;
    @Contended Integer int19 = 19;
    @Contended Integer int20 = 20;
    @Contended Integer int21 = 21;
    @Contended Integer int22 = 22;
    @Contended Integer int23 = 23;
    @Contended Integer int24 = 24;
    @Contended Integer int25 = 25;
    @Contended Integer int26 = 26;
    @Contended Integer int27 = 27;
    @Contended Integer int28 = 28;
    @Contended Integer int29 = 29;
    @Contended Integer int30 = 30;
    @Contended Integer int31 = 31;
    @Contended Integer int32 = 32;
    @Contended Integer int33 = 33;
    @Contended Integer int34 = 34;
    @Contended Integer int35 = 35;
    @Contended Integer int36 = 36;
    @Contended Integer int37 = 37;
    @Contended Integer int38 = 38;
    @Contended Integer int39 = 39;
    @Contended Integer int40 = 40;
    @Contended Integer int41 = 41;
    @Contended Integer int42 = 42;
    @Contended Integer int43 = 43;
    @Contended Integer int44 = 44;
    @Contended Integer int45 = 45;
    @Contended Integer int46 = 46;
    @Contended Integer int47 = 47;
    @Contended Integer int48 = 48;
    @Contended Integer int49 = 49;
    @Contended Integer int50 = 50;
    @Contended Integer int51 = 51;
    @Contended Integer int52 = 52;
    @Contended Integer int53 = 53;
    @Contended Integer int54 = 54;
    @Contended Integer int55 = 55;
    @Contended Integer int56 = 56;
    @Contended Integer int57 = 57;
    @Contended Integer int58 = 58;
    @Contended Integer int59 = 59;
    @Contended Integer int60 = 60;
    @Contended Integer int61 = 61;
    @Contended Integer int62 = 62;
    @Contended Integer int63 = 63;
    @Contended Integer int64 = 64;
    @Contended Integer int65 = 65;
    @Contended Integer int66 = 66;
    @Contended Integer int67 = 67;
    @Contended Integer int68 = 68;
    @Contended Integer int69 = 69;
    @Contended Integer int70 = 70;
    @Contended Integer int71 = 71;
    @Contended Integer int72 = 72;
    @Contended Integer int73 = 73;
    @Contended Integer int74 = 74;
    @Contended Integer int75 = 75;
    @Contended Integer int76 = 76;
    @Contended Integer int77 = 77;
    @Contended Integer int78 = 78;
    @Contended Integer int79 = 79;
    @Contended Integer int80 = 80;
    @Contended Integer int81 = 81;
    @Contended Integer int82 = 82;
    @Contended Integer int83 = 83;
    @Contended Integer int84 = 84;
    @Contended Integer int85 = 85;
    @Contended Integer int86 = 86;
    @Contended Integer int87 = 87;
    @Contended Integer int88 = 88;
    @Contended Integer int89 = 89;
    @Contended Integer int90 = 90;
    @Contended Integer int91 = 91;
    @Contended Integer int92 = 92;
    @Contended Integer int93 = 93;
    @Contended Integer int94 = 94;
    @Contended Integer int95 = 95;
    @Contended Integer int96 = 96;
    @Contended Integer int97 = 97;
    @Contended Integer int98 = 98;
    @Contended Integer int99 = 99;
    @Contended Integer int100 = 100;
    @Contended Integer int101 = 101;
    @Contended Integer int102 = 102;
    @Contended Integer int103 = 103;
    @Contended Integer int104 = 104;
    @Contended Integer int105 = 105;
    @Contended Integer int106 = 106;
    @Contended Integer int107 = 107;
    @Contended Integer int108 = 108;
    @Contended Integer int109 = 109;
    @Contended Integer int110 = 110;
    @Contended Integer int111 = 111;
    @Contended Integer int112 = 112;
    @Contended Integer int113 = 113;
    @Contended Integer int114 = 114;
    @Contended Integer int115 = 115;
    @Contended Integer int116 = 116;
    @Contended Integer int117 = 117;
    @Contended Integer int118 = 118;
    @Contended Integer int119 = 119;
    @Contended Integer int120 = 120;
    @Contended Integer int121 = 121;
    @Contended Integer int122 = 122;
    @Contended Integer int123 = 123;
    @Contended Integer int124 = 124;
    @Contended Integer int125 = 125;
    @Contended Integer int126 = 126;
    @Contended Integer int127 = 127;
    @Contended Integer int128 = 128;
    @Contended Integer int129 = 129;
    @Contended Integer int130 = 130;
    @Contended Integer int131 = 131;
    @Contended Integer int132 = 132;
    @Contended Integer int133 = 133;
    @Contended Integer int134 = 134;
    @Contended Integer int135 = 135;
    @Contended Integer int136 = 136;
    @Contended Integer int137 = 137;
    @Contended Integer int138 = 138;
    @Contended Integer int139 = 139;
    @Contended Integer int140 = 140;
    @Contended Integer int141 = 141;
    @Contended Integer int142 = 142;
    @Contended Integer int143 = 143;
    @Contended Integer int144 = 144;
    @Contended Integer int145 = 145;
    @Contended Integer int146 = 146;
    @Contended Integer int147 = 147;
    @Contended Integer int148 = 148;
    @Contended Integer int149 = 149;
    @Contended Integer int150 = 150;
    @Contended Integer int151 = 151;
    @Contended Integer int152 = 152;
    @Contended Integer int153 = 153;
    @Contended Integer int154 = 154;
    @Contended Integer int155 = 155;
    @Contended Integer int156 = 156;
    @Contended Integer int157 = 157;
    @Contended Integer int158 = 158;
    @Contended Integer int159 = 159;
    @Contended Integer int160 = 160;
    @Contended Integer int161 = 161;
    @Contended Integer int162 = 162;
    @Contended Integer int163 = 163;
    @Contended Integer int164 = 164;
    @Contended Integer int165 = 165;
    @Contended Integer int166 = 166;
    @Contended Integer int167 = 167;
    @Contended Integer int168 = 168;
    @Contended Integer int169 = 169;
    @Contended Integer int170 = 170;
    @Contended Integer int171 = 171;
    @Contended Integer int172 = 172;
    @Contended Integer int173 = 173;
    @Contended Integer int174 = 174;
    @Contended Integer int175 = 175;
    @Contended Integer int176 = 176;
    @Contended Integer int177 = 177;
    @Contended Integer int178 = 178;
    @Contended Integer int179 = 179;
    @Contended Integer int180 = 180;
    @Contended Integer int181 = 181;
    @Contended Integer int182 = 182;
    @Contended Integer int183 = 183;
    @Contended Integer int184 = 184;
    @Contended Integer int185 = 185;
    @Contended Integer int186 = 186;
    @Contended Integer int187 = 187;
    @Contended Integer int188 = 188;
    @Contended Integer int189 = 189;
    @Contended Integer int190 = 190;
    @Contended Integer int191 = 191;
    @Contended Integer int192 = 192;
    @Contended Integer int193 = 193;
    @Contended Integer int194 = 194;
    @Contended Integer int195 = 195;
    @Contended Integer int196 = 196;
    @Contended Integer int197 = 197;
    @Contended Integer int198 = 198;
    @Contended Integer int199 = 199;
    @Contended Integer int200 = 200;
    @Contended Integer int201 = 201;
    @Contended Integer int202 = 202;
    @Contended Integer int203 = 203;
    @Contended Integer int204 = 204;
    @Contended Integer int205 = 205;
    @Contended Integer int206 = 206;
    @Contended Integer int207 = 207;
    @Contended Integer int208 = 208;
    @Contended Integer int209 = 209;
    @Contended Integer int210 = 210;
    @Contended Integer int211 = 211;
    @Contended Integer int212 = 212;
    @Contended Integer int213 = 213;
    @Contended Integer int214 = 214;
    @Contended Integer int215 = 215;
    @Contended Integer int216 = 216;
    @Contended Integer int217 = 217;
    @Contended Integer int218 = 218;
    @Contended Integer int219 = 219;
    @Contended Integer int220 = 220;
    @Contended Integer int221 = 221;
    @Contended Integer int222 = 222;
    @Contended Integer int223 = 223;
    @Contended Integer int224 = 224;
    @Contended Integer int225 = 225;
    @Contended Integer int226 = 226;
    @Contended Integer int227 = 227;
    @Contended Integer int228 = 228;
    @Contended Integer int229 = 229;
    @Contended Integer int230 = 230;
    @Contended Integer int231 = 231;
    @Contended Integer int232 = 232;
    @Contended Integer int233 = 233;
    @Contended Integer int234 = 234;
    @Contended Integer int235 = 235;
    @Contended Integer int236 = 236;
    @Contended Integer int237 = 237;
    @Contended Integer int238 = 238;
    @Contended Integer int239 = 239;
    @Contended Integer int240 = 240;
    @Contended Integer int241 = 241;
    @Contended Integer int242 = 242;
    @Contended Integer int243 = 243;
    @Contended Integer int244 = 244;
    @Contended Integer int245 = 245;
    @Contended Integer int246 = 246;
    @Contended Integer int247 = 247;
    @Contended Integer int248 = 248;
    @Contended Integer int249 = 249;
    @Contended Integer int250 = 250;
    @Contended Integer int251 = 251;
    @Contended Integer int252 = 252;
    @Contended Integer int253 = 253;
    @Contended Integer int254 = 254;
    @Contended Integer int255 = 255;
    @Contended Integer int256 = 256;
    @Contended Integer int257 = 257;
    @Contended Integer int258 = 258;
    @Contended Integer int259 = 259;
    @Contended Integer int260 = 260;
    @Contended Integer int261 = 261;
    @Contended Integer int262 = 262;
    @Contended Integer int263 = 263;
    @Contended Integer int264 = 264;
    @Contended Integer int265 = 265;
    @Contended Integer int266 = 266;
    @Contended Integer int267 = 267;
    @Contended Integer int268 = 268;
    @Contended Integer int269 = 269;
    @Contended Integer int270 = 270;
    @Contended Integer int271 = 271;
    @Contended Integer int272 = 272;
    @Contended Integer int273 = 273;
    @Contended Integer int274 = 274;
    @Contended Integer int275 = 275;
    @Contended Integer int276 = 276;
    @Contended Integer int277 = 277;
    @Contended Integer int278 = 278;
    @Contended Integer int279 = 279;
    @Contended Integer int280 = 280;
    @Contended Integer int281 = 281;
    @Contended Integer int282 = 282;
    @Contended Integer int283 = 283;
    @Contended Integer int284 = 284;
    @Contended Integer int285 = 285;
    @Contended Integer int286 = 286;
    @Contended Integer int287 = 287;
    @Contended Integer int288 = 288;
    @Contended Integer int289 = 289;
    @Contended Integer int290 = 290;
    @Contended Integer int291 = 291;
    @Contended Integer int292 = 292;
    @Contended Integer int293 = 293;
    @Contended Integer int294 = 294;
    @Contended Integer int295 = 295;
    @Contended Integer int296 = 296;
    @Contended Integer int297 = 297;
    @Contended Integer int298 = 298;
    @Contended Integer int299 = 299;
    @Contended Integer int300 = 300;
}
