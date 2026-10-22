package com.mrjackspade.kairo.frontend

/**
 * First-run setup on the shared setup pages: the ROM folder, then the product's firmware.
 * [firmware] supplies the second step's rows; a row's import shows its own progress.
 */
class MachineFirstRun(
    private val pages: PageStack,
    private val brand: String,
    private val appName: String,
    private val guestName: String,
    /** What step 1 scans for, such as "disk images and ZIP files". */
    private val media: String,
    private val selectRomFolder: () -> Unit,
    private val skipRomFolder: (Boolean) -> Unit,
    private val finish: () -> Unit,
    private val firmware: Firmware
) {
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
        val created = firmwarePage()
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

    private fun folderPage() = SetupPage(brand, "STEP 1 OF 2", "Choose a ROM folder",
        "Select the folder containing your $guestName games. $appName will scan its $media.",
        "You can choose or change it later in Menu › Manage library.",
        footerButtons = {
            listOf(FooterButton("skip", { "Skip for now" }) { skipRomFolder(false) },
                FooterButton("choose", { "Choose ROM folder…" }, ButtonTone.PRIMARY, run = selectRomFolder))
        }, onBack = { skipRomFolder(false) }, startFocus = "choose")

    private fun firmwarePage() = SetupPage(brand, "STEP 2 OF 2", firmware.title, firmware.description,
        content = { listOf(RowGroup("FIRMWARE", firmware.rows(), firmware.footnote)) },
        footerButtons = {
            listOf(FooterButton("done", { "Continue to library" }, ButtonTone.PRIMARY, { !firmware.busy() },
                "Wait for the current import to finish") { finish() })
        }, onBack = { if (!firmware.busy()) show(Step.ROM_FOLDER) })
}
