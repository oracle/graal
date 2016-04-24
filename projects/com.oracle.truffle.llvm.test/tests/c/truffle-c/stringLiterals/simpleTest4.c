int sum(char t[4]) {
  int i;
  int sum = 0;
  // + '\0'
  for (i = 0; i < 5; i++) {
    sum += t[i];
  }
  return sum;
}

int main() { return sum("asdf") % 256; }
