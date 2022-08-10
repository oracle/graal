/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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

typedef void *_ThrowInfo;

#include <ehdata.h>
#include <typeinfo>
#include "stdio.h"
#include "cxa_exception.h"
#include "../exception_helpers.h"

bool sulong_exception_type_can_catch(std::type_info *exceptionType, std::type_info *catchType) {
    return *exceptionType == *catchType;
}

extern "C" {
bool sulong_eh_canCatch_windows(void *thrownObject, ThrowInfo *throwInfo, std::type_info *catchType, char *imageBase) {
    CatchableTypeArray *catchableTypeArray = reinterpret_cast<CatchableTypeArray *>(imageBase + throwInfo->pCatchableTypeArray);

    for (int i = 0; i < catchableTypeArray->nCatchableTypes; i++) {
        CatchableType *catchableType = reinterpret_cast<CatchableType *>(imageBase + catchableTypeArray->arrayOfCatchableTypes[i]);
        TypeDescriptor *typeDescriptor = reinterpret_cast<TypeDescriptor *>(imageBase + catchableType->pType);
        std::type_info *matchType = (std::type_info *) typeDescriptor;

        if (sulong_exception_type_can_catch(matchType, catchType)) {
            return true;
        }
    }

    return false;
}
}
