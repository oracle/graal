/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>

#ifndef _WIN64
#include <alloca.h>
#else
#include <malloc.h>
#define alloca _alloca
#endif

#if defined(__x86_64__) || defined(_WIN64)
#include "amd64cpufeatures.h"
#include "amd64hotspotcpuinfo.h"

#ifndef _WIN64
#include <cpuid.h>

static void read_xem_xcr0(uint32_t *eax, uint32_t *edx) {
  __asm__ __volatile__("xgetbv" : "=a"(*eax), "=d"(*edx) : "c"(0));
}

unsigned int get_cpuid_max (unsigned int ext, unsigned int *sig) {
    return __get_cpuid_max(ext, sig);
}

int get_cpuid_count (unsigned int leaf, unsigned int subleaf, unsigned int *eax, unsigned int *ebx, unsigned int *ecx, unsigned int *edx) {
    int cpuInfo[4];

    __cpuid_count (leaf, subleaf, *eax, *ebx, *ecx, *edx);
    return 1;
}

int get_cpuid (unsigned int leaf, unsigned int *eax, unsigned int *ebx, unsigned int *ecx, unsigned int *edx) {
    return (get_cpuid_count(leaf, 0, eax, ebx, ecx, edx));
}

#else

#include <intrin.h>
#include <immintrin.h>

// microsoft intrinsic list: https://docs.microsoft.com/en-us/cpp/intrinsics/x64-amd64-intrinsics-list?view=msvc-160

static void read_xem_xcr0(uint32_t *eax, uint32_t *edx) {
  uint64_t result = _xgetbv(0);
  *eax = (uint32_t) result;
  *edx = (uint32_t) (result >> 32);
}

// https://docs.microsoft.com/en-us/cpp/intrinsics/cpuid-cpuidex?view=msvc-160
unsigned int get_cpuid_max (unsigned int ext, unsigned int *sig) {
    int cpuInfo[4];

    cpuInfo[0] = 0;
    __cpuidex(cpuInfo, ext, 0);
    *sig = cpuInfo[1];
    return cpuInfo[0];
}

int get_cpuid_count (unsigned int leaf, unsigned int subleaf, unsigned int *eax, unsigned int *ebx, unsigned int *ecx, unsigned int *edx) {
    int cpuInfo[4];

    __cpuidex(cpuInfo, leaf, subleaf);
    *eax = cpuInfo[0];
    *ebx = cpuInfo[1];
    *ecx = cpuInfo[2];
    *edx = cpuInfo[3];
    return 1;
}

int get_cpuid (unsigned int leaf, unsigned int *eax, unsigned int *ebx, unsigned int *ecx, unsigned int *edx) {
    return (get_cpuid_count(leaf, 0, eax, ebx, ecx, edx));
}

#endif

static uint32_t extended_cpu_model(CpuidInfo *_cpuid_info) {
  uint32_t result = _cpuid_info->std_cpuid1_eax.bits.model;
  result |= _cpuid_info->std_cpuid1_eax.bits.ext_model << 4;
  return result;
}

static uint32_t extended_cpu_family(CpuidInfo *_cpuid_info) {
  uint32_t result = _cpuid_info->std_cpuid1_eax.bits.family;
  result += _cpuid_info->std_cpuid1_eax.bits.ext_family;
  return result;
}

static int cpu_family(CpuidInfo *_cpuid_info) {
  return extended_cpu_family(_cpuid_info);
}

static char is_amd(CpuidInfo *_cpuid_info) {
  return _cpuid_info->std_vendor_name_0 == 0x68747541; // 'htuA'
}

static char is_hygon(CpuidInfo *_cpuid_info) {
  return _cpuid_info->std_vendor_name_0 == 0x6F677948; // 'ogyH'
}

static char is_amd_family(CpuidInfo *_cpuid_info) {
  return is_amd(_cpuid_info) || is_hygon(_cpuid_info);
}

static char is_intel(CpuidInfo *_cpuid_info) {
  return _cpuid_info->std_vendor_name_0 == 0x756e6547; // 'uneG'
}

static char is_zx(CpuidInfo *_cpuid_info) {
  return (_cpuid_info->std_vendor_name_0 == 0x746e6543) || (_cpuid_info->std_vendor_name_0 == 0x68532020); // 'tneC'||'hS  '
}

