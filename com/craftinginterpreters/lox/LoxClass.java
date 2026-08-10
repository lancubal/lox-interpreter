package com.craftinginterpreters.lox;

import java.util.List;
import java.util.Map;

class LoxClass extends LoxInstance implements LoxCallable {
  final String name;
  private final Map<String, LoxFunction> methods;
  final LoxClass superclass;

  LoxClass(LoxClass metaclass, String name, LoxClass superclass, Map<String, LoxFunction> methods) {
    super(metaclass);
    this.superclass = superclass;
    this.name = name;
    this.methods = methods;
  }

  Map<String, LoxFunction> getMethods() {
    return methods;
  }

  static class ClassMethodPair {
    final LoxClass klass;
    final LoxFunction method;

    ClassMethodPair(LoxClass klass, LoxFunction method) {
      this.klass = klass;
      this.method = method;
    }
  }

  ClassMethodPair findTopMethod(String name) {
    List<LoxClass> chain = new java.util.ArrayList<>();
    LoxClass curr = this;
    while (curr != null) {
      chain.add(0, curr); // Prepend to get top-to-bottom order
      curr = curr.superclass;
    }

    for (LoxClass klass : chain) {
      if (klass.methods.containsKey(name)) {
        return new ClassMethodPair(klass, klass.methods.get(name));
      }
    }
    return null;
  }

  LoxClass findNextSubclassMethod(LoxClass currentClass, String name) {
    List<LoxClass> chain = new java.util.ArrayList<>();
    LoxClass curr = this;
    while (curr != null) {
      chain.add(0, curr);
      curr = curr.superclass;
    }

    int index = chain.indexOf(currentClass);
    if (index < 0) return null;

    for (int i = index + 1; i < chain.size(); i++) {
      if (chain.get(i).methods.containsKey(name)) {
        return chain.get(i);
      }
    }
    return null;
  }

  LoxFunction findMethod(String name) {
    if (methods.containsKey(name)) {
      return methods.get(name);
    }
    if (superclass != null) {
      return superclass.findMethod(name);
    }
    return null;
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public Object call(Interpreter interpreter, List<Object> arguments) {
    LoxInstance instance = new LoxInstance(this);
    LoxFunction initializer = findMethod("init");
    if (initializer != null) {
      initializer.bind(instance).call(interpreter, arguments);
    }
    return instance;
  }

  @Override
  public int arity() {
    LoxFunction initializer = findMethod("init");
    if (initializer == null) return 0;
    return initializer.arity();
  }
}
