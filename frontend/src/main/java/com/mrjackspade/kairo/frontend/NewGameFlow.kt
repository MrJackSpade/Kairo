package com.mrjackspade.kairo.frontend

import android.app.Activity

/**
 * Runs an app's [NewGameWorkflow] (Kairo#181): one page for each of its pages shown for the choices so
 * far, then the name, each with Next (Create on the name) in its footer and Back to the page before.
 * What the player chose is kept while they go back and forth; a page shown again lists what fits
 * the choices before it. Nothing is written until Create, which writes the game on a Creating page
 * of its own ([CreatingPage], [CreateGameFlow.write]) and then closes the workflow onto the library;
 * leaving it writes nothing.
 */
class NewGameFlow<T : LibraryItem>(
    private val activity: Activity,
    private val pages: PageStack,
    private val library: LibraryFlow<T>,
    private val workflow: NewGameWorkflow,
    /** What the games folder is called, in messages. */
    private val folder: String,
    /** The app's firmware imports, for the firmware page; null for an app without firmware. */
    private val imports: FirmwareImports? = null,
    /** The workflow opened (true) or closed (false), as a two-screen device's lower screen follows it (Kairo#179). */
    private val shown: (Boolean) -> Unit = {}
) {
    private val chosen = HashMap<String, String>()
    private val values: Map<String, String> get() = workflow.resolve(chosen)
    /** The workflow's first page, which Create closes along with every page above it. */
    private var first: Page? = null

    /** Starts the workflow, from the beginning. */
    fun open() {
        val tree = library.tree
        if (tree == null) {
            Ui.message(activity, activity.getString(R.string.kairo_library_choose_in_manage))
            return
        }
        if (!library.hasWriteGrant(tree)) {
            pages.push(ModalPage(null, activity.getString(R.string.kairo_create_grant, folder), listOf(
                ModalButton(activity.getString(R.string.kairo_cancel)),
                ModalButton(activity.getString(R.string.kairo_choose_named, folder), ButtonTone.PRIMARY) { library.chooseFolder() }),
                initialButton = 1))
            return
        }
        chosen.clear()
        first = null
        show(0)
    }

    private fun show(index: Int) {
        val shown = workflow.shownPages(values)
        // After the choices, the firmware they need that the player has not imported (KairoX86#438), then the name
        val missing = if (index == shown.size) workflow.firmware(values).filterNot { it.set.installed(it.part) } else emptyList()
        val page = when {
            index < shown.size -> choicesPage(shown[index], index)
            missing.isNotEmpty() -> firmwarePage(missing, index)
            else -> NamePage()
        }
        if (first == null) {
            first = page
            this.shown(true)
        }
        pages.push(page)
    }

    /** The label of a step's primary button: Next, or Create on the last. */
    private fun nextLabel() = activity.getString(R.string.kairo_next)

    private fun choicesPage(page: WorkflowPage, index: Int): Page = StepPage(page.title.resolve(activity), {
        val now = values
        listOf(RowGroup(null, page.fields.mapNotNull { field -> row(field, now) }))
    }, { listOf(FooterButton("next", ::nextLabel, ButtonTone.PRIMARY) { show(index + 1) }) })

    /**
     * The firmware page (KairoX86#438): what the choices need that the player has not imported, each
     * file in its own group: what it is, its name, where it goes in the set and its size, then a row to
     * choose it, which says whether it is there; what it does and why it is needed below. Last, the
     * folder import, for a whole set at once. Next goes on to the name once every file is there.
     */
    private fun firmwarePage(needs: List<FirmwareNeed>, index: Int): Page {
        val imports = checkNotNull(imports) { "The New game workflow needs firmware, but the app imports none" }
        val idle = { !imports.busy() }
        val wait = { activity.getString(R.string.kairo_firmware_wait) }
        return StepPage(activity.getString(R.string.kairo_new_game_firmware_title), {
            val files = needs.mapIndexed { i, need ->
                val part = need.part
                val name = part.name.substringAfterLast('/')
                val place = part.name.substringBeforeLast('/', "")
                RowGroup(part.label.resolve(activity), listOfNotNull(
                    SettingsRow.info("new-firmware$i-name", activity.getString(R.string.kairo_new_game_firmware_file_name)) { name },
                    place.takeIf { it.isNotEmpty() }?.let {
                        SettingsRow.info("new-firmware$i-folder", activity.getString(R.string.kairo_new_game_firmware_folder)) { it }
                    },
                    part.sizes.singleOrNull()?.let { size ->
                        SettingsRow.info("new-firmware$i-size", activity.getString(R.string.kairo_new_game_firmware_size)) {
                            FirmwareSet.sizeText(activity, size)
                        }
                    },
                    SettingsRow.action("new-firmware$i-choose",
                        activity.getString(if (need.set.installed(part)) R.string.kairo_firmware_replace else R.string.kairo_choose_file),
                        value = { activity.getString(if (need.set.installed(part)) R.string.kairo_installed else R.string.kairo_missing) },
                        tone = { if (need.set.installed(part)) ValueTone.CURRENT else ValueTone.SUMMARY },
                        busy = imports.busy, enabled = idle, disabledReason = wait) { imports.pickFile(need.set, part) }),
                    part.description.resolve(activity))
            }
            val folder = SettingsRow.action("new-firmware-folder", activity.getString(R.string.kairo_firmware_import_folder),
                busy = imports.busy, enabled = idle, disabledReason = wait) { imports.pickFolder() }
            // The folder import's note: the set's own (which collection it is), else what the import does;
            // then the project the set belongs to
            val set = needs.first().set
            val folderNote = set.note?.resolve(activity) ?: activity.getString(R.string.kairo_firmware_import_folder_description)
            val project = set.project?.let { link ->
                SettingsRow.action("new-firmware-project", link.label.resolve(activity)) { link.open(activity) { Ui.message(activity, it) } }
            }
            listOf(RowGroup(null, emptyList(), activity.getString(R.string.kairo_new_game_firmware_intro))) + files +
                RowGroup(null, listOfNotNull(folder, project), folderNote)
        }, {
            listOf(FooterButton("next", ::nextLabel, ButtonTone.PRIMARY, { needs.all { it.set.installed(it.part) } },
                activity.getString(R.string.kairo_new_game_firmware_missing)) { show(index + 1) })
        })
    }

    /** The workflow's first page went (Back from it, or Create): the workflow is over. */
    private fun removed(page: Page) {
        if (page !== first) return
        first = null
        shown(false)
    }

    private inner class StepPage(title: String, content: () -> List<RowGroup>, footer: () -> List<FooterButton>) :
        GroupsPage(title, { null }, content, footer) {
        override fun onRemoved() = removed(this)
    }

    private fun row(field: WorkflowField, now: Map<String, String>): SettingsRow? = when (field) {
        is WorkflowField.Select -> choiceRow(field.id, field.label, { field.choices(values) }, field.custom(now))
        is WorkflowField.Range -> choiceRow(field.id, field.label, {
            field.range(values).values.map { ChoiceOption(it.toString(), field.labelOf(it)) }
        }, field.custom(now))
        // Text is typed on a page of its own, where the keyboard leaves it in view: only the name page has one.
        is WorkflowField.Text -> error("${field.id}: a text field goes on the name page only")
    }.takeIf { now.containsKey(field.id) }

    private fun choiceRow(id: String, label: UiText, options: () -> List<ChoiceOption>, custom: CustomValue?): SettingsRow {
        val title = label.resolve(activity)
        return SettingsRow.choice("new-$id", title, ChoiceSpec(title, { null }, { groups(options()) }, { values[id] }, select = { picked ->
            chosen[id] = picked
            pages.refresh()
        }, custom = custom))
    }

    /**
     * Options in folders (a processor's family) open as folders; the rest list as they are, under
     * their sections' labels (KairoX86#335) in the order the sections first come.
     */
    private fun groups(options: List<ChoiceOption>): List<ChoiceGroup> {
        val folders = options.mapNotNull { it.folder }.distinct().map { name ->
            ChoiceFolder(name, name) { listOf(ChoiceGroup(null, options.filter { it.folder == name })) }
        }
        val sections = options.filter { it.folder == null }.groupBy { it.section }
        if (sections.isEmpty()) return listOf(ChoiceGroup(null, emptyList(), null, folders))
        return sections.entries.mapIndexed { index, (section, listed) ->
            ChoiceGroup(section?.resolve(activity), listed, null, if (index == 0) folders else emptyList())
        }
    }

    /**
     * Writes the game the choices make, named [name] (checked by the name page), on the Creating
     * page (Kairo#184); once it is written the workflow closes onto the library, which finds it. A
     * failure stays on that page, and Back returns to the name with every choice kept. An empty drive
     * writes nothing: what is kept for the game goes first, then the drive starts with it.
     */
    private fun create(name: String) {
        chosen[NewGameWorkflow.NAME] = name
        val blank = workflow.create(values)
        val made = values
        if (blank is BlankDisk.Drive) {
            first?.let(pages::popThrough)
            workflow.created(made, blank.path(name))
            blank.start(name)
            return
        }
        val tree = checkNotNull(library.tree) { activity.getString(R.string.kairo_library_choose_in_manage) }
        pages.push(CreatingPage(activity, name, { progress -> CreateGameFlow.write(activity, tree, blank, name, folder, progress) }) { path ->
            first?.let(pages::popThrough)
            workflow.created(made, path)
            library.screen.showStatus(activity.getString(R.string.kairo_created, name), LibraryScreen.StatusKind.SUMMARY)
            library.refresh(ScanDepth.EVERY_FOLDER, announce = true)
        })
    }

    /** The name: the shared text page (its field at the top, in view above the keyboard), Create in its footer. */
    private inner class NamePage : TextEntryPage(activity.getString(R.string.kairo_create_name_title),
        chosen[NewGameWorkflow.NAME] ?: activity.getString(R.string.kairo_create_name_default),
        activity.getString(R.string.kairo_create_name_hint), numeric = false, button = activity.getString(R.string.kairo_create),
        fieldId = "new-game.name",
        problem = { name -> if (CreateGameFlow.usable(name)) null else activity.getString(R.string.kairo_create_bad_name) },
        done = { name -> create(name) }) {
        override val testName: String get() = "new-game-name"
        override fun onRemoved() {
            super.onRemoved()
            removed(this)
        }
    }
}
