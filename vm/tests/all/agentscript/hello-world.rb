class Welcome
    def initialize(hi)
      @h = hi
    end

    def welcome(w)
      text = @h + " " + w + "!"
      return text
    end
end


msg = Welcome.new("Hello")
puts(msg.welcome("World"));
