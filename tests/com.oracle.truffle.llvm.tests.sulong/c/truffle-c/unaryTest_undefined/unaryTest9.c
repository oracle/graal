int a = 1;
int b = 2;
int c = 3;

int main() { 
  // NOTE: unary semantics differ between
  // GCC and Clang
  // One unary per line hides these differences.
  int t1 = ++a;
  int t2 = --a;
  int t3 = ++b;
  int t4 = --b;
  int t5 = --c;
  int t6 = ++c;
  int t7 = c++;
  int t8 = c--;
  int t9 = c++;

  return t1 - t2 + t3 + t4 * t5 * t6 + t7 - t8 * t9; 
}
