#include <stdlib.h>
#include <stdio.h>
#include <ctype.h>

int main() {
  if (iscntrl('a')) {
    abort();
  }
  if (iscntrl('z')) {
    abort();
  }
  if (iscntrl('A')) {
    abort();
  }
  if (iscntrl('Z')) {
    abort();
  }
  if (iscntrl(' ')) {
    abort();
  }
  if (iscntrl('5')) {
    abort();
  }
  if (iscntrl('!')) {
    abort();
  }
  if (iscntrl('@')) {
    abort();
  }
  if (iscntrl('[')) {
    abort();
  }
  if (iscntrl(' ')) {
    abort();
  }
  if (!iscntrl(0x1f)) {
    abort();
  }
  if (!iscntrl(0x00)) {
    abort();
  }
  if (!iscntrl(0x7f)) {
    abort();
  }
}
