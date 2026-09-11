/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#ifdef _WIN32

#include <windows.h>
#include <wchar.h>

static HANDLE create_read_only_file_handle(const wchar_t *file) {
    /* Log files can already be open for writing when their identities are compared. */
    return CreateFileW(file, 0, FILE_SHARE_READ | FILE_SHARE_WRITE, NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
}

int SVM_same_files(const void *raw_file1, const void *raw_file2) {
    const wchar_t *file1 = (const wchar_t *) raw_file1;
    const wchar_t *file2 = (const wchar_t *) raw_file2;
    if (file1 == NULL && file2 == NULL) {
        return 1;
    }
    if (file1 == NULL || file2 == NULL) {
        return 0;
    }
    if (wcscmp(file1, file2) == 0) {
        return 1;
    }

    HANDLE handle1 = create_read_only_file_handle(file1);
    HANDLE handle2 = create_read_only_file_handle(file2);
    int result = 0;
    if (handle1 != INVALID_HANDLE_VALUE && handle2 != INVALID_HANDLE_VALUE) {
        BY_HANDLE_FILE_INFORMATION file_info1;
        BY_HANDLE_FILE_INFORMATION file_info2;
        if (GetFileInformationByHandle(handle1, &file_info1) && GetFileInformationByHandle(handle2, &file_info2)) {
            result = file_info1.dwVolumeSerialNumber == file_info2.dwVolumeSerialNumber &&
                            file_info1.nFileIndexHigh == file_info2.nFileIndexHigh &&
                            file_info1.nFileIndexLow == file_info2.nFileIndexLow;
        }
    }
    if (handle1 != INVALID_HANDLE_VALUE) {
        CloseHandle(handle1);
    }
    if (handle2 != INVALID_HANDLE_VALUE) {
        CloseHandle(handle2);
    }
    return result;
}

#else

#include <string.h>
#include <sys/stat.h>

int SVM_same_files(const void *raw_file1, const void *raw_file2) {
    const char *file1 = (const char *) raw_file1;
    const char *file2 = (const char *) raw_file2;
    if (file1 == NULL && file2 == NULL) {
        return 1;
    }
    if (file1 == NULL || file2 == NULL) {
        return 0;
    }
    if (strcmp(file1, file2) == 0) {
        return 1;
    }

    struct stat stat1;
    struct stat stat2;
    if (stat(file1, &stat1) < 0 || stat(file2, &stat2) < 0) {
        return 0;
    }
    return stat1.st_dev == stat2.st_dev && stat1.st_ino == stat2.st_ino;
}

#endif
