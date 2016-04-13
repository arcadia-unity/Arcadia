Arcadia
=======
The integration of the Clojure Programming Language with the Unity 3D game engine.

Status
------
Arcadia is alpha-quality software, and shouldn't be used for anything important yet.

Until the next release, the most reliable branch is [```unstable```](https://github.com/arcadia-unity/Arcadia/tree/unstable). This is where we put versions of Arcadia we're relatively confident aren't grossly broken, and is the best place to get started. As the name implies, ```unstable``` (and everything else, at least until the next release) is potentially subject to breaking changes.

Primary work is done on the rapidly evolving [```develop``` branch](https://github.com/arcadia-unity/Arcadia/tree/develop). Use that for bleeding edge features.

Community
---------
- [Mailing List](https://groups.google.com/forum/#!forum/arcadia-unity)
- [Twitter](https://twitter.com/arcadiaunity)
- [Gittr](https://gitter.im/arcadia-unity/Arcadia)
- IRC freenode #arcadiaunity

Usage
-----
The contents of the repository should be copied to a folder named `Arcadia` in any Unity project's Assets folder to enable Clojure. In the future, this will be available from the Asset Store. Once copied, you have access to the REPL and the Clojure compiler.

There's a screencast on getting set up [here](https://www.youtube.com/watch?v=KLq9b9lDmkc).

#### Download Zip File

1. Create a subfolder of your Assets folder named Arcadia
2. [Download](https://github.com/arcadia-unity/Arcadia/archive/develop.zip) our zip file and extract its contents into Arcadia

#### Git Clone

```
cd path/to/unity/project/Assets
git clone https://github.com/arcadia-unity/Arcadia.git
```

### Prerequisites
Before doing anything, Arcadia requires that Unity be set to run in the background and use the full .NET API

#### Run in Background
Click `Edit > Project Settings > Player`, then in the Inspector under Resolution and Presentation make sure Run In Background is checked.

#### .NET API
Click `Edit > Project Settings > Player`, then in the Inspector under Other Settings make sure Api Compatibility Level is set to .NET 2.0 and not .NET 2.0 Subset.

### REPL
Arcadia ships with a simple networked REPL that is started automatically or from `Clojure > REPL > Start`. You can connect to this REPL using `Clojure/Editor/repl-client.rb`. This script is also the basis of our [Sublime Text support (via SublimeREPL)](https://github.com/clojure-unity/repl-sublimetext) and [our Emacs support (via Inferior Lisp)](https://github.com/arcadia-unity/arcadia/wiki/Editor-support#emacs).

### Clojure Components
You can write components in pure Clojure using our [`defcomponent`](https://github.com/arcadia-unity/arcadia/wiki/arcadia.core#defcomponent) form. To be recognized by Unity these must be saved in Clojure files, not just defined in the REPL.

Mailing List
------------
The mailing list is [here](https://groups.google.com/forum/#!forum/arcadia-unity).

Gitter
---
The [gitter chat](https://gitter.im/arcadia-unity/Arcadia) for the project, if we're online we're usually on the channel.

Name
-----
This project was originally named "clojure-unity", but was changed to "Arcadia" to avoid infinging on Clojure's trademarks. It was suggested to us by @ztellman at a bar during StrangeLoop 2014, and we are eternally grateful.

Legal
-----
Copyright © 2014 Tims Gardner and Ramsey Nasser

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

```
http://www.apache.org/licenses/LICENSE-2.0
```

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

