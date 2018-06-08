#include <limits>

int start() __attribute__((constructor))
{
    long double a = 1.23L;
    long double b = -4.56L;
    long double c = a - b;
    long double d = 5553.6547;
    long double e = 0;
    long double f = std::numeric_limits<long double>::quiet_NaN();
    long double g = std::numeric_limits<long double>::signaling_NaN();
    long double h = std::numeric_limits<long double>::infinity();
    return 0;
}
