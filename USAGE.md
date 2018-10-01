_This document aims to capture the current state of Arcadia. The project is still pre-alpha and has not had an official release yet, so the ideas here remain in flux. That said, the API and architecture have stabilized enough that it merits recording for those willing to take the early plunge. The [community Gitter](https://gitter.im/arcadia-unity/Arcadia) is the best place to ask questions._

_Most older videos and code snippets are no longer valid, particularly anything to do with `defcomponent`, as we underwent a major rearchitecting a year ago._

_This document assumes familiarity with [Clojure] and [Unity]._

# Contents

- [Introduction](#arcadia)
- [Starting Arcadia from Scratch](#starting-arcadia)

  - [Unity Projects](#unity-projects)
  - [Cloning Arcadia](#cloning-arcadia)
  - [Unity Configuration](#unity-configuration)

    - [Mono Runtime](#mono-runtime)
    - [Running in the Background](#running-in-the-background)

- [Arcadia Configuration](#arcadia-configuration)

- [Improving Startup Times](#improving-startup-times)

- [Livecoding and the Repl](#livecoding-and-the-repl)

  - [Under the Hood](#under-the-hood)
  - [Editor Integration](#editor-integration)

    - [Command Line](#command-line)
    - [Emacs](#emacs)
    - [SublimeText](#sublimetext)

- [Programming in Arcadia](#programming-in-arcadia)

  - [Clojure CLR](#clojure-clr)
  - [Unity Interop](#unity-interop)
  - [Hooks](#hooks)

    - [Why use Vars rather than functions?](#why-use-vars-rather-than-functions)
    - [Hooks, Serialization and Namespaces](#hooks-serialization-and-namespaces)
    - [Entry Point](#entry-point)
    - [Workflow](#workflow)

  - [State](#state)

  - [Multithreading](#multithreading)

  - [Namespace Roots](#namespace-roots)

  - [VM Restarting](#vm-restarting)

- [Packages](#packages)

  - [Leiningen Projects](#leiningen-projects)
  - [Other Projects](#other-projects)

## Arcadia

Arcadia is the integration of the [Clojure programming language][clojure] with the [Unity 3D game engine][unity]. The goal is to combine a modern, expressive programming language with the industry standard cross-platform engine for interactive media to transform the way we make creative work. Arcadia is free and open source and always will be.

## Using Arcadia

The basic Arcadia workflow is:

1. [Start a Unity project](#unity-projects)
2. [Configure Unity to work with Arcadia](#unity-configuration)
3. [Clone the Arcadia repository into project](#cloning-arcadia)
4. [Connect to the REPL](#livecoding-and-the-repl)
5. [Write a game, library, etc](#programming-in-arcadia)
6. Export or publish(#leiningen) your project

### Unity Projects

_These steps are not Arcadia specific._

Arcadia is installed on a per-project basis. It is developed and tested Unity 2017.1.0f3

Launch Unity and select _NEW_ to start a new project

![](http://imgur.com/K89L5Z0.png)

Fill out the _Project name_ and _Location_ fields. These determine where on your hard drive the files associated with the project will be created: a subdirectory with named _Project name_ in the folder _Location_. In the following example, the project files will be created in `/Users/nasser/Projects/ArcadiaDocumentation`.

![](http://imgur.com/zxMnABM.png)

Unity creates four folders to manage each project: Temp, Assets, Library, ProjectSettings. The folder that contains these four folders is the project itself. All of your own game assets (e.g. code, sound files, images, and 3D models) go in Assets. Everything else is essentially Unity's business. When version controlling a Unity project do not commit Temp or Library as their contents are often changed by Unity.

![](http://imgur.com/7VY15sZ.png)

You now have a new Unity project, almost ready for Arcadia.

### Unity Configuration

_We are trying to automate this step, and it may disappear in the future_

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

### Arcadia Configuration

Configuration for Arcadia itself is done via a user-supplied `configuration.edn` file that should be placed under `Assets` in your Unity project. Options to `configuration.edn` are given in the format of a Clojure map literal, and are documented in [`Arcadia/configuration.edn`](Arcadia/configuration.edn), which is also the default configuration file.

To determine the final set of configuration settings, Arcadia merges the user-supplied `configuration.edn` under `Assets` with the built-in `Arcadia/configuration.edn`; in other words, user settings overwrite those specified in `Arcadia/configuration.edn`. Please do not modify the default configuration file itself, as it provides the default behavior of Arcadia!

Noteworthy configuration options include:

- `:dependencies`

  Specify project dependencies, using basically the same format as Leiningen; see [Packages](#packages) below.

- `:arcadia.compiler/loadpaths`

  Specify paths to Clojure source Arcadia wouldn't pick up on otherwise.

- `:reactive`

  Specify whether Arcadia should automatically respond to changes in the file system.

- `:reload-on-change`

  Specify whether Arcadia should automatically reload Clojure files corresponding to already-loaded namespaces when they are saved, similar to Clojurescript's [Figwheel](https://github.com/bhauman/lein-figwheel) library.

- `:repl/injections`

  Specify forms to evaluate along with every form entered into the repl. Useful for things like always having the `clojure.repl` namespace available (ie, `:repl/injections (use 'clojure.repl)`).

See [`Arcadia/configuration.edn`](configuration.edn) for more details.

### Improving Startup Times

Arcadia's startup time can be improved by clicking `Arcadia → Build → Internal Namespaces` menu item. You only need to do this once per project, and each time you update Arcadia from git.

This will compile all of Arcadia's internal namespaces (including Clojure's standard library) into `.clj.dll` files (containing MSIL bytecode) ahead of time. These files can be loaded faster than the `.clj` source code files we currently ship. Otherwise, internal namespaces are compiled every time [the VM is restarted](#vm-restarting), which can slow down your workflow considerably.

### Livecoding and the REPL

One of Arcadia's most exciting features is it's ability to livecode Unity. This is enabled by Clojure which, as a member of the Lisp family of programming languages, is designed with a Read Evaluate Print Loop (or REPL) in mind. The function of a REPL is to

1. **Read incoming source code**, turning text into expressions
2. **Evaluate new expressions**, producing values, creating new functions, or effecting the state of the application
3. **Print the results** back to the programmer
4. **Loop** back to the first step, waiting for more code

The evaluated code can be as large as a whole file, or as small as an individual expression. The REPL is used to incrementally sculpt your game while developing it, to query the state of things while debugging, to put on livecoding performances, and much more.

#### Under the Hood

Arcadia uses a custom UDP socket REPL listening on port 11211\. _It does not use nREPL_, though it may move to the built in Clojure socket REPL at some point in the future.

The current protocol aims to be straightforward:

1. The REPL runs inside of Unity on a separate thread
2. It expects strings of valid Clojure code over UDP on port 11211
3. When new code is received, it is placed in a queue
4. Meanwhile, on a regular interval, the contents of the queue are evaluated on the _main thread_
5. The results of the evaluation are sent back over the socket

#### Editor Integration

Arcadia becomes most powerful when you can integrate the REPL into your coding editor. This is an area we are actively working on and has not settled down completely. We currently have a number of solutions, and you find what works best for you, your workflow, your editor, and your OS.

##### Command Line

We include two REPL clients `Editor/repl-client.rb` and `Editor/repl-client.javascript`. Running these from the command line via `ruby` or `node` will connect you to a listening Arcadia REPL and present you with a prompt at where you can evaluate code. Any editor that has facilities for interactive command line script integration can use these, too. You will need [Node.js](https://nodejs.org) or [Ruby](https://www.ruby-lang.org) installed to use these scripts (Ruby is installed by default on OSX).

They both do the same thing, though the node REPL is somewhat more robust. Use whichever works best for you.

##### Emacs

[Emacs support](https://github.com/arcadia-unity/arcadia-dot-el) uses the included Ruby and Node REPL clients.

##### SublimeText

Our [current SumblimeText support](https://github.com/arcadia-unity/repl-sublimetext) builds on [SublimeREPL](http://sublimerepl.readthedocs.io/en/latest/), which you will have to install first. It uses copies of the Ruby and Node REPL clients.

Work has started on an [alternative SumblimeText REPL integration](https://github.com/nasser/pipe) that does not depend on the script clients.

### Programming in Arcadia

Arcadia is different from both Unity and Clojure in important ways. Knowledge of both is important, but so is understanding how Arcadia itself works is

#### Clojure CLR

Most Clojure programmers are familiar with the JVM-based version of the language, but Arcadia does not use that. Instead, it is built on the [official port to the Common Language Runtime](https://github.com/clojure/clojure-clr) that [David Miller](https://github.com/dmiller) maintains. We maintain [our own fork](https://github.com/arcadia-unity/clojure-clr) of the compiler so that we can introduce Unity specific fixes.

As an Arcadia programmer you should be aware of the differences between ClojureCLR and ClojureJVM, and the [ClojureCLR wiki](https://github.com/clojure/clojure-clr/wiki) is a good place to start, in particular the pages on [interop](https://github.com/clojure/clojure-clr/wiki/Basic-CLR-interop) and [types](https://github.com/clojure/clojure-clr/wiki/Specifying-types).

#### Unity Interop

Arcadia does not go out of its way to "wrap" the Unity API in Clojure functions. Instead, a lot of Arcadia programming bottoms out in interoperating directly with Unity. For a function to point the camera at a point in space could look like

```clojure
(defn point-camera [p]
  (.. Camera/main transform (LookAt p)))
```

This uses Clojure's [dot special form](http://clojure.org/reference/java_interop#_the_dot_special_form) to access the static [`main`](https://docs.unity3d.com/ScriptReference/Camera-main.html) field of the [`Camera`](https://docs.unity3d.com/ScriptReference/Camera.html) class, which is a `Camera` instance and has a [`transform`](https://docs.unity3d.com/ScriptReference/Component-transform.html) property of type [`Transform`](https://docs.unity3d.com/ScriptReference/Transform.html) that has a [`LookAt`](https://docs.unity3d.com/ScriptReference/Transform.LookAt.html) method that takes a [`Vector3`](https://docs.unity3d.com/ScriptReference/Vector3.html). It is the equivalent of `Camera.main.transform.LookAt(p)` in C#.

Unity is a highly mutable, stateful system. The above function will mutate the main camera's rotation to look at the new point. Furthermore, the reference to `Camera/main` could be changed by some other bit of code. Unity's API is single-threaded by design, so memory corruption is avoided. Your own Clojure code can still be be as functional and multi-threaded as you like, but keep in mind that talking to Unity side effecting and impure.

There are parts of the Unity API that we have wrapped in Clojure functions, however. These are usually very commonly used methods that would be clumsy to use without wrapping, or would benefit from protocolization. The scope of what does and does not get wrapped is an on going design exercise of the framework, but in general we plan to be conservative about how much of our own ideas we impose on top of the Unity API.

#### Hooks

Unity will notify GameObjects when particular events occur, such as collisions with other objects or interaction from the user. Methods that listen for these events are called "Messages", and most are listed [here](https://docs.unity3d.com/ScriptReference/MonoBehaviour.html).

In C# Unity development, you specify how to respond to messages by implementing [Component](https://docs.unity3d.com/Manual/CreatingComponents.html) classes implementing message methods, and attaching instances of these classes to GameObjects.

Arcadia provides a simple and consistent associative (key-based) interface into Unity's message system. Users can process the scene graph as data, transparently converting messages and state to Clojure persistent maps and back. Rather than defining static Component classes, Arcadia users associate callback functions (referred to as _hooks_) with GameObjects and Unity message types.

This comprises a system of related functions:

function                            | description
----------------------------------- | ----------------------------------------------------------------------
`(hook obj, message-kw, key)`       | Retrieves a message callback (hook) from GameObject obj on a key
`(hook+ obj, message-kw, key, IFn)` | Sets a message callback on a key
`(hook- obj, message-kw, key)`      | Removes a message callback on a key
`(state obj, key)`                  | Retrieves a piece of state from obj on a key
`(state+ obj, key)`                 | Sets a piece of state on a key
`(state- obj, key)`                 | Removes a piece of state on a key
`(update-state obj, key, f & args)` | Updates the state on a key by applying function `f` to it
`(role obj, key)`                   | Retrieves a map containing all hooks and state on a key
`(role+ obj, key, role-map)`        | Sets state and multiple hooks on a key
`(role- obj, key)`                  | Removes state and all hooks on a key
`(roles obj)`                       | Retrieves mapping from all keys on obj to the role-map for a given key
`(roles+ obj, roles-map)`           | Sets multiple roles at once (may remove hooks or state)

The hook system is complemented by a type-definition mechanism for high-performance mutable state:

form                                      | description
----------------------------------------- | --------------------------------------------------------------------------------------
`(defmutable OrbitState [^float radius])` | Defines mutable type with unboxed primitive field `radius`
`(snapshot orbit-state)`                  | Retrieves persistent representation of `defmutable`-type instance `orbit-state`
`(mutable orbit-state-map)`               | Constructs `defmutable`-type instance from persistent representation `orbit-state-map`

`roles` will snapshot any `defmutable`-type instances in a GameObject's state, and `role+` will convert any `defmutable` snapshot data to a corresponding `defmutable` instance. Users can thereby process the scene graph as immutable data without sacrificing imperative performance.

The hook system is fully optional and can coexist with other approaches. If you want to roll your own treatment of the scene graph Arcadia will not get in your way.

```clojure
(use 'arcadia.core 'arcadia.linear)
(import '[UnityEngine GameObject Time Mathf Transform])

(defn orbit [^GameObject obj, k]         ; Takes the GameObject and the key this function was attached with
  (let [{:keys [:radius]} (state obj k)] ; Looks up the piece of state corresponding to the key `k`
    (with-cmpt obj [tr Transform]
      (set! (. tr position)
        (v3 (* radius (Mathf/Cos Time/realtimeSinceStartup))
            0
            (* radius (Mathf/Sin Time/realtimeSinceStartup)))))))

(let [gobj (create-primitive :cube "Orbiter")]
  (state+ gobj :orbit {:radius 5})          ; set up state
  (hook+ gobj :fixed-update :orbit #'orbit) ; set up message callback (hook)
  )
```

The role system can abbreviate this and treat it as data:

```clojure
(let [gobj (create-primitive :cube "Orbiter")]
  (role+ gobj :orbit
    {:state {:radius 5}
     :fixed-update #'orbit}))
```

`role+` is intended to emulate `assoc` with the GameObject in the place of a map, and will therefore _replace_ any hooks and state already on the GameObject at the specified key.

```clojure
(defn orbit-collide [obj1 obj2 k]
  (UnityEngine.Debug/Log "a collision!"))

;; We'll start by setting up a role.
(role+ (object-named "Orbiter") :orbit
  {:state {:radius 5},
   :fixed-update #'orbit
   :on-collision-enter #'orbit-collide})

(role (object-named "Orbiter") :orbit)

;; =>
;; {:state {:radius 5},
;;  :fixed-update #'orbit
;;  :on-collision-enter #'orbit-collide}

(defn other-orbit-collide [obj1 obj2 k]
  (UnityEngine.Debug/Log "another collision!"))

;; If we call `role+` again with the same key but a different map of
;; hooks and state, it will:
;; - reset hooks and state to the specified values
;; - remove any existing hooks or state not specified
(role+ (object-named "Orbiter") :orbit
  {:state {:radius 8},
   :on-collision-enter #'other-orbit-collide})

(role (object-named "Orbiter") :orbit)

;; =>
;; {:state {:radius 8},
;;  :on-collision-enter #'other-orbit-collide}

;; Note that the :fixed-update hook has been removed.
```

Attached state and roles can then be retrieved as persistent maps using `role`:

```clojure
  (role (object-named "Orbiter") :orbit)

  ;; =>
  ;; {:state {:radius 5},
  ;;  :fixed-update #'user/orbit}
```

Multiple callbacks and pieces of state may be attached in this manner.

```clojure
;; ...
(defn bobble [^GameObject obj, k] ; Takes the GameObject and the key this function was attached with
  (let [{:keys [:amplitude]} (state obj k)] ; Looks up the piece of state corresponding to the key `k`
    (with-cmpt obj [tr Transform]
      (set! (. tr position)
        (v3 (.. tr position x)
            (* amplitude (Mathf/Cos Time/realtimeSinceStartup))
            (.. tr position y))))))

(let [gobj (create-primitive :cube "Orbiter")]
  (state+ gobj :orbit {:radius 5})          ; set up state
  (hook+ gobj :fixed-update :orbit #'orbit) ; set up the hook
  (state+ gobj :bobble {:amplitude 5})      ; set up another piece of state
  (hook+ gobj :fixed-update #'bobble)       ; set up another hook
  )
```

All keys can be accessed and managed at once using `roles` and `roles+`.

`roles` bundles up all the keys, hooks, and state into a map with the hook or state keys as its keys, and maps suitable for use in `role+` as its values.

```clojure
  (roles (object-named "Orbiter"))

  ;; =>
  ;; {:orbit {:state {:radius 5},
  ;;          :fixed-update #'orbit},
  ;;  :bobble {:state {:amplitude 5},
  ;;           :fixed-update #'bobble}}
```

`roles+` attaches a bundle of keys, hooks, and state, such as might be returned by `roles`, to the scene graph.

```clojure
  (roles+ (object-named "Orbiter")
    {:orbit {:state {:radius 5},
             :fixed-update #'orbit},
     :bobble {:state {:amplitude 5},
              :fixed-update #'bobble}})
```

##### Why use Vars rather than functions?

While anonymous `fn` functions can be used as hook callbacks, Vars are greatly preferred because they can be serialized. Any data that would be closed over by an anonymous function instance can be stored in the state corresponding to a callback Var.

For example, consider the following function that takes a GameObject and a numeric rate of spin, and starts the GameObject spinning at that rate.

```clojure
(defn start-spinning [obj, rate]
  (let [f (fn [obj, _]
            (with-cmpt obj [tr Transform]
              (set! (.. tr rotation)
                (qq* (.. tr rotation)
                     (aa rate 0 1 0)))))] ; uses `rate` to set degrees spun per frame
    (role+ obj ::gyrate
      {:update f})))
```

Here we attach an anonymous `fn` to `obj` as the `:update` hook. Anonymous `fn`'s cannot serialize, therefore this GameObject cannot serialize, greatly limiting its role in our scene.

We can solve this problem by boosting the `:update` hook into a top-level named function, and storing `rate` in the state:

```clojure
(defn spin [obj, k]
  (let [{:keys [rate]} (state obj k)] ; retrieve rate of spinning from state
    (with-cmpt obj [tr Transform]
      (set! (.. tr rotation)
        (qq* (.. tr rotation)
             (aa rate 0 1 0))))))

(defn start-spinning [obj, rate]
 (role+ obj ::gyrate
   {:update #'spin        ; attach the Var #'spin as an update hook
    :state {:rate rate}}) ; store rate of spinning in state
 )
```

This arrangement will serialize correctly.

##### Hooks, Serialization and Namespaces

Hooks attached to a GameObject are stored and managed by instances of [ArcadiaBehaviour](https://github.com/arcadia-unity/Arcadia/blob/develop/Components/ArcadiaBehaviour.cs) components.

When the ArcadiaBehaviour instance is [serialized](https://docs.unity3d.com/Manual/script-Serialization.html), the hooks and their keys are converted into an [edn](https://github.com/edn-format/edn) string. Upon deserialization, any Vars attached as hooks are [interned](https://clojuredocs.org/clojure.core/intern), but their corresponding namespace is not immediately [`require`'d](https://clojuredocs.org/clojure.core/require). If a referenced user namespace includes definitions or logic that assume the existence of certain elements in the scene graph, for example, or use methods forbidden during deserialization such as [`UnityEngine.Object/Find`](https://docs.unity3d.com/ScriptReference/GameObject.Find.html), we want to delay loading them. The situation is similar to that motivating [`window.onload`](https://developer.mozilla.org/en-US/docs/Web/API/GlobalEventHandlers/onload) in frontend Javascript.

The namespace corresponding to a deserialized Var is `required` once for whichever of the following happens first:

- The [`Awake`](https://docs.unity3d.com/ScriptReference/MonoBehaviour.Awake.html) method of the `ArcadiaBehaviour` instance is called.
- The particular message method of the `ArcadiaBehaviour` instance is called -- ie, for an instance of the derived [`FixedUpdateHook.cs`](https://github.com/arcadia-unity/Arcadia/blob/develop/Components/FixedUpdateHook.cs) class, when the `FixedUpdate` method is first called.

Remember `require` in Clojure by default will _not_ run the code, including definitions, in a given namespace if that namespace has already been `require`'d. It is therefore safe to call `require` multiple times on the same namespace.

##### Entry Point

Clojure developers often look for a "main" function or an "entry point" that they can use to kick off their code. Unity does not have such a thing. _All_ code at runtime is triggered through Messages, meaning _all code at runtime is triggered by Components attached to GameObjects_. If your game logic would benefit from an entry point, you will have to add an empty GameObject to the scene with a `:start` hook associated with the function you want to run at startup.

##### Workflow

For hooks that serialize and persist, writing code to a file is crucial. At the same time, the UI for adding hooks is not finished yet, so the REPL is still needed. A good workflow is the following:

1. Start a Clojure file in `Assets` following Clojure folder naming conventions (e.g. `Assets/game/core.clj`)
2. Give the file a namespace form as usual with any `require`s you need (e.g. `(ns game.core (:require arcadia.core))`)
3. Define a function in that file that you want to use as a hook (e.g. `(defn rotate [gameobject] (.. gameobject transform (Rotate 0 1 0)))`)
4. Save the file
5. In the REPL, require the namespace and use `arcadia.core` (e.g. `(require 'game.core) (use 'arcadia.core)`)
6. In the REPL, attach the var refering to the function to a GameObject as a hook (e.g. `(hook+ (object-named "Main Camera") :update #'game.core/rotate)`)
7. Save the scene
8. The GameObject will `require` the namespace associated with the var and lookup the function so that it can be invoked whenever unity sends its message. Hooks require the namespace of their associated vars once, either when their message is first called or on `Awake`, whichever comes first.

#### Multithreading

While the Unity scene graph API is ostensibly single threaded, the Mono VM it runs on is not. This means you can write multithreaded Clojure code in all its functional glory, provided that you do not call Unity scene graph methods off the main thread (they will throw an exception).

Note that not all types in the UnityEngine namespace need to be used from the main thread. Value types such as Vector3 are usable anywhere, for example.

To trigger behavior restricted to the main thread from other threads, consider implementing a callback queue driven by the [Update Monobehaviour method](https://docs.unity3d.com/ScriptReference/MonoBehaviour.Update.html) of a component in the scene graph, or an [EditorApplication.update](https://docs.unity3d.com/ScriptReference/EditorApplication-update.html) delegate for edit-mode operations. An example of the latter can be found in the (internal, unstable) Arcadia namespace [arcadia.internal.editor-callbacks](https://github.com/arcadia-unity/Arcadia/blob/develop/Source/arcadia/internal/editor_callbacks.clj).

#### Namespace Roots

The `Assets` folder is the root of your namespaces. So a file at `Assets/game/logic/hard_ai.clj` should correspond to the namespace `game.logic.hard-ai`. Arcadia internally manages other namespace roots as well for its own operation, but `Assets` is where your own logic should go.

#### VM Restarting

Unity will restart the Mono VM at specific times

- Whenever the editor compiles a file (e.g. when a `.cs` file under `Assets` is updated or a new `.dll` file is added)
- Whenever the editor enters play mode

When this happens, any state you had set up in the REPL will be lost and you will have to re-`require` any namespaces you were working with. This is a deep part of Unity's architecture and not something we can meaningfully affect.

## Packages

Arcadia ships with a built-in package manager compatible with [Leiningen](https://leiningen.org/).

Arcadia supports developing (and publishing, via Leiningen) multiple Clojure libraries in a single Unity project. Dependencies can be specified either in `Assets/configuration.edn` or in the `project.clj` file of any Leiningen project in `Assets`.

By default, Arcadia will automatically pull in any newly-specified dependencies from Maven when one of these files changes. This happens on a separate thread, and will not block the normal operation of Unity or Arcadia.

### Specifying Dependencies

Dependencies may be specified in the user-supplied `Assets/configuration.edn` with a vector keyed to `:dependencies`, with the same vector-of-vectors format used by Leiningen.

Arcadia will merge the dependencies declared in `Assets/configuration.edn` with those declared in all Leiningen `project.clj`s to compute the final set of dependencies and pull them in from Maven.

### Leiningen Projects

Leiningen projects should work correctly in Arcadia. Any directory in the `Assets` folder that contains a properly-formatted Leiningen `project.clj` will be considered a Leiningen project. Arcadia will treat the roots of these directories the same way Leiningen does, using `:source-paths` if it is defined and `src` otherwise.

For example, say one has a Leiningen project `some_lein_project` under `Assets` with the following structure:

```
Assets
|- some_lein_project
   |- project.clj
   |- src
      |- top_namespace
         |- inner_namespace
            |- core.clj
```

```clojure
(require 'top-namespace.inner-namespace.core)
```

In this case, Arcadia would look in `Assets/some_lein_project/src/top_namespace/inner_namespace/core.clj` for the corresponding file (and find it).

Arcadia will extract the declared dependencies of a Leiningen project's `project.clj` and pull them in from Maven, just like running `lein deps`.

Arcadia libraries can be published the same way you would publish a Leiningen library. Once published, a Leiningen library can be declared as a dependency either in `Assets/configuration.edn` or the `project.clj` of a Leiningen project directly under `Assets`.

There is no guarantee, of course, that a Clojure library which works fine on the JVM will work in Arcadia. Porting JVM libraries to Arcadia is usually fairly easy, however. Libraries with no host interop can usually be ported directly, and C# is similar enough to Java that even porting libraries with heavy interop is often straightforward.

### Other Projects

If you need to set the root of a project somewhere else, you can specify it in the `Assets/configuration.edn` file. Place all paths you wish to consider roots for Clojure namespaces in a vector keyed to `:arcadia.compiler/loadpaths`, this works similarly to Leiningen `:source-paths`.

This option is especially useful for cloning repositories into Arcadia that don't have a Leiningen-style structure, and that don't assume their containing directory should be their Clojure namespace root.

[clojure]: http://clojure.org/
[unity]: http://unity3d.com/
