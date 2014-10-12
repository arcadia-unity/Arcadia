(ns import-hydrate
  (:use unity.core)
  (:import [UnityEngine Debug Profiler]))

(defcomponent ImportHydrate [^int health]
  (Start [this]
       this))

 
 
 ; (defcomponent TheSmoo [^int x ^float y])
 
; icon = (EditorGUIUtility.IconContent ("TextAsset Icon").image as Texture2D);
; IL_105:
; ProjectWindowUtil.StartNameEditingIfProjectWindowExists (0, ScriptableObject.CreateInstance<DoCreateScriptAsset> (), destName, icon, templatePath);

(import '[UnityEditor EditorGUIUtility ProjectWindowUtil]
        '[UnityEngine ScriptableObject])
(let [icon (.image (EditorGUIUtility/IconContent "TextAsset Icon"))
      dest-name "foo.clj"
      template "template.clj"]
  (ProjectWindowUtil/StartNameEditingIfProjectWindowExists
    0
    (ScriptableObject/CreateInstance (type-args DoCreateScriptAsset))))

 
 (pprint (->> AppDomain/CurrentDomain
              .GetAssemblies
              (map #(-> %
                        .FullName
                        (split #" ")
                        first))
              (remove #(re-matches #"^clojure.*" %))))

(doseq [asm ["UnityScript"]]
  (pprint
    (count (->>
      (Assembly/Load asm)
      .GetTypes
      (keep #(seq (.GetMethods % (enum-or BindingFlags/Static BindingFlags/Instance BindingFlags/NonPublic BindingFlags/Public))))
      flatten
      (filter (fn thing2 [m] (some #(instance? UnityEditor.MenuItem %)
                         (.GetCustomAttributes m false))))      
      (map #(str (.DeclaringType %) "." (.Name %)))))))
 
 (use 'clojure.pprint)
 (use 'clojure.string)
 
 (first (split "hello world" #" "))
 
 (set! *print-length* nil)