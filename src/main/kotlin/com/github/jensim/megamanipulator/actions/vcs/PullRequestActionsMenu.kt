package com.github.jensim.megamanipulator.actions.vcs

import com.github.jensim.megamanipulator.actions.NotificationsOperator
import com.github.jensim.megamanipulator.actions.git.clone.CloneOperator
import com.github.jensim.megamanipulator.ui.DialogGenerator
import com.github.jensim.megamanipulator.ui.EditPullRequestDialog
import com.github.jensim.megamanipulator.ui.UiProtector
import com.intellij.notification.NotificationType
import java.awt.Desktop
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JOptionPane.CANCEL_OPTION
import javax.swing.JOptionPane.OK_CANCEL_OPTION
import javax.swing.JOptionPane.OK_OPTION
import javax.swing.JOptionPane.QUESTION_MESSAGE
import javax.swing.JPopupMenu

@SuppressWarnings("LongParameterList")
class PullRequestActionsMenu(
    private val prRouter: PrRouter,
    private val notificationsOperator: NotificationsOperator,
    private val dialogGenerator: DialogGenerator,
    private val cloneOperator: CloneOperator,
    private val uiProtector: UiProtector,
) : JPopupMenu() {

    var prProvider: () -> List<PullRequestWrapper> = { emptyList() }
    var postActionHook: () -> Unit = {}
    var codeHostName: String? = null
    var searchHostName: String? = null

    init {
        val declineMenuItem = JMenuItem("Decline PRs").apply {
            addActionListener { _ ->
                if (dialogGenerator.showConfirm("Decline selected PRs", "No undo path available im afraid..\nDecline selected PRs?")) {
                    val dropBranchesAns = JOptionPane.showConfirmDialog(null, "Drop source branches?", "Drop branches?", OK_CANCEL_OPTION, QUESTION_MESSAGE, null)
                    if (!listOf(OK_OPTION, CANCEL_OPTION).contains(dropBranchesAns)) return@addActionListener
                    val dropForksAns = JOptionPane.showConfirmDialog(null, "Also drop source forks?", "Drop forks?", OK_CANCEL_OPTION, QUESTION_MESSAGE, null)
                    if (!listOf(OK_OPTION, CANCEL_OPTION).contains(dropForksAns)) return@addActionListener
                    uiProtector.mapConcurrentWithProgress(
                        title = "Declining prs",
                        extraText2 = { "${it.codeHostName()}/${it.project()}/${it.baseRepo()} ${it.fromBranch()}" },
                        data = prProvider(),
                    ) { pullRequest: PullRequestWrapper ->
                        prRouter.closePr(dropFork = dropForksAns == OK_OPTION, dropBranch = dropBranchesAns == OK_OPTION, pullRequest)
                    }
                    postActionHook()
                }
            }
        }
        val alterMenuItem = JMenuItem("Reword PRs").apply {
            addActionListener {
                val prs = prProvider()
                if (prs.isEmpty()) {
                    notificationsOperator.show("No PRs selected", "Please select at least one PR to use this", NotificationType.WARNING)
                } else {
                    val dialog = EditPullRequestDialog(prs)
                    if (dialog.showAndGet()) {
                        uiProtector.mapConcurrentWithProgress(
                            title = "Reword PRs",
                            extraText1 = "Setting new title and body for Pull requests",
                            extraText2 = { "${it.codeHostName()}/${it.project()}/${it.baseRepo()} ${it.fromBranch()}" },
                            data = prs,
                        ) { pr ->
                            prRouter.updatePr(dialog.prTitle!!, dialog.prDescription!!, pr)
                        }
                        postActionHook()
                    } else {
                        notificationsOperator.show("No PRs edited", "Cancelled or missing data")
                    }
                }
            }
        }
        val defaultReviewersMenuItem = JMenuItem("Add default reviewers").apply {
            addActionListener { _ ->
                if (dialogGenerator.showConfirm(title = "Add default reviewers", message = "Add default reviewers")) {
                    uiProtector.mapConcurrentWithProgress(
                        title = "Add default reviewers",
                        extraText2 = { "${it.codeHostName()}/${it.project()}/${it.baseRepo()} ${it.fromBranch()}" },
                        data = prProvider(),
                    ) { pr ->
                        val codeHostName = codeHostName
                        val searchHostName = searchHostName
                        if (codeHostName == null || searchHostName == null) {
                            return@mapConcurrentWithProgress
                        }
                        prRouter.addDefaultReviewers(pr)
                    }
                    postActionHook()
                }
            }
        }
        val cloneMenuItem = JMenuItem("Clone PRs").apply {
            addActionListener {
                val prs = prProvider()
                if (dialogGenerator.showConfirm(title = "Clone...", message = "Clone ${prs.size} selected PR branches")) {
                    cloneOperator.clone(prs)
                }
            }
        }
        val openInBrowserMenuItem = JMenuItem("Open in browser").apply {
            addActionListener {
                val failed = mutableMapOf<PullRequestWrapper, String>()
                val prs = prProvider()
                prs.forEach { prWrapper ->
                    val browseUrl = prWrapper.browseUrl()
                    if (browseUrl == null) {
                        failed[prWrapper] = "Missing BrowseURL"
                    } else {
                        try {
                            com.intellij.ide.BrowserUtil.browse(browseUrl)
                        } catch (e: Exception) {
                            failed[prWrapper] = "Exception opening link ${e.javaClass.name} ${e.message}"
                        }
                    }
                }
                if (failed.isNotEmpty()) {
                    val failMsg = failed.map { (k, v) -> "${k.project()}/${k.baseRepo()} ${k.title().take(10)} :: $v" }
                        .joinToString("\n")
                    notificationsOperator.show(
                        title = "Failed opening ${failed.size}/${prs.size} pull requests",
                        body = failMsg,
                        type = NotificationType.ERROR
                    )
                }
            }
        }

        val commentMenuItem = JMenuItem("Add comment").apply {
            addActionListener {
                val prs = prProvider()
                if (prs.isNotEmpty()) {
                    val comment = dialogGenerator.askForInput(
                        title = "Comment selected pull requests",
                        message = "Comment"
                    )
                    comment?.let { comment ->
                        uiProtector.mapConcurrentWithProgress(
                            title = "Add comments",
                            data = prs
                        ) {
                            prRouter.commentPR(comment, it)
                        }
                    }
                }
            }
        }

        add(declineMenuItem)
        add(alterMenuItem)
        add(defaultReviewersMenuItem)
        add(cloneMenuItem)
        if (isBrowsingAllowed()) {
            add(openInBrowserMenuItem)
        }
        add(commentMenuItem)
    }

    private fun isBrowsingAllowed(): Boolean = try {
        Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)
    } catch (e: java.awt.HeadlessException) {
        false
    }
}
