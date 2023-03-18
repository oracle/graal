/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
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
 
#include <windows.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <errno.h>
#include <fcntl.h>
#include <io.h>
#include <stdio.h>
#include <stdbool.h> 

#define NEW_C_HEAP_ARRAY(type, size, memflags) malloc((size) * sizeof(type))
#define FREE_C_HEAP_ARRAY(type, old) free(old)
#define MAX2(a, b) ((a > b) ? a : b)

static errno_t convert_to_unicode(char const* char_path, LPWSTR* unicode_path) {
  // Get required buffer size to convert to Unicode
  int unicode_path_len = MultiByteToWideChar(CP_ACP,
                                             MB_ERR_INVALID_CHARS,
                                             char_path, -1,
                                             NULL, 0);
  if (unicode_path_len == 0) {
    return EINVAL;
  }

  *unicode_path = NEW_C_HEAP_ARRAY(WCHAR, unicode_path_len, mtInternal);
  if (*unicode_path == NULL) {
    return ENOMEM;
  }

  int result = MultiByteToWideChar(CP_ACP,
                                   MB_ERR_INVALID_CHARS,
                                   char_path, -1,
                                   *unicode_path, unicode_path_len);

  return ERROR_SUCCESS;
}

static errno_t get_full_path(LPCWSTR unicode_path, LPWSTR* full_path) {
  // Get required buffer size to convert to full path. The return
  // value INCLUDES the terminating null character.
  DWORD full_path_len = GetFullPathNameW(unicode_path, 0, NULL, NULL);
  if (full_path_len == 0) {
    return EINVAL;
  }

  *full_path = NEW_C_HEAP_ARRAY(WCHAR, full_path_len, mtInternal);
  if (*full_path == NULL) {
    return ENOMEM;
  }

  // When the buffer has sufficient size, the return value EXCLUDES the
  // terminating null character
  DWORD result = GetFullPathNameW(unicode_path, full_path_len, *full_path, NULL);

  return ERROR_SUCCESS;
}

static void set_path_prefix(char* buf, LPWSTR* prefix, int* prefix_off, bool* needs_fullpath) {
  *prefix_off = 0;
  *needs_fullpath = true;

  if (isalpha(buf[0]) && !IsDBCSLeadByte(buf[0]) && buf[1] == ':' && buf[2] == '\\') {
    *prefix = L"\\\\?\\";
  } else if (buf[0] == '\\' && buf[1] == '\\') {
    if (buf[2] == '?' && buf[3] == '\\') {
      *prefix = L"";
      *needs_fullpath = false;
    } else {
      *prefix = L"\\\\?\\UNC";
      *prefix_off = 1; // Overwrite the first char with the prefix, so \\share\path becomes \\?\UNC\share\path
    }
  } else {
    *prefix = L"\\\\?\\";
  }
}

// Convert a pathname to native format.  On win32, this involves forcing all
// separators to be '\\' rather than '/' (both are legal inputs, but Win95
// sometimes rejects '/') and removing redundant separators.  The input path is
// assumed to have been converted into the character encoding used by the local
// system.  Because this might be a double-byte encoding, care is taken to
// treat double-byte lead characters correctly.
//
// This procedure modifies the given path in place, as the result is never
// longer than the original.  There is no error return; this operation always
// succeeds.
static char * native_path(char *path) {
  char *src = path, *dst = path, *end = path;
  char *colon = NULL;  // If a drive specifier is found, this will
                       // point to the colon following the drive letter

  // Check for leading separators
#define isfilesep(c) ((c) == '/' || (c) == '\\')
  while (isfilesep(*src)) {
    src++;
  }

  if (isalpha(*src) && !IsDBCSLeadByte(*src) && src[1] == ':') {
    // Remove leading separators if followed by drive specifier.  This
    // hack is necessary to support file URLs containing drive
    // specifiers (e.g., "file://c:/path").  As a side effect,
    // "/c:/path" can be used as an alternative to "c:/path".
    *dst++ = *src++;
    colon = dst;
    *dst++ = ':';
    src++;
  } else {
    src = path;
    if (isfilesep(src[0]) && isfilesep(src[1])) {
      // UNC pathname: Retain first separator; leave src pointed at
      // second separator so that further separators will be collapsed
      // into the second separator.  The result will be a pathname
      // beginning with "\\\\" followed (most likely) by a host name.
      src = dst = path + 1;
      path[0] = '\\';     // Force first separator to '\\'
    }
  }

  end = dst;

  // Remove redundant separators from remainder of path, forcing all
  // separators to be '\\' rather than '/'. Also, single byte space
  // characters are removed from the end of the path because those
  // are not legal ending characters on this operating system.
  //
  while (*src != '\0') {
    if (isfilesep(*src)) {
      *dst++ = '\\'; src++;
      while (isfilesep(*src)) src++;
      if (*src == '\0') {
        // Check for trailing separator
        end = dst;
        if (colon == dst - 2) break;  // "z:\\"
        if (dst == path + 1) break;   // "\\"
        if (dst == path + 2 && isfilesep(path[0])) {
          // "\\\\" is not collapsed to "\\" because "\\\\" marks the
          // beginning of a UNC pathname.  Even though it is not, by
          // itself, a valid UNC pathname, we leave it as is in order
          // to be consistent with the path canonicalizer as well
          // as the win32 APIs, which treat this case as an invalid
          // UNC pathname rather than as an alias for the root
          // directory of the current drive.
          break;
        }
        end = --dst;  // Path does not denote a root directory, so
                      // remove trailing separator
        break;
      }
      end = dst;
    } else {
      if (IsDBCSLeadByte(*src)) {  // Copy a double-byte character
        *dst++ = *src++;
        if (*src) *dst++ = *src++;
        end = dst;
      } else {  // Copy a single-byte character
        char c = *src++;
        *dst++ = c;
        // Space is not a legal ending character
        if (c != ' ') end = dst;
      }
    }
  }

  *end = '\0';

  // For "z:", add "." to work around a bug in the C runtime library
  if (colon == dst - 1) {
    path[2] = '.';
    path[3] = '\0';
  }

  return path;
}

