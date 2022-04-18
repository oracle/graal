/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#include <cstdio>

#include "cxa_exception.h"
#include "private_typeinfo.h"

namespace __cxxabiv1 {

static inline __cxa_exception *cxa_exception_from_thrown_object(void *thrown_object) {
    return static_cast<__cxa_exception *>(thrown_object) - 1;
}

// Note:  This is never called when exception_header is masquerading as a
//        __cxa_dependent_exception.
static inline void *thrown_object_from_cxa_exception(__cxa_exception *exception_header) {
    return static_cast<void *>(exception_header + 1);
}

//  Get the exception object from the unwind pointer.
//  Relies on the structure layout, where the unwind pointer is right in
//  front of the user's exception object
static __cxa_exception *cxa_exception_from_exception_unwind_exception(_Unwind_Exception *unwind_exception) {
    return cxa_exception_from_thrown_object(unwind_exception + 1);
}

extern "C" {

unsigned int sulong_eh_canCatch(_Unwind_Exception *unwindHeader, std::type_info *catchType) {
    __cxa_exception *ex = cxa_exception_from_exception_unwind_exception(unwindHeader);
    void *p = thrown_object_from_cxa_exception(ex);
    __shim_type_info *et = dynamic_cast<__shim_type_info *>(ex->exceptionType);
    __shim_type_info *ct = dynamic_cast<__shim_type_info *>(catchType);
    if (et == NULL || ct == NULL) {
        fprintf(stderr, "libsulong: Type error in sulong_eh_canCatch(...).\n");
        abort();
    }
    if (ct->can_catch(et, p)) {
        ex->adjustedPtr = p;
        return 1;
    } else {
        return 0;
    }
}

} // extern "C"

} // namespace __cxxabiv1
