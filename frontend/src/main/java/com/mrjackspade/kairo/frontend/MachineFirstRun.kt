package com.mrjackspade.kairo.frontend

/**
 * First-run setup on the shared setup pages: the game folder, then the product's firmware when it
 * has some. [firmware] supplies the second step's rows; a row's import shows its own progress.
 */
class MachineFirstRun(
    private val pages: PageStack,
    private val brand: String,
    private val folderStep: Folder,
    private val selectRomFolder: () -> Unit,
    private val skipRomFolder: (Boolean) -> Unit,
    private val finish: () -> Unit,
    private val firmware: Firmware?
) {
    /** Step 1's title, such as "Choose a games folder", its description, and its button. */
    class Folder(val title: UiText, val description: UiText, val button: UiText)

    /** The firmware step: a title, a description, its row groups, and whether an import runs. */
    class Firmware(val title: String, val description: String, val groups: () -> List<RowGroup>,
                   val busy: () -> Boolean)

    enum class Step { ROM_FOLDER, FIRMWARE }

    private var step = Step.ROM_FOLDER
    private var page: SetupPage? = null
    private var folder: SetupPage? = null
    val isOpen: Boolean get() = page?.inStack == true
    val isChoosingRomFolder: Boolean get() = isOpen && step == Step.ROM_FOLDER

    /** [folderChosen] says whether step 1 picked a folder, for the library afterwards. */
    fun show(next: Step, folderChosen: Boolean = false) {
        step = next
        if (next == Step.ROM_FOLDER) {
            // Back from step 2 returns to the step 1 page underneath, if there is one.
            folder?.takeIf { it.inStack }?.let { existing ->
                pages.popTo(existing)
                page = existing
                return
            }
            val created = folderPage()
            folder = created
            if (page?.inStack == true) pages.replace(created) else pages.push(created)
            page = created
            return
        }
        val created = firmwarePage(checkNotNull(firmware) { "This app has no firmware step" })
        page = created
        pages.push(created)
    }

    fun close() {
        page = null
        folder = null
        pages.closeAll()
    }

    fun refreshFirmware() {
        if (isOpen && step == Step.FIRMWARE) pages.refresh()
    }

    private fun str(id: Int): String = pages.activity.getString(id)

    private fun folderPage() = SetupPage(brand, if (firmware == null) null else str(R.string.kairo_first_run_step_1),
        folderStep.title.resolve(pages.activity), folderStep.description.resolve(pages.activity), str(R.string.kairo_first_run_folder_later),
        footerButtons = {
            listOf(FooterButton("skip", { str(R.string.kairo_first_run_skip) }) { skipRomFolder(false) },
                FooterButton("choose", { folderStep.button.resolve(pages.activity) }, ButtonTone.PRIMARY, run = selectRomFolder))
        }, onBack = { skipRomFolder(false) }, startFocus = "choose")

    private fun firmwarePage(firmware: Firmware) = SetupPage(brand, str(R.string.kairo_first_run_step_2), firmware.title,
        firmware.description, content = firmware.groups,
        footerButtons = {
            listOf(FooterButton("done", { str(R.string.kairo_first_run_to_library) }, ButtonTone.PRIMARY, { !firmware.busy() },
                str(R.string.kairo_firmware_wait)) { finish() })
        }, onBack = { if (!firmware.busy()) show(Step.ROM_FOLDER) })
}
