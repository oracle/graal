/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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

#include <sys/mman.h>
#include <stdlib.h>
#include <stdint.h>
#include <unistd.h>

struct globals_header {
    uint64_t size;
    __int128 data[0]; // align to 128 bit
};

static uint64_t align_up(uint64_t size) {
    long pagesize = sysconf(_SC_PAGESIZE);
    uint64_t ret = size;
    if (ret % pagesize != 0) {
        ret += pagesize - ret % pagesize;
    }
    return ret;
}

void *__sulong_allocate_globals_block(uint64_t size) {
    uint64_t finalSize = align_up(size + sizeof(struct globals_header));
    struct globals_header *page = mmap(NULL, finalSize, PROT_READ | PROT_WRITE, MAP_ANON | MAP_PRIVATE, -1, 0);
    page->size = finalSize;
    return &page->data;
}

void __sulong_protect_readonly_globals_block(void *ptr) {
    struct globals_header *header = (struct globals_header *) (ptr - sizeof(struct globals_header));
    mprotect(header, header->size, PROT_READ);
}

void __sulong_free_globals_block(void *ptr) {
    struct globals_header *header = (struct globals_header *) (ptr - sizeof(struct globals_header));
    munmap(header, header->size);
}
