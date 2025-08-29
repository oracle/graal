/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, 2020, Arm Limited. All rights reserved.
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
  uint8_t fFP;
  uint8_t fASIMD;
  uint8_t fEVTSTRM;
  uint8_t fAES;
  uint8_t fPMULL;
  uint8_t fSHA1;
  uint8_t fSHA2;
  uint8_t fCRC32;
  uint8_t fLSE;
  uint8_t fDCPOP;
  uint8_t fSHA3;
  uint8_t fSHA512;
  uint8_t fSVE;
  uint8_t fSVE2;
  uint8_t fSTXR_PREFETCH;
  uint8_t fA53MAC;
  uint8_t fDMB_ATOMICS;
  uint8_t fPACA;
  uint8_t fSVEBITPERM;
  uint8_t fFPHP;
  uint8_t fASIMDHP;
  uint8_t fSB;
} CPUFeatures;
