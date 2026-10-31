package com.brenninhotools.vconverter.script

enum class ScriptBase(val className: String, val folder: String, val refKind: String) {
    MODULE("Module", "scripts/modules", "module"),
    SONG("Song", "scripts/songs", "song"),
    STAGE("Stage", "scripts/stages", "stage"),
    NOTE_KIND("NoteKind", "scripts/notekinds", "notekind"),
    SONG_EVENT("SongEvent", "scripts/events", "event")
}

data class ScriptHost(val base: ScriptBase, val id: String, val title: String = id, val noteStyle: String? = null) {
    val ref: String get() = "${base.refKind}:$id"
}

object ScriptBases {
    val imports = mapOf(
        ScriptBase.MODULE to listOf("funkin.modding.module.Module"),
        ScriptBase.SONG to listOf("funkin.play.song.Song"),
        ScriptBase.STAGE to listOf("funkin.play.stage.Stage"),
        ScriptBase.NOTE_KIND to listOf("funkin.play.notes.notekind.NoteKind"),
        ScriptBase.SONG_EVENT to listOf("funkin.play.event.SongEvent", "funkin.data.event.SongEventSchema")
    )

    val noteKindCallbacks = setOf(
        "onCreate", "onCreatePost", "onUpdate", "onUpdatePost", "onKeyPress", "onKeyPressPre", "onKeyRelease", "onKeyReleasePre",
        "goodNoteHit", "goodNoteHitPre", "opponentNoteHit", "opponentNoteHitPre", "noteMiss", "onSpawnNote", "onDestroy", "onEventPushed"
    )

    private val registryEntry = setOf("id", "_data", "_fetchData", "destroy", "toString")

    private val flxBasic = setOf(
        "ID", "active", "alive", "exists", "visible", "camera", "cameras", "container", "zIndex", "kill", "revive", "destroy", "update",
        "draw", "toString", "getCameras", "getDefaultCamera", "flixelType"
    )

    private val flxObject = setOf(
        "x", "y", "width", "height", "angle", "moves", "immovable", "solid", "scrollFactor", "velocity", "acceleration", "drag",
        "maxVelocity", "last", "mass", "elasticity", "angularVelocity", "angularAcceleration", "angularDrag", "maxAngular", "health",
        "touching", "wasTouching", "allowCollisions", "collisionXDrag", "collisionYDrag", "pixelPerfectRender", "pixelPerfectPosition",
        "path", "hurt", "reset", "setPosition", "setSize", "overlaps", "overlapsAt", "overlapsPoint", "inWorldBounds",
        "getScreenPosition", "getPosition", "getMidpoint", "getHitbox", "getRotatedBounds", "isOnScreen", "isPixelPerfectRender",
        "isTouching", "justTouched", "updateMotion", "screenCenter", "debugBoundingBoxColor", "ignoreDrawDebug"
    )

    private val flxSprite = setOf(
        "animation", "frames", "frame", "framePixels", "graphic", "pixels", "frameWidth", "frameHeight", "numFrames", "alpha", "facing",
        "flipX", "flipY", "origin", "offset", "scale", "blend", "color", "colorTransform", "useColorTransform", "useFramePixels",
        "antialiasing", "dirty", "shader", "clipRect", "bakedRotationAngle", "loadGraphic", "loadRotatedGraphic", "loadGraphicFromSprite",
        "makeGraphic", "setGraphicSize", "updateHitbox", "resetSize", "resetFrameSize", "resetSizeFromFrame", "resetFrame", "resetHelpers",
        "centerOffsets", "centerOrigin", "setFrames", "updateFramePixels", "stamp", "getGraphicMidpoint", "isSimpleRender",
        "setColorTransform", "replaceColor", "drawFrame", "getPixelAt", "getScreenBounds", "transformWorldToPixels", "pixelsOverlapPoint",
        "setFacingFlip", "loadFrame", "updateColorTransform", "checkFlipX", "checkFlipY", "graphicLoaded"
    )

