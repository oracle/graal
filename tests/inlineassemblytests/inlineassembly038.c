int main() {
  int out;
  __asm__("xorl %%eax, %%eax; movb $1, %%ah; movb %%ah, %%al; xorb %%ah, %%ah;" : "=a"(out));
  return out;
}
