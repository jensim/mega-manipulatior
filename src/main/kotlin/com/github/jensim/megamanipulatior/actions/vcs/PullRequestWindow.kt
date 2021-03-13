package com.github.jensim.megamanipulatior.actions.vcs

import com.github.jensim.megamanipulatior.settings.SerializationHolder
import com.github.jensim.megamanipulatior.toolswindow.ToolWindowTab
import com.github.jensim.megamanipulatior.ui.CodeHostSelector
import com.github.jensim.megamanipulatior.ui.GeneralListCellRenderer.addCellRenderer
import com.github.jensim.megamanipulatior.ui.uiProtectedOperation
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.layout.panel
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.JButton
import javax.swing.JComponent

object PullRequestWindow : ToolWindowTab {

    override val index: Int = 4

    private val codeHostSelect = CodeHostSelector()
    private val prList = JBList<PullRequest>()
    private val prScroll = JBScrollPane(prList)
    private val peekArea = JBTextArea()
    private val peekScroll = JBScrollPane(peekArea)
    private val menu = PullRequestActionsMenu(prProvider = { prList.selectedValuesList }, postActionHook = this::refresh)
    private val menuOpenButton = JButton("Actions")
    override val content: JComponent = panel {
        row {
            component(codeHostSelect)
            button("Fetch PRs") {
                fetchPRs()
            }
            right {
                component(menuOpenButton)
            }
        }
        row {
            component(prScroll)
            component(peekScroll)
        }
    }

    init {
        menuOpenButton.isEnabled = false
        menuOpenButton.addMouseListener(object : MouseListener {
            override fun mouseClicked(e: MouseEvent) {
                val selected = codeHostSelect.selectedItem
                menu.codeHostName = selected?.codeHostName
                menu.searchHostName = selected?.searchHostName
                menu.show(menuOpenButton, e.x, e.y)
            }

            override fun mousePressed(e: MouseEvent?) = Unit
            override fun mouseReleased(e: MouseEvent?) = Unit
            override fun mouseEntered(e: MouseEvent?) = Unit
            override fun mouseExited(e: MouseEvent?) = Unit
        })

        prList.addCellRenderer { "${it.project()}/${it.repo()} ${it.title()}" }
        prList.addListSelectionListener {
            menuOpenButton.isEnabled = false
            prList.selectedValuesList.firstOrNull()?.let {
                peekArea.text = SerializationHolder.yamlObjectMapper.writeValueAsString(it)
                menuOpenButton.isEnabled = true
            }
        }
    }

    override fun refresh() {
        peekArea.text = ""
        prList.setListData(emptyArray())
        codeHostSelect.load()
        fetchPRs()
    }

    private fun fetchPRs() {
        prList.setListData(emptyArray())
        (codeHostSelect.selectedItem)?.let { selected ->
            val prs: List<PullRequest>? = uiProtectedOperation("Fetching PRs") {
                PrRouter.getAllPrs(selected.searchHostName, selected.codeHostName)
            }
            prList.setListData(prs?.toTypedArray())
        }
    }
}
