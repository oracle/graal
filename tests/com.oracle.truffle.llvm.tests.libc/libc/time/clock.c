#include <time.h>
#include <assert.h>

int var;

void func() { var++; }

int main() {
  clock_t start = clock();
  for (int i = 0; i < 1000000; i++) {
    func();
  }
  clock_t end = clock();
  long time = (end - start) / CLOCKS_PER_SEC;
  assert(time >= 0 && time < 100);
}
