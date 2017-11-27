#define LOOP_COUNT 10000

enum { A, B, C, D, E, F, G, H, I, J, K, L, M, N };

int main() {
  int i;
  int sum = 0;
  for (i = 0; i < LOOP_COUNT; i++) {
    switch (i % (N + 1)) {
    case A:
      sum += 1;
    case B:
      sum += 4;
    case C:
      sum += 3;
    case D:
      sum += 5;
    case E:
      sum += 3;
    case F:
      sum += 2;
    case G:
      sum += 2;
    case H:
      sum += 2;
    case I:
      sum += 4;
    case J:
      sum += 1;
    case K:
      sum += 2;
    case L:
      sum += 1;
    case M:
      sum += 4;
    case N:
      sum += 2;
    }
  }
  if (sum != 182200) {
    abort();
  }
  return 0;
}
