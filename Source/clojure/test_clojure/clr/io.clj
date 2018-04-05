;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.



(ns clojure.test-clojure.clr.io
  (:use clojure.test clojure.clr.io
        [clojure.test-helper :only [platform-newlines]])
  (:import 
    (System.IO FileInfo FileMode FileStream StreamReader StreamWriter MemoryStream Path)
    (System.Text Encoding UTF8Encoding UnicodeEncoding UTF32Encoding)
  ))

(def utf8 (UTF8Encoding.))
(def utf16 (UnicodeEncoding.))
(def utf32 (UTF32Encoding.))

(defmacro with-temp-file [[v] & body]
	`(let [~v (FileInfo. (Path/GetTempFileName))]
	   (try
	      ~@body
		  (finally (.Delete ~v)))))


(defn temp-file
  [fname]
  (let [fi (FileInfo. fname)]
    (when (.Exists fi)
       (.Delete fi))
    ;(doto (.Create fi) (.Close))
    fi))

 
(defn- get-bytes [^String s ^Encoding encoding]
  (let [cs (.ToCharArray s)
        enc (.GetEncoder encoding)
        cnt (.GetByteCount enc cs 0 (.Length cs) true)
        bs (make-array Byte cnt)]
    (.GetBytes enc cs 0 (.Length cs) bs 0 true)
    bs))
    
(deftest test-spit-and-slurp
  (let [content (apply str (concat "a" (repeat 500 "\u226a\ud83d\ude03")))]
    (with-temp-file [f]
	  (testing "spit/slurp with default encoding"
        (spit f content)
        (is (= content (slurp f :encoding utf8)))))	;;RJ Added :encoding utf8
    (doseq [enc [ utf8 utf16 utf32 ]]
	  (with-temp-file [f]
	    (testing (str "split/slurp with encoding " enc)
          (spit f content :encoding enc)
		  (is (= content (slurp f :encoding enc))))))
    (testing "deprecated arity"
	  (with-temp-file [f]
	    (spit f content :encoding utf16)
        (is (=
              (platform-newlines "WARNING: (slurp f enc) is deprecated, use (slurp f :encoding enc).\n")
              (with-out-str
                (is (= content (slurp f utf16))))))))))
  
(deftest test-streams-defaults
  (let [f (temp-file "test-reader-writer")
        content "testing"]
    (try
      (is (thrown? Exception (text-reader (Object.))))
      (is (thrown? Exception (text-writer (Object.))))

      (are [write-to read-from] (= content (do
                                             (spit write-to content :encoding utf8)
                                             (slurp read-from :encoding utf8)))
           f f
           (.FullName f) (.FullName f)
           (FileStream. (.FullName f) FileMode/Create) (FileStream. (.FullName f) FileMode/Open)
           (StreamWriter. (FileStream. (.FullName f) FileMode/Create) utf8) (text-reader f :encoding utf8)
           f (FileStream. (.FullName f) FileMode/Open)
           (text-writer f :encoding utf8) (StreamReader. (FileStream. (.FullName f) FileMode/Open) utf8))

      (is (= content (slurp (get-bytes content utf8) :encoding utf8)))	;;RJ Added :encoding utf8
      ;(is (= content (slurp (.ToCharArray content))))
      (finally
       (.Delete f)))))

(deftest test-streams-nil
  (is (thrown-with-msg? ArgumentException #"Cannot open.*nil" (text-reader nil)))                  ;;; IllegalArgumentException
  (is (thrown-with-msg? ArgumentException #"Cannot open.*nil" (text-writer nil)))                  ;;; IllegalArgumentException
  (is (thrown-with-msg? ArgumentException #"Cannot open.*nil" (input-stream nil)))            ;;; IllegalArgumentException
  (is (thrown-with-msg? ArgumentException #"Cannot open.*nil" (output-stream nil))))          ;;; IllegalArgumentException

(defn bytes-should-equal [byte-array-1 byte-array-2 msg]
  (is (= |System.Byte[]| (class byte-array-1) (class byte-array-2)) msg)
  (is (= (into []  byte-array-1) (into []  byte-array-2)) msg))
 
(defn data-fixture
  "in memory fixture data for tests"
  [in-encoding out-encoding]
  (let [s (apply str (concat "a" (repeat 500 "\u226a\ud83d\ude03")))
        bs (get-bytes s in-encoding)
        i (MemoryStream. bs)
        r (StreamReader. i in-encoding)
        o (MemoryStream.)
        w (StreamWriter. o out-encoding )]
    {:bs bs
     :i i
     :r r
     :o o
     :s s
     :w w}))

(deftest test-copy
  (dorun
   (for [{:keys [in out flush] :as test}
         [{:in :i :out :o}
          {:in :i :out :w}
          {:in :r :out :o}
          {:in :r :out :w}
          {:in :bs :out :o}
          {:in :bs :out :w}]
         
         opts
         [{} {:buffer-size 16} {:buffer-size 256}]]
     (let [{:keys [s o] :as d} (data-fixture utf8 utf8)]
       (apply copy (in d) (out d) (flatten (vec opts)))
       #_(when (= out :w) (.Flush (:w d)))
       (.Flush (out d))
       (bytes-should-equal (get-bytes s utf8)
                           (.ToArray o)
                           (str "combination " test opts))))))

(deftest test-copy-encodings
  (doseq [enc [ utf8 utf16 utf32 ]]
    (testing (str "from inputstream " enc " to writer UTF-8")
      (let [{:keys [i s o w bs]} (data-fixture enc utf8)]
        (copy i w :encoding enc :buffer-size 16)
        (.Flush w)
        (bytes-should-equal (get-bytes s utf8) (.ToArray o) "")))
    (testing (str "from reader UTF-8 to output-stream " enc)
      (let [{:keys [r o s]} (data-fixture utf8  enc)]
        (copy r o :encoding enc :buffer-size 16)
        (bytes-should-equal (get-bytes s enc) (.ToArray o) "")))))

;(deftest test-as-file
;  (are [result input] (= result (as-file input))
;       (File. "foo") "foo"
;       (File. "bar") (File. "bar")
;       (File. "baz") (URL. "file:baz")
;       (File. "quux") (URI. "file:quux")
;       nil nil))

;(deftest test-file
;  (are [result args] (= (File. result) (apply file args))
;       "foo" ["foo"]
;       "foo/bar" ["foo" "bar"]
;       "foo/bar/baz" ["foo" "bar" "baz"]))
;(deftest test-as-url
;  (are [file-part input] (= (URL. (str "file:" file-part)) (as-url input))
;       "foo" "file:foo"
;       "/foo" (File. "/foo")
;       "baz" (URL. "file:baz")
;       "quux" (URI. "file:quux"))
;  (is (nil? (as-url nil))))
;
;(deftest test-input-stream
;  (let [file (temp-file "test-input-stream" "txt")
;        content (apply str (concat "a" (repeat 500 "\u226a\ud83d\ude03")))
;        bytes (.getBytes content)]
;    (spit file content)
;    (doseq [[expr msg]
;            [[file File]
;             [(FileInputStream. file) FileInputStream]
;             [(BufferedInputStream. (FileInputStream. file)) BufferedInputStream]
;             [(.. file toURI) URI]
;             [(.. file toURI toURL) URL]
;             [(.. file toURI toURL toString) "URL as String"]
;             [(.. file toString) "File as String"]]]
;      (with-open [s (input-stream expr)]
;        (stream-should-have s bytes msg)))))

;(deftest test-socket-iofactory
;  (let [server-socket (ServerSocket. 0)
;        client-socket (Socket. "localhost" (.getLocalPort server-socket))]
;    (try
;      (is (instance? InputStream (input-stream client-socket)))
;      (is (instance? OutputStream (output-stream client-socket)))
;      (finally (.close server-socket)
;               (.close client-socket)))))