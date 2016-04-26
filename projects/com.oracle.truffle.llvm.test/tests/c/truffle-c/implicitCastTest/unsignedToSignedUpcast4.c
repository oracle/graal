extern void abort(void);
unsigned long c = 0x8000000000000000;
double t = 0x8000000000000000;

int main() {
  if (c - t < 0)
    abort();
  return 0;
}
