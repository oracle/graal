/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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
#include <assert.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/stat.h>

int main() {
    int ret = 0;

    if ((ret = access("", F_OK)) != -1) {
        assert(ret == 0);
        return 1;
    }
    assert(errno == ENOENT);

    if ((ret = access("this file should not exist", F_OK)) != -1) {
        assert(ret == 0);
        return 2;
    }
    assert(errno == ENOENT);

    const char *test_filename = "sulong_access_test_file";
    FILE *test_file = fopen(test_filename, "w");
    if (!test_file) {
        /* Cannot continue test */
        return 3;
    }
    fclose(test_file);

#ifndef __APPLE__
    /* macOS would accept the following and thus stop here */
    if ((ret = access(test_filename, (R_OK | W_OK | X_OK) + 1)) != -1) {
        assert(ret == 0);
        return 4;
    }
    assert(errno == EINVAL);
#endif

    if (chmod(test_filename, S_IRUSR) == -1) {
        /* Cannot continue test */
        return 5;
    }

    if ((ret = access(test_filename, R_OK)) == -1) {
        assert(errno == EACCES);
        return 6;
    }
    assert(ret == 0);

    if ((ret = access(test_filename, R_OK | W_OK)) != -1) {
        assert(ret == 0);
        return 7;
    }
    assert(errno == EACCES);

    if ((ret = access(test_filename, X_OK)) != -1) {
        assert(ret == 0);
        return 8;
    }
    assert(errno == EACCES);

    if (chmod(test_filename, S_IRUSR | S_IWUSR) == -1) {
        /* Cannot continue test */
        return 9;
    }

    if ((ret = access(test_filename, R_OK | W_OK)) == -1) {
        assert(errno == EACCES);
        return 10;
    }
    assert(ret == 0);

    if ((ret = access(test_filename, F_OK)) == -1) {
        assert(errno == EACCES);
        return 11;
    }
    assert(ret == 0);

    if (chmod(test_filename, S_IXUSR) == -1) {
        /* Cannot continue test */
        return 12;
    }

    if ((ret = access(test_filename, R_OK | W_OK)) != -1) {
        assert(ret == 0);
        return 13;
    }
    assert(errno == EACCES);

    (void) remove(test_filename);

    return 0;
}
