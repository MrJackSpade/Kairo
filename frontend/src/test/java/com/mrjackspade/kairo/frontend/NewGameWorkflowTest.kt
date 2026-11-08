package com.mrjackspade.kairo.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class NewGameWorkflowTest {
    private fun text(value: String) = UiText.fixed(value)
    private fun option(id: String, disabled: String? = null) = ChoiceOption(id, text(id), null, disabled?.let(::text))

    // A machine, then the processors it takes (a 386 board only the 386s), then memory from what the machine allows.
    private val processors = mapOf("386board" to listOf("386sx", "386dx"), "486board" to listOf("486sx", "486dx", "486dx2"))
    private val machine = WorkflowField.Select("machine", text("Machine"), { listOf(option("386board"), option("486board")) }, { "486board" })
    private val processor = WorkflowField.Select("processor", text("Processor"),
        { values -> processors.getValue(values.getValue("machine")).map { option(it) } }, { "486dx2" })
    private val memory = WorkflowField.Range("memory", text("Memory"),
        { values -> if (values["machine"] == "386board") WorkflowField.Range.Steps(1, 16, 1) else WorkflowField.Range.Steps(4, 64, 4) },
        { 32 }, { text("$it MB") })
    private val workflow = NewGameWorkflow(listOf(
        WorkflowPage("machine", text("Machine"), listOf(machine)),
        WorkflowPage("hardware", text("Hardware"), listOf(processor, memory)))) { error("not created") }

    @Test
    fun defaultsAreTakenWhereNothingIsChosen() {
        assertEquals(mapOf("machine" to "486board", "processor" to "486dx2", "memory" to "32"), workflow.resolve(emptyMap()))
    }

    @Test
    fun aLaterFieldOffersOnlyWhatFitsTheChoicesBeforeIt() {
        val values = workflow.resolve(mapOf("machine" to "386board"))
        assertEquals(listOf("386sx", "386dx"), processor.choices(values).map { it.id })
        // The default processor (486dx2) and memory (32) do not fit the 386 board: its first processor, and memory's minimum
        assertEquals("386sx", values["processor"])
        assertEquals("1", values["memory"])
    }

    @Test
    fun aChoiceIsKeptWhileItFitsAndDroppedWhenAnEarlierChoiceRulesItOut() {
        assertEquals("486sx", workflow.resolve(mapOf("processor" to "486sx"))["processor"])
        assertEquals("386sx", workflow.resolve(mapOf("machine" to "386board", "processor" to "486sx"))["processor"])
        // Going back to the 486 board brings the player's 486 choice back
        assertEquals("486sx", workflow.resolve(mapOf("machine" to "486board", "processor" to "486sx"))["processor"])
    }

    @Test
    fun aRangeKeepsOnlyItsSteps() {
        assertEquals("8", workflow.resolve(mapOf("memory" to "8"))["memory"])
        assertEquals("32", workflow.resolve(mapOf("memory" to "6"))["memory"])
        assertEquals((4..64 step 4).toList(), memory.range(workflow.resolve(emptyMap())).values)
    }

    @Test
    fun aChoiceThatCannotBeTakenIsNeverTheValue() {
        val select = WorkflowField.Select("kind", text("Kind"), { listOf(option("a", disabled = "no firmware"), option("b")) }, { "a" })
        val values = NewGameWorkflow(listOf(WorkflowPage("kind", text("Kind"), listOf(select)))) { error("not created") }
            .resolve(mapOf("kind" to "a"))
        assertEquals("b", values["kind"])
    }

    @Test
    fun aPageNotShownKeepsNoValues() {
        val disk = WorkflowField.Range("disk", text("Hard disk"), { WorkflowField.Range.Steps(10, 40, 10) }, { 20 }, { text("$it MB") })
        val pages = NewGameWorkflow(listOf(
            WorkflowPage("machine", text("Machine"), listOf(machine)),
            WorkflowPage("disk", text("Hard disk"), listOf(disk)) { it["machine"] == "486board" })) { error("not created") }
        assertEquals("20", pages.resolve(emptyMap())["disk"])
        assertFalse(pages.resolve(mapOf("machine" to "386board")).containsKey("disk"))
        assertEquals(listOf("machine"), pages.shownPages(mapOf("machine" to "386board")).map { it.id })
    }

    @Test
    fun theNameIsKeptAsGivenAndIsNoPagesField() {
        assertEquals("My PC", workflow.resolve(mapOf(NewGameWorkflow.NAME to "My PC"))[NewGameWorkflow.NAME])
        assertThrows(IllegalArgumentException::class.java) {
            NewGameWorkflow(listOf(WorkflowPage("p", text("P"), listOf(WorkflowField.Text(NewGameWorkflow.NAME, text("Name"), { "" }, { null }))))) {
                error("not created")
            }
        }
    }

    @Test
    fun anAppsBlankDisksAreAKindPage() {
        val blanks = listOf(BlankDisk.Image("fd", text("Floppy"), "img") {}, BlankDisk.Image("hd", text("Hard disk"), "hdi") {})
        val workflow = NewGameWorkflow.ofBlankDisks(blanks, text("New game"), text("Kind"))
        val values = workflow.resolve(mapOf(NewGameWorkflow.KIND to "hd", NewGameWorkflow.NAME to "Mine"))
        assertEquals("hd", (workflow.create(values) as BlankDisk.Image).id)
        assertEquals("fd", workflow.resolve(emptyMap())[NewGameWorkflow.KIND])
    }
}
