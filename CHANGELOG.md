# Arcadia Changelog

## Beta 1.0.3
- Fixed MAGIC issue [#35](https://github.com/nasser/magic/issues/35) with a compiler built from [arcadia-unity/clojure-clr@9e5b7330](https://github.com/arcadia-unity/clojure-clr/commit/9e5b7330c7e46ac7e5978bd289a869199c2f6e53)

## Beta 1.0.2
- Fixed issue [#333](https://github.com/arcadia-unity/Arcadia/issues/333).
- Fixed issue [#338](https://github.com/arcadia-unity/Arcadia/issues/338).

## Beta 1.0.1

- Fixed issue [#331](https://github.com/arcadia-unity/Arcadia/issues/331).
- Fixed issue [#330](https://github.com/arcadia-unity/Arcadia/issues/330).

## Beta 1.0.0

This beta release includes many breaking changes. In particular, scenes and prefabs using Arcadia components saved from previous versions will not work. Subsequent releases will be backwards-compatible with scenes and prefabs saved as of this release.

These changes are relative to [83e0931a](https://github.com/arcadia-unity/Arcadia/commit/83e0931abc2b583715553bc0e2c77664af375e55).

- Improved startup times.
- Removed UDP REPL server and its various clients.
- Added basic nREPL server listening on port 3277.
- Changed Socket Server port from 5555 to 37220.
- Disabled package manager, pending NuGet support.
- [Customizable stacktrace formatting](https://github.com/arcadia-unity/Arcadia/wiki/Stacktraces-and-Error-Reporting) for exceptions in REPL.
- Added a Code of Conduct.
- Changed position of `key` argument of hook functions from last to second position.
- Changed `arcadia.core/hook+` and `arcadia.core/role+` to issue a warning whenever non-serializable hooks are used.
- Updated export policy. Namespaces meant for export must be explicitly listed in configuration file under the `:export-namespaces` key.
- Added `:arcadia` options map entry to `project.clj` format.
- Changed `:arcadia.compiler/loadpaths` to `:source-paths` in configuration file.
- No longer supporting fastkeys annotation for optimized `state` lookup, optimizations are now handled by internal caching mechanism.
- Documented API.
- `USAGE.md` is deprecated. Please use the wiki from now on.
- Added new state and hook inspector UI.
- Restructured Arcadia menu:
  - `Arcadia / Build / Internal Namespaces` and `Arcadia / Build / User Namespaces` are now `Arcadia / AOT Compile`
  - `Arcadia / Build / Prepare Export` is now `Arcadia / Prepare for Export`
  - `Arcadia / Build / Clean Compiled` is now `Arcadia / Clean / Clean Compiled`
  - `Arcadia / Clean` is now `Arcadia / Clean / Clean All`
- Removed IEntityComponent and ISceneGraph protocols, and restricting what were previously their methods to only take GameObjects (not Components and vars). This does not pertain to `arcadia.core/gobj`, which still takes GameObjects and Components.
  This change effects the following vars:
  - `arcadia.core`:
    `child+`, `child-`, `children`, `cmpt`, `cmpts`, `cmpt+`, `cmpt-`, `ensure-cmpt`, `with-cmpt`, `if-cmpt`, `hook+`, `hook-`, `clear-hooks`, `hook`, `state`, `state+`, `state-`, `clear-state`, `update-state`, `role`, `role-`, `role+`, `role-`, `roles+`, `roles-`, `roles`
- Changed `arcadia.core/state` to convert `defmutable` instances to persistent representations.
- Changed `arcadia.core/state+` to convert persistent representations of `defmutable` data to `defmutable` instances.
- Added `arcadia.core/lookup`.
- Moved some `defmutable` implementation details into `arcadia.internal.protocols`.
- Changed `arcadia.core/cmpts` to filter out null Components.
- Changed `arcadia.core/children` to filter out null GameObjects.
- Made `(arcadia.core/child- x y)` only remove parent of `y` if `y` is a child of `x`.
- Fixed bug in which `arcadia.core/children` throws if some children are destroyed.
- Renamed `gobj-seq` to `descendents` and restricted to GameObjects.
- Removed `arcadia.core/hook?`.
- Removed `arcadia.core/hook-var`.
- Made `arcadia.core/hook-types` private.
- Added `arcadia.core/available-hooks`.
- Removed 3-ary overload for `arcadia.core/hook+`.
- Renamed `arcadia.core/clear-hook` to `arcadia.core/clear-hooks`.
- Renamed `arcadia.core/null-obj?` to `arcadia.core/null?`.
- Renamed `arcadia.core/obj-nil` to `arcadia.core/null->nil`.
- Extended `clojure.core.CollReduce` protocol to GameObjects.
- Removed `arcadia.introspection/snapshot-data-fn`.
- Removed `arcadia.linear/point-pivot`.
- Removed `arcadia.core/with-gobj`.
- Made `arcadia.core/object-*` core functions filter out destroyed UnityEngine.Objects. Affects the following functions: `object-typed`, `objects-typed`, `object-named`, `objects-named`, `object-tagged`, `objects-tagged`.
- `object-named` argument may be a Regex.
- Made `arcadia.core/if-cmpt` throw for `nil` or destroyed GameObjects.
- Made `arcadia.core/state+` no longer support 2-ary overload, and made it now return its third argument.
- Made `arcadia.core/state-` no longer support 2-ary overload, and made it now return `nil`.
- Made `arcadia.core/clear-state` return nil.
- Persistent representation of defmutable instances store defmutable type as `:arcadia.data/type` rather than `:arcadia.core/mutable-type`.
- Persistent representation of defmutable instances store all fields on the top level rather than in a separate `:arcadia.core/dictionary` key.
- New syntax in defmutable for extending `snapshot` and `mutable`.
- defmutable types partially implement IDictionary interface.
- `if-cmpt` no longer uses `ensure-cmpt` and avoids double-evaluation (PR [#273](https://github.com/arcadia-unity/Arcadia/pull/273), thx [pjago](https://github.com/pjago)).
- Moved several namespaces to `arcadia.internal`:
    - `arcadia.config` -> `arcadia.internal.config`
    - `arcadia.compiler` -> `arcadia.internal.compiler`
    - `arcadia.socket-repl` -> `arcadia.internal.socket-repl`
    - `arcadia.packages` -> `arcadia.internal.packages`
    - `arcadia.repl` -> `arcadia.internal.repl`
- Deleted the following namespaces:
    - `arcadia.core-examples`
    - `arcadia.scene`
- Renamed `arcadia.literals` namespace to `arcadia.data`.
- Exceptions encountered while running hooks preserve stack traces.
- `arcadia.core/state` returns `nil` for GameObjects without ArcadiaState attached.
- In documentation and parameter names, changed references to Unity "messages" to Unity "events".
- Fixed `System.MethodAccessException` bug in `arcadia.introspection`.
- Removed extraneous logging to Unity console.
- Fixed [#248](https://github.com/arcadia-unity/Arcadia/issues/248), resolved Android export issues.
- Fixed namespace qualification bugs in `arcadia.linear` Matrix functions.
- Dropped `:compiler/verbose` and `:compiler/warn-on-reflection` configuration options.
- Renamed `:compiler/options` to `:compiler-options` in configuration file.
- Added `arcadia.core/parent`.

### Contributors

Special thanks to [@pjago](https://github.com/pjago)'s pull request and the following community members for the issues they filed:

- [@Saikyun](https://github.com/Saikyun)
- [@IGJoshua](https://github.com/IGJoshua)
- [@pjago](https://github.com/pjago)
- [@selfsame](https://github.com/selfsame)
- [@srfoster](https://github.com/srfoster)
- [@acron0](https://github.com/acron0)
- [@markfarrell](https://github.com/markfarrell)
- [@aeberts](https://github.com/aeberts)
- [@gfixler](https://github.com/gfixler)
- [@Polyrhythm](https://github.com/Polyrhythm)
- [@Mechrophile](https://github.com/Mechrophile)
- [@hatmatter](https://github.com/hatmatter)
- [@LispEngineer](https://github.com/LispEngineer)
- [@djeis97](https://github.com/djeis97)
