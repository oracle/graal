void function(char t[4]) { t[3] = 0; }

int sum(char t[4]) {
  int i;
  int sum = 0;
  // + '\0'
  for (i = 0; i < 5; i++) {
    sum += t[i];
  }
  return sum;
}

int main() {
  char t[] = "asdf";
  function(t);
  return sum(t) % 256;
}
