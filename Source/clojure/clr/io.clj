;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns 
  ^{:author "David Miller",
     :doc "Shamelessly based on the clojure.java.io package authored by Stuart Sierra, Chas Emerick, Stuart Halloway.
     This file defines polymorphic I/O utility functions for Clojure."}
    clojure.clr.io
    (:import 
     (System.IO 
       Stream  BufferedStream 
       FileInfo  FileStream  MemoryStream
       FileMode FileShare FileAccess FileOptions
       BinaryReader BinaryWriter
       StreamReader StreamWriter
       StringReader StringWriter 
       TextReader TextWriter)
     (System.Net.Sockets 
       Socket NetworkStream) 
     (System.Text 
       Encoding UTF8Encoding UnicodeEncoding UTF32Encoding UTF7Encoding ASCIIEncoding Decoder Encoder)
     (System 
       Uri UriFormatException)))
     

(defprotocol ^{:added "1.2"} Coercions
  "Coerce between various 'resource-namish' things."
  (^{:tag System.IO.FileInfo, :added "1.2"} as-file [x] "Coerce argument to a file.")
  (^{:tag System.Uri, :added "1.2"} as-uri [x] "Coerce argument to a URI."))

(extend-protocol Coercions
  nil
  (as-file [_] nil)
  (as-uri [_] nil)
  
  String
  (as-file [s] (FileInfo. s))
  (as-uri [s] (Uri. s))
    
  FileInfo
  (as-file [f] f)
  (as-uri [f] (Uri. (str "file://" (.FullName f))))

  Uri
  (as-uri [u] u)
  (as-file [u] 
	(if (.IsFile u)
	  (as-file (.LocalPath u))
      (throw (ArgumentException. (str "Not a file: " u))))))

(defprotocol ^{:added "1.2"} IOFactory
  "Factory functions that create ready-to-use, buffered versions of
   the various Java I/O stream types, on top of anything that can
   be unequivocally converted to the requested kind of stream.

   Common options include
   
     :buffer-size   Ths size of buffer to use (default: 1024).
     :file-share    A value from the System.IO.FileShare enumeration.
     :file-mode     A value from the System.IO.FileMode enumeration.
     :file-access   A value from the System.IO.FileAccess enumeration.
     :file-options  A value from the System.IO.FileOptions enumeration.
     :encoding  The encoding to use, either as a string, e.g. \"UTF-8\",
                a keyword, e.g. :utf-8, or a an System.Text.Encoding instance,
                e.g., (System.Text.UTF8Encoding.)

   Callers should generally prefer the higher level API provided by
   reader, writer, input-stream, and output-stream."
  (^{:added "1.2"} make-text-reader [x opts] "Creates a TextReader.  See also IOFactory docs.")
  (^{:added "1.2"} make-text-writer [x opts] "Creates a TextWriter.  See also IOFactory docs.")
  (^{:added "1.2"} make-input-stream [x opts] "Creates a Stream in input mode.  See also IOFactory docs.")
  (^{:added "1.2"} make-output-stream [x opts] "Creates a Stream in output mode.  See also IOFactory docs.")
  (^{:added "1.2"} make-binary-reader [x opts] "Creates a BinaryReader.  See also IOFactory docs.")
  (^{:added "1.2"} make-binary-writer [x opts] "Creates a BinaryWriter.  See also IOFactory docs."))

(defn ^TextReader text-reader
  "Attempts to coerce its argument into an open System.IO.TextReader.

   Default implementations are provided for Stream, Uri, FileInfo, Socket,
   byte arrays, and String.

   If argument is a String, it tries to resolve it first as a URI, then
   as a local file name.  URIs with a 'file' protocol are converted to
   local file names.

   Should be used inside with-open to ensure the TextReader is properly
   closed."
  {:added "1.2"}
  [x & opts]
  (make-text-reader x (when opts (apply hash-map opts))))

(defn ^TextWriter text-writer 
  "Attempts to coerce its argument into an open System.IO.TextWriter.

   Default implementations are provided for Stream, Uri, FileInfo, Socket, 
   and String.

   If the argument is a String, it tries to resolve it first as a URI, then
   as a local file name.  URIs with a 'file' protocol are converted to
   local file names.

   Should be used inside with-open to ensure the TestWriter is properly
   closed."
  {:added "1.2"}
  [x & opts]
  (make-text-writer x (when opts (apply hash-map opts))))

