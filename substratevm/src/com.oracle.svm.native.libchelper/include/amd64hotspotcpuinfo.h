/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include<stdint.h>

// These datastructures are directly copied from HotSpot vm_version_x86.hpp

// cpuid result register layouts.  These are all unions of a uint32_t
// (in case anyone wants access to the register as a whole) and a bitfield.
typedef union {
  uint32_t value;
  struct {
    uint32_t stepping   : 4,
             model      : 4,
             family     : 4,
             proc_type  : 2,
                        : 2,
             ext_model  : 4,
             ext_family : 8,
                        : 4;
  } bits;
} StdCpuid1Eax ;

typedef union { // example, unused
  uint32_t value;
  struct {
    uint32_t brand_id         : 8,
             clflush_size     : 8,
             threads_per_cpu  : 8,
             apic_id          : 8;
  } bits;
} StdCpuid1Ebx ;

typedef union {
  uint32_t value;
  struct {
    uint32_t sse3     : 1,
             clmul    : 1,
                      : 1,
             monitor  : 1,
                      : 1,
             vmx      : 1,
                      : 1,
             est      : 1,
                      : 1,
             ssse3    : 1,
             cid      : 1,
                      : 1,
             fma      : 1,
             cmpxchg16: 1,
                      : 4,
             dca      : 1,
             sse4_1   : 1,
             sse4_2   : 1,
                      : 2,
             popcnt   : 1,
                      : 1,
             aes      : 1,
                      : 1,
             osxsave  : 1,
             avx      : 1,
             f16c     : 1,
                      : 1,
             hv       : 1;
  } bits;
} StdCpuid1Ecx;

typedef union {
  uint32_t value;
  struct {
    uint32_t          : 4,
             tsc      : 1,
                      : 3,
             cmpxchg8 : 1,
                      : 6,
             cmov     : 1,
                      : 3,
             clflush  : 1,
                      : 3,
             mmx      : 1,
             fxsr     : 1,
             sse      : 1,
             sse2     : 1,
                      : 1,
             ht       : 1,
                      : 3;
  } bits;
} StdCpuid1Edx;

typedef union {
  uint32_t value;
  struct {
    uint32_t cache_type    : 5,
                           : 21,
             cores_per_cpu : 6;
  } bits;
} DcpCpuid4Eax;

typedef union {
  uint32_t value;
  struct {
    uint32_t L1_line_size  : 12,
             partitions    : 10,
             associativity : 10;
  } bits;
} DcpCpuid4Ebx;

typedef union {
  uint32_t value;
  struct {
    uint32_t logical_cpus : 16,
                          : 16;
  } bits;
} TplCpuidBEbx;

typedef union {
  uint32_t value;
  struct {
    uint32_t LahfSahf     : 1,
             CmpLegacy    : 1,
                          : 3,
             lzcnt        : 1,
             sse4a        : 1,
             misalignsse  : 1,
             prefetchw    : 1,
                          : 23;
  } bits;
} ExtCpuid1Ecx;

typedef union {
  uint32_t value;
  struct {
    uint32_t           : 22,
             mmx_amd   : 1,
             mmx       : 1,
             fxsr      : 1,
             fxsr_opt  : 1,
             pdpe1gb   : 1,
             rdtscp    : 1,
                       : 1,
             long_mode : 1,
             tdnow2    : 1,
             tdnow     : 1;
  } bits;
} ExtCpuid1Edx;

typedef union {
  uint32_t value;
  struct {
    uint32_t L1_line_size : 8,
             L1_tag_lines : 8,
             L1_assoc     : 8,
             L1_size      : 8;
  } bits;
} ExtCpuid5Ex;

typedef union {
  uint32_t value;
  struct {
    uint32_t               : 8,
            tsc_invariance : 1,
                           : 23;
  } bits;
} ExtCpuid7Edx;

typedef union {
  uint32_t value;
  struct {
    uint32_t cores_per_cpu : 8,
                           : 24;
  } bits;
} ExtCpuid8Ecx;

typedef union {
  uint32_t value;
} SefCpuid7Eax;

