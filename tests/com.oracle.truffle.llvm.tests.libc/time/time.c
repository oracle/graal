#include <time.h>
#include <stdlib.h>

int main() {
  long firstTimeStamp = time(NULL);
  for (int i = 0; i < 100000; i++) {
    time(NULL);
  }
  long lastTimeStamp = time(NULL);
  if (lastTimeStamp < firstTimeStamp) {
    abort();
  }
}
