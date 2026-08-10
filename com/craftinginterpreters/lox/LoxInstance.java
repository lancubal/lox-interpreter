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
      LoxFunction method = klass.findMethod(name.lexeme);
      if (method != null) {
        LoxFunction bound = method.bind(this);
        if (method.isGetter && interpreter != null) {
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