typedef union {
  uint32_t value;
  struct {
    uint32_t fsgsbase : 1,
                      : 2,
                 bmi1 : 1,
                      : 1,
                 avx2 : 1,
                      : 2,
                 bmi2 : 1,
                 erms : 1,
                      : 1,
                  rtm : 1,
                      : 4,
              avx512f : 1,
             avx512dq : 1,
                      : 1,
                  adx : 1,
                      : 1,
           avx512ifma : 1,
                      : 1,
           clflushopt : 1,
                 clwb : 1,
                      : 1,
             avx512pf : 1,
             avx512er : 1,
             avx512cd : 1,
                  sha : 1,
             avx512bw : 1,
             avx512vl : 1;
  } bits;
} SefCpuid7Ebx;

typedef union {
  uint32_t value;
  struct {
    uint32_t prefetchwt1 : 1,
             avx512_vbmi : 1,
                    umip : 1,
                     pku : 1,
                   ospke : 1,
                         : 1,
            avx512_vbmi2 : 1,
                  cet_ss : 1,
                    gfni : 1,
                    vaes : 1,
       avx512_vpclmulqdq : 1,
             avx512_vnni : 1,
           avx512_bitalg : 1,
                         : 1,
        avx512_vpopcntdq : 1,
                         : 1,
                         : 1,
                   mawau : 5,
                   rdpid : 1,
                         : 9;
  } bits;
} SefCpuid7Ecx;

typedef union {
  uint32_t value;
  struct {
    uint32_t             : 2,
           avx512_4vnniw : 1,
           avx512_4fmaps : 1,
      fast_short_rep_mov : 1,
                         : 9,
               serialize : 1,
                         : 5,
                 cet_ibt : 1,
                         : 11;
  } bits;
} SefCpuid7Edx;

typedef union {
  uint32_t value;
  struct {
    uint32_t             : 23,
                avx_ifma : 1,
                         : 8;
  } bits;
} SefCpuid7SubLeaf1Eax;

typedef union {
  uint32_t value;
  struct {
    uint32_t             : 21,
                apx_f    : 1,
                         : 10;
  } bits;
} SefCpuid7SubLeaf1Edx;

typedef union {
  uint32_t value;
  struct {
    uint32_t                  : 8,
             threads_per_core : 8,
                              : 16;
  } bits;
} ExtCpuid1EEbx;

typedef union {
  uint32_t value;
  struct {
    uint32_t x87     : 1,
             sse     : 1,
             ymm     : 1,
             bndregs : 1,
             bndcsr  : 1,
             opmask  : 1,
             zmm512  : 1,
             zmm32   : 1,
                     : 11,
             apx_f   : 1,
                     : 12;
  } bits;
} XemXcr0Eax;

