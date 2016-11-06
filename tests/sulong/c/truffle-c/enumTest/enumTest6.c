enum NUMBERS {
  ONE,
  TWO,
  THREE,
  FOUR,
  FIVE
};

int add(enum NUMBERS first, enum NUMBERS second) { return first + second; }

int main() { return ONE + add(TWO, (enum NUMBERS)add(THREE, FOUR)) + (int)FIVE; }
