/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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

#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <locale.h>
#include <graalvm/llvm/polyglot.h>

#include "exit.h"
#include "abort.h"

#ifdef __linux__
#include <elf.h>
#else
#define AT_NULL 0
#define AT_PLATFORM 15
#define AT_RANDOM 25
#define AT_EXECFN 31
typedef struct {
    uint64_t a_type;
    union {
        uint64_t a_val;
    } a_un;
} Elf64_auxv_t;
#endif

#if !defined(_WIN32)
extern char **environ;
#endif
char *__progname;
static Elf64_auxv_t *__auxv;
long *__sulong_start_arguments = NULL;

__attribute__((visibility("hidden"))) const size_t _DYNAMIC[1];

char *__sulong_byte_array_to_native(void *java_byte_array) {
    int length = polyglot_get_array_size(java_byte_array);
    char *result = malloc(sizeof(char) * length + 1);
    for (int i = 0; i < length; i++) {
        result[i] = polyglot_as_i8(polyglot_get_array_element(java_byte_array, i));
    }
    result[length] = '\0';
    return result;
}

void __sulong_byte_arrays_to_native(char **dest, void **java_byte_arrays) {
    int length = polyglot_get_array_size(java_byte_arrays);
    for (int i = 0; i < length; i++) {
        dest[i] = __sulong_byte_array_to_native(polyglot_get_array_element(java_byte_arrays, i));
    }
}

__attribute__((optnone, noinline)) void __sulong_init_libc(char **envp, char *pn) {
    // nothing to do
}

/**
 * Initialize native bits of the LLVM context.
 * WARNING: this is called before constructors are executed!
 */
void __sulong_init_context(void **argv_java_byte_arrays, void **envp_java_byte_arrays, void **random_java_byte_array) {
    int argc = polyglot_get_array_size(argv_java_byte_arrays);
    int envc = polyglot_get_array_size(envp_java_byte_arrays);
    int auxc = 4;

    size_t total_argument_size = sizeof(void *) + (argc + 1) * sizeof(char *) + (envc + 1) * sizeof(char *) + auxc * sizeof(Elf64_auxv_t);
    long *p = __sulong_start_arguments = malloc(total_argument_size);
    p[0] = argc;

    char **argv = (char **) (p + 1);
    __sulong_byte_arrays_to_native(argv, argv_java_byte_arrays);
    argv[argc] = NULL;

    char **envp = argv + argc + 1;
    __sulong_byte_arrays_to_native(envp, envp_java_byte_arrays);
    envp[envc] = NULL;

    Elf64_auxv_t *aux = (Elf64_auxv_t *) (envp + envc + 1);
    aux[0].a_type = AT_EXECFN;
    aux[0].a_un.a_val = (uint64_t) argv[0];
    aux[1].a_type = AT_PLATFORM;
    aux[1].a_un.a_val = (uint64_t) "x86_64";
    aux[2].a_type = AT_RANDOM;
    aux[2].a_un.a_val = (uint64_t) __sulong_byte_array_to_native(random_java_byte_array);
    aux[3].a_type = AT_NULL;
    aux[3].a_un.a_val = 0;

    __sulong_init_libc(envp, argv[0]);
}

void __sulong_update_application_path(char *application_path, char **argv, Elf64_auxv_t *auxv) {
    __progname = argv[0] = application_path;
    auxv[0].a_un.a_val = (uint64_t) application_path;
}

int _start(int type, char *application_path_java_byte_array, void *main) {
    long *p = __sulong_start_arguments;
    int argc = p[0];
    char **argv = (void *) (p + 1);
    char **envp = argv + argc + 1;

    int envc = 0;
    char **ptr;
    for (ptr = envp; *ptr; ptr++) {
        envc++;
    }

    environ = envp;
    __auxv = (Elf64_auxv_t *) (envp + envc + 1);

    // update the application path now that we know it
    char *application_path = __sulong_byte_array_to_native(application_path_java_byte_array);
    __sulong_update_application_path(application_path, argv, __auxv);

    // setlocale(3): On startup of the main program, the portable "C" locale is selected as default.
    setlocale(LC_ALL, "C");

    switch (type) {
        /* C/C++/... */
        default:
        case 0: {
            int (*i32main)(int argc, char **argv, char **envp) = (int (*)(int, char **, char **)) main;
            __sulong_exit(i32main(argc, argv, envp));
            break;
        }
        /* Rust */
        case 1: {
            long (*i64main)(long argc, char **argv) = (long (*)(long, char **)) main;
            __sulong_exit(i64main(argc, argv));
            break;
        }
        /* non-standard C: void main(int, char**, char**) */
        case 2: {
            void (*vmain)(int argc, char **argv, char **envp) = (void (*)(int, char **, char **)) main;
            vmain(argc, argv, envp);
            __sulong_exit(0);
            break;
        }
        /* non-standard C: char main(int, char**, char**) */
        case 3: {
            char (*i8main)(int argc, char **argv, char **envp) = (char (*)(int, char **, char **)) main;
            __sulong_exit(i8main(argc, argv, envp));
            break;
        }
        /* non-standard C: short main(int, char**, char**) */
        case 4: {
            short (*i16main)(int argc, char **argv, char **envp) = (short (*)(int, char **, char **)) main;
            __sulong_exit(i16main(argc, argv, envp));
            break;
        }
        /* non-standard C: long main(int, char**, char**) */
        case 5: {
            long (*i64main)(int argc, char **argv, char **envp) = (long (*)(int, char **, char **)) main;
            __sulong_exit(i64main(argc, argv, envp));
            break;
        }
    }
    __sulong_abort();
}

#ifdef __linux__
unsigned long getauxval(unsigned long type) {
    Elf64_auxv_t *auxv;
    for (auxv = __auxv; auxv->a_type != AT_NULL; auxv++) {
        if (auxv->a_type == type) {
            return auxv->a_un.a_val;
        }
    }
    return 0;
}
#endif