// cpuid information block.  All info derived from executing cpuid with
// various function numbers is stored here.  Intel and AMD info is
// merged in this block: accessor methods disentangle it.
//
// The info block is laid out in subblocks of 4 dwords corresponding to
// eax, ebx, ecx and edx, whether or not they contain anything useful.
typedef struct {
  // cpuid function 0
  uint32_t std_max_function;
  uint32_t std_vendor_name_0;
  uint32_t std_vendor_name_1;
  uint32_t std_vendor_name_2;

  // cpuid function 1
  StdCpuid1Eax std_cpuid1_eax;
  StdCpuid1Ebx std_cpuid1_ebx;
  StdCpuid1Ecx std_cpuid1_ecx;
  StdCpuid1Edx std_cpuid1_edx;

  // cpuid function 4 (deterministic cache parameters)
  DcpCpuid4Eax dcp_cpuid4_eax;
  DcpCpuid4Ebx dcp_cpuid4_ebx;
  uint32_t     dcp_cpuid4_ecx; // unused currently
  uint32_t     dcp_cpuid4_edx; // unused currently

  // cpuid function 7 (structured extended features)
  // eax = 7, ecx = 0
  SefCpuid7Eax sef_cpuid7_eax;
  SefCpuid7Ebx sef_cpuid7_ebx;
  SefCpuid7Ecx sef_cpuid7_ecx;
  SefCpuid7Edx sef_cpuid7_edx;
  // cpuid function 7 (structured extended features enumeration sub-leaf 1)
  // eax = 7, ecx = 1
  SefCpuid7SubLeaf1Eax sefsl1_cpuid7_eax;
  SefCpuid7SubLeaf1Edx sefsl1_cpuid7_edx;

  // cpuid function 0xB (processor topology)
  // ecx = 0
  uint32_t     tpl_cpuidB0_eax;
  TplCpuidBEbx tpl_cpuidB0_ebx;
  uint32_t     tpl_cpuidB0_ecx; // unused currently
  uint32_t     tpl_cpuidB0_edx; // unused currently

  // ecx = 1
  uint32_t     tpl_cpuidB1_eax;
  TplCpuidBEbx tpl_cpuidB1_ebx;
  uint32_t     tpl_cpuidB1_ecx; // unused currently
  uint32_t     tpl_cpuidB1_edx; // unused currently

  // ecx = 2
  uint32_t     tpl_cpuidB2_eax;
  TplCpuidBEbx tpl_cpuidB2_ebx;
  uint32_t     tpl_cpuidB2_ecx; // unused currently
  uint32_t     tpl_cpuidB2_edx; // unused currently

  // cpuid function 0x80000000 // example, unused
  uint32_t ext_max_function;
  uint32_t ext_vendor_name_0;
  uint32_t ext_vendor_name_1;
  uint32_t ext_vendor_name_2;

  // cpuid function 0x80000001
  uint32_t     ext_cpuid1_eax; // reserved
  uint32_t     ext_cpuid1_ebx; // reserved
  ExtCpuid1Ecx ext_cpuid1_ecx;
  ExtCpuid1Edx ext_cpuid1_edx;

  // cpuid functions 0x80000002 thru 0x80000004: example, unused
  uint32_t proc_name_0, proc_name_1, proc_name_2, proc_name_3;
  uint32_t proc_name_4, proc_name_5, proc_name_6, proc_name_7;
  uint32_t proc_name_8, proc_name_9, proc_name_10,proc_name_11;

  // cpuid function 0x80000005 // AMD L1, Intel reserved
  uint32_t     ext_cpuid5_eax; // unused currently
  uint32_t     ext_cpuid5_ebx; // reserved
  ExtCpuid5Ex  ext_cpuid5_ecx; // L1 data cache info (AMD)
  ExtCpuid5Ex  ext_cpuid5_edx; // L1 instruction cache info (AMD)

  // cpuid function 0x80000007
  uint32_t     ext_cpuid7_eax; // reserved
  uint32_t     ext_cpuid7_ebx; // reserved
  uint32_t     ext_cpuid7_ecx; // reserved
  ExtCpuid7Edx ext_cpuid7_edx; // tscinv

  // cpuid function 0x80000008
  uint32_t     ext_cpuid8_eax; // unused currently
  uint32_t     ext_cpuid8_ebx; // reserved
  ExtCpuid8Ecx ext_cpuid8_ecx;
  uint32_t     ext_cpuid8_edx; // reserved

  // cpuid function 0x8000001E // AMD 17h
  uint32_t      ext_cpuid1E_eax;
  ExtCpuid1EEbx ext_cpuid1E_ebx; // threads per core (AMD17h)
  uint32_t      ext_cpuid1E_ecx;
  uint32_t      ext_cpuid1E_edx; // unused currently

  // extended control register XCR0 (the XFEATURE_ENABLED_MASK register)
  XemXcr0Eax   xem_xcr0_eax;
  uint32_t     xem_xcr0_edx; // reserved

  // Space to save ymm registers after signal handle
  int          ymm_save[8*4]; // Save ymm0, ymm7, ymm8, ymm15

  // Space to save zmm registers after signal handle
  int          zmm_save[16*4]; // Save zmm0, zmm7, zmm8, zmm31
} CpuidInfo;

enum Extended_Family {
  // AMD
  CPU_FAMILY_AMD_11H       = 0x11,
  // ZX
  CPU_FAMILY_ZX_CORE_F6    = 6,
  CPU_FAMILY_ZX_CORE_F7    = 7,
  // Intel
  CPU_FAMILY_INTEL_CORE    = 6,
  CPU_MODEL_NEHALEM        = 0x1e,
  CPU_MODEL_NEHALEM_EP     = 0x1a,
  CPU_MODEL_NEHALEM_EX     = 0x2e,
  CPU_MODEL_WESTMERE       = 0x25,
  CPU_MODEL_WESTMERE_EP    = 0x2c,
  CPU_MODEL_WESTMERE_EX    = 0x2f,
  CPU_MODEL_SANDYBRIDGE    = 0x2a,
  CPU_MODEL_SANDYBRIDGE_EP = 0x2d,
  CPU_MODEL_IVYBRIDGE_EP   = 0x3a,
  CPU_MODEL_HASWELL_E3     = 0x3c,
  CPU_MODEL_HASWELL_E7     = 0x3f,
  CPU_MODEL_BROADWELL      = 0x3d,
  CPU_MODEL_SKYLAKE        = 0x55
};
