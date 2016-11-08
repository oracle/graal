
struct test {
  int a;
  int *p;
};

int main() {
  struct test *pStruct;
  struct test str;
  str.a = 5;
  str.p = &str.a;
  pStruct = &str;
  return *(pStruct->p);
}
