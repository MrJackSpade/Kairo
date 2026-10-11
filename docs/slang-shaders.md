# RetroArch Slang shaders

Both Kairo98 and KairoDos expose **Graphics → RetroArch shader**. Choose
**Import shader ZIP…** and select a ZIP containing RetroArch `.slangp` presets
and their referenced `.slang` files and textures. Keep the pack's directory
structure intact so relative paths resolve. If a ZIP contains one preset it is
selected automatically; if it contains several, choose one from the list.
Select **Off** to disable it. Presets are copied into app-private storage; the
original ZIP can then be removed. Each app keeps its own selection and imports.

The common frontend owns the import UI, storage, preset selection, and
`librashader` OpenGL ES 3 runtime. Kairo98 composes its 640×400 guest frame
before filtering; KairoDos filters the software-produced RGBA frame. The
runtime handles multipass presets, referenced textures, preset parameters,
and shader includes. Device GPU limits or unsupported shader features can
prevent a particular preset from running; both apps fall back to their normal
presentation path if the filter cannot be created or rendered. Presets are
user-supplied and are not part of either APK or Play bundle.
