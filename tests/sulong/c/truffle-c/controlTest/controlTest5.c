int a = 5;
int *pa = &a;

int main() {
  if (a == *pa) {
    return 5;
  } else {
    return -1;
  }
}