static char is_atom_family(CpuidInfo *_cpuid_info) {
  uint32_t extended_cpu = extended_cpu_model(_cpuid_info);
  return ((cpu_family(_cpuid_info) == 0x06) && ((extended_cpu == 0x36) || (extended_cpu == 0x37) || (extended_cpu == 0x4D))); //Silvermont and Centerton
}

static char is_knights_family(CpuidInfo *_cpuid_info) {
  uint32_t extended_cpu = extended_cpu_model(_cpuid_info);
  return ((cpu_family(_cpuid_info) == 0x06) && ((extended_cpu == 0x57) || (extended_cpu == 0x85))); // Xeon Phi 3200/5200/7200 and Future Xeon Phi
}

static char is_amd_Barcelona(CpuidInfo *_cpuid_info)  {
  return is_amd(_cpuid_info) && extended_cpu_family(_cpuid_info) == CPU_FAMILY_AMD_11H;
}

static char is_intel_family_core(CpuidInfo *_cpuid_info) {
return is_intel(_cpuid_info) && extended_cpu_family(_cpuid_info) == CPU_FAMILY_INTEL_CORE;
}

static char is_intel_tsc_synched_at_init(CpuidInfo *_cpuid_info)  {
  if (is_intel_family_core(_cpuid_info)) {
    uint32_t ext_model = extended_cpu_model(_cpuid_info);
    if (ext_model == CPU_MODEL_NEHALEM_EP     ||
        ext_model == CPU_MODEL_WESTMERE_EP    ||
        ext_model == CPU_MODEL_SANDYBRIDGE_EP ||
        ext_model == CPU_MODEL_IVYBRIDGE_EP) {
      // <= 2-socket invariant tsc support. EX versions are usually used
      // in > 2-socket systems and likely don't synchronize tscs at
      // initialization.
      // Code that uses tsc values must be prepared for them to arbitrarily
      // jump forward or backward.
      return 1;
    }
  }
  return 0;
}

static char supports_processor_topology(CpuidInfo *_cpuid_info) {
  return (_cpuid_info->std_max_function >= 0xB) &&
          // eax[4:0] | ebx[0:15] == 0 indicates invalid topology level.
          // Some cpus have max cpuid >= 0xB but do not support processor topology.
          (((_cpuid_info->tpl_cpuidB0_eax & 0x1f) | _cpuid_info->tpl_cpuidB0_ebx.bits.logical_cpus) != 0);
}

static uint32_t cores_per_cpu(CpuidInfo *_cpuid_info) {
  uint32_t result = 1;
  if (is_intel(_cpuid_info))
  {
    char supports_topology = supports_processor_topology(_cpuid_info);
    if (supports_topology)
    {
      result = _cpuid_info->tpl_cpuidB1_ebx.bits.logical_cpus /
               _cpuid_info->tpl_cpuidB0_ebx.bits.logical_cpus;
    }
    if (!supports_topology || result == 0)
    {
      result = (_cpuid_info->dcp_cpuid4_eax.bits.cores_per_cpu + 1);
    }
  }
  else if (is_amd_family(_cpuid_info))
  {
    result = (_cpuid_info->ext_cpuid8_ecx.bits.cores_per_cpu + 1);
  }
  else if (is_zx(_cpuid_info))
  {
    char supports_topology = supports_processor_topology(_cpuid_info);
    if (supports_topology)
    {
      result = _cpuid_info->tpl_cpuidB1_ebx.bits.logical_cpus /
               _cpuid_info->tpl_cpuidB0_ebx.bits.logical_cpus;
    }
    if (!supports_topology || result == 0)
    {
      result = (_cpuid_info->dcp_cpuid4_eax.bits.cores_per_cpu + 1);
    }
  }
  return result;
}

static uint32_t threads_per_core(CpuidInfo *_cpuid_info) {
  uint32_t result = 1;
  if (is_intel(_cpuid_info) && supports_processor_topology(_cpuid_info))
  {
    result = _cpuid_info->tpl_cpuidB0_ebx.bits.logical_cpus;
  }
  else if (is_zx(_cpuid_info) && supports_processor_topology(_cpuid_info))
  {
    result = _cpuid_info->tpl_cpuidB0_ebx.bits.logical_cpus;
  }
  else if (_cpuid_info->std_cpuid1_edx.bits.ht != 0)
  {
    if (cpu_family(_cpuid_info) >= 0x17)
    {
      result = _cpuid_info->ext_cpuid1E_ebx.bits.threads_per_core + 1;
    }
    else
    {
      result = _cpuid_info->std_cpuid1_ebx.bits.threads_per_cpu /
               cores_per_cpu(_cpuid_info);
    }
  }
  return (result == 0 ? 1 : result);
}

