int main() {
  unsigned long test = -1L >> 1;
  switch (test) {
  case(char)-1:
    return 1;
  case((unsigned long)-1L >> 1) :
    return 2;
  default:
    return 3;
  }
}
