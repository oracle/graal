struct test {
  int a;
  int *p;
  int **pp;
};

struct test2 {
  struct test *p;
};

int main() {

  struct test str1;
  struct test2 str2;
  str1.a = 23;
  str2.p = &str1;

  str2.p->pp = &(str2.p->p);
  str1.p = &str1.a;
  return **(str2.p->pp);
}
