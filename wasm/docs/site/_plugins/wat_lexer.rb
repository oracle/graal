# Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

require 'rouge'

# overly simplistic wat lexer
module Rouge
  module Lexers
    class Wat < RegexLexer
      tag 'wat'
      filenames '*.wat'
      title "wat"
      desc "WebAssembly Text Format"

      state :root do
        rule %r/;;.*$/, Comment::Single
        rule %r/\(;.*?;\)/m, Comment::Multiline

        # literals
        rule %r/"([^\\"]|\\.)*?"/, Str
        rule %r/[-+]?(?:\d+|0x[0-9a-fA-F]+)/, Num::Integer
        rule %r/[-+]?\d+\.\d+(?:[eE][-+]?\d+)?/, Num::Float

        # keywords
        rule %r/\b(
          module|func|type|param|result|local|global|memory|data|table|elem|start|import|export|mut
        )(?!\.)\b/x, Keyword

        # types
        rule %r/\b(
          i32|i64|f32|f64|v128|funcref|externref
        )(?!\.)\b/x, Keyword::Type

        # instructions
        rule %r/\b(
          local\.(?:get|set|tee)|
          global\.(?:get|set)|
          (?:i32|i64|f32|f64|v128|i8x16|i16x8|i32x4|i64x2|f32x4|f64x2)\.[a-z0-9_]+|
          nop|unreachable|
          block|loop|if|else|end|
          br|br_if|br_table|
          return|
          call|call_indirect|
          drop|select|
          table\.(?:get|set|size|grow|fill|copy|init)|
          elem\.drop|
          memory\.(?:size|grow|fill|copy|init)
          data\.drop|
          ref\.(?:null|is_null|func)
        )(?!\.)\b/x, Name::Attribute

        # names
        rule %r/\$[a-zA-Z0-9._-]*/, Name::Builtin

        # attributes
        rule %r/(?:offset|align)=/, Name::Property

        rule %r/[()]/, Punctuation

        rule %r/\s+/, Text
      end
    end
  end
end
