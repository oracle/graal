/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2015, 2024, SAP SE. All rights reserved.
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

#include "logging/log.hpp"
#include "osContainer_linux.hpp"
#include "os_linux.inline.hpp"
#include "os_posix.inline.hpp"
#include "runtime/globals.hpp"
#include "utilities/checkedCast.hpp"

// put OS-includes here
# include <ctype.h>
# include <stdlib.h>
# include <sys/types.h>
# include <sys/mman.h>
# include <sys/stat.h>
# include <sys/select.h>
# include <sys/sendfile.h>
# include <pthread.h>
# include <signal.h>
# include <endian.h>
# include <errno.h>
# include <fenv.h>
# include <dlfcn.h>
# include <stdio.h>
# include <unistd.h>
# include <sys/resource.h>
# include <pthread.h>
# include <sys/stat.h>
# include <sys/time.h>
# include <sys/times.h>
# include <sys/utsname.h>
# include <sys/socket.h>
# include <pwd.h>
# include <poll.h>
# include <fcntl.h>
# include <string.h>
# include <syscall.h>
# include <sys/sysinfo.h>
# include <sys/ipc.h>
# include <link.h>
# include <stdint.h>
# include <inttypes.h>
# include <sys/ioctl.h>
# include <linux/elf-em.h>
# include <sys/prctl.h>
#ifdef __GLIBC__
# include <malloc.h>
#endif

#ifndef _GNU_SOURCE
  #define _GNU_SOURCE
  #include <sched.h>
  #undef _GNU_SOURCE
#else
  #include <sched.h>
#endif


////////////////////////////////////////////////////////////////////////////////
// global variables
julong os::Linux::_physical_memory = 0;


julong os::physical_memory() {
  jlong phys_mem = 0;
  if (OSContainer::is_containerized()) {
    jlong mem_limit;
    if ((mem_limit = OSContainer::memory_limit_in_bytes()) > 0) {
      log_trace(os)("total container memory: " JLONG_FORMAT, mem_limit);
      return mem_limit;
    }
  }

  phys_mem = Linux::physical_memory();
  log_trace(os)("total system memory: " JLONG_FORMAT, phys_mem);
  return phys_mem;
}


// Returns the amount of swap currently configured, in bytes.
// This can change at any time.
julong os::Linux::host_swap() {
  struct sysinfo si;
  sysinfo(&si);
  return (julong)(si.totalswap * si.mem_unit);
}


void os::Linux::initialize_system_info() {
  set_processor_count((int)sysconf(_SC_NPROCESSORS_CONF));
  _physical_memory = (julong)sysconf(_SC_PHYS_PAGES) * (julong)sysconf(_SC_PAGESIZE);
  assert(processor_count() > 0, "linux error");
}


// Get the current number of available processors for this process.
// This value can change at any time during a process's lifetime.
// sched_getaffinity gives an accurate answer as it accounts for cpusets.
// If it appears there may be more than 1024 processors then we do a
// dynamic check - see 6515172 for details.
// If anything goes wrong we fallback to returning the number of online
// processors - which can be greater than the number available to the process.
static int get_active_processor_count() {
  // Note: keep this function, with its CPU_xx macros, *outside* the os namespace (see JDK-8289477).
  cpu_set_t cpus;  // can represent at most 1024 (CPU_SETSIZE) processors
  cpu_set_t* cpus_p = &cpus;
  size_t cpus_size = sizeof(cpu_set_t);

  int configured_cpus = os::processor_count();  // upper bound on available cpus
  int cpu_count = 0;

// old build platforms may not support dynamic cpu sets
#ifdef CPU_ALLOC

  // To enable easy testing of the dynamic path on different platforms we
  // introduce a diagnostic flag: UseCpuAllocPath
  if (configured_cpus >= CPU_SETSIZE || UseCpuAllocPath) {
    // kernel may use a mask bigger than cpu_set_t
    log_trace(os)("active_processor_count: using dynamic path %s"
                  "- configured processors: %d",
                  UseCpuAllocPath ? "(forced) " : "",
                  configured_cpus);
    cpus_p = CPU_ALLOC(configured_cpus);
    if (cpus_p != nullptr) {
      cpus_size = CPU_ALLOC_SIZE(configured_cpus);
      // zero it just to be safe
      CPU_ZERO_S(cpus_size, cpus_p);
    }
    else {
       // failed to allocate so fallback to online cpus
       int online_cpus = checked_cast<int>(::sysconf(_SC_NPROCESSORS_ONLN));
       log_trace(os)("active_processor_count: "
                     "CPU_ALLOC failed (%s) - using "
                     "online processor count: %d",
                     os::strerror(errno), online_cpus);
       return online_cpus;
    }
  }
  else {
    log_trace(os)("active_processor_count: using static path - configured processors: %d",
                  configured_cpus);
  }
#else // CPU_ALLOC
// these stubs won't be executed
#define CPU_COUNT_S(size, cpus) -1
#define CPU_FREE(cpus)

  log_trace(os)("active_processor_count: only static path available - configured processors: %d",
                configured_cpus);
#endif // CPU_ALLOC

  // pid 0 means the current thread - which we have to assume represents the process
  if (sched_getaffinity(0, cpus_size, cpus_p) == 0) {
    if (cpus_p != &cpus) { // can only be true when CPU_ALLOC used
      cpu_count = CPU_COUNT_S(cpus_size, cpus_p);
    }
    else {
      cpu_count = CPU_COUNT(cpus_p);
    }
    log_trace(os)("active_processor_count: sched_getaffinity processor count: %d", cpu_count);
  }
  else {
    cpu_count = checked_cast<int>(::sysconf(_SC_NPROCESSORS_ONLN));
    warning("sched_getaffinity failed (%s)- using online processor count (%d) "
            "which may exceed available processors", os::strerror(errno), cpu_count);
  }

  if (cpus_p != &cpus) { // can only be true when CPU_ALLOC used
    CPU_FREE(cpus_p);
  }

  assert(cpu_count > 0 && cpu_count <= os::processor_count(), "sanity check");
  return cpu_count;
}

int os::Linux::active_processor_count() {
  return get_active_processor_count();
}