// Returns the given path as an absolute wide path in unc format. The returned path is NULL
// on error (with err being set accordingly) and should be freed via free() otherwise.
// additional_space is the size of space, in wchar_t, the function will additionally add to
// the allocation of return buffer (such that the size of the returned buffer is at least
// wcslen(buf) + 1 + additional_space).
static wchar_t* wide_abs_unc_path(char const* path, errno_t* err, int additional_space) {
  if ((path == NULL) || (path[0] == '\0')) {
    *err = ENOENT;
    return NULL;
  }

  // Need to allocate at least room for 3 characters, since native_path transforms C: to C:.
  size_t buf_len = 1 + MAX2((size_t)3, strlen(path));
  char* buf = NEW_C_HEAP_ARRAY(char, buf_len, mtInternal);
  if (buf == NULL) {
    *err = ENOMEM;
    return NULL;
  }
  
  strncpy(buf, path, buf_len);
  native_path(buf);

  LPWSTR prefix = NULL;
  int prefix_off = 0;
  bool needs_fullpath = true;
  set_path_prefix(buf, &prefix, &prefix_off, &needs_fullpath);

  LPWSTR unicode_path = NULL;
  *err = convert_to_unicode(buf, &unicode_path);
  FREE_C_HEAP_ARRAY(char, buf);
  if (*err != ERROR_SUCCESS) {
    return NULL;
  }

  LPWSTR converted_path = NULL;
  if (needs_fullpath) {
    *err = get_full_path(unicode_path, &converted_path);
  } else {
    converted_path = unicode_path;
  }

  LPWSTR result = NULL;
  if (converted_path != NULL) {
    size_t prefix_len = wcslen(prefix);
    size_t result_len = prefix_len - prefix_off + wcslen(converted_path) + additional_space + 1;
    result = NEW_C_HEAP_ARRAY(WCHAR, result_len, mtInternal);
    if (result != NULL) {
      _snwprintf(result, result_len, L"%s%s", prefix, &converted_path[prefix_off]);

      // Remove trailing pathsep (not for \\?\<DRIVE>:\, since it would make it relative)
      result_len = wcslen(result);
      if ((result[result_len - 1] == L'\\') &&
          !(iswalpha(result[4]) && result[5] == L':' && result_len == 7)) {
        result[result_len - 1] = L'\0';
      }
    }
  }

  if (converted_path != unicode_path) {
    FREE_C_HEAP_ARRAY(WCHAR, converted_path);
  }
  FREE_C_HEAP_ARRAY(WCHAR, unicode_path);

  return (wchar_t*) result; // LPWSTR and wchat_t* are the same type on Windows.
}

int iohelper_open_file(const char *path, int oflag, int mode) {
  errno_t err;
  wchar_t* wide_path = wide_abs_unc_path(path, &err, 0);

  if (wide_path == NULL) {
    errno = err;
    return -1;
  }
  int fd = _wopen(wide_path, oflag | O_NOINHERIT, mode);
  free(wide_path);

  if (fd == -1) {
    errno = GetLastError();
  }

  return fd;
}
