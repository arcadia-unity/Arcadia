Arcadia
=======
The integration of the Clojure Programming Language with the Unity 3D game engine.

Status (Important!)
-------------------
Arcadia is under development, and should not be used for real projects yet. The remainder of this file is mostly for our own reference. More to come.

Usage
-----
The `Clojure` folder can be copied into any Unity project's Assets folder to enable Clojure. In the future, this will be available from the Asset Store. Once copied, you have access to the REPL and the Clojure compiler.

### Prerequisites
Before doing anything, Arcadia requires that Unity be set to run in the background and use the full .NET API

#### Run in Background
Click `Edit > Project Settings > Player`, then in the Inspector under Resolution and Presentation make sure Run In Background is checked.

#### .NET API
Click `Edit > Project Settings > Player`, then in the Inspector under Other Settings make sure Api Compatibility Level is set to .NET 2.0 and not .NET 2.0 Subset.

### REPL
Arcadia ships with a simple networked REPL that is started automatically or from `Clojure > REPL > Start`. You can connect to this REPL using `Clojure/Editor/repl-client.rb`. This script is also the basis of our [Sublime Text support (via SublimeREPL)](https://github.com/clojure-unity/repl-sublimetext) and our Emacs support (via Inferior Lisp).

### Clojure Components
You can write components in pure Clojure using our `defcomponent` form.

Name
-----
This project was originally named "clojure-unity", but was changed to "Arcadia" To avoid infinging on Clojure's trademarks. It was suggested to us by @ztellman at a bar during StrangeLoop 2014, and we are eternally grateful.

Legal
-----
Copyright © 2014 Ramsey Nasser and Tims Gardner.

Licensed under the [Eclipse License](https://www.eclipse.org/legal/epl-v10.html), the same as Clojure.
