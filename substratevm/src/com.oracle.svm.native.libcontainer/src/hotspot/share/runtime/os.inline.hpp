/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_OS_INLINE_HPP
#define SHARE_RUNTIME_OS_INLINE_HPP

#include "runtime/os.hpp"

#ifndef NATIVE_IMAGE
#include OS_HEADER_INLINE(os)
#include OS_CPU_HEADER_INLINE(os)

// Below are inline functions that are rarely implemented by the platforms.
// Provide default empty implementation.

#ifndef HAVE_PLATFORM_PRINT_NATIVE_STACK

namespace svm_container {

inline bool os::platform_print_native_stack(outputStream* st, const void* context,
                                     char *buf, int buf_size, address& lastpc) {
  return false;
}

} // namespace svm_container

#endif

#ifndef HAVE_CDS_CORE_REGION_ALIGNMENT

namespace svm_container {

inline size_t os::cds_core_region_alignment() {
  return (size_t)os::vm_allocation_granularity();
}

} // namespace svm_container

#endif

#ifndef _WINDOWS
// Currently used only on Windows.

namespace svm_container {

inline bool os::register_code_area(char *low, char *high) {
  return true;
}

} // namespace svm_container

#endif

#ifndef HAVE_FUNCTION_DESCRIPTORS

namespace svm_container {

inline void* os::resolve_function_descriptor(void* p) {
  return nullptr;
}

} // namespace svm_container

#endif
#endif // !NATIVE_IMAGE

#endif // SHARE_RUNTIME_OS_INLINE_HPP
