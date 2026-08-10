package com.craftinginterpreters.lox;

class LoxInstance {
  LoxClass klass;
  private final Map<String, Object> fields = new HashMap<>();

  LoxInstance(LoxClass klass) {
    this.klass = klass;
  }

  Object get(Token name, Interpreter interpreter) {
    if (fields.containsKey(name.lexeme)) {
      return fields.get(name.lexeme);
    }

    if (klass != null) {
      LoxClass.ClassMethodPair pair = klass.findTopMethod(name.lexeme);
      if (pair != null) {
        LoxFunction bound = pair.method.bind(this, pair.klass, name.lexeme);
        if (pair.method.isGetter && interpreter != null) {
          return bound.call(interpreter, new ArrayList<>());
        }
        return bound;
      }
    }

    throw new RuntimeError(name, "Undefined property '" + name.lexeme + "'.");
  }

  Object get(Token name) {
    return get(name, null);
  }

  void set(Token name, Object value) {
    fields.put(name.lexeme, value);
  }

  @Override
  public String toString() {
    if (klass == null) return "Object instance";
    return klass.name + " instance";
  }
}
