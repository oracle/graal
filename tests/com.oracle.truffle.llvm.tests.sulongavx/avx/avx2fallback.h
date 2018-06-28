/*
 * Helper for test cases that use AVX2 processor features. If a test case uses
 * AVX2 instructions, include this file, and implement the `avx2_fallback`
 * method. In the reference executable, this function will be used as fallback
 * when AVX2 is not supported. On Sulong, the AVX2 function will be emulated.
 */

#include <signal.h>

int avx2_fallback();

void __handle_sigill() {
  int ret = avx2_fallback();
  exit(ret);
}

__attribute__((constructor)) void __install_sigill() {
  signal(SIGILL, __handle_sigill);
}
