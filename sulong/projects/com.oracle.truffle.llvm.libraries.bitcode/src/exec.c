/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

int execl(const char *path, const char *arg0, ...) {
    fprintf(stderr, "ERROR: execl is unsupported!\n");
    fprintf(stderr, "Tried to execute '%s' with arg0 '%s'\n", path, arg0);
    abort();
}
int execle(const char *path, const char *arg0, ...) {
    fprintf(stderr, "ERROR: execle is unsupported!\n");
    fprintf(stderr, "Tried to execute '%s' with arg0 '%s'\n", path, arg0);
    abort();
}
int execlp(const char *file, const char *arg0, ...) {
    fprintf(stderr, "ERROR: execlp is unsupported!\n");
    fprintf(stderr, "Tried to execute '%s' with arg0 '%s'\n", file, arg0);
    abort();
}
int execv(const char *path, char *const argv[]) {
    fprintf(stderr, "ERROR: execv is unsupported!\n");
    fprintf(stderr, "Tried to execute '%s' with arg0 '%s'\n", path, *argv);
    abort();
}
int execve(const char *path, char *const argv[], char *const envp[]) {
    fprintf(stderr, "ERROR: execve is unsupported!\n");
    fprintf(stderr, "Tried to execute '%s' with arg0 '%s'\n", path, *argv);
    abort();
}
int execvp(const char *file, char *const argv[]) {
    fprintf(stderr, "ERROR: execvp is unsupported!\n");
    fprintf(stderr, "Tried to execute '%s' with arg0 '%s'\n", file, *argv);
    abort();
}
int fexecve(int fd, char *const argv[], char *const envp[]) {
    fprintf(stderr, "ERROR: fexecve is unsupported!\n");
    fprintf(stderr, "Tried to execute fd %d with arg0 '%s'\n", fd, *argv);
    abort();
}
