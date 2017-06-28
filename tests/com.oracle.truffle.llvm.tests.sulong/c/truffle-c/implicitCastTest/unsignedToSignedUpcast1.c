extern void abort(void);
unsigned short c = 0x8000;
int main() {
  if (c - 0x8000 < 0)
    abort();
  return 0;
}