// This is ported from the HotSpot stub get_cpu_info_stub within vm_version_x86.cpp
static void initialize_cpuinfo(CpuidInfo *_cpuid_info)
{
  uint32_t eax, ebx, ecx, edx;
  uint32_t max_level, ext_level;
  uint32_t vendor;

  max_level = get_cpuid_max(0, &vendor);

  get_cpuid(0, &eax, &ebx, &ecx, &edx);
  _cpuid_info->std_max_function = eax;
  _cpuid_info->std_vendor_name_0 = ebx;
  _cpuid_info->std_vendor_name_1 = ecx;
  _cpuid_info->std_vendor_name_2 = edx;

  if (max_level >= 1)
  {
    get_cpuid(1, &eax, &ebx, &ecx, &edx);
    _cpuid_info->std_cpuid1_eax.value = eax;
    _cpuid_info->std_cpuid1_ebx.value = ebx;
    _cpuid_info->std_cpuid1_ecx.value = ecx;
    _cpuid_info->std_cpuid1_edx.value = edx;

    //
    // XCR0, XFEATURE_ENABLED_MASK register
    //
    if (_cpuid_info->std_cpuid1_ecx.bits.osxsave && _cpuid_info->std_cpuid1_ecx.bits.avx)
    {
      // Reading extended control register
      read_xem_xcr0(&eax, &edx);
      _cpuid_info->xem_xcr0_eax.value = eax;
      _cpuid_info->xem_xcr0_edx = edx;
    }
  }

  if (max_level >= 4)
  {
    get_cpuid(4, &eax, &ebx, &ecx, &edx);
    // eax[4:0] == 0 indicates invalid cache
    if ((eax & 0x1f) != 0)
    {
      _cpuid_info->dcp_cpuid4_eax.value = eax;
      _cpuid_info->dcp_cpuid4_ebx.value = ebx;
      _cpuid_info->dcp_cpuid4_ecx = ecx;
      _cpuid_info->dcp_cpuid4_edx = edx;
    }
  }

  if (max_level >= 7)
  {
    get_cpuid(7, &eax, &ebx, &ecx, &edx);
    _cpuid_info->sef_cpuid7_eax.value = eax;
    _cpuid_info->sef_cpuid7_ebx.value = ebx;
    _cpuid_info->sef_cpuid7_ecx.value = ecx;
    _cpuid_info->sef_cpuid7_edx.value = edx;
  }

  // topology
  if (max_level >= 0xB)
  {
    // Threads level
    get_cpuid(0xB, &eax, &ebx, &ecx, &edx);
    _cpuid_info->tpl_cpuidB0_eax = eax;
    _cpuid_info->tpl_cpuidB0_ebx.value = ebx;
    _cpuid_info->tpl_cpuidB0_ecx = ecx;
    _cpuid_info->tpl_cpuidB0_edx = edx;

    // Cores level
    get_cpuid_count(0xB, 1, &eax, &ebx, &ecx, &edx);
    // eax[4:0] | ebx[0:15] == 0 indicates invalid level
    if ((eax & 0x1f) != 0 || (ebx & 0xff) != 0)
    {
      _cpuid_info->tpl_cpuidB1_eax = eax;
      _cpuid_info->tpl_cpuidB1_ebx.value = ebx;
      _cpuid_info->tpl_cpuidB1_ecx = ecx;
      _cpuid_info->tpl_cpuidB1_edx = edx;
    }

    // Packages level
    get_cpuid_count(0xB, 2, &eax, &ebx, &ecx, &edx);
    // eax[4:0] | ebx[0:15] == 0 indicates invalid level
    if ((eax & 0x1f) != 0 || (ebx & 0xff) != 0)
    {
      _cpuid_info->tpl_cpuidB2_eax = eax;
      _cpuid_info->tpl_cpuidB2_ebx.value = ebx;
      _cpuid_info->tpl_cpuidB2_ecx = ecx;
      _cpuid_info->tpl_cpuidB2_edx = edx;
    }
  }

  // ext features

  // figuring out max extended features level
  get_cpuid(0x80000000, &ext_level, &ebx, &ecx, &edx);

  if (ext_level >= 0x80000001)
  {
    get_cpuid(0x80000001, &eax, &ebx, &ecx, &edx);
    _cpuid_info->ext_cpuid1_eax = eax;
    _cpuid_info->ext_cpuid1_ebx = ebx;
    _cpuid_info->ext_cpuid1_ecx.value = ecx;
    _cpuid_info->ext_cpuid1_edx.value = edx;
  }

  if (ext_level >= 0x80000005)
  {
    get_cpuid(0x80000005, &eax, &ebx, &ecx, &edx);
    _cpuid_info->ext_cpuid5_eax = eax;
    _cpuid_info->ext_cpuid5_ebx = ebx;
    _cpuid_info->ext_cpuid5_ecx.value = ecx;
    _cpuid_info->ext_cpuid5_edx.value = edx;
  }

  if (ext_level >= 0x80000007)
  {
    get_cpuid(0x80000007, &eax, &ebx, &ecx, &edx);
    _cpuid_info->ext_cpuid7_eax = eax;
    _cpuid_info->ext_cpuid7_ebx = ebx;
    _cpuid_info->ext_cpuid7_ecx = ecx;
    _cpuid_info->ext_cpuid7_edx.value = edx;
  }

  if (ext_level >= 0x80000008)
  {
    get_cpuid(0x80000008, &eax, &ebx, &ecx, &edx);
    _cpuid_info->ext_cpuid8_eax = eax;
    _cpuid_info->ext_cpuid8_ebx = ebx;
    _cpuid_info->ext_cpuid8_ecx.value = ecx;
    _cpuid_info->ext_cpuid8_edx = edx;
  }

  if (ext_level >= 0x8000001E)
  {
    get_cpuid(0x8000001E, &eax, &ebx, &ecx, &edx);
    _cpuid_info->ext_cpuid1E_eax = eax;
    _cpuid_info->ext_cpuid1E_ebx.value = ebx;
    _cpuid_info->ext_cpuid1E_ecx = ecx;
    _cpuid_info->ext_cpuid1E_edx = edx;
  }
}

