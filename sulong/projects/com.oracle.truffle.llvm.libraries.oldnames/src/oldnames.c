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

#define _CRT_SECURE_NO_WARNINGS
#define __STDC__ 1

#include <conio.h>
#include <direct.h>
#include <errno.h>
#include <float.h>
#include <io.h>
#include <math.h>
#include <process.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/timeb.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/utime.h>
#include <time.h>

// TODO: daylight, environ, sys_errlist, sys_nerr, huge, timezone, tzname

#define REDIR_FUNC extern inline

REDIR_FUNC int access(const char *path, int mode) {
    return _access(path, mode);
}

// REDIR_FUNC double cabs(struct _complex z) {
//   return _cabs(z);
// }

REDIR_FUNC int chdir(const char *path) {
    return _chdir(path);
}

REDIR_FUNC int chmod(const char *filename, int pmode) {
    return _chmod(filename, pmode);
}

REDIR_FUNC int chsize(int fd, long size) {
    return _chsize(fd, size);
}

// this function has been completely deprecated
// REDIR_FUNC char * cgets(char * buffer) {
//  return _cgets(buffer);
// }

REDIR_FUNC int close(int fd) {
    return _close(fd);
}

REDIR_FUNC unsigned int control87(unsigned int new, unsigned int mask) {
    return _control87(new, mask);
}

REDIR_FUNC int cprintf(const char *format, ...) {
    va_list args;
    va_start(args, format);
    int result = _vcprintf(format, args);
    va_end(args);
    return result;
}

REDIR_FUNC int cputs(const char *str) {
    return _cputs(str);
}

REDIR_FUNC int creat(const char *filename, int pmode) {
    return _creat(filename, pmode);
}

REDIR_FUNC int cscanf(const char *format, ...) {
    va_list arglist;
    va_start(arglist, format);
    int result = vscanf(format, arglist);
    va_end(arglist);
    return result;
}

REDIR_FUNC intptr_t cwait(int *termstat, intptr_t procHandle, int action) {
    return _cwait(termstat, procHandle, action);
}

REDIR_FUNC int dup(int fd) {
    return _dup(fd);
}

REDIR_FUNC int dup2(int fd1, int fd2) {
    return _dup2(fd1, fd2);
}

REDIR_FUNC char *ecvt(double value, int count, int *dec, int *sign) {
    return _ecvt(value, count, dec, sign);
}

REDIR_FUNC int eof(int fd) {
    return _eof(fd);
}

int countArgs(va_list args) {
    va_list arglist;
    char *arg;
    int argc;
    va_copy(arglist, args);

    // first count the number of arguments
    va_copy(arglist, args);
    do {
        arg = va_arg(arglist, char *);
        argc++;
    } while (arg != NULL);
    argc--;

    return argc;
}

void copyArgs(const char **argv, int argc, va_list *args) {
    for (int i = 0; i < argc + 1; i++)
        argv[i] = va_arg(*args, const char *);
}

REDIR_FUNC intptr_t execl(const char *cmdname, ...) {
    va_list arglist;
    va_start(arglist, cmdname);

    int argc = countArgs(arglist);
    const char **argv = (const char **) _alloca((argc + 1) * sizeof(char *));
    copyArgs(argv, argc, &arglist);

    return _execv(cmdname, argv);
}

REDIR_FUNC intptr_t execlp(const char *cmdname, ...) {
    va_list arglist;
    va_start(arglist, cmdname);

    int argc = countArgs(arglist);
    const char **argv = (const char **) _alloca((argc + 1) * sizeof(char *));
    copyArgs(argv, argc, &arglist);

    return _execvp(cmdname, argv);
}

REDIR_FUNC intptr_t execle(const char *cmdname, ...) {
    va_list arglist;
    va_start(arglist, cmdname);

    int argc = countArgs(arglist);
    const char **argv = (const char **) _alloca((argc + 1) * sizeof(char *));
    copyArgs(argv, argc, &arglist);

    const char *const *envp = va_arg(arglist, const char *const *);

    return _execve(cmdname, argv, envp);
}

REDIR_FUNC intptr_t execlpe(const char *cmdname, ...) {
    va_list arglist;
    va_start(arglist, cmdname);

    int argc = countArgs(arglist);
    const char **argv = (const char **) _alloca((argc + 1) * sizeof(char *));
    copyArgs(argv, argc, &arglist);

    const char *const *envp = va_arg(arglist, const char *const *);

    return _execvpe(cmdname, argv, envp);
}

REDIR_FUNC intptr_t execv(const char *cmdname, const char *const *argv) {
    return _execv(cmdname, argv);
}

REDIR_FUNC intptr_t execve(const char *cmdname, const char *const *argv, const char *const *envp) {
    return _execve(cmdname, argv, envp);
}

REDIR_FUNC intptr_t execvp(const char *cmdname, const char *const *argv) {
    return _execvp(cmdname, argv);
}

REDIR_FUNC intptr_t execvpe(const char *cmdname, const char *const *argv, const char *const *envp) {
    return _execvpe(cmdname, argv, envp);
}