    private val flxSpriteGroup = setOf(
        "group", "members", "length", "maxSize", "directAlpha", "add", "insert", "remove", "replace", "recycle", "sort", "clear",
        "forEach", "forEachAlive", "forEachDead", "forEachExists", "forEachOfType", "getFirstAvailable", "getFirstNull",
        "getFirstExisting", "getFirstAlive", "getFirstDead", "countLiving", "countDead", "getRandom", "iterator", "preAdd",
        "findMinX", "findMaxX", "findMinY", "findMaxY", "transformChildren", "multiTransformChildren"
    )

    private val stage = setOf(
        "addBopper", "addCharacter", "addProp", "boppers", "buildStage", "camZoom", "characters", "debugIconGroup", "dispatchToCharacter",
        "dispatchToCharacters", "fetchAssetPaths", "frameBufferMan", "frameBuffersUpdated", "getBoyfriend", "getBoyfriendPosition",
        "getCharacter", "getDad", "getDadPosition", "getGirlfriend", "getGirlfriendPosition", "getNamedProp", "getOpponent", "getPlayer",
        "get_camZoom", "get_stageName", "log", "maskTexture", "namedProps", "pause", "refresh", "resetStage", "resume", "setShader",
        "setupFrameBuffers", "stageName"
    )

    private val song = setOf(
        "_metadata", "applyChartData", "buildRaw", "cacheCharts", "charter", "clearCharts", "difficulties", "difficulty",
        "fetchVariationMetadata", "getAlbumId", "getBaseInstrumentalId", "getDifficulty", "getFirstValidVariation", "getRawMetadata",
        "getStickerPackId", "getVariationsByCharacter", "getVariationsByCharacterId", "get_charter", "get_songArtist", "get_songName",
        "get_variations", "hasDifficulty", "isDifficultyVisible", "isDiscordRPCAnonymous", "isSongNew", "listAlbums",
        "listAltInstrumentalIds", "listDifficulties", "listSuffixedDifficulties", "log", "populateDifficulties", "songArtist", "songName",
        "validScore", "validateVariationId", "variation", "variations"
    )

    private val songEvent = setOf("getEventSchema", "getIconPath", "getTitle", "handleEvent", "processOldEvents")

    private val noteKind = setOf(
        "description", "getNotes", "getOtherNotes", "noanim", "noteKind", "noteStyleId", "params", "scoreable", "suffix"
    )

    private val callbacks = setOf(
        "onScriptEvent", "onCreate", "onDestroy", "onUpdate", "onStepHit", "onBeatHit", "onPause", "onResume", "onSongStart", "onSongEnd",
        "onGameOver", "onNoteIncoming", "onNoteHit", "onNoteMiss", "onNoteHoldDrop", "onSongEvent", "onNoteGhostMiss", "onCountdownStart",
        "onCountdownStep", "onCountdownEnd", "onSongLoaded", "onSongRetry"
    )

    fun members(base: ScriptBase): Set<String> = when (base) {
        ScriptBase.MODULE -> emptySet()
        ScriptBase.SONG -> song + registryEntry + callbacks
        ScriptBase.STAGE -> stage + flxSpriteGroup + flxSprite + flxObject + flxBasic + registryEntry + callbacks
        ScriptBase.NOTE_KIND -> noteKind + setOf("toString", "onScriptEvent", "onCreate", "onDestroy", "onUpdate", "onNoteIncoming", "onNoteHit", "onNoteMiss", "onNoteHoldDrop")
        ScriptBase.SONG_EVENT -> songEvent + setOf("id", "toString") + callbacks
    }

    val baseGameIds = mapOf(
        ScriptBase.SONG to setOf(
            "2hot", "blammed", "blazin", "bopeebo", "cocoa", "dadbattle", "darnell", "eggnog", "fresh", "guns", "lit-up", "philly-nice",
            "pico", "roses", "senpai", "south", "spaghetti", "spookeez", "stress", "thorns", "ugh", "winter-horrorland"
        ),
        ScriptBase.STAGE to setOf(
            "limoRide", "limoRideErect", "mainStageErect", "mallXmasErect", "phillyBlazin", "phillyStreets", "phillyStreetsErect",
            "phillyTrain", "phillyTrainErect", "school", "schoolErect", "schoolEvil", "schoolEvilErect", "spookyMansion",
            "spookyMansionErect", "sserafim", "tankmanBattlefield", "tankmanBattlefieldErect"
        ),
        ScriptBase.NOTE_KIND to setOf("censor", "hehPrettyGood", "mom", "ugh", "alt", "hey", "hurt", "noanim", "gf-sing")
    )