(defn ^Stream input-stream
  "Attempts to coerce its argument into an open System.IO.Stream
   in input mode.

   Default implementations are defined for Stream, FileInfo, Uri,
   Socket, byte array, char array, and String arguments.

   If the argument is a String, it tries to resolve it first as a URI, then
   as a local file name.  URIs with a 'file' protocol are converted to
   local file names.

   Should be used inside with-open to ensure the Stream is properly
   closed."
  {:added "1.2"}
  [x & opts]
  (make-input-stream x (when opts (apply hash-map opts))))

(defn ^Stream output-stream
  "Attempts to coerce its argument into an open System.IO.Stream.

   Default implementations are defined for Stream, FileInfo, URI,
   Socket, and String arguments.

   If the argument is a String, it tries to resolve it first as a URI, then
   as a local file name.  URIs with a 'file' protocol are converted to
   local file names.

   Should be used inside with-open to ensure the OutputStream is
   properly closed."
  {:added "1.2"}
  [x & opts]
  (make-output-stream x (when opts (apply hash-map opts))))
  
  
(defn ^BinaryReader binary-reader
  "Attempt to coerce its argument into an open System.IO.BinaryReader.
  
  Default implementations are defined for Stream, FileInfo, URI, Socket,
  byte array, and String arguments.
  
   If the argument is a String, it tries to resolve it first as a URI, then
   as a local file name.  URIs with a 'file' protocol are converted to
   local file names.
   
   Should be used inside with-open to ensure the BinaryReader is
   properly closed."
  {:added "1.2"}
  [x & opts]
  (make-binary-reader x (when opts (apply hash-map opts))))
  
(defn ^BinaryWriter binary-writer
  "Attempt to coerce its argument into an open System.IO.BinaryWriter.
  
  Default implementations are defined for Stream, FileInfo, URI, Socket,
  and String arguments.
  
   If the argument is a String, it tries to resolve it first as a URI, then
   as a local file name.  URIs with a 'file' protocol are converted to
   local file names.
   
   Should be used inside with-open to ensure the BinaryWriter is
   properly closed."
  {:added "1.2"}
  [x & opts]
  (make-binary-writer x (when opts (apply hash-map opts))))  
  
(def string->encoding
  { "UTF-8" (UTF8Encoding.)
    "UTF-16" (UnicodeEncoding.)
    "UTF-32" (UTF32Encoding.)
    "UTF-7" (UTF7Encoding.)
    "ascii" (ASCIIEncoding.)
    "ASCII" (ASCIIEncoding.)
    "us-ascii" (ASCIIEncoding.)
    :utf8 (UTF8Encoding.)
    :utf16 (UnicodeEncoding.)
    :utf32 (UTF32Encoding.)
    :utf7 (UTF7Encoding.)
    :ascii (ASCIIEncoding.)
    :utf-8 (UTF8Encoding.)
    :utf-16 (UnicodeEncoding.)
    :utf-32 (UTF32Encoding.)
    :utf-7 (UTF7Encoding.)   
    })
   
(defn- normalize-encoding [key]
   (if (string? key)
       (get string->encoding key)
       key))   
   
(defn- ^Encoding encoding [opts]
  (or (normalize-encoding (:encoding opts)) (get string->encoding "UTF-8")))

(defn- buffer-size [opts]
  (or (:buffer-size opts) 1024))
  
(defn- ^FileMode file-mode [mode opts]
  (or (:file-mode opts)
      (if (= mode :read)
          FileMode/Open
          FileMode/OpenOrCreate)))
  
(defn- ^FileShare file-share [opts]
  (or (:file-share opts) FileShare/None))
  
(defn- ^FileAccess file-access [mode opts]
  (or (:file-access opts) 
       (if (= mode :read)
         FileAccess/Read 
         FileAccess/Write)))
  
(defn- ^FileOptions file-options [opts]
  (or (:file-options opts) FileOptions/None))  
  
  
(def default-streams-impl
  {:make-text-reader (fn [x opts] (make-text-reader (make-input-stream x opts) opts))
   :make-text-writer (fn [x opts] (make-text-writer (make-output-stream x opts) opts))
   :make-binary-reader (fn [x opts] (make-binary-reader (make-input-stream x opts) opts))
   :make-binary-writer (fn [x opts] (make-binary-writer (make-output-stream x opts) opts))
   :make-input-stream (fn [x opts]
                        (throw (ArgumentException.
                                (str "Cannot open <" (pr-str x) "> as an input Stream."))))
   :make-output-stream (fn [x opts]
                         (throw (ArgumentException. 
                                 (str "Cannot open <" (pr-str x) "> as an output Stream."))))})

