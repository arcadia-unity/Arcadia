using System.Linq;
using UnityEngine;
using UnityEditor;
using clojure.lang;

namespace Arcadia
{
	public class ConfigurationObject : ScriptableObject { }

	[CustomEditor(typeof(ConfigurationObject))]
	public class Configuration : Editor
	{
		public static string defaultConfigFilePath = Initialization.VariadicPathCombine("Assets", "Arcadia", "configuration.edn");
		public static string userConfigFilePath = Initialization.VariadicPathCombine("Assets", "configuration.edn");

		static ConfigurationObject _clojureConfigurationObject;

		public static object Get(string ns, string name)
		{
			var configAtom = (Atom)RT.var("arcadia.config", "configuration").deref();
			var configIfn = (IFn)configAtom.deref();
			return (Keyword)configIfn.invoke(Keyword.intern(ns, name));
		}

		[MenuItem("Arcadia/Configuration...")]
		public static void Init()
		{
			RT.load("arcadia/config");
			RT.var("arcadia.config", "update!").invoke();

			if (_clojureConfigurationObject == null)
				_clojureConfigurationObject = ScriptableObject.CreateInstance<ConfigurationObject>();

			Selection.activeObject = _clojureConfigurationObject;
		}

		public override void OnInspectorGUI()
		{
			userConfigFilePath = EditorGUILayout.TextField("Configuration File", userConfigFilePath);
			RT.var("arcadia.config", "render-gui").invoke();
		}
	}
}