// ported from from vm_version_x86.hpp::feature_flags
static void set_cpufeatures(CPUFeatures *features, CpuidInfo *_cpuid_info)
{
  if (_cpuid_info->std_cpuid1_edx.bits.cmpxchg8 != 0)
    features->fCX8 = 1;
  if (_cpuid_info->std_cpuid1_edx.bits.cmov != 0)
    features->fCMOV = 1;
  if (_cpuid_info->std_cpuid1_edx.bits.clflush != 0)
    features->fFLUSH = 1;
  if (_cpuid_info->std_cpuid1_edx.bits.fxsr != 0 || (is_amd_family(_cpuid_info) && _cpuid_info->ext_cpuid1_edx.bits.fxsr != 0))
    features->fFXSR = 1;
  // HT flag is set for multi-core processors also.
  if (threads_per_core(_cpuid_info) > 1)
    features->fHT = 1;
  if (_cpuid_info->std_cpuid1_edx.bits.mmx != 0 || (is_amd_family(_cpuid_info) && _cpuid_info->ext_cpuid1_edx.bits.mmx != 0))
    features->fMMX = 1;
  if (_cpuid_info->std_cpuid1_edx.bits.sse != 0)
    features->fSSE = 1;
  if (_cpuid_info->std_cpuid1_edx.bits.sse2 != 0)
    features->fSSE2 = 1;
  if (_cpuid_info->std_cpuid1_ecx.bits.sse3 != 0)
    features->fSSE3 = 1;
  if (_cpuid_info->std_cpuid1_ecx.bits.ssse3 != 0)
    features->fSSSE3 = 1;
  if (_cpuid_info->std_cpuid1_ecx.bits.sse4_1 != 0)
    features->fSSE4_1 = 1;
  if (_cpuid_info->std_cpuid1_ecx.bits.sse4_2 != 0)
    features->fSSE4_2 = 1;
  if (_cpuid_info->std_cpuid1_ecx.bits.popcnt != 0)
    features->fPOPCNT = 1;
  if (_cpuid_info->std_cpuid1_ecx.bits.avx != 0 &&
      _cpuid_info->std_cpuid1_ecx.bits.osxsave != 0 &&
      _cpuid_info->xem_xcr0_eax.bits.sse != 0 &&
      _cpuid_info->xem_xcr0_eax.bits.ymm != 0)
  {
    features->fAVX = 1;
    features->fVZEROUPPER = 1;
    if (_cpuid_info->sef_cpuid7_ebx.bits.avx2 != 0)
      features->fAVX2 = 1;
    if (_cpuid_info->sef_cpuid7_ebx.bits.avx512f != 0 &&
        _cpuid_info->xem_xcr0_eax.bits.opmask != 0 &&
        _cpuid_info->xem_xcr0_eax.bits.zmm512 != 0 &&
        _cpuid_info->xem_xcr0_eax.bits.zmm32 != 0)
    {
      features->fAVX512F = 1;
      if (_cpuid_info->sef_cpuid7_ebx.bits.avx512cd != 0)
        features->fAVX512CD = 1;
      if (_cpuid_info->sef_cpuid7_ebx.bits.avx512dq != 0)
        features->fAVX512DQ = 1;
      if (_cpuid_info->sef_cpuid7_ebx.bits.avx512pf != 0)
        features->fAVX512PF = 1;
      if (_cpuid_info->sef_cpuid7_ebx.bits.avx512er != 0)
        features->fAVX512ER = 1;
      if (_cpuid_info->sef_cpuid7_ebx.bits.avx512bw != 0)
        features->fAVX512BW = 1;
      if (_cpuid_info->sef_cpuid7_ebx.bits.avx512vl != 0)
        features->fAVX512VL = 1;
      if (_cpuid_info->sef_cpuid7_ecx.bits.avx512_vpopcntdq != 0)
        features->fAVX512_VPOPCNTDQ = 1;
      if (_cpuid_info->sef_cpuid7_ecx.bits.avx512_vpclmulqdq != 0)
        features->fAVX512_VPCLMULQDQ = 1;
      if (_cpuid_info->sef_cpuid7_ecx.bits.vaes != 0)
        features->fAVX512_VAES = 1;
      if (_cpuid_info->sef_cpuid7_ecx.bits.gfni != 0)
        features->fGFNI = 1;
      if (_cpuid_info->sef_cpuid7_ecx.bits.avx512_vnni != 0)
        features->fAVX512_VNNI = 1;
      if (_cpuid_info->sef_cpuid7_ecx.bits.avx512_bitalg != 0)
        features->fAVX512_BITALG = 1;
      if (_cpuid_info->sef_cpuid7_ecx.bits.avx512_vbmi != 0)
        features->fAVX512_VBMI = 1;
      if (_cpuid_info->sef_cpuid7_ecx.bits.avx512_vbmi2 != 0)
        features->fAVX512_VBMI2 = 1;
    }
  }
  if (_cpuid_info->std_cpuid1_ecx.bits.hv != 0)
    features->fHV = 1;
  if (_cpuid_info->sef_cpuid7_ebx.bits.bmi1 != 0)
    features->fBMI1 = 1;
  if (_cpuid_info->std_cpuid1_edx.bits.tsc != 0)
    features->fTSC = 1;
  if (_cpuid_info->ext_cpuid7_edx.bits.tsc_invariance != 0)
    features->fTSCINV_BIT = 1;
  if (_cpuid_info->std_cpuid1_ecx.bits.aes != 0)
    features->fAES = 1;
  if (_cpuid_info->sef_cpuid7_ebx.bits.erms != 0)
    features->fERMS = 1;
  if (_cpuid_info->sef_cpuid7_edx.bits.fast_short_rep_mov != 0)
    features->fFSRM = 1;
  if (_cpuid_info->std_cpuid1_ecx.bits.clmul != 0)
    features->fCLMUL = 1;
  if (_cpuid_info->sef_cpuid7_ebx.bits.rtm != 0)
    features->fRTM = 1;
  if (_cpuid_info->sef_cpuid7_ebx.bits.adx != 0)
    features->fADX = 1;
  if (_cpuid_info->sef_cpuid7_ebx.bits.bmi2 != 0)
    features->fBMI2 = 1;
  if (_cpuid_info->sef_cpuid7_ebx.bits.sha != 0)
    features->fSHA = 1;
  if (_cpuid_info->std_cpuid1_ecx.bits.fma != 0)
    features->fFMA = 1;
  if (_cpuid_info->sef_cpuid7_ebx.bits.clflushopt != 0)
    features->fFLUSHOPT = 1;
  if (_cpuid_info->ext_cpuid1_edx.bits.rdtscp != 0)
    features->fRDTSCP = 1;
  if (_cpuid_info->sef_cpuid7_ecx.bits.rdpid != 0)
    features->fRDPID = 1;

  // AMD|Hygon features.
  if (is_amd_family(_cpuid_info))
  {
    if ((_cpuid_info->ext_cpuid1_edx.bits.tdnow != 0) ||
        (_cpuid_info->ext_cpuid1_ecx.bits.prefetchw != 0))
      features->fAMD_3DNOW_PREFETCH = 1;
    if (_cpuid_info->ext_cpuid1_ecx.bits.lzcnt != 0)
      features->fLZCNT = 1;
    if (_cpuid_info->ext_cpuid1_ecx.bits.sse4a != 0)
      features->fSSE4A = 1;
  }

  // Intel features.
  if (is_intel(_cpuid_info))
  {
    if (_cpuid_info->ext_cpuid1_ecx.bits.lzcnt_intel != 0)
      features->fLZCNT = 1;
    // for Intel, ecx.bits.misalignsse bit (bit 8) indicates support for prefetchw
    if (_cpuid_info->ext_cpuid1_ecx.bits.misalignsse != 0)
    {
      features->fAMD_3DNOW_PREFETCH = 1;
    }
    if (_cpuid_info->sef_cpuid7_ebx.bits.clwb != 0)
    {
      features->fCLWB = 1;
    }
    if (_cpuid_info->sef_cpuid7_edx.bits.serialize != 0)
    {
      features->fSERIALIZE = 1;
    }
  }

  // ZX features.
  if (is_zx(_cpuid_info))
  {
    if (_cpuid_info->ext_cpuid1_ecx.bits.lzcnt_intel != 0)
      features->fLZCNT = 1;
    // for ZX, ecx.bits.misalignsse bit (bit 8) indicates support for prefetchw
    if (_cpuid_info->ext_cpuid1_ecx.bits.misalignsse != 0)
    {
      features->fAMD_3DNOW_PREFETCH = 1;
    }
  }

  // Protection key features.
  if (_cpuid_info->sef_cpuid7_ecx.bits.pku != 0) {
    features->fPKU = 1;
  }
  if (_cpuid_info->sef_cpuid7_ecx.bits.ospke != 0) {
    features->fOSPKE = 1;
  }

  // Control flow enforcement (CET) features.
  if (_cpuid_info->sef_cpuid7_ecx.bits.cet_ss != 0) {
    features->fCET_SS = 1;
  }
  if (_cpuid_info->sef_cpuid7_edx.bits.cet_ibt != 0) {
    features->fCET_IBT = 1;
  }

  // Composite features.
  if (features->fTSCINV_BIT &&
      ((is_amd_family(_cpuid_info) && !is_amd_Barcelona(_cpuid_info)) ||
      is_intel_tsc_synched_at_init(_cpuid_info)))
  {
    features->fTSCINV = 1;
  }
}

