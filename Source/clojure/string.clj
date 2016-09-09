;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{:doc "Clojure String utilities

It is poor form to (:use clojure.string). Instead, use require
with :as to specify a prefix, e.g.

(ns your.namespace.here
  (:require [clojure.string :as str]))

Design notes for clojure.string:

1. Strings are objects (as opposed to sequences). As such, the
   string being manipulated is the first argument to a function;
   passing nil will result in a NullPointerException unless
   documented otherwise. If you want sequence-y behavior instead,
   use a sequence.

2. Functions are generally not lazy, and call straight to host
   methods where those are available and efficient.

3. Functions take advantage of String implementation details to
   write high-performing loop/recurs instead of using higher-order
   functions. (This is not idiomatic in general-purpose application
   code.)

4. When a function is documented to accept a string argument, it
   will take any implementation of the correct *interface* on the
   host platform. In Java, this is CharSequence, which is more
   general than String. In ordinary usage you will almost always
   pass concrete strings. If you are doing something unusual,
   e.g. passing a mutable implementation of CharSequence, then
   thread-safety is your responsibility."
      :author "Stuart Sierra, Stuart Halloway, David Liebke"}
  clojure.string
  (:refer-clojure :exclude (replace reverse))
  (:import (System.Text.RegularExpressions Regex MatchEvaluator Match)              ; java.util.regex Pattern
           clojure.lang.LazilyPersistentVector))
(declare re-groups-direct)                                    ;;; I'm going to add a little helper
(defn ^String reverse
  "Returns s with its characters reversed."
  {:added "1.2"}
  [^String s]
  (clojure.lang.RT/StringReverse s))                           ;;; (.toString (.reverse (StringBuilder. s))))

(defn ^String re-quote-replacement
  "Given a replacement string that you wish to be a literal
   replacement for a pattern match in replace or replace-first, do the
   necessary escaping of special characters in the replacement."
  {:added "1.5"}
  [^String replacement]                                           ;;; ^CharSequence
  replacement)                                                    ;;; TODO:  a no-op until I figure out the CLR equivalent -- (Matcher/quoteReplacement (.toString ^CharSequence replacement)))

(defn- replace-by
  [^String s ^Regex re f]
  (let [^MatchEvaluator me (gen-delegate MatchEvaluator [m] (f (re-groups-direct m)))]
    (.Replace re s me)))                                                         ;;; (let [m (re-matcher re s)]
                                                                              ;;;    (if (.find m)
                                                                              ;;;      (let [buffer (StringBuffer. (.length s))]
                                                                              ;;;        (loop [found true]
                                                                              ;;;           (if found
                                                                              ;;;            (do (.appendReplacement m buffer (Matcher/quoteReplacement (f (re-groups m))))
                                                                              ;;;                (recur (.find m)))
                                                                              ;;;            (do (.appendTail m buffer)
                                                                              ;;;                (.toString buffer)))))
                                                                              ;;;      s)))

(defn replace
  "Replaces all instance of match with replacement in s.

   match/replacement can be:

   string / string
   char / char
   pattern / (string or function of match).
   
   See also replace-first.

   The replacement is literal (i.e. none of its characters are treated
   specially) for all cases above except pattern / string.

   For pattern / string, $1, $2, etc. in the replacement string are
   substituted with the string that matched the corresponding
   parenthesized group in the pattern.  If you wish your replacement
   string r to be used literally, use (re-quote-replacement r) as the
   replacement argument.  See also documentation for
   java.util.regex.Matcher's appendReplacement method.

   Example:
   (clojure.string/replace \"Almost Pig Latin\" #\"\\b(\\w)(\\w+)\\b\" \"$2$1ay\")
   -> \"lmostAay igPay atinLay\""
  {:added "1.2"}
  [^String s ^Regex match replacement]
  (let []   ;                                                                                  ;;; [s (.toString s)]
    (cond 
     (instance? Char match) (.Replace s ^Char match ^Char replacement)                         ;;;  Character  .replace
     (instance? String match) (.Replace s ^String match ^String replacement)                   ;;; .replace
     (instance? Regex match) (if (string? replacement)                                         ;;; Pattern
                               (.Replace match s ^String replacement)                                  ;;; (.replaceAll (re-matcher ^Pattern match s)
							                                                                   ;;;     (.toString ^CharSequence replacement))
                               (replace-by s match replacement))
     :else (throw (ArgumentException. (str "Invalid match arg: " match))))))                   ;;; IllegalArgumentException

(defn- replace-first-by
  [^String s ^Regex re f]                                                       ;;; Pattern
                                                                                ;;; (let [m (re-matcher re s)]
  (let [^MatchEvaluator me (gen-delegate MatchEvaluator [m] (f (re-groups-direct m)))]
    (.Replace re s me 1)))
                                                                              ;;;   (if (.find m)
                                                                              ;;;     (let [buffer (StringBuffer. (.length s))
                                                                              ;;;           rep (Matcher/quoteReplacement (f (re-groups m)))]
                                                                                ;;;        (.appendReplacement m buffer rep)
                                                                                ;;;        (.appendTail m buffer)
                                                                                ;;;        (str buffer)))
                                                                                ;;;     s)))