    fun isBaseGameId(base: ScriptBase, id: String): Boolean =
        baseGameIds[base]?.contains(id) == true || (base == ScriptBase.NOTE_KIND && id.startsWith("weekend-1-"))

    val baseGameClasses = setOf(
        "ABotAtlasSprite", "ABotVisualizer", "BFBedPerfectResults", "BFHoldingGFCharacter", "BFPixelCharacter", "BFShitResults",
        "BlammedSong", "BlazinSong", "BopeeboSong", "BoyfriendCard", "BoyfriendCharacter", "BoyfriendChristmasCharacter",
        "BoyfriendDarkCharacter", "BoyfriendFreeplayDJ", "BuildingEffectShader", "CasingSprite", "CensorNoteKind", "CocoaSong",
        "DadShootsSprite", "DadbattleSong", "DarnellBlazinCharacter", "DarnellCharacter", "DarnellSong", "EggnogErectSong", "EggnogSong",
        "FreshSong", "GirlfriendCharacter", "GirlfriendDarkCharacter", "GunsSong", "HehPrettyGoodNoteKind", "LimoRideErectStage",
        "LimoRideStage", "LitUpSong", "MainStageErectStage", "MallXmasErectStage", "MomSingsNoteKind", "MonsterCharacter",
        "NeneBaseCharacter", "NeneChristmasCharacter", "NeneDarkCharacter", "NenePixelCharacter", "NeneTankmenCharacter",
        "OtisSpeakerCharacter", "ParentsChristmasCharacter", "PerspectiveSprite", "PhillyBlazinStage", "PhillyNiceSong",
        "PhillyStreetsErectStage", "PhillyStreetsStage", "PhillyTrainErectStage", "PhillyTrainStage", "PicoBlazinCharacter",
        "PicoBloodPool", "PicoCard", "PicoChristmasCharacter", "PicoDarkCharacter", "PicoDopplegangerSprite", "PicoGoodResults",
        "PicoGreatResults", "PicoHoldingNeneCharacter", "PicoPerfectResults", "PicoPixelCharacter", "PicoPlayableCharacter",
        "PicoPlayerBaseCharacter", "PicoSong", "PicoSpeakerCharacter", "RosesDialogueBox", "RosesPicoMixSong", "RosesSong",
        "SantaDiesSprite", "SchoolErectStage", "SchoolEvilErectStage", "SchoolEvilStage", "SchoolStage", "SenpaiPicoConversation",
        "SenpaiPicoMixSong", "SenpaiSong", "SouthSong", "SpaghettiSong", "SpiritCharacter", "SpookeezSong", "SpookyDarkCharacter",
        "SpookyMansionErectStage", "SpookyMansionStage", "SpraycanAtlasSprite", "SserafimLevel", "SserafimStage", "StressPicoMixSong",
        "StressSong", "TankmanBattlefieldErectStage", "TankmanBattlefieldStage", "TankmanBloodyCharacter", "TankmanCharacter",
        "TankmanSprite", "TankmanSpriteGroup", "ThornsDialogueBox", "ThornsSong", "TwoHOTSong", "UghNoteKind", "UghSong",
        "Weekend1CockGunNoteKind", "Weekend1FireGunNoteKind", "Weekend1KickCanNoteKind", "Weekend1KneeCanNoteKind", "Weekend1Level",
        "Weekend1LightCanNoteKind", "WinterHorrorlandSong", "Module", "Song", "Stage", "NoteKind", "SongEvent", "PlayState"
    )

    fun classFor(base: ScriptBase, id: String, pascal: (String) -> String): String {
        val core = pascal(id).ifEmpty { "Script" }.let { if (it.first().isDigit()) "S$it" else it }
        return core + base.className
    }
}
