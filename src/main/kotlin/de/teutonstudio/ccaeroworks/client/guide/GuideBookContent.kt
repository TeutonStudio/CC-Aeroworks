package de.teutonstudio.ccaeroworks.client.guide

sealed interface GuideEntry {
    data class Text(val key: String) : GuideEntry
    data class Heading(val key: String) : GuideEntry
    data class Note(val key: String) : GuideEntry
    data class Warning(val key: String) : GuideEntry
    data class InputHint(val key: String) : GuideEntry
    data class Code(val lines: List<String>) : GuideEntry
    data class Method(val documentation: ApiMethodDocumentation) : GuideEntry
    data class Event(val documentation: ApiEventDocumentation) : GuideEntry
    data object PixelEditor : GuideEntry
}

data class GuidePage(
    val id: String,
    val labelKey: String,
    val titleKey: String,
    val entries: List<GuideEntry>,
    val keywords: Set<String> = emptySet()
)

sealed interface GuideNode {
    val id: String
    val labelKey: String

    data class Category(
        override val id: String,
        override val labelKey: String,
        val children: List<GuideNode>
    ) : GuideNode

    data class Page(
        val page: GuidePage
    ) : GuideNode {
        override val id: String get() = page.id
        override val labelKey: String get() = page.labelKey
    }
}

data class GuideBreadcrumb(val id: String, val labelKey: String)

object GuideBookContent {
    private fun page(
        id: String,
        labelKey: String,
        titleKey: String,
        vararg entries: GuideEntry,
        keywords: Set<String> = emptySet()
    ): GuideNode.Page = GuideNode.Page(GuidePage(id, labelKey, titleKey, entries.toList(), keywords))

    private fun apiPage(module: ApiModuleDocumentation): GuideNode.Page {
        val entries = buildList {
            add(GuideEntry.Text(module.summaryKey))
            module.moduleName?.let { name ->
                add(GuideEntry.Code(listOf("local ${module.id} = require(\"$name\")")))
            }
            add(GuideEntry.Heading("guide.cc_aeroworks.api.methods"))
            module.methods.forEach { add(GuideEntry.Method(it)) }
            if (module.events.isNotEmpty()) {
                add(GuideEntry.Heading("guide.cc_aeroworks.api.events"))
                module.events.forEach { add(GuideEntry.Event(it)) }
            }
        }
        return GuideNode.Page(
            GuidePage(
                id = "api/${module.id}",
                labelKey = "guide.cc_aeroworks.api.${module.id}.label",
                titleKey = "guide.cc_aeroworks.api.${module.id}.title",
                entries = entries,
                keywords = buildSet {
                    add(module.id)
                    add(module.displayName.lowercase())
                    module.moduleName?.let(::add)
                    module.methods.forEach { add(it.name.lowercase()) }
                    module.events.forEach { add(it.name.lowercase()) }
                }
            )
        )
    }

