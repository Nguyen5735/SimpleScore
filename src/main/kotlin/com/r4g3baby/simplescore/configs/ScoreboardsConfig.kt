package com.r4g3baby.simplescore.configs

import com.r4g3baby.simplescore.SimpleScore
import com.r4g3baby.simplescore.scoreboard.models.ScoreLine
import com.r4g3baby.simplescore.scoreboard.models.Scoreboard
import com.r4g3baby.simplescore.utils.configs.ConfigFile
import org.bukkit.configuration.ConfigurationSection

class ScoreboardsConfig(plugin: SimpleScore, updateTime: Int) : ConfigFile(plugin, "scoreboards") {
    val scoreboards = HashMap<String, Scoreboard>()

    init {
        for (scoreboard in config.getKeys(false).filter { !scoreboards.containsKey(it.lowercase()) }) {
            if (config.isConfigurationSection(scoreboard)) {
                val scoreboardSec = config.getConfigurationSection(scoreboard)

                val titles = ScoreLine()
                scoreboardSec.getList("titles").forEach {
                    when (it) {
                        is String -> titles.add(it, updateTime)
                        is Map<*, *> -> titles.add(it["text"] as String, it["time"] as Int)
                        else -> {
                            plugin.logger.warning("Failed to parse titles expected String or Map but got ${it!!::class.java} instead.")
                        }
                    }
                }

                val scores = HashMap<Int, ScoreLine>()
                if (scoreboardSec.isConfigurationSection("scores")) {
                    val scoresSec = scoreboardSec.getConfigurationSection("scores")
                    for (score in scoresSec.getKeys(false).filter { !scores.containsKey(it.toInt()) }) {
                        val scoreLine = ScoreLine()
                        scoresSec.getList(score).forEach {
                            when (it) {
                                is String -> scoreLine.add(it, updateTime)
                                is Map<*, *> -> scoreLine.add(it["text"] as String, it["time"] as Int)
                                else -> {
                                    plugin.logger.warning("Failed to parse score \"$score\" expected String or Map but got ${it!!::class.java} instead.")
                                }
                            }
                        }
                        scores[score.toInt()] = scoreLine
                    }
                }

                val name = scoreboard.lowercase()
                val permission = scoreboardSec.getString("permission", "")
                scoreboards[name] = Scoreboard(name, titles, scores, permission)
            }
        }
    }

    internal fun saveOldScoreboards(section: ConfigurationSection) {
        for (scoreboard in section.getKeys(false)) {
            config.set(scoreboard, section.getConfigurationSection(scoreboard))

            config.getConfigurationSection(scoreboard).apply {
                if (getBoolean("Restricted", false)) {
                    set("permission", scoreboard.lowercase())
                    set("Restricted", null)
                }

                set("titles", get("Titles"))
                set("Titles", null)

                set("scores", get("Scores"))
                set("Scores", null)
            }
        }
        config.save(this)
    }
}