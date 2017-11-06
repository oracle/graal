#include <time.h>
#include <sys/time.h>
#include <stdio.h>
#include <stdlib.h>

struct timeval begin, end;

int main() {
  if (gettimeofday(&begin, (struct timezone *)0)) {
    fprintf(stderr, "can not get time\n");
    exit(1);
  }
  for (volatile int i = 0; i < 10000000; i++)
    ;
  if (gettimeofday(&end, (struct timezone *)0)) {
    fprintf(stderr, "can not get time\n");
    exit(1);
  }
}