/*
* Extracts the CPU features by using cpuid.h.
* Note: This function is implemented in C as cpuid.h
* uses assembly and pollutes registers; it would be
* difficult to keep track of this in Java.
*/
void determineCPUFeatures(CPUFeatures *features)
{


  CpuidInfo cpuid_info_data = {0};
  CpuidInfo *_cpuid_info = &cpuid_info_data;

  initialize_cpuinfo(_cpuid_info);

  set_cpufeatures(features, _cpuid_info);

  // copied from vm_version_x86.cpp::get_processor_features
  if (is_intel(_cpuid_info))
  { // Intel cpus specific settings
    if (is_knights_family(_cpuid_info))
    {
      features->fVZEROUPPER = 0;
      features->fAVX512BW = 0;
      features->fAVX512VL = 0;
      features->fAVX512DQ = 0;
      features->fAVX512_VNNI = 0;
      features->fAVX512_VAES = 0;
      features->fAVX512_VPOPCNTDQ = 0;
      features->fAVX512_VPCLMULQDQ = 0;
      features->fAVX512_VBMI = 0;
      features->fAVX512_VBMI2 = 0;
      features->fCLWB = 0;
      features->fFLUSHOPT = 0;
      features->fGFNI = 0;
      features->fAVX512_BITALG = 0;
    }
  }
}

