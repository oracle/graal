#include <stdexcept>
#include <iostream>

void throwException() {
  throw std::runtime_error("thrown from C++");
}

int main() {
  try {
    throwException();
  } catch (std::runtime_error &ex) {
    std::cout << "Caught from C++" << std::endl;
  }
  return 0;
}