    val roots: List<GuideNode> = listOf(
        GuideNode.Category(
            "start",
            "guide.cc_aeroworks.category.start",
            listOf(
                page(
                    "start/overview",
                    "guide.cc_aeroworks.tab.start",
                    "guide.cc_aeroworks.start.title",
                    GuideEntry.Text("guide.cc_aeroworks.start.text"),
                    GuideEntry.InputHint("guide.cc_aeroworks.start.controls"),
                    GuideEntry.Note("guide.cc_aeroworks.start.note"),
                    keywords = setOf("start", "computer", "multiblock")
                ),
                page(
                    "start/computers",
                    "guide.cc_aeroworks.tab.computers",
                    "guide.cc_aeroworks.computers.title",
                    GuideEntry.Text("guide.cc_aeroworks.computers.text"),
                    GuideEntry.InputHint("guide.cc_aeroworks.computers.controls"),
                    GuideEntry.Warning("guide.cc_aeroworks.computers.warning"),
                    GuideEntry.Note("guide.cc_aeroworks.computers.note"),
                    keywords = setOf("computer", "terminal", "advanced")
                )
            )
        ),
        GuideNode.Category(
            "desks",
            "guide.cc_aeroworks.category.desks",
            listOf(
                page(
                    "desks/network",
                    "guide.cc_aeroworks.tab.network",
                    "guide.cc_aeroworks.network.title",
                    GuideEntry.Text("guide.cc_aeroworks.network.text"),
                    GuideEntry.Code(
                        listOf(
                            "local desks = peripherals.find(\"ControlDesk\")",
                            "local tree = peripherals.getTree()",
                            "local desk = desks[\"12,64,-7\"]",
                            "local modem = peripherals.find(\"endermodem\")"
                        )
                    ),
                    GuideEntry.Note("guide.cc_aeroworks.network.note"),
                    keywords = setOf("peripheral", "network", "desk", "tree")
                ),
                page(
                    "desks/modules",
                    "guide.cc_aeroworks.tab.modules",
                    "guide.cc_aeroworks.modules.title",
                    GuideEntry.Text("guide.cc_aeroworks.modules.text"),
                    GuideEntry.Code(
                        listOf(
                            "getSockets() -- left, right, big",
                            "getModules() / getModule(socket)",
                            "getInputs() / getInput(socket)",
                            "os.pullEvent(\"cc_aeroworks_desk_input\")"
                        )
                    ),
                    GuideEntry.Note("guide.cc_aeroworks.modules.note"),
                    keywords = setOf("socket", "module", "input")
                ),
                page(
                    "desks/controls",
                    "guide.cc_aeroworks.tab.controls",
                    "guide.cc_aeroworks.controls.title",
                    GuideEntry.Text("guide.cc_aeroworks.controls.text"),
                    GuideEntry.InputHint("guide.cc_aeroworks.controls.input"),
                    GuideEntry.Note("guide.cc_aeroworks.controls.note"),
                    keywords = setOf("combined", "control", "mouse", "override")
                )
            )
        ),
        GuideNode.Category(
            "displays",
            "guide.cc_aeroworks.category.displays",
            listOf(
                page(
                    "displays/raw",
                    "guide.cc_aeroworks.tab.displays",
                    "guide.cc_aeroworks.displays.title",
                    GuideEntry.Text("guide.cc_aeroworks.displays.text"),
                    GuideEntry.Code(
                        listOf(
                            "desk.setDisplayText(\"big\", \"123\")",
                            "local size = desk.getDisplaySize(\"big\")",
                            "desk.setDisplayPixel(\"big\", 1, 1, true)"
                        )
                    ),
                    GuideEntry.Note("guide.cc_aeroworks.displays.note"),
                    keywords = setOf("display", "pixel", "raw", "resolution")
                ),
                page(
                    "displays/pixel-editor",
                    "guide.cc_aeroworks.tab.pixel_editor",
                    "guide.cc_aeroworks.pixel_editor.title",
                    GuideEntry.Text("guide.cc_aeroworks.pixel_editor.text"),
                    GuideEntry.PixelEditor,
                    GuideEntry.Note("guide.cc_aeroworks.pixel_editor.note"),
                    keywords = setOf("pixel", "editor", "raster")
                ),
                GuideNode.Category(
                    "displays/reactive",
                    "guide.cc_aeroworks.category.reactive_ui",
                    listOf(
                        page(
                            "displays/reactive/intro",
                            "guide.cc_aeroworks.reactive.intro.label",
                            "guide.cc_aeroworks.reactive.intro.title",
                            GuideEntry.Text("guide.cc_aeroworks.reactive.intro.text"),
                            GuideEntry.Code(
                                listOf(
                                    "local ui = require(\"cc_aeroworks.ui\")",
                                    "return ui.app(function()",
                                    "  ui.Text(\"FUEL\")",
                                    "end)"
                                )
                            ),
                            GuideEntry.Note("guide.cc_aeroworks.reactive.intro.note"),
                            keywords = setOf("reactive", "compose", "ui", "app", "supervise")
                        ),
                        page(
                            "displays/reactive/state",
                            "guide.cc_aeroworks.reactive.state.label",
                            "guide.cc_aeroworks.reactive.state.title",
                            GuideEntry.Text("guide.cc_aeroworks.reactive.state.text"),
                            GuideEntry.Code(
                                listOf(
                                    "local selected = ui.state(\"selected\", 1)",
                                    "local percent = ui.derived(\"fuelPercent\", function()",
                                    "  local fuel = ui.telemetry.get(\"fuel\")",
                                    "  return math.floor(fuel.value.percent + 0.5)",
                                    "end)"
                                )
                            ),
                            GuideEntry.Note("guide.cc_aeroworks.reactive.state.note"),
                            keywords = setOf("state", "derived", "dependency", "telemetry", "recomposition")
                        ),
                        page(
                            "displays/reactive/layout",
                            "guide.cc_aeroworks.reactive.layout.label",
                            "guide.cc_aeroworks.reactive.layout.title",
                            GuideEntry.Text("guide.cc_aeroworks.reactive.layout.text"),
                            GuideEntry.Code(
                                listOf(
                                    "ui.Column({ padding = 2, gap = 1 }, function()",
                                    "  ui.Text({ text = \"FUEL\", width = 20 })",
                                    "  ui.ProgressBar({ value = function() return percent.get()/100 end })",
                                    "end)"
                                )
                            ),
                            GuideEntry.Note("guide.cc_aeroworks.reactive.layout.note"),
                            keywords = setOf("layout", "row", "column", "box", "draw", "phase")
                        ),
                        page(
                            "displays/reactive/navigation",
                            "guide.cc_aeroworks.reactive.navigation.label",
                            "guide.cc_aeroworks.reactive.navigation.title",
                            GuideEntry.Text("guide.cc_aeroworks.reactive.navigation.text"),
                            GuideEntry.Code(
                                listOf(
                                    "local nav = ui.navigator(\"main\", \"home\")",
                                    "ui.Button({ text=\"FUEL\", onTap=function() nav.go(\"fuel\") end })",
                                    "ui.Route(nav, { home=Home, fuel=Fuel })"
                                )
                            ),
                            keywords = setOf("navigation", "route", "controller", "boot program", "touch")
                        ),
                        page(
                            "displays/reactive/performance",
                            "guide.cc_aeroworks.reactive.performance.label",
                            "guide.cc_aeroworks.reactive.performance.title",
                            GuideEntry.Text("guide.cc_aeroworks.reactive.performance.text"),
                            GuideEntry.Code(
                                listOf(
                                    "State read in composition -> composition invalidated",
                                    "State read in layout      -> layout invalidated",
                                    "State read in draw        -> draw invalidated",
                                    "Changed draw bounds       -> dirty 64x64 tiles only"
                                )
                            ),
                            GuideEntry.Note("guide.cc_aeroworks.reactive.performance.note"),
                            keywords = setOf("performance", "tile", "dirty", "draw", "layout", "composition")
                        )
                    )
                )
            )
        ),
        GuideNode.Category(
            "data",
            "guide.cc_aeroworks.category.data",
            listOf(
                page(
                    "data/sources",
                    "guide.cc_aeroworks.data.sources.label",
                    "guide.cc_aeroworks.data.sources.title",
                    GuideEntry.Text("guide.cc_aeroworks.data.sources.text"),
                    GuideEntry.Code(
                        listOf(
                            "source value -> revision -> dependent UI scope",
                            "unchanged derived value -> no redraw",
                            "changed frame tile -> compact client patch"
                        )
                    ),
                    GuideEntry.Note("guide.cc_aeroworks.data.sources.note"),
                    keywords = setOf("source", "revision", "telemetry", "data")
                )
            )
        ),
        GuideNode.Category(
            "api",
            "guide.cc_aeroworks.category.api",
            buildList {
                add(
                    page(
                        "api/overview",
                        "guide.cc_aeroworks.api.overview.label",
                        "guide.cc_aeroworks.api.overview.title",
                        GuideEntry.Text("guide.cc_aeroworks.api.overview.text"),
                        GuideEntry.Note("guide.cc_aeroworks.api.overview.note"),
                        keywords = setOf("api", "reference", "require")
                    )
                )
                ApiDocumentationRegistry.modules.forEach { add(apiPage(it)) }
            }
        ),
        GuideNode.Category(
            "diagnostics",
            "guide.cc_aeroworks.category.diagnostics",
            listOf(
                page(
                    "diagnostics/network",
                    "guide.cc_aeroworks.tab.errors",
                    "guide.cc_aeroworks.errors.title",
                    GuideEntry.Text("guide.cc_aeroworks.errors.text"),
                    GuideEntry.Warning("guide.cc_aeroworks.errors.warning"),
                    GuideEntry.Code(
                        listOf(
                            "local network = peripherals.getNetwork()",
                            "for dependency, scopes in pairs(ui.dependencies()) do",
                            "  print(dependency, #scopes)",
                            "end"
                        )
                    ),
                    GuideEntry.Note("guide.cc_aeroworks.errors.note"),
                    keywords = setOf("error", "diagnostic", "dependency", "network")
                )
            )
        )
    )

