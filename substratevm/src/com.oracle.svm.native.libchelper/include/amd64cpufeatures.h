/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Oracle designates this
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

#include <stdint.h>

typedef struct {
  uint8_t fCX8;
  uint8_t fCMOV;
  uint8_t fFXSR;
  uint8_t fHT;
  uint8_t fMMX;
  uint8_t fAMD_3DNOW_PREFETCH;
  uint8_t fSSE;
  uint8_t fSSE2;
  uint8_t fSSE3;
  uint8_t fSSSE3;
  uint8_t fSSE4A;
  uint8_t fSSE4_1;
  uint8_t fSSE4_2;
  uint8_t fPOPCNT;
  uint8_t fLZCNT;
  uint8_t fTSC;
  uint8_t fTSCINV;
  uint8_t fTSCINV_BIT;
  uint8_t fAVX;
  uint8_t fAVX2;
  uint8_t fAES;
  uint8_t fERMS;
  uint8_t fCLMUL;
  uint8_t fBMI1;
  uint8_t fBMI2;
  uint8_t fRTM;
  uint8_t fADX;
  uint8_t fAVX512F;
  uint8_t fAVX512DQ;
  uint8_t fAVX512PF;
  uint8_t fAVX512ER;
  uint8_t fAVX512CD;
  uint8_t fAVX512BW;
  uint8_t fAVX512VL;
  uint8_t fSHA;
  uint8_t fFMA;
  uint8_t fVZEROUPPER;
  uint8_t fAVX512_VPOPCNTDQ;
  uint8_t fAVX512_VPCLMULQDQ;
  uint8_t fAVX512_VAES;
  uint8_t fAVX512_VNNI;
  uint8_t fFLUSH;
  uint8_t fFLUSHOPT;
  uint8_t fCLWB;
  uint8_t fAVX512_VBMI2;
  uint8_t fAVX512_VBMI;
  uint8_t fHV;
  uint8_t fSERIALIZE;
  uint8_t fRDTSCP;
  uint8_t fRDPID;
  uint8_t fFSRM;
  uint8_t fGFNI;
  uint8_t fAVX512_BITALG;
  uint8_t fPKU;
  uint8_t fOSPKE;
  uint8_t fCET_IBT;
  uint8_t fCET_SS;
  uint8_t fF16C;
  uint8_t fAVX512_IFMA;
  uint8_t fAVX_IFMA;
  uint8_t fAPX_F;
  uint8_t fSHA512;
  uint8_t fAVX512_FP16;
  uint8_t fAVX10_1;
  uint8_t fAVX10_2;
  uint8_t fHYBRID;
} CPUFeatures;
