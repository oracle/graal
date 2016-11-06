int s1 = 1;
int s2 = 1;
int s3 = 1;
int s4 = 1;
int s5 = 1;
int s6 = 1;
int s7 = 1;
int s8 = 1;
int s9 = 1;

int main() {
  int *ps1 = &s1;
  int *ps2 = &s2;
  int *ps3 = &s3;
  int *ps4 = &s4;
  int *ps5 = &s5;
  int *ps6 = &s6;
  int *ps7 = &s7;
  int *ps8 = &s8;
  int *ps9 = &s9;

  return *ps1 + *ps2 + *ps3 + *ps4 + *ps5 + *ps6 + *ps7 + *ps8 + *ps9;
}
