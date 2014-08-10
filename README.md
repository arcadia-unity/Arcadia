Clojure Unity
=============
The integration of the Clojure Programming Language with the Unity 3D game engine.

Status
------
Clojure Unity is still in development. It works well, but is still subject to change as we stabalize it. Building large or high-stakes projects with it is probably a bad idea.

Usage
-----
The `Assets/Clojure` folder can be copied into any Unity project to enable Clojure. In the future, this will be available from the Asset Store. Once copied, you have access to the REPL and the Clojure compiler.

### Prerequisites
Before doing anything, Clojure Unity requires that Unity be set to run in the background and use the full .NET API

#### Run in Background
Click `Edit > Project Settings > Player`, then in the Inspector under Resolution and Presentation make sure Run In Background is checked.

#### .NET API
Click `Edit > Project Settings > Player`, then in the Inspector under Other Settings make sure Api Compatibility Level is set to .NET 2.0 and not .NET 2.0 Subset.

### REPL
Clojure Unity ships with a simple networked REPL that is started from `Clojure > REPL > Start`. You can connect to this REPL using `Extra/REPL/client.rb`. This script is also the basis of our Sublime Text support (via SublimeREPL) and our Emacs support (via Inferior Lisp).

### Clojure Compiler
You can write components in pure Clojure. The API is still very much in the air, but the appropriate `gen-class` call will work. Examples and documentation will follow once we settle on a more permanent approach.

Legal
-----
Copyright © 2014 Ramsey Nasser and Tims Gardner.

Licensed under the [Eclipse License](https://www.eclipse.org/legal/epl-v10.html), the same as Clojure.