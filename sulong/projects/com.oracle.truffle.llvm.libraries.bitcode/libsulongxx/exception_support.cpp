/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates.
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

namespace __extern_C {
namespace ___sulong_import_base64 {

namespace bGliYysrYWJp {
static void *__cxa_begin_catch(void *unwind);
static void __cxa_end_catch();
} // namespace bGliYysrYWJp
} // namespace ___sulong_import_base64
} // namespace __extern_C

namespace __cxxabiv1 {

// special sulong namespace
namespace ___sulong_import_base64 {
// we use base64 encoded library names
// libname base64(libc++abi)
namespace bGliYysrYWJp {
static __cxa_exception *cxa_exception_from_exception_unwind_exception(_Unwind_Exception *unwind_exception);

static void *thrown_object_from_cxa_exception(__cxa_exception *exception_header);

static void *__cxa_begin_catch(void *unwind);

} // namespace bGliYysrYWJp
} // namespace ___sulong_import_base64

extern "C" {

unsigned int sulong_eh_canCatch(_Unwind_Exception *unwindHeader, std::type_info *catchType) {

    __cxa_exception *ex = ___sulong_import_base64::bGliYysrYWJp::cxa_exception_from_exception_unwind_exception(unwindHeader);
    void *p = ___sulong_import_base64::bGliYysrYWJp::thrown_object_from_cxa_exception(ex);
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

struct Foreign_unwind_header {
    int64_t exception_class;
    void *foreign_object;
};

void *__cxa_begin_catch(void *unwind) {
    Foreign_unwind_header *v = (Foreign_unwind_header *) unwind;
    if (v->exception_class == 0x504c594754455843) {
        /*
		 * 0x504c594754455843 = (char-encoded) PLYGTEXC = polyglot exception. 
		 * denotes that the exception has not been thrown by LLVM itself, 
		 * but via foreign language and the polyglot interop API. 
		 * See com.oracle.truffle.llvm.runtime.interop.LLVMManagedExceptionObject[.java]
		 */
        return v->foreign_object;
    }
    return __extern_C::___sulong_import_base64::bGliYysrYWJp::__cxa_begin_catch(unwind);
}

void __cxa_end_catch() {
    //for foreign exceptions via the interop library, globals/headers are nullptr
    __cxa_eh_globals *globals = __cxa_get_globals_fast();
    if (globals) {
        __cxa_exception *exception_header = globals->caughtExceptions;
        if (exception_header) {
            __extern_C::___sulong_import_base64::bGliYysrYWJp::__cxa_end_catch();
        }
    }
}

} // extern "C"

} // namespace __cxxabiv1
