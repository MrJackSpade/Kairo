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
    /** Step 1's title, such as "Choose a ROM folder", its description, and its button. */
    class Folder(val title: String, val description: String, val button: String)

    /** The firmware step: a title, a description, its rows and their footnote, and whether an import runs. */
    class Firmware(val title: String, val description: String, val rows: () -> List<SettingsRow>,
                   val footnote: String, val busy: () -> Boolean)

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

    private fun folderPage() = SetupPage(brand, if (firmware == null) null else "STEP 1 OF 2", folderStep.title,
        folderStep.description, "You can choose or change it later in Menu › Manage library.",
        footerButtons = {
            listOf(FooterButton("skip", { "Skip for now" }) { skipRomFolder(false) },
                FooterButton("choose", { folderStep.button }, ButtonTone.PRIMARY, run = selectRomFolder))
        }, onBack = { skipRomFolder(false) }, startFocus = "choose")

    private fun firmwarePage(firmware: Firmware) = SetupPage(brand, "STEP 2 OF 2", firmware.title, firmware.description,
        content = { listOf(RowGroup("FIRMWARE", firmware.rows(), firmware.footnote)) },
        footerButtons = {
            listOf(FooterButton("done", { "Continue to library" }, ButtonTone.PRIMARY, { !firmware.busy() },
                "Wait for the current import to finish") { finish() })
        }, onBack = { if (!firmware.busy()) show(Step.ROM_FOLDER) })
}
