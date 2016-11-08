int main() {
  double arr1[] = { -1, 4, -3 };
  char arr2[] = {(char)-1, (char)5, (char)-3 };

  int sum = 0;
  int i = 0;
  for (; i < 3; i++) {
    sum += (int)arr1[i];
    sum += arr2[i];
  }
  return sum;
}
