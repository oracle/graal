enum NUMBERS { ONE, TWO, THREE, FOUR, FIVE };

enum NUMBERS *add(enum NUMBERS *first, enum NUMBERS *second) {
  static int result;
  result = (int)*first + (int)*second;
  return (enum NUMBERS *)&result;
}

int main() {
  int a = 3, b = 4, c = 2;
  enum NUMBERS *nr3;
  nr3 = (enum NUMBERS *)&a;
  enum NUMBERS *nr4;
  nr4 = (enum NUMBERS *)&b;
  enum NUMBERS *nr2;
  nr2 = (enum NUMBERS *)&c;
  enum NUMBERS *n = add(nr3, nr4);
  return (int)(ONE + *add(nr2, n) + (int)FIVE);
}
