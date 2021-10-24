package com.r4g3baby.simplescore.scoreboard

import com.r4g3baby.simplescore.SimpleScore
import com.r4g3baby.simplescore.scoreboard.handlers.BukkitScoreboard
import com.r4g3baby.simplescore.scoreboard.handlers.ProtocolScoreboard
import com.r4g3baby.simplescore.scoreboard.handlers.ScoreboardHandler
import com.r4g3baby.simplescore.scoreboard.listeners.McMMOListener
import com.r4g3baby.simplescore.scoreboard.listeners.PlayersListener
import com.r4g3baby.simplescore.scoreboard.models.PlayerData
import com.r4g3baby.simplescore.scoreboard.models.Scoreboard
import com.r4g3baby.simplescore.scoreboard.placeholders.ScoreboardExpansion
import com.r4g3baby.simplescore.scoreboard.tasks.ScoreboardTask
import com.r4g3baby.simplescore.utils.configs.ConfigFile
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.*

class ScoreboardManager {
    private val scoreboardHandler: ScoreboardHandler

    val scoreboards: Scoreboards = Scoreboards()
    val playersData: PlayersData = PlayersData()

    init {
        Bukkit.getPluginManager().apply {
            registerEvents(PlayersListener(), SimpleScore.plugin)
            if (Bukkit.getPluginManager().isPluginEnabled("mcMMO")) {
                registerEvents(McMMOListener(), SimpleScore.plugin)
            }
        }

        if (SimpleScore.usePlaceholderAPI) {
            ScoreboardExpansion(SimpleScore.plugin).register()
        }

        scoreboardHandler = if (!SimpleScore.config.forceLegacy) {
            if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
                ProtocolScoreboard()
            } else BukkitScoreboard()
        } else BukkitScoreboard()

