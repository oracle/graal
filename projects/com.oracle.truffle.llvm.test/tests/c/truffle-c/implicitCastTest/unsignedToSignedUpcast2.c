extern void abort(void);
unsigned char c = 0x80;
int main() {
  if (c - 0x80 < 0)
    abort();
  return 0;
}