    val pages: List<GuidePage> = roots.flatMap(::flattenPages)
    private val pagesById = pages.associateBy(GuidePage::id)

    fun page(id: String): GuidePage? = pagesById[id]

    fun firstPage(): GuidePage = pages.first()

    fun search(query: String): List<GuidePage> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotEmpty)
        if (words.isEmpty()) return emptyList()
        return pages.filter { page ->
            val haystack = buildString {
                append(page.id.lowercase())
                append(' ')
                append(page.keywords.joinToString(" ").lowercase())
                page.entries.forEach { entry ->
                    when (entry) {
                        is GuideEntry.Code -> append(' ').append(entry.lines.joinToString(" ").lowercase())
                        is GuideEntry.Method -> append(' ').append(entry.documentation.signature.lowercase())
                        is GuideEntry.Event -> append(' ').append(entry.documentation.name.lowercase())
                        else -> Unit
                    }
                }
            }
            words.all(haystack::contains)
        }
    }

    fun breadcrumbs(pageId: String): List<GuideBreadcrumb> {
        fun visit(nodes: List<GuideNode>, trail: List<GuideBreadcrumb>): List<GuideBreadcrumb>? {
            nodes.forEach { node ->
                val next = trail + GuideBreadcrumb(node.id, node.labelKey)
                when (node) {
                    is GuideNode.Page -> if (node.page.id == pageId) return next
                    is GuideNode.Category -> visit(node.children, next)?.let { return it }
                }
            }
            return null
        }
        return visit(roots, emptyList()).orEmpty()
    }

    private fun flattenPages(node: GuideNode): List<GuidePage> = when (node) {
        is GuideNode.Page -> listOf(node.page)
        is GuideNode.Category -> node.children.flatMap(::flattenPages)
    }
}
