#include <stdlib.h>
#include <stdio.h>
#include <ctype.h>

int main() {
  if (!isgraph('a')) {
    abort();
  }
  if (!isgraph('z')) {
    abort();
  }
  if (!isgraph('A')) {
    abort();
  }
  if (!isgraph('Z')) {
    abort();
  }
  if (isgraph(' ')) {
    abort();
  }
  if (!isgraph('5')) {
    abort();
  }
  if (!isgraph('!')) {
    abort();
  }
  if (!isgraph('@')) {
    abort();
  }
  if (!isgraph('[')) {
    abort();
  }
  if (isgraph('\t')) {
    abort();
  }
  if (isgraph('\n')) {
    abort();
  }

  if (isgraph(0x08)) {
    abort();
  }
  if (isgraph(0x00)) {
    abort();
  }
  if (isgraph(0x09)) {
    abort();
  }
  if (isgraph(0x1f)) {
    abort();
  }
  if (!isgraph(';')) {
    abort();
  }
  if (isgraph(0x7f)) {
    abort();
  }
}
