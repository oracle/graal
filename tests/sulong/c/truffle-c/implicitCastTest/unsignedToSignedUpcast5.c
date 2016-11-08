extern void abort(void);
unsigned int c = 0x80000000;
float t = 0x80000000;

int main() {
  if (c - t < 0)
    abort();
  return 0;
}