(defn- replace-first-char
  [^String s  match replace] (let [match ^Char (char match)]                                   ;;; Character hint on match
  (let [                                                          ;;; s (.toString s)
        i (.IndexOf s match)]                                 ;;; .indexOf (int match)
    (if (= -1 i)
      s
      (str (subs s 0 i) replace (subs s (inc i))))))  )
      
(defn- replace-first-str
  [^String s ^String match ^String replace]                               ;;; ^CharSequence
  (let [                                                                  ;;; ^String s (.toString s)
        i (.IndexOf s match)]                                             ;;; .indexOf
    (if (= -1 i)
      s
      (str (subs s 0 i) replace (subs s (+ i (.Length match)))))))         ;;; .length

(defn replace-first
  "Replaces the first instance of match with replacement in s.

   match/replacement can be:

   char / char
   string / string
   pattern / (string or function of match).

   See also replace.

   The replacement is literal (i.e. none of its characters are treated
   specially) for all cases above except pattern / string.

   For pattern / string, $1, $2, etc. in the replacement string are
   substituted with the string that matched the corresponding
   parenthesized group in the pattern.  If you wish your replacement
   string r to be used literally, use (re-quote-replacement r) as the
   replacement argument.  See also documentation for
   java.util.regex.Matcher's appendReplacement method.

   Example:
   (clojure.string/replace-first \"swap first two words\"
                                 #\"(\\w+)(\\s+)(\\w+)\" \"$3$2$1\")
   -> \"first swap two words\""

  {:added "1.2"}
  [^String s match replacement]
  ;;;(let [s (.toString s)]
    (cond
     (instance? Char match)                                                         ;;; Character
     (replace-first-char s ^Char match replacement)
     (instance? String match)                                                       ;;; CharSequence
     (replace-first-str s match                                                     ;;; (.toString ^CharSequence match)
	                    replacement)                                                ;;; (.toString ^CharSequence replacement)
     (instance? Regex match)                                                        ;;; Pattern
      (if (string? replacement)
       (.Replace ^Regex match s ^String replacement 1)                              ;;; (.replaceFirst (re-matcher ^Pattern match s) ^String replacement)
       (replace-first-by s match replacement))
   :else (throw (ArgumentException. (str "Invalid match arg: " match)))))           ;;; IllegalArgumentException


(defn ^String join
  "Returns a string of all elements in coll, as returned by (seq coll),
  separated by  an optional separator."  
  {:added "1.2"}
  ([coll]
   (str
     (reduce (fn
               ([sb] sb)
               ([^StringBuilder sb s]
                (.Append sb (str s))
                sb))
       (StringBuilder.)
       coll)))
  ([sep coll]
   (let [sep (str sep)]
     (str
       (reduce (fn
                 ([sb] sb)
                 ([^StringBuilder sb s]
                  (if (nil? sb)
                    (StringBuilder. (str s))
                    (do (.Append sb sep)
                        (.Append sb (str s))
                        sb))))
         nil
         coll)))))

(defn ^String capitalize
  "Converts first character of the string to upper-case, all other
  characters to lower-case."
  {:added "1.2"}
  [^String s]                                                           ;;; ^CharSequence
  (let []                                                               ;;; [s (.toString s)]
    (if (< (count s) 2)
      (.ToUpper s)                                                      ;;; .toUpperCase
      (str (.ToUpper ^String (subs s 0 1))                              ;;; .toUpperCase
           (.ToLower ^String (subs s 1))))))                            ;;; .toLowerCase

(defn ^String upper-case
  "Converts string to all upper-case."
  {:added "1.2"}
  [^String s]
  (.ToUpper s))                               ;;; .toUpperCase

(defn ^String lower-case
  "Converts string to all lower-case."
  {:added "1.2"}
  [^String s]
  (.ToLower s))                               ;;; .toLowerCase

(defn split
  "Splits string on a regular expression.  Optional argument limit is
  the maximum number of splits. Not lazy. Returns vector of the splits."
  {:added "1.2"}
  ([^String s ^Regex re]                                                   ;;; ^Pattern 
     (LazilyPersistentVector/createOwning (.Split re s)))                  ;;; .split
  ([^String s ^Regex re limit]                                             ;;; ^Pattern 
     (LazilyPersistentVector/createOwning (.Split re s limit))))           ;;; .split
 
(defn split-lines
  "Splits s on \\n or \\r\\n."
  {:added "1.2"}
  [^String s]
  (split s #"\r?\n"))

(defn ^String trim
  "Removes whitespace from both ends of string."
  {:added "1.2"}
  [^String s]                                                              ;;; ^CharSequence
  (let [len (.Length s)]                                                   ;;; .length
    (loop [rindex len]                                                       
      (if (zero? rindex)
        ""
        (if (Char/IsWhiteSpace (.get_Chars s (dec rindex)))                ;;; Character/isWhitespace   .charAt 
          (recur (dec rindex))
          ;; there is at least one non-whitespace char in the string,
          ;; so no need to check for lindex reaching len.
          (loop [lindex 0]
            (if (Char/IsWhiteSpace (.get_Chars s lindex))                  ;;; Character/isWhitespace   .charAt 
              (recur (inc lindex))
              (.. s (Substring lindex (- rindex lindex))))))))))           ;;;  (subSequence lindex rindex) toSTring

(defn ^String triml
  "Removes whitespace from the left side of string."
  {:added "1.2"}
  [^String s]                                                              ;;; ^CharSequence           
  (let [len (.Length s)]                                                   ;;; .length
    (loop [index 0]
      (if (= len index)
        ""
        (if (Char/IsWhiteSpace (.get_Chars s index))                       ;;; Character/isWhitespace   .charAt 
          (recur (unchecked-inc index))
          (.. s (Substring index)))))))                                    ;;;  (subSequence index len)  toSTring

(defn ^String trimr
  "Removes whitespace from the right side of string."
  {:added "1.2"}
  [^String s]                                                              ;;; ^CharSequence  
  (loop [index (.Length s)]                                                ;;; .length
    (if (zero? index)
      ""
      (if (Char/IsWhiteSpace (.get_Chars s (unchecked-dec index)))         ;;; Character/isWhitespace   .charAt 
        (recur (unchecked-dec index))
        (.. s (Substring 0 index))))))                     ;;;  (subSequence 0 index)  toSTring

(defn ^String trim-newline
  "Removes all trailing newline \\n or return \\r characters from
  string.  Similar to Perl's chomp."
  {:added "1.2"}
  [^String s]
  (loop [index (.Length s)]                                  ;;; .length
    (if (zero? index)
      ""
      (let [ch (.get_Chars s (dec index))]                        ;;; .charAt
        (if (or (= ch \newline) (= ch \return))
          (recur (dec index))
          (.Substring s 0 index))))))                           ;;;  .substring

(defn blank?
  "True if s is nil, empty, or contains only whitespace."
  {:added "1.2"}
  [^String s]                                                      ;;; CharSequence
  (if s
    (loop [index (int 0)]
      (if (= (.Length s) index)                                       ;;; .length
        true
        (if (Char/IsWhiteSpace (.get_Chars s index))                ;;; Character/isWhitespace  .charAt
          (recur (inc index))
          false)))
    true))

(defn ^String escape
  "Return a new string, using cmap to escape each character ch
   from s as follows:
   
   If (cmap ch) is nil, append ch to the new string.
   If (cmap ch) is non-nil, append (str (cmap ch)) instead."
  {:added "1.2"}
  [^String s cmap]                                                              ;;; CharSequence
  (loop [index (int 0)
         buffer (StringBuilder. (.Length s))]                                   ;;; .length
    (if (= (.Length s) index)                                                   ;;; .length
      (.ToString buffer)                                                        ;;; .toString 
      (let [ch (.get_Chars s index)]                                                ;;; .charAt
        (if-let [replacement (cmap ch)]
          (.Append buffer replacement)                                          ;;; .append
          (.Append buffer ch))                                                  ;;; .append
        (recur (inc index) buffer)))))


(defn- re-groups-direct
  "similar to re-groups, but works on a Match directly, rather than JReMatcher"
  [^Match m]
  (let [strs (map #(.Value %) (.Groups ^Match m))
        cnt (count strs)]
	 (if (<= cnt 1) 
	   (first strs)
	   (into [] strs))))
	    
