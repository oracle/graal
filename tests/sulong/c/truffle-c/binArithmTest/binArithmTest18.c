int a = 5;
int *pa = &a;

int main() {
  *pa = *pa + 1;
  return *pa;
}