REDIR_FUNC int fcloseall() {
    return _fcloseall();
}

REDIR_FUNC char *fcvt(double value, int count, int *dec, int *sign) {
    return _fcvt(value, count, dec, sign);
}

REDIR_FUNC int fgetchar() {
    return _fgetchar();
}

REDIR_FUNC FILE *fdopen(int fd, const char *mode) {
    return _fdopen(fd, mode);
}

REDIR_FUNC long filelength(int fd) {
    return _filelength(fd);
}

REDIR_FUNC int fileno(FILE *stream) {
    return _fileno(stream);
}

REDIR_FUNC int flushall() {
    return _flushall();
}

REDIR_FUNC void fpreset() {
    _fpreset();
}

REDIR_FUNC int fputchar(int c) {
    return _fputchar(c);
}

REDIR_FUNC int fstat(int fd, struct _stat *buffer) {
    return _fstat(fd, buffer);
}

REDIR_FUNC void ftime(struct __timeb32 *timeptr) {
    return _ftime32(timeptr);
}

REDIR_FUNC char *gcvt(double value, int digits, char *buffer) {
    return _gcvt(value, digits, buffer);
}

REDIR_FUNC int getch() {
    return _getch();
}

REDIR_FUNC int getche() {
    return _getche();
}

REDIR_FUNC char *getcwd(char *buffer, int maxlen) {
    return _getcwd(buffer, maxlen);
}

REDIR_FUNC int getpid() {
    return _getpid();
}

REDIR_FUNC int getw(FILE *stream) {
    return _getw(stream);
}

REDIR_FUNC int isatty(int fd) {
    return _isatty(fd);
}

REDIR_FUNC char *itoa(int value, char *buffer, int radix) {
    return _itoa(value, buffer, radix);
}

REDIR_FUNC double j0(double x) {
    return _j0(x);
}

REDIR_FUNC double j1(double x) {
    return _j1(x);
}

REDIR_FUNC double jn(int n, double x) {
    return _jn(n, x);
}

REDIR_FUNC int locking(int fd, int mode, long nbytes) {
    return _locking(fd, mode, nbytes);
}

REDIR_FUNC void *lsearch(const void *key, void *base, unsigned int *num, unsigned int width, int(__cdecl *compare)(const void *, const void *)) {
    return _lsearch(key, base, num, width, compare);
}

REDIR_FUNC long lseek(int fd, long offset, int origin) {
    return _lseek(fd, offset, origin);
}

REDIR_FUNC char *ltoa(long value, char *buffer, int radix) {
    return _ltoa(value, buffer, radix);
}

REDIR_FUNC int kbhit() {
    return _kbhit();
}

REDIR_FUNC void *memccpy(void *dest, const void *src, int c, size_t count) {
    return _memccpy(dest, src, c, count);
}

REDIR_FUNC int memicmp(const void *buffer1, const void *buffer2, size_t count) {
    return _memicmp(buffer1, buffer2, count);
}

REDIR_FUNC int mkdir(const char *dirname) {
    return _mkdir(dirname);
}

REDIR_FUNC char *mktemp(char *nameTemplate) {
    return _mktemp(nameTemplate);
}

REDIR_FUNC int open(const char *filename, int oflag, int pmode) {
    return _open(filename, oflag, pmode);
}

REDIR_FUNC _onexit_t onexit(_onexit_t function) {
    return _onexit(function);
}

REDIR_FUNC int putch(int c) {
    return _putch(c);
}

REDIR_FUNC int putenv(const char *envstring) {
    return _putenv(envstring);
}

REDIR_FUNC int putw(int binint, FILE *stream) {
    return _putw(binint, stream);
}

REDIR_FUNC int read(int const fd, void *const buffer, unsigned const buffer_size) {
    return _read(fd, buffer, buffer_size);
}

REDIR_FUNC int rmdir(const char *dirname) {
    return _rmdir(dirname);
}

REDIR_FUNC int rmtmp() {
    return _rmtmp();
}

REDIR_FUNC int setmode(int fd, int mode) {
    return _setmode(fd, mode);
}

REDIR_FUNC int sopen(const char *filename, int oflag, int shflag, int pmode) {
    return _sopen(filename, oflag, shflag, pmode);
}

REDIR_FUNC intptr_t spawnl(int mode, const char *cmdname, ...) {
    va_list arglist;
    va_start(arglist, cmdname);

    int argc = countArgs(arglist);
    const char **argv = (const char **) _alloca((argc + 1) * sizeof(char *));
    copyArgs(argv, argc, &arglist);

    return _spawnv(mode, cmdname, argv);
}

REDIR_FUNC intptr_t spawnlp(int mode, const char *cmdname, ...) {
    va_list arglist;
    va_start(arglist, cmdname);

    int argc = countArgs(arglist);
    const char **argv = (const char **) _alloca((argc + 1) * sizeof(char *));
    copyArgs(argv, argc, &arglist);

    return _spawnvp(mode, cmdname, argv);
}

