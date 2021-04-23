package com.github.jensim.megamanipulator

import com.github.jensim.megamanipulator.settings.CodeHostSettings
import com.github.jensim.megamanipulator.settings.ForkSetting
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.Properties

object TestHelper {
    const val MEGA_MANIPULATOR_REPO = "mega-manipulator"
    const val GITHUB_TOKEN = "GITHUB_TOKEN"
    private const val GITHUB_USERNAME = "GITHUB_USERNAME"

    val githubCredentials: CodeHostSettings.GitHubSettings by lazy {
        val prop = Properties()
        try {
            val fis = FileInputStream(".env")
            prop.load(fis)
            val username = prop.getProperty(GITHUB_USERNAME)
            val token = prop.getProperty(GITHUB_TOKEN)
                ?: throw RuntimeException("To run the test you must to provide a github token")
            System.setProperty(GITHUB_TOKEN, token)
            CodeHostSettings.GitHubSettings(
                username = username,
                forkSetting = ForkSetting.PLAIN_BRANCH,
            )
        } catch (e: FileNotFoundException) {
            val env = System.getenv()
            val username = env[GITHUB_USERNAME]
            env[GITHUB_TOKEN]
                ?: throw RuntimeException("To run the test you must to provide a github token")
            CodeHostSettings.GitHubSettings(
                username = username ?: throw RuntimeException("To run this test you must to provide a username"),
                forkSetting = ForkSetting.PLAIN_BRANCH,
            )
        }
    }
}
