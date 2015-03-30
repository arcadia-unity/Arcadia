(ns arcadia.internal.meval)

(defmacro meval
  "Evaluates arguments in macro phase and injects result at macro
  site. Useful for manipulating code evaluation order without having
  to define named macros."
  [& stuff]
  (eval (cons 'do stuff)))

(defmacro defn-meval
  "Same as defn, except last form in preamble-and-meval-to-forms is
  evaluated during macro expansion, and the results spliced as code
  into the tail of the function definition. Useful for many things,
  such as automating the generation of specific arities for a function
  that would otherwise be inefficiently variadic."
  [& preamble-and-meval-to-forms]
  (let [preamble (butlast preamble-and-meval-to-forms)
        meval-to-forms (last preamble-and-meval-to-forms)
        mevaluated (eval meval-to-forms)]
    `(defn ~@preamble
       ~@mevaluated)))
