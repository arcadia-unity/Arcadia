*This document aims to capture the current state of Arcadia. The project is still pre-alpha and has not had an official release yet, so the ideas here remain in flux. That said, the API and architecture have stabilized enough that it merits recording for those willing to take the early plunge. The [community Gitter](https://gitter.im/arcadia-unity/Arcadia) is the best place to ask questions.*

*Most videos and code snippets from a year ago are no longer valid, particularly anything to do with `defcomponent`, as we underwent a major rearchitecting some months ago.*

*This document assumes familiarity with [Clojure][clojure] and [Unity][unity].*

## Arcadia
Arcadia is the integration of the [Clojure programming language][clojure] with the [Unity 3D game engine][unity]. The goal is to combine a modern, expressive programming language with the industry standard cross-platform engine for interactive media to transform the way we make creative work. Arcadia is free and open source and always will be.

## Using Arcadia
The basic Arcadia workflow is:

1. Start a Unity project
2. Configure Unity to work with Arcadia
3. Clone Arcadia repository into project
4. Connect to the REPL
5. Write game logic into Clojure namespaces
6. Connect game logic to objects using Hooks
7. Associate state with game objects using State
8. Export your project

### Unity Projects
*These steps are not Arcadia specific.*

Arcadia is installed on a per-project basis. It is developed and tested Unity 5.2.x and 5.3.x. 

Launch Unity and select *NEW* to start a new project

![](http://imgur.com/K89L5Z0.png)

Fill out the *Project name* and *Location* fields. These determine where on your hard drive the files associated with the project will be created: a subdirectory with named *Project name* in the folder *Location*. In the following example, the project files will be created in `/Users/nasser/Projects/ArcadiaDocumentation`.

![](http://imgur.com/zxMnABM.png)

Unity creates four folders to manage each project: Temp, Assets, Library, ProjectSettings. The folder that contains these four folders is the project itself. All of your own game assets (e.g. code, sound files, images, and 3D models) go in Assets. Everything else is essentially Unity's business. When version controlling a Unity project do not commit Temp or Library as their contents are often changed by Unity.

![](http://imgur.com/7VY15sZ.png)

You now have a new Unity project, almost ready for Arcadia.

### Unity Configuration
*We are trying to automate this step, and it may disappear in the future*

By default, Unity is configured in a way that does not allow Arcadia to run well.

#### Mono Runtime
Unity uses a subset of the .NET 2.0 framework by default, which prevents any Clojure code from running. To change this to the full framework, select from the main menu Edit → Project Settings → Player. This will open the Player inspector in your inspector tab. In the Player inspector, locate Other Settings → Optimization → Api Compatibility Level and set that to .NET 2.0

![](http://imgur.com/ixba13Y.png)

#### Running in the Background
Unity will stop executing code if its application does not have focus. This is an optimization that gets in the way of live coding, where having Unity in the background while you send code to it from your editor is typical. To change this, in the Player inspector locate Resolution and Presentation → Run in Background and make sure the box is checked.

![](http://imgur.com/XmjGkAe.png)

Unity is now ready for Arcadia.

### Cloning Arcadia
As Arcadia has not been released, the only way to use it at the moment is by cloning the git repository. In the future, Arcadia builds will be available from the Unity Asset store, but the repository cloning path will remain useful for developers who need the most bleeding edge features and fixes.

Git clients work differently, but the basic task is to

1. Clone the Arcadia repository at `https://github.com/arcadia-unity/Arcadia.git` into a folder called Arcadia in your Assets folder
2. Switch to the `develop` branch

On OSX/Linux machine, the shell commands are

```
$ cd ~/Projects/ArcadiaDocumentation/Assets
$ git clone https://github.com/arcadia-unity/Arcadia.git
$ cd Arcadia/
$ git checkout develop
```

On Windows or using a GUI git client you will have to perform the equivalent operations.

Switch back into Unity. It is normal for it to take a moment to load all the new code.

![](http://imgur.com/CdH3qMi.png)

To confirm Arcadia's installation, check your Unity console. If it is not open, select from the main menu Window → Console. There may be errors and warnings, but if it ends with `Starting REPL` and `Arcadia Started!` you should be set.

![](http://imgur.com/PrWoc0P.png)

### Livecoding and the REPL
One of Arcadia's most exciting features is it's ability to livecode Unity. This is enabled by Clojure which, as a member of the Lisp family of programming languages, is designed with a Read Evaluate Print Loop (or REPL) in mind. The function of a REPL is to

1. **Read incoming source code**, turning text into expressions
2. **Evaluate new expressions**, producing values, creating new functions, or effecting the state of the application
3. **Print the results** back to the programmer
4. **Loop** back to the first step, waiting for more code

The evaluated code can be as large as a whole file, or as small as an individual expression. The REPL is used to incrementally sculpt your game while developing it, to query the state of things while debugging, to put on livecoding performances, and much more. 

#### Under the Hood
Arcadia uses a custom UDP socket REPL listening on port 11211. *It does not use nREPL*, though it may move to the built in Clojure socket REPL at some point in the future.

The current protocol aims to be straightforward:

1. The REPL runs inside of Unity on a separate thread
2. It expects strings of valid Clojure code over UDP on port 11211
3. When new code is received, it is placed in a queue
4. Meanwhile, on a regular interval, the contents of the queue are evaluated on the *main thread*
5. The results of the evaluation are sent back over the socket

#### Editor Integration
Arcadia becomes most powerful when you can integrate the REPL into your coding editor. This is an area we are actively working on and has not settled down completely. We currently have a number of solutions, and you find what works best for you, your workflow, your editor, and your OS.

##### Command Line
We include two REPL clients `Editor/repl-client.rb` and `Editor/repl-client.javascript`. Running these from the command line via `ruby` or `node` will connect you to a listening Arcadia REPL and present you with a prompt at where you can evaluate code. Any editor that has facilities for interactive command line script integration can use these, too. You will need [Node.js](https://nodejs.org) or [Ruby](https://www.ruby-lang.org) installed to use these scripts (Ruby is installed by default on OSX).

They both do the same thing, though the node REPL is somewhat more robust. Use whichever works best for you.

##### Emacs
[Emacs support](https://github.com/arcadia-unity/arcadia-dot-el) uses the included Ruby and Node REPL clients.

##### SumblimeText
Our [current SumblimeText support](https://github.com/arcadia-unity/repl-sublimetext) builds on [SublimeREPL](http://sublimerepl.readthedocs.io/en/latest/), which you will have to install first. It uses copies of the Ruby and Node REPL clients.

Work has started on an [alternative SumblimeText REPL integration](https://github.com/nasser/pipe) that does not depend on the script clients.

<!--
### Programming in Arcadia
* vm flushing
* namespace roots
* interop
* focus-dependent REPL
* unity is stateful
* arcadia core
* arcadia linear

### Hooks

### State

### Export

### Future?
* config
* packages
-->

[clojure]: http://clojure.org/
[unity]: http://unity3d.com/