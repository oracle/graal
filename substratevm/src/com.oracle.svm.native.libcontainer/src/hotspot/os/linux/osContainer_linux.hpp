/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_LINUX_OSCONTAINER_LINUX_HPP
#define OS_LINUX_OSCONTAINER_LINUX_HPP

#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"
#include "memory/allStatic.hpp"

#define OSCONTAINER_ERROR (-2)

// 20ms timeout between re-reads of memory limit and _active_processor_count.
#define OSCONTAINER_CACHE_TIMEOUT (NANOSECS_PER_SEC/50)


namespace svm_container {

class OSContainer: AllStatic {

 private:
  static bool   _is_initialized;
  static bool   _is_containerized;
  static int    _active_processor_count;

 public:
  static void init();
#ifndef NATIVE_IMAGE
  static void print_version_specific_info(outputStream* st);
  static void print_container_helper(outputStream* st, jlong j, const char* metrics);
#endif // !NATIVE_IMAGE

  static inline bool is_containerized();
  static const char * container_type();

  static jlong memory_limit_in_bytes();
  static jlong memory_and_swap_limit_in_bytes();
  static jlong memory_and_swap_usage_in_bytes();
  static jlong memory_soft_limit_in_bytes();
  static jlong memory_usage_in_bytes();
  static jlong memory_max_usage_in_bytes();
  static jlong rss_usage_in_bytes();
  static jlong cache_usage_in_bytes();

  static int active_processor_count();

  static char * cpu_cpuset_cpus();
  static char * cpu_cpuset_memory_nodes();

  static int cpu_quota();
  static int cpu_period();

  static int cpu_shares();

  static jlong pids_max();
  static jlong pids_current();
};

inline bool OSContainer::is_containerized() {
  return _is_containerized;
}


} // namespace svm_container

#endif // OS_LINUX_OSCONTAINER_LINUX_HPP
