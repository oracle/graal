#include <getopt.h>
#include <stdio.h>
#include <assert.h>

int main() {
  assert(optind == 1);
  assert(opterr == 1);
  assert(optopt == '?');
  assert(optarg == NULL);
}
