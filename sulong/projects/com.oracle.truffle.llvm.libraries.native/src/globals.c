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

#include <stdlib.h>
#include <stdint.h>

#if defined(_WIN32)
#include <Windows.h>
#elif defined(__unix__) || defined(__APPLE__)
#include <sys/mman.h>
#include <unistd.h>
#endif

struct globals_header {
    uint64_t size;
    __int128 data[0]; // align to 128 bit
};

#if !defined(_WIN32)
static uint64_t align_up(uint64_t size) {
    long pagesize = sysconf(_SC_PAGESIZE);
    uint64_t ret = size;
    if (ret % pagesize != 0) {
        ret += pagesize - ret % pagesize;
    }
    return ret;
}
#endif

void *__sulong_allocate_globals_block(uint64_t size) {
#if defined(_WIN32)
    uint64_t finalSize = size + sizeof(struct globals_header);
    struct globals_header *page = VirtualAlloc(NULL, finalSize, MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
#else
    uint64_t finalSize = align_up(size + sizeof(struct globals_header));
    struct globals_header *page = mmap(NULL, finalSize, PROT_READ | PROT_WRITE, MAP_ANON | MAP_PRIVATE, -1, 0);
#endif
    page->size = finalSize;
    return &page->data;
}

void __sulong_protect_readonly_globals_block(void *ptr) {
    struct globals_header *header = (struct globals_header *) (ptr - sizeof(struct globals_header));
#if defined(_WIN32)
    DWORD old_protect;
    VirtualProtect(header, header->size, PAGE_READONLY, &old_protect);
#else
    mprotect(header, header->size, PROT_READ);
#endif
}

void __sulong_free_globals_block(void *ptr) {
    struct globals_header *header = (struct globals_header *) (ptr - sizeof(struct globals_header));
#if defined(_WIN32)
    /*
     * The `dwSize` must be 0 when using MEM_RELEASE.
     * @see https://docs.microsoft.com/en-us/windows/win32/api/memoryapi/nf-memoryapi-virtualfree
     */
    VirtualFree(header, 0, MEM_RELEASE);
#else
    munmap(header, header->size);
#endif
}
