#include <stdlib.h>
#include <stdio.h>
#include <ctype.h>

int main() {
  if (!isprint('a')) {
    abort();
  }
  if (!isprint('z')) {
    abort();
  }
  if (!isprint('A')) {
    abort();
  }
  if (!isprint('Z')) {
    abort();
  }
  if (!isprint(' ')) {
    abort();
  }
  if (!isprint('5')) {
    abort();
  }
  if (!isprint('!')) {
    abort();
  }
  if (!isprint('@')) {
    abort();
  }
  if (!isprint('[')) {
    abort();
  }
  if (isprint('\t')) {
    abort();
  }
  if (isprint(0x08)) {
    abort();
  }
  if (isprint(0x00)) {
    abort();
  }
  if (isprint(0x09)) {
    abort();
  }
  if (isprint(0x1f)) {
    abort();
  }
  if (!isprint(';')) {
    abort();
  }
  if (isprint(0x7f)) {
    abort();
  }
}