(extend Stream
  IOFactory
  (assoc default-streams-impl
    :make-text-reader (fn [^Stream x opts] (StreamReader. x (encoding opts)))
    :make-text-writer (fn [^Stream x opts] (StreamWriter. x (encoding opts)))
    :make-binary-reader (fn [^Stream x opts] (BinaryReader. x (encoding opts)))
    :make-binary-writer (fn [^Stream x opts] (BinaryWriter. x (encoding opts)))
    :make-input-stream (fn [^Stream x opts] (if (.CanRead x) x (throw (ArgumentException. "Cannot convert non-reading stream to input stream"))))
    :make-output-stream (fn [^Stream x opts] (if (.CanWrite x) x (throw (ArgumentException. "Cannot convert non-reading stream to input stream"))))))

(extend BinaryReader
  IOFactory
  (assoc default-streams-impl
    :make-binary-reader (fn [x opts] x)
    :make-input-stream (fn [^BinaryReader x opts] (.BaseStream x))
    :make-output-stream (fn [^BinaryReader x opts] (make-output-stream (.BaseStream x) opts))))
    
(extend BinaryWriter
  IOFactory
  (assoc default-streams-impl
    :make-binary-writer (fn [x opts] x)
    :make-input-stream (fn [^BinaryWriter x opts] (make-input-stream (.BaseStream x) opts))
    :make-output-stream (fn [^BinaryWriter x opts] (.BaseStream x))))
    
(extend StreamReader
  IOFactory
  (assoc default-streams-impl
    :make-text-reader (fn [x opts] x)
    :make-input-stream (fn [^StreamReader x opts] (.BaseStream x))
    :make-output-stream (fn [^StreamReader x opts] (make-output-stream (.BaseStream x) opts))))
    
(extend StreamWriter
  IOFactory
  (assoc default-streams-impl
    :make-text-writer (fn [x opts] x)
    :make-input-stream (fn [^StreamWriter x opts] (make-input-stream (.BaseStream x) opts))
    :make-output-stream (fn [^StreamWriter x opts] (.BaseStream x))))
    
(extend StringReader
  IOFactory
  (assoc default-streams-impl
    :make-text-reader (fn [x opts] x)))
    
(extend StringWriter
  IOFactory
  (assoc default-streams-impl
    :make-text-writer (fn [x opts] x)))

(extend FileInfo
  IOFactory
  (assoc default-streams-impl
    :make-input-stream (fn [^FileInfo x opts] 
      (make-input-stream 
        (FileStream. (.FullName x)
                     (file-mode :read opts) 
                     (file-access :read opts) 
                     (file-share opts) 
                     (buffer-size opts) 
                     (file-options opts)) 
         opts))
    :make-output-stream (fn [^FileInfo x opts] 
      (make-output-stream 
        (FileStream. (.FullName x)
                     (file-mode :write opts) 
                     (file-access :write opts) 
                     (file-share opts) 
                     (buffer-size opts) 
                     (file-options opts)) 
         opts))))
        
(extend String
  IOFactory
  (assoc default-streams-impl
    :make-input-stream (fn [^String x opts]
                         (try
                          (make-input-stream (Uri. x) opts)
                          (catch UriFormatException err
                            (make-input-stream (FileInfo. x) opts))))
    :make-output-stream (fn [^String x opts]
                          (try
                           (make-output-stream (Uri. x) opts)
                           (catch UriFormatException err
                             (make-output-stream (FileInfo. x) opts))))))
(extend Socket
  IOFactory
  (assoc default-streams-impl
    :make-input-stream (fn [^Socket x opts] (NetworkStream. x (file-access :read opts)))
    :make-output-stream (fn [^Socket x opts] (NetworkStream. x (file-access :write opts)))))


(extend Uri
  IOFactory
  (assoc default-streams-impl
    :make-input-stream (fn [^Uri x opts]
                         (if (.IsFile x)
                           (make-input-stream (FileInfo. (.LocalPath x)) opts)
                           (throw (ArgumentException. (str "No easy way to get an input stream for a non-file URI <" x ">")))))
    :make-output-stream (fn [^Uri x opts]
                         (if (.IsFile x)
                           (make-output-stream (FileInfo. (.LocalPath x)) opts)
                           (throw (ArgumentException. (str "No easy way to get an output stream for a non-file URI <" x ">")))))))


(extend |System.Byte[]|
  IOFactory
  (assoc default-streams-impl
    :make-input-stream (fn [^|System.Byte[]| x opts] (MemoryStream. x))))

