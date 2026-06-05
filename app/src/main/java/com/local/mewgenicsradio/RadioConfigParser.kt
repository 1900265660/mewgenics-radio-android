package com.local.mewgenicsradio

class RadioConfigParser {
    fun parse(raw: String): RadioConfig {
        val allSongs = parseListBlock(raw, "all")
        val lockedSongs = parseListBlock(raw, "locked")
        val tutorialSongs = parseListBlock(raw, "tutorial_radio")
        val stateMachine = parseStateMachine(raw)

        return RadioConfig(
            allSongs = allSongs,
            lockedSongs = lockedSongs,
            tutorialSongs = tutorialSongs,
            stateMachine = stateMachine,
        )
    }

    private fun parseListBlock(raw: String, name: String): List<String> {
        val match = Regex("""(?s)\b${Regex.escape(name)}\s*\[(.*?)]""").find(raw)
            ?: return emptyList()

        return match.groupValues[1]
            .lineSequence()
            .map { it.substringBefore("//").trim() }
            .flatMap { it.splitToSequence(Regex("""\s+""")) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    private fun parseStateMachine(raw: String): Map<String, RadioStateDefinition> {
        val body = extractBraceBlock(raw, "radio_state_machine") ?: return emptyMap()
        val result = linkedMapOf<String, RadioStateDefinition>()

        Regex("""(?s)\b([A-Za-z0-9_]+)\s*\{(.*?)\}""")
            .findAll(body)
            .forEach { match ->
                val name = match.groupValues[1]
                val stateBody = match.groupValues[2]
                val playlist = Regex("""\bplaylist\s+([A-Za-z0-9_]+)""")
                    .find(stateBody)
                    ?.groupValues
                    ?.get(1)
                val specialPlaylist = Regex("""\bspecial_playlist\s+([A-Za-z0-9_]+)""")
                    .find(stateBody)
                    ?.groupValues
                    ?.get(1)
                val nextStates = Regex("""(?s)\bnext\s*\[(.*?)]""")
                    .find(stateBody)
                    ?.groupValues
                    ?.get(1)
                    ?.lineSequence()
                    ?.map { it.substringBefore("//").trim() }
                    ?.flatMap { it.splitToSequence(Regex("""\s+""")) }
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.toList()
                    .orEmpty()

                result[name] = RadioStateDefinition(
                    name = name,
                    playlist = playlist,
                    specialPlaylist = specialPlaylist,
                    nextStates = nextStates,
                )
            }

        return result
    }

    private fun extractBraceBlock(raw: String, name: String): String? {
        val nameIndex = raw.indexOf(name)
        if (nameIndex < 0) return null

        val openIndex = raw.indexOf('{', nameIndex)
        if (openIndex < 0) return null

        var depth = 0
        for (index in openIndex until raw.length) {
            when (raw[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return raw.substring(openIndex + 1, index)
                    }
                }
            }
        }

        return null
    }
}
