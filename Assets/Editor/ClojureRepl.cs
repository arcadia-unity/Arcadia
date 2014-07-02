using UnityEngine;
using UnityEditor;
using clojure.lang;
using System.Collections.Generic;
using System;
using System.Reflection;
using System.Reflection.Emit;

public class ClojureRepl : EditorWindow {
  string output = "Clojure REPL v0.1 (sexant)\n";
  string input = "";
  List<string> history = new List<string>();

  [MenuItem ("Window/Clojure REPL")]
  static void Init () {
    ClojureRepl window = (ClojureRepl)EditorWindow.GetWindow (typeof (ClojureRepl));
    RT.load("unityRepl");
  }
  
  void OnGUI () {
    // GUILayout.TextArea(UnityScriptLanguage, 200);

    // string namespace = "UnityEditor.Scripting.Compilers";

    /*
    if(GUILayout.Button("New Assembly")) {
      // http://stackoverflow.com/questions/14480332/create-a-class-dynamically-with-reflection-emit-i-got-stuck
      // http://stackoverflow.com/questions/3862226/dynamically-create-a-class-in-c-sharp
      AssemblyName assembly = new AssemblyName("SuperShim");
      AppDomain appDomain = AppDomain.CurrentDomain;
      AssemblyBuilder assemblyBuilder = appDomain.DefineDynamicAssembly(assembly, AssemblyBuilderAccess.RunAndSave, "/Users/nasser/Desktop");
      ModuleBuilder moduleBuilder = assemblyBuilder.DefineDynamicModule(assembly.Name);

      TypeBuilder typeBuilder = moduleBuilder.DefineType("SuperShimComponent",
          TypeAttributes.Public |
          TypeAttributes.Class |
          TypeAttributes.AutoClass |
          TypeAttributes.AnsiClass |
          TypeAttributes.BeforeFieldInit,
          typeof(UnityEngine.MonoBehaviour));

      ConstructorBuilder constructorBuilder = typeBuilder.DefineConstructor(MethodAttributes.Public, CallingConventions.Standard, null);
      ILGenerator ilGenerator = constructorBuilder.GetILGenerator();
      ilGenerator.Emit(OpCodes.Ret);

      FieldBuilder nameBuilder = typeBuilder.DefineField("name", typeof(System.String), FieldAttributes.Public);
      nameBuilder.SetConstant("");
      FieldBuilder heightInMetersBuilder = typeBuilder.DefineField("heightInMeters", typeof(System.Int32), FieldAttributes.Public);
      heightInMetersBuilder.SetConstant(14);

      typeBuilder.CreateType();

      assemblyBuilder.Save("SuperShim.dll");
      AppDomain.CurrentDomain.Load(assembly);

    }
    */

    /*
    if(GUILayout.Button("Hack!")) {
      foreach(var t in Assembly.GetAssembly(typeof(EditorWindow)).GetTypes()) {
        if(t.Name == "UnityScriptLanguage") {
          Debug.Log(t.Name);
          var usl = System.Activator.CreateInstance(t);
          Debug.Log(usl.GetType().GetMethod("GetExtensionICanCompile").Invoke(usl, null));
          Debug.Log(usl.GetType().GetMethod("GetLanguageName").Invoke(usl, null));
        }
      }
    }
    */
    //         where t.IsClass && t.Namespace == @namespace
    //         select t;
    // q.ToList().ForEach(t => output += t.Name);


    GUILayout.TextArea(output, 200);
    input = GUILayout.TextField(input, 200);

    Event e = Event.current;
    if (e.type == EventType.KeyDown && e.character == '\n') {
      var result = RT.var("unityRepl", "repl-eval-string").invoke(input);
      Debug.Log("REPL result should be: " + result);

      output += input;
      output += "\n ==> ";
      output += result;
      output += "\n";

      input = "";
      
    }
  }
}
