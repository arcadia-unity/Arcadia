;   Copyright (c) David Miller. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


;;;  A Celsius/Fahrenheit converter
;;;  A WinForms equivalent to the Swing app shown here:  http://clojure.org/jvm_hosted

; If you are running on .Net 4, you will have to change this.
(System.Reflection.Assembly/Load "System.Windows.Forms,Version=2.0.0.0, Culture=neutral, PublicKeyToken=b77a5c561934e089") 

(import 
  '(System.Drawing Size)
  '(System.Windows.Forms 
       Form TableLayoutPanel Label Button TextBox
       PaintEventHandler PaintEventArgs)      
 ) 
    
  
 (defn celsius []
   (let [form (Form.)
         panel (TableLayoutPanel.)
         p-controls (.Controls panel)
         tb (TextBox.)
         c-label (Label.)
         button (Button.)
         f-label (Label.)]
         
      (.set_Text form "Celsius Converter")
      (.set_Text c-label "Celsius")
      (.set_Text f-label "Fahrenheit")
      (.set_Text button "Convert")
      
      (.. form (Controls) (Add panel))
     
	  (.add_Click button 
	     (gen-delegate EventHandler [sender args]
	        (let [c  (Double/Parse (.Text tb)) ]
	          (.set_Text f-label (str (+ 32 (* 1.8 c)) " Fahrenheit")))))
	                        
      (doto panel
         (.set_ColumnCount 2)
         (.set_RowCount 2))
         
      (doto  p-controls
         (.Add tb)
         (.Add c-label)
         (.Add button)
         (.Add f-label))
          
      (doto form
        (.set_Size (Size. 300 120))
        .ShowDialog)))
      
      
 (celsius)