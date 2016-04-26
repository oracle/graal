#define LOOP_COUNT 10000

enum {
  A,
  B,
  C,
  D,
  E,
  F,
  G,
  H,
  I,
  J,
  K,
  L,
  M,
  N
};

int main() {
  int i;
  int sum = 0;
  for (i = 0; i < LOOP_COUNT; i++) {
    switch (i % (N + 1)) {
    case A:
      sum += 1;
      break;
    case B:
      sum += 4;
      break;
    case C:
      sum += 3;
      break;
    case D:
      sum += 5;
      break;
    case E:
      sum += 3;
      break;
    case F:
      sum += 2;
      break;
    case G:
      sum += 2;
      break;
    case H:
      sum += 2;
      break;
    case I:
      sum += 4;
      break;
    case J:
      sum += 1;
      break;
    case K:
      sum += 2;
      break;
    case L:
      sum += 1;
      break;
    case M:
      sum += 4;
      break;
    case N:
      sum += 2;
      break;
    }
  }
  if (sum != 25717) {
    abort();
  }
  return 0;
}
