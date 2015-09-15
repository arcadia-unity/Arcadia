using System;
using System.Linq;
using System.IO;
using System.Text.RegularExpressions;

using UnityEngine;
using UnityEditor;
using clojure.lang;

public class ClojureNewFile : EditorWindow {
  // TODO read file paths from config

  private const string DefaultScriptPath = "Assets";

  [MenuItem ("Assets/Create/Clojure Component", false, 90)]
  [MenuItem ("Arcadia/New Component", false, 90)]
  public static void NewComponent () {
    CreateFromTemplate("new-component.clj", "Assets/Arcadia/Editor/new-component-template.clj.txt");
  }
  
  [MenuItem ("Arcadia/New File", false, 91)]
  public static void NewFile () {
    CreateFromTemplate("new-file.clj", "Assets/Arcadia/Editor/new-file-template.clj.txt");
  }

  private static void CreateFromTemplate(string path, string templatePath) {
    ProjectWindowUtil.StartNameEditingIfProjectWindowExists(0,
      CreateInstance<ClojureNewFileEndNameEditAction>(),
      GetUniqueFilename(path),
      null,
      templatePath);
  }

  /**
   * Given a filename, return a unique version of it, relative to the current selection in the Project window, if any.
   */
  private static string GetUniqueFilename(string filename) {
    string path = null;
    var selection = Selection.GetFiltered(typeof (UnityEngine.Object), SelectionMode.Assets).FirstOrDefault();
    if (selection != null) {
      path = AssetDatabase.GetAssetPath(selection);
      if (File.Exists(path)) {
        path = Path.GetDirectoryName(path);
      }
    }
    if (path == null) {
      path = DefaultScriptPath;
    }

    return AssetDatabase.GenerateUniqueAssetPath(Path.Combine(path, filename));
  }
}

/**
 * Helper ScriptableObject whose Action method will be invoked by Unity when the user finishes editing
 * the name of an asset in the Project window.
 * 
 * Creates a new script at the given path using the contents of a template, 
 * replacing '#NAMESPACE#' and '#COMPONENTNAME#' tags with appropriate values.
 */
public class ClojureNewFileEndNameEditAction : UnityEditor.ProjectWindowCallback.EndNameEditAction {
  public override void Action(int i, string path, string templatePath) {

    var filename = Path.GetFileName(path);
    var dir = Path.GetDirectoryName(path);
    if (filename == null) return;
    if (dir == null) dir = "Assets";

    dir = Path.Combine(Environment.CurrentDirectory, dir);

    // hyphens to underscores
    var outPath = Path.Combine(dir, filename.Replace('-', '_'));
    // create the output file and open a StreamWriter
    var writer = File.CreateText(outPath);

    // now ask arcadia.compiler what the namespace should be
    // note that we have to do this *after* the file exists, or we'll get a nil result
    var nsSym = (Symbol) RT.var("arcadia.compiler", "asset->ns").invoke(outPath);
    if (nsSym == null) {
      Debug.LogWarning("Unable to determine namespace for " + outPath + ". Is it on the compiler load-path?");
      writer.Dispose();
      File.Delete(outPath);
      return;
    }

    // slurp up the template and replace #NAMESPACE# with the namespace and #COMPONENTNAME# with the last namespace component
    // writing the result to our destination
    var ns = nsSym.Name;
    var component = ns.Split('.').Last();
    var tmpl = File.ReadAllText(templatePath);
    var contents = Regex.Replace(tmpl, "#NAMESPACE#", ns);
    contents = Regex.Replace(contents, "#COMPONENTNAME#", component);
    writer.Write(contents);
    writer.Close();

    AssetDatabase.Refresh();
  }


}
