/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "runtime/os.hpp"
#include "os_linux.hpp"
#include "osContainer_linux.hpp"
#include "svm_container.hpp"


namespace svm_container {

extern "C" {

// keep in sync with ContainerLibrary.java
constexpr int SUCCESS_IS_NOT_CONTAINERIZED = 0;
constexpr int SUCCESS_IS_CONTAINERIZED = 1;
constexpr int ERROR_LIBCONTAINER_TOO_OLD = 2;
constexpr int ERROR_LIBCONTAINER_TOO_NEW = 3;

static bool is_initialized = false;

// NO_TRANSITION
EXPORT_FOR_SVM int svm_container_initialize(int actual_native_image_container_version) {
  // Note: Do not pass and store any option values to the C++ in here.
  // The C++ code is shared between isolates, but options are not.
  const int expected_native_image_container_version = 240100;
  if (actual_native_image_container_version > expected_native_image_container_version) {
    return ERROR_LIBCONTAINER_TOO_OLD;
  }
  if (actual_native_image_container_version < expected_native_image_container_version) {
    return ERROR_LIBCONTAINER_TOO_NEW;
  }

  os::Linux::initialize_system_info();
  OSContainer::init();
  is_initialized = true;
  return OSContainer::is_containerized() ? SUCCESS_IS_CONTAINERIZED : SUCCESS_IS_NOT_CONTAINERIZED;
}

// NO_TRANSITION
EXPORT_FOR_SVM jlong svm_container_physical_memory() {
  assert(is_initialized, "libsvm_container not yet initialized");
  return os::physical_memory();
}

// NO_TRANSITION
EXPORT_FOR_SVM jlong svm_container_memory_limit_in_bytes() {
  assert(is_initialized, "libsvm_container not yet initialized");
  return OSContainer::memory_limit_in_bytes();
}

// NO_TRANSITION
EXPORT_FOR_SVM jlong svm_container_memory_and_swap_limit_in_bytes() {
  assert(is_initialized, "libsvm_container not yet initialized");
  return OSContainer::memory_and_swap_limit_in_bytes();
}

// NO_TRANSITION
EXPORT_FOR_SVM jlong svm_container_memory_soft_limit_in_bytes() {
  assert(is_initialized, "libsvm_container not yet initialized");
  return OSContainer::memory_soft_limit_in_bytes();
}

// NO_TRANSITION
EXPORT_FOR_SVM jlong svm_container_memory_usage_in_bytes() {
  assert(is_initialized, "libsvm_container not yet initialized");
  return OSContainer::memory_usage_in_bytes();
}

// NO_TRANSITION
EXPORT_FOR_SVM jlong svm_container_memory_max_usage_in_bytes() {
  assert(is_initialized, "libsvm_container not yet initialized");
  return OSContainer::memory_max_usage_in_bytes();
}

// NO_TRANSITION
EXPORT_FOR_SVM jlong svm_container_rss_usage_in_bytes() {
  assert(is_initialized, "libsvm_container not yet initialized");
  return OSContainer::rss_usage_in_bytes();
}

// NO_TRANSITION
EXPORT_FOR_SVM jlong svm_container_cache_usage_in_bytes() {
  assert(is_initialized, "libsvm_container not yet initialized");
  return OSContainer::cache_usage_in_bytes();
}

// NO_TRANSITION
EXPORT_FOR_SVM int svm_container_active_processor_count() {
  assert(is_initialized, "libsvm_container not yet initialized");
  return OSContainer::active_processor_count();
}

} // extern C

} // namespace svm_container