#elif defined(__aarch64__)

/*
 * The corresponding HotSpot code can be found in vm_version_bsd_aarch64.
 */
#if defined(__APPLE__)

#include <sys/sysctl.h>
#include "aarch64cpufeatures.h"

static uint32_t cpu_has(const char* optional) {
  uint32_t val;
  size_t len = sizeof(val);
  if (sysctlbyname(optional, &val, &len, NULL, 0)) {
    return 0;
  }
  return val;
}

void determineCPUFeatures(CPUFeatures* features) {
  /*
   * Note that Apple HW detection code is not accurate on older processors.
   * All Apple devices have FP and ASIMD.
   */
  features->fFP = 1;
  features->fASIMD = 1;
  features->fEVTSTRM = 0;
  features->fAES = 0;
  features->fPMULL = 0;
  features->fSHA1 = 0;
  features->fSHA2 = 0;
  features->fCRC32 = !!(cpu_has("hw.optional.armv8_crc32"));
  features->fLSE = !!(cpu_has("hw.optional.armv8_1_atomics"));
  features->fDCPOP = 0;
  features->fSHA3 = 0;
  features->fSHA512 = 0;
  features->fSVE = 0;
  features->fSVE2 = 0;
  features->fSTXR_PREFETCH = 0;
  features->fA53MAC = 0;
  features->fDMB_ATOMICS = 0;
  features->fPACA = 0;
  features->fSVEBITPERM = 0;
}

