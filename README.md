Arcadia
=======

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/arcadia-unity/Arcadia?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
The integration of the Clojure Programming Language with the Unity 3D game engine.

Status
------
Arcadia is alpha-quality software, and shouldn't be used for anything important yet.

Community
---------
- [Homepage](https://arcadia-unity.github.io/)
- [Gittr/IRC](https://gitter.im/arcadia-unity/Arcadia)
- [Twitter](https://twitter.com/arcadiaunity)

Installing
----------
The contents of the repository should be copied to a folder named `Arcadia` in any Unity project's Assets folder to enable Clojure. In the future, this will be available from the Asset Store. Once copied, you have access to the REPL and the Clojure compiler.

#### Download Zip File

1. Create a subfolder of your Assets folder named Arcadia
2. [Download](https://github.com/arcadia-unity/Arcadia/archive/develop.zip) our zip file and extract its contents into Arcadia

#### Git Clone

```
cd path/to/unity/project/Assets
git clone https://github.com/arcadia-unity/Arcadia.git
```

Usage
-----
[USAGE.md](https://github.com/arcadia-unity/Arcadia/blob/develop/USAGE.md) us a good place to start, as is the [Wiki](https://github.com/arcadia-unity/Arcadia/wiki). They both cover how to get started and all the prerequisites you'll need. The community on the [Gitter](https://gitter.im/arcadia-unity/Arcadia) is very helpful and happy to answer questions. 

### REPL
Arcadia ships with a simple networked REPL that is started automatically or from `Clojure > REPL > Start`. To integrate your text editor with Arcadia, there are a few clients listed on [the Wiki](https://github.com/arcadia-unity/Arcadia/wiki/Resources). Additionally, there are three command line scripts that act as REPL clients if that makes your integration easier. `Editor/repl-client.rb`, `Editor/repl-client.javascript`, and `Infrastructure/repl`. `Infrastructure/repl` is the future, but not totally stable yet. You should try them all and use what works best.

The core devs use the REPL from [Emacs (via Inferior Lisp)](https://github.com/arcadia-unity/arcadia/wiki/Editor-support#emacs) and [SublimeText (via Socket)](https://github.com/nasser/Socket).

Contributing
------------
If you're thinking of submitting code to Arcadia – thanks! We're excited to have your help. First, all contributors must read and agree to our [contributor license agreement](./CONTRIBUTOR-LICENSE-AGREEMENT.md). It is based on [GitHub's CLA](https://cla.github.com/) and ensures that the code you submit remains useable by Arcadia and its community without issue. It confirms that

1. Anyone can use your contributions anywhere, for free, forever.
2. Your contributions do not infringe on anyone else's rights.
3. You retain ownership of your contribution.

Once you have read and agree to it, submit a [Pull Request](https://github.com/arcadia-unity/Arcadia/pull/new) adding your name and GitHub id to [CONTRIBUTORS.md](./CONTRIBUTORS.md) with the following commit message:

I have read and agree to the terms of the Arcadia Contributor License Agreement.

You only need to do this once. After that, we can review and merge any contributions you send us!

Name
----
This project was originally named "clojure-unity", but was changed to "Arcadia" to avoid infinging on Clojure's trademarks. It was suggested to us by @ztellman at a bar during StrangeLoop 2014, and we are eternally grateful.

Legal
-----
Copyright © 2014-2017 Tims Gardner, Ramsey Nasser, and [contributors](./CONTRIBUTORS.md).

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with the License. You may obtain a copy of the License at

```
http://www.apache.org/licenses/LICENSE-2.0
```

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

