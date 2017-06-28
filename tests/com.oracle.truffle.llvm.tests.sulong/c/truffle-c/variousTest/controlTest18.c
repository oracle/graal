enum Flag {
  NO_ERRORS = 1,
  IO_ERROR = 2,
  OTHER_ERROR = 3
};

int main() {
  enum Flag f = IO_ERROR;
  switch (f) {
  case NO_ERRORS:
    return 4;
  case IO_ERROR:
    return 5;
  case OTHER_ERROR:
    return 6;
  }
}
