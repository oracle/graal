extern void abort(void);
unsigned int c = 0x80000000;
int main() {
  if (c - 0x80000000L < 0)
    abort();
  return 0;
}
