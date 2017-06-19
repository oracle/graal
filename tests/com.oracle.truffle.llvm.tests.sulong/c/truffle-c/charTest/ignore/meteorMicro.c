char arr[5][5][5];
int i[2][2] = { { 1, 2 }, { 3, 4 } };

char foo() {
  arr[1][1][i[1][1]] = 'a';
  return arr[1][1][i[1][1]];
}

int main() { return foo(); }
