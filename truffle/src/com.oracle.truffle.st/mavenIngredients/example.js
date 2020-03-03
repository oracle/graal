var N = 2000;
var EXPECTED = 17393;

function Natural() {
    x = 2;
    return {
        'next' : function() { return x++; }
    };
}

function Filter(number, filter) {
    var self = this;
    this.number = number;
    this.filter = filter;
    this.accept = function(n) {
      var filter = self;
      for (;;) {
          if (n % filter.number === 0) {
              return false;
          }
          filter = filter.filter;
          if (filter === null) {
              break;
          }
      }
      return true;
    };
    return this;
}

function Primes(natural) {
    var self = this;
    this.natural = natural;
    this.filter = null;
    this.next = function() {
        for (;;) {
            var n = self.natural.next();
            if (self.filter === null || self.filter.accept(n)) {
                self.filter = new Filter(n, self.filter);
                return n;
            }
        }
    };
}

var holdsAFunctionThatIsNeverCalled = function(natural) {
    var self = this;
    this.natural = natural;
    this.filter = null;
    this.next = function() {
        for (;;) {
            var n = self.natural.next();
            if (self.filter === null || self.filter.accept(n)) {
                self.filter = new Filter(n, self.filter);
                return n;
            }
        }
    };
}

var holdsAFunctionThatIsNeverCalledOneLine = function() {return null;}

function primesMain() {
    var primes = new Primes(Natural());
    var primArray = [];
    for (var i=0;i<=N;i++) { primArray.push(primes.next()); }
    if (primArray[N] != EXPECTED) { throw new Error('wrong prime found: ' + primArray[N]); }
}
primesMain();
