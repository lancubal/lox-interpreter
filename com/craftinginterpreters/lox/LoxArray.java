package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

class LoxArray extends LoxInstance {
  final List<Object> elements;

  LoxArray(List<Object> elements) {
    super(null);
    this.elements = elements;
  }

  Object get(int index, Token token) {
    if (index < 0 || index >= elements.size()) {
      throw new RuntimeError(token, "Array index out of bounds: " + index + " for length " + elements.size());
    }
    return elements.get(index);
  }

  void set(int index, Object value, Token token) {
    if (index < 0 || index >= elements.size()) {
      throw new RuntimeError(token, "Array index out of bounds: " + index + " for length " + elements.size());
    }
    elements.set(index, value);
  }

  @Override
  Object get(Token name, Interpreter interpreter) {
    if (name.lexeme.equals("length") || name.lexeme.equals("len")) {
      return (double) elements.size();
    }
    if (name.lexeme.equals("add") || name.lexeme.equals("push")) {
      return new LoxCallable() {
        @Override
        public int arity() {
          return 1;
        }

        @Override
        public Object call(Interpreter interpreter, List<Object> arguments) {
          elements.add(arguments.get(0));
          return null;
        }

        @Override
        public String toString() {
          return "<native fn add>";
        }
      };
    }
    return super.get(name, interpreter);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("[");
    for (int i = 0; i < elements.size(); i++) {
      if (i > 0) builder.append(", ");
      Object elem = elements.get(i);
      if (elem == null) {
        builder.append("nil");
      } else {
        builder.append(elem.toString());
      }
    }
    builder.append("]");
    return builder.toString();
  }
}
