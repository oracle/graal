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
#include <stdio.h>
#include <typeinfo>

typedef void *(__cdecl *eh_unwind_pfn)(void *);

typedef void *(__cdecl *eh_copy_pfn)(void *, void *);

extern "C" {
void __sulong_eh_unwind_windows(void *thrownObject, ThrowInfo *throwInfo, char *imageBase) {
    PMFN relUnwind = throwInfo->pmfnUnwind;
    if (relUnwind) {
        eh_unwind_pfn unwind = reinterpret_cast<eh_unwind_pfn>(relUnwind + imageBase);
        unwind(thrownObject);
    }
}

void __sulong_eh_copy_windows(void *thrownObject, CatchableType *catchableType, char *imageBase, void *exceptionSlot, int32_t attributes) {
    // if the exception slot is null, nothing needs to be done
    if (!exceptionSlot) {
        return;
    }

    if (attributes & HT_IsReference) {
        *reinterpret_cast<void **>(exceptionSlot) = thrownObject;
    } else if (catchableType->copyFunction) {
        eh_copy_pfn copy = reinterpret_cast<eh_copy_pfn>(catchableType->copyFunction + imageBase);
        copy(exceptionSlot, thrownObject);
    } else {
        switch (catchableType->sizeOrOffset) {
            case 8:
                *reinterpret_cast<int64_t *>(exceptionSlot) = *reinterpret_cast<int64_t *>(thrownObject);
                break;
            case 4:
                *reinterpret_cast<int32_t *>(exceptionSlot) = *reinterpret_cast<int32_t *>(thrownObject);
                break;
            case 2:
                *reinterpret_cast<int16_t *>(exceptionSlot) = *reinterpret_cast<int16_t *>(thrownObject);
                break;
            case 1:
                *reinterpret_cast<int8_t *>(exceptionSlot) = *reinterpret_cast<int8_t *>(thrownObject);
                break;
            default:
                fprintf(stderr, "__sulong_eh_copy_windows failed because %d is an unsupported size or offset value.\n", catchableType->sizeOrOffset);
                abort();
        }
    }
}

CatchableType *__sulong_eh_canCatch_windows(void *thrownObject, ThrowInfo *throwInfo, std::type_info *catchType, char *imageBase) {
    CatchableTypeArray *catchableTypeArray = reinterpret_cast<CatchableTypeArray *>(imageBase + throwInfo->pCatchableTypeArray);

    for (int i = 0; i < catchableTypeArray->nCatchableTypes; i++) {
        CatchableType *catchableType = reinterpret_cast<CatchableType *>(imageBase + catchableTypeArray->arrayOfCatchableTypes[i]);
        TypeDescriptor *typeDescriptor = reinterpret_cast<TypeDescriptor *>(imageBase + catchableType->pType);
        std::type_info *matchType = (std::type_info *) typeDescriptor;

        if (*matchType == *catchType) {
            return catchableType;
        }
    }

    return NULL;
}
}
