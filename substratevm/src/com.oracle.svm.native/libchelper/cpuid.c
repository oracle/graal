/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include <cpuid.h>
#include "include/cpufeatures.h"


#define bit_CX8_compat           0x00000100
#define bit_SSE41_compat         0x00080000
#define bit_SSE42_compat         0x00100000
#define bit_AES_compat           0x02000000
#define bit_PCLMUL_compat        0x00000002
#define bit_TSC_compat           0x00000010
#define bit_ERMS_compat          0x00000200
#define bit_AVX2_compat          0x00000020
#define bit_PREFETCHW_compat     0x00000100
#define bit_BMI1_compat          0x00000008
#define bit_BMI2_compat          0x00000100
#define bit_AVX512F_compat       0x00010000
#define bit_AVX512DQ_compat      0x00020000
#define bit_AVX512PF_compat      0x04000000
#define bit_AVX512ER_compat      0x08000000
#define bit_AVX512CD_compat      0x10000000
#define bit_AVX512BW_compat      0x40000000
#define bit_RTM_compat           0x00000800
#define bit_ADX_compat           0x00080000
#define bit_SSE4a_compat         0x00000040
#define bit_LZCNT_compat         0x00000020
#define bit_HTT_compat           0x10000000

/*
 * Extracts the CPU features by using cpuid.h.
 * Note: This function is implemented in C as cpuid.h
 * uses assembly and pollutes registers; it would be
 * difficult to keep track of this in Java.
 */
void determineCPUFeatures(CPUFeatures* features) {
  unsigned int eax, ebx, ecx, edx;
  unsigned int max_level, ext_level;
  unsigned int vendor;

  max_level = __get_cpuid_max (0, &vendor);
  if (max_level < 1) {
    return;
  }

  __cpuid (1, eax, ebx, ecx, edx);

  features->fCX8  = !!(edx & bit_CX8_compat);
  features->fCMOV = !!(edx & bit_CMOV);
  features->fFXSR = !!(edx & bit_FXSAVE);
  features->fHT   = !!(edx & bit_HTT_compat);
  features->fMMX  = !!(edx & bit_MMX);
  features->fSSE  = !!(edx & bit_SSE);
  features->fSSE2 = !!(edx & bit_SSE2);
  features->fTSC  = !!(edx & bit_TSC_compat);

  features->fSSE3   = !!(ecx & bit_SSE3);
  features->fSSSE3  = !!(ecx & bit_SSSE3);
  features->fSSE41  = !!(ecx & bit_SSE41_compat);
  features->fSSE42  = !!(ecx & bit_SSE42_compat);
  features->fPOPCNT = !!(ecx & bit_POPCNT);
  features->fAVX    = !!(ecx & bit_AVX);
  features->fAES    = !!(ecx & bit_AES_compat);
  features->fCLMUL  = !!(ecx & bit_PCLMUL_compat);

  if (max_level >= 7) {
    __cpuid_count (7, 0, eax, ebx, ecx, edx);

    features->fERMS     = !!(ebx & bit_ERMS_compat);
    features->fAVX2     = !!(ebx & bit_AVX2_compat);
    features->fBMI1     = !!(ebx & bit_BMI1_compat);
    features->fBMI2     = !!(ebx & bit_BMI2_compat);
    features->fRTM      = !!(ebx & bit_RTM_compat);
    features->fADX      = !!(ebx & bit_ADX_compat);
    features->fAVX512F  = !!(ebx & bit_AVX512F_compat);
    features->fAVX512DQ = !!(ebx & bit_AVX512DQ_compat);
    features->fAVX512PF = !!(ebx & bit_AVX512PF_compat);
    features->fAVX512ER = !!(ebx & bit_AVX512ER_compat);
    features->fAVX512CD = !!(ebx & bit_AVX512CD_compat);
    features->fAVX512BW = !!(ebx & bit_AVX512BW_compat);
  }

  // figuring out extended features
  __cpuid (0x80000000, ext_level, ebx, ecx, edx);

  if (ext_level > 0x80000000) {
    __cpuid (0x80000001, eax, ebx, ecx, edx);

    features->fSSE4A = !!(ecx & bit_SSE4a_compat);
    features->fLZCNT = !!(ecx & bit_LZCNT_compat);
    features->fAMD3DNOWPREFETCH = !!(ecx & bit_PREFETCHW_compat);
  }
}