/*
 * The corresponding HotSpot code can be found in vm_version_aarch64 and
 * vm_version_linux_aarch64.
 */
#elif defined(__linux__)

#include <sys/auxv.h>
#include <asm/hwcap.h>
#include "aarch64cpufeatures.h"

#ifndef HWCAP_FP
#define HWCAP_FP            (1L << 0)
#endif
#ifndef HWCAP_ASIMD
#define HWCAP_ASIMD         (1L << 1)
#endif
#ifndef HWCAP_EVTSTRM
#define HWCAP_EVTSTRM       (1L << 2)
#endif
#ifndef HWCAP_AES
#define HWCAP_AES           (1L << 3)
#endif
#ifndef HWCAP_PMULL
#define HWCAP_PMULL         (1L << 4)
#endif
#ifndef HWCAP_SHA1
#define HWCAP_SHA1          (1L << 5)
#endif
#ifndef HWCAP_SHA2
#define HWCAP_SHA2          (1L << 6)
#endif
#ifndef HWCAP_CRC32
#define HWCAP_CRC32         (1L << 7)
#endif
#ifndef HWCAP_LSE
#define HWCAP_LSE           (1L << 8)
#endif
#ifndef HWCAP_DCPOP
#define HWCAP_DCPOP         (1L << 16)
#endif
#ifndef HWCAP_SHA3
#define HWCAP_SHA3          (1L << 17)
#endif
#ifndef HWCAP_SHA512
#define HWCAP_SHA512        (1L << 21)
#endif
#ifndef HWCAP_SVE
#define HWCAP_SVE           (1L << 22)
#endif
#ifndef HWCAP_PACA
#define HWCAP_PACA          (1L << 30)
#endif
#ifndef HWCAP2_SVE2
#define HWCAP2_SVE2         (1L << 1)
#endif
#ifndef HWCAP2_SVEBITPERM
#define HWCAP2_SVEBITPERM   (1L << 4)
#endif

#define CPU_ARM 'A'
#define CPU_CAVIUM 'C'

/*
 * Extracts the CPU features by both reading the hwcaps as well as
 * the proc cpuinfo
 */
