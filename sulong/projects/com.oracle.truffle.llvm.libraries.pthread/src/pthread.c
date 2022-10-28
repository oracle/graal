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

#include <stdlib.h>
#include <stdint.h>
#include <process.h>
#include <windows.h>


#define PTHREAD_EXPORT __declspec(dllexport)


typedef void *(*pthread_function_t)(void * arg);

typedef struct {
  HANDLE handle;
  pthread_function_t fn;
  void * arg;
  void * retval;
} pthread_function_call_t;

typedef pthread_function_call_t * pthread_t;
typedef void * pthread_key_t;
typedef void * pthread_attr_t;


__declspec(thread) pthread_function_call_t * __sulong_pthread_self;


__stdcall unsigned pthread_start_function(void * data) {
  pthread_function_call_t * call = (pthread_function_call_t*)data;
  __sulong_pthread_self = call;
  call->retval = call->fn(call->arg);
  return 0;
}

PTHREAD_EXPORT int pthread_create(pthread_t *thread, const pthread_attr_t *attr, pthread_function_t start_routine, void *arg) {
  pthread_function_call_t * call = (pthread_function_call_t*)malloc(sizeof(pthread_function_call_t));
  call->fn = start_routine;
  call->arg = arg;
  call->handle = (pthread_t)_beginthreadex(NULL, 0, &pthread_start_function, call, 0, NULL);
  *thread = call;

  return call->handle ? 0 : GetLastError();
}

PTHREAD_EXPORT int pthread_join(pthread_t thread, void **retval) {
  HANDLE hThread = thread->handle;
  if (WaitForSingleObjectEx(hThread, INFINITE, FALSE) == WAIT_FAILED ||
      !CloseHandle(hThread)) {
    return GetLastError();
  }
  *retval = thread->retval;
  free(thread);
  return 0;
}

PTHREAD_EXPORT pthread_t pthread_self() {
  return __sulong_pthread_self;
}

PTHREAD_EXPORT int pthread_key_create(pthread_key_t * key, void (*destructor)(void*)) {
  // TODO: implement
  return -1;
}

PTHREAD_EXPORT int pthread_setspecific(pthread_key_t key, const void * value) {
  // TODO: implement
  return -1;
}

PTHREAD_EXPORT void* pthread_getspecific(pthread_key_t key) {
  // TODO: implement
  return NULL;
}

wchar_t * utf8ToWideChar(const char * name) {
  int length = MultiByteToWideChar(CP_UTF8, MB_PRECOMPOSED, name, -1, NULL, 0);
  wchar_t * wName = (wchar_t*)malloc(length * sizeof(wchar_t));
  MultiByteToWideChar(CP_UTF8, MB_PRECOMPOSED, name, -1, wName, length);
  return wName;
}

PTHREAD_EXPORT int pthread_setname_np(pthread_t thread, const char * name) {
  wchar_t * wName = utf8ToWideChar(name);
  HRESULT hr = SetThreadDescription(thread->handle, wName);
  free(wName);
  return FAILED(hr) ? hr : 0;
}

PTHREAD_EXPORT int pthread_getname_np(pthread_t thread, char * name, size_t len) {
  wchar_t * wName;
  HRESULT hr = GetThreadDescription(thread->handle, &wName);
  if (!FAILED(hr)) {
    int copied = WideCharToMultiByte(CP_UTF8, 0, wName, -1, name, len, NULL, NULL);
    return copied > 0 ? 0 : ERANGE;
  }
  return FAILED(hr) ? hr : 0;
}