        ScoreboardTask().runTaskTimerAsynchronously(SimpleScore.plugin, 20L, 1L)
    }

    fun reload() {
        scoreboards.clearCache()
        if (!SimpleScore.config.savePlayerData) {
            playersData.clearPlayerData()
        }
        Bukkit.getOnlinePlayers().forEach { clearScoreboard(it) }
    }

    fun createScoreboard(player: Player) {
        if (!playersData.get(player).isDisabled) {
            scoreboardHandler.createScoreboard(player)
        }
    }

    fun removeScoreboard(player: Player) {
        scoreboardHandler.removeScoreboard(player)
    }

    fun hasScoreboard(player: Player): Boolean {
        return scoreboardHandler.hasScoreboard(player)
    }

    fun clearScoreboard(player: Player) {
        scoreboardHandler.clearScoreboard(player)
    }

    fun updateScoreboard(title: String, scores: Map<Int, String>, player: Player) {
        val playerData = playersData.get(player)
        if (!playerData.isHidden && !playerData.isDisabled) {
            scoreboardHandler.updateScoreboard(title, scores, player)
        }
    }

    fun hasLineLengthLimit(): Boolean {
        return scoreboardHandler.hasLineLengthLimit()
    }

    class Scoreboards : Iterable<Map.Entry<String, Scoreboard>> {
        private val worldScoreboardsCache = HashMap<String, List<Scoreboard>>()

        fun get(scoreboard: String): Scoreboard? {
            return SimpleScore.config.scoreboards[scoreboard.lowercase()]
        }

        fun getForWorld(world: World) = getForWorld(world.name)
        fun getForWorld(world: String): List<Scoreboard> {
            return worldScoreboardsCache.computeIfAbsent(world) {
                mutableListOf<Scoreboard>().also { list ->
                    SimpleScore.config.worlds.forEach { (predicate, scoreboards) ->
                        if (predicate.test(world)) {
                            scoreboards.mapNotNull { get(it) }.forEach { scoreboard ->
                                list.add(scoreboard)
                            }
                            return@forEach
                        }
                    }
                }.toList()
            }
        }

        fun clearCache() {
            worldScoreboardsCache.clear()
        }

        override fun iterator(): Iterator<Map.Entry<String, Scoreboard>> {
            return SimpleScore.config.scoreboards.asIterable().iterator()
        }
    }

    class PlayersData {
        private val playersData = HashMap<UUID, PlayerData>()
        private val playersDataFile: ConfigFile by lazy {
            ConfigFile(SimpleScore.plugin, "playersData").apply {
                config.options().header("This file is generated by the plugin to store player information.")
            }
        }

        init {
            if (SimpleScore.config.savePlayerData) {
                SimpleScore.plugin.logger.info("Loading player data...")

                playersDataFile.config.apply {
                    getKeys(false).forEach { uniqueId ->
                        val playerSection = getConfigurationSection(uniqueId)
                        val isHidden = playerSection.getBoolean("isHidden", false)
                        val isDisabled = playerSection.getBoolean("isDisabled", false)
                        val scoreboard = playerSection.getString("scoreboard", null)?.let { scoreboard ->
                            SimpleScore.config.scoreboards[scoreboard.lowercase()]
                        }

                        playersData[UUID.fromString(uniqueId)] = PlayerData(
                            if (isHidden) mutableSetOf(SimpleScore.plugin) else mutableSetOf(),
                            if (isDisabled) mutableSetOf(SimpleScore.plugin) else mutableSetOf(),
                            if (scoreboard != null) LinkedHashMap(
                                mapOf(SimpleScore.plugin to scoreboard)
                            ) else LinkedHashMap()
                        )
                    }
                }

                SimpleScore.plugin.logger.info("Player data loaded.")
            }
        }

        fun save() {
            if (SimpleScore.config.savePlayerData) {
                SimpleScore.plugin.logger.info("Saving player data...")

                playersDataFile.config.apply {
                    // Clear current player data
                    getKeys(false).forEach { set(it, null) }

                    playersData.forEach { (uniqueId, playerData) ->
                        val isHidden = playerData.isHiding(SimpleScore.plugin)
                        val isDisabled = playerData.isDisabling(SimpleScore.plugin)
                        val scoreboard = playerData.getScoreboard(SimpleScore.plugin)

                        if (isHidden || isDisabled || scoreboard != null) {
                            createSection(
                                uniqueId.toString(), mapOf(
                                    "isHidden" to if (isHidden) true else null,
                                    "isDisabled" to if (isDisabled) true else null,
                                    "scoreboard" to scoreboard?.name
                                )
                            )
                        }
                    }

                    save(playersDataFile)
                }

                SimpleScore.plugin.logger.info("Player data saved.")
            }
        }

        fun clearPlayerData() {
            playersData.clear()
        }

        fun get(player: Player) = get(player.uniqueId)
        fun get(uniqueId: UUID): PlayerData {
            return playersData.getOrPut(uniqueId) {
                return@getOrPut PlayerData()
            }
        }

        fun getScoreboards(player: Player): List<Scoreboard> {
            return get(player).scoreboards
        }

        fun hasScoreboards(player: Player): Boolean {
            return get(player).hasScoreboards
        }

        fun getScoreboard(plugin: Plugin, player: Player): Scoreboard? {
            return get(player).getScoreboard(plugin)
        }

        fun setScoreboard(plugin: Plugin, player: Player, scoreboard: Scoreboard?) {
            get(player).also { playerData ->
                playerData.setScoreboard(plugin, scoreboard)
                SimpleScore.manager.clearScoreboard(player)
                if (playerData.scoreboards.isEmpty()) {
                    SimpleScore.manager.removeScoreboard(player)
                } else SimpleScore.manager.createScoreboard(player)
            }
        }

        fun isHidden(player: Player): Boolean {
            return get(player).isHidden
        }

        fun isHiding(plugin: Plugin, player: Player): Boolean {
            return get(player).isHiding(plugin)
        }

        fun setHidden(plugin: Plugin, player: Player, hidden: Boolean) {
            get(player).also { playerData ->
                if (hidden) {
                    if (playerData.hide(plugin)) {
                        SimpleScore.manager.clearScoreboard(player)
                    }
                } else playerData.show(plugin)
            }
        }

        fun toggleHidden(plugin: Plugin, player: Player): Boolean {
            return get(player).let { playerData ->
                if (!playerData.show(plugin)) {
                    playerData.hide(plugin)
                    SimpleScore.manager.clearScoreboard(player)
                    return@let true
                }
                return@let false
            }
        }

        fun isDisabled(player: Player): Boolean {
            return get(player).isDisabled
        }

        fun isDisabling(plugin: Plugin, player: Player): Boolean {
            return get(player).isDisabling(plugin)
        }

        fun setDisabled(plugin: Plugin, player: Player, disabled: Boolean) {
            get(player).also { playerData ->
                if (disabled) {
                    if (playerData.disable(plugin)) {
                        SimpleScore.manager.removeScoreboard(player)
                    }
                } else if (playerData.enable(plugin)) {
                    SimpleScore.manager.createScoreboard(player)
                }
            }
        }

        fun toggleDisabled(plugin: Plugin, player: Player): Boolean {
            return get(player).let { playerData ->
                if (!playerData.enable(plugin)) {
                    playerData.disable(plugin)
                    SimpleScore.manager.removeScoreboard(player)
                    return@let true
                }
                SimpleScore.manager.createScoreboard(player)
                return@let false
            }
        }
    }
}