REDIR_FUNC intptr_t spawnle(int mode, const char *cmdname, ...) {
    va_list arglist;
    va_start(arglist, cmdname);

    int argc = countArgs(arglist);
    const char **argv = (const char **) _alloca((argc + 1) * sizeof(char *));
    copyArgs(argv, argc, &arglist);

    const char *const *envp = va_arg(arglist, const char *const *);

    return _spawnve(mode, cmdname, argv, envp);
}

REDIR_FUNC intptr_t spawnlpe(int mode, const char *cmdname, ...) {
    va_list arglist;
    va_start(arglist, cmdname);

    int argc = countArgs(arglist);
    const char **argv = (const char **) _alloca((argc + 1) * sizeof(char *));
    copyArgs(argv, argc, &arglist);

    const char *const *envp = va_arg(arglist, const char *const *);

    return _spawnvpe(mode, cmdname, argv, envp);
}

REDIR_FUNC intptr_t spawnv(int mode, const char *cmdname, const char *const *argv) {
    return _spawnv(mode, cmdname, argv);
}

REDIR_FUNC intptr_t spawnve(int mode, const char *cmdname, const char *const *argv, const char *const *envp) {
    return _spawnve(mode, cmdname, argv, envp);
}

REDIR_FUNC intptr_t spawnvp(int mode, const char *cmdname, const char *const *argv) {
    return _spawnvp(mode, cmdname, argv);
}

REDIR_FUNC intptr_t spawnvpe(int mode, const char *cmdname, const char *const *argv, const char *const *envp) {
    return _spawnvpe(mode, cmdname, argv, envp);
}

REDIR_FUNC int stat(const char *path, struct _stat32 *buffer) {
    return _stat32(path, buffer);
}

REDIR_FUNC int strcmpi(const char *string1, const char *string2) {
    return _strcmpi(string1, string2);
}

REDIR_FUNC char *strdup(const char *strSource) {
    return _strdup(strSource);
}

REDIR_FUNC int stricmp(const char *string1, const char *string2) {
    return _stricmp(string1, string2);
}

REDIR_FUNC char *strlwr(char *str) {
    return _strlwr(str);
}

REDIR_FUNC int strnicmp(const char *string1, const char *string2, size_t count) {
    return _strnicmp(string1, string2, count);
}

REDIR_FUNC char *strnset(char *str, int c, size_t count) {
    return _strnset(str, c, count);
}

REDIR_FUNC char *strrev(char *str) {
    return _strrev(str);
}

REDIR_FUNC char *strset(char *str, int c) {
    return _strset(str, c);
}

REDIR_FUNC char *strupr(char *str) {
    return _strlwr(str);
}

REDIR_FUNC void swab(char *src, char *dest, int n) {
    _swab(src, dest, n);
}

REDIR_FUNC long tell(int handle) {
    return _tell(handle);
}

REDIR_FUNC char *tempnam(const char *dir, const char *prefix) {
    return _tempnam(dir, prefix);
}

REDIR_FUNC void tzset() {
    _tzset();
}

REDIR_FUNC char *ultoa(unsigned long value, char *buffer, int radix) {
    return _ultoa(value, buffer, radix);
}

REDIR_FUNC int umask(int pmode) {
    return _umask(pmode);
}

REDIR_FUNC int ungetch(int c) {
    return _ungetch(c);
}

REDIR_FUNC int unlink(const char *filename) {
    return _unlink(filename);
}

REDIR_FUNC int utime(const char *filename, struct __utimbuf32 *times) {
    return _utime32(filename, times);
}

REDIR_FUNC wchar_t *wcsdup(const wchar_t *strSource) {
    return _wcsdup(strSource);
}

REDIR_FUNC int wcsicmp(const wchar_t *string1, const wchar_t *string2) {
    return _wcsicmp(string1, string2);
}

REDIR_FUNC wchar_t *wcslwr(wchar_t *str) {
    return _wcslwr(str);
}

REDIR_FUNC int wcsnicmp(const wchar_t *string1, const wchar_t *string2, size_t count) {
    return _wcsnicmp(string1, string2, count);
}

REDIR_FUNC int wcsicoll(const wchar_t *string1, const wchar_t *string2) {
    return _wcsicoll(string1, string2);
}

REDIR_FUNC wchar_t *wcsnset(wchar_t *str, wchar_t c, size_t count) {
    return _wcsnset(str, c, count);
}

REDIR_FUNC wchar_t *wcsrev(wchar_t *str) {
    return _wcsrev(str);
}

REDIR_FUNC wchar_t *wcsset(wchar_t *str, wchar_t c) {
    return _wcsset(str, c);
}

REDIR_FUNC wchar_t *wcsupr(wchar_t *str) {
    return _wcsupr(str);
}

REDIR_FUNC int write(int fd, const void *buffer, unsigned int count) {
    return _write(fd, buffer, count);
}

REDIR_FUNC double y0(double x) {
    return _y0(x);
}

REDIR_FUNC double y1(double x) {
    return _y1(x);
}

REDIR_FUNC double yn(int n, double x) {
    return _yn(n, x);
}