(extend Object
  IOFactory
  default-streams-impl)

(defmulti
  ^{:doc "Internal helper for copy"
     :private true
     :arglists '([input output opts])}
  do-copy
  (fn [input output opts] [(type input) (type output)]))

(defmethod do-copy [Stream Stream] [^Stream input ^Stream output opts]
  (let [ len (buffer-size opts)
         ^bytes buffer (make-array Byte len)]
    (loop []
      (let [size (.Read input buffer 0 len)]
        (when (pos? size)
          (do (.Write output buffer 0 size)
              (recur)))))))

(defmethod do-copy [Stream TextWriter] [^Stream input ^TextWriter output opts]
  (let [ len (buffer-size opts) 
         ^bytes  buffer (make-array Byte len)
         ^Decoder decoder (.GetDecoder (encoding opts)) ]   
    (loop []
      (let [size (.Read input buffer 0 len)]
        (when (pos? size)
          (let [ cnt (.GetCharCount decoder buffer 0 size)
                 ^|System.Char[]| chbuf (make-array Char cnt)]
            (do (.GetChars decoder buffer 0 (int size) chbuf 0)
                (.Write output chbuf 0 cnt)
                (recur))))))))

(defmethod do-copy [Stream FileInfo] [^Stream input ^FileInfo output opts]
  (with-open [out (make-output-stream output opts)]
    (do-copy input out opts)))

(defmethod do-copy [TextReader Stream] [^TextReader input ^Stream output opts]
  (let [ len (buffer-size opts)
         ^chars buffer (make-array Char len)
         ^Encoder encoder (.GetEncoder (encoding opts))]
    (loop []
      (let [size (.Read input buffer 0 len)]
        (when (pos? size)
          (let [cnt (.GetByteCount encoder buffer 0 size false)
                bytes (make-array Byte cnt)]
            (do (.GetBytes encoder buffer 0 size bytes 0 false)
                (.Write output bytes 0 cnt)
                (recur))))))))

(defmethod do-copy [TextReader TextWriter] [^TextReader input ^TextWriter output opts]
  (let [ len (buffer-size opts)
         ^chars buffer (make-array Char len)]
    (loop []
      (let [size (.Read input buffer 0 len)]
        (when (pos? size)
          (do (.Write output buffer 0 size)
              (recur)))))))

(defmethod do-copy [TextReader FileInfo] [^TextReader input ^FileInfo output opts]
  (with-open [out (make-output-stream output opts)]
    (do-copy input out opts)))

(defmethod do-copy [FileInfo Stream] [^FileInfo input ^Stream output opts]
  (with-open [in (make-input-stream input opts)]
    (do-copy in output opts)))

(defmethod do-copy [FileInfo TextWriter] [^FileInfo input ^TextWriter output opts]
  (with-open [in (make-input-stream input opts)]
    (do-copy in output opts)))

(defmethod do-copy [FileInfo FileInfo] [^FileInfo input ^FileInfo output opts]
  (with-open [in (make-input-stream input opts)
              out (make-output-stream output opts)]
    (do-copy in out opts)))

(defmethod do-copy [String Stream] [^String input ^Stream output opts]
  (do-copy (StringReader. input) output opts))

(defmethod do-copy [String TextWriter] [^String input ^TextWriter output opts]
  (do-copy (StringReader. input) output opts))

(defmethod do-copy [String FileInfo] [^String input ^FileInfo output opts]
  (do-copy (StringReader. input) output opts))

(defmethod do-copy [|System.Byte[]| Stream] [^bytes input ^Stream output opts]
  (do-copy (MemoryStream. input) output opts))

(defmethod do-copy [|System.Byte[]| TextWriter] [^bytes input ^TextWriter output opts]
  (do-copy (MemoryStream. input) output opts))

(defmethod do-copy [|System.Byte[]| FileInfo] [^bytes input ^FileInfo output opts]
  (do-copy (MemoryStream. input) output opts))

(defn copy
  "Copies input to output.  Returns nil or throws IOException.
  Input may be an InputStream, Reader, File, byte[], or String.
  Output may be an OutputStream, Writer, or File.

  Options are key/value pairs and may be one of

    :buffer-size  buffer size to use, default is 1024.
    :encoding     encoding to use if converting between
                  byte and char streams.   

  Does not close any streams except those it opens itself 
  (on a File)."
  {:added "1.2"}
  [input output & opts]
  (do-copy input output (when opts (apply hash-map opts))))
