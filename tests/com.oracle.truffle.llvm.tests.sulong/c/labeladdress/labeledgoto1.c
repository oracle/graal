
int main() {
  void *arr[] = { &&label1, &&label2, &&label3, &&label4, &&label5 };
  int i;
label1:
  i = 2;
label2:
  i = 4;
label3:
  goto *arr[i];
label4:
  i = 3;
label5:
  return i;
}