void determineCPUFeatures(CPUFeatures* features) {

  unsigned long auxv = getauxval(AT_HWCAP);
  unsigned long auxv2 = getauxval(AT_HWCAP2);
  features->fFP = !!(auxv & HWCAP_FP);
  features->fASIMD = !!(auxv & HWCAP_ASIMD);
  features->fEVTSTRM = !!(auxv & HWCAP_EVTSTRM);
  features->fAES = !!(auxv & HWCAP_AES);
  features->fPMULL = !!(auxv & HWCAP_PMULL);
  features->fSHA1 = !!(auxv & HWCAP_SHA1);
  features->fSHA2 = !!(auxv & HWCAP_SHA2);
  features->fCRC32 = !!(auxv & HWCAP_CRC32);
  features->fLSE = !!(auxv & HWCAP_LSE);
  features->fDCPOP = !!(auxv & HWCAP_DCPOP);
  features->fSHA3 = !!(auxv & HWCAP_SHA3);
  features->fSHA512 = !!(auxv & HWCAP_SHA512);
  features->fSVE = !!(auxv & HWCAP_SVE);
  features->fSVE2 = !!(auxv2 & HWCAP2_SVE2);
  features->fSTXR_PREFETCH = 0;
  features->fA53MAC = 0;
  features->fDMB_ATOMICS = 0;
  features->fPACA = !!(auxv & HWCAP_PACA);
  features->fSVEBITPERM = !!(auxv2 & HWCAP2_SVEBITPERM);

  //checking for features signaled in another way

  int _cpu = 0;
  int _model = 0;
  int _model2 = 0;
  int _variant = -1;
  int _cpu_lines = 0;

  FILE *f = fopen("/proc/cpuinfo", "r");
  if (f) {
    // need a large buffer as the flags line may include lots of text
    char buf[1024], *p;
    while (fgets(buf, sizeof(buf), f) != NULL) {
      if (p = strchr(buf, ':')) {
        long v = strtol(p + 1, NULL, 0);
        if (strncmp(buf, "CPU implementer", sizeof "CPU implementer" - 1) == 0) {
          _cpu = v;
          _cpu_lines++;
        } else if (strncmp(buf, "CPU variant", sizeof "CPU variant" - 1) == 0) {
          _variant = v;
        } else if (strncmp(buf, "CPU part", sizeof "CPU part" - 1) == 0) {
          if (_model != v)
            _model2 = _model;
          _model = v;
        }
      }
    }
    fclose(f);
  } else {
    return;
  }

  if (_cpu == CPU_ARM && _cpu_lines == 1 && _model == 0xd07)
    features->fA53MAC = 1;
  if (_cpu == CPU_ARM && (_model == 0xd03 || _model2 == 0xd03))
    features->fA53MAC = 1;
  if (_cpu == CPU_ARM && (_model == 0xd07 || _model2 == 0xd07))
    features->fSTXR_PREFETCH = 1;
  if (_cpu == CPU_CAVIUM && _model == 0xA1 && _variant == 0)
    features->fDMB_ATOMICS = 1;
}
#endif

#else
/*
 * Dummy for non AMD64 and non AArch64
 */
void determineCPUFeatures(void* features) {
}

#endif

int checkCPUFeatures(uint8_t *buildtimeFeaturesPtr)
{
  // tri-state: -1=unchecked, 0=check ok, 1=check failed
  static int checked = -1;
  if (checked != -1)
    return checked;
  // Over-allocate to a multiple of 64 bit
  const size_t structSizeUint64 = (sizeof(CPUFeatures) + sizeof(uint64_t) - 1) / sizeof(uint64_t);
  const size_t structSizeBytes = structSizeUint64 * sizeof(uint64_t);
  CPUFeatures *hostFeatures = (CPUFeatures*) alloca(structSizeBytes);
  memset(hostFeatures, 0, structSizeBytes);
  determineCPUFeatures(hostFeatures);
  uint8_t *hostFeaturesPtr = (uint8_t*) hostFeatures;
  size_t i;
  for (i = 0; i < structSizeBytes; i += sizeof(uint64_t))
  {
    // Handle 64 bits at once. The memmoves might seem like an overkill,
    // but they are a clear (and defined) way of tell the C compiler our
    // intention. Even at O0, the memmove calls are inlined and the 64 bits
    // are loaded in a single instruction. Starting with O1, no copying
    // whatsoever happens and the | (or) is performed directly using the
    // source memory, just as if we would have cast the (CPUFeatures*) to
    // (uint64_t*), which is unfortunately undefined behavior and leads to
    // undefined (wrong) results in certain compiler/flag combinations
    // (i.e., gcc -O2).
    uint64_t mask;
    uint64_t host;
    memmove(&mask, buildtimeFeaturesPtr + i, sizeof(uint64_t));
    memmove(&host, hostFeaturesPtr + i, sizeof(uint64_t));
    if ((mask | host) != -1)
    {
      checked = 1;
      return checked;
    }
  }
  checked = 0;
  return checked;
}

void checkCPUFeaturesOrExit(uint8_t *buildtimeFeaturesPtr, const char *errorMessage)
{
    if (checkCPUFeatures(buildtimeFeaturesPtr)) {
       fputs(errorMessage, stderr);
       exit(1);
    }
